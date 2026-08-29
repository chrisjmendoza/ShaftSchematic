package com.android.shaftschematic.geom

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.sqrt

/**
 * Unit tests for the pure spoon-bowl math (`geom/KeywaySpoonMath.kt`): radius scaling, the bowl's
 * far edge landing on the keyway LET extent, the y-semi's uniform drawn clearance, the slot walls
 * meeting the drawn ellipse, and the major-arc sweep. Mirror-image behaviour is checked for both
 * slot directions.
 */
class KeywaySpoonMathTest {

    @Test
    fun `radius is the width ratio times half-width`() {
        val b = keywaySpoonBowl(letX = 100f, dir = 1f, halfW = 10f, halfH = 10f, widthRatio = 2.4f)
        assertEquals(24f, b.radius, 1e-4f)
    }

    @Test
    fun `shift ratio slides the bowl centre between the LET tip and one radius back`() {
        val r = 10f * 2.4f
        // shift 1 → centred on the LET tip; shift 0 → far edge tangent (one radius back toward SET).
        assertEquals(100f, keywaySpoonBowl(letX = 100f, dir = 1f, halfW = 10f, halfH = 10f, shiftRatio = 1f).cx, 1e-3f)
        assertEquals(100f - r, keywaySpoonBowl(letX = 100f, dir = 1f, halfW = 10f, halfH = 10f, shiftRatio = 0f).cx, 1e-3f)
        // default 0.5 → halfway between (half a radius back).
        assertEquals(100f - r / 2f, keywaySpoonBowl(letX = 100f, dir = 1f, halfW = 10f, halfH = 10f).cx, 1e-3f)
        // mirror direction.
        assertEquals(r / 2f, keywaySpoonBowl(letX = 0f, dir = -1f, halfW = 10f, halfH = 10f).cx, 1e-3f)
    }

    @Test
    fun `y-semi is the slot half-height plus the poke-past distance`() {
        // Uniform drawn clearance: the walls' daylight (ry − halfH) equals the bowl's axial poke
        // past the LET tip (radius × shiftRatio), whatever the sheet's two scales did to halfH.
        val iso = keywaySpoonBowl(letX = 100f, dir = 1f, halfW = 10f, halfH = 10f)
        assertEquals(10f + 24f * 0.5f, iso.ry, 1e-3f)

        // A compressed sheet: the transverse term three times the axial one. The vertical
        // clearance stays the axial poke-past — the tall-bowl case this construction removes.
        val squeezed = keywaySpoonBowl(letX = 100f, dir = 1f, halfW = 10f, halfH = 30f)
        assertEquals(30f + 24f * 0.5f, squeezed.ry, 1e-3f)
        assertEquals(iso.ry - 10f, squeezed.ry - 30f, 1e-3f)   // same daylight above the wall
    }

    @Test
    fun `wall terminus lies on the drawn ellipse at the slot half-height`() {
        val halfW = 10f
        val halfH = 30f
        val b = keywaySpoonBowl(letX = 100f, dir = 1f, halfW = halfW, halfH = halfH, widthRatio = 2.4f)
        // ((wallEndX - cx)/radius)^2 + (halfH/ry)^2 == 1
        val nx = (b.wallEndX - b.cx) / b.radius
        val ny = halfH / b.ry
        assertEquals(1f, nx * nx + ny * ny, 1e-3f)
    }

    @Test
    fun `wall terminus is inward of centre toward SET for both directions`() {
        val plus = keywaySpoonBowl(letX = 100f, dir = 1f, halfW = 10f, halfH = 10f)
        // dir +1: SET is to the left, so the walls terminate left of centre.
        assert(plus.wallEndX < plus.cx)
        val minus = keywaySpoonBowl(letX = 0f, dir = -1f, halfW = 10f, halfH = 10f)
        // dir -1: SET is to the right, so the walls terminate right of centre.
        assert(minus.wallEndX > minus.cx)
    }

    @Test
    fun `sweep is the full turn minus the mouth wedge`() {
        val halfW = 10f
        val halfH = 25f
        val b = keywaySpoonBowl(letX = 100f, dir = 1f, halfW = halfW, halfH = halfH, widthRatio = 2.4f)
        val phi = Math.toDegrees(kotlin.math.asin((halfH / b.ry).toDouble())).toFloat()
        assertEquals(360f - 2f * phi, b.arcSweepDeg, 1e-3f)
        assertEquals(180f + phi, b.arcStartDeg, 1e-3f)
    }
}
