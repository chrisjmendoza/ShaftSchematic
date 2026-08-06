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
import com.android.shaftschematic.settings.PdfPrefs
import com.android.shaftschematic.ui.drawing.render.HIDDEN_DASH_OFF
import com.android.shaftschematic.ui.drawing.render.HIDDEN_DASH_ON
import com.android.shaftschematic.ui.resolved.ResolvedBody
import com.android.shaftschematic.ui.resolved.ResolvedComponent
import com.android.shaftschematic.ui.resolved.ResolvedComponentSource
import com.android.shaftschematic.settings.PdfTieringMode
import com.android.shaftschematic.util.UnitSystem
import com.android.shaftschematic.util.VerboseLog
import com.android.shaftschematic.util.autoTaperRateText
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
    /**
     * "Shaft height" slider — the same per-job multiplier the runout/consolidated sheets
     * use (`RunoutConfig.heightScale`): exaggerate or shrink the drawn shaft, hard-capped
     * at 1.5" on paper and by the page budget (`exaggeratedProfileScale`).
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

    // PDF-only classification: a shaft with exactly one Body and no other detail
    // components. Body-only shafts still go through the same body draw path (center
    // breaks included); the flag only suppresses the footer's compression note, since
    // there is nothing else on the sheet the squeeze needs explaining against.
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

    // ── Visual diameter scale + compressed x mapping (hand-sheet convention) ──
    // On-device rule (with rulered reference sketches): drawn shaft height follows TRUE
    // diameter on the default sizing curve (`defaultShaftHeightPt`; standard 4" → 0.75",
    // 8" → 1.25", anchor heights user-adjustable) and is never diluted by shaft length.
    // The x axis is schematic: every feature keeps a
    // per-kind minimum drawn width and foreshortens above it in proportion to true length
    // (geom/ProfileCompression.kt); plain body runs absorb the squeeze with center-breaks.
    // Shafts that fit at the visual scale keep a plain linear map.
    val maxDiaMm = spec.maxOuterDiaMm().coerceAtLeast(1f)
    val featureSpans: List<ProfileFeatureSpan> = buildList {
        // Tapers may shrink but stay PROPORTIONAL to each other (on-device direction:
        // two very different taper lengths must never draw equal). No flat floor — a
        // ratio-preserving fraction-of-true floor instead, λ-fit like the liner raises;
        // the drawn height never yields to it.
        spec.tapers.forEach {
            add(
                ProfileFeatureSpan(
                    it.startFromAftMm, it.startFromAftMm + it.lengthMm, 0f,
                    minWidthFracOfTrue = PROFILE_TAPER_MIN_FRAC_OF_TRUE,
                )
            )
        }
        // Liners compress in SIZE only — proportional foreshortening above their floor,
        // never a body-style S-break cutout (on-device clarification). The per-job
        // "Liner compression" control raises the floor toward true width — best-effort,
        // λ-fitted; the drawn height never yields to it. The schematic uses the LEAN
        // floors — its values live on rails and callouts, so proportion wins over
        // write-in room here (the consolidated sheet keeps the writable floors).
        spec.liners.forEach {
            add(
                ProfileFeatureSpan(
                    it.startFromAftMm, it.startFromAftMm + it.lengthMm, SCHEMATIC_MIN_LINER_PT,
                    minWidthFracOfTrue = linerMinFracOfTrue,
                )
            )
        }
        spec.threads.forEach {
            add(ProfileFeatureSpan(it.startFromAftMm, it.startFromAftMm + it.lengthMm, SCHEMATIC_MIN_THREAD_PT))
        }
        // A keyway-bearing body pins at true scale — its drawn slot geometry is real.
        bodiesForPdf.filter { it.hasKeyway }.forEach {
            add(ProfileFeatureSpan(it.startFromAftMm, it.startFromAftMm + it.lengthMm, Float.MAX_VALUE))
        }
    }
    // Pinned spans (tapers, keyway bodies) demand true width → when one needs the room,
    // the HEIGHT yields ("doesn't have to be perfectly proportional, just close" —
    // on-device rule). The per-job "Shaft height" slider multiplies the default sizing
    // curve (standard: proportional, 8" → 1.125"; anchor heights user-adjustable via
    // Settings → PDF Export); the 1.5" ceiling and the page budget cap the result
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
    drawBodiesCompressedCenterBreak(
        c, bodiesForPdf, cy, ::xAt, ::rPx, outline, geomRect, bodyFill,
        truePtPerMm = diaPtPerMm,
    )
    // Keyway clocking: the aft-most keyway (measurement datum) always draws face-on; every other
    // host is a secondary. At 180° a secondary renders hidden (dashed, no fill); at 90° it renders
    // as a notch cut into a silhouette edge. Mirrors the canvas renderer.
    val clocking = spec.keywayClocking()
    val hiddenKeywayIds = spec.hiddenKeywayHostIds()
    val secondaryKeywayIds = spec.secondaryKeywayHostIds()
    val silhouetteKeyways = clocking == KeywayClocking.DEG_90_CW || clocking == KeywayClocking.DEG_90_CCW
    // Body keyways — drawn from model bodies (resolved fragments and center-break
    // compression keep true end faces, so the slot lands at its physical position).
    spec.bodies.filter { it.hasKeyway }.forEach { b ->
        if (silhouetteKeyways && b.id in secondaryKeywayIds) {
            b.keywaySilhouetteNotch(clocking)?.let {
                drawKeywaySilhouetteNotchPdf(c, it, ::xAt, diaPtPerMm, cy, outline)
            }
        } else {
            drawKeywayNotchBodyPdf(c, b, ::xAt, cy, outline, hidden = b.id in hiddenKeywayIds)
        }
    }
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
            xAt((dimMm + win.measureStartMm).toFloat())
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

        val renderer = PdfDimensionRenderer(
            pageX = pageX,
            linePaint = dim,
            textPaint = dimText,
            objectTopY = yTopOfShaft,
            objectClearance = 6f,
            blankLabels = blank,
            blankLabelWidthPx = BLANK_DIM_GAP_PT
        )

        val oalAft = if (spec.threads.any { t ->
            abs(t.startFromAftMm.toDouble()) <= END_EPS_MM && !t.excludeFromOAL
        }) 0.0 else sets.aftSETxMm
        val oalFwd = if (spec.threads.any { t ->
            abs((t.startFromAftMm + t.lengthMm).toDouble() - spec.overallLengthMm.toDouble()) <= END_EPS_MM && !t.excludeFromOAL
        }) win.oalMm else sets.fwdSETxMm
        val oalDimSpan = oalSpan(oalAft, oalFwd, unit, labelMm = spec.overallLengthMm.toDouble())

        // Planner rows, OAL topmost. Rail y values here are UNLIFTED; the plan returns the
        // lifted line positions.
        fun rows(gap: Float, unliftedTopY: Float) =
            assignments.map { renderer.spanInput(it.rail, baseY - gap * it.rail, it.span) } +
                renderer.spanInput(DimensionRailLayout.TOP_RAIL, unliftedTopY, oalDimSpan)

        // A span too short to seat its value in the line prints it ABOVE the line, in the next
        // rail's band — so every rail above lifts by one label band. Inline-vs-above is decided
        // from x-geometry alone, so the lift is known before the lane budget is fixed and the
        // fit loop can shrink the gap (then the text) until the lifted block still clears the
        // content top.
        fun liftFor(gap: Float): Float = renderer.topLift(rows(gap, 0f))
        // The OAL lane is the topmost measurement but rides exactly ONE regular tier
        // pitch above the highest component tier (on-device report: a wider gap wastes
        // whitespace); the planner's lift adds a label band only when the tier below
        // floats a label into the lane.
        fun computeTopY(gap: Float): Float =
            baseY - gap * (maxRail + 1f)

        var topY = computeTopY(railGap) - liftFor(railGap)
        repeat(10) {
            if (topY >= geomRect.top + topSafePad) return@repeat
            if (railGap > minRailGap) {
                railGap = maxOf(railGap - 2f, minRailGap)
            } else if (dimTextSize > minDimTextSize) {
                dimTextSize = maxOf(dimTextSize - 1f, minDimTextSize)
                dimText.textSize = dimTextSize
            }
            topY = computeTopY(railGap) - liftFor(railGap)
        }
        // Final clamp after loop — the OAL rail lands at topY once the lift is applied.
        val topLift = liftFor(railGap)
        topY = max(computeTopY(railGap) - topLift, geomRect.top + topSafePad)

        val plan = renderer.plan(rows(railGap, topY + topLift), safeTopY = geomRect.top + topSafePad)
        assignments.forEachIndexed { i, rs ->
            renderer.drawPlanned(c, rs.span, plan.placements[i], true)
        }
        renderer.drawPlanned(c, oalDimSpan, plan.placements.last(), true)
    }

    // --- Ø callouts below the shaft: one leader per unique body OD and per unique liner OD ---
    run {
        val calls = buildBodyOdCallouts(bodiesForPdf) + buildLinerOdCallouts(spec.liners)
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
                blankValues = blank
            )
            leader.draw(c, calls, unit)
        }
    }

    if (effectiveOptions.showFooter) {
        // Test the same body list the geometry pass drew (resolved incl. auto-bodies),
        // so the note and the drawn center breaks can't disagree.
        val showCompressionNote = !bodyOnly && bodiesForPdf.any { b ->
            val drawnPt = abs(xAt(b.startFromAftMm + b.lengthMm) - xAt(b.startFromAftMm))
            drawnPt < b.lengthMm * diaPtPerMm - 1f || drawnPt >= COMPRESS_TRIGGER_PT
        }

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
            showCompressionNote = showCompressionNote
        )

        val infoTop = cy + halfHeightPx + INFO_GAP_PT
        val infoBottom = min(infoTop + footerBlockPt, pageH - PAGE_MARGIN_PT)
        val infoRect = RectF(geomRect.left, infoTop, geomRect.right, infoBottom)

        drawFooter(c, infoRect, spec, unit, project, filename, appVersion, text, footerCfg, blankValues = blank)
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
private const val BASE_DIM_OFFSET_PT = 24f   // distance from shaft top to first component dim
private const val LANE_GAP_PT = 24f          // spacing between dimension lanes

// Component title labels (PDF only)
private const val COMPONENT_LABEL_OFFSET_PT = 32f
private const val INFO_GAP_PT = 72f          // exactly 1 inch below geometry
internal const val FOOTER_BLOCK_PT = 96f
// Blank drafts reserve a taller footer band: line spacing opens up for handwriting.
internal const val FOOTER_BLOCK_BLANK_PT = 150f
private const val FOOTER_LINE_FACTOR = 1.35f
private const val FOOTER_LINE_FACTOR_BLANK = 1.8f

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
private const val ZIGZAG_GAP_MAX_PT = 20f    // classic central gap; breakPairLayout may widen it to keep the pair clear

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

private fun drawBodiesCompressedCenterBreak(
    c: Canvas,
    bodies: List<Body>,
    cy: Float,
    xAt: (Float) -> Float,
    rPx: (Float) -> Float,
    outline: Paint,
    geomRect: RectF,
    fill: Paint? = null,
    /** True-scale pt/mm — a body drawn shorter than this is foreshortened and MUST break. */
    truePtPerMm: Float = 0f,
) {
    val capPaint = Paint(outline).apply { style = Paint.Style.STROKE }
    bodies.forEach { b ->
        if (b.lengthMm <= 0f || b.diaMm <= 0f) return@forEach
        val x0 = xAt(b.startFromAftMm); val x1 = xAt(b.startFromAftMm + b.lengthMm)
        val r = rPx(b.diaMm); val top = cy - r; val bot = cy + r

        val bodyLenPt = abs(x1 - x0)
        val foreshortened = truePtPerMm > 0f && bodyLenPt < b.lengthMm * truePtPerMm - 1f
        val compress = foreshortened || bodyLenPt >= COMPRESS_TRIGGER_PT

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
            val (gap, amp) = breakPairLayout(
                runLenPt = bodyLenPt,
                desiredAmplitudePt = r * 0.6f,
                classicGapPt = min(ZIGZAG_GAP_MAX_PT, 0.25f * bodyLenPt),
                strokeWidthPt = capPaint.strokeWidth,
            )
            val half = 0.5f * gap
            val leftEnd  = (mid - half).coerceIn(geomRect.left, geomRect.right)
            val rightBeg = (mid + half).coerceIn(geomRect.left, geomRect.right)

            // Left stub — S-curve on right end
            if (fill != null) c.drawRect(x0, top, leftEnd, bot, fill)
            c.drawLine(x0, top, leftEnd, top, outline)
            c.drawLine(x0, bot, leftEnd, bot, outline)
            c.drawLine(x0, top, x0, bot, outline)
            drawBreakEdge(c, leftEnd, top, bot, amp, capPaint, eyeAtTop = false)

            // Right stub — same-direction S-curve on left end (curves match so edges appear to merge)
            if (fill != null) c.drawRect(rightBeg, top, x1, bot, fill)
            drawBreakEdge(c, rightBeg, top, bot, amp, capPaint, eyeAtTop = true)
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
    val silhouetteKeyways = clocking == KeywayClocking.DEG_90_CW || clocking == KeywayClocking.DEG_90_CCW
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
            if (silhouetteKeyways && t.id in secondaryKeywayIds) {
                t.keywaySilhouetteNotch(clocking)?.let {
                    drawKeywaySilhouetteNotchPdf(c, it, xAt, ptPerMm, cy, outline)
                }
            } else {
                drawKeywayNotchPdf(c, t, x0, x1, top0, top1, cy, outline, hidden = t.id in hiddenKeywayIds)
            }
        }
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
    @Suppress("UNUSED_PARAMETER") top0: Float,
    @Suppress("UNUSED_PARAMETER") top1: Float,
    cy: Float,
    outline: Paint,
    hidden: Boolean = false,
) {
    if (x1 == x0 || t.keywayWidthMm <= 0f) return

    val setAtStart = t.startDiaMm <= t.endDiaMm
    val setX = if (setAtStart) x0 else x1
    val letX = if (setAtStart) x1 else x0
    val dir  = if (letX > setX) 1f else -1f

    // Scale mm → pt using the taper's own pixel span (avoids needing ptPerMm).
    val ptPerMm = if (t.lengthMm > 0f) kotlin.math.abs(x1 - x0) / t.lengthMm else 1f

    drawKeywaySlotPdf(c, setX, dir, ptPerMm, t.keywayWidthMm, t.keywayOffsetFromSetMm, t.keywayLengthMm, cy, outline, hidden, t.keywaySpooned)
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
    val ptPerMm = kotlin.math.abs(x1 - x0) / b.lengthMm

    drawKeywaySlotPdf(c, refX, dir, ptPerMm, b.keywayWidthMm, b.keywayOffsetFromEndMm, b.keywayLengthMm, cy, outline, hidden, b.keywaySpooned)
}

/**
 * Shared keyway slot geometry. [refX] is the referenced face (SET face for tapers,
 * AFT/FWD end face for bodies); [dir] is +1 when the slot extends rightward from it.
 * offset ≈ 0 = open at the referenced face; > 0 = floating (mill arcs both ends).
 *
 * [hidden] draws the slot as a far-side feature (keyways 180° apart): dashed outline and
 * **no** white void fill (the near surface is unbroken). Dash matches the preview
 * renderer (`HIDDEN_DASH_ON`/`HIDDEN_DASH_OFF`).
 */
private fun drawKeywaySlotPdf(
    c: Canvas,
    refX: Float,
    dir: Float,
    ptPerMm: Float,
    widthMm: Float,
    offsetMm: Float,
    lengthMm: Float,
    cy: Float,
    outline: Paint,
    hidden: Boolean = false,
    spooned: Boolean = false,
) {
    val halfW       = (widthMm * ptPerMm) / 2f
    val kwSetX      = refX + dir * offsetMm * ptPerMm
    val kwLetX      = kwSetX + dir * lengthMm * ptPerMm
    val isOpen      = offsetMm < 0.01f

    // Spooned (open keyways only): keep the normal keyway (full-length walls + mill semicircle) and
    // ADD an enlarged circle around the closed (LET) end — the mill end stays as an inner reference
    // line inside the bowl. Floating keyways ignore the flag. Mirrors the canvas renderer.
    val bowl = if (spooned && isOpen && halfW > 0f) keywaySpoonBowl(kwLetX, dir, halfW) else null

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
        c.drawRect(fillLeft, cy - halfW, fillRight, cy + halfW, whiteFill)
        c.drawArc(letOval, letArcStart, 180f, false, whiteFill)
        if (!isOpen) c.drawArc(setOval, setArcStart, 180f, false, whiteFill)
        // Spoon bowl void: the enlarged disc around the LET end (its SET-side overlaps the slot).
        if (bowl != null) c.drawCircle(bowl.cx, cy, bowl.radius, whiteFill)
    }

    // ── Outline strokes on top (dashed for hidden far-side keyways) ──
    val stroke = if (hidden) {
        android.graphics.Paint(outline).apply {
            pathEffect = android.graphics.DashPathEffect(floatArrayOf(HIDDEN_DASH_ON, HIDDEN_DASH_OFF), 0f)
        }
    } else outline
    c.drawLine(lineLeft, cy - halfW, lineRight, cy - halfW, stroke)
    c.drawLine(lineLeft, cy + halfW, lineRight, cy + halfW, stroke)
    // Inner mill semicircle — the standard rounded end (kept as a reference line inside the bowl).
    c.drawArc(letOval, letArcStart, 180f, false, stroke)
    if (!isOpen) c.drawArc(setOval, setArcStart, 180f, false, stroke)
    // Spoon bowl: the enlarged circle's major arc around the far side.
    if (bowl != null) {
        val bowlOval = android.graphics.RectF(
            bowl.cx - bowl.radius, cy - bowl.radius, bowl.cx + bowl.radius, cy + bowl.radius,
        )
        c.drawArc(bowlOval, bowl.arcStartDeg, bowl.arcSweepDeg, false, stroke)
    }
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
    blankValues: Boolean = false
) {
    val top = rect.top + 6f
    val lh = text.textSize * (if (blankValues) FOOTER_LINE_FACTOR_BLANK else FOOTER_LINE_FACTOR)

    val cols = buildFooterEndColumns(spec, unit, cfg, blankValues)

    // Blank drafts: any line that ends with ":" (or a bare "Ø") is a label whose value gets
    // hand-written — draw a writing rule after it instead of a printed value.
    fun drawFooterLine(line: String, x: Float, y: Float, maxW: Float) {
        if (blankValues && (line.endsWith(":") || line.endsWith("Ø"))) {
            drawLabelWithRule(c, line, x, y, text, ruleWidth = BLANK_RULE_PT, maxRight = x + maxW)
        } else {
            c.drawText(ellipsizeToWidth(line, text, maxW), x, y, text)
        }
    }

    val leftX  = rect.left
    val midX   = rect.left + rect.width() * 0.40f
    val rightX = rect.left + rect.width() * 0.76f

    // Column budgets: long free text (customer/vessel names) must never overrun the
    // neighbouring column — ellipsize to the available width.
    val colPad = 6f
    val leftMaxW  = midX - leftX - colPad
    val midMaxW   = rightX - midX - colPad
    val rightMaxW = rect.right - rightX

    // AFT (left) — left-aligned at left margin
    run {
        var y = top
        cols.aftLines.forEach { line ->
            drawFooterLine(line, leftX, y, leftMaxW)
            y += lh
        }
    }

    // Middle (Work order) — left-aligned at 1/3 mark
    run {
        var y = top
        if (blankValues) {
            // Job info is hand-written on a blank draft — a fresh date, a different vessel.
            drawFooterLine("Customer:", midX, y, midMaxW); y += lh
            drawFooterLine("Vessel:",   midX, y, midMaxW); y += lh
            drawFooterLine("Job #:",    midX, y, midMaxW); y += lh
            drawFooterLine("Date:",     midX, y, midMaxW); y += lh
            if (cfg.bodyDiasMm.isNotEmpty()) {
                drawFooterLine("Body: Ø", midX, y, midMaxW); y += lh
            }
            keywayClockingFooterNote(spec)?.let { note ->
                c.drawText(ellipsizeToWidth(note, text, midMaxW), midX, y, text); y += lh
            }
            drawFooterLine("Side:", midX, y, midMaxW)
        } else {
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            c.drawText(ellipsizeToWidth("Customer: ${project.customer}", text, midMaxW), midX, y, text); y += lh
            c.drawText(ellipsizeToWidth("Vessel: ${project.vessel}", text, midMaxW),     midX, y, text); y += lh
            c.drawText(ellipsizeToWidth("Job #: ${project.jobNumber}", text, midMaxW),   midX, y, text); y += lh
            c.drawText("Date: $date",                   midX, y, text); y += lh

            if (cfg.bodyDiasMm.isNotEmpty()) {
                val label = cfg.bodyDiasMm.joinToString(", ") { "Ø ${formatDiaWithUnit(it.toDouble(), unit)}" }
                c.drawText(ellipsizeToWidth("Body: $label", text, midMaxW), midX, y, text); y += lh
            }

            // Keyway clocking note — only meaningful with ≥ 2 keyways on the shaft.
            keywayClockingFooterNote(spec)?.let { note ->
                c.drawText(ellipsizeToWidth(note, text, midMaxW), midX, y, text); y += lh
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
    }

    // FWD (right) — left-aligned at 2/3 mark
    run {
        var y = top
        cols.fwdLines.forEach { line ->
            drawFooterLine(line, rightX, y, rightMaxW)
            y += lh
        }
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

/**
 * Builds the exact left/right footer text lines that [drawFooter] will render.
 * Exposed for JVM unit tests so we can validate end-feature detection without
 * depending on Android Canvas/PdfDocument runtime.
 */
internal fun buildFooterEndColumns(
    spec: ShaftSpec,
    unit: UnitSystem,
    cfg: FooterConfig,
    blankValues: Boolean = false
): FooterColumns {
    val ends = detectEndFeatures(spec)
    val taperSides = selectFooterTapers(spec)

    // Blank drafts keep every LABEL (so the writer knows what goes where) and drop the value;
    // drawFooter turns the trailing ":" into a writing rule.
    fun line(label: String, value: () -> String): String =
        if (blankValues) label else "$label ${value()}"

    fun taperLines(tp: Taper): List<String> = buildList {
        val ls = letSet(tp)
        add(line("Rate:") { printedTaperRate(tp.taperRateText.trim().ifEmpty { rate1toN(tp) }, unit) })
        add(line("L.E.T. (${ls.letFace}):") { formatDiaWithUnit(ls.let.toDouble(), unit) })
        add(line("S.E.T. (${ls.setFace}):") { formatDiaWithUnit(ls.set.toDouble(), unit) })
        add(line("Length:") { formatLenWithUnit(tp.lengthMm.toDouble(), unit) })
        if (tp.keywayWidthMm > 0f && tp.keywayDepthMm > 0f) {
            val spoon = if (tp.keywaySpooned) " (spooned)" else ""
            add(line("KW:") {
                if (tp.keywayLengthMm > 0f) {
                    "${formatLenWithUnit(tp.keywayWidthMm.toDouble(), unit)} × ${formatLenWithUnit(tp.keywayDepthMm.toDouble(), unit)} × ${formatLenWithUnit(tp.keywayLengthMm.toDouble(), unit)}$spoon"
                } else {
                    "${formatLenWithUnit(tp.keywayWidthMm.toDouble(), unit)} × ${formatLenWithUnit(tp.keywayDepthMm.toDouble(), unit)}$spoon"
                }
            })
            if (tp.keywaySpooned) add(SPOONED_KW_NOTE)
        }
    }

    val aft = mutableListOf<String>()
    if (cfg.showAftTaper) {
        taperSides.aft?.let { tp ->
            aft += "AFT Taper"
            aft += taperLines(tp)
        }
    }
    if (cfg.showAftThread && ends.aftThread) {
        getAftEndThread(spec)?.let { th ->
            aft += line("Thread:") { "${formatDiaWithUnit(th.majorDiaMm.toDouble(), unit)} × ${fmtPitch(th.pitchMm, unit)} × ${formatLenWithUnit(th.lengthMm.toDouble(), unit)}" }
        }
    }

    val fwd = mutableListOf<String>()
    if (cfg.showFwdTaper) {
        taperSides.fwd?.let { tp ->
            fwd += "FWD Taper"
            fwd += taperLines(tp)
        }
    }
    if (cfg.showFwdThread && ends.fwdThread) {
        getFwdEndThread(spec)?.let { th ->
            fwd += line("Thread:") { "${formatDiaWithUnit(th.majorDiaMm.toDouble(), unit)} × ${fmtPitch(th.pitchMm, unit)} × ${formatLenWithUnit(th.lengthMm.toDouble(), unit)}" }
        }
    }

    // Body-hosted keyways (fitted couplings on intermediate shafts): list in the column
    // matching the keyway's physical half of the shaft.
    fun bodyKwLine(b: Body): String {
        val spoon = if (b.keywaySpooned) " (spooned)" else ""
        return line("Body KW:") {
            "${formatLenWithUnit(b.keywayWidthMm.toDouble(), unit)} × " +
                "${formatLenWithUnit(b.keywayDepthMm.toDouble(), unit)} × " +
                "${formatLenWithUnit(b.keywayLengthMm.toDouble(), unit)}$spoon"
        }
    }
    spec.bodies.filter { it.hasKeyway }.forEach { b ->
        val span = b.keywayAbsSpanMm() ?: return@forEach
        val centerMm = (span.first + span.second) * 0.5f
        val col = if (centerMm <= spec.overallLengthMm * 0.5f) aft else fwd
        col += bodyKwLine(b)
        if (b.keywaySpooned) col += SPOONED_KW_NOTE
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
 */
internal fun buildBodyOdCallouts(bodies: List<Body>): List<DiaCallout> =
    bodies
        .filter { it.diaMm > 0f }
        .groupBy { it.diaMm }
        .entries
        .sortedByDescending { it.key }
        .mapNotNull { (diaMm, group) ->
            val anchor = group.maxByOrNull { it.lengthMm } ?: return@mapNotNull null
            val centerMm = (anchor.startFromAftMm + anchor.lengthMm * 0.5).toDouble()
            DiaCallout(xMm = centerMm, valueMm = diaMm.toDouble(), side = LeaderSide.BELOW)
        }

/**
 * Liner mirror of [buildBodyOdCallouts]: one [DiaCallout] per unique liner OD, anchored at
 * the center of the longest liner with that OD, all BELOW the shaft. Bodies and liners are
 * separate groups — a liner OD is never deduped against a body OD.
 */
internal fun buildLinerOdCallouts(liners: List<Liner>): List<DiaCallout> =
    liners
        .filter { it.odMm > 0f }
        .groupBy { it.odMm }
        .entries
        .sortedByDescending { it.key }
        .mapNotNull { (odMm, group) ->
            val anchor = group.maxByOrNull { it.lengthMm } ?: return@mapNotNull null
            val centerMm = (anchor.startFromAftMm + anchor.lengthMm * 0.5).toDouble()
            DiaCallout(xMm = centerMm, valueMm = odMm.toDouble(), side = LeaderSide.BELOW)
        }

private data class LetSetResult(val let: Float, val set: Float, val letFace: String, val setFace: String)

// startDiaMm is always the AFT-facing end of the taper (position = startFromAftMm).
// Determine which physical face carries the large end so the footer can label it correctly.
private fun letSet(t: Taper): LetSetResult =
    if (t.startDiaMm >= t.endDiaMm)
        LetSetResult(t.startDiaMm, t.endDiaMm, "AFT", "FWD")
    else
        LetSetResult(t.endDiaMm, t.startDiaMm, "FWD", "AFT")

/**
 * Shop notation for the two most common tapers on inch drawings: 1:12 prints as 1"/ft and
 * 1:16 as ¾"/ft — the way the shop hand-writes them. Every other rate keeps its ratio form
 * (1:10, 1:20, exact 1:N.NNN, or the user's own manual text). Metric drawings keep the
 * ratio for all rates; inch-per-foot notation would clash with mm dimensions.
 */
internal fun printedTaperRate(rateText: String, unit: UnitSystem): String = when {
    unit != UnitSystem.INCHES -> rateText
    rateText == "1:12" -> "1\"/ft"
    rateText == "1:16" -> "¾\"/ft"
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
    return copy(bodies = resolved.filterIsInstance<ResolvedBody>().map { b ->
        Body(
            id = b.id,
            startFromAftMm = b.startMmPhysical,
            lengthMm = b.endMmPhysical - b.startMmPhysical,
            diaMm = b.diaMm,
        )
    })
}

/**
 * Truncates [text] with an ellipsis so it fits within [maxWidth] points. Footer columns sit
 * at fixed x positions; long customer/vessel names must never overrun the next column.
 */
internal fun ellipsizeToWidth(text: String, paint: Paint, maxWidth: Float): String {
    if (maxWidth <= 0f || paint.measureText(text) <= maxWidth) return text
    val ellipsis = "…"
    var end = text.length
    while (end > 0 && paint.measureText(text.substring(0, end) + ellipsis) > maxWidth) end--
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
    val showCompressionNote: Boolean,
    /** Distinct body ODs (mm) to print as the "Body:" line; empty hides the line. */
    val bodyDiasMm: List<Float> = emptyList(),
)
