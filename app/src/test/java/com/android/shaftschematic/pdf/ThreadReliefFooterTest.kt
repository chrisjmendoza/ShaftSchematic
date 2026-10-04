package com.android.shaftschematic.pdf

import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Threads
import com.android.shaftschematic.util.DisplayUnits
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The footer's thread-relief line: ONE "Thread relief: <len> × Ø <dia>" line directly under a
 * relieved thread's "Thread:" line, in the thread's own display unit, for the AFT and FWD thread
 * blocks alike; the STORED values print (never the drawn clamps); none without a relief; none on
 * a blank draft.
 */
class ThreadReliefFooterTest {

    private val cfg = FooterConfig(
        showAftThread = true,
        showFwdThread = true,
        showAftTaper = false,
        showFwdTaper = false,
    )

    private fun aftThread(reliefLen: Float = 0f, reliefDia: Float = 0f) = Threads(
        id = "aft", startFromAftMm = 0f, lengthMm = 152.4f, majorDiaMm = 114.3f, pitchMm = 6.35f,
        reliefLenMm = reliefLen, reliefDiaMm = reliefDia,
    )

    private fun fwdThread(reliefLen: Float = 0f, reliefDia: Float = 0f) = Threads(
        id = "fwd", startFromAftMm = 1847.6f, lengthMm = 152.4f, majorDiaMm = 114.3f, pitchMm = 6.35f,
        reliefLenMm = reliefLen, reliefDiaMm = reliefDia, reliefAtAft = true,
    )

    private fun spec(vararg th: Threads) = ShaftSpec(overallLengthMm = 2000f, threads = th.toList())

    @Test
    fun `a relieved aft thread prints its relief line right under the thread line`() {
        val cols = buildFooterEndColumns(spec(aftThread(9.525f, 106.045f)), UnitSystem.INCHES, cfg)
        val i = cols.aftLines.indexOfFirst { it.startsWith("Thread:") }
        assertTrue(i >= 0)
        val len = formatLenWithUnitDual(9.525, UnitSystem.INCHES, false)
        assertEquals("Thread relief: $len × Ø 4.175\"", cols.aftLines[i + 1])
    }

    @Test
    fun `a relieved fwd thread prints its relief line in the fwd block`() {
        val cols = buildFooterEndColumns(spec(fwdThread(9.525f, 106.045f)), UnitSystem.INCHES, cfg)
        val i = cols.fwdLines.indexOfFirst { it.startsWith("Thread:") }
        assertTrue(i >= 0)
        assertTrue(cols.fwdLines[i + 1].startsWith(THREAD_RELIEF_PREFIX))
        assertFalse(cols.aftLines.any { it.startsWith(THREAD_RELIEF_PREFIX) })
    }

    @Test
    fun `no relief line without a relief`() {
        val cols = buildFooterEndColumns(spec(aftThread(), fwdThread()), UnitSystem.INCHES, cfg)
        assertFalse((cols.aftLines + cols.fwdLines).any { it.startsWith(THREAD_RELIEF_PREFIX) })
        // A length with no Ø is no relief either.
        val half = buildFooterEndColumns(spec(aftThread(9.525f, 0f)), UnitSystem.INCHES, cfg)
        assertFalse(half.aftLines.any { it.startsWith(THREAD_RELIEF_PREFIX) })
    }

    @Test
    fun `the relief prints in the thread's own unit`() {
        val units = DisplayUnits(UnitSystem.INCHES, overrides = mapOf("aft" to UnitSystem.MILLIMETERS))
        val cols = buildFooterEndColumns(spec(aftThread(10f, 106f)), UnitSystem.INCHES, cfg, displayUnits = units)
        val line = cols.aftLines.single { it.startsWith(THREAD_RELIEF_PREFIX) }
        assertTrue(line, line.contains("mm"))
        assertFalse(line, line.contains("\""))
    }

    @Test
    fun `the stored values print, never the drawn clamps`() {
        // A relief Ø above the major Ø draws clamped below it; the footer states what was typed.
        val cols = buildFooterEndColumns(spec(aftThread(9.525f, 120f)), UnitSystem.MILLIMETERS, cfg)
        val line = cols.aftLines.single { it.startsWith(THREAD_RELIEF_PREFIX) }
        assertTrue(line, line.contains("120"))
    }

    @Test
    fun `a blank draft prints no relief line`() {
        val cols = buildFooterEndColumns(
            spec(aftThread(9.525f, 106.045f)), UnitSystem.INCHES, cfg, blankValues = true,
        )
        assertTrue(cols.aftLines.contains("Thread:"))
        assertFalse(cols.aftLines.any { it.startsWith(THREAD_RELIEF_PREFIX) })
    }
}
