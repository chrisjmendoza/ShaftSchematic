package com.android.shaftschematic.pdf

import com.android.shaftschematic.geom.computeOalWindow
import com.android.shaftschematic.geom.computeSetPositionsInMeasureSpace
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Same-math SVG previews of the wear sheet's multi-component detail strips, for visual (markup)
 * review without an on-device round trip — the `WearDiaCalloutSvgPreviewTest` pattern. Drives the
 * REAL layout code (`collectWearStripWindows`, `sharedWearStripWindowPtPerMm`,
 * `computeWearStripWindowLayout`, `WearStripWindow.xAt`, `wearStripGapProfile`,
 * `computeWearStripInnerLayout`, `computeWearStripRadii`) and writes the silhouettes
 * `WearPdfComposer` would draw into `app/build/reports/wear-strip-preview/`.
 *
 * Doubles as a geometry regression: every drawn segment must stay inside its cell, the
 * near/far taper cases must pick different gap modes, and a liner-only window must land exactly
 * where the legacy per-liner strip did.
 */
class WearStripWindowSvgPreviewTest {

    private val cellLeft = 36f
    private val cellRight = 396f
    private val cellTop = 10f
    private val cellBottom = 150f

    private data class Svg(val sb: StringBuilder = StringBuilder()) {
        fun line(x0: Float, y0: Float, x1: Float, y1: Float, w: Float = 1.2f, color: String = "black", dash: String? = null) {
            sb.append("""<line x1="$x0" y1="$y0" x2="$x1" y2="$y1" stroke="$color" stroke-width="$w"""")
            if (dash != null) sb.append(""" stroke-dasharray="$dash"""")
            sb.append("/>\n")
        }
        fun rect(x: Float, y: Float, w: Float, h: Float, stroke: String = "black", fill: String = "none", sw: Float = 1.2f) {
            sb.append("""<rect x="$x" y="$y" width="$w" height="$h" stroke="$stroke" fill="$fill" stroke-width="$sw"/>""").append('\n')
        }
        fun text(x: Float, y: Float, s: String, size: Float = 9f, anchor: String = "start") {
            sb.append("""<text x="$x" y="$y" font-size="$size" font-family="Helvetica, Arial, sans-serif" text-anchor="$anchor">$s</text>""").append('\n')
        }
        fun wrap(w: Float, h: Float): String =
            "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 $w $h\" width=\"${w * 1.6f}\" height=\"${h * 1.6f}\" style=\"background:white\">\n$sb</svg>\n"
    }

    /** Every x the strip draws at, so the caller can assert nothing escapes its cell. */
    private data class Drawn(val svg: String, val minX: Float, val maxX: Float)

    /** Faithful to drawWearStripWindow's silhouette geometry (values/rails omitted). */
    private fun windowSvg(spec: ShaftSpec, window: WearStripWindow, ptPerMm: Float, title: String): Drawn {
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

        var minX = h.linerLeftPt - h.stubWidthPt
        var maxX = h.linerRightPt + h.stubWidthPt
        fun seen(x: Float) { if (x < minX) minX = x; if (x > maxX) maxX = x }

        svg.rect(cellLeft, cellTop, cellRight - cellLeft, cellBottom - cellTop, stroke = "#bbb", sw = 0.5f)

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
                    val rL = rOf((window.segments.getOrNull(i - 1) as? WearStripComponentSeg)?.component?.fwdDiaMm ?: aftDia)
                    val rR = rOf((window.segments.getOrNull(i + 1) as? WearStripComponentSeg)?.component?.aftDiaMm ?: fwdDia)
                    if (seg.trueScale) {
                        val verts = wearStripGapProfile(spec, seg.startMm, seg.endMm,
                            samples = ((x1 - x0) / 2f).toInt().coerceIn(2, 64))
                        if (verts.isEmpty()) {
                            svg.line(x0, cy - rL, x1, cy - rR, color = "#c60")
                            svg.line(x0, cy + rL, x1, cy + rR, color = "#c60")
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
                        // Break pair: the two edges stand in for drawBreakEdge's S glyphs.
                        svg.line(x0, cy - rL, x0, cy + rL, color = "#c00", dash = "3,2")
                        svg.line(x1, cy - rR, x1, cy + rR, color = "#c00", dash = "3,2")
                    }
                }
            }
        }

        // Neighbor stubs at the window's outer ends.
        svg.line(h.linerLeftPt - h.stubWidthPt, cy - radii.aftRPt, h.linerLeftPt, cy - radii.aftRPt)
        svg.line(h.linerLeftPt - h.stubWidthPt, cy + radii.aftRPt, h.linerLeftPt, cy + radii.aftRPt)
        svg.line(h.linerRightPt, cy - radii.fwdRPt, h.linerRightPt + h.stubWidthPt, cy - radii.fwdRPt)
        svg.line(h.linerRightPt, cy + radii.fwdRPt, h.linerRightPt + h.stubWidthPt, cy + radii.fwdRPt)

        svg.text(cellLeft, cellBottom - 2f, title)
        return Drawn(svg.wrap(cellRight - cellLeft + 20f, cellBottom - cellTop + 10f), minX, maxX)
    }

    private fun spec(): ShaftSpec = ShaftSpec(
        bodies = listOf(
            Body(id = "b1", startFromAftMm = 0f, lengthMm = 400f, diaMm = 150f),
            Body(id = "b2", startFromAftMm = 500f, lengthMm = 300f, diaMm = 140f),
        ),
        tapers = listOf(
            Taper(id = "t1", startFromAftMm = 400f, lengthMm = 100f, startDiaMm = 150f, endDiaMm = 140f),
        ),
        liners = listOf(
            Liner(id = "lnNear", startFromAftMm = 540f, lengthMm = 200f, odMm = 160f),
        ),
    )

    @Test
    fun `render strip window preview SVGs`() {
        val outDir = File("build/reports/wear-strip-preview").apply { mkdirs() }
        val spec = spec()
        val comps = wearStripComponentsFor(spec, null)
        val inner = cellRight - cellLeft - 2f * WEAR_STRIP_STUB_WIDTH_PT

        // A) Combined taper + liner, 40 mm apart — the gap draws TRUE, with the real shaft
        //    outline (the body under it) running between the two components.
        val near = collectWearStripWindows(comps, listOf("t1", "lnNear")).single()
        assertEquals(listOf("t1", "lnNear"), near.components.map { it.id })
        assertTrue("a 40 mm gap must draw true", near.segments.filterIsInstance<WearStripGapSeg>().single().trueScale)
        val nearScale = sharedWearStripWindowPtPerMm(listOf(near), listOf(inner))
        val a = windowSvg(spec, near, nearScale, "AFT Taper + AFT Liner — combined, true gap")
        File(outDir, "a-taper-liner-true-gap.svg").writeText(a.svg)
        assertTrue("nothing may escape the cell", a.minX >= cellLeft - 1e-3f && a.maxX <= cellRight + 1e-3f)

        // B) The same pair pushed far apart — the gap compresses to the fixed break run.
        val farSpec = spec.copy(liners = listOf(Liner(id = "lnFar", startFromAftMm = 900f, lengthMm = 200f, odMm = 160f)))
        val farComps = wearStripComponentsFor(farSpec, null)
        val far = collectWearStripWindows(farComps, listOf("t1", "lnFar")).single()
        val farGap = far.segments.filterIsInstance<WearStripGapSeg>().single()
        assertTrue("a 400 mm gap must compress", !farGap.trueScale)
        val farScale = sharedWearStripWindowPtPerMm(listOf(far), listOf(inner))
        val b = windowSvg(farSpec, far, farScale, "AFT Taper + FWD Liner — combined, S-break gap")
        File(outDir, "b-taper-liner-break-gap.svg").writeText(b.svg)
        assertTrue(b.minX >= cellLeft - 1e-3f && b.maxX <= cellRight + 1e-3f)

        // C) A body elected on its own — a plain rectangle strip with neighbor stubs.
        val bodyWindow = collectWearStripWindows(comps, listOf("b1")).single()
        val bodyScale = sharedWearStripWindowPtPerMm(listOf(bodyWindow), listOf(inner))
        val cSvg = windowSvg(spec, bodyWindow, bodyScale, "Body #1 — own strip")
        File(outDir, "c-body-own-strip.svg").writeText(cSvg.svg)
        assertTrue(cSvg.minX >= cellLeft - 1e-3f && cSvg.maxX <= cellRight + 1e-3f)

        // D) The default liner-only sheet — the window must land exactly where the legacy
        //    per-liner strip did, which is the compatibility guarantee for every old document.
        val linerOnly = collectWearStripWindows(comps, null).single()
        val linerScale = sharedWearStripWindowPtPerMm(listOf(linerOnly), listOf(inner))
        val legacy = computeWearStripHorizontalLayout(cellLeft, cellRight, 200f, ptPerMmOverride = linerScale)
        val now = computeWearStripWindowLayout(cellLeft, cellRight, linerOnly.drawnWidthPt(linerScale), linerScale)
        assertEquals(legacy.linerLeftPt, now.linerLeftPt, 1e-3f)
        assertEquals(legacy.linerRightPt, now.linerRightPt, 1e-3f)
        val d = windowSvg(spec, linerOnly, linerScale, "AFT Liner — default liner-only strip")
        File(outDir, "d-liner-only.svg").writeText(d.svg)

        // E) A taper elected on its own — it carries the SAME anchor-from-SET dimension a liner
        //    strip does (wear is measured from a S.E.T.), so the title is name + anchor label.
        val sets = computeSetPositionsInMeasureSpace(computeOalWindow(spec), spec)
        val taperWindow = collectWearStripWindows(comps, listOf("t1")).single()
        assertEquals(listOf("t1"), taperWindow.components.map { it.id })
        val anchorLabel = buildSpanAnchorLabel(
            spec, taperWindow.startMm, taperWindow.endMm, sets, UnitSystem.INCHES,
        )
        assertTrue("a taper strip must print an anchor dimension", anchorLabel.contains("S.E.T."))
        val taperScale = sharedWearStripWindowPtPerMm(listOf(taperWindow), listOf(inner))
        val e = windowSvg(spec, taperWindow, taperScale, "AFT Taper — $anchorLabel")
        File(outDir, "e-taper-only-anchor-label.svg").writeText(e.svg)
        assertTrue("the anchor text must reach the drawing", e.svg.contains("S.E.T."))
        assertTrue(e.minX >= cellLeft - 1e-3f && e.maxX <= cellRight + 1e-3f)

        assertTrue(outDir.listFiles()!!.size >= 5)
    }

    @Test
    fun `a true gap's outline follows the shaft under it, step by step`() {
        // The gap between the taper's FWD end (500 mm, Ø140) and the liner (540 mm) runs over
        // body b2 at Ø140 — the drawn outline must report that diameter, not a bridged guess.
        val spec = spec()
        val verts = wearStripGapProfile(spec, 500f, 540f, samples = 8)
        assertTrue(verts.isNotEmpty())
        verts.filter { it.mm < 540f }.forEach { assertEquals(140f, it.diaMm, 1e-3f) }
        // The closing vertex sits on the liner's own edge, so it reports the liner OD — the
        // outline meets the next component's surface rather than stopping short of it.
        assertEquals(160f, verts.last().diaMm, 1e-3f)
        // …and a run over bare nothing reports no profile, so the caller bridges instead.
        assertTrue(wearStripGapProfile(ShaftSpec(), 0f, 100f).isEmpty())
    }
}
