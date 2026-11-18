package com.android.shaftschematic.ui.screen

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.AnnotatedString
import com.android.shaftschematic.geom.UndercutLinerSide
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
 * A cut typed on a liner's page that misses the liner (on-device report: a sketch drawn mirrored
 * was entered from the FWD S.E.T. instead of the AFT one, and the cuts landed on bare shaft with
 * only a grey "Extends … past the liner's FWD edge" line to say so). The card warns, the pill asks,
 * and the question offers to record it anyway or to read the same Distance from the other S.E.T.
 *
 * The shaft: 120 in (3048 mm), no tapers — S.E.T.s at 0 and 3048 — with one liner 20–40 in.
 * 24 in from the FWD S.E.T., 2 in long, is 94–96 in: 54 in FWD of the liner. The same 24 in from
 * the AFT S.E.T. is 24–26 in, on the liner.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class UndercutOffLinerOverlayTest {

    @get:Rule
    val rule = createComposeRule()

    private val inch = 25.4f
    private val oal = 120f * inch
    private val liner = Liner(id = "L1", startFromAftMm = 20f * inch, lengthMm = 20f * inch, odMm = 200f)
    private val linerSpan = UndercutLinerSpan("L1", 20f * inch, 40f * inch)
    private val spec = ShaftSpec(overallLengthMm = oal, liners = listOf(liner))

    private var record by mutableStateOf(UndercutRecord())
    private val added = mutableListOf<Float>()
    private val addedRefs = mutableListOf<UndercutReference>()
    private var closed = 0

    private fun show(cuts: List<Undercut> = emptyList(), seedNewCut: Boolean = true) {
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
                    onAddUndercut = { start, len, ref, refLinerId, _ ->
                        added += start
                        addedRefs += ref
                        val u = Undercut(
                            id = "new", startFromAftMm = start, lengthMm = len,
                            authoredReference = ref, referenceLinerId = refLinerId,
                        )
                        record = record.copy(undercuts = record.undercuts + u)
                        u.id
                    },
                    onUpdateUndercut = { _, _, _, _, _ -> },
                    onUpdateReference = { _, _, _, _ -> },
                    onRemoveUndercut = {},
                    onClose = { closed++ },
                    seedNewCut = seedNewCut,
                )
            }
        }
        settle()
    }

    private fun settle() {
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
    }

    private val pendingCard = hasAnyAncestor(hasTestTag("undercut_card_$PENDING_UNDERCUT_ID"))
    private val pill get() = rule.onNodeWithTag("undercut_status_pill", useUnmergedTree = true)

    private fun typeAndCommit(field: SemanticsMatcher, text: String) {
        rule.onNode(field).performScrollTo().performTextReplacement(text)
        rule.onNode(field).performImeAction()
        settle()
    }

    private fun SemanticsMatcher.holds(text: String) = rule.onNode(this).assert(
        SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(text)),
    )

    private fun exists(m: SemanticsMatcher) =
        rule.onAllNodes(m, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** The new cut typed 24 in from the FWD S.E.T., 2 in long — the mirrored sketch. */
    private fun typeTheMirroredCut() {
        show()
        rule.onNode(hasText("FWD S.E.T.") and pendingCard).performScrollTo().performClick()
        settle()
        typeAndCommit(hasText("Distance from FWD S.E.T. (in)") and pendingCard, "24")
        typeAndCommit(hasText("Length (in)") and pendingCard, "2")
    }

    private val reason get() = undercutOffLinerMessage("${disp(54f * inch, UnitSystem.INCHES)} in", UndercutLinerSide.FWD)

    @Test
    fun `a cut off the liner is warned on the card and asked about on the pill`() {
        typeTheMirroredCut()
        assertEquals("Not on this liner — 54 in FWD of it", reason)
        assertTrue("the card's warning", exists(hasText(reason) and pendingCard))
        assertTrue(
            "the other S.E.T.'s reading",
            exists(hasText(undercutMirrorHint("${disp(24f * inch, UnitSystem.INCHES)} in", UndercutReference.AFT_SET)) and pendingCard),
        )
        assertTrue(
            "no 'Extends … past the liner's edge' line for a cut that misses it",
            !exists(hasTestTag("undercut_overhang_info")),
        )
        rule.onNodeWithTag("undercut_overhang_dialog").assertDoesNotExist()
        pill.assert(hasAnyDescendant(hasText("Not on this liner — tap to record anyway")))
        assertTrue("nothing recorded yet", added.isEmpty())
    }

    @Test
    fun `record anyway from the pill records the cut as typed`() {
        typeTheMirroredCut()
        rule.onNodeWithTag("undercut_blocked_save", useUnmergedTree = true).performClick()
        settle()
        rule.onNodeWithText(UNDERCUT_OFF_LINER_TITLE).assertExists()
        rule.onNodeWithTag("undercut_leave_mirror_datum").assertExists()
        rule.onNodeWithText("Measure from AFT S.E.T. instead").assertExists()
        rule.onNodeWithTag("undercut_leave_keep").assertExists()
        rule.onNodeWithTag("undercut_leave_record_off_liner").performClick()
        settle()

        assertEquals(1, added.size)
        assertEquals(94f * inch, added.single(), 0.01f)
        assertEquals(listOf(UndercutReference.FWD_SET), addedRefs)
    }

    @Test
    fun `measuring from the other SET moves the cut onto the liner and keeps the typed number`() {
        typeTheMirroredCut()
        rule.onNodeWithTag("undercut_blocked_save", useUnmergedTree = true).performClick()
        settle()
        rule.onNodeWithTag("undercut_leave_mirror_datum").performClick()
        settle()

        rule.onNodeWithText(UNDERCUT_OFF_LINER_TITLE).assertDoesNotExist()
        (hasText("Distance from AFT S.E.T. (in)") and pendingCard).holds(disp(24f * inch, UnitSystem.INCHES))
        (hasText("Length (in)") and pendingCard).holds(disp(2f * inch, UnitSystem.INCHES))
        assertTrue("the warning goes with the cut", !exists(hasText(reason) and pendingCard))
        // Moved, not recorded: the card stays open for a look.
        assertTrue(added.isEmpty())
        pill.assert(hasAnyDescendant(hasText("Record cut")))

        rule.onNodeWithTag("undercut_confirm", useUnmergedTree = true).performClick()
        settle()
        assertEquals(24f * inch, added.single(), 0.01f)
        assertEquals(listOf(UndercutReference.AFT_SET), addedRefs)
    }

    @Test
    fun `leaving with a cut off the liner asks, and Record anyway records it and goes`() {
        typeTheMirroredCut()
        rule.onNodeWithContentDescription("Back to Undercut Drawing").performClick()
        settle()
        rule.onNodeWithText(UNDERCUT_OFF_LINER_TITLE).assertExists()
        rule.onNodeWithTag("undercut_leave_discard").assertExists()
        assertEquals("closed before the answer", 0, closed)

        rule.onNodeWithTag("undercut_leave_record_off_liner").performClick()
        settle()
        assertEquals(94f * inch, added.single(), 0.01f)
        assertEquals(1, closed)
    }

    @Test
    fun `a cut run on from the liner's own cuts is on its section`() {
        // u1 runs off the liner's FWD edge (38–42 in); the next cut butts on its FWD end, clear
        // of the liner but chained to its section, as the strip builder assigns it.
        show(
            listOf(
                Undercut(
                    id = "u1", startFromAftMm = 38f * inch, lengthMm = 4f * inch,
                    authoredReference = UndercutReference.LINER_AFT, referenceLinerId = "L1",
                ),
            ),
            seedNewCut = false,
        )
        rule.onNodeWithTag("undercut_add_in_liner").performClick()
        settle()
        typeAndCommit(hasText("Length (in)") and pendingCard, "1")
        pill.assert(hasAnyDescendant(hasText("Record cut")))
        assertTrue(!exists(hasTestTag("undercut_off_liner_warning")))
    }
}
