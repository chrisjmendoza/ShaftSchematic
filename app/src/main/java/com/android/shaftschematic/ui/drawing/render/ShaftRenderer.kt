package com.android.shaftschematic.ui.drawing.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
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
 * - Four-sided outlines for components
 * - No preview centerline
 * - Overall label sits just under lowest geometry
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

        val shaftW = opts.lineWidthPx
        val dimW = opts.dimLineWidthPx

        // IMPORTANT: project using (mm - minXMm) so X=0 aligns even when minXMm < 0
        fun xPx(mm: Float) = left + (mm - layout.minXMm) * pxPerMm
        fun rPx(diaMm: Float) = 0.5f * diaMm * pxPerMm

        if (opts.showGrid) drawGrid(layout, opts, textMeasurer)

        // AFT ◀︎ —— ▶︎ FWD indicator
        drawLine(
            color = Color.Black,
            start = Offset(left + 56f, top + 22f),
            end   = Offset(right - 56f, top + 22f),
            strokeWidth = dimW
        )

        var lowestYPx = cy

        // Bodies
        spec.bodies.forEach { b ->
            val x0 = xPx(b.startFromAftMm)
            val x1 = xPx(b.startFromAftMm + b.lengthMm)
            val r  = rPx(b.diaMm)
            val topEdge = cy - r
            val botEdge = cy + r
            drawLine(Color.Black, Offset(x0, topEdge), Offset(x1, topEdge), shaftW)
            drawLine(Color.Black, Offset(x0, botEdge), Offset(x1, botEdge), shaftW)
            drawLine(Color.Black, Offset(x0, topEdge), Offset(x0, botEdge), shaftW)
            drawLine(Color.Black, Offset(x1, topEdge), Offset(x1, botEdge), shaftW)
            if (botEdge > lowestYPx) lowestYPx = botEdge
        }

        // Tapers (your existing fields: startDiaMm/endDiaMm)
        spec.tapers.forEach { t ->
            val x0 = xPx(t.startFromAftMm)
            val x1 = xPx(t.startFromAftMm + t.lengthMm)
            val r0 = rPx(t.startDiaMm)
            val r1 = rPx(t.endDiaMm)
            val top0 = cy - r0; val bot0 = cy + r0
            val top1 = cy - r1; val bot1 = cy + r1
            drawLine(Color.Black, Offset(x0, top0), Offset(x1, top1), shaftW)
            drawLine(Color.Black, Offset(x0, bot0), Offset(x1, bot1), shaftW)
            drawLine(Color.Black, Offset(x0, top0), Offset(x0, bot0), shaftW)
            drawLine(Color.Black, Offset(x1, top1), Offset(x1, bot1), shaftW)
            if (bot0 > lowestYPx) lowestYPx = bot0
            if (bot1 > lowestYPx) lowestYPx = bot1
        }

        // Threads
        spec.threads.forEach { th ->
            val x0 = xPx(th.startFromAftMm)
            val x1 = xPx(th.startFromAftMm + th.lengthMm)
            val r  = rPx(th.majorDiaMm)
            val topEdge = cy - r
            val botEdge = cy + r
            drawLine(Color.Black, Offset(x0, topEdge), Offset(x1, topEdge), dimW)
            drawLine(Color.Black, Offset(x0, botEdge), Offset(x1, botEdge), dimW)
            drawLine(Color.Black, Offset(x0, topEdge), Offset(x0, botEdge), dimW)
            drawLine(Color.Black, Offset(x1, topEdge), Offset(x1, botEdge), dimW)
            // simple 45° hatch spaced by pitch
            val hatchStep = max(8f, th.pitchMm * pxPerMm)
            var hx = x0 + 4f
            while (hx <= x1) {
                drawLine(Color.Black, Offset(hx - 4f, botEdge), Offset(hx + 4f, topEdge), dimW)
                hx += hatchStep
            }
            if (botEdge > lowestYPx) lowestYPx = botEdge
        }

        // Liners
        spec.liners.forEach { ln ->
            val x0 = xPx(ln.startFromAftMm)
            val x1 = xPx(ln.startFromAftMm + ln.lengthMm)
            val r  = rPx(ln.odMm)
            val topEdge = cy - r
            val botEdge = cy + r
            drawLine(Color.Black, Offset(x0, topEdge), Offset(x1, topEdge), dimW)
            drawLine(Color.Black, Offset(x0, botEdge), Offset(x1, botEdge), dimW)
            drawLine(Color.Black, Offset(x0, topEdge), Offset(x0, botEdge), dimW)
            drawLine(Color.Black, Offset(x1, topEdge), Offset(x1, botEdge), dimW)
            if (botEdge > lowestYPx) lowestYPx = botEdge
        }

        // Overall label just under the lowest geometry, clamped inside content.
        drawOverallBelowShaft(layout, opts, textMeasurer, lowestYPx + 6f)
    }

    // ---------------- helpers ----------------

    @OptIn(ExperimentalTextApi::class)
    private fun DrawScope.drawGrid(
        layout: ShaftLayout.Result,
        opts: RenderOptions,
        textMeasurer: TextMeasurer
    ) {
        // (Internal grid retained; preview centerline is intentionally not drawn here.)
        val left = layout.contentLeftPx
        val right = layout.contentRightPx
        val top = layout.contentTopPx
        val bottom = layout.contentBottomPx

        val width = right - left
        val majors = max(1, opts.gridDesiredMajors)
        val majorGapPx = width / majors
        val minors = max(1, opts.gridMinorsPerMajor)
        val minorGapPx = majorGapPx / minors
        val useMinor = minorGapPx >= opts.gridMinPixelGap

        var x = left
        var i = 0
        while (x <= right + 0.5f) {
            val isMajor = i % minors == 0
            val stroke = if (isMajor) opts.gridMajorStrokePx else opts.gridMinorStrokePx
            val color = if (isMajor) Color(opts.gridMajorColor) else Color(opts.gridMinorColor)
            if (isMajor || useMinor) drawLine(color, Offset(x, top), Offset(x, bottom), stroke)
            x += minorGapPx; i++
        }

        var y = top
        i = 0
        while (y <= bottom + 0.5f) {
            val isMajor = i % minors == 0
            val stroke = if (isMajor) opts.gridMajorStrokePx else opts.gridMinorStrokePx
            val color = if (isMajor) Color(opts.gridMajorColor) else Color(opts.gridMinorColor)
            if (isMajor || useMinor) drawLine(color, Offset(left, y), Offset(right, y), stroke)
            y += minorGapPx; i++
        }
    }

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
        drawText(tl, topLeft = Offset(tx, ty))
    }
}
