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
import kotlin.math.min

/**
 * Compose-first shaft drawing renderer.
 *
 * ## Responsibilities
 * - Draws the shaft geometry (bodies, tapers, threads, liners) using canonical mm → px mapping.
 * - Top/bottom **and side walls** are rendered for a complete outline on each component.
 * - Centerline is **not** drawn in the preview (by design — PDF may include it).
 * - Grid is optional; when the screen-level `ShaftDrawing` draws its own grid, disable ours via [RenderOptions].
 *
 * ## Inputs
 * - [ShaftLayout.Result] supplies the content rect and uniform [pxPerMm] scale.
 * - [RenderOptions] configures line weights and (optionally) the internal grid/legend if enabled.
 *
 * All geometry is **millimeters** in the model.
 */
object ShaftRenderer {

    /**
     * Entry that computes a layout and renders the shaft into the caller's [DrawScope].
     */
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

    /**
     * Render using a precomputed [layout].
     *
     * @param layout Content rect, centerline, and mm→px scale.
     * @param opts   Rendering options (line widths, colors, grid toggle, legend).
     * @param textMeasurer For on-canvas text (overall label, optional legend when grid enabled).
     */
    @OptIn(ExperimentalTextApi::class)
    fun DrawScope.draw(
        layout: ShaftLayout.Result,
        opts: RenderOptions,
        textMeasurer: TextMeasurer
    ) {
        val spec = layout.spec

        // Short aliases
        val left = layout.contentLeftPx
        val right = layout.contentRightPx
        val top = layout.contentTopPx
        val bottom = layout.contentBottomPx
        val cy = layout.centerlineYPx
        val pxPerMm = layout.pxPerMm

        // Strokes
        val shaftWidth = opts.lineWidthPx
        val dimWidth = opts.dimLineWidthPx

        // Helpers
        fun xAt(mm: Float) = left + mm * pxPerMm
        fun radPx(diaMm: Float) = 0.5f * diaMm * pxPerMm

        // Optional internal grid (usually disabled when ShaftDrawing provides the grid)
        if (opts.showGrid) {
            drawGrid(layout, opts, textMeasurer)
        }

        // Bodies — full rectangle (top, bottom, left, right)
        spec.bodies.forEach { b ->
            val x0 = xAt(b.startFromAftMm)
            val x1 = xAt(b.startFromAftMm + b.lengthMm)
            val r = radPx(b.diaMm)
            val topEdge = cy - r
            val bottomEdge = cy + r

            // Top & bottom
            drawLine(Color.Black, Offset(x0, topEdge), Offset(x1, topEdge), shaftWidth)
            drawLine(Color.Black, Offset(x0, bottomEdge), Offset(x1, bottomEdge), shaftWidth)
            // Side walls
            drawLine(Color.Black, Offset(x0, topEdge), Offset(x0, bottomEdge), shaftWidth)
            drawLine(Color.Black, Offset(x1, topEdge), Offset(x1, bottomEdge), shaftWidth)
        }

        // Tapers — trapezoid (top/bottom edges + side walls at each end)
        spec.tapers.forEach { t ->
            val x0 = xAt(t.startFromAftMm)
            val x1 = xAt(t.startFromAftMm + t.lengthMm)
            val r0 = radPx(t.startDiaMm)
            val r1 = radPx(t.endDiaMm)

            // Top & bottom sloped edges
            drawLine(Color.Black, Offset(x0, cy - r0), Offset(x1, cy - r1), shaftWidth)
            drawLine(Color.Black, Offset(x0, cy + r0), Offset(x1, cy + r1), shaftWidth)

            // Side walls at the ends
            drawLine(Color.Black, Offset(x0, cy - r0), Offset(x0, cy + r0), shaftWidth)
            drawLine(Color.Black, Offset(x1, cy - r1), Offset(x1, cy + r1), shaftWidth)
        }

        // Threads — OD lines + simple hatch + side walls
        spec.threads.forEach { th ->
            val x0 = xAt(th.startFromAftMm)
            val x1 = xAt(th.startFromAftMm + th.lengthMm)
            val r = radPx(th.majorDiaMm)
            val topEdge = cy - r
            val bottomEdge = cy + r

            // Top & bottom OD bounds (use dimWidth for lighter line)
            drawLine(Color.Black, Offset(x0, topEdge), Offset(x1, topEdge), dimWidth)
            drawLine(Color.Black, Offset(x0, bottomEdge), Offset(x1, bottomEdge), dimWidth)

            // Side walls at thread ends for a complete outline
            drawLine(Color.Black, Offset(x0, topEdge), Offset(x0, bottomEdge), dimWidth)
            drawLine(Color.Black, Offset(x1, topEdge), Offset(x1, bottomEdge), dimWidth)

            // Simple 45° hatch cue spaced by pitch
            val hatchStep = max(8f, th.pitchMm * pxPerMm)
            var hx = x0 + 4f
            while (hx <= x1) {
                drawLine(Color.Black, Offset(hx - 4f, bottomEdge), Offset(hx + 4f, topEdge), dimWidth)
                hx += hatchStep
            }
        }

        // Liners — rectangle (top, bottom, side walls)
        spec.liners.forEach { ln ->
            val x0 = xAt(ln.startFromAftMm)
            val x1 = xAt(ln.startFromAftMm + ln.lengthMm)
            val r = radPx(ln.odMm)
            val topEdge = cy - r
            val bottomEdge = cy + r

            drawLine(Color.Black, Offset(x0, topEdge), Offset(x1, topEdge), dimWidth)
            drawLine(Color.Black, Offset(x0, bottomEdge), Offset(x1, bottomEdge), dimWidth)
            drawLine(Color.Black, Offset(x0, topEdge), Offset(x0, bottomEdge), dimWidth)
            drawLine(Color.Black, Offset(x1, topEdge), Offset(x1, bottomEdge), dimWidth)
        }

        // Overall label (unchanged)
        drawOverall(layout, opts, textMeasurer)
    }

    // ------------------------------------------------------------------------

    @OptIn(ExperimentalTextApi::class)
    private fun DrawScope.drawGrid(
        layout: ShaftLayout.Result,
        opts: RenderOptions,
        textMeasurer: TextMeasurer
    ) {
        // Existing internal grid implementation left intact, since the screen-level
        // ShaftDrawing typically disables this grid in favor of its own unit-aware one.
        // (No centerline drawn here.)
        val left = layout.contentLeftPx
        val right = layout.contentRightPx
        val top = layout.contentTopPx
        val bottom = layout.contentBottomPx

        val width = right - left
        val height = bottom - top

        val majors = max(1, opts.gridDesiredMajors)
        val majorGapPx = width / majors
        val minors = max(1, opts.gridMinorsPerMajor)
        val minorGapPx = majorGapPx / minors
        val useMinor = minorGapPx >= opts.gridMinPixelGap

        // Vertical lines
        var x = left
        var i = 0
        while (x <= right + 0.5f) {
            val isMajor = i % minors == 0
            val strokeWidth = if (isMajor) opts.gridMajorStrokePx else opts.gridMinorStrokePx
            val color = if (isMajor) Color(opts.gridMajorColor) else Color(opts.gridMinorColor)
            if (isMajor || useMinor) drawLine(color, Offset(x, top), Offset(x, bottom), strokeWidth)
            x += minorGapPx; i++
        }

        // Horizontal lines
        var y = top
        i = 0
        while (y <= bottom + 0.5f) {
            val isMajor = i % minors == 0
            val strokeWidth = if (isMajor) opts.gridMajorStrokePx else opts.gridMinorStrokePx
            val color = if (isMajor) Color(opts.gridMajorColor) else Color(opts.gridMinorColor)
            if (isMajor || useMinor) drawLine(color, Offset(left, y), Offset(right, y), strokeWidth)
            y += minorGapPx; i++
        }

        // Optional simple legend bar (kept as before)
        if (opts.showGridLegend) {
            val barW = majorGapPx.coerceAtMost(width - 16f)
            val barH = 6f
            val pad = 8f
            if (barW > 8f) {
                drawRect(
                    color = Color.Black.copy(alpha = 0.6f),
                    topLeft = Offset(right - barW - pad, top + pad),
                    size = androidx.compose.ui.geometry.Size(barW, barH)
                )
            }
        }
    }

    @OptIn(ExperimentalTextApi::class)
    private fun DrawScope.drawOverall(
        layout: ShaftLayout.Result,
        opts: RenderOptions,
        textMeasurer: TextMeasurer
    ) {
        val label = "Overall: ${layout.spec.overallLengthMm.toInt()} mm"
        val style = TextStyle(
            color = Color.Black,
            fontSize = (max(18f, 0.75f * opts.textSizePx) / density).sp,
            platformStyle = PlatformTextStyle(includeFontPadding = false)
        )
        val tl = textMeasurer.measure(AnnotatedString(label), style)
        val tx = (layout.contentLeftPx + layout.contentRightPx - tl.size.width) / 2f
        val ty = layout.contentBottomPx - 6f - tl.size.height
        drawText(tl, topLeft = Offset(tx, ty))
    }
}
