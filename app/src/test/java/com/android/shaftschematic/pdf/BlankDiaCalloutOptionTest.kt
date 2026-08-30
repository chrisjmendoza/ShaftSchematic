package com.android.shaftschematic.pdf

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The blank-draft Ø-callout election. One rule lives on [PdfExportOptions] so the four
 * call sites that build export options can never disagree about when the callout pass runs.
 */
class BlankDiaCalloutOptionTest {

    @Test
    fun `a printed sheet always draws its callouts`() {
        assertTrue(PdfExportOptions().showDiaCallouts)
        // The blank-mode preference has no say outside blank mode.
        assertTrue(PdfExportOptions(blankDiaCallouts = false).showDiaCallouts)
    }

    @Test
    fun `a blank draft draws callouts by default`() {
        assertTrue(PdfExportOptions(blankValues = true).showDiaCallouts)
    }

    @Test
    fun `a blank draft with callouts elected out skips the pass`() {
        assertFalse(
            PdfExportOptions(blankValues = true, blankDiaCallouts = false).showDiaCallouts
        )
    }
}
