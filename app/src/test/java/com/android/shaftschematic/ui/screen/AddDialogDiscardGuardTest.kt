package com.android.shaftschematic.ui.screen

import android.app.Dialog
import androidx.activity.ComponentDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.android.shaftschematic.model.BoltHoleClocking
import com.android.shaftschematic.model.BoltHoleStyle
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/**
 * Back or a tap outside on an Add dialog the user has filled in asks before discarding the entry
 * (on-device ruling: "it should ask to be safe"); an untouched dialog closes as before, and the
 * explicit Cancel button stays a direct discard. Back is driven through the dialog window's own
 * back dispatcher — the path the system Back gesture takes, and the one that reaches the
 * dialog's `onDismissRequest`, the same callback a tap outside fires.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h1600dp")
class AddDialogDiscardGuardTest {

    @get:Rule
    val rule = createComposeRule()

    private val spec = ShaftSpec(overallLengthMm = 100f * 25.4f)

    private var cancelled = 0
    private var submitted = 0

    private fun field(label: String): SemanticsNodeInteraction = rule.onNode(hasSetTextAction() and hasText(label))

    /** Hosts [dialog] behind an open flag its cancel clears, so a discard really closes it. */
    private fun show(dialog: @Composable (onCancel: () -> Unit) -> Unit) {
        rule.setContent {
            MaterialTheme {
                var open by remember { mutableStateOf(true) }
                if (open) dialog { cancelled++; open = false }
            }
        }
        rule.waitForIdle()
    }

    private fun showLiner() = show { onCancel ->
        AddLinerDialog(
            unit = UnitSystem.INCHES,
            spec = spec,
            onSubmit = { _, _, _, _, _ -> submitted++ },
            onCancel = onCancel,
        )
    }

    /** System Back on the top-most showing dialog window. */
    private fun pressBack() {
        val top: Dialog = ShadowDialog.getShownDialogs().last { it.isShowing }
        rule.runOnUiThread { (top as ComponentDialog).onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    private fun promptShown(): Boolean =
        rule.onAllNodes(hasText("What you typed will be lost.")).fetchSemanticsNodes().isNotEmpty()

    private fun dialogShown(title: String): Boolean =
        rule.onAllNodes(hasText(title)).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `an untouched dialog closes on Back with no prompt`() {
        showLiner()
        pressBack()
        assertEquals(false, promptShown())
        assertEquals(1, cancelled)
        assertEquals(false, dialogShown("Add Liner"))
    }

    @Test
    fun `a typed value makes Back ask first`() {
        showLiner()
        field("Length (in)").performTextReplacement("12")
        rule.waitForIdle()
        pressBack()
        rule.onNodeWithTag("add_dialog_discard_confirm").assertExists()
        rule.onNodeWithText("Discard this liner?").assertExists()
        assertEquals(0, cancelled)
        assertEquals(true, dialogShown("Add Liner"))
    }

    @Test
    fun `Keep editing returns to the dialog with the typed text intact`() {
        showLiner()
        field("Length (in)").performTextReplacement("12")
        rule.waitForIdle()
        pressBack()
        rule.onNodeWithTag("add_dialog_keep_editing").performClick()
        rule.waitForIdle()
        assertEquals(false, promptShown())
        assertEquals(0, cancelled)
        rule.onNode(hasSetTextAction() and hasText("12")).assertExists()
    }

    @Test
    fun `Back on the prompt itself keeps editing`() {
        showLiner()
        field("Length (in)").performTextReplacement("12")
        rule.waitForIdle()
        pressBack()
        pressBack()
        assertEquals(false, promptShown())
        assertEquals(0, cancelled)
        rule.onNode(hasSetTextAction() and hasText("12")).assertExists()
    }

    @Test
    fun `Discard closes the dialog and adds nothing`() {
        showLiner()
        field("Length (in)").performTextReplacement("12")
        rule.waitForIdle()
        pressBack()
        rule.onNodeWithTag("add_dialog_discard").performClick()
        rule.waitForIdle()
        assertEquals(1, cancelled)
        assertEquals(0, submitted)
        assertEquals(false, dialogShown("Add Liner"))
        assertEquals(false, promptShown())
    }

    @Test
    fun `a chip change alone counts as an edit`() {
        showLiner()
        rule.onNodeWithText("FWD").performClick()
        rule.waitForIdle()
        pressBack()
        rule.onNodeWithTag("add_dialog_discard_confirm").assertExists()
        assertEquals(0, cancelled)
    }

    @Test
    fun `re-tapping the selected chip is not an edit`() {
        showLiner()
        rule.onNodeWithText("AFT").performClick()
        rule.waitForIdle()
        pressBack()
        assertEquals(false, promptShown())
        assertEquals(1, cancelled)
    }

    @Test
    fun `Cancel on a filled dialog discards without a prompt`() {
        showLiner()
        field("Length (in)").performTextReplacement("12")
        rule.waitForIdle()
        rule.onNodeWithText("Cancel").performClick()
        rule.waitForIdle()
        assertEquals(false, promptShown())
        assertEquals(1, cancelled)
        assertEquals(0, submitted)
        assertEquals(false, dialogShown("Add Liner"))
    }

    // ── Every Add dialog routes through the guard ───────────────────────────────

    @Test
    fun `the body dialog asks, and its shared Keyway checkbox alone counts`() {
        show { onCancel ->
            AddBodyDialog(
                unit = UnitSystem.INCHES,
                spec = spec,
                onSubmit = { _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> submitted++ },
                onCancel = onCancel,
            )
        }
        rule.onNodeWithTag("add_body_kw_gate").performClick()
        rule.waitForIdle()
        pressBack()
        rule.onNodeWithText("Discard this body?").assertExists()
        assertEquals(0, cancelled)
    }

    @Test
    fun `the taper dialog asks`() {
        show { onCancel ->
            AddTaperDialog(
                unit = UnitSystem.INCHES,
                spec = spec,
                onSubmit = { _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> submitted++ },
                onCancel = onCancel,
            )
        }
        field("L.E.T. Ø (in)").performTextReplacement("6")
        rule.waitForIdle()
        pressBack()
        rule.onNodeWithText("Discard this taper?").assertExists()
        assertEquals(0, cancelled)
    }

    @Test
    fun `the thread dialog asks, and its shared relief switch alone counts`() {
        show { onCancel ->
            AddThreadDialog(
                unit = UnitSystem.INCHES,
                spec = spec,
                initialStartMm = 0f,
                initialLengthMm = 5f * 25.4f,
                initialMajorDiaMm = 5f * 25.4f,
                initialPitchMm = 25.4f / 4f,
                onSubmit = { _, _, _, _, _, _, _, _, _, _ -> submitted++ },
                onCancel = onCancel,
            )
        }
        rule.onNodeWithTag("add_thread_relief").performClick()
        rule.waitForIdle()
        pressBack()
        rule.onNodeWithText("Discard this thread?").assertExists()
        assertEquals(0, cancelled)
    }

    @Test
    fun `the coupler bolt slot dialog asks on a switch flip`() {
        show { onCancel ->
            AddCouplerBoltSlotDialog(
                unit = UnitSystem.INCHES,
                spec = spec,
                initialStartMm = 0f,
                initialHoleDiaMm = 25.4f,
                initialCount = 1,
                initialSpacingMm = 50.8f,
                initialDepthMm = 12.7f,
                onSubmit = { _, _, _, _, _, _, _, _: BoltHoleStyle, _: BoltHoleClocking -> submitted++ },
                onCancel = onCancel,
            )
        }
        rule.onNodeWithText("Cross-drilled").performClick()
        rule.waitForIdle()
        pressBack()
        rule.onNodeWithText("Discard this coupler bolt slot?").assertExists()
        assertEquals(0, cancelled)
    }

    @Test
    fun `an untouched thread dialog still closes on Back`() {
        show { onCancel ->
            AddThreadDialog(
                unit = UnitSystem.INCHES,
                spec = spec,
                initialStartMm = 0f,
                initialLengthMm = 5f * 25.4f,
                initialMajorDiaMm = 5f * 25.4f,
                initialPitchMm = 25.4f / 4f,
                onSubmit = { _, _, _, _, _, _, _, _, _, _ -> submitted++ },
                onCancel = onCancel,
            )
        }
        pressBack()
        assertEquals(false, promptShown())
        assertEquals(1, cancelled)
    }

    // ── The worn-section editor, whose dismiss discards typed values too ─────────

    private fun showWornSection() = show { onCancel ->
        WornSectionDialog(
            existing = null,
            unit = UnitSystem.INCHES,
            aftSetXMm = 0f,
            fwdSetXMm = spec.overallLengthMm,
            onSave = { _, _, _, _ -> submitted++ },
            onDelete = null,
            onDismiss = onCancel,
        )
    }

    @Test
    fun `the worn-section editor asks once something is typed`() {
        showWornSection()
        field("Length (in)").performTextReplacement("4")
        rule.waitForIdle()
        pressBack()
        rule.onNodeWithText("Discard this worn section?").assertExists()
        assertEquals(0, cancelled)
    }

    @Test
    fun `an untouched worn-section editor closes on Back`() {
        showWornSection()
        pressBack()
        assertEquals(false, promptShown())
        assertEquals(1, cancelled)
    }
}
