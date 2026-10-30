package com.android.shaftschematic.pdf

import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Test

/** Printed inch lengths: fractions to the nearest 1/32, three decimals off that grid. */
class UnitFormatLenTest {

    private val inch = UnitSystem.INCHES

    /** Through the stored Float mm, as a typed value reaches the sheet. */
    private fun stored(v: Double) = (v * 25.4).toFloat().toDouble()

    @Test
    fun `a 32nd prints as its fraction on rails and footer lines`() {
        assertEquals("12 11/32\"", formatLenDim(stored(12.34375), inch))
        assertEquals("12 11/32\"", formatLenWithUnit(stored(12.34375), inch))
        assertEquals("1/32\"", formatLenDim(stored(1.0 / 32), inch))
    }

    @Test
    fun `sixteenths and halves stay reduced`() {
        assertEquals("11 1/2\"", formatLenDim(stored(11.5), inch))
        assertEquals("15/16\"", formatLenDim(stored(0.9375), inch))
        assertEquals("14\"", formatLenDim(stored(14.0), inch))
    }

    @Test
    fun `off the grid prints three decimals`() {
        assertEquals("1.505\"", formatLenDim(stored(1.505), inch))
        assertEquals("0.016\"", formatLenDim(stored(1.0 / 64), inch))
    }
}
