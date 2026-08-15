package com.android.shaftschematic.ui.screen

import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ShaftSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * estimatedLinerKeptFracOfTrue — the live readout under the "Liner compression"
 * control. Height precedence (on-device direction): the control never changes the drawn
 * height, so the readout reports the fraction of true length liners actually keep at
 * the selected height. Pins: fitting requests kept in full; page-limited requests
 * λ-shortened; kept ≤ requested, monotone; zero request → zero.
 */
class EstimatedLinerKeptFracTest {

    // Long shaft, Ø 8.5" liners (215.9mm), two 700mm liners — the review artifact's
    // sample. At the curve base their full true widths overflow the 720pt page, so a
    // full-proportional request must come back shortened — never the height.
    private val spec = ShaftSpec(
        overallLengthMm = 4000f,
        liners = listOf(
            Liner(startFromAftMm = 800f, lengthMm = 700f, odMm = 215.9f),
            Liner(startFromAftMm = 2600f, lengthMm = 700f, odMm = 215.9f),
        ),
    )
    private val base = 90f / 215.9f

    private fun kept(requested: Float): Float =
        estimatedLinerKeptFracOfTrue(spec, base, heightScale = 1f, requestedFracOfTrue = requested)

    @Test
    fun `page-limited request reports the shortened kept fraction`() {
        // Body gaps share the λ pool (PROFILE_BODY_RUN_MIN_FRAC_OF_TRUE), so a
        // full-proportional liner request settles lower than it would with fixed gap
        // floors — the balance rule. The pinned value is this fixture's λ at the current
        // pool fractions (it rises when the body-run fraction eases, since bodies then
        // demand less of the page).
        val k = kept(1f)
        assertTrue("kept $k must be short of the full request", k < 1f - 0.01f)
        assertEquals(0.792f, k, 0.02f)
    }

    @Test
    fun `fitting request is kept in full`() {
        assertEquals(0.5f, kept(0.5f), 1e-3f)
    }

    @Test
    fun `kept never exceeds requested and never decreases`() {
        var prev = 0f
        for (r in listOf(0.25f, 0.5f, 0.75f, 1f)) {
            val k = kept(r)
            assertTrue("kept $k exceeds requested $r", k <= r + 1e-4f)
            assertTrue("kept fell at $r", k >= prev - 1e-4f)
            prev = k
        }
    }

    @Test
    fun `short shaft keeps the full request`() {
        val short = ShaftSpec(
            overallLengthMm = 600f,
            liners = listOf(Liner(startFromAftMm = 100f, lengthMm = 300f, odMm = 215.9f)),
        )
        assertEquals(1f, estimatedLinerKeptFracOfTrue(short, base, 1f, 1f), 1e-3f)
    }

    @Test
    fun `zero request returns zero`() {
        assertEquals(0f, kept(0f), 1e-6f)
    }
}
