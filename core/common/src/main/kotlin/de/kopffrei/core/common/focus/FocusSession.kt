package de.kopffrei.core.common.focus

import de.kopffrei.core.common.time.TimeUtils

/**
 * Zustand eines Fokus-Timers. Es wird kein Herunterzählen gespeichert, sondern der Endzeitpunkt
 * ([endAtMs], solange er läuft) bzw. die Restzeit ([pausedRemainingMs], solange er pausiert). So
 * bleibt der Timer korrekt, auch wenn die App im Hintergrund war oder neu gestartet wurde.
 */
data class FocusSession(
    val totalMs: Long,
    val endAtMs: Long? = null,
    val pausedRemainingMs: Long? = null,
) {
    val isRunning: Boolean get() = endAtMs != null
    val isPaused: Boolean get() = endAtMs == null && pausedRemainingMs != null
    val isIdle: Boolean get() = endAtMs == null && pausedRemainingMs == null

    fun remainingMs(now: Long): Long = when {
        endAtMs != null -> (endAtMs - now).coerceAtLeast(0L)
        pausedRemainingMs != null -> pausedRemainingMs
        else -> totalMs
    }

    fun isFinished(now: Long): Boolean = endAtMs != null && now >= endAtMs

    /** Verbleibender Anteil 1.0 … 0.0 für den Ring. */
    fun progress(now: Long): Float =
        if (totalMs <= 0L) 0f else (remainingMs(now).toFloat() / totalMs).coerceIn(0f, 1f)

    fun pause(now: Long): FocusSession =
        if (isRunning) copy(endAtMs = null, pausedRemainingMs = remainingMs(now)) else this

    fun resume(now: Long): FocusSession =
        if (isPaused) copy(endAtMs = now + (pausedRemainingMs ?: 0L), pausedRemainingMs = null) else this

    companion object {
        /** Vorschläge in Minuten. */
        val PRESET_MINUTES = listOf(5, 10, 15, 25, 45)

        fun idle(totalMs: Long) = FocusSession(totalMs)

        fun start(totalMs: Long, now: Long) = FocusSession(totalMs, endAtMs = now + totalMs)

        fun minutesToMs(minutes: Int): Long = minutes * TimeUtils.MINUTE_MS

        /** „24:59“ bzw. „1:05:00“; angefangene Sekunden werden aufgerundet. */
        fun formatClock(ms: Long): String {
            val totalSeconds = ((ms.coerceAtLeast(0L) + 999) / 1000)
            val h = totalSeconds / 3600
            val m = (totalSeconds % 3600) / 60
            val s = totalSeconds % 60
            val mm = m.toString().padStart(2, '0')
            val ss = s.toString().padStart(2, '0')
            return if (h > 0) "$h:$mm:$ss" else "$mm:$ss"
        }
    }
}
