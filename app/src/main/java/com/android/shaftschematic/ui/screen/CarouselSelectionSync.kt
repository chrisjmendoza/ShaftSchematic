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
 *
 * An **orphaned** selection ([selectedIndex] == [NO_CAROUSEL_TARGET]) also counts as
 * user-initiated: the follow effect never animates toward a missing row
 * ([shouldAnimateToSelection] requires `targetIndex >= 0`), so the scroll cannot be our own
 * catch-up. Without this arm, a selection whose id stopped resolving — an auto-body row
 * whose position-derived id regenerated after an edit, or a stale id carried across a
 * document open — bricked swipe adoption permanently: swiping changed cards but never
 * updated the selection, so the preview highlight never followed (fixed).
 */
fun isUserInitiatedScroll(selectedId: String?, selectedIndex: Int, currentPage: Int): Boolean =
    selectedId == null || selectedIndex == NO_CAROUSEL_TARGET || selectedIndex == currentPage

/**
 * Whether a settled user swipe from [startPage] to [endPage] should change the selection.
 * A swipe that springs back to where it started is not a selection change.
 */
fun shouldAdoptSwipeSelection(startPage: Int?, endPage: Int): Boolean =
    startPage != null && startPage != endPage

/** What the carousel's seed/heal effect should do for the current selection state. */
enum class SeedAction {
    /** Selection resolves to a live row (or there are no rows) — leave everything alone. */
    NONE,
    /**
     * Nothing selected: fresh document — scroll to and select the FIRST card (physical
     * order, so the AFT-most component: thread, taper, whatever leads the shaft). Product
     * decision: on open, the highlight defaults to the first item rather than
     * remembering or guessing a previous selection.
     */
    SEED_FIRST,
    /**
     * Selection is orphaned (its id no longer resolves to a row): adopt the current page's
     * row WITHOUT scrolling, so the preview highlight comes back without yanking the user
     * off the card they are on. Orphans are routine — auto-body ids are position-derived
     * and regenerate on every edit.
     */
    ADOPT_CURRENT,
}

/**
 * Seed-or-heal decision for the carousel selection, so the preview highlight is always
 * present whenever highlighting is enabled and the shaft has components:
 *  • no rows → [SeedAction.NONE] (nothing to highlight)
 *  • nothing selected (document just opened/created) → [SeedAction.SEED_FIRST]
 *  • orphaned selection → [SeedAction.ADOPT_CURRENT]
 *  • live selection → [SeedAction.NONE]
 */
fun seedSelectionAction(rowCount: Int, selectedId: String?, selectedIndex: Int): SeedAction =
    when {
        rowCount <= 0 -> SeedAction.NONE
        selectedId == null -> SeedAction.SEED_FIRST
        selectedIndex == NO_CAROUSEL_TARGET -> SeedAction.ADOPT_CURRENT
        else -> SeedAction.NONE
    }
