package com.android.shaftschematic.geom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Hit-test / coordinate tests for `geom/WearDiaMath.kt` — mirrors `WearPitMathTest`. */
class WearDiaMathTest {

    @Test
    fun `diaAxialLocalMm converts shaft space to component local and never goes negative`() {
        assertEquals(30f, diaAxialLocalMm(130f, 100f), 0.001f)
        assertEquals(0f, diaAxialLocalMm(90f, 100f), 0.001f)
    }

    @Test
    fun `tap on a tick picks it, within pad`() {
        val targets = listOf(DiaHitTarget("a", cx = 100f, topY = 50f, botY = 150f))
        // Directly on the segment.
        assertEquals("a", pickDiaReadingAt(100f, 100f, targets, padPx = 10f))
        // Horizontally within pad.
        assertEquals("a", pickDiaReadingAt(108f, 100f, targets, padPx = 10f))
        // Vertically past the end but within pad of the endpoint.
        assertEquals("a", pickDiaReadingAt(100f, 158f, targets, padPx = 10f))
    }

    @Test
    fun `tap outside pad misses`() {
        val targets = listOf(DiaHitTarget("a", cx = 100f, topY = 50f, botY = 150f))
        assertNull(pickDiaReadingAt(120f, 100f, targets, padPx = 10f))
        // Diagonal from the endpoint: dx=8, dy=8 → dist ≈ 11.3 > 10.
        assertNull(pickDiaReadingAt(108f, 158f, targets, padPx = 10f))
    }

    @Test
    fun `nearest tick wins when two are within pad`() {
        val targets = listOf(
            DiaHitTarget("near", cx = 100f, topY = 50f, botY = 150f),
            DiaHitTarget("far", cx = 112f, topY = 50f, botY = 150f),
        )
        assertEquals("near", pickDiaReadingAt(104f, 100f, targets, padPx = 10f))
        assertEquals("far", pickDiaReadingAt(109f, 100f, targets, padPx = 10f))
    }
}
