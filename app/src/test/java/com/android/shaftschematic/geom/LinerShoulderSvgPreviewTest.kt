package com.android.shaftschematic.geom

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Renders shouldered liners to SVG through the SAME pipeline the app draws with
 * ([shoulderDrawSpec] → [linerTopSilhouette]), so the step and fillet can be reviewed and
 * marked up without an on-device build. Writes
 * `app/build/reports/blend-preview/liner-shoulders.svg`.
 *
 * A review artifact, not an appearance assertion — the only pin is that every row that asked
 * for a shoulder produced a stepped silhouette, so a silently square sheet fails instead of
 * publishing.
 */
class LinerShoulderSvgPreviewTest {

    private class Row(
        val title: String,
        val aftLenMm: Float = 0f, val aftOdMm: Float = 0f, val aftRadiusIn: Float = 0f,
        val fwdLenMm: Float = 0f, val fwdOdMm: Float = 0f, val fwdRadiusIn: Float = 0f,
    )

    @Test
    fun `render liner shoulders to svg`() {
        val IN = 25.4f
        val linerOdMm = 203.2f      // 8 in sleeve
        val linerLenMm = 500f
        val shaftDiaMm = 152.4f     // 6 in shaft under it
        val scaleX = 0.6f
        val scaleR = 0.5f
        val padL = 180f
        val rowH = 150f

        val rows = listOf(
            Row("No shoulders (control)"),
            Row("AFT 2\" shoulder to Ø7, sharp", aftLenMm = 2 * IN, aftOdMm = 177.8f),
            Row("AFT 2\" shoulder to Ø7, 1/8\" radius", aftLenMm = 2 * IN, aftOdMm = 177.8f, aftRadiusIn = 1f / 8f),
            Row("Both ends, 1/4\" radius",
                aftLenMm = 1.5f * IN, aftOdMm = 177.8f, aftRadiusIn = 1f / 4f,
                fwdLenMm = 2.5f * IN, fwdOdMm = 171.45f, fwdRadiusIn = 1f / 4f),
            Row("FWD only, 1/2\" radius (cap test)", fwdLenMm = 2 * IN, fwdOdMm = 177.8f, fwdRadiusIn = 1f / 2f),
        )

        val sb = StringBuilder()
        fun line(x0: Float, y0: Float, x1: Float, y1: Float, sw: Float = 1.4f, stroke: String = "#111") {
            sb.append("""<line x1="$x0" y1="$y0" x2="$x1" y2="$y1" stroke="$stroke" stroke-width="$sw"/>""").append('\n')
        }
        fun poly(pts: List<Pair<Float, Float>>, fill: String) {
            sb.append("""<polygon points="${pts.joinToString(" ") { "${it.first},${it.second}" }}" fill="$fill" stroke="none"/>""").append('\n')
        }
        fun text(x: Float, y: Float, s: String) {
            sb.append("""<text x="$x" y="$y" font-family="Helvetica,Arial" font-size="13" fill="#111">$s</text>""").append('\n')
        }

        var y = 40f
        var missing = 0
        rows.forEach { r ->
            val cy = y + rowH / 2f
            text(12f, cy - 60f, r.title)

            val x0 = padL
            val x1 = padL + linerLenMm * scaleX
            val linerR = linerOdMm / 2f * scaleR
            val shaftR = shaftDiaMm / 2f * scaleR

            // Shaft under the liner, for context.
            line(x0 - 60f, cy - shaftR, x1 + 60f, cy - shaftR, sw = 1f, stroke = "#999")
            line(x0 - 60f, cy + shaftR, x1 + 60f, cy + shaftR, sw = 1f, stroke = "#999")

            fun spec(lenMm: Float, odMm: Float, radiusIn: Float): ShoulderDrawSpec? {
                if (lenMm <= 0f || odMm <= 0f) return null
                return shoulderDrawSpec(
                    trueLenPx = lenMm * scaleX,
                    runWidthPx = x1 - x0,
                    linerRPx = linerR,
                    shoulderRPx = odMm / 2f * scaleR,
                    filletRPx = radiusIn * IN * scaleR,
                    minWidthPx = 7f,
                )
            }
            val aft = spec(r.aftLenMm, r.aftOdMm, r.aftRadiusIn)
            val fwd = spec(r.fwdLenMm, r.fwdOdMm, r.fwdRadiusIn)
            if ((r.aftLenMm > 0f && aft == null) || (r.fwdLenMm > 0f && fwd == null)) missing++

            val pts = linerTopSilhouette(x0, x1, linerR, aft, fwd)
            if (r.aftLenMm > 0f || r.fwdLenMm > 0f) {
                assertTrue("row '${r.title}' drew square", pts.size > 2)
            }
            poly(
                pts.map { it.xPx to cy - it.rPx } + pts.reversed().map { it.xPx to cy + it.rPx },
                fill = "#00000022",
            )
            for (k in 1 until pts.size) {
                line(pts[k - 1].xPx, cy - pts[k - 1].rPx, pts[k].xPx, cy - pts[k].rPx)
                line(pts[k - 1].xPx, cy + pts[k - 1].rPx, pts[k].xPx, cy + pts[k].rPx)
            }
            line(x0, cy - pts.first().rPx, x0, cy + pts.first().rPx)
            line(x1, cy - pts.last().rPx, x1, cy + pts.last().rPx)
            y += rowH
        }

        val svg = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 560 ${y + 20f}" width="560" height="${y + 20f}" style="background:white">""" +
            "\n$sb</svg>\n"
        val out = File("build/reports/blend-preview").also { it.mkdirs() }
        File(out, "liner-shoulders.svg").writeText(svg)
        org.junit.Assert.assertEquals("rows asked for a shoulder and got none", 0, missing)
    }
}
