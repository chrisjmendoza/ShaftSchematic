package com.android.shaftschematic.pdf

import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Threads
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * A thread relief prints NO footer line (on-device ruling: the groove is drawing detail only). A
 * relieved thread's footer column is exactly the column it prints without a relief, AFT and FWD
 * blocks alike, standard and blank.
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
    fun `a relieved thread prints the same footer as an unrelieved one`() {
        val plain = buildFooterEndColumns(spec(aftThread(), fwdThread()), UnitSystem.INCHES, cfg)
        val relieved = buildFooterEndColumns(
            spec(aftThread(9.525f, 106.045f), fwdThread(9.525f, 106.045f)), UnitSystem.INCHES, cfg,
        )
        assertEquals(plain.aftLines, relieved.aftLines)
        assertEquals(plain.fwdLines, relieved.fwdLines)
        assertFalse((relieved.aftLines + relieved.fwdLines).any { it.contains("relief", ignoreCase = true) })
    }

    @Test
    fun `a blank draft prints no relief line either`() {
        val cols = buildFooterEndColumns(
            spec(aftThread(9.525f, 106.045f)), UnitSystem.INCHES, cfg, blankValues = true,
        )
        assertFalse(cols.aftLines.any { it.contains("relief", ignoreCase = true) })
    }
}
