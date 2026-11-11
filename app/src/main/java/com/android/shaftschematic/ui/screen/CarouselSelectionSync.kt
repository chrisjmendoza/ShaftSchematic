package com.android.shaftschematic.ui.screen

/**
 * File: CarouselSelectionSync.kt
 * Layer: UI → Screen
 *
 * The selection ↔ pager-page sync decisions for `ComponentCarouselPager`, extracted from the
 * `LaunchedEffect`s so they can be unit-tested without a Compose harness.
 *
 * The pager's FIRST page is the start card (Overall Length, shaft diameter, project information)
 * — always page [START_CARD_PAGE], never a component. Component rows follow it in physical order,
 * so the row at index `i` is page `i + 1`. Every conversion between a selected component id and a
 * pager page goes through [carouselSelectedPage] / [componentIdForPage]; nothing else adds or
 * subtracts the start card's page.
 *
 * A null selection IS the start card: a new or opened document clears the selection, which lands
 * the carousel on the start card, and a swipe onto the start card selects nothing.
 *
 * Two effects keep selection and pager page in agreement, in opposite directions:
 *  • selection changed externally (preview tap, add, delete, a warning line) → scroll to it
 *  • user swiped the pager → adopt the landed page as the selection
 *
 * They must not fight — if they do, the result is visible jumping. See the comment
 * above the initial-load effect in `ComponentCarousel.kt`.
 */

/** No page matches the selected id: it no longer resolves to a row (an orphaned selection). */
const val NO_CAROUSEL_TARGET = -1

/** The start card's page — first, whatever the shaft holds. */
const val START_CARD_PAGE = 0

/** The pager's page count over [rowCount] component rows: the start card plus one per row. */
fun carouselPageCount(rowCount: Int): Int = rowCount.coerceAtLeast(0) + 1

/**
 * The page showing [selectedId] among [orderedIds] (the component rows in carousel order):
 * [START_CARD_PAGE] for no selection, the row's index + 1 for a live id, and
 * [NO_CAROUSEL_TARGET] for an id that resolves to nothing.
 *
 * A selected id that resolves to nothing is normal, not an error: selection survives a
 * delete, and auto-generated rows come and go as the resolve layer re-runs.
 */
fun carouselSelectedPage(orderedIds: List<String>, selectedId: String?): Int {
    if (selectedId == null) return START_CARD_PAGE
    val index = orderedIds.indexOfFirst { it == selectedId }
    return if (index >= 0) index + 1 else NO_CAROUSEL_TARGET
}

/**
 * The component id a [page] shows — null for the start card (and for a page past the rows, which
 * a pager never settles on).
 */
fun componentIdForPage(orderedIds: List<String>, page: Int): String? =
    if (page <= START_CARD_PAGE) null else orderedIds.getOrNull(page - 1)

/**
 * Whether the pager should animate to [targetPage].
 *
 * The offset check matters: a pager parked mid-swipe reports the nearest page as
 * `currentPage` while still visually settled between two cards, so "already on the right
 * page" is not sufficient — it must also be squarely on it.
 */
fun shouldAnimateToSelection(
    targetPage: Int,
    currentPage: Int,
    currentPageOffsetFraction: Float
): Boolean =
    targetPage >= 0 && (currentPage != targetPage || currentPageOffsetFraction != 0f)

/**
 * Whether a scroll that just started should be treated as user-initiated.
 *
 * Only scrolls beginning from the currently-selected page count — the start card's page when
 * nothing is selected. A scroll starting anywhere else is this component's own
 * `animateScrollToPage` catching up to a selection change, and adopting its landing page as a
 * new selection would overwrite the very selection it was chasing.
 *
 * An **orphaned** selection ([selectedPage] == [NO_CAROUSEL_TARGET]) also counts as
 * user-initiated: the follow effect never animates toward a missing row
 * ([shouldAnimateToSelection] requires a target ≥ 0), so the scroll cannot be our own
 * catch-up. Without this arm, a selection whose id stops resolving — an auto-body row
 * whose position-derived id regenerates after an edit — would brick swipe adoption
 * permanently: swiping would change cards but never update the selection, so the preview
 * highlight would never follow.
 */
fun isUserInitiatedScroll(selectedPage: Int, currentPage: Int): Boolean =
    selectedPage == NO_CAROUSEL_TARGET || selectedPage == currentPage

/**
 * Whether a settled user swipe from [startPage] to [endPage] should change the selection.
 * A swipe that springs back to where it started is not a selection change.
 */
fun shouldAdoptSwipeSelection(startPage: Int?, endPage: Int): Boolean =
    startPage != null && startPage != endPage

/**
 * Whether the selection is orphaned — its id resolves to no row — so the carousel adopts the
 * card it is on WITHOUT scrolling, bringing the preview highlight back without yanking the user
 * off that card. Orphans are routine: auto-body ids are position-derived and regenerate on every
 * edit. A null selection is not an orphan; it is the start card.
 */
fun shouldAdoptCurrentPage(selectedPage: Int): Boolean = selectedPage == NO_CAROUSEL_TARGET
