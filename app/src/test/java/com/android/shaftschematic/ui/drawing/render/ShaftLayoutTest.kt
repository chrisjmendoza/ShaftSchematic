package com.android.shaftschematic.ui.drawing.render

import com.android.shaftschematic.model.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for ShaftLayout rendering calculations.
 *
 * Tests cover:
 * - MM to PX mapping
 * - Scale calculation (fits both axes)
 * - Centerline positioning
 * - Margin handling
 */
class ShaftLayoutTest {

    @Test
    fun `compute returns valid result for empty spec`() {
        val spec = ShaftSpec(overallLengthMm = 1000f)
        val result = ShaftLayout.compute(
            spec = spec,
            leftPx = 0f,
            topPx = 0f,
            rightPx = 800f,
            bottomPx = 600f,
            marginPx = 12f
        )

        assertTrue(result.pxPerMm > 0f)
        assertEquals(spec, result.spec)
        assertEquals(0f, result.minXMm, 0.001f)
        assertTrue(result.maxXMm >= result.minXMm)
    }

    @Test
    fun `compute applies margins correctly`() {
        val spec = ShaftSpec(overallLengthMm = 1000f)
        val margin = 20f
        val result = ShaftLayout.compute(
            spec = spec,
            leftPx = 0f,
            topPx = 0f,
            rightPx = 800f,
            bottomPx = 600f,
            marginPx = margin
        )

        assertEquals(margin, result.contentLeftPx, 0.001f)
        assertEquals(margin, result.contentTopPx, 0.001f)
        assertEquals(800f - margin, result.contentRightPx, 0.001f)
        assertEquals(600f - margin, result.contentBottomPx, 0.001f)
    }

    @Test
    fun `compute scales to fit both width and height`() {
        // Tall shaft (large diameter, short length)
        val spec = ShaftSpec(
            overallLengthMm = 100f,
            bodies = listOf(Body(diaMm = 200f))
        )
        
        val result = ShaftLayout.compute(
            spec = spec,
            leftPx = 0f,
            topPx = 0f,
            rightPx = 1000f,
            bottomPx = 400f,
            marginPx = 0f
        )

        // Should be constrained by height (400px / 200mm diameter)
        // not by width
        assertTrue(result.pxPerMm <= 400f / 200f)
    }

    @Test
    fun `xPx maps millimeters to pixels correctly`() {
        val spec = ShaftSpec(overallLengthMm = 1000f)
        val result = ShaftLayout.compute(
            spec = spec,
            leftPx = 0f,
            topPx = 0f,
            rightPx = 1000f,
            bottomPx = 600f,
            marginPx = 0f
        )

        // At x=0mm, should be at contentLeft
        val x0 = result.xPx(0f)
        assertEquals(result.contentLeftPx, x0, 0.1f)

        // At x=overallLength, should be further right
        val xMax = result.xPx(spec.overallLengthMm)
        assertTrue(xMax > x0)
    }

    @Test
    fun `rPx maps diameter to radius in pixels`() {
        val spec = ShaftSpec(overallLengthMm = 1000f)
        val result = ShaftLayout.compute(
            spec = spec,
            leftPx = 0f,
            topPx = 0f,
            rightPx = 1000f,
            bottomPx = 600f,
            marginPx = 0f
        )

        // 100mm diameter → 50mm radius
        val radiusPx = result.rPx(100f)
        assertEquals(50f * result.pxPerMm, radiusPx, 0.01f)
    }

    @Test
    fun `centerlineYPx is at vertical midpoint of content area`() {
        val spec = ShaftSpec(overallLengthMm = 1000f)
        val result = ShaftLayout.compute(
            spec = spec,
            leftPx = 0f,
            topPx = 0f,
            rightPx = 1000f,
            bottomPx = 600f,
            marginPx = 20f
        )

        val expectedCenterY = (result.contentTopPx + result.contentBottomPx) * 0.5f
        assertEquals(expectedCenterY, result.centerlineYPx, 0.1f)
    }

    @Test
    fun `compute handles inverted rect coordinates`() {
        val spec = ShaftSpec(overallLengthMm = 1000f)
        // Pass bottom < top
        val result = ShaftLayout.compute(
            spec = spec,
            leftPx = 100f,
            topPx = 600f,
            rightPx = 800f,
            bottomPx = 0f,
            marginPx = 0f
        )

        // Should normalize to proper bounds
        assertTrue(result.contentLeftPx < result.contentRightPx)
        assertTrue(result.contentTopPx < result.contentBottomPx)
    }

    @Test
    fun `compute ensures minimum scale to avoid division by zero`() {
        // Very small shaft
        val spec = ShaftSpec(overallLengthMm = 0.001f)
        val result = ShaftLayout.compute(
            spec = spec,
            leftPx = 0f,
            topPx = 0f,
            rightPx = 10f,
            bottomPx = 10f,
            marginPx = 0f
        )

        // Should have minimum scale threshold
        assertTrue(result.pxPerMm >= 0.0001f)
    }

    @Test
    fun `dbg returns readable debug string`() {
        val spec = ShaftSpec(overallLengthMm = 1000f)
        val result = ShaftLayout.compute(
            spec = spec,
            leftPx = 0f,
            topPx = 0f,
            rightPx = 1000f,
            bottomPx = 600f,
            marginPx = 12f
        )

        val debug = result.dbg()
        assertTrue(debug.contains("pxPerMm"))
        assertTrue(debug.contains("content="))
        assertTrue(debug.contains("spanMm="))
    }
}
