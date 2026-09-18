package com.android.shaftschematic.geom

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
