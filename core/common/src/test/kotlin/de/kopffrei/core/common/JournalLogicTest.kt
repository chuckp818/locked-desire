package de.kopffrei.core.common

import de.kopffrei.core.common.journal.JournalItem
import de.kopffrei.core.common.journal.JournalLogic
import de.kopffrei.core.common.model.Mood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JournalLogicTest {
    @Test
    fun `Filter nach Stimmung und Tag`() {
        assertTrue(JournalLogic.matchesFilter(Mood.GOOD, "Arbeit, Sport", null, null))
        assertTrue(JournalLogic.matchesFilter(Mood.GOOD, "Arbeit, Sport", Mood.GOOD, "sport"))
        assertFalse(JournalLogic.matchesFilter(Mood.GOOD, "Arbeit", Mood.SAD, null))
        assertFalse(JournalLogic.matchesFilter(null, "Arbeit", Mood.GOOD, null))
        assertFalse(JournalLogic.matchesFilter(Mood.GOOD, "Arbeit", null, "Sport"))
    }

    @Test
    fun `Tags werden ohne Doppelte und sortiert gesammelt`() {
        val tags = JournalLogic.collectTags(listOf("Sport, arbeit", "Arbeit", "", "familie , Sport"))
        assertEquals(listOf("arbeit", "familie", "Sport"), tags)
    }

    @Test
    fun `Anzeigetitel faellt auf erste Zeile und dann auf Ohne Titel zurueck`() {
        assertEquals("Mein Tag", JournalLogic.displayTitle(" Mein Tag ", "egal"))
        assertEquals("Erste Zeile", JournalLogic.displayTitle("", "Erste Zeile\nZweite"))
        assertEquals("Ohne Titel", JournalLogic.displayTitle("", "  "))
    }

    @Test
    fun `Export sortiert nach Datum und enthaelt Stimmung und Tags`() {
        val older = JournalItem("Alt", "Text eins", Mood.GOOD, listOf("a", "b"), ms(2026, 3, 1, 8, 15))
        val newer = JournalItem("", "Text zwei", null, emptyList(), ms(2026, 3, 2, 20, 0))
        val text = JournalLogic.exportText(listOf(newer, older), BERLIN, ms(2026, 3, 3, 12, 0))
        assertTrue(text.contains("Einträge: 2"))
        assertTrue(text.indexOf("Text eins") < text.indexOf("Text zwei"))
        assertTrue(text.contains("Stimmung: ${Mood.GOOD.emoji} Gut"))
        assertTrue(text.contains("Tags: a, b"))
        assertTrue(text.contains("08:15 – Alt"))
    }

    @Test
    fun `Export ohne Eintraege sagt das ausdruecklich`() {
        val text = JournalLogic.exportText(emptyList(), BERLIN, ms(2026, 3, 3, 12, 0))
        assertTrue(text.contains("Keine Einträge."))
    }
}
