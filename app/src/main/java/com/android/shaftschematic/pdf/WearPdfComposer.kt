package com.android.shaftschematic.pdf

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.android.shaftschematic.model.*
import com.android.shaftschematic.geom.computeOalWindow
import com.android.shaftschematic.util.UnitSystem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

// ──────────────────────────────────────────────────────────────────────────────
// Public entry point
// ──────────────────────────────────────────────────────────────────────────────

/**
 * WearPdfComposer
 *
 * Generates a shaft wear / inspection record PDF page.
 *
 * ## Purpose
 * A printable form the machinist uses in the field to mark damage, pitting, and
 * dye-penetrant inspection results directly on a simplified shaft outline.
 * The drawing is intentionally blank inside the shaft profile — the machinist
 * draws X's, circles, and notes by hand on the printed page.
 *
 * ## Page layout (landscape US Letter, 792 × 612 pt)
 * ```
 * ┌─── header: Customer / Vessel / Job# / Date / Side ────────────────────────┐
 * │   ←────────── OAL (AFT SET → FWD SET) ─────────────────────────→         │
 * │                                                                             │
 * │   [shaft profile — large, centred, blank interior for hand annotation]     │
 * │                                                                             │
 * │   Dye pen inspection:  PASS □   FAIL □     Notes: ____________________    │
 * └─────────────────────────────────────────────────────────────────────────────┘
 * ```
 *
 * ## Future enhancements (Phase 2)
 * - Digital damage annotation: tap to mark damage zones, severity selector, dye-pen pass/fail toggle.
 * - Damage zones render as coloured overlays on the shaft profile, printed with a legend.
 *
 * @param page    Target PDF page (US Letter landscape, already started).
 * @param spec    Shaft specification in millimeters.
 * @param project Job information (customer, vessel, job#, side).
 * @param unit    Display unit for the OAL dimension label.
 */
fun composeWearPdf(
    page: PdfDocument.Page,
    spec: ShaftSpec,
    project: ProjectInfo,
    unit: UnitSystem,
) {
    val c = page.canvas
    c.drawColor(Color.WHITE)

    val pageW = page.info.pageWidth.toFloat()
    val pageH = page.info.pageHeight.toFloat()

    // ── Paints ──────────────────────────────────────────────────────────────
    val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = WEAR_OUTLINE_PT; color = Color.BLACK
    }
    val dim = Paint(outline).apply { strokeWidth = WEAR_DIM_PT }
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; textSize = WEAR_TEXT_PT
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        color = Color.BLACK
    }

    // ── Page geometry ─────────────────────────────────────────────────────
    val margin       = WEAR_MARGIN_PT
    val contentLeft  = margin
    val contentRight = pageW - margin
    val contentW     = contentRight - contentLeft
    val contentTop   = margin
    val contentBot   = pageH - margin

    val headerBottom = contentTop + WEAR_HEADER_HEIGHT_PT
    val oalLineY     = headerBottom + WEAR_OAL_GAP_PT

    // Shaft draws large — give it more vertical real-estate than the runout sheet
    val shaftCy   = oalLineY + WEAR_OAL_SPACE_PT + WEAR_SHAFT_HALF_HEIGHT_PT
    val shaftBottom = shaftCy + WEAR_SHAFT_HALF_HEIGHT_PT
    val geomRect  = RectF(contentLeft, contentTop, contentRight, contentBot)

    val notesY = min(shaftBottom + 48f, contentBot - 16f)

    // ── Compute scale ────────────────────────────────────────────────────
    val oalWindow   = computeOalWindow(spec)
    val drawSpanMm  = oalWindow.oalMm.toFloat().coerceAtLeast(1f)
    val ptPerMm     = contentW / drawSpanMm
    val measureStartMm = oalWindow.measureStartMm.toFloat()

    fun xAt(mm: Float): Float = contentLeft + (mm - measureStartMm) * ptPerMm
    fun rPx(diaMm: Float): Float = (diaMm * 0.5f) * ptPerMm

    // ── Header ───────────────────────────────────────────────────────────
    drawWearHeader(c, text, contentLeft, contentRight, contentTop, project, unit, oalWindow.oalMm.toFloat())

    // ── OAL line ──────────────────────────────────────────────────────────
    drawWearOalLine(c, dim, text, contentLeft, contentRight, oalLineY, unit, oalWindow.oalMm.toFloat())

    // ── Shaft profile ─────────────────────────────────────────────────────
    drawWearShaftProfile(c, spec, shaftCy, outline, geomRect, ::xAt, ::rPx)

    // ── Notes / dye-pen area ──────────────────────────────────────────────
    drawWearNotesArea(c, text, contentLeft, contentRight, notesY)
}

// ──────────────────────────────────────────────────────────────────────────────
// Header
// ──────────────────────────────────────────────────────────────────────────────

private fun drawWearHeader(
    c: Canvas,
    text: Paint,
    left: Float,
    right: Float,
    top: Float,
    project: ProjectInfo,
    unit: UnitSystem,
    oalMm: Float,
) {
    val y = top + text.textSize + 2f
    val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    val oalDisplay = if (unit == UnitSystem.INCHES) {
        "${"%.4f".format(oalMm / 25.4f)} in"
    } else {
        "${"%.2f".format(oalMm)} mm"
    }
    val side = project.side.printableLabelOrNull()?.let { "  $it" } ?: ""
    val header = buildString {
        if (project.customer.isNotBlank()) append("Customer: ${project.customer}   ")
        if (project.vessel.isNotBlank())   append("Vessel: ${project.vessel}   ")
        if (project.jobNumber.isNotBlank()) append("Job #: ${project.jobNumber}   ")
        append("Date: $date$side   OAL: $oalDisplay   — WEAR / INSPECTION RECORD")
    }
    c.drawText(header, left, y, text)
    val ruleY = top + WEAR_HEADER_HEIGHT_PT
    c.drawLine(left, ruleY, right, ruleY, Paint(text).apply {
        style = Paint.Style.STROKE; strokeWidth = 0.5f
    })
}

// ──────────────────────────────────────────────────────────────────────────────
// OAL line
// ──────────────────────────────────────────────────────────────────────────────

private fun drawWearOalLine(
    c: Canvas, dim: Paint, text: Paint,
    x0: Float, x1: Float, y: Float,
    unit: UnitSystem, oalMm: Float,
) {
    val arrowLen = 8f
    c.drawLine(x0, y, x1, y, dim)
    c.drawLine(x0, y, x0 + arrowLen, y - arrowLen * 0.5f, dim)
    c.drawLine(x0, y, x0 + arrowLen, y + arrowLen * 0.5f, dim)
    c.drawLine(x1, y, x1 - arrowLen, y - arrowLen * 0.5f, dim)
    c.drawLine(x1, y, x1 - arrowLen, y + arrowLen * 0.5f, dim)
    val label = if (unit == UnitSystem.INCHES) "OAL: ${"%.4f".format(oalMm / 25.4f)}\""
    else "OAL: ${"%.2f".format(oalMm)} mm"
    val lw = text.measureText(label)
    c.drawText(label, (x0 + x1) * 0.5f - lw * 0.5f, y - 4f, text)
}

// ──────────────────────────────────────────────────────────────────────────────
// Shaft profile (same drawing logic as runout, just larger)
// ──────────────────────────────────────────────────────────────────────────────

private fun drawWearShaftProfile(
    c: Canvas,
    spec: ShaftSpec,
    cy: Float,
    outline: Paint,
    geomRect: RectF,
    xAt: (Float) -> Float,
    rPx: (Float) -> Float,
) {
    // Bodies with compression breaks
    val capPaint = Paint(outline)
    spec.bodies.forEach { b ->
        if (b.lengthMm <= 0f || b.diaMm <= 0f) return@forEach
        val x0 = xAt(b.startFromAftMm); val x1 = xAt(b.startFromAftMm + b.lengthMm)
        val r = rPx(b.diaMm); val top = cy - r; val bot = cy + r
        val lenPt = abs(x1 - x0)
        if (lenPt < COMPRESS_TRIGGER_PT) {
            c.drawLine(x0, top, x1, top, outline); c.drawLine(x0, bot, x1, bot, outline)
            c.drawLine(x0, top, x0, bot, outline); c.drawLine(x1, top, x1, bot, outline)
        } else {
            val mid = (x0 + x1) * 0.5f; val gap = min(ZIGZAG_GAP_MAX_PT, 0.25f * lenPt)
            val half = gap * 0.5f; val amp = r * 0.6f
            val lEnd = (mid - half).coerceIn(geomRect.left, geomRect.right)
            val rBeg = (mid + half).coerceIn(geomRect.left, geomRect.right)
            c.drawLine(x0, top, lEnd, top, outline); c.drawLine(x0, bot, lEnd, bot, outline)
            c.drawLine(x0, top, x0, bot, outline)
            drawWearBreakEdge(c, lEnd, top, bot, amp, capPaint)
            drawWearBreakEdge(c, rBeg, top, bot, amp, capPaint)
            c.drawLine(rBeg, top, x1, top, outline); c.drawLine(rBeg, bot, x1, bot, outline)
            c.drawLine(x1, top, x1, bot, outline)
        }
    }
    // Tapers
    spec.tapers.forEach { t ->
        if (t.lengthMm <= 0f || (t.startDiaMm <= 0f && t.endDiaMm <= 0f)) return@forEach
        val x0 = xAt(t.startFromAftMm); val x1 = xAt(t.startFromAftMm + t.lengthMm)
        val top0 = cy - rPx(t.startDiaMm); val bot0 = cy + rPx(t.startDiaMm)
        val top1 = cy - rPx(t.endDiaMm);   val bot1 = cy + rPx(t.endDiaMm)
        c.drawLine(x0, top0, x1, top1, outline); c.drawLine(x0, bot0, x1, bot1, outline)
        c.drawLine(x0, top0, x0, bot0, outline); c.drawLine(x1, top1, x1, bot1, outline)
    }
    // Liners
    val dimPaint = Paint(outline).apply { strokeWidth = WEAR_DIM_PT }
    spec.liners.forEach { ln ->
        if (ln.lengthMm <= 0f || ln.odMm <= 0f) return@forEach
        val x0 = xAt(ln.startFromAftMm); val x1 = xAt(ln.startFromAftMm + ln.lengthMm)
        val r = rPx(ln.odMm); val top = cy - r; val bot = cy + r
        c.drawLine(x0, top, x1, top, outline); c.drawLine(x0, bot, x1, bot, outline)
        c.drawLine(x0, top, x0, bot, dimPaint); c.drawLine(x1, top, x1, bot, dimPaint)
    }
}

private fun drawWearBreakEdge(c: Canvas, x: Float, yTop: Float, yBot: Float, amplitude: Float, p: Paint) {
    val h = yBot - yTop; val q = h / 4f
    val path = android.graphics.Path().apply {
        moveTo(x, yTop)
        cubicTo(x + amplitude, yTop + q, x - amplitude, yTop + q * 2f, x, yTop + q * 2f)
        cubicTo(x + amplitude, yTop + q * 3f, x - amplitude, yBot, x, yBot)
    }
    c.drawPath(path, p)
}

// ──────────────────────────────────────────────────────────────────────────────
// Notes / dye-pen area
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Draw the dye penetrant result checkboxes and a notes field at the bottom of the page.
 *
 * The machinist circles PASS or FAIL by hand, then adds free-form notes about damage,
 * pitting locations, or other observations. Phase 2 will add digital entry for these.
 */
private fun drawWearNotesArea(
    c: Canvas,
    text: Paint,
    left: Float,
    right: Float,
    y: Float,
) {
    val boxSize = 10f
    var x = left

    c.drawText("Dye pen inspection: ", x, y, text)
    x += text.measureText("Dye pen inspection: ")

    // PASS checkbox
    c.drawRect(x, y - boxSize, x + boxSize, y, Paint(text).apply { style = Paint.Style.STROKE; strokeWidth = 1f })
    x += boxSize + 4f
    c.drawText("PASS", x, y, text); x += text.measureText("PASS") + 20f

    // FAIL checkbox
    c.drawRect(x, y - boxSize, x + boxSize, y, Paint(text).apply { style = Paint.Style.STROKE; strokeWidth = 1f })
    x += boxSize + 4f
    c.drawText("FAIL", x, y, text); x += text.measureText("FAIL") + 24f

    // Notes fill-in line
    c.drawText("Notes:", x, y, text); x += text.measureText("Notes:") + 6f
    c.drawLine(x, y + 2f, right, y + 2f, Paint(text).apply { style = Paint.Style.STROKE; strokeWidth = 0.7f })
}

// ──────────────────────────────────────────────────────────────────────────────
// Constants
// ──────────────────────────────────────────────────────────────────────────────

private const val WEAR_OUTLINE_PT = 2.0f
private const val WEAR_DIM_PT     = 1.2f
private const val WEAR_TEXT_PT    = 10f
private const val WEAR_MARGIN_PT  = 36f

private const val WEAR_HEADER_HEIGHT_PT = 22f
private const val WEAR_OAL_GAP_PT       = 8f
private const val WEAR_OAL_SPACE_PT     = 18f

// Shaft draws taller on the wear doc (more room to annotate by hand)
private const val WEAR_SHAFT_HALF_HEIGHT_PT = 60f

private const val COMPRESS_TRIGGER_PT = 220f
private const val ZIGZAG_GAP_MAX_PT   = 20f
