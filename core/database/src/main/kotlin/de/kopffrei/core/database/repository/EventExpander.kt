package de.kopffrei.core.database.repository

import de.kopffrei.core.common.recurrence.Recurrence
import de.kopffrei.core.common.time.TimeUtils
import de.kopffrei.core.database.entity.CalendarEventEntity
import java.time.ZoneId

/** Ein konkreter Termin einer Serie (oder ein einmaliger Termin) mit Start/Ende. */
data class EventOccurrence(
    val event: CalendarEventEntity,
    val startMillis: Long,
    val endMillis: Long,
)

/** Berechnet konkrete Termine aus gespeicherten Terminen und Serien. */
object EventExpander {
    fun expand(
        events: List<CalendarEventEntity>,
        fromMillis: Long,
        toMillis: Long,
        zone: ZoneId,
        limitPerEvent: Int = 500,
    ): List<EventOccurrence> =
        events.flatMap { event ->
            val duration = (event.endAt - event.startAt).coerceAtLeast(0)
            Recurrence.occurrencesBetween(
                event.startAt, duration, event.recurrence, zone, fromMillis, toMillis, limitPerEvent,
            ).map { start -> EventOccurrence(event, start, start + duration) }
        }.sortedWith(compareBy({ it.startMillis }, { it.event.title }))

    /** Der nächste (oder gerade laufende) Termin ab [nowMillis] innerhalb von [horizonDays] Tagen. */
    fun next(
        events: List<CalendarEventEntity>,
        nowMillis: Long,
        zone: ZoneId,
        horizonDays: Int = 366,
    ): EventOccurrence? =
        expand(events, nowMillis, nowMillis + horizonDays * TimeUtils.DAY_MS, zone, limitPerEvent = 1)
            .firstOrNull()

    /** Alle Termine eines Kalendertags. */
    fun onDay(events: List<CalendarEventEntity>, dayStartMillis: Long, zone: ZoneId): List<EventOccurrence> =
        expand(events, dayStartMillis, dayStartMillis + TimeUtils.DAY_MS, zone)
}
