package de.kopffrei.core.common

import de.kopffrei.core.common.settings.UserSettings
import de.kopffrei.core.common.tasks.TaskView
import de.kopffrei.core.common.widget.WidgetLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetLogicTest {
    @Test
    fun `Mit App-Sperre zeigen Widgets keine Inhalte`() {
        assertTrue(WidgetLogic.showsContent(UserSettings()))
        assertFalse(WidgetLogic.showsContent(UserSettings(biometricLock = true)))
    }

    @Test
    fun `Hinweistext der Sperre verraet keinen Inhalt`() {
        assertTrue(WidgetLogic.LOCKED_TEXT.contains("Gesperrt"))
    }

    @Test
    fun `Terminzeile mit und ohne Uhrzeit`() {
        assertEquals("14:30 Zahnarzt", WidgetLogic.eventLine("Zahnarzt", ms(2026, 3, 10, 14, 30), false, BERLIN))
        assertEquals("Ganztägig · Urlaub", WidgetLogic.eventLine("Urlaub", ms(2026, 3, 10), true, BERLIN))
    }

    @Test
    fun `Aufgabenzeile nennt ueberfaellig und heute`() {
        val now = ms(2026, 3, 10, 12, 0)
        val overdue = TaskView(id = "1", title = "Rechnung", dueAt = ms(2026, 3, 9), dueHasTime = false)
        val today = TaskView(id = "2", title = "Anrufen", dueAt = ms(2026, 3, 10, 15, 0), dueHasTime = true)
        val todayNoTime = TaskView(id = "3", title = "Wäsche", dueAt = ms(2026, 3, 10), dueHasTime = false)
        val later = TaskView(id = "4", title = "Steuer", dueAt = ms(2026, 3, 20), dueHasTime = false)
        assertEquals("Rechnung · überfällig", WidgetLogic.taskLine(overdue, now, BERLIN))
        assertEquals("Anrufen · heute 15:00", WidgetLogic.taskLine(today, now, BERLIN))
        assertEquals("Wäsche · heute", WidgetLogic.taskLine(todayNoTime, now, BERLIN))
        assertEquals("Steuer", WidgetLogic.taskLine(later, now, BERLIN))
    }

    @Test
    fun `Notizvorschau fuer Text und Checkliste`() {
        assertEquals("Kurz", WidgetLogic.notePreview("  Kurz  ", false, 50, 3))
        val checklist = "[ ] Milch\n[x] Brot\n[ ] Eier"
        assertEquals("☐ Milch\n☑ Brot", WidgetLogic.notePreview(checklist, true, 50, 2))
    }

    @Test
    fun `Sichtbare Zeilen richten sich nach der Hoehe`() {
        assertEquals(1, WidgetLogic.visibleItems(40, 30, 24, 5))
        assertEquals(3, WidgetLogic.visibleItems(120, 30, 28, 5))
        assertEquals(5, WidgetLogic.visibleItems(400, 30, 24, 5))
        assertEquals(1, WidgetLogic.visibleItems(400, 30, 0, 5))
    }
}
