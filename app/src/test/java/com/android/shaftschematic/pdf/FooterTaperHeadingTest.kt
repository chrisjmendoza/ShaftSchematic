package com.android.shaftschematic.pdf

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.LinerAuthoredReference
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.Threads
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A footer taper block is headed with the taper's NAME when the user gave it one (a rename must reach
 * the printed page); otherwise "AFT Taper"/"FWD Taper" only when that taper is the shaft's END
 * feature on its side, else plain "Taper". On-device report (a 239" shaft whose FWD end is a straight keyed body): a
 * step-down taper mid-shaft printed as the "FWD Taper", and the FWD body's keyway line under it
 * read as the taper's keyway.
 */
class FooterTaperHeadingTest {

    private val inch = 25.4f
    private val cfg = FooterConfig(
        showAftThread = true, showFwdThread = true, showAftTaper = true, showFwdTaper = true,
    )

    private fun pacFish(midLabel: String? = "Body size transition taper") = ShaftSpec(
        overallLengthMm = 239f * inch,
        threads = listOf(
            Threads(
                id = "TH", startFromAftMm = -4.875f * inch, lengthMm = 4.875f * inch,
                majorDiaMm = 4.5f * inch, tpi = 4f, excludeFromOAL = true, isAftEnd = true,
            ),
        ),
        tapers = listOf(
            Taper(
                id = "TA", startFromAftMm = 0f, lengthMm = 16.125f * inch,
                startDiaMm = 5.182f * inch, endDiaMm = 6.526f * inch,
            ),
            Taper(
                id = "TM", startFromAftMm = 180f * inch, lengthMm = 6.375f * inch,
                startDiaMm = 6.5f * inch, endDiaMm = 5.906f * inch, label = midLabel,
            ),
        ),
        bodies = listOf(
            Body(
                id = "B1", startFromAftMm = 224.25f * inch, lengthMm = 14.75f * inch, diaMm = 5.998f * inch,
                keywayWidthMm = 1.375f * inch, keywayDepthMm = 0.5f * inch, keywayLengthMm = 11f * inch,
                keywayEnd = LinerAuthoredReference.FWD,
            ),
        ),
    )

    @Test
    fun `a mid-shaft taper is headed with its label, never as the FWD taper`() {
        val cols = buildFooterEndColumns(pacFish(), UnitSystem.INCHES, cfg)
        assertEquals(FOOTER_AFT_TAPER_HEADER, cols.aftLines.first())
        assertEquals("Body size transition taper", cols.fwdLines.first())
        assertFalse(cols.fwdLines.contains(FOOTER_FWD_TAPER_HEADER))
    }

    @Test
    fun `an unlabelled mid-shaft taper is headed plain Taper`() {
        val cols = buildFooterEndColumns(pacFish(midLabel = null), UnitSystem.INCHES, cfg)
        assertEquals(FOOTER_MID_TAPER_HEADER, cols.fwdLines.first())
    }

    @Test
    fun `a body keyway under a taper block names its body`() {
        val cols = buildFooterEndColumns(pacFish(), UnitSystem.INCHES, cfg)
        assertTrue(cols.fwdLines.toString(), cols.fwdLines.any { it.startsWith("Body #1 KW: ") })
        assertTrue(cols.fwdLines.none { it.startsWith("Body KW: ") })
    }

    @Test
    fun `a lone body keyway with no taper in its column keeps the plain prefix`() {
        val spec = pacFish().copy(tapers = pacFish().tapers.filter { it.id == "TA" })
        val cols = buildFooterEndColumns(spec, UnitSystem.INCHES, cfg)
        assertTrue(cols.fwdLines.any { it.startsWith("Body KW: ") })
    }

    @Test
    fun `end tapers keep their AFT and FWD headings`() {
        val spec = pacFish().copy(
            tapers = pacFish().tapers + Taper(
                id = "TF", startFromAftMm = 225f * inch, lengthMm = 14f * inch,
                startDiaMm = 6f * inch, endDiaMm = 5f * inch,
            ),
            bodies = emptyList(),
        )
        val cols = buildFooterEndColumns(spec, UnitSystem.INCHES, cfg)
        assertEquals(FOOTER_AFT_TAPER_HEADER, cols.aftLines.first())
        assertEquals(FOOTER_FWD_TAPER_HEADER, cols.fwdLines.first())
    }

    @Test
    fun `the blank draft heads the mid taper the same way`() {
        val cols = buildFooterEndColumns(pacFish(), UnitSystem.INCHES, cfg, blankValues = true)
        assertEquals(FOOTER_AFT_TAPER_HEADER, cols.aftLines.first())
        assertEquals("Body size transition taper", cols.fwdLines.first())
        assertTrue(cols.fwdLeadsWithHeading)
        assertTrue(cols.fwdLines.contains("Body #1 KW:"))
    }

    @Test
    fun `a named end taper prints its name`() {
        val spec = pacFish().let { s ->
            s.copy(tapers = s.tapers.map { if (it.id == "TA") it.copy(label = "Prop taper") else it })
        }
        val cols = buildFooterEndColumns(spec, UnitSystem.INCHES, cfg)
        assertEquals("Prop taper", cols.aftLines.first())
        assertTrue(cols.aftLeadsWithHeading)
    }

    @Test
    fun `a blank name falls back to the positional heading`() {
        val spec = pacFish(midLabel = "   ")
        val cols = buildFooterEndColumns(spec, UnitSystem.INCHES, cfg)
        assertEquals(FOOTER_MID_TAPER_HEADER, cols.fwdLines.first())
    }
}
