package com.android.shaftschematic.geom

/**
 * ProfileCompression — the piecewise x mapping behind the hand-sheet drawing convention
 * (on-device request, with rulered reference sketches):
 *
 * - The shaft's drawn HEIGHT follows its true diameter on the default sizing curve
 *   ([defaultShaftHeightPt]) — standard: proportional, an 8" shaft prints 1" tall, a
 *   6" shaft 3/4", a 4" shaft 1/2" — and is never diluted by shaft length.
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
 * Flat visual diameter scale (pt per mm of TRUE diameter) — the degenerate-diameter
 * fallback in [defaultVisualScale]; the sizing curve ([defaultShaftHeightPt]) is the
 * default.
 */
const val VISUAL_DIA_SCALE_PT_PER_MM = 0.40f

// Default sizing curve anchors. The STANDARD values put the line through the origin —
// drawn height stays strictly proportional to true diameter (8" → 1", 6" → 3/4",
// 4" → 1/2" — the hand-sheet rule from the original rulered sketches; taller defaults
// read "chubby" on-device) — and the line continues past both anchors until the
// absolute [PROFILE_MAX_SHAFT_HEIGHT_PT] ceiling. The anchor DIAMETERS are fixed; the
// anchor HEIGHTS are user-adjustable (Settings → Drawing → Default drawing size,
// `PdfPrefs.curveLoHeightIn/curveHiHeightIn`) with these as the standard values — a
// non-proportional pair (e.g. 0.75"/1.25") is a deliberate choice there, never the
// default. The "Shaft height" slider multiplies on top.
const val PROFILE_DEFAULT_HEIGHT_LO_DIA_MM = 101.6f  // 4 in
const val PROFILE_DEFAULT_HEIGHT_LO_PT = 36f         // 0.5 in
const val PROFILE_DEFAULT_HEIGHT_HI_DIA_MM = 203.2f  // 8 in
const val PROFILE_DEFAULT_HEIGHT_HI_PT = 72f         // 1.0 in

/**
 * Default drawn shaft height (pt) for a true max diameter — the sizing-curve line
 * through 4" → [loHeightPt] and 8" → [hiHeightPt]. Anchor heights are sanitized here so
 * every configured pair yields a sane drawing: each is capped at the absolute ceiling,
 * and [hiHeightPt] never drops below [loHeightPt] (a larger shaft never draws smaller —
 * an inverted pair flattens the line at the low anchor instead).
 */
fun defaultShaftHeightPt(
    maxDiaMm: Float,
    loHeightPt: Float = PROFILE_DEFAULT_HEIGHT_LO_PT,
    hiHeightPt: Float = PROFILE_DEFAULT_HEIGHT_HI_PT,
): Float {
    if (maxDiaMm <= 0f) return 0f
    val lo = loHeightPt.coerceIn(1f, PROFILE_MAX_SHAFT_HEIGHT_PT)
    val hi = hiHeightPt.coerceIn(lo, PROFILE_MAX_SHAFT_HEIGHT_PT)
    val slope = (hi - lo) /
        (PROFILE_DEFAULT_HEIGHT_HI_DIA_MM - PROFILE_DEFAULT_HEIGHT_LO_DIA_MM)
    return (lo + (maxDiaMm - PROFILE_DEFAULT_HEIGHT_LO_DIA_MM) * slope)
        .coerceIn(1f, PROFILE_MAX_SHAFT_HEIGHT_PT)
}

/**
 * The sizing curve expressed as a diameter scale (pt per mm) — the base every composer
 * solve and height-slider surface starts from. Falls back to the legacy flat scale when
 * the diameter is degenerate.
 */
fun defaultVisualScale(
    maxDiaMm: Float,
    loHeightPt: Float = PROFILE_DEFAULT_HEIGHT_LO_PT,
    hiHeightPt: Float = PROFILE_DEFAULT_HEIGHT_HI_PT,
): Float =
    if (maxDiaMm <= 0f) VISUAL_DIA_SCALE_PT_PER_MM
    else defaultShaftHeightPt(maxDiaMm, loHeightPt, hiHeightPt) / maxDiaMm

// Per-kind minimum drawn widths (pt). A span whose TRUE width is smaller than its floor
// simply draws true — floors never stretch anything. Liners compress in SIZE only
// (proportional foreshortening above their floor, on-device clarification: "compressed
// like bodies" — an S-break cutout — is what liners must never get; the S-break glyph is
// a body-only draw path). TAPERS carry NO flat floor — a flat floor equalizes unequal
// tapers when both clamp to it (on-device report: a 19.5" and an 11.5" taper drew
// identical). They use [PROFILE_TAPER_MIN_FRAC_OF_TRUE] instead: a fraction-of-true
// floor is ratio-preserving by construction — both tapers scale by the same factor at
// every squeeze (same λ, same K threshold), so their relative widths always read true.
// Tapers may shrink; they may never equalize.
const val PROFILE_MIN_THREAD_PT = 36f   // hatched stub stays legible
const val PROFILE_MIN_BODY_RUN_PT = 64f // write a diameter, hang runout leaders
const val PROFILE_MIN_LINER_PT = 100f   // room to write wear values in / read the liner

// Ratio-preserving taper floor: tapers keep at least this fraction of their true drawn
// width (λ-fit like the liner raises — the drawn height never yields to it). Deliberately
// the LARGEST fraction in the λ pool: within the shared λ, width flows to spans in
// proportion to their fraction, so tapers out-prioritize body runs by this ratio
// (on-device request: "sacrifice a little more of the body compression to make the
// tapers more proportional — liners get the most proportionality but tapers are
// important too").
const val PROFILE_TAPER_MIN_FRAC_OF_TRUE = 0.7f

// Ratio-preserving BODY-RUN floor: body gaps join the same λ pool so liner raises can
// never consume the whole page (on-device report: with proportional liners the body
// runs collapsed to their flat floors — equalized slivers, "I can't tell that the span
// between the aft and mid liner is longer"). Body runs keep at least this fraction of
// true width × λ, so their relative lengths always read; when space is tight, liners
// and bodies shrink TOGETHER (one λ) instead of liners taking all the slack. Body runs
// carry the SMALLEST fraction in the pool — they are the give that funds taper
// proportionality (see the taper floor above); their relative lengths still read at any
// squeeze because the fraction is ratio-preserving.
const val PROFILE_BODY_RUN_MIN_FRAC_OF_TRUE = 0.30f

// Schematic-only lean floors (on-device direction, experiment: the schematic needs
// PROPORTION more than write-in room — its values live on dimension rails and
// below-shaft callouts, never inside spans — while the runout/consolidated sheet keeps
// the writable floors above for in-profile values and hand-written bubbles).
const val SCHEMATIC_MIN_THREAD_PT = 28f
const val SCHEMATIC_MIN_BODY_RUN_PT = 40f
const val SCHEMATIC_MIN_LINER_PT = 56f

// Absolute bounds on the DRAWN shaft height — the band the "Shaft height" slider offers, in
// paper inches, on every shaft: 1/2" to 1 1/2" (on-device request). The ceiling is flexibility
// rather than a recommendation — a big shaft is usually a long one and drawing it tall really
// cramps the schematic — but the room is worth keeping, not least to write into. The floor is
// what lets exactly those big shafts be shrunk out of the way: without it a Ø11" shaft bottomed
// out at 0.69", half its standard height, because the track was a multiple of the curve rather
// than a paper measure.
//
// Every anchor height, slider track and settings range derives from these, so moving one moves
// the whole system; do not restate the figures.
const val PROFILE_MAX_SHAFT_HEIGHT_PT = 108f  // 1.5 in
const val PROFILE_MIN_SHAFT_HEIGHT_PT = 36f   // 0.5 in

// Multiplier bounds on the stored per-job `heightScale`. Deliberately wider than the band above
// — the slider is VALUE-based, so these only have to be roomy enough to express the whole band
// on any shaft: reaching 1/2" on a 14" shaft needs ~0.29, reaching 1 1/2" on a 2" shaft needs 6.
// The drawn height is clamped to the band regardless ([exaggeratedProfileScale]), so a wide
// multiplier range cannot produce a wide drawing.
const val PROFILE_HEIGHT_SCALE_MIN = 0.25f
const val PROFILE_HEIGHT_SCALE_MAX = 6.0f

/**
 * Apply the "Shaft height" exaggeration slider to a solved base scale: multiply by
 * [heightFrac] (clamped to the multiplier bounds), then clamp so the drawn shaft height
 * ([maxDiaMm] × scale) lands in the [PROFILE_MIN_SHAFT_HEIGHT_PT] … [PROFILE_MAX_SHAFT_HEIGHT_PT]
 * band — and never past the page budget ([budgetCapPt]).
 *
 * The ceiling is ABSOLUTE (on-device direction): a short shaft whose width-fit would
 * draw taller is capped too — it then simply doesn't span the page width, keeping true
 * proportion and leaving room for the dimension rails and the rest of the sheet.
 *
 * The floor **never raises a shaft above the sizing curve**. A shaft whose standard height is
 * already under the floor keeps that standard, so the proportional hand-sheet rule
 * ([defaultShaftHeightPt] — 4" → 1/2") is untouched at 100% and a small shaft is never fattened
 * into something it isn't. The floor exists so a LARGE shaft can be shrunk out of the way, which
 * is the case that needed it.
 *
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
    val floorScale = minOf(PROFILE_MIN_SHAFT_HEIGHT_PT / maxDiaMm, baseScale, capScale)
    return (baseScale * frac).coerceIn(floorScale, capScale)
}

/**
 * Drawn shaft height (pt) that [heightFrac] produces for a given base solve — the value
 * the "Shaft height" slider displays. Mirrors [exaggeratedProfileScale] with an
 * unbounded page budget (the UI can't know the final budget; it only ever lowers the
 * result a hair in extreme layouts).
 */
fun drawnShaftHeightPt(baseScale: Float, heightFrac: Float, maxDiaMm: Float): Float =
    exaggeratedProfileScale(baseScale, heightFrac, Float.MAX_VALUE, maxDiaMm) * maxDiaMm

/**
 * Inverse of [drawnShaftHeightPt]: the slider fraction that yields [targetHeightPt] for
 * this base solve, clamped to the multiplier bounds. The slider UIs select the height by
 * VALUE — the track spans the [PROFILE_MIN_SHAFT_HEIGHT_PT] … [PROFILE_MAX_SHAFT_HEIGHT_PT] band
 * on paper ("select the height by value, not percentage" — on-device request); this converts the
 * picked value back to the stored per-job multiplier.
 */
fun heightFracForDrawnHeight(baseScale: Float, targetHeightPt: Float, maxDiaMm: Float): Float {
    if (maxDiaMm <= 0f || baseScale <= 0f) return 1f
    return (targetHeightPt / (maxDiaMm * baseScale))
        .coerceIn(PROFILE_HEIGHT_SCALE_MIN, PROFILE_HEIGHT_SCALE_MAX)
}

/**
 * A feature span the caller wants width-managed individually. [minWidthPt] is its floor;
 * pass `Float.MAX_VALUE` to pin the span at true scale (e.g. a keyway-bearing body whose
 * drawn slot geometry must stay real).
 *
 * [minWidthFracOfTrue] raises the floor to a fraction of the span's TRUE drawn width at
 * the solved scale (0 = the flat [minWidthPt] floor alone; 1 = true scale) — the "Liner
 * compression" control: liners may only foreshorten this far. The fraction is
 * BEST-EFFORT and secondary to the drawing height (on-device direction: "that one takes
 * precedence"): it never enters the scale solve ([solveMaxProfileScale] ignores it), and
 * when the raised floors don't fit the page at the solved scale, the raises shrink
 * uniformly until they do ([fracFitFactor]) — flat floors and pins are untouched. Only a
 * `Float.MAX_VALUE` pin may yield the height.
 */
data class ProfileFeatureSpan(
    val startMm: Float,
    val endMm: Float,
    val minWidthPt: Float,
    val minWidthFracOfTrue: Float = 0f,
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
    gapMinFracOfTrue: Float = PROFILE_BODY_RUN_MIN_FRAC_OF_TRUE,
): CompressedProfileXMap {
    val width = contentRight - contentLeft
    val winLen = windowEndMm - windowStartMm
    if (winLen <= 1e-4f || width <= 1e-4f || diaPtPerMm <= 0f) {
        return CompressedProfileXMap(
            listOf(ProfileXSegment(windowStartMm, windowEndMm, contentLeft, contentRight, compressed = false))
        )
    }

    val spans = walkSpans(windowStartMm, windowEndMm, features, gapMinWidthPt, gapMinFracOfTrue)

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
    // A span's floor is its flat pt floor raised by its fraction-of-true demand; the
    // raises are best-effort — λ-fitted so they never overflow the page (height
    // precedence: the scale was solved without them and must not be re-litigated).
    val trues = spans.map { it.lenMm * diaPtPerMm }
    val lambda = fracFitLambda(spans, trues, width)
    val floors = spans.mapIndexed { i, s -> effectiveFloor(s, trues[i], lambda) }
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

/**
 * Effective width floor for a span at frac-raise factor [lambda]:
 * `min(true, max(flatFloor, true × frac × λ))`. A `Float.MAX_VALUE` pin resolves to the
 * span's true width regardless of λ — pins are guarantees, not raises.
 */
private fun effectiveFloor(span: WalkSpan, truePt: Float, lambda: Float): Float =
    minOf(maxOf(minOf(span.floorPt, truePt), truePt * span.fracOfTrue * lambda), truePt)

/**
 * Largest uniform factor λ ∈ [0,1] on the fraction-of-true floor raises such that the
 * floors still fit [width] — the height-precedence rule (on-device direction): the scale
 * is solved without the raises, and the raises then take only the room the page has at
 * that scale. Flat floors and pins never shrink here; if they alone overflow,
 * [solveSpanWidths]' degenerate squeeze remains the last resort.
 */
internal fun fracFitLambda(spans: List<WalkSpan>, trues: List<Float>, width: Float): Float {
    fun floorsSum(lambda: Float): Float =
        spans.indices.sumOf { effectiveFloor(spans[it], trues[it], lambda).toDouble() }.toFloat()
    if (spans.none { it.fracOfTrue > 0f } || floorsSum(1f) <= width) return 1f
    if (floorsSum(0f) >= width) return 0f
    var lo = 0f
    var hi = 1f
    repeat(30) {
        val mid = (lo + hi) / 2f
        if (floorsSum(mid) <= width) lo = mid else hi = mid
    }
    return lo
}

/**
 * The λ the map build will apply to this window's fraction-of-true floors — exposed pure
 * so UI readouts can report the proportionality liners actually achieve at a given scale
 * (requested fraction × λ). 1 = the full requested fractions fit.
 */
fun fracFitFactor(
    windowStartMm: Float,
    windowEndMm: Float,
    features: List<ProfileFeatureSpan>,
    contentWidthPt: Float,
    diaPtPerMm: Float,
    gapMinWidthPt: Float = PROFILE_MIN_BODY_RUN_PT,
    gapMinFracOfTrue: Float = PROFILE_BODY_RUN_MIN_FRAC_OF_TRUE,
): Float {
    if (windowEndMm - windowStartMm <= 1e-4f || contentWidthPt <= 1e-4f || diaPtPerMm <= 0f) return 1f
    val spans = walkSpans(windowStartMm, windowEndMm, features, gapMinWidthPt, gapMinFracOfTrue)
    val trues = spans.map { it.lenMm * diaPtPerMm }
    return fracFitLambda(spans, trues, contentWidthPt)
}

/** One walked span: a feature (with its floors) or a plain body gap between features. */
internal data class WalkSpan(
    val startMm: Float,
    val endMm: Float,
    val floorPt: Float,
    val fracOfTrue: Float = 0f,
) {
    val lenMm get() = endMm - startMm
}

/** Normalize features to the window and walk it: features + the gaps between them. */
internal fun walkSpans(
    windowStartMm: Float,
    windowEndMm: Float,
    features: List<ProfileFeatureSpan>,
    gapMinWidthPt: Float,
    gapMinFracOfTrue: Float = 0f,
): List<WalkSpan> {
    val normalized = normalizeFeatures(windowStartMm, windowEndMm, features)
    return buildList {
        var cursor = windowStartMm
        normalized.forEach { f ->
            if (f.startMm > cursor + 1e-4f) {
                add(WalkSpan(cursor, f.startMm, gapMinWidthPt, gapMinFracOfTrue))
            }
            add(WalkSpan(maxOf(f.startMm, cursor), f.endMm, f.minWidthPt, f.minWidthFracOfTrue))
            cursor = maxOf(cursor, f.endMm)
        }
        if (windowEndMm > cursor + 1e-4f) {
            add(WalkSpan(cursor, windowEndMm, gapMinWidthPt, gapMinFracOfTrue))
        }
    }
}

/**
 * Largest diameter scale at which the window can still lay out inside [contentWidth]:
 * a PINNED span (`minWidthPt == Float.MAX_VALUE` — keyway-bearing bodies, whose drawn
 * slot geometry must stay real) demands its full true width at the scale (the drawn
 * HEIGHT yields instead when a pinned span needs the room), every other span — liners
 * included — demands at most its FLAT floor. Fraction-of-true raises
 * ([ProfileFeatureSpan.minWidthFracOfTrue]) are deliberately IGNORED here — the drawing
 * height takes precedence over liner proportionality (on-device direction); the raises
 * λ-fit whatever room remains at the solved scale ([fracFitFactor]). The demand is
 * monotone in scale, so bisection finds the ceiling; [scaleHi] (the desired visual
 * scale, pre-capped by the height budget) is returned whenever it already fits.
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

/** Clip to the window, drop empties, sort, and merge overlaps (keeping the larger floors). */
private fun normalizeFeatures(
    windowStartMm: Float,
    windowEndMm: Float,
    features: List<ProfileFeatureSpan>,
): List<ProfileFeatureSpan> {
    val clipped = features
        .map {
            ProfileFeatureSpan(
                maxOf(it.startMm, windowStartMm), minOf(it.endMm, windowEndMm),
                it.minWidthPt, it.minWidthFracOfTrue,
            )
        }
        .filter { it.endMm - it.startMm > 1e-4f }
        .sortedBy { it.startMm }
    if (clipped.isEmpty()) return emptyList()
    val merged = mutableListOf(clipped.first())
    clipped.drop(1).forEach { f ->
        val last = merged.last()
        if (f.startMm <= last.endMm + 1e-4f) {
            merged[merged.lastIndex] = ProfileFeatureSpan(
                last.startMm, maxOf(last.endMm, f.endMm), maxOf(last.minWidthPt, f.minWidthPt),
                maxOf(last.minWidthFracOfTrue, f.minWidthFracOfTrue),
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
