package de.kopffrei.core.common

import de.kopffrei.core.common.model.TaskStatus
import de.kopffrei.core.common.tasks.TaskFilter
import de.kopffrei.core.common.tasks.TaskLogic
import de.kopffrei.core.common.tasks.TaskView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskLogicTest {
    private val now = ms(2026, 3, 12, 10, 0) // Donnerstag
    private val today = ms(2026, 3, 12)
    private val yesterday = ms(2026, 3, 11)
    private val tomorrow = ms(2026, 3, 13)

    private fun task(
        id: String,
        priority: Int = 1,
        dueAt: Long? = null,
        hasTime: Boolean = false,
        minutes: Int? = null,
        status: TaskStatus = TaskStatus.OPEN,
        title: String = id,
        notes: String = "",
        completedAt: Long? = null,
    ) = TaskView(id, title, notes, priority, dueAt, hasTime, minutes, status, completedAt)

    @Test
    fun `Aufgabe von gestern ohne Uhrzeit ist ueberfaellig, heutige nicht`() {
        assertTrue(TaskLogic.isOverdue(task("a", dueAt = yesterday), now, BERLIN))
        assertFalse(TaskLogic.isOverdue(task("b", dueAt = today), now, BERLIN))
    }

    @Test
    fun `Aufgabe mit Uhrzeit ist ueberfaellig sobald die Zeit vorbei ist`() {
        assertTrue(TaskLogic.isOverdue(task("a", dueAt = ms(2026, 3, 12, 9, 0), hasTime = true), now, BERLIN))
        assertFalse(TaskLogic.isOverdue(task("b", dueAt = ms(2026, 3, 12, 18, 0), hasTime = true), now, BERLIN))
    }

    @Test
    fun `Erledigte und pausierte Aufgaben sind nie ueberfaellig`() {
        assertFalse(TaskLogic.isOverdue(task("a", dueAt = yesterday, status = TaskStatus.DONE), now, BERLIN))
        assertFalse(TaskLogic.isOverdue(task("b", dueAt = yesterday, status = TaskStatus.PAUSED), now, BERLIN))
    }

    @Test
    fun `Filter Heute Woche und Ohne Datum`() {
        val t = task("t", dueAt = today)
        val inThreeDays = task("w", dueAt = ms(2026, 3, 15))
        val inEightDays = task("x", dueAt = ms(2026, 3, 20))
        val none = task("n")
        assertTrue(TaskLogic.matches(t, TaskFilter.TODAY, now, BERLIN))
        assertFalse(TaskLogic.matches(inThreeDays, TaskFilter.TODAY, now, BERLIN))
        assertTrue(TaskLogic.matches(inThreeDays, TaskFilter.WEEK, now, BERLIN))
        assertFalse(TaskLogic.matches(inEightDays, TaskFilter.WEEK, now, BERLIN))
        assertTrue(TaskLogic.matches(none, TaskFilter.NO_DATE, now, BERLIN))
        assertFalse(TaskLogic.matches(t, TaskFilter.NO_DATE, now, BERLIN))
    }

    @Test
    fun `Filter Status`() {
        val done = task("d", status = TaskStatus.DONE)
        val paused = task("p", status = TaskStatus.PAUSED)
        val open = task("o")
        assertTrue(TaskLogic.matches(done, TaskFilter.DONE, now, BERLIN))
        assertFalse(TaskLogic.matches(done, TaskFilter.OPEN, now, BERLIN))
        assertTrue(TaskLogic.matches(paused, TaskFilter.PAUSED, now, BERLIN))
        assertFalse(TaskLogic.matches(paused, TaskFilter.OPEN, now, BERLIN))
        assertTrue(TaskLogic.matches(open, TaskFilter.OPEN, now, BERLIN))
    }

    @Test
    fun `Suche findet Titel und Notizen ohne Beachtung der Gross- und Kleinschreibung`() {
        val t = task("a", title = "Steuererklärung", notes = "Belege aus dem Ordner")
        assertTrue(TaskLogic.matchesQuery(t, "steuer"))
        assertTrue(TaskLogic.matchesQuery(t, "ORDNER"))
        assertFalse(TaskLogic.matchesQuery(t, "Urlaub"))
        assertTrue(TaskLogic.matchesQuery(t, "   "))
    }

    @Test
    fun `Ranking - ueberfaellig vor heute vor Rest`() {
        val tasks = listOf(
            task("rest-urgent", priority = 3, dueAt = tomorrow),
            task("today", priority = 1, dueAt = today),
            task("overdue", priority = 0, dueAt = yesterday),
        )
        assertEquals(listOf("overdue", "today", "rest-urgent"), TaskLogic.rank(tasks, now, BERLIN).map { it.id })
    }

    @Test
    fun `Ranking - hoehere Prioritaet zuerst, dann kuerzere Aufgabe`() {
        val tasks = listOf(
            task("long", priority = 2, minutes = 90),
            task("short", priority = 2, minutes = 10),
            task("low", priority = 0, minutes = 5),
            task("high", priority = 3, minutes = 120),
        )
        assertEquals(listOf("high", "short", "long"), TaskLogic.rank(tasks, now, BERLIN).map { it.id })
    }

    @Test
    fun `Ranking ignoriert erledigte und pausierte Aufgaben und begrenzt die Anzahl`() {
        val tasks = listOf(
            task("done", priority = 3, status = TaskStatus.DONE),
            task("paused", priority = 3, status = TaskStatus.PAUSED),
            task("a"), task("b"), task("c"), task("d"),
        )
        val ranked = TaskLogic.rank(tasks, now, BERLIN)
        assertEquals(3, ranked.size)
        assertTrue(ranked.none { it.id == "done" || it.id == "paused" })
    }

    @Test
    fun `Listensortierung stellt erledigte ans Ende, neueste zuerst`() {
        val tasks = listOf(
            task("done-old", status = TaskStatus.DONE, completedAt = 100),
            task("open", priority = 1),
            task("done-new", status = TaskStatus.DONE, completedAt = 200),
        )
        assertEquals(listOf("open", "done-new", "done-old"), TaskLogic.sortForList(tasks, now, BERLIN).map { it.id })
    }
}
