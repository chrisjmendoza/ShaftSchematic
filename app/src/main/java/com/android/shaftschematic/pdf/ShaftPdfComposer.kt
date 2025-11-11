// File: app/src/main/java/com/android/shaftschematic/pdf/ShaftPdfComposer.kt
@file:Suppress("MemberVisibilityCanBePrivate", "unused")

package com.android.shaftschematic.pdf

import android.graphics.*
import android.graphics.pdf.PdfDocument
import com.android.shaftschematic.geom.computeOalWindow
import com.android.shaftschematic.geom.computeSetPositionsInMeasureSpace
import com.android.shaftschematic.model.*
import com.android.shaftschematic.pdf.dim.*
import com.android.shaftschematic.pdf.notes.*
import com.android.shaftschematic.pdf.render.PdfDimensionRenderer
import com.android.shaftschematic.settings.PdfPrefs
import com.android.shaftschematic.util.UnitSystem
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import androidx.core.graphics.withClip


/**
 * PDF Composer — draws the shaft preview and export annotations.
 *
 * Units: millimeters (mm) in model space. Paper units are points (1/72").
 * Axis: AFT → FWD. Measurement origin (x=0) is the first counted AFT surface,
 * respecting end threads flagged `excludeFromOAL` (those shift the measurement window).
 *
 * This composer:
 *  • Renders bodies (with centered long-break compression), tapers, threads, and liners.
 *  • Draws **liner-only dimensions** in the PDF: for each liner we show
 *      – offset from SET → near edge, and
 *      – liner length,
 *    with stacked rails; the **top rail is OAL only**.
 *  • Keeps all geometry unchanged; only the dimension pass was added.
 */
fun composeShaftPdf(
    page: PdfDocument.Page,
    spec: ShaftSpec,
    unit: UnitSystem,
    project: ProjectInfo,
    appVersion: String,
    filename: String,
    pdfPrefs: PdfPrefs = PdfPrefs(),
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

    run {
        val baseY = yTopOfShaft - BAND_CLEAR_PT - BASE_DIM_OFFSET_PT

        // Standard rail gap you're already using for liner rails
        val railGap = LANE_GAP_PT + 6f

        // Compute a higher top rail Y for OAL by adding extra multiples of the normal gap
        // Example: 2.5x the normal gap above where OAL would have been.
        val factor = pdfPrefs.oalSpacingFactor.coerceIn(1.0f, 6.0f)
        val topY = baseY - OVERALL_EXTRA_PT - railGap * (factor - 1f)

        val dimText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            textSize = TEXT_PT - 2f
            color = 0xFF000000.toInt()
        }

        // Shared world→page mapper (same geometry mapping)
        val pageX: (Double) -> Float = { mm -> (geomRect.left + (mm.toFloat() * ptPerMm)) }

        val linerDims = mapToLinerDimsForPdf(spec)
        val win  = computeOalWindow(spec)
        val sets = computeSetPositionsInMeasureSpace(win)
        val spans = buildLinerSpans(linerDims, sets, unit)
        val planner = RailPlanner()
        val assignments = spans.map { planner.assign(it) }

        val renderer = PdfDimensionRenderer(
            pageX = pageX,
            baseY = baseY,
            railDy = railGap,
            topRailY = topY,            // ← OAL sits higher thanks to the factor above
            linePaint = dim,
            textPaint = dimText,
            objectTopY = yTopOfShaft,
            objectClearance = 6f
        )

        renderer.drawTop(c, oalSpan(win.oalMm, unit), true)
        assignments.forEach { rs ->
            renderer.drawOnRail(c, rs.rail, rs.span, true)
        }
    }

    // --- diameter callouts (optional; leave empty until you have stations) ---
    run {
        val calls: List<DiaCallout> = emptyList()
        if (calls.isNotEmpty()) {
            val leaderText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                textSize = TEXT_PT - 2f
                color = 0xFF000000.toInt()
            }
            val leader = DiameterLeaderRenderer(
                pageX = { mm -> (geomRect.left + (mm.toFloat() * ptPerMm)) },
                shaftTopY = yTopOfShaft,
                shaftBottomY = cy + (maxDiaMm * 0.5f) * ptPerMm,
                linePaint = dim,
                textPaint = leaderText
            )
            leader.draw(c, calls, unit)
        }
    }

    // footer
    val showCompressionNote = hasCenterBreak(spec)
    val footerCfg = FooterConfig(
        showAftThread = hasAftThread(spec),
        showFwdThread = hasFwdThread(spec),
        showAftTaper  = hasAftTaper(spec),
        showFwdTaper  = hasFwdTaper(spec),
        showCompressionNote = showCompressionNote
    )

    val infoTop = cy + halfHeightPx + INFO_GAP_PT
    val infoBottom = kotlin.math.min(infoTop + FOOTER_BLOCK_PT, pageH - PAGE_MARGIN_PT)
    val infoRect = RectF(geomRect.left, infoTop, geomRect.right, infoBottom)

    drawFooter(c, infoRect, spec, unit, project, filename, appVersion, text, footerCfg)

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
            drawSCurveBreak(c, top, bot, mid, gap, capPaint)
        }
    }
}

/**
 * Draws liner-only dimensions using the current renderer contract.
 * OAL is on the top rail. Spans align with geometry via the provided pageX mapper.
 */
private fun drawLinerDimensionsPdf(
    canvas: Canvas,
    spec: ShaftSpec,
    liners: List<LinerDim>,
    unit: UnitSystem,
    pageX: (Double) -> Float,
    topY: Float,
    baseY: Float,
    railDy: Float,
    linePaint: Paint,
    textPaint: Paint,
    objectTopY: Float
) {
    val win = computeOalWindow(spec)
    val sets = computeSetPositionsInMeasureSpace(win)

    val spans = buildLinerSpans(liners, sets, unit)
    val planner = RailPlanner()
    val assignments = spans.map { planner.assign(it) }

    val renderer = PdfDimensionRenderer(
        pageX = pageX,
        baseY = baseY,
        railDy = railDy,
        topRailY = topY,
        linePaint = linePaint,
        textPaint = textPaint,
        objectTopY = objectTopY,
        objectClearance = 6f
    )

    renderer.drawTop(canvas, oalSpan(win.oalMm, unit), true)
    assignments.forEach { rs -> renderer.drawOnRail(canvas, rs.rail, rs.span, true) }
}


/**
 * Adapter from model liners to export-only LinerDim.
 * Anchor is inferred by proximity to SETs; swap to explicit anchors if your model stores them.
 */
private fun mapToLinerDimsForPdf(spec: ShaftSpec): List<LinerDim> {
    val win  = computeOalWindow(spec)
    val sets = computeSetPositionsInMeasureSpace(win)

    return spec.liners.map { ln ->
        // Edges in measurement space (AFT→FWD)
        val aftEdge = win.toMeasureX(ln.startFromAftMm.toDouble())
        val fwdEdge = aftEdge + ln.lengthMm.toDouble()
        val length  = (fwdEdge - aftEdge).coerceAtLeast(0.0)

        // Compare SET→nearest edge distances using the correct edge per SET
        val distAft = (aftEdge - sets.aftSETxMm).coerceAtLeast(0.0)      // AFT SET → AFT edge
        val distFwd = (sets.fwdSETxMm - fwdEdge).coerceAtLeast(0.0)      // FWD SET → FWD edge

        val useFwd = distFwd < distAft

        if (useFwd) {
            // FWD-anchored: offset is FWD SET → FWD edge (toward AFT); length runs AFT
            LinerDim(
                id = ln.id,
                anchor = LinerAnchor.FWD_SET,
                offsetFromSetMm = distFwd,
                lengthMm = length
            )
        } else {
            // AFT-anchored: offset is AFT SET → AFT edge (forward); length runs FWD
            LinerDim(
                id = ln.id,
                anchor = LinerAnchor.AFT_SET,
                offsetFromSetMm = distAft,
                lengthMm = length
            )
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

// And add this function (near the old one):
private fun drawSCurveBreak(
    c: Canvas,
    yTop: Float,
    yBot: Float,
    xMid: Float,
    gap: Float,
    p: Paint
) {
    // Draw two mirrored cubic Béziers to suggest a smooth long-break
    val half = gap * 0.5f
    val xL = xMid - half
    val xR = xMid + half
    val yC = (yTop + yBot) * 0.5f
    val amp = (yBot - yTop) * 0.22f  // curvature amplitude

    val path = android.graphics.Path().apply {
        // upper S
        moveTo(xL, yC - amp)
        cubicTo(
            xL + half * 0.35f, yC - amp * 1.35f,
            xR - half * 0.35f, yC + amp * 1.35f,
            xR, yC + amp
        )
        // lower S (mirror back for visual weight)
        moveTo(xL, yC + amp)
        cubicTo(
            xL + half * 0.35f, yC + amp * 1.35f,
            xR - half * 0.35f, yC - amp * 1.35f,
            xR, yC - amp
        )
    }
    c.drawPath(path, p)
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
        c.withClip(rect) {
            var hx = rect.left + 4f
            while (hx <= rect.right + 4f) {
                c.drawLine(hx - 4f, rect.bottom, hx + 4f, rect.top, dim)
                hx += step
            }
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
    cfg: FooterConfig
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
    val ends = detectEndFeatures(spec)

    // AFT (left)
    run {
        var y = top
        if (cfg.showAftTaper && ends.aftTaper) {
            getAftEndTaper(spec)?.let { tp ->
                val (let, set) = letSet(tp)
                c.drawText("AFT Taper", leftX, y, text); y += lh
                c.drawText("L.E.T.: ${fmtDia(unit, let)}", leftX, y, text); y += lh
                c.drawText("S.E.T.: ${fmtDia(unit, set)}", leftX, y, text); y += lh
                c.drawText("Length: ${fmtLen(unit, tp.lengthMm)}", leftX, y, text); y += lh
                c.drawText("Rate: ${rate1toN(tp)}", leftX, y, text); y += lh
            }
        }
        if (cfg.showAftThread && ends.aftThread) {
            getAftEndThread(spec)?.let { th ->
                c.drawText(
                    "Thread: ${fmtDiaWithUnit(unit, th.majorDiaMm)} × ${fmtTpi(tpiFromPitch(th.pitchMm))} TPI × ${fmtLen(unit, th.lengthMm)}",
                    leftX, y, text
                )
                y += lh
            }
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
        // c.drawText("Not to scale: long bodies compressed for readability.", midX, y, text)
    }

    // FWD (right)
    run {
        var y = top
        if (cfg.showFwdTaper && ends.fwdTaper) {
            getFwdEndTaper(spec)?.let { tp ->
                val (let, set) = letSet(tp)
                c.drawText("FWD Taper", rightX, y, text); y += lh
                c.drawText("L.E.T.: ${fmtDia(unit, let)}", rightX, y, text); y += lh
                c.drawText("S.E.T.: ${fmtDia(unit, set)}", rightX, y, text); y += lh
                c.drawText("Length: ${fmtLen(unit, tp.lengthMm)}", rightX, y, text); y += lh
                c.drawText("Rate: ${rate1toN(tp)}", rightX, y, text); y += lh
            }
        }
        if (cfg.showFwdThread && ends.fwdThread) {
            getFwdEndThread(spec)?.let { th ->
                c.drawText(
                    "Thread: ${fmtDiaWithUnit(unit, th.majorDiaMm)} × ${fmtTpi(tpiFromPitch(th.pitchMm))} TPI × ${fmtLen(unit, th.lengthMm)}",
                    rightX, y, text
                )
                y += lh
            }
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

// Threads helper – keep your house style: DIA × TPI × LEN
private fun fmtThread(th: Threads, unit: UnitSystem): String {
    val dia = fmtDiaWithUnit(unit, th.majorDiaMm)
    val tpi = fmtTpi(tpiFromPitch(th.pitchMm))
    val len = fmtLen(unit, th.lengthMm)
    return "$dia × $tpi × $len"
}

// Taper helper – compute 1:N from (length / Δdia), then show "1:N over LEN"
private fun fmtTaper(tp: Taper, unit: UnitSystem): String {
    val rate = rate1toN(tp) // you already have this
    return "$rate over ${fmtLen(unit, tp.lengthMm)}"
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

/** Returns true if any body was center-broken for readability. */
private fun hasCenterBreak(spec: ShaftSpec): Boolean {
    // Heuristic: mark true when any body length exceeds the visual compression threshold.
    // Replace with your actual flag if drawBodiesCompressedCenterBreak exposes it.
    val totalBodies = spec.bodies.size
    if (totalBodies == 0) return false
    // Simple conservative rule: if overall length >> drawing width, assume a break occurred.
    return spec.overallLengthMm > 3_000f && totalBodies >= 2
}

/** Presence checks for end features. */
private fun hasAftThread(spec: ShaftSpec): Boolean =
    spec.threads.any { it.startFromAftMm <= 0.5f }    // thread touches AFT end

private fun hasFwdThread(spec: ShaftSpec): Boolean {
    val oal = spec.overallLengthMm
    return spec.threads.any { (it.startFromAftMm + it.lengthMm) >= (oal - 0.5f) } // thread touches FWD end
}

private const val END_EPS_MM = 0.5f

private fun hasAftTaper(spec: ShaftSpec): Boolean =
    spec.tapers.any { tp ->
        // touches AFT end if its start is near 0 OR it spans across 0
        val start = tp.startFromAftMm
        val end   = tp.startFromAftMm + tp.lengthMm
        start <= END_EPS_MM || (start < 0f && end > 0f)
    }

private fun hasFwdTaper(spec: ShaftSpec): Boolean {
    val oal = spec.overallLengthMm
    return spec.tapers.any { tp ->
        val start = tp.startFromAftMm
        val end   = tp.startFromAftMm + tp.lengthMm
        // touches FWD end if its end is near OAL OR it spans across OAL
        abs(end - oal) <= END_EPS_MM || (start < oal && end > oal)
    }
}

// --- End-feature presence detection -----------------------------------------

private data class EndFlags(
    val aftThread: Boolean,
    val fwdThread: Boolean,
    val aftTaper:  Boolean,
    val fwdTaper:  Boolean
)

/**
 * Determines which geometric features (taper, thread, etc.) physically reach
 * each end face of the shaft. A feature "exists at the end" only if its
 * interval in millimeters actually touches the end face within a small epsilon.
 *
 * We deliberately do NOT infer from component types or labels; only from
 * real geometric extents in shaft coordinates.
 *
 * Conventions:
 * - Shaft X-axis increases AFT ➜ FWD.
 * - AFT end face is fixed at X = 0.0 mm.
 * - FWD end face is at X = spec.overallLengthMm.
 * - Threads/tapers expose [startFromAftMm, startFromAftMm + lengthMm].
 *   • AFT-end features begin exactly at 0.0 mm and extend forward.
 *   • FWD-end features terminate exactly at overallLengthMm and approach from aft.
 * - A feature is considered “present” at an end when its start or end point
 *   lies within ±epsMm of that end face and its length exceeds epsMm.
 *
 * The returned EndFlags report presence independently for tapers and threads
 * at both ends, allowing combinations (e.g. a taper and a thread at the same end)
 * to be rendered in proper stacked order in the footer.
 */

private fun detectEndFeatures(spec: ShaftSpec, epsMm: Double = 0.01): EndFlags {
    val aftX = 0.0
    val fwdX = spec.overallLengthMm.toDouble()

    fun near(a: Double, b: Double) = kotlin.math.abs(a - b) <= epsMm

    // Threads
    val aftThread = spec.threads.any { th ->
        near(th.startFromAftMm.toDouble(), aftX) && th.lengthMm > epsMm
    }
    val fwdThread = spec.threads.any { th ->
        near((th.startFromAftMm + th.lengthMm).toDouble(), fwdX) && th.lengthMm > epsMm
    }

    // Tapers
    val aftTaper = spec.tapers.any { tp ->
        near(tp.startFromAftMm.toDouble(), aftX) && tp.lengthMm > epsMm
    }
    val fwdTaper = spec.tapers.any { tp ->
        near((tp.startFromAftMm + tp.lengthMm).toDouble(), fwdX) && tp.lengthMm > epsMm
    }

    return EndFlags(aftThread, fwdThread, aftTaper, fwdTaper)
}

private const val EPS_MM = 0.01

private fun near(a: Double, b: Double, eps: Double = EPS_MM) =
    kotlin.math.abs(a - b) <= eps

private fun getAftEndThread(spec: ShaftSpec): Threads? =
    spec.threads.firstOrNull { th ->
        near(th.startFromAftMm.toDouble(), 0.0) && th.lengthMm > EPS_MM
    }

private fun getFwdEndThread(spec: ShaftSpec): Threads? {
    val fwdX = spec.overallLengthMm.toDouble()
    return spec.threads.firstOrNull { th ->
        near((th.startFromAftMm + th.lengthMm).toDouble(), fwdX) && th.lengthMm > EPS_MM
    }
}

private fun getAftEndTaper(spec: ShaftSpec): Taper? =
    spec.tapers.firstOrNull { tp ->
        near(tp.startFromAftMm.toDouble(), 0.0) && tp.lengthMm > EPS_MM
    }

private fun getFwdEndTaper(spec: ShaftSpec): Taper? {
    val fwdX = spec.overallLengthMm.toDouble()
    return spec.tapers.firstOrNull { tp ->
        near((tp.startFromAftMm + tp.lengthMm).toDouble(), fwdX) && tp.lengthMm > EPS_MM
    }
}


data class FooterConfig(
    val showAftThread: Boolean,
    val showFwdThread: Boolean,
    val showAftTaper: Boolean,
    val showFwdTaper: Boolean,
    val showCompressionNote: Boolean
)
