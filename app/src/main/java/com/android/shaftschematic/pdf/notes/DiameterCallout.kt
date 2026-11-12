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
     * others existed) prints `Ø` + [valueMm]; [CalloutKind.ANGLE] is an Angle-mode blend's ONE
     * callout: [angleDeg] as `45°`, followed by its corner radius when [valueMm] > 0
     * (`45°, 3/8" R`) — STORED values, never measured off the drawing.
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
    /**
     * The axial span (mm) of the component a [CalloutKind.DIAMETER] value belongs to. Its leader
     * stands at [xMm] unless that collides, and may then slide anywhere inside this span — the
     * value reads as its component's from any point along it. Null (the default) = the leader
     * stays at [xMm]. Both or neither are set.
     */
    val spanStartMm: Double? = null,
    val spanEndMm: Double? = null,
    /**
     * A SECOND leader, for one value that belongs to two components: a liner and the shown body
     * touching it at the same Ø print the value once with a leader to each (on-device request —
     * `joinSharedOdCallouts`). Both leaders drop to the same row, a line on that row joins them,
     * and the value hangs past the outer one. [secondXMm] is the second component's centre and
     * [secondSpanStartMm]/[secondSpanEndMm] its axial span; the renderer stands that leader near the
     * face it shares with the first component. Null (the default) = one leader.
     */
    val secondXMm: Double? = null,
    val secondSpanStartMm: Double? = null,
    val secondSpanEndMm: Double? = null,
)

enum class LeaderSide { ABOVE, BELOW }

/** What a below-shaft leader callout reports — see [DiaCallout.kind]. */
enum class CalloutKind { DIAMETER, ANGLE }
