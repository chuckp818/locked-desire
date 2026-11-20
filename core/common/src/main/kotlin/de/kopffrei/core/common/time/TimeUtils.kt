package de.kopffrei.core.common.time

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/** Erzeugt neue eindeutige IDs (UUID als Text). */
object Ids {
    fun newId(): String = UUID.randomUUID().toString()
}

object TimeUtils {
    const val MINUTE_MS = 60_000L
    const val HOUR_MS = 3_600_000L
    const val DAY_MS = 86_400_000L

    /** Uhrzeit, zu der „nur Datum“-Aufgaben für Erinnerungen als fällig gelten. */
    val DEFAULT_DUE_TIME: LocalTime = LocalTime.of(9, 0)

    fun nowMillis(): Long = System.currentTimeMillis()

    fun systemZone(): ZoneId = ZoneId.systemDefault()

    fun toLocalDateTime(millis: Long, zone: ZoneId): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)

    fun toLocalDate(millis: Long, zone: ZoneId): LocalDate = toLocalDateTime(millis, zone).toLocalDate()

    fun startOfDay(date: LocalDate, zone: ZoneId): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    fun startOfDay(millis: Long, zone: ZoneId): Long = startOfDay(toLocalDate(millis, zone), zone)

    fun millisOf(date: LocalDate, time: LocalTime, zone: ZoneId): Long =
        date.atTime(time).atZone(zone).toInstant().toEpochMilli()

    fun epochDay(millis: Long, zone: ZoneId): Long = toLocalDate(millis, zone).toEpochDay()

    fun minutesOfDay(millis: Long, zone: ZoneId): Int {
        val t = toLocalDateTime(millis, zone)
        return t.hour * 60 + t.minute
    }

    /** Wie viele Minuten die Zeitspanne dauert (mindestens 0). */
    fun minutesBetween(startMillis: Long, endMillis: Long): Long =
        ((endMillis - startMillis) / MINUTE_MS).coerceAtLeast(0)
}

/** Bezugszeitpunkt für Erinnerungen an Aufgaben. */
object TaskTime {
    /** Bei Aufgaben ohne Uhrzeit zählt 09:00 Uhr als Bezugszeit. */
    fun anchorMillis(dueAt: Long, hasTime: Boolean, zone: ZoneId): Long =
        if (hasTime) {
            dueAt
        } else {
            TimeUtils.millisOf(TimeUtils.toLocalDate(dueAt, zone), TimeUtils.DEFAULT_DUE_TIME, zone)
        }
}
