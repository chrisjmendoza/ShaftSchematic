package com.android.shaftschematic.pdf

import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.Threads
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertTrue
import org.junit.Test

class FooterUnitsTest {

    @Test
    fun `footer length and diameter fields include units in mm and inches`() {
        val th = Threads(
            startFromAftMm = 0f,
            lengthMm = 100f,
            majorDiaMm = 50f,
            pitchMm = 2f,
            excludeFromOAL = false
        )
        val taper = Taper(
            startFromAftMm = 0f,
            lengthMm = 200f,
            startDiaMm = 60f,
            endDiaMm = 40f
        )
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            threads = listOf(th),
            tapers = listOf(taper)
        )

        val cfg = FooterConfig(
            showAftThread = true,
            showFwdThread = false,
            showAftTaper = true,
            showFwdTaper = false,
            showCompressionNote = false
        )

        run {
            val cols = buildFooterEndColumns(spec, UnitSystem.MILLIMETERS, cfg)
            val lines = cols.aftLines.joinToString("\n")
            assertTrue(lines.contains("L.E.T. (AFT): ") && lines.contains(" mm"))
            assertTrue(lines.contains("S.E.T. (FWD): ") && lines.contains(" mm"))
            assertTrue(lines.contains("Length: ") && lines.contains(" mm"))
            assertTrue(lines.contains("Thread: ") && lines.contains(" mm"))
            // Metric mode prints pitch in mm, never TPI.
            assertTrue(lines.contains("mm pitch"))
            assertTrue(!lines.contains("TPI"))
        }

        run {
            val cols = buildFooterEndColumns(spec, UnitSystem.INCHES, cfg)
            val lines = cols.aftLines.joinToString("\n")
            assertTrue(lines.contains("L.E.T. (AFT): ") && lines.contains("\""))
            assertTrue(lines.contains("S.E.T. (FWD): ") && lines.contains("\""))
            assertTrue(lines.contains("Length: ") && lines.contains("\""))
            assertTrue(lines.contains("Thread: ") && lines.contains("\""))
            assertTrue(lines.contains(" TPI "))
        }
    }
}
