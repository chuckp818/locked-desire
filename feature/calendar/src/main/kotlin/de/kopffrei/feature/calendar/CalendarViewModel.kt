package de.kopffrei.feature.calendar

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import de.kopffrei.core.common.calendar.CalendarLogic
import de.kopffrei.core.common.calendar.CalendarMode
import de.kopffrei.core.common.ics.IcsCalendar
import de.kopffrei.core.common.ics.IcsEvent
import de.kopffrei.core.common.model.TaskStatus
import de.kopffrei.core.common.time.TimeUtils
import de.kopffrei.core.database.entity.CalendarEventEntity
import de.kopffrei.core.database.entity.TaskEntity
import de.kopffrei.core.database.repository.EventExpander
import de.kopffrei.core.database.repository.EventOccurrence
import de.kopffrei.core.database.repository.EventRepository
import de.kopffrei.core.database.repository.TaskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate
import javax.inject.Inject

/** Ein Tag mit seinen Terminen und fälligen Aufgaben. */
data class DayEntries(
    val date: LocalDate,
    val events: List<EventOccurrence>,
    val tasks: List<TaskEntity>,
)

data class CalendarUiState(
    val mode: CalendarMode = CalendarMode.MONTH,
    val selectedDate: LocalDate = LocalDate.now(),
    val today: LocalDate = LocalDate.now(),
    val title: String = "",
    /** Für Monats- und Wochenansicht: alle sichtbaren Tage. */
    val days: List<DayEntries> = emptyList(),
    val message: String? = null,
) {
    val selectedDay: DayEntries? get() = days.firstOrNull { it.date == selectedDate }
}

/**
 * Kalender: Termine (inkl. Serien) werden für den sichtbaren Zeitraum berechnet und
 * zusammen mit fälligen Aufgaben pro Tag gruppiert. ICS-Import/-Export läuft über den
 * Systemdialog zur Dateiauswahl; es ist keine Speicherberechtigung nötig.
 */
@HiltViewModel
class CalendarViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val events: EventRepository,
    private val tasks: TaskRepository,
) : ViewModel() {

    private val zone = TimeUtils.systemZone()
    private val mode = MutableStateFlow(CalendarMode.MONTH)
    private val selected = MutableStateFlow(LocalDate.now(zone))
    private val message = MutableStateFlow<String?>(null)

    val state: StateFlow<CalendarUiState> = combine(
        events.observeAll(),
        tasks.observeAll(),
        mode,
        selected,
        message,
    ) { allEvents, allTasks, currentMode, date, msg ->
        build(allEvents, allTasks, currentMode, date, msg)
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())

    private fun build(
        allEvents: List<CalendarEventEntity>,
        allTasks: List<TaskEntity>,
        currentMode: CalendarMode,
        date: LocalDate,
        msg: String?,
    ): CalendarUiState {
        val (from, to) = CalendarLogic.visibleRange(currentMode, date)
        val fromMillis = TimeUtils.startOfDay(from, zone)
        val toMillis = TimeUtils.startOfDay(to, zone)
        val occurrences = EventExpander.expand(allEvents, fromMillis, toMillis, zone)
        val openTasks = allTasks.filter { it.status != TaskStatus.DONE && it.dueAt != null }
        val days = generateSequence(from) { it.plusDays(1) }
            .takeWhile { it < to }
            .map { day ->
                DayEntries(
                    date = day,
                    events = occurrences
                        .filter { CalendarLogic.overlapsDay(it.startMillis, it.endMillis, day, zone) }
                        .sortedWith(compareByDescending<EventOccurrence> { it.event.allDay }.thenBy { it.startMillis }),
                    tasks = openTasks
                        .filter { TimeUtils.toLocalDate(it.dueAt!!, zone) == day }
                        .sortedBy { it.dueAt },
                )
            }
            .toList()
        return CalendarUiState(
            mode = currentMode,
            selectedDate = date,
            today = LocalDate.now(zone),
            title = CalendarLogic.title(currentMode, date),
            days = days,
            message = msg,
        )
    }

    fun setMode(value: CalendarMode) {
        mode.value = value
    }

    fun select(date: LocalDate) {
        selected.value = date
    }

    fun showToday() {
        selected.value = LocalDate.now(zone)
    }

    fun page(delta: Int) {
        selected.update { CalendarLogic.shift(mode.value, it, delta) }
    }

    fun consumeMessage() {
        message.value = null
    }

    // ------------------------------------------------------------------ ICS

    /** Schreibt alle Termine als .ics in die vom Nutzer gewählte Datei. */
    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val all = events.getAll()
                    val ics = IcsCalendar.export(
                        all.map {
                            IcsEvent(
                                uid = it.id,
                                summary = it.title,
                                description = it.notes,
                                location = it.location,
                                startMillis = it.startAt,
                                endMillis = it.endAt,
                                allDay = it.allDay,
                                rule = it.recurrence,
                            )
                        },
                        zone,
                    )
                    context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                        out.write(ics.toByteArray(Charsets.UTF_8))
                    } ?: throw IOException("Datei konnte nicht geöffnet werden")
                    "${all.size} Termine exportiert."
                } catch (e: IOException) {
                    "Export fehlgeschlagen: ${e.message ?: "unbekannter Fehler"}"
                } catch (e: SecurityException) {
                    "Export fehlgeschlagen: kein Zugriff auf die Datei."
                }
            }
            message.value = result
        }
    }

    /**
     * Liest eine .ics-Datei und legt neue Termine an. Termine, deren UID bereits vorhanden ist,
     * werden nicht überschrieben (kein Duplikat, keine verlorenen Änderungen).
     */
    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val text = readLimited(uri)
                        ?: return@withContext "Die Datei ist zu groß (mehr als 5 MB) oder konnte nicht gelesen werden."
                    val parsed = IcsCalendar.import(text, zone)
                    val existing = events.getAll().map { it.id }.toSet()
                    val fresh = parsed.events.filter { it.uid !in existing }
                    events.saveAll(
                        fresh.map {
                            CalendarEventEntity(
                                id = it.uid,
                                title = it.summary.ifBlank { "Termin" },
                                notes = it.description,
                                location = it.location,
                                allDay = it.allDay,
                                startAt = it.startMillis,
                                endAt = it.endMillis,
                                recurrence = it.rule,
                            )
                        },
                    )
                    buildString {
                        append("${fresh.size} Termine importiert")
                        val duplicates = parsed.events.size - fresh.size
                        if (duplicates > 0) append(", $duplicates bereits vorhanden")
                        if (parsed.skipped > 0) append(", ${parsed.skipped} übersprungen")
                        append('.')
                        if (parsed.warnings.isNotEmpty()) {
                            append(" Hinweis: ")
                            append(parsed.warnings.joinToString("; "))
                        }
                    }
                } catch (e: IOException) {
                    "Import fehlgeschlagen: ${e.message ?: "unbekannter Fehler"}"
                } catch (e: SecurityException) {
                    "Import fehlgeschlagen: kein Zugriff auf die Datei."
                }
            }
            message.value = result
        }
    }

    private fun readLimited(uri: Uri): String? {
        val limit = 5 * 1024 * 1024
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = java.io.ByteArrayOutputStream()
            val chunk = ByteArray(16 * 1024)
            while (true) {
                val read = input.read(chunk)
                if (read < 0) break
                buffer.write(chunk, 0, read)
                if (buffer.size() > limit) return null
            }
            buffer.toByteArray()
        } ?: return null
        return String(bytes, Charsets.UTF_8)
    }
}
