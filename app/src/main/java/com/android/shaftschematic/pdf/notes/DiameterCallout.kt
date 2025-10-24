package com.android.shaftschematic.pdf.notes

/**
 * Diameter callout anchored at a specific axial station.
 * valueMm is the diameter in mm; display formatting happens in the renderer.
 */
data class DiaCallout(
    val xMm: Double,
    val valueMm: Double,
    val side: LeaderSide = LeaderSide.ABOVE
)

enum class LeaderSide { ABOVE, BELOW }
