package com.android.shaftschematic.geom

import com.android.shaftschematic.model.CouplerBoltSlot

/**
 * Drawn x of every hole center in [slot]'s row, placed off the row's OWN mapped span — first
 * center − hole radius … last center + hole radius — the way a body keyway derives its axial
 * scale from its own pinned window rather than the host body's average. The window is pinned
 * at true scale ([boltRowPinnedSpans]), so the local pt/mm equals the diameter scale the hole
 * circles are sized on and the drawn pitch is the true pitch at that scale; the holes stay
 * round and correctly spaced inside a body that compresses around them. ONE source for both
 * draw sites (`ShaftRenderer` overlay, `drawCouplerBoltSlots`); on the canvas's linear map it
 * reduces to `xAt(center)` exactly.
 */
fun boltHoleCentersX(slot: CouplerBoltSlot, xAt: (Float) -> Float): List<Float> {
    if (slot.count < 1) return emptyList()
    val r = slot.holeDiaMm.coerceAtLeast(0f) * 0.5f
    val loMm = slot.centerMmAt(0) - r
    val hiMm = slot.centerMmAt(slot.count - 1) + r
    val xLo = xAt(loMm)
    val ptPerMm = if (hiMm > loMm + 1e-6f) (xAt(hiMm) - xLo) / (hiMm - loMm) else 0f
    return List(slot.count) { i -> xLo + (r + i * slot.spacingMm) * ptPerMm }
}

/**
 * Hidden-bore construction for a cross-drilled coupler bolt hole clocked 90° from the keyway.
 *
 * The keyway draws face-on, so a hole 90° from it has its axis in the page: the bore runs from
 * the top silhouette toward the bottom and shows as two HIDDEN (dashed) lines one hole width
 * apart, plus a dashed floor when the hole is blind. Pure, unit-agnostic (px or pt) — the ONE
 * source for both draw sites (`ShaftRenderer` overlay, `drawCouplerBoltSlots`), so the preview
 * and the sheet cannot disagree about where the bore lands.
 *
 * @property x1 Left bore wall. @property x2 Right bore wall.
 * @property yTop Where the bore enters — the top surface at the hole's station.
 * @property yBottom Where it ends — the bottom surface (through) or the drill floor (blind).
 * @property floor True when [yBottom] is a drill floor to draw across the walls (blind hole).
 */
data class CrossBoreLines(
    val x1: Float,
    val x2: Float,
    val yTop: Float,
    val yBottom: Float,
    val floor: Boolean,
)

/**
 * Bore lines for one hole at station [cx] with drawn radius [holeR] on a shaft of centerline
 * [cy] and local surface radius [rSurface]. A blind hole's bore stops [depthPx] below the top
 * surface (clamped to the far surface); a [through] hole runs surface to surface. A
 * non-positive [depthPx] on a blind hole draws nothing (placed-but-empty).
 */
fun crossBoreLines(
    cx: Float,
    holeR: Float,
    cy: Float,
    rSurface: Float,
    through: Boolean,
    depthPx: Float,
): CrossBoreLines? {
    if (holeR <= 0f || rSurface <= 0f) return null
    val top = cy - rSurface
    val bottomSurface = cy + rSurface
    val yBottom = if (through) bottomSurface else {
        if (depthPx <= 0f) return null
        (top + depthPx).coerceAtMost(bottomSurface)
    }
    return CrossBoreLines(
        x1 = cx - holeR,
        x2 = cx + holeR,
        yTop = top,
        yBottom = yBottom,
        floor = !through && yBottom < bottomSurface,
    )
}
