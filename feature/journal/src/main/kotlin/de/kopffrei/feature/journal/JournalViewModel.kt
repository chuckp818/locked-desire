package de.kopffrei.feature.journal

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import de.kopffrei.core.common.journal.JournalItem
import de.kopffrei.core.common.journal.JournalLogic
import de.kopffrei.core.common.model.Mood
import de.kopffrei.core.common.text.TextUtils
import de.kopffrei.core.common.time.TimeUtils
import de.kopffrei.core.database.entity.JournalEntryEntity
import de.kopffrei.core.database.repository.JournalRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

data class JournalListItem(
    val id: String,
    val title: String,
    val preview: String,
    val mood: Mood?,
    val tags: List<String>,
    val createdAt: Long,
)

data class JournalUiState(
    val items: List<JournalListItem> = emptyList(),
    val query: String = "",
    val moodFilter: Mood? = null,
    val tagFilter: String? = null,
    val allTags: List<String> = emptyList(),
    val hasAnyEntries: Boolean = false,
    val message: String? = null,
)

/** Tagebuch-Liste mit Suche, Stimmungs-/Tag-Filter und Textexport. */
@HiltViewModel
class JournalViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val journal: JournalRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val moodFilter = MutableStateFlow<Mood?>(null)
    private val tagFilter = MutableStateFlow<String?>(null)
    private val message = MutableStateFlow<String?>(null)

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private val results = query.debounce(250).flatMapLatest { journal.observeAll(it) }

    private val filters = combine(moodFilter, tagFilter) { mood, tag -> mood to tag }

    val state: StateFlow<JournalUiState> = combine(
        results,
        journal.observeAll(""),
        filters,
        query,
        message,
    ) { found, all, (mood, tag), currentQuery, msg ->
        JournalUiState(
            items = found
                .filter { JournalLogic.matchesFilter(it.mood, it.tags, mood, tag) }
                .map { it.toListItem() },
            query = currentQuery,
            moodFilter = mood,
            tagFilter = tag,
            allTags = JournalLogic.collectTags(all.map { it.tags }),
            hasAnyEntries = all.isNotEmpty(),
            message = msg,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JournalUiState())

    private fun JournalEntryEntity.toListItem() = JournalListItem(
        id = id,
        title = JournalLogic.displayTitle(title, text),
        // Vorschau: Text ohne die Zeile, die schon als Titel dient.
        preview = TextUtils.truncate(
            (if (title.isBlank()) text.lines().drop(1).joinToString(" ") else text.replace('\n', ' ')).trim(),
            140,
        ),
        mood = mood,
        tags = TextUtils.parseTags(tags),
        createdAt = createdAt,
    )

    fun setQuery(value: String) {
        query.value = value
    }

    fun setMoodFilter(value: Mood?) {
        moodFilter.value = value
    }

    fun setTagFilter(value: String?) {
        tagFilter.value = value
    }

    fun consumeMessage() {
        message.value = null
    }

    /** Schreibt alle Einträge als lesbare Textdatei in die vom Nutzer gewählte Datei. */
    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val entries = journal.getAll()
                    val text = JournalLogic.exportText(
                        entries.map {
                            JournalItem(it.title, it.text, it.mood, TextUtils.parseTags(it.tags), it.createdAt)
                        },
                        TimeUtils.systemZone(),
                        TimeUtils.nowMillis(),
                    )
                    context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                        out.write(text.toByteArray(Charsets.UTF_8))
                    } ?: throw IOException("Datei konnte nicht geöffnet werden")
                    "${entries.size} Einträge exportiert."
                } catch (e: IOException) {
                    "Export fehlgeschlagen: ${e.message ?: "unbekannter Fehler"}"
                } catch (e: SecurityException) {
                    "Export fehlgeschlagen: kein Zugriff auf die Datei."
                }
            }
            message.value = result
        }
    }
}
