package com.android.shaftschematic.pdf

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ProjectInfo
import com.android.shaftschematic.model.ShaftPosition
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.SheetDateMode
import com.android.shaftschematic.model.SheetDateSheet
import com.android.shaftschematic.model.Undercut
import com.android.shaftschematic.model.UndercutRecord
import com.android.shaftschematic.model.forSheet
import com.android.shaftschematic.settings.RunoutConfig
import com.android.shaftschematic.ui.resolved.resolveComponents
import com.android.shaftschematic.util.PDF_PAGE_HEIGHT_PT
import com.android.shaftschematic.util.PDF_PAGE_WIDTH_PT
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * "Date on this sheet" on the real composers: with a sheet's switch off the composed page inks no
 * "Date:" anywhere, with it on the job's date prints, and a blank draft rules its "Date:" line
 * either way. One case per sheet family — schematic, consolidated, classic runout, wear, undercut
 * and the Final tab's Original + Final sheet — each handed its project through
 * [forSheet], as every print site does.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SheetDateOnSheetPrintTest {

    private val inch = 25.4f
    private val pageW = PDF_PAGE_WIDTH_PT.toFloat()
    private val pageH = PDF_PAGE_HEIGHT_PT.toFloat()

    private val spec = ShaftSpec(
        overallLengthMm = 120f * inch,
        liners = listOf(Liner(id = "L1", startFromAftMm = 30f * inch, lengthMm = 20f * inch, odMm = 7f * inch)),
        bodies = listOf(Body(id = "B1", startFromAftMm = 0f, lengthMm = 120f * inch, diaMm = 6f * inch)),
    )
    private val resolved = resolveComponents(spec)
    private val undercuts = UndercutRecord(
        undercuts = listOf(Undercut(startFromAftMm = 35f * inch, lengthMm = 2f * inch, diaMm = 6.9f * inch)),
    )

    private val job = ProjectInfo(
        customer = "NorthSound Marine",
        vessel = "FV Tern Point",
        side = ShaftPosition.PORT,
        jobNumber = "814201",
        dateMode = SheetDateMode.CUSTOM,
        dateText = "Nov 6",
    )

    private fun on(sheet: SheetDateSheet) = job.forSheet(sheet, mapOf(sheet.name to true))
    private fun off(sheet: SheetDateSheet) = job.forSheet(sheet, mapOf(sheet.name to false))

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

    private fun runout(project: ProjectInfo, consolidated: Boolean, blank: Boolean = false) = drawn { c ->
        composeRunoutPdfOnCanvas(
            c, pageW, pageH, spec,
            config = RunoutConfig(), project = project, unit = UnitSystem.INCHES,
            resolvedComponents = resolved, blankValues = blank, consolidated = consolidated,
        )
    }

    private fun wear(project: ProjectInfo) = drawn { c ->
        composeWearPdf(
            page = pageOnCanvas(c, PDF_PAGE_WIDTH_PT, PDF_PAGE_HEIGHT_PT),
            spec = spec, project = project, unit = UnitSystem.INCHES,
            resolvedComponents = resolved,
        )
    }

    private fun undercut(project: ProjectInfo, blank: Boolean = false) = drawn { c ->
        composeUndercutPdf(
            page = pageOnCanvas(c, PDF_PAGE_WIDTH_PT, PDF_PAGE_HEIGHT_PT),
            spec = spec, project = project, unit = UnitSystem.INCHES,
            resolvedComponents = resolved, undercutRecord = undercuts, blankValues = blank,
        )
    }

    private fun beforeAfter(project: ProjectInfo) = drawn { c ->
        composeBeforeAfterPdfOnCanvas(
            c, pageW, pageH, originalSpec = spec, finalSpec = spec,
            unit = UnitSystem.INCHES, project = project.copy(drawingLabel = "Final"),
            appVersion = "test", filename = "job_Final_BeforeAfter.pdf",
            originalResolved = resolved, finalResolved = resolved,
        )
    }

    private fun assertDated(sheet: String, strings: List<String>) =
        assertTrue("$sheet: the job's date is not printed: $strings", strings.any { "Date: Nov 6" in it })

    private fun assertUndated(sheet: String, strings: List<String>) {
        assertFalse("$sheet: a Date label printed: $strings", strings.any { "Date" in it })
        assertFalse("$sheet: the date printed: $strings", strings.any { "Nov 6" in it })
        // The rest of the job block still prints — only the date is left off.
        assertTrue("$sheet: the job number is missing: $strings", strings.any { "814201" in it })
    }

    @Test
    fun `the schematic prints the date with its switch on and none with it off`() {
        assertDated("schematic", schematic(on(SheetDateSheet.SCHEMATIC)))
        assertUndated("schematic", schematic(off(SheetDateSheet.SCHEMATIC)))
    }

    @Test
    fun `the consolidated sheet prints the date with its switch on and none with it off`() {
        assertDated("consolidated", runout(on(SheetDateSheet.CONSOLIDATED), consolidated = true))
        assertUndated("consolidated", runout(off(SheetDateSheet.CONSOLIDATED), consolidated = true))
    }

    @Test
    fun `the classic runout sheet prints the date with its switch on and none with it off`() {
        assertDated("runout", runout(on(SheetDateSheet.RUNOUT), consolidated = false))
        assertUndated("runout", runout(off(SheetDateSheet.RUNOUT), consolidated = false))
    }

    @Test
    fun `the wear sheet prints the date with its switch on and none with it off`() {
        assertDated("wear", wear(on(SheetDateSheet.WEAR)))
        assertUndated("wear", wear(off(SheetDateSheet.WEAR)))
    }

    @Test
    fun `the undercut sheet prints the date with its switch on and none with it off`() {
        assertDated("undercut", undercut(on(SheetDateSheet.UNDERCUT)))
        assertUndated("undercut", undercut(off(SheetDateSheet.UNDERCUT)))
    }

    @Test
    fun `the Final tab's Original + Final sheet follows the Final switch`() {
        assertDated("final", beforeAfter(on(SheetDateSheet.FINAL)))
        assertUndated("final", beforeAfter(off(SheetDateSheet.FINAL)))
    }

    @Test
    fun `the shipped defaults date the schematic and leave the working sheets undated`() {
        val none = emptyMap<String, Boolean>()

        assertDated("schematic", schematic(job.forSheet(SheetDateSheet.SCHEMATIC, none)))
        assertDated("consolidated", runout(job.forSheet(SheetDateSheet.CONSOLIDATED, none), consolidated = true))
        assertDated("final", beforeAfter(job.forSheet(SheetDateSheet.FINAL, none)))
        assertUndated("runout", runout(job.forSheet(SheetDateSheet.RUNOUT, none), consolidated = false))
        assertUndated("wear", wear(job.forSheet(SheetDateSheet.WEAR, none)))
        assertUndated("undercut", undercut(job.forSheet(SheetDateSheet.UNDERCUT, none)))
    }

    @Test
    fun `a blank draft rules its Date line with the switch off`() {
        fun ruled(sheet: String, strings: List<String>) =
            assertTrue("$sheet: no Date line to fill in: $strings", strings.any { "Date:" in it })

        ruled("schematic", schematic(off(SheetDateSheet.SCHEMATIC), blank = true))
        ruled("runout", runout(off(SheetDateSheet.RUNOUT), consolidated = false, blank = true))
        ruled("undercut", undercut(off(SheetDateSheet.UNDERCUT), blank = true))
    }
}
