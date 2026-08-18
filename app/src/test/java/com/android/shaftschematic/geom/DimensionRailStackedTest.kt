package com.android.shaftschematic.geom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two planner-level changes stacking brought, both pure geometry:
 *
 * 1. **A taller value is expressed by inflating the ascent**, and every derived quantity — the
 *    band, the per-rail lifts, the collision boxes — follows from it with no other change.
 * 2. **Extension lines are obstacles.** A rail's extension lines run from the object up to that
 *    rail, so they cross the band of every rail below it, which is exactly where those rails park
 *    a floating value (`docs/DualUnitStacking_PLAN.md` §1b — an extension line printed straight
 *    through the tail of a dual label on a real sheet).
 */
class DimensionRailStackedTest {

    // 10 pt glyph box; the stacked variant inflates the ascent by one advance (11 pt here).
    private val single = DimensionRailLayout.TextMetrics(ascent = -8f, descent = 2f, aboveDy = 12f)
    private val advance = 11f
    private val stacked = single.copy(ascent = single.ascent - advance)
    private val arrow = 5f
    private val pad = 6f

    private fun span(rail: Int, railY: Float, xa: Float, xb: Float, w: Float) =
        DimensionRailLayout.SpanInput(railIndex = rail, railY = railY, xa = xa, xb = xb, labelWidth = w)

    @Test
    fun `an inflated ascent grows the box and the band by exactly one advance`() {
        assertEquals(single.height + advance, stacked.height, 1e-4f)
        assertEquals(single.band + advance, stacked.band, 1e-4f)
        // The descent is untouched, which is what keeps a fallback value's BOTTOM line at the
        // clearance above the rail it always had.
        assertEquals(single.descent, stacked.descent, 1e-4f)
    }

    @Test
    fun `every lift scales with the taller band`() {
        // Rail 0 floats its value (label far wider than its span); rails above it must lift.
        val spans = listOf(
            span(0, 200f, 0f, 40f, 120f),
            span(1, 170f, 0f, 400f, 40f),
            span(DimensionRailLayout.TOP_RAIL, 140f, 0f, 400f, 40f),
        )
        val liftSingle = DimensionRailLayout.topLift(spans, single, arrow, pad)
        val liftStacked = DimensionRailLayout.topLift(spans, stacked, arrow, pad)
        assertTrue("rail 0 should float, producing a lift", liftSingle > 0f)
        assertEquals(liftSingle + advance, liftStacked, 1e-4f)
    }

    @Test
    fun `a narrower stacked value seats in the line where the inline pair could not`() {
        // Same span, two widths: the inline dual pair (100) overflows it, the stack (45) does not.
        // The break costs `labelWidth + 2·textPad + 2·arrowSize` — 122 pt for the inline pair here,
        // 67 pt for the stack — so a 115 pt span takes one and not the other.
        val inlineWidth = 100f
        val stackedWidth = 45f
        val xa = 0f
        val xb = 115f

        val inlinePlan = DimensionRailLayout.plan(
            listOf(span(0, 200f, xa, xb, inlineWidth)), single, arrow, pad,
        )
        val stackedPlan = DimensionRailLayout.plan(
            listOf(span(0, 200f, xa, xb, stackedWidth)), stacked, arrow, pad,
        )
        assertTrue("the inline pair should overflow this span", !inlinePlan.placements[0].inline)
        assertTrue("the stack should seat in the break", stackedPlan.placements[0].inline)
    }

    @Test
    fun `a floating label clears a neighbouring rail's extension line`() {
        // Rail 1 spans 100..300, so it draws extension lines at x=100 and x=300 running DOWN past
        // rail 0's band. Rail 0's value floats and its label is wide enough to sit across x=300,
        // which is exactly the strike-through the printed sheet showed.
        val spans = listOf(
            span(0, 200f, 250f, 290f, 90f),     // floats (label wider than span)
            span(1, 170f, 100f, 300f, 30f),     // its extensions cross rail 0's band
        )
        val plan = DimensionRailLayout.plan(spans, single, arrow, pad, safeTopY = 0f)
        val label = plan.placements[0].label

        // Rail 1's extension at x=300 runs from ITS rail line (y=170) down to the object, so the
        // label clears it either by sliding aside or by rising above its top end — this label is
        // wider than its own span and has no slide room, so the planner lifts it instead.
        val extensionBox = DimensionRailLayout.Box(
            300f - DimensionRailLayout.LINE_HALF_CLEAR, 170f,
            300f + DimensionRailLayout.LINE_HALF_CLEAR, Float.MAX_VALUE,
        )
        assertTrue(
            "label $label should not overlap the extension line at x=300",
            !label.intersects(extensionBox),
        )
        assertTrue("the label should have been lifted clear", label.bottom < 170f)
    }

    @Test
    fun `a span's OWN extension lines never evict its label`() {
        // A label wider than its span legitimately overhangs its own extension lines. Counting
        // them would make the case unsolvable and bump the value away for nothing.
        val spans = listOf(span(0, 200f, 100f, 140f, 120f))
        val plan = DimensionRailLayout.plan(spans, single, arrow, pad, safeTopY = 0f)
        val p = plan.placements[0]
        // Centred on its span, at its own rail's band — not bumped upward.
        assertEquals(120f, p.cx, 1f)
        assertEquals(200f - single.aboveDy + single.ascent, p.label.top, 1e-3f)
    }

    @Test
    fun `a chained neighbour sharing a boundary counts as own, not as an obstacle`() {
        // Two spans meeting at x=200: the extension line there belongs to both. If the shared line
        // were treated as foreign, every chained inline value would be pushed off its centre.
        val spans = listOf(
            span(0, 200f, 0f, 200f, 40f),
            span(0, 200f, 200f, 400f, 40f),
        )
        val plan = DimensionRailLayout.plan(spans, single, arrow, pad)
        assertTrue(plan.placements.all { it.inline })
        assertEquals(100f, plan.placements[0].cx, 1f)
        assertEquals(300f, plan.placements[1].cx, 1f)
        assertNotEquals(plan.placements[0].cx, plan.placements[1].cx)
    }
}
