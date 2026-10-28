package com.android.shaftschematic.ui.screen

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The card title's in-place rename can be opened again after a rename. The editor's focus
 * baseline was remembered per COMPONENT, so after the first commit it stayed true and the second
 * open's initial unfocused callback committed and closed the field at once (on-device report:
 * "the edit input opened and closed instantly").
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class EditableCardTitleTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `the rename editor opens again after a rename`() {
        var label: String? by mutableStateOf(null)
        rule.setContent {
            MaterialTheme {
                EditableCardTitle(
                    componentId = "taper-1",
                    title = label ?: "FWD Taper",
                    label = label,
                    onCommitLabel = { label = it },
                )
            }
        }

        // First rename: open, type, Done.
        rule.onNodeWithTag("card_title_edit", useUnmergedTree = true).performClick()
        rule.onAllNodes(hasSetTextAction()).assertCountEquals(1)
        rule.onAllNodes(hasSetTextAction())[0].performTextReplacement("Mid Taper")
        rule.onAllNodes(hasSetTextAction())[0].performImeAction()
        rule.waitForIdle()
        assertEquals("Mid Taper", label)

        // Second rename: the editor must stay open until the user commits.
        rule.onNodeWithTag("card_title_edit", useUnmergedTree = true).performClick()
        rule.waitForIdle()
        rule.onAllNodes(hasSetTextAction()).assertCountEquals(1)
        rule.onAllNodes(hasSetTextAction())[0].performTextReplacement("Step taper")
        rule.onAllNodes(hasSetTextAction())[0].performImeAction()
        rule.waitForIdle()
        assertEquals("Step taper", label)
    }
}
