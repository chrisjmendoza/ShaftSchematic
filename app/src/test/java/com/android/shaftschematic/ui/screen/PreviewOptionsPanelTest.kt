package com.android.shaftschematic.ui.screen

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The PDF preview's options are a NON-modal panel in the preview's own layout, not a modal
 * sheet: the page stays reachable — pinch, pan, tap — while the options are up, so a slider's
 * effect can be zoomed in on and watched (on-device request: zoom on the threads while moving
 * the thread sliders). Pins that wiring on the shared [PdfPreviewOverlay]: the panel opens and
 * closes by its button and by Back (Back closing the panel BEFORE the route's own preview-closing
 * handler), a touch on the page leaves it open, its header is exactly the chrome the strip math
 * reserves, and a zoom survives a re-render.
 *
 * Runs on the JVM under Robolectric, so it goes in CI with the plain unit tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class PreviewOptionsPanelTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private var overlayCloses = 0
    // Created per test, not at construction: Robolectric has no bitmap backing yet when the
    // test class is instantiated.
    private var bitmap by mutableStateOf<ImageBitmap?>(null)
    private var loading by mutableStateOf(false)

    private fun setUpOverlay() {
        bitmap = testBitmap()
        rule.setContent {
            MaterialTheme {
                // Mirrors the routes: their preview-closing Back handler is composed BEFORE
                // the overlay that hosts the panel.
                BackHandler { overlayCloses++ }
                PdfPreviewOverlay(
                    bitmap = bitmap,
                    loading = loading,
                    title = "Preview",
                    onClose = { overlayCloses++ },
                    onExport = {},
                    optionsSheet = { Text("probe", Modifier.testTag("probe")) },
                    sheetTunesPage = true,
                )
            }
        }
    }

    // The plain platform overload: Compose's `ImageBitmap(w, h)` goes through a
    // colour-space overload Robolectric's legacy graphics cannot back.
    private fun testBitmap(): ImageBitmap =
        Bitmap.createBitmap(80, 60, Bitmap.Config.ARGB_8888).asImageBitmap()

    private fun openOptions() {
        rule.onNodeWithContentDescription(OPTIONS_ICON).performClick()
        rule.waitForIdle()
    }

    private fun previewWidthPx(): Float =
        rule.onNodeWithContentDescription(PREVIEW).fetchSemanticsNode().boundsInRoot.width

    @Test
    fun `opening the options keeps the page in the same tree and reachable`() {
        setUpOverlay()
        openOptions()

        rule.onNodeWithTag("probe").assertIsDisplayed()
        rule.onNodeWithContentDescription(PREVIEW).assertExists()

        // A touch on the page is a page touch, not a dismiss — a modal sheet took every touch
        // outside it as one.
        rule.onNodeWithContentDescription(PREVIEW).performTouchInput { click(center) }
        rule.waitForIdle()
        rule.onNodeWithTag("probe").assertIsDisplayed()
    }

    @Test
    fun `the close button closes the panel and leaves the preview up`() {
        setUpOverlay()
        openOptions()

        rule.onNodeWithTag("preview_options_close").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("probe").assertDoesNotExist()
        rule.onNodeWithContentDescription(PREVIEW).assertExists()
        assertEquals(0, overlayCloses)
    }

    @Test
    fun `the Tune icon toggles the panel`() {
        setUpOverlay()
        openOptions()
        rule.onNodeWithTag("probe").assertIsDisplayed()

        openOptions()
        rule.onNodeWithTag("probe").assertDoesNotExist()
    }

    @Test
    fun `Back closes the panel before the route's own handler`() {
        setUpOverlay()
        openOptions()

        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()

        rule.onNodeWithTag("probe").assertDoesNotExist()
        rule.onNodeWithContentDescription(PREVIEW).assertExists()
        assertEquals(0, overlayCloses)

        // With the panel gone, Back reaches the route's handler again.
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        assertEquals(1, overlayCloses)
    }

    @Test
    fun `the header is exactly the chrome the strip math reserves`() {
        setUpOverlay()
        openOptions()

        rule.onNodeWithTag("preview_options_header")
            .assertHeightIsEqualTo(TUNING_SHEET_CHROME_DP.dp)
    }

    @Test
    fun `a zoom survives a re-render and resets when the panel closes`() {
        setUpOverlay()
        openOptions()
        val fitWidth = previewWidthPx()

        // Pinch IN (zoom out): the bounds shrink inside the clipped preview Box, so the zoom
        // is readable off the node's bounds — a zoom-in would be clipped back to the Box.
        rule.onNodeWithContentDescription(PREVIEW).performTouchInput {
            pinch(
                start0 = center - Offset(150f, 0f),
                end0 = center - Offset(20f, 0f),
                start1 = center + Offset(150f, 0f),
                end1 = center + Offset(20f, 0f),
            )
        }
        rule.waitForIdle()
        val zoomed = previewWidthPx()
        assertTrue("pinch should zoom the page out ($zoomed vs $fitWidth)", zoomed < fitWidth * 0.9f)

        // A commit re-renders: the page STAYS on screen at its zoom while the pass runs,
        // the corner indicator marks it, and then a NEW bitmap arrives.
        loading = true
        rule.waitForIdle()
        rule.onNodeWithContentDescription(PREVIEW).assertExists()
        rule.onNodeWithTag("preview_rerender_indicator").assertExists()
        assertEquals(zoomed, previewWidthPx(), 0.5f)
        bitmap = testBitmap()
        loading = false
        rule.waitForIdle()
        rule.onNodeWithTag("preview_rerender_indicator").assertDoesNotExist()
        assertEquals(zoomed, previewWidthPx(), 0.5f)

        // Closing the panel switches layouts, so the zoom resets: the full page fills the
        // same width the fitted strip did.
        rule.onNodeWithTag("preview_options_close").performClick()
        rule.waitForIdle()
        assertEquals(fitWidth, previewWidthPx(), 0.5f)
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land")
    fun `at EXPANDED the panel docks beside a full-height page`() {
        setUpOverlay()
        openOptions()

        val density = rule.activity.resources.displayMetrics.density
        val area = rule.onNodeWithTag("pdf_preview_area").fetchSemanticsNode().boundsInRoot
        val panel = rule.onNodeWithTag("preview_options_panel").fetchSemanticsNode().boundsInRoot
        val page = rule.onNodeWithContentDescription(PREVIEW).fetchSemanticsNode().boundsInRoot

        // The panel is the right-hand column, the full height of the preview box.
        assertEquals(dockedPanelWidthDp(area.width / density), panel.width / density, 1f)
        assertEquals(area.height, panel.height, 1f)
        assertEquals(area.right, panel.right, 1f)
        // The page is the ordinary full page in the width left of it — full height, never under
        // the panel, no strip.
        assertEquals(dockedPageWidthDp(area.width / density), page.width / density, 1f)
        assertEquals(area.height, page.height, 1f)
        assertTrue("page ${page.right} ends where the panel ${panel.left} starts", page.right <= panel.left + 1f)

        // Closing gives the page the whole box back.
        rule.onNodeWithTag("preview_options_close").performClick()
        rule.waitForIdle()
        assertEquals(area.width, previewWidthPx(), 1f)
    }

    private companion object {
        const val OPTIONS_ICON = "PDF options"
        const val PREVIEW = "PDF preview — pinch to zoom, drag to pan"
    }
}
