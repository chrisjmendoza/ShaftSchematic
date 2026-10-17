package com.android.shaftschematic.ui.screen

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import com.android.shaftschematic.geom.UndercutLinerSpan
import com.android.shaftschematic.geom.UndercutSpanMm
import com.android.shaftschematic.geom.linerStripFor
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Undercut
import com.android.shaftschematic.model.UndercutRecord
import com.android.shaftschematic.model.UndercutReference
import com.android.shaftschematic.ui.resolved.resolveComponents
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The undercut detail overlay's selection ↔ pager binding, composed against a live
 * [UndercutRecord] that every record callback writes back, the way the ViewModel does: the trash
 * deletes the SELECTED card and nothing else, the overlay opens on the requested card, and "Add
 * undercut" whose pending page sorts to the open card's index shows the pending card without
 * recording anything. A typed value that cannot be confirmed stays in its field and the card
 * says why, and an Undo that takes away a cut with unsaved edits says those edits went.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class UndercutDetailOverlayTest {

    @get:Rule
    val rule = createComposeRule()

    private val oal = 2000f
    private val liner = Liner(id = "L1", startFromAftMm = 500f, lengthMm = 1000f, odMm = 200f)
    private val linerSpan = UndercutLinerSpan("L1", 500f, 1500f)
    private val spec = ShaftSpec(overallLengthMm = oal, liners = listOf(liner))

    private var record by mutableStateOf(UndercutRecord())
    private val removed = mutableListOf<String>()
    private val added = mutableListOf<Float>()
    private val updated = mutableListOf<String>()

    private fun cut(id: String, start: Float, len: Float = 50f) = Undercut(
        id = id, startFromAftMm = start, lengthMm = len,
        authoredReference = UndercutReference.LINER_AFT, referenceLinerId = "L1",
    )

    private fun show(cuts: List<Undercut>, initialSelectedId: String? = null) {
        record = UndercutRecord(cuts)
        val resolved = resolveComponents(spec)
        rule.setContent {
            MaterialTheme {
                val spans = record.undercuts.map {
                    UndercutSpanMm(it.id, it.startFromAftMm, it.startFromAftMm + it.lengthMm)
                }
                UndercutWindowDetailOverlay(
                    strip = linerStripFor(linerSpan, spans, oal),
                    stripKey = "liner:L1",
                    spec = spec,
                    resolvedComponents = resolved,
                    unit = UnitSystem.INCHES,
                    undercutRecord = record,
                    onAddUndercut = { start, len, ref, refLinerId ->
                        added += start
                        val u = Undercut(
                            id = "new", startFromAftMm = start, lengthMm = len,
                            authoredReference = ref, referenceLinerId = refLinerId,
                        )
                        record = record.copy(undercuts = record.undercuts + u)
                        u.id
                    },
                    onUpdateUndercut = { id, _, _, _, _ -> updated += id },
                    onUpdateReference = { _, _, _ -> },
                    onRemoveUndercut = { id ->
                        removed += id
                        record = record.copy(undercuts = record.undercuts.filterNot { it.id == id })
                    },
                    onClose = {},
                    initialSelectedId = initialSelectedId,
                )
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun `trash on the selected card deletes that cut and no other`() {
        show(
            listOf(cut("u1", 600f), cut("u2", 800f), cut("u3", 1000f)),
            initialSelectedId = "u2",
        )
        // A neighbour's trash (its card peeks beside the open one) is not the selected card:
        // it brings the selected card back to the front and deletes nothing.
        rule.onNodeWithContentDescription("Delete ${undercutCardTitle(1, 3)}")
            .performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
        assertTrue("a neighbour's trash deleted $removed", removed.isEmpty())

        // The overlay opened on u2 (the second card), so its trash is the live one.
        rule.onNodeWithContentDescription("Delete ${undercutCardTitle(2, 3)}").performClick()
        rule.waitForIdle()
        assertEquals(listOf("u2"), removed)
        assertEquals(listOf("u1", "u3"), record.undercuts.map { it.id })
    }

    @Test
    fun `add in this liner shows the pending card and records nothing until confirm`() {
        show(listOf(cut("u1", 900f)))
        rule.onNodeWithTag("undercut_add_in_liner").performClick()
        rule.waitForIdle()
        // Let any settle/heal/leave pass run to completion before judging.
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
        rule.onNodeWithText("New undercut").assertIsDisplayed()
        assertTrue("the pending cut was recorded at $added", added.isEmpty())
        assertEquals(listOf("u1"), record.undercuts.map { it.id })
    }

    private fun editableStartsWith(prefix: String) = SemanticsMatcher("editable text starts with $prefix") {
        it.config.getOrNull(SemanticsProperties.EditableText)?.text?.startsWith(prefix) == true
    }

    @Test
    fun `a length that runs off the shaft stays as typed and the card says why`() {
        // 100 in on a 2000 mm shaft: the field must keep the typed value — it lands in the draft
        // and Confirm is what refuses it — never put the old length back.
        show(listOf(cut("u1", 900f)))
        rule.onNodeWithText("Length (in)").performTextReplacement("100")
        rule.onNodeWithText("Length (in)").performImeAction()
        rule.waitForIdle()

        assertTrue(
            "the typed length was reverted",
            rule.onAllNodes(editableStartsWith("100")).fetchSemanticsNodes().isNotEmpty(),
        )
        assertTrue(
            "no field may report the value as not applied",
            rule.onAllNodesWithText("not applied", substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty(),
        )
        assertTrue(
            "the blocking reason must be stated",
            rule.onAllNodesWithText("Undercut extends past the shaft's FWD end", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
        // The value reached the DRAFT: the pill reports the draft blocked, not "Saved".
        rule.onNodeWithTag("undercut_status_pill", useUnmergedTree = true)
            .assert(hasAnyDescendant(hasText("Undercut extends past the shaft's FWD end")))
        assertTrue("a blocked draft reached the record", updated.isEmpty())
    }

    @Test
    fun `an undo that removes a cut with unsaved edits says they were discarded`() {
        show(listOf(cut("u1", 900f)))
        rule.onNodeWithText("Length (in)").performTextReplacement("3")
        rule.onNodeWithText("Length (in)").performImeAction()
        rule.waitForIdle()

        // What an Undo of the cut's own add does to the record.
        record = record.copy(undercuts = emptyList())
        rule.waitForIdle()

        rule.onNodeWithText(UNDERCUT_DISCARDED_DRAFT_MSG).assertIsDisplayed()
    }

    @Test
    fun `an undo that removes an unedited cut raises no discard notice`() {
        show(listOf(cut("u1", 900f)))
        record = record.copy(undercuts = emptyList())
        rule.waitForIdle()

        assertTrue(
            rule.onAllNodesWithText(UNDERCUT_DISCARDED_DRAFT_MSG).fetchSemanticsNodes().isEmpty(),
        )
    }

    // ── "Past the liner edge" ──
    // u1 sits at 900 mm, 50 mm long, on the 500–1500 mm liner; a 30 in (762 mm) Length runs its
    // FWD end to 1662 mm — 162 mm past the liner's FWD edge, still on the 2000 mm shaft.

    private fun typeLengthPastTheLiner() {
        show(listOf(cut("u1", 900f)))
        rule.onNodeWithText("Length (in)").performTextReplacement("30")
        rule.onNodeWithText("Length (in)").performImeAction()
        rule.waitForIdle()
    }

    private val fwdWarning get() =
        undercutOverhangWarning("${disp(162f, UnitSystem.INCHES)} in", "FWD")

    @Test
    fun `a length run past the liner asks, and No keeps the value and warns`() {
        typeLengthPastTheLiner()
        rule.onNodeWithTag("undercut_overhang_dialog").assertIsDisplayed()
        rule.onNodeWithText("Past the liner edge").assertIsDisplayed()

        rule.onNodeWithTag("undercut_overhang_decline").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("undercut_overhang_dialog").assertDoesNotExist()
        // The warning sits under the Length field, in the card's own scroll.
        rule.onNodeWithTag("undercut_overhang_warning", useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
        rule.onNodeWithText(fwdWarning, useUnmergedTree = true).assertIsDisplayed()
        assertTrue(
            "the typed length was changed",
            rule.onAllNodes(editableStartsWith("30")).fetchSemanticsNodes().isNotEmpty(),
        )
        // The geometry is legal: the draft is confirmable, not blocked.
        rule.onNodeWithTag("undercut_confirm", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `a length run past the liner asks, and Add extension leaves no warning`() {
        typeLengthPastTheLiner()
        rule.onNodeWithTag("undercut_overhang_accept").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("undercut_overhang_dialog").assertDoesNotExist()
        rule.onNodeWithTag("undercut_overhang_warning", useUnmergedTree = true).assertDoesNotExist()
        assertTrue(
            "the typed length was changed",
            rule.onAllNodes(editableStartsWith("30")).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    @Test
    fun `a length that stays on the liner asks nothing`() {
        show(listOf(cut("u1", 900f)))
        rule.onNodeWithText("Length (in)").performTextReplacement("3")
        rule.onNodeWithText("Length (in)").performImeAction()
        rule.waitForIdle()

        rule.onNodeWithTag("undercut_overhang_dialog").assertDoesNotExist()
    }
}
