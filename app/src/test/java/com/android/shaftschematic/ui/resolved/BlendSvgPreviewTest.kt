package com.android.shaftschematic.ui.resolved

import com.android.shaftschematic.model.BlendProfile
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.ShaftSpec
import org.junit.Test
import java.io.File

/**
 * Renders the blend silhouette to SVG through the SAME pipeline the app draws with
 * ([bodyBlends] → [bodyDrawEdges]), so a curve can be reviewed and marked up without an
 * on-device build. Writes `app/build/reports/blend-preview/blend-profiles.svg`.
 *
 * Nothing here asserts an appearance — it is a review artifact. The only assertion is that
 * every profile produced a curve, so a silently empty sheet fails instead of publishing.
 */
class BlendSvgPreviewTest {

    private class Svg(val w: Float, val h: Float) {
        val sb = StringBuilder()
        fun line(x0: Float, y0: Float, x1: Float, y1: Float, stroke: String = "#111", sw: Float = 1.4f) {
            sb.append("""<line x1="$x0" y1="$y0" x2="$x1" y2="$y1" stroke="$stroke" stroke-width="$sw"/>""").append('\n')
        }
        fun poly(pts: List<Pair<Float, Float>>, fill: String) {
            sb.append("""<polygon points="${pts.joinToString(" ") { "${it.first},${it.second}" }}" fill="$fill" stroke="none"/>""").append('\n')
        }
        fun text(x: Float, y: Float, s: String, size: Float = 13f, fill: String = "#111") {
            sb.append("""<text x="$x" y="$y" font-family="Helvetica,Arial" font-size="$size" fill="$fill">$s</text>""").append('\n')
        }
        fun render() =
            """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $w $h" width="$w" height="$h" style="background:white">""" +
                "\n$sb</svg>\n"
    }

    /** The shop case: a coupling fit at Ø6 enlarging to a Ø8 body, blended over 2 in. */
    private fun spec(profile: BlendProfile, blendMm: Float) = ShaftSpec(
        overallLengthMm = 1000f,
        bodies = listOf(
            Body(id = "fit", startFromAftMm = 0f, lengthMm = 500f, diaMm = 152.4f),
            Body(
                id = "run", startFromAftMm = 500f, lengthMm = 500f, diaMm = 203.2f,
                blendAftMm = blendMm, blendProfile = profile,
            ),
        ),
    )

    @Test
    fun `render blend profiles to svg`() {
        val rowH = 150f
        val padL = 130f
        val padT = 46f
        val scaleX = 0.62f       // pt per mm along the shaft
        val scaleR = 0.28f       // pt per mm of radius

        val rows = listOf(
            Triple("S-curve (default)", BlendProfile.OGEE, 50.8f),
            Triple("Fillet", BlendProfile.FILLET, 50.8f),
            Triple("Eased cone", BlendProfile.EASED_CONE, 50.8f),
            Triple("Square face (no blend)", BlendProfile.OGEE, 0f),
            Triple("S-curve, 6 in", BlendProfile.OGEE, 152.4f),
        )
        val svg = Svg(w = padL + 1000f * scaleX + 40f, h = padT + rows.size * rowH + 20f)
        var drewACurve = false

        rows.forEachIndexed { i, (title, profile, blendMm) ->
            val cy = padT + i * rowH + rowH / 2f
            val s = spec(profile, blendMm)
            val comps = resolveComponents(s, overallIsManual = true)
            val blends = bodyBlends(s, comps)
            svg.text(12f, cy - 44f, title, size = 13f)
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
                if (e.hasBlend) drewACurve = true

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
            }
        }

        val out = File("build/reports/blend-preview").also { it.mkdirs() }
        File(out, "blend-profiles.svg").writeText(svg.render())
        org.junit.Assert.assertTrue("no blend curve was produced", drewACurve)
    }
}
