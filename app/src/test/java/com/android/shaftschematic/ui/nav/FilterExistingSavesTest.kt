package com.android.shaftschematic.ui.nav

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The Save Drawing screen's existing-saves list. The File name field opens pre-filled with a
 * default (`Shaft_20261028_1401`) that matches no save, so filtering by it hid every save under an
 * "Existing saves" heading (emulator QA).
 */
class FilterExistingSavesTest {

    private val saves = listOf("QA_Titan_rebuild", "Pac Fish STBD", "titan_port")
    private val default = "Shaft_20261028_1401"

    @Test
    fun `the untouched default name lists every save`() {
        assertEquals(saves, filterExistingSaves(saves, default, default))
        assertEquals(saves, filterExistingSaves(saves, "  $default ", default))
    }

    @Test
    fun `a typed name filters, ignoring case`() {
        assertEquals(listOf("QA_Titan_rebuild", "titan_port"), filterExistingSaves(saves, "TITAN", default))
    }

    @Test
    fun `a blank field lists every save`() {
        assertEquals(saves, filterExistingSaves(saves, "", default))
    }

    /** An empty result is what hides the heading (the screen shows it only over a non-empty list). */
    @Test
    fun `a name that matches nothing gives an empty list`() {
        assertEquals(emptyList<String>(), filterExistingSaves(saves, "zzz", default))
    }
}
