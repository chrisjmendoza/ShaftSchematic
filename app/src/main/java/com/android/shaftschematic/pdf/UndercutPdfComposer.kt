package com.android.shaftschematic.pdf

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.android.shaftschematic.geom.ClampedUndercutSpanMm
import com.android.shaftschematic.geom.SetPositions
import com.android.shaftschematic.geom.SurfaceSeg
import com.android.shaftschematic.geom.UndercutLinerSpan
import com.android.shaftschematic.geom.UndercutNotch
import com.android.shaftschematic.geom.UndercutSpanMm
import com.android.shaftschematic.geom.UndercutStrip
import com.android.shaftschematic.geom.buildUndercutNotches
import com.android.shaftschematic.geom.LinerOutlineEdge
import com.android.shaftschematic.geom.UndercutLinerOd
import com.android.shaftschematic.geom.linerOutlineSegments
import com.android.shaftschematic.geom.buildUndercutStrips
import com.android.shaftschematic.geom.buriedUndercutLinerEdgesMm
import com.android.shaftschematic.geom.clampUndercutSpan
import com.android.shaftschematic.geom.computeOalWindow
import com.android.shaftschematic.geom.computeSetPositionsInMeasureSpace
import com.android.shaftschematic.geom.linerStripFor
import com.android.shaftschematic.geom.undercutSetReachMm
import com.android.shaftschematic.geom.effectiveUndercutTotalRail
import com.android.shaftschematic.geom.NOTCH_FACE_MIN_STEP_PX
import com.android.shaftschematic.geom.UNDERCUT_SECTION_FILL_ALPHA
import com.android.shaftschematic.geom.UNDERCUT_BREAK_AMP_FRAC_OF_R
import com.android.shaftschematic.geom.withUndercutBreakRoom
import com.android.shaftschematic.geom.maxOuterDiaOver
import com.android.shaftschematic.geom.outerDiaAt
import com.android.shaftschematic.geom.DiaCalloutPlan
import com.android.shaftschematic.geom.planDiaCallouts
import com.android.shaftschematic.geom.hatchSpan
import com.android.shaftschematic.geom.reliefSpan
import com.android.shaftschematic.geom.shoulderEndMm
import com.android.shaftschematic.geom.threadSurfaceSegs
import com.android.shaftschematic.model.*
import com.android.shaftschematic.settings.PDF_ARROW_SIZE_DEFAULT_PT
import com.android.shaftschematic.settings.PdfPrefs
import com.android.shaftschematic.ui.resolved.ResolvedComponent
import com.android.shaftschematic.ui.resolved.bodyBlends
import com.android.shaftschematic.ui.resolved.linerSurfaceSegs
import com.android.shaftschematic.ui.resolved.surfaceSegsFrom
import com.android.shaftschematic.util.DisplayUnits
import com.android.shaftschematic.util.DualLabel
import com.android.shaftschematic.util.PdfPageOrientation
import com.android.shaftschematic.util.VerboseLog
import com.android.shaftschematic.util.DualUnitLayout
import com.android.shaftschematic.util.drawDualLabelCentered
import com.android.shaftschematic.util.measureDualLabel
import com.android.shaftschematic.util.UnitSystem
import com.android.shaftschematic.util.buildLinerTitleById
import com.android.shaftschematic.util.drawRichText
import com.android.shaftschematic.util.measureRichText
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// ──────────────────────────────────────────────────────────────────────────────
// Public entry point
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Sheet title, printed centred on the header's second line in both modes. One constant so
 * the shop's preferred wording ("WELD UNDERCUTS", …) is a one-line change.
 */
const val UNDERCUT_DOC_TITLE = "UNDERCUT RECORD"

/**
 * UndercutPdfComposer
 *
 * Generates the undercut drawing PDF page — the printable record of a shaft's undercut
 * sections (see `docs/archive/UndercutDrawing_PLAN.md` §8): header, an orientation row, and the
 * zoomed detail strips — one per **liner holding cuts** plus one per bare-shaft cluster
 * (chained dimension rail + total above, measured-Ø callouts below, anchor-from-SET title).
 *
 * ## The strips own the page
 * A real undercut drawing shows ONLY the zoomed sections — the feature's reference hand
 * sketch has no whole-shaft view at all — so whenever there is at least one detail strip the
 * main profile and its SET-to-SET OAL line are **not drawn**. The reader still needs to know
 * which way the shaft runs, so a single "← AFT / FWD →" row spanning the content width stays
 * above the strips; everything the profile used to occupy goes to strip legibility (rail
 * separation, label air, Ø-callout room — see [computeUndercutStripInnerLayout]'s cylinder
 * cap, which stops the reclaimed band from becoming one drawn slab).
 *
 * ## Page layout (US Letter; landscape 792 × 612 pt shown)
 * ```
 * ┌─── header: job info line / "UNDERCUT RECORD" ─────────────────────────────┐
 * │   ← AFT                                                          FWD →    │
 * │   [detail strip: total rail / chained rail / profile / Ø / name + SET]    │
 * │   [detail strip: …]                                                       │
 * │   Notes: ______________________________________________________________   │
 * └───────────────────────────────────────────────────────────────────────────┘
 * ```
 * The page shape is the caller's ([undercutSheetOrientation], the job's
 * [UndercutRecord.pageOrientation] against the strip count; this composer reads it off
 * `page.info`). Landscape lays one strip at full width and two or more in a 2-column grid;
 * PORTRAIT stacks every strip in ONE column, each the full content width, top → bottom
 * AFT → FWD ([undercutStripCells]) — the shop's layout for several sections (on-device request).
 * A portrait page with room sizes each strip to what it draws — one shaft height down the page,
 * the title under its own figure with [UC_PORTRAIT_TITLE_AIR_PT] of air — and spreads the spare
 * height between sections; a crowded one gives every strip the same shaft height, a busy section
 * taking height from a plain one ([undercutStripCells]).
 *
 * ## Nothing recorded yet — started liner strips
 * A page with **no recorded cuts** — the blank write-in template (whose record is dropped
 * outright) and a non-blank export of an empty record alike — draws one *started* strip per
 * drawable liner instead: the liner at its true scale and station, but no notches, no rail
 * spans beyond the two chain-datum witness bars, and a write-in anchor title. The machinist
 * sketches the sections in by hand, so the sheet must give them the liner's stations and clear
 * space above and below.
 *
 * How much of the liner is printed depends on which page it is:
 * - **Blank write-in template** (`blankValues`) — *starting geometry only*: the liner's two
 *   vertical end faces, the neighbour stock outboard of them (normal outline + fill) and the
 *   break edges. Its span carries **no fill and no top/bottom surface lines**, so the middle is
 *   clear paper for the hand-drawn liner surface and cuts.
 * - **Export of an empty record** — the liner's zoomed profile as usual (true edges, neighbour
 *   slivers, break edges, cylinder), just with nothing recorded on it.
 *
 * The whole-shaft profile form survives ONLY as the last fallback: a shaft with no cuts AND no
 * drawable liner has nothing to strip, and would otherwise print an empty sheet.
 *
 * ## What one strip covers
 * A cut inside a liner draws the **whole liner**, wear-style: its true edges visible, a
 * sliver of the neighbouring stock beyond each edge, break edges past that, and room for a
 * cut that overhangs a liner edge (the notch simply continues onto the body). The chained
 * rail's outer witness lines then land on the **liner's own edges**, not on the zoom pad —
 * an arbitrary margin is not a figure worth printing (on-device report: a padded window with
 * no visible liner edges printed as an anonymous grey slab). Bare-shaft cuts keep the padded
 * cluster window; `geom/UndercutMath.kt`'s `buildUndercutStrips` decides which is which.
 *
 * Undercuts are a **reference-only** feature: nothing here feeds geometry back into the
 * model. Every notch on both draw sites (this composer and the canvas overlay) comes from the
 * ONE shared builder, `geom/UndercutOverlayMath.kt`'s `buildUndercutNotches` over
 * `surfaceSegsFrom(resolved)` — clamped spans, the containment forest, region topology at the
 * TRUE floor, and the drawn floors re-scaled for DRAWING only (once per sheet, against
 * `deepestUndercutDepthMm` and the record's `exaggerationFrac`). This file maps those notches
 * to points and draws them; it derives no geometry of its own, which is what keeps the printed
 * sheet and the screen from disagreeing about where material was removed — nested cuts, drawn
 * as a staircase off each parent's floor, included.
 *
 * Same public contract as `composeWearPdf`/`composeRunoutPdf`: a US Letter page already started
 * by the caller (landscape, or portrait when [undercutSheetOrientation] says so);
 * `resolvedComponents`, when provided, replace `spec.bodies` for the drawn profile
 * (`withResolvedBodies`) and supply the surface envelope the notches are cut against.
 *
 * @param page    Target PDF page (US Letter, already started; portrait when its height
 *                exceeds its width).
 * @param spec    Shaft specification in millimeters.
 * @param project Job information (customer, vessel, job#, side).
 * @param unit    Display unit for every printed dimension.
 * @param undercutRecord Recorded undercut sections. Strips (`buildUndercutStrips`, or one
 *                started strip per drawable liner when nothing is recorded) decide the page
 *                mode: 1 → one full-width strip, 2+ → a 2-column grid (portrait: one
 *                full-width column) with a "+N more" note past the cap, 0 → the whole-shaft
 *                profile form.
 * @param blankValues Blank-draft (write-in) mode: the record is dropped entirely
 *                (`UndercutRecord()`) — the wear sheet's decision that blank templates carry
 *                no recorded stations — so the page comes out as started liner strips with
 *                header writing rules, drawn as end faces + slivers only (see above).
 */
fun composeUndercutPdf(
    page: PdfDocument.Page,
    spec: ShaftSpec,
    project: ProjectInfo,
    unit: UnitSystem,
    /**
     * Per-component display-unit overrides + the sheet-wide dual (inline "primary [secondary]")
     * flag. Defaults to a single-unit resolver equivalent to [unit] everywhere, reproducing
     * today's output exactly. A liner strip resolves its own unit via [DisplayUnits.unitFor];
     * a bare-shaft strip or the OAL line (no component) uses [DisplayUnits.documentUnit].
     */
    displayUnits: DisplayUnits = DisplayUnits.single(unit),
    pdfPrefs: PdfPrefs = PdfPrefs(),
    resolvedComponents: List<ResolvedComponent>? = null,
    undercutRecord: UndercutRecord = UndercutRecord(),
    lineThicknessScale: Float = 1.0f,
    blankValues: Boolean = false,
) {
    val c = page.canvas
    c.drawColor(Color.WHITE)

    val docSpec = spec.withResolvedBodies(resolvedComponents)
    val effectiveRecord = if (blankValues) UndercutRecord() else undercutRecord
    // "Total length of cuts": the job's own choice, else the Settings → PDF Export default. Read off
    // the CALLER's record, not effectiveRecord — a blank draft drops the cuts, not the job's
    // dimensioning choice (a started strip plans no total either way).
    val showTotalRail = effectiveUndercutTotalRail(undercutRecord.showTotalRail, pdfPrefs.undercutTotalRail)
    // Shared liner display titles — custom label wins, else positional AFT/MID/FWD defaults
    // (util/LinerTitles.kt). Same names the carousel cards, the wear sheet, and the runout
    // sheet show, so a liner-anchored strip is identifiable at a glance.
    val linerTitles = buildLinerTitleById(docSpec)

    val pageW = page.info.pageWidth.toFloat()
    val pageH = page.info.pageHeight.toFloat()

    // ── Paints ──────────────────────────────────────────────────────────────
    val thicknessScale = lineThicknessScale.coerceIn(0.5f, 2.0f)
    val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = UC_OUTLINE_PT * thicknessScale; color = Color.BLACK
    }
    val dim = Paint(outline).apply { strokeWidth = UC_DIM_PT * thicknessScale }
    val text = sheetTextPaint(UC_TEXT_PT)
    fun shadeFill() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; color = Color.argb(40, 0, 0, 0)
    }
    // The liner outline (`PdfPrefs.undercutLinerOutline`): a fine grey dotted retrace of each
    // liner's original edges over its cuts, at the dimension weight. Null = not drawn.
    val linerOutlinePaint: Paint? = if (pdfPrefs.undercutLinerOutline) {
        Paint(dim).apply {
            color = Color.argb(150, 0, 0, 0)
            pathEffect = DashPathEffect(floatArrayOf(1.5f, 3f), 0f)
        }
    } else {
        null
    }
    // §7 degradation: stacked dual values only if the tightest strip on the page can carry the
    // taller rail rows AND still draw its cut — decided once, for the whole sheet.
    val wantDualStacked = displayUnits.dual && pdfPrefs.dualUnitLayout == DualUnitLayout.STACKED
    // Every fill on this sheet, decided once by the pure [undercutPdfFillPlan]: each component
    // kind follows its own "Shade in Components" preference, the detail strips ALWAYS shade the
    // liner whatever `shadedLiners` says (the notch voids are pure white, so the grey liner is
    // what gives the boxed cut sections their contrast — on-device request), and
    // `PdfPrefs.undercutLineArt` drops all of it, the strip liner and the section core included.
    // The blank template's edges-only started strip draws no liner span either way, so it stays
    // clear paper; the whole-shaft fallback form (no strips) takes the plain liner decision.
    val fills = undercutPdfFillPlan(pdfPrefs)
    val bodyFill: Paint? = if (fills.body) shadeFill() else null
    val taperFill: Paint? = if (fills.taper) shadeFill() else null
    val linerFill: Paint? = if (fills.liner) shadeFill() else null
    val stripLinerFill: Paint? = if (fills.stripLiner) shadeFill() else null
    // A notch is a VOID: the removed material is painted out in the page colour so the
    // surface stroke and any shading fill are erased inside the cut, then the shoulders and
    // floor are outlined on top.
    val voidFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.WHITE }
    val diaText = Paint(text).apply { textSize = UC_DIA_TEXT_PT }

    // ── Page geometry ─────────────────────────────────────────────────────
    val margin = UC_MARGIN_PT
    val contentLeft = margin
    val contentRight = pageW - margin
    val contentW = contentRight - contentLeft
    val contentTop = margin
    val contentBot = pageH - margin

    // The blank write-in header is taller (handwriting room for the rules); the space it
    // costs is reclaimed from the profile→strips gap below, as on the wear sheet.
    val headerBottom = contentTop + (if (blankValues) UC_HEADER_HEIGHT_BLANK_PT else UC_HEADER_HEIGHT_PT)
    val notesY = contentBot - UC_NOTES_BOTTOM_OFFSET_PT
    val midTopFull = headerBottom + UC_HEADER_GAP_PT
    val midBotFull = notesY - UC_NOTES_GAP_PT

    // ── Strips + the geometry they are built from ─────────────────────────────
    // ONE source with the callers' page choice (`undercutSheetOrientation` counts the same
    // strips), so the page a caller starts and the layout drawn on it cannot disagree.
    val sheet = undercutSheetStrips(spec, resolvedComponents, undercutRecord, blankValues)
    val setPositions = sheet.setPositions
    val aftSetMm = sheet.aftSetMm
    val fwdSetMm = sheet.fwdSetMm

    // ── Horizontal scale: SET to SET, so the profile fills the page width ──────
    val drawSpanMm = (fwdSetMm - aftSetMm).coerceAtLeast(1f)
    val ptPerMm = contentW / drawSpanMm

    fun xAt(mm: Float): Float = contentLeft + (mm - aftSetMm) * ptPerMm
    fun rPx(diaMm: Float): Float = (diaMm * 0.5f) * ptPerMm

    val maxDiaMm = (docSpec.maxOuterDiaMm().takeIf { it > 0f } ?: 50f).coerceAtLeast(20f)

    // ── Undercut geometry (shared pipeline — see the file KDoc) ────────────────
    val surfaceSegs = sheet.surfaceSegs
    val shaftExtentMm = sheet.shaftExtentMm
    val clampedById = sheet.clampedById
    val liveSpans = sheet.liveSpans
    // Every notch on the sheet, from the ONE shared builder the canvas overlay runs
    // (`geom/UndercutOverlayMath.kt`): clamped spans, the containment forest, true floors, and
    // the per-sheet drawn-depth normalization — resolved once, so the printed sheet and the
    // screen cannot disagree about where material was removed OR how deep it draws. Nested cuts
    // come back cut against their parent's floor, parents before children.
    val sheetNotches = buildUndercutNotches(
        effectiveRecord.undercuts, surfaceSegs, shaftExtentMm,
        exaggerationFrac = effectiveRecord.exaggerationFrac,
    )
    val pageStrips = sheet.strips
    // Started strips carry no cuts to dimension: rails, notches and printed values all drop out.
    val startedPage = sheet.startedPage
    // The page shape is the caller's: it started the page `undercutSheetOrientation` chose.
    val portrait = pageH > pageW
    val mode = determineUndercutPdfMode(pageStrips.size)
    val maxPerPage = undercutStripsPerPage(mode, portrait)
    val onPage = pageStrips.take(maxPerPage)
    val overflow = pageStrips.drop(maxPerPage)
    val overflowNoteH = if (overflow.isNotEmpty()) UC_OVERFLOW_NOTE_HEIGHT_PT else 0f

    val undercutById = effectiveRecord.undercuts.associateBy { it.id }
    // Per strip: the liner it zooms (null on a bare-shaft cluster), the unit it prints in — a
    // liner strip resolves the liner's own unit override; a bare-shaft cluster has no single
    // component to key off, so it falls back to the document unit — and, with
    // `PdfPrefs.undercutSplitAtLinerEdge` on, the buried liner edges some cut on the sheet runs past
    // into the body, which take chain nodes so the overhang is dimensioned against the edge's
    // position (`buildUndercutRailSpans`); off, every cut prints its full length.
    fun stripLinerOf(strip: UndercutStrip): Liner? = (strip as? UndercutStrip.LinerStrip)
        ?.let { ls -> docSpec.liners.firstOrNull { it.id == ls.linerId } }
    fun stripUnitOf(strip: UndercutStrip): UnitSystem = displayUnits.unitFor(stripLinerOf(strip)?.id)
    fun railSplitOf(strip: UndercutStrip): List<Float> = (strip as? UndercutStrip.LinerStrip)
        ?.takeIf { pdfPrefs.undercutSplitAtLinerEdge }
        ?.let { buriedUndercutLinerEdgesMm(it.linerStartMm, it.linerEndMm, liveSpans) }
        ?: emptyList()
    // The S.E.T. each strip measures to — its title anchor AND the lead continuing its chain —
    // from the ONE resolver (`undercutStripSetSide`), so the two never disagree.
    fun stripSideOf(strip: UndercutStrip): UndercutAnchorSide = undercutStripSetSide(
        strip, docSpec, setPositions, effectiveRecord.setDatum,
        strip.undercutIds.mapNotNull { id ->
            clampedById[id]?.takeIf { !it.isEmpty }?.let { UndercutSpanMm(id, it.startMm, it.endMm) }
        }.sortedBy { it.startMm },
    )

    // ── Header ───────────────────────────────────────────────────────────────
    drawSheetHeader(
        c, text, contentLeft, contentRight, contentTop, project,
        title = UNDERCUT_DOC_TITLE,
        headerHeightPt = UC_HEADER_HEIGHT_PT,
        headerHeightBlankPt = UC_HEADER_HEIGHT_BLANK_PT,
        blankLineGapPt = UC_HEADER_BLANK_LINE_GAP_PT,
        blankValues = blankValues,
        // A portrait page is 540 pt across, where an ordinary job line overflows: it sets smaller
        // before it loses the date and side. Landscape keeps its line as it always printed.
        minFitTextSizePt = if (portrait) UC_HEADER_MIN_FIT_TEXT_PT else 0f,
    )

    // Every section left off ("Sections to print"): header, one centred note, the notes row. Never
    // the whole-shaft profile fallback — that would print a drawing nobody asked for.
    if (sheet.noneSelected) {
        val note = Paint(text).apply { textAlign = Paint.Align.CENTER }
        c.drawText(
            ellipsizeToWidth(UNDERCUT_NONE_SELECTED_NOTE, note, contentW),
            (contentLeft + contentRight) * 0.5f, (midTopFull + midBotFull) * 0.5f, note,
        )
        drawUndercutNotesRow(c, text, contentLeft, contentRight, notesY)
        return
    }

    // ── Vertical banding ──────────────────────────────────────────────────────
    // Strips own the page (see the file KDoc): with at least one strip the main profile and
    // the OAL line are not drawn at all, and the strips take the whole drawing band under a
    // single orientation row. The profile form is the FALLBACK — a shaft with no cuts and no
    // drawable liner, which has nothing to strip and would otherwise print an empty sheet.
    val stripsOwnPage = onPage.isNotEmpty()
    val stripCells: List<WearStripCell>
    val overflowBandTop: Float

    if (stripsOwnPage) {
        drawUndercutDirectionRow(c, text, contentLeft, contentRight, midTopFull + text.textSize)
        val bandTop = midTopFull + UC_DIRECTION_ROW_PT
        // Zeroing the profile params hands the whole band to the strips; the banding functions
        // are otherwise the wear sheet's, unchanged (count-driven, content-agnostic). Per-strip
        // growth is deliberately UNCAPPED here — the reclaimed height is the point — with
        // `computeUndercutStripInnerLayout`'s cylinder cap keeping the drawn shaft in proportion.
        // Landscape: one full-width strip or the 2-column grid; portrait: one column, every strip
        // the full content width (`undercutStripCells`). Portrait first measures what each strip
        // stacks around its shaft, from the very plan the strip then draws: a page with room
        // sizes every strip to what it draws and spends the rest as air between them (a lone
        // forced-portrait strip centres), and on a crowded page a busy section takes height from
        // a plain one instead of drawing a sliver of a shaft.
        // The natural-height page (roomy by definition) is measured with every strip's chain
        // values LIFTED above the line — the layout `resolveRails` then draws on a cell that size;
        // the crowded paths keep the compressed overheads and each strip decides for itself.
        val portraitPlans = if (portrait) {
            onPage.map { strip ->
                planUndercutDetailStrip(
                    surfaceSegs, strip, strip.undercutIds.mapNotNull { id -> undercutById[id] },
                    clampedById, contentLeft, contentRight, stripUnitOf(strip), text, diaText,
                    shaftExtentMm, startedPage, displayUnits.dual, wantDualStacked,
                    railSplitOf(strip), pdfPrefs.arrowSizePt,
                    titleAirPt = UC_PORTRAIT_TITLE_AIR_PT,
                    leadSide = stripSideOf(strip), aftSetMm = aftSetMm, fwdSetMm = fwdSetMm,
                    showTotal = showTotalRail,
                )
            }
        } else {
            null
        }
        stripCells = undercutStripCells(
            portrait, onPage.size, bandTop, midBotFull, contentLeft, contentRight,
            reservedBottomPt = overflowNoteH,
            overheadsPt = portraitPlans?.map { it?.verticalOverheadPt(wantDualStacked) ?: 0f },
            liftedOverheadsPt = portraitPlans?.map { it?.verticalOverheadPt(wantDualStacked, lifted = true) ?: 0f },
        )
        overflowBandTop = stripCells.lastOrNull()?.bottom ?: bandTop
    } else {
        // ── Fallback: the whole-shaft profile form ────────────────────────────
        // The profile's minimum height protects the actual drawn shaft radius — ptPerMm is a
        // purely horizontal scale, so a wide/short shaft's true diameter could otherwise exceed
        // a shrunk profile band.
        val minProfileHeightPt = maxOf(WEAR_MIN_PROFILE_HEIGHT_PT, 2f * rPx(maxDiaMm) + UC_PROFILE_RADIUS_MARGIN_PT)
        val preferredProfileHeightPt = maxOf(
            UC_OAL_TOP_REGION_PT + UC_OAL_ABOVE_SHAFT_PT + 2f * rPx(maxDiaMm) +
                UC_PROFILE_NAMES_ROW_PT + UC_PROFILE_BOTTOM_PAD_PT,
            minProfileHeightPt,
        )
        val v = computeWearVerticalLayout(
            midTopFull, midBotFull, 0,
            minProfileHeightPt = minProfileHeightPt,
            preferredProfileHeightPt = preferredProfileHeightPt,
        )
        val profileTop = v.profileTop
        val profileBottom = v.profileBottom
        stripCells = emptyList()
        overflowBandTop = profileBottom

        val profileSlack = ((profileBottom - profileTop) - preferredProfileHeightPt).coerceAtLeast(0f)
        val shaftCy = (profileTop + UC_OAL_TOP_REGION_PT + UC_OAL_ABOVE_SHAFT_PT +
            rPx(maxDiaMm) + profileSlack * 0.5f)
            .coerceAtMost(profileBottom - rPx(maxDiaMm) - UC_PROFILE_NAMES_ROW_PT)
        val geomRect = RectF(contentLeft, profileTop, contentRight, profileBottom)
        val shaftTopApprox = shaftCy - rPx(maxDiaMm)
        val oalLineY = (shaftTopApprox - UC_OAL_ABOVE_SHAFT_PT).coerceAtLeast(profileTop + UC_TEXT_PT + 6f)

        drawUndercutOalLine(
            c, dim, text, contentLeft, contentRight, oalLineY, shaftTopApprox,
            displayUnits.documentUnit, spec.overallLengthMm, blankValues, dual = displayUnits.dual,
        )
        drawSimpleShaftProfile(
            c, docSpec, shaftCy, outline, geomRect, ::xAt, ::rPx,
            bodyFill = bodyFill, taperFill = taperFill, linerFill = linerFill,
            // Ratio of the outline's (already thickness-scaled) weight, so Settings ->
            // "Line thickness" reaches the secondary strokes here too — the wear sheet's
            // rule, applied to this document's profile as well.
            dimStrokeWidthPt = outline.strokeWidth * (UC_DIM_PT / UC_OUTLINE_PT),
        )
        // Notches on the full profile, at true position and the true (small) scale — the same
        // construction the strips draw zoomed, so nothing is a separate "marker style". Only
        // reachable with an empty record, so in practice this draws nothing; it stays because
        // the form is a *fallback*, not a separate drawing.
        drawUndercutNotches(
            c, sheetNotches, shaftCy, ::xAt, ::rPx, outline, voidFill,
            sectionCoreFill = fills.sectionCore,
        )
        drawLinerOutlinesPdf(c, docSpec, sheetNotches, shaftCy, ::xAt, ::rPx, linerOutlinePaint)
        drawUndercutDirectionRow(
            c, text, contentLeft, contentRight,
            (shaftCy + rPx(maxDiaMm) + text.textSize + 12f).coerceAtMost(profileBottom - 2f),
        )
    }

    // The stacked decision for the whole sheet: the tightest strip must still afford the taller
    // rail rows. Undercut rows are the app's most generous already (17 pt), so this rarely bites.
    val dualStacked = wantDualStacked && run {
        val probe = Paint(text).apply { textSize = (text.textSize - 2f).coerceAtLeast(7f) }
        val tightest = stripCells.minOfOrNull { it.bottom - it.top } ?: Float.MAX_VALUE
        wearStripAffordsStackedRail(
            stripHeightPt = tightest,
            titleHeightPt = (text.textSize - 1f).coerceAtLeast(7f),
            rowHeightPt = undercutRailRowHeightPt(probe, dualStacked = true),
        )
    }
    if (wantDualStacked && !dualStacked) {
        VerboseLog.i(VerboseLog.Category.PDF, "UndercutPdf") {
            "dual stacking: undercut sheet fell back to INLINE — the tightest strip cannot " +
                "carry stacked rail rows and still draw its cut"
        }
    }

    // ── Detail strips, one per liner / bare-shaft cluster ────────────────────
    onPage.forEachIndexed { i, strip ->
        val cell = stripCells[i]
        // A liner strip's title anchor is the LINER's own edge-to-SET datum — the same
        // figure the schematic and the wear sheet print for this liner — never a cut's
        // shoulder (on-device report: a cut 11.5 in into a liner 20 in from the AFT SET
        // printed the liner as 31.5 in out). Cuts are located on the chain rail, measured
        // from the liner's edges.
        val stripLiner = stripLinerOf(strip)
        val stripUnit = stripUnitOf(strip)
        val stripSide = stripSideOf(strip)
        drawUndercutDetailStrip(
            c, docSpec, surfaceSegs, strip,
            strip.undercutIds.mapNotNull { id -> undercutById[id] },
            clampedById,
            cell.top, cell.bottom, cell.left, cell.right,
            stripUnit, aftSetMm, fwdSetMm, text, outline, dim, diaText,
            bodyFill = bodyFill, taperFill = taperFill, linerFill = stripLinerFill,
            shaftExtentMm = shaftExtentMm,
            voidFill = voidFill,
            linerTitle = (strip as? UndercutStrip.LinerStrip)?.let { linerTitles[it.linerId] },
            // The liner's EDGE on the chosen side to that S.E.T. — NEAREST is the nearer edge, the
            // figure the schematic and the wear sheet print for this liner.
            linerAnchorLabel = stripLiner
                ?.let {
                    buildLinerAnchorLabel(
                        docSpec, it, setPositions, stripUnit, displayUnits.dual, forced = stripSide.toLinerAnchor(),
                    )
                }
                ?.takeIf { it.isNotBlank() },
            linerAnchorAlignRight = stripSide == UndercutAnchorSide.FWD_SET,
            leadSide = stripSide,
            // A started strip is a write-in figure whatever the caller asked for: its title is
            // the circle-one anchor rule, and it has no recorded value to print anywhere.
            blankValues = blankValues || startedPage,
            startedStrip = startedPage,
            // Edges-only is the write-in TEMPLATE's look alone: an export of an empty record is
            // still a record of this shaft, so it keeps the liner fully drawn.
            linerSpanBlank = startedPage && blankValues,
            // This strip's slice of the sheet's notches, in the builder's parents-before-children
            // order — never rebuilt here, so a strip's staircase is the sheet's staircase.
            notches = sheetNotches.filter { it.id in strip.undercutIds },
            sectionCoreFill = fills.sectionCore,
            linerOutlinePaint = linerOutlinePaint,
            linerOdLabel = stripLiner
                ?.let { "Ø" + formatDiaWithUnitDual(it.odMm.toDouble(), stripUnit, displayUnits.dual) },
            dual = displayUnits.dual,
            dualStacked = dualStacked,
            railSplitAtMm = railSplitOf(strip),
            // The app-wide "Dimension arrows" head — the same pref the schematic's rails read.
            arrowSizePt = pdfPrefs.arrowSizePt,
            showTotal = showTotalRail,
            // Portrait strips are sized to what they draw, so the title gets its own air under
            // the Ø callouts; landscape keeps today's banding exactly.
            titleAirPt = if (portrait) UC_PORTRAIT_TITLE_AIR_PT else 0f,
        )
    }
    if (overflow.isNotEmpty()) {
        drawUndercutOverflowNote(c, text, contentLeft, contentRight, overflowBandTop, midBotFull, overflow.size)
    }

    // ── Notes row ────────────────────────────────────────────────────────────
    // Notes only: the dye-pen PASS/FAIL checkboxes are a wear/inspection concern and have no
    // place on a machining record (`docs/archive/UndercutDrawing_PLAN.md` §11.4).
    drawUndercutNotesRow(c, text, contentLeft, contentRight, notesY)
}

// ──────────────────────────────────────────────────────────────────────────────
// The sheet's strips — one source for the composer and the callers' page choice
// ──────────────────────────────────────────────────────────────────────────────

/**
 * What [undercutSheetStrips] resolves for one undercut sheet: the detail [strips] it draws, AFT →
 * FWD as `buildUndercutStrips` orders them, and the geometry they were built from, which the
 * composer reuses rather than re-derives.
 *
 * @property strips Cut strips, or — when nothing is recorded — one started strip per drawable
 *   liner. Empty only when the sheet falls back to the whole-shaft profile form. The FULL list:
 *   the composer applies its per-page cap and prints the rest as a "+N more" note.
 * @property startedPage True when [strips] are started strips (no cuts on the sheet).
 * @property setPositions The page's own S.E.T.s (`computeSetPositionsInMeasureSpace`).
 * @property surfaceSegs The outer-surface envelope every notch is cut against.
 * @property shaftExtentMm The extent cut spans are clamped to: OAL or the FWD S.E.T., the larger.
 * @property clampedById Every recorded cut's span clamped to [shaftExtentMm], by id.
 * @property liveSpans The clamped spans that are not empty.
 */
internal data class UndercutSheetStrips(
    val strips: List<UndercutStrip>,
    val startedPage: Boolean,
    val setPositions: SetPositions,
    val surfaceSegs: List<SurfaceSeg>,
    val shaftExtentMm: Float,
    val clampedById: Map<String, ClampedUndercutSpanMm>,
    val liveSpans: List<UndercutSpanMm>,
    /**
     * Every section the sheet could print, BEFORE the "Sections to print" filter — the list the
     * election UI offers, in the order the sheet draws it. [strips] is this list filtered by
     * [isUndercutStripPrinted].
     */
    val allStrips: List<UndercutStrip> = strips,
) {
    val aftSetMm: Float get() = setPositions.aftSETxMm.toFloat()
    val fwdSetMm: Float get() = setPositions.fwdSETxMm.toFloat()

    /**
     * There were sections to print and the job left every one of them off: the sheet prints a
     * "no sections selected" note — never the whole-shaft profile fallback, which would print a
     * drawing nobody asked for.
     */
    val noneSelected: Boolean get() = allStrips.isNotEmpty() && strips.isEmpty()
}

/**
 * The detail strips [composeUndercutPdf] draws for this job, with the geometry behind them —
 * the ONE derivation behind both the composer and [undercutSheetOrientation], so the page a
 * caller starts and the layout the composer draws on it count the same sections.
 *
 * [blankValues] drops the record, as the composer does (a blank template carries no recorded
 * cuts), so a blank draft counts its started liner strips. [resolvedComponents], when provided,
 * replace `spec.bodies` for the drawn geometry and supply the surface envelope.
 *
 * The job's "Sections to print" election ([isUndercutStripPrinted], read off [undercutRecord] itself
 * so a blank draft keeps it) is applied HERE, after the strips are built — grouping is unchanged —
 * so the composer, the section count, the page choice and every caption count the same PRINTED
 * sections.
 */
internal fun undercutSheetStrips(
    spec: ShaftSpec,
    resolvedComponents: List<ResolvedComponent>?,
    undercutRecord: UndercutRecord,
    blankValues: Boolean,
): UndercutSheetStrips {
    val docSpec = spec.withResolvedBodies(resolvedComponents)
    val effectiveRecord = if (blankValues) UndercutRecord() else undercutRecord

    val oalWindow = computeOalWindow(spec)
    val setPositions = computeSetPositionsInMeasureSpace(oalWindow, spec)
    val aftSetMm = setPositions.aftSETxMm.toFloat()
    val fwdSetMm = setPositions.fwdSETxMm.toFloat()

    // The surface envelope comes from the SAME mapping the canvas overlay uses
    // (`surfaceSegsFrom`), so both sites cut notches against an identical local surface.
    // With no resolved list the drawn spec is the only source available, so its components
    // stand in — auto-body spans are already folded into `docSpec.bodies` by
    // `withResolvedBodies` whenever a resolved list WAS supplied.
    val surfaceSegs = resolvedComponents?.let { surfaceSegsFrom(it, bodyBlends(spec, it)) }
        ?: surfaceSegsFromSpec(docSpec)
    val shaftExtentMm = max(spec.overallLengthMm, fwdSetMm)
    val clampedById: Map<String, ClampedUndercutSpanMm> = effectiveRecord.undercuts.associate { u ->
        u.id to clampUndercutSpan(u.startFromAftMm, u.lengthMm, shaftExtentMm)
    }
    val liveSpans = effectiveRecord.undercuts.mapNotNull { u ->
        val s = clampedById[u.id] ?: return@mapNotNull null
        if (s.isEmpty) null else UndercutSpanMm(u.id, s.startMm, s.endMm)
    }
    // Strip source: a cut overlapping a liner joins that liner's strip (whole liner drawn,
    // wear-style); the leftovers cluster into padded bare-shaft windows. Degenerate liners
    // (no length or no OD) would only produce a blank cell, so they are not offered — the
    // same drawable filter `collectWearLinerGroups` applies.
    val linerSpans = docSpec.liners
        .filter { it.lengthMm > 0f && it.odMm > 0f }
        .map { ln -> UndercutLinerSpan(ln.id, ln.startFromAftMm, ln.startFromAftMm + ln.lengthMm) }
    // "Draw to the shaft end" (per-job): a liner strip near an S.E.T. chains out to it and draws
    // on to the shaft's physical end. The S.E.T.s are the page's own (`computeSetPositionsInMeasureSpace`,
    // the source `undercutSetPositions` reads), so the chain ends on the datum the sheet titles from.
    val setReachMm = undercutSetReachMm(effectiveRecord.drawToSet)
    val cutStrips = buildUndercutStrips(
        liveSpans, linerSpans, shaftExtentMm, segs = surfaceSegs,
        reachToSetMm = setReachMm, aftSetMm = aftSetMm, fwdSetMm = fwdSetMm,
    )
    // Nothing recorded (blank template, or an export of an empty record): every drawable liner
    // gets a STARTED strip — drawn to scale, left empty for hand sketching. `linerStripFor`
    // with no assigned cuts is exactly what the overlay uses to zoom an undercut-free liner,
    // so a started strip and a real one are the same figure at the same scale.
    val startedStrips: List<UndercutStrip> =
        if (cutStrips.isNotEmpty()) emptyList()
        else linerSpans.sortedBy { it.startMm }.map {
            linerStripFor(
                it, emptyList(), shaftExtentMm, segs = surfaceSegs,
                reachToSetMm = setReachMm, aftSetMm = aftSetMm, fwdSetMm = fwdSetMm,
            )
        }
    val allStrips = cutStrips.ifEmpty { startedStrips }
    return UndercutSheetStrips(
        strips = allStrips.filter { isUndercutStripPrinted(it, undercutRecord) },
        allStrips = allStrips,
        startedPage = cutStrips.isEmpty(),
        setPositions = setPositions,
        surfaceSegs = surfaceSegs,
        shaftExtentMm = shaftExtentMm,
        clampedById = clampedById,
        liveSpans = liveSpans,
    )
}

/**
 * How many detail strips [composeUndercutPdf] draws for this job — the PRINTED sections ("Sections
 * to print" applied), before the per-page cap (a five-strip sheet is still two or more sections).
 * Blank draft included.
 */
fun undercutSheetStripCount(
    spec: ShaftSpec,
    resolvedComponents: List<ResolvedComponent>?,
    undercutRecord: UndercutRecord,
    blankValues: Boolean,
): Int = undercutSheetStrips(spec, resolvedComponents, undercutRecord, blankValues).strips.size

/**
 * The page every caller of [composeUndercutPdf] starts — preview, print, export and Export all
 * alike: the job's [UndercutRecord.pageOrientation] resolved against the number of strips the
 * sheet draws ([undercutSheetStripCount], blank draft included) by
 * [resolveUndercutPageOrientation]. The choice is read off [undercutRecord] itself, so a blank
 * draft keeps a job's forced shape. A sheet with every section left off prints its one-line note on
 * a landscape page whatever the choice.
 */
fun undercutSheetOrientation(
    spec: ShaftSpec,
    resolvedComponents: List<ResolvedComponent>?,
    undercutRecord: UndercutRecord,
    blankValues: Boolean,
): PdfPageOrientation {
    val sheet = undercutSheetStrips(spec, resolvedComponents, undercutRecord, blankValues)
    if (sheet.noneSelected) return PdfPageOrientation.LANDSCAPE
    return resolveUndercutPageOrientation(undercutRecord.pageOrientation, sheet.strips.size)
}

/**
 * True when the job leaves EVERY section of its undercut sheet off ("Sections to print") — the sheet
 * then has nothing to print: the tab disables Print and Export, and "Export all" skips it.
 */
fun undercutSheetNoneSelected(
    spec: ShaftSpec,
    resolvedComponents: List<ResolvedComponent>?,
    undercutRecord: UndercutRecord,
    blankValues: Boolean,
): Boolean = undercutSheetStrips(spec, resolvedComponents, undercutRecord, blankValues).noneSelected

/** The note a sheet with every section left off prints in place of its strips. */
const val UNDERCUT_NONE_SELECTED_NOTE = "No undercut sections selected for this sheet."

// ──────────────────────────────────────────────────────────────────────────────
// OAL line + direction reference
// ──────────────────────────────────────────────────────────────────────────────

/**
 * SET-to-SET dimension line above the profile, with witness lines down to the shaft and the
 * typed OAL seated in a break cut mid-span — identical rules to the wear/runout sheets, so
 * every drawing output states the shaft's overall length the same way. Blank mode cuts the
 * break and leaves it empty for handwriting.
 */
private fun drawUndercutOalLine(
    c: Canvas, dim: Paint, text: Paint,
    x0: Float, x1: Float,
    oalLineY: Float, shaftTopY: Float,
    unit: UnitSystem, oalMm: Float,
    blankValues: Boolean,
    /** OAL is a whole-shaft dimension, not component-keyed — one dual flag for the sheet. */
    dual: Boolean = false,
) {
    val arrowLen = 8f
    val witnessGap = 3f
    val witnessExt = 5f

    c.drawLine(x0, shaftTopY - witnessGap, x0, oalLineY - witnessExt, dim)
    c.drawLine(x1, shaftTopY - witnessGap, x1, oalLineY - witnessExt, dim)

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
            // Span too short to host the break plus inward arrows: continuous line, label
            // above — PdfDimensionRenderer's fallback rule.
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
 * "← AFT" / "FWD →" on one baseline spanning the content width — the app-wide orientation
 * convention. On a strips-own-page sheet this row is the ONLY whole-shaft reference left, so
 * it sits directly under the header; on the profile-form fallback it sits under the shaft.
 */
private fun drawUndercutDirectionRow(
    c: Canvas, text: Paint, left: Float, right: Float, baselineY: Float,
) {
    val p = Paint(text)
    p.textAlign = Paint.Align.LEFT
    c.drawText("← AFT", left, baselineY, p)
    p.textAlign = Paint.Align.RIGHT
    c.drawText("FWD →", right, baselineY, p)
}

/**
 * Outer-surface segments derived straight from the drawn spec — the fallback used when the
 * caller supplied no resolved component list. Mirrors `surfaceSegsFrom`'s rules (bodies
 * constant Ø, liners constant Ø or stepped through the SAME `linerSurfaceSegs` a shouldered
 * liner resolves to, tapers linear, threads at their major-Ø envelope with a relief groove at
 * its own Ø through the SAME `threadSurfaceSegs` a resolved thread maps to, coupler bolt slots
 * skipped as radial cutouts rather than surface material).
 */
private fun surfaceSegsFromSpec(spec: ShaftSpec): List<SurfaceSeg> = buildList {
    spec.bodies.forEach { add(SurfaceSeg(it.startFromAftMm, it.startFromAftMm + it.lengthMm, it.diaMm, it.diaMm)) }
    spec.liners.forEach {
        addAll(
            linerSurfaceSegs(
                startMm = it.startFromAftMm,
                endMm = it.startFromAftMm + it.lengthMm,
                odMm = it.odMm,
                aft = it.shoulderOn(LinerAuthoredReference.AFT),
                fwd = it.shoulderOn(LinerAuthoredReference.FWD),
            )
        )
    }
    spec.tapers.forEach { add(SurfaceSeg(it.startFromAftMm, it.startFromAftMm + it.lengthMm, it.startDiaMm, it.endDiaMm)) }
    spec.threads.forEach {
        addAll(threadSurfaceSegs(it.startFromAftMm, it.lengthMm, it.majorDiaMm, it.reliefLenMm, it.reliefDiaMm, it.reliefAtAft))
    }
}.filter { it.endMm > it.startMm && (it.diaStartMm > 0f || it.diaEndMm > 0f) }

// ──────────────────────────────────────────────────────────────────────────────
// Notches — the void cut into the surface, drawn identically at every scale
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Draws every notch in [notches] as a **step in the silhouette** — the hand-sketch
 * convention: the removed material is painted out in the page colour from the surface
 * polyline down to the floor (mirrored about the centreline, erasing the component's surface
 * stroke across the mouth — the cut is OPEN at the surface, never closed by a lid), a
 * full-height **section face** at each end (one vertical from top surface to bottom surface,
 * like any machined diameter step), and the floor lines across the span. Each undercut
 * thereby reads as its own reduced-Ø rectangle section seated between two faces — only the
 * undercut section exists over that span (on-device report: a lid along the surface plus the
 * surviving liner outline read as a white box pasted ON the liner instead of material removed
 * FROM it). A face draws only where there is a real step (surface meaningfully above the
 * floor); a taper that has run down to the floor leaves no face. Portions where the surface
 * is already at or below the floor yield no region and draw nothing, so a cut running off a
 * liner onto smaller stock simply stops at the liner edge.
 *
 * This function is pt-mapping and Canvas work ONLY. Spans, region topology, the local surface
 * a cut is taken against, and the drawn (depth-exaggerated) floors all come from
 * `geom/UndercutOverlayMath.kt`'s `buildUndercutNotches` — the same builder the canvas overlay
 * runs, which is what keeps the two draw sites identical and gives this composer nested cuts
 * with no code of its own: a child notch simply arrives with its parent's DRAWN floor as its
 * surface, paints over the relief around it, and reads as the next step down the staircase.
 */
/**
 * The liner outline on the printed sheet — the dotted retrace of each liner's original OD and end
 * faces over the stretches [notches] erased — the OD and shoulder steps mirrored about [cy], an
 * end face ONE full-height line from top OD to bottom OD through the body — from the ONE pure
 * builder the canvases also run ([linerOutlineSegments]). Drawn after the notch pass and before the Ø
 * callouts and rails. A null [paint] (the pref off) draws nothing.
 */
private fun drawLinerOutlinesPdf(
    c: Canvas,
    docSpec: ShaftSpec,
    notches: List<UndercutNotch>,
    cy: Float,
    xAt: (Float) -> Float,
    rAt: (Float) -> Float,
    paint: Paint?,
) {
    if (paint == null || notches.isEmpty()) return
    docSpec.liners.forEach { ln ->
        val endMm = ln.startFromAftMm + ln.lengthMm
        val liner = UndercutLinerOd(
            ln.id, ln.startFromAftMm, endMm, ln.odMm,
            surface = linerSurfaceSegs(
                ln.startFromAftMm, endMm, ln.odMm,
                ln.shoulderOn(LinerAuthoredReference.AFT), ln.shoulderOn(LinerAuthoredReference.FWD),
            ),
        )
        linerOutlineSegments(liner, notches).forEach { seg ->
            val r0 = rAt(seg.dia0Mm)
            val r1 = rAt(seg.dia1Mm)
            when (seg.edge) {
                LinerOutlineEdge.SURFACE -> {
                    val x0 = xAt(seg.x0Mm)
                    val x1 = xAt(seg.x1Mm)
                    c.drawLine(x0, cy - r0, x1, cy - r0, paint)
                    c.drawLine(x0, cy + r0, x1, cy + r0, paint)
                }
                LinerOutlineEdge.FULL_FACE -> {
                    val x = xAt(seg.x0Mm)
                    c.drawLine(x, cy - r1, x, cy + r1, paint)
                }
                LinerOutlineEdge.STEP -> {
                    val x = xAt(seg.x0Mm)
                    c.drawLine(x, cy - r1, x, cy - r0, paint)
                    c.drawLine(x, cy + r0, x, cy + r1, paint)
                }
            }
        }
    }
}

private fun drawUndercutNotches(
    c: Canvas,
    notches: List<UndercutNotch>,
    cy: Float,
    xAt: (Float) -> Float,
    rAt: (Float) -> Float,
    outline: Paint,
    voidFill: Paint,
    /**
     * Whether the section's core takes its light fill — [undercutPdfFillPlan]'s decision, off
     * under print line art. The void erase below runs either way: it is what removes the
     * component's surface stroke and shading across the cut, not a tone.
     */
    sectionCoreFill: Boolean,
) {
    if (notches.isEmpty()) return
    // One step LIGHTER than the liner shade (half its 40/255 alpha): the section's core is
    // erased to the page colour first, so this is its absolute tone, not a darkening overlay.
    val sectionFill = if (!sectionCoreFill) null else Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb((UNDERCUT_SECTION_FILL_ALPHA * 255f).roundToInt(), 0, 0, 0)
    }
    notches.forEach { notch ->
        if (notch.floorDiaMm <= 0f) return@forEach
        notch.profiles.forEach { np ->
            if (np.surface.size < 2) return@forEach
            val rFloor = rAt(np.floorDiaMm)
            val xStart = xAt(np.startMm)
            val xEnd = xAt(np.endMm)

            // Void fill, top half then its mirror below the centreline. The surface
            // boundary overdraws OUTWARD by the outline stroke width — the surface
            // stroke is centred on that line, and a fill stopping exactly there would
            // leave half of the *component's* stroke ragged across the mouth. Nothing
            // redraws over the mouth: the cut stays OPEN at the surface.
            val od = outline.strokeWidth
            listOf(-1f, 1f).forEach { sign ->
                val path = Path()
                np.surface.forEachIndexed { i, sp ->
                    val y = cy + sign * (rAt(sp.diaMm) + od)
                    if (i == 0) path.moveTo(xAt(sp.xMm), y) else path.lineTo(xAt(sp.xMm), y)
                }
                path.lineTo(xEnd, cy + sign * rFloor)
                path.lineTo(xStart, cy + sign * rFloor)
                path.close()
                c.drawPath(path, voidFill)
            }

            // Remaining core: erased to the page colour, then refilled one step lighter
            // than the liner shade so the section reads distinct (on-device request). Under
            // print line art the erase stands alone and the faces carry the section.
            c.drawRect(xStart, cy - rFloor, xEnd, cy + rFloor, voidFill)
            sectionFill?.let { c.drawRect(xStart, cy - rFloor, xEnd, cy + rFloor, it) }

            // Outline — the step-section construction: a full-height section face at each
            // end (top surface to bottom surface, through the core, only where the surface
            // actually stands above the floor) and the floor lines across the span. No lid:
            // the silhouette itself steps down, so each cut reads as its own reduced-Ø
            // rectangle between two faces (the hand-sketch convention).
            val rSurf0 = rAt(np.surface.first().diaMm)
            val rSurf1 = rAt(np.surface.last().diaMm)
            if (rSurf0 > rFloor + NOTCH_FACE_MIN_STEP_PX) {
                c.drawLine(xStart, cy - rSurf0, xStart, cy + rSurf0, outline)
            }
            if (rSurf1 > rFloor + NOTCH_FACE_MIN_STEP_PX) {
                c.drawLine(xEnd, cy - rSurf1, xEnd, cy + rSurf1, outline)
            }
            listOf(-1f, 1f).forEach { sign ->
                c.drawLine(xStart, cy + sign * rFloor, xEnd, cy + sign * rFloor, outline)
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Detail strip — one zoomed liner / bare-shaft cluster
// ──────────────────────────────────────────────────────────────────────────────

/**
 * The horizontal plan of one detail strip — everything [drawUndercutDetailStrip] resolves before
 * it splits the strip's height: the draw range and its x map, the text paints, the strip's cut
 * spans, the measured-Ø callout plan and the rails. Pure horizontal geometry, so the composer can
 * also measure a strip's vertical NEEDS ([verticalOverheadPt]) before it bands the page, from the
 * very plan the strip then draws.
 */
internal class UndercutStripPlan(
    val drawStartMm: Float,
    val drawEndMm: Float,
    val hLayout: WearStripHorizontalLayout,
    val titleText: Paint,
    val dimText: Paint,
    val spans: List<UndercutSpanMm>,
    val diaPlan: DiaCalloutPlan?,
    val diaBandPt: Float,
    val rails: UndercutStripRails,
    /**
     * Air between the Ø callouts and the title, reserved under the callout band. Zero on a
     * landscape sheet (byte-identical); a portrait page sizes each strip to what it draws, so with
     * no spare height inside the strip the title would otherwise sit right on the last callout.
     */
    val titleAirPt: Float = 0f,
) {
    fun xAt(mm: Float): Float = hLayout.linerLeftPt + (mm - drawStartMm) * hLayout.ptPerMm

    /** [rails] with the chain's fallback values lifted above the line ([UndercutStripRails.withFallbacksLifted]). */
    private val liftedRails: UndercutStripRails by lazy { rails.withFallbacksLifted() }

    /**
     * The strip's vertical split inside `[stripTop, stripBottom]` ([computeUndercutStripInnerLayout]),
     * for [rails] as planned or, [lifted], with the chain's fallback values lifted above the line.
     */
    fun innerLayout(
        stripTop: Float,
        stripBottom: Float,
        dualStacked: Boolean,
        cylMaxFloorPt: Float = UNDERCUT_CYL_MAX_FLOOR_PT,
        lifted: Boolean = false,
    ): UndercutStripInnerLayout {
        val r = if (lifted) liftedRails else rails
        // ONE row metric for the reserved band and the drawn rows (`drawUndercutStripRails` steps
        // by it): a stacked dual sheet's taller rows otherwise overrun the band into the shaft.
        val rowHeight = undercutRailRowHeightPt(dimText, dualStacked)
        return computeUndercutStripInnerLayout(
            stripTop, stripBottom,
            titleHeightPt = titleText.textSize,
            hasTotalRail = r.totalSpan != null,
            rowHeightPt = rowHeight,
            diaBandPt = if (diaPlan != null) diaBandPt + titleAirPt else diaBandPt,
            maxLabelRows = r.rows.belowRows,
            chainAboveBandPt = r.rows.aboveRows * rowHeight,
            cylMaxFloorPt = cylMaxFloorPt,
            // The chain-only compressed plan has no clear row under its last below-line value.
            belowClearPt = if (r.rows.chainBelowRows > 0 && r.rows.nestedRows == 0) {
                undercutRailBelowClearPt(dimText, dualStacked)
            } else {
                0f
            },
        )
    }

    /**
     * The rails this strip DRAWS inside `[stripTop, stripBottom]`, with the vertical split that
     * goes with them — the ONE answer the draw and every measurement read. The chain's fallback
     * values lift above the line, under the total rail, when the strip can afford the extra row
     * ([undercutRailsLift] on the lifted split's cylinder); otherwise they stay below the line,
     * the compressed layout.
     */
    fun resolveRails(
        stripTop: Float,
        stripBottom: Float,
        dualStacked: Boolean,
    ): Pair<UndercutStripRails, UndercutStripInnerLayout> {
        if (rails.canLiftFallbacks) {
            val liftedInner = innerLayout(stripTop, stripBottom, dualStacked, lifted = true)
            if (undercutRailsLift(liftedInner.cylBottom - liftedInner.cylTop)) return liftedRails to liftedInner
        }
        return rails to innerLayout(stripTop, stripBottom, dualStacked)
    }

    /**
     * Everything this strip stacks around its drawn cylinder — rails, their label rows, the Ø
     * callouts, the title — measured on a band too tall for any row to drop out and with no
     * cylinder cap, so the strip's height less this is exactly the cylinder it draws. [lifted]
     * measures the rails with the chain's fallback values above the line — the layout a strip
     * with room draws ([resolveRails]).
     */
    fun verticalOverheadPt(dualStacked: Boolean, lifted: Boolean = false): Float {
        val probe = innerLayout(0f, OVERHEAD_PROBE_PT, dualStacked, cylMaxFloorPt = Float.MAX_VALUE, lifted = lifted)
        return OVERHEAD_PROBE_PT - (probe.cylBottom - probe.cylTop)
    }

    private companion object {
        const val OVERHEAD_PROBE_PT = 10_000f
    }
}

/**
 * Resolves [strip]'s [UndercutStripPlan] across `[stripLeft, stripRight]`, or null when its draw
 * range is empty (nothing to draw).
 *
 * The draw range is widened at layout time so the undimensioned pad outside each chain datum
 * prints with real air whatever the strip's scale is — the mm pad alone reads as cramped in
 * a grid cell (see `computeUndercutStripDrawRange`). Datums and dimensions are untouched.
 * That widening is blind to the profile and can carry an end onto a short seg again (a stub
 * body between a taper and the liner), so the break-room rule runs once more on its result:
 * each window-end S-break sits in stock with room for its lobes before the next face.
 */
internal fun planUndercutDetailStrip(
    segs: List<SurfaceSeg>,
    strip: UndercutStrip,
    undercuts: List<Undercut>,
    clampedById: Map<String, ClampedUndercutSpanMm>,
    stripLeft: Float,
    stripRight: Float,
    unit: UnitSystem,
    text: Paint,
    diaText: Paint,
    shaftExtentMm: Float,
    startedStrip: Boolean,
    dual: Boolean,
    dualStacked: Boolean,
    railSplitAtMm: List<Float>,
    arrowSizePt: Float,
    titleAirPt: Float = 0f,
    /** The S.E.T. this strip measures to ([undercutStripSetSide]); null draws no lead. */
    leadSide: UndercutAnchorSide? = null,
    aftSetMm: Float = 0f,
    fwdSetMm: Float = 0f,
    /** The job's resolved "Total length of cuts" choice — [planUndercutStripRails]' `showTotal`. */
    showTotal: Boolean = true,
): UndercutStripPlan? {
    // The strip's own cuts only: another strip's cut in this window draws as plain stock here.
    val memberCutSpans = strip.undercutIds.mapNotNull { id ->
        clampedById[id]?.takeIf { !it.isEmpty }?.let { cs -> UndercutSpanMm(id, cs.startMm, cs.endMm) }
    }
    val dimText = Paint(text).apply { textSize = (text.textSize - 2f).coerceAtLeast(7f) }
    // The S.E.T. lead's room: on its side the strip draws enough stock past the chain datum for the
    // lead's value to seat in its own line with an open tail after it (`undercutSetLeadRoomPt`,
    // measured as the rail draws it). No lead — a started strip, or a chain already at its
    // S.E.T. — and the range is the pad rule's alone.
    val leadValueMm = if (startedStrip || memberCutSpans.isEmpty()) null
    else leadSide?.let { side ->
        undercutSetLeadValueMm(
            strip.chainStartMm, strip.chainEndMm, side,
            if (side == UndercutAnchorSide.AFT_SET) aftSetMm else fwdSetMm,
        )
    }
    val leadRoomPt = leadValueMm?.let { v ->
        undercutSetLeadRoomPt(
            dimText.measureDualLabel(formatLenDimDualLabel(v.toDouble(), unit, dual), dualStacked),
            arrowSizePt,
        )
    } ?: 0f
    val drawRange = computeUndercutStripDrawRange(
        strip.drawStartMm, strip.drawEndMm, strip.chainStartMm, strip.chainEndMm,
        stripLeft, stripRight, shaftExtentMm,
        leadSide = leadSide.takeIf { leadValueMm != null }, leadRoomPt = leadRoomPt,
    )
    val (drawStartMm, drawEndMm) =
        withUndercutBreakRoom(drawRange.startMm, drawRange.endMm, segs, shaftExtentMm, memberCutSpans)
    val drawLengthMm = drawEndMm - drawStartMm
    if (drawLengthMm <= 0f) return null

    val hLayout = computeWearStripHorizontalLayout(
        stripLeft, stripRight, drawLengthMm, stubWidthPt = UNDERCUT_STRIP_EDGE_INSET_PT,
    )
    val ptPerMmStrip = hLayout.ptPerMm
    fun xAtStrip(mm: Float): Float = hLayout.linerLeftPt + (mm - drawStartMm) * ptPerMmStrip

    val titleText = Paint(text).apply { textSize = (text.textSize - 1f).coerceAtLeast(7f) }

    // Spans on this strip, aft → fwd, render-clamped.
    val spans = undercuts.mapNotNull { u ->
        val s = clampedById[u.id] ?: return@mapNotNull null
        if (s.isEmpty) null else UndercutSpanMm(u.id, s.startMm, s.endMm)
    }.sortedBy { it.startMm }

    // Measured-Ø plan first, so the strip reserves exactly the label rows its cuts need.
    val diaStations = buildUndercutDiaStations(
        undercuts, clampedById, ::xAtStrip, unit,
        { s -> diaText.measureDualLabel(s, dualStacked) }, dual,
    )
    val diaPlan = if (diaStations.isEmpty()) null else
        planDiaCallouts(diaStations, stripLeft + 2f, stripRight - 2f, UC_DIA_MIN_GAP_PT)
    val diaBandPt = diaPlan?.let { it.labelsHeightPt(diaText.textSize, UC_DIA_ROW_GAP_PT) + 2f } ?: 0f

    // The rails — level-0 chain over the top-level cuts, one row per nesting level located
    // against the parent's edges, the total across the top-level cuts — from the ONE plan the
    // detail canvas runs too. Resolved before the vertical split (pure horizontal geometry), so
    // the split reserves only the fallback rows these labels actually use.
    // The S.E.T. lead runs out from the chain's outer witness to the chosen S.E.T., stopping at
    // this strip's DRAWN edge when the S.E.T. is off it.
    val rails = planUndercutStripRails(
        strip.chainStartMm, strip.chainEndMm, spans, unit, dual, railSplitAtMm, startedStrip,
        xAt = { mm -> xAtStrip(mm) },
        labelWidth = { s: DualLabel -> dimText.measureDualLabel(s, dualStacked) },
        arrowSizePt = arrowSizePt,
        setLead = leadSide?.let { side ->
            UndercutSetLead(
                side = side,
                setMm = if (side == UndercutAnchorSide.AFT_SET) aftSetMm else fwdSetMm,
                drawStartMm = drawStartMm,
                drawEndMm = drawEndMm,
            )
        },
        showTotal = showTotal,
    )
    return UndercutStripPlan(
        drawStartMm, drawEndMm, hLayout, titleText, dimText, spans, diaPlan, diaBandPt, rails,
        titleAirPt,
    )
}

/**
 * One broken-out detail strip: the resolved profile over the strip's draw range at
 * strip-local scale with a break edge at each cut end, the notches cut into it, the total
 * dimension on a rail line at the very top, the chained rail below that, the measured Ø
 * values below with leaders to their notch floors, and the title at the bottom.
 *
 * The draw range and the chain range come from the [strip] and differ on a
 * `LinerStrip`: the profile spans the whole liner (plus any cut overhang) padded each side
 * so the liner's edges and its neighbours' slivers are visible, while the chain — and with
 * it the rail's outer witness lines — anchors on the liner's true edges, leaving the pad
 * undimensioned. A `FreeStrip`'s two ranges coincide, so it draws exactly as before.
 *
 * Layout math is pure and lives in `UndercutStripLayout.kt`; this function only draws.
 *
 * [blankValues] carries the write-in posture (dimension LINES kept, VALUES dropped; the
 * anchor title becomes a writing rule with both directions printed for circling one) in
 * lockstep with the wear strips.
 *
 * [startedStrip] is the empty-record posture: the liner is drawn to scale exactly as above,
 * but the strip carries no cuts, so the chain and total rails collapse to the two chain-datum
 * witness bars and the bands above and below the cylinder are left as clear space for the
 * machinist's own sections and Ø values. It always arrives with [blankValues] set.
 *
 * [linerSpanBlank] narrows that further, for the blank write-in TEMPLATE only: the liner's own
 * span prints no fill and no top/bottom surface lines at all, leaving only the *starting*
 * geometry — the liner's two vertical end faces, the neighbour stock outboard of them, and the
 * break edges. The middle is clear paper the machinist draws the liner surface and its cuts
 * onto (on-device report: a fully outlined liner reads as a finished figure and invites
 * sketching on top of printed lines). A non-blank export of an empty record keeps the fully
 * drawn started strip.
 */
private fun drawUndercutDetailStrip(
    c: Canvas,
    docSpec: ShaftSpec,
    segs: List<SurfaceSeg>,
    strip: UndercutStrip,
    undercuts: List<Undercut>,
    clampedById: Map<String, ClampedUndercutSpanMm>,
    stripTop: Float,
    stripBottom: Float,
    stripLeft: Float,
    stripRight: Float,
    unit: UnitSystem,
    aftSetMm: Float,
    fwdSetMm: Float,
    text: Paint,
    outline: Paint,
    dim: Paint,
    diaText: Paint,
    bodyFill: Paint?,
    taperFill: Paint?,
    linerFill: Paint?,
    shaftExtentMm: Float,
    voidFill: Paint,
    linerTitle: String?,
    /**
     * The liner's own edge-to-SET anchor label ("<dist> FROM … S.E.T.") for a liner strip —
     * `buildLinerAnchorLabel`'s text, identical to the schematic's and wear sheet's figure
     * for this liner. Null on a bare-shaft strip (no liner to reference), which titles
     * itself from its cuts' near shoulder instead ([undercutAnchorFor]).
     */
    linerAnchorLabel: String?,
    /** Direction cue for [linerAnchorLabel]: right-align a FWD-SET-referenced title. */
    linerAnchorAlignRight: Boolean,
    blankValues: Boolean,
    startedStrip: Boolean,
    linerSpanBlank: Boolean,
    /**
     * This strip's notches, already resolved by the shared builder (`buildUndercutNotches`) and
     * ordered parents before children. The strip only maps them to points — it never re-derives
     * a floor, so its staircase and the sheet's Ø-leader anchors read the same numbers.
     */
    notches: List<UndercutNotch>,
    /** Whether the section cores take their light fill — [undercutPdfFillPlan]'s decision. */
    sectionCoreFill: Boolean,
    /** The liner outline's dotted paint; null when `PdfPrefs.undercutLinerOutline` is off. */
    linerOutlinePaint: Paint? = null,
    /**
     * The strip liner's OD as printed in its title (`Ø10.000"`, in the strip's own unit); null on a
     * bare-shaft strip. Ignored by the blank write-in title.
     */
    linerOdLabel: String? = null,
    /** [unit] is already this strip's resolved unit (liner override, else the document unit);
     *  this only carries the sheet-wide dual (inline "primary [secondary]") flag through. */
    dual: Boolean = false,
    /**
     * Set this strip's dual values as two-line stacks. Decided once for the whole SHEET by the
     * composer (the tightest strip must still afford the taller rail rows), never re-derived here.
     */
    dualStacked: Boolean = false,
    /**
     * Buried liner edges — extra chain nodes for the level-0 rail ([buildUndercutRailSpans]);
     * empty unless `PdfPrefs.undercutSplitAtLinerEdge` is on.
     */
    railSplitAtMm: List<Float> = emptyList(),
    /** `PdfPrefs.arrowSizePt`: the rail heads, and the seat-in-break / arrow-direction decisions. */
    arrowSizePt: Float = PDF_ARROW_SIZE_DEFAULT_PT,
    /** [UndercutStripPlan.titleAirPt] — nonzero on a portrait page only. */
    titleAirPt: Float = 0f,
    /**
     * The S.E.T. this strip measures to ([undercutStripSetSide]): the bare-shaft title's anchor and
     * the lead continuing the chain. Null keeps the bare-shaft proximity rule and draws no lead.
     */
    leadSide: UndercutAnchorSide? = null,
    /** The job's resolved "Total length of cuts" choice — [planUndercutStripRails]' `showTotal`. */
    showTotal: Boolean = true,
) {
    val plan = planUndercutDetailStrip(
        segs, strip, undercuts, clampedById, stripLeft, stripRight, unit, text, diaText,
        shaftExtentMm, startedStrip, dual, dualStacked, railSplitAtMm, arrowSizePt, titleAirPt,
        leadSide = leadSide, aftSetMm = aftSetMm, fwdSetMm = fwdSetMm, showTotal = showTotal,
    ) ?: return
    val drawStartMm = plan.drawStartMm
    val drawEndMm = plan.drawEndMm
    fun xAtStrip(mm: Float): Float = plan.xAt(mm)
    val titleText = plan.titleText
    val dimText = plan.dimText
    val spans = plan.spans
    val diaPlan = plan.diaPlan
    // The rails as drawn: the chain's fallback values lifted above the line when this strip has
    // the room for it, else below it — decided once, read by the whole draw.
    val (rails, inner) = plan.resolveRails(stripTop, stripBottom, dualStacked)
    val cy = (inner.cylTop + inner.cylBottom) / 2f
    val rCap = ((inner.cylBottom - inner.cylTop) / 2f).coerceAtLeast(0f)

    // Vertical scale: the largest surface Ø inside the draw range fills the strip's budget, so
    // a strip always draws as tall as the strip allows regardless of its length. On a liner
    // strip that maximum is the liner's own OD, so the liner reads full height and the
    // neighbouring stock slivers step down from it — the wear strip's look.
    val stripMaxDiaMm = maxOuterDiaOver(segs, drawStartMm, drawEndMm).takeIf { it > 0f }
        ?: docSpec.maxOuterDiaMm().takeIf { it > 0f } ?: 1f
    fun rStrip(diaMm: Float): Float = (rCap * (diaMm / stripMaxDiaMm)).coerceIn(0f, rCap)

    // Blank write-in template: only the STARTING geometry, so the liner's span stays clear
    // paper (see the KDoc). The two outboard calls draw the neighbour slivers with their normal
    // outline + fill; the liner edge sits at a window END in each, so the profile draws no cap
    // there and the full-height end faces below are the only vertical lines at those stations.
    // The window's two ends as the fills see them: each component fill runs past a cut end by the
    // glyph's reach and is clipped to the S that end strokes, so the shade ends ON the curve.
    val aftEdge = undercutWindowEndEdge(segs, drawStartMm, ::xAtStrip, ::rStrip, cy, shaftExtentMm)
    val fwdEdge = undercutWindowEndEdge(segs, drawEndMm, ::xAtStrip, ::rStrip, cy, shaftExtentMm)
    val fillClip = if (aftEdge != null && fwdEdge != null) {
        breakWindowClipPath(aftEdge, fwdEdge, cy - 2f * rCap - 1f, cy + 2f * rCap + 1f)
    } else null
    val ptPerMmStrip = abs(xAtStrip(drawEndMm) - xAtStrip(drawStartMm)) / (drawEndMm - drawStartMm).coerceAtLeast(1e-3f)
    fun fillReachMm(edge: WindowEndEdge?): Float =
        if (edge == null || edge.amplitude <= 0f || ptPerMmStrip <= 0f) 0f else edge.amplitude / ptPerMmStrip
    val fillStartMm = drawStartMm - fillReachMm(aftEdge)
    val fillEndMm = drawEndMm + fillReachMm(fwdEdge)

    val blankLiner = if (linerSpanBlank) strip as? UndercutStrip.LinerStrip else null
    if (blankLiner != null) {
        val faceAftMm = blankLiner.linerStartMm.coerceIn(drawStartMm, drawEndMm)
        val faceFwdMm = blankLiner.linerEndMm.coerceIn(drawStartMm, drawEndMm)
        drawUndercutWindowProfile(
            c, docSpec, drawStartMm, faceAftMm, cy, ::xAtStrip, ::rStrip,
            outline, bodyFill, taperFill, linerFill,
            fillW0Mm = fillStartMm, fillW1Mm = faceAftMm, fillClip = fillClip,
        )
        drawUndercutWindowProfile(
            c, docSpec, faceFwdMm, drawEndMm, cy, ::xAtStrip, ::rStrip,
            outline, bodyFill, taperFill, linerFill,
            fillW0Mm = faceFwdMm, fillW1Mm = fillEndMm, fillClip = fillClip,
        )
        // Full drawn height at the liner's own OD — the surface envelope over the liner's span
        // is the liner, so its maximum there IS that OD. Drawn at outline weight: with the
        // surface lines gone these faces carry the figure.
        val rFace = rStrip(maxOuterDiaOver(segs, faceAftMm, faceFwdMm))
        if (rFace > 0f) listOf(faceAftMm, faceFwdMm).forEach { mm ->
            c.drawLine(xAtStrip(mm), cy - rFace, xAtStrip(mm), cy + rFace, outline)
        }
    } else {
        drawUndercutWindowProfile(
            c, docSpec, drawStartMm, drawEndMm, cy, ::xAtStrip, ::rStrip,
            outline, bodyFill, taperFill, linerFill,
            fillW0Mm = fillStartMm, fillW1Mm = fillEndMm, fillClip = fillClip,
        )
    }
    // Cut ends: an S-break where the strip slices through material (void beyond it, so the
    // AFT stub's eye is at the top and the FWD stub's at the bottom — the stub convention,
    // the inverse of a centred compression break), a flat edge where the draw range's end IS
    // the physical shaft end.
    drawUndercutWindowEnd(c, segs, drawStartMm, cy, ::xAtStrip, ::rStrip, outline, shaftExtentMm, eyeAtTop = true)
    drawUndercutWindowEnd(c, segs, drawEndMm, cy, ::xAtStrip, ::rStrip, outline, shaftExtentMm, eyeAtTop = false)

    drawUndercutNotches(
        c, notches, cy, ::xAtStrip, ::rStrip, outline, voidFill,
        sectionCoreFill = sectionCoreFill,
    )
    drawLinerOutlinesPdf(c, docSpec, notches, cy, ::xAtStrip, ::rStrip, linerOutlinePaint)

    // ── Measured-Ø callouts: leader from each notch floor down to the printed value ──
    if (diaPlan != null) {
        // The leader must land on the DRAWN floor, so it reads the notch's own floor — the
        // builder's, nested cuts included — rather than re-deriving one.
        val floorBottomYByKey = notches.associate { n -> n.id to (cy + rStrip(n.floorDiaMm)) }
        val placed = diaPlan.finish(
            row0Top = inner.cylBottom + WEAR_STRIP_LABEL_HEADROOM_PT,
            labelTextHeight = diaText.textSize,
            rowGap = UC_DIA_ROW_GAP_PT,
            surfaceYAt = { i -> floorBottomYByKey[diaPlan.stations[i].key] ?: cy },
            leaderStartGap = 1f,
        )
        placed.forEach { p ->
            for (s in 0 until p.leader.size - 1) {
                c.drawLine(p.leader[s].x, p.leader[s].y, p.leader[s + 1].x, p.leader[s + 1].y, dim)
            }
            val fm = diaText.fontMetrics
            c.drawDualLabelCentered(p.label, p.labelCx, p.labelTopY - fm.ascent, diaText, dualStacked)
        }
    }

    // ── Rails: the total, the chain under it, then the nested levels under that ──
    if (startedStrip) {
        // Datum bars only — the liner's own edges extended up into the empty band, so a
        // hand-drawn chain has something real to measure from. No dimension line, no arrows:
        // the space above the cylinder belongs to the machinist.
        listOf(strip.chainStartMm, strip.chainEndMm).forEach { mm ->
            val x = xAtStrip(mm)
            c.drawLine(x, inner.cylTop - UNDERCUT_RAIL_WITNESS_GAP_PT, x, inner.chainRailY, dim)
        }
    }
    // The total, the chain, then the nested levels — the ONE rail sequence the
    // Undercut tab's detail canvas draws too (`drawUndercutStripRails`).
    drawUndercutStripRails(
        c, dim, dimText, rails,
        chainRailY = inner.chainRailY,
        totalRailY = inner.totalRailY,
        witnessBottomY = inner.cylTop - UNDERCUT_RAIL_WITNESS_GAP_PT,
        railLabelRows = inner.railLabelRows,
        drawLabels = !blankValues,
        dualStacked = dualStacked,
        arrowSizePt = arrowSizePt,
    )

    // ── Title at the bottom: "<liner name> — <dist> FROM … S.E.T." ──
    val titleBaselineY = (stripBottom - 2f).coerceAtLeast(inner.cylBottom + titleText.textSize)
    if (blankValues) {
        // Write-in title: the anchor value becomes a writing rule and BOTH directions print
        // for the machinist to circle one. Always left-aligned — the FWD right-align cue
        // mirrors a KNOWN measurement direction, which a write-in sheet doesn't have.
        titleText.textAlign = Paint.Align.LEFT
        val afterRule = drawLabelWithRule(
            c, linerTitle?.let { "$it —" } ?: "", stripLeft, titleBaselineY, titleText,
            ruleWidth = BLANK_DIM_GAP_PT, maxRight = stripRight,
        )
        c.drawText(WEAR_BLANK_ANCHOR_SUFFIX, afterRule - 8f, titleBaselineY, titleText)
    } else if (linerAnchorLabel != null) {
        // Liner strip: the anchor locates the LINER (its near edge from its SET), never a
        // cut — a cut's position is read off the chain rail, from the liner's edges.
        val label = ellipsizeToWidth(
            buildUndercutStripTitle(linerTitle, linerAnchorLabel, linerOdLabel),
            titleText,
            stripRight - stripLeft,
            rich = true,
        )
        if (linerAnchorAlignRight) {
            titleText.textAlign = Paint.Align.RIGHT
            c.drawRichText(label, stripRight, titleBaselineY, titleText)
        } else {
            titleText.textAlign = Paint.Align.LEFT
            c.drawRichText(label, stripLeft, titleBaselineY, titleText)
        }
    } else if (spans.isNotEmpty()) {
        // Bare-shaft strip: no liner to reference, so the cut run itself anchors to the
        // nearer SET, measured to its near shoulder.
        val anchor = undercutAnchorFor(
            firstShoulderMm = spans.first().startMm,
            lastShoulderMm = spans.last().endMm,
            aftSetXMm = aftSetMm,
            fwdSetXMm = fwdSetMm,
            forcedSide = leadSide,
        )
        val label = ellipsizeToWidth(
            buildUndercutStripTitle(null, buildUndercutAnchorLabel(anchor, unit, dual)),
            titleText,
            stripRight - stripLeft,
            rich = true,
        )
        if (anchor.alignRight) {
            titleText.textAlign = Paint.Align.RIGHT
            c.drawRichText(label, stripRight, titleBaselineY, titleText)
        } else {
            titleText.textAlign = Paint.Align.LEFT
            c.drawRichText(label, stripLeft, titleBaselineY, titleText)
        }
    } else if (linerTitle != null) {
        // A liner strip can exist with no drawable cut (every assigned span clamped away):
        // the liner is still worth naming, there is just no anchor dimension to state.
        titleText.textAlign = Paint.Align.LEFT
        c.drawText(
            ellipsizeToWidth(buildUndercutStripTitle(linerTitle, "", linerOdLabel), titleText, stripRight - stripLeft),
            stripLeft, titleBaselineY, titleText,
        )
    }
}

/**
 * The resolved profile clipped to `[w0Mm, w1Mm]` at strip-local scale: each component draws
 * its true local diameter (a taper's Ø interpolated at the clip points), and an end cap is
 * drawn only where the component's real edge falls inside the window — an edge coinciding
 * with a window end belongs to the break/flat edge instead. Component boundaries inside the
 * window (a liner edge, a body Ø step) therefore appear naturally, which is what lets a
 * notch crossing one show its taller shoulder on the liner side.
 */
private fun drawUndercutWindowProfile(
    c: Canvas,
    spec: ShaftSpec,
    w0Mm: Float,
    w1Mm: Float,
    cy: Float,
    xAt: (Float) -> Float,
    rAt: (Float) -> Float,
    outline: Paint,
    bodyFill: Paint?,
    taperFill: Paint?,
    linerFill: Paint?,
    /**
     * The range the FILLS cover — wider than [w0Mm]..[w1Mm] at a cut end by the break glyph's
     * reach — and the clip that cuts them back to the S each end strokes ([breakWindowClipPath]).
     * Outlines keep the window range. Defaults reproduce the plain window.
     */
    fillW0Mm: Float = w0Mm,
    fillW1Mm: Float = w1Mm,
    fillClip: Path? = null,
) {
    val dimPaint = Paint(outline).apply { strokeWidth = outline.strokeWidth * (UC_DIM_PT / UC_OUTLINE_PT) }
    fun inside(mm: Float) = mm > w0Mm + UC_EDGE_EPS_MM && mm < w1Mm - UC_EDGE_EPS_MM
    fun clippedFill(draw: () -> Unit) {
        if (fillClip == null) { draw(); return }
        c.save()
        c.clipPath(fillClip)
        draw()
        c.restore()
    }

    fun drawConstant(startMm: Float, endMm: Float, diaMm: Float, fill: Paint?, capPaint: Paint) {
        if (diaMm <= 0f) return
        val r = rAt(diaMm)
        if (fill != null) {
            val fa = max(startMm, fillW0Mm); val fb = min(endMm, fillW1Mm)
            if (fb - fa > UC_EDGE_EPS_MM) clippedFill { c.drawRect(xAt(fa), cy - r, xAt(fb), cy + r, fill) }
        }
        val a = max(startMm, w0Mm); val b = min(endMm, w1Mm)
        if (b - a <= UC_EDGE_EPS_MM) return
        val x0 = xAt(a); val x1 = xAt(b)
        c.drawLine(x0, cy - r, x1, cy - r, outline)
        c.drawLine(x0, cy + r, x1, cy + r, outline)
        if (inside(startMm)) c.drawLine(x0, cy - r, x0, cy + r, capPaint)
        if (inside(endMm)) c.drawLine(x1, cy - r, x1, cy + r, capPaint)
    }

    spec.bodies.forEach { b ->
        if (b.lengthMm > 0f) drawConstant(b.startFromAftMm, b.startFromAftMm + b.lengthMm, b.diaMm, bodyFill, outline)
    }
    spec.tapers.forEach { t ->
        if (t.lengthMm <= 0f || (t.startDiaMm <= 0f && t.endDiaMm <= 0f)) return@forEach
        val s = t.startFromAftMm; val e = s + t.lengthMm
        fun diaAt(mm: Float) = t.startDiaMm + (t.endDiaMm - t.startDiaMm) * ((mm - s) / t.lengthMm).coerceIn(0f, 1f)
        taperFill?.let { f ->
            val fa = max(s, fillW0Mm); val fb = min(e, fillW1Mm)
            if (fb - fa > UC_EDGE_EPS_MM) {
                val fra = rAt(diaAt(fa)); val frb = rAt(diaAt(fb))
                val fxa = xAt(fa); val fxb = xAt(fb)
                clippedFill {
                    c.drawPath(
                        Path().apply {
                            moveTo(fxa, cy - fra); lineTo(fxb, cy - frb); lineTo(fxb, cy + frb); lineTo(fxa, cy + fra); close()
                        },
                        f,
                    )
                }
            }
        }
        val a = max(s, w0Mm); val b = min(e, w1Mm)
        if (b - a <= UC_EDGE_EPS_MM) return@forEach
        val ra = rAt(diaAt(a)); val rb = rAt(diaAt(b))
        val xa = xAt(a); val xb = xAt(b)
        c.drawLine(xa, cy - ra, xb, cy - rb, outline)
        c.drawLine(xa, cy + ra, xb, cy + rb, outline)
        if (inside(s)) c.drawLine(xa, cy - ra, xa, cy + ra, outline)
        if (inside(e)) c.drawLine(xb, cy - rb, xb, cy + rb, outline)
    }
    spec.liners.forEach { ln ->
        if (ln.lengthMm > 0f) drawConstant(ln.startFromAftMm, ln.startFromAftMm + ln.lengthMm, ln.odMm, linerFill, dimPaint)
    }
    val hatchPaint = Paint(outline).apply { strokeWidth = outline.strokeWidth * (UC_DIM_PT * 0.6f / UC_OUTLINE_PT); alpha = 160 }
    // Threads — a window-clipped slice of the same split every sheet draws
    // (`geom/ThreadReliefMath.kt`): the hatch and the major-Ø envelope over the clipped HATCH span
    // only, the relief groove as its reduced rectangle where the window reaches it. A face is
    // drawn only at an edge that is the thread's own, never at the window's cut (same rule as
    // every other component here); the face where the relief meets its shoulder spans the relief
    // Ø only, since the shoulder draws its own larger face there.
    spec.threads.forEach { th ->
        if (th.lengthMm <= 0f || th.majorDiaMm <= 0f) return@forEach
        val r = rAt(th.majorDiaMm)
        th.hatchSpan()?.let { hs ->
            val a = max(hs.startMm, w0Mm); val b = min(hs.endMm, w1Mm)
            if (b - a > UC_EDGE_EPS_MM) {
                drawThreadHatch(c, xAt(a), xAt(b), cy - r, cy + r, hatchPaint, th)
                c.drawLine(xAt(a), cy - r, xAt(b), cy - r, outline)
                c.drawLine(xAt(a), cy + r, xAt(b), cy + r, outline)
                if (inside(hs.startMm)) c.drawLine(xAt(a), cy - r, xAt(a), cy + r, outline)
                if (inside(hs.endMm)) c.drawLine(xAt(b), cy - r, xAt(b), cy + r, outline)
            }
        }
        th.reliefSpan()?.let { rs ->
            val a = max(rs.startMm, w0Mm); val b = min(rs.endMm, w1Mm)
            if (b - a <= UC_EDGE_EPS_MM) return@let
            val rr = rAt(rs.diaMm)
            c.drawLine(xAt(a), cy - rr, xAt(b), cy - rr, outline)
            c.drawLine(xAt(a), cy + rr, xAt(b), cy + rr, outline)
            val shoulderMm = rs.shoulderEndMm(th.reliefAtAft)
            val xs = if (th.reliefAtAft) xAt(a) else xAt(b)
            if (inside(shoulderMm)) c.drawLine(xs, cy - rr, xs, cy + rr, outline)
            // A relief that takes the whole thread has no hatched span to close its other end.
            if (th.hatchSpan() == null) {
                val otherMm = if (th.reliefAtAft) rs.endMm else rs.startMm
                val xo = if (th.reliefAtAft) xAt(b) else xAt(a)
                if (inside(otherMm)) c.drawLine(xo, cy - rr, xo, cy + rr, outline)
            }
        }
    }
}

/**
 * One end of a zoomed window: the round-stock S-break where the window cuts through
 * material, or a plain flat edge where the window end is the shaft's own physical end
 * (nothing was cut away there, so a break symbol would lie).
 */
private fun drawUndercutWindowEnd(
    c: Canvas,
    segs: List<SurfaceSeg>,
    atMm: Float,
    cy: Float,
    xAt: (Float) -> Float,
    rAt: (Float) -> Float,
    outline: Paint,
    shaftExtentMm: Float,
    eyeAtTop: Boolean,
) {
    val edge = undercutWindowEndEdge(segs, atMm, xAt, rAt, cy, shaftExtentMm) ?: return
    if (edge.amplitude <= 0f) {
        c.drawLine(edge.x, edge.yTop, edge.x, edge.yBot, outline)
    } else {
        drawBreakEdge(c, edge.x, edge.yTop, edge.yBot, edge.amplitude, outline, eyeAtTop = eyeAtTop)
    }
}

/**
 * The window end at [atMm] as drawn: the S-break where the window slices through material, flat
 * (amplitude 0) where it is the shaft's physical end; null where there is no surface to cut. ONE
 * answer for the stroked end and the fill clip ([breakWindowClipPath]).
 */
private fun undercutWindowEndEdge(
    segs: List<SurfaceSeg>,
    atMm: Float,
    xAt: (Float) -> Float,
    rAt: (Float) -> Float,
    cy: Float,
    shaftExtentMm: Float,
): WindowEndEdge? {
    val dia = outerDiaAt(segs, atMm)
    if (dia <= 0f) return null
    val r = rAt(dia)
    if (r <= 0f) return null
    val x = xAt(atMm)
    val isPhysicalEnd = atMm <= UC_EDGE_EPS_MM || atMm >= shaftExtentMm - UC_EDGE_EPS_MM
    // Amplitude tracks the drawn radius so a small section still gets a legible symbol, but is
    // capped absolutely: uncapped, a tall strip's lobes sprawl past the cylinder corners and read
    // as ears hung off the end (on-device report). See UNDERCUT_BREAK_AMP_MAX_PT for the
    // deviation-vs-inset check.
    val amp = if (isPhysicalEnd) 0f else min(r * UNDERCUT_BREAK_AMP_FRAC_OF_R, UNDERCUT_BREAK_AMP_MAX_PT)
    return WindowEndEdge(x, cy - r, cy + r, amp)
}

/** Text note for detail strips that didn't fit on this page. */
private fun drawUndercutOverflowNote(
    c: Canvas, text: Paint, left: Float, right: Float, bandTop: Float, bandBottom: Float, count: Int,
) {
    val msg = "+$count more undercut detail strip(s) not shown"
    val y = (bandTop + bandBottom) * 0.5f + text.textSize * 0.35f
    c.drawText(ellipsizeToWidth(msg, text, right - left), left, y, text)
}

/** Free-text notes rule at the page bottom. */
private fun drawUndercutNotesRow(c: Canvas, text: Paint, left: Float, right: Float, y: Float) {
    c.drawText("Notes:", left, y, text)
    val x = left + text.measureText("Notes:") + 6f
    c.drawLine(x, y + 2f, right, y + 2f, Paint(text).apply { style = Paint.Style.STROKE; strokeWidth = 0.7f })
}

// ──────────────────────────────────────────────────────────────────────────────
// Constants
// ──────────────────────────────────────────────────────────────────────────────

private const val UC_OUTLINE_PT = 2.0f
private const val UC_DIM_PT = 1.2f
private const val UC_TEXT_PT = 10f
private const val UC_HEADER_MIN_FIT_TEXT_PT = 8f        // portrait job line: smallest size before ellipsizing
private const val UC_MARGIN_PT = 36f

private const val UC_HEADER_HEIGHT_PT = 36f          // 2-line header block
private const val UC_HEADER_HEIGHT_BLANK_PT = 56f    // blank write-in header: handwriting room
private const val UC_HEADER_BLANK_LINE_GAP_PT = 24f  // baseline gap between the blank header's two lines
private const val UC_HEADER_GAP_PT = 16f             // header rule → drawing area
private const val UC_OAL_ABOVE_SHAFT_PT = 44f        // shaft top edge → OAL line

private const val UC_PROFILE_NAMES_ROW_PT = 26f      // direction row under the shaft bottom
private const val UC_PROFILE_BOTTOM_PAD_PT = 6f      // air under the direction row before the strips gap
private const val UC_OAL_TOP_REGION_PT = 18f         // OAL label + air above the dimension line
private const val UC_DIRECTION_ROW_PT = 22f          // orientation row band above the strips (strips-own-page)
private const val UC_NOTES_BOTTOM_OFFSET_PT = 24f    // notes baseline above contentBot
private const val UC_NOTES_GAP_PT = 28f              // drawing area bottom → notes
private const val UC_PORTRAIT_TITLE_AIR_PT = 10f      // Ø callouts → strip title, portrait pages only
private const val UC_OVERFLOW_NOTE_HEIGHT_PT = 16f   // reserved band for the "+N more" note
private const val UC_PROFILE_RADIUS_MARGIN_PT = 8f   // headroom above/below the shaft's drawn radius



private const val UC_DIA_TEXT_PT = 8f                // measured-Ø value text size
private const val UC_DIA_MIN_GAP_PT = 5f             // min clear gap between label edges / leader drops
private const val UC_DIA_ROW_GAP_PT = 3f             // vertical gap between the two label rows

/** Tolerance for "this edge coincides with a window end" tests, mm. */
private const val UC_EDGE_EPS_MM = 1e-3f
