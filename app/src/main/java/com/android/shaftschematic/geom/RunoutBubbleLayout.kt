package com.android.shaftschematic.geom

import com.android.shaftschematic.model.RunoutStationPlacements
import com.android.shaftschematic.settings.RunoutConfig
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

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
 * 5. Leaders are straight station→bubble diagonals when the segment provably clears
 *    every other bubble and leader. A straight leader AIMS AT ITS CIRCLE'S CENTER and
 *    stops on the rim (the hand-sheet drafting convention): the arrival direction alone
 *    says which circle it lands in, where a leader bent to the top-center of a distant
 *    bubble reads ambiguously (on-device report). Straight leaders are also verified with
 *    a wider VISUAL clearance ([STRAIGHT_LEADER_CLEARANCE_RADIUS_FRAC] of the radius) —
 *    a segment that grazes a foreign circle by a hair is geometrically legal but
 *    unreadable. Otherwise the leader becomes a DOGLEG: a vertical stub from the station
 *    down to a common departure line at the deepest shaft surface, a diagonal to an elbow
 *    at the bubble's x, then a vertical drop to the bubble top. Dogleg segments keep the
 *    tighter geometric clearance (0.5·minGap): their diagonals legitimately skim the lane
 *    just above the row-0 circle tops, and the vertical drop already lands unambiguously.
 *
 *    A dogleg's elbow DIPS below that lane wherever the circle field allows — as deep as
 *    its own horizontal run needs to descend at [LEADER_DOGLEG_MIN_SLOPE], and never past
 *    the bubble's own top, so the last leg stays a drop. A leader that spends all of its
 *    sideways travel inside the thin lane leaves the shaft nearly tangent to the profile
 *    line and so points at nothing; several of them read as a bundle of rules under the
 *    shaft and the eye cannot tell which station any one of them left (on-device report).
 *    The dip costs no page height — it stays inside the band the rows already occupy — and
 *    it re-establishes clearance per leader instead of inheriting it: the depth comes from
 *    a search that only ever accepts a diagonal clearing every foreign circle, floored at
 *    the lane, and the drop is a sub-segment of the lane-level drop, which invariant 3
 *    already covers. A dipped diagonal no longer shares the lane's common elbow line, so
 *    it can cross a neighbour; flattening it back to the lane is the repair's fallback.
 *    Repair therefore moves each leader through at most two states (straight → dipped
 *    dogleg → lane dogleg), which is what keeps the loop terminating on the all-lane
 *    layout whose no-crossing guarantee is structural.
 * 6. Two rows is not just the shop convention — it is width-optimal. Every leader's
 *    final drop passes through every row band above its bubble and needs its own
 *    horizontal lane (`crossRowPitch`) past the circles there, so each bubble consumes
 *    ~one lane of width REGARDLESS of how deep it sits. Deeper row cycles therefore
 *    cannot reduce the required width — they only add page height. When the station
 *    count cannot fit the content width at minimum clearances (~27 stations on a letter
 *    page), spacing compresses uniformly and the plan flags itself
 *    ([RunoutBubblePlan.compressed]; [RunoutBubbleResult.unresolvedCollisions] reports
 *    anything the repair pass could not fix in that degenerate state).
 * 7. Even-spread waterfill, BRAKED by station fidelity (both on-device direction): the
 *    minimum pitches are collision floors, not a layout goal — a sheet that packs its
 *    bubbles at the minimums under compressed runs leaves the rest of the width empty.
 *    When the page has slack, every adjacent gap floor rises toward one common level
 *    (Σ max(gap_i, L) = available, capped at [RunoutBubbleGeometry.spreadPitch]) — but
 *    the spread may never pull a bubble further than [RunoutBubbleGeometry.spreadMaxOffset]
 *    (one same-row pitch) from its own station. An unbraked page-filling comb over
 *    clustered stations turned the pointer lines near-horizontal — you could not see
 *    where they landed (on-device report). The brake takes the largest level whose
 *    least-squares solve keeps every |bubbleX − stationX| inside the bound; a sheet whose
 *    geometric floors alone already exceed the bound takes no widening at all (its
 *    pointers stay as straight as the minimums allow, and the doglegs carry the rest).
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
 * Multiplier on [RunoutBubbleGeometry.sameRowPitch] bounding how far the even-spread
 * waterfill may pull a bubble off its own station ([RunoutBubbleGeometry.spreadMaxOffset]
 * — see [planRunoutBubbles] rule 7). One same-row pitch: a bubble never strays more than
 * one bubble-diameter-and-gap from the station it reads, so its pointer stays traceable.
 * The bound brakes only the OPTIONAL spread — geometric minimum pitches may still exceed
 * it on a genuinely dense sheet.
 */
const val BUBBLE_SPREAD_MAX_OFFSET_FACTOR = 1.0f

/**
 * Fraction of the bubble radius kept as VISUAL clearance between a straight leader and
 * every foreign circle (≈ 8 pt at PDF scale). A straight leader that would pass closer is
 * rerouted as a dogleg even though it misses geometrically — a segment shaving past a
 * circle's rim reads as entering it (on-device report: "hard to see where they land").
 * Doglegs keep the tighter 0.5·minGap geometric clearance; their elbow lane legitimately
 * runs just above the row-0 circle tops, and the vertical drop is unambiguous anyway.
 */
const val STRAIGHT_LEADER_CLEARANCE_RADIUS_FRAC = 0.35f

/**
 * Descent slope (rise ÷ run, ≈ 26.6°) a dogleg's diagonal aims for before its elbow stops
 * dipping — see [planRunoutBubbles] rule 5. It is a TARGET, not a guarantee: the elbow may
 * only dip as far as the surrounding circles leave room, and never past its own bubble's
 * top, so a leader whose bubble sits far off its station on a packed sheet still finishes
 * shallow. The value is the shallowest slope at which a pointer still reads as leaving the
 * shaft rather than running along it, and asking for more would buy nothing: past this the
 * dips get blocked by the neighbouring circles instead of granted.
 */
const val LEADER_DOGLEG_MIN_SLOPE = 0.5f

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
 *
 * [placements] overrides the derived position of any station the user has dragged. An
 * authored position wins over every rule above — it is a typed input in the golden-rule sense,
 * and no derivation may move it. Components with no placements derive exactly as before, and a
 * placement whose index is beyond the component's current count is simply never read (the
 * render-layer orphan rule). On a mixed component a derived station that would print out of
 * index order against a pin (compressed maps, geometry edits) is clamped to the pin's drawn
 * position — the derived one yields, never the pin, so the sheet always reads AFT→FWD.
 */
fun collectRunoutStations(
    spans: List<RunoutComponentSpan>,
    overrides: Map<String, Int>,
    xAtMm: (Float) -> Float,
    mmAtX: ((Float) -> Float)? = null,
    placements: RunoutStationPlacements = RunoutStationPlacements(),
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
        val stations = ArrayList<RunoutStationX>(count)
        var nextIndex = 0
        runs.forEachIndexed { runIdx, span ->
            val runCount = perRun[runIdx]
            if (runCount <= 0) return@forEachIndexed

            if (kind == RunoutComponentKind.BODY && mmAtX != null) {
                val x0 = xAtMm(span.startMm)
                val x1 = xAtMm(span.startMm + span.lengthMm)
                repeat(runCount) { i ->
                    val xs = x0 + (i + 0.5f) * (x1 - x0) / runCount
                    stations.add(RunoutStationX(id, mmAtX(xs), xs, stationIndex = nextIndex++))
                }
            } else {
                runoutStationPositionsMm(
                    startMm = span.startMm,
                    lengthMm = span.lengthMm,
                    count = runCount,
                    useEdgeInset = kind != RunoutComponentKind.BODY,
                ).forEach { mm ->
                    stations.add(RunoutStationX(id, mm, xAtMm(mm), stationIndex = nextIndex++))
                }
            }
        }

        // Overlay the user's dragged positions. Applied AFTER the derivation above rather than
        // instead of it, so a component keeps a full station for every index even when its
        // placements are partial (the ordinary state — a drag pins only the station it moved),
        // and so the apportionment across runs stays the one thing that decides how many
        // stations exist.
        val authored = placements.positionsFor(id)
        if (authored.isNotEmpty()) {
            for (i in stations.indices) {
                val localMm = authored[stations[i].stationIndex] ?: continue
                val mm = resolveStationShaftMm(runs, localMm)
                stations[i] = stations[i].copy(stationMm = mm, stationX = xAtMm(mm))
            }

            // Order repair for mixed pinned/derived components. A pin holds physical mm while
            // a derived body sibling holds drawn-even x — under a compressed output map, or
            // after a geometry edit moved a derived sibling, the two can land out of index
            // order, printing station i+1 aft of station i. The sheet must read AFT→FWD, and
            // a pin is a typed input, so the DERIVED station always yields: clamp it to its
            // neighbour (coincident ticks are legal — the bubble planner keeps the circles
            // apart regardless). Two passes, forward then backward; pins are ordered among
            // themselves by the drag clamp, so the passes cannot fight.
            for (i in 1 until stations.size) {
                val s = stations[i]
                if (s.stationIndex in authored) continue
                val prev = stations[i - 1]
                if (s.stationX < prev.stationX) {
                    stations[i] = s.copy(
                        stationX = prev.stationX,
                        stationMm = mmAtX?.invoke(prev.stationX) ?: prev.stationMm,
                    )
                }
            }
            for (i in stations.size - 2 downTo 0) {
                val s = stations[i]
                if (s.stationIndex in authored) continue
                val next = stations[i + 1]
                if (s.stationX > next.stationX) {
                    stations[i] = s.copy(
                        stationX = next.stationX,
                        stationMm = mmAtX?.invoke(next.stationX) ?: next.stationMm,
                    )
                }
            }
        }

        out.addAll(stations)
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

    /**
     * Station-fidelity brake on the waterfill (rule 7): the furthest the optional spread
     * may pull a bubble from its own station. See [BUBBLE_SPREAD_MAX_OFFSET_FACTOR].
     */
    val spreadMaxOffset: Float get() = sameRowPitch * BUBBLE_SPREAD_MAX_OFFSET_FACTOR
}

/** One vertex of a leader polyline. */
data class LeaderVertex(val x: Float, val y: Float)

/** A fully placed bubble: circle centre, row, and its leader polyline — a straight
 *  station→rim segment (2 vertices, aimed at the circle centre) or a dogleg (4 vertices:
 *  a vertical stub at the station, a diagonal to the elbow, and a vertical drop to the
 *  bubble top). Either way the polyline STARTS at the station's x on the shaft surface and
 *  ENDS on the bubble's rim, so a bubble sitting off its station always shows a pointer
 *  joining the two — there is no proximity rule that drops or shortens one. */
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
    // one common level so the bubbles distribute the width under the shaft instead of
    // bunching beneath compressed runs (on-device request, hand-sheet reference). The
    // level solves Σ max(gap_i, L) = available, capped at `spreadPitch` — and then BRAKED
    // by station fidelity: the spread may never pull a bubble further than
    // `spreadMaxOffset` from its own station (on-device report: a page-filling comb over
    // clustered stations turned the pointer lines near-horizontal — hard to see where
    // they landed). The brake searches the largest level whose least-squares solve keeps
    // every |bubbleX − stationX| inside the bound; a sheet whose geometric floors alone
    // already exceed it takes no widening at all. Floors only ever GROW here (never below
    // the geometric minimum), so every collision guarantee is untouched, and a page with
    // no slack gets zero widening.
    val baseNeeded = gaps.sum()
    if (available - baseNeeded > 0f && n > 1) {
        fun totalAt(level: Float): Float {
            var t = 0f
            for (g in gaps) t += max(g, level)
            return t
        }
        fun maxOffsetAt(level: Float): Float {
            val raised = FloatArray(gaps.size) { max(gaps[it], level) }
            val bx = solveBubbleX(desired, raised, geom)
            var worst = 0f
            for (i in bx.indices) worst = max(worst, abs(bx[i] - desired[i]))
            return worst
        }
        val cap = geom.spreadPitch
        val fillLevel = if (totalAt(cap) <= available) {
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
        val bound = geom.spreadMaxOffset
        val level = when {
            maxOffsetAt(fillLevel) <= bound -> fillLevel
            maxOffsetAt(0f) >= bound -> 0f
            else -> {
                var lo = 0f
                var hi = fillLevel
                repeat(30) {
                    val mid = (lo + hi) / 2f
                    if (maxOffsetAt(mid) <= bound) lo = mid else hi = mid
                }
                lo
            }
        }
        if (level > 0f) for (i in gaps.indices) gaps[i] = max(gaps[i], level)
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

        // Dogleg geometry. At LANE level every dogleg's diagonal runs between the same two
        // horizontal lines — a common departure line at the deepest shaft surface and a
        // common elbow line just above the row-0 circle tops. Station order equals bubble
        // order (both monotonic), so two lane diagonals can never properly cross; the
        // elbow clearance keeps them above every circle; and the vertical stub/drop
        // segments are parallel. A lane dogleg therefore cannot collide with anything
        // except a straight leader — which the repair loop then also converts. This is
        // what makes the repair converge to zero collisions (when spacing isn't
        // compressed), and it is why the lane is the fallback every dipped elbow can be
        // flattened back to.
        val laneElbowY = anchor + geom.shortLeader - 0.75f * geom.minGap
        val clearance = 0.5f * geom.minGap
        // Straight leaders take a wider VISUAL clearance (rule 5): geometrically-legal
        // grazes read as entering the circle they shave past. Dogleg segments keep the
        // geometric clearance — their diagonals legitimately run in the thin lane above
        // the row-0 tops (the elbow line sits only 0.75·minGap above them), so testing
        // them at the visual clearance would flag every dogleg and break the repair
        // loop's convergence guarantee.
        val straightClearance = max(clearance, STRAIGHT_LEADER_CLEARANCE_RADIUS_FRAC * geom.radius)

        // Straight leaders aim at the circle's CENTER and stop on the rim (rule 5) — the
        // arrival direction alone says which circle the pointer lands in. A station close
        // enough that the segment degenerates keeps the plain top-center attach.
        val paths = Array(n) { i ->
            val sx = stations[i].stationX
            val sy = surfaceY[i]
            val dx = bubbleX[i] - sx
            val dy = centerY[i] - sy
            val d = sqrt(dx * dx + dy * dy)
            if (d <= geom.radius + 1e-3f) {
                listOf(
                    LeaderVertex(sx, sy),
                    LeaderVertex(bubbleX[i], centerY[i] - geom.radius),
                )
            } else {
                val f = (d - geom.radius) / d
                listOf(LeaderVertex(sx, sy), LeaderVertex(sx + dx * f, sy + dy * f))
            }
        }
        val dogleg = BooleanArray(n)
        val elbow = FloatArray(n) { laneElbowY }

        // A dogleg diagonal is clear when it misses every FOREIGN circle by [clearance].
        // Its own circle is skipped: the diagonal lands on that circle's top, and the
        // endpoint is its lowest point, so it cannot enter the circle on the way in.
        fun diagonalClears(i: Int, elbowY: Float): Boolean {
            val a = LeaderVertex(stations[i].stationX, anchor)
            val b = LeaderVertex(bubbleX[i], elbowY)
            for (j in 0 until n) {
                if (j == i) continue
                if (segmentIntersectsCircle(a, b, bubbleX[j], centerY[j], geom.radius + clearance)) {
                    return false
                }
            }
            return true
        }

        // How far this dogleg's elbow may dip below the lane (rule 5): far enough for the
        // diagonal to descend at LEADER_DOGLEG_MIN_SLOPE over its own horizontal run, but
        // never past the bubble's own top and never into a foreign circle. Only the depth
        // actually needed is taken — dipping deeper than that would buy no readability and
        // would put the diagonal in the way of more of its neighbours.
        fun elbowDepthFor(i: Int): Float {
            val run = abs(bubbleX[i] - stations[i].stationX)
            val target = min(anchor + run * LEADER_DOGLEG_MIN_SLOPE, centerY[i] - geom.radius)
            if (target <= laneElbowY) return laneElbowY
            if (diagonalClears(i, target)) return target
            // Clearance is not monotone in elbow depth (a diagonal can dive UNDER a circle
            // it would clip at mid depth), so the search only ever moves `lo` onto a depth
            // it has verified. `laneElbowY` seeds it: the lane runs above every circle top,
            // so it is clear by construction and the search can never return worse.
            var lo = laneElbowY
            var hi = target
            repeat(16) {
                val mid = (lo + hi) / 2f
                if (diagonalClears(i, mid)) lo = mid else hi = mid
            }
            return lo
        }

        fun buildDogleg(i: Int) {
            paths[i] = listOf(
                LeaderVertex(stations[i].stationX, surfaceY[i]),
                // Vertical stub down to the common departure line (zero-length when the
                // station already sits on the deepest surface — harmless to draw/test).
                LeaderVertex(stations[i].stationX, anchor),
                LeaderVertex(bubbleX[i], elbow[i]),
                LeaderVertex(bubbleX[i], centerY[i] - geom.radius),
            )
        }

        fun makeDogleg(i: Int) {
            dogleg[i] = true
            elbow[i] = elbowDepthFor(i)
            buildDogleg(i)
        }

        /** Give up this leader's dip and put its elbow back on the provably safe lane. */
        fun flattenToLane(i: Int) {
            elbow[i] = laneElbowY
            buildDogleg(i)
        }

        fun dipped(i: Int): Boolean = elbow[i] > laneElbowY + 1e-3f

        fun pathHitsForeignCircle(i: Int): Boolean {
            val p = paths[i]
            val clr = if (dogleg[i]) clearance else straightClearance
            for (j in 0 until n) {
                if (j == i) continue
                for (s in 0 until p.size - 1) {
                    if (segmentIntersectsCircle(p[s], p[s + 1], bubbleX[j], centerY[j], geom.radius + clr)) {
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

        // Repair loop: a leader that clips a circle or crosses another leader gives up one
        // step of freedom — a straight becomes a dogleg (dipped as far as rule 5 allows), a
        // dipped dogleg flattens onto the lane. Both moves are one-way, so each leader
        // changes at most twice and the loop terminates in ≤ 2n+1 passes; anything left
        // after that is counted, not silently drawn over.
        fun yieldOneStep(i: Int): Boolean = when {
            !dogleg[i] -> { makeDogleg(i); true }
            dipped(i) -> { flattenToLane(i); true }
            else -> false
        }

        var pass = 0
        while (pass++ <= 2 * n + 1) {
            var changed = false
            for (i in 0 until n) {
                if (pathHitsForeignCircle(i) && yieldOneStep(i)) changed = true
            }
            for (i in 0 until n) for (j in i + 1 until n) {
                if (pathsCross(i, j)) {
                    if (yieldOneStep(i)) changed = true
                    if (yieldOneStep(j)) changed = true
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
