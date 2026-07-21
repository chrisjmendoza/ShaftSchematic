package com.android.shaftschematic.geom

import com.android.shaftschematic.model.PitSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for the pure wear-pit math (`geom/WearPitMath.kt`): symbol sizing, across-fraction
 * clamping, component-local axial conversion, centre-Y mapping, and pit hit-testing.
 */
class WearPitMathTest {

    @Test
    fun `large pit arm is the shared ratio times the small arm`() {
        assertEquals(10f, pitHalfArm(PitSize.SMALL, 10f), 0f)
        assertEquals(10f * PIT_LARGE_TO_SMALL_RATIO, pitHalfArm(PitSize.LARGE, 10f), 1e-4f)
    }

    @Test
    fun `across fraction clamps into the interior band`() {
        assertEquals(PIT_ACROSS_MIN, clampPitAcrossFrac(-0.5f), 1e-6f)
        assertEquals(PIT_ACROSS_MAX, clampPitAcrossFrac(1.5f), 1e-6f)
        assertEquals(0.5f, clampPitAcrossFrac(0.5f), 1e-6f)
    }

    @Test
    fun `axial local mm subtracts the component start and never goes negative`() {
        assertEquals(20f, pitAxialLocalMm(physicalMm = 120f, componentStartMm = 100f), 1e-4f)
        assertEquals(0f, pitAxialLocalMm(physicalMm = 90f, componentStartMm = 100f), 1e-4f)
    }

    @Test
    fun `centre Y interpolates between the segment edges using the clamped fraction`() {
        // frac 0.5 → midpoint
        assertEquals(50f, pitCenterY(topY = 0f, botY = 100f, acrossFrac = 0.5f), 1e-4f)
        // frac 0 clamps up to PIT_ACROSS_MIN
        assertEquals(100f * PIT_ACROSS_MIN, pitCenterY(0f, 100f, 0f), 1e-4f)
    }

    @Test
    fun `acrossFracFromTapY inverts a tap into a fraction`() {
        assertEquals(0.25f, acrossFracFromTapY(tapY = 25f, topY = 0f, botY = 100f), 1e-4f)
        // degenerate zero-height segment → centred default
        assertEquals(0.5f, acrossFracFromTapY(tapY = 10f, topY = 50f, botY = 50f), 1e-4f)
    }

    @Test
    fun `pickPitAt returns the nearest pit within reach, null otherwise`() {
        val targets = listOf(
            PitHitTarget("a", cx = 10f, cy = 10f, halfArm = 4f),
            PitHitTarget("b", cx = 40f, cy = 10f, halfArm = 4f),
        )
        // Right on "a"
        assertEquals("a", pickPitAt(11f, 11f, targets, padPx = 2f))
        // Between them but nearer "b", inside b's reach (4 + pad 6 = 10)
        assertEquals("b", pickPitAt(45f, 10f, targets, padPx = 6f))
        // Far from both
        assertNull(pickPitAt(25f, 80f, targets, padPx = 2f))
    }
}
