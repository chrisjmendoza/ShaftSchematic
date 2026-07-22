package com.android.shaftschematic.geom

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.sqrt

/**
 * Unit tests for the pure spoon-bowl math (`geom/KeywaySpoonMath.kt`): radius scaling, the bowl's
 * far edge landing on the keyway LET extent, the slot walls meeting the circle, and the major-arc
 * sweep. Mirror-image behaviour is checked for both slot directions.
 */
class KeywaySpoonMathTest {

    @Test
    fun `radius is the width ratio times half-width`() {
        val b = keywaySpoonBowl(letX = 100f, dir = 1f, halfW = 10f, widthRatio = 2.4f)
        assertEquals(24f, b.radius, 1e-4f)
    }

    @Test
    fun `shift ratio slides the bowl centre between the LET tip and one radius back`() {
        val r = 10f * 2.4f
        // shift 1 → centred on the LET tip; shift 0 → far edge tangent (one radius back toward SET).
        assertEquals(100f, keywaySpoonBowl(letX = 100f, dir = 1f, halfW = 10f, shiftRatio = 1f).cx, 1e-3f)
        assertEquals(100f - r, keywaySpoonBowl(letX = 100f, dir = 1f, halfW = 10f, shiftRatio = 0f).cx, 1e-3f)
        // default 0.5 → halfway between (half a radius back).
        assertEquals(100f - r / 2f, keywaySpoonBowl(letX = 100f, dir = 1f, halfW = 10f).cx, 1e-3f)
        // mirror direction.
        assertEquals(r / 2f, keywaySpoonBowl(letX = 0f, dir = -1f, halfW = 10f).cx, 1e-3f)
    }

    @Test
    fun `wall terminus lies on the circle at the slot half-width`() {
        val halfW = 10f
        val b = keywaySpoonBowl(letX = 100f, dir = 1f, halfW = halfW, widthRatio = 2.4f)
        // (wallEndX - cx)^2 + halfW^2 == radius^2
        val dxx = b.wallEndX - b.cx
        assertEquals(b.radius, sqrt(dxx * dxx + halfW * halfW), 1e-3f)
    }

    @Test
    fun `wall terminus is inward of centre toward SET for both directions`() {
        val plus = keywaySpoonBowl(letX = 100f, dir = 1f, halfW = 10f)
        // dir +1: SET is to the left, so the walls terminate left of centre.
        assert(plus.wallEndX < plus.cx)
        val minus = keywaySpoonBowl(letX = 0f, dir = -1f, halfW = 10f)
        // dir -1: SET is to the right, so the walls terminate right of centre.
        assert(minus.wallEndX > minus.cx)
    }

    @Test
    fun `sweep is the full turn minus the mouth wedge`() {
        val halfW = 10f
        val b = keywaySpoonBowl(letX = 100f, dir = 1f, halfW = halfW, widthRatio = 2.4f)
        val phi = Math.toDegrees(kotlin.math.asin((halfW / b.radius).toDouble())).toFloat()
        assertEquals(360f - 2f * phi, b.arcSweepDeg, 1e-3f)
        assertEquals(180f + phi, b.arcStartDeg, 1e-3f)
    }
}
