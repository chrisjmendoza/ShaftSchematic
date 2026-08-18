package com.android.shaftschematic.pdf

import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Test

class UnitFormatDualTest {

    private val inch = UnitSystem.INCHES
    private val mm = UnitSystem.MILLIMETERS

    @Test
    fun `dual off collapses to the plain single-unit formatters`() {
        val v = 38.1
        assertEquals(formatLenDim(v, inch), formatLenDimDual(v, inch, dual = false))
        assertEquals(formatLenWithUnit(v, mm), formatLenWithUnitDual(v, mm, dual = false))
        assertEquals(formatDiaWithUnit(v, inch), formatDiaWithUnitDual(v, inch, dual = false))
    }

    @Test
    fun `dual appends the other unit in brackets, primary inches`() {
        // 38.1 mm == 1.5" == 1 1/2"
        assertEquals("1 1/2\" [38.1 mm]", formatLenWithUnitDual(38.1, inch, dual = true))
        assertEquals("1.5\" [38.1 mm]", formatDiaWithUnitDual(38.1, inch, dual = true))
    }

    @Test
    fun `dual appends the other unit in brackets, primary mm`() {
        assertEquals("38.1 mm [1 1/2\"]", formatLenWithUnitDual(38.1, mm, dual = true))
        assertEquals("38.1 mm [1.5\"]", formatDiaWithUnitDual(38.1, mm, dual = true))
    }

    @Test
    fun `both terms carry a unit suffix so neither is ambiguous`() {
        val s = formatDiaWithUnitDual(50.8, inch, dual = true)
        // primary declares inches, secondary declares mm — the mixed-sheet safety rule.
        org.junit.Assert.assertTrue(s, s.contains("\"") && s.contains("mm"))
    }
}
