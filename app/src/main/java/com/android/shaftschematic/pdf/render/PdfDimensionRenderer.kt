package com.android.shaftschematic.pdf.render

import android.graphics.Canvas
import android.graphics.Paint
import com.android.shaftschematic.pdf.dim.DimSpan
import kotlin.math.max
import kotlin.math.min

/**
 * Stacked dimension rails with extension lines and simple label de-collision.
 * Uses the same world→page X mapper as geometry so spans align with parts.
 */
class PdfDimensionRenderer(
    private val pageX: (Double) -> Float,   // mm → page X
    private val baseY: Float,
    private val railDy: Float,
    private val topRailY: Float,
    private val linePaint: Paint,           // strokes for lines/ticks
    private val textPaint: Paint,           // text paint (fill)
    private val objectTopY: Float,          // top of shaft outline in page coords
    private val objectClearance: Float = 6f,
    private val textAboveDy: Float = 12f,
    private val textBelowDy: Float = 14f,
    private val tickSize: Float = 7f,
    private val textPad: Float = 6f,        // left/right text padding inside a span
    private val minGap: Float = 8f,         // minimum gap between labels on same rail
    private val lineAdvance: Float = 10f    // vertical bump when span too tight
) {
    // track last label's right edge and current bump level per rail
    private val lastRightByRail = mutableMapOf<Int, Float>()
    private val bumpLevelByRail = mutableMapOf<Int, Int>()

    fun drawTop(canvas: Canvas, span: DimSpan, drawExtensions: Boolean = true) {
        // treat top rail as railIndex = -1 for independent state
        drawSpan(canvas, railIndex = -1, span = span, y = topRailY, drawExtensions = drawExtensions)
    }

    fun drawOnRail(canvas: Canvas, railIndex: Int, span: DimSpan, drawExtensions: Boolean = true) {
        val y = baseY - railDy * railIndex
        drawSpan(canvas, railIndex, span, y, drawExtensions)
    }

    private fun drawSpan(canvas: Canvas, railIndex: Int, span: DimSpan, y: Float, drawExtensions: Boolean) {
        val x1 = pageX(span.x1Mm)
        val x2 = pageX(span.x2Mm)

        // line + ticks
        canvas.drawLine(x1, y, x2, y, linePaint)
        tick(canvas, x1, y, true)
        tick(canvas, x2, y, false)

        // extension lines (from object to rail with clearance)
        if (drawExtensions) {
            val extTop = objectTopY - objectClearance
            canvas.drawLine(x1, extTop, x1, y, linePaint)
            canvas.drawLine(x2, extTop, x2, y, linePaint)
        }

        // ---- label placement with clamping + min-gap + bump ----
        val mid = (x1 + x2) * 0.5f
        val label = span.labelTop
        val w = textPaint.measureText(label)
        val half = w * 0.5f

        // keep label inside span bounds with padding
        val leftBound  = x1 + textPad + half
        val rightBound = x2 - textPad - half
        var xLabel = when {
            leftBound > rightBound -> mid // degenerate: very tight span
            else -> clamp(mid, leftBound, rightBound)
        }

        // enforce min gap to the previous label on this rail
        val lastRight = lastRightByRail[railIndex] ?: Float.NEGATIVE_INFINITY
        if (xLabel - half < lastRight + minGap) {
            xLabel = min(max(lastRight + minGap + half, leftBound), rightBound)
        }

        // if still overlapping (span too short), bump this label upward
        val bumpedLevels = if (xLabel - half < lastRight + minGap) {
            (bumpLevelByRail[railIndex] ?: 0) + 1
        } else {
            bumpLevelByRail[railIndex] ?: 0
        }
        bumpLevelByRail[railIndex] = bumpedLevels

        val yTopText = y - textAboveDy - bumpedLevels * lineAdvance
        canvas.drawText(label, xLabel, yTopText, textPaint)

        // bottom label (SET name) stays in fixed position to maximize predictability
        span.labelBottom?.let { canvas.drawText(it, (x1 + x2) * 0.5f, y + textBelowDy, textPaint) }

        // update state for this rail
        lastRightByRail[railIndex] = max(lastRight, xLabel + half)
    }

    private fun tick(canvas: Canvas, x: Float, y: Float, left: Boolean) {
        val s = tickSize
        val dx = if (left) -s else s
        canvas.drawLine(x, y, x + dx, y - s, linePaint)
        canvas.drawLine(x, y, x + dx, y + s, linePaint)
    }

    private fun clamp(v: Float, lo: Float, hi: Float): Float = min(max(v, lo), hi)
}
