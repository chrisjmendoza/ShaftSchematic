// file: app/src/main/java/com/android/shaftschematic/geom/WearDiaMath.kt
package com.android.shaftschematic.geom

/**
 * Wear diameter-reading pure math — hit-testing and coordinate helpers shared by the
 * detail-overlay tap handler and both draw sites (canvas + PDF), with no Compose/Android
 * imports so it's directly unit-testable on the JVM. Same posture as `WearPitMath.kt`.
 *
 * A reading is drawn as a **witness tick** — a thin vertical line spanning the component's
 * full drawn height at the reading's axial station — plus a leader down to the value label
 * (see `geom/WearDiaCalloutLayout.kt` for the label spread + leader routing). Both draw
 * sites build the tick from the same station x and segment top/bottom; only the drawing API
 * differs.
 */

/**
 * Convert a physical shaft-space axial position [physicalMm] into a component-local axial
 * value (from the component's AFT edge [componentStartMm]) — the canonical
 * [com.android.shaftschematic.model.WearDiaReading.axialMm] storage. Never negative.
 */
fun diaAxialLocalMm(physicalMm: Float, componentStartMm: Float): Float =
    (physicalMm - componentStartMm).coerceAtLeast(0f)

/**
 * A reading's drawn witness tick in destination units, for hit-testing a tap against the
 * readings already on screen. [id] is the [com.android.shaftschematic.model.WearDiaReading.id];
 * the tick is the vertical segment `(cx, topY)–(cx, botY)`.
 */
data class DiaHitTarget(val id: String, val cx: Float, val topY: Float, val botY: Float)

/**
 * Pick the id of the reading whose witness tick (inflated by [padPx] for an easy touch
 * target) is nearest to `(px, py)`, or `null` when the tap lands on none. Distance is
 * point-to-vertical-segment. Used by the detail overlay to decide "tap on an existing tick →
 * edit that reading" vs "tap on bare metal → place a new one".
 */
fun pickDiaReadingAt(px: Float, py: Float, targets: List<DiaHitTarget>, padPx: Float): String? {
    var bestId: String? = null
    var bestDistSq = Float.MAX_VALUE
    for (t in targets) {
        val dx = px - t.cx
        val dy = when {
            py < t.topY -> t.topY - py
            py > t.botY -> py - t.botY
            else -> 0f
        }
        val distSq = dx * dx + dy * dy
        if (distSq <= padPx * padPx && distSq < bestDistSq) {
            bestDistSq = distSq
            bestId = t.id
        }
    }
    return bestId
}
