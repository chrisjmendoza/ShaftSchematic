package com.android.shaftschematic.pdf

import com.android.shaftschematic.geom.computeOalWindow
import com.android.shaftschematic.model.BoltHoleStyle
import com.android.shaftschematic.model.CouplerBoltSlot
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.SlotAuthoredReference
import com.android.shaftschematic.model.Threads
import com.android.shaftschematic.pdf.dim.SpanKind
import com.android.shaftschematic.util.DisplayUnits
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `buildCouplerSlotSpans` — the coupler bolt row's opt-in dimension rail. ONE LOCAL span per
 * row with "Show dimension rail" on, from the shaft end the row was measured from to the
 * nearest hole center, labelled with the stored distance. Off (the default) contributes
 * nothing, so untouched sheets print byte-identically. Shared by the schematic and the
 * consolidated sheet.
 */
class CouplerSlotDimSpanTest {

    private val inch = 25.4f
    private val oal = 180f * inch

    private fun fwdRow(show: Boolean) = CouplerBoltSlot(
        id = "S1", startFromAftMm = 172f * inch, holeDiaMm = 1f * inch, count = 3, spacingMm = 3f * inch,
        authoredReference = SlotAuthoredReference.FWD, showDimensionRail = show,
    )

    private fun aftHole(show: Boolean) = CouplerBoltSlot(
        id = "S2", startFromAftMm = 16f * inch, holeDiaMm = 0.875f * inch, count = 1,
        authoredReference = SlotAuthoredReference.AFT, holeStyle = BoltHoleStyle.CROSS, showDimensionRail = show,
    )

    @Test
    fun `off by default - no span`() {
        val spec = ShaftSpec(overallLengthMm = oal, couplerBoltSlots = listOf(fwdRow(false), aftHole(false)))
        assertTrue(buildCouplerSlotSpans(spec, computeOalWindow(spec), UnitSystem.INCHES).isEmpty())
        assertTrue("the model default is off", !CouplerBoltSlot().showDimensionRail)
    }

    @Test
    fun `FWD row - from the FWD face to the fwd-most hole center, labelled with the stored distance`() {
        val spec = ShaftSpec(overallLengthMm = oal, couplerBoltSlots = listOf(fwdRow(true)))
        val span = buildCouplerSlotSpans(spec, computeOalWindow(spec), UnitSystem.INCHES).single()
        assertEquals(SpanKind.LOCAL, span.kind)
        val lo = minOf(span.x1Mm, span.x2Mm); val hi = maxOf(span.x1Mm, span.x2Mm)
        assertEquals("fwd-most center (172 + 2·3 = 178\")", (178f * inch).toDouble(), lo, 1e-3)
        assertEquals("the FWD face, not a SET", oal.toDouble(), hi, 1e-3)
        // The stored distance: OAL − last center = 2".
        assertEquals(formatLenDim((2f * inch).toDouble(), UnitSystem.INCHES), span.label.inline())
    }

    @Test
    fun `AFT hole - from the AFT face to the aft-most center`() {
        val spec = ShaftSpec(overallLengthMm = oal, couplerBoltSlots = listOf(aftHole(true)))
        val span = buildCouplerSlotSpans(spec, computeOalWindow(spec), UnitSystem.INCHES).single()
        val lo = minOf(span.x1Mm, span.x2Mm); val hi = maxOf(span.x1Mm, span.x2Mm)
        assertEquals(0.0, lo, 1e-3)
        assertEquals((16f * inch).toDouble(), hi, 1e-3)
        assertEquals(formatLenDim((16f * inch).toDouble(), UnitSystem.INCHES), span.label.inline())
    }

    @Test
    fun `one span per row that is on, the rest contribute nothing`() {
        val spec = ShaftSpec(overallLengthMm = oal, couplerBoltSlots = listOf(fwdRow(true), aftHole(false)))
        assertEquals(1, buildCouplerSlotSpans(spec, computeOalWindow(spec), UnitSystem.INCHES).size)
    }

    @Test
    fun `the label takes the row's own display unit`() {
        val spec = ShaftSpec(overallLengthMm = oal, couplerBoltSlots = listOf(aftHole(true)))
        val units = DisplayUnits(
            documentUnit = UnitSystem.INCHES, overrides = mapOf("S2" to UnitSystem.MILLIMETERS), dual = false,
        )
        val span = buildCouplerSlotSpans(spec, computeOalWindow(spec), UnitSystem.INCHES, units).single()
        assertEquals(formatLenDim((16f * inch).toDouble(), UnitSystem.MILLIMETERS), span.label.inline())
    }

    /** The span rides the physical coordinates like the taper spans — an excluded thread moves nothing. */
    @Test
    fun `an excluded AFT thread does not shift the span`() {
        val spec = ShaftSpec(
            overallLengthMm = oal,
            threads = listOf(Threads(startFromAftMm = -4f * inch, lengthMm = 4f * inch, majorDiaMm = 50f, pitchMm = 2f, excludeFromOAL = true, isAftEnd = true)),
            couplerBoltSlots = listOf(aftHole(true)),
        )
        val win = computeOalWindow(spec)
        val span = buildCouplerSlotSpans(spec, win, UnitSystem.INCHES).single()
        assertEquals(win.toMeasureX(0.0), minOf(span.x1Mm, span.x2Mm), 1e-3)
        assertEquals(win.toMeasureX((16f * inch).toDouble()), maxOf(span.x1Mm, span.x2Mm), 1e-3)
    }

    @Test
    fun `a hole centered on the face has nothing to dimension`() {
        val spec = ShaftSpec(overallLengthMm = oal, couplerBoltSlots = listOf(aftHole(true).copy(startFromAftMm = 0f)))
        assertTrue(buildCouplerSlotSpans(spec, computeOalWindow(spec), UnitSystem.INCHES).isEmpty())
    }
}
