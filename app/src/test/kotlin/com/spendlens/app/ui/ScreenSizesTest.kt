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
 * The same journey on different devices: a small 4.7" phone, a large phone, a 10" tablet,
 * a foldable in landscape, dark mode and the largest system font.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [35])
class ScreenSizesTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun journey() = with(compose) {
        tapTag("get_started")
        tapTag("allow_sms")
        waitForTag("import_all")
        tapTag("import_all")
        tapTag("import_continue")
        typeInto("budget_input", "20000")
        tapTag("save_budget")
        waitForTag("budget_hero")
        onNodeWithTag("budget_hero").assertIsDisplayed()
        waitForText("Where it went")
        tapTag("tab_budgets")
        waitForText("Monthly budget")
        tapTag("tab_activity")
        waitForText("Swiggy")
    }

    @Test @Config(qualifiers = "w320dp-h568dp-xhdpi")
    fun smallPhone() = journey()

    @Test @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun largePhone() = journey()

    @Test @Config(qualifiers = "w800dp-h1280dp-xhdpi")
    fun tablet() = journey()

    @Test @Config(qualifiers = "w841dp-h673dp-land-xhdpi")
    fun foldableLandscape() = journey()

    @Test @Config(qualifiers = "w411dp-h891dp-night-xxhdpi")
    fun darkMode() = journey()

    @Test @Config(qualifiers = "w360dp-h780dp-xhdpi", fontScale = 1.5f)
    fun largestFont() = journey()
}
