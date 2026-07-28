package com.android.shaftschematic.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the selection ↔ pager-page sync used by `ComponentCarouselPager`, including the
 * "tap a component in the preview → carousel scrolls to its card" path.
 *
 * The two directions must not fight each other; several cases below exist specifically to
 * pin the guards that keep the follow-selection effect from re-triggering the swipe effect.
 */
class CarouselSelectionSyncTest {

    private val rows = listOf("body-1", "taper-1", "liner-1", "body-2")

    // ── carouselTargetIndex ──────────────────────────────────────────────────────

    @Test
    fun `resolves the index of the selected id`() {
        assertEquals(0, carouselTargetIndex(rows, "body-1"))
        assertEquals(2, carouselTargetIndex(rows, "liner-1"))
        assertEquals(3, carouselTargetIndex(rows, "body-2"))
    }

    @Test
    fun `null selection has no target`() {
        assertEquals(NO_CAROUSEL_TARGET, carouselTargetIndex(rows, null))
    }

    @Test
    fun `selection that no longer resolves has no target`() {
        // Normal after a delete, or when an auto-body id disappears on re-resolve.
        assertEquals(NO_CAROUSEL_TARGET, carouselTargetIndex(rows, "deleted-body"))
    }

    @Test
    fun `empty carousel has no target`() {
        assertEquals(NO_CAROUSEL_TARGET, carouselTargetIndex(emptyList(), "body-1"))
    }

    // ── shouldAnimateToSelection ─────────────────────────────────────────────────

    @Test
    fun `scrolls when the selected card is on another page`() {
        // The preview-tap case: user taps liner-1 while the carousel sits on body-1.
        assertTrue(shouldAnimateToSelection(targetIndex = 2, currentPage = 0, currentPageOffsetFraction = 0f))
    }

    @Test
    fun `does not scroll when already settled on the selected card`() {
        assertFalse(shouldAnimateToSelection(targetIndex = 2, currentPage = 2, currentPageOffsetFraction = 0f))
    }

    @Test
    fun `scrolls when on the right page but not settled on it`() {
        // A pager parked mid-swipe reports the nearest page as currentPage while still
        // visually between two cards. Being on the right page is not enough.
        assertTrue(shouldAnimateToSelection(targetIndex = 2, currentPage = 2, currentPageOffsetFraction = 0.35f))
        assertTrue(shouldAnimateToSelection(targetIndex = 2, currentPage = 2, currentPageOffsetFraction = -0.2f))
    }

    @Test
    fun `never scrolls without a resolvable target`() {
        // A deleted selection must not drag the pager to page -1.
        assertFalse(
            shouldAnimateToSelection(NO_CAROUSEL_TARGET, currentPage = 1, currentPageOffsetFraction = 0f)
        )
        assertFalse(
            shouldAnimateToSelection(NO_CAROUSEL_TARGET, currentPage = 1, currentPageOffsetFraction = 0.5f)
        )
    }

    // ── isUserInitiatedScroll ────────────────────────────────────────────────────

    @Test
    fun `a scroll starting from the selected page is the user swiping`() {
        assertTrue(isUserInitiatedScroll(selectedId = "liner-1", selectedIndex = 2, currentPage = 2))
    }

    @Test
    fun `a scroll starting away from the selected page is our own catch-up`() {
        // This is the guard that stops the loop: the follow-selection effect is animating
        // toward page 2, and if we adopted its landing page as a new selection we would be
        // overwriting the selection we were chasing.
        assertFalse(isUserInitiatedScroll(selectedId = "liner-1", selectedIndex = 2, currentPage = 0))
    }

    @Test
    fun `with nothing selected any scroll is the user`() {
        assertTrue(isUserInitiatedScroll(selectedId = null, selectedIndex = NO_CAROUSEL_TARGET, currentPage = 0))
    }

    // ── seedSelectionAction ─────────────────────────────────────────────────────

    @Test
    fun `no rows - nothing to seed or heal`() {
        assertEquals(SeedAction.NONE, seedSelectionAction(rowCount = 0, selectedId = null, selectedIndex = NO_CAROUSEL_TARGET))
        assertEquals(SeedAction.NONE, seedSelectionAction(rowCount = 0, selectedId = "stale", selectedIndex = NO_CAROUSEL_TARGET))
    }

    @Test
    fun `nothing selected with rows present seeds the first card`() {
        // Document open/new: highlight must appear immediately, not only after a swipe.
        // First card = AFT-most component in physical order (product decision).
        assertEquals(SeedAction.SEED_FIRST, seedSelectionAction(rowCount = 3, selectedId = null, selectedIndex = NO_CAROUSEL_TARGET))
    }

    @Test
    fun `orphaned selection adopts the current page`() {
        // Auto-body ids regenerate on every edit; the highlight must come back without
        // yanking the user off the card they are editing.
        assertEquals(
            SeedAction.ADOPT_CURRENT,
            seedSelectionAction(rowCount = 3, selectedId = "auto_body_0.000_100.000", selectedIndex = NO_CAROUSEL_TARGET)
        )
    }

    @Test
    fun `live selection is left alone`() {
        assertEquals(SeedAction.NONE, seedSelectionAction(rowCount = 3, selectedId = "taper-1", selectedIndex = 1))
    }

    @Test
    fun `with an orphaned selection any scroll is the user`() {
        // The selected id no longer resolves to any row (auto-body ids regenerate on every
        // edit; opens carry stale ids). The follow effect never animates toward a missing
        // row, so this scroll cannot be catch-up — treating it as ours bricked swipe
        // adoption (and the preview highlight) until a preview tap set a valid id.
        assertTrue(
            isUserInitiatedScroll(selectedId = "auto_body_0.000_100.000", selectedIndex = NO_CAROUSEL_TARGET, currentPage = 1)
        )
    }

    // ── shouldAdoptSwipeSelection ────────────────────────────────────────────────

    @Test
    fun `a swipe that lands elsewhere changes the selection`() {
        assertTrue(shouldAdoptSwipeSelection(startPage = 1, endPage = 2))
    }

    @Test
    fun `a swipe that springs back does not change the selection`() {
        assertFalse(shouldAdoptSwipeSelection(startPage = 1, endPage = 1))
    }

    @Test
    fun `a scroll with no recorded start page does not change the selection`() {
        // Defensive: start page and the user-initiated flag are always set together, so
        // this should be unreachable. Pinned so that if they ever come apart, the failure
        // is a missed selection update rather than a selection jumping to page 0.
        assertFalse(shouldAdoptSwipeSelection(startPage = null, endPage = 0))
    }

    // ── end-to-end: the reported on-device behavior ──────────────────────────────

    @Test
    fun `preview tap on an offscreen component scrolls the carousel to it`() {
        // Carousel settled on body-1 (page 0); user taps body-2 in the preview.
        val target = carouselTargetIndex(rows, "body-2")
        assertEquals(3, target)
        assertTrue(shouldAnimateToSelection(target, currentPage = 0, currentPageOffsetFraction = 0f))
    }

    @Test
    fun `preview tap on the already-shown component is a no-op`() {
        val target = carouselTargetIndex(rows, "body-1")
        assertFalse(shouldAnimateToSelection(target, currentPage = 0, currentPageOffsetFraction = 0f))
    }
}
