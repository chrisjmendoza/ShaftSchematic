package com.android.shaftschematic.geom

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.LinerAuthoredReference
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.keywayAbsSpanMm
import com.android.shaftschematic.model.maxOuterDiaMm
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/**
 * Same-math SVG preview of keyway width vs the "Shaft height" slider, for visual (markup) review
 * without an on-device round trip.
 *
 * Drives the REAL layout code the schematic composer runs — `defaultVisualScale`,
 * `exaggeratedProfileScale`, `profileFeatureSpans`, `solveMaxProfileScale`,
 * `buildCompressedProfileXMap` — and the REAL slot math `drawnKeywayHalfWidthPx` /
 * `minKeywaySlotLenPx`, then writes what the draw sites produce into
 * `app/build/reports/keyway-width-scale-preview/`. Only the SVG plumbing is local; the shaft
 * outline and slot construction are redrawn from the same numbers `drawKeywaySlotPdf` uses.
 *
 * Each height renders the slot twice — sized off the AXIAL scale (what pinned the width to the
 * page and left keyways behind when the height moved) and off the DIAMETER scale (current) — so
 * the difference is visible side by side.
 *
 * Doubles as a smoke test: the diameter-scaled slot must hold a constant fraction of the drawn
 * shaft height across slider positions, and the axial-scaled one must not.
 */
class KeywayWidthScaleSvgPreviewTest {

    // A 20 ft × 8" shaft: aft taper with the propeller keyway, one long body carrying a
    // coupling keyway at the fwd end. Long enough that the x map compresses hard.
    private val taper = Taper(
        id = "t1", startFromAftMm = 0f, lengthMm = 381f,       // 15"
        startDiaMm = 127f, endDiaMm = 203.2f,                  // SET at the aft face
        keywayWidthMm = 38.1f, keywayDepthMm = 19.05f, keywayLengthMm = 254f,
    )
    private val body = Body(
        id = "b1", startFromAftMm = 381f, lengthMm = 5715f, diaMm = 203.2f,
        keywayWidthMm = 38.1f, keywayDepthMm = 19.05f, keywayLengthMm = 305f,
        keywayEnd = LinerAuthoredReference.FWD,
    )
    private val spec = ShaftSpec(overallLengthMm = 6096f, tapers = listOf(taper), bodies = listOf(body))

    private val contentLeft = 40f
    private val contentRight = 740f     // 700 pt of content, a landscape sheet's drawing band
    private val strokePt = 1.25f        // OUTLINE_PT_BASE at 100% line thickness

    // ── Minimal SVG writer ────────────────────────────────────────────────────

    private class Svg {
        val sb = StringBuilder()
        fun line(x0: Float, y0: Float, x1: Float, y1: Float, w: Float = 1f, color: String = "black") {
            sb.append("""<line x1="$x0" y1="$y0" x2="$x1" y2="$y1" stroke="$color" stroke-width="$w"/>""").append('\n')
        }
        fun rect(x0: Float, y0: Float, x1: Float, y1: Float, fill: String) {
            sb.append("""<rect x="$x0" y="$y0" width="${x1 - x0}" height="${y1 - y0}" fill="$fill"/>""").append('\n')
        }
        fun ellipseArc(x0: Float, y0: Float, rx: Float, ry: Float, x1: Float, y1: Float, sweep: Int, w: Float, color: String = "black") {
            sb.append("""<path d="M $x0 $y0 A $rx $ry 0 0 $sweep $x1 $y1" stroke="$color" stroke-width="$w" fill="none"/>""").append('\n')
        }
        fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float, fill: String = "none", stroke: String = "none", w: Float = 1f) {
            sb.append("""<ellipse cx="$cx" cy="$cy" rx="$rx" ry="$ry" fill="$fill" stroke="$stroke" stroke-width="$w"/>""").append('\n')
        }
        fun text(x: Float, y: Float, s: String, size: Float = 11f, color: String = "black") {
            sb.append("""<text x="$x" y="$y" font-size="$size" font-family="Helvetica, Arial, sans-serif" fill="$color">$s</text>""").append('\n')
        }
        fun wrap(w: Float, h: Float): String =
            "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 $w $h\" width=\"$w\" height=\"$h\" style=\"background:white\">\n$sb</svg>\n"
    }

    /** The schematic composer's own solve, for one "Shaft height" slider position. */
    private class Layout(spec: ShaftSpec, heightFrac: Float, contentLeft: Float, contentRight: Float) {
        val maxDiaMm = spec.maxOuterDiaMm().coerceAtLeast(1f)
        private val features = profileFeatureSpans(
            spec,
            linerFloorPt = SCHEMATIC_MIN_LINER_PT,
            threadFloorPt = SCHEMATIC_MIN_THREAD_PT,
            linerMinFracOfTrue = 0f,
        )
        val diaPtPerMm = solveMaxProfileScale(
            windowStartMm = 0f, windowEndMm = spec.overallLengthMm,
            features = features, contentWidth = contentRight - contentLeft,
            scaleHi = exaggeratedProfileScale(
                baseScale = defaultVisualScale(maxDiaMm),
                heightFrac = heightFrac,
                budgetCapPt = 400f,
                maxDiaMm = maxDiaMm,
            ),
            gapMinWidthPt = SCHEMATIC_MIN_BODY_RUN_PT,
        )
        private val map = buildCompressedProfileXMap(
            windowStartMm = 0f, windowEndMm = spec.overallLengthMm,
            features = features, contentLeft = contentLeft, contentRight = contentRight,
            diaPtPerMm = diaPtPerMm, gapMinWidthPt = SCHEMATIC_MIN_BODY_RUN_PT,
        )
        fun xAt(mm: Float) = map.xAt(mm)
        fun rPt(diaMm: Float) = diaMm * 0.5f * diaPtPerMm
        val shaftHeightPt get() = maxDiaMm * diaPtPerMm
    }

    /**
     * One plan-view slot, built exactly as `drawKeywaySlotPdf` builds it: x from the AXIAL
     * scale, y from [widthScale] — the diameter scale (current) or, for the comparison row, the
     * axial scale again (what pinned the slot's width to the page). Every round part is the
     * ellipse those two terms make.
     */
    private fun Svg.slot(
        refX: Float, dir: Float, axialPtPerMm: Float, widthScale: Float, hostRadiusPt: Float,
        widthMm: Float, offsetMm: Float, lengthMm: Float, cy: Float, color: String,
        spooned: Boolean = false,
    ): Float {
        val halfW = widthMm * axialPtPerMm / 2f
        val halfH = drawnKeywayHalfWidthPx(
            trueHalfWidthPx = widthMm * widthScale / 2f,
            hostRadiusPx = hostRadiusPt,
            minWidthPx = MIN_KEYWAY_WIDTH_PT,
            strokeWidthPx = strokePt,
        )
        val yScale = if (halfW > 0f) halfH / halfW else 1f
        val isOpen = offsetMm < 0.01f
        val setX = refX + dir * offsetMm * axialPtPerMm
        val letX = setX + dir * maxOf(lengthMm * axialPtPerMm, minKeywaySlotLenPx(halfW, isOpen))
        val letArcCx = letX - dir * halfW
        val lineLeft = minOf(setX, letArcCx)
        val lineRight = maxOf(setX, letArcCx)
        val bowl = if (spooned && isOpen && halfW > 0f) keywaySpoonBowl(letX, dir, halfW) else null

        rect(lineLeft, cy - halfH, lineRight, cy + halfH, "white")
        if (bowl != null) ellipse(bowl.cx, cy, bowl.radius, bowl.radius * yScale, fill = "white")
        line(lineLeft, cy - halfH, lineRight, cy - halfH, w = strokePt, color = color)
        line(lineLeft, cy + halfH, lineRight, cy + halfH, w = strokePt, color = color)
        // Mill arc at the closed (LET) end — the half on the LET side of its centre.
        ellipseArc(
            letArcCx, cy - halfH, halfW, halfH, letArcCx, cy + halfH,
            sweep = if (dir > 0f) 1 else 0, w = strokePt, color = color,
        )
        if (bowl != null) {
            ellipse(bowl.cx, cy, bowl.radius, bowl.radius * yScale, stroke = color, w = strokePt)
        }
        return halfH
    }

    private fun Svg.outline(L: Layout, cy: Float) {
        val tx0 = L.xAt(0f); val tx1 = L.xAt(taper.lengthMm)
        val r0 = L.rPt(taper.startDiaMm); val r1 = L.rPt(taper.endDiaMm)
        line(tx0, cy - r0, tx1, cy - r1); line(tx0, cy + r0, tx1, cy + r1)
        line(tx0, cy - r0, tx0, cy + r0)
        val bx0 = L.xAt(body.startFromAftMm); val bx1 = L.xAt(body.startFromAftMm + body.lengthMm)
        val rb = L.rPt(body.diaMm)
        line(bx0, cy - rb, bx1, cy - rb); line(bx0, cy + rb, bx1, cy + rb)
        line(bx1, cy - rb, bx1, cy + rb)
        line(contentLeft - 8f, cy, contentRight + 8f, cy, w = 0.4f, color = "#999")
    }

    /**
     * What one scene measured: the taper slot's drawn WIDTH as a fraction of the drawn shaft
     * height, and the spoon bowl's drawn AXIAL half-extent. The first must hold true proportion
     * across the slider; the second must not grow with it.
     */
    private data class Measured(val widthFracOfShaft: Float, val bowlRadiusPt: Float)

    /** Draws one scene and returns what it measured. */
    private fun Svg.scene(L: Layout, cy: Float, useDiaScale: Boolean, title: String): Measured {
        outline(L, cy)
        val color = if (useDiaScale) "black" else "#c0392b"

        // Aft taper keyway: open at the SET (aft) face, running fwd.
        val tx0 = L.xAt(0f); val tx1 = L.xAt(taper.lengthMm)
        val taperAxial = abs(tx1 - tx0) / taper.lengthMm
        val taperHalf = slot(
            refX = tx0, dir = 1f, axialPtPerMm = taperAxial,
            widthScale = if (useDiaScale) L.diaPtPerMm else taperAxial,
            hostRadiusPt = minOf(L.rPt(taper.startDiaMm), L.rPt(taper.endDiaMm)),
            widthMm = taper.keywayWidthMm, offsetMm = taper.keywayOffsetFromSetMm,
            lengthMm = taper.keywayLengthMm, cy = cy, color = color,
            spooned = true,
        )

        // Fwd body keyway: open at the FWD face, running aft. Its window pins at true scale, so
        // its own mapped span is the local axial scale.
        val span = body.keywayAbsSpanMm()!!
        val sLo = L.xAt(span.loMm); val sHi = L.xAt(span.hiMm)
        val bodyAxial = abs(sHi - sLo) / (span.hiMm - span.loMm)
        slot(
            refX = sHi, dir = -1f, axialPtPerMm = bodyAxial,
            widthScale = if (useDiaScale) L.diaPtPerMm else bodyAxial,
            hostRadiusPt = L.rPt(body.diaMm),
            widthMm = body.keywayWidthMm, offsetMm = body.keywayOffsetFromEndMm,
            lengthMm = body.keywayLengthMm, cy = cy, color = color,
        )

        val frac = taperHalf * 2f / L.shaftHeightPt
        val bowlRadius = keywaySpoonBowl(tx0, 1f, taper.keywayWidthMm * taperAxial / 2f).radius
        text(contentLeft, cy - L.shaftHeightPt / 2f - 14f, title, size = 12f)
        text(
            contentLeft, cy + L.shaftHeightPt / 2f + 16f,
            "shaft ${"%.0f".format(L.shaftHeightPt)} pt tall · taper keyway drawn " +
                "${"%.1f".format(taperHalf * 2f)} pt = ${"%.1f".format(frac * 100f)}% of it " +
                "(true 18.8%) · spoon bowl ${"%.1f".format(bowlRadius * 2f)} pt along the axis",
            size = 10f, color = "#555",
        )
        return Measured(frac, bowlRadius)
    }

    @Test
    fun `render preview SVG`() {
        val outDir = File("build/reports/keyway-width-scale-preview").apply { mkdirs() }
        val svg = Svg()
        var cy = 90f
        val m = mutableMapOf<String, Measured>()

        listOf("100%" to 1.0f, "200%" to 2.0f).forEach { (label, frac) ->
            val L = Layout(spec, frac, contentLeft, contentRight)
            m["axial-$label"] = svg.scene(L, cy, useDiaScale = false, title = "Shaft height $label — width off the AXIAL scale (red)")
            cy += 150f
            m["dia-$label"] = svg.scene(L, cy, useDiaScale = true, title = "Shaft height $label — width off the DIAMETER scale (current)")
            cy += 190f
        }

        File(outDir, "keyway-width-scale.svg").writeText(svg.wrap(contentRight + 40f, cy))

        // The diameter-scaled slot holds true proportion at every slider position …
        val trueFrac = taper.keywayWidthMm / spec.maxOuterDiaMm()
        assertTrue(abs(m.getValue("dia-100%").widthFracOfShaft - trueFrac) < 0.01f)
        assertTrue(abs(m.getValue("dia-200%").widthFracOfShaft - trueFrac) < 0.01f)
        // … while the axial-scaled one shrinks against the shaft as the height grows.
        assertTrue(
            "an axial-scaled slot loses ground when the height slider moves",
            m.getValue("axial-200%").widthFracOfShaft < m.getValue("axial-100%").widthFracOfShaft - 0.01f,
        )
        // The spoon bowl rides the AXIAL scale, so raising the height must never inflate it
        // along the shaft — drawn as a true circle at the transverse scale it grew until it
        // swallowed its own slot (on-device report).
        assertTrue(
            "the spoon bowl must not grow along the axis with the height slider",
            m.getValue("dia-200%").bowlRadiusPt <= m.getValue("dia-100%").bowlRadiusPt + 1e-3f,
        )
        assertTrue(File(outDir, "keyway-width-scale.svg").length() > 0)
    }
}
