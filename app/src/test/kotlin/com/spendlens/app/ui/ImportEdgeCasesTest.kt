package com.spendlens.app.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.spendlens.app.TEST_CLOCK
import com.spendlens.app.TestApp
import com.spendlens.core.SmsMessage
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/*
 * First imports from inboxes unlike the sample one: only senders SpendLens doesn't know yet, and no
 * transactions at all. Neither may leave "Choose what to import" as a dead end.
 */

private const val DAY = 24 * 60 * 60_000L
private val NOW = TEST_CLOCK.millis()

/** Starts the app with [inbox] as the phone's SMS inbox. */
abstract class FirstImportTest(inbox: List<SmsMessage>) {
    protected val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: TestRule = RuleChain
        .outerRule(object : ExternalResource() {
            override fun before() { TestApp.inboxOverride = inbox.toMutableList() }
            override fun after() { TestApp.inboxOverride = null }
        })
        .around(compose)
}

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [36], qualifiers = "w393dp-h852dp-xxhdpi")
class OnlyUnrecognisedSendersTest : FirstImportTest(
    listOf(
        SmsMessage(1, "VM-XYZPAY-S", "Rs.120 debited from your wallet for order 5512 on 10-10-26.", NOW - 3 * DAY),
        SmsMessage(2, "AD-NEWBNK-S", "Rs 2,500.00 debited from A/c XX1234 to ZOMATO on 15-10-26. Not you? Call us.", NOW - 2 * DAY),
        SmsMessage(3, "+919876543210", "Rs 500 sent to you for dinner thanks", NOW - DAY),
    ),
) {
    @Test
    fun unrecognisedSendersAreSwitchedOnForTheFirstImport() {
        with(compose) {
            tapTag("get_started")
            tapTag("allow_sms")
            waitForText("2 transactions from 1 of 1 senders")
            tapTag("review_button")
            waitForText("2 of 2 selected")
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [36], qualifiers = "w393dp-h852dp-xxhdpi")
class NoTransactionsTest : FirstImportTest(
    listOf(
        SmsMessage(1, "VM-HDFCBK-S", "123456 is your OTP to log in to NetBanking. Do not share it with anyone.", NOW - DAY),
        SmsMessage(2, "VM-HDFCBK-S", "Your HDFC Bank e-statement for Sept 2026 has been sent to your registered email.", NOW - 2 * DAY),
        SmsMessage(3, "+919876543210", "See you at 7", NOW - 3 * DAY),
    ),
) {
    @Test
    fun explainsWhatWasSkippedAndOffersAWayForward() {
        with(compose) {
            tapTag("get_started")
            tapTag("allow_sms")
            waitForText("No transactions found")
            tapTag("see_skipped")
            waitForText("No amount found")
            waitForText("OTPs and codes")
            tapTag("copy_skipped")
            waitForText("Copied")
            activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            tapTag("continue_manual")
            waitForTag("budget_input")
        }
    }
}
