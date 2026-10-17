package com.android.shaftschematic.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The "Show name on drawing" row names where its default lives while the card follows it
 * (on-device report: the app-wide default could not be found), and drops the line once the card
 * holds its own choice. Rendered the way every card renders the row: `checked` is the stored
 * tri-state flag or the default, and the caption is shown only while the flag is unset.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class ComponentNamesCaptionTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `the caption shows while the card follows the default and goes once it is set`() {
        rule.setContent {
            MaterialTheme {
                var flag by remember { mutableStateOf<Boolean?>(null) }
                Column {
                    ShowDiaToggleRow(
                        label = "Show name on drawing",
                        checked = flag ?: true,
                        testTag = "name_toggle",
                        caption = if (flag == null) COMPONENT_NAMES_DEFAULT_CAPTION else null,
                        onCheckedChange = { flag = it },
                    )
                }
            }
        }
        rule.onNodeWithText(COMPONENT_NAMES_DEFAULT_CAPTION, useUnmergedTree = true).assertExists()

        rule.onNodeWithTag("name_toggle").performClick()
        rule.waitForIdle()

        rule.onNodeWithText(COMPONENT_NAMES_DEFAULT_CAPTION, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `the caption names the Settings row by its own label`() {
        // The card points at "Settings → Drawing → Component names"; the row it names must carry
        // that name, or the pointer leads nowhere.
        assertTrue(COMPONENT_NAMES_SETTING_LABEL.startsWith("Component names"))
        assertTrue(COMPONENT_NAMES_DEFAULT_CAPTION.endsWith("Component names"))
    }
}
