package de.kopffrei.core.common.recurrence

import de.kopffrei.core.common.model.Frequency
import de.kopffrei.core.common.model.RecurrenceRule
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Berechnung von Wiederholungen. Alle Zeiten in Millisekunden (UTC-Epoche); die Wiederholung folgt
 * der lokalen Uhrzeit in [ZoneId], bleibt also über Sommerzeitwechsel hinweg zur gleichen Wanduhrzeit.
 *
 * Monatliche Wiederholungen werden immer vom ersten Termin aus gerechnet (31. Januar → 28. Februar →
 * 31. März), damit der Starttag nicht „wegdriftet“.
 */
object Recurrence {
    private const val MAX_ITERATIONS = 5_000

    private fun step(rule: RecurrenceRule): Long = rule.interval.coerceAtLeast(1).toLong()

    /** Startzeitpunkt des Termins mit Nummer [index] (0 = erster Termin). */
    fun occurrenceAt(startMillis: Long, rule: RecurrenceRule, zone: ZoneId, index: Long): Long {
        val base = LocalDateTime.ofInstant(Instant.ofEpochMilli(startMillis), zone)
        val n = index * step(rule)
        val shifted = when (rule.frequency) {
            Frequency.NONE -> base
            Frequency.DAILY -> base.plusDays(n)
            Frequency.WEEKLY -> base.plusWeeks(n)
            Frequency.MONTHLY -> base.plusMonths(n)
            Frequency.YEARLY -> base.plusYears(n)
        }
        return shifted.atZone(zone).toInstant().toEpochMilli()
    }

    private fun withinUntil(candidate: Long, rule: RecurrenceRule): Boolean =
        rule.until == null || candidate <= rule.until

    private fun estimateIndex(startMillis: Long, rule: RecurrenceRule, zone: ZoneId, minStart: Long): Long {
        val s = LocalDateTime.ofInstant(Instant.ofEpochMilli(startMillis), zone)
        val m = LocalDateTime.ofInstant(Instant.ofEpochMilli(minStart), zone)
        val unit = when (rule.frequency) {
            Frequency.WEEKLY -> ChronoUnit.WEEKS
            Frequency.MONTHLY -> ChronoUnit.MONTHS
            Frequency.YEARLY -> ChronoUnit.YEARS
            else -> ChronoUnit.DAYS
        }
        val units = unit.between(s, m)
        return ((units / step(rule)) - 1).coerceAtLeast(0)
    }

    /**
     * Nummer des ersten Termins, dessen Start >= [minStartMillis] ist, oder null wenn es keinen
     * (mehr) gibt (z. B. wegen [RecurrenceRule.until]).
     */
    fun indexAtOrAfter(startMillis: Long, rule: RecurrenceRule, zone: ZoneId, minStartMillis: Long): Long? {
        if (!rule.isRecurring) return if (startMillis >= minStartMillis) 0L else null
        var index = if (minStartMillis <= startMillis) 0L else estimateIndex(startMillis, rule, zone, minStartMillis)
        var iterations = 0
        while (iterations++ < MAX_ITERATIONS) {
            val candidate = occurrenceAt(startMillis, rule, zone, index)
            if (!withinUntil(candidate, rule)) return null
            if (candidate >= minStartMillis) return index
            index++
        }
        return null
    }

    /** Startzeitpunkt des ersten Termins mit Start >= [minStartMillis]. */
    fun firstOccurrenceAtOrAfter(startMillis: Long, rule: RecurrenceRule, zone: ZoneId, minStartMillis: Long): Long? {
        val index = indexAtOrAfter(startMillis, rule, zone, minStartMillis) ?: return null
        return occurrenceAt(startMillis, rule, zone, index)
    }

    /**
     * Alle Terme, die den Zeitraum [fromMillis, toMillis) berühren, als Startzeitpunkte. Ein Termin mit
     * Dauer [durationMillis] zählt, wenn er im Zeitraum beginnt oder hineinreicht.
     */
    fun occurrencesBetween(
        startMillis: Long,
        durationMillis: Long,
        rule: RecurrenceRule,
        zone: ZoneId,
        fromMillis: Long,
        toMillis: Long,
        limit: Int = 500,
    ): List<Long> {
        val minStart = if (durationMillis > 0) fromMillis - durationMillis + 1 else fromMillis
        var index = indexAtOrAfter(startMillis, rule, zone, minStart) ?: return emptyList()
        val result = ArrayList<Long>()
        while (result.size < limit) {
            val s = occurrenceAt(startMillis, rule, zone, index)
            if (s >= toMillis || !withinUntil(s, rule)) break
            result += s
            if (!rule.isRecurring) break
            index++
        }
        return result
    }

    /** Wiederholt eine fällige Aufgabe: nächster Termin nach [dueMillis], aber nicht vor [notBeforeMillis]. */
    fun nextDue(dueMillis: Long, rule: RecurrenceRule, zone: ZoneId, notBeforeMillis: Long): Long? {
        if (!rule.isRecurring) return null
        return firstOccurrenceAtOrAfter(dueMillis, rule, zone, maxOf(dueMillis + 1, notBeforeMillis))
    }

    /** Bei COUNT-Angaben (iCalendar): letzter Startzeitpunkt der Serie mit [count] Terminen. */
    fun lastStartForCount(startMillis: Long, rule: RecurrenceRule, zone: ZoneId, count: Int): Long =
        occurrenceAt(startMillis, rule.copy(until = null), zone, (count - 1).coerceAtLeast(0).toLong())
}
