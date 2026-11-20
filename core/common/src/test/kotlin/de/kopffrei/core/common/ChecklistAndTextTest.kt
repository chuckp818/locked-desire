package de.kopffrei.core.common

import de.kopffrei.core.common.checklist.Checklist
import de.kopffrei.core.common.checklist.ChecklistItem
import de.kopffrei.core.common.model.DashboardCard
import de.kopffrei.core.common.settings.UserSettings
import de.kopffrei.core.common.text.Greeting
import de.kopffrei.core.common.text.TextUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChecklistAndTextTest {

    @Test
    fun `Checkliste - Serialisieren und Lesen ergibt dasselbe`() {
        val items = listOf(ChecklistItem("Milch", false), ChecklistItem("Brot", true))
        val text = Checklist.serialize(items)
        assertEquals("[ ] Milch\n[x] Brot", text)
        assertEquals(items, Checklist.parse(text))
    }

    @Test
    fun `Checkliste - Zeilen ohne Marker und grosses X`() {
        val parsed = Checklist.parse("Eier\n[X] Butter\n\n   \n[ ] Salz")
        assertEquals(
            listOf(ChecklistItem("Eier"), ChecklistItem("Butter", true), ChecklistItem("Salz")),
            parsed,
        )
    }

    @Test
    fun `Checkliste - Umwandlung Text und Liste`() {
        val items = Checklist.fromPlainText("a\n\n b \nc")
        assertEquals(listOf("a", "b", "c"), items.map { it.text })
        assertEquals("a\nb\nc", Checklist.toPlainText(items))
        assertEquals(1 to 3, Checklist.progress(items.mapIndexed { i, it -> it.copy(checked = i == 0) }))
    }

    @Test
    fun `Leere Punkte werden nicht gespeichert`() {
        assertEquals("[ ] a", Checklist.serialize(listOf(ChecklistItem("a"), ChecklistItem("  "))))
    }

    @Test
    fun `LIKE-Muster maskiert Sonderzeichen`() {
        assertEquals("%", TextUtils.likePattern("   "))
        assertEquals("%100\\%%", TextUtils.likePattern("100%"))
        assertEquals("%a\\_b%", TextUtils.likePattern("a_b"))
        assertEquals("%a\\\\b%", TextUtils.likePattern("a\\b"))
    }

    @Test
    fun `Tags werden bereinigt`() {
        // Duplikate (ohne Beachtung der Gross-/Kleinschreibung) fallen weg, der erste Eintrag bleibt.
        assertEquals(listOf("Arbeit", "idee"), TextUtils.parseTags(" #Arbeit, idee ,Idee;; "))
        assertEquals("a, b", TextUtils.joinTags(listOf("a", "b")))
    }

    @Test
    fun `Erinnerungsstufen werden lesbar formuliert`() {
        assertEquals("Zur Startzeit", TextUtils.formatOffset(0))
        assertEquals("30 Min. vorher", TextUtils.formatOffset(30))
        assertEquals("1 Std. vorher", TextUtils.formatOffset(60))
        assertEquals("1 Std. 30 Min. vorher", TextUtils.formatOffset(90))
        assertEquals("1 Tag vorher", TextUtils.formatOffset(1440))
        assertEquals("2 Tage vorher", TextUtils.formatOffset(2880))
    }

    @Test
    fun `Erste Zeile und Kuerzen`() {
        assertEquals("Hallo", TextUtils.firstLine("\n  \n Hallo \nWelt"))
        assertEquals("abc…", TextUtils.truncate("abcdefgh", 4))
        assertEquals("abc", TextUtils.truncate("abc", 4))
    }

    @Test
    fun `Begruessung und Hinweise`() {
        assertTrue(Greeting.headline(8).startsWith("Guten Morgen"))
        assertTrue(Greeting.headline(2).startsWith("Noch wach"))
        assertTrue(Greeting.motivation(-3).isNotBlank()) // auch negative Tage sind gueltig
    }

    @Test
    fun `Startseite - Reihenfolge und ausgeblendete Karten`() {
        val settings = UserSettings(
            dashboardOrder = listOf(DashboardCard.FOCUS, DashboardCard.MOOD),
            dashboardHidden = setOf(DashboardCard.MOOD),
        )
        val visible = settings.visibleDashboardCards()
        assertEquals(DashboardCard.FOCUS, visible.first())
        assertFalse(DashboardCard.MOOD in visible)
        // fehlende Karten werden angehaengt, nichts geht verloren
        assertEquals(DashboardCard.entries.size - 1, visible.size)
    }
}
