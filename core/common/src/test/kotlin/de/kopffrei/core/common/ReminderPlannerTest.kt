package de.kopffrei.core.common

import de.kopffrei.core.common.model.Frequency
import de.kopffrei.core.common.model.RecurrenceRule
import de.kopffrei.core.common.reminder.ReminderAnchor
import de.kopffrei.core.common.reminder.ReminderPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderPlannerTest {
    // Arzttermin um 15:00, Stufen: 90 Min. vorher (Unterlagen), 60 (fertigmachen), 30 (losgehen)
    private val appointment = ReminderAnchor(ms(2026, 6, 10, 15, 0))

    private fun trigger(
        offset: Int,
        now: Long,
        last: Long? = null,
        snoozed: Long? = null,
        anchor: ReminderAnchor = appointment,
    ) = ReminderPlanner.nextTrigger(null, offset, anchor, last, snoozed, now, BERLIN)

    @Test
    fun `mehrstufige Erinnerung liefert jeweils den passenden Zeitpunkt`() {
        val now = ms(2026, 6, 10, 8, 0)
        assertEquals(ms(2026, 6, 10, 13, 30), trigger(90, now))
        assertEquals(ms(2026, 6, 10, 14, 0), trigger(60, now))
        assertEquals(ms(2026, 6, 10, 14, 30), trigger(30, now))
    }

    @Test
    fun `bereits ausgeloeste Erinnerung wird nicht erneut geplant`() {
        val fired = ms(2026, 6, 10, 14, 30)
        assertNull(trigger(30, now = ms(2026, 6, 10, 14, 31), last = fired))
    }

    @Test
    fun `verpasste Erinnerung wird innerhalb der Kulanzzeit nachgeholt`() {
        val result = trigger(30, now = ms(2026, 6, 10, 16, 0))
        assertEquals(ms(2026, 6, 10, 14, 30), result) // liegt in der Vergangenheit = sofort ausloesen
    }

    @Test
    fun `sehr alte Erinnerung wird verworfen`() {
        assertNull(trigger(30, now = ms(2026, 6, 11, 9, 0)))
    }

    @Test
    fun `Spaeter-Zeitpunkt hat Vorrang`() {
        val snoozed = ms(2026, 6, 10, 14, 40)
        assertEquals(snoozed, trigger(30, now = ms(2026, 6, 10, 14, 31), last = ms(2026, 6, 10, 14, 30), snoozed = snoozed))
    }

    @Test
    fun `taegliche Serie plant nach Ausloesung den naechsten Tag`() {
        val daily = ReminderAnchor(ms(2026, 6, 1, 15, 0), RecurrenceRule(Frequency.DAILY))
        val fired = ms(2026, 6, 10, 14, 30)
        val next = trigger(30, now = ms(2026, 6, 10, 14, 35), last = fired, anchor = daily)
        assertEquals(ms(2026, 6, 11, 14, 30), next)
    }

    @Test
    fun `fester Zeitpunkt`() {
        val at = ms(2026, 6, 10, 20, 0)
        assertEquals(at, ReminderPlanner.nextTrigger(at, null, null, null, null, ms(2026, 6, 10, 10), BERLIN))
        assertNull(ReminderPlanner.nextTrigger(at, null, null, at, null, ms(2026, 6, 10, 21), BERLIN))
    }

    @Test
    fun `ohne Bezug und ohne festen Zeitpunkt gibt es keinen Zeitpunkt`() {
        assertNull(ReminderPlanner.nextTrigger(null, 30, null, null, null, ms(2026, 6, 10), BERLIN))
    }

    @Test
    fun `Stufen aus Vorbereitungs- und Fahrzeit`() {
        val stages = ReminderPlanner.stagesFromTimes(prepMinutes = 30, travelMinutes = 20)
        assertEquals(listOf("Fertigmachen", "Losgehen"), stages.map { it.label })
        assertEquals(listOf(50, 20), stages.map { it.offsetMinutes })
        assertEquals(emptyList<Any>(), ReminderPlanner.stagesFromTimes(0, 0))
    }
}
