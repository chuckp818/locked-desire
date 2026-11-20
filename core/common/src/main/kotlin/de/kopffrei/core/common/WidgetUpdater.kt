package de.kopffrei.core.common

/**
 * Wird von den Repositories nach Schreibzugriffen aufgerufen, damit Startbildschirm-Widgets neue Daten
 * anzeigen. Die Implementierung liegt im Modul feature:widgets.
 */
interface WidgetUpdater {
    fun requestUpdate()
}

object NoOpWidgetUpdater : WidgetUpdater {
    override fun requestUpdate() = Unit
}
