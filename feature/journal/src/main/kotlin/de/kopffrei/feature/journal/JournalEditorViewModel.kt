package de.kopffrei.feature.journal

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.kopffrei.core.common.journal.JournalLogic
import de.kopffrei.core.common.model.Mood
import de.kopffrei.core.common.text.TextUtils
import de.kopffrei.core.database.entity.JournalEntryEntity
import de.kopffrei.core.database.repository.JournalRepository
import kotlinx.coroutines.CancellationException
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

data class JournalEditorState(
    val loaded: Boolean = false,
    val exists: Boolean = false,
    val id: String = "",
    val title: String = "",
    val text: String = "",
    val mood: Mood? = null,
    val tagsText: String = "",
    val createdAt: Long = 0L,
    /** Bereits benutzte Tags als Vorschläge. */
    val knownTags: List<String> = emptyList(),
    val message: String? = null,
) {
    /** Ein Eintrag ohne Titel, Text, Stimmung und Tags wird nicht angelegt. */
    val isBlank: Boolean
        get() = title.isBlank() && text.isBlank() && mood == null && tagsText.isBlank()
}

/** Bearbeitet einen Tagebucheintrag. Speichert automatisch (kurz nach dem Tippen, beim Verlassen, im Hintergrund). */
@HiltViewModel
class JournalEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val journal: JournalRepository,
) : ViewModel() {

    private val entryIdArg: String? = savedStateHandle["entryId"]

    private val _state = MutableStateFlow(JournalEditorState())
    val state: StateFlow<JournalEditorState> = _state.asStateFlow()

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val saveMutex = Mutex()
    private var saveJob: Job? = null
    @Volatile private var dirty = false
    private var base = JournalEntryEntity()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val known = JournalLogic.collectTags(journal.getAll().map { it.tags })
        val existing = entryIdArg?.let { journal.get(it) }
        if (existing != null) {
            base = existing
            _state.value = JournalEditorState(
                loaded = true,
                exists = true,
                id = existing.id,
                title = existing.title,
                text = existing.text,
                mood = existing.mood,
                tagsText = existing.tags,
                createdAt = existing.createdAt,
                knownTags = known,
            )
        } else {
            base = JournalEntryEntity()
            _state.value = JournalEditorState(loaded = true, id = base.id, createdAt = base.createdAt, knownTags = known)
        }
    }

    private fun edit(transform: JournalEditorState.() -> JournalEditorState) {
        _state.update { it.transform() }
        dirty = true
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(AUTOSAVE_MS)
            persist()
        }
    }

    fun setTitle(value: String) = edit { copy(title = value) }

    fun setText(value: String) = edit { copy(text = value) }

    /** Diktierten Text an den Eintrag anhängen. */
    fun appendText(spoken: String) = edit {
        copy(text = if (text.isBlank()) spoken else text.trimEnd() + "\n" + spoken)
    }

    fun setMood(value: Mood?) = edit { copy(mood = value) }

    fun setTagsText(value: String) = edit { copy(tagsText = value) }

    fun addTag(tag: String) = edit {
        val current = TextUtils.parseTags(tagsText)
        if (current.any { it.equals(tag, ignoreCase = true) }) this else copy(tagsText = TextUtils.joinTags(current + tag))
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    fun flushNow() {
        saveJob?.cancel()
        if (dirty) ioScope.launch { persist() }
    }

    /** Speichert und ruft danach [onDone] auf – schlägt das Speichern fehl, bleibt der Editor offen. */
    fun leave(onDone: () -> Unit) {
        saveJob?.cancel()
        viewModelScope.launch {
            if (persist()) onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        saveJob?.cancel()
        viewModelScope.launch {
            dirty = false
            if (_state.value.exists) journal.delete(_state.value.id)
            onDone()
        }
    }

    private suspend fun persist(): Boolean = saveMutex.withLock {
        val s = _state.value
        if (!s.loaded || !dirty) return@withLock true
        if (s.isBlank) return@withLock true
        val entry = base.copy(
            title = s.title.trim(),
            text = s.text,
            mood = s.mood,
            tags = TextUtils.joinTags(TextUtils.parseTags(s.tagsText)),
        )
        dirty = false
        try {
            journal.save(entry)
            base = entry
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

    override fun onCleared() {
        saveJob?.cancel()
        if (dirty) ioScope.launch { persist() }
        super.onCleared()
    }

    private companion object {
        const val AUTOSAVE_MS = 600L
    }
}
