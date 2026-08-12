package com.android.shaftschematic.pdf

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.android.shaftschematic.model.*
import com.android.shaftschematic.geom.DimensionRailLayout
import com.android.shaftschematic.geom.PlacedRunoutBubble
import com.android.shaftschematic.geom.RunoutBubbleGeometry
import com.android.shaftschematic.geom.RunoutComponentKind
import com.android.shaftschematic.geom.RunoutComponentSpan
import com.android.shaftschematic.ui.resolved.runoutComponentSpans
import com.android.shaftschematic.geom.clampUndercutSpan
import com.android.shaftschematic.geom.clockTickRimOffset
import com.android.shaftschematic.geom.collectRunoutStations
import com.android.shaftschematic.geom.computeOalWindow
import com.android.shaftschematic.geom.PROFILE_MIN_LINER_PT
import com.android.shaftschematic.geom.PROFILE_TAPER_MIN_FRAC_OF_TRUE
import com.android.shaftschematic.geom.PROFILE_MIN_THREAD_PT
import com.android.shaftschematic.geom.ProfileFeatureSpan
import com.android.shaftschematic.geom.defaultVisualScale
import com.android.shaftschematic.geom.buildCompressedProfileXMap
import com.android.shaftschematic.geom.exaggeratedProfileScale
import com.android.shaftschematic.geom.solveMaxProfileScale
import com.android.shaftschematic.geom.computeSetPositionsInMeasureSpace
import com.android.shaftschematic.geom.WORN_VALUE_BAND_FIT_FRAC
import com.android.shaftschematic.geom.WornValueColumn
import com.android.shaftschematic.geom.fittedValueTextSize
import com.android.shaftschematic.geom.layoutWornSectionValues
import com.android.shaftschematic.geom.planRunoutBubbles
import com.android.shaftschematic.geom.wornValueBandHeightNeeded
import com.android.shaftschematic.pdf.dim.RailPlanner
import com.android.shaftschematic.pdf.dim.buildLinerSpans
import com.android.shaftschematic.pdf.dim.oalSpan
import com.android.shaftschematic.pdf.render.PdfDimensionRenderer
import com.android.shaftschematic.settings.PDF_SBREAK_THRESHOLD_DEFAULT
import com.android.shaftschematic.settings.PdfPrefs
import com.android.shaftschematic.settings.RunoutConfig
import com.android.shaftschematic.settings.TirDirection
import com.android.shaftschematic.ui.resolved.ResolvedBody
import com.android.shaftschematic.ui.resolved.ResolvedComponent
import com.android.shaftschematic.ui.resolved.ResolvedComponentSource
import com.android.shaftschematic.util.UnitSystem
import com.android.shaftschematic.util.formatRunoutValue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min

// ──────────────────────────────────────────────────────────────────────────────
// Public entry point
// ──────────────────────────────────────────────────────────────────────────────

/**
 * RunoutPdfComposer
 *
 * Generates a runout measurement sheet PDF page for the given shaft specification, in one
 * of two selectable layouts (landscape US Letter, 792 × 612 pt):
 *
 * ## Consolidated ONE-SHEET (`consolidated = true`, the default)
 *
 * ```
 * ┌─── |←──────────────── OAL (top rail) ────────────────→| ─────────────────┐
 * │      |←─ liner/taper dimension tiers (RailPlanner) ─→|                    │
 * │  [shaft profile: compressed, wear bands/pits, in-profile Ø values]        │
 * │   ╲  ╲  ╲  ╲   ╲  ╲  ╲   ╲  ╲  ╲     ← leader lines (straight/dogleg)  │
 * │   ○     ○      ○      ○      ○        ← row-0 bubbles (closer to shaft) │
 * │      ○      ○     ○      ○       ○    ← row-1 bubbles (alternating)     │
 * │  TIR's taken looking: _______________________                             │
 * │  [footer: AFT taper | Customer/Vessel/Job#/Date/Side | FWD taper]         │
 * └───────────────────────────────────────────────────────────────────────────┘
 * ```
 *
 * ## Classic runout sheet (`consolidated = false`)
 *
 * ```
 * ┌─── header: Customer / Vessel / Job# / Date / Side ───────────────────────┐
 * │  |←────────── OAL (AFT SET → FWD SET) ────────────────────→|  ← raised  │
 * │  |  [shaft profile: bodies, tapers, liners, threads, breaks] |  witness  │
 * │                                                                            │
 * │   ╲  ╲  ╲  ╲   ╲  ╲  ╲   ╲  ╲  ╲     ← leader lines (straight/dogleg)  │
 * │   ○     ○      ○      ○      ○      ← bubbles, same engine as above     │
 * │  TIR's taken looking: _______________________                             │
 * └───────────────────────────────────────────────────────────────────────────┘
 * ```
 *
 * No dimension rails, no footer, and no wear info in classic mode — the standalone sheet
 * the Runout tab prints (and the Runout document in the Output tab's batch export). Both
 * modes share the compressed profile, the bubble engine, and the "Shaft height" slider.
 *
 * ## Bubble placement convention
 * - **Tapers**: two stations inset from each edge by [RunoutConfig.RUNOUT_EDGE_INSET_MM].
 *   Readings taken right on a taper's SET or LET face are unreliable.
 * - **Liners**: same inset convention as tapers — worn areas rarely reach a liner's very
 *   edges, so near-edge readings are the best runout spots (on-device rule).
 * - **Bodies**: stations spread evenly across each body's DRAWN span (cell midpoints in
 *   page x, inverted to physical mm through the compressed mapping) — a body surface is
 *   uniform, so the exact physical spot is free, and drawn-even placement keeps the
 *   sheet readable where physical midpoints would bunch into a foreshortened run
 *   (on-device request).
 * - **Threads**: no stations (threads are not measured for runout). Still drawn as
 *   hatched envelopes for visual reference; excluded-from-OAL threads sit outside the
 *   SET-to-SET arrows at their physical position.
 *
 * ## Bubble rows and leader routing
 * Placement is delegated to the shared engine in `geom/RunoutBubbleLayout.kt` (also used
 * by the RunoutRoute canvas preview, so the two renderings are identical). Within each
 * component, consecutive stations alternate between row 0 (closer to the shaft) and
 * row 1 (further away) — matching the hand-drawn convention in the shop reference
 * drawings — and the engine guarantees that no bubble touches another bubble and no
 * leader line crosses a bubble or another leader. See RunoutBubbleLayout's KDoc for the
 * spacing invariants and the dogleg fallback.
 *
 * ## Keyway reference marker
 * An open square notch straddling each circle's rim at 12-o'clock indicates
 * keyway-at-top centre, matching the hand-drawn shop sheets. A recorded high spot prints
 * as a short dash straddling the rim at its clock position; the notch is the 12-o'clock
 * reference.
 *
 * @param page     Target PDF page (US Letter landscape, already started).
 * @param spec     Shaft specification in millimeters.
 * @param config   Runout preferences — bubble count overrides and TIR direction label.
 * @param project  Job information (customer, vessel, job#, side).
 * @param unit     Display unit for the OAL dimension label.
 * @param resolvedComponents Resolved component list from the ViewModel. When provided,
 *                 resolved bodies (subtracted against tapers/liners, split/merged, with
 *                 auto-fill gaps) replace `spec.bodies` for the profile and the stations —
 *                 same contract as `composeShaftPdf`. Raw spec bodies may legally overlap
 *                 tapers/liners; resolution is what turns them into drawable segments.
 */
fun composeRunoutPdf(
    page: PdfDocument.Page,
    spec: ShaftSpec,
    config: RunoutConfig,
    project: ProjectInfo,
    unit: UnitSystem,
    pdfPrefs: PdfPrefs = PdfPrefs(),
    resolvedComponents: List<ResolvedComponent>? = null,
    lineThicknessScale: Float = 1.0f,
    runoutReadings: RunoutReadings = RunoutReadings(),
    /**
     * Wear record for the consolidated runout/wear sheet: worn sections and point readings
     * print their measured Ø values inside the shaft profile, spots and pits draw as marks
     * on it. Whether any VALUE prints also decides the liner fill —
     * [consolidatedSheetHasInProfileValues].
     */
    wearRecord: WearRecord = WearRecord(),
    /**
     * Blank-draft (write-in) mode: header job info, the OAL value, recorded TIR readings, and
     * the TIR direction are all blanked so the whole sheet can be filled in by hand.
     */
    blankValues: Boolean = false,
    /**
     * `true` (default) prints the consolidated ONE-SHEET: schematic dimension rails above,
     * wear info in the profile, spec footer below. `false` prints the original standalone
     * runout sheet — one-line job header, raised OAL span line, profile + bubbles + TIR
     * only (no rails, no footer, no wear info) — the Runout tab's own document.
     * Both modes share the compressed profile and the "Shaft height" slider.
     */
    consolidated: Boolean = true,
    /**
     * Consolidated-sheet content election (`ConsolidatedVariant` on the Output tab):
     * [includeBubbles] keeps the runout stations/bubbles and the TIR line;
     * [includeWearInfo] keeps the wear marks, worn sections, and in-profile Ø values.
     * The schematic rails + footer are always on. Both ignored in classic mode
     * (`consolidated = false` is inherently bubbles-only).
     */
    includeBubbles: Boolean = true,
    includeWearInfo: Boolean = true,
) {
    // Normalized content flags: the classic sheet IS the runout document (bubbles always
    // on, wear info never), whatever the variant flags say.
    val drawBubbles = !consolidated || includeBubbles
    val drawWear = consolidated && includeWearInfo
    val c = page.canvas
    c.drawColor(Color.WHITE)

    val docSpec = spec.withResolvedBodies(resolvedComponents)

    val pageW = page.info.pageWidth.toFloat()
    val pageH = page.info.pageHeight.toFloat()

    // ── Paints ──────────────────────────────────────────────────────────────
    val thicknessScale = lineThicknessScale.coerceIn(0.5f, 2.0f)
    val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = OUTLINE_PT * thicknessScale
        color = Color.BLACK
    }
    val dim = Paint(outline).apply { strokeWidth = DIM_PT * thicknessScale }
    fun shadeFill() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(40, 0, 0, 0)
    }
    val bodyFill : Paint? = if (pdfPrefs.shadedBodies) shadeFill() else null
    val taperFill: Paint? = if (pdfPrefs.shadedTapers) shadeFill() else null
    // Liners follow `shadedLiners` like bodies and tapers, EXCEPT on a sheet that prints
    // measured Ø values INSIDE the profile: those values sit on sheet-white knockout halos,
    // and shading the liner under them would turn every halo into a pasted white box
    // instead of clear paper. One predicate governs this fill and the Output tab's "Liners"
    // checkbox, so the control can never offer a shade the sheet does not draw.
    val inProfileValues = consolidatedSheetHasInProfileValues(
        wornSections = wearRecord.wornSections,
        diaReadings = wearRecord.diaReadings,
        resolvedComponentIds = resolvedComponents?.map { it.id }?.toSet() ?: emptySet(),
        includeWearInfo = drawWear,
        blankValues = blankValues,
    )
    val linerFill: Paint? = if (pdfPrefs.shadedLiners && !inProfileValues) shadeFill() else null
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        textSize = TEXT_PT
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        color = Color.BLACK
    }
    // ── Page regions ─────────────────────────────────────────────────────────
    val margin = PAGE_MARGIN_PT
    val contentLeft  = margin
    val contentRight = pageW - margin
    val contentW     = contentRight - contentLeft

    // ── Compute shaft scale ───────────────────────────────────────────────────
    // The runout sheet spans the SET-to-SET measurement window (AFT SET face → FWD SET face).
    // Thread components outside this window are not drawn on the runout profile.
    val oalWindow      = computeOalWindow(spec)
    val setPositions   = computeSetPositionsInMeasureSpace(oalWindow, spec)
    val aftSetMm       = setPositions.aftSETxMm.toFloat()
    val fwdSetMm       = setPositions.fwdSETxMm.toFloat()
    val drawSpanMm     = (fwdSetMm - aftSetMm).coerceAtLeast(1f)
    val widthFitPtPerMm = contentW / drawSpanMm

    /** Prelim linear mapping — used ONLY for the bubble-row budget (see the cycle note below). */
    fun xAtLinear(mm: Float): Float = contentLeft + (mm - aftSetMm) * widthFitPtPerMm

    // Empty when bubbles are elected out — the plan then reserves zero height and the
    // shaft area absorbs the difference.
    //
    // Built through the SHARED span mapping (`ui/resolved/RunoutSpans.kt`) the live canvas
    // uses, so both sites key stations by the component the user names. Building these from
    // `docSpec.bodies` instead kept the resolved FRAGMENT id ("<id>#2"), which silently
    // dropped the user's count override and orphaned hand-entered TIR readings on any body a
    // liner had split.
    val stationSpans = if (!drawBubbles) {
        emptyList()
    } else {
        resolvedComponents?.let { runoutComponentSpans(it) } ?: buildList {
            docSpec.bodies.forEach { add(RunoutComponentSpan(it.id, RunoutComponentKind.BODY, it.startFromAftMm, it.lengthMm)) }
            docSpec.tapers.forEach { add(RunoutComponentSpan(it.id, RunoutComponentKind.TAPER, it.startFromAftMm, it.lengthMm)) }
            docSpec.liners.forEach { add(RunoutComponentSpan(it.id, RunoutComponentKind.LINER, it.startFromAftMm, it.lengthMm)) }
        }
    }
    val bubbleGeom = RunoutBubbleGeometry(
        radius = BUBBLE_RADIUS_PT,
        minGap = BUBBLE_MIN_GAP_PT,
        shortLeader = SHORT_LEADER_PT,
        contentLeft = contentLeft,
        contentRight = contentRight,
    )

    // ── Consolidated sheet layout (on-device request, hand-drawn reference) ───
    // One page carries everything: schematic dimension rails ABOVE the shaft (OAL topmost,
    // datum + length tiers beneath — the schematic's own span builders and tier engine),
    // the compressed profile with wear info, runout bubbles BELOW, the TIR line, and the
    // schematic's 3-column footer (AFT taper | job info | FWD taper) at the bottom.
    //
    //   margin → OAL rail → dim tiers → [shaft] → bubbles → TIR line → footer → margin

    // Dimension spans are mm-space and independent of the x mapping, so the rail count is
    // known before any scale is chosen. Rails exist only on the consolidated sheet; the
    // classic sheet reserves its one-line header + the raised OAL lane instead.
    val measureFromMode = pdfPrefs.tieringMode
    val railAssignments = if (consolidated) {
        val dimSpans = buildLinerSpans(
            liners = mapToLinerDimsForPdf(spec, measureFromMode),
            sets = setPositions,
            unit = unit,
            measureFrom = measureFromMode,
        ) + buildTaperLengthSpans(spec, oalWindow, unit)
        RailPlanner().assignAll(dimSpans, tierOriginMmFor(measureFromMode, oalWindow.oalMm))
    } else emptyList()
    val maxRail = railAssignments.maxOfOrNull { it.rail } ?: -1
    val railGap = RUNOUT_RAIL_GAP_PT
    val dimText = Paint(text).apply { textSize = RUNOUT_DIM_TEXT_PT }
    // OAL brackets the SET-to-SET span; the label is ALWAYS the typed OAL (the number is
    // sacred; see docs/OverallLength.md).
    val oalDimSpan = oalSpan(
        setPositions.aftSETxMm, setPositions.fwdSETxMm, unit,
        labelMm = spec.overallLengthMm.toDouble(),
    )

    /** Planner rows for the rail block under a given mm→page mapping; OAL topmost. */
    fun dimRows(renderer: PdfDimensionRenderer, railY: (Int) -> Float) =
        railAssignments.map { renderer.spanInput(it.rail, railY(it.rail), it.span) } +
            renderer.spanInput(DimensionRailLayout.TOP_RAIL, railY(DimensionRailLayout.TOP_RAIL), oalDimSpan)

    // A span too short to seat its value in the line prints it ABOVE the line — inside the next
    // rail's band — so every rail above lifts by one label band and the reserved block has to
    // grow by the same amount. Inline-vs-above depends only on a span's drawn WIDTH, so the
    // prelim linear map answers it before the shaft scale is solved (the same prelim-then-
    // resolve posture the bubble budget uses); the drawn plan below re-solves on the real map.
    val prelimRailLift = if (!consolidated) 0f else {
        val prelim = PdfDimensionRenderer(
            pageX = { dimMm -> xAtLinear((dimMm + oalWindow.measureStartMm).toFloat()) },
            linePaint = dim,
            textPaint = dimText,
            objectTopY = 0f,   // lift query only — nothing is drawn through this renderer
            blankLabels = blankValues,
            blankLabelWidthPx = BLANK_DIM_GAP_PT,
        )
        prelim.topLift(dimRows(prelim) { 0f })
    }

    // Height reserved above the shaft: first-rail offset + tier rows + above-line label lifts
    // + the OAL lane (consolidated), or header strip + gap + raised OAL span line (classic).
    val railsBlockH =
        if (consolidated) RUNOUT_BASE_DIM_OFFSET_PT + railGap * (maxRail + 1) + 8f + prelimRailLift
        else HEADER_HEIGHT_PT + OAL_GAP_PT + OAL_LINE_SPACE_PT

    // Footer block pinned to the page bottom (consolidated only); the TIR line sits
    // directly above it — or directly above the margin on the classic sheet.
    val footerBlockH = when {
        !consolidated -> 0f
        blankValues -> FOOTER_BLOCK_BLANK_PT
        else -> FOOTER_BLOCK_PT
    }
    val footerTop = pageH - margin - footerBlockH
    // No TIR line when bubbles are elected out — its lane returns to the shaft area.
    val tirY = footerTop - (if (drawBubbles) TIR_LINE_HEIGHT_PT else 0f)

    // ── Vertical budget ───────────────────────────────────────────────────────
    // The diameter scale needs the shaft's height budget, the budget needs the bubble row
    // count, and the final bubble x positions need the (scale-dependent) compressed
    // mapping — a cycle. Break it with a prelim linear-map plan for the BUDGET only; the
    // plan used for drawing is re-solved on the real mapping below.
    val maxOuterDiaMm  = docSpec.maxOuterDiaMm().coerceAtLeast(10f)
    val shaftTopBudgetY = margin + railsBlockH
    val availableH     = tirY - shaftTopBudgetY
    val prelimPlan     = planRunoutBubbles(
        collectRunoutStations(
            stationSpans, config.componentOverrides, ::xAtLinear,
            mmAtX = { x -> aftSetMm + (x - contentLeft) / widthFitPtPerMm },
        ),
        bubbleGeom,
    )
    val shaftAreaBudgetH = availableH - prelimPlan.sectionHeight(BUBBLE_GAP_PT)

    // ── Outer envelope diameter (mm) at a station — scale solve + drawing share it ─
    fun outerDiaMmAt(mm: Float): Float {
        var maxDia = 0f
        docSpec.bodies.forEach { b ->
            if (mm >= b.startFromAftMm - 0.1f && mm <= b.startFromAftMm + b.lengthMm + 0.1f)
                maxDia = maxOf(maxDia, b.diaMm)
        }
        docSpec.tapers.forEach { t ->
            val s = t.startFromAftMm; val e = s + t.lengthMm
            if (mm >= s - 0.1f && mm <= e + 0.1f) {
                val frac = ((mm - s) / (e - s)).coerceIn(0f, 1f)
                maxDia = maxOf(maxDia, t.startDiaMm + (t.endDiaMm - t.startDiaMm) * frac)
            }
        }
        docSpec.liners.forEach { ln ->
            if (mm >= ln.startFromAftMm - 0.1f && mm <= ln.startFromAftMm + ln.lengthMm + 0.1f)
                maxDia = maxOf(maxDia, ln.odMm)
        }
        return maxDia.coerceAtLeast(maxOuterDiaMm * 0.1f)
    }

    // ── Diameter scale + compressed x mapping ─────────────────────────────────
    // The hand-sheet convention (on-device request, with the shop's reference sketch):
    // the shaft's drawn height follows its true diameter on the default sizing curve,
    // never diluted by length. Details (tapers, liners,
    // threads) keep TRUE proportions at the diameter scale; the plain body runs between
    // them absorb the horizontal overflow — foreshortened with S-break glyphs
    // ("compressed to give the impression of a thicker shaft"). Short shafts whose
    // width-fit already meets the target keep the classic linear map unchanged.
    //
    // The scale solve also folds in the in-profile wear values: a value's rotated length
    // sets a minimum band height at its station, so readings print at full text size
    // whenever the page allows (auto-fit in the draw functions stays as the backstop).

    // Feature spans with per-kind width floors: everything may foreshorten (the hand-sheet
    // x axis is schematic) but each kind keeps a writable minimum, and a keyway-bearing
    // body pins at true scale so its drawn slot geometry stays real.
    val featureSpans: List<ProfileFeatureSpan> = buildList {
        // Tapers may shrink but stay PROPORTIONAL to each other (on-device direction:
        // two very different taper lengths must never draw equal). No flat floor — a
        // ratio-preserving fraction-of-true floor instead, λ-fit like the liner raises;
        // the drawn height never yields to it.
        docSpec.tapers.forEach {
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
        // λ-fitted; the drawn height never yields to it.
        docSpec.liners.forEach {
            add(
                ProfileFeatureSpan(
                    it.startFromAftMm, it.startFromAftMm + it.lengthMm, PROFILE_MIN_LINER_PT,
                    minWidthFracOfTrue = config.linerMinFracOfTrue,
                )
            )
        }
        docSpec.threads.forEach {
            add(ProfileFeatureSpan(it.startFromAftMm, it.startFromAftMm + it.lengthMm, PROFILE_MIN_THREAD_PT))
        }
        docSpec.bodies.filter { it.hasKeyway }.forEach {
            add(ProfileFeatureSpan(it.startFromAftMm, it.startFromAftMm + it.lengthMm, Float.MAX_VALUE))
        }
    }

    val valueNeedScale: Float = run {
        // No in-profile values without wear info — they place no demand on the scale.
        if (blankValues || !drawWear) return@run 0f
        val fm = text.fontMetrics
        val baseLine = fm.descent - fm.ascent
        var need = 0f
        fun consider(labels: List<String>, stationDiaMm: Float) {
            if (labels.isEmpty() || stationDiaMm <= 0f) return
            val neededPt = labels.maxOf { wornValueBandHeightNeeded(text.measureText(it), baseLine) }
            need = maxOf(need, neededPt / (stationDiaMm * WORN_VALUE_BAND_FIT_FRAC))
        }
        wearRecord.wornSections.forEach { s ->
            val clamped = clampUndercutSpan(s.startFromAftMm, s.lengthMm, spec.overallLengthMm)
            if (clamped.isEmpty) return@forEach
            val startMm = maxOf(clamped.startMm, aftSetMm)
            val endMm = minOf(clamped.endMm, fwdSetMm)
            if (endMm - startMm <= 1e-3f) return@forEach
            consider(wornSectionValueLabels(s.diaMm, unit), outerDiaMmAt((startMm + endMm) / 2f))
        }
        resolvedComponents?.associateBy { it.id }?.let { byId ->
            wearRecord.diaReadings.forEach { r ->
                if (r.diaMm <= 0f) return@forEach
                val rc = byId[r.componentId] ?: return@forEach
                val lenMm = (rc.endMmPhysical - rc.startMmPhysical).coerceAtLeast(0.001f)
                val stationMm = rc.startMmPhysical + r.axialMm.coerceIn(0f, lenMm)
                consider(listOf(diaReadingValueLabel(r.diaMm, unit)), outerDiaMmAt(stationMm))
            }
        }
        need
    }

    // Sizing rule (shared with the schematic): height follows TRUE diameter on the
    // default curve (standard: 8" → 1.25", 4" → 0.75", linear between; anchor heights
    // user-adjustable via Settings → Drawing). Keyway-bearing bodies stay
    // pinned at true width, so when one needs the room the HEIGHT yields —
    // solveMaxProfileScale finds the largest scale that still lays out on the page
    // ("doesn't have to be perfectly proportional, just close").
    //
    // The "Shaft height" slider (config.heightScale) then multiplies the conventional
    // scale — exaggerate or shrink the whole drawn shaft. The 1.5" ceiling is ABSOLUTE:
    // even a short shaft's width-fit is capped, keeping proportion without spanning the
    // page; the page budget caps everything (exaggeratedProfileScale, pure, unit-tested).
    val targetScale  = defaultVisualScale(maxOuterDiaMm, pdfPrefs.curveLoHeightPt, pdfPrefs.curveHiHeightPt)
    val desiredScale = exaggeratedProfileScale(
        baseScale = maxOf(widthFitPtPerMm, targetScale, valueNeedScale),
        heightFrac = config.heightScale,
        budgetCapPt = shaftAreaBudgetH - 12f,
        maxDiaMm = maxOuterDiaMm,
    )
    val diaPtPerMm = solveMaxProfileScale(
        windowStartMm = aftSetMm, windowEndMm = fwdSetMm,
        features = featureSpans, contentWidth = contentW, scaleHi = desiredScale,
    ).coerceAtLeast(1e-5f)

    val xMap = buildCompressedProfileXMap(
        windowStartMm = aftSetMm, windowEndMm = fwdSetMm,
        features = featureSpans,
        contentLeft = contentLeft, contentRight = contentRight,
        diaPtPerMm = diaPtPerMm,
    )

    /** Physical shaft mm → page x through the compressed mapping. */
    fun xAt(mm: Float): Float = xMap.xAt(mm)

    /** Convert a diameter mm → drawn radius pt (the solved diameter scale). */
    fun rPx(diaMm: Float): Float = (diaMm * 0.5f) * diaPtPerMm

    // Final bubble plan on the real mapping. Taper/liner stations ride their physical mm
    // through the compressed map; BODY stations place evenly across each drawn span
    // (mmAt inverts them back to physical mm — see collectRunoutStations). Row count can
    // differ from the prelim by a hair; the shaftCy coerce below absorbs it.
    val bubblePlan = planRunoutBubbles(
        collectRunoutStations(stationSpans, config.componentOverrides, ::xAt, mmAtX = xMap::mmAt),
        bubbleGeom,
    )
    val shaftAreaH  = availableH - bubblePlan.sectionHeight(BUBBLE_GAP_PT)
    val shaftHalfPt = rPx(maxOuterDiaMm)  // drawn half-height of the shaft
    val shaftCy     = shaftTopBudgetY + (shaftAreaH / 2f).coerceAtLeast(shaftHalfPt + 4f)

    val geomRect = RectF(contentLeft, margin, contentRight, pageH - margin)

    // ── Outer-radius lookup — returns the ACTUAL drawn surface y for a given mm ─
    // Used so leader lines originate from the shaft's visible outline, not a fixed y.
    fun shaftOuterRPxAt(mm: Float): Float = rPx(outerDiaMmAt(mm))

    // ── Classic sheet: one-line job header + raised OAL span line ─────────────
    // The standalone runout layout — what the Runout tab prints; the Consolidated Output
    // tab owns the consolidated election.
    if (!consolidated) {
        drawRunoutHeader(c, text, contentLeft, contentRight,
            margin, HEADER_HEIGHT_PT, project, blankValues)
        val oalLineY = shaftCy - shaftHalfPt - OAL_LINE_SPACE_PT
        drawOalSpanLine(
            c, dim, text, xMap.x0, xMap.x1, oalLineY,
            aftShaftTopY = shaftCy - shaftOuterRPxAt(aftSetMm),
            fwdShaftTopY = shaftCy - shaftOuterRPxAt(fwdSetMm),
            unit = unit, oalMm = spec.overallLengthMm,
            blankValues = blankValues,
        )
    }

    // ── Dimension rails ABOVE the shaft — the schematic's own span/tier/renderer ──
    // Labels always print TRUE (typed) lengths; the drawn spans ride the compressed
    // mapping, exactly like the hand sheets (a foreshortened liner still reads 29").
    // Value-in-break, blank-draft write-in gaps, and tiering rules all come with the
    // shared PdfDimensionRenderer.
    if (consolidated) {
        val yTopOfShaft = shaftCy - shaftHalfPt
        val railBaseY = yTopOfShaft - RUNOUT_BASE_DIM_OFFSET_PT
        val renderer = PdfDimensionRenderer(
            pageX = { dimMm -> xAt((dimMm + oalWindow.measureStartMm).toFloat()) },
            linePaint = dim,
            textPaint = dimText,
            objectTopY = yTopOfShaft,
            objectClearance = 4f,
            blankLabels = blankValues,
            blankLabelWidthPx = BLANK_DIM_GAP_PT,
        )
        // Lift on the REAL mapping — it can differ from the prelim by a band when a span
        // foreshortens across the inline threshold; the clamp below absorbs the difference.
        val railLift = renderer.topLift(dimRows(renderer) { 0f })
        // The OAL lane rides exactly ONE tier pitch above the highest component tier —
        // same rule as the schematic; the planner lift is the only thing that widens it.
        val unliftedTopRailY = (railBaseY - railGap * (maxRail + 1))
            .coerceAtLeast(margin + 8f + railLift)
        val plan = renderer.plan(
            dimRows(renderer) { rail ->
                if (rail == DimensionRailLayout.TOP_RAIL) unliftedTopRailY else railBaseY - railGap * rail
            },
            safeTopY = margin + 6f,
        )
        railAssignments.forEachIndexed { i, ra -> renderer.drawPlanned(c, ra.span, plan.placements[i], true) }
        renderer.drawPlanned(c, oalDimSpan, plan.placements.last(), true)
    }

    // ── Draw shaft profile ────────────────────────────────────────────────────
    drawShaftProfile(c, docSpec, shaftCy, outline, geomRect, ::xAt, ::rPx,
        bodyFill = bodyFill, taperFill = taperFill, linerFill = linerFill,
        ptPerMm = diaPtPerMm, truePtPerMm = diaPtPerMm,
        breakMinFracOfTrue = pdfPrefs.sBreakThresholdFrac)

    // ── Wear marks + worn sections + in-profile values (consolidated sheet) ───
    // Z-order (on-device request): marks first — wear-area bands and pit X's — then the
    // worn-section boundaries, then ALL value text last: every value sits on a sheet-white
    // halo that knocks out whatever lies beneath it, so the numbers always read. Blank
    // drafts drop recorded wear data entirely (the wear document's blank rule) and keep
    // only the worn-section boundaries as write-in areas.
    if (drawWear && !blankValues) {
        drawWearMarksOnRunoutProfile(
            c, wearRecord, docSpec.liners, resolvedComponents,
            cy = shaftCy, xAt = ::xAt, rPx = ::rPx, outline = outline,
            pitSmallHalf = WEAR_PIT_SMALL_HALF_PROFILE_PT,
        )
    }
    if (drawWear) {
        drawWornSections(
            c, wearRecord.wornSections,
            oalMm = spec.overallLengthMm,
            windowStartMm = aftSetMm, windowEndMm = fwdSetMm,
            unit = unit,
            xAt = ::xAt, surfaceRAt = ::shaftOuterRPxAt, cy = shaftCy,
            outline = outline, text = text,
            includeValues = !blankValues,
            minTextSize = WORN_VALUE_MIN_TEXT_PT,
        )
    }
    if (drawWear && !blankValues) {
        drawDiaReadingsInProfile(
            c, wearRecord.diaReadings, resolvedComponents,
            cy = shaftCy, xAt = ::xAt, surfaceRAt = ::shaftOuterRPxAt,
            unit = unit, text = text,
            minTextSize = WORN_VALUE_MIN_TEXT_PT,
        )
    }

    // ── Fix vertical bubble positions, route leaders, draw ────────────────────
    val bubbleResult = bubblePlan.finish(
        anchorY = shaftCy + shaftHalfPt,
        surfaceYAtMm = { mm -> shaftCy + shaftOuterRPxAt(mm) },
    )
    // Blank drafts keep the bubbles (they ARE the write-in circles) but drop recorded values.
    val effectiveReadings = if (blankValues) RunoutReadings() else runoutReadings
    drawPlacedBubbles(c, bubbleResult.bubbles, outline, effectiveReadings, unit)

    // ── Draw TIR direction line (directly above the footer block) ─────────────
    // Runout content only — elected out with the bubbles on a Schematic + Wear sheet.
    if (drawBubbles) {
        val effectiveTir = if (blankValues) TirDirection.UNSET else config.tirDirection
        drawTirLine(c, text, contentLeft, contentRight, tirY, effectiveTir)
    }

    // ── Footer — the schematic's 3-column block (AFT taper | job info | FWD taper) ──
    // One shared implementation (ShaftPdfComposer.drawFooter): taper Rate/L.E.T./S.E.T./
    // Length/KW/Threads columns, work-order center (Customer/Vessel/Job#/Date/Side, keyway
    // clocking note), blank-draft write-in rules. Consolidated sheet only — the classic
    // sheet carries its job info in the one-line header instead.
    if (consolidated) {
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
            showAftTaper = footerTapers.aft != null,
            showFwdTaper = footerTapers.fwd != null,
            // The note keys off ACTUAL foreshortening in the compressed mapping.
            showCompressionNote = xMap.isCompressedOver(aftSetMm, fwdSetMm),
        )
        drawFooter(
            c, RectF(contentLeft, footerTop, contentRight, pageH - margin),
            spec, unit, project,
            filename = "", appVersion = "",
            text = text, cfg = footerCfg, blankValues = blankValues,
        )
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// In-profile value predicate — shared by the composer and the Output tab's options
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Does this sheet print measured Ø values INSIDE the shaft profile?
 *
 * Those values sit on sheet-white knockout halos, so shading the liner beneath one turns the
 * halo into a pasted white box: liners draw unfilled whenever this is true, whatever
 * `PdfPrefs.shadedLiners` says. ONE predicate serves both consumers — [composeRunoutPdf]
 * decides the liner fill with it, and the Consolidated Output tab locks its "Liners" shade
 * checkbox with it — so the control can never offer a shade the sheet does not draw.
 *
 * The inputs mirror exactly what the value passes draw:
 * - [includeWearInfo] is the composer's own `drawWear` (`consolidated && includeWearInfo`);
 *   the classic runout sheet carries no wear info at all, so it always comes out false.
 * - [blankValues] blanks every recorded value — worn sections keep boundaries only.
 * - A worn section prints only its measurements > 0 (the placed-but-empty rule).
 * - A reading prints only with `diaMm > 0` AND a component that still resolves
 *   ([resolvedComponentIds]); orphans are skipped at the render layer.
 *
 * Wear-area bands and pit X's are marks, not text — they carry no halo and never suppress
 * the fill.
 */
internal fun consolidatedSheetHasInProfileValues(
    wornSections: List<WornSection>,
    diaReadings: List<WearDiaReading>,
    resolvedComponentIds: Set<String>,
    includeWearInfo: Boolean,
    blankValues: Boolean,
): Boolean {
    if (!includeWearInfo || blankValues) return false
    if (wornSections.any { section -> section.diaMm.any { it > 0f } }) return true
    return diaReadings.any { it.diaMm > 0f && it.componentId in resolvedComponentIds }
}

// ──────────────────────────────────────────────────────────────────────────────
// Worn sections — measured Ø values inside the profile
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Draw designated worn sections on the shaft profile: a full-height boundary line at each
 * end of the span, and the section's measured Ø values stacked across the span **inside**
 * the profile — each value rotated 90° (reading bottom-to-top) with a sheet-white halo
 * knocked out behind it, so no profile line runs through a measurement number.
 *
 * SINGLE draw implementation for both sites — the RunoutRoute preview calls this same
 * function through its Compose canvas's `nativeCanvas`, so the on-screen sheet and the
 * printed sheet are one construction by definition (the strongest form of the
 * draw-both-sites rule). Layout comes from the pure engine `geom/WornSectionMath.kt`.
 *
 * Rules:
 * - Span clamped to the shaft extent ([clampUndercutSpan] — stored record never mutated)
 *   and to the drawn window ([windowStartMm]..[windowEndMm], the SET-to-SET profile span;
 *   the preview canvas passes the full 0..OAL window).
 * - Values ≤ 0 never print (placed-but-empty rule); an empty section draws boundaries
 *   only — on a blank draft ([includeValues] = false) that clear interior IS the write-in
 *   area, same posture as the write-in bubbles.
 * - Value text: "Ø" + [formatDiaWithUnit] — the measurement verbatim, never re-derived.
 */
/** Smallest legible in-profile value text on the printed page (pt). */
internal const val WORN_VALUE_MIN_TEXT_PT = 6f

/** Printable value labels for a worn section — the shared solve/draw source of truth. */
internal fun wornSectionValueLabels(diaMm: List<Float>, unit: UnitSystem): List<String> =
    diaMm.filter { it > 0f }.map { "Ø" + formatDiaWithUnit(it.toDouble(), unit) }

/** Printable label for one measured-Ø point reading. */
internal fun diaReadingValueLabel(diaMm: Float, unit: UnitSystem): String =
    "Ø" + formatDiaWithUnit(diaMm.toDouble(), unit)

internal fun drawWornSections(
    c: Canvas,
    sections: List<WornSection>,
    oalMm: Float,
    windowStartMm: Float,
    windowEndMm: Float,
    unit: UnitSystem,
    xAt: (Float) -> Float,
    surfaceRAt: (Float) -> Float,
    cy: Float,
    outline: Paint,
    text: Paint,
    includeValues: Boolean,
    /**
     * Floor for the per-section value auto-fit ([fittedValueTextSize]): each section's
     * values shrink together until the longest sits inside the local band
     * (surface-to-surface at the span midpoint × [WORN_VALUE_BAND_FIT_FRAC]), never
     * below this size — the numbers must stay legible even if the halo then overhangs.
     */
    minTextSize: Float,
) {
    if (sections.isEmpty()) return
    val eps = 1e-3f
    val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    val baseText = Paint(text).apply { textAlign = Paint.Align.CENTER }
    val baseFm = baseText.fontMetrics
    val baseLine = baseFm.descent - baseFm.ascent

    sections.forEach { section ->
        val clamped = clampUndercutSpan(section.startFromAftMm, section.lengthMm, oalMm)
        if (clamped.isEmpty) return@forEach
        val startMm = maxOf(clamped.startMm, windowStartMm)
        val endMm = minOf(clamped.endMm, windowEndMm)
        if (endMm - startMm <= eps) return@forEach

        val x0 = xAt(startMm)
        val x1 = xAt(endMm)

        // Boundary lines at the section's ends, full local profile height.
        val r0 = surfaceRAt(startMm)
        val r1 = surfaceRAt(endMm)
        c.drawLine(x0, cy - r0, x0, cy + r0, outline)
        c.drawLine(x1, cy - r1, x1, cy + r1, outline)

        if (!includeValues) return@forEach
        val labels = wornSectionValueLabels(section.diaMm, unit)
        if (labels.isEmpty()) return@forEach

        // Auto-fit: one size per section, from its longest value against the local band.
        val bandPx = 2f * surfaceRAt((startMm + endMm) / 2f) * WORN_VALUE_BAND_FIT_FRAC
        val fitted = fittedValueTextSize(
            baseTextSize = baseText.textSize,
            minTextSize = minTextSize,
            labelLengthAtBase = labels.maxOf(baseText::measureText),
            lineHeightAtBase = baseLine,
            bandHeight = bandPx,
        )
        val valueText = if (fitted == baseText.textSize) baseText
                        else Paint(baseText).apply { textSize = fitted }
        val fm = valueText.fontMetrics

        val layout = layoutWornSectionValues(
            x0 = x0, x1 = x1, cy = cy,
            labelLengths = labels.map(valueText::measureText),
            lineHeight = fm.descent - fm.ascent,
        )
        layout.columns.forEachIndexed { i, col ->
            drawRotatedValueColumn(c, col, labels[i], valueText, halo)
        }
    }
}

/**
 * One in-profile value column: knockout halo first (erases every mark already drawn under
 * the value — the "no lines through the numbers" rule), then the 90°-rotated text set into
 * the cleared slot. Shared by the worn-section value pass and the migrated dia readings.
 */
private fun drawRotatedValueColumn(
    c: Canvas,
    col: WornValueColumn,
    label: String,
    valueText: Paint,
    halo: Paint,
) {
    c.drawRect(col.haloLeft, col.haloTop, col.haloRight, col.haloBottom, halo)
    val fm = valueText.fontMetrics
    c.save()
    c.rotate(-90f, col.cx, col.cy)
    c.drawText(label, col.cx, col.cy - (fm.ascent + fm.descent) / 2f, valueText)
    c.restore()
}

/**
 * Wear marks migrated from the retired wear document onto the consolidated runout sheet:
 * the vertical-line wear-area bands (each `WearSpot`, clamped to its liner's span — reuses
 * the wear composer's [drawVerticalBand] construction) and the pit "X" markers (reuses
 * [drawWearPitsOnProfile], so the X stays identical across every draw site per
 * `geom/WearPitMath.kt`). Marks only — value text is drawn later, over halos, by
 * [drawWornSections] / [drawDiaReadingsInProfile] (text always on top).
 *
 * Orphan spots (liner no longer in the spec) and orphan pits are simply skipped —
 * render-layer orphan handling, stored record never mutated.
 */
internal fun drawWearMarksOnRunoutProfile(
    c: Canvas,
    record: WearRecord,
    liners: List<Liner>,
    components: List<ResolvedComponent>?,
    cy: Float,
    xAt: (Float) -> Float,
    rPx: (Float) -> Float,
    outline: Paint,
    pitSmallHalf: Float,
) {
    // Wear areas — thin vertical strokes across the liner span, the shop's hand mark.
    if (record.spots.isNotEmpty()) {
        val bandLines = Paint(outline).apply { strokeWidth = outline.strokeWidth * 0.5f; alpha = 120 }
        record.spots.forEach { spot ->
            val ln = liners.firstOrNull { it.id == spot.linerId } ?: return@forEach
            if (ln.lengthMm <= 0f || ln.odMm <= 0f) return@forEach
            val clamp = clampWearBandToLiner(spot.startMm, spot.lengthMm, ln.lengthMm)
            if (clamp.lengthMm <= 0f) return@forEach
            val r = rPx(ln.odMm)
            drawVerticalBand(
                c,
                xAt(ln.startFromAftMm + clamp.startMm),
                xAt(ln.startFromAftMm + clamp.startMm + clamp.lengthMm),
                cy - r, cy + r,
                bandLines, pitchPt = 6f,
            )
        }
    }
    // Pit X's — the exact wear-document profile construction.
    if (record.pits.isNotEmpty() && components != null) {
        val pitPaint = Paint(outline).apply { strokeCap = Paint.Cap.ROUND }
        drawWearPitsOnProfile(c, record.pits, components, cy, xAt, rPx, pitPaint, smallHalf = pitSmallHalf)
    }
}

/**
 * Measured-Ø readings drawn INSIDE the profile at each reading's station — the consolidated
 * sheet's replacement for the wear document's below-shaft leader callouts (on-device
 * request: values live in the measured area, vertical, like the hand sketch). One rotated
 * column per reading, centered on the station and the centerline, halo knockout first so
 * no mark or line crosses the number. Liner readings draw here too (the retired wear
 * document zoomed them onto detail strips instead).
 *
 * Value-less readings (`diaMm <= 0`) and orphans (unresolved componentId) are skipped —
 * the placed-but-empty and render-layer-orphan rules.
 */
internal fun drawDiaReadingsInProfile(
    c: Canvas,
    readings: List<WearDiaReading>,
    components: List<ResolvedComponent>?,
    cy: Float,
    xAt: (Float) -> Float,
    surfaceRAt: (Float) -> Float,
    unit: UnitSystem,
    text: Paint,
    /** Auto-fit floor, same rule as [drawWornSections] — fitted per reading. */
    minTextSize: Float,
) {
    if (readings.isEmpty() || components == null) return
    val byId = components.associateBy { it.id }
    val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    val baseText = Paint(text).apply { textAlign = Paint.Align.CENTER }
    val baseFm = baseText.fontMetrics
    val baseLine = baseFm.descent - baseFm.ascent

    readings.forEach { r ->
        if (r.diaMm <= 0f) return@forEach
        val rc = byId[r.componentId] ?: return@forEach
        val lenMm = (rc.endMmPhysical - rc.startMmPhysical).coerceAtLeast(0.001f)
        val local = r.axialMm.coerceIn(0f, lenMm)
        val stationMm = rc.startMmPhysical + local
        val stationX = xAt(stationMm)
        val label = diaReadingValueLabel(r.diaMm, unit)

        val bandPx = 2f * surfaceRAt(stationMm) * WORN_VALUE_BAND_FIT_FRAC
        val fitted = fittedValueTextSize(
            baseTextSize = baseText.textSize,
            minTextSize = minTextSize,
            labelLengthAtBase = baseText.measureText(label),
            lineHeightAtBase = baseLine,
            bandHeight = bandPx,
        )
        val valueText = if (fitted == baseText.textSize) baseText
                        else Paint(baseText).apply { textSize = fitted }
        val fm = valueText.fontMetrics

        // Degenerate span → the single column centers exactly on the station.
        val layout = layoutWornSectionValues(
            x0 = stationX, x1 = stationX, cy = cy,
            labelLengths = listOf(valueText.measureText(label)),
            lineHeight = fm.descent - fm.ascent,
        )
        drawRotatedValueColumn(c, layout.columns.single(), label, valueText, halo)
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Classic-sheet header + OAL span line (consolidated = false)
// ──────────────────────────────────────────────────────────────────────────────
// The consolidated sheet prints the schematic's footer block for job info and runs the
// OAL through the shared PdfDimensionRenderer's top rail; the classic sheet keeps the
// original one-line header strip and standalone OAL span line below.

/**
 * Draw the job-info header strip at the top of the classic runout page.
 *
 * Format (single line):  Customer: ___  |  Vessel: ___  |  Job #: ___  |  Date  |  STBD/PORT
 * (The OAL is drawn separately by the OAL span line, not in this header.)
 */
private fun drawRunoutHeader(
    c: Canvas,
    text: Paint,
    left: Float,
    right: Float,
    top: Float,
    height: Float,
    project: ProjectInfo,
    blankValues: Boolean = false,
) {
    val y = top + text.textSize + 2f

    if (blankValues) {
        // Blank draft: every job-info label prints with a writing rule, regardless of what
        // the current document holds — the draft may be used on a different shaft.
        var x = left
        listOf("Customer:", "Vessel:", "Job #:", "Date:", "Side:").forEach { label ->
            x = drawLabelWithRule(c, label, x, y, text, maxRight = right)
        }
    } else {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val side = project.side.printableLabelOrNull()?.let { "  $it" } ?: ""

        val headerText = buildString {
            if (project.customer.isNotBlank()) append("Customer: ${project.customer}   ")
            if (project.vessel.isNotBlank())   append("Vessel: ${project.vessel}   ")
            if (project.jobNumber.isNotBlank()) append("Job #: ${project.jobNumber}   ")
            append("Date: $date$side")
        }
        c.drawText(ellipsizeToWidth(headerText, text, right - left), left, y, text)
    }

    // Thin rule below header
    val ruleY = top + height
    c.drawLine(left, ruleY, right, ruleY, Paint(text).apply {
        style = Paint.Style.STROKE; strokeWidth = 0.5f
    })
}

/**
 * Draw a single OAL dimension arrow spanning the full SET-to-SET measurement window,
 * with witness (extension) lines dropping to the shaft's top edge at each SET face —
 * the same convention as the main schematic and the wear document.
 *
 * This is the only dimension shown on the classic runout sheet — everything else the
 * field crew needs is on the schematic (or the consolidated sheet's dimension rails).
 */
private fun drawOalSpanLine(
    c: Canvas,
    dim: Paint,
    text: Paint,
    x0: Float,
    x1: Float,
    y: Float,
    aftShaftTopY: Float,
    fwdShaftTopY: Float,
    unit: UnitSystem,
    oalMm: Float,
    blankValues: Boolean = false,
) {
    val arrowLen = 8f
    val witnessGap = 3f   // gap between shaft edge and witness line start
    val witnessExt = 5f   // how far the witness line extends past the dimension line

    // Witness lines from the shaft's local top edge up past the dimension line
    c.drawLine(x0, aftShaftTopY - witnessGap, x0, y - witnessExt, dim)
    c.drawLine(x1, fwdShaftTopY - witnessGap, x1, y - witnessExt, dim)

    // Both modes cut a break mid-span — the schematic's dimension-value convention, kept
    // consistent across drawing outputs. Blank: an empty writable gap (no wording where
    // handwriting goes). Printed: the "OAL: value" label seats IN the gap, vertically
    // centred on the line (the small printed prefix is a deliberate visual identifier).
    val mid = (x0 + x1) * 0.5f
    if (blankValues) {
        val gapHalf = BLANK_DIM_GAP_PT * 0.5f
        c.drawLine(x0, y, mid - gapHalf, y, dim)
        c.drawLine(mid + gapHalf, y, x1, y, dim)
    } else {
        // Same formatter as the schematic's OAL rail — inches print as mixed fractions
        // (falling back to 3 decimals), never raw 4-decimal.
        val label = "OAL: ${formatLenDim(oalMm.toDouble(), unit)}"
        val lw = text.measureText(label)
        val gapHalf = lw * 0.5f + DIM_BREAK_TEXT_PAD_PT
        if ((mid - gapHalf) - x0 >= arrowLen + 2f) {
            c.drawLine(x0, y, mid - gapHalf, y, dim)
            c.drawLine(mid + gapHalf, y, x1, y, dim)
            val fm = text.fontMetrics
            c.drawText(label, mid - lw * 0.5f, y - (fm.ascent + fm.descent) * 0.5f, text)
        } else {
            // Fallback for a span too short to host the break + inward arrows: continuous
            // line, label above — mirrors PdfDimensionRenderer's fallback rule.
            c.drawLine(x0, y, x1, y, dim)
            c.drawText(label, mid - lw * 0.5f, y - 4f, text)
        }
    }
    // Left arrowhead
    c.drawLine(x0, y, x0 + arrowLen, y - arrowLen * 0.5f, dim)
    c.drawLine(x0, y, x0 + arrowLen, y + arrowLen * 0.5f, dim)
    // Right arrowhead
    c.drawLine(x1, y, x1 - arrowLen, y - arrowLen * 0.5f, dim)
    c.drawLine(x1, y, x1 - arrowLen, y + arrowLen * 0.5f, dim)
}

// ──────────────────────────────────────────────────────────────────────────────
// Shaft profile drawing
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Draw the simplified shaft profile (no dimension tiers, no labels).
 *
 * Renders bodies (with compression breaks for long sections), tapers, and liners as
 * black outlines. Threads are drawn as thin hatch rectangles — they appear on the shaft
 * so the field crew knows the zone is threaded, but they produce no runout stations.
 *
 * The shaft profile is intentionally simple — the runout sheet is a measurement form,
 * not a technical drawing. All dimensional detail lives on the schematic page.
 */
private fun drawShaftProfile(
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
    /**
     * True-scale pt-per-mm of the diameter solve. A body drawn below
     * [breakMinFracOfTrue] of its true width at this scale shows the S-break pair
     * ([breakForCompression]); milder foreshortening prints plain. 0 disables the check
     * (glyph on long spans only).
     */
    truePtPerMm: Float = 0f,
    /** The user's `PdfPrefs.sBreakThresholdFrac`; 0 = never break on compression. */
    breakMinFracOfTrue: Float = PDF_SBREAK_THRESHOLD_DEFAULT,
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
    // Bodies — with compression breaks for foreshortened (and very long) sections
    drawBodiesForRunout(c, spec.bodies, cy, xAt, rPx, outline, geomRect, truePtPerMm, breakMinFracOfTrue)
    // Tapers
    drawTapersForRunout(c, spec, xAt, rPx, cy, outline)
    // Liners (elevated outline, thin end ticks)
    val dimPaint = Paint(outline).apply { strokeWidth = DIM_PT }
    spec.liners.forEach { ln ->
        if (ln.lengthMm <= 0f || ln.odMm <= 0f) return@forEach
        val x0 = xAt(ln.startFromAftMm); val x1 = xAt(ln.startFromAftMm + ln.lengthMm)
        val r = rPx(ln.odMm); val top = cy - r; val bot = cy + r
        c.drawLine(x0, top, x1, top, outline)
        c.drawLine(x0, bot, x1, bot, outline)
        c.drawLine(x0, top, x0, bot, dimPaint)
        c.drawLine(x1, top, x1, bot, dimPaint)
    }
    // Threads — envelope outline + diagonal hatch to identify the threaded zone
    val hatchPaint = Paint(outline).apply { strokeWidth = DIM_PT * 0.6f; alpha = 160 }
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

/**
 * Draw bodies with the S-break pair on every deeply compressed section (drawn below
 * [breakMinFracOfTrue] of its true width at [truePtPerMm] — [breakForCompression]) and on
 * any traditionally long span ([COMPRESS_TRIGGER_PT]) — the hand-sheet convention. Milder
 * foreshortening prints a plain outline.
 */
private fun drawBodiesForRunout(
    c: Canvas,
    bodies: List<Body>,
    cy: Float,
    xAt: (Float) -> Float,
    rPx: (Float) -> Float,
    outline: Paint,
    geomRect: RectF,
    truePtPerMm: Float = 0f,
    /** The user's `PdfPrefs.sBreakThresholdFrac`; 0 = never break on compression. */
    breakMinFracOfTrue: Float = PDF_SBREAK_THRESHOLD_DEFAULT,
) {
    val capPaint = Paint(outline)
    bodies.forEach { b ->
        if (b.lengthMm <= 0f || b.diaMm <= 0f) return@forEach
        val x0 = xAt(b.startFromAftMm); val x1 = xAt(b.startFromAftMm + b.lengthMm)
        val r  = rPx(b.diaMm);          val top = cy - r; val bot = cy + r
        val lenPt = abs(x1 - x0)
        val foreshortened = breakForCompression(lenPt, b.lengthMm, truePtPerMm, breakMinFracOfTrue)

        if (!foreshortened && lenPt < COMPRESS_TRIGGER_PT) {
            c.drawLine(x0, top, x1, top, outline)
            c.drawLine(x0, bot, x1, bot, outline)
            c.drawLine(x0, top, x0, bot, outline)
            c.drawLine(x1, top, x1, bot, outline)
        } else {
            // Centre break (same S-curve logic as the main schematic PDF)
            val mid   = (x0 + x1) * 0.5f
            val (gap, amp) = breakPairLayout(
                runLenPt = lenPt,
                desiredAmplitudePt = r * 0.6f,
                classicGapPt = min(ZIGZAG_GAP_MAX_PT, 0.25f * lenPt),
                strokeWidthPt = capPaint.strokeWidth,
            )
            val half  = gap * 0.5f
            val lEnd  = (mid - half).coerceIn(geomRect.left, geomRect.right)
            val rBeg  = (mid + half).coerceIn(geomRect.left, geomRect.right)

            c.drawLine(x0, top, lEnd, top, outline)
            c.drawLine(x0, bot, lEnd, bot, outline)
            c.drawLine(x0, top, x0, bot, outline)
            drawBreakEdge(c, lEnd, top, bot, amp, capPaint, eyeAtTop = false)
            drawBreakEdge(c, rBeg, top, bot, amp, capPaint, eyeAtTop = true)
            c.drawLine(rBeg, top, x1, top, outline)
            c.drawLine(rBeg, bot, x1, bot, outline)
            c.drawLine(x1, top, x1, bot, outline)
        }
    }
}

/** Draw taper trapezoids. Also draws keyway indicators if the taper has one. */
private fun drawTapersForRunout(
    c: Canvas,
    spec: ShaftSpec,
    xAt: (Float) -> Float,
    rPx: (Float) -> Float,
    cy: Float,
    outline: Paint,
) {
    spec.tapers.forEach { t ->
        if (t.lengthMm <= 0f || (t.startDiaMm <= 0f && t.endDiaMm <= 0f)) return@forEach
        val x0 = xAt(t.startFromAftMm);              val x1 = xAt(t.startFromAftMm + t.lengthMm)
        val r0 = rPx(t.startDiaMm);                  val r1 = rPx(t.endDiaMm)
        val top0 = cy - r0; val bot0 = cy + r0
        val top1 = cy - r1; val bot1 = cy + r1
        c.drawLine(x0, top0, x1, top1, outline)
        c.drawLine(x0, bot0, x1, bot1, outline)
        c.drawLine(x0, top0, x0, bot0, outline)
        c.drawLine(x1, top1, x1, bot1, outline)

        // Draw keyway indicator on the taper if one exists (same convention as main schematic).
        if (t.hasKeyway) drawKeywayNotchPdf(c, t, x0, x1, top0, top1, cy, outline)
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Bubble drawing
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Draw all placed runout bubbles: leader polylines, the circle with a keyway cutout at 12
 * o'clock, and — when recorded in [readings] — the TIR value (centred, formatted in [unit]) and
 * the high-spot marker (a short dash straddling the rim at the clock position).
 *
 * Placement comes from the shared engine (`geom/RunoutBubbleLayout.kt`), which guarantees bubbles
 * never touch and leaders never enter a bubble or cross each other. A leader polyline is either a
 * straight station→bubble diagonal (2 vertices) or a dogleg with a vertical drop (3 vertices) when
 * the straight route would collide.
 *
 * The keyway cutout and marker geometry mirror the on-screen canvas
 * (`RunoutRoute.drawRunoutMarkers` / `drawRunoutBubbleRing`) so preview and export are identical.
 */
private fun drawPlacedBubbles(
    c: Canvas,
    bubbles: List<PlacedRunoutBubble>,
    outline: Paint,
    readings: RunoutReadings,
    unit: UnitSystem,
) {
    val r = BUBBLE_RADIUS_PT
    val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.BLACK
        // Printed value sits small inside the (larger) circle, leaving room to hand-write.
        textSize = r * 0.60f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }
    val highSpot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(198, 40, 40) // red — the high spot, per shop convention
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = outline.strokeWidth * 1.7f
    }

    for (b in bubbles) {
        // Leader polyline from the shaft surface to the top of the circle.
        b.leader.zipWithNext { p, q -> c.drawLine(p.x, p.y, q.x, q.y, outline) }

        // Ring with keyway cutout at 12 o'clock (top arc broken across an open-topped slot).
        drawRunoutBubbleRingPdf(c, b.bubbleX, b.bubbleCenterY, r, outline)

        val reading = readings.find(b.componentId, b.stationIndex)
        // TIR value, centred in the circle.
        reading?.valueMm?.let { valueMm ->
            val txt = formatRunoutValue(valueMm, unit)
            val fm = valuePaint.fontMetrics
            val baseline = b.bubbleCenterY - (fm.ascent + fm.descent) / 2f
            c.drawText(txt, b.bubbleX, baseline, valuePaint)
        }
        // High-spot marker: a short dash straddling the rim at the clock position (no radial
        // line — it would crowd the centred value). Matches the hand-drawn shop convention.
        reading?.highSpotHalfHours?.let { tick ->
            val (ux, uy) = clockTickRimOffset(tick, 1f)
            val inner = r * 0.70f
            val outer = r * 1.30f
            c.drawLine(
                b.bubbleX + ux * inner, b.bubbleCenterY + uy * inner,
                b.bubbleX + ux * outer, b.bubbleCenterY + uy * outer, highSpot,
            )
        }
    }
}

/**
 * Draw a runout bubble ring with a keyway cutout at 12 o'clock: the top arc is broken across the
 * slot mouth and an open-topped slot descends into the circle (key-at-top shop convention). Shared
 * geometry with the on-screen `RunoutRoute.drawRunoutBubbleRing`.
 */
private fun drawRunoutBubbleRingPdf(c: Canvas, cx: Float, cy: Float, r: Float, outline: Paint) {
    val slotHalf = r * 0.22f
    val slotDepth = r * 0.42f
    val gapDeg = Math.toDegrees(asin((slotHalf / r).coerceIn(0f, 1f).toDouble())).toFloat()

    // Arc everywhere except the gap at the top (top = -90° in the drawArc convention).
    val oval = RectF(cx - r, cy - r, cx + r, cy + r)
    c.drawArc(oval, -90f + gapDeg, 360f - 2f * gapDeg, false, outline)

    // Slot: two verticals descending from the gap edges + a bottom connector.
    val topY = cy - r * cos(Math.toRadians(gapDeg.toDouble())).toFloat()
    val botY = topY + slotDepth
    c.drawLine(cx - slotHalf, topY, cx - slotHalf, botY, outline)
    c.drawLine(cx + slotHalf, topY, cx + slotHalf, botY, outline)
    c.drawLine(cx - slotHalf, botY, cx + slotHalf, botY, outline)
}

// ──────────────────────────────────────────────────────────────────────────────
// TIR direction line
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Draw the "TIR's taken looking: ___" line at the bottom of the runout sheet.
 *
 * If the TIR direction has been set in [RunoutConfig], the direction label is printed
 * in the blank. Otherwise a fill-in line is drawn for handwriting.
 */
private fun drawTirLine(
    c: Canvas,
    text: Paint,
    left: Float,
    right: Float,
    y: Float,
    direction: TirDirection,
) {
    val label = "TIR's taken looking:  "
    val labelW = text.measureText(label)
    c.drawText(label, left, y, text)

    val fillStart = left + labelW
    when (direction) {
        TirDirection.UNSET -> {
            // Blank fill-in line for handwriting
            c.drawLine(fillStart, y + 2f, fillStart + 180f, y + 2f, Paint(text).apply {
                style = Paint.Style.STROKE; strokeWidth = 0.7f
            })
        }
        TirDirection.AFT     -> c.drawText("AFT",     fillStart, y, text)
        TirDirection.FORWARD -> c.drawText("FORWARD", fillStart, y, text)
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Constants
// ──────────────────────────────────────────────────────────────────────────────

// Stroke weights
private const val OUTLINE_PT = 2.0f
private const val DIM_PT     = 1.2f
private const val TEXT_PT    = 10f

// Page layout
private const val PAGE_MARGIN_PT   = 36f    // 0.5 in margins
private const val TIR_LINE_HEIGHT_PT = 20f  // Space for TIR direction line at bottom

// Classic-sheet layout (consolidated = false)
private const val HEADER_HEIGHT_PT = 22f    // Compact single-line header
private const val OAL_GAP_PT       = 6f     // Gap from header rule to OAL line
private const val OAL_LINE_SPACE_PT = 90f   // OAL line height above shaft top (≈1.25 in — raised so the dimension doesn't crowd the profile)

// Bubble geometry — sized to hold hand-written decimal readings (e.g. .016)
// Row spacing and leader routing are derived from these by geom/RunoutBubbleLayout.kt.
private const val BUBBLE_RADIUS_PT      = 23f  // 46 pt ≈ 0.64 inch diameter (roomy to hand-write a value in)
private const val BUBBLE_MIN_GAP_PT     = 5f   // Minimum clear distance between circle edges
private const val SHORT_LEADER_PT       = 18f  // Deepest shaft surface → top of bubble row 0

// Extra space below the last bubble row
private const val BUBBLE_GAP_PT         = 8f

// Body compression break (matches ShaftPdfComposer threshold)
private const val COMPRESS_TRIGGER_PT = 220f

// Consolidated-sheet dimension rails (above the shaft; compact versions of the
// schematic's lane constants so the rails, bubbles, and footer share one page).
private const val RUNOUT_BASE_DIM_OFFSET_PT = 22f
private const val RUNOUT_RAIL_GAP_PT = 18f
private const val RUNOUT_DIM_TEXT_PT = 8.5f

// Classic central gap; breakPairLayout may widen it to keep the pair clear.
private const val ZIGZAG_GAP_MAX_PT   = 20f


