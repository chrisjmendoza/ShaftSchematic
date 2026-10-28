package com.android.shaftschematic.model

/**
 * Minimal, export-only shape used by the PDF dimensioning pass: a component dimensioned
 * "SET → near edge, then length" — a liner, or a taper that stands off both SETs (see
 * `ShaftPdfComposer.mapToMidTaperDimsForPdf`).
 */
data class LinerDim(
    val id: String,
    val anchor: LinerAnchor,
    val offsetFromSetMm: Double,
    val lengthMm: Double
)
