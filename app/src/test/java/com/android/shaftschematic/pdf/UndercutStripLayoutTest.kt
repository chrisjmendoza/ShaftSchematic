package com.android.shaftschematic.pdf

import com.android.shaftschematic.geom.ClampedUndercutSpanMm
import com.android.shaftschematic.geom.UndercutLinerSpan
import com.android.shaftschematic.geom.UndercutSpanMm
import com.android.shaftschematic.geom.UndercutStrip
import com.android.shaftschematic.geom.UndercutWindow
import com.android.shaftschematic.geom.buildUndercutStrips
import com.android.shaftschematic.geom.linerStripFor
import com.android.shaftschematic.model.Undercut
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-math tests for the undercut-PDF detail-strip layout
 * (`docs/archive/UndercutDrawing_PLAN.md` §8) — plain JVM assertions, no Robolectric, exercising
 * `UndercutStripLayout.kt`'s functions directly rather than replicating their formulas.
 */
class UndercutStripLayoutTest {

    private val mm = UnitSystem.MILLIMETERS

    private fun window(startMm: Float, endMm: Float, vararg ids: String) =
        UndercutWindow(startMm, endMm, ids.toList())

    private fun span(id: String, startMm: Float, endMm: Float) = UndercutSpanMm(id, startMm, endMm)

    private fun List<WearRailSpan>.totalLengthMm(): Float =
        sumOf { (it.endMm - it.startMm).toDouble() }.toFloat()

    /** The chain over a strip's own chain bounds — the composer's exact call. */
    private fun railFor(strip: UndercutStrip, spans: List<UndercutSpanMm>) =
        buildUndercutRailSpans(strip.chainStartMm, strip.chainEndMm, spans.sortedBy { it.startMm }, mm)

    // ── Chained rail: coverage, omissions, overlaps ──────────────────────────

    @Test
    fun `chain covers the window exactly with pads, cuts and gaps`() {
        val w = window(100f, 300f, "a", "b")
        val spans = listOf(span("a", 130f, 160f), span("b", 200f, 240f))
        val rail = railFor(UndercutStrip.FreeStrip(w), spans)

        // pad, cut a, gap, cut b, pad
        assertEquals(5, rail.size)
        assertEquals(100f, rail.first().startMm, 1e-3f)
        assertEquals(300f, rail.last().endMm, 1e-3f)
        assertEquals(w.lengthMm, rail.totalLengthMm(), 1e-2f)
        // Chain is contiguous: every span starts where the previous ended.
        rail.zipWithNext().forEach { (p, n) -> assertEquals(p.endMm, n.startMm, 1e-3f) }
    }

    @Test
    fun `zero-length spans are omitted but the chain still covers the window`() {
        // Cut flush with BOTH window edges: no leading pad, no trailing pad.
        val rail = buildUndercutRailSpans(50f, 150f, listOf(span("a", 50f, 150f)), mm)
        assertEquals(1, rail.size)
        assertEquals(100f, rail.totalLengthMm(), 1e-3f)

        // Two back-to-back cuts: no gap span between them.
        val rail2 = buildUndercutRailSpans(0f, 100f, listOf(span("a", 20f, 50f), span("b", 50f, 80f)), mm)
        assertEquals(4, rail2.size)   // pad, a, b, pad — no zero-width gap
        assertEquals(100f, rail2.totalLengthMm(), 1e-3f)
        assertTrue(rail2.none { it.endMm - it.startMm <= 1e-3f })
    }

    @Test
    fun `overlapping undercuts are never double-counted and the chain never runs backward`() {
        val spans = listOf(span("a", 40f, 120f), span("b", 80f, 140f))
        val rail = buildUndercutRailSpans(0f, 200f, spans, mm)

        assertEquals(200f, rail.totalLengthMm(), 1e-3f)
        rail.forEach { assertTrue("span must run aft→fwd", it.endMm > it.startMm) }
        rail.zipWithNext().forEach { (p, n) -> assertEquals(p.endMm, n.startMm, 1e-3f) }
        // The overlap reads as belonging to the first cut: the second contributes 120→140.
        assertTrue(rail.any { kotlin.math.abs(it.startMm - 120f) < 1e-3f && kotlin.math.abs(it.endMm - 140f) < 1e-3f })
    }

    @Test
    fun `a cluster with no drawable spans still dimensions the whole window`() {
        val rail = buildUndercutRailSpans(10f, 90f, emptyList(), mm)
        assertEquals(1, rail.size)
        assertEquals(80f, rail.totalLengthMm(), 1e-3f)
    }

    // ── Liner strips: the chain anchors on the liner's edges, never on the pad ──

    private val liner = UndercutLinerSpan("liner", 600f, 1000f)
    private val oal = 2000f

    private fun linerStrip(spans: List<UndercutSpanMm>): UndercutStrip.LinerStrip =
        buildUndercutStrips(spans, listOf(liner), oal)
            .filterIsInstance<UndercutStrip.LinerStrip>()
            .single()

    @Test
    fun `a liner strip's chain covers the liner exactly and never dimensions the pad`() {
        val spans = listOf(span("a", 700f, 760f), span("b", 820f, 880f))
        val strip = linerStrip(spans)

        // Chain = the liner's own edges; the draw range is padded well outside them.
        assertEquals(600f, strip.chainStartMm, 1e-3f)
        assertEquals(1000f, strip.chainEndMm, 1e-3f)
        assertTrue(strip.drawStartMm < strip.chainStartMm)
        assertTrue(strip.drawEndMm > strip.chainEndMm)

        val rail = railFor(strip, spans)
        // liner aft → a, a, gap, b, b → liner fwd
        assertEquals(5, rail.size)
        assertEquals(600f, rail.first().startMm, 1e-3f)
        assertEquals(1000f, rail.last().endMm, 1e-3f)
        assertEquals(400f, rail.totalLengthMm(), 1e-3f)
        rail.zipWithNext().forEach { (p, n) -> assertEquals(p.endMm, n.startMm, 1e-3f) }
        // Nothing in the pad between a break edge and the liner edge is dimensioned.
        assertTrue(rail.none { it.startMm < strip.chainStartMm - 1e-3f || it.endMm > strip.chainEndMm + 1e-3f })
    }

    @Test
    fun `a cut overhanging the liner's FWD edge extends the chain to the cut's shoulder`() {
        // 960→1030 crosses the liner's FWD edge at 1000; most of it (40 mm vs 30 mm) is on
        // the liner, so it joins the liner's strip and drags the chain datum out with it.
        val spans = listOf(span("a", 700f, 760f), span("b", 960f, 1030f))
        val strip = linerStrip(spans)

        assertEquals(600f, strip.chainStartMm, 1e-3f)
        assertEquals(1030f, strip.chainEndMm, 1e-3f)
        assertTrue("the pad still clears the overhung shoulder", strip.drawEndMm > 1030f)

        val rail = railFor(strip, spans)
        assertEquals(1030f, rail.last().endMm, 1e-3f)
        assertEquals(430f, rail.totalLengthMm(), 1e-3f)
        // The overhanging cut's own length is dimensioned in full, both sides of the edge.
        assertTrue(rail.any { kotlin.math.abs(it.startMm - 960f) < 1e-3f && kotlin.math.abs(it.endMm - 1030f) < 1e-3f })
    }

    @Test
    fun `a bare-shaft strip keeps the padded window as its chain`() {
        val spans = listOf(span("a", 200f, 240f))
        val strip = buildUndercutStrips(spans, listOf(liner), oal)
            .filterIsInstance<UndercutStrip.FreeStrip>()
            .single()

        assertEquals(strip.drawStartMm, strip.chainStartMm, 1e-3f)
        assertEquals(strip.drawEndMm, strip.chainEndMm, 1e-3f)
        val rail = railFor(strip, spans)
        assertEquals(strip.drawEndMm - strip.drawStartMm, rail.totalLengthMm(), 1e-3f)
    }

    // ── Drawn pad: the pt floor on the stock outside each chain datum ─────────

    /** A full-width strip on a strips-own-page sheet (36 pt page margins). */
    private val fullLeft = 36f
    private val fullRight = 756f

    /** The LEFT cell of a 2-up grid page — half the width, so half the scale. */
    private val cellLeft = 36f
    private val cellRight = 385f

    /**
     * What the two pads (chain datum → break edge) actually PRINT for a draw range laid out in
     * the given cell — the composer's own arithmetic, `computeWearStripHorizontalLayout` with the
     * strip's edge inset as the stub.
     */
    private fun drawnPadsPt(
        range: UndercutStripDrawRangeMm,
        chainStartMm: Float,
        chainEndMm: Float,
        leftPt: Float,
        rightPt: Float,
    ): Pair<Float, Float> {
        val h = computeWearStripHorizontalLayout(
            leftPt, rightPt, range.endMm - range.startMm, stubWidthPt = UNDERCUT_STRIP_EDGE_INSET_PT,
        )
        return (chainStartMm - range.startMm) * h.ptPerMm to (range.endMm - chainEndMm) * h.ptPerMm
    }

    private fun drawRangeFor(strip: UndercutStrip, leftPt: Float, rightPt: Float) =
        computeUndercutStripDrawRange(
            strip.drawStartMm, strip.drawEndMm, strip.chainStartMm, strip.chainEndMm,
            leftPt, rightPt, oal,
        )

    @Test
    fun `a strip whose mm pad already prints wide enough is left alone`() {
        val strip = linerStrip(listOf(span("a", 700f, 760f), span("b", 820f, 880f)))
        val range = drawRangeFor(strip, fullLeft, fullRight)

        assertEquals(strip.drawStartMm, range.startMm, 1e-3f)
        assertEquals(strip.drawEndMm, range.endMm, 1e-3f)
        val (padA, padF) = drawnPadsPt(range, strip.chainStartMm, strip.chainEndMm, fullLeft, fullRight)
        assertTrue("full width already clears the floor", padA >= UNDERCUT_STRIP_MIN_PAD_PT)
        assertTrue(padF >= UNDERCUT_STRIP_MIN_PAD_PT)
    }

    @Test
    fun `a grid cell widens its draw range until the pad prints at the floor`() {
        val strip = linerStrip(listOf(span("a", 700f, 760f), span("b", 820f, 880f)))

        // Unwidened, the same 1 in of stock would print at well under the floor at cell scale —
        // the break edge all but against the liner's end face (on-device report).
        val asIs = UndercutStripDrawRangeMm(strip.drawStartMm, strip.drawEndMm)
        val (rawPad, _) = drawnPadsPt(asIs, strip.chainStartMm, strip.chainEndMm, cellLeft, cellRight)
        assertTrue("the cramped case must actually be cramped", rawPad < UNDERCUT_STRIP_MIN_PAD_PT)

        val range = drawRangeFor(strip, cellLeft, cellRight)
        val (padA, padF) = drawnPadsPt(range, strip.chainStartMm, strip.chainEndMm, cellLeft, cellRight)
        assertEquals(UNDERCUT_STRIP_MIN_PAD_PT, padA, 1e-2f)
        assertEquals(UNDERCUT_STRIP_MIN_PAD_PT, padF, 1e-2f)
        // Only ever widens, and only outward: the datums the rail measures to do not move.
        assertTrue(range.startMm < strip.drawStartMm)
        assertTrue(range.endMm > strip.drawEndMm)
        assertEquals(600f, strip.chainStartMm, 1e-3f)
        assertEquals(1000f, strip.chainEndMm, 1e-3f)
    }

    @Test
    fun `a bare-shaft strip gets its end air from the floor alone`() {
        // A free strip's chain datums ARE its window edges, so with no widening the break edge
        // lands exactly on the outer witness line.
        val strip = buildUndercutStrips(listOf(span("a", 200f, 240f)), listOf(liner), oal)
            .filterIsInstance<UndercutStrip.FreeStrip>()
            .single()
        val range = drawRangeFor(strip, cellLeft, cellRight)

        val (padA, padF) = drawnPadsPt(range, strip.chainStartMm, strip.chainEndMm, cellLeft, cellRight)
        assertEquals(UNDERCUT_STRIP_MIN_PAD_PT, padA, 1e-2f)
        assertEquals(UNDERCUT_STRIP_MIN_PAD_PT, padF, 1e-2f)
        // The window itself is untouched, so its chain still prints the same pad dimensions.
        assertEquals(strip.drawStartMm, strip.chainStartMm, 1e-3f)
        assertTrue(range.startMm < strip.chainStartMm)
    }

    @Test
    fun `the widened range never runs past the shaft's ends`() {
        // A cut hard against the AFT face: the window is already clamped at 0, and that end draws
        // as the physical shaft end (flat), so it must not grow a pad past the metal.
        val strip = buildUndercutStrips(listOf(span("a", 0f, 40f)), emptyList(), oal)
            .filterIsInstance<UndercutStrip.FreeStrip>()
            .single()
        assertEquals(0f, strip.drawStartMm, 1e-3f)

        val range = drawRangeFor(strip, cellLeft, cellRight)
        assertEquals("the AFT end stays on the shaft face", 0f, range.startMm, 1e-3f)
        assertTrue(range.endMm > strip.drawEndMm)
        assertTrue(range.endMm <= oal + 1e-3f)
        val (_, padF) = drawnPadsPt(range, strip.chainStartMm, strip.chainEndMm, cellLeft, cellRight)
        assertEquals(UNDERCUT_STRIP_MIN_PAD_PT, padF, 1e-2f)
    }

    @Test
    fun `a degenerate cell cannot spend itself on pad`() {
        val strip = linerStrip(listOf(span("a", 700f, 760f)))
        // 100 pt wide: the floor would be nearly half of it, so the fraction guard bites and the
        // chain keeps most of the width.
        val range = drawRangeFor(strip, 0f, 100f)
        val (padA, padF) = drawnPadsPt(range, strip.chainStartMm, strip.chainEndMm, 0f, 100f)
        val avail = 100f - 2f * UNDERCUT_STRIP_EDGE_INSET_PT
        assertTrue(padA <= avail * 0.15f + 1e-2f)
        assertTrue(padF <= avail * 0.15f + 1e-2f)
        assertTrue(range.startMm <= strip.drawStartMm)
        assertTrue(range.endMm >= strip.drawEndMm)
    }

    // ── Strip titles ─────────────────────────────────────────────────────────

    @Test
    fun `a liner strip title carries the liner name before its anchor dimension`() {
        val anchor = buildUndercutAnchorLabel(UndercutAnchor(UndercutAnchorSide.AFT_SET, 500f), mm)
        assertEquals("AFT Liner — $anchor", buildUndercutStripTitle("AFT Liner", anchor))
        // A bare-shaft strip has nothing to name: anchor only, no dangling separator.
        assertEquals(anchor, buildUndercutStripTitle(null, anchor))
        assertEquals(anchor, buildUndercutStripTitle("  ", anchor))
        assertEquals("AFT Liner", buildUndercutStripTitle("AFT Liner", ""))
    }

    // ── Total span ────────────────────────────────────────────────────────────

    @Test
    fun `total span runs first shoulder to last shoulder`() {
        val spans = listOf(span("a", 130f, 160f), span("b", 200f, 240f), span("c", 260f, 275f))
        val total = buildUndercutTotalSpan(spans, mm)
        assertNotNull(total)
        assertEquals(130f, total!!.startMm, 1e-3f)
        assertEquals(275f, total.endMm, 1e-3f)
    }

    @Test
    fun `a single undercut gets no total span because the chain already states its length`() {
        assertNull(buildUndercutTotalSpan(listOf(span("a", 10f, 40f)), mm))
        assertNull(buildUndercutTotalSpan(emptyList(), mm))
        // Degenerate spans don't count toward the pair either.
        assertNull(buildUndercutTotalSpan(listOf(span("a", 10f, 40f), span("b", 60f, 60f)), mm))
    }

    // ── Anchor from SET ───────────────────────────────────────────────────────

    @Test
    fun `an aft-half cluster anchors to the AFT SET from its first shoulder and aligns left`() {
        val a = undercutAnchorFor(
            firstShoulderMm = 300f, lastShoulderMm = 400f,
            aftSetXMm = 100f, fwdSetXMm = 2100f,
        )
        assertEquals(UndercutAnchorSide.AFT_SET, a.side)
        assertEquals(200f, a.distanceMm, 1e-3f)
        assertFalse(a.alignRight)
    }

    @Test
    fun `a fwd-half cluster anchors to the FWD SET from its last shoulder and aligns right`() {
        val a = undercutAnchorFor(
            firstShoulderMm = 1700f, lastShoulderMm = 1800f,
            aftSetXMm = 100f, fwdSetXMm = 2100f,
        )
        assertEquals(UndercutAnchorSide.FWD_SET, a.side)
        assertEquals(300f, a.distanceMm, 1e-3f)
        assertTrue(a.alignRight)
    }

    @Test
    fun `a cluster outboard of its SET reports a positive distance`() {
        // Undercut aft of the AFT SET (between the shaft end and the taper's small end).
        val a = undercutAnchorFor(
            firstShoulderMm = 20f, lastShoulderMm = 60f,
            aftSetXMm = 100f, fwdSetXMm = 2100f,
        )
        assertEquals(UndercutAnchorSide.AFT_SET, a.side)
        assertEquals(80f, a.distanceMm, 1e-3f)
    }

    @Test
    fun `anchor label reuses the wear sheet's SET wording`() {
        val label = buildUndercutAnchorLabel(UndercutAnchor(UndercutAnchorSide.FWD_SET, 250f), mm)
        assertTrue(label, label.endsWith("FROM FWD S.E.T."))
        assertTrue(label, label.startsWith(formatLenDim(250.0, mm)))
    }

    // ── Page mode by cluster count ────────────────────────────────────────────

    @Test
    fun `strip mode follows the cluster count`() {
        assertEquals(WearPdfMode.PROFILE_FORM, determineUndercutPdfMode(0))
        assertEquals(WearPdfMode.COMBINED, determineUndercutPdfMode(1))
        assertEquals(WearPdfMode.GRID, determineUndercutPdfMode(2))
        assertEquals(WearPdfMode.GRID, determineUndercutPdfMode(7))

        assertEquals(WEAR_STRIP_MAX_PER_PAGE, undercutStripsPerPage(WearPdfMode.COMBINED))
        assertEquals(WEAR_STRIP_GRID_MAX_PER_PAGE, undercutStripsPerPage(WearPdfMode.GRID))
    }

    // ── Strip inner banding ──────────────────────────────────────────────────

    @Test
    fun `inner layout orders the total rail above the chain and never leaves the strip`() {
        val inner = computeUndercutStripInnerLayout(
            stripTop = 400f, stripBottom = 540f, titleHeightPt = 9f, hasTotalRail = true, diaBandPt = 12f,
        )
        assertTrue(inner.totalRailY >= 400f)
        assertTrue(inner.totalRailY <= inner.chainRailY)
        assertTrue(inner.chainRailY <= inner.cylTop)
        assertTrue(inner.cylTop <= inner.cylBottom)
        assertTrue(inner.cylBottom <= 540f)
        // The reserved total band actually pushes the chain down relative to no-total, and
        // with no total span the two rail lines collapse onto each other.
        val noTotal = computeUndercutStripInnerLayout(400f, 540f, 9f, hasTotalRail = false, diaBandPt = 12f)
        assertTrue(inner.chainRailY > noTotal.chainRailY)
        assertEquals(noTotal.chainRailY, noTotal.totalRailY, 1e-3f)
    }

    @Test
    fun `the total rail keeps air above itself and clear separation down to the chain`() {
        val inner = computeUndercutStripInnerLayout(
            stripTop = 400f, stripBottom = 540f, titleHeightPt = 9f, hasTotalRail = true, diaBandPt = 12f,
        )
        // Above: the row a total too wide to seat in its own break falls back to.
        assertTrue(
            "total rail must keep its above-label row",
            inner.totalRailY - 400f >= UNDERCUT_TOTAL_RAIL_ABOVE_PT - 1e-2f,
        )
        // Below: the two rails' values must not read as one crowded band (on-device report).
        assertEquals(
            UNDERCUT_TOTAL_RAIL_BAND_PT - UNDERCUT_TOTAL_RAIL_ABOVE_PT,
            inner.chainRailY - inner.totalRailY,
            1e-2f,
        )
    }

    // ── Cylinder cap: the reclaimed profile band becomes air, not a slab ──────

    /** A full-width strip on a strips-own-page sheet: header + direction row → notes gap. */
    private val fullPageStripTop = 110f
    private val fullPageStripBottom = 524f

    private fun cylCapFor(stripTop: Float, stripBottom: Float): Float =
        maxOf(
            UNDERCUT_CYL_MAX_FLOOR_PT,
            minOf((stripBottom - stripTop) * UNDERCUT_CYL_MAX_HEIGHT_FRAC, UNDERCUT_CYL_MAX_ABS_PT),
        )

    /** The same band with the cap lifted out of the way — the plain wear-shaped delegation. */
    private fun uncapped(
        stripTop: Float,
        stripBottom: Float,
        hasTotalRail: Boolean = true,
        diaBandPt: Float = 12f,
    ) = computeUndercutStripInnerLayout(
        stripTop, stripBottom, titleHeightPt = 9f, hasTotalRail = hasTotalRail, diaBandPt = diaBandPt,
        cylMaxFrac = 1f, cylMaxAbsPt = Float.MAX_VALUE,
    )

    @Test
    fun `a full-page strip caps its cylinder and spends the surplus on air`() {
        val inner = computeUndercutStripInnerLayout(
            fullPageStripTop, fullPageStripBottom,
            titleHeightPt = 9f, hasTotalRail = true, diaBandPt = 12f,
        )
        val cap = cylCapFor(fullPageStripTop, fullPageStripBottom)
        assertTrue(
            "the drawn cylinder must not eat the reclaimed profile band",
            inner.cylBottom - inner.cylTop <= cap + 1e-2f,
        )
        // At page height the FRACTION binds, not the absolute ceiling — the ceiling is the
        // guard for any taller band (see the next test).
        assertEquals(
            (fullPageStripBottom - fullPageStripTop) * UNDERCUT_CYL_MAX_HEIGHT_FRAC, cap, 1e-2f,
        )
        // A moderately drawn section, not a slab: well under half the band it sits in.
        assertTrue("the section must stay a drawing, not a poster", cap < (fullPageStripBottom - fullPageStripTop) * 0.45f)

        // The surplus shows up as air: the rails sit well down from the strip top, and the Ø
        // callout band clears the title.
        assertTrue("air above the rails", inner.chainRailY - fullPageStripTop > 40f)
        assertTrue("air below the callouts", fullPageStripBottom - inner.cylBottom > 60f)

        // …and it is spent EVENLY. Measured against the uncapped delegation: after the fixed
        // rail headroom, the same number of points go below the cylinder as above the rails.
        // If either side capped out, the leftover would all pile up on the other.
        val plain = uncapped(fullPageStripTop, fullPageStripBottom)
        val belowExtra = plain.cylBottom - inner.cylBottom
        val aboveExtra = inner.chainRailY - plain.chainRailY
        assertEquals("surplus must split evenly below/above", belowExtra, aboveExtra, 1e-2f)
        assertTrue(
            "the below-air cap must not bite at page height",
            belowExtra < UNDERCUT_CYL_BELOW_EXTRA_MAX_PT,
        )

        // Ordering still holds.
        assertTrue(inner.totalRailY >= fullPageStripTop)
        assertTrue(inner.totalRailY <= inner.chainRailY)
        assertTrue(inner.chainRailY <= inner.cylTop)
        assertTrue(inner.cylBottom <= fullPageStripBottom)
    }

    @Test
    fun `a band taller than the page stops growing at the absolute ceiling`() {
        // No sheet is this tall today; the ceiling exists so a taller band never turns the strip
        // into one giant cylinder. `frac × band` would ask for 228 pt here.
        val top = 60f
        val bottom = 660f
        val inner = computeUndercutStripInnerLayout(
            top, bottom, titleHeightPt = 9f, hasTotalRail = true, diaBandPt = 12f,
        )
        assertTrue((bottom - top) * UNDERCUT_CYL_MAX_HEIGHT_FRAC > UNDERCUT_CYL_MAX_ABS_PT)
        assertEquals(UNDERCUT_CYL_MAX_ABS_PT, inner.cylBottom - inner.cylTop, 1e-2f)
        assertTrue(inner.chainRailY <= inner.cylTop)
        assertTrue(inner.cylBottom <= bottom)
    }

    @Test
    fun `a grid cell is short enough that the cap never bites`() {
        val top = 110f
        val bottom = 310f
        val inner = computeUndercutStripInnerLayout(
            top, bottom, titleHeightPt = 9f, hasTotalRail = true, diaBandPt = 12f,
        )
        // At this height the FLOOR is the cap, and the delegation's own cylinder lands exactly
        // on it — so there is no surplus to spend and the layout is untouched.
        assertEquals(UNDERCUT_CYL_MAX_FLOOR_PT, cylCapFor(top, bottom), 1e-2f)
        assertTrue(inner.cylBottom - inner.cylTop <= cylCapFor(top, bottom) + 1e-2f)
        // No surplus ⇒ bit-identical to the plain delegation: the chain rail sits exactly at
        // the bottom of the reserved total band and the cylinder keeps its full bottom.
        assertEquals(top + UNDERCUT_TOTAL_RAIL_BAND_PT, inner.chainRailY, 1e-2f)
        assertEquals(bottom - 9f - 11f - 12f, inner.cylBottom, 1e-2f)
        val plain = uncapped(top, bottom)
        assertEquals(plain.cylTop, inner.cylTop, 1e-3f)
        assertEquals(plain.cylBottom, inner.cylBottom, 1e-3f)
        assertEquals(plain.chainRailY, inner.chainRailY, 1e-3f)
    }

    // ── Rail row plan: reserve the rows the chain's labels actually use ───────

    @Test
    fun `the rail row plan follows the fallback rows in use and which level owns the air above`() {
        // Arrow direction is irrelevant here — only whether the value seats in the break, which
        // is what decides if the span claims a fallback row.
        fun span(row: Int, seated: Boolean) =
            WearRailSpanLayout(0f, 10f, "x", 5f, row, seatsInBreak = seated, arrowInward = true)
        // Break-seated labels sit on the rail line itself — one clear row below keeps the rail
        // (and its arrowheads) off the shaft, nothing more, wherever fallbacks would go.
        assertEquals(
            UndercutRailRowPlan(belowRows = 1, aboveRows = 0),
            planUndercutRailRows(listOf(span(0, true), span(1, true)), startedStrip = false, hasTotalRail = true),
        )
        assertEquals(
            UndercutRailRowPlan(belowRows = 1, aboveRows = 0),
            planUndercutRailRows(emptyList(), startedStrip = false, hasTotalRail = false),
        )
        // Total rail above ⇒ fallbacks tuck BELOW, reserving through their own row (wear cap).
        assertEquals(
            UndercutRailRowPlan(belowRows = 1, aboveRows = 0),
            planUndercutRailRows(listOf(span(0, false)), startedStrip = false, hasTotalRail = true),
        )
        assertEquals(
            UndercutRailRowPlan(belowRows = 2, aboveRows = 0),
            planUndercutRailRows(listOf(span(0, true), span(1, false)), startedStrip = false, hasTotalRail = true),
        )
        assertEquals(
            UndercutRailRowPlan(belowRows = WEAR_RAIL_MAX_LABEL_ROWS, aboveRows = 0),
            planUndercutRailRows(listOf(span(5, false)), startedStrip = false, hasTotalRail = true),
        )
        // Chain is the only label level ⇒ fallbacks go ABOVE the line (on-device report),
        // below keeps just the clear row.
        assertEquals(
            UndercutRailRowPlan(belowRows = 1, aboveRows = 1),
            planUndercutRailRows(listOf(span(0, false)), startedStrip = false, hasTotalRail = false),
        )
        assertEquals(
            UndercutRailRowPlan(belowRows = 1, aboveRows = WEAR_RAIL_MAX_LABEL_ROWS),
            planUndercutRailRows(listOf(span(5, false)), startedStrip = false, hasTotalRail = false),
        )
        // A started strip draws no chain; the full hand-drawing budget stays below.
        assertEquals(
            UndercutRailRowPlan(belowRows = WEAR_RAIL_MAX_LABEL_ROWS, aboveRows = 0),
            planUndercutRailRows(emptyList(), startedStrip = true, hasTotalRail = false),
        )
    }

    @Test
    fun `the chain above-band reserves air over a single-level rail`() {
        // No-surplus band, no total: the above band pushes the whole figure down by its height,
        // keeping room for the stacked labels above the rail line inside the strip.
        val flat = computeUndercutStripInnerLayout(400f, 540f, 9f, hasTotalRail = false, diaBandPt = 12f, maxLabelRows = 1)
        val above = computeUndercutStripInnerLayout(
            400f, 540f, 9f, hasTotalRail = false, diaBandPt = 12f, maxLabelRows = 1,
            chainAboveBandPt = UNDERCUT_RAIL_ROW_HEIGHT_PT,
        )
        assertEquals(UNDERCUT_RAIL_ROW_HEIGHT_PT, above.chainRailY - flat.chainRailY, 1e-2f)
        assertTrue("reserved air above the rail", above.chainRailY - 400f >= UNDERCUT_RAIL_ROW_HEIGHT_PT - 1e-2f)
        // Ordering guarantee holds with the band in play.
        assertTrue(above.chainRailY <= above.cylTop)
        assertTrue(above.cylTop <= above.cylBottom)
        assertTrue(above.cylBottom <= 540f)
    }

    @Test
    fun `a one-row budget pulls the chain rail in against the cylinder`() {
        // No-surplus band, so the rail-to-cylinder air is exactly the budgeted rows.
        val two = computeUndercutStripInnerLayout(400f, 540f, 9f, hasTotalRail = true, diaBandPt = 12f)
        val one = computeUndercutStripInnerLayout(
            400f, 540f, 9f, hasTotalRail = true, diaBandPt = 12f, maxLabelRows = 1,
        )
        assertEquals(UNDERCUT_RAIL_ROW_HEIGHT_PT, one.cylTop - one.chainRailY, 1e-2f)
        assertEquals(2 * UNDERCUT_RAIL_ROW_HEIGHT_PT, two.cylTop - two.chainRailY, 1e-2f)
        assertEquals(1, one.railLabelRows)
        // The level-to-level separation up to the total rail is untouched by the budget.
        assertEquals(
            UNDERCUT_TOTAL_RAIL_BAND_PT - UNDERCUT_TOTAL_RAIL_ABOVE_PT,
            one.chainRailY - one.totalRailY,
            1e-2f,
        )
    }

    // ── Started strips: the blank template / empty-record page ────────────────

    @Test
    fun `a started liner strip draws the whole liner and dimensions nothing`() {
        val strip = linerStripFor(liner, emptyList(), oal)

        assertEquals("liner", strip.linerId)
        assertTrue("a started strip carries no cuts", strip.undercutIds.isEmpty())
        // Chain datums are the liner's own edges; the draw range pads past them, so the
        // liner's edges and its neighbour slivers are visible to sketch against.
        assertEquals(600f, strip.chainStartMm, 1e-3f)
        assertEquals(1000f, strip.chainEndMm, 1e-3f)
        assertTrue(strip.drawStartMm < strip.chainStartMm)
        assertTrue(strip.drawEndMm > strip.chainEndMm)
        // Nothing recorded ⇒ no total rail (and the composer skips the chain entirely, leaving
        // the two chain-datum witness bars).
        assertNull(buildUndercutTotalSpan(emptyList(), mm))
        // The page mode still follows the strip count, so N started liners lay out exactly as
        // N cut strips would.
        assertEquals(WearPdfMode.COMBINED, determineUndercutPdfMode(1))
        assertEquals(WearPdfMode.GRID, determineUndercutPdfMode(3))
    }

    @Test
    fun `a started strip leaves clear space above and below the cylinder for hand work`() {
        // Full-page band, no total rail and no Ø band — the empty-record posture. The cylinder
        // cap must keep most of that band writable rather than drawing a slab.
        val inner = computeUndercutStripInnerLayout(
            fullPageStripTop, fullPageStripBottom, titleHeightPt = 9f, hasTotalRail = false,
        )
        assertTrue(
            inner.cylBottom - inner.cylTop <= cylCapFor(fullPageStripTop, fullPageStripBottom) + 1e-2f,
        )
        assertTrue("room above for hand-drawn dimensions", inner.cylTop - fullPageStripTop > 80f)
        assertTrue("room below for hand-written Ø values", fullPageStripBottom - inner.cylBottom > 40f)
        // This is the biggest surplus any real strip produces (no rail band, no Ø band), so it
        // is the one the below-air cap has to be able to absorb: the split must still come out
        // even here, or the write-in space piles up above the cylinder and starves the values.
        val plain = uncapped(fullPageStripTop, fullPageStripBottom, hasTotalRail = false, diaBandPt = 0f)
        val belowExtra = plain.cylBottom - inner.cylBottom
        val aboveExtra = inner.chainRailY - plain.chainRailY
        assertEquals("surplus must split evenly below/above", belowExtra, aboveExtra, 1e-2f)
        assertTrue(belowExtra <= UNDERCUT_CYL_BELOW_EXTRA_MAX_PT + 1e-2f)
    }

    @Test
    fun `a pathologically short strip still keeps every band inside it`() {
        val inner = computeUndercutStripInnerLayout(100f, 104f, titleHeightPt = 9f, hasTotalRail = true)
        assertTrue(inner.totalRailY >= 100f)
        assertTrue(inner.totalRailY <= inner.chainRailY)
        assertTrue(inner.chainRailY <= inner.cylTop)
        assertTrue(inner.cylTop <= inner.cylBottom)
        assertTrue(inner.cylBottom <= 104f)
        assertEquals(0, inner.railLabelRows)
    }

    // ── Measured-Ø stations ──────────────────────────────────────────────────

    @Test
    fun `dia stations sit at the span centre and unmeasured undercuts are skipped`() {
        val measured = Undercut(id = "a", startFromAftMm = 100f, lengthMm = 40f, diaMm = 200f)
        val empty = Undercut(id = "b", startFromAftMm = 200f, lengthMm = 40f, diaMm = 0f)
        val clamped = mapOf(
            "a" to ClampedUndercutSpanMm(100f, 140f),
            "b" to ClampedUndercutSpanMm(200f, 240f),
        )
        val stations = buildUndercutDiaStations(
            listOf(measured, empty), clamped, xAtMm = { it }, unit = mm, labelWidthPt = { it.length * 5f },
        )
        assertEquals(1, stations.size)
        assertEquals("a", stations[0].key)
        assertEquals(120f, stations[0].stationX, 1e-3f)
        assertEquals(formatDiaWithUnit(200.0, mm), stations[0].label)
    }

    @Test
    fun `an undercut clamped fully off the shaft contributes no station`() {
        val u = Undercut(id = "a", startFromAftMm = 900f, lengthMm = 40f, diaMm = 200f)
        val stations = buildUndercutDiaStations(
            listOf(u), mapOf("a" to ClampedUndercutSpanMm(800f, 800f)),
            xAtMm = { it }, unit = mm, labelWidthPt = { 10f },
        )
        assertTrue(stations.isEmpty())
    }
}
