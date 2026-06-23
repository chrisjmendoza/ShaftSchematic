// File: app/src/main/java/com/android/shaftschematic/pdf/ShaftPdfComposer.kt
@file:Suppress("MemberVisibilityCanBePrivate")

package com.android.shaftschematic.pdf

import android.graphics.*
import android.graphics.pdf.PdfDocument
import com.android.shaftschematic.geom.END_EPS_MM
import com.android.shaftschematic.geom.computeOalWindow
import com.android.shaftschematic.geom.computeSetPositionsInMeasureSpace
import com.android.shaftschematic.model.*
import com.android.shaftschematic.pdf.dim.*
import com.android.shaftschematic.pdf.notes.*
import com.android.shaftschematic.pdf.render.PdfDimensionRenderer
import com.android.shaftschematic.settings.PdfPrefs
import com.android.shaftschematic.ui.resolved.ResolvedBody
import com.android.shaftschematic.ui.resolved.ResolvedComponent
import com.android.shaftschematic.settings.PdfTieringMode
import com.android.shaftschematic.util.UnitSystem
import com.android.shaftschematic.util.VerboseLog
import com.android.shaftschematic.util.buildBodyTitleById
import com.android.shaftschematic.util.buildLinerTitleById
import com.android.shaftschematic.util.buildTaperTitleById
import com.android.shaftschematic.util.buildThreadTitleById
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
 * Layout notes
 * - The page is explicitly painted white for viewer compatibility.
 * - A fixed footer band is reserved at the bottom of the page; all geometry is clamped
 *   to stay above it.
 * - Vertical placement uses a small paper-space bias (see `SHAFT_DOWN_PT`) but is still
 *   clamped to keep the schematic and footer readable.
 *
 * This composer:
 *  • Renders bodies (with centered long-break compression), tapers, threads, and liners.
 *  • Draws **liner-only dimensions** in the PDF: for each liner we show
 *      – offset from SET → near edge, and
 *      – liner length,
 *    with stacked rails; the **top rail is OAL only**.
 *  • Uses PDF-specific styling; preview-only styling options are not consumed here.
 */
fun composeShaftPdf(
    page: PdfDocument.Page,
    spec: ShaftSpec,
    unit: UnitSystem,
    project: ProjectInfo,
    appVersion: String,
    filename: String,
    pdfPrefs: PdfPrefs = PdfPrefs(),
    options: PdfExportOptions = PdfExportOptions(),
    resolvedComponents: List<ResolvedComponent>? = null,
    lineThicknessScale: Float = 1.0f,
) {
    val effectiveOptions = when (options.mode) {
        PdfExportMode.Template -> options.copy(
            showDimensions = false,
            showLabels = false,
            showFooter = false,
        )
        PdfExportMode.Standard -> options
    }

    val c = page.canvas
    // PDF safety: explicitly paint a white page background so geometry/labels are visible
    // even if the viewer/app is in dark mode (some viewers treat an unpainted page as dark).
    c.drawColor(Color.WHITE)
    val pageW = page.info.pageWidth.toFloat()
    val pageH = page.info.pageHeight.toFloat()

    VerboseLog.d(VerboseLog.Category.PDF, "ShaftPdf") {
        "compose start: page=${page.info.pageWidth}x${page.info.pageHeight}pt filename=$filename unit=$unit oalMm=${"%.3f".format(spec.overallLengthMm)}" +
            " parts(bodies=${spec.bodies.size}, tapers=${spec.tapers.size}, threads=${spec.threads.size}, liners=${spec.liners.size})"
    }

    val geomRect = RectF(
        PAGE_MARGIN_PT,
        PAGE_MARGIN_PT + TOP_TEXT_PAD_PT,
        pageW - PAGE_MARGIN_PT,
        pageH - PAGE_MARGIN_PT - FOOTER_BLOCK_PT
    )

    // PDF-only guard: a shaft with exactly one Body and no other detail components.
    // For body-only shafts there is nothing to "make room for", so do not apply
    // any body compression/break logic.
    val resolvedBodies = resolvedComponents
        ?.filterIsInstance<ResolvedBody>()
        ?.map { b ->
            Body(
                id = b.id,
                startFromAftMm = b.startMmPhysical,
                lengthMm = b.endMmPhysical - b.startMmPhysical,
                diaMm = b.diaMm
            )
        }

    val bodiesForPdf = resolvedBodies ?: spec.bodies
    val hasNonBodyDetail = spec.tapers.isNotEmpty() || spec.threads.isNotEmpty() || spec.liners.isNotEmpty()
    val bodyOnlyResolved = resolvedBodies != null && resolvedBodies.size == 1 && !hasNonBodyDetail

    val bodyOnly = isBodyOnlyShaft(spec) || bodyOnlyResolved
    val singleTaperOnly = isSingleTaperOnly(spec)

    val scale = lineThicknessScale.coerceIn(0.5f, 2.0f)
    val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = OUTLINE_PT_BASE * scale; color = 0xFF000000.toInt()
    }
    val dim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = DIM_PT_BASE * scale; color = 0xFF000000.toInt()
    }
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; textSize = TEXT_PT
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        color = 0xFF000000.toInt()
    }

    // Total content span: shaft body (0..OAL) plus any excluded end threads that sit
    // outside the OAL range.  AFT excluded threads have startFromAftMm = -lengthMm (negative);
    // FWD excluded threads have startFromAftMm = OAL and extend to OAL + lengthMm.
    // ptPerMm must be derived from this full span so neither end clips the page margin.
    val contentMinMm = minOf(0f,
        spec.threads.filter { it.excludeFromOAL && it.isAftEnd }
            .minOfOrNull { it.startFromAftMm } ?: 0f
    )
    val contentMaxMm = maxOf(spec.overallLengthMm,
        spec.threads.filter { it.excludeFromOAL && !it.isAftEnd }
            .maxOfOrNull { it.startFromAftMm + it.lengthMm } ?: spec.overallLengthMm
    )
    val contentSpanMm = (contentMaxMm - contentMinMm).coerceAtLeast(1f)

    // For the width-fit constraint, shrink the available page width proportionally so that
    // the scale derived from the shaft OAL alone (used by computeDetailPtPerMm) still fits
    // the full content span inside geomRect.  Scaling identity:
    //   ptPerMm = geomWidth / contentSpanMm  =  (geomWidth × oal/contentSpan) / oal
    val oalMmClamped = max(1f, spec.overallLengthMm)
    val effectiveGeomWidthPt = geomRect.width() * (oalMmClamped / contentSpanMm)

    val ptPerMm = when {
        bodyOnly || hasNonBodyDetail -> {
            // Target a stable drawn shaft height (~1.25") while never exceeding the page width
            // or the full content span (including excluded end threads).
            computeDetailPtPerMm(spec, effectiveGeomWidthPt, geomRect.height())
        }
        else -> {
            // Bodies-only: classic width-fit to the full content span.
            geomRect.width() / contentSpanMm
        }
    }
    // Origin offset: shift right so excluded AFT threads (at negative mm) have drawing room.
    val left = geomRect.left + (-contentMinMm * ptPerMm).coerceAtLeast(0f)

    val winDbg = computeOalWindow(spec)
    VerboseLog.d(VerboseLog.Category.PDF, "ShaftPdf") {
        "layout: geomRect=${geomRect.width().toInt()}x${geomRect.height().toInt()}pt ptPerMm=${"%.4f".format(ptPerMm)} maxDiaMm=${"%.3f".format(spec.maxOuterDiaMm())}" +
            " oalWindow(start=${"%.3f".format(winDbg.measureStartMm)}, end=${"%.3f".format(winDbg.measureEndMm)}, oal=${"%.3f".format(winDbg.oalMm)})"
    }

    val maxDiaMm = spec.maxOuterDiaMm().coerceAtLeast(1f)
    val halfHeightPx = (maxDiaMm * 0.5f) * ptPerMm
    // Place the shaft centered on the paper when possible.
    // Clamp so the shaft stays inside geomRect and the footer block can still fit below.
    val minCy = geomRect.top + halfHeightPx
    val maxCy = min(
        geomRect.bottom - halfHeightPx,
        pageH - PAGE_MARGIN_PT - FOOTER_BLOCK_PT - INFO_GAP_PT - halfHeightPx
    )
    val desiredCy = pageH * 0.5f + SHAFT_DOWN_PT
    val cy = if (minCy <= maxCy) desiredCy.coerceIn(minCy, maxCy) else (geomRect.top + geomRect.bottom) * 0.5f
    val yTopOfShaft = cy - halfHeightPx

    val pageDrawableHeightPx = geomRect.height()
    val shaftBoundsHeightPx = maxDiaMm * ptPerMm
    val verticalOffsetPx = if (effectiveOptions.mode == PdfExportMode.Template) {
        (pageDrawableHeightPx - shaftBoundsHeightPx) / 2f
    } else {
        0f
    }
    val shaftTranslateY = if (effectiveOptions.mode == PdfExportMode.Template) {
        (geomRect.top + verticalOffsetPx) - yTopOfShaft
    } else {
        0f
    }

    fun xAt(mm: Float) = left + mm * ptPerMm
    fun rPx(d: Float)  = (d * 0.5f) * ptPerMm

    // geometry
    c.save()
    if (shaftTranslateY != 0f) {
        c.translate(0f, shaftTranslateY)
    }
    fun shadeFill() = Paint().apply { style = Paint.Style.FILL; color = Color.argb(40, 0, 0, 0) }
    val bodyFill:  Paint? = if (pdfPrefs.shadedBodies)  shadeFill() else null
    val taperFill: Paint? = if (pdfPrefs.shadedTapers) shadeFill() else null
    val linerFill: Paint? = if (pdfPrefs.shadedLiners)  shadeFill() else null

    if (bodyOnly || singleTaperOnly) {
        drawBodiesPlain(c, bodiesForPdf, cy, ::xAt, ::rPx, outline, bodyFill)
    } else {
        drawBodiesCompressedCenterBreak(c, bodiesForPdf, cy, ::xAt, ::rPx, outline, geomRect, bodyFill)
    }
    drawTapers(c, spec.tapers, cy, ::xAt, ::rPx, outline, taperFill)
    drawThreads(c, spec.threads, cy, ::xAt, ::rPx, outline, dim, ptPerMm)
    drawLiners(c, spec.liners, cy, ::xAt, ::rPx, outline, dim, linerFill)
    c.restore()

    if (effectiveOptions.showLabels && pdfPrefs.showComponentTitles) {
        drawComponentLabelsPdf(
            canvas = c,
            spec = spec,
            geomRect = geomRect,
            cy = cy,
            halfHeightPx = halfHeightPx,
            xAt = ::xAt,
            textPaint = text,
        )
    }

    if (effectiveOptions.showDimensions) {
        val baseY = yTopOfShaft - BAND_CLEAR_PT - BASE_DIM_OFFSET_PT

        // Fit-to-band safety for dimensional rails (OAL always visible)
        val topSafePad = 6f
        var railGap = LANE_GAP_PT + 6f
        val minRailGap = 10f
        var dimTextSize = TEXT_PT - 2f
        val minDimTextSize = 7f
        val dimText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            textSize = dimTextSize
            color = 0xFF000000.toInt()
        }

        // Measurement reference (AFT/FWD/AUTO) is separate from tier origin.
        val measureFromMode = pdfPrefs.tieringMode
        val linerDims = mapToLinerDimsForPdf(spec, measureFromMode)
        val win  = computeOalWindow(spec)
        val pageX: (Double) -> Float = { dimMm ->
            (left + ((dimMm + win.measureStartMm).toFloat() * ptPerMm))
        }
        val sets = computeSetPositionsInMeasureSpace(win, spec)
        val spans = buildLinerSpans(
            liners = linerDims,
            sets = sets,
            unit = unit,
            measureFrom = measureFromMode
        ) + buildTaperLengthSpans(spec, win, unit)
        val planner = RailPlanner()
        val tierOriginMm = tierOriginMmFor(pdfPrefs.tieringMode, win.oalMm)
        val assignments = planner.assignAll(spans, tierOriginMm)

        val maxRail = assignments.maxOfOrNull { it.rail } ?: 0
        val extraClearRails = (pdfPrefs.oalSpacingFactor.coerceIn(1.0f, 6.0f) - 1.0f) * 0.5f
        // (already declared above)
        fun computeTopY(gap: Float): Float = baseY - OVERALL_EXTRA_PT - gap * (maxRail + 1f + extraClearRails)

        var topY = computeTopY(railGap)
        repeat(10) {
            if (topY >= geomRect.top + topSafePad) return@repeat
            if (railGap > minRailGap) {
                railGap = maxOf(railGap - 2f, minRailGap)
            } else if (dimTextSize > minDimTextSize) {
                dimTextSize = maxOf(dimTextSize - 1f, minDimTextSize)
                dimText.textSize = dimTextSize
            }
            topY = computeTopY(railGap)
        }
        // Final clamp after loop
        topY = max(computeTopY(railGap), geomRect.top + topSafePad)

        val renderer = PdfDimensionRenderer(
            pageX = pageX,
            baseY = baseY,
            railDy = railGap,
            topRailY = topY,
            linePaint = dim,
            textPaint = dimText,
            objectTopY = yTopOfShaft,
            contentTopPx = geomRect.top,
            objectClearance = 6f
        )

        assignments.forEach { rs ->
            renderer.drawOnRail(c, rs.rail, rs.span, true)
        }
        val oalAft = if (spec.threads.any { t ->
            abs(t.startFromAftMm.toDouble()) <= END_EPS_MM && !t.excludeFromOAL
        }) 0.0 else sets.aftSETxMm
        val oalFwd = if (spec.threads.any { t ->
            abs((t.startFromAftMm + t.lengthMm).toDouble() - spec.overallLengthMm.toDouble()) <= END_EPS_MM && !t.excludeFromOAL
        }) win.oalMm else sets.fwdSETxMm
        renderer.drawTop(c, oalSpan(oalAft, oalFwd, unit, labelMm = spec.overallLengthMm.toDouble()), true)
    }

    // --- body Ø callouts: one leader per unique body OD ---
    run {
        val calls = buildBodyOdCallouts(bodiesForPdf)
        if (calls.isNotEmpty()) {
            val leaderText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                textSize = TEXT_PT - 2f
                color = 0xFF000000.toInt()
            }
            val leader = DiameterLeaderRenderer(
                pageX = { mm -> (left + (mm.toFloat() * ptPerMm)) },
                shaftTopY = yTopOfShaft,
                shaftBottomY = cy + (maxDiaMm * 0.5f) * ptPerMm,
                linePaint = dim,
                textPaint = leaderText
            )
            leader.draw(c, calls, unit)
        }
    }

    if (effectiveOptions.showFooter) {
        // footer
        val showCompressionNote = !bodyOnly && spec.bodies.any { b -> b.lengthMm * ptPerMm >= COMPRESS_TRIGGER_PT }

        val footerTapers = selectFooterTapers(spec)
        val footerCfg = FooterConfig(
            showAftThread = hasAftThread(spec),
            showFwdThread = hasFwdThread(spec),
            // Taper rendering is gated by detectEndFeatures(); this flag only controls whether
            // taper details are enabled for the footer at all.
            showAftTaper  = footerTapers.aft != null,
            showFwdTaper  = footerTapers.fwd != null,
            showCompressionNote = showCompressionNote
        )

        val infoTop = cy + halfHeightPx + INFO_GAP_PT
        val infoBottom = min(infoTop + FOOTER_BLOCK_PT, pageH - PAGE_MARGIN_PT)
        val infoRect = RectF(geomRect.left, infoTop, geomRect.right, infoBottom)

        drawFooter(c, infoRect, spec, unit, project, filename, appVersion, text, footerCfg)
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Constants
// ──────────────────────────────────────────────────────────────────────────────

private const val MM_PER_IN = 25.4f

// Body-only PDF rendering uses a stable, fixed target drawing height.
// 1.25 in keeps the shaft visible without eating the page.
private const val BODY_ONLY_TARGET_HEIGHT_PT = 1.25f * 72f

// Strokes / text
private const val OUTLINE_PT_BASE = 1.25f  // 100% default; slider goes to 200% (original 2.5pt)
private const val DIM_PT_BASE = 0.8f       // 100% default; slider goes to 200% (original 1.6pt)
private const val TEXT_PT = 12f

// Layout
private const val PAGE_MARGIN_PT = 36f       // 0.5 in
private const val TOP_TEXT_PAD_PT = 12f
private const val SHAFT_DOWN_PT = 36f        // 0.5 in downward shift (moves footer down too)
private const val BAND_CLEAR_PT = 12f        // breathing room above shaft before first dim line
private const val BASE_DIM_OFFSET_PT = 24f   // distance from shaft top to first component dim
private const val OVERALL_EXTRA_PT = 16f     // overall lane sits above components
private const val LANE_GAP_PT = 24f          // spacing between dimension lanes

// Component title labels (PDF only)
private const val COMPONENT_LABEL_OFFSET_PT = 32f
private const val INFO_GAP_PT = 72f          // exactly 1 inch below geometry
private const val FOOTER_BLOCK_PT = 96f

private fun drawComponentLabelsPdf(
    canvas: Canvas,
    spec: ShaftSpec,
    geomRect: RectF,
    cy: Float,
    halfHeightPx: Float,
    xAt: (Float) -> Float,
    textPaint: Paint,
) {
    if (spec.bodies.isEmpty() && spec.tapers.isEmpty() && spec.threads.isEmpty() && spec.liners.isEmpty()) return

    val labelPaint = Paint(textPaint).apply {
        textSize = (textSize - 2f).coerceAtLeast(8f)
    }

    val yBottomOfShaft = cy + halfHeightPx
    val baseY    = (yBottomOfShaft + COMPONENT_LABEL_OFFSET_PT).coerceAtMost(geomRect.bottom - 6f)
    val rowStep  = labelPaint.textSize * 1.4f
    val padX     = 3f  // minimum horizontal gap between adjacent labels on the same row

    // Collect every label as a placed x-interval + text, then assign rows.
    data class Entry(val xLeft: Float, val xRight: Float, val text: String)

    fun entry(label: String, startMm: Float, endMm: Float): Entry? {
        val trimmed = label.trim().ifEmpty { return null }
        val cx = (xAt(startMm) + xAt(endMm)) * 0.5f
        val w  = labelPaint.measureText(trimmed)
        val xL = (cx - w * 0.5f).coerceIn(geomRect.left, geomRect.right - w)
        return Entry(xL, xL + w, trimmed)
    }

    val entries = buildList {
        val bodyTitleById = buildBodyTitleById(spec)
        spec.bodies.sortedWith(compareBy({ it.startFromAftMm }, { it.id }))
            .forEachIndexed { i, b -> entry(bodyTitleById[b.id] ?: "Body #${i+1}", b.startFromAftMm, b.startFromAftMm + b.lengthMm)?.let(::add) }

        val taperTitleById = buildTaperTitleById(spec)
        spec.tapers.sortedWith(compareBy({ it.startFromAftMm }, { it.id }))
            .forEachIndexed { i, t -> entry(taperTitleById[t.id] ?: "Taper #${i+1}", t.startFromAftMm, t.startFromAftMm + t.lengthMm)?.let(::add) }

        val threadTitleById = buildThreadTitleById(spec)
        spec.threads.sortedWith(compareBy({ it.startFromAftMm }, { it.id }))
            .forEachIndexed { i, th -> entry(threadTitleById[th.id] ?: "Thread #${i+1}", th.startFromAftMm, th.startFromAftMm + th.lengthMm)?.let(::add) }

        val linerTitleById = buildLinerTitleById(spec)
        spec.liners.sortedWith(compareBy({ it.startFromAftMm }, { it.id }))
            .forEachIndexed { i, ln ->
                val label = ln.label?.trim()?.ifEmpty { null } ?: linerTitleById[ln.id] ?: "Liner ${i+1}"
                entry(label, ln.startFromAftMm, ln.startFromAftMm + ln.lengthMm)?.let(::add)
            }
    }.sortedBy { it.xLeft }

    // Greedy row assignment: place each label on the first row where it doesn't overlap.
    val rowOccupied = mutableListOf<MutableList<Pair<Float, Float>>>()

    for (e in entries) {
        var row = 0
        while (true) {
            if (row >= rowOccupied.size) rowOccupied.add(mutableListOf())
            val free = rowOccupied[row].none { (oL, oR) -> e.xLeft < oR + padX && e.xRight + padX > oL }
            if (free) {
                rowOccupied[row].add(e.xLeft to e.xRight)
                val rowY = (baseY + row * rowStep).coerceAtMost(geomRect.bottom - 4f)
                canvas.drawText(e.text, e.xLeft, rowY, labelPaint)
                break
            }
            row++
        }
    }
}

// Compression (paper-space heuristic; bodies only)
private const val COMPRESS_TRIGGER_PT = 220f // if body length on paper ≥ this, show center-break
private const val ZIGZAG_GAP_MAX_PT = 20f    // max central gap width

// Label collision avoidance

private fun isBodyOnlyShaft(spec: ShaftSpec): Boolean {
    if (
        spec.bodies.size != 1 ||
        spec.tapers.isNotEmpty() ||
        spec.threads.isNotEmpty() ||
        spec.liners.isNotEmpty()
    ) {
        return false
    }

    // Future-proofing: also require *no* features that reach either end.
    // This prevents accidentally treating “body + end details” as “body-only”.
    val ends = detectEndFeatures(spec)
    return !(ends.aftThread || ends.fwdThread || ends.aftTaper || ends.fwdTaper)
}

private fun isSingleTaperOnly(spec: ShaftSpec): Boolean {
    if (spec.tapers.size != 1) return false
    if (spec.threads.isNotEmpty()) return false
    if (spec.liners.isNotEmpty()) return false
    // Allow at most one base body under the taper; more implies a multi-body shaft.
    if (spec.bodies.size > 1) return false
    return true
}

internal data class FooterTapers(
    val aft: Taper?,
    val fwd: Taper?
)

internal fun selectFooterTapers(spec: ShaftSpec): FooterTapers {
    val oal = spec.overallLengthMm
    val tapers = spec.tapers
        .asSequence()
        .filter { it.lengthMm > 0f && it.startDiaMm > 0f && it.endDiaMm > 0f }
        .toList()
    if (tapers.isEmpty()) return FooterTapers(aft = null, fwd = null)

    fun midMm(t: Taper): Float = t.startFromAftMm + t.lengthMm * 0.5f

    if (tapers.size == 1) {
        val t = tapers.first()
        // Default to AFT if we can't meaningfully classify.
        if (oal <= 0f) return FooterTapers(aft = t, fwd = null)
        return if (midMm(t) <= oal * 0.5f) FooterTapers(aft = t, fwd = null) else FooterTapers(aft = null, fwd = t)
    }

    val sorted = tapers.sortedBy(::midMm)
    return FooterTapers(aft = sorted.first(), fwd = sorted.last())
}

internal fun computeBodyOnlyPtPerMm(spec: ShaftSpec, geomWidthPt: Float): Float {
    // Explicit guards: avoid divide-by-zero / infinity scaling.
    // In pathological specs, fall back to a safe 1mm baseline.
    val overallMm = if (spec.overallLengthMm > 0f) spec.overallLengthMm else 1f
    val maxDiaMmRaw = spec.maxOuterDiaMm()
    val maxDiaMm = if (maxDiaMmRaw > 0f) maxDiaMmRaw else 1f

    val byWidth = geomWidthPt / overallMm
    val byTargetHeight = BODY_ONLY_TARGET_HEIGHT_PT / maxDiaMm
    return min(byWidth, byTargetHeight)
}

internal fun computeDetailPtPerMm(spec: ShaftSpec, geomWidthPt: Float, geomHeightPt: Float): Float {
    // Same target-height behavior as body-only, but also never exceed the available content height.
    val overallMm = if (spec.overallLengthMm > 0f) spec.overallLengthMm else 1f
    val maxDiaMmRaw = spec.maxOuterDiaMm()
    val maxDiaMm = if (maxDiaMmRaw > 0f) maxDiaMmRaw else 1f

    val byWidth = geomWidthPt / overallMm
    val byTargetHeight = BODY_ONLY_TARGET_HEIGHT_PT / maxDiaMm
    val byGeomHeight = geomHeightPt / maxDiaMm
    return requireFinite("ptPerMm", min(byWidth, min(byTargetHeight, byGeomHeight)).coerceAtLeast(1e-6f))
}

internal fun computePdfPtPerMmFitAxes(spec: ShaftSpec, geomWidthPt: Float, geomHeightPt: Float): Float {
    // Explicit guards: prevent divide-by-zero / infinity scaling.
    val overallMm = spec.overallLengthMm.takeIf { it > 0f } ?: 1f
    val maxDiaMmRaw = spec.maxOuterDiaMm()
    val maxDiaMm = maxDiaMmRaw.takeIf { it > 0f } ?: 1f

    val byWidth = geomWidthPt / overallMm
    val byHeight = geomHeightPt / maxDiaMm

    return requireFinite("ptPerMm", min(byWidth, byHeight).coerceAtLeast(1e-6f))
}

private fun requireFinite(name: String, v: Float): Float {
    if (!v.isFinite()) throw IllegalArgumentException("$name is not finite: $v")
    return v
}

private fun drawBodiesPlain(
    c: Canvas,
    bodies: List<Body>,
    cy: Float,
    xAt: (Float) -> Float,
    rPx: (Float) -> Float,
    outline: Paint,
    fill: Paint? = null,
) {
    bodies.forEach { b ->
        if (b.lengthMm <= 0f || b.diaMm <= 0f) return@forEach
        val x0 = xAt(b.startFromAftMm)
        val x1 = xAt(b.startFromAftMm + b.lengthMm)
        val r = rPx(b.diaMm)
        val top = cy - r
        val bot = cy + r

        if (fill != null) c.drawRect(x0, top, x1, bot, fill)
        c.drawLine(x0, top, x1, top, outline)
        c.drawLine(x0, bot, x1, bot, outline)
        c.drawLine(x0, top, x0, bot, outline)
        c.drawLine(x1, top, x1, bot, outline)
    }
}

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
    geomRect: RectF,
    fill: Paint? = null,
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
            if (fill != null) c.drawRect(x0, top, x1, bot, fill)
            c.drawLine(x0, top, x1, top, outline)
            c.drawLine(x0, bot, x1, bot, outline)
            c.drawLine(x0, top, x0, bot, outline)
            c.drawLine(x1, top, x1, bot, outline)
        } else {
            // centered break: two stubs, each with an S-curve end instead of a straight cap
            val mid = (x0 + x1) * 0.5f
            val gap = min(ZIGZAG_GAP_MAX_PT, 0.25f * bodyLenPt)
            val half = 0.5f * gap
            val leftEnd  = (mid - half).coerceIn(geomRect.left, geomRect.right)
            val rightBeg = (mid + half).coerceIn(geomRect.left, geomRect.right)
            val amp = r * 0.6f

            // Left stub — S-curve on right end
            if (fill != null) c.drawRect(x0, top, leftEnd, bot, fill)
            c.drawLine(x0, top, leftEnd, top, outline)
            c.drawLine(x0, bot, leftEnd, bot, outline)
            c.drawLine(x0, top, x0, bot, outline)
            drawBreakEdge(c, leftEnd, top, bot, amp, capPaint)

            // Right stub — same-direction S-curve on left end (curves match so edges appear to merge)
            if (fill != null) c.drawRect(rightBeg, top, x1, bot, fill)
            drawBreakEdge(c, rightBeg, top, bot, amp, capPaint)
            c.drawLine(rightBeg, top, x1, top, outline)
            c.drawLine(rightBeg, bot, x1, bot, outline)
            c.drawLine(x1, top, x1, bot, outline)
        }
    }
}

/**
 * Adds one LOCAL span per end-taper so taper lengths are shown on the diagram.
 *
 * Span endpoints are expressed in measurement-space (rebased by [win.measureStartMm]).
 * Do not classify these as DATUM even if they touch a SET; these are feature↔feature lengths.
 */
internal fun buildTaperLengthSpans(spec: ShaftSpec, win: com.android.shaftschematic.geom.OalWindow, unit: UnitSystem): List<DimSpan> = buildList {
    getAftEndTaper(spec)?.let { tp ->
        val x0 = win.toMeasureX(tp.startFromAftMm.toDouble())
        val x1 = win.toMeasureX((tp.startFromAftMm + tp.lengthMm).toDouble())
        add(
            DimSpan(
                x0,
                x1,
                labelTop = formatLenDim(abs(x1 - x0), unit),
                kind = SpanKind.LOCAL
            )
        )
    }

    getFwdEndTaper(spec)?.let { tp ->
        val x0 = win.toMeasureX(tp.startFromAftMm.toDouble())
        val x1 = win.toMeasureX((tp.startFromAftMm + tp.lengthMm).toDouble())
        add(
            DimSpan(
                x0,
                x1,
                labelTop = formatLenDim(abs(x1 - x0), unit),
                kind = SpanKind.LOCAL
            )
        )
    }
}


/**
 * Adapter from model liners to export-only LinerDim.
 * Anchor is inferred by proximity to SETs; swap to explicit anchors if your model stores them.
 */

internal fun mapToLinerDimsForPdf(spec: ShaftSpec, measureFrom: PdfTieringMode): List<LinerDim> {
    val win  = computeOalWindow(spec)
    val sets = computeSetPositionsInMeasureSpace(win, spec)

    return spec.liners.map { ln ->
        // Edges in measurement space (AFT→FWD)
        val aftEdge = win.toMeasureX(ln.startFromAftMm.toDouble())
        val fwdEdge = aftEdge + ln.lengthMm.toDouble()
        val length  = (fwdEdge - aftEdge).coerceAtLeast(0.0)

        // Compare SET→nearest edge distances using the correct edge per SET
        val distAft = (aftEdge - sets.aftSETxMm).coerceAtLeast(0.0)      // AFT SET → AFT edge
        val distFwd = (sets.fwdSETxMm - fwdEdge).coerceAtLeast(0.0)      // FWD SET → FWD edge

        val forcedAnchor = when (measureFrom) {
            PdfTieringMode.AFT -> LinerAnchor.AFT_SET
            PdfTieringMode.FWD -> LinerAnchor.FWD_SET
            PdfTieringMode.AUTO -> null
        }
        // Forced AFT/FWD overrides any per-component anchoring; AUTO keeps existing behavior.
        val anchor = forcedAnchor ?: if (distFwd < distAft) LinerAnchor.FWD_SET else LinerAnchor.AFT_SET
        val offset = when (anchor) {
            LinerAnchor.AFT_SET -> distAft
            LinerAnchor.FWD_SET -> distFwd
        }
        LinerDim(
            id = ln.id,
            anchor = anchor,
            offsetFromSetMm = offset,
            lengthMm = length
        )
    }
}

/**
 * Resolve a single tier origin for the layout pass.
 * - AFT → 0
 * - FWD → OAL
 * - AUTO → null (preserve existing left-to-right tiering)
 */
private fun tierOriginMmFor(mode: PdfTieringMode, oalMm: Double): Double? = when (mode) {
    PdfTieringMode.AFT -> 0.0
    PdfTieringMode.FWD -> oalMm
    PdfTieringMode.AUTO -> null
}

/** S-curve from (x, yTop) to (x, yBot). Positive [amplitude] bulges right then left; negative mirrors. */
private fun drawBreakEdge(c: Canvas, x: Float, yTop: Float, yBot: Float, amplitude: Float, p: Paint) {
    val h = yBot - yTop
    val path = Path().apply {
        moveTo(x, yTop)
        cubicTo(x + amplitude, yTop + h / 3f, x - amplitude, yBot - h / 3f, x, yBot)
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
    fill: Paint? = null,
) {
    tapers.forEach { t ->
        if (t.lengthMm <= 0f || (t.startDiaMm <= 0f && t.endDiaMm <= 0f)) return@forEach
        val x0 = requireFinite("taper.x0", xAt(t.startFromAftMm))
        val x1 = requireFinite("taper.x1", xAt(t.startFromAftMm + t.lengthMm))
        val r0 = requireFinite("taper.r0", rPx(t.startDiaMm))
        val r1 = requireFinite("taper.r1", rPx(t.endDiaMm))
        val top0 = requireFinite("taper.top0", cy - r0); val bot0 = requireFinite("taper.bot0", cy + r0)
        val top1 = requireFinite("taper.top1", cy - r1); val bot1 = requireFinite("taper.bot1", cy + r1)

        if (fill != null) {
            val path = Path().apply {
                moveTo(x0, top0); lineTo(x1, top1); lineTo(x1, bot1); lineTo(x0, bot0); close()
            }
            c.drawPath(path, fill)
        }
        c.drawLine(x0, top0, x1, top1, outline)
        c.drawLine(x0, bot0, x1, bot1, outline)
        c.drawLine(x0, top0, x0, bot0, outline)
        c.drawLine(x1, top1, x1, bot1, outline)

        if (t.hasKeyway) {
            drawKeywayNotchPdf(c, t, x0, x1, top0, top1, cy, outline)
        }
    }
}

internal fun drawKeywayNotchPdf(
    c: Canvas,
    t: Taper,
    x0: Float, x1: Float,
    @Suppress("UNUSED_PARAMETER") top0: Float,
    @Suppress("UNUSED_PARAMETER") top1: Float,
    cy: Float,
    outline: Paint,
) {
    if (x1 == x0 || t.keywayWidthMm <= 0f) return

    val setAtStart = t.startDiaMm <= t.endDiaMm
    val setX = if (setAtStart) x0 else x1
    val letX = if (setAtStart) x1 else x0
    val dir  = if (letX > setX) 1f else -1f

    // Scale mm → pt using the taper's own pixel span (avoids needing ptPerMm).
    val ptPerMm = if (t.lengthMm > 0f) kotlin.math.abs(x1 - x0) / t.lengthMm else 1f

    val halfW       = (t.keywayWidthMm * ptPerMm) / 2f
    val kwSetX      = setX + dir * t.keywayOffsetFromSetMm * ptPerMm
    val kwLetX      = kwSetX + dir * t.keywayLengthMm * ptPerMm
    val isOpen      = t.keywayOffsetFromSetMm < 0.01f

    val letArcCx    = kwLetX - dir * halfW
    val letArcStart = if (dir > 0) 270f else 90f
    val letOval     = android.graphics.RectF(letArcCx - halfW, cy - halfW, letArcCx + halfW, cy + halfW)

    val setArcCx    = kwSetX + dir * halfW
    val setArcStart = if (dir > 0) 90f else 270f
    val setOval     = android.graphics.RectF(setArcCx - halfW, cy - halfW, setArcCx + halfW, cy + halfW)

    val lineNear  = if (isOpen) kwSetX else setArcCx
    val lineFar   = letArcCx
    val lineLeft  = min(lineNear, lineFar)
    val lineRight = max(lineNear, lineFar)

    // ── White fill (keyway is a void in the material) ──
    // Inset from the SET face by one stroke-width so the taper end-face line keeps
    // its full thickness where it meets the keyway fill.
    val strokeW   = outline.strokeWidth
    val fillNear  = if (isOpen) kwSetX + dir * strokeW else setArcCx
    val fillLeft  = min(fillNear, letArcCx)
    val fillRight = max(fillNear, letArcCx)
    val whiteFill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.FILL
        color = android.graphics.Color.WHITE
    }
    c.drawRect(fillLeft, cy - halfW, fillRight, cy + halfW, whiteFill)
    c.drawArc(letOval, letArcStart, 180f, false, whiteFill)
    if (!isOpen) c.drawArc(setOval, setArcStart, 180f, false, whiteFill)

    // ── Outline strokes on top ──
    c.drawLine(lineLeft, cy - halfW, lineRight, cy - halfW, outline)
    c.drawLine(lineLeft, cy + halfW, lineRight, cy + halfW, outline)
    c.drawArc(letOval, letArcStart, 180f, false, outline)
    if (!isOpen) c.drawArc(setOval, setArcStart, 180f, false, outline)
    // Open keyway: shaft face end-line already closes the SET end.
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
    fill: Paint? = null,
) {
    liners.forEach { ln ->
        if (ln.lengthMm <= 0f || ln.odMm <= 0f) return@forEach
        val x0 = xAt(ln.startFromAftMm); val x1 = xAt(ln.startFromAftMm + ln.lengthMm)
        val r = rPx(ln.odMm); val top = cy - r; val bot = cy + r
        if (fill != null) c.drawRect(x0, top, x1, bot, fill)
        c.drawLine(x0, top, x1, top, outline)
        c.drawLine(x0, bot, x1, bot, outline)
        c.drawLine(x0, top, x0, bot, dim) // thin end ticks
        c.drawLine(x1, top, x1, bot, dim)
    }
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
    val top = rect.top + 6f
    val lh = text.textSize * 1.35f

    val cols = buildFooterEndColumns(spec, unit, cfg)

    val leftX  = rect.left
    val midX   = rect.left + rect.width() * 0.40f
    val rightX = rect.left + rect.width() * 0.76f

    // AFT (left) — left-aligned at left margin
    run {
        var y = top
        cols.aftLines.forEach { line ->
            c.drawText(line, leftX, y, text)
            y += lh
        }
    }

    // Middle (Work order) — left-aligned at 1/3 mark
    run {
        var y = top
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        c.drawText("Customer: ${project.customer}", midX, y, text); y += lh
        c.drawText("Vessel: ${project.vessel}",     midX, y, text); y += lh
        c.drawText("Job #: ${project.jobNumber}",   midX, y, text); y += lh
        c.drawText("Date: $date",                   midX, y, text); y += lh

        val uniqueODs = spec.bodies
            .filter { it.diaMm > 0f }
            .map { it.diaMm }
            .distinct()
            .sorted()
        if (uniqueODs.isNotEmpty()) {
            val label = uniqueODs.joinToString(", ") { "Ø ${formatDiaWithUnit(it.toDouble(), unit)}" }
            c.drawText("Body: $label", midX, y, text); y += lh
        }

        project.side.printableLabelOrNull()?.let { pos ->
            y += lh * 0.35f
            val posPaint = Paint(text).apply {
                textSize = text.textSize * 1.20f
                isFakeBoldText = true
            }
            c.drawText(pos, midX, y, posPaint)
        }
    }

    // FWD (right) — left-aligned at 2/3 mark
    run {
        var y = top
        cols.fwdLines.forEach { line ->
            c.drawText(line, rightX, y, text)
            y += lh
        }
    }
}

internal data class FooterColumns(
    val aftLines: List<String>,
    val fwdLines: List<String>
)

/**
 * Builds the exact left/right footer text lines that [drawFooter] will render.
 * Exposed for JVM unit tests so we can validate end-feature detection without
 * depending on Android Canvas/PdfDocument runtime.
 */
internal fun buildFooterEndColumns(spec: ShaftSpec, unit: UnitSystem, cfg: FooterConfig): FooterColumns {
    val ends = detectEndFeatures(spec)
    val taperSides = selectFooterTapers(spec)

    val aft = mutableListOf<String>()
    if (cfg.showAftTaper) {
        taperSides.aft?.let { tp ->
            val (let, set) = letSet(tp)
            aft += "AFT Taper"
            aft += "Rate: ${tp.taperRateText.trim().ifEmpty { rate1toN(tp) }}"
            aft += "L.E.T.: ${formatDiaWithUnit(let.toDouble(), unit)}"
            aft += "S.E.T.: ${formatDiaWithUnit(set.toDouble(), unit)}"
            aft += "Length: ${formatLenWithUnit(tp.lengthMm.toDouble(), unit)}"
            if (tp.keywayWidthMm > 0f && tp.keywayDepthMm > 0f) {
                val spoon = if (tp.keywaySpooned) " (spooned)" else ""
                aft += if (tp.keywayLengthMm > 0f) {
                    "KW: ${formatLenWithUnit(tp.keywayWidthMm.toDouble(), unit)} × ${formatLenWithUnit(tp.keywayDepthMm.toDouble(), unit)} × ${formatLenWithUnit(tp.keywayLengthMm.toDouble(), unit)}$spoon"
                } else {
                    "KW: ${formatLenWithUnit(tp.keywayWidthMm.toDouble(), unit)} × ${formatLenWithUnit(tp.keywayDepthMm.toDouble(), unit)}$spoon"
                }
            }
        }
    }
    if (cfg.showAftThread && ends.aftThread) {
        getAftEndThread(spec)?.let { th ->
            aft += "Thread: ${formatDiaWithUnit(th.majorDiaMm.toDouble(), unit)} × ${fmtTpi(tpiFromPitch(th.pitchMm))} TPI × ${formatLenWithUnit(th.lengthMm.toDouble(), unit)}"
        }
    }

    val fwd = mutableListOf<String>()
    if (cfg.showFwdTaper) {
        taperSides.fwd?.let { tp ->
            val (let, set) = letSet(tp)
            fwd += "FWD Taper"
            fwd += "Rate: ${tp.taperRateText.trim().ifEmpty { rate1toN(tp) }}"
            fwd += "L.E.T.: ${formatDiaWithUnit(let.toDouble(), unit)}"
            fwd += "S.E.T.: ${formatDiaWithUnit(set.toDouble(), unit)}"
            fwd += "Length: ${formatLenWithUnit(tp.lengthMm.toDouble(), unit)}"
            if (tp.keywayWidthMm > 0f && tp.keywayDepthMm > 0f) {
                val spoon = if (tp.keywaySpooned) " (spooned)" else ""
                fwd += if (tp.keywayLengthMm > 0f) {
                    "KW: ${formatLenWithUnit(tp.keywayWidthMm.toDouble(), unit)} × ${formatLenWithUnit(tp.keywayDepthMm.toDouble(), unit)} × ${formatLenWithUnit(tp.keywayLengthMm.toDouble(), unit)}$spoon"
                } else {
                    "KW: ${formatLenWithUnit(tp.keywayWidthMm.toDouble(), unit)} × ${formatLenWithUnit(tp.keywayDepthMm.toDouble(), unit)}$spoon"
                }
            }
        }
    }
    if (cfg.showFwdThread && ends.fwdThread) {
        getFwdEndThread(spec)?.let { th ->
            fwd += "Thread: ${formatDiaWithUnit(th.majorDiaMm.toDouble(), unit)} × ${fmtTpi(tpiFromPitch(th.pitchMm))} TPI × ${formatLenWithUnit(th.lengthMm.toDouble(), unit)}"
        }
    }

    return FooterColumns(aftLines = aft, fwdLines = fwd)
}

// ──────────────────────────────────────────────────────────────────────────────
// Helpers
// ──────────────────────────────────────────────────────────────────────────────

/**
 * One [DiaCallout] per unique body OD, anchored at the center of the longest
 * body with that diameter.  Multiple ODs alternate ABOVE/BELOW to prevent overlap.
 */
internal fun buildBodyOdCallouts(bodies: List<Body>): List<DiaCallout> =
    bodies
        .filter { it.diaMm > 0f }
        .groupBy { it.diaMm }
        .entries
        .sortedByDescending { it.key }
        .mapIndexed { idx, (diaMm, group) ->
            val anchor = group.maxByOrNull { it.lengthMm } ?: return@mapIndexed null
            val centerMm = (anchor.startFromAftMm + anchor.lengthMm * 0.5).toDouble()
            val side = if (idx % 2 == 0) LeaderSide.ABOVE else LeaderSide.BELOW
            DiaCallout(xMm = centerMm, valueMm = diaMm.toDouble(), side = side)
        }
        .filterNotNull()

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
    "INCHES", "IN" -> String.format(Locale.US, "%.3f", mm / MM_PER_IN).trimEnd('0').trimEnd('.') + "\""
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

/** Presence checks for end features. */
private fun hasAftThread(spec: ShaftSpec): Boolean =
    spec.threads.any { it.startFromAftMm <= 0.5f }    // thread touches AFT end

private fun hasFwdThread(spec: ShaftSpec): Boolean {
    val oal = spec.overallLengthMm
    return spec.threads.any { (it.startFromAftMm + it.lengthMm) >= (oal - 0.5f) } // thread touches FWD end
}

private fun hasAftTaper(spec: ShaftSpec): Boolean =
    spec.tapers.any { tp ->
        // touches AFT end if its start is near 0 OR it spans across 0
        val start = tp.startFromAftMm.toDouble()
        val end   = (tp.startFromAftMm + tp.lengthMm).toDouble()
        start <= END_EPS_MM || (start < 0.0 && end > 0.0)
    }

private fun hasFwdTaper(spec: ShaftSpec): Boolean {
    val oal = spec.overallLengthMm.toDouble()
    return spec.tapers.any { tp ->
        val start = tp.startFromAftMm.toDouble()
        val end   = (tp.startFromAftMm + tp.lengthMm).toDouble()
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

private fun detectEndFeatures(spec: ShaftSpec, epsMm: Double = END_EPS_MM): EndFlags {
    val aftX = 0.0
    val fwdX = spec.overallLengthMm.toDouble()

    fun near(a: Double, b: Double) = abs(a - b) <= epsMm

    // Threads — also match excluded threads (their startFromAftMm is negative or ≥ OAL,
    // but they physically live at the shaft ends).
    val aftThread = spec.threads.any { th ->
        th.lengthMm > epsMm &&
            (near(th.startFromAftMm.toDouble(), aftX) || (th.excludeFromOAL && th.isAftEnd))
    }
    val fwdThread = spec.threads.any { th ->
        th.lengthMm > epsMm &&
            (near((th.startFromAftMm + th.lengthMm).toDouble(), fwdX) || (th.excludeFromOAL && !th.isAftEnd))
    }

    // If an end-thread exists, its shoulder can be the effective boundary for a taper.
    // Example: AFT thread starts at X=0 and a taper starts at X=threadEnd.
    val aftThreadEndX = spec.threads
        .asSequence()
        .filter { th -> near(th.startFromAftMm.toDouble(), aftX) && th.lengthMm > epsMm }
        .minByOrNull { it.startFromAftMm }
        ?.let { (it.startFromAftMm + it.lengthMm).toDouble() }

    val fwdThreadStartX = spec.threads
        .asSequence()
        .filter { th -> near((th.startFromAftMm + th.lengthMm).toDouble(), fwdX) && th.lengthMm > epsMm }
        .maxByOrNull { it.startFromAftMm + it.lengthMm }
        ?.startFromAftMm
        ?.toDouble()

    // Tapers
    val aftTaper = spec.tapers.any { tp ->
        tp.lengthMm > epsMm && (
            near(tp.startFromAftMm.toDouble(), aftX) ||
                (aftThreadEndX != null && near(tp.startFromAftMm.toDouble(), aftThreadEndX))
            )
    }
    val fwdTaper = spec.tapers.any { tp ->
        val endX = (tp.startFromAftMm + tp.lengthMm).toDouble()
        tp.lengthMm > epsMm && (
            near(endX, fwdX) ||
                (fwdThreadStartX != null && near(endX, fwdThreadStartX))
            )
    }

    return EndFlags(aftThread, fwdThread, aftTaper, fwdTaper)
}

private fun near(a: Double, b: Double, eps: Double = END_EPS_MM) =
    abs(a - b) <= eps

private fun getAftEndThread(spec: ShaftSpec): Threads? =
    spec.threads
        .asSequence()
        .filter { th ->
            th.lengthMm > END_EPS_MM &&
                (near(th.startFromAftMm.toDouble(), 0.0) || (th.excludeFromOAL && th.isAftEnd))
        }
        .minByOrNull { it.startFromAftMm }

private fun getFwdEndThread(spec: ShaftSpec): Threads? {
    val fwdX = spec.overallLengthMm.toDouble()
    return spec.threads
        .asSequence()
        .filter { th ->
            th.lengthMm > END_EPS_MM &&
                (near((th.startFromAftMm + th.lengthMm).toDouble(), fwdX) || (th.excludeFromOAL && !th.isAftEnd))
        }
        .maxByOrNull { it.startFromAftMm + it.lengthMm }
}

private fun getAftEndTaper(spec: ShaftSpec): Taper? {
    val aftThread = getAftEndThread(spec)
    val anchors = mutableListOf(0.0)
    if (aftThread != null) {
        anchors += (aftThread.startFromAftMm + aftThread.lengthMm).toDouble()
    }

    return spec.tapers
        .asSequence()
        .filter { tp ->
            tp.lengthMm > END_EPS_MM && anchors.any { a -> near(tp.startFromAftMm.toDouble(), a) }
        }
        .minByOrNull { it.startFromAftMm }
}

private fun getFwdEndTaper(spec: ShaftSpec): Taper? {
    val fwdX = spec.overallLengthMm.toDouble()
    val fwdThread = getFwdEndThread(spec)
    val anchors = mutableListOf(fwdX)
    if (fwdThread != null) {
        anchors += fwdThread.startFromAftMm.toDouble()
    }

    return spec.tapers
        .asSequence()
        .filter { tp ->
            val endX = (tp.startFromAftMm + tp.lengthMm).toDouble()
            tp.lengthMm > END_EPS_MM && anchors.any { a -> near(endX, a) }
        }
        .maxByOrNull { it.startFromAftMm + it.lengthMm }
}


data class FooterConfig(
    val showAftThread: Boolean,
    val showFwdThread: Boolean,
    val showAftTaper: Boolean,
    val showFwdTaper: Boolean,
    val showCompressionNote: Boolean
)
