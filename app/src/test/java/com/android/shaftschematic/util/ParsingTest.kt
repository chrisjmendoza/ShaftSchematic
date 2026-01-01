package com.android.shaftschematic.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ParsingTest {

    @Test
    fun `parseFractionOrDecimal parses mixed fractions`() {
        assertEquals(15.5, parseFractionOrDecimal("15 1/2")!!, 1e-9)
        assertEquals(0.5, parseFractionOrDecimal("1/2")!!, 1e-9)
        assertEquals(-1.5, parseFractionOrDecimal("-1 1/2")!!, 1e-9)
    }

    @Test
    fun `parseFractionOrDecimal tolerates unit suffixes`() {
        assertEquals(15.5, parseFractionOrDecimal("15 1/2 in")!!, 1e-9)
        assertEquals(15.5, parseFractionOrDecimal("15 1/2\"")!!, 1e-9)
        assertEquals(25.0, parseFractionOrDecimal("25 mm")!!, 1e-9)
    }

    @Test
    fun `parseToMm converts inches to mm`() {
        assertEquals(15.5 * 25.4, parseToMm("15 1/2", UnitSystem.INCHES), 1e-6)
        assertEquals(12.7, parseToMm("1/2\"", UnitSystem.INCHES), 1e-6)
    }
}
