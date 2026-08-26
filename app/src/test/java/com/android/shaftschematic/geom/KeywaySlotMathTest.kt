package com.android.shaftschematic.geom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `geom/KeywaySlotMath.kt` — the plan-view slot's transverse half-width and the drawn-length
 * guard that keeps its mill arcs inside its own walls.
 */
class KeywaySlotMathTest {

    private val eps = 1e-3f

    @Test
    fun `a slot wider than the floor draws true`() {
        assertEquals(
            10f,
            drawnKeywayHalfWidthPx(trueHalfWidthPx = 10f, hostRadiusPx = 50f, minWidthPx = 3f, strokeWidthPx = 1f),
            eps,
        )
    }

    @Test
    fun `a sub-pixel slot takes the visibility floor`() {
        // Floor is a WIDTH, so the half-width is half of it.
        assertEquals(
            1.5f,
            drawnKeywayHalfWidthPx(trueHalfWidthPx = 0.2f, hostRadiusPx = 50f, minWidthPx = 3f, strokeWidthPx = 0.5f),
            eps,
        )
    }

    @Test
    fun `a heavy line raises the floor so the two walls cannot merge`() {
        // 4 pt stroke (200% line thickness on the runout sheet) needs more than the flat 3 pt.
        val half = drawnKeywayHalfWidthPx(trueHalfWidthPx = 0.2f, hostRadiusPx = 50f, minWidthPx = 3f, strokeWidthPx = 4f)
        assertEquals(4f * KEYWAY_MIN_WIDTH_STROKES / 2f, half, eps)
        assertTrue("the walls keep daylight between them", half * 2f > 4f)
    }

    @Test
    fun `the floor yields to a small host rather than swallowing it`() {
        // A 6 pt radius host: the floor would eat most of it, so the cap wins.
        val half = drawnKeywayHalfWidthPx(trueHalfWidthPx = 0.2f, hostRadiusPx = 6f, minWidthPx = 8f, strokeWidthPx = 1f)
        assertEquals(6f * MAX_KEYWAY_FRAC_OF_HOST_DIA, half, eps)
        assertTrue(half < 4f)
    }

    @Test
    fun `a degenerate width draws nothing`() {
        assertEquals(0f, drawnKeywayHalfWidthPx(0f, 50f, 3f, 1f), eps)
    }

    @Test
    fun `an unknown host radius leaves the floor uncapped`() {
        assertEquals(4f, drawnKeywayHalfWidthPx(trueHalfWidthPx = 0.2f, hostRadiusPx = 0f, minWidthPx = 8f, strokeWidthPx = 1f), eps)
    }

    /**
     * The width comes off the diameter scale and the length off the compressed axial map, so
     * the drawn length has to clear the arcs the width demands.
     */
    @Test
    fun `an open slot reserves one arc and a floating slot two`() {
        assertEquals(10f, minKeywaySlotLenPx(halfWidthPx = 10f, openEnd = true), eps)
        assertEquals(20f, minKeywaySlotLenPx(halfWidthPx = 10f, openEnd = false), eps)
    }
}
