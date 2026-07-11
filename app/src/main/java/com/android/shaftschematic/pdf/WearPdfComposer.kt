package com.android.shaftschematic.pdf

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.android.shaftschematic.model.*
import com.android.shaftschematic.geom.computeOalWindow
import com.android.shaftschematic.geom.computeSetPositionsInMeasureSpace
import com.android.shaftschematic.settings.PdfPrefs
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
    pdfPrefs: PdfPrefs = PdfPrefs(),
    lineThicknessScale: Float = 1.0f,
) {
    val c = page.canvas
    c.drawColor(Color.WHITE)

    val pageW = page.info.pageWidth.toFloat()
    val pageH = page.info.pageHeight.toFloat()

    // ── Paints ──────────────────────────────────────────────────────────────
    val thicknessScale = lineThicknessScale.coerceIn(0.5f, 2.0f)
    val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = WEAR_OUTLINE_PT * thicknessScale; color = Color.BLACK
    }
    val dim = Paint(outline).apply { strokeWidth = WEAR_DIM_PT * thicknessScale }
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; textSize = WEAR_TEXT_PT
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        color = Color.BLACK
    }
    fun shadeFill() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(40, 0, 0, 0)
    }
    val bodyFill : Paint? = if (pdfPrefs.shadedBodies) shadeFill() else null
    val taperFill: Paint? = if (pdfPrefs.shadedTapers) shadeFill() else null
    val linerFill: Paint? = if (pdfPrefs.shadedLiners) shadeFill() else null

    // ── Page geometry ─────────────────────────────────────────────────────
    val margin       = WEAR_MARGIN_PT
    val contentLeft  = margin
    val contentRight = pageW - margin
    val contentW     = contentRight - contentLeft
    val contentTop   = margin
    val contentBot   = pageH - margin

    val headerBottom = contentTop + WEAR_HEADER_HEIGHT_PT

    // Notes anchored near the page bottom
    val notesY = contentBot - WEAR_NOTES_BOTTOM_OFFSET_PT

    // Shaft centred in the space between header and notes
    val midTop   = headerBottom + WEAR_HEADER_GAP_PT
    val midBot   = notesY - WEAR_NOTES_GAP_PT
    val shaftCy  = (midTop + midBot) * 0.5f
    val geomRect = RectF(contentLeft, midTop, contentRight, midBot)

    // ── Compute scale ────────────────────────────────────────────────────
    // Scale to the SET-to-SET span so the drawn shaft profile fills the page width.
    val oalWindow      = computeOalWindow(spec)
    val setPositions   = computeSetPositionsInMeasureSpace(oalWindow, spec)
    val aftSetMm       = setPositions.aftSETxMm.toFloat()
    val fwdSetMm       = setPositions.fwdSETxMm.toFloat()
    val drawSpanMm     = (fwdSetMm - aftSetMm).coerceAtLeast(1f)
    val ptPerMm        = contentW / drawSpanMm
    val measureStartMm = aftSetMm

    fun xAt(mm: Float): Float = contentLeft + (mm - measureStartMm) * ptPerMm
    fun rPx(diaMm: Float): Float = (diaMm * 0.5f) * ptPerMm

    // OAL dimension line sits just above the shaft's top outline.
    // Computed after scale so we can use the actual drawn shaft radius.
    val maxDiaMm       = (spec.bodies.maxOfOrNull { it.diaMm } ?: 50f).coerceAtLeast(20f)
    val shaftTopApprox = shaftCy - rPx(maxDiaMm)
    val oalLineY       = (shaftTopApprox - WEAR_OAL_ABOVE_SHAFT_PT)
        .coerceAtLeast(midTop + WEAR_TEXT_PT + 6f)

    // ── Header ───────────────────────────────────────────────────────────
    drawWearHeader(c, text, contentLeft, contentRight, contentTop, project, unit, drawSpanMm)

    // ── OAL line (with witness lines, anchored near shaft) ────────────────
    drawWearOalLine(c, dim, text, contentLeft, contentRight, oalLineY, shaftTopApprox, unit, drawSpanMm)

    // ── Shaft profile ─────────────────────────────────────────────────────
    drawWearShaftProfile(c, spec, shaftCy, outline, geomRect, ::xAt, ::rPx,
        bodyFill = bodyFill, taperFill = taperFill, linerFill = linerFill, ptPerMm = ptPerMm)

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
    val ts = text.textSize
    val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    val oalDisplay = if (unit == UnitSystem.INCHES) {
        "${"%.4f".format(oalMm / 25.4f)}\""
    } else {
        "${"%.2f".format(oalMm)} mm"
    }
    val side = project.side.printableLabelOrNull()?.let { "  $it" } ?: ""

    val line1 = buildString {
        if (project.customer.isNotBlank()) append("Customer: ${project.customer}   ")
        if (project.vessel.isNotBlank())   append("Vessel: ${project.vessel}   ")
        if (project.jobNumber.isNotBlank()) append("Job #: ${project.jobNumber}   ")
        append("Date: $date$side")
    }
    val line2 = "OAL: $oalDisplay  —  WEAR / INSPECTION RECORD"

    fun centeredX(str: String): Float =
        ((left + right - text.measureText(str)) * 0.5f).coerceAtLeast(left)

    c.drawText(line1, centeredX(line1), top + ts, text)
    c.drawText(line2, centeredX(line2), top + ts + ts * 1.4f, text)

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
    x0: Float, x1: Float,
    oalLineY: Float, shaftTopY: Float,
    unit: UnitSystem, oalMm: Float,
) {
    val arrowLen    = 8f
    val witnessGap  = 3f   // gap between shaft edge and witness line start
    val witnessExt  = 5f   // how far the witness line extends past the dimension line

    // Witness (extension) lines from shaft top up to past the dimension line
    c.drawLine(x0, shaftTopY - witnessGap, x0, oalLineY - witnessExt, dim)
    c.drawLine(x1, shaftTopY - witnessGap, x1, oalLineY - witnessExt, dim)

    // Dimension line with arrowheads
    c.drawLine(x0, oalLineY, x1, oalLineY, dim)
    c.drawLine(x0, oalLineY, x0 + arrowLen, oalLineY - arrowLen * 0.4f, dim)
    c.drawLine(x0, oalLineY, x0 + arrowLen, oalLineY + arrowLen * 0.4f, dim)
    c.drawLine(x1, oalLineY, x1 - arrowLen, oalLineY - arrowLen * 0.4f, dim)
    c.drawLine(x1, oalLineY, x1 - arrowLen, oalLineY + arrowLen * 0.4f, dim)

    // Label centred above the dimension line
    val label = if (unit == UnitSystem.INCHES) "OAL: ${"%.4f".format(oalMm / 25.4f)}\""
    else "OAL: ${"%.2f".format(oalMm)} mm"
    val lw = text.measureText(label)
    c.drawText(label, (x0 + x1) * 0.5f - lw * 0.5f, oalLineY - 4f, text)
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
    bodyFill: Paint? = null,
    taperFill: Paint? = null,
    linerFill: Paint? = null,
    ptPerMm: Float = 1f,
) {
    // ── Shade fills first (drawn under all outlines) ──────────────────────
    bodyFill?.let { f ->
        spec.bodies.forEach { b ->
            if (b.lengthMm <= 0f || b.diaMm <= 0f) return@forEach
            val r = rPx(b.diaMm)
            c.drawRect(xAt(b.startFromAftMm), cy - r, xAt(b.startFromAftMm + b.lengthMm), cy + r, f)
        }
    }
    taperFill?.let { f ->
        spec.tapers.forEach { t ->
            if (t.lengthMm <= 0f || (t.startDiaMm <= 0f && t.endDiaMm <= 0f)) return@forEach
            val path = android.graphics.Path().apply {
                moveTo(xAt(t.startFromAftMm), cy - rPx(t.startDiaMm))
                lineTo(xAt(t.startFromAftMm + t.lengthMm), cy - rPx(t.endDiaMm))
                lineTo(xAt(t.startFromAftMm + t.lengthMm), cy + rPx(t.endDiaMm))
                lineTo(xAt(t.startFromAftMm), cy + rPx(t.startDiaMm))
                close()
            }
            c.drawPath(path, f)
        }
    }
    linerFill?.let { f ->
        spec.liners.forEach { ln ->
            if (ln.lengthMm <= 0f || ln.odMm <= 0f) return@forEach
            val r = rPx(ln.odMm)
            c.drawRect(xAt(ln.startFromAftMm), cy - r, xAt(ln.startFromAftMm + ln.lengthMm), cy + r, f)
        }
    }
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
    // Threads — outline envelope + diagonal hatch so the machinist knows the zone is threaded
    val hatchPaint = Paint(outline).apply { strokeWidth = WEAR_DIM_PT * 0.6f; alpha = 160 }
    spec.threads.forEach { th ->
        if (th.lengthMm <= 0f || th.majorDiaMm <= 0f) return@forEach
        val x0 = xAt(th.startFromAftMm); val x1 = xAt(th.startFromAftMm + th.lengthMm)
        val r = rPx(th.majorDiaMm); val top = cy - r; val bot = cy + r
        val pitchPt = ((th.pitchMm.takeIf { it > 0f } ?: 2.5f) * ptPerMm).coerceIn(4f, 18f)
        val saved = c.save()
        c.clipRect(x0, top, x1, bot)
        var hx = x0 - (bot - top)
        while (hx <= x1) {
            c.drawLine(hx, bot, hx + (bot - top), top, hatchPaint)
            hx += pitchPt
        }
        c.restoreToCount(saved)
        c.drawLine(x0, top, x1, top, outline); c.drawLine(x0, bot, x1, bot, outline)
        c.drawLine(x0, top, x0, bot, outline); c.drawLine(x1, top, x1, bot, outline)
    }
    // Coupler bolt slots — reference cutouts, same as the main schematic.
    val slotFill = Paint(outline).apply { style = Paint.Style.FILL; alpha = 40 }
    drawCouplerBoltSlots(c, spec.couplerBoltSlots, spec, cy, xAt, rPx, outline, slotFill)
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

private const val WEAR_HEADER_HEIGHT_PT       = 36f   // 2-line header block height
private const val WEAR_HEADER_GAP_PT          = 16f   // gap from header rule to drawing area
private const val WEAR_OAL_ABOVE_SHAFT_PT     = 16f   // gap from shaft top edge to OAL line
private const val WEAR_NOTES_BOTTOM_OFFSET_PT = 24f   // notes baseline above contentBot
private const val WEAR_NOTES_GAP_PT           = 28f   // gap from drawing area bottom to notes

private const val COMPRESS_TRIGGER_PT = 220f
private const val ZIGZAG_GAP_MAX_PT   = 20f
