package com.android.shaftschematic.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The inch-field formatter: a value on the 1/32 grid reads as the fraction it was typed as (or its
 * exact decimal), never as a rounded decimal; anything else reads as a decimal in both styles.
 */
class InchFieldFormatTest {

    /** A typed inch value, through the same parse and `Float` storage a field commit takes. */
    private fun stored(typed: String): Double =
        parseLengthMmOrNull(typed, UnitSystem.INCHES)!!.toFloat().toDouble() / 25.4

    private fun frac(typed: String) = inchFieldText(stored(typed), InchFieldStyle.FRACTION)
    private fun dec(typed: String) = inchFieldText(stored(typed), InchFieldStyle.DECIMAL)

    @Test
    fun `a typed fraction reads back as itself`() {
        assertEquals("12 11/32", frac("12 11/32"))
        assertEquals("15/16", frac("15/16"))
        assertEquals("3/4", frac("3/4"))
        assertEquals("368 1/2", frac("368-1/2"))
        assertEquals("1/32", frac("1/32"))
    }

    @Test
    fun `fractions are reduced and whole numbers print bare`() {
        assertEquals("1/2", frac("16/32"))
        assertEquals("12", frac("12"))
        assertEquals("0", frac("0"))
    }

    @Test
    fun `decimal style shows the exact decimal, never a rounded one`() {
        assertEquals("12.34375", dec("12 11/32"))
        assertEquals("0.9375", dec("15/16"))
        assertEquals("0.03125", dec("1/32"))
        assertEquals("12", dec("12"))
    }

    @Test
    fun `a value off the grid stays a decimal in both styles`() {
        assertEquals("10.99", frac("10.990"))
        assertEquals("10.99", dec("10.990"))
        assertEquals("0.0156", frac("1/64"))
        assertEquals("1.505", frac("1.505"))
    }

    @Test
    fun `a typed decimal that is a 32nd reads as that fraction`() {
        // The style decides values where both spellings are the same number.
        assertEquals("1/2", frac("0.5"))
        assertEquals("0.5", dec("0.5"))
    }

    @Test
    fun `a three-place decimal near a 32nd is not snapped`() {
        assertEquals("0.031", frac("0.031"))
        assertEquals("12.344", frac("12.344"))
    }

    @Test
    fun `a fraction re-derived at shaft scale still reads as its fraction`() {
        // A FWD-referenced distance is OAL minus a stored start: both carry Float noise.
        val oalMm = (368.5 * 25.4).toFloat()
        val startMm = oalMm - (12.34375 * 25.4).toFloat()
        val distIn = (oalMm - startMm).toDouble() / 25.4
        assertEquals("12 11/32", inchFieldText(distIn, InchFieldStyle.FRACTION))
    }

    @Test
    fun `negatives keep their sign and never print negative zero`() {
        assertEquals("-1 1/4", inchFieldText(-1.25, InchFieldStyle.FRACTION))
        assertEquals("0", inchFieldText(-0.0, InchFieldStyle.FRACTION))
        assertEquals("0", inchFieldText(-0.00001, InchFieldStyle.DECIMAL))
    }

    @Test
    fun `every form parses back to the stored value`() {
        for (typed in listOf("12 11/32", "15/16", "10.990", "0.5", "368 1/2", "1/64")) {
            for (style in InchFieldStyle.entries) {
                val text = inchFieldText(stored(typed), style)
                val back = parseLengthMmOrNull(text, UnitSystem.INCHES)!!.toFloat()
                val want = parseLengthMmOrNull(typed, UnitSystem.INCHES)!!.toFloat()
                if (typed == "1/64") {
                    // Off-grid: four decimals, the field's precision before this formatter.
                    assertEquals(want, back, 0.0025f)
                } else {
                    assertEquals("$typed as $style ($text)", want, back, 0f)
                }
            }
        }
    }

    @Test
    fun `the setting name round-trips and an unknown name falls back to fractions`() {
        assertEquals(InchFieldStyle.DECIMAL, InchFieldStyle.fromName("DECIMAL"))
        assertEquals(InchFieldStyle.FRACTION, InchFieldStyle.fromName(null))
        assertEquals(InchFieldStyle.FRACTION, InchFieldStyle.fromName("bogus"))
    }
}
