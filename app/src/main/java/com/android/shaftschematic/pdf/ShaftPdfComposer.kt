// File: com/android/shaftschematic/pdf/ShaftPdfComposer.kt
// COMPLETE REWRITE v5 — minimal, compiling version (no AxisMapping)
// - Pure android.graphics.Canvas (no Compose bridge)
// - Fit‑to‑width single scale (ptPerMm)
// - Centerline OFF in PDF
// - Liners: top/bottom = outline width; ends = dim width (ticks)
// - Dimension band above drawing: top Overall line + per‑component length lines
//   with centered labels that include units (e.g., "100.0 mm", "12.5 in.") and a handwriting gap
// - Footer: AFT / Work Order / FWD (no boxes)
// - Uses your model names: Body.diaMm, ThreadSpec.majorDiaMm, Liner.odMm
// - Distance‑from‑reference lines + body‑only compression can be added later

package com.android.shaftschematic.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.ThreadSpec
import com.android.shaftschematic.util.UnitSystem
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private const val MM_PER_IN = 25.4f
private const val OUTLINE_PT = 2.5f
private const val DIM_PT = 1.6f
private const val TEXT_PT = 12f

private const val PAGE_MARGIN_PT = 36f      // 0.5 in
private const val FOOTER_H_PT = 84f         // ~1.17 in
private const val DIM_BAND_H_PT = 90f       // space above drawing for dimension lines
private const val LANE_GAP_PT = 12f
private const val ARROW_PT = 6f
private const val LABEL_PAD_PT = 6f
private const val EPS_END_MM = 0.25f


/**
 * Backwards‑compatibility shim so existing imports `ShaftPdfComposer` keep working.
 * Delegates to the v5 top‑level function `composeShaftPdf(...)`.
 */
/**
 * Backwards‑compatibility shim so existing imports `ShaftPdfComposer` keep working.
 * Provides the legacy `exportToStream(...)` API and delegates to the v5
 * top‑level composer (`composeShaftPdf`). Grid is ignored per PDF rules.
 */
object ShaftPdfComposer {
    /** Legacy entry point still used by ShaftRoute. */
    @JvmStatic
    fun exportToStream(
        context: Context,
        spec: ShaftSpec,
        unit: UnitSystem,
        showGrid: Boolean, // ignored; PDF grid is off by contract
        out: OutputStream,
        title: String? = null,
    ) {
// Determine app version (robust to older SDKs)
        val pm = context.packageManager
        val pkg = context.packageName
        val appVersion = try {
            val pi = pm.getPackageInfo(pkg, 0)
            (pi?.versionName ?: "").ifBlank { "" }
        } catch (t: Throwable) { "" }


        val filename = (title ?: "shaft_drawing").ifBlank { "shaft_drawing" }


        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(612, 792, 1) // Letter 8.5x11in @72dpi
            .create()
        val page = doc.startPage(pageInfo)


// Compose content on the page
        composeShaftPdf(
            page = page,
            spec = spec,
            unit = unit,
            project = ProjectInfo(), // no project info available here
            appVersion = appVersion,
            filename = filename,
        )


        doc.finishPage(page)
        doc.writeTo(out)
        doc.close()
    }


    // Optional convenience alias kept from earlier messages
    @JvmStatic
    fun compose(
        page: PdfDocument.Page,
        spec: ShaftSpec,
        unit: UnitSystem,
        project: ProjectInfo,
        appVersion: String,
        filename: String,
    ) {
        composeShaftPdf(page, spec, unit, project, appVersion, filename)
    }


    // Alternate overload in case older call sites passed filename before project
    @JvmStatic
    fun compose(
        page: PdfDocument.Page,
        spec: ShaftSpec,
        unit: UnitSystem,
        filename: String,
        project: ProjectInfo,
        appVersion: String,
    ) {
        composeShaftPdf(page, spec, unit, project, appVersion, filename)
    }
}

// Mid column carrier
data class ProjectInfo(
    val customer: String = "",
    val vessel: String = "",
    val jobNumber: String = "",
)

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

    // Layout
    val geomRect = RectF(
        PAGE_MARGIN_PT,
        PAGE_MARGIN_PT + DIM_BAND_H_PT,
        pageW - PAGE_MARGIN_PT,
        pageH - PAGE_MARGIN_PT - FOOTER_H_PT
    )
    val dimRect = RectF(PAGE_MARGIN_PT, PAGE_MARGIN_PT, pageW - PAGE_MARGIN_PT, PAGE_MARGIN_PT + DIM_BAND_H_PT)
    val footerRect = RectF(PAGE_MARGIN_PT, pageH - PAGE_MARGIN_PT - FOOTER_H_PT, pageW - PAGE_MARGIN_PT, pageH - PAGE_MARGIN_PT)

    // Paints
    val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = OUTLINE_PT; color = 0xFF000000.toInt() }
    val dim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = DIM_PT; color = 0xFF000000.toInt() }
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; textSize = TEXT_PT; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL); color = 0xFF000000.toInt() }

    // Scale (fit to width)
    val overall = max(1f, spec.overallLengthMm)
    val ptPerMm = geomRect.width() / overall
    val left = geomRect.left
    val cy = (geomRect.top + geomRect.bottom) * 0.5f

    fun xAt(mm: Float) = left + mm * ptPerMm
    fun rPx(diaMm: Float) = (diaMm * 0.5f) * ptPerMm

    // 1) Components (no centerline in PDF)
    drawBodies(c, spec.bodies, cy, ::xAt, ::rPx, outline)
    drawTapers(c, spec.tapers, cy, ::xAt, ::rPx, outline)
    drawThreads(c, spec.threads, cy, ::xAt, ::rPx, outline, dim, ptPerMm)
    drawLiners(c, spec.liners, cy, ::xAt, ::rPx, outline, dim)

    // 2) Dimension band
    drawDimensionBand(c, dimRect, spec, unit, ::xAt, text, dim)

    // 3) Footer
    drawFooter(c, footerRect, spec, unit, project, filename, appVersion, text)
}

/* ------------------------------ Components ------------------------------ */
private fun drawBodies(c: Canvas, bodies: List<Body>, cy: Float, xAt: (Float) -> Float, rPx: (Float) -> Float, outline: Paint) {
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

private fun drawTapers(c: Canvas, tapers: List<Taper>, cy: Float, xAt: (Float) -> Float, rPx: (Float) -> Float, outline: Paint) {
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

private fun drawThreads(c: Canvas, threads: List<ThreadSpec>, cy: Float, xAt: (Float) -> Float, rPx: (Float) -> Float, outline: Paint, dim: Paint, ptPerMm: Float) {
    threads.forEach { th ->
        if (th.lengthMm <= 0f || th.majorDiaMm <= 0f) return@forEach
        val x0 = xAt(th.startFromAftMm); val x1 = xAt(th.startFromAftMm + th.lengthMm)
        val r = rPx(th.majorDiaMm); val top = cy - r; val bot = cy + r
        c.drawLine(x0, top, x1, top, outline)
        c.drawLine(x0, bot, x1, bot, outline)
        c.drawLine(x0, top, x0, bot, outline)
        c.drawLine(x1, top, x1, bot, outline)
        // Hatch at 45° using pitch
        val step = th.pitchMm * ptPerMm
        if (step > 0f) {
            var x = x0
            val path = Path()
            while (x < x1 + step) {
                path.rewind(); path.moveTo(x, bot); path.lineTo(x + (bot - top), top)
                c.drawPath(path, dim); x += step
            }
        }
    }
}

private fun drawLiners(c: Canvas, liners: List<Liner>, cy: Float, xAt: (Float) -> Float, rPx: (Float) -> Float, outline: Paint, dim: Paint) {
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

/* --------------------------- Dimension band --------------------------- */
private data class Interval(val startMm: Float, val endMm: Float)

private fun drawDimensionBand(c: Canvas, band: RectF, spec: ShaftSpec, unit: UnitSystem, xAt: (Float) -> Float, text: Paint, dim: Paint) {
    // Top: Overall
    val overallY = band.top + text.textSize + 6f
    val xa = xAt(0f); val xf = xAt(spec.overallLengthMm)
    drawDimWithGap(c, xa, xf, overallY, fmtLen(unit, spec.overallLengthMm), text, dim)

    // Per‑component lengths
    val ivs = ArrayList<Interval>()
    spec.bodies.forEach { if (it.lengthMm > 0f && it.diaMm > 0f) ivs += Interval(it.startFromAftMm, it.startFromAftMm + it.lengthMm) }
    spec.tapers.forEach { if (it.lengthMm > 0f && (it.startDiaMm > 0f || it.endDiaMm > 0f)) ivs += Interval(it.startFromAftMm, it.startFromAftMm + it.lengthMm) }
    spec.threads.forEach { if (it.lengthMm > 0f && it.majorDiaMm > 0f) ivs += Interval(it.startFromAftMm, it.startFromAftMm + it.lengthMm) }
    spec.liners.forEach { if (it.lengthMm > 0f && it.odMm > 0f) ivs += Interval(it.startFromAftMm, it.startFromAftMm + it.lengthMm) }

    // Greedy lane stack under overall line
    data class Lane(var endX: Float, val y: Float)
    val lanes = mutableListOf<Lane>()
    val firstLaneY = overallY + text.textSize * 1.6f
    val sorted = ivs.sortedWith(compareBy({ it.startMm }, { it.endMm }))

    sorted.forEach { iv ->
        val x0 = xAt(iv.startMm); val x1 = xAt(iv.endMm)
        var lane = -1
        for (i in lanes.indices) if (x0 >= lanes[i].endX) { lane = i; break }
        if (lane == -1) { lanes += Lane(x1, firstLaneY + lanes.size * (TEXT_PT + LANE_GAP_PT)); lane = lanes.lastIndex } else { lanes[lane].endX = x1 }
        val y = lanes[lane].y
        drawDimWithGap(c, x0, x1, y, fmtLen(unit, iv.endMm - iv.startMm), text, dim)
    }
}

private fun drawDimWithGap(c: Canvas, x0: Float, x1: Float, y: Float, label: String, text: Paint, dim: Paint) {
    val mid = (x0 + x1) * 0.5f
    val w = text.measureText(label)
    val gap = w + LABEL_PAD_PT * 2
    val leftEnd = mid - gap / 2f
    val rightStart = mid + gap / 2f

    // Baseline split
    c.drawLine(x0, y, leftEnd, y, dim)
    c.drawLine(rightStart, y, x1, y, dim)

    // Arrowheads
    c.drawLine(x0, y, x0 + ARROW_PT, y - ARROW_PT, dim)
    c.drawLine(x0, y, x0 + ARROW_PT, y + ARROW_PT, dim)
    c.drawLine(x1, y, x1 - ARROW_PT, y - ARROW_PT, dim)
    c.drawLine(x1, y, x1 - ARROW_PT, y + ARROW_PT, dim)

    // Label
    val tx = mid - w / 2f
    val ty = y - 6f
    c.drawText(label, tx, ty, text)
}

/* -------------------------------- Footer -------------------------------- */
private fun drawFooter(c: Canvas, rect: RectF, spec: ShaftSpec, unit: UnitSystem, project: ProjectInfo, filename: String, appVersion: String, text: Paint) {
    val gutter = 16f
    val innerW = rect.width() - gutter * 2
    val colW = innerW / 3f

    val leftX = rect.left
    val midX = leftX + colW + gutter
    val rightX = midX + colW + gutter

    val top = rect.top + 18f
    val lh = text.textSize * 1.35f

    // Left (AFT)
    run {
        var y = top
        val aftTaper = findAftTaper(spec)
        val aftThread = findAftThread(spec)
        if (aftTaper != null) {
            val (let, set) = letSet(aftTaper)
            c.drawText("AFT Taper", leftX, y, text); y += lh
            c.drawText("L.E.T.: ${fmtDia(unit, let)}", leftX, y, text); y += lh
            c.drawText("S.E.T.: ${fmtDia(unit, set)}", leftX, y, text); y += lh
            c.drawText("Length: ${fmtLen(unit, aftTaper.lengthMm)}", leftX, y, text); y += lh
            c.drawText("Rate: ${rate1toN(aftTaper)}", leftX, y, text); y += lh
        }
        if (aftThread != null) {
            val tpi = tpiFromPitch(aftThread.pitchMm)
            val dia = fmtDia(unit, aftThread.majorDiaMm)
            val len = fmtLen(unit, aftThread.lengthMm)
            c.drawText("Thread: ${dia} × ${fmtTpi(tpi)} × ${len}", leftX, y, text)
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
        val fwdTaper = findFwdTaper(spec)
        val fwdThread = findFwdThread(spec)
        if (fwdTaper != null) {
            val (let, set) = letSet(fwdTaper)
            c.drawText("FWD Taper", rightX, y, text); y += lh
            c.drawText("L.E.T.: ${fmtDia(unit, let)}", rightX, y, text); y += lh
            c.drawText("S.E.T.: ${fmtDia(unit, set)}", rightX, y, text); y += lh
            c.drawText("Length: ${fmtLen(unit, fwdTaper.lengthMm)}", rightX, y, text); y += lh
            c.drawText("Rate: ${rate1toN(fwdTaper)}", rightX, y, text); y += lh
        }
        if (fwdThread != null) {
            val tpi = tpiFromPitch(fwdThread.pitchMm)
            val dia = fmtDia(unit, fwdThread.majorDiaMm)
            val len = fmtLen(unit, fwdThread.lengthMm)
            c.drawText("Thread: ${dia} × ${fmtTpi(tpi)} × ${len}", rightX, y, text)
        }
    }

    // Metadata (tiny) at bottom-right
    val meta = "$filename  •  ShaftSchematic $appVersion"
    val metaW = text.measureText(meta)
    c.drawText(meta, rect.right - metaW, rect.bottom - 6f, text)
}

/* ------------------------------ Helpers ------------------------------ */
private fun findAftTaper(spec: ShaftSpec): Taper? = spec.tapers.minByOrNull { it.startFromAftMm }?.takeIf { it.startFromAftMm < EPS_END_MM }
private fun findFwdTaper(spec: ShaftSpec): Taper? = spec.tapers.maxByOrNull { it.startFromAftMm + it.lengthMm }?.takeIf { abs((it.startFromAftMm + it.lengthMm) - spec.overallLengthMm) < EPS_END_MM }
private fun findAftThread(spec: ShaftSpec): ThreadSpec? = spec.threads.minByOrNull { it.startFromAftMm }?.takeIf { it.startFromAftMm < EPS_END_MM }
private fun findFwdThread(spec: ShaftSpec): ThreadSpec? = spec.threads.maxByOrNull { it.startFromAftMm + it.lengthMm }?.takeIf { abs((it.startFromAftMm + it.lengthMm) - spec.overallLengthMm) < EPS_END_MM }

private fun letSet(t: Taper): Pair<Float, Float> { val let = max(t.startDiaMm, t.endDiaMm); val set = min(t.startDiaMm, t.endDiaMm); return let to set }
private fun rate1toN(t: Taper): String { val d = abs(t.startDiaMm - t.endDiaMm); if (d <= 1e-4f || t.lengthMm <= 0f) return "—"; val n = t.lengthMm / d; return "1:" + String.format(Locale.US, "%.0f", n) }

private fun fmtLen(unit: UnitSystem, mm: Float): String = when (unit) {
    // Support both possible enum spellings; default to mm
    else -> String.format(Locale.US, "%.1f mm", mm)
}.let {
    when (unit.name.uppercase(Locale.US)) {
        "MILLIMETERS", "MM" -> String.format(Locale.US, "%.1f mm", mm)
        "INCHES", "IN" -> {
            val inches = mm / MM_PER_IN
            val s = String.format(Locale.US, "%.3f", inches).trimEnd('0').trimEnd('.')
            "$s in."
        }
        else -> String.format(Locale.US, "%.1f mm", mm)
    }
}

private fun fmtDia(unit: UnitSystem, mm: Float): String = when (unit.name.uppercase(Locale.US)) {
    "MILLIMETERS", "MM" -> String.format(Locale.US, "%.1f", mm)
    "INCHES", "IN" -> String.format(Locale.US, "%.3f", mm / MM_PER_IN).trimEnd('0').trimEnd('.')
    else -> String.format(Locale.US, "%.1f", mm)
}

private fun tpiFromPitch(pitchMm: Float): Float = if (pitchMm > 0f) MM_PER_IN / pitchMm else 0f
private fun fmtTpi(tpi: Float): String { val i = tpi.toInt(); return if (abs(tpi - i) < 0.01f) i.toString() else String.format(Locale.US, "%.2f", tpi) }
