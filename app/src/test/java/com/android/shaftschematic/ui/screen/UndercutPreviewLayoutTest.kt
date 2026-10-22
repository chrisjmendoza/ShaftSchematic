package com.android.shaftschematic.ui.screen

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import com.android.shaftschematic.ui.viewmodel.addBodyAt
import com.android.shaftschematic.ui.viewmodel.addLinerAt
import com.android.shaftschematic.util.PDF_PREVIEW_RENDER_SCALE
import com.android.shaftschematic.util.PdfPageOrientation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The undercut preview's options panel keeps the page in view: it takes the page-strip layout
 * the wear sheet uses (on-device report: the panel covered the whole page, so nothing it changed
 * could be watched), and the strip takes the page's own shape — the undercut sheet is the one
 * portrait-capable document, and a strip sized for a landscape page would draw a portrait one at
 * ~60% of the width.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class UndercutPreviewLayoutTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun dp(px: Float): Float = px / rule.activity.resources.displayMetrics.density

    private fun screenWidthDp(): Float = rule.activity.resources.configuration.screenWidthDp.toFloat()
    private fun screenHeightDp(): Float = rule.activity.resources.configuration.screenHeightDp.toFloat()

    @Test
    fun `a portrait page opens the strip at the portrait page's fit-width height`() {
        val o = PdfPageOrientation.PORTRAIT
        val s = PDF_PREVIEW_RENDER_SCALE
        // Created in the test, not at construction: Robolectric has no bitmap backing before.
        val portrait = Bitmap.createBitmap(o.widthPt * s, o.heightPt * s, Bitmap.Config.ARGB_8888)
            .asImageBitmap()
        rule.setContent {
            MaterialTheme {
                PdfPreviewOverlay(
                    bitmap = portrait,
                    loading = false,
                    title = "Undercut Drawing Preview",
                    onClose = {},
                    onExport = {},
                    optionsSheet = { Text("probe", Modifier.testTag("probe")) },
                    sheetTunesPage = true,
                )
            }
        }
        rule.onNodeWithContentDescription(OPTIONS_ICON).performClick()
        rule.waitForIdle()

        val strip = rule.onNodeWithContentDescription(PREVIEW).fetchSemanticsNode().boundsInRoot
        val expected = tuningPageStripHeightDp(
            screenWidthDp(), screenHeightDp(), TUNING_SHEET_CHROME_DP + navBarDp(),
            inkFrac = 1f, pageAspect = pageAspectOf(portrait.width, portrait.height),
        )
        assertEquals(expected, dp(strip.height), 1f)
        // The strip spans the screen width — the page tops the screen, not a sliver of it.
        assertEquals(screenWidthDp(), dp(strip.width), 1f)
    }

    @Test
    fun `the undercut route's preview panel stops below the page strip`() {
        val vm = ShaftViewModel(ApplicationProvider.getApplicationContext<Application>()).also {
            it.onSetOverallLengthMm(3000f)
            it.addBodyAt(startMm = 0f, lengthMm = 3000f, diaMm = 152.4f)
            it.addLinerAt(startMm = 400f, lengthMm = 400f, odMm = 170f)
            it.addLinerAt(startMm = 1800f, lengthMm = 500f, odMm = 160f)
        }
        rule.setContent { MaterialTheme { UndercutRoute(vm = vm) } }
        rule.onNodeWithTag("undercut_tab_preview").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription(OPTIONS_ICON).performClick()
        rule.waitForIdle()

        val panel = rule.onNodeWithTag("preview_options_panel").fetchSemanticsNode().boundsInRoot
        // The strip the panel must stay clear of: whatever page the route rendered (none under
        // Robolectric's PDF stack, which is the landscape default with no ink band) — the same
        // inputs the overlay itself reads.
        val chrome = TUNING_SHEET_CHROME_DP + navBarDp()
        val strip = tuningPageStripHeightDp(screenWidthDp(), screenHeightDp(), chrome)
        val cap = tuningSheetMaxHeightDp(screenHeightDp(), strip, chrome)
        val panelDp = dp(panel.height)
        // Header row + content capped below the strip. The plain 78% cap would let the panel
        // run up over the page.
        assertTrue(
            "panel $panelDp dp must fit under the strip (cap $cap + header $TUNING_SHEET_CHROME_DP)",
            panelDp <= cap + TUNING_SHEET_CHROME_DP + 1f,
        )
        assertTrue(
            "this sheet's options are taller than the strip cap, so the cap is what holds it " +
                "($panelDp dp vs the plain cap ${screenHeightDp() * PREVIEW_SHEET_MAX_FRAC})",
            panelDp > cap,
        )
    }

    @Test
    fun `the tab's options panel stops below the overview canvas it restyles`() {
        val vm = ShaftViewModel(ApplicationProvider.getApplicationContext<Application>()).also {
            it.onSetOverallLengthMm(3000f)
            it.addBodyAt(startMm = 0f, lengthMm = 3000f, diaMm = 152.4f)
            it.addLinerAt(startMm = 400f, lengthMm = 400f, odMm = 170f)
            it.addLinerAt(startMm = 1800f, lengthMm = 500f, odMm = 160f)
        }
        rule.setContent { MaterialTheme { UndercutRoute(vm = vm) } }
        rule.onNodeWithTag("undercut_tab_options").performScrollTo().performClick()
        rule.waitForIdle()

        val overview = rule.onNodeWithContentDescription(SheetSemantics.undercutOverview(0))
            .fetchSemanticsNode().boundsInRoot
        val panel = rule.onNodeWithTag("preview_options_panel").fetchSemanticsNode().boundsInRoot
        assertTrue(
            "overview bottom ${dp(overview.bottom)} dp must sit above the panel top ${dp(panel.top)} dp",
            overview.bottom <= panel.top + 1f,
        )
        // The options are taller than the room left, so the cap is what holds the panel there —
        // the plain 78% cap would have run it up over the overview.
        assertTrue(
            "panel ${dp(panel.height)} dp should be held by the cap",
            dp(panel.height) < screenHeightDp() * PREVIEW_SHEET_MAX_FRAC,
        )
    }

    private fun navBarDp(): Float = 0f

    private companion object {
        const val OPTIONS_ICON = "PDF options"
        const val PREVIEW = "PDF preview — pinch to zoom, drag to pan"
    }
}
