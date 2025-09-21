package com.android.shaftschematic.pdf

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.os.Environment
import androidx.core.graphics.withScale
import androidx.core.graphics.withTranslation
import com.android.shaftschematic.model.*
import com.android.shaftschematic.util.UnitSystem
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sign
import kotlin.math.sin

/**
 * ShaftPdfComposer (ShaftSpec in millimeters)
 *
 * PURPOSE
 *  - Render a complete shaft drawing to PDF using Android PdfDocument.
 *  - All geometry is taken from ShaftSpec (stored in mm). We convert to points (pt) for PDF.
 *  - Title block (bottom/landscape), optional grid underlay, scale-to-fit drawing band.
 *
 * KEY CHOICES
 *  - mm → pt via a single constant; no ad-hoc conversions.
 *  - Scale-to-fit horizontally; stroke widths/arrow lengths compensated for scale
 *    so dimension lines, leaders, and arrowheads look consistent.
 *  - Labels follow UI unit selection (mm | in) WITHOUT changing geometry math.
 *
 * NON-GOALS (for now)
 *  - Pagination across multiple pages (we fit to width).
 *  - Embedded metadata (PdfDocument has no metadata API).
 */

// ───────────────────────────── Units & page ─────────────────────────────
private const val POINTS_PER_INCH = 72f
private const val MM_PER_INCH = 25.4f
private val PT_PER_MM = POINTS_PER_INCH / MM_PER_INCH

// US Letter landscape
private const val PAGE_W_PT = 792   // 11 in * 72
private const val PAGE_H_PT = 612   // 8.5 in * 72

// ───────────────────────────── Layout ─────────────────────────────
private const val MARGIN_PT = 36f                // 0.5 in
private const val TITLE_BLOCK_H_PT = 90f         // bottom title block height
private const val LEFT_LEADER_GUTTER_PT = 80f    // space on left of drawing for leaders
private const val VERTICAL_PADDING_PT = 24f      // inner padding

// Dimension layout (above & below)
private const val DIM_ROW_SPACING_PT = 26f
private const val DIM_STACK_CLEAR_PT = 40f
private const val DIM_TEXT_OFFSET_PT = -18f

// Arrow geometry (in pt at page scale; we compensate for drawing scale)
private const val ARROW_DEG = 160.0
private val ARROW_RAD = ARROW_DEG * PI / 180.0

// ───────────────────────────── Paints (base) ─────────────────────────────
private val titleText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    textSize = 16f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = Color.BLACK
}
private val smallText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    textSize = 12f; color = Color.BLACK
}
private val dimText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    textSize = 11.5f; color = Color.BLACK
}
private val frameStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE; strokeWidth = 1.2f; color = Color.BLACK
}

// Scale-compensated stroke (keeps visual thickness stable regardless of drawing scale)
private fun scaledStroke(basePt: Float, scale: Float, color: Int = Color.BLACK) =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = basePt / max(1f, scale)
        this.color = color
    }

// ───────────────────────────── Public API ─────────────────────────────
object ShaftPdfComposer {

    /**
     * Export to an OutputStream (SAF-friendly).
     *
     * @param spec    canonical geometry in mm
     * @param unit    label unit for text (mm|in); geometry remains in mm
     * @param showGrid draw subtle grid underlay
     */
    fun exportToStream(
        context: Context,
        spec: ShaftSpec,
        unit: UnitSystem = UnitSystem.MILLIMETERS,
        showGrid: Boolean = false,
        out: OutputStream,
        title: String? = null   // ⬅️ NEW
    ) {
        val pdf = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_W_PT, PAGE_H_PT, 1).create()
        val page = pdf.startPage(pageInfo)
        val c = page.canvas

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

        // Optional: frame around working area (kept for now)
        c.drawRect(workRect, frameStroke)

        if (showGrid) {
            drawGrid(c, workRect, minorEveryMm = 10.0, majorEveryMm = 50.0)
        }

        drawShaftSheet(c, spec, workRect, unit)
        drawTitleBlock(c, spec, titleRect, unit, title)

        pdf.finishPage(page)
        pdf.writeTo(out)
        pdf.close()
    }

    /**
     * Export to an app-private file (Documents/… or files dir).
     */
    fun export(
        context: Context,
        spec: ShaftSpec,
        unit: UnitSystem = UnitSystem.MILLIMETERS,
        fileName: String = defaultFileName("shaft_drawing"),
        showGrid: Boolean = false
    ): File {
        val pdf = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_W_PT, PAGE_H_PT, 1).create()
        val page = pdf.startPage(pageInfo)
        val c = page.canvas

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

        c.drawRect(workRect, frameStroke)

        if (showGrid) {
            drawGrid(c, workRect, minorEveryMm = 10.0, majorEveryMm = 50.0)
        }

        drawShaftSheet(c, spec, workRect, unit)
        drawTitleBlock(c, spec, titleRect, unit)

        pdf.finishPage(page)

        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        val out = File(dir, fileName)
        FileOutputStream(out).use { pdf.writeTo(it) }
        pdf.close()
        return out
    }
}

// ───────────────────────────── Core drawing ─────────────────────────────

/** Normalized forms (forward-origin coordinates; doubles for math) */
private data class BodyN(val xStartMm: Double, val lengthMm: Double, val diaMm: Double)
private data class TaperN(val xStartMm: Double, val lengthMm: Double, val startDiaMm: Double, val endDiaMm: Double)
private data class ThreadN(val xStartMm: Double, val lengthMm: Double, val majorDiaMm: Double, val pitchMm: Double)
private data class LinerN(val xStartMm: Double, val lengthMm: Double, val odMm: Double)

private data class Normalized(
    val overallLenMm: Double,
    val bodies: List<BodyN>,
    val tapers: List<TaperN>,
    val threads: List<ThreadN>,
    val liners: List<LinerN>,
    val maxDiaMm: Double
)

/** Convert AFT-based start to FORWARD-based xStart (left→right). */
private fun aftToForwardStart(overallLenMm: Double, startFromAftMm: Double, lengthMm: Double): Double {
    // AFT origin: 0 at aft, increasing toward forward
    // FORWARD origin target: 0 at forward, increasing toward aft
    // x_fwd = overall - (startFromAft + length)
    return (overallLenMm - (startFromAftMm + lengthMm)).coerceAtLeast(0.0)
}

/** Gather lists and normalize to forward-origin working lists. */
private fun normalize(spec: ShaftSpec): Normalized {
    val overall = spec.overallLengthMm.toDouble()

    val bodies = buildList {
        spec.bodies.forEach { b ->
            val len = b.lengthMm.toDouble()
            if (len > 0.0) {
                val x = aftToForwardStart(overall, b.startFromAftMm.toDouble(), len)
                add(BodyN(x, len, b.diaMm.toDouble()))
            }
        }
    }.sortedBy { it.xStartMm }

    val tapers = buildList {
        spec.tapers.forEach { t ->
            val len = t.lengthMm.toDouble()
            if (len > 0.0) {
                val x = aftToForwardStart(overall, t.startFromAftMm.toDouble(), len)
                add(TaperN(x, len, t.startDiaMm.toDouble(), t.endDiaMm.toDouble()))
            }
        }
    }.sortedBy { it.xStartMm }

    val threads = buildList {
        spec.threads.forEach { th ->
            val len = th.lengthMm.toDouble()
            if (len > 0.0 && th.majorDiaMm > 0f) {
                val x = aftToForwardStart(overall, th.startFromAftMm.toDouble(), len)
                add(ThreadN(x, len, th.majorDiaMm.toDouble(), th.pitchMm.toDouble()))
            }
        }
    }.sortedBy { it.xStartMm }

    val liners = buildList {
        spec.liners.forEach { ln ->
            val len = ln.lengthMm.toDouble()
            if (len > 0.0 && ln.odMm > 0f) {
                val x = aftToForwardStart(overall, ln.startFromAftMm.toDouble(), len)
                add(LinerN(x, len, ln.odMm.toDouble()))
            }
        }
    }.sortedBy { it.xStartMm }

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

private fun drawShaftSheet(c: Canvas, spec: ShaftSpec, area: RectF, unit: UnitSystem) {
    val n = normalize(spec)

    // Fit-to-width scale and band layout
    val widthPt = area.width()
    val heightPt = area.height()
    val drawWidthPt = widthPt - LEFT_LEADER_GUTTER_PT
    val neededWPt = (n.overallLenMm * PT_PER_MM).toFloat()
    val neededHPt = (n.maxDiaMm * PT_PER_MM).toFloat() * 2f + 80f
    val scale = min(1f, min(drawWidthPt / neededWPt, (heightPt - 2 * VERTICAL_PADDING_PT) / neededHPt))

    c.withTranslation(area.left + LEFT_LEADER_GUTTER_PT, area.top + VERTICAL_PADDING_PT + 60f) {
        withScale(scale, scale) {
            val centerY = 150f

            // Scale-compensated strokes
            val strokeBold = scaledStroke(2f, scale)
            val strokeThin = scaledStroke(1.5f, scale)
            val pDim = scaledStroke(1.2f, scale)
            val pLeader = scaledStroke(1.0f, scale)
            val arrowLen = 14f / max(1f, scale)

            /* Bodies */
            n.bodies.forEach { b ->
                val x0 = mmToPt(b.xStartMm)
                val x1 = mmToPt(b.xStartMm + b.lengthMm)
                val half = mmToPt(b.diaMm) / 2f
                c.drawRect(x0, centerY - half, x1, centerY + half, strokeBold)
            }

            /* Tapers */
            n.tapers.forEach { t ->
                val x0 = mmToPt(t.xStartMm)
                val x1 = mmToPt(t.xStartMm + t.lengthMm)
                val half0 = mmToPt(t.startDiaMm) / 2f
                val half1 = mmToPt(t.endDiaMm) / 2f
                val p = Path().apply {
                    moveTo(x0, centerY - half0); lineTo(x1, centerY - half1)
                    lineTo(x1, centerY + half1); lineTo(x0, centerY + half0); close()
                }
                c.drawPath(p, strokeBold)
            }

            /* Threads (tick + leader text) */
            n.threads.forEach { th ->
                val x = mmToPt(th.xStartMm)
                c.drawLine(x, centerY - 22f, x, centerY + 22f, strokeThin)
                val text = buildString {
                    append("Threads Ø ").append(lenLabel(th.majorDiaMm, unit))
                    if (th.pitchMm > 0.0) append("  P ").append(lenLabel(th.pitchMm, unit))
                    append("  L ").append(lenLabel(th.lengthMm, unit))
                }
                drawLeaderText(c, x - 48f, centerY - 30f, text, pLeader)
            }

            /* Liners */
            n.liners.forEach { ln ->
                val x0 = mmToPt(ln.xStartMm)
                val x1 = mmToPt(ln.xStartMm + ln.lengthMm)
                val half = mmToPt(ln.odMm) / 2f
                c.drawRect(x0, centerY - half, x1, centerY + half, strokeThin)
                drawDim(
                    c,
                    x0, centerY + half + 24f,
                    x1, centerY + half + 24f,
                    offset = +22f,
                    text = "Liner Ø ${lenLabel(ln.odMm, unit)} × ${lenLabel(ln.lengthMm, unit)}",
                    above = false,
                    paint = pDim,
                    arrowLen = arrowLen
                )
            }

            /* Stacked dimension rows ABOVE shaft */
            val stackItems = collectDimItems(n, unit)
            if (stackItems.isNotEmpty()) {
                drawDimStackAbove(c, centerY, n.maxDiaMm, stackItems, pDim, arrowLen)
            }

            /* Overall length BELOW */
            val x0 = 0f
            val x1 = mmToPt(n.overallLenMm)
            drawDim(
                c,
                x0, centerY + mmToPt(n.maxDiaMm) / 2f + 70f,
                x1, centerY + mmToPt(n.maxDiaMm) / 2f + 70f,
                offset = +26f,
                text = "Overall ${lenLabel(n.overallLenMm, unit)}",
                above = false,
                paint = pDim,
                arrowLen = arrowLen
            )
        }
    }
}

// ───────────────────────────── Title block ─────────────────────────────
private fun drawTitleBlock(c: Canvas, spec: ShaftSpec, area: RectF, unit: UnitSystem, title: String? = null) {
    c.drawRect(area, frameStroke)
    val colW = area.width() / 3f
    c.drawLine(area.left + colW, area.top, area.left + colW, area.bottom, frameStroke)
    c.drawLine(area.left + 2 * colW, area.top, area.left + 2 * colW, area.bottom, frameStroke)

    val y0 = area.top + 20f
    val unitTxt = if (unit == UnitSystem.MILLIMETERS) "mm" else "in"
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

    val leftTitle = title?.takeIf { it.isNotBlank() } ?: "Shaft Schematic"
    c.drawText(leftTitle, area.left + 12f, y0, titleText)
    c.drawText("Overall: ${lenLabel(spec.overallLengthMm.toDouble(), unit)}", area.left + 12f, y0 + 18f, smallText)
    c.drawText("App: ShaftSchematic", area.left + 12f, y0 + 34f, smallText)

    c.drawText("Date: ${sdf.format(Date())}", area.left + colW + 12f, y0, smallText)
    c.drawText("Units: $unitTxt (fit to width)", area.left + colW + 12f, y0 + 18f, smallText)

    c.drawText("File: ${defaultFileName("shaft_drawing")}", area.left + 2 * colW + 12f, y0, smallText)
    c.drawText("Rev: A", area.left + 2 * colW + 12f, y0 + 18f, smallText)
}

// ───────────────────────────── Dimension & leaders ─────────────────────────────
private data class DimItem(val startMm: Double, val endMm: Double, val label: String)

private fun collectDimItems(n: Normalized, unit: UnitSystem): List<DimItem> {
    val items = mutableListOf<DimItem>()
    n.bodies.forEach { if (it.lengthMm > 0.0) items += DimItem(it.xStartMm, it.xStartMm + it.lengthMm, lenLabel(it.lengthMm, unit)) }
    n.liners.forEach { if (it.lengthMm > 0.0) items += DimItem(it.xStartMm, it.xStartMm + it.lengthMm, lenLabel(it.lengthMm, unit)) }
    n.tapers.forEach { if (it.lengthMm > 0.0) items += DimItem(it.xStartMm, it.xStartMm + it.lengthMm, lenLabel(it.lengthMm, unit)) }
    n.threads.forEach { if (it.lengthMm > 0.0) items += DimItem(it.xStartMm, it.xStartMm + it.lengthMm, lenLabel(it.lengthMm, unit)) }
    return items
}

private fun drawDimStackAbove(
    c: Canvas,
    centerY: Float,
    maxDiaMm: Double,
    items: List<DimItem>,
    paint: Paint,
    arrowLen: Float
) {
    var row = 0
    items.forEach { itx ->
        val x0 = mmToPt(itx.startMm)
        val x1 = mmToPt(itx.endMm)
        val y = dimRowYAbove(centerY, maxDiaMm, row++)
        drawDim(c, x0, y, x1, y, offset = DIM_TEXT_OFFSET_PT, text = itx.label, above = true, paint = paint, arrowLen = arrowLen)
    }
}

private fun dimRowYAbove(centerY: Float, maxDiaMm: Double, row: Int): Float {
    val odHalf = mmToPt(maxDiaMm) / 2f
    val base = centerY - odHalf - DIM_STACK_CLEAR_PT
    return base - row * DIM_ROW_SPACING_PT
}

private fun drawDim(
    c: Canvas,
    x0: Float, y0: Float,
    x1: Float, y1: Float,
    offset: Float = 20f,
    text: String,
    above: Boolean = false,
    paint: Paint,
    arrowLen: Float = 14f
) {
    val isHorizontal = abs(y1 - y0) < 1e-3
    val p = paint
    if (isHorizontal) {
        val y = y0 + offset
        val a = min(x0, x1); val b = max(x0, x1)
        c.drawLine(x0, y0, x0, y, p)
        c.drawLine(x1, y1, x1, y, p)
        c.drawLine(a, y, b, y, p)
        drawArrow(c, a, y, a + arrowLen * sign(b - a), y, p)
        drawArrow(c, b, y, b - arrowLen * sign(b - a), y, p)
        val tw = dimText.measureText(text)
        c.drawText(text, (a + b) / 2f - tw / 2f, y + if (above) -6f else -4f, dimText)
    } else {
        val x = x0 + offset
        val a = min(y0, y1); val b = max(y0, y1)
        c.drawLine(x0, y0, x, y0, p)
        c.drawLine(x1, y1, x, y1, p)
        c.drawLine(x, a, x, b, p)
        drawArrow(c, x, a, x, a + arrowLen * sign(b - a), p)
        drawArrow(c, x, b, x, b - arrowLen * sign(b - a), p)
        c.drawText(text, x + 6f, (a + b) / 2f - 2f, dimText)
    }
}

private fun drawLeaderText(c: Canvas, x: Float, y: Float, text: String, paint: Paint) {
    c.drawLine(x - 16f, y - 8f, x - 2f, y - 2f, paint)
    c.drawText(text, x, y, dimText)
}

// Arrowheads built from the segment direction
private fun drawArrow(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, p: Paint) {
    c.drawLine(x0, y0, x1, y1, p)
    val ang = atan2((y1 - y0).toDouble(), (x1 - x0).toDouble())
    val a1 = ang + ARROW_RAD
    val a2 = ang - ARROW_RAD
    val xA = (x1 + 6.0 * cos(a1)).toFloat()
    val yA = (y1 + 6.0 * sin(a1)).toFloat()
    val xB = (x1 + 6.0 * cos(a2)).toFloat()
    val yB = (y1 + 6.0 * sin(a2)).toFloat()
    c.drawLine(x1, y1, xA, yA, p)
    c.drawLine(x1, y1, xB, yB, p)
}

// ───────────────────────────── Grid & utils ─────────────────────────────
private fun drawGrid(c: Canvas, area: RectF, minorEveryMm: Double = 10.0, majorEveryMm: Double = 50.0) {
    val minorPt = mmToPt(minorEveryMm)
    val majorPt = mmToPt(majorEveryMm)

    val minor = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.6f
        color = Color.argb(40, 0, 0, 0)
    }
    val major = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.9f
        color = Color.argb(70, 0, 0, 0)
    }

    // Vertical lines
    run {
        var x = area.left
        var i = 0
        val mod = max(1, (majorPt / minorPt).toInt())
        while (x <= area.right + 0.5f) {
            val p = if ((i % mod) == 0) major else minor
            val xr = x.roundHalf()
            c.drawLine(xr, area.top, xr, area.bottom, p)
            x += minorPt
            i++
        }
    }
    // Horizontal lines
    run {
        var y = area.top
        var i = 0
        val mod = max(1, (majorPt / minorPt).toInt())
        while (y <= area.bottom + 0.5f) {
            val p = if ((i % mod) == 0) major else minor
            val yr = y.roundHalf()
            c.drawLine(area.left, yr, area.right, yr, p)
            y += minorPt
            i++
        }
    }
}

private fun Float.roundHalf(): Float = (round(this * 2f) / 2f)

/** mm → pt (PDF points, 1/72 in). */
private fun mmToPt(mm: Double): Float = (mm * PT_PER_MM).toFloat()

/** Unit-aware label (mm or in), integer when whole, trimmed decimals otherwise. */
private fun lenLabel(mm: Double, unit: UnitSystem, decimals: Int = 3): String {
    val (value, suffix) = if (unit == UnitSystem.MILLIMETERS) {
        mm to " mm"
    } else {
        (mm / MM_PER_IN) to " in"
    }
    val s = "%.${decimals}f".format(Locale.US, value).trimEnd('0').trimEnd('.')
    return if (s.isEmpty()) "0$suffix" else s + suffix
}

private fun defaultFileName(prefix: String): String {
    val ts = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
    return "${prefix}_$ts.pdf"
}
