package com.android.shaftschematic.geom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.atan

/**
 * The thread hatch leans by the thread's OWN crest geometry — half a pitch across the major
 * diameter — times the user's slant factor, standing steep like a thread seen from the side and
 * never the 45° section-cut glyph it replaced (on-device request). It spaces by the same
 * geometry — one pitch at the drawing's own diametral scale — thinned by the user's density.
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
    fun `true spacing is one pitch at the drawn height's diametral scale`() {
        // A 100 mm thread drawn 200 units tall is at 2 units/mm across, so a 6 mm pitch is 12.
        assertEquals(12f, threadHatchTrueSpacing(bandH = 200f, pitchMm = 6f, majorDiaMm = 100f), 1e-5f)
    }

    @Test
    fun `full density spaces at true pitch, half density every other crest`() {
        // 6 mm pitch on a 100 mm thread drawn 100 units tall: crests 6 units apart.
        assertEquals(6f, threadHatchSpacing(100f, 6f, 100f, density = 1f), 1e-5f)
        assertEquals(12f, threadHatchSpacing(100f, 6f, 100f, density = 0.5f), 1e-5f)
    }

    @Test
    fun `4 TPI on 4,5 inch at the default sheet height spaces about 4,5 pt at the shipped density`() {
        // Default sizing curve: 8 in draws 1 in tall, so 4.5 in draws 0.5625 in = 40.5 pt.
        val bandH = 4.5f / 8f * 72f
        assertEquals(2.25f, threadHatchTrueSpacing(bandH, tpi4PitchMm, dia4p5Mm), 1e-4f)
        assertEquals(4.5f, threadHatchSpacing(bandH, tpi4PitchMm, dia4p5Mm, THREAD_DENSITY_DEFAULT), 1e-4f)
    }

    @Test
    fun `a fine thread sits on the legibility floor whatever the density`() {
        var density = THREAD_DENSITY_MIN
        while (density <= THREAD_DENSITY_MAX + 1e-4f) {
            // 1 mm pitch on a 100 mm thread drawn 40 units tall: 0.4 units at true density.
            assertEquals(THREAD_HATCH_SPACING_MIN, threadHatchSpacing(40f, 1f, 100f, density), 0f)
            density += THREAD_DENSITY_STEP
        }
        assertEquals(3f, THREAD_HATCH_SPACING_MIN, 0f)
    }

    @Test
    fun `a sparse coarse thread is capped`() {
        assertEquals(THREAD_HATCH_SPACING_MAX, threadHatchSpacing(200f, 6f, 100f, THREAD_DENSITY_MIN), 0f)
    }

    @Test
    fun `true spacing scales with the drawn height, as the diametral scale does`() {
        // The scale comes from the drawn height, not from any site's pt-per-mm, so the same
        // thread drawn twice as tall spaces twice as wide — and the same thread at the same
        // height spaces the same on every site (pixel-pinned in ThreadHatchParityTest).
        val bandH = 60f
        assertEquals(1f, threadHatchTrueSpacing(bandH, 6f, 60f) / (bandH * 6f / 60f), 1e-6f)
        assertEquals(2f * threadHatchTrueSpacing(bandH, 6f, 60f), threadHatchTrueSpacing(2f * bandH, 6f, 60f), 1e-5f)
    }

    @Test
    fun `a thread with no pitch spaces at the fallback pitch`() {
        assertEquals(
            threadHatchSpacing(80f, THREAD_HATCH_FALLBACK_PITCH_MM, 30f, 0.5f),
            threadHatchSpacing(80f, 0f, 30f, 0.5f),
            1e-6f,
        )
    }

    @Test
    fun `a thread with no diameter spaces like the reference thread`() {
        assertEquals(threadHatchReferenceSpacing(120f, 0.4f), threadHatchSpacing(120f, 6f, 0f, 0.4f), 1e-6f)
        assertEquals(
            threadHatchSpacing(120f, tpi4PitchMm, dia4p5Mm, 0.4f),
            threadHatchReferenceSpacing(120f, 0.4f),
            1e-6f,
        )
    }

    @Test
    fun `an out-of-range density is sanitized before it spaces anything`() {
        assertEquals(THREAD_DENSITY_DEFAULT, sanitizeThreadDensity(Float.NaN), 0f)
        assertEquals(THREAD_DENSITY_DEFAULT, sanitizeThreadDensity(Float.POSITIVE_INFINITY), 0f)
        assertEquals(THREAD_DENSITY_MIN, sanitizeThreadDensity(0f), 0f)
        assertEquals(THREAD_DENSITY_MAX, sanitizeThreadDensity(3f), 0f)
        assertEquals(0.35f, sanitizeThreadDensity(0.35f), 0f)
        // A zero density would divide by zero; it spaces at the minimum density instead.
        assertEquals(
            threadHatchSpacing(200f, 6f, 100f, THREAD_DENSITY_MIN),
            threadHatchSpacing(200f, 6f, 100f, 0f),
            0f,
        )
    }
}
