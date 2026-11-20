package de.kopffrei.core.database

import android.util.JsonWriter
import de.kopffrei.core.common.WidgetUpdater
import de.kopffrei.core.common.time.TimeUtils
import de.kopffrei.core.database.repository.AttachmentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.io.OutputStreamWriter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Datenexport (Backup als JSON) und vollständiges Löschen. Der Export enthält die Metadaten der Anhänge,
 * aber nicht die Anhangsdateien selbst. Die Datei ist NICHT verschlüsselt.
 */
@Singleton
class DataManagement @Inject constructor(
    private val database: KopfFreiDatabase,
    private val attachments: AttachmentRepository,
    private val widgets: WidgetUpdater,
) {
    suspend fun exportJson(output: OutputStream) = withContext(Dispatchers.IO) {
        JsonWriter(OutputStreamWriter(output, Charsets.UTF_8)).use { w ->
            w.setIndent("  ")
            w.beginObject()
            w.field("app", "KopfFrei")
            w.field("exportVersion", 1)
            w.field("exportedAt", TimeUtils.nowMillis())

            w.array("notes") {
                database.noteDao().getAll().forEach { n ->
                    obj {
                        field("id", n.id); field("type", n.type.name); field("title", n.title)
                        field("body", n.body); field("isChecklist", n.isChecklist)
                        field("colorIndex", n.colorIndex); field("category", n.category)
                        field("isPinned", n.isPinned); field("isArchived", n.isArchived)
                        field("trashedAt", n.trashedAt); field("createdAt", n.createdAt)
                        field("updatedAt", n.updatedAt)
                    }
                }
            }
            w.array("attachments") {
                database.attachmentDao().getAll().forEach { a ->
                    obj {
                        field("id", a.id); field("ownerType", a.ownerType.name); field("ownerId", a.ownerId)
                        field("displayName", a.displayName); field("mimeType", a.mimeType)
                        field("sizeBytes", a.sizeBytes); field("createdAt", a.createdAt)
                    }
                }
            }
            w.array("journalEntries") {
                database.journalDao().getAll().forEach { j ->
                    obj {
                        field("id", j.id); field("title", j.title); field("text", j.text)
                        field("mood", j.mood?.name); field("tags", j.tags)
                        field("createdAt", j.createdAt); field("updatedAt", j.updatedAt)
                    }
                }
            }
            w.array("moodEntries") {
                database.moodDao().getAll().forEach { m ->
                    obj {
                        field("id", m.id); field("mood", m.mood.name)
                        field("epochDay", m.epochDay); field("createdAt", m.createdAt)
                    }
                }
            }
            w.array("tasks") {
                database.taskDao().getAll().forEach { t ->
                    obj {
                        field("id", t.id); field("title", t.title); field("notes", t.notes)
                        field("priority", t.priority); field("dueAt", t.dueAt); field("dueHasTime", t.dueHasTime)
                        field("estimatedMinutes", t.estimatedMinutes); field("status", t.status.name)
                        field("recurrence", t.recurrence.frequency.name)
                        field("recurrenceInterval", t.recurrence.interval)
                        field("recurrenceUntil", t.recurrence.until)
                        field("completedAt", t.completedAt); field("sourceNoteId", t.sourceNoteId)
                        field("createdAt", t.createdAt); field("updatedAt", t.updatedAt)
                    }
                }
            }
            w.array("taskSteps") {
                database.taskDao().getAllSteps().forEach { s ->
                    obj {
                        field("id", s.id); field("taskId", s.taskId); field("title", s.title)
                        field("isDone", s.isDone); field("position", s.position)
                    }
                }
            }
            w.array("calendarEvents") {
                database.calendarEventDao().getAll().forEach { e ->
                    obj {
                        field("id", e.id); field("title", e.title); field("notes", e.notes)
                        field("location", e.location); field("allDay", e.allDay)
                        field("startAt", e.startAt); field("endAt", e.endAt)
                        field("colorIndex", e.colorIndex); field("category", e.category)
                        field("recurrence", e.recurrence.frequency.name)
                        field("recurrenceInterval", e.recurrence.interval)
                        field("recurrenceUntil", e.recurrence.until)
                        field("prepMinutes", e.prepMinutes); field("travelMinutes", e.travelMinutes)
                        field("linkedTaskId", e.linkedTaskId)
                        field("createdAt", e.createdAt); field("updatedAt", e.updatedAt)
                    }
                }
            }
            w.array("reminders") {
                database.reminderDao().getAll().forEach { r ->
                    obj {
                        field("id", r.id); field("ownerType", r.ownerType.name); field("ownerId", r.ownerId)
                        field("label", r.label); field("offsetMinutes", r.offsetMinutes)
                        field("fixedAt", r.fixedAt); field("repeatEveryMinutes", r.repeatEveryMinutes)
                        field("state", r.state.name)
                    }
                }
            }
            w.array("drafts") {
                database.draftDao().getAll().forEach { d ->
                    obj { field("kind", d.kind); field("text", d.text); field("updatedAt", d.updatedAt) }
                }
            }
            w.endObject()
        }
    }

    /** Löscht alle Tabellen und alle Anhangsdateien. Alarme müssen vorher abgebrochen werden. */
    suspend fun deleteEverything() = withContext(Dispatchers.IO) {
        database.clearAllTables()
        attachments.deleteAllFiles()
        widgets.requestUpdate()
    }
}

private fun JsonWriter.field(key: String, v: Any?) {
    name(key)
    when (v) {
        null -> nullValue()
        is Boolean -> value(v)
        is Number -> value(v)
        else -> value(v.toString())
    }
}

private inline fun JsonWriter.obj(block: JsonWriter.() -> Unit) {
    beginObject()
    block()
    endObject()
}

private inline fun JsonWriter.array(key: String, block: JsonWriter.() -> Unit) {
    name(key)
    beginArray()
    block()
    endArray()
}
