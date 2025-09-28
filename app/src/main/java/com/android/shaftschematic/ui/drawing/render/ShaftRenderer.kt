package com.android.shaftschematic.ui.drawing.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.odMm

/**
 * ShaftRenderer — drop‑in rewrite (v2)
 * - Liners now render exactly like bodies: filled rect + single outline stroke.
 * - No mixed stroke widths on liners.
 */
object ShaftRenderer {

    data class RenderOptions(
        val outlineColor: Color,
        val fillColor: Color,
        val shaftStrokePx: Float,
        val dimStrokePx: Float,
    )

    /** Minimal layout contract used by this renderer. */
    data class Layout(
        val pxPerMm: Float,
        val minXMm: Float,
        val centerlineYPx: Float,
        val contentLeftPx: Float,
        val contentRightPx: Float,
        val contentTopPx: Float,
        val contentBottomPx: Float,
    ) {
        fun xPx(mm: Float): Float = contentLeftPx + (mm - minXMm) * pxPerMm
        fun rPx(diaMm: Float): Float = diaMm * 0.5f * pxPerMm
    }

    /** Adapter to your existing ShaftLayout.Result */
    fun from(layout: ShaftLayout.Result): Layout = Layout(
        pxPerMm = layout.pxPerMm,
        minXMm = layout.minXMm,
        centerlineYPx = layout.centerlineYPx,
        contentLeftPx = layout.contentLeftPx,
        contentRightPx = layout.contentRightPx,
        contentTopPx = layout.contentTopPx,
        contentBottomPx = layout.contentBottomPx,
    )

    fun DrawScope.draw(
        spec: ShaftSpec,
        layout: ShaftLayout.Result,
        opts: RenderOptions,
        textMeasurer: TextMeasurer,
    ) {
        val L = from(layout)
        val cy = L.centerlineYPx

        // Bodies
        spec.bodies.forEach { b ->
            val x0 = L.xPx(b.startFromAftMm)
            val x1 = L.xPx(b.startFromAftMm + b.lengthMm)
            val r  = L.rPx(b.odMm) // If your model uses a different name, alias via extension
            val top = cy - r
            drawRect(
                color = opts.fillColor,
                topLeft = Offset(x0, top),
                size = androidx.compose.ui.geometry.Size(x1 - x0, 2f * r)
            )
            drawRect(
                color = opts.outlineColor,
                topLeft = Offset(x0, top),
                size = androidx.compose.ui.geometry.Size(x1 - x0, 2f * r),
                style = Stroke(width = opts.shaftStrokePx)
            )
        }

        // Tapers — drawn as four lines (two edges + two ends)
        spec.tapers.forEach { t ->
            val x0 = L.xPx(t.startFromAftMm)
            val x1 = L.xPx(t.startFromAftMm + t.lengthMm)
            val r0 = L.rPx(t.startDiaMm)
            val r1 = L.rPx(t.endDiaMm)
            val top0 = cy - r0
            val bot0 = cy + r0
            val top1 = cy - r1
            val bot1 = cy + r1
            // top & bottom edges
            drawLine(opts.outlineColor, Offset(x0, top0), Offset(x1, top1), opts.shaftStrokePx)
            drawLine(opts.outlineColor, Offset(x0, bot0), Offset(x1, bot1), opts.shaftStrokePx)
            // end caps
            drawLine(opts.outlineColor, Offset(x0, top0), Offset(x0, bot0), opts.shaftStrokePx)
            drawLine(opts.outlineColor, Offset(x1, top1), Offset(x1, bot1), opts.shaftStrokePx)
        }

        // Threads — simple outer envelope (fill optional)
        spec.threads.forEach { th ->
            val x0 = L.xPx(th.startFromAftMm)
            val x1 = L.xPx(th.startFromAftMm + th.lengthMm)
            val r  = L.rPx(th.odMm) // If different name, alias via extension
            val top = cy - r
            drawRect(
                color = opts.fillColor,
                topLeft = Offset(x0, top),
                size = androidx.compose.ui.geometry.Size(x1 - x0, 2f * r)
            )
            drawRect(
                color = opts.outlineColor,
                topLeft = Offset(x0, top),
                size = androidx.compose.ui.geometry.Size(x1 - x0, 2f * r),
                style = Stroke(width = opts.shaftStrokePx)
            )
        }

        // Liners — draw exactly like bodies (consistent thickness)
        spec.liners.forEach { ln ->
            val x0 = L.xPx(ln.startFromAftMm)
            val x1 = L.xPx(ln.startFromAftMm + ln.lengthMm)
            val r  = L.rPx(ln.odMm)
            val top = cy - r
            drawRect(
                color = opts.fillColor,
                topLeft = Offset(x0, top),
                size = androidx.compose.ui.geometry.Size(x1 - x0, 2f * r)
            )
            drawRect(
                color = opts.outlineColor,
                topLeft = Offset(x0, top),
                size = androidx.compose.ui.geometry.Size(x1 - x0, 2f * r),
                style = Stroke(width = opts.shaftStrokePx)
            )
        }
    }
}
