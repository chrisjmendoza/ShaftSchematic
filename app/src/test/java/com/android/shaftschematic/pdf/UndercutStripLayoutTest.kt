package com.android.shaftschematic.pdf

import com.android.shaftschematic.geom.ClampedUndercutSpanMm
import com.android.shaftschematic.geom.UndercutSpanMm
import com.android.shaftschematic.geom.UndercutWindow
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

    // ── Chained rail: coverage, omissions, overlaps ──────────────────────────

    @Test
    fun `chain covers the window exactly with pads, cuts and gaps`() {
        val w = window(100f, 300f, "a", "b")
        val spans = listOf(span("a", 130f, 160f), span("b", 200f, 240f))
        val rail = buildUndercutRailSpans(w, spans, mm)

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
        val w = window(50f, 150f, "a")
        val rail = buildUndercutRailSpans(w, listOf(span("a", 50f, 150f)), mm)
        assertEquals(1, rail.size)
        assertEquals(w.lengthMm, rail.totalLengthMm(), 1e-3f)

        // Two back-to-back cuts: no gap span between them.
        val w2 = window(0f, 100f, "a", "b")
        val rail2 = buildUndercutRailSpans(w2, listOf(span("a", 20f, 50f), span("b", 50f, 80f)), mm)
        assertEquals(4, rail2.size)   // pad, a, b, pad — no zero-width gap
        assertEquals(w2.lengthMm, rail2.totalLengthMm(), 1e-3f)
        assertTrue(rail2.none { it.endMm - it.startMm <= 1e-3f })
    }

    @Test
    fun `overlapping undercuts are never double-counted and the chain never runs backward`() {
        val w = window(0f, 200f, "a", "b")
        val spans = listOf(span("a", 40f, 120f), span("b", 80f, 140f))
        val rail = buildUndercutRailSpans(w, spans, mm)

        assertEquals(w.lengthMm, rail.totalLengthMm(), 1e-3f)
        rail.forEach { assertTrue("span must run aft→fwd", it.endMm > it.startMm) }
        rail.zipWithNext().forEach { (p, n) -> assertEquals(p.endMm, n.startMm, 1e-3f) }
        // The overlap reads as belonging to the first cut: the second contributes 120→140.
        assertTrue(rail.any { kotlin.math.abs(it.startMm - 120f) < 1e-3f && kotlin.math.abs(it.endMm - 140f) < 1e-3f })
    }

    @Test
    fun `a cluster with no drawable spans still dimensions the whole window`() {
        val w = window(10f, 90f)
        val rail = buildUndercutRailSpans(w, emptyList(), mm)
        assertEquals(1, rail.size)
        assertEquals(w.lengthMm, rail.totalLengthMm(), 1e-3f)
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
