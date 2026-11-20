package de.kopffrei.core.common

import de.kopffrei.core.common.ics.IcsCalendar
import de.kopffrei.core.common.ics.IcsEvent
import de.kopffrei.core.common.model.Frequency
import de.kopffrei.core.common.model.RecurrenceRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IcsCalendarTest {

    @Test
    fun `Export und Import eines einfachen Termins`() {
        val event = IcsEvent(
            uid = "abc-123",
            summary = "Arzttermin; Dr. Meier, Praxis",
            description = "Unterlagen mitnehmen\nVersichertenkarte",
            location = "Hauptstr. 1",
            startMillis = ms(2026, 6, 10, 15, 0),
            endMillis = ms(2026, 6, 10, 16, 0),
        )
        val ics = IcsCalendar.export(listOf(event), BERLIN, nowMillis = ms(2026, 6, 1))
        assertTrue(ics.contains("BEGIN:VCALENDAR"))
        assertTrue(ics.contains("\r\n"))
        val imported = IcsCalendar.import(ics, BERLIN)
        assertEquals(0, imported.skipped)
        assertEquals(listOf(event), imported.events)
    }

    @Test
    fun `Ganztaegiger Termin bleibt ganztaegig`() {
        val event = IcsEvent(
            uid = "allday",
            summary = "Urlaub",
            startMillis = ms(2026, 8, 1),
            endMillis = ms(2026, 8, 4), // exklusiv: 1.-3. August
            allDay = true,
        )
        val ics = IcsCalendar.export(listOf(event), BERLIN)
        assertTrue(ics.contains("DTSTART;VALUE=DATE:20260801"))
        assertTrue(ics.contains("DTEND;VALUE=DATE:20260804"))
        assertEquals(listOf(event), IcsCalendar.import(ics, BERLIN).events)
    }

    @Test
    fun `Serie mit Intervall und Enddatum`() {
        val event = IcsEvent(
            uid = "series",
            summary = "Training",
            startMillis = ms(2026, 3, 2, 18, 0),
            endMillis = ms(2026, 3, 2, 19, 0),
            rule = RecurrenceRule(Frequency.WEEKLY, interval = 2, until = ms(2026, 5, 1, 18, 0)),
        )
        val ics = IcsCalendar.export(listOf(event), BERLIN)
        assertTrue(ics.contains("RRULE:FREQ=WEEKLY;INTERVAL=2;UNTIL="))
        assertEquals(listOf(event), IcsCalendar.import(ics, BERLIN).events)
    }

    @Test
    fun `Lange Zeilen werden gefaltet und wieder zusammengesetzt`() {
        val long = "x".repeat(300)
        val event = IcsEvent("long", "Lang", description = long, startMillis = ms(2026, 1, 1, 10), endMillis = ms(2026, 1, 1, 11))
        val ics = IcsCalendar.export(listOf(event), BERLIN)
        assertTrue(ics.lines().all { it.length <= 76 })
        assertEquals(long, IcsCalendar.import(ics, BERLIN).events.single().description)
    }

    @Test
    fun `Import fremder Datei - TZID, DURATION, COUNT, Alarm und nicht unterstuetzte Regeln`() {
        val text = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:ext-1
            DTSTART;TZID=Europe/Berlin:20260601T090000
            DURATION:PT1H30M
            SUMMARY:Standup
            RRULE:FREQ=DAILY;COUNT=3
            BEGIN:VALARM
            ACTION:DISPLAY
            DESCRIPTION:Erinnerung
            TRIGGER:-PT15M
            END:VALARM
            END:VEVENT
            BEGIN:VEVENT
            UID:ext-2
            DTSTART:20260601T090000Z
            DTEND:20260601T100000Z
            SUMMARY:Montags
            RRULE:FREQ=WEEKLY;BYDAY=MO,WE
            END:VEVENT
            BEGIN:VEVENT
            SUMMARY:Kaputt ohne Start
            END:VEVENT
            END:VCALENDAR
        """.trimIndent().replace("\n", "\r\n")

        val result = IcsCalendar.import(text, BERLIN)
        assertEquals(2, result.events.size)
        assertEquals(1, result.skipped)

        val standup = result.events[0]
        assertEquals("Standup", standup.summary) // nicht von der Alarm-Beschreibung ueberschrieben
        assertEquals("", standup.description)
        assertEquals(ms(2026, 6, 1, 9, 0), standup.startMillis)
        assertEquals(ms(2026, 6, 1, 10, 30), standup.endMillis)
        assertEquals(Frequency.DAILY, standup.rule.frequency)
        assertEquals(ms(2026, 6, 3, 9, 0), standup.rule.until) // COUNT=3 -> 1., 2., 3. Juni

        val monday = result.events[1]
        assertEquals(Frequency.WEEKLY, monday.rule.frequency)
        assertTrue(result.warnings.any { it.contains("BYDAY") })
    }

    @Test
    fun `Leere oder fremde Eingabe fuehrt nicht zu Fehlern`() {
        assertEquals(0, IcsCalendar.import("", BERLIN).events.size)
        assertEquals(0, IcsCalendar.import("kein kalender", BERLIN).events.size)
    }
}
