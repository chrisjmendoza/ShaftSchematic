package com.android.shaftschematic.pdf

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path

// ──────────────────────────────────────────────────────────────────────────────
// Round-stock break symbol (shared by Shaft/Runout/Wear PDF composers)
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Draw one round-stock "S-break" edge at position [x], spanning the shaft from
 * [yTop] to [yBot].
 *
 * Two strokes: a full S-curve, plus a return sweep that starts at one tip of the
 * S, arcs back on the opposite side of that half's lobe, and dies into the S at
 * the centerline — enclosing the open "eye" that makes the break read as a
 * revolved round surface rather than a flat plate. [eyeAtTop] picks which tip
 * the sweep returns from; a break's two edges alternate (left edge bottom,
 * right edge top) the way the symbol is drawn by hand.
 *
 * Positive [amplitude] bulges the S right in the upper half and left in the
 * lower half; negative mirrors both strokes.
 */
internal fun drawBreakEdge(
    c: Canvas,
    x: Float,
    yTop: Float,
    yBot: Float,
    amplitude: Float,
    p: Paint,
    eyeAtTop: Boolean,
) {
    val h = yBot - yTop
    val cy = yTop + h / 2f
    val stroke = Paint(p).apply { style = Paint.Style.STROKE }

    // Return sweep: the S's own half-lobe (by de Casteljau subdivision at t=0.5)
    // mirrored about the break line and widened by RETURN_SWEEP_FULLNESS, so it
    // leaves the tip and rejoins the centerline tangent to the eye it closes.
    val k = RETURN_SWEEP_FULLNESS
    val sweep = Path().apply {
        if (eyeAtTop) {
            moveTo(x, yTop)
            cubicTo(x - k * amplitude / 2f, yTop + h / 6f, x - k * amplitude / 4f, yTop + h / 3f, x, cy)
        } else {
            moveTo(x, yBot)
            cubicTo(x + k * amplitude / 2f, yBot - h / 6f, x + k * amplitude / 4f, yBot - h / 3f, x, cy)
        }
    }

    // Shade the eye first so both strokes stay crisp on top. The closing curve is
    // the S's own half-lobe (subdivided controls), traced center → tip. Translucent
    // wash rather than opaque grey so it darkens shaded bodies too, like a shadow.
    val eye = Path(sweep).apply {
        if (eyeAtTop) {
            cubicTo(x + amplitude / 4f, yTop + h / 3f, x + amplitude / 2f, yTop + h / 6f, x, yTop)
        } else {
            cubicTo(x - amplitude / 4f, yBot - h / 3f, x - amplitude / 2f, yBot - h / 6f, x, yBot)
        }
        close()
    }
    c.drawPath(eye, Paint(p).apply { style = Paint.Style.FILL; color = EYE_SHADE_COLOR })

    c.drawPath(
        Path().apply {
            moveTo(x, yTop)
            cubicTo(x + amplitude, yTop + h / 3f, x - amplitude, yBot - h / 3f, x, yBot)
        },
        stroke,
    )
    c.drawPath(sweep, stroke)
}

/** Return-sweep width relative to the S's own lobe; 1 = exact mirror. */
private const val RETURN_SWEEP_FULLNESS = 1.5f

/** Light translucent wash inside the eye (~18% black; matches shaded-body recipe). */
private const val EYE_SHADE_COLOR = 0x2E000000

// ──────────────────────────────────────────────────────────────────────────────
// Paired-break layout — the two edges must never overlap
// ──────────────────────────────────────────────────────────────────────────────

/**
 * How far a break edge's curves reach horizontally INWARD toward its partner, as a
 * fraction of the amplitude — the pair's combined reach in each half of the shaft.
 * Derived from the cubics in [drawBreakEdge]: the main S peaks at √3/6 of its ±amplitude
 * controls, and the return sweep (controls at [RETURN_SWEEP_FULLNESS]·amp/2 and /4) at
 * [RETURN_SWEEP_FULLNESS]·√3/6; one edge contributes its S while the other contributes
 * its sweep in the same half, so the pair closes (1 + fullness)·√3/6 of the amplitude.
 * Changing the glyph's control geometry changes this fraction.
 */
internal val BREAK_PAIR_REACH_FRAC = (1f + RETURN_SWEEP_FULLNESS) * (kotlin.math.sqrt(3f) / 6f)

/**
 * How far a break edge's curves reach horizontally OUTWARD past its x — into the void
 * side — as a fraction of the amplitude. The return sweep is the far reacher: its cubic
 * (controls at [RETURN_SWEEP_FULLNESS]·amp/2 and /4) simplifies to
 * `(3k/4)·t(1−t)(2−t)·amp`, which peaks at `k·√3/6·amp` — past the main S's own `√3/6`.
 * Two packed wear strips' facing break stubs each bulge this far into their shared
 * gutter, so the gutter must host both reaches plus daylight — see
 * `spreadWearStripRowGutters` / `wearStripBreakAmplitudePt` (`WearStripLayout.kt`).
 * Changing the glyph's control geometry changes this fraction.
 */
internal val BREAK_EDGE_OUTWARD_REACH_FRAC = RETURN_SWEEP_FULLNESS * (kotlin.math.sqrt(3f) / 6f)

/** Minimum daylight between the pair's nearest curves ("at worst 1px" — on-device report). */
internal const val BREAK_PAIR_MIN_CLEAR_PT = 1f

/**
 * True when a body run drawn [drawnPt] wide represents less than [minFracOfTrue] of its
 * true width ([trueLenMm] × [truePtPerMm]) — the compressed x mapping squeezed it far
 * enough that the S-break pair must mark the longer span. Mild foreshortening prints a
 * plain outline instead; the dimension rails print true lengths either way, so this is
 * purely a visual-honesty threshold.
 *
 * [minFracOfTrue] is the user's `PdfPrefs.sBreakThresholdFrac` (Settings → Drawing →
 * "Body S-break", default half): `0` disables compression breaks entirely — all
 * foreshortening stays hidden — and `1` breaks on any shortfall at all. `truePtPerMm ≤ 0`
 * also disables the check (callers that never compress pass 0 and break on span length
 * alone). The traditional long-span trigger (`COMPRESS_TRIGGER_PT` at the draw sites) is
 * independent of this rule and unaffected by it.
 *
 * ONE predicate for every break draw site AND the schematic footer's compression note, so
 * the note and the drawn breaks can never disagree.
 */
internal fun breakForCompression(
    drawnPt: Float,
    trueLenMm: Float,
    truePtPerMm: Float,
    minFracOfTrue: Float,
): Boolean =
    truePtPerMm > 0f && minFracOfTrue > 0f && drawnPt < trueLenMm * truePtPerMm * minFracOfTrue

/**
 * Drawn body-run length (pt of paper) at which the center break appears regardless of
 * compression — a run eating this much paper at true scale is not hidden foreshortening,
 * so it breaks at every "Body S-break" setting, Never included. ONE constant for every
 * composer; a per-file copy invites tuning one sheet and missing the other three.
 */
internal const val COMPRESS_TRIGGER_PT = 220f

/** Classic central gap; [breakPairLayout] may widen it to keep the pair clear. */
internal const val ZIGZAG_GAP_MAX_PT = 20f

/** Minimum stub a break gap must leave at each end of the flat span it cuts. */
internal const val BREAK_GAP_MIN_STUB_PT = 6f

/**
 * Center x for a break gap on the flat span [flatX0]..[flatX1], steering the gap clear of
 * [avoidRanges] — drawn x-spans that must stay unbroken (a body keyway's slot: the gap
 * landing inside it would cut the one region the sheet promises at true scale). The span
 * midpoint is preferred (the hand-sheet convention); a centered gap that would touch an
 * avoid range shifts the minimal distance that clears every range while keeping
 * [minStubPt] of stub at both ends. Returns null when no clear placement exists — the
 * caller prints the run plain rather than break inside a protected span.
 */
internal fun breakGapCenter(
    flatX0: Float,
    flatX1: Float,
    gapPt: Float,
    avoidRanges: List<ClosedFloatingPointRange<Float>>,
    minStubPt: Float = BREAK_GAP_MIN_STUB_PT,
): Float? {
    val half = gapPt / 2f
    val lo = flatX0 + minStubPt + half
    val hi = flatX1 - minStubPt - half
    if (hi < lo) return null
    val mid = ((flatX0 + flatX1) / 2f).coerceIn(lo, hi)
    // Centers whose gap would touch a (run-clipped) avoid range.
    val blocked = avoidRanges.mapNotNull { r ->
        val s = maxOf(r.start, flatX0)
        val e = minOf(r.endInclusive, flatX1)
        if (e > s) (s - half)..(e + half) else null
    }
    fun clear(c: Float) = blocked.none { c in it }
    if (clear(mid)) return mid
    val eps = 0.01f
    return blocked
        .flatMap { listOf(it.start - eps, it.endInclusive + eps) }
        .filter { it in lo..hi }
        .sortedBy { kotlin.math.abs(it - mid) }
        .firstOrNull { clear(it) }
}

internal data class BreakPairLayout(val gapPt: Float, val amplitudePt: Float)

/**
 * Gap and amplitude for the paired break glyph on a compressed body run. A pair set
 * `gap` apart keeps `gap − [BREAK_PAIR_REACH_FRAC]·amplitude − strokeWidth` of white
 * between its nearest curves; drawn closer, the glyphs cross (on-device report:
 * overlapping S-breaks on a tall shaft's narrow compressed runs).
 *
 * The classic gap widens when the full amplitude needs more room — up to half the run,
 * so each stub keeps at least a quarter — and the amplitude then clamps to whatever the
 * final gap can host with [BREAK_PAIR_MIN_CLEAR_PT] of daylight: on a run too narrow to
 * host the full glyph, the S flattens slightly rather than ever overlap.
 */
internal fun breakPairLayout(
    runLenPt: Float,
    desiredAmplitudePt: Float,
    classicGapPt: Float,
    strokeWidthPt: Float,
): BreakPairLayout {
    val neededGap = desiredAmplitudePt * BREAK_PAIR_REACH_FRAC + strokeWidthPt + BREAK_PAIR_MIN_CLEAR_PT
    val gap = maxOf(classicGapPt, minOf(neededGap, runLenPt * 0.5f))
    val amp = minOf(
        desiredAmplitudePt,
        ((gap - strokeWidthPt - BREAK_PAIR_MIN_CLEAR_PT) / BREAK_PAIR_REACH_FRAC).coerceAtLeast(0f),
    )
    return BreakPairLayout(gapPt = gap, amplitudePt = amp)
}
