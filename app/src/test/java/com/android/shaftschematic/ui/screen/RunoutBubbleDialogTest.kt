package com.android.shaftschematic.ui.screen

import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/**
 * The bubble editor's TIR field reads what it admits. The input filter allowed shop fractions
 * and Save parsed with `toDoubleOrNull`, so `1/64` saved nothing (on-device report). Save now
 * goes through the same parser every numeric field uses, and is disabled while the text is
 * unreadable rather than quietly dropping it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class RunoutBubbleDialogTest {

    @get:Rule
    val rule = createComposeRule()

    private var saved: Pair<Float?, Int?>? = null

    private fun show(initialMm: Float? = null, unit: UnitSystem = UnitSystem.INCHES) {
        saved = null
        rule.setContent {
            MaterialTheme {
                RunoutBubbleDialog(
                    title = "AFT Liner · station 1",
                    unit = unit,
                    initialValueMm = initialMm,
                    initialHighSpotHalfHours = null,
                    onSave = { mm, tick -> saved = mm to tick },
                    onDismiss = {},
                )
            }
        }
    }

    @Test
    fun `a fraction saves as its mm value`() {
        show()
        rule.onNodeWithTag("runout_bubble_value").performTextInput("1/64")
        rule.onNodeWithTag("runout_bubble_save").assertIsEnabled().performClick()

        val mm = requireNotNull(saved).first
        assertNotNull("the typed fraction must reach onSave", mm)
        assertEquals(25.4f / 64f, mm!!, 1e-5f)
    }

    @Test
    fun `a decimal still saves`() {
        show(unit = UnitSystem.MILLIMETERS)
        rule.onNodeWithTag("runout_bubble_value").performTextInput("0.05")
        rule.onNodeWithTag("runout_bubble_save").performClick()
        assertEquals(0.05f, requireNotNull(saved).first!!, 1e-6f)
    }

    @Test
    fun `blank saves no value`() {
        show(initialMm = 0.254f)
        rule.onNodeWithTag("runout_bubble_value").performTextClearance()
        rule.onNodeWithTag("runout_bubble_save").assertIsEnabled().performClick()
        assertTrue(saved != null)
        assertNull(requireNotNull(saved).first)
    }

    /**
     * Emulator QA: a typed 0.0015" re-opened as `.002`, and a Save that only moved the high spot
     * stored 0.002". An untouched seed saves the STORED value, bit for bit.
     */
    @Test
    fun `re-saving an untouched reading keeps the stored value exactly`() {
        show(initialMm = 0.0381f) // 0.0015"
        rule.onNodeWithTag("runout_bubble_value").assertTextContains(".002")
        rule.onNodeWithTag("runout_bubble_save").performClick()
        assertEquals(0.0381f, requireNotNull(saved).first!!, 0f)
    }

    @Test
    fun `a fraction re-saved untouched is not rounded to thousandths`() {
        show(initialMm = 25.4f / 64f)
        rule.onNodeWithTag("runout_bubble_save").performClick()
        assertEquals(25.4f / 64f, requireNotNull(saved).first!!, 0f)
    }

    @Test
    fun `an edited reading is re-parsed`() {
        show(initialMm = 0.0381f)
        rule.onNodeWithTag("runout_bubble_value").performTextClearance()
        rule.onNodeWithTag("runout_bubble_value").performTextInput(".002")
        rule.onNodeWithTag("runout_bubble_save").performClick()
        assertEquals(0.0508f, requireNotNull(saved).first!!, 1e-6f)
    }

    @Test
    fun `unreadable text cannot be saved`() {
        show()
        rule.onNodeWithTag("runout_bubble_value").performTextInput("1/")
        rule.onNodeWithTag("runout_bubble_save").assertIsNotEnabled()
    }

    /** A rotation recreates the activity; the typed TIR comes back with the dialog. */
    @Test
    fun `a typed value survives a recreation and still saves`() {
        saved = null
        val restorer = StateRestorationTester(rule)
        restorer.setContent {
            MaterialTheme {
                RunoutBubbleDialog(
                    title = "AFT Liner · station 1",
                    unit = UnitSystem.MILLIMETERS,
                    initialValueMm = null,
                    initialHighSpotHalfHours = null,
                    onSave = { mm, tick -> saved = mm to tick },
                    onDismiss = {},
                )
            }
        }
        rule.onNodeWithTag("runout_bubble_value").performTextInput("0.08")

        restorer.emulateSavedInstanceStateRestore()

        rule.onNodeWithTag("runout_bubble_save").performClick()
        assertEquals(0.08f, requireNotNull(saved).first!!, 1e-6f)
    }

    /**
     * In landscape the keyboard covered Cancel and Save (on-device report): the editor is an
     * [ImeResizingAlertDialog], so its window resizes above the keyboard at a fixed size.
     */
    @Test
    fun `the editor resizes above the keyboard and keeps its buttons`() {
        show()
        rule.waitForIdle()
        val attrs = ShadowDialog.getLatestDialog().window!!.attributes
        assertEquals(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
            attrs.softInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST,
        )
        assertEquals(MATCH_PARENT, attrs.width)
        assertEquals(MATCH_PARENT, attrs.height)
        rule.onNodeWithText("Clear").assertExists()
        rule.onNodeWithText("Cancel").assertExists()
        rule.onNodeWithTag("runout_bubble_save").assertExists()
    }
}
