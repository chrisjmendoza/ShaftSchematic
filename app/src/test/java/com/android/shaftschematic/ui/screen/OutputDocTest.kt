package com.android.shaftschematic.ui.screen

import com.android.shaftschematic.model.ShaftPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * [OutputDoc] filename shape: `{customer_vessel_job[_SIDE] | fallback}_{suffix}
 * [_BlankDraft].pdf` — the same shape every document's own tab has always produced. See
 * `OutputDoc.kt`.
 */
class OutputDocTest {

    private val noSide = ShaftPosition.OTHER

    @Test
    fun `each doc produces its own filename suffix`() {
        assertEquals(
            "A_B_C_consolidated.pdf",
            buildOutputFilename("A", "B", "C", noSide, OutputDoc.CONSOLIDATED),
        )
        assertEquals(
            "A_B_C_runout.pdf",
            buildOutputFilename("A", "B", "C", noSide, OutputDoc.RUNOUT),
        )
        assertEquals(
            "A_B_C_schematic.pdf",
            buildOutputFilename("A", "B", "C", noSide, OutputDoc.SCHEMATIC),
        )
        assertEquals(
            "A_B_C_wear.pdf",
            buildOutputFilename("A", "B", "C", noSide, OutputDoc.WEAR),
        )
        assertEquals(
            "A_B_C_undercuts.pdf",
            buildOutputFilename("A", "B", "C", noSide, OutputDoc.UNDERCUT),
        )
    }

    @Test
    fun `all-blank customer vessel job falls back to each doc's own base name`() {
        assertEquals(
            "ConsolidatedSheet_consolidated.pdf",
            buildOutputFilename("", "", "", noSide, OutputDoc.CONSOLIDATED),
        )
        assertEquals(
            "RunoutSheet_runout.pdf",
            buildOutputFilename("", "", "", noSide, OutputDoc.RUNOUT),
        )
        assertEquals(
            "Schematic_schematic.pdf",
            buildOutputFilename("", "", "", noSide, OutputDoc.SCHEMATIC),
        )
        assertEquals(
            "WearDocument_wear.pdf",
            buildOutputFilename("", "", "", noSide, OutputDoc.WEAR),
        )
        assertEquals(
            "Undercuts_undercuts.pdf",
            buildOutputFilename("", "", "", noSide, OutputDoc.UNDERCUT),
        )
    }

    @Test
    fun `blank parts are skipped in the join`() {
        assertEquals(
            "Acme_runout.pdf",
            buildOutputFilename("Acme", "", "", noSide, OutputDoc.RUNOUT),
        )
        assertEquals(
            "Acme_Tidewater_wear.pdf",
            buildOutputFilename("Acme", "Tidewater", "", noSide, OutputDoc.WEAR),
        )
    }

    @Test
    fun `blankDraft appends _BlankDraft before the extension`() {
        assertEquals(
            "A_B_C_consolidated_BlankDraft.pdf",
            buildOutputFilename("A", "B", "C", noSide, OutputDoc.CONSOLIDATED, blankDraft = true),
        )
        assertEquals(
            "RunoutSheet_runout_BlankDraft.pdf",
            buildOutputFilename("", "", "", noSide, OutputDoc.RUNOUT, blankDraft = true),
        )
    }

    /* ── Shaft side: two mates on one job must not propose one filename ── */

    @Test
    fun `a printable side lands last in the base`() {
        assertEquals(
            "Acme_Tidewater_J-1_PORT_runout.pdf",
            buildOutputFilename("Acme", "Tidewater", "J-1", ShaftPosition.PORT, OutputDoc.RUNOUT),
        )
        assertEquals(
            "Acme_Tidewater_J-1_STBD_runout.pdf",
            buildOutputFilename("Acme", "Tidewater", "J-1", ShaftPosition.STBD, OutputDoc.RUNOUT),
        )
        assertEquals(
            "Acme_Tidewater_J-1_CENTER_wear.pdf",
            buildOutputFilename("Acme", "Tidewater", "J-1", ShaftPosition.CENTER, OutputDoc.WEAR),
        )
    }

    @Test
    fun `mates on one job get different filenames for every document`() {
        OutputDoc.entries.forEach { doc ->
            assertNotEquals(
                "mates collide on ${doc.label}",
                buildOutputFilename("Acme", "Tidewater", "J-1", ShaftPosition.PORT, doc),
                buildOutputFilename("Acme", "Tidewater", "J-1", ShaftPosition.STBD, doc),
            )
        }
    }

    @Test
    fun `a side that prints nothing leaves the name exactly as it was`() {
        assertEquals(
            "A_B_C_undercuts.pdf",
            buildOutputFilename("A", "B", "C", ShaftPosition.OTHER, OutputDoc.UNDERCUT),
        )
    }

    @Test
    fun `a side alone is never a filename`() {
        // Matches the schematic's rule: with no job information, the fallback base wins and
        // the side is dropped rather than standing in as the whole name.
        assertEquals(
            "WearDocument_wear.pdf",
            buildOutputFilename("", "", "", ShaftPosition.PORT, OutputDoc.WEAR),
        )
    }

    @Test
    fun `side and blank draft compose`() {
        assertEquals(
            "A_B_C_STBD_schematic_BlankDraft.pdf",
            buildOutputFilename(
                "A", "B", "C", ShaftPosition.STBD, OutputDoc.SCHEMATIC, blankDraft = true,
            ),
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
