package de.kopffrei.core.common

import de.kopffrei.core.common.text.DateText
import de.kopffrei.core.common.text.TextUtils
import de.kopffrei.core.common.DeepLinks
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class DateTextTest {
    private val thursday = LocalDate.of(2026, 3, 12)

    @Test
    fun `Datum kurz und lang`() {
        assertEquals("Do., 12. März", DateText.dateShort(thursday))
        assertEquals("Donnerstag, 12. März 2026", DateText.dateLong(thursday))
        assertEquals("März 2026", DateText.monthTitle(YearMonth.of(2026, 3)))
    }

    @Test
    fun `Relative Tage`() {
        assertEquals("Heute", DateText.relativeDay(thursday, thursday))
        assertEquals("Morgen", DateText.relativeDay(thursday.plusDays(1), thursday))
        assertEquals("Übermorgen", DateText.relativeDay(thursday.plusDays(2), thursday))
        assertEquals("Gestern", DateText.relativeDay(thursday.minusDays(1), thursday))
        assertEquals("So., 15. März", DateText.relativeDay(thursday.plusDays(3), thursday))
    }

    @Test
    fun `Uhrzeit und Zeitspannen`() {
        assertEquals("09:05", DateText.clock(9 * 60 + 5))
        assertEquals("00:00", DateText.clock(0))
        val start = ms(2026, 3, 12, 14, 30)
        val end = ms(2026, 3, 12, 15, 45)
        assertEquals("14:30–15:45", DateText.eventRange(start, end, false, BERLIN))
        assertEquals("Ganztägig", DateText.eventRange(start, end, true, BERLIN))
        assertEquals("Morgen, 14:30", DateText.relativeDayTime(ms(2026, 3, 13, 14, 30), true, thursday, BERLIN))
        assertEquals("Morgen", DateText.relativeDayTime(ms(2026, 3, 13), false, thursday, BERLIN))
    }

    @Test
    fun `Dauer in Minuten lesbar`() {
        assertEquals("25 Min.", TextUtils.formatMinutes(25))
        assertEquals("1 Std.", TextUtils.formatMinutes(60))
        assertEquals("1 Std. 30 Min.", TextUtils.formatMinutes(90))
    }

    @Test
    fun `Deep-Links haben die erwartete Form`() {
        assertEquals("kopffrei://note/new?voice=true&thought=false", DeepLinks.newNote(voice = true))
        assertEquals("kopffrei://note/open/abc", DeepLinks.openNote("abc"))
        assertEquals("kopffrei://task/open/t1", DeepLinks.openTask("t1"))
        assertEquals("kopffrei://event/open/e1", DeepLinks.openEvent("e1"))
        assertEquals("kopffrei://focus?minutes=25&autostart=true", DeepLinks.focus(25, true))
        assertEquals("kopffrei://focus", DeepLinks.focus())
    }
}

class RecurrenceTextTest {
    @Test
    fun `Wiederholungen werden lesbar beschrieben`() {
        val none = de.kopffrei.core.common.model.RecurrenceRule.NONE
        assertEquals("Einmalig", de.kopffrei.core.common.text.RecurrenceText.describe(none))
        val daily = de.kopffrei.core.common.model.RecurrenceRule(de.kopffrei.core.common.model.Frequency.DAILY)
        assertEquals("Jeden Tag", de.kopffrei.core.common.text.RecurrenceText.describe(daily))
        val biweekly = de.kopffrei.core.common.model.RecurrenceRule(de.kopffrei.core.common.model.Frequency.WEEKLY, 2)
        assertEquals("Alle 2 Wochen", de.kopffrei.core.common.text.RecurrenceText.describe(biweekly))
        val monthly = de.kopffrei.core.common.model.RecurrenceRule(de.kopffrei.core.common.model.Frequency.MONTHLY)
        assertEquals("Jeden Monat", de.kopffrei.core.common.text.RecurrenceText.describe(monthly))
        assertEquals("Täglich", de.kopffrei.core.common.text.RecurrenceText.frequencyLabel(de.kopffrei.core.common.model.Frequency.DAILY))
    }
}
