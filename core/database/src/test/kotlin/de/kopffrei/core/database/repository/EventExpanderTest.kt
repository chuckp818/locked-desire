package de.kopffrei.core.database.repository

import de.kopffrei.core.common.model.Frequency
import de.kopffrei.core.common.model.RecurrenceRule
import de.kopffrei.core.database.entity.CalendarEventEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class EventExpanderTest {
    private val berlin: ZoneId = ZoneId.of("Europe/Berlin")

    private fun ms(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(berlin).toInstant().toEpochMilli()

    private fun event(
        title: String,
        start: Long,
        end: Long,
        rule: RecurrenceRule = RecurrenceRule.NONE,
    ) = CalendarEventEntity(title = title, startAt = start, endAt = end, recurrence = rule)

    @Test
    fun `einmaliger Termin im Zeitraum wird geliefert`() {
        val e = event("Zahnarzt", ms(2026, 5, 4, 9), ms(2026, 5, 4, 10))
        val result = EventExpander.expand(listOf(e), ms(2026, 5, 1), ms(2026, 5, 8), berlin)
        assertEquals(1, result.size)
        assertEquals(ms(2026, 5, 4, 9), result[0].startMillis)
        assertEquals(ms(2026, 5, 4, 10), result[0].endMillis)
    }

    @Test
    fun `einmaliger Termin ausserhalb des Zeitraums fehlt`() {
        val e = event("Zahnarzt", ms(2026, 5, 4, 9), ms(2026, 5, 4, 10))
        assertTrue(EventExpander.expand(listOf(e), ms(2026, 6, 1), ms(2026, 6, 8), berlin).isEmpty())
    }

    @Test
    fun `Termin der in den Zeitraum hineinreicht zaehlt`() {
        val e = event("Nachtschicht", ms(2026, 5, 3, 22), ms(2026, 5, 4, 6))
        val result = EventExpander.expand(listOf(e), ms(2026, 5, 4), ms(2026, 5, 5), berlin)
        assertEquals(1, result.size)
    }

    @Test
    fun `taegliche Serie ergibt je Tag einen Termin mit gleicher Dauer`() {
        val e = event("Tabletten", ms(2026, 5, 1, 8), ms(2026, 5, 1, 8, 15), RecurrenceRule(Frequency.DAILY))
        val result = EventExpander.expand(listOf(e), ms(2026, 5, 10), ms(2026, 5, 13), berlin)
        assertEquals(3, result.size)
        assertEquals(ms(2026, 5, 10, 8), result[0].startMillis)
        assertEquals(ms(2026, 5, 12, 8), result[2].startMillis)
        assertTrue(result.all { it.endMillis - it.startMillis == 15 * 60_000L })
    }

    @Test
    fun `Serie endet am Enddatum`() {
        val rule = RecurrenceRule(Frequency.DAILY, until = ms(2026, 5, 11, 8))
        val e = event("Kur", ms(2026, 5, 1, 8), ms(2026, 5, 1, 9), rule)
        val result = EventExpander.expand(listOf(e), ms(2026, 5, 9), ms(2026, 5, 20), berlin)
        assertEquals(listOf(ms(2026, 5, 9, 8), ms(2026, 5, 10, 8), ms(2026, 5, 11, 8)), result.map { it.startMillis })
    }

    @Test
    fun `woechentliche Serie behaelt die Ortszeit ueber die Sommerzeit`() {
        // Die Uhren werden am 29.03.2026 umgestellt. Der Termin bleibt um 10:00 Uhr Ortszeit.
        val e = event("Gruppe", ms(2026, 3, 26, 10), ms(2026, 3, 26, 11), RecurrenceRule(Frequency.WEEKLY))
        val result = EventExpander.expand(listOf(e), ms(2026, 3, 26), ms(2026, 4, 10), berlin)
        assertEquals(listOf(ms(2026, 3, 26, 10), ms(2026, 4, 2, 10), ms(2026, 4, 9, 10)), result.map { it.startMillis })
    }

    @Test
    fun `mehrere Termine sind nach Start und dann Titel sortiert`() {
        val a = event("B-Termin", ms(2026, 5, 4, 9), ms(2026, 5, 4, 10))
        val b = event("A-Termin", ms(2026, 5, 4, 9), ms(2026, 5, 4, 10))
        val c = event("Frueh", ms(2026, 5, 4, 7), ms(2026, 5, 4, 8))
        val result = EventExpander.expand(listOf(a, b, c), ms(2026, 5, 4), ms(2026, 5, 5), berlin)
        assertEquals(listOf("Frueh", "A-Termin", "B-Termin"), result.map { it.event.title })
    }

    @Test
    fun `naechster Termin ist der erste kommende`() {
        val past = event("Vorbei", ms(2026, 5, 1, 9), ms(2026, 5, 1, 10))
        val soon = event("Bald", ms(2026, 5, 4, 15), ms(2026, 5, 4, 16))
        val later = event("Spaeter", ms(2026, 5, 6, 9), ms(2026, 5, 6, 10))
        val next = EventExpander.next(listOf(past, later, soon), ms(2026, 5, 4, 12), berlin)
        assertEquals("Bald", next?.event?.title)
    }

    @Test
    fun `gerade laufender Termin gilt als naechster`() {
        val running = event("Meeting", ms(2026, 5, 4, 11), ms(2026, 5, 4, 13))
        val next = EventExpander.next(listOf(running), ms(2026, 5, 4, 12), berlin)
        assertEquals("Meeting", next?.event?.title)
    }

    @Test
    fun `ohne kommende Termine gibt es keinen naechsten`() {
        val past = event("Vorbei", ms(2026, 5, 1, 9), ms(2026, 5, 1, 10))
        assertNull(EventExpander.next(listOf(past), ms(2026, 5, 4, 12), berlin))
        assertNull(EventExpander.next(emptyList(), ms(2026, 5, 4, 12), berlin))
    }

    @Test
    fun `naechster Termin einer Serie ist der naechste Durchlauf`() {
        val e = event("Gassi", ms(2026, 5, 1, 7), ms(2026, 5, 1, 8), RecurrenceRule(Frequency.DAILY))
        val next = EventExpander.next(listOf(e), ms(2026, 5, 4, 12), berlin)
        assertEquals(ms(2026, 5, 5, 7), next?.startMillis)
    }

    @Test
    fun `Tagesansicht zeigt Serientermin und einmaligen Termin`() {
        val series = event("Tabletten", ms(2026, 5, 1, 8), ms(2026, 5, 1, 8, 5), RecurrenceRule(Frequency.DAILY))
        val once = event("Friseur", ms(2026, 5, 4, 16), ms(2026, 5, 4, 17))
        val other = event("Anderer Tag", ms(2026, 5, 5, 16), ms(2026, 5, 5, 17))
        val result = EventExpander.onDay(listOf(series, once, other), ms(2026, 5, 4), berlin)
        assertEquals(listOf("Tabletten", "Friseur"), result.map { it.event.title })
    }
}
