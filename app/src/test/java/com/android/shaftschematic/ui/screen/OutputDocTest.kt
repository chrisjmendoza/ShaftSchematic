package com.android.shaftschematic.ui.screen

import com.android.shaftschematic.model.ShaftPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * [OutputDoc] filename shape: `{<DocumentNaming base> | fallback}_{suffix}[_BlankDraft].pdf`.
 * The base is the ONE the document is named by — `Job - Customer - Vessel - SIDE`, every part
 * sanitized — so the schematic exported from its own tab and from Export all share a name, and
 * a `/` in a customer name never reaches a filename (a second, raw `_`-joined scheme once
 * lived here). See `OutputDoc.kt`.
 */
class OutputDocTest {

    private val noSide = ShaftPosition.OTHER

    @Test
    fun `each doc produces its own filename suffix`() {
        assertEquals(
            "C - A - B_consolidated.pdf",
            buildOutputFilename("A", "B", "C", noSide, OutputDoc.CONSOLIDATED),
        )
        assertEquals(
            "C - A - B_runout.pdf",
            buildOutputFilename("A", "B", "C", noSide, OutputDoc.RUNOUT),
        )
        assertEquals(
            "C - A - B_schematic.pdf",
            buildOutputFilename("A", "B", "C", noSide, OutputDoc.SCHEMATIC),
        )
        assertEquals(
            "C - A - B_wear.pdf",
            buildOutputFilename("A", "B", "C", noSide, OutputDoc.WEAR),
        )
        assertEquals(
            "C - A - B_undercuts.pdf",
            buildOutputFilename("A", "B", "C", noSide, OutputDoc.UNDERCUT),
        )
    }

    @Test
    fun `the base is DocumentNaming's - job first then customer then vessel`() {
        // The same order and separators the saved document itself is named with.
        assertEquals(
            "J-1 - Acme - Tidewater_schematic.pdf",
            buildOutputFilename("Acme", "Tidewater", "J-1", noSide, OutputDoc.SCHEMATIC),
        )
    }

    @Test
    fun `every part is sanitized before it reaches the filename`() {
        // The reported case: a slash, colon or star in a customer name went straight into the name.
        val name = buildOutputFilename("Acme/Marine: *", "M/V Tide?", "J:1", noSide, OutputDoc.RUNOUT)
        assertEquals("J_1 - Acme_Marine_ _ - M_V Tide__runout.pdf", name)
        assertFalse(name.any { it in "\\/:*?\"<>|" })
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
            "Acme - Tidewater_wear.pdf",
            buildOutputFilename("Acme", "Tidewater", "", noSide, OutputDoc.WEAR),
        )
    }

    @Test
    fun `blankDraft appends _BlankDraft before the extension`() {
        assertEquals(
            "C - A - B_consolidated_BlankDraft.pdf",
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
            "J-1 - Acme - Tidewater - PORT_runout.pdf",
            buildOutputFilename("Acme", "Tidewater", "J-1", ShaftPosition.PORT, OutputDoc.RUNOUT),
        )
        assertEquals(
            "J-1 - Acme - Tidewater - STBD_runout.pdf",
            buildOutputFilename("Acme", "Tidewater", "J-1", ShaftPosition.STBD, OutputDoc.RUNOUT),
        )
        assertEquals(
            "J-1 - Acme - Tidewater - CENTER_wear.pdf",
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
            "C - A - B_undercuts.pdf",
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
            "C - A - B - STBD_schematic_BlankDraft.pdf",
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
