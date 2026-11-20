package de.kopffrei.core.common

import java.time.LocalDateTime
import java.time.ZoneId

/** Gemeinsame Hilfen für die Tests: feste Zeitzone, damit Sommerzeit-Fälle reproduzierbar sind. */
val BERLIN: ZoneId = ZoneId.of("Europe/Berlin")

fun ms(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): Long =
    LocalDateTime.of(year, month, day, hour, minute).atZone(BERLIN).toInstant().toEpochMilli()
