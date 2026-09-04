package com.android.shaftschematic.geom

import kotlin.math.abs

/**
 * BelowShaftLabelLayout — ONE collision space for everything the schematic prints BELOW the shaft.
 *
 * Two independent passes hang text under the drawing: the Ø callouts (leader + value, tiered by
 * [DiameterCalloutLayout]) and the component-name labels. Both anchor on a component's CENTER, so
 * a component that shows a name and a Ø aims both strings at the same x — they printed over each
 * other on any sheet that elected both. Tracking collisions per pass is blind exactly where the
 * two meet, so the name labels are placed against the callouts as obstacles, in one pass, here.
 *
 * Same posture and the same resolution order as [DimensionRailLayout]:
 *  1. **slide the label horizontally along its own component's span** — smallest shift from the
 *     centered position that clears every obstacle. A name stays legible anywhere over its own
 *     component, so this is nearly free and costs no vertical room;
 *  2. only when no slide fits, **drop to the next row** below.
 *
 * Purely geometric: every input is an already-measured output-unit (pt) float, so the engine
 * carries no [android.graphics.Paint] and follows the `geom/` convention — no Android imports, no
 * `pdf`/`ui` dependency, unit-testable on the JVM. The caller owns the pixels and the metrics.
 */
object BelowShaftLabelLayout {

    /** Horizontal clearance kept between a label and anything it is placed beside (pt). */
    const val PAD_X: Float = 3f

    /** Positions that differ by less than this are the same position (pt). */
    private const val EPS = 1e-3f

    /** An occupied rectangle below the shaft — a callout's value box, or one of its leader lines. */
    data class Box(val left: Float, val right: Float, val top: Float, val bottom: Float)

    /** The vertical band one label row occupies (the row's text box, ascent to descent). */
    data class RowBand(val top: Float, val bottom: Float)

    /**
     * One label asking for room.
     *
     * @property width measured advance width of the text.
     * @property preferredLeft where the label wants to start — centered over its component.
     * @property minLeft left bound of the window it may slide inside (its own span, widened to
     *   contain the centered position, already clamped to the content rect by the caller).
     * @property maxRight right bound of that window.
     */
    data class Request(
        val width: Float,
        val preferredLeft: Float,
        val minLeft: Float,
        val maxRight: Float,
    )

    /**
     * Where a label landed.
     *
     * @property left the drawn left edge.
     * @property row index into the caller's bands.
     * @property fitted false when nothing cleared and the label took the least-bad row anyway.
     *   A caller with a lever to pull — smaller text, another row — reads this and retries; one
     *   without it prints the least-bad placement, which is still better than a dropped label.
     */
    data class Placement(val left: Float, val row: Int, val fitted: Boolean = true)

    /**
     * Places every label so it clears [obstacles] and every other label.
     *
     * Labels are considered left-to-right (deterministic for equal inputs) but the result is
     * parallel to [requests]. A label that fits nowhere — every row blocked across its whole
     * window, which needs a crowded sheet AND no vertical room left — takes the row where it
     * overlaps least, at its preferred position: the least-bad placement, never a dropped label
     * (the degenerate rule [DiameterCalloutLayout.assignTiers] already follows).
     *
     * @param rows the vertical bands available, nearest the shaft first. Empty rows in → every
     *   label placed on row 0 at its preferred position (nothing to solve against).
     */
    fun place(
        requests: List<Request>,
        obstacles: List<Box>,
        rows: List<RowBand>,
        padX: Float = PAD_X,
    ): List<Placement> {
        if (requests.isEmpty()) return emptyList()
        if (rows.isEmpty()) return requests.map { Placement(it.preferredLeft, 0, fitted = false) }

        // Obstacles that matter to each row: the ones whose band overlaps that row's band.
        val perRowObstacles = rows.map { band ->
            obstacles.filter { it.bottom > band.top && it.top < band.bottom }
                .map { it.left to it.right }
        }
        // Labels already placed on each row, which block the ones after them.
        val placedOnRow = rows.map { mutableListOf<Pair<Float, Float>>() }

        val result = arrayOfNulls<Placement>(requests.size)
        val order = requests.indices.sortedWith(
            compareBy({ requests[it].preferredLeft }, { it })
        )

        for (idx in order) {
            val req = requests[idx]
            var placement: Placement? = null
            for (row in rows.indices) {
                val left = bestLeft(req, perRowObstacles[row] + placedOnRow[row], padX) ?: continue
                placement = Placement(left, row)
                break
            }
            if (placement == null) {
                // Degenerate: nothing fits anywhere. Take the row this label overlaps least at its
                // preferred position, so the damage is one clipped word rather than a stack of them.
                val best = rows.indices.minByOrNull { row ->
                    overlapAt(req.preferredLeft, req.width, perRowObstacles[row] + placedOnRow[row])
                } ?: 0
                placement = Placement(req.preferredLeft, best, fitted = false)
            }
            placedOnRow[placement.row].add(placement.left to placement.left + req.width)
            result[idx] = placement
        }
        return result.map { it!! }
    }

    /**
     * The feasible left edge closest to [Request.preferredLeft], or null when the label cannot
     * clear [blockers] anywhere inside its window.
     *
     * A left edge `x` collides with a blocker `[l, r]` exactly when `x < r + padX` and
     * `x + width > l - padX`, so each blocker forbids the OPEN interval
     * `(l - padX - width, r + padX)` of left edges. Merging those turns the search into "the point
     * nearest the preferred one that is outside a union of intervals" — the candidates are the
     * preferred position itself and the edges of the merged intervals, all clamped to the window.
     */
    private fun bestLeft(req: Request, blockers: List<Pair<Float, Float>>, padX: Float): Float? {
        val lo = req.minLeft
        val hi = req.maxRight - req.width
        if (hi < lo - EPS) return null   // window narrower than the label

        val forbidden = blockers
            .map { (l, r) -> (l - padX - req.width) to (r + padX) }
            .sortedBy { it.first }
            .fold(mutableListOf<Pair<Float, Float>>()) { acc, iv ->
                val last = acc.lastOrNull()
                if (last != null && iv.first <= last.second + EPS) {
                    acc[acc.lastIndex] = last.first to maxOf(last.second, iv.second)
                } else {
                    acc.add(iv)
                }
                acc
            }

        fun feasible(x: Float) = forbidden.none { (a, b) -> x > a + EPS && x < b - EPS }

        val candidates = buildList {
            add(req.preferredLeft)
            forbidden.forEach { (a, b) -> add(a); add(b) }
        }.map { it.coerceIn(lo, maxOf(lo, hi)) }

        return candidates.filter { feasible(it) }.minByOrNull { abs(it - req.preferredLeft) }
    }

    /** Total horizontal overlap a label of [width] at [left] has with [blockers] — the tie-break
     *  for the degenerate case, so "least bad" is measured rather than guessed. */
    private fun overlapAt(left: Float, width: Float, blockers: List<Pair<Float, Float>>): Float {
        val right = left + width
        return blockers.sumOf { (l, r) ->
            (minOf(right, r) - maxOf(left, l)).coerceAtLeast(0f).toDouble()
        }.toFloat()
    }
}
