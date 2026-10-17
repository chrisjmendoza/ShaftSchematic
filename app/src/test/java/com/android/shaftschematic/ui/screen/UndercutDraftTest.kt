package com.android.shaftschematic.ui.screen

import com.android.shaftschematic.geom.UNDERCUT_PARTIAL_OVERLAP_MSG
import com.android.shaftschematic.geom.UndercutLinerSpan
import com.android.shaftschematic.geom.UndercutSpanMm
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

    // ── Card title + Butt against ────────────────────────────────────────────

    @Test
    fun `the card title states the aft to fwd position convention`() {
        assertEquals("Undercut 2 of 3 · aft → fwd", undercutCardTitle(2, 3))
    }

    @Test
    fun `butting after a cut starts the draft at its end, length and reference untouched`() {
        val draft = draftOf(
            "d", 300f, lengthMm = 40f,
            reference = UndercutReference.LINER_FWD, referenceLinerId = "L1",
        )
        val moved = draft.buttingAgainst(UndercutSpanMm("o", 100f, 150f))
        assertEquals(150f, moved.startFromAftMm, 0f)
        assertEquals(draft.copy(startFromAftMm = 150f), moved)
    }

    @Test
    fun `butting before a cut ends the draft at its start, length and reference untouched`() {
        val draft = draftOf("d", 20f, lengthMm = 40f, reference = UndercutReference.FWD_SET)
        val moved = draft.buttingAgainst(UndercutSpanMm("o", 100f, 150f))
        assertEquals(60f, moved.startFromAftMm, 0f)
        assertEquals(draft.copy(startFromAftMm = 60f), moved)
    }

    @Test
    fun `a pending add card has no baseline, so leaving it always resolves`() {
        // Nothing stored to equal, so the add page is dirty by construction: it commits on the
        // way out when it is clear, and prompts when it is not — it never evaporates silently.
        val pending = draftOf(PENDING_UNDERCUT_ID, 250f)
        assertEquals(
            UndercutLeaveAction.COMMIT,
            undercutLeaveAction(pending, baseline = null, confirmIssue = null),
        )
        assertEquals(
            UndercutLeaveAction.PROMPT,
            undercutLeaveAction(pending, baseline = null, confirmIssue = "Extends past the shaft"),
        )
    }
}
