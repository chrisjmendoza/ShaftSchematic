// file: app/src/main/java/com/android/shaftschematic/geom/WearDiaCalloutLayout.kt
package com.android.shaftschematic.geom
import com.android.shaftschematic.util.DualLabel

import kotlin.math.max
import kotlin.math.min

/**
 * WearDiaCalloutLayout — shared placement engine for measured-diameter value callouts on
 * the wear document (the hand sketch's fan of diameter values under a worn section, each
 * with a leader pointing at its measured spot).
 *
 * Used by BOTH draw sites so the two renderings are identical by construction (the same
 * rule as `RunoutBubbleLayout`): the wear PDF (`WearPdfComposer` — liner readings on the
 * detail strips, body/taper readings under the main profile) and the detail-overlay canvas
 * (`ComponentWearDetailOverlay`). All coordinates are abstract output units (pt / px);
 * callers map mm → output x before planning.
 *
 * This is `RunoutBubbleLayout`'s scaled-down sibling, sized to **label widths** instead of
 * fixed-radius circles:
 * 1. Labels hang below the shaft in one row when they fit, else two alternating rows
 *    (row 0 nearest the shaft) — the hand sketch's stagger. Label x-order ALWAYS equals
 *    station x-order.
 * 2. Spacing invariants between x-adjacent labels (centre-to-centre horizontal):
 *      - same row      → ≥ `(wᵢ + wⱼ)/2 + minGap`  (labels never touch)
 *      - different row → ≥ `max(wᵢ, wⱼ)/2 + minGap` (a vertical leader drop at one label's
 *        x clears the other row's label box; also ≥ half of any same-row pair two apart,
 *        so adjacent-pair constraints are sufficient for all pairs)
 * 3. Label x positions minimise Σ(labelX − stationX)² under those constraints
 *    (pool-adjacent-violators, the same solver the runout bubbles use), so labels sit
 *    directly under their stations whenever there is room.
 * 4. Leaders: a vertical stub from the surface down to a common departure line, then a
 *    straight diagonal to the label. Row-1 leaders dogleg — diagonal to an elbow just above
 *    the row-0 band at the label's own x, then a vertical drop through the row-0 band
 *    (which invariant 2 keeps clear of every row-0 label). All diagonals run between the
 *    same two horizontal lines with matching left-to-right order at both ends, so leaders
 *    can never properly cross — the runout dogleg argument, one row shallower.
 * 5. Degenerate fallback: when even two rows can't fit the band width, spacing compresses
 *    uniformly and the plan flags itself ([DiaCalloutPlan.compressed]); the clearance
 *    guarantees are void in that case only.
 */

/**
 * One measured-diameter station: stable key, output-space x, and its printed label.
 *
 * [label] carries a dual value's two terms apart so a stacked layout can set them on two lines;
 * [labelWidth] is always measured by the caller in whichever form it will DRAW (the measure/draw
 * pairing rule, `util/DualLabelRenderer.kt`), so this engine needs to know nothing about it.
 */
data class DiaCalloutStation(
    val key: String,
    val stationX: Float,
    val label: DualLabel,
    val labelWidth: Float,
)

/** A fully placed callout: label centre/row/top plus its leader polyline (2–4 vertices). */
data class PlacedDiaCallout(
    val key: String,
    val stationX: Float,
    val labelCx: Float,
    val row: Int,
    val labelTopY: Float,
    val label: DualLabel,
    val labelWidth: Float,
    val leader: List<LeaderVertex>,
)

/**
 * Phase 1 — horizontal solve (rows + label x). Purely horizontal so callers can reserve
 * vertical band height from [rowCount] BEFORE fixing the shaft/strip vertical layout, then
 * call [finish] once the row-0 top y is known. Mirrors `RunoutBubblePlan`'s two-phase shape.
 */
class DiaCalloutPlan internal constructor(
    /** Stations sorted by stationX — parallel to [labelCx] and [rows]. */
    val stations: List<DiaCalloutStation>,
    val labelCx: FloatArray,
    val rows: IntArray,
    /** True when two rows couldn't fit the band width and spacing was compressed to fit. */
    val compressed: Boolean,
) {
    /** Number of label rows used: 0 (no stations), 1, or 2. */
    val rowCount: Int = (rows.maxOrNull() ?: -1) + 1

    /** Height of the label rows alone (no leader region): rows × text height + inter-row gaps. */
    fun labelsHeightPt(labelTextHeight: Float, rowGap: Float): Float =
        if (rowCount == 0) 0f else rowCount * labelTextHeight + (rowCount - 1) * rowGap

    /** Total band height below the surface: leader drop region + the label rows. */
    fun bandHeightPt(labelTextHeight: Float, rowGap: Float, leaderLen: Float): Float =
        if (rowCount == 0) 0f else leaderLen + labelsHeightPt(labelTextHeight, rowGap)

    /**
     * Phase 2 — fix vertical positions and route leaders.
     *
     * @param row0Top       Top y of the row-0 label band.
     * @param labelTextHeight One label row's text height (row pitch adds [rowGap]).
     * @param rowGap        Vertical gap between the two label rows.
     * @param surfaceYAt    Drawn bottom-surface y at a station (by sorted-station index) —
     *                      leaders originate just below it.
     * @param leaderStartGap Clear gap between the surface and the leader's first vertex.
     */
    fun finish(
        row0Top: Float,
        labelTextHeight: Float,
        rowGap: Float,
        surfaceYAt: (Int) -> Float,
        leaderStartGap: Float = 1f,
    ): List<PlacedDiaCallout> {
        val n = stations.size
        if (n == 0) return emptyList()
        val surfaceY = FloatArray(n) { surfaceYAt(it) }
        // Elbow line just above the row-0 label tops; the common departure line sits at the
        // deepest surface (or is pulled up to the elbow when the band is squeezed).
        val elbowY = row0Top - 2f
        val departY = min(surfaceY.max() + leaderStartGap, elbowY)
        fun rowTop(r: Int) = row0Top + r * (labelTextHeight + rowGap)

        return List(n) { i ->
            val s = stations[i]
            val startY = min(surfaceY[i] + leaderStartGap, departY)
            val leader = buildList {
                add(LeaderVertex(s.stationX, startY))
                if (departY > startY + 0.01f) add(LeaderVertex(s.stationX, departY))
                if (rows[i] == 0) {
                    add(LeaderVertex(labelCx[i], rowTop(0) - 1f))
                } else {
                    add(LeaderVertex(labelCx[i], elbowY))
                    add(LeaderVertex(labelCx[i], rowTop(1) - 1f))
                }
            }
            PlacedDiaCallout(
                key = s.key,
                stationX = s.stationX,
                labelCx = labelCx[i],
                row = rows[i],
                labelTopY = rowTop(rows[i]),
                label = s.label,
                labelWidth = s.labelWidth,
                leader = leader,
            )
        }
    }
}

/**
 * Solve rows and label x positions for all stations (see the file KDoc for the rules).
 * [contentLeft]/[contentRight] bound the label boxes; [minGap] is the minimum clear
 * horizontal distance kept between label edges (and between a leader drop and a foreign
 * label edge).
 */
fun planDiaCallouts(
    stations: List<DiaCalloutStation>,
    contentLeft: Float,
    contentRight: Float,
    minGap: Float,
): DiaCalloutPlan {
    val sorted = stations.sortedBy { it.stationX }
    val n = sorted.size
    if (n == 0) return DiaCalloutPlan(sorted, FloatArray(0), IntArray(0), compressed = false)

    val w = FloatArray(n) { sorted[it].labelWidth }
    val desired = FloatArray(n) { sorted[it].stationX }

    fun gapsFor(rows: IntArray) = FloatArray(max(n - 1, 0)) { i ->
        if (rows[i + 1] == rows[i]) (w[i] + w[i + 1]) * 0.5f + minGap
        else max(w[i], w[i + 1]) * 0.5f + minGap
    }

    // Bounds check in u-space (u_i = x_i − g_i with g the cumulative gap chain): feasible iff
    // the tightest lower bound ≤ the tightest upper bound. Label half-widths vary, so every
    // index contributes a candidate bound (unlike the fixed-radius runout case).
    fun feasible(gaps: FloatArray): Boolean {
        var g = 0f
        var lo = Float.NEGATIVE_INFINITY
        var hi = Float.POSITIVE_INFINITY
        for (i in 0 until n) {
            if (i > 0) g += gaps[i - 1]
            lo = max(lo, contentLeft + w[i] * 0.5f - g)
            hi = min(hi, contentRight - w[i] * 0.5f - g)
        }
        return lo <= hi
    }

    val singleRows = IntArray(n)
    var rows = singleRows
    var gaps = gapsFor(singleRows)
    var compressed = false
    if (n > 1 && !feasible(gaps)) {
        rows = IntArray(n) { it % 2 }
        gaps = gapsFor(rows)
        if (!feasible(gaps)) {
            // Degenerate: compress the gap chain uniformly until the chain itself fits the
            // width between the two edge labels' half-widths. Clearance guarantees are void.
            compressed = true
            val chain = gaps.sum()
            val room = (contentRight - contentLeft) - (w[0] + w[n - 1]) * 0.5f
            if (chain > 0f && room > 0f && chain > room) {
                val f = room / chain
                for (i in gaps.indices) gaps[i] *= f
            }
        }
    }

    // Least-squares x fit under the gap chain + bounds (same PAVA substitution as
    // RunoutBubbleLayout.solveBubbleX, with per-index bounds folded to two constants).
    val g = FloatArray(n)
    for (i in 1 until n) g[i] = g[i - 1] + gaps[i - 1]
    val u = isotonicNonDecreasing(DoubleArray(n) { (desired[it] - g[it]).toDouble() })
    var lo = Double.NEGATIVE_INFINITY
    var hi = Double.POSITIVE_INFINITY
    for (i in 0 until n) {
        lo = max(lo, (contentLeft + w[i] * 0.5f - g[i]).toDouble())
        hi = min(hi, (contentRight - w[i] * 0.5f - g[i]).toDouble())
    }
    hi = max(hi, lo)
    val x = FloatArray(n) { i -> ((u[i].coerceIn(lo, hi)) + g[i]).toFloat() }

    return DiaCalloutPlan(sorted, x, rows, compressed)
}
