package com.android.shaftschematic.geom

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.KeywayClocking
import com.android.shaftschematic.model.LinerAuthoredReference
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.hiddenKeywayHostIds
import com.android.shaftschematic.model.keywayAbsSpanMm
import com.android.shaftschematic.model.keywayClocking
import com.android.shaftschematic.model.secondaryKeywayHostIds
import com.android.shaftschematic.pdf.keywayClockingFooterNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Same-math SVG preview of keyway clocking, for visual (markup) review without an on-device
 * round trip: drives the REAL classification and geometry code — `keywayClocking()`,
 * `secondaryKeywayHostIds()`, `hiddenKeywayHostIds()`, `Body/Taper.keywaySilhouetteNotch`,
 * `fillPolygonMm`/`wallStartMm`/`wallEndMm`/`floorMm`, `KeywaySilhouetteSide.yFor`, and the
 * footer note `keywayClockingFooterNote` — and writes what the canvas/PDF draw sites would
 * produce as SVG into `app/build/reports/keyway-clocking-preview/`. The shaft outline and the
 * primary keyway's face-view slot are scenery redrawn with the same dimensions; the notch
 * polygon, its side (top/bottom), the sloped taper floor, and the 180° hidden-dash treatment
 * all come from the shared math the two real draw sites consume.
 *
 * Doubles as a smoke test: CW must land the notch on the BOTTOM edge, CCW on TOP, a
 * taper-hosted notch must carry a sloped floor, and the 180° mode must produce hidden ids and
 * no notch.
 */
class KeywaySilhouetteSvgPreviewTest {

    private val pxPerMm = 0.5f
    private val marginX = 40f

    // ── Scenario A: aft taper hosts the primary keyway, fwd body the secondary ──

    private val aftTaper = Taper(
        id = "taper", startFromAftMm = 0f, lengthMm = 300f,
        startDiaMm = 80f, endDiaMm = 120f,                       // SET at the aft face
        keywayWidthMm = 20f, keywayDepthMm = 10f, keywayLengthMm = 150f,
    )
    private val fwdBody = Body(
        id = "body", startFromAftMm = 300f, lengthMm = 1700f, diaMm = 120f,
        keywayWidthMm = 30f, keywayDepthMm = 15f, keywayLengthMm = 300f,
        keywayEnd = LinerAuthoredReference.FWD,                  // slot at the coupling end
    )
    private fun scenarioA(k180: Boolean = false, k90: Boolean = false, cw: Boolean = true) =
        ShaftSpec(
            overallLengthMm = 2000f,
            tapers = listOf(aftTaper), bodies = listOf(fwdBody),
            keyways180Apart = k180, keyways90Apart = k90, keyways90Cw = cw,
        )

    // ── Scenario B: aft body primary, fwd taper secondary (sloped silhouette) ──

    private val aftBody = Body(
        id = "body", startFromAftMm = 0f, lengthMm = 1700f, diaMm = 120f,
        keywayWidthMm = 30f, keywayDepthMm = 15f, keywayLengthMm = 300f,
        keywayEnd = LinerAuthoredReference.AFT,
    )
    private val fwdTaper = Taper(
        id = "taper", startFromAftMm = 1700f, lengthMm = 300f,
        startDiaMm = 120f, endDiaMm = 80f,                       // SET at the fwd face
        keywayWidthMm = 20f, keywayDepthMm = 10f, keywayLengthMm = 150f,
    )
    private fun scenarioB(cw: Boolean) = ShaftSpec(
        overallLengthMm = 2000f,
        tapers = listOf(fwdTaper), bodies = listOf(aftBody),
        keyways90Apart = true, keyways90Cw = cw,
    )

    // ── Minimal SVG writer ────────────────────────────────────────────────────

    private class Svg {
        val sb = StringBuilder()
        fun line(x0: Float, y0: Float, x1: Float, y1: Float, w: Float = 1f, dash: String? = null) {
            sb.append("""<line x1="$x0" y1="$y0" x2="$x1" y2="$y1" stroke="black" stroke-width="$w"""")
            if (dash != null) sb.append(""" stroke-dasharray="$dash"""")
            sb.append("/>\n")
        }
        fun poly(pts: List<Pair<Float, Float>>, fill: String = "white", stroke: String = "none") {
            sb.append("""<polygon points="${pts.joinToString(" ") { "${it.first},${it.second}" }}" fill="$fill" stroke="$stroke"/>""").append('\n')
        }
        fun arc(x0: Float, y0: Float, r: Float, x1: Float, y1: Float, sweep: Int, dash: String? = null) {
            sb.append("""<path d="M $x0 $y0 A $r $r 0 0 $sweep $x1 $y1" stroke="black" stroke-width="1" fill="none"""")
            if (dash != null) sb.append(""" stroke-dasharray="$dash"""")
            sb.append("/>\n")
        }
        fun text(x: Float, y: Float, s: String, size: Float = 11f, anchor: String = "start", color: String = "black") {
            sb.append("""<text x="$x" y="$y" font-size="$size" font-family="Helvetica, Arial, sans-serif" text-anchor="$anchor" fill="$color">$s</text>""").append('\n')
        }
        fun wrap(w: Float, h: Float): String =
            "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 $w $h\" width=\"$w\" height=\"$h\" style=\"background:white\">\n$sb</svg>\n"
    }

    /** Hidden-line dash, mirroring `ShaftRenderer.HIDDEN_DASH_ON/OFF` (6 on, 4 off). */
    private val hiddenDash = "6,4"

    private fun xAt(mm: Float) = marginX + mm * pxPerMm

    /** Shaft outline for either scenario: taper trapezoid + body rect + centerline. */
    private fun Svg.outline(spec: ShaftSpec, cy: Float) {
        spec.bodies.forEach { b ->
            val r = b.diaMm * 0.5f * pxPerMm
            val x0 = xAt(b.startFromAftMm); val x1 = xAt(b.startFromAftMm + b.lengthMm)
            line(x0, cy - r, x1, cy - r); line(x0, cy + r, x1, cy + r)
            line(x0, cy - r, x0, cy + r); line(x1, cy - r, x1, cy + r)
        }
        spec.tapers.forEach { t ->
            val r0 = t.startDiaMm * 0.5f * pxPerMm; val r1 = t.endDiaMm * 0.5f * pxPerMm
            val x0 = xAt(t.startFromAftMm); val x1 = xAt(t.startFromAftMm + t.lengthMm)
            line(x0, cy - r0, x1, cy - r1); line(x0, cy + r0, x1, cy + r1)
            line(x0, cy - r0, x0, cy + r0); line(x1, cy - r1, x1, cy + r1)
        }
        val end = xAt(spec.overallLengthMm)
        line(marginX - 8f, cy, end + 8f, cy, w = 0.4f, dash = "10,4")
    }

    /**
     * Face-view slot for a keyway (primary, or 180° far-side when [dashed]): two full-length
     * walls at ±width/2 around the centerline, mill semicircle at each closed end, white void
     * fill only when solid — the same construction `drawKeywaySlot` uses.
     */
    private fun Svg.faceSlot(spanLo: Float, spanHi: Float, widthMm: Float, cy: Float, dashed: Boolean, openAtLo: Boolean, openAtHi: Boolean) {
        val halfW = widthMm * 0.5f * pxPerMm
        val x0 = xAt(spanLo); val x1 = xAt(spanHi)
        val d = if (dashed) hiddenDash else null
        if (!dashed) poly(listOf(x0 to cy - halfW, x1 to cy - halfW, x1 to cy + halfW, x0 to cy + halfW))
        line(x0, cy - halfW, x1, cy - halfW, dash = d)
        line(x0, cy + halfW, x1, cy + halfW, dash = d)
        if (!openAtHi) arc(x1, cy - halfW, halfW, x1, cy + halfW, sweep = 1, dash = d)
        if (!openAtLo) arc(x0, cy + halfW, halfW, x0, cy - halfW, sweep = 1, dash = d)
    }

    /** The 90° silhouette notch, from the shared math — fill, then walls + floor, no surface line. */
    private fun Svg.notch(n: KeywaySilhouetteNotch, cy: Float) {
        poly(n.fillPolygonMm().map { p -> xAt(p.xMm) to n.side.yFor(cy, p.radiusMm, pxPerMm) })
        listOf(n.wallStartMm(), n.wallEndMm(), n.floorMm()).forEach { (a, b) ->
            line(xAt(a.xMm), n.side.yFor(cy, a.radiusMm, pxPerMm), xAt(b.xMm), n.side.yFor(cy, b.radiusMm, pxPerMm))
        }
    }

    private fun Svg.caption(cy: Float, title: String, note: String?) {
        text(marginX, cy - 52f, title, size = 12f)
        if (note != null) text(marginX, cy + 58f, "footer: “$note”", size = 10f, color = "#555")
    }

    // ── The preview ───────────────────────────────────────────────────────────

    @Test
    fun `render preview SVGs`() {
        val outDir = File("build/reports/keyway-clocking-preview").apply { mkdirs() }
        val sceneH = 150f
        val svg = Svg()
        var cy = 80f

        // 1) Scenario A, 90° CW: body slot (secondary) becomes a BOTTOM-edge notch.
        run {
            val spec = scenarioA(k90 = true, cw = true)
            assertEquals(KeywayClocking.DEG_90_CW, spec.keywayClocking())
            assertEquals(setOf("body"), spec.secondaryKeywayHostIds())
            assertTrue(spec.hiddenKeywayHostIds().isEmpty())
            val n = fwdBody.keywaySilhouetteNotch(spec.keywayClocking())
            assertNotNull("CW must produce a notch on the body", n)
            assertEquals(KeywaySilhouetteSide.BOTTOM, n!!.side)
            assertEquals(1700f, n.startMm, 1e-3f); assertEquals(2000f, n.endMm, 1e-3f)
            svg.outline(spec, cy)
            val (lo, hi) = aftTaper.keywayAbsSpanMm()!!
            svg.faceSlot(lo, hi, aftTaper.keywayWidthMm, cy, dashed = false, openAtLo = true, openAtHi = false)
            svg.notch(n, cy)
            svg.caption(cy, "A1 — 90° CW (viewed from aft): secondary keyway notched into the BOTTOM edge", keywayClockingFooterNote(spec))
        }

        // 2) Scenario A, 90° CCW: same notch, TOP edge.
        cy += sceneH
        run {
            val spec = scenarioA(k90 = true, cw = false)
            val n = fwdBody.keywaySilhouetteNotch(spec.keywayClocking())
            assertNotNull(n)
            assertEquals(KeywaySilhouetteSide.TOP, n!!.side)
            svg.outline(spec, cy)
            val (lo, hi) = aftTaper.keywayAbsSpanMm()!!
            svg.faceSlot(lo, hi, aftTaper.keywayWidthMm, cy, dashed = false, openAtLo = true, openAtHi = false)
            svg.notch(n, cy)
            svg.caption(cy, "A2 — 90° CCW: same secondary keyway, TOP edge", keywayClockingFooterNote(spec))
        }

        // 3) Scenario A, 180°: no notch — far-side slot drawn hidden-dashed, no void fill.
        cy += sceneH
        run {
            val spec = scenarioA(k180 = true)
            assertEquals(setOf("body"), spec.hiddenKeywayHostIds())
            assertNull("180° draws hidden slots, never notches", fwdBody.keywaySilhouetteNotch(spec.keywayClocking()))
            svg.outline(spec, cy)
            val (tLo, tHi) = aftTaper.keywayAbsSpanMm()!!
            svg.faceSlot(tLo, tHi, aftTaper.keywayWidthMm, cy, dashed = false, openAtLo = true, openAtHi = false)
            val (bLo, bHi) = fwdBody.keywayAbsSpanMm()!!
            svg.faceSlot(bLo, bHi, fwdBody.keywayWidthMm, cy, dashed = true, openAtLo = false, openAtHi = true)
            svg.caption(cy, "A3 — 180° apart (existing): far-side slot hidden-dashed for comparison", keywayClockingFooterNote(spec))
        }

        // 4) Scenario B, 90° CW: taper-hosted secondary — floor runs parallel to the slope.
        cy += sceneH
        run {
            val spec = scenarioB(cw = true)
            assertEquals(setOf("taper"), spec.secondaryKeywayHostIds())
            val n = fwdTaper.keywaySilhouetteNotch(spec.keywayClocking())
            assertNotNull(n)
            assertEquals(KeywaySilhouetteSide.BOTTOM, n!!.side)
            assertTrue(
                "a taper-hosted notch must carry a sloped floor",
                kotlin.math.abs(n.floorRadiusStartMm - n.floorRadiusEndMm) > 1f,
            )
            assertEquals(
                "floor stays parallel to the surface",
                n.surfaceRadiusStartMm - n.surfaceRadiusEndMm,
                n.floorRadiusStartMm - n.floorRadiusEndMm, 1e-3f,
            )
            svg.outline(spec, cy)
            val (lo, hi) = aftBody.keywayAbsSpanMm()!!
            // Offset 0 from the AFT end face: the slot is open at the shaft's aft end.
            svg.faceSlot(lo, hi, aftBody.keywayWidthMm, cy, dashed = false, openAtLo = true, openAtHi = false)
            svg.notch(n, cy)
            svg.caption(cy, "B — 90° CW on a fwd taper: notch floor parallel to the sloped silhouette", keywayClockingFooterNote(spec))
        }

        File(outDir, "keyway-clocking-scenes.svg").writeText(svg.wrap(marginX * 2f + 2000f * pxPerMm, cy + 90f))
        assertTrue(File(outDir, "keyway-clocking-scenes.svg").length() > 0)
    }
}
