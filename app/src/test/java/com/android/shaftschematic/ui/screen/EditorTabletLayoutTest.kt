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
