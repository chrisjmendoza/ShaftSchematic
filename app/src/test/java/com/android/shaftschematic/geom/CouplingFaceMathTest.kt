package com.android.shaftschematic.geom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.asin

/**
 * Pins the coupling end view's proportions — the numbers `RunoutPdfComposer.drawCouplingFace`
 * draws from. The face is PDF-only, so this is the only place its geometry can be checked
 * without rasterizing a page.
 */
class CouplingFaceMathTest {

    private val outerR = 36f

    /** Half-angle of the keyseat's arc gap, measured on the pilot rim. */
    private fun keywayHalfAngleDeg(l: CouplingFaceLayout): Float =
        Math.toDegrees(asin((l.keywaySlotHalf / l.pilotR).toDouble())).toFloat()

    /** Smallest angular distance from [deg] to 12 o'clock (-90°), in degrees. */
    private fun degreesFromTop(deg: Float): Float {
        val d = ((deg + 90f) % 360f + 360f) % 360f
        return if (d > 180f) 360f - d else d
    }

    @Test
    fun `the bore sits inside the OD and every ratio tracks its constant`() {
        val l = couplingFaceLayout(outerR, boltCount = 6)

        assertTrue("pilot bore must be inside the coupling OD", l.pilotR < l.outerR)
        assertEquals(outerR * COUPLING_PILOT_FRAC, l.pilotR, 1e-4f)
        assertEquals(l.pilotR * COUPLING_KEYWAY_SLOT_HALF_FRAC, l.keywaySlotHalf, 1e-4f)
        assertEquals(l.pilotR * COUPLING_KEYWAY_DEPTH_FRAC, l.keywayDepth, 1e-4f)
        assertEquals(outerR * COUPLING_BOLT_HOLE_FRAC, l.boltHoleR, 1e-4f)
        assertEquals((l.outerR + l.pilotR) / 2f, l.boltCircleR, 1e-4f)
        assertTrue("bolt circle sits between bore and OD", l.boltCircleR in l.pilotR..l.outerR)
    }

    @Test
    fun `the keyseat stands outward and still clears the bolt holes`() {
        // The coupling's keyseat is cut into the HUB — it protrudes past the bore rather than
        // biting into it (the opposite of the runout bubble's shaft-keyway slot). Its cap must
        // stop short of the bolt holes or the two features would collide on the sheet.
        for (count in 1..12) {
            val l = couplingFaceLayout(outerR, count)
            assertTrue(
                "keyseat cap must clear the bolt holes at count=$count",
                l.pilotR + l.keywayDepth < l.boltCircleR - l.boltHoleR,
            )
        }
    }

    @Test
    fun `bolt holes are evenly pitched and clear of the keyseat`() {
        for (count in 1..12) {
            val l = couplingFaceLayout(outerR, count)
            assertEquals("hole count", count, l.boltAngleDegs.size)

            val pitch = 360f / count
            l.boltAngleDegs.zipWithNext { a, b ->
                assertEquals("even pitch at count=$count", pitch, b - a, 1e-3f)
            }

            // Half-pitch offset: the nearest hole is exactly half a pitch off 12 o'clock, so
            // none of them hides behind the keyseat.
            val notchHalf = keywayHalfAngleDeg(l)
            val nearest = l.boltAngleDegs.minOf { degreesFromTop(it) }
            assertEquals("half-pitch offset at count=$count", pitch / 2f, nearest, 1e-3f)
            assertTrue(
                "no bolt hole inside the keyseat span at count=$count",
                l.boltAngleDegs.all { degreesFromTop(it) > notchHalf },
            )
        }
    }

    @Test
    fun `a coupling with no bolt data draws the plain two-circle face`() {
        for (count in intArrayOf(0, -3)) {
            val l = couplingFaceLayout(outerR, count)
            assertTrue("no bolt angles at count=$count", l.boltAngleDegs.isEmpty())
            // The circles themselves are unaffected — the hand-sketch minimum still draws.
            assertEquals(outerR, l.outerR, 1e-4f)
            assertEquals(outerR * COUPLING_PILOT_FRAC, l.pilotR, 1e-4f)
        }
    }

    @Test
    fun `layout scales linearly with the drawn radius`() {
        val small = couplingFaceLayout(18f, 4)
        val big = couplingFaceLayout(36f, 4)
        assertEquals(small.pilotR * 2f, big.pilotR, 1e-4f)
        assertEquals(small.keywayDepth * 2f, big.keywayDepth, 1e-4f)
        assertEquals(small.boltCircleR * 2f, big.boltCircleR, 1e-4f)
        small.boltAngleDegs.zip(big.boltAngleDegs) { a, b ->
            assertTrue("angles are size-independent", abs(a - b) < 1e-4f)
        }
    }
}
