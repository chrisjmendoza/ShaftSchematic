package com.android.shaftschematic.pdf

import com.android.shaftschematic.geom.computeOalWindow
import com.android.shaftschematic.geom.computeSetPositionsInMeasureSpace
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.Threads
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Same-math SVG previews of the wear sheet's multi-component detail strips, for visual (markup)
 * review without an on-device round trip — the `WearDiaCalloutSvgPreviewTest` pattern. Drives the
 * REAL layout code (`collectWearStripWindows`, `sharedWearStripWindowPtPerMm`,
 * `computeWearStripWindowLayout`, `WearStripWindow.xAt`, `wearStripGapProfile`,
 * `computeWearStripInnerLayout`, `computeWearStripRadii`, `wearStripEndStyle`,
 * `wearStripEndThreadDiaMm`, `wearStripClusters`) and writes the silhouettes `WearPdfComposer`
 * would draw into `app/build/reports/wear-strip-preview/`.
 *
 * The preview mirrors `drawWearStripWindow`'s three "how a strip reads" rules:
 * - **Window ends** follow [wearStripEndStyle] — a BREAK stub (the shaft genuinely continues), a
 *   THREAD_END stub drawn at [wearStripEndThreadDiaMm] with a flat outer edge + thread hatch, or
 *   FLAT: no stub at all, just a full-weight end cap where the shaft physically ends.
 * - **Compressed gaps** lead in from each component edge with [WEAR_STRIP_BREAK_LEAD_PT] of the
 *   gap's TRUE outline (`outerDiaMmAt` half a millimetre inside the gap, falling back to the
 *   adjacent component's edge Ø) before that side's break edge.
 * - **Titles are per attachment cluster** ([wearStripClusters]): a taper-bearing cluster prints
 *   names only ([wearStripClusterShowsAnchor]), a lone liner/body keeps its from-SET anchor, and
 *   a multi-cluster window centres each label under its own cluster's drawn span.
 *
 * Doubles as a geometry regression: every drawn segment must stay inside its cell, the
 * near/far taper cases must pick different gap modes, a FLAT end must draw nothing past the
 * window edge, and a liner-only window must land exactly where the legacy per-liner strip did.
 */
class WearStripWindowSvgPreviewTest {

    private val cellLeft = 36f
    private val cellRight = 396f
    private val cellTop = 10f
    private val cellBottom = 150f

    /** Fixed hatch pitch inside a thread-end stub — mirrors the composer's private constant. */
    private val threadHatchPitchPt = 6f

    private data class Svg(val sb: StringBuilder = StringBuilder()) {
        fun line(x0: Float, y0: Float, x1: Float, y1: Float, w: Float = 1.2f, color: String = "black", dash: String? = null) {
            sb.append("""<line x1="$x0" y1="$y0" x2="$x1" y2="$y1" stroke="$color" stroke-width="$w"""")
            if (dash != null) sb.append(""" stroke-dasharray="$dash"""")
            sb.append("/>\n")
        }
        fun rect(x: Float, y: Float, w: Float, h: Float, stroke: String = "black", fill: String = "none", sw: Float = 1.2f) {
            sb.append("""<rect x="$x" y="$y" width="$w" height="$h" stroke="$stroke" fill="$fill" stroke-width="$sw"/>""").append('\n')
        }
        fun text(x: Float, y: Float, s: String, size: Float = 9f, anchor: String = "start", color: String = "black") {
            sb.append(
                """<text x="$x" y="$y" font-size="$size" fill="$color" """ +
                    """font-family="Helvetica, Arial, sans-serif" text-anchor="$anchor">$s</text>""",
            ).append('\n')
        }
        fun wrap(w: Float, h: Float): String =
            "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 $w $h\" width=\"${w * 1.6f}\" height=\"${h * 1.6f}\" style=\"background:white\">\n$sb</svg>\n"
    }

    private fun esc(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    /** What the strip drew, so the caller can assert on its geometry as well as look at it. */
    private data class Drawn(
        val svg: String,
        /** Extremes of every drawn line — nothing may escape the cell. */
        val minX: Float,
        val maxX: Float,
        /** The window's own run, without the neighbor stubs. */
        val leftPt: Float,
        val rightPt: Float,
        val stubWidthPt: Float,
        /** x of each compressed gap's two break edges (already stood off by the lead-in). */
        val gapBreakXs: List<Float>,
        /** One title per attachment cluster, AFT→FWD, exactly as drawn. */
        val titles: List<String>,
    )

    /** Faithful to drawWearStripWindow's silhouette geometry (rails/values omitted). */
    private fun windowSvg(
        spec: ShaftSpec,
        window: WearStripWindow,
        ptPerMm: Float,
        names: Map<String, String>,
        caption: String,
    ): Drawn {
        val svg = Svg()
        val h = computeWearStripWindowLayout(cellLeft, cellRight, window.drawnWidthPt(ptPerMm), ptPerMm)
        fun xAt(mm: Float) = window.xAt(mm, h.linerLeftPt, ptPerMm)

        val inner = computeWearStripInnerLayout(cellTop, cellBottom, titleHeightPt = 9f)
        val cy = (inner.cylTop + inner.cylBottom) / 2f
        val rCap = (inner.cylBottom - inner.cylTop) / 2f
        val refDia = window.refDiaMm
        fun rOf(diaMm: Float) = (rCap * (diaMm / refDia)).coerceIn(0f, rCap)
        val comps = window.components
        val aftDia = neighborDiaMmAtAft(spec, window.startMm) ?: comps.first().aftDiaMm
        val fwdDia = neighborDiaMmAtFwd(spec, window.endMm) ?: comps.last().fwdDiaMm
        val radii = computeWearStripRadii(refDia, aftDia, fwdDia, rCap)

        // End styles decided exactly as the composer decides them, BEFORE any drawing: they say
        // whether each side has a stub at all, which the x-extent seeding below depends on.
        val aftStyle = wearStripEndStyle(spec, window.startMm, aftSide = true)
        val fwdStyle = wearStripEndStyle(spec, window.endMm, aftSide = false)
        val stubLeftX = h.linerLeftPt - h.stubWidthPt
        val stubRightX = h.linerRightPt + h.stubWidthPt

        // Seeded PER SIDE: a FLAT end draws no stub, so that side must not claim the stub's
        // width — seeding it unconditionally would hide a stub drawn where none belongs.
        var minX = if (aftStyle == WearStripEndStyle.FLAT) h.linerLeftPt else stubLeftX
        var maxX = if (fwdStyle == WearStripEndStyle.FLAT) h.linerRightPt else stubRightX
        fun seen(x: Float) { if (x < minX) minX = x; if (x > maxX) maxX = x }

        /** Symbolic 45° thread hatch across a stub rect, clipped by hand (no SVG clipPath). */
        fun hatch(x0: Float, top: Float, x1: Float, bot: Float) {
            if (x1 <= x0 || bot <= top) return
            val hgt = bot - top
            var hx = x0 - hgt
            while (hx <= x1) {
                val ax = maxOf(hx, x0)
                val bx = minOf(hx + hgt, x1)
                if (bx > ax) svg.line(ax, bot - (ax - hx), bx, bot - (bx - hx), w = 0.6f, color = "#888")
                hx += threadHatchPitchPt
            }
        }

        svg.rect(cellLeft, cellTop, cellRight - cellLeft, cellBottom - cellTop, stroke = "#bbb", sw = 0.5f)
        svg.text(cellLeft, cellTop + 8f, esc(caption), size = 7f, color = "#888")

        val gapBreakXs = mutableListOf<Float>()

        window.segments.forEachIndexed { i, seg ->
            val x0 = xAt(seg.startMm); val x1 = xAt(seg.endMm)
            seen(x0); seen(x1)
            when (seg) {
                is WearStripComponentSeg -> {
                    val comp = seg.component
                    val rA = rOf(comp.aftDiaMm); val rF = rOf(comp.fwdDiaMm)
                    val color = when (comp.kind) {
                        WearStripComponentKind.LINER -> "black"
                        WearStripComponentKind.TAPER -> "#06c"
                        WearStripComponentKind.BODY -> "#080"
                    }
                    svg.line(x0, cy - rA, x1, cy - rF, color = color)
                    svg.line(x0, cy + rA, x1, cy + rF, color = color)
                    svg.line(x0, cy - rA, x0, cy + rA, w = 0.7f, color = color)
                    svg.line(x1, cy - rF, x1, cy + rF, w = 0.7f, color = color)
                }
                is WearStripGapSeg -> {
                    val diaL = (window.segments.getOrNull(i - 1) as? WearStripComponentSeg)?.component?.fwdDiaMm ?: aftDia
                    val diaR = (window.segments.getOrNull(i + 1) as? WearStripComponentSeg)?.component?.aftDiaMm ?: fwdDia
                    if (seg.trueScale) {
                        val verts = wearStripGapProfile(spec, seg.startMm, seg.endMm,
                            samples = ((x1 - x0) / 2f).toInt().coerceIn(2, 64))
                        if (verts.isEmpty()) {
                            svg.line(x0, cy - rOf(diaL), x1, cy - rOf(diaR), color = "#c60")
                            svg.line(x0, cy + rOf(diaL), x1, cy + rOf(diaR), color = "#c60")
                        } else {
                            var px = x0; var pr = rOf(verts.first().diaMm)
                            verts.drop(1).forEach { v ->
                                val vx = xAt(v.mm); val vr = rOf(v.diaMm)
                                svg.line(px, cy - pr, vx, cy - vr, color = "#c60")
                                svg.line(px, cy + pr, vx, cy + vr, color = "#c60")
                                px = vx; pr = vr
                            }
                        }
                    } else {
                        // Each break stands off its component by a lead-in of the shaft as it
                        // ACTUALLY is just inside the gap, so the strip reads "edge cap → a bit
                        // of real shaft → break → removed length → break → real shaft".
                        val rGapL = rOf(outerDiaMmAt(spec, seg.startMm + 0.5f).takeIf { it > 0f } ?: diaL)
                        val rGapR = rOf(outerDiaMmAt(spec, seg.endMm - 0.5f).takeIf { it > 0f } ?: diaR)
                        val lead = WEAR_STRIP_BREAK_LEAD_PT
                        svg.line(x0, cy - rGapL, x0 + lead, cy - rGapL, color = "#c60")
                        svg.line(x0, cy + rGapL, x0 + lead, cy + rGapL, color = "#c60")
                        svg.line(x1 - lead, cy - rGapR, x1, cy - rGapR, color = "#c60")
                        svg.line(x1 - lead, cy + rGapR, x1, cy + rGapR, color = "#c60")
                        // The two dashed verticals stand in for drawBreakEdge's S glyphs.
                        svg.line(x0 + lead, cy - rGapL, x0 + lead, cy + rGapL, color = "#c00", dash = "3,2")
                        svg.line(x1 - lead, cy - rGapR, x1 - lead, cy + rGapR, color = "#c00", dash = "3,2")
                        gapBreakXs += x0 + lead
                        gapBreakXs += x1 - lead
                    }
                }
            }
        }

        // Window ends — a stub's S-break claims "the shaft continues past here", so it is drawn
        // only where it does; a threaded remainder draws whole (flat outer edge + hatch) and an
        // end with nothing beyond it gets no stub at all, only a full-weight end cap.
        when (aftStyle) {
            WearStripEndStyle.BREAK -> {
                val r = radii.aftRPt
                svg.line(stubLeftX, cy - r, h.linerLeftPt, cy - r)
                svg.line(stubLeftX, cy + r, h.linerLeftPt, cy + r)
                svg.line(stubLeftX, cy - r, stubLeftX, cy + r, color = "#c00", dash = "3,2")
            }
            WearStripEndStyle.THREAD_END -> {
                val threadDia = wearStripEndThreadDiaMm(spec, window.startMm, aftSide = true)
                val r = if (threadDia > 0f) rOf(threadDia) else radii.aftRPt
                svg.line(stubLeftX, cy - r, h.linerLeftPt, cy - r)
                svg.line(stubLeftX, cy + r, h.linerLeftPt, cy + r)
                svg.line(stubLeftX, cy - r, stubLeftX, cy + r)
                hatch(stubLeftX, cy - r, h.linerLeftPt, cy + r)
            }
            WearStripEndStyle.FLAT -> {
                val r = rOf(comps.first().aftDiaMm)
                svg.line(h.linerLeftPt, cy - r, h.linerLeftPt, cy + r)
            }
        }
        when (fwdStyle) {
            WearStripEndStyle.BREAK -> {
                val r = radii.fwdRPt
                svg.line(h.linerRightPt, cy - r, stubRightX, cy - r)
                svg.line(h.linerRightPt, cy + r, stubRightX, cy + r)
                svg.line(stubRightX, cy - r, stubRightX, cy + r, color = "#c00", dash = "3,2")
            }
            WearStripEndStyle.THREAD_END -> {
                val threadDia = wearStripEndThreadDiaMm(spec, window.endMm, aftSide = false)
                val r = if (threadDia > 0f) rOf(threadDia) else radii.fwdRPt
                svg.line(h.linerRightPt, cy - r, stubRightX, cy - r)
                svg.line(h.linerRightPt, cy + r, stubRightX, cy + r)
                svg.line(stubRightX, cy - r, stubRightX, cy + r)
                hatch(h.linerRightPt, cy - r, stubRightX, cy + r)
            }
            WearStripEndStyle.FLAT -> {
                val r = rOf(comps.last().fwdDiaMm)
                svg.line(h.linerRightPt, cy - r, h.linerRightPt, cy + r)
            }
        }

        // Titles, one per attachment cluster, on the strip's single bottom baseline — the
        // composer's rule (names joined " + ", anchor appended only where the cluster shows one).
        data class ClusterLabel(val cluster: WearStripCluster, val text: String, val anchored: Boolean)
        val sets = computeSetPositionsInMeasureSpace(computeOalWindow(spec), spec)
        val labels = wearStripClusters(window).map { cl ->
            val nameText = cl.components.joinToString(" + ") { names[it.id] ?: "Component" }
            val anchor = if (!wearStripClusterShowsAnchor(cl)) null else {
                val ln = cl.components.firstOrNull { it.kind == WearStripComponentKind.LINER }
                    ?.let { lc -> spec.liners.firstOrNull { it.id == lc.id } }
                val label = if (ln != null) buildLinerAnchorLabel(spec, ln, sets, UnitSystem.INCHES)
                else buildSpanAnchorLabel(spec, cl.startMm, cl.endMm, sets, UnitSystem.INCHES)
                label.takeIf { it.isNotEmpty() }
            }
            ClusterLabel(cl, if (anchor == null) nameText else "$nameText — $anchor", anchor != null)
        }
        // The historical lone anchored cluster keeps its start-aligned title; anything else
        // centres under its own cluster's drawn span so a name points at the metal it names.
        val loneAnchored = labels.size == 1 && labels[0].anchored
        val baselineY = cellBottom - 2f
        labels.forEach { l ->
            if (loneAnchored) {
                svg.text(cellLeft, baselineY, esc(l.text))
            } else {
                val cx = (xAt(l.cluster.startMm) + xAt(l.cluster.endMm)) / 2f
                svg.text(cx, baselineY, esc(l.text), anchor = "middle")
            }
        }

        return Drawn(
            svg = svg.wrap(cellRight - cellLeft + 20f, cellBottom - cellTop + 10f),
            minX = minX, maxX = maxX,
            leftPt = h.linerLeftPt, rightPt = h.linerRightPt, stubWidthPt = h.stubWidthPt,
            gapBreakXs = gapBreakXs, titles = labels.map { it.text },
        )
    }

    // The AFT taper narrows TOWARD its aft SET (small end aft, large end fwd) — the real
    // orientation; a preview drawn the other way around reads backwards (on-device report).
    private fun spec(): ShaftSpec = ShaftSpec(
        overallLengthMm = 800f,
        bodies = listOf(
            Body(id = "b1", startFromAftMm = 0f, lengthMm = 400f, diaMm = 140f),
            Body(id = "b2", startFromAftMm = 500f, lengthMm = 300f, diaMm = 150f),
        ),
        tapers = listOf(
            Taper(id = "t1", startFromAftMm = 400f, lengthMm = 100f, startDiaMm = 140f, endDiaMm = 150f),
        ),
        liners = listOf(
            Liner(id = "lnNear", startFromAftMm = 540f, lengthMm = 200f, odMm = 160f),
        ),
    )

    private val names = mapOf(
        "b1" to "AFT Body", "b2" to "FWD Body", "t1" to "AFT Taper",
        "lnNear" to "AFT Liner", "lnFar" to "FWD Liner", "lnEnd" to "AFT Liner",
    )

    @Test
    fun `render strip window preview SVGs`() {
        val outDir = File("build/reports/wear-strip-preview").apply { mkdirs() }
        val spec = spec()
        val comps = wearStripComponentsFor(spec, null)
        val inner = cellRight - cellLeft - 2f * WEAR_STRIP_STUB_WIDTH_PT

        // A) Combined taper + liner, 40 mm apart — the gap draws TRUE with the real shaft outline
        //    under it — and beyond the taper's outer end there is nothing but the threaded shaft
        //    end, so that side draws flat + hatched rather than claiming the shaft continues.
        //    The cluster holds a taper, so its title is names only: no from-SET anchor.
        val threadSpec = ShaftSpec(
            overallLengthMm = 800f,
            bodies = listOf(Body(id = "b2", startFromAftMm = 500f, lengthMm = 300f, diaMm = 150f)),
            tapers = listOf(Taper(id = "t1", startFromAftMm = 400f, lengthMm = 100f, startDiaMm = 140f, endDiaMm = 150f)),
            threads = listOf(Threads(id = "th", startFromAftMm = 340f, lengthMm = 60f, majorDiaMm = 120f)),
            liners = listOf(Liner(id = "lnNear", startFromAftMm = 540f, lengthMm = 200f, odMm = 160f)),
        )
        val threadComps = wearStripComponentsFor(threadSpec, null)
        val near = collectWearStripWindows(threadComps, listOf("t1", "lnNear")).single()
        assertEquals(listOf("t1", "lnNear"), near.components.map { it.id })
        assertTrue("a 40 mm gap must draw true", near.segments.filterIsInstance<WearStripGapSeg>().single().trueScale)
        assertEquals(
            "only thread lies beyond the taper's outer end",
            WearStripEndStyle.THREAD_END,
            wearStripEndStyle(threadSpec, near.startMm, aftSide = true),
        )
        assertEquals(120f, wearStripEndThreadDiaMm(threadSpec, near.startMm, aftSide = true), 1e-4f)
        val nearScale = sharedWearStripWindowPtPerMm(listOf(near), listOf(inner))
        val a = windowSvg(threadSpec, near, nearScale, names, "A — taper + liner, true gap, threaded aft end")
        File(outDir, "a-taper-liner-true-gap-thread-end.svg").writeText(a.svg)
        assertTrue("nothing may escape the cell", a.minX >= cellLeft - 1e-3f && a.maxX <= cellRight + 1e-3f)
        assertEquals(listOf("AFT Taper + AFT Liner"), a.titles)
        assertFalse("a taper-bearing cluster prints no from-SET anchor", a.svg.contains("S.E.T."))
        // The thread end still draws a stub — it is the whole remaining shaft, not a truncation.
        assertEquals(a.leftPt - a.stubWidthPt, a.minX, 1e-3f)

        // B) The same pair pushed far apart — the gap compresses to the fixed break run, which
        //    splits the window into TWO clusters: the taper titles itself (no anchor) and the
        //    lone liner keeps its from-SET dimension.
        val farSpec = spec.copy(
            overallLengthMm = 1200f,
            bodies = listOf(
                Body(id = "b1", startFromAftMm = 0f, lengthMm = 400f, diaMm = 140f),
                Body(id = "b2", startFromAftMm = 500f, lengthMm = 700f, diaMm = 150f),
            ),
            liners = listOf(Liner(id = "lnFar", startFromAftMm = 900f, lengthMm = 200f, odMm = 160f)),
        )
        val farComps = wearStripComponentsFor(farSpec, null)
        val far = collectWearStripWindows(farComps, listOf("t1", "lnFar")).single()
        val farGap = far.segments.filterIsInstance<WearStripGapSeg>().single()
        assertTrue("a 400 mm gap must compress", !farGap.trueScale)
        assertEquals(
            listOf(listOf("t1"), listOf("lnFar")),
            wearStripClusters(far).map { cl -> cl.components.map { it.id } },
        )
        val farScale = sharedWearStripWindowPtPerMm(listOf(far), listOf(inner))
        val b = windowSvg(farSpec, far, farScale, names, "B — taper + liner, compressed gap, two clusters")
        File(outDir, "b-taper-liner-break-gap.svg").writeText(b.svg)
        assertTrue(b.minX >= cellLeft - 1e-3f && b.maxX <= cellRight + 1e-3f)
        assertEquals(2, b.titles.size)
        assertEquals("AFT Taper", b.titles[0])
        assertTrue("the lone liner cluster keeps its anchor", b.titles[1].contains("S.E.T."))
        // The break edges stand a lead-in inside the compressed run's two drawn ends, so a bit of
        // real shaft connects each component to its break.
        val hFar = computeWearStripWindowLayout(cellLeft, cellRight, far.drawnWidthPt(farScale), farScale)
        val gx0 = far.xAt(farGap.startMm, hFar.linerLeftPt, farScale)
        val gx1 = far.xAt(farGap.endMm, hFar.linerLeftPt, farScale)
        assertEquals(WEAR_STRIP_BREAK_GAP_PT, gx1 - gx0, 1e-3f)
        assertEquals(2, b.gapBreakXs.size)
        assertEquals(gx0 + WEAR_STRIP_BREAK_LEAD_PT, b.gapBreakXs[0], 1e-3f)
        assertEquals(gx1 - WEAR_STRIP_BREAK_LEAD_PT, b.gapBreakXs[1], 1e-3f)
        assertTrue("the pair must keep open space between them", b.gapBreakXs[1] > b.gapBreakXs[0])

        // C) A body elected on its own — a plain rectangle strip.
        val bodyWindow = collectWearStripWindows(comps, listOf("b1")).single()
        val bodyScale = sharedWearStripWindowPtPerMm(listOf(bodyWindow), listOf(inner))
        val cSvg = windowSvg(spec, bodyWindow, bodyScale, names, "C — body on its own strip")
        File(outDir, "c-body-own-strip.svg").writeText(cSvg.svg)
        assertTrue(cSvg.minX >= cellLeft - 1e-3f && cSvg.maxX <= cellRight + 1e-3f)
        assertTrue("a lone body keeps its anchor dimension", cSvg.titles.single().contains("S.E.T."))

        // D) The default liner-only sheet — the window must land exactly where the legacy
        //    per-liner strip did, which is the compatibility guarantee for every old document.
        val linerOnly = collectWearStripWindows(comps, null).single()
        val linerScale = sharedWearStripWindowPtPerMm(listOf(linerOnly), listOf(inner))
        val legacy = computeWearStripHorizontalLayout(cellLeft, cellRight, 200f, ptPerMmOverride = linerScale)
        val now = computeWearStripWindowLayout(cellLeft, cellRight, linerOnly.drawnWidthPt(linerScale), linerScale)
        assertEquals(legacy.linerLeftPt, now.linerLeftPt, 1e-3f)
        assertEquals(legacy.linerRightPt, now.linerRightPt, 1e-3f)
        val d = windowSvg(spec, linerOnly, linerScale, names, "D — default liner-only strip")
        File(outDir, "d-liner-only.svg").writeText(d.svg)
        assertTrue(d.minX >= cellLeft - 1e-3f && d.maxX <= cellRight + 1e-3f)
        // Shaft on both sides, so both ends break out and both stubs draw.
        assertEquals(d.leftPt - d.stubWidthPt, d.minX, 1e-3f)
        assertEquals(d.rightPt + d.stubWidthPt, d.maxX, 1e-3f)

        // E) A taper elected on its own — the shared span-anchor helper still resolves a from-SET
        //    dimension for it, but a taper cluster no longer PRINTS one: the strip's own rail is
        //    the measuring surface and a taper at the shaft end is self-evidently placed.
        val sets = computeSetPositionsInMeasureSpace(computeOalWindow(spec), spec)
        val taperWindow = collectWearStripWindows(comps, listOf("t1")).single()
        assertEquals(listOf("t1"), taperWindow.components.map { it.id })
        val anchorLabel = buildSpanAnchorLabel(
            spec, taperWindow.startMm, taperWindow.endMm, sets, UnitSystem.INCHES,
        )
        assertTrue("the span-anchor helper itself still resolves a SET", anchorLabel.contains("S.E.T."))
        assertFalse(wearStripClusterShowsAnchor(wearStripClusters(taperWindow).single()))
        val taperScale = sharedWearStripWindowPtPerMm(listOf(taperWindow), listOf(inner))
        val e = windowSvg(spec, taperWindow, taperScale, names, "E — taper on its own strip, name-only title")
        File(outDir, "e-taper-only-no-anchor.svg").writeText(e.svg)
        assertEquals(listOf("AFT Taper"), e.titles)
        assertFalse("the drawn title must be name-only", e.svg.contains("S.E.T."))
        assertTrue(e.minX >= cellLeft - 1e-3f && e.maxX <= cellRight + 1e-3f)

        // F) A liner that ends the shaft outright — nothing lies aft of it, so that side draws a
        //    full-weight end cap and NO stub; the forward side still breaks out onto the body.
        val endSpec = ShaftSpec(
            overallLengthMm = 600f,
            bodies = listOf(Body(id = "b", startFromAftMm = 200f, lengthMm = 400f, diaMm = 150f)),
            liners = listOf(Liner(id = "lnEnd", startFromAftMm = 0f, lengthMm = 200f, odMm = 160f)),
        )
        val endComps = wearStripComponentsFor(endSpec, null)
        val endWindow = collectWearStripWindows(endComps, listOf("lnEnd")).single()
        assertEquals(WearStripEndStyle.FLAT, wearStripEndStyle(endSpec, endWindow.startMm, aftSide = true))
        assertEquals(WearStripEndStyle.BREAK, wearStripEndStyle(endSpec, endWindow.endMm, aftSide = false))
        val endScale = sharedWearStripWindowPtPerMm(listOf(endWindow), listOf(inner))
        val f = windowSvg(endSpec, endWindow, endScale, names, "F — shaft ends at the window: flat end, no stub")
        File(outDir, "f-flat-shaft-end.svg").writeText(f.svg)
        assertTrue(f.minX >= cellLeft - 1e-3f && f.maxX <= cellRight + 1e-3f)
        assertEquals("nothing may be drawn past a FLAT window edge", f.leftPt, f.minX, 1e-3f)
        assertEquals("…while the breaking side still draws its stub", f.rightPt + f.stubWidthPt, f.maxX, 1e-3f)

        assertTrue(outDir.listFiles()!!.size >= 6)
    }

    @Test
    fun `a true gap's outline follows the shaft under it, step by step`() {
        // The gap between the taper's FWD end (500 mm, Ø150) and the liner (540 mm) runs over
        // body b2 at Ø150 — the drawn outline must report that diameter, not a bridged guess.
        val spec = spec()
        val verts = wearStripGapProfile(spec, 500f, 540f, samples = 8)
        assertTrue(verts.isNotEmpty())
        verts.filter { it.mm < 540f }.forEach { assertEquals(150f, it.diaMm, 1e-3f) }
        // The closing vertex sits on the liner's own edge, so it reports the liner OD — the
        // outline meets the next component's surface rather than stopping short of it.
        assertEquals(160f, verts.last().diaMm, 1e-3f)
        // …and a run over bare nothing reports no profile, so the caller bridges instead.
        assertTrue(wearStripGapProfile(ShaftSpec(), 0f, 100f).isEmpty())
    }

    @Test
    fun `a compressed gap's lead-in reads the shaft just inside the gap, not the component edge`() {
        // The composer takes the gap's TRUE outline half a millimetre inside each end. Between the
        // taper (ending Ø150 at 500 mm) and a far liner the shaft under the gap is body b2's
        // Ø150 — so both lead-ins sit on the real shaft, and the fallback is never reached.
        val farSpec = spec().copy(
            overallLengthMm = 1200f,
            bodies = listOf(
                Body(id = "b1", startFromAftMm = 0f, lengthMm = 400f, diaMm = 140f),
                Body(id = "b2", startFromAftMm = 500f, lengthMm = 700f, diaMm = 150f),
            ),
            liners = listOf(Liner(id = "lnFar", startFromAftMm = 900f, lengthMm = 200f, odMm = 160f)),
        )
        assertEquals(150f, outerDiaMmAt(farSpec, 500.5f), 1e-3f)
        assertEquals(150f, outerDiaMmAt(farSpec, 899.5f), 1e-3f)
        // Bare nothing under the gap reports 0, which is exactly when the composer (and the
        // preview) falls back to the adjacent component's edge diameter.
        assertEquals(0f, outerDiaMmAt(ShaftSpec(), 50f), 1e-3f)
    }
}
