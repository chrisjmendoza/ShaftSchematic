package com.android.shaftschematic.pdf

import com.android.shaftschematic.geom.MIN_BLEND_WIDTH_PT
import com.android.shaftschematic.geom.shoulderDrawSpec
import com.android.shaftschematic.model.Liner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ONE shoulder spec construction both PDF composers draw through
 * (`pdf/LinerShoulderDraw.kt`). What is pinned here is the recipe itself — which mm endpoints
 * map through the sheet's `xAt`, and the radius→diameter doubling into `rPx` — because a
 * silent divergence between the schematic and the consolidated sheet is exactly what having
 * one implementation is meant to prevent.
 */
class LinerShoulderDrawTest {

    private val eps = 1e-3f

    /** A uniform sheet: 2 pt per mm, origin offset so an x-map slip cannot pass unnoticed. */
    private fun xAtLinear(mm: Float) = 10f + mm * 2f
    private fun rPxLinear(diaMm: Float) = diaMm * 0.5f * 2f

    private val shouldered = Liner(
        id = "ln1",
        startFromAftMm = 100f,
        lengthMm = 300f,
        odMm = 120f,
        endMmPhysical = 400f,
        shoulderAftLenMm = 25f,
        shoulderAftOdMm = 100f,
        shoulderAftRadiusMm = 4f,
        shoulderFwdLenMm = 40f,
        shoulderFwdOdMm = 90f,
        shoulderFwdRadiusMm = 0f,
    )

    @Test
    fun `a liner with no shoulders is square`() {
        val ln = Liner(id = "ln0", startFromAftMm = 100f, lengthMm = 300f, odMm = 120f, endMmPhysical = 400f)
        val specs = linerShoulderSpecs(ln, xAtLinear(100f), xAtLinear(400f), rPxLinear(120f), ::xAtLinear, ::rPxLinear)
        assertTrue(specs.square)
        assertNull(specs.aft)
        assertNull(specs.fwd)
    }

    @Test
    fun `the recipe maps each shoulder's own endpoints and doubles the fillet radius`() {
        val x0 = xAtLinear(100f) // 210
        val x1 = xAtLinear(400f) // 810
        val r = rPxLinear(120f)  // 120
        val specs = linerShoulderSpecs(shouldered, x0, x1, r, ::xAtLinear, ::rPxLinear)

        val aft = specs.aft!!
        assertEquals(50f, aft.lenPx, eps)   // |xAt(125) − x0|
        assertEquals(100f, aft.odRPx, eps)  // rPx(100)
        // The stored 4 mm is a RADIUS and rPx takes a DIAMETER: 4 mm at 2 pt/mm is 8 pt, not 4.
        // Dropping the ×2 is the likeliest silent regression, so it is asserted by value.
        assertEquals(8f, aft.filletRPx, eps)

        val fwd = specs.fwd!!
        assertEquals(80f, fwd.lenPx, eps)   // |x1 − xAt(360)|
        assertEquals(90f, fwd.odRPx, eps)
        assertEquals(0f, fwd.filletRPx, eps)
    }

    @Test
    fun `the shared helper reproduces the schematic recipe argument for argument`() {
        val x0 = xAtLinear(100f)
        val x1 = xAtLinear(400f)
        val r = rPxLinear(120f)
        val expectedAft = shoulderDrawSpec(
            trueLenPx = kotlin.math.abs(xAtLinear(125f) - x0),
            runWidthPx = kotlin.math.abs(x1 - x0),
            linerRPx = r,
            shoulderRPx = rPxLinear(100f),
            filletRPx = rPxLinear(4f * 2f),
            minWidthPx = MIN_BLEND_WIDTH_PT,
        )
        val expectedFwd = shoulderDrawSpec(
            trueLenPx = kotlin.math.abs(x1 - xAtLinear(360f)),
            runWidthPx = kotlin.math.abs(x1 - x0),
            linerRPx = r,
            shoulderRPx = rPxLinear(90f),
            filletRPx = rPxLinear(0f * 2f),
            minWidthPx = MIN_BLEND_WIDTH_PT,
        )
        val specs = linerShoulderSpecs(shouldered, x0, x1, r, ::xAtLinear, ::rPxLinear)
        assertNotNull(expectedAft)
        assertEquals(expectedAft, specs.aft)
        assertEquals(expectedFwd, specs.fwd)
    }

    @Test
    fun `a shoulder inherits its liner's LOCAL foreshortening, not a share of the run`() {
        // The consolidated sheet's x map is piecewise: 1 pt/mm up to 200 mm, 4 pt/mm past it.
        // The aft shoulder sits entirely in the slow stretch, so it draws 25 pt — a
        // proportional derivation off the run width would have given it three times that.
        fun xAt(mm: Float) = if (mm <= 200f) mm else 200f + (mm - 200f) * 4f
        fun rPx(diaMm: Float) = diaMm * 0.5f * 2f
        val x0 = xAt(100f)  // 100
        val x1 = xAt(400f)  // 1000
        val specs = linerShoulderSpecs(shouldered, x0, x1, rPx(120f), ::xAt, ::rPx)
        assertEquals(25f, specs.aft!!.lenPx, eps)
        assertEquals(160f, specs.fwd!!.lenPx, eps) // |1000 − xAt(360)|
    }

    @Test
    fun `a sub-pixel shoulder on a hard-compressed sheet still takes the visibility floor`() {
        // 24 pt of paper for a 300 mm liner: the aft shoulder's true width is 2 pt. The floor
        // lifts it to 7, still under the run's 40% ceiling, so the step stays visible.
        fun xAt(mm: Float) = mm * 0.08f
        fun rPx(diaMm: Float) = diaMm * 0.5f
        val specs = linerShoulderSpecs(shouldered, xAt(100f), xAt(400f), rPx(120f), ::xAt, ::rPx)
        assertEquals(MIN_BLEND_WIDTH_PT, specs.aft!!.lenPx, eps)
    }

    @Test
    fun `a shoulder OD at the liner OD draws no step`() {
        val flat = shouldered.copy(shoulderAftOdMm = 120f, shoulderFwdLenMm = 0f)
        val specs = linerShoulderSpecs(
            flat, xAtLinear(100f), xAtLinear(400f), rPxLinear(120f), ::xAtLinear, ::rPxLinear,
        )
        assertTrue(specs.square)
    }
}
