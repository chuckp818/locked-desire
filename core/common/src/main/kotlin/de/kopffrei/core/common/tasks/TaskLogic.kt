package de.kopffrei.core.common.tasks

import de.kopffrei.core.common.model.TaskStatus
import de.kopffrei.core.common.time.TimeUtils
import java.time.ZoneId

/** Schlanke, rein logische Sicht auf eine Aufgabe (unabhängig von Room), damit sie lokal testbar ist. */
data class TaskView(
    val id: String,
    val title: String,
    val notes: String = "",
    val priority: Int = 1,
    val dueAt: Long? = null,
    val dueHasTime: Boolean = false,
    val estimatedMinutes: Int? = null,
    val status: TaskStatus = TaskStatus.OPEN,
    val completedAt: Long? = null,
)

/** Filter der Aufgabenliste. */
enum class TaskFilter(val label: String) {
    OPEN("Offen"),
    TODAY("Heute"),
    OVERDUE("Überfällig"),
    WEEK("Diese Woche"),
    NO_DATE("Ohne Datum"),
    PAUSED("Pausiert"),
    DONE("Erledigt"),
}

/** Ranking, Filter und Suche für Aufgaben. Keine Strafen, keine Serien – nur Reihenfolge. */
object TaskLogic {

    fun isOverdue(task: TaskView, now: Long, zone: ZoneId): Boolean {
        val due = task.dueAt ?: return false
        if (task.status != TaskStatus.OPEN) return false
        return if (task.dueHasTime) due < now else due < TimeUtils.startOfDay(now, zone)
    }

    fun isDueToday(task: TaskView, now: Long, zone: ZoneId): Boolean {
        val due = task.dueAt ?: return false
        return TimeUtils.toLocalDate(due, zone) == TimeUtils.toLocalDate(now, zone)
    }

    /** Fällig in den nächsten sieben Tagen (heute eingeschlossen), noch nicht überfällig. */
    fun isDueThisWeek(task: TaskView, now: Long, zone: ZoneId): Boolean {
        val due = task.dueAt ?: return false
        val today = TimeUtils.toLocalDate(now, zone)
        val date = TimeUtils.toLocalDate(due, zone)
        return !date.isBefore(today) && date.isBefore(today.plusDays(7))
    }

    fun matches(task: TaskView, filter: TaskFilter, now: Long, zone: ZoneId): Boolean = when (filter) {
        TaskFilter.OPEN -> task.status == TaskStatus.OPEN
        TaskFilter.TODAY -> task.status == TaskStatus.OPEN && isDueToday(task, now, zone)
        TaskFilter.OVERDUE -> isOverdue(task, now, zone)
        TaskFilter.WEEK -> task.status == TaskStatus.OPEN && isDueThisWeek(task, now, zone)
        TaskFilter.NO_DATE -> task.status == TaskStatus.OPEN && task.dueAt == null
        TaskFilter.PAUSED -> task.status == TaskStatus.PAUSED
        TaskFilter.DONE -> task.status == TaskStatus.DONE
    }

    /** Volltextsuche in Titel und Notizen, Groß-/Kleinschreibung egal; leere Suche findet alles. */
    fun matchesQuery(task: TaskView, query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        return task.title.contains(q, ignoreCase = true) || task.notes.contains(q, ignoreCase = true)
    }

    private fun urgencyBucket(task: TaskView, now: Long, zone: ZoneId): Int = when {
        isOverdue(task, now, zone) -> 0
        isDueToday(task, now, zone) -> 1
        else -> 2
    }

    /**
     * Reihenfolge offener Aufgaben: überfällig, heute fällig, dann nach Priorität, Fälligkeit, kurze
     * Aufgaben zuerst (leichter Einstieg), zuletzt alphabetisch.
     */
    private fun rankingComparator(now: Long, zone: ZoneId): Comparator<TaskView> =
        compareBy<TaskView>(
            { urgencyBucket(it, now, zone) },
            { -it.priority },
            { it.dueAt ?: Long.MAX_VALUE },
            { it.estimatedMinutes ?: Int.MAX_VALUE },
            { it.title.lowercase() },
        )

    /** Die wichtigsten offenen Aufgaben („Drei wichtigste Aufgaben“). */
    fun rank(tasks: List<TaskView>, now: Long, zone: ZoneId, limit: Int = 3): List<TaskView> =
        tasks.filter { it.status == TaskStatus.OPEN }
            .sortedWith(rankingComparator(now, zone))
            .take(limit)

    /** Sortierung für die Listenansicht: Erledigte nach Erledigungszeit, alle anderen wie beim Ranking. */
    fun sortForList(tasks: List<TaskView>, now: Long, zone: ZoneId): List<TaskView> {
        val (done, rest) = tasks.partition { it.status == TaskStatus.DONE }
        return rest.sortedWith(rankingComparator(now, zone)) + done.sortedByDescending { it.completedAt ?: 0L }
    }
}
