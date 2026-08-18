package com.android.shaftschematic.util

import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.max

/**
 * Sets a [DualLabel] — one measure/draw pair behind every drawn dual value.
 *
 * This is the sibling of [Paint.measureRichText] / [Canvas.drawRichText] and exists for the same
 * reason: **measuring one way and drawing the other is the bug the pairing prevents.** Each line
 * of a stack goes through the rich pair itself, so a fraction inside a stacked dual value is still
 * built up exactly as it is anywhere else.
 *
 * ## What moves, and what the fraction renderer promised
 * `util/FractionTextRenderer.kt` states its own contract as *"Width is the only thing that moves…
 * Nothing that budgets vertical space has to change."* A stacked dual value is the app's first
 * label that **does** move height, so it inherits the other half of that discipline instead: every
 * vertical budget it touches is derived from [dualStackMetrics] and nowhere else. One definition of
 * the line advance, read by both the layout planner and the draw site — they cannot drift.
 *
 * ## The baseline convention
 * [drawDualLabel] takes the baseline of the **first (primary)** line and steps down by
 * [DualStackMetrics.advance] for the secondary. That matches how the callers already compute a
 * baseline — `bounds.top - paint.fontMetrics.ascent`, off the REAL paint ascent — so a caller that
 * hands the planner an inflated-ascent box (see `geom/DimensionRailLayout.TextMetrics`) needs no
 * arithmetic change at all: the inflation grows the box upward, the primary lands where the single
 * line used to, and the secondary fills the room the inflation reserved.
 */

/**
 * Air between the two lines of a stack, as a fraction of the text size.
 *
 * Does double duty: in the in-line (value-in-a-break) case the stack centres on the rail line, so
 * the line's two stubs point straight into this gap and it reads as the seam between the terms. It
 * may want to grow toward 0.25–0.3 once seen on paper — this is the one place to tune it.
 */
const val DUAL_STACK_LEADING_FRAC = 0.15f

/**
 * The vertical geometry of a two-line stack in one paint.
 *
 * @property lineHeight One line's full box, `descent - ascent`.
 * @property leading Air between the lines ([DUAL_STACK_LEADING_FRAC] of the text size).
 * @property advance Baseline-to-baseline step: [lineHeight] + [leading].
 * @property height The whole stack's box, `advance + lineHeight` — what a vertical budget must
 *   reserve, and exactly `lineHeight` more than a single line needs.
 */
data class DualStackMetrics(
    val lineHeight: Float,
    val leading: Float,
    val advance: Float,
    val height: Float,
)

/** The stack geometry for this paint's current text size. */
fun Paint.dualStackMetrics(): DualStackMetrics {
    val fm = fontMetrics
    val lineHeight = fm.descent - fm.ascent
    val leading = textSize * DUAL_STACK_LEADING_FRAC
    val advance = lineHeight + leading
    return DualStackMetrics(
        lineHeight = lineHeight,
        leading = leading,
        advance = advance,
        height = advance + lineHeight,
    )
}

/**
 * Whether [label] actually sets as a stack: it needs two terms AND a stacked layout. Every site
 * asks this one question rather than testing the two conditions apart, so a single-unit label on a
 * stacked sheet can never reserve a stack's height for one line of text.
 */
fun DualLabel.setsStacked(stacked: Boolean): Boolean = stacked && isDual

/**
 * Advance width [drawDualLabel] will occupy: the WIDER line when stacked, the joined one-liner
 * when not. Fractions are measured built-up, via [measureRichText].
 */
fun Paint.measureDualLabel(
    label: DualLabel,
    stacked: Boolean,
    style: FractionTextStyle = FractionTypography.active,
): Float =
    if (label.setsStacked(stacked)) {
        label.lines().fold(0f) { w, line -> max(w, measureRichText(line, style)) }
    } else {
        measureRichText(label.inline(), style)
    }

/**
 * Draws [label] at [x] (per `paint.textAlign`, exactly like [Canvas.drawRichText]) with [baseline]
 * as the FIRST line's baseline; the secondary follows one [DualStackMetrics.advance] below.
 *
 * A label that does not set as a stack draws as one rich line at [baseline] — byte-identical to
 * what the site drew before stacking existed.
 */
fun Canvas.drawDualLabel(
    label: DualLabel,
    x: Float,
    baseline: Float,
    paint: Paint,
    stacked: Boolean,
    style: FractionTextStyle = FractionTypography.active,
) {
    if (!label.setsStacked(stacked)) {
        drawRichText(label.inline(), x, baseline, paint, style)
        return
    }
    val advance = paint.dualStackMetrics().advance
    label.lines().forEachIndexed { i, line ->
        drawRichText(line, x, baseline + i * advance, paint, style)
    }
}

/**
 * [drawDualLabel] with both lines CENTRED on [cx], whatever the paint's own alignment.
 *
 * Sites that hand-centre a single label (`cx - width / 2` with a LEFT-aligned paint) get identical
 * pixels from this for a one-line value, and a properly centred pair for a stack — a stack centred
 * line-by-line is the only form that reads as one value; left-aligning the two terms of different
 * widths reads as two.
 */
fun Canvas.drawDualLabelCentered(
    label: DualLabel,
    cx: Float,
    baseline: Float,
    paint: Paint,
    stacked: Boolean,
    style: FractionTextStyle = FractionTypography.active,
) {
    val prev = paint.textAlign
    paint.textAlign = Paint.Align.CENTER
    drawDualLabel(label, cx, baseline, paint, stacked, style)
    paint.textAlign = prev
}
