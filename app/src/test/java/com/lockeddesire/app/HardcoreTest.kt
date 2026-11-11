package com.lockeddesire.app

import com.lockeddesire.app.data.HardcoreRun
import com.lockeddesire.app.data.HardcoreTask
import com.lockeddesire.app.engine.Hardcore
import com.lockeddesire.app.engine.HardcoreEngine
import com.lockeddesire.app.engine.HcContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

class HardcoreTest {
    private val tasks: List<HardcoreTask> by lazy {
        val f = listOf(File("src/main/assets/hardcore_tasks.txt"), File("app/src/main/assets/hardcore_tasks.txt")).first { it.exists() }
        HardcoreEngine.parse(f.readText()).mapIndexed { i, t -> t.copy(id = i + 1L) }
    }
    private val everything = HcContext(
        enabled = Hardcore.KEYS, maxDifficulty = 6, anatomy = setOf("PENIS", "VULVA"), role = "",
        partnerAvailable = true, toyCategories = setOf("Dildos", "Analplugs", "Keuschheitszubehör", "BDSM-Zubehör")
    )

    @Test fun libraryHasAtLeast300UniqueTasks() {
        assertTrue("Gefunden: ${tasks.size}", tasks.size >= 300)
        assertEquals(tasks.size, tasks.map { it.code }.toSet().size)
        assertEquals(tasks.size, tasks.map { it.categoryKey + it.title }.toSet().size)
        tasks.forEach { assertTrue(it.code, it.categoryKey in Hardcore.KEYS) }
        Hardcore.KEYS.forEach { k -> assertTrue(k, tasks.count { it.categoryKey == k } >= 15) }
        (1..6).forEach { d -> assertTrue("Stufe $d", tasks.count { it.difficulty == d } >= 20) }
    }

    @Test fun everyTaskPassesAllSafetyRulesWithFullPermissions() {
        tasks.forEach { t -> assertNull("${t.code} ${t.title}: ${HardcoreEngine.reject(t, everything)}", HardcoreEngine.reject(t, everything)) }
    }

    @Test fun noGenitalStrikesCompressionOrLongWear() {
        tasks.forEach { t ->
            val text = (t.title + " " + t.description).lowercase()
            listOf("hodenschlag", "schlag auf die hoden", "quetsch", "abbinden", "atemkontrolle", "über nacht").forEach { assertFalse("${t.code}: $it", text.contains(it)) }
            if (t.physical) assertTrue("${t.code} zu lang", t.durationMin <= Hardcore.maxPhysicalMinutes(t.categoryKey))
            if (t.categoryKey == "hc_cbt" || t.categoryKey == "hc_pussy") assertTrue(t.code, t.anatomy.isNotBlank())
        }
    }

    @Test fun anatomyNeverDerivedAndRequired() {
        val cbt = tasks.first { it.categoryKey == "hc_cbt" }
        val ctx = everything.copy(anatomy = emptySet())
        assertEquals("Anatomische Angabe fehlt", HardcoreEngine.reject(cbt, ctx))
        assertNull(HardcoreEngine.reject(cbt, ctx.copy(anatomy = setOf("PENIS"))))
        assertNotNull(HardcoreEngine.reject(tasks.first { it.categoryKey == "hc_pussy" }, ctx.copy(anatomy = setOf("PENIS"))))
    }

    @Test fun categoryToyPartnerRoleAndExclusions() {
        val anal = tasks.first { it.toyCategory == "Analplugs" }
        assertNotNull(HardcoreEngine.reject(anal, everything.copy(toyCategories = emptySet())))
        val partner = tasks.first { it.partnerRequired }
        assertEquals("Partner erforderlich", HardcoreEngine.reject(partner, everything.copy(partnerAvailable = false)))
        val ds = tasks.first { it.categoryKey == "hc_ds" && it.roles == "SUB,SWITCH,SOLO" }
        assertNotNull(HardcoreEngine.reject(ds, everything.copy(role = "Dominant")))
        assertNull(HardcoreEngine.reject(ds, everything.copy(role = "Submissiv")))
        assertNotNull(HardcoreEngine.reject(ds, everything.copy(enabled = Hardcore.KEYS - "hc_ds")))
        val impact = tasks.first { it.categoryKey == "hc_impact" }
        assertNotNull(HardcoreEngine.reject(impact, everything.copy(excludedFetishes = setOf("spanking"))))
        val pet = tasks.first { it.categoryKey == "hc_pet" }
        assertNull(HardcoreEngine.reject(pet, everything.copy(excludedFetishes = setOf("puppyplay"))))
        assertNotNull(HardcoreEngine.reject(pet, everything.copy(excludedFetishes = setOf("puppyplay", "kittenplay", "ponyplay", "bunnyplay", "foxplay"))))
        assertNotNull(HardcoreEngine.reject(ds, everything.copy(base = everything.base.copy(blockedWords = listOf(ds.title.split(" ").first { it.length >= 4 })))))
        val hard = tasks.first { it.difficulty == 6 }
        assertEquals("Zu schwierig", HardcoreEngine.reject(hard, everything.copy(maxDifficulty = 3)))
    }

    @Test fun higherDifficultyMeansMoreStepsAndXpButSamePhysicalCap() {
        val r = Random(7)
        val low = HardcoreEngine.compose(tasks, everything.copy(maxDifficulty = 1), 1, random = r)!!
        assertEquals(1, low.steps.size)
        repeat(30) {
            val high = HardcoreEngine.compose(tasks, everything, 6, random = Random(it))!!
            assertTrue(high.steps.size in 2..3)
            assertTrue(high.steps.filter { s -> s.physical }.sumOf { s -> s.durationMin } <= 60)
            assertTrue(high.steps.all { s -> HardcoreEngine.allowed(s, everything) })
            assertTrue(high.durationMin <= 180)
        }
        assertTrue(Hardcore.xpFactor(6) > Hardcore.xpFactor(1))
        assertTrue(Hardcore.scaledMinutes(10, false, 6) > Hardcore.scaledMinutes(10, false, 1))
        assertEquals(10, Hardcore.scaledMinutes(10, true, 6))
    }

    @Test fun composeReturnsNullWhenNothingAllowed() {
        assertNull(HardcoreEngine.compose(tasks, everything.copy(enabled = emptySet())))
    }

    private fun run(status: String, d: Int = 2, xp: Int = 40, cats: String = "hc_ds", steps: Int = 1, end: Long = 1000) =
        HardcoreRun(taskIds = "1", code = "HC-001", title = "T", description = "D", categoryKey = cats.split(",").first(), categories = cats,
            difficulty = d, xp = xp, stepsTotal = steps, status = status, endedAt = end)

    @Test fun onlyPassedGivesXpAndNothingIsDeducted() {
        assertEquals(40, HardcoreEngine.xpFor(Hardcore.PASSED, run(Hardcore.PASSED)))
        assertEquals(0, HardcoreEngine.xpFor(Hardcore.FAILED, run(Hardcore.FAILED)))
        assertEquals(0, HardcoreEngine.xpFor(Hardcore.ABORTED, run(Hardcore.ABORTED)))
    }

    @Test fun statsSeparateAbortFromFail() {
        val runs = listOf(
            run(Hardcore.PASSED, 3, end = 1), run(Hardcore.PASSED, 5, cats = "hc_impact,hc_ds", steps = 3, end = 2),
            run(Hardcore.FAILED, 6, end = 3), run(Hardcore.ABORTED, 6, end = 4), run(Hardcore.AVAILABLE)
        )
        val s = HardcoreEngine.stats(runs) { it / 100 }
        assertEquals(4, s.started)
        assertEquals(2, s.passed)
        assertEquals(1, s.failed)
        assertEquals(1, s.aborted)
        assertEquals(66, s.successRate)
        assertEquals(5, s.highestPassed)
        assertEquals(80, s.xp)
        assertEquals(2, s.bestStreak)
        assertEquals(3, s.longestSeries)
        assertEquals("hc_ds", s.favorites.first().first)
    }

    @Test fun personasHaveHardcoreLinesForEveryOutcome() {
        com.lockeddesire.app.ai.HardcoreVoice.PERSONALITIES.forEach { p ->
            listOf("hc_enter", "hc_offer", "hc_accept", "hc_pass", "hc_fail", "hc_abort", "hc_none", "greet", "small").forEach { k ->
                assertTrue("$p/$k", com.lockeddesire.app.ai.HardcoreVoice.lines(p, k).isNotEmpty())
            }
            // Ein Abbruch wird nie verspottet
            com.lockeddesire.app.ai.HardcoreVoice.lines(p, "hc_abort").forEach { assertTrue(it, it.contains("Strafe") || it.contains("Recht") || it.contains("richtig") || it.contains("stoppe")) }
        }
    }
}
