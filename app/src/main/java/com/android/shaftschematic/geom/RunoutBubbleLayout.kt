package com.android.shaftschematic.geom

import com.android.shaftschematic.settings.RunoutConfig
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * RunoutBubbleLayout — shared placement engine for runout-sheet measurement bubbles.
 *
 * Used by BOTH the PDF composer (`RunoutPdfComposer`) and the live canvas preview
 * (`RunoutRoute`) so the two renderings are guaranteed identical. All coordinates are
 * abstract output units (pt for the PDF, px for the canvas); the engine never converts
 * mm — callers map mm → output x before planning.
 *
 * ## Layout rules (matching the hand-drawn shop convention)
 * 1. Bubbles hang below the shaft in horizontal rows. Row 0 is closest to the shaft.
 *    All bubbles in a row share one centre Y (rows are aligned across the whole sheet).
 * 2. Within a component, consecutive stations ALTERNATE rows (0,1,0,1,…). The
 *    alternating heights let adjacent bubbles overlap in x without touching, which keeps
 *    each bubble close to its measurement station instead of splaying sideways.
 * 3. Spacing invariants between x-adjacent bubbles (centre-to-centre horizontal):
 *      - same row      → ≥ `sameRowPitch` = 2·radius + minGap  (circles never touch)
 *      - different row → ≥ `crossRowPitch` = radius + minGap   (a vertical leader drop at
 *        one bubble's x clears every circle in the rows above it)
 *    Because `crossRowPitch × 2 ≥ sameRowPitch`, enforcing only ADJACENT-pair gaps is
 *    sufficient: any same-row pair k ≥ 2 apart accumulates at least `sameRowPitch`.
 *    Together with `rowStep` = 2·radius + minGap this makes bubble-bubble contact
 *    geometrically impossible, at any density.
 * 4. Bubble x positions are solved as a least-squares fit to the station positions
 *    subject to the pitch constraints (isotonic regression / pool-adjacent-violators),
 *    so bubbles sit directly under their stations whenever there is room and clusters
 *    stay centred over their stations when there is not.
 * 5. Leaders are straight station→bubble-top diagonals when that segment provably clears
 *    every other bubble and leader. Otherwise the leader becomes a DOGLEG: a diagonal
 *    from the station to an elbow ABOVE the first bubble row at the bubble's x, then a
 *    vertical drop to the bubble top. The diagonal lives entirely above every circle and
 *    the drop is guaranteed clear by invariant 3 — a dogleg can never enter a bubble.
 * 6. Two rows is not just the shop convention — it is width-optimal. Every leader's
 *    final drop passes through every row band above its bubble and needs its own
 *    horizontal lane (`crossRowPitch`) past the circles there, so each bubble consumes
 *    ~one lane of width REGARDLESS of how deep it sits. Deeper row cycles therefore
 *    cannot reduce the required width — they only add page height. When the station
 *    count cannot fit the content width at minimum clearances (~27 stations on a letter
 *    page), spacing compresses uniformly and the plan flags itself
 *    ([RunoutBubblePlan.compressed]; [RunoutBubbleResult.unresolvedCollisions] reports
 *    anything the repair pass could not fix in that degenerate state).
 * 7. Even-spread waterfill (on-device request, hand-sheet reference): the minimum pitches
 *    are collision floors, not a layout goal — a sheet that packs its bubbles at the
 *    minimums under compressed runs leaves the rest of the width empty and the leaders
 *    tangled. When the page has slack, every adjacent gap floor rises toward one common
 *    level (Σ max(gap_i, L) = available, capped at [RunoutBubbleGeometry.spreadPitch]),
 *    so bubbles distribute the space under the shaft evenly — leaders stay straight
 *    wherever they clear, and a rerouted one is the clean vertical-drop dogleg.
 *    A dense sheet divides the width evenly among its bubbles; a sparse sheet
 *    spreads comfortably near its stations (the cap), never flung to the page corners.
 *    Floors only ever grow — never below the geometric minimum — so no collision
 *    guarantee changes, and a page with no slack keeps the exact minimum-pitch layout.
 */

// ─────────────────────────────────────────────────────────────────────────────
// Station positions (mm domain)
// ─────────────────────────────────────────────────────────────────────────────

/** Fraction of a component's length that caps the edge inset for short tapers/liners. */
const val RUNOUT_EDGE_INSET_MAX_FRACTION = 0.20f

/**
 * Multiplier on [RunoutBubbleGeometry.sameRowPitch] that caps the even-spread waterfill
 * level ([RunoutBubbleGeometry.spreadPitch]): on a page with room to spare, adjacent
 * bubbles spread apart up to this comfortable pitch — near their stations, never flung to
 * the page corners. A dense sheet's level lands below this cap (the width simply divides
 * among the bubbles, the hand-sheet look); a sparse sheet stops here.
 */
const val BUBBLE_SPREAD_PITCH_CAP_FACTOR = 1.5f

/**
 * Axial mm positions of [count] measurement stations within one component.
 *
 * - Bodies (`useEdgeInset = false`): cell midpoints, `(i + 0.5) · length / count`,
 *   evenly covering the full length. (When the caller can invert its x mapping,
 *   [collectRunoutStations] instead places body stations evenly across the DRAWN span —
 *   see its KDoc.)
 * - Tapers / liners (`useEdgeInset = true`): first/last stations inset from each edge by
 *   `min(edgeInsetMm, length × 20%)` so readings land on the cylindrical run, not the
 *   transition slope; interior stations spread linearly between.
 */
fun runoutStationPositionsMm(
    startMm: Float,
    lengthMm: Float,
    count: Int,
    useEdgeInset: Boolean,
    edgeInsetMm: Float = RunoutConfig.RUNOUT_EDGE_INSET_MM,
): List<Float> {
    if (count <= 0 || lengthMm <= 0f) return emptyList()
    if (count == 1) return listOf(startMm + lengthMm * 0.5f)
    return if (useEdgeInset) {
        val inset = min(edgeInsetMm, lengthMm * RUNOUT_EDGE_INSET_MAX_FRACTION)
        val span = lengthMm - 2f * inset
        List(count) { i -> startMm + inset + span * (i.toFloat() / (count - 1)) }
    } else {
        List(count) { i -> startMm + (i + 0.5f) * lengthMm / count }
    }
}

/** Component type for runout station assignment — determines default count and inset behaviour. */
enum class RunoutComponentKind { BODY, TAPER, LINER }

/**
 * Station count for a component with no user override — **length-driven**, one station per
 * [RunoutConfig.RUNOUT_STATION_INTERVAL_MM] (20 inches) of drawn component.
 *
 * - **Bodies**: `ceil(length / interval)`, at least 1. A body always gets a reading, and never
 *   gets three just for existing — the flat "3 per body" this replaced put three stations on a
 *   1–2" leftover run and only three on a 100" line shaft (on-device report).
 * - **Tapers**: 2, whatever the length. The two stations are the shop convention (one inset
 *   from each of the S.E.T. and L.E.T. ends), not a density choice.
 * - **Liners**: the interval, floored at 2 — the edge-inset convention needs both ends, and a
 *   long stern-tube liner earns extra stations.
 *
 * Capped at [RunoutConfig.MAX_STATIONS_PER_COMPONENT] so a very long shaft cannot derive more
 * bubbles than the page can seat; a user override may still exceed the cap. Zero-length
 * components get 0 — nothing to measure.
 */
fun defaultStationCount(kind: RunoutComponentKind, lengthMm: Float): Int {
    if (lengthMm <= 0f) return 0
    val byInterval = ceil(lengthMm / RunoutConfig.RUNOUT_STATION_INTERVAL_MM).toInt()
    return when (kind) {
        RunoutComponentKind.TAPER -> RunoutConfig.TAPER_DEFAULT_COUNT
        RunoutComponentKind.BODY ->
            byInterval.coerceIn(1, RunoutConfig.MAX_STATIONS_PER_COMPONENT)
        RunoutComponentKind.LINER ->
            byInterval.coerceIn(RunoutConfig.LINER_DEFAULT_COUNT, RunoutConfig.MAX_STATIONS_PER_COMPONENT)
    }
}

/**
 * Split [total] stations across a component's drawn runs in proportion to their lengths
 * (largest-remainder apportionment).
 *
 * A body cut by liners draws as several runs but is ONE component to the user: the carousel
 * names it once, the station editor gives it one row, and one override governs it. So the
 * count is derived once for the whole body and handed out here — never re-derived per run,
 * which is what made a short leftover fragment collect a full default's worth of bubbles.
 *
 * A run too short to earn a station gets 0 (you would not put an indicator on a 1" sliver).
 * Returns all-zero when [total] ≤ 0 or every run is empty.
 */
fun apportionStations(runLengthsMm: List<Float>, total: Int): List<Int> {
    val n = runLengthsMm.size
    if (n == 0) return emptyList()
    if (total <= 0) return List(n) { 0 }
    val lengths = runLengthsMm.map { max(it, 0f) }
    val sum = lengths.sum()
    if (sum <= 0f) return List(n) { 0 }

    val exact = lengths.map { it / sum * total }
    val floors = exact.map { it.toInt() }
    var remaining = total - floors.sum()

    // Hand the leftovers to the largest fractional parts; ties break toward the aft-most run
    // (stable sort on descending remainder) so the layout is deterministic.
    val order = exact.indices.sortedByDescending { exact[it] - floors[it] }
    val out = floors.toMutableList()
    var i = 0
    while (remaining > 0 && i < order.size) {
        out[order[i]] = out[order[i]] + 1
        remaining--
        i++
        if (i == order.size && remaining > 0) i = 0  // more stations than runs — wrap
    }
    return out
}

/** A component span eligible for runout stations, in physical mm. */
data class RunoutComponentSpan(
    val id: String,
    val kind: RunoutComponentKind,
    val startMm: Float,
    val lengthMm: Float,
)

/**
 * One measurement station: which component it belongs to, its axial mm, and its output-space x.
 *
 * @property stationIndex 0-based ordinal of this station within its component (AFT→FWD order,
 *   assigned by [collectRunoutStations]). Stable key for attaching a [com.android.shaftschematic.model.RunoutReading]
 *   to a bubble — travels with the station through the plan's stationX sort.
 */
data class RunoutStationX(
    val componentId: String,
    val stationMm: Float,
    val stationX: Float,
    val stationIndex: Int = 0,
)

/**
 * Expand component spans into the flat station list, applying per-component count overrides
 * and the length-derived defaults ([defaultStationCount]).
 *
 * Station placement per kind (on-device request):
 * - **Tapers / liners**: physical mm with the edge inset — the worn areas usually don't
 *   reach a liner's very edges, so near-edge readings are the best runout spots.
 * - **Bodies**: a body surface is uniform, so the exact physical spot is free — stations
 *   spread **evenly across the DRAWN span** (cell midpoints in output x, inverted back to
 *   mm via [mmAtX]). Under the compressed hand-sheet mapping, physical midpoints bunch
 *   into the foreshortened run; drawn-even placement keeps the sheet readable. When the
 *   caller has no inverse ([mmAtX] = null), bodies fall back to physical cell midpoints
 *   (identical under a linear mapping).
 */
fun collectRunoutStations(
    spans: List<RunoutComponentSpan>,
    overrides: Map<String, Int>,
    xAtMm: (Float) -> Float,
    mmAtX: ((Float) -> Float)? = null,
): List<RunoutStationX> {
    val out = mutableListOf<RunoutStationX>()

    // Group by component id FIRST: a body split by liners arrives as several runs sharing one
    // id, and it is one component to the user — one carousel name, one station-editor row, one
    // override. The count is therefore derived once from the total drawn length and then
    // apportioned across the runs, never re-derived per run.
    val byComponent = spans
        .filter { it.lengthMm > 0f }
        .sortedBy { it.startMm }
        .groupBy { it.id }
        .entries
        .sortedBy { (_, runs) -> runs.minOf { it.startMm } }

    for ((id, runs) in byComponent) {
        val kind = runs.first().kind
        val totalLengthMm = runs.sumOf { it.lengthMm.toDouble() }.toFloat()
        val count = overrides[id] ?: defaultStationCount(kind, totalLengthMm)
        if (count <= 0) continue

        val perRun = apportionStations(runs.map { it.lengthMm }, count)

        // stationIndex runs continuously AFT→FWD across every run of the component, so a
        // reading keyed (componentId, stationIndex) identifies exactly one bubble no matter
        // how the body is fragmented.
        var nextIndex = 0
        runs.forEachIndexed { runIdx, span ->
            val runCount = perRun[runIdx]
            if (runCount <= 0) return@forEachIndexed

            if (kind == RunoutComponentKind.BODY && mmAtX != null) {
                val x0 = xAtMm(span.startMm)
                val x1 = xAtMm(span.startMm + span.lengthMm)
                repeat(runCount) { i ->
                    val xs = x0 + (i + 0.5f) * (x1 - x0) / runCount
                    out.add(RunoutStationX(id, mmAtX(xs), xs, stationIndex = nextIndex++))
                }
            } else {
                runoutStationPositionsMm(
                    startMm = span.startMm,
                    lengthMm = span.lengthMm,
                    count = runCount,
                    useEdgeInset = kind != RunoutComponentKind.BODY,
                ).forEach { mm ->
                    out.add(RunoutStationX(id, mm, xAtMm(mm), stationIndex = nextIndex++))
                }
            }
        }
    }
    return out
}

// ─────────────────────────────────────────────────────────────────────────────
// Geometry parameters and results (output units)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Bubble geometry in output units.
 *
 * @param radius      Bubble circle radius.
 * @param minGap      Minimum clear distance kept between circle edges, and the clearance
 *                    used when verifying leader lines against circles.
 * @param shortLeader Vertical distance from the deepest shaft surface to the top of row 0.
 * @param contentLeft  Leftmost x a bubble edge may reach.
 * @param contentRight Rightmost x a bubble edge may reach.
 */
data class RunoutBubbleGeometry(
    val radius: Float,
    val minGap: Float,
    val shortLeader: Float,
    val contentLeft: Float,
    val contentRight: Float,
) {
    /** Min centre-to-centre dx for x-adjacent bubbles on the SAME row. */
    val sameRowPitch: Float get() = 2f * radius + minGap

    /** Min centre-to-centre dx for x-adjacent bubbles on DIFFERENT rows. */
    val crossRowPitch: Float get() = radius + minGap

    /** Vertical distance between row centres. */
    val rowStep: Float get() = 2f * radius + minGap

    /**
     * Cap on the even-spread waterfill level (see [planRunoutBubbles] rule 7): the widest
     * pitch a sparse page spreads adjacent bubbles to. Comfort spacing for hand-written
     * readings, not a collision requirement.
     */
    val spreadPitch: Float get() = sameRowPitch * BUBBLE_SPREAD_PITCH_CAP_FACTOR
}

/** One vertex of a leader polyline. */
data class LeaderVertex(val x: Float, val y: Float)

/** A fully placed bubble: circle centre, row, and its leader polyline (2 or 3 vertices). */
data class PlacedRunoutBubble(
    val componentId: String,
    val stationMm: Float,
    val stationX: Float,
    val surfaceY: Float,
    val bubbleX: Float,
    val bubbleCenterY: Float,
    val row: Int,
    val leader: List<LeaderVertex>,
    /** 0-based ordinal of this bubble's station within its component — see [RunoutStationX.stationIndex]. */
    val stationIndex: Int = 0,
)

/**
 * @param unresolvedCollisions Number of collisions the repair pass could not eliminate.
 *   0 in every non-degenerate configuration; > 0 only when spacing had to be compressed
 *   below the geometric minimum to fit the page (absurd station counts).
 */
data class RunoutBubbleResult(
    val bubbles: List<PlacedRunoutBubble>,
    val unresolvedCollisions: Int,
)

// ─────────────────────────────────────────────────────────────────────────────
// Phase 1 — horizontal solve (rows + bubble x). Independent of vertical layout.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Solve rows and bubble x positions for all stations. Purely horizontal — callers can
 * use [RunoutBubblePlan.rowCount] / [RunoutBubblePlan.sectionHeight] to size the
 * vertical layout BEFORE fixing the shaft centreline, then call [RunoutBubblePlan.finish].
 */
fun planRunoutBubbles(
    stations: List<RunoutStationX>,
    geom: RunoutBubbleGeometry,
): RunoutBubblePlan {
    val sorted = stations.sortedBy { it.stationX }
    val n = sorted.size
    if (n == 0) return RunoutBubblePlan(sorted, FloatArray(0), IntArray(0), geom, compressed = false)

    val desired = FloatArray(n) { sorted[it].stationX }
    val available = (geom.contentRight - geom.contentLeft) - 2f * geom.radius

    // Two-row alternation is width-optimal (see class KDoc rule 6): in tight regions the
    // alternation + boundary phase flip make every binding adjacent pair a cross-row pair,
    // which is the geometric minimum pitch. Deeper row cycles cannot pack tighter.
    val rows = assignRows(sorted, desired, geom)
    val gaps = FloatArray(max(n - 1, 0)) { i ->
        if (rows[i + 1] == rows[i]) geom.sameRowPitch else geom.crossRowPitch
    }

    // Even-spread waterfill (see class KDoc rule 7): raise every adjacent gap floor toward
    // one common level so the bubbles distribute the width under the shaft evenly instead
    // of bunching beneath compressed runs (on-device request, hand-sheet reference: the
    // circles spread across the sheet and the pointer lines stay easy to follow). The
    // level solves Σ max(gap_i, L) = available, capped at `spreadPitch` — a dense sheet
    // divides the width evenly among its bubbles, a sparse sheet spreads comfortably near
    // its stations, never flung to the page corners. Floors only ever GROW here (never
    // below the geometric minimum), so every collision guarantee is untouched, and a page
    // with no slack gets zero widening.
    val baseNeeded = gaps.sum()
    if (available - baseNeeded > 0f && n > 1) {
        fun totalAt(level: Float): Float {
            var t = 0f
            for (g in gaps) t += max(g, level)
            return t
        }
        val cap = geom.spreadPitch
        val level = if (totalAt(cap) <= available) {
            cap
        } else {
            var lo = 0f
            var hi = cap
            repeat(30) {
                val mid = (lo + hi) / 2f
                if (totalAt(mid) <= available) lo = mid else hi = mid
            }
            lo
        }
        for (i in gaps.indices) gaps[i] = max(gaps[i], level)
    }

    // Degenerate fallback: compress uniformly so the group still fits the page.
    // Clearance guarantees no longer hold; the plan is flagged and finish() reports leftovers.
    val needed = gaps.sum()
    val compressed = needed > available && needed > 0f
    if (compressed) {
        val f = available / needed
        for (i in gaps.indices) gaps[i] *= f
    }

    return RunoutBubblePlan(sorted, solveBubbleX(desired, gaps, geom), rows, geom, compressed)
}

/**
 * Assign rows: alternate 0,1 within each component group; single-station groups sit on
 * row 0. When the previous group ended on row 0 close enough that a same-row pair would
 * form across the boundary, the next group starts on row 1 instead — keeping the
 * alternating rhythm across component boundaries.
 */
private fun assignRows(
    sorted: List<RunoutStationX>,
    desired: FloatArray,
    geom: RunoutBubbleGeometry,
): IntArray {
    val n = sorted.size
    val rows = IntArray(n)
    var i = 0
    var prevRow = -1
    var prevX = Float.NEGATIVE_INFINITY
    while (i < n) {
        var j = i
        while (j < n && sorted[j].componentId == sorted[i].componentId) j++
        val size = j - i
        var start = 0
        if (prevRow == 0 && desired[i] - prevX < geom.sameRowPitch) start = 1
        for (k in 0 until size) rows[i + k] = if (size == 1) start else (start + k) % 2
        prevRow = rows[j - 1]
        prevX = desired[j - 1]
        i = j
    }
    return rows
}

/**
 * Least-squares bubble x fit: minimise Σ(x_i − station_i)² subject to
 * x_{i+1} − x_i ≥ gap_i and page bounds. Solved by substituting out the gaps and running
 * isotonic regression (pool-adjacent-violators), which keeps every bubble under its
 * station when there is room and mean-centres clusters over their stations when not.
 */
private fun solveBubbleX(
    desired: FloatArray,
    gaps: FloatArray,
    geom: RunoutBubbleGeometry,
): FloatArray {
    val n = desired.size
    val loX = geom.contentLeft + geom.radius
    val hiX = geom.contentRight - geom.radius
    if (n == 1) return floatArrayOf(desired[0].coerceIn(loX, hiX))

    val g = FloatArray(n)
    for (i in 1 until n) g[i] = g[i - 1] + gaps[i - 1]

    val u = isotonicNonDecreasing(DoubleArray(n) { (desired[it] - g[it]).toDouble() })

    // Bounds in u-space: the lower bound is tightest at i=0, the upper at i=n−1, and both
    // clamps preserve monotonicity, so per-index bounds reduce to two constants.
    // hi can dip a float-epsilon below lo when the (compressed) span exactly fills the
    // width — collapse to lo in that case rather than throw.
    val lo = loX.toDouble()
    val hi = max((hiX - g[n - 1]).toDouble(), lo)
    return FloatArray(n) { i -> ((u[i].coerceIn(lo, hi)) + g[i]).toFloat() }
}

/**
 * Classic pool-adjacent-violators isotonic regression (non-decreasing, unit weights).
 * Internal (not private) so `WearDiaCalloutLayout` reuses the same solver for its
 * label-spread x fit.
 */
internal fun isotonicNonDecreasing(t: DoubleArray): DoubleArray {
    val n = t.size
    val mean = DoubleArray(n)
    val weight = IntArray(n)
    var m = 0
    for (v in t) {
        mean[m] = v
        weight[m] = 1
        m++
        while (m > 1 && mean[m - 2] >= mean[m - 1]) {
            val w = weight[m - 2] + weight[m - 1]
            mean[m - 2] = (mean[m - 2] * weight[m - 2] + mean[m - 1] * weight[m - 1]) / w
            weight[m - 2] = w
            m--
        }
    }
    val out = DoubleArray(n)
    var idx = 0
    for (b in 0 until m) repeat(weight[b]) { out[idx++] = mean[b] }
    return out
}

// ─────────────────────────────────────────────────────────────────────────────
// Phase 2 — vertical placement, leader routing, collision verification
// ─────────────────────────────────────────────────────────────────────────────

class RunoutBubblePlan internal constructor(
    /** Stations sorted by stationX — parallel to [bubbleX] and [rows]. */
    val stations: List<RunoutStationX>,
    val bubbleX: FloatArray,
    val rows: IntArray,
    val geom: RunoutBubbleGeometry,
    /**
     * True when the station count physically cannot fit the content width at minimum
     * clearances even at the deepest row cycle, and spacing was compressed to fit.
     * The no-collision guarantees hold only when this is false.
     */
    val compressed: Boolean,
) {
    /** Number of bubble rows used: 0 when there are no stations, otherwise 1 or 2. */
    val rowCount: Int = (rows.maxOrNull() ?: -1) + 1

    /**
     * Vertical space needed below the deepest shaft surface: leader gap + all rows +
     * [tailGap] breathing room. 0 when there are no stations.
     */
    fun sectionHeight(tailGap: Float): Float =
        if (rowCount == 0) 0f
        else geom.shortLeader + 2f * geom.radius + (rowCount - 1) * geom.rowStep + tailGap

    /**
     * Fix vertical positions and route leaders.
     *
     * @param anchorY      Y of the deepest drawn shaft point (row 0 hangs [RunoutBubbleGeometry.shortLeader]
     *                     below this). Coerced up to the deepest station surface if needed.
     * @param surfaceYAtMm Shaft outer-surface y at a station's axial mm — leaders originate here.
     */
    fun finish(anchorY: Float, surfaceYAtMm: (Float) -> Float): RunoutBubbleResult {
        val n = stations.size
        if (n == 0) return RunoutBubbleResult(emptyList(), 0)

        val surfaceY = FloatArray(n) { surfaceYAtMm(stations[it].stationMm) }
        val anchor = max(anchorY, surfaceY.max())
        val centerY = FloatArray(n) { anchor + geom.shortLeader + geom.radius + rows[it] * geom.rowStep }

        // Dogleg geometry. The diagonal of every dogleg runs between the same two
        // horizontal lines — a common departure line at the deepest shaft surface and a
        // common elbow line just above the row-0 circle tops. Station order equals bubble
        // order (both monotonic), so two dogleg diagonals can never properly cross; the
        // elbow clearance keeps diagonals above every circle; and the vertical stub/drop
        // segments are parallel. A dogleg therefore cannot collide with anything except a
        // straight leader — which the repair loop then also converts. This is what makes
        // the repair provably converge to zero collisions (when spacing isn't compressed).
        val elbowY = anchor + geom.shortLeader - 0.75f * geom.minGap
        val clearance = 0.5f * geom.minGap

        val paths = Array(n) { i ->
            listOf(
                LeaderVertex(stations[i].stationX, surfaceY[i]),
                LeaderVertex(bubbleX[i], centerY[i] - geom.radius),
            )
        }
        val dogleg = BooleanArray(n)
        fun makeDogleg(i: Int) {
            dogleg[i] = true
            paths[i] = listOf(
                LeaderVertex(stations[i].stationX, surfaceY[i]),
                // Vertical stub down to the common departure line (zero-length when the
                // station already sits on the deepest surface — harmless to draw/test).
                LeaderVertex(stations[i].stationX, anchor),
                LeaderVertex(bubbleX[i], elbowY),
                LeaderVertex(bubbleX[i], centerY[i] - geom.radius),
            )
        }

        fun pathHitsForeignCircle(i: Int): Boolean {
            val p = paths[i]
            for (j in 0 until n) {
                if (j == i) continue
                for (s in 0 until p.size - 1) {
                    if (segmentIntersectsCircle(p[s], p[s + 1], bubbleX[j], centerY[j], geom.radius + clearance)) {
                        return true
                    }
                }
            }
            return false
        }

        fun pathsCross(i: Int, j: Int): Boolean {
            val a = paths[i]
            val b = paths[j]
            for (s in 0 until a.size - 1) for (q in 0 until b.size - 1) {
                if (segmentsProperlyIntersect(a[s], a[s + 1], b[q], b[q + 1])) return true
            }
            return false
        }

        // Repair loop: any straight leader that clips a circle or crosses another leader
        // becomes a dogleg. Doglegging is monotone (never reverts), so the loop terminates
        // in ≤ n passes; anything left after that is counted, not silently drawn over.
        var pass = 0
        while (pass++ <= n) {
            var changed = false
            for (i in 0 until n) {
                if (!dogleg[i] && pathHitsForeignCircle(i)) {
                    makeDogleg(i)
                    changed = true
                }
            }
            for (i in 0 until n) for (j in i + 1 until n) {
                if (pathsCross(i, j)) {
                    if (!dogleg[i]) { makeDogleg(i); changed = true }
                    if (!dogleg[j]) { makeDogleg(j); changed = true }
                }
            }
            if (!changed) break
        }

        var unresolved = 0
        for (i in 0 until n) {
            if (pathHitsForeignCircle(i)) unresolved++
            for (j in i + 1 until n) if (pathsCross(i, j)) unresolved++
        }

        val bubbles = List(n) { i ->
            PlacedRunoutBubble(
                componentId = stations[i].componentId,
                stationMm = stations[i].stationMm,
                stationX = stations[i].stationX,
                surfaceY = surfaceY[i],
                bubbleX = bubbleX[i],
                bubbleCenterY = centerY[i],
                row = rows[i],
                leader = paths[i],
                stationIndex = stations[i].stationIndex,
            )
        }
        return RunoutBubbleResult(bubbles, unresolved)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Geometry primitives
// ─────────────────────────────────────────────────────────────────────────────

/** True when segment a→b passes strictly within [r] of centre (cx, cy). */
internal fun segmentIntersectsCircle(
    a: LeaderVertex,
    b: LeaderVertex,
    cx: Float,
    cy: Float,
    r: Float,
): Boolean {
    val abx = b.x - a.x
    val aby = b.y - a.y
    val acx = cx - a.x
    val acy = cy - a.y
    val len2 = abx * abx + aby * aby
    val t = if (len2 <= 1e-9f) 0f else ((acx * abx + acy * aby) / len2).coerceIn(0f, 1f)
    val dx = acx - t * abx
    val dy = acy - t * aby
    return dx * dx + dy * dy < r * r
}

/** True when the two segments properly cross (shared endpoints / touching don't count). */
internal fun segmentsProperlyIntersect(
    a1: LeaderVertex,
    a2: LeaderVertex,
    b1: LeaderVertex,
    b2: LeaderVertex,
): Boolean {
    fun orient(p: LeaderVertex, q: LeaderVertex, r: LeaderVertex): Float =
        (q.x - p.x) * (r.y - p.y) - (q.y - p.y) * (r.x - p.x)

    val d1 = orient(b1, b2, a1)
    val d2 = orient(b1, b2, a2)
    val d3 = orient(a1, a2, b1)
    val d4 = orient(a1, a2, b2)
    return ((d1 > 0f && d2 < 0f) || (d1 < 0f && d2 > 0f)) &&
        ((d3 > 0f && d4 < 0f) || (d3 < 0f && d4 > 0f))
}
