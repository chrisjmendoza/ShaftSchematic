package com.android.shaftschematic.ui.drawing.render

import android.graphics.*

class ShaftRenderer {
    fun render(canvas: Canvas, layout: LayoutResult, opts: RenderOptions) {
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK; strokeWidth = opts.lineWidthPx; style = Paint.Style.STROKE
        }
        val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK; strokeWidth = opts.dimLineWidthPx; style = Paint.Style.STROKE
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK; textSize = opts.textSizePx; textAlign = Paint.Align.CENTER
        }
        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.LTGRAY; strokeWidth = 1f; style = Paint.Style.STROKE
            pathEffect = DashPathEffect(floatArrayOf(8f, 8f), 0f)
        }

        // Grid
        layout.grid.forEach { g -> if (g is Prim.Line) canvas.drawLine(g.x1, g.y1, g.x2, g.y2, gridPaint) }

        // Axis
        canvas.drawLine(layout.shaftAxis.x1, layout.shaftAxis.y1, layout.shaftAxis.x2, layout.shaftAxis.y2, linePaint)

        // Parts
        layout.parts.forEach { p ->
            when (p) {
                is Prim.Rect -> canvas.drawRect(p.left, p.top, p.right, p.bottom, linePaint)
                is Prim.Line -> canvas.drawLine(p.x1, p.y1, p.x2, p.y2, linePaint)
                is Prim.Path -> {
                    val path = Path().apply {
                        if (p.points.isNotEmpty()) moveTo(p.points.first().first, p.points.first().second)
                        for (i in 1 until p.points.size) {
                            val (x, y) = p.points[i]; lineTo(x, y)
                        }
                        if (p.close) close()
                    }
                    canvas.drawPath(path, linePaint)
                }
                is Prim.Text -> { /* skip here */ }
            }
        }

        // Dims
        layout.dims.forEach { d ->
            when (d) {
                is Prim.Line -> canvas.drawLine(d.x1, d.y1, d.x2, d.y2, dimPaint)
                is Prim.Rect -> canvas.drawRect(d.left, d.top, d.right, d.bottom, dimPaint)
                is Prim.Path -> {
                    val path = Path().apply {
                        if (d.points.isNotEmpty()) moveTo(d.points.first().first, d.points.first().second)
                        for (i in 1 until d.points.size) { val (x, y) = d.points[i]; lineTo(x, y) }
                        if (d.close) close()
                    }
                    canvas.drawPath(path, dimPaint)
                }
                is Prim.Text -> { /* skip here */ }
            }
        }

        // Labels
        layout.labels.forEach { t -> canvas.drawText(t.text, t.x, t.y, textPaint) }
    }
}
