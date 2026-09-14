package com.android.shaftschematic.geom

import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.maxOuterDiaMm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Same-math SVG preview of the "Liner & taper compression" control BEFORE and AFTER the
 * tapers were coupled to it, for visual (markup) review without an on-device round trip.
 *
 * Drives the REAL layout code the composers run — `defaultVisualScale`,
 * `exaggeratedProfileScale`, `profileFeatureSpans`, `solveMaxProfileScale`,
 * `buildCompressedProfileXMap` — at three slider positions, and writes the result to
 * `app/build/reports/taper-liner-compression-preview/`. Only the SVG plumbing is local; every
 * span's drawn width comes out of the same x map the sheet uses.
 *
 * BEFORE = tapers pinned at [PROFILE_TAPER_MIN_FRAC_OF_TRUE] whatever the slider said;
 * AFTER = tapers ride the request with the liners ([taperMinFracOfTrue]).
 *
 * Doubles as a smoke test on the whole stack: the coupling may never shorten a taper, it must
 * be a no-op at the stored default, and asking for proportional length must land the two
 * measured kinds on the same fraction of true length.
 */
class TaperLinerCompressionSvgPreviewTest {

    // A 25 ft × 8" shaft with two very different tapers (19.5" aft, 11.5" fwd) and three
    // 16" liners — long enough that the x map compresses hard, which is when the two kinds'
    // proportions are visibly different.
    private val spec = ShaftSpec(
        overallLengthMm = 7620f,
        tapers = listOf(
            Taper(id = "t_aft", startFromAftMm = 0f, lengthMm = 495f, startDiaMm = 127f, endDiaMm = 203.2f),
            Taper(id = "t_fwd", startFromAftMm = 7328f, lengthMm = 292f, startDiaMm = 203.2f, endDiaMm = 152.4f),
        ),
        liners = listOf(
            Liner(id = "l1", startFromAftMm = 1500f, lengthMm = 406f, odMm = 209f),
            Liner(id = "l2", startFromAftMm = 3800f, lengthMm = 406f, odMm = 209f),
            Liner(id = "l3", startFromAftMm = 6000f, lengthMm = 406f, odMm = 209f),
        ),
    )

    private val contentLeft = 40f
    private val contentRight = 740f  // 700 pt of content — a landscape sheet's drawing band

    /** The slider positions the preview renders, as the compression VALUE the UI stores. */
    private val settings = listOf(
        Triple(1.0f, "100%", "compression 100% — the stored default (request 0)"),
        Triple(0.5f, "50%", "compression 50% (request 0.5 — under the taper baseline)"),
        Triple(0.2f, "20%", "compression 20% (request 0.8 — the tapers start moving)"),
        Triple(0.0f, "0%", "compression 0% — \"Keep liners and tapers proportional\""),
    )

    // ── The composer's own solve, for one request and one taper rule ──────────

    /** The two floor sets in the app: the writable sheet floors and the schematic's lean ones. */
    private data class Floors(val name: String, val liner: Float, val thread: Float, val gap: Float)

    private val writableFloors = Floors("Runout / consolidated sheet", PROFILE_MIN_LINER_PT, PROFILE_MIN_THREAD_PT, PROFILE_MIN_BODY_RUN_PT)
    private val leanFloors = Floors("Schematic", SCHEMATIC_MIN_LINER_PT, SCHEMATIC_MIN_THREAD_PT, SCHEMATIC_MIN_BODY_RUN_PT)

    private class Layout(
        spec: ShaftSpec,
        request: Float,
        coupled: Boolean,
        floors: Floors,
        left: Float,
        right: Float,
    ) {
        val maxDiaMm = spec.maxOuterDiaMm().coerceAtLeast(1f)
        private val features: List<ProfileFeatureSpan> = if (coupled) {
            profileFeatureSpans(
                spec,
                linerFloorPt = floors.liner,
                threadFloorPt = floors.thread,
                linerMinFracOfTrue = request,
            )
        } else {
            // The pre-coupling builder: tapers at the flat baseline whatever the slider said.
            spec.tapers.map {
                ProfileFeatureSpan(
                    it.startFromAftMm, it.startFromAftMm + it.lengthMm, 0f,
                    minWidthFracOfTrue = PROFILE_TAPER_MIN_FRAC_OF_TRUE,
                )
            } + spec.liners.map {
                ProfileFeatureSpan(
                    it.startFromAftMm, it.startFromAftMm + it.lengthMm, floors.liner,
                    minWidthFracOfTrue = request,
                )
            }
        }
        val diaPtPerMm = solveMaxProfileScale(
            windowStartMm = 0f, windowEndMm = spec.overallLengthMm,
            features = features, contentWidth = right - left,
            scaleHi = exaggeratedProfileScale(
                baseScale = defaultVisualScale(maxDiaMm),
                heightFrac = 1f, budgetCapPt = 400f, maxDiaMm = maxDiaMm,
            ),
            gapMinWidthPt = floors.gap,
        )
        private val map = buildCompressedProfileXMap(
            windowStartMm = 0f, windowEndMm = spec.overallLengthMm,
            features = features, contentLeft = left, contentRight = right,
            diaPtPerMm = diaPtPerMm,
            gapMinWidthPt = floors.gap,
        )
        fun xAt(mm: Float) = map.xAt(mm)
        fun rPt(diaMm: Float) = diaMm * 0.5f * diaPtPerMm
        /** Drawn fraction of the span's TRUE width — the number the readout reports. */
        fun keptFrac(startMm: Float, lenMm: Float) =
            (xAt(startMm + lenMm) - xAt(startMm)) / (lenMm * diaPtPerMm)
    }

    // ── Minimal SVG writer ────────────────────────────────────────────────────

    private class Svg {
        val sb = StringBuilder()
        fun line(x0: Float, y0: Float, x1: Float, y1: Float, w: Float = 1f, color: String = "#111") {
            sb.append("""<line x1="$x0" y1="$y0" x2="$x1" y2="$y1" stroke="$color" stroke-width="$w"/>""").append('\n')
        }
        fun rect(x0: Float, y0: Float, x1: Float, y1: Float, fill: String, stroke: String = "none", w: Float = 1f) {
            sb.append(
                """<rect x="$x0" y="$y0" width="${x1 - x0}" height="${y1 - y0}" fill="$fill" stroke="$stroke" stroke-width="$w"/>"""
            ).append('\n')
        }
        fun poly(points: String, fill: String, stroke: String = "#111", w: Float = 1f) {
            sb.append("""<polygon points="$points" fill="$fill" stroke="$stroke" stroke-width="$w"/>""").append('\n')
        }
        fun text(x: Float, y: Float, s: String, size: Float = 10f, color: String = "#111", anchor: String = "start", weight: String = "normal") {
            sb.append(
                """<text x="$x" y="$y" font-size="$size" text-anchor="$anchor" font-weight="$weight" font-family="Helvetica, Arial, sans-serif" fill="$color">$s</text>"""
            ).append('\n')
        }
        fun wrap(w: Float, h: Float): String =
            "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 $w $h\" width=\"$w\" height=\"$h\" style=\"background:white\">\n$sb</svg>\n"
    }

    /** One shaft profile, drawn from [l]'s x map at baseline y [cy]. */
    private fun drawProfile(svg: Svg, l: Layout, cy: Float) {
        val r = l.rPt(203.2f)
        // Bare shaft silhouette.
        svg.rect(l.xAt(0f), cy - r, l.xAt(spec.overallLengthMm), cy + r, fill = "#fff", stroke = "#111", w = 1f)
        // Tapers — trapezoids at their real end diameters, so a squeezed taper reads as one.
        spec.tapers.forEach { t ->
            val x0 = l.xAt(t.startFromAftMm)
            val x1 = l.xAt(t.startFromAftMm + t.lengthMm)
            val r0 = l.rPt(t.startDiaMm)
            val r1 = l.rPt(t.endDiaMm)
            svg.poly(
                "$x0,${cy - r0} $x1,${cy - r1} $x1,${cy + r1} $x0,${cy + r0}",
                fill = "#cfe3f7",
            )
            val pct = (l.keptFrac(t.startFromAftMm, t.lengthMm) * 100).toInt()
            svg.text((x0 + x1) / 2f, cy + r + 13f, "$pct%", size = 9f, color = "#1c4e80", anchor = "middle")
        }
        // Liners — proud of the shaft OD, the way they sit on the sheet.
        spec.liners.forEach { li ->
            val x0 = l.xAt(li.startFromAftMm)
            val x1 = l.xAt(li.startFromAftMm + li.lengthMm)
            val rl = l.rPt(li.odMm)
            svg.rect(x0, cy - rl, x1, cy + rl, fill = "#d8d8d8", stroke = "#111", w = 1f)
            val pct = (l.keptFrac(li.startFromAftMm, li.lengthMm) * 100).toInt()
            svg.text((x0 + x1) / 2f, cy + rl + 13f, "$pct%", size = 9f, color = "#444", anchor = "middle")
        }
    }

    @Test
    fun `writes a before-and-after SVG for each slider position`() {
        val dir = File("build/reports/taper-liner-compression-preview").apply { mkdirs() }
        listOf(writableFloors to "consolidated", leanFloors to "schematic").forEach { (floors, file) ->
            val svg = Svg()
            var y = 46f
            svg.text(contentLeft, 26f, "Taper compression follows the liner request — ${floors.name}", size = 15f, weight = "bold")
            svg.text(
                contentLeft, 40f,
                "25 ft × 8\" shaft — 19.5\" and 11.5\" tapers, three 16\" liners. " +
                    "Percentages are drawn width as a fraction of true length " +
                    "(liner floor ${floors.liner.toInt()} pt).",
                size = 10f, color = "#555",
            )
            settings.forEach { (compression, _, caption) ->
                val request = 1f - compression
                listOf(false, true).forEach { coupled ->
                    val l = Layout(spec, request, coupled, floors, contentLeft, contentRight)
                    val cy = y + 52f
                    svg.text(
                        contentLeft, y + 10f,
                        if (coupled) "AFTER · $caption" else "BEFORE · $caption",
                        size = 11f, weight = "bold",
                    )
                    drawProfile(svg, l, cy)
                    y += 112f
                }
                y += 18f
                svg.line(contentLeft, y - 12f, contentRight, y - 12f, w = 0.5f, color = "#ccc")
            }
            File(dir, "taper-liner-compression-$file.svg").writeText(svg.wrap(contentRight + 40f, y + 20f))
        }

        // ── Smoke assertions on the same numbers the picture shows ────────────
        var previousTaper = 0f
        settings.forEach { (compression, label, _) ->
            val request = 1f - compression
            val before = Layout(spec, request, coupled = false, writableFloors, contentLeft, contentRight)
            val after = Layout(spec, request, coupled = true, writableFloors, contentLeft, contentRight)
            val beforeTaper = before.keptFrac(0f, 495f)
            val afterTaper = after.keptFrac(0f, 495f)
            assertTrue(
                "the coupling may never shorten a taper (at $label: $beforeTaper → $afterTaper)",
                afterTaper >= beforeTaper - 1e-3f,
            )
            if (request <= PROFILE_TAPER_MIN_FRAC_OF_TRUE) {
                // Under the baseline the taper floor is unchanged, so these settings must
                // print exactly as they did before the coupling.
                assertEquals("must be a no-op at $label", beforeTaper, afterTaper, 1e-3f)
            } else {
                assertTrue("the taper must grow at $label", afterTaper > beforeTaper + 1e-2f)
            }
            assertTrue(
                "the taper must not shrink as the request rises ($label)",
                afterTaper >= previousTaper - 1e-3f,
            )
            previousTaper = afterTaper
            // Unequal tapers keep their true ratio at every setting — the standing rule the
            // fraction-of-true floor exists to hold.
            assertEquals(
                "unequal tapers must never equalize at $label",
                495f / 292f,
                after.keptFrac(0f, 495f) * 495f / (after.keptFrac(7328f, 292f) * 292f),
                3e-2f,
            )
        }
    }
}
