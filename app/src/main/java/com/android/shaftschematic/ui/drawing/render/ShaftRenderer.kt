package com.android.shaftschematic.ui.drawing.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.sp
import com.android.shaftschematic.model.ShaftSpec
import kotlin.math.max

/**
 * Preview renderer:
 * - Four-sided outlines for all components
 * - No preview centerline (grid emphasizes center major)
 * - Overall label sits just under the lowest geometry
 */
object ShaftRenderer {

    @OptIn(ExperimentalTextApi::class)
    fun DrawScope.draw(
        spec: ShaftSpec,
        opts: RenderOptions,
        textMeasurer: TextMeasurer
    ) {
        val layout = ShaftLayout.compute(
            spec = spec,
            leftPx = 0f,
            topPx = 0f,
            rightPx = size.width,
            bottomPx = size.height
        )
        draw(layout, opts, textMeasurer)
    }

    @OptIn(ExperimentalTextApi::class)
    fun DrawScope.draw(
        layout: ShaftLayout.Result,
        opts: RenderOptions,
        textMeasurer: TextMeasurer
    ) {
        val spec = layout.spec
        val left = layout.contentLeftPx
        val right = layout.contentRightPx
        val top = layout.contentTopPx
        val bottom = layout.contentBottomPx
        val cy = layout.centerlineYPx
        val pxPerMm = layout.pxPerMm

        val shaftWidth = if (opts.lineWidthPx > 0f) opts.lineWidthPx else 2.5f
        val dimWidth   = if (opts.dimLineWidthPx > 0f) opts.dimLineWidthPx else 1.6f

        // Project using (mm - minXMm) so X=0 aligns even when minXMm < 0
        fun xPx(mm: Float) = left + (mm - layout.minXMm) * pxPerMm
        fun rPx(diaMm: Float) = 0.5f * diaMm * pxPerMm

        // AFT ◀︎ —— ▶︎ FWD indicator (thin & subtle)
        drawLine(
            color = Color.Black.copy(alpha = 0.45f),
            start = Offset(left + 56f, top + 22f),
            end   = Offset(right - 56f, top + 22f),
            strokeWidth = dimWidth
        )

        var lowestYPx = cy

        // Bodies — full rectangle (top, bottom, left, right)
        spec.bodies.forEach { b ->
            val x0 = xPx(b.startFromAftMm)
            val x1 = xPx(b.startFromAftMm + b.lengthMm)
            val r  = rPx(b.diaMm)
            val topEdge = cy - r
            val botEdge = cy + r

            drawLine(Color.Black, Offset(x0, topEdge), Offset(x1, topEdge), shaftWidth)
            drawLine(Color.Black, Offset(x0, botEdge), Offset(x1, botEdge), shaftWidth)
            drawLine(Color.Black, Offset(x0, topEdge), Offset(x0, botEdge), shaftWidth)
            drawLine(Color.Black, Offset(x1, topEdge), Offset(x1, botEdge), shaftWidth)

            if (botEdge > lowestYPx) lowestYPx = botEdge
        }

        // Tapers — trapezoid from startDiaMm → endDiaMm (no direction flag needed)
        spec.tapers.forEach { t ->
            val x0 = xPx(t.startFromAftMm)
            val x1 = xPx(t.startFromAftMm + t.lengthMm)

            val r0 = rPx(t.startDiaMm)
            val r1 = rPx(t.endDiaMm)

            val top0 = cy - r0; val bot0 = cy + r0
            val top1 = cy - r1; val bot1 = cy + r1

            drawLine(Color.Black, Offset(x0, top0), Offset(x1, top1), shaftWidth)
            drawLine(Color.Black, Offset(x0, bot0), Offset(x1, bot1), shaftWidth)
            drawLine(Color.Black, Offset(x0, top0), Offset(x0, bot0), shaftWidth)
            drawLine(Color.Black, Offset(x1, top1), Offset(x1, bot1), shaftWidth)

            if (bot0 > lowestYPx) lowestYPx = bot0
            if (bot1 > lowestYPx) lowestYPx = bot1
        }

        // Threads — OD + hatch + side walls
        spec.threads.forEach { th ->
            val x0 = xPx(th.startFromAftMm)
            val x1 = xPx(th.startFromAftMm + th.lengthMm)
            val r  = rPx(th.majorDiaMm)
            val topEdge = cy - r
            val botEdge = cy + r

            drawLine(Color.Black, Offset(x0, topEdge), Offset(x1, topEdge), dimWidth)
            drawLine(Color.Black, Offset(x0, botEdge), Offset(x1, botEdge), dimWidth)
            drawLine(Color.Black, Offset(x0, topEdge), Offset(x0, botEdge), dimWidth)
            drawLine(Color.Black, Offset(x1, topEdge), Offset(x1, botEdge), dimWidth)

            val hatchStep = max(8f, th.pitchMm * pxPerMm)
            var hx = x0 + 4f
            while (hx <= x1) {
                drawLine(Color.Black, Offset(hx - 4f, botEdge), Offset(hx + 4f, topEdge), dimWidth)
                hx += hatchStep
            }

            if (botEdge > lowestYPx) lowestYPx = botEdge
        }

        // Liners — rectangle
        spec.liners.forEach { ln ->
            val x0 = xPx(ln.startFromAftMm)
            val x1 = xPx(ln.startFromAftMm + ln.lengthMm)
            val r  = rPx(ln.odMm)
            val topEdge = cy - r
            val botEdge = cy + r

            // Use BODY-thickness for all four edges of the liner box:
            drawRect(
                color = Color.Black,
                topLeft = Offset(x0, topEdge),
                size = Size(width = x1 - x0, height = botEdge - topEdge),
                style = Stroke(width = shaftWidth)  // import androidx.compose.ui.graphics.drawscope.Stroke
            )

            if (botEdge > lowestYPx) lowestYPx = botEdge
        }

        // Overall label: a few px below the lowest geometry, clamped inside content.
        drawOverallBelowShaft(layout, opts, textMeasurer, lowestYPx + 6f)
    }

    // --- Overall label helper ------------------------------------------------

    @OptIn(ExperimentalTextApi::class)
    private fun DrawScope.drawOverallBelowShaft(
        layout: ShaftLayout.Result,
        opts: RenderOptions,
        textMeasurer: TextMeasurer,
        preferredTopY: Float
    ) {
        val overallMm = layout.spec.overallLengthMm
        val label = if (opts.gridUseInches) {
            val inches = overallMm / 25.4f
            "Overall: %.3f in".format(inches)
        } else {
            if (overallMm >= 100f) "Overall: ${overallMm.toInt()} mm"
            else "Overall: %.1f mm".format(overallMm)
        }
        val style = TextStyle(
            color = Color.Black,
            fontSize = (max(18f, 0.75f * opts.textSizePx) / density).sp,
            platformStyle = PlatformTextStyle(includeFontPadding = false)
        )
        val tl = textMeasurer.measure(AnnotatedString(label), style)
        val tx = (layout.contentLeftPx + layout.contentRightPx - tl.size.width) / 2f
        val maxTop = layout.contentBottomPx - tl.size.height - 4f
        val ty = preferredTopY.coerceAtMost(maxTop)
        drawText(tl, topLeft = androidx.compose.ui.geometry.Offset(tx, ty))
    }
}
