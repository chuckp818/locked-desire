package de.kopffrei.core.database.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import de.kopffrei.core.common.model.Mood
import de.kopffrei.core.common.model.NoteType
import de.kopffrei.core.common.model.OwnerType
import de.kopffrei.core.common.model.Priority
import de.kopffrei.core.common.model.RecurrenceRule
import de.kopffrei.core.common.model.ReminderState
import de.kopffrei.core.common.model.TaskStatus
import de.kopffrei.core.common.time.Ids
import de.kopffrei.core.common.time.TimeUtils

// Alle IDs sind UUIDs (Text), alle Zeitstempel Millisekunden seit 1970 (UTC).

/** Notiz oder Gedanke (Gedanken-Parkplatz). Checklisten stehen als Text im [body]. */
@Entity(tableName = "notes", indices = [Index("type"), Index("trashedAt")])
data class NoteEntity(
    @PrimaryKey val id: String = Ids.newId(),
    val type: NoteType = NoteType.NOTE,
    val title: String = "",
    val body: String = "",
    val isChecklist: Boolean = false,
    val colorIndex: Int = 0,
    val category: String? = null,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    /** Gesetzt = im Papierkorb. */
    val trashedAt: Long? = null,
    val createdAt: Long = TimeUtils.nowMillis(),
    val updatedAt: Long = createdAt,
)

/**
 * Anhang (Bild oder Datei) zu einer Notiz, einem Tagebucheintrag oder einem Termin.
 * Allgemeinere Form von „NoteAttachments“ aus dem Auftrag. Die Datei liegt in der App
 * (filesDir), [filePath] ist relativ dazu.
 */
@Entity(tableName = "attachments", indices = [Index("ownerType", "ownerId")])
data class AttachmentEntity(
    @PrimaryKey val id: String = Ids.newId(),
    val ownerType: OwnerType,
    val ownerId: String,
    val filePath: String,
    val mimeType: String,
    val displayName: String,
    val sizeBytes: Long,
    val createdAt: Long = TimeUtils.nowMillis(),
)

@Entity(tableName = "journal_entries", indices = [Index("createdAt")])
data class JournalEntryEntity(
    @PrimaryKey val id: String = Ids.newId(),
    val title: String = "",
    val text: String = "",
    val mood: Mood? = null,
    /** Kommagetrennte Tags. */
    val tags: String = "",
    val createdAt: Long = TimeUtils.nowMillis(),
    val updatedAt: Long = createdAt,
)

/** Stimmung des Tages (höchstens ein Eintrag je Kalendertag). */
@Entity(tableName = "mood_entries", indices = [Index(value = ["epochDay"], unique = true)])
data class MoodEntryEntity(
    @PrimaryKey val id: String = Ids.newId(),
    val mood: Mood,
    val epochDay: Long,
    val createdAt: Long = TimeUtils.nowMillis(),
)

@Entity(tableName = "tasks", indices = [Index("status"), Index("dueAt")])
data class TaskEntity(
    @PrimaryKey val id: String = Ids.newId(),
    val title: String,
    val notes: String = "",
    /** 0 = niedrig … 3 = dringend, siehe [Priority]. */
    val priority: Int = Priority.NORMAL,
    /** Fälligkeit; ohne Uhrzeit (dueHasTime = false) ist es der Beginn des Tages. */
    val dueAt: Long? = null,
    val dueHasTime: Boolean = false,
    val estimatedMinutes: Int? = null,
    val status: TaskStatus = TaskStatus.OPEN,
    @Embedded(prefix = "rec_") val recurrence: RecurrenceRule = RecurrenceRule.NONE,
    val completedAt: Long? = null,
    val sourceNoteId: String? = null,
    val createdAt: Long = TimeUtils.nowMillis(),
    val updatedAt: Long = createdAt,
)

@Entity(
    tableName = "task_steps",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("taskId")],
)
data class TaskStepEntity(
    @PrimaryKey val id: String = Ids.newId(),
    val taskId: String,
    val title: String,
    val isDone: Boolean = false,
    val position: Int = 0,
    val createdAt: Long = TimeUtils.nowMillis(),
)

@Entity(tableName = "calendar_events", indices = [Index("startAt")])
data class CalendarEventEntity(
    @PrimaryKey val id: String = Ids.newId(),
    val title: String,
    val notes: String = "",
    val location: String = "",
    val allDay: Boolean = false,
    val startAt: Long,
    /** Bei ganztägigen Terminen der (exklusive) Beginn des Folgetags. */
    val endAt: Long,
    val colorIndex: Int = 0,
    val category: String? = null,
    @Embedded(prefix = "rec_") val recurrence: RecurrenceRule = RecurrenceRule.NONE,
    val prepMinutes: Int = 0,
    val travelMinutes: Int = 0,
    val linkedTaskId: String? = null,
    val createdAt: Long = TimeUtils.nowMillis(),
    val updatedAt: Long = createdAt,
)

/**
 * Eine Erinnerungsstufe. Entweder [offsetMinutes] vor Termin/Fälligkeit des Besitzers oder ein fester
 * Zeitpunkt [fixedAt]. Zustandsfelder steuern Bestätigung, „Später“ und automatische Wiederholung.
 */
@Entity(tableName = "reminders", indices = [Index("ownerType", "ownerId"), Index("state")])
data class ReminderEntity(
    @PrimaryKey val id: String = Ids.newId(),
    val ownerType: OwnerType,
    val ownerId: String,
    val label: String = "",
    val offsetMinutes: Int? = null,
    val fixedAt: Long? = null,
    val repeatEveryMinutes: Int? = null,
    val state: ReminderState = ReminderState.PENDING,
    val snoozedUntil: Long? = null,
    /** Zeitpunkt, für den zuletzt ausgelöst wurde (verhindert doppelte Auslösung). */
    val lastTriggeredAt: Long? = null,
    val lastFiredAt: Long? = null,
    val repeatsDone: Int = 0,
    val createdAt: Long = TimeUtils.nowMillis(),
)

/** Automatisch gespeicherter Entwurf (z. B. „Kopf ausschütten“), je Art höchstens einer. */
@Entity(tableName = "drafts")
data class DraftEntity(
    @PrimaryKey val kind: String,
    val text: String,
    val updatedAt: Long = TimeUtils.nowMillis(),
)

/** Ergebnis der Fortschrittsabfrage für Unteraufgaben (kein eigener Tabelleneintrag). */
data class StepProgress(
    val taskId: String,
    val total: Int,
    val done: Int,
)
