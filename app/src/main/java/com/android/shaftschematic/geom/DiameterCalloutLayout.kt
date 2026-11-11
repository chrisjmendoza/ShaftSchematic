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
     * (on-device report). Only when no arrangement is clear does a callout take the least-crowded
     * row on its usual side — never dropped.
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
    ): List<Placement> {
        if (items.isEmpty()) return emptyList()
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
        fun spots(item: Item): List<Placement> = anchors(item).flatMap { anchor ->
            val usual = labelLeft(anchor, item.labelWidth, dogleg, contentLeft, contentRight)
            // The other side of the leader, when the label fits inside the content there.
            val flipped = if (usual >= anchor) anchor - dogleg - item.labelWidth else anchor + dogleg
            val sides = buildList {
                add(usual)
                if (flipped >= contentLeft && flipped + item.labelWidth <= contentRight) add(flipped)
            }
            sides.flatMap { labelL -> (0 until tiers).map { t -> Placement(t, labelL, anchor) } }
        }
        fun placedAt(item: Item, at: Placement) = Placed(
            at.tier, at.anchorX,
            minOf(at.anchorX, at.labelLeft), maxOf(at.anchorX, at.labelLeft + item.labelWidth),
        )
        fun isClear(item: Item, at: Placement): Boolean {
            val box = placedAt(item, at)
            return clear(at.tier, at.anchorX, box.left, box.right)
        }

        val forced = ArrayList<Int>()
        for (idx in order) {
            val item = items[idx]
            val options = spots(item)
            val pick = options.firstOrNull { isClear(item, it) } ?: run {
                forced += idx
                Placement(
                    tier = (0 until tiers).minBy { t ->
                        placed.filter { p -> p.tier == t }.maxOfOrNull { p -> p.right } ?: Float.NEGATIVE_INFINITY
                    },
                    labelLeft = options.first().labelLeft,
                    anchorX = item.anchorX,
                )
            }
            placed += placedAt(item, pick)
            result[idx] = pick
        }
        if (forced.isEmpty()) return result.map { it!! }

        // Taking each callout's first clear spot in turn left one with none, so it stands on
        // another's value. An earlier callout giving up its first choice can free the spot: search
        // the spots of the crowded callouts TOGETHER for an arrangement in which every one is
        // clear. Only the callouts that can reach the stuck one take part — everything a callout
        // could ever cover is its `reach`, and the group is the stuck callouts plus whatever
        // their reaches chain to — so a callout at the other end of the shaft keeps its place
        // and costs the search nothing. The search is bounded; without a clear arrangement the
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
        // around them, fewest spots first (the callout with least freedom claims its spot before
        // one that can move around it), then in the usual order.
        placed.clear()
        for (idx in order) if (idx !in group) placed += placedAt(items[idx], result[idx]!!)
        val groupSpots = group.associateWith { spots(items[it]) }
        val groupOrder = order.filter { it in group }.sortedBy { groupSpots.getValue(it).size }
        val joint = HashMap<Int, Placement>()
        var budget = JOINT_SEARCH_BUDGET
        fun search(k: Int): Boolean {
            if (k == groupOrder.size) return true
            val idx = groupOrder[k]
            val item = items[idx]
            for (at in groupSpots.getValue(idx)) {
                if (budget-- <= 0) return false
                if (!isClear(item, at)) continue
                placed += placedAt(item, at)
                joint[idx] = at
                if (search(k + 1)) return true
                placed.removeAt(placed.lastIndex)
            }
            return false
        }
        if (search(0)) for ((idx, at) in joint) result[idx] = at
        return result.map { it!! }
    }
}
