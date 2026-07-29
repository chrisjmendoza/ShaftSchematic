package com.android.shaftschematic.pdf

import com.android.shaftschematic.geom.DiaCalloutStation
import com.android.shaftschematic.geom.PlacedDiaCallout
import com.android.shaftschematic.geom.planDiaCallouts
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * Same-math SVG previews of the measured-Ø callouts, for visual (markup) review without an
 * on-device round trip: drives the REAL layout code — `planDiaCallouts`/`finish`,
 * `computeWearStripHorizontalLayout`, `computeWearStripInnerLayout(diaBandPt)`,
 * `formatDiaWithUnit` — and writes what `WearPdfComposer` would draw as SVG into
 * `app/build/reports/wear-dia-preview/`. Only text width is approximated (no
 * `Paint.measureText` on the JVM; a fixed per-char factor stands in), which can shift
 * label spreads by a point or two but exercises identical placement rules.
 *
 * Doubles as a smoke test: the shop-sketch scenario must fit one row uncompressed, and the
 * crowded scenario must use the two-row stagger.
 */
class WearDiaCalloutSvgPreviewTest {

    private val textH = 8f
    private val rowGap = 3f
    private val minGap = 5f
    private val tickOver = 2f
    private val headroom = WEAR_STRIP_LABEL_HEADROOM_PT

    /** Stand-in for Paint.measureText at 8pt sans — close enough for preview spreads. */
    private fun labelW(s: String): Float = s.length * textH * 0.58f

    private fun fmt(f: Float): String = String.format(Locale.US, "%.2f", f)

    private data class Svg(val sb: StringBuilder = StringBuilder()) {
        fun line(x0: Float, y0: Float, x1: Float, y1: Float, w: Float = 0.8f, color: String = "black", dash: String? = null) {
            sb.append("""<line x1="${x0}" y1="${y0}" x2="${x1}" y2="${y1}" stroke="$color" stroke-width="$w"""")
            if (dash != null) sb.append(""" stroke-dasharray="$dash"""")
            sb.append("/>\n")
        }
        fun rect(x: Float, y: Float, w: Float, h: Float, stroke: String = "black", fill: String = "none", sw: Float = 1.2f) {
            sb.append("""<rect x="$x" y="$y" width="$w" height="$h" stroke="$stroke" fill="$fill" stroke-width="$sw"/>""").append('\n')
        }
        fun text(x: Float, y: Float, s: String, size: Float = 8f, anchor: String = "middle", color: String = "black") {
            sb.append("""<text x="$x" y="$y" font-size="$size" font-family="Helvetica, Arial, sans-serif" text-anchor="$anchor" fill="$color">$s</text>""").append('\n')
        }
        fun wrap(w: Float, h: Float): String =
            "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 $w $h\" width=\"${w * 1.6f}\" height=\"${h * 1.6f}\" style=\"background:white\">\n$sb</svg>\n"
    }

    private fun Svg.drawCallouts(placed: List<PlacedDiaCallout>) {
        placed.forEach { p ->
            for (s in 0 until p.leader.size - 1) {
                line(p.leader[s].x, p.leader[s].y, p.leader[s + 1].x, p.leader[s + 1].y, w = 0.7f)
            }
            // Baseline ≈ top + 0.8·textH (SVG has no fontMetrics; visually equivalent).
            text(p.labelCx, p.labelTopY + textH * 0.8f, p.label)
        }
    }

    /** One liner detail strip, faithful to drawWearDetailStrip's callout geometry. */
    private fun stripSvg(
        name: String,
        stripLeft: Float,
        stripRight: Float,
        stripTop: Float,
        stripBottom: Float,
        linerLenMm: Float,
        readingsAxialToDiaMm: List<Pair<Float, Float>>,
        minDiaBand: Pair<Float, Float>? = null,  // wear band startMm..endMm for context
        title: String,
    ): Pair<String, Boolean> {
        val svg = Svg()
        val h = computeWearStripHorizontalLayout(stripLeft, stripRight, linerLenMm)
        fun xAt(mm: Float) = h.linerLeftPt + mm * h.ptPerMm

        val titleH = 9f
        val stations = readingsAxialToDiaMm.map { (ax, dia) ->
            val label = formatDiaWithUnit(dia.toDouble(), "INCHES")
            DiaCalloutStation("r$ax", xAt(ax.coerceIn(0f, linerLenMm)), label, labelW(label))
        }
        val plan = planDiaCallouts(stations, stripLeft + 2f, stripRight - 2f, minGap)
        val diaBandPt = plan.labelsHeightPt(textH, rowGap) + 2f
        val inner = computeWearStripInnerLayout(stripTop, stripBottom, titleHeightPt = titleH, diaBandPt = diaBandPt)

        val cy = (inner.cylTop + inner.cylBottom) / 2f
        val r = (inner.cylBottom - inner.cylTop) / 2f
        val top = cy - r
        val bot = cy + r

        // Strip frame (context only) + cylinder + stubs
        svg.rect(stripLeft, stripTop, stripRight - stripLeft, stripBottom - stripTop, stroke = "#bbb", sw = 0.5f)
        svg.rect(h.linerLeftPt, top, h.linerRightPt - h.linerLeftPt, bot - top)
        svg.line(h.linerLeftPt - h.stubWidthPt, cy - r * 0.8f, h.linerLeftPt, cy - r * 0.8f)
        svg.line(h.linerLeftPt - h.stubWidthPt, cy + r * 0.8f, h.linerLeftPt, cy + r * 0.8f)
        svg.line(h.linerRightPt, cy - r * 0.8f, h.linerRightPt + h.stubWidthPt, cy - r * 0.8f)
        svg.line(h.linerRightPt, cy + r * 0.8f, h.linerRightPt + h.stubWidthPt, cy + r * 0.8f)
        // Rail line placeholder (real rail spans omitted — not what's under review)
        svg.line(h.linerLeftPt, inner.railY, h.linerRightPt, inner.railY, w = 0.5f, color = "#999", dash = "3,2")

        // Wear band context + min-Ø label position
        minDiaBand?.let { (s, e) ->
            svg.rect(xAt(s), top, xAt(e) - xAt(s), bot - top, stroke = "#c00", fill = "#fdd", sw = 0.8f)
            svg.text((xAt(s) + xAt(e)) / 2f, bot + textH, "⌀9.66\"", color = "#c00")
        }

        // Witness ticks + callouts — the feature under review
        plan.stations.forEach { s ->
            svg.line(s.stationX, top - tickOver, s.stationX, bot + tickOver, w = 0.6f, color = "#333")
        }
        val placed = plan.finish(
            row0Top = inner.cylBottom + headroom,
            labelTextHeight = textH,
            rowGap = rowGap,
            surfaceYAt = { bot },
            leaderStartGap = tickOver + 1f,
        )
        svg.drawCallouts(placed)
        svg.text(stripLeft, stripBottom - 2f, title, anchor = "start")
        return svg.wrap(stripRight - stripLeft + 20f, stripBottom - stripTop + 10f) to plan.compressed
    }

    @Test
    fun `render preview SVGs`() {
        val outDir = File("build/reports/wear-dia-preview").apply { mkdirs() }

        // A) The shop-sketch case: full-width strip, 4 worn readings + nominal at the edge.
        val (svgA, compressedA) = stripSvg(
            name = "sketch",
            stripLeft = 36f, stripRight = 756f, stripTop = 10f, stripBottom = 150f,
            linerLenMm = 300f,
            readingsAxialToDiaMm = listOf(
                20f to 9.755f * 25.4f,
                90f to 9.66f * 25.4f,
                150f to 9.68f * 25.4f,
                210f to 9.753f * 25.4f,
                292f to 10.000f * 25.4f,
            ),
            minDiaBand = 70f to 170f,
            title = "AFT LINER — 110 FROM AFT S.E.T.",
        )
        File(outDir, "a-sketch-fullwidth.svg").writeText(svgA)
        assertFalse("sketch case should not compress", compressedA)

        // B) Clustered readings on a half-width (GRID) cell: the row still fits the strip
        // width, so labels FAN OUT on one row with slanted leaders — the hand sketch's look.
        val (svgB, compressedB) = stripSvg(
            name = "crowded",
            stripLeft = 36f, stripRight = 385f, stripTop = 10f, stripBottom = 150f,
            linerLenMm = 250f,
            readingsAxialToDiaMm = (0 until 8).map { i ->
                (70f + i * 12f) to (9.6f + 0.037f * i) * 25.4f
            },
            title = "MID LINER — 300 FROM AFT S.E.T.",
        )
        File(outDir, "b-crowded-gridwidth.svg").writeText(svgB)
        assertFalse("clustered case should not compress", compressedB)

        // D) So many readings the row physically can't fit the grid cell → the two-row
        // stagger with dogleg drops (row-1 leaders elbow above the row-0 band).
        val (svgD, compressedD) = stripSvg(
            name = "stagger",
            stripLeft = 36f, stripRight = 385f, stripTop = 10f, stripBottom = 150f,
            linerLenMm = 250f,
            readingsAxialToDiaMm = (0 until 14).map { i ->
                (15f + i * 16f) to (9.6f + 0.019f * i) * 25.4f
            },
            title = "MID LINER — 300 FROM AFT S.E.T. (stagger stress)",
        )
        File(outDir, "d-stagger-gridwidth.svg").writeText(svgD)
        assertFalse("stagger case should not compress", compressedD)

        // C) Main-profile band: body/taper readings under the whole-shaft profile.
        run {
            val svg = Svg()
            val left = 36f; val right = 756f
            val cy = 90f
            val bodyR = 24f; val taperR0 = 24f; val taperR1 = 34f
            // Simplified shaft: body 36..500, taper 500..700 (widening fwd)
            svg.rect(left, cy - bodyR, 500f - left, bodyR * 2f)
            svg.line(500f, cy - taperR0, 700f, cy - taperR1); svg.line(500f, cy + taperR0, 700f, cy + taperR1)
            svg.line(700f, cy - taperR1, 700f, cy + taperR1)
            val namesBottom = cy + bodyR + 26f
            svg.text(left, namesBottom - 8f, "← AFT", anchor = "start"); svg.text(right, namesBottom - 8f, "FWD →", anchor = "end")

            data class R(val x: Float, val dia: Float, val surfR: Float)
            val readings = listOf(
                R(120f, 9.995f * 25.4f, bodyR), R(260f, 9.97f * 25.4f, bodyR),
                R(300f, 9.975f * 25.4f, bodyR), R(620f, 11.24f * 25.4f, taperR0 + (taperR1 - taperR0) * 0.6f),
            )
            val stations = readings.map { r ->
                val label = formatDiaWithUnit(r.dia.toDouble(), "INCHES")
                DiaCalloutStation("p${r.x}", r.x, label, labelW(label))
            }
            val plan = planDiaCallouts(stations, left, right, minGap)
            val placed = plan.finish(
                row0Top = namesBottom + 2f,
                labelTextHeight = textH,
                rowGap = rowGap,
                surfaceYAt = { i -> cy + readings.first { r -> "p${r.x}" == plan.stations[i].key }.surfR },
                leaderStartGap = 2f,
            )
            svg.drawCallouts(placed)
            File(outDir, "c-profile-band.svg").writeText(svg.wrap(right - left + 40f, 190f))
            assertFalse(plan.compressed)
        }

        assertTrue(outDir.listFiles()!!.size >= 3)
    }
}
