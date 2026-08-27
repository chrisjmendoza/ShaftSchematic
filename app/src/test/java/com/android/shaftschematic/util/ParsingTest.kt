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

    @Test
    fun `parseFractionOrDecimal parses a colon ratio`() {
        assertEquals(1.0 / 12.0, parseFractionOrDecimal("1:12")!!, 1e-9)
    }

    // ── toMmOrNull ────────────────────────────────────────────────────────────

    @Test
    fun `toMmOrNull blank returns null`() {
        assertEquals(null, toMmOrNull("", UnitSystem.INCHES))
        assertEquals(null, toMmOrNull("   ", UnitSystem.MILLIMETERS))
    }

    @Test
    fun `toMmOrNull invalid returns null`() {
        assertEquals(null, toMmOrNull("abc", UnitSystem.MILLIMETERS))
    }

    @Test
    fun `toMmOrNull mm passthrough`() {
        assertEquals(127f, toMmOrNull("127", UnitSystem.MILLIMETERS))
    }

    @Test
    fun `toMmOrNull converts a mixed fraction of inches`() {
        // 15 1/2" = 15.5" -> 393.7 mm
        assertEquals(15.5f * 25.4f, toMmOrNull("15 1/2", UnitSystem.INCHES)!!, 0.0001f)
    }

    @Test
    fun `toMmOrNull tolerates a trailing unit suffix`() {
        assertEquals(12.7f, toMmOrNull("1/2\"", UnitSystem.INCHES)!!, 0.0001f)
        assertEquals(25f, toMmOrNull("25 mm", UnitSystem.MILLIMETERS)!!, 0.0001f)
    }

    @Test
    fun `toMmOrNull parses a colon ratio`() {
        assertEquals((1.0 / 12.0).toFloat(), toMmOrNull("1:12", UnitSystem.MILLIMETERS)!!, 0.0001f)
    }
}
