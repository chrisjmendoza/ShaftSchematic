package com.android.shaftschematic.pdf

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.os.Environment
import androidx.core.graphics.withScale
import androidx.core.graphics.withTranslation
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

    private data class Span(
        val xStartMm: Double,
        val lengthMm: Double,
        val diaMm: Double
    )

    /* ---------------- Units & page ---------------- */
    private const val POINTS_PER_INCH = 72f
    private const val MM_PER_INCH = 25.4f
    private val PT_PER_MM = POINTS_PER_INCH / MM_PER_INCH // ≈ 2.8346

    // US Letter landscape: 11" × 8.5" → 792 × 612 pt
    private const val PAGE_W_PT = 792
    private const val PAGE_H_PT = 612

    /* ---------------- Layout constants ---------------- */
    private const val MARGIN_PT = 36f            // 0.5"
    private const val TITLE_BLOCK_H_PT = 90f     // fixed title block height
    private const val LEFT_LEADER_GUTTER_PT = 80f
    private const val VERTICAL_PADDING_PT = 24f

    /* ---------------- Arrowhead constants ---------------- */
    private const val ARROW_SIZE = 6.0
    private const val ARROW_DEG = 160.0
    private val ARROW_RAD = ARROW_DEG * PI / 180.0

    /* ---------------- Reusable paints ---------------- */
    private val stroke1 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1.2f
    }
    private val stroke2 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1.7f
    }
    private val dashedCenter = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1.2f
        pathEffect = DashPathEffect(floatArrayOf(8f, 8f), 0f)
    }
    private val titleText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 16f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val smallText = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f }
    private val dimText = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11.5f }

    /* ---------------- Public API ---------------- */
    fun export(context: Context, spec: ShaftSpecMm, fileName: String = "shaft_drawing.pdf"): File {
        // Build the PDF
        val pdf = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_W_PT, PAGE_H_PT, 1).create()
        val page = pdf.startPage(pageInfo)
        val c = page.canvas

        // Page-constant rectangles (computed once per page)
        val pageRect = RectF(0f, 0f, PAGE_W_PT.toFloat(), PAGE_H_PT.toFloat())
        val titleRect = RectF(
            MARGIN_PT,
            pageRect.bottom - MARGIN_PT - TITLE_BLOCK_H_PT,
            pageRect.right - MARGIN_PT,
            pageRect.bottom - MARGIN_PT
        )
        val workRect = RectF(
            MARGIN_PT,
            MARGIN_PT,
            pageRect.right - MARGIN_PT,
            titleRect.top - VERTICAL_PADDING_PT * 0f // keep clear separation
        )

        // Border around the work area (optional)
        c.drawRect(workRect, stroke1)

        // Draw main content using the fixed rectangles
        drawShaftSheet(c, spec, workRect)
        drawTitleBlock(c, spec, titleRect)

        pdf.finishPage(page)

        // Save to app Documents (scoped, no permissions)
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        val out = File(dir, fileName)
        FileOutputStream(out).use { pdf.writeTo(it) }
        pdf.close()
        return out
    }

    /* ---------------- Core drawing ---------------- */

    private fun drawShaftSheet(c: Canvas, spec: ShaftSpecMm, area: RectF) {
        // Precompute width/height once
        val widthPt = area.width()
        val heightPt = area.height()

        // Build spans from base + body segments
        val baseDia = spec.shaftDiameterMm
        val spans = mutableListOf(Span(0.0, spec.overallLengthMm, baseDia))
        spec.bodySegments.sortedBy { it.positionFromForwardMm }.forEach { seg ->
            val pos = seg.positionFromForwardMm.coerceIn(0.0, spec.overallLengthMm)
            val len = min(seg.lengthMm, spec.overallLengthMm - pos).coerceAtLeast(0.0)
            if (len > 0.0) spans.spliceWith(pos, len, seg.diameterMm)
        }

        val linerNotes = spec.liners.filter { it.lengthMm > 0 && it.diameterMm > 0 }
        val forwardTaper = computeTaperSpanForward(spec.forwardTaper)
        val aftTaper = computeTaperSpanAft(spec.aftTaper, spec.overallLengthMm)

        val maxDiaMm = max(
            spans.maxOfOrNull { it.diaMm } ?: baseDia,
            max(forwardTaper?.second ?: 0.0, aftTaper?.second ?: 0.0)
        )

        // Fit-to-area scaling (computed once)
        val drawWidthPt = widthPt - LEFT_LEADER_GUTTER_PT
        val neededWPt = (spec.overallLengthMm * PT_PER_MM).toFloat()
        val neededHPt = (maxDiaMm * PT_PER_MM).toFloat() * 2f + 80f
        val scale = min(1f, min(drawWidthPt / neededWPt, (heightPt - 2 * VERTICAL_PADDING_PT) / neededHPt))

        // All drawing happens within this translated + scaled block (auto-saved/restored)
        c.withTranslation(area.left + LEFT_LEADER_GUTTER_PT, area.top + VERTICAL_PADDING_PT + 60f) {
            withScale(scale, scale) {
                val centerY = 150f

                // Stroke width that remains readable when scaled down
                val scaledStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE; strokeWidth = 2f / max(1f, scale)
                }

                // Span rectangles
                spans.forEach { s ->
                    val x0 = mmToPt(s.xStartMm)
                    val x1 = mmToPt(s.xStartMm + s.lengthMm)
                    val half = mmToPt(s.diaMm) / 2f
                    drawRect(x0, centerY - half, x1, centerY + half, scaledStroke)
                }

                // Forward taper
                forwardTaper?.let { (startMm, maxDia) ->
                    val lenMm = spec.forwardTaper.lengthMm
                    val x0 = mmToPt(startMm)
                    val x1 = mmToPt(startMm + lenMm)
                    val halfBig = mmToPt(maxDia) / 2f
                    val smallDiaMm = spec.forwardTaper.smallEndMm.takeIf { it > 0 }
                        ?: (maxDia - spec.forwardTaper.ratio.value * lenMm)
                    val halfSmall = mmToPt(smallDiaMm) / 2f
                    val p = Path().apply {
                        moveTo(x0, centerY - halfBig)
                        lineTo(x1, centerY - halfSmall)
                        lineTo(x1, centerY + halfSmall)
                        lineTo(x0, centerY + halfBig)
                        close()
                    }
                    drawPath(p, scaledStroke)
                }

                // Aft taper
                aftTaper?.let { (endMm, maxDia) ->
                    val lenMm = spec.aftTaper.lengthMm
                    val x1 = mmToPt(endMm)
                    val x0 = mmToPt(endMm - lenMm)
                    val halfBig = mmToPt(maxDia) / 2f
                    val smallDiaMm = spec.aftTaper.smallEndMm.takeIf { it > 0 }
                        ?: (maxDia - spec.aftTaper.ratio.value * lenMm)
                    val halfSmall = mmToPt(smallDiaMm) / 2f
                    val p = Path().apply {
                        moveTo(x1, centerY - halfBig)
                        lineTo(x0, centerY - halfSmall)
                        lineTo(x0, centerY + halfSmall)
                        lineTo(x1, centerY + halfBig)
                        close()
                    }
                    drawPath(p, scaledStroke)
                }

                // Centerline
                drawLine(0f, centerY, mmToPt(spec.overallLengthMm), centerY, dashedCenter)

                // Threads callouts
                drawThreadCallout(this, spec.forwardThreads, atMm = 0.0, centerY = centerY, outward = -1f, scale = scale)
                drawThreadCallout(this, spec.aftThreads, atMm = spec.overallLengthMm, centerY = centerY, outward = +1f, scale = scale)

                // Keyways (simple top-notch + callout)
                spec.keyways.forEach { k ->
                    if (k.lengthMm <= 0.0 || k.widthMm <= 0.0) return@forEach
                    val x0 = mmToPt(k.positionFromForwardMm)
                    val x1 = mmToPt(k.positionFromForwardMm + k.lengthMm)
                    val notchTop = centerY - (mmToPt(baseDia) / 2f)
                    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f / max(1f, scale) }
                    drawLine(x0, notchTop, x1, notchTop, p)
                    drawDim(
                        this, x0, notchTop - 20f, x1, notchTop - 20f, offset = -28f,
                        text = "Keyway W ${fmt(k.widthMm)} × D ${fmt(k.depthMm)} × L ${fmt(k.lengthMm)}", above = true
                    )
                }

                // Liners
                val stroke2Scaled = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE; strokeWidth = 1.7f / max(1f, scale)
                }
                linerNotes.forEach { ln ->
                    val x0 = mmToPt(ln.positionFromForwardMm)
                    val x1 = mmToPt(ln.positionFromForwardMm + ln.lengthMm)
                    val half = mmToPt(ln.diameterMm) / 2f
                    drawRect(x0, centerY - half, x1, centerY + half, stroke2Scaled)
                    drawDim(
                        this, x0, centerY + half + 24f, x1, centerY + half + 24f, offset = +22f,
                        text = "Liner Ø ${fmt(ln.diameterMm)} × ${fmt(ln.lengthMm)}"
                    )
                }

                // Overall length dimension (below)
                val x0 = 0f
                val x1 = mmToPt(spec.overallLengthMm)
                drawDim(
                    this, x0, centerY + mmToPt(maxDiaMm) / 2f + 70f, x1, centerY + mmToPt(maxDiaMm) / 2f + 70f,
                    offset = +26f, text = "Overall ${fmt(spec.overallLengthMm)}"
                )
            }
        }
    }


    /* ---------------- Title block ---------------- */

    private fun drawTitleBlock(c: Canvas, spec: ShaftSpecMm, area: RectF) {
        c.drawRect(area, stroke1)

        // 3 columns
        val colW = area.width() / 3f
        c.drawLine(area.left + colW, area.top, area.left + colW, area.bottom, stroke1)
        c.drawLine(area.left + 2 * colW, area.top, area.left + 2 * colW, area.bottom, stroke1)

        val y0 = area.top + 20f
        c.drawText("Shaft Schematic", area.left + 12f, y0, titleText)
        c.drawText("Overall: ${fmt(spec.overallLengthMm)} mm", area.left + 12f, y0 + 18f, smallText)
        c.drawText("Base Ø: ${fmt(spec.shaftDiameterMm)} mm", area.left + 12f, y0 + 34f, smallText)

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        c.drawText("Date: ${sdf.format(Date())}", area.left + colW + 12f, y0, smallText)
        c.drawText("Units: mm (scaled to fit)", area.left + colW + 12f, y0 + 18f, smallText)

        c.drawText("File: shaft_drawing.pdf", area.left + 2 * colW + 12f, y0, smallText)
        c.drawText("Rev: A", area.left + 2 * colW + 12f, y0 + 18f, smallText)
        c.drawText("App: ShaftSchematic", area.left + 2 * colW + 12f, y0 + 36f, smallText)
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
                // no overlap
                out.add(s)
            } else {
                // left remainder
                if (startMm > s0) out.add(Span(s0, startMm - s0, s.diaMm))
                // middle overlap replaced with new diameter
                val midLen = kotlin.math.min(endMm, s1) - kotlin.math.max(startMm, s0)
                if (midLen > 0) out.add(Span(kotlin.math.max(startMm, s0), midLen, newDiaMm))
                // right remainder
                if (endMm < s1) out.add(Span(endMm, s1 - endMm, s.diaMm))
            }
        }
        clear()
        addAll(out.sortedBy { it.xStartMm })
    }

    private fun computeTaperSpanForward(t: TaperSpec): Pair<Double, Double>? {
        if (t.lengthMm <= 0.0) return null
        val large = when {
            t.largeEndMm > 0 -> t.largeEndMm
            t.smallEndMm > 0 && t.ratio.den > 0 -> t.smallEndMm + t.ratio.value * t.lengthMm
            else -> return null
        }
        return 0.0 to max(large, t.smallEndMm)
    }

    private fun computeTaperSpanAft(t: TaperSpec, overallLen: Double): Pair<Double, Double>? {
        if (t.lengthMm <= 0.0) return null
        val large = when {
            t.largeEndMm > 0 -> t.largeEndMm
            t.smallEndMm > 0 && t.ratio.den > 0 -> t.smallEndMm + t.ratio.value * t.lengthMm
            else -> return null
        }
        return overallLen to max(large, t.smallEndMm)
    }

    private fun drawDim(
        c: Canvas,
        x0: Float, y0: Float,
        x1: Float, y1: Float,
        offset: Float = 20f,
        text: String,
        above: Boolean = false
    ) {
        val isHorizontal = abs(y1 - y0) < 1e-3
        val dimStroke = stroke1

        if (isHorizontal) {
            val y = y0 + offset
            val a = min(x0, x1); val b = max(x0, x1)
            c.drawLine(x0, y0, x0, y, dimStroke)
            c.drawLine(x1, y1, x1, y, dimStroke)
            c.drawLine(a, y, b, y, dimStroke)
            drawArrow(c, a, y, a + 14f * sign(b - a), y, dimStroke)
            drawArrow(c, b, y, b - 14f * sign(b - a), y, dimStroke)
            val tw = dimText.measureText(text)
            c.drawText(text, (a + b) / 2f - tw / 2f, y + if (above) -6f else -4f, dimText)
        } else {
            val x = x0 + offset
            val a = min(y0, y1); val b = max(y0, y1)
            c.drawLine(x0, y0, x, y0, dimStroke)
            c.drawLine(x1, y1, x, y1, dimStroke)
            c.drawLine(x, a, x, b, dimStroke)
            drawArrow(c, x, a, x, a + 14f * sign(b - a), dimStroke)
            drawArrow(c, x, b, x, b - 14f * sign(b - a), dimStroke)
            c.drawText(text, x + 6f, (a + b) / 2f - 2f, dimText)
        }
    }

    private fun drawThreadCallout(
        c: Canvas,
        t: ThreadSpec,
        atMm: Double,
        centerY: Float,
        outward: Float,
        scale: Float
    ) {
        if (t.lengthMm <= 0.0 || t.diameterMm <= 0.0) return
        val hatch = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 1.5f / max(1f, scale)
        }
        val x = mmToPt(atMm)
        c.drawLine(x, centerY - 22f, x, centerY + 22f, hatch)
        val text = "Threads Ø ${fmt(t.diameterMm)}  P ${fmt(t.pitchMm)}  L ${fmt(t.lengthMm)}"
        drawLeaderText(c, x + 48f * outward, centerY - 30f, text)
    }

    private fun drawLeaderText(c: Canvas, x: Float, y: Float, text: String) {
        c.drawLine(x - 16f, y - 8f, x - 2f, y - 2f, stroke1)
        c.drawText(text, x, y, dimText)
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
