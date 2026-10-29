package com.android.shaftschematic.ui.screen

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.android.shaftschematic.doc.SHAFT_DOT_EXT
import com.android.shaftschematic.io.InternalStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A refused rename says why UNDER THE NAME FIELD (emulator QA: renaming onto an existing name
 * left the dialog open with no message — the refusal went to a snackbar behind the dialog and its
 * keyboard). Nothing on disk changes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RenameShaftDocumentDialogRefusalTest {

    @get:Rule
    val rule = createComposeRule()

    private val ctx = ApplicationProvider.getApplicationContext<Application>()
    private val from = "QA_Polar_copy$SHAFT_DOT_EXT"
    private val taken = "QA_Titan_copy2$SHAFT_DOT_EXT"
    private var renamedTo: String? = null

    private fun show() {
        InternalStorage.save(ctx, from, "{}")
        InternalStorage.save(ctx, taken, "{\"other\":1}")
        rule.setContent {
            MaterialTheme {
                RenameShaftDocumentDialog(
                    fromName = from,
                    onDismiss = {},
                    onRenamed = { renamedTo = it },
                )
            }
        }
    }

    @Test
    fun `renaming onto a saved name is refused under the field and touches nothing`() {
        show()
        rule.onNodeWithTag(RENAME_DOC_FIELD_TAG).performTextReplacement("QA_Titan_copy2")
        rule.onNodeWithTag(RENAME_DOC_CONFIRM_TAG).performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag(RENAME_DOC_REFUSAL_TAG, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

        rule.onNodeWithTag(RENAME_DOC_REFUSAL_TAG, useUnmergedTree = true)
            .assertTextEquals("A saved shaft named ‘QA_Titan_copy2’ already exists.")
        assertNull(renamedTo)
        assertTrue(InternalStorage.exists(ctx, from))
        assertEquals("{\"other\":1}", InternalStorage.load(ctx, taken))
    }

    @Test
    fun `a blank name is refused under the field, and editing clears the message`() {
        show()
        rule.onNodeWithTag(RENAME_DOC_FIELD_TAG).performTextReplacement("")
        rule.onNodeWithTag(RENAME_DOC_CONFIRM_TAG).performClick()
        rule.onNodeWithTag(RENAME_DOC_REFUSAL_TAG, useUnmergedTree = true).assertTextEquals(RENAME_BLANK_NAME_MESSAGE)

        rule.onNodeWithTag(RENAME_DOC_FIELD_TAG).performTextInput("x")
        assertTrue(rule.onAllNodesWithTag(RENAME_DOC_REFUSAL_TAG, useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
    }
}
