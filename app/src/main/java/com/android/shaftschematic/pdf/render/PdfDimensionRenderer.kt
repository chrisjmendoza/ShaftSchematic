package com.android.shaftschematic.pdf.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
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
    private val contentTopPx: Float = Float.NEGATIVE_INFINITY,
    private val railSafePaddingPx: Float = 6f,
    private val objectClearance: Float = 6f,
    private val textAboveDy: Float = 12f,
    private val textBelowDy: Float = 14f,
    private val arrowSize: Float = 7f,      // arrowhead half-size
    private val textPad: Float = 6f,        // left/right text padding inside a span
    private val minGap: Float = 8f,         // reserved for future use; do not shift horizontally
    private val lineAdvance: Float = 10f    // reserved for future use; do not shift horizontally
) {
    private val labelBoundsByRail = mutableMapOf<Int, MutableList<RectF>>()

    init {
        // Intentionally do not mutate textPaint defaults here.
        // Alignment is set/restored locally when drawing labels.
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

        // ---- centered label placement with clamping + bounded bump ----
        val label = span.labelTop
        val w = textPaint.measureText(label)
        val mid = (xa + xb) * 0.5f

        // keep label center inside [xa+pad+w/2, xb-pad-w/2]
        val half = w * 0.5f
        val leftBoundCenter  = xa + textPad + half
        val rightBoundCenter = xb - textPad - half
        val cx = if (leftBoundCenter > rightBoundCenter) mid else clamp(mid, leftBoundCenter, rightBoundCenter)

        val fm = textPaint.fontMetrics
        val textHeightPx = fm.descent - fm.ascent
        val bumpStepPx = textHeightPx + LABEL_GAP_PX
        val safeTop = contentTopPx + railSafePaddingPx

        val existing = labelBoundsByRail.getOrPut(railIndex) { mutableListOf() }
        val labelBounds = centeredLabelBounds(cx, baselineY = y - textAboveDy, halfWidthPx = half, fm = fm)

        var placedBounds = RectF(labelBounds)
        var bumps = 0
        while (bumps < MAX_BUMPS && collidesWithAny(placedBounds, existing)) {
            placedBounds = placedBounds.offsetCopy(0f, -bumpStepPx)
            if (placedBounds.top < safeTop) {
                placedBounds = placedBounds.offsetCopy(0f, safeTop - placedBounds.top)
            }
            bumps++
        }

        drawLabelAtBounds(canvas, label, placedBounds, fm)
        existing += RectF(placedBounds)

        // bottom label (e.g., SET name) stays under the rail for predictability
        span.labelBottom?.let {
            val prevAlign = textPaint.textAlign
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(it, (xa + xb) * 0.5f, y + textBelowDy, textPaint)
            textPaint.textAlign = prevAlign
        }

        // ---- arrowheads: inward by default, outward only when cramped ----
        val needInward = canFitInwardArrows(xa, xb, cx, w, textPad, arrowSize)
        drawArrow(canvas, xAt = xa, y = y, inward = needInward, isLeftEnd = true)
        drawArrow(canvas, xAt = xb, y = y, inward = needInward, isLeftEnd = false)
    }

    private fun centeredLabelBounds(cx: Float, baselineY: Float, halfWidthPx: Float, fm: Paint.FontMetrics): RectF {
        val left = cx - halfWidthPx
        val right = cx + halfWidthPx
        val top = baselineY + fm.ascent
        val bottom = baselineY + fm.descent
        return RectF(left, top, right, bottom)
    }

    private fun drawLabelAtBounds(canvas: Canvas, label: String, bounds: RectF, fm: Paint.FontMetrics) {
        val baseline = bounds.top - fm.ascent
        val prevAlign = textPaint.textAlign
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(label, bounds.centerX(), baseline, textPaint)
        textPaint.textAlign = prevAlign
    }

    private fun collidesWithAny(candidate: RectF, existing: List<RectF>): Boolean {
        val padded = RectF(candidate).apply { inset(-LABEL_GAP_PX * 0.5f, -LABEL_GAP_PX * 0.5f) }
        return existing.any { RectF.intersects(padded, it) }
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

private const val LABEL_GAP_PX = 6f
private const val MAX_BUMPS = 3

private fun RectF.offsetCopy(dx: Float, dy: Float): RectF = RectF(left + dx, top + dy, right + dx, bottom + dy)
