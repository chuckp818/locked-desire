package de.kopffrei.core.common.calendar

import de.kopffrei.core.common.text.DateText
import de.kopffrei.core.common.time.TimeUtils
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Ansichten des Kalenders. */
enum class CalendarMode(val label: String) {
    MONTH("Monat"),
    WEEK("Woche"),
    DAY("Tag"),
    AGENDA("Agenda"),
}

/** Reine Rechenlogik für die Kalenderansichten (Wochenbeginn ist Montag). */
object CalendarLogic {
    /** Anzahl der Tage, die die Agenda ab dem gewählten Tag zeigt. */
    const val AGENDA_DAYS = 60

    fun weekStart(date: LocalDate): LocalDate = date.minusDays((date.dayOfWeek.value - 1).toLong())

    fun weekDays(date: LocalDate): List<LocalDate> {
        val start = weekStart(date)
        return (0L until 7L).map { start.plusDays(it) }
    }

    /** Alle Tage des Monatsrasters: volle Wochen von Montag bis Sonntag (5 oder 6 Zeilen). */
    fun monthGrid(month: YearMonth): List<LocalDate> {
        val start = weekStart(month.atDay(1))
        val last = month.atEndOfMonth()
        val days = last.toEpochDay() - start.toEpochDay() + 1
        val weeks = ((days + 6) / 7).toInt()
        return (0L until weeks * 7L).map { start.plusDays(it) }
    }

    /** Verschiebt das gewählte Datum um eine Seite der jeweiligen Ansicht. */
    fun shift(mode: CalendarMode, date: LocalDate, delta: Int): LocalDate = when (mode) {
        CalendarMode.MONTH -> date.plusMonths(delta.toLong())
        CalendarMode.WEEK -> date.plusWeeks(delta.toLong())
        CalendarMode.DAY -> date.plusDays(delta.toLong())
        CalendarMode.AGENDA -> date.plusDays(delta.toLong() * AGENDA_DAYS)
    }

    /** Sichtbarer Zeitraum: Start inklusive, Ende exklusiv. */
    fun visibleRange(mode: CalendarMode, date: LocalDate): Pair<LocalDate, LocalDate> = when (mode) {
        CalendarMode.MONTH -> {
            val grid = monthGrid(YearMonth.from(date))
            grid.first() to grid.last().plusDays(1)
        }
        CalendarMode.WEEK -> weekStart(date) to weekStart(date).plusDays(7)
        CalendarMode.DAY -> date to date.plusDays(1)
        CalendarMode.AGENDA -> date to date.plusDays(AGENDA_DAYS.toLong())
    }

    fun title(mode: CalendarMode, date: LocalDate): String = when (mode) {
        CalendarMode.MONTH -> DateText.monthTitle(YearMonth.from(date))
        CalendarMode.WEEK -> {
            val days = weekDays(date)
            "${DateText.dateShort(days.first())} – ${DateText.dateShort(days.last())}"
        }
        CalendarMode.DAY -> DateText.dateLong(date)
        CalendarMode.AGENDA -> "Agenda ab ${DateText.dateShort(date)}"
    }

    /**
     * Liegt ein Zeitraum (ganz oder teilweise) auf dem Kalendertag [day]?
     * Das Ende ist exklusiv (ganztägige Termine enden am Beginn des Folgetags); ein Termin ohne
     * Dauer zählt zu dem Tag, an dem er beginnt.
     */
    fun overlapsDay(startMillis: Long, endMillis: Long, day: LocalDate, zone: ZoneId): Boolean {
        val dayStart = TimeUtils.startOfDay(day, zone)
        val dayEnd = TimeUtils.startOfDay(day.plusDays(1), zone)
        return if (endMillis <= startMillis) {
            startMillis >= dayStart && startMillis < dayEnd
        } else {
            startMillis < dayEnd && endMillis > dayStart
        }
    }
}
