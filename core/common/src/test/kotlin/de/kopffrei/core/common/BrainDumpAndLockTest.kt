package de.kopffrei.core.common

import de.kopffrei.core.common.security.AppLockPolicy
import de.kopffrei.core.common.text.BrainDump
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrainDumpAndLockTest {
    @Test
    fun splitsLinesAndDropsBlanks() {
        val result = BrainDump.splitLines("Milch kaufen\n\n  Zahnarzt anrufen  \n\n")
        assertEquals(listOf("Milch kaufen", "Zahnarzt anrufen"), result)
    }

    @Test
    fun removesBulletsAndNumbering() {
        val text = "- Wäsche\n• Steuer\n* Mama anrufen\n1. Auto\n2) Bank\n3. "
        assertEquals(listOf("Wäsche", "Steuer", "Mama anrufen", "Auto", "Bank"), BrainDump.splitLines(text))
    }

    @Test
    fun keepsNumbersInsideText() {
        assertEquals(listOf("2024 Steuer machen", "-5 Grad Jacke"), BrainDump.splitLines("2024 Steuer machen\n-5 Grad Jacke"))
    }

    @Test
    fun emptyTextGivesNoThoughts() {
        assertTrue(BrainDump.splitLines("").isEmpty())
        assertTrue(BrainDump.splitLines("  \n \n").isEmpty())
    }

    @Test
    fun lockOnlyWhenEnabledAndNotUnlocked() {
        assertTrue(AppLockPolicy.needsUnlock(lockEnabled = true, unlocked = false))
        assertFalse(AppLockPolicy.needsUnlock(lockEnabled = true, unlocked = true))
        assertFalse(AppLockPolicy.needsUnlock(lockEnabled = false, unlocked = false))
    }

    @Test
    fun relockAfterGracePeriod() {
        assertFalse(AppLockPolicy.shouldRelock(null, 1_000_000))
        assertFalse(AppLockPolicy.shouldRelock(1_000, 30_000))
        assertTrue(AppLockPolicy.shouldRelock(1_000, 61_000))
        assertTrue(AppLockPolicy.shouldRelock(1_000, 100_000))
    }

    @Test
    fun relockWhenClockWentBackwards() {
        assertTrue(AppLockPolicy.shouldRelock(50_000, 10_000))
    }
}
