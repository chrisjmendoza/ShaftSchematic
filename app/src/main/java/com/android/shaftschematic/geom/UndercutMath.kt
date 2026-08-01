// file: app/src/main/java/com/android/shaftschematic/geom/UndercutMath.kt
package com.android.shaftschematic.geom

import com.android.shaftschematic.model.Undercut
import com.android.shaftschematic.model.UndercutReference
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

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
 * - [UndercutReference.LINER_AFT] locates the undercut's **AFT edge**, measured FWD from
 *   the reference liner's AFT edge: canonical = `linerStartMm + enteredMm`.
 * - [UndercutReference.LINER_FWD] locates the undercut's **FWD edge**, measured AFT from
 *   the reference liner's FWD edge: canonical = `linerEndMm − enteredMm − lengthMm`.
 *
 * [linerStartMm]/[linerEndMm] are the reference liner's shaft-space edges (the liner
 * named by `Undercut.referenceLinerId`); they are read only by the `LINER_*` branches, so
 * SET-authored call sites may pass zeros. Callers offer the liner references only while
 * that liner exists — a deleted reference liner falls back to the AFT_SET projection.
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
    linerStartMm: Float = 0f,
    linerEndMm: Float = 0f,
): Float = when (reference) {
    UndercutReference.AFT_SET -> aftSetXMm + enteredMm
    UndercutReference.FWD_SET -> fwdSetXMm - enteredMm - lengthMm
    UndercutReference.LINER_AFT -> linerStartMm + enteredMm
    UndercutReference.LINER_FWD -> linerEndMm - enteredMm - lengthMm
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
    linerStartMm: Float = 0f,
    linerEndMm: Float = 0f,
): Float = when (reference) {
    UndercutReference.AFT_SET -> canonicalStartMm - aftSetXMm
    UndercutReference.FWD_SET -> fwdSetXMm - canonicalStartMm - lengthMm
    UndercutReference.LINER_AFT -> canonicalStartMm - linerStartMm
    UndercutReference.LINER_FWD -> linerEndMm - canonicalStartMm - lengthMm
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
 * Blocking draft-confirm validation: a cut's span may not intrude into another cut's
 * bounds — two overlapping undercuts are physically one cut and would double-dimension
 * the chain rail. Checked when CONFIRMING a drafted card (see `UndercutDetail`'s
 * draft/confirm flow), against the clamped spans of every OTHER cut on the sheet;
 * touching edge-to-edge is legal (epsilon [UNDERCUT_SPAN_EPS_MM]). Returns a short
 * message or `null`. Stored data is never retroactively rejected — like
 * [isUndercutStaleOverrun], anything already in the record keeps rendering.
 */
fun undercutOverlapIssue(
    canonicalStartMm: Float,
    lengthMm: Float,
    otherSpans: List<UndercutSpanMm>,
): String? {
    val eps = UNDERCUT_SPAN_EPS_MM
    val s = canonicalStartMm
    val e = canonicalStartMm + lengthMm
    val hit = otherSpans.firstOrNull { it.endMm > s + eps && it.startMm < e - eps }
    return if (hit != null) "Overlaps an adjacent undercut" else null
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

// ── Strips: liner-anchored vs free windows ──

/** A liner's shaft-space span, the liner-side input to strip grouping (geom-local so this
 *  file stays free of `ui`/`model` component imports beyond the reference enum). */
data class UndercutLinerSpan(val id: String, val startMm: Float, val endMm: Float)

/**
 * One zoomed detail strip. The drawing pipeline (profile, notches, break/flat ends,
 * Ø callouts) treats every strip as the draw range `[drawStartMm, drawEndMm]`; the two
 * kinds differ in what anchors that range and where the dimension chain runs
 * (on-device report: a grey slab with no liner edges was unreadable — a cut inside a
 * liner must show the whole liner, wear-style).
 *
 * - [LinerStrip]: the cuts live in a liner. The draw range covers the **whole liner**
 *   (plus any cut overhang past its edges) padded each side, so the liner's true edges
 *   are always visible and a neighbor sliver shows before the break edge. The chain rail
 *   anchors at the **liner edges** (extended only by overhang), like the wear rail.
 * - [FreeStrip]: bare-shaft cuts, no liner involved — the padded cluster window, chain
 *   anchored at the window edges (the original strip behavior).
 */
sealed class UndercutStrip {
    abstract val drawStartMm: Float
    abstract val drawEndMm: Float
    /** Where the chained dimension rail starts/ends (witness datums, chain coverage). */
    abstract val chainStartMm: Float
    abstract val chainEndMm: Float
    abstract val undercutIds: List<String>

    data class LinerStrip(
        val linerId: String,
        val linerStartMm: Float,
        val linerEndMm: Float,
        override val drawStartMm: Float,
        override val drawEndMm: Float,
        override val chainStartMm: Float,
        override val chainEndMm: Float,
        override val undercutIds: List<String>,
    ) : UndercutStrip()

    data class FreeStrip(val window: UndercutWindow) : UndercutStrip() {
        override val drawStartMm: Float get() = window.startMm
        override val drawEndMm: Float get() = window.endMm
        override val chainStartMm: Float get() = window.startMm
        override val chainEndMm: Float get() = window.endMm
        override val undercutIds: List<String> get() = window.undercutIds
    }
}

/**
 * The liner a cut belongs to for strip purposes: the one overlapping the largest share
 * of the cut's span (`null` when no liner overlaps at all). A cut crossing a liner edge
 * belongs to the liner holding most of it; an exact tie breaks to the AFT-most liner.
 */
fun assignUndercutLiner(span: UndercutSpanMm, liners: List<UndercutLinerSpan>): String? =
    liners
        .map { l -> l to (min(span.endMm, l.endMm) - max(span.startMm, l.startMm)) }
        .filter { (_, overlap) -> overlap > 0f }
        .maxWithOrNull(compareBy({ it.second }, { -it.first.startMm }))
        ?.first?.id

/**
 * Build one [UndercutStrip.LinerStrip] for [liner] and the cuts assigned to it (may be
 * empty — the overlay uses this to zoom an undercut-free liner for authoring). The draw
 * range is the liner's full span, expanded by any cut overhang past its edges, then
 * padded by [padMm] each side and clamped to `[0, oalMm]`; the chain range is the liner
 * span expanded by overhang only (no pad), so the rail's outer witness lines sit on real
 * datums — liner edges or cut shoulders, never the arbitrary pad edge.
 */
fun linerStripFor(
    liner: UndercutLinerSpan,
    assignedSpans: List<UndercutSpanMm>,
    oalMm: Float,
    padMm: Float = UNDERCUT_WINDOW_PAD_MM,
): UndercutStrip.LinerStrip {
    val hi = oalMm.coerceAtLeast(0f)
    val cutsMin = assignedSpans.minOfOrNull { it.startMm } ?: liner.startMm
    val cutsMax = assignedSpans.maxOfOrNull { it.endMm } ?: liner.endMm
    val chainStart = min(liner.startMm, cutsMin)
    val chainEnd = max(liner.endMm, cutsMax)
    return UndercutStrip.LinerStrip(
        linerId = liner.id,
        linerStartMm = liner.startMm,
        linerEndMm = liner.endMm,
        drawStartMm = (chainStart - padMm).coerceIn(0f, hi),
        drawEndMm = (chainEnd + padMm).coerceIn(0f, hi),
        chainStartMm = chainStart.coerceIn(0f, hi),
        chainEndMm = chainEnd.coerceIn(0f, hi),
        undercutIds = assignedSpans.sortedBy { it.startMm }.map { it.id },
    )
}

/**
 * Group cuts into detail strips: every cut overlapping a liner joins that liner's
 * [UndercutStrip.LinerStrip] (one strip per liner with ≥1 cut, covering the whole liner);
 * the remaining bare-shaft cuts cluster into [UndercutStrip.FreeStrip] windows via
 * [clusterUndercuts]. Result is sorted aft → fwd by draw start. Consumed by the overview
 * affordances, the detail overlay, and the PDF strips, so all three agree by
 * construction. Empty spans are skipped, as in [clusterUndercuts].
 */
fun buildUndercutStrips(
    spans: List<UndercutSpanMm>,
    liners: List<UndercutLinerSpan>,
    oalMm: Float,
    gapMm: Float = UNDERCUT_CLUSTER_GAP_MM,
    padMm: Float = UNDERCUT_WINDOW_PAD_MM,
): List<UndercutStrip> {
    val live = spans.filter { it.endMm > it.startMm }
    val byLiner = live.groupBy { assignUndercutLiner(it, liners) }
    val strips = mutableListOf<UndercutStrip>()
    for (liner in liners) {
        val assigned = byLiner[liner.id] ?: continue
        strips += linerStripFor(liner, assigned, oalMm, padMm)
    }
    for (window in clusterUndercuts(byLiner[null].orEmpty(), oalMm, gapMm, padMm)) {
        strips += UndercutStrip.FreeStrip(window)
    }
    return strips.sortedBy { it.drawStartMm }
}

/**
 * Pick the strip containing shaft-space [xMm], or `null`. Unlike free windows, liner
 * strips can overlap a neighboring strip's pad; the first hit in aft → fwd order wins —
 * ties are visually indistinguishable at pad scale.
 */
fun pickUndercutStripAt(xMm: Float, strips: List<UndercutStrip>): UndercutStrip? =
    strips.firstOrNull { xMm >= it.drawStartMm && xMm <= it.drawEndMm }

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

// ── Drawn depth exaggeration (per-sheet, normalized to the deepest cut) ──

/**
 * Slider cap for [com.android.shaftschematic.model.UndercutRecord.exaggerationFrac]: the
 * sheet's deepest cut never draws deeper than this fraction of its local surface Ø
 * (product decision — shafts run up to ~10" Ø, where real cuts of 1/16"–1/2" are
 * hairlines at true scale; the hand-drawn sheets exaggerate depth heavily while the
 * printed Ø carries the real number).
 */
const val UNDERCUT_EXAGGERATION_MAX_FRAC = 0.25f

/** A placed-but-unmeasured cut (Ø 0) draws at this share of the sheet's exaggeration. */
const val UNDERCUT_PLACEHOLDER_OF_EXAGGERATION = 0.5f

/**
 * Every measured cut draws at no less than this share of the sheet's exaggeration.
 * Without a floor, a shallow cut normalized against a much deeper one elsewhere on the
 * shaft all but vanished (on-device report: two ~0.005"-deep cuts in one liner drew as
 * hairlines because a 0.05" cut in another liner owned the reference). Combined with the
 * square-root ratio compression, the deepest cut still owns the slider depth and deeper
 * always draws deeper, but nothing readable is ever lost.
 */
const val UNDERCUT_MIN_SHARE_OF_EXAGGERATION = 0.25f

/** Visibility floor for placeholder cuts so they stay findable even at 0% exaggeration. */
const val UNDERCUT_PLACEHOLDER_MIN_DRAWN_FRAC = 0.04f

/**
 * Depth of the sheet's deepest **measured** cut, in Ø-reduction mm — the normalization
 * reference for [normalizedNotchFloorDiaMm]. Placeholder cuts (Ø 0) and cuts whose Ø
 * meets/exceeds their local surface (warning case, no material removed) contribute
 * nothing. One shared implementation so every draw site normalizes identically.
 */
fun deepestUndercutDepthMm(
    undercuts: List<Undercut>,
    segs: List<SurfaceSeg>,
    oalMm: Float,
): Float = undercuts.maxOfOrNull { u ->
    if (u.diaMm <= 0f) 0f else {
        val c = clampUndercutSpan(u.startFromAftMm, u.lengthMm, oalMm)
        if (c.isEmpty) 0f
        else (minOuterDiaOver(segs, c.startMm, c.endMm) - u.diaMm).coerceAtLeast(0f)
    }
} ?: 0f

/**
 * Display-only drawn floor Ø for one cut, normalized to the sheet's deepest cut:
 * `drawnDepth = minSurfaceDiaMm × exaggerationFrac × max(√(trueDepth / deepestDepthMm),
 * `[UNDERCUT_MIN_SHARE_OF_EXAGGERATION]`)` — the deepest cut draws at the sheet's chosen
 * exaggeration and shallower cuts scale relative to IT, so a sheet whose worst cut is 1"
 * deep and one whose worst is 1/4" read alike (on-device request). The ratio is
 * **square-root compressed** and floored at a minimum share: with a linear ratio, a
 * shallow cut normalized against a much deeper one elsewhere on the shaft drew as a
 * hairline (on-device report) — √ keeps deeper-draws-deeper ordering while shrinking the
 * dynamic range, and the floor guarantees every measured cut stays readable.
 *
 * Rules:
 * - `exaggerationFrac` clamps to `0..`[UNDERCUT_EXAGGERATION_MAX_FRAC]; `0` = true scale.
 * - Never shallower than reality: `drawnDepth ≥ trueDepth` for a measured cut.
 * - Placeholder cuts (Ø 0) draw at [UNDERCUT_PLACEHOLDER_OF_EXAGGERATION] of the sheet's
 *   exaggeration (never below [UNDERCUT_PLACEHOLDER_MIN_DRAWN_FRAC]) and are excluded
 *   from [deepestUndercutDepthMm], so an unmeasured cut can't squash the real ones.
 *
 * Applied by every notch draw site AFTER `notchProfiles` computes the drawable regions
 * from the TRUE floor — region topology stays truthful (a cut that never touched the
 * neighboring stock must not draw into it); only the floor line and shoulders deepen.
 * Ø callouts keep printing the stored value; golden rule untouched.
 */
fun normalizedNotchFloorDiaMm(
    diaMm: Float,
    minSurfaceDiaMm: Float,
    deepestDepthMm: Float,
    exaggerationFrac: Float,
): Float {
    if (minSurfaceDiaMm <= 0f) return diaMm
    val ex = exaggerationFrac.coerceIn(0f, UNDERCUT_EXAGGERATION_MAX_FRAC)
    val drawnDepth = if (diaMm <= 0f) {
        minSurfaceDiaMm * max(ex * UNDERCUT_PLACEHOLDER_OF_EXAGGERATION, UNDERCUT_PLACEHOLDER_MIN_DRAWN_FRAC)
    } else {
        val trueDepth = (minSurfaceDiaMm - diaMm).coerceAtLeast(0f)
        val ratio = if (deepestDepthMm > 0f) (trueDepth / deepestDepthMm).coerceIn(0f, 1f) else 1f
        val share = if (trueDepth > 0f) max(sqrt(ratio), UNDERCUT_MIN_SHARE_OF_EXAGGERATION) else 0f
        max(trueDepth, minSurfaceDiaMm * ex * share)
    }
    return minSurfaceDiaMm - drawnDepth
}
