package de.kopffrei.app.navigation

import de.kopffrei.core.common.DeepLinks

/**
 * Alle Routen der App. Die Muster (`…_PATTERN`) stehen im NavHost; die Funktionen bauen daraus
 * konkrete Adressen. Alle Werte sind UUIDs, Zahlen oder Wahrheitswerte – deshalb ist kein
 * URL-Escaping nötig. Reine Kotlin-Logik ohne Android-Klassen, damit sie lokal testbar ist.
 */
object Routes {
    // Hauptnavigation
    const val TODAY = "today"
    const val NOTES = "notes"
    const val CALENDAR = "calendar"
    const val FOCUS = "focus"
    const val MORE = "more"

    // Weitere Bildschirme
    const val TASKS = "tasks"
    const val JOURNAL = "journal"
    const val SETTINGS = "settings"
    const val SOS = "sos"
    const val BRAIN_DUMP = "braindump"

    // Editoren (mit optionalen Argumenten)
    const val NOTE_EDIT = "note_edit"
    const val TASK_EDIT = "task_edit"
    const val EVENT_EDIT = "event_edit"
    const val JOURNAL_EDIT = "journal_edit"

    // Argumentnamen (müssen zu den SavedStateHandle-Schlüsseln der ViewModels passen)
    const val ARG_NOTE_ID = "noteId"
    const val ARG_VOICE = "voice"
    const val ARG_THOUGHT = "thought"
    const val ARG_TASK_ID = "taskId"
    const val ARG_EVENT_ID = "eventId"
    const val ARG_FROM_NOTE = "fromNote"
    const val ARG_DATE = "date"
    const val ARG_ENTRY_ID = "entryId"
    const val ARG_MINUTES = "minutes"
    const val ARG_AUTOSTART = "autostart"

    // Muster für den NavHost
    const val FOCUS_PATTERN = "$FOCUS?$ARG_MINUTES={$ARG_MINUTES}&$ARG_AUTOSTART={$ARG_AUTOSTART}"
    const val NOTE_EDIT_PATTERN =
        "$NOTE_EDIT?$ARG_NOTE_ID={$ARG_NOTE_ID}&$ARG_VOICE={$ARG_VOICE}&$ARG_THOUGHT={$ARG_THOUGHT}"
    const val TASK_EDIT_PATTERN = "$TASK_EDIT?$ARG_TASK_ID={$ARG_TASK_ID}&$ARG_FROM_NOTE={$ARG_FROM_NOTE}"
    const val EVENT_EDIT_PATTERN =
        "$EVENT_EDIT?$ARG_EVENT_ID={$ARG_EVENT_ID}&$ARG_FROM_NOTE={$ARG_FROM_NOTE}&$ARG_DATE={$ARG_DATE}"
    const val JOURNAL_EDIT_PATTERN = "$JOURNAL_EDIT?$ARG_ENTRY_ID={$ARG_ENTRY_ID}"

    // Deep-Link-Muster (Platzhalter in {…}); die Gegenstücke ohne Platzhalter stehen in DeepLinks (core:common).
    const val LINK_NOTE_NEW = "${DeepLinks.SCHEME}://note/new?$ARG_VOICE={$ARG_VOICE}&$ARG_THOUGHT={$ARG_THOUGHT}"
    const val LINK_NOTE_OPEN = "${DeepLinks.SCHEME}://note/open/{$ARG_NOTE_ID}"
    const val LINK_TASK_OPEN = "${DeepLinks.SCHEME}://task/open/{$ARG_TASK_ID}"
    const val LINK_EVENT_OPEN = "${DeepLinks.SCHEME}://event/open/{$ARG_EVENT_ID}"
    const val LINK_FOCUS = "${DeepLinks.FOCUS_BASE}?$ARG_MINUTES={$ARG_MINUTES}&$ARG_AUTOSTART={$ARG_AUTOSTART}"

    /** Reihenfolge der Hauptnavigation: Heute | Notizen | Kalender | Fokus | Mehr. */
    val TOP_LEVEL: List<String> = listOf(TODAY, NOTES, CALENDAR, FOCUS, MORE)

    /** Bildschirme, auf denen der schwebende Schnellaktions-Knopf sichtbar ist. */
    val WITH_QUICK_ACTION: Set<String> = TOP_LEVEL.toSet() + TASKS

    fun noteEdit(noteId: String? = null, voice: Boolean = false, thought: Boolean = false): String =
        build(NOTE_EDIT, ARG_NOTE_ID to noteId, ARG_VOICE to voice, ARG_THOUGHT to thought)

    fun taskEdit(taskId: String? = null, fromNote: String? = null): String =
        build(TASK_EDIT, ARG_TASK_ID to taskId, ARG_FROM_NOTE to fromNote)

    /** [date] = Beginn des Tages in Millisekunden (vorbelegtes Startdatum), sonst heute. */
    fun eventEdit(eventId: String? = null, fromNote: String? = null, date: Long? = null): String =
        build(EVENT_EDIT, ARG_EVENT_ID to eventId, ARG_FROM_NOTE to fromNote, ARG_DATE to date)

    fun journalEdit(entryId: String? = null): String = build(JOURNAL_EDIT, ARG_ENTRY_ID to entryId)

    fun focus(minutes: Int? = null, autostart: Boolean = false): String =
        if (minutes == null) FOCUS else build(FOCUS, ARG_MINUTES to minutes, ARG_AUTOSTART to autostart)

    /** Name des Bildschirms ohne Argumente („focus?minutes={minutes}…“ wird zu „focus“). */
    fun baseOf(route: String?): String? = route?.substringBefore('?')

    private fun build(base: String, vararg args: Pair<String, Any?>): String {
        val query = args.filter { it.second != null }.joinToString("&") { "${it.first}=${it.second}" }
        return if (query.isEmpty()) base else "$base?$query"
    }
}
