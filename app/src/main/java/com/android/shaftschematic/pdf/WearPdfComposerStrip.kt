package com.android.shaftschematic.pdf

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.android.shaftschematic.model.*
import com.android.shaftschematic.geom.DiaCalloutStation
import com.android.shaftschematic.geom.SetPositions
import com.android.shaftschematic.geom.WearTraceReading
import com.android.shaftschematic.geom.WearTraceVertex
import com.android.shaftschematic.geom.WEAR_TRACE_MAX_DEPTH_FRAC
import com.android.shaftschematic.geom.buildWearTrace
import com.android.shaftschematic.geom.pitCenterY
import com.android.shaftschematic.geom.pitHalfArm
import com.android.shaftschematic.geom.planDiaCallouts
import com.android.shaftschematic.geom.sequenceWearTraces
import com.android.shaftschematic.geom.smoothWearTrace
import com.android.shaftschematic.settings.PDF_WEAR_BAND_SHADE_DEFAULT
import com.android.shaftschematic.util.DisplayUnits
import com.android.shaftschematic.util.drawDualLabelCentered
import com.android.shaftschematic.util.dualStackMetrics
import com.android.shaftschematic.util.measureDualLabel
import com.android.shaftschematic.util.setsStacked
import com.android.shaftschematic.util.drawRichText
import com.android.shaftschematic.util.measureRichText

/**
 * Canvas drawing for the wear document's broken-out detail strips — the other half of
 * `WearPdfComposer`, split out so the composer file holds the page assembly.
 *
 * Every strip's layout math (selection/pagination, clamping, vertical/horizontal banding, the
 * neighbor-diameter lookup, the rail spans, and the anchor-from-SET label) lives in the
 * android-free `WearStripLayout.kt`; the functions here only do the Canvas drawing.
 */

/** SMALL pit half-arm (pt) on a broken-out detail strip (zoomed, so a touch larger). */
private const val WEAR_PIT_SMALL_HALF_STRIP_PT = 2.5f

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
internal fun drawWearStripWindow(
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

    // Ratio of the outline's (already thickness-scaled) weight — see WearPdfComposer's dimPaint.
    val dimPaint = Paint(outline).apply { strokeWidth = outline.strokeWidth * (WEAR_DIM_PT / WEAR_OUTLINE_PT) }

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
        val tickPaint = Paint(outline).apply { strokeWidth = outline.strokeWidth * (WEAR_DIM_PT * 0.6f / WEAR_OUTLINE_PT); alpha = 160 }
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
    val hatch = Paint(outline).apply { strokeWidth = outline.strokeWidth * (WEAR_DIM_PT * 0.6f / WEAR_OUTLINE_PT); alpha = 160 }
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

private const val WEAR_DIA_TICK_OVERSHOOT_PT = 2f   // witness tick overshoot past the cylinder edges (strips)
