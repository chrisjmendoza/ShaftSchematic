package com.android.shaftschematic.settings

import com.android.shaftschematic.geom.WEAR_TRACE_MAX_DEPTH_FRAC
import com.android.shaftschematic.geom.WEAR_TRACE_MIN_DEPTH_FRAC
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.roundToInt

/**
 * The user-adjustable drawing prefs on Settings → Drawing: the "Default drawing size"
 * sizing-curve pair, the "Body S-break" threshold, the "Wear depth exaggeration" default, and
 * the "Wear area shade". Pins the shipped defaults (the proportional hand-sheet anchors
 * 4" → 1/2", 8" → 1"; S-break at half of true; wear trace at the 25% high end; the band shade
 * at the historical 40/255 alpha), the pt conversion the composers consume, and the clamps to
 * each settable range.
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

    // ── Body S-break threshold (Settings → Drawing → "Body S-break") ────────────

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

    // ── Wear trace depth (Settings → Drawing → "Wear depth exaggeration") ──────

    @Test
    fun `wear trace depth defaults to the shipped high end`() {
        assertEquals(WEAR_TRACE_MAX_DEPTH_FRAC, PdfPrefs().wearTraceDepthFrac, 1e-6f)
    }

    @Test
    fun `clamped coerces the wear trace depth into the settable range`() {
        assertEquals(
            WEAR_TRACE_MIN_DEPTH_FRAC,
            PdfPrefs(wearTraceDepthFrac = 0f).clamped().wearTraceDepthFrac, 1e-6f,
        )
        assertEquals(
            WEAR_TRACE_MAX_DEPTH_FRAC,
            PdfPrefs(wearTraceDepthFrac = 0.8f).clamped().wearTraceDepthFrac, 1e-6f,
        )
    }

    @Test
    fun `in-range wear trace depths pass through clamped verbatim`() {
        assertEquals(0.05f, PdfPrefs(wearTraceDepthFrac = 0.05f).clamped().wearTraceDepthFrac, 1e-6f)
        assertEquals(0.13f, PdfPrefs(wearTraceDepthFrac = 0.13f).clamped().wearTraceDepthFrac, 1e-6f)
        assertEquals(0.25f, PdfPrefs(wearTraceDepthFrac = 0.25f).clamped().wearTraceDepthFrac, 1e-6f)
    }

    // ── Wear band shade (Settings → Drawing → "Wear area shade") ───────────────

    @Test
    fun `wear band shade defaults to the historical fixed alpha`() {
        // 40/255 — the value the detail strips' band fill shipped as a constant, so an
        // untouched install draws exactly the same grey.
        assertEquals(40f / 255f, PDF_WEAR_BAND_SHADE_DEFAULT, 1e-6f)
        assertEquals(PDF_WEAR_BAND_SHADE_DEFAULT, PdfPrefs().wearBandShadeFrac, 1e-6f)
        assertEquals(40, (PdfPrefs().wearBandShadeFrac * 255f).roundToInt())
    }

    @Test
    fun `clamped coerces the wear band shade into the settable range`() {
        // The cap is load-bearing: a heavier wash buries the pit "X"s drawn into the band.
        assertEquals(
            PDF_WEAR_BAND_SHADE_MIN,
            PdfPrefs(wearBandShadeFrac = 0f).clamped().wearBandShadeFrac, 1e-6f,
        )
        assertEquals(
            PDF_WEAR_BAND_SHADE_MAX,
            PdfPrefs(wearBandShadeFrac = 1f).clamped().wearBandShadeFrac, 1e-6f,
        )
    }

    @Test
    fun `in-range wear band shades pass through clamped verbatim`() {
        assertEquals(0.05f, PdfPrefs(wearBandShadeFrac = 0.05f).clamped().wearBandShadeFrac, 1e-6f)
        assertEquals(0.20f, PdfPrefs(wearBandShadeFrac = 0.20f).clamped().wearBandShadeFrac, 1e-6f)
        assertEquals(0.35f, PdfPrefs(wearBandShadeFrac = 0.35f).clamped().wearBandShadeFrac, 1e-6f)
        assertEquals(
            PDF_WEAR_BAND_SHADE_DEFAULT,
            PdfPrefs(wearBandShadeFrac = PDF_WEAR_BAND_SHADE_DEFAULT).clamped().wearBandShadeFrac,
            1e-6f,
        )
    }

    // ── Taper–liner join threshold (Settings → Drawing → "Taper–liner join") ───

    @Test
    fun `the join threshold defaults to the shipped 3 inches, in canonical mm`() {
        assertEquals(76.2f, PDF_WEAR_JOIN_GAP_DEFAULT_MM, 1e-4f)
        assertEquals(PDF_WEAR_JOIN_GAP_DEFAULT_MM, PdfPrefs().wearJoinGapMaxMm, 1e-6f)
        // 0–12": the low end breaks on any gap, the high end draws a foot of shaft true.
        assertEquals(0f, PDF_WEAR_JOIN_GAP_MIN_MM, 1e-6f)
        assertEquals(304.8f, PDF_WEAR_JOIN_GAP_MAX_MM, 1e-4f)
    }

    @Test
    fun `clamped coerces the join threshold into the settable range`() {
        assertEquals(
            PDF_WEAR_JOIN_GAP_MIN_MM,
            PdfPrefs(wearJoinGapMaxMm = -50f).clamped().wearJoinGapMaxMm, 1e-6f,
        )
        assertEquals(
            PDF_WEAR_JOIN_GAP_MAX_MM,
            PdfPrefs(wearJoinGapMaxMm = 1000f).clamped().wearJoinGapMaxMm, 1e-6f,
        )
    }

    @Test
    fun `in-range join thresholds pass through clamped verbatim`() {
        // Both ends are legal settings: 0 = break on any gap, 12" = a foot drawn true.
        listOf(0f, 12.7f, 76.2f, 152.4f, 304.8f).forEach { mm ->
            assertEquals(mm, PdfPrefs(wearJoinGapMaxMm = mm).clamped().wearJoinGapMaxMm, 1e-6f)
        }
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
