package com.android.shaftschematic.pdf.render

import android.graphics.Canvas
import android.graphics.Paint
import com.android.shaftschematic.pdf.dim.DimSpan

/**
 * Stacked dimension rails with extension lines. Arrows are ticks (arrowheads later).
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
    private val tickSize: Float = 7f
) {
    fun drawTop(canvas: Canvas, span: DimSpan, drawExtensions: Boolean = true) {
        drawSpan(canvas, span, topRailY, drawExtensions)
    }

    fun drawOnRail(canvas: Canvas, railIndex: Int, span: DimSpan, drawExtensions: Boolean = true) {
        val y = baseY - railDy * railIndex
        drawSpan(canvas, span, y, drawExtensions)
    }

    private fun drawSpan(canvas: Canvas, span: DimSpan, y: Float, drawExtensions: Boolean) {
        val x1 = pageX(span.x1Mm)
        val x2 = pageX(span.x2Mm)

        canvas.drawLine(x1, y, x2, y, linePaint)
        tick(canvas, x1, y, true)
        tick(canvas, x2, y, false)

        if (drawExtensions) {
            val extTop = objectTopY - objectClearance
            canvas.drawLine(x1, extTop, x1, y, linePaint)
            canvas.drawLine(x2, extTop, x2, y, linePaint)
        }

        val mid = (x1 + x2) * 0.5f
        canvas.drawText(span.labelTop, mid, y - textAboveDy, textPaint)
        span.labelBottom?.let { canvas.drawText(it, mid, y + textBelowDy, textPaint) }
    }

    private fun tick(canvas: Canvas, x: Float, y: Float, left: Boolean) {
        val s = tickSize
        val dx = if (left) -s else s
        canvas.drawLine(x, y, x + dx, y - s, linePaint)
        canvas.drawLine(x, y, x + dx, y + s, linePaint)
    }
}
