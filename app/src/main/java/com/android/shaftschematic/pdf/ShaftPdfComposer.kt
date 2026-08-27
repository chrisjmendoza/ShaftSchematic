// File: app/src/main/java/com/android/shaftschematic/pdf/ShaftPdfComposer.kt
@file:Suppress("MemberVisibilityCanBePrivate")

package com.android.shaftschematic.pdf

import android.graphics.*
import android.graphics.pdf.PdfDocument
import com.android.shaftschematic.geom.DimensionRailLayout
import com.android.shaftschematic.geom.END_EPS_MM
import com.android.shaftschematic.geom.KeywaySilhouetteNotch
import com.android.shaftschematic.geom.KeywaySilhouettePoint
import com.android.shaftschematic.geom.PROFILE_TAPER_MIN_FRAC_OF_TRUE
import com.android.shaftschematic.geom.SCHEMATIC_MIN_BODY_RUN_PT
import com.android.shaftschematic.geom.SCHEMATIC_MIN_LINER_PT
import com.android.shaftschematic.geom.SCHEMATIC_MIN_THREAD_PT
import com.android.shaftschematic.geom.ProfileFeatureSpan
import com.android.shaftschematic.geom.bodyKeywayProtectedSpansMm
import com.android.shaftschematic.geom.keywayPinnedBodySpans
import com.android.shaftschematic.geom.profileFeatureSpans
import com.android.shaftschematic.geom.defaultVisualScale
import com.android.shaftschematic.geom.buildCompressedProfileXMap
import com.android.shaftschematic.geom.exaggeratedProfileScale
import com.android.shaftschematic.geom.solveMaxProfileScale
import com.android.shaftschematic.geom.computeOalWindow
import com.android.shaftschematic.geom.computeSetPositionsInMeasureSpace
import com.android.shaftschematic.geom.fillPolygonMm
import com.android.shaftschematic.geom.floorMm
import com.android.shaftschematic.geom.keywaySilhouetteNotch
import com.android.shaftschematic.geom.keywaySpoonBowl
import com.android.shaftschematic.geom.wallEndMm
import com.android.shaftschematic.geom.wallStartMm
import com.android.shaftschematic.geom.yFor
import com.android.shaftschematic.model.*
import com.android.shaftschematic.pdf.dim.*
import com.android.shaftschematic.pdf.notes.*
import com.android.shaftschematic.pdf.render.PdfDimensionRenderer
import com.android.shaftschematic.settings.PDF_SBREAK_THRESHOLD_DEFAULT
import com.android.shaftschematic.settings.PdfPrefs
import com.android.shaftschematic.ui.drawing.render.HIDDEN_DASH_OFF
import com.android.shaftschematic.ui.drawing.render.HIDDEN_DASH_ON
import com.android.shaftschematic.geom.MIN_BLEND_WIDTH_PT
import com.android.shaftschematic.geom.MIN_KEYWAY_WIDTH_PT
import com.android.shaftschematic.geom.drawnKeywayHalfWidthPx
import com.android.shaftschematic.geom.minKeywaySlotLenPx
import com.android.shaftschematic.geom.SEAL_DASH_OFF_PT
import com.android.shaftschematic.geom.SEAL_DASH_ON_PT
import com.android.shaftschematic.geom.ShoulderDrawSpec
import com.android.shaftschematic.geom.linerTopSilhouette
import com.android.shaftschematic.geom.shoulderDrawSpec
import com.android.shaftschematic.ui.resolved.BodyBlend
import com.android.shaftschematic.ui.resolved.BodyEdgePoint
import com.android.shaftschematic.ui.resolved.ResolvedBody
import com.android.shaftschematic.ui.resolved.bodyBlends
import com.android.shaftschematic.ui.resolved.bodyDrawEdges
import com.android.shaftschematic.ui.resolved.ResolvedComponent
import com.android.shaftschematic.ui.resolved.ResolvedComponentSource
import com.android.shaftschematic.ui.resolved.resolvedBodyBaseId
import com.android.shaftschematic.settings.PdfTieringMode
import com.android.shaftschematic.util.DisplayUnits
import com.android.shaftschematic.util.wrapRichLines
import com.android.shaftschematic.util.DualUnitLayout
import com.android.shaftschematic.util.UnitSystem
import com.android.shaftschematic.util.VerboseLog
import com.android.shaftschematic.util.autoTaperRateText
import com.android.shaftschematic.util.buildBodyTitleById
import com.android.shaftschematic.util.buildLinerTitleById
import com.android.shaftschematic.util.buildTaperTitleById
import com.android.shaftschematic.util.buildThreadTitleById
import com.android.shaftschematic.util.drawRichText
import com.android.shaftschematic.util.measureRichText
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
    /**
     * "Shaft height" slider — the same per-job multiplier the runout/consolidated sheets
     * use (`RunoutConfig.heightScale`): exaggerate or shrink the drawn shaft, hard-capped
     * at PROFILE_MAX_SHAFT_HEIGHT_PT on paper and by the page budget (`exaggeratedProfileScale`).
     */
    heightScale: Float = 1.0f,
    /**
     * "Liner compression" control — the per-job liner width floor as a fraction of true
     * drawn width (`RunoutConfig.linerMinFracOfTrue`): 0 = liners may foreshorten to the
     * writable floor (default), 1 = liners ask for full true-scale width. Best-effort:
     * the drawn height takes precedence — the raise never enters the scale solve and
     * λ-fits the room the page has (`fracFitFactor`).
     */
    linerMinFracOfTrue: Float = 0f,
    /**
     * Per-component display-unit resolution plus the sheet-wide inline-dual flag. Defaults
     * to a single-unit resolver with no overrides, which reproduces today's output exactly:
     * [DisplayUnits.unitFor] returns [unit] for every component and every `*Dual` formatter
     * collapses to its single-unit form.
     */
    displayUnits: DisplayUnits = DisplayUnits.single(unit),
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

    // Blank-draft (write-in) mode: keep drawing + layout, blank every value. The footer band
    // grows and its lines space out because handwriting is larger than printed text.
    val blank = effectiveOptions.blankValues
    val footerBlockPt = if (blank) FOOTER_BLOCK_BLANK_PT else FOOTER_BLOCK_PT

    VerboseLog.d(VerboseLog.Category.PDF, "ShaftPdf") {
        "compose start: page=${page.info.pageWidth}x${page.info.pageHeight}pt filename=$filename unit=$unit oalMm=${"%.3f".format(spec.overallLengthMm)}" +
            " parts(bodies=${spec.bodies.size}, tapers=${spec.tapers.size}, threads=${spec.threads.size}, liners=${spec.liners.size})"
    }

    val geomRect = RectF(
        PAGE_MARGIN_PT,
        PAGE_MARGIN_PT + TOP_TEXT_PAD_PT,
        pageW - PAGE_MARGIN_PT,
        pageH - PAGE_MARGIN_PT - footerBlockPt
    )

    val resolvedBodies = resolvedComponents
        ?.filterIsInstance<ResolvedBody>()
        ?.map { b -> spec.bodyForPdf(b) }

    val bodiesForPdf = resolvedBodies ?: spec.bodies
    // Blends need the resolved neighbours to know what diameter each face steps to; without
    // a resolve pass there is nothing to blend against, so the faces simply stay square.
    val blendsForPdf = resolvedComponents?.let { bodyBlends(spec, it) } ?: emptyList()

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

    // ── Visual diameter scale + compressed x mapping (hand-sheet convention) ──
    // On-device rule (with rulered reference sketches): drawn shaft height follows TRUE
    // diameter on the default sizing curve (`defaultShaftHeightPt`; standard 4" → 0.75",
    // 8" → 1.25", anchor heights user-adjustable) and is never diluted by shaft length.
    // The x axis is schematic: every feature keeps a
    // per-kind minimum drawn width and foreshortens above it in proportion to true length
    // (geom/ProfileCompression.kt); plain body runs absorb the squeeze with center-breaks.
    // Shafts that fit at the visual scale keep a plain linear map.
    val maxDiaMm = spec.maxOuterDiaMm().coerceAtLeast(1f)
    // ONE shared structure (`geom/ProfileFeatureSpans.kt`): the schematic differs from the
    // runout/consolidated sheet and the UI estimator only by its LEAN floors — its values
    // live on rails and callouts, so proportion wins over write-in room here.
    val featureSpans: List<ProfileFeatureSpan> = profileFeatureSpans(
        spec,
        linerFloorPt = SCHEMATIC_MIN_LINER_PT,
        threadFloorPt = SCHEMATIC_MIN_THREAD_PT,
        linerMinFracOfTrue = linerMinFracOfTrue,
    )
    // Pinned spans (tapers, keyway bodies) demand true width → when one needs the room,
    // the HEIGHT yields ("doesn't have to be perfectly proportional, just close" —
    // on-device rule). The per-job "Shaft height" slider multiplies the default sizing
    // curve (standard: proportional, 8" → 1.125"; anchor heights user-adjustable via
    // Settings → Drawing); the absolute ceiling and the page budget cap the result
    // (exaggeratedProfileScale, pure, unit-tested).
    val desiredScale = exaggeratedProfileScale(
        baseScale = defaultVisualScale(maxDiaMm, pdfPrefs.curveLoHeightPt, pdfPrefs.curveHiHeightPt),
        heightFrac = heightScale,
        budgetCapPt = geomRect.height(),
        maxDiaMm = maxDiaMm,
    )
    val diaPtPerMm = solveMaxProfileScale(
        windowStartMm = contentMinMm, windowEndMm = contentMaxMm,
        features = featureSpans, contentWidth = geomRect.width(), scaleHi = desiredScale,
        gapMinWidthPt = SCHEMATIC_MIN_BODY_RUN_PT,
    ).coerceAtLeast(1e-6f)
    val xMap = buildCompressedProfileXMap(
        windowStartMm = contentMinMm, windowEndMm = contentMaxMm,
        features = featureSpans,
        contentLeft = geomRect.left, contentRight = geomRect.right,
        diaPtPerMm = diaPtPerMm,
        gapMinWidthPt = SCHEMATIC_MIN_BODY_RUN_PT,
    )

    val winDbg = computeOalWindow(spec)
    VerboseLog.d(VerboseLog.Category.PDF, "ShaftPdf") {
        "layout: geomRect=${geomRect.width().toInt()}x${geomRect.height().toInt()}pt diaPtPerMm=${"%.4f".format(diaPtPerMm)} span=${"%.1f".format(contentSpanMm)}mm maxDiaMm=${"%.3f".format(spec.maxOuterDiaMm())}" +
            " oalWindow(start=${"%.3f".format(winDbg.measureStartMm)}, end=${"%.3f".format(winDbg.measureEndMm)}, oal=${"%.3f".format(winDbg.oalMm)})"
    }

    val halfHeightPx = (maxDiaMm * 0.5f) * diaPtPerMm
    // Place the shaft centered on the paper when possible.
    // Clamp so the shaft stays inside geomRect and the footer block can still fit below.
    val minCy = geomRect.top + halfHeightPx
    val maxCy = min(
        geomRect.bottom - halfHeightPx,
        pageH - PAGE_MARGIN_PT - footerBlockPt - INFO_GAP_PT - halfHeightPx
    )
    val desiredCy = pageH * 0.5f + SHAFT_DOWN_PT
    val cy = if (minCy <= maxCy) desiredCy.coerceIn(minCy, maxCy) else (geomRect.top + geomRect.bottom) * 0.5f
    val yTopOfShaft = cy - halfHeightPx

    val pageDrawableHeightPx = geomRect.height()
    val shaftBoundsHeightPx = maxDiaMm * diaPtPerMm
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

    fun xAt(mm: Float) = xMap.xAt(mm)
    fun rPx(d: Float)  = (d * 0.5f) * diaPtPerMm

    c.save()
    if (shaftTranslateY != 0f) {
        c.translate(0f, shaftTranslateY)
    }
    fun shadeFill() = Paint().apply { style = Paint.Style.FILL; color = Color.argb(40, 0, 0, 0) }
    val bodyFill:  Paint? = if (pdfPrefs.shadedBodies)  shadeFill() else null
    val taperFill: Paint? = if (pdfPrefs.shadedTapers) shadeFill() else null
    val linerFill: Paint? = if (pdfPrefs.shadedLiners)  shadeFill() else null

    // One body path: plain rectangles when a body draws at true scale under the break
    // threshold, the center-break pair when foreshortened by the compressed mapping or
    // traditionally long — body-only shafts included (a long body-only shaft compresses
    // too under the visual-scale rule).
    drawBodyRunsWithBreaks(
        c, bodiesForPdf, cy, ::xAt, ::rPx, outline, geomRect, bodyFill,
        truePtPerMm = diaPtPerMm,
        breakMinFracOfTrue = pdfPrefs.sBreakThresholdFrac,
        blends = blendsForPdf,
        keywayAvoidSpansMm = bodyKeywayProtectedSpansMm(spec),
    )
    // Keyway clocking: the aft-most keyway (measurement datum) always draws face-on; every other
    // host is a secondary. At 180° a secondary renders hidden (dashed, no fill); at 90° it renders
    // as a notch cut into a silhouette edge. Mirrors the canvas renderer.
    val clocking = spec.keywayClocking()
    val hiddenKeywayIds = spec.hiddenKeywayHostIds()
    val secondaryKeywayIds = spec.secondaryKeywayHostIds()
    // Body keyways — drawn from model bodies (resolved fragments and center-break
    // compression keep true end faces, so the slot lands at its physical position).
    drawBodyKeywaysPdf(
        c, spec.bodies, ::xAt, cy, diaPtPerMm, outline,
        clocking, hiddenKeywayIds, secondaryKeywayIds,
    )
    drawTapers(
        c, spec.tapers, cy, ::xAt, ::rPx, outline, taperFill,
        hiddenKeywayIds, clocking, secondaryKeywayIds, diaPtPerMm,
    )
    drawThreads(c, spec.threads, cy, ::xAt, ::rPx, outline, dim, diaPtPerMm)
    drawLiners(c, spec.liners, cy, ::xAt, ::rPx, outline, dim, linerFill)
    drawCouplerBoltSlots(c, spec.couplerBoltSlots, spec, cy, ::xAt, ::rPx, outline, shadeFill(), bodies = bodiesForPdf)
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
        val startRailGap = LANE_GAP_PT + 6f
        val startDimTextSize = TEXT_PT - 2f
        val minDimTextSize = 7f
        val dimText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            textSize = startDimTextSize
            color = 0xFF000000.toInt()
        }

        // Measurement reference (AFT/FWD/AUTO) is separate from tier origin.
        val measureFromMode = pdfPrefs.tieringMode
        val linerDims = mapToLinerDimsForPdf(spec, measureFromMode)
        val win  = computeOalWindow(spec)
        val pageX: (Double) -> Float = { dimMm ->
            xAt((dimMm + win.measureStartMm).toFloat())
        }
        val sets = computeSetPositionsInMeasureSpace(win, spec)
        val spans = buildLinerSpans(
            liners = linerDims,
            sets = sets,
            unit = unit,
            measureFrom = measureFromMode,
            displayUnits = displayUnits,
        ) + buildTaperLengthSpans(spec, win, unit, displayUnits)
        val planner = RailPlanner()
        val tierOriginMm = tierOriginMmFor(pdfPrefs.tieringMode, win.oalMm)
        val assignments = planner.assignAll(spans, tierOriginMm)

        val maxRail = assignments.maxOfOrNull { it.rail } ?: 0

        // Stacked dual values are a whole-SHEET choice, taken here and either honoured below or
        // given up on entirely (`docs/DualUnitStacking_PLAN.md` §7): a sheet with some two-line and
        // some one-line values reads as a mistake, so the fallback is per sheet, never per label.
        val wantStacked = displayUnits.dual && pdfPrefs.dualUnitLayout == DualUnitLayout.STACKED
        fun railRenderer(stacked: Boolean) = PdfDimensionRenderer(
            pageX = pageX,
            linePaint = dim,
            textPaint = dimText,
            objectTopY = yTopOfShaft,
            objectClearance = 6f,
            arrowSize = pdfPrefs.arrowSizePt,
            blankLabels = blank,
            blankLabelWidthPx = BLANK_DIM_GAP_PT,
            blankLabelMinWidthPx = BLANK_DIM_GAP_MIN_PT,
            dualStacked = stacked,
        )

        val oalAft = if (spec.threads.any { t ->
            abs(t.startFromAftMm.toDouble()) <= END_EPS_MM && !t.excludeFromOAL
        }) 0.0 else sets.aftSETxMm
        val oalFwd = if (spec.threads.any { t ->
            abs((t.startFromAftMm + t.lengthMm).toDouble() - spec.overallLengthMm.toDouble()) <= END_EPS_MM && !t.excludeFromOAL
        }) win.oalMm else sets.fwdSETxMm
        val oalDimSpan = oalSpan(
            oalAft, oalFwd, displayUnits.documentUnit,
            labelMm = spec.overallLengthMm.toDouble(), dual = displayUnits.dual,
        )

        // Planner rows, OAL topmost. Rail y values here are UNLIFTED; the plan returns the
        // lifted line positions.
        fun rows(r: PdfDimensionRenderer, gap: Float, unliftedTopY: Float) =
            assignments.map { r.spanInput(it.rail, baseY - gap * it.rail, it.span) } +
                r.spanInput(DimensionRailLayout.TOP_RAIL, unliftedTopY, oalDimSpan)

        // The OAL lane is the topmost measurement but rides exactly ONE regular tier
        // pitch above the highest component tier (on-device report: a wider gap wastes
        // whitespace); the planner's lift adds a label band only when the tier below
        // floats a label into the lane.
        fun computeTopY(gap: Float): Float =
            baseY - gap * (maxRail + 1f)

        val contentTopY = geomRect.top + topSafePad

        // Shrinks the lane pitch, then the text, until the lifted rail block clears the content
        // top — and reports whether it got there. A span too short to seat its value in the line
        // prints it ABOVE the line, in the next rail's band, so every rail above lifts by one
        // label band; inline-vs-above is decided from x-geometry alone, so the lift is known
        // before the lane budget is fixed.
        //
        // The lane floor is METRICS-DERIVED when values are stacked: a lane narrower than the
        // value box lets the neighbouring rail's line print through the stack, and no amount of
        // horizontal sliding can fix that. Single-line sheets keep the historical flat 10 pt
        // floor, so nothing about a non-dual sheet moves.
        fun fitRails(stacked: Boolean): RailFit {
            dimText.textSize = startDimTextSize
            val r = railRenderer(stacked)
            var gap = startRailGap
            var textSize = startDimTextSize
            fun minGap(): Float =
                if (!stacked) 10f
                else max(10f, r.labelHeight() + 2f * DimensionRailLayout.LINE_HALF_CLEAR + 2f)
            fun topYFor(g: Float): Float = computeTopY(g) - r.topLift(rows(r, g, 0f))
            var topY = topYFor(gap)
            repeat(12) {
                if (topY >= contentTopY) return@repeat
                if (gap > minGap()) {
                    gap = maxOf(gap - 2f, minGap())
                } else if (textSize > minDimTextSize) {
                    textSize = maxOf(textSize - 1f, minDimTextSize)
                    dimText.textSize = textSize
                } else {
                    return@repeat
                }
                topY = topYFor(gap)
            }
            return RailFit(gap, textSize, topY, fits = topY >= contentTopY)
        }

        var fit = fitRails(wantStacked)
        var stacked = wantStacked
        if (wantStacked && !fit.fits) {
            // §7 degradation: the taller stack cannot be made to fit even at the smallest lane and
            // the 7 pt text floor, so the WHOLE sheet reverts to the inline rendering rather than
            // print a rail block that runs down into the drawing.
            val inlineFit = fitRails(false)
            if (inlineFit.fits || inlineFit.topY > fit.topY) {
                fit = inlineFit
                stacked = false
                VerboseLog.i(VerboseLog.Category.PDF, "ShaftPdf") {
                    "dual stacking: schematic rails fell back to INLINE — the stacked block " +
                        "did not fit even at the smallest lane and text"
                }
            }
        }
        val renderer = railRenderer(stacked)
        dimText.textSize = fit.textSize
        val railGap = fit.railGap

        // Final clamp after the fit — the OAL rail lands at topY once the lift is applied.
        val topLift = renderer.topLift(rows(renderer, railGap, 0f))
        val topY = max(computeTopY(railGap) - topLift, contentTopY)

        val plan = renderer.plan(rows(renderer, railGap, topY + topLift), safeTopY = contentTopY)
        assignments.forEachIndexed { i, rs ->
            renderer.drawPlanned(c, rs.span, plan.placements[i], true)
        }
        renderer.drawPlanned(c, oalDimSpan, plan.placements.last(), true)
    }

    // --- Ø callouts below the shaft: one leader per unique body OD and per unique liner OD ---
    // A blank draft may elect the whole pass out (`blankDiaCallouts`) so the shaft prints
    // clear for freehand annotation instead of carrying write-in rules.
    if (effectiveOptions.showDiaCallouts) {
        val calls = buildBodyOdCallouts(bodiesForPdf, displayUnits) + buildLinerOdCallouts(spec.liners, displayUnits)
        if (calls.isNotEmpty()) {
            val leaderText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                textSize = TEXT_PT - 2f
                color = 0xFF000000.toInt()
            }
            val leader = DiameterLeaderRenderer(
                pageX = { mm -> xAt(mm.toFloat()) },
                shaftTopY = yTopOfShaft,
                shaftBottomY = cy + halfHeightPx,
                linePaint = dim,
                textPaint = leaderText,
                blankValues = blank,
                dualStacked = displayUnits.dual && pdfPrefs.dualUnitLayout == DualUnitLayout.STACKED,
            )
            leader.draw(c, calls)
        }
    }

    if (effectiveOptions.showFooter) {
        // Footer "Body:" diameters — authored bodies as actually drawn. Raw spec.bodies
        // can hold degenerate rows (zero-length, or fully swallowed by body subtraction
        // under a liner/taper) that are invisible in the drawing and the carousel; their
        // Ø must not print in the footer.
        val footerBodyDiasMm = (
            resolvedComponents
                ?.filterIsInstance<ResolvedBody>()
                ?.filter {
                    it.source == ResolvedComponentSource.EXPLICIT &&
                        it.endMmPhysical - it.startMmPhysical > 0f && it.diaMm > 0f
                }
                ?.map { it.diaMm }
                ?: spec.bodies.filter { it.lengthMm > 0f && it.diaMm > 0f }.map { it.diaMm }
            ).distinct().sorted()

        val footerTapers = selectFooterTapers(spec)
        val footerCfg = FooterConfig(
            bodyDiasMm = footerBodyDiasMm,
            showAftThread = hasAftThread(spec),
            showFwdThread = hasFwdThread(spec),
            // Taper rendering is gated by detectEndFeatures(); this flag only controls whether
            // taper details are enabled for the footer at all.
            showAftTaper  = footerTapers.aft != null,
            showFwdTaper  = footerTapers.fwd != null,
        )

        val infoBottom = pageH - PAGE_MARGIN_PT
        val infoTop = footerBandTop(pageH, PAGE_MARGIN_PT, footerBlockPt, cy + halfHeightPx)
        val infoRect = RectF(geomRect.left, infoTop, geomRect.right, infoBottom)

        drawFooter(c, infoRect, spec, unit, project, filename, appVersion, text, footerCfg, blankValues = blank, displayUnits = displayUnits)
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Constants
// ──────────────────────────────────────────────────────────────────────────────

private const val MM_PER_IN = 25.4f

// Target drawing height used by computeDetailPtPerMm (the scaling tests' reference
// solve); the composer sizes shafts from the sizing curve.
private const val BODY_ONLY_TARGET_HEIGHT_PT = 1.25f * 72f

// Strokes / text
private const val OUTLINE_PT_BASE = 1.25f  // 100% default; slider goes to 200%
private const val DIM_PT_BASE = 0.8f       // 100% default; slider goes to 200%
private const val TEXT_PT = 12f

// Layout
private const val PAGE_MARGIN_PT = 36f       // 0.5 in
private const val TOP_TEXT_PAD_PT = 12f
private const val SHAFT_DOWN_PT = 36f        // 0.5 in downward shift (moves footer down too)
private const val BAND_CLEAR_PT = 12f        // breathing room above shaft before first dim line
/**
 * Outcome of one rail-block fit attempt: the lane pitch and text size it settled on, the resulting
 * unlifted top-rail y, and whether that actually cleared the content top.
 *
 * [fits] is what makes the stacked-vs-inline decision possible: the fit loop can exhaust its lane
 * and text shrinks and still not make room, and on a stacked sheet that is the signal to give the
 * stacking up for the whole sheet rather than print into the drawing.
 */
private class RailFit(
    val railGap: Float,
    val textSize: Float,
    val topY: Float,
    val fits: Boolean,
)

private const val BASE_DIM_OFFSET_PT = 24f   // distance from shaft top to first component dim
private const val LANE_GAP_PT = 24f          // spacing between dimension lanes

// Component title labels (PDF only)
private const val COMPONENT_LABEL_OFFSET_PT = 32f
/**
 * Air reserved between the drawing and the footer when the vertical budget is planned — the
 * shaft is placed so at least this much survives once the bottom-anchored footer block is
 * subtracted from the page.
 */
private const val INFO_GAP_PT = 72f          // exactly 1 inch below geometry
/**
 * Hard floor on that air. Only reachable by a shaft too tall for the budget to have honoured
 * [INFO_GAP_PT]; it keeps the block off the drawing and still leaves clearance for
 * [FOOTER_GROWTH_MAX_PT] of upward growth.
 */
private const val INFO_GAP_MIN_PT = 56f

/**
 * Top of the footer band. The block **sits on the bottom margin** and grows upward from it
 * ([drawFooter] lays its rows out from `rect.bottom`), so a page's slack is the air between the
 * drawing and the footer rather than a dead strip below it (on-device direction: "bring the
 * footer down a little bit and make better use of the white space"). A block that needs more
 * room — dual-unit lines, wrapped columns — takes it from that air and moves closer to the
 * shaft; the page's bottom edge never moves. The runout/consolidated sheet has always placed
 * its footer this way, so this is also what makes the two documents agree.
 *
 * [shaftBottomY] only binds in the degenerate case: the shaft placement already reserves
 * [INFO_GAP_PT] measured up from the margin, so the floor here is reachable only when the shaft
 * was too tall for that reservation to hold. [INFO_GAP_MIN_PT] stays clear of
 * [FOOTER_GROWTH_MAX_PT] so even a fully-grown band cannot climb into the drawing.
 */
internal fun footerBandTop(
    pageH: Float,
    marginPt: Float,
    footerBlockPt: Float,
    shaftBottomY: Float,
): Float = max(pageH - marginPt - footerBlockPt, shaftBottomY + INFO_GAP_MIN_PT)
internal const val FOOTER_BLOCK_PT = 96f
// Blank drafts reserve a taller footer band: line spacing opens up for handwriting.
// Sized for the fullest column (taper header + Rate/L.E.T./S.E.T./Length/KW + spooned note
// + Thread = 8 lines) at the blank pitch; drawFooter additionally fit-clamps its pitch to
// the band, so an overloaded column tightens up instead of running off the page.
internal const val FOOTER_BLOCK_BLANK_PT = 200f
private const val FOOTER_LINE_FACTOR = 1.35f
/**
 * Tightest footer line pitch. The fit-clamp may squeeze toward it when wrapped lines make a column
 * tall, but never past it — below this the lines touch and the block stops being readable.
 */
private const val FOOTER_LINE_FACTOR_MIN = 1.12f
/**
 * How far the footer band may grow UPWARD to fit wrapped content, in points.
 *
 * Comfortably inside `INFO_GAP_PT` (72 pt of air between the geometry and the footer), so a footer
 * that grows can never climb into the drawing.
 */
private const val FOOTER_GROWTH_MAX_PT = 48f
// Handwriting pitch: ~2.2 lines of the footer text size (≈ 26 pt on the schematic, ≈ 22 pt
// on the consolidated sheet) — a printed-density 1.35 factor leaves no room to write a value
// between the rules (on-device report).
private const val FOOTER_LINE_FACTOR_BLANK = 2.2f

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

// The "body-only" and "single-taper-only" shaft classifiers lived here to decide whether the
// footer's compression note was worth printing. The note is gone (the S-break says it), and
// nothing else on the sheet branches on shaft shape — every component draws from its own pass.

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

private fun requireFinite(name: String, v: Float): Float {
    if (!v.isFinite()) throw IllegalArgumentException("$name is not finite: $v")
    return v
}

// ──────────────────────────────────────────────────────────────────────────────
// Geometry — bodies with centered long-break compression
// ──────────────────────────────────────────────────────────────────────────────


/**
 * One blended face: the void between the curve and the centreline filled, then the top and
 * bottom curves stroked. Mirrors `ShaftRenderer.drawBlendCurve` — the two draw sites must
 * place the identical curve, so both build it from [bodyDrawEdges]. Shared with
 * `RunoutPdfComposer`'s body pass so the schematic and the runout/consolidated sheets
 * print the same curve from the same points.
 */
internal fun drawBlendCurvePdf(
    c: Canvas,
    curve: List<BodyEdgePoint>,
    cy: Float,
    outline: Paint,
    fill: Paint?,
) {
    if (curve.size < 2) return
    if (fill != null) {
        val path = Path()
        path.moveTo(curve.first().xPx, cy - curve.first().rPx)
        curve.drop(1).forEach { path.lineTo(it.xPx, cy - it.rPx) }
        curve.reversed().forEach { path.lineTo(it.xPx, cy + it.rPx) }
        path.close()
        c.drawPath(path, fill)
    }
    for (i in 1 until curve.size) {
        val a = curve[i - 1]
        val b = curve[i]
        c.drawLine(a.xPx, cy - a.rPx, b.xPx, cy - b.rPx, outline)
        c.drawLine(a.xPx, cy + a.rPx, b.xPx, cy + b.rPx, outline)
    }
}

/**
 * Adds one LOCAL span per end-taper so taper lengths are shown on the diagram.
 *
 * Span endpoints are expressed in measurement-space (rebased by [win.measureStartMm]).
 * Do not classify these as DATUM even if they touch a SET; these are feature↔feature lengths.
 * Each taper's label resolves its own display unit via [displayUnits] (keyed by [Taper.id]).
 */
internal fun buildTaperLengthSpans(
    spec: ShaftSpec,
    win: com.android.shaftschematic.geom.OalWindow,
    unit: UnitSystem,
    displayUnits: DisplayUnits = DisplayUnits.single(unit),
): List<DimSpan> = buildList {
    getAftEndTaper(spec)?.let { tp ->
        val x0 = win.toMeasureX(tp.startFromAftMm.toDouble())
        val x1 = win.toMeasureX((tp.startFromAftMm + tp.lengthMm).toDouble())
        add(
            DimSpan(
                x0,
                x1,
                label = formatLenDimDualLabel(abs(x1 - x0), displayUnits.unitFor(tp.id), displayUnits.dual),
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
                label = formatLenDimDualLabel(abs(x1 - x0), displayUnits.unitFor(tp.id), displayUnits.dual),
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
internal fun tierOriginMmFor(mode: PdfTieringMode, oalMm: Double): Double? = when (mode) {
    PdfTieringMode.AFT -> 0.0
    PdfTieringMode.FWD -> oalMm
    PdfTieringMode.AUTO -> null
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
    hiddenKeywayIds: Set<String> = emptySet(),
    clocking: KeywayClocking = KeywayClocking.NONE,
    secondaryKeywayIds: Set<String> = emptySet(),
    ptPerMm: Float = 1f,
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
            drawTaperKeywayPdf(
                c, t, x0, x1, top0, top1, xAt, cy, ptPerMm, outline,
                clocking, hiddenKeywayIds, secondaryKeywayIds,
            )
        }
    }
}

/**
 * Body keyway pass for a whole sheet: the clocking decision plus the slot (or silhouette
 * notch) draw. The schematic and the runout/consolidated sheet both call this, so a keyway
 * can never print on one sheet and go missing from the other.
 *
 * [bodies] must be the **STORED** bodies. Keyways are authored on stored bodies and
 * [bodyForPdf] carries drawable geometry only — a resolved body's [hasKeyway] is always
 * false, so a resolved list draws nothing at all. Stored spans also keep one slot per
 * authored keyway where a liner trims the run into several drawn pieces, and put the slot at
 * its physical position (fragments and the center-break pair keep true end faces).
 *
 * [diaPtPerMm] is the sheet's DIAMETER scale — every transverse dimension a keyway draws (a
 * silhouette notch's depth, a plan-view slot's width) rides it, so keyways scale with the drawn
 * shaft height rather than with the compressed x map.
 */
internal fun drawBodyKeywaysPdf(
    c: Canvas,
    bodies: List<Body>,
    xAt: (Float) -> Float,
    cy: Float,
    diaPtPerMm: Float,
    outline: Paint,
    clocking: KeywayClocking = KeywayClocking.NONE,
    hiddenKeywayIds: Set<String> = emptySet(),
    secondaryKeywayIds: Set<String> = emptySet(),
) {
    val silhouetteKeyways = clocking == KeywayClocking.DEG_90_CW || clocking == KeywayClocking.DEG_90_CCW
    bodies.filter { it.hasKeyway }.forEach { b ->
        if (silhouetteKeyways && b.id in secondaryKeywayIds) {
            b.keywaySilhouetteNotch(clocking)?.let {
                drawKeywaySilhouetteNotchPdf(c, it, xAt, diaPtPerMm, cy, outline)
            }
        } else {
            drawKeywayNotchBodyPdf(c, b, xAt, cy, diaPtPerMm, outline, hidden = b.id in hiddenKeywayIds)
        }
    }
}

/**
 * One taper's keyway, drawn after its outline — the same clocking decision as
 * [drawBodyKeywaysPdf], shared by the schematic's taper pass and the runout sheet's so the
 * two keyway passes on one sheet cannot disagree about which host is a secondary.
 */
internal fun drawTaperKeywayPdf(
    c: Canvas,
    t: Taper,
    x0: Float, x1: Float, top0: Float, top1: Float,
    xAt: (Float) -> Float,
    cy: Float,
    diaPtPerMm: Float,
    outline: Paint,
    clocking: KeywayClocking = KeywayClocking.NONE,
    hiddenKeywayIds: Set<String> = emptySet(),
    secondaryKeywayIds: Set<String> = emptySet(),
) {
    val silhouetteKeyways = clocking == KeywayClocking.DEG_90_CW || clocking == KeywayClocking.DEG_90_CCW
    if (silhouetteKeyways && t.id in secondaryKeywayIds) {
        t.keywaySilhouetteNotch(clocking)?.let {
            drawKeywaySilhouetteNotchPdf(c, it, xAt, diaPtPerMm, cy, outline)
        }
    } else {
        drawKeywayNotchPdf(c, t, x0, x1, top0, top1, cy, diaPtPerMm, outline, hidden = t.id in hiddenKeywayIds)
    }
}

/**
 * Draw a 90°-clocked secondary keyway as a notch cut into the host's silhouette edge — the true
 * edge-on projection, where the slot's depth is what shows in profile.
 *
 * Geometry (span clamp, floor radii, top/bottom side) comes entirely from
 * `geom/KeywaySilhouetteMath.kt`; this only maps mm → pt. The canvas
 * mirror is `ShaftRenderer.drawKeywaySilhouetteNotch` — the two must stay in lockstep.
 *
 * The void polygon is filled first so it erases the host outline segment crossing the notch
 * (keyways draw after outlines), then the two walls and the floor are stroked. The original
 * surface line is deliberately not stroked — the notch is open at the surface.
 */
private fun drawKeywaySilhouetteNotchPdf(
    c: Canvas,
    notch: KeywaySilhouetteNotch,
    xAt: (Float) -> Float,
    ptPerMm: Float,
    cy: Float,
    outline: Paint,
) {
    fun y(radiusMm: Float) = notch.side.yFor(cy, radiusMm, ptPerMm)

    val path = Path().apply {
        notch.fillPolygonMm().forEachIndexed { i, p ->
            val px = xAt(p.xMm); val py = y(p.radiusMm)
            if (i == 0) moveTo(px, py) else lineTo(px, py)
        }
        close()
    }
    // The keyway is a void — always white, regardless of the host's fill shading.
    val whiteFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    c.drawPath(path, whiteFill)

    fun stroke(seg: Pair<KeywaySilhouettePoint, KeywaySilhouettePoint>) {
        val (a, b) = seg
        c.drawLine(xAt(a.xMm), y(a.radiusMm), xAt(b.xMm), y(b.radiusMm), outline)
    }
    stroke(notch.wallStartMm())
    stroke(notch.floorMm())
    stroke(notch.wallEndMm())
}

internal fun drawKeywayNotchPdf(
    c: Canvas,
    t: Taper,
    x0: Float, x1: Float,
    top0: Float,
    top1: Float,
    cy: Float,
    diaPtPerMm: Float,
    outline: Paint,
    hidden: Boolean = false,
) {
    if (x1 == x0 || t.keywayWidthMm <= 0f) return

    val setAtStart = t.startDiaMm <= t.endDiaMm
    val setX = if (setAtStart) x0 else x1
    val letX = if (setAtStart) x1 else x0
    val dir  = if (letX > setX) 1f else -1f

    // Axial scale from the taper's own pixel span — the compressed map is linear across one
    // taper, so this is its local pt/mm.
    val axialPtPerMm = if (t.lengthMm > 0f) kotlin.math.abs(x1 - x0) / t.lengthMm else 1f
    // The keyway sits at the SET (small) end, so the smaller drawn radius is the host the
    // slot must stay inside.
    val hostRadiusPt = min(cy - top0, cy - top1)

    drawKeywaySlotPdf(
        c, setX, dir, axialPtPerMm, diaPtPerMm, hostRadiusPt,
        t.keywayWidthMm, t.keywayOffsetFromSetMm, t.keywayLengthMm, cy, outline, hidden, t.keywaySpooned,
    )
}

/**
 * Body-hosted keyway (intermediate shafts with fitted couplings). Same plan-view slot
 * as the taper keyway, referenced from the body's AFT or FWD end face.
 */
internal fun drawKeywayNotchBodyPdf(
    c: Canvas,
    b: Body,
    xAt: (Float) -> Float,
    cy: Float,
    diaPtPerMm: Float,
    outline: Paint,
    hidden: Boolean = false,
) {
    if (b.keywayWidthMm <= 0f || b.lengthMm <= 0f) return
    val x0 = xAt(b.startFromAftMm)
    val x1 = xAt(b.startFromAftMm + b.lengthMm)
    if (x1 == x0) return

    val aftRef = b.keywayEnd == LinerAuthoredReference.AFT
    val refX = if (aftRef) x0 else x1
    val farX = if (aftRef) x1 else x0
    val dir  = if (farX > refX) 1f else -1f

    // The slot's AXIAL scale comes from ITS OWN mapped span, not the whole body's average: the
    // keyway window is pinned at true scale while the rest of a long body compresses, so a
    // body-average pt/mm would draw the slot shrunken inside the very window that was
    // pinned to keep it real. Within the pinned window the map is linear, so this is exact;
    // an anchor reconstructed one offset back keeps a floating slot at its mapped position.
    val span = b.keywayAbsSpanMm()
    val axialPtPerMm: Float
    val anchorX: Float
    if (span != null && span.hiMm > span.loMm) {
        val sLo = xAt(span.loMm); val sHi = xAt(span.hiMm)
        axialPtPerMm = kotlin.math.abs(sHi - sLo) / (span.hiMm - span.loMm)
        anchorX = (if (aftRef) sLo else sHi) - dir * b.keywayOffsetFromEndMm * axialPtPerMm
    } else {
        axialPtPerMm = kotlin.math.abs(x1 - x0) / b.lengthMm
        anchorX = refX
    }

    drawKeywaySlotPdf(
        c, anchorX, dir, axialPtPerMm, diaPtPerMm, (b.diaMm * 0.5f) * diaPtPerMm,
        b.keywayWidthMm, b.keywayOffsetFromEndMm, b.keywayLengthMm, cy, outline, hidden, b.keywaySpooned,
    )
}

/**
 * Shared keyway slot geometry. [refX] is the referenced face (SET face for tapers,
 * AFT/FWD end face for bodies); [dir] is +1 when the slot extends rightward from it.
 * offset ≈ 0 = open at the referenced face; > 0 = floating (mill arcs both ends).
 *
 * Two scales, because the sheet has two: the slot's offset and length ride
 * [axialPtPerMm] (the compressed x map's local scale), its WIDTH and mill-arc radius ride
 * [diaPtPerMm] — the same scale as the drawn shaft height, so the slot stays proportional to
 * its host at every "Shaft height" setting. See `geom/KeywaySlotMath.kt` for the floor and the
 * [hostRadiusPt] cap.
 *
 * [hidden] draws the slot as a far-side feature (keyways 180° apart): dashed outline and
 * **no** white void fill (the near surface is unbroken). Dash matches the preview
 * renderer (`HIDDEN_DASH_ON`/`HIDDEN_DASH_OFF`).
 */
private fun drawKeywaySlotPdf(
    c: Canvas,
    refX: Float,
    dir: Float,
    axialPtPerMm: Float,
    diaPtPerMm: Float,
    hostRadiusPt: Float,
    widthMm: Float,
    offsetMm: Float,
    lengthMm: Float,
    cy: Float,
    outline: Paint,
    hidden: Boolean = false,
    spooned: Boolean = false,
) {
    // Two half-widths, one per axis. [halfW] is the AXIAL term and drives every x: the slot's
    // straight run, its arc centres, and the spoon bowl's reach. [halfH] is the TRANSVERSE term
    // off the diameter scale, so the slot's drawn width tracks the drawn shaft height. Every
    // round part is then an ellipse (halfW, halfH) — a true circle at the transverse scale grows
    // axially with the height slider until the spoon bowl eats its own slot (on-device report).
    val halfW       = (widthMm * axialPtPerMm) / 2f
    val halfH       = drawnKeywayHalfWidthPx(
        trueHalfWidthPx = (widthMm * diaPtPerMm) / 2f,
        hostRadiusPx = hostRadiusPt,
        minWidthPx = MIN_KEYWAY_WIDTH_PT,
        strokeWidthPx = outline.strokeWidth,
    )
    // Vertical stretch that turns each circular construction into its ellipse. `drawArc` sweeps a
    // parametric angle, so every angle the bowl math derives survives it untouched.
    val yScale      = if (halfW > 0f) halfH / halfW else 1f
    val isOpen      = offsetMm < 0.01f
    val kwSetX      = refX + dir * offsetMm * axialPtPerMm
    val kwLetX      = kwSetX + dir * maxOf(lengthMm * axialPtPerMm, minKeywaySlotLenPx(halfW, isOpen))

    // Spooned (open keyways only): keep the normal keyway (full-length walls + mill semicircle) and
    // ADD an enlarged circle around the closed (LET) end — the mill end stays as an inner reference
    // line inside the bowl. Floating keyways ignore the flag. Mirrors the canvas renderer.
    val bowl        = if (spooned && isOpen && halfW > 0f) keywaySpoonBowl(kwLetX, dir, halfW) else null
    val bowlRy      = bowl?.radius?.times(yScale) ?: 0f

    val letArcCx    = kwLetX - dir * halfW
    val letArcStart = if (dir > 0) 270f else 90f
    val letOval     = android.graphics.RectF(letArcCx - halfW, cy - halfH, letArcCx + halfW, cy + halfH)

    val setArcCx    = kwSetX + dir * halfW
    val setArcStart = if (dir > 0) 90f else 270f
    val setOval     = android.graphics.RectF(setArcCx - halfW, cy - halfH, setArcCx + halfW, cy + halfH)

    val lineNear  = if (isOpen) kwSetX else setArcCx
    val lineFar   = letArcCx
    val lineLeft  = min(lineNear, lineFar)
    val lineRight = max(lineNear, lineFar)

    // ── White fill (keyway is a void in the material) ──
    // Far-side (hidden) keyways aren't cut into the near surface, so they get no fill.
    // Inset from the SET face by one stroke-width so the taper end-face line keeps
    // its full thickness where it meets the keyway fill.
    if (!hidden) {
        val strokeW   = outline.strokeWidth
        val fillNear  = if (isOpen) kwSetX + dir * strokeW else setArcCx
        val fillLeft  = min(fillNear, letArcCx)
        val fillRight = max(fillNear, letArcCx)
        val whiteFill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.FILL
            color = android.graphics.Color.WHITE
        }
        c.drawRect(fillLeft, cy - halfH, fillRight, cy + halfH, whiteFill)
        c.drawArc(letOval, letArcStart, 180f, false, whiteFill)
        if (!isOpen) c.drawArc(setOval, setArcStart, 180f, false, whiteFill)
        // Spoon bowl void: the enlarged disc around the LET end (its SET-side overlaps the slot).
        if (bowl != null) {
            c.drawOval(
                android.graphics.RectF(
                    bowl.cx - bowl.radius, cy - bowlRy, bowl.cx + bowl.radius, cy + bowlRy,
                ),
                whiteFill,
            )
        }
    }

    // ── Outline strokes on top (dashed for hidden far-side keyways) ──
    val stroke = if (hidden) {
        android.graphics.Paint(outline).apply {
            pathEffect = android.graphics.DashPathEffect(floatArrayOf(HIDDEN_DASH_ON, HIDDEN_DASH_OFF), 0f)
        }
    } else outline
    c.drawLine(lineLeft, cy - halfH, lineRight, cy - halfH, stroke)
    c.drawLine(lineLeft, cy + halfH, lineRight, cy + halfH, stroke)
    // Inner mill semicircle — the standard rounded end (kept as a reference line inside the bowl).
    c.drawArc(letOval, letArcStart, 180f, false, stroke)
    if (!isOpen) c.drawArc(setOval, setArcStart, 180f, false, stroke)
    // Spoon bowl: the enlarged ellipse's major arc around the far side.
    if (bowl != null) {
        val bowlOval = android.graphics.RectF(
            bowl.cx - bowl.radius, cy - bowlRy, bowl.cx + bowl.radius, cy + bowlRy,
        )
        c.drawArc(bowlOval, bowl.arcStartDeg, bowl.arcSweepDeg, false, stroke)
    }
    // Open keyway: shaft face end-line already closes the SET end.
}

internal fun drawThreads(
    c: Canvas,
    threads: List<Threads>,
    cy: Float,
    xAt: (Float) -> Float,
    rPx: (Float) -> Float,
    outline: Paint,
    dim: Paint,
    ptPerMm: Float,
) {
    // ONE hatch convention for every sheet (`drawThreadHatch` + the shared pitch/paint
    // recipe): full-band diagonals at the thread's own pitch, capped 4–18 pt, on a
    // 60%-dim-weight alpha-160 paint. A per-sheet hatch style made the same thread read
    // differently across documents (on-device direction: match them — no sense in
    // different forms with different outputs).
    val hatchPaint = Paint(outline).apply { strokeWidth = dim.strokeWidth * 0.6f; alpha = 160 }
    threads.forEach { th ->
        if (th.lengthMm <= 0f || th.majorDiaMm <= 0f) return@forEach
        val x0 = xAt(th.startFromAftMm); val x1 = xAt(th.startFromAftMm + th.lengthMm)
        val r = rPx(th.majorDiaMm); val top = cy - r; val bot = cy + r

        val pitchPt = ((th.pitchMm.takeIf { it > 0f } ?: 2.5f) * ptPerMm).coerceIn(4f, 18f)
        drawThreadHatch(c, min(x0, x1), max(x0, x1), top, bot, hatchPaint, pitchPt)

        // Envelope on top of the hatch
        c.drawLine(x0, top, x1, top, outline)
        c.drawLine(x0, bot, x1, bot, outline)
        c.drawLine(x0, top, x0, bot, outline)
        c.drawLine(x1, top, x1, bot, outline)
    }
}

/**
 * Draw coupler bolt-slot cutouts on the shaft profile. Each cutout is a circle straddling the
 * shaft outline (half in the shaft, half in the coupling), mirrored top and bottom. Reference
 * feature — no dimension rail in v1.
 */
internal fun drawCouplerBoltSlots(
    c: Canvas,
    slots: List<CouplerBoltSlot>,
    spec: ShaftSpec,
    cy: Float,
    xAt: (Float) -> Float,
    rPx: (Float) -> Float,
    outline: Paint,
    fill: Paint?,
    // The bodies actually drawn on this surface. The main composer draws *resolved* bodies
    // (auto-bodies included); the local surface radius must come from the same list or a
    // slot over an auto-body region falls back to the global max OD at the wrong radius.
    bodies: List<Body> = spec.bodies,
) {
    if (slots.isEmpty()) return
    val fallbackDia = spec.maxOuterDiaMm()

    fun surfaceRadiusPx(xMm: Float): Float {
        var maxDia = 0f
        bodies.forEach {
            if (xMm >= it.startFromAftMm && xMm <= it.startFromAftMm + it.lengthMm) maxDia = max(maxDia, it.diaMm)
        }
        spec.liners.forEach {
            if (xMm >= it.startFromAftMm && xMm <= it.startFromAftMm + it.lengthMm) maxDia = max(maxDia, it.odMm)
        }
        spec.threads.forEach {
            if (xMm >= it.startFromAftMm && xMm <= it.startFromAftMm + it.lengthMm) maxDia = max(maxDia, it.majorDiaMm)
        }
        spec.tapers.forEach {
            val s = it.startFromAftMm; val e = it.startFromAftMm + it.lengthMm
            if (xMm in s..e) {
                val span = e - s
                val t = if (span > 1e-3f) ((xMm - s) / span).coerceIn(0f, 1f) else 0f
                maxDia = max(maxDia, it.startDiaMm + (it.endDiaMm - it.startDiaMm) * t)
            }
        }
        if (maxDia <= 0f) maxDia = fallbackDia
        return rPx(maxDia)
    }

    slots.forEach { slot ->
        val holeR = rPx(slot.holeDiaMm)
        if (holeR <= 0f || slot.count < 1) return@forEach
        for (i in 0 until slot.count) {
            val cxMm = slot.startFromAftMm + i * slot.spacingMm
            val cx = xAt(cxMm)
            val rSurface = surfaceRadiusPx(cxMm)
            floatArrayOf(cy - rSurface, cy + rSurface).forEach { surfY ->
                if (fill != null) c.drawCircle(cx, surfY, holeR, fill)
                c.drawCircle(cx, surfY, holeR, outline)
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

        val aftSpec = linerShoulderSpec(ln, LinerAuthoredReference.AFT, x0, x1, r, xAt, rPx)
        val fwdSpec = linerShoulderSpec(ln, LinerAuthoredReference.FWD, x0, x1, r, xAt, rPx)
        if (aftSpec == null && fwdSpec == null) {
            if (fill != null) c.drawRect(x0, top, x1, bot, fill)
            c.drawLine(x0, top, x1, top, outline)
            c.drawLine(x0, bot, x1, bot, outline)
            c.drawLine(x0, top, x0, bot, dim) // thin end ticks
            c.drawLine(x1, top, x1, bot, dim)
            return@forEach
        }

        // Shouldered: fill and stroke decompose the SAME silhouette (`linerTopSilhouette`),
        // mirrored about the centerline; the step faces ride the point list, so they reach
        // fill and stroke with no extra draw code. End caps keep the thin-tick paint at the
        // reduced OD — the cap IS the shoulder's outer face.
        val pts = linerTopSilhouette(x0, x1, r, aftSpec, fwdSpec)
        if (fill != null) {
            val path = Path()
            path.moveTo(pts.first().xPx, cy - pts.first().rPx)
            pts.drop(1).forEach { path.lineTo(it.xPx, cy - it.rPx) }
            pts.reversed().forEach { path.lineTo(it.xPx, cy + it.rPx) }
            path.close()
            c.drawPath(path, fill)
        }
        for (i in 1 until pts.size) {
            val a = pts[i - 1]; val b = pts[i]
            c.drawLine(a.xPx, cy - a.rPx, b.xPx, cy - b.rPx, outline)
            c.drawLine(a.xPx, cy + a.rPx, b.xPx, cy + b.rPx, outline)
        }
        c.drawLine(x0, cy - pts.first().rPx, x0, cy + pts.first().rPx, dim)
        c.drawLine(x1, cy - pts.last().rPx, x1, cy + pts.last().rPx, dim)
    }
}

/**
 * One end's shoulder in drawn units, or null when that end draws square. The fillet radius is
 * a RADIUS in mm, so it maps through `rPx` at twice its value (`rPx` takes a diameter).
 */
private fun linerShoulderSpec(
    ln: Liner,
    end: LinerAuthoredReference,
    x0: Float,
    x1: Float,
    linerRPx: Float,
    xAt: (Float) -> Float,
    rPx: (Float) -> Float,
): ShoulderDrawSpec? {
    val s = ln.shoulderOn(end) ?: return null
    val trueLenPx = when (end) {
        LinerAuthoredReference.AFT -> abs(xAt(ln.startFromAftMm + s.lenMm) - x0)
        LinerAuthoredReference.FWD -> abs(x1 - xAt(ln.startFromAftMm + ln.lengthMm - s.lenMm))
    }
    return shoulderDrawSpec(
        trueLenPx = trueLenPx,
        runWidthPx = abs(x1 - x0),
        linerRPx = linerRPx,
        shoulderRPx = rPx(s.odMm),
        filletRPx = rPx(s.radiusMm * 2f),
        minWidthPx = MIN_BLEND_WIDTH_PT,
    )
}

// ──────────────────────────────────────────────────────────────────────────────
// Footer (3 columns; center column is work-order info)
// ──────────────────────────────────────────────────────────────────────────────

// Internal (not private): the consolidated runout sheet prints the SAME footer block —
// one footer implementation for both documents, so the spec lines can never drift apart.
internal fun drawFooter(
    c: Canvas,
    rect: RectF,
    spec: ShaftSpec,
    unit: UnitSystem,
    project: ProjectInfo,
    filename: String,
    appVersion: String,
    text: Paint,
    cfg: FooterConfig,
    blankValues: Boolean = false,
    displayUnits: DisplayUnits = DisplayUnits.single(unit),
) {
    val cols = buildFooterEndColumns(spec, unit, cfg, blankValues, displayUnits)

    // The end columns lead with a taper heading; the middle job-info block has no heading of its
    // own, so its writing rules would sit one line proud of the end columns' rules. On a blank
    // draft (every line a rule) that misalignment is what the eye reads first — drop the middle
    // column one line so all three columns share the same set of baselines. Printed footers carry
    // values, not rules, and their reserved band has no room to spare, so they stay flush.
    val midLeadLines = if (
        blankValues && (
            cols.aftLines.firstOrNull() == FOOTER_AFT_TAPER_HEADER ||
                cols.fwdLines.firstOrNull() == FOOTER_FWD_TAPER_HEADER
            )
    ) 1 else 0


    // Column starts. A printed footer weights the band toward the left and middle columns,
    // where the long free text lives (taper specs, customer and vessel names) — the FWD column
    // holds the same short spec lines as AFT and needs no more. A blank draft prints no values
    // at all: every line is a writing rule that runs to its column edge, so an uneven split is
    // read as uneven writing room and the FWD column comes out visibly short (on-device report).
    // Blank drafts therefore split the band into equal thirds.
    val midFrac   = if (blankValues) 1f / 3f else 0.40f
    val rightFrac = if (blankValues) 2f / 3f else 0.76f
    val leftX  = rect.left
    val midX   = rect.left + rect.width() * midFrac
    val rightX = rect.left + rect.width() * rightFrac

    // Column budgets: long free text (customer/vessel names) must never overrun the
    // neighbouring column.
    val colPad = 6f
    val leftMaxW  = midX - leftX - colPad
    val midMaxW   = rightX - midX - colPad
    // The last column has no neighbour to clear, so printed text may run to the band edge; a
    // blank draft pads it like the others so all three rules come out the same length.
    val rightMaxW = rect.right - rightX - (if (blankValues) colPad else 0f)

    // The middle column's content, built as a list so its line count is known BEFORE the pitch is
    // chosen — the end columns already arrive as lists. A `null` value means "label only": on a
    // blank draft it draws a writing rule, on a printed sheet it is simply skipped.
    val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    val midLines: List<String> = if (blankValues) {
        buildList {
            add("Customer:"); add("Vessel:"); add("Job #:"); add("Date:")
            if (cfg.bodyDiasMm.isNotEmpty()) add("Body: Ø")
            keywayClockingFooterNote(spec)?.let { add(it) }
            add("Side:")
        }
    } else {
        buildList {
            add("Customer: ${project.customer}")
            add("Vessel: ${project.vessel}")
            add("Job #: ${project.jobNumber}")
            add("Date: $date")
            if (cfg.bodyDiasMm.isNotEmpty()) {
                // No single component backs this line (it's the distinct set across every body),
                // so it prints in the document unit — the same posture as the OAL rail.
                val label = cfg.bodyDiasMm.joinToString(", ") {
                    "Ø ${formatDiaWithUnitDual(it.toDouble(), displayUnits.documentUnit, displayUnits.dual)}"
                }
                add("Body: $label")
            }
            keywayClockingFooterNote(spec)?.let { add(it) }
        }
    }

    // WRAPPING, not ellipsizing. A dual-unit spec line is roughly twice as wide as a single-unit
    // one, and the old `…` truncation dropped the very figure the sheet exists to carry
    // (on-device sheet, `docs/DualUnitStacking_PLAN.md` §1d). Wrapped rows cost line count, which
    // the pitch below absorbs — and the band grows a little if it must.
    fun wrapCount(lines: List<String>, maxW: Float): Int =
        lines.sumOf { wrapRichLines(it, text, maxW, rich = true).size }
    val wrappedMaxLines = maxOf(
        wrapCount(cols.aftLines, leftMaxW),
        wrapCount(cols.fwdLines, rightMaxW),
        midLeadLines + wrapCount(midLines, midMaxW) + (if (blankValues) 0 else 1),  // +1: Side badge
        1,
    )

    // The band grows UPWARD into the info gap when the wrapped content cannot fit it at the
    // printed pitch — never past [FOOTER_GROWTH_MAX_PT], which is well inside `INFO_GAP_PT`, so
    // the footer can never climb into the drawing.
    val printedPitch = text.textSize * FOOTER_LINE_FACTOR
    val neededH = wrappedMaxLines * printedPitch + 10f
    val bandH = maxOf(rect.height(), minOf(neededH, rect.height() + FOOTER_GROWTH_MAX_PT))
    val top = rect.bottom - bandH + 6f

    // Blank drafts open the line pitch up for handwriting; both modes then fit-clamp to the band
    // so the fullest column tightens instead of running off the page.
    val lh = min(
        text.textSize * (if (blankValues) FOOTER_LINE_FACTOR_BLANK else FOOTER_LINE_FACTOR),
        (bandH - 10f) / wrappedMaxLines,
    ).coerceAtLeast(text.textSize * FOOTER_LINE_FACTOR_MIN)

    // Blank drafts: any line that ends with ":" (or a bare "Ø") is a label whose value gets
    // hand-written — draw a writing rule after it instead of a printed value. The rule runs
    // to the COLUMN edge, not a fixed width: a ~1" rule is too short to hand-write a customer
    // name or a diameter on a clipboard (on-device report).
    //
    // Returns the y AFTER this line, one pitch per WRAPPED row.
    fun drawFooterLine(line: String, x: Float, y: Float, maxW: Float): Float {
        if (blankValues && (line.endsWith(":") || line.endsWith("Ø"))) {
            drawLabelWithRule(c, line, x, y, text, ruleWidth = maxW, maxRight = x + maxW)
            return y + lh
        }
        // Rich: footer spec lines carry the shop fractions ("Length: 12 5/8\"",
        // "KW: 1/4 × 1/8 × 2\"") and must set them the way the rails do.
        var yy = y
        wrapRichLines(line, text, maxW, rich = true).forEach { row ->
            c.drawRichText(row, x, yy, text)
            yy += lh
        }
        return yy
    }

    // AFT (left) — left-aligned at left margin
    run {
        var y = top
        cols.aftLines.forEach { line -> y = drawFooterLine(line, leftX, y, leftMaxW) }
    }

    // Middle (Work order) — left-aligned at 1/3 mark, one line down on a blank draft so its
    // rules line up with the end columns' (see midLeadLines).
    run {
        var y = top + midLeadLines * lh
        // One list, one draw loop, for both modes — the same lines the pitch was measured from
        // above, so what was counted is exactly what prints. Free-text job fields (customer,
        // vessel, job #) WRAP like every other footer line rather than losing their tail.
        midLines.forEach { line -> y = drawFooterLine(line, midX, y, midMaxW) }

        // The Side badge sits below the job block, set larger — a blank draft writes it in on a
        // rule instead, which `midLines` already carries as its own label line.
        if (!blankValues) {
            project.side.printableLabelOrNull()?.let { pos ->
                y += lh * 0.35f
                val posPaint = Paint(text).apply {
                    textSize = text.textSize * 1.20f
                    isFakeBoldText = true
                }
                c.drawText(pos, midX, y, posPaint)
            }
        }
    }

    // FWD (right) — left-aligned at 2/3 mark
    run {
        var y = top
        cols.fwdLines.forEach { line -> y = drawFooterLine(line, rightX, y, rightMaxW) }
    }
}

/**
 * The single keyway-clocking note printed in the footer's middle column, or null when none
 * applies. Only meaningful with ≥ 2 keyways on the shaft — "apart from each other" says nothing
 * about a lone keyway.
 *
 * At most one note ever prints: [keywayClocking] resolves the mutually-exclusive flags into one
 * mode. Shared by the printed and blank-draft footer branches so they can't drift apart.
 */
internal fun keywayClockingFooterNote(spec: ShaftSpec): String? {
    if (spec.keywayCount() < 2) return null
    return when (spec.keywayClocking()) {
        KeywayClocking.DEG_180    -> "Keyways 180° apart"
        KeywayClocking.DEG_90_CW  -> "Keyways 90° apart (CW from aft)"
        KeywayClocking.DEG_90_CCW -> "Keyways 90° apart (CCW from aft)"
        KeywayClocking.NONE       -> null
    }
}

internal data class FooterColumns(
    val aftLines: List<String>,
    val fwdLines: List<String>
)

/**
 * Reader note printed directly under a spooned keyway's footer spec line: the stated KW
 * length runs to the base of the spoon bowl (where the mill cut ends), not to the tip
 * of the spoon.
 */
internal const val SPOONED_KW_NOTE = "KW length to base of spoon (mill end)"

// The footer carries NO compression note. The S-break pair IS the drawing's statement that a
// body run is foreshortened — a line of prose repeating it is redundant (on-device direction),
// and it cost a footer row on exactly the long shafts with the least room to spare.

// Column headings of the footer's end columns. Named because [drawFooter] tests whether a column
// leads with one to decide the middle column's first baseline — a literal there would drift.
internal const val FOOTER_AFT_TAPER_HEADER = "AFT Taper"
internal const val FOOTER_FWD_TAPER_HEADER = "FWD Taper"

/**
 * Builds the exact left/right footer text lines that [drawFooter] will render.
 * Exposed for JVM unit tests so we can validate end-feature detection without
 * depending on Android Canvas/PdfDocument runtime.
 */
internal fun buildFooterEndColumns(
    spec: ShaftSpec,
    unit: UnitSystem,
    cfg: FooterConfig,
    blankValues: Boolean = false,
    displayUnits: DisplayUnits = DisplayUnits.single(unit),
): FooterColumns {
    val ends = detectEndFeatures(spec)
    val taperSides = selectFooterTapers(spec)
    val dual = displayUnits.dual

    // Blank drafts keep every LABEL (so the writer knows what goes where) and drop the value;
    // drawFooter turns the trailing ":" into a writing rule.
    fun line(label: String, value: () -> String): String =
        if (blankValues) label else "$label ${value()}"

    fun taperLines(tp: Taper): List<String> = buildList {
        val ls = letSet(tp)
        val tpUnit = displayUnits.unitFor(tp.id)
        // Rate notation follows the taper's OWN unit like the L.E.T./S.E.T./Length lines
        // beside it — a metric-overridden taper keeps the ratio form ("/ft would clash with
        // mm dimensions"), instead of borrowing the document unit's convention.
        add(line("Rate:") { printedTaperRate(tp.taperRateText.trim().ifEmpty { rate1toN(tp) }, tpUnit) })
        // No face suffix on L.E.T./S.E.T. — the footer column already says which end of the
        // shaft this taper is, and "(FWD)" beside an AFT taper's L.E.T. read as if it were
        // asking about the FWD taper (on-device report).
        add(line("L.E.T.:") { formatDiaWithUnitDual(ls.let.toDouble(), tpUnit, dual) })
        add(line("S.E.T.:") { formatDiaWithUnitDual(ls.set.toDouble(), tpUnit, dual) })
        add(line("Length:") { formatLenWithUnitDual(tp.lengthMm.toDouble(), tpUnit, dual) })
        if (tp.keywayWidthMm > 0f && tp.keywayDepthMm > 0f) {
            val spoon = if (tp.keywaySpooned) " (spooned)" else ""
            // The keyway resolves its OWN unit, falling back to the taper's: a metric keyway on an
            // imperial taper is the common European case, and the rest of this column stays inches.
            val kwUnit = displayUnits.keywayUnitFor(tp.id)
            add(line("KW:") {
                if (tp.keywayLengthMm > 0f) {
                    "${formatLenWithUnitDual(tp.keywayWidthMm.toDouble(), kwUnit, dual)} × ${formatLenWithUnitDual(tp.keywayDepthMm.toDouble(), kwUnit, dual)} × ${formatLenWithUnitDual(tp.keywayLengthMm.toDouble(), kwUnit, dual)}$spoon"
                } else {
                    "${formatLenWithUnitDual(tp.keywayWidthMm.toDouble(), kwUnit, dual)} × ${formatLenWithUnitDual(tp.keywayDepthMm.toDouble(), kwUnit, dual)}$spoon"
                }
            })
            if (tp.keywaySpooned) add(SPOONED_KW_NOTE)
        }
    }

    // Thread spec line: a metric-designated thread (`Threads.metricDesignation`) prints its
    // designation verbatim in place of the dia × pitch term — a metric thread names both in
    // one token, and re-deriving them from majorDiaMm/pitchMm would be redundant and could
    // drift from the authored designation (golden rule: authored text is never re-derived).
    // The length term still resolves the thread's own display unit (its id already carries
    // an mm override when metric — see DisplayUnits/ShaftViewModel). Non-metric threads keep
    // today's dia × TPI-or-mm-pitch × length line, unchanged apart from dual-awareness.
    fun threadLine(th: Threads): String {
        val thUnit = displayUnits.unitFor(th.id)
        return line("Thread:") {
            val designation = th.metricDesignation
            if (designation != null) {
                "$designation × ${formatLenWithUnitDual(th.lengthMm.toDouble(), thUnit, dual)}"
            } else {
                "${formatDiaWithUnitDual(th.majorDiaMm.toDouble(), thUnit, dual)} × ${fmtPitch(th.pitchMm, thUnit)} × ${formatLenWithUnitDual(th.lengthMm.toDouble(), thUnit, dual)}"
            }
        }
    }

    val aft = mutableListOf<String>()
    if (cfg.showAftTaper) {
        taperSides.aft?.let { tp ->
            aft += FOOTER_AFT_TAPER_HEADER
            aft += taperLines(tp)
        }
    }
    if (cfg.showAftThread && ends.aftThread) {
        getAftEndThread(spec)?.let { th -> aft += threadLine(th) }
    }

    val fwd = mutableListOf<String>()
    if (cfg.showFwdTaper) {
        taperSides.fwd?.let { tp ->
            fwd += FOOTER_FWD_TAPER_HEADER
            fwd += taperLines(tp)
        }
    }
    if (cfg.showFwdThread && ends.fwdThread) {
        getFwdEndThread(spec)?.let { th -> fwd += threadLine(th) }
    }

    // Body-hosted keyways (fitted couplings on intermediate shafts): list in the column
    // matching the keyway's physical half of the shaft.
    fun bodyKwLine(b: Body): String {
        val spoon = if (b.keywaySpooned) " (spooned)" else ""
        val kwUnit = displayUnits.keywayUnitFor(b.id)
        return line("Body KW:") {
            "${formatLenWithUnitDual(b.keywayWidthMm.toDouble(), kwUnit, dual)} × " +
                "${formatLenWithUnitDual(b.keywayDepthMm.toDouble(), kwUnit, dual)} × " +
                "${formatLenWithUnitDual(b.keywayLengthMm.toDouble(), kwUnit, dual)}$spoon"
        }
    }
    spec.bodies.filter { it.hasKeyway }.forEach { b ->
        val span = b.keywayAbsSpanMm() ?: return@forEach
        val centerMm = span.centerMm
        val col = if (centerMm <= spec.overallLengthMm * 0.5f) aft else fwd
        col += bodyKwLine(b)
        if (b.keywaySpooned) col += SPOONED_KW_NOTE
    }

    // Liner shoulder edge radii — the radius's ONLY printed value (the drawing shows the
    // step and fillet curve; no leader, by decision). Listed in the column matching the
    // shouldered end's physical half; a sharp corner (radius 0) prints nothing.
    fun shoulderLine(radiusMm: Float, lnUnit: UnitSystem): String =
        line("Liner shoulder R:") { formatLenWithUnitDual(radiusMm.toDouble(), lnUnit, dual) }
    spec.liners.forEach { ln ->
        val lnUnit = displayUnits.unitFor(ln.id)
        ln.shoulderOn(LinerAuthoredReference.AFT)?.takeIf { it.radiusMm > 0f }?.let { s ->
            val col = if (ln.startFromAftMm <= spec.overallLengthMm * 0.5f) aft else fwd
            col += shoulderLine(s.radiusMm, lnUnit)
        }
        ln.shoulderOn(LinerAuthoredReference.FWD)?.takeIf { it.radiusMm > 0f }?.let { s ->
            val endMm = ln.startFromAftMm + ln.lengthMm
            val col = if (endMm <= spec.overallLengthMm * 0.5f) aft else fwd
            col += shoulderLine(s.radiusMm, lnUnit)
        }
    }

    return FooterColumns(aftLines = aft, fwdLines = fwd)
}

// ──────────────────────────────────────────────────────────────────────────────
// Helpers
// ──────────────────────────────────────────────────────────────────────────────

/**
 * One [DiaCallout] per unique body OD, anchored at the center of the longest body with
 * that diameter. All callouts hang BELOW the shaft; horizontally-close labels are stacked
 * onto a second row by the renderer ([DiameterLeaderRenderer] / [DiameterCalloutLayout]),
 * so no side alternation is needed here.
 *
 * [Body.showDiaOnDrawing] is applied BEFORE the grouping, which is what makes the toggle
 * useful: hiding one body of a shared-Ø group does not delete the value from the sheet, it
 * moves the anchor to the longest body of that Ø the user can still point at. Hiding every
 * body carrying a Ø is what drops it.
 *
 * The callout's display unit resolves off the anchor body's id via [displayUnits]. A body
 * from [bodiesForPdf]/`bodyForPdf` may carry a resolved fragment id (`"<id>#2"`, a body split
 * by a liner or taper); [resolvedBodyBaseId] strips that before the lookup, matching the
 * pattern `ShaftSpec.bodyForPdf` already uses for the Ø-visibility flag.
 */
internal fun buildBodyOdCallouts(
    bodies: List<Body>,
    displayUnits: DisplayUnits = DisplayUnits.single(UnitSystem.INCHES),
): List<DiaCallout> =
    bodies
        .filter { it.diaMm > 0f && it.showDiaOnDrawing }
        .groupBy { it.diaMm }
        .entries
        .sortedByDescending { it.key }
        .mapNotNull { (diaMm, group) ->
            val anchor = group.maxByOrNull { it.lengthMm } ?: return@mapNotNull null
            val centerMm = (anchor.startFromAftMm + anchor.lengthMm * 0.5).toDouble()
            DiaCallout(
                xMm = centerMm, valueMm = diaMm.toDouble(), side = LeaderSide.BELOW,
                unit = displayUnits.unitFor(resolvedBodyBaseId(anchor.id)), dual = displayUnits.dual,
            )
        }

/**
 * Liner mirror of [buildBodyOdCallouts]: one [DiaCallout] per unique liner OD, anchored at
 * the center of the longest liner with that OD, all BELOW the shaft. Bodies and liners are
 * separate groups — a liner OD is never deduped against a body OD.
 *
 * [Liner.showDiaOnDrawing] is applied before the grouping, same rule as the body builder.
 * Liners are never fragmented, so the anchor's id resolves directly (no base-id stripping).
 */
internal fun buildLinerOdCallouts(
    liners: List<Liner>,
    displayUnits: DisplayUnits = DisplayUnits.single(UnitSystem.INCHES),
): List<DiaCallout> =
    liners
        .filter { it.odMm > 0f && it.showDiaOnDrawing }
        .groupBy { it.odMm }
        .entries
        .sortedByDescending { it.key }
        .mapNotNull { (odMm, group) ->
            val anchor = group.maxByOrNull { it.lengthMm } ?: return@mapNotNull null
            val centerMm = (anchor.startFromAftMm + anchor.lengthMm * 0.5).toDouble()
            DiaCallout(
                xMm = centerMm, valueMm = odMm.toDouble(), side = LeaderSide.BELOW,
                unit = displayUnits.unitFor(anchor.id), dual = displayUnits.dual,
            )
        }

private data class LetSetResult(val let: Float, val set: Float)

// startDiaMm is always the AFT-facing end of the taper (position = startFromAftMm); the
// large end is the L.E.T. whichever physical face carries it.
private fun letSet(t: Taper): LetSetResult =
    if (t.startDiaMm >= t.endDiaMm)
        LetSetResult(t.startDiaMm, t.endDiaMm)
    else
        LetSetResult(t.endDiaMm, t.startDiaMm)

/**
 * Shop notation for the two most common tapers on inch drawings: 1:12 prints as 1"/ft and
 * 1:16 as 3/4"/ft — the way the shop hand-writes them. Every other rate keeps its ratio form
 * (1:10, 1:20, exact 1:N.NNN, or the user's own manual text). Metric drawings keep the
 * ratio for all rates; inch-per-foot notation would clash with mm dimensions.
 * The fraction is spelled plain n/d — the fraction renderer sets it built-up at the draw
 * site, and FractionText.kt's parse map is the only place a vulgar glyph may appear.
 */
internal fun printedTaperRate(rateText: String, unit: UnitSystem): String = when {
    unit != UnitSystem.INCHES -> rateText
    rateText == "1:12" -> "1\"/ft"
    rateText == "1:16" -> "3/4\"/ft"
    else -> rateText
}

// Delegate to the shared auto-rate formatter so a blank-rate taper prints the
// same snapped/exact text the taper card's Auto mode shows on screen.
private fun rate1toN(t: Taper): String =
    autoTaperRateText(
        lengthMm = t.lengthMm,
        setDiaMm = t.startDiaMm,
        letDiaMm = t.endDiaMm,
        exactDecimals = 3,
    ) ?: "—"

private fun tpiFromPitch(pitchMm: Float): Float = if (pitchMm > 0f) MM_PER_IN / pitchMm else 0f
private fun fmtTpi(tpi: Float): String {
    val i = tpi.toInt()
    return if (abs(tpi - i) < 0.01f) i.toString() else String.format(Locale.US, "%.2f", tpi)
}

/** Pitch callout matching the active unit system: TPI for inches, mm pitch for metric. */
private fun fmtPitch(pitchMm: Float, unit: UnitSystem): String =
    if (unit == UnitSystem.INCHES) "${fmtTpi(tpiFromPitch(pitchMm))} TPI"
    else "${formatLenWithUnit(pitchMm.toDouble(), unit)} pitch"

/**
 * Returns a copy of this spec whose `bodies` are the resolved body segments — subtracted
 * against tapers/liners, split/merged, auto-fill gaps included. Raw spec bodies may
 * legally overlap tapers/liners; only resolution turns them into drawable segments.
 * Returns the spec unchanged when [resolved] is null. Tapers, threads, liners, and
 * coupler bolt slots pass through resolution verbatim, so only bodies are swapped.
 */
internal fun ShaftSpec.withResolvedBodies(resolved: List<ResolvedComponent>?): ShaftSpec {
    if (resolved == null) return this
    return copy(bodies = resolved.filterIsInstance<ResolvedBody>().map { b -> bodyForPdf(b) })
}

/**
 * The drawable [Body] behind a [ResolvedBody] — start/length/Ø as resolved, plus the authored
 * Ø-callout visibility carried over from the spec.
 *
 * Deliberately carries **no keyway fields**: a keyway is authored against a stored body's own
 * end face, and a run trimmed by a liner would otherwise repeat the slot on every drawn piece.
 * So `hasKeyway` is false on everything this returns — anything keyway-driven (the slot pass,
 * the true-width pin) reads the STORED bodies instead. See [drawBodyKeywaysPdf] and
 * [keywayPinnedBodySpans]; filtering a resolved list for keyways silently matches nothing.
 *
 * Fragment ids must be stripped before the lookup: a body split by a liner or taper resolves
 * into several runs (`"<id>#2"`, …), and showing/hiding the body has to cover every one of
 * them. AUTO spans share the single bare-shaft flag ([ShaftSpec.showAutoBodyDia]), matching
 * the single Ø they already share. A resolved id with no stored match (never expected)
 * follows the model default — hidden, the opt-in posture.
 */
internal fun ShaftSpec.bodyForPdf(b: ResolvedBody): Body = Body(
    id = b.id,
    startFromAftMm = b.startMmPhysical,
    lengthMm = b.endMmPhysical - b.startMmPhysical,
    diaMm = b.diaMm,
    showDiaOnDrawing = if (b.source == ResolvedComponentSource.AUTO) {
        showAutoBodyDia
    } else {
        bodies.firstOrNull { it.id == resolvedBodyBaseId(b.id) }?.showDiaOnDrawing ?: false
    },
)

/**
 * Truncates [text] with an ellipsis so it fits within [maxWidth] points. Footer columns sit
 * at fixed x positions; long customer/vessel names must never overrun the next column.
 *
 * [rich] must match how the caller will DRAW the result: a built-up fraction is narrower than
 * its characters inline, so measuring plain and drawing rich clips a line that actually fits.
 * It stays off by default because the free-text footer fields are drawn plain on purpose —
 * a job number like `24/1138` is not a fraction and must never be set as one.
 */
internal fun ellipsizeToWidth(text: String, paint: Paint, maxWidth: Float, rich: Boolean = false): String {
    fun width(s: String) = if (rich) paint.measureRichText(s) else paint.measureText(s)
    if (maxWidth <= 0f || width(text) <= maxWidth) return text
    val ellipsis = "…"
    var end = text.length
    while (end > 0 && width(text.substring(0, end) + ellipsis) > maxWidth) end--
    return text.substring(0, end).trimEnd() + ellipsis
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
internal fun hasAftThread(spec: ShaftSpec): Boolean =
    spec.threads.any { it.startFromAftMm <= 0.5f }    // thread touches AFT end

internal fun hasFwdThread(spec: ShaftSpec): Boolean {
    val oal = spec.overallLengthMm
    return spec.threads.any { (it.startFromAftMm + it.lengthMm) >= (oal - 0.5f) } // thread touches FWD end
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
    /** Distinct body ODs (mm) to print as the "Body:" line; empty hides the line. */
    val bodyDiasMm: List<Float> = emptyList(),
)
