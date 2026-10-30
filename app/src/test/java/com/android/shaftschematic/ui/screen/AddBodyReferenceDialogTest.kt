package com.android.shaftschematic.ui.screen

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.android.shaftschematic.model.LinerAuthoredReference
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `AddBodyDialog`'s "Measure From: AFT | FWD" chips — the add-dialog-parity mirror of the
 * explicit-body card's row. A FWD entry submits the PHYSICAL start with `reference = FWD`, and a
 * FWD start that would reach past the AFT face is refused with a message, never clamped.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h1600dp")
class AddBodyReferenceDialogTest {

    @get:Rule
    val rule = createComposeRule()

    private var submitted: Pair<Float, LinerAuthoredReference>? = null

    private fun show() {
        rule.setContent {
            MaterialTheme {
                AddBodyDialog(
                    unit = UnitSystem.MILLIMETERS,
                    spec = ShaftSpec(overallLengthMm = 1000f),
                    initialStartMm = 0f,
                    initialLengthMm = 300f,
                    onSubmit = { start, _, _, ref, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _ ->
                        submitted = start to ref
                    },
                    onCancel = {},
                )
            }
        }
        rule.waitForIdle()
    }

    private fun field(label: String): SemanticsNodeInteraction =
        rule.onNode(hasSetTextAction() and hasText(label, substring = true))

    private fun addButton() = rule.onNode(hasText("Add") and hasSetTextAction().not())

    @Test
    fun `the chips are present and AFT submits the typed start with AFT`() {
        show()
        rule.onNodeWithTag("add_body_ref_aft").assertExists()
        rule.onNodeWithTag("add_body_ref_fwd").assertExists()
        field("Start from AFT").performTextReplacement("120")
        rule.waitForIdle()

        addButton().performClick()
        rule.waitForIdle()

        assertNotNull("Add submitted", submitted)
        assertEquals(120f, submitted!!.first, 1e-3f)
        assertEquals(LinerAuthoredReference.AFT, submitted!!.second)
    }

    @Test
    fun `a FWD entry submits the physical start and the FWD reference`() {
        show()
        rule.onNodeWithTag("add_body_ref_fwd").performClick()
        rule.waitForIdle()
        field("Start from FWD").performTextReplacement("50")
        rule.waitForIdle()

        addButton().assertIsEnabled().performClick()
        rule.waitForIdle()

        // 50 from the FWD face of a 1000 shaft, 300 long → starts 650 from AFT.
        assertNotNull("Add submitted", submitted)
        assertEquals(650f, submitted!!.first, 1e-3f)
        assertEquals(LinerAuthoredReference.FWD, submitted!!.second)
    }

    @Test
    fun `a FWD start past the AFT face disables Add with the message`() {
        show()
        rule.onNodeWithTag("add_body_ref_fwd").performClick()
        rule.waitForIdle()
        field("Start from FWD").performTextReplacement("800")
        rule.waitForIdle()

        rule.onNodeWithText(ADD_PAST_AFT_END_MSG, substring = true).assertExists()
        addButton().assertIsNotEnabled()
    }
}
