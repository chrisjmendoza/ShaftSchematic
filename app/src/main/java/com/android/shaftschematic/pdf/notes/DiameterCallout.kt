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
    /**
     * What the value is. [CalloutKind.DIAMETER] (the default — every callout built before the
     * others existed) prints `Ø` + [valueMm]; [CalloutKind.ANGLE] prints [angleDeg] as `45°`;
     * [CalloutKind.RADIUS] prints [valueMm] as a length with an `R` (`3/8" R`). The last two are an
     * Angle-mode blend's chamfer and corner radius — STORED values, never measured off the drawing.
     */
    val kind: CalloutKind = CalloutKind.DIAMETER,
    /** The angle an [CalloutKind.ANGLE] callout prints, degrees; unused otherwise. */
    val angleDeg: Float = 0f,
    /**
     * Page point the leader leaves from, when it points at a FEATURE rather than a component's
     * diameter: an Angle-mode blend's leader lands on the chamfer's bottom outline, not at the
     * shaft's lowest edge. Null (the default) = `pageX(xMm)` and the shaft's bottom edge, as
     * before. Both or neither are set.
     */
    val anchorXPt: Float? = null,
    val anchorYPt: Float? = null,
)

enum class LeaderSide { ABOVE, BELOW }

/** What a below-shaft leader callout reports — see [DiaCallout.kind]. */
enum class CalloutKind { DIAMETER, ANGLE, RADIUS }
