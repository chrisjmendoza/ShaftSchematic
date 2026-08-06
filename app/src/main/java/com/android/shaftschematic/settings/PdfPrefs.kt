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
) {
    /** The anchor heights in PDF points (72 pt per paper inch) — what the geometry consumes. */
    val curveLoHeightPt: Float get() = curveLoHeightIn * 72f
    val curveHiHeightPt: Float get() = curveHiHeightIn * 72f

    fun clamped(): PdfPrefs =
        copy(
            curveLoHeightIn = curveLoHeightIn.coerceIn(PDF_CURVE_HEIGHT_MIN_IN, PDF_CURVE_HEIGHT_MAX_IN),
            curveHiHeightIn = curveHiHeightIn.coerceIn(PDF_CURVE_HEIGHT_MIN_IN, PDF_CURVE_HEIGHT_MAX_IN),
        )
}
