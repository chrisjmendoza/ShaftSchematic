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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * ShaftPdfComposer (schema: ShaftSpecMm with startFromAftMm-based elements)
 *
 * - No global base diameter; draws whatever Body segments exist
 * - Accepts singletons (forward/aft) + list elements; normalizes to lists
 * - Converts AFT-based coordinates -> forward-origin for drawing (x = 0 at forward end)
 * - Stacked dimension lines ABOVE the shaft; overall length BELOW
 * - Centerline intentionally omitted in preview
 */
object ShaftPdfComposer {

    /* ───────────────────────── Units & page ───────────────────────── */
    private const val POINTS_PER_INCH = 72f
    private const val MM_PER_INCH = 25.4f
    private val PT_PER_MM = POINTS_PER_INCH / MM_PER_INCH

    // US Letter landscape
    private const val PAGE_W_PT = 792
    private const val PAGE_H_PT = 612

    /* ───────────────────────── Layout ───────────────────────── */
    private const val MARGIN_PT = 36f
    private const val TITLE_BLOCK_H_PT = 90f
    private const val LEFT_LEADER_GUTTER_PT = 80f
    private const val VERTICAL_PADDING_PT = 24f

    // Stacked dimensions (above shaft)
    private const val DIM_ROW_SPACING_PT = 26f
    private const val DIM_STACK_CLEAR_PT = 40f
    private const val DIM_TEXT_OFFSET_PT = -18f

    /* ───────────────────────── Arrows ───────────────────────── */
    private const val ARROW_SIZE = 6.0
    private const val ARROW_DEG = 160.0
    private val ARROW_RAD = ARROW_DEG * PI / 180.0

    /* ───────────────────────── Paints ───────────────────────── */
    private val stroke1 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1.2f
    }
    private val titleText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 16f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val smallText = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f }
    private val dimText = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11.5f }

    /* ───────────────────────── Public API ───────────────────────── */
    fun export(context: Context, spec: ShaftSpecMm, fileName: String = "shaft_drawing.pdf"): File {
        val pdf = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_W_PT, PAGE_H_PT, 1).create()
        val page = pdf.startPage(pageInfo)
        val c = page.canvas

        // Page rects
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
            titleRect.top - VERTICAL_PADDING_PT * 0f
        )

        // Optional work area border
        c.drawRect(workRect, stroke1)

        drawShaftSheet(c, spec, workRect)
        drawTitleBlock(c, spec, titleRect)

        pdf.finishPage(page)

        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        val out = File(dir, fileName)
        FileOutputStream(out).use { pdf.writeTo(it) }
        pdf.close()
        return out
    }

    /* ───────────────────────── Core drawing ───────────────────────── */

    // Internal normalized forms (forward-origin coordinates, doubles for math)
    private data class BodyN(val xStartMm: Double, val lengthMm: Double, val diaMm: Double)
    private data class TaperN(val xStartMm: Double, val lengthMm: Double, val startDiaMm: Double, val endDiaMm: Double)
    private data class ThreadN(val xStartMm: Double, val lengthMm: Double, val majorDiaMm: Double, val pitchMm: Double, val endLabel: String)
    private data class LinerN(val xStartMm: Double, val lengthMm: Double, val odMm: Double)

    /** Convert AFT-based start to FORWARD-based xStart (left->right). */
    private fun aftToForwardStart(overallLenMm: Double, startFromAftMm: Double, lengthMm: Double): Double {
        // AFT origin: 0 at aft, increasing toward forward
        // FORWARD origin target: 0 at forward, increasing toward aft
        // x_fwd = overall - (startFromAft + length)
        return (overallLenMm - (startFromAftMm + lengthMm)).coerceAtLeast(0.0)
    }

    /** Gather singletons + lists, normalize to forward-origin working lists. */
    private fun normalize(spec: ShaftSpecMm): Normalized {
        val overall = spec.overallLengthMm.toDouble()

        val bodies = buildList {
            // collection
            spec.bodies.forEach {
                val len = it.lengthMm.toDouble()
                if (len > 0.0) {
                    val x = aftToForwardStart(overall, it.startFromAftMm.toDouble(), len)
                    add(BodyN(x, len, it.diaMm.toDouble()))
                }
            }
        }.sortedBy { it.xStartMm }

        val tapers = buildList {
            spec.tapers.forEach {
                val len = it.lengthMm.toDouble()
                if (len > 0.0) {
                    val x = aftToForwardStart(overall, it.startFromAftMm.toDouble(), len)
                    add(TaperN(x, len, it.startDiaMm.toDouble(), it.endDiaMm.toDouble()))
                }
            }
            // optional singletons
            spec.forwardTaper?.let {
                val len = it.lengthMm.toDouble()
                if (len > 0.0) {
                    val x = aftToForwardStart(overall, it.startFromAftMm.toDouble(), len)
                    add(TaperN(x, len, it.startDiaMm.toDouble(), it.endDiaMm.toDouble()))
                }
            }
            spec.aftTaper?.let {
                val len = it.lengthMm.toDouble()
                if (len > 0.0) {
                    val x = aftToForwardStart(overall, it.startFromAftMm.toDouble(), len)
                    add(TaperN(x, len, it.startDiaMm.toDouble(), it.endDiaMm.toDouble()))
                }
            }
        }.sortedBy { it.xStartMm }

        val threads = buildList {
            spec.threads.forEach {
                val len = it.lengthMm.toDouble()
                if (len > 0.0 && it.majorDiaMm > 0f) {
                    val x = aftToForwardStart(overall, it.startFromAftMm.toDouble(), len)
                    add(ThreadN(x, len, it.majorDiaMm.toDouble(), it.pitchMm.toDouble(), it.endLabel))
                }
            }
            spec.forwardThread?.let {
                val len = it.lengthMm.toDouble()
                if (len > 0.0 && it.majorDiaMm > 0f) {
                    val x = aftToForwardStart(overall, it.startFromAftMm.toDouble(), len)
                    add(ThreadN(x, len, it.majorDiaMm.toDouble(), it.pitchMm.toDouble(), if (it.endLabel.isNotBlank()) it.endLabel else "FWD"))
                }
            }
            spec.aftThread?.let {
                val len = it.lengthMm.toDouble()
                if (len > 0.0 && it.majorDiaMm > 0f) {
                    val x = aftToForwardStart(overall, it.startFromAftMm.toDouble(), len)
                    add(ThreadN(x, len, it.majorDiaMm.toDouble(), it.pitchMm.toDouble(), if (it.endLabel.isNotBlank()) it.endLabel else "AFT"))
                }
            }
        }.sortedBy { it.xStartMm }

        val liners = buildList {
            spec.liners.forEach {
                val len = it.lengthMm.toDouble()
                if (len > 0.0 && it.odMm > 0f) {
                    val x = aftToForwardStart(overall, it.startFromAftMm.toDouble(), len)
                    add(LinerN(x, len, it.odMm.toDouble()))
                }
            }
        }.sortedBy { it.xStartMm }

        // derive max OD from anything we have
        val maxDia = listOfNotNull(
            bodies.maxOfOrNull { it.diaMm },
            tapers.maxOfOrNull { max(it.startDiaMm, it.endDiaMm) },
            liners.maxOfOrNull { it.odMm },
            threads.maxOfOrNull { it.majorDiaMm }
        ).maxOrNull() ?: 0.0

        return Normalized(
            overallLenMm = overall,
            bodies = bodies,
            tapers = tapers,
            threads = threads,
            liners = liners,
            maxDiaMm = maxDia
        )
    }

    private data class Normalized(
        val overallLenMm: Double,
        val bodies: List<BodyN>,
        val tapers: List<TaperN>,
        val threads: List<ThreadN>,
        val liners: List<LinerN>,
        val maxDiaMm: Double
    )

    private fun drawShaftSheet(c: Canvas, spec: ShaftSpecMm, area: RectF) {
        val n = normalize(spec)

        // Scale to fit
        val widthPt = area.width()
        val heightPt = area.height()
        val drawWidthPt = widthPt - LEFT_LEADER_GUTTER_PT
        val neededWPt = (n.overallLenMm * PT_PER_MM).toFloat()
        val neededHPt = (n.maxDiaMm * PT_PER_MM).toFloat() * 2f + 80f
        val scale = min(1f, min(drawWidthPt / neededWPt, (heightPt - 2 * VERTICAL_PADDING_PT) / neededHPt))

        c.withTranslation(area.left + LEFT_LEADER_GUTTER_PT, area.top + VERTICAL_PADDING_PT + 60f) {
            withScale(scale, scale) {
                val centerY = 150f

                // Scaled strokes for readability
                val strokeScaled = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE; strokeWidth = 2f / max(1f, scale)
                }
                val strokeThin = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE; strokeWidth = 1.5f / max(1f, scale)
                }

                /* ── Bodies (rects) ───────────────────────────── */
                n.bodies.forEach { b ->
                    val x0 = mmToPt(b.xStartMm)
                    val x1 = mmToPt(b.xStartMm + b.lengthMm)
                    val half = mmToPt(b.diaMm) / 2f
                    drawRect(x0, centerY - half, x1, centerY + half, strokeScaled)
                }

                /* ── Tapers (quads) ───────────────────────────── */
                n.tapers.forEach { t ->
                    val x0 = mmToPt(t.xStartMm)
                    val x1 = mmToPt(t.xStartMm + t.lengthMm)
                    val half0 = mmToPt(t.startDiaMm) / 2f
                    val half1 = mmToPt(t.endDiaMm) / 2f
                    val p = Path().apply {
                        moveTo(x0, centerY - half0); lineTo(x1, centerY - half1)
                        lineTo(x1, centerY + half1); lineTo(x0, centerY + half0); close()
                    }
                    drawPath(p, strokeScaled)
                }

                /* ── Centerline intentionally omitted ─────────── */
                // (turn on if desired)
                // drawLine(0f, centerY, mmToPt(n.overallLenMm), centerY, strokeThin)

                /* ── Threads (tick + leader text) ─────────────── */
                n.threads.forEach { th ->
                    val x = mmToPt(th.xStartMm)
                    drawLine(x, centerY - 22f, x, centerY + 22f, strokeThin)
                    val text = buildString {
                        append("Threads Ø ").append(fmt(th.majorDiaMm))
                        if (th.pitchMm > 0.0) append("  P ").append(fmt(th.pitchMm))
                        append("  L ").append(fmt(th.lengthMm))
                        if (th.endLabel.isNotBlank()) append("  ").append(th.endLabel)
                    }
                    drawLeaderText(this, x + 48f * -1f, centerY - 30f, text) // default leader to upper-left
                }

                /* ── Liners (rects + small bottom dims) ───────── */
                n.liners.forEach { ln ->
                    val x0 = mmToPt(ln.xStartMm)
                    val x1 = mmToPt(ln.xStartMm + ln.lengthMm)
                    val half = mmToPt(ln.odMm) / 2f
                    drawRect(x0, centerY - half, x1, centerY + half, strokeThin)
                    drawDim(
                        this,
                        x0, centerY + half + 24f,
                        x1, centerY + half + 24f,
                        offset = +22f,
                        text = "Liner Ø ${fmt(ln.odMm)} × ${fmt(ln.lengthMm)}"
                    )
                }

                /* ── Stacked dims ABOVE shaft ─────────────────── */
                val stackItems = collectDimItems(n)
                if (stackItems.isNotEmpty()) {
                    drawDimStackAbove(this, centerY, n.maxDiaMm, stackItems)
                }

                /* ── Overall length BELOW ─────────────────────── */
                val x0 = 0f
                val x1 = mmToPt(n.overallLenMm)
                drawDim(
                    this,
                    x0, centerY + mmToPt(n.maxDiaMm) / 2f + 70f,
                    x1, centerY + mmToPt(n.maxDiaMm) / 2f + 70f,
                    offset = +26f,
                    text = "Overall ${fmt(n.overallLenMm)}"
                )
            }
        }
    }

    /* ───────────────────────── Title block ───────────────────────── */
    private fun drawTitleBlock(c: Canvas, spec: ShaftSpecMm, area: RectF) {
        c.drawRect(area, stroke1)
        val colW = area.width() / 3f
        c.drawLine(area.left + colW, area.top, area.left + colW, area.bottom, stroke1)
        c.drawLine(area.left + 2 * colW, area.top, area.left + 2 * colW, area.bottom, stroke1)

        val y0 = area.top + 20f
        c.drawText("Shaft Schematic", area.left + 12f, y0, titleText)
        c.drawText("Overall: ${fmt(spec.overallLengthMm.toDouble())} mm", area.left + 12f, y0 + 18f, smallText)
        c.drawText("App: ShaftSchematic", area.left + 12f, y0 + 34f, smallText)

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        c.drawText("Date: ${sdf.format(Date())}", area.left + colW + 12f, y0, smallText)
        c.drawText("Units: mm (scaled to fit)", area.left + colW + 12f, y0 + 18f, smallText)

        c.drawText("File: shaft_drawing.pdf", area.left + 2 * colW + 12f, y0, smallText)
        c.drawText("Rev: A", area.left + 2 * colW + 12f, y0 + 18f, smallText)
    }

    /* ───────────────────────── Stacked dimensions ───────────────────────── */

    private data class DimItem(val startMm: Double, val endMm: Double, val label: String)

    private fun collectDimItems(n: Normalized): List<DimItem> {
        val items = mutableListOf<DimItem>()

        // Bodies
        n.bodies.forEach { b ->
            if (b.lengthMm > 0.0) items += DimItem(b.xStartMm, b.xStartMm + b.lengthMm, fmt(b.lengthMm))
        }
        // Liners
        n.liners.forEach { ln ->
            if (ln.lengthMm > 0.0) items += DimItem(ln.xStartMm, ln.xStartMm + ln.lengthMm, fmt(ln.lengthMm))
        }
        // Tapers
        n.tapers.forEach { t ->
            if (t.lengthMm > 0.0) items += DimItem(t.xStartMm, t.xStartMm + t.lengthMm, fmt(t.lengthMm))
        }
        // Threads (optional)
        n.threads.forEach { th ->
            if (th.lengthMm > 0.0) items += DimItem(th.xStartMm, th.xStartMm + th.lengthMm, fmt(th.lengthMm))
        }

        return items
    }

    private fun drawDimStackAbove(c: Canvas, centerY: Float, maxDiaMm: Double, items: List<DimItem>) {
        var row = 0
        items.forEach { itx ->
            val x0 = mmToPt(itx.startMm)
            val x1 = mmToPt(itx.endMm)
            val y = dimRowYAbove(centerY, maxDiaMm, row++)
            drawDim(c, x0, y, x1, y, offset = DIM_TEXT_OFFSET_PT, text = itx.label, above = true)
        }
    }

    private fun dimRowYAbove(centerY: Float, maxDiaMm: Double, row: Int): Float {
        val odHalf = mmToPt(maxDiaMm) / 2f
        val base = centerY - odHalf - DIM_STACK_CLEAR_PT
        return base - row * DIM_ROW_SPACING_PT
    }

    /* ───────────────────────── Dimension & leaders ───────────────────────── */

    private fun drawDim(
        c: Canvas,
        x0: Float, y0: Float,
        x1: Float, y1: Float,
        offset: Float = 20f,
        text: String,
        above: Boolean = false
    ) {
        val isHorizontal = abs(y1 - y0) < 1e-3
        val p = stroke1
        if (isHorizontal) {
            val y = y0 + offset
            val a = min(x0, x1); val b = max(x0, x1)
            c.drawLine(x0, y0, x0, y, p)
            c.drawLine(x1, y1, x1, y, p)
            c.drawLine(a, y, b, y, p)
            drawArrow(c, a, y, a + 14f * sign(b - a), y, p)
            drawArrow(c, b, y, b - 14f * sign(b - a), y, p)
            val tw = dimText.measureText(text)
            c.drawText(text, (a + b) / 2f - tw / 2f, y + if (above) -6f else -4f, dimText)
        } else {
            val x = x0 + offset
            val a = min(y0, y1); val b = max(y0, y1)
            c.drawLine(x0, y0, x, y0, p)
            c.drawLine(x1, y1, x, y1, p)
            c.drawLine(x, a, x, b, p)
            drawArrow(c, x, a, x, a + 14f * sign(b - a), p)
            drawArrow(c, x, b, x, b - 14f * sign(b - a), p)
            c.drawText(text, x + 6f, (a + b) / 2f - 2f, dimText)
        }
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

    /* ───────────────────────── Utils ───────────────────────── */
    private fun mmToPt(mm: Double): Float = (mm * PT_PER_MM).toFloat()
    private fun fmt(v: Double, decimals: Int = 2): String = "%.${decimals}f".format(Locale.US, v)
    private fun sign(v: Float): Float = if (v < 0) -1f else 1f
}
