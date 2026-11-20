package de.kopffrei.feature.tasks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.kopffrei.core.common.checklist.Checklist
import de.kopffrei.core.common.model.OwnerType
import de.kopffrei.core.common.model.Priority
import de.kopffrei.core.common.model.RecurrenceRule
import de.kopffrei.core.common.model.TaskStatus
import de.kopffrei.core.common.reminder.ReminderDraft
import de.kopffrei.core.common.text.TextUtils
import de.kopffrei.core.common.time.Ids
import de.kopffrei.core.common.time.TimeUtils
import de.kopffrei.core.database.entity.TaskEntity
import de.kopffrei.core.database.entity.TaskStepEntity
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
import java.time.LocalTime
import javax.inject.Inject

data class StepUi(
    val id: String = Ids.newId(),
    val title: String = "",
    val done: Boolean = false,
)

data class TaskEditorState(
    val loaded: Boolean = false,
    val exists: Boolean = false,
    val id: String = "",
    val title: String = "",
    val notes: String = "",
    val priority: Int = Priority.NORMAL,
    /** Beginn des Fälligkeitstages (lokale Zeit) oder null. */
    val dueDate: Long? = null,
    /** Uhrzeit in Minuten seit Mitternacht oder null („ohne Uhrzeit“). */
    val dueMinutes: Int? = null,
    val estimateText: String = "",
    val status: TaskStatus = TaskStatus.OPEN,
    val recurrence: RecurrenceRule = RecurrenceRule.NONE,
    val steps: List<StepUi> = emptyList(),
    val reminders: List<ReminderDraft> = emptyList(),
    val message: String? = null,
)

/**
 * Bearbeitet eine Aufgabe. Gespeichert wird beim Verlassen und wenn die App in den Hintergrund geht
 * (sofern ein Titel vorhanden ist) – Eingaben gehen nicht verloren.
 *
 * Aufrufargumente: `taskId` (bestehende Aufgabe) oder `fromNote` (Notiz/Gedanke in eine Aufgabe
 * umwandeln; Checklisten-Punkte werden zu Schritten).
 */
@HiltViewModel
class TaskEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val tasks: TaskRepository,
    private val notes: NoteRepository,
    private val reminders: ReminderCoordinator,
) : ViewModel() {

    private val taskIdArg: String? = savedStateHandle["taskId"]
    private val fromNoteArg: String? = savedStateHandle["fromNote"]

    private val _state = MutableStateFlow(TaskEditorState())
    val state: StateFlow<TaskEditorState> = _state.asStateFlow()

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val saveMutex = Mutex()
    @Volatile private var dirty = false

    private var base = TaskEntity(title = "")
    private var savedReminders: List<ReminderDraft> = emptyList()
    private var savedDueAt: Long? = null

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val zone = TimeUtils.systemZone()
        val existing = taskIdArg?.let { tasks.get(it) }
        if (existing != null) {
            base = existing
            val steps = tasks.getSteps(existing.id).map { StepUi(it.id, it.title, it.isDone) }
            val drafts = reminders.loadDrafts(OwnerType.TASK, existing.id)
            savedReminders = drafts
            savedDueAt = existing.dueAt
            val due = existing.dueAt
            _state.value = TaskEditorState(
                loaded = true,
                exists = true,
                id = existing.id,
                title = existing.title,
                notes = existing.notes,
                priority = existing.priority,
                dueDate = due?.let { TimeUtils.startOfDay(it, zone) },
                dueMinutes = if (existing.dueHasTime && due != null) TimeUtils.minutesOfDay(due, zone) else null,
                estimateText = existing.estimatedMinutes?.toString().orEmpty(),
                status = existing.status,
                recurrence = existing.recurrence,
                steps = steps,
                reminders = drafts,
            )
            return
        }
        base = TaskEntity(title = "")
        var state = TaskEditorState(loaded = true, id = base.id)
        val note = fromNoteArg?.let { notes.get(it) }
        if (note != null) {
            base = base.copy(sourceNoteId = note.id)
            val firstLine = TextUtils.firstLine(note.body)
            state = if (note.isChecklist) {
                state.copy(
                    title = note.title.ifBlank { "Aufgabe aus Checkliste" },
                    steps = Checklist.parse(note.body).map { StepUi(title = it.text, done = it.checked) },
                )
            } else if (note.title.isNotBlank()) {
                state.copy(title = note.title, notes = note.body)
            } else {
                // Ohne Titel wird die erste Zeile zum Titel, der Rest zu den Notizen.
                state.copy(
                    title = TextUtils.truncate(firstLine, 120),
                    notes = note.body.lines().drop(1).joinToString("\n").trim(),
                )
            }
            dirty = true // aus einer Notiz erzeugte Vorbelegung soll gespeichert werden
        }
        _state.value = state
    }

    // ------------------------------------------------------------------ Bearbeiten

    private fun edit(transform: TaskEditorState.() -> TaskEditorState) {
        _state.update { it.transform() }
        dirty = true
    }

    fun setTitle(value: String) = edit { copy(title = value) }

    fun setNotes(value: String) = edit { copy(notes = value) }

    fun setPriority(value: Int) = edit { copy(priority = value) }

    fun setEstimate(value: String) = edit { copy(estimateText = value.filter { it.isDigit() }.take(4)) }

    fun setDueDate(value: Long?) = edit {
        // Ohne Datum gibt es weder Uhrzeit noch Wiederholung noch zeitbezogene Erinnerungen.
        if (value == null) {
            copy(
                dueDate = null,
                dueMinutes = null,
                recurrence = RecurrenceRule.NONE,
                reminders = reminders.filter { it.offsetMinutes == null },
            )
        } else {
            copy(dueDate = value)
        }
    }

    /** Schnellwahl: heute (0), morgen (1) oder in einer Woche (7). */
    fun setDueInDays(days: Long) {
        val zone = TimeUtils.systemZone()
        val date = TimeUtils.toLocalDate(TimeUtils.nowMillis(), zone).plusDays(days)
        setDueDate(TimeUtils.startOfDay(date, zone))
    }

    fun setDueMinutes(value: Int?) = edit { copy(dueMinutes = value) }

    fun setRecurrence(rule: RecurrenceRule) = edit {
        // Eine Wiederholung braucht ein Fälligkeitsdatum; ohne eines wird „heute“ gesetzt.
        val date = dueDate ?: if (rule.isRecurring) {
            TimeUtils.startOfDay(TimeUtils.nowMillis(), TimeUtils.systemZone())
        } else {
            null
        }
        copy(recurrence = rule, dueDate = date)
    }

    fun setReminders(value: List<ReminderDraft>) = edit { copy(reminders = value) }

    fun addStep() = edit { copy(steps = steps + StepUi()) }

    fun setStepTitle(id: String, title: String) = edit {
        copy(steps = steps.map { if (it.id == id) it.copy(title = title) else it })
    }

    fun setStepDone(id: String, done: Boolean) = edit {
        copy(steps = steps.map { if (it.id == id) it.copy(done = done) else it })
    }

    fun removeStep(id: String) = edit { copy(steps = steps.filter { it.id != id }) }

    fun moveStep(id: String, delta: Int) = edit {
        val from = steps.indexOfFirst { it.id == id }
        val to = (from + delta).coerceIn(0, (steps.size - 1).coerceAtLeast(0))
        if (from < 0 || from == to) {
            this
        } else {
            copy(steps = steps.toMutableList().also {
                val moved = it.removeAt(from)
                it.add(to, moved)
            })
        }
    }

    // ------------------------------------------------------------------ Speichern

    /** Sofort speichern, z. B. wenn die App in den Hintergrund geht. */
    fun flushNow() {
        if (dirty) ioScope.launch { persist() }
    }

    /** Speichert und ruft danach [onDone] auf – schlägt das Speichern fehl, bleibt der Editor offen. */
    fun leave(onDone: () -> Unit) {
        viewModelScope.launch {
            if (persist()) onDone()
        }
    }

    /** Neue Aufgabe verwerfen (ohne Speichern). */
    fun discard(onDone: () -> Unit) {
        dirty = false
        onDone()
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    /** @return false, wenn das Speichern fehlgeschlagen ist (die Änderungen bleiben dann erhalten). */
    private suspend fun persist(): Boolean = saveMutex.withLock {
        val s = _state.value
        if (!s.loaded || !dirty) return@withLock true
        if (s.title.isBlank()) return@withLock true // ohne Titel wird nichts angelegt
        val zone = TimeUtils.systemZone()
        val date = s.dueDate
        val minutes = s.dueMinutes
        val dueAt: Long? = date?.let {
            if (minutes != null) {
                TimeUtils.millisOf(TimeUtils.toLocalDate(it, zone), LocalTime.of(minutes / 60, minutes % 60), zone)
            } else {
                it
            }
        }
        val task = base.copy(
            title = s.title.trim(),
            notes = s.notes,
            priority = s.priority,
            dueAt = dueAt,
            dueHasTime = dueAt != null && minutes != null,
            estimatedMinutes = s.estimateText.toIntOrNull()?.takeIf { it > 0 },
            status = s.status,
            recurrence = if (dueAt == null) RecurrenceRule.NONE else s.recurrence,
        )
        val steps = s.steps.mapIndexed { index, step ->
            TaskStepEntity(id = step.id, taskId = task.id, title = step.title.trim(), isDone = step.done, position = index)
        }
        // Vor dem Speichern zurücksetzen: Änderungen während des Speicherns setzen das Flag erneut.
        dirty = false
        try {
            tasks.save(task, steps)
            base = task
            // Erinnerungen nur neu planen, wenn sie oder die Fälligkeit geändert wurden.
            if (s.reminders != savedReminders || dueAt != savedDueAt) {
                reminders.replaceForOwner(OwnerType.TASK, task.id, s.reminders)
                savedReminders = s.reminders
                savedDueAt = dueAt
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

    /** Speichern und die Aufgabe abhaken (bei Serien springt sie zum nächsten Termin). */
    fun complete(onDone: () -> Unit) {
        viewModelScope.launch {
            if (!persist()) return@launch
            val id = _state.value.id
            if (_state.value.exists) {
                tasks.complete(id)
                reminders.rescheduleOwner(OwnerType.TASK, id)
            }
            dirty = false
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            val id = _state.value.id
            if (_state.value.exists) {
                reminders.deleteForOwner(OwnerType.TASK, id)
                tasks.delete(id)
            }
            dirty = false
            onDone()
        }
    }

    override fun onCleared() {
        if (dirty) ioScope.launch { persist() }
        super.onCleared()
    }
}
