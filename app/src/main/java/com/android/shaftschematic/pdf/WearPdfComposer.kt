package com.android.shaftschematic.pdf

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.android.shaftschematic.model.*
import com.android.shaftschematic.geom.SetPositions
import com.android.shaftschematic.geom.computeOalWindow
import com.android.shaftschematic.geom.computeSetPositionsInMeasureSpace
import com.android.shaftschematic.settings.PdfPrefs
import com.android.shaftschematic.ui.resolved.ResolvedComponent
import com.android.shaftschematic.util.UnitSystem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

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
 * dye-penetrant inspection results directly on a simplified shaft outline.
 * The drawing is intentionally blank inside the shaft profile — the machinist
 * draws X's, circles, and notes by hand on the printed page.
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
 * ## Future enhancements (Phase 2)
 * - Digital damage annotation: tap to mark damage zones, severity selector, dye-pen pass/fail toggle.
 * - Damage zones render as coloured overlays on the shaft profile, printed with a legend.
 *
 * @param page    Target PDF page (US Letter landscape, already started).
 * @param spec    Shaft specification in millimeters.
 * @param project Job information (customer, vessel, job#, side).
 * @param unit    Display unit for the OAL dimension label.
 * @param resolvedComponents Resolved component list from the ViewModel. When provided,
 *                resolved bodies replace `spec.bodies` for the drawn profile — same
 *                contract as `composeShaftPdf`/`composeRunoutPdf`.
 * @param wearRecord Recorded liner wear spots (Phase 4, `docs/LinerWearAreas_Proposal.md`
 *                §6.2). The page picks one of three [WearPdfMode]s from
 *                `determineWearPdfMode(collectWearLinerGroups(...).size)` (2026-07-18
 *                either/or spec):
 *                - **0 wear liners** ([WearPdfMode.PROFILE_FORM]) — the original blank
 *                  hand-marking shaft profile, unchanged.
 *                - **1-2 wear liners** ([WearPdfMode.COMBINED]) — today's page: thin
 *                  hatched wear bands on the main profile (true position, clamped to the
 *                  liner span) plus a broken-out detail strip below the (shrunk) profile.
 *                - **3+ wear liners** ([WearPdfMode.STRIPS_ONLY]) — no main shaft profile,
 *                  no OAL dimension line; the detail strips alone fill the freed page,
 *                  each grown up to [WEAR_STRIP_MAX_HEIGHT_STRIPS_ONLY_PT] tall.
 *                In every mode with strips, they're aft→fwd order, max
 *                [WEAR_STRIP_MAX_PER_PAGE] per page; any remainder is listed as a text
 *                note (see `selectWearStripsForPage`'s KDoc for why this document draws
 *                into a single caller-supplied page rather than growing to a second
 *                page). Defaults to empty so every existing call site is unaffected.
 */
fun composeWearPdf(
    page: PdfDocument.Page,
    spec: ShaftSpec,
    project: ProjectInfo,
    unit: UnitSystem,
    pdfPrefs: PdfPrefs = PdfPrefs(),
    resolvedComponents: List<ResolvedComponent>? = null,
    wearRecord: WearRecord = WearRecord(),
    lineThicknessScale: Float = 1.0f,
) {
    val c = page.canvas
    c.drawColor(Color.WHITE)

    val docSpec = spec.withResolvedBodies(resolvedComponents)

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
    val bodyFill : Paint? = if (pdfPrefs.shadedBodies) shadeFill() else null
    val taperFill: Paint? = if (pdfPrefs.shadedTapers) shadeFill() else null
    val linerFill: Paint? = if (pdfPrefs.shadedLiners) shadeFill() else null

    // ── Page geometry ─────────────────────────────────────────────────────
    val margin       = WEAR_MARGIN_PT
    val contentLeft  = margin
    val contentRight = pageW - margin
    val contentW     = contentRight - contentLeft
    val contentTop   = margin
    val contentBot   = pageH - margin

    val headerBottom = contentTop + WEAR_HEADER_HEIGHT_PT

    // Notes anchored near the page bottom
    val notesY = contentBot - WEAR_NOTES_BOTTOM_OFFSET_PT

    // Full vertical band available to the shaft profile before any wear strips
    // are carved out of it (identical to the pre-Phase-4 layout when there are none).
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
    // Liners with ≥1 wear spot, aft→fwd, max WEAR_STRIP_MAX_PER_PAGE on this page.
    val wearGroups     = collectWearLinerGroups(docSpec.liners, wearRecord)
    val stripSelection = selectWearStripsForPage(wearGroups)
    val stripCount     = stripSelection.onPage.size
    val overflowNoteH  = if (stripSelection.overflow.isNotEmpty()) WEAR_OVERFLOW_NOTE_HEIGHT_PT else 0f

    // Which of the three modes this page renders (2026-07-18 either/or spec) — see
    // determineWearPdfMode's KDoc and the composeWearPdf-level KDoc above for the full rule.
    val wearMode = determineWearPdfMode(wearGroups.size)

    // ── Header (always drawn, in every mode) ───────────────────────────────
    drawWearHeader(c, text, contentLeft, contentRight, contentTop, project, unit, spec.overallLengthMm)

    if (wearMode == WearPdfMode.STRIPS_ONLY) {
        // ── Strips-only page (3+ wear liners): no shaft profile, no OAL line ───
        // Strips get the ENTIRE freed vertical band below the header, each grown up to
        // WEAR_STRIP_MAX_HEIGHT_STRIPS_ONLY_PT tall instead of the combined page's 108pt.
        val vLayout = computeWearStripsOnlyVerticalLayout(
            midTopFull, midBotFull, stripCount, reservedBottomPt = overflowNoteH,
        )
        stripSelection.onPage.forEachIndexed { i, group ->
            drawWearDetailStrip(
                c, docSpec, group, vLayout.stripTops[i], vLayout.stripBottoms[i],
                contentLeft, contentRight, unit, setPositions, text, outline, dim,
            )
        }
        if (stripSelection.overflow.isNotEmpty()) {
            drawWearOverflowNote(
                c, text, contentLeft, contentRight,
                vLayout.stripBottoms.last(), midBotFull, stripSelection.overflow,
            )
        }
    } else {
        // ── Combined page (0-2 wear liners): shaft profile + bands (+ strips below when
        // there are 1-2 wear liners) — unchanged from the pre-2026-07-18 layout.
        // Shaft profile shrinks/shifts to make room for the strips (zero-op when stripCount == 0).
        // The profile's minimum height also protects the actual drawn shaft radius — ptPerMm
        // is a purely horizontal (SET-to-SET) scale, so a wide/short shaft's true diameter
        // could otherwise exceed a shrunk profile band.
        val minProfileHeightPt = maxOf(WEAR_MIN_PROFILE_HEIGHT_PT, 2f * rPx(maxDiaMm) + WEAR_PROFILE_RADIUS_MARGIN_PT)
        val vLayout = computeWearVerticalLayout(
            midTopFull, midBotFull, stripCount,
            reservedBottomPt = overflowNoteH,
            minProfileHeightPt = minProfileHeightPt,
        )
        val midTop   = vLayout.profileTop
        val midBot   = vLayout.profileBottom
        val shaftCy  = (midTop + midBot) * 0.5f
        val geomRect = RectF(contentLeft, midTop, contentRight, midBot)

        // OAL dimension line sits well above the shaft's top outline (same 90 pt convention
        // as the runout sheet), not crowding the profile.
        val shaftTopApprox = shaftCy - rPx(maxDiaMm)
        val oalLineY       = (shaftTopApprox - WEAR_OAL_ABOVE_SHAFT_PT)
            .coerceAtLeast(midTop + WEAR_TEXT_PT + 6f)

        // ── OAL line (with witness lines, anchored near shaft) ────────────────
        // Label rule: the printed OAL is always the user's typed OAL (same as the main
        // schematic); the arrows below bracket the drawn SET-to-SET span.
        drawWearOalLine(c, dim, text, contentLeft, contentRight, oalLineY, shaftTopApprox, unit, spec.overallLengthMm)

        // ── Shaft profile ─────────────────────────────────────────────────────
        drawWearShaftProfile(c, docSpec, shaftCy, outline, geomRect, ::xAt, ::rPx,
            bodyFill = bodyFill, taperFill = taperFill, linerFill = linerFill, ptPerMm = ptPerMm)

        // ── Wear bands on the main profile ─────────────────────────────────────
        // Thin hatched bands at true position for every liner with recorded wear spots —
        // visible but not dominant (proposal §6.2 / build-log §10.3). Bands are clamped to
        // the liner span for rendering only; the stored spot data is never touched.
        drawWearBandsOnProfile(c, wearGroups, shaftCy, ::xAt, ::rPx, outline)

        // ── Per-liner detail strips ─────────────────────────────────────────────
        if (stripCount > 0) {
            stripSelection.onPage.forEachIndexed { i, group ->
                drawWearDetailStrip(
                    c, docSpec, group, vLayout.stripTops[i], vLayout.stripBottoms[i],
                    contentLeft, contentRight, unit, setPositions, text, outline, dim,
                )
            }
            if (stripSelection.overflow.isNotEmpty()) {
                drawWearOverflowNote(
                    c, text, contentLeft, contentRight,
                    vLayout.stripBottoms.last(), midBotFull, stripSelection.overflow,
                )
            }
        }
    }

    // ── Notes / dye-pen area ──────────────────────────────────────────────
    drawWearNotesArea(c, text, contentLeft, contentRight, notesY)
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
    unit: UnitSystem,
    oalMm: Float,
) {
    val ts = text.textSize
    val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    val oalDisplay = if (unit == UnitSystem.INCHES) {
        "${"%.4f".format(oalMm / 25.4f)}\""
    } else {
        "${"%.2f".format(oalMm)} mm"
    }
    val side = project.side.printableLabelOrNull()?.let { "  $it" } ?: ""

    val line1 = buildString {
        if (project.customer.isNotBlank()) append("Customer: ${project.customer}   ")
        if (project.vessel.isNotBlank())   append("Vessel: ${project.vessel}   ")
        if (project.jobNumber.isNotBlank()) append("Job #: ${project.jobNumber}   ")
        append("Date: $date$side")
    }
    val line2 = "OAL: $oalDisplay  —  WEAR / INSPECTION RECORD"

    fun centeredX(str: String): Float =
        ((left + right - text.measureText(str)) * 0.5f).coerceAtLeast(left)

    val line1Fit = ellipsizeToWidth(line1, text, right - left)
    val line2Fit = ellipsizeToWidth(line2, text, right - left)
    c.drawText(line1Fit, centeredX(line1Fit), top + ts, text)
    c.drawText(line2Fit, centeredX(line2Fit), top + ts + ts * 1.4f, text)

    val ruleY = top + WEAR_HEADER_HEIGHT_PT
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
) {
    val arrowLen    = 8f
    val witnessGap  = 3f   // gap between shaft edge and witness line start
    val witnessExt  = 5f   // how far the witness line extends past the dimension line

    // Witness (extension) lines from shaft top up to past the dimension line
    c.drawLine(x0, shaftTopY - witnessGap, x0, oalLineY - witnessExt, dim)
    c.drawLine(x1, shaftTopY - witnessGap, x1, oalLineY - witnessExt, dim)

    // Dimension line with arrowheads
    c.drawLine(x0, oalLineY, x1, oalLineY, dim)
    c.drawLine(x0, oalLineY, x0 + arrowLen, oalLineY - arrowLen * 0.4f, dim)
    c.drawLine(x0, oalLineY, x0 + arrowLen, oalLineY + arrowLen * 0.4f, dim)
    c.drawLine(x1, oalLineY, x1 - arrowLen, oalLineY - arrowLen * 0.4f, dim)
    c.drawLine(x1, oalLineY, x1 - arrowLen, oalLineY + arrowLen * 0.4f, dim)

    // Label centred above the dimension line
    val label = if (unit == UnitSystem.INCHES) "OAL: ${"%.4f".format(oalMm / 25.4f)}\""
    else "OAL: ${"%.2f".format(oalMm)} mm"
    val lw = text.measureText(label)
    c.drawText(label, (x0 + x1) * 0.5f - lw * 0.5f, oalLineY - 4f, text)
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
            val mid = (x0 + x1) * 0.5f; val gap = min(ZIGZAG_GAP_MAX_PT, 0.25f * lenPt)
            val half = gap * 0.5f; val amp = r * 0.6f
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
 * Thin hatched bands on the MAIN shaft profile for every liner with recorded wear
 * spots, at their true axial position — "visible but not dominant" (proposal
 * §6.2). Bands are clamped to the liner's own span for rendering; the underlying
 * [WearSpot] data is never mutated.
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
    val bandHatch = Paint(outline).apply { strokeWidth = WEAR_DIM_PT * 0.5f; alpha = 120 }
    groups.forEach { g ->
        val ln = g.liner
        if (ln.lengthMm <= 0f || ln.odMm <= 0f) return@forEach
        val r = rPx(ln.odMm); val top = cy - r; val bot = cy + r
        g.spots.forEach { spot ->
            val clamp = clampWearBandToLiner(spot.startMm, spot.lengthMm, ln.lengthMm)
            if (clamp.lengthMm <= 0f) return@forEach
            val x0 = xAt(ln.startFromAftMm + clamp.startMm)
            val x1 = xAt(ln.startFromAftMm + clamp.startMm + clamp.lengthMm)
            drawHatchBand(c, x0, x1, top, bot, bandHatch, pitchPt = 6f)
        }
    }
}

/**
 * One broken-out liner detail strip: neighbor stubs with S-curve break edges, the liner at
 * strip-local large scale, hatched wear bands with a chained dimension rail below the
 * cylinder (liner AFT edge → first band start → each band's length → inter-band gaps →
 * trailing remainder to the liner FWD edge, standard witness-line/arrowed-span convention —
 * see `buildWearStripRailSpans`/`layoutWearStripRail`/`drawWearStripRail`), a min-Ø reading per
 * band when recorded, and the liner's anchor dimension from its nearer SET (the "110 FROM
 * CPLG S.E.T." line in the shop sketch this feature digitizes).
 */
private fun drawWearDetailStrip(
    c: Canvas,
    docSpec: ShaftSpec,
    group: WearLinerGroup,
    stripTop: Float,
    stripBottom: Float,
    contentLeft: Float,
    contentRight: Float,
    unit: UnitSystem,
    setPositions: SetPositions,
    text: Paint,
    outline: Paint,
    dim: Paint,
) {
    val ln = group.liner
    if (ln.lengthMm <= 0f || ln.odMm <= 0f) return
    val aftMm = ln.startFromAftMm
    val fwdMm = aftMm + ln.lengthMm

    val hLayout = computeWearStripHorizontalLayout(contentLeft, contentRight, ln.lengthMm)
    val ptPerMmStrip = hLayout.ptPerMm
    fun xAtStrip(mm: Float): Float = hLayout.linerLeftPt + (mm - aftMm) * ptPerMmStrip

    val titleText = Paint(text).apply { textSize = (text.textSize - 1f).coerceAtLeast(7f) }
    val title = (ln.label?.takeIf { it.isNotBlank() } ?: "Liner") + " — " +
        buildLinerAnchorLabel(docSpec, ln, setPositions, unit)
    val titleBaselineY = (stripTop + titleText.textSize).coerceAtMost(stripBottom)
    c.drawText(ellipsizeToWidth(title, titleText, contentRight - contentLeft), contentLeft, titleBaselineY, titleText)

    val sortedSpots = group.spots.sortedBy { it.startMm }
    val inner = computeWearStripInnerLayout(
        stripTop, stripBottom,
        titleHeightPt = titleText.textSize,
    )
    val cy = (inner.cylTop + inner.cylBottom) / 2f
    val rCap = ((inner.cylBottom - inner.cylTop) / 2f).coerceAtLeast(0f)

    // Neighbor diameters resolved up front so the liner + both stubs can be scaled
    // by ONE common factor (computeWearStripRadii) instead of each being capped to
    // rCap independently — independent capping erases the liner-vs-neighbor
    // diameter step whenever both exceed the budget (2026-07-18 SVG review).
    val aftDia = neighborDiaMmAtAft(docSpec, aftMm) ?: ln.odMm
    val fwdDia = neighborDiaMmAtFwd(docSpec, fwdMm) ?: ln.odMm
    val radii = computeWearStripRadii(ln.odMm, aftDia, fwdDia, ptPerMmStrip, rCap)

    val linerR = radii.linerRPt
    val top = cy - linerR; val bot = cy + linerR

    // Liner cylinder outline
    val dimPaint = Paint(outline).apply { strokeWidth = WEAR_DIM_PT }
    c.drawLine(hLayout.linerLeftPt, top, hLayout.linerRightPt, top, outline)
    c.drawLine(hLayout.linerLeftPt, bot, hLayout.linerRightPt, bot, outline)
    c.drawLine(hLayout.linerLeftPt, top, hLayout.linerLeftPt, bot, dimPaint)
    c.drawLine(hLayout.linerRightPt, top, hLayout.linerRightPt, bot, dimPaint)

    // Neighbor stubs, broken out with the S-curve edge convention (BreakSymbol.kt). Each stub's
    // break sits at its own far/outer end (void beyond it, material toward the liner) — the
    // inverse of a centered compression break's shared-gap geometry — so eyeAtTop is the
    // opposite of the compression-break convention above: left/AFT stub void is to its left
    // (eyeAtTop = true), right/FWD stub void is to its right (eyeAtTop = false). See
    // `LinerWearDetail.kt`'s `drawBreakEdgeCompose` KDoc (2026-07-18 device review fix).
    val aftR = radii.aftRPt; val fwdR = radii.fwdRPt
    val stubLeftX = hLayout.linerLeftPt - hLayout.stubWidthPt
    val stubRightX = hLayout.linerRightPt + hLayout.stubWidthPt
    c.drawLine(stubLeftX, cy - aftR, hLayout.linerLeftPt, cy - aftR, outline)
    c.drawLine(stubLeftX, cy + aftR, hLayout.linerLeftPt, cy + aftR, outline)
    drawBreakEdge(c, stubLeftX, cy - aftR, cy + aftR, aftR * 0.6f, outline, eyeAtTop = true)
    c.drawLine(hLayout.linerRightPt, cy - fwdR, stubRightX, cy - fwdR, outline)
    c.drawLine(hLayout.linerRightPt, cy + fwdR, stubRightX, cy + fwdR, outline)
    drawBreakEdge(c, stubRightX, cy - fwdR, cy + fwdR, fwdR * 0.6f, outline, eyeAtTop = false)

    // Wear bands (hatch fill + edge ticks on the cylinder itself) and the min-Ø reading —
    // per spot, as before. The dimension story (offsets/lengths) is now the chained rail
    // drawn below, not a per-spot row here.
    val bandHatch = Paint(outline).apply { strokeWidth = WEAR_DIM_PT * 0.6f; alpha = 160 }
    val dimText = Paint(text).apply { textSize = (text.textSize - 2f).coerceAtLeast(7f) }
    val clampedBands = sortedSpots.map { spot -> clampWearBandToLiner(spot.startMm, spot.lengthMm, ln.lengthMm) }
    sortedSpots.forEachIndexed { idx, spot ->
        val clamp = clampedBands[idx]
        val x0 = xAtStrip(aftMm + clamp.startMm)
        val x1 = xAtStrip(aftMm + clamp.startMm + clamp.lengthMm)
        if (clamp.lengthMm > 0f) {
            drawHatchBand(c, x0, x1, top, bot, bandHatch, pitchPt = 5f)
            c.drawLine(x0, top, x0, bot, dimPaint); c.drawLine(x1, top, x1, bot, dimPaint)
        }

        // Min-Ø reading, printed just above the band (skipped when unrecorded).
        formatMinDiaLabelOrNull(spot.minDiaMm, unit)?.let { label ->
            val lw = dimText.measureText(label)
            val lx = (((x0 + x1) * 0.5f) - lw * 0.5f).coerceIn(contentLeft, contentRight - lw)
            val ly = (top - 3f).coerceIn(inner.cylTop, inner.cylBottom)
            c.drawText(label, lx, ly, dimText)
        }
    }

    // Chained dimension rail below the cylinder (2026-07-18 dimension-rail rework, see
    // "Wear Detail Strips" in docs/RunoutSheet.md): liner AFT edge → first band start, each
    // band's own length, inter-band gaps, and the trailing remainder to the liner FWD edge —
    // standard witness-line/arrowed-span/centered-label rail convention, replacing the old
    // per-spot offset/length text rows.
    val railSpans = buildWearStripRailSpans(ln.lengthMm, clampedBands, unit)
    val railLayout = layoutWearStripRail(
        railSpans,
        xAtStripMm = { mm -> xAtStrip(aftMm + mm) },
        labelWidthPt = { s -> dimText.measureText(s) },
    )
    drawWearStripRail(c, dim, dimText, railLayout, inner.cylBottom, inner.railY, inner.railLabelRows)
}

/**
 * Draws one strip's chained dimension rail (`buildWearStripRailSpans`/`layoutWearStripRail` in
 * `WearStripLayout.kt`): witness lines from the cylinder edge down to the rail, an arrowed
 * dimension line per chained span, and each span's label — stacked onto whichever row
 * `layoutWearStripRail` assigned it, clamped to [maxLabelRows] (rows beyond the budget
 * `computeWearStripInnerLayout` actually fit for this strip are never drawn).
 */
private fun drawWearStripRail(
    c: Canvas,
    dim: Paint,
    dimText: Paint,
    layout: List<WearRailSpanLayout>,
    cylBottom: Float,
    railY: Float,
    maxLabelRows: Int,
) {
    if (layout.isEmpty()) return
    val arrow = 4f
    val labelGapPt = 2f
    val rowStepPt = dimText.textSize + 3f
    val witnessExt = 3f

    layout.forEach { s ->
        c.drawLine(s.x0Pt, cylBottom, s.x0Pt, railY + witnessExt, dim)
        c.drawLine(s.x1Pt, cylBottom, s.x1Pt, railY + witnessExt, dim)
        c.drawLine(s.x0Pt, railY, s.x1Pt, railY, dim)

        val dirLeft = if (s.arrowInward) 1f else -1f
        val dirRight = if (s.arrowInward) -1f else 1f
        c.drawLine(s.x0Pt, railY, s.x0Pt + dirLeft * arrow, railY - arrow * 0.5f, dim)
        c.drawLine(s.x0Pt, railY, s.x0Pt + dirLeft * arrow, railY + arrow * 0.5f, dim)
        c.drawLine(s.x1Pt, railY, s.x1Pt + dirRight * arrow, railY - arrow * 0.5f, dim)
        c.drawLine(s.x1Pt, railY, s.x1Pt + dirRight * arrow, railY + arrow * 0.5f, dim)

        val row = s.labelRow.coerceAtMost(maxLabelRows - 1)
        if (row >= 0) {
            val ly = railY - labelGapPt - row * rowStepPt
            val lw = dimText.measureText(s.label)
            c.drawText(s.label, s.labelCxPt - lw * 0.5f, ly, dimText)
        }
    }
}

/** Diagonal hatch fill clipped to `[x0,x1] × [top,bot]` — shared by the profile bands and strip bands. */
private fun drawHatchBand(c: Canvas, x0: Float, x1: Float, top: Float, bot: Float, paint: Paint, pitchPt: Float) {
    if (x1 <= x0) return
    val saved = c.save()
    c.clipRect(x0, top, x1, bot)
    var hx = x0 - (bot - top)
    while (hx <= x1) {
        c.drawLine(hx, bot, hx + (bot - top), top, paint)
        hx += pitchPt
    }
    c.restoreToCount(saved)
}

/** Text note for liners that didn't fit within [WEAR_STRIP_MAX_PER_PAGE] strips on this page. */
private fun drawWearOverflowNote(
    c: Canvas,
    text: Paint,
    left: Float,
    right: Float,
    bandTop: Float,
    bandBottom: Float,
    overflow: List<WearLinerGroup>,
) {
    val names = overflow.joinToString(", ") { g ->
        g.liner.label?.takeIf { it.isNotBlank() } ?: "liner @ ${g.liner.startFromAftMm.toInt()}mm"
    }
    val msg = "+${overflow.size} more liner(s) with wear spots (page limit ${WEAR_STRIP_MAX_PER_PAGE}): $names"
    val y = (bandTop + bandBottom) * 0.5f + text.textSize * 0.35f
    c.drawText(ellipsizeToWidth(msg, text, right - left), left, y, text)
}

// ──────────────────────────────────────────────────────────────────────────────
// Notes / dye-pen area
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Draw the dye penetrant result checkboxes and a notes field at the bottom of the page.
 *
 * The machinist circles PASS or FAIL by hand, then adds free-form notes about damage,
 * pitting locations, or other observations. Phase 2 will add digital entry for these.
 */
private fun drawWearNotesArea(
    c: Canvas,
    text: Paint,
    left: Float,
    right: Float,
    y: Float,
) {
    val boxSize = 10f
    var x = left

    c.drawText("Dye pen inspection: ", x, y, text)
    x += text.measureText("Dye pen inspection: ")

    // PASS checkbox
    c.drawRect(x, y - boxSize, x + boxSize, y, Paint(text).apply { style = Paint.Style.STROKE; strokeWidth = 1f })
    x += boxSize + 4f
    c.drawText("PASS", x, y, text); x += text.measureText("PASS") + 20f

    // FAIL checkbox
    c.drawRect(x, y - boxSize, x + boxSize, y, Paint(text).apply { style = Paint.Style.STROKE; strokeWidth = 1f })
    x += boxSize + 4f
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
private const val WEAR_HEADER_GAP_PT          = 16f   // gap from header rule to drawing area
private const val WEAR_OAL_ABOVE_SHAFT_PT     = 90f   // gap from shaft top edge to OAL line (≈1.25 in — raised so the dimension doesn't crowd the profile; matches RunoutPdfComposer.OAL_LINE_SPACE_PT, 2026-07-18)
private const val WEAR_NOTES_BOTTOM_OFFSET_PT = 24f   // notes baseline above contentBot
private const val WEAR_NOTES_GAP_PT           = 28f   // gap from drawing area bottom to notes

// Wear detail strips (Phase 4) — sizing/pagination constants live in WearStripLayout.kt
// (WEAR_STRIP_MAX_PER_PAGE, WEAR_STRIP_HEIGHT_PT, WEAR_MIN_PROFILE_HEIGHT_PT, etc.) since
// that file's layout math is what the unit tests exercise directly. These two are purely
// about THIS composer's own reserved space and stay local.
private const val WEAR_OVERFLOW_NOTE_HEIGHT_PT   = 16f  // reserved band for the "+N more liners" text note
private const val WEAR_PROFILE_RADIUS_MARGIN_PT  = 8f   // headroom above/below the shaft's actual drawn radius

private const val COMPRESS_TRIGGER_PT = 220f
private const val ZIGZAG_GAP_MAX_PT   = 20f
