// file: app/src/main/java/com/android/shaftschematic/ui/screen/LinerWearMath.kt
package com.android.shaftschematic.ui.screen

import com.android.shaftschematic.model.WearSpotReference
import kotlin.math.abs
import kotlin.math.min

/**
 * Liner Wear Areas — Phase 2/3 pure math helpers.
 *
 * Kept as small, dependency-free top-level functions (no Compose/Android imports) so the
 * hit-testing, coordinate mapping, and clamping rules are directly unit-testable in a plain
 * JVM test, independent of the Compose canvas that calls them.
 *
 * See `docs/archive/LinerWearAreas_Proposal.md` §2 (tap hit-testing), §6.1 (detail-view scale + clamp),
 * §7 (invariants — wear is reference-only; clamping affects rendering, never stored data).
 */

/** A liner's axial span in shaft-physical mm, used for tap hit-testing on the overview canvas. */
data class LinerSpanMm(val id: String, val startMm: Float, val endMm: Float)

/**
 * Pick the liner whose span contains [tapMm]. Liners never overlap in practice, but if a tap
 * lands exactly on a shared boundary and two spans both claim it, the tie is broken by whichever
 * liner has the nearer edge to the tap position (proposal §2.1). Returns `null` when no liner
 * contains the tap.
 */
fun pickLinerIdAtMm(tapMm: Float, liners: List<LinerSpanMm>): String? {
    val candidates = liners.filter { tapMm >= it.startMm && tapMm <= it.endMm }
    if (candidates.isEmpty()) return null
    if (candidates.size == 1) return candidates[0].id
    return candidates.minByOrNull { span -> min(abs(tapMm - span.startMm), abs(tapMm - span.endMm)) }?.id
}

/**
 * A wear-spot span clamped into a liner's local length, `[0, linerLengthMm]`. Rendering-only —
 * the underlying `WearSpot` is never mutated by clamping. [isEmpty] is true when the spot lies
 * entirely outside the liner's current length (e.g. the liner was shortened after the spot was
 * recorded) — callers should skip drawing it in that case.
 */
data class ClampedWearBandMm(val startMm: Float, val endMm: Float) {
    val isEmpty: Boolean get() = endMm <= startMm
    val lengthMm: Float get() = (endMm - startMm).coerceAtLeast(0f)
}

/**
 * Clamp a wear spot's liner-local [spotStartMm]/[spotLengthMm] into `[0, linerLengthMm]`.
 * A spot extending past the liner end renders clamped; the stored data is untouched
 * (proposal §3, §7.2).
 */
fun clampWearBandToLiner(spotStartMm: Float, spotLengthMm: Float, linerLengthMm: Float): ClampedWearBandMm {
    val hi = linerLengthMm.coerceAtLeast(0f)
    val s = spotStartMm.coerceIn(0f, hi)
    val e = (spotStartMm + spotLengthMm).coerceIn(0f, hi)
    return ClampedWearBandMm(s, e.coerceAtLeast(s))
}

/**
 * Map a clamped liner-local band to canvas-space px, given the liner's origin x (px, the liner's
 * own AFT-edge screen position) and the detail canvas's scale (px per mm).
 */
fun wearBandToPx(band: ClampedWearBandMm, linerOriginPx: Float, pxPerMm: Float): Pair<Float, Float> =
    (linerOriginPx + band.startMm * pxPerMm) to (linerOriginPx + band.endMm * pxPerMm)

// ── Wear-spot "Measure from" reference conversion (post-review spec) ──

/**
 * Convert an entered "Start" value, authored against [reference], into the canonical
 * liner-local AFT-edge storage value ([com.android.shaftschematic.model.WearSpot.startMm]).
 *
 * Convention (matches the `Liner`/`CouplerBoltSlot` authored-reference pattern):
 * - AFT-referenced entries ([WearSpotReference.LINER_AFT], [WearSpotReference.AFT_SET])
 *   locate the band's **AFT edge**, measuring FWD from the reference point.
 * - FWD-referenced entries ([WearSpotReference.LINER_FWD], [WearSpotReference.FWD_SET])
 *   locate the band's **FWD edge**, measuring AFT from the reference point — the AFT
 *   edge (canonical storage) is then the FWD edge less [lengthMm].
 *
 * | reference  | canonical formula                                          |
 * |------------|-------------------------------------------------------------|
 * | LINER_AFT  | `enteredMm`                                                  |
 * | LINER_FWD  | `linerLengthMm - enteredMm - lengthMm`                       |
 * | AFT_SET    | `(aftSetXMm + enteredMm) - linerStartFromAftMm`              |
 * | FWD_SET    | `(fwdSetXMm - enteredMm) - linerStartFromAftMm - lengthMm`   |
 *
 * [aftSetXMm]/[fwdSetXMm] are physical shaft-space mm from the shaft's AFT end — the same
 * space as [linerStartFromAftMm] — e.g. `geom/OalComputations.kt`'s
 * `computeSetPositionsInMeasureSpace` (its `measureStartMm` is always `0.0`, so its
 * measure-space output already *is* physical shaft-space mm).
 *
 * Pure and side-effect-free; does not clamp or validate — see [wearSpotSpanProblem] for the
 * display-only in-span classification the card shows.
 */
fun wearStartToCanonicalMm(
    reference: WearSpotReference,
    enteredMm: Float,
    lengthMm: Float,
    linerStartFromAftMm: Float,
    linerLengthMm: Float,
    aftSetXMm: Float,
    fwdSetXMm: Float,
): Float = when (reference) {
    WearSpotReference.LINER_AFT -> enteredMm
    WearSpotReference.LINER_FWD -> linerLengthMm - enteredMm - lengthMm
    WearSpotReference.AFT_SET -> (aftSetXMm + enteredMm) - linerStartFromAftMm
    WearSpotReference.FWD_SET -> (fwdSetXMm - enteredMm) - linerStartFromAftMm - lengthMm
}

/**
 * Inverse of [wearStartToCanonicalMm]: project the canonical liner-local AFT-edge start
 * back into the value that would have been entered under [reference], for display. Exact
 * algebraic inverse — round-trips to within float precision, never clamped.
 */
fun canonicalToWearStartMm(
    reference: WearSpotReference,
    canonicalStartMm: Float,
    lengthMm: Float,
    linerStartFromAftMm: Float,
    linerLengthMm: Float,
    aftSetXMm: Float,
    fwdSetXMm: Float,
): Float = when (reference) {
    WearSpotReference.LINER_AFT -> canonicalStartMm
    WearSpotReference.LINER_FWD -> linerLengthMm - canonicalStartMm - lengthMm
    WearSpotReference.AFT_SET -> (linerStartFromAftMm + canonicalStartMm) - aftSetXMm
    WearSpotReference.FWD_SET -> fwdSetXMm - (linerStartFromAftMm + canonicalStartMm + lengthMm)
}

/**
 * The canonical start that keeps the DISPLAYED Start fixed when a spot's length changes from
 * [oldLengthMm] to [newLengthMm] under [reference] — the conversion pair composed. Identity
 * under an AFT-flavored reference; under a FWD-flavored one the band's FWD edge (the point the
 * Start was authored against) stays put and the band grows/shrinks AFT-ward. Committing a new
 * length against the old canonical start would rewrite the displayed Start by the length delta,
 * a typed value changing under the user (the undercut card's `undercutCanonicalForNewLength`
 * rule).
 */
fun wearSpotCanonicalForNewLength(
    reference: WearSpotReference,
    canonicalStartMm: Float,
    oldLengthMm: Float,
    newLengthMm: Float,
    linerStartFromAftMm: Float,
    linerLengthMm: Float,
    aftSetXMm: Float,
    fwdSetXMm: Float,
): Float {
    val displayedMm = canonicalToWearStartMm(
        reference, canonicalStartMm, oldLengthMm, linerStartFromAftMm, linerLengthMm, aftSetXMm, fwdSetXMm,
    )
    return wearStartToCanonicalMm(
        reference, displayedMm, newLengthMm, linerStartFromAftMm, linerLengthMm, aftSetXMm, fwdSetXMm,
    )
}

/** Epsilon for wear-span in-bounds checks — matches the app's other bounds-check tolerance. */
private const val WEAR_SPAN_EPS_MM = 1e-3f

/**
 * Why a wear spot's stored span does not fit, with the full [message] shown under the card's
 * fields and the [shortNote] shown beside the card's title.
 */
enum class WearSpotSpanProblem(val message: String, val shortNote: String) {
    ZERO_LENGTH("Length must be greater than zero", "No length"),
    PAST_LINER_AFT("Wear spot extends past the liner's AFT edge", "Extends past liner end"),
    PAST_LINER_FWD("Wear spot extends past the liner's FWD edge", "Extends past liner end"),
    PAST_SHAFT_FWD("Wear spot extends past the shaft's FWD end", "Extends past shaft end"),
}

/**
 * Classifies a wear spot's canonical liner-local band `[canonicalStartMm, canonicalStartMm +
 * lengthMm]` against its liner `[0, linerLengthMm]` and, when [oalMm] > 0, against the shaft's
 * FWD end (the band's shaft-space FWD edge is `linerStartFromAftMm + canonicalStartMm +
 * lengthMm`; `oalMm == 0` means the OAL is not typed yet and skips that check). Boundary-exact
 * bands are accepted (epsilon [WEAR_SPAN_EPS_MM], the app's bounds-check tolerance).
 *
 * DISPLAY-ONLY: the Start and Length fields commit a typed value verbatim whatever this says,
 * and the card states the problem (on-device ruling: no wear-spot field reverts a typed value).
 * The same classification covers a spot the liner was shortened under after it was recorded.
 * The drawing clamps the band to the liner ([clampWearBandToLiner]); the stored spot is never
 * rewritten.
 */
fun wearSpotSpanProblem(
    canonicalStartMm: Float,
    lengthMm: Float,
    linerLengthMm: Float,
    linerStartFromAftMm: Float = 0f,
    oalMm: Float = 0f,
): WearSpotSpanProblem? {
    val eps = WEAR_SPAN_EPS_MM
    val endMm = canonicalStartMm + lengthMm
    return when {
        lengthMm <= eps -> WearSpotSpanProblem.ZERO_LENGTH
        canonicalStartMm < -eps -> WearSpotSpanProblem.PAST_LINER_AFT
        endMm > linerLengthMm + eps -> WearSpotSpanProblem.PAST_LINER_FWD
        oalMm > 0f && linerStartFromAftMm + endMm > oalMm + eps -> WearSpotSpanProblem.PAST_SHAFT_FWD
        else -> null
    }
}

/** [wearSpotSpanProblem]'s full message, or `null` when the band fits. */
fun wearSpotSpanIssue(
    canonicalStartMm: Float,
    lengthMm: Float,
    linerLengthMm: Float,
    linerStartFromAftMm: Float = 0f,
    oalMm: Float = 0f,
): String? = wearSpotSpanProblem(canonicalStartMm, lengthMm, linerLengthMm, linerStartFromAftMm, oalMm)?.message

/**
 * Scale (px per mm) for the liner detail canvas. Fills the available width, but capped so a very
 * short liner doesn't blow the drawn diameter past the available height budget — otherwise a
 * 10mm liner would compute an enormous pxPerMm from the width term alone and render as a
 * screen-filling slab (proposal §6.1: "capped so very short liners don't explode").
 */
fun computeLinerDetailPxPerMm(
    usableWidthPx: Float,
    linerLengthMm: Float,
    maxOdMm: Float,
    usableHeightPx: Float,
    heightFillFraction: Float = 0.8f,
): Float {
    val widthDrivenPxPerMm = if (linerLengthMm > 0f) usableWidthPx / linerLengthMm else Float.MAX_VALUE
    val heightDrivenPxPerMm = if (maxOdMm > 0f) (usableHeightPx * heightFillFraction) / maxOdMm else Float.MAX_VALUE
    return min(widthDrivenPxPerMm, heightDrivenPxPerMm).coerceAtLeast(0.0001f)
}
