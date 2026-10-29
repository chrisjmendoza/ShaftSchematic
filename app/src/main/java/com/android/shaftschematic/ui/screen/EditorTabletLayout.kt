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
 * The height the component region under the drawing band keeps while the keyboard is up, dp:
 * the "Components" heading, the card's top, and one two-up field row with its label and the
 * "not applied" notice line under it. The band yields height to the keyboard down to this.
 */
const val EXPANDED_CAROUSEL_MIN_HEIGHT_DP = 220f

/** Height of the fade at the foot of the side column while it has more to scroll to, dp. */
const val EXPANDED_SIDE_COLUMN_FADE_DP = 24f

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
 * Height the DRAWING wants in the band across the top of the EXPANDED editor, dp: the preview
 * keeps its [aspect] (width : height) over whatever width is left beside the side column, between
 * [floorDp] and [capDp]. The band itself is the larger of this and the side column's content
 * ([expandedBandHeightDp]), so the side column is never clipped short of the cap.
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

/**
 * Height of the drawing band, dp: the LARGER of what the drawing wants ([drawingDp], from
 * [expandedDrawingBandHeightDp]) and the side column's content ([sideColumnDp] — the overall
 * length box, the add button and the warnings), never over [capDp]. A side column taller than the
 * drawing grows the band rather than being clipped mid-warning under spare paper beside the shaft
 * (on-device report); one taller than the cap scrolls inside the band, so a long warning list can
 * still never push the carousel below the cap.
 */
fun expandedBandHeightDp(drawingDp: Float, sideColumnDp: Float, capDp: Float): Float =
    maxOf(drawingDp, sideColumnDp).coerceAtMost(capDp).coerceAtLeast(0f)

/**
 * The band's height once the keyboard is up, dp. [bandDp] is the band with no keyboard,
 * [viewportDp] the height the band and the component region share, and [imeDp] the keyboard's
 * inset over that viewport. With the keyboard up the band gives up height so the component region
 * keeps [carouselMinDp] — the focused field, its label and its notice stay above the keyboard
 * (on-device report: the band held its height and left the card ~65 dp) — but never below
 * [bandMinDp], the drawing's own floor. No keyboard returns [bandDp] unchanged, and the band only
 * ever shrinks for one.
 */
fun expandedBandHeightUnderImeDp(
    bandDp: Float,
    viewportDp: Float,
    imeDp: Float,
    carouselMinDp: Float,
    bandMinDp: Float,
): Float {
    if (imeDp <= 0f) return bandDp
    val room = viewportDp - imeDp - carouselMinDp
    return minOf(bandDp, room.coerceAtLeast(bandMinDp))
}

/**
 * The widest the preview may draw inside a band [bandInnerDp] tall, dp: the width that keeps its
 * [aspect] at the band's height (never over [capDp]). A band shortened for the keyboard narrows
 * the drawing rather than stretching its box to the full width at the shorter height.
 */
fun expandedPreviewMaxWidthDp(bandInnerDp: Float, aspect: Float, capDp: Float): Float =
    minOf(bandInnerDp, capDp).coerceAtLeast(0f) * aspect

/**
 * Height of the component carousel in a MEDIUM window's scrolling column (a portrait tablet):
 * whatever the column's viewport has left once everything above the carousel is laid out, so the
 * card runs to the foot of the screen — never under [floor], the phone's fixed height, so a short
 * window scrolls the column rather than squeezing the card. [contentHeight] is the column's whole
 * scrolled content and [carouselHeight] the carousel's share of it, both from the same layout
 * pass; any one unit (px or dp) throughout.
 */
fun mediumCarouselHeight(
    viewportHeight: Float,
    contentHeight: Float,
    carouselHeight: Float,
    floor: Float,
): Float = (viewportHeight - (contentHeight - carouselHeight)).coerceAtLeast(floor)
