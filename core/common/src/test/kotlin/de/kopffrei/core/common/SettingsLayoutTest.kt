package de.kopffrei.core.common

import de.kopffrei.core.common.model.DashboardCard
import de.kopffrei.core.common.model.MoreEntry
import de.kopffrei.core.common.settings.DashboardLayout
import de.kopffrei.core.common.settings.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsLayoutTest {

    @Test
    fun `Standard zeigt alle Karten in der Standardreihenfolge`() {
        assertEquals(DashboardCard.entries.toList(), UserSettings().visibleDashboardCards())
    }

    @Test
    fun `Ausgeblendete Karten verschwinden, fehlende werden angehaengt`() {
        val settings = UserSettings(
            dashboardOrder = listOf(DashboardCard.FOCUS, DashboardCard.MOOD),
            dashboardHidden = setOf(DashboardCard.MOOD),
        )
        val visible = settings.visibleDashboardCards()
        assertEquals(DashboardCard.FOCUS, visible.first())
        assertFalse(DashboardCard.MOOD in visible)
        assertEquals(DashboardCard.entries.size - 1, visible.size)
    }

    @Test
    fun `Karte nach oben und unten verschieben`() {
        val order = listOf(DashboardCard.MOOD, DashboardCard.FOCUS, DashboardCard.TOP_TASKS)
        assertEquals(
            listOf(DashboardCard.FOCUS, DashboardCard.MOOD, DashboardCard.TOP_TASKS),
            DashboardLayout.move(order, DashboardCard.FOCUS, -1),
        )
        assertEquals(
            listOf(DashboardCard.MOOD, DashboardCard.TOP_TASKS, DashboardCard.FOCUS),
            DashboardLayout.move(order, DashboardCard.FOCUS, +1),
        )
    }

    @Test
    fun `Verschieben an den Raendern aendert nichts`() {
        val order = listOf(DashboardCard.MOOD, DashboardCard.FOCUS)
        assertEquals(order, DashboardLayout.move(order, DashboardCard.MOOD, -1))
        assertEquals(order, DashboardLayout.move(order, DashboardCard.FOCUS, +1))
        assertEquals(order, DashboardLayout.move(order, DashboardCard.TOP_TASKS, +1))
    }

    @Test
    fun `Volle Reihenfolge enthaelt jede Karte genau einmal`() {
        val settings = UserSettings(dashboardOrder = listOf(DashboardCard.FOCUS, DashboardCard.FOCUS))
        val full = DashboardLayout.fullOrder(settings)
        assertEquals(DashboardCard.entries.size, full.size)
        assertEquals(DashboardCard.entries.toSet(), full.toSet())
    }

    @Test
    fun `Nur Phase-1-Eintraege im Mehr-Bereich sind nutzbar`() {
        assertTrue(MoreEntry.JOURNAL.isAvailable)
        assertTrue(MoreEntry.TASKS.isAvailable)
        assertTrue(MoreEntry.SETTINGS.isAvailable)
        assertFalse(MoreEntry.AI.isAvailable)
        assertFalse(MoreEntry.COMMUNITY.isAvailable)
        assertFalse(MoreEntry.PARTNER.isAvailable)
        assertFalse(MoreEntry.ROUTINES.isAvailable)
        assertFalse(MoreEntry.STATS.isAvailable)
    }
}
