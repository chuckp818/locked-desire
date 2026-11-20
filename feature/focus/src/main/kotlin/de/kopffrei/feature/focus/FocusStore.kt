package de.kopffrei.feature.focus

import android.content.Context
import de.kopffrei.core.common.focus.FocusSession

/**
 * Merkt sich den Fokus-Timer (nur Endzeitpunkt bzw. Restzeit), damit er App-Neustarts übersteht.
 * Kein Datenbank-Eintrag nötig – es gibt höchstens eine laufende Sitzung.
 */
internal class FocusStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("focus_timer", Context.MODE_PRIVATE)

    fun load(): FocusSession? {
        val total = prefs.getLong(KEY_TOTAL, 0L)
        if (total <= 0L) return null
        val endAt = prefs.getLong(KEY_END_AT, NONE).takeIf { it != NONE }
        val paused = prefs.getLong(KEY_PAUSED, NONE).takeIf { it != NONE }
        return FocusSession(totalMs = total, endAtMs = endAt, pausedRemainingMs = paused)
    }

    fun save(session: FocusSession) {
        prefs.edit()
            .putLong(KEY_TOTAL, session.totalMs)
            .putLong(KEY_END_AT, session.endAtMs ?: NONE)
            .putLong(KEY_PAUSED, session.pausedRemainingMs ?: NONE)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_TOTAL = "totalMs"
        const val KEY_END_AT = "endAtMs"
        const val KEY_PAUSED = "pausedRemainingMs"
        const val NONE = -1L
    }
}
