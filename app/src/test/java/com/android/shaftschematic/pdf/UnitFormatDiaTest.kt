package com.android.shaftschematic.pdf

import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Test

/** Diameter print rule: inches are whole or three decimals, never trimmed; mm stays compact. */
class UnitFormatDiaTest {

    private val inch = UnitSystem.INCHES
    private val mm = UnitSystem.MILLIMETERS

    private fun inches(v: Double) = formatDiaWithUnit(v * 25.4, inch)

    @Test
    fun `a value with any decimal keeps all three places`() {
        assertEquals("10.990\"", inches(10.990))
        assertEquals("10.500\"", inches(10.5))
        assertEquals("8.266\"", inches(8.2660))
    }

    @Test
    fun `a whole number prints bare`() {
        assertEquals("11\"", inches(11.0))
        assertEquals("2\"", inches(2.0))
    }

    @Test
    fun `millimeters stay compact`() {
        assertEquals("279.4 mm", formatDiaWithUnit(279.4, mm))
        assertEquals("280 mm", formatDiaWithUnit(280.0, mm))
    }
}
