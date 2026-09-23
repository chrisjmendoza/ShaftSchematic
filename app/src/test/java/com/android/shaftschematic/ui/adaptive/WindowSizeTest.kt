package com.android.shaftschematic.ui.adaptive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowSizeTest {

    @Test
    fun `phone widths are compact`() {
        assertEquals(WindowWidthClass.COMPACT, windowWidthClassFor(0))
        assertEquals(WindowWidthClass.COMPACT, windowWidthClassFor(360))
        assertEquals(WindowWidthClass.COMPACT, windowWidthClassFor(411))
        assertEquals(WindowWidthClass.COMPACT, windowWidthClassFor(WINDOW_WIDTH_MEDIUM_DP - 1))
    }

    @Test
    fun `small tablet and landscape phone widths are medium`() {
        assertEquals(WindowWidthClass.MEDIUM, windowWidthClassFor(WINDOW_WIDTH_MEDIUM_DP))
        assertEquals(WindowWidthClass.MEDIUM, windowWidthClassFor(800))
        assertEquals(WindowWidthClass.MEDIUM, windowWidthClassFor(WINDOW_WIDTH_EXPANDED_DP - 1))
    }

    @Test
    fun `tablet landscape widths are expanded`() {
        assertEquals(WindowWidthClass.EXPANDED, windowWidthClassFor(WINDOW_WIDTH_EXPANDED_DP))
        assertEquals(WindowWidthClass.EXPANDED, windowWidthClassFor(960))
        assertEquals(WindowWidthClass.EXPANDED, windowWidthClassFor(1280))
    }

    @Test
    fun `only expanded lays out two panes`() {
        assertFalse(WindowWidthClass.COMPACT.twoPane)
        assertFalse(WindowWidthClass.MEDIUM.twoPane)
        assertTrue(WindowWidthClass.EXPANDED.twoPane)
    }
}
