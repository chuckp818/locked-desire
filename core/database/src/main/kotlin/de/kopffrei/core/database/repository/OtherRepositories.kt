package de.kopffrei.core.database.repository

import de.kopffrei.core.common.model.Mood
import de.kopffrei.core.common.model.OwnerType
import de.kopffrei.core.common.text.TextUtils
import de.kopffrei.core.common.time.TimeUtils
import de.kopffrei.core.database.dao.DraftDao
import de.kopffrei.core.database.dao.JournalDao
import de.kopffrei.core.database.dao.MoodDao
import de.kopffrei.core.database.dao.ReminderDao
import de.kopffrei.core.database.entity.DraftEntity
import de.kopffrei.core.database.entity.JournalEntryEntity
import de.kopffrei.core.database.entity.MoodEntryEntity
import de.kopffrei.core.database.entity.ReminderEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JournalRepository @Inject constructor(
    private val dao: JournalDao,
    private val attachments: AttachmentRepository,
) {
    fun observeAll(query: String = ""): Flow<List<JournalEntryEntity>> =
        dao.observeAll(TextUtils.likePattern(query))

    suspend fun get(id: String): JournalEntryEntity? = dao.get(id)

    suspend fun getAll(): List<JournalEntryEntity> = dao.getAll()

    suspend fun save(entry: JournalEntryEntity) {
        dao.upsert(entry.copy(updatedAt = TimeUtils.nowMillis()))
    }

    suspend fun delete(id: String) {
        attachments.removeAllFor(OwnerType.JOURNAL, id)
        dao.delete(id)
    }
}

/** Stimmung des Tages – immer freiwillig, ein Eintrag je Kalendertag. */
@Singleton
class MoodRepository @Inject constructor(private val dao: MoodDao) {
    fun observeToday(): Flow<MoodEntryEntity?> =
        dao.observeForDay(TimeUtils.epochDay(TimeUtils.nowMillis(), TimeUtils.systemZone()))

    suspend fun setToday(mood: Mood) {
        val day = TimeUtils.epochDay(TimeUtils.nowMillis(), TimeUtils.systemZone())
        dao.insert(MoodEntryEntity(mood = mood, epochDay = day))
    }

    suspend fun clearToday() {
        dao.deleteForDay(TimeUtils.epochDay(TimeUtils.nowMillis(), TimeUtils.systemZone()))
    }
}

/** Automatisch gespeicherte Entwürfe. */
@Singleton
class DraftRepository @Inject constructor(private val dao: DraftDao) {
    suspend fun get(kind: String): String? = dao.get(kind)?.text

    suspend fun save(kind: String, text: String) {
        if (text.isBlank()) dao.delete(kind) else dao.upsert(DraftEntity(kind, text))
    }

    suspend fun clear(kind: String) = dao.delete(kind)

    companion object {
        const val KIND_BRAIN_DUMP = "BRAIN_DUMP"
    }
}

/** Reiner Datenzugriff auf Erinnerungen. Planung und Auslösung liegen in core:reminders. */
@Singleton
class ReminderRepository @Inject constructor(private val dao: ReminderDao) {
    suspend fun get(id: String): ReminderEntity? = dao.get(id)

    suspend fun getAll(): List<ReminderEntity> = dao.getAll()

    suspend fun getNotDone(): List<ReminderEntity> = dao.getNotDone()

    suspend fun getForOwner(ownerType: OwnerType, ownerId: String): List<ReminderEntity> =
        dao.getForOwner(ownerType, ownerId)

    fun observeFired(): Flow<List<ReminderEntity>> = dao.observeFired()

    suspend fun save(reminder: ReminderEntity) = dao.upsert(reminder)

    suspend fun delete(id: String) = dao.delete(id)

    suspend fun deleteForOwner(ownerType: OwnerType, ownerId: String) =
        dao.deleteForOwner(ownerType, ownerId)
}
