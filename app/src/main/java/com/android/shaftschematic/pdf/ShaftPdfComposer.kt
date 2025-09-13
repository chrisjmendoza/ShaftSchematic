package com.android.shaftschematic.pdf

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.os.Environment
import java.io.File
import com.android.shaftschematic.data.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

object ShaftPdfComposer {

    /* ---------------- Page & units ---------------- */
    private const val POINTS_PER_INCH = 72f
    private const val MM_PER_INCH = 25.4f
    private val PT_PER_MM = POINTS_PER_INCH / MM_PER_INCH // ≈ 2.8346

    // US Letter landscape: 11" × 8.5" → 792 × 612 pt
    private const val PAGE_W = 792
    private const val PAGE_H = 612

    // Margins & blocks
    private const val MARGIN_PT = 36f // 0.5"
    private const val TITLE_BLOCK_H = 90f // bottom strip for title block

    private const val ARROW_SIZE = 6.0         // in points
    private const val ARROW_DEG = 160.0        // arrow wing angle from axis in degrees
    private val   ARROW_RAD = ARROW_DEG * PI / 180.0

    /* ---------------- Public API ---------------- */
    fun export(context: Context, spec: ShaftSpecMm, fileName: String = "shaft_drawing.pdf"): File {
        val pdf = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create()
        val page = pdf.startPage(pageInfo)
        val c = page.canvas

        // Work area (leave bottom for title)
        val workLeft = MARGIN_PT
        val workTop = MARGIN_PT
        val workRight = PAGE_W - MARGIN_PT
        val workBottom = PAGE_H - MARGIN_PT - TITLE_BLOCK_H

        // Border
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
        }
        c.drawRect(workLeft, workTop, workRight, workBottom, border)

        // Draw shaft with dimensions
        drawShaftSheet(c, spec, workLeft, workTop, workRight, workBottom)

        // Title block
        drawTitleBlock(c, spec, RectF(MARGIN_PT, PAGE_H - MARGIN_PT - TITLE_BLOCK_H, PAGE_W - MARGIN_PT, PAGE_H - MARGIN_PT))

        pdf.finishPage(page)

        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val out = File(dir, fileName)
        FileOutputStream(out).use { pdf.writeTo(it) }
        pdf.close()
        return out
    }

    /* ---------------- Drawing core ---------------- */

    private data class Span( // one drawable axial span at some diameter
        val xStartMm: Double,
        val lengthMm: Double,
        val diaMm: Double,
        val note: String? = null
    )

    private fun drawShaftSheet(
        c: Canvas,
        spec: ShaftSpecMm,
        left: Float, top: Float, right: Float, bottom: Float
    ) {
        val widthPt = right - left
        val heightPt = bottom - top

        // Build axial spans (forward → aft) from bodySegments + fallbacks.
        // Start with base shaft diameter; overlay body segments where present.
        val baseDia = spec.shaftDiameterMm
        val spans = mutableListOf<Span>()

        // Start with a single base span covering whole shaft
        spans.add(Span(0.0, spec.overallLengthMm, baseDia))

        // Replace parts by body segments (clip inside overall length)
        spec.bodySegments.sortedBy { it.positionFromForwardMm }.forEach { seg ->
            val pos = seg.positionFromForwardMm.coerceIn(0.0, spec.overallLengthMm)
            val len = min(seg.lengthMm, spec.overallLengthMm - pos).coerceAtLeast(0.0)
            if (len <= 0.0) return@forEach
            spans.spliceWith(pos, len, seg.diameterMm)
        }

        // Add liners as overlay notes (same diameter draw, annotate)
        val linerNotes = spec.liners.filter { it.lengthMm > 0 && it.diameterMm > 0 }

        // Simple taper visualization as short cones at ends (if provided)
        val forwardTaper = computeTaperSpanForward(spec.forwardTaper)
        val aftTaper = computeTaperSpanAft(spec.aftTaper, spec.overallLengthMm)

        // Compute needed drawing extents in mm
        val maxDiaMm = max(
            spans.maxOfOrNull { it.diaMm } ?: baseDia,
            max(
                forwardTaper?.second ?: 0.0,
                aftTaper?.second ?: 0.0
            )
        )

        // Choose a vertical layout:
        val padDiaPt = 24f
        val drawHeightPt = heightPt - 2 * padDiaPt
        val drawWidthPt = widthPt - 160f // leave space for dims arrows

        val neededWPt = (spec.overallLengthMm * PT_PER_MM).toFloat()
        val neededHPt = (maxDiaMm * PT_PER_MM).toFloat() * 2.0f + 80f // plus padding

        val scale = min(1f, min(drawWidthPt / neededWPt, drawHeightPt / neededHPt))
        val originX = left + 80f // left offset for dimension leaders
        val originY = top + padDiaPt + (drawHeightPt - neededHPt * scale) / 2f + 60f

        c.save()
        c.translate(originX, originY)
        c.scale(scale, scale)

        val centerY = 150f
        val shaftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f / max(1f, scale) // keep min width when scaled down
        }

        // Draw base spans as rectangles
        spans.forEach { s ->
            val x0 = mmToPt(s.xStartMm)
            val x1 = mmToPt(s.xStartMm + s.lengthMm)
            val half = mmToPt(s.diaMm) / 2f
            c.drawRect(x0, centerY - half, x1, centerY + half, shaftPaint)
        }

        // Draw forward taper (triangle) if any
        forwardTaper?.let { (startMm, maxDia) ->
            val lenMm = spec.forwardTaper.lengthMm
            val x0 = mmToPt(startMm)
            val x1 = mmToPt(startMm + lenMm)
            val halfBig = mmToPt(maxDia) / 2f
            val smallDiaMm = spec.forwardTaper.smallEndMm.takeIf { it > 0 }
                ?: (maxDia - spec.forwardTaper.ratio.value * lenMm)
            val halfSmall = mmToPt(smallDiaMm) / 2f    // <-- fixed: no extra PT_PER_MM
            val p = Path().apply {
                moveTo(x0, centerY - halfBig)
                lineTo(x1, centerY - halfSmall)
                lineTo(x1, centerY + halfSmall)
                lineTo(x0, centerY + halfBig)
                close()
            }
            c.drawPath(p, shaftPaint)
        }

        // Draw aft taper (triangle) if any
        aftTaper?.let { (endMm, maxDia) ->
            val lenMm = spec.aftTaper.lengthMm
            val x1 = mmToPt(endMm)
            val x0 = mmToPt(endMm - lenMm)
            val halfBig = mmToPt(maxDia) / 2f
            val smallDia = spec.aftTaper.smallEndMm.takeIf { it > 0 }
                ?: (maxDia - spec.aftTaper.ratio.value * lenMm)
            val halfSmall = mmToPt(smallDia) / 2f
            val p = Path().apply {
                moveTo(x1, centerY - halfBig)
                lineTo(x0, centerY - halfSmall)
                lineTo(x0, centerY + halfSmall)
                lineTo(x1, centerY + halfBig)
                close()
            }
            c.drawPath(p, shaftPaint)
        }

        // Centerline
        val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            pathEffect = DashPathEffect(floatArrayOf(8f, 8f), 0f)
            strokeWidth = 1.2f / max(1f, scale)
        }
        c.drawLine(0f, centerY, mmToPt(spec.overallLengthMm), centerY, centerPaint)

        // Threads callouts (draw short hatch at the end and dimension)
        drawThreadCallout(c, spec.forwardThreads, atMm = 0.0, centerY = centerY, outward = -1f, scale = scale)
        drawThreadCallout(c, spec.aftThreads, atMm = spec.overallLengthMm, centerY = centerY, outward = +1f, scale = scale)

        // Keyways (simple notch + callout)
        spec.keyways.forEach { k ->
            if (k.lengthMm <= 0.0 || k.widthMm <= 0.0) return@forEach
            val x0 = mmToPt(k.positionFromForwardMm)
            val x1 = mmToPt(k.positionFromForwardMm + k.lengthMm)
            val depth = mmToPt(k.depthMm)
            val notchTop = centerY - (mmToPt(baseDia) / 2f)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f / max(1f, scale) }
            // draw on top face as a shallow notch line
            c.drawLine(x0, notchTop, x1, notchTop, p)
            // callout
            drawDim(c, x0, notchTop - 20f, x1, notchTop - 20f, offset = -28f,
                text = "Keyway W ${fmt(k.widthMm)} × D ${fmt(k.depthMm)} × L ${fmt(k.lengthMm)}", above = true)
        }

        // Liners (bands with their own Ø)
        val bandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.7f / max(1f, scale) }
        linerNotes.forEach { ln ->
            val x0 = mmToPt(ln.positionFromForwardMm)
            val x1 = mmToPt(ln.positionFromForwardMm + ln.lengthMm)
            val half = mmToPt(ln.diameterMm) / 2f
            c.drawRect(x0, centerY - half, x1, centerY + half, bandPaint)
            drawDim(c, x0, centerY + half + 24f, x1, centerY + half + 24f, offset = +22f,
                text = "Liner Ø ${fmt(ln.diameterMm)} × ${fmt(ln.lengthMm)}")
        }

        // Overall length dim (below)
        val x0 = 0f
        val x1 = mmToPt(spec.overallLengthMm)
        drawDim(c, x0, centerY + mmToPt(maxDiaMm) / 2f + 70f, x1, centerY + mmToPt(maxDiaMm) / 2f + 70f,
            offset = +26f, text = "Overall ${fmt(spec.overallLengthMm)}")

        c.restore()
    }

    /* ---------------- Helpers: spans, tapers, dims ---------------- */

    private fun MutableList<Span>.spliceWith(startMm: Double, lenMm: Double, newDiaMm: Double) {
        if (lenMm <= 0.0) return
        val endMm = startMm + lenMm
        val out = mutableListOf<Span>()
        for (s in this) {
            val s0 = s.xStartMm
            val s1 = s.xStartMm + s.lengthMm
            if (endMm <= s0 || startMm >= s1) {
                out.add(s) // no overlap
            } else {
                // left remainder
                if (startMm > s0) out.add(Span(s0, startMm - s0, s.diaMm))
                // middle replaced
                val midLen = min(endMm, s1) - max(startMm, s0)
                if (midLen > 0) out.add(Span(max(startMm, s0), midLen, newDiaMm))
                // right remainder
                if (endMm < s1) out.add(Span(endMm, s1 - endMm, s.diaMm))
            }
        }
        clear(); addAll(out.sortedBy { it.xStartMm })
    }

    private fun computeTaperSpanForward(t: TaperSpec): Pair<Double, Double>? {
        if (t.lengthMm <= 0.0) return null
        val large = when {
            t.largeEndMm > 0 -> t.largeEndMm
            t.smallEndMm > 0 && t.ratio.den > 0 -> t.smallEndMm + t.ratio.value * t.lengthMm
            else -> return null
        }
        val maxDia = max(large, t.smallEndMm)
        return 0.0 to maxDia
    }

    private fun computeTaperSpanAft(t: TaperSpec, overallLen: Double): Pair<Double, Double>? {
        if (t.lengthMm <= 0.0) return null
        val large = when {
            t.largeEndMm > 0 -> t.largeEndMm
            t.smallEndMm > 0 && t.ratio.den > 0 -> t.smallEndMm + t.ratio.value * t.lengthMm
            else -> return null
        }
        val maxDia = max(large, t.smallEndMm)
        return overallLen to maxDia
    }

    private fun drawTitleBlock(c: Canvas, spec: ShaftSpecMm, area: RectF) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.2f }
        c.drawRect(area, p)

        // Divide into 3 columns
        val colW = area.width() / 3f
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f }
        c.drawLine(area.left + colW, area.top, area.left + colW, area.bottom, line)
        c.drawLine(area.left + 2 * colW, area.top, area.left + 2 * colW, area.bottom, line)

        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f }

        // Left: Title & meta
        val y0 = area.top + 20f
        c.drawText("Shaft Schematic", area.left + 12f, y0, title)
        c.drawText("Overall: ${fmt(spec.overallLengthMm)} mm", area.left + 12f, y0 + 18f, text)
        c.drawText("Base Ø: ${fmt(spec.shaftDiameterMm)} mm", area.left + 12f, y0 + 34f, text)

        // Middle: date / scale note
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        c.drawText("Date: ${sdf.format(Date())}", area.left + colW + 12f, y0, text)
        c.drawText("Units: mm (drawing scaled to fit)", area.left + colW + 12f, y0 + 18f, text)

        // Right: file / rev placeholders
        c.drawText("File: shaft_drawing.pdf", area.left + 2 * colW + 12f, y0, text)
        c.drawText("Rev: A", area.left + 2 * colW + 12f, y0 + 18f, text)
        c.drawText("App: ShaftSchematic", area.left + 2 * colW + 12f, y0 + 36f, text)
    }

    /* ---------------- Dimension arrows ---------------- */

    private fun drawDim(
        c: Canvas,
        x0: Float, y0: Float,
        x1: Float, y1: Float,
        offset: Float = 20f,
        text: String,
        above: Boolean = false
    ) {
        // Draw a horizontal or vertical dimension with arrows and text
        val isHorizontal = abs(y1 - y0) < 1e-3
        val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.2f }
        val txtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 11.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        if (isHorizontal) {
            val y = y0 + offset
            val a = min(x0, x1); val b = max(x0, x1)
            // extension lines
            c.drawLine(x0, y0, x0, y, dimPaint)
            c.drawLine(x1, y1, x1, y, dimPaint)
            // axis
            c.drawLine(a, y, b, y, dimPaint)
            // arrows
            drawArrow(c, a, y, a + 14f * sign(b - a), y, dimPaint)
            drawArrow(c, b, y, b - 14f * sign(b - a), y, dimPaint)
            // label centered
            val tw = txtPaint.measureText(text)
            c.drawText(text, (a + b) / 2f - tw / 2f, y + if (above) -6f else -4f, txtPaint)
        } else {
            val x = x0 + offset
            val a = min(y0, y1); val b = max(y0, y1)
            // extension lines
            c.drawLine(x0, y0, x, y0, dimPaint)
            c.drawLine(x1, y1, x, y1, dimPaint)
            // axis
            c.drawLine(x, a, x, b, dimPaint)
            // arrows
            drawArrow(c, x, a, x, a + 14f * sign(b - a), dimPaint)
            drawArrow(c, x, b, x, b - 14f * sign(b - a), dimPaint)
            // label centered
            val tw = txtPaint.measureText(text)
            c.drawText(text, x + 6f, (a + b) / 2f - 2f, txtPaint)
        }
    }

    private fun drawThreadCallout(
        c: Canvas,
        t: ThreadSpec,
        atMm: Double,             // x position from forward end
        centerY: Float,
        outward: Float,           // -1 = left of 0, +1 = right of overall
        scale: Float
    ) {
        if (t.lengthMm <= 0.0 || t.diameterMm <= 0.0) return
        val hatch = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f / max(1f, scale) }
        val x = mmToPt(atMm)
        // short hatch mark to indicate thread region start
        c.drawLine(x, centerY - 22f, x, centerY + 22f, hatch)
        // dimension/label off to side
        val text = "Threads Ø ${fmt(t.diameterMm)}  P ${fmt(t.pitchMm)}  L ${fmt(t.lengthMm)}"
        val dx = 48f * outward
        drawLeaderText(c, x + dx, centerY - 30f, text)
    }

    private fun drawLeaderText(c: Canvas, x: Float, y: Float, text: String) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.1f }
        val t = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11.5f }
        // tiny leader line
        c.drawLine(x - 16f, y - 8f, x - 2f, y - 2f, p)
        c.drawText(text, x, y, t)
    }

    private fun drawArrow(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, p: Paint) {
        c.drawLine(x0, y0, x1, y1, p)

        val ang = atan2((y1 - y0).toDouble(), (x1 - x0).toDouble())
        val a1 = ang + ARROW_RAD
        val a2 = ang - ARROW_RAD

        val xA = (x1 + ARROW_SIZE * cos(a1)).toFloat()
        val yA = (y1 + ARROW_SIZE * sin(a1)).toFloat()
        val xB = (x1 + ARROW_SIZE * cos(a2)).toFloat()
        val yB = (y1 + ARROW_SIZE * sin(a2)).toFloat()

        c.drawLine(x1, y1, xA, yA, p)
        c.drawLine(x1, y1, xB, yB, p)
    }

    /* ---------------- Utils ---------------- */

    private fun mmToPt(mm: Double): Float = (mm * PT_PER_MM).toFloat()
    private fun fmt(v: Double, decimals: Int = 2): String = "%.${decimals}f".format(Locale.US, v)
    private fun sign(v: Float): Float = if (v < 0) -1f else 1f
}
