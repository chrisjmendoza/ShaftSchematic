package com.android.shaftschematic.pdf

import com.android.shaftschematic.geom.WearTraceReading
import com.android.shaftschematic.geom.WearTraceVertex
import com.android.shaftschematic.geom.buildWearTrace
import com.android.shaftschematic.geom.computeOalWindow
import com.android.shaftschematic.geom.computeSetPositionsInMeasureSpace
import com.android.shaftschematic.geom.deepestWearDepthMm
import com.android.shaftschematic.geom.smoothWearTrace
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
 * - **Window MEMBERSHIP follows the join threshold** ([collectWearStripWindows]): a taper within
 *   it shares one window with its nearest liner (the gap drawn true, with the real shaft outline
 *   under it), and a taper past it becomes its own strip — so no window here carries a compressed
 *   gap, and the two ends of the far pair each centre in their own cell.
 * - **Titles are per attachment cluster** ([wearStripClusters]): a taper-bearing cluster prints
 *   names only ([wearStripClusterShowsAnchor]) and a lone liner/body keeps its from-SET anchor.
 *
 * The rendered scenarios: **A** near taper + liner in one window with a threaded aft end,
 * **B1/B2** a far liner and a far taper as separate strips on the page's one shared scale,
 * **C** a lone body, **D** the default liner-only strip, **E** a lone taper, **F** a liner at the
 * shaft's own end, **G** a liner's smoothed worn-profile trace over a wear band with five
 * measured stations (`buildWearTrace` → `smoothWearTrace`), the straight run it replaces drawn
 * under it in dashed grey.
 *
 * Doubles as a geometry regression: every drawn segment must stay inside its cell, the near/far
 * taper cases must produce different window counts, a FLAT end must draw nothing past the window
 * edge while a BREAK end stands its glyph a full stub width off it, and a liner-only window must
 * land exactly where the legacy per-liner strip did.
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

    /**
     * Faithful to drawWearStripWindow's silhouette geometry (rails/values omitted).
     *
     * The cell rect and [stubWidthPt] default to the single-strip fixture; the packed-page preview
     * passes one packed cell and the page's own squeezed stub instead, and hands in an [into]
     * canvas so every strip lands on one sheet. With [into] set the returned `svg` is empty — the
     * caller wraps the whole page itself.
     */
    private fun windowSvg(
        spec: ShaftSpec,
        window: WearStripWindow,
        ptPerMm: Float,
        names: Map<String, String>,
        caption: String,
        left: Float = cellLeft,
        right: Float = cellRight,
        top: Float = cellTop,
        bottom: Float = cellBottom,
        stubWidthPt: Float = WEAR_STRIP_STUB_WIDTH_PT,
        into: Svg? = null,
    ): Drawn {
        val svg = into ?: Svg()
        val h = computeWearStripWindowLayout(
            left, right, window.drawnWidthPt(ptPerMm), ptPerMm, stubWidthPt = stubWidthPt,
        )
        fun xAt(mm: Float) = window.xAt(mm, h.linerLeftPt, ptPerMm)

        val inner = computeWearStripInnerLayout(top, bottom, titleHeightPt = 9f)
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

        svg.rect(left, top, right - left, bottom - top, stroke = "#bbb", sw = 0.5f)
        svg.text(left, top + 8f, esc(caption), size = 7f, color = "#888")

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
        val baselineY = bottom - 2f
        labels.forEach { l ->
            if (loneAnchored) {
                svg.text(left, baselineY, esc(l.text))
            } else {
                val cx = (xAt(l.cluster.startMm) + xAt(l.cluster.endMm)) / 2f
                svg.text(cx, baselineY, esc(l.text), anchor = "middle")
            }
        }

        return Drawn(
            svg = if (into != null) "" else svg.wrap(right - left + 20f, bottom - top + 10f),
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

        // B) The same pair pushed far apart — past the join threshold the taper does not attach
        //    at all: two single-component strips, sharing the page's one scale. The taper titles
        //    itself (no anchor) and the lone liner keeps its from-SET dimension, each centred in
        //    its own cell instead of the liner being pushed off-centre by a shared window.
        val farSpec = spec.copy(
            overallLengthMm = 1200f,
            bodies = listOf(
                Body(id = "b1", startFromAftMm = 0f, lengthMm = 400f, diaMm = 140f),
                Body(id = "b2", startFromAftMm = 500f, lengthMm = 700f, diaMm = 150f),
            ),
            liners = listOf(Liner(id = "lnFar", startFromAftMm = 900f, lengthMm = 200f, odMm = 160f)),
        )
        val farComps = wearStripComponentsFor(farSpec, null)
        val farWindows = collectWearStripWindows(farComps, listOf("t1", "lnFar"))
        assertEquals(
            "a 400 mm gap is past the join threshold, so neither joins",
            listOf(listOf("t1"), listOf("lnFar")),
            farWindows.map { w -> w.components.map { it.id } },
        )
        val farTaperW = farWindows[0]
        val farLinerW = farWindows[1]
        assertTrue(
            "a separated window carries no gap segment at all",
            farWindows.all { w -> w.segments.none { it is WearStripGapSeg } },
        )
        // ONE page scale solved over both windows, exactly as the sheet does it.
        val farScale = sharedWearStripWindowPtPerMm(listOf(farTaperW, farLinerW), listOf(inner))
        File(outDir, "b-taper-liner-break-gap.svg").delete()   // superseded by the two strips below
        val b1 = windowSvg(farSpec, farLinerW, farScale, names, "B1 — far liner on its own strip")
        File(outDir, "b1-far-liner-own-strip.svg").writeText(b1.svg)
        assertTrue(b1.minX >= cellLeft - 1e-3f && b1.maxX <= cellRight + 1e-3f)
        assertTrue("the lone liner keeps its anchor", b1.titles.single().contains("S.E.T."))

        val b2 = windowSvg(farSpec, farTaperW, farScale, names, "B2 — far taper on its own strip")
        File(outDir, "b2-far-taper-own-strip.svg").writeText(b2.svg)
        assertTrue(b2.minX >= cellLeft - 1e-3f && b2.maxX <= cellRight + 1e-3f)
        assertEquals(listOf("AFT Taper"), b2.titles)
        assertFalse("a taper cluster prints no from-SET anchor", b2.svg.contains("S.E.T."))
        // Body b2 runs on past the taper's LARGE (fwd) end, so that side is a BREAK stub: the
        // glyph now stands a full stub width off the component edge instead of being pressed
        // against it inside a shared window (on-device report).
        assertEquals(
            WearStripEndStyle.BREAK,
            wearStripEndStyle(farSpec, farTaperW.endMm, aftSide = false),
        )
        assertEquals(b2.rightPt + b2.stubWidthPt, b2.maxX, 1e-3f)
        assertEquals(b2.leftPt - b2.stubWidthPt, b2.minX, 1e-3f)

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
    fun `render the smoothed worn-profile trace preview SVG`() {
        // The worn liner surface: same math the wear sheet's liner strip draws with
        // (`buildWearTrace` → `smoothWearTrace`), so the SVG shows exactly the curve that prints.
        // The straight run it replaces is drawn under it in dashed grey for markup comparison.
        val outDir = File("build/reports/wear-strip-preview").apply { mkdirs() }
        val spec = spec()
        val comps = wearStripComponentsFor(spec, null)
        val inner = cellRight - cellLeft - 2f * WEAR_STRIP_STUB_WIDTH_PT
        val window = collectWearStripWindows(comps, null).single()
        val liner = window.components.single { it.kind == WearStripComponentKind.LINER }
        val ptPerMm = sharedWearStripWindowPtPerMm(listOf(window), listOf(inner))

        // One wear band over most of the liner, five measured stations inside it — a deep dip
        // flanked by shallower readings, the shape that shows both the easing and the fact that
        // the curve never bulges past a station.
        val nominal = liner.aftDiaMm
        val readings = listOf(
            WearTraceReading(40f, 156f),
            WearTraceReading(70f, 150f),
            WearTraceReading(100f, 146f),
            WearTraceReading(130f, 152f),
            WearTraceReading(160f, 158f),
        )
        val deepest = deepestWearDepthMm(readings.map { nominal to it.diaMm })
        val band = clampWearBandToLiner(30f, 140f, liner.lengthMm)
        val raw = buildWearTrace(
            bandStartMm = band.startMm, bandLengthMm = band.lengthMm,
            readings = readings, nominalOdMm = nominal, deepestDepthMm = deepest,
        )
        val smooth = smoothWearTrace(raw)
        assertTrue("the fixture must actually trace", raw.size >= 5)

        // Regression, not just a picture: the drawn curve keeps every authored station depth and
        // never digs past one — the whole reason the smoother is monotone cubic.
        assertTrue("every authored station must survive smoothing", smooth.containsAll(raw))
        val deepestFrac = raw.maxOf { it.depthFrac }
        smooth.forEach {
            assertTrue("a sample dug past the deepest reading", it.depthFrac <= deepestFrac + 1e-6f)
            assertTrue("a sample rose above the liner surface", it.depthFrac >= -1e-6f)
        }

        val svg = Svg()
        val h = computeWearStripWindowLayout(cellLeft, cellRight, window.drawnWidthPt(ptPerMm), ptPerMm)
        fun xAt(mm: Float) = window.xAt(mm, h.linerLeftPt, ptPerMm)
        fun xLocal(mm: Float) = xAt(liner.startMm + mm)
        val innerLay = computeWearStripInnerLayout(cellTop, cellBottom, titleHeightPt = 9f)
        val cy = (innerLay.cylTop + innerLay.cylBottom) / 2f
        val rCap = (innerLay.cylBottom - innerLay.cylTop) / 2f
        val r = (rCap * (nominal / window.refDiaMm)).coerceIn(0f, rCap)
        val top = cy - r
        val bot = cy + r
        val x0 = xLocal(0f)
        val x1 = xLocal(liner.lengthMm)

        svg.rect(cellLeft, cellTop, cellRight - cellLeft, cellBottom - cellTop, stroke = "#bbb", sw = 0.5f)
        svg.text(cellLeft, cellTop + 8f, esc("G — smoothed worn-profile trace (dashed grey = the straight run it replaces)"), size = 7f, color = "#888")

        /** One traced edge, exactly as `tracedLinerEdgePath` walks it: straight lineTo per vertex. */
        fun edge(verts: List<WearTraceVertex>, dir: Float, color: String, w: Float, dash: String?) {
            val edgeY = cy - dir * r      // dir +1 = the TOP edge, -1 = the BOTTOM
            var px = x0
            var py = edgeY
            verts.forEach { v ->
                val vx = xLocal(v.localMm)
                val vy = edgeY + dir * v.depthFrac * r
                svg.line(px, py, vx, vy, w = w, color = color, dash = dash)
                px = vx; py = vy
            }
            svg.line(px, py, x1, edgeY, w = w, color = color, dash = dash)
        }
        // The unsmoothed run first, so the curve draws over it.
        edge(raw, 1f, "#999", 0.7f, "3,2")
        edge(raw, -1f, "#999", 0.7f, "3,2")
        edge(smooth, 1f, "black", 1.2f, null)
        edge(smooth, -1f, "black", 1.2f, null)
        svg.line(x0, top, x0, bot, w = 0.7f)
        svg.line(x1, top, x1, bot, w = 0.7f)

        // Band edges + a witness tick and value at each measured station, so the curve can be
        // read against the numbers it was built from.
        listOf(band.startMm, band.startMm + band.lengthMm).forEach { mm ->
            svg.line(xLocal(mm), top, xLocal(mm), bot, w = 0.7f, color = "#c60", dash = "2,2")
        }
        readings.forEach { rd ->
            val sx = xLocal(rd.localMm)
            svg.line(sx, top - 3f, sx, bot + 3f, w = 0.5f, color = "#06c")
            svg.text(sx, top - 5f, esc("Ø${rd.diaMm}"), size = 6f, anchor = "middle", color = "#06c")
        }
        svg.text(cellLeft, cellBottom - 2f, esc("AFT Liner — worn profile, ${smooth.size} vertices from ${raw.size}"))

        val file = File(outDir, "g-smooth-worn-trace.svg")
        file.writeText(svg.wrap(cellRight - cellLeft + 20f, cellBottom - cellTop + 10f))
        assertTrue(file.exists())
    }

    @Test
    fun `render the packed-page preview SVGs`() {
        // Whole-page previews of the dynamic row packing: the FEWEST rows that hold the election,
        // then the largest scale within them, then whitespace (stubs + gutters) re-expanded as far
        // as the rows allow. The row BUDGET follows the profile toggle — two rows with the shaft on
        // the page, three without — so the freed band is capacity, spent only when two rows can't
        // hold everything. Same page geometry the wear sheet uses (landscape US Letter).
        val outDir = File("build/reports/wear-strip-preview").apply { mkdirs() }
        val pageLeft = 36f
        val pageRight = 756f
        val bandTop = 84f
        val bandBottom = 500f

        // Seven liners on one shaft — an election two rows cannot hold, so the profile toggle
        // decides whether the seventh prints or goes to the "+N more" note. Under the fixed
        // 2-column grid only four ever printed.
        val packSpec = ShaftSpec(
            overallLengthMm = 4200f,
            bodies = listOf(Body(id = "bp", startFromAftMm = 0f, lengthMm = 4200f, diaMm = 150f)),
            liners = listOf(
                Liner(id = "l1", startFromAftMm = 100f, lengthMm = 150f, odMm = 160f),
                Liner(id = "l2", startFromAftMm = 600f, lengthMm = 200f, odMm = 160f),
                Liner(id = "l3", startFromAftMm = 1100f, lengthMm = 150f, odMm = 160f),
                Liner(id = "l4", startFromAftMm = 1700f, lengthMm = 250f, odMm = 160f),
                Liner(id = "l5", startFromAftMm = 2400f, lengthMm = 180f, odMm = 160f),
                Liner(id = "l6", startFromAftMm = 3000f, lengthMm = 160f, odMm = 160f),
                Liner(id = "l7", startFromAftMm = 3600f, lengthMm = 220f, odMm = 160f),
            ),
        )
        val packWindows = collectWearStripWindows(wearStripComponentsFor(packSpec, null), null)
        assertEquals(7, packWindows.size)
        val packNames = packSpec.liners.mapIndexed { i, ln -> ln.id to "Liner ${i + 1}" }.toMap()

        /** One packed sheet, drawn exactly as `composeWearPdf`'s GRID branch lays it out. */
        fun page(
            showProfile: Boolean,
            file: String,
            maxPtPerMm: Float = WEAR_STRIP_MAX_PT_PER_MM,
        ): WearStripPacking {
            val packing = packWearStripWindows(
                packWindows, pageLeft, pageRight, maxRows = wearStripMaxRows(showProfile),
                maxPtPerMm = maxPtPerMm,
            )
            val v = computeWearVerticalLayout(
                bandTop, bandBottom, packing.rowCount,
                minProfileHeightPt = if (showProfile) WEAR_MIN_PROFILE_HEIGHT_PT else 0f,
                profileToStripsGapPt = if (showProfile) WEAR_STRIP_TOP_GAP_PT else 0f,
                preferredProfileHeightPt = if (showProfile) 190f else 0f,
                maxStripHeightPt = if (showProfile) 170f else Float.MAX_VALUE,
            )
            val svg = Svg()
            svg.rect(pageLeft, bandTop, pageRight - pageLeft, bandBottom - bandTop, stroke = "#ddd", sw = 0.5f)
            if (showProfile) {
                svg.rect(pageLeft, v.profileTop, pageRight - pageLeft, v.profileBottom - v.profileTop,
                    stroke = "#ccc", sw = 0.5f)
                svg.text(pageLeft + 4f, v.profileTop + 12f, esc("[ shaft profile band ]"), size = 8f, color = "#aaa")
            }
            svg.text(
                pageLeft, bandTop - 4f,
                esc(
                    (if (showProfile) "profile shown — " else "profile hidden — ") +
                        "${packing.rowCount} row(s), ${packing.placedCount}/${packWindows.size} strips, " +
                        "scale ${"%.3f".format(packing.ptPerMm)} pt/mm, " +
                        "stub ${"%.1f".format(packing.spacing.stubWidthPt)} pt, " +
                        "gutter ${"%.1f".format(packing.spacing.colGapPt)} pt",
                ),
                size = 8f, color = "#666",
            )
            packing.cells.forEach { cell ->
                val w = packWindows[cell.windowIndex]
                val drawn = windowSvg(
                    packSpec, w, packing.ptPerMm, packNames, "",
                    left = cell.left, right = cell.right,
                    top = v.stripTops[cell.row], bottom = v.stripBottoms[cell.row],
                    stubWidthPt = packing.spacing.stubWidthPt, into = svg,
                )
                assertTrue(
                    "strip ${cell.windowIndex} escaped its packed cell",
                    drawn.minX >= cell.left - 1e-3f && drawn.maxX <= cell.right + 1e-3f,
                )
                assertEquals("the page's one stub width", packing.spacing.stubWidthPt, drawn.stubWidthPt, 1e-4f)
            }
            File(outDir, file).writeText(svg.wrap(pageRight + 20f, bandBottom + 10f))
            return packing
        }

        val shown = page(showProfile = true, file = "h1-packed-page-profile-shown.svg")
        assertEquals("the shaft keeps one of the page's three bands", 2, shown.rowCount)
        assertEquals("two rows hold six of the seven strips", 6, shown.placedCount)
        // Whitespace reclaimed: a row carries THREE short components, which the fixed 2-column
        // grid could never do (its cells were half the page whatever the strips drew).
        assertEquals(3, shown.cells.count { it.row == 0 })
        assertTrue(
            "stubs squeezed toward their floor to seat three in a row",
            shown.spacing.stubWidthPt < WEAR_STRIP_STUB_WIDTH_PT &&
                shown.spacing.stubWidthPt >= WEAR_STRIP_STUB_MIN_PT - 1e-4f,
        )

        val hidden = page(showProfile = false, file = "h2-packed-page-profile-hidden.svg")
        assertEquals("the freed profile band becomes the third row this election needs", 3, hidden.rowCount)
        assertEquals("…so the whole election prints", 7, hidden.placedCount)
        // Fewer strips per row, so the whole page draws bigger on its ONE shared scale.
        assertTrue(
            "the third row buys drawn size (${hidden.ptPerMm} vs ${shown.ptPerMm})",
            hidden.ptPerMm > shown.ptPerMm,
        )
        assertTrue(File(outDir, "h1-packed-page-profile-shown.svg").exists())
        assertTrue(File(outDir, "h2-packed-page-profile-hidden.svg").exists())

        // H3) The same election in COMPACT mode (WearRecord.compactStrips): the ceiling drops to
        // the main profile's own pt/mm — here a 4.2 m shaft across 720 pt, so the compact FLOOR
        // takes over — and the strips stop stretching toward the page. Nothing else changes: the
        // packer still solves rows-then-scale under the lower ceiling.
        val profilePtPerMm = (pageRight - pageLeft) / packSpec.overallLengthMm
        val compactCap = wearStripMaxPtPerMm(compactStrips = true, profilePtPerMm = profilePtPerMm)
        assertEquals(
            "a 4.2 m shaft draws too small to cap the strips at, so the floor holds",
            WEAR_STRIP_COMPACT_MIN_PT_PER_MM, compactCap, 1e-6f,
        )
        val compact = page(
            showProfile = true, file = "h3-packed-page-compact.svg", maxPtPerMm = compactCap,
        )
        assertTrue("compact must not exceed its ceiling", compact.ptPerMm <= compactCap + 1e-4f)
        assertTrue(
            "compact must draw smaller than the stretched page (${compact.ptPerMm} vs ${shown.ptPerMm})",
            compact.ptPerMm < shown.ptPerMm,
        )
        assertTrue(File(outDir, "h3-packed-page-compact.svg").exists())
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
