package com.android.shaftschematic.pdf.render

import android.graphics.Canvas
import android.graphics.Paint
import com.android.shaftschematic.pdf.dim.DimSpan
import kotlin.math.max
import kotlin.math.min

/**
 * Stacked dimension rails with extension lines, centered labels, and smart arrowheads.
 * Uses the same world→page X mapper as geometry so spans align with parts.
 */
class PdfDimensionRenderer(
    private val pageX: (Double) -> Float,   // mm → page X
    private val baseY: Float,
    private val railDy: Float,
    private val topRailY: Float,
    private val linePaint: Paint,           // strokes for lines/ticks/arrows
    private val textPaint: Paint,           // text paint (fill)
    private val objectTopY: Float,          // top of shaft outline in page coords
    private val objectClearance: Float = 6f,
    private val textAboveDy: Float = 12f,
    private val textBelowDy: Float = 14f,
    private val arrowSize: Float = 7f,      // arrowhead half-size
    private val textPad: Float = 6f,        // left/right text padding inside a span
    private val minGap: Float = 8f,         // minimum gap between labels on same rail
    private val lineAdvance: Float = 10f    // vertical bump when span too tight
) {
    // track last label's right edge and current bump level per rail (page X coords)
    private val lastRightByRail = mutableMapOf<Int, Float>()
    private val bumpLevelByRail = mutableMapOf<Int, Int>()

    init {
        // Center align so drawText(x, y) uses x as center
        textPaint.textAlign = Paint.Align.CENTER
    }

    fun drawTop(canvas: Canvas, span: DimSpan, drawExtensions: Boolean = true) {
        drawSpan(canvas, railIndex = -1, span = span, y = topRailY, drawExtensions = drawExtensions)
    }

    fun drawOnRail(canvas: Canvas, railIndex: Int, span: DimSpan, drawExtensions: Boolean = true) {
        val y = baseY - railDy * railIndex
        drawSpan(canvas, railIndex, span, y, drawExtensions)
    }

    private fun drawSpan(canvas: Canvas, railIndex: Int, span: DimSpan, y: Float, drawExtensions: Boolean) {
        val x1 = pageX(span.x1Mm)
        val x2 = pageX(span.x2Mm)
        val xa = min(x1, x2)
        val xb = max(x1, x2)

        // main dimension line
        canvas.drawLine(xa, y, xb, y, linePaint)

        // extension lines (from object to rail with clearance)
        if (drawExtensions) {
            val extTop = objectTopY - objectClearance
            canvas.drawLine(xa, extTop, xa, y, linePaint)
            canvas.drawLine(xb, extTop, xb, y, linePaint)
        }

        // ---- centered label placement with clamping + min-gap + bump ----
        val label = span.labelTop
        val w = textPaint.measureText(label)
        val mid = (xa + xb) * 0.5f

        // keep label center inside [xa+pad+w/2, xb-pad-w/2]
        val half = w * 0.5f
        val leftBoundCenter  = xa + textPad + half
        val rightBoundCenter = xb - textPad - half
        var cx = if (leftBoundCenter > rightBoundCenter) mid else clamp(mid, leftBoundCenter, rightBoundCenter)

        // enforce min gap to the previous label on this rail
        val lastRight = lastRightByRail[railIndex] ?: Float.NEGATIVE_INFINITY
        val currentLeft = cx - half
        if (currentLeft < lastRight + minGap) {
            val shiftedCenter = (lastRight + minGap) + half
            cx = clamp(shiftedCenter, leftBoundCenter, rightBoundCenter)
        }

        // if still overlapping (span too short), bump this label upward
        val stillTight = (cx - half) < (lastRight + minGap)
        val bumpedLevels = if (stillTight) (bumpLevelByRail[railIndex] ?: 0) + 1 else (bumpLevelByRail[railIndex] ?: 0)
        bumpLevelByRail[railIndex] = bumpedLevels

        val yTopText = y - textAboveDy - bumpedLevels * lineAdvance
        canvas.drawText(label, cx, yTopText, textPaint)

        // bottom label (e.g., SET name) stays under the rail for predictability
        span.labelBottom?.let {
            canvas.drawText(it, (xa + xb) * 0.5f, y + textBelowDy, textPaint)
        }

        // update lastRight to this label's right edge
        lastRightByRail[railIndex] = max(lastRight, cx + half)

        // ---- arrowheads: inward by default, outward only when cramped ----
        val needInward = canFitInwardArrows(xa, xb, cx, w, textPad, arrowSize)
        drawArrow(canvas, xAt = xa, y = y, inward = needInward, isLeftEnd = true)
        drawArrow(canvas, xAt = xb, y = y, inward = needInward, isLeftEnd = false)
    }

    private fun canFitInwardArrows(xa: Float, xb: Float, cx: Float, w: Float, pad: Float, s: Float): Boolean {
        // Require enough leftover line length on both sides of the label window to place arrowheads pointing inward.
        // Label window = [cx - w/2 - pad, cx + w/2 + pad]
        val leftWindow = cx - w * 0.5f - pad
        val rightWindow = cx + w * 0.5f + pad
        val leftRoom = leftWindow - xa
        val rightRoom = xb - rightWindow
        // need at least arrowSize margin on each side
        return leftRoom >= s * 1.5f && rightRoom >= s * 1.5f
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

    private fun clamp(v: Float, lo: Float, hi: Float): Float = min(max(v, lo), hi)
}
