package com.android.shaftschematic.pdf

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.BoltHoleClocking
import com.android.shaftschematic.model.BoltHoleStyle
import com.android.shaftschematic.model.CouplerBoltSlot
import com.android.shaftschematic.model.LinerAuthoredReference
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.SlotAuthoredReference
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Footer lines for a cross-drilled coupler bolt hole: Ø, the authored center distance, and the
 * clocking note — in the column of the face it was quoted from. Seam rows keep printing nothing.
 */
class CrossDrilledHoleFooterTest {

    private val cfg = FooterConfig(
        showAftThread = false, showFwdThread = false, showAftTaper = false, showFwdTaper = false,
    )

    private fun keyedBody() = Body(
        startFromAftMm = 0f, lengthMm = 600f, diaMm = 100f,
        keywayWidthMm = 30f, keywayDepthMm = 15f, keywayLengthMm = 150f,
        keywayEnd = LinerAuthoredReference.AFT,
    )

    // 2000 OAL, hole center 1900 → 100 mm from the FWD face.
    private fun crossHole(clocking: BoltHoleClocking = BoltHoleClocking.DEG_90) = CouplerBoltSlot(
        startFromAftMm = 1900f, holeDiaMm = 25f, count = 1,
        authoredReference = SlotAuthoredReference.FWD,
        holeStyle = BoltHoleStyle.CROSS, clocking = clocking,
    )

    @Test
    fun `cross-drilled hole quoted from FWD prints its spec in the FWD column`() {
        val spec = ShaftSpec(overallLengthMm = 2000f, bodies = listOf(keyedBody()), couplerBoltSlots = listOf(crossHole()))
        val cols = buildFooterEndColumns(spec, UnitSystem.MILLIMETERS, cfg)
        val i = cols.fwdLines.indexOfFirst { it.startsWith("Bolt hole: Ø ") }
        assertTrue(i >= 0)
        assertTrue(cols.fwdLines[i].contains("25"))
        assertTrue(cols.fwdLines[i + 1].startsWith("Hole center from FWD: "))
        assertTrue(cols.fwdLines[i + 1].contains("100"))
        assertEquals(BOLT_HOLE_90_NOTE, cols.fwdLines[i + 2])
        assertTrue(cols.aftLines.none { it.startsWith("Bolt hole") })
    }

    @Test
    fun `in-line clocking prints its own note and AFT reference lands in the AFT column`() {
        val hole = crossHole(BoltHoleClocking.IN_LINE).copy(startFromAftMm = 100f, authoredReference = SlotAuthoredReference.AFT)
        val spec = ShaftSpec(overallLengthMm = 2000f, bodies = listOf(keyedBody()), couplerBoltSlots = listOf(hole))
        val cols = buildFooterEndColumns(spec, UnitSystem.MILLIMETERS, cfg)
        val i = cols.aftLines.indexOfFirst { it.startsWith("Bolt hole: ") }
        assertTrue(i >= 0)
        assertTrue(cols.aftLines[i + 1].startsWith("Hole center from AFT: "))
        assertEquals(BOLT_HOLE_IN_LINE_NOTE, cols.aftLines[i + 2])
    }

    @Test
    fun `no keyway on the shaft means no clocking note`() {
        val spec = ShaftSpec(overallLengthMm = 2000f, couplerBoltSlots = listOf(crossHole()))
        val cols = buildFooterEndColumns(spec, UnitSystem.MILLIMETERS, cfg)
        assertTrue(cols.fwdLines.any { it.startsWith("Bolt hole: ") })
        assertTrue(cols.fwdLines.none { it == BOLT_HOLE_90_NOTE || it == BOLT_HOLE_IN_LINE_NOTE })
    }

    @Test
    fun `a row of holes carries its count and pitch`() {
        val row = crossHole().copy(startFromAftMm = 1800f, count = 3, spacingMm = 50f)
        val spec = ShaftSpec(overallLengthMm = 2000f, couplerBoltSlots = listOf(row))
        val cols = buildFooterEndColumns(spec, UnitSystem.MILLIMETERS, cfg)
        assertTrue(cols.fwdLines.any { it.startsWith("Bolt hole: Ø ") && it.endsWith("× 3") })
        // fwd-most center 1900 → 100 from FWD, pitch 50.
        val center = cols.fwdLines.first { it.startsWith("Hole center from FWD: ") }
        assertTrue(center.contains("100"))
        assertTrue(center.contains("@ ") && center.contains("50"))
    }

    @Test
    fun `blank draft keeps the labels and the clocking note`() {
        val spec = ShaftSpec(overallLengthMm = 2000f, bodies = listOf(keyedBody()), couplerBoltSlots = listOf(crossHole()))
        val cols = buildFooterEndColumns(spec, UnitSystem.MILLIMETERS, cfg, blankValues = true)
        val i = cols.fwdLines.indexOf("Bolt hole:")
        assertTrue(i >= 0)
        assertEquals("Hole center from FWD:", cols.fwdLines[i + 1])
        assertEquals(BOLT_HOLE_90_NOTE, cols.fwdLines[i + 2])
    }

    @Test
    fun `seam rows still print nothing`() {
        val seam = crossHole().copy(holeStyle = BoltHoleStyle.SEAM)
        val spec = ShaftSpec(overallLengthMm = 2000f, bodies = listOf(keyedBody()), couplerBoltSlots = listOf(seam))
        val cols = buildFooterEndColumns(spec, UnitSystem.MILLIMETERS, cfg)
        assertTrue((cols.aftLines + cols.fwdLines).none { it.startsWith("Bolt hole") || it.startsWith("Hole center") })
    }
}
