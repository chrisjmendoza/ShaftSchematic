package com.android.shaftschematic.pdf

import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.Threads
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertTrue
import org.junit.Test

class FooterEndDetectionTest {

    @Test
    fun `aft taper is considered present when it starts at excluded end-thread shoulder`() {
        val th = Threads(
            startFromAftMm = 0f,
            lengthMm = 100f,
            majorDiaMm = 50f,
            pitchMm = 2f,
            excludeFromOAL = true
        )
        val taper = Taper(
            startFromAftMm = 100f,
            lengthMm = 200f,
            startDiaMm = 50f,
            endDiaMm = 40f
        )
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            threads = listOf(th),
            tapers = listOf(taper)
        )

        val cfg = FooterConfig(
            showAftThread = true,
            showFwdThread = true,
            showAftTaper = true,
            showFwdTaper = true,
            showCompressionNote = false
        )

        val cols = buildFooterEndColumns(spec, UnitSystem.MILLIMETERS, cfg)

        // If this is present, the footer will render the full AFT taper block.
        assertTrue(cols.aftLines.firstOrNull() == "AFT Taper")
        assertTrue(cols.aftLines.any { it.startsWith("Rate: ") })
    }

    @Test
    fun `aft taper is considered present when it starts at included end-thread shoulder`() {
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
            endDiaMm = 40f
        )
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            threads = listOf(th),
            tapers = listOf(taper)
        )

        val cfg = FooterConfig(
            showAftThread = true,
            showFwdThread = true,
            showAftTaper = true,
            showFwdTaper = true,
            showCompressionNote = false
        )

        val cols = buildFooterEndColumns(spec, UnitSystem.MILLIMETERS, cfg)

        assertTrue(cols.aftLines.firstOrNull() == "AFT Taper")
        assertTrue(cols.aftLines.any { it.startsWith("Rate: ") })
    }
}
