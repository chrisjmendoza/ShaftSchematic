package com.android.shaftschematic.model

/**
 * Minimal, export-only shape used by the PDF dimensioning pass.
 */
data class LinerDim(
    val id: String,
    val anchor: LinerAnchor,
    val offsetFromSetMm: Double,
    val lengthMm: Double
)
