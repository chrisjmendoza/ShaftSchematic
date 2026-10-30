package com.android.shaftschematic.ui.screen

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * "Save changes?" — asked when the user changes cards while a card field holds an unsaved value.
 * Each answer fires exactly its own action, and the prompt names the field (without its unit)
 * and the card it sits on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class UnsavedCardEditDialogTest {

    @get:Rule
    val rule = createComposeRule()

    private val calls = mutableListOf<String>()

    private fun show() {
        rule.setContent {
            MaterialTheme {
                UnsavedCardEditDialog(
                    edit = PendingCardEdit("AFT Taper", "Length (in)", { true }, {}),
                    onSave = { calls += "save" },
                    onDiscard = { calls += "discard" },
                    onKeepEditing = { calls += "keep" },
                )
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun `save fires only save`() {
        show()
        rule.onNodeWithTag(UNSAVED_EDIT_SAVE_TAG).performClick()
        rule.waitForIdle()
        assertEquals(listOf("save"), calls)
    }

    @Test
    fun `discard fires only discard`() {
        show()
        rule.onNodeWithTag(UNSAVED_EDIT_DISCARD_TAG).performClick()
        rule.waitForIdle()
        assertEquals(listOf("discard"), calls)
    }

    @Test
    fun `keep editing fires only keep editing`() {
        show()
        rule.onNodeWithTag(UNSAVED_EDIT_KEEP_TAG).performClick()
        rule.waitForIdle()
        assertEquals(listOf("keep"), calls)
    }

    @Test
    fun `the prompt names the field without its unit and the card`() {
        show()
        rule.onAllNodesWithTag(UNSAVED_EDIT_DIALOG_TAG).assertCountEquals(1)
        rule.onAllNodesWithText("“Length” on AFT Taper holds a value you have not saved.")
            .assertCountEquals(1)
        rule.onAllNodesWithText("(in)", substring = true).assertCountEquals(0)
    }
}
