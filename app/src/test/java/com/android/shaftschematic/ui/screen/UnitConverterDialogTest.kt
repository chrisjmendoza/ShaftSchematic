package com.android.shaftschematic.ui.screen

import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Pure-function tests for [converterResult] — the mm ↔ in calculator's live conversion core. */
class UnitConverterDialogTest {

    @Test
    fun `blank input yields Blank`() {
        assertEquals(ConverterResult.Blank, converterResult("", UnitSystem.INCHES))
        assertEquals(ConverterResult.Blank, converterResult("   ", UnitSystem.MILLIMETERS))
    }

    @Test
    fun `unparseable input yields Invalid, distinct from blank`() {
        val result = converterResult("abc", UnitSystem.INCHES)
        assertEquals(ConverterResult.Invalid, result)
        assertNotEquals(ConverterResult.Blank, result)
    }

    @Test
    fun `mixed fraction inches converts to mm at 3 decimals`() {
        val result = converterResult("1 1/2", UnitSystem.INCHES) as ConverterResult.Value
        assertEquals(listOf("38.100 mm"), result.lines)
    }

    @Test
    fun `simple fraction inches converts to mm`() {
        val result = converterResult("3/4", UnitSystem.INCHES) as ConverterResult.Value
        assertEquals(listOf("19.050 mm"), result.lines)
    }

    @Test
    fun `mm converts to decimal inches plus nearest 64th`() {
        val result = converterResult("25.4", UnitSystem.MILLIMETERS) as ConverterResult.Value
        assertEquals(listOf("1.0000 in", "≈ 1 in"), result.lines)
    }

    @Test
    fun `mm value lands on a reduced fraction of a 64th`() {
        // 12.7 mm = 0.5 in exactly -> nearest 64th reduces to 1/2.
        val result = converterResult("12.7", UnitSystem.MILLIMETERS) as ConverterResult.Value
        assertEquals("0.5000 in", result.lines[0])
        assertEquals("≈ 1/2 in", result.lines[1])
    }

    @Test
    fun `trailing unit suffix is tolerated`() {
        val withSuffix = converterResult("25.4mm", UnitSystem.MILLIMETERS) as ConverterResult.Value
        val bare = converterResult("25.4", UnitSystem.MILLIMETERS) as ConverterResult.Value
        assertEquals(bare.lines, withSuffix.lines)
    }

    @Test
    fun `mixed fraction entry with spaces is tolerated`() {
        val result = converterResult("2 3/8", UnitSystem.INCHES) as ConverterResult.Value
        assertEquals(listOf("60.325 mm"), result.lines)
    }
}
