package de.kopffrei.core.common.text

import de.kopffrei.core.common.time.TimeUtils
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * Deutsche Datums- und Zeittexte. Bewusst ohne `Locale`-Abhängigkeit, damit die Ausgabe auf jedem
 * Gerät (und in Tests) gleich ist.
 */
object DateText {
    private val WEEKDAYS_SHORT = listOf("Mo.", "Di.", "Mi.", "Do.", "Fr.", "Sa.", "So.")
    private val WEEKDAYS_LONG = listOf(
        "Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag",
    )
    private val MONTHS_LONG = listOf(
        "Januar", "Februar", "März", "April", "Mai", "Juni",
        "Juli", "August", "September", "Oktober", "November", "Dezember",
    )
    private val MONTHS_SHORT = listOf(
        "Jan.", "Feb.", "März", "Apr.", "Mai", "Juni",
        "Juli", "Aug.", "Sept.", "Okt.", "Nov.", "Dez.",
    )

    fun weekdayShort(date: LocalDate): String = WEEKDAYS_SHORT[date.dayOfWeek.value - 1]

    fun weekdayLong(date: LocalDate): String = WEEKDAYS_LONG[date.dayOfWeek.value - 1]

    /** Kurzer Wochentag für Spaltenköpfe ohne Punkt: „Mo“. */
    fun weekdayLetters(dayOfWeekValue: Int): String = WEEKDAYS_SHORT[dayOfWeekValue - 1].removeSuffix(".")

    fun monthLong(month: Int): String = MONTHS_LONG[month - 1]

    fun monthTitle(month: YearMonth): String = "${monthLong(month.monthValue)} ${month.year}"

    /** „09:05“ aus Minuten seit Mitternacht. */
    fun clock(minutesOfDay: Int): String =
        (minutesOfDay / 60).toString().padStart(2, '0') + ":" + (minutesOfDay % 60).toString().padStart(2, '0')

    fun clock(millis: Long, zone: ZoneId): String = clock(TimeUtils.minutesOfDay(millis, zone))

    /** „Mo., 12. März“ */
    fun dateShort(date: LocalDate): String =
        "${weekdayShort(date)}, ${date.dayOfMonth}. ${MONTHS_SHORT[date.monthValue - 1]}"

    /** „Montag, 12. März 2026“ */
    fun dateLong(date: LocalDate): String =
        "${weekdayLong(date)}, ${date.dayOfMonth}. ${monthLong(date.monthValue)} ${date.year}"

    /** „Heute“, „Morgen“, „Übermorgen“, „Gestern“ oder das kurze Datum. */
    fun relativeDay(date: LocalDate, today: LocalDate): String = when (date.toEpochDay() - today.toEpochDay()) {
        0L -> "Heute"
        1L -> "Morgen"
        2L -> "Übermorgen"
        -1L -> "Gestern"
        else -> dateShort(date)
    }

    /** „Morgen, 14:30“ bzw. „Morgen“ ohne Uhrzeit. */
    fun relativeDayTime(millis: Long, hasTime: Boolean, today: LocalDate, zone: ZoneId): String {
        val day = relativeDay(TimeUtils.toLocalDate(millis, zone), today)
        return if (hasTime) "$day, ${clock(millis, zone)}" else day
    }

    /** Zeitspanne eines Termins: „14:30–15:30“ oder „Ganztägig“. */
    fun eventRange(startMillis: Long, endMillis: Long, allDay: Boolean, zone: ZoneId): String =
        if (allDay) "Ganztägig" else "${clock(startMillis, zone)}–${clock(endMillis, zone)}"
}
