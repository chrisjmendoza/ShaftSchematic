package com.android.shaftschematic.geom

private const val EPS_MM: Double = 1e-3

/** Classification for axial dimension tiering (mm-space). */
enum class SpanKind { DATUM, LOCAL, OAL }

/**
 * Deterministic tier assignment for axial dimension spans (mm-space).
 *
 * Rules implemented:
 * - Order by kind first (LOCAL before DATUM), then by start ascending (AFT→FWD).
 * - Within DATUM spans with equal start, shorter spans first so offsets stair-step outward.
 * - DATUM spans may not overlap any span on the same tier.
 * - LOCAL spans may overlap other LOCAL spans on the same tier, but may not overlap DATUM
 *   spans on the same tier.
 * - Endpoints touching are allowed (within EPS_MM).
 *
 * This is purely geometric and intentionally does not consider pixel overlap, text bounds,
 * arrowheads, or any model component types.
 */
object DeterministicTierAssigner {

    data class Tiered<T>(val tier: Int, val item: T)

    fun <T> assign(
        spans: List<T>,
        startMm: (T) -> Double,
        endMm: (T) -> Double,
        kind: (T) -> SpanKind = { SpanKind.LOCAL },
    ): List<Tiered<T>> {
        if (spans.isEmpty()) return emptyList()

        data class Norm<T>(val item: T, val start: Double, val end: Double, val kind: SpanKind)

        fun kindOrder(k: SpanKind): Int = when (k) {
            SpanKind.LOCAL -> 0
            SpanKind.DATUM -> 1
            SpanKind.OAL -> 2
        }

        fun overlapsStrict(a1: Double, b1: Double, a2: Double, b2: Double): Boolean {
            // endpoints touching are allowed; require overlap beyond EPS_MM
            return maxOf(a1, a2) + EPS_MM < minOf(b1, b2)
        }

        val sorted = spans
            .asSequence()
            .map { s ->
                val a = startMm(s)
                val b = endMm(s)
                val lo = minOf(a, b)
                val hi = maxOf(a, b)
                Norm(item = s, start = lo, end = hi, kind = kind(s))
            }
            .filter { it.kind != SpanKind.OAL }
            .sortedWith { a, b ->
                val k = kindOrder(a.kind) - kindOrder(b.kind)
                if (k != 0) return@sortedWith k

                val s = a.start.compareTo(b.start)
                if (s != 0) return@sortedWith s

                val e = when (a.kind) {
                    SpanKind.DATUM -> a.end.compareTo(b.end) // shorter first
                    SpanKind.LOCAL -> b.end.compareTo(a.end) // longer first
                    SpanKind.OAL -> 0
                }
                if (e != 0) return@sortedWith e
                0
            }
            .toList()

        val tiers = mutableListOf<MutableList<Norm<T>>>()
        val out = ArrayList<Tiered<T>>(sorted.size)

        for (n in sorted) {
            var tier = 0
            while (true) {
                if (tier >= tiers.size) {
                    tiers.add(mutableListOf())
                }
                val line = tiers[tier]

                val blocks = when (n.kind) {
                    SpanKind.DATUM -> {
                        // Datum spans must stair-step: no overlap on same tier with any span.
                        line.any { placed -> overlapsStrict(n.start, n.end, placed.start, placed.end) }
                    }
                    SpanKind.LOCAL -> {
                        // Local spans hug low tiers: only avoid overlapping datum spans on that tier.
                        line.asSequence()
                            .filter { it.kind == SpanKind.DATUM }
                            .any { placed -> overlapsStrict(n.start, n.end, placed.start, placed.end) }
                    }
                    SpanKind.OAL -> false
                }

                if (!blocks) {
                    line += n
                    out += Tiered(tier = tier, item = n.item)
                    break
                }
                tier++
            }
        }

        return out
    }
}
