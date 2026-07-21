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
 * See `docs/LinerWearAreas_Proposal.md` §2 (tap hit-testing), §6.1 (detail-view scale + clamp),
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

// ── Wear-spot "Measure from" reference conversion (post-review spec, 2026-07-18) ──

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
 * Pure and side-effect-free; does not clamp or validate — see [wearSpotSpanIssue] for the
 * blocking in-span check applied at commit time.
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

/** Epsilon for wear-span in-bounds checks — matches the app's other bounds-check tolerance. */
private const val WEAR_SPAN_EPS_MM = 1e-3f

/**
 * Blocking in-span validation (post-review spec): the band
 * `[canonicalStartMm, canonicalStartMm + lengthMm]` must lie entirely within
 * `[0, linerLengthMm]`. Returns a short message describing the violation, or `null` when
 * the band is in-span (boundary-exact bands are accepted — epsilon [WEAR_SPAN_EPS_MM],
 * same tolerance convention as the app's other bounds checks, e.g. `CouplerBoltSlot.isValid`).
 *
 * Called at ENTRY (both the Start field, after converting to canonical via
 * [wearStartToCanonicalMm], and the Length field) to reject an out-of-span commit before it
 * ever reaches the model — see `NumberField.md`'s validator contract. This is deliberately
 * separate from [isWearSpotStaleOverrun], which classifies already-stored (possibly stale)
 * data for display rather than blocking new entry.
 */
fun wearSpotSpanIssue(canonicalStartMm: Float, lengthMm: Float, linerLengthMm: Float): String? {
    val eps = WEAR_SPAN_EPS_MM
    return when {
        canonicalStartMm < -eps -> "Start is before the liner's AFT edge"
        canonicalStartMm > linerLengthMm + eps -> "Start is past the liner's FWD edge"
        canonicalStartMm + lengthMm > linerLengthMm + eps -> "Band extends past the liner's FWD edge"
        else -> null
    }
}

/**
 * True when a **previously recorded** wear spot's canonical band no longer fits within the
 * liner's current length — e.g. the liner was shortened after the spot was recorded. This is
 * a non-blocking, display-only classifier: the caller should still render the band via the
 * existing [clampWearBandToLiner] safety net and show a small warning on the spot's card
 * ("extends past liner end — re-measure") rather than retroactively rejecting stored data
 * (only new entry is blocked, via [wearSpotSpanIssue]).
 */
fun isWearSpotStaleOverrun(canonicalStartMm: Float, lengthMm: Float, linerLengthMm: Float): Boolean =
    wearSpotSpanIssue(canonicalStartMm, lengthMm, linerLengthMm) != null

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
