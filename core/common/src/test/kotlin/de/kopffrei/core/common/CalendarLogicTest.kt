package de.kopffrei.core.common

import de.kopffrei.core.common.calendar.CalendarLogic
import de.kopffrei.core.common.calendar.CalendarMode
import de.kopffrei.core.common.time.TimeUtils
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse

class CalendarLogicTest {
    @Test
    fun wochenbeginnIstMontag() {
        // 2026-03-15 ist ein Sonntag.
        assertEquals(LocalDate.of(2026, 3, 9), CalendarLogic.weekStart(LocalDate.of(2026, 3, 15)))
        assertEquals(LocalDate.of(2026, 3, 9), CalendarLogic.weekStart(LocalDate.of(2026, 3, 9)))
        assertEquals(7, CalendarLogic.weekDays(LocalDate.of(2026, 3, 12)).size)
    }

    @Test
    fun monatsrasterHatVolleWochen() {
        // Februar 2026: 1. Februar ist ein Sonntag, 28 Tage -> 5 Zeilen von Mo 26.1. bis So 1.3.
        val feb = CalendarLogic.monthGrid(YearMonth.of(2026, 2))
        assertEquals(35, feb.size)
        assertEquals(LocalDate.of(2026, 1, 26), feb.first())
        assertEquals(LocalDate.of(2026, 3, 1), feb.last())
        // August 2026: 1. August ist ein Samstag, 31 Tage -> 6 Zeilen.
        val aug = CalendarLogic.monthGrid(YearMonth.of(2026, 8))
        assertEquals(42, aug.size)
        assertEquals(LocalDate.of(2026, 7, 27), aug.first())
    }

    @Test
    fun verschiebenJeAnsicht() {
        val d = LocalDate.of(2026, 1, 31)
        assertEquals(LocalDate.of(2026, 2, 28), CalendarLogic.shift(CalendarMode.MONTH, d, 1))
        assertEquals(LocalDate.of(2026, 2, 7), CalendarLogic.shift(CalendarMode.WEEK, d, 1))
        assertEquals(LocalDate.of(2026, 1, 30), CalendarLogic.shift(CalendarMode.DAY, d, -1))
        assertEquals(LocalDate.of(2026, 4, 1), CalendarLogic.shift(CalendarMode.AGENDA, d, 1))
    }

    @Test
    fun sichtbarerZeitraum() {
        val d = LocalDate.of(2026, 3, 12)
        val week = CalendarLogic.visibleRange(CalendarMode.WEEK, d)
        assertEquals(LocalDate.of(2026, 3, 9), week.first)
        assertEquals(LocalDate.of(2026, 3, 16), week.second)
        val day = CalendarLogic.visibleRange(CalendarMode.DAY, d)
        assertEquals(LocalDate.of(2026, 3, 13), day.second)
        val agenda = CalendarLogic.visibleRange(CalendarMode.AGENDA, d)
        assertEquals(d.plusDays(60), agenda.second)
    }

    @Test
    fun ganztaegigerTerminLiegtNurAmEigenenTag() {
        val start = TimeUtils.startOfDay(LocalDate.of(2026, 3, 10), BERLIN)
        val end = TimeUtils.startOfDay(LocalDate.of(2026, 3, 11), BERLIN)
        assertTrue(CalendarLogic.overlapsDay(start, end, LocalDate.of(2026, 3, 10), BERLIN))
        assertFalse(CalendarLogic.overlapsDay(start, end, LocalDate.of(2026, 3, 11), BERLIN))
        assertFalse(CalendarLogic.overlapsDay(start, end, LocalDate.of(2026, 3, 9), BERLIN))
    }

    @Test
    fun terminUeberMitternachtLiegtAufBeidenTagen() {
        val start = ms(2026, 3, 10, 23, 0)
        val end = ms(2026, 3, 11, 1, 0)
        assertTrue(CalendarLogic.overlapsDay(start, end, LocalDate.of(2026, 3, 10), BERLIN))
        assertTrue(CalendarLogic.overlapsDay(start, end, LocalDate.of(2026, 3, 11), BERLIN))
        assertFalse(CalendarLogic.overlapsDay(start, end, LocalDate.of(2026, 3, 12), BERLIN))
    }

    @Test
    fun terminOhneDauerZaehltZumStarttag() {
        val t = ms(2026, 3, 10, 0, 0)
        assertTrue(CalendarLogic.overlapsDay(t, t, LocalDate.of(2026, 3, 10), BERLIN))
        assertFalse(CalendarLogic.overlapsDay(t, t, LocalDate.of(2026, 3, 9), BERLIN))
    }

    @Test
    fun titelJeAnsicht() {
        val d = LocalDate.of(2026, 3, 12)
        assertTrue(CalendarLogic.title(CalendarMode.MONTH, d).contains("2026"))
        assertTrue(CalendarLogic.title(CalendarMode.AGENDA, d).startsWith("Agenda ab"))
    }
}
