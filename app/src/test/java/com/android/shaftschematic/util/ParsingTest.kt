package com.android.shaftschematic.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParsingTest {

    @Test
    fun `an untouched seed saves the stored value, not its rounded display`() {
        val stored = 253.6031f // 9 63/64"
        assertEquals(stored, valueForSave("9.984", edited = false, storedMm = stored) { toMmOrNull(it, UnitSystem.INCHES) }!!, 0f)
    }

    @Test
    fun `an edited text is parsed`() {
        val v = valueForSave("10", edited = true, storedMm = 253.6031f) { toMmOrNull(it, UnitSystem.INCHES) }
        assertEquals(254f, v!!, 1e-4f)
    }

    @Test
    fun `a seed retyped by hand is the typed value, not the stored one`() {
        val v = valueForSave("9.984", edited = true, storedMm = 253.6031f) { toMmOrNull(it, UnitSystem.INCHES) }
        assertEquals(9.984f * 25.4f, v!!, 1e-3f)
    }

    @Test
    fun `a field that opened empty always parses`() {
        assertNull(valueForSave("", edited = false, storedMm = null) { toMmOrNull(it, UnitSystem.INCHES) })
        assertEquals(25.4f, valueForSave("1", edited = false, storedMm = null) { toMmOrNull(it, UnitSystem.INCHES) }!!, 1e-4f)
    }

    @Test
    fun `parseFractionOrDecimal parses mixed fractions`() {
        assertEquals(15.5, parseFractionOrDecimal("15 1/2")!!, 1e-9)
        assertEquals(0.5, parseFractionOrDecimal("1/2")!!, 1e-9)
        assertEquals(-1.5, parseFractionOrDecimal("-1 1/2")!!, 1e-9)
    }

    @Test
    fun `the unit-blind parser refuses any unit suffix rather than dropping it`() {
        // A caller multiplying this result by the document unit would silently lose the suffix.
        assertNull(parseFractionOrDecimal("15 1/2 in"))
        assertNull(parseFractionOrDecimal("15 1/2\""))
        assertNull(parseFractionOrDecimal("25 mm"))
        assertNull(parseFractionOrDecimal("260mm"))
        assertNull(parseFractionOrDecimal("4tpi"))
        assertNull(parseFractionOrDecimal("4in"))
        assertNull(parseFractionOrDecimal("12ft"))
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
    fun `toMmOrNull reads a matching unit suffix`() {
        assertEquals(12.7f, toMmOrNull("1/2\"", UnitSystem.INCHES)!!, 0.0001f)
        assertEquals(25f, toMmOrNull("25 mm", UnitSystem.MILLIMETERS)!!, 0.0001f)
    }

    @Test
    fun `toMmOrNull parses a colon ratio`() {
        assertEquals((1.0 / 12.0).toFloat(), toMmOrNull("1:12", UnitSystem.MILLIMETERS)!!, 0.0001f)
    }

    // ── Unit suffixes convert, whatever the document unit ─────────────────────

    @Test
    fun `a mm suffix in an inch document is millimetres`() {
        assertEquals(260f, toMmOrNull("260mm", UnitSystem.INCHES)!!, 1e-4f)
        assertEquals(260f, toMmOrNull("260 mm", UnitSystem.INCHES)!!, 1e-4f)
        assertEquals(260f, toMmOrNull("260MM", UnitSystem.INCHES)!!, 1e-4f)
        assertEquals(260.0, parseToMm("260mm", UnitSystem.INCHES), 1e-9)
    }

    @Test
    fun `an inch suffix in a mm document is inches`() {
        assertEquals(279.4f, toMmOrNull("11\"", UnitSystem.MILLIMETERS)!!, 1e-4f)
        assertEquals(279.4f, toMmOrNull("11in", UnitSystem.MILLIMETERS)!!, 1e-4f)
        assertEquals(279.4f, toMmOrNull("11 IN", UnitSystem.MILLIMETERS)!!, 1e-4f)
        assertEquals(279.4f, toMmOrNull("11″", UnitSystem.MILLIMETERS)!!, 1e-4f)
        assertEquals(279.4f, toMmOrNull("11''", UnitSystem.MILLIMETERS)!!, 1e-4f)
        assertEquals(260.35f, toMmOrNull("10 1/4\"", UnitSystem.MILLIMETERS)!!, 1e-3f)
        assertEquals(260.35f, toMmOrNull("10-1/4\"", UnitSystem.MILLIMETERS)!!, 1e-3f)
    }

    @Test
    fun `parseLengthInUnit converts a suffix into the entry unit`() {
        assertEquals(20.0 / 25.4, parseLengthInUnit("20mm", UnitSystem.INCHES)!!, 1e-9)
        assertEquals(25.4, parseLengthInUnit("1in", UnitSystem.MILLIMETERS)!!, 1e-9)
        assertEquals(1.5, parseLengthInUnit("1 1/2", UnitSystem.INCHES)!!, 1e-9)
    }

    @Test
    fun `any other trailing letters are not a number`() {
        for (typed in listOf("12ft", "5'", "4tpi", "10 inches", "12x", "1mm in", "1min", "11\"11\"")) {
            assertNull("$typed must not parse", toMmOrNull(typed, UnitSystem.INCHES))
            assertNull("$typed must not parse", toMmOrNull(typed, UnitSystem.MILLIMETERS))
        }
        assertNull(toMmOrNull("mm", UnitSystem.MILLIMETERS))
        assertNull(toMmOrNull("\"", UnitSystem.INCHES))
    }

    @Test
    fun `the field gates follow the two parsers`() {
        assertTrue(isLengthText("260mm"))
        assertTrue(isLengthText("10-1/4\""))
        assertFalse(isLengthText("4tpi"))
        assertFalse(isLengthText(""))
        assertTrue(isUnitlessNumberText("4"))
        assertFalse(isUnitlessNumberText("4in"))
        assertFalse(isUnitlessNumberText("4tpi"))
    }

    // ── Hyphenated mixed fractions ────────────────────────────────────────────

    @Test
    fun `a hyphen between a whole number and a fraction is a separator`() {
        assertEquals(10.25, parseFractionOrDecimal("10-1/4")!!, 1e-9)
        assertEquals(368.5, parseFractionOrDecimal("368-1/2")!!, 1e-9)
        assertEquals(25.1875, parseFractionOrDecimal("25-3/16")!!, 1e-9)
        assertEquals(10.25, parseFractionOrDecimal("10 - 1/4")!!, 1e-9)
        assertEquals(10.25 * 25.4, toMmOrNull("10-1/4", UnitSystem.INCHES)!!.toDouble(), 1e-3)
        assertEquals(10.25 * 25.4, toMmOrNull("10-1/4\"", UnitSystem.INCHES)!!.toDouble(), 1e-3)
        assertEquals(10.25, toMmOrNull("10-1/4", UnitSystem.MILLIMETERS)!!.toDouble(), 1e-6)
    }

    @Test
    fun `a leading hyphen is still a sign`() {
        assertEquals(-0.25, parseFractionOrDecimal("-1/4")!!, 1e-9)
        assertEquals(-10.25, parseFractionOrDecimal("-10-1/4")!!, 1e-9)
        assertEquals(-10.25, parseFractionOrDecimal("-10 1/4")!!, 1e-9)
        assertEquals(-0.5, parseFractionOrDecimal("-0 1/2")!!, 1e-9)
        assertEquals(-3.0, parseFractionOrDecimal("-3")!!, 1e-9)
        assertEquals(-10.25 * 25.4, toMmOrNull("-10-1/4", UnitSystem.INCHES)!!.toDouble(), 1e-3)
    }

    @Test
    fun `a hyphen anywhere else is not a number`() {
        for (typed in listOf("10-", "10--1/4", "1/2-1/4", "10-1", "10-1/", "10.5-1/4", "-")) {
            assertNull("$typed must not parse", parseFractionOrDecimal(typed))
        }
    }

    // ── Commas ────────────────────────────────────────────────────────────────

    @Test
    fun `a lone comma with no dot is a decimal comma`() {
        assertEquals(1.5, parseFractionOrDecimal("1,5")!!, 1e-9)
        assertEquals(12.75, parseFractionOrDecimal("12,75")!!, 1e-9)
        assertEquals(-1.5, parseFractionOrDecimal("-1,5")!!, 1e-9)
        assertEquals(0.5, parseFractionOrDecimal("0,500")!!, 1e-9) // no thousands reading exists
        assertEquals(0.5, parseFractionOrDecimal(",5")!!, 1e-9)
        assertEquals(1.5 * 25.4, toMmOrNull("1,5", UnitSystem.INCHES)!!.toDouble(), 1e-4)
        assertEquals(1.5, toMmOrNull("1,5mm", UnitSystem.INCHES)!!.toDouble(), 1e-6)
        assertEquals(12.75, toMmOrNull("12,75", UnitSystem.MILLIMETERS)!!.toDouble(), 1e-5)
    }

    @Test
    fun `one comma before exactly three digits is ambiguous and refused`() {
        assertNull(parseFractionOrDecimal("1,500"))
        assertNull(parseFractionOrDecimal("12,750"))
        assertNull(parseFractionOrDecimal("-1,500"))
        assertNull(toMmOrNull("1,500", UnitSystem.INCHES))
        assertNull(toMmOrNull("1,500", UnitSystem.MILLIMETERS))
    }

    @Test
    fun `with a dot, commas are thousands groups only`() {
        assertEquals(1234.5, parseFractionOrDecimal("1,234.5")!!, 1e-9)
        assertEquals(12345678.25, parseFractionOrDecimal("12,345,678.25")!!, 1e-6)
        assertNull(parseFractionOrDecimal("1,5.5"))
        assertNull(parseFractionOrDecimal("1.5,5"))
        assertNull(parseFractionOrDecimal("1234,567.5"))
    }

    @Test
    fun `other comma shapes are not a number`() {
        for (typed in listOf("1,2,3", "1,500,000", "1,5/2", "10 1,5/2", "1,")) {
            assertNull("$typed must not parse", parseFractionOrDecimal(typed))
        }
    }
}
