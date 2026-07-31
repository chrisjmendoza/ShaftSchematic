package com.android.shaftschematic.pdf

import com.android.shaftschematic.geom.SurfaceSeg
import com.android.shaftschematic.geom.UndercutSpanMm
import com.android.shaftschematic.geom.clampUndercutSpan
import com.android.shaftschematic.geom.clusterUndercuts
import com.android.shaftschematic.geom.effectiveNotchDiaMm
import com.android.shaftschematic.geom.maxOuterDiaOver
import com.android.shaftschematic.geom.minOuterDiaOver
import com.android.shaftschematic.geom.notchProfiles
import com.android.shaftschematic.geom.outerDiaAt
import com.android.shaftschematic.geom.planDiaCallouts
import com.android.shaftschematic.model.Undercut
import com.android.shaftschematic.ui.resolved.ResolvedBody
import com.android.shaftschematic.ui.resolved.ResolvedComponentType
import com.android.shaftschematic.ui.resolved.ResolvedComponentSource
import com.android.shaftschematic.ui.resolved.ResolvedLiner
import com.android.shaftschematic.ui.resolved.surfaceSegsFrom
import com.android.shaftschematic.util.UnitSystem
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
 * `clusterUndercuts`, `computeWearStripHorizontalLayout`,
 * `computeUndercutStripInnerLayout`, `buildUndercutRailSpans`/`buildUndercutTotalSpan`/
 * `layoutWearStripRail`, `buildUndercutDiaStations`/`planDiaCallouts`, `undercutAnchorFor` —
 * and writes what `UndercutPdfComposer` would draw as SVG into
 * `app/build/reports/undercut-preview/`. Only text width is approximated (no
 * `Paint.measureText` on the JVM; a fixed per-char factor stands in), which can shift label
 * spreads by a point or two but exercises identical placement rules.
 *
 * Doubles as a smoke test: the representative scenario must read as ONE cluster, its chain
 * must cover the window exactly, and its Ø callouts must fit uncompressed.
 */
class UndercutStripSvgPreviewTest {

    private val unit = UnitSystem.INCHES
    private val textH = 8f
    private val titleH = 9f
    private val rowGap = 3f
    private val minGap = 5f

    /** Stand-in for Paint.measureText at 8pt sans — close enough for preview spreads. */
    private fun labelW(s: String): Float = s.length * textH * 0.58f

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

    private fun scenarioUndercuts(thirdDiaMm: Float, secondDiaMm: Float) = listOf(
        Undercut(id = "u1", startFromAftMm = 700f, lengthMm = 60f, diaMm = 200f),
        Undercut(id = "u2", startFromAftMm = 820f, lengthMm = 60f, diaMm = secondDiaMm),
        // Crosses the liner's FWD edge at 1000 mm: the shoulder is taller on the liner side.
        Undercut(id = "u3", startFromAftMm = 960f, lengthMm = 70f, diaMm = thirdDiaMm),
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

    /** One arrowed rail span with its value seated in a break, mirroring `drawUndercutRail`. */
    private fun Svg.railSpan(s: WearRailSpanLayout, witnessBottomY: Float, railY: Float, labelAbove: Boolean) {
        val arrow = 4f
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
            if (labelAbove) text(s.labelCxPt, railY - 3f, s.label)
            else text(s.labelCxPt, railY + 3f + textH, s.label)
        }
        val dl = if (s.arrowInward) 1f else -1f
        val dr = if (s.arrowInward) -1f else 1f
        line(s.x0Pt, railY, s.x0Pt + dl * arrow, railY - arrow * 0.5f, w = 0.6f)
        line(s.x0Pt, railY, s.x0Pt + dl * arrow, railY + arrow * 0.5f, w = 0.6f)
        line(s.x1Pt, railY, s.x1Pt + dr * arrow, railY - arrow * 0.5f, w = 0.6f)
        line(s.x1Pt, railY, s.x1Pt + dr * arrow, railY + arrow * 0.5f, w = 0.6f)
    }

    // ── The preview ───────────────────────────────────────────────────────────

    private class StripResult(val svg: String, val chainCoversWindow: Boolean, val compressed: Boolean, val hasTotal: Boolean)

    private fun renderStrip(
        undercuts: List<Undercut>,
        stripLeft: Float = 36f,
        stripRight: Float = 756f,
        stripTop: Float = 16f,
        stripBottom: Float = 200f,
    ): StripResult {
        val segs: List<SurfaceSeg> = surfaceSegsFrom(components)
        val clampedById = undercuts.associate { it.id to clampUndercutSpan(it.startFromAftMm, it.lengthMm, oalMm) }
        val liveSpans = undercuts.mapNotNull { u ->
            val s = clampedById.getValue(u.id)
            if (s.isEmpty) null else UndercutSpanMm(u.id, s.startMm, s.endMm)
        }
        val windows = clusterUndercuts(liveSpans, oalMm)
        assertEquals("the scenario must read as one zoom window", 1, windows.size)
        val window = windows[0]
        val spans = liveSpans.filter { it.id in window.undercutIds }.sortedBy { it.startMm }

        val h = computeWearStripHorizontalLayout(
            stripLeft, stripRight, window.lengthMm, stubWidthPt = UNDERCUT_STRIP_EDGE_INSET_PT,
        )
        fun xAt(mm: Float) = h.linerLeftPt + (mm - window.startMm) * h.ptPerMm

        val stations = buildUndercutDiaStations(undercuts, clampedById, ::xAt, unit, ::labelW)
        val plan = if (stations.isEmpty()) null else planDiaCallouts(stations, stripLeft + 2f, stripRight - 2f, minGap)
        val diaBand = plan?.let { it.labelsHeightPt(textH, rowGap) + 2f } ?: 0f

        val railSpans = buildUndercutRailSpans(window, spans, unit)
        val totalSpan = buildUndercutTotalSpan(spans, unit)
        val inner = computeUndercutStripInnerLayout(
            stripTop, stripBottom, titleHeightPt = titleH, hasTotalRail = totalSpan != null, diaBandPt = diaBand,
        )

        val cy = (inner.cylTop + inner.cylBottom) / 2f
        val rCap = (inner.cylBottom - inner.cylTop) / 2f
        val windowMaxDia = maxOuterDiaOver(segs, window.startMm, window.endMm)
        fun rAt(diaMm: Float) = (rCap * (diaMm / windowMaxDia)).coerceIn(0f, rCap)

        val svg = Svg()
        svg.rect(stripLeft, stripTop, stripRight - stripLeft, stripBottom - stripTop, stroke = "#ddd", sw = 0.5f)

        // Window profile: every component clipped to the window, true local diameters.
        components.forEach { comp ->
            val dia = when (comp) {
                is ResolvedBody -> comp.diaMm
                is ResolvedLiner -> comp.odMm
                else -> return@forEach
            }
            val a = maxOf(comp.startMmPhysical, window.startMm)
            val b = minOf(comp.endMmPhysical, window.endMm)
            if (b <= a) return@forEach
            val r = rAt(dia)
            svg.line(xAt(a), cy - r, xAt(b), cy - r)
            svg.line(xAt(a), cy + r, xAt(b), cy + r)
            // End caps only where the real edge falls inside the window.
            listOf(comp.startMmPhysical, comp.endMmPhysical).forEach { edge ->
                if (edge > window.startMm + 0.001f && edge < window.endMm - 0.001f) {
                    svg.line(xAt(edge), cy - r, xAt(edge), cy + r, w = 0.9f)
                }
            }
        }
        // Cut ends.
        listOf(window.startMm to true, window.endMm to false).forEach { (atMm, eyeAtTop) ->
            val r = rAt(outerDiaAt(segs, atMm))
            svg.breakEdge(xAt(atMm), cy - r, cy + r, r * 0.6f, eyeAtTop)
        }

        // Notches — white void from surface to floor, mirrored, then shoulders + floor.
        spans.forEach { s ->
            val u = undercuts.first { it.id == s.id }
            val floorDia = effectiveNotchDiaMm(u.diaMm, minOuterDiaOver(segs, s.startMm, s.endMm))
            val rFloor = rAt(floorDia)
            notchProfiles(segs, s.startMm, s.endMm, floorDia).forEach { np ->
                listOf(-1f, 1f).forEach { sign ->
                    val pts = np.surface.map { sp -> xAt(sp.xMm) to cy + sign * rAt(sp.diaMm) } +
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
                val floor = if (s.isEmpty) 0f else effectiveNotchDiaMm(u.diaMm, minOuterDiaOver(segs, s.startMm, s.endMm))
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

        // Rails: chain, then the cluster total above it.
        val chain = layoutWearStripRail(railSpans, xAtStripMm = ::xAt, labelWidthPt = ::labelW)
        chain.forEach { svg.railSpan(it, inner.cylTop - 3f, inner.chainRailY, labelAbove = false) }
        totalSpan?.let { ts ->
            layoutWearStripRail(listOf(ts), xAtStripMm = ::xAt, labelWidthPt = ::labelW)
                .forEach { svg.railSpan(it, inner.chainRailY, inner.totalRailY, labelAbove = true) }
        }

        // Anchor title.
        val anchor = undercutAnchorFor(spans.first().startMm, spans.last().endMm, aftSetMm, fwdSetMm)
        val title = buildUndercutAnchorLabel(anchor, unit)
        if (anchor.alignRight) svg.text(stripRight, stripBottom - 2f, title, size = titleH, anchor = "end")
        else svg.text(stripLeft, stripBottom - 2f, title, size = titleH, anchor = "start")

        val chainLen = railSpans.sumOf { (it.endMm - it.startMm).toDouble() }.toFloat()
        // All draw coordinates are absolute page-space (origin 0, strip frame at
        // stripLeft/stripTop), so the viewBox must span from 0 to past stripRight/stripBottom
        // — sizing it to the strip's width alone would clip the frame's right border and the
        // FWD break edge.
        return StripResult(
            svg = svg.wrap(stripRight + 20f, stripBottom + 12f),
            chainCoversWindow = kotlin.math.abs(chainLen - window.lengthMm) < 0.01f,
            compressed = plan?.compressed ?: false,
            hasTotal = totalSpan != null,
        )
    }

    @Test
    fun `render preview SVGs`() {
        val outDir = File("build/reports/undercut-preview").apply { mkdirs() }

        // A) All three cuts measured — the sketch case: chained rail + total span + three Ø
        //    values, the third notch crossing the liner's FWD edge.
        val a = renderStrip(scenarioUndercuts(thirdDiaMm = 170f, secondDiaMm = 198f))
        File(outDir, "a-cluster-fullwidth.svg").writeText(a.svg)
        assertTrue("chain must cover the window exactly", a.chainCoversWindow)
        assertTrue("three cuts must produce a cluster total span", a.hasTotal)
        assertFalse("the sketch case must not compress its callouts", a.compressed)

        // B) The middle cut has no measured Ø yet: its notch and its rail dimension stay, its
        //    Ø callout does NOT print — the placed-but-empty rule.
        val b = renderStrip(scenarioUndercuts(thirdDiaMm = 170f, secondDiaMm = 0f))
        File(outDir, "b-unmeasured-middle.svg").writeText(b.svg)
        assertTrue(b.chainCoversWindow)
        assertFalse(b.compressed)

        // C) The same cluster in a half-width GRID cell.
        val cCell = renderStrip(
            scenarioUndercuts(thirdDiaMm = 170f, secondDiaMm = 198f),
            stripLeft = 36f, stripRight = 385f, stripTop = 16f, stripBottom = 150f,
        )
        File(outDir, "c-cluster-gridcell.svg").writeText(cCell.svg)
        assertTrue(cCell.chainCoversWindow)

        assertNotNull(outDir.listFiles())
        assertEquals(3, outDir.listFiles()!!.count { it.name.endsWith(".svg") })
    }
}
