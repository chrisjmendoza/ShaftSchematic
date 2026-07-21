package com.android.shaftschematic.pdf

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.LinerAuthoredReference
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Footer lines for body-hosted keyways: each lands in the column matching the keyway's
 * physical half of the shaft (an intermediate shaft with fitted couplings can carry one
 * at each end, 180° apart).
 */
class BodyKeywayFooterTest {

    private val cfg = FooterConfig(
        showAftThread = false,
        showFwdThread = false,
        showAftTaper = false,
        showFwdTaper = false,
        showCompressionNote = false,
    )

    private fun keyedBody(startMm: Float, lengthMm: Float, end: LinerAuthoredReference) = Body(
        startFromAftMm = startMm, lengthMm = lengthMm, diaMm = 100f,
        keywayWidthMm = 30f, keywayDepthMm = 15f, keywayLengthMm = 150f,
        keywayEnd = end,
    )

    @Test
    fun `aft body keyway lands in the AFT column`() {
        val spec = ShaftSpec(
            overallLengthMm = 2000f,
            bodies = listOf(keyedBody(0f, 600f, LinerAuthoredReference.AFT)),
        )
        val cols = buildFooterEndColumns(spec, UnitSystem.MILLIMETERS, cfg)
        assertTrue(cols.aftLines.any { it.startsWith("Body KW: ") })
        assertTrue(cols.fwdLines.none { it.startsWith("Body KW: ") })
    }

    @Test
    fun `fwd body keyway lands in the FWD column`() {
        val spec = ShaftSpec(
            overallLengthMm = 2000f,
            bodies = listOf(keyedBody(1400f, 600f, LinerAuthoredReference.FWD)),
        )
        val cols = buildFooterEndColumns(spec, UnitSystem.MILLIMETERS, cfg)
        assertTrue(cols.fwdLines.any { it.startsWith("Body KW: ") })
        assertTrue(cols.aftLines.none { it.startsWith("Body KW: ") })
    }

    @Test
    fun `one keyway per end for a double-keyed intermediate shaft`() {
        val spec = ShaftSpec(
            overallLengthMm = 2000f,
            bodies = listOf(
                keyedBody(0f, 600f, LinerAuthoredReference.AFT),
                keyedBody(1400f, 600f, LinerAuthoredReference.FWD),
            ),
            keyways180Apart = true,
        )
        val cols = buildFooterEndColumns(spec, UnitSystem.MILLIMETERS, cfg)
        assertTrue(cols.aftLines.count { it.startsWith("Body KW: ") } == 1)
        assertTrue(cols.fwdLines.count { it.startsWith("Body KW: ") } == 1)
    }
}
