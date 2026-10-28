package com.android.shaftschematic.pdf

import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Threads
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The thread's major Ø is the one exception to the three-decimal diameter rule (on-device
 * ruling): a typed `4.5` prints `4.5"` on the footer's "Thread:" line, never `4.500"`. Every
 * other diameter keeps [formatDiaWithUnit]'s whole-or-three-decimals form.
 */
class ThreadDiaFooterFormatTest {

    private val inch = UnitSystem.INCHES

    /** Through the stored Float mm, as a typed inch value actually reaches the footer. */
    private fun storedInches(v: Double) = formatThreadDiaWithUnit((v * 25.4).toFloat().toDouble(), inch)

    @Test
    fun `inch thread diameters drop trailing zeros`() {
        assertEquals("4.5\"", storedInches(4.5))
        assertEquals("3.75\"", storedInches(3.75))
        assertEquals("5.125\"", storedInches(5.125))
        assertEquals("4\"", storedInches(4.0))
    }

    @Test
    fun `more than three decimals still rounds to three`() {
        assertEquals("4.188\"", storedInches(4.1875))
    }

    @Test
    fun `millimeters keep the compact diameter form`() {
        assertEquals("60 mm", formatThreadDiaWithUnit(60.0, UnitSystem.MILLIMETERS))
        assertEquals("63.5 mm", formatThreadDiaWithUnit(63.5, UnitSystem.MILLIMETERS))
    }

    @Test
    fun `dual keeps both terms with their units`() {
        assertEquals("4.5\" [114.3 mm]", formatThreadDiaWithUnitDual(114.3, inch, true))
    }

    @Test
    fun `the footer thread line prints the typed form and other diameters keep three places`() {
        val th = Threads(
            id = "aft", startFromAftMm = 0f, lengthMm = 152.4f, majorDiaMm = 114.3f, pitchMm = 6.35f,
        )
        val cfg = FooterConfig(
            showAftThread = true, showFwdThread = false, showAftTaper = false, showFwdTaper = false,
        )
        val cols = buildFooterEndColumns(
            ShaftSpec(overallLengthMm = 2000f, threads = listOf(th)), inch, cfg,
        )
        val line = cols.aftLines.first { it.startsWith("Thread:") }
        assertTrue(line, line.startsWith("Thread: 4.5\" × "))
        assertEquals("4.500\"", formatDiaWithUnit(114.3, inch))
    }
}
