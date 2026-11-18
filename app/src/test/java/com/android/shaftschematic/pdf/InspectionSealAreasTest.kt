package com.android.shaftschematic.pdf

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.ProjectInfo
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.settings.PdfPrefs
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
 * "Seal areas" on the inspection sheets (`PdfPrefs.inspectionSealAreas`, on-device request): the
 * classic runout sheet and the consolidated sheet draw a body's seal-area lines only while the
 * switch is on — off by default — and a Final sheet draws them either way.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InspectionSealAreasTest {

    private val inch = 25.4f
    private val pageW = PDF_PAGE_WIDTH_PT.toFloat()
    private val pageH = PDF_PAGE_HEIGHT_PT.toFloat()

    private fun spec(seal: Boolean) = ShaftSpec(
        overallLengthMm = 60f * inch,
        bodies = listOf(
            Body(
                id = "B1", startFromAftMm = 0f, lengthMm = 60f * inch, diaMm = 6f * inch,
                blendAftSeal = seal, blendAftSealLenMm = 4f * inch,
                blendFwdSeal = seal, blendFwdSealLenMm = 4f * inch,
            ),
        ),
    )

    private fun lineCount(
        seal: Boolean,
        prefs: PdfPrefs = PdfPrefs(),
        project: ProjectInfo = ProjectInfo(),
        consolidated: Boolean,
    ): Int {
        val s = spec(seal)
        return SheetTextRecorder(pageW, pageH).also { c ->
            composeRunoutPdfOnCanvas(
                c, pageW, pageH, s,
                config = RunoutConfig(), project = project, unit = UnitSystem.INCHES,
                pdfPrefs = prefs, resolvedComponents = resolveComponents(s), consolidated = consolidated,
            )
        }.lines.size
    }

    @Test
    fun `off by default, and a final sheet shows them either way`() {
        assertFalse(PdfPrefs().inspectionSealAreas)
        assertFalse(inspectionSheetShowsSealAreas(PdfPrefs(), ProjectInfo()))
        assertTrue(inspectionSheetShowsSealAreas(PdfPrefs(inspectionSealAreas = true), ProjectInfo()))
        assertTrue(inspectionSheetShowsSealAreas(PdfPrefs(), ProjectInfo(drawingLabel = "Final")))
    }

    @Test
    fun `the runout and consolidated sheets draw the seal lines only while the switch is on`() {
        listOf(false, true).forEach { consolidated ->
            val plain = lineCount(seal = false, consolidated = consolidated)
            val on = PdfPrefs(inspectionSealAreas = true)
            assertEquals("off: as a shaft with no seal areas", plain, lineCount(seal = true, consolidated = consolidated))
            assertTrue("on: the seal lines draw", lineCount(seal = true, prefs = on, consolidated = consolidated) > plain)
        }
    }

    @Test
    fun `a final sheet draws them with the switch off`() {
        val final = ProjectInfo(drawingLabel = "Final")
        assertTrue(
            lineCount(seal = true, project = final, consolidated = true) >
                lineCount(seal = false, project = final, consolidated = true),
        )
    }
}
