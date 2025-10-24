package com.android.shaftschematic.settings

/**
 * PDF-only preferences. Add more knobs here as you grow the exporter.
 */
data class PdfPrefs(
    /**
     * Extra spacing between the OAL rail and the first liner rail,
     * expressed as a multiple of the standard rail gap (>= 1.0).
     * Example: 2.5f = OAL sits 2.5× the normal gap above the next rail.
     */
    val oalSpacingFactor: Float = 2.5f
) {
    fun clamped(): PdfPrefs =
        copy(oalSpacingFactor = oalSpacingFactor.coerceIn(1.0f, 6.0f))
}
