package com.android.shaftschematic.geom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The editor preview's room-for-rails rule ([PreviewRailFit]): the rails take the least
 * reservation that fits, up to everything the box has above the shaft; past that they run off the
 * top of the box. No rail is dropped and the shaft is never squeezed.
 */
class PreviewRailFitTest {

    /**
     * A model box: the rail block is [blockPx] tall; the room above the shaft starts at
     * [headroomPx] and grows by half the reservation (the shaft, centred in what is left, sits
     * lower).
     */
    private fun deficit(blockPx: Float, headroomPx: Float) =
        { reserve: Float -> blockPx - (headroomPx + 0.5f * reserve) }

    private val tol = 2f * PreviewRailFit.FIT_TOLERANCE_PX + 0.01f

    @Test
    fun `the cap is the box less padding less the shaft`() {
        assertEquals(357f - 40f - 30f, PreviewRailFit.maxReservePx(357f, 40f, widthFitShaftPx = 30f), 0f)
        // Never negative.
        assertEquals(0f, PreviewRailFit.maxReservePx(50f, 40f, widthFitShaftPx = 300f), 0f)
    }

    @Test
    fun `rails that fit the natural headroom reserve nothing`() {
        assertEquals(PreviewRailFit.Fit(0f, fits = true), PreviewRailFit.fit(240f, deficit(120f, 200f)))
    }

    @Test
    fun `the shaft moves down by the least that fits`() {
        // Block 120; headroom 60 grows by half the reservation → 120 reserved.
        val fit = PreviewRailFit.fit(250f, deficit(120f, 60f))
        assertTrue(fit.fits)
        assertEquals(120f, fit.reservePx, tol)
    }

    @Test
    fun `rails the box cannot hold still draw, with the shaft as low as it goes`() {
        // Cap 60 → room at most 50 + 30 = 80 against a 210 block: it overruns, nothing is dropped.
        val fit = PreviewRailFit.fit(60f, deficit(210f, 50f))
        assertFalse(fit.fits)
        assertEquals(60f, fit.reservePx, 0f)
    }
}
