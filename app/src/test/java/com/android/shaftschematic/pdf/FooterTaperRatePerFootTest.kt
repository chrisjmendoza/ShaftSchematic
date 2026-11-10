package com.android.shaftschematic.pdf

import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.settings.PdfPrefs
import com.android.shaftschematic.util.DisplayUnits
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `PdfPrefs.taperRatePerFoot` — an inch taper's footer "Rate:" in inches per foot (on-device
 * request: a 3 3/4"/ft taper printed as 1:3.210, a ratio the shop does not use).
 */
class FooterTaperRatePerFootTest {

    private val inch = 25.4f

    private fun spec(rateText: String, manual: Boolean?) = ShaftSpec(
        overallLengthMm = 60f * inch,
        tapers = listOf(
            Taper(
                id = "TA", startFromAftMm = 0f, lengthMm = 9.531f * inch,
                startDiaMm = 6.011f * inch, endDiaMm = 8.980f * inch,
                taperRateText = rateText, taperRateManual = manual,
            ),
        ),
    )

    private fun rateLine(s: ShaftSpec, perFoot: Boolean, unit: UnitSystem = UnitSystem.INCHES): String {
        val cfg = FooterConfig(
            showAftThread = false, showFwdThread = false, showAftTaper = true, showFwdTaper = false,
            taperRatePerFoot = perFoot,
        )
        return buildFooterEndColumns(s, unit, cfg, displayUnits = DisplayUnits.single(unit))
            .aftLines.first { it.startsWith("Rate:") }
    }

    @Test
    fun `the shipped default prints per foot`() {
        assertTrue(PdfPrefs().taperRatePerFoot)
        assertTrue(sheetFooterConfig(spec("1:3.210", false), PdfPrefs()).taperRatePerFoot)
        assertFalse(sheetFooterConfig(spec("1:3.210", false), PdfPrefs(taperRatePerFoot = false)).taperRatePerFoot)
    }

    @Test
    fun `an auto rate prints its nearest eighth per foot`() {
        assertEquals("Rate: 3 3/4\"/ft", rateLine(spec("1:3.210", manual = false), perFoot = true))
    }

    @Test
    fun `a typed per-foot rate keeps its own number`() {
        assertEquals("Rate: 3 3/4\"/ft", rateLine(spec("3 3/4\"/ft", manual = true), perFoot = true))
        assertEquals("Rate: 3.74\"/ft", rateLine(spec("3.74 in/ft", manual = true), perFoot = true))
    }

    @Test
    fun `a typed ratio converts exactly, never snapped`() {
        assertEquals("Rate: 3.738\"/ft", rateLine(spec("1:3.21", manual = true), perFoot = true))
    }

    @Test
    fun `off prints the ratio exactly as before`() {
        assertEquals("Rate: 1:3.210", rateLine(spec("1:3.210", manual = false), perFoot = false))
        assertEquals("Rate: 3 3/4\"/ft", rateLine(spec("3 3/4\"/ft", manual = true), perFoot = false))
    }

    @Test
    fun `a metric drawing keeps the ratio`() {
        assertEquals("Rate: 1:3.210", rateLine(spec("1:3.210", manual = false), perFoot = true, unit = UnitSystem.MILLIMETERS))
    }
}
