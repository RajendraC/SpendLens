package com.spendlens.app.ui

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement

/** Waits for background database work, then returns. Fails with a clear message after [timeoutMs]. */
fun ComposeTestRule.waitForText(text: String, substring: Boolean = true, timeoutMs: Long = 10_000) {
    waitUntil(timeoutMs) { onAllNodes(hasText(text, substring = substring), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
}

fun ComposeTestRule.waitForTag(tag: String, timeoutMs: Long = 10_000) {
    waitUntil(timeoutMs) { onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
}

fun ComposeTestRule.tapText(text: String, substring: Boolean = false) {
    waitForText(text, substring)
    onNodeWithText(text, substring = substring, useUnmergedTree = false).performScrollToSafe().performClick()
    waitForIdle()
}

fun ComposeTestRule.tapTag(tag: String) {
    waitForTag(tag)
    onNodeWithTag(tag).performScrollToSafe().performClick()
    waitForIdle()
}

fun ComposeTestRule.typeInto(tag: String, value: String) {
    waitForTag(tag)
    onNodeWithTag(tag).performTextReplacement(value)
    waitForIdle()
}

private fun androidx.compose.ui.test.SemanticsNodeInteraction.performScrollToSafe() = try {
    performScrollTo()
} catch (e: Throwable) {
    this
}
