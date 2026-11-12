package com.android.shaftschematic.geom

/**
 * DiameterCalloutLayout — pure row and side placement for below-shaft callouts.
 *
 * The schematic PDF hangs body/liner Ø labels — and an Angle-mode blend's angle callout —
 * BELOW the shaft. When two labels sit close together horizontally their text would overlap,
 * so a colliding label is bumped down to a second row (tier 1) — the same two-row posture the
 * runout bubbles use ([RunoutBubbleLayout]) — or hung on the other side of its leader. [place]
 * decides both; the renderer owns the pixels.
 *
 * Purely geometric: inputs are plain output-unit floats already measured by the caller
 * (which owns the text [android.graphics.Paint]). Follows the `geom/` convention — no
 * Android imports, no `pdf`/`ui` dependency — so it is unit-testable on the JVM.
 */
object DiameterCalloutLayout {

    /** Two rows below the shaft, matching the runout-bubble convention. */
    const val MAX_TIERS: Int = 2

    /** Spots the joint search in [place] may try before it gives up. */
    private const val JOINT_SEARCH_BUDGET: Int = 200_000

    /** Horizontal clearance (output units, pt) kept between a placed label's right edge
     *  and the next label's left edge on the same tier. */
    const val MIN_GAP: Float = 4f

    /** The occupied horizontal span of one callout in output units (pt). */
    data class Footprint(val left: Float, val right: Float)

    /**
     * Assigns a 0-based tier to each callout footprint so labels on the same tier never
     * overlap horizontally (best effort within [maxTiers]).
     *
     * Greedy interval-graph coloring: footprints are placed left-to-right, each onto the
     * lowest tier whose last-placed label clears it by [gap]. When more than [maxTiers]
     * labels crowd one x-range the extra lands on the least-crowded tier (minor overlap
     * accepted rather than throwing or growing past [maxTiers]).
     *
     * @return tier per input footprint, parallel to [footprints] (same order and size).
     *   Every returned tier is in `[0, maxTiers)`. Empty in → empty out.
     */
    fun assignTiers(
        footprints: List<Footprint>,
        maxTiers: Int = MAX_TIERS,
        gap: Float = MIN_GAP,
    ): List<Int> {
        if (footprints.isEmpty()) return emptyList()
        val tiers = maxTiers.coerceAtLeast(1)

        // Sort by (left, then right, then original index) so equal inputs are deterministic.
        val order = footprints.indices.sortedWith(
            compareBy({ footprints[it].left }, { footprints[it].right }, { it })
        )

        val rightEdge = FloatArray(tiers) { Float.NEGATIVE_INFINITY }
        val result = IntArray(footprints.size)

        for (idx in order) {
            val fp = footprints[idx]
            var placed = -1
            for (t in 0 until tiers) {
                if (rightEdge[t] + gap <= fp.left) { placed = t; break }
            }
            if (placed < 0) {
                // Degenerate: every tier is still occupied near this x. Use the tier whose
                // last label ends earliest — the least-bad overlap.
                var best = 0
                for (t in 1 until tiers) if (rightEdge[t] < rightEdge[best]) best = t
                placed = best
            }
            rightEdge[placed] = maxOf(rightEdge[placed], fp.right)
            result[idx] = placed
        }
        return result.toList()
    }

    /**
     * Left x of a callout label hung off a leader at [anchorX]. The label normally sits right of
     * the anchor past the [dogleg]; one that would run past [contentRight] flips to the LEFT of
     * the anchor instead (the dogleg then runs left into its right edge), and one that fits on
     * neither side is pushed back inside the content, so no value ever prints into the page
     * margin (on-device report: a `Ø 5.998"` ran to within a quarter inch of the paper edge,
     * where a printer's unprintable border takes the closing mark). The leader always stays on
     * its anchor.
     */
    fun labelLeft(
        anchorX: Float,
        labelWidth: Float,
        dogleg: Float,
        contentLeft: Float = Float.NEGATIVE_INFINITY,
        contentRight: Float = Float.POSITIVE_INFINITY,
    ): Float {
        val right = anchorX + dogleg
        if (right + labelWidth <= contentRight) return right
        val left = anchorX - dogleg - labelWidth
        if (left >= contentLeft) return left
        return (contentRight - labelWidth).coerceAtLeast(contentLeft)
    }

    /**
     * One callout as [place] sees it: where its leader stands, how wide its label is, and whether
     * the leader points at a FEATURE (a blend's chamfer) rather than a component's diameter.
     */
    data class Item(
        val anchorX: Float,
        val labelWidth: Float,
        val feature: Boolean = false,
        /**
         * How far the leader may stand from [anchorX]: a diameter reads as its component's from
         * anywhere along it, so its leader may slide inside `[anchorMin, anchorMax]` to clear a
         * neighbour. Both default to [anchorX] — a leader that cannot move (a feature's).
         */
        val anchorMin: Float = anchorX,
        val anchorMax: Float = anchorX,
    )

    /** Where [place] put a callout: its row, its label's left edge and where its leader stands. */
    data class Placement(val tier: Int, val labelLeft: Float, val anchorX: Float)

    /** Cost of one point of a value's width set over another value on its row (no clear spot left). */
    private const val OVERLAP_COST_PER_PT: Float = 2f

    /** Cost of a leader dropped through a value — about as bad as 12 pt of two values overlapping. */
    private const val LEADER_CROSS_COST: Float = 24f

    /** Alternative leader positions [place] tries on each side of a callout's own anchor. */
    private const val ANCHOR_STEPS: Int = 4

    private class Placed(val tier: Int, val anchorX: Float, val left: Float, val right: Float)

    /**
     * Tier AND label side for every below-shaft callout, placed together.
     *
     * Three things a callout may not do: overlap another callout's label-and-dogleg on its own
     * row, drop its leader through a label on a row above it, or set its label across the leader
     * of a callout on a row below it. [assignTiers] checks only the first, row by row, so a
     * tier-1 callout standing under a tier-0 value printed its leader through that value — a
     * blend's angle beside a liner's Ø (on-device report).
     *
     * Each callout takes the first clear spot in this order: row 0 on its usual side
     * ([labelLeft]), row 1 on its usual side, then the same rows with the label hung on the
     * OTHER side of its leader — and, for a diameter, the same again with its leader slid along
     * its component ([Item.anchorMin]..[Item.anchorMax]), nearest its own anchor first (on-device
     * report: a body's Ø beside a liner's Ø and a blend's angle had nowhere clear to stand). Rows before sides, so a sheet whose callouts never conflicted, or
     * only needed a second row, is placed exactly as [assignTiers] placed it. Feature callouts are
     * placed first: their leaders point at one spot on the outline and cannot stand anywhere else,
     * while a diameter's value reads as its component's from any row. When that leaves a callout
     * with no clear spot, the spots are searched TOGETHER for an arrangement that clears them all
     * — an earlier callout moving to its second row or other side — so a blend's angle whose
     * value had to hang left, under the liner, no longer takes the liner's Ø leader through it
     * (on-device report). A caller with room for it passes [fallbackTiers]: a crowd with no clear
     * arrangement in [maxTiers] rows then tries one more row at a time, up to that many (large
     * print — the values are wide enough that two rows cannot hold them). Only when no
     * arrangement is clear at all does a callout take the spot that overlaps its neighbours
     * LEAST — a value set squarely on another is the one result worse than any other, so a
     * leader through a value or two values touching is chosen before it. Never dropped.
     *
     * [overhang] is how far a label may run past the content edge into the page margin when no
     * spot inside the content is clear (on-device direction: the margin beyond the shaft's end is
     * paper too). Spots inside the content are always tried first.
     *
     * @return one [Placement] per item, parallel to [items].
     */
    fun place(
        items: List<Item>,
        dogleg: Float,
        contentLeft: Float = Float.NEGATIVE_INFINITY,
        contentRight: Float = Float.POSITIVE_INFINITY,
        maxTiers: Int = MAX_TIERS,
        gap: Float = MIN_GAP,
        fallbackTiers: Int = maxTiers,
        overhang: Float = 0f,
    ): List<Placement> {
        val (placements, clear) = placeIn(items, dogleg, contentLeft, contentRight, maxTiers, gap, overhang)
        if (clear || fallbackTiers <= maxTiers) return placements
        // No clear arrangement in [maxTiers] rows — large values on a crowded stretch. One more
        // row is cheaper than a value printed through a leader; the caller's band reserves
        // whatever rows the placement uses ([occupancy] reads them off the same answer).
        for (t in maxTiers + 1..fallbackTiers) {
            val (more, moreClear) = placeIn(items, dogleg, contentLeft, contentRight, t, gap, overhang)
            if (moreClear || t == fallbackTiers) return more
        }
        return placements
    }

    /** [place] at a fixed number of rows: the placements, and whether every callout is clear. */
    private fun placeIn(
        items: List<Item>,
        dogleg: Float,
        contentLeft: Float,
        contentRight: Float,
        maxTiers: Int,
        gap: Float,
        overhang: Float,
    ): Pair<List<Placement>, Boolean> {
        if (items.isEmpty()) return emptyList<Placement>() to true
        val tiers = maxTiers.coerceAtLeast(1)
        val order = items.indices.sortedWith(
            compareBy({ !items[it].feature }, { items[it].anchorX }, { items[it].labelWidth }, { it })
        )
        val placed = ArrayList<Placed>(items.size)
        val result = arrayOfNulls<Placement>(items.size)

        fun clear(tier: Int, anchorX: Float, left: Float, right: Float): Boolean = placed.none { p ->
            when {
                p.tier == tier -> left < p.right + gap && p.left < right + gap
                // This leader drops through p's row.
                p.tier < tier -> anchorX > p.left - gap && anchorX < p.right + gap
                // p's leader drops through this row.
                else -> p.anchorX > left - gap && p.anchorX < right + gap
            }
        }

        // Where a callout's leader may stand: its own anchor first, then steps away from it
        // toward each end of its component, nearest first.
        fun anchors(item: Item): List<Float> = buildList {
            add(item.anchorX)
            val lo = minOf(item.anchorMin, item.anchorX)
            val hi = maxOf(item.anchorMax, item.anchorX)
            for (k in 1..ANCHOR_STEPS) {
                if (lo < item.anchorX) add(item.anchorX - (item.anchorX - lo) * k / ANCHOR_STEPS)
                if (hi > item.anchorX) add(item.anchorX + (hi - item.anchorX) * k / ANCHOR_STEPS)
            }
        }

        // Every spot a callout may take, in preference order: its own anchor before a slid one,
        // rows before sides.
        fun spots(item: Item): List<Placement> {
            fun rows(labelL: Float, anchor: Float) = (0 until tiers).map { t -> Placement(t, labelL, anchor) }
            val inside = ArrayList<Placement>()
            val margin = ArrayList<Placement>()
            for (anchor in anchors(item)) {
                val usual = labelLeft(anchor, item.labelWidth, dogleg, contentLeft, contentRight)
                inside += rows(usual, anchor)
                // The other side of the leader, when the label fits inside the content there.
                val flipped = if (usual >= anchor) anchor - dogleg - item.labelWidth else anchor + dogleg
                if (flipped >= contentLeft && flipped + item.labelWidth <= contentRight) inside += rows(flipped, anchor)
                // Either side with the label run a little into the page margin — tried only after
                // every spot inside the content, slid leaders included.
                if (overhang > 0f) {
                    val right = anchor + dogleg
                    val left = anchor - dogleg - item.labelWidth
                    if (right + item.labelWidth > contentRight && right + item.labelWidth <= contentRight + overhang) {
                        margin += rows(right, anchor)
                    }
                    if (left < contentLeft && left >= contentLeft - overhang) margin += rows(left, anchor)
                }
            }
            return inside + margin
        }
        fun placedAt(item: Item, at: Placement) = Placed(
            at.tier, at.anchorX,
            minOf(at.anchorX, at.labelLeft), maxOf(at.anchorX, at.labelLeft + item.labelWidth),
        )
        fun isClear(item: Item, at: Placement): Boolean {
            val box = placedAt(item, at)
            return clear(at.tier, at.anchorX, box.left, box.right)
        }

        // What a spot costs when none is clear: every point of a value set over another, and a
        // flat price for each leader through a value.
        fun cost(item: Item, at: Placement): Float {
            val box = placedAt(item, at)
            var sum = 0f
            for (p in placed) {
                sum += when {
                    p.tier == box.tier ->
                        (minOf(box.right, p.right) + gap - maxOf(box.left, p.left)).coerceAtLeast(0f) * OVERLAP_COST_PER_PT
                    p.tier < box.tier ->
                        if (box.anchorX > p.left - gap && box.anchorX < p.right + gap) LEADER_CROSS_COST else 0f
                    else ->
                        if (p.anchorX > box.left - gap && p.anchorX < box.right + gap) LEADER_CROSS_COST else 0f
                }
            }
            return sum
        }

        val forced = ArrayList<Int>()
        for (idx in order) {
            val item = items[idx]
            val options = spots(item)
            val pick = options.firstOrNull { isClear(item, it) } ?: run {
                forced += idx
                // The earliest of the least-bad spots, so preference order still breaks ties.
                options.minBy { cost(item, it) }
            }
            placed += placedAt(item, pick)
            result[idx] = pick
        }
        if (forced.isEmpty()) return result.map { it!! } to true

        // Taking each callout's first clear spot in turn left one with none, so it stands on
        // another's value. An earlier callout giving up its first choice can free the spot: search
        // the spots of the crowded callouts TOGETHER for an arrangement in which every one is
        // clear. Only the callouts that can reach the stuck one take part — everything a callout
        // could ever cover is its `reach`, and the group is the stuck callouts plus whatever
        // their reaches chain to — so a callout clear of every crowd keeps its place and costs the
        // search nothing. The search is bounded; without a clear arrangement the
        // one-at-a-time placement above stands.
        fun reach(item: Item): ClosedFloatingPointRange<Float> {
            val pad = dogleg + item.labelWidth + gap
            return (minOf(item.anchorMin, item.anchorX) - pad)..(maxOf(item.anchorMax, item.anchorX) + pad)
        }
        val group = forced.toMutableSet()
        var grew = true
        while (grew) {
            grew = false
            for (idx in items.indices) {
                if (idx in group) continue
                val r = reach(items[idx])
                if (group.any { g -> reach(items[g]).let { it.start <= r.endInclusive && r.start <= it.endInclusive } }) {
                    group += idx
                    grew = true
                }
            }
        }
        // The callouts outside the group stand where they were put; the group is placed again
        // around them — one CROWD at a time. Callouts whose reaches never chain cannot affect each
        // other, so a crowd at each end of the shaft is two small searches, not one whose size is
        // their product (a sheet with both ran the search out of budget and arranged neither).
        // Within a crowd: fewest spots first (the callout with least freedom claims its spot
        // before one that can move around it), then in the usual order.
        placed.clear()
        for (idx in order) if (idx !in group) placed += placedAt(items[idx], result[idx]!!)
        val crowds = ArrayList<MutableSet<Int>>()
        for (idx in group) {
            val r = reach(items[idx])
            val touching = crowds.filter { c ->
                c.any { g -> reach(items[g]).let { it.start <= r.endInclusive && r.start <= it.endInclusive } }
            }
            crowds.removeAll(touching)
            crowds += touching.fold(mutableSetOf(idx)) { acc, c -> acc.apply { addAll(c) } }
        }
        var solved = true
        for (crowd in crowds) {
            val crowdSpots = crowd.associateWith { spots(items[it]) }
            val crowdOrder = order.filter { it in crowd }.sortedBy { crowdSpots.getValue(it).size }
            val joint = HashMap<Int, Placement>()
            var budget = JOINT_SEARCH_BUDGET
            val base = placed.size
            fun search(k: Int): Boolean {
                if (k == crowdOrder.size) return true
                val idx = crowdOrder[k]
                val item = items[idx]
                for (at in crowdSpots.getValue(idx)) {
                    if (budget-- <= 0) return false
                    if (!isClear(item, at)) continue
                    placed += placedAt(item, at)
                    joint[idx] = at
                    if (search(k + 1)) return true
                    placed.removeAt(placed.lastIndex)
                }
                return false
            }
            if (search(0)) {
                for ((idx, at) in joint) result[idx] = at
            } else {
                // No clear arrangement for this crowd: its one-at-a-time placement stands.
                solved = false
                while (placed.size > base) placed.removeAt(placed.lastIndex)
                for (idx in crowdOrder) placed += placedAt(items[idx], result[idx]!!)
            }
        }
        return result.map { it!! } to solved
    }
}
