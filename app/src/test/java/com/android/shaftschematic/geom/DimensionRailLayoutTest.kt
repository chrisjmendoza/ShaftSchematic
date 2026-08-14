package com.android.shaftschematic.geom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DimensionRailLayout — the shared collision space behind stacked dimension rails. Pins:
 * above-line labels never strike another label or another tier's rail line; a colliding label
 * slides along its own span before it bumps; every rail above a fallback-carrying rail lifts by
 * one label band (cumulative); an all-inline sheet is untouched (naive centered layout, zero
 * lift); an over-wide label still places; arrowheads turn outward only on a span too narrow to
 * hold both, never merely because the value fell back.
 */
class DimensionRailLayoutTest {

    // Realistic 8.5 pt dimension text: 10 pt tall glyph box, 16 pt label band.
    private val metrics = DimensionRailLayout.TextMetrics(ascent = -8f, descent = 2f, aboveDy = 12f)
    private val arrow = 5f
    private val pad = 6f
    private val band = metrics.band  // 16

    private fun span(rail: Int, railY: Float, xa: Float, xb: Float, w: Float) =
        DimensionRailLayout.SpanInput(railIndex = rail, railY = railY, xa = xa, xb = xb, labelWidth = w)

    private fun plan(
        spans: List<DimensionRailLayout.SpanInput>,
        safeTopY: Float = 0f,
    ) = DimensionRailLayout.plan(spans, metrics, arrow, pad, safeTopY)

    /** The rail line each span draws, as the planner treats it: an obstacle band. */
    private fun lineBox(s: DimensionRailLayout.SpanInput, p: DimensionRailLayout.Placement) =
        DimensionRailLayout.Box(
            s.xa,
            p.railY - DimensionRailLayout.LINE_HALF_CLEAR,
            s.xb,
            p.railY + DimensionRailLayout.LINE_HALF_CLEAR,
        )

    private fun assertLabelsClear(spans: List<DimensionRailLayout.SpanInput>, plan: DimensionRailLayout.Plan) {
        val placements = plan.placements
        for (i in placements.indices) {
            for (j in placements.indices) {
                if (i == j) continue
                assertFalse(
                    "label $i overlaps label $j",
                    placements[i].label.intersects(placements[j].label),
                )
                assertFalse(
                    "label $i is struck through by span $j's rail line",
                    placements[i].label.intersects(lineBox(spans[j], placements[j])),
                )
            }
        }
    }

    @Test
    fun `two short spans on different rails do not print on top of each other`() {
        // The 19 1/2 / 21 1/2 case: both spans are too short to seat their value in the line,
        // so both float above — into the next rail's band, on overlapping x.
        val spans = listOf(
            span(rail = 0, railY = 400f, xa = 100f, xb = 140f, w = 30f),
            span(rail = 1, railY = 382f, xa = 110f, xb = 150f, w = 30f),
        )
        val p = plan(spans)

        assertFalse("short span falls back", p.placements[0].inline)
        assertFalse("short span falls back", p.placements[1].inline)
        assertLabelsClear(spans, p)
    }

    @Test
    fun `a wide span slides along its own span instead of overlapping a neighbour's label`() {
        // The 24 1/2 / 11 1/2 case at the schematic's tightest rail gap (10 pt): the short
        // span's floating value sits in the wide span's band.
        val short = span(rail = 0, railY = 400f, xa = 200f, xb = 240f, w = 30f)
        val wide = span(rail = 1, railY = 390f, xa = 100f, xb = 400f, w = 30f)
        val spans = listOf(short, wide)
        val p = plan(spans)

        assertFalse("short span falls back", p.placements[0].inline)
        assertTrue("wide span keeps the value in the line break", p.placements[1].inline)

        val cx = p.placements[1].cx
        assertNotEquals("wide label slid off center", 250f, cx, 0.5f)
        // Smallest shift that clears: the short label occupies x [205, 235], so centers in
        // (205 − 15 − 3, 235 + 15 + 3) are blocked and 253 is the nearest allowed to mid 250.
        assertEquals(253f, cx, 1e-3f)
        // Still inside its own span, with room for both inward arrowheads.
        val half = wide.labelWidth * 0.5f
        assertTrue("slid label stays inside its span", cx >= wide.xa + pad + half + arrow)
        assertTrue("slid label stays inside its span", cx <= wide.xb - pad - half - arrow)
        assertTrue(
            "inward arrows still fit",
            DimensionRailLayout.canFitInwardArrows(wide.xa, wide.xb, cx, wide.labelWidth, pad, arrow),
        )
        assertLabelsClear(spans, p)
    }

    @Test
    fun `a rail above a fallback rail lifts by exactly one label band`() {
        val spans = listOf(
            span(rail = 0, railY = 400f, xa = 100f, xb = 140f, w = 30f),   // short → floats above
            span(rail = 1, railY = 382f, xa = 100f, xb = 400f, w = 30f),   // wide → inline
            span(rail = DimensionRailLayout.TOP_RAIL, railY = 340f, xa = 90f, xb = 500f, w = 40f),
        )
        val p = plan(spans)

        assertEquals(0f, p.liftFor(0), 1e-3f)
        assertEquals(band, p.liftFor(1), 1e-3f)
        assertEquals("the OAL rail is above every numbered rail", band, p.liftFor(DimensionRailLayout.TOP_RAIL), 1e-3f)
        assertEquals(band, DimensionRailLayout.topLift(spans, metrics, arrow, pad), 1e-3f)
        assertEquals(382f - band, p.placements[1].railY, 1e-3f)
        assertEquals(340f - band, p.placements[2].railY, 1e-3f)
    }

    @Test
    fun `two fallback rails lift the tiers above them cumulatively`() {
        val spans = listOf(
            span(rail = 0, railY = 400f, xa = 100f, xb = 140f, w = 30f),   // short
            span(rail = 1, railY = 382f, xa = 300f, xb = 340f, w = 30f),   // short
            span(rail = 2, railY = 364f, xa = 100f, xb = 400f, w = 30f),   // wide
            span(rail = DimensionRailLayout.TOP_RAIL, railY = 320f, xa = 90f, xb = 500f, w = 40f),
        )
        val p = plan(spans)

        assertEquals(0f, p.liftFor(0), 1e-3f)
        assertEquals(band, p.liftFor(1), 1e-3f)
        assertEquals(2f * band, p.liftFor(2), 1e-3f)
        assertEquals(2f * band, p.liftFor(DimensionRailLayout.TOP_RAIL), 1e-3f)
        assertEquals(2f * band, DimensionRailLayout.topLift(spans, metrics, arrow, pad), 1e-3f)
        assertLabelsClear(spans, p)
    }

    @Test
    fun `all-inline sheet is untouched - zero lift, naive centered labels`() {
        val spans = listOf(
            span(rail = 0, railY = 400f, xa = 100f, xb = 300f, w = 30f),
            span(rail = 0, railY = 400f, xa = 320f, xb = 520f, w = 30f),
            span(rail = 1, railY = 382f, xa = 100f, xb = 520f, w = 30f),
            span(rail = DimensionRailLayout.TOP_RAIL, railY = 340f, xa = 90f, xb = 540f, w = 40f),
        )
        val p = plan(spans)

        assertTrue("nothing lifts when nothing floats", p.liftByRail.values.all { it == 0f })
        spans.forEachIndexed { i, s ->
            assertTrue("span $i seats its value in the line", p.placements[i].inline)
            assertEquals("span $i label stays centered", (s.xa + s.xb) * 0.5f, p.placements[i].cx, 1e-3f)
            assertEquals("span $i line stays put", s.railY, p.placements[i].railY, 1e-3f)
        }
    }

    @Test
    fun `a label wider than its span still places, centered on the span`() {
        val s = span(rail = 0, railY = 400f, xa = 200f, xb = 210f, w = 80f)
        val p = plan(listOf(s))

        assertFalse(p.placements[0].inline)
        assertEquals(205f, p.placements[0].cx, 1e-3f)
        assertEquals(80f, p.placements[0].label.right - p.placements[0].label.left, 1e-3f)
    }

    @Test
    fun `blank-draft write-in windows are reserved without overlapping`() {
        // Blank drafts reserve a fixed write-in width instead of measuring value text.
        val blankW = 46f
        val spans = listOf(
            span(rail = 0, railY = 400f, xa = 100f, xb = 150f, w = blankW),
            span(rail = 1, railY = 382f, xa = 120f, xb = 170f, w = blankW),
            span(rail = DimensionRailLayout.TOP_RAIL, railY = 340f, xa = 90f, xb = 500f, w = blankW),
        )
        val p = plan(spans)

        assertEquals(2f * band, DimensionRailLayout.topLift(spans, metrics, arrow, pad), 1e-3f)
        p.placements.forEach {
            assertEquals("write-in gap keeps the fixed width", blankW, it.label.right - it.label.left, 1e-3f)
        }
        assertLabelsClear(spans, p)
    }

    @Test
    fun `a fallback span keeps its inward arrows when the span itself has room`() {
        // 40 pt of rail with a 30 pt value: the value cannot seat in the line, but two 5 pt
        // heads fit between the extension lines easily. Tying direction to the value's
        // placement spent the cramped tips-in convention here, and adjacent spans' outward
        // heads then crossed at their shared boundary.
        val spans = listOf(
            span(rail = 0, railY = 400f, xa = 100f, xb = 140f, w = 30f),
            span(rail = 1, railY = 382f, xa = 100f, xb = 400f, w = 30f),
        )
        val p = plan(spans)

        assertFalse("short span falls back", p.placements[0].inline)
        assertTrue("...but it still has arrow room", p.placements[0].arrowsInward)
        assertTrue("an inline span always has arrow room", p.placements[1].arrowsInward)
    }

    @Test
    fun `only a span too narrow for both arrowheads prints them outward`() {
        val narrow = span(rail = 0, railY = 400f, xa = 200f, xb = 208f, w = 30f)
        assertFalse(plan(listOf(narrow)).placements[0].arrowsInward)

        // One point of daylight past 2×arrow + ARROW_CLEAR is enough to turn them in.
        val wide = span(rail = 0, railY = 400f, xa = 200f, xb = 213f, w = 30f)
        assertTrue(plan(listOf(wide)).placements[0].arrowsInward)
    }

    // ── Blank-draft write-in gaps ────────────────────────────────────────────

    private fun blankGap(spanWidth: Float, preferred: Float = 60f, min: Float = 28f) =
        DimensionRailLayout.blankGapWidth(spanWidth, preferred, min, pad, arrow)

    @Test
    fun `a span with room keeps the full write-in gap`() {
        // 60 gap + 2×6 pad + 2×5 arrows + slack.
        assertEquals(60f, blankGap(200f), 1e-3f)
        assertEquals(60f, blankGap(82.5f), 1e-3f)
    }

    @Test
    fun `a short span cuts a narrower gap instead of losing it`() {
        // The on-device case: a rail wide enough to write in, but not for the full 60 pt gap,
        // used to fall back to a continuous line with nowhere to write at all.
        val gap = blankGap(80f)
        assertTrue("gap shrank", gap < 60f)
        assertTrue("...but stays writable", gap >= 28f)
        // A shrunk gap must still pass its own eligibility test, or the shrink buys nothing.
        val s = span(rail = 0, railY = 400f, xa = 100f, xb = 180f, w = gap)
        val p = plan(listOf(s))
        assertTrue("shrunk gap seats in the line", p.placements[0].inline)
        assertTrue(p.placements[0].arrowsInward)
    }

    @Test
    fun `a span too tight for even the minimum gap keeps the continuous-line fallback`() {
        // Handed back the preferred width, which fails eligibility → fallback, no cut gap.
        assertEquals(60f, blankGap(45f), 1e-3f)
        val s = span(rail = 0, railY = 400f, xa = 100f, xb = 145f, w = blankGap(45f))
        assertFalse(plan(listOf(s)).placements[0].inline)
    }

    @Test
    fun `bumped labels never climb past the content top`() {
        // Three short spans stacked at the same x on one rail: sliding has no room, so the
        // planner bumps — bounded by the safe top.
        val spans = listOf(
            span(rail = 0, railY = 60f, xa = 100f, xb = 140f, w = 30f),
            span(rail = 0, railY = 60f, xa = 102f, xb = 142f, w = 30f),
            span(rail = 0, railY = 60f, xa = 104f, xb = 144f, w = 30f),
        )
        val p = plan(spans, safeTopY = 20f)
        p.placements.forEach { assertTrue("label stays below the safe top", it.label.top >= 20f - 1e-3f) }
    }
}
