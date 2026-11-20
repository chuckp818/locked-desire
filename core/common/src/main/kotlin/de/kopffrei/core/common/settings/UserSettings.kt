package de.kopffrei.core.common.settings

import de.kopffrei.core.common.model.AccentPreset
import de.kopffrei.core.common.model.DashboardCard
import de.kopffrei.core.common.model.QuickAction
import de.kopffrei.core.common.model.ThemeMode

/** Alle Einstellungen der App (werden lokal in DataStore gespeichert). */
data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val accent: AccentPreset = AccentPreset.TURQUOISE,
    /** Zusätzlicher Skalierungsfaktor für Schrift (multipliziert mit der Systemeinstellung). */
    val fontScale: Float = 1.0f,
    val largeButtons: Boolean = false,
    val reduceAnimations: Boolean = false,
    val biometricLock: Boolean = false,
    val snoozeMinutes: Int = 10,
    val dashboardOrder: List<DashboardCard> = DashboardCard.entries.toList(),
    val dashboardHidden: Set<DashboardCard> = emptySet(),
    val dashboardCompact: Boolean = false,
    val showMotivation: Boolean = true,
    val quickAction: QuickAction = QuickAction.NOTE,
    /** Schlüssel der „Mehr“-Einträge, die auf der Startseite als Schnellzugriff erscheinen. */
    val pinnedShortcuts: Set<String> = emptySet(),
) {
    /** Sichtbare Startseiten-Karten in der gewählten Reihenfolge (fehlende Karten werden angehängt). */
    fun visibleDashboardCards(): List<DashboardCard> {
        val ordered = dashboardOrder + DashboardCard.entries.filter { it !in dashboardOrder }
        return ordered.distinct().filter { it !in dashboardHidden }
    }
}

/** Hilfen zum Anpassen der Startseite (Reihenfolge, Verschieben). */
object DashboardLayout {
    /** Alle Karten in gewählter Reihenfolge (auch ausgeblendete; fehlende werden angehängt). */
    fun fullOrder(settings: UserSettings): List<DashboardCard> =
        (settings.dashboardOrder + DashboardCard.entries.filter { it !in settings.dashboardOrder }).distinct()

    /** Verschiebt [card] um [delta] Plätze (negativ = nach oben); an den Rändern passiert nichts. */
    fun move(order: List<DashboardCard>, card: DashboardCard, delta: Int): List<DashboardCard> {
        val from = order.indexOf(card)
        if (from < 0) return order
        val to = (from + delta).coerceIn(0, order.lastIndex)
        if (to == from) return order
        return order.toMutableList().also {
            it.removeAt(from)
            it.add(to, card)
        }
    }
}
