package com.android.shaftschematic.pdf.render

import android.graphics.Canvas
import android.graphics.Paint
import com.android.shaftschematic.geom.DimensionRailLayout
import com.android.shaftschematic.pdf.DIM_BREAK_TEXT_PAD_PT
import com.android.shaftschematic.pdf.dim.DimSpan
import com.android.shaftschematic.util.DualLabel
import com.android.shaftschematic.util.drawDualLabel
import com.android.shaftschematic.util.dualStackMetrics
import com.android.shaftschematic.util.measureDualLabel
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Arrowhead barb spread as a fraction of its length — a 2:1 head, the drafting proportion. */
private const val ARROW_BARB_FRAC = 0.5f

/**
 * Stacked dimension rails with extension lines, in-line labels, and smart arrowheads.
 * Uses the same world→page X mapper as geometry so spans align with parts.
 *
 * The dimension value is seated in a BREAK in the line (drafting convention): the main line
 * is drawn as two stubs [xa..gapLeft] and [gapRight..xb] with the value vertically centered on
 * the line in the gap. Spans too short to keep an inward arrowhead on both stubs fall back to a
 * continuous line with the value floating above it.
 *
 * Arrow direction is a SEPARATE question from where the value landed: a fallback span still gets
 * inward heads, and only one too narrow to hold both between its extension lines prints them
 * tips-in ([DimensionRailLayout.arrowsPointInward]).
 *
 * Placement is not decided here: the pure [DimensionRailLayout] plans every span at once so
 * labels and rail lines across ALL tiers share one collision space (a floating value lives in
 * the next rail's band). This class measures the text, maps the spans to page x, and draws the
 * planned result — the plan owns inline-vs-above, the label center, and the rail lift.
 *
 * Blank-draft mode ([blankLabels]): the break is still cut — at a writable width — but no value
 * text is drawn, leaving the gap as a hand-write-in spot. A span too short for the full gap
 * cuts a narrower one instead of losing it, down to [blankLabelMinWidthPx].
 */
class PdfDimensionRenderer(
    private val pageX: (Double) -> Float,   // mm → page X
    private val linePaint: Paint,           // strokes for lines/ticks/arrows
    private val textPaint: Paint,           // text paint (fill)
    private val objectTopY: Float,          // top of shaft outline in page coords
    private val objectClearance: Float = 6f,
    private val textAboveDy: Float = 12f,   // fallback (label-above-line) baseline offset; primary path seats the label in the line break
    private val arrowSize: Float = 4f,      // arrowhead length along the line
    /**
     * Left/right clearance between the value and the line stubs it sits between — the same
     * `DIM_BREAK_TEXT_PAD_PT` the wear/undercut strip rails cut, so one gap convention serves
     * every rail in the app.
     *
     * It is not only cosmetic: the break costs `labelWidth + 2·textPad`, and a span must afford
     * that plus an arrowhead on each stub to seat its value inline at all. Padding wider than
     * the value itself pushes short spans into the above-line fallback for no gain in legibility
     * (on-device report: a value that plainly fitted its rail printed above it).
     */
    private val textPad: Float = DIM_BREAK_TEXT_PAD_PT,
    private val blankLabels: Boolean = false, // blank-draft: cut the break, draw no value text
    private val blankLabelWidthPx: Float = 46f,
    private val blankLabelMinWidthPx: Float = 28f, // smallest write-in gap still worth cutting
    /**
     * Set every dual value as a two-line stack instead of one `primary [secondary]` line.
     *
     * Sheet-wide, never per label: `dual_units` is a document flag, so either every value here has
     * a second term or none does, and the planner needs exactly ONE text height per plan. A stack
     * is ~55% narrower, so it seats in the line break far more often — which is what pays for the
     * taller band ([metrics]). See `docs/DualUnitStacking_PLAN.md`.
     */
    private val dualStacked: Boolean = false,
) {
    /**
     * The width this renderer occupies for a value — blank drafts reserve the write-in gap,
     * shrunk toward [blankLabelMinWidthPx] on a span too short for the full one so it still
     * gets somewhere to write. Both the planner (via [spanInput]) and the break in
     * [drawPlanned] read this, so the reserved box and the cut gap are the same width.
     */
    fun labelWidth(span: DimSpan): Float =
        if (blankLabels) {
            DimensionRailLayout.blankGapWidth(
                spanWidth = abs(pageX(span.x2Mm) - pageX(span.x1Mm)),
                preferredWidth = blankLabelWidthPx,
                minWidth = blankLabelMinWidthPx,
                textPad = textPad,
                arrowSize = arrowSize,
            )
        } else {
            // Rich measure: a value carrying a fraction is set as a built-up stack, which is
            // NARROWER than the same characters inline. Measuring plain here would over-reserve
            // the break and leave the value floating off-centre in it. A stacked dual value
            // measures as its WIDER line, which is the whole point of stacking.
            textPaint.measureDualLabel(span.label, dualStacked)
        }

    /**
     * Text box metrics the planner needs, read live from this renderer's text paint.
     *
     * A stacked dual value is expressed by INFLATING the ascent by one line advance, and that is
     * the only vertical change the whole rail path needs: `height`, `band`, the per-rail lifts and
     * every collision box grow from it, and [drawLabel] still computes its baseline off the REAL
     * paint ascent — so the primary line lands exactly where the single line used to and the
     * secondary fills the room the inflation reserved. One definition of the advance
     * (`Paint.dualStackMetrics`) serves both sides, so they cannot drift.
     */
    fun metrics(): DimensionRailLayout.TextMetrics {
        val fm = textPaint.fontMetrics
        val extra = if (dualStacked) textPaint.dualStackMetrics().advance else 0f
        return DimensionRailLayout.TextMetrics(
            ascent = fm.ascent - extra,
            descent = fm.descent,
            aboveDy = textAboveDy,
        )
    }

    /**
     * The vertical room one value needs, gap included — the label band. A composer reads this to
     * derive its lane pitch: a lane narrower than this prints the neighbouring rail's line through
     * the value (`minRailGap`, `docs/DualUnitStacking_PLAN.md` §6).
     */
    fun labelBand(): Float = metrics().band

    /** The value box height alone (no gap) — a stack is one line taller than a single value. */
    fun labelHeight(): Float = metrics().height

    /**
     * Planner input for one (rail, span) row. [railY] is the caller's UNLIFTED rail position;
     * the plan returns the lifted y to draw at. Use [DimensionRailLayout.TOP_RAIL] for the OAL rail.
     */
    fun spanInput(railIndex: Int, railY: Float, span: DimSpan): DimensionRailLayout.SpanInput {
        val x1 = pageX(span.x1Mm)
        val x2 = pageX(span.x2Mm)
        return DimensionRailLayout.SpanInput(
            railIndex = railIndex,
            railY = railY,
            xa = min(x1, x2),
            xb = max(x1, x2),
            labelWidth = labelWidth(span),
        )
    }

    /**
     * Extra height the rail block needs: one label band per rail carrying an above-line value.
     * Answerable from x-geometry alone, so a composer can size its vertical budget before it
     * places the rails.
     */
    fun topLift(inputs: List<DimensionRailLayout.SpanInput>): Float =
        DimensionRailLayout.topLift(inputs, metrics(), arrowSize, textPad)

    /** Plans placements parallel to [inputs]; vertical bumps stay below [safeTopY]. */
    fun plan(
        inputs: List<DimensionRailLayout.SpanInput>,
        safeTopY: Float = Float.NEGATIVE_INFINITY,
    ): DimensionRailLayout.Plan =
        DimensionRailLayout.plan(inputs, metrics(), arrowSize, textPad, safeTopY)

    /** Draws one planned span: extension lines, the (broken or continuous) line, value, arrows. */
    fun drawPlanned(
        canvas: Canvas,
        span: DimSpan,
        placement: DimensionRailLayout.Placement,
        drawExtensions: Boolean = true,
    ) {
        val x1 = pageX(span.x1Mm)
        val x2 = pageX(span.x2Mm)
        val xa = min(x1, x2)
        val xb = max(x1, x2)
        val y = placement.railY

        // extension lines (from object to rail with clearance) — independent of the label
        if (drawExtensions) {
            val extTop = objectTopY - objectClearance
            canvas.drawLine(xa, extTop, xa, y, linePaint)
            canvas.drawLine(xb, extTop, xb, y, linePaint)
        }

        if (placement.inline) {
            // ---- primary path: value seated in a break in the line ----
            val half = labelWidth(span) * 0.5f
            canvas.drawLine(xa, y, placement.cx - half - textPad, y, linePaint)
            canvas.drawLine(placement.cx + half + textPad, y, xb, y, linePaint)
        } else {
            // ---- fallback path: continuous line, value floating above it ----
            canvas.drawLine(xa, y, xb, y, linePaint)
        }

        drawLabel(canvas, span.label, placement.label)

        // ---- arrowheads: outward only on a span too narrow to hold both (planner's call) ----
        drawArrow(canvas, xAt = xa, y = y, inward = placement.arrowsInward, isLeftEnd = true)
        drawArrow(canvas, xAt = xb, y = y, inward = placement.arrowsInward, isLeftEnd = false)
    }

    private fun drawLabel(canvas: Canvas, label: DualLabel, bounds: DimensionRailLayout.Box) {
        if (blankLabels) return  // the gap itself is the write-in spot; the plan still reserved it
        // The REAL paint ascent, deliberately — see [metrics]. On a stacked sheet the box top is
        // one advance higher than a single line's would be, so this baseline is the PRIMARY line's
        // and `drawDualLabel` steps down to the secondary from there.
        val baseline = bounds.top - textPaint.fontMetrics.ascent
        val prevAlign = textPaint.textAlign
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawDualLabel(label, bounds.centerX, baseline, textPaint, dualStacked)
        textPaint.textAlign = prevAlign
    }

    private fun drawArrow(canvas: Canvas, xAt: Float, y: Float, inward: Boolean, isLeftEnd: Boolean) {
        val s = arrowSize
        // Inward: left end points ➜ (to the right); right end points ⬅︎ (to the left).
        // Outward: left end points ⬅︎ ; right end points ➜.
        val dir = when {
            inward && isLeftEnd -> +1f
            inward && !isLeftEnd -> -1f
            !inward && isLeftEnd -> -1f
            else -> +1f
        }
        // Slim V, half as tall as it is long — the drafting proportion, and the same head the
        // wear/undercut strip rails draw. A 1:1 head reads as a fat blob at sheet scale.
        val barb = s * ARROW_BARB_FRAC
        canvas.drawLine(xAt, y, xAt + dir * s, y - barb, linePaint)
        canvas.drawLine(xAt, y, xAt + dir * s, y + barb, linePaint)
    }
}
