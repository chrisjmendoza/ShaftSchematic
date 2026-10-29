package com.android.shaftschematic.ui.screen

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
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
 * The ONE Undo / Redo pair every sheet tab's top bar carries (Runout, Wear and its detail page,
 * Undercut and its detail page, Consolidated Output): enabled from the history, tagged per tab.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UndoRedoButtonsTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `the pair follows the history and calls back`() {
        var undos = 0
        var redos = 0
        rule.setContent {
            MaterialTheme {
                Row {
                    UndoRedoButtons(
                        canUndo = true, canRedo = false,
                        onUndo = { undos++ }, onRedo = { redos++ },
                        tagPrefix = "runout",
                    )
                }
            }
        }
        rule.onNodeWithTag("runout_undo").assertIsEnabled().performClick()
        rule.onNodeWithTag("runout_redo").assertIsNotEnabled()
        assertEquals(1, undos)
        assertEquals(0, redos)
    }
}
