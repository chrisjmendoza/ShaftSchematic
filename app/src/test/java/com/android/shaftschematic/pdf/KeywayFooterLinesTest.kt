package com.android.shaftschematic.pdf

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.LinerAuthoredReference
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.settings.PdfPrefs
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A spooned keyway prints NOTHING extra on the footer — no "(spooned)" and no note; the bowl on the
 * drawing shows it (on-device ruling). Its KW column reads exactly as the plain keyway's.
 *
 * A captured keyway (inset > 0) gets ONE line directly under its KW line stating the inset and the
 * face it is measured from, while `PdfPrefs.footerCapturedKwInset` is on (the default); off, or on
 * an open keyway, there is none.
 */
class KeywayFooterLinesTest {

    private val cfg = FooterConfig(
        showAftThread = false,
        showFwdThread = false,
        showAftTaper = true,
        showFwdTaper = true
    )

    private fun spoonedTaper() = Taper(
        startFromAftMm = 0f, lengthMm = 300f, startDiaMm = 100f, endDiaMm = 80f,
        keywayWidthMm = 25f, keywayDepthMm = 12f, keywayLengthMm = 200f,
        keywaySpooned = true,
    )

    private fun spoonedBody(spooned: Boolean = true) = Body(
        startFromAftMm = 1400f, lengthMm = 600f, diaMm = 100f,
        keywayWidthMm = 30f, keywayDepthMm = 15f, keywayLengthMm = 150f,
        keywayEnd = LinerAuthoredReference.FWD,
        keywaySpooned = spooned,
    )

    private fun noSpoonWording(lines: List<String>) =
        assertTrue("got: $lines", lines.none { it.contains("spoon", ignoreCase = true) })

    @Test
    fun `a spooned taper keyway prints the same footer as a plain one`() {
        val spooned = buildFooterEndColumns(
            ShaftSpec(overallLengthMm = 2000f, tapers = listOf(spoonedTaper())), UnitSystem.MILLIMETERS, cfg,
        )
        val plain = buildFooterEndColumns(
            ShaftSpec(overallLengthMm = 2000f, tapers = listOf(spoonedTaper().copy(keywaySpooned = false))),
            UnitSystem.MILLIMETERS, cfg,
        )
        assertEquals(plain.aftLines, spooned.aftLines)
        noSpoonWording(spooned.aftLines)
    }

    @Test
    fun `a spooned body keyway prints the same footer as a plain one`() {
        val spooned = buildFooterEndColumns(
            ShaftSpec(overallLengthMm = 2000f, bodies = listOf(spoonedBody())), UnitSystem.MILLIMETERS, cfg,
        )
        val plain = buildFooterEndColumns(
            ShaftSpec(overallLengthMm = 2000f, bodies = listOf(spoonedBody(spooned = false))),
            UnitSystem.MILLIMETERS, cfg,
        )
        assertEquals(plain.fwdLines, spooned.fwdLines)
        noSpoonWording(spooned.aftLines + spooned.fwdLines)
    }

    // ── Captured keyways (inset > 0): one inset line directly under the KW line ──

    @Test
    fun `a captured taper keyway prints its inset from the S E T under the KW line`() {
        val spec = ShaftSpec(
            overallLengthMm = 2000f,
            tapers = listOf(spoonedTaper().copy(keywaySpooned = false, keywayOffsetFromSetMm = 25f)),
        )
        val cols = buildFooterEndColumns(spec, UnitSystem.MILLIMETERS, cfg)
        val kwIdx = cols.aftLines.indexOfFirst { it.startsWith("KW: ") }
        assertTrue(kwIdx >= 0)
        val inset = cols.aftLines[kwIdx + 1]
        assertTrue("got: $inset", inset.startsWith("$CAPTURED_KW_PREFIX inset "))
        assertTrue("got: $inset", inset.contains("25"))
        assertTrue("got: $inset", inset.endsWith(" from S.E.T."))
    }

    @Test
    fun `a captured body keyway prints its inset from the referenced face`() {
        val spec = ShaftSpec(
            overallLengthMm = 2000f,
            bodies = listOf(
                Body(
                    startFromAftMm = 1400f, lengthMm = 600f, diaMm = 100f,
                    keywayWidthMm = 30f, keywayDepthMm = 15f, keywayLengthMm = 150f,
                    keywayOffsetFromEndMm = 40f,
                    keywayEnd = LinerAuthoredReference.FWD,
                ),
            ),
        )
        val cols = buildFooterEndColumns(spec, UnitSystem.MILLIMETERS, cfg)
        val kwIdx = cols.fwdLines.indexOfFirst { it.startsWith("Body KW: ") }
        assertTrue(kwIdx >= 0)
        val inset = cols.fwdLines[kwIdx + 1]
        assertTrue("got: $inset", inset.startsWith("$CAPTURED_KW_PREFIX inset "))
        assertTrue("got: $inset", inset.contains("40"))
        assertTrue("got: $inset", inset.endsWith(" from FWD"))
    }

    @Test
    fun `a captured AFT-referenced body keyway says from AFT`() {
        val spec = ShaftSpec(
            overallLengthMm = 2000f,
            bodies = listOf(
                Body(
                    startFromAftMm = 0f, lengthMm = 600f, diaMm = 100f,
                    keywayWidthMm = 30f, keywayDepthMm = 15f, keywayLengthMm = 150f,
                    keywayOffsetFromEndMm = 40f,
                    keywayEnd = LinerAuthoredReference.AFT,
                ),
            ),
        )
        val cols = buildFooterEndColumns(spec, UnitSystem.MILLIMETERS, cfg)
        val inset = cols.aftLines.single { it.startsWith(CAPTURED_KW_PREFIX) }
        assertTrue("got: $inset", inset.endsWith(" from AFT"))
    }

    @Test
    fun `the captured inset line follows the setting, on every keyway and on blank drafts`() {
        val spec = ShaftSpec(
            overallLengthMm = 2000f,
            tapers = listOf(spoonedTaper().copy(keywaySpooned = false, keywayOffsetFromSetMm = 25f)),
            bodies = listOf(spoonedBody(spooned = false).copy(keywayOffsetFromEndMm = 40f)),
        )
        val off = cfg.copy(capturedKwInset = false)
        for (blank in listOf(false, true)) {
            val on = buildFooterEndColumns(spec, UnitSystem.MILLIMETERS, cfg, blankValues = blank)
            assertEquals(2, (on.aftLines + on.fwdLines).count { it.startsWith(CAPTURED_KW_PREFIX) })
            val cols = buildFooterEndColumns(spec, UnitSystem.MILLIMETERS, off, blankValues = blank)
            assertTrue((cols.aftLines + cols.fwdLines).none { it.startsWith(CAPTURED_KW_PREFIX) })
            assertTrue(cols.aftLines.any { it.startsWith("KW:") })
            assertTrue(cols.fwdLines.any { it.startsWith("Body KW:") })
        }
    }

    @Test
    fun `the footer config reads the captured inset setting, default on`() {
        val spec = ShaftSpec(overallLengthMm = 2000f)
        assertTrue(sheetFooterConfig(spec).capturedKwInset)
        assertEquals(false, sheetFooterConfig(spec, PdfPrefs(footerCapturedKwInset = false)).capturedKwInset)
    }

    @Test
    fun `an open keyway prints no inset line`() {
        val spec = ShaftSpec(
            overallLengthMm = 2000f,
            tapers = listOf(spoonedTaper().copy(keywaySpooned = false)),
            bodies = listOf(
                Body(
                    startFromAftMm = 1400f, lengthMm = 600f, diaMm = 100f,
                    keywayWidthMm = 30f, keywayDepthMm = 15f, keywayLengthMm = 150f,
                    keywayEnd = LinerAuthoredReference.FWD,
                ),
            ),
        )
        val cols = buildFooterEndColumns(spec, UnitSystem.MILLIMETERS, cfg)
        assertTrue((cols.aftLines + cols.fwdLines).none { it.startsWith(CAPTURED_KW_PREFIX) })
    }
}
