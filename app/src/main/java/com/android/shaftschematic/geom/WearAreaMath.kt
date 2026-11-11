package com.android.shaftschematic.geom

import com.android.shaftschematic.model.WearSpot

/**
 * WearAreaMath — where a wear area ([WearSpot]) sits on the shaft, for every whole-shaft draw
 * site (the wear sheet's main profile, the consolidated sheet's wear-area marks, the Wear tab's
 * count badges).
 *
 * A wear area is hosted by a liner or by one drawn body run (explicit or bare shaft), keyed by
 * the host's resolved component id and stored host-local from the host's AFT edge. This file
 * turns that into shaft-space spans: ONE resolution, so every site agrees on which areas draw
 * and where. An area whose host no longer resolves is an orphan and yields nothing — the
 * render-layer orphan rule pits and Ø readings follow; the stored record is never touched.
 *
 * Pure — no Android, no resolve: callers hand in the hosts (`ui/resolved/WearAreaHosts.kt`).
 */

/**
 * A component that can carry wear areas: a liner, or one drawn body run. [startMm] is its drawn
 * AFT edge in shaft space, [diaMm] the diameter the band draws at (liner OD, body Ø).
 */
data class WearAreaHost(
    val id: String,
    val startMm: Float,
    val lengthMm: Float,
    val diaMm: Float,
    val isLiner: Boolean,
)

/** One wear area resolved onto its host: the clamped band in shaft space, mm. */
data class WearAreaSpan(
    val spotId: String,
    val host: WearAreaHost,
    val startMm: Float,
    val endMm: Float,
) {
    val lengthMm: Float get() = endMm - startMm
}

/**
 * Every area of [spots] whose host is in [hosts], clamped to the host's own span `[0, length]`
 * and moved to shaft space, in record order. Orphans (host not in [hosts]), degenerate hosts and
 * areas the clamp leaves empty are skipped. The clamp is for drawing only — a host shortened
 * under an area leaves the stored values alone.
 *
 * A host is one drawn run, already trimmed around the liners and tapers beside it, so a body's
 * area never draws across a neighbouring component.
 */
fun resolveWearAreaSpans(spots: List<WearSpot>, hosts: List<WearAreaHost>): List<WearAreaSpan> {
    if (spots.isEmpty() || hosts.isEmpty()) return emptyList()
    val byId = hosts.associateBy { it.id }
    return spots.mapNotNull { spot ->
        val host = byId[spot.linerId] ?: return@mapNotNull null
        if (host.lengthMm <= 0f || host.diaMm <= 0f) return@mapNotNull null
        val s = spot.startMm.coerceIn(0f, host.lengthMm)
        val e = (spot.startMm + spot.lengthMm).coerceIn(0f, host.lengthMm)
        if (e <= s) return@mapNotNull null
        WearAreaSpan(spot.id, host, host.startMm + s, host.startMm + e)
    }
}

/**
 * The parts of the drawn span `[x0, x1]` that lie outside every range of [gaps] — a body's wear
 * band on a sheet whose body run is cut by an S-break: the gap is bare paper, so the band stops
 * at each side of it. No gaps returns the span itself.
 */
fun spanPiecesClearOf(
    x0: Float,
    x1: Float,
    gaps: List<ClosedFloatingPointRange<Float>>,
): List<ClosedFloatingPointRange<Float>> {
    val lo = minOf(x0, x1)
    val hi = maxOf(x0, x1)
    if (hi <= lo) return emptyList()
    var pieces = listOf(lo..hi)
    gaps.forEach { gap ->
        pieces = pieces.flatMap { p ->
            if (gap.endInclusive <= p.start || gap.start >= p.endInclusive) listOf(p)
            else buildList {
                if (gap.start > p.start) add(p.start..gap.start)
                if (gap.endInclusive < p.endInclusive) add(gap.endInclusive..p.endInclusive)
            }
        }
    }
    return pieces
}
