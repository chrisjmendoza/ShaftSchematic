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
 * to components — see `model/Undercut.kt` and `docs/archive/UndercutDrawing_PLAN.md`. The SET x
 * positions used by the conversion pair come from `geom/OalComputations.kt`'s
 * `computeSetPositionsInMeasureSpace` (its `measureStartMm` is always `0.0`, so its
 * measure-space output already *is* physical shaft-space mm).
 */

/** Epsilon for undercut bounds checks — same tolerance convention as `LinerWearMath`. */
internal const val UNDERCUT_SPAN_EPS_MM = 1e-3f

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

/**
 * Canonical start after a LENGTH edit that must keep the authored Distance fixed: project
 * the current canonical to the displayed distance under [reference] at the OLD length,
 * then back to canonical at the NEW length.
 *
 * Under an AFT-flavored reference this is the identity — the distance doesn't involve the
 * length. Under a FWD-flavored one it pins the cut's FWD end (the datum the distance was
 * authored against) and grows/shrinks the cut AFT-ward. Committing a new length while
 * keeping the old canonical would instead slide the FWD end and rewrite the displayed
 * Distance by the length delta — a golden-rule violation (on-device report: Distance 5 /
 * Length 12 under a liner-FWD reference became Distance 7 after shortening Length to 10).
 * The "canonical never moves" rule covers reference *switching* (display re-projection),
 * not length edits.
 */
fun undercutCanonicalForNewLength(
    reference: UndercutReference,
    canonicalStartMm: Float,
    oldLengthMm: Float,
    newLengthMm: Float,
    aftSetXMm: Float,
    fwdSetXMm: Float,
    linerStartMm: Float = 0f,
    linerEndMm: Float = 0f,
): Float {
    val distanceMm = canonicalToUndercutStartMm(
        reference, canonicalStartMm, oldLengthMm, aftSetXMm, fwdSetXMm, linerStartMm, linerEndMm,
    )
    return undercutStartToCanonicalMm(
        reference, distanceMm, newLengthMm, aftSetXMm, fwdSetXMm, linerStartMm, linerEndMm,
    )
}

/**
 * The S.E.T. a **bare-shaft** (body-only) cut authors its Distance against by default:
 * the nearer one — [UndercutReference.AFT_SET] when the span's midpoint sits in the AFT
 * half of the SET-to-SET window, [UndercutReference.FWD_SET] otherwise. A body cut has no
 * liner edge to measure from, so the SETs are its only datums; picking by proximity is the
 * same rule the printed sheet's `undercutAnchorFor` uses to anchor a bare-shaft strip's
 * title (which delegates here), so the card's default Distance reads from the SET the
 * sheet will anchor to. Liner cuts don't use this — they author against the liner edge
 * the machinist is standing at (LINER_AFT).
 */
fun nearestSetReference(
    startMm: Float,
    endMm: Float,
    aftSetXMm: Float,
    fwdSetXMm: Float,
): UndercutReference =
    if ((startMm + endMm) * 0.5f <= (aftSetXMm + fwdSetXMm) * 0.5f) UndercutReference.AFT_SET
    else UndercutReference.FWD_SET

// ── Validation ──

/**
 * Blocking entry validation: the span `[canonicalStartMm, canonicalStartMm + lengthMm]`
 * must lie entirely within the shaft extent `[0, oalMm]`, and the length must be
 * positive. Returns a short message describing the violation, or `null` when the span is
 * acceptable (boundary-exact spans accepted — epsilon [UNDERCUT_SPAN_EPS_MM]).
 *
 * Called at ENTRY (Distance field after conversion to canonical, and the Length field)
 * to reject an out-of-span commit before it ever reaches the model — `docs/contracts/NumberField.md`'s
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
 * Blocking draft-confirm validation: a cut's span must be either fully CLEAR of every other
 * cut (disjoint, touching edge-to-edge included) or fully NESTED with it — inside another cut,
 * or containing one ([undercutSpanContains]). A **partial** overlap is physically one cut and
 * would double-dimension the chain rail, so it stays blocked; so does a span identical to
 * another, which is one cut entered twice rather than a cut inside a cut.
 *
 * Nested cuts are legal — a machinist cuts a wide relief and then deepens a corroded section
 * of it, which may run right up to the relief's own shoulder — and draw as a staircase
 * (`geom/UndercutOverlayMath.kt`'s `buildUndercutNotches`).
 *
 * Checked when CONFIRMING a drafted card (see `UndercutDetail`'s draft/confirm flow), against
 * the clamped spans of every OTHER cut on the sheet. Returns a short message or `null`. Stored
 * data is never retroactively rejected — like [isUndercutStaleOverrun], anything already in the
 * record keeps rendering, partial overlaps from older records included.
 */
fun undercutOverlapIssue(
    canonicalStartMm: Float,
    lengthMm: Float,
    otherSpans: List<UndercutSpanMm>,
): String? {
    val eps = UNDERCUT_SPAN_EPS_MM
    val draft = UndercutSpanMm("", canonicalStartMm, canonicalStartMm + lengthMm)
    val hit = otherSpans.firstOrNull { o ->
        val clear = o.endMm <= draft.startMm + eps || o.startMm >= draft.endMm - eps
        !clear && !undercutSpanContains(draft, o) && !undercutSpanContains(o, draft)
    }
    return if (hit != null) UNDERCUT_PARTIAL_OVERLAP_MSG else null
}

/**
 * The one blocked-overlap wording: a partial intrusion and a duplicate span read the same,
 * because the fix is the same — move the cut fully inside its neighbour or fully clear of it.
 */
const val UNDERCUT_PARTIAL_OVERLAP_MSG =
    "Overlaps an adjacent undercut — a cut must sit fully inside or fully clear of another"

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

// ── Containment forest (nested cuts) ──

/**
 * One span's place in the containment forest ([undercutNestingForest]): [parentId] is the
 * smallest cut strictly containing it (`null` at the top level) and [level] counts the
 * containments above it — 0 = cut into the shaft's own surface, 1 = cut into a level-0 cut's
 * floor, and so on.
 */
data class UndercutNesting(val id: String, val level: Int, val parentId: String?)

/**
 * True when [outer] contains [inner] — the inner span inside both outer edges within [epsMm],
 * and the two not the SAME span (both edges coincident within [epsMm]).
 *
 * A shared edge IS containment: the shop machines the original relief and then deepens a
 * corroded section of it that may run right up to the relief's own shoulder (on-device
 * intent), and that must print exactly as separately-authored adjacent sections would — one
 * face running from the surface down to the deeper floor. Two spans that coincide are not
 * nesting at all but one cut entered twice, so they stay blocked ([undercutOverlapIssue]).
 */
fun undercutSpanContains(
    outer: UndercutSpanMm,
    inner: UndercutSpanMm,
    epsMm: Float = UNDERCUT_SPAN_EPS_MM,
): Boolean {
    val within = inner.startMm >= outer.startMm - epsMm && inner.endMm <= outer.endMm + epsMm
    val sameSpan = abs(inner.startMm - outer.startMm) <= epsMm &&
        abs(inner.endMm - outer.endMm) <= epsMm
    return within && !sameSpan
}

/**
 * The containment forest over [spans], in input order: each span's nesting level and the id of
 * the smallest span strictly containing it. A machinist cuts a wide relief section and then a
 * smaller, deeper undercut inside it; the forest is what lets the drawn floors stack
 * ([nestedNotchFloorDiaMm]), the notch build cut a child against its parent's floor, and the
 * rail give each level its own chain row.
 *
 * PARTIAL overlaps are TOLERATED, not repaired: neither span contains the other, so both stay
 * at the level their own containment gives them and they render exactly as they always have.
 * Only new entry is gated ([undercutOverlapIssue]) — stored data is never retroactively
 * rejected, so an older record keeps drawing.
 *
 * Ties break to the FIRST in input order (two equal-width spans both containing one cut, which
 * spans sharing edges can produce), so the forest is deterministic for a given record order.
 * Containment stays antisymmetric — mutual containment would mean the same span within epsilon,
 * which [undercutSpanContains] excludes — so the parent chains are always finite.
 */
fun undercutNestingForest(spans: List<UndercutSpanMm>): List<UndercutNesting> {
    val n = spans.size
    if (n == 0) return emptyList()
    fun width(i: Int) = spans[i].endMm - spans[i].startMm

    val parent = IntArray(n) { -1 }
    for (i in 0 until n) {
        var best = -1
        for (j in 0 until n) {
            if (j == i || !undercutSpanContains(spans[j], spans[i])) continue
            if (best < 0 || width(j) < width(best)) best = j
        }
        parent[i] = best
    }

    val level = IntArray(n) { -1 }
    fun levelOf(i: Int, guard: Int): Int {
        if (level[i] >= 0) return level[i]
        val p = parent[i]
        val v = if (p < 0 || guard <= 0) 0 else levelOf(p, guard - 1) + 1
        level[i] = v
        return v
    }
    return spans.indices.map { i ->
        UndercutNesting(
            id = spans[i].id,
            level = levelOf(i, n),
            parentId = parent[i].takeIf { it >= 0 }?.let { spans[it].id },
        )
    }
}

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
 * a tap merely within another span's pad.
 *
 * Several spans can contain the tap — a nested cut sits entirely inside its parent — and the
 * **innermost** (narrowest) one wins: the child is the smaller target and the one drawn on
 * top, so a tap on it must never select the relief around it. Equal widths, and every pad-only
 * hit, break to the nearer span edge — the `pickLinerIdAtMm` convention.
 */
fun pickUndercutAt(xMm: Float, spans: List<UndercutSpanMm>, padMm: Float): String? {
    val inside = spans.filter { xMm >= it.startMm && xMm <= it.endMm }
    if (inside.isNotEmpty()) {
        if (inside.size == 1) return inside[0].id
        return inside.minWithOrNull(
            compareBy(
                { it.endMm - it.startMm },
                { min(abs(xMm - it.startMm), abs(xMm - it.endMm)) },
            ),
        )?.id
    }
    val candidates = spans.filter { xMm >= it.startMm - padMm && xMm <= it.endMm + padMm }
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
 * The draw range a detail overlay should render given the spans it is PREVIEWING — the
 * stored cuts with any live draft substituted: the strip's own range, **widened, never
 * narrowed**, so a cut edited past the strip's stored range (an overhang past a liner
 * edge, mid-edit) stays inside the drawing with the standard [padMm] of neighbour stock
 * beyond it — the same range a confirmed overhang gets when [linerStripFor] /
 * [clusterUndercuts] rebuild the strip on commit. Never narrowing keeps the window stable
 * while a draft shrinks a cut that had extended it; the rebuild on confirm re-tightens.
 * Clamped to `[0, oalMm]`. Spans must already be render-clamped ([clampUndercutSpan]).
 */
fun undercutPreviewDrawRange(
    strip: UndercutStrip,
    previewSpans: List<UndercutSpanMm>,
    oalMm: Float,
    padMm: Float = UNDERCUT_WINDOW_PAD_MM,
): Pair<Float, Float> {
    val hi = oalMm.coerceAtLeast(0f)
    val spansMin = previewSpans.minOfOrNull { it.startMm }
        ?: return strip.drawStartMm to strip.drawEndMm
    val spansMax = previewSpans.maxOf { it.endMm }
    return min(strip.drawStartMm, spansMin - padMm).coerceIn(0f, hi) to
        max(strip.drawEndMm, spansMax + padMm).coerceIn(0f, hi)
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
 * Depth of the sheet's deepest **measured, TOP-LEVEL** cut, in Ø-reduction mm — the
 * normalization reference for [normalizedNotchFloorDiaMm]. Placeholder cuts (Ø 0) and cuts
 * whose Ø meets/exceeds their local surface (warning case, no material removed) contribute
 * nothing. One shared implementation so every draw site normalizes identically.
 *
 * NESTED cuts are excluded: a child's depth is relative to its parent's floor
 * ([nestedNotchFloorDiaMm]), so measuring it from the base surface would hand the sheet a
 * reference no cut is drawn against and squash every top-level cut toward the minimum share.
 */
fun deepestUndercutDepthMm(
    undercuts: List<Undercut>,
    segs: List<SurfaceSeg>,
    oalMm: Float,
): Float {
    val clampedById = undercuts.associate { u ->
        u.id to clampUndercutSpan(u.startFromAftMm, u.lengthMm, oalMm)
    }
    val spans = undercuts.mapNotNull { u ->
        val c = clampedById[u.id] ?: return@mapNotNull null
        if (c.isEmpty) null else UndercutSpanMm(u.id, c.startMm, c.endMm)
    }
    val topLevelIds = undercutNestingForest(spans)
        .filter { it.level == 0 }
        .mapTo(HashSet()) { it.id }
    return undercuts.maxOfOrNull { u ->
        val c = clampedById[u.id]
        if (u.diaMm <= 0f || c == null || c.isEmpty || u.id !in topLevelIds) 0f
        else (minOuterDiaOver(segs, c.startMm, c.endMm) - u.diaMm).coerceAtLeast(0f)
    } ?: 0f
}

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

/**
 * Deepest a NESTED cut's drawn floor may sit below its parent's DRAWN floor, as a share of
 * that floor's Ø — so a staircase always keeps a visible core inside the innermost step. The
 * true relative depth overrides it (truth beats prettiness); nothing else does.
 */
const val UNDERCUT_NESTED_MAX_DEPTH_FRAC = 0.75f

/**
 * Display-only drawn floor Ø for a cut machined INSIDE another cut: the child's exaggerated
 * depth is computed **relative to its parent's floor** ([normalizedNotchFloorDiaMm] with the
 * parent's TRUE floor standing in as the local surface, against the sheet's top-level
 * normalization pool) and then subtracted from the parent's DRAWN floor. Recursive by
 * construction — at level ≥ 2 the "parent" values are the level-above results.
 *
 * Two invariants this construction guarantees:
 * - **The stair is always visible.** A child that is truly deeper than its parent draws a
 *   strictly smaller floor Ø than the parent's, at every slider value: the relative depth is
 *   measured against the parent floor, so [UNDERCUT_MIN_SHARE_OF_EXAGGERATION] can no longer
 *   flatten a shallow-from-the-base pair into one step.
 * - **Never shallower than true.** `childDrawn = parentDrawn − relDrawn ≤ parentTrue − relTrue
 *   = childTrue`, since the parent's drawn floor is never above its true floor and the
 *   relative drawn depth is never below the relative true depth.
 *
 * [childDiaMm] is the stored Ø, `0` for a placed-but-unmeasured cut (which takes the symbolic
 * [UNDERCUT_PLACEHOLDER_DEPTH_FRAC] fraction of the PARENT's floor, [effectiveNotchDiaMm]'s
 * rule applied one level in). The drawn depth is capped at [UNDERCUT_NESTED_MAX_DEPTH_FRAC] of
 * the parent's drawn floor unless the true relative depth demands deeper, and the result is
 * floored above zero so a step always has a floor line to draw. Stored/printed Ø values are
 * untouched — golden rule.
 */
fun nestedNotchFloorDiaMm(
    childDiaMm: Float,
    parentTrueFloorDiaMm: Float,
    parentDrawnFloorDiaMm: Float,
    deepestDepthMm: Float,
    exaggerationFrac: Float,
): Float {
    if (parentTrueFloorDiaMm <= 0f || parentDrawnFloorDiaMm <= 0f) return childDiaMm
    val relDrawnDepth = parentTrueFloorDiaMm - normalizedNotchFloorDiaMm(
        diaMm = childDiaMm,
        minSurfaceDiaMm = parentTrueFloorDiaMm,
        deepestDepthMm = deepestDepthMm,
        exaggerationFrac = exaggerationFrac,
    )
    val trueRelDepth =
        (parentTrueFloorDiaMm - effectiveNotchDiaMm(childDiaMm, parentTrueFloorDiaMm))
            .coerceAtLeast(0f)
    val cap = max(parentDrawnFloorDiaMm * UNDERCUT_NESTED_MAX_DEPTH_FRAC, trueRelDepth)
    val drawnDepth = min(relDrawnDepth.coerceAtLeast(0f), cap)
    return (parentDrawnFloorDiaMm - drawnDepth).coerceAtLeast(UNDERCUT_SPAN_EPS_MM)
}
