package com.android.shaftschematic.pdf

enum class PdfExportMode {
    Standard,
    Template
}

data class PdfExportOptions(
    val mode: PdfExportMode = PdfExportMode.Standard,
    val showDimensions: Boolean = true,
    val showLabels: Boolean = true,
    val showFooter: Boolean = true,
    /**
     * Blank-draft (write-in) mode: the full drawing, dimension lines, and footer layout are
     * kept, but every VALUE (dimension numbers, Ø callouts, footer specs, job info, date) is
     * replaced by a writable blank so the sheet can be filled in by hand in the field.
     */
    val blankValues: Boolean = false,
)
