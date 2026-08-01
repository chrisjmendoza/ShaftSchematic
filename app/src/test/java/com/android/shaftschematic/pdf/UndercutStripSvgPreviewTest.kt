package com.android.shaftschematic.pdf

import com.android.shaftschematic.geom.SurfaceSeg
import com.android.shaftschematic.geom.UndercutLinerSpan
import com.android.shaftschematic.geom.UndercutSpanMm
import com.android.shaftschematic.geom.UndercutStrip
import com.android.shaftschematic.geom.buildUndercutStrips
import com.android.shaftschematic.geom.clampUndercutSpan
import com.android.shaftschematic.geom.deepestUndercutDepthMm
import com.android.shaftschematic.geom.effectiveNotchDiaMm
import com.android.shaftschematic.geom.linerStripFor
import com.android.shaftschematic.geom.maxOuterDiaOver
import com.android.shaftschematic.geom.minOuterDiaOver
import com.android.shaftschematic.geom.normalizedNotchFloorDiaMm
import com.android.shaftschematic.geom.notchProfiles
import com.android.shaftschematic.geom.outerDiaAt
import com.android.shaftschematic.geom.planDiaCallouts
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Undercut
import com.android.shaftschematic.ui.resolved.ResolvedBody
import com.android.shaftschematic.ui.resolved.ResolvedComponentType
import com.android.shaftschematic.ui.resolved.ResolvedComponentSource
import com.android.shaftschematic.ui.resolved.ResolvedLiner
import com.android.shaftschematic.ui.resolved.surfaceSegsFrom
import com.android.shaftschematic.util.UnitSystem
import com.android.shaftschematic.util.buildLinerTitleById
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Same-math SVG preview of an undercut detail strip, for visual (markup) review without an
 * on-device round trip: drives the REAL layout and geometry code —
 * `surfaceSegsFrom`/`minOuterDiaOver`/`effectiveNotchDiaMm`/`notchProfiles`,
 * `buildUndercutStrips`, `computeWearStripHorizontalLayout`,
 * `computeUndercutStripInnerLayout`, `buildUndercutRailSpans`/`buildUndercutTotalSpan`/
 * `layoutWearStripRail`, `buildUndercutDiaStations`/`planDiaCallouts`, `undercutAnchorFor`,
 * `buildLinerTitleById`/`buildUndercutStripTitle` — and writes what `UndercutPdfComposer`
 * would draw as SVG into `app/build/reports/undercut-preview/`. Only text width is
 * approximated (no `Paint.measureText` on the JVM; a fixed per-char factor stands in), which
 * can shift label spreads by a point or two but exercises identical placement rules.
 *
 * Doubles as a smoke test: a cut inside a liner must produce ONE liner strip whose chain
 * anchors on the liner's own edges (never on the zoom pad), and the callouts must fit
 * uncompressed.
 */
class UndercutStripSvgPreviewTest {

    private val unit = UnitSystem.INCHES
    private val textH = 8f
    private val titleH = 9f
    private val rowGap = 3f
    private val minGap = 5f

    /** The sheet's drawn-depth exaggeration, the record's default (`exaggerationFrac`). */
    private val exaggerationFrac = 0.25f

    /** Stand-in for Paint.measureText at 8pt sans — close enough for preview spreads. */
    private fun labelW(s: String): Float = s.length * textH * 0.58f

    // ── Page geometry, mirroring `UndercutPdfComposer`'s constants ────────────
    // Landscape US Letter, 36 pt margins, 36 pt header + 16 pt header gap, 24 pt notes
    // baseline offset + 28 pt notes gap, and the 22 pt orientation-row band that is all the
    // strips-own-page layout keeps of the former whole-shaft profile.

    private val PAGE_W = 792f
    private val PAGE_H = 612f
    private val PAGE_MARGIN = 36f
    private val HEADER_RULE_Y = 72f
    private val DIRECTION_ROW_BASELINE = 98f
    /** Top of the strip band: the whole drawing area below the orientation row. */
    private val BAND_TOP = 110f
    private val BAND_BOTTOM = 524f
    private val NOTES_Y = 552f

    // ── Scenario: a liner over a body, three undercuts, one crossing the liner edge ──

    private val bodyDiaMm = 180f
    private val linerOdMm = 220f
    private val linerAftMm = 600f
    private val linerFwdMm = 1000f
    private val oalMm = 2000f
    private val aftSetMm = 100f
    private val fwdSetMm = 1900f

    private val components = listOf(
        ResolvedBody(
            id = "body", type = ResolvedComponentType.BODY, source = ResolvedComponentSource.EXPLICIT,
            startMmPhysical = 0f, endMmPhysical = oalMm, diaMm = bodyDiaMm,
        ),
        ResolvedLiner(
            id = "liner", startMmPhysical = linerAftMm, endMmPhysical = linerFwdMm, odMm = linerOdMm,
        ),
    )

    /** The spec the composer would draw from — the liner-title source (`buildLinerTitleById`). */
    private val spec = ShaftSpec(
        overallLengthMm = oalMm,
        bodies = listOf(Body(id = "body", startFromAftMm = 0f, lengthMm = oalMm, diaMm = bodyDiaMm)),
        liners = listOf(
            Liner(
                id = "liner", startFromAftMm = linerAftMm, lengthMm = linerFwdMm - linerAftMm,
                odMm = linerOdMm, endMmPhysical = linerFwdMm,
            ),
        ),
    )

    private val linerSpans = spec.liners
        .filter { it.lengthMm > 0f && it.odMm > 0f }
        .map { ln -> UndercutLinerSpan(ln.id, ln.startFromAftMm, ln.startFromAftMm + ln.lengthMm) }

    private val linerTitles = buildLinerTitleById(spec)

    /** Three cuts inside the liner, the last one overhanging its FWD edge at 1000 mm. */
    private fun linerUndercuts(thirdDiaMm: Float, secondDiaMm: Float) = listOf(
        Undercut(id = "u1", startFromAftMm = 700f, lengthMm = 60f, diaMm = 200f),
        Undercut(id = "u2", startFromAftMm = 820f, lengthMm = 60f, diaMm = secondDiaMm),
        // Crosses the liner's FWD edge at 1000 mm: the shoulder is taller on the liner side.
        Undercut(id = "u3", startFromAftMm = 960f, lengthMm = 70f, diaMm = thirdDiaMm),
    )

    /** Two cuts on bare stock well aft of the liner — the FreeStrip (padded window) case. */
    private fun bareShaftUndercuts() = listOf(
        Undercut(id = "b1", startFromAftMm = 240f, lengthMm = 50f, diaMm = 168f),
        Undercut(id = "b2", startFromAftMm = 340f, lengthMm = 40f, diaMm = 172f),
    )

    // ── Minimal SVG writer ────────────────────────────────────────────────────

    private class Svg {
        val sb = StringBuilder()
        fun line(x0: Float, y0: Float, x1: Float, y1: Float, w: Float = 0.8f, color: String = "black", dash: String? = null) {
            sb.append("""<line x1="$x0" y1="$y0" x2="$x1" y2="$y1" stroke="$color" stroke-width="$w"""")
            if (dash != null) sb.append(""" stroke-dasharray="$dash"""")
            sb.append("/>\n")
        }
        fun rect(x: Float, y: Float, w: Float, h: Float, stroke: String = "black", fill: String = "none", sw: Float = 1.2f) {
            sb.append("""<rect x="$x" y="$y" width="$w" height="$h" stroke="$stroke" fill="$fill" stroke-width="$sw"/>""").append('\n')
        }
        fun poly(pts: List<Pair<Float, Float>>, fill: String) {
            sb.append("""<polygon points="${pts.joinToString(" ") { "${it.first},${it.second}" }}" fill="$fill" stroke="none"/>""").append('\n')
        }
        fun path(d: String, w: Float = 1.2f) {
            sb.append("""<path d="$d" stroke="black" stroke-width="$w" fill="none"/>""").append('\n')
        }
        fun text(x: Float, y: Float, s: String, size: Float = 8f, anchor: String = "middle", color: String = "black") {
            sb.append("""<text x="$x" y="$y" font-size="$size" font-family="Helvetica, Arial, sans-serif" text-anchor="$anchor" fill="$color">$s</text>""").append('\n')
        }
        fun wrap(w: Float, h: Float): String =
            "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 $w $h\" width=\"${w * 1.4f}\" height=\"${h * 1.4f}\" style=\"background:white\">\n$sb</svg>\n"
    }

    /** The round-stock S-break, same cubics as `pdf/BreakSymbol.kt`. */
    private fun Svg.breakEdge(x: Float, yTop: Float, yBot: Float, amp: Float, eyeAtTop: Boolean) {
        val h = yBot - yTop
        val cy = yTop + h / 2f
        val k = 1.5f
        path("M $x $yTop C ${x + amp} ${yTop + h / 3f}, ${x - amp} ${yBot - h / 3f}, $x $yBot")
        if (eyeAtTop) {
            path("M $x $yTop C ${x - k * amp / 2f} ${yTop + h / 6f}, ${x - k * amp / 4f} ${yTop + h / 3f}, $x $cy")
        } else {
            path("M $x $yBot C ${x + k * amp / 2f} ${yBot - h / 6f}, ${x + k * amp / 4f} ${yBot - h / 3f}, $x $cy")
        }
    }

    /**
     * One arrowed rail span with its value seated in a break, mirroring `drawUndercutRail` —
     * including its label spacing: a fallback label clears the arrowheads by
     * `UC_RAIL_LABEL_GAP_PT` and stacks by [UNDERCUT_RAIL_ROW_HEIGHT_PT], the same pitch the
     * inner layout budgets.
     */
    private fun Svg.railSpan(s: WearRailSpanLayout, witnessBottomY: Float, railY: Float, labelAbove: Boolean) {
        val arrow = 4f
        val labelGap = 5f
        line(s.x0Pt, witnessBottomY, s.x0Pt, railY - 3f, w = 0.6f)
        line(s.x1Pt, witnessBottomY, s.x1Pt, railY - 3f, w = 0.6f)
        val lw = labelW(s.label)
        if (s.arrowInward) {
            val gapHalf = lw * 0.5f + DIM_BREAK_TEXT_PAD_PT
            line(s.x0Pt, railY, s.labelCxPt - gapHalf, railY, w = 0.7f)
            line(s.labelCxPt + gapHalf, railY, s.x1Pt, railY, w = 0.7f)
            text(s.labelCxPt, railY + textH * 0.35f, s.label)
        } else {
            line(s.x0Pt, railY, s.x1Pt, railY, w = 0.7f)
            val row = s.labelRow.coerceIn(0, WEAR_RAIL_MAX_LABEL_ROWS - 1)
            if (labelAbove) {
                text(
                    s.labelCxPt,
                    railY - arrow * 0.5f - labelGap - textH * 0.25f - row * UNDERCUT_RAIL_ROW_HEIGHT_PT,
                    s.label,
                )
            } else {
                text(s.labelCxPt, railY + arrow + labelGap + textH + row * UNDERCUT_RAIL_ROW_HEIGHT_PT, s.label)
            }
        }
        val dl = if (s.arrowInward) 1f else -1f
        val dr = if (s.arrowInward) -1f else 1f
        line(s.x0Pt, railY, s.x0Pt + dl * arrow, railY - arrow * 0.5f, w = 0.6f)
        line(s.x0Pt, railY, s.x0Pt + dl * arrow, railY + arrow * 0.5f, w = 0.6f)
        line(s.x1Pt, railY, s.x1Pt + dr * arrow, railY - arrow * 0.5f, w = 0.6f)
        line(s.x1Pt, railY, s.x1Pt + dr * arrow, railY + arrow * 0.5f, w = 0.6f)
    }

    /**
     * The page furniture a strips-own-page sheet keeps: the content frame, the single
     * orientation row that replaced the whole-shaft profile, and the notes rule. Drawn so a
     * preview shows the strip at its real place on the page, not floating.
     */
    private fun Svg.pageChrome() {
        rect(PAGE_MARGIN, PAGE_MARGIN, PAGE_W - 2 * PAGE_MARGIN, PAGE_H - 2 * PAGE_MARGIN, stroke = "#eee", sw = 0.5f)
        line(PAGE_MARGIN, HEADER_RULE_Y, PAGE_W - PAGE_MARGIN, HEADER_RULE_Y, w = 0.5f, color = "#bbb")
        text(PAGE_W / 2f, PAGE_MARGIN + 12f, "UNDERCUT RECORD", size = 10f)
        text(PAGE_MARGIN, DIRECTION_ROW_BASELINE, "← AFT", size = 10f, anchor = "start")
        text(PAGE_W - PAGE_MARGIN, DIRECTION_ROW_BASELINE, "FWD →", size = 10f, anchor = "end")
        text(PAGE_MARGIN, NOTES_Y, "Notes:", size = 10f, anchor = "start")
        line(PAGE_MARGIN + 34f, NOTES_Y + 2f, PAGE_W - PAGE_MARGIN, NOTES_Y + 2f, w = 0.7f, color = "#bbb")
    }

    // ── The preview ───────────────────────────────────────────────────────────

    private class StripResult(
        val svg: String,
        val strip: UndercutStrip,
        val chainCoversStrip: Boolean,
        val railInsideChain: Boolean,
        val compressed: Boolean,
        val hasTotal: Boolean,
        /** Fallback rows stacked ABOVE the chain rail line (`planUndercutRailRows`). */
        val fallbackRowsAbove: Int,
        /** Narrower of the two drawn pads (chain datum → break edge), points. */
        val minPadPt: Float,
        /**
         * Horizontal surface lines (top + bottom, any component) crossing the liner's
         * mid-station. Must be 0 on the blank write-in template, whose liner span is clear
         * paper — including for a body running underneath the liner.
         */
        val surfaceLinesAtLinerMid: Int,
    )

    private fun renderStrip(
        undercuts: List<Undercut>,
        stripLeft: Float = PAGE_MARGIN,
        stripRight: Float = PAGE_W - PAGE_MARGIN,
        stripTop: Float = BAND_TOP,
        stripBottom: Float = BAND_BOTTOM,
    ): StripResult {
        val segs: List<SurfaceSeg> = surfaceSegsFrom(components)
        val clampedById = undercuts.associate { it.id to clampUndercutSpan(it.startFromAftMm, it.lengthMm, oalMm) }
        val liveSpans = undercuts.mapNotNull { u ->
            val s = clampedById.getValue(u.id)
            if (s.isEmpty) null else UndercutSpanMm(u.id, s.startMm, s.endMm)
        }
        // Normalization reference, computed ONCE per sheet exactly as the composer does.
        val deepestDepthMm = deepestUndercutDepthMm(undercuts, segs, oalMm)
        val strips = buildUndercutStrips(liveSpans, linerSpans, oalMm)
        // Nothing recorded ⇒ the STARTED-strip page: one `linerStripFor` per drawable liner,
        // drawn to scale but dimensioned nowhere, for the machinist to sketch into.
        val started = undercuts.isEmpty()
        val strip: UndercutStrip = if (started) {
            assertTrue("an empty record yields no cut-derived strips", strips.isEmpty())
            linerStripFor(linerSpans.single(), emptyList(), oalMm)
        } else {
            assertEquals("the scenario must read as one detail strip", 1, strips.size)
            strips[0]
        }
        val linerTitle = (strip as? UndercutStrip.LinerStrip)?.let { linerTitles[it.linerId] }
        val spans = liveSpans.filter { it.id in strip.undercutIds }.sortedBy { it.startMm }

        // Draw range widened to the pt pad floor exactly as the composer does, so a preview
        // shows the real end air for the cell it is laid out in.
        val range = computeUndercutStripDrawRange(
            strip.drawStartMm, strip.drawEndMm, strip.chainStartMm, strip.chainEndMm,
            stripLeft, stripRight, oalMm,
        )
        val drawStartMm = range.startMm
        val drawEndMm = range.endMm
        val drawLenMm = drawEndMm - drawStartMm
        val h = computeWearStripHorizontalLayout(
            stripLeft, stripRight, drawLenMm, stubWidthPt = UNDERCUT_STRIP_EDGE_INSET_PT,
        )
        fun xAt(mm: Float) = h.linerLeftPt + (mm - drawStartMm) * h.ptPerMm

        val stations = buildUndercutDiaStations(undercuts, clampedById, ::xAt, unit, ::labelW)
        val plan = if (stations.isEmpty()) null else planDiaCallouts(stations, stripLeft + 2f, stripRight - 2f, minGap)
        val diaBand = plan?.let { it.labelsHeightPt(textH, rowGap) + 2f } ?: 0f

        val railSpans = if (started) emptyList()
        else buildUndercutRailSpans(strip.chainStartMm, strip.chainEndMm, spans, unit)
        val totalSpan = if (started) null else buildUndercutTotalSpan(spans, unit)
        // Chain before the vertical split, composer order: the split reserves the rows used,
        // on the side of the line the plan puts them.
        val chain = layoutWearStripRail(railSpans, xAtStripMm = ::xAt, labelWidthPt = ::labelW)
        val railPlan = planUndercutRailRows(chain, started, hasTotalRail = totalSpan != null)
        val inner = computeUndercutStripInnerLayout(
            stripTop, stripBottom, titleHeightPt = titleH, hasTotalRail = totalSpan != null, diaBandPt = diaBand,
            maxLabelRows = railPlan.belowRows,
            chainAboveBandPt = railPlan.aboveRows * UNDERCUT_RAIL_ROW_HEIGHT_PT,
        )

        val cy = (inner.cylTop + inner.cylBottom) / 2f
        val rCap = (inner.cylBottom - inner.cylTop) / 2f
        val stripMaxDia = maxOuterDiaOver(segs, drawStartMm, drawEndMm)
        fun rAt(diaMm: Float) = (rCap * (diaMm / stripMaxDia)).coerceIn(0f, rCap)

        val svg = Svg()
        svg.pageChrome()
        svg.rect(stripLeft, stripTop, stripRight - stripLeft, stripBottom - stripTop, stroke = "#ddd", sw = 0.5f)

        // Profile over the strip's DRAW range: every component clipped to it, true local
        // diameters. On a liner strip that range covers the whole liner plus a pad each side,
        // so the liner's edges and its neighbours' slivers appear naturally.
        //
        // The blank write-in template is the exception (`linerSpanBlank` in the composer): the
        // liner's own span is drawn as STARTING geometry only — its two vertical end faces and
        // the neighbour stock outboard of them, with no fill and no surface lines across the
        // middle, which stays clear paper for the hand-drawn liner and cuts. `started` is the
        // blank template here; a non-blank export of an empty record keeps the full profile.
        val blankLiner = if (started) strip as? UndercutStrip.LinerStrip else null
        val profileBands =
            if (blankLiner == null) listOf(drawStartMm to drawEndMm)
            else listOf(
                drawStartMm to blankLiner.linerStartMm.coerceIn(drawStartMm, drawEndMm),
                blankLiner.linerEndMm.coerceIn(drawStartMm, drawEndMm) to drawEndMm,
            )
        val linerMidMm = (linerAftMm + linerFwdMm) / 2f
        var surfaceLinesAtLinerMid = 0
        profileBands.forEach { (bandStartMm, bandEndMm) ->
            components.forEach { comp ->
                val dia = when (comp) {
                    is ResolvedBody -> comp.diaMm
                    is ResolvedLiner -> comp.odMm
                    else -> return@forEach
                }
                val a = maxOf(comp.startMmPhysical, bandStartMm)
                val b = minOf(comp.endMmPhysical, bandEndMm)
                if (b <= a) return@forEach
                val r = rAt(dia)
                svg.line(xAt(a), cy - r, xAt(b), cy - r)
                svg.line(xAt(a), cy + r, xAt(b), cy + r)
                if (a < linerMidMm && b > linerMidMm) surfaceLinesAtLinerMid += 2
                // End caps only where the real edge falls inside the band.
                listOf(comp.startMmPhysical, comp.endMmPhysical).forEach { edge ->
                    if (edge > bandStartMm + 0.001f && edge < bandEndMm - 0.001f) {
                        svg.line(xAt(edge), cy - r, xAt(edge), cy + r, w = 0.9f)
                    }
                }
            }
        }
        // The blank template's end faces: full drawn height at the liner's OD, the only vertical
        // lines at those stations now the surface lines are gone.
        blankLiner?.let { bl ->
            val faceAftMm = bl.linerStartMm.coerceIn(drawStartMm, drawEndMm)
            val faceFwdMm = bl.linerEndMm.coerceIn(drawStartMm, drawEndMm)
            val rFace = rAt(maxOuterDiaOver(segs, faceAftMm, faceFwdMm))
            listOf(faceAftMm, faceFwdMm).forEach { mm ->
                svg.line(xAt(mm), cy - rFace, xAt(mm), cy + rFace, w = 1.2f)
            }
        }
        // Cut ends — amplitude capped exactly as `drawUndercutWindowEnd` caps it, so the preview
        // shows the real lobe size rather than an ear the PDF no longer draws.
        listOf(drawStartMm to true, drawEndMm to false).forEach { (atMm, eyeAtTop) ->
            val r = rAt(outerDiaAt(segs, atMm))
            svg.breakEdge(xAt(atMm), cy - r, cy + r, minOf(r * 0.6f, UNDERCUT_BREAK_AMP_MAX_PT), eyeAtTop)
        }

        // Notches — white void from surface to floor, mirrored, then shoulders + floor.
        // Regions come from the TRUE floor; the drawn floor is depth-exaggerated against the
        // sheet's deepest cut and the void overdraws the surface stroke, both mirroring the
        // composer.
        spans.forEach { s ->
            val u = undercuts.first { it.id == s.id }
            val minSurface = minOuterDiaOver(segs, s.startMm, s.endMm)
            val floorDia = effectiveNotchDiaMm(u.diaMm, minSurface)
            val rFloor = rAt(
                normalizedNotchFloorDiaMm(u.diaMm, minSurface, deepestDepthMm, exaggerationFrac),
            )
            val od = 0.8f
            notchProfiles(segs, s.startMm, s.endMm, floorDia).forEach { np ->
                listOf(-1f, 1f).forEach { sign ->
                    val pts = np.surface.map { sp -> xAt(sp.xMm) to cy + sign * (rAt(sp.diaMm) + od) } +
                        listOf(xAt(np.endMm) to cy + sign * rFloor, xAt(np.startMm) to cy + sign * rFloor)
                    svg.poly(pts, fill = "white")
                }
                val r0 = rAt(np.surface.first().diaMm)
                val r1 = rAt(np.surface.last().diaMm)
                listOf(-1f, 1f).forEach { sign ->
                    svg.line(xAt(np.startMm), cy + sign * r0, xAt(np.startMm), cy + sign * rFloor)
                    svg.line(xAt(np.startMm), cy + sign * rFloor, xAt(np.endMm), cy + sign * rFloor)
                    svg.line(xAt(np.endMm), cy + sign * rFloor, xAt(np.endMm), cy + sign * r1)
                }
            }
        }

        // Ø callouts: leader from each notch floor down to the value.
        if (plan != null) {
            val floorBottomY = undercuts.associate { u ->
                val s = clampedById.getValue(u.id)
                val floor = if (s.isEmpty) 0f else normalizedNotchFloorDiaMm(
                    u.diaMm, minOuterDiaOver(segs, s.startMm, s.endMm),
                    deepestDepthMm, exaggerationFrac,
                )
                u.id to cy + rAt(floor)
            }
            val placed = plan.finish(
                row0Top = inner.cylBottom + WEAR_STRIP_LABEL_HEADROOM_PT,
                labelTextHeight = textH,
                rowGap = rowGap,
                surfaceYAt = { i -> floorBottomY[plan.stations[i].key] ?: cy },
                leaderStartGap = 1f,
            )
            placed.forEach { p ->
                for (i in 0 until p.leader.size - 1) {
                    svg.line(p.leader[i].x, p.leader[i].y, p.leader[i + 1].x, p.leader[i + 1].y, w = 0.7f)
                }
                svg.text(p.labelCx, p.labelTopY + textH * 0.8f, p.label)
            }
        }

        // Rails: chain, then the total above it. The chain's outer witness lines land on the
        // strip's chain datums — a liner's own edges, not the zoom pad. A started strip keeps
        // ONLY those datum bars: the band above the cylinder is the machinist's to draw in.
        if (started) {
            listOf(strip.chainStartMm, strip.chainEndMm).forEach { mm ->
                svg.line(xAt(mm), inner.cylTop - 3f, xAt(mm), inner.chainRailY, w = 0.6f)
            }
        }
        chain.forEach { svg.railSpan(it, inner.cylTop - 3f, inner.chainRailY, labelAbove = railPlan.aboveRows > 0) }
        totalSpan?.let { ts ->
            layoutWearStripRail(listOf(ts), xAtStripMm = ::xAt, labelWidthPt = ::labelW)
                .forEach { svg.railSpan(it, inner.chainRailY, inner.totalRailY, labelAbove = true) }
        }

        // Title: "<liner name> — <dist> FROM … S.E.T.", or the anchor alone on bare stock. A
        // started strip prints the write-in form instead: name, a rule for the distance, and
        // both directions for the machinist to circle one.
        val titleY = stripBottom - 2f
        if (started) {
            val stem = linerTitle?.let { "$it — " } ?: ""
            svg.text(stripLeft, titleY, stem, size = titleH, anchor = "start")
            val ruleX0 = stripLeft + labelW(stem) * (titleH / textH)
            svg.line(ruleX0, titleY + 1f, ruleX0 + BLANK_DIM_GAP_PT, titleY + 1f, w = 0.7f)
            svg.text(ruleX0 + BLANK_DIM_GAP_PT + 4f, titleY, WEAR_BLANK_ANCHOR_SUFFIX, size = titleH, anchor = "start")
        } else {
            val anchor = undercutAnchorFor(spans.first().startMm, spans.last().endMm, aftSetMm, fwdSetMm)
            val title = buildUndercutStripTitle(linerTitle, buildUndercutAnchorLabel(anchor, unit))
            if (anchor.alignRight) svg.text(stripRight, titleY, title, size = titleH, anchor = "end")
            else svg.text(stripLeft, titleY, title, size = titleH, anchor = "start")
        }

        val chainLen = railSpans.sumOf { (it.endMm - it.startMm).toDouble() }.toFloat()
        // All draw coordinates are absolute page-space, so the viewBox must span from 0 to past
        // stripRight/stripBottom — sizing it to the strip's width alone would clip the frame's
        // right border and the FWD break edge. The whole 792 × 612 page satisfies that and
        // additionally shows the strip in its real place on the sheet.
        return StripResult(
            svg = svg.wrap(PAGE_W, PAGE_H),
            strip = strip,
            chainCoversStrip = started ||
                kotlin.math.abs(chainLen - (strip.chainEndMm - strip.chainStartMm)) < 0.01f,
            railInsideChain = railSpans.all {
                it.startMm >= strip.chainStartMm - 0.01f && it.endMm <= strip.chainEndMm + 0.01f
            },
            compressed = plan?.compressed ?: false,
            hasTotal = totalSpan != null,
            fallbackRowsAbove = railPlan.aboveRows,
            minPadPt = minOf(
                xAt(strip.chainStartMm) - xAt(drawStartMm),
                xAt(drawEndMm) - xAt(strip.chainEndMm),
            ),
            surfaceLinesAtLinerMid = surfaceLinesAtLinerMid,
        )
    }

    @Test
    fun `render preview SVGs`() {
        val outDir = File("build/reports/undercut-preview").apply { mkdirs() }
        // Clear previous runs: the file set changes as scenarios are renamed, and a stale SVG
        // would both mislead a reviewer and break the count assertion below.
        outDir.listFiles()?.forEach { if (it.name.endsWith(".svg")) it.delete() }

        // Every scenario is a whole strips-own-page sheet: no main profile, no OAL line, one
        // orientation row under the header, and the strips filling the rest of the band.

        // A) LINER strip, all three cuts measured — the sketch case: the WHOLE liner is drawn
        //    with its true edges, body slivers and break edges beyond, the chained rail
        //    anchored on the liner's edges (extended to the third cut, which overhangs the
        //    FWD edge), the cluster total above, and three Ø values below.
        val a = renderStrip(linerUndercuts(thirdDiaMm = 170f, secondDiaMm = 198f))
        File(outDir, "a-liner-strip-fullwidth.svg").writeText(a.svg)
        val aLiner = a.strip as UndercutStrip.LinerStrip
        assertEquals("chain must anchor on the liner's AFT edge", linerAftMm, aLiner.chainStartMm, 1e-3f)
        assertEquals("chain must follow the overhanging cut's shoulder", 1030f, aLiner.chainEndMm, 1e-3f)
        assertTrue("the whole liner plus a pad must be drawn", aLiner.drawStartMm < linerAftMm)
        assertTrue("chain must cover the liner exactly", a.chainCoversStrip)
        assertTrue("the pad outside the chain is never dimensioned", a.railInsideChain)
        assertTrue("three cuts must produce a total span", a.hasTotal)
        assertFalse("the sketch case must not compress its callouts", a.compressed)
        // A full-width strip of this liner already prints its 1 in pad wide enough, so the pt
        // floor leaves it alone — the mm pad and the drawn pad still agree here.
        assertTrue("break edges must not sit against the liner faces", a.minPadPt >= UNDERCUT_STRIP_MIN_PAD_PT)

        // B) The middle cut has no measured Ø yet: its notch and its rail dimension stay, its
        //    Ø callout does NOT print — the placed-but-empty rule.
        val b = renderStrip(linerUndercuts(thirdDiaMm = 170f, secondDiaMm = 0f))
        File(outDir, "b-liner-strip-unmeasured-middle.svg").writeText(b.svg)
        assertTrue(b.chainCoversStrip)
        assertFalse(b.compressed)

        // C) The same liner strip as the LEFT cell of a 2-up GRID page — a grid row now owns
        //    the full band height too, so a cell is half-width but full-height.
        val c = renderStrip(
            linerUndercuts(thirdDiaMm = 170f, secondDiaMm = 198f),
            stripLeft = 36f, stripRight = 385f, stripTop = BAND_TOP, stripBottom = BAND_BOTTOM,
        )
        File(outDir, "c-liner-strip-gridcell.svg").writeText(c.svg)
        assertTrue(c.chainCoversStrip)
        assertTrue(c.railInsideChain)
        // Half the width halves the scale, so the mm pad alone would print at ~17 pt — the cell
        // is where the pt floor earns its keep.
        assertTrue("a grid cell must read with the same end air", c.minPadPt >= UNDERCUT_STRIP_MIN_PAD_PT - 1e-2f)

        // D) Bare-shaft cuts in the RIGHT grid cell: no liner to draw, so the padded cluster
        //    window is the strip and the chain runs window edge → window edge.
        val d = renderStrip(
            bareShaftUndercuts(),
            stripLeft = 407f, stripRight = 756f, stripTop = BAND_TOP, stripBottom = BAND_BOTTOM,
        )
        File(outDir, "d-bareshaft-freestrip-gridcell.svg").writeText(d.svg)
        val dFree = d.strip as UndercutStrip.FreeStrip
        assertEquals("a free strip dimensions its whole window", dFree.drawStartMm, dFree.chainStartMm, 1e-3f)
        assertEquals(dFree.drawEndMm, dFree.chainEndMm, 1e-3f)
        assertTrue(d.chainCoversStrip)
        assertTrue(d.hasTotal)
        // The window edge IS the chain datum here, so all of the drawn end air comes from the pt
        // floor — the break edge no longer lands on the outer witness line.
        assertTrue("a free strip's window edge must clear its break", d.minPadPt >= UNDERCUT_STRIP_MIN_PAD_PT - 1e-2f)

        // E) STARTED strip, blank write-in TEMPLATE: starting geometry only — the liner's two
        //    end faces, the neighbour slivers outboard of them and the break edges, with its
        //    span left as clear paper (no fill, no top/bottom surface lines). No notches, no
        //    rail spans (only the two chain-datum bars), a write-in anchor title. Everything
        //    else is air the machinist sketches the liner and its sections into. (A non-blank
        //    export of an empty record keeps the fully drawn started strip — same layout,
        //    profile intact.)
        val e = renderStrip(emptyList())
        File(outDir, "e-started-liner-strip-blank.svg").writeText(e.svg)
        val eLiner = e.strip as UndercutStrip.LinerStrip
        assertEquals("a started strip covers the whole liner", linerAftMm, eLiner.chainStartMm, 1e-3f)
        assertEquals(linerFwdMm, eLiner.chainEndMm, 1e-3f)
        assertTrue("nothing is recorded on a started strip", eLiner.undercutIds.isEmpty())
        assertFalse("no cuts ⇒ no total rail", e.hasTotal)
        assertEquals(
            "the blank template's liner span must be clear paper — no surface line crosses it",
            0, e.surfaceLinesAtLinerMid,
        )
        // …and the contrast: a strip that is not the blank template draws the liner (and the
        // body under it) right across that station.
        assertTrue("a real strip still draws the liner surface", a.surfaceLinesAtLinerMid > 0)

        // F) SINGLE cut in the liner, half-width grid cell: no total rail, and the narrow AFT
        //    pad's value can't seat in its break — with the chain the only label level, that
        //    fallback stacks ABOVE the line (on-device report: it read as orphaned pushed
        //    underneath). Contrast: A's chain sits under a total, so its fallbacks stay below.
        val f = renderStrip(
            listOf(Undercut(id = "s1", startFromAftMm = 630f, lengthMm = 320f, diaMm = 176f)),
            stripLeft = 36f, stripRight = 385f, stripTop = BAND_TOP, stripBottom = BAND_BOTTOM,
        )
        File(outDir, "f-liner-strip-singlecut-gridcell.svg").writeText(f.svg)
        assertFalse("a single cut prints no total span", f.hasTotal)
        assertTrue("the narrow pad's value must stack above the line", f.fallbackRowsAbove >= 1)
        assertEquals("fallbacks under a total rail stay below", 0, a.fallbackRowsAbove)

        assertNotNull(outDir.listFiles())
        assertEquals(6, outDir.listFiles()!!.count { it.name.endsWith(".svg") })
    }
}
