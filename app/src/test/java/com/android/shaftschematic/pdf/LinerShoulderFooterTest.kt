package com.android.shaftschematic.pdf

import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The "Liner shoulder R:" footer line — ONE line per shouldered liner. A liner shouldered at
 * both ends used to print two identical lines with nothing telling them apart (on-device
 * report); now matching radii print once and differing radii print `AFT r, FWD r`. A liner
 * shouldered at one end keeps the single-value line it always had, and with more than one
 * shouldered liner on the shaft each line carries the liner's carousel name.
 */
class LinerShoulderFooterTest {

    private val cfg = FooterConfig(
        showAftThread = false,
        showFwdThread = false,
        showAftTaper = false,
        showFwdTaper = false,
    )

    private val quarterIn = 0.25f * 25.4f
    private val threeEighthsIn = 0.375f * 25.4f

    private fun liner(startMm: Float, lengthMm: Float, aftR: Float = 0f, fwdR: Float = 0f, label: String? = null) =
        Liner(
            startFromAftMm = startMm, lengthMm = lengthMm, odMm = 160f, label = label,
            shoulderAftLenMm = if (aftR > 0f) 20f else 0f, shoulderAftOdMm = if (aftR > 0f) 150f else 0f,
            shoulderAftRadiusMm = aftR,
            shoulderFwdLenMm = if (fwdR > 0f) 20f else 0f, shoulderFwdOdMm = if (fwdR > 0f) 150f else 0f,
            shoulderFwdRadiusMm = fwdR,
        )

    private fun shoulderLines(cols: FooterColumns): List<String> =
        (cols.aftLines + cols.fwdLines).filter { it.contains("shoulder R:") }

    @Test
    fun `both ends at one radius print a single line`() {
        val spec = ShaftSpec(
            overallLengthMm = 3000f,
            liners = listOf(liner(200f, 600f, aftR = quarterIn, fwdR = quarterIn)),
        )
        val cols = buildFooterEndColumns(spec, UnitSystem.INCHES, cfg)
        assertEquals(listOf("Liner shoulder R: 1/4\""), shoulderLines(cols))
        assertTrue("an AFT-half liner lists in the AFT column", cols.aftLines.contains("Liner shoulder R: 1/4\""))
    }

    @Test
    fun `both ends at different radii name each end on the one line`() {
        val spec = ShaftSpec(
            overallLengthMm = 3000f,
            liners = listOf(liner(2200f, 600f, aftR = quarterIn, fwdR = threeEighthsIn)),
        )
        val cols = buildFooterEndColumns(spec, UnitSystem.INCHES, cfg)
        assertEquals(listOf("Liner shoulder R: AFT 1/4\", FWD 3/8\""), shoulderLines(cols))
        assertTrue("a FWD-half liner lists in the FWD column", cols.fwdLines.contains("Liner shoulder R: AFT 1/4\", FWD 3/8\""))
    }

    @Test
    fun `one shouldered end keeps the plain single-value line in its end's column`() {
        // Byte-identical to the line documents with one shoulder have always printed.
        val spec = ShaftSpec(
            overallLengthMm = 3000f,
            liners = listOf(liner(1300f, 400f, fwdR = quarterIn)),
        )
        val cols = buildFooterEndColumns(spec, UnitSystem.INCHES, cfg)
        assertEquals(listOf("Liner shoulder R: 1/4\""), shoulderLines(cols))
        assertTrue("the FWD end at 1700 of 3000 sits in the FWD half", cols.fwdLines.contains("Liner shoulder R: 1/4\""))
    }

    @Test
    fun `a sharp corner prints nothing`() {
        val spec = ShaftSpec(
            overallLengthMm = 3000f,
            liners = listOf(Liner(startFromAftMm = 200f, lengthMm = 600f, odMm = 160f, shoulderAftLenMm = 20f, shoulderAftOdMm = 150f)),
        )
        assertTrue(shoulderLines(buildFooterEndColumns(spec, UnitSystem.INCHES, cfg)).isEmpty())
    }

    @Test
    fun `two shouldered liners carry their names so the lines tell apart`() {
        val spec = ShaftSpec(
            overallLengthMm = 3000f,
            liners = listOf(
                liner(200f, 600f, aftR = quarterIn, fwdR = quarterIn),
                liner(2200f, 600f, aftR = threeEighthsIn, label = "SKF sleeve"),
            ),
        )
        val cols = buildFooterEndColumns(spec, UnitSystem.INCHES, cfg)
        assertEquals(listOf("AFT Liner shoulder R: 1/4\""), cols.aftLines.filter { it.contains("shoulder R:") })
        assertEquals(listOf("SKF sleeve shoulder R: 3/8\""), cols.fwdLines.filter { it.contains("shoulder R:") })
    }

    @Test
    fun `a blank draft keeps the label and drops the value`() {
        val spec = ShaftSpec(
            overallLengthMm = 3000f,
            liners = listOf(liner(200f, 600f, aftR = quarterIn, fwdR = threeEighthsIn)),
        )
        val cols = buildFooterEndColumns(spec, UnitSystem.INCHES, cfg, blankValues = true)
        assertEquals(listOf("Liner shoulder R:"), shoulderLines(cols))
    }
}
