package com.android.shaftschematic.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [formatRunoutValue] display rules: unit conversion at the edge, fixed 3-decimal (thousandths)
 * precision with trailing zeros kept, and the dropped leading zero before the decimal (so a TIR
 * value fits the runout bubble and reads like a hand-written shop value).
 */
class RunoutValueFormatTest {

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
