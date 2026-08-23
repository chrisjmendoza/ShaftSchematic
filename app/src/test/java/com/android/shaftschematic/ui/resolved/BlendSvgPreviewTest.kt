package com.android.shaftschematic.ui.resolved

import com.android.shaftschematic.geom.SEAL_DASH_OFF_PT
import com.android.shaftschematic.geom.SEAL_DASH_ON_PT
import com.android.shaftschematic.model.BlendProfile
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ShaftSpec
import org.junit.Test
import java.io.File

/**
 * Renders the blend silhouette to SVG through the SAME pipeline the app draws with
 * ([bodyBlends] → [bodyDrawEdges]), so a curve can be reviewed and marked up without an
 * on-device build. Writes `app/build/reports/blend-preview/blend-profiles.svg`.
 *
 * Nothing here asserts an appearance — it is a review artifact. The only assertion is that
 * every row that asked for a blend produced one, so a silently empty sheet fails instead of
 * publishing.
 */
class BlendSvgPreviewTest {

    private class Svg(val w: Float, val h: Float) {
        val sb = StringBuilder()
        fun line(x0: Float, y0: Float, x1: Float, y1: Float, stroke: String = "#111", sw: Float = 1.4f, dash: String? = null) {
            val d = if (dash != null) """ stroke-dasharray="$dash"""" else ""
            sb.append("""<line x1="$x0" y1="$y0" x2="$x1" y2="$y1" stroke="$stroke" stroke-width="$sw"$d/>""").append('\n')
        }
        fun poly(pts: List<Pair<Float, Float>>, fill: String) {
            sb.append("""<polygon points="${pts.joinToString(" ") { "${it.first},${it.second}" }}" fill="$fill" stroke="none"/>""").append('\n')
        }
        fun text(x: Float, y: Float, s: String, size: Float = 13f, fill: String = "#111", weight: String = "normal") {
            sb.append("""<text x="$x" y="$y" font-family="Helvetica,Arial" font-size="$size" font-weight="$weight" fill="$fill">$s</text>""").append('\n')
        }
        fun render() =
            """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $w $h" width="$w" height="$h" style="background:white">""" +
                "\n$sb</svg>\n"
    }

    private class Row(
        val title: String,
        val profile: BlendProfile,
        val blendMm: Float,
        val smallDiaMm: Float = 152.4f,   // 6 in
        val bigDiaMm: Float = 203.2f,     // 8 in
        val group: String? = null,
        /** > 0 puts a liner fwd of the face instead of a second body — the seal-area case. */
        val linerOdMm: Float = 0f,
        val seal: Boolean = false,
    )

    /** Smaller aft body enlarging into a bigger one — the coupling-fit case, blended on its AFT face. */
    private fun spec(r: Row) = if (r.linerOdMm > 0f) ShaftSpec(
        // Seal area: the body's FWD face butts a liner; the seat under it is never drawn.
        overallLengthMm = 1000f,
        bodies = listOf(
            Body(
                id = "run", startFromAftMm = 0f, lengthMm = 500f, diaMm = r.smallDiaMm,
                blendFwdMm = r.blendMm, blendProfile = r.profile, blendFwdSeal = r.seal,
            ),
        ),
        liners = listOf(Liner(startFromAftMm = 500f, lengthMm = 500f, odMm = r.linerOdMm)),
    ) else ShaftSpec(
        overallLengthMm = 1000f,
        bodies = listOf(
            Body(id = "fit", startFromAftMm = 0f, lengthMm = 500f, diaMm = r.smallDiaMm),
            Body(
                id = "run", startFromAftMm = 500f, lengthMm = 500f, diaMm = r.bigDiaMm,
                blendAftMm = r.blendMm, blendProfile = r.profile,
            ),
        ),
    )

    @Test
    fun `render blend profiles to svg`() {
        val rowH = 128f
        val padL = 152f
        val padT = 40f
        val scaleX = 0.60f       // pt per mm along the shaft
        val scaleR = 0.26f       // pt per mm of radius
        val IN = 25.4f

        val rows = listOf(
            Row("S-curve (default)", BlendProfile.OGEE, 2 * IN, group = "2 in blend  ·  Ø6 → Ø8"),
            Row("Fillet", BlendProfile.FILLET, 2 * IN),
            Row("Eased cone", BlendProfile.EASED_CONE, 2 * IN),
            Row("No blend (control)", BlendProfile.OGEE, 0f),

            Row("S-curve", BlendProfile.OGEE, 1 * IN, group = "1 in blend, same step  —  twice as steep"),
            Row("Fillet", BlendProfile.FILLET, 1 * IN),
            Row("Eased cone", BlendProfile.EASED_CONE, 1 * IN),

            Row("S-curve", BlendProfile.OGEE, 1 * IN, 177.8f, 190.5f,
                group = "Coupling seat  —  1 in blend, Ø7 → Ø7½"),
            Row("Eased cone", BlendProfile.EASED_CONE, 1 * IN, 177.8f, 190.5f),
            Row("No blend (control)", BlendProfile.OGEE, 0f, 177.8f, 190.5f),

            Row("S-curve", BlendProfile.OGEE, 1 * IN, 177.8f, linerOdMm = 203.2f,
                group = "Liner seal area  —  body Ø7 butting a Ø8 liner, seat derived at the midpoint (Ø7½)"),
            Row("Eased cone", BlendProfile.EASED_CONE, 1 * IN, 177.8f, linerOdMm = 203.2f),
            Row("No blend (control)", BlendProfile.OGEE, 0f, 177.8f, linerOdMm = 203.2f),

            Row("S-curve + seal area", BlendProfile.OGEE, 1 * IN, 177.8f, linerOdMm = 203.2f,
                seal = true,
                group = "Seal area  —  3 radius cuts across the blend, for the fiberglass to seat into"),
            Row("Eased cone + seal area", BlendProfile.EASED_CONE, 1 * IN, 177.8f,
                linerOdMm = 203.2f, seal = true),
            Row("S-curve + seal, 2 in blend", BlendProfile.OGEE, 2 * IN, 177.8f,
                linerOdMm = 203.2f, seal = true),
        )

        val groupCount = rows.count { it.group != null }
        val svg = Svg(w = padL + 1000f * scaleX + 40f, h = padT + rows.size * rowH + groupCount * 30f + 20f)
        var y = padT
        var missing = 0

        rows.forEach { r ->
            r.group?.let {
                y += 22f
                svg.text(12f, y, it, size = 12.5f, fill = "#b0432c", weight = "bold")
                svg.line(12f, y + 8f, padL + 1000f * scaleX + 12f, y + 8f, stroke = "#e0ddd6", sw = 1f)
                y += 8f
            }
            val cy = y + rowH / 2f
            val s = spec(r)
            val comps = resolveComponents(s, overallIsManual = true)
            val blends = bodyBlends(s, comps)
            if (r.blendMm > 0f && blends.isEmpty()) missing++

            svg.text(12f, cy - 38f, r.title, size = 13f)
            svg.line(padL - 12f, cy, padL + 1000f * scaleX + 12f, cy, stroke = "#c33", sw = 0.5f)

            comps.filterIsInstance<ResolvedBody>().forEach { run ->
                val e = bodyDrawEdges(
                    runId = run.id,
                    runStartMm = run.startMmPhysical,
                    runEndMm = run.endMmPhysical,
                    runDiaMm = run.diaMm,
                    blends = blends,
                    xAt = { mm -> padL + mm * scaleX },
                    rAt = { dia -> dia / 2f * scaleR },
                    minWidthPx = 7f,
                )
                val x0 = padL + run.startMmPhysical * scaleX
                val x1 = padL + run.endMmPhysical * scaleX
                val top = buildList {
                    if (e.aftCurve.isNotEmpty()) addAll(e.aftCurve.map { it.xPx to it.rPx })
                    else add(x0 to e.capAftR)
                    add(e.flatX0 to e.flatR)
                    add(e.flatX1 to e.flatR)
                    if (e.fwdCurve.isNotEmpty()) addAll(e.fwdCurve.map { it.xPx to it.rPx })
                    else add(x1 to e.capFwdR)
                }
                svg.poly(
                    top.map { it.first to cy - it.second } + top.reversed().map { it.first to cy + it.second },
                    fill = "#00000018",
                )
                for (k in 1 until top.size) {
                    svg.line(top[k - 1].first, cy - top[k - 1].second, top[k].first, cy - top[k].second)
                    svg.line(top[k - 1].first, cy + top[k - 1].second, top[k].first, cy + top[k].second)
                }
                svg.line(x0, cy - e.capAftR, x0, cy + e.capAftR)
                svg.line(x1, cy - e.capFwdR, x1, cy + e.capFwdR)
                (e.aftSeal + e.fwdSeal).forEach { g ->
                    svg.line(g.xPx, cy - g.rPx, g.xPx, cy + g.rPx,
                        dash = "$SEAL_DASH_ON_PT $SEAL_DASH_OFF_PT")
                }
            }

            comps.filterIsInstance<ResolvedLiner>().forEach { ln ->
                val lx0 = padL + ln.startMmPhysical * scaleX
                val lx1 = padL + ln.endMmPhysical * scaleX
                val lr = ln.odMm / 2f * scaleR
                svg.poly(
                    listOf(lx0 to cy - lr, lx1 to cy - lr, lx1 to cy + lr, lx0 to cy + lr),
                    fill = "#00000028",
                )
                svg.line(lx0, cy - lr, lx1, cy - lr)
                svg.line(lx0, cy + lr, lx1, cy + lr)
                svg.line(lx0, cy - lr, lx0, cy + lr)
                svg.line(lx1, cy - lr, lx1, cy + lr)
            }
            y += rowH
        }

        val out = File("build/reports/blend-preview").also { it.mkdirs() }
        File(out, "blend-profiles.svg").writeText(svg.render())
        org.junit.Assert.assertEquals("rows asked for a blend and got none", 0, missing)
    }
}
