package com.android.shaftschematic.geom

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * DimensionRailLayout — pure placement engine for stacked dimension-rail labels.
 *
 * The PDF dimension renderer seats a value in a BREAK in its dimension line whenever the span
 * is wide enough to keep an inward arrowhead on both residual stubs; a span too short for that
 * falls back to a continuous line with the value floating above it. A floating value lives in
 * the vertical band of the NEXT rail up, so labels and rail lines from different tiers share
 * one collision space — they cannot be resolved rail by rail.
 *
 * This engine owns that whole decision:
 *  - inline vs above is decided from x-geometry ALONE ([canFitInwardArrows]), so the fallback
 *    set — and therefore the tier lifts — are known before any vertical budget is chosen;
 *  - every rail above a rail that carries an above-line label LIFTS by one label band
 *    (text height + gap), cumulatively, so no rail line is ever drawn through a value;
 *  - a colliding label first SLIDES horizontally along its own span (smallest shift from
 *    center that clears everything), and only bumps vertically when the slide has no room.
 *
 * Labels (inline and above) and rail lines are all obstacles, across every rail including the
 * topmost OAL rail. The renderer owns the pixels; this engine owns the geometry.
 *
 * Follows the `geom/` convention — no Android imports, no `pdf`/`ui` dependency — so it is
 * unit-testable on the JVM.
 */
object DimensionRailLayout {

    /** The topmost rail (OAL). It sits above every numbered rail, whatever its page y. */
    const val TOP_RAIL: Int = -1

    /** Clearance kept between two labels, and between a label and a rail line. */
    const val LABEL_GAP: Float = 6f

    /** Vertical half-thickness a rail line occupies as a label obstacle. */
    const val LINE_HALF_CLEAR: Float = 1f

    /** Bounded vertical escape for an above-line label that cannot slide clear. */
    const val MAX_BUMPS: Int = 3

    /**
     * One dimension span as the planner sees it: page-x endpoints and the measured label
     * width, on a rail whose page y is the caller's UNLIFTED position. [plan] returns the
     * lifted y to draw at.
     */
    data class SpanInput(
        val railIndex: Int,
        val railY: Float,
        val xa: Float,
        val xb: Float,
        val labelWidth: Float,
    )

    /** Text box metrics in output units (pt); [ascent] is negative, font-metrics convention. */
    data class TextMetrics(
        val ascent: Float,
        val descent: Float,
        /** Baseline offset above the line for the fallback (above-line) label. */
        val aboveDy: Float,
        val labelGap: Float = LABEL_GAP,
    ) {
        val height: Float get() = descent - ascent

        /** One label band: the vertical room a floating label needs, gap included. */
        val band: Float get() = height + labelGap
    }

    /** Axis-aligned box in page coordinates (y grows downward). */
    data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        fun intersects(other: Box): Boolean =
            left < other.right && other.left < right && top < other.bottom && other.top < bottom

        fun expanded(dx: Float, dy: Float): Box = Box(left - dx, top - dy, right + dx, bottom + dy)

        val centerX: Float get() = (left + right) * 0.5f
    }

    /**
     * Where one span's line and value land.
     *
     * @param inline true = value seated in a break in the line at [cx]; false = continuous
     *   line with the value floating above it.
     * @param railY the LIFTED page y of this span's dimension line.
     * @param label the placed glyph box; its baseline is `label.top - metrics.ascent`.
     */
    data class Placement(
        val inline: Boolean,
        val cx: Float,
        val railY: Float,
        val label: Box,
    )

    /** Placements parallel to the input spans, plus the per-rail lift that positioned them. */
    data class Plan(
        val placements: List<Placement>,
        val liftByRail: Map<Int, Float>,
    ) {
        fun liftFor(railIndex: Int): Float = liftByRail[railIndex] ?: 0f
    }

    /**
     * The lift each rail takes: one label band per rail BELOW it that carries an above-line
     * label. Depends only on x-geometry and rail assignment — `railY` is ignored — so a caller
     * can size its vertical budget before it knows where the rails will sit.
     */
    fun lifts(
        spans: List<SpanInput>,
        metrics: TextMetrics,
        arrowSize: Float,
        textPad: Float,
    ): Map<Int, Float> {
        if (spans.isEmpty()) return emptyMap()
        val fallbackRails = spans.filterNot { fitsInline(it, arrowSize, textPad) }
            .map { it.railIndex }
            .toSet()
        return spans.map { it.railIndex }.toSet().associateWith { rail ->
            metrics.band * fallbackRails.count { order(it) < order(rail) }
        }
    }

    /**
     * The lift of the topmost rail — one band per fallback-carrying numbered rail. This is the
     * total extra height a rail block needs, whether or not a top-rail span was passed in.
     */
    fun topLift(
        spans: List<SpanInput>,
        metrics: TextMetrics,
        arrowSize: Float,
        textPad: Float,
    ): Float {
        if (spans.isEmpty()) return 0f
        val fallbackRails = spans.filterNot { fitsInline(it, arrowSize, textPad) }
            .map { it.railIndex }
            .filter { it != TOP_RAIL }
            .toSet()
        return metrics.band * fallbackRails.size
    }

    /**
     * Plans every span at once.
     *
     * Placement order is least-freedom-first (a label with little slide room claims its spot
     * before a wide span that can move around it), then left-to-right, then input order — so
     * the result is deterministic and the wide span is the one that slides.
     *
     * @param safeTopY vertical bumps never carry a label above this y.
     * @return placements parallel to [spans].
     */
    fun plan(
        spans: List<SpanInput>,
        metrics: TextMetrics,
        arrowSize: Float,
        textPad: Float,
        safeTopY: Float = Float.NEGATIVE_INFINITY,
    ): Plan {
        if (spans.isEmpty()) return Plan(emptyList(), emptyMap())
        val liftByRail = lifts(spans, metrics, arrowSize, textPad)
        val pad = metrics.labelGap * 0.5f

        val rows = spans.map { s ->
            val inline = fitsInline(s, arrowSize, textPad)
            val half = s.labelWidth * 0.5f
            val slack = textPad + half + (if (inline) arrowSize else 0f)
            val lo = s.xa + slack
            val hi = s.xb - slack
            val mid = (s.xa + s.xb) * 0.5f
            Row(
                inline = inline,
                y = s.railY - (liftByRail[s.railIndex] ?: 0f),
                half = half,
                mid = mid,
                // A label wider than its own span has no slide room at all: it centers on the
                // span and overhangs symmetrically.
                lo = if (lo > hi) mid else lo,
                hi = if (lo > hi) mid else hi,
                xa = s.xa,
                xb = s.xb,
            )
        }

        // Rail lines are obstacles: a value must never be struck through by a neighbouring
        // tier's dimension line. A span's OWN line is excluded — an inline value sits in the
        // break that line leaves for it.
        val lineBoxes = rows.map { Box(it.xa, it.y - LINE_HALF_CLEAR, it.xb, it.y + LINE_HALF_CLEAR) }

        val order = rows.indices.sortedWith(
            compareBy({ rows[it].hi - rows[it].lo }, { rows[it].xa }, { it })
        )

        val placedLabels = mutableListOf<Box>()
        val result = arrayOfNulls<Placement>(rows.size)

        for (i in order) {
            val r = rows[i]
            val obstacles = placedLabels + lineBoxes.filterIndexed { j, _ -> j != i }

            var top = if (r.inline) r.y - metrics.height * 0.5f else r.y - metrics.aboveDy + metrics.ascent
            var bottom = top + metrics.height
            var cx = clearCenter(r, top, bottom, obstacles, pad)

            if (cx == null && !r.inline) {
                // No horizontal escape at this height — bump the floating label up a band at
                // a time, bounded, never past the content top.
                var bumps = 0
                while (cx == null && bumps < MAX_BUMPS) {
                    top -= metrics.band
                    bottom -= metrics.band
                    if (top < safeTopY) {
                        val shift = safeTopY - top
                        top += shift
                        bottom += shift
                    }
                    cx = clearCenter(r, top, bottom, obstacles, pad)
                    bumps++
                }
            }
            // Best effort: an unresolvable label still prints, centered on its span.
            val finalCx = cx ?: r.mid.coerceIn(r.lo, r.hi)
            val box = Box(finalCx - r.half, top, finalCx + r.half, bottom)
            placedLabels += box
            result[i] = Placement(inline = r.inline, cx = finalCx, railY = r.y, label = box)
        }

        return Plan(result.map { it!! }, liftByRail)
    }

    /**
     * Inline eligibility: the residual stubs on both sides of the label window must be long
     * enough to host an inward arrowhead. Same predicate the renderer uses to choose arrow
     * direction, so an inline value always gets inward arrows.
     */
    fun canFitInwardArrows(
        xa: Float,
        xb: Float,
        cx: Float,
        labelWidth: Float,
        textPad: Float,
        arrowSize: Float,
    ): Boolean {
        val leftWindow = cx - labelWidth * 0.5f - textPad
        val rightWindow = cx + labelWidth * 0.5f + textPad
        return (leftWindow - xa) >= arrowSize && (xb - rightWindow) >= arrowSize
    }

    private class Row(
        val inline: Boolean,
        val y: Float,
        val half: Float,
        val mid: Float,
        val lo: Float,
        val hi: Float,
        val xa: Float,
        val xb: Float,
    )

    /** Rails stack upward; the OAL rail is above every numbered one. */
    private fun order(railIndex: Int): Int = if (railIndex == TOP_RAIL) Int.MAX_VALUE else railIndex

    private fun fitsInline(s: SpanInput, arrowSize: Float, textPad: Float): Boolean {
        val half = s.labelWidth * 0.5f
        val mid = (s.xa + s.xb) * 0.5f
        val lo = s.xa + textPad + half
        val hi = s.xb - textPad - half
        val cx = if (lo > hi) mid else min(max(mid, lo), hi)
        return canFitInwardArrows(s.xa, s.xb, cx, s.labelWidth, textPad, arrowSize)
    }

    /**
     * The label center nearest the span's midpoint, inside `[lo, hi]`, that clears every
     * obstacle overlapping the candidate's vertical band. Null when the band is fully blocked.
     */
    private fun clearCenter(
        r: Row,
        top: Float,
        bottom: Float,
        obstacles: List<Box>,
        pad: Float,
    ): Float? {
        // An obstacle that shares no vertical band with the candidate can never block it.
        // The rest forbid an open interval of centers.
        val forbidden = obstacles
            .filter { it.top < bottom + pad && top - pad < it.bottom }
            .map { it.left - r.half - pad to it.right + r.half + pad }
            .sortedBy { it.first }

        if (forbidden.isEmpty()) return r.mid.coerceIn(r.lo, r.hi)

        val merged = mutableListOf<Pair<Float, Float>>()
        for (iv in forbidden) {
            val last = merged.lastOrNull()
            if (last != null && iv.first <= last.second) {
                if (iv.second > last.second) merged[merged.lastIndex] = last.first to iv.second
            } else {
                merged += iv
            }
        }

        fun allowed(x: Float): Boolean =
            x >= r.lo && x <= r.hi && merged.none { x > it.first && x < it.second }

        val wanted = r.mid.coerceIn(r.lo, r.hi)
        if (allowed(wanted)) return wanted

        var best = Float.NaN
        for (iv in merged) {
            for (candidate in listOf(iv.first, iv.second)) {
                val c = candidate.coerceIn(r.lo, r.hi)
                if (!allowed(c)) continue
                if (best.isNaN() || abs(c - wanted) < abs(best - wanted)) best = c
            }
        }
        return if (best.isNaN()) null else best
    }
}
