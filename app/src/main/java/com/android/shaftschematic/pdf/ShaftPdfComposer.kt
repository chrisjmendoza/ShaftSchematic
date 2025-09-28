// File: com/android/shaftschematic/pdf/ShaftPdfComposer.kt
// v7.1 — polish + streamlining (2025‑09‑27)
// • Keeps the v7 behavior you approved (dimension lanes, extension lines, title stamp, 1" info gap, taper/thread footer).
// • Minor cleanup: remove unused imports; clearer KDoc; constants grouped.
// • This is the **single canonical** top‑level composer function.

package com.android.shaftschematic.pdf

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.android.shaftschematic.model.*
import com.android.shaftschematic.util.UnitSystem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// ------------------------------- Constants -------------------------------
private const val MM_PER_IN = 25.4f

// Strokes / text
private const val OUTLINE_PT = 2.5f
private const val DIM_PT = 1.6f
private const val TEXT_PT = 12f

// Layout
private const val PAGE_MARGIN_PT = 36f       // 0.5 in
private const val BAND_CLEAR_PT = 12f        // breathing room above shaft before first dim line
private const val BASE_DIM_OFFSET_PT = 24f   // ~1/3 in for component lines
private const val OVERALL_EXTRA_PT = 16f     // overall sits higher than components
private const val LANE_GAP_PT = 24f          // spacing between lanes
private const val ARROW_PT = 6f
private const val LABEL_PAD_PT = 6f
private const val EXT_OFFSET_PT = 9f         // ~1/8 in gap from shaft to start of extension line
private const val EXT_OVERRUN_PT = 4f        // how much extension lines rise past the dimension line
private const val INFO_GAP_PT = 72f          // exactly 1 inch
private const val EPS_END_MM = 0.25f

// ------------------------------- Models ----------------------------------
/** Mid‑column work‑order info for footer. */
data class ProjectInfo(
    val customer: String = "",
    val vessel: String = "",
    val jobNumber: String = "",
)

/**
 * File: ShaftPdfComposer.kt
 * Layer: Export → PDF
 * Purpose: Convert the same mm‑based shaft geometry and metadata into a paginated PDF.
 *
 * Responsibilities
 * • Mirror on‑screen layout where sensible; keep a dedicated PDF layout for print fidelity.
 * • Use mm as the canonical unit; convert to PDF points only at the final draw step.
 * • Encapsulate text styles, line styles, and margins; provide a stable title block API.
 *
 * Contracts
 * • Inputs: ShaftSpec (mm), RenderOptions (strokes/fonts), page size (e.g., Letter/A4 in mm).
 * • Outputs: ByteArray/File; never hold references to Android UI objects.
 *
 * Notes
 * • Be deterministic for testing; avoid timestamps in output unless provided.
 * • Thread hatch/texture should be PDF‑safe (no overly dense pattern that explodes file size).
 */
fun composeShaftPdf(
    page: PdfDocument.Page,
    spec: ShaftSpec,
    unit: UnitSystem,
    project: ProjectInfo,
    appVersion: String,
    filename: String,
) {
    val c = page.canvas
    val pageW = page.info.pageWidth.toFloat()
    val pageH = page.info.pageHeight.toFloat()

    // Geometry area
    val geomRect = RectF(
        PAGE_MARGIN_PT,
        PAGE_MARGIN_PT + TEXT_PT, // small top margin
        pageW - PAGE_MARGIN_PT,
        pageH - PAGE_MARGIN_PT - 160f // reserve for info + page stamp
    )

    // Paints
    val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = OUTLINE_PT; color = 0xFF000000.toInt()
    }
    val dim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = DIM_PT; color = 0xFF000000.toInt()
    }
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; textSize = TEXT_PT; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        color = 0xFF000000.toInt()
    }

    // Scale (fit to width)
    val overall = max(1f, spec.overallLengthMm)
    val ptPerMm = geomRect.width() / overall
    val left = geomRect.left

    // Vertical placement
    val maxDia = spec.maxOuterDiaMm().coerceAtLeast(1f)
    val halfHeight = (maxDia * 0.5f) * ptPerMm
    val cy = (geomRect.top + geomRect.bottom) * 0.5f - 40f // bias up to give room for info below
    val yTop = cy - halfHeight

    fun xAt(mm: Float) = left + mm * ptPerMm
    fun rPx(diaMm: Float) = (diaMm * 0.5f) * ptPerMm

    // 1) Components (centerline intentionally off)
    drawBodies(c, spec.bodies, cy, ::xAt, ::rPx, outline)
    drawTapers(c, spec.tapers, cy, ::xAt, ::rPx, outline)
    drawThreads(c, spec.threads, cy, ::xAt, ::rPx, outline, dim, ptPerMm)
    drawLiners(c, spec.liners, cy, ::xAt, ::rPx, outline, dim)

    // 2) Dimension system just above the shaft
    drawDimensionsLikeSketch(c, spec, unit, ::xAt, yTop, text, dim)

    // 3) Info columns placed **1 inch** below geometry
    val infoTop = cy + halfHeight + INFO_GAP_PT
    val infoBottom = min(infoTop + 96f, pageH - PAGE_MARGIN_PT) // a bit taller than before
    val infoRect = RectF(geomRect.left, infoTop, geomRect.right, infoBottom)
    drawFooter(c, infoRect, spec, unit, project, filename, appVersion, text)

    // 4) Page-bottom stamp (bottom-right of the PAGE, not the info block)
    val meta = "$filename  •  ShaftSchematic $appVersion"
    val metaW = text.measureText(meta)
    c.drawText(meta, pageW - PAGE_MARGIN_PT - metaW, pageH - PAGE_MARGIN_PT - 4f, text)
}

// ------------------------------ Components ------------------------------
private fun drawBodies(
    c: Canvas,
    bodies: List<Body>,
    cy: Float,
    xAt: (Float) -> Float,
    rPx: (Float) -> Float,
    outline: Paint,
) {
    bodies.forEach { b ->
        if (b.lengthMm <= 0f || b.diaMm <= 0f) return@forEach
        val x0 = xAt(b.startFromAftMm); val x1 = xAt(b.startFromAftMm + b.lengthMm)
        val r = rPx(b.diaMm); val top = cy - r; val bot = cy + r
        c.drawLine(x0, top, x1, top, outline)
        c.drawLine(x0, bot, x1, bot, outline)
        c.drawLine(x0, top, x0, bot, outline)
        c.drawLine(x1, top, x1, bot, outline)
    }
}

private fun drawTapers(
    c: Canvas,
    tapers: List<Taper>,
    cy: Float,
    xAt: (Float) -> Float,
    rPx: (Float) -> Float,
    outline: Paint,
) {
    tapers.forEach { t ->
        if (t.lengthMm <= 0f || (t.startDiaMm <= 0f && t.endDiaMm <= 0f)) return@forEach
        val x0 = xAt(t.startFromAftMm); val x1 = xAt(t.startFromAftMm + t.lengthMm)
        val r0 = rPx(t.startDiaMm); val r1 = rPx(t.endDiaMm)
        val top0 = cy - r0; val bot0 = cy + r0
        val top1 = cy - r1; val bot1 = cy + r1
        c.drawLine(x0, top0, x1, top1, outline)
        c.drawLine(x0, bot0, x1, bot1, outline)
        c.drawLine(x0, top0, x0, bot0, outline)
        c.drawLine(x1, top1, x1, bot1, outline)
    }
}

private fun drawThreads(
    c: Canvas,
    threads: List<ThreadSpec>,
    cy: Float,
    xAt: (Float) -> Float,
    rPx: (Float) -> Float,
    outline: Paint,
    dim: Paint,
    ptPerMm: Float,
) {
    threads.forEach { th ->
        if (th.lengthMm <= 0f || th.majorDiaMm <= 0f) return@forEach
        val x0 = xAt(th.startFromAftMm); val x1 = xAt(th.startFromAftMm + th.lengthMm)
        val r = rPx(th.majorDiaMm); val top = cy - r; val bot = cy + r
        // Outline
        c.drawLine(x0, top, x1, top, outline)
        c.drawLine(x0, bot, x1, bot, outline)
        c.drawLine(x0, top, x0, bot, outline)
        c.drawLine(x1, top, x1, bot, outline)
        // Hatch from pitch — clipped to rect; stripes via drawLine (robust on PDF)
        val step = th.pitchMm * ptPerMm
        if (step > 0f) {
            val save = c.save()
            c.clipRect(RectF(x0, top, x1, bot))
            var x = x0
            while (x <= x1 + step) {
                c.drawLine(x, bot, x + (bot - top), top, dim)
                x += step
            }
            c.restoreToCount(save)
        }
    }
}

private fun drawLiners(
    c: Canvas,
    liners: List<Liner>,
    cy: Float,
    xAt: (Float) -> Float,
    rPx: (Float) -> Float,
    outline: Paint,
    dim: Paint,
) {
    liners.forEach { ln ->
        if (ln.lengthMm <= 0f || ln.odMm <= 0f) return@forEach
        val x0 = xAt(ln.startFromAftMm); val x1 = xAt(ln.startFromAftMm + ln.lengthMm)
        val r = rPx(ln.odMm); val top = cy - r; val bot = cy + r
        c.drawLine(x0, top, x1, top, outline)
        c.drawLine(x0, bot, x1, bot, outline)
        c.drawLine(x0, top, x0, bot, dim)
        c.drawLine(x1, top, x1, bot, dim)
    }
}

// --------------------------- Dimensions like sketches ---------------------------
private data class Interval(val startMm: Float, val endMm: Float)

private fun drawDimensionsLikeSketch(
    c: Canvas,
    spec: ShaftSpec,
    unit: UnitSystem,
    xAt: (Float) -> Float,
    yTop: Float,
    text: Paint,
    dim: Paint,
) {
    // Overall above components; both above the shaft top.
    val overallY = yTop - BAND_CLEAR_PT - BASE_DIM_OFFSET_PT - OVERALL_EXTRA_PT
    val firstLaneY = yTop - BAND_CLEAR_PT - BASE_DIM_OFFSET_PT

    // Overall
    val xa = xAt(0f); val xf = xAt(spec.overallLengthMm)
    drawDimWithExtensions(c, xa, xf, overallY, yTop, fmtLen(unit, spec.overallLengthMm), text, dim)

    // Component intervals
    val ivs = ArrayList<Interval>()
    spec.bodies.forEach   { if (it.lengthMm > 0f && it.diaMm > 0f)           ivs += Interval(it.startFromAftMm, it.startFromAftMm + it.lengthMm) }
    spec.tapers.forEach   { if (it.lengthMm > 0f && (it.startDiaMm > 0f || it.endDiaMm > 0f)) ivs += Interval(it.startFromAftMm, it.startFromAftMm + it.lengthMm) }
    spec.threads.forEach  { if (it.lengthMm > 0f && it.majorDiaMm > 0f)      ivs += Interval(it.startFromAftMm, it.startFromAftMm + it.lengthMm) }
    spec.liners.forEach   { if (it.lengthMm > 0f && it.odMm > 0f)            ivs += Interval(it.startFromAftMm, it.startFromAftMm + it.lengthMm) }

    // Greedy lanes with extra spacing
    data class Lane(var endX: Float, val y: Float)
    val lanes = mutableListOf<Lane>()
    val sorted = ivs.sortedWith(compareBy({ it.startMm }, { it.endMm }))

    sorted.forEach { iv ->
        val x0 = xAt(iv.startMm); val x1 = xAt(iv.endMm)
        var idx = -1
        for (i in lanes.indices) if (x0 >= lanes[i].endX) { idx = i; break }
        if (idx == -1) { lanes += Lane(x1, firstLaneY - lanes.size * (TEXT_PT + LANE_GAP_PT)); idx = lanes.lastIndex } else { lanes[idx].endX = x1 }
        val y = lanes[idx].y
        drawDimWithExtensions(c, x0, x1, y, yTop, fmtLen(unit, iv.endMm - iv.startMm), text, dim)
    }
}

private fun drawDimWithExtensions(
    c: Canvas,
    x0: Float,
    x1: Float,
    yDim: Float,
    yTopOfShaft: Float,
    label: String,
    text: Paint,
    dim: Paint,
) {
    // Extension lines rise from just above the shaft to slightly beyond the dimension line
    val extStartY = yTopOfShaft - EXT_OFFSET_PT
    val extEndY = yDim + EXT_OVERRUN_PT
    c.drawLine(x0, extStartY, x0, extEndY, dim)
    c.drawLine(x1, extStartY, x1, extEndY, dim)

    // Baseline with handwriting gap
    val mid = (x0 + x1) * 0.5f
    val w = text.measureText(label)
    val gap = w + LABEL_PAD_PT * 2
    val leftEnd = mid - gap / 2f
    val rightStart = mid + gap / 2f
    c.drawLine(x0, yDim, leftEnd, yDim, dim)
    c.drawLine(rightStart, yDim, x1, yDim, dim)

    // Inward arrowheads
    drawArrowInward(c, x0, yDim, dim)
    drawArrowInward(c, x1, yDim, dim, left = false)

    // Center label vertically on the baseline using ascent/descent
    val ty = yDim - (text.descent() + text.ascent()) / 2f
    c.drawText(label, mid - w / 2f, ty, text)
}

private fun drawArrowInward(c: Canvas, x: Float, y: Float, p: Paint, left: Boolean = true) {
    val s = if (left) 1f else -1f
    c.drawLine(x, y, x + s * ARROW_PT, y - ARROW_PT, p)
    c.drawLine(x, y, x + s * ARROW_PT, y + ARROW_PT, p)
}

// -------------------------------- Footer ---------------------------------
private fun drawFooter(
    c: Canvas,
    rect: RectF,
    spec: ShaftSpec,
    unit: UnitSystem,
    project: ProjectInfo,
    filename: String,
    appVersion: String,
    text: Paint,
) {
    val gutter = 16f
    val innerW = rect.width() - gutter * 2
    val colW = innerW / 3f

    val leftX = rect.left
    val midX = leftX + colW + gutter
    val rightX = midX + colW + gutter

    val top = rect.top + 6f
    val lh = text.textSize * 1.35f

    val (aftTaper, fwdTaper) = pickAftFwdTapers(spec)

    // Left (AFT)
    run {
        var y = top
        if (aftTaper != null) {
            val (let, set) = letSet(aftTaper)
            c.drawText("AFT Taper", leftX, y, text); y += lh
            c.drawText("L.E.T.: ${fmtDia(unit, let)}", leftX, y, text); y += lh
            c.drawText("S.E.T.: ${fmtDia(unit, set)}", leftX, y, text); y += lh
            c.drawText("Length: ${fmtLen(unit, aftTaper.lengthMm)}", leftX, y, text); y += lh
            c.drawText("Rate: ${rate1toN(aftTaper)}", leftX, y, text); y += lh
        }
        findAftThread(spec)?.let { th ->
            val tpi = tpiFromPitch(th.pitchMm)
            val dia = fmtDiaWithUnit(unit, th.majorDiaMm)
            val len = fmtLen(unit, th.lengthMm)
            c.drawText("Thread: ${dia} × ${fmtTpi(tpi)} TPI × ${len}", leftX, y, text)
        }
    }

    // Middle (Work order)
    run {
        var y = top
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        c.drawText("Customer: ${project.customer}", midX, y, text); y += lh
        c.drawText("Vessel: ${project.vessel}", midX, y, text); y += lh
        c.drawText("Job #: ${project.jobNumber}", midX, y, text); y += lh
        c.drawText("Date: $date", midX, y, text); y += lh
    }

    // Right (FWD)
    run {
        var y = top
        if (fwdTaper != null) {
            val (let, set) = letSet(fwdTaper)
            c.drawText("FWD Taper", rightX, y, text); y += lh
            c.drawText("L.E.T.: ${fmtDia(unit, let)}", rightX, y, text); y += lh
            c.drawText("S.E.T.: ${fmtDia(unit, set)}", rightX, y, text); y += lh
            c.drawText("Length: ${fmtLen(unit, fwdTaper.lengthMm)}", rightX, y, text); y += lh
            c.drawText("Rate: ${rate1toN(fwdTaper)}", rightX, y, text); y += lh
        }
        findFwdThread(spec)?.let { th ->
            val tpi = tpiFromPitch(th.pitchMm)
            val dia = fmtDiaWithUnit(unit, th.majorDiaMm)
            val len = fmtLen(unit, th.lengthMm)
            c.drawText("Thread: ${dia} × ${fmtTpi(tpi)} TPI × ${len}", rightX, y, text)
        }
    }
}

// -------------------------------- Helpers --------------------------------
private fun pickAftFwdTapers(spec: ShaftSpec): Pair<Taper?, Taper?> {
    if (spec.tapers.isEmpty()) return null to null
    var aft: Taper? = spec.tapers.minByOrNull { it.startFromAftMm }
    var fwd: Taper? = spec.tapers.maxByOrNull { it.startFromAftMm + it.lengthMm }
    if (aft == fwd) {
        // Single taper: place it on the nearer end
        val t = aft!!
        val distAft = t.startFromAftMm
        val distFwd = spec.overallLengthMm - (t.startFromAftMm + t.lengthMm)
        if (distAft <= distFwd) { fwd = null } else { aft = null }
    }
    return aft to fwd
}

private fun findAftThread(spec: ShaftSpec): ThreadSpec? = spec.threads.minByOrNull { it.startFromAftMm }
private fun findFwdThread(spec: ShaftSpec): ThreadSpec? = spec.threads.maxByOrNull { it.startFromAftMm + it.lengthMm }

private fun letSet(t: Taper): Pair<Float, Float> {
    val let = max(t.startDiaMm, t.endDiaMm)
    val set = min(t.startDiaMm, t.endDiaMm)
    return let to set
}

private fun rate1toN(t: Taper): String {
    val d = abs(t.startDiaMm - t.endDiaMm)
    if (d <= 1e-4f || t.lengthMm <= 0f) return "—"
    val n = t.lengthMm / d
    return "1:" + String.format(Locale.US, "%.0f", n)
}

private fun fmtLen(unit: UnitSystem, mm: Float): String = when (unit.name.uppercase(Locale.US)) {
    "MILLIMETERS", "MM" -> String.format(Locale.US, "%.1f mm", mm)
    "INCHES", "IN" -> String.format(Locale.US, "%.3f", mm / MM_PER_IN).trimEnd('0').trimEnd('.') + " in."
    else -> String.format(Locale.US, "%.1f mm", mm)
}

private fun fmtDia(unit: UnitSystem, mm: Float): String = when (unit.name.uppercase(Locale.US)) {
    "MILLIMETERS", "MM" -> String.format(Locale.US, "%.1f", mm)
    "INCHES", "IN" -> String.format(Locale.US, "%.3f", mm / MM_PER_IN).trimEnd('0').trimEnd('.')
    else -> String.format(Locale.US, "%.1f", mm)
}

private fun fmtDiaWithUnit(unit: UnitSystem, mm: Float): String = when (unit.name.uppercase(Locale.US)) {
    "MILLIMETERS", "MM" -> String.format(Locale.US, "%.1f mm", mm)
    "INCHES", "IN" -> String.format(Locale.US, "%.3f", mm / MM_PER_IN).trimEnd('0').trimEnd('.') + " in."
    else -> String.format(Locale.US, "%.1f mm", mm)
}

private fun tpiFromPitch(pitchMm: Float): Float = if (pitchMm > 0f) MM_PER_IN / pitchMm else 0f
private fun fmtTpi(tpi: Float): String {
    val i = tpi.toInt()
    return if (abs(tpi - i) < 0.01f) i.toString() else String.format(Locale.US, "%.2f", tpi)
}

// Aggregate helper
private fun ShaftSpec.maxOuterDiaMm(): Float {
    var maxDia = 0f
    bodies.forEach  { maxDia = maxOf(maxDia, it.diaMm) }
    tapers.forEach  { maxDia = maxOf(maxDia, max(it.startDiaMm, it.endDiaMm)) }
    threads.forEach { maxDia = maxOf(maxDia, it.majorDiaMm) }
    liners.forEach  { maxDia = maxOf(maxDia, it.odMm) }
    return maxDia
}
