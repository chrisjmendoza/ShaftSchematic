package com.android.shaftschematic.pdf.notes

import com.android.shaftschematic.util.UnitSystem

/**
 * Diameter callout anchored at a specific axial station.
 * valueMm is the diameter in mm; display formatting happens in the renderer.
 *
 * @property unit The resolved display unit for this callout's owning component
 *   (`DisplayUnits.unitFor(componentId)` at the builder call site) — each callout carries
 *   its own unit so a mixed-unit sheet can show a body in mm beside a liner in inches.
 * @property dual When true the renderer prints both units inline (`DisplayUnits.dual`).
 */
data class DiaCallout(
    val xMm: Double,
    val valueMm: Double,
    val side: LeaderSide = LeaderSide.ABOVE,
    val unit: UnitSystem = UnitSystem.INCHES,
    val dual: Boolean = false,
)

enum class LeaderSide { ABOVE, BELOW }
