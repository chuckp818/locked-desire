package de.kopffrei.core.common.text

import de.kopffrei.core.common.model.Frequency
import de.kopffrei.core.common.model.RecurrenceRule

/** Lesbare deutsche Beschreibung von Wiederholungsregeln. */
object RecurrenceText {
    fun frequencyLabel(frequency: Frequency): String = when (frequency) {
        Frequency.NONE -> "Keine Wiederholung"
        Frequency.DAILY -> "Täglich"
        Frequency.WEEKLY -> "Wöchentlich"
        Frequency.MONTHLY -> "Monatlich"
        Frequency.YEARLY -> "Jährlich"
    }

    /** „Jeden Tag“, „Alle 2 Wochen“, „Einmalig“ … */
    fun describe(rule: RecurrenceRule): String {
        if (!rule.isRecurring) return "Einmalig"
        if (rule.interval <= 1) {
            return when (rule.frequency) {
                Frequency.DAILY -> "Jeden Tag"
                Frequency.WEEKLY -> "Jede Woche"
                Frequency.MONTHLY -> "Jeden Monat"
                Frequency.YEARLY -> "Jedes Jahr"
                Frequency.NONE -> "Einmalig"
            }
        }
        val unit = when (rule.frequency) {
            Frequency.DAILY -> "Tage"
            Frequency.WEEKLY -> "Wochen"
            Frequency.MONTHLY -> "Monate"
            Frequency.YEARLY -> "Jahre"
            Frequency.NONE -> ""
        }
        return "Alle ${rule.interval} $unit"
    }
}
