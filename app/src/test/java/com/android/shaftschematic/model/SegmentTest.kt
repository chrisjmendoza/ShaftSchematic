package com.android.shaftschematic.model

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for Segment interface and component validation logic.
 */
class SegmentTest {

    @Test
    fun `Body isValid returns true for valid body`() {
        val body = Body(startFromAftMm = 0f, lengthMm = 100f, diaMm = 50f)
        assertTrue(body.isValid(200f))
    }

    @Test
    fun `Body isValid returns false for negative start`() {
        val body = Body(startFromAftMm = -10f, lengthMm = 100f, diaMm = 50f)
        assertFalse(body.isValid(200f))
    }

    @Test
    fun `Body isValid returns false for negative length`() {
        val body = Body(startFromAftMm = 0f, lengthMm = -100f, diaMm = 50f)
        assertFalse(body.isValid(200f))
    }

    @Test
    fun `Body isValid returns false for negative diameter`() {
        val body = Body(startFromAftMm = 0f, lengthMm = 100f, diaMm = -50f)
        assertFalse(body.isValid(200f))
    }

    @Test
    fun `Body isValid returns false when exceeds overall length`() {
        val body = Body(startFromAftMm = 150f, lengthMm = 100f, diaMm = 50f)
        assertFalse(body.isValid(200f))
    }

    @Test
    fun `Taper isValid returns true for valid taper`() {
        val taper = Taper(startFromAftMm = 0f, lengthMm = 100f, startDiaMm = 50f, endDiaMm = 30f)
        assertTrue(taper.isValid(200f))
    }

    @Test
    fun `Taper isValid returns false for negative diameters`() {
        val taper = Taper(startFromAftMm = 0f, lengthMm = 100f, startDiaMm = -50f, endDiaMm = 30f)
        assertFalse(taper.isValid(200f))
    }

    @Test
    fun `Taper maxDiaMm returns larger of start and end`() {
        val taper1 = Taper(startDiaMm = 50f, endDiaMm = 30f)
        assertEquals(50f, taper1.maxDiaMm, 0.001f)

        val taper2 = Taper(startDiaMm = 30f, endDiaMm = 50f)
        assertEquals(50f, taper2.maxDiaMm, 0.001f)
    }

    @Test
    fun `Threads isValid returns true for valid thread`() {
        val thread = Threads(
            startFromAftMm = 0f,
            lengthMm = 50f,
            majorDiaMm = 30f,
            pitchMm = 2f
        )
        assertTrue(thread.isValid(200f))
    }

    @Test
    fun `Threads isValid returns false for negative pitch`() {
        val thread = Threads(
            startFromAftMm = 0f,
            lengthMm = 50f,
            majorDiaMm = 30f,
            pitchMm = -2f
        )
        assertFalse(thread.isValid(200f))
    }

    @Test
    fun `Threads hasPitch returns true when pitchMm is positive`() {
        val thread = Threads(pitchMm = 2.5f)
        assertTrue(thread.hasPitch)
    }

    @Test
    fun `Threads hasPitch returns true when tpi is positive`() {
        val thread = Threads(pitchMm = 0f, tpi = 10f)
        assertTrue(thread.hasPitch)
    }

    @Test
    fun `Threads hasPitch returns false when both are zero or null`() {
        val thread = Threads(pitchMm = 0f, tpi = null)
        assertFalse(thread.hasPitch)
    }

    @Test
    fun `Liner isValid returns true for valid liner`() {
        val liner = Liner(startFromAftMm = 0f, lengthMm = 100f, odMm = 60f)
        assertTrue(liner.isValid(200f))
    }

    @Test
    fun `Liner isValid returns false for negative od`() {
        val liner = Liner(startFromAftMm = 0f, lengthMm = 100f, odMm = -60f)
        assertFalse(liner.isValid(200f))
    }

    @Test
    fun `Segment endFromAftMm calculates correctly`() {
        val body: Segment = Body(startFromAftMm = 50f, lengthMm = 100f, diaMm = 50f)
        assertEquals(150f, body.endFromAftMm, 0.001f)
    }

    @Test
    fun `isWithin returns true when segment fits with small tolerance`() {
        val body: Segment = Body(startFromAftMm = 0f, lengthMm = 200f, diaMm = 50f)
        // Should pass with 1e-3 tolerance
        assertTrue(body.isWithin(200.0005f))
    }

    @Test
    fun `isWithin returns false when segment clearly exceeds`() {
        val body: Segment = Body(startFromAftMm = 0f, lengthMm = 200f, diaMm = 50f)
        assertFalse(body.isWithin(150f))
    }
}
