package com.android.shaftschematic.ui.resolved

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `bodyAtShaftEnd` / `effectiveBodyDimensionRail` — the ONE decision behind the body card's
 * "Show dimension rail" and both dimensioned sheets. Unset follows the body's position: on when
 * its STORED span touches the AFT face or the FWD face (the typed OAL, or the renderer's 0-OAL
 * coverage end), off mid-shaft; an authored value wins either way.
 */
class BodyDimensionRailTest {

    private val oal = 3000f

    private fun body(start: Float, length: Float, rail: Boolean? = null) =
        Body(id = "b", startFromAftMm = start, lengthMm = length, diaMm = 120f, showDimensionRail = rail)

    private fun specWith(b: Body, oalMm: Float = oal) = ShaftSpec(overallLengthMm = oalMm, bodies = listOf(b))

    @Test
    fun `a body on the AFT face is at a shaft end`() {
        val b = body(0f, 400f)
        assertTrue(bodyAtShaftEnd(b, specWith(b)))
        assertTrue(effectiveBodyDimensionRail(b, specWith(b)))
    }

    @Test
    fun `a body on the FWD face is at a shaft end`() {
        val b = body(2600f, 400f)
        assertTrue(bodyAtShaftEnd(b, specWith(b)))
        assertTrue(effectiveBodyDimensionRail(b, specWith(b)))
    }

    @Test
    fun `a mid-shaft body is not`() {
        val b = body(1000f, 400f)
        assertFalse(bodyAtShaftEnd(b, specWith(b)))
        assertFalse(effectiveBodyDimensionRail(b, specWith(b)))
    }

    @Test
    fun `float noise within the bounds slop still counts as the end`() {
        // The on-device job: 5695.95 + 374.6499 lands 0.0001 short of the typed 6070.6.
        val fwd = body(5695.95f, 374.6499f)
        assertTrue(bodyAtShaftEnd(fwd, specWith(fwd, oalMm = 6070.6f)))
        val aft = body(0.005f, 400f)
        assertTrue(bodyAtShaftEnd(aft, specWith(aft)))
    }

    @Test
    fun `a gap past the slop is mid-shaft`() {
        val b = body(2599f, 400f) // ends 1 mm short of the FWD face
        assertFalse(bodyAtShaftEnd(b, specWith(b)))
        val aft = body(0.05f, 400f)
        assertFalse(bodyAtShaftEnd(aft, specWith(aft)))
    }

    @Test
    fun `with no OAL typed the FWD face is the coverage end the renderer draws to`() {
        val b = body(1000f, 500f)
        val spec = ShaftSpec(
            overallLengthMm = 0f,
            bodies = listOf(b),
            tapers = listOf(Taper(id = "t", startFromAftMm = 0f, lengthMm = 1000f, startDiaMm = 100f, endDiaMm = 120f)),
        )
        assertTrue("the body ends where the drawing ends", bodyAtShaftEnd(b, spec))

        val mid = body(200f, 300f)
        val spec2 = spec.copy(bodies = listOf(mid, b))
        assertFalse(bodyAtShaftEnd(mid, spec2))
    }

    @Test
    fun `an authored value wins over the position default`() {
        val mid = body(1000f, 400f, rail = true)
        assertTrue(effectiveBodyDimensionRail(mid, specWith(mid)))
        val end = body(0f, 400f, rail = false)
        assertFalse(effectiveBodyDimensionRail(end, specWith(end)))
    }

    @Test
    fun `a zero-length body is never at an end`() {
        val b = body(0f, 0f)
        assertFalse(bodyAtShaftEnd(b, specWith(b)))
    }
}
