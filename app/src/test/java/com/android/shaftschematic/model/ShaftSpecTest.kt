package com.android.shaftschematic.model

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for ShaftSpec data model and validation logic.
 *
 * Tests cover:
 * - Validation rules (non-negative, within bounds)
 * - Coverage calculations
 * - Max diameter computations
 * - Thread pitch normalization
 */
class ShaftSpecTest {

    @Test
    fun `validate returns true for empty spec`() {
        val spec = ShaftSpec()
        assertTrue(spec.validate())
    }

    @Test
    fun `validate returns false for negative overallLengthMm`() {
        val spec = ShaftSpec(overallLengthMm = -10f)
        assertFalse(spec.validate())
    }

    @Test
    fun `validate returns true when components fit within overall length`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            bodies = listOf(
                Body(startFromAftMm = 0f, lengthMm = 500f, diaMm = 100f)
            )
        )
        assertTrue(spec.validate())
    }

    @Test
    fun `validate returns false when body exceeds overall length`() {
        val spec = ShaftSpec(
            overallLengthMm = 100f,
            bodies = listOf(
                Body(startFromAftMm = 0f, lengthMm = 150f, diaMm = 100f)
            )
        )
        assertFalse(spec.validate())
    }

    @Test
    fun `validate returns false when body has negative diameter`() {
        val spec = ShaftSpec(
            overallLengthMm = 100f,
            bodies = listOf(
                Body(startFromAftMm = 0f, lengthMm = 50f, diaMm = -10f)
            )
        )
        assertFalse(spec.validate())
    }

    @Test
    fun `coverageEndMm returns 0 for empty spec`() {
        val spec = ShaftSpec()
        assertEquals(0f, spec.coverageEndMm(), 0.001f)
    }

    @Test
    fun `coverageEndMm returns max of all component ends`() {
        val spec = ShaftSpec(
            bodies = listOf(Body(startFromAftMm = 0f, lengthMm = 100f, diaMm = 50f)),
            tapers = listOf(Taper(startFromAftMm = 100f, lengthMm = 50f, startDiaMm = 50f, endDiaMm = 30f)),
            threads = listOf(Threads(startFromAftMm = 150f, lengthMm = 25f, majorDiaMm = 30f, pitchMm = 2f)),
            liners = listOf(Liner(startFromAftMm = 50f, lengthMm = 200f, odMm = 60f))
        )
        // Liner ends at 250mm (50 + 200)
        assertEquals(250f, spec.coverageEndMm(), 0.001f)
    }

    @Test
    fun `coverageEndMm ignores excluded threads`() {
        val spec = ShaftSpec(
            bodies = listOf(Body(startFromAftMm = 0f, lengthMm = 80f, diaMm = 40f)),
            threads = listOf(
                Threads(startFromAftMm = 120f, lengthMm = 20f, majorDiaMm = 30f, pitchMm = 2f, excludeFromOAL = true)
            )
        )

        assertEquals(80f, spec.coverageEndMm(), 0.001f)
    }

    @Test
    fun `freeToEndMm returns difference when coverage less than overall`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            bodies = listOf(Body(startFromAftMm = 0f, lengthMm = 500f, diaMm = 100f))
        )
        assertEquals(500f, spec.freeToEndMm(), 0.001f)
    }

    @Test
    fun `freeToEndMm returns 0 when coverage equals overall`() {
        val spec = ShaftSpec(
            overallLengthMm = 500f,
            bodies = listOf(Body(startFromAftMm = 0f, lengthMm = 500f, diaMm = 100f))
        )
        assertEquals(0f, spec.freeToEndMm(), 0.001f)
    }

    @Test
    fun `freeToEndMm returns 0 when coverage exceeds overall`() {
        val spec = ShaftSpec(
            overallLengthMm = 100f,
            bodies = listOf(Body(startFromAftMm = 0f, lengthMm = 500f, diaMm = 100f))
        )
        assertEquals(0f, spec.freeToEndMm(), 0.001f)
    }

    @Test
    fun `maxOuterDiaMm returns 0 for empty spec`() {
        val spec = ShaftSpec()
        assertEquals(0f, spec.maxOuterDiaMm(), 0.001f)
    }

    @Test
    fun `maxOuterDiaMm returns max across all component types`() {
        val spec = ShaftSpec(
            bodies = listOf(Body(diaMm = 50f)),
            tapers = listOf(Taper(startDiaMm = 40f, endDiaMm = 70f)), // max is 70
            threads = listOf(Threads(majorDiaMm = 60f)),
            liners = listOf(Liner(odMm = 80f)) // max overall
        )
        assertEquals(80f, spec.maxOuterDiaMm(), 0.001f)
    }

    @Test
    fun `normalized populates tpi from pitchMm`() {
        val thread = Threads(pitchMm = 2.54f, tpi = null)
        val normalized = thread.normalized()
        
        assertEquals(2.54f, normalized.pitchMm, 0.001f)
        assertEquals(10f, normalized.tpi ?: 0f, 0.01f) // 25.4 / 2.54 = 10
    }

    @Test
    fun `normalized populates pitchMm from tpi`() {
        val thread = Threads(pitchMm = 0f, tpi = 10f)
        val normalized = thread.normalized()
        
        assertEquals(2.54f, normalized.pitchMm, 0.001f) // 25.4 / 10 = 2.54
        assertEquals(10f, normalized.tpi ?: 0f, 0.001f)
    }

    @Test
    fun `normalized handles both pitchMm and tpi present`() {
        val thread = Threads(pitchMm = 2.54f, tpi = 10f)
        val normalized = thread.normalized()
        
        // Both should be retained when both valid
        assertEquals(2.54f, normalized.pitchMm, 0.001f)
        assertEquals(10f, normalized.tpi ?: 0f, 0.001f)
    }

    @Test
    fun `normalized handles zero values gracefully`() {
        val thread = Threads(pitchMm = 0f, tpi = null)
        val normalized = thread.normalized()
        
        assertEquals(0f, normalized.pitchMm, 0.001f)
        assertNull(normalized.tpi)
    }

    @Test
    fun `ShaftSpec normalized applies to all threads`() {
        val spec = ShaftSpec(
            threads = listOf(
                Threads(pitchMm = 2.54f, tpi = null),
                Threads(pitchMm = 0f, tpi = 8f)
            )
        )
        
        val normalized = spec.normalized()
        
        assertEquals(10f, normalized.threads[0].tpi ?: 0f, 0.01f)
        assertEquals(3.175f, normalized.threads[1].pitchMm, 0.001f) // 25.4 / 8
    }
}
