package com.android.shaftschematic.ui.screen

import com.android.shaftschematic.settings.PdfPrefs
import com.android.shaftschematic.settings.RunoutConfig
import com.android.shaftschematic.settings.TirDirection
import com.android.shaftschematic.ui.adaptive.WindowWidthClass
import com.android.shaftschematic.util.PDF_PAGE_HEIGHT_PT
import com.android.shaftschematic.util.PDF_PAGE_WIDTH_PT
import com.android.shaftschematic.util.PDF_PREVIEW_RENDER_SCALE
import com.android.shaftschematic.util.PdfPageOrientation
import com.android.shaftschematic.util.inkBandFromRows
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
    fun `a landscape tablet keeps the sheet floor and gives the strip the remainder`() {
        // ~1280 x 800 dp — the window a tablet in landscape hands the preview.
        val w = 1280f
        val h = 800f
        val strip = tuningPageStripHeightDp(w, h, chrome)
        val sheet = tuningSheetMaxHeightDp(h, strip, chrome)
        // The fit-width page is taller than this short, wide window, so the sheet keeps its
        // floor and the strip takes what is left — the landscape-phone case at tablet size.
        assertTrue(fitWidthPageHeightDp(w) > h)
        assertEquals(h * TUNING_SHEET_MIN_FRAC, sheet, 0.01f)
        assertEquals(296f, strip, 0.01f)
        assertTrue(strip > 0f)
        assertEquals(h, PREVIEW_TOP_CHROME_DP + strip + sheet + chrome, 0.01f)
    }

    @Test
    fun `a portrait tablet caps the strip so the sheet still clears its floor`() {
        // ~800 x 1280 dp. The fit-width page fits the window, but not alongside the sheet.
        val w = 800f
        val h = 1280f
        val strip = tuningPageStripHeightDp(w, h, chrome)
        val sheet = tuningSheetMaxHeightDp(h, strip, chrome)
        assertTrue(strip < fitWidthPageHeightDp(w))
        assertEquals(584f, strip, 0.01f)
        assertEquals(h * TUNING_SHEET_MIN_FRAC, sheet, 0.01f)
        assertEquals(h, PREVIEW_TOP_CHROME_DP + strip + sheet + chrome, 0.01f)

        // A measured ink band hands the paper it crops back to the sheet, and the sheet
        // stays inside its ceiling — neither term runs away on a tall window.
        val cropped = tuningPageStripHeightDp(w, h, chrome, inkFrac = 0.5f)
        val croppedSheet = tuningSheetMaxHeightDp(h, cropped, chrome)
        assertEquals(fitWidthPageHeightDp(w) * 0.5f, cropped, 0.01f)
        assertTrue(croppedSheet > sheet)
        assertTrue(croppedSheet <= h * PREVIEW_SHEET_MAX_FRAC)
    }

    @Test
    fun `the strip never goes negative on a tiny screen`() {
        assertTrue(tuningPageStripHeightDp(200f, 120f, chrome) >= 0f)
        assertTrue(tuningPageStripHeightDp(731f, 200f, chrome, inkFrac = 0.5f) >= 0f)
    }

    // ── Page shape: the undercut sheet is portrait at 2+ sections ─────────────

    private val portraitAspect = PDF_PAGE_WIDTH_PT.toFloat() / PDF_PAGE_HEIGHT_PT.toFloat()

    @Test
    fun `the page aspect is read off the raster for both page shapes`() {
        val s = PDF_PREVIEW_RENDER_SCALE
        val land = PdfPageOrientation.LANDSCAPE
        val port = PdfPageOrientation.PORTRAIT
        assertEquals(LANDSCAPE_PAGE_ASPECT, pageAspectOf(land.widthPt * s, land.heightPt * s), 1e-4f)
        assertEquals(portraitAspect, pageAspectOf(port.widthPt * s, port.heightPt * s), 1e-4f)
        assertEquals(1.2941f, portraitAspect, 1e-3f)
        // No page yet: the landscape default, never a division by zero.
        assertEquals(LANDSCAPE_PAGE_ASPECT, pageAspectOf(0, 0), 0f)
        // The landscape default leaves every existing caller's strip unchanged.
        assertEquals(fitWidthPageHeightDp(393f), fitWidthPageHeightDp(393f, LANDSCAPE_PAGE_ASPECT), 0f)
    }

    @Test
    fun `a portrait page's strip is sized for the portrait page, so it fills the width`() {
        val w = 393f
        val h = 851f
        val strip = tuningPageStripHeightDp(w, h, chrome, inkFrac = 0.6f, pageAspect = portraitAspect)
        assertEquals(fitWidthPageHeightDp(w, portraitAspect) * 0.6f, strip, 0.01f)
        // drawPageBand fits the band to min(width, strip / band height): a strip exactly the
        // band's fit-width height draws the page across the whole screen width.
        val bandHeightAtFullWidth = w * portraitAspect * 0.6f
        assertEquals(1f, strip / bandHeightAtFullWidth, 1e-4f)
        // A strip sized as if the page were landscape would have drawn it at ~60% width.
        val landscapeSized = tuningPageStripHeightDp(w, h, chrome, inkFrac = 0.6f)
        assertTrue(landscapeSized / bandHeightAtFullWidth < 0.61f)
        // The panel still clears its floor and the layout accounts for the screen.
        val sheet = tuningSheetMaxHeightDp(h, strip, chrome)
        assertTrue(sheet >= h * TUNING_SHEET_MIN_FRAC)
        assertEquals(h, PREVIEW_TOP_CHROME_DP + strip + sheet + chrome, 0.01f)
    }

    @Test
    fun `the ink band crops a portrait page's blank paper as it does a landscape one's`() {
        val w = 393f
        val h = 851f
        val s = PDF_PREVIEW_RENDER_SCALE
        // Portrait raster: two strips and the footer ink rows 160..1000 of 1584, the rest is paper.
        val portH = PdfPageOrientation.PORTRAIT.heightPt * s
        val portBand = inkBandFromRows({ it in 160..1000 }, portH, rowStep = 1, padFrac = 0.025f)!!
        assertTrue(portBand.frac < 0.7f)
        val portStrip = tuningPageStripHeightDp(w, h, chrome, portBand.frac, portraitAspect)
        assertEquals(fitWidthPageHeightDp(w, portraitAspect) * portBand.frac, portStrip, 0.01f)
        assertTrue(portStrip < fitWidthPageHeightDp(w, portraitAspect))

        // Landscape raster, same posture.
        val landH = PdfPageOrientation.LANDSCAPE.heightPt * s
        val landBand = inkBandFromRows({ it in 200..900 }, landH, rowStep = 1, padFrac = 0.025f)!!
        val landStrip = tuningPageStripHeightDp(w, h, chrome, landBand.frac, LANDSCAPE_PAGE_ASPECT)
        assertEquals(fitWidthPageHeightDp(w) * landBand.frac, landStrip, 0.01f)
        assertTrue(landStrip < fitWidthPageHeightDp(w))
    }

    @Test
    fun `an unmeasured portrait page yields to the panel floor`() {
        // Before a band exists the whole portrait page is taller than the room left over, so
        // the panel keeps its floor and the strip takes the remainder (the page fits to it).
        val w = 393f
        val h = 851f
        val strip = tuningPageStripHeightDp(w, h, chrome, pageAspect = portraitAspect)
        val sheet = tuningSheetMaxHeightDp(h, strip, chrome)
        assertTrue(strip < fitWidthPageHeightDp(w, portraitAspect))
        assertEquals(h * TUNING_SHEET_MIN_FRAC, sheet, 0.01f)
        assertEquals(h, PREVIEW_TOP_CHROME_DP + strip + sheet + chrome, 0.01f)
    }

    // ── Measured preview box ──

    @Test
    fun `with the measured preview box the panel meets the strip whatever the top chrome`() {
        // On-device report: a 1080 x 2400 phone at 2.625x (411 x 914 dp, the screen height
        // counting the system bars) with a 52 dp status bar and a 56 dp preview bar — 108 dp of
        // top chrome against the 88 dp allowance — so the panel covered the strip's last 20 dp
        // (the undercut sheet's "Notes" line).
        val w = 411f
        val h = 914f
        val nav = 24f
        val box = h - 52f - 56f - nav
        val ink = 0.887f
        val strip = tuningPageStripHeightDp(w, h, TUNING_SHEET_CHROME_DP, ink, availableHeightDp = box)
        val cap = tuningSheetMaxHeightDp(h, strip, TUNING_SHEET_CHROME_DP, availableHeightDp = box)
        assertEquals("the strip is still the page's ink band at fit width", fitWidthPageHeightDp(w) * ink, strip, 0.01f)
        assertEquals("strip + header + content fill the box exactly", box, strip + TUNING_SHEET_CHROME_DP + cap, 0.01f)
        // The screen-height budget on the same device overran the strip by the chrome shortfall.
        val screenCap = tuningSheetMaxHeightDp(h, tuningPageStripHeightDp(w, h, TUNING_SHEET_CHROME_DP + nav, ink), TUNING_SHEET_CHROME_DP + nav)
        assertEquals(20f, (strip + TUNING_SHEET_CHROME_DP + screenCap) - box, 0.01f)
    }

    @Test
    fun `in a measured box an unmeasured portrait page still yields to the panel floor`() {
        val w = 411f
        val h = 914f
        val box = 782f
        val strip = tuningPageStripHeightDp(w, h, TUNING_SHEET_CHROME_DP, pageAspect = portraitAspect, availableHeightDp = box)
        val cap = tuningSheetMaxHeightDp(h, strip, TUNING_SHEET_CHROME_DP, availableHeightDp = box)
        assertTrue(strip < fitWidthPageHeightDp(w, portraitAspect))
        assertEquals(h * TUNING_SHEET_MIN_FRAC, cap, 0.01f)
        assertEquals(box, strip + TUNING_SHEET_CHROME_DP + cap, 0.01f)
    }

    // ── Undercut options panels (tab + detail page) ──

    @Test
    fun `the undercut panels budget from the measured drawing bottom so the phone's bars cost nothing`() {
        // The same phone: 914 dp of screen height counting the system bars, a 52 dp status bar,
        // a 24 dp navigation bar. The undercut panels measure the drawing block's bottom in ROOT
        // coordinates — which already include the real status bar and the page's own top bar —
        // and carry no fixed top-chrome allowance, so they cannot fall short the way the
        // preview's 88 dp allowance did. The panel (anchored above the navigation bar, header
        // row + capped content) meets the drawing exactly.
        val h = 914f
        val nav = 24f
        for (drawingBottom in listOf(52f + 57f + 260f, 52f + 57f + 330f)) {
            val cap = undercutDetailOptionsMaxHeightDp(h, drawingBottom, TUNING_SHEET_CHROME_DP + nav)
            val panelTop = h - nav - TUNING_SHEET_CHROME_DP - cap
            assertEquals("drawing bottom $drawingBottom", drawingBottom, panelTop, 0.01f)
        }
    }

    @Test
    fun `where the screen height excludes the bars the undercut panels stop short of the drawing`() {
        // Pre-edge-to-edge configuration: screenHeightDp excludes the 52 + 24 dp of bars while the
        // window (and the root coordinates) still span them — the cap comes out conservative.
        val window = 914f
        val nav = 24f
        val screen = window - 52f - nav
        val drawingBottom = 52f + 57f + 260f
        val cap = undercutDetailOptionsMaxHeightDp(screen, drawingBottom, TUNING_SHEET_CHROME_DP + nav)
        val panelTop = window - nav - TUNING_SHEET_CHROME_DP - cap
        assertTrue("panel top $panelTop clears the drawing", panelTop >= drawingBottom)
    }

    @Test
    fun `while typing the undercut drawing leaves the card its floor`() {
        // A landscape tablet with the keyboard up: ~390 dp of page under the bars and keyboard,
        // a 56 dp top bar. The drawing takes the rest above the card's floor and the 1 dp rule.
        val cap = undercutDrawingCapWhileTypingDp(pageHeightDp = 390f, topBarHeightDp = 56f)
        assertEquals(390f - 56f - 1f - UNDERCUT_CARD_MIN_HEIGHT_TYPING_DP, cap, 0.01f)
    }

    @Test
    fun `on a page too short for the card's floor the drawing gets nothing, never a negative cap`() {
        assertEquals(0f, undercutDrawingCapWhileTypingDp(pageHeightDp = 180f, topBarHeightDp = 56f), 0f)
    }

    // ── Docked layout (EXPANDED) ─────────────────────────────────────────────

    @Test
    fun `only an EXPANDED window docks the panel beside the page`() {
        assertEquals(false, previewPanelDocksBeside(WindowWidthClass.COMPACT))
        assertEquals(false, previewPanelDocksBeside(WindowWidthClass.MEDIUM))
        assertEquals(true, previewPanelDocksBeside(WindowWidthClass.EXPANDED))
    }

    @Test
    fun `COMPACT and MEDIUM keep the strip and the centered full page`() {
        // The layouts the phone has always had: closed = full page, a page-tuning panel = strip,
        // a panel that tunes nothing on the page floats over the full page.
        assertEquals(PreviewPageLayout.FULL, previewPageLayout(false, sheetTunesPage = true, docksBeside = false))
        assertEquals(PreviewPageLayout.STRIP, previewPageLayout(true, sheetTunesPage = true, docksBeside = false))
        assertEquals(PreviewPageLayout.FULL, previewPageLayout(true, sheetTunesPage = false, docksBeside = false))
    }

    @Test
    fun `EXPANDED docks every open panel and is the full page while closed`() {
        assertEquals(PreviewPageLayout.FULL, previewPageLayout(false, sheetTunesPage = true, docksBeside = true))
        assertEquals(PreviewPageLayout.DOCKED, previewPageLayout(true, sheetTunesPage = true, docksBeside = true))
        assertEquals(PreviewPageLayout.DOCKED, previewPageLayout(true, sheetTunesPage = false, docksBeside = true))
    }

    @Test
    fun `the docked panel is a phone-width column and the page takes the rest`() {
        // A landscape tablet's preview box beside the permanent sidebar (~1040 dp).
        val box = 1040f
        assertEquals(PREVIEW_PANEL_DOCK_WIDTH_DP, dockedPanelWidthDp(box), 0f)
        assertEquals(box - PREVIEW_PANEL_DOCK_WIDTH_DP, dockedPageWidthDp(box), 0f)
        assertEquals(box, dockedPanelWidthDp(box) + dockedPageWidthDp(box), 0f)
    }

    @Test
    fun `on a narrow EXPANDED box the page keeps at least half`() {
        val box = 600f
        assertEquals(300f, dockedPanelWidthDp(box), 0f)
        assertEquals(300f, dockedPageWidthDp(box), 0f)
        assertEquals(0f, dockedPanelWidthDp(0f), 0f)
        assertEquals(0f, dockedPageWidthDp(0f), 0f)
    }

    @Test
    fun `a docked portrait page is drawn far larger than in the landscape tablet strip`() {
        // The on-device case: Pixel Tablet landscape, preview box ~1040 x 720 dp, a portrait
        // undercut page. Docked, the page fits the box's full height beside the panel; in the
        // strip it fit a band about a third of the screen tall.
        val boxW = 1040f
        val boxH = 720f
        val aspect = PdfPageOrientation.PORTRAIT.heightPt.toFloat() / PdfPageOrientation.PORTRAIT.widthPt
        val pageW = dockedPageWidthDp(boxW)
        val dockedDrawnH = minOf(boxH, pageW * aspect)
        val strip = tuningPageStripHeightDp(
            boxW, 800f, TUNING_SHEET_CHROME_DP, pageAspect = aspect, availableHeightDp = boxH,
        )
        assertEquals(boxH, dockedDrawnH, 0.01f)
        assertTrue("docked $dockedDrawnH vs strip $strip", dockedDrawnH > 2f * strip)
    }
}
