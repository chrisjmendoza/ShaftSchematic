package com.android.shaftschematic.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [OutputDoc] filename shape: `{customer_vessel_job | fallback}_{suffix}[_BlankDraft].pdf` —
 * the same shape every document's own tab has always produced. See `OutputDoc.kt`.
 */
class OutputDocTest {

    @Test
    fun `each doc produces its own filename suffix`() {
        assertEquals(
            "A_B_C_consolidated.pdf",
            buildOutputFilename("A", "B", "C", OutputDoc.CONSOLIDATED),
        )
        assertEquals(
            "A_B_C_runout.pdf",
            buildOutputFilename("A", "B", "C", OutputDoc.RUNOUT),
        )
        assertEquals(
            "A_B_C_schematic.pdf",
            buildOutputFilename("A", "B", "C", OutputDoc.SCHEMATIC),
        )
        assertEquals(
            "A_B_C_wear.pdf",
            buildOutputFilename("A", "B", "C", OutputDoc.WEAR),
        )
        assertEquals(
            "A_B_C_undercuts.pdf",
            buildOutputFilename("A", "B", "C", OutputDoc.UNDERCUT),
        )
    }

    @Test
    fun `all-blank customer vessel job falls back to each doc's own base name`() {
        assertEquals(
            "ConsolidatedSheet_consolidated.pdf",
            buildOutputFilename("", "", "", OutputDoc.CONSOLIDATED),
        )
        assertEquals(
            "RunoutSheet_runout.pdf",
            buildOutputFilename("", "", "", OutputDoc.RUNOUT),
        )
        assertEquals(
            "Schematic_schematic.pdf",
            buildOutputFilename("", "", "", OutputDoc.SCHEMATIC),
        )
        assertEquals(
            "WearDocument_wear.pdf",
            buildOutputFilename("", "", "", OutputDoc.WEAR),
        )
        assertEquals(
            "Undercuts_undercuts.pdf",
            buildOutputFilename("", "", "", OutputDoc.UNDERCUT),
        )
    }

    @Test
    fun `blank parts are skipped in the join`() {
        assertEquals(
            "Acme_runout.pdf",
            buildOutputFilename("Acme", "", "", OutputDoc.RUNOUT),
        )
        assertEquals(
            "Acme_Tidewater_wear.pdf",
            buildOutputFilename("Acme", "Tidewater", "", OutputDoc.WEAR),
        )
    }

    @Test
    fun `blankDraft appends _BlankDraft before the extension`() {
        assertEquals(
            "A_B_C_consolidated_BlankDraft.pdf",
            buildOutputFilename("A", "B", "C", OutputDoc.CONSOLIDATED, blankDraft = true),
        )
        assertEquals(
            "RunoutSheet_runout_BlankDraft.pdf",
            buildOutputFilename("", "", "", OutputDoc.RUNOUT, blankDraft = true),
        )
    }

    @Test
    fun `CONSOLIDATED is the first entry - the picker's default selection`() {
        assertEquals(OutputDoc.CONSOLIDATED, OutputDoc.entries.first())
    }

    @Test
    fun `consolidated variants - ALL is the default and the flags match the labels`() {
        assertEquals(ConsolidatedVariant.ALL, ConsolidatedVariant.entries.first())
        assertEquals(true, ConsolidatedVariant.ALL.includeBubbles)
        assertEquals(true, ConsolidatedVariant.ALL.includeWearInfo)
        assertEquals(true, ConsolidatedVariant.SCHEMATIC_RUNOUT.includeBubbles)
        assertEquals(false, ConsolidatedVariant.SCHEMATIC_RUNOUT.includeWearInfo)
        assertEquals(false, ConsolidatedVariant.SCHEMATIC_WEAR.includeBubbles)
        assertEquals(true, ConsolidatedVariant.SCHEMATIC_WEAR.includeWearInfo)
    }
}
