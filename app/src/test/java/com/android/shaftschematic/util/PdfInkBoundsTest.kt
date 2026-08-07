package com.android.shaftschematic.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pure ink-band core behind the tuning page strip: a composed sheet rarely inks its full
 * height, and the strip is where blank paper costs the drawing room. Pins that the band
 * brackets exactly the inked rows, that a blank page reports nothing, that the padding never
 * leaves the page, and that the last row is always sampled — the sampling grid only lands on
 * it by coincidence, and the footer/rail is exactly what must not be cropped.
 */
class PdfInkBoundsTest {

    private fun band(height: Int, rowStep: Int, padFrac: Float, inked: Set<Int>) =
        inkBandFromRows(
            rowHasInk = { y -> y in inked },
            height = height,
            rowStep = rowStep,
            padFrac = padFrac,
        )

    @Test
    fun `the band brackets the first and last inked rows`() {
        // Ink on rows 200..600 of a 1000-row page, no padding: [0.20, 0.601].
        val inked = (200..600).toSet()
        val b = band(height = 1000, rowStep = 1, padFrac = 0f, inked = inked)
        assertNotNull(b)
        assertEquals(0.200f, b!!.topFrac, 1e-4f)
        assertEquals(0.601f, b.bottomFrac, 1e-4f)
        assertEquals(0.401f, b.frac, 1e-4f)
    }

    @Test
    fun `padding widens the band on both sides`() {
        val b = band(height = 1000, rowStep = 1, padFrac = 0.025f, inked = (400..500).toSet())!!
        assertEquals(0.375f, b.topFrac, 1e-4f)
        assertEquals(0.526f, b.bottomFrac, 1e-4f)
    }

    @Test
    fun `an all-white page has no band`() {
        assertNull(band(height = 1000, rowStep = 4, padFrac = 0.025f, inked = emptySet()))
        // …and a zero-height page never reports one either.
        assertNull(band(height = 0, rowStep = 1, padFrac = 0f, inked = setOf(0)))
    }

    @Test
    fun `padding clamps at the page edges`() {
        val b = band(height = 100, rowStep = 1, padFrac = 0.5f, inked = setOf(10, 90))!!
        assertEquals(0f, b.topFrac, 1e-6f)
        assertEquals(1f, b.bottomFrac, 1e-6f)
        assertTrue(b.topFrac < b.bottomFrac)
    }

    @Test
    fun `the last row is always sampled`() {
        // Row 999 is off the 0, 7, 14 … grid; the footer must still be found.
        val b = band(height = 1000, rowStep = 7, padFrac = 0f, inked = setOf(999))
        assertNotNull(b)
        assertEquals(0.999f, b!!.topFrac, 1e-4f)
        assertEquals(1f, b.bottomFrac, 1e-4f)
    }

    @Test
    fun `a row missed between samples still falls inside the pad cushion`() {
        // Adapter defaults: rowStep = height/200, padFrac = 0.025 — the pad is 5 sampling
        // steps wide, so a hairline stepped over is nowhere near the band edge.
        val height = 1224
        val rowStep = maxOf(1, height / 200)
        val padFrac = 0.025f
        assertTrue(padFrac > rowStep.toFloat() / height)

        // Ink on a sampled row plus a hairline the grid steps over, just below it.
        val sampled = 40 * rowStep
        val hairline = sampled + rowStep - 1
        val b = band(height = height, rowStep = rowStep, padFrac = padFrac, inked = setOf(sampled, hairline))!!
        assertTrue(hairline.toFloat() / height < b.bottomFrac)
        assertTrue(sampled.toFloat() / height > b.topFrac)
    }

    @Test
    fun `a single inked row yields a real band`() {
        val b = band(height = 500, rowStep = 1, padFrac = 0f, inked = setOf(250))!!
        assertTrue(b.topFrac < b.bottomFrac)
        assertEquals(1f / 500f, b.frac, 1e-6f)
    }
}
