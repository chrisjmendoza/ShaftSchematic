package com.android.shaftschematic.pdf

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.android.shaftschematic.geom.ClampedUndercutSpanMm
import com.android.shaftschematic.geom.SurfaceSeg
import com.android.shaftschematic.geom.UndercutLinerSpan
import com.android.shaftschematic.geom.UndercutSpanMm
import com.android.shaftschematic.geom.UndercutStrip
import com.android.shaftschematic.geom.buildUndercutStrips
import com.android.shaftschematic.geom.clampUndercutSpan
import com.android.shaftschematic.geom.computeOalWindow
import com.android.shaftschematic.geom.computeSetPositionsInMeasureSpace
import com.android.shaftschematic.geom.effectiveNotchDiaMm
import com.android.shaftschematic.geom.maxOuterDiaOver
import com.android.shaftschematic.geom.minOuterDiaOver
import com.android.shaftschematic.geom.notchProfiles
import com.android.shaftschematic.geom.outerDiaAt
import com.android.shaftschematic.geom.planDiaCallouts
import com.android.shaftschematic.model.*
import com.android.shaftschematic.settings.PdfPrefs
import com.android.shaftschematic.ui.resolved.ResolvedComponent
import com.android.shaftschematic.ui.resolved.surfaceSegsFrom
import com.android.shaftschematic.util.UnitSystem
import com.android.shaftschematic.util.buildLinerTitleById
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

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
 * sections (see `docs/UndercutDrawing_PLAN.md` §8): header, SET-to-SET OAL line, the full
 * shaft profile with notches, and one zoomed detail strip per **liner holding cuts** plus
 * one per bare-shaft cluster (chained dimension rail + total above, measured-Ø callouts
 * below, anchor-from-SET title).
 *
 * ## Page layout (landscape US Letter, 792 × 612 pt)
 * ```
 * ┌─── header: job info line / "UNDERCUT RECORD" ─────────────────────────────┐
 * │   ←────────── OAL (AFT SET → FWD SET) ─────────────────────────→          │
 * │   [shaft profile — full length, notches cut at each undercut]             │
 * │   ← AFT                                                          FWD →    │
 * │   [detail strip: total rail / chained rail / profile / Ø / name + SET]    │
 * │   Notes: ______________________________________________________________   │
 * └───────────────────────────────────────────────────────────────────────────┘
 * ```
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
 * model. Every notch on both draw sites (this composer and the canvas overlay) comes from
 * the one shared pipeline — `clampUndercutSpan` → `effectiveNotchDiaMm(dia,
 * minOuterDiaOver(segs, …))` → `notchProfiles(surfaceSegsFrom(resolved), …)` — so the
 * printed sheet and the screen cannot disagree about where material was removed.
 *
 * Same public contract as `composeWearPdf`/`composeRunoutPdf`: landscape US Letter page
 * already started by the caller; `resolvedComponents`, when provided, replace `spec.bodies`
 * for the drawn profile (`withResolvedBodies`) and supply the surface envelope the notches
 * are cut against.
 *
 * @param page    Target PDF page (US Letter landscape, already started).
 * @param spec    Shaft specification in millimeters.
 * @param project Job information (customer, vessel, job#, side).
 * @param unit    Display unit for every printed dimension.
 * @param undercutRecord Recorded undercut sections. Strips (`buildUndercutStrips`) decide the
 *                page mode: 0 → profile-only form, 1 → one full-width strip, 2+ → a
 *                2-column grid of strips with a "+N more" note past the cap.
 * @param blankValues Blank-draft (write-in) mode: the record is dropped entirely
 *                (`UndercutRecord()`), so the page is the profile form with header writing
 *                rules and an empty OAL break — the wear sheet's decision that blank
 *                templates carry no recorded stations.
 */
fun composeUndercutPdf(
    page: PdfDocument.Page,
    spec: ShaftSpec,
    project: ProjectInfo,
    unit: UnitSystem,
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
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; textSize = UC_TEXT_PT
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        color = Color.BLACK
    }
    fun shadeFill() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; color = Color.argb(40, 0, 0, 0)
    }
    val bodyFill: Paint? = if (pdfPrefs.shadedBodies) shadeFill() else null
    val taperFill: Paint? = if (pdfPrefs.shadedTapers) shadeFill() else null
    val linerFill: Paint? = if (pdfPrefs.shadedLiners) shadeFill() else null
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

    // ── Horizontal scale: SET to SET, so the profile fills the page width ──────
    val oalWindow = computeOalWindow(spec)
    val setPositions = computeSetPositionsInMeasureSpace(oalWindow, spec)
    val aftSetMm = setPositions.aftSETxMm.toFloat()
    val fwdSetMm = setPositions.fwdSETxMm.toFloat()
    val drawSpanMm = (fwdSetMm - aftSetMm).coerceAtLeast(1f)
    val ptPerMm = contentW / drawSpanMm

    fun xAt(mm: Float): Float = contentLeft + (mm - aftSetMm) * ptPerMm
    fun rPx(diaMm: Float): Float = (diaMm * 0.5f) * ptPerMm

    val maxDiaMm = (docSpec.maxOuterDiaMm().takeIf { it > 0f } ?: 50f).coerceAtLeast(20f)

    // ── Undercut geometry (shared pipeline — see the file KDoc) ────────────────
    // The surface envelope comes from the SAME mapping the canvas overlay uses
    // (`surfaceSegsFrom`), so both sites cut notches against an identical local surface.
    // With no resolved list the drawn spec is the only source available, so its components
    // stand in — auto-body spans are already folded into `docSpec.bodies` by
    // `withResolvedBodies` whenever a resolved list WAS supplied.
    val surfaceSegs = resolvedComponents?.let { surfaceSegsFrom(it) } ?: surfaceSegsFromSpec(docSpec)
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
    val strips = buildUndercutStrips(liveSpans, linerSpans, shaftExtentMm)
    val mode = determineUndercutPdfMode(strips.size)
    val maxPerPage = undercutStripsPerPage(mode)
    val onPage = strips.take(maxPerPage)
    val overflow = strips.drop(maxPerPage)
    val overflowNoteH = if (overflow.isNotEmpty()) UC_OVERFLOW_NOTE_HEIGHT_PT else 0f

    val undercutById = effectiveRecord.undercuts.associateBy { it.id }

    // ── Header ───────────────────────────────────────────────────────────────
    drawUndercutHeader(c, text, contentLeft, contentRight, contentTop, project, blankValues)

    // The profile's minimum height protects the actual drawn shaft radius — ptPerMm is a
    // purely horizontal scale, so a wide/short shaft's true diameter could otherwise exceed
    // a shrunk profile band.
    val minProfileHeightPt = maxOf(WEAR_MIN_PROFILE_HEIGHT_PT, 2f * rPx(maxDiaMm) + UC_PROFILE_RADIUS_MARGIN_PT)
    val preferredProfileHeightPt = maxOf(
        UC_OAL_TOP_REGION_PT + UC_OAL_ABOVE_SHAFT_PT + 2f * rPx(maxDiaMm) +
            UC_PROFILE_NAMES_ROW_PT + UC_PROFILE_BOTTOM_PAD_PT,
        minProfileHeightPt,
    )

    // Vertical banding: GRID lays the strips two-up, otherwise a single full-width column
    // below the profile (0 strips = profile form). Both keep the profile on top. Reused from
    // the wear sheet verbatim — the banding is count-driven and content-agnostic.
    val profileTop: Float
    val profileBottom: Float
    val stripCells: List<WearStripCell>
    val overflowBandTop: Float
    val profileToStripsGap = if (blankValues) WEAR_STRIP_TOP_GAP_BLANK_PT else WEAR_STRIP_TOP_GAP_PT
    if (mode == WearPdfMode.GRID) {
        val grid = computeWearStripGridLayout(
            midTopFull, midBotFull, contentLeft, contentRight, onPage.size,
            reservedBottomPt = overflowNoteH, minProfileHeightPt = minProfileHeightPt,
            profileToStripsGapPt = profileToStripsGap,
            preferredProfileHeightPt = preferredProfileHeightPt,
            maxStripHeightPt = UC_STRIP_HEIGHT_MAX_PT,
        )
        profileTop = grid.profileTop; profileBottom = grid.profileBottom
        stripCells = grid.cells
        overflowBandTop = grid.cells.lastOrNull()?.bottom ?: profileBottom
    } else {
        val v = computeWearVerticalLayout(
            midTopFull, midBotFull, onPage.size,
            reservedBottomPt = overflowNoteH, minProfileHeightPt = minProfileHeightPt,
            profileToStripsGapPt = profileToStripsGap,
            preferredProfileHeightPt = preferredProfileHeightPt,
            maxStripHeightPt = UC_STRIP_HEIGHT_MAX_PT,
        )
        profileTop = v.profileTop; profileBottom = v.profileBottom
        stripCells = onPage.indices.map { i ->
            WearStripCell(v.stripTops[i], v.stripBottoms[i], contentLeft, contentRight)
        }
        overflowBandTop = v.stripBottoms.lastOrNull() ?: profileBottom
    }

    // ── Main profile + OAL line ───────────────────────────────────────────────
    val profileSlack = ((profileBottom - profileTop) - preferredProfileHeightPt).coerceAtLeast(0f)
    val shaftCy = (profileTop + UC_OAL_TOP_REGION_PT + UC_OAL_ABOVE_SHAFT_PT +
        rPx(maxDiaMm) + profileSlack * 0.5f)
        .coerceAtMost(profileBottom - rPx(maxDiaMm) - UC_PROFILE_NAMES_ROW_PT)
    val geomRect = RectF(contentLeft, profileTop, contentRight, profileBottom)
    val shaftTopApprox = shaftCy - rPx(maxDiaMm)
    val oalLineY = (shaftTopApprox - UC_OAL_ABOVE_SHAFT_PT).coerceAtLeast(profileTop + UC_TEXT_PT + 6f)

    drawUndercutOalLine(
        c, dim, text, contentLeft, contentRight, oalLineY, shaftTopApprox,
        unit, spec.overallLengthMm, blankValues,
    )
    drawUndercutShaftProfile(
        c, docSpec, shaftCy, outline, geomRect, ::xAt, ::rPx,
        bodyFill = bodyFill, taperFill = taperFill, linerFill = linerFill, ptPerMm = ptPerMm,
    )
    // Notches on the full profile, at true position and the true (small) scale — the same
    // construction the strips draw zoomed, so nothing is a separate "marker style".
    drawUndercutNotches(
        c, surfaceSegs,
        liveSpans.map { s -> NotchCut(s.startMm, s.endMm, undercutById[s.id]?.diaMm ?: 0f) },
        shaftCy, ::xAt, ::rPx, outline, voidFill,
    )

    drawUndercutDirectionRef(c, text, contentLeft, contentRight, shaftCy + rPx(maxDiaMm), profileBottom)

    // ── Detail strips, one per liner / bare-shaft cluster ────────────────────
    onPage.forEachIndexed { i, strip ->
        val cell = stripCells[i]
        drawUndercutDetailStrip(
            c, docSpec, surfaceSegs, strip,
            strip.undercutIds.mapNotNull { id -> undercutById[id] },
            clampedById,
            cell.top, cell.bottom, cell.left, cell.right,
            unit, aftSetMm, fwdSetMm, text, outline, dim, diaText,
            bodyFill = bodyFill, taperFill = taperFill, linerFill = linerFill,
            shaftExtentMm = shaftExtentMm,
            voidFill = voidFill,
            linerTitle = (strip as? UndercutStrip.LinerStrip)?.let { linerTitles[it.linerId] },
            blankValues = blankValues,
        )
    }
    if (overflow.isNotEmpty()) {
        drawUndercutOverflowNote(c, text, contentLeft, contentRight, overflowBandTop, midBotFull, overflow.size)
    }

    // ── Notes row ────────────────────────────────────────────────────────────
    // Notes only: the dye-pen PASS/FAIL checkboxes are a wear/inspection concern and have no
    // place on a machining record (`docs/UndercutDrawing_PLAN.md` §11.4).
    drawUndercutNotesRow(c, text, contentLeft, contentRight, notesY)
}

// ──────────────────────────────────────────────────────────────────────────────
// Header
// ──────────────────────────────────────────────────────────────────────────────

private fun drawUndercutHeader(
    c: Canvas,
    text: Paint,
    left: Float,
    right: Float,
    top: Float,
    project: ProjectInfo,
    blankValues: Boolean,
) {
    val ts = text.textSize
    fun centeredX(str: String): Float = ((left + right - text.measureText(str)) * 0.5f).coerceAtLeast(left)

    if (blankValues) {
        // Blank draft: the five job-info fields spread edge-to-edge with equal writing rules.
        // The OAL belongs to the drawing's end-to-end span, never the header.
        val line1Y = top + ts + 4f
        val line2Y = line1Y + UC_HEADER_BLANK_LINE_GAP_PT
        val labels = listOf("Customer:", "Vessel:", "Job #:", "Date:", "Side:")
        val labelsW = labels.sumOf { text.measureText(it).toDouble() }.toFloat()
        // drawLabelWithRule inserts 4f label→rule and returns ruleEnd + 14f (inter-field gap).
        val ruleW = ((right - left - labelsW - labels.size * 4f - (labels.size - 1) * 14f) / labels.size)
            .coerceAtLeast(BLANK_RULE_PT * 0.5f)
        var x = left
        labels.forEach { label -> x = drawLabelWithRule(c, label, x, line1Y, text, ruleWidth = ruleW, maxRight = right) }
        c.drawText(UNDERCUT_DOC_TITLE, centeredX(UNDERCUT_DOC_TITLE), line2Y, text)
    } else {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val side = project.side.printableLabelOrNull()?.let { "  $it" } ?: ""
        val line1 = buildString {
            if (project.customer.isNotBlank()) append("Customer: ${project.customer}   ")
            if (project.vessel.isNotBlank()) append("Vessel: ${project.vessel}   ")
            if (project.jobNumber.isNotBlank()) append("Job #: ${project.jobNumber}   ")
            append("Date: $date$side")
        }
        val line1Fit = ellipsizeToWidth(line1, text, right - left)
        val line2Fit = ellipsizeToWidth(UNDERCUT_DOC_TITLE, text, right - left)
        c.drawText(line1Fit, centeredX(line1Fit), top + ts, text)
        c.drawText(line2Fit, centeredX(line2Fit), top + ts + ts * 1.4f, text)
    }

    val ruleY = top + (if (blankValues) UC_HEADER_HEIGHT_BLANK_PT else UC_HEADER_HEIGHT_PT)
    c.drawLine(left, ruleY, right, ruleY, Paint(text).apply { style = Paint.Style.STROKE; strokeWidth = 0.5f })
}

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
        val label = if (unit == UnitSystem.INCHES) "OAL: ${"%.4f".format(oalMm / 25.4f)}\""
        else "OAL: ${"%.2f".format(oalMm)} mm"
        val lw = text.measureText(label)
        val gapHalf = lw * 0.5f + DIM_BREAK_TEXT_PAD_PT
        if ((mid - gapHalf) - x0 >= arrowLen + 2f) {
            c.drawLine(x0, oalLineY, mid - gapHalf, oalLineY, dim)
            c.drawLine(mid + gapHalf, oalLineY, x1, oalLineY, dim)
            val fm = text.fontMetrics
            c.drawText(label, mid - lw * 0.5f, oalLineY - (fm.ascent + fm.descent) * 0.5f, text)
        } else {
            // Span too short to host the break plus inward arrows: continuous line, label
            // above — PdfDimensionRenderer's fallback rule.
            c.drawLine(x0, oalLineY, x1, oalLineY, dim)
            c.drawText(label, mid - lw * 0.5f, oalLineY - 4f, text)
        }
    }
    c.drawLine(x0, oalLineY, x0 + arrowLen, oalLineY - arrowLen * 0.4f, dim)
    c.drawLine(x0, oalLineY, x0 + arrowLen, oalLineY + arrowLen * 0.4f, dim)
    c.drawLine(x1, oalLineY, x1 - arrowLen, oalLineY - arrowLen * 0.4f, dim)
    c.drawLine(x1, oalLineY, x1 - arrowLen, oalLineY + arrowLen * 0.4f, dim)
}

/** "← AFT" / "FWD →" under the profile — the app-wide orientation convention. */
private fun drawUndercutDirectionRef(
    c: Canvas, text: Paint, left: Float, right: Float, shaftBottomY: Float, bandBottom: Float,
) {
    val p = Paint(text)
    val y = (shaftBottomY + p.textSize + 12f).coerceAtMost(bandBottom - 2f)
    p.textAlign = Paint.Align.LEFT
    c.drawText("← AFT", left, y, p)
    p.textAlign = Paint.Align.RIGHT
    c.drawText("FWD →", right, y, p)
}

// ──────────────────────────────────────────────────────────────────────────────
// Shaft profile (full length) — same drawing approach as the wear sheet's
// ──────────────────────────────────────────────────────────────────────────────

private fun drawUndercutShaftProfile(
    c: Canvas,
    spec: ShaftSpec,
    cy: Float,
    outline: Paint,
    geomRect: RectF,
    xAt: (Float) -> Float,
    rPx: (Float) -> Float,
    bodyFill: Paint?,
    taperFill: Paint?,
    linerFill: Paint?,
    ptPerMm: Float,
) {
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
            val path = Path().apply {
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
    // Bodies, with a compression break in any long run.
    val capPaint = Paint(outline)
    spec.bodies.forEach { b ->
        if (b.lengthMm <= 0f || b.diaMm <= 0f) return@forEach
        val x0 = xAt(b.startFromAftMm); val x1 = xAt(b.startFromAftMm + b.lengthMm)
        val r = rPx(b.diaMm); val top = cy - r; val bot = cy + r
        val lenPt = abs(x1 - x0)
        if (lenPt < UC_COMPRESS_TRIGGER_PT) {
            c.drawLine(x0, top, x1, top, outline); c.drawLine(x0, bot, x1, bot, outline)
            c.drawLine(x0, top, x0, bot, outline); c.drawLine(x1, top, x1, bot, outline)
        } else {
            val mid = (x0 + x1) * 0.5f; val gap = min(UC_ZIGZAG_GAP_MAX_PT, 0.25f * lenPt)
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
    spec.tapers.forEach { t ->
        if (t.lengthMm <= 0f || (t.startDiaMm <= 0f && t.endDiaMm <= 0f)) return@forEach
        val x0 = xAt(t.startFromAftMm); val x1 = xAt(t.startFromAftMm + t.lengthMm)
        c.drawLine(x0, cy - rPx(t.startDiaMm), x1, cy - rPx(t.endDiaMm), outline)
        c.drawLine(x0, cy + rPx(t.startDiaMm), x1, cy + rPx(t.endDiaMm), outline)
        c.drawLine(x0, cy - rPx(t.startDiaMm), x0, cy + rPx(t.startDiaMm), outline)
        c.drawLine(x1, cy - rPx(t.endDiaMm), x1, cy + rPx(t.endDiaMm), outline)
    }
    val dimPaint = Paint(outline).apply { strokeWidth = UC_DIM_PT }
    spec.liners.forEach { ln ->
        if (ln.lengthMm <= 0f || ln.odMm <= 0f) return@forEach
        val x0 = xAt(ln.startFromAftMm); val x1 = xAt(ln.startFromAftMm + ln.lengthMm)
        val r = rPx(ln.odMm); val top = cy - r; val bot = cy + r
        c.drawLine(x0, top, x1, top, outline); c.drawLine(x0, bot, x1, bot, outline)
        c.drawLine(x0, top, x0, bot, dimPaint); c.drawLine(x1, top, x1, bot, dimPaint)
    }
    val hatchPaint = Paint(outline).apply { strokeWidth = UC_DIM_PT * 0.6f; alpha = 160 }
    spec.threads.forEach { th ->
        if (th.lengthMm <= 0f || th.majorDiaMm <= 0f) return@forEach
        val x0 = xAt(th.startFromAftMm); val x1 = xAt(th.startFromAftMm + th.lengthMm)
        val r = rPx(th.majorDiaMm); val top = cy - r; val bot = cy + r
        val pitchPt = ((th.pitchMm.takeIf { it > 0f } ?: 2.5f) * ptPerMm).coerceIn(4f, 18f)
        drawUndercutHatch(c, x0, x1, top, bot, hatchPaint, pitchPt)
        c.drawLine(x0, top, x1, top, outline); c.drawLine(x0, bot, x1, bot, outline)
        c.drawLine(x0, top, x0, bot, outline); c.drawLine(x1, top, x1, bot, outline)
    }
    val slotFill = Paint(outline).apply { style = Paint.Style.FILL; alpha = 40 }
    drawCouplerBoltSlots(c, spec.couplerBoltSlots, spec, cy, xAt, rPx, outline, slotFill)
}

/** Diagonal hatch clipped to `[x0,x1] × [top,bot]` — the threaded-zone mark. */
private fun drawUndercutHatch(c: Canvas, x0: Float, x1: Float, top: Float, bot: Float, paint: Paint, pitchPt: Float) {
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

/**
 * Outer-surface segments derived straight from the drawn spec — the fallback used when the
 * caller supplied no resolved component list. Mirrors `surfaceSegsFrom`'s rules (bodies and
 * liners constant Ø, tapers linear, threads at their major-Ø envelope, coupler bolt slots
 * skipped as radial cutouts rather than surface material).
 */
private fun surfaceSegsFromSpec(spec: ShaftSpec): List<SurfaceSeg> = buildList {
    spec.bodies.forEach { add(SurfaceSeg(it.startFromAftMm, it.startFromAftMm + it.lengthMm, it.diaMm, it.diaMm)) }
    spec.liners.forEach { add(SurfaceSeg(it.startFromAftMm, it.startFromAftMm + it.lengthMm, it.odMm, it.odMm)) }
    spec.tapers.forEach { add(SurfaceSeg(it.startFromAftMm, it.startFromAftMm + it.lengthMm, it.startDiaMm, it.endDiaMm)) }
    spec.threads.forEach { add(SurfaceSeg(it.startFromAftMm, it.startFromAftMm + it.lengthMm, it.majorDiaMm, it.majorDiaMm)) }
}.filter { it.endMm > it.startMm && (it.diaStartMm > 0f || it.diaEndMm > 0f) }

// ──────────────────────────────────────────────────────────────────────────────
// Notches — the void cut into the surface, drawn identically at every scale
// ──────────────────────────────────────────────────────────────────────────────

/** One undercut reduced to what a notch needs: its render-clamped span and its recorded Ø. */
private data class NotchCut(val startMm: Float, val endMm: Float, val diaMm: Float)

/**
 * The Ø a notch's floor is cut to — the shared pipeline both draw sites run: a recorded Ø
 * verbatim, or the symbolic shallow floor `effectiveNotchDiaMm` substitutes for a
 * placed-but-unmeasured undercut, referenced to the smallest local surface Ø over the span.
 * A symbolic floor keeps the section visible and dimensioned on the sheet; it never becomes
 * a printed Ø value (see `buildUndercutDiaStations`).
 */
private fun notchFloorDiaMm(segs: List<SurfaceSeg>, cut: NotchCut): Float =
    effectiveNotchDiaMm(cut.diaMm, minOuterDiaOver(segs, cut.startMm, cut.endMm))

/**
 * Draws every notch in [cuts] against the local outer surface [segs]: the removed material
 * is painted out in the page colour from the surface polyline down to the floor (mirrored
 * about the centreline), then the shoulders and floor are outlined on top. Portions where
 * the surface is already at or below the floor yield no region and draw nothing —
 * `notchProfiles` owns that rule, so a cut running off a liner onto smaller stock simply
 * stops at the liner edge.
 */
private fun drawUndercutNotches(
    c: Canvas,
    segs: List<SurfaceSeg>,
    cuts: List<NotchCut>,
    cy: Float,
    xAt: (Float) -> Float,
    rAt: (Float) -> Float,
    outline: Paint,
    voidFill: Paint,
) {
    if (cuts.isEmpty() || segs.isEmpty()) return
    cuts.forEach { cut ->
        val floorDia = notchFloorDiaMm(segs, cut)
        if (floorDia <= 0f) return@forEach
        val rFloor = rAt(floorDia)
        notchProfiles(segs, cut.startMm, cut.endMm, floorDia).forEach { np ->
            if (np.surface.size < 2) return@forEach
            val xStart = xAt(np.startMm)
            val xEnd = xAt(np.endMm)

            // Void fill, top half then its mirror below the centreline.
            listOf(-1f, 1f).forEach { sign ->
                val path = Path()
                np.surface.forEachIndexed { i, sp ->
                    val y = cy + sign * rAt(sp.diaMm)
                    if (i == 0) path.moveTo(xAt(sp.xMm), y) else path.lineTo(xAt(sp.xMm), y)
                }
                path.lineTo(xEnd, cy + sign * rFloor)
                path.lineTo(xStart, cy + sign * rFloor)
                path.close()
                c.drawPath(path, voidFill)
            }

            // Outline: the two shoulders and the floor, top and bottom. A shoulder whose
            // surface has run down to meet the floor (a taper dropping into the cut)
            // degenerates to zero height and simply disappears.
            val rSurf0 = rAt(np.surface.first().diaMm)
            val rSurf1 = rAt(np.surface.last().diaMm)
            listOf(-1f, 1f).forEach { sign ->
                c.drawLine(xStart, cy + sign * rSurf0, xStart, cy + sign * rFloor, outline)
                c.drawLine(xStart, cy + sign * rFloor, xEnd, cy + sign * rFloor, outline)
                c.drawLine(xEnd, cy + sign * rFloor, xEnd, cy + sign * rSurf1, outline)
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Detail strip — one zoomed liner / bare-shaft cluster
// ──────────────────────────────────────────────────────────────────────────────

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
 * lockstep with the wear strips. A blank undercut sheet currently composes no strips at
 * all — its record is dropped before clustering — so this path only comes alive if that
 * template decision is revisited; keeping it here is what makes revisiting it a one-line
 * change rather than a rewrite.
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
    blankValues: Boolean,
) {
    val drawStartMm = strip.drawStartMm
    val drawEndMm = strip.drawEndMm
    val drawLengthMm = drawEndMm - drawStartMm
    if (drawLengthMm <= 0f) return

    val hLayout = computeWearStripHorizontalLayout(
        stripLeft, stripRight, drawLengthMm, stubWidthPt = UNDERCUT_STRIP_EDGE_INSET_PT,
    )
    val ptPerMmStrip = hLayout.ptPerMm
    fun xAtStrip(mm: Float): Float = hLayout.linerLeftPt + (mm - drawStartMm) * ptPerMmStrip

    val titleText = Paint(text).apply { textSize = (text.textSize - 1f).coerceAtLeast(7f) }
    val dimText = Paint(text).apply { textSize = (text.textSize - 2f).coerceAtLeast(7f) }

    // Spans on this strip, aft → fwd, render-clamped.
    val spans = undercuts.mapNotNull { u ->
        val s = clampedById[u.id] ?: return@mapNotNull null
        if (s.isEmpty) null else UndercutSpanMm(u.id, s.startMm, s.endMm)
    }.sortedBy { it.startMm }

    // Measured-Ø plan first, so the strip reserves exactly the label rows its cuts need.
    val diaStations = buildUndercutDiaStations(
        undercuts, clampedById, ::xAtStrip, unit, { s -> diaText.measureText(s) },
    )
    val diaPlan = if (diaStations.isEmpty()) null else
        planDiaCallouts(diaStations, stripLeft + 2f, stripRight - 2f, UC_DIA_MIN_GAP_PT)
    val diaBandPt = diaPlan?.let { it.labelsHeightPt(diaText.textSize, UC_DIA_ROW_GAP_PT) + 2f } ?: 0f

    val railSpans = buildUndercutRailSpans(strip.chainStartMm, strip.chainEndMm, spans, unit)
    val totalSpan = buildUndercutTotalSpan(spans, unit)

    val inner = computeUndercutStripInnerLayout(
        stripTop, stripBottom,
        titleHeightPt = titleText.textSize,
        hasTotalRail = totalSpan != null,
        diaBandPt = diaBandPt,
    )
    val cy = (inner.cylTop + inner.cylBottom) / 2f
    val rCap = ((inner.cylBottom - inner.cylTop) / 2f).coerceAtLeast(0f)

    // Vertical scale: the largest surface Ø inside the draw range fills the strip's budget, so
    // a strip always draws as tall as the strip allows regardless of its length. On a liner
    // strip that maximum is the liner's own OD, so the liner reads full height and the
    // neighbouring stock slivers step down from it — the wear strip's look.
    val stripMaxDiaMm = maxOuterDiaOver(segs, drawStartMm, drawEndMm).takeIf { it > 0f }
        ?: docSpec.maxOuterDiaMm().takeIf { it > 0f } ?: 1f
    fun rStrip(diaMm: Float): Float = (rCap * (diaMm / stripMaxDiaMm)).coerceIn(0f, rCap)

    drawUndercutWindowProfile(
        c, docSpec, drawStartMm, drawEndMm, cy, ::xAtStrip, ::rStrip,
        outline, bodyFill, taperFill, linerFill, ptPerMmStrip,
    )
    // Cut ends: an S-break where the strip slices through material (void beyond it, so the
    // AFT stub's eye is at the top and the FWD stub's at the bottom — the stub convention,
    // the inverse of a centred compression break), a flat edge where the draw range's end IS
    // the physical shaft end.
    drawUndercutWindowEnd(c, segs, drawStartMm, cy, ::xAtStrip, ::rStrip, outline, shaftExtentMm, eyeAtTop = true)
    drawUndercutWindowEnd(c, segs, drawEndMm, cy, ::xAtStrip, ::rStrip, outline, shaftExtentMm, eyeAtTop = false)

    drawUndercutNotches(
        c, segs,
        spans.map { s -> NotchCut(s.startMm, s.endMm, undercuts.first { it.id == s.id }.diaMm) },
        cy, ::xAtStrip, ::rStrip, outline, voidFill,
    )

    // ── Measured-Ø callouts: leader from each notch floor down to the printed value ──
    if (diaPlan != null) {
        val floorBottomYByKey = undercuts.associate { u ->
            val s = clampedById[u.id]
            val floorDia = if (s == null || s.isEmpty) 0f
            else notchFloorDiaMm(segs, NotchCut(s.startMm, s.endMm, u.diaMm))
            u.id to (cy + rStrip(floorDia))
        }
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
            c.drawText(p.label, p.labelCx - p.labelWidth * 0.5f, p.labelTopY - fm.ascent, diaText)
        }
    }

    // ── Rails: the chain, then the total on its own line above it ──
    val railLayout = layoutWearStripRail(
        railSpans,
        xAtStripMm = { mm -> xAtStrip(mm) },
        labelWidthPt = { s -> dimText.measureText(s) },
    )
    drawUndercutRail(
        c, dim, dimText, railLayout,
        witnessBottomY = inner.cylTop - UC_RAIL_WITNESS_GAP_PT,
        railY = inner.chainRailY,
        maxLabelRows = inner.railLabelRows,
        drawLabels = !blankValues,
    )
    if (totalSpan != null) {
        val totalLayout = layoutWearStripRail(
            listOf(totalSpan),
            xAtStripMm = { mm -> xAtStrip(mm) },
            labelWidthPt = { s -> dimText.measureText(s) },
        )
        drawUndercutRail(
            c, dim, dimText, totalLayout,
            witnessBottomY = inner.chainRailY,
            railY = inner.totalRailY,
            maxLabelRows = 1,
            drawLabels = !blankValues,
            fallbackLabelAbove = true,
        )
    }

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
    } else if (spans.isNotEmpty()) {
        val anchor = undercutAnchorFor(
            firstShoulderMm = spans.first().startMm,
            lastShoulderMm = spans.last().endMm,
            aftSetXMm = aftSetMm,
            fwdSetXMm = fwdSetMm,
        )
        val label = ellipsizeToWidth(
            buildUndercutStripTitle(linerTitle, buildUndercutAnchorLabel(anchor, unit)),
            titleText,
            stripRight - stripLeft,
        )
        if (anchor.alignRight) {
            titleText.textAlign = Paint.Align.RIGHT
            c.drawText(label, stripRight, titleBaselineY, titleText)
        } else {
            titleText.textAlign = Paint.Align.LEFT
            c.drawText(label, stripLeft, titleBaselineY, titleText)
        }
    } else if (linerTitle != null) {
        // A liner strip can exist with no drawable cut (every assigned span clamped away):
        // the liner is still worth naming, there is just no anchor dimension to state.
        titleText.textAlign = Paint.Align.LEFT
        c.drawText(ellipsizeToWidth(linerTitle, titleText, stripRight - stripLeft), stripLeft, titleBaselineY, titleText)
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
    ptPerMm: Float,
) {
    val dimPaint = Paint(outline).apply { strokeWidth = UC_DIM_PT }
    fun inside(mm: Float) = mm > w0Mm + UC_EDGE_EPS_MM && mm < w1Mm - UC_EDGE_EPS_MM

    fun drawConstant(startMm: Float, endMm: Float, diaMm: Float, fill: Paint?, capPaint: Paint) {
        if (diaMm <= 0f) return
        val a = max(startMm, w0Mm); val b = min(endMm, w1Mm)
        if (b - a <= UC_EDGE_EPS_MM) return
        val x0 = xAt(a); val x1 = xAt(b); val r = rAt(diaMm)
        fill?.let { c.drawRect(x0, cy - r, x1, cy + r, it) }
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
        val a = max(s, w0Mm); val b = min(e, w1Mm)
        if (b - a <= UC_EDGE_EPS_MM) return@forEach
        fun diaAt(mm: Float) = t.startDiaMm + (t.endDiaMm - t.startDiaMm) * ((mm - s) / t.lengthMm).coerceIn(0f, 1f)
        val ra = rAt(diaAt(a)); val rb = rAt(diaAt(b))
        val xa = xAt(a); val xb = xAt(b)
        taperFill?.let { f ->
            c.drawPath(
                Path().apply {
                    moveTo(xa, cy - ra); lineTo(xb, cy - rb); lineTo(xb, cy + rb); lineTo(xa, cy + ra); close()
                },
                f,
            )
        }
        c.drawLine(xa, cy - ra, xb, cy - rb, outline)
        c.drawLine(xa, cy + ra, xb, cy + rb, outline)
        if (inside(s)) c.drawLine(xa, cy - ra, xa, cy + ra, outline)
        if (inside(e)) c.drawLine(xb, cy - rb, xb, cy + rb, outline)
    }
    spec.liners.forEach { ln ->
        if (ln.lengthMm > 0f) drawConstant(ln.startFromAftMm, ln.startFromAftMm + ln.lengthMm, ln.odMm, linerFill, dimPaint)
    }
    val hatchPaint = Paint(outline).apply { strokeWidth = UC_DIM_PT * 0.6f; alpha = 160 }
    spec.threads.forEach { th ->
        if (th.lengthMm <= 0f || th.majorDiaMm <= 0f) return@forEach
        val s = th.startFromAftMm; val e = s + th.lengthMm
        val a = max(s, w0Mm); val b = min(e, w1Mm)
        if (b - a <= UC_EDGE_EPS_MM) return@forEach
        val r = rAt(th.majorDiaMm)
        val pitchPt = ((th.pitchMm.takeIf { it > 0f } ?: 2.5f) * ptPerMm).coerceIn(4f, 18f)
        drawUndercutHatch(c, xAt(a), xAt(b), cy - r, cy + r, hatchPaint, pitchPt)
        c.drawLine(xAt(a), cy - r, xAt(b), cy - r, outline)
        c.drawLine(xAt(a), cy + r, xAt(b), cy + r, outline)
        if (inside(s)) c.drawLine(xAt(a), cy - r, xAt(a), cy + r, outline)
        if (inside(e)) c.drawLine(xAt(b), cy - r, xAt(b), cy + r, outline)
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
    val dia = outerDiaAt(segs, atMm)
    if (dia <= 0f) return
    val r = rAt(dia)
    if (r <= 0f) return
    val x = xAt(atMm)
    val isPhysicalEnd = atMm <= UC_EDGE_EPS_MM || atMm >= shaftExtentMm - UC_EDGE_EPS_MM
    if (isPhysicalEnd) {
        c.drawLine(x, cy - r, x, cy + r, outline)
    } else {
        drawBreakEdge(c, x, cy - r, cy + r, r * 0.6f, outline, eyeAtTop = eyeAtTop)
    }
}

/**
 * Draws one dimension rail: witness lines from [witnessBottomY] up past [railY], an arrowed
 * dimension line per span, and each span's value. A label that fits inside its own span
 * (the inward-arrow test, which also guarantees stub room at [DIM_BREAK_TEXT_PAD_PT])
 * **seats in a break cut in the line**, vertically centred — the schematic's
 * value-in-a-break convention. A label wider than its span falls back to the stacked rows
 * `layoutWearStripRail` assigned, clamped to [maxLabelRows]; on the total-span rail
 * ([fallbackLabelAbove]) it goes ABOVE the line instead, since the chained rail occupies
 * the space below it.
 *
 * [drawLabels] = false (blank write-in draft) keeps every line and arrowhead but skips the
 * values — the lines-in/values-out template rule.
 *
 * This mirrors the wear sheet's `drawWearStripRail` rather than calling it: that function
 * is private to `WearPdfComposer.kt`, and this one additionally needs the
 * [witnessBottomY]/[fallbackLabelAbove] freedom the second (total) rail line requires. The
 * shared part — where labels seat, how arrows flip, how rows stack — is the pure
 * `layoutWearStripRail` both call.
 */
private fun drawUndercutRail(
    c: Canvas,
    dim: Paint,
    dimText: Paint,
    layout: List<WearRailSpanLayout>,
    witnessBottomY: Float,
    railY: Float,
    maxLabelRows: Int,
    drawLabels: Boolean,
    fallbackLabelAbove: Boolean = false,
) {
    if (layout.isEmpty()) return
    val arrow = 4f
    val labelGapPt = 2f
    val rowStepPt = dimText.textSize + 3f
    val witnessExt = 3f

    layout.forEach { s ->
        c.drawLine(s.x0Pt, witnessBottomY, s.x0Pt, railY - witnessExt, dim)
        c.drawLine(s.x1Pt, witnessBottomY, s.x1Pt, railY - witnessExt, dim)

        val lw = dimText.measureText(s.label)
        val seatsInBreak = drawLabels && s.arrowInward
        if (seatsInBreak) {
            val gapHalf = lw * 0.5f + DIM_BREAK_TEXT_PAD_PT
            c.drawLine(s.x0Pt, railY, s.labelCxPt - gapHalf, railY, dim)
            c.drawLine(s.labelCxPt + gapHalf, railY, s.x1Pt, railY, dim)
            val fm = dimText.fontMetrics
            c.drawText(s.label, s.labelCxPt - lw * 0.5f, railY - (fm.ascent + fm.descent) * 0.5f, dimText)
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
            if (fallbackLabelAbove) {
                c.drawText(s.label, s.labelCxPt - lw * 0.5f, railY - labelGapPt - 1f, dimText)
            } else {
                val row = s.labelRow.coerceAtMost(maxLabelRows - 1)
                if (row >= 0) {
                    val ly = railY + labelGapPt + dimText.textSize + row * rowStepPt
                    c.drawText(s.label, s.labelCxPt - lw * 0.5f, ly, dimText)
                }
            }
        }
    }
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
private const val UC_MARGIN_PT = 36f

private const val UC_HEADER_HEIGHT_PT = 36f          // 2-line header block
private const val UC_HEADER_HEIGHT_BLANK_PT = 56f    // blank write-in header: handwriting room
private const val UC_HEADER_BLANK_LINE_GAP_PT = 24f  // baseline gap between the blank header's two lines
private const val UC_HEADER_GAP_PT = 16f             // header rule → drawing area
private const val UC_OAL_ABOVE_SHAFT_PT = 44f        // shaft top edge → OAL line

private const val UC_PROFILE_NAMES_ROW_PT = 26f      // direction row under the shaft bottom
private const val UC_PROFILE_BOTTOM_PAD_PT = 6f      // air under the direction row before the strips gap
private const val UC_OAL_TOP_REGION_PT = 18f         // OAL label + air above the dimension line
private const val UC_STRIP_HEIGHT_MAX_PT = 190f      // per-strip growth cap when the profile donates slack
private const val UC_NOTES_BOTTOM_OFFSET_PT = 24f    // notes baseline above contentBot
private const val UC_NOTES_GAP_PT = 28f              // drawing area bottom → notes
private const val UC_OVERFLOW_NOTE_HEIGHT_PT = 16f   // reserved band for the "+N more" note
private const val UC_PROFILE_RADIUS_MARGIN_PT = 8f   // headroom above/below the shaft's drawn radius

private const val UC_COMPRESS_TRIGGER_PT = 220f
private const val UC_ZIGZAG_GAP_MAX_PT = 20f

private const val UC_RAIL_WITNESS_GAP_PT = 3f        // clear gap between the drawn surface and a witness line

private const val UC_DIA_TEXT_PT = 8f                // measured-Ø value text size
private const val UC_DIA_MIN_GAP_PT = 5f             // min clear gap between label edges / leader drops
private const val UC_DIA_ROW_GAP_PT = 3f             // vertical gap between the two label rows

/** Tolerance for "this edge coincides with a window end" tests, mm. */
private const val UC_EDGE_EPS_MM = 1e-3f
