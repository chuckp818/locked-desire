package de.kopffrei.feature.calendar

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.kopffrei.core.common.checklist.Checklist
import de.kopffrei.core.common.model.OwnerType
import de.kopffrei.core.common.model.RecurrenceRule
import de.kopffrei.core.common.reminder.ReminderDraft
import de.kopffrei.core.common.reminder.ReminderPlanner
import de.kopffrei.core.common.text.TextUtils
import de.kopffrei.core.common.time.TimeUtils
import de.kopffrei.core.database.entity.AttachmentEntity
import de.kopffrei.core.database.entity.CalendarEventEntity
import de.kopffrei.core.database.entity.TaskEntity
import de.kopffrei.core.database.repository.AttachmentRepository
import de.kopffrei.core.database.repository.EventRepository
import de.kopffrei.core.database.repository.NoteRepository
import de.kopffrei.core.database.repository.TaskRepository
import de.kopffrei.core.reminders.ReminderCoordinator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class EventEditorState(
    val loaded: Boolean = false,
    val exists: Boolean = false,
    val id: String = "",
    val title: String = "",
    val notes: String = "",
    val location: String = "",
    val allDay: Boolean = false,
    /** Beginn des Starttags (lokale Zeit). */
    val startDate: Long = 0L,
    val startMinutes: Int = 9 * 60,
    /** Beginn des (letzten) Endtags; bei ganztägigen Terminen ist dieser Tag noch enthalten. */
    val endDate: Long = 0L,
    val endMinutes: Int = 10 * 60,
    val colorIndex: Int = 0,
    val category: String = "",
    val recurrence: RecurrenceRule = RecurrenceRule.NONE,
    val prepText: String = "",
    val travelText: String = "",
    val linkedTaskId: String? = null,
    val reminders: List<ReminderDraft> = emptyList(),
    val attachments: List<AttachmentEntity> = emptyList(),
    /** Aufgaben zur Auswahl für die Verknüpfung. */
    val selectableTasks: List<TaskEntity> = emptyList(),
    val message: String? = null,
) {
    /** Das Ende liegt vor dem Beginn (wird beim Speichern auf den Beginn angehoben). */
    val endBeforeStart: Boolean
        get() = endDate < startDate || (!allDay && endDate == startDate && endMinutes < startMinutes)
}

/**
 * Bearbeitet einen Termin. Gespeichert wird beim Verlassen und wenn die App in den Hintergrund
 * geht. Eine Serie wird immer als Ganzes bearbeitet (Ausnahmen einzelner Termine sind noch nicht enthalten).
 *
 * Argumente: `eventId` (bestehender Termin), `fromNote` (Notiz in einen Termin umwandeln),
 * `date` (Beginn des vorgewählten Tages in Millisekunden, als Text).
 */
@HiltViewModel
class EventEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val events: EventRepository,
    private val tasks: TaskRepository,
    private val notes: NoteRepository,
    private val attachmentRepository: AttachmentRepository,
    private val reminders: ReminderCoordinator,
) : ViewModel() {

    private val eventIdArg: String? = savedStateHandle["eventId"]
    private val fromNoteArg: String? = savedStateHandle["fromNote"]
    private val dateArg: Long? = (savedStateHandle.get<Any?>("date"))?.toString()?.toLongOrNull()?.takeIf { it > 0 }

    private val zone = TimeUtils.systemZone()
    private val _state = MutableStateFlow(EventEditorState())
    val state: StateFlow<EventEditorState> = _state.asStateFlow()

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val saveMutex = Mutex()
    @Volatile private var dirty = false

    private var base = CalendarEventEntity(title = "", startAt = 0L, endAt = 0L)
    private var savedReminders: List<ReminderDraft> = emptyList()
    private var savedStartAt: Long? = null
    private var savedRecurrence: RecurrenceRule = RecurrenceRule.NONE

    init {
        viewModelScope.launch {
            load()
            attachmentRepository.observe(OwnerType.EVENT, _state.value.id).collect { list ->
                _state.update { it.copy(attachments = list) }
            }
        }
    }

    private suspend fun load() {
        val selectable = tasks.getOpen()
        val existing = eventIdArg?.let { events.get(it) }
        if (existing != null) {
            base = existing
            val drafts = reminders.loadDrafts(OwnerType.EVENT, existing.id)
            savedReminders = drafts
            savedStartAt = existing.startAt
            savedRecurrence = existing.recurrence
            val startDay = TimeUtils.startOfDay(existing.startAt, zone)
            val endDay = if (existing.allDay) {
                // endAt ist exklusiv (Beginn des Folgetags) – im Editor wird der letzte Tag gezeigt.
                val last = TimeUtils.toLocalDate(existing.endAt, zone).minusDays(1)
                maxOf(startDay, TimeUtils.startOfDay(last, zone))
            } else {
                TimeUtils.startOfDay(existing.endAt, zone)
            }
            val linked = existing.linkedTaskId?.let { id ->
                if (selectable.any { it.id == id }) null else tasks.get(id)
            }
            _state.value = EventEditorState(
                loaded = true,
                exists = true,
                id = existing.id,
                title = existing.title,
                notes = existing.notes,
                location = existing.location,
                allDay = existing.allDay,
                startDate = startDay,
                startMinutes = TimeUtils.minutesOfDay(existing.startAt, zone),
                endDate = endDay,
                endMinutes = TimeUtils.minutesOfDay(existing.endAt, zone),
                colorIndex = existing.colorIndex,
                category = existing.category.orEmpty(),
                recurrence = existing.recurrence,
                prepText = existing.prepMinutes.takeIf { it > 0 }?.toString().orEmpty(),
                travelText = existing.travelMinutes.takeIf { it > 0 }?.toString().orEmpty(),
                linkedTaskId = existing.linkedTaskId,
                reminders = drafts,
                selectableTasks = selectable + listOfNotNull(linked),
            )
            return
        }

        base = CalendarEventEntity(title = "", startAt = 0L, endAt = 0L)
        val now = TimeUtils.nowMillis()
        val today = TimeUtils.startOfDay(now, zone)
        val day = dateArg?.let { TimeUtils.startOfDay(it, zone) } ?: today
        // Heute: nächste volle Stunde, sonst 9:00 Uhr.
        val startMinutes = if (day == today) {
            ((TimeUtils.minutesOfDay(now, zone) / 60) + 1).coerceAtMost(22) * 60
        } else {
            9 * 60
        }
        var state = EventEditorState(
            loaded = true,
            id = base.id,
            startDate = day,
            startMinutes = startMinutes,
            endDate = day,
            endMinutes = startMinutes + 60,
            selectableTasks = selectable,
        )
        val note = fromNoteArg?.let { notes.get(it) }
        if (note != null) {
            val firstLine = TextUtils.firstLine(note.body)
            val body = if (note.isChecklist) Checklist.toPlainText(Checklist.parse(note.body)) else note.body
            state = if (note.title.isNotBlank()) {
                state.copy(title = note.title, notes = body)
            } else {
                state.copy(
                    title = TextUtils.truncate(firstLine, 120),
                    notes = body.lines().drop(1).joinToString("\n").trim(),
                )
            }
            dirty = true // Umwandlung aus einer Notiz: Vorbelegung wird beim Verlassen gespeichert
        }
        _state.value = state
    }

    // ------------------------------------------------------------------ Bearbeiten

    private fun edit(transform: EventEditorState.() -> EventEditorState) {
        _state.update { it.transform() }
        dirty = true
    }

    fun setTitle(value: String) = edit { copy(title = value) }

    fun setNotes(value: String) = edit { copy(notes = value) }

    fun setLocation(value: String) = edit { copy(location = value) }

    fun setCategory(value: String) = edit { copy(category = value) }

    fun setColor(index: Int) = edit { copy(colorIndex = index) }

    fun setPrep(value: String) = edit { copy(prepText = value.filter { it.isDigit() }.take(3)) }

    fun setTravel(value: String) = edit { copy(travelText = value.filter { it.isDigit() }.take(3)) }

    fun setRecurrence(rule: RecurrenceRule) = edit { copy(recurrence = rule) }

    fun setReminders(value: List<ReminderDraft>) = edit { copy(reminders = value) }

    fun setLinkedTask(taskId: String?) = edit { copy(linkedTaskId = taskId) }

    fun setAllDay(value: Boolean) = edit {
        if (value == allDay) {
            this
        } else if (value) {
            copy(allDay = true, endDate = maxOf(endDate, startDate))
        } else {
            val start = startDateTime()
            val end = endDateTime()
            val fixedEnd = if (!end.isAfter(start)) start.plusMinutes(60) else end
            copy(
                allDay = false,
                endDate = TimeUtils.startOfDay(fixedEnd.toLocalDate(), zone),
                endMinutes = fixedEnd.hour * 60 + fixedEnd.minute,
            )
        }
    }

    /** Beginn ändern; Dauer bzw. Anzahl der Tage bleibt erhalten, das Ende wandert mit. */
    fun setStart(dateMillis: Long?, minutes: Int?) = edit {
        val newDate = dateMillis ?: startDate
        val newMinutes = minutes ?: startMinutes
        if (allDay) {
            val days = ChronoUnit.DAYS.between(
                TimeUtils.toLocalDate(startDate, zone),
                TimeUtils.toLocalDate(endDate, zone),
            ).coerceAtLeast(0)
            copy(
                startDate = newDate,
                endDate = TimeUtils.startOfDay(TimeUtils.toLocalDate(newDate, zone).plusDays(days), zone),
            )
        } else {
            val duration = Duration.between(startDateTime(), endDateTime())
                .let { if (it.isNegative) Duration.ofMinutes(60) else it }
            val newStart = TimeUtils.toLocalDate(newDate, zone).atTime(newMinutes / 60, newMinutes % 60)
            val newEnd = newStart.plus(duration)
            copy(
                startDate = newDate,
                startMinutes = newMinutes,
                endDate = TimeUtils.startOfDay(newEnd.toLocalDate(), zone),
                endMinutes = newEnd.hour * 60 + newEnd.minute,
            )
        }
    }

    fun setEnd(dateMillis: Long?, minutes: Int?) = edit {
        copy(endDate = dateMillis ?: endDate, endMinutes = minutes ?: endMinutes)
    }

    private fun EventEditorState.startDateTime(): LocalDateTime =
        TimeUtils.toLocalDate(startDate, zone).atTime(startMinutes / 60, startMinutes % 60)

    private fun EventEditorState.endDateTime(): LocalDateTime =
        TimeUtils.toLocalDate(endDate, zone).atTime(endMinutes / 60, endMinutes % 60)

    /** Ergänzt Stufen „Fertigmachen“ und „Losgehen“ aus Vorbereitungs- und Fahrzeit. */
    fun generateStagesFromTimes() {
        val s = _state.value
        val prep = s.prepText.toIntOrNull() ?: 0
        val travel = s.travelText.toIntOrNull() ?: 0
        val generated = ReminderPlanner.stagesFromTimes(prep, travel)
        if (generated.isEmpty()) {
            _state.update { it.copy(message = "Trage zuerst eine Vorbereitungs- oder Fahrzeit ein.") }
            return
        }
        val additions = generated.filter { g ->
            s.reminders.none { it.offsetMinutes == g.offsetMinutes && it.label == g.label }
        }
        if (additions.isEmpty()) {
            _state.update { it.copy(message = "Diese Erinnerungen gibt es schon.") }
            return
        }
        setReminders(s.reminders + additions)
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    // ------------------------------------------------------------------ Anhänge

    fun addAttachment(uri: Uri) {
        viewModelScope.launch {
            val added = attachmentRepository.addFromUri(OwnerType.EVENT, _state.value.id, uri)
            if (added == null) {
                _state.update { it.copy(message = "Die Datei konnte nicht gelesen werden.") }
            } else {
                _state.update { it.copy(attachments = it.attachments.filter { a -> a.id != added.id } + added) }
                dirty = true
                persist()
            }
        }
    }

    fun removeAttachment(attachmentId: String) {
        viewModelScope.launch { attachmentRepository.remove(attachmentId) }
    }

    fun attachmentOpenInfo(attachmentId: String): Pair<Uri, String>? {
        val attachment = _state.value.attachments.firstOrNull { it.id == attachmentId } ?: return null
        return attachmentRepository.shareUri(attachment) to attachment.mimeType
    }

    fun fileOf(attachment: AttachmentEntity): java.io.File = attachmentRepository.file(attachment)

    // ------------------------------------------------------------------ Speichern

    fun flushNow() {
        if (dirty) ioScope.launch { persist() }
    }

    /** Speichert und ruft danach [onDone] auf – schlägt das Speichern fehl, bleibt der Editor offen. */
    fun leave(onDone: () -> Unit) {
        viewModelScope.launch {
            if (persist()) onDone()
        }
    }

    /** Neuen Termin verwerfen (ohne Speichern); hinzugefügte Anhänge werden wieder entfernt. */
    fun discard(onDone: () -> Unit) {
        viewModelScope.launch {
            dirty = false
            if (!_state.value.exists) attachmentRepository.removeAllFor(OwnerType.EVENT, _state.value.id)
            onDone()
        }
    }

    /** @return false, wenn das Speichern fehlgeschlagen ist (die Änderungen bleiben dann erhalten). */
    private suspend fun persist(): Boolean = saveMutex.withLock {
        val s = _state.value
        if (!s.loaded || !dirty) return@withLock true
        // Ohne Titel wird nur gespeichert, wenn es Anhänge gibt (nichts soll verloren gehen).
        val hasAttachments = s.attachments.isNotEmpty() ||
            attachmentRepository.getFor(OwnerType.EVENT, s.id).isNotEmpty()
        if (s.title.isBlank() && !hasAttachments) return@withLock true

        val startAt: Long
        val endAt: Long
        if (s.allDay) {
            startAt = s.startDate
            val lastDay = TimeUtils.toLocalDate(maxOf(s.endDate, s.startDate), zone)
            endAt = TimeUtils.startOfDay(lastDay.plusDays(1), zone)
        } else {
            startAt = TimeUtils.millisOf(
                TimeUtils.toLocalDate(s.startDate, zone),
                LocalTime.of(s.startMinutes / 60, s.startMinutes % 60),
                zone,
            )
            val rawEnd = TimeUtils.millisOf(
                TimeUtils.toLocalDate(s.endDate, zone),
                LocalTime.of(s.endMinutes / 60, s.endMinutes % 60),
                zone,
            )
            endAt = maxOf(rawEnd, startAt)
        }
        val event = base.copy(
            title = s.title.trim().ifBlank { "Termin ohne Titel" },
            notes = s.notes,
            location = s.location.trim(),
            allDay = s.allDay,
            startAt = startAt,
            endAt = endAt,
            colorIndex = s.colorIndex,
            category = s.category.trim().ifBlank { null },
            recurrence = s.recurrence,
            prepMinutes = s.prepText.toIntOrNull() ?: 0,
            travelMinutes = s.travelText.toIntOrNull() ?: 0,
            linkedTaskId = s.linkedTaskId,
        )
        // Vor dem Speichern zurücksetzen: Änderungen während des Speicherns setzen das Flag erneut.
        dirty = false
        try {
            events.save(event)
            base = event
            // Erinnerungen nur neu planen, wenn sie, der Beginn oder die Wiederholung geändert wurden.
            if (s.reminders != savedReminders || startAt != savedStartAt || s.recurrence != savedRecurrence) {
                reminders.replaceForOwner(OwnerType.EVENT, event.id, s.reminders)
                savedReminders = s.reminders
                savedStartAt = startAt
                savedRecurrence = s.recurrence
            }
            _state.update { it.copy(exists = true) }
            true
        } catch (e: CancellationException) {
            dirty = true
            throw e
        } catch (e: Exception) {
            dirty = true
            _state.update { it.copy(message = "Speichern fehlgeschlagen. Bitte versuche es noch einmal.") }
            false
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            val id = _state.value.id
            reminders.deleteForOwner(OwnerType.EVENT, id)
            events.delete(id)
            dirty = false
            onDone()
        }
    }

    override fun onCleared() {
        if (dirty) ioScope.launch { persist() }
        super.onCleared()
    }
}
