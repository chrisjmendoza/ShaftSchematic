package com.android.shaftschematic.ui.screen

import kotlin.math.abs

/**
 * EditorTabletLayout — the pure numbers behind the Schematic editor's EXPANDED (landscape
 * tablet) layout: the drawing band across the top and the peeking component carousel under it.
 *
 * On a phone one card fills the pager. On a tablet the pager is wider than a card wants to be,
 * so the CURRENT card sits centred at [CAROUSEL_PEEK_PAGE_WIDTH_DP] and its neighbours peek in
 * on either side, faded and slightly shrunk, so the next and previous components are visible
 * and a tap on one brings it to the centre. Everything here is plain dp/fraction arithmetic
 * with no Compose dependency, so `EditorTabletLayoutTest` pins it on the JVM.
 */

/** Width of the centred card in a peeking carousel, dp — a little wider than a phone card. */
const val CAROUSEL_PEEK_PAGE_WIDTH_DP = 520f

/** Gap between the centred card and each peeking neighbour, dp. */
const val CAROUSEL_PEEK_SPACING_DP = 16f

/** The alpha a neighbour settles at one full page off centre; the centred card is opaque. */
const val CAROUSEL_PEEK_NEIGHBOUR_ALPHA = 0.45f

/** The scale a neighbour settles at one full page off centre; the centred card is full size. */
const val CAROUSEL_PEEK_NEIGHBOUR_SCALE = 0.94f

/**
 * Width of the CONTROLS in the column beside the drawing — the overall-length box, the add
 * button and the warnings share this one width so they read as a block, dp. The column itself
 * is this plus the OAL field's ✓/✗ action slot, which sits to the right of the box.
 */
const val EXPANDED_SIDE_COLUMN_WIDTH_DP = 260f

/** Gap between the drawing and that side column, dp. */
const val EXPANDED_BAND_GAP_DP = 16f

/**
 * The card width a peeking pager uses: [requestedDp], never wider than the pager itself (a
 * narrow tablet window still shows one whole card) and never negative.
 */
fun peekPageWidthDp(pagerWidthDp: Float, requestedDp: Float): Float =
    minOf(requestedDp, pagerWidthDp).coerceAtLeast(0f)

/** Side padding that centres a page [pageWidthDp] wide in a pager [pagerWidthDp] wide. */
fun peekSidePaddingDp(pagerWidthDp: Float, pageWidthDp: Float): Float =
    ((pagerWidthDp - pageWidthDp) / 2f).coerceAtLeast(0f)

/**
 * How far [page] sits from the centre, in pages, clamped to `[0, 1]`: 0 is the centred card,
 * 1 a neighbour fully off centre. [currentPageOffsetFraction] is the pager's own signed
 * fraction (positive while scrolling toward the next page), so a card sliding in brightens
 * continuously as the one sliding out fades.
 */
fun pageOffsetFromCentre(page: Int, currentPage: Int, currentPageOffsetFraction: Float): Float =
    abs((currentPage - page) + currentPageOffsetFraction).coerceIn(0f, 1f)

/** Alpha for a card [offset] pages from the centre — 1 at the centre, [CAROUSEL_PEEK_NEIGHBOUR_ALPHA] one page off. */
fun neighbourAlpha(offset: Float): Float =
    1f + (CAROUSEL_PEEK_NEIGHBOUR_ALPHA - 1f) * offset.coerceIn(0f, 1f)

/** Scale for a card [offset] pages from the centre — 1 at the centre, [CAROUSEL_PEEK_NEIGHBOUR_SCALE] one page off. */
fun neighbourScale(offset: Float): Float =
    1f + (CAROUSEL_PEEK_NEIGHBOUR_SCALE - 1f) * offset.coerceIn(0f, 1f)

/**
 * Height of the drawing band across the top of the EXPANDED editor, dp: the preview keeps its
 * [aspect] (width : height) over whatever width is left beside the side column, between
 * [floorDp] and [capDp]. The band is sized from the drawing, never from the side column, so
 * the column scrolls inside the band and a long warning list can never push the carousel down.
 */
fun expandedDrawingBandHeightDp(
    contentWidthDp: Float,
    aspect: Float,
    sideColumnWidthDp: Float = EXPANDED_SIDE_COLUMN_WIDTH_DP,
    gapDp: Float = EXPANDED_BAND_GAP_DP,
    floorDp: Float,
    capDp: Float,
): Float {
    val previewWidth = (contentWidthDp - sideColumnWidthDp - gapDp).coerceAtLeast(0f)
    return (previewWidth / aspect).coerceIn(floorDp, capDp)
}
