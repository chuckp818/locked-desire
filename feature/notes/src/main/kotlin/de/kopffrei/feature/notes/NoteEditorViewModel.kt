package de.kopffrei.feature.notes

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.kopffrei.core.common.checklist.Checklist
import de.kopffrei.core.common.checklist.ChecklistItem
import de.kopffrei.core.common.model.NoteType
import de.kopffrei.core.common.model.OwnerType
import de.kopffrei.core.common.reminder.ReminderDraft
import de.kopffrei.core.common.time.Ids
import de.kopffrei.core.database.entity.AttachmentEntity
import de.kopffrei.core.database.entity.JournalEntryEntity
import de.kopffrei.core.database.entity.NoteEntity
import de.kopffrei.core.database.repository.AttachmentRepository
import de.kopffrei.core.database.repository.JournalRepository
import de.kopffrei.core.database.repository.NoteRepository
import de.kopffrei.core.reminders.ReminderCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

data class NoteEditorState(
    val loaded: Boolean = false,
    val id: String = "",
    val type: NoteType = NoteType.NOTE,
    val title: String = "",
    val body: String = "",
    val isChecklist: Boolean = false,
    val items: List<ChecklistItem> = emptyList(),
    val colorIndex: Int = 0,
    val category: String = "",
    val isPinned: Boolean = false,
    val reminder: ReminderDraft? = null,
    val attachments: List<AttachmentEntity> = emptyList(),
    val message: String? = null,
) {
    /** Leer = weder Text noch Anhang. Leere neue Notizen werden nicht gespeichert. */
    val isBlank: Boolean
        get() = title.isBlank() &&
            attachments.isEmpty() &&
            (if (isChecklist) items.all { it.text.isBlank() } else body.isBlank())
}

/**
 * Bearbeitet eine Notiz. Änderungen werden automatisch gespeichert (kurz nach dem Tippen, beim
 * Verlassen und wenn die App in den Hintergrund geht) – es gibt keinen „Speichern“-Knopf, und
 * nichts geht durch Zurückgehen verloren.
 */
@HiltViewModel
class NoteEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val notes: NoteRepository,
    private val attachmentRepository: AttachmentRepository,
    private val journal: JournalRepository,
    private val reminders: ReminderCoordinator,
) : ViewModel() {

    private val noteIdArg: String? = savedStateHandle["noteId"]
    private val startAsThought: Boolean = savedStateHandle["thought"] ?: false

    /** Beim Öffnen sofort die Spracheingabe starten (Schnellaktion „Einsprechen“). */
    val startWithVoice: Boolean = savedStateHandle["voice"] ?: false

    private val _state = MutableStateFlow(NoteEditorState())
    val state: StateFlow<NoteEditorState> = _state.asStateFlow()

    // Speichern darf nicht mit dem ViewModel abbrechen (z. B. beim schnellen Zurückgehen).
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val saveMutex = Mutex()
    private var saveJob: Job? = null
    private var dirty = false

    private var base: NoteEntity = NoteEntity()
    private var existsInDb = false
    private var savedReminder: ReminderDraft? = null

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val existing = noteIdArg?.let { notes.get(it) }
        if (existing != null) {
            base = existing
            existsInDb = true
            val reminder = reminders.loadDrafts(OwnerType.NOTE, existing.id).firstOrNull { it.fixedAt != null }
            savedReminder = reminder
            _state.value = NoteEditorState(
                loaded = true,
                id = existing.id,
                type = existing.type,
                title = existing.title,
                body = existing.body,
                isChecklist = existing.isChecklist,
                items = if (existing.isChecklist) Checklist.parse(existing.body) else emptyList(),
                colorIndex = existing.colorIndex,
                category = existing.category.orEmpty(),
                isPinned = existing.isPinned,
                reminder = reminder,
            )
        } else {
            val type = if (startAsThought) NoteType.THOUGHT else NoteType.NOTE
            base = NoteEntity(type = type)
            _state.value = NoteEditorState(loaded = true, id = base.id, type = type)
        }
        val id = _state.value.id
        viewModelScope.launch {
            attachmentRepository.observe(OwnerType.NOTE, id).collect { list ->
                _state.update { it.copy(attachments = list) }
            }
        }
    }

    // ------------------------------------------------------------------ Bearbeiten

    private fun edit(transform: NoteEditorState.() -> NoteEditorState) {
        _state.update { it.transform() }
        dirty = true
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(AUTOSAVE_MS)
            persist()
        }
    }

    fun setTitle(value: String) = edit { copy(title = value) }

    fun setBody(value: String) = edit { copy(body = value) }

    fun setCategory(value: String) = edit { copy(category = value) }

    fun setColor(index: Int) = edit { copy(colorIndex = index) }

    fun setPinned(pinned: Boolean) = edit { copy(isPinned = pinned) }

    fun setReminder(draft: ReminderDraft?) = edit { copy(reminder = draft) }

    /** Wechselt zwischen Text und Checkliste; Haken gehen beim Zurückwechseln zu Text verloren. */
    fun toggleChecklist() = edit {
        if (isChecklist) {
            copy(isChecklist = false, body = Checklist.toPlainText(items), items = emptyList())
        } else {
            copy(isChecklist = true, items = Checklist.fromPlainText(body).ifEmpty { listOf(ChecklistItem("")) })
        }
    }

    fun setItemText(index: Int, text: String) = edit {
        copy(items = items.mapIndexed { i, item -> if (i == index) item.copy(text = text) else item })
    }

    fun setItemChecked(index: Int, checked: Boolean) = edit {
        copy(items = items.mapIndexed { i, item -> if (i == index) item.copy(checked = checked) else item })
    }

    /** Fügt hinter [index] einen leeren Punkt ein und gibt dessen Position zurück (für den Fokus). */
    fun addItemAfter(index: Int): Int {
        val position = (index + 1).coerceIn(0, _state.value.items.size)
        edit { copy(items = items.toMutableList().also { it.add(position, ChecklistItem("")) }) }
        return position
    }

    fun removeItem(index: Int) = edit {
        copy(items = items.filterIndexed { i, _ -> i != index }.ifEmpty { listOf(ChecklistItem("")) })
    }

    /** Hängt erkannten Sprachtext an. */
    fun appendSpoken(text: String) {
        if (text.isBlank()) return
        if (_state.value.isChecklist) {
            edit {
                val cleaned = items.filter { it.text.isNotBlank() }
                copy(items = cleaned + Checklist.fromPlainText(text))
            }
        } else {
            edit { copy(body = if (body.isBlank()) text else body.trimEnd() + "\n" + text) }
        }
    }

    // ------------------------------------------------------------------ Anhänge

    fun addAttachment(uri: Uri) {
        viewModelScope.launch {
            val added = attachmentRepository.addFromUri(OwnerType.NOTE, _state.value.id, uri)
            if (added == null) _state.update { it.copy(message = "Die Datei konnte nicht gelesen werden.") }
            // Anhang zählt als Inhalt: neue Notiz sofort sichern.
            if (added != null) {
                // Sofort im Zustand vermerken, damit die Notiz nicht als „leer“ übersprungen wird,
                // bevor der Flow den neuen Anhang meldet.
                _state.update { it.copy(attachments = it.attachments.filter { a -> a.id != added.id } + added) }
                dirty = true
                persist()
            }
        }
    }

    fun removeAttachment(attachmentId: String) {
        viewModelScope.launch { attachmentRepository.remove(attachmentId) }
    }

    /** Freigabe-URI und Dateityp zum Öffnen in einer anderen App. */
    fun attachmentOpenInfo(attachmentId: String): Pair<Uri, String>? {
        val attachment = _state.value.attachments.firstOrNull { it.id == attachmentId } ?: return null
        return attachmentRepository.shareUri(attachment) to attachment.mimeType
    }

    /** Datei eines Anhangs (für Vorschaubilder). */
    fun fileOf(attachment: AttachmentEntity): java.io.File = attachmentRepository.file(attachment)

    fun clearMessage() = _state.update { it.copy(message = null) }

    // ------------------------------------------------------------------ Speichern

    /** Sofort speichern (z. B. wenn die App in den Hintergrund geht). */
    fun flushNow() {
        saveJob?.cancel()
        if (!dirty) return
        ioScope.launch { persist() }
    }

    /** Speichern und danach [onDone] aufrufen (Zurück, Umwandeln …). */
    fun flush(onDone: () -> Unit) {
        saveJob?.cancel()
        viewModelScope.launch {
            persist(final = true)
            onDone()
        }
    }

    private suspend fun persist(final: Boolean = false) = saveMutex.withLock {
        val s = _state.value
        if (!s.loaded) return@withLock
        if (s.isBlank) {
            // Leere Notizen nicht anlegen; eine bestehende, leer gewordene beim Verlassen aufräumen.
            if (final && existsInDb) {
                reminders.deleteForOwner(OwnerType.NOTE, s.id)
                notes.deleteForever(s.id)
                existsInDb = false
            }
            dirty = false
            return@withLock
        }
        val entity = base.copy(
            type = s.type,
            title = s.title.trim(),
            body = if (s.isChecklist) Checklist.serialize(s.items) else s.body,
            isChecklist = s.isChecklist,
            colorIndex = s.colorIndex,
            category = s.category.trim().ifEmpty { null },
            isPinned = s.isPinned,
        )
        notes.save(entity)
        base = entity
        existsInDb = true
        // Erinnerungen nur bei Änderung neu planen, sonst würde jedes Autosave eine ausgelöste
        // Erinnerung wieder scharf stellen.
        if (s.reminder != savedReminder) {
            reminders.replaceForOwner(OwnerType.NOTE, s.id, listOfNotNull(s.reminder))
            savedReminder = s.reminder
        }
        dirty = false
    }

    // ------------------------------------------------------------------ Umwandeln und Aufräumen

    fun saveAsJournalEntry() {
        viewModelScope.launch {
            persist()
            val s = _state.value
            val text = if (s.isChecklist) Checklist.toPlainText(s.items) else s.body
            if (s.title.isBlank() && text.isBlank()) {
                _state.update { it.copy(message = "Die Notiz ist noch leer.") }
                return@launch
            }
            journal.save(JournalEntryEntity(id = Ids.newId(), title = s.title.trim(), text = text))
            _state.update { it.copy(message = "Als Tagebucheintrag gespeichert.") }
        }
    }

    fun archive(onDone: () -> Unit) {
        saveJob?.cancel()
        viewModelScope.launch {
            persist(final = true)
            if (existsInDb) notes.setArchived(_state.value.id, true)
            onDone()
        }
    }

    fun moveToTrash(onDone: () -> Unit) {
        saveJob?.cancel()
        viewModelScope.launch {
            persist(final = true)
            if (existsInDb) {
                notes.moveToTrash(_state.value.id)
                reminders.rescheduleOwner(OwnerType.NOTE, _state.value.id, reset = false)
            }
            onDone()
        }
    }

    override fun onCleared() {
        // Falls noch ein verzögertes Speichern offen war: jetzt nachholen.
        if (dirty) ioScope.launch { persist(final = true) }
        super.onCleared()
    }

    private companion object {
        const val AUTOSAVE_MS = 600L
    }
}
