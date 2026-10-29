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
 *    center that clears everything); a floating value that still has no room — typically a
 *    span narrower than its own value — then tries the spot just OUTSIDE its span on its own
 *    rail's band (beyond the FWD extension line, then before the AFT one, inside the content
 *    area), and only then bumps vertically — inside its own rail's band, past it only when
 *    the alternative is printing over another value;
 *  - arrowheads point inward unless the span itself is too narrow to hold both
 *    ([arrowsPointInward]) — a fallback value overhead never costs a span its inward arrows;
 *  - a value that ends up OFF its own line — beside its span, or bumped above its home height —
 *    carries a leader to its own span ([Placement.leader], the undercut rails' [railValueLeader]
 *    construction). A leader is an obstacle like a label: its landing point slides along the
 *    span to clear the values already placed, an outside spot whose leader would still cross one
 *    is not taken, and every later value keeps clear of it.
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

    /**
     * How far apart two extension lines' page-x must be to count as different lines. Chained
     * spans SHARE a boundary, so the same drawn line belongs to both of them; anything inside
     * this tolerance is treated as one line and as "own" to either span that ends there.
     */
    const val EXT_SAME_X_EPS: Float = 0.5f

    /** Bounded vertical escape for an above-line label that cannot slide clear. */
    const val MAX_BUMPS: Int = 3

    /**
     * How far past its nearest outside spot a value placed OUTSIDE its span may slide to clear an
     * obstacle — enough to step over a neighbouring extension line a few points beyond the span's
     * end, never so far that the value reads as belonging to the next span.
     */
    const val OUTSIDE_REACH: Float = 12f

    /**
     * Daylight kept between the two inward arrowheads of a span whose value did NOT seat in a
     * break. Below this the heads read as one blob and the tips-in convention is the clearer
     * draw.
     */
    const val ARROW_CLEAR: Float = 2f

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
     * @param arrowsInward true = arrowheads sit between the extension lines; false = the
     *   cramped-span (tips-in) convention, heads hanging outside them.
     * @param label the placed glyph box; its baseline is `label.top - metrics.ascent`.
     * @param leader the stroke joining a value placed off its own line (beside its span, or
     *   bumped above its home height) to that span; null for a value in its break or floating at
     *   home over its span (emulator QA: a value set beside its span read as the next span's).
     */
    data class Placement(
        val inline: Boolean,
        val cx: Float,
        val railY: Float,
        val arrowsInward: Boolean,
        val label: Box,
        val leader: RailValueLeader? = null,
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
     * A floating value with no clear spot over its own span tries the spot just OUTSIDE the
     * span at the same height: right of the FWD extension line first, then left of the AFT one,
     * clear of an outward arrowhead and inside [[contentLeft], [contentRight]]. Only then does it
     * bump upward, and only while it stays in the band directly above its OWN rail line — never
     * across another rail's line and never nearer to one than to its own (on-device report: two
     * short AFT-end values floated two bands up, each over the wrong rail). A value with no clear
     * spot in its own band prints at its own rail's height, centred on its span — unless that
     * would set it on another value, the one case it may climb past its band.
     *
     * @param safeTopY vertical bumps never carry a label above this y.
     * @param contentLeft a value placed outside its span stays right of this x.
     * @param contentRight a value placed outside its span stays left of this x.
     * @return placements parallel to [spans].
     */
    fun plan(
        spans: List<SpanInput>,
        metrics: TextMetrics,
        arrowSize: Float,
        textPad: Float,
        safeTopY: Float = Float.NEGATIVE_INFINITY,
        contentLeft: Float = Float.NEGATIVE_INFINITY,
        contentRight: Float = Float.POSITIVE_INFINITY,
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
                overhangs = lo > hi,
            )
        }

        // Rail lines are obstacles: a value must never be struck through by a neighbouring
        // tier's dimension line. A span's OWN line is excluded — an inline value sits in the
        // break that line leaves for it.
        val lineBoxes = rows.map { Box(it.xa, it.y - LINE_HALF_CLEAR, it.xb, it.y + LINE_HALF_CLEAR) }

        // EXTENSION lines are obstacles too. Each runs from the object up to its OWN rail, so it
        // crosses the band of every rail below it — which is exactly where those rails park a
        // floating value. Leaving them out is what let an extension line print straight through
        // the tail of a fallback label (on-device sheet, `docs/DualUnitStacking_PLAN.md` §1b);
        // dual labels are simply wide enough to be hit every time.
        //
        // Collinear lines at one x (the same boundary dimensioned on several rails) are ONE
        // obstacle spanning from the topmost of them downward, and a span's own boundaries are
        // excluded: a chained neighbour shares that line, and a label wider than its own span
        // legitimately overhangs its own extensions — counting them would make that case
        // unsolvable and bump every such label to [MAX_BUMPS].
        val extLines = buildExtensionLines(rows)

        val order = rows.indices.sortedWith(
            compareBy({ rows[it].hi - rows[it].lo }, { rows[it].xa }, { it })
        )

        val placedLabels = mutableListOf<Box>()
        val placedLeaders = mutableListOf<Box>()
        val placedIdx = HashSet<Int>()
        val result = arrayOfNulls<Placement>(rows.size)

        for (i in order) {
            val r = rows[i]
            val foreign = placedLabels + placedLeaders +
                lineBoxes.filterIndexed { j, _ -> j != i } +
                extLines.filterNot { it.belongsTo(r) }.map { it.box() }
            // A span's own boundary line that a HIGHER rail continues upward (the AFT S.E.T. line
            // rising to the OAL rail) crosses the band a floating value sits in; a value
            // overhanging its own span there would print with the line through its first digit.
            // Only a CROSSING counts: the box is narrowed by the clearance [clearCenter] adds, so
            // a value may still sit right up to its own line, as a centred value just inside a
            // short span does.
            val ownAbove = extLines.filter { it.belongsTo(r) && it.topY < r.y - LINE_HALF_CLEAR }
                .map { Box(it.x - LINE_HALF_CLEAR + pad, it.topY, it.x + LINE_HALF_CLEAR - pad, r.y - LINE_HALF_CLEAR) }
            val insideObstacles = if (r.overhangs && !r.inline) foreign + ownAbove else foreign
            // Outside its span a value must clear its own extension lines too — and must not take
            // the spot a not-yet-placed floating neighbour on the same rail sits in over its own
            // span, or the step outside just moves the collision onto that neighbour.
            val neighbourHomes = rows.indices
                .filter { j -> j != i && j !in placedIdx && !rows[j].inline && abs(rows[j].y - r.y) < 0.5f }
                .map { j ->
                    val o = rows[j]
                    val c = o.mid.coerceIn(o.lo, o.hi)
                    val t = o.y - metrics.aboveDy + metrics.ascent
                    Box(c - o.half, t, c + o.half, t + metrics.height)
                }
            val outsideObstacles = foreign + extLines.filter { it.belongsTo(r) }.map { it.box() } + neighbourHomes

            val outGap = textPad + if (arrowsPointInward(r.xa, r.xb, r.inline, arrowSize)) 0f else arrowSize
            val rightLo = r.xb + outGap + r.half
            val rightHi = min(rightLo + OUTSIDE_REACH, contentRight - r.half)
            val leftHi = min(r.xa - outGap - r.half, contentRight - r.half)
            val leftLo = max(leftHi - OUTSIDE_REACH, contentLeft + r.half)

            val homeTop = if (r.inline) r.y - metrics.height * 0.5f else r.y - metrics.aboveDy + metrics.ascent

            // The leader a value at (cxL, topL) needs, and whether it reaches its line clear of
            // every value already placed. None for a value in its break or at home over its span.
            fun leaderAt(cxL: Float, topL: Float): Pair<RailValueLeader?, Boolean> {
                if (r.inline) return null to true
                val beside = cxL > r.xb || cxL < r.xa
                val lifted = topL < homeTop - LIFT_EPS
                if (!beside && !lifted) return null to true
                val nearEdge = topL + metrics.height
                val preferred = railValueLeader(r.xa, r.xb, cxL, arrowSize, r.y, nearEdge, r.half)
                    ?: return null to true
                if (placedLabels.none { leaderHits(preferred, it) }) return preferred to true
                // Slide the landing point along the span until the stroke clears the values in
                // its way — the label stays where it was placed; only where it lands moves.
                val inTheWay = placedLabels.filter { it.top < r.y && it.bottom > nearEdge }
                val landings = inTheWay
                    .flatMap { listOf(it.left - LEADER_CLEAR, it.right + LEADER_CLEAR) }
                    .sortedBy { abs(it - preferred.x) }
                for (x in landings) {
                    val l = railValueLeader(r.xa, r.xb, cxL, arrowSize, r.y, nearEdge, r.half, anchorX = x)
                        ?: continue
                    if (placedLabels.none { leaderHits(l, it) }) return l to true
                }
                return preferred to false
            }

            // The best spot at one height: over the span, else just outside it (floating only).
            // An outside spot whose leader could not reach the span without crossing a value is
            // no spot at all — the value would read as the value it crosses.
            fun spotAt(top: Float, bottom: Float): Float? {
                clearCenter(r.lo, r.hi, r.mid.coerceIn(r.lo, r.hi), r.half, top, bottom, insideObstacles, pad)
                    ?.let { return it }
                if (r.inline) return null
                if (rightLo <= rightHi) {
                    clearCenter(rightLo, rightHi, rightLo, r.half, top, bottom, outsideObstacles, pad)
                        ?.let { if (leaderAt(it, top).second) return it }
                }
                if (leftLo <= leftHi) {
                    clearCenter(leftLo, leftHi, leftHi, r.half, top, bottom, outsideObstacles, pad)
                        ?.let { if (leaderAt(it, top).second) return it }
                }
                return null
            }

            var top = homeTop
            var cx = spotAt(top, top + metrics.height)

            if (cx == null && !r.inline) {
                // No spot at its own height — bump the floating label up a band at a time,
                // bounded and never past the content top. First only while it stays in its own
                // rail's band. A value that fits nowhere there prints at home — unless home
                // would set it on top of another VALUE, the one outcome worse than a value away
                // from its rail; only then may the bump climb past the band.
                val homeCx = r.mid.coerceIn(r.lo, r.hi)
                val homeBox = Box(homeCx - r.half, homeTop, homeCx + r.half, homeTop + metrics.height)
                val homeHitsValue = placedLabels.any { it.intersects(homeBox) }
                for (ownBandOnly in if (homeHitsValue) listOf(true, false) else listOf(true)) {
                    var bumps = 0
                    var t = homeTop
                    while (cx == null && bumps < MAX_BUMPS) {
                        val next = max(t - metrics.band, safeTopY)
                        if (next >= t) break
                        t = next
                        bumps++
                        val spot = spotAt(t, t + metrics.height) ?: continue
                        val box = Box(spot - r.half, t, spot + r.half, t + metrics.height)
                        if (ownBandOnly && !bumpStaysOwn(box, r.y, rows, i)) break
                        cx = spot
                        top = t
                    }
                    if (cx != null) break
                }
            }
            // Best effort: an unresolvable label still prints at its own rail, centered on its span.
            val finalCx = cx ?: r.mid.coerceIn(r.lo, r.hi)
            val box = Box(finalCx - r.half, top, finalCx + r.half, top + metrics.height)
            val leader = leaderAt(finalCx, top).first
            placedLabels += box
            leader?.let { placedLeaders += leaderBox(it) }
            placedIdx += i
            result[i] = Placement(
                inline = r.inline,
                cx = finalCx,
                railY = r.y,
                arrowsInward = arrowsPointInward(r.xa, r.xb, r.inline, arrowSize),
                label = box,
                leader = leader,
            )
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

    /**
     * Float headroom subtracted when a blank draft's write-in gap is shrunk to exactly what its
     * span affords. Without it the shrunk gap lands on [canFitInwardArrows]'s boundary, where
     * rounding can fail the span by a hair and drop the gap it was just sized for.
     */
    const val GAP_FIT_SLACK: Float = 0.5f

    /**
     * Width of the write-in gap a blank (write-in) draft cuts in one span: the [preferredWidth]
     * a hand needs, shrunk toward [minWidth] when the span cannot host it, so a short span gets
     * a smaller write-in spot rather than none at all.
     *
     * A span too tight even for [minWidth] is handed back [preferredWidth] — it then fails
     * [canFitInwardArrows] and takes the continuous-line fallback, which is the right answer
     * once the gap would be too small to write a dimension in.
     */
    fun blankGapWidth(
        spanWidth: Float,
        preferredWidth: Float,
        minWidth: Float,
        textPad: Float,
        arrowSize: Float,
    ): Float {
        val afford = spanWidth - 2f * textPad - 2f * arrowSize - GAP_FIT_SLACK
        return when {
            afford >= preferredWidth -> preferredWidth
            afford >= minWidth -> afford
            else -> preferredWidth
        }
    }

    /**
     * Arrow direction for one span, decided from the SPAN's own width — never from where its
     * value landed.
     *
     * Outward (tips-in) heads hang OUTSIDE the extension lines, so two of them on spans that
     * share a boundary meet there and cross. That is the price of the cramped-span convention
     * and it is only worth paying when the heads genuinely do not fit between the extension
     * lines: a span whose value floats above a continuous line still has its whole width free
     * for them (on-device report — a rail of long spans printed tips-in and the adjacent heads
     * crossed into an X).
     *
     * @param seatsInBreak the value sits in a break in this line, whose stubs the break was cut
     *   to keep arrow-wide — such a span always keeps inward heads.
     * @param arrowClear the daylight left between two inward heads, in the same unit as [xa]/[xb]
     *   — [ARROW_CLEAR] on the page; a caller laying out at another scale passes it scaled.
     */
    fun arrowsPointInward(
        xa: Float,
        xb: Float,
        seatsInBreak: Boolean,
        arrowSize: Float,
        arrowClear: Float = ARROW_CLEAR,
    ): Boolean = seatsInBreak || (xb - xa) >= 2f * arrowSize + arrowClear

    /** How far above its home height a floating value must sit to count as lifted off its line. */
    private const val LIFT_EPS: Float = 0.5f

    /** Clearance a leader keeps from a value it passes, each side. */
    private const val LEADER_CLEAR: Float = 2f

    /** A leader's bounding box, as the obstacle later values keep clear of. */
    private fun leaderBox(l: RailValueLeader): Box = Box(
        min(l.fromX, l.x) - LINE_HALF_CLEAR,
        min(l.fromY, l.toY),
        max(l.fromX, l.x) + LINE_HALF_CLEAR,
        max(l.fromY, l.toY),
    )

    /**
     * True when the leader's stroke passes through [b] grown by [LEADER_CLEAR] — the exact
     * segment, not its bounding box, so a slanted leader is not refused for a value that only
     * sits in its box's empty corner (Liang–Barsky clip).
     */
    private fun leaderHits(l: RailValueLeader, b: Box): Boolean {
        val e = b.expanded(LEADER_CLEAR, LEADER_CLEAR)
        val dx = l.x - l.fromX
        val dy = l.toY - l.fromY
        val p = floatArrayOf(-dx, dx, -dy, dy)
        val q = floatArrayOf(l.fromX - e.left, e.right - l.fromX, l.fromY - e.top, e.bottom - l.fromY)
        var t0 = 0f
        var t1 = 1f
        for (k in 0..3) {
            if (p[k] == 0f) {
                if (q[k] <= 0f) return false
            } else {
                val t = q[k] / p[k]
                if (p[k] < 0f) {
                    if (t > t1) return false
                    if (t > t0) t0 = t
                } else {
                    if (t < t0) return false
                    if (t < t1) t1 = t
                }
            }
        }
        return t0 < t1
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
        /** The value is wider than its own span's slide window, so it overhangs the span's ends. */
        val overhangs: Boolean,
    )

    /**
     * One drawn extension line as an obstacle: a vertical segment at [x] running from [topY] (its
     * highest owning rail) DOWN to the object. Modelled as unbounded below — every label sits
     * above the object, so nothing is lost and no caller has to pass the object's y in.
     */
    private class ExtLine(val x: Float, val topY: Float) {
        fun box(): Box = Box(x - LINE_HALF_CLEAR, topY, x + LINE_HALF_CLEAR, Float.MAX_VALUE)

        /** True when this line is one of [r]'s own boundaries — shared lines count as own. */
        fun belongsTo(r: Row): Boolean =
            abs(x - r.xa) <= EXT_SAME_X_EPS || abs(x - r.xb) <= EXT_SAME_X_EPS
    }

    /** Extension lines, collinear ones merged: one obstacle per x, topped by its highest rail. */
    private fun buildExtensionLines(rows: List<Row>): List<ExtLine> {
        val topByX = LinkedHashMap<Long, Pair<Float, Float>>(rows.size * 2)
        rows.forEach { r ->
            listOf(r.xa, r.xb).forEach { x ->
                val key = (x / EXT_SAME_X_EPS).toLong()
                val prev = topByX[key]
                topByX[key] = if (prev == null) x to r.y else prev.first to min(prev.second, r.y)
            }
        }
        return topByX.values.map { (x, topY) -> ExtLine(x, topY) }
    }

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
     * True when a BUMPED label [box] still reads with its own rail (line at [ownY]): no other
     * rail's line under it horizontally lies between the label and its own line, or sits nearer
     * to the label's centre than its own line does.
     */
    private fun bumpStaysOwn(box: Box, ownY: Float, rows: List<Row>, self: Int): Boolean {
        val cy = (box.top + box.bottom) * 0.5f
        val ownDist = ownY - cy
        return rows.indices.none { j ->
            val o = rows[j]
            j != self &&
                o.y < ownY - LINE_HALF_CLEAR &&
                o.xb > box.left && o.xa < box.right &&
                (o.y > box.top - LINE_HALF_CLEAR || (cy - o.y) < ownDist)
        }
    }

    /**
     * The label center nearest [wanted], inside `[lo, hi]`, that clears every obstacle
     * overlapping the candidate's vertical band. Null when the band is fully blocked.
     */
    private fun clearCenter(
        lo: Float,
        hi: Float,
        wanted: Float,
        half: Float,
        top: Float,
        bottom: Float,
        obstacles: List<Box>,
        pad: Float,
    ): Float? {
        // An obstacle that shares no vertical band with the candidate can never block it.
        // The rest forbid an open interval of centers.
        val forbidden = obstacles
            .filter { it.top < bottom + pad && top - pad < it.bottom }
            .map { it.left - half - pad to it.right + half + pad }
            .sortedBy { it.first }

        if (forbidden.isEmpty()) return wanted

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
            x >= lo && x <= hi && merged.none { x > it.first && x < it.second }

        if (allowed(wanted)) return wanted

        var best = Float.NaN
        for (iv in merged) {
            for (candidate in listOf(iv.first, iv.second)) {
                val c = candidate.coerceIn(lo, hi)
                if (!allowed(c)) continue
                if (best.isNaN() || abs(c - wanted) < abs(best - wanted)) best = c
            }
        }
        return if (best.isNaN()) null else best
    }
}
