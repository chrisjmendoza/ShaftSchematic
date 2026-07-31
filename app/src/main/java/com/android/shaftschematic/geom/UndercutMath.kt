// file: app/src/main/java/com/android/shaftschematic/geom/UndercutMath.kt
package com.android.shaftschematic.geom

import com.android.shaftschematic.model.UndercutReference
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Undercut pure math — reference conversion, validation, cluster windows, hit-testing.
 *
 * Kept as small, dependency-free top-level functions (no Compose/Android imports, `geom`
 * package, no `pdf → ui` dependency) so the rules are directly unit-testable on a plain
 * JVM — the same posture as `geom/WearPitMath.kt` and `ui/screen/LinerWearMath.kt`.
 *
 * Undercuts are canonical **shaft space** (mm from the AFT face, x=0) and are NOT keyed
 * to components — see `model/Undercut.kt` and `docs/UndercutDrawing_PLAN.md`. The SET x
 * positions used by the conversion pair come from `geom/OalComputations.kt`'s
 * `computeSetPositionsInMeasureSpace` (its `measureStartMm` is always `0.0`, so its
 * measure-space output already *is* physical shaft-space mm).
 */

/** Epsilon for undercut bounds checks — same tolerance convention as `LinerWearMath`. */
private const val UNDERCUT_SPAN_EPS_MM = 1e-3f

/**
 * Undercuts whose clamped spans are separated by a gap of at most this many mm share one
 * zoomed detail window (the reference sketch's three weld undercuts read as one view).
 * Tunable product constant; keep it ≥ `2 × UNDERCUT_WINDOW_PAD_MM` or adjacent padded
 * windows could touch — [clusterUndercuts] defensively merges windows that do.
 */
const val UNDERCUT_CLUSTER_GAP_MM = 152.4f

/** Context padding added on each side of a cluster's span to form its zoom window. */
const val UNDERCUT_WINDOW_PAD_MM = 25.4f

/**
 * When an undercut has no entered Ø yet (`diaMm == 0` — placed but empty), the overlay
 * still needs to draw *some* notch so the section is visible and tappable. The renderer
 * substitutes a symbolic floor at this fraction of the smallest local surface Ø over the
 * span (via [effectiveNotchDiaMm]) — display-only, never stored, never printed.
 */
const val UNDERCUT_PLACEHOLDER_DEPTH_FRAC = 0.85f

// ── "Measure from" reference conversion ──

/**
 * Convert an entered "Distance" value, authored against [reference], into the canonical
 * shaft-space storage value ([com.android.shaftschematic.model.Undercut.startFromAftMm]).
 *
 * Convention (matches the [com.android.shaftschematic.model.WearSpotReference] pattern):
 * - [UndercutReference.AFT_SET] locates the undercut's **AFT edge**, measured FWD from
 *   the AFT SET: canonical = `aftSetXMm + enteredMm`.
 * - [UndercutReference.FWD_SET] locates the undercut's **FWD edge**, measured AFT from
 *   the FWD SET: canonical = `fwdSetXMm − enteredMm − lengthMm`.
 *
 * Pure and side-effect-free; does not clamp or validate — see [undercutSpanIssue] for
 * the blocking in-span check applied at commit time.
 */
fun undercutStartToCanonicalMm(
    reference: UndercutReference,
    enteredMm: Float,
    lengthMm: Float,
    aftSetXMm: Float,
    fwdSetXMm: Float,
): Float = when (reference) {
    UndercutReference.AFT_SET -> aftSetXMm + enteredMm
    UndercutReference.FWD_SET -> fwdSetXMm - enteredMm - lengthMm
}

/**
 * Inverse of [undercutStartToCanonicalMm]: project the canonical shaft-space start back
 * into the value that would have been entered under [reference], for display. Exact
 * algebraic inverse — round-trips to within float precision, never clamped.
 */
fun canonicalToUndercutStartMm(
    reference: UndercutReference,
    canonicalStartMm: Float,
    lengthMm: Float,
    aftSetXMm: Float,
    fwdSetXMm: Float,
): Float = when (reference) {
    UndercutReference.AFT_SET -> canonicalStartMm - aftSetXMm
    UndercutReference.FWD_SET -> fwdSetXMm - canonicalStartMm - lengthMm
}

// ── Validation ──

/**
 * Blocking entry validation: the span `[canonicalStartMm, canonicalStartMm + lengthMm]`
 * must lie entirely within the shaft extent `[0, oalMm]`, and the length must be
 * positive. Returns a short message describing the violation, or `null` when the span is
 * acceptable (boundary-exact spans accepted — epsilon [UNDERCUT_SPAN_EPS_MM]).
 *
 * Called at ENTRY (Distance field after conversion to canonical, and the Length field)
 * to reject an out-of-span commit before it ever reaches the model — `NumberField.md`'s
 * validator contract. Deliberately separate from [isUndercutStaleOverrun], which
 * classifies already-stored data for display rather than blocking new entry. The Ø field
 * is NOT validated against the local surface — a measurement is sacred; an implausible Ø
 * gets a non-blocking card warning instead (see `SurfaceProfileMath`).
 */
fun undercutSpanIssue(canonicalStartMm: Float, lengthMm: Float, oalMm: Float): String? {
    val eps = UNDERCUT_SPAN_EPS_MM
    return when {
        lengthMm <= eps -> "Length must be greater than zero"
        canonicalStartMm < -eps -> "Start is before the shaft's AFT end"
        canonicalStartMm > oalMm + eps -> "Start is past the shaft's FWD end"
        canonicalStartMm + lengthMm > oalMm + eps -> "Undercut extends past the shaft's FWD end"
        else -> null
    }
}

/**
 * True when a **previously recorded** undercut no longer fits within the shaft's current
 * extent (e.g. OAL was reduced after it was recorded). Non-blocking, display-only: the
 * caller still renders the clamped span ([clampUndercutSpan]) and shows a warning on the
 * undercut's card ("extends past shaft end — re-measure") rather than retroactively
 * rejecting stored data.
 */
fun isUndercutStaleOverrun(canonicalStartMm: Float, lengthMm: Float, oalMm: Float): Boolean =
    undercutSpanIssue(canonicalStartMm, lengthMm, oalMm) != null

/**
 * An undercut span clamped into the shaft extent `[0, oalMm]`. Rendering-only — the
 * stored [com.android.shaftschematic.model.Undercut] is never mutated by clamping.
 * [isEmpty] is true when the span lies entirely outside the shaft (skip drawing it).
 */
data class ClampedUndercutSpanMm(val startMm: Float, val endMm: Float) {
    val isEmpty: Boolean get() = endMm <= startMm
    val lengthMm: Float get() = (endMm - startMm).coerceAtLeast(0f)
}

/** Clamp an undercut's shaft-space span into `[0, oalMm]` for rendering. */
fun clampUndercutSpan(startFromAftMm: Float, lengthMm: Float, oalMm: Float): ClampedUndercutSpanMm {
    val hi = oalMm.coerceAtLeast(0f)
    val s = startFromAftMm.coerceIn(0f, hi)
    val e = (startFromAftMm + lengthMm).coerceIn(0f, hi)
    return ClampedUndercutSpanMm(s, e.coerceAtLeast(s))
}

// ── Cluster windows ──

/** An undercut's clamped axial span in shaft-space mm, the clustering/hit-test input. */
data class UndercutSpanMm(val id: String, val startMm: Float, val endMm: Float)

/**
 * A zoomed detail window covering one cluster of undercuts, in shaft-space mm.
 * [undercutIds] are the member undercuts in aft → fwd order. Windows returned by
 * [clusterUndercuts] are disjoint and sorted aft → fwd — consumed by the overview
 * affordances, the detail overlay, and the PDF strips, so all three agree by
 * construction.
 */
data class UndercutWindow(val startMm: Float, val endMm: Float, val undercutIds: List<String>) {
    val lengthMm: Float get() = endMm - startMm
}

/**
 * Group undercuts into zoomed detail windows: sort clamped spans aft → fwd, merge spans
 * whose gap is ≤ [gapMm] into one cluster, then pad each cluster by [padMm] per side,
 * clamped to `[0, oalMm]`. Padded windows that still touch or overlap (possible only if
 * the constants are retuned so `gapMm < 2 × padMm`) are defensively merged, so the
 * result is always disjoint. Empty spans (fully outside the shaft) are skipped; an empty
 * input yields an empty list.
 */
fun clusterUndercuts(
    spans: List<UndercutSpanMm>,
    oalMm: Float,
    gapMm: Float = UNDERCUT_CLUSTER_GAP_MM,
    padMm: Float = UNDERCUT_WINDOW_PAD_MM,
): List<UndercutWindow> {
    val live = spans.filter { it.endMm > it.startMm }.sortedBy { it.startMm }
    if (live.isEmpty()) return emptyList()

    // Merge raw spans into clusters by gap.
    data class Cluster(var startMm: Float, var endMm: Float, val ids: MutableList<String>)
    val clusters = mutableListOf<Cluster>()
    for (s in live) {
        val last = clusters.lastOrNull()
        if (last != null && s.startMm - last.endMm <= gapMm) {
            last.endMm = max(last.endMm, s.endMm)
            last.ids += s.id
        } else {
            clusters += Cluster(s.startMm, s.endMm, mutableListOf(s.id))
        }
    }

    // Pad, clamp, and defensively merge any windows the padding made touch.
    val hi = oalMm.coerceAtLeast(0f)
    val windows = mutableListOf<UndercutWindow>()
    for (c in clusters) {
        val w = UndercutWindow(
            startMm = (c.startMm - padMm).coerceIn(0f, hi),
            endMm = (c.endMm + padMm).coerceIn(0f, hi),
            undercutIds = c.ids.toList(),
        )
        val last = windows.lastOrNull()
        if (last != null && w.startMm <= last.endMm) {
            windows[windows.size - 1] = UndercutWindow(
                startMm = last.startMm,
                endMm = max(last.endMm, w.endMm),
                undercutIds = last.undercutIds + w.undercutIds,
            )
        } else {
            windows += w
        }
    }
    return windows
}

// ── Hit-testing ──

/**
 * Pick the window containing shaft-space [xMm], or `null`. Windows from
 * [clusterUndercuts] are disjoint, so at most one can contain the tap.
 */
fun pickUndercutWindowAt(xMm: Float, windows: List<UndercutWindow>): UndercutWindow? =
    windows.firstOrNull { xMm >= it.startMm && xMm <= it.endMm }

/**
 * Pick the id of the undercut whose span (inflated by [padMm] for an easy touch target)
 * contains [xMm], or `null` when the tap lands on none. A tap inside a span always beats
 * a tap merely within another span's pad; remaining ties break to the nearer span edge —
 * the `pickLinerIdAtMm` convention.
 */
fun pickUndercutAt(xMm: Float, spans: List<UndercutSpanMm>, padMm: Float): String? {
    val inside = spans.filter { xMm >= it.startMm && xMm <= it.endMm }
    val candidates = inside.ifEmpty {
        spans.filter { xMm >= it.startMm - padMm && xMm <= it.endMm + padMm }
    }
    if (candidates.isEmpty()) return null
    if (candidates.size == 1) return candidates[0].id
    return candidates.minByOrNull { s -> min(abs(xMm - s.startMm), abs(xMm - s.endMm)) }?.id
}

// ── Placeholder Ø ──

/**
 * The Ø a renderer should cut the notch to. A real entered Ø (`> 0`) is used verbatim;
 * a placed-but-empty undercut (`diaMm == 0`) gets a symbolic shallow floor at
 * [UNDERCUT_PLACEHOLDER_DEPTH_FRAC] of the smallest local surface Ø ([minSurfaceDiaMm],
 * from `minOuterDiaOver` in `SurfaceProfileMath`) so the section stays visible in the
 * overlay. Display-only — never stored, and a Ø-less undercut never prints a callout.
 */
fun effectiveNotchDiaMm(diaMm: Float, minSurfaceDiaMm: Float): Float =
    if (diaMm > 0f) diaMm else minSurfaceDiaMm * UNDERCUT_PLACEHOLDER_DEPTH_FRAC
