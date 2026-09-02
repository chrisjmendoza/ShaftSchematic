package com.android.shaftschematic.ui.screen

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Help's Glossary section. It sits second, right after Getting Started, so a shop term can
 * be looked up without reading past the how-to guides, and its topics expand like any other.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class HelpGlossaryTest {

    @get:Rule
    val rule = createComposeRule()

    private fun hostHelp() {
        rule.setContent {
            MaterialTheme {
                HelpRoute(onBack = {})
            }
        }
    }

    @Test
    fun `Glossary is the section after Getting Started`() {
        hostHelp()

        rule.onNodeWithText("Getting Started").assertIsDisplayed()
        rule.onNodeWithText("Glossary").assertIsDisplayed()
    }

    @Test
    fun `Glossary defines a shop term`() {
        hostHelp()

        rule.onNode(hasScrollAction())
            .performScrollToNode(hasText("TIR (total indicator reading)"))
        rule.onNodeWithText("TIR (total indicator reading)").performClick()

        rule.onNodeWithText("high and low readings", substring = true).assertIsDisplayed()
    }
}
