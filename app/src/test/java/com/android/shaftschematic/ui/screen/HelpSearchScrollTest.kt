package com.android.shaftschematic.ui.screen

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A new Help query opens on its FIRST result. The list anchors its scroll to the key of the
 * item at the top, so without a reset a result that survives the re-rank kept the view and the
 * leading results sat above the fold (on-device report).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class HelpSearchScrollTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `a query typed from deep in the list opens on its first result`() {
        val query = "shaft"
        val results = filterHelpSections(helpSections, query)
        assertTrue("query must match many topics", results.sumOf { it.topics.size } > 8)
        // Deep link to the LAST result: the list opens scrolled to it, and it survives the query.
        val anchor = results.last().topics.last()

        rule.setContent {
            MaterialTheme {
                HelpRoute(onBack = {}, initialTopicKey = anchor.key, onOpenManual = { _, _ -> })
            }
        }
        rule.onNodeWithText(anchor.title).assertIsDisplayed()

        rule.onNodeWithTag(HELP_SEARCH_TAG).performTextInput(query)
        rule.waitForIdle()

        rule.onNodeWithText(results.first().title).assertIsDisplayed()
        rule.onNodeWithText(results.first().topics.first().title).assertIsDisplayed()
    }
}
