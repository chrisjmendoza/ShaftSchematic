package com.android.shaftschematic.settings

/**
 * Controls how liner dimension rails are anchored in the PDF export.
 */
enum class PdfTieringMode {
    AUTO, // Let the PDF composer choose the anchor (default)
    AFT,  // Always anchor liner rails to the aft (leftmost) liner
    FWD   // Always anchor liner rails to the fwd (rightmost) liner
}

/**
 * PDF-only preferences. Add more knobs here as you grow the exporter.
 */
data class PdfPrefs(
    /**
     * Extra spacing between the OAL rail and the first liner rail,
     * expressed as a multiple of the standard rail gap (>= 1.0).
     * Example: 2.5f = OAL sits 2.5× the normal gap above the next rail.
     */
    val oalSpacingFactor: Float = 2.5f,
    val tieringMode: PdfTieringMode = PdfTieringMode.AUTO,
    val showComponentTitles: Boolean = true,
) {
    fun clamped(): PdfPrefs =
        copy(oalSpacingFactor = oalSpacingFactor.coerceIn(1.0f, 6.0f))
}
