package com.android.shaftschematic.pdf

import com.android.shaftschematic.model.ProjectInfo
import com.android.shaftschematic.model.ShaftPosition
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.util.DisplayUnits
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The optional "Item" project field (shaft designation) on printed sheets.
 *
 * Two rules, and they are the whole feature:
 *  - a **printed** sheet omits the line entirely when Item is blank — a label with nothing
 *    after it is an orphan on a finished drawing, and every other job-info field is
 *    unconditional precisely because it is not optional;
 *  - a **blank draft** always rules a line for it, because the draft may be filled in on a
 *    different shaft than the one that produced it.
 *
 * Both header sites (wear/undercut and the classic runout sheet) build the same text from
 * [jobInfoHeaderLine] / [JOB_INFO_BLANK_LABELS], so testing the pair covers both.
 */
class ItemFieldPrintTest {

    private val spec = ShaftSpec(overallLengthMm = 2000f)
    private val cfg = FooterConfig(
        showAftThread = false, showFwdThread = false,
        showAftTaper = false, showFwdTaper = false,
    )
    private val date = "2026-01-02"

    private fun project(item: String) = ProjectInfo(
        customer = "NorthSound Marine",
        vessel = "FV Tern Point",
        side = ShaftPosition.PORT,
        jobNumber = "814201",
        item = item,
    )

    private fun midColumn(item: String, blank: Boolean = false): List<String> =
        buildFooterMidColumn(spec, project(item), cfg, date, blank, DisplayUnits.single(UnitSystem.INCHES))

    // ── Footer ───────────────────────────────────────────────────────────────

    @Test
    fun `a set item prints one footer line, after Job number and before Date`() {
        val lines = midColumn("Tail shaft")

        assertTrue(lines.contains("Item: Tail shaft"))
        assertTrue(
            "Item keeps its place in the identity block",
            lines.indexOf("Job #: 814201") < lines.indexOf("Item: Tail shaft"),
        )
        assertTrue(lines.indexOf("Item: Tail shaft") < lines.indexOf("Date: $date"))
    }

    @Test
    fun `a blank item prints no footer line at all`() {
        val lines = midColumn("")

        assertFalse(lines.any { it.startsWith("Item") })
    }

    @Test
    fun `a blank item costs a printed footer nothing`() {
        // Line-for-line what the column held before the field existed — the whole point of an
        // all-defaulted additive field is that an untouched document prints unchanged.
        assertEquals(
            listOf(
                "Customer: NorthSound Marine",
                "Vessel: FV Tern Point",
                "Job #: 814201",
                "Date: $date",
            ),
            midColumn(""),
        )
        assertEquals(midColumn("").size + 1, midColumn("Tail shaft").size)
    }

    @Test
    fun `a blank draft rules an Item line whatever the document holds`() {
        listOf("", "Tail shaft").forEach { item ->
            val lines = midColumn(item, blank = true)
            assertTrue("blank draft always offers the Item rule", lines.contains("Item:"))
            assertTrue(lines.indexOf("Job #:") < lines.indexOf("Item:"))
            assertTrue(lines.indexOf("Item:") < lines.indexOf("Date:"))
        }
    }

    // ── Headers ──────────────────────────────────────────────────────────────

    @Test
    fun `a set item prints in the sheet header line`() {
        val line = jobInfoHeaderLine(project("Tail shaft"), date)

        assertTrue(line.contains("Item: Tail shaft"))
        assertTrue(line.indexOf("Job #:") < line.indexOf("Item:"))
        assertTrue(line.indexOf("Item:") < line.indexOf("Date:"))
    }

    @Test
    fun `a blank item leaves the sheet header line as it was`() {
        val line = jobInfoHeaderLine(project(""), date)

        assertFalse(line.contains("Item"))
        assertEquals(
            "Customer: NorthSound Marine   Vessel: FV Tern Point   Job #: 814201   Date: $date  PORT",
            line,
        )
    }

    @Test
    fun `the blank header labels rule a line for Item`() {
        assertTrue(JOB_INFO_BLANK_LABELS.contains("Item:"))
        assertTrue(
            JOB_INFO_BLANK_LABELS.indexOf("Job #:") < JOB_INFO_BLANK_LABELS.indexOf("Item:"),
        )
        assertTrue(
            JOB_INFO_BLANK_LABELS.indexOf("Item:") < JOB_INFO_BLANK_LABELS.indexOf("Date:"),
        )
    }
}
