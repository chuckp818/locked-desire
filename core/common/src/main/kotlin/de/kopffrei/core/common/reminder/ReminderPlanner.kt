package de.kopffrei.core.common.reminder

import de.kopffrei.core.common.model.RecurrenceRule
import de.kopffrei.core.common.recurrence.Recurrence
import de.kopffrei.core.common.time.Ids
import de.kopffrei.core.common.time.TimeUtils
import java.time.ZoneId

/** Bezugszeitpunkt (Start eines Termins bzw. Fälligkeit einer Aufgabe) samt Wiederholungsregel. */
data class ReminderAnchor(
    val startMillis: Long,
    val rule: RecurrenceRule = RecurrenceRule.NONE,
)

/**
 * Bearbeitbare Beschreibung einer Erinnerungsstufe (UI-Modell).
 * Entweder [offsetMinutes] vor dem Bezugszeitpunkt oder ein fester Zeitpunkt [fixedAt].
 */
data class ReminderDraft(
    val id: String = Ids.newId(),
    val label: String = "",
    val offsetMinutes: Int? = null,
    val fixedAt: Long? = null,
    val repeatEveryMinutes: Int? = null,
)

/** Reine Rechenlogik: wann soll eine Erinnerung als Nächstes ausgelöst werden? */
object ReminderPlanner {
    /** Verpasste Erinnerungen (z. B. Gerät war aus) werden bis zu 6 Stunden später noch nachgeholt. */
    const val GRACE_MS = 6 * TimeUtils.HOUR_MS

    /** Höchstzahl automatischer Wiederholungen, wenn nicht bestätigt wird. */
    const val MAX_REPEATS = 5

    /**
     * @param lastTriggeredAt Zeitpunkt, für den bereits ausgelöst wurde (verhindert Doppelauslösung).
     * @param snoozedUntil    Folgezeitpunkt nach „Später“ bzw. automatischer Wiederholung.
     * @return Zeitpunkt in Millisekunden (kann in der Vergangenheit liegen = sofort auslösen) oder null.
     */
    fun nextTrigger(
        fixedAt: Long?,
        offsetMinutes: Int?,
        anchor: ReminderAnchor?,
        lastTriggeredAt: Long?,
        snoozedUntil: Long?,
        now: Long,
        zone: ZoneId,
    ): Long? {
        if (snoozedUntil != null) return snoozedUntil
        val earliest = maxOf(now - GRACE_MS, lastTriggeredAt?.plus(1) ?: Long.MIN_VALUE)
        if (offsetMinutes != null && anchor != null) {
            val offsetMs = offsetMinutes * TimeUtils.MINUTE_MS
            val start = Recurrence.firstOccurrenceAtOrAfter(
                anchor.startMillis, anchor.rule, zone, earliest + offsetMs,
            ) ?: return null
            return start - offsetMs
        }
        if (fixedAt != null) return fixedAt.takeIf { it >= earliest }
        return null
    }

    /** Vorschläge für Stufen aus Vorbereitungs- und Fahrzeit („Fertigmachen“, „Losgehen“). */
    fun stagesFromTimes(prepMinutes: Int, travelMinutes: Int): List<ReminderDraft> {
        val stages = mutableListOf<ReminderDraft>()
        if (prepMinutes > 0) {
            stages += ReminderDraft(label = "Fertigmachen", offsetMinutes = prepMinutes + travelMinutes)
        }
        if (travelMinutes > 0) {
            stages += ReminderDraft(label = "Losgehen", offsetMinutes = travelMinutes)
        }
        return stages
    }
}
