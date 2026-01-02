package com.android.shaftschematic.pdf

import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.Threads
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertTrue
import org.junit.Test

class FooterOrderTest {

    @Test
    fun `aft footer shows Rate before LET SET Length and threads last`() {
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
            showFwdThread = false,
            showAftTaper = true,
            showFwdTaper = false,
            showCompressionNote = false
        )

        val cols = buildFooterEndColumns(spec, UnitSystem.MILLIMETERS, cfg)
        val lines = cols.aftLines

        val idxRate = lines.indexOfFirst { it.startsWith("Rate: ") }
        val idxLet = lines.indexOfFirst { it.startsWith("L.E.T.: ") }
        val idxSet = lines.indexOfFirst { it.startsWith("S.E.T.: ") }
        val idxLen = lines.indexOfFirst { it.startsWith("Length: ") }
        val idxThread = lines.indexOfFirst { it.startsWith("Thread: ") }

        assertTrue("Expected Rate line", idxRate >= 0)
        assertTrue("Expected L.E.T. line", idxLet >= 0)
        assertTrue("Expected S.E.T. line", idxSet >= 0)
        assertTrue("Expected Length line", idxLen >= 0)
        assertTrue("Expected Thread line", idxThread >= 0)

        assertTrue("Rate should come before LET", idxRate < idxLet)
        assertTrue("LET should come before SET", idxLet < idxSet)
        assertTrue("SET should come before Length", idxSet < idxLen)
        assertTrue("Length should come before Thread", idxLen < idxThread)
    }
}
