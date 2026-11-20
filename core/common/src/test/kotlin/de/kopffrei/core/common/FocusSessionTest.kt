package de.kopffrei.core.common

import de.kopffrei.core.common.focus.FocusSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusSessionTest {
    private val minute = 60_000L

    @Test
    fun `gestartete Sitzung zaehlt ab Startzeit herunter`() {
        val s = FocusSession.start(25 * minute, now = 1_000_000)
        assertTrue(s.isRunning)
        assertEquals(25 * minute, s.remainingMs(1_000_000))
        assertEquals(20 * minute, s.remainingMs(1_000_000 + 5 * minute))
        assertFalse(s.isFinished(1_000_000 + 24 * minute))
        assertTrue(s.isFinished(1_000_000 + 25 * minute))
    }

    @Test
    fun `Pause friert die Restzeit ein und Fortsetzen verschiebt das Ende`() {
        val s = FocusSession.start(10 * minute, now = 0)
        val paused = s.pause(now = 4 * minute)
        assertTrue(paused.isPaused)
        assertEquals(6 * minute, paused.remainingMs(now = 100 * minute))
        val resumed = paused.resume(now = 100 * minute)
        assertTrue(resumed.isRunning)
        assertEquals(106 * minute, resumed.endAtMs)
        assertEquals(6 * minute, resumed.remainingMs(100 * minute))
    }

    @Test
    fun `Pause im Leerlauf und Fortsetzen ohne Pause aendern nichts`() {
        val idle = FocusSession.idle(5 * minute)
        assertTrue(idle.isIdle)
        assertEquals(idle, idle.pause(0))
        assertEquals(idle, idle.resume(0))
        assertEquals(5 * minute, idle.remainingMs(12345))
    }

    @Test
    fun `Fortschritt liegt zwischen null und eins`() {
        val s = FocusSession.start(10 * minute, now = 0)
        assertEquals(1f, s.progress(0))
        assertEquals(0.5f, s.progress(5 * minute))
        assertEquals(0f, s.progress(50 * minute))
        assertEquals(0f, FocusSession.idle(0).progress(0))
    }

    @Test
    fun `Uhrzeit-Format rundet angefangene Sekunden auf`() {
        assertEquals("25:00", FocusSession.formatClock(25 * minute))
        assertEquals("00:01", FocusSession.formatClock(1))
        assertEquals("00:00", FocusSession.formatClock(0))
        assertEquals("1:05:00", FocusSession.formatClock(65 * minute))
        assertEquals("00:00", FocusSession.formatClock(-5))
    }
}
