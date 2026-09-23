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
 * `ProjectInfo.drawingLabel` — the "Final" marker that keeps a final-schematic sheet from being
 * mistaken for the original. Blank (every existing caller) must print nothing anywhere; a set
 * label prints a "Drawing: <label>" line in the footer job block, after Item and before Date,
 * and in the sheet header's job-info line, after the date/side segment.
 *
 * Both header sites (wear/undercut and the classic runout sheet) build their non-blank line from
 * the shared [jobInfoHeaderLine], so testing it covers both — same posture as
 * [ItemFieldPrintTest].
 */
class DrawingLabelPrintTest {

    private val spec = ShaftSpec(overallLengthMm = 2000f)
    private val cfg = FooterConfig(
        showAftThread = false, showFwdThread = false,
        showAftTaper = false, showFwdTaper = false,
    )
    private val date = "2026-01-02"

    private fun project(drawingLabel: String, item: String = "") = ProjectInfo(
        customer = "NorthSound Marine",
        vessel = "FV Tern Point",
        side = ShaftPosition.PORT,
        jobNumber = "814201",
        item = item,
        drawingLabel = drawingLabel,
    )

    private fun midColumn(drawingLabel: String, item: String = "", blank: Boolean = false): List<String> =
        buildFooterMidColumn(spec, project(drawingLabel, item), cfg, date, blank, DisplayUnits.single(UnitSystem.INCHES))

    // ── Footer, printed branch ──────────────────────────────────────────────

    @Test
    fun `a blank drawing label prints no footer line and costs the column nothing`() {
        val lines = midColumn(drawingLabel = "")

        assertFalse(lines.any { it.startsWith("Drawing") })
        // Line-for-line what the column held before the field existed.
        assertEquals(
            listOf(
                "Customer: NorthSound Marine",
                "Vessel: FV Tern Point",
                "Job #: 814201",
                "Date: $date",
            ),
            lines,
        )
    }

    @Test
    fun `a set drawing label prints one footer line, after Item and before Date`() {
        val lines = midColumn(drawingLabel = "Final", item = "Tail shaft")

        assertTrue(lines.contains("Drawing: Final"))
        assertTrue(
            "Drawing sits after Item",
            lines.indexOf("Item: Tail shaft") < lines.indexOf("Drawing: Final"),
        )
        assertTrue(
            "Drawing sits before Date",
            lines.indexOf("Drawing: Final") < lines.indexOf("Date: $date"),
        )
    }

    @Test
    fun `a set drawing label still prints after Job number even with no Item`() {
        val lines = midColumn(drawingLabel = "Final")

        assertTrue(lines.contains("Drawing: Final"))
        assertTrue(lines.indexOf("Job #: 814201") < lines.indexOf("Drawing: Final"))
        assertTrue(lines.indexOf("Drawing: Final") < lines.indexOf("Date: $date"))
    }

    // ── Footer, blank-draft branch ──────────────────────────────────────────

    @Test
    fun `a blank draft of the original drawing rules no Drawing line`() {
        val lines = midColumn(drawingLabel = "", blank = true)

        assertFalse(
            "a blank draft with no drawing label must not grow a line",
            lines.any { it.startsWith("Drawing") },
        )
    }

    @Test
    fun `a blank draft of the final drawing rules a Drawing line, after Item and before Date`() {
        val lines = midColumn(drawingLabel = "Final", blank = true)

        assertTrue(lines.contains("Drawing:"))
        assertTrue(lines.indexOf("Item:") < lines.indexOf("Drawing:"))
        assertTrue(lines.indexOf("Drawing:") < lines.indexOf("Date:"))
    }

    // ── Header ───────────────────────────────────────────────────────────────

    @Test
    fun `a set drawing label prints in the sheet header line, after the date and side`() {
        val line = jobInfoHeaderLine(project(drawingLabel = "Final"), date)

        assertTrue(line.contains("Drawing: Final"))
        assertTrue(line.indexOf("Date: $date") < line.indexOf("Drawing: Final"))
        assertTrue(
            "the side badge stays ahead of the drawing label",
            line.indexOf("PORT") < line.indexOf("Drawing: Final"),
        )
    }

    @Test
    fun `a blank drawing label leaves the sheet header line exactly as it was`() {
        val line = jobInfoHeaderLine(project(drawingLabel = ""), date)

        assertFalse(line.contains("Drawing"))
        assertEquals(
            "Customer: NorthSound Marine   Vessel: FV Tern Point   Job #: 814201   Date: $date  PORT",
            line,
        )
    }
}
