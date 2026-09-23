package com.android.shaftschematic.geom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val EPS = 1e-4f

/** The hidden-bore construction both draw sites share for a cross-drilled hole 90° from the keyway. */
class BoltHoleMathTest {

    @Test
    fun `through hole runs surface to surface, one hole width wide, no floor`() {
        val b = crossBoreLines(cx = 100f, holeR = 5f, cy = 50f, rSurface = 20f, through = true, depthPx = 0f)
        assertNotNull(b); b!!
        assertEquals(95f, b.x1, EPS)
        assertEquals(105f, b.x2, EPS)
        assertEquals(30f, b.yTop, EPS)
        assertEquals(70f, b.yBottom, EPS)
        assertFalse(b.floor)
    }

    @Test
    fun `blind hole stops at its depth below the top surface and draws a floor`() {
        val b = crossBoreLines(cx = 100f, holeR = 5f, cy = 50f, rSurface = 20f, through = false, depthPx = 12f)
        assertNotNull(b); b!!
        assertEquals(30f, b.yTop, EPS)
        assertEquals(42f, b.yBottom, EPS)
        assertTrue(b.floor)
    }

    @Test
    fun `blind depth past the far surface clamps to it and loses the floor`() {
        val b = crossBoreLines(cx = 100f, holeR = 5f, cy = 50f, rSurface = 20f, through = false, depthPx = 99f)
        assertNotNull(b); b!!
        assertEquals(70f, b.yBottom, EPS)
        assertFalse(b.floor)
    }

    @Test
    fun `placed-but-empty blind hole and degenerate radii draw nothing`() {
        assertNull(crossBoreLines(100f, 5f, 50f, 20f, through = false, depthPx = 0f))
        assertNull(crossBoreLines(100f, 0f, 50f, 20f, through = true, depthPx = 0f))
        assertNull(crossBoreLines(100f, 5f, 50f, 0f, through = true, depthPx = 0f))
    }
}
