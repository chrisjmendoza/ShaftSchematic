package com.android.shaftschematic.ui.screen

/**
 * File: CarouselSelectionSync.kt
 * Layer: UI → Screen
 *
 * The selection ↔ pager-page sync decisions for `ComponentCarouselPager`, extracted from the
 * `LaunchedEffect`s so they can be unit-tested without a Compose harness.
 *
 * Two effects keep selection and pager page in agreement, in opposite directions:
 *  • selection changed externally (preview tap, add, delete) → scroll the pager to it
 *  • user swiped the pager → adopt the landed page as the selection
 *
 * They must not fight. An earlier version did, and produced visible jumping — see the comment
 * above the initial-load effect in `ComponentCarousel.kt`.
 */

/** No row matches the selected id (or nothing is selected). */
const val NO_CAROUSEL_TARGET = -1

/**
 * Index of [selectedId] within [orderedIds], or [NO_CAROUSEL_TARGET] if absent.
 *
 * A selected id that resolves to nothing is normal, not an error: selection survives a
 * delete, and auto-generated rows come and go as the resolve layer re-runs.
 */
fun carouselTargetIndex(orderedIds: List<String>, selectedId: String?): Int {
    if (selectedId == null) return NO_CAROUSEL_TARGET
    val index = orderedIds.indexOfFirst { it == selectedId }
    return if (index >= 0) index else NO_CAROUSEL_TARGET
}

/**
 * Whether the pager should animate to [targetIndex].
 *
 * The offset check matters: a pager parked mid-swipe reports the nearest page as
 * `currentPage` while still visually settled between two cards, so "already on the right
 * page" is not sufficient — it must also be squarely on it.
 */
fun shouldAnimateToSelection(
    targetIndex: Int,
    currentPage: Int,
    currentPageOffsetFraction: Float
): Boolean =
    targetIndex >= 0 && (currentPage != targetIndex || currentPageOffsetFraction != 0f)

/**
 * Whether a scroll that just started should be treated as user-initiated.
 *
 * Only scrolls beginning from the currently-selected page count. A scroll starting anywhere
 * else is this component's own `animateScrollToPage` catching up to a selection change, and
 * adopting its landing page as a new selection would overwrite the very selection it was
 * chasing.
 */
fun isUserInitiatedScroll(selectedId: String?, selectedIndex: Int, currentPage: Int): Boolean =
    selectedId == null || selectedIndex == currentPage

/**
 * Whether a settled user swipe from [startPage] to [endPage] should change the selection.
 * A swipe that springs back to where it started is not a selection change.
 */
fun shouldAdoptSwipeSelection(startPage: Int?, endPage: Int): Boolean =
    startPage != null && startPage != endPage
