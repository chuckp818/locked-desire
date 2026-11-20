package de.kopffrei.core.database.repository

import de.kopffrei.core.common.WidgetUpdater
import de.kopffrei.core.common.model.NoteType
import de.kopffrei.core.common.model.OwnerType
import de.kopffrei.core.common.text.TextUtils
import de.kopffrei.core.common.time.TimeUtils
import de.kopffrei.core.database.dao.NoteDao
import de.kopffrei.core.database.entity.NoteEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Notizen und Gedanken inklusive Archiv und Papierkorb mit Wiederherstellung. */
@Singleton
class NoteRepository @Inject constructor(
    private val dao: NoteDao,
    private val attachments: AttachmentRepository,
    private val widgets: WidgetUpdater,
) {
    fun observeActive(type: NoteType, query: String = ""): Flow<List<NoteEntity>> =
        dao.observeActive(type, TextUtils.likePattern(query))

    fun observeArchived(type: NoteType, query: String = ""): Flow<List<NoteEntity>> =
        dao.observeArchived(type, TextUtils.likePattern(query))

    fun observeTrashed(type: NoteType, query: String = ""): Flow<List<NoteEntity>> =
        dao.observeTrashed(type, TextUtils.likePattern(query))

    fun observeLatestThoughts(limit: Int): Flow<List<NoteEntity>> = dao.observeLatestThoughts(limit)

    suspend fun getLatestThoughts(limit: Int): List<NoteEntity> = dao.getLatestThoughts(limit)

    suspend fun getLatestPinned(): NoteEntity? = dao.getLatestPinned()

    suspend fun get(id: String): NoteEntity? = dao.get(id)

    fun observe(id: String): Flow<NoteEntity?> = dao.observe(id)

    suspend fun save(note: NoteEntity) {
        dao.upsert(note.copy(updatedAt = TimeUtils.nowMillis()))
        widgets.requestUpdate()
    }

    /** Schnelles Festhalten eines Gedankens (Gedanken-Parkplatz, „Kopf ausschütten“). */
    suspend fun addThought(text: String, category: String? = null): NoteEntity {
        val note = NoteEntity(type = NoteType.THOUGHT, body = text.trim(), category = category)
        save(note)
        return note
    }

    private suspend fun update(id: String, transform: (NoteEntity) -> NoteEntity) {
        val current = dao.get(id) ?: return
        dao.upsert(transform(current).copy(updatedAt = TimeUtils.nowMillis()))
        widgets.requestUpdate()
    }

    suspend fun setPinned(id: String, pinned: Boolean) = update(id) { it.copy(isPinned = pinned) }

    suspend fun setCategory(id: String, category: String?) = update(id) { it.copy(category = category) }

    suspend fun setArchived(id: String, archived: Boolean) = update(id) { it.copy(isArchived = archived) }

    suspend fun moveToTrash(id: String) =
        update(id) { it.copy(trashedAt = TimeUtils.nowMillis(), isPinned = false) }

    suspend fun restore(id: String) = update(id) { it.copy(trashedAt = null) }

    suspend fun deleteForever(id: String) {
        attachments.removeAllFor(OwnerType.NOTE, id)
        dao.delete(id)
        widgets.requestUpdate()
    }

    suspend fun emptyTrash() {
        dao.getTrashedIds().forEach { deleteForever(it) }
    }

    /** Räumt Papierkorb-Einträge auf, die älter als [days] Tage sind. */
    suspend fun purgeTrashOlderThan(days: Int) {
        val before = TimeUtils.nowMillis() - days * TimeUtils.DAY_MS
        dao.getTrashedIdsBefore(before).forEach { deleteForever(it) }
    }
}
