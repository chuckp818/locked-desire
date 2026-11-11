package com.lockeddesire.app

import com.lockeddesire.app.engine.ChallengeEngine
import com.lockeddesire.app.engine.DungeonPlanner
import com.lockeddesire.app.engine.EngineContext
import com.lockeddesire.app.engine.Leveling
import com.lockeddesire.app.engine.Proposal
import com.lockeddesire.app.engine.Roulette
import com.lockeddesire.app.engine.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class EngineTest {
    private val cats = setOf("fitness", "selfcontrol", "roleplay", "partner")
    private val ctx = EngineContext(allowedCategories = cats)
    private fun p(title: String = "Test", cat: String = "fitness", desc: String = "", d: Int = 1, min: Int = 5) =
        Proposal(title, desc, cat, d, min)

    @Test fun approvesNormalTask() = assertTrue(ChallengeEngine.approved(p(), ctx))

    @Test fun rejectsExcludedCategory() =
        assertFalse(ChallengeEngine.approved(p(), ctx.copy(excludedCategories = setOf("fitness"))))

    @Test fun rejectsUnknownCategory() = assertFalse(ChallengeEngine.approved(p(cat = "irgendwas"), ctx))

    @Test fun rejectsDangerousContent() {
        assertFalse(ChallengeEngine.approved(p(desc = "Übe Atemkontrolle für zwei Minuten"), ctx))
        assertFalse(ChallengeEngine.approved(p(title = "Würgen"), ctx))
    }

    @Test fun rejectsPersonalLimitWord() =
        assertFalse(ChallengeEngine.approved(p(desc = "Trage ein Halsband"), ctx.copy(blockedWords = listOf("Halsband"))))

    @Test fun rejectsExcludedTag() =
        assertFalse(ChallengeEngine.approved(p().copy(tag = "spanking"), ctx.copy(excludedTags = setOf("spanking"))))

    @Test fun rejectsPartnerTaskWithoutPartner() {
        val t = p(cat = "partner").copy(partnerRequired = true)
        assertFalse(ChallengeEngine.approved(t, ctx))
        assertTrue(ChallengeEngine.approved(t, ctx.copy(partnerAvailable = true)))
    }

    @Test fun respectsFitnessLimits() {
        assertFalse(ChallengeEngine.approved(p().copy(reps = 60), ctx.copy(fitnessMaxReps = 40)))
        assertFalse(ChallengeEngine.approved(p().copy(jointLoad = true), ctx.copy(jointFriendly = true)))
    }

    @Test fun rejectsLongPhysicalTask() =
        assertFalse(ChallengeEngine.approved(p(cat = "roleplay", min = 120).copy(tag = "plug"), ctx))

    @Test fun enforcesQuotaAndQuietHours() {
        val quota = ChallengeEngine.check(p(), ctx.copy(enforceQuota = true, offersToday = 8, maxPerDay = 8))
        assertTrue(quota is Verdict.Rejected)
        val quiet = ChallengeEngine.check(p(), ctx.copy(enforceQuiet = true, minuteOfDay = 23 * 60))
        assertTrue(quiet is Verdict.Rejected)
        assertTrue(ChallengeEngine.isQuiet(60, 22 * 60, 8 * 60))
        assertFalse(ChallengeEngine.isQuiet(12 * 60, 22 * 60, 8 * 60))
    }

    @Test fun levelingIsMonotonic() {
        assertEquals(1, Leveling.levelFor(0))
        assertEquals(2, Leveling.levelFor(Leveling.xpForLevel(2)))
        assertEquals(100, Leveling.levelFor(Int.MAX_VALUE / 2))
        assertEquals("Anfänger", Leveling.titleFor(1))
        assertEquals("Mythos", Leveling.titleFor(100))
        assertEquals("Diszipliniert", Leveling.titleFor(12))
        var last = 0
        for (xp in 0..20000 step 37) { val l = Leveling.levelFor(xp); assertTrue(l >= last); last = l }
    }

    @Test fun dungeonPlanStaysInsideLimits() {
        val pool = (1..60).map { p("Aufgabe $it", cat = cats.elementAt(it % 3), d = 1 + it % 6) }
        val start = 0L
        val quietCtx = ctx.copy(quietStart = 22 * 60, quietEnd = 8 * 60)
        val minute: (Long) -> Int = { ((it / 60000) % 1440).toInt() }
        val plan = DungeonPlanner.plan(pool, start, 6, 16, quietCtx, minute, Random(7))
        assertTrue(plan.isNotEmpty())
        assertEquals(plan.size, plan.map { it.proposal.title }.toSet().size)
        plan.forEach {
            assertFalse(ChallengeEngine.isQuiet(minute(it.scheduledAt), 22 * 60, 8 * 60))
            assertTrue(it.scheduledAt in start until start + 24 * DungeonPlanner.HOUR)
            assertTrue(it.proposal.difficulty <= DungeonPlanner.maxDifficulty(6, it.phaseIdx))
        }
        assertTrue(DungeonPlanner.maxDifficulty(6, 0) <= DungeonPlanner.maxDifficulty(6, 3))
    }

    @Test fun roulettePrefersFreshItems() {
        val pick = Roulette.pick(listOf("a", "b"), recent = listOf("a"), random = Random(1))
        assertEquals("b", pick)
        assertNotNull(Roulette.pick(listOf("a"), recent = listOf("a")))
        val m = Roulette.randomMinutes(5 to 10, Random(3))
        assertTrue(m in 5..10)
    }
}
