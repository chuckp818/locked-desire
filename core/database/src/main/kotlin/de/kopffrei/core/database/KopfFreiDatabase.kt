package de.kopffrei.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import de.kopffrei.core.database.dao.AttachmentDao
import de.kopffrei.core.database.dao.CalendarEventDao
import de.kopffrei.core.database.dao.DraftDao
import de.kopffrei.core.database.dao.JournalDao
import de.kopffrei.core.database.dao.MoodDao
import de.kopffrei.core.database.dao.NoteDao
import de.kopffrei.core.database.dao.ReminderDao
import de.kopffrei.core.database.dao.TaskDao
import de.kopffrei.core.database.entity.AttachmentEntity
import de.kopffrei.core.database.entity.CalendarEventEntity
import de.kopffrei.core.database.entity.DraftEntity
import de.kopffrei.core.database.entity.JournalEntryEntity
import de.kopffrei.core.database.entity.MoodEntryEntity
import de.kopffrei.core.database.entity.NoteEntity
import de.kopffrei.core.database.entity.ReminderEntity
import de.kopffrei.core.database.entity.TaskEntity
import de.kopffrei.core.database.entity.TaskStepEntity

/**
 * Lokale Datenbank. Bei jeder Schemaänderung: Version erhöhen und hier eine [Migration] ergänzen.
 * Es wird bewusst KEINE destruktive Migration verwendet – Nutzerdaten dürfen nie stillschweigend
 * verloren gehen.
 */
@Database(
    entities = [
        NoteEntity::class,
        AttachmentEntity::class,
        JournalEntryEntity::class,
        MoodEntryEntity::class,
        TaskEntity::class,
        TaskStepEntity::class,
        CalendarEventEntity::class,
        ReminderEntity::class,
        DraftEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class KopfFreiDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun journalDao(): JournalDao
    abstract fun moodDao(): MoodDao
    abstract fun taskDao(): TaskDao
    abstract fun calendarEventDao(): CalendarEventDao
    abstract fun reminderDao(): ReminderDao
    abstract fun draftDao(): DraftDao

    companion object {
        const val NAME = "kopffrei.db"
    }
}

/** Sammelstelle für alle Migrationen (Version 1 ist der Anfang, daher noch leer). */
object DatabaseMigrations {
    val ALL: Array<Migration> = arrayOf()
}
