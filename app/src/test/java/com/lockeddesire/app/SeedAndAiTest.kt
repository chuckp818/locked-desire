package com.lockeddesire.app

import com.lockeddesire.app.ai.Intent
import com.lockeddesire.app.ai.IntentDetector
import com.lockeddesire.app.ai.RemoteEngine
import com.lockeddesire.app.ai.Safety
import com.lockeddesire.app.data.Seed
import com.lockeddesire.app.data.toProposal
import com.lockeddesire.app.engine.ChallengeEngine
import com.lockeddesire.app.engine.EngineContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SeedAndAiTest {
    private fun seedText(): String {
        val candidates = listOf(File("src/main/assets/tasks.txt"), File("app/src/main/assets/tasks.txt"))
        return candidates.first { it.exists() }.readText()
    }

    @Test fun has200ValidStarterTasks() {
        val tasks = Seed.parseTasks(seedText())
        assertTrue("Mindestens 200 Aufgaben, gefunden ${tasks.size}", tasks.size >= 200)
        assertEquals(tasks.size, tasks.map { it.title }.toSet().size)
        tasks.forEach { assertTrue(it.categoryKey in Seed.CATEGORY_KEYS) }
    }

    @Test fun everyStarterTaskPassesSafetyRules() {
        val ctx = EngineContext(allowedCategories = Seed.CATEGORY_KEYS, partnerAvailable = true, fitnessMaxReps = 100, fitnessMaxMinutes = 60)
        Seed.parseTasks(seedText()).forEach { assertTrue(it.title, ChallengeEngine.approved(it.toProposal(), ctx)) }
    }

    @Test fun riskyFetishesNeverHaveTasks() {
        val risky = Seed.FETISHES.filter { it.risky }.map { it.key }.toSet()
        Seed.parseTasks(seedText()).forEach { assertFalse(it.title, it.fetishKey in risky) }
    }

    @Test fun safetyDetection() {
        assertTrue(Safety.hasDiscomfort("Mir tut das Knie weh"))
        assertTrue(Safety.hasDiscomfort("Mir ist schwindelig"))
        assertFalse(Safety.hasDiscomfort("Ich bin bereit"))
        assertTrue(Safety.hasSafeword("ROT!", "Rot"))
        assertTrue(Safety.hasSafeword("ich sage rot jetzt", "Rot"))
        assertFalse(Safety.hasSafeword("Rotkäppchen", "Rot"))
    }

    @Test fun intents() {
        assertEquals(Intent.TASK, IntentDetector.detect("Gib mir eine Aufgabe"))
        assertEquals(Intent.PAUSE, IntentDetector.detect("Ich brauche eine Pause"))
        assertEquals(Intent.DECLINE, IntentDetector.detect("Nein, das möchte ich nicht"))
        assertEquals(Intent.PROGRESS, IntentDetector.detect("Wie ist mein Fortschritt?"))
    }

    @Test fun parsesStructuredAiProposal() {
        val r = RemoteEngine.parse("Auf die Knie, Kleines.\nTASK_JSON: {\"title\":\"Haltung\",\"description\":\"Fünf Minuten gerade stehen\",\"category\":\"selfcontrol\",\"difficulty\":2,\"minutes\":5,\"xp\":15}", null)
        assertEquals("Auf die Knie, Kleines.", r.text)
        assertNotNull(r.proposal)
        assertEquals("selfcontrol", r.proposal!!.categoryKey)
        assertTrue(r.proposal!!.fromAi)
        assertNull(RemoteEngine.parse("Nur Text", null).proposal)
    }
}
