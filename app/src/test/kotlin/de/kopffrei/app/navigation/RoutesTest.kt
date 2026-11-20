package de.kopffrei.app.navigation

import de.kopffrei.core.common.DeepLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutesTest {
    @Test
    fun noteEditOmitsMissingId() {
        assertEquals("note_edit?voice=true&thought=false", Routes.noteEdit(voice = true))
        assertEquals("note_edit?noteId=abc&voice=false&thought=true", Routes.noteEdit("abc", thought = true))
    }

    @Test
    fun taskAndEventRoutesCarryOnlyGivenArguments() {
        assertEquals("task_edit", Routes.taskEdit())
        assertEquals("task_edit?taskId=t1", Routes.taskEdit("t1"))
        assertEquals("task_edit?fromNote=n1", Routes.taskEdit(fromNote = "n1"))
        assertEquals("event_edit", Routes.eventEdit())
        assertEquals("event_edit?eventId=e1", Routes.eventEdit("e1"))
        assertEquals("event_edit?fromNote=n1", Routes.eventEdit(fromNote = "n1"))
        assertEquals("event_edit?date=1700000000000", Routes.eventEdit(date = 1_700_000_000_000L))
    }

    @Test
    fun journalAndFocusRoutes() {
        assertEquals("journal_edit", Routes.journalEdit())
        assertEquals("journal_edit?entryId=j1", Routes.journalEdit("j1"))
        assertEquals("focus", Routes.focus())
        assertEquals("focus?minutes=25&autostart=true", Routes.focus(25, autostart = true))
    }

    @Test
    fun baseOfStripsArguments() {
        assertEquals("focus", Routes.baseOf(Routes.FOCUS_PATTERN))
        assertEquals("today", Routes.baseOf("today"))
        assertEquals("note_edit", Routes.baseOf(Routes.noteEdit("x")))
        assertEquals(null, Routes.baseOf(null))
    }

    @Test
    fun patternsContainEveryArgumentOfTheirBuilders() {
        assertTrue(Routes.NOTE_EDIT_PATTERN.contains("{noteId}") && Routes.NOTE_EDIT_PATTERN.contains("{voice}") && Routes.NOTE_EDIT_PATTERN.contains("{thought}"))
        assertTrue(Routes.TASK_EDIT_PATTERN.contains("{taskId}") && Routes.TASK_EDIT_PATTERN.contains("{fromNote}"))
        assertTrue(Routes.EVENT_EDIT_PATTERN.contains("{eventId}") && Routes.EVENT_EDIT_PATTERN.contains("{fromNote}") && Routes.EVENT_EDIT_PATTERN.contains("{date}"))
        assertTrue(Routes.FOCUS_PATTERN.contains("{minutes}") && Routes.FOCUS_PATTERN.contains("{autostart}"))
    }

    @Test
    fun mainNavigationOrderMatchesSpecification() {
        assertEquals(listOf("today", "notes", "calendar", "focus", "more"), Routes.TOP_LEVEL)
    }

    @Test
    fun quickActionVisibleOnTabsAndTasksOnly() {
        assertTrue(Routes.TODAY in Routes.WITH_QUICK_ACTION)
        assertTrue(Routes.TASKS in Routes.WITH_QUICK_ACTION)
        assertFalse(Routes.NOTE_EDIT in Routes.WITH_QUICK_ACTION)
        assertFalse(Routes.SETTINGS in Routes.WITH_QUICK_ACTION)
    }

    @Test
    fun deepLinksUseTheSameArgumentNamesAsRoutes() {
        assertTrue(DeepLinks.newNote(voice = true).contains("${Routes.ARG_VOICE}=true"))
        assertTrue(DeepLinks.newNote(thought = true).contains("${Routes.ARG_THOUGHT}=true"))
        assertTrue(DeepLinks.focus(15, autostart = true).contains("${Routes.ARG_MINUTES}=15"))
        assertTrue(DeepLinks.focus(15, autostart = true).contains("${Routes.ARG_AUTOSTART}=true"))
    }

    @Test
    fun deepLinkPatternsMatchTheLinksBuiltByDeepLinks() {
        assertEquals(DeepLinks.openNote("abc"), Routes.LINK_NOTE_OPEN.replace("{noteId}", "abc"))
        assertEquals(DeepLinks.openTask("t1"), Routes.LINK_TASK_OPEN.replace("{taskId}", "t1"))
        assertEquals(DeepLinks.openEvent("e1"), Routes.LINK_EVENT_OPEN.replace("{eventId}", "e1"))
        assertEquals(
            DeepLinks.newNote(voice = true, thought = false),
            Routes.LINK_NOTE_NEW.replace("{voice}", "true").replace("{thought}", "false"),
        )
        assertEquals(
            DeepLinks.focus(25, autostart = true),
            Routes.LINK_FOCUS.replace("{minutes}", "25").replace("{autostart}", "true"),
        )
    }
}
