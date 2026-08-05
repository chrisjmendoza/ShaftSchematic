package com.android.shaftschematic.ui.screen

import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ShaftSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * estimatedShaftHeightIn — the live readout under the "Liner compression" control
 * (on-device report: the slider "gives no indication on how it's changing the height").
 * Pins: no liner demand → full height; raising the liner fraction is monotone
 * non-increasing; a pinned-liner demand that overflows the page yields height.
 */
class EstimatedShaftHeightTest {

    // Long shaft, Ø 8" (203.2mm), two 700mm liners — the review artifact's sample. At
    // 0.4429 pt/mm the liners' true widths (310pt each) plus floors overflow 720pt when
    // pinned, so the height must yield.
    private val spec = ShaftSpec(
        overallLengthMm = 4000f,
        liners = listOf(
            Liner(startFromAftMm = 800f, lengthMm = 700f, odMm = 215.9f),
            Liner(startFromAftMm = 2600f, lengthMm = 700f, odMm = 215.9f),
        ),
    )
    private val base = 90f / 215.9f // curve height over the liner OD (the max dia here)

    @Test
    fun `free compression keeps the full slider height`() {
        val h = estimatedShaftHeightIn(spec, base, heightScale = 1f, linerMinFracOfTrue = 0f)
        assertEquals(90f / 72f, h, 1e-3f)
    }

    @Test
    fun `raising the liner fraction never raises the height and eventually costs it`() {
        var prev = Float.MAX_VALUE
        for (frac in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
            val h = estimatedShaftHeightIn(spec, base, 1f, frac)
            assertTrue("height rose at frac=$frac", h <= prev + 1e-4f)
            prev = h
        }
        val pinned = estimatedShaftHeightIn(spec, base, 1f, 1f)
        val free = estimatedShaftHeightIn(spec, base, 1f, 0f)
        assertTrue("pinned liners must cost height on this shaft ($pinned vs $free)", pinned < free - 0.05f)
    }

    @Test
    fun `a short shaft holds full height even fully proportional`() {
        val short = ShaftSpec(
            overallLengthMm = 600f,
            liners = listOf(Liner(startFromAftMm = 100f, lengthMm = 300f, odMm = 215.9f)),
        )
        // True total at this scale is well under the page — nothing to yield.
        val h = estimatedShaftHeightIn(short, base, 1f, linerMinFracOfTrue = 1f)
        assertEquals(90f / 72f, h, 1e-3f)
    }
}
