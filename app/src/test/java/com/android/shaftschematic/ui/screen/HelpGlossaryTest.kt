package com.android.shaftschematic.ui.screen

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Help's Glossary section. It sits after the How-To Guides — the guides are what a reader opens
 * Help for, the glossary the reference behind them — and its topics expand like any other.
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
    fun `Glossary is the section after the How-To Guides`() {
        assertEquals(
            listOf("Getting Started", "How-To Guides", "Glossary", "Settings Reference", "FAQ"),
            helpSections.map { it.title },
        )
    }

    @Test
    fun `Glossary defines a shop term`() {
        hostHelp()

        rule.onNode(hasScrollAction())
            .performScrollToNode(hasText("TIR (total indicated runout)"))
        rule.onNodeWithText("TIR (total indicated runout)").performClick()

        rule.onNodeWithText("high and low readings", substring = true).assertIsDisplayed()
    }
}
