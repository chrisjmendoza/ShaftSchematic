package com.android.shaftschematic.util

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for UnitSystem conversions.
 */
class UnitSystemTest {

    @Test
    fun `MILLIMETERS to millimeters is identity`() {
        val mm = 100.0
        assertEquals(mm, UnitSystem.MILLIMETERS.toMillimeters(mm), 0.001)
    }

    @Test
    fun `MILLIMETERS from millimeters is identity`() {
        val mm = 100.0
        assertEquals(mm, UnitSystem.MILLIMETERS.fromMillimeters(mm), 0.001)
    }

    @Test
    fun `INCHES to millimeters multiplies by 25 point 4`() {
        val inches = 10.0
        val expected = inches * 25.4
        assertEquals(expected, UnitSystem.INCHES.toMillimeters(inches), 0.001)
    }

    @Test
    fun `INCHES from millimeters divides by 25 point 4`() {
        val mm = 254.0
        val expected = mm / 25.4
        assertEquals(expected, UnitSystem.INCHES.fromMillimeters(mm), 0.001)
    }

    @Test
    fun `round trip conversion preserves value`() {
        val original = 123.45
        val converted = UnitSystem.INCHES.fromMillimeters(
            UnitSystem.INCHES.toMillimeters(original)
        )
        assertEquals(original, converted, 0.0001)
    }

    @Test
    fun `display names are correct`() {
        assertEquals("Inches", UnitSystem.INCHES.displayName)
        assertEquals("Millimeters", UnitSystem.MILLIMETERS.displayName)
    }
}
