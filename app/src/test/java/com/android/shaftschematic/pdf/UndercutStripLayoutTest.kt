package com.android.shaftschematic.pdf

import com.android.shaftschematic.geom.ClampedUndercutSpanMm
import com.android.shaftschematic.geom.UndercutLinerSpan
import com.android.shaftschematic.geom.UndercutSpanMm
import com.android.shaftschematic.geom.UndercutStrip
import com.android.shaftschematic.geom.UndercutWindow
import com.android.shaftschematic.geom.buildUndercutStrips
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
 * (`docs/UndercutDrawing_PLAN.md` §8) — plain JVM assertions, no Robolectric, exercising
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
