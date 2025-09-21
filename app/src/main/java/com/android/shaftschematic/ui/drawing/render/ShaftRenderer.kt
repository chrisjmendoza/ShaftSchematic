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
import com.android.shaftschematic.model.ShaftSpec
import kotlin.math.max
import kotlin.math.min

/**
 * Compose-first shaft drawing renderer.
 *
 * ## Responsibilities
 * - Draws the engineering grid (optional), shaft geometry (bodies, tapers, threads, liners),
 *   centerline/ticks, and text labels.
 * - Consumes a precomputed [ShaftLayout.Result] that supplies the mm→px mapping and content bounds.
 * - Treats [com.android.shaftschematic.model.ShaftSpec] geometry as **canonical millimeters**.
 *   Unit-dependent behavior (e.g., grid legend in inches vs mm) is controlled via [RenderOptions].
 *
 * ## Usage
 * ```kotlin
 * val layout = ShaftLayout.compute(spec, 0f, 0f, size.width, size.height)
 * val opts = RenderOptions(showGrid = true, gridUseInches = unit == UnitSystem.INCHES)
 * with(this) {                      // this = DrawScope inside Canvas { ... }
 *   ShaftRenderer.draw(layout, opts, textMeasurer)
 * }
 * ```
 *
 * The renderer is pure w.r.t. its inputs (layout/options/spec) and does not hold state.
 * Call from a Compose `Canvas` block on the UI thread.
 */
object ShaftRenderer {

    /**
     * Render the shaft into the current [DrawScope].
     *
     * @receiver The active [DrawScope] provided by a Compose `Canvas { … }` block.
     * @param layout A precomputed layout describing the content rect, centerline, and mm→px scale.
     * @param opts Rendering options, including grid visibility and legend/line styling.
     * @param textMeasurer A Compose text measurer supplied by the caller to ensure text
     *                     measurement/layout is consistent with the preview host.
     */
    @OptIn(ExperimentalTextApi::class)
    fun DrawScope.draw(
        layout: ShaftLayout.Result,
        opts: RenderOptions,
        textMeasurer: TextMeasurer
    ) {
        val spec: ShaftSpec = layout.spec

        // Use simple width floats for Compose drawLine
        val shaftWidth = opts.lineWidthPx
        val dimWidth   = opts.dimLineWidthPx

        // Optional grid underlay
        if (opts.showGrid) drawGrid(layout, opts, textMeasurer)

        val cy = layout.centerlineYPx

        // Centerline
        drawLine(
            color = Color.Black,
            start = Offset(layout.contentLeftPx, cy),
            end   = Offset(layout.contentRightPx, cy),
            strokeWidth = dimWidth
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

            // Ticks
            val top0 = cy - r0
            drawLine(Color.Black, Offset(x0, top0 - 6f), Offset(x0, top0 + 6f), dimWidth)
            val top1 = cy - r1
            drawLine(Color.Black, Offset(x1, top1 - 6f), Offset(x1, top1 + 6f), dimWidth)
        }

        // Threads (render a simple hatch inside the OD bounds)
        spec.threads.forEach { th ->
            val x0 = xAt(th.startFromAftMm)
            val x1 = xAt(th.startFromAftMm + th.lengthMm)
            val r  = radPx(th.majorDiaMm)
            val top = cy - r
            val bottom = cy + r

            // OD lines
            drawLine(Color.Black, Offset(x0, top), Offset(x1, top), dimWidth)
            drawLine(Color.Black, Offset(x0, bottom), Offset(x1, bottom), dimWidth)

            // 45° hatch based on pitch (fallback to 8 px if pitch==0)
            val hatchStep = max(8f, th.pitchMm * layout.pxPerMm)
            var hx = x0 + 4f
            while (hx <= x1) {
                drawLine(Color.Black, Offset(hx - 4f, bottom), Offset(hx + 4f, top), dimWidth)
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

    @OptIn(ExperimentalTextApi::class)
    fun drawOn(
        scope: DrawScope,
        layout: ShaftLayout.Result,
        opts: RenderOptions,
        textMeasurer: TextMeasurer
    ) {
        with(scope) { draw(layout, opts, textMeasurer) }
    }

    /**
     * Draw the background engineering grid inside the content rect defined by [layout].
     *
     * Grid cadence and legend units are driven by [RenderOptions], not by the underlying geometry,
     * which remains in millimeters.
     *
     * @receiver The active [DrawScope].
     * @param layout Content bounds and mm→px scale.
     * @param opts Grid styling (visibility, major/minor strokes, legend).
     */
    private fun DrawScope.drawGrid(layout: ShaftLayout.Result, opts: RenderOptions, textMeasurer: TextMeasurer) {
        val width  = layout.contentRightPx - layout.contentLeftPx
        val height = layout.contentBottomPx - layout.contentTopPx

        // Try to place desired number of majors across width
        val majors = max(1, opts.gridDesiredMajors)
        val majorGapPx = width / majors
        val minors = max(1, opts.gridMinorsPerMajor)
        val minorGapPx = majorGapPx / minors

        val useMinor = minorGapPx >= opts.gridMinPixelGap

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

            val legendSize = if (opts.legendTextSizePx > 0f) opts.legendTextSizePx else 0.6f * opts.textSizePx
            val style = TextStyle(
                color = Color(opts.legendTextColor),
                fontSize = (legendSize / density).sp,
                platformStyle = PlatformTextStyle(includeFontPadding = false)
            )
            val oneMajorMm = majorGapPx / layout.pxPerMm
            val legend = if (opts.gridUseInches) {
                val inches = oneMajorMm / 25.4f
                "%.2f in".format(inches)
            } else {
                if (oneMajorMm >= 100f) "${oneMajorMm.toInt()} mm" else "%.1f mm".format(oneMajorMm)
            }
            val textLayout = textMeasurer.measure(AnnotatedString(legend), style)
            drawText(
                textLayout,
                topLeft = Offset(layout.contentRightPx - textLayout.size.width - pad, layout.contentTopPx + pad + barH + 6f)
            )
        }
    }

    /**
     * Draw a single overall-length label aligned near the bottom of the content rect.
     *
     * Display units follow [RenderOptions.gridUseInches]; geometry remains canonical (mm).
     *
     * @receiver The active [DrawScope].
     * @param layout Content bounds and mm→px scale.
     * @param opts Text styling hints (via [RenderOptions]).
     * @param textMeasurer Caller-supplied measurer for consistent typography.
     */
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
