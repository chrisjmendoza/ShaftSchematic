package com.android.shaftschematic.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [formatRunoutValue] display rules: unit conversion at the edge, fixed 3-decimal (thousandths)
 * precision with trailing zeros kept, and the dropped leading zero before the decimal (so a TIR
 * value fits the runout bubble and reads like a hand-written shop value).
 */
class RunoutValueFormatTest {

    // ── parseRunoutValueInput — what the bubble editor's Save reads ───────────────────────

    @Test fun `a fraction saves as its mm value in inches`() {
        // The reported case: the field admitted "1/64" and Save dropped it.
        assertEquals(25.4f / 64f, parseRunoutValueInput("1/64", UnitSystem.INCHES)!!, 1e-5f)
        assertEquals(25.4f * 1.5f, parseRunoutValueInput("1 1/2", UnitSystem.INCHES)!!, 1e-4f)
    }

    @Test fun `a decimal saves in either unit`() {
        assertEquals(0.254f, parseRunoutValueInput(".010", UnitSystem.INCHES)!!, 1e-5f)
        assertEquals(0.01f, parseRunoutValueInput("0.010", UnitSystem.MILLIMETERS)!!, 1e-6f)
    }

    @Test fun `a unit suffix converts instead of being refused`() {
        assertEquals(0.127f, parseRunoutValueInput(".005\"", UnitSystem.INCHES)!!, 1e-5f)
        assertEquals(0.127f, parseRunoutValueInput("0.005in", UnitSystem.MILLIMETERS)!!, 1e-5f)
        assertEquals(0.13f, parseRunoutValueInput("0.13mm", UnitSystem.INCHES)!!, 1e-5f)
    }

    @Test fun `blank and unreadable text read as nothing`() {
        assertNull(parseRunoutValueInput("", UnitSystem.INCHES))
        assertNull(parseRunoutValueInput("   ", UnitSystem.INCHES))
        assertNull(parseRunoutValueInput("1/", UnitSystem.INCHES))
        assertNull(parseRunoutValueInput("abc", UnitSystem.MILLIMETERS))
    }

    @Test fun `a saved fraction formats back the way it was read`() {
        val mm = parseRunoutValueInput("1/64", UnitSystem.INCHES)!!
        assertEquals(".016", formatRunoutValue(mm, UnitSystem.INCHES))
    }

    // ── formatRunoutValue ────────────────────────────────────────────────────────────────

    @Test fun `mm value drops the leading zero`() {
        assertEquals(".003", formatRunoutValue(0.003f, UnitSystem.MILLIMETERS))
    }

    @Test fun `inch value drops the leading zero`() {
        // 0.0762 mm == 0.003 in
        assertEquals(".003", formatRunoutValue(0.0762f, UnitSystem.INCHES))
    }

    @Test fun `trailing zeros are kept to three decimals`() {
        // The reported case: .010 must stay .010, not shrink to .01.
        assertEquals(".010", formatRunoutValue(0.010f, UnitSystem.MILLIMETERS))
        // 0.254 mm == 0.010 in
        assertEquals(".010", formatRunoutValue(0.254f, UnitSystem.INCHES))
    }

    @Test fun `value at or above one keeps its integer part with three decimals`() {
        assertEquals("1.500", formatRunoutValue(1.5f, UnitSystem.MILLIMETERS))
    }

    @Test fun `zero prints three decimals`() {
        assertEquals(".000", formatRunoutValue(0f, UnitSystem.MILLIMETERS))
    }

    @Test fun `negative sub-one value keeps the sign and drops the zero`() {
        assertEquals("-.003", formatRunoutValue(-0.003f, UnitSystem.MILLIMETERS))
    }
}
