package com.android.shaftschematic.geom

import com.android.shaftschematic.model.Undercut
import com.android.shaftschematic.model.UndercutReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max

/**
 * Tests for `geom/UndercutMath.kt` — reference conversion, span validation, cluster
 * windows, hit-testing, and the per-sheet drawn-depth exaggeration for the undercut
 * drawing feature.
 */
class UndercutMathTest {

    // ── Reference conversion ──

    @Test
    fun `AFT_SET entry is offset by the aft SET position`() {
        val canonical = undercutStartToCanonicalMm(
            reference = UndercutReference.AFT_SET,
            enteredMm = 100f, lengthMm = 40f, aftSetXMm = 50f, fwdSetXMm = 950f,
        )
        assertEquals(150f, canonical, 1e-4f)
    }

    @Test
    fun `FWD_SET entry locates the FWD edge measured aft from the fwd SET`() {
        val canonical = undercutStartToCanonicalMm(
            reference = UndercutReference.FWD_SET,
            enteredMm = 100f, lengthMm = 40f, aftSetXMm = 50f, fwdSetXMm = 950f,
        )
        // FWD edge at 950 - 100 = 850, AFT edge (canonical) = 850 - 40.
        assertEquals(810f, canonical, 1e-4f)
    }

    @Test
    fun `conversion pair round-trips exactly for all references`() {
        for (ref in UndercutReference.entries) {
            for (entered in listOf(0f, 12.7f, 101.6001f, 333.333f)) {
                val canonical = undercutStartToCanonicalMm(
                    ref, entered, 55.5f, 42.42f, 1234.56f,
                    linerStartMm = 600f, linerEndMm = 1000f,
                )
                val redisplayed = canonicalToUndercutStartMm(
                    ref, canonical, 55.5f, 42.42f, 1234.56f,
                    linerStartMm = 600f, linerEndMm = 1000f,
                )
                assertEquals("ref=$ref entered=$entered", entered, redisplayed, 1e-3f)
            }
        }
    }

    @Test
    fun `LINER_AFT entry is offset by the liner AFT edge`() {
        val canonical = undercutStartToCanonicalMm(
            reference = UndercutReference.LINER_AFT,
            enteredMm = 50f, lengthMm = 40f, aftSetXMm = 0f, fwdSetXMm = 0f,
            linerStartMm = 600f, linerEndMm = 1000f,
        )
        assertEquals(650f, canonical, 1e-4f)
    }

    @Test
    fun `LINER_FWD entry locates the FWD edge measured aft from the liner FWD edge`() {
        val canonical = undercutStartToCanonicalMm(
            reference = UndercutReference.LINER_FWD,
            enteredMm = 50f, lengthMm = 40f, aftSetXMm = 0f, fwdSetXMm = 0f,
            linerStartMm = 600f, linerEndMm = 1000f,
        )
        // FWD edge at 1000 - 50 = 950, AFT edge (canonical) = 950 - 40.
        assertEquals(910f, canonical, 1e-4f)
    }

    // ── Length edits keep the authored Distance fixed ──

    @Test
    fun `length edit keeps the displayed distance unchanged for all references`() {
        for (ref in UndercutReference.entries) {
            val canonical = undercutStartToCanonicalMm(
                ref, enteredMm = 127f, lengthMm = 304.8f, aftSetXMm = 50f, fwdSetXMm = 1900f,
                linerStartMm = 600f, linerEndMm = 1000f,
            )
            val newCanonical = undercutCanonicalForNewLength(
                ref, canonical, oldLengthMm = 304.8f, newLengthMm = 254f,
                aftSetXMm = 50f, fwdSetXMm = 1900f, linerStartMm = 600f, linerEndMm = 1000f,
            )
            val redisplayed = canonicalToUndercutStartMm(
                ref, newCanonical, lengthMm = 254f, aftSetXMm = 50f, fwdSetXMm = 1900f,
                linerStartMm = 600f, linerEndMm = 1000f,
            )
            assertEquals("ref=$ref", 127f, redisplayed, 1e-3f)
        }
    }

    @Test
    fun `length edit under an AFT reference leaves canonical untouched`() {
        for (ref in listOf(UndercutReference.AFT_SET, UndercutReference.LINER_AFT)) {
            val newCanonical = undercutCanonicalForNewLength(
                ref, canonicalStartMm = 700f, oldLengthMm = 60f, newLengthMm = 90f,
                aftSetXMm = 50f, fwdSetXMm = 1900f, linerStartMm = 600f, linerEndMm = 1000f,
            )
            assertEquals("ref=$ref", 700f, newCanonical, 1e-4f)
        }
    }

    @Test
    fun `length edit under a FWD reference pins the cut's FWD end`() {
        // The on-device report's shape: liner-FWD reference, Distance 5 units, Length 12 -> 10.
        // The FWD end (linerEnd - distance) must stay put; the AFT start absorbs the delta.
        val canonical = undercutStartToCanonicalMm(
            UndercutReference.LINER_FWD, enteredMm = 127f, lengthMm = 304.8f,
            aftSetXMm = 0f, fwdSetXMm = 0f, linerStartMm = 600f, linerEndMm = 1000f,
        )
        val newCanonical = undercutCanonicalForNewLength(
            UndercutReference.LINER_FWD, canonical, oldLengthMm = 304.8f, newLengthMm = 254f,
            aftSetXMm = 0f, fwdSetXMm = 0f, linerStartMm = 600f, linerEndMm = 1000f,
        )
        assertEquals(canonical + 304.8f, newCanonical + 254f, 1e-3f)
    }

    // ── Liner extensions ──

    @Test
    fun `a span inside the liner extends past neither edge`() {
        val (aft, fwd) = undercutLinerExtensionsMm(
            startMm = 650f, lengthMm = 200f, linerStartMm = 600f, linerEndMm = 1000f,
        )
        assertEquals(0f, aft, 1e-4f)
        assertEquals(0f, fwd, 1e-4f)
    }

    @Test
    fun `a span overhanging both liner edges reports both overhangs`() {
        val (aft, fwd) = undercutLinerExtensionsMm(
            startMm = 550f, lengthMm = 480f, linerStartMm = 600f, linerEndMm = 1000f,
        )
        assertEquals(50f, aft, 1e-4f)
        assertEquals(30f, fwd, 1e-4f)
    }

    // ── "Past the liner edge" trigger ──

    @Test
    fun `an edit that leaves the span inside the liner overhangs nothing new`() {
        assertEquals(
            false to false,
            newLinerOverhangs(650f, 200f, 700f, 250f, linerStartMm = 600f, linerEndMm = 1000f),
        )
    }

    @Test
    fun `a length that grows past the FWD edge is a new FWD overhang`() {
        assertEquals(
            false to true,
            newLinerOverhangs(650f, 200f, 650f, 400f, linerStartMm = 600f, linerEndMm = 1000f),
        )
    }

    @Test
    fun `a distance that moves the start past the AFT edge is a new AFT overhang`() {
        assertEquals(
            true to false,
            newLinerOverhangs(650f, 200f, 550f, 200f, linerStartMm = 600f, linerEndMm = 1000f),
        )
    }

    @Test
    fun `a span already past the edge that grows further is not new`() {
        assertEquals(
            false to false,
            newLinerOverhangs(650f, 400f, 650f, 500f, linerStartMm = 600f, linerEndMm = 1000f),
        )
    }

    @Test
    fun `a span pulled back inside the liner overhangs nothing new`() {
        assertEquals(
            false to false,
            newLinerOverhangs(550f, 500f, 650f, 200f, linerStartMm = 600f, linerEndMm = 1000f),
        )
    }

    @Test
    fun `an overhang within the touch slop is not an overhang`() {
        // Float round trips at a long shaft's FWD end leave sub-slop residue; that is the edge.
        assertEquals(
            false to false,
            newLinerOverhangs(650f, 200f, 650f, 350.005f, linerStartMm = 600f, linerEndMm = 1000f),
        )
    }

    // ── Whole-liner gate: a liner with company ──

    private val gateLiner = UndercutLinerSpan("L1", 600f, 1000f)

    @Test
    fun `a liner no other cut reaches has no company`() {
        assertFalse(undercutLinerHasOtherCuts(emptyList(), gateLiner))
    }

    @Test
    fun `a cut wholly outside the liner is not company`() {
        val others = listOf(UndercutSpanMm("a", 100f, 300f), UndercutSpanMm("b", 1100f, 1200f))
        assertFalse(undercutLinerHasOtherCuts(others, gateLiner))
    }

    @Test
    fun `a cut that only touches the liner edge within the slop is not company`() {
        // Butted to the AFT edge, and one reaching 0.005 mm onto the FWD edge — both the edge.
        val others = listOf(UndercutSpanMm("a", 500f, 600f), UndercutSpanMm("b", 999.995f, 1100f))
        assertFalse(undercutLinerHasOtherCuts(others, gateLiner))
    }

    @Test
    fun `a cut overlapping the liner by more than the slop is company`() {
        assertTrue(undercutLinerHasOtherCuts(listOf(UndercutSpanMm("a", 500f, 600.5f)), gateLiner))
    }

    @Test
    fun `a cut nested inside the liner is company`() {
        assertTrue(undercutLinerHasOtherCuts(listOf(UndercutSpanMm("a", 700f, 750f)), gateLiner))
    }

    @Test
    fun `a cut spanning the whole liner is company`() {
        assertTrue(undercutLinerHasOtherCuts(listOf(UndercutSpanMm("a", 600f, 1000f)), gateLiner))
    }

    @Test
    fun `whole-liner predicate holds within eps and fails past it`() {
        assertTrue(undercutIsWholeLiner(600f, 400f, linerStartMm = 600f, linerEndMm = 1000f))
        assertTrue(
            undercutIsWholeLiner(
                600f + UNDERCUT_SPAN_EPS_MM * 0.5f, 400f - UNDERCUT_SPAN_EPS_MM * 0.5f,
                linerStartMm = 600f, linerEndMm = 1000f,
            ),
        )
        assertFalse(undercutIsWholeLiner(601f, 399f, linerStartMm = 600f, linerEndMm = 1000f))
        assertFalse(undercutIsWholeLiner(600f, 450f, linerStartMm = 600f, linerEndMm = 1000f))
        assertTrue(undercutIsWholeLiner(600f, 400f, 600f, 1000f, epsMm = 2f))
        assertTrue(undercutIsWholeLiner(601f, 400f, 600f, 1000f, epsMm = 2f))
    }

    // ── Nearest-SET default for bare-shaft cuts ──

    @Test
    fun `bare-shaft cut in the AFT half defaults to the AFT SET`() {
        assertEquals(
            UndercutReference.AFT_SET,
            nearestSetReference(startMm = 300f, endMm = 360f, aftSetXMm = 100f, fwdSetXMm = 1900f),
        )
    }

    @Test
    fun `bare-shaft cut in the FWD half defaults to the FWD SET`() {
        assertEquals(
            UndercutReference.FWD_SET,
            nearestSetReference(startMm = 1500f, endMm = 1560f, aftSetXMm = 100f, fwdSetXMm = 1900f),
        )
    }

    @Test
    fun `bare-shaft cut straddling the midpoint breaks the tie to the AFT SET`() {
        assertEquals(
            UndercutReference.AFT_SET,
            nearestSetReference(startMm = 970f, endMm = 1030f, aftSetXMm = 100f, fwdSetXMm = 1900f),
        )
    }

    // ── Preview draw range (overlay window follows the previewed spans) ──

    @Test
    fun `preview range is the strip range while spans stay inside it`() {
        val liner = UndercutLinerSpan("ln", 600f, 1000f)
        val stored = listOf(UndercutSpanMm("u1", 700f, 760f))
        val strip = linerStripFor(liner, stored, oalMm = 2000f)
        val (start, end) = undercutPreviewDrawRange(strip, stored, oalMm = 2000f)
        assertEquals(strip.drawStartMm, start, 1e-4f)
        assertEquals(strip.drawEndMm, end, 1e-4f)
    }

    @Test
    fun `draft overhanging the liner edge widens the preview range with the standard pad`() {
        val liner = UndercutLinerSpan("ln", 600f, 1000f)
        val strip = linerStripFor(liner, listOf(UndercutSpanMm("u1", 700f, 760f)), oalMm = 2000f)
        // The draft slid the cut past the liner's AFT edge; the stored strip hasn't rebuilt.
        val preview = listOf(UndercutSpanMm("u1", 575f, 635f))
        val (start, end) = undercutPreviewDrawRange(strip, preview, oalMm = 2000f)
        assertEquals(575f - UNDERCUT_WINDOW_PAD_MM, start, 1e-4f)
        assertEquals(strip.drawEndMm, end, 1e-4f)
    }

    @Test
    fun `preview range clamps to the shaft and never narrows`() {
        val liner = UndercutLinerSpan("ln", 600f, 1000f)
        val strip = linerStripFor(liner, listOf(UndercutSpanMm("u1", 700f, 760f)), oalMm = 2000f)
        // Overhang near the shaft's AFT end: pad would go negative — clamp to 0.
        val (start, _) = undercutPreviewDrawRange(
            strip, listOf(UndercutSpanMm("u1", 10f, 50f)), oalMm = 2000f,
        )
        assertEquals(0f, start, 1e-4f)
        // No spans at all (every preview span clamped away): the strip range, unchanged.
        val (s2, e2) = undercutPreviewDrawRange(strip, emptyList(), oalMm = 2000f)
        assertEquals(strip.drawStartMm, s2, 1e-4f)
        assertEquals(strip.drawEndMm, e2, 1e-4f)
    }

    // ── Window-end break room ──

    /** Constant-Ø seg. */
    private fun seg(startMm: Float, endMm: Float, diaMm: Float) = SurfaceSeg(startMm, endMm, diaMm, diaMm)

    /**
     * The on-device shaft: a taper 0 → 25-3/16" (Ø 180 → S.E.T. Ø 8.26"), a 1/4" body, the AFT
     * liner, then body to the 368.5" OAL.
     */
    private val stubSegs = listOf(
        SurfaceSeg(0f, 639.76f, 180f, 209.8f),
        seg(639.76f, 646.11f, 209.8f),
        seg(646.11f, 1500f, 228.6f),
        seg(1500f, 9360f, 209.8f),
    )

    @Test
    fun `break room is the glyph reach plus clearance at the canvas amplitude`() {
        val dia = 150f
        val amp = dia * 0.5f * UNDERCUT_BREAK_AMP_FRAC_OF_R
        assertEquals(
            amp * (BREAK_EDGE_OUTWARD_REACH_FRAC + UNDERCUT_BREAK_CLEAR_FRAC_OF_AMP),
            undercutBreakRoomMm(dia), 1e-4f,
        )
        // A wide shaft outgrows the 1" pad; a small one does not.
        assertTrue(undercutBreakRoomMm(210f) > UNDERCUT_WINDOW_PAD_MM)
        assertFalse(undercutBreakRoomMm(50f) > UNDERCUT_WINDOW_PAD_MM)
    }

    @Test
    fun `no segs leaves the range unchanged`() {
        assertEquals(620.71f to 1525.4f, withUndercutBreakRoom(620.71f, 1525.4f, emptyList(), 9360f))
    }

    @Test
    fun `an end with room in a long uniform body is unchanged`() {
        val segs = listOf(seg(0f, 1000f, 100f), seg(1000f, 2000f, 120f), seg(2000f, 4000f, 100f))
        // 100 mm of interior stock each side, far more than the ~20 mm room at Ø 100.
        val (s, e) = withUndercutBreakRoom(900f, 2100f, segs, 4000f)
        assertEquals(900f, s, 1e-4f)
        assertEquals(2100f, e, 1e-4f)
    }

    @Test
    fun `on-device case - the AFT break leaves the stub and sits in the taper with room`() {
        val liner = UndercutLinerSpan("ln", 646.11f, 1500f)
        val strip = linerStripFor(liner, emptyList(), 9360f, segs = stubSegs)
        // The 1" pad landed 0.75" inside the taper; the break now sits a full room before the
        // taper face, and the 6 mm body stub between the taper and the liner is walked over.
        assertEquals(639.76f - undercutBreakRoomMm(209.8f), strip.drawStartMm, 1e-3f)
        // FWD: the 1" pad leaves less than a room of body before the liner face, so the end
        // moves out to a full room past it.
        val fwd = max(1500f + UNDERCUT_WINDOW_PAD_MM, 1500f + undercutBreakRoomMm(209.8f))
        assertEquals(fwd, strip.drawEndMm, 1e-3f)
        // Chain datums never move.
        assertEquals(646.11f, strip.chainStartMm, 1e-4f)
        assertEquals(1500f, strip.chainEndMm, 1e-4f)
        // Without segs the old padded range is unchanged.
        val plain = linerStripFor(liner, emptyList(), 9360f)
        assertEquals(646.11f - UNDERCUT_WINDOW_PAD_MM, plain.drawStartMm, 1e-3f)
        assertEquals(1500f + UNDERCUT_WINDOW_PAD_MM, plain.drawEndMm, 1e-3f)
    }

    @Test
    fun `a chain of short segs is walked over to the first that can host the break`() {
        val room = undercutBreakRoomMm(200f)
        val short = room * 0.4f
        // Long body, then three short collars (each shorter than room), then the liner.
        val c1 = 1000f
        val segs = listOf(
            seg(0f, c1, 200f),
            seg(c1, c1 + short, 200f),
            seg(c1 + short, c1 + 2 * short, 200f),
            seg(c1 + 2 * short, c1 + 3 * short, 200f),
            seg(c1 + 3 * short, 2000f, 220f),
        )
        val (s, _) = withUndercutBreakRoom(c1 + 2.5f * short, 1900f, segs, 3000f)
        assertEquals(c1 - room, s, 1e-3f)
        // Mirror on the FWD side: collars after the liner, then a long body.
        val f0 = 1000f
        val fwdSegs = listOf(
            seg(0f, f0, 220f),
            seg(f0, f0 + short, 200f),
            seg(f0 + short, f0 + 2 * short, 200f),
            seg(f0 + 2 * short, 3000f, 200f),
        )
        val (_, e) = withUndercutBreakRoom(100f, f0 + 0.5f * short, fwdSegs, 3000f)
        assertEquals(f0 + 2 * short + room, e, 1e-3f)
    }

    @Test
    fun `walking off either shaft end gives the flat physical end`() {
        val room = undercutBreakRoomMm(200f)
        val segs = listOf(
            seg(0f, room * 0.5f, 200f),
            seg(room * 0.5f, 500f, 220f),
            seg(500f, 1000f - room * 0.5f, 220f),
            seg(1000f - room * 0.5f, 1000f, 200f),
        )
        val (s, e) = withUndercutBreakRoom(room * 0.3f, 1000f - room * 0.3f, segs, 1000f)
        assertEquals(0f, s, 0f)
        assertEquals(1000f, e, 0f)
    }

    @Test
    fun `break room is idempotent`() {
        val once = withUndercutBreakRoom(620.71f, 1525.4f, stubSegs, 9360f)
        val twice = withUndercutBreakRoom(once.first, once.second, stubSegs, 9360f)
        assertEquals(once.first, twice.first, 1e-4f)
        assertEquals(once.second, twice.second, 1e-4f)
    }

    @Test
    fun `a body under a liner never hides the liner face`() {
        // The body runs the whole shaft under the liner; the envelope's liner face still counts.
        val segs = listOf(seg(0f, 2000f, 200f), seg(600f, 1000f, 220f))
        val (s, e) = withUndercutBreakRoom(600f - UNDERCUT_WINDOW_PAD_MM, 1000f + UNDERCUT_WINDOW_PAD_MM, segs, 2000f)
        assertEquals(600f - undercutBreakRoomMm(200f), s, 1e-3f)
        assertEquals(1000f + undercutBreakRoomMm(200f), e, 1e-3f)
    }

    @Test
    fun `preview range with segs never narrows and gets break room`() {
        val liner = UndercutLinerSpan("ln", 646.11f, 1500f)
        val strip = linerStripFor(liner, listOf(UndercutSpanMm("u1", 900f, 1020f)), 9360f, segs = stubSegs)
        val (s, e) = undercutPreviewDrawRange(
            strip, listOf(UndercutSpanMm("u1", 1000f, 1010f)), 9360f, segs = stubSegs,
        )
        assertEquals(strip.drawStartMm, s, 1e-4f)
        assertEquals(strip.drawEndMm, e, 1e-4f)
        // A draft overhanging the liner's FWD edge widens the range — and gets room past it.
        val (_, e2) = undercutPreviewDrawRange(
            strip, listOf(UndercutSpanMm("u1", 1480f, 1520f)), 9360f, segs = stubSegs,
        )
        assertTrue(e2 >= 1520f + UNDERCUT_WINDOW_PAD_MM - 1e-3f)
        assertTrue(e2 >= strip.drawEndMm)
    }

    @Test
    fun `free strips keep their window as the chain and grow only the draw range`() {
        // A bare-shaft cut whose 1" pad lands 10.4 mm before a taper's FWD face.
        val segs = listOf(SurfaceSeg(0f, 515f, 180f, 210f), seg(515f, 3000f, 210f))
        val strips = buildUndercutStrips(
            listOf(UndercutSpanMm("u", 530f, 600f)), emptyList(), 3000f, segs = segs,
        )
        val free = strips.single() as UndercutStrip.FreeStrip
        assertEquals(530f - UNDERCUT_WINDOW_PAD_MM, free.chainStartMm, 1e-4f)
        assertEquals(600f + UNDERCUT_WINDOW_PAD_MM, free.chainEndMm, 1e-4f)
        // The break cannot sit 10.4 mm short of the taper face: it moves back into the taper
        // with a full room before the face; the window (and its dimensioned pad) stays put.
        assertEquals(515f - undercutBreakRoomMm(210f), free.drawStartMm, 1e-3f)
        // The FWD break clears the cut's own FWD shoulder by the same room, never less than the pad.
        assertEquals(600f + max(UNDERCUT_WINDOW_PAD_MM, undercutBreakRoomMm(210f)), free.drawEndMm, 1e-3f)
    }

    // ── Validation ──

    @Test
    fun `in-bounds span passes validation`() {
        assertNull(undercutSpanIssue(canonicalStartMm = 100f, lengthMm = 50f, oalMm = 1000f))
    }

    @Test
    fun `boundary-exact span is accepted`() {
        assertNull(undercutSpanIssue(canonicalStartMm = 0f, lengthMm = 1000f, oalMm = 1000f))
    }

    @Test
    fun `zero or negative length is rejected`() {
        assertNotNull(undercutSpanIssue(canonicalStartMm = 100f, lengthMm = 0f, oalMm = 1000f))
        assertNotNull(undercutSpanIssue(canonicalStartMm = 100f, lengthMm = -5f, oalMm = 1000f))
    }

    @Test
    fun `span before the AFT end is rejected`() {
        assertNotNull(undercutSpanIssue(canonicalStartMm = -10f, lengthMm = 50f, oalMm = 1000f))
    }

    @Test
    fun `span past the FWD end is rejected`() {
        assertNotNull(undercutSpanIssue(canonicalStartMm = 980f, lengthMm = 50f, oalMm = 1000f))
    }

    @Test
    fun `overlap check blocks intrusion into an adjacent cut and allows touching edges`() {
        val others = listOf(UndercutSpanMm("other", 200f, 260f))
        // Partial intrusion from either side — the containment cases are legal and live in
        // their own tests below.
        assertNotNull(undercutOverlapIssue(canonicalStartMm = 250f, lengthMm = 40f, otherSpans = others))
        assertNotNull(undercutOverlapIssue(canonicalStartMm = 180f, lengthMm = 50f, otherSpans = others))
        // Edge-to-edge is legal, as is clear separation and an empty sheet.
        assertNull(undercutOverlapIssue(canonicalStartMm = 260f, lengthMm = 40f, otherSpans = others))
        assertNull(undercutOverlapIssue(canonicalStartMm = 100f, lengthMm = 100f, otherSpans = others))
        assertNull(undercutOverlapIssue(canonicalStartMm = 250f, lengthMm = 40f, otherSpans = emptyList()))
    }

    @Test
    fun `edges touching within the touch eps are clear on both sides`() {
        val others = listOf(UndercutSpanMm("other", 200f, 260f))
        // 0.005 mm into the neighbour from the FWD side, then from the AFT side.
        assertNull(undercutOverlapIssue(canonicalStartMm = 259.995f, lengthMm = 40f, otherSpans = others))
        assertNull(undercutOverlapIssue(canonicalStartMm = 160.005f, lengthMm = 40f, otherSpans = others))
        // The overlap band agrees with the confirm check.
        assertTrue(undercutPartialOverlapsMm(259.995f, 40f, others).isEmpty())
        assertTrue(undercutPartialOverlapsMm(160.005f, 40f, others).isEmpty())
        // A real intrusion, well past the slop, is still blocked.
        assertNotNull(undercutOverlapIssue(canonicalStartMm = 259.9f, lengthMm = 40f, otherSpans = others))
        assertTrue(UNDERCUT_TOUCH_EPS_MM > UNDERCUT_SPAN_EPS_MM)
    }

    @Test
    fun `an end-at-start-of preset on a long shaft clears the overlap check`() {
        // 12 m out, one Float ulp is ~1e-3 mm — the old touch slop — so the preset's own
        // `other.start - L` then `start + L` round trip could land inside the neighbour.
        val others = listOf(UndercutSpanMm("other", 12_000.37f, 12_254.37f))
        val lengthMm = 254f
        val start = undercutEndAtStartOf(otherStartMm = 12_000.37f, lengthMm = lengthMm)
        assertNull(undercutOverlapIssue(canonicalStartMm = start, lengthMm = lengthMm, otherSpans = others))
        // And with a drift larger than one ulp on the shared edge.
        assertNull(
            undercutOverlapIssue(
                canonicalStartMm = 12_000.37f - lengthMm + 0.004f, lengthMm = lengthMm, otherSpans = others,
            ),
        )
    }

    @Test
    fun `whole-liner predicate holds through Float drift on a long shaft`() {
        assertTrue(undercutIsWholeLiner(12_000f + 1e-3f, 400f - 1e-3f, 12_000f, 12_400f))
        assertTrue(undercutIsWholeLiner(12_000f + 4e-3f, 400f, 12_000f, 12_400f))
        assertFalse(undercutIsWholeLiner(12_000.5f, 400f, 12_000f, 12_400f))
    }

    @Test
    fun `containment is legal in both directions and partial overlap stays blocked`() {
        val others = listOf(UndercutSpanMm("other", 200f, 300f))
        // A cut machined INSIDE the other one, and one machined AROUND it — both legal.
        assertNull(undercutOverlapIssue(canonicalStartMm = 220f, lengthMm = 40f, otherSpans = others))
        assertNull(undercutOverlapIssue(canonicalStartMm = 150f, lengthMm = 200f, otherSpans = others))
        // Partial intrusion from either side is still one physical cut, double-dimensioned.
        assertNotNull(undercutOverlapIssue(canonicalStartMm = 250f, lengthMm = 100f, otherSpans = others))
        assertNotNull(undercutOverlapIssue(canonicalStartMm = 150f, lengthMm = 100f, otherSpans = others))
    }

    @Test
    fun `a nested cut may run up to its parent's own shoulder`() {
        val others = listOf(UndercutSpanMm("other", 200f, 300f))
        // Flush with the surrounding cut's AFT shoulder, then its FWD one: the shop deepens a
        // corroded section that reaches the original relief's boundary.
        assertNull(undercutOverlapIssue(canonicalStartMm = 200f, lengthMm = 40f, otherSpans = others))
        assertNull(undercutOverlapIssue(canonicalStartMm = 260f, lengthMm = 40f, otherSpans = others))
        // The same the other way round: a new relief machined around an existing cut, flush at
        // one end.
        assertNull(undercutOverlapIssue(canonicalStartMm = 200f, lengthMm = 200f, otherSpans = others))
    }

    @Test
    fun `an identical span is blocked and touching edge-to-edge stays legal`() {
        val others = listOf(UndercutSpanMm("other", 200f, 300f))
        // Two coincident spans are one cut entered twice, not a cut inside a cut.
        assertNotNull(undercutOverlapIssue(canonicalStartMm = 200f, lengthMm = 100f, otherSpans = others))
        assertNull(undercutOverlapIssue(canonicalStartMm = 300f, lengthMm = 40f, otherSpans = others))
        assertNull(undercutOverlapIssue(canonicalStartMm = 160f, lengthMm = 40f, otherSpans = others))
    }

    // ── Containment forest ──

    @Test
    fun `forest levels a nested pair and names its parent`() {
        val spans = listOf(
            UndercutSpanMm("child", 250f, 280f),
            UndercutSpanMm("parent", 200f, 400f),
            UndercutSpanMm("far", 600f, 650f),
        )
        val forest = undercutNestingForest(spans).associateBy { it.id }
        assertEquals(0, forest.getValue("parent").level)
        assertNull(forest.getValue("parent").parentId)
        assertEquals(1, forest.getValue("child").level)
        assertEquals("parent", forest.getValue("child").parentId)
        assertEquals(0, forest.getValue("far").level)
        assertNull(forest.getValue("far").parentId)
    }

    @Test
    fun `forest takes the SMALLEST containing span and reaches level 2`() {
        val spans = listOf(
            UndercutSpanMm("outer", 0f, 1000f),
            UndercutSpanMm("mid", 100f, 500f),
            UndercutSpanMm("inner", 200f, 300f),
        )
        val forest = undercutNestingForest(spans).associateBy { it.id }
        assertEquals("mid", forest.getValue("inner").parentId)
        assertEquals(2, forest.getValue("inner").level)
        assertEquals("outer", forest.getValue("mid").parentId)
        assertEquals(1, forest.getValue("mid").level)
        assertEquals(0, forest.getValue("outer").level)
    }

    @Test
    fun `forest tolerates a partial overlap as two siblings`() {
        // Stored data from before the containment gate: neither span contains the other, so both
        // stay top-level and render exactly as they always did.
        val forest = undercutNestingForest(
            listOf(UndercutSpanMm("a", 100f, 300f), UndercutSpanMm("b", 200f, 400f)),
        )
        assertTrue(forest.all { it.level == 0 && it.parentId == null })
    }

    @Test
    fun `a cut flush with its parent's shoulder still nests`() {
        val forest = undercutNestingForest(
            listOf(
                UndercutSpanMm("p", 100f, 300f),
                UndercutSpanMm("aftFlush", 100f, 160f),
                UndercutSpanMm("fwdFlush", 240f, 300f),
            ),
        ).associateBy { it.id }
        assertEquals("p", forest.getValue("aftFlush").parentId)
        assertEquals(1, forest.getValue("aftFlush").level)
        assertEquals("p", forest.getValue("fwdFlush").parentId)
        assertEquals(0, forest.getValue("p").level)
    }

    @Test
    fun `coincident spans are siblings, never each other's parent`() {
        val forest = undercutNestingForest(
            listOf(UndercutSpanMm("a", 100f, 200f), UndercutSpanMm("b", 100f, 200f)),
        )
        assertTrue(forest.all { it.level == 0 && it.parentId == null })
    }

    @Test
    fun `equal-width candidate parents break the tie to the first in input order`() {
        // Spans sharing edges can leave two equally small containers; the choice must be
        // deterministic for a given record order rather than map-iteration luck.
        val forest = undercutNestingForest(
            listOf(
                UndercutSpanMm("first", 100f, 300f),
                UndercutSpanMm("second", 100f, 300f),
                UndercutSpanMm("child", 120f, 200f),
            ),
        ).associateBy { it.id }
        assertEquals("first", forest.getValue("child").parentId)
    }

    // ── Ø callout shelf anchors ──

    @Test
    fun `a childless cut anchors at its own midpoint`() {
        val only = UndercutSpanMm("solo", 100f, 300f)
        assertEquals(200f, undercutCalloutAnchorMm(only, listOf(only)), 1e-3f)
        // A neighbour that is not contained changes nothing.
        val sibling = UndercutSpanMm("sib", 400f, 500f)
        assertEquals(200f, undercutCalloutAnchorMm(only, listOf(only, sibling)), 1e-3f)
    }

    @Test
    fun `one mid-span child sends the anchor to the larger shelf`() {
        val parent = UndercutSpanMm("p", 0f, 300f)
        val child = UndercutSpanMm("c", 60f, 100f)
        // Shelves [0,60] and [100,300]; the FWD one is wider.
        assertEquals(200f, undercutCalloutAnchorMm(parent, listOf(parent, child)), 1e-3f)
    }

    @Test
    fun `equal shelves break the tie to the AFT-most`() {
        val parent = UndercutSpanMm("p", 0f, 300f)
        val child = UndercutSpanMm("c", 100f, 200f)
        assertEquals(50f, undercutCalloutAnchorMm(parent, listOf(parent, child)), 1e-3f)
    }

    @Test
    fun `a child flush at the AFT shoulder leaves one FWD shelf`() {
        val parent = UndercutSpanMm("p", 100f, 300f)
        val child = UndercutSpanMm("c", 100f, 160f)
        assertEquals(230f, undercutCalloutAnchorMm(parent, listOf(parent, child)), 1e-3f)
    }

    @Test
    fun `a concentric staircase gives every level its own anchor`() {
        // The bird-foot case: three cuts sharing one span midpoint. Midpoint anchoring put all
        // three leaders on one stem; each must now leave from its own visible floor.
        val spans = listOf(
            UndercutSpanMm("n0", 200f, 440f),
            UndercutSpanMm("n1", 250f, 390f),
            UndercutSpanMm("n2", 290f, 350f),
        )
        assertTrue("the scenario is concentric", spans.all { (it.startMm + it.endMm) / 2f == 320f })
        val anchors = spans.associate { it.id to undercutCalloutAnchorMm(it, spans) }
        assertEquals(225f, anchors.getValue("n0"), 1e-3f)   // [200,250] ties [390,440], AFT wins
        assertEquals(270f, anchors.getValue("n1"), 1e-3f)   // [250,290] ties [350,390]
        assertEquals(320f, anchors.getValue("n2"), 1e-3f)   // childless
        val distinct = anchors.values.map { it }.toSet()
        assertEquals("every level anchors somewhere else", 3, distinct.size)
    }

    @Test
    fun `a parent split by a nested cut gets one anchor per visible piece`() {
        // On-device report: a relief with a deeper cut in the middle printed its Ø on one side
        // only, and the other piece of the same floor read as an unrecorded diameter.
        val parent = UndercutSpanMm("p", 100f, 400f)
        val child = UndercutSpanMm("c", 220f, 260f)
        val anchors = undercutCalloutAnchorsMm(listOf(parent, child))
        assertEquals(listOf(160f, 330f), anchors.getValue("p"))   // AFT piece, then FWD piece
        assertEquals(listOf(240f), anchors.getValue("c"))         // the child: its own midpoint
    }

    @Test
    fun `a concentric staircase labels both sides of every level`() {
        val spans = listOf(
            UndercutSpanMm("n0", 200f, 440f),
            UndercutSpanMm("n1", 250f, 390f),
            UndercutSpanMm("n2", 290f, 350f),
        )
        val anchors = undercutCalloutAnchorsMm(spans)
        assertEquals(listOf(225f, 415f), anchors.getValue("n0"))
        assertEquals(listOf(270f, 370f), anchors.getValue("n1"))
        assertEquals(listOf(320f), anchors.getValue("n2"))
    }

    @Test
    fun `a piece narrower than the minimum is left unlabelled`() {
        // The child sits 2 mm off the AFT shoulder: that sliver is no floor to label, so the
        // parent keeps ONE anchor, on its wide FWD piece.
        val parent = UndercutSpanMm("p", 100f, 400f)
        val child = UndercutSpanMm("c", 102f, 160f)
        val anchors = undercutCalloutAnchorsMm(listOf(parent, child))
        assertEquals(listOf(280f), anchors.getValue("p"))
    }

    @Test
    fun `a cut with no piece wide enough falls back to its single anchor`() {
        // Children leave only slivers (2 mm each side): the fallback is the widest sliver, the
        // same shelf the single-anchor rule picks, so the cut never loses its value.
        val parent = UndercutSpanMm("p", 100f, 300f)
        val child = UndercutSpanMm("c", 102f, 298f)
        val anchors = undercutCalloutAnchorsMm(listOf(parent, child))
        assertEquals(listOf(undercutCalloutAnchorMm(parent, listOf(parent, child))), anchors.getValue("p"))
        // Covered edge to edge: the historical midpoint, once.
        val halves = listOf(parent, UndercutSpanMm("d", 100f, 200f), UndercutSpanMm("e", 200f, 300f))
        assertEquals(listOf(200f), undercutCalloutAnchorsMm(halves).getValue("p"))
    }

    @Test
    fun `a deeper descendant is covered by subtracting its parent`() {
        // n2 lies inside n1, so subtracting n1 alone already removes it — the result must not
        // depend on whether the grandchild is listed.
        val outer = UndercutSpanMm("n0", 200f, 440f)
        val withGrandchild = listOf(outer, UndercutSpanMm("n1", 250f, 390f), UndercutSpanMm("n2", 290f, 350f))
        val without = withGrandchild.dropLast(1)
        assertEquals(
            undercutCalloutAnchorMm(outer, without),
            undercutCalloutAnchorMm(outer, withGrandchild),
            1e-3f,
        )
    }

    @Test
    fun `children covering the whole span fall back to the midpoint`() {
        val parent = UndercutSpanMm("p", 100f, 300f)
        val children = listOf(
            UndercutSpanMm("a", 100f, 200f),
            UndercutSpanMm("b", 200f, 300f),
        )
        assertEquals(200f, undercutCalloutAnchorMm(parent, listOf(parent) + children), 1e-3f)
    }

    @Test
    fun `a partially overlapping sibling is never subtracted`() {
        // Legacy data: neither span contains the other, so both keep their historical midpoints.
        val a = UndercutSpanMm("a", 100f, 300f)
        val b = UndercutSpanMm("b", 200f, 400f)
        val anchors = undercutCalloutAnchorsMm(listOf(a, b))
        assertEquals(listOf(200f), anchors.getValue("a"))
        assertEquals(listOf(300f), anchors.getValue("b"))
    }

    @Test
    fun `stale classifier mirrors the span issue check`() {
        assertFalse(isUndercutStaleOverrun(100f, 50f, 1000f))
        assertTrue(isUndercutStaleOverrun(980f, 50f, 1000f))
    }

    @Test
    fun `clamp trims a span into the shaft extent without mutating length semantics`() {
        val clamped = clampUndercutSpan(startFromAftMm = 980f, lengthMm = 50f, oalMm = 1000f)
        assertEquals(980f, clamped.startMm, 1e-4f)
        assertEquals(1000f, clamped.endMm, 1e-4f)
        assertFalse(clamped.isEmpty)
    }

    @Test
    fun `clamp of a fully outside span is empty`() {
        assertTrue(clampUndercutSpan(1100f, 50f, 1000f).isEmpty)
    }

    // ── Cluster windows ──

    @Test
    fun `single undercut gets one padded window`() {
        val windows = clusterUndercuts(
            listOf(UndercutSpanMm("a", 500f, 540f)), oalMm = 1000f,
        )
        assertEquals(1, windows.size)
        assertEquals(500f - UNDERCUT_WINDOW_PAD_MM, windows[0].startMm, 1e-4f)
        assertEquals(540f + UNDERCUT_WINDOW_PAD_MM, windows[0].endMm, 1e-4f)
        assertEquals(listOf("a"), windows[0].undercutIds)
    }

    @Test
    fun `window padding clamps to the shaft extent`() {
        val windows = clusterUndercuts(
            listOf(UndercutSpanMm("a", 5f, 990f)), oalMm = 1000f,
        )
        assertEquals(0f, windows[0].startMm, 1e-4f)
        assertEquals(1000f, windows[0].endMm, 1e-4f)
    }

    @Test
    fun `nearby undercuts share one window in aft-fwd order`() {
        val windows = clusterUndercuts(
            listOf(
                UndercutSpanMm("mid", 300f, 320f),
                UndercutSpanMm("aft", 200f, 240f),
                UndercutSpanMm("fwd", 320f + UNDERCUT_CLUSTER_GAP_MM, 500f + UNDERCUT_CLUSTER_GAP_MM),
            ),
            oalMm = 2000f,
        )
        assertEquals(1, windows.size)
        assertEquals(listOf("aft", "mid", "fwd"), windows[0].undercutIds)
    }

    @Test
    fun `far apart undercuts get separate windows`() {
        val windows = clusterUndercuts(
            listOf(
                UndercutSpanMm("a", 100f, 140f),
                UndercutSpanMm("b", 140f + UNDERCUT_CLUSTER_GAP_MM + 1f, 900f),
            ),
            oalMm = 2000f,
        )
        assertEquals(2, windows.size)
        assertEquals(listOf("a"), windows[0].undercutIds)
        assertEquals(listOf("b"), windows[1].undercutIds)
        assertTrue(windows[0].endMm < windows[1].startMm)
    }

    @Test
    fun `padded windows that touch are defensively merged`() {
        // Retuned constants where pad overwhelms the gap: spans 10mm apart, gap
        // threshold 5mm (separate clusters), pad 20mm (padded windows overlap).
        val windows = clusterUndercuts(
            listOf(UndercutSpanMm("a", 100f, 120f), UndercutSpanMm("b", 130f, 150f)),
            oalMm = 1000f, gapMm = 5f, padMm = 20f,
        )
        assertEquals(1, windows.size)
        assertEquals(listOf("a", "b"), windows[0].undercutIds)
        assertEquals(80f, windows[0].startMm, 1e-4f)
        assertEquals(170f, windows[0].endMm, 1e-4f)
    }

    @Test
    fun `empty and fully-outside spans yield no windows`() {
        assertTrue(clusterUndercuts(emptyList(), 1000f).isEmpty())
        assertTrue(
            clusterUndercuts(listOf(UndercutSpanMm("a", 500f, 500f)), 1000f).isEmpty(),
        )
    }

    // ── Hit-testing ──

    @Test
    fun `window pick finds the containing window`() {
        val windows = clusterUndercuts(listOf(UndercutSpanMm("a", 500f, 540f)), 1000f)
        assertNotNull(pickUndercutWindowAt(520f, windows))
        assertNull(pickUndercutWindowAt(700f, windows))
    }

    @Test
    fun `undercut pick prefers a span containing the tap over a nearby pad hit`() {
        val spans = listOf(
            UndercutSpanMm("inside", 100f, 200f),
            UndercutSpanMm("padded", 205f, 300f),
        )
        assertEquals("inside", pickUndercutAt(199f, spans, padPx(10f)))
    }

    @Test
    fun `undercut pick takes the innermost span when cuts are nested`() {
        val spans = listOf(
            UndercutSpanMm("parent", 100f, 400f),
            UndercutSpanMm("child", 200f, 260f),
        )
        // Inside the child: the smaller, on-top target wins over the relief around it.
        assertEquals("child", pickUndercutAt(230f, spans, padPx(10f)))
        // Inside the parent only.
        assertEquals("parent", pickUndercutAt(150f, spans, padPx(10f)))
    }

    @Test
    fun `undercut pick falls back to the pad zone and breaks ties by nearer edge`() {
        val spans = listOf(
            UndercutSpanMm("left", 100f, 200f),
            UndercutSpanMm("right", 210f, 300f),
        )
        assertEquals("right", pickUndercutAt(207f, spans, padPx(10f)))
        assertNull(pickUndercutAt(500f, spans, padPx(10f)))
    }

    // ── Strips: liner-anchored vs free ──

    private val liners = listOf(UndercutLinerSpan("liner1", 600f, 1000f))

    @Test
    fun `cut is assigned to the liner with the largest overlap`() {
        assertEquals("liner1", assignUndercutLiner(UndercutSpanMm("a", 700f, 760f), liners))
        // Crossing the FWD edge but mostly inside.
        assertEquals("liner1", assignUndercutLiner(UndercutSpanMm("b", 960f, 1030f), liners))
        // Mostly outside: 990-1000 inside (10) vs 1000-1090 outside (90) — still liner1's
        // (it is the only overlapping liner); no overlap at all → null.
        assertEquals("liner1", assignUndercutLiner(UndercutSpanMm("c", 990f, 1090f), liners))
        assertNull(assignUndercutLiner(UndercutSpanMm("d", 100f, 160f), liners))
    }

    @Test
    fun `cut crossing two liners belongs to the one holding most of it`() {
        val two = listOf(
            UndercutLinerSpan("aftLiner", 0f, 500f),
            UndercutLinerSpan("fwdLiner", 500f, 1200f),
        )
        assertEquals("fwdLiner", assignUndercutLiner(UndercutSpanMm("x", 480f, 580f), two))
        assertEquals("aftLiner", assignUndercutLiner(UndercutSpanMm("y", 420f, 520f), two))
    }

    @Test
    fun `liner strip covers the whole liner and chains at its edges`() {
        val strips = buildUndercutStrips(
            listOf(UndercutSpanMm("a", 700f, 760f)), liners, oalMm = 2000f,
        )
        assertEquals(1, strips.size)
        val s = strips[0] as UndercutStrip.LinerStrip
        assertEquals(600f - UNDERCUT_WINDOW_PAD_MM, s.drawStartMm, 1e-3f)
        assertEquals(1000f + UNDERCUT_WINDOW_PAD_MM, s.drawEndMm, 1e-3f)
        assertEquals(600f, s.chainStartMm, 1e-3f)
        assertEquals(1000f, s.chainEndMm, 1e-3f)
        assertEquals(listOf("a"), s.undercutIds)
    }

    @Test
    fun `cut overhanging the liner edge extends both draw and chain ranges`() {
        val strips = buildUndercutStrips(
            listOf(UndercutSpanMm("a", 960f, 1030f)), liners, oalMm = 2000f,
        )
        val s = strips[0] as UndercutStrip.LinerStrip
        assertEquals(1030f, s.chainEndMm, 1e-3f)
        assertEquals(1030f + UNDERCUT_WINDOW_PAD_MM, s.drawEndMm, 1e-3f)
        assertEquals(600f, s.chainStartMm, 1e-3f)
    }

    @Test
    fun `bare-shaft cuts still cluster into free strips beside liner strips`() {
        val strips = buildUndercutStrips(
            listOf(
                UndercutSpanMm("bare", 100f, 160f),
                UndercutSpanMm("inLiner", 700f, 760f),
            ),
            liners, oalMm = 2000f,
        )
        assertEquals(2, strips.size)
        assertTrue(strips[0] is UndercutStrip.FreeStrip)
        assertTrue(strips[1] is UndercutStrip.LinerStrip)
        assertEquals(listOf("bare"), strips[0].undercutIds)
        // Free strip keeps the original window chain behavior: chain == draw range.
        assertEquals(strips[0].drawStartMm, strips[0].chainStartMm, 1e-4f)
    }

    @Test
    fun `liner with no cuts builds an empty authoring strip on demand`() {
        val s = linerStripFor(liners[0], emptyList(), oalMm = 2000f)
        assertTrue(s.undercutIds.isEmpty())
        assertEquals(600f, s.chainStartMm, 1e-3f)
        assertEquals(1000f, s.chainEndMm, 1e-3f)
    }

    @Test
    fun `strip pick finds the containing strip aft-first`() {
        val strips = buildUndercutStrips(
            listOf(UndercutSpanMm("a", 700f, 760f)), liners, oalMm = 2000f,
        )
        assertNotNull(pickUndercutStripAt(600f, strips))
        assertNull(pickUndercutStripAt(200f, strips))
    }

    // ── Drawn depth exaggeration (normalized to the sheet's deepest cut) ──

    /** Plain round stock of [diaMm] over the whole shaft — the exaggeration scenarios' surface. */
    private fun stockSegs(diaMm: Float, oalMm: Float = EX_OAL_MM): List<SurfaceSeg> =
        listOf(SurfaceSeg(0f, oalMm, diaMm, diaMm))

    /** A cut of [depthMm] Ø-reduction below [surfaceDiaMm], placed at [startMm]. */
    private fun cutOfDepth(id: String, depthMm: Float, surfaceDiaMm: Float, startMm: Float) =
        Undercut(id = id, startFromAftMm = startMm, lengthMm = 50f, diaMm = surfaceDiaMm - depthMm)

    /** Drawn depth as a fraction of the local surface Ø — what the eye reads on the sheet. */
    private fun drawnFrac(diaMm: Float, surfaceDiaMm: Float, deepestMm: Float, ex: Float): Float =
        (surfaceDiaMm - normalizedNotchFloorDiaMm(diaMm, surfaceDiaMm, deepestMm, ex)) / surfaceDiaMm

    @Test
    fun `the sheet's deepest measured cut draws at exactly the exaggeration fraction`() {
        val surface = 10f * 25.4f
        val deep = cutOfDepth("deep", depthMm = 12.7f, surfaceDiaMm = surface, startMm = 200f)
        val deepest = deepestUndercutDepthMm(listOf(deep), stockSegs(surface), EX_OAL_MM)
        assertEquals(12.7f, deepest, 1e-3f)

        assertEquals(0.25f, drawnFrac(deep.diaMm, surface, deepest, 0.25f), 1e-4f)
    }

    @Test
    fun `a half-as-deep cut draws at the square root of its depth ratio`() {
        // Ordering WITHIN a sheet stays honest but the ratio is sqrt-compressed, so a
        // shallow cut never reads as a hairline next to a deep one.
        val surface = 10f * 25.4f
        val deep = cutOfDepth("deep", depthMm = 12.7f, surfaceDiaMm = surface, startMm = 200f)
        val half = cutOfDepth("half", depthMm = 6.35f, surfaceDiaMm = surface, startMm = 600f)
        val deepest = deepestUndercutDepthMm(listOf(deep, half), stockSegs(surface), EX_OAL_MM)

        val deepFrac = drawnFrac(deep.diaMm, surface, deepest, 0.25f)
        val halfFrac = drawnFrac(half.diaMm, surface, deepest, 0.25f)
        assertEquals(0.25f, deepFrac, 1e-4f)
        assertEquals(deepFrac * kotlin.math.sqrt(0.5f), halfFrac, 1e-4f)
        assertTrue(halfFrac < deepFrac)
    }

    @Test
    fun `shallow cuts in one liner stay readable when a deeper cut lives in another`() {
        // On-device report: two ~0.005"-deep cuts in the MID liner drew as hairlines
        // because the FWD liner's 0.05"-deep cut owned the sheet reference. Every
        // measured cut draws at no less than the minimum share of the exaggeration.
        val surface = 7f * 25.4f
        val shallow = cutOfDepth("mid1", depthMm = 0.005f * 25.4f, surfaceDiaMm = surface, startMm = 200f)
        val deeper = cutOfDepth("fwd", depthMm = 0.05f * 25.4f, surfaceDiaMm = surface, startMm = 800f)
        val deepest = deepestUndercutDepthMm(listOf(shallow, deeper), stockSegs(surface), EX_OAL_MM)

        val shallowFrac = drawnFrac(shallow.diaMm, surface, deepest, 0.25f)
        assertTrue(
            "shallow frac $shallowFrac must be >= min share of the exaggeration",
            shallowFrac >= 0.25f * UNDERCUT_MIN_SHARE_OF_EXAGGERATION - 1e-4f,
        )
        assertEquals(0.25f, drawnFrac(deeper.diaMm, surface, deepest, 0.25f), 1e-4f)
    }

    @Test
    fun `sheets with very different absolute depths read alike`() {
        // The point of normalizing: a sheet whose worst cut is 1" deep and one whose worst is
        // 1/4" deep must print their deepest cut at the SAME drawn depth (on-device request).
        val surface = 10f * 25.4f
        val inchSheet = listOf(cutOfDepth("a", 25.4f, surface, 200f))
        val quarterSheet = listOf(cutOfDepth("b", 6.35f, surface, 200f))

        val inchDeepest = deepestUndercutDepthMm(inchSheet, stockSegs(surface), EX_OAL_MM)
        val quarterDeepest = deepestUndercutDepthMm(quarterSheet, stockSegs(surface), EX_OAL_MM)

        assertEquals(
            drawnFrac(inchSheet[0].diaMm, surface, inchDeepest, 0.25f),
            drawnFrac(quarterSheet[0].diaMm, surface, quarterDeepest, 0.25f),
            1e-4f,
        )
    }

    @Test
    fun `a cut deeper than the cap is never drawn shallower than its true depth`() {
        // True depth frac 0.5 exceeds the 0.25 cap — the drawn floor stays the true floor.
        val surface = 114.3f
        assertEquals(
            57.15f,
            normalizedNotchFloorDiaMm(57.15f, surface, deepestDepthMm = 57.15f, exaggerationFrac = 0.25f),
            1e-3f,
        )
    }

    @Test
    fun `zero exaggeration draws measured cuts at true scale`() {
        val surface = 10f * 25.4f
        val cut = cutOfDepth("c", depthMm = 12.7f, surfaceDiaMm = surface, startMm = 200f)
        val deepest = deepestUndercutDepthMm(listOf(cut), stockSegs(surface), EX_OAL_MM)

        assertEquals(
            cut.diaMm,
            normalizedNotchFloorDiaMm(cut.diaMm, surface, deepest, exaggerationFrac = 0f),
            1e-3f,
        )
    }

    @Test
    fun `a placeholder cut draws at half the exaggeration and never below the visibility floor`() {
        val surface = 10f * 25.4f
        assertEquals(
            0.25f * UNDERCUT_PLACEHOLDER_OF_EXAGGERATION,
            drawnFrac(0f, surface, deepestMm = 12.7f, ex = 0.25f),
            1e-4f,
        )
        // At true scale a placeholder would vanish, so it keeps the visibility floor.
        assertEquals(
            UNDERCUT_PLACEHOLDER_MIN_DRAWN_FRAC,
            drawnFrac(0f, surface, deepestMm = 12.7f, ex = 0f),
            1e-4f,
        )
    }

    @Test
    fun `placeholder cuts are excluded from the deepest-depth reference`() {
        // An unmeasured cut must not be able to squash the real ones: only measured cuts
        // (and only where material was actually removed) set the normalization reference.
        val surface = 10f * 25.4f
        val cuts = listOf(
            Undercut(id = "placeholder", startFromAftMm = 200f, lengthMm = 50f, diaMm = 0f),
            cutOfDepth("measured", depthMm = 6.35f, surfaceDiaMm = surface, startMm = 600f),
            // Ø at the surface: nothing removed, so it contributes no depth either.
            Undercut(id = "no-cut", startFromAftMm = 900f, lengthMm = 50f, diaMm = surface),
        )
        assertEquals(6.35f, deepestUndercutDepthMm(cuts, stockSegs(surface), EX_OAL_MM), 1e-3f)
    }

    @Test
    fun `a hairline cut on a ten inch shaft draws at the full exaggeration when it is the only cut`() {
        // Sizing context: shafts run up to ~10" Ø, where a 1/16" Ø-reduction is 0.6% of the
        // diameter — invisible at true scale. As the sheet's deepest cut it sets the
        // reference, so it draws at the full chosen exaggeration.
        val surface = 10f * 25.4f
        val cut = cutOfDepth("hairline", depthMm = (1f / 16f) * 25.4f, surfaceDiaMm = surface, startMm = 200f)
        val deepest = deepestUndercutDepthMm(listOf(cut), stockSegs(surface), EX_OAL_MM)

        assertEquals(0.25f, drawnFrac(cut.diaMm, surface, deepest, 0.25f), 1e-4f)
    }

    @Test
    fun `exaggeration degrades safely on degenerate inputs`() {
        // No surface to reference: the Ø passes straight through.
        assertEquals(50f, normalizedNotchFloorDiaMm(50f, 0f, 10f, 0.25f), 1e-4f)
        // A Ø at or above the surface removed no material (the card's warning case), so there
        // is no depth to exaggerate and the notch degenerates to the surface itself.
        assertEquals(114.3f, normalizedNotchFloorDiaMm(120f, 114.3f, 10f, 0.25f), 1e-4f)
        // An empty sheet has no reference depth at all.
        assertEquals(0f, deepestUndercutDepthMm(emptyList(), stockSegs(114.3f), EX_OAL_MM), 1e-4f)
    }

    // ── Nested cuts: the drawn floors stack ──

    @Test
    fun `the deepest-cut reference ignores nested children`() {
        // A child's depth is relative to its parent's floor, so measuring it from the base
        // surface would hand the sheet a reference no cut is drawn against — and squash every
        // top-level cut toward the minimum share.
        val surface = 200f
        val parent = Undercut(id = "p", startFromAftMm = 200f, lengthMm = 200f, diaMm = 190f)
        val child = Undercut(id = "c", startFromAftMm = 250f, lengthMm = 60f, diaMm = 100f)
        assertEquals(
            10f,
            deepestUndercutDepthMm(listOf(parent, child), stockSegs(surface), EX_OAL_MM),
            1e-3f,
        )
    }

    @Test
    fun `a nested cut always draws a visible step below its parent floor`() {
        // The collapse case: both cuts are shallow from the BASE surface, so the min-share clamp
        // would give them the same drawn depth and flatten the stair. Measuring the child against
        // the parent's floor keeps the step at every slider value, true scale included.
        val surface = 200f
        val parentDia = 199f
        val childDia = 198.5f
        val deepest = 1f
        listOf(0f, 0.05f, UNDERCUT_EXAGGERATION_MAX_FRAC).forEach { ex ->
            val parentDrawn = normalizedNotchFloorDiaMm(parentDia, surface, deepest, ex)
            val childDrawn = nestedNotchFloorDiaMm(childDia, parentDia, parentDrawn, deepest, ex)
            assertTrue("ex=$ex must keep a visible step", childDrawn < parentDrawn - 1e-4f)
            assertTrue("ex=$ex must never draw shallower than true", childDrawn <= childDia + 1e-3f)
        }
    }

    @Test
    fun `the nested depth cap bites only when the parent floor is already deep`() {
        // Parent drawn at Ø 20 (heavily exaggerated) out of a true floor of 100: the child's
        // exaggerated relative depth of 25 would punch through it, so the cap holds it to 75% of
        // the parent's DRAWN floor.
        assertEquals(
            5f,
            nestedNotchFloorDiaMm(
                childDiaMm = 99f, parentTrueFloorDiaMm = 100f, parentDrawnFloorDiaMm = 20f,
                deepestDepthMm = 1f, exaggerationFrac = 0.25f,
            ),
            1e-3f,
        )
    }

    @Test
    fun `a truly deeper nested cut overrides the depth cap`() {
        // True relative depth 18 > the cap's 15: truth beats prettiness, and the child draws at
        // exactly its true relative depth rather than the capped 5.
        assertEquals(
            2f,
            nestedNotchFloorDiaMm(
                childDiaMm = 82f, parentTrueFloorDiaMm = 100f, parentDrawnFloorDiaMm = 20f,
                deepestDepthMm = 25f, exaggerationFrac = 0.25f,
            ),
            1e-3f,
        )
    }

    @Test
    fun `nesting stacks recursively at level 2`() {
        val surface = 200f
        val ex = UNDERCUT_EXAGGERATION_MAX_FRAC
        val deepest = 10f
        val l0Dia = 190f
        val l1Dia = 186f
        val l2Dia = 183f
        val l0 = normalizedNotchFloorDiaMm(l0Dia, surface, deepest, ex)
        val l1 = nestedNotchFloorDiaMm(l1Dia, l0Dia, l0, deepest, ex)
        val l2 = nestedNotchFloorDiaMm(l2Dia, l1Dia, l1, deepest, ex)
        assertTrue("level 1 must step below level 0", l1 < l0 - 1e-4f)
        assertTrue("level 2 must step below level 1", l2 < l1 - 1e-4f)
        assertTrue("every step stays on the shaft", l2 > 0f)
        assertTrue("never shallower than true", l2 <= l2Dia + 1e-3f)
    }

    @Test
    fun `a placeholder child still draws a step inside its parent`() {
        // Ø 0 — placed but not measured: the symbolic floor is taken off the PARENT's floor, so
        // the cut is visible and tappable inside the relief rather than at the shaft surface.
        val drawn = nestedNotchFloorDiaMm(
            childDiaMm = 0f, parentTrueFloorDiaMm = 100f, parentDrawnFloorDiaMm = 100f,
            deepestDepthMm = 1f, exaggerationFrac = 0.25f,
        )
        assertTrue(drawn < 100f - 1e-4f)
        assertTrue(drawn > 0f)
    }

    // ── Placeholder Ø ──

    @Test
    fun `entered dia is used verbatim and zero dia gets the symbolic floor`() {
        assertEquals(101.6f, effectiveNotchDiaMm(101.6f, 114.3f), 1e-4f)
        assertEquals(
            114.3f * UNDERCUT_PLACEHOLDER_DEPTH_FRAC,
            effectiveNotchDiaMm(0f, 114.3f),
            1e-4f,
        )
    }

    /** The pad parameter is mm in shaft space; named helper only for test readability. */
    private fun padPx(mm: Float): Float = mm

    // ── Butt-against presets ──

    @Test
    fun `start at end of puts the draft's AFT end on the other cut's FWD end`() {
        assertEquals(150f, undercutStartAtEndOf(otherEndMm = 150f), 0f)
    }

    @Test
    fun `end at start of puts the draft's FWD end on the other cut's AFT end`() {
        assertEquals(80f, undercutEndAtStartOf(otherStartMm = 100f, lengthMm = 20f), 0f)
    }

    @Test
    fun `a butted draft touches its neighbour and clears the overlap check`() {
        val others = listOf(UndercutSpanMm("other", 100f, 150f))
        assertNull(undercutOverlapIssue(undercutStartAtEndOf(150f), 30f, others))
        assertNull(undercutOverlapIssue(undercutEndAtStartOf(100f, 30f), 30f, others))
        // Both started out intruding — the preset is what made them clear.
        assertNotNull(undercutOverlapIssue(140f, 30f, others))
        assertNotNull(undercutOverlapIssue(90f, 30f, others))
    }

    // ── Naming the overlap partner ──

    @Test
    fun `the overlap partner is the first cut the span partially overlaps`() {
        val others = listOf(
            UndercutSpanMm("clear", 0f, 50f),
            UndercutSpanMm("host", 100f, 600f),
            UndercutSpanMm("hit", 450f, 550f),
        )
        // Nested in "host", partially into "hit": only "hit" blocks.
        assertEquals("hit", undercutOverlapPartnerId(500f, 80f, others))
        assertEquals("hit", undercutOverlapPartnerId(500f, 100f, others))
        assertNull(undercutOverlapPartnerId(200f, 50f, others))
        assertNull(undercutOverlapPartnerId(50f, 50f, others))
    }

    @Test
    fun `the overlap message names the partner or says it is off this strip`() {
        assertEquals("Overlaps cut 3 — sit fully inside it or fully clear", undercutOverlapMessage(3))
        assertEquals(
            "Overlaps a cut outside this strip — sit fully inside it or fully clear",
            undercutOverlapMessage(null),
        )
    }

    // ── One tolerance for containment and touching ──

    @Test
    fun `containment holds through sub-touch-eps drift`() {
        val outer = UndercutSpanMm("o", 200f, 300f)
        // 0.005 mm past the outer's AFT shoulder: more than the bounds eps, inside the touch eps.
        val drifted = UndercutSpanMm("i", 199.995f, 240f)
        assertTrue(undercutSpanContains(outer, drifted))
        assertEquals("o", undercutNestingForest(listOf(outer, drifted)).first { it.id == "i" }.parentId)
        // The confirm check and the overlap band agree: nested, so neither blocks.
        assertNull(undercutOverlapIssue(199.995f, 40.005f, listOf(outer)))
        assertTrue(undercutPartialOverlapsMm(199.995f, 40.005f, listOf(outer)).isEmpty())
        // A real intrusion past the slop is still a partial overlap.
        assertNotNull(undercutOverlapIssue(199.9f, 40.1f, listOf(outer)))
        assertTrue(undercutPartialOverlapsMm(199.9f, 40.1f, listOf(outer)).isNotEmpty())
    }

    @Test
    fun `a duplicate span within the touch eps stays blocked`() {
        val outer = UndercutSpanMm("o", 200f, 300f)
        assertFalse(undercutSpanContains(outer, UndercutSpanMm("d", 200.005f, 299.995f)))
        assertNotNull(undercutOverlapIssue(200.005f, 99.99f, listOf(outer)))
    }

    // ── Overlap bands ──

    @Test
    fun `no overlap gives no band`() {
        val others = listOf(UndercutSpanMm("a", 100f, 150f))
        assertTrue(undercutPartialOverlapsMm(200f, 20f, others).isEmpty())
        assertTrue(undercutPartialOverlapsMm(200f, 20f, emptyList()).isEmpty())
    }

    @Test
    fun `a partial overlap gives the intersection`() {
        val others = listOf(UndercutSpanMm("a", 100f, 150f))
        assertEquals(listOf(130f to 150f), undercutPartialOverlapsMm(130f, 40f, others))
        assertEquals(listOf(100f to 110f), undercutPartialOverlapsMm(80f, 30f, others))
    }

    @Test
    fun `containment either way and touching give no band`() {
        val others = listOf(UndercutSpanMm("a", 100f, 150f))
        assertTrue(undercutPartialOverlapsMm(110f, 20f, others).isEmpty())   // inside
        assertTrue(undercutPartialOverlapsMm(50f, 200f, others).isEmpty())   // around
        assertTrue(undercutPartialOverlapsMm(150f, 20f, others).isEmpty())   // touching FWD
        assertTrue(undercutPartialOverlapsMm(80f, 20f, others).isEmpty())    // touching AFT
    }

    @Test
    fun `two partners give two bands`() {
        val others = listOf(UndercutSpanMm("a", 100f, 150f), UndercutSpanMm("b", 200f, 250f))
        assertEquals(
            listOf(140f to 150f, 200f to 210f),
            undercutPartialOverlapsMm(140f, 70f, others),
        )
    }

    @Test
    fun `bands and the blocking check agree on a duplicate span`() {
        val others = listOf(UndercutSpanMm("a", 100f, 150f))
        assertNotNull(undercutOverlapIssue(100f, 50f, others))
        assertEquals(listOf(100f to 150f), undercutPartialOverlapsMm(100f, 50f, others))
    }

    // ── Buried liner edges (a cut run past the liner's end into the body) ──

    @Test
    fun `a cut ending at the liner edge leaves the edge a datum`() {
        assertFalse(undercutLinerEdgeBuried(500f, listOf(UndercutSpanMm("a", 500f, 600f))))
        assertFalse(undercutLinerEdgeBuried(1500f, listOf(UndercutSpanMm("a", 1400f, 1500f))))
    }

    @Test
    fun `a cut covering the liner end without crossing it leaves the edge a datum`() {
        // The whole liner trimmed: both ends sit exactly on the edges, still a step to the body.
        assertFalse(undercutLinerEdgeBuried(500f, listOf(UndercutSpanMm("a", 500f, 1500f))))
        assertFalse(undercutLinerEdgeBuried(1500f, listOf(UndercutSpanMm("a", 500f, 1500f))))
    }

    @Test
    fun `a cut crossing the liner edge buries it`() {
        assertTrue(undercutLinerEdgeBuried(500f, listOf(UndercutSpanMm("a", 450f, 600f))))
        assertTrue(undercutLinerEdgeBuried(1500f, listOf(UndercutSpanMm("a", 1400f, 1550f))))
    }

    @Test
    fun `a crossing within eps does not bury the edge`() {
        val tiny = UNDERCUT_TOUCH_EPS_MM * 0.5f
        assertFalse(undercutLinerEdgeBuried(500f, listOf(UndercutSpanMm("a", 500f - tiny, 600f))))
        assertFalse(undercutLinerEdgeBuried(1500f, listOf(UndercutSpanMm("a", 1400f, 1500f + tiny))))
    }

    @Test
    fun `buried edges are listed aft first and only when buried`() {
        val spans = listOf(UndercutSpanMm("a", 450f, 600f), UndercutSpanMm("b", 1400f, 1550f))
        assertEquals(listOf(500f, 1500f), buriedUndercutLinerEdgesMm(500f, 1500f, spans))
        assertEquals(emptyList<Float>(), buriedUndercutLinerEdgesMm(500f, 1500f, listOf(UndercutSpanMm("a", 500f, 600f))))
    }

    @Test
    fun `a buried referenced edge falls back to the nearer SET`() {
        val liner = UndercutLinerSpan("L", 500f, 1500f)
        // AFT edge buried by the cut itself; its span sits in the AFT half of SET 100..1900.
        val aftCut = Undercut(
            id = "u", startFromAftMm = 450f, lengthMm = 150f, diaMm = 0f,
            authoredReference = UndercutReference.LINER_AFT, referenceLinerId = "L",
        )
        val aftSpans = listOf(UndercutSpanMm("u", 450f, 600f))
        assertEquals(
            UndercutReference.AFT_SET,
            effectiveUndercutReference(aftCut, listOf(liner), aftSpans, aftSetXMm = 100f, fwdSetXMm = 1900f),
        )
        // The same rule picks the FWD S.E.T. when the cut sits nearer it.
        val fwdLiner = UndercutLinerSpan("L", 1200f, 1700f)
        val fwdCut = aftCut.copy(startFromAftMm = 1150f, lengthMm = 150f)
        assertEquals(
            UndercutReference.FWD_SET,
            effectiveUndercutReference(
                fwdCut, listOf(fwdLiner), listOf(UndercutSpanMm("u", 1150f, 1300f)),
                aftSetXMm = 100f, fwdSetXMm = 1900f,
            ),
        )
        // A LINER_FWD reference reads its own (FWD) edge: buried by ANOTHER cut on the sheet.
        val fwdRefCut = aftCut.copy(
            startFromAftMm = 1300f, lengthMm = 100f, authoredReference = UndercutReference.LINER_FWD,
        )
        assertEquals(
            UndercutReference.FWD_SET,
            effectiveUndercutReference(
                fwdRefCut, listOf(liner),
                listOf(UndercutSpanMm("u", 1300f, 1400f), UndercutSpanMm("o", 1450f, 1600f)),
                aftSetXMm = 100f, fwdSetXMm = 1900f,
            ),
        )
    }

    @Test
    fun `an intact referenced edge keeps the liner reference`() {
        val liner = UndercutLinerSpan("L", 500f, 1500f)
        val cut = Undercut(
            id = "u", startFromAftMm = 500f, lengthMm = 100f, diaMm = 0f,
            authoredReference = UndercutReference.LINER_AFT, referenceLinerId = "L",
        )
        assertEquals(
            UndercutReference.LINER_AFT,
            effectiveUndercutReference(
                cut, listOf(liner), listOf(UndercutSpanMm("u", 500f, 600f)),
                aftSetXMm = 100f, fwdSetXMm = 1900f,
            ),
        )
        // The FWD edge buried does not touch a LINER_AFT reference.
        assertEquals(
            UndercutReference.LINER_AFT,
            effectiveUndercutReference(
                cut, listOf(liner),
                listOf(UndercutSpanMm("u", 500f, 600f), UndercutSpanMm("o", 1400f, 1600f)),
                aftSetXMm = 100f, fwdSetXMm = 1900f,
            ),
        )
        // No spans (the default) skips the check: the two-argument form is unchanged.
        assertEquals(
            UndercutReference.LINER_AFT,
            effectiveUndercutReference(cut.copy(startFromAftMm = 450f, lengthMm = 150f), listOf(liner)),
        )
    }

    @Test
    fun `SET references are unchanged by a buried edge`() {
        val liner = UndercutLinerSpan("L", 500f, 1500f)
        val spans = listOf(UndercutSpanMm("u", 450f, 600f))
        listOf(UndercutReference.AFT_SET, UndercutReference.FWD_SET).forEach { ref ->
            val cut = Undercut(
                id = "u", startFromAftMm = 450f, lengthMm = 150f, diaMm = 0f,
                authoredReference = ref, referenceLinerId = "",
            )
            assertEquals(
                ref,
                effectiveUndercutReference(cut, listOf(liner), spans, aftSetXMm = 100f, fwdSetXMm = 1900f),
            )
        }
    }

    private companion object {
        /** Shaft extent used by the exaggeration scenarios (long enough for several cuts). */
        const val EX_OAL_MM = 2000f
    }

    // ── Parent-cut datum ──

    @Test
    fun `parent AFT and FWD conversions round trip against the parent's span`() {
        val ps = 600f
        val pe = 800f
        val aft = undercutStartToCanonicalMm(
            UndercutReference.PARENT_AFT, 60f, 50f, 100f, 1900f, parentStartMm = ps, parentEndMm = pe,
        )
        assertEquals(660f, aft, 1e-4f)
        assertEquals(
            60f,
            canonicalToUndercutStartMm(
                UndercutReference.PARENT_AFT, aft, 50f, 100f, 1900f, parentStartMm = ps, parentEndMm = pe,
            ),
            1e-4f,
        )
        val fwd = undercutStartToCanonicalMm(
            UndercutReference.PARENT_FWD, 90f, 50f, 100f, 1900f, parentStartMm = ps, parentEndMm = pe,
        )
        assertEquals(660f, fwd, 1e-4f)
        assertEquals(
            90f,
            canonicalToUndercutStartMm(
                UndercutReference.PARENT_FWD, fwd, 50f, 100f, 1900f, parentStartMm = ps, parentEndMm = pe,
            ),
            1e-4f,
        )
        // The liner terms are not read by the parent references, and vice versa.
        assertEquals(
            660f,
            undercutStartToCanonicalMm(
                UndercutReference.PARENT_AFT, 60f, 50f, 100f, 1900f,
                linerStartMm = 9999f, linerEndMm = 9999f, parentStartMm = ps, parentEndMm = pe,
            ),
            1e-4f,
        )
    }

    @Test
    fun `a length change under Parent FWD pins the FWD end and under Parent AFT the AFT end`() {
        // Cut 660..710 inside the parent 600..800, re-lengthened to 80.
        val fwd = undercutCanonicalForNewLength(
            UndercutReference.PARENT_FWD, 660f, 50f, 80f, 100f, 1900f,
            parentStartMm = 600f, parentEndMm = 800f,
        )
        assertEquals("the FWD end stays at 710", 710f, fwd + 80f, 1e-4f)
        val aft = undercutCanonicalForNewLength(
            UndercutReference.PARENT_AFT, 660f, 50f, 80f, 100f, 1900f,
            parentStartMm = 600f, parentEndMm = 800f,
        )
        assertEquals(660f, aft, 1e-4f)
    }

    // ── The on-device job (fixture `undercut_polar_ranger_stbd.shaft`) ──

    @Test
    fun `the on-device job's five cuts are each clear or nested against every other`() {
        val raw = requireNotNull(javaClass.classLoader?.getResource("fixtures/undercut_polar_ranger_stbd.shaft"))
            .readText()
        val cuts = com.android.shaftschematic.doc.ShaftDocCodec.decode(raw).undercutRecord.undercuts
        assertEquals(5, cuts.size)
        val spans = cuts.map { UndercutSpanMm(it.id, it.startFromAftMm, it.startFromAftMm + it.lengthMm) }
        cuts.forEach { u ->
            val others = spans.filter { it.id != u.id }
            assertNull(
                "cut ${u.id} must not be blocked against the others",
                undercutOverlapIssue(u.startFromAftMm, u.lengthMm, others),
            )
            assertNull(undercutOverlapPartnerId(u.startFromAftMm, u.lengthMm, others))
            assertTrue(undercutPartialOverlapsMm(u.startFromAftMm, u.lengthMm, others).isEmpty())
        }
        // E ends where its parent C starts (touch within eps), D sits inside C.
        val e = spans.single { it.id.startsWith("de3bedb8") }
        val c = spans.single { it.id.startsWith("ce3aa5f1") }
        val d = spans.single { it.id.startsWith("748c4863") }
        assertEquals(c.startMm, e.endMm, UNDERCUT_TOUCH_EPS_MM)
        assertTrue(undercutSpanContains(c, d))
    }

    // ── Chained cuts ride their liner's strip; window-end breaks clear cut shoulders ──

    private fun jobFixture() = com.android.shaftschematic.doc.ShaftDocCodec.decode(
        requireNotNull(javaClass.classLoader?.getResource("fixtures/undercut_polar_ranger_stbd.shaft")).readText(),
    )

    private fun jobSpans(): List<UndercutSpanMm> = jobFixture().undercutRecord.undercuts
        .map { UndercutSpanMm(it.id, it.startFromAftMm, it.startFromAftMm + it.lengthMm) }

    private fun jobLiners(): List<UndercutLinerSpan> = jobFixture().spec.liners
        .map { UndercutLinerSpan(it.id, it.startFromAftMm, it.startFromAftMm + it.lengthMm) }

    private fun jobSegs(): List<SurfaceSeg> {
        val spec = jobFixture().spec
        val resolved = com.android.shaftschematic.ui.resolved.resolveComponents(spec)
        return com.android.shaftschematic.ui.resolved.surfaceSegsFrom(
            resolved, com.android.shaftschematic.ui.resolved.bodyBlends(spec, resolved),
        )
    }

    private val fwdLinerPrefix = "02d0b768"

    @Test
    fun `the job's edge-crossing cuts ride the FWD liner's strip`() {
        val spec = jobFixture().spec
        val strips = buildUndercutStrips(jobSpans(), jobLiners(), spec.overallLengthMm, segs = jobSegs())
        val strip = strips.filterIsInstance<UndercutStrip.LinerStrip>()
            .single { it.linerId.startsWith(fwdLinerPrefix) }
        assertEquals(jobSpans().map { it.id }.toSet(), strip.undercutIds.toSet())
        assertTrue(strips.none { it is UndercutStrip.FreeStrip })
    }

    @Test
    fun `a body cut chained to a liner's cut joins that liner, a far one does not`() {
        val spans = jobSpans()
        val e = spans.single { it.id.startsWith("de3bedb8") }
        val touching = UndercutSpanMm("touch", e.startMm - 40f, e.startMm)
        val chained = UndercutSpanMm("chain", touching.startMm - 30f, touching.startMm + 0.005f)
        val far = UndercutSpanMm("far", e.startMm - 1000f, e.startMm - 980f)
        val all = spans + listOf(far, chained, touching)
        val linerOf = assignUndercutLiners(all, jobLiners())
        val fwdLiner = jobLiners().single { it.id.startsWith(fwdLinerPrefix) }.id
        assertEquals(fwdLiner, linerOf["touch"])
        assertEquals(fwdLiner, linerOf["chain"])
        assertNull(linerOf["far"])

        val oal = jobFixture().spec.overallLengthMm
        val strip = buildUndercutStrips(all, jobLiners(), oal, segs = jobSegs())
            .filterIsInstance<UndercutStrip.LinerStrip>().single { it.linerId == fwdLiner }
        assertTrue("touch" in strip.undercutIds && "chain" in strip.undercutIds)
        assertTrue("far" !in strip.undercutIds)
        assertEquals(chained.startMm, strip.chainStartMm, 1e-3f)
    }

    @Test
    fun `the job's strip clears A's FWD shoulder and E's AFT shoulder by the break room`() {
        val oal = jobFixture().spec.overallLengthMm
        val segs = jobSegs()
        val strip = buildUndercutStrips(jobSpans(), jobLiners(), oal, segs = segs)
            .filterIsInstance<UndercutStrip.LinerStrip>().single { it.linerId.startsWith(fwdLinerPrefix) }
        val a = jobSpans().single { it.id.startsWith("eb9709da") }
        val e = jobSpans().single { it.id.startsWith("de3bedb8") }
        val bodyDia = 228.6f
        assertTrue(strip.drawEndMm - a.endMm >= undercutBreakRoomMm(bodyDia) - 0.01f)
        assertTrue(e.startMm - strip.drawStartMm >= undercutBreakRoomMm(bodyDia) - 0.01f)
    }

    @Test
    fun `a window end inside a cut moves outward past the cut's shoulder`() {
        val segs = listOf(seg(0f, 2000f, 228.6f))
        val room = undercutBreakRoomMm(228.6f)
        val cut = listOf(UndercutSpanMm("c", 1000f, 1100f))
        // Without cuts the body hosts the break where it is.
        assertEquals(1050f to 1900f, withUndercutBreakRoom(1050f, 1900f, segs, 2000f))
        val (s, _) = withUndercutBreakRoom(1050f, 1900f, segs, 2000f, cut)
        assertEquals(1000f - room, s, 1e-3f)
        val (_, e) = withUndercutBreakRoom(100f, 1050f, segs, 2000f, cut)
        assertEquals(1100f + room, e, 1e-3f)
    }

    @Test
    fun `a window end within the room of a cut's shoulder moves outward to clear it`() {
        val segs = listOf(seg(0f, 2000f, 228.6f))
        val room = undercutBreakRoomMm(228.6f)
        val cut = listOf(UndercutSpanMm("c", 1000f, 1100f))
        val (s, _) = withUndercutBreakRoom(1000f - room * 0.5f, 1900f, segs, 2000f, cut)
        assertEquals(1000f - room, s, 1e-3f)
        val (_, e) = withUndercutBreakRoom(100f, 1100f + room * 0.5f, segs, 2000f, cut)
        assertEquals(1100f + room, e, 1e-3f)
        // Already clear: unchanged.
        assertEquals(800f to 1300f, withUndercutBreakRoom(800f, 1300f, segs, 2000f, cut))
    }

    @Test
    fun `a liner strip's window is not walked past another strip's cut`() {
        val liner = UndercutLinerSpan("L", 1000f, 1500f)
        val segs = listOf(seg(0f, 1000f, 228.6f), seg(1000f, 1500f, 254f), seg(1500f, 3000f, 228.6f))
        val own = UndercutSpanMm("own", 1100f, 1200f)
        // A bare-shaft cut in the strip's FWD pad, clear of the liner's chain (not touching it).
        val foreign = UndercutSpanMm("foreign", 1510f, 1530f)
        val strips = buildUndercutStrips(listOf(own, foreign), listOf(liner), 3000f, segs = segs)
        val ls = strips.filterIsInstance<UndercutStrip.LinerStrip>().single()
        assertEquals(listOf("own"), ls.undercutIds)
        // The FWD end clears the liner face by the room and is not walked on past the foreign
        // cut's shoulder (which would put it at 1530 + room).
        assertEquals(1500f + max(UNDERCUT_WINDOW_PAD_MM, undercutBreakRoomMm(228.6f)), ls.drawEndMm, 1e-3f)
        assertTrue(strips.any { it is UndercutStrip.FreeStrip && "foreign" in it.undercutIds })
    }

    // ── "Draw to the shaft end" (UndercutRecord.drawToSet → linerStripFor's reachToSetMm) ──

    @Test
    fun `a chain end within the reach moves onto the FWD shaft end and the draw range ends there`() {
        val oal = 3000f
        val liner = UndercutLinerSpan("L", 2000f, 2600f)
        val segs = listOf(seg(0f, 2000f, 228.6f), seg(2000f, 2600f, 254f), seg(2600f, oal, 228.6f))
        val cut = listOf(UndercutSpanMm("c", 2500f, 2700f))
        val s = linerStripFor(liner, cut, oal, segs = segs, cutSpans = cut, reachToSetMm = UNDERCUT_SET_REACH_MM)
        assertEquals(oal, s.chainEndMm, 1e-3f)
        assertEquals(oal, s.drawEndMm, 1e-3f)
        // The AFT edge is 2000 mm from the AFT end — beyond the reach, so it stays on the liner.
        assertEquals(2000f, s.chainStartMm, 1e-3f)
        assertTrue(s.drawStartMm < 2000f)
    }

    @Test
    fun `a chain end beyond the reach is unchanged`() {
        val oal = 3000f
        val liner = UndercutLinerSpan("L", 1000f, 1600f)
        val cut = listOf(UndercutSpanMm("c", 1100f, 1200f))
        val on = linerStripFor(liner, cut, oal, reachToSetMm = UNDERCUT_SET_REACH_MM)
        val off = linerStripFor(liner, cut, oal)
        assertEquals(off, on)
        assertEquals(1600f, on.chainEndMm, 1e-3f)
    }

    @Test
    fun `an AFT chain start within the reach moves onto the AFT shaft end`() {
        val liner = UndercutLinerSpan("L", 400f, 1000f)
        val cut = listOf(UndercutSpanMm("c", 500f, 600f))
        val s = linerStripFor(liner, cut, 3000f, reachToSetMm = UNDERCUT_SET_REACH_MM)
        assertEquals(0f, s.chainStartMm, 1e-3f)
        assertEquals(0f, s.drawStartMm, 1e-3f)
        assertEquals(1000f, s.chainEndMm, 1e-3f)
    }

    @Test
    fun `reach zero is byte-identical to the default strip`() {
        val oal = 3000f
        val liner = UndercutLinerSpan("L", 2000f, 2900f)
        val segs = listOf(seg(0f, 2000f, 228.6f), seg(2000f, 2900f, 254f), seg(2900f, oal, 228.6f))
        val cut = listOf(UndercutSpanMm("c", 2100f, 2200f))
        assertEquals(
            linerStripFor(liner, cut, oal, segs = segs, cutSpans = cut),
            linerStripFor(liner, cut, oal, segs = segs, cutSpans = cut, reachToSetMm = 0f),
        )
        assertEquals(0f, undercutSetReachMm(false), 0f)
        assertEquals(UNDERCUT_SET_REACH_MM, undercutSetReachMm(true), 0f)
    }

    @Test
    fun `a job's total-rail choice wins over the app default, and an unset job follows it`() {
        assertTrue(effectiveUndercutTotalRail(null, globalDefault = true))
        assertFalse(effectiveUndercutTotalRail(null, globalDefault = false))
        assertTrue(effectiveUndercutTotalRail(true, globalDefault = false))
        assertFalse(effectiveUndercutTotalRail(false, globalDefault = true))
    }

    @Test
    fun `the chain ends at the FWD SET while the draw range runs on to the shaft end`() {
        // A FWD thread counted in the OAL: the S.E.T. (1900) sits inboard of the shaft end (2000).
        val oal = 2000f
        val liner = UndercutLinerSpan("L", 1000f, 1500f)
        val cut = listOf(UndercutSpanMm("c", 1450f, 1600f))
        val s = linerStripFor(
            liner, cut, oal, cutSpans = cut,
            reachToSetMm = UNDERCUT_SET_REACH_MM, aftSetMm = 100f, fwdSetMm = 1900f,
        )
        assertEquals(1900f, s.chainEndMm, 1e-3f)
        assertEquals(oal, s.drawEndMm, 1e-3f)
        // The AFT edge is 900 mm from the AFT S.E.T. — beyond the reach.
        assertEquals(1000f, s.chainStartMm, 1e-3f)
    }

    @Test
    fun `a chain already past the SET is left alone`() {
        // A cut run into the FWD thread, beyond the S.E.T. at 1900.
        val oal = 2000f
        val liner = UndercutLinerSpan("L", 1400f, 1800f)
        val cut = listOf(UndercutSpanMm("c", 1700f, 1950f))
        val on = linerStripFor(
            liner, cut, oal, reachToSetMm = UNDERCUT_SET_REACH_MM, aftSetMm = 100f, fwdSetMm = 1900f,
        )
        val off = linerStripFor(liner, cut, oal)
        assertEquals(off, on)
        assertEquals(1950f, on.chainEndMm, 1e-3f)
    }

    @Test
    fun `the AFT SET mirror moves the chain onto it and the draw range to zero`() {
        val liner = UndercutLinerSpan("L", 400f, 1000f)
        val cut = listOf(UndercutSpanMm("c", 500f, 600f))
        val s = linerStripFor(
            liner, cut, 3000f, reachToSetMm = UNDERCUT_SET_REACH_MM, aftSetMm = 150f, fwdSetMm = 3000f,
        )
        assertEquals(150f, s.chainStartMm, 1e-3f)
        assertEquals(0f, s.drawStartMm, 1e-3f)
    }

    @Test
    fun `buildUndercutStrips forwards the reach to liner strips only`() {
        val oal = 3000f
        val liner = UndercutLinerSpan("L", 2000f, 2500f)
        val inLiner = UndercutSpanMm("in", 2100f, 2200f)
        // A bare-shaft cut near the FWD end, well clear of the liner's chain.
        val bare = UndercutSpanMm("bare", 2800f, 2850f)
        val off = buildUndercutStrips(listOf(inLiner, bare), listOf(liner), oal)
        val on = buildUndercutStrips(listOf(inLiner, bare), listOf(liner), oal, reachToSetMm = UNDERCUT_SET_REACH_MM)
        val linerOn = on.filterIsInstance<UndercutStrip.LinerStrip>().single()
        assertEquals(oal, linerOn.chainEndMm, 1e-3f)
        // The S.E.T.s are forwarded too.
        val setOn = buildUndercutStrips(
            listOf(inLiner, bare), listOf(liner), oal,
            reachToSetMm = UNDERCUT_SET_REACH_MM, aftSetMm = 50f, fwdSetMm = 2950f,
        ).filterIsInstance<UndercutStrip.LinerStrip>().single()
        assertEquals(2950f, setOn.chainEndMm, 1e-3f)
        assertEquals(oal, setOn.drawEndMm, 1e-3f)
        assertEquals(
            off.filterIsInstance<UndercutStrip.FreeStrip>(),
            on.filterIsInstance<UndercutStrip.FreeStrip>(),
        )
    }

    // ── New-cut seeds ──

    @Test
    fun `an add seed with no cuts on the strip falls back to the default span`() {
        assertNull(undercutAddSeedSpan(emptyList(), emptyList(), oalMm = 2000f))
        // A degenerate (zero-width) member counts as no cut.
        assertNull(undercutAddSeedSpan(listOf(UndercutSpanMm("z", 700f, 700f)), emptyList(), 2000f))
    }

    @Test
    fun `an add seed butts on the FWD end of the FWD-most cut`() {
        val parent = UndercutSpanMm("p", 600f, 800f)
        val child = UndercutSpanMm("c", 700f, 800f)   // shares the parent's FWD shoulder
        val aft = UndercutSpanMm("a", 500f, 550f)
        val strip = listOf(aft, child, parent)
        val seed = undercutAddSeedSpan(strip, strip, oalMm = 2000f)
        assertNotNull(seed)
        assertEquals(800f, seed!!.startMm, 1e-4f)
        assertEquals(UNDERCUT_DEFAULT_LENGTH_MM, seed.lengthMm, 1e-4f)
        // Ties on the end go to the outermost cut — the parent, never the nested child.
        assertEquals("p", seed.neighbourId)
        // Touching counts as clear, so the seed opens confirmable.
        assertNull(undercutOverlapIssue(seed.startMm, seed.lengthMm, strip))
    }

    @Test
    fun `an add seed with no stock FWD butts on the AFT end of the AFT-most cut`() {
        val a = UndercutSpanMm("a", 900f, 950f)
        val b = UndercutSpanMm("b", 960f, 1990f)
        val seed = undercutAddSeedSpan(listOf(a, b), listOf(a, b), oalMm = 2000f)
        assertNotNull(seed)
        assertEquals(900f - UNDERCUT_DEFAULT_LENGTH_MM, seed!!.startMm, 1e-4f)
        assertEquals("a", seed.neighbourId)
        // A cut on another strip in the FWD stock also sends the seed AFT.
        val s = UndercutSpanMm("s", 900f, 1000f)
        val foreign = UndercutSpanMm("f", 1010f, 1050f)
        val aftSeed = undercutAddSeedSpan(listOf(s), listOf(s, foreign), oalMm = 2000f)
        assertEquals(900f - UNDERCUT_DEFAULT_LENGTH_MM, aftSeed!!.startMm, 1e-4f)
        assertEquals("s", aftSeed.neighbourId)
    }

    @Test
    fun `an add seed with no stock on either side falls back to the default span`() {
        val only = UndercutSpanMm("u", 10f, 1990f)
        assertNull(undercutAddSeedSpan(listOf(only), listOf(only), oalMm = 2000f))
        // Exactly the default length of stock FWD still fits.
        val flush = UndercutSpanMm("f", 10f, 2000f - UNDERCUT_DEFAULT_LENGTH_MM)
        assertEquals(
            2000f - UNDERCUT_DEFAULT_LENGTH_MM,
            undercutAddSeedSpan(listOf(flush), listOf(flush), oalMm = 2000f)!!.startMm,
            1e-4f,
        )
    }

    @Test
    fun `the next cut seed starts on the current cut's FWD end and carries its datum`() {
        val current = Undercut(
            id = "cur", startFromAftMm = 600f, lengthMm = 100f, diaMm = 180f, note = "weld",
            authoredReference = UndercutReference.LINER_FWD, referenceLinerId = "L1",
            referenceCutId = "",
        )
        val seed = undercutNextCutSeed(current, oalMm = 2000f)
        assertNotNull(seed)
        assertEquals(700f, seed!!.startFromAftMm, 1e-4f)
        assertEquals(UNDERCUT_DEFAULT_LENGTH_MM, seed.lengthMm, 1e-4f)
        assertEquals(0f, seed.diaMm, 0f)
        assertEquals("", seed.note)
        assertEquals(UndercutReference.LINER_FWD, seed.authoredReference)
        assertEquals("L1", seed.referenceLinerId)
        assertTrue(seed.id != current.id)
        // The current cut is untouched — the seed is a new value.
        assertEquals(180f, current.diaMm, 0f)
    }

    @Test
    fun `the next cut seed is refused when the shaft ends before it`() {
        val nearEnd = Undercut(id = "e", startFromAftMm = 1900f, lengthMm = 90f)
        assertNull(undercutNextCutSeed(nearEnd, oalMm = 2000f))
        val exact = Undercut(id = "x", startFromAftMm = 1900f, lengthMm = 100f - UNDERCUT_DEFAULT_LENGTH_MM)
        assertNotNull(undercutNextCutSeed(exact, oalMm = 2000f))
    }

    // ── Missing diameters ──

    @Test
    fun `cuts with no measured diameter are listed in record order`() {
        val cuts = listOf(
            Undercut(id = "a", diaMm = 0f),
            Undercut(id = "b", diaMm = 150f),
            Undercut(id = "c", diaMm = -1f),
        )
        assertEquals(listOf("a", "c"), undercutsMissingDia(cuts).map { it.id })
        assertTrue(undercutsMissingDia(listOf(Undercut(id = "m", diaMm = 1f))).isEmpty())
        assertEquals("1 cut has no diameter and will print without one.", undercutMissingDiaMessage(1))
        assertEquals("3 cuts have no diameter and will print without one.", undercutMissingDiaMessage(3))
    }

    // ── undercutStripsReachThread: the undercut options' Thread-section gate ──

    private fun linerStripDrawn(drawStart: Float, drawEnd: Float) = UndercutStrip.LinerStrip(
        linerId = "L", linerStartMm = drawStart + 10f, linerEndMm = drawEnd - 10f,
        drawStartMm = drawStart, drawEndMm = drawEnd,
        chainStartMm = drawStart + 10f, chainEndMm = drawEnd - 10f, undercutIds = emptyList(),
    )

    @Test
    fun `a strip far from every thread does not reach one`() {
        // The on-device job: an excluded AFT thread behind the AFT face, the strip near the FWD end.
        val strips = listOf(linerStripDrawn(9900f, 10995f))
        assertFalse(undercutStripsReachThread(strips, listOf(-203.2f to 0f)))
    }

    @Test
    fun `a strip whose window overlaps a thread reaches it`() {
        val strips = listOf(linerStripDrawn(9900f, 10995f))
        assertTrue(undercutStripsReachThread(strips, listOf(10900f to 11100f)))
    }

    @Test
    fun `touching is not reaching and a zero-length thread never counts`() {
        val strips = listOf(linerStripDrawn(1000f, 2000f))
        assertFalse(undercutStripsReachThread(strips, listOf(2000f to 2200f)))
        assertFalse(undercutStripsReachThread(strips, listOf(1500f to 1500f)))
        assertFalse(undercutStripsReachThread(emptyList(), listOf(0f to 100f)))
    }
}
