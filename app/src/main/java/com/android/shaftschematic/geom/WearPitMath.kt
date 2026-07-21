// file: app/src/main/java/com/android/shaftschematic/geom/WearPitMath.kt
package com.android.shaftschematic.geom

import com.android.shaftschematic.model.PitSize

/**
 * Wear-pit ("X" marker) pure math — shared by the two draw sites and the tap handler.
 *
 * Kept as small, dependency-free top-level functions (no Compose/Android imports, `geom`
 * package) so the sizing, hit-testing, and clamping rules are directly unit-testable in a
 * plain JVM test — the same posture as `geom/RunoutReadingMath.kt` and
 * `ui/screen/LinerWearMath.kt`.
 *
 * A pit "X" is drawn **identically** in both draw sites (the two must stay in lockstep, same
 * rule as the runout marker):
 * - canvas: `ComponentWearDetailOverlay`'s `drawPitX` (Compose `DrawScope`),
 * - PDF: `WearPdfComposer`'s `drawWearPits*` (`android.graphics.Canvas`/`Paint`).
 *
 * Both compute the four X endpoints from a centre + [pitHalfArm]; only the drawing API differs.
 */

/**
 * The [PitSize.LARGE] X is this multiple of the [PitSize.SMALL] X's arm length — a bigger "X"
 * marks a bigger hole, matching the hand convention. Symbol-space only; a pit's true diameter is
 * not modelled.
 */
const val PIT_LARGE_TO_SMALL_RATIO = 1.7f

/** Comfortable interior band for [com.android.shaftschematic.model.WearPit.acrossFrac] so the X stays on the metal. */
const val PIT_ACROSS_MIN = 0.08f
const val PIT_ACROSS_MAX = 0.92f

/**
 * Half-arm length (centre to a tip) of a pit "X" for [size], given the [PitSize.SMALL] half-arm
 * [smallHalf] expressed in the destination's own units (px on canvas, pt in the PDF). The caller
 * picks [smallHalf] per surface; the small:large ratio ([PIT_LARGE_TO_SMALL_RATIO]) is shared so
 * the two surfaces stay proportional.
 */
fun pitHalfArm(size: PitSize, smallHalf: Float): Float =
    if (size == PitSize.LARGE) smallHalf * PIT_LARGE_TO_SMALL_RATIO else smallHalf

/**
 * Clamp an across-fraction into `[PIT_ACROSS_MIN, PIT_ACROSS_MAX]` so a placed X's centre keeps
 * the symbol inside the drawn segment rather than half-hanging into empty space above/below it.
 */
fun clampPitAcrossFrac(frac: Float): Float = frac.coerceIn(PIT_ACROSS_MIN, PIT_ACROSS_MAX)

/**
 * Convert a physical shaft-space axial position [physicalMm] into a component-local axial value
 * (from the component's AFT edge [componentStartMm]) — the canonical
 * [com.android.shaftschematic.model.WearPit.axialMm] storage. Never negative.
 */
fun pitAxialLocalMm(physicalMm: Float, componentStartMm: Float): Float =
    (physicalMm - componentStartMm).coerceAtLeast(0f)

/**
 * A pit's drawn centre + half-arm in destination units, for hit-testing a tap against the pits
 * already on screen. [id] is the [com.android.shaftschematic.model.WearPit.id].
 */
data class PitHitTarget(val id: String, val cx: Float, val cy: Float, val halfArm: Float)

/**
 * Pick the id of the pit nearest to `(px, py)` whose X symbol (inflated by [padPx] for an easy
 * touch target) is hit, or `null` when the tap lands on none. Used by the detail overlay to
 * decide "tap on an existing X → remove it" vs "tap on bare metal → add a new pit".
 */
fun pickPitAt(px: Float, py: Float, targets: List<PitHitTarget>, padPx: Float): String? {
    var bestId: String? = null
    var bestDistSq = Float.MAX_VALUE
    for (t in targets) {
        val reach = t.halfArm + padPx
        val dx = px - t.cx
        val dy = py - t.cy
        val distSq = dx * dx + dy * dy
        if (distSq <= reach * reach && distSq < bestDistSq) {
            bestDistSq = distSq
            bestId = t.id
        }
    }
    return bestId
}

/**
 * Vertical pixel/point position of a pit centre inside a segment whose top edge is at [topY] and
 * bottom edge at [botY], for across-fraction [acrossFrac] (`0` = top, `1` = bottom). The fraction
 * is clamped ([clampPitAcrossFrac]) so the returned centre keeps the symbol on the metal.
 */
fun pitCenterY(topY: Float, botY: Float, acrossFrac: Float): Float =
    topY + clampPitAcrossFrac(acrossFrac) * (botY - topY)

/**
 * Inverse of [pitCenterY]: the across-fraction implied by a tap at [tapY] within a segment spanning
 * [topY]..[botY]. Returns `0.5` for a degenerate zero-height segment. Not clamped — callers clamp on
 * store ([clampPitAcrossFrac]) so a tap slightly outside the outline still records a sensible pit.
 */
fun acrossFracFromTapY(tapY: Float, topY: Float, botY: Float): Float {
    val h = botY - topY
    if (h <= 0f) return 0.5f
    return (tapY - topY) / h
}
