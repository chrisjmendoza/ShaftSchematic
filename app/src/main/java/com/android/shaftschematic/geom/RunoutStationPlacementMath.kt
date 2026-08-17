// file: app/src/main/java/com/android/shaftschematic/geom/RunoutStationPlacementMath.kt
package com.android.shaftschematic.geom

import com.android.shaftschematic.settings.RunoutConfig
import kotlin.math.max
import kotlin.math.min

/**
 * Authored runout-station placement — the pure math behind dragging a bubble along its
 * component and behind adding/removing a station once positions are authored.
 *
 * Dependency-free top-level functions (no Compose/Android imports, `geom` package) so the
 * clamping, insertion, and local↔shaft conversion rules are unit-testable in a plain JVM test
 * — the same posture as `geom/WearPitMath.kt` and `geom/RunoutReadingMath.kt`.
 *
 * Positions here are **component-local mm** measured from the AFT edge of the component's
 * aft-most drawn run, matching `com.android.shaftschematic.model.RunoutStationPlacement.axialMm`.
 * Nothing in this file knows about output units — `collectRunoutStations` maps the resolved
 * shaft mm through the caller's `xAtMm`, so the same authored position lands correctly on both
 * the linear canvas and the compressed PDF profile.
 */

/**
 * Smallest axial separation a drag may leave between two adjacent authored stations.
 *
 * Half an inch. Bubble-vs-bubble overlap is already impossible ([planRunoutBubbles] enforces
 * hard pitch floors), so this is not a collision guard — it stops two stations from landing on
 * effectively the same spot, where both leaders would point into one place and the sheet could
 * not say which reading was taken where. Shrinks on a short component so its stations never
 * lock ([effectiveStationGapMm]).
 */
const val RUNOUT_MIN_STATION_GAP_MM = 12.7f

/**
 * The usable separation for a component holding [count] stations across [spanMm]: the nominal
 * [RUNOUT_MIN_STATION_GAP_MM], reduced so `count` stations always fit. Without the reduction a
 * short liner with several stations would clamp every one of them to its current spot.
 */
fun effectiveStationGapMm(
    spanMm: Float,
    count: Int,
    minGapMm: Float = RUNOUT_MIN_STATION_GAP_MM,
): Float {
    if (spanMm <= 0f || count <= 1) return 0f
    return min(minGapMm, spanMm / (count + 1))
}

/**
 * Axial span a component's stations may occupy: aft-most run start to fwd-most run end.
 *
 * Deliberately NOT the summed run length — a body cut by a liner measures local distance in
 * shaft space **across** the gap, so one scalar keeps addressing every run. Stations stranded
 * in a gap are pulled onto metal by [resolveStationShaftMm].
 */
fun runoutComponentSpanMm(runs: List<RunoutComponentSpan>): Float {
    if (runs.isEmpty()) return 0f
    val start = runs.minOf { it.startMm }
    val end = runs.maxOf { it.startMm + it.lengthMm }
    return (end - start).coerceAtLeast(0f)
}

/** Shaft-space mm of a component's AFT edge — the origin every local position is measured from. */
fun runoutComponentOriginMm(runs: List<RunoutComponentSpan>): Float =
    runs.minOfOrNull { it.startMm } ?: 0f

/**
 * Convert a component-local authored position to shaft-space mm, pulled onto the nearest drawn
 * run when it lands in a gap between runs.
 *
 * The clamp is a **render-layer** repair, never a rewrite of storage: adding a liner over a
 * dragged body station must not silently edit the position the user authored, but the bubble
 * still has to point at metal — a leader landing on a liner while labelled with the body's name
 * reads as the wrong component. Removing the liner restores the authored spot exactly. Same
 * posture as the orphan rules for readings and pits.
 *
 * A position exactly midway between two runs takes the aft one, matching the aft-first
 * tie-break [apportionStations] already uses.
 */
fun resolveStationShaftMm(runs: List<RunoutComponentSpan>, localMm: Float): Float {
    if (runs.isEmpty()) return localMm
    val ordered = runs.sortedBy { it.startMm }
    val target = runoutComponentOriginMm(ordered) + localMm

    var bestMm = ordered.first().startMm
    var bestDist = Float.MAX_VALUE
    for (run in ordered) {
        val lo = run.startMm
        val hi = run.startMm + run.lengthMm
        if (target in lo..hi) return target
        val nearest = target.coerceIn(lo, hi)
        val dist = kotlin.math.abs(target - nearest)
        if (dist < bestDist) {
            bestDist = dist
            bestMm = nearest
        }
    }
    return bestMm
}

/**
 * Component-local positions of a component's currently drawn stations, in station-index order.
 *
 * Reading from the DRAWN stations rather than re-deriving keeps one source of truth: whatever
 * `collectRunoutStations` just placed is exactly what the user is looking at and reaching for.
 * The drag handler builds its neighbour-clamp set this way.
 */
fun localStationPositions(
    runs: List<RunoutComponentSpan>,
    stations: List<RunoutStationX>,
): List<Float> {
    val origin = runoutComponentOriginMm(runs)
    val span = runoutComponentSpanMm(runs)
    return stations.sortedBy { it.stationIndex }
        .map { (it.stationMm - origin).coerceIn(0f, span) }
}

/**
 * The component's complete current station set in component-local mm: derived physical
 * positions for [count] stations across [runs], with the user's pins from [authoredByIndex]
 * overlaid verbatim — what a count edit (+/−) plans against and then freezes wholesale.
 *
 * Derived values are the PHYSICAL convention (cell midpoints / edge insets, identical to what
 * the linear live canvas draws); a pin is stored **verbatim**, never coerced — a pin left
 * beyond the component by a later shrink stays authored (golden rule), and
 * [planStationInsertion] already widens its band around outliers. A derived value that lands
 * out of index order against a pin (a geometry edit moved it) is clamped to the pin — the
 * derived one yields, matching `collectRunoutStations`' order repair — so insertion indices
 * always read AFT→FWD.
 */
fun currentLocalStationPositions(
    runs: List<RunoutComponentSpan>,
    count: Int,
    authoredByIndex: Map<Int, Float>,
): List<Float> {
    if (runs.isEmpty() || count <= 0) return emptyList()
    val ordered = runs.sortedBy { it.startMm }
    val kind = ordered.first().kind
    val perRun = apportionStations(ordered.map { it.lengthMm }, count)
    val origin = runoutComponentOriginMm(ordered)
    val span = runoutComponentSpanMm(ordered)

    val out = ArrayList<Float>(count)
    var index = 0
    ordered.forEachIndexed { runIdx, run ->
        runoutStationPositionsMm(
            startMm = run.startMm,
            lengthMm = run.lengthMm,
            count = perRun[runIdx],
            useEdgeInset = kind != RunoutComponentKind.BODY,
        ).forEach { mm ->
            out.add(authoredByIndex[index] ?: (mm - origin).coerceIn(0f, span))
            index++
        }
    }

    for (i in 1 until out.size) {
        if (i !in authoredByIndex && out[i] < out[i - 1]) out[i] = out[i - 1]
    }
    for (i in out.size - 2 downTo 0) {
        if (i !in authoredByIndex && out[i] > out[i + 1]) out[i] = out[i + 1]
    }
    return out
}

/**
 * Where a dragged station may actually land: [targetMm] held inside the component and kept
 * [minGapMm] clear of the stations either side of it.
 *
 * Neighbour clamping is what keeps station numbering readable AFT→FWD on the printed sheet and
 * keeps a typed TIR on its own bubble — a station allowed to cross its neighbour would either
 * renumber under the readings or print station 3 to the left of station 2.
 *
 * @param positionsMm All of the component's authored positions, AFT→FWD.
 * @param index Which of them is being dragged.
 */
fun clampDraggedStationMm(
    positionsMm: List<Float>,
    index: Int,
    targetMm: Float,
    spanMm: Float,
    minGapMm: Float = RUNOUT_MIN_STATION_GAP_MM,
): Float {
    if (index !in positionsMm.indices) return targetMm.coerceIn(0f, max(spanMm, 0f))
    val gap = effectiveStationGapMm(spanMm, positionsMm.size, minGapMm)
    val lo = if (index > 0) positionsMm[index - 1] + gap else 0f
    val hi = if (index < positionsMm.size - 1) positionsMm[index + 1] - gap else spanMm
    // A component too tight to honour the gap collapses to the one point between its
    // neighbours rather than refusing the drag outright.
    if (lo > hi) return (lo + hi) * 0.5f
    return targetMm.coerceIn(lo, hi)
}

/** Where a newly added station lands among a component's authored positions. */
data class RunoutStationInsertion(
    /** Station index the new station takes; everything from here up shifts one higher. */
    val index: Int,
    /** Component-local position, mm from the AFT edge. */
    val axialMm: Float,
)

/**
 * Place one more station among [positionsMm] (on-device request):
 *
 * - **No stations yet** — the component's midpoint, matching the single-station default.
 * - **Exactly one** — whichever of the two-station default positions is farther from the one
 *   already placed, i.e. "the normal location a second bubble would be added". The existing
 *   station never moves to meet it: it is authored.
 * - **Two or more** — the midpoint of the widest gap. Gaps run neighbour-to-neighbour plus each
 *   end of the usable band to its nearest station, so the ordinary case (default stations at
 *   the two ends) inserts between them, while stations clustered at one end make the empty end
 *   the widest gap instead of squeezing another bubble into the cluster.
 *
 * The usable band is the edge-inset band for tapers/liners and the full span for bodies —
 * [runoutStationPositionsMm]'s own convention — widened where needed to contain positions the
 * user has already dragged outside it.
 */
fun planStationInsertion(
    positionsMm: List<Float>,
    spanMm: Float,
    useEdgeInset: Boolean,
    edgeInsetMm: Float = RunoutConfig.RUNOUT_EDGE_INSET_MM,
): RunoutStationInsertion {
    val span = spanMm.coerceAtLeast(0f)
    if (positionsMm.isEmpty()) return RunoutStationInsertion(0, span * 0.5f)

    val sorted = positionsMm.sorted()

    if (sorted.size == 1) {
        val existing = sorted[0]
        val defaults = runoutStationPositionsMm(
            startMm = 0f, lengthMm = span, count = 2,
            useEdgeInset = useEdgeInset, edgeInsetMm = edgeInsetMm,
        )
        val pick = defaults.maxByOrNull { kotlin.math.abs(it - existing) } ?: (span * 0.5f)
        return RunoutStationInsertion(index = if (pick < existing) 0 else 1, axialMm = pick)
    }

    val inset = if (useEdgeInset) min(edgeInsetMm, span * RUNOUT_EDGE_INSET_MAX_FRACTION) else 0f
    val bandLo = min(inset, sorted.first())
    val bandHi = max(span - inset, sorted.last())

    var bestWidth = bandLo.let { sorted.first() - it }
    var best = RunoutStationInsertion(0, (bandLo + sorted.first()) * 0.5f)
    for (i in 0 until sorted.size - 1) {
        val width = sorted[i + 1] - sorted[i]
        if (width > bestWidth) {
            bestWidth = width
            best = RunoutStationInsertion(i + 1, (sorted[i] + sorted[i + 1]) * 0.5f)
        }
    }
    val tailWidth = bandHi - sorted.last()
    if (tailWidth > bestWidth) {
        best = RunoutStationInsertion(sorted.size, (sorted.last() + bandHi) * 0.5f)
    }
    return best
}

/** Apply [insertion] to a component's authored positions, keeping them AFT→FWD. */
fun insertStationPosition(
    positionsMm: List<Float>,
    insertion: RunoutStationInsertion,
): List<Float> {
    val out = positionsMm.toMutableList()
    out.add(insertion.index.coerceIn(0, out.size), insertion.axialMm)
    return out
}

/**
 * Which station "−" removes from an AUTHORED component — the geometric inverse of
 * [planStationInsertion].
 *
 * Two rules, in order:
 * 1. **Prefer an unmeasured station.** A component with a blank bubble on it has something to
 *    give up; deleting a typed TIR while one sits there would throw away a measurement for no
 *    reason. Only when every station has been read does one of them have to go.
 * 2. **Among those, take the most redundant** — the station sitting closest to the midpoint
 *    between its two neighbours (the usable band's edge standing in for a missing neighbour).
 *    A station exactly at that midpoint adds nothing its neighbours do not already cover, which
 *    is precisely the station [planStationInsertion] would have put there. So "−" undoes "+":
 *    add a station between two dragged bubbles, change your mind, and the pair you placed come
 *    back untouched. Ties take the FWD-most, the aft-authored-first convention.
 *
 * Derived components keep the plain "drop the highest index" rule — their positions all re-space
 * on any count change, so which index goes is not something the user can see.
 *
 * Returns `-1` when there is nothing to remove.
 */
fun authoredStationIndexToRemove(
    positionsMm: List<Float>,
    spanMm: Float,
    useEdgeInset: Boolean,
    edgeInsetMm: Float = RunoutConfig.RUNOUT_EDGE_INSET_MM,
    hasReading: (Int) -> Boolean,
): Int {
    val n = positionsMm.size
    if (n == 0) return -1
    if (n == 1) return 0

    val unmeasured = positionsMm.indices.filter { !hasReading(it) }
    val candidates = if (unmeasured.isEmpty()) positionsMm.indices.toList() else unmeasured

    val span = spanMm.coerceAtLeast(0f)
    val inset = if (useEdgeInset) min(edgeInsetMm, span * RUNOUT_EDGE_INSET_MAX_FRACTION) else 0f
    val bandLo = min(inset, positionsMm.first())
    val bandHi = max(span - inset, positionsMm.last())

    var best = candidates.first()
    var bestRedundancy = Float.MAX_VALUE
    for (i in candidates) {
        val prev = if (i > 0) positionsMm[i - 1] else bandLo
        val next = if (i < n - 1) positionsMm[i + 1] else bandHi
        val redundancy = kotlin.math.abs(positionsMm[i] - (prev + next) * 0.5f)
        if (redundancy <= bestRedundancy) {
            bestRedundancy = redundancy
            best = i
        }
    }
    return best
}

/** Remove one station from a component's authored positions, keeping the rest AFT→FWD. */
fun removeStationPosition(positionsMm: List<Float>, index: Int): List<Float> {
    if (index !in positionsMm.indices) return positionsMm
    return positionsMm.toMutableList().apply { removeAt(index) }
}
