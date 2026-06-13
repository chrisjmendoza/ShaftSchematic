package com.android.shaftschematic.pdf

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.android.shaftschematic.model.*
import com.android.shaftschematic.geom.computeOalWindow
import com.android.shaftschematic.geom.computeSetPositionsInMeasureSpace
import com.android.shaftschematic.settings.PdfPrefs
import com.android.shaftschematic.settings.RunoutConfig
import com.android.shaftschematic.settings.TirDirection
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
 * RunoutPdfComposer
 *
 * Generates a runout measurement sheet PDF page for the given shaft specification.
 *
 * ## Page layout (landscape US Letter, 792 × 612 pt)
 *
 * ```
 * ┌─── header: Customer / Vessel / Job# / Date / Side ───────────────────────┐
 * │   ←─────────── OAL (AFT SET → FWD SET) ───────────────────────→         │
 * │   [shaft profile: bodies, tapers, liners, compression breaks]             │
 * │                                                                            │
 * │   ╲  ╲  ╲  ╲   ╲  ╲  ╲   ╲  ╲  ╲     ← diagonal leader lines           │
 * │   ○  ○  ○  ○   ○  ○  ○   ○  ○  ○     ← row-1 bubbles (shorter leaders) │
 * │      ○     ○      ○          ○        ← row-2 bubbles (longer leaders)  │
 * │                                                                            │
 * │  TIR's taken looking: _______________________                             │
 * └───────────────────────────────────────────────────────────────────────────┘
 * ```
 *
 * ## Bubble placement convention
 * - **Tapers**: two stations inset from each edge by [RunoutConfig.RUNOUT_EDGE_INSET_MM].
 *   Readings taken right on a taper's SET or LET face are unreliable.
 * - **Liners**: same inset convention as tapers.
 * - **Bodies**: evenly distributed stations, no edge inset (body surfaces are uniform).
 * - **Threads**: no stations (threads are not measured for runout).
 *
 * ## Two-row layout
 * Within each component section, consecutive stations alternate between row 1 (shorter
 * leader, bubbles closer to shaft) and row 2 (longer leader, bubbles further away).
 * This prevents overlap between adjacent circles and visually groups each section's
 * readings together — matching the hand-drawn convention in the shop reference drawings.
 *
 * ## Keyway reference marker
 * A small filled square drawn at the top of each circle indicates keyway-at-top centre.
 * In Phase 2 (value entry), a radial line will be added inside the circle to indicate
 * the high-spot direction relative to this reference.
 *
 * @param page     Target PDF page (US Letter landscape, already started).
 * @param spec     Shaft specification in millimeters.
 * @param config   Runout preferences — bubble count overrides and TIR direction label.
 * @param project  Job information (customer, vessel, job#, side).
 * @param unit     Display unit for the OAL dimension label.
 */
fun composeRunoutPdf(
    page: PdfDocument.Page,
    spec: ShaftSpec,
    config: RunoutConfig,
    project: ProjectInfo,
    unit: UnitSystem,
    pdfPrefs: PdfPrefs = PdfPrefs(),
    lineThicknessScale: Float = 1.0f,
) {
    val c = page.canvas
    c.drawColor(Color.WHITE)

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
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.BLACK
    }
    fun shadeFill() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(40, 0, 0, 0)
    }
    val bodyFill : Paint? = if (pdfPrefs.shadedBodies) shadeFill() else null
    val taperFill: Paint? = if (pdfPrefs.shadedTapers) shadeFill() else null
    val linerFill: Paint? = if (pdfPrefs.shadedLiners) shadeFill() else null
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        textSize = TEXT_PT
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        color = Color.BLACK
    }
    val textSmall = Paint(text).apply { textSize = TEXT_PT - 2f }

    // ── Page regions ─────────────────────────────────────────────────────────
    val margin = PAGE_MARGIN_PT
    val contentLeft  = margin
    val contentRight = pageW - margin
    val contentW     = contentRight - contentLeft

    // Header strip (job info, compact single area at the very top)
    val headerTop    = margin
    val headerHeight = HEADER_HEIGHT_PT
    val headerBottom = headerTop + headerHeight

    // ── Compute shaft scale ───────────────────────────────────────────────────
    // The runout sheet spans the SET-to-SET measurement window (AFT SET face → FWD SET face).
    // Thread components outside this window are not drawn on the runout profile.
    val oalWindow      = computeOalWindow(spec)
    val setPositions   = computeSetPositionsInMeasureSpace(oalWindow, spec)
    val aftSetMm       = setPositions.aftSETxMm.toFloat()
    val fwdSetMm       = setPositions.fwdSETxMm.toFloat()
    val drawSpanMm     = (fwdSetMm - aftSetMm).coerceAtLeast(1f)
    val ptPerMm        = contentW / drawSpanMm
    val measureStartMm = aftSetMm   // contentLeft ↔ AFT SET face, contentRight ↔ FWD SET face

    /** Convert physical shaft mm → page-x points. Anchored at the AFT SET face. */
    fun xAt(mm: Float): Float = contentLeft + (mm - measureStartMm) * ptPerMm

    /** Convert a diameter mm → radius in points. */
    fun rPx(diaMm: Float): Float = (diaMm * 0.5f) * ptPerMm

    // ── Vertical layout — shaft centred, bubbles below, TIR at bottom ─────────
    //
    // The shaft is drawn at its ACTUAL outer diameter (not a fixed height) so the
    // profile looks proportionally correct. We then compute the remaining vertical
    // space and split it between the bubble area and breathing room.
    //
    // Layout (top → bottom):
    //   margin → header → OAL line → [shaftTop … shaftCy … shaftBottom] →
    //   bubble area (two rows) → TIR line → margin

    val maxOuterDiaMm  = spec.maxOuterDiaMm().coerceAtLeast(10f)
    val shaftHalfPt    = rPx(maxOuterDiaMm)  // actual half-height of the shaft drawing

    // Vertical space from below the OAL line to the bottom margin
    val tirY           = pageH - margin - TIR_LINE_HEIGHT_PT
    val availableH     = tirY - (headerBottom + OAL_GAP_PT + OAL_LINE_SPACE_PT)

    // How tall the bubble section is: short leaders + long leaders row + two diameters + gaps
    val bubbleSectionH = LONG_LEADER_PT + BUBBLE_RADIUS_PT * 2f + BUBBLE_GAP_PT

    // Place the shaft centred in the top part of the available space
    val shaftAreaH     = availableH - bubbleSectionH
    val shaftCy        = headerBottom + OAL_GAP_PT + OAL_LINE_SPACE_PT +
                         (shaftAreaH / 2f).coerceAtLeast(shaftHalfPt + 4f)

    val geomRect = RectF(contentLeft, margin, contentRight, pageH - margin)

    // ── Outer-radius lookup — returns the ACTUAL shaft surface y for a given mm ─
    // Used so leader lines originate from the shaft's visible outline, not a fixed y.
    fun shaftOuterRPxAt(mm: Float): Float {
        var maxR = 0f
        spec.bodies.forEach { b ->
            if (mm >= b.startFromAftMm - 0.1f && mm <= b.startFromAftMm + b.lengthMm + 0.1f)
                maxR = maxOf(maxR, rPx(b.diaMm))
        }
        spec.tapers.forEach { t ->
            val s = t.startFromAftMm; val e = s + t.lengthMm
            if (mm >= s - 0.1f && mm <= e + 0.1f) {
                val frac = ((mm - s) / (e - s)).coerceIn(0f, 1f)
                maxR = maxOf(maxR, rPx(t.startDiaMm + (t.endDiaMm - t.startDiaMm) * frac))
            }
        }
        spec.liners.forEach { ln ->
            if (mm >= ln.startFromAftMm - 0.1f && mm <= ln.startFromAftMm + ln.lengthMm + 0.1f)
                maxR = maxOf(maxR, rPx(ln.odMm))
        }
        return maxR.coerceAtLeast(shaftHalfPt * 0.1f)
    }

    // ── Draw header ───────────────────────────────────────────────────────────
    drawRunoutHeader(c, text, contentLeft, contentRight,
        headerTop, headerHeight, spec, project, unit, drawSpanMm)

    // ── Draw OAL span line — sits just above the shaft top, like the schematic ─
    val oalLineY = shaftCy - shaftHalfPt - OAL_LINE_SPACE_PT
    drawOalSpanLine(c, dim, text, contentLeft, contentRight,
        oalLineY, spec, unit, drawSpanMm)

    // ── Draw shaft profile ────────────────────────────────────────────────────
    drawShaftProfile(c, spec, shaftCy, outline, geomRect, ::xAt, ::rPx,
        bodyFill = bodyFill, taperFill = taperFill, linerFill = linerFill, ptPerMm = ptPerMm)

    // ── Compute placed stations (with guaranteed collision-free bubble positions) ──
    val placedStations = computePlacedStations(
        spec = spec,
        config = config,
        contentLeft = contentLeft,
        contentRight = contentRight,
        ptPerMm = ptPerMm,
        measureStartMm = measureStartMm,
        shaftCy = shaftCy,
        shaftOuterRPxAt = ::shaftOuterRPxAt,
    )

    // ── Draw bubbles with diagonal fanning leaders ────────────────────────────
    drawPlacedBubbles(c, placedStations, shaftCy, outline, fill)

    // ── Draw TIR direction line ───────────────────────────────────────────────
    drawTirLine(c, text, contentLeft, contentRight, tirY, config.tirDirection)
}

// ──────────────────────────────────────────────────────────────────────────────
// Station data model — placed (collision-free)
// ──────────────────────────────────────────────────────────────────────────────

/**
 * A fully resolved runout station, ready to draw.
 *
 * @param stationXPt     Page-x of the axial measurement point on the shaft.
 * @param shaftBottomYPt Page-y of the shaft's outer surface at this station.
 *                       Leaders originate HERE, touching the shaft outline.
 * @param bubbleXPt      Page-x assigned to the bubble centre (spread out to avoid collision).
 * @param bubbleCenterYPt Page-y of the bubble centre.
 */
private data class PlacedStation(
    val stationXPt: Float,
    val shaftBottomYPt: Float,
    val bubbleXPt: Float,
    val bubbleCenterYPt: Float,
)

// ──────────────────────────────────────────────────────────────────────────────
// Station computation
// ──────────────────────────────────────────────────────────────────────────────

/** Component type for runout station assignment — determines default count and inset behaviour. */
private enum class StationKind { BODY, TAPER, LINER }

/**
 * Compute all runout stations and assign component-local, collision-free bubble positions.
 *
 * ## Key design principle
 * Bubbles must stay **near the component they measure**. A taper's bubbles cluster below
 * the taper's page region; a body's bubbles cluster below the body. This matches the
 * hand-drawn shop convention and makes the drawing readable.
 *
 * ## Algorithm (per component)
 * 1. Compute the component's page X span: `[xAt(startMm), xAt(endMm)]`.
 * 2. Compute N axial station positions within that component.
 * 3. Split into two rows: local index 0,2,4,… → row 0 (shorter leaders);
 *    local index 1,3,5,… → row 1 (longer leaders).
 * 4. Spread N bubble X positions centred on the component's midpoint.
 *    Slot width = BUBBLE_DIAMETER + MIN_GAP. If N slots exceed the component width
 *    the group is centred symmetrically beyond the edges — a natural "splay".
 * 5. Leaders fan diagonally from the shaft surface at each station's axial x to the
 *    assigned bubble position. Monotonicity within each component guarantees leaders
 *    do not cross each other.
 *
 * Thread components produce no stations.
 */
private fun computePlacedStations(
    spec: ShaftSpec,
    config: RunoutConfig,
    contentLeft: Float,
    contentRight: Float,
    ptPerMm: Float,
    measureStartMm: Float,
    shaftCy: Float,
    shaftOuterRPxAt: (Float) -> Float,
): List<PlacedStation> {

    data class ComponentEntry(
        val startMm: Float,
        val lengthMm: Float,
        val kind: StationKind,
        val id: String,
    )

    // Sort all components by AFT→FWD start position.
    val entries = buildList {
        spec.bodies.forEach  { add(ComponentEntry(it.startFromAftMm, it.lengthMm, StationKind.BODY,  it.id)) }
        spec.tapers.forEach  { add(ComponentEntry(it.startFromAftMm, it.lengthMm, StationKind.TAPER, it.id)) }
        spec.liners.forEach  { add(ComponentEntry(it.startFromAftMm, it.lengthMm, StationKind.LINER, it.id)) }
    }.filter { it.lengthMm > 0f }.sortedBy { it.startMm }

    val bubbleD = BUBBLE_RADIUS_PT * 2f
    val slot    = bubbleD + BUBBLE_MIN_GAP_PT

    data class RawStation(val stationXPt: Float, val shaftBottomYPt: Float, val bubbleXPt: Float)
    val rawList = mutableListOf<RawStation>()

    for (entry in entries) {
        val count = config.componentOverrides[entry.id]
            ?: when (entry.kind) {
                StationKind.TAPER -> RunoutConfig.TAPER_DEFAULT_COUNT
                StationKind.LINER -> RunoutConfig.LINER_DEFAULT_COUNT
                StationKind.BODY  -> RunoutConfig.BODY_DEFAULT_COUNT
            }
        if (count <= 0) continue

        // Axial station positions for this component (sorted AFT→FWD)
        val stationsMm = stationPositions(
            startMm      = entry.startMm,
            lengthMm     = entry.lengthMm,
            count        = count,
            useEdgeInset = entry.kind == StationKind.TAPER || entry.kind == StationKind.LINER,
        )

        // Component page X span
        val compLeft  = contentLeft + (entry.startMm - measureStartMm) * ptPerMm
        val compRight = contentLeft + (entry.startMm + entry.lengthMm - measureStartMm) * ptPerMm
        val compMidX  = (compLeft + compRight) * 0.5f

        // Centre N bubbles on the component's midpoint.
        // totalW = full width needed for this component's bubble group.
        val totalW   = count * slot - BUBBLE_MIN_GAP_PT
        val groupLeft = compMidX - totalW * 0.5f   // may extend outside the component edges

        stationsMm.forEachIndexed { localIdx, mm ->
            val stationX = contentLeft + (mm - measureStartMm) * ptPerMm
            val outerR   = shaftOuterRPxAt(mm)
            val bubbleX  = (groupLeft + localIdx * slot + BUBBLE_RADIUS_PT)
                .coerceIn(contentLeft + BUBBLE_RADIUS_PT, contentRight - BUBBLE_RADIUS_PT)
            rawList.add(RawStation(stationX, shaftCy + outerR, bubbleX))
        }
    }

    // Global greedy level assignment: sort by bubble X, assign the lowest level where
    // this bubble doesn't horizontally overlap any already-placed bubble at that level.
    val levels = IntArray(rawList.size)
    val levelRightEdge = mutableListOf<Float>()
    for (origIdx in rawList.indices.sortedBy { rawList[it].bubbleXPt }) {
        val bLeft = rawList[origIdx].bubbleXPt - BUBBLE_RADIUS_PT
        val level = levelRightEdge.indexOfFirst { it + BUBBLE_MIN_GAP_PT <= bLeft }
            .takeIf { it >= 0 } ?: levelRightEdge.size
        while (levelRightEdge.size <= level) levelRightEdge.add(Float.NEGATIVE_INFINITY)
        levelRightEdge[level] = rawList[origIdx].bubbleXPt + BUBBLE_RADIUS_PT
        levels[origIdx] = level
    }

    val levelStep = LONG_LEADER_PT - SHORT_LEADER_PT
    return rawList.mapIndexed { idx, raw ->
        val leaderLen = SHORT_LEADER_PT + levels[idx] * levelStep
        PlacedStation(
            stationXPt      = raw.stationXPt,
            shaftBottomYPt  = raw.shaftBottomYPt,
            bubbleXPt       = raw.bubbleXPt,
            bubbleCenterYPt = raw.shaftBottomYPt + leaderLen + BUBBLE_RADIUS_PT,
        )
    }
}

/**
 * Compute the axial mm positions of [count] measurement stations within a component.
 *
 * @param startMm      Component start position (AFT edge), mm.
 * @param lengthMm     Component axial length, mm.
 * @param count        Number of stations.
 * @param useEdgeInset If true (tapers and liners), the first and last stations are inset
 *                     from the component edges by [RunoutConfig.RUNOUT_EDGE_INSET_MM].
 *                     Bodies distribute evenly across the full length.
 */
private fun stationPositions(
    startMm: Float,
    lengthMm: Float,
    count: Int,
    useEdgeInset: Boolean,
): List<Float> {
    if (count == 1) return listOf(startMm + lengthMm * 0.5f)

    return if (useEdgeInset) {
        // Cap inset to 20% of component length so very short components still get sensible positions.
        val inset = min(RunoutConfig.RUNOUT_EDGE_INSET_MM, lengthMm * 0.20f)
        val innerStart = startMm + inset
        val innerEnd   = startMm + lengthMm - inset
        val innerSpan  = innerEnd - innerStart
        List(count) { i ->
            if (count == 1) innerStart + innerSpan * 0.5f
            else            innerStart + innerSpan * (i.toFloat() / (count - 1))
        }
    } else {
        // Evenly distributed across the full length (no inset for body sections).
        val spacing = lengthMm / (count + 1)
        List(count) { i -> startMm + spacing * (i + 1) }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Header drawing
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Draw the job-info header strip at the top of the runout page.
 *
 * Format (single line):  Customer: ___  |  Vessel: ___  |  Job#: ___  |  Date  |  STBD/PORT  |  OAL: xx in
 *
 * The OAL is included here so the field crew can confirm which shaft they're working on
 * without referring back to the main schematic page.
 */
private fun drawRunoutHeader(
    c: Canvas,
    text: Paint,
    left: Float,
    right: Float,
    top: Float,
    height: Float,
    spec: ShaftSpec,
    project: ProjectInfo,
    unit: UnitSystem,
    oalMm: Float,
) {
    val y = top + text.textSize + 2f
    val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    val oalDisplay = if (unit == UnitSystem.INCHES) {
        "${"%.4f".format(oalMm / 25.4f)} in"
    } else {
        "${"%.2f".format(oalMm)} mm"
    }
    val side = project.side.printableLabelOrNull()?.let { "  $it" } ?: ""

    val headerText = buildString {
        if (project.customer.isNotBlank()) append("Customer: ${project.customer}   ")
        if (project.vessel.isNotBlank())   append("Vessel: ${project.vessel}   ")
        if (project.jobNumber.isNotBlank()) append("Job #: ${project.jobNumber}   ")
        append("Date: $date$side")
    }
    c.drawText(headerText, left, y, text)

    // Thin rule below header
    val ruleY = top + height
    c.drawLine(left, ruleY, right, ruleY, Paint(text).apply {
        style = Paint.Style.STROKE; strokeWidth = 0.5f
    })
}

// ──────────────────────────────────────────────────────────────────────────────
// OAL span line
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Draw a single OAL dimension arrow spanning the full SET-to-SET measurement window.
 *
 * This is the only dimension shown on the runout sheet — everything else the field crew
 * needs is on the main schematic page.
 */
private fun drawOalSpanLine(
    c: Canvas,
    dim: Paint,
    text: Paint,
    x0: Float,
    x1: Float,
    y: Float,
    spec: ShaftSpec,
    unit: UnitSystem,
    oalMm: Float,
) {
    val arrowLen = 8f
    // Horizontal line
    c.drawLine(x0, y, x1, y, dim)
    // Left arrowhead
    c.drawLine(x0, y, x0 + arrowLen, y - arrowLen * 0.5f, dim)
    c.drawLine(x0, y, x0 + arrowLen, y + arrowLen * 0.5f, dim)
    // Right arrowhead
    c.drawLine(x1, y, x1 - arrowLen, y - arrowLen * 0.5f, dim)
    c.drawLine(x1, y, x1 - arrowLen, y + arrowLen * 0.5f, dim)

    // OAL label centred above the line
    val label = if (unit == UnitSystem.INCHES) {
        "OAL: ${"%.4f".format(oalMm / 25.4f)}\""
    } else {
        "OAL: ${"%.2f".format(oalMm)} mm"
    }
    val lw = text.measureText(label)
    c.drawText(label, (x0 + x1) * 0.5f - lw * 0.5f, y - 4f, text)
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
    // Bodies — with compression breaks for long sections
    drawBodiesForRunout(c, spec.bodies, cy, xAt, rPx, outline, geomRect)
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
}

/** Draw bodies using compression breaks for sections long enough to trigger it. */
private fun drawBodiesForRunout(
    c: Canvas,
    bodies: List<Body>,
    cy: Float,
    xAt: (Float) -> Float,
    rPx: (Float) -> Float,
    outline: Paint,
    geomRect: RectF,
) {
    val capPaint = Paint(outline)
    bodies.forEach { b ->
        if (b.lengthMm <= 0f || b.diaMm <= 0f) return@forEach
        val x0 = xAt(b.startFromAftMm); val x1 = xAt(b.startFromAftMm + b.lengthMm)
        val r  = rPx(b.diaMm);          val top = cy - r; val bot = cy + r
        val lenPt = abs(x1 - x0)

        if (lenPt < COMPRESS_TRIGGER_PT) {
            c.drawLine(x0, top, x1, top, outline)
            c.drawLine(x0, bot, x1, bot, outline)
            c.drawLine(x0, top, x0, bot, outline)
            c.drawLine(x1, top, x1, bot, outline)
        } else {
            // Centre break (same S-curve logic as the main schematic PDF)
            val mid   = (x0 + x1) * 0.5f
            val gap   = min(ZIGZAG_GAP_MAX_PT, 0.25f * lenPt)
            val half  = gap * 0.5f
            val lEnd  = (mid - half).coerceIn(geomRect.left, geomRect.right)
            val rBeg  = (mid + half).coerceIn(geomRect.left, geomRect.right)
            val amp   = r * 0.6f

            c.drawLine(x0, top, lEnd, top, outline)
            c.drawLine(x0, bot, lEnd, bot, outline)
            c.drawLine(x0, top, x0, bot, outline)
            drawBreakEdge(c, lEnd, top, bot, amp, capPaint)
            drawBreakEdge(c, rBeg, top, bot, amp, capPaint)
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
 * Draw all placed runout bubbles with diagonal fanning leader lines.
 *
 * Each [PlacedStation] has a pre-computed [bubbleXPt] that guarantees no collision
 * between adjacent circles. The leader runs diagonally from the shaft's actual outer
 * surface at the station's axial x to the bubble's assigned x position below — exactly
 * matching the hand-drawn convention where lines fan out to separate the bubbles.
 *
 * The monotonic assignment (station order = bubble order) guarantees that no two leader
 * lines cross each other.
 */
private fun drawPlacedBubbles(
    c: Canvas,
    stations: List<PlacedStation>,
    shaftCy: Float,
    outline: Paint,
    fill: Paint,
) {
    for (st in stations) {
        val leaderEndY = st.bubbleCenterYPt - BUBBLE_RADIUS_PT  // top of the circle

        // Diagonal leader from the shaft surface at the station's axial x
        // to the top of the bubble at its assigned (spread-out) x.
        c.drawLine(st.stationXPt, st.shaftBottomYPt, st.bubbleXPt, leaderEndY, outline)

        // Circle (blank — space for hand-written TIR value)
        c.drawCircle(st.bubbleXPt, st.bubbleCenterYPt, BUBBLE_RADIUS_PT, outline)

        // Keyway reference marker: small filled square at 12-o'clock on the circle.
        // Indicates keyway-at-top-centre as the angular reference for the reading.
        val sqH = KEYWAY_SQUARE_SIZE_PT * 0.5f
        c.drawRect(
            st.bubbleXPt - sqH,
            st.bubbleCenterYPt - BUBBLE_RADIUS_PT - sqH * 0.6f,
            st.bubbleXPt + sqH,
            st.bubbleCenterYPt - BUBBLE_RADIUS_PT + sqH * 0.6f,
            fill,
        )
    }
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
// S-curve break edge helper (shared with ShaftPdfComposer convention)
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Draw one S-curve break edge at position x, spanning the shaft from [yTop] to [yBot].
 * Amplitude controls how far the S-curves bulge. Positive amplitude bulges right then left;
 * negative mirrors. This matches the convention in [ShaftPdfComposer].
 */
private fun drawBreakEdge(c: Canvas, x: Float, yTop: Float, yBot: Float, amplitude: Float, p: Paint) {
    val h = yBot - yTop
    val q = h / 4f
    val path = android.graphics.Path().apply {
        moveTo(x, yTop)
        cubicTo(x + amplitude, yTop + q, x - amplitude, yTop + q * 2f, x, yTop + q * 2f)
        cubicTo(x + amplitude, yTop + q * 3f, x - amplitude, yBot, x, yBot)
    }
    c.drawPath(path, p)
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
private const val HEADER_HEIGHT_PT = 22f    // Compact single-line header
private const val OAL_GAP_PT       = 6f     // Gap from header rule to OAL line
private const val OAL_LINE_SPACE_PT = 18f   // Vertical room for OAL span line + label
private const val TIR_LINE_HEIGHT_PT = 20f  // Space for TIR direction line at bottom

// Bubble geometry — sized to hold hand-written decimal readings (e.g. .016)
private const val BUBBLE_RADIUS_PT      = 20f  // 40 pt ≈ 0.55 inch diameter (readable, not too large)
private const val BUBBLE_MIN_GAP_PT     = 5f   // Minimum horizontal gap between bubble edges
private const val SHORT_LEADER_PT       = 18f  // Leader length: row-0 (even-indexed per component)
private const val LONG_LEADER_PT        = 56f  // Leader length: row-1 (odd-indexed per component)
private const val KEYWAY_SQUARE_SIZE_PT = 4f   // Small filled square at top of each circle

// Total height occupied by both bubble rows below the shaft
private const val BUBBLE_GAP_PT         = 8f   // Extra space below last bubble row

// Body compression break (matches ShaftPdfComposer threshold)
private const val COMPRESS_TRIGGER_PT = 220f
private const val ZIGZAG_GAP_MAX_PT   = 20f


