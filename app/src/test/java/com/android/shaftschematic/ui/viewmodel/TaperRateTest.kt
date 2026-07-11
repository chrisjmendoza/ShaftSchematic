package com.android.shaftschematic.ui.viewmodel

import org.junit.Test
import org.junit.Assert.*

class TaperRateTest {

    // ─────────────────────────────────────────────
    // parseRateText
    // ─────────────────────────────────────────────

    @Test fun parseRateText_colon() {
        val r = ShaftViewModel.parseRateText("1:12")
        assertNotNull(r)
        assertEquals(1f / 12f, r!!, 1e-5f)
    }

    @Test fun parseRateText_slash() {
        val r = ShaftViewModel.parseRateText("3/4")
        assertNotNull(r)
        assertEquals(0.75f, r!!, 1e-5f)
    }

    @Test fun parseRateText_decimal() {
        val r = ShaftViewModel.parseRateText("0.0833")
        assertNotNull(r)
        assertEquals(0.0833f, r!!, 1e-4f)
    }

    @Test fun parseRateText_bareInt_treatedAs1overN() {
        // "12" → 1:12 → 1/12
        val r = ShaftViewModel.parseRateText("12")
        assertNotNull(r)
        assertEquals(1f / 12f, r!!, 1e-5f)
    }

    @Test fun parseRateText_bareDecimalBelowOne_returnedAsIs() {
        val r = ShaftViewModel.parseRateText("0.5")
        assertNotNull(r)
        assertEquals(0.5f, r!!, 1e-5f)
    }

    @Test fun parseRateText_blank_returnsNull() {
        assertNull(ShaftViewModel.parseRateText(""))
        assertNull(ShaftViewModel.parseRateText("   "))
    }

    @Test fun parseRateText_invalid_returnsNull() {
        assertNull(ShaftViewModel.parseRateText("abc"))
        assertNull(ShaftViewModel.parseRateText("1:0"))   // div by zero
        assertNull(ShaftViewModel.parseRateText("3/0"))   // div by zero
    }

    // ─────────────────────────────────────────────
    // deriveTaperDiameters — direction-aware
    //
    // Model diameters are axial start/end (AFT → FWD). The SET (Small End of Taper)
    // faces the nearer shaft end:
    //   AFT-end taper  → SET at start (smallEndAtStart = true)  → end = start + rate·len
    //   FWD-end taper  → SET at end   (smallEndAtStart = false) → end = start − rate·len
    // ─────────────────────────────────────────────

    @Test fun derive_bothProvided_rateIgnored() {
        val (start, end) = ShaftViewModel.deriveTaperDiameters(
            startDiaMm = 100f, endDiaMm = 80f, lengthMm = 200f, rateText = "1:12"
        )
        assertEquals(100f, start, 1e-4f)
        assertEquals(80f, end, 1e-4f)
    }

    @Test fun derive_aftTaper_onlySet_deriveLetLarger() {
        // AFT taper: SET=100 at start, length=240, rate=1:12 → delta 20 → LET (end) = 120
        val (start, end) = ShaftViewModel.deriveTaperDiameters(
            startDiaMm = 100f, endDiaMm = 0f, lengthMm = 240f, rateText = "1:12",
            smallEndAtStart = true
        )
        assertEquals(100f, start, 1e-4f)
        assertEquals(120f, end, 1e-4f)
    }

    @Test fun derive_aftTaper_onlyLet_deriveSetSmaller() {
        // AFT taper: LET=120 at end → SET (start) = 120 − 20 = 100
        val (start, end) = ShaftViewModel.deriveTaperDiameters(
            startDiaMm = 0f, endDiaMm = 120f, lengthMm = 240f, rateText = "1:12",
            smallEndAtStart = true
        )
        assertEquals(100f, start, 1e-4f)
        assertEquals(120f, end, 1e-4f)
    }

    @Test fun derive_fwdTaper_onlyLetAtStart_deriveSetSmaller() {
        // FWD taper: LET=100 at start → SET (end) = 100 − 20 = 80
        val (start, end) = ShaftViewModel.deriveTaperDiameters(
            startDiaMm = 100f, endDiaMm = 0f, lengthMm = 240f, rateText = "1:12",
            smallEndAtStart = false
        )
        assertEquals(100f, start, 1e-4f)
        assertEquals(80f, end, 1e-4f)
    }

    @Test fun derive_fwdTaper_onlySetAtEnd_deriveLetLarger() {
        // FWD taper: SET=80 at end → LET (start) = 80 + 20 = 100
        val (start, end) = ShaftViewModel.deriveTaperDiameters(
            startDiaMm = 0f, endDiaMm = 80f, lengthMm = 240f, rateText = "1:12",
            smallEndAtStart = false
        )
        assertEquals(100f, start, 1e-4f)
        assertEquals(80f, end, 1e-4f)
    }

    @Test fun derive_blankRate_diametersUnchanged() {
        val (start, end) = ShaftViewModel.deriveTaperDiameters(
            startDiaMm = 100f, endDiaMm = 0f, lengthMm = 240f, rateText = ""
        )
        // No rate → can't derive → returned as-is
        assertEquals(100f, start, 1e-4f)
        assertEquals(0f, end, 1e-4f)
    }

    @Test fun derive_zeroLength_diametersUnchanged() {
        val (start, end) = ShaftViewModel.deriveTaperDiameters(
            startDiaMm = 100f, endDiaMm = 0f, lengthMm = 0f, rateText = "1:12"
        )
        assertEquals(100f, start, 1e-4f)
        assertEquals(0f, end, 1e-4f)
    }

    @Test fun derive_resultClampedToZero() {
        // Very steep rate on a FWD taper can drive the derived end negative → clamp to 0
        val (_, end) = ShaftViewModel.deriveTaperDiameters(
            startDiaMm = 10f, endDiaMm = 0f, lengthMm = 1000f, rateText = "1:1",
            smallEndAtStart = false
        )
        assertTrue("derived end must be ≥ 0", end >= 0f)
    }

    // ─────────────────────────────────────────────
    // taperSmallEndAtStart — midpoint classification
    // ─────────────────────────────────────────────

    @Test fun smallEnd_aftHalf_isAtStart() {
        assertTrue(ShaftViewModel.taperSmallEndAtStart(startMm = 0f, lengthMm = 100f, overallLengthMm = 1000f))
    }

    @Test fun smallEnd_fwdHalf_isAtEnd() {
        assertFalse(ShaftViewModel.taperSmallEndAtStart(startMm = 900f, lengthMm = 100f, overallLengthMm = 1000f))
    }

    @Test fun smallEnd_unknownOal_defaultsToStart() {
        assertTrue(ShaftViewModel.taperSmallEndAtStart(startMm = 500f, lengthMm = 100f, overallLengthMm = 0f))
    }
}
