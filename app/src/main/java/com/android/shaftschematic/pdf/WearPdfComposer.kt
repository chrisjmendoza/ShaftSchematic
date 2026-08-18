package com.android.shaftschematic.pdf

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.android.shaftschematic.model.*
import com.android.shaftschematic.geom.DiaCalloutStation
import com.android.shaftschematic.geom.PlacedDiaCallout
import com.android.shaftschematic.geom.SetPositions
import com.android.shaftschematic.geom.WearTraceReading
import com.android.shaftschematic.geom.WearTraceVertex
import com.android.shaftschematic.geom.WEAR_TRACE_MAX_DEPTH_FRAC
import com.android.shaftschematic.geom.buildWearTrace
import com.android.shaftschematic.geom.computeOalWindow
import com.android.shaftschematic.geom.computeSetPositionsInMeasureSpace
import com.android.shaftschematic.geom.deepestWearDepthMm
import com.android.shaftschematic.geom.pitCenterY
import com.android.shaftschematic.geom.pitHalfArm
import com.android.shaftschematic.geom.planDiaCallouts
import com.android.shaftschematic.geom.sequenceWearTraces
import com.android.shaftschematic.geom.smoothWearTrace
import com.android.shaftschematic.settings.PDF_WEAR_BAND_SHADE_DEFAULT
import com.android.shaftschematic.settings.PDF_WEAR_BAND_SHADE_MAX
import com.android.shaftschematic.settings.PDF_WEAR_BAND_SHADE_MIN
import com.android.shaftschematic.settings.PdfPrefs
import com.android.shaftschematic.ui.resolved.ResolvedBody
import com.android.shaftschematic.ui.resolved.ResolvedComponent
import com.android.shaftschematic.ui.resolved.ResolvedLiner
import com.android.shaftschematic.ui.resolved.ResolvedTaper
import com.android.shaftschematic.util.DisplayUnits
import com.android.shaftschematic.util.VerboseLog
import com.android.shaftschematic.util.DualUnitLayout
import com.android.shaftschematic.util.drawDualLabelCentered
import com.android.shaftschematic.util.dualStackMetrics
import com.android.shaftschematic.util.measureDualLabel
import com.android.shaftschematic.util.setsStacked
import com.android.shaftschematic.util.UnitSystem
import com.android.shaftschematic.util.buildLinerTitleById
import com.android.shaftschematic.util.drawRichText
import com.android.shaftschematic.util.measureRichText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

// ──────────────────────────────────────────────────────────────────────────────
// Public entry point
// ──────────────────────────────────────────────────────────────────────────────

/**
 * WearPdfComposer
 *
 * Generates a shaft wear / inspection record PDF page.
 *
 * ## Purpose
 * A printable form the machinist uses in the field to mark damage, pitting, and
 * dye-penetrant inspection results on a simplified shaft outline. The interior is
 * left largely blank for hand annotation, but any wear recorded in-app is printed at
 * its true position: liner wear bands (marked) and pit "X" markers (small/large) on
 * bodies, tapers, and liners — see "Wear Pits" in `docs/contracts/RunoutSheet.md`.
 *
 * ## Page layout (landscape US Letter, 792 × 612 pt)
 * ```
 * ┌─── header: Customer / Vessel / Job# / Date / Side ────────────────────────┐
 * │   ←────────── OAL (AFT SET → FWD SET) ─────────────────────────→         │
 * │                                                                             │
 * │   [shaft profile — large, centred, blank interior for hand annotation]     │
 * │                                                                             │
 * │   Dye pen inspection:  PASS □   FAIL □     Notes: ____________________    │
 * └─────────────────────────────────────────────────────────────────────────────┘
 * ```
 *
 * @param page    Target PDF page (US Letter landscape, already started).
 * @param spec    Shaft specification in millimeters.
 * @param project Job information (customer, vessel, job#, side).
 * @param unit    Display unit for the OAL dimension label.
 * @param resolvedComponents Resolved component list from the ViewModel. When provided,
 *                resolved bodies replace `spec.bodies` for the drawn profile — same
 *                contract as `composeShaftPdf`/`composeRunoutPdf`.
 * @param wearRecord Recorded liner wear spots + pit "X" markers, plus this job's sheet shape:
 *                [WearRecord.showShaftProfile] keeps or drops the whole-shaft profile band (kept
 *                by default, on top, so body/taper/liner pits stay visible) and
 *                [WearRecord.stripComponentIds] elects which components get a detail strip
 *                below — liners, tapers, and bodies (explicit or auto), defaulting to **every
 *                drawable liner, with or without recorded wear** (the shop's normal operating
 *                procedure). An elected taper joins its nearest elected liner in one combined
 *                strip ([collectWearStripWindows]). Both fields are read from the passed record
 *                even in blank-draft mode: a write-in sheet blanks values, never the drawing's
 *                shape. The layout is picked by a [WearPdfMode] from
 *                `determineWearPdfMode(collectWearStripWindows(...).size)`:
 *                - **0 strips** ([WearPdfMode.PROFILE_FORM]) — profile only (still shows any
 *                  recorded pits).
 *                - **1 strip** ([WearPdfMode.COMBINED]) — profile + one full-width detail
 *                  strip below.
 *                - **2+ strips** ([WearPdfMode.GRID]) — profile + detail strips packed into rows
 *                  by their ACTUAL drawn width ([packWearStripWindows]): whitespace shrinks to its
 *                  floor before the shared scale does, so a row carries as many strips as its
 *                  width allows (up to [WEAR_STRIP_MAX_PER_ROW]). The row budget is
 *                  [wearStripMaxRows] — two with the profile on the page, three without, since
 *                  hiding the shaft frees a whole band. The single-column path caps at
 *                  [WEAR_STRIP_MAX_PER_PAGE]. Any remainder is a "+N more" text note (see
 *                  `selectWearStripWindowsForPage`'s KDoc for the single-page constraint).
 *                Defaults to empty so every existing call site is unaffected.
 */
fun composeWearPdf(
    page: PdfDocument.Page,
    spec: ShaftSpec,
    project: ProjectInfo,
    unit: UnitSystem,
    /**
     * Per-component display-unit overrides + the sheet-wide dual (inline "primary [secondary]")
     * flag. Defaults to a single-unit resolver equivalent to [unit] everywhere, reproducing
     * today's output exactly. A measured-Ø reading or a strip's anchor dimension resolves its
     * own unit via [DisplayUnits.unitFor]; a value with no component (the OAL line) uses
     * [DisplayUnits.documentUnit] instead.
     */
    displayUnits: DisplayUnits = DisplayUnits.single(unit),
    pdfPrefs: PdfPrefs = PdfPrefs(),
    resolvedComponents: List<ResolvedComponent>? = null,
    wearRecord: WearRecord = WearRecord(),
    lineThicknessScale: Float = 1.0f,
    /**
     * Blank-draft (write-in) mode: header job info and the OAL value are blanked, and any
     * recorded wear (bands, pits, measured-Ø readings) is omitted — but the shaft profile AND every
     * liner's zoomed detail strip still render, with the dimension lines kept and their values
     * left out (rail labels omitted, the anchor-from-SET value replaced by a writing rule with
     * "AFT / FWD" printed for circling one) so the sheet prints as a fresh inspection form the
     * machinist fills in by hand (matching the blank schematic's lines-in/values-out
     * posture).
     */
    blankValues: Boolean = false,
    /**
     * Worn-profile trace exaggeration for this sheet: how deep the record's deepest valued liner
     * reading draws, as a fraction of the drawn radius (`geom/WearTraceMath.kt`). Resolve it at
     * the call site with `effectiveWearTraceDepthFrac(wearRecord.traceDepthFrac,
     * pdfPrefs.wearTraceDepthFrac)` — the SAME value the detail overlay draws with, so the two
     * sites stay identical. Defaults to the shipped high end.
     */
    traceDepthFrac: Float = WEAR_TRACE_MAX_DEPTH_FRAC,
) {
    val c = page.canvas
    c.drawColor(Color.WHITE)

    val docSpec = spec.withResolvedBodies(resolvedComponents)
    val effectiveRecord = if (blankValues) WearRecord() else wearRecord
    // Shared liner display titles — custom label wins, else positional AFT/MID/FWD defaults
    // (util/LinerTitles.kt). Same names the carousel cards and runout sheet show, so the
    // printed sheet and the app always agree.
    val linerTitles = buildLinerTitleById(docSpec)

    val pageW = page.info.pageWidth.toFloat()
    val pageH = page.info.pageHeight.toFloat()

    // ── Paints ──────────────────────────────────────────────────────────────
    val thicknessScale = lineThicknessScale.coerceIn(0.5f, 2.0f)
    val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = WEAR_OUTLINE_PT * thicknessScale; color = Color.BLACK
    }
    val dim = Paint(outline).apply { strokeWidth = WEAR_DIM_PT * thicknessScale }
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; textSize = WEAR_TEXT_PT
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        color = Color.BLACK
    }
    fun shadeFill() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(40, 0, 0, 0)
    }
    // Stacked dual values, WANTED (the pref) — whether the sheet actually gets them is decided once
    // the strip bands are known (§7 degradation, `docs/DualUnitStacking_PLAN.md`).
    val wantDualStacked = displayUnits.dual && pdfPrefs.dualUnitLayout == DualUnitLayout.STACKED
    val bodyFill : Paint? = if (pdfPrefs.shadedBodies) shadeFill() else null
    val taperFill: Paint? = if (pdfPrefs.shadedTapers) shadeFill() else null
    val linerFill: Paint? = if (pdfPrefs.shadedLiners) shadeFill() else null
    // Pit "X" markers — solid black crossed strokes, round caps (the hand-drawn look).
    val pitPaint = Paint(outline).apply {
        strokeWidth = WEAR_OUTLINE_PT * thicknessScale; strokeCap = Paint.Cap.ROUND
    }
    // Measured-Ø callout value labels — small, same family as the body text.
    val diaText = Paint(text).apply { textSize = WEAR_DIA_TEXT_PT }

    // ── Page geometry ─────────────────────────────────────────────────────
    val margin       = WEAR_MARGIN_PT
    val contentLeft  = margin
    val contentRight = pageW - margin
    val contentW     = contentRight - contentLeft
    val contentTop   = margin
    val contentBot   = pageH - margin

    // The blank write-in header is taller (handwriting room for the rules, device
    // feedback); the space it costs is reclaimed from the profile→strips gap below.
    val headerBottom = contentTop + (if (blankValues) WEAR_HEADER_HEIGHT_BLANK_PT else WEAR_HEADER_HEIGHT_PT)

    // Notes anchored near the page bottom
    val notesY = contentBot - WEAR_NOTES_BOTTOM_OFFSET_PT

    // Full vertical band available to the shaft profile before any wear strips
    // are carved out of it.
    val midTopFull = headerBottom + WEAR_HEADER_GAP_PT
    val midBotFull = notesY - WEAR_NOTES_GAP_PT

    // ── Compute scale ────────────────────────────────────────────────────
    // Scale to the SET-to-SET span so the drawn shaft profile fills the page width.
    // Computed here (before the strip vertical split) so the actual drawn shaft
    // radius can be folded into the profile's minimum height below.
    val oalWindow      = computeOalWindow(spec)
    val setPositions   = computeSetPositionsInMeasureSpace(oalWindow, spec)
    val aftSetMm       = setPositions.aftSETxMm.toFloat()
    val fwdSetMm       = setPositions.fwdSETxMm.toFloat()
    val drawSpanMm     = (fwdSetMm - aftSetMm).coerceAtLeast(1f)
    val ptPerMm        = contentW / drawSpanMm
    val measureStartMm = aftSetMm

    fun xAt(mm: Float): Float = contentLeft + (mm - measureStartMm) * ptPerMm
    fun rPx(diaMm: Float): Float = (diaMm * 0.5f) * ptPerMm

    val maxDiaMm = (docSpec.maxOuterDiaMm().takeIf { it > 0f } ?: 50f).coerceAtLeast(20f)

    // ── Wear strip selection (pure — pdf/WearStripLayout.kt) ──────────────────
    // The job's elected components, defaulting to EVERY drawable liner, aft→fwd, with or without
    // recorded wear (normal shop procedure; also what makes the blank write-in template render
    // all zoomed strips). GRID mode (2+ strips) packs them into width-filled rows; the
    // single-column path (0-1 strips) uses the fixed cap.
    //
    // The election and the profile toggle read `wearRecord`, not `effectiveRecord`: a blank
    // write-in draft blanks the VALUES, never the sheet's shape — the machinist writes into the
    // same drawing the printed sheet has.
    val showProfile = wearRecord.showShaftProfile
    // Strip windows: one per elected component, except that an elected taper joins its nearest
    // elected liner in a combined window (see `collectWearStripWindows`). A liner-only election
    // — the default — makes one single-component window per liner, exactly the historical sheet.
    val stripComponents = wearStripComponentsFor(spec, resolvedComponents)
    val stripTitles = buildWearStripTitleById(spec, stripComponents)
    val windows = collectWearStripWindows(
        stripComponents, wearRecord.stripComponentIds, pdfPrefs.wearJoinGapMaxMm,
    )
    // Liner groups still drive the MAIN profile's wear bands and liner names; the elected liners
    // are exactly the liners the windows hold.
    val wearGroups = collectWearLinerGroups(docSpec.liners, effectiveRecord, wearRecord.stripComponentIds)
    val wearMode   = determineWearPdfMode(windows.size)
    // GRID pages pack by ACTUAL drawn width (packWearStripWindows): rows are filled to the page's
    // width, whitespace is squeezed to its floor before the shared scale gives way, and the row
    // budget follows the profile toggle — two rows with the shaft on the page, three without,
    // since hiding it frees a whole band. The packing therefore decides what fits, so it runs
    // BEFORE the overflow note reserves its height. The single-column paths keep their fixed cap.
    val packing = if (wearMode == WearPdfMode.GRID) packWearStripWindows(
        windows, contentLeft, contentRight, maxRows = wearStripMaxRows(showProfile),
    ) else null
    val stripSelection = if (packing != null)
        WearStripWindowSelection(windows.take(packing.placedCount), windows.drop(packing.placedCount))
    else selectWearStripWindowsForPage(windows, WEAR_STRIP_MAX_PER_PAGE)
    val onPage         = stripSelection.onPage
    val onPageLinerIds = onPage.mapNotNull { it.liner?.id }.toSet()
    val onPageComponentIds = onPage.flatMap { w -> w.components.map { it.id } }.toSet()
    val onPageGroups   = wearGroups.filter { it.liner.id in onPageLinerIds }
    val overflowNoteH  = if (stripSelection.overflow.isNotEmpty()) WEAR_OVERFLOW_NOTE_HEIGHT_PT else 0f
    // The page's largest reference diameter fills its strip band; every other strip draws at
    // its true ratio to it (wearStripHeightFrac), so heights read proportional on paper.
    // Feeds both the strips' drawn radii and the gutter-spread radius bound.
    val pageMaxRefDiaMm = onPage.maxOfOrNull { it.refDiaMm } ?: 0f

    // ── Measured-Ø callouts under the MAIN profile (body/taper readings only — liner
    // readings print on their detail strip at the zoomed scale). Planned here, before the
    // vertical split, so the profile band can reserve the label rows' height; drawn after
    // the profile once shaftCy is fixed. Orphans (unresolved componentId) and value-less
    // readings are skipped at this render layer, same posture as pits.
    // A body/taper reading whose component HAS a strip on this page draws in that strip instead,
    // at the zoomed scale — the placement rule liners have always followed.
    val profileDia = if (showProfile && resolvedComponents != null) {
        // Measured INLINE deliberately, even when the sheet may stack: the horizontal solve runs
        // before the strips are laid out, so it cannot yet know whether stacking survives §7's
        // fallback. Inline is the WIDER form, so a stacked label always fits the slot a plan solved
        // for an inline one. Stacking simply does not earn fewer rows here, which is the cheap side
        // of the trade — the reserved band below is sized for the taller row either way.
        buildProfileDiaCalloutInput(
            effectiveRecord.diaReadings, resolvedComponents, ::xAt, diaText, displayUnits,
            skipComponentIds = onPageComponentIds,
        )
    } else ProfileDiaCalloutInput(emptyList(), emptyMap())
    val profileDiaPlan = if (profileDia.stations.isEmpty()) null else
        planDiaCallouts(profileDia.stations, contentLeft, contentRight, WEAR_DIA_MIN_GAP_PT)
    // Reserved at the STACK height whenever stacking is wanted, before §7's per-sheet fallback is
    // known: reserving the taller row costs a few points of white if the sheet ends up inline, while
    // reserving the shorter one would overlap the rows if it ends up stacked.
    val profileDiaRowHeightPt =
        if (wantDualStacked) diaText.dualStackMetrics().height else diaText.textSize
    val profileDiaBandPt = profileDiaPlan?.bandHeightPt(
        profileDiaRowHeightPt, WEAR_DIA_ROW_GAP_PT, WEAR_DIA_PROFILE_LEADER_PT) ?: 0f

    // ── Header (always drawn) ───────────────────────────────────────────────
    drawWearHeader(c, text, contentLeft, contentRight, contentTop, project, blankValues)

    // The profile's minimum height also protects the actual drawn shaft radius — ptPerMm is a
    // purely horizontal (SET-to-SET) scale, so a wide/short shaft's true diameter could otherwise
    // exceed a shrunk profile band. A hidden profile band claims no height at all: zero floor,
    // zero preference, and no profile→strips gap, so the strips start at the top of the band
    // instead of leaving a phantom gap where the profile would have been.
    val minProfileHeightPt = if (!showProfile) 0f else
        maxOf(WEAR_MIN_PROFILE_HEIGHT_PT, 2f * rPx(maxDiaMm) + WEAR_PROFILE_RADIUS_MARGIN_PT)

    // Content-derived profile height: OAL label region + OAL clearance + shaft + names row.
    // The band shrinks toward this (never below minProfileHeightPt) and the strips absorb
    // the reclaimed height (device feedback: dead white between shaft and strips).
    val preferredProfileHeightPt = if (!showProfile) 0f else maxOf(
        WEAR_OAL_TOP_REGION_PT + WEAR_OAL_ABOVE_SHAFT_PT + 2f * rPx(maxDiaMm) +
            WEAR_PROFILE_NAMES_ROW_PT + profileDiaBandPt + WEAR_PROFILE_BOTTOM_PAD_PT,
        minProfileHeightPt,
    )

    // Vertical layout + per-strip cells depend on mode: GRID lays strips two-up (profile keeps the
    // top of the page); otherwise a single full-width column below the profile (0 strips = profile
    // only, unchanged). Both keep the shaft profile on top.
    val profileTop: Float
    val profileBottom: Float
    val stripCells: List<WearStripCell>
    val overflowBandTop: Float
    // Per-window outward room for the stub-end break curls (left, right), aligned with
    // `onPage` — this end's share of the (spread) gutter beside it, MAX_VALUE where nothing
    // bounds the void side. See `spreadWearStripRowGutters`/`wearStripBreakAmplitudePt`.
    val stripBreakRooms: List<Pair<Float, Float>>
    // The blank template's taller header is paid for here: its profile→strips gap shrinks
    // (WEAR_STRIP_TOP_GAP_BLANK_PT) instead of the strips themselves getting shorter.
    val profileToStripsGap = when {
        !showProfile -> 0f
        blankValues -> WEAR_STRIP_TOP_GAP_BLANK_PT
        else -> WEAR_STRIP_TOP_GAP_PT
    }
    // Single-column path only: with no profile band there is nothing for a capped strip's leftover
    // height to flow back to, so the cap lifts and the lone COMBINED strip fills the page instead
    // of stranding white at the top.
    val maxStripHeightPt = if (showProfile) WEAR_STRIP_HEIGHT_MAX_PT else Float.MAX_VALUE
    if (packing != null) {
        // The packer owns the horizontal split (one cell per window, rows centered); the vertical
        // band is the same count-driven split the fixed grid makes internally, fed the PACKED row
        // count instead of ceil(strips / 2).
        //
        // Per-row height cap: with the profile shown the fixed cap holds and the profile band
        // absorbs the slack (its shaft slack-centered inside it). With the profile hidden the
        // rows OWN the band — a multi-row page splits the whole height between its rows, so the
        // cap must never bite there (capping every packed row stranded the bottom half of the
        // page as dead white under two top-pinned rows, on-device report). Only a LONE row keeps
        // a guard, at the height a two-row page would give it, so it still cannot stretch into a
        // short fat cylinder; its leftover height goes to the page bottom via the lift below.
        val packedRowCap = if (showProfile) WEAR_STRIP_HEIGHT_MAX_PT else {
            val bandH = (midBotFull - overflowNoteH - midTopFull).coerceAtLeast(0f)
            maxOf(WEAR_STRIP_HEIGHT_MAX_PT, (bandH - WEAR_STRIP_GAP_PT) / 2f)
        }
        val v = computeWearVerticalLayout(
            midTopFull, midBotFull, packing.rowCount,
            reservedBottomPt = overflowNoteH, minProfileHeightPt = minProfileHeightPt,
            profileToStripsGapPt = profileToStripsGap,
            preferredProfileHeightPt = preferredProfileHeightPt,
            maxStripHeightPt = packedRowCap,
        )
        val lift = if (showProfile) 0f else (v.stripTops.firstOrNull() ?: midTopFull) - midTopFull

        // Facing S-break curls must never cross a gutter: widen each row's gutters to what
        // its facing break ends need at the radius these rows will actually draw, funded by
        // the slack a centered row parks at the margins (`spreadWearStripRowGutters`). The
        // radius bound is the tallest cylinder a strip this tall can draw (no Ø-callout
        // band) — an overestimate only ever buys a little extra daylight.
        val rowH = ((v.stripBottoms.firstOrNull() ?: 0f) - (v.stripTops.firstOrNull() ?: 0f))
            .coerceAtLeast(0f)
        val innerBound = computeWearStripInnerLayout(
            0f, rowH, titleHeightPt = (text.textSize - 1f).coerceAtLeast(7f),
        )
        val stubRBoundPt = ((innerBound.cylBottom - innerBound.cylTop) / 2f).coerceAtLeast(0f)
        fun endReachPt(windowIdx: Int, aftSide: Boolean): Float {
            val w = onPage[windowIdx]
            val style = wearStripEndStyle(docSpec, if (aftSide) w.startMm else w.endMm, aftSide)
            return if (style == WearStripEndStyle.BREAK)
                BREAK_EDGE_OUTWARD_REACH_FRAC * (WEAR_STRIP_BREAK_AMP_FRAC * stubRBoundPt) *
                    wearStripHeightFrac(w.refDiaMm, pageMaxRefDiaMm)
            else 0f
        }
        val spreadCells = spreadWearStripRowGutters(packing.cells, contentLeft, contentRight) { li, ri ->
            val reach = endReachPt(li, aftSide = false) + endReachPt(ri, aftSide = true)
            if (reach <= 0f) 0f
            else reach + outline.strokeWidth + WEAR_STRIP_GUTTER_DAYLIGHT_PT
        }
        // Each end's share of the gutter beside it, proportional to its curl's reach — the
        // amplitude backstop for a row whose slack couldn't fund the full widening.
        val roomL = FloatArray(onPage.size) { Float.MAX_VALUE }
        val roomR = FloatArray(onPage.size) { Float.MAX_VALUE }
        spreadCells.groupBy { it.row }.forEach { (_, row) ->
            val ordered = row.sortedBy { it.left }
            for (k in 0 until ordered.size - 1) {
                val a = ordered[k]; val b = ordered[k + 1]
                val reachA = endReachPt(a.windowIndex, aftSide = false)
                val reachB = endReachPt(b.windowIndex, aftSide = true)
                if (reachA + reachB <= 0f) continue
                val usable = (b.left - a.right - WEAR_STRIP_GUTTER_DAYLIGHT_PT).coerceAtLeast(0f)
                roomR[a.windowIndex] = usable * reachA / (reachA + reachB)
                roomL[b.windowIndex] = usable * reachB / (reachA + reachB)
            }
        }
        stripBreakRooms = onPage.indices.map { roomL[it] to roomR[it] }

        profileTop = v.profileTop; profileBottom = v.profileBottom - lift
        stripCells = spreadCells.map { cell ->
            WearStripCell(
                v.stripTops[cell.row] - lift, v.stripBottoms[cell.row] - lift, cell.left, cell.right,
            )
        }
        overflowBandTop = (v.stripBottoms.lastOrNull() ?: v.profileBottom) - lift
    } else {
        val v = computeWearVerticalLayout(
            midTopFull, midBotFull, onPage.size,
            reservedBottomPt = overflowNoteH, minProfileHeightPt = minProfileHeightPt,
            profileToStripsGapPt = profileToStripsGap,
            preferredProfileHeightPt = preferredProfileHeightPt,
            maxStripHeightPt = maxStripHeightPt,
        )
        profileTop = v.profileTop; profileBottom = v.profileBottom
        stripCells = onPage.indices.map { i ->
            WearStripCell(v.stripTops[i], v.stripBottoms[i], contentLeft, contentRight)
        }
        // Single-column strips are alone in their row — nothing bounds their break curls.
        stripBreakRooms = onPage.indices.map { Float.MAX_VALUE to Float.MAX_VALUE }
        overflowBandTop = v.stripBottoms.lastOrNull() ?: profileBottom
    }

    // §7 degradation, wear sheet: the strips are the tightest vertical budget in the app, so the
    // stacked decision is taken ONCE here against the tightest strip on the page and applied to all
    // of them — a page with some two-line and some one-line values reads as a mistake. A strip that
    // cannot carry stacked rail rows and still draw a readable liner sends the whole sheet inline.
    val dualStacked = wantDualStacked && run {
        val probe = Paint(text).apply { textSize = stripDimTextSize(text) }
        val tightest = stripCells.minOfOrNull { it.bottom - it.top } ?: Float.MAX_VALUE
        wearStripAffordsStackedRail(
            stripHeightPt = tightest,
            titleHeightPt = stripTitleTextSize(text),
            rowHeightPt = wearRailRowHeightPt(probe, dualStacked = true),
        )
    }
    if (wantDualStacked && !dualStacked) {
        VerboseLog.i(VerboseLog.Category.PDF, "WearPdf") {
            "dual stacking: wear strips fell back to INLINE — the tightest strip cannot carry " +
                "stacked rail rows and still draw its liner"
        }
    }

    // ── Shaft profile + OAL line ──────────────────────────────────────────────
    // Elected out (showShaftProfile = false) the whole band is skipped — profile, OAL rail,
    // on-profile bands/pits, liner names, and the direction reference — and the strips already
    // hold its height. Header, strips, and the dye-pen row are unaffected.
    if (showProfile) {
        // Anchor the shaft snugly under the OAL region instead of centering it in the band; when
        // the band is larger than its content needs (0-1 strips, capped strip growth), the slack
        // is split evenly so the roomy case still reads centered.
        // Clamped so a squeezed band (huge shaft OD on a short page) keeps the shaft above the names row instead of overlapping the strips.
        val profileSlack = ((profileBottom - profileTop) - preferredProfileHeightPt).coerceAtLeast(0f)
        val shaftCy = (profileTop + WEAR_OAL_TOP_REGION_PT + WEAR_OAL_ABOVE_SHAFT_PT +
            rPx(maxDiaMm) + profileSlack * 0.5f)
            .coerceAtMost(profileBottom - rPx(maxDiaMm) - WEAR_PROFILE_NAMES_ROW_PT - profileDiaBandPt)
        val geomRect = RectF(contentLeft, profileTop, contentRight, profileBottom)
        val shaftTopApprox = shaftCy - rPx(maxDiaMm)
        val oalLineY = (shaftTopApprox - WEAR_OAL_ABOVE_SHAFT_PT).coerceAtLeast(profileTop + WEAR_TEXT_PT + 6f)

        // Label rule: the printed value is always the user's typed OAL (same as the main schematic),
        // shown bare (no "OAL" prefix — the end-to-end span already implies it); the arrows below
        // bracket the drawn SET-to-SET span. Blank draft: no label at all, just an empty writable
        // break mid-span (drawWearOalLine).
        drawWearOalLine(
            c, dim, text, contentLeft, contentRight, oalLineY, shaftTopApprox,
            displayUnits.documentUnit, spec.overallLengthMm, blankValues, dual = displayUnits.dual,
        )
        drawWearShaftProfile(c, docSpec, shaftCy, outline, geomRect, ::xAt, ::rPx,
            bodyFill = bodyFill, taperFill = taperFill, linerFill = linerFill, ptPerMm = ptPerMm)

        // Vertical-stroke wear bands (liner spots) + pit "X"s at true position on the profile. Bands
        // clamp to the liner span for rendering only; the stored data is never touched.
        drawWearBandsOnProfile(c, wearGroups, shaftCy, ::xAt, ::rPx, outline)
        if (resolvedComponents != null) {
            drawWearPitsOnProfile(c, effectiveRecord.pits, resolvedComponents, shaftCy, ::xAt, ::rPx, pitPaint)
        }

        // Shaft-direction reference so anyone reading the sheet knows the layout: AFT is drawn at the
        // left, FWD at the right (the schematic/SET convention).
        drawWearDirectionRef(c, text, contentLeft, contentRight, shaftCy + rPx(maxDiaMm), profileBottom)

        // Liner names centered under their span on the main profile — a reference tying each wear band
        // to its broken-out (zoomed) strip below. Only the liners that get a strip on this page.
        drawWearLinerNamesOnProfile(c, onPageGroups, shaftCy + rPx(maxDiaMm), profileBottom, contentLeft, contentRight, ::xAt, text, linerTitles)

        // Measured-Ø callouts (body/taper readings): leader from the drawn bottom surface at the
        // station down to the value, in the band reserved below the names/direction row. Same
        // engine + construction as the detail strips and the overlay canvas.
        if (profileDiaPlan != null) {
            val placed = profileDiaPlan.finish(
                row0Top = shaftCy + rPx(maxDiaMm) + WEAR_PROFILE_NAMES_ROW_PT + 2f,
                labelTextHeight = profileDiaRowHeightPt,
                rowGap = WEAR_DIA_ROW_GAP_PT,
                surfaceYAt = { i ->
                    shaftCy + rPx(profileDia.surfaceDiaByKey[profileDiaPlan.stations[i].key] ?: maxDiaMm)
                },
                leaderStartGap = 2f,
            )
            drawDiaCallouts(c, placed, dim, diaText, dualStacked)
        }
    }

    // ── Per-liner detail strips ─────────────────────────────────────────────
    // Worn-profile trace baseline (geom/WearTraceMath.kt): the deepest valued reading on ANY
    // liner, computed ONCE so every strip on the sheet exaggerates against the same worst
    // wear. Body/taper readings never trace, so they stay out of the baseline.
    val linerOdMmById = docSpec.liners.associate { it.id to it.odMm }
    val deepestLinerWearMm = deepestWearDepthMm(
        effectiveRecord.diaReadings.mapNotNull { r ->
            val odMm = linerOdMmById[r.componentId] ?: return@mapNotNull null
            odMm to r.diaMm
        }
    )
    // ONE mm→pt scale across every strip on the page, so relative component lengths read true —
    // a 22" liner draws half the width of a 44" one instead of both filling their own cells.
    // A packed page's scale comes out of the packer, which solved it against the whole page width;
    // the single-column paths fit each window to its own full-width cell (a window's compressed
    // gaps spend a fixed number of points whatever the scale, so they come off the cell first).
    val stripStubWidthPt = packing?.spacing?.stubWidthPt ?: WEAR_STRIP_STUB_WIDTH_PT
    val stripPtPerMm = packing?.ptPerMm ?: sharedWearStripWindowPtPerMm(
        windows = onPage,
        innerWidthsPt = stripCells.map { it.right - it.left - 2f * WEAR_STRIP_STUB_WIDTH_PT },
    )
    val spotsByLinerId = effectiveRecord.spots.groupBy { it.linerId }
    onPage.forEachIndexed { i, window ->
        val cell = stripCells[i]
        val ids = window.components.map { it.id }.toSet()
        drawWearStripWindow(
            c, docSpec, window, cell.top, cell.bottom, cell.left, cell.right,
            displayUnits, setPositions, text, outline, dim,
            stripPtPerMm = stripPtPerMm,
            stubWidthPt = stripStubWidthPt,
            breakRoomLeftPt = stripBreakRooms[i].first,
            breakRoomRightPt = stripBreakRooms[i].second,
            heightFrac = wearStripHeightFrac(window.refDiaMm, pageMaxRefDiaMm),
            titles = stripTitles,
            spots = window.liner?.let { spotsByLinerId[it.id] }.orEmpty(),
            pits = effectiveRecord.pits.filter { it.componentId in ids },
            diaReadings = effectiveRecord.diaReadings.filter { it.componentId in ids },
            deepestWearDepthMm = deepestLinerWearMm,
            traceDepthFrac = traceDepthFrac,
            bandShadeAlpha = wearBandShadeAlpha(pdfPrefs.wearBandShadeFrac),
            blankValues = blankValues,
            dualStacked = dualStacked,
        )
    }
    if (stripSelection.overflow.isNotEmpty()) {
        drawWearOverflowNote(c, text, contentLeft, contentRight, overflowBandTop, midBotFull, stripSelection.overflow, stripTitles)
    }

    // ── Notes / dye-pen area ──────────────────────────────────────────────
    // effectiveRecord, not wearRecord: a blank write-in draft keeps both boxes empty.
    drawWearNotesArea(c, text, contentLeft, contentRight, notesY, effectiveRecord.dyePenResult)
}

// ──────────────────────────────────────────────────────────────────────────────
// Header
// ──────────────────────────────────────────────────────────────────────────────

private fun drawWearHeader(
    c: Canvas,
    text: Paint,
    left: Float,
    right: Float,
    top: Float,
    project: ProjectInfo,
    blankValues: Boolean = false,
) {
    val ts = text.textSize

    fun centeredX(str: String): Float =
        ((left + right - text.measureText(str)) * 0.5f).coerceAtLeast(left)

    if (blankValues) {
        // Blank draft: the five job-info fields spread edge-to-edge across the full content
        // width with equal writing rules — handwriting room; the OAL belongs to the drawing's
        // end-to-end span (drawWearOalLine), not the header, so it is never printed here.
        val line1Y = top + ts + 4f
        val line2Y = line1Y + WEAR_HEADER_BLANK_LINE_GAP_PT
        val labels = listOf("Customer:", "Vessel:", "Job #:", "Date:", "Side:")
        val labelsW = labels.map { text.measureText(it) }.sum()
        // drawLabelWithRule inserts 4f label→rule and returns ruleEnd + 14f (inter-field gap).
        val ruleW = ((right - left - labelsW - labels.size * 4f - (labels.size - 1) * 14f) / labels.size)
            .coerceAtLeast(BLANK_RULE_PT * 0.5f)
        var x = left
        labels.forEach { label -> x = drawLabelWithRule(c, label, x, line1Y, text, ruleWidth = ruleW, maxRight = right) }

        val line2 = "WEAR / INSPECTION RECORD"
        c.drawText(line2, centeredX(line2), line2Y, text)
    } else {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val side = project.side.printableLabelOrNull()?.let { "  $it" } ?: ""

        val line1 = buildString {
            if (project.customer.isNotBlank()) append("Customer: ${project.customer}   ")
            if (project.vessel.isNotBlank())   append("Vessel: ${project.vessel}   ")
            if (project.jobNumber.isNotBlank()) append("Job #: ${project.jobNumber}   ")
            append("Date: $date$side")
        }
        // The OAL belongs to the drawing's end-to-end span (drawWearOalLine); the header never
        // repeats it.
        val line2 = "WEAR / INSPECTION RECORD"

        val line1Fit = ellipsizeToWidth(line1, text, right - left)
        val line2Fit = ellipsizeToWidth(line2, text, right - left)
        c.drawText(line1Fit, centeredX(line1Fit), top + ts, text)
        c.drawText(line2Fit, centeredX(line2Fit), top + ts + ts * 1.4f, text)
    }

    val ruleY = top + (if (blankValues) WEAR_HEADER_HEIGHT_BLANK_PT else WEAR_HEADER_HEIGHT_PT)
    c.drawLine(left, ruleY, right, ruleY, Paint(text).apply {
        style = Paint.Style.STROKE; strokeWidth = 0.5f
    })
}

// ──────────────────────────────────────────────────────────────────────────────
// OAL line
// ──────────────────────────────────────────────────────────────────────────────

private fun drawWearOalLine(
    c: Canvas, dim: Paint, text: Paint,
    x0: Float, x1: Float,
    oalLineY: Float, shaftTopY: Float,
    unit: UnitSystem, oalMm: Float,
    blankValues: Boolean = false,
    /** OAL is a whole-shaft dimension, not component-keyed — one dual flag for the sheet. */
    dual: Boolean = false,
) {
    val arrowLen    = 8f
    val witnessGap  = 3f   // gap between shaft edge and witness line start
    val witnessExt  = 5f   // how far the witness line extends past the dimension line

    // Witness (extension) lines from shaft top up to past the dimension line
    c.drawLine(x0, shaftTopY - witnessGap, x0, oalLineY - witnessExt, dim)
    c.drawLine(x1, shaftTopY - witnessGap, x1, oalLineY - witnessExt, dim)

    // Both modes cut a break mid-span — the schematic's dimension-value convention, kept
    // consistent across drawing outputs. Blank: an empty writable gap (no wording where
    // handwriting goes). Printed: the "OAL: value" label seats IN the gap, vertically
    // centred on the line (the small printed prefix is a deliberate visual identifier).
    val mid = (x0 + x1) * 0.5f
    if (blankValues) {
        val gapHalf = BLANK_DIM_GAP_PT * 0.5f
        c.drawLine(x0, oalLineY, mid - gapHalf, oalLineY, dim)
        c.drawLine(mid + gapHalf, oalLineY, x1, oalLineY, dim)
    } else {
        // Same formatter as the schematic's OAL rail — inches print as mixed fractions
        // (falling back to 3 decimals), never raw 4-decimal.
        val label = "OAL: ${formatLenDimDual(oalMm.toDouble(), unit, dual)}"
        val lw = text.measureRichText(label)
        val gapHalf = lw * 0.5f + DIM_BREAK_TEXT_PAD_PT
        if ((mid - gapHalf) - x0 >= arrowLen + 2f) {
            c.drawLine(x0, oalLineY, mid - gapHalf, oalLineY, dim)
            c.drawLine(mid + gapHalf, oalLineY, x1, oalLineY, dim)
            val fm = text.fontMetrics
            c.drawRichText(label, mid - lw * 0.5f, oalLineY - (fm.ascent + fm.descent) * 0.5f, text)
        } else {
            // Fallback for a span too short to host the break + inward arrows: continuous
            // line, label above — mirrors PdfDimensionRenderer's fallback rule.
            c.drawLine(x0, oalLineY, x1, oalLineY, dim)
            c.drawRichText(label, mid - lw * 0.5f, oalLineY - 4f, text)
        }
    }
    c.drawLine(x0, oalLineY, x0 + arrowLen, oalLineY - arrowLen * 0.4f, dim)
    c.drawLine(x0, oalLineY, x0 + arrowLen, oalLineY + arrowLen * 0.4f, dim)
    c.drawLine(x1, oalLineY, x1 - arrowLen, oalLineY - arrowLen * 0.4f, dim)
    c.drawLine(x1, oalLineY, x1 - arrowLen, oalLineY + arrowLen * 0.4f, dim)
}

/**
 * "← AFT" (left) and "FWD →" (right) direction labels under the shaft profile, so a shop reader
 * can orient the whole document. Drawn just below the shaft's bottom edge, clamped inside the
 * profile band. Matches the app-wide convention (AFT drawn left, FWD right) and the in-app wear
 * detail overlay's caption.
 */
private fun drawWearDirectionRef(
    c: Canvas,
    text: Paint,
    left: Float,
    right: Float,
    shaftBottomY: Float,
    bandBottom: Float,
) {
    val p = Paint(text)
    val y = (shaftBottomY + p.textSize + 12f).coerceAtMost(bandBottom - 2f)
    p.textAlign = Paint.Align.LEFT
    c.drawText("← AFT", left, y, p)
    p.textAlign = Paint.Align.RIGHT
    c.drawText("FWD →", right, y, p)
}

/**
 * Liner names centered under their span on the MAIN profile — a lightweight reference tying each
 * wear band to its broken-out (zoomed) strip below, so a reader can match "this band on the shaft"
 * to "that strip". Shares the row with the "← AFT" / "FWD →" direction labels: each name is
 * centered on its liner span but clamped clear of those edge-anchored labels. Uses the same shared
 * title (`buildLinerTitleById` — custom label or positional AFT/MID/FWD default) the strip title
 * shows, so the two always read the same.
 */
private fun drawWearLinerNamesOnProfile(
    c: Canvas,
    groups: List<WearLinerGroup>,
    shaftBottomY: Float,
    bandBottom: Float,
    contentLeft: Float,
    contentRight: Float,
    xAt: (Float) -> Float,
    text: Paint,
    linerTitles: Map<String, String>,
) {
    if (groups.isEmpty()) return
    val p = Paint(text).apply { textAlign = Paint.Align.CENTER }
    val y = (shaftBottomY + p.textSize + 12f).coerceAtMost(bandBottom - 2f)
    // Reserve the edge zones where "← AFT" / "FWD →" sit (drawn with the same [text] size).
    val loX = contentLeft + text.measureText("← AFT") + 10f
    val hiX = contentRight - text.measureText("FWD →") - 10f
    if (hiX <= loX) return
    groups.forEach { g ->
        val ln = g.liner
        if (ln.lengthMm <= 0f) return@forEach
        val name = linerTitles[ln.id] ?: "Liner"
        val cx = ((xAt(ln.startFromAftMm) + xAt(ln.startFromAftMm + ln.lengthMm)) * 0.5f).coerceIn(loX, hiX)
        c.drawText(ellipsizeToWidth(name, p, hiX - loX), cx, y, p)
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Shaft profile (same drawing logic as runout, just larger)
// ──────────────────────────────────────────────────────────────────────────────

private fun drawWearShaftProfile(
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
    // Bodies with compression breaks
    val capPaint = Paint(outline)
    spec.bodies.forEach { b ->
        if (b.lengthMm <= 0f || b.diaMm <= 0f) return@forEach
        val x0 = xAt(b.startFromAftMm); val x1 = xAt(b.startFromAftMm + b.lengthMm)
        val r = rPx(b.diaMm); val top = cy - r; val bot = cy + r
        val lenPt = abs(x1 - x0)
        if (lenPt < COMPRESS_TRIGGER_PT) {
            c.drawLine(x0, top, x1, top, outline); c.drawLine(x0, bot, x1, bot, outline)
            c.drawLine(x0, top, x0, bot, outline); c.drawLine(x1, top, x1, bot, outline)
        } else {
            val mid = (x0 + x1) * 0.5f
            val (gap, amp) = breakPairLayout(
                runLenPt = lenPt,
                desiredAmplitudePt = r * 0.6f,
                classicGapPt = min(ZIGZAG_GAP_MAX_PT, 0.25f * lenPt),
                strokeWidthPt = capPaint.strokeWidth,
            )
            val half = gap * 0.5f
            val lEnd = (mid - half).coerceIn(geomRect.left, geomRect.right)
            val rBeg = (mid + half).coerceIn(geomRect.left, geomRect.right)
            c.drawLine(x0, top, lEnd, top, outline); c.drawLine(x0, bot, lEnd, bot, outline)
            c.drawLine(x0, top, x0, bot, outline)
            drawBreakEdge(c, lEnd, top, bot, amp, capPaint, eyeAtTop = false)
            drawBreakEdge(c, rBeg, top, bot, amp, capPaint, eyeAtTop = true)
            c.drawLine(rBeg, top, x1, top, outline); c.drawLine(rBeg, bot, x1, bot, outline)
            c.drawLine(x1, top, x1, bot, outline)
        }
    }
    // Tapers
    spec.tapers.forEach { t ->
        if (t.lengthMm <= 0f || (t.startDiaMm <= 0f && t.endDiaMm <= 0f)) return@forEach
        val x0 = xAt(t.startFromAftMm); val x1 = xAt(t.startFromAftMm + t.lengthMm)
        val top0 = cy - rPx(t.startDiaMm); val bot0 = cy + rPx(t.startDiaMm)
        val top1 = cy - rPx(t.endDiaMm);   val bot1 = cy + rPx(t.endDiaMm)
        c.drawLine(x0, top0, x1, top1, outline); c.drawLine(x0, bot0, x1, bot1, outline)
        c.drawLine(x0, top0, x0, bot0, outline); c.drawLine(x1, top1, x1, bot1, outline)
    }
    // Liners
    val dimPaint = Paint(outline).apply { strokeWidth = WEAR_DIM_PT }
    spec.liners.forEach { ln ->
        if (ln.lengthMm <= 0f || ln.odMm <= 0f) return@forEach
        val x0 = xAt(ln.startFromAftMm); val x1 = xAt(ln.startFromAftMm + ln.lengthMm)
        val r = rPx(ln.odMm); val top = cy - r; val bot = cy + r
        c.drawLine(x0, top, x1, top, outline); c.drawLine(x0, bot, x1, bot, outline)
        c.drawLine(x0, top, x0, bot, dimPaint); c.drawLine(x1, top, x1, bot, dimPaint)
    }
    // Threads — outline envelope + diagonal hatch so the machinist knows the zone is threaded
    val hatchPaint = Paint(outline).apply { strokeWidth = WEAR_DIM_PT * 0.6f; alpha = 160 }
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

// ──────────────────────────────────────────────────────────────────────────────
// Wear bands (main profile) + per-liner detail strips
// ──────────────────────────────────────────────────────────────────────────────
//
// Layout math (selection/pagination, clamping, vertical/horizontal banding, the
// neighbor-diameter lookup, and the anchor-from-SET label) lives in the
// android-free pdf/WearStripLayout.kt so it's directly unit-testable — see
// WearStripLayoutTest. The functions below only do the Canvas drawing.

/**
 * Thin vertical-line bands on the MAIN shaft profile for every liner with recorded wear
 * spots, at their true axial position — "visible but not dominant" (proposal §6.2). The band
 * is filled with **vertical** strokes (matching how the shop marks wear areas by
 * hand — see the reference sketch); the broken-out detail strips, which are what pits get
 * hand-marked into, fill theirs a user-set light grey instead ([wearBandShadeAlpha]).
 * Bands are clamped to the liner's own span for rendering; the underlying [WearSpot] data is
 * never mutated.
 */
private fun drawWearBandsOnProfile(
    c: Canvas,
    groups: List<WearLinerGroup>,
    cy: Float,
    xAt: (Float) -> Float,
    rPx: (Float) -> Float,
    outline: Paint,
) {
    if (groups.isEmpty()) return
    val bandLines = Paint(outline).apply { strokeWidth = WEAR_DIM_PT * 0.5f; alpha = 120 }
    groups.forEach { g ->
        val ln = g.liner
        if (ln.lengthMm <= 0f || ln.odMm <= 0f) return@forEach
        val r = rPx(ln.odMm); val top = cy - r; val bot = cy + r
        g.spots.forEach { spot ->
            val clamp = clampWearBandToLiner(spot.startMm, spot.lengthMm, ln.lengthMm)
            if (clamp.lengthMm <= 0f) return@forEach
            val x0 = xAt(ln.startFromAftMm + clamp.startMm)
            val x1 = xAt(ln.startFromAftMm + clamp.startMm + clamp.lengthMm)
            drawVerticalBand(c, x0, x1, top, bot, bandLines, pitchPt = 6f)
        }
    }
}

// Printed pit sizes are the confirmed-good baseline (on-device clarification: only the detail
// OVERLAY drew its X's oversized — these PDF arms and their outline-width stroke were right).

/** SMALL pit half-arm (pt) on the main shaft profile; LARGE scales by the shared ratio. */
internal const val WEAR_PIT_SMALL_HALF_PROFILE_PT = 1.7f

/** SMALL pit half-arm (pt) on a broken-out detail strip (zoomed, so a touch larger). */
private const val WEAR_PIT_SMALL_HALF_STRIP_PT = 2.5f

/**
 * Pit "X" markers on the MAIN shaft profile, at each pit's true axial + across position. A pit
 * whose [WearPit.componentId] no longer resolves is skipped (orphan handling at the render layer,
 * same posture as runout readings). Taper diameter is interpolated at the pit's axial position so
 * the X lands on the sloped surface. Drawn identically (by construction) to the detail-canvas and
 * strip draw sites — see `geom/WearPitMath.kt` and `ui/screen/LinerWearDetail.kt`.
 *
 * Internal (not private): the consolidated runout sheet reuses this exact construction for
 * its migrated pit marks — [smallHalf] is the per-surface SMALL half-arm (pt on PDFs, px on
 * the preview canvas), the `WearPitMath` caller-picks-scale rule.
 */
internal fun drawWearPitsOnProfile(
    c: Canvas,
    pits: List<WearPit>,
    components: List<ResolvedComponent>,
    cy: Float,
    xAt: (Float) -> Float,
    rPx: (Float) -> Float,
    pitPaint: Paint,
    smallHalf: Float = WEAR_PIT_SMALL_HALF_PROFILE_PT,
) {
    if (pits.isEmpty()) return
    val byId = components.associateBy { it.id }
    pits.forEach { pit ->
        val rc = byId[pit.componentId] ?: return@forEach
        val lenMm = (rc.endMmPhysical - rc.startMmPhysical).coerceAtLeast(0.001f)
        val local = pit.axialMm.coerceIn(0f, lenMm)
        val diaMm = when (rc) {
            is ResolvedBody -> rc.diaMm
            is ResolvedLiner -> rc.odMm
            is ResolvedTaper -> {
                val t = (local / lenMm).coerceIn(0f, 1f)
                rc.startDiaMm + (rc.endDiaMm - rc.startDiaMm) * t
            }
            else -> return@forEach
        }
        if (diaMm <= 0f) return@forEach
        val cx = xAt(rc.startMmPhysical + local)
        val r = rPx(diaMm)
        val py = pitCenterY(cy - r, cy + r, pit.acrossFrac)
        drawWearPitX(c, cx, py, pitHalfArm(pit.size, smallHalf), pitPaint)
    }
}

/** A pit "X": two crossed strokes centred on `(cx, cy)`, half-arm [half]. */
private fun drawWearPitX(c: Canvas, cx: Float, cy: Float, half: Float, paint: Paint) {
    c.drawLine(cx - half, cy - half, cx + half, cy + half, paint)
    c.drawLine(cx - half, cy + half, cx + half, cy - half, paint)
}

/**
 * Body/taper measured-Ø stations for the MAIN-profile callouts, plus each station's drawn
 * diameter (keyed by reading id) so the leader can originate on the actual bottom surface —
 * a taper's diameter is interpolated at the reading's axial position, same as
 * `drawWearPitsOnProfile`. Liner readings are excluded here, and so is any reading whose
 * component is in [skipComponentIds] — a component with a detail strip on this page prints its
 * readings there, at the zoomed scale. Value-less (`diaMm == 0`) readings and orphans
 * (unresolved componentId) are skipped — render-layer orphan handling.
 */
private class ProfileDiaCalloutInput(
    val stations: List<DiaCalloutStation>,
    val surfaceDiaByKey: Map<String, Float>,
)

private fun buildProfileDiaCalloutInput(
    readings: List<WearDiaReading>,
    components: List<ResolvedComponent>,
    xAt: (Float) -> Float,
    diaText: Paint,
    displayUnits: DisplayUnits,
    skipComponentIds: Set<String> = emptySet(),
    dualStacked: Boolean = false,
): ProfileDiaCalloutInput {
    if (readings.isEmpty()) return ProfileDiaCalloutInput(emptyList(), emptyMap())
    val byId = components.associateBy { it.id }
    val stations = mutableListOf<DiaCalloutStation>()
    val surface = mutableMapOf<String, Float>()
    readings.forEach { r ->
        if (r.diaMm <= 0f) return@forEach
        if (r.componentId in skipComponentIds) return@forEach
        val rc = byId[r.componentId] ?: return@forEach
        val lenMm = (rc.endMmPhysical - rc.startMmPhysical).coerceAtLeast(0.001f)
        val local = r.axialMm.coerceIn(0f, lenMm)
        val drawnDiaMm = when (rc) {
            is ResolvedBody -> rc.diaMm
            is ResolvedTaper -> {
                val t = (local / lenMm).coerceIn(0f, 1f)
                rc.startDiaMm + (rc.endDiaMm - rc.startDiaMm) * t
            }
            else -> return@forEach   // liners → detail strip; threads/slots ineligible
        }
        if (drawnDiaMm <= 0f) return@forEach
        val label = formatDiaWithUnitDualLabel(
            r.diaMm.toDouble(), displayUnits.unitFor(r.componentId), displayUnits.dual,
        )
        stations += DiaCalloutStation(
            key = r.id,
            stationX = xAt(rc.startMmPhysical + local),
            label = label,
            labelWidth = diaText.measureDualLabel(label, dualStacked),
        )
        surface[r.id] = drawnDiaMm
    }
    return ProfileDiaCalloutInput(stations, surface)
}

/**
 * Draws placed measured-Ø callouts: each leader polyline (straight or dogleg — see
 * `geom/WearDiaCalloutLayout.kt`) plus its value label at the planned row position. Shared
 * by the main-profile and detail-strip draw paths so both surfaces render identically.
 */
private fun drawDiaCallouts(
    c: Canvas,
    placed: List<PlacedDiaCallout>,
    dim: Paint,
    diaText: Paint,
    dualStacked: Boolean = false,
) {
    placed.forEach { p ->
        for (s in 0 until p.leader.size - 1) {
            c.drawLine(p.leader[s].x, p.leader[s].y, p.leader[s + 1].x, p.leader[s + 1].y, dim)
        }
        val fm = diaText.fontMetrics
        c.drawDualLabelCentered(p.label, p.labelCx, p.labelTopY - fm.ascent, diaText, dualStacked)
    }
}

/**
 * One broken-out detail strip — a [WearStripWindow]: neighbor stubs at the window's outer ends,
 * its components drawn at the sheet's shared scale through the window's single piecewise mapping,
 * and the shaft between them either drawn true or compressed to an S-break pair (each break stood
 * off its component by a [WEAR_STRIP_BREAK_LEAD_PT] lead-in of the gap's TRUE outline).
 *
 * An end stub's style follows [wearStripEndStyle], never a blanket S-break: the break claims the
 * shaft continues past the stub, so a threaded shaft end draws flat + hatched and an end with
 * nothing beyond it draws no stub at all, just its own edge cap at full weight.
 *
 * A **liner** segment carries everything the strip has always had: light-grey wear bands with a
 * chained dimension rail above the cylinder (liner AFT edge → first band start → each band's
 * length → inter-band gaps → trailing remainder to the liner FWD edge, standard
 * witness-line/arrowed-span convention — see
 * `buildWearStripRailSpans`/`layoutWearStripRail`/`drawWearStripRail`), measured-Ø value callouts
 * below the cylinder when recorded, and pit "X"s. A **taper** segment draws its trapezoid
 * silhouette and a **body** segment its rectangle; both carry their own pits and measured-Ø
 * callouts, which is why a component with a strip no longer prints its readings under the main
 * profile.
 *
 * **Titles are per attachment cluster** ([wearStripClusters]), not one joined title per window:
 * components split by a compressed gap are not adjacent, so each side names itself. A cluster
 * holding a taper prints names only ([wearStripClusterShowsAnchor] — the rail measures it and a
 * taper at the shaft end is self-evidently placed); a lone liner or lone body run adds its
 * anchor dimension from the nearer SET (the "110 FROM CPLG S.E.T." line in the shop sketch this
 * feature digitizes). A window whose ONE cluster carries an anchor keeps the historical
 * placement — left-aligned, or right-aligned when measured from the FWD SET; every other label
 * centers under its own cluster's drawn span, pushed clear of its predecessor when they'd meet.
 *
 * Every segment's radius scales against the window's largest diameter, which fills the strip's
 * vertical budget — so a single-liner window is drawn exactly as it always was, and in a combined
 * window the taper and the liner keep their true diameter ratio.
 *
 * A wear band whose measured-Ø readings show material gone draws its **worn-profile trace**:
 * the cylinder's top and bottom edges dip through the readings instead of running straight, and
 * the band's grey fill follows them, so the bite reads at a glance (a liner measured half an inch
 * down used to print as a perfect cylinder — on-device report). Depth is display-exaggerated
 * against [deepestWearDepthMm], capped at the user-set [traceDepthFrac] but never drawn
 * shallower than true scale; the pure construction is `geom/WearTraceMath.kt`, shared with the
 * detail overlay's canvas so both render identically.
 * A band with no valued reading keeps its straight edges and full-rect fill.
 *
 * A liner with no recorded wear (spots empty — every liner gets a strip)
 * degenerates cleanly: no bands, no callouts, and the band-less rail draws only the two
 * liner-edge witness bars — no spanning line, arrowheads, or value, since that span would just
 * re-state the liner's own length and the rail exists to measure distances to wear areas
 * (device feedback). With [blankValues] the strip keeps its dimension LINES but drops
 * the VALUES: rail labels are omitted and an anchored cluster's title number becomes a writing
 * rule — the write-in template posture shared with the blank schematic (and since a blank sheet
 * has no bands, every blank strip's rail is witness-bars-only). Taper and body strips follow the
 * same lines-in/values-out rule; a cluster that prints no anchor leaves no write-in blank either,
 * since its location needs no measurement.
 *
 * [spots], [pits], and [diaReadings] must already be filtered to this window's components; the
 * shared strip scale [stripPtPerMm] comes from the page's packer (or
 * `sharedWearStripWindowPtPerMm` on the single-column paths), so relative lengths read true across
 * the whole page. [stubWidthPt] is the page's uniform neighbor-stub width — every end style here
 * measures off it through `hLayout.stubWidthPt`, so a packed page's squeezed stub can never
 * overrun the cell it was sized for.
 */
/**
 * The strip title / dimension text sizes, derived from the sheet's base text once.
 *
 * The composer needs them BEFORE the strip loop (to ask whether a stacked rail fits) and the window
 * needs them to build its paints; two copies of the expression would drift.
 */
internal fun stripTitleTextSize(text: Paint): Float = (text.textSize - 1f).coerceAtLeast(7f)
internal fun stripDimTextSize(text: Paint): Float = (text.textSize - 2f).coerceAtLeast(7f)

private fun drawWearStripWindow(
    c: Canvas,
    docSpec: ShaftSpec,
    window: WearStripWindow,
    stripTop: Float,
    stripBottom: Float,
    contentLeft: Float,
    contentRight: Float,
    /** Per-attachment-cluster anchor labels and per-reading Ø callouts each resolve their own
     *  unit via [DisplayUnits.unitFor] — a window may mix components with different overrides. */
    displayUnits: DisplayUnits,
    setPositions: SetPositions,
    text: Paint,
    outline: Paint,
    dim: Paint,
    stripPtPerMm: Float,
    stubWidthPt: Float = WEAR_STRIP_STUB_WIDTH_PT,
    // Outward room for each end's break curl — its share of the packed gutter beside it
    // (MAX_VALUE = unbounded void side). Clamps the glyph amplitude via
    // `wearStripBreakAmplitudePt` so facing curls can never cross a gutter.
    breakRoomLeftPt: Float = Float.MAX_VALUE,
    breakRoomRightPt: Float = Float.MAX_VALUE,
    // This strip's vertical scale relative to the page (`wearStripHeightFrac`): the window
    // with the page's largest reference diameter fills its band, the rest draw at their
    // true ratio to it, so heights read proportional on paper across the strips.
    heightFrac: Float = 1f,
    /**
     * Set dual values as two-line stacks in this strip's rail and Ø callouts. Decided once for the
     * whole SHEET by the composer (§7 degradation) and handed down, never re-derived here.
     */
    dualStacked: Boolean = false,
    titles: Map<String, String>,
    spots: List<WearSpot> = emptyList(),
    pits: List<WearPit> = emptyList(),
    diaReadings: List<WearDiaReading> = emptyList(),
    deepestWearDepthMm: Float = 0f,
    traceDepthFrac: Float = WEAR_TRACE_MAX_DEPTH_FRAC,
    bandShadeAlpha: Int = wearBandShadeAlpha(PDF_WEAR_BAND_SHADE_DEFAULT),
    blankValues: Boolean = false,
) {
    val comps = window.components
    val refDiaMm = window.refDiaMm
    if (comps.isEmpty() || refDiaMm <= 0f) return
    val compById = comps.associateBy { it.id }

    val hLayout = computeWearStripWindowLayout(
        contentLeft, contentRight, window.drawnWidthPt(stripPtPerMm), stripPtPerMm,
        stubWidthPt = stubWidthPt,
    )
    fun xAtStrip(mm: Float): Float = window.xAt(mm, hLayout.linerLeftPt, stripPtPerMm)

    val titleText = Paint(text).apply { textSize = stripTitleTextSize(text) }
    val dimText = Paint(text).apply { textSize = stripDimTextSize(text) }
    val linerComp = window.liner
    // Titles are per attachment CLUSTER, not per window (wearStripClusters): a compressed gap
    // means the components either side of it are NOT adjacent, so one joined
    // "A + B — dist FROM SET" title across the break would misread as one continuous area
    // (on-device request). Each cluster names its own components, AFT→FWD.
    //
    // A cluster holding a taper prints NO anchor dimension (wearStripClusterShowsAnchor): the
    // strip's dimension rail is the measuring surface, and a taper at the shaft end is
    // self-evidently placed. A lone liner keeps the LINER's anchor — the same number the
    // schematic prints — and a lone body run measures its own span against the nearer SET
    // through the same shared rule.
    val clusterTitles = wearStripClusters(window).map { cl ->
        val names = cl.components.joinToString(" + ") { titles[it.id] ?: "Component" }
        if (!wearStripClusterShowsAnchor(cl)) {
            ClusterTitle(cl, names, null, null)
        } else {
            // A cluster that shows an anchor is always a single lone component (a taper
            // disqualifies it above, and bodies/liners never combine with each other) — its own
            // id is the resolved-component key for a per-component unit override.
            val clusterUnit = displayUnits.unitFor(cl.components.firstOrNull()?.id)
            val ln = cl.components.firstOrNull { it.kind == WearStripComponentKind.LINER }
                ?.let { lc -> docSpec.liners.firstOrNull { it.id == lc.id } }
            val label = if (ln != null) buildLinerAnchorLabel(docSpec, ln, setPositions, clusterUnit, displayUnits.dual)
            else buildSpanAnchorLabel(docSpec, cl.startMm, cl.endMm, setPositions, clusterUnit, displayUnits.dual)
            val from = if (ln != null) linerAnchorForPdf(docSpec, ln)
            else wearStripAnchorForSpan(docSpec, cl.startMm, cl.endMm, setPositions).anchor
            ClusterTitle(cl, names, label.takeIf { it.isNotEmpty() }, from)
        }
    }
    // The titles are drawn LAST, at the bottom of the strip, to match the hand-marked sheet.
    // See the title block near the end.

    // Measured-Ø callout plan (readings with a recorded value only — a placed-but-empty
    // station is an overlay-only affordance and never prints). Planned BEFORE the inner
    // split so the strip reserves exactly the label rows this window needs; the leader
    // region reuses the existing label headroom below the cylinder, so a reading-free
    // strip's layout is unchanged.
    val valuedReadings = diaReadings.filter { it.diaMm > 0f && compById.containsKey(it.componentId) }
    val diaStations = valuedReadings.map { r ->
        val comp = compById.getValue(r.componentId)
        val local = r.axialMm.coerceIn(0f, comp.lengthMm)
        val label = formatDiaWithUnitDualLabel(
            r.diaMm.toDouble(), displayUnits.unitFor(r.componentId), displayUnits.dual,
        )
        DiaCalloutStation(
            r.id, xAtStrip(comp.startMm + local), label,
            dimText.measureDualLabel(label, dualStacked),
        )
    }
    // Drawn diameter under each station, so a leader and its witness tick meet the actual
    // surface — a taper's is interpolated at the reading's own position.
    val stationDiaByKey = valuedReadings.associate { r ->
        val comp = compById.getValue(r.componentId)
        r.id to comp.diaAtLocalMm(r.axialMm.coerceIn(0f, comp.lengthMm))
    }
    val diaPlan = if (diaStations.isEmpty()) null else
        planDiaCallouts(diaStations, contentLeft + 2f, contentRight - 2f, WEAR_DIA_MIN_GAP_PT)
    // A stacked value is two lines tall, so a callout ROW is the whole stack — the reserved band
    // and `finish`'s row pitch below must read the same number or the rows overprint.
    val diaRowHeightPt = if (dualStacked) dimText.dualStackMetrics().height else dimText.textSize
    val diaBandPt = diaPlan?.let { it.labelsHeightPt(diaRowHeightPt, WEAR_DIA_ROW_GAP_PT) + 2f } ?: 0f

    val sortedSpots = spots.sortedBy { it.startMm }
    // Bands clamp to the liner's own span for rendering; the stored spots are never touched.
    val clampedBands = if (linerComp == null) emptyList() else
        sortedSpots.map { spot -> clampWearBandToLiner(spot.startMm, spot.lengthMm, linerComp.lengthMm) }

    // Worn-profile traces, one per band (geom/WearTraceMath.kt). Pure mm→depth-fraction math,
    // so it is computed before any px geometry and mapped through the strip's scale below —
    // the same output the detail overlay's canvas walks. Only a liner traces: body/taper
    // readings never drive a worn profile.
    //
    // Each band is smoothed HERE, before sequencing, so the surface edges and that band's own
    // grey fill (which takes its band trace directly) walk exactly the same vertices — and so
    // the overlay canvas, which smooths per band too, curves identically.
    val traceReadings = if (linerComp == null) emptyList() else valuedReadings
        .filter { it.componentId == linerComp.id }
        .map { WearTraceReading(it.axialMm.coerceIn(0f, linerComp.lengthMm), it.diaMm) }
    val bandTraces = clampedBands.map { clamp ->
        smoothWearTrace(
            buildWearTrace(
                bandStartMm = clamp.startMm,
                bandLengthMm = clamp.lengthMm,
                readings = traceReadings,
                nominalOdMm = linerComp?.aftDiaMm ?: 0f,
                deepestDepthMm = deepestWearDepthMm,
                maxDepthFrac = traceDepthFrac,
            )
        )
    }
    val traceVerts = sequenceWearTraces(bandTraces)

    // ONE rail row height for this strip: the budget below and `drawWearStripRail`'s own stepping
    // both read it. They used to be separate numbers (13 pt budgeted, `textSize + 3` drawn) that
    // only happened to nest; a stacked row is taller than either, so a single value is the only way
    // the reserved band and the drawn rows can agree.
    val railRowHeightPt = wearRailRowHeightPt(dimText, dualStacked)
    val inner = computeWearStripInnerLayout(
        stripTop, stripBottom,
        titleHeightPt = titleText.textSize,
        rowHeightPt = railRowHeightPt,
        diaBandPt = diaBandPt,
    )
    val cy = (inner.cylTop + inner.cylBottom) / 2f
    // The band radius, scaled by the strip's page-proportional height fraction: every drawn
    // radius below derives from this one cap, so the whole strip (components, stubs, gap
    // outlines, bands, trace) shares the page's single vertical diameter scale.
    val rCap = ((inner.cylBottom - inner.cylTop) / 2f).coerceAtLeast(0f) *
        heightFrac.coerceIn(0f, 1f)

    // Neighbor diameters resolved up front for the break-out stubs. The window's largest
    // component fills this strip's height-scaled cap (horizontal scale never affects height);
    // everything else — other components, the gap outlines, and the stubs — keeps its true
    // diameter ratio to that reference (computeWearStripRadii).
    val aftDia = neighborDiaMmAtAft(docSpec, window.startMm) ?: comps.first().aftDiaMm
    val fwdDia = neighborDiaMmAtFwd(docSpec, window.endMm) ?: comps.last().fwdDiaMm
    val radii = computeWearStripRadii(refDiaMm, aftDia, fwdDia, rCap)
    fun rOf(diaMm: Float): Float = (rCap * (diaMm / refDiaMm)).coerceIn(0f, rCap)

    val dimPaint = Paint(outline).apply { strokeWidth = WEAR_DIM_PT }

    // ── Silhouettes, segment by segment through the window's one mapping ──────
    window.segments.forEachIndexed { i, seg ->
        val x0 = xAtStrip(seg.startMm)
        val x1 = xAtStrip(seg.endMm)
        when (seg) {
            is WearStripComponentSeg -> {
                val comp = seg.component
                val rA = rOf(comp.aftDiaMm)
                val rF = rOf(comp.fwdDiaMm)
                when (comp.kind) {
                    WearStripComponentKind.LINER -> {
                        // The liner's edges run straight except inside a traced wear band, where
                        // they curve down through the measured diameters (mirrored, so the bite
                        // is symmetric); with no trace the two paths are exactly straight lines.
                        val top = cy - rA
                        val bot = cy + rA
                        if (traceVerts.isEmpty()) {
                            c.drawLine(x0, top, x1, top, outline)
                            c.drawLine(x0, bot, x1, bot, outline)
                        } else {
                            val xAtLocal: (Float) -> Float = { mm -> xAtStrip(comp.startMm + mm) }
                            c.drawPath(tracedLinerEdgePath(x0, x1, top, 1f, rA, traceVerts, xAtLocal), outline)
                            c.drawPath(tracedLinerEdgePath(x0, x1, bot, -1f, rA, traceVerts, xAtLocal), outline)
                        }
                    }
                    WearStripComponentKind.TAPER -> {
                        c.drawLine(x0, cy - rA, x1, cy - rF, outline)
                        c.drawLine(x0, cy + rA, x1, cy + rF, outline)
                    }
                    WearStripComponentKind.BODY -> {
                        c.drawLine(x0, cy - rA, x1, cy - rA, outline)
                        c.drawLine(x0, cy + rA, x1, cy + rA, outline)
                    }
                }
                // Edge caps, thin so they read as component boundaries rather than shaft ends.
                c.drawLine(x0, cy - rA, x0, cy + rA, dimPaint)
                c.drawLine(x1, cy - rF, x1, cy + rF, dimPaint)
            }
            is WearStripGapSeg -> {
                val diaL = (window.segments.getOrNull(i - 1) as? WearStripComponentSeg)?.component?.fwdDiaMm ?: aftDia
                val diaR = (window.segments.getOrNull(i + 1) as? WearStripComponentSeg)?.component?.aftDiaMm ?: fwdDia
                val rL = rOf(diaL)
                val rR = rOf(diaR)
                if (seg.trueScale) {
                    // The real shaft between the two components, at true scale.
                    val verts = wearStripGapProfile(
                        docSpec, seg.startMm, seg.endMm,
                        samples = ((x1 - x0) / 2f).toInt().coerceIn(2, 64),
                    )
                    if (verts.isEmpty()) {
                        // Nothing resolves under the gap: bridge the two neighbors directly.
                        c.drawLine(x0, cy - rL, x1, cy - rR, outline)
                        c.drawLine(x0, cy + rL, x1, cy + rR, outline)
                    } else {
                        var px = x0
                        var pr = rOf(verts.first().diaMm)
                        verts.drop(1).forEach { v ->
                            val vx = xAtStrip(v.mm)
                            val vr = rOf(v.diaMm)
                            c.drawLine(px, cy - pr, vx, cy - vr, outline)
                            c.drawLine(px, cy + pr, vx, cy + vr, outline)
                            px = vx; pr = vr
                        }
                    }
                } else {
                    // Too far to draw true: the run compresses to a fixed width and the pair of
                    // S-break edges marks the removed length, open space between them. Same
                    // glyph and eye convention as the window's own neighbor stubs — material
                    // toward the component, void toward the gap.
                    //
                    // Each break stands off its component by a short lead-in of the shaft as it
                    // ACTUALLY is just inside the gap (WEAR_STRIP_BREAK_LEAD_PT). Jumping the
                    // outline straight to the adjacent component's edge Ø made the component
                    // itself look shifted, and a break drawn hard against that edge left no
                    // connecting shaft at all (on-device report).
                    val rGapL = rOf(outerDiaMmAt(docSpec, seg.startMm + 0.5f).takeIf { it > 0f } ?: diaL)
                    val rGapR = rOf(outerDiaMmAt(docSpec, seg.endMm - 0.5f).takeIf { it > 0f } ?: diaR)
                    val lead = WEAR_STRIP_BREAK_LEAD_PT
                    c.drawLine(x0, cy - rGapL, x0 + lead, cy - rGapL, outline)
                    c.drawLine(x0, cy + rGapL, x0 + lead, cy + rGapL, outline)
                    drawBreakEdge(c, x0 + lead, cy - rGapL, cy + rGapL, rGapL * 0.6f, outline, eyeAtTop = false)
                    drawBreakEdge(c, x1 - lead, cy - rGapR, cy + rGapR, rGapR * 0.6f, outline, eyeAtTop = true)
                    c.drawLine(x1 - lead, cy - rGapR, x1, cy - rGapR, outline)
                    c.drawLine(x1 - lead, cy + rGapR, x1, cy + rGapR, outline)
                }
            }
        }
    }

    // Window ends. A stub's S-break says "the shaft continues past here", so it is drawn only
    // where it does (wearStripEndStyle): a threaded end shows the WHOLE remaining shaft — flat
    // outer edge + thread hatch — and an end with nothing beyond it gets no stub at all, its
    // edge cap redrawn at full weight so the shaft reads as physically ending there (the
    // per-segment caps are thin). Mirror of the wear detail overlay's convention
    // (`LinerWearDetail.kt`).
    //
    // A break stub's own break sits at its far/outer end (void beyond it, material toward the
    // window) — the inverse of a centered compression break's shared-gap geometry — so eyeAtTop
    // is the opposite of the compression-break convention: left/AFT stub void is to its left
    // (eyeAtTop = true), right/FWD stub void is to its right (eyeAtTop = false). See
    // `LinerWearDetail.kt`'s `drawBreakEdgeCompose` KDoc.
    val stubLeftX = hLayout.linerLeftPt - hLayout.stubWidthPt
    val stubRightX = hLayout.linerRightPt + hLayout.stubWidthPt
    when (wearStripEndStyle(docSpec, window.startMm, aftSide = true)) {
        WearStripEndStyle.BREAK -> {
            val r = radii.aftRPt
            c.drawLine(stubLeftX, cy - r, hLayout.linerLeftPt, cy - r, outline)
            c.drawLine(stubLeftX, cy + r, hLayout.linerLeftPt, cy + r, outline)
            val amp = wearStripBreakAmplitudePt(r, breakRoomLeftPt, outline.strokeWidth)
            drawBreakEdge(c, stubLeftX, cy - r, cy + r, amp, outline, eyeAtTop = true)
        }
        WearStripEndStyle.THREAD_END -> {
            val threadDia = wearStripEndThreadDiaMm(docSpec, window.startMm, aftSide = true)
            val r = if (threadDia > 0f) rOf(threadDia) else radii.aftRPt
            c.drawLine(stubLeftX, cy - r, hLayout.linerLeftPt, cy - r, outline)
            c.drawLine(stubLeftX, cy + r, hLayout.linerLeftPt, cy + r, outline)
            c.drawLine(stubLeftX, cy - r, stubLeftX, cy + r, outline)
            drawThreadStubHatch(c, stubLeftX, cy - r, hLayout.linerLeftPt, cy + r, outline)
        }
        WearStripEndStyle.FLAT -> {
            val r = rOf(comps.first().aftDiaMm)
            c.drawLine(hLayout.linerLeftPt, cy - r, hLayout.linerLeftPt, cy + r, outline)
        }
    }
    when (wearStripEndStyle(docSpec, window.endMm, aftSide = false)) {
        WearStripEndStyle.BREAK -> {
            val r = radii.fwdRPt
            c.drawLine(hLayout.linerRightPt, cy - r, stubRightX, cy - r, outline)
            c.drawLine(hLayout.linerRightPt, cy + r, stubRightX, cy + r, outline)
            val amp = wearStripBreakAmplitudePt(r, breakRoomRightPt, outline.strokeWidth)
            drawBreakEdge(c, stubRightX, cy - r, cy + r, amp, outline, eyeAtTop = false)
        }
        WearStripEndStyle.THREAD_END -> {
            val threadDia = wearStripEndThreadDiaMm(docSpec, window.endMm, aftSide = false)
            val r = if (threadDia > 0f) rOf(threadDia) else radii.fwdRPt
            c.drawLine(hLayout.linerRightPt, cy - r, stubRightX, cy - r, outline)
            c.drawLine(hLayout.linerRightPt, cy + r, stubRightX, cy + r, outline)
            c.drawLine(stubRightX, cy - r, stubRightX, cy + r, outline)
            drawThreadStubHatch(c, hLayout.linerRightPt, cy - r, stubRightX, cy + r, outline)
        }
        WearStripEndStyle.FLAT -> {
            val r = rOf(comps.last().fwdDiaMm)
            c.drawLine(hLayout.linerRightPt, cy - r, hLayout.linerRightPt, cy + r, outline)
        }
    }

    // Wear bands (light grey fill + edge ticks on the liner itself) — per spot. The dimension
    // story (offsets/lengths) is the chained rail above; the diameter story is the
    // measured-Ø callouts below, exclusively: printing a per-band min-Ø label here would
    // collide with the callout values under a wear band (on-device report).
    // [WearSpot.minDiaMm] is model-only, for older files.
    // A traced band fills between its traced edges instead of the full rect, so the material
    // measured away stays white above and below the grey.
    if (linerComp != null) {
        val linerR = rOf(linerComp.aftDiaMm)
        val top = cy - linerR; val bot = cy + linerR
        val bandFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL; color = Color.argb(bandShadeAlpha, 0, 0, 0)
        }
        clampedBands.forEachIndexed { i, clamp ->
            if (clamp.lengthMm <= 0f) return@forEachIndexed
            val x0 = xAtStrip(linerComp.startMm + clamp.startMm)
            val x1 = xAtStrip(linerComp.startMm + clamp.startMm + clamp.lengthMm)
            val trace = bandTraces[i]
            if (trace.isEmpty()) {
                c.drawRect(x0, top, x1, bot, bandFill)
            } else {
                c.drawPath(
                    tracedBandFillPath(top, bot, linerR, trace) { mm -> xAtStrip(linerComp.startMm + mm) },
                    bandFill,
                )
            }
            c.drawLine(x0, top, x0, bot, dimPaint); c.drawLine(x1, top, x1, bot, dimPaint)
        }
    }

    // Pit "X" markers on the broken-out components (strip-local scale) — reinforcing the same
    // pits drawn on the main profile, at the strip's larger scale. Same X construction as the
    // profile / detail-canvas draw sites (geom/WearPitMath.kt); a taper's local radius is
    // interpolated so the X lands on the sloped surface.
    if (pits.isNotEmpty()) {
        val pitPaintStrip = Paint(outline).apply { strokeCap = Paint.Cap.ROUND }
        pits.forEach { pit ->
            val comp = compById[pit.componentId] ?: return@forEach
            val local = pit.axialMm.coerceIn(0f, comp.lengthMm)
            val r = rOf(comp.diaAtLocalMm(local))
            val cxp = xAtStrip(comp.startMm + local)
            val pyp = pitCenterY(cy - r, cy + r, pit.acrossFrac)
            drawWearPitX(c, cxp, pyp, pitHalfArm(pit.size, WEAR_PIT_SMALL_HALF_STRIP_PT), pitPaintStrip)
        }
    }

    // Measured-Ø readings: a thin witness tick across the full component height at each
    // station (where the diameter was taken), plus the value below with a leader — the
    // hand sketch's fan of diameters. Leaders run through the label headroom; the value
    // rows sit in the diaBand reserved by computeWearStripInnerLayout above. Same engine +
    // construction as the main-profile callouts and the overlay canvas.
    if (diaPlan != null) {
        val tickPaint = Paint(outline).apply { strokeWidth = WEAR_DIM_PT * 0.6f; alpha = 160 }
        diaPlan.stations.forEach { s ->
            val r = rOf(stationDiaByKey[s.key] ?: refDiaMm)
            c.drawLine(
                s.stationX, cy - r - WEAR_DIA_TICK_OVERSHOOT_PT,
                s.stationX, cy + r + WEAR_DIA_TICK_OVERSHOOT_PT, tickPaint,
            )
        }
        val placed = diaPlan.finish(
            row0Top = inner.cylBottom + WEAR_STRIP_LABEL_HEADROOM_PT,
            labelTextHeight = dimText.textSize,
            rowGap = WEAR_DIA_ROW_GAP_PT,
            surfaceYAt = { i -> cy + rOf(stationDiaByKey[diaPlan.stations[i].key] ?: refDiaMm) },
            leaderStartGap = WEAR_DIA_TICK_OVERSHOOT_PT + 1f,
        )
        drawDiaCallouts(c, placed, dim, dimText)
    }

    // Chained dimension rail ABOVE the cylinder (matches the hand-marked sheet convention;
    // see "Wear Detail Strips" in docs/contracts/RunoutSheet.md): liner AFT edge → first
    // band start, each band's own length, inter-band gaps, and the trailing remainder to the liner
    // FWD edge — standard witness-line/arrowed-span/centered-label rail convention. The rail
    // measures WEAR, so it belongs to the window's liner; a taper/body-only window has none.
    if (linerComp != null) {
        val railSpans = buildWearStripRailSpans(
            linerComp.lengthMm, clampedBands, displayUnits.unitFor(linerComp.id), displayUnits.dual,
        )
        val railLayout = layoutWearStripRail(
            railSpans,
            xAtStripMm = { mm -> xAtStrip(linerComp.startMm + mm) },
            labelWidthPt = { s -> dimText.measureDualLabel(s, dualStacked) },
        )
        // Blank draft: the rail's dimension lines still draw, the value labels do not — the
        // machinist writes the measured figures under the rail by hand. And with no wear bands at
        // all the rail has nothing to dimension, so only its liner-edge witness bars draw (device
        // feedback — a full-length span would just re-state the liner's own length).
        val hasWearBands = clampedBands.any { it.lengthMm > 0f }
        // Witness lines run down to the liner's ACTUAL drawn top — a height-scaled strip's
        // surface sits below the band top, and bars stopping in the air above it would read
        // as measuring nothing.
        drawWearStripRail(c, dim, dimText, railLayout, cy - rOf(linerComp.aftDiaMm), inner.railY,
            inner.railLabelRows, drawLabels = !blankValues, drawSpanLines = hasWearBands,
            rowHeightPt = railRowHeightPt, dualStacked = dualStacked)
    }

    // Titles, drawn LAST at the BOTTOM of the strip — one per attachment cluster, all sharing
    // this one baseline. A cluster that isn't the window's lone anchored one sits CENTERED under
    // its own drawn span, so a name always points at the metal it names; the historical
    // single-liner / single-body strip keeps its SET-direction alignment cue instead — a
    // FWD-SET-referenced title right-aligns (toward the FWD end drawn on the right), an
    // AFT-SET-referenced one stays left-aligned.
    val titleBaselineY = (stripBottom - 2f).coerceAtLeast(inner.cylBottom + titleText.textSize)
    val loneAnchored = clusterTitles.size == 1 && clusterTitles[0].anchor != null
    // Labels are placed left→right; one that would run into its predecessor is pushed clear of
    // it and then clamped/ellipsized against the strip's right edge.
    var prevRightPt = Float.NEGATIVE_INFINITY
    fun placeLeftPt(preferredLeft: Float, width: Float): Float =
        maxOf(preferredLeft, prevRightPt + WEAR_STRIP_TITLE_GAP_PT)
            .coerceIn(contentLeft, (contentRight - width).coerceAtLeast(contentLeft))

    clusterTitles.forEach { ct ->
        val cx = (xAtStrip(ct.cluster.startMm) + xAtStrip(ct.cluster.endMm)) / 2f
        titleText.textAlign = Paint.Align.LEFT
        if (blankValues && ct.anchor != null) {
            // Write-in title: "Name — ____ FROM  AFT / FWD  S.E.T." — the anchor VALUE becomes a
            // writing rule and BOTH directions print for the machinist to circle one
            // (WEAR_BLANK_ANCHOR_SUFFIX). A body strip takes the same construction: its anchor is
            // measured the same way, so a write-in sheet must leave the same blank.
            // Always left-aligned: the FWD right-align cue mirrors a KNOWN measurement direction,
            // which a write-in sheet doesn't have. drawLabelWithRule needs LEFT-aligned paint.
            val prefix = "${ct.names} —"
            val suffixW = titleText.measureText(WEAR_BLANK_ANCHOR_SUFFIX)
            val totalW = titleText.measureText(prefix) + BLANK_RULE_LEAD_PT + BLANK_DIM_GAP_PT +
                BLANK_RULE_TRAIL_PT + suffixW
            val x = if (loneAnchored) contentLeft else placeLeftPt(cx - totalW / 2f, totalW)
            val afterRule = drawLabelWithRule(
                c, prefix, x, titleBaselineY, titleText,
                ruleWidth = BLANK_DIM_GAP_PT, maxRight = contentRight,
            )
            c.drawText(WEAR_BLANK_ANCHOR_SUFFIX, afterRule - 8f, titleBaselineY, titleText)
            prevRightPt = afterRule - 8f + suffixW
        } else {
            // A cluster with no anchor prints names only — on a blank sheet too, since a location
            // that needs no measurement needs no write-in blank either.
            val label = if (ct.anchor == null) ct.names else "${ct.names} — ${ct.anchor}"
            if (loneAnchored && !blankValues) {
                val fit = ellipsizeToWidth(label, titleText, contentRight - contentLeft, rich = true)
                if (ct.from == LinerAnchor.FWD_SET) {
                    titleText.textAlign = Paint.Align.RIGHT
                    c.drawRichText(fit, contentRight, titleBaselineY, titleText)
                    prevRightPt = contentRight
                } else {
                    c.drawRichText(fit, contentLeft, titleBaselineY, titleText)
                    prevRightPt = contentLeft + titleText.measureRichText(fit)
                }
            } else {
                val w = titleText.measureRichText(label)
                val x = placeLeftPt(cx - w / 2f, w)
                val fit = ellipsizeToWidth(label, titleText, contentRight - x, rich = true)
                c.drawRichText(fit, x, titleBaselineY, titleText)
                prevRightPt = x + titleText.measureRichText(fit)
            }
        }
    }
}

/** One strip title: a cluster's names, its anchor dimension (null when it prints none), and the
 *  SET that anchor is measured from (the alignment cue on a lone anchored cluster). */
private data class ClusterTitle(
    val cluster: WearStripCluster,
    val names: String,
    val anchor: String?,
    val from: LinerAnchor?,
)

/** Clear run kept between two cluster titles sharing a strip's title baseline. */
private const val WEAR_STRIP_TITLE_GAP_PT = 8f

/** `drawLabelWithRule`'s label→rule lead-in, mirrored here so a blank title can be pre-measured. */
private const val BLANK_RULE_LEAD_PT = 4f

/** `drawLabelWithRule`'s trailing advance, less the suffix's own pull-back (`afterRule - 8f`). */
private const val BLANK_RULE_TRAIL_PT = 6f

/** Fixed hatch pitch inside a thread-end stub — the stub is symbolic, never drawn to scale. */
private const val WEAR_STRIP_THREAD_HATCH_PITCH_PT = 6f

/**
 * Diagonal thread hatch filling a strip window's thread-end stub — the PDF port of the wear
 * detail overlay's `drawThreadStubHatch` (`ui/screen/LinerWearDetail.kt`), drawn with the same
 * thin part-transparent recipe the main profile's thread hatch uses, so a threaded shaft end
 * reads the same on the strip as it does on the profile above it. Only a
 * [WearStripEndStyle.THREAD_END] end gets one: it shows the whole remaining shaft, so it carries
 * a flat outer edge and this hatch instead of an S-break.
 */
private fun drawThreadStubHatch(c: Canvas, x0: Float, top: Float, x1: Float, bot: Float, outline: Paint) {
    if (x1 <= x0 || bot <= top) return
    val hatch = Paint(outline).apply { strokeWidth = WEAR_DIM_PT * 0.6f; alpha = 160 }
    val h = bot - top
    val saved = c.save()
    c.clipRect(x0, top, x1, bot)
    var hx = x0 - h
    while (hx <= x1) {
        c.drawLine(hx, bot, hx + h, top, hatch)
        hx += WEAR_STRIP_THREAD_HATCH_PITCH_PT
    }
    c.restoreToCount(saved)
}

/**
 * One traced liner surface edge as a polyline: it runs along [edgeY] from [leftPt] to [rightPt]
 * and dips to `edgeY + dir × depthFrac × radiusPt` at each worn-profile vertex ([verts], one
 * left-to-right run from `sequenceWearTraces`). The run arrives already smoothed
 * (`smoothWearTrace`), so walking it with straight segments still draws a flowing curve — the
 * curve lives in the vertices, never in this path's construction. [dir] is `+1` for the TOP edge
 * and `-1` for the BOTTOM, so both edges bite the same amount out of the cylinder. [xAtLocalMm]
 * maps a liner-local mm to strip x.
 */
private fun tracedLinerEdgePath(
    leftPt: Float,
    rightPt: Float,
    edgeY: Float,
    dir: Float,
    radiusPt: Float,
    verts: List<WearTraceVertex>,
    xAtLocalMm: (Float) -> Float,
): android.graphics.Path = android.graphics.Path().apply {
    moveTo(leftPt, edgeY)
    verts.forEach { v -> lineTo(xAtLocalMm(v.localMm), edgeY + dir * v.depthFrac * radiusPt) }
    lineTo(rightPt, edgeY)
}

/**
 * A traced band's grey fill: the closed polygon between the traced TOP edge (left→right) and the
 * traced BOTTOM edge (right→left), from ONE band's [verts]. The material measured away is left
 * white outside it.
 */
private fun tracedBandFillPath(
    topY: Float,
    botY: Float,
    radiusPt: Float,
    verts: List<WearTraceVertex>,
    xAtLocalMm: (Float) -> Float,
): android.graphics.Path = android.graphics.Path().apply {
    if (verts.isEmpty()) return@apply
    verts.forEachIndexed { i, v ->
        val x = xAtLocalMm(v.localMm)
        val y = topY + v.depthFrac * radiusPt
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    for (i in verts.indices.reversed()) {
        val v = verts[i]
        lineTo(xAtLocalMm(v.localMm), botY - v.depthFrac * radiusPt)
    }
    close()
}

/**
 * Draws one strip's chained dimension rail (`buildWearStripRailSpans`/`layoutWearStripRail` in
 * `WearStripLayout.kt`): witness lines from just above the cylinder edge up to the rail (a
 * small clear gap at the liner, matching the main profile's OAL witness convention), an arrowed
 * dimension line per chained span, and each span's label. A label that fits inside its own
 * span (the inward-arrow test, which also guarantees stub room at [DIM_BREAK_TEXT_PAD_PT])
 * **seats in a break cut in the span line**, vertically centred — the schematic's
 * value-in-a-break convention, consistent across drawing outputs. A label wider than its
 * span (short bands/gaps) falls back to the stacked rows assigned by `layoutWearStripRail`,
 * stacked ABOVE the rail line (row 0 nearest it) and clamped to [maxLabelRows] (rows beyond
 * the budget `computeWearStripInnerLayout` actually fit for this strip are never drawn) —
 * break-seated labels can never collide with each other since chained spans are disjoint.
 *
 * The fallback rows go above because the band between the rail and the cylinder is the witness
 * lines' run: a value parked there prints across them (on-device report). Above is also where
 * the schematic's dimension rails put a value that cannot seat in its line.
 *
 * [drawLabels] = false (blank write-in draft) keeps every line and arrowhead but skips the
 * value labels — the lines-in/values-out template rule.
 *
 * [drawSpanLines] = false (a rail with no wear bands: every strip in blank write-in mode, and
 * spotless liners on the printed sheet) keeps only the edge witness bars: the full-length span
 * would just re-state the liner's own length, and the rail's job is measuring distances to wear
 * areas, not each liner's OAL (device feedback).
 */
private fun drawWearStripRail(
    c: Canvas,
    dim: Paint,
    dimText: Paint,
    layout: List<WearRailSpanLayout>,
    cylTop: Float,
    railY: Float,
    maxLabelRows: Int,
    drawLabels: Boolean = true,
    drawSpanLines: Boolean = true,
    /**
     * Row pitch — the SAME value the strip reserved its rail band with
     * ([computeWearStripInnerLayout]'s `rowHeightPt`, via [wearRailRowHeightPt]). Never derive a
     * second one here: a drawn step wider than the budgeted row overflows the band it was sized
     * for, and a narrower one wastes it.
     */
    rowHeightPt: Float = WEAR_STRIP_ROW_HEIGHT_PT,
    dualStacked: Boolean = false,
) {
    if (layout.isEmpty()) return
    val arrow = 4f
    val rowStepPt = rowHeightPt
    val witnessExt = 3f
    val witnessGap = 3f   // gap between the liner's top edge and the witness line start — same
                          // convention as the main profile's OAL witness lines (device feedback)
    // Clears the witness lines' overshoot past the rail, so a fallback value never prints over
    // one of them.
    val labelGapPt = witnessExt + 1f

    // The rail sits ABOVE the cylinder: witness lines run up from just above the cylinder top
    // past the rail line, and any fallback span label stacks UPWARD from the rail line, away
    // from the witness run below it.
    layout.forEach { s ->
        c.drawLine(s.x0Pt, cylTop - witnessGap, s.x0Pt, railY - witnessExt, dim)
        c.drawLine(s.x1Pt, cylTop - witnessGap, s.x1Pt, railY - witnessExt, dim)
        // Band-less rail: witness bars only — no spanning line, arrows, or label (a label with no
        // span line under it would float, so this suppresses labels regardless of drawLabels).
        if (!drawSpanLines) return@forEach

        // Value-in-a-break when the label fits its span (the layout's seatsInBreak guarantees
        // the break's stubs still have arrow room); otherwise a continuous line with the
        // above-line fallback row — the schematic's value-in-a-break rule, mirrored.
        val lw = dimText.measureDualLabel(s.label, dualStacked)
        val seatsInBreak = drawLabels && s.seatsInBreak
        if (seatsInBreak) {
            val gapHalf = lw * 0.5f + DIM_BREAK_TEXT_PAD_PT
            c.drawLine(s.x0Pt, railY, s.labelCxPt - gapHalf, railY, dim)
            c.drawLine(s.labelCxPt + gapHalf, railY, s.x1Pt, railY, dim)
            val fm = dimText.fontMetrics
            // A stack straddles the rail line: primary above it, secondary below, with the line's
            // two stubs pointing into the seam between them.
            val seatBaseline =
                if (s.label.setsStacked(dualStacked))
                    railY - dimText.dualStackMetrics().height * 0.5f - fm.ascent
                else railY - (fm.ascent + fm.descent) * 0.5f
            c.drawDualLabelCentered(s.label, s.labelCxPt, seatBaseline, dimText, dualStacked)
        } else {
            c.drawLine(s.x0Pt, railY, s.x1Pt, railY, dim)
        }

        val dirLeft = if (s.arrowInward) 1f else -1f
        val dirRight = if (s.arrowInward) -1f else 1f
        c.drawLine(s.x0Pt, railY, s.x0Pt + dirLeft * arrow, railY - arrow * 0.5f, dim)
        c.drawLine(s.x0Pt, railY, s.x0Pt + dirLeft * arrow, railY + arrow * 0.5f, dim)
        c.drawLine(s.x1Pt, railY, s.x1Pt + dirRight * arrow, railY - arrow * 0.5f, dim)
        c.drawLine(s.x1Pt, railY, s.x1Pt + dirRight * arrow, railY + arrow * 0.5f, dim)

        if (drawLabels && !seatsInBreak) {
            val row = s.labelRow.coerceAtMost(maxLabelRows - 1)
            if (row >= 0) {
                // Row 0 sits just above the rail (clear of the witness overshoot), the rest
                // stack upward into the band computeWearStripInnerLayout reserved there.
                val fm = dimText.fontMetrics
                // The BOTTOM line keeps the historical clearance above the rail; a stack grows
                // upward from there into the row the budget reserved.
                val stackLift = if (s.label.setsStacked(dualStacked))
                    dimText.dualStackMetrics().advance else 0f
                val ly = railY - labelGapPt - fm.descent - row * rowStepPt - stackLift
                c.drawDualLabelCentered(s.label, s.labelCxPt, ly, dimText, dualStacked)
            }
        }
    }
}

/**
 * Vertical-line fill across `[x0,x1] × [top,bot]` — the wear-band mark on the MAIN profile, matching
 * how the shop marks wear areas by hand (vertical strokes, not diagonal hatch). Evenly spaced at
 * [pitchPt], with both band edges drawn so the span reads closed.
 */
internal fun drawVerticalBand(c: Canvas, x0: Float, x1: Float, top: Float, bot: Float, paint: Paint, pitchPt: Float) {
    if (x1 <= x0) return
    var vx = x0
    while (vx < x1) {
        c.drawLine(vx, top, vx, bot, paint)
        vx += pitchPt
    }
    c.drawLine(x1, top, x1, bot, paint)
}

/** Text note for strips that didn't fit within [WEAR_STRIP_MAX_PER_PAGE] on this page. */
private fun drawWearOverflowNote(
    c: Canvas,
    text: Paint,
    left: Float,
    right: Float,
    bandTop: Float,
    bandBottom: Float,
    overflow: List<WearStripWindow>,
    titles: Map<String, String>,
) {
    val names = overflow.joinToString(", ") { w ->
        w.components.joinToString(" + ") { comp ->
            titles[comp.id] ?: "@ ${comp.startMm.toInt()}mm"
        }
    }
    // A sheet of nothing but liner strips keeps the wording it always had.
    val kind = if (overflow.all { w -> w.components.all { it.kind == WearStripComponentKind.LINER } })
        "liner(s)" else "component(s)"
    val msg = "+${overflow.size} more $kind: $names"
    val y = (bandTop + bandBottom) * 0.5f + text.textSize * 0.35f
    c.drawText(ellipsizeToWidth(msg, text, right - left), left, y, text)
}

// ──────────────────────────────────────────────────────────────────────────────
// Notes / dye-pen area
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Draw the dye penetrant result checkboxes and a notes field at the bottom of the page.
 *
 * [dyePenResult] is the in-app selection ([WearRecord.dyePenResult]): the chosen box gets
 * an "X" drawn inside it, the other stays present and blank — the form always reads as the
 * same two-box row. `null` (nothing selected, and every blank write-in draft) leaves both
 * boxes empty for hand-marking, the original posture. Free-form notes remain hand-written.
 */
private fun drawWearNotesArea(
    c: Canvas,
    text: Paint,
    left: Float,
    right: Float,
    y: Float,
    dyePenResult: DyePenResult? = null,
) {
    val boxSize = 10f
    var x = left

    fun drawCheckbox(marked: Boolean) {
        c.drawRect(x, y - boxSize, x + boxSize, y, Paint(text).apply { style = Paint.Style.STROKE; strokeWidth = 1f })
        if (marked) {
            // Inset X so the strokes read inside the box rather than merging with its border.
            val inset = 1.6f
            val mark = Paint(text).apply { style = Paint.Style.STROKE; strokeWidth = 1.2f }
            c.drawLine(x + inset, y - boxSize + inset, x + boxSize - inset, y - inset, mark)
            c.drawLine(x + inset, y - inset, x + boxSize - inset, y - boxSize + inset, mark)
        }
        x += boxSize + 4f
    }

    c.drawText("Dye pen inspection: ", x, y, text)
    x += text.measureText("Dye pen inspection: ")

    drawCheckbox(marked = dyePenResult == DyePenResult.PASS)
    c.drawText("PASS", x, y, text); x += text.measureText("PASS") + 20f

    drawCheckbox(marked = dyePenResult == DyePenResult.FAIL)
    c.drawText("FAIL", x, y, text); x += text.measureText("FAIL") + 24f

    // Notes fill-in line
    c.drawText("Notes:", x, y, text); x += text.measureText("Notes:") + 6f
    c.drawLine(x, y + 2f, right, y + 2f, Paint(text).apply { style = Paint.Style.STROKE; strokeWidth = 0.7f })
}

// ──────────────────────────────────────────────────────────────────────────────
// Constants
// ──────────────────────────────────────────────────────────────────────────────

private const val WEAR_OUTLINE_PT = 2.0f
private const val WEAR_DIM_PT     = 1.2f
private const val WEAR_TEXT_PT    = 10f
private const val WEAR_MARGIN_PT  = 36f

private const val WEAR_HEADER_HEIGHT_PT       = 36f   // 2-line header block height
private const val WEAR_HEADER_HEIGHT_BLANK_PT = 56f   // blank write-in header: taller so the writing rules have hand-writing room (device feedback)
private const val WEAR_HEADER_BLANK_LINE_GAP_PT = 24f // baseline gap between the two blank header rule lines (printed header keeps ts*1.4)
private const val WEAR_HEADER_GAP_PT          = 16f   // gap from header rule to drawing area
private const val WEAR_OAL_ABOVE_SHAFT_PT     = 44f   // gap from shaft top edge to OAL line: on the strip-bearing wear sheet nothing else occupies that band, so 44 keeps the dimension line + its label clear of the shaft and returns the rest of the height to the detail strips (device feedback)

// Profile-band budget (device feedback): what the band actually needs, so it can
// shrink toward that instead of absorbing every spare point as white space.
private const val WEAR_PROFILE_NAMES_ROW_PT = 26f   // names/direction row reserved under the shaft bottom (drawWearDirectionRef's ts+12 offset + descender)
private const val WEAR_PROFILE_BOTTOM_PAD_PT = 6f   // air under the names row before the strips gap
private const val WEAR_OAL_TOP_REGION_PT = 18f      // OAL label + air above the dimension line at the top of the profile band (matches the oalLineY clamp floor ts + 6, plus label height)
private const val WEAR_STRIP_HEIGHT_MAX_PT = 170f   // per-strip growth cap when the profile donates its slack
private const val WEAR_NOTES_BOTTOM_OFFSET_PT = 24f   // notes baseline above contentBot
private const val WEAR_NOTES_GAP_PT           = 28f   // gap from drawing area bottom to notes

// Wear detail strips — sizing/pagination constants live in WearStripLayout.kt
// (WEAR_STRIP_MAX_PER_PAGE, WEAR_STRIP_HEIGHT_PT, WEAR_MIN_PROFILE_HEIGHT_PT, etc.) since
// that file's layout math is what the unit tests exercise directly. These two are purely
// about THIS composer's own reserved space and stay local.
private const val WEAR_OVERFLOW_NOTE_HEIGHT_PT   = 16f  // reserved band for the "+N more liners" text note
private const val WEAR_PROFILE_RADIUS_MARGIN_PT  = 8f   // headroom above/below the shaft's actual drawn radius

/**
 * Black alpha of a detail strip's wear-area fill, from the user-set `PdfPrefs.wearBandShadeFrac`
 * (Settings → Drawing → "Wear area shade" and the wear preview's PDF options sheet). The
 * default, [PDF_WEAR_BAND_SHADE_DEFAULT], is the light grey wash the sheet's other shaded fills
 * use.
 *
 * The band is where pits get marked, by the printed "X"s and by the machinist's pen on the
 * printed sheet, and a fill any heavier — a diagonal hatch above all — buries both (on-device
 * report); that is what the pref's cap protects. The MAIN profile's bands are a different mark
 * and keep their vertical strokes ([drawVerticalBand]), the shop convention there.
 */
private fun wearBandShadeAlpha(frac: Float): Int =
    (frac.coerceIn(PDF_WEAR_BAND_SHADE_MIN, PDF_WEAR_BAND_SHADE_MAX) * 255f).roundToInt()

private const val COMPRESS_TRIGGER_PT = 220f
// Classic central gap; breakPairLayout may widen it to keep the pair clear.
private const val ZIGZAG_GAP_MAX_PT   = 20f

// Measured-Ø callouts (geom/WearDiaCalloutLayout.kt drives placement; these size the text
// band and clearances on both surfaces — profile + detail strips).
private const val WEAR_DIA_TEXT_PT           = 8f   // value label text size
private const val WEAR_DIA_MIN_GAP_PT        = 5f   // min clear gap between label edges / leader drops
private const val WEAR_DIA_ROW_GAP_PT        = 3f   // vertical gap between the two label rows
private const val WEAR_DIA_PROFILE_LEADER_PT = 10f  // leader region reserved below the names row (profile band only; strips reuse the label headroom)
private const val WEAR_DIA_TICK_OVERSHOOT_PT = 2f   // witness tick overshoot past the cylinder edges (strips)
