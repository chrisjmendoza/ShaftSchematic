package com.android.shaftschematic.geom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.atan

/**
 * The thread hatch leans by the thread's OWN crest geometry — half a pitch across the major
 * diameter — times the user's slant factor, standing steep like a thread seen from the side and
 * never the 45° section-cut glyph it replaced (on-device request).
 */
class ThreadHatchMathTest {

    private val tpi4PitchMm = 25.4f / 4f
    private val dia4p5Mm = 4.5f * 25.4f

    /** Stroke angle to the shaft axis for a run/rise [lean]. */
    private fun angleToAxisDeg(lean: Float): Double = 90.0 - Math.toDegrees(atan(lean.toDouble()))

    @Test
    fun `true lean is half a pitch over the major diameter`() {
        assertEquals(3f / 100f, threadHatchLean(pitchMm = 6f, majorDiaMm = 100f, slant = 1f), 1e-7f)
    }

    @Test
    fun `4 TPI on a 4,5 inch diameter stands at about 85 degrees at the shipped slant`() {
        val lean = threadHatchLean(tpi4PitchMm, dia4p5Mm, THREAD_SLANT_DEFAULT)
        assertEquals(85.24, angleToAxisDeg(lean), 0.01)
    }

    @Test
    fun `lean grows with the slant factor`() {
        var prev = 0f
        var slant = THREAD_SLANT_MIN
        while (slant <= THREAD_SLANT_MAX) {
            val lean = threadHatchLean(tpi4PitchMm, dia4p5Mm, slant)
            assertTrue("lean at $slant× must exceed the previous step", lean > prev)
            prev = lean
            slant += THREAD_SLANT_STEP
        }
    }

    @Test
    fun `lean is the slant factor times the true lean, whatever the diameter`() {
        listOf(30f, 114.3f, 400f).forEach { dia ->
            val trueLean = threadHatchLean(tpi4PitchMm, dia, 1f)
            assertEquals(4f * trueLean, threadHatchLean(tpi4PitchMm, dia, 4f), 1e-6f)
        }
    }

    @Test
    fun `a small coarse thread never leans flatter than 60 degrees`() {
        val lean = threadHatchLean(pitchMm = 6f, majorDiaMm = 10f, slant = THREAD_SLANT_MAX)
        assertEquals(THREAD_HATCH_MAX_LEAN, lean, 1e-7f)
        assertEquals(60.0, angleToAxisDeg(lean), 0.01)
    }

    @Test
    fun `a thread with no pitch leans at the fallback pitch`() {
        assertEquals(
            threadHatchLean(THREAD_HATCH_FALLBACK_PITCH_MM, 80f, 3f),
            threadHatchLean(0f, 80f, 3f),
            1e-7f,
        )
    }

    @Test
    fun `a thread with no diameter takes the reference thread's lean`() {
        assertEquals(threadHatchReferenceLean(5f), threadHatchLean(6f, 0f, 5f), 1e-7f)
        assertEquals(
            threadHatchLean(tpi4PitchMm, dia4p5Mm, 5f),
            threadHatchReferenceLean(5f),
            1e-7f,
        )
    }

    @Test
    fun `an out-of-range slant is clamped before it leans anything`() {
        assertEquals(
            threadHatchLean(tpi4PitchMm, dia4p5Mm, THREAD_SLANT_MIN),
            threadHatchLean(tpi4PitchMm, dia4p5Mm, 0f),
            1e-7f,
        )
        assertEquals(THREAD_SLANT_DEFAULT, sanitizeThreadSlant(Float.NaN), 0f)
        assertEquals(THREAD_SLANT_MAX, sanitizeThreadSlant(99f), 0f)
    }

    @Test
    fun `run is the band height times the lean`() {
        assertEquals(12.5f, threadHatchRun(100f, 0.125f), 1e-6f)
        assertEquals(0f, threadHatchRun(0f, 0.3f), 0f)
    }

    @Test
    fun `spacing follows the pitch inside its bounds`() {
        assertEquals(6f, threadHatchSpacing(6f, 1f), 1e-6f)
        assertEquals(THREAD_HATCH_FALLBACK_PITCH_MM * 2f, threadHatchSpacing(0f, 2f), 1e-6f)
        assertEquals(THREAD_HATCH_SPACING_MIN, threadHatchSpacing(null, 0.5f), 1e-6f)
        assertEquals(THREAD_HATCH_SPACING_MAX, threadHatchSpacing(6f, 10f), 1e-6f)
    }
}
