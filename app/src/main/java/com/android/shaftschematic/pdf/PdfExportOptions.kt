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
)
