package com.android.shaftschematic.geom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WornSectionMath — layout of a worn section's in-profile measured values (rotated
 * columns + knockout halos). Pins the invariants both draw sites rely on:
 * group centering on the span midpoint, order-preserving left→right columns at a fixed
 * pitch, halos that fully contain the rotated text, and the overflow flag.
 */
class WornSectionMathTest {

    private val lineH = 10f

    @Test
    fun `empty labels lay out to nothing`() {
        val layout = layoutWornSectionValues(100f, 300f, 50f, emptyList(), lineH)
        assertTrue(layout.columns.isEmpty())
        assertFalse(layout.overflows)
    }

    @Test
    fun `a single value centers on the span midpoint and the centerline`() {
        val layout = layoutWornSectionValues(100f, 300f, 50f, listOf(60f), lineH)
        val col = layout.columns.single()
        assertEquals(200f, col.cx, 1e-4f)
        assertEquals(50f, col.cy, 1e-4f)
    }

    @Test
    fun `columns stack left to right at a fixed pitch, group centered`() {
        val layout = layoutWornSectionValues(0f, 400f, 50f, listOf(60f, 60f, 60f), lineH)
        val pitch = lineH * WORN_VALUE_COLUMN_PITCH_FACTOR
        val xs = layout.columns.map { it.cx }
        // Order-preserving, evenly pitched…
        assertEquals(xs.sorted(), xs)
        assertEquals(pitch, xs[1] - xs[0], 1e-4f)
        assertEquals(pitch, xs[2] - xs[1], 1e-4f)
        // …and the middle column sits on the span midpoint.
        assertEquals(200f, xs[1], 1e-4f)
    }

    @Test
    fun `halo contains the rotated text with padding on every side`() {
        val len = 80f
        val layout = layoutWornSectionValues(100f, 300f, 50f, listOf(len), lineH)
        val col = layout.columns.single()
        // Rotated 90°: the text occupies ~lineHeight horizontally and its measured
        // length vertically, centered on (cx, cy).
        assertTrue(col.haloLeft < col.cx - lineH / 2f)
        assertTrue(col.haloRight > col.cx + lineH / 2f)
        assertTrue(col.haloTop < col.cy - len / 2f)
        assertTrue(col.haloBottom > col.cy + len / 2f)
    }

    @Test
    fun `unequal label lengths keep their own halo heights`() {
        val layout = layoutWornSectionValues(0f, 400f, 50f, listOf(40f, 100f), lineH)
        val (short, long) = layout.columns
        assertTrue(long.haloBottom - long.haloTop > short.haloBottom - short.haloTop)
        // Both stay centered on the same centerline.
        assertEquals(short.cy, long.cy, 1e-4f)
    }

    @Test
    fun `degenerate span centers a single column on the station`() {
        // The in-profile dia readings reuse this layout with x0 == x1 (a point station):
        // the lone column must land exactly on the station, halo intact.
        val layout = layoutWornSectionValues(250f, 250f, 50f, listOf(70f), lineH)
        val col = layout.columns.single()
        assertEquals(250f, col.cx, 1e-4f)
        assertEquals(50f, col.cy, 1e-4f)
        assertTrue(col.haloRight > col.haloLeft)
        assertTrue(col.haloBottom > col.haloTop)
    }

    // ── Value text fitting (band-height auto-fit) ─────────────────────────────

    @Test
    fun `text keeps its base size when the value already fits the band`() {
        // needed = 60 + 2*0.3*12 = 67.2 < 100 → no shrink.
        assertEquals(10f, fittedValueTextSize(10f, 6f, 60f, 12f, 100f), 1e-4f)
    }

    @Test
    fun `text shrinks proportionally when the band is too short`() {
        val fitted = fittedValueTextSize(10f, 1f, 60f, 12f, 33.6f)
        // needed at base = 67.2; band = 33.6 → exactly half size.
        assertEquals(5f, fitted, 1e-4f)
    }

    @Test
    fun `shrink floors at the minimum legible size`() {
        assertEquals(6f, fittedValueTextSize(10f, 6f, 60f, 12f, 10f), 1e-4f)
    }

    @Test
    fun `degenerate inputs keep the base size`() {
        assertEquals(10f, fittedValueTextSize(10f, 6f, 0f, 12f, 50f), 1e-4f)
        assertEquals(10f, fittedValueTextSize(10f, 6f, 60f, 12f, 0f), 1e-4f)
    }

    @Test
    fun `overflow flags when the group is wider than the span`() {
        val narrow = layoutWornSectionValues(0f, 30f, 50f, listOf(60f, 60f, 60f), lineH)
        assertTrue(narrow.overflows)
        val wide = layoutWornSectionValues(0f, 400f, 50f, listOf(60f, 60f, 60f), lineH)
        assertFalse(wide.overflows)
        // Overflowing still lays out every column (drawn centered, never dropped).
        assertEquals(3, narrow.columns.size)
    }
}
