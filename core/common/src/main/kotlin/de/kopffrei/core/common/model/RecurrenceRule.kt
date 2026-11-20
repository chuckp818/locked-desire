package de.kopffrei.core.common.model

/**
 * Einfache Wiederholungsregel. [until] ist der letzte erlaubte Startzeitpunkt (inklusive) in
 * Millisekunden oder null für „unbegrenzt“.
 *
 * Nicht unterstützt (bewusst, Phase 1): mehrere Wochentage, „n-ter Wochentag im Monat“, Ausnahmen
 * für einzelne Termine einer Serie.
 */
data class RecurrenceRule(
    val frequency: Frequency = Frequency.NONE,
    val interval: Int = 1,
    val until: Long? = null,
) {
    val isRecurring: Boolean get() = frequency != Frequency.NONE

    companion object {
        val NONE = RecurrenceRule()
    }
}
