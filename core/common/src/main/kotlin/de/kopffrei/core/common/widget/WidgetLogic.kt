package de.kopffrei.core.common.widget

import de.kopffrei.core.common.checklist.Checklist
import de.kopffrei.core.common.settings.UserSettings
import de.kopffrei.core.common.tasks.TaskLogic
import de.kopffrei.core.common.tasks.TaskView
import de.kopffrei.core.common.text.DateText
import de.kopffrei.core.common.text.TextUtils
import java.time.ZoneId

/**
 * Reine Textlogik für die Startbildschirm-Widgets – und die Datenschutzgrenze: Ist die App-Sperre aktiv,
 * zeigen Widgets keine persönlichen Inhalte (Titel, Notizen, Gedanken), sondern nur einen neutralen Hinweis.
 * Widgets zum Erfassen (Schnellnotiz, Einsprechen, Fokus-Start) zeigen ohnehin keine Inhalte.
 */
object WidgetLogic {
    const val LOCKED_TEXT = "Gesperrt – zum Anzeigen KopfFrei öffnen"

    /** Inhalte dürfen nur angezeigt werden, wenn keine App-Sperre aktiv ist. */
    fun showsContent(settings: UserSettings): Boolean = !settings.biometricLock

    /** „14:30 Zahnarzt“ bzw. „Ganztägig · Geburtstag“. */
    fun eventLine(title: String, startMillis: Long, allDay: Boolean, zone: ZoneId): String =
        if (allDay) "Ganztägig · $title" else "${DateText.clock(startMillis, zone)} $title"

    /** Titel mit kurzem Zusatz: „· überfällig“, „· heute“ bzw. „· heute 15:00“. */
    fun taskLine(task: TaskView, now: Long, zone: ZoneId): String {
        val suffix = when {
            TaskLogic.isOverdue(task, now, zone) -> " · überfällig"
            TaskLogic.isDueToday(task, now, zone) -> {
                val due = task.dueAt
                if (task.dueHasTime && due != null) " · heute ${DateText.clock(due, zone)}" else " · heute"
            }
            else -> ""
        }
        return task.title + suffix
    }

    /** Vorschau einer angehefteten Notiz: Checklisten als Zeilen mit Häkchen, sonst gekürzter Text. */
    fun notePreview(body: String, isChecklist: Boolean, maxChars: Int, maxChecklistItems: Int): String =
        if (isChecklist) Checklist.preview(body, maxChecklistItems) else TextUtils.truncate(body.trim(), maxChars)

    /** Wie viele Zeilen passen in die Widget-Höhe? Mindestens 1, höchstens [max]. */
    fun visibleItems(heightDp: Int, headerDp: Int, itemDp: Int, max: Int): Int {
        if (itemDp <= 0) return 1
        return ((heightDp - headerDp) / itemDp).coerceIn(1, max)
    }
}
