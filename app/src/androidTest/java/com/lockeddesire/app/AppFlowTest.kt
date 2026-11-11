package com.lockeddesire.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun waitFor(text: String) = rule.waitUntil(15_000) { rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    @Test fun ageGateThenDashboardThenRoulette() {
        waitFor("mindestens 18")
        rule.onNodeWithText("Ich bin mindestens 18 Jahre alt").performClick()
        waitFor("Alle Bereiche")
        rule.onNodeWithText("Roulette drehen", substring = true).performClick()
        waitFor("Kategorien")
        rule.onAllNodesWithText("Roulette drehen")[0].performScrollTo().performClick()
        rule.waitUntil(20_000) {
            rule.onAllNodesWithText("Annehmen").fetchSemanticsNodes().isNotEmpty() ||
                rule.onAllNodesWithText("Keine Aufgabe passt", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
