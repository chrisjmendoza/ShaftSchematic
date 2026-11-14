package com.android.shaftschematic.pdf

import com.android.shaftschematic.model.ProjectInfo
import com.android.shaftschematic.model.ShaftPosition
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.SheetDateMode
import com.android.shaftschematic.model.printedDateOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sheet date on the printed header line and the footer job block. A null date (the
 * document's "None") leaves the "Date:" piece off entirely — no bare label and no stray
 * separator that would shift the centred header — while a blank draft still rules a "Date:"
 * line to be filled in by hand. Both header sites build their text from [jobInfoHeaderLine].
 */
class SheetDatePrintTest {

    private val spec = ShaftSpec(overallLengthMm = 2000f)
    private val cfg = FooterConfig(
        showAftThread = false, showFwdThread = false,
        showAftTaper = false, showFwdTaper = false,
    )

    private fun project(
        side: ShaftPosition = ShaftPosition.PORT,
        drawingLabel: String = "",
        mode: SheetDateMode = SheetDateMode.TODAY,
        text: String = "",
    ) = ProjectInfo(
        customer = "NorthSound Marine",
        vessel = "FV Tern Point",
        side = side,
        jobNumber = "814201",
        item = "Tail shaft",
        drawingLabel = drawingLabel,
        dateMode = mode,
        dateText = text,
    )

    // ── Header line ──────────────────────────────────────────────────────────

    @Test
    fun `a null date omits Date cleanly from the header line`() {
        val line = jobInfoHeaderLine(project(), null)

        assertFalse(line.contains("Date"))
        assertEquals(
            "Customer: NorthSound Marine   Vessel: FV Tern Point   Job #: 814201   Item: Tail shaft  PORT",
            line,
        )
        assertEquals("no leading or trailing space to shift the centred line", line.trim(), line)
    }

    @Test
    fun `a null date with no side and a drawing label leaves no stray spaces`() {
        val line = jobInfoHeaderLine(project(side = ShaftPosition.OTHER, drawingLabel = "Final"), null)

        assertEquals(
            "Customer: NorthSound Marine   Vessel: FV Tern Point   Job #: 814201   Item: Tail shaft  Drawing: Final",
            line,
        )
    }

    @Test
    fun `a null date on an otherwise empty job prints just the side`() {
        assertEquals("PORT", jobInfoHeaderLine(ProjectInfo(side = ShaftPosition.PORT), null))
        assertEquals("", jobInfoHeaderLine(ProjectInfo(), null))
    }

    @Test
    fun `a custom date prints verbatim where today's date would`() {
        val p = project(mode = SheetDateMode.CUSTOM, text = "11/6/2026")
        val line = jobInfoHeaderLine(p, p.printedDateOrNull("2026-11-13"))

        assertEquals(
            "Customer: NorthSound Marine   Vessel: FV Tern Point   Job #: 814201   Item: Tail shaft   Date: 11/6/2026  PORT",
            line,
        )
    }

    @Test
    fun `the bold-side header variant omits a null date too`() {
        val (line, bold) = sheetHeaderJobLineAndBoldSide(project(), null, boldSideOnTitle = true)

        assertEquals("PORT", bold)
        assertEquals(
            "Customer: NorthSound Marine   Vessel: FV Tern Point   Job #: 814201   Item: Tail shaft",
            line,
        )
    }

    @Test
    fun `the blank header labels still rule a Date line`() {
        assertTrue(JOB_INFO_BLANK_LABELS.contains("Date:"))
    }

    // ── Footer job block ─────────────────────────────────────────────────────

    @Test
    fun `a null date omits the footer Date line`() {
        val lines = buildFooterMidColumn(spec, project(), cfg, null, blankValues = false)

        assertFalse(lines.any { it.startsWith("Date") })
        assertEquals(
            listOf("Customer: NorthSound Marine", "Vessel: FV Tern Point", "Job #: 814201", "Item: Tail shaft"),
            lines,
        )
    }

    @Test
    fun `a custom date prints verbatim on the footer`() {
        val p = project(mode = SheetDateMode.CUSTOM, text = "11/6/2026")
        val lines = buildFooterMidColumn(spec, p, cfg, p.printedDateOrNull("2026-11-13"), blankValues = false)

        assertTrue(lines.contains("Date: 11/6/2026"))
    }

    @Test
    fun `a blank draft rules a Date line whatever the mode`() {
        val lines = buildFooterMidColumn(spec, project(mode = SheetDateMode.NONE), cfg, null, blankValues = true)

        assertTrue(lines.contains("Date:"))
    }
}
