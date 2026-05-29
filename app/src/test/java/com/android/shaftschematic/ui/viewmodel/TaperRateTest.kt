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
    // deriveTaperDiameters
    // ─────────────────────────────────────────────

    @Test fun derive_bothProvided_rateIgnored() {
        val (set, let) = ShaftViewModel.deriveTaperDiameters(
            setMm = 100f, letMm = 80f, lengthMm = 200f, rateText = "1:12"
        )
        assertEquals(100f, set, 1e-4f)
        assertEquals(80f, let, 1e-4f)
    }

    @Test fun derive_onlySet_deriveLetFromRate() {
        // SET=100, length=240, rate=1:12 → diameter change = 240/12=20 → LET=80
        val (set, let) = ShaftViewModel.deriveTaperDiameters(
            setMm = 100f, letMm = 0f, lengthMm = 240f, rateText = "1:12"
        )
        assertEquals(100f, set, 1e-4f)
        assertEquals(80f, let, 1e-4f)
    }

    @Test fun derive_onlyLet_deriveSetFromRate() {
        // LET=80, length=240, rate=1:12 → SET = 80 + 240/12 = 100
        val (set, let) = ShaftViewModel.deriveTaperDiameters(
            setMm = 0f, letMm = 80f, lengthMm = 240f, rateText = "1:12"
        )
        assertEquals(100f, set, 1e-4f)
        assertEquals(80f, let, 1e-4f)
    }

    @Test fun derive_blankRate_diametersUnchanged() {
        val (set, let) = ShaftViewModel.deriveTaperDiameters(
            setMm = 100f, letMm = 0f, lengthMm = 240f, rateText = ""
        )
        // No rate → can't derive → returned as-is
        assertEquals(100f, set, 1e-4f)
        assertEquals(0f, let, 1e-4f)
    }

    @Test fun derive_zeroLength_diametersUnchanged() {
        val (set, let) = ShaftViewModel.deriveTaperDiameters(
            setMm = 100f, letMm = 0f, lengthMm = 0f, rateText = "1:12"
        )
        assertEquals(100f, set, 1e-4f)
        assertEquals(0f, let, 1e-4f)
    }

    @Test fun derive_resultClampedToZero() {
        // Very steep rate might produce negative LET → clamp to 0
        val (_, let) = ShaftViewModel.deriveTaperDiameters(
            setMm = 10f, letMm = 0f, lengthMm = 1000f, rateText = "1:1"
        )
        assertTrue("LET must be ≥ 0", let >= 0f)
    }
}
