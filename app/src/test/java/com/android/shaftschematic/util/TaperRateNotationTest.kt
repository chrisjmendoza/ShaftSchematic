package com.android.shaftschematic.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Taper rate notation — per-foot entry and print (on-device request: "3 3/4", not "1:3.2"). */
class TaperRateNotationTest {

    private val IN = 25.4f

    @Test
    fun `a bare number under in-ft is spelled out with its digits kept`() {
        assertEquals("3 3/4\"/ft", normalizeTaperRateEntry("3 3/4", TaperRateEntry.PER_FOOT))
        assertEquals("3/4\"/ft", normalizeTaperRateEntry(" 3/4 ", TaperRateEntry.PER_FOOT))
        assertEquals(".75\"/ft", normalizeTaperRateEntry(".75", TaperRateEntry.PER_FOOT))
        assertEquals("3 3/4\"/ft", normalizeTaperRateEntry("3 3/4\"", TaperRateEntry.PER_FOOT))
        assertEquals("1\"/ft", normalizeTaperRateEntry("1", TaperRateEntry.PER_FOOT))
    }

    @Test
    fun `a spelling that names its notation, blank and junk are left alone`() {
        assertEquals("1:12", normalizeTaperRateEntry("1:12", TaperRateEntry.PER_FOOT))
        assertEquals("3/4 in/ft", normalizeTaperRateEntry("3/4 in/ft", TaperRateEntry.PER_FOOT))
        assertEquals("", normalizeTaperRateEntry("  ", TaperRateEntry.PER_FOOT))
        assertEquals("abc", normalizeTaperRateEntry("abc", TaperRateEntry.PER_FOOT))
        assertEquals("0", normalizeTaperRateEntry("0", TaperRateEntry.PER_FOOT))
        // Under 1:N nothing changes — the field reads as it always did.
        assertEquals("3 3/4", normalizeTaperRateEntry("3 3/4", TaperRateEntry.RATIO))
    }

    @Test
    fun `3 3-4 per foot is the 1 to 3-2 taper`() {
        val slope = parseTaperRateText(normalizeTaperRateEntry("3 3/4", TaperRateEntry.PER_FOOT), false)!!
        assertEquals(1f / 3.2f, slope, 1e-6f)
    }

    @Test
    fun `per foot entry exists only on an inch drawing`() {
        assertEquals(TaperRateEntry.RATIO, effectiveTaperRateEntry(TaperRateEntry.PER_FOOT, UnitSystem.MILLIMETERS))
        assertEquals(TaperRateEntry.PER_FOOT, effectiveTaperRateEntry(TaperRateEntry.PER_FOOT, UnitSystem.INCHES))
    }

    @Test
    fun `chips open on a typed notation, else on the print setting`() {
        assertEquals(TaperRateEntry.RATIO, initialTaperRateEntry("1:12", manual = true, printPerFoot = true))
        assertEquals(TaperRateEntry.PER_FOOT, initialTaperRateEntry("3/4\"/ft", manual = true, printPerFoot = false))
        assertEquals(TaperRateEntry.PER_FOOT, initialTaperRateEntry("1:3.210", manual = false, printPerFoot = true))
        assertEquals(TaperRateEntry.RATIO, initialTaperRateEntry("", manual = false, printPerFoot = false))
    }

    @Test
    fun `an auto rate near an eighth prints as it`() {
        // The on-device taper: 8.980 / 6.011 over 9.531 → 3.738"/ft → 3 3/4"/ft (0.3% off).
        assertEquals("3 3/4\"/ft", autoTaperRatePerFootText(9.531f * IN, 6.011f * IN, 8.980f * IN))
    }

    @Test
    fun `an auto rate on a common ratio prints that ratio per foot`() {
        // 1:12.2 is within 3% of 1:12, so it prints 1"/ft like the ratio sheet's 1:12.
        assertEquals("1\"/ft", autoTaperRatePerFootText(12.2f * IN, 7f * IN, 8f * IN))
        assertEquals("3/4\"/ft", autoTaperRatePerFootText(16f * IN, 5f * IN, 6f * IN))
    }

    @Test
    fun `an auto rate far from an eighth prints its decimal`() {
        // 1" of taper over 4.444" is 2.700"/ft — 1.8% from 2 3/4, 2.9% from 2 5/8, on no 1/32 step.
        assertEquals("2.700\"/ft", autoTaperRatePerFootText(4.444f * IN, 5f * IN, 6f * IN))
        assertNull(autoTaperRatePerFootText(10f * IN, 6f * IN, 6f * IN))
    }

    @Test
    fun `a typed rate converts exactly and a typed per-foot keeps its number`() {
        assertEquals("3 3/4\"/ft", typedTaperRatePerFootText("1:3.2"))
        assertEquals("1.200\"/ft", typedTaperRatePerFootText("1:10"))
        assertEquals("1\"/ft", typedTaperRatePerFootText("1:12"))
        // Never snapped: 1:3.21 is 3.738"/ft, not 3 3/4.
        assertEquals("3.738\"/ft", typedTaperRatePerFootText("1:3.21"))
        assertEquals("3.74\"/ft", typedTaperRatePerFootText("3.74 in/ft"))
        assertNull(typedTaperRatePerFootText("abc"))
    }

    @Test
    fun `auto mode rule is shared`() {
        assertTrue(taperRateModeIsAuto(false, "1:3", "1:12"))
        assertTrue(taperRateModeIsAuto(null, "", null))
        assertTrue(!taperRateModeIsAuto(true, "1:12", "1:12"))
        assertTrue(!taperRateModeIsAuto(null, "1:10", "1:12"))
    }
}
