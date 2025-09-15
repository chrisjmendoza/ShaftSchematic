package com.android.shaftschematic.ui.drawing.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.android.shaftschematic.data.BodySegmentSpec
import com.android.shaftschematic.data.LinerSpec
import com.android.shaftschematic.data.ShaftSpecMm
import com.android.shaftschematic.data.TaperSpec
import com.android.shaftschematic.data.ThreadSpec
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Renders the shaft geometry and (optionally) a units-aware grid with legend.
 */
class ShaftRenderer {

    // ======= paints =======
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        strokeJoin = Paint.Join.MITER
        strokeCap = Paint.Cap.BUTT
        color = 0xFF000000.toInt()
    }

    private val thinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        strokeCap = Paint.Cap.BUTT
        color = 0xFF000000.toInt()
    }

    private val hatchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = 0xFF000000.toInt()
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
        color = 0x88000000.toInt()
        strokeWidth = 1.5f
    }

    private val legendTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF000000.toInt()
        textSize = 28f
    }

    private val legendStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFF000000.toInt()
        strokeWidth = 2f
    }

    /**
     * Main entry point: draws grid (optional) then geometry.
     */
    fun render(canvas: Canvas, layout: ShaftLayout.Result, opts: RenderOptions) {
        // Line widths/colors
        outlinePaint.strokeWidth = opts.lineWidthPx
        thinPaint.strokeWidth = max(1f, opts.dimLineWidthPx)

        // --- GRID ---
        if (opts.showGrid) {
            val info = drawGrid(canvas, layout, opts)
            if (opts.showGridLegend) drawScaleLegend(canvas, layout, opts, info)
        }

        // --- GEOMETRY ---
        val spec: ShaftSpecMm = layout.spec
        spec.bodies.forEach { drawBodySegment(canvas, layout, opts, it) }
        spec.tapers.forEach { drawTaperSegment(canvas, layout, opts, it) }
        spec.aftTaper?.let { drawTaperSegment(canvas, layout, opts, it) }
        spec.forwardTaper?.let { drawTaperSegment(canvas, layout, opts, it) }
        spec.threads.forEach { drawThreadSegment(canvas, layout, opts, it) }
        spec.liners.forEach { drawLinerSegment(canvas, layout, opts, it) }
    }

    // ======= geometry =======

    private fun drawBodySegment(
        canvas: Canvas,
        layout: ShaftLayout.Result,
        opts: RenderOptions,
        b: BodySegmentSpec
    ) {
        val pxPerMm = layout.pxPerMm
        val x0 = layout.contentLeftPx + b.startFromAftMm * pxPerMm
        val x1 = x0 + b.lengthMm * pxPerMm
        val cy = layout.centerlineYPx
        val r = (b.diaMm * pxPerMm) * 0.5f
        val yTop = cy - r
        val yBot = cy + r

        canvas.drawLine(x0, yTop, x1, yTop, outlinePaint)
        canvas.drawLine(x0, yBot, x1, yBot, outlinePaint)
        canvas.drawLine(x0, yTop, x0, yBot, outlinePaint)
        canvas.drawLine(x1, yTop, x1, yBot, outlinePaint)
    }

    private fun drawTaperSegment(
        canvas: Canvas,
        layout: ShaftLayout.Result,
        opts: RenderOptions,
        t: TaperSpec
    ) {
        val pxPerMm = layout.pxPerMm
        val x0 = layout.contentLeftPx + t.startFromAftMm * pxPerMm
        val x1 = x0 + t.lengthMm * pxPerMm
        val cy = layout.centerlineYPx
        val r0 = (t.startDiaMm * pxPerMm) * 0.5f
        val r1 = (t.endDiaMm * pxPerMm) * 0.5f

        val path = Path().apply {
            moveTo(x0, cy - r0)
            lineTo(x1, cy - r1)
            lineTo(x1, cy + r1)
            lineTo(x0, cy + r0)
            close()
        }
        canvas.drawPath(path, outlinePaint)
    }

    private fun drawThreadSegment(
        canvas: Canvas,
        layout: ShaftLayout.Result,
        opts: RenderOptions,
        th: ThreadSpec
    ) {
        val pxPerMm = layout.pxPerMm
        val x0 = layout.contentLeftPx + th.startFromAftMm * pxPerMm
        val x1 = x0 + th.lengthMm * pxPerMm
        val cy = layout.centerlineYPx
        val r = (th.majorDiaMm * pxPerMm) * 0.5f
        val yTop = cy - r
        val yBot = cy + r

        // Box
        canvas.drawLine(x0, yTop, x1, yTop, outlinePaint)
        canvas.drawLine(x0, yBot, x1, yBot, outlinePaint)
        canvas.drawLine(x0, yTop, x0, yBot, outlinePaint)
        canvas.drawLine(x1, yTop, x1, yBot, outlinePaint)

        // Hatch using pitch (fallback to ~3mm)
        val pitchMm = if (th.pitchMm > 0f) th.pitchMm else 3f
        val stepPx = pitchMm * pxPerMm
        var x = x0 + stepPx * 0.5f
        val span = x1 - x0
        val maxLines = 500 // safety
        var count = 0
        while (x < x1 - 1f && count < maxLines) {
            canvas.drawLine(x - stepPx * 0.3f, yTop, x + stepPx * 0.3f, yBot, thinPaint)
            x += stepPx
            count++
        }
    }

    private fun drawLinerSegment(
        canvas: Canvas,
        layout: ShaftLayout.Result,
        opts: RenderOptions,
        ln: LinerSpec
    ) {
        val pxPerMm = layout.pxPerMm
        val x0 = layout.contentLeftPx + ln.startFromAftMm * pxPerMm
        val x1 = x0 + ln.lengthMm * pxPerMm
        val cy = layout.centerlineYPx
        val r = (ln.odMm * pxPerMm) * 0.5f
        val yTop = cy - r
        val yBot = cy + r

        canvas.drawLine(x0, yTop, x1, yTop, outlinePaint)
        canvas.drawLine(x0, yBot, x1, yBot, outlinePaint)
        canvas.drawLine(x0, yTop, x0, yBot, outlinePaint)
        canvas.drawLine(x1, yTop, x1, yBot, outlinePaint)
    }

    // ======= grid & legend =======

    private data class GridInfo(
        val majorStepMm: Float,
        val minorStepMm: Float,
        val majorsCount: Int
    )

    /**
     * Draws a units-aware grid that targets ~opts.gridDesiredMajors across content width.
     * Minor lines subdivide each major by (gridMinorsPerMajor + 1) segments.
     * Respects a minimum pixel gap to avoid visual mush.
     */
    private fun drawGrid(canvas: Canvas, layout: ShaftLayout.Result, opts: RenderOptions): GridInfo {
        val left = layout.contentLeftPx
        val right = layout.contentRightPx
        val top = layout.contentTopPx
        val bottom = layout.contentBottomPx
        val pxPerMm = layout.pxPerMm

        val majorsDesired = max(1, opts.gridDesiredMajors)
        val minorsPerMajor = max(0, opts.gridMinorsPerMajor)

        // Content width in mm
        val contentWidthMm = (right - left) / pxPerMm

        // Ideal major step to achieve ~N majors
        var majorStepMm = contentWidthMm / majorsDesired
        majorStepMm = if (opts.gridUseInches) snapNiceInchStepMm(majorStepMm)
        else snapNiceMetricStepMm(majorStepMm)

        // Minor step from major step
        val baseMinorStepMm = if (minorsPerMajor > 0) majorStepMm / (minorsPerMajor + 1) else majorStepMm

        // Enforce a minimum pixel gap between drawn lines
        val baseMinorPx = baseMinorStepMm * pxPerMm
        val multiplier = if (baseMinorPx < opts.gridMinPixelGap)
            ceil(opts.gridMinPixelGap / baseMinorPx).toInt().coerceAtLeast(1)
        else 1
        val minorStepMm = baseMinorStepMm * multiplier
        val minorStepPx = minorStepMm * pxPerMm

        // Keep major alignment by using an integer number of minor steps per major
        val minorsPerMajorFinal = max(1, (majorStepMm / minorStepMm).roundToInt())
        val majorEveryMinor = minorsPerMajorFinal
        val majorStepPx = minorStepPx * majorEveryMinor

        // Vertical lines
        run {
            var i = 0
            var x = left
            while (x <= right + 0.5f) {
                val isMajor = (i % majorEveryMinor == 0)
                gridPaint.strokeWidth = if (isMajor) opts.gridMajorStrokePx else opts.gridMinorStrokePx
                gridPaint.color = if (isMajor) opts.gridMajorColor else opts.gridMinorColor
                canvas.drawLine(x, top, x, bottom, gridPaint)
                i++
                x += minorStepPx
            }
        }
        // Horizontal lines
        run {
            var j = 0
            var y = top
            while (y <= bottom + 0.5f) {
                val isMajor = (j % majorEveryMinor == 0)
                gridPaint.strokeWidth = if (isMajor) opts.gridMajorStrokePx else opts.gridMinorStrokePx
                gridPaint.color = if (isMajor) opts.gridMajorColor else opts.gridMinorColor
                canvas.drawLine(left, y, right, y, gridPaint)
                j++
                y += minorStepPx
            }
        }

        // Estimate majors count (may differ slightly after snapping)
        val actualMajors = floor((right - left) / majorStepPx).toInt().coerceAtLeast(1)

        return GridInfo(
            majorStepMm = minorsPerMajorFinal * minorStepMm,
            minorStepMm = minorStepMm,
            majorsCount = actualMajors
        )
    }

    /** Legend: Major, Minor, Content Width, and a 1-major scale bar (upper-right). */
    private fun drawScaleLegend(
        canvas: Canvas,
        layout: ShaftLayout.Result,
        opts: RenderOptions,
        info: GridInfo
    ) {
        val inch = 25.4f

        val contentWidthMm = (layout.contentRightPx - layout.contentLeftPx) / layout.pxPerMm
        val widthLabel = if (opts.gridUseInches) {
            val wIn = contentWidthMm / inch
            "Width: ${"%.2f".format(wIn)} in (${info.majorsCount} majors)"
        } else {
            val wStr = if (contentWidthMm >= 100f) "${contentWidthMm.toInt()}" else "%.1f".format(contentWidthMm)
            "Width: $wStr mm (${info.majorsCount} majors)"
        }

        val majorLabel = if (opts.gridUseInches) {
            val majIn = info.majorStepMm / inch
            "Major: ${formatInches(majIn)}"
        } else {
            "Major: ${formatMm(info.majorStepMm)}"
        }

        val minorLabel = if (opts.gridUseInches) {
            val minIn = info.minorStepMm / inch
            "Minor: ${formatInches(minIn)}"
        } else {
            "Minor: ${formatMm(info.minorStepMm)}"
        }

        val right = layout.contentRightPx
        val top = layout.contentTopPx
        val pad = opts.legendPaddingPx.toFloat()

        val textSize = if (opts.legendTextSizePx > 0f) opts.legendTextSizePx else (opts.textSizePx * 0.6f)
        legendTextPaint.textSize = textSize
        legendTextPaint.color = opts.legendTextColor

        val lineGap = textSize * 1.1f
        val label1 = majorLabel
        val label2 = minorLabel
        val label3 = widthLabel

        val widthText = maxOf(
            legendTextPaint.measureText(label1),
            legendTextPaint.measureText(label2),
            legendTextPaint.measureText(label3)
        )

        val barLenPx = info.majorStepMm * layout.pxPerMm
        val barHeight = opts.legendBarHeightPx

        val boxW = max(widthText, barLenPx) + pad * 2
        val boxH = lineGap * 3 + barHeight + pad * 4
        val boxLeft = right - boxW - pad
        val boxTop = top + pad

        // Optional outline: uncomment if you want a box
        // canvas.drawRect(boxLeft, boxTop, boxLeft + boxW, boxTop + boxH, legendStroke)

        // Texts
        val textX = boxLeft + pad
        var textY = boxTop + pad + textSize
        canvas.drawText(label1, textX, textY, legendTextPaint)
        textY += lineGap
        canvas.drawText(label2, textX, textY, legendTextPaint)
        textY += lineGap
        canvas.drawText(label3, textX, textY, legendTextPaint)

        // Scale bar
        val barX = boxLeft + pad
        val barY = textY + pad
        legendStroke.strokeWidth = opts.gridMajorStrokePx
        canvas.drawLine(barX, barY, barX + barLenPx, barY, legendStroke)
        val tickH = barHeight
        canvas.drawLine(barX, barY - tickH, barX, barY + tickH, legendStroke)
        canvas.drawLine(barX + barLenPx, barY - tickH, barX + barLenPx, barY + tickH, legendStroke)
    }

    // ======= helpers =======

    /** Snap to friendly metric step sizes around the target (mm). */
    private fun snapNiceMetricStepMm(targetMm: Float): Float {
        if (targetMm <= 0f) return 1f
        val decade = 10f.pow(floor(log10(targetMm)))
        val bases = floatArrayOf(1f, 2f, 2.5f, 5f, 10f, 12.5f, 20f, 25f, 50f, 100f)
        var best = bases[0] * decade
        var bestErr = abs(best - targetMm)
        for (b in bases) {
            val v = b * decade
            val e = abs(v - targetMm)
            if (e < bestErr) { best = v; bestErr = e }
        }
        return best
    }

    /** Snap to friendly inch steps (1/16, 1/8, 1/4, 1/2, 1, 2, 4, 6, 8 in) but return **mm**. */
    private fun snapNiceInchStepMm(targetMm: Float): Float {
        val inch = 25.4f
        val candidatesIn = floatArrayOf(1f/16f, 1f/8f, 1f/4f, 1f/2f, 1f, 2f, 3f, 4f, 6f, 8f)
        var best = candidatesIn[0] * inch
        var bestErr = abs(best - targetMm)
        for (c in candidatesIn) {
            val v = c * inch
            val e = abs(v - targetMm)
            if (e < bestErr) { best = v; bestErr = e }
        }
        return best
    }

    private fun formatMm(v: Float): String =
        if (v >= 100f) "${v.toInt()} mm" else String.format("%.1f mm", v)

    private fun formatInches(vIn: Float): String {
        val s = String.format("%.3f in", vIn)
        // trim trailing zeros
        return s.replace(Regex("(\\.\\d*?)0+ in$"), "$1 in")
            .replace(Regex("\\. in$"), " in")
    }
}
