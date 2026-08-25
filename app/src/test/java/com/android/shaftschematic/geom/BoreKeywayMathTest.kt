package com.android.shaftschematic.geom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BoreKeywayMath — the rough-cutter depth solve and its scale-check companion.
 *
 * Vectors are the ones from the on-device spec (inches), verified by hand against the
 * expanded PlaneY form before the simplified formula was adopted.
 */
class BoreKeywayMathTest {

    private fun depth(d: Double, wf: Double, df: Double, wc: Double): Double =
        roughCutterTargetDepth(d, wf, df, wc).depth!!

    // ── Spec test vectors ─────────────────────────────────────────────────────

    @Test
    fun `vector 1 - 7 bore, 1_5 keyway at 19-32, 1_0 cutter`() {
        assertEquals(0.5483, depth(7.0, 1.5, 0.59375, 1.0), 5e-4)
    }

    @Test
    fun `vector 2 - 8 bore flattens the correction`() {
        assertEquals(0.5282, depth(8.0, 1.75, 0.59375, 1.0), 5e-4)
    }

    @Test
    fun `vector 3 - shallower finished depth`() {
        assertEquals(0.5171, depth(7.0, 1.5, 0.5625, 1.0), 5e-4)
    }

    @Test
    fun `vector 4 - equal widths return the finished depth`() {
        // Identical widths give identical sqrt terms, but `d + x - x` still re-rounds at
        // the intermediate sum — equal to FP tolerance, not bit-exact.
        assertEquals(0.59375, depth(7.0, 1.5, 0.59375, 1.5), 1e-9)
    }

    // ── Invariants ────────────────────────────────────────────────────────────

    @Test
    fun `a narrower cutter always measures less`() {
        // Strict, not "normally": sqrt(R^2 - x^2) is strictly decreasing in x.
        for (wc in listOf(0.25, 0.5, 0.75, 1.0, 1.25, 1.49)) {
            assertTrue(depth(7.0, 1.5, 0.59375, wc) < 0.59375)
        }
    }

    @Test
    fun `narrower cutter depth approaches the finished depth as widths converge`() {
        val far = depth(7.0, 1.5, 0.59375, 1.0)
        val near = depth(7.0, 1.5, 0.59375, 1.499)
        assertTrue(near > far)
        assertEquals(0.59375, near, 1e-3)
    }

    @Test
    fun `smaller bore curves more, so the correction grows`() {
        val small = 0.59375 - depth(4.0, 1.5, 0.59375, 1.0)
        val large = 0.59375 - depth(12.0, 1.5, 0.59375, 1.0)
        assertTrue(small > large)
        assertTrue(large > 0.0)
    }

    @Test
    fun `a cutter wider than the finished keyway is rejected, not solved`() {
        // The on-device case: a 2" cutter left over from a 2 1/2" keyway job, then the
        // keyway switched to 1 1/2". The geometry answers happily; the cut is wrong.
        val r = roughCutterTargetDepth(7.0, 1.5, 0.4375, 2.0)
        assertNull(r.depth)
        assertEquals(BoreKeywayIssue.CUTTER_WIDER_THAN_KEYWAY, r.issue)
    }

    @Test
    fun `a cutter equal to the keyway is not rejected as wider`() {
        // Guards the epsilon: 1.5 typed as "1 1/2" and as "1.5" must both be accepted.
        assertEquals(0.4375, depth(7.0, 1.5, 0.4375, 1.5), 1e-9)
    }

    @Test
    fun `unit-independent - same numbers in mm scale with the inputs`() {
        val inches = depth(7.0, 1.5, 0.59375, 1.0)
        val mm = depth(7.0 * 25.4, 1.5 * 25.4, 0.59375 * 25.4, 1.0 * 25.4)
        assertEquals(inches * 25.4, mm, 1e-9)
    }

    // ── Validation ────────────────────────────────────────────────────────────

    @Test
    fun `non-positive inputs are rejected`() {
        for (r in listOf(
            roughCutterTargetDepth(0.0, 1.5, 0.5, 1.0),
            roughCutterTargetDepth(7.0, 0.0, 0.5, 1.0),
            roughCutterTargetDepth(7.0, 1.5, 0.0, 1.0),
            roughCutterTargetDepth(7.0, 1.5, 0.5, -1.0),
        )) {
            assertNull(r.depth)
            assertEquals(BoreKeywayIssue.NON_POSITIVE_INPUT, r.issue)
        }
    }

    @Test
    fun `keyway half-width at or past the bore radius is rejected`() {
        val r = roughCutterTargetDepth(7.0, 7.0, 0.5, 1.0)
        assertNull(r.depth)
        assertEquals(BoreKeywayIssue.FINAL_WIDTH_EXCEEDS_BORE, r.issue)
    }

    @Test
    fun `a cutter past the bore is caught by the keyway bound`() {
        // No separate cutter-vs-bore check is needed: a cutter can be no wider than the
        // keyway, which is itself bounded inside the bore, so this reports the machining
        // reason rather than a geometric one.
        val r = roughCutterTargetDepth(7.0, 1.5, 0.5, 7.0)
        assertNull(r.depth)
        assertEquals(BoreKeywayIssue.CUTTER_WIDER_THAN_KEYWAY, r.issue)
    }

    @Test
    fun `base validation stands alone for field-level checking`() {
        assertNull(validateBoreKeyway(7.0, 1.5, 0.59375))
        assertEquals(BoreKeywayIssue.NON_POSITIVE_INPUT, validateBoreKeyway(0.0, 1.5, 0.5))
        assertEquals(BoreKeywayIssue.NON_POSITIVE_INPUT, validateBoreKeyway(7.0, 1.5, 0.0))
        assertEquals(BoreKeywayIssue.FINAL_WIDTH_EXCEEDS_BORE, validateBoreKeyway(7.0, 7.0, 0.5))
    }

    @Test
    fun `a cutter that never breaks the surface is flagged, never negative`() {
        // Wide shallow keyway in a small bore: the finished plane sits above the bore
        // surface at a very narrow cutter's edges (Y_final ~ 0.688, depth 0.05 -> plane at
        // +0.638; Y_current ~ 0.005).
        val r = roughCutterTargetDepth(2.0, 1.9, 0.05, 0.2)
        assertNull(r.depth)
        assertEquals(BoreKeywayIssue.CUTTER_NEVER_BREAKS_SURFACE, r.issue)
    }

    // ── Nearest 64th (scale-check label) ─────────────────────────────────────

    @Test
    fun `nearest 64th matches the spec example`() {
        assertEquals("35/64", nearestSixtyFourthLabel(0.5483))
    }

    @Test
    fun `nearest 64th reduces`() {
        assertEquals("1/2", nearestSixtyFourthLabel(0.5))
        assertEquals("1/4", nearestSixtyFourthLabel(0.2501))
        assertEquals("3/8", nearestSixtyFourthLabel(0.3749))
    }

    @Test
    fun `nearest 64th handles mixed numbers and wholes`() {
        assertEquals("1 13/64", nearestSixtyFourthLabel(1.2031))
        assertEquals("2", nearestSixtyFourthLabel(1.999))
    }

    @Test
    fun `nearest 64th is null at and below zero`() {
        assertNull(nearestSixtyFourthLabel(0.0))
        assertNull(nearestSixtyFourthLabel(-0.5))
        assertNull(nearestSixtyFourthLabel(0.004))  // rounds to 0/64
    }

    // ── Nearest fraction, generalized (Scale chip: 64 | 32 | 16) ────────────────

    @Test
    fun `denominator 64 delegate matches nearestFractionLabel exactly`() {
        // Not just equal values — the SAME function, so any future 64th behavior change
        // cannot silently diverge between the two entry points.
        for (v in listOf(0.5483, 0.5, 0.2501, 0.3749, 1.2031, 1.999, 0.0, -0.5, 0.004)) {
            assertEquals(nearestSixtyFourthLabel(v), nearestFractionLabel(v, 64))
        }
    }

    @Test
    fun `nearest fraction at 32nds reduces`() {
        // 0.5483 * 32 = 17.5456 -> round 18 -> 18/32 -> gcd(18, 32) = 2 -> 9/16.
        assertEquals("9/16", nearestFractionLabel(0.5483, 32))
        // 0.5 * 32 = 16.0 -> 16/32 -> gcd 16 -> 1/2.
        assertEquals("1/2", nearestFractionLabel(0.5, 32))
        // 0.65625 * 32 = 21.0 exactly -> 21/32, gcd(21,32) = 1, already reduced.
        assertEquals("21/32", nearestFractionLabel(0.65625, 32))
    }

    @Test
    fun `nearest fraction at 16ths reduces`() {
        // 0.5483 * 16 = 8.7728 -> round 9 -> 9/16, gcd(9,16) = 1, already reduced.
        assertEquals("9/16", nearestFractionLabel(0.5483, 16))
        // 0.5 * 16 = 8.0 -> 8/16 -> gcd 8 -> 1/2.
        assertEquals("1/2", nearestFractionLabel(0.5, 16))
        // 0.3125 * 16 = 5.0 exactly -> 5/16, gcd(5,16) = 1, already reduced.
        assertEquals("5/16", nearestFractionLabel(0.3125, 16))
    }

    @Test
    fun `nearest fraction handles mixed numbers and wholes at every grid`() {
        // 1.03125 * 32 = 33.0 exactly -> whole 1, num 1 -> "1 1/32".
        assertEquals("1 1/32", nearestFractionLabel(1.03125, 32))
        // 1.1875 * 16 = 19.0 exactly -> whole 1, num 3 -> "1 3/16".
        assertEquals("1 3/16", nearestFractionLabel(1.1875, 16))
        // 2.0 * 16 = 32.0 -> whole 2, num 0 -> "2".
        assertEquals("2", nearestFractionLabel(2.0, 16))
        // 2.0 * 32 = 64.0 -> whole 2, num 0 -> "2".
        assertEquals("2", nearestFractionLabel(2.0, 32))
    }

    @Test
    fun `nearest fraction is null at and below zero at every grid`() {
        for (den in listOf(64, 32, 16)) {
            assertNull(nearestFractionLabel(0.0, den))
            assertNull(nearestFractionLabel(-0.5, den))
        }
        assertNull(nearestFractionLabel(0.008, 16))  // 0.008 * 16 = 0.128 -> rounds to 0/16
    }

    @Test
    fun `bore surface rise matches the spec worked example`() {
        assertEquals(0.0813, boreSurfaceRise(3.5, 0.75), 5e-4)
        assertEquals(0.0359, boreSurfaceRise(3.5, 0.5), 5e-4)
    }
}
