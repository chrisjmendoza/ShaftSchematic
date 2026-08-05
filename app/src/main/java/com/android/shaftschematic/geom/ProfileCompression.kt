package com.android.shaftschematic.geom

/**
 * ProfileCompression — the piecewise x mapping behind the hand-sheet drawing convention:
 * the shaft prints ~1–1.25" tall regardless of its true length, detail features (tapers,
 * liners, threads) keep TRUE proportions at that diameter scale, and the plain body runs
 * between them absorb the horizontal overflow — drawn foreshortened with S-break glyphs,
 * "compressed to give the impression of a thicker shaft" (on-device request, with the
 * shop's hand-drawn reference sheet).
 *
 * Pure and android-free (geom posture) so the allocation rules are directly unit-testable:
 * - Detail ("fixed") spans always draw at the supplied diameter scale — never distorted.
 * - When everything fits at that scale, the whole window maps linearly (no compression).
 * - Otherwise the compressible gaps share the remaining width by an equal-cap waterfill:
 *   every run keeps its true drawn length up to a common cap, and only runs longer than
 *   the cap are foreshortened — a short body between two liners never shrinks while a
 *   10-foot run takes the whole squeeze.
 *
 * The map is strictly monotonic, so every consumer (bubble stations, worn sections, wear
 * marks, witness lines) can use `xAt` interchangeably with the old linear mapping.
 */

/** One monotonic piece of the compressed-profile x mapping. */
data class ProfileXSegment(
    val startMm: Float,
    val endMm: Float,
    val x0: Float,
    val x1: Float,
    /** True when this span draws below true scale (a foreshortened body run). */
    val compressed: Boolean,
) {
    val ptPerMm: Float get() = if (endMm > startMm) (x1 - x0) / (endMm - startMm) else 0f
}

class CompressedProfileXMap internal constructor(val segments: List<ProfileXSegment>) {

    val startMm: Float get() = segments.first().startMm
    val endMm: Float get() = segments.last().endMm
    val x0: Float get() = segments.first().x0
    val x1: Float get() = segments.last().x1

    /** Map shaft-space mm → page x. Positions outside the window extrapolate at the edge scale. */
    fun xAt(mm: Float): Float {
        val first = segments.first()
        if (mm <= first.startMm) return first.x0 - (first.startMm - mm) * edgeScale(first)
        val last = segments.last()
        if (mm >= last.endMm) return last.x1 + (mm - last.endMm) * edgeScale(last)
        val seg = segments.first { mm <= it.endMm + 1e-4f }
        return seg.x0 + (mm - seg.startMm) * seg.ptPerMm
    }

    /** True when any part of [startMm]..[endMm] draws foreshortened. */
    fun isCompressedOver(startMm: Float, endMm: Float): Boolean =
        segments.any { it.compressed && it.startMm < endMm && it.endMm > startMm }

    private fun edgeScale(seg: ProfileXSegment): Float =
        seg.ptPerMm.takeIf { it > 0f }
            ?: segments.firstOrNull { it.ptPerMm > 0f }?.ptPerMm ?: 0f
}

/**
 * Fixed-span statistics used by callers to pre-clamp the diameter scale before building:
 * total never-compressed length (mm, clipped + merged) and the number of compressible
 * gaps in the window.
 */
data class ProfilePlanStats(val fixedLenMm: Float, val compressibleGapCount: Int)

fun compressedProfilePlanStats(
    windowStartMm: Float,
    windowEndMm: Float,
    fixedSpansMm: List<Pair<Float, Float>>,
): ProfilePlanStats {
    val fixed = normalizeFixedSpans(windowStartMm, windowEndMm, fixedSpansMm)
    val fixedLen = fixed.sumOf { (it.second - it.first).toDouble() }.toFloat()
    var gaps = 0
    var cursor = windowStartMm
    fixed.forEach { (s, e) ->
        if (s > cursor + 1e-4f) gaps++
        cursor = maxOf(cursor, e)
    }
    if (windowEndMm > cursor + 1e-4f) gaps++
    return ProfilePlanStats(fixedLen, gaps)
}

/**
 * Build the piecewise mapping for [windowStartMm]..[windowEndMm] into
 * [contentLeft]..[contentRight], drawing [fixedSpansMm] (and, when everything fits, the
 * whole window) at [diaPtPerMm].
 */
fun buildCompressedProfileXMap(
    windowStartMm: Float,
    windowEndMm: Float,
    fixedSpansMm: List<Pair<Float, Float>>,
    contentLeft: Float,
    contentRight: Float,
    diaPtPerMm: Float,
): CompressedProfileXMap {
    val width = contentRight - contentLeft
    val winLen = windowEndMm - windowStartMm
    if (winLen <= 1e-4f || width <= 1e-4f || diaPtPerMm <= 0f) {
        return CompressedProfileXMap(
            listOf(ProfileXSegment(windowStartMm, windowEndMm, contentLeft, contentRight, compressed = false))
        )
    }

    // Alternating span walk: fixed spans at true scale, gaps compressible.
    data class Span(val startMm: Float, val endMm: Float, val fixed: Boolean) {
        val lenMm get() = endMm - startMm
    }

    val fixed = normalizeFixedSpans(windowStartMm, windowEndMm, fixedSpansMm)
    val spans = buildList {
        var cursor = windowStartMm
        fixed.forEach { (s, e) ->
            if (s > cursor + 1e-4f) add(Span(cursor, s, fixed = false))
            add(Span(maxOf(s, cursor), e, fixed = true))
            cursor = maxOf(cursor, e)
        }
        if (windowEndMm > cursor + 1e-4f) add(Span(cursor, windowEndMm, fixed = false))
    }

    // Everything fits at true scale → plain linear map (may end short of contentRight).
    val totalTruePt = winLen * diaPtPerMm
    if (totalTruePt <= width + 0.5f) {
        return CompressedProfileXMap(
            listOf(
                ProfileXSegment(
                    windowStartMm, windowEndMm,
                    contentLeft, contentLeft + totalTruePt,
                    compressed = false,
                )
            )
        )
    }

    val fixedPt = spans.filter { it.fixed }.sumOf { (it.lenMm * diaPtPerMm).toDouble() }.toFloat()
    val gaps = spans.filter { !it.fixed }
    val availPt = width - fixedPt

    // Defensive fallback: detail spans alone (nearly) fill the page — the caller should
    // have pre-clamped the scale; degrade to the classic width-fit linear map.
    if (gaps.isEmpty() || availPt < gaps.size * 8f) {
        return CompressedProfileXMap(
            listOf(ProfileXSegment(windowStartMm, windowEndMm, contentLeft, contentRight, compressed = false))
        )
    }

    // Equal-cap waterfill: each gap keeps its true width up to a common cap chosen so the
    // capped widths exactly consume the available width. Only runs above the cap compress.
    val trueWidths = gaps.map { it.lenMm * diaPtPerMm }
    val cap = solveEqualCap(trueWidths, availPt)
    val gapWidths = trueWidths.map { minOf(it, cap) }

    // Walk the spans, laying x left → right.
    val segments = mutableListOf<ProfileXSegment>()
    var x = contentLeft
    var gapIdx = 0
    spans.forEach { span ->
        val w = if (span.fixed) span.lenMm * diaPtPerMm else gapWidths[gapIdx++]
        val truePt = span.lenMm * diaPtPerMm
        segments += ProfileXSegment(
            startMm = span.startMm, endMm = span.endMm,
            x0 = x, x1 = x + w,
            compressed = !span.fixed && w < truePt - 0.25f,
        )
        x += w
    }
    return CompressedProfileXMap(segments)
}

/** Clip to the window, drop empties, sort, and merge overlapping/touching spans. */
private fun normalizeFixedSpans(
    windowStartMm: Float,
    windowEndMm: Float,
    spans: List<Pair<Float, Float>>,
): List<Pair<Float, Float>> {
    val clipped = spans
        .map { (s, e) -> maxOf(s, windowStartMm) to minOf(e, windowEndMm) }
        .filter { (s, e) -> e - s > 1e-4f }
        .sortedBy { it.first }
    if (clipped.isEmpty()) return emptyList()
    val merged = mutableListOf(clipped.first())
    clipped.drop(1).forEach { (s, e) ->
        val last = merged.last()
        if (s <= last.second + 1e-4f) {
            if (e > last.second) merged[merged.lastIndex] = last.first to e
        } else {
            merged += s to e
        }
    }
    return merged
}

/**
 * Find the common cap so that `Σ min(width_i, cap) == avail`. Assumes
 * `Σ width_i > avail` (the compression branch) and `avail > 0`.
 */
internal fun solveEqualCap(trueWidths: List<Float>, avail: Float): Float {
    val sorted = trueWidths.sorted()
    var accumulated = 0f
    sorted.forEachIndexed { idx, w ->
        val remaining = sorted.size - idx
        if (accumulated + w * remaining >= avail) {
            return (avail - accumulated) / remaining
        }
        accumulated += w
    }
    return sorted.last()
}
