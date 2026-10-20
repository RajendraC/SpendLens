package com.spendlens.app

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.spendlens.app.ui.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith

/**
 * The production app on a real Android build: SQLCipher database, Keystore key, real activity.
 * Starts from a clean slate, uses manual mode (no SMS dialog), and ends with a full reset.
 */
@RunWith(AndroidJUnit4::class)
class AppFlowDeviceTest {

    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: TestRule = RuleChain
        .outerRule(object : ExternalResource() {
            override fun before() {
                ApplicationProvider.getApplicationContext<SpendLensApp>().container.eraseEverything()
            }
        })
        .around(compose)

    private fun AndroidComposeTestRule<*, *>.waitText(t: String) =
        waitUntil(15_000) { onAllNodes(hasText(t, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

    private fun AndroidComposeTestRule<*, *>.tap(tag: String) {
        waitUntil(15_000) { onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        runCatching { onNodeWithTag(tag).performScrollTo() }
        onNodeWithTag(tag).performClick()
        waitForIdle()
    }

    private fun AndroidComposeTestRule<*, *>.tapText(t: String) {
        waitText(t)
        runCatching { onNodeWithText(t).performScrollTo() }
        onNodeWithText(t).performClick()
        waitForIdle()
    }

    @Test
    fun manualExpenseOverBudgetThenReset() {
        with(compose) {
            tap("get_started")
            tap("not_now")
            tap("budget_input")
            onNodeWithTag("budget_input").performTextReplacement("1000")
            tap("save_budget")
            waitText("SMS import is off")

            tap("add_expense")
            onNodeWithTag("add_amount").performTextReplacement("1500")
            onNodeWithTag("add_merchant").performTextReplacement("Tea stall")
            tap("save_expense")
            waitText("Tea stall")
            waitText("over your monthly budget")

            tap("tab_more")
            tapText("Settings")
            waitText("Encryption key")
            tapText("Reset SpendLens")
            tap("confirm_reset")
            waitUntil(15_000) { onAllNodes(hasTestTag("get_started")).fetchSemanticsNodes().isNotEmpty() }
        }
    }
}
