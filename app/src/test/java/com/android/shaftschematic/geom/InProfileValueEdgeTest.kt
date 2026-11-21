package com.android.shaftschematic.geom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A measured Ø printed inside the profile near a component's end keeps its DIGITS off the end
 * line (on-device report: the knockout covered the liner's end line — the outline takes
 * precedence; the knockout itself is clipped to the part when drawn), moving only as far as it
 * must, and never so its knockout lands on another value's digits or its digits under another
 * knockout (manual-figure check: a value pushed off a liner end landed on a worn-section value).
 */
class InProfileValueEdgeTest {

    /** A column: halo [cx − 7, cx + 7] — 10 of digits with a 2 pad each side. */
    private fun col(cx: Float) =
        WornValueColumn(cx = cx, cy = 50f, haloLeft = cx - 7f, haloTop = 20f, haloRight = cx + 7f, haloBottom = 80f)

    private fun req(cx: Float, lo: Float = 100f, hi: Float = 300f) = ValueColumnRequest(col(cx), lo, hi, pad = 2f)

    private fun digits(c: WornValueColumn) = (c.haloLeft + 2f)..(c.haloRight - 2f)

    @Test fun `a value whose digits are clear does not move, even with its pad over the end line`() {
        // Digits 103..113 clear the 3-wide clearance; only the pad crosses x = 100.
        assertEquals(col(108f), placeValueColumnsClear(listOf(req(108f)), 3f).single())
        assertEquals(listOf(col(150f)), placeValueColumnsClear(listOf(req(150f)), 3f))
    }

    @Test fun `a value whose digits cross an end line moves in just far enough`() {
        val left = placeValueColumnsClear(listOf(req(101f)), 3f).single()
        assertEquals(103f, digits(left).start, 1e-4f)
        val right = placeValueColumnsClear(listOf(req(299f)), 3f).single()
        assertEquals(297f, digits(right).endInclusive, 1e-4f)
        // Only x moves.
        assertEquals(20f, right.haloTop, 0f)
        assertEquals(80f, right.haloBottom, 0f)
    }

    @Test fun `a value moved off an end line lands clear of a worn-section value beside it`() {
        // Worn halo 114..128 (digits 116..126): the end-line spot 101..115 clears it.
        val worn = ValueBox(114f, 128f, pad = 2f)
        val placed = placeValueColumnsClear(listOf(req(101f)), 3f, obstacles = listOf(worn)).single()
        assertEquals(103f, digits(placed).start, 1e-4f)
        assertTrue("knockout clear of the worn digits", placed.haloRight <= 116f + 1e-3f)
    }

    @Test fun `with no clear spot within reach the value still clears the end line, overlapping least`() {
        // Worn halo 110..124 (digits 112..122): nothing within one knockout width is clear, so the
        // outline wins and the value takes the end-line spot, the least overlap in reach.
        val worn = ValueBox(110f, 124f, pad = 2f)
        val placed = placeValueColumnsClear(listOf(req(101f)), 3f, obstacles = listOf(worn)).single()
        assertEquals(103f, digits(placed).start, 1e-4f)
    }

    @Test fun `a value never jumps to the far end of its part`() {
        // A clear spot exists only far up the span, past the worn value; the value neither
        // travels there nor crosses the worn value — it stays AFT of it, in station order.
        val worn = ValueBox(108f, 200f, pad = 2f)
        val placed = placeValueColumnsClear(listOf(req(101f)), 3f, obstacles = listOf(worn)).single()
        assertTrue("moved ${placed.haloLeft - 94f}", placed.haloLeft - 94f <= 28f + 1e-3f)
        assertTrue("stays AFT of the worn value", placed.cx <= 154f)
    }

    @Test fun `two knockouts may share their pads`() {
        // Digits 4 apart: each knockout reaches only the other's pad.
        val worn = ValueBox(100f, 114f, pad = 2f)
        val c = ValueColumnRequest(col(125f), 50f, 300f, pad = 2f)
        assertEquals(col(125f), placeValueColumnsClear(listOf(c), 3f, obstacles = listOf(worn)).single())
    }

    @Test fun `values the move would stack are set apart in order`() {
        val placed = placeValueColumnsClear(listOf(req(100f), req(102f)), 3f)
        assertTrue(placed[0].haloRight <= digits(placed[1]).start + 1e-3f)
        assertTrue(digits(placed[0]).start >= 103f - 1e-4f)
    }

    @Test fun `when station order rules out every spot the value still clears the end line`() {
        // A value planned AFT of an earlier one that was pushed past its end-line spot.
        val first = ValueColumnRequest(col(100f), 100f, 300f, pad = 2f)
        val second = ValueColumnRequest(col(100.5f), 100f, 300f, pad = 2f)
        val placed = placeValueColumnsClear(listOf(first, second), 3f)
        assertTrue("digits clear of the end line", digits(placed[1]).start >= 103f - 1e-3f)
    }

    @Test fun `with no clear spot in its span a value stays where it was planned`() {
        val placed = placeValueColumnsClear(listOf(ValueColumnRequest(col(105f), 100f, 112f, pad = 2f)), 3f).single()
        assertEquals(col(105f), placed)
    }
}
