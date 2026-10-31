// File: app/src/main/java/com/android/shaftschematic/pdf/ShaftPdfComposer.kt
@file:Suppress("MemberVisibilityCanBePrivate")

package com.android.shaftschematic.pdf

import android.graphics.*
import android.graphics.pdf.PdfDocument
import com.android.shaftschematic.geom.BelowShaftLabelLayout
import com.android.shaftschematic.geom.DimensionRailLayout
import com.android.shaftschematic.geom.END_EPS_MM
import com.android.shaftschematic.geom.crossBoreLines
import com.android.shaftschematic.ui.drawing.render.HIDDEN_DASH_OFF
import com.android.shaftschematic.ui.drawing.render.HIDDEN_DASH_ON
import com.android.shaftschematic.geom.PROFILE_TAPER_MIN_FRAC_OF_TRUE
import com.android.shaftschematic.geom.SCHEMATIC_MIN_BODY_RUN_PT
import com.android.shaftschematic.geom.SCHEMATIC_MIN_LINER_PT
import com.android.shaftschematic.geom.SCHEMATIC_MIN_THREAD_PT
import com.android.shaftschematic.geom.ProfileFeatureSpan
import com.android.shaftschematic.geom.bodyKeywayProtectedSpansMm
import com.android.shaftschematic.geom.boltHoleCentersX
import com.android.shaftschematic.geom.boltRowProtectedSpansMm
import com.android.shaftschematic.geom.keywayPinnedBodySpans
import com.android.shaftschematic.geom.profileFeatureSpans
import com.android.shaftschematic.geom.defaultVisualScale
import com.android.shaftschematic.geom.buildCompressedProfileXMap
import com.android.shaftschematic.geom.CompressedProfileXMap
import com.android.shaftschematic.geom.exaggeratedProfileScale
import com.android.shaftschematic.geom.solveMaxProfileScale
import com.android.shaftschematic.geom.computeOalWindow
import com.android.shaftschematic.geom.OalWindow
import com.android.shaftschematic.geom.drawnExtentMm
import com.android.shaftschematic.geom.computeSetPositionsInMeasureSpace
import com.android.shaftschematic.geom.oalRailEndsMm
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
import com.android.shaftschematic.ui.resolved.effectiveBodyDimensionRail
import com.android.shaftschematic.ui.drawing.renderSpanSpec
import com.android.shaftschematic.ui.util.BOUNDS_EPS_MM
import com.android.shaftschematic.ui.resolved.ResolvedComponent
import com.android.shaftschematic.ui.resolved.ResolvedComponentSource
import com.android.shaftschematic.ui.resolved.resolvedBodyBaseId
import com.android.shaftschematic.ui.resolved.unshadedBodyRunIds
import com.android.shaftschematic.ui.resolved.unshadedLinerIds
import com.android.shaftschematic.ui.resolved.unshadedTaperIds
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
    heightScale: Float = 1.0f,
    linerMinFracOfTrue: Float = 0f,
    displayUnits: DisplayUnits = DisplayUnits.single(unit),
) = composeShaftPdfOnCanvas(
    c = page.canvas,
    pageW = page.info.pageWidth.toFloat(),
    pageH = page.info.pageHeight.toFloat(),
    spec = spec, unit = unit, project = project, appVersion = appVersion, filename = filename,
    pdfPrefs = pdfPrefs, options = options, resolvedComponents = resolvedComponents,
    lineThicknessScale = lineThicknessScale, heightScale = heightScale,
    linerMinFracOfTrue = linerMinFracOfTrue, displayUnits = displayUnits,
)

/**
 * The whole sheet, drawn onto any [Canvas] of the given page size — the seam a test uses to
 * put a recording canvas under the composer (`SheetTextOverlapTest`) while the app always
 * comes in through [composeShaftPdf] with a real `PdfDocument.Page`. Same parameters, same
 * drawing.
 */
internal fun composeShaftPdfOnCanvas(
    c: Canvas,
    pageW: Float,
    pageH: Float,
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
    /**
     * The vertical band of the page this schematic draws in. `null` = the whole page, drawn exactly
     * as the full-page sheet. The before/after sheet stacks two bands — the original above the
     * final — and only the band that hosts the footer reserves and draws it.
     */
    band: SheetBand? = null,
) {
    val effectiveOptions = effectiveSchematicOptions(options)

    // The band this drawing owns. With no band it is the page inside its margins, so every
    // expression below reduces to the full-page sheet's.
    val bandTop = band?.topPt ?: PAGE_MARGIN_PT
    val bandBottom = band?.bottomPt ?: (pageH - PAGE_MARGIN_PT)
    val hostsFooter = band?.hostsFooter ?: true

    // PDF safety: explicitly paint a white page background so geometry/labels are visible
    // even if the viewer/app is in dark mode (some viewers treat an unpainted page as dark).
    // A band never paints: the sheet that stacks bands paints its page once, and a second band's
    // white would erase the first.
    if (band == null) c.drawColor(Color.WHITE)

    // Blank-draft (write-in) mode: keep drawing + layout, blank every value. The footer band
    // grows and its lines space out because handwriting is larger than printed text.
    val blank = effectiveOptions.blankValues

    val text = sheetTextPaint(TEXT_PT)

    // The band's label, in its own row above the drawing — the footer Side badge's construction.
    band?.badge?.let { badge ->
        c.drawText(badge, PAGE_MARGIN_PT, bandTop + TEXT_PT, sheetTextPaint(TEXT_PT).apply { isFakeBoldText = true })
    }

    // The footer band is reserved at the height the footer will actually PRINT — see
    // [schematicFooterBlockPt]. A band that does not host the footer reserves nothing: its
    // drawing runs down to the band's own bottom edge.
    val footerCfg = if (effectiveOptions.showFooter && hostsFooter) sheetFooterConfig(spec, pdfPrefs) else null
    val footerBlockPt = if (band != null && !hostsFooter) {
        0f
    } else {
        schematicFooterBlockPt(pageW, spec, unit, project, pdfPrefs, options, displayUnits)
    }

    VerboseLog.d(VerboseLog.Category.PDF, "ShaftPdf") {
        "compose start: page=${pageW.toInt()}x${pageH.toInt()}pt filename=$filename unit=$unit oalMm=${"%.3f".format(spec.overallLengthMm)}" +
            " parts(bodies=${spec.bodies.size}, tapers=${spec.tapers.size}, threads=${spec.threads.size}, liners=${spec.liners.size})" +
            (band?.let { " band=${it.topPt}..${it.bottomPt} footer=${it.hostsFooter}" } ?: "")
    }

    val geomRect = RectF(
        PAGE_MARGIN_PT,
        bandTop + (if (band?.badge != null) BAND_BADGE_ROW_PT else TOP_TEXT_PAD_PT),
        pageW - PAGE_MARGIN_PT,
        bandBottom - footerBlockPt
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

    // Total content span: shaft body (0..OAL) plus any excluded end threads that sit
    // outside the OAL range.  AFT excluded threads have startFromAftMm = -lengthMm (negative);
    // FWD excluded threads have startFromAftMm = OAL and extend to OAL + lengthMm.
    // ptPerMm must be derived from this full span so neither end clips the page margin.
    // The ONE drawn-extent rule (`geom/DrawnExtent.kt`) — shared with the runout/consolidated
    // sheet so no composer can start its x map at a datum a drawn thread sits outside of.
    // [schematicProfileFit] maps exactly this span.
    val contentSpanMm = spec.drawnExtentMm().spanMm

    // ── Visual diameter scale + compressed x mapping (hand-sheet convention) ──
    // On-device rule (with rulered reference sketches): drawn shaft height follows TRUE
    // diameter on the default sizing curve (`defaultShaftHeightPt`; standard 4" → 0.75",
    // 8" → 1.25", anchor heights user-adjustable) and is never diluted by shaft length.
    // The x axis is schematic: every feature keeps a
    // per-kind minimum drawn width and foreshortens above it in proportion to true length
    // (geom/ProfileCompression.kt); plain body runs absorb the squeeze with center-breaks.
    // Shafts that fit at the visual scale keep a plain linear map.
    val maxDiaMm = spec.maxOuterDiaMm().coerceAtLeast(1f)
    // A band solves its x map at its budget share's height ([BAND_SHAFT_BUDGET_FRAC]) and then
    // draws the shaft at a scale no larger than the room its rails leave (see
    // [schematicBandLayout]) — the map stays the one the rails were measured on, so a short band
    // cannot narrow the drawing, float more values and outgrow the room it was given. The
    // before/after sheet hands both bands the smaller of their two scales, so a changed largest
    // diameter reads as a changed height.
    val bandLayout = band?.let { b ->
        schematicBandLayout(pageW, spec, unit, pdfPrefs, options, displayUnits, b, footerBlockPt, heightScale, linerMinFracOfTrue)
    }
    val profile = schematicProfileFit(
        spec, pdfPrefs, heightScale, linerMinFracOfTrue,
        contentLeft = geomRect.left, contentRight = geomRect.right,
        budgetCapPt = if (band == null) geomRect.height() else geomRect.height() * BAND_SHAFT_BUDGET_FRAC,
    )
    val diaPtPerMm = if (band != null && bandLayout != null) {
        bandLayout.diaPtPerMm.let { own -> band.diaPtPerMmCap?.let { min(it, own) } ?: own }
    } else {
        profile.diaPtPerMm
    }
    val xMap = profile.xMap

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
        bandBottom - footerBlockPt - (bandLayout?.infoGapPt ?: INFO_GAP_PT) - halfHeightPx
    )
    // A band centres on its own span with the same downward bias — the page's middle is
    // `(margin + (pageH - margin)) / 2`, so a band covering the whole page asks for exactly the
    // full-page position.
    val desiredCy = if (band == null) pageH * 0.5f + SHAFT_DOWN_PT else (bandTop + bandBottom) * 0.5f + SHAFT_DOWN_PT
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
    // The fill paints exist unconditionally; WHICH components use them is one decision per
    // component, made by the unshaded-id builders. A kind's checkbox turned off simply names
    // every component of that kind, so the flags stay the default a per-component override
    // can beat — never a master gate over an authored choice.
    val bodyFill:  Paint = shadeFill()
    val taperFill: Paint = shadeFill()
    val linerFill: Paint = shadeFill()
    val unfilledBodyIds = unshadedBodyRunIds(
        spec, resolvedComponents, pdfPrefs.shadedBodies, pdfPrefs.shadeExplicitBodiesOnly,
    )
    val unfilledTaperIds = unshadedTaperIds(spec, pdfPrefs.shadedTapers)
    val unfilledLinerIds = unshadedLinerIds(spec, pdfPrefs.shadedLiners)

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
        boltRowAvoidSpansMm = boltRowProtectedSpansMm(spec),
        unfilledBodyIds = unfilledBodyIds,
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
        unfilledIds = unfilledTaperIds,
    )
    drawThreads(c, spec.threads, cy, ::xAt, ::rPx, outline, dim)
    drawLiners(c, spec.liners, cy, ::xAt, ::rPx, outline, dim, linerFill, unfilledIds = unfilledLinerIds)
    drawCouplerBoltSlots(c, spec.couplerBoltSlots, spec, cy, ::xAt, ::rPx, outline, shadeFill(), bodies = bodiesForPdf)
    c.restore()

    // --- Ø callouts below the shaft: one leader per unique body OD and per unique liner OD ---
    // A blank draft may elect the whole pass out (`blankDiaCallouts`) so the shaft prints
    // clear for freehand annotation instead of carrying write-in rules.
    //
    // Built HERE, drawn further down: the component-name labels hang in the same band and share
    // ONE collision space with these callouts, so their planner needs the leaders' occupancy
    // before it places anything.
    val diaCalls =
        if (effectiveOptions.showDiaCallouts) {
            buildBodyOdCallouts(bodiesForPdf, displayUnits) + buildLinerOdCallouts(spec.liners, displayUnits)
        } else {
            emptyList()
        }
    val diaLeader = if (diaCalls.isEmpty()) null else DiameterLeaderRenderer(
        pageX = { mm -> xAt(mm.toFloat()) },
        shaftTopY = yTopOfShaft,
        shaftBottomY = cy + halfHeightPx,
        linePaint = dim,
        textPaint = sheetTextPaint(TEXT_PT - 2f),
        blankValues = blank,
        dualStacked = displayUnits.dual && pdfPrefs.dualUnitLayout == DualUnitLayout.STACKED,
        contentLeft = geomRect.left,
        contentRight = geomRect.right,
    )

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
            reserved = diaLeader?.occupancy(diaCalls).orEmpty(),
        )
    }

    if (effectiveOptions.showDimensions) {
        val baseY = yTopOfShaft - BAND_CLEAR_PT - BASE_DIM_OFFSET_PT

        // Fit-to-band safety for dimensional rails (OAL always visible)
        val topSafePad = RAIL_TOP_SAFE_PAD_PT
        val startRailGap = LANE_GAP_PT + 6f
        val startDimTextSize = TEXT_PT - 2f
        val minDimTextSize = 7f
        val dimText = sheetTextPaint(startDimTextSize)

        val railSpans = schematicRailSpans(spec, unit, displayUnits, pdfPrefs)
        val win = railSpans.win
        val pageX: (Double) -> Float = { dimMm ->
            xAt((dimMm + win.measureStartMm).toFloat())
        }
        val assignments = railSpans.assignments

        val maxRail = railSpans.maxRail

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

        val oalDimSpan = railSpans.oalDimSpan

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
                if (!stacked) RAIL_MIN_GAP_PT
                else max(RAIL_MIN_GAP_PT, r.labelHeight() + 2f * DimensionRailLayout.LINE_HALF_CLEAR + 2f)
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

        val plan = renderer.plan(
            rows(renderer, railGap, topY + topLift), safeTopY = contentTopY,
            contentLeft = geomRect.left, contentRight = geomRect.right,
        )
        // One call for the whole plan: every line first, then every value over its halo.
        renderer.drawPlan(c, assignments.map { it.span } + oalDimSpan, plan.placements)
    }

    // The callouts themselves — planned above (the name labels had to see them), inked here.
    diaLeader?.draw(c, diaCalls)

    if (footerCfg != null) {
        val infoBottom = bandBottom
        // `footerBandTop` measures from a page bottom and subtracts the margin itself; handing it
        // the band's bottom plus that margin is the same arithmetic against the band's edge.
        val footerPageBottom = band?.let { it.bottomPt + PAGE_MARGIN_PT } ?: pageH
        val infoTop = footerBandTop(footerPageBottom, PAGE_MARGIN_PT, footerBlockPt, cy + halfHeightPx)
        val infoRect = RectF(geomRect.left, infoTop, geomRect.right, infoBottom)

        drawFooter(c, infoRect, spec, unit, project, filename, appVersion, text, footerCfg, blankValues = blank, displayUnits = displayUnits)
    }

    // Last, in the bottom margin strip: the opt-in "Units in …" note. Page coordinates — it is
    // the page's note, so only the band that hosts the footer prints it.
    if (pdfPrefs.showUnitsNote && hostsFooter) drawUnitsNote(c, pageW, pageH, PAGE_MARGIN_PT, displayUnits.documentUnit)
}

/**
 * One vertical band of a schematic page, in page points: the drawing's top and bottom edges,
 * the bold label printed in the band's own row above the drawing ([badge], `null` = no label row),
 * and whether this band reserves and draws the sheet's footer ([hostsFooter]).
 */
internal data class SheetBand(
    val topPt: Float,
    val bottomPt: Float,
    val badge: String? = null,
    val hostsFooter: Boolean = true,
    /**
     * The largest diameter scale (points per mm) this band may draw at; `null` = the scale this
     * band's own room allows ([schematicBandLayout]). A sheet stacking several bands passes the
     * smallest of their scales to every band, so all of them draw at ONE scale and a shaft whose
     * largest diameter changed reads as a changed height.
     */
    val diaPtPerMmCap: Float? = null,
)

/** Template mode drops the dimensions, the names and the footer; Standard prints [options] as given. */
private fun effectiveSchematicOptions(options: PdfExportOptions): PdfExportOptions = when (options.mode) {
    PdfExportMode.Template -> options.copy(
        showDimensions = false,
        showLabels = false,
        showFooter = false,
    )
    PdfExportMode.Standard -> options
}

/**
 * The height a full-page schematic reserves for its footer band, in points.
 *
 * The band is reserved at the height the footer will actually PRINT (`footerBlockHeightPt`): its
 * mode's default block, or taller when a column wraps or runs to the full eight spec lines.
 * Reserving only the default and letting `drawFooter` grow upward would put the footer into the
 * 1-inch info gap — where the component-name rows and the Ø callouts live — so a fully loaded
 * footer (two tapers with keyways, both threads, a wrapped customer name) would print its
 * "FWD Taper" heading through a liner's name (`SheetTextOverlapTest`). Reserving the measured
 * height keeps the label rows above the footer by construction; the gap stays air.
 *
 * A sheet that prints no footer (Template mode, `showFooter` off) still reserves its mode's
 * default block, so the drawing sits where it always has. The before/after sheet places its
 * divider off this same value, so the divider and the footer it sits above can never disagree.
 */
internal fun schematicFooterBlockPt(
    pageW: Float,
    spec: ShaftSpec,
    unit: UnitSystem,
    project: ProjectInfo,
    pdfPrefs: PdfPrefs,
    options: PdfExportOptions,
    displayUnits: DisplayUnits,
): Float {
    val effectiveOptions = effectiveSchematicOptions(options)
    val blank = effectiveOptions.blankValues
    val footerCfg = if (effectiveOptions.showFooter) sheetFooterConfig(spec, pdfPrefs) else null
    return footerCfg?.let {
        footerBlockHeightPt(
            PAGE_MARGIN_PT, pageW - PAGE_MARGIN_PT, spec, unit, project, sheetTextPaint(TEXT_PT), it,
            blankValues = blank, displayUnits = displayUnits,
        )
    } ?: (if (blank) FOOTER_BLOCK_BLANK_PT else FOOTER_BLOCK_PT)
}

/** The schematic's dimension spans and their rail assignment — pure mm, no page geometry. */
private class SchematicRailSpans(
    val win: OalWindow,
    val assignments: List<RailSpan>,
    val oalDimSpan: DimSpan,
) {
    val maxRail: Int get() = assignments.maxOfOrNull { it.rail } ?: 0
}

/**
 * Builds the schematic's dimension spans — liner, taper, coupler-slot and body-length spans on
 * their planned rails, plus the top OAL span. ONE builder behind the composer's rail pass and the
 * band layout's rail estimate, so the estimate counts exactly the rails that print.
 */
private fun schematicRailSpans(
    spec: ShaftSpec,
    unit: UnitSystem,
    displayUnits: DisplayUnits,
    pdfPrefs: PdfPrefs,
): SchematicRailSpans {
    // Measurement reference (AFT/FWD/AUTO) is separate from tier origin.
    val measureFromMode = pdfPrefs.tieringMode
    val linerDims = mapToLinerDimsForPdf(spec, measureFromMode)
    val win = computeOalWindow(spec)
    val sets = computeSetPositionsInMeasureSpace(win, spec)
    val componentDimSpans = buildLinerSpans(
        liners = linerDims,
        sets = sets,
        unit = unit,
        measureFrom = measureFromMode,
        displayUnits = displayUnits,
    ) + buildTaperLengthSpans(spec, win, unit, displayUnits, measureFromMode) +
        buildCouplerSlotSpans(spec, win, unit, displayUnits)
    val spans = componentDimSpans + buildBodyLengthSpans(spec, win, unit, displayUnits, componentDimSpans)
    val tierOriginMm = tierOriginMmFor(pdfPrefs.tieringMode, win.oalMm)
    val assignments = RailPlanner().assignAll(spans, tierOriginMm)

    val (oalAft, oalFwd) = oalRailEndsMm(spec, sets, win)
    val oalDimSpan = oalSpan(
        oalAft, oalFwd, displayUnits.documentUnit,
        labelMm = spec.overallLengthMm.toDouble(), dual = displayUnits.dual,
    )
    return SchematicRailSpans(win, assignments, oalDimSpan)
}

/**
 * How a [SheetBand]'s height divides below its rails: the drawn shaft height it has room for
 * ([shaftRoomPt]) and the names / Ø callouts band under the shaft ([infoGapPt]), in points — and
 * the diameter scale that room allows ([diaPtPerMm]: the band's own profile scale, capped so its
 * largest diameter fits the room, never below [BAND_MIN_SHAFT_HEIGHT_PT]).
 */
internal class SchematicBandLayout(val shaftRoomPt: Float, val infoGapPt: Float, val diaPtPerMm: Float)

/**
 * Lays a [band] out top to bottom — its badge row, the rail block, the shaft, the names / Ø
 * callouts — and answers how tall the shaft may draw, and at what diameter scale.
 *
 * The rail block is measured at [BAND_RAIL_GAP_PT] lane pitch and the starting value size — its
 * lift from above-line values taken off the band's x map, which the composer solves at
 * [BAND_SHAFT_BUDGET_FRAC] of the band whatever height the shaft then draws at, so the estimate
 * and the sheet see the same spans. The composer's rail fit then settles the real pitch.
 *
 * A band too short for that (a blank draft's tall write-in footer halves what is left) gives way
 * in order: the rails drop to the fit's minimum pitch ([RAIL_MIN_GAP_PT]), then the names band
 * yields from [BAND_INFO_GAP_PT] toward [BAND_INFO_GAP_MIN_PT] (the name planner sets its rows a
 * size smaller there), and the shaft keeps [BAND_MIN_SHAFT_HEIGHT_PT]. A rail block that spills
 * over the band's top would print across the rule into the band above, which is the one outcome
 * this order exists to prevent. Template mode prints no rails, so the rails take nothing there.
 */
internal fun schematicBandLayout(
    pageW: Float,
    spec: ShaftSpec,
    unit: UnitSystem,
    pdfPrefs: PdfPrefs,
    options: PdfExportOptions,
    displayUnits: DisplayUnits,
    band: SheetBand,
    footerBlockPt: Float,
    heightScale: Float,
    linerMinFracOfTrue: Float,
): SchematicBandLayout {
    val effectiveOptions = effectiveSchematicOptions(options)
    val top = band.topPt + (if (band.badge != null) BAND_BADGE_ROW_PT else TOP_TEXT_PAD_PT)
    val bottom = band.bottomPt - footerBlockPt
    val height = bottom - top
    // The band's profile — the x map and scale the composer draws on (see the band branch there).
    val provisional = schematicProfileFit(
        spec, pdfPrefs, heightScale, linerMinFracOfTrue,
        contentLeft = PAGE_MARGIN_PT, contentRight = pageW - PAGE_MARGIN_PT,
        budgetCapPt = height * BAND_SHAFT_BUDGET_FRAC,
    )
    val maxDiaMm = spec.maxOuterDiaMm().coerceAtLeast(1f)
    fun layout(shaftRoomPt: Float, infoGapPt: Float) = SchematicBandLayout(
        shaftRoomPt, infoGapPt,
        diaPtPerMm = min(provisional.diaPtPerMm, max(shaftRoomPt, BAND_MIN_SHAFT_HEIGHT_PT) / maxDiaMm),
    )
    if (!effectiveOptions.showDimensions) {
        return layout(height - BAND_INFO_GAP_PT, BAND_INFO_GAP_PT)
    }
    fun rails(gap: Float) = schematicRailBlockPt(
        spec, unit, displayUnits, pdfPrefs, effectiveOptions.blankValues,
        xAt = provisional.xMap::xAt,
        gap = gap,
    )

    val comfortable = height - rails(BAND_RAIL_GAP_PT) - BAND_INFO_GAP_PT
    if (comfortable >= BAND_MIN_SHAFT_HEIGHT_PT) return layout(comfortable, BAND_INFO_GAP_PT)

    // At the minimum pitch the fit has no give left, so the room keeps a point of slack for it.
    val tightRails = rails(RAIL_MIN_GAP_PT) + 1f
    val infoGap = (height - tightRails - BAND_MIN_SHAFT_HEIGHT_PT).coerceIn(BAND_INFO_GAP_MIN_PT, BAND_INFO_GAP_PT)
    return layout(height - tightRails - infoGap, infoGap)
}

/** The schematic's diameter scale and compressed x map — what [schematicProfileFit] solves. */
private class SchematicProfileFit(val diaPtPerMm: Float, val xMap: CompressedProfileXMap)

/**
 * The schematic's drawn diameter scale and its compressed x map for a content width and a height
 * budget — ONE pipeline behind the composer and the band layout's rail estimate.
 */
private fun schematicProfileFit(
    spec: ShaftSpec,
    pdfPrefs: PdfPrefs,
    heightScale: Float,
    linerMinFracOfTrue: Float,
    contentLeft: Float,
    contentRight: Float,
    budgetCapPt: Float,
): SchematicProfileFit {
    // Total content span: shaft body (0..OAL) plus any excluded end threads that sit outside the
    // OAL range — the ONE drawn-extent rule (`geom/DrawnExtent.kt`).
    val drawnExtent = spec.drawnExtentMm()
    val contentMinMm = drawnExtent.startMm
    val contentMaxMm = drawnExtent.endMm
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
        budgetCapPt = budgetCapPt,
        maxDiaMm = maxDiaMm,
    )
    val diaPtPerMm = solveMaxProfileScale(
        windowStartMm = contentMinMm, windowEndMm = contentMaxMm,
        features = featureSpans, contentWidth = contentRight - contentLeft, scaleHi = desiredScale,
        gapMinWidthPt = SCHEMATIC_MIN_BODY_RUN_PT,
    ).coerceAtLeast(1e-6f)
    val xMap = buildCompressedProfileXMap(
        windowStartMm = contentMinMm, windowEndMm = contentMaxMm,
        features = featureSpans,
        contentLeft = contentLeft, contentRight = contentRight,
        diaPtPerMm = diaPtPerMm,
        gapMinWidthPt = SCHEMATIC_MIN_BODY_RUN_PT,
    )
    return SchematicProfileFit(diaPtPerMm, xMap)
}

/**
 * Height from the content top to the shaft top that the rail block takes at lane pitch [gap] and
 * the starting value size: the safe pad, the clearance above the shaft, one pitch per rail (OAL
 * included) and the lift every above-line value adds — the same terms the composer's rail fit
 * measures, over the x map [xAt].
 */
private fun schematicRailBlockPt(
    spec: ShaftSpec,
    unit: UnitSystem,
    displayUnits: DisplayUnits,
    pdfPrefs: PdfPrefs,
    blank: Boolean,
    xAt: (Float) -> Float,
    gap: Float,
): Float {
    val rails = schematicRailSpans(spec, unit, displayUnits, pdfPrefs)
    val renderer = PdfDimensionRenderer(
        pageX = { dimMm -> xAt((dimMm + rails.win.measureStartMm).toFloat()) },
        linePaint = Paint(),
        textPaint = sheetTextPaint(TEXT_PT - 2f),
        objectTopY = 0f,
        objectClearance = 6f,
        arrowSize = pdfPrefs.arrowSizePt,
        blankLabels = blank,
        blankLabelWidthPx = BLANK_DIM_GAP_PT,
        blankLabelMinWidthPx = BLANK_DIM_GAP_MIN_PT,
        dualStacked = displayUnits.dual && pdfPrefs.dualUnitLayout == DualUnitLayout.STACKED,
    )
    val rows = rails.assignments.map { renderer.spanInput(it.rail, -gap * it.rail, it.span) } +
        renderer.spanInput(DimensionRailLayout.TOP_RAIL, -gap * (rails.maxRail + 1), rails.oalDimSpan)
    return RAIL_TOP_SAFE_PAD_PT + BAND_CLEAR_PT + BASE_DIM_OFFSET_PT +
        gap * (rails.maxRail + 1) + renderer.topLift(rows)
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
/** The page margin every schematic band is inset by — the before/after sheet lays its bands out on it. */
internal const val SCHEMATIC_PAGE_MARGIN_PT = PAGE_MARGIN_PT
private const val TOP_TEXT_PAD_PT = 12f
private const val SHAFT_DOWN_PT = 36f        // 0.5 in downward shift (moves footer down too)
private const val BAND_CLEAR_PT = 12f        // breathing room above shaft before first dim line
/** A labelled [SheetBand]'s badge row: the drawing starts this far below the band's top. */
private const val BAND_BADGE_ROW_PT = 16f
/**
 * The share of a [SheetBand]'s drawing height the shaft itself may take. The rails need ~40 pt
 * above it and the names / Ø callouts band 72 pt below, so a shaft sized to the whole band would
 * leave room for neither. The tunable knob for a crowded band: lower it and the shaft draws
 * smaller, leaving the rails and names more air.
 */
private const val BAND_SHAFT_BUDGET_FRAC = 0.30f
/**
 * The rail lane pitch a [SheetBand] budgets for when it sizes its shaft — tighter than the full
 * page's starting pitch ([LANE_GAP_PT] + 6), which has a whole page of air to spend; the rail fit
 * still opens the pitch up to whatever the band's room allows.
 */
private const val BAND_RAIL_GAP_PT = 14f
/**
 * The names / Ø callouts band under a [SheetBand]'s shaft — two name rows at the starting size
 * (the first [COMPONENT_LABEL_OFFSET_PT] below the shaft, one row pitch more) plus the air that
 * keeps the second row off the footer's first line, which sets right at the band's top. The full
 * page keeps its 1-inch [INFO_GAP_PT]; a half-page band cannot, and the name planner shrinks into
 * further rows down to the band's bottom when a job needs them.
 */
private const val BAND_INFO_GAP_PT = 64f
/**
 * The floor the names / Ø callouts band under a [SheetBand]'s shaft yields to when the band is
 * short: two name rows still fit, the planner setting them a size or two smaller.
 */
private const val BAND_INFO_GAP_MIN_PT = 46f
/** The smallest shaft a [SheetBand] draws: under it, the rails tighten rather than the shaft shrink. */
private const val BAND_MIN_SHAFT_HEIGHT_PT = 18f
/** The rail fit's tightest lane pitch for single-line values. */
private const val RAIL_MIN_GAP_PT = 10f
/** Air the rail block keeps under the content top. */
private const val RAIL_TOP_SAFE_PAD_PT = 6f
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
/** How far the fit loop may shrink a name label before it accepts a least-bad placement. */
private const val COMPONENT_LABEL_MIN_TEXT_PT = 7f
/** Hard stop on stacked label rows, so a pathological sheet cannot plan an unbounded band. */
private const val COMPONENT_LABEL_MAX_ROWS = 8
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

/**
 * Draws the component-name labels in the band under the shaft.
 *
 * [reserved] is everything else already printed there — the Ø callouts' values and leaders. Both
 * passes anchor on a component's CENTER, so a component showing a name and a Ø aimed both strings
 * at the same x and printed one through the other (on-device report); they share ONE collision
 * space now ([BelowShaftLabelLayout]), with that engine's resolution order: slide the name along
 * its own component's span first, drop it to the next row only when no slide fits.
 *
 * The rows stop at the footer band, so a crowded sheet cannot walk labels off the drawing. When
 * even the last row leaves a label with nowhere to go, the whole pass retries a size smaller
 * (narrower text needs fewer rows) down to [COMPONENT_LABEL_MIN_TEXT_PT] — the fit-loop posture
 * the dimension rails already use.
 */
private fun drawComponentLabelsPdf(
    canvas: Canvas,
    spec: ShaftSpec,
    titlesDefault: Boolean,
    geomRect: RectF,
    cy: Float,
    halfHeightPx: Float,
    xAt: (Float) -> Float,
    textPaint: Paint,
    reserved: List<BelowShaftLabelLayout.Box> = emptyList(),
) {
    val spans = componentLabelSpans(spec, titlesDefault)
    if (spans.isEmpty()) return

    val labelPaint = Paint(textPaint).apply {
        textSize = (textSize - 2f).coerceAtLeast(COMPONENT_LABEL_MIN_TEXT_PT)
    }

    val yBottomOfShaft = cy + halfHeightPx
    val baseY = (yBottomOfShaft + COMPONENT_LABEL_OFFSET_PT).coerceAtMost(geomRect.bottom - 6f)

    // Shrink until every label finds a clear spot: narrower text both needs less room of its own
    // and fits more rows above the footer band. The first size that fits wins.
    var plan = planComponentLabels(spans, labelPaint, geomRect, baseY, xAt, reserved)
    while (plan.placements.any { !it.fitted } && plan.textSize > COMPONENT_LABEL_MIN_TEXT_PT) {
        labelPaint.textSize = (plan.textSize - 1f).coerceAtLeast(COMPONENT_LABEL_MIN_TEXT_PT)
        plan = planComponentLabels(spans, labelPaint, geomRect, baseY, xAt, reserved)
    }
    labelPaint.textSize = plan.textSize

    val unfitted = plan.placements.count { !it.fitted }
    if (unfitted > 0) {
        VerboseLog.d(VerboseLog.Category.PDF, "ShaftPdf") {
            "component labels: no clear placement for $unfitted of ${plan.placements.size}" +
                " at ${plan.textSize}pt — least-bad rows used"
        }
    }

    plan.placements.forEachIndexed { i, p ->
        canvas.drawText(spans[i].text, p.left, baseY + p.row * plan.rowStep, labelPaint)
    }
}

/** One trial layout of the component-name labels at a given text size. */
internal class ComponentLabelPlan(
    val placements: List<BelowShaftLabelLayout.Placement>,
    val rowStep: Float,
    val textSize: Float,
)

/**
 * Plans the name labels at [labelPaint]'s current size.
 *
 * A label's slide window is its own component's span, widened to contain the centered position
 * (a short component's name is wider than the component) and clamped to the content rect — a name
 * still reads as its component's from anywhere over it, which is what makes sliding cheaper than
 * a row drop. Rows run from [baseY] down to the footer band; row 0 always exists so a sheet with
 * no vertical room at all still prints its labels where it always did.
 */
internal fun planComponentLabels(
    spans: List<ComponentLabelSpan>,
    labelPaint: Paint,
    geomRect: RectF,
    baseY: Float,
    xAt: (Float) -> Float,
    reserved: List<BelowShaftLabelLayout.Box>,
): ComponentLabelPlan {
    val fm = labelPaint.fontMetrics
    val rowStep = labelPaint.textSize * 1.4f

    val rows = buildList {
        var r = 0
        while (r == 0 || (baseY + r * rowStep + fm.descent <= geomRect.bottom && r < COMPONENT_LABEL_MAX_ROWS)) {
            val baseline = baseY + r * rowStep
            add(BelowShaftLabelLayout.RowBand(top = baseline + fm.ascent, bottom = baseline + fm.descent))
            r++
        }
    }

    val requests = spans.map { span ->
        val x0 = min(xAt(span.startMm), xAt(span.endMm))
        val x1 = max(xAt(span.startMm), xAt(span.endMm))
        val w = labelPaint.measureText(span.text)
        val centered = (x0 + x1) * 0.5f - w * 0.5f
        val preferred = centered.coerceIn(geomRect.left, max(geomRect.left, geomRect.right - w))
        BelowShaftLabelLayout.Request(
            width = w,
            preferredLeft = preferred,
            minLeft = min(x0, preferred).coerceAtLeast(geomRect.left),
            maxRight = max(x1, preferred + w).coerceAtMost(geomRect.right),
        )
    }

    return ComponentLabelPlan(
        placements = BelowShaftLabelLayout.place(requests, reserved, rows),
        rowStep = rowStep,
        textSize = labelPaint.textSize,
    )
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
 * The taper spans: one LOCAL length span per END taper, and the liner pair (DATUM from its SET
 * to the near edge + LOCAL length) for every OTHER taper.
 *
 * An end taper's SET is one of its own edges, so its length is the whole story and the span
 * stays LOCAL even though it touches the SET — it is a feature↔feature length, never a datum.
 * A taper that stands off both SETs — a mid-shaft step that brings the Ø down ahead of a liner
 * (on-device request) — is located like a liner: the shop needs where it starts as well as how
 * long it is, so it goes through [mapToMidTaperDimsForPdf] and the ONE liner span builder, and
 * the "Measurement reference" option reaches it exactly as it reaches a liner. The footer's
 * AFT/FWD taper columns are untouched: they still note every taper's rate and diameters.
 *
 * Span endpoints are expressed in measurement-space (rebased by [win.measureStartMm]).
 * Each taper's labels resolve their own display unit via [displayUnits] (keyed by [Taper.id]).
 */
internal fun buildTaperLengthSpans(
    spec: ShaftSpec,
    win: com.android.shaftschematic.geom.OalWindow,
    unit: UnitSystem,
    displayUnits: DisplayUnits = DisplayUnits.single(unit),
    measureFrom: PdfTieringMode = PdfTieringMode.AUTO,
): List<DimSpan> = buildList {
    fun endTaperLength(tp: Taper) {
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
    getAftEndTaper(spec)?.let(::endTaperLength)
    getFwdEndTaper(spec)?.let(::endTaperLength)

    addAll(
        buildLinerSpans(
            liners = mapToMidTaperDimsForPdf(spec, measureFrom),
            sets = computeSetPositionsInMeasureSpace(win, spec),
            unit = unit,
            measureFrom = measureFrom,
            displayUnits = displayUnits,
        )
    )
}

/**
 * The tapers that are NOT end tapers — neither [getAftEndTaper] nor [getFwdEndTaper] claims
 * them — adapted to the liner dimensioning shape ([LinerDim]) by the same rule
 * [mapToLinerDimsForPdf] applies to a liner: SET → near edge, anchored to the nearer SET under
 * AUTO or to the forced SET. Zero-length tapers draw nothing and dimension nothing.
 */
internal fun mapToMidTaperDimsForPdf(spec: ShaftSpec, measureFrom: PdfTieringMode): List<LinerDim> {
    val win = computeOalWindow(spec)
    val sets = computeSetPositionsInMeasureSpace(win, spec)
    val endIds = setOfNotNull(getAftEndTaper(spec)?.id, getFwdEndTaper(spec)?.id)
    return spec.tapers
        .filter { it.id !in endIds && it.lengthMm > END_EPS_MM }
        .map { tp -> setDimForPdf(tp.id, tp.startFromAftMm, tp.lengthMm, win, sets, measureFrom) }
}

/**
 * One LOCAL span per coupler bolt row whose card has "Show dimension rail" on
 * ([CouplerBoltSlot.showDimensionRail]) — the row's authored position: from the shaft END it
 * was measured from (`Measure From` AFT = the AFT face at 0, FWD = the FWD face at OAL — the
 * shaft end, not a SET) to the NEAREST hole center, labelled with the stored distance
 * ([CouplerBoltSlot.authoredCenterMm], the value the card shows and the footer prints). The
 * row's own display unit resolves off its id, like every other span. Shared by the schematic
 * and the consolidated sheet beside [buildTaperLengthSpans], so the planner tiers it like any
 * other span; blank drafts rule the value like every other span. Default OFF contributes
 * nothing, so sheets that never touched the toggle print byte-identically.
 *
 * Draw-only, like the row itself: the span changes no geometry and the slot stays outside
 * OAL, body resolution and collision.
 */
internal fun buildCouplerSlotSpans(
    spec: ShaftSpec,
    win: com.android.shaftschematic.geom.OalWindow,
    unit: UnitSystem,
    displayUnits: DisplayUnits = DisplayUnits.single(unit),
): List<DimSpan> = spec.couplerBoltSlots.mapNotNull { s ->
    if (!s.showDimensionRail || s.count < 1) return@mapNotNull null
    val fromFwd = s.authoredReference == SlotAuthoredReference.FWD
    val faceMm = if (fromFwd) spec.overallLengthMm else 0f
    val centerMm = if (fromFwd) s.centerMmAt(s.count - 1) else s.centerMmAt(0)
    val distanceMm = s.authoredCenterMm(spec.overallLengthMm)
    // A hole centered on the face has nothing to dimension; a negative distance means the
    // OAL is not typed yet (0) and the FWD face is nowhere to measure from.
    if (distanceMm <= 1e-3f) return@mapNotNull null
    DimSpan(
        win.toMeasureX(faceMm.toDouble()),
        win.toMeasureX(centerMm.toDouble()),
        label = formatLenDimDualLabel(distanceMm.toDouble(), displayUnits.unitFor(s.id), displayUnits.dual),
        kind = SpanKind.LOCAL,
    )
}

/**
 * One LOCAL length span per explicit body whose length rail is on
 * ([effectiveBodyDimensionRail] — the card's "Show dimension rail", or while unset the position
 * default: on at a shaft end, off mid-shaft). The span runs over the body's STORED span and is
 * labelled with its stored length in the body's own display unit, the way an end taper's length
 * prints; a rail dimensions the stored span, never a drawn fragment, so a body a liner splits or
 * an S-break foreshortens still gets ONE span across it. Shared by the schematic and the
 * consolidated sheet beside [buildTaperLengthSpans]; blank drafts rule the value like every
 * other span.
 *
 * A span the sheet already prints is not printed twice: a body whose stored span coincides
 * (both ends within the bounds slop) with one of [existing] — the usual case is a liner's datum
 * rail from the FWD SET to a liner butting an end body — is skipped, and so is a body running
 * face to face, whose length is the OAL rail's.
 *
 * Draw-only: the span changes no geometry, and auto spans never get one.
 */
internal fun buildBodyLengthSpans(
    spec: ShaftSpec,
    win: com.android.shaftschematic.geom.OalWindow,
    unit: UnitSystem,
    displayUnits: DisplayUnits = DisplayUnits.single(unit),
    existing: List<DimSpan> = emptyList(),
): List<DimSpan> {
    val eps = BOUNDS_EPS_MM.toDouble()
    val shaftEndMm = spec.renderSpanSpec().overallLengthMm
    fun coincides(lo: Double, hi: Double, s: DimSpan) =
        abs(min(s.x1Mm, s.x2Mm) - lo) <= eps && abs(max(s.x1Mm, s.x2Mm) - hi) <= eps
    return spec.bodies.mapNotNull { b ->
        if (b.lengthMm <= 0f || !effectiveBodyDimensionRail(b, spec)) return@mapNotNull null
        val endMm = b.startFromAftMm + b.lengthMm
        val faceToFace = abs(b.startFromAftMm) <= BOUNDS_EPS_MM &&
            shaftEndMm > 0f && abs(endMm - shaftEndMm) <= BOUNDS_EPS_MM
        if (faceToFace) return@mapNotNull null
        val x0 = win.toMeasureX(b.startFromAftMm.toDouble())
        val x1 = win.toMeasureX(endMm.toDouble())
        if (existing.any { coincides(min(x0, x1), max(x0, x1), it) }) return@mapNotNull null
        DimSpan(
            x0,
            x1,
            label = formatLenDimDualLabel(b.lengthMm.toDouble(), displayUnits.unitFor(b.id), displayUnits.dual),
            kind = SpanKind.LOCAL,
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
    return spec.liners.map { ln -> setDimForPdf(ln.id, ln.startFromAftMm, ln.lengthMm, win, sets, measureFrom) }
}

/**
 * ONE rule for every component dimensioned "SET → near edge, then length" — liners and
 * mid-shaft tapers: edges rebased into measurement space, the anchor forced by the
 * "Measurement reference" option or, under AUTO, the SET nearer to the component. Keyed by the
 * component's own id so its display unit resolves per component.
 */
private fun setDimForPdf(
    id: String,
    startFromAftMm: Float,
    lengthMm: Float,
    win: com.android.shaftschematic.geom.OalWindow,
    sets: com.android.shaftschematic.geom.SetPositions,
    measureFrom: PdfTieringMode,
): LinerDim {
    // Edges in measurement space (AFT→FWD)
    val aftEdge = win.toMeasureX(startFromAftMm.toDouble())
    val fwdEdge = aftEdge + lengthMm.toDouble()
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
    return LinerDim(
        id = id,
        anchor = anchor,
        offsetFromSetMm = offset,
        lengthMm = length
    )
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
    /**
     * Tapers whose shade is suppressed while the rest of [tapers] keeps [fill]
     * (`ui/resolved/unshadedTaperIds` — the kind's checkbox with each taper's own tri-state
     * override applied). Empty fills every taper the same.
     */
    unfilledIds: Set<String> = emptySet(),
) {
    tapers.forEach { t ->
        if (t.lengthMm <= 0f || (t.startDiaMm <= 0f && t.endDiaMm <= 0f)) return@forEach
        val taperFill = if (t.id in unfilledIds) null else fill
        val x0 = requireFinite("taper.x0", xAt(t.startFromAftMm))
        val x1 = requireFinite("taper.x1", xAt(t.startFromAftMm + t.lengthMm))
        val r0 = requireFinite("taper.r0", rPx(t.startDiaMm))
        val r1 = requireFinite("taper.r1", rPx(t.endDiaMm))
        val top0 = requireFinite("taper.top0", cy - r0); val bot0 = requireFinite("taper.bot0", cy + r0)
        val top1 = requireFinite("taper.top1", cy - r1); val bot1 = requireFinite("taper.bot1", cy + r1)

        if (taperFill != null) {
            val path = Path().apply {
                moveTo(x0, top0); lineTo(x1, top1); lineTo(x1, bot1); lineTo(x0, bot0); close()
            }
            c.drawPath(path, taperFill)
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
) {
    // ONE hatch convention for every sheet (`drawThreadHatch` + the shared pitch/paint
    // recipe): full-band slanted strokes spaced and leaned by the thread's own pitch at the
    // app-wide density and slant, on a 60%-dim-weight alpha-160 paint. A per-sheet hatch
    // style made the same thread read differently across documents (on-device direction:
    // match them — no sense in different forms with different outputs).
    // The thread itself — hatch span, envelope and any relief groove — draws through the ONE
    // shared pass every sheet calls (`drawThreadPdf`).
    val hatchPaint = Paint(outline).apply { strokeWidth = dim.strokeWidth * 0.6f; alpha = 160 }
    threads.forEach { th -> drawThreadPdf(c, th, cy, xAt, rPx, outline, hatchPaint) }
}

/**
 * Draw coupler bolt-slot cutouts on the shaft profile. A seam cutout is a circle straddling
 * the shaft outline (half in the shaft, half in the coupling), mirrored top and bottom; a
 * cross-drilled hole ([BoltHoleStyle.CROSS]) is ONE circle on the centerline, the hole as
 * seen on the near surface — or, clocked 90° from the keyway, a HIDDEN bore: dashed walls
 * from the shared `crossBoreLines`. Must stay identical to the preview's overlay pass in
 * `ShaftRenderer`. Reference feature — its only rail is the opt-in position span
 * ([buildCouplerSlotSpans]).
 *
 * Hole centers come from the shared [boltHoleCentersX]: placed off the row's own mapped
 * window (pinned at true scale by `boltRowPinnedSpans`, `geom/ProfileFeatureSpans.kt`), so
 * the circles — sized on the diameter scale — keep their true pitch inside a body that
 * compresses around them.
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

    val hiddenStroke by lazy {
        Paint(outline).apply {
            pathEffect = android.graphics.DashPathEffect(floatArrayOf(HIDDEN_DASH_ON, HIDDEN_DASH_OFF), 0f)
        }
    }

    slots.forEach { slot ->
        val holeR = rPx(slot.holeDiaMm)
        if (holeR <= 0f || slot.count < 1) return@forEach
        val centersX = boltHoleCentersX(slot, xAt)
        for (i in 0 until slot.count) {
            val cxMm = slot.centerMmAt(i)
            val cx = centersX[i]
            if (slot.isHiddenCrossBore) {
                val bore = crossBoreLines(
                    cx, holeR, cy, surfaceRadiusPx(cxMm), slot.through, rPx(slot.depthMm * 2f),
                ) ?: continue
                c.drawLine(bore.x1, bore.yTop, bore.x1, bore.yBottom, hiddenStroke)
                c.drawLine(bore.x2, bore.yTop, bore.x2, bore.yBottom, hiddenStroke)
                if (bore.floor) c.drawLine(bore.x1, bore.yBottom, bore.x2, bore.yBottom, hiddenStroke)
                continue
            }
            val centersY = if (slot.holeStyle == BoltHoleStyle.CROSS) {
                floatArrayOf(cy)
            } else {
                val rSurface = surfaceRadiusPx(cxMm)
                floatArrayOf(cy - rSurface, cy + rSurface)
            }
            centersY.forEach { surfY ->
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
    /**
     * Liners whose shade is suppressed while the rest of [liners] keeps [fill]
     * (`ui/resolved/unshadedLinerIds`). Empty fills every liner the same.
     */
    unfilledIds: Set<String> = emptySet(),
) {
    liners.forEach { ln ->
        if (ln.lengthMm <= 0f || ln.odMm <= 0f) return@forEach
        val linerFill = if (ln.id in unfilledIds) null else fill
        val x0 = xAt(ln.startFromAftMm); val x1 = xAt(ln.startFromAftMm + ln.lengthMm)
        val r = rPx(ln.odMm)

        // Shoulder specs, fill and stroke all come from the shared pass
        // (`pdf/LinerShoulderDraw.kt`) the runout/consolidated sheet draws through too.
        val specs = linerShoulderSpecs(ln, x0, x1, r, xAt, rPx)
        if (linerFill != null) drawLinerFillPdf(c, cy, x0, x1, r, specs, linerFill)
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

