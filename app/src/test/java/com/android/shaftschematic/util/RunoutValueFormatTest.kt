package com.android.shaftschematic.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [formatRunoutValue] display rules: unit conversion at the edge, trailing-zero trim, and the
 * dropped leading zero before the decimal (so a TIR value fits the runout bubble and reads like
 * a hand-written shop value).
 */
class RunoutValueFormatTest {

    @Test fun `mm value drops the leading zero`() {
        assertEquals(".003", formatRunoutValue(0.003f, UnitSystem.MILLIMETERS))
    }

    @Test fun `inch value drops the leading zero`() {
        // 0.0762 mm == 0.003 in
        assertEquals(".003", formatRunoutValue(0.0762f, UnitSystem.INCHES))
    }

    @Test fun `value at or above one keeps its integer part`() {
        assertEquals("1.5", formatRunoutValue(1.5f, UnitSystem.MILLIMETERS))
    }

    @Test fun `zero stays zero`() {
        assertEquals("0", formatRunoutValue(0f, UnitSystem.MILLIMETERS))
    }

    @Test fun `negative sub-one value keeps the sign and drops the zero`() {
        assertEquals("-.003", formatRunoutValue(-0.003f, UnitSystem.MILLIMETERS))
    }
}
