package com.android.shaftschematic.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The one join behind every exported PDF's name. What matters is the ORDER: the drawing's
 * identity sits next to the base, and the blank-draft marker always trails it — a write-in
 * copy of the final drawing must read as the final drawing first, or a folder of exports
 * sorts the two drawings of one job apart.
 */
class ExportFilenameTest {

    @Test
    fun `no suffixes is the base plus the extension`() {
        assertEquals("Job12_Acme.pdf", exportPdfFilename("Job12_Acme"))
    }

    @Test
    fun `the drawing suffix names which drawing this is`() {
        // The final drawing produces three distinct documents, and each has to land under its
        // own name or one export silently replaces another: the machining copy, the same
        // schematic with runout stations elected on, and the blank classic runout sheet.
        assertEquals("Job12_Acme_Final.pdf", exportPdfFilename("Job12_Acme", "_Final"))
        assertEquals(
            "Job12_Acme_Final_Runout.pdf",
            exportPdfFilename("Job12_Acme", "_Final_Runout"),
        )
        assertEquals(
            "Job12_Acme_Final_RunoutSheet.pdf",
            exportPdfFilename("Job12_Acme", "_Final_RunoutSheet"),
        )
    }

    @Test
    fun `the blank-draft marker trails the drawing suffix`() {
        assertEquals(
            "Job12_Acme_Final_BlankDraft.pdf",
            exportPdfFilename("Job12_Acme", "_Final", blankDraft = true),
        )
        assertEquals(
            "Job12_Acme_BlankDraft.pdf",
            exportPdfFilename("Job12_Acme", blankDraft = true),
        )
    }
}
