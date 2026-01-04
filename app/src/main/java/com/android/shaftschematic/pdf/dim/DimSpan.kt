package com.android.shaftschematic.pdf.dim

import com.android.shaftschematic.geom.DeterministicTierAssigner
import kotlin.math.roundToLong

private const val DEDUPE_EPS_MM: Double = 1e-3

/** Classification for axial dimension tiering (mm-space). */
enum class SpanKind { DATUM, LOCAL, OAL }

/**
 * A single dimension line from x1→x2 with labels.
 * x values are in measurement-space millimeters.
 */
data class DimSpan(
    val x1Mm: Double,
    val x2Mm: Double,
    val labelTop: String,
    val kind: SpanKind = SpanKind.LOCAL,
    val labelBottom: String? = null
)

data class RailSpan(val rail: Int, val span: DimSpan)

/**
 * Packs dimension spans into stacked rails using deterministic, left-to-right tiering.
 *
 * Tiering is purely geometric (mm-space) and follows machinist conventions:
 * - LOCAL spans hug low rails.
 * - DATUM spans stair-step above to avoid crossing/overlap.
 * - Endpoints touching are allowed.
 */
class RailPlanner {
    fun assignAll(spans: List<DimSpan>): List<RailSpan> {
        val unique = dedupeExactSpans(spans)
        val tiered = DeterministicTierAssigner.assign(
            spans = unique,
            startMm = { minOf(it.x1Mm, it.x2Mm) },
            endMm = { maxOf(it.x1Mm, it.x2Mm) },
            kind = { it.kind.toGeomKind() },
        )

        return tiered.map { RailSpan(rail = it.tier, span = it.item) }
    }
}

private fun dedupeExactSpans(spans: List<DimSpan>): List<DimSpan> {
    if (spans.size <= 1) return spans

    fun q(x: Double): Long = (x / DEDUPE_EPS_MM).roundToLong()

    data class Key(
        val a: Long,
        val b: Long,
        val labelTop: String,
        val labelBottom: String?
    )

    fun prefer(a: SpanKind, b: SpanKind): SpanKind {
        // Prefer a LOCAL representation when two different sources produce the same span,
        // so the span stays on low rails and doesn't force stair-stepping.
        fun score(k: SpanKind) = when (k) {
            SpanKind.LOCAL -> 2
            SpanKind.DATUM -> 1
            SpanKind.OAL -> 0
        }
        return if (score(a) >= score(b)) a else b
    }

    val byKey = LinkedHashMap<Key, DimSpan>(spans.size)

    for (s in spans) {
        val lo = minOf(s.x1Mm, s.x2Mm)
        val hi = maxOf(s.x1Mm, s.x2Mm)
        val key = Key(q(lo), q(hi), s.labelTop, s.labelBottom)

        val existing = byKey[key]
        if (existing == null) {
            byKey[key] = s
        } else {
            val keepKind = prefer(existing.kind, s.kind)
            if (keepKind != existing.kind) {
                byKey[key] = if (keepKind == s.kind) s else existing
            }
        }
    }

    return byKey.values.toList()
}

private fun SpanKind.toGeomKind(): com.android.shaftschematic.geom.SpanKind = when (this) {
    SpanKind.DATUM -> com.android.shaftschematic.geom.SpanKind.DATUM
    SpanKind.LOCAL -> com.android.shaftschematic.geom.SpanKind.LOCAL
    SpanKind.OAL -> com.android.shaftschematic.geom.SpanKind.OAL
}
