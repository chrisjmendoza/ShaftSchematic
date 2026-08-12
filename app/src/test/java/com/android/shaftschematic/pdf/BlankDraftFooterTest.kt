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
 * Blank-draft (write-in) footer contract: `buildFooterEndColumns(blankValues = true)` keeps
 * every LABEL, in the same order and count as the standard footer, but no line carries a
 * numeric value — the values are hand-written on the printed rule.
 */
class BlankDraftFooterTest {

    private fun specWithEndFeatures(): ShaftSpec {
        val th = Threads(
            startFromAftMm = 0f,
            lengthMm = 100f,
            majorDiaMm = 50f,
            pitchMm = 2f,
            excludeFromOAL = false
        )
        val taper = Taper(
            startFromAftMm = 100f,
            lengthMm = 200f,
            startDiaMm = 50f,
            endDiaMm = 40f,
            keywayWidthMm = 10f,
            keywayDepthMm = 3f,
            keywayLengthMm = 80f
        )
        return ShaftSpec(
            overallLengthMm = 1000f,
            threads = listOf(th),
            tapers = listOf(taper)
        )
    }

    private val cfg = FooterConfig(
        showAftThread = true,
        showFwdThread = false,
        showAftTaper = true,
        showFwdTaper = false,
        showCompressionNote = false
    )

    @Test
    fun `blank footer keeps labels and drops all digits`() {
        val spec = specWithEndFeatures()
        val cols = buildFooterEndColumns(spec, UnitSystem.MILLIMETERS, cfg, blankValues = true)
        val lines = cols.aftLines

        assertTrue("Expected AFT Taper header", lines.contains("AFT Taper"))
        assertTrue("Expected bare Rate label", lines.contains("Rate:"))
        assertTrue("Expected bare Length label", lines.contains("Length:"))
        assertTrue("Expected bare KW label", lines.contains("KW:"))
        assertTrue("Expected bare Thread label", lines.contains("Thread:"))
        // No face suffix — the footer column already names the end (on-device report).
        assertTrue("Expected bare L.E.T. label", lines.contains("L.E.T.:"))
        assertTrue("Expected bare S.E.T. label", lines.contains("S.E.T.:"))

        lines.forEach { line ->
            assertFalse("Blank footer line must not contain digits: \"$line\"", line.any { it.isDigit() })
        }
    }

    @Test
    fun `blank footer has same line count and order of labels as standard footer`() {
        val spec = specWithEndFeatures()
        val standard = buildFooterEndColumns(spec, UnitSystem.MILLIMETERS, cfg, blankValues = false)
        val blank = buildFooterEndColumns(spec, UnitSystem.MILLIMETERS, cfg, blankValues = true)

        assertEquals(standard.aftLines.size, blank.aftLines.size)
        assertEquals(standard.fwdLines.size, blank.fwdLines.size)

        // Every blank line is a prefix of its standard counterpart (label kept, value dropped).
        standard.aftLines.zip(blank.aftLines).forEach { (std, blk) ->
            assertTrue(
                "\"$std\" should start with its blank label \"$blk\"",
                std.startsWith(blk)
            )
        }
    }

    @Test
    fun `blank footer includes body keyway label without values`() {
        val body = Body(
            startFromAftMm = 0f,
            lengthMm = 500f,
            diaMm = 80f,
            keywayWidthMm = 12f,
            keywayDepthMm = 4f,
            keywayLengthMm = 100f,
            keywayEnd = LinerAuthoredReference.AFT
        )
        val spec = ShaftSpec(overallLengthMm = 1000f, bodies = listOf(body))
        val cfgNoEnds = FooterConfig(
            showAftThread = false,
            showFwdThread = false,
            showAftTaper = false,
            showFwdTaper = false,
            showCompressionNote = false
        )

        val cols = buildFooterEndColumns(spec, UnitSystem.MILLIMETERS, cfgNoEnds, blankValues = true)
        assertTrue("Expected bare Body KW label", cols.aftLines.contains("Body KW:"))
        cols.aftLines.forEach { line ->
            assertFalse("Blank line must not contain digits: \"$line\"", line.any { it.isDigit() })
        }
    }

    @Test
    fun `standard footer output is unchanged by the blankValues default`() {
        val spec = specWithEndFeatures()
        val explicit = buildFooterEndColumns(spec, UnitSystem.MILLIMETERS, cfg, blankValues = false)
        val default = buildFooterEndColumns(spec, UnitSystem.MILLIMETERS, cfg)
        assertEquals(explicit, default)
    }
}
