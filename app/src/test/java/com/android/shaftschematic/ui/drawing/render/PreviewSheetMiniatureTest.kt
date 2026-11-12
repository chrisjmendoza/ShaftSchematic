package com.android.shaftschematic.ui.drawing.render

import com.android.shaftschematic.geom.drawnExtentMm
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.pdf.SCHEMATIC_SHEET_CONTENT_WIDTH_PT
import com.android.shaftschematic.pdf.schematicProfileFit
import com.android.shaftschematic.util.PDF_PAGE_HEIGHT_PT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The editor preview's sheet layout is a uniform MINIATURE of the sheet's shaft
 * (`PreviewSheetLayout.kt`): the fit is solved over the schematic sheet's content width and drawn
 * into the box under one scale, so liners and tapers keep the sheet's proportion on a phone. The
 * reported shape: three liners of 45 3/8", 30 7/8" and 16 1/2" drew the same width in a ~330 dp
 * box, with or without "Keep liners and tapers proportional lengthwise" (on-device report).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PreviewSheetMiniatureTest {

    private val inch = 25.4f

    /** OAL 304", an 8" shaft, a taper at each end and three liners with bare shaft between them. */
    private val spec = ShaftSpec(
        overallLengthMm = 304f * inch,
        autoBodyDiaMm = 8f * inch,
        tapers = listOf(
            Taper(id = "TA", startFromAftMm = 0f, lengthMm = 17f * inch, startDiaMm = 6.5f * inch, endDiaMm = 8f * inch),
            Taper(id = "TF", startFromAftMm = 290f * inch, lengthMm = 14f * inch, startDiaMm = 8f * inch, endDiaMm = 7f * inch),
        ),
        liners = listOf(
            Liner(id = "L1", startFromAftMm = 30f * inch, lengthMm = 45.375f * inch, odMm = 9f * inch),
            Liner(id = "L2", startFromAftMm = 120f * inch, lengthMm = 30.875f * inch, odMm = 9f * inch),
            Liner(id = "L3", startFromAftMm = 200f * inch, lengthMm = 16.5f * inch, odMm = 9f * inch),
        ),
    )

    private val proportional = PreviewSheetSettings(linerMinFracOfTrue = 1f)

    private fun phoneProfile(density: Float, sheetWidthPt: Float = SCHEMATIC_SHEET_CONTENT_WIDTH_PT) =
        previewSheetProfile(
            spec = spec,
            settings = proportional,
            contentWidthPx = 330f * density,
            contentHeightPx = 110f * density,
            densityPxPerPt = density,
            drawnMaxDiaMm = 9f * inch,
            sheetWidthPt = sheetWidthPt,
        )

    /** Each liner's drawn width in px: the profile's map times its one scale. */
    private fun linerWidthsPx(p: PreviewSheetProfile): List<Float> = spec.liners.map {
        (p.xMap.xAt(it.startFromAftMm + it.lengthMm) - p.xMap.xAt(it.startFromAftMm)) * p.pxPerPt
    }

    @Test
    fun `liners keep the sheet's proportion in a phone box`() {
        val sheet = schematicProfileFit(
            spec,
            proportional.pdfPrefs(),
            proportional.heightScale,
            proportional.linerMinFracOfTrue,
            contentLeft = 0f,
            contentRight = SCHEMATIC_SHEET_CONTENT_WIDTH_PT,
            budgetCapPt = PDF_PAGE_HEIGHT_PT.toFloat(),
            drawnMaxDiaMm = 9f * inch,
        )
        val sheetWidths = spec.liners.map {
            sheet.xMap.xAt(it.startFromAftMm + it.lengthMm) - sheet.xMap.xAt(it.startFromAftMm)
        }
        for (density in listOf(1f, 2.75f)) {
            val p = phoneProfile(density)
            assertNotNull(p)
            p!!
            val widths = linerWidthsPx(p)
            val before = linerWidthsPx(phoneProfile(density, sheetWidthPt = 0f)!!)
            println(
                "density $density: liner widths (dp) now ${widths.map { it / density }}, " +
                    "solved over the box ${before.map { it / density }}, sheet (pt) $sheetWidths",
            )
            assertTrue("longest > middle > shortest: $widths", widths[0] > widths[1] && widths[1] > widths[2])
            // The preview is the sheet's map times one constant.
            val k = widths[0] / sheetWidths[0]
            widths.indices.forEach { i ->
                assertEquals("liner $i ratio", sheetWidths[i] * k, widths[i], 0.01f * density)
            }
            assertEquals(330f * density / SCHEMATIC_SHEET_CONTENT_WIDTH_PT, k, 1e-3f)
            // The whole drawn extent spans exactly the box's content width.
            val extent = spec.drawnExtentMm()
            assertEquals(
                330f * density,
                (p.xMap.xAt(extent.endMm) - p.xMap.xAt(extent.startMm)) * p.pxPerPt,
                0.01f * density,
            )
            // The drawn shaft stays inside the box's height cap.
            assertTrue(9f * inch * p.diaPtPerMm * p.pxPerPt <= 110f * density * PREVIEW_SHEET_SHAFT_MAX_FRAC + 0.01f)
        }
    }

    @Test
    fun `a box at least as wide as the sheet solves over its own width, unscaled`() {
        val density = 2f
        val widthPt = SCHEMATIC_SHEET_CONTENT_WIDTH_PT + 80f
        val heightPt = 200f
        val p = previewSheetProfile(
            spec = spec,
            settings = proportional,
            contentWidthPx = widthPt * density,
            contentHeightPx = heightPt * density,
            densityPxPerPt = density,
            drawnMaxDiaMm = 9f * inch,
        )!!
        val own = schematicProfileFit(
            spec,
            proportional.pdfPrefs(),
            proportional.heightScale,
            proportional.linerMinFracOfTrue,
            contentLeft = 0f,
            contentRight = widthPt * density / density,
            budgetCapPt = heightPt * density / density * PREVIEW_SHEET_SHAFT_MAX_FRAC,
            drawnMaxDiaMm = 9f * inch,
        )
        assertEquals(density, p.pxPerPt, 0f)
        assertEquals(own.diaPtPerMm, p.diaPtPerMm, 0f)
        listOf(0f, 17f, 30f, 75.375f, 120f, 200f, 290f, 304f).map { it * inch }.forEach { mm ->
            assertEquals("x at $mm", own.xMap.xAt(mm), p.xMap.xAt(mm), 0f)
        }
        // At exactly the sheet's width the scale is still one.
        val exact = previewSheetProfile(
            spec = spec,
            settings = proportional,
            contentWidthPx = SCHEMATIC_SHEET_CONTENT_WIDTH_PT * density,
            contentHeightPx = heightPt * density,
            densityPxPerPt = density,
        )!!
        assertEquals(density, exact.pxPerPt, 0f)
    }
}
