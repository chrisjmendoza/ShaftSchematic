package com.android.shaftschematic.pdf.render

import android.graphics.Canvas
import android.graphics.Paint
import com.android.shaftschematic.geom.DimensionRailLayout
import com.android.shaftschematic.pdf.dim.DimSpan
import kotlin.math.max
import kotlin.math.min

/**
 * Stacked dimension rails with extension lines, in-line labels, and smart arrowheads.
 * Uses the same world→page X mapper as geometry so spans align with parts.
 *
 * The dimension value is seated in a BREAK in the line (drafting convention): the main line
 * is drawn as two stubs [xa..gapLeft] and [gapRight..xb] with the value vertically centered on
 * the line in the gap. Spans too short to keep an inward arrowhead on both stubs fall back to a
 * continuous line with the value floating above it.
 *
 * Placement is not decided here: the pure [DimensionRailLayout] plans every span at once so
 * labels and rail lines across ALL tiers share one collision space (a floating value lives in
 * the next rail's band). This class measures the text, maps the spans to page x, and draws the
 * planned result — the plan owns inline-vs-above, the label center, and the rail lift.
 *
 * Blank-draft mode ([blankLabels]): the break is still cut — at a fixed writable width — but no
 * value text is drawn, leaving the gap as a hand-write-in spot.
 */
class PdfDimensionRenderer(
    private val pageX: (Double) -> Float,   // mm → page X
    private val linePaint: Paint,           // strokes for lines/ticks/arrows
    private val textPaint: Paint,           // text paint (fill)
    private val objectTopY: Float,          // top of shaft outline in page coords
    private val objectClearance: Float = 6f,
    private val textAboveDy: Float = 12f,   // fallback (label-above-line) baseline offset; primary path seats the label in the line break
    private val arrowSize: Float = 5f,      // arrowhead half-size
    private val textPad: Float = 6f,        // left/right text padding inside a span
    private val blankLabels: Boolean = false, // blank-draft: cut the break, draw no value text
    private val blankLabelWidthPx: Float = 46f
) {
    /** The width this renderer occupies for a value — blank drafts reserve the write-in gap. */
    fun labelWidth(span: DimSpan): Float =
        if (blankLabels) blankLabelWidthPx else textPaint.measureText(span.labelTop)

    /** Text box metrics the planner needs, read live from this renderer's text paint. */
    fun metrics(): DimensionRailLayout.TextMetrics {
        val fm = textPaint.fontMetrics
        return DimensionRailLayout.TextMetrics(
            ascent = fm.ascent,
            descent = fm.descent,
            aboveDy = textAboveDy,
        )
    }

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

        drawLabel(canvas, span.labelTop, placement.label)

        // ---- arrowheads: inward whenever the value is seated in the break ----
        drawArrow(canvas, xAt = xa, y = y, inward = placement.inline, isLeftEnd = true)
        drawArrow(canvas, xAt = xb, y = y, inward = placement.inline, isLeftEnd = false)
    }

    private fun drawLabel(canvas: Canvas, label: String, bounds: DimensionRailLayout.Box) {
        if (blankLabels) return  // the gap itself is the write-in spot; the plan still reserved it
        val baseline = bounds.top - textPaint.fontMetrics.ascent
        val prevAlign = textPaint.textAlign
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(label, bounds.centerX, baseline, textPaint)
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
        // small V arrow
        canvas.drawLine(xAt, y, xAt + dir * s, y - s, linePaint)
        canvas.drawLine(xAt, y, xAt + dir * s, y + s, linePaint)
    }
}
