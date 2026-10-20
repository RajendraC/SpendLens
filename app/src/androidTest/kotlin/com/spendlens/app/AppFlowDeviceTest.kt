package com.spendlens.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.spendlens.app.ui.MainActivity
import com.spendlens.app.ui.tapTag
import com.spendlens.app.ui.tapText
import com.spendlens.app.ui.typeInto
import com.spendlens.app.ui.waitForTag
import com.spendlens.app.ui.waitForText
import kotlinx.coroutines.runBlocking
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
                runBlocking { ApplicationProvider.getApplicationContext<SpendLensApp>().container.eraseEverything() }
            }
        })
        .around(compose)

    @Test
    fun manualExpenseOverBudgetThenReset() {
        with(compose) {
            tapTag("get_started")
            tapTag("not_now")
            typeInto("budget_input", "1000")
            tapTag("save_budget")
            waitForText("SMS import is off", timeoutMs = 20_000)

            tapTag("add_expense")
            typeInto("add_amount", "1500")
            typeInto("add_merchant", "Tea stall")
            tapTag("save_expense")
            waitForText("over your monthly budget", timeoutMs = 20_000)
            waitForText("Tea stall", timeoutMs = 20_000)

            tapTag("tab_more")
            tapText("Settings")
            waitForText("Encryption key", timeoutMs = 20_000)
            tapText("Reset SpendLens")
            tapTag("confirm_reset")
            waitForTag("get_started", timeoutMs = 20_000)
        }
    }
}
