package com.android.shaftschematic.ui.screen

import com.android.shaftschematic.model.WearSpotReference
import com.android.shaftschematic.util.UnitSystem
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * WearAreaDragMath — the wear detail canvas's start / end bars: which bar a press reaches, and
 * what a dragged bar does to its wear area.
 *
 * A drag is the ONE place a stored wear-area value changes without being typed (on-device
 * request: size a wear area on the drawing instead of measuring its length). It is still a
 * direct user action, and because the result is read off the drawing it is an ESTIMATE, so it
 * lands on a readable grid ([wearDragGridMm]). A typed Start or Length is never snapped — only
 * a drag is.
 *
 * Pure — no Compose, no Android — so the hit-test, clamp and snap rules are unit-tested directly.
 */

/** Which edge of a wear area a bar moves: the AFT edge (start) or the FWD edge (end). */
enum class WearAreaEdge { START, END }

/**
 * One drawn bar, in untransformed canvas px. [priority] breaks a distance tie between two bars
 * (the end of one area butted to the start of the next): the higher wins — the area dragged
 * last, else the one added most recently.
 */
data class WearBarTarget(
    val spotId: String,
    val edge: WearAreaEdge,
    val xPx: Float,
    val topPx: Float,
    val bottomPx: Float,
    val priority: Int = 0,
)

/** The bar a press reached. */
data class WearBarHit(val spotId: String, val edge: WearAreaEdge)

/** Two bars this close in distance to the press are a tie, decided by [WearBarTarget.priority]. */
private const val WEAR_BAR_TIE_EPS_PX = 0.5f

/**
 * The bar a press at ([x], [y]) reaches: within [reachPx] of the bar's x and inside its height
 * extended by [yPadPx] at each end. Several in reach — the nearest in x; a tie goes to the
 * higher [WearBarTarget.priority]. None in reach returns null, and the press is an ordinary
 * canvas touch.
 */
fun pickWearAreaBar(
    x: Float,
    y: Float,
    targets: List<WearBarTarget>,
    reachPx: Float,
    yPadPx: Float = 0f,
): WearBarHit? {
    val inReach = targets.filter { t ->
        abs(x - t.xPx) <= reachPx && y >= t.topPx - yPadPx && y <= t.bottomPx + yPadPx
    }
    if (inReach.isEmpty()) return null
    val nearest = inReach.minOf { abs(x - it.xPx) }
    return inReach
        .filter { abs(x - it.xPx) <= nearest + WEAR_BAR_TIE_EPS_PX }
        .maxByOrNull { it.priority }
        ?.let { WearBarHit(it.spotId, it.edge) }
}

/** The grid a dragged edge lands on: 1/8" in an inch document, 1 mm in a millimetre one. */
fun wearDragGridMm(unit: UnitSystem): Float = when (unit) {
    UnitSystem.INCHES -> 25.4f / 8f
    UnitSystem.MILLIMETERS -> 1f
}

/** The shortest area a drag can leave: 1/8" in an inch document, 3 mm in a millimetre one. */
fun wearDragMinLengthMm(unit: UnitSystem): Float = when (unit) {
    UnitSystem.INCHES -> 25.4f / 8f
    UnitSystem.MILLIMETERS -> 3f
}

/** [valueMm] on the nearest multiple of [gridMm]; a non-positive grid returns it unchanged. */
fun snapToGridMm(valueMm: Float, gridMm: Float): Float {
    if (gridMm <= 0f) return valueMm
    return ((valueMm.toDouble() / gridMm).roundToLong() * gridMm.toDouble()).toFloat()
}

/** A wear area's canonical (host-local, from the host's AFT edge) start and length, mm. */
data class WearAreaDragResult(val startMm: Float, val lengthMm: Float)

/** True when the card's Start field locates the area's AFT edge under [reference]. */
private fun WearSpotReference.locatesAftEdge(): Boolean =
    this == WearSpotReference.LINER_AFT || this == WearSpotReference.AFT_SET

/**
 * The wear area after its [edge] is dragged to host-local [edgeLocalMm] (mm from the host's AFT
 * edge, read off the drawing). The other edge keeps its STORED position.
 *
 * What snaps follows what the card shows, so both of its fields read as clean values:
 * - the edge the Start field locates (the AFT edge under an AFT-flavoured [reference], the FWD
 *   edge under a FWD-flavoured one) snaps the displayed START to [gridMm];
 * - the other edge leaves the displayed Start alone and snaps the LENGTH to [gridMm].
 *
 * The moved edge is then held inside the host `[0, hostLengthMm]` and at least [minLengthMm]
 * from the other edge, so an edge can neither leave the component nor cross its partner. A
 * bound that wins over the grid leaves the edge exactly on that bound.
 *
 * [hostStartFromAftMm], [aftSetXMm] and [fwdSetXMm] are shaft-space mm, as in
 * [canonicalToWearStartMm].
 */
fun dragWearAreaEdge(
    edge: WearAreaEdge,
    edgeLocalMm: Float,
    startMm: Float,
    lengthMm: Float,
    hostLengthMm: Float,
    reference: WearSpotReference,
    hostStartFromAftMm: Float,
    aftSetXMm: Float,
    fwdSetXMm: Float,
    gridMm: Float,
    minLengthMm: Float,
): WearAreaDragResult {
    val endMm = startMm + lengthMm
    val hostLen = hostLengthMm.coerceAtLeast(0f)
    val aftFlavoured = reference.locatesAftEdge()
    return when (edge) {
        WearAreaEdge.START -> {
            // The start may run from the host's AFT edge up to [minLengthMm] short of the end.
            val hi = (minOf(endMm, hostLen) - minLengthMm).coerceAtLeast(0f)
            val snapped = if (aftFlavoured) {
                // Start locates this edge: snap the displayed Start.
                val offset = if (reference == WearSpotReference.AFT_SET) hostStartFromAftMm - aftSetXMm else 0f
                snapToGridMm(edgeLocalMm + offset, gridMm) - offset
            } else {
                // Start locates the fixed FWD edge: snap the length.
                endMm - snapToGridMm(endMm - edgeLocalMm, gridMm)
            }
            val newStart = snapped.coerceIn(0f, hi)
            WearAreaDragResult(newStart, endMm - newStart)
        }
        WearAreaEdge.END -> {
            // The end may run from [minLengthMm] past the start up to the host's FWD edge.
            val lo = (maxOf(startMm, 0f) + minLengthMm).coerceAtMost(hostLen)
            val snapped = if (aftFlavoured) {
                // Start locates the fixed AFT edge: snap the length.
                startMm + snapToGridMm(edgeLocalMm - startMm, gridMm)
            } else {
                // Start locates this edge, measured AFT from the reference: snap that distance.
                val datum = if (reference == WearSpotReference.FWD_SET) fwdSetXMm - hostStartFromAftMm else hostLen
                datum - snapToGridMm(datum - edgeLocalMm, gridMm)
            }
            val newEnd = snapped.coerceIn(lo, hostLen)
            WearAreaDragResult(startMm, newEnd - startMm)
        }
    }
}
