package com.android.shaftschematic.pdf

import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.util.FractionText
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two most common shop tapers print in taper-per-foot notation on inch drawings:
 * 1:12 → 1"/ft and 1:16 → 3/4"/ft. Every other rate — other common ratios, exact 1:N.NNN,
 * manual text — keeps its ratio form, and metric drawings keep the ratio for all rates.
 */
class TaperRatePrintNotationTest {

    // ── printedTaperRate mapping ─────────────────────────────────────────────

    @Test
    fun `inch drawings print 1-12 and 1-16 as taper per foot`() {
        assertEquals("1\"/ft", printedTaperRate("1:12", UnitSystem.INCHES))
        assertEquals("3/4\"/ft", printedTaperRate("1:16", UnitSystem.INCHES))
    }

    @Test
    fun `other rates keep the ratio form on inch drawings`() {
        assertEquals("1:10", printedTaperRate("1:10", UnitSystem.INCHES))
        assertEquals("1:20", printedTaperRate("1:20", UnitSystem.INCHES))
        assertEquals("1:15.875", printedTaperRate("1:15.875", UnitSystem.INCHES))
        // Manual text that isn't the exact snapped form stays verbatim (user-authored).
        assertEquals("1/16", printedTaperRate("1/16", UnitSystem.INCHES))
    }

    @Test
    fun `metric drawings keep the ratio form for all rates`() {
        assertEquals("1:12", printedTaperRate("1:12", UnitSystem.MILLIMETERS))
        assertEquals("1:16", printedTaperRate("1:16", UnitSystem.MILLIMETERS))
    }

    // ── Footer integration ───────────────────────────────────────────────────

    private fun specWithTaper(taper: Taper) = ShaftSpec(
        overallLengthMm = 1000f,
        tapers = listOf(taper),
    )

    private val cfg = FooterConfig(
        showAftThread = false,
        showFwdThread = false,
        showAftTaper = true,
        showFwdTaper = false,
        showCompressionNote = false,
    )

    @Test
    fun `auto-snapped 1-12 taper prints as 1 inch per foot in the inch footer`() {
        // 60 → 40 over 240 mm is exactly 1:12.
        val spec = specWithTaper(
            Taper(startFromAftMm = 0f, lengthMm = 240f, startDiaMm = 60f, endDiaMm = 40f)
        )
        val lines = buildFooterEndColumns(spec, UnitSystem.INCHES, cfg).aftLines
        assertTrue(lines.contains("Rate: 1\"/ft"))
    }

    @Test
    fun `auto-snapped 1-16 taper prints as three-quarter inch per foot in the inch footer`() {
        // 60 → 40 over 320 mm is exactly 1:16.
        val spec = specWithTaper(
            Taper(startFromAftMm = 0f, lengthMm = 320f, startDiaMm = 60f, endDiaMm = 40f)
        )
        val lines = buildFooterEndColumns(spec, UnitSystem.INCHES, cfg).aftLines
        assertTrue(lines.contains("Rate: 3/4\"/ft"))
    }

    @Test
    fun `auto-snapped 1-12 taper keeps the ratio in the metric footer`() {
        val spec = specWithTaper(
            Taper(startFromAftMm = 0f, lengthMm = 240f, startDiaMm = 60f, endDiaMm = 40f)
        )
        val lines = buildFooterEndColumns(spec, UnitSystem.MILLIMETERS, cfg).aftLines
        assertTrue(lines.contains("Rate: 1:12"))
    }

    @Test
    fun `manually typed 1-16 also prints as taper per foot in the inch footer`() {
        val spec = specWithTaper(
            Taper(
                startFromAftMm = 0f, lengthMm = 320f, startDiaMm = 60f, endDiaMm = 40f,
                taperRateText = "1:16",
            )
        )
        val lines = buildFooterEndColumns(spec, UnitSystem.INCHES, cfg).aftLines
        assertTrue(lines.contains("Rate: 3/4\"/ft"))
    }

    @Test
    fun `other common rates keep the ratio in the inch footer`() {
        // 60 → 40 over 200 mm is exactly 1:10.
        val spec = specWithTaper(
            Taper(startFromAftMm = 0f, lengthMm = 200f, startDiaMm = 60f, endDiaMm = 40f)
        )
        val lines = buildFooterEndColumns(spec, UnitSystem.INCHES, cfg).aftLines
        assertTrue(lines.contains("Rate: 1:10"))
    }

    /**
     * The plain `3/4` spelling is what lets the footer SET the rate as a built-up fraction —
     * `drawFooterLine` draws every spec line rich, and the parser must recognise the token or
     * the rate would print inline beside built-up lengths.
     */
    @Test
    fun `the 1-16 rate participates in built-up fraction rendering`() {
        assertTrue(FractionText.hasFraction(printedTaperRate("1:16", UnitSystem.INCHES)))
    }

    /** The renderer is the only place a fraction takes shape — no vulgar glyph may leave here. */
    @Test
    fun `printed rates never carry a Unicode vulgar glyph`() {
        listOf("1:12", "1:16", "1:10", "1:15.875", "1/16").forEach { rate ->
            UnitSystem.entries.forEach { unit ->
                val s = printedTaperRate(rate, unit)
                assertEquals("$rate ($unit) -> $s", -1, s.indexOfFirst { it in "¼½¾⅛⅜⅝⅞" })
            }
        }
    }
}
