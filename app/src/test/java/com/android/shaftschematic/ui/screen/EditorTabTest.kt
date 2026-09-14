package com.android.shaftschematic.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [EditorTab]'s declaration order IS the sidebar's order, so where a tab sits in the enum is
 * a product decision rather than a formatting one. The Final Schematic follows the Undercut
 * Drawing because the final drawing is decided once the wear and undercut work is known, and
 * it comes before Consolidated Output because that sheet is the last thing produced — moving
 * it silently reorders the shop's navigation.
 */
class EditorTabTest {

    @Test
    fun `Final sits between Undercut and Output`() {
        val order = EditorTab.entries.toList()

        assertEquals(
            "Final Schematic follows the Undercut Drawing",
            order.indexOf(EditorTab.UNDERCUT) + 1,
            order.indexOf(EditorTab.FINAL),
        )
        assertEquals(
            "Consolidated Output stays last",
            order.indexOf(EditorTab.FINAL) + 1,
            order.indexOf(EditorTab.OUTPUT),
        )
        assertEquals("Consolidated Output is the last tab", order.size - 1, order.indexOf(EditorTab.OUTPUT))
    }

    @Test
    fun `Final carries the tab label and its accessibility description`() {
        assertEquals("Final Schematic", EditorTab.FINAL.label)
        assertEquals("Final schematic editor", EditorTab.FINAL.contentDescription)
    }
}
