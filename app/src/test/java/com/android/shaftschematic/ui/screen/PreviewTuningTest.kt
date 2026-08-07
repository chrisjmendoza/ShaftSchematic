package com.android.shaftschematic.ui.screen

import com.android.shaftschematic.settings.PdfPrefs
import com.android.shaftschematic.settings.RunoutConfig
import com.android.shaftschematic.settings.TirDirection
import com.android.shaftschematic.util.PDF_PREVIEW_RENDER_SCALE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pure merge/resolution helpers behind live preview tuning: a slider drag is a
 * visual-only override folded into the render inputs, never a write. Pins that an
 * override reaches the drawing through the config/prefs the composers already consume
 * (so the derived liner floor is never a re-stated formula), that no override leaves the
 * committed values disturbed, and that drag frames raster at draft resolution.
 *
 * Plus the tuning LAYOUT math: live rendering is worthless if the open menu covers the
 * page, so the page takes a fit-width strip on top and the sheet is capped below it.
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

    // ── Tuning layout: page strip on top, sheet capped below it ──────────────

    /**
     * The M3 drag handle plus a typical navigation-bar inset — the sheet furniture that
     * stacks OUTSIDE the content column's cap and so has to be budgeted for.
     */
    private val chrome = TUNING_SHEET_CHROME_DP + 48f

    @Test
    fun `the page strip is the landscape page fitted to the screen width`() {
        // 612 / 792 of the width — the whole sheet, before the ink band crops it.
        assertEquals(303.68f, fitWidthPageHeightDp(393f), 0.01f)
        assertEquals(0.7727f, fitWidthPageHeightDp(1f), 1e-3f)
    }

    @Test
    fun `a phone sheet stops exactly below the strip`() {
        // 1080 x 2340 @ 2.75x ≈ 393 x 851 dp.
        val w = 393f
        val h = 851f
        val strip = tuningPageStripHeightDp(w, h, chrome)
        val sheet = tuningSheetMaxHeightDp(h, strip, chrome)
        assertEquals(303.68f, strip, 0.01f)
        assertEquals(363.32f, sheet, 0.01f)
        // Chrome + strip + sheet + the sheet's own furniture account for the screen:
        // nothing of the page is covered.
        assertEquals(h, PREVIEW_TOP_CHROME_DP + strip + sheet + chrome, 0.01f)
        // …and the sheet is well clear of both clamps.
        assertTrue(sheet > h * TUNING_SHEET_MIN_FRAC)
        assertTrue(sheet < h * PREVIEW_SHEET_MAX_FRAC)
    }

    @Test
    fun `the sheet's own chrome comes out of the sheet, never out of the identity`() {
        val w = 393f
        val h = 851f
        val lean = tuningPageStripHeightDp(w, h, sheetChromeDp = 0f)
        val fat = tuningPageStripHeightDp(w, h, chrome)
        // Neither is clamped here, so the strip is the same fit-width page either way…
        assertEquals(lean, fat, 0.01f)
        // …and the extra furniture is paid for entirely by the sheet.
        val leanSheet = tuningSheetMaxHeightDp(h, lean, 0f)
        val fatSheet = tuningSheetMaxHeightDp(h, fat, chrome)
        assertEquals(chrome, leanSheet - fatSheet, 0.01f)
        assertEquals(h, PREVIEW_TOP_CHROME_DP + fat + fatSheet + chrome, 0.01f)
    }

    @Test
    fun `the strip shortens with the page's ink band`() {
        val w = 393f
        val h = 851f
        val whole = tuningPageStripHeightDp(w, h, chrome, inkFrac = 1f)
        val cropped = tuningPageStripHeightDp(w, h, chrome, inkFrac = 0.6f)
        assertEquals(whole * 0.6f, cropped, 0.01f)
        // The room the blank paper gave up goes to the sheet.
        assertTrue(tuningSheetMaxHeightDp(h, cropped, chrome) > tuningSheetMaxHeightDp(h, whole, chrome))
        // A degenerate band cannot collapse the strip to nothing.
        assertEquals(whole * 0.05f, tuningPageStripHeightDp(w, h, chrome, inkFrac = 0f), 0.01f)
    }

    @Test
    fun `a short wide screen keeps the sheet floor and the strip yields`() {
        // Landscape phone: the fit-width page alone is taller than the screen.
        val w = 731f
        val h = 411f
        assertTrue(fitWidthPageHeightDp(w) > h)
        val strip = tuningPageStripHeightDp(w, h, chrome)
        val sheet = tuningSheetMaxHeightDp(h, strip, chrome)
        // Sheet keeps its floor; the strip takes the remainder (the page fits to it).
        assertEquals(h * TUNING_SHEET_MIN_FRAC, sheet, 0.01f)
        assertEquals(62.6f, strip, 0.01f)
        assertTrue(strip > 0f)
        assertTrue(strip < fitWidthPageHeightDp(w))
        assertEquals(h, PREVIEW_TOP_CHROME_DP + strip + sheet + chrome, 0.01f)
    }

    @Test
    fun `a very tall screen still leaves an edge to swipe the sheet down by`() {
        val w = 300f
        val h = 2000f
        val strip = tuningPageStripHeightDp(w, h, chrome)
        assertEquals(h * PREVIEW_SHEET_MAX_FRAC, tuningSheetMaxHeightDp(h, strip, chrome), 0.01f)
        // The strip is not stretched to fill the surplus — it stays fit-width.
        assertEquals(fitWidthPageHeightDp(w), strip, 0.01f)
    }

    @Test
    fun `the strip never goes negative on a tiny screen`() {
        assertTrue(tuningPageStripHeightDp(200f, 120f, chrome) >= 0f)
        assertTrue(tuningPageStripHeightDp(731f, 200f, chrome, inkFrac = 0.5f) >= 0f)
    }
}
