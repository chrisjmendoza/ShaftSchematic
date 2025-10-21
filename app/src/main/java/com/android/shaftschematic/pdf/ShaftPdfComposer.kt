// File: app/src/main/java/com/android/shaftschematic/pdf/ShaftPdfComposer.kt
@file:Suppress("MemberVisibilityCanBePrivate", "unused")

package com.android.shaftschematic.pdf

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.android.shaftschematic.domain.geom.computeOalWindow
import com.android.shaftschematic.domain.geom.computeSetPositionsInMeasureSpace
import com.android.shaftschematic.domain.model.LinerDim
import com.android.shaftschematic.model.*
import com.android.shaftschematic.pdf.dim.RailPlanner
import com.android.shaftschematic.pdf.dim.buildLinerSpans
import com.android.shaftschematic.pdf.dim.oalSpan
import com.android.shaftschematic.pdf.render.PdfDimensionRenderer
import com.android.shaftschematic.util.UnitSystem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/*  … same header contract as before … */

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

    val geomRect = RectF(
        PAGE_MARGIN_PT,
        PAGE_MARGIN_PT + TOP_TEXT_PAD_PT,
        pageW - PAGE_MARGIN_PT,
        pageH - PAGE_MARGIN_PT - FOOTER_BLOCK_PT
    )

    val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = OUTLINE_PT; color = 0xFF000000.toInt()
    }
    val dim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = DIM_PT; color = 0xFF000000.toInt()
    }
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; textSize = TEXT_PT
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        color = 0xFF000000.toInt()
    }

    val overallMm = max(1f, spec.overallLengthMm)
    val ptPerMm = geomRect.width() / overallMm
    val left = geomRect.left

    val maxDiaMm = spec.maxOuterDiaMm().coerceAtLeast(1f)
    val halfHeightPx = (maxDiaMm * 0.5f) * ptPerMm
    val cy = (geomRect.top + geomRect.bottom) * 0.5f - CY_UP_BIAS_PT
    val yTopOfShaft = cy - halfHeightPx

    fun xAt(mm: Float) = (left + mm * ptPerMm).coerceIn(geomRect.left, geomRect.right)
    fun rPx(d: Float)  = (d * 0.5f) * ptPerMm

    // geometry
    drawBodiesCompressedCenterBreak(c, spec.bodies, cy, ::xAt, ::rPx, outline, geomRect)
    drawTapers(c, spec.tapers, cy, ::xAt, ::rPx, outline)
    drawThreads(c, spec.threads, cy, ::xAt, ::rPx, outline, dim, ptPerMm)
    drawLiners(c, spec.liners, cy, ::xAt, ::rPx, outline, dim)

    // dims
    /**
     * Lightweight style carrier for dimension drawing.
     */
    data class DimStyle(
        val paint: Paint,
        val pxPerMm: Float,
        val topY: Float,
        val baseY: Float,
        val railDy: Float
    )

    /**
     * Draws PDF dimensions for liners only.
     * OAL is always on the top rail; other dims are rail-packed without overlap.
     */
    fun drawLinerDimensionsPdf(
        canvas: Canvas,
        spec: ShaftSpec,
        liners: List<LinerDim>,
        style: DimStyle
    ) {
        val win = computeOalWindow(spec)
        val sets = computeSetPositionsInMeasureSpace(win)

        val spans = buildLinerSpans(liners, sets)
        val planner = RailPlanner()
        val assignments = spans.map { planner.assign(it) }

        val renderer = PdfDimensionRenderer(
            pxPerMm = style.pxPerMm,
            baseY = style.baseY,
            railDy = style.railDy,
            topRailY = style.topY
        )

        renderer.drawTop(canvas, oalSpan(win.oalMm), style.paint)
        assignments.forEach { rs -> renderer.drawOnRail(canvas, rs.rail, rs.span, style.paint) }
    }

    // footer
    val infoTop = cy + halfHeightPx + INFO_GAP_PT
    val infoBottom = min(infoTop + FOOTER_BLOCK_PT, pageH - PAGE_MARGIN_PT)
    val infoRect = RectF(geomRect.left, infoTop, geomRect.right, infoBottom)
    drawFooter(c, infoRect, spec, unit, project, filename, appVersion, text)

    // stamp
    val meta = "$filename  •  ShaftSchematic $appVersion"
    val metaW = text.measureText(meta)
    c.drawText(meta, pageW - PAGE_MARGIN_PT - metaW, pageH - PAGE_MARGIN_PT - 4f, text)
}

// ──────────────────────────────────────────────────────────────────────────────
// Constants
// ──────────────────────────────────────────────────────────────────────────────

private const val MM_PER_IN = 25.4f

// Strokes / text
private const val OUTLINE_PT = 2.5f
private const val DIM_PT = 1.6f
private const val TEXT_PT = 12f

// Layout
private const val PAGE_MARGIN_PT = 36f       // 0.5 in
private const val TOP_TEXT_PAD_PT = 12f
private const val CY_UP_BIAS_PT = 40f        // bias geometry upward to make room for footer
private const val BAND_CLEAR_PT = 12f        // breathing room above shaft before first dim line
private const val BASE_DIM_OFFSET_PT = 24f   // distance from shaft top to first component dim
private const val OVERALL_EXTRA_PT = 16f     // overall lane sits above components
private const val LANE_GAP_PT = 24f          // spacing between dimension lanes
private const val ARROW_PT = 6f
private const val LABEL_PAD_PT = 6f
private const val EXT_OFFSET_PT = 9f         // gap from shaft to start of extension line
private const val EXT_OVERRUN_PT = 4f        // how much extension lines rise past dim line
private const val INFO_GAP_PT = 72f          // exactly 1 inch below geometry
private const val FOOTER_BLOCK_PT = 96f

// Compression (paper-space heuristic; bodies only)
private const val COMPRESS_TRIGGER_PT = 220f // if body length on paper ≥ this, show center-break
private const val ZIGZAG_GAP_MAX_PT = 40f    // max central gap width
private const val ZIGZAG_TEETH = 3           // 2–3 looks best; using 3 by default

// Label collision avoidance
private const val LABEL_STACK_STEP_PT = 10f  // vertical nudge per collision
private const val LABEL_LEADER_PT = 8f       // short leader when label is nudged

// ──────────────────────────────────────────────────────────────────────────────
// Geometry — bodies with centered long-break compression
// ──────────────────────────────────────────────────────────────────────────────

private fun drawBodiesCompressedCenterBreak(
    c: Canvas,
    bodies: List<Body>,
    cy: Float,
    xAt: (Float) -> Float,
    rPx: (Float) -> Float,
    outline: Paint,
    geomRect: RectF
) {
    val capPaint = Paint(outline).apply { style = Paint.Style.STROKE }
    bodies.forEach { b ->
        if (b.lengthMm <= 0f || b.diaMm <= 0f) return@forEach
        val x0 = xAt(b.startFromAftMm); val x1 = xAt(b.startFromAftMm + b.lengthMm)
        val r = rPx(b.diaMm); val top = cy - r; val bot = cy + r

        val bodyLenPt = abs(x1 - x0)
        val compress = bodyLenPt >= COMPRESS_TRIGGER_PT

        if (!compress) {
            // classic rectangle body
            c.drawLine(x0, top, x1, top, outline)
            c.drawLine(x0, bot, x1, bot, outline)
            c.drawLine(x0, top, x0, bot, outline)
            c.drawLine(x1, top, x1, bot, outline)
        } else {
            // centered break: two stubs + zig-zag gap
            val mid = (x0 + x1) * 0.5f
            val gap = min(ZIGZAG_GAP_MAX_PT, 0.25f * bodyLenPt)
            val half = 0.5f * gap
            val leftEnd = (mid - half).coerceIn(geomRect.left, geomRect.right)
            val rightBeg = (mid + half).coerceIn(geomRect.left, geomRect.right)

            // Left stub
            c.drawLine(x0, top, leftEnd, top, outline)
            c.drawLine(x0, bot, leftEnd, bot, outline)
            c.drawLine(x0, top, x0, bot, outline)
            c.drawLine(leftEnd, top, leftEnd, bot, capPaint)

            // Right stub
            c.drawLine(rightBeg, top, x1, top, outline)
            c.drawLine(rightBeg, bot, x1, bot, outline)
            c.drawLine(rightBeg, top, rightBeg, bot, capPaint)
            c.drawLine(x1, top, x1, bot, outline)

            // Zig-zag break, centered at mid within the gap
            drawZigZagBreak(c, top, bot, mid, gap, capPaint)
        }
    }
}

private fun drawZigZagBreak(
    c: Canvas,
    yTop: Float,
    yBot: Float,
    xMid: Float,
    gap: Float,
    p: Paint,
    teeth: Int = ZIGZAG_TEETH
) {
    val height = yBot - yTop
    val zTop = yTop + 0.25f * height
    val zBot = yBot - 0.25f * height
    val toothW = gap / max(1, teeth)
    var xL = xMid - 0.5f * gap
    repeat(teeth) {
        val xR = xL + toothW
        // “/” then “\”
        c.drawLine(xL, zBot, (xL + xR) * 0.5f, zTop, p)
        c.drawLine((xL + xR) * 0.5f, zTop, xR, zBot, p)
        xL = xR
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Geometry — components (continuous for tapers/threads/liners)
// ──────────────────────────────────────────────────────────────────────────────

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
    threads: List<Threads>,
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

        // Envelope
        c.drawLine(x0, top, x1, top, outline)
        c.drawLine(x0, bot, x1, bot, outline)
        c.drawLine(x0, top, x0, bot, outline)
        c.drawLine(x1, top, x1, bot, outline)

        // Hatch — stride = max(8 px, pitchMm * ptPerMm), clipped to band
        val step = max(8f, th.pitchMm * ptPerMm)
        val rect = RectF(min(x0, x1), min(top, bot), max(x0, x1), max(top, bot))
        val save = c.save()
        c.clipRect(rect)
        var hx = rect.left + 4f
        while (hx <= rect.right + 4f) {
            c.drawLine(hx - 4f, rect.bottom, hx + 4f, rect.top, dim)
            hx += step
        }
        c.restoreToCount(save)
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
        c.drawLine(x0, top, x0, bot, dim) // thin end ticks
        c.drawLine(x1, top, x1, bot, dim)
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Dimensions (Preview-style; avoid label collisions per lane)
// ──────────────────────────────────────────────────────────────────────────────

private data class Interval(val startMm: Float, val endMm: Float)

private fun drawDimensionsLikePreview(
    c: Canvas,
    spec: ShaftSpec,
    unit: UnitSystem,
    xAt: (Float) -> Float,
    yTopOfShaft: Float,
    text: Paint,
    dim: Paint,
) {
    val firstLaneY = yTopOfShaft - BAND_CLEAR_PT - BASE_DIM_OFFSET_PT

    // Gather component intervals
    data class Interval(val startMm: Float, val endMm: Float)
    val ivs = ArrayList<Interval>()
    spec.bodies.forEach  { if (it.lengthMm > 0f && it.diaMm       > 0f) ivs += Interval(it.startFromAftMm, it.startFromAftMm + it.lengthMm) }
    spec.tapers.forEach  { if (it.lengthMm > 0f && (it.startDiaMm > 0f || it.endDiaMm > 0f)) ivs += Interval(it.startFromAftMm, it.startFromAftMm + it.lengthMm) }
    spec.threads.forEach { if (it.lengthMm > 0f && it.majorDiaMm  > 0f) ivs += Interval(it.startFromAftMm, it.startFromAftMm + it.lengthMm) }
    spec.liners.forEach  { if (it.lengthMm > 0f && it.odMm        > 0f) ivs += Interval(it.startFromAftMm, it.startFromAftMm + it.lengthMm) }

    // Greedy lane packing; track smallest Y (topmost)
    data class Lane(var endX: Float, val y: Float, val labels: MutableList<RectF> = mutableListOf())
    val lanes = mutableListOf<Lane>()
    val sorted = ivs.sortedWith(compareBy({ it.startMm }, { it.endMm }))
    var topmostLaneY = firstLaneY

    sorted.forEach { iv ->
        val x0 = xAt(iv.startMm); val x1 = xAt(iv.endMm)
        var idx = -1
        for (i in lanes.indices) if (x0 >= lanes[i].endX) { idx = i; break }
        if (idx == -1) {
            lanes += Lane(x1, firstLaneY - lanes.size * (TEXT_PT + LANE_GAP_PT))
            idx = lanes.lastIndex
        } else {
            lanes[idx].endX = x1
        }
        val lane = lanes[idx]
        if (lane.y < topmostLaneY) topmostLaneY = lane.y

        drawDimWithExtensionsAvoidingOverlap(
            c, x0, x1, lane.y, yTopOfShaft,
            fmtLen(unit, iv.endMm - iv.startMm),
            text, dim, lane.labels
        )
    }

    // Overall one lane above the topmost component lane
    val gap = TEXT_PT + LANE_GAP_PT
    val overallY = if (lanes.isEmpty()) firstLaneY - OVERALL_EXTRA_PT else topmostLaneY - gap
    val xa = xAt(0f); val xf = xAt(spec.overallLengthMm)

    drawDimWithExtensionsAvoidingOverlap(
        c, xa, xf, overallY, yTopOfShaft,
        fmtLen(unit, spec.overallLengthMm),
        text, dim, mutableListOf()
    )
}

private fun drawDimWithExtensionsAvoidingOverlap(
    c: Canvas,
    x0: Float,
    x1: Float,
    yDim: Float,
    yTopOfShaft: Float,
    label: String,
    text: Paint,
    dim: Paint,
    occupiedLabels: MutableList<RectF>
) {
    val extStartY = yTopOfShaft - EXT_OFFSET_PT
    val extEndY = yDim + EXT_OVERRUN_PT
    c.drawLine(x0, extStartY, x0, extEndY, dim)
    c.drawLine(x1, extStartY, x1, extEndY, dim)

    val mid = (x0 + x1) * 0.5f
    val w = text.measureText(label)
    val gap = w + LABEL_PAD_PT * 2
    val leftEnd = mid - gap / 2f
    val rightStart = mid + gap / 2f

    // Leave a window for the label
    c.drawLine(x0, yDim, leftEnd, yDim, dim)
    c.drawLine(rightStart, yDim, x1, yDim, dim)

    drawArrowInward(c, x0, yDim, dim)
    drawArrowInward(c, x1, yDim, dim, left = false)

    // Ideal label rect centered on the line
    val textYCenter = yDim - (text.descent() + text.ascent()) / 2f
    val baseRect = RectF(mid - w / 2f, textYCenter + text.ascent(), mid + w / 2f, textYCenter + text.descent())

    // Stack upward until no collision with previous labels on this lane
    var bumpedRect = RectF(baseRect)
    var bumps = 0
    while (occupiedLabels.any { it.intersects(bumpedRect.left, bumpedRect.top, bumpedRect.right, bumpedRect.bottom) }) {
        bumps++
        val dy = LABEL_STACK_STEP_PT * bumps
        bumpedRect.set(baseRect.left, baseRect.top - dy, baseRect.right, baseRect.bottom - dy)
    }

    // If bumped, draw a tiny leader from gap center to the raised label
    val labelCx = bumpedRect.left
    val labelCy = (bumpedRect.top - text.ascent()) // rect→baseline
    if (bumps > 0) {
        val leaderY = bumpedRect.top - 2f
        c.drawLine(mid, yDim - 2f, mid, leaderY, dim)
        c.drawLine(mid - LABEL_LEADER_PT / 2f, leaderY, mid + LABEL_LEADER_PT / 2f, leaderY, dim)
    }

    c.drawText(label, labelCx, labelCy, text)
    occupiedLabels += RectF(
        labelCx,
        labelCy + text.ascent(),
        labelCx + w,
        labelCy + text.descent()
    )
}

private fun drawArrowInward(c: Canvas, x: Float, y: Float, p: Paint, left: Boolean = true) {
    val s = if (left) 1f else -1f
    c.drawLine(x, y, x + s * ARROW_PT, y - ARROW_PT, p)
    c.drawLine(x, y, x + s * ARROW_PT, y + ARROW_PT, p)
}

// ──────────────────────────────────────────────────────────────────────────────
// Footer (3 columns; center column is work-order info)
// ──────────────────────────────────────────────────────────────────────────────

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
        c.drawText("Vessel: ${project.vessel}",   midX, y, text); y += lh
        c.drawText("Job #: ${project.jobNumber}", midX, y, text); y += lh
        c.drawText("Date: $date", midX, y, text); y += lh
        c.drawText("Not to scale: long bodies compressed for readability.", midX, y, text)
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

// ──────────────────────────────────────────────────────────────────────────────
// Helpers
// ──────────────────────────────────────────────────────────────────────────────

private fun pickAftFwdTapers(spec: ShaftSpec): Pair<Taper?, Taper?> {
    if (spec.tapers.isEmpty()) return null to null
    var aft: Taper? = spec.tapers.minByOrNull { it.startFromAftMm }
    var fwd: Taper? = spec.tapers.maxByOrNull { it.startFromAftMm + it.lengthMm }
    if (aft == fwd) {
        val t = aft!!
        val distAft = t.startFromAftMm
        val distFwd = spec.overallLengthMm - (t.startFromAftMm + t.lengthMm)
        if (distAft <= distFwd) { fwd = null } else { aft = null }
    }
    return aft to fwd
}

private fun findAftThread(spec: ShaftSpec): Threads? = spec.threads.minByOrNull { it.startFromAftMm }
private fun findFwdThread(spec: ShaftSpec): Threads? = spec.threads.maxByOrNull { it.startFromAftMm + it.lengthMm }

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

private fun ShaftSpec.maxOuterDiaMm(): Float {
    var maxDia = 0f
    bodies.forEach  { maxDia = maxOf(maxDia, it.diaMm) }
    tapers.forEach  { maxDia = maxOf(maxDia, max(it.startDiaMm, it.endDiaMm)) }
    threads.forEach { maxDia = maxOf(maxDia, it.majorDiaMm) }
    liners.forEach  { maxDia = maxOf(maxDia, it.odMm) }
    return maxDia
}
