package de.kopffrei.core.common.security

/**
 * Regeln der optionalen App-Sperre (PIN/Biometrie des Geräts). Reine Logik, damit sie ohne Android
 * testbar ist. Zeiten sind monotone Millisekunden (SystemClock.elapsedRealtime), keine Uhrzeit.
 */
object AppLockPolicy {
    /** Nach so langer Zeit im Hintergrund wird die App wieder gesperrt. */
    const val RELOCK_AFTER_MS: Long = 60_000L

    /** True, wenn statt der Inhalte der Sperrbildschirm gezeigt werden muss. */
    fun needsUnlock(lockEnabled: Boolean, unlocked: Boolean): Boolean = lockEnabled && !unlocked

    /**
     * True, wenn die App nach der Zeit im Hintergrund wieder gesperrt sein soll.
     * [stoppedAtMs] = null heißt „war nie im Hintergrund“. Läuft die Zeit rückwärts (Neustart des
     * Zählers), wird im Zweifel gesperrt.
     */
    fun shouldRelock(stoppedAtMs: Long?, nowMs: Long, graceMs: Long = RELOCK_AFTER_MS): Boolean {
        if (stoppedAtMs == null) return false
        if (nowMs < stoppedAtMs) return true
        return nowMs - stoppedAtMs >= graceMs
    }
}
