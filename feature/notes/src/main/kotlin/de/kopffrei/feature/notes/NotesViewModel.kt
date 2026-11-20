package de.kopffrei.feature.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.kopffrei.core.common.model.NoteType
import de.kopffrei.core.common.model.OwnerType
import de.kopffrei.core.database.entity.NoteEntity
import de.kopffrei.core.database.repository.NoteRepository
import de.kopffrei.core.reminders.ReminderCoordinator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class NotesTab(val label: String) {
    NOTES("Notizen"),
    THOUGHTS("Gedanken"),
    ARCHIVE("Archiv"),
    TRASH("Papierkorb"),
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class NotesViewModel @Inject constructor(
    private val repository: NoteRepository,
    private val reminders: ReminderCoordinator,
) : ViewModel() {

    private val _tab = MutableStateFlow(NotesTab.NOTES)
    val tab: StateFlow<NotesTab> = _tab

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    /** Notizen passend zu Tab und Suchbegriff; die Suche durchsucht Titel, Text und Kategorie. */
    val items: StateFlow<List<NoteEntity>> = combine(_tab, _query.debounce(SEARCH_DEBOUNCE_MS)) { tab, query ->
        tab to query
    }.flatMapLatest { (tab, query) ->
        when (tab) {
            NotesTab.NOTES -> repository.observeActive(NoteType.NOTE, query)
            NotesTab.THOUGHTS -> repository.observeActive(NoteType.THOUGHT, query)
            NotesTab.ARCHIVE -> combine(
                repository.observeArchived(NoteType.NOTE, query),
                repository.observeArchived(NoteType.THOUGHT, query),
            ) { notes, thoughts -> (notes + thoughts).sortedByDescending { it.updatedAt } }
            NotesTab.TRASH -> combine(
                repository.observeTrashed(NoteType.NOTE, query),
                repository.observeTrashed(NoteType.THOUGHT, query),
            ) { notes, thoughts -> (notes + thoughts).sortedByDescending { it.trashedAt ?: 0L } }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectTab(tab: NotesTab) {
        _tab.value = tab
    }

    fun setQuery(query: String) {
        _query.value = query
    }

    fun addThought(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch { repository.addThought(text) }
    }

    fun setPinned(note: NoteEntity, pinned: Boolean) {
        viewModelScope.launch { repository.setPinned(note.id, pinned) }
    }

    fun setArchived(note: NoteEntity, archived: Boolean) {
        viewModelScope.launch { repository.setArchived(note.id, archived) }
    }

    fun moveToTrash(note: NoteEntity) {
        viewModelScope.launch {
            repository.moveToTrash(note.id)
            // Erinnerungen einer weggeworfenen Notiz sollen nicht mehr auslösen.
            reminders.rescheduleOwner(OwnerType.NOTE, note.id, reset = false)
        }
    }

    fun restore(note: NoteEntity) {
        viewModelScope.launch {
            repository.restore(note.id)
            repository.setArchived(note.id, false)
        }
    }

    fun deleteForever(note: NoteEntity) {
        viewModelScope.launch {
            reminders.deleteForOwner(OwnerType.NOTE, note.id)
            repository.deleteForever(note.id)
        }
    }

    fun emptyTrash(trashed: List<NoteEntity>) {
        viewModelScope.launch {
            trashed.forEach { reminders.deleteForOwner(OwnerType.NOTE, it.id) }
            repository.emptyTrash()
        }
    }

    /** Aus einem Gedanken wird eine normale Notiz. */
    fun thoughtToNote(note: NoteEntity) {
        viewModelScope.launch { repository.save(note.copy(type = NoteType.NOTE)) }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 150L
    }
}
