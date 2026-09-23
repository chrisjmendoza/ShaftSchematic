package com.android.shaftschematic.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.android.shaftschematic.geom.BelowShaftLabelLayout
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.pdf.notes.DiaCallout
import com.android.shaftschematic.pdf.notes.DiameterLeaderRenderer
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Same-math SVG previews of the band under the shaft, for markup review without an on-device
 * round trip — the house convention for a drawing change (`BodyBreakFillSvgPreviewTest`).
 *
 * "Same math" is literal: every box and every label position comes from the production pair —
 * [DiameterLeaderRenderer.occupancy] for what the Ø callouts ink, [planComponentLabels] for where
 * the names land against them. The reserved boxes are stroked so a reviewer can see the clearance
 * itself, not just the glyphs.
 *
 * The scenarios sweep the drawing width, which is what compression moves: at full width the names
 * sit centered, and as the sheet narrows they slide along their own components and finally drop a
 * row. Written to `build/reports/below-shaft-labels/`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BelowShaftLabelSvgPreviewTest {

    /** Robolectric's native graphics stack needs a Bitmap/Canvas up before a bare Paint measures. */
    @Before
    fun warmUpGraphics() {
        Canvas(Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)).drawColor(Color.WHITE)
    }

    private val shaftBottomY = 120f
    private val shaftHeight = 80f

    private fun geomRect(widthPt: Float) = RectF(20f, 20f, 20f + widthPt, 260f)

    private fun leaderPaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; color = Color.BLACK }

    private fun spec(): ShaftSpec {
        val oal = 3829.05f   // 150 3/4", the reported sheet
        return ShaftSpec(
            overallLengthMm = oal,
            bodies = listOf(
                Body(id = "b1", startFromAftMm = 0f, lengthMm = oal, diaMm = 190f, showDiaOnDrawing = true),
            ),
            liners = listOf(
                Liner(id = "l1", startFromAftMm = 0f, lengthMm = 298.45f, odMm = 201.6f, label = "AFT Liner"),
                Liner(id = "l2", startFromAftMm = oal - 449.3f, lengthMm = 449.3f, odMm = 203.2f, label = "FWD Liner"),
            ),
        )
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun box(b: BelowShaftLabelLayout.Box, stroke: String, dash: String = "") =
        "<rect x=\"${b.left}\" y=\"${b.top}\" width=\"${b.right - b.left}\" height=\"${b.bottom - b.top}\" " +
            "fill=\"none\" stroke=\"$stroke\" stroke-width=\"0.4\"$dash/>"

    private fun svg(widthPt: Float, title: String): String {
        val spec = spec()
        val rect = geomRect(widthPt)
        val ptPerMm = widthPt / spec.overallLengthMm
        val xAt = { mm: Float -> rect.left + mm * ptPerMm }

        val calls: List<DiaCallout> = buildBodyOdCallouts(spec.bodies) + buildLinerOdCallouts(spec.liners)
        val leaderText = leaderPaint()
        val leader = DiameterLeaderRenderer(
            pageX = { mm -> xAt(mm.toFloat()) },
            shaftTopY = shaftBottomY - shaftHeight,
            shaftBottomY = shaftBottomY,
            linePaint = Paint(),
            textPaint = leaderText,
        )
        val reserved = leader.occupancy(calls)

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; color = Color.BLACK }
        val spans = componentLabelSpans(spec, titlesDefault = true)
        val plan = planComponentLabels(spans, labelPaint, rect, shaftBottomY + 32f, xAt, reserved)
        val fm = labelPaint.fontMetrics

        val sb = StringBuilder()
        sb.append(
            "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ${rect.right + 20f} 280\" " +
                "width=\"${rect.right + 20f}\" height=\"280\">\n"
        )
        sb.append("<rect width=\"100%\" height=\"100%\" fill=\"white\"/>\n")
        sb.append(
            "<text x=\"20\" y=\"16\" font-family=\"sans-serif\" font-size=\"9\" fill=\"#555\">" +
                "${esc(title)} — drawing width ${widthPt.toInt()} pt</text>\n"
        )

        // The shaft, and the liners on it, so the callout anchors read.
        sb.append(
            "<rect x=\"${rect.left}\" y=\"${shaftBottomY - shaftHeight}\" width=\"$widthPt\" " +
                "height=\"$shaftHeight\" fill=\"none\" stroke=\"black\" stroke-width=\"1\"/>\n"
        )
        spec.liners.forEach { ln ->
            val x0 = xAt(ln.startFromAftMm)
            val x1 = xAt(ln.startFromAftMm + ln.lengthMm)
            sb.append(
                "<rect x=\"$x0\" y=\"${shaftBottomY - shaftHeight - 4f}\" width=\"${x1 - x0}\" " +
                    "height=\"${shaftHeight + 8f}\" fill=\"#00000018\" stroke=\"black\" stroke-width=\"1\"/>\n"
            )
        }

        // What the Ø callouts ink — leaders and values — as the boxes the planner was handed.
        reserved.forEach { sb.append(box(it, "#c00")).append('\n') }
        calls.forEach { call ->
            val x = xAt(call.xMm.toFloat())
            val tier = reserved.indexOfFirst { it.left >= x + 14f - 0.01f && it.left <= x + 14f + 0.01f }
            val b = reserved.getOrNull(tier) ?: return@forEach
            sb.append(
                "<text x=\"${b.left}\" y=\"${b.bottom - fm.descent}\" font-family=\"sans-serif\" " +
                    "font-size=\"10\" fill=\"#c00\">Ø ${"%.3f".format(call.valueMm / 25.4)}\"</text>\n"
            )
        }

        // Ghost: where each name sat BEFORE the two passes shared a collision space — centered
        // over its component on the first row, which is the planner's own starting position.
        spans.forEach { span ->
            val w = labelPaint.measureText(span.text)
            val ghostLeft = ((xAt(span.startMm) + xAt(span.endMm)) * 0.5f - w * 0.5f)
                .coerceIn(rect.left, maxOf(rect.left, rect.right - w))
            sb.append(
                "<text x=\"$ghostLeft\" y=\"${shaftBottomY + 32f}\" font-family=\"sans-serif\" " +
                    "font-size=\"10\" fill=\"#bbb\">${esc(span.text)}</text>\n"
            )
        }

        // Where the names landed.
        plan.placements.forEachIndexed { i, p ->
            val baseline = shaftBottomY + 32f + p.row * plan.rowStep
            val w = labelPaint.measureText(spans[i].text)
            sb.append(
                box(
                    BelowShaftLabelLayout.Box(p.left, p.left + w, baseline + fm.ascent, baseline + fm.descent),
                    "#06c",
                    " stroke-dasharray=\"2 2\"",
                )
            ).append('\n')
            sb.append(
                "<text x=\"${p.left}\" y=\"$baseline\" font-family=\"sans-serif\" font-size=\"10\" " +
                    "fill=\"#06c\">${esc(spans[i].text)}</text>\n"
            )
        }

        sb.append("</svg>\n")
        return sb.toString()
    }

    @Test
    fun `writes below-shaft label previews across compression widths`() {
        val outDir = File("build/reports/below-shaft-labels").apply { mkdirs() }
        outDir.listFiles()?.forEach { it.delete() }

        listOf(
            Triple("a-full-width", 700f, "Centered names, callouts clear"),
            Triple("b-half-width", 360f, "Compressed"),
            Triple("c-narrow", 240f, "Heavily compressed"),
            Triple("d-very-narrow", 160f, "Worst case"),
        ).forEach { (name, w, title) ->
            File(outDir, "$name.svg").writeText(svg(w, title))
        }

        assertEquals(4, outDir.listFiles()!!.count { it.name.endsWith(".svg") })
    }
}
