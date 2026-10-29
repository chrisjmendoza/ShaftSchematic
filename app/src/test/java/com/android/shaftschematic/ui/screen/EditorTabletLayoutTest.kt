package com.android.shaftschematic.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Test

/** The EXPANDED editor's layout numbers: the peeking carousel and the drawing band. */
class EditorTabletLayoutTest {

    @Test
    fun `the centred card takes its requested width and the rest is split into peeks`() {
        val page = peekPageWidthDp(pagerWidthDp = 1000f, requestedDp = CAROUSEL_PEEK_PAGE_WIDTH_DP)
        assertEquals(520f, page, 1e-3f)
        assertEquals(240f, peekSidePaddingDp(1000f, page), 1e-3f)
    }

    @Test
    fun `a pager narrower than the card shows one whole card with no peek`() {
        val page = peekPageWidthDp(pagerWidthDp = 400f, requestedDp = CAROUSEL_PEEK_PAGE_WIDTH_DP)
        assertEquals(400f, page, 1e-3f)
        assertEquals(0f, peekSidePaddingDp(400f, page), 1e-3f)
    }

    @Test
    fun `offset from centre follows the pager's signed fraction`() {
        // Settled on page 2: the neighbours are one full page off, page 2 is centred.
        assertEquals(0f, pageOffsetFromCentre(2, 2, 0f), 1e-6f)
        assertEquals(1f, pageOffsetFromCentre(3, 2, 0f), 1e-6f)
        assertEquals(1f, pageOffsetFromCentre(1, 2, 0f), 1e-6f)
        // Dragging 30% toward page 3: page 3 is closer, page 1 is clamped at 1.
        assertEquals(0.3f, pageOffsetFromCentre(2, 2, 0.3f), 1e-6f)
        assertEquals(0.7f, pageOffsetFromCentre(3, 2, 0.3f), 1e-6f)
        assertEquals(1f, pageOffsetFromCentre(1, 2, 0.3f), 1e-6f)
    }

    @Test
    fun `the centred card is opaque and full size and a neighbour is faded and shrunk`() {
        assertEquals(1f, neighbourAlpha(0f), 1e-6f)
        assertEquals(1f, neighbourScale(0f), 1e-6f)
        assertEquals(CAROUSEL_PEEK_NEIGHBOUR_ALPHA, neighbourAlpha(1f), 1e-6f)
        assertEquals(CAROUSEL_PEEK_NEIGHBOUR_SCALE, neighbourScale(1f), 1e-6f)
        // Halfway is halfway, and anything past a full page stays at the neighbour values.
        assertEquals((1f + CAROUSEL_PEEK_NEIGHBOUR_ALPHA) / 2f, neighbourAlpha(0.5f), 1e-6f)
        assertEquals(CAROUSEL_PEEK_NEIGHBOUR_ALPHA, neighbourAlpha(2f), 1e-6f)
    }

    @Test
    fun `the drawing band keeps the preview's shape over the width beside the side column`() {
        // 1008 dp of content: 1008 − 300 − 16 = 692 wide → 173 tall at 4:1, 230.67 at 3:1.
        assertEquals(692f / 4f, expandedDrawingBandHeightDp(1008f, aspect = 4f, sideColumnWidthDp = 300f, floorDp = 120f, capDp = 320f), 1e-3f)
        assertEquals(692f / 3f, expandedDrawingBandHeightDp(1008f, aspect = 3f, sideColumnWidthDp = 300f, floorDp = 120f, capDp = 320f), 1e-3f)
    }

    @Test
    fun `the drawing band is clamped between its floor and its cap`() {
        assertEquals(120f, expandedDrawingBandHeightDp(400f, aspect = 4f, floorDp = 120f, capDp = 320f), 1e-3f)
        assertEquals(320f, expandedDrawingBandHeightDp(2000f, aspect = 4f, floorDp = 120f, capDp = 320f), 1e-3f)
    }

    @Test
    fun `the band is the taller of the drawing and the side column`() {
        // The side column (OAL box, add button, two warnings) is taller than the drawing: the
        // band grows to it rather than clipping the second warning.
        assertEquals(260f, expandedBandHeightDp(drawingDp = 171f, sideColumnDp = 260f, capDp = 320f), 1e-3f)
        // A short side column leaves the band at the drawing's height.
        assertEquals(171f, expandedBandHeightDp(drawingDp = 171f, sideColumnDp = 140f, capDp = 320f), 1e-3f)
        // Nothing measured yet (first frame): the drawing decides.
        assertEquals(171f, expandedBandHeightDp(drawingDp = 171f, sideColumnDp = 0f, capDp = 320f), 1e-3f)
    }

    @Test
    fun `a side column past the cap scrolls inside a band held at the cap`() {
        assertEquals(320f, expandedBandHeightDp(drawingDp = 171f, sideColumnDp = 900f, capDp = 320f), 1e-3f)
    }

    @Test
    fun `with no keyboard the band keeps its height`() {
        assertEquals(
            187f,
            expandedBandHeightUnderImeDp(
                bandDp = 187f, viewportDp = 690f, imeDp = 0f,
                carouselMinDp = EXPANDED_CAROUSEL_MIN_HEIGHT_DP, bandMinDp = 136f,
            ),
            1e-3f,
        )
    }

    @Test
    fun `under a keyboard the band yields so the carousel keeps its floor`() {
        // 800 of viewport, a 365 dp keyboard: 800 − 365 − 220 = 215 for the band.
        val band = expandedBandHeightUnderImeDp(
            bandDp = 336f, viewportDp = 800f, imeDp = 365f,
            carouselMinDp = EXPANDED_CAROUSEL_MIN_HEIGHT_DP, bandMinDp = 136f,
        )
        assertEquals(215f, band, 1e-3f)
        assertEquals(EXPANDED_CAROUSEL_MIN_HEIGHT_DP, 800f - 365f - band, 1e-3f)
        // A band already short enough is left alone — the keyboard never grows it.
        assertEquals(
            150f,
            expandedBandHeightUnderImeDp(
                bandDp = 150f, viewportDp = 800f, imeDp = 365f,
                carouselMinDp = EXPANDED_CAROUSEL_MIN_HEIGHT_DP, bandMinDp = 136f,
            ),
            1e-3f,
        )
    }

    @Test
    fun `under a keyboard the band never drops below its floor`() {
        assertEquals(
            136f,
            expandedBandHeightUnderImeDp(
                bandDp = 336f, viewportDp = 400f, imeDp = 300f,
                carouselMinDp = EXPANDED_CAROUSEL_MIN_HEIGHT_DP, bandMinDp = 136f,
            ),
            1e-3f,
        )
    }

    @Test
    fun `the preview's width follows the band's height at its own shape`() {
        // A 120 dp band at 4:1 draws 480 wide; a band past the cap is held to the cap's width.
        assertEquals(480f, expandedPreviewMaxWidthDp(bandInnerDp = 120f, aspect = 4f, capDp = 320f), 1e-3f)
        assertEquals(1280f, expandedPreviewMaxWidthDp(bandInnerDp = 400f, aspect = 4f, capDp = 320f), 1e-3f)
    }

    @Test
    fun `a medium carousel takes what the viewport has left above its floor`() {
        // 900 of viewport; the content is 1000 tall of which the carousel is 360, so 640 sits above.
        assertEquals(900f - 640f, mediumCarouselHeight(900f, 1000f, 360f, floor = 200f), 1e-3f)
        // Re-measured at that height the answer holds — the layout settles in one pass.
        assertEquals(260f, mediumCarouselHeight(900f, 640f + 260f, 260f, floor = 200f), 1e-3f)
    }

    @Test
    fun `a medium carousel never drops under the phone's height`() {
        assertEquals(360f, mediumCarouselHeight(700f, 1000f, 360f, floor = 360f), 1e-3f)
    }
}
