package com.lockeddesire.app

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import java.io.File
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lockeddesire.app.ui.ROUTES
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun has(text: String) = rule.onAllNodesWithText(text, substring = true, ignoreCase = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(text: String, ms: Long = 20_000) = rule.waitUntil(ms) { has(text) }

    private fun shot(name: String) {
        try {
            val dir = File(rule.activity.filesDir, "shots").apply { mkdirs() }
            val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
            File(dir, name.replace('/', '_') + ".png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
        } catch (_: Throwable) { }
    }

    private fun passAgeGate() {
        rule.waitUntil(20_000) { has("mindestens 18") || has("Alle Bereiche") }
        if (has("mindestens 18")) shot("00_altersabfrage")
        if (has("mindestens 18")) rule.onNodeWithText("Ich bin mindestens 18 Jahre alt").performClick()
        waitFor("Alle Bereiche")
    }

    @Test fun ageGateThenDashboardThenRoulette() {
        passAgeGate()
        rule.onNodeWithText("Roulette drehen", substring = true).performClick()
        waitFor("Kategorien")
        rule.onAllNodesWithText("Roulette drehen")[0].performScrollTo().performClick()
        rule.waitUntil(30_000) { has("Annehmen") || has("Keine Aufgabe passt") }
        Thread.sleep(500)
        shot("90_roulette_ergebnis")
    }

    /** Öffnet jeden Screen einmal und prüft, dass nichts abstürzt. */
    @Test fun everyScreenOpens() {
        passAgeGate()
        val routes = ROUTES.toList() + listOf("personaEdit/0", "toyDetail/0")
        routes.forEach { r ->
            rule.runOnUiThread { rule.activity.pendingRoute.value = r }
            rule.waitForIdle()
            Thread.sleep(900)
            shot("%02d_%s".format(routes.indexOf(r) + 1, r))
            rule.runOnUiThread { rule.activity.pendingRoute.value = "back" }
            rule.waitForIdle()
        }
    }

    @Test fun chatAnswersAndOffersTask() {
        passAgeGate()
        rule.runOnUiThread { rule.activity.pendingRoute.value = "chat" }
        waitFor("Neue Challenge")
        rule.onNodeWithText("Neue Challenge").performClick()
        rule.waitUntil(30_000) { has("Annehmen") || has("passt keine Aufgabe") }
        Thread.sleep(500)
        shot("91_chat_aufgabe")
    }

    @Test fun hardcoreTrialPassFlow() {
        passAgeGate()
        rule.runOnUiThread { rule.activity.pendingRoute.value = "hardcore" }
        waitFor("DUNGEON BETRETEN")
        shot("92_hardcore_tor")
        rule.onNodeWithText("Ich habe die Regeln gelesen", substring = true).performScrollTo().performClick()
        rule.onNodeWithText("DUNGEON BETRETEN", substring = true).performScrollTo().performClick()
        waitFor("NEUE PRÜFUNG")
        Thread.sleep(800)
        shot("93_hardcore_start")
        rule.onAllNodesWithText("NEUE PRÜFUNG", substring = true)[0].performScrollTo().performClick()
        rule.waitUntil(40_000) { has("PRÜFUNG ANNEHMEN") || has("Keine Prüfung passt") }
        Thread.sleep(500)
        shot("94_hardcore_pruefung")
        if (has("PRÜFUNG ANNEHMEN")) {
            rule.onNodeWithText("PRÜFUNG ANNEHMEN").performScrollTo().performClick()
            waitFor("PRÜFUNG STARTEN")
            rule.onNodeWithText("PRÜFUNG STARTEN", substring = true).performScrollTo().performClick()
            waitFor("NICHT BESTANDEN")
            shot("95_hardcore_aktiv")
            rule.onNodeWithText("BESTANDEN").performScrollTo().performClick()
            waitFor("Erfolg dokumentiert")
            Thread.sleep(500)
            shot("96_hardcore_ergebnis")
        }
    }
}
