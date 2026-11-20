package de.kopffrei.core.common

import de.kopffrei.core.common.model.Frequency
import de.kopffrei.core.common.model.RecurrenceRule
import de.kopffrei.core.common.recurrence.Recurrence
import de.kopffrei.core.common.time.TimeUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurrenceTest {

    @Test
    fun `taeglich - erster Termin ab Mindestzeit`() {
        val rule = RecurrenceRule(Frequency.DAILY)
        val start = ms(2026, 1, 1, 10, 0)
        val result = Recurrence.firstOccurrenceAtOrAfter(start, rule, BERLIN, ms(2026, 1, 5, 0, 0))
        assertEquals(ms(2026, 1, 5, 10, 0), result)
    }

    @Test
    fun `Mindestzeit vor dem Start liefert den Start`() {
        val rule = RecurrenceRule(Frequency.DAILY)
        val start = ms(2026, 1, 10, 8, 0)
        assertEquals(start, Recurrence.firstOccurrenceAtOrAfter(start, rule, BERLIN, ms(2026, 1, 1)))
    }

    @Test
    fun `woechentlich mit Intervall 2`() {
        val rule = RecurrenceRule(Frequency.WEEKLY, interval = 2)
        val start = ms(2026, 1, 1, 9, 30)
        assertEquals(ms(2026, 1, 15, 9, 30), Recurrence.occurrenceAt(start, rule, BERLIN, 1))
        assertEquals(
            ms(2026, 1, 29, 9, 30),
            Recurrence.firstOccurrenceAtOrAfter(start, rule, BERLIN, ms(2026, 1, 16)),
        )
    }

    @Test
    fun `monatlich - 31 Januar bleibt am Monatsende und driftet nicht`() {
        val rule = RecurrenceRule(Frequency.MONTHLY)
        val start = ms(2026, 1, 31, 12, 0)
        assertEquals(ms(2026, 2, 28, 12, 0), Recurrence.occurrenceAt(start, rule, BERLIN, 1))
        assertEquals(ms(2026, 3, 31, 12, 0), Recurrence.occurrenceAt(start, rule, BERLIN, 2))
        assertEquals(ms(2026, 4, 30, 12, 0), Recurrence.occurrenceAt(start, rule, BERLIN, 3))
    }

    @Test
    fun `jaehrlich - Schalttag`() {
        val rule = RecurrenceRule(Frequency.YEARLY)
        val start = ms(2024, 2, 29, 8, 0)
        assertEquals(ms(2025, 2, 28, 8, 0), Recurrence.occurrenceAt(start, rule, BERLIN, 1))
        assertEquals(ms(2028, 2, 29, 8, 0), Recurrence.occurrenceAt(start, rule, BERLIN, 4))
    }

    @Test
    fun `Enddatum begrenzt die Serie`() {
        val start = ms(2026, 1, 1, 10, 0)
        val rule = RecurrenceRule(Frequency.DAILY, until = ms(2026, 1, 3, 10, 0))
        assertEquals(ms(2026, 1, 3, 10, 0), Recurrence.firstOccurrenceAtOrAfter(start, rule, BERLIN, ms(2026, 1, 3)))
        assertNull(Recurrence.firstOccurrenceAtOrAfter(start, rule, BERLIN, ms(2026, 1, 4)))
    }

    @Test
    fun `Sommerzeitwechsel haelt die Wanduhrzeit`() {
        // Sommerzeit beginnt in Europa am 29.03.2026.
        val rule = RecurrenceRule(Frequency.DAILY)
        val start = ms(2026, 3, 27, 10, 0)
        val after = Recurrence.occurrenceAt(start, rule, BERLIN, 4) // 31.03.
        val local = TimeUtils.toLocalDateTime(after, BERLIN)
        assertEquals(10, local.hour)
        assertEquals(0, local.minute)
        assertEquals(31, local.dayOfMonth)
    }

    @Test
    fun `Termine im Zeitraum - woechentliche Serie`() {
        val rule = RecurrenceRule(Frequency.WEEKLY)
        val start = ms(2026, 1, 5, 18, 0) // Montag
        val hits = Recurrence.occurrencesBetween(
            start, 60 * TimeUtils.MINUTE_MS, rule, BERLIN, ms(2026, 1, 1), ms(2026, 2, 1),
        )
        assertEquals(
            listOf(ms(2026, 1, 5, 18), ms(2026, 1, 12, 18), ms(2026, 1, 19, 18), ms(2026, 1, 26, 18)),
            hits,
        )
    }

    @Test
    fun `Termin der in den Zeitraum hineinreicht zaehlt`() {
        val none = RecurrenceRule.NONE
        val start = ms(2026, 1, 1, 22, 0)
        val duration = 4 * TimeUtils.HOUR_MS
        val hits = Recurrence.occurrencesBetween(start, duration, none, BERLIN, ms(2026, 1, 2), ms(2026, 1, 3))
        assertEquals(listOf(start), hits)
        val miss = Recurrence.occurrencesBetween(start, duration, none, BERLIN, ms(2026, 1, 2, 6), ms(2026, 1, 3))
        assertTrue(miss.isEmpty())
    }

    @Test
    fun `naechste Faelligkeit springt ueber ueberfaellige Termine`() {
        val rule = RecurrenceRule(Frequency.DAILY)
        val due = ms(2026, 1, 1, 9, 0)
        val next = Recurrence.nextDue(due, rule, BERLIN, notBeforeMillis = ms(2026, 1, 5, 0, 0))
        assertEquals(ms(2026, 1, 5, 9, 0), next)
        assertNull(Recurrence.nextDue(due, RecurrenceRule.NONE, BERLIN, 0))
    }

    @Test
    fun `COUNT wird in ein Enddatum umgerechnet`() {
        val rule = RecurrenceRule(Frequency.DAILY)
        val start = ms(2026, 1, 1, 10)
        assertEquals(ms(2026, 1, 3, 10), Recurrence.lastStartForCount(start, rule, BERLIN, 3))
    }
}
