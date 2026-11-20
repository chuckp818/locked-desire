package de.kopffrei.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import de.kopffrei.core.common.model.NoteType
import de.kopffrei.core.common.model.OwnerType
import de.kopffrei.core.database.entity.AttachmentEntity
import de.kopffrei.core.database.entity.CalendarEventEntity
import de.kopffrei.core.database.entity.DraftEntity
import de.kopffrei.core.database.entity.JournalEntryEntity
import de.kopffrei.core.database.entity.MoodEntryEntity
import de.kopffrei.core.database.entity.NoteEntity
import de.kopffrei.core.database.entity.ReminderEntity
import de.kopffrei.core.database.entity.StepProgress
import de.kopffrei.core.database.entity.TaskEntity
import de.kopffrei.core.database.entity.TaskStepEntity
import kotlinx.coroutines.flow.Flow

// Suchmuster `:like` kommen aus TextUtils.likePattern (Sonderzeichen mit \ maskiert).

@Dao
interface NoteDao {
    @Query(
        "SELECT * FROM notes WHERE type = :type AND trashedAt IS NULL AND isArchived = 0 AND " +
            "(title LIKE :like ESCAPE '\\' OR body LIKE :like ESCAPE '\\' OR IFNULL(category, '') LIKE :like ESCAPE '\\') " +
            "ORDER BY isPinned DESC, updatedAt DESC",
    )
    fun observeActive(type: NoteType, like: String): Flow<List<NoteEntity>>

    @Query(
        "SELECT * FROM notes WHERE type = :type AND trashedAt IS NULL AND isArchived = 1 AND " +
            "(title LIKE :like ESCAPE '\\' OR body LIKE :like ESCAPE '\\' OR IFNULL(category, '') LIKE :like ESCAPE '\\') " +
            "ORDER BY updatedAt DESC",
    )
    fun observeArchived(type: NoteType, like: String): Flow<List<NoteEntity>>

    @Query(
        "SELECT * FROM notes WHERE type = :type AND trashedAt IS NOT NULL AND " +
            "(title LIKE :like ESCAPE '\\' OR body LIKE :like ESCAPE '\\' OR IFNULL(category, '') LIKE :like ESCAPE '\\') " +
            "ORDER BY trashedAt DESC",
    )
    fun observeTrashed(type: NoteType, like: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE type = 'THOUGHT' AND trashedAt IS NULL ORDER BY createdAt DESC LIMIT :limit")
    fun observeLatestThoughts(limit: Int): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE type = 'THOUGHT' AND trashedAt IS NULL ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getLatestThoughts(limit: Int): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE type = 'NOTE' AND isPinned = 1 AND trashedAt IS NULL ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getLatestPinned(): NoteEntity?

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun get(id: String): NoteEntity?

    @Query("SELECT * FROM notes WHERE id = :id")
    fun observe(id: String): Flow<NoteEntity?>

    @Query("SELECT * FROM notes")
    suspend fun getAll(): List<NoteEntity>

    @Upsert
    suspend fun upsert(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT id FROM notes WHERE trashedAt IS NOT NULL")
    suspend fun getTrashedIds(): List<String>

    @Query("SELECT id FROM notes WHERE trashedAt IS NOT NULL AND trashedAt < :before")
    suspend fun getTrashedIdsBefore(before: Long): List<String>
}

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachments WHERE ownerType = :ownerType AND ownerId = :ownerId ORDER BY createdAt")
    fun observeFor(ownerType: OwnerType, ownerId: String): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE ownerType = :ownerType AND ownerId = :ownerId ORDER BY createdAt")
    suspend fun getFor(ownerType: OwnerType, ownerId: String): List<AttachmentEntity>

    @Query("SELECT * FROM attachments WHERE id = :id")
    suspend fun get(id: String): AttachmentEntity?

    @Query("SELECT * FROM attachments")
    suspend fun getAll(): List<AttachmentEntity>

    @Upsert
    suspend fun upsert(attachment: AttachmentEntity)

    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM attachments WHERE ownerType = :ownerType AND ownerId = :ownerId")
    suspend fun deleteFor(ownerType: OwnerType, ownerId: String)
}

@Dao
interface JournalDao {
    @Query(
        "SELECT * FROM journal_entries WHERE " +
            "(title LIKE :like ESCAPE '\\' OR text LIKE :like ESCAPE '\\' OR tags LIKE :like ESCAPE '\\') " +
            "ORDER BY createdAt DESC",
    )
    fun observeAll(like: String): Flow<List<JournalEntryEntity>>

    @Query("SELECT * FROM journal_entries WHERE id = :id")
    suspend fun get(id: String): JournalEntryEntity?

    @Query("SELECT * FROM journal_entries ORDER BY createdAt")
    suspend fun getAll(): List<JournalEntryEntity>

    @Upsert
    suspend fun upsert(entry: JournalEntryEntity)

    @Query("DELETE FROM journal_entries WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface MoodDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: MoodEntryEntity)

    @Query("SELECT * FROM mood_entries WHERE epochDay = :epochDay")
    fun observeForDay(epochDay: Long): Flow<MoodEntryEntity?>

    @Query("DELETE FROM mood_entries WHERE epochDay = :epochDay")
    suspend fun deleteForDay(epochDay: Long)

    @Query("SELECT * FROM mood_entries ORDER BY epochDay")
    suspend fun getAll(): List<MoodEntryEntity>
}

@Dao
abstract class TaskDao {
    @Query("SELECT * FROM tasks")
    abstract fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE status = 'OPEN'")
    abstract suspend fun getOpen(): List<TaskEntity>

    @Query("SELECT * FROM tasks")
    abstract suspend fun getAll(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :id")
    abstract suspend fun get(id: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE id = :id")
    abstract fun observe(id: String): Flow<TaskEntity?>

    @Upsert
    abstract suspend fun upsert(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    abstract suspend fun delete(id: String)

    @Query("SELECT * FROM task_steps WHERE taskId = :taskId ORDER BY position, createdAt")
    abstract suspend fun getSteps(taskId: String): List<TaskStepEntity>

    @Query("SELECT * FROM task_steps WHERE taskId = :taskId ORDER BY position, createdAt")
    abstract fun observeSteps(taskId: String): Flow<List<TaskStepEntity>>

    @Query("SELECT * FROM task_steps ORDER BY taskId, position")
    abstract suspend fun getAllSteps(): List<TaskStepEntity>

    @Query("SELECT taskId, COUNT(*) AS total, SUM(isDone) AS done FROM task_steps GROUP BY taskId")
    abstract fun observeStepProgress(): Flow<List<StepProgress>>

    @Upsert
    abstract suspend fun upsertSteps(steps: List<TaskStepEntity>)

    @Query("UPDATE task_steps SET isDone = :done WHERE id = :stepId")
    abstract suspend fun setStepDone(stepId: String, done: Boolean)

    @Query("UPDATE task_steps SET isDone = 0 WHERE taskId = :taskId")
    abstract suspend fun resetSteps(taskId: String)

    @Query("DELETE FROM task_steps WHERE taskId = :taskId")
    abstract suspend fun deleteSteps(taskId: String)

    /** Aufgabe und Unteraufgaben in einer Transaktion speichern. */
    @Transaction
    open suspend fun saveWithSteps(task: TaskEntity, steps: List<TaskStepEntity>) {
        upsert(task)
        deleteSteps(task.id)
        upsertSteps(steps)
    }
}

@Dao
interface CalendarEventDao {
    @Query("SELECT * FROM calendar_events")
    fun observeAll(): Flow<List<CalendarEventEntity>>

    @Query("SELECT * FROM calendar_events")
    suspend fun getAll(): List<CalendarEventEntity>

    /**
     * Einmalige Termine, die den Zeitraum berühren, plus alle Serien, die vor Ende des Zeitraums
     * beginnen und nicht schon beendet sind (die Wiederholungen werden anschließend berechnet).
     */
    @Query(
        "SELECT * FROM calendar_events WHERE " +
            "(rec_frequency = 'NONE' AND startAt < :to AND (endAt > :from OR startAt >= :from)) OR " +
            "(rec_frequency != 'NONE' AND startAt < :to AND (rec_until IS NULL OR rec_until >= :from))",
    )
    fun observeBetween(from: Long, to: Long): Flow<List<CalendarEventEntity>>

    @Query("SELECT * FROM calendar_events WHERE id = :id")
    suspend fun get(id: String): CalendarEventEntity?

    @Upsert
    suspend fun upsert(event: CalendarEventEntity)

    @Upsert
    suspend fun upsertAll(events: List<CalendarEventEntity>)

    @Query("DELETE FROM calendar_events WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun get(id: String): ReminderEntity?

    @Query("SELECT * FROM reminders")
    suspend fun getAll(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE state != 'DONE'")
    suspend fun getNotDone(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE ownerType = :ownerType AND ownerId = :ownerId ORDER BY createdAt")
    suspend fun getForOwner(ownerType: OwnerType, ownerId: String): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE state = 'FIRED' ORDER BY lastFiredAt DESC")
    fun observeFired(): Flow<List<ReminderEntity>>

    @Upsert
    suspend fun upsert(reminder: ReminderEntity)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM reminders WHERE ownerType = :ownerType AND ownerId = :ownerId")
    suspend fun deleteForOwner(ownerType: OwnerType, ownerId: String)
}

@Dao
interface DraftDao {
    @Query("SELECT * FROM drafts WHERE kind = :kind")
    suspend fun get(kind: String): DraftEntity?

    @Upsert
    suspend fun upsert(draft: DraftEntity)

    @Query("DELETE FROM drafts WHERE kind = :kind")
    suspend fun delete(kind: String)

    @Query("SELECT * FROM drafts")
    suspend fun getAll(): List<DraftEntity>
}
