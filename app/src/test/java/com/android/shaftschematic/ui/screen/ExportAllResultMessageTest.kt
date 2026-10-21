package com.android.shaftschematic.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The Output tab's "Export all" result line: an undercut drawing with every section left off is
 * skipped rather than written as its "no sections selected" page, and the line says so.
 */
class ExportAllResultMessageTest {

    @Test
    fun `a clean batch reads as before`() {
        assertEquals("5 of 5 PDFs written.", exportAllResultMessage(5, 5, undercutSkipped = false))
    }

    @Test
    fun `a skipped undercut drawing is named`() {
        assertEquals(
            "4 of 4 PDFs written. Undercut drawing skipped: no sections selected.",
            exportAllResultMessage(4, 4, undercutSkipped = true),
        )
    }

    @Test
    fun `failures and a skip both report`() {
        assertEquals(
            "3 of 4 written — 1 failed (see the error page inside each failed file). " +
                "Undercut drawing skipped: no sections selected.",
            exportAllResultMessage(3, 4, undercutSkipped = true),
        )
    }
}
