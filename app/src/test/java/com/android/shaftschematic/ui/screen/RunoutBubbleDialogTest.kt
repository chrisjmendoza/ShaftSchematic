package com.android.shaftschematic.ui.screen

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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

    @Test
    fun `unreadable text cannot be saved`() {
        show()
        rule.onNodeWithTag("runout_bubble_value").performTextInput("1/")
        rule.onNodeWithTag("runout_bubble_save").assertIsNotEnabled()
    }
}
