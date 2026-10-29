package com.android.shaftschematic.ui.input

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [FocusParkingRoot]: out of touch mode, `View.clearFocus()` makes the window refocus, and the
 * Compose view hands that focus to the top-left focusable — on the editor, the Overall Length
 * field. The root target catches it instead, so releasing focus leaves no field focused
 * (emulator QA: cancelling the auto-body "Apply diameter to…" dialog dropped the cursor and the
 * keyboard into the OAL field). The control test pins the mechanism itself, so a platform or
 * Compose change that stops refocusing shows up here rather than as a silently idle wrapper.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class FocusParkingRootTest {

    @get:Rule
    val rule = createComposeRule()

    private lateinit var focusManager: FocusManager
    private lateinit var view: android.view.View

    @Composable
    private fun TwoFields() {
        focusManager = LocalFocusManager.current
        view = LocalView.current
        Column {
            OutlinedTextField(value = "368.5", onValueChange = {}, label = { Text("first") },
                modifier = Modifier.testTag("first"))
            OutlinedTextField(value = "10.25", onValueChange = {}, label = { Text("second") },
                modifier = Modifier.testTag("second"))
        }
    }

    /** Focuses the second field, leaves touch mode, and releases focus. */
    private fun focusSecondThenClearOutOfTouchMode() {
        rule.onNodeWithTag("second").performClick()
        rule.onNodeWithTag("second").assertIsFocused()
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        rule.waitForIdle()
        rule.runOnIdle { focusManager.clearFocus() }
        rule.waitForIdle()
    }

    @Test
    fun `without the root, releasing focus out of touch mode jumps to the first field`() {
        rule.setContent { MaterialTheme { TwoFields() } }
        focusSecondThenClearOutOfTouchMode()
        assertFalse("the window must be out of touch mode for the refocus", view.isInTouchMode)
        rule.onNodeWithTag("first").assertIsFocused()
    }

    @Test
    fun `with the root, releasing focus out of touch mode leaves every field unfocused`() {
        rule.setContent { MaterialTheme { FocusParkingRoot { TwoFields() } } }
        focusSecondThenClearOutOfTouchMode()
        rule.onNodeWithTag("first").assertIsNotFocused()
        rule.onNodeWithTag("second").assertIsNotFocused()
    }

    @Test
    fun `with the root, a field still takes focus when tapped`() {
        rule.setContent { MaterialTheme { FocusParkingRoot { TwoFields() } } }
        focusSecondThenClearOutOfTouchMode()
        rule.onNodeWithTag("first").performClick()
        rule.onNodeWithTag("first").assertIsFocused()
    }
}
