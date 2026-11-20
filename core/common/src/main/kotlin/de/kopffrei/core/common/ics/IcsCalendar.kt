package de.kopffrei.core.common.ics

import de.kopffrei.core.common.model.Frequency
import de.kopffrei.core.common.model.RecurrenceRule
import de.kopffrei.core.common.recurrence.Recurrence
import de.kopffrei.core.common.time.Ids
import de.kopffrei.core.common.time.TimeUtils
import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

data class IcsEvent(
    val uid: String,
    val summary: String,
    val description: String = "",
    val location: String = "",
    val startMillis: Long,
    /** Bei ganztägigen Terminen der (exklusive) Beginn des Folgetags. */
    val endMillis: Long,
    val allDay: Boolean = false,
    val rule: RecurrenceRule = RecurrenceRule.NONE,
)

data class IcsImportResult(
    val events: List<IcsEvent>,
    val skipped: Int,
    val warnings: List<String>,
)

/**
 * Minimaler iCalendar-Export/-Import (RFC 5545) für Termine.
 *
 * Unterstützt: SUMMARY, DESCRIPTION, LOCATION, DTSTART, DTEND, DURATION und einfache RRULE
 * (FREQ, INTERVAL, UNTIL, COUNT). Alles andere (Zeitzonen-Definitionen, Einladungen, Ausnahmen,
 * BYDAY …) wird ignoriert und als Hinweis gemeldet.
 */
object IcsCalendar {
    private val DATE_TIME_UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
    private val DATE_TIME_LOCAL = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val DATE_ONLY = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val DURATION_REGEX = Regex("""^([+-])?P(?:(\d+)W)?(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?)?$""")
    private const val FOLD_AT = 60

    // ---------------------------------------------------------------- Export

    fun export(
        events: List<IcsEvent>,
        zone: ZoneId,
        nowMillis: Long = System.currentTimeMillis(),
    ): String {
        val sb = StringBuilder()
        fun line(s: String) = foldInto(sb, s)

        line("BEGIN:VCALENDAR")
        line("VERSION:2.0")
        line("PRODID:-//KopfFrei//Kalender//DE")
        line("CALSCALE:GREGORIAN")
        for (e in events) {
            line("BEGIN:VEVENT")
            line("UID:${e.uid}")
            line("DTSTAMP:${formatUtc(nowMillis)}")
            when {
                e.allDay -> {
                    line("DTSTART;VALUE=DATE:${formatDate(e.startMillis, zone)}")
                    line("DTEND;VALUE=DATE:${formatDate(e.endMillis, zone)}")
                }
                e.rule.isRecurring -> {
                    // Serien als „schwebende“ Ortszeit, damit die Wanduhrzeit bei Sommerzeit stabil bleibt.
                    line("DTSTART:${formatLocal(e.startMillis, zone)}")
                    line("DTEND:${formatLocal(e.endMillis, zone)}")
                }
                else -> {
                    line("DTSTART:${formatUtc(e.startMillis)}")
                    line("DTEND:${formatUtc(e.endMillis)}")
                }
            }
            line("SUMMARY:${escape(e.summary)}")
            if (e.description.isNotBlank()) line("DESCRIPTION:${escape(e.description)}")
            if (e.location.isNotBlank()) line("LOCATION:${escape(e.location)}")
            if (e.rule.isRecurring) line("RRULE:${buildRrule(e.rule, e.allDay, zone)}")
            line("END:VEVENT")
        }
        line("END:VCALENDAR")
        return sb.toString()
    }

    private fun buildRrule(rule: RecurrenceRule, allDay: Boolean, zone: ZoneId): String {
        val freq = when (rule.frequency) {
            Frequency.DAILY -> "DAILY"
            Frequency.WEEKLY -> "WEEKLY"
            Frequency.MONTHLY -> "MONTHLY"
            Frequency.YEARLY -> "YEARLY"
            Frequency.NONE -> "DAILY"
        }
        val parts = mutableListOf("FREQ=$freq")
        if (rule.interval > 1) parts += "INTERVAL=${rule.interval}"
        rule.until?.let { until ->
            parts += "UNTIL=" + if (allDay) formatDate(until, zone) else formatLocal(until, zone)
        }
        return parts.joinToString(";")
    }

    private fun formatUtc(millis: Long): String =
        DATE_TIME_UTC.format(java.time.Instant.ofEpochMilli(millis).atOffset(ZoneOffset.UTC))

    private fun formatLocal(millis: Long, zone: ZoneId): String =
        DATE_TIME_LOCAL.format(TimeUtils.toLocalDateTime(millis, zone))

    private fun formatDate(millis: Long, zone: ZoneId): String =
        DATE_ONLY.format(TimeUtils.toLocalDate(millis, zone))

    private fun escape(text: String): String =
        text.replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace("\r\n", "\\n")
            .replace("\n", "\\n")
            .replace("\r", "\\n")

    private fun foldInto(sb: StringBuilder, line: String) {
        var rest = line
        var first = true
        while (rest.length > FOLD_AT) {
            var cut = FOLD_AT
            // Nicht mitten in ein Surrogatpaar schneiden.
            if (Character.isHighSurrogate(rest[cut - 1])) cut--
            sb.append(if (first) "" else " ").append(rest, 0, cut).append("\r\n")
            rest = rest.substring(cut)
            first = false
        }
        sb.append(if (first) "" else " ").append(rest).append("\r\n")
    }

    // ---------------------------------------------------------------- Import

    private data class ContentLine(val name: String, val params: Map<String, String>, val value: String)

    fun import(text: String, zone: ZoneId): IcsImportResult {
        val events = mutableListOf<IcsEvent>()
        val warnings = linkedSetOf<String>()
        var skipped = 0
        var current: MutableList<ContentLine>? = null
        var subDepth = 0

        for (raw in unfold(text)) {
            val upper = raw.trim().uppercase()
            val props = current
            when {
                upper == "BEGIN:VEVENT" -> {
                    current = mutableListOf()
                    subDepth = 0
                }
                upper == "END:VEVENT" && props != null && subDepth == 0 -> {
                    val event = parseEvent(props, zone, warnings)
                    if (event != null) events += event else skipped++
                    current = null
                }
                props != null && upper.startsWith("BEGIN:") -> subDepth++
                props != null && upper.startsWith("END:") -> if (subDepth > 0) subDepth--
                props != null && subDepth == 0 -> {
                    val parsed = parseContentLine(raw)
                    if (parsed != null) props.add(parsed)
                }
            }
        }
        return IcsImportResult(events, skipped, warnings.toList())
    }

    private fun unfold(text: String): List<String> {
        val result = mutableListOf<String>()
        for (line in text.replace("\r\n", "\n").replace('\r', '\n').split('\n')) {
            if ((line.startsWith(" ") || line.startsWith("\t")) && result.isNotEmpty()) {
                result[result.lastIndex] = result.last() + line.substring(1)
            } else {
                result += line
            }
        }
        return result
    }

    private fun parseContentLine(line: String): ContentLine? {
        val colon = line.indexOf(':')
        if (colon <= 0) return null
        val head = line.substring(0, colon)
        val value = line.substring(colon + 1)
        val headParts = head.split(';')
        val name = headParts.first().trim().uppercase()
        val params = headParts.drop(1).mapNotNull { p ->
            val eq = p.indexOf('=')
            if (eq <= 0) null else p.substring(0, eq).trim().uppercase() to p.substring(eq + 1).trim().trim('"')
        }.toMap()
        return ContentLine(name, params, value)
    }

    private fun parseEvent(props: List<ContentLine>, zone: ZoneId, warnings: MutableSet<String>): IcsEvent? {
        fun find(name: String): ContentLine? = props.firstOrNull { it.name == name }

        val startProp = find("DTSTART") ?: return null
        val (startMillis, allDay) = parseDateTime(startProp, zone) ?: return null
        val endProp = find("DTEND")
        val durationProp = find("DURATION")
        val endMillis: Long = when {
            endProp != null -> parseDateTime(endProp, zone)?.first ?: return null
            durationProp != null -> startMillis + (parseDuration(durationProp.value) ?: 0L)
            allDay -> TimeUtils.startOfDay(TimeUtils.toLocalDate(startMillis, zone).plusDays(1), zone)
            else -> startMillis
        }.let { if (it < startMillis) startMillis else it }

        val rule = find("RRULE")?.let { parseRrule(it.value, startMillis, allDay, zone, warnings) }
            ?: RecurrenceRule.NONE

        return IcsEvent(
            uid = find("UID")?.value?.takeIf { it.isNotBlank() } ?: Ids.newId(),
            summary = unescape(find("SUMMARY")?.value.orEmpty()).ifBlank { "(ohne Titel)" },
            description = unescape(find("DESCRIPTION")?.value.orEmpty()),
            location = unescape(find("LOCATION")?.value.orEmpty()),
            startMillis = startMillis,
            endMillis = endMillis,
            allDay = allDay,
            rule = rule,
        )
    }

    /** @return Zeitpunkt in Millisekunden und ob es ein reines Datum (ganztägig) ist. */
    private fun parseDateTime(prop: ContentLine, zone: ZoneId): Pair<Long, Boolean>? {
        val value = prop.value.trim()
        return try {
            when {
                prop.params["VALUE"] == "DATE" || value.length == 8 ->
                    TimeUtils.startOfDay(LocalDate.parse(value, DATE_ONLY), zone) to true
                value.endsWith("Z", ignoreCase = true) ->
                    LocalDateTime.parse(value.dropLast(1), DATE_TIME_LOCAL)
                        .toInstant(ZoneOffset.UTC).toEpochMilli() to false
                else -> {
                    val eventZone = prop.params["TZID"]?.let { id ->
                        try {
                            ZoneId.of(id)
                        } catch (_: DateTimeException) {
                            null
                        }
                    } ?: zone
                    LocalDateTime.parse(value, DATE_TIME_LOCAL).atZone(eventZone).toInstant().toEpochMilli() to false
                }
            }
        } catch (_: DateTimeException) {
            null
        }
    }

    private fun parseDuration(value: String): Long? {
        val m = DURATION_REGEX.matchEntire(value.trim()) ?: return null
        fun g(i: Int) = m.groupValues[i].toLongOrNull() ?: 0L
        val ms = g(2) * 7 * TimeUtils.DAY_MS + g(3) * TimeUtils.DAY_MS + g(4) * TimeUtils.HOUR_MS +
            g(5) * TimeUtils.MINUTE_MS + g(6) * 1000L
        return if (m.groupValues[1] == "-") -ms else ms
    }

    private fun parseRrule(
        value: String,
        startMillis: Long,
        allDay: Boolean,
        zone: ZoneId,
        warnings: MutableSet<String>,
    ): RecurrenceRule {
        val parts = value.split(';').mapNotNull { p ->
            val eq = p.indexOf('=')
            if (eq <= 0) null else p.substring(0, eq).trim().uppercase() to p.substring(eq + 1).trim()
        }.toMap()
        val frequency = when (parts["FREQ"]?.uppercase()) {
            "DAILY" -> Frequency.DAILY
            "WEEKLY" -> Frequency.WEEKLY
            "MONTHLY" -> Frequency.MONTHLY
            "YEARLY" -> Frequency.YEARLY
            else -> {
                warnings += "Wiederholungsart wird nicht unterstützt und wurde ignoriert."
                return RecurrenceRule.NONE
            }
        }
        val interval = parts["INTERVAL"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        var rule = RecurrenceRule(frequency, interval, null)

        val untilText = parts["UNTIL"]
        val countText = parts["COUNT"]?.toIntOrNull()
        if (untilText != null) {
            val prop = ContentLine("UNTIL", emptyMap(), untilText)
            val until = parseDateTime(prop, zone)?.first
            if (until != null) {
                // Bei Datums-UNTIL gilt der ganze Tag als eingeschlossen.
                rule = rule.copy(until = if (allDay || untilText.length == 8) until + TimeUtils.DAY_MS - 1 else until)
            }
        } else if (countText != null && countText > 0) {
            rule = rule.copy(until = Recurrence.lastStartForCount(startMillis, rule, zone, countText))
        }
        val unsupported = listOf("BYDAY", "BYMONTHDAY", "BYMONTH", "BYSETPOS", "BYYEARDAY", "BYWEEKNO")
        if (unsupported.any { it in parts }) {
            warnings += "Wiederholungsdetails wie BYDAY werden nicht unterstützt (es wird einfach wiederholt)."
        }
        return rule
    }

    private fun unescape(text: String): String {
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '\\' && i + 1 < text.length) {
                when (val n = text[i + 1]) {
                    'n', 'N' -> sb.append('\n')
                    else -> sb.append(n)
                }
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }
}
