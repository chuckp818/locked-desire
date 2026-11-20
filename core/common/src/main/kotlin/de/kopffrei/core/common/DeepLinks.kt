package de.kopffrei.core.common

/**
 * Deep-Link-Adressen der App. Widgets und Benachrichtigungen öffnen die App darüber, ohne die
 * Activity-Klasse zu kennen. Die Muster müssen zu den Routen in der Navigation (Modul app) passen.
 */
object DeepLinks {
    const val SCHEME = "kopffrei"

    const val TODAY = "kopffrei://today"
    const val NOTES = "kopffrei://notes"
    const val CALENDAR = "kopffrei://calendar"
    const val TASKS = "kopffrei://tasks"
    const val FOCUS_BASE = "kopffrei://focus"
    const val JOURNAL = "kopffrei://journal"
    const val SETTINGS = "kopffrei://settings"

    const val NEW_TASK = "kopffrei://task/new"
    const val NEW_EVENT = "kopffrei://event/new"
    const val BRAIN_DUMP = "kopffrei://braindump"

    fun newNote(voice: Boolean = false, thought: Boolean = false): String =
        "kopffrei://note/new?voice=$voice&thought=$thought"

    fun openNote(id: String): String = "kopffrei://note/open/$id"

    fun openTask(id: String): String = "kopffrei://task/open/$id"

    fun openEvent(id: String): String = "kopffrei://event/open/$id"

    fun focus(minutes: Int? = null, autostart: Boolean = false): String =
        if (minutes == null) FOCUS_BASE else "$FOCUS_BASE?minutes=$minutes&autostart=$autostart"
}
