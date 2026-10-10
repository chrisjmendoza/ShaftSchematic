package com.android.shaftschematic.ui.screen

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Move-mode toolbar under the Runout tab's canvas: −/+ act only on a selection, − follows
 * the station editor's own rule, and the label names what is selected. Hosted standalone —
 * the whole `RunoutRoute` is not composable under Robolectric (its dialogs OOM).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class RunoutMoveToolbarTest {

    @get:Rule
    val rule = createComposeRule()

    private var adds = 0
    private var removes = 0

    private fun host(label: String, hasSelection: Boolean, canRemove: Boolean) {
        rule.setContent {
            MaterialTheme {
                RunoutMoveToolbar(
                    selectionLabel = label,
                    hasSelection = hasSelection,
                    canRemove = canRemove,
                    onRemove = { removes++ },
                    onAdd = { adds++ },
                )
            }
        }
    }

    @Test
    fun `with nothing selected both buttons are disabled`() {
        host("Nothing selected", hasSelection = false, canRemove = true)

        rule.onNodeWithTag("runout_move_selection").assertTextEquals("Nothing selected")
        rule.onNodeWithTag("runout_move_add").assertIsNotEnabled()
        rule.onNodeWithTag("runout_move_remove").assertIsNotEnabled()
    }

    @Test
    fun `plus fires onAdd for a selection`() {
        host("Body 2", hasSelection = true, canRemove = true)

        rule.onNodeWithTag("runout_move_add").assertIsEnabled().performClick()

        assertEquals(1, adds)
        assertEquals(0, removes)
    }

    @Test
    fun `minus is disabled when the editor's rule forbids a removal`() {
        host("Body 2", hasSelection = true, canRemove = false)

        rule.onNodeWithTag("runout_move_remove").assertIsNotEnabled()
        rule.onNodeWithTag("runout_move_add").assertIsEnabled()
    }

    @Test
    fun `minus fires onRemove when a removal is allowed`() {
        host("Body 2 · Station 3", hasSelection = true, canRemove = true)

        rule.onNodeWithTag("runout_move_selection").assertTextEquals("Body 2 · Station 3")
        rule.onNodeWithTag("runout_move_remove").assertIsEnabled().performClick()

        assertEquals(1, removes)
        assertEquals(0, adds)
    }
}
