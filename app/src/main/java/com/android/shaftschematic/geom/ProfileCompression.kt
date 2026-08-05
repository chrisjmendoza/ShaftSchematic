package com.android.shaftschematic.geom

/**
 * ProfileCompression — the piecewise x mapping behind the hand-sheet drawing convention
 * (on-device request, with rulered reference sketches):
 *
 * - The shaft's drawn HEIGHT is proportional to its true diameter at a fixed visual scale
 *   ([VISUAL_DIA_SCALE_PT_PER_MM]) — an 8" shaft prints ~1.1" tall, a 5–6" shaft ~3/4" —
 *   and is never diluted by shaft length.
 * - The x axis is schematic: every span may foreshorten, but each KIND keeps a minimum
 *   drawn width (liners stay wide enough to write wear values in, body runs wide enough
 *   to write diameters and hang runout leaders from, tapers keep their read).
 * - Above the floors, remaining width distributes in proportion to TRUE length — a longer
 *   body run visibly draws longer than a shorter one, and equal runs draw equal
 *   (on-device request). No span ever draws LONGER than its true scale.
 *
 * Pure and android-free (geom posture); the width solve is a single monotone parameter
 * (a horizontal pt-per-mm K): width_i(K) = clamp(K·len_i, min(floor_i, true_i), true_i),
 * with K found by bisection so Σ width_i equals the page width exactly.
 *
 * The map is strictly monotonic, so every consumer (dimension rails, bubble stations,
 * worn sections, wear marks, witness lines) can use `xAt` like a linear mapping.
 */

/**
 * Fixed visual diameter scale (pt per mm of TRUE diameter) — the hand-sheet sizing rule:
 * at 0.40 pt/mm an 8" shaft prints ~1.13" tall, 7" → ~1", 6" → ~0.85", 5" → ~0.71" —
 * "7–8 inch shafts about an inch tall, 5–6 inch about three-quarters".
 */
const val VISUAL_DIA_SCALE_PT_PER_MM = 0.40f

// Per-kind minimum drawn widths (pt). A span whose TRUE width is smaller than its floor
// simply draws true — floors never stretch anything. Liners compress in SIZE only
// (proportional foreshortening above their floor, on-device clarification: "compressed
// like bodies" — an S-break cutout — is what liners must never get; the S-break glyph is
// a body-only draw path). Keyway-bearing bodies stay PINNED at true scale
// (`Float.MAX_VALUE` floor); when a pinned span needs the room, the drawn height yields
// via [solveMaxProfileScale].
const val PROFILE_MIN_TAPER_PT = 80f    // keeps the taper read (slope may steepen)
const val PROFILE_MIN_THREAD_PT = 36f   // hatched stub stays legible
const val PROFILE_MIN_BODY_RUN_PT = 64f // write a diameter, hang runout leaders
const val PROFILE_MIN_LINER_PT = 100f   // room to write wear values in / read the liner

// "Shaft height" slider bounds (multiplier on the solved profile scale) and the absolute
// cap on the drawn shaft height — grown to at most 1.5" on paper (on-device request).
const val PROFILE_HEIGHT_SCALE_MIN = 0.5f
const val PROFILE_HEIGHT_SCALE_MAX = 3.0f
const val PROFILE_MAX_SHAFT_HEIGHT_PT = 108f  // 1.5 in

/**
 * Apply the "Shaft height" exaggeration slider to a solved base scale: multiply by
 * [heightFrac] (clamped to the slider bounds), then cap so the drawn shaft height
 * ([maxDiaMm] × scale) never exceeds [PROFILE_MAX_SHAFT_HEIGHT_PT] (1.5" on paper) or
 * the page budget ([budgetCapPt]).
 *
 * The ceiling is ABSOLUTE (on-device direction): a short shaft whose width-fit would
 * draw taller is capped too — it then simply doesn't span the page width, keeping true
 * proportion and leaving room for the dimension rails and the rest of the sheet.
 * Pure — the composer scale solve and any preview share this exact arithmetic.
 */
fun exaggeratedProfileScale(
    baseScale: Float,
    heightFrac: Float,
    budgetCapPt: Float,
    maxDiaMm: Float,
): Float {
    if (maxDiaMm <= 0f) return baseScale
    val frac = heightFrac.coerceIn(PROFILE_HEIGHT_SCALE_MIN, PROFILE_HEIGHT_SCALE_MAX)
    val capScale = (minOf(budgetCapPt, PROFILE_MAX_SHAFT_HEIGHT_PT) / maxDiaMm)
        .coerceAtLeast(1e-4f)
    return (baseScale * frac).coerceAtMost(capScale)
}

/**
 * The largest slider fraction that still changes the drawing for a given base solve —
 * past it the [PROFILE_MAX_SHAFT_HEIGHT_PT] ceiling (or the page budget) holds the scale
 * flat. The slider UIs end their track here so the dead zone reads as a limit instead of
 * an inert drag ("informs me the limit of the zoom slider range" — on-device request).
 * Never below [PROFILE_HEIGHT_SCALE_MIN] + a working margin, never above
 * [PROFILE_HEIGHT_SCALE_MAX].
 */
fun effectiveHeightScaleMax(
    baseScale: Float,
    budgetCapPt: Float,
    maxDiaMm: Float,
): Float {
    if (maxDiaMm <= 0f || baseScale <= 0f) return PROFILE_HEIGHT_SCALE_MAX
    val capScale = (minOf(budgetCapPt, PROFILE_MAX_SHAFT_HEIGHT_PT) / maxDiaMm)
        .coerceAtLeast(1e-4f)
    return (capScale / baseScale).coerceIn(PROFILE_HEIGHT_SCALE_MIN + 0.1f, PROFILE_HEIGHT_SCALE_MAX)
}

/**
 * A feature span the caller wants width-managed individually. [minWidthPt] is its floor;
 * pass `Float.MAX_VALUE` to pin the span at true scale (e.g. a keyway-bearing body whose
 * drawn slot geometry must stay real).
 */
data class ProfileFeatureSpan(
    val startMm: Float,
    val endMm: Float,
    val minWidthPt: Float,
)

/** One monotonic piece of the compressed-profile x mapping. */
data class ProfileXSegment(
    val startMm: Float,
    val endMm: Float,
    val x0: Float,
    val x1: Float,
    /** True when this span draws below true scale (foreshortened). */
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

    /**
     * Inverse of [xAt] — page x → shaft-space mm. Well-defined because the mapping is
     * strictly monotonic; positions outside the drawn window invert at the edge scale.
     * Lets consumers place marks evenly in DRAWN space (e.g. body runout stations) and
     * recover the physical mm they landed on.
     */
    fun mmAt(x: Float): Float {
        val first = segments.first()
        if (x <= first.x0) return first.startMm - (first.x0 - x) / edgeScale(first).coerceAtLeast(1e-6f)
        val last = segments.last()
        if (x >= last.x1) return last.endMm + (x - last.x1) / edgeScale(last).coerceAtLeast(1e-6f)
        val seg = segments.first { x <= it.x1 + 1e-4f }
        val ppm = seg.ptPerMm
        return if (ppm > 0f) seg.startMm + (x - seg.x0) / ppm else seg.startMm
    }

    private fun edgeScale(seg: ProfileXSegment): Float =
        seg.ptPerMm.takeIf { it > 0f }
            ?: segments.firstOrNull { it.ptPerMm > 0f }?.ptPerMm ?: 0f
}

/**
 * Build the piecewise mapping for [windowStartMm]..[windowEndMm] into
 * [contentLeft]..[contentRight]. [features] are the individually-floored spans (tapers,
 * liners, threads, pinned bodies); the gaps between them are plain body runs floored at
 * [gapMinWidthPt]. [diaPtPerMm] is the TRUE horizontal scale (the visual diameter scale) —
 * every span's width cap, and the uniform scale when the whole window fits.
 */
fun buildCompressedProfileXMap(
    windowStartMm: Float,
    windowEndMm: Float,
    features: List<ProfileFeatureSpan>,
    contentLeft: Float,
    contentRight: Float,
    diaPtPerMm: Float,
    gapMinWidthPt: Float = PROFILE_MIN_BODY_RUN_PT,
): CompressedProfileXMap {
    val width = contentRight - contentLeft
    val winLen = windowEndMm - windowStartMm
    if (winLen <= 1e-4f || width <= 1e-4f || diaPtPerMm <= 0f) {
        return CompressedProfileXMap(
            listOf(ProfileXSegment(windowStartMm, windowEndMm, contentLeft, contentRight, compressed = false))
        )
    }

    val spans = walkSpans(windowStartMm, windowEndMm, features, gapMinWidthPt)

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

    // Width solve: width_i(K) = clamp(K·len_i, min(floor_i, true_i), true_i), Σ = width.
    val trues = spans.map { it.lenMm * diaPtPerMm }
    val floors = spans.mapIndexed { i, s -> minOf(s.floorPt, trues[i]) }
    val widths = solveSpanWidths(spans.map { it.lenMm }, floors, trues, width)

    val segments = mutableListOf<ProfileXSegment>()
    var x = contentLeft
    spans.forEachIndexed { i, span ->
        val w = widths[i]
        segments += ProfileXSegment(
            startMm = span.startMm, endMm = span.endMm,
            x0 = x, x1 = x + w,
            compressed = w < trues[i] - 0.25f,
        )
        x += w
    }
    return CompressedProfileXMap(segments)
}

/** One walked span: a feature (with its floor) or a plain body gap between features. */
internal data class WalkSpan(val startMm: Float, val endMm: Float, val floorPt: Float) {
    val lenMm get() = endMm - startMm
}

/** Normalize features to the window and walk it: features + the gaps between them. */
internal fun walkSpans(
    windowStartMm: Float,
    windowEndMm: Float,
    features: List<ProfileFeatureSpan>,
    gapMinWidthPt: Float,
): List<WalkSpan> {
    val normalized = normalizeFeatures(windowStartMm, windowEndMm, features)
    return buildList {
        var cursor = windowStartMm
        normalized.forEach { f ->
            if (f.startMm > cursor + 1e-4f) add(WalkSpan(cursor, f.startMm, gapMinWidthPt))
            add(WalkSpan(maxOf(f.startMm, cursor), f.endMm, f.minWidthPt))
            cursor = maxOf(cursor, f.endMm)
        }
        if (windowEndMm > cursor + 1e-4f) add(WalkSpan(cursor, windowEndMm, gapMinWidthPt))
    }
}

/**
 * Largest diameter scale at which the window can still lay out inside [contentWidth]:
 * a PINNED span (`minWidthPt == Float.MAX_VALUE` — keyway-bearing bodies, whose drawn
 * slot geometry must stay real) demands its full true width at the scale (the drawn
 * HEIGHT yields instead when a pinned span needs the room), every other span — liners
 * included — demands at most its floor. The demand is monotone in scale, so bisection
 * finds the ceiling; [scaleHi] (the desired visual scale, pre-capped by the height
 * budget) is returned whenever it already fits.
 */
fun solveMaxProfileScale(
    windowStartMm: Float,
    windowEndMm: Float,
    features: List<ProfileFeatureSpan>,
    contentWidth: Float,
    scaleHi: Float,
    gapMinWidthPt: Float = PROFILE_MIN_BODY_RUN_PT,
): Float {
    if (windowEndMm - windowStartMm <= 1e-4f || contentWidth <= 1e-4f || scaleHi <= 0f) return scaleHi
    val spans = walkSpans(windowStartMm, windowEndMm, features, gapMinWidthPt)

    fun minWidthAt(s: Float): Float = spans.sumOf { span ->
        val truePt = span.lenMm * s
        (if (span.floorPt == Float.MAX_VALUE) truePt else minOf(span.floorPt, truePt)).toDouble()
    }.toFloat()

    if (minWidthAt(scaleHi) <= contentWidth) return scaleHi
    var lo = 0f
    var hi = scaleHi
    repeat(40) {
        val mid = (lo + hi) / 2f
        if (minWidthAt(mid) <= contentWidth) lo = mid else hi = mid
    }
    return lo
}

/** Clip to the window, drop empties, sort, and merge overlaps (keeping the larger floor). */
private fun normalizeFeatures(
    windowStartMm: Float,
    windowEndMm: Float,
    features: List<ProfileFeatureSpan>,
): List<ProfileFeatureSpan> {
    val clipped = features
        .map { ProfileFeatureSpan(maxOf(it.startMm, windowStartMm), minOf(it.endMm, windowEndMm), it.minWidthPt) }
        .filter { it.endMm - it.startMm > 1e-4f }
        .sortedBy { it.startMm }
    if (clipped.isEmpty()) return emptyList()
    val merged = mutableListOf(clipped.first())
    clipped.drop(1).forEach { f ->
        val last = merged.last()
        if (f.startMm <= last.endMm + 1e-4f) {
            merged[merged.lastIndex] = ProfileFeatureSpan(
                last.startMm, maxOf(last.endMm, f.endMm), maxOf(last.minWidthPt, f.minWidthPt),
            )
        } else {
            merged += f
        }
    }
    return merged
}

/**
 * Solve per-span widths: `clamp(K·len_i, floor_i, cap_i)` with the single scale K found by
 * bisection so the total equals [targetWidth]. Floors are pre-clamped to caps by the
 * caller. Monotone in K, so bisection converges; when even the floors alone exceed the
 * page (extreme feature counts) every floor scales down proportionally instead.
 */
internal fun solveSpanWidths(
    lensMm: List<Float>,
    floors: List<Float>,
    caps: List<Float>,
    targetWidth: Float,
): List<Float> {
    fun widthsAt(k: Float): List<Float> =
        lensMm.mapIndexed { i, len -> (k * len).coerceIn(floors[i], caps[i]) }

    val minTotal = floors.sum()
    if (minTotal >= targetWidth) {
        // Degenerate: floors alone overflow — squeeze everything proportionally.
        val s = targetWidth / minTotal.coerceAtLeast(1e-4f)
        return floors.map { it * s }
    }
    val maxTotal = caps.sum()
    if (maxTotal <= targetWidth) return caps.toList()

    var lo = 0f
    var hi = caps.max() / lensMm.filter { it > 0f }.min().coerceAtLeast(1e-4f)
    repeat(40) {
        val mid = (lo + hi) / 2f
        if (widthsAt(mid).sum() < targetWidth) lo = mid else hi = mid
    }
    val k = (lo + hi) / 2f
    // Exact-fit residue: spread the tiny bisection remainder over the unclamped spans.
    val w = widthsAt(k).toMutableList()
    val residue = targetWidth - w.sum()
    if (kotlin.math.abs(residue) > 1e-3f) {
        val free = w.indices.filter { w[it] > floors[it] + 1e-4f && w[it] < caps[it] - 1e-4f }
        if (free.isNotEmpty()) {
            val share = residue / free.size
            free.forEach { w[it] = (w[it] + share).coerceIn(floors[it], caps[it]) }
        }
    }
    return w
}
