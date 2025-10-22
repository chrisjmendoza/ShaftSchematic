package com.android.shaftschematic.pdf.render

import android.graphics.Canvas
import android.graphics.Paint
import com.android.shaftschematic.pdf.dim.DimSpan

class PdfDimensionRenderer(
    private val pxPerMm: Float,
    private val baseY: Float,
    private val railDy: Float,
    private val topRailY: Float,
    private val textAboveDy: Float = 10f,
    private val textBelowDy: Float = 12f
) {
    fun drawTop(canvas: Canvas, span: DimSpan, paint: Paint) {
        drawSpan(canvas, span, topRailY, paint)
    }

    fun drawOnRail(canvas: Canvas, railIndex: Int, span: DimSpan, paint: Paint) {
        val y = baseY - railDy * railIndex
        drawSpan(canvas, span, y, paint)
    }

    private fun drawSpan(canvas: Canvas, span: DimSpan, y: Float, p: Paint) {
        val x1 = (span.x1Mm * pxPerMm).toFloat()
        val x2 = (span.x2Mm * pxPerMm).toFloat()
        canvas.drawLine(x1, y, x2, y, p)
        tick(canvas, x1, y, p, true)
        tick(canvas, x2, y, p, false)

        val mid = ((x1 + x2) * 0.5f)
        canvas.drawText(span.labelTop, mid, y - textAboveDy, p)
        span.labelBottom?.let { canvas.drawText(it, mid, y + textBelowDy, p) }
    }

    private fun tick(canvas: Canvas, x: Float, y: Float, p: Paint, left: Boolean) {
        val s = 6f
        val dx = if (left) -s else s
        canvas.drawLine(x, y, x + dx, y - s, p)
        canvas.drawLine(x, y, x + dx, y + s, p)
    }
}
