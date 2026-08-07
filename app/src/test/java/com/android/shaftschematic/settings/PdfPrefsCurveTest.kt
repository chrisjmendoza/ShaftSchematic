package com.android.shaftschematic.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The user-adjustable drawing prefs on Settings → PDF Export: the "Default drawing size"
 * sizing-curve pair and the "Body S-break" threshold. Pins the shipped defaults (the
 * proportional hand-sheet anchors 4" → 1/2", 8" → 1"; S-break at half of true), the pt
 * conversion the composers consume, and the clamps to each settable range.
 */
class PdfPrefsCurveTest {

    @Test
    fun `defaults are the proportional hand-sheet anchors`() {
        // 1/2" at 4", 1" at 8" — a line through the origin, so drawn height stays
        // strictly proportional to true diameter by default.
        val p = PdfPrefs()
        assertEquals(0.5f, p.curveLoHeightIn, 1e-6f)
        assertEquals(1.0f, p.curveHiHeightIn, 1e-6f)
        assertEquals(36f, p.curveLoHeightPt, 1e-4f)
        assertEquals(72f, p.curveHiHeightPt, 1e-4f)
    }

    @Test
    fun `clamped coerces anchors into the settable range`() {
        val p = PdfPrefs(curveLoHeightIn = 0.1f, curveHiHeightIn = 9f).clamped()
        assertEquals(PDF_CURVE_HEIGHT_MIN_IN, p.curveLoHeightIn, 1e-6f)
        assertEquals(PDF_CURVE_HEIGHT_MAX_IN, p.curveHiHeightIn, 1e-6f)
    }

    @Test
    fun `in-range anchors pass through clamped verbatim`() {
        val p = PdfPrefs(curveLoHeightIn = 0.5f, curveHiHeightIn = 1.0f).clamped()
        assertEquals(0.5f, p.curveLoHeightIn, 1e-6f)
        assertEquals(1.0f, p.curveHiHeightIn, 1e-6f)
    }

    // ── Body S-break threshold (Settings → PDF Export → "Body S-break") ──────────

    @Test
    fun `S-break threshold defaults to half of true`() {
        assertEquals(0.5f, PDF_SBREAK_THRESHOLD_DEFAULT, 1e-6f)
        assertEquals(PDF_SBREAK_THRESHOLD_DEFAULT, PdfPrefs().sBreakThresholdFrac, 1e-6f)
    }

    @Test
    fun `clamped coerces the S-break threshold into 0 to 1`() {
        assertEquals(0f, PdfPrefs(sBreakThresholdFrac = -0.4f).clamped().sBreakThresholdFrac, 1e-6f)
        assertEquals(1f, PdfPrefs(sBreakThresholdFrac = 2.5f).clamped().sBreakThresholdFrac, 1e-6f)
    }

    @Test
    fun `in-range S-break thresholds pass through clamped verbatim`() {
        // Both ends are legal settings: 0 = never break on compression, 1 = always.
        assertEquals(0f, PdfPrefs(sBreakThresholdFrac = 0f).clamped().sBreakThresholdFrac, 1e-6f)
        assertEquals(0.25f, PdfPrefs(sBreakThresholdFrac = 0.25f).clamped().sBreakThresholdFrac, 1e-6f)
        assertEquals(0.75f, PdfPrefs(sBreakThresholdFrac = 0.75f).clamped().sBreakThresholdFrac, 1e-6f)
        assertEquals(1f, PdfPrefs(sBreakThresholdFrac = 1f).clamped().sBreakThresholdFrac, 1e-6f)
    }
}
