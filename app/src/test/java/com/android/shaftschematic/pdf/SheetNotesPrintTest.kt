package com.android.shaftschematic.pdf

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ProjectInfo
import com.android.shaftschematic.model.ShaftPosition
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.SheetDateSheet
import com.android.shaftschematic.model.forSheet
import com.android.shaftschematic.model.sheetPrintsNotes
import com.android.shaftschematic.settings.RunoutConfig
import com.android.shaftschematic.ui.resolved.resolveComponents
import com.android.shaftschematic.util.PDF_PAGE_HEIGHT_PT
import com.android.shaftschematic.util.PDF_PAGE_WIDTH_PT
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * "Notes on this sheet": the job's notes print under the footer's job block on a footer sheet whose
 * switch is on — never by default, never on a blank draft, never on a sheet without the footer.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SheetNotesPrintTest {

    private val inch = 25.4f
    private val pageW = PDF_PAGE_WIDTH_PT.toFloat()
    private val pageH = PDF_PAGE_HEIGHT_PT.toFloat()

    private val spec = ShaftSpec(
        overallLengthMm = 120f * inch,
        liners = listOf(Liner(id = "L1", startFromAftMm = 30f * inch, lengthMm = 20f * inch, odMm = 7f * inch)),
        bodies = listOf(Body(id = "B1", startFromAftMm = 0f, lengthMm = 120f * inch, diaMm = 6f * inch)),
    )
    private val resolved = resolveComponents(spec)

    private val job = ProjectInfo(
        customer = "NorthSound Marine",
        vessel = "FV Tern Point",
        side = ShaftPosition.STBD,
        jobNumber = "814201",
        notes = "Partially clad taper\nFiberglass FWD mid and AFT body",
    )

    private fun on(sheet: SheetDateSheet) = job.forSheet(sheet, emptyMap(), mapOf(sheet.name to true))

    private fun drawn(compose: (SheetTextRecorder) -> Unit): List<String> =
        SheetTextRecorder(pageW, pageH).also(compose).strings

    private fun schematic(project: ProjectInfo, blank: Boolean = false) = drawn { c ->
        composeShaftPdfOnCanvas(
            c, pageW, pageH, spec, UnitSystem.INCHES, project,
            appVersion = "test", filename = "job.pdf",
            options = PdfExportOptions(blankValues = blank),
            resolvedComponents = resolved,
        )
    }

    private fun consolidated(project: ProjectInfo) = drawn { c ->
        composeRunoutPdfOnCanvas(
            c, pageW, pageH, spec,
            config = RunoutConfig(), project = project, unit = UnitSystem.INCHES,
            resolvedComponents = resolved, consolidated = true,
        )
    }

    private fun List<String>.hasNotes() =
        any { it.contains("Partially clad taper") } && any { it.contains("Fiberglass FWD mid and AFT body") }

    @Test
    fun `notes print on no sheet by default`() {
        SheetDateSheet.values().forEach { assertFalse(it.name, sheetPrintsNotes(it, emptyMap())) }
        assertFalse(schematic(job.forSheet(SheetDateSheet.SCHEMATIC, emptyMap())).hasNotes())
    }

    @Test
    fun `the schematic prints the notes under the job block when its switch is on`() {
        val strings = schematic(on(SheetDateSheet.SCHEMATIC))
        assertTrue(strings.toString(), strings.hasNotes())
        assertTrue(strings.any { it.startsWith(FOOTER_NOTES_LABEL) })
    }

    @Test
    fun `the consolidated sheet prints them by its own switch`() {
        assertTrue(consolidated(on(SheetDateSheet.CONSOLIDATED)).hasNotes())
        // The schematic's switch does not reach the consolidated sheet.
        val schematicOnly = mapOf(SheetDateSheet.SCHEMATIC.name to true)
        assertFalse(consolidated(job.forSheet(SheetDateSheet.CONSOLIDATED, emptyMap(), schematicOnly)).hasNotes())
    }

    @Test
    fun `a blank draft prints no notes`() {
        assertFalse(schematic(on(SheetDateSheet.SCHEMATIC), blank = true).hasNotes())
    }

    @Test
    fun `a sheet without the footer never prints notes, switch or not`() {
        listOf(SheetDateSheet.RUNOUT, SheetDateSheet.WEAR, SheetDateSheet.UNDERCUT).forEach { sheet ->
            assertFalse(sheetPrintsNotes(sheet, mapOf(sheet.name to true)))
            assertEquals("", on(sheet).notes)
        }
    }

    @Test
    fun `typed lines become rows, blank ends dropped, the first labelled`() {
        assertEquals(emptyList<String>(), footerNoteLines(ProjectInfo(notes = "  \n ")))
        assertEquals(
            listOf("Notes: a", "", "b"),
            footerNoteLines(ProjectInfo(notes = "\na\n\nb\n\n")),
        )
    }

    @Test
    fun `the footer plan reserves the note rows`() {
        val paint = android.graphics.Paint().apply { textSize = 10f }
        val cfg = FooterConfig(showAftThread = false, showFwdThread = false, showAftTaper = false, showFwdTaper = false)
        fun rows(p: ProjectInfo) = planFooter(0f, 700f, spec, UnitSystem.INCHES, p, paint, cfg).wrappedMaxLines
        assertEquals(rows(job.copy(notes = "")) + 2, rows(job))
    }
}
