// File: app/src/main/java/com/android/shaftschematic/pdf/ShaftPdfComposer.kt
@file:Suppress("MemberVisibilityCanBePrivate")

package com.android.shaftschematic.pdf

import android.graphics.*
import android.graphics.pdf.PdfDocument
import com.android.shaftschematic.geom.DimensionRailLayout
import com.android.shaftschematic.geom.END_EPS_MM
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
import com.android.shaftschematic.model.*
import com.android.shaftschematic.pdf.dim.*
import com.android.shaftschematic.pdf.notes.*
import com.android.shaftschematic.pdf.render.PdfDimensionRenderer
import com.android.shaftschematic.settings.PDF_SBREAK_THRESHOLD_DEFAULT
import com.android.shaftschematic.settings.PdfPrefs
import com.android.shaftschematic.geom.SEAL_DASH_OFF_PT
import com.android.shaftschematic.geom.SEAL_DASH_ON_PT
import com.android.shaftschematic.ui.resolved.BodyBlend
import com.android.shaftschematic.ui.resolved.BodyEdgePoint
import com.android.shaftschematic.ui.resolved.ResolvedBody
import com.android.shaftschematic.ui.resolved.bodyBlends
import com.android.shaftschematic.ui.resolved.bodyDrawEdges
import com.android.shaftschematic.ui.resolved.ResolvedComponent
import com.android.shaftschematic.ui.resolved.ResolvedComponentSource
import com.android.shaftschematic.ui.resolved.resolvedBodyBaseId
import com.android.shaftschematic.ui.resolved.unshadedAutoBodyRunIds
import com.android.shaftschematic.settings.PdfTieringMode
import com.android.shaftschematic.util.DisplayUnits
import com.android.shaftschematic.util.DualUnitLayout
import com.android.shaftschematic.util.UnitSystem
import com.android.shaftschematic.util.VerboseLog
import com.android.shaftschematic.util.buildBodyTitleById
import com.android.shaftschematic.util.buildLinerTitleById
import com.android.shaftschematic.util.buildTaperTitleById
import com.android.shaftschematic.util.buildThreadTitleById
import com.android.shaftschematic.util.measureRichText
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
    // Antialiased like every other sheet's shade (and like every stroke here): a shade fill now
    // ends on the break glyph's own S curve, and an aliased edge against an antialiased stroke
    // fringes the one place the two must read as a single line.
    fun shadeFill() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(40, 0, 0, 0)
    }
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
        unfilledBodyIds = unshadedAutoBodyRunIds(
            resolvedComponents, pdfPrefs.shadedBodies, pdfPrefs.shadeExplicitBodiesOnly,
        ),
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

    // The global titles switch is the per-component flags' DEFAULT, not a master gate — an
    // explicitly-shown component prints under a global off (see componentLabelSpans). Only the
    // per-sheet option (template mode) drops the pass whole.
    if (effectiveOptions.showLabels) {
        drawComponentLabelsPdf(
            canvas = c,
            spec = spec,
            titlesDefault = pdfPrefs.showComponentTitles,
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

/** One component-name label, as the axial span it centers over. */
internal data class ComponentLabelSpan(val text: String, val startMm: Float, val endMm: Float)

/**
 * The component-name labels a sheet prints, AFT→FWD within each kind.
 *
 * Per-component visibility (`showNameOnDrawing`) is TRI-STATE: `null` follows
 * [titlesDefault] — the global
 * [com.android.shaftschematic.settings.PdfPrefs.showComponentTitles] switch — while an
 * explicit `true`/`false` overrides it for that one component in either direction. Gating the
 * whole pass on the global switch made a freshly checked card toggle print nothing under a
 * global switch turned off long before (on-device report), so the global is a DEFAULT here,
 * not a master gate; only the per-sheet [PdfExportOptions.showLabels] option (template mode)
 * still drops the pass whole at the call site.
 *
 * A hidden component still takes its place in the fallback numbering ("Body #2" stays #2 when
 * #1 is hidden), so turning one label off never renumbers the rest.
 */
internal fun componentLabelSpans(spec: ShaftSpec, titlesDefault: Boolean): List<ComponentLabelSpan> = buildList {
    fun emit(shown: Boolean?, label: String, startMm: Float, lengthMm: Float) {
        if (!(shown ?: titlesDefault)) return
        val trimmed = label.trim()
        if (trimmed.isEmpty()) return
        add(ComponentLabelSpan(trimmed, startMm, startMm + lengthMm))
    }

    val bodyTitleById = buildBodyTitleById(spec)
    spec.bodies.sortedWith(compareBy({ it.startFromAftMm }, { it.id }))
        .forEachIndexed { i, b ->
            emit(b.showNameOnDrawing, bodyTitleById[b.id] ?: "Body #${i + 1}", b.startFromAftMm, b.lengthMm)
        }

    val taperTitleById = buildTaperTitleById(spec)
    spec.tapers.sortedWith(compareBy({ it.startFromAftMm }, { it.id }))
        .forEachIndexed { i, t ->
            emit(t.showNameOnDrawing, taperTitleById[t.id] ?: "Taper #${i + 1}", t.startFromAftMm, t.lengthMm)
        }

    val threadTitleById = buildThreadTitleById(spec)
    spec.threads.sortedWith(compareBy({ it.startFromAftMm }, { it.id }))
        .forEachIndexed { i, th ->
            emit(th.showNameOnDrawing, threadTitleById[th.id] ?: "Thread #${i + 1}", th.startFromAftMm, th.lengthMm)
        }

    val linerTitleById = buildLinerTitleById(spec)
    spec.liners.sortedWith(compareBy({ it.startFromAftMm }, { it.id }))
        .forEachIndexed { i, ln ->
            val label = ln.label?.trim()?.ifEmpty { null } ?: linerTitleById[ln.id] ?: "Liner ${i + 1}"
            emit(ln.showNameOnDrawing, label, ln.startFromAftMm, ln.lengthMm)
        }
}

private fun drawComponentLabelsPdf(
    canvas: Canvas,
    spec: ShaftSpec,
    titlesDefault: Boolean,
    geomRect: RectF,
    cy: Float,
    halfHeightPx: Float,
    xAt: (Float) -> Float,
    textPaint: Paint,
) {
    val spans = componentLabelSpans(spec, titlesDefault)
    if (spans.isEmpty()) return

    val labelPaint = Paint(textPaint).apply {
        textSize = (textSize - 2f).coerceAtLeast(8f)
    }

    val yBottomOfShaft = cy + halfHeightPx
    val baseY    = (yBottomOfShaft + COMPONENT_LABEL_OFFSET_PT).coerceAtMost(geomRect.bottom - 6f)
    val rowStep  = labelPaint.textSize * 1.4f
    val padX     = 3f  // minimum horizontal gap between adjacent labels on the same row

    // Place every label as an x-interval + text, then assign rows.
    data class Entry(val xLeft: Float, val xRight: Float, val text: String)

    val entries = spans.map { span ->
        val cx = (xAt(span.startMm) + xAt(span.endMm)) * 0.5f
        val w  = labelPaint.measureText(span.text)
        val xL = (cx - w * 0.5f).coerceIn(geomRect.left, geomRect.right - w)
        Entry(xL, xL + w, span.text)
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
        val r = rPx(ln.odMm)

        // Shoulder specs, fill and stroke all come from the shared pass
        // (`pdf/LinerShoulderDraw.kt`) the runout/consolidated sheet draws through too.
        val specs = linerShoulderSpecs(ln, x0, x1, r, xAt, rPx)
        if (fill != null) drawLinerFillPdf(c, cy, x0, x1, r, specs, fill)
        drawLinerOutlinePdf(c, cy, x0, x1, r, specs, outline, dim)
    }
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
 *
 * [Body.compressOnDrawing] rides the same fragment-stripped lookup, so every run of a split
 * body keeps its author's decision and [drawBodyRunsWithBreaks] can read it off the run it
 * is drawing. AUTO spans always compress: bare-shaft fill is the give that funds every other
 * span's proportion, so it is never opted out.
 */
internal fun ShaftSpec.bodyForPdf(b: ResolvedBody): Body {
    val stored = if (b.source == ResolvedComponentSource.AUTO) {
        null
    } else {
        bodies.firstOrNull { it.id == resolvedBodyBaseId(b.id) }
    }
    return Body(
        id = b.id,
        startFromAftMm = b.startMmPhysical,
        lengthMm = b.endMmPhysical - b.startMmPhysical,
        diaMm = b.diaMm,
        showDiaOnDrawing = if (b.source == ResolvedComponentSource.AUTO) {
            showAutoBodyDia
        } else {
            stored?.showDiaOnDrawing ?: false
        },
        compressOnDrawing = stored?.compressOnDrawing ?: true,
    )
}

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

