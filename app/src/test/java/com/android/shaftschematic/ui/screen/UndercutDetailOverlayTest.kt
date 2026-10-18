package com.android.shaftschematic.ui.screen

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithTag
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
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.geom.UndercutLinerSpan
import com.android.shaftschematic.geom.UndercutSpanMm
import com.android.shaftschematic.geom.linerStripFor
import com.android.shaftschematic.geom.nearestSetReference
import com.android.shaftschematic.geom.undercutAddSeedSpan
import com.android.shaftschematic.geom.undercutReferenceLabel
import com.android.shaftschematic.geom.undercutSetPositions
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Undercut
import com.android.shaftschematic.model.UndercutRecord
import com.android.shaftschematic.model.UndercutReference
import com.android.shaftschematic.ui.resolved.resolveComponents
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import com.android.shaftschematic.util.UnitSystem
import androidx.test.core.app.ApplicationProvider
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
    private val addedRefs = mutableListOf<UndercutReference>()
    private val updated = mutableListOf<String>()
    private var closed = 0
    private var previewed = 0

    private fun cut(id: String, start: Float, len: Float = 50f) = Undercut(
        id = id, startFromAftMm = start, lengthMm = len,
        authoredReference = UndercutReference.LINER_AFT, referenceLinerId = "L1",
    )

    /**
     * [withChrome] hosts the top bar's Preview and Options the way the Undercut tab does: Preview
     * counts into [previewed], Options carries the undercut preview's real options sheet.
     */
    private fun show(cuts: List<Undercut>, initialSelectedId: String? = null, withChrome: Boolean = false) {
        record = UndercutRecord(cuts)
        val resolved = resolveComponents(spec)
        val vm = if (withChrome) ShaftViewModel(ApplicationProvider.getApplicationContext<Application>()) else null
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
                    onUpdateUndercut = { id, _, _, _, _ -> updated += id },
                    onUpdateReference = { _, _, _, _ -> },
                    onRemoveUndercut = { id ->
                        removed += id
                        record = record.copy(undercuts = record.undercuts.filterNot { it.id == id })
                    },
                    onClose = { closed++ },
                    initialSelectedId = initialSelectedId,
                    onPreview = if (withChrome) ({ previewed++ }) else null,
                    optionsSheet = vm?.let { v ->
                        { UndercutOptionsSheet(vm = v, blankDraft = false, onSetBlankDraft = {}) }
                    },
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

    private fun settle() {
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
    }

    @Test
    fun `leaving an untouched new undercut card discards it`() {
        // The seeded default span nobody typed into: committing it on the way out recorded stray
        // cuts on the device. The pending page sits FWD of u1 (the seed butts on u1's FWD end),
        // so a swipe right leaves it for u1's card.
        show(listOf(cut("u1", 900f)))
        rule.onNodeWithTag("undercut_add_in_liner").performClick()
        settle()
        rule.onNodeWithText("New undercut").assertIsDisplayed()

        rule.onNodeWithTag("undercut_card_pager").performTouchInput { swipeRight() }
        settle()

        assertTrue("an untouched add was recorded at $added", added.isEmpty())
        assertEquals(listOf("u1"), record.undercuts.map { it.id })
        assertTrue(
            "the pending card must be gone",
            rule.onAllNodesWithText("New undercut").fetchSemanticsNodes().isEmpty(),
        )
        rule.onNodeWithTag("undercut_add_in_liner").assertIsEnabled()
    }

    @Test
    fun `an overlap names its partner on the pill, the card and the leave prompt`() {
        // u2 moved to 3 in from the liner's AFT edge (576.2–626.2 mm) runs half into u1
        // (600–700 mm), which is the FIRST card on this page.
        show(listOf(cut("u1", 600f, len = 100f), cut("u2", 900f)), initialSelectedId = "u2")
        val distance = hasText("Distance from Liner AFT (in)") and hasAnyAncestor(hasTestTag("undercut_card_u2"))
        rule.onNode(distance).performTextReplacement("3")
        rule.onNode(distance).performImeAction()
        settle()

        val msg = "Overlaps cut 1 — sit fully inside it or fully clear"
        rule.onNodeWithTag("undercut_status_pill", useUnmergedTree = true)
            .assert(hasAnyDescendant(hasText(msg)))
        assertTrue(
            "the card's inline reason must read the same",
            rule.onAllNodes(hasText(msg) and hasAnyAncestor(hasTestTag("undercut_card_u2")), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )

        rule.onNodeWithTag("undercut_card_pager").performTouchInput { swipeRight() }
        settle()
        rule.onNodeWithText("Undercut can't be saved").assertIsDisplayed()
        assertTrue(
            "the leave prompt must state the same reason",
            rule.onAllNodesWithText(msg, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(),
        )
        rule.onNodeWithTag("undercut_leave_discard").assertIsDisplayed()
        assertTrue("a blocked draft reached the record", updated.isEmpty())
    }

    @Test
    fun `start at end of and end at start of move the draft to either side of a cut`() {
        show(listOf(cut("u1", 600f, len = 100f), cut("u2", 900f)), initialSelectedId = "u2")
        rule.onNode(hasTestTag("undercut_butt_after_1") and hasAnyAncestor(hasTestTag("undercut_card_u2")))
            .performScrollTo().performClick()
        settle()
        // 700 mm = 200 mm past the liner's AFT edge = 7.874 in.
        assertTrue(
            rule.onAllNodes(
                editableStartsWith("7.87") and hasAnyAncestor(hasTestTag("undercut_card_u2")),
            ).fetchSemanticsNodes().isNotEmpty(),
        )
        rule.onNode(hasTestTag("undercut_butt_before_1") and hasAnyAncestor(hasTestTag("undercut_card_u2")))
            .performScrollTo().performClick()
        settle()
        // 600 − 50 = 550 mm = 50 mm past the liner's AFT edge = 1.969 in.
        assertTrue(
            rule.onAllNodes(
                editableStartsWith("1.96") and hasAnyAncestor(hasTestTag("undercut_card_u2")),
            ).fetchSemanticsNodes().isNotEmpty(),
        )
        assertTrue("a preset is a draft edit, not a record write", updated.isEmpty())
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

    // ── Buried liner edge: a cut run past the liner into the body takes the edge away ──

    /** Opens the strip over [stored] and drafts a new cut with "Add undercut in this liner". */
    private fun addInLinerOver(stored: Undercut) {
        show(listOf(stored))
        rule.onNodeWithTag("undercut_add_in_liner").performClick()
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
    }

    /** The S.E.T. the add flow's seed (butted on [stored]) sits nearer, from the overlay's own SETs. */
    private fun nearerSetForDefaultSpan(stored: Undercut): UndercutReference {
        val spans = listOf(UndercutSpanMm(stored.id, stored.startFromAftMm, stored.startFromAftMm + stored.lengthMm))
        val seed = requireNotNull(undercutAddSeedSpan(spans, spans, oal))
        val (aftSet, fwdSet) = undercutSetPositions(spec)
        return nearestSetReference(seed.startMm, seed.startMm + seed.lengthMm, aftSet, fwdSet)
    }

    @Test
    fun `a buried AFT edge seeds a new cut from the nearer SET and disables Liner AFT`() {
        val stored = cut("u1", 450f, len = 150f)   // 450–600: crosses the liner's AFT edge at 500
        addInLinerOver(stored)
        val expected = nearerSetForDefaultSpan(stored)

        rule.onNodeWithText("New undercut").assertIsDisplayed()
        assertTrue(
            "the new card's Distance must read from ${undercutReferenceLabel(expected)}",
            rule.onAllNodesWithText("Distance from ${undercutReferenceLabel(expected)} (in)")
                .fetchSemanticsNodes().isNotEmpty(),
        )
        val chips = rule.onAllNodesWithTag("undercut_ref_liner_aft")
        assertTrue(chips.fetchSemanticsNodes().isNotEmpty())
        repeat(chips.fetchSemanticsNodes().size) { chips[it].assertIsNotEnabled() }
        assertTrue(
            rule.onAllNodesWithTag("undercut_ref_liner_aft_buried", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
        assertTrue(
            rule.onAllNodesWithText(undercutBuriedEdgeCaption("AFT"), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )

        rule.onNodeWithTag("undercut_confirm", useUnmergedTree = true).performClick()
        rule.waitForIdle()
        assertEquals(listOf(expected), addedRefs)
    }

    @Test
    fun `a cut ending at the AFT edge leaves Liner AFT enabled and the seed`() {
        val stored = cut("u1", 500f, len = 100f)   // 500–600: ends at the edge, never crosses it
        addInLinerOver(stored)

        rule.onNodeWithText("New undercut").assertIsDisplayed()
        val chips = rule.onAllNodesWithTag("undercut_ref_liner_aft")
        assertTrue(chips.fetchSemanticsNodes().isNotEmpty())
        repeat(chips.fetchSemanticsNodes().size) { chips[it].assertIsEnabled() }
        assertTrue(
            rule.onAllNodesWithTag("undercut_ref_liner_aft_buried", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty(),
        )
        assertTrue(
            rule.onAllNodesWithText("Distance from Liner AFT (in)").fetchSemanticsNodes().isNotEmpty(),
        )

        rule.onNodeWithTag("undercut_confirm", useUnmergedTree = true).performClick()
        rule.waitForIdle()
        assertEquals(listOf(UndercutReference.LINER_AFT), addedRefs)
    }

    @Test
    fun `a Distance that carries the cut across its own liner edge re-reads from the SET`() {
        // 1350–1500 against the liner's FWD edge; −2 in puts its FWD end 50.8 mm past that edge
        // while the AFT end stays on the liner, so the cut now crosses the edge it measured from.
        show(
            listOf(
                Undercut(
                    id = "u1", startFromAftMm = 1350f, lengthMm = 150f,
                    authoredReference = UndercutReference.LINER_FWD, referenceLinerId = "L1",
                ),
            ),
        )
        rule.onNodeWithText("Distance from Liner FWD (in)").performTextReplacement("-2")
        rule.onNodeWithText("Distance from Liner FWD (in)").performImeAction()
        rule.waitForIdle()
        rule.onNodeWithTag("undercut_overhang_accept").performClick()
        rule.waitForIdle()

        val (aftSet, fwdSet) = undercutSetPositions(spec)
        val expected = nearestSetReference(1400.8f, 1550.8f, aftSet, fwdSet)
        rule.onNodeWithText("Distance from ${undercutReferenceLabel(expected)} (in)").assertExists()
        rule.onNodeWithTag("undercut_ref_liner_fwd").assertIsNotEnabled()
        rule.onNodeWithTag("undercut_ref_liner_fwd_buried", useUnmergedTree = true).assertExists()
        assertTrue("an unconfirmed draft reached the record", updated.isEmpty())
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

    // ── Parent-cut datum ──

    @Test
    fun `a cut inside another offers the Parent chips and measures from its shoulders`() {
        // Parent 600–800 mm, child 660–710 mm inside it.
        show(listOf(cut("p", 600f, len = 200f), cut("c", 660f)), initialSelectedId = "c")
        val inChild = hasAnyAncestor(hasTestTag("undercut_card_c"))
        rule.onNode(hasTestTag("undercut_ref_parent_aft") and inChild).assertExists()
        rule.onNode(hasTestTag("undercut_ref_parent_fwd") and inChild).assertExists()

        rule.onNode(hasTestTag("undercut_ref_parent_aft") and inChild).performScrollTo().performClick()
        settle()
        rule.onNode(hasText("Distance from Parent AFT (in)") and inChild).assertExists()
        // 660 − 600 = 60 mm = 2.362 in from the parent's AFT shoulder.
        assertTrue(rule.onAllNodes(editableStartsWith("2.362") and inChild).fetchSemanticsNodes().isNotEmpty())

        rule.onNode(hasTestTag("undercut_ref_parent_fwd") and inChild).performScrollTo().performClick()
        settle()
        rule.onNode(hasText("Distance from Parent FWD (in)") and inChild).assertExists()
        // 800 − 710 = 90 mm = 3.543 in from the parent's FWD shoulder.
        assertTrue(rule.onAllNodes(editableStartsWith("3.543") and inChild).fetchSemanticsNodes().isNotEmpty())
        assertTrue("a reference switch is a draft edit, not a record write", updated.isEmpty())
    }

    @Test
    fun `a cut inside no other shows no Parent chips`() {
        show(listOf(cut("u1", 900f)))
        assertTrue(rule.onAllNodesWithTag("undercut_ref_parent_aft").fetchSemanticsNodes().isEmpty())
        assertTrue(rule.onAllNodesWithTag("undercut_ref_parent_fwd").fetchSemanticsNodes().isEmpty())
    }

    // ── The on-device job (fixture `undercut_polar_ranger_stbd.shaft`) ──
    // Its FWD liner carries five cuts, every Distance from the FWD S.E.T.: E straddles the liner's
    // AFT edge and ends where C (the relief parent) starts; D sits inside C; B butts on C's FWD
    // end; A crosses the liner's FWD edge. E is last in the record — it was added last.

    private val jobDoc by lazy {
        val raw = requireNotNull(javaClass.classLoader?.getResource("fixtures/undercut_polar_ranger_stbd.shaft"))
            .readText()
        ShaftDocCodec.decode(raw)
    }
    private val jobSpec get() = jobDoc.spec
    private val jobCuts get() = jobDoc.undercutRecord.undercuts
    private fun jobCut(prefix: String) = jobCuts.single { it.id.startsWith(prefix) }
    private val jobLiner get() = jobSpec.liners.single { it.id.startsWith("02d0b768") }
    private val jobLinerSpan get() = UndercutLinerSpan(
        jobLiner.id, jobLiner.startFromAftMm, jobLiner.startFromAftMm + jobLiner.lengthMm,
    )
    private val cutE get() = jobCut("de3bedb8")
    private val cutC get() = jobCut("ce3aa5f1")

    private val past = ArrayDeque<UndercutRecord>()
    private val future = ArrayDeque<UndercutRecord>()
    private var nextId = 0

    /** Every record write the ViewModel makes, with one undo history behind the top bar. */
    private fun write(next: UndercutRecord) {
        past.addLast(record)
        future.clear()
        record = next
    }

    /** The job's FWD-liner page over [cuts], with a host that writes back every callback. */
    private fun showJob(cuts: List<Undercut>, initialSelectedId: String? = null) {
        record = UndercutRecord(cuts)
        val spec = jobSpec
        val oalMm = spec.overallLengthMm
        val linerSpan = jobLinerSpan
        val resolved = resolveComponents(spec)
        rule.setContent {
            MaterialTheme {
                val spans = record.undercuts.map {
                    UndercutSpanMm(it.id, it.startFromAftMm, it.startFromAftMm + it.lengthMm)
                }
                UndercutWindowDetailOverlay(
                    strip = linerStripFor(linerSpan, spans, oalMm),
                    stripKey = "liner:${linerSpan.id}",
                    spec = spec,
                    resolvedComponents = resolved,
                    unit = UnitSystem.INCHES,
                    undercutRecord = record,
                    onAddUndercut = { start, len, ref, refLinerId, refCutId ->
                        added += start
                        addedRefs += ref
                        val u = Undercut(
                            id = "added-${++nextId}", startFromAftMm = start, lengthMm = len,
                            authoredReference = ref, referenceLinerId = refLinerId,
                            referenceCutId = refCutId,
                        )
                        write(record.copy(undercuts = record.undercuts + u))
                        u.id
                    },
                    onUpdateUndercut = { id, start, len, dia, note ->
                        updated += id
                        write(
                            record.copy(
                                undercuts = record.undercuts.map {
                                    if (it.id != id) it
                                    else it.copy(startFromAftMm = start, lengthMm = len, diaMm = dia, note = note)
                                },
                            ),
                        )
                    },
                    onUpdateReference = { id, ref, refLinerId, refCutId ->
                        write(
                            record.copy(
                                undercuts = record.undercuts.map {
                                    if (it.id != id) it
                                    else it.copy(
                                        authoredReference = ref, referenceLinerId = refLinerId,
                                        referenceCutId = refCutId,
                                    )
                                },
                            ),
                        )
                    },
                    onRemoveUndercut = { id ->
                        removed += id
                        write(record.copy(undercuts = record.undercuts.filterNot { it.id == id }))
                    },
                    onRestoreUndercut = { u, index ->
                        if (record.undercuts.none { it.id == u.id }) {
                            val list = record.undercuts.toMutableList()
                            if (index in 0..list.size) list.add(index, u) else list.add(u)
                            write(record.copy(undercuts = list))
                        }
                    },
                    canUndo = true,
                    canRedo = true,
                    onUndo = {
                        past.removeLastOrNull()?.let { prev ->
                            future.addLast(record)
                            record = prev
                        }
                    },
                    onRedo = {
                        future.removeLastOrNull()?.let { next ->
                            past.addLast(record)
                            record = next
                        }
                    },
                    onClose = {},
                    initialSelectedId = initialSelectedId,
                )
            }
        }
        settle()
    }

    private val pendingCard = hasAnyAncestor(hasTestTag("undercut_card_$PENDING_UNDERCUT_ID"))
    private fun onCard(id: String) = hasAnyAncestor(hasTestTag("undercut_card_$id"))
    private val pill get() = rule.onNodeWithTag("undercut_status_pill", useUnmergedTree = true)
    private fun assertPillSaved() = pill.assert(hasAnyDescendant(hasText("Saved")))
    private fun assertNoOverlapNamed(n: Int) = assertTrue(
        "\"Overlaps cut $n\" is on screen",
        rule.onAllNodesWithText("Overlaps cut $n", substring = true, useUnmergedTree = true)
            .fetchSemanticsNodes().isEmpty(),
    )
    private fun assertNoLeavePrompt() = assertTrue(
        "a leave prompt is up",
        rule.onAllNodesWithText("Undercut can't be saved").fetchSemanticsNodes().isEmpty(),
    )
    private fun swipe(left: Boolean) {
        rule.onNodeWithTag("undercut_card_pager").performTouchInput { if (left) swipeLeft() else swipeRight() }
        settle()
    }
    private fun typeAndCommit(field: SemanticsMatcher, text: String) {
        rule.onNode(field).performScrollTo().performTextReplacement(text)
        rule.onNode(field).performImeAction()
        settle()
    }

    /**
     * The add flow E came from: with A–D recorded, "Add undercut in this liner", the FWD S.E.T.
     * chip, "End at start of" C, Length 1.25 in (the FWD reference keeps the FWD end on C's AFT
     * shoulder, so the cut grows past the liner's AFT edge and the overhang question is accepted),
     * and a Ø typed into its field but NOT committed — the field still holds focus when the card
     * is confirmed or left.
     */
    private fun draftCutEWithDiaInFlight(dia: String) {
        val stored = jobCuts.filterNot { it.id == cutE.id }
        showJob(stored)
        rule.onNodeWithTag("undercut_add_in_liner").performClick()
        settle()
        rule.onNode(hasText("FWD S.E.T.") and pendingCard).performScrollTo().performClick()
        settle()
        // "End at start of" numbers C by its page position, the pending card's seed included.
        val cPosition = buildUndercutPageIds(stored.sortedBy { it.startFromAftMm }, jobSeedStartMm(stored))
            .indexOf(cutC.id) + 1
        rule.onNode(hasTestTag("undercut_butt_before_$cPosition") and pendingCard).performScrollTo().performClick()
        settle()
        typeAndCommit(hasText("Length (in)") and pendingCard, "1.25")
        if (rule.onAllNodesWithTag("undercut_overhang_accept").fetchSemanticsNodes().isNotEmpty()) {
            rule.onNodeWithTag("undercut_overhang_accept").performClick()
            settle()
        }
        rule.onNode(hasText("Measured Ø (in)") and pendingCard).performScrollTo().performTextReplacement(dia)
        rule.waitForIdle()
    }

    private fun recordedCutE(): Undercut = record.undercuts.single { it.id.startsWith("added-") }

    /** Where "Add undercut in this liner" seeds over [stored]: butted on the strip's cuts. */
    private fun jobSeedStartMm(stored: List<Undercut>): Float {
        val spans = stored.map { UndercutSpanMm(it.id, it.startFromAftMm, it.startFromAftMm + it.lengthMm) }
        return requireNotNull(undercutAddSeedSpan(spans, spans, jobSpec.overallLengthMm)).startMm
    }

    /** Card 1 is the recorded E, an edit to it is clear, and nothing names cut 1 or prompts. */
    private fun assertCardOneIsCleanAndEditable(eId: String) {
        rule.onNode(hasText(undercutCardTitle(1, 5)) and onCard(eId)).assertExists()
        assertNoLeavePrompt()
        assertPillSaved()
        assertNoOverlapNamed(1)
        typeAndCommit(hasText("Measured Ø (in)") and onCard(eId), "9.25")
        assertNoOverlapNamed(1)
        rule.onNodeWithTag("undercut_confirm", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `the recorded job opens clean on every card`() {
        showJob(jobCuts, initialSelectedId = cutE.id)
        rule.onNode(hasText(undercutCardTitle(1, 5)) and onCard(cutE.id)).assertExists()
        assertPillSaved()
        repeat(4) {
            swipe(left = true)
            assertPillSaved()
            assertNoLeavePrompt()
        }
        assertNoOverlapNamed(1)
        rule.onNodeWithTag("undercut_add_in_liner").assertIsEnabled()
    }

    @Test
    fun `confirming a new cut with a value still in its field leaves no ghost draft behind`() {
        draftCutEWithDiaInFlight("9.5")
        rule.onNodeWithTag("undercut_confirm", useUnmergedTree = true).performClick()
        settle()

        val e = recordedCutE()
        assertEquals(cutE.startFromAftMm, e.startFromAftMm, 0.01f)
        assertEquals(cutE.lengthMm, e.lengthMm, 0.01f)
        // The Ø was in the box when Confirm was tapped: it is part of what was confirmed.
        assertEquals(9.5f * 25.4f, e.diaMm, 0.01f)
        // No pending draft survives the record: the add entry point is live again.
        rule.onNodeWithTag("undercut_add_in_liner").assertIsEnabled()

        swipe(left = true)
        assertNoLeavePrompt()
        swipe(left = false)
        assertCardOneIsCleanAndEditable(e.id)
    }

    @Test
    fun `swiping off a new cut with a value still in its field records it and leaves no ghost`() {
        draftCutEWithDiaInFlight("9.5")
        // E's seed butts on the FWD end of the strip's FWD-most cut, so the pending card is the
        // last page: swiping right leaves it.
        val stored = jobCuts.filterNot { it.id == cutE.id }
        assertEquals(
            stored.size,
            buildUndercutPageIds(stored.sortedBy { it.startFromAftMm }, jobSeedStartMm(stored))
                .indexOf(PENDING_UNDERCUT_ID),
        )
        swipe(left = false)
        assertNoLeavePrompt()

        val e = recordedCutE()
        assertEquals(cutE.lengthMm, e.lengthMm, 0.01f)
        assertEquals(9.5f * 25.4f, e.diaMm, 0.01f)
        rule.onNodeWithTag("undercut_add_in_liner").assertIsEnabled()

        repeat(4) {
            swipe(left = true)
            assertNoLeavePrompt()
        }
        repeat(5) {
            swipe(left = false)
            assertNoLeavePrompt()
        }
        assertCardOneIsCleanAndEditable(e.id)
    }

    @Test
    fun `an undo and redo of the add leave card 1 clean and never self-overlapping`() {
        draftCutEWithDiaInFlight("9.5")
        rule.onNodeWithTag("undercut_confirm", useUnmergedTree = true).performClick()
        settle()
        val eId = recordedCutE().id

        rule.onNodeWithContentDescription("Undo").performClick()
        settle()
        rule.onNodeWithContentDescription("Redo").performClick()
        settle()
        assertNoLeavePrompt()
        rule.onNodeWithTag("undercut_add_in_liner").assertIsEnabled()
        repeat(4) { swipe(left = false) }
        assertCardOneIsCleanAndEditable(eId)
    }

    @Test
    fun `a delete and its snackbar undo leave card 1 clean and never self-overlapping`() {
        showJob(jobCuts, initialSelectedId = cutE.id)
        rule.onNodeWithContentDescription("Delete ${undercutCardTitle(1, 5)}").performClick()
        settle()
        rule.onNodeWithText("Undo").performClick()
        settle()
        assertEquals(5, record.undercuts.size)
        assertCardOneIsCleanAndEditable(cutE.id)
    }

    @Test
    fun `discarding a new cut with a value still in its field leaves no ghost draft behind`() {
        // Discard flushes nothing: the field's blur lands after the pending card is gone, from a
        // card with no page, and must not resurrect the add flow's draft.
        draftCutEWithDiaInFlight("9.5")
        rule.onNodeWithContentDescription("Discard change").performClick()
        settle()
        assertTrue("a discarded add was recorded at $added", added.isEmpty())
        rule.onNodeWithTag("undercut_add_in_liner").assertIsEnabled()
        repeat(4) {
            swipe(left = true)
            assertNoLeavePrompt()
        }
        assertNoOverlapNamed(1)
    }

    // ── Confirm re-checks the draft after the flush lands a typed value ──

    /** u2 dirty and confirmable (a committed Ø), then [field] typed to [value] but NOT committed. */
    private fun confirmWithInFlight(field: String, value: String) {
        show(listOf(cut("u1", 600f, len = 100f), cut("u2", 900f)), initialSelectedId = "u2")
        val inU2 = hasAnyAncestor(hasTestTag("undercut_card_u2"))
        rule.onNode(hasText("Measured Ø (in)") and inU2).performScrollTo().performTextReplacement("7")
        rule.onNode(hasText("Measured Ø (in)") and inU2).performImeAction()
        settle()
        rule.onNodeWithTag("undercut_confirm", useUnmergedTree = true).assertExists()
        rule.onNode(hasText(field) and inU2).performScrollTo().performTextReplacement(value)
        rule.waitForIdle()
        rule.onNodeWithTag("undercut_confirm", useUnmergedTree = true).performClick()
        settle()
    }

    private fun assertBlockedWith(reason: String) {
        rule.onNodeWithTag("undercut_status_pill", useUnmergedTree = true)
            .assert(hasAnyDescendant(hasText(reason, substring = true)))
        assertTrue("an invalid draft reached the record: $updated", updated.isEmpty())
    }

    @Test
    fun `confirm with an in-flight overlapping Distance records nothing and blocks`() {
        confirmWithInFlight("Distance from Liner AFT (in)", "3")
        assertBlockedWith("Overlaps cut 1")
    }

    @Test
    fun `confirm with an in-flight Distance past the shaft end records nothing and blocks`() {
        confirmWithInFlight("Distance from Liner AFT (in)", "60")
        assertBlockedWith("past the shaft")
    }

    @Test
    fun `confirm with an in-flight zero length records nothing and blocks`() {
        confirmWithInFlight("Length (in)", "0")
        assertBlockedWith("Length must be greater than zero")
    }

    // ── A flush blurs only the card being left ──

    @Test
    fun `moving to the card that holds focus keeps its field focused`() {
        show(listOf(cut("u1", 600f, len = 100f), cut("u2", 900f)), initialSelectedId = "u1")
        val length = hasText("Length (in)") and hasAnyAncestor(hasTestTag("undercut_card_u2"))
        rule.onNode(length).performSemanticsAction(SemanticsActions.RequestFocus)
        settle()
        rule.onNodeWithTag("undercut_card_pager").performTouchInput { swipeLeft() }
        settle()
        rule.onNode(length).assertIsFocused()
    }

    // ── "Past the liner edge" for an overhang the flush lands ──

    /** The add flow's card with a 40 in Length typed but not committed: its FWD end runs off the liner. */
    private fun pendingWithInFlightOverhang() {
        show(listOf(cut("u1", 600f)))
        rule.onNodeWithTag("undercut_add_in_liner").performClick()
        settle()
        val length = hasText("Length (in)") and
            hasAnyAncestor(hasTestTag("undercut_card_$PENDING_UNDERCUT_ID"))
        rule.onNode(length).performScrollTo().performTextReplacement("40")
        rule.waitForIdle()
    }

    private fun assertRecordedAsTyped() {
        val cutNew = record.undercuts.single { it.id == "new" }
        assertEquals(40f * 25.4f, cutNew.lengthMm, 0.01f)
    }

    @Test
    fun `confirming a new cut with an in-flight overhang asks first, and Add extension records it`() {
        pendingWithInFlightOverhang()
        rule.onNodeWithTag("undercut_confirm", useUnmergedTree = true).performClick()
        settle()
        rule.onNodeWithTag("undercut_overhang_dialog").assertIsDisplayed()
        assertTrue("recorded before the answer", added.isEmpty())
        rule.onNodeWithTag("undercut_overhang_accept").performClick()
        settle()
        assertRecordedAsTyped()
    }

    @Test
    fun `confirming a new cut with an in-flight overhang asks first, and No records it too`() {
        pendingWithInFlightOverhang()
        rule.onNodeWithTag("undercut_confirm", useUnmergedTree = true).performClick()
        settle()
        rule.onNodeWithTag("undercut_overhang_dialog").assertIsDisplayed()
        rule.onNodeWithTag("undercut_overhang_decline").performClick()
        settle()
        assertRecordedAsTyped()
        rule.onNodeWithTag("undercut_overhang_dialog").assertDoesNotExist()
    }

    @Test
    fun `closing with an in-flight overhang asks first, then records and closes`() {
        pendingWithInFlightOverhang()
        rule.onNodeWithContentDescription("Back to Undercut Drawing").performClick()
        settle()
        rule.onNodeWithTag("undercut_overhang_dialog").assertIsDisplayed()
        assertEquals("closed before the answer", 0, closed)
        rule.onNodeWithTag("undercut_overhang_accept").performClick()
        settle()
        assertRecordedAsTyped()
        assertEquals(1, closed)
    }

    // ── A refused late write says so ──

    @Test
    fun `an undo that takes away a card with a typed value still in its field says it was discarded`() {
        show(listOf(cut("u1", 900f)))
        rule.onNodeWithText("Length (in)").performTextReplacement("3")
        rule.waitForIdle()

        record = record.copy(undercuts = emptyList())
        settle()

        rule.onNodeWithText(UNDERCUT_DISCARDED_DRAFT_MSG).assertIsDisplayed()
        assertTrue("an undone card's value reached the record", updated.isEmpty())
    }

    // ── "Next cut" ──

    private fun nextCutOn(id: String) = hasTestTag("undercut_next_cut") and onCard(id)

    private fun editableIs(text: String) = SemanticsMatcher("editable text is $text") {
        it.config.getOrNull(SemanticsProperties.EditableText)?.text == text
    }

    @Test
    fun `next cut records the card and opens a new cut butted on its FWD end`() {
        show(listOf(cut("u1", 600f), cut("u2", 900f)), initialSelectedId = "u1")
        typeAndCommit(hasText("Measured Ø (in)") and onCard("u1"), "7")
        rule.onNode(nextCutOn("u1")).performScrollTo().assertIsEnabled().performClick()
        settle()

        // The current draft went through Confirm.
        assertEquals(listOf("u1"), updated)
        assertTrue("the seed is a draft, not a record write", added.isEmpty())
        // The new card is the one in front, starting on u1's FWD end: 650 mm = 150 mm past the
        // liner's AFT edge = 5.9055 in, under u1's own Liner AFT datum, Ø empty.
        rule.onNodeWithText("New undercut").assertIsDisplayed()
        rule.onNode(hasText("Distance from Liner AFT (in)") and pendingCard).assertExists()
        assertTrue(rule.onAllNodes(editableStartsWith("5.905") and pendingCard).fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.onAllNodes(editableIs("1") and pendingCard).fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.onAllNodes(editableStartsWith("7") and pendingCard).fetchSemanticsNodes().isEmpty())
        rule.onNodeWithTag("undercut_status_pill", useUnmergedTree = true)
            .assert(hasAnyDescendant(hasText("Confirm change")))
    }

    @Test
    fun `next cut on a new cut records it and opens the cut after it`() {
        show(listOf(cut("u1", 900f)))
        rule.onNodeWithTag("undercut_add_in_liner").performClick()
        settle()
        typeAndCommit(hasText("Measured Ø (in)") and pendingCard, "6")
        rule.onNode(nextCutOn(PENDING_UNDERCUT_ID)).performScrollTo().performClick()
        settle()

        // The first add (butted on u1 at 950 mm) is recorded; the next opens at 975.4 mm.
        assertEquals(1, added.size)
        assertEquals(950f, added.single(), 0.01f)
        rule.onNodeWithText("New undercut").assertIsDisplayed()
        // 975.4 − 500 = 475.4 mm = 18.7165 in from the liner's AFT edge.
        assertTrue(rule.onAllNodes(editableStartsWith("18.71") and pendingCard).fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.onAllNodes(editableStartsWith("6") and pendingCard).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `next cut is disabled while the card's draft cannot be confirmed`() {
        show(listOf(cut("u1", 600f, len = 100f), cut("u2", 900f)), initialSelectedId = "u2")
        typeAndCommit(hasText("Distance from Liner AFT (in)") and onCard("u2"), "3")
        rule.onNode(nextCutOn("u2")).assertIsNotEnabled()
        assertTrue(
            "a blocked draft explains itself on the pill, not with a stock caption",
            rule.onAllNodesWithTag("undercut_next_cut_caption", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty(),
        )
        assertTrue("a blocked draft reached the record", updated.isEmpty())
    }

    @Test
    fun `next cut is disabled with a caption when no stock remains FWD`() {
        // 1400–1990 mm on the 2000 mm shaft: a 1 in cut after it would leave the shaft.
        show(listOf(cut("u1", 1400f, len = 590f)))
        rule.onNode(nextCutOn("u1")).assertIsNotEnabled()
        rule.onNode(
            hasText(UNDERCUT_NO_STOCK_FWD_MSG) and onCard("u1"),
            useUnmergedTree = true,
        ).assertExists()
    }

    // ── Preview and Options from the top bar (on-device request: both within reach) ──

    @Test
    fun `the top bar carries Preview and Drawing options`() {
        show(listOf(cut("u1", 600f)), withChrome = true)
        rule.onNodeWithTag("undercut_detail_preview").assertIsDisplayed()
        rule.onNodeWithContentDescription("Preview").assertIsDisplayed()
        rule.onNodeWithTag("undercut_detail_options").assertIsDisplayed()
        rule.onNodeWithContentDescription("Drawing options").assertIsDisplayed()
    }

    @Test
    fun `without a host the top bar shows neither`() {
        show(listOf(cut("u1", 600f)))
        rule.onNodeWithTag("undercut_detail_preview").assertDoesNotExist()
        rule.onNodeWithTag("undercut_detail_options").assertDoesNotExist()
    }

    @Test
    fun `Options opens the undercut options panel over the page, and the icon closes it`() {
        show(listOf(cut("u1", 600f)), withChrome = true)
        rule.onNodeWithTag("preview_options_panel").assertDoesNotExist()

        rule.onNodeWithTag("undercut_detail_options").performClick()
        settle()
        rule.onNodeWithTag("preview_options_panel").assertIsDisplayed()
        // Over the detail page, not instead of it: its top bar is still there and live.
        rule.onNodeWithTag("undercut_detail_undo").assertIsDisplayed()
        // The undercut sheet's own rows: the liner outline in Shading, the arrow sizes in Lines.
        rule.onNodeWithTag("options_shade_expander").performScrollTo().performClick()
        rule.onNodeWithTag("pdf_undercut_liner_outline").assertExists()
        rule.onNodeWithTag("options_lines_expander").performScrollTo().performClick()
        rule.onNodeWithText("Dimension arrows").assertExists()

        rule.onNodeWithTag("undercut_detail_options").performClick()
        settle()
        rule.onNodeWithTag("preview_options_panel").assertDoesNotExist()
        assertEquals("the panel is not a way out of the page", 0, closed)
    }

    @Test
    fun `the panel's own close button closes it`() {
        show(listOf(cut("u1", 600f)), withChrome = true)
        rule.onNodeWithTag("undercut_detail_options").performClick()
        settle()
        rule.onNodeWithTag("preview_options_close").performClick()
        settle()
        rule.onNodeWithTag("preview_options_panel").assertDoesNotExist()
    }

    @Test
    fun `Preview asks the host for the preview and leaves the page open`() {
        show(listOf(cut("u1", 600f)), withChrome = true)
        rule.onNodeWithTag("undercut_detail_preview").performClick()
        settle()
        assertEquals(1, previewed)
        assertEquals("the detail page stays open beneath the preview", 0, closed)
        rule.onNodeWithTag("undercut_detail_undo").assertIsDisplayed()
    }

    @Test
    fun `Preview saves a typed edit first, the way leaving does`() {
        show(listOf(cut("u1", 600f, len = 100f), cut("u2", 900f)), initialSelectedId = "u2", withChrome = true)
        val distance = hasText("Distance from Liner AFT (in)") and hasAnyAncestor(hasTestTag("undercut_card_u2"))
        rule.onNode(distance).performTextReplacement("12")
        rule.onNode(distance).performImeAction()
        settle()
        assertTrue("nothing recorded before the preview", updated.isEmpty())

        rule.onNodeWithTag("undercut_detail_preview").performClick()
        settle()
        assertEquals(listOf("u2"), updated)
        assertEquals(1, previewed)
    }

    @Test
    fun `Preview with a blocked draft asks Keep editing or Discard first`() {
        show(listOf(cut("u1", 600f, len = 100f), cut("u2", 900f)), initialSelectedId = "u2", withChrome = true)
        val distance = hasText("Distance from Liner AFT (in)") and hasAnyAncestor(hasTestTag("undercut_card_u2"))
        rule.onNode(distance).performTextReplacement("3")
        rule.onNode(distance).performImeAction()
        settle()

        rule.onNodeWithTag("undercut_detail_preview").performClick()
        settle()
        rule.onNodeWithText("Undercut can't be saved").assertIsDisplayed()
        assertEquals("no preview while the question is open", 0, previewed)

        rule.onNodeWithTag("undercut_leave_keep").performClick()
        settle()
        assertEquals("Keep editing stays on the page", 0, previewed)

        rule.onNodeWithTag("undercut_detail_preview").performClick()
        settle()
        rule.onNodeWithTag("undercut_leave_discard").performClick()
        settle()
        assertEquals("Discard goes on to the preview", 1, previewed)
        assertEquals(0, closed)
        assertTrue("a blocked draft reached the record", updated.isEmpty())
    }
}
