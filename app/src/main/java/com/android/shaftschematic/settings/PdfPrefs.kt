package com.android.shaftschematic.settings

/**
 * Controls how liner dimension rails are anchored in the PDF export.
 */
enum class PdfTieringMode {
    AUTO, // Let the PDF composer choose the anchor (default)
    AFT,  // Always anchor liner rails to the aft (leftmost) liner
    FWD   // Always anchor liner rails to the fwd (rightmost) liner
}

// Bounds for the sizing-curve anchor heights (paper inches). The ceiling matches the
// absolute drawn-height cap (PROFILE_MAX_SHAFT_HEIGHT_PT = 108 pt = 1.5").
const val PDF_CURVE_HEIGHT_MIN_IN = 0.25f
const val PDF_CURVE_HEIGHT_MAX_IN = 1.5f

/**
 * Default body S-break threshold: a body run breaks once it draws below HALF its true
 * width. The single source for the shipped value — the pref field, the Settings slider's
 * reset button, and the tests all read it from here.
 */
const val PDF_SBREAK_THRESHOLD_DEFAULT = 0.5f

/**
 * PDF-only preferences. Add more knobs here as you grow the exporter.
 */
data class PdfPrefs(
    val tieringMode: PdfTieringMode = PdfTieringMode.AUTO,
    val showComponentTitles: Boolean = true,
    val shadedBodies: Boolean = false,
    val shadedTapers: Boolean = false,
    val shadedLiners: Boolean = false,
    /**
     * Default sizing-curve anchor heights (paper inches of drawn height at 100% on the
     * "Shaft height" slider): a 4" shaft draws [curveLoHeightIn] tall, an 8" shaft
     * [curveHiHeightIn]; sizes between and beyond follow the line
     * (`geom/ProfileCompression.defaultShaftHeightPt`). Adjustable in
     * Settings → PDF Export → Default drawing size; the standard pair is PROPORTIONAL
     * (through the origin: 8" → 1", 6" → 3/4", 4" → 1/2" — the hand-sheet rule from
     * the original rulered sketches; taller defaults read "chubby" on-device).
     */
    val curveLoHeightIn: Float = 0.5f,
    val curveHiHeightIn: Float = 1.0f,
    /**
     * Fraction of true drawn width below which a compressed BODY run shows the S-break
     * pair (`pdf/BreakSymbol.breakForCompression`). Adjustable in Settings → PDF Export →
     * "Body S-break": 0 = never break on compression (all foreshortening stays hidden),
     * 1 = break on any foreshortening at all. The traditional long-span trigger
     * (`COMPRESS_TRIGGER_PT`) is deliberately NOT governed by this — a run that eats
     * 220 pt of paper at true scale is not hidden compression, so it breaks regardless.
     */
    val sBreakThresholdFrac: Float = PDF_SBREAK_THRESHOLD_DEFAULT,
) {
    /** The anchor heights in PDF points (72 pt per paper inch) — what the geometry consumes. */
    val curveLoHeightPt: Float get() = curveLoHeightIn * 72f
    val curveHiHeightPt: Float get() = curveHiHeightIn * 72f

    fun clamped(): PdfPrefs =
        copy(
            curveLoHeightIn = curveLoHeightIn.coerceIn(PDF_CURVE_HEIGHT_MIN_IN, PDF_CURVE_HEIGHT_MAX_IN),
            curveHiHeightIn = curveHiHeightIn.coerceIn(PDF_CURVE_HEIGHT_MIN_IN, PDF_CURVE_HEIGHT_MAX_IN),
            sBreakThresholdFrac = sBreakThresholdFrac.coerceIn(0f, 1f),
        )
}
