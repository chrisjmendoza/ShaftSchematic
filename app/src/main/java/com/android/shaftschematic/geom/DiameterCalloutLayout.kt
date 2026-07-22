package com.android.shaftschematic.geom

/**
 * DiameterCalloutLayout — pure tier assignment for on-shaft diameter callouts.
 *
 * The schematic PDF hangs body/liner Ø labels BELOW the shaft. When two labels sit close
 * together horizontally their text would overlap, so a colliding label is bumped down to a
 * second row (tier 1) — the same two-row posture the runout bubbles use
 * ([RunoutBubbleLayout]). This engine decides the tier; the renderer owns the pixels.
 *
 * Purely geometric: inputs are plain output-unit floats already measured by the caller
 * (which owns the text [android.graphics.Paint]). Follows the `geom/` convention — no
 * Android imports, no `pdf`/`ui` dependency — so it is unit-testable on the JVM.
 */
object DiameterCalloutLayout {

    /** Two rows below the shaft, matching the runout-bubble convention. */
    const val MAX_TIERS: Int = 2

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
}
