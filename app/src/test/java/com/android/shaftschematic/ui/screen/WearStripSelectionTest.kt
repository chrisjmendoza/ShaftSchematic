package com.android.shaftschematic.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the wear-strip election toggle (`toggleWearStripSelection`, `WearRoute.kt`) — the pure
 * seam behind the "Components" section's checkboxes. That section now has TWO hosts (the Wear
 * tab body and the preview's PDF options sheet), so the toggle rules live in one tested
 * function rather than in a lambda each surface could drift on.
 */
class WearStripSelectionTest {

    private val offered = listOf("aft_liner", "mid_taper", "fwd_liner")

    @Test
    fun `ticking a row adds it in offered order, not tap order`() {
        val next = toggleWearStripSelection(
            offered = offered, base = listOf("fwd_liner"), id = "aft_liner", checked = true,
        )
        assertEquals(listOf("aft_liner", "fwd_liner"), next)
    }

    @Test
    fun `clearing a row drops only that row`() {
        val next = toggleWearStripSelection(
            offered = offered, base = offered, id = "mid_taper", checked = false,
        )
        assertEquals(listOf("aft_liner", "fwd_liner"), next)
    }

    @Test
    fun `the first tick materializes the default election it was showing`() {
        // An un-authored sheet shows `defaultIds` (every drawable liner). Ticking the taper must
        // author exactly what was on screen plus the taper — never just the taper.
        val next = toggleWearStripSelection(
            offered = offered,
            base = listOf("aft_liner", "fwd_liner"),
            id = "mid_taper",
            checked = true,
        )
        assertEquals(listOf("aft_liner", "mid_taper", "fwd_liner"), next)
    }

    @Test
    fun `an elected id this geometry no longer offers is kept, never pruned`() {
        // Render-layer orphan rule: the id has no row here, but the component may come back
        // (undo, a reloaded template), and it must still be elected when it does.
        val next = toggleWearStripSelection(
            offered = offered,
            base = listOf("aft_liner", "ghost"),
            id = "fwd_liner",
            checked = true,
        )
        assertEquals(listOf("aft_liner", "fwd_liner", "ghost"), next)
    }

    @Test
    fun `clearing a row leaves unoffered elected ids alone`() {
        val next = toggleWearStripSelection(
            offered = offered,
            base = listOf("aft_liner", "ghost"),
            id = "aft_liner",
            checked = false,
        )
        assertEquals(listOf("ghost"), next)
    }

    @Test
    fun `re-ticking an already elected row is a no-op`() {
        val next = toggleWearStripSelection(
            offered = offered, base = offered, id = "mid_taper", checked = true,
        )
        assertEquals(offered, next)
    }
}
