package com.android.shaftschematic.ui.screen

import com.android.shaftschematic.settings.PdfPrefs
import com.android.shaftschematic.settings.RunoutConfig
import com.android.shaftschematic.settings.TirDirection
import com.android.shaftschematic.util.PDF_PREVIEW_RENDER_SCALE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The pure merge/resolution helpers behind live preview tuning: a slider drag is a
 * visual-only override folded into the render inputs, never a write. Pins that an
 * override reaches the drawing through the config/prefs the composers already consume
 * (so the derived liner floor is never a re-stated formula), that no override leaves the
 * committed values disturbed, and that drag frames raster at draft resolution.
 */
class PreviewTuningTest {

    private val config = RunoutConfig(
        componentOverrides = mapOf("body-1" to 4),
        tirDirection = TirDirection.AFT,
        heightScale = 1.2f,
        linersProportional = false,
        linerCompression = 0.4f,
    )

    @Test
    fun `no override leaves the committed config untouched`() {
        assertSame(config, tunedRunoutConfig(config, heightScale = null, linerCompression = null))
    }

    @Test
    fun `height override applies alone`() {
        val tuned = tunedRunoutConfig(config, heightScale = 2.1f, linerCompression = null)
        assertEquals(2.1f, tuned.heightScale, 1e-6f)
        assertEquals(config.linerCompression, tuned.linerCompression, 1e-6f)
        assertEquals(config.componentOverrides, tuned.componentOverrides)
        assertEquals(config.tirDirection, tuned.tirDirection)
    }

    @Test
    fun `liner compression override drives the derived width floor`() {
        val tuned = tunedRunoutConfig(config, heightScale = null, linerCompression = 0.25f)
        assertEquals(config.heightScale, tuned.heightScale, 1e-6f)
        // The floor the composers consume is derived off the copy, not restated.
        assertEquals(0.75f, tuned.linerMinFracOfTrue, 1e-6f)
    }

    @Test
    fun `both overrides apply together`() {
        val tuned = tunedRunoutConfig(config, heightScale = 0.7f, linerCompression = 1f)
        assertEquals(0.7f, tuned.heightScale, 1e-6f)
        assertEquals(1f, tuned.linerCompression, 1e-6f)
        assertEquals(0f, tuned.linerMinFracOfTrue, 1e-6f)
    }

    @Test
    fun `proportional liners still win over a compression override`() {
        val proportional = config.copy(linersProportional = true)
        val tuned = tunedRunoutConfig(proportional, heightScale = null, linerCompression = 0.9f)
        assertEquals(1f, tuned.linerMinFracOfTrue, 1e-6f)
    }

    @Test
    fun `s-break threshold rides in on a prefs copy`() {
        val prefs = PdfPrefs(sBreakThresholdFrac = 0.5f, shadedLiners = true)
        assertSame(prefs, tunedPdfPrefs(prefs, 0.5f))
        val tuned = tunedPdfPrefs(prefs, 0.15f)
        assertEquals(0.15f, tuned.sBreakThresholdFrac, 1e-6f)
        // Every other pref survives the copy — a drag changes one thing only.
        assertEquals(prefs.copy(sBreakThresholdFrac = 0.15f), tuned)
    }

    @Test
    fun `drag frames raster at draft resolution and the release pass at full`() {
        assertEquals(1, previewRenderScale(tuningActive = true))
        assertEquals(PDF_PREVIEW_RENDER_SCALE, previewRenderScale(tuningActive = false))
    }
}
