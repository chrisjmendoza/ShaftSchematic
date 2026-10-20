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
 * Slop for "do two edges TOUCH" — the clear test of the partial-overlap predicate and the
 * whole-liner match. A preset computes its edge in `Float` (`other.start − L`, then `start + L`;
 * `end − start`, then `start + L`), and at 8–16 m of shaft one ulp is about 1e-3 mm — as large as
 * [UNDERCUT_SPAN_EPS_MM] — so a butted cut could read as overlapping its neighbour and the
 * whole-liner chip as unselected. 0.01 mm is 0.0004": no real overlap is that thin.
 */
const val UNDERCUT_TOUCH_EPS_MM = 0.01f

/**
 * Undercuts whose clamped spans are separated by a gap of at most this many mm share one
 * zoomed detail window (the reference sketch's three weld undercuts read as one view).
 * Tunable product constant; keep it ≥ `2 × UNDERCUT_WINDOW_PAD_MM` or adjacent padded
 * windows could touch — [clusterUndercuts] defensively merges windows that do.
 */
const val UNDERCUT_CLUSTER_GAP_MM = 152.4f

/**
 * Context padding added on each side of a cluster's span to form its zoom window — the
 * MINIMUM pad. A window end that is not the shaft's physical end draws an S-break whose lobes
 * scale with the stock's diameter, so on a wide shaft this pad cannot host them; the strip
 * builders then widen the draw range by [withUndercutBreakRoom], and the break room is what a
 * wide shaft actually gets.
 */
const val UNDERCUT_WINDOW_PAD_MM = 25.4f

/**
 * "Draw to the shaft end" reach (`UndercutRecord.drawToSet`, 24 in): a liner strip whose chain
 * ends within this many mm of an S.E.T. extends its chain to that S.E.T. and its draw range to
 * the shaft's physical end beyond it, so the chain dimensions the run from the last cut to the
 * S.E.T. (on-device request). The knob: one value behind the route, the detail overlay, the PDF
 * strips and the Undercut tab's caption.
 */
const val UNDERCUT_SET_REACH_MM = 609.6f

/** The strip builders' `reachToSetMm` for a record's `drawToSet` flag — [UNDERCUT_SET_REACH_MM] or `0` (off). */
fun undercutSetReachMm(drawToSet: Boolean): Float = if (drawToSet) UNDERCUT_SET_REACH_MM else 0f

/**
 * Clearance past the window-end break glyph's reach, as a fraction of its amplitude, so the lobe
 * never kisses the next component face.
 */
const val UNDERCUT_BREAK_CLEAR_FRAC_OF_AMP = 0.25f

/**
 * Uniform stock a window-end break needs on its INTERIOR side — from the break line to the next
 * face — for stock of outer Ø [diaMm]. The amplitude in shaft mm is
 * `(dia/2)·`[UNDERCUT_BREAK_AMP_FRAC_OF_R] because the detail canvas maps x and r through one
 * scale; the PDF caps its amplitude lower (`UNDERCUT_BREAK_AMP_MAX_PT`), so this room is
 * sufficient on both surfaces. The reach is the glyph's widest excursion to either side of its
 * line ([BREAK_EDGE_OUTWARD_REACH_FRAC]) plus [UNDERCUT_BREAK_CLEAR_FRAC_OF_AMP] of daylight.
 */
fun undercutBreakRoomMm(diaMm: Float): Float =
    diaMm * 0.5f * UNDERCUT_BREAK_AMP_FRAC_OF_R *
        (BREAK_EDGE_OUTWARD_REACH_FRAC + UNDERCUT_BREAK_CLEAR_FRAC_OF_AMP)

/**
 * Widen the draw range `[startMm, endMm]` OUTWARD — never inward — until each end that is not
 * the shaft's physical end sits in stock with [undercutBreakRoomMm] of that stock between its
 * break line and the next face on the window's interior side (on-device report: a liner strip's
 * 1" pad landed a hair inside a taper, and the window-end S-break tangled with the taper face,
 * a 1/4" body and the liner face).
 *
 * Works on the outer-surface envelope of [segs] ([buildSurfaceEnvelope]), so a body running
 * under a liner never hides the liner's face. A piece too short to host the break is walked
 * over to the next piece outward — a blend's sampled sub-pieces included, so a break never sits
 * inside a ramp. An end that reaches `0` / [oalMm] becomes that exact value: the flat physical
 * end. A piece's room is sized off its LARGER end Ø (conservative on a taper). An end over
 * uncovered x (no seg) stays put. Empty [segs] returns the input unchanged. Idempotent.
 *
 * Chain datums are never passed here: only the drawn context outside them grows.
 *
 * [cutSpans] are the STRIP's own cuts (its members, chained cuts included — never another strip's,
 * which the strip draws as plain stock): each cut's shoulders are faces in the same walk, and the
 * stretch a cut spans never hosts a break (on-device request: cuts run past a liner into the body
 * crashed into the window-end S-break). An end landing inside a cut, or within the room of a
 * cut's shoulder on the interior side, therefore moves outward past it. Empty [cutSpans] walks the
 * component faces alone.
 */
fun withUndercutBreakRoom(
    startMm: Float,
    endMm: Float,
    segs: List<SurfaceSeg>,
    oalMm: Float,
    cutSpans: List<UndercutSpanMm> = emptyList(),
): Pair<Float, Float> {
    if (segs.isEmpty()) return startMm to endMm
    val eps = UNDERCUT_SPAN_EPS_MM
    val hi = oalMm.coerceAtLeast(0f)
    val lo = segs.minOf { it.startMm }
    val top = segs.maxOf { it.endMm }
    val envelope = buildSurfaceEnvelope(segs, lo, top)
    if (envelope.isEmpty()) return startMm to endMm
    val cuts = cutSpans.filter { it.endMm > it.startMm }
    val pieces = splitEnvelopeAtCutShoulders(envelope, cuts, eps)
    fun inCut(p: EnvelopePiece): Boolean {
        val mid = (p.x0Mm + p.x1Mm) * 0.5f
        return cuts.any { mid > it.startMm && mid < it.endMm }
    }
    val cutPiece = BooleanArray(pieces.size) { inCut(pieces[it]) }
    fun room(p: EnvelopePiece) = undercutBreakRoomMm(max(p.dia0Mm, p.dia1Mm))
    fun contiguous(a: EnvelopePiece, b: EnvelopePiece) = abs(a.x1Mm - b.x0Mm) <= eps

    // AFT end: interior is +x, the face ahead of the break is the piece's x1.
    var start = startMm
    if (start <= eps) {
        start = min(0f, startMm)
    } else {
        // Half-open: a start on a face belongs to the piece beginning there.
        var i = pieces.indexOfFirst { start >= it.x0Mm - eps && start < it.x1Mm - eps }
        var guard = pieces.size + 1
        while (i >= 0 && guard-- > 0) {
            val p = pieces[i]
            val r = room(p)
            if (!cutPiece[i] && p.x1Mm - start >= r - eps) break
            val candidate = p.x1Mm - r
            if (!cutPiece[i] && candidate >= p.x0Mm - eps) {
                start = min(start, candidate)
                break
            }
            // Too short to host the break: walk over it to the piece ending at its AFT face.
            start = min(start, p.x0Mm)
            i = if (i > 0 && contiguous(pieces[i - 1], p)) i - 1 else -1
        }
        if (start <= eps) start = min(0f, startMm)
    }

    // FWD end: the mirror — interior is −x, the face behind the break is the piece's x0.
    var end = endMm
    if (end >= hi - eps) {
        end = max(hi, endMm)
    } else {
        // Half-open the other way: an end on a face belongs to the piece ending there.
        var i = pieces.indexOfFirst { end > it.x0Mm + eps && end <= it.x1Mm + eps }
        var guard = pieces.size + 1
        while (i >= 0 && guard-- > 0) {
            val p = pieces[i]
            val r = room(p)
            if (!cutPiece[i] && end - p.x0Mm >= r - eps) break
            val candidate = p.x0Mm + r
            if (!cutPiece[i] && candidate <= p.x1Mm + eps) {
                end = max(end, candidate)
                break
            }
            end = max(end, p.x1Mm)
            i = if (i < pieces.size - 1 && contiguous(p, pieces[i + 1])) i + 1 else -1
        }
        if (end >= hi - eps) end = max(hi, endMm)
    }
    return start to end
}

/**
 * [pieces] cut at every shoulder of [cuts] that falls strictly inside a piece, the Ø interpolated
 * linearly to the split — the walk in [withUndercutBreakRoom] then meets each shoulder as a face.
 * No cuts returns [pieces] itself.
 */
private fun splitEnvelopeAtCutShoulders(
    pieces: List<EnvelopePiece>,
    cuts: List<UndercutSpanMm>,
    epsMm: Float,
): List<EnvelopePiece> {
    if (cuts.isEmpty()) return pieces
    val shoulders = cuts.flatMap { listOf(it.startMm, it.endMm) }.distinct().sorted()
    val out = mutableListOf<EnvelopePiece>()
    for (p in pieces) {
        val inside = shoulders.filter { it > p.x0Mm + epsMm && it < p.x1Mm - epsMm }
        if (inside.isEmpty()) {
            out += p
            continue
        }
        fun diaAt(x: Float): Float {
            val w = p.x1Mm - p.x0Mm
            return if (w <= 0f) p.dia0Mm else p.dia0Mm + (p.dia1Mm - p.dia0Mm) * ((x - p.x0Mm) / w)
        }
        val cuts1 = listOf(p.x0Mm) + inside + p.x1Mm
        for (k in 0 until cuts1.size - 1) {
            val a = cuts1[k]
            val b = cuts1[k + 1]
            out += EnvelopePiece(a, b, diaAt(a), diaAt(b))
        }
    }
    return out
}

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
 * - [UndercutReference.PARENT_AFT] / [UndercutReference.PARENT_FWD]: the liner formulas with
 *   the PARENT cut's span — canonical = `parentStartMm + enteredMm` /
 *   `parentEndMm − enteredMm − lengthMm`.
 *
 * [linerStartMm]/[linerEndMm] are the reference liner's shaft-space edges (the liner
 * named by `Undercut.referenceLinerId`); they are read only by the `LINER_*` branches, so
 * SET-authored call sites may pass zeros. Callers offer the liner references only while
 * that liner exists — a deleted reference liner falls back to the AFT_SET projection.
 * [parentStartMm]/[parentEndMm] are the parent cut's span (`Undercut.referenceCutId`), read
 * only by the `PARENT_*` branches.
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
    parentStartMm: Float = 0f,
    parentEndMm: Float = 0f,
): Float = when (reference) {
    UndercutReference.AFT_SET -> aftSetXMm + enteredMm
    UndercutReference.FWD_SET -> fwdSetXMm - enteredMm - lengthMm
    UndercutReference.LINER_AFT -> linerStartMm + enteredMm
    UndercutReference.LINER_FWD -> linerEndMm - enteredMm - lengthMm
    UndercutReference.PARENT_AFT -> parentStartMm + enteredMm
    UndercutReference.PARENT_FWD -> parentEndMm - enteredMm - lengthMm
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
    parentStartMm: Float = 0f,
    parentEndMm: Float = 0f,
): Float = when (reference) {
    UndercutReference.AFT_SET -> canonicalStartMm - aftSetXMm
    UndercutReference.FWD_SET -> fwdSetXMm - canonicalStartMm - lengthMm
    UndercutReference.LINER_AFT -> canonicalStartMm - linerStartMm
    UndercutReference.LINER_FWD -> linerEndMm - canonicalStartMm - lengthMm
    UndercutReference.PARENT_AFT -> canonicalStartMm - parentStartMm
    UndercutReference.PARENT_FWD -> parentEndMm - canonicalStartMm - lengthMm
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
    parentStartMm: Float = 0f,
    parentEndMm: Float = 0f,
): Float {
    val distanceMm = canonicalToUndercutStartMm(
        reference, canonicalStartMm, oldLengthMm, aftSetXMm, fwdSetXMm, linerStartMm, linerEndMm,
        parentStartMm, parentEndMm,
    )
    return undercutStartToCanonicalMm(
        reference, distanceMm, newLengthMm, aftSetXMm, fwdSetXMm, linerStartMm, linerEndMm,
        parentStartMm, parentEndMm,
    )
}

// ── Liner extensions ──
//
// A cut that covers a liner and runs on into the neighbouring body is the stored shaft-space
// span `[startMm, startMm + lengthMm]` read against the reference liner's edges — the relation
// "Distance from Liner AFT" already expresses, with the sign flipped. Nothing here is stored:
// the extensions are a view of the span, and a typed extension maps back to canonical through
// the authored liner edge exactly as a typed Distance does.

/**
 * How far the span `[startMm, startMm + lengthMm]` runs past the reference liner's edges into
 * the neighbouring stock, as `(aft, fwd)`. Each term is ≥ 0, and 0 when that end lies inside
 * the liner (or exactly on its edge).
 */
fun undercutLinerExtensionsMm(
    startMm: Float,
    lengthMm: Float,
    linerStartMm: Float,
    linerEndMm: Float,
): Pair<Float, Float> {
    val endMm = startMm + lengthMm
    return max(0f, linerStartMm - startMm) to max(0f, endMm - linerEndMm)
}

/**
 * Which liner edges an edit has newly carried the span past, as `(aft, fwd)`: an end is "new"
 * when its extension ([undercutLinerExtensionsMm]) was within [epsMm] BEFORE the edit and is
 * beyond it AFTER. An end that already overhung and grows further, or one pulled back inside, is
 * not new. Pure; it decides only whether to ASK — the card's "Past the liner edge" question after
 * a Distance or Length commit (on-device request: the machinist may not know the typed numbers
 * run the cut off the liner, or the measurement may be wrong). Nothing is rewritten either way.
 */
fun newLinerOverhangs(
    beforeStartMm: Float,
    beforeLengthMm: Float,
    afterStartMm: Float,
    afterLengthMm: Float,
    linerStartMm: Float,
    linerEndMm: Float,
    epsMm: Float = UNDERCUT_TOUCH_EPS_MM,
): Pair<Boolean, Boolean> {
    val (beforeAft, beforeFwd) =
        undercutLinerExtensionsMm(beforeStartMm, beforeLengthMm, linerStartMm, linerEndMm)
    val (afterAft, afterFwd) =
        undercutLinerExtensionsMm(afterStartMm, afterLengthMm, linerStartMm, linerEndMm)
    return (beforeAft <= epsMm && afterAft > epsMm) to (beforeFwd <= epsMm && afterFwd > epsMm)
}

/**
 * The span after typing an AFT extension of [extMm] past the liner's AFT edge [linerStartMm],
 * as `(start, length)`. The span's FWD end stays pinned. `extMm > 0` puts the start at
 * `linerStartMm − extMm`; `extMm == 0` pulls a start that lies AFT of the liner edge back to
 * the edge and leaves a start inside the liner where it is (zero extension means "does not run
 * past the edge", not "starts at the edge"). A negative [extMm] is treated as 0.
 *
 * Pure; does not validate or clamp — the caller limits [extMm] to the stock that exists
 * ([clampUndercutExtension]) and checks the result's length (a span whose FWD end lies AFT of
 * the liner edge comes back with a non-positive length).
 */
fun undercutSpanWithAftExtension(
    startMm: Float,
    lengthMm: Float,
    linerStartMm: Float,
    extMm: Float,
): Pair<Float, Float> {
    val endMm = startMm + lengthMm
    val ext = max(0f, extMm)
    val newStartMm = when {
        ext > 0f -> linerStartMm - ext
        startMm < linerStartMm -> linerStartMm
        else -> startMm
    }
    return newStartMm to (endMm - newStartMm)
}

/**
 * Mirror of [undercutSpanWithAftExtension] for the liner's FWD edge [linerEndMm]: the span's
 * AFT end stays pinned. `extMm > 0` puts the FWD end at `linerEndMm + extMm`; `extMm == 0`
 * pulls a FWD end that lies past the liner edge back to it and leaves one inside the liner
 * where it is. A negative [extMm] is treated as 0. Returns `(start, length)`.
 */
fun undercutSpanWithFwdExtension(
    startMm: Float,
    lengthMm: Float,
    linerEndMm: Float,
    extMm: Float,
): Pair<Float, Float> {
    val endMm = startMm + lengthMm
    val ext = max(0f, extMm)
    val newEndMm = when {
        ext > 0f -> linerEndMm + ext
        endMm > linerEndMm -> linerEndMm
        else -> endMm
    }
    return startMm to (newEndMm - startMm)
}

/**
 * Stock available AFT of the liner for an extension: from the shaft's AFT end (0) to the liner's
 * AFT edge [linerStartMm]; never negative. A cut can only run past the liner into stock that is
 * there (on-device request), so this is the most the AFT "Extend past liner" field can apply.
 */
fun maxUndercutAftExtensionMm(linerStartMm: Float): Float = max(0f, linerStartMm)

/**
 * Stock available FWD of the liner: from the liner's FWD edge [linerEndMm] to the shaft's FWD
 * end [oalMm]; never negative. Mirror of [maxUndercutAftExtensionMm].
 */
fun maxUndercutFwdExtensionMm(linerEndMm: Float, oalMm: Float): Float = max(0f, oalMm - linerEndMm)

/**
 * A typed extension limited to the stock that exists: [appliedMm] is the value the span is built
 * from, and [limited] is true when the typed value asked for more than the shaft has.
 */
data class UndercutExtensionClamp(val appliedMm: Float, val limited: Boolean)

/**
 * Limits a typed extension [typedMm] to [maxMm] (from [maxUndercutAftExtensionMm] /
 * [maxUndercutFwdExtensionMm]). A value at or under the maximum passes through unchanged; a
 * larger one applies the maximum and reports `limited`. A negative [typedMm] applies 0 and is not
 * limited — the extension functions already read a negative as 0.
 *
 * This is a deliberate carve-out from the golden rule, for a DERIVED field only: the extension is
 * a view of the stored shaft-space span, nothing stores it, and a cut running past the shaft's
 * end is a limit of the shaft rather than a measurement to keep. The clamp applies at the moment
 * of typing; a stored span is never clamped after the fact.
 */
fun clampUndercutExtension(typedMm: Float, maxMm: Float): UndercutExtensionClamp {
    val typed = max(0f, typedMm)
    val cap = max(0f, maxMm)
    return if (typed > cap) UndercutExtensionClamp(cap, limited = true)
    else UndercutExtensionClamp(typed, limited = false)
}

/**
 * True when the span `[startMm, startMm + lengthMm]` is the liner's own span
 * `[linerStartMm, linerEndMm]`, both ends within [epsMm] ([UNDERCUT_TOUCH_EPS_MM] by default, so
 * the "Whole liner" preset's own `Float` round trip still reads as the whole liner on a long shaft).
 */
fun undercutIsWholeLiner(
    startMm: Float,
    lengthMm: Float,
    linerStartMm: Float,
    linerEndMm: Float,
    epsMm: Float = UNDERCUT_TOUCH_EPS_MM,
): Boolean =
    abs(startMm - linerStartMm) <= epsMm && abs(startMm + lengthMm - linerEndMm) <= epsMm

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

/**
 * True when the liner edge at [edgeMm] is **buried**: some span in [spans] strictly crosses it,
 * running more than [epsMm] past it on BOTH sides — the cut is machined past the liner's end and
 * on into the body, so the step the edge used to make is gone and there is nothing at that
 * station a machinist can measure from (on-device request: such a cut measures from a S.E.T.).
 *
 * A cut that merely ENDS at the edge, or covers the liner's end without running past it, leaves a
 * step in the silhouette there — the trimmed liner is still a different Ø from the body beside it
 * — so that edge stays a datum and this returns false. [spans] is every cut on the sheet, the one
 * being edited included (its previewed span), since any of them can take the edge away.
 */
fun undercutLinerEdgeBuried(
    edgeMm: Float,
    spans: List<UndercutSpanMm>,
    epsMm: Float = UNDERCUT_TOUCH_EPS_MM,
): Boolean = spans.any { it.startMm < edgeMm - epsMm && it.endMm > edgeMm + epsMm }

/**
 * The edges of the liner `[linerStartMm, linerEndMm]` that [spans] bury
 * ([undercutLinerEdgeBuried]), AFT first — the stations where a liner strip's chained rail takes
 * an extra node (`pdf/UndercutStripLayout.kt`'s `buildUndercutRailSpans`). Empty when both edges
 * still stand.
 */
fun buriedUndercutLinerEdgesMm(
    linerStartMm: Float,
    linerEndMm: Float,
    spans: List<UndercutSpanMm>,
    epsMm: Float = UNDERCUT_TOUCH_EPS_MM,
): List<Float> = listOf(linerStartMm, linerEndMm)
    .distinct()
    .filter { undercutLinerEdgeBuried(it, spans, epsMm) }

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
): String? =
    if (undercutOverlapPartnerId(canonicalStartMm, lengthMm, otherSpans) != null) {
        UNDERCUT_PARTIAL_OVERLAP_MSG
    } else {
        null
    }

/**
 * The id of the first cut in [otherSpans] the span `[canonicalStartMm, canonicalStartMm +
 * lengthMm]` partially overlaps (or duplicates), or `null` when it overlaps none — the partner
 * [undercutOverlapIssue] blocks on, so a caller that knows where that cut sits can NAME it
 * ([undercutOverlapMessage]).
 */
fun undercutOverlapPartnerId(
    canonicalStartMm: Float,
    lengthMm: Float,
    otherSpans: List<UndercutSpanMm>,
): String? {
    val draft = UndercutSpanMm("", canonicalStartMm, canonicalStartMm + lengthMm)
    return otherSpans.firstOrNull { o -> undercutSpansPartiallyOverlap(draft, o) }?.id
}

/**
 * The blocked-overlap predicate behind [undercutOverlapIssue] and [undercutPartialOverlapsMm]:
 * [a] and [b] are neither clear of each other (touching edge-to-edge counts as clear, within
 * [UNDERCUT_TOUCH_EPS_MM] — an edge-to-edge preset's `Float` edge drifts by an ulp on a long
 * shaft) nor nested either way ([undercutSpanContains]). Two identical spans satisfy it — one cut
 * entered twice is blocked like a partial intrusion. The confirm check and the canvas overlap
 * band both read this one predicate, so they share the slop.
 */
private fun undercutSpansPartiallyOverlap(a: UndercutSpanMm, b: UndercutSpanMm): Boolean {
    val eps = UNDERCUT_TOUCH_EPS_MM
    val clear = b.endMm <= a.startMm + eps || b.startMm >= a.endMm - eps
    return !clear && !undercutSpanContains(a, b) && !undercutSpanContains(b, a)
}

/**
 * The shaft-space intervals `[max(starts), min(ends)]` where the draft span
 * `[draftStartMm, draftStartMm + draftLengthMm]` partially overlaps one of [otherSpans] — the
 * same partners [undercutOverlapIssue] blocks on, one interval per partner in [otherSpans]
 * order. Touching and nested partners contribute nothing. The overlay paints these as the
 * visible reason a blocked draft cannot be confirmed.
 */
fun undercutPartialOverlapsMm(
    draftStartMm: Float,
    draftLengthMm: Float,
    otherSpans: List<UndercutSpanMm>,
): List<Pair<Float, Float>> {
    val draft = UndercutSpanMm("", draftStartMm, draftStartMm + draftLengthMm)
    return otherSpans
        .filter { o -> undercutSpansPartiallyOverlap(draft, o) }
        .map { o -> max(draft.startMm, o.startMm) to min(draft.endMm, o.endMm) }
}

/**
 * The generic blocked-overlap wording, for a caller with no page to number the partner against.
 * A partial intrusion and a duplicate span read the same, because the fix is the same — move the
 * cut fully inside its neighbour or fully clear of it. Short enough to fit the status pill in two
 * lines on a phone.
 */
const val UNDERCUT_PARTIAL_OVERLAP_MSG =
    "Overlaps another cut — sit fully inside it or fully clear"

/**
 * The blocked-overlap wording that NAMES the partner (on-device report: the generic text did not
 * say which cut the draft collided with): `"Overlaps cut N — …"` with [partnerPosition] the
 * partner's 1-based page position (the number its card title carries), or "a cut outside this
 * strip" when the partner is on no page here (`null`).
 */
fun undercutOverlapMessage(partnerPosition: Int?): String =
    if (partnerPosition != null) {
        "Overlaps cut $partnerPosition — sit fully inside it or fully clear"
    } else {
        "Overlaps a cut outside this strip — sit fully inside it or fully clear"
    }

// ── Butt-against presets ──

/**
 * "Start at end of cut N": the canonical start that puts a draft's AFT end on the other cut's
 * FWD end [otherEndMm] — edge to edge, which [undercutOverlapIssue] counts as clear. The length
 * is never touched; shaft bounds are the caller's check ([undercutSpanIssue]).
 */
fun undercutStartAtEndOf(otherEndMm: Float): Float = otherEndMm

/**
 * "End at start of cut N": the canonical start that puts the FWD end of a draft of [lengthMm] on
 * the other cut's AFT end [otherStartMm]. The length is never touched; shaft bounds are the
 * caller's check ([undercutSpanIssue]).
 */
fun undercutEndAtStartOf(otherStartMm: Float, lengthMm: Float): Float = otherStartMm - lengthMm

// ── New-cut seeds ──

/** Length a newly drafted or added undercut starts at (1 in). */
const val UNDERCUT_DEFAULT_LENGTH_MM = 25.4f

/**
 * A new cut's seed butted against a cut already on the strip: the span `[startMm, startMm +
 * lengthMm]` touches [neighbourId] edge to edge. The caller seeds the new card's reference from
 * that neighbour, so a chain authored from one datum keeps reading from it.
 */
data class UndercutButtedSeed(val startMm: Float, val lengthMm: Float, val neighbourId: String)

/**
 * Where an "Add undercut…" on a strip seeds its new cut when the strip already holds cuts
 * ([stripCuts], render-clamped): butted on the FWD end of the FWD-most cut while
 * [lengthMm] of shaft remains FWD of it, else butted on the AFT end of the AFT-most cut while
 * [lengthMm] of shaft remains AFT of it — the shop records a run of cuts one after the next, so
 * the next one usually starts where the last one ends (on-device request). `null` when the strip
 * has no cuts or neither side fits; the caller then seeds the strip's default span.
 *
 * A side is taken only when the seed lands in CLEAR stock — no cut in [occupied] (every cut on
 * the sheet, render-clamped) reaches into it by more than [UNDERCUT_TOUCH_EPS_MM] — so a seed
 * never opens already blocked by a cut on a neighbouring strip. The FWD-most cut is the one with
 * the largest end (ties to the outermost, a parent over a child sharing its shoulder), the
 * AFT-most the one with the smallest start; a nested cut never ends past its parent, so the
 * neighbour is always a top-level cut. Pure: nothing is recorded — the seed is a draft.
 */
fun undercutAddSeedSpan(
    stripCuts: List<UndercutSpanMm>,
    occupied: List<UndercutSpanMm>,
    oalMm: Float,
    lengthMm: Float = UNDERCUT_DEFAULT_LENGTH_MM,
): UndercutButtedSeed? {
    val cuts = stripCuts.filter { it.endMm > it.startMm }
    if (cuts.isEmpty() || lengthMm <= 0f) return null
    val eps = UNDERCUT_TOUCH_EPS_MM
    fun clear(startMm: Float): Boolean = occupied.none { o ->
        o.startMm < startMm + lengthMm - eps && o.endMm > startMm + eps
    }

    val fwdMost = cuts.sortedWith(compareByDescending<UndercutSpanMm> { it.endMm }.thenBy { it.startMm }).first()
    val fwdStart = undercutStartAtEndOf(fwdMost.endMm)
    if (fwdStart + lengthMm <= oalMm + UNDERCUT_SPAN_EPS_MM && clear(fwdStart)) {
        return UndercutButtedSeed(fwdStart, lengthMm, fwdMost.id)
    }
    val aftMost = cuts.sortedWith(compareBy<UndercutSpanMm> { it.startMm }.thenByDescending { it.endMm }).first()
    val aftStart = undercutEndAtStartOf(aftMost.startMm, lengthMm)
    if (aftStart >= -UNDERCUT_SPAN_EPS_MM && clear(aftStart)) {
        return UndercutButtedSeed(aftStart, lengthMm, aftMost.id)
    }
    return null
}

/**
 * The card's "Next cut" seed after [current]: a cut of [lengthMm] starting on [current]'s FWD end,
 * Ø unentered (0), no note, with [current]'s authored reference, reference liner and reference cut
 * carried over verbatim — the next cut of a run reads from the same datum. `null` when the shaft
 * has less than [lengthMm] of stock FWD of [current] (the button is then disabled; a seed is never
 * placed past the shaft end). The returned id is fresh and meaningless: the caller keys the seed
 * as its pending draft. Pure: nothing is recorded.
 */
fun undercutNextCutSeed(
    current: Undercut,
    oalMm: Float,
    lengthMm: Float = UNDERCUT_DEFAULT_LENGTH_MM,
): Undercut? {
    val startMm = undercutStartAtEndOf(current.startFromAftMm + current.lengthMm)
    if (lengthMm <= 0f || startMm + lengthMm > oalMm + UNDERCUT_SPAN_EPS_MM) return null
    return Undercut(
        startFromAftMm = startMm,
        lengthMm = lengthMm,
        diaMm = 0f,
        authoredReference = current.authoredReference,
        referenceLinerId = current.referenceLinerId,
        referenceCutId = current.referenceCutId,
    )
}

// ── Missing diameters ──

/**
 * The recorded cuts with no measured Ø (`diaMm <= 0` — placed but not yet measured), in record
 * order. Such a cut prints with no diameter, so the tab flags it on its list row and asks before
 * printing or exporting a sheet that carries values.
 */
fun undercutsMissingDia(undercuts: List<Undercut>): List<Undercut> = undercuts.filter { it.diaMm <= 0f }

/** The tab's statement of [count] cuts with no Ø — its list note and the print/export question. */
fun undercutMissingDiaMessage(count: Int): String =
    if (count == 1) "1 cut has no diameter and will print without one."
    else "$count cuts have no diameter and will print without one."

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
 * [epsMm] defaults to [UNDERCUT_TOUCH_EPS_MM], the slop the clear test of the overlap predicate
 * uses: a cut sitting on another's edge within `Float` drift must read as nested or as clear,
 * never as a partial overlap in the gap between two tolerances. The containment forest, the
 * confirm check and the overlap band all go through here, so they agree.
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
    epsMm: Float = UNDERCUT_TOUCH_EPS_MM,
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
 * Where a cut's measured-Ø callout hangs from: the midpoint of the largest **visible** segment
 * of its own drawn floor, in shaft-space mm.
 *
 * A leader has to terminate on the floor it names. A cut's span midpoint does that only while
 * nothing is machined inside it: a child cut replaces the parent's floor over its own span, so a
 * parent whose midpoint falls in a child lands its leader on the child's face or in the void
 * below it. Concentric cuts are worse — several cuts sharing a midpoint drop every leader onto
 * ONE shared stem that fans out to the spread labels, a bird foot in which no leader identifies
 * a floor at all (on-device report).
 *
 * So the anchor is a shelf: subtract from [span] every other span CONTAINED in it
 * ([undercutSpanContains] — subtracting the direct children covers deeper descendants too, since
 * those lie inside the children), then take the midpoint of the widest remaining segment. Ties
 * go to the AFT-most segment, so concentric nesting stays deterministic.
 *
 * A childless cut has one segment — its whole span — so it anchors at its own midpoint exactly
 * as it always did. A cut whose children cover it end to end draws no floor of its own anywhere;
 * it falls back to the span midpoint, the least-bad position and the historical one.
 */
fun undercutCalloutAnchorMm(
    span: UndercutSpanMm,
    others: List<UndercutSpanMm>,
    epsMm: Float = UNDERCUT_TOUCH_EPS_MM,
): Float {
    val mid = (span.startMm + span.endMm) * 0.5f
    if (span.endMm - span.startMm <= epsMm) return mid

    val children = others
        .filter { it.id != span.id && undercutSpanContains(span, it, epsMm) }
        .map { it.startMm.coerceIn(span.startMm, span.endMm) to it.endMm.coerceIn(span.startMm, span.endMm) }
        .sortedBy { it.first }

    var bestStart = 0f
    var bestEnd = 0f
    var bestWidth = 0f
    fun consider(a: Float, b: Float) {
        if (b - a > bestWidth) { bestStart = a; bestEnd = b; bestWidth = b - a }
    }
    var cursor = span.startMm
    children.forEach { (cs, ce) ->
        consider(cursor, cs)
        cursor = max(cursor, ce)
    }
    consider(cursor, span.endMm)

    return if (bestWidth <= epsMm) mid else (bestStart + bestEnd) * 0.5f
}

/**
 * [undercutCalloutAnchorMm] over a whole strip's [spans], keyed by span id — the form both
 * draw sites take, so a leader leaves the same shelf on the canvas overlay and on the sheet.
 */
fun undercutCalloutAnchorsMm(
    spans: List<UndercutSpanMm>,
    epsMm: Float = UNDERCUT_TOUCH_EPS_MM,
): Map<String, Float> = spans.associate { it.id to undercutCalloutAnchorMm(it, spans, epsMm) }

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

    /**
     * The window is the chain (its edges are the outer witness datums); the draw range starts
     * as the window and only grows OUTWARD past it — by [withUndercutBreakRoom] in
     * [buildUndercutStrips] — so the extra stock prints undimensioned beyond the window edge
     * and the window's pad spans keep their value.
     */
    data class FreeStrip(
        val window: UndercutWindow,
        override val drawStartMm: Float = window.startMm,
        override val drawEndMm: Float = window.endMm,
    ) : UndercutStrip() {
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
 * Every span's liner for strip purposes, id → liner id (`null` = a bare-shaft cut): the
 * overlap-share rule ([assignUndercutLiner]) first, then CHAINING — a bare-shaft cut touching
 * (within [epsMm]), overlapping or nested with a cut assigned to liner L joins L too, repeated
 * to a fixed point, so a run of cuts machined on from a liner's edge into the body stays on that
 * liner's strip rather than splitting off into a free window (on-device request). A cut that
 * overlaps a liner keeps its overlap-share liner; a bare-shaft cut reaching two liners' chains in
 * the same pass joins the AFT-most. Each pass reads only the previous pass's result, so the
 * outcome does not depend on [spans] order.
 */
fun assignUndercutLiners(
    spans: List<UndercutSpanMm>,
    liners: List<UndercutLinerSpan>,
    epsMm: Float = UNDERCUT_TOUCH_EPS_MM,
): Map<String, String?> {
    val assigned = LinkedHashMap<String, String?>()
    spans.forEach { assigned[it.id] = assignUndercutLiner(it, liners) }
    val linerStart = liners.associate { it.id to it.startMm }
    fun inContact(a: UndercutSpanMm, b: UndercutSpanMm) =
        a.startMm <= b.endMm + epsMm && b.startMm <= a.endMm + epsMm
    var guard = spans.size + 1
    while (guard-- > 0) {
        val prev = HashMap(assigned)
        var changed = false
        for (s in spans) {
            if (prev[s.id] != null) continue
            val joined = spans
                .filter { o -> o.id != s.id && prev[o.id] != null && inContact(s, o) }
                .mapNotNull { prev[it.id] }
                .distinct()
                .minByOrNull { linerStart[it] ?: Float.MAX_VALUE }
                ?: continue
            assigned[s.id] = joined
            changed = true
        }
        if (!changed) break
    }
    return assigned
}

/**
 * Build one [UndercutStrip.LinerStrip] for [liner] and the cuts assigned to it (may be
 * empty — the overlay uses this to zoom an undercut-free liner for authoring). The draw
 * range is the liner's full span, expanded by any cut overhang past its edges, then
 * padded by [padMm] each side and clamped to `[0, oalMm]`, then given break room against
 * [segs] ([withUndercutBreakRoom] — empty [segs] skips it); the chain range is the liner
 * span expanded by overhang only (no pad), so the rail's outer witness lines sit on real
 * datums — liner edges or cut shoulders, never the arbitrary pad edge.
 *
 * [reachToSetMm] > 0 ("Draw to the shaft end", [UNDERCUT_SET_REACH_MM]): a chain end at or
 * inboard of the FWD S.E.T. ([fwdSetMm]) and within the reach of it moves onto the S.E.T. — a
 * real datum — so the chain rail's trailing span dimensions the run from the last cut to it,
 * while the DRAW range runs on to the shaft's physical end ([oalMm]: a flat end, no break), so
 * a FWD thread counted in the OAL draws beyond the S.E.T. undimensioned. The AFT end mirrors it
 * against [aftSetMm] and `0`. A chain already past its S.E.T. (a cut into the thread) is left
 * alone. `0` leaves the chain on the liner.
 */
fun linerStripFor(
    liner: UndercutLinerSpan,
    assignedSpans: List<UndercutSpanMm>,
    oalMm: Float,
    padMm: Float = UNDERCUT_WINDOW_PAD_MM,
    segs: List<SurfaceSeg> = emptyList(),
    /** The strip's own cuts, whose shoulders the window-end breaks also clear ([withUndercutBreakRoom]). */
    cutSpans: List<UndercutSpanMm> = emptyList(),
    /** "Draw to the shaft end" reach; `0` = off (see the KDoc). */
    reachToSetMm: Float = 0f,
    /** AFT S.E.T. x (physical mm) the reach measures from and the chain moves onto. */
    aftSetMm: Float = 0f,
    /** FWD S.E.T. x (physical mm) the reach measures from and the chain moves onto. */
    fwdSetMm: Float = oalMm,
): UndercutStrip.LinerStrip {
    val hi = oalMm.coerceAtLeast(0f)
    val cutsMin = assignedSpans.minOfOrNull { it.startMm } ?: liner.startMm
    val cutsMax = assignedSpans.maxOfOrNull { it.endMm } ?: liner.endMm
    var chainStart = min(liner.startMm, cutsMin)
    var chainEnd = max(liner.endMm, cutsMax)
    var drawToAftEnd = false
    var drawToFwdEnd = false
    if (reachToSetMm > 0f) {
        if (chainEnd <= fwdSetMm && fwdSetMm - chainEnd <= reachToSetMm) {
            chainEnd = fwdSetMm
            drawToFwdEnd = true
        }
        if (chainStart >= aftSetMm && chainStart - aftSetMm <= reachToSetMm) {
            chainStart = aftSetMm
            drawToAftEnd = true
        }
    }
    val (drawStart, drawEnd) = withUndercutBreakRoom(
        if (drawToAftEnd) 0f else (chainStart - padMm).coerceIn(0f, hi),
        if (drawToFwdEnd) hi else (chainEnd + padMm).coerceIn(0f, hi),
        segs, oalMm, cutSpans,
    )
    return UndercutStrip.LinerStrip(
        linerId = liner.id,
        linerStartMm = liner.startMm,
        linerEndMm = liner.endMm,
        drawStartMm = drawStart,
        drawEndMm = drawEnd,
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
 * Clamped to `[0, oalMm]`, then given break room against [segs] ([withUndercutBreakRoom] —
 * widening only, so the never-narrow rule holds; empty [segs] skips it). Spans must already be
 * render-clamped ([clampUndercutSpan]).
 */
fun undercutPreviewDrawRange(
    strip: UndercutStrip,
    previewSpans: List<UndercutSpanMm>,
    oalMm: Float,
    padMm: Float = UNDERCUT_WINDOW_PAD_MM,
    segs: List<SurfaceSeg> = emptyList(),
    /** The strip's own previewed cuts, whose shoulders the window-end breaks also clear. */
    cutSpans: List<UndercutSpanMm> = emptyList(),
): Pair<Float, Float> {
    val hi = oalMm.coerceAtLeast(0f)
    val spansMin = previewSpans.minOfOrNull { it.startMm }
        ?: return withUndercutBreakRoom(strip.drawStartMm, strip.drawEndMm, segs, oalMm, cutSpans)
    val spansMax = previewSpans.maxOf { it.endMm }
    return withUndercutBreakRoom(
        min(strip.drawStartMm, spansMin - padMm).coerceIn(0f, hi),
        max(strip.drawEndMm, spansMax + padMm).coerceIn(0f, hi),
        segs, oalMm, cutSpans,
    )
}

/**
 * Group cuts into detail strips: every cut overlapping a liner — or chained to one that does,
 * [assignUndercutLiners] — joins that liner's [UndercutStrip.LinerStrip] (one strip per liner
 * with ≥1 cut, covering the whole liner and every chained cut); the remaining bare-shaft cuts
 * cluster into [UndercutStrip.FreeStrip] windows via
 * [clusterUndercuts]. Every strip's DRAW range gets break room against [segs]
 * ([withUndercutBreakRoom]; chain datums never move — a free strip's window stays its chain
 * and only its draw range grows). Result is sorted aft → fwd by draw start. Consumed by the
 * overview affordances, the detail overlay, and the PDF strips, so all three agree by
 * construction. Empty spans are skipped, as in [clusterUndercuts]. Empty [segs] skips the
 * break room. [reachToSetMm], [aftSetMm] and [fwdSetMm] are forwarded to every liner strip
 * ([linerStripFor]'s "Draw to the shaft end"); free strips ignore them.
 */
fun buildUndercutStrips(
    spans: List<UndercutSpanMm>,
    liners: List<UndercutLinerSpan>,
    oalMm: Float,
    gapMm: Float = UNDERCUT_CLUSTER_GAP_MM,
    padMm: Float = UNDERCUT_WINDOW_PAD_MM,
    segs: List<SurfaceSeg> = emptyList(),
    reachToSetMm: Float = 0f,
    aftSetMm: Float = 0f,
    fwdSetMm: Float = oalMm,
): List<UndercutStrip> {
    val live = spans.filter { it.endMm > it.startMm }
    val linerOf = assignUndercutLiners(live, liners)
    val byLiner = live.groupBy { linerOf[it.id] }
    val strips = mutableListOf<UndercutStrip>()
    for (liner in liners) {
        val assigned = byLiner[liner.id] ?: continue
        strips += linerStripFor(
            liner, assigned, oalMm, padMm, segs, cutSpans = assigned,
            reachToSetMm = reachToSetMm, aftSetMm = aftSetMm, fwdSetMm = fwdSetMm,
        )
    }
    for (window in clusterUndercuts(byLiner[null].orEmpty(), oalMm, gapMm, padMm)) {
        val members = live.filter { it.id in window.undercutIds }
        val (drawStart, drawEnd) =
            withUndercutBreakRoom(window.startMm, window.endMm, segs, oalMm, cutSpans = members)
        strips += UndercutStrip.FreeStrip(window, drawStart, drawEnd)
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

/**
 * Whether any of the undercut sheet's detail [strips] draws a thread: a strip's DRAW range
 * overlapping a thread span ([threadSpansMm], shaft-space start to end, zero-length ones
 * ignored). The undercut sheet draws a thread only where a strip window reaches it (or on the
 * whole-shaft profile, which prints only when there are no strips — the caller's case), so the
 * options sheet offers its thread-hatch controls only then; elsewhere they would tune nothing
 * (on-device request: fewer controls). The printed strip may widen its range by a few points
 * of paper pad beyond these ranges; a thread reached only by that pad keeps its hatch while the
 * controls stay hidden — the setting still applies, and it remains on the other sheets.
 */
fun undercutStripsReachThread(
    strips: List<UndercutStrip>,
    threadSpansMm: List<Pair<Float, Float>>,
): Boolean = threadSpansMm.any { (a, b) ->
    b > a && strips.any { st -> st.drawStartMm < b && st.drawEndMm > a }
}
