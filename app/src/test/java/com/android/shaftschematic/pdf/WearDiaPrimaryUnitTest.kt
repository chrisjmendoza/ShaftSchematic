package com.android.shaftschematic.pdf

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ProjectInfo
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.WearDiaReading
import com.android.shaftschematic.model.WearRecord
import com.android.shaftschematic.model.WornSection
import com.android.shaftschematic.settings.PdfPrefs
import com.android.shaftschematic.settings.RunoutConfig
import com.android.shaftschematic.ui.resolved.resolveComponents
import com.android.shaftschematic.util.DisplayUnits
import com.android.shaftschematic.util.DualUnitLayout
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
 * A wear Ø value prints the PRIMARY unit only — never a dual secondary, inline or stacked
 * (on-device ruling: on a dual job the in-profile "[278.9 mm]" column stood taller than the
 * shaft). Covers the consolidated sheet's in-profile reading and worn-section values and the wear
 * document's own Ø callouts. The dimension rails keep dual, which the composer checks also prove:
 * the same dual sheet still prints millimetres somewhere.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WearDiaPrimaryUnitTest {

    private val inch = 25.4f
    private val pageW = PDF_PAGE_WIDTH_PT.toFloat()
    private val pageH = PDF_PAGE_HEIGHT_PT.toFloat()

    private val spec = ShaftSpec(
        overallLengthMm = 200f * inch,
        tapers = listOf(
            Taper(id = "T1", startFromAftMm = 0f, lengthMm = 20f * inch, startDiaMm = 10f * inch, endDiaMm = 8.5f * inch),
        ),
        liners = listOf(Liner(id = "L1", startFromAftMm = 40f * inch, lengthMm = 30f * inch, odMm = 11f * inch)),
        bodies = listOf(Body(id = "B1", startFromAftMm = 70f * inch, lengthMm = 130f * inch, diaMm = 10f * inch)),
    )

    private val linerReadingMm = 10.985f * inch
    private val bodyReadingMm = 9.997f * inch
    private val wornMm = 10.978f * inch

    private val wear = WearRecord(
        wornSections = listOf(WornSection(startFromAftMm = 45f * inch, lengthMm = 6f * inch, diaMm = listOf(wornMm))),
        diaReadings = listOf(
            WearDiaReading(componentId = "L1", axialMm = 20f * inch, diaMm = linerReadingMm),
            WearDiaReading(componentId = "B1", axialMm = 60f * inch, diaMm = bodyReadingMm),
        ),
    )

    private val dual = DisplayUnits(UnitSystem.INCHES, dual = true)

    private fun mmTerm(mm: Float) = formatDiaWithUnit(mm.toDouble(), UnitSystem.MILLIMETERS)

    // ── The label helpers ────────────────────────────────────────────────────

    @Test fun `label helpers print the plain primary format, one term`() {
        assertEquals(
            listOf("Ø" + formatDiaWithUnit(wornMm.toDouble(), UnitSystem.INCHES)),
            wornSectionValueLabels(listOf(wornMm), UnitSystem.INCHES),
        )
        assertEquals(
            "Ø" + formatDiaWithUnit(linerReadingMm.toDouble(), UnitSystem.INCHES),
            diaReadingValueLabel(linerReadingMm, UnitSystem.INCHES),
        )
        val callout = wearDiaCalloutLabel(bodyReadingMm, UnitSystem.INCHES)
        assertEquals(formatDiaWithUnit(bodyReadingMm.toDouble(), UnitSystem.INCHES), callout.primary)
        assertFalse(callout.isDual)
    }

    // ── The consolidated sheet ───────────────────────────────────────────────

    private fun consolidated(layout: DualUnitLayout): SheetTextRecorder {
        val c = SheetTextRecorder(pageW, pageH)
        composeRunoutPdfOnCanvas(
            c, pageW, pageH, spec,
            config = RunoutConfig(), project = ProjectInfo(), unit = UnitSystem.INCHES,
            displayUnits = dual,
            pdfPrefs = PdfPrefs(dualUnitLayout = layout),
            resolvedComponents = resolveComponents(spec),
            wearRecord = wear, consolidated = true,
            includeBubbles = false, includeWearInfo = true,
        )
        return c
    }

    @Test fun `consolidated in-profile values print the primary unit only under dual`() {
        for (layout in DualUnitLayout.values()) {
            val drawn = consolidated(layout).strings
            for (mm in listOf(wornMm, linerReadingMm, bodyReadingMm)) {
                val primary = "Ø" + formatDiaWithUnit(mm.toDouble(), UnitSystem.INCHES)
                assertTrue("$layout: $primary not printed in $drawn", primary in drawn)
                assertTrue(
                    "$layout: a dual secondary for $primary printed: $drawn",
                    drawn.none { mmTerm(mm) in it },
                )
            }
            assertTrue("$layout: the rails lost their dual mm term: $drawn", drawn.any { " mm" in it })
        }
    }

    // ── The wear document ────────────────────────────────────────────────────

    @Test fun `wear sheet Ø callouts print the primary unit only under dual`() {
        for (layout in DualUnitLayout.values()) {
            val c = SheetTextRecorder(pageW, pageH)
            composeWearPdf(
                page = pageOnCanvas(c, PDF_PAGE_WIDTH_PT, PDF_PAGE_HEIGHT_PT),
                spec = spec, project = ProjectInfo(), unit = UnitSystem.INCHES,
                displayUnits = dual, pdfPrefs = PdfPrefs(dualUnitLayout = layout),
                resolvedComponents = resolveComponents(spec), wearRecord = wear,
            )
            val drawn = c.strings
            for (mm in listOf(linerReadingMm, bodyReadingMm)) {
                val primary = formatDiaWithUnit(mm.toDouble(), UnitSystem.INCHES)
                assertTrue("$layout: $primary not printed in $drawn", primary in drawn)
                assertTrue(
                    "$layout: a dual secondary for $primary printed: $drawn",
                    drawn.none { mmTerm(mm) in it },
                )
            }
            assertTrue("$layout: the wear sheet's dimensions lost dual: $drawn", drawn.any { " mm" in it })
        }
    }
}
