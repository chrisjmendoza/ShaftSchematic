package com.android.shaftschematic.ui.screen

import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowManager
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/**
 * [ImeResizingAlertDialog] switches its dialog window to RESIZE above the keyboard rather
 * than pan, so the fields at a dialog's foot and its buttons can be scrolled into view while
 * typing (on-device report: the keyway calculator's cutter fields sat under the keyboard), and
 * fixes the window at `MATCH_PARENT` so the platform's wrap-content pre-measure cannot reset
 * the scroll on every keystroke (on-device report: the dialog "kept moving down"). Pinned on
 * plain dialogs: the calculator dialogs themselves never idle under Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class CalcDialogImeResizeTest {

    @get:Rule
    val rule = createComposeRule()

    private fun latestAttrs(): WindowManager.LayoutParams =
        ShadowDialog.getLatestDialog().window!!.attributes

    private fun latestAdjustMode(): Int =
        latestAttrs().softInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST

    @Test
    fun `the resizing dialog resizes for the keyboard and fixes its window size`() {
        rule.setContent {
            MaterialTheme {
                ImeResizingAlertDialog(
                    onDismissRequest = {},
                    modifier = Modifier.testTag("dlg"),
                    title = { Text("Title") },
                    text = { Text("body") },
                    confirmButton = { TextButton(onClick = {}) { Text("Close") } },
                )
            }
        }
        rule.onNodeWithTag("dlg").assertExists()
        rule.waitForIdle()
        assertEquals(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE, latestAdjustMode())
        assertEquals(MATCH_PARENT, latestAttrs().width)
        assertEquals(MATCH_PARENT, latestAttrs().height)
    }

    /** The dialog's root view, the one view whose padding the box centers inside. */
    private fun dialogRoot(): View {
        fun find(v: View): View? {
            if (v is DialogWindowProvider) return v
            if (v is ViewGroup) for (i in 0 until v.childCount) find(v.getChildAt(i))?.let { return it }
            return null
        }
        return find(ShadowDialog.getLatestDialog().window!!.decorView)!!
    }

    private fun dispatchImeInset(bottomPx: Int) {
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, bottomPx))
            .setVisible(WindowInsetsCompat.Type.ime(), bottomPx > 0)
            .build()
        // Dispatched at the dialog root: the platform's fitsSystemWindows frame above it is not
        // what is under test, and on the JVM it consumes a hand-built inset set outright.
        rule.runOnIdle { ViewCompat.dispatchApplyWindowInsets(dialogRoot(), insets) }
        rule.waitForIdle()
    }

    @Test
    fun `the keyboard's inset pads the dialog root so the box sits above the keyboard`() {
        // On API 36 the window often keeps its full height while the keyboard inset is delivered
        // on top of it (emulator QA: Save under the keyboard after a saved runout reading); the
        // inset, not the window resize, is what lifts the box.
        rule.setContent {
            MaterialTheme {
                ImeResizingAlertDialog(
                    onDismissRequest = {},
                    modifier = Modifier.testTag("dlg"),
                    title = { Text("Title") },
                    text = { Text("body") },
                    confirmButton = { TextButton(onClick = {}) { Text("Save") } },
                )
            }
        }
        rule.onNodeWithTag("dlg").assertExists()
        rule.waitForIdle()
        assertEquals(0, dialogRoot().paddingBottom)
        dispatchImeInset(820)
        assertEquals(820, dialogRoot().paddingBottom)
        dispatchImeInset(0)
        assertEquals(0, dialogRoot().paddingBottom)
    }

    @Test
    fun `a plain AlertDialog does neither`() {
        rule.setContent {
            MaterialTheme {
                AlertDialog(
                    onDismissRequest = {},
                    modifier = Modifier.testTag("dlg"),
                    text = { Text("body") },
                    confirmButton = { TextButton(onClick = {}) { Text("Close") } },
                )
            }
        }
        rule.onNodeWithTag("dlg").assertExists()
        rule.waitForIdle()
        assertNotEquals(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE, latestAdjustMode())
        assertNotEquals(MATCH_PARENT, latestAttrs().height)
    }
}
