package com.spendlens.app.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement

/*
 * Helpers for the Robolectric journeys.
 *
 * Lists are lazy, so a row below the fold doesn't exist until it is scrolled into view: every wait
 * also scrolls any list on screen towards what it is looking for, the way a person would.
 * Taps check the target is really on screen, then trigger its click action directly, so a snackbar
 * that happens to be passing over a button doesn't swallow the tap.
 */

private fun ComposeTestRule.exists(m: SemanticsMatcher): Boolean =
    onAllNodes(m, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

/** Scrolls each scrollable list until one shows a node matching [m]. */
private fun ComposeTestRule.scrollListsTo(m: SemanticsMatcher): Boolean {
    val lists = onAllNodes(hasScrollToNodeAction())
    val count = runCatching { lists.fetchSemanticsNodes().size }.getOrDefault(0)
    for (i in 0 until count) {
        try {
            lists[i].performScrollToNode(m)
            waitForIdle()
            if (exists(m)) return true
        } catch (e: Throwable) {
            // Not in this list
        }
    }
    return false
}

/** Waits for background database work, scrolling lists as needed, until a node matching [m] exists. */
fun ComposeTestRule.waitForNode(m: SemanticsMatcher, timeoutMs: Long = 10_000) {
    val end = System.currentTimeMillis() + timeoutMs
    while (true) {
        try {
            waitUntil(250) { exists(m) }
            return
        } catch (e: Throwable) {
            // keep looking
        }
        if (scrollListsTo(m)) return
        if (System.currentTimeMillis() > end) throw AssertionError("Nothing on screen matches ${m.description} after ${timeoutMs} ms")
    }
}

fun ComposeTestRule.waitForText(text: String, substring: Boolean = true, timeoutMs: Long = 10_000) =
    waitForNode(hasText(text, substring = substring), timeoutMs)

fun ComposeTestRule.waitForTag(tag: String, timeoutMs: Long = 10_000) =
    waitForNode(hasTestTag(tag), timeoutMs)

fun ComposeTestRule.tapText(text: String, substring: Boolean = false) {
    waitForText(text, substring)
    tap(onAllNodes(hasText(text, substring = substring))[0])
}

fun ComposeTestRule.tapTag(tag: String) {
    waitForTag(tag)
    tap(onNode(hasTestTag(tag)))
}

fun ComposeTestRule.typeInto(tag: String, value: String) {
    waitForTag(tag)
    onNode(hasTestTag(tag)).performScrollToSafe().performTextReplacement(value)
    waitForIdle()
}

private fun ComposeTestRule.tap(node: SemanticsNodeInteraction) {
    node.performScrollToSafe()
    waitForIdle()
    node.assertIsDisplayed()
    node.performSemanticsAction(SemanticsActions.OnClick)
    waitForIdle()
}

private fun SemanticsNodeInteraction.performScrollToSafe(): SemanticsNodeInteraction = try {
    performScrollTo()
} catch (e: Throwable) {
    this
}
