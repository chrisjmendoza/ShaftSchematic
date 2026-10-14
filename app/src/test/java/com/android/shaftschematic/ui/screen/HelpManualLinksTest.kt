package com.android.shaftschematic.ui.screen

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Help's three ways into the User Manual: the card at the top, a topic's "Read more in the
 * manual", and "Search the user manual" under a search. Without a manual callback none of
 * them shows.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class HelpManualLinksTest {

    @get:Rule
    val rule = createComposeRule()

    private val opened = mutableListOf<Pair<String?, String?>>()

    private fun hostHelp(withManual: Boolean = true, topic: String? = null) {
        rule.setContent {
            MaterialTheme {
                HelpRoute(
                    onBack = {},
                    initialTopicKey = topic,
                    onOpenManual = if (withManual) { a, f -> opened += a to f } else null,
                )
            }
        }
    }

    @Test
    fun `the manual card opens the manual at its start`() {
        hostHelp()

        rule.onNodeWithTag(HELP_MANUAL_CARD_TAG).assertIsDisplayed().performClick()

        assertEquals(listOf<Pair<String?, String?>>(null to null), opened)
    }

    @Test
    fun `an expanded topic links to its manual section`() {
        hostHelp()

        rule.onNodeWithText("Undo and redo").performClick()
        rule.onNodeWithTag(HELP_READ_MORE_TAG).assertIsDisplayed().performClick()

        assertEquals(listOf<Pair<String?, String?>>("editor-history" to null), opened)
    }

    @Test
    fun `a search offers the manual's find on the same words`() {
        hostHelp()

        rule.onNodeWithTag(HELP_SEARCH_TAG).performTextInput("  pilot runout ")
        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag(HELP_SEARCH_MANUAL_TAG))
        rule.onNodeWithTag(HELP_SEARCH_MANUAL_TAG).performClick()

        assertEquals(listOf<Pair<String?, String?>>(null to "pilot runout"), opened)
    }

    @Test
    fun `a deep link still lands on its topic below the manual card`() {
        hostHelp(topic = HELP_TOPIC_RECORD_RUNOUT)

        rule.onNodeWithText("Record runout").assertIsDisplayed()
        rule.onNodeWithTag(HELP_READ_MORE_TAG).assertIsDisplayed()
    }

    @Test
    fun `no manual callback, no manual links`() {
        hostHelp(withManual = false)

        rule.onNodeWithTag(HELP_MANUAL_CARD_TAG).assertDoesNotExist()
        rule.onNodeWithText("Undo and redo").performClick()
        rule.onNodeWithTag(HELP_READ_MORE_TAG).assertDoesNotExist()
        rule.onNode(hasText("Undo and redo")).assertIsDisplayed()
    }
}
