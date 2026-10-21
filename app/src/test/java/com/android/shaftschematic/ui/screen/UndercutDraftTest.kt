package com.android.shaftschematic.ui.screen

import com.android.shaftschematic.geom.UNDERCUT_PARTIAL_OVERLAP_MSG
import com.android.shaftschematic.geom.UndercutLinerSpan
import com.android.shaftschematic.geom.UndercutSpanMm
import com.android.shaftschematic.geom.undercutGapAfterMm
import com.android.shaftschematic.geom.undercutGapBeforeMm
import com.android.shaftschematic.model.Undercut
import com.android.shaftschematic.model.UndercutReference
import com.android.shaftschematic.util.UnitSystem
import com.android.shaftschematic.util.toMmOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the undercut carousel's draft/ordering rules (`UndercutDetail.kt`): page order aft → fwd
 * off STORED values (cards move only on confirm), draft substitution for the canvas preview, the
 * default span a new draft opens at, the confirm-time blocking check, and the leave decision that
 * auto-saves a card on the way out (or asks, when the draft is blocked).
 */
class UndercutDraftTest {

    private fun cut(id: String, startMm: Float, lengthMm: Float = 25f, diaMm: Float = 0f) =
        Undercut(id = id, startFromAftMm = startMm, lengthMm = lengthMm, diaMm = diaMm)

    private fun draftOf(
        id: String,
        startMm: Float,
        lengthMm: Float = 25f,
        diaMm: Float = 0f,
        note: String = "",
        reference: UndercutReference = UndercutReference.AFT_SET,
        referenceLinerId: String = "",
    ) = UndercutDraft(id, startMm, lengthMm, diaMm, note, reference, referenceLinerId)

    // ── Page order ───────────────────────────────────────────────────────────

    @Test
    fun `pages run aft to fwd by stored start`() {
        val stored = listOf(cut("c", 400f), cut("a", 100f), cut("b", 250f))
        assertEquals(listOf("a", "b", "c"), buildUndercutPageIds(stored, pendingSeedStartMm = null))
    }

    @Test
    fun `a pending add draft takes its seed position among the stored cards`() {
        val stored = listOf(cut("a", 100f), cut("b", 400f))
        assertEquals(
            listOf("a", PENDING_UNDERCUT_ID, "b"),
            buildUndercutPageIds(stored, pendingSeedStartMm = 250f),
        )
        assertEquals(
            listOf(PENDING_UNDERCUT_ID, "a", "b"),
            buildUndercutPageIds(stored, pendingSeedStartMm = 10f),
        )
    }

    @Test
    fun `an edited draft does not reorder its card until it is confirmed`() {
        // Ordering reads stored starts only, so typing a new Distance can't slide the card out
        // from under the field being typed in.
        val stored = listOf(cut("a", 100f), cut("b", 400f))
        val order = buildUndercutPageIds(stored, pendingSeedStartMm = null)
        val confirmed = listOf(cut("a", 900f), cut("b", 400f))
        assertEquals(listOf("a", "b"), order)
        assertEquals(listOf("b", "a"), buildUndercutPageIds(confirmed, pendingSeedStartMm = null))
    }

    // ── Draft substitution for the preview ───────────────────────────────────

    @Test
    fun `a draft replaces its stored cut in the drawn list and re-sorts`() {
        val stored = listOf(cut("a", 100f), cut("b", 400f))
        val drawn = applyUndercutDraft(stored, draftOf("a", startMm = 900f, diaMm = 180f))
        assertEquals(listOf("b", "a"), drawn.map { it.id })
        assertEquals(900f, drawn.first { it.id == "a" }.startFromAftMm, 1e-4f)
        assertEquals(180f, drawn.first { it.id == "a" }.diaMm, 1e-4f)
    }

    @Test
    fun `a pending draft is appended so the canvas previews a cut the record has not got`() {
        val stored = listOf(cut("a", 100f), cut("b", 400f))
        val drawn = applyUndercutDraft(stored, draftOf(PENDING_UNDERCUT_ID, startMm = 250f))
        assertEquals(listOf("a", PENDING_UNDERCUT_ID, "b"), drawn.map { it.id })
    }

    @Test
    fun `no draft leaves the list untouched`() {
        val stored = listOf(cut("a", 100f))
        assertTrue(applyUndercutDraft(stored, null) === stored)
    }

    // ── Baseline / dirty ─────────────────────────────────────────────────────

    @Test
    fun `an untouched card is not dirty and a liner reference whose liner is gone stays stored`() {
        val liners = listOf(UndercutLinerSpan("liner-1", 200f, 600f))
        val u = cut("a", 300f).copy(
            authoredReference = UndercutReference.LINER_AFT,
            referenceLinerId = "deleted-liner",
        )
        val baseline = undercutDraftOf(u, liners)
        // Displays its AFT_SET fallback, and equals itself — so nothing is dirty and Confirm
        // never rewrites the stored LINER_AFT reference behind the machinist's back.
        assertEquals(UndercutReference.AFT_SET, baseline.reference)
        assertEquals("", baseline.referenceLinerId)
        assertEquals(baseline, undercutDraftOf(u, liners))
    }

    @Test
    fun `a resolving liner reference is carried into the draft`() {
        val liners = listOf(UndercutLinerSpan("liner-1", 200f, 600f))
        val u = cut("a", 300f).copy(
            authoredReference = UndercutReference.LINER_FWD,
            referenceLinerId = "liner-1",
        )
        val baseline = undercutDraftOf(u, liners)
        assertEquals(UndercutReference.LINER_FWD, baseline.reference)
        assertEquals("liner-1", baseline.referenceLinerId)
    }

    // ── Default span for a new draft ─────────────────────────────────────────

    @Test
    fun `an empty liner drafts a centred default-length cut`() {
        val span = defaultUndercutSpan(200f, 600f, occupied = emptyList(), oalMm = 1000f)
        assertEquals(25.4f, span.lengthMm, 1e-3f)
        assertEquals(200f + (400f - 25.4f) / 2f, span.startMm, 1e-3f)
    }

    @Test
    fun `a new draft takes the aft-most free gap rather than landing on a recorded cut`() {
        val occupied = listOf(UndercutSpanMm("a", 200f, 500f))
        val span = defaultUndercutSpan(200f, 600f, occupied, oalMm = 1000f)
        assertTrue("draft must start fwd of the recorded cut", span.startMm >= 500f)
        assertTrue(span.startMm + span.lengthMm <= 600f + 1e-3f)
        assertNull(
            "a fresh draft must not open already blocked",
            undercutConfirmIssue(
                draftOf("x", span.startMm, span.lengthMm), oalMm = 1000f, otherSpans = occupied,
            ),
        )
    }

    @Test
    fun `a gap narrower than the default length shortens the draft to fit`() {
        val occupied = listOf(UndercutSpanMm("a", 210f, 590f))
        val span = defaultUndercutSpan(200f, 600f, occupied, oalMm = 1000f)
        assertEquals(10f, span.lengthMm, 1e-3f)
    }

    @Test
    fun `a fully occupied range still yields a usable draft`() {
        val occupied = listOf(UndercutSpanMm("a", 200f, 600f))
        val span = defaultUndercutSpan(200f, 600f, occupied, oalMm = 1000f)
        assertEquals(25.4f, span.lengthMm, 1e-3f)
        assertEquals(200f, span.startMm, 1e-3f)
    }

    // ── Confirm gate ─────────────────────────────────────────────────────────

    @Test
    fun `confirm blocks a span off the shaft and a span intruding on a neighbour`() {
        val others = listOf(UndercutSpanMm("a", 200f, 300f))
        assertNotNull(undercutConfirmIssue(draftOf("x", 980f, 50f), 1000f, emptyList()))
        assertNotNull(undercutConfirmIssue(draftOf("x", 250f, 100f), 1000f, others))
        // Edge-to-edge is legal — two cuts may be machined back to back.
        assertNull(undercutConfirmIssue(draftOf("x", 300f, 100f), 1000f, others))
    }

    @Test
    fun `confirm is the one gate on every span problem the fields no longer refuse`() {
        // The card's fields commit whatever parses, so each of these reaches the draft as typed —
        // and Confirm (and the leave prompt) must be what stops it, with the reason.
        assertEquals(
            "Length must be greater than zero",
            undercutConfirmIssue(draftOf("x", 100f, 0f), 1000f, emptyList()),
        )
        assertEquals(
            "Length must be greater than zero",
            undercutConfirmIssue(draftOf("x", 100f, -20f), 1000f, emptyList()),
        )
        assertEquals(
            "Start is before the shaft's AFT end",
            undercutConfirmIssue(draftOf("x", -5f, 50f), 1000f, emptyList()),
        )
        assertEquals(
            "Undercut extends past the shaft's FWD end",
            undercutConfirmIssue(draftOf("x", 980f, 50f), 1000f, emptyList()),
        )
        val stored = draftOf("x", 900f, 50f)
        assertEquals(
            UndercutLeaveAction.PROMPT,
            undercutLeaveAction(
                draftOf("x", 980f, 50f), stored,
                undercutConfirmIssue(draftOf("x", 980f, 50f), 1000f, emptyList()),
            ),
        )
    }

    @Test
    fun `confirm passes a cut drafted fully inside another and still blocks a partial overlap`() {
        val others = listOf(UndercutSpanMm("relief", 200f, 400f))
        // A smaller, deeper cut inside the relief — the nested case, now legal.
        assertNull(undercutConfirmIssue(draftOf("x", 250f, 100f), 1000f, others))
        // …a draft machined AROUND the relief, and one running right up to its AFT shoulder
        // (the shop's case: deepening a corroded section that reaches the relief's boundary).
        assertNull(undercutConfirmIssue(draftOf("x", 150f, 300f), 1000f, others))
        assertNull(undercutConfirmIssue(draftOf("x", 200f, 100f), 1000f, others))
        // Partial intrusion stays blocked, as does a span identical to the relief's.
        assertNotNull(undercutConfirmIssue(draftOf("x", 350f, 100f), 1000f, others))
        assertNotNull(undercutConfirmIssue(draftOf("x", 200f, 200f), 1000f, others))
    }

    @Test
    fun `the adjacency pool excludes the draft's own cut and any degenerate span`() {
        val sheet = listOf(cut("a", 100f), cut("b", 400f), cut("c", 700f, lengthMm = 0f))
        val pool = undercutOtherSpans(sheet, excludeId = "a", oalMm = 1000f)
        assertEquals(listOf("b"), pool.map { it.id })
    }

    // ── Whole liner / Extend past liner ──────────────────────────────────────

    @Test
    fun `whole liner sets the draft to the liner span authored from Liner AFT`() {
        val liner = UndercutLinerSpan("L1", 600f, 1000f)
        val draft = draftOf("x", 700f, 50f, diaMm = 180f, note = "weld").withWholeLiner(liner)
        assertEquals(600f, draft.startFromAftMm, 1e-4f)
        assertEquals(400f, draft.lengthMm, 1e-4f)
        assertEquals(UndercutReference.LINER_AFT, draft.reference)
        assertEquals("L1", draft.referenceLinerId)
        // The measurement and the note ride along untouched.
        assertEquals(180f, draft.diaMm, 1e-4f)
        assertEquals("weld", draft.note)
    }

    @Test
    fun `a typed 2 inch AFT extension on a whole-liner draft runs into the body and pins the FWD end`() {
        val liner = UndercutLinerSpan("L1", 600f, 1000f)
        val whole = draftOf("x", 700f, 50f).withWholeLiner(liner)
        val extMm = toMmOrNull("2", UnitSystem.INCHES)!!
        val edit = whole.withAftLinerExtension(liner, extMm, oalMm = 1900f)
        val extended = edit.draft
        assertEquals(600f - 50.8f, extended.startFromAftMm, 1e-3f)
        assertEquals(1000f, extended.startFromAftMm + extended.lengthMm, 1e-3f)
        // An extension is a view of the span, never a datum: the reference stays put.
        assertEquals(UndercutReference.LINER_AFT, extended.reference)
        assertEquals("L1", extended.referenceLinerId)
        assertEquals(50.8f, edit.appliedMm, 1e-3f)
        assertFalse(edit.limited)
    }

    @Test
    fun `a FWD extension pins the AFT end`() {
        val liner = UndercutLinerSpan("L1", 600f, 1000f)
        val edit = draftOf("x", 700f, 50f).withFwdLinerExtension(liner, 25.4f, oalMm = 1900f)
        assertEquals(700f, edit.draft.startFromAftMm, 1e-4f)
        assertEquals(1025.4f, edit.draft.startFromAftMm + edit.draft.lengthMm, 1e-3f)
        assertFalse(edit.limited)
    }

    @Test
    fun `an AFT extension past the shaft's AFT end applies the stock that is there`() {
        // Liner 50 mm from the AFT end; 100 mm typed — only 50 mm of body to cut into.
        val liner = UndercutLinerSpan("L1", 50f, 450f)
        val edit = draftOf("x", 50f, 400f).withAftLinerExtension(liner, 100f, oalMm = 1000f)
        assertEquals(0f, edit.draft.startFromAftMm, 1e-4f)
        assertEquals(450f, edit.draft.startFromAftMm + edit.draft.lengthMm, 1e-4f)
        assertEquals(50f, edit.appliedMm, 1e-4f)
        assertTrue(edit.limited)
    }

    @Test
    fun `a FWD extension past the shaft's FWD end applies the stock that is there`() {
        // Liner FWD edge 50 mm short of the FWD end; 100 mm typed.
        val liner = UndercutLinerSpan("L1", 550f, 950f)
        val edit = draftOf("x", 550f, 400f).withFwdLinerExtension(liner, 100f, oalMm = 1000f)
        assertEquals(550f, edit.draft.startFromAftMm, 1e-4f)
        assertEquals(1000f, edit.draft.startFromAftMm + edit.draft.lengthMm, 1e-4f)
        assertEquals(50f, edit.appliedMm, 1e-4f)
        assertTrue(edit.limited)
    }

    @Test
    fun `an extension inside the available stock is not limited`() {
        val liner = UndercutLinerSpan("L1", 50f, 950f)
        val aft = draftOf("x", 50f, 900f).withAftLinerExtension(liner, 30f, oalMm = 1000f)
        val fwd = draftOf("x", 50f, 900f).withFwdLinerExtension(liner, 30f, oalMm = 1000f)
        assertFalse(aft.limited)
        assertFalse(fwd.limited)
        assertEquals(20f, aft.draft.startFromAftMm, 1e-4f)
        assertEquals(980f, fwd.draft.startFromAftMm + fwd.draft.lengthMm, 1e-4f)
    }

    // ── Leaving a card ───────────────────────────────────────────────────────

    @Test
    fun `leaving an untouched card does nothing`() {
        val baseline = draftOf("a", 100f)
        assertEquals(
            UndercutLeaveAction.NONE,
            undercutLeaveAction(baseline, baseline, confirmIssue = null),
        )
        assertEquals(
            UndercutLeaveAction.NONE,
            undercutLeaveAction(draft = null, baseline = baseline, confirmIssue = null),
        )
    }

    @Test
    fun `leaving a clear dirty card commits it`() {
        assertEquals(
            UndercutLeaveAction.COMMIT,
            undercutLeaveAction(draftOf("a", 150f), draftOf("a", 100f), confirmIssue = null),
        )
    }

    @Test
    fun `leaving a blocked card asks instead of committing or dropping it`() {
        assertEquals(
            UndercutLeaveAction.PROMPT,
            undercutLeaveAction(
                draftOf("a", 150f), draftOf("a", 100f),
                confirmIssue = UNDERCUT_PARTIAL_OVERLAP_MSG,
            ),
        )
    }

    // ── Card title + edge-to-edge presets ────────────────────────────────────────────

    @Test
    fun `the card title names the cut the way the chips and list rows do`() {
        assertEquals("Cut 2 of 3", undercutCardTitle(2, 3))
        assertEquals(undercutButtChipLabel(2), undercutCardTitle(2, 3).substringBefore(" of"))
    }

    @Test
    fun `the past-the-liner-edge question names the end and the amount as a yes or no`() {
        assertEquals(
            "This cut runs 2.000 in past the liner's FWD edge into the shaft. Is that right?",
            undercutOverhangQuestion(aft = null, fwd = "2.000 in"),
        )
        assertEquals(
            "This cut runs 2.000 in past the liner's AFT edge into the shaft. Is that right?",
            undercutOverhangQuestion(aft = "2.000 in", fwd = null),
        )
        assertEquals(
            "This cut runs 1.000 in past the liner's AFT edge and 2.000 in past its FWD edge " +
                "into the shaft. Is that right?",
            undercutOverhangQuestion(aft = "1.000 in", fwd = "2.000 in"),
        )
        assertEquals(
            "Runs 2.000 in past the liner's FWD edge — check Distance and Length",
            undercutOverhangWarning("2.000 in", "FWD"),
        )
    }

    @Test
    fun `the Ø warning names the figure it was compared against`() {
        assertEquals(
            "Ø isn't smaller than the shaft here (Ø 10.000 in) — check the number",
            undercutDiaNotBelowSurfaceWarning(nested = false, dia = "10.000", unitAbbr = "in"),
        )
        assertEquals(
            "Ø isn't smaller than the surrounding cut's floor here (Ø 9.500 in) — check the number",
            undercutDiaNotBelowSurfaceWarning(nested = true, dia = "9.500", unitAbbr = "in"),
        )
    }

    @Test
    fun `liner end flags combine and clear per end`() {
        val aft = UndercutLinerEnds(aft = true, fwd = false)
        val fwd = UndercutLinerEnds(aft = false, fwd = true)
        assertEquals(UndercutLinerEnds(aft = true, fwd = true), aft.with(fwd))
        assertEquals(fwd, aft.with(fwd).without(aft))
        assertEquals(UndercutLinerEnds.NONE, fwd.without(fwd))
    }

    @Test
    fun `a refused preset says it changed nothing`() {
        assertEquals(
            "Not applied — Undercut extends past the shaft's FWD end",
            undercutPresetRefusal("Undercut extends past the shaft's FWD end"),
        )
    }

    @Test
    fun `start at end of a cut puts the draft after it, length and reference untouched`() {
        val draft = draftOf(
            "d", 300f, lengthMm = 40f,
            reference = UndercutReference.LINER_FWD, referenceLinerId = "L1",
        )
        val moved = draft.startingAtEndOf(UndercutSpanMm("o", 100f, 150f))
        assertEquals(draft.copy(startFromAftMm = 150f), moved)
    }

    @Test
    fun `end at start of a cut puts the draft before it, length and reference untouched`() {
        val draft = draftOf("d", 20f, lengthMm = 40f, reference = UndercutReference.FWD_SET)
        val moved = draft.endingAtStartOf(UndercutSpanMm("o", 100f, 150f))
        assertEquals(draft.copy(startFromAftMm = 60f), moved)
    }

    @Test
    fun `a gap moves the draft that far past the other cut, and the gap view reads it back`() {
        val other = UndercutSpanMm("o", 100f, 150f)
        val draft = draftOf("d", 300f, lengthMm = 40f, reference = UndercutReference.LINER_AFT, referenceLinerId = "L1")
        val after = draft.startingAtEndOf(other, gapMm = 57.15f)
        assertEquals(draft.copy(startFromAftMm = 207.15f), after)
        assertEquals(57.15f, undercutGapAfterMm(after.startFromAftMm, other.endMm), 1e-4f)

        val before = draft.endingAtStartOf(other, gapMm = 10f)
        assertEquals(draft.copy(startFromAftMm = 50f), before)
        assertEquals(10f, undercutGapBeforeMm(before.startFromAftMm, before.lengthMm, other.startMm), 1e-4f)
    }

    @Test
    fun `the gap field names its chip`() {
        assertEquals("Gap after Cut 1 (in)", undercutGapFieldLabel(after = true, position = 1, unitAbbr = "in"))
        assertEquals("Gap before Cut 3 (mm)", undercutGapFieldLabel(after = false, position = 3, unitAbbr = "mm"))
    }

    @Test
    fun `both presets go where they say whichever side the draft starts on`() {
        // The midpoint rule is gone: a draft sitting FWD of the other cut can still be put
        // BEFORE it, and one sitting AFT of it can be put AFTER it.
        val other = UndercutSpanMm("o", 100f, 150f)
        assertEquals(60f, draftOf("d", 400f, lengthMm = 40f).endingAtStartOf(other).startFromAftMm, 0f)
        assertEquals(150f, draftOf("d", 0f, lengthMm = 40f).startingAtEndOf(other).startFromAftMm, 0f)
    }

    @Test
    fun `a preset chip names the other cut by its page position`() {
        assertEquals("Cut 4", undercutButtChipLabel(4))
    }

    // ── Naming the overlap partner ──

    @Test
    fun `the confirm issue names a partner on this page`() {
        val others = listOf(UndercutSpanMm("u3", 200f, 300f))
        assertEquals(
            "Overlaps cut 3 — sit fully inside it or fully clear",
            undercutConfirmIssue(draftOf("x", 250f, 100f), 1000f, others, mapOf("u3" to 3)),
        )
        assertEquals(
            "Overlaps a cut outside this strip — sit fully inside it or fully clear",
            undercutConfirmIssue(draftOf("x", 250f, 100f), 1000f, others, mapOf("u1" to 1)),
        )
        // No page context: the generic wording.
        assertEquals(
            UNDERCUT_PARTIAL_OVERLAP_MSG,
            undercutConfirmIssue(draftOf("x", 250f, 100f), 1000f, others),
        )
        // Shaft bounds still outrank the overlap.
        assertEquals(
            "Undercut extends past the shaft's FWD end",
            undercutConfirmIssue(draftOf("x", 250f, 800f), 1000f, others, mapOf("u3" to 3)),
        )
        assertNull(undercutConfirmIssue(draftOf("x", 300f, 50f), 1000f, others, mapOf("u3" to 3)))
    }

    @Test
    fun `an edited pending add card commits or prompts on the way out`() {
        // Nothing stored to equal, so an add page the machinist changed is dirty by construction:
        // it commits on the way out when it is clear, and prompts when it is not.
        val seed = draftOf(PENDING_UNDERCUT_ID, 250f)
        val edited = seed.copy(diaMm = 180f)
        assertEquals(
            UndercutLeaveAction.COMMIT,
            undercutLeaveAction(edited, baseline = null, confirmIssue = null, pendingSeed = seed),
        )
        assertEquals(
            UndercutLeaveAction.PROMPT,
            undercutLeaveAction(
                edited, baseline = null, confirmIssue = "Extends past the shaft", pendingSeed = seed,
            ),
        )
        // Any one field counts as an edit.
        listOf(
            seed.copy(startFromAftMm = 251f),
            seed.copy(lengthMm = 10f),
            seed.copy(note = "pitted"),
            seed.copy(reference = UndercutReference.FWD_SET),
        ).forEach {
            assertEquals(
                UndercutLeaveAction.COMMIT,
                undercutLeaveAction(it, baseline = null, confirmIssue = null, pendingSeed = seed),
            )
        }
    }

    @Test
    fun `an untouched pending add card is discarded on the way out`() {
        // The seeded default span nobody typed into: committing it recorded stray cuts.
        val seed = draftOf(PENDING_UNDERCUT_ID, 250f)
        assertEquals(
            UndercutLeaveAction.DISCARD,
            undercutLeaveAction(seed, baseline = null, confirmIssue = null, pendingSeed = seed),
        )
        // …even when the seed itself is blocked: there is nothing to ask about keeping.
        assertEquals(
            UndercutLeaveAction.DISCARD,
            undercutLeaveAction(
                seed, baseline = null, confirmIssue = UNDERCUT_PARTIAL_OVERLAP_MSG, pendingSeed = seed,
            ),
        )
        // A STORED card equal to a pending seed's values is not pending: the seed never applies.
        val stored = seed.copy(id = "a")
        assertEquals(
            UndercutLeaveAction.COMMIT,
            undercutLeaveAction(stored, draftOf("a", 100f), confirmIssue = null, pendingSeed = stored),
        )
    }

    // ── Parent-cut datum ──

    @Test
    fun `a parent chip sets the reference and the parent id and never moves canonical`() {
        val draft = draftOf("c", 660f, lengthMm = 50f, reference = UndercutReference.LINER_AFT, referenceLinerId = "L1")
        val parent = UndercutSpanMm("p", 600f, 800f)
        val aft = draft.measuredFromParent(UndercutReference.PARENT_AFT, parent)
        assertEquals(
            draft.copy(reference = UndercutReference.PARENT_AFT, referenceCutId = "p", referenceLinerId = ""),
            aft,
        )
        val fwd = draft.measuredFromParent(UndercutReference.PARENT_FWD, parent)
        assertEquals(660f, fwd.startFromAftMm, 0f)
        assertEquals(50f, fwd.lengthMm, 0f)
        assertEquals("p", fwd.referenceCutId)
    }

    @Test
    fun `a parent reference displays while its cut still contains this one`() {
        val u = Undercut(
            id = "c", startFromAftMm = 660f, lengthMm = 50f,
            authoredReference = UndercutReference.PARENT_FWD, referenceCutId = "p",
        )
        val spans = listOf(UndercutSpanMm("p", 600f, 800f), UndercutSpanMm("c", 660f, 710f))
        assertEquals(
            UndercutReference.PARENT_FWD,
            com.android.shaftschematic.geom.effectiveUndercutReference(u, emptyList(), spans, 100f, 1900f),
        )
    }

    @Test
    fun `a parent reference whose cut is gone or no longer contains it falls back to the nearer SET`() {
        val u = Undercut(
            id = "c", startFromAftMm = 660f, lengthMm = 50f,
            authoredReference = UndercutReference.PARENT_AFT, referenceCutId = "p",
        )
        val effective = { spans: List<UndercutSpanMm> ->
            com.android.shaftschematic.geom.effectiveUndercutReference(u, emptyList(), spans, 100f, 1900f)
        }
        // Parent deleted: 660..710 sits nearer the AFT S.E.T. at 100.
        assertEquals(UndercutReference.AFT_SET, effective(listOf(UndercutSpanMm("c", 660f, 710f))))
        // Parent shortened so the cut now runs past its FWD shoulder.
        assertEquals(
            UndercutReference.AFT_SET,
            effective(listOf(UndercutSpanMm("p", 600f, 700f), UndercutSpanMm("c", 660f, 710f))),
        )
        // Near the FWD S.E.T. the fallback is the FWD one.
        val far = u.copy(startFromAftMm = 1700f)
        assertEquals(
            UndercutReference.FWD_SET,
            com.android.shaftschematic.geom.effectiveUndercutReference(
                far, emptyList(), listOf(UndercutSpanMm("c", 1700f, 1750f)), 100f, 1900f,
            ),
        )
        // Display-only: the stored values are untouched, and with no sheet to resolve against
        // (the baseline) the reference passes through.
        assertEquals(UndercutReference.PARENT_AFT, u.authoredReference)
        assertEquals("p", u.referenceCutId)
        assertEquals(UndercutReference.PARENT_AFT, com.android.shaftschematic.geom.effectiveUndercutReference(u, emptyList()))
    }

    @Test
    fun `the baseline keeps a parent id only under a parent reference`() {
        val parentRef = Undercut(
            id = "c", startFromAftMm = 660f, lengthMm = 50f,
            authoredReference = UndercutReference.PARENT_AFT, referenceCutId = "p",
        )
        assertEquals("p", undercutDraftOf(parentRef, emptyList()).referenceCutId)
        assertEquals(UndercutReference.PARENT_AFT, undercutDraftOf(parentRef, emptyList()).reference)
        val stale = parentRef.copy(authoredReference = UndercutReference.FWD_SET)
        assertEquals("", undercutDraftOf(stale, emptyList()).referenceCutId)
        // The draft carries it back out to the preview cut.
        assertEquals("p", undercutDraftOf(parentRef, emptyList()).toUndercut().referenceCutId)
    }

    @Test
    fun `the parent candidate is the smallest cut fully containing the draft`() {
        val spans = listOf(
            UndercutSpanMm("relief", 500f, 1000f),
            UndercutSpanMm("inner", 600f, 800f),
            UndercutSpanMm("d", 650f, 700f),
            UndercutSpanMm("beside", 1000f, 1100f),
        )
        assertEquals("inner", com.android.shaftschematic.geom.undercutParentCandidate("d", spans)?.id)
        assertEquals(null, com.android.shaftschematic.geom.undercutParentCandidate("beside", spans))
        assertEquals(null, com.android.shaftschematic.geom.undercutParentCandidate("relief", spans))
    }

    // ── Overlap pool: a draft is never checked against its own card's cut ──

    @Test
    fun `a draft's pool excludes its own cut and keeps every other`() {
        val all = listOf(cut("a", 100f), cut("b", 200f))
        val pool = undercutDraftPool(all, draftOf("a", 100f), heldUnderId = "a", oalMm = 1000f)
        assertEquals(listOf("b"), pool.map { it.id })
    }

    @Test
    fun `a draft held under another card's id never overlaps that card`() {
        // The stale state the on-device loop came from: a draft (here the add flow's id) on top of
        // the very cut it duplicates. Checked against a pool holding that cut it blocks as
        // "Overlaps cut 1" — the card it is shown on; the guard drops the self entry.
        val all = listOf(cut("a", 100f), cut("b", 200f))
        val stale = draftOf(PENDING_UNDERCUT_ID, 100f)
        val pool = undercutDraftPool(all, stale, heldUnderId = "a", oalMm = 1000f)
        assertEquals(listOf("b"), pool.map { it.id })
        assertNull(undercutConfirmIssue(stale, 1000f, pool, mapOf("a" to 1, "b" to 2)))
    }

    @Test
    fun `a pending draft's pool is the whole sheet`() {
        val all = listOf(cut("a", 100f), cut("b", 200f))
        val pool = undercutDraftPool(all, draftOf(PENDING_UNDERCUT_ID, 400f), PENDING_UNDERCUT_ID, 1000f)
        assertEquals(listOf("a", "b"), pool.map { it.id })
    }

    // ── Detail canvas rail: the printed strip's plan at canvas scale ──

    private fun detailRailInputs(
        spans: List<UndercutSpanMm>,
        chainStart: Float,
        chainEnd: Float,
        drawStart: Float = chainStart - 60f,
        drawEnd: Float = chainEnd + 60f,
    ) = UndercutDetailRailInputs(
        spans = spans,
        chainStartMm = chainStart,
        chainEndMm = chainEnd,
        splitAtMm = emptyList(),
        unit = UnitSystem.INCHES,
        startedStrip = spans.isEmpty(),
        widthPx = 1000f,
        profileRowHeightPx = 367f,
        edgePadPx = 84f,
        drawStartMm = drawStart,
        windowLengthMm = drawEnd - drawStart,
        maxOdMm = 254f,
        scale = 3.28f,
        textSizePx = 26.25f,
        typeface = null,
        fractionStyle = null,
    )

    @Test
    fun `the detail canvas runs the printed strip's rail plan over the job`() {
        val raw = requireNotNull(javaClass.classLoader?.getResource("fixtures/undercut_polar_ranger_stbd.shaft"))
            .readText()
        val doc = com.android.shaftschematic.doc.ShaftDocCodec.decode(raw)
        val liner = doc.spec.liners.single { it.id.startsWith("02d0b768") }
        val spans = doc.undercutRecord.undercuts
            .map { UndercutSpanMm(it.id, it.startFromAftMm, it.startFromAftMm + it.lengthMm) }
        val chainStart = minOf(liner.startFromAftMm, spans.minOf { it.startMm })
        val chainEnd = maxOf(liner.startFromAftMm + liner.lengthMm, spans.maxOf { it.endMm })
        val inputs = detailRailInputs(spans, chainStart, chainEnd)
        val widthOf: (com.android.shaftschematic.util.DualLabel) -> Float = { it.inline().length * 14f }
        val rails = requireNotNull(planUndercutDetailRails(inputs, widthOf))

        // The top-level chain: the nested 2 3/8" cut is not on it — it has its own row, against
        // its parent's shoulders.
        assertEquals(
            listOf("1 1/4\"", "12 5/8\"", "3 11/16\"", "9 1/4\""),
            rails.chainSpans.map { it.label.inline() },
        )
        assertEquals(listOf("5.080\"", "2 3/8\"", "5.170\""), rails.nestedSpans.single().map { it.label.inline() })
        assertNotNull(rails.totalSpan)

        // It IS the printed strip's planner, over the canvas's own x map at the canvas scale.
        val lay = computeUndercutWindowLayout(
            widthPx = inputs.widthPx, profileTopPx = 0f, profileRowHeightPx = inputs.profileRowHeightPx,
            windowLengthMm = inputs.windowLengthMm, maxOdMm = inputs.maxOdMm, edgePadPx = inputs.edgePadPx,
        )
        val direct = com.android.shaftschematic.pdf.planUndercutStripRails(
            chainStart, chainEnd, spans, UnitSystem.INCHES, dual = false, splitAtMm = emptyList(),
            startedStrip = false,
            xAt = { mm -> lay.startPx + (mm - inputs.drawStartMm) * lay.pxPerMm },
            labelWidth = widthOf,
            scale = inputs.scale,
        )
        // ...with the chain's fallback values always lifted (the canvas has no page to run out of).
        assertEquals(direct.withFallbacksLifted(), rails)

        // The band is the printed strip's: total line, the rest of the total band, the chain's
        // lifted rows, the chain line, then the plan's below-rows and the rail headroom down to
        // the cylinder top.
        val rowStep = 17f * inputs.scale
        val band = undercutDetailRailBand(rails, inputs.scale, rowStep)
        assertEquals(
            20f * inputs.scale + rails.rows.aboveRows * rowStep,
            band.chainRailY - band.totalRailY, 1e-3f,
        )
        assertEquals(
            band.chainRailY + rails.rows.belowRows * rowStep + UNDERCUT_DETAIL_RAIL_HEADROOM_PT * inputs.scale,
            band.cylTopPx, 1e-3f,
        )
    }

    @Test
    fun `the detail canvas lifts the job's short chain values above the line, under the total`() {
        val raw = requireNotNull(javaClass.classLoader?.getResource("fixtures/undercut_polar_ranger_stbd.shaft"))
            .readText()
        val doc = com.android.shaftschematic.doc.ShaftDocCodec.decode(raw)
        val liner = doc.spec.liners.single { it.id.startsWith("02d0b768") }
        val spans = doc.undercutRecord.undercuts
            .map { UndercutSpanMm(it.id, it.startFromAftMm, it.startFromAftMm + it.lengthMm) }
        val chainStart = minOf(liner.startFromAftMm, spans.minOf { it.startMm })
        val chainEnd = maxOf(liner.startFromAftMm + liner.lengthMm, spans.maxOf { it.endMm })
        val inputs = detailRailInputs(spans, chainStart, chainEnd)
        val rails = requireNotNull(planUndercutDetailRails(inputs) { it.inline().length * 14f })

        val used = com.android.shaftschematic.pdf.undercutRailFallbackRows(rails.chainWithLead)
        assertTrue("the job's 1 1/4\" falls off its line", used >= 1)
        assertNotNull(rails.totalSpan)
        assertEquals("every used row is lifted above the chain", used, rails.rows.aboveRows)
        assertEquals("below keeps the clear row and the nested rows", 1 + rails.rows.nestedRows, rails.rows.belowRows)

        val rowStep = 17f * inputs.scale
        val band = undercutDetailRailBand(rails, inputs.scale, rowStep)
        assertTrue(
            "the total line sits above the lifted rows",
            band.totalRailY < band.chainRailY - rails.rows.aboveRows * rowStep,
        )
    }

    @Test
    fun `a window shift with unchanged cuts re-plans the rail's x`() {
        val spans = listOf(UndercutSpanMm("a", 1030f, 1100f), UndercutSpanMm("b", 1200f, 1300f))
        val base = detailRailInputs(spans, 1000f, 1500f, drawStart = 950f, drawEnd = 1550f)
        val shifted = base.copy(drawStartMm = 900f)
        // The plan's remember key is the inputs record, so a moved window is a different key...
        assertTrue(base != shifted)
        val a = requireNotNull(planUndercutDetailRails(base) { it.inline().length * 14f })
        val b = requireNotNull(planUndercutDetailRails(shifted) { it.inline().length * 14f })
        // ...and a different plan: every span sits further right on the canvas.
        assertEquals(a.chain.size, b.chain.size)
        a.chain.zip(b.chain).forEach { (x, y) -> assertTrue(y.x0Pt > x.x0Pt) }
    }

    @Test
    fun `an empty strip draws no rail and the canvas has no plan before its first layout`() {
        val started = detailRailInputs(emptyList(), 1000f, 1500f)
        val rails = requireNotNull(planUndercutDetailRails(started) { 10f })
        assertTrue(rails.chain.isEmpty() && rails.nested.isEmpty() && rails.total.isEmpty())
        val band = undercutDetailRailBand(rails, started.scale, 17f * started.scale)
        assertEquals("only the top pad", band.chainRailY, band.cylTopPx, 1e-3f)
        assertNull(planUndercutDetailRails(started.copy(widthPx = 0f)) { 10f })
    }

    // ── New-cut seeds ────────────────────────────────────────────────────────

    @Test
    fun `a seed inherits its neighbour's datum while that datum stands`() {
        val liner = UndercutLinerSpan("L1", 500f, 1500f)
        val from = draftOf("u1", 600f, 50f, diaMm = 180f, note = "weld",
            reference = UndercutReference.LINER_FWD, referenceLinerId = "L1")
        val seed = undercutSeedInheriting(
            startMm = 650f, lengthMm = 25.4f, from = from,
            linerSpans = listOf(liner), sheetSpans = listOf(UndercutSpanMm("u1", 600f, 650f)),
            aftSetXMm = 0f, fwdSetXMm = 2000f,
        )
        assertTrue(seed.isPending)
        assertEquals(650f, seed.startFromAftMm, 0f)
        assertEquals(25.4f, seed.lengthMm, 0f)
        assertEquals(0f, seed.diaMm, 0f)
        assertEquals("", seed.note)
        assertEquals(UndercutReference.LINER_FWD, seed.reference)
        assertEquals("L1", seed.referenceLinerId)
    }

    @Test
    fun `a seed whose inherited liner edge is buried reads from the nearer SET`() {
        val liner = UndercutLinerSpan("L1", 500f, 1500f)
        // u1 crosses the liner's AFT edge, so Liner AFT is no datum for the seed beside it.
        val from = draftOf("u1", 450f, 150f, reference = UndercutReference.LINER_AFT, referenceLinerId = "L1")
        val seed = undercutSeedInheriting(
            startMm = 600f, lengthMm = 25.4f, from = from,
            linerSpans = listOf(liner), sheetSpans = listOf(UndercutSpanMm("u1", 450f, 600f)),
            aftSetXMm = 0f, fwdSetXMm = 2000f,
        )
        assertEquals(UndercutReference.AFT_SET, seed.reference)
        assertEquals("", seed.referenceLinerId)
        assertEquals("", seed.referenceCutId)
    }

    @Test
    fun `next cut is enabled on a clear card and says why only when stock runs out`() {
        val clear = undercutNextCutState(draftOf("u1", 600f, 50f), blocked = false, otherPendingOpen = false, oalMm = 2000f)
        assertTrue(clear.enabled)
        assertNull(clear.caption)

        val blocked = undercutNextCutState(draftOf("u1", 600f, 50f), blocked = true, otherPendingOpen = false, oalMm = 2000f)
        assertFalse(blocked.enabled)
        assertNull(blocked.caption)

        val otherAdd = undercutNextCutState(draftOf("u1", 600f, 50f), blocked = false, otherPendingOpen = true, oalMm = 2000f)
        assertFalse(otherAdd.enabled)

        val atEnd = undercutNextCutState(draftOf("u1", 1950f, 40f), blocked = false, otherPendingOpen = false, oalMm = 2000f)
        assertFalse(atEnd.enabled)
        assertEquals(UNDERCUT_NO_STOCK_FWD_MSG, atEnd.caption)
    }
}
