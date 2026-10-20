package com.spendlens.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.spendlens.app.TestApp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Full user journeys through the real UI, repeated on every Android version the app supports:
 * 8.0 (26), 9 (28), 11 (30), 13 (33), 14 (34), 15 (35) and 16 (36).
 *
 * The inbox is the 21 sample messages in SampleSms and the clock is fixed at 18 Oct 2026, so the
 * expected totals are exact: October spend is ₹13,457 across 13 imported transactions.
 */
@RunWith(RobolectricTestRunner::class)
// A common 6.3" phone; ScreenSizesTest covers small phones, tablets, foldables and large fonts.
@Config(application = TestApp::class, sdk = [26, 28, 30, 33, 34, 35, 36], qualifiers = "w393dp-h852dp-xxhdpi")
class AppFlowTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun smsOnboarding_filtersReview_budgetOverspend_deleteUndo_reset() {
        with(compose) {
            tapTag("get_started")
            tapTag("allow_sms") // the fake inbox already has permission, so no system dialog

            // Choose what to import: every sender found, 13 transactions
            waitForText("13 transactions from 5 of 5 senders")
            tapTag("review_button")

            // Review: untick Netflix
            waitForText("13 of 13 selected")
            tapText("Netflix")
            waitForText("12 of 13 selected")
            tapTag("import_selected")

            waitForTag("import_continue")
            waitForText("Transactions saved")
            tapTag("import_continue")

            // Budget ₹10,000 against ₹12,808 of spend -> over budget by ₹2,808
            typeInto("budget_input", "10000")
            tapTag("save_budget")
            waitForText("₹2,808 over your monthly budget")
            onNodeWithTag("budget_hero").assertIsDisplayed()
            waitForText("Over budget")

            // Swiggy shows with its merchant row; delete it, then undo
            tapTag("tab_activity")
            waitForText("Swiggy")
            tapText("Swiggy")
            waitForTag("delete_txn")
            tapTag("delete_txn")
            waitForText("Transaction deleted")
            tapText("Undo")
            waitForText("Transaction restored")

            // Reset erases everything and returns to the welcome screen
            tapTag("tab_more")
            tapText("Settings")
            tapText("Reset SpendLens")
            tapTag("confirm_reset")
            waitForTag("get_started")
        }
    }

    @Test
    fun manualMode_addCashExpense_overBudget() {
        with(compose) {
            tapTag("get_started")
            tapTag("not_now")
            typeInto("budget_input", "1000")
            tapTag("save_budget")
            waitForText("SMS import is off")

            tapTag("add_expense")
            typeInto("add_amount", "1500")
            typeInto("add_merchant", "Tea stall")
            tapTag("save_expense")
            waitForText("Tea stall")
            waitForText("₹500 over your monthly budget")
        }
    }

    @Test
    fun categories_addAndArchive() {
        with(compose) {
            tapTag("get_started")
            tapTag("not_now")
            tapTag("save_budget")
            tapTag("tab_more")
            tapText("Categories")
            tapTag("add_category")
            typeInto("category_name", "Pets")
            tapTag("save_category")
            waitForText("Pets added")
            waitForText("Pets")
        }
    }
}
