package com.android.shaftschematic.ui.drawing.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.sp
import com.android.shaftschematic.data.ShaftSpecMm
import kotlin.math.max
import kotlin.math.min

/**
 * Compose-first renderer for the shaft drawing. Uses DrawScope APIs only.
 * Expects a precomputed ShaftLayout (for pixel mapping) and RenderOptions (styling).
 */
class ShaftRenderer {

    @OptIn(ExperimentalTextApi::class)
    fun DrawScope.render(
        layout: ShaftLayout.Result,
        opts: RenderOptions,
        textMeasurer: TextMeasurer
    ) {
        val spec: ShaftSpecMm = layout.spec

        // Use simple width floats for Compose drawLine
        val shaftWidth = opts.lineWidthPx
        val dimWidth   = opts.dimLineWidthPx

        // Optional grid underlay
        if (opts.showGrid) drawGrid(layout, opts)

        val cy = layout.centerlineYPx

        // Centerline
        drawLine(
            color = Color.Black,
            start = Offset(layout.contentLeftPx, cy),
            end   = Offset(layout.contentRightPx, cy),
            strokeWidth = shaftWidth
        )

        // Helpers: unit conversions
        fun xAt(mmFromAft: Float): Float = layout.contentLeftPx + mmFromAft * layout.pxPerMm
        fun radPx(diaMm: Float): Float = (diaMm * 0.5f) * layout.pxPerMm

        // Bodies
        spec.bodies.forEach { b ->
            val x0 = xAt(b.startFromAftMm)
            val x1 = xAt(b.startFromAftMm + b.lengthMm)
            val r  = radPx(b.diaMm)
            val top = cy - r
            val bottom = cy + r

            drawLine(Color.Black, Offset(x0, top), Offset(x1, top), shaftWidth)
            drawLine(Color.Black, Offset(x0, bottom), Offset(x1, bottom), shaftWidth)

            // End ticks
            drawLine(Color.Black, Offset(x0, top - 6f), Offset(x0, top + 6f), dimWidth)
            drawLine(Color.Black, Offset(x1, top - 6f), Offset(x1, top + 6f), dimWidth)
        }

        // Tapers
        spec.tapers.forEach { t ->
            val x0 = xAt(t.startFromAftMm)
            val x1 = xAt(t.startFromAftMm + t.lengthMm)
            val r0 = radPx(t.startDiaMm)
            val r1 = radPx(t.endDiaMm)

            drawLine(Color.Black, Offset(x0, cy - r0), Offset(x1, cy - r1), shaftWidth)
            drawLine(Color.Black, Offset(x0, cy + r0), Offset(x1, cy + r1), shaftWidth)

            // End ticks (span the larger radius)
            drawLine(Color.Black, Offset(x0, cy - max(r0, r1) - 6f), Offset(x0, cy - min(r0, r1) + 6f), dimWidth)
            drawLine(Color.Black, Offset(x1, cy - max(r0, r1) - 6f), Offset(x1, cy - min(r0, r1) + 6f), dimWidth)
        }

        // Threads (band + light hatch)
        spec.threads.forEach { th ->
            val x0 = xAt(th.startFromAftMm)
            val x1 = xAt(th.startFromAftMm + th.lengthMm)
            val r  = radPx(th.majorDiaMm)
            val top = cy - r
            val bottom = cy + r

            drawLine(Color.Black, Offset(x0, top), Offset(x1, top), shaftWidth)
            drawLine(Color.Black, Offset(x0, bottom), Offset(x1, bottom), shaftWidth)

            // Diagonal hatch to suggest thread zone (subtle)
            var hx = x0
            val hatchStep = 8f
            while (hx < x1) {
                drawLine(Color(0x99000000), Offset(hx, top), Offset(hx + 6f, top + 12f), dimWidth)
                hx += hatchStep
            }

            // End ticks
            drawLine(Color.Black, Offset(x0, top - 6f), Offset(x0, top + 6f), dimWidth)
            drawLine(Color.Black, Offset(x1, top - 6f), Offset(x1, top + 6f), dimWidth)
        }

        // Liners
        spec.liners.forEach { ln ->
            val x0 = xAt(ln.startFromAftMm)
            val x1 = xAt(ln.startFromAftMm + ln.lengthMm)
            val r  = radPx(ln.odMm)

            drawLine(Color.Black, Offset(x0, cy - r), Offset(x1, cy - r), dimWidth)
            drawLine(Color.Black, Offset(x0, cy + r), Offset(x1, cy + r), dimWidth)
            drawLine(Color.Black, Offset(x0, cy - r - 5f), Offset(x0, cy - r + 5f), dimWidth)
            drawLine(Color.Black, Offset(x1, cy - r - 5f), Offset(x1, cy - r + 5f), dimWidth)
        }

        // First dimension label: Overall Length (units inferred from gridUseInches)
        drawOverallLengthLabel(layout, opts, textMeasurer)
    }

    private fun DrawScope.drawGrid(layout: ShaftLayout.Result, opts: RenderOptions) {
        val width  = layout.contentRightPx - layout.contentLeftPx
        val height = layout.contentBottomPx - layout.contentTopPx

        // Try to place desired number of majors across width
        val majors = max(1, opts.gridDesiredMajors)
        val majorGapPx = width / majors
        val minors = max(1, opts.gridMinorsPerMajor)
        val minorGapPx = majorGapPx / minors

        // Keep minors from getting too dense
        val minGap = 2f
        val useMinor = minorGapPx >= minGap

        // Vertical lines
        var x = layout.contentLeftPx
        var i = 0
        while (x <= layout.contentRightPx + 0.5f) {
            val isMajor = i % minors == 0
            val strokeWidth = if (isMajor) opts.gridMajorStrokePx else opts.gridMinorStrokePx
            val color = if (isMajor) Color(opts.gridMajorColor) else Color(opts.gridMinorColor)
            if (isMajor || useMinor) {
                drawLine(color, Offset(x, layout.contentTopPx), Offset(x, layout.contentBottomPx), strokeWidth)
            }
            x += minorGapPx
            i++
        }

        // Horizontal lines
        var y = layout.contentTopPx
        i = 0
        while (y <= layout.contentBottomPx + 0.5f) {
            val isMajor = i % minors == 0
            val strokeWidth = if (isMajor) opts.gridMajorStrokePx else opts.gridMinorStrokePx
            val color = if (isMajor) Color(opts.gridMajorColor) else Color(opts.gridMinorColor)
            if (isMajor || useMinor) {
                drawLine(color, Offset(layout.contentLeftPx, y), Offset(layout.contentRightPx, y), strokeWidth)
            }
            y += minorGapPx
            i++
        }

        // Simple scale bar (top-right)
        if (opts.showGridLegend) {
            val barW = majorGapPx
            val barH = 6f
            val pad = 8f
            drawRect(
                color = Color.Black,
                topLeft = Offset(layout.contentRightPx - barW - pad, layout.contentTopPx + pad),
                size = Size(barW, barH),
                alpha = 0.6f
            )
        }
    }

    @OptIn(ExperimentalTextApi::class)
    private fun DrawScope.drawOverallLengthLabel(
        layout: ShaftLayout.Result,
        opts: RenderOptions,
        textMeasurer: TextMeasurer
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

        val textLayout = textMeasurer.measure(AnnotatedString(label), style)

        // Center the label horizontally, place just above the bottom of content rect
        val tx = (layout.contentLeftPx + layout.contentRightPx - textLayout.size.width) / 2f
        val ty = layout.contentBottomPx - 6f - textLayout.size.height

        drawText(textLayout, topLeft = Offset(tx, ty))
    }
}
