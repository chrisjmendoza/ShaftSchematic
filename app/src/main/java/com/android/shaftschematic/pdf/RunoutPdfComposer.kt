package com.android.shaftschematic.pdf

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.android.shaftschematic.model.*
import com.android.shaftschematic.geom.PlacedRunoutBubble
import com.android.shaftschematic.geom.RunoutBubbleGeometry
import com.android.shaftschematic.geom.RunoutComponentKind
import com.android.shaftschematic.geom.RunoutComponentSpan
import com.android.shaftschematic.geom.collectRunoutStations
import com.android.shaftschematic.geom.computeOalWindow
import com.android.shaftschematic.geom.computeSetPositionsInMeasureSpace
import com.android.shaftschematic.geom.planRunoutBubbles
import com.android.shaftschematic.settings.PdfPrefs
import com.android.shaftschematic.settings.RunoutConfig
import com.android.shaftschematic.settings.TirDirection
import com.android.shaftschematic.ui.resolved.ResolvedBody
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
 * RunoutPdfComposer
 *
 * Generates a runout measurement sheet PDF page for the given shaft specification.
 *
 * ## Page layout (landscape US Letter, 792 × 612 pt)
 *
 * ```
 * ┌─── header: Customer / Vessel / Job# / Date / Side ───────────────────────┐
 * │  |←────────── OAL (AFT SET → FWD SET) ────────────────────→|  ← raised  │
 * │  |  [shaft profile: bodies, tapers, liners, threads, breaks] |  witness  │
 * │                                                                            │
 * │   ╲  ╲  ╲  ╲   ╲  ╲  ╲   ╲  ╲  ╲     ← leader lines (straight/dogleg)  │
 * │   ○     ○      ○      ○      ○        ← row-0 bubbles (closer to shaft) │
 * │      ○      ○     ○      ○       ○    ← row-1 bubbles (alternating)     │
 * │                                                                            │
 * │  TIR's taken looking: _______________________                             │
 * └───────────────────────────────────────────────────────────────────────────┘
 * ```
 *
 * ## Bubble placement convention
 * - **Tapers**: two stations inset from each edge by [RunoutConfig.RUNOUT_EDGE_INSET_MM].
 *   Readings taken right on a taper's SET or LET face are unreliable.
 * - **Liners**: same inset convention as tapers.
 * - **Bodies**: stations at cell midpoints across the full length, no edge inset
 *   (body surfaces are uniform).
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
 * keyway-at-top centre, matching the hand-drawn shop sheets. In Phase 2 (value entry),
 * a radial line will be added inside the circle to indicate the high-spot direction
 * relative to this reference.
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
) {
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

    // ── Bubble plan — horizontal solve first (rows + bubble x need only ptPerMm) ─
    // The shared engine (geom/RunoutBubbleLayout.kt) is also used by the RunoutRoute
    // canvas preview, so both renderings are guaranteed identical.
    val stationSpans = buildList {
        docSpec.bodies.forEach { add(RunoutComponentSpan(it.id, RunoutComponentKind.BODY, it.startFromAftMm, it.lengthMm)) }
        docSpec.tapers.forEach { add(RunoutComponentSpan(it.id, RunoutComponentKind.TAPER, it.startFromAftMm, it.lengthMm)) }
        docSpec.liners.forEach { add(RunoutComponentSpan(it.id, RunoutComponentKind.LINER, it.startFromAftMm, it.lengthMm)) }
    }
    val bubbleGeom = RunoutBubbleGeometry(
        radius = BUBBLE_RADIUS_PT,
        minGap = BUBBLE_MIN_GAP_PT,
        shortLeader = SHORT_LEADER_PT,
        contentLeft = contentLeft,
        contentRight = contentRight,
    )
    val bubblePlan = planRunoutBubbles(
        collectRunoutStations(stationSpans, config.componentOverrides, ::xAt),
        bubbleGeom,
    )

    // ── Vertical layout — shaft centred, bubbles below, TIR at bottom ─────────
    //
    // The shaft is drawn at its ACTUAL outer diameter (not a fixed height) so the
    // profile looks proportionally correct. We then compute the remaining vertical
    // space and split it between the bubble area and breathing room.
    //
    // Layout (top → bottom):
    //   margin → header → OAL line → [shaftTop … shaftCy … shaftBottom] →
    //   bubble area (rows from the plan) → TIR line → margin

    val maxOuterDiaMm  = docSpec.maxOuterDiaMm().coerceAtLeast(10f)
    val shaftHalfPt    = rPx(maxOuterDiaMm)  // actual half-height of the shaft drawing

    // Vertical space from below the OAL line to the bottom margin
    val tirY           = pageH - margin - TIR_LINE_HEIGHT_PT
    val availableH     = tirY - (headerBottom + OAL_GAP_PT + OAL_LINE_SPACE_PT)

    // How tall the bubble section is: leader gap + however many rows the plan needs
    val bubbleSectionH = bubblePlan.sectionHeight(BUBBLE_GAP_PT)

    // Place the shaft centred in the top part of the available space
    val shaftAreaH     = availableH - bubbleSectionH
    val shaftCy        = headerBottom + OAL_GAP_PT + OAL_LINE_SPACE_PT +
                         (shaftAreaH / 2f).coerceAtLeast(shaftHalfPt + 4f)

    val geomRect = RectF(contentLeft, margin, contentRight, pageH - margin)

    // ── Outer-radius lookup — returns the ACTUAL shaft surface y for a given mm ─
    // Used so leader lines originate from the shaft's visible outline, not a fixed y.
    fun shaftOuterRPxAt(mm: Float): Float {
        var maxR = 0f
        docSpec.bodies.forEach { b ->
            if (mm >= b.startFromAftMm - 0.1f && mm <= b.startFromAftMm + b.lengthMm + 0.1f)
                maxR = maxOf(maxR, rPx(b.diaMm))
        }
        docSpec.tapers.forEach { t ->
            val s = t.startFromAftMm; val e = s + t.lengthMm
            if (mm >= s - 0.1f && mm <= e + 0.1f) {
                val frac = ((mm - s) / (e - s)).coerceIn(0f, 1f)
                maxR = maxOf(maxR, rPx(t.startDiaMm + (t.endDiaMm - t.startDiaMm) * frac))
            }
        }
        docSpec.liners.forEach { ln ->
            if (mm >= ln.startFromAftMm - 0.1f && mm <= ln.startFromAftMm + ln.lengthMm + 0.1f)
                maxR = maxOf(maxR, rPx(ln.odMm))
        }
        return maxR.coerceAtLeast(shaftHalfPt * 0.1f)
    }

    // ── Draw header ───────────────────────────────────────────────────────────
    drawRunoutHeader(c, text, contentLeft, contentRight,
        headerTop, headerHeight, spec, project, unit, drawSpanMm)

    // ── Draw OAL span line — raised well above the shaft, with witness lines ──
    // The arrows bracket the drawn SET-to-SET span, but the LABEL is always the typed
    // OAL — same rule as the main schematic (the OAL number is sacred and never changes;
    // see docs/OverallLength.md). Witness (extension) lines drop to the shaft's actual
    // top edge at each SET face, matching the schematic/wear-document convention.
    val oalLineY = shaftCy - shaftHalfPt - OAL_LINE_SPACE_PT
    drawOalSpanLine(
        c, dim, text, contentLeft, contentRight, oalLineY,
        aftShaftTopY = shaftCy - shaftOuterRPxAt(aftSetMm),
        fwdShaftTopY = shaftCy - shaftOuterRPxAt(fwdSetMm),
        unit = unit, oalMm = spec.overallLengthMm,
    )

    // ── Draw shaft profile ────────────────────────────────────────────────────
    drawShaftProfile(c, docSpec, shaftCy, outline, geomRect, ::xAt, ::rPx,
        bodyFill = bodyFill, taperFill = taperFill, linerFill = linerFill, ptPerMm = ptPerMm)

    // ── Fix vertical bubble positions, route leaders, draw ────────────────────
    val bubbleResult = bubblePlan.finish(
        anchorY = shaftCy + shaftHalfPt,
        surfaceYAtMm = { mm -> shaftCy + shaftOuterRPxAt(mm) },
    )
    drawPlacedBubbles(c, bubbleResult.bubbles, outline)

    // ── Draw TIR direction line ───────────────────────────────────────────────
    drawTirLine(c, text, contentLeft, contentRight, tirY, config.tirDirection)
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
        "${"%.4f".format(oalMm / 25.4f)}\""
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
    c.drawText(ellipsizeToWidth(headerText, text, right - left), left, y, text)

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
 * Draw a single OAL dimension arrow spanning the full SET-to-SET measurement window,
 * with witness (extension) lines dropping to the shaft's top edge at each SET face —
 * the same convention as the main schematic and the wear document.
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
    aftShaftTopY: Float,
    fwdShaftTopY: Float,
    unit: UnitSystem,
    oalMm: Float,
) {
    val arrowLen = 8f
    val witnessGap = 3f   // gap between shaft edge and witness line start
    val witnessExt = 5f   // how far the witness line extends past the dimension line

    // Witness lines from the shaft's local top edge up past the dimension line
    c.drawLine(x0, aftShaftTopY - witnessGap, x0, y - witnessExt, dim)
    c.drawLine(x1, fwdShaftTopY - witnessGap, x1, y - witnessExt, dim)

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
    // Coupler bolt slots — reference cutouts, same as the main schematic.
    val slotFill = Paint(outline).apply { style = Paint.Style.FILL; alpha = 40 }
    drawCouplerBoltSlots(c, spec.couplerBoltSlots, spec, cy, xAt, rPx, outline, slotFill)
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
 * Draw all placed runout bubbles with their leader polylines.
 *
 * Placement comes from the shared engine (`geom/RunoutBubbleLayout.kt`), which
 * guarantees bubbles never touch and leaders never enter a bubble or cross each other.
 * A leader polyline is either a straight station→bubble diagonal (2 vertices) or a
 * dogleg with a vertical drop (3 vertices) when the straight route would collide.
 */
private fun drawPlacedBubbles(
    c: Canvas,
    bubbles: List<PlacedRunoutBubble>,
    outline: Paint,
) {
    val notchBlank = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    for (b in bubbles) {
        // Leader polyline from the shaft surface to the top of the circle.
        b.leader.zipWithNext { p, q -> c.drawLine(p.x, p.y, q.x, q.y, outline) }

        // Circle (blank — space for hand-written TIR value)
        c.drawCircle(b.bubbleX, b.bubbleCenterY, BUBBLE_RADIUS_PT, outline)

        // Keyway reference marker: open square notch straddling the rim at 12-o'clock —
        // key-at-top-centre as the angular reference, matching the hand-drawn sheets.
        // The white fill blanks the rim (and leader tip) inside the notch first.
        val sq = KEYWAY_SQUARE_SIZE_PT
        val top = b.bubbleCenterY - BUBBLE_RADIUS_PT - sq * 0.6f
        c.drawRect(b.bubbleX - sq * 0.5f, top, b.bubbleX + sq * 0.5f, top + sq, notchBlank)
        c.drawRect(b.bubbleX - sq * 0.5f, top, b.bubbleX + sq * 0.5f, top + sq, outline)
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
private const val OAL_LINE_SPACE_PT = 90f   // OAL line height above shaft top (≈1.25 in — raised so the dimension doesn't crowd the profile, 2026-07-18)
private const val TIR_LINE_HEIGHT_PT = 20f  // Space for TIR direction line at bottom

// Bubble geometry — sized to hold hand-written decimal readings (e.g. .016)
// Row spacing and leader routing are derived from these by geom/RunoutBubbleLayout.kt.
private const val BUBBLE_RADIUS_PT      = 20f  // 40 pt ≈ 0.55 inch diameter (readable, not too large)
private const val BUBBLE_MIN_GAP_PT     = 5f   // Minimum clear distance between circle edges
private const val SHORT_LEADER_PT       = 18f  // Deepest shaft surface → top of bubble row 0
private const val KEYWAY_SQUARE_SIZE_PT = 7f   // Open square notch straddling the rim at 12-o'clock

// Extra space below the last bubble row
private const val BUBBLE_GAP_PT         = 8f

// Body compression break (matches ShaftPdfComposer threshold)
private const val COMPRESS_TRIGGER_PT = 220f
private const val ZIGZAG_GAP_MAX_PT   = 20f


