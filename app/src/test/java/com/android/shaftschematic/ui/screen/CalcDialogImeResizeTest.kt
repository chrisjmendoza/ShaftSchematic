package com.android.shaftschematic.ui.screen

import android.view.WindowManager
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/**
 * [DialogImeResize] switches its dialog window to RESIZE above the keyboard rather than pan, so
 * the fields at a dialog's foot and its buttons can be scrolled into view while typing
 * (on-device report: the keyway calculator's cutter fields sat under the keyboard). Pinned on a
 * plain `AlertDialog`: the calculator dialogs themselves never idle under Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class CalcDialogImeResizeTest {

    @get:Rule
    val rule = createComposeRule()

    private fun latestAdjustMode(): Int =
        ShadowDialog.getLatestDialog().window!!.attributes.softInputMode and
            WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST

    private fun showDialog(resize: Boolean) {
        rule.setContent {
            MaterialTheme {
                AlertDialog(
                    onDismissRequest = {},
                    modifier = Modifier.testTag("dlg"),
                    text = {
                        if (resize) DialogImeResize()
                        Text("body")
                    },
                    confirmButton = { TextButton(onClick = {}) { Text("Close") } },
                )
            }
        }
        rule.onNodeWithTag("dlg").assertExists()
    }

    @Test
    fun `the helper makes the dialog window resize for the keyboard`() {
        showDialog(resize = true)
        assertEquals(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE, latestAdjustMode())
    }

    @Test
    fun `a dialog without it does not resize`() {
        showDialog(resize = false)
        assertNotEquals(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE, latestAdjustMode())
    }
}
