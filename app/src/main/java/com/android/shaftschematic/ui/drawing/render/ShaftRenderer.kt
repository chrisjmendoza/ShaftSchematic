package com.android.shaftschematic.ui.drawing.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextMeasurer
import com.android.shaftschematic.model.ShaftSpec

/**
 * File: ShaftRenderer.kt
 * Layer: UI → Drawing/Render
 * Purpose: Render the shaft geometry (bodies, tapers, liners, threads) onto a Compose Canvas.
 *
 * Contracts / Invariants
 *  • **Model is millimeters**: All inputs read from [ShaftSpec] are in mm. This renderer performs
 *    **no unit conversion** and must remain unit‑agnostic.
 *  • Layout mapping is provided by [ShaftLayout.Result] and adapted here. All mm→px math goes
 *    through that mapping (xPx/rPx/pxPerMm). Renderer never stores density or scale globally.
 *  • Drawing order is: bodies → tapers → threads → liners (so liners sit above bodies when overlapping).
 *
 * Responsibilities
 *  • Draw filled shapes and single‑width outlines for consistent visual thickness across components.
 *  • Render thread regions with a clipped diagonal hatch whose spacing follows pitchMm when provided.
 *  • Avoid allocations in hot paths; keep temporary math local.
 *
 * Notes
 *  • If visual hierarchy needs to change (e.g., show threads on top), reorder the component loops.
 *  • Do not read UI unit state here; conversion happens in the screen layer. Pixel styling (colors,
 *    stroke widths) are injected via [RenderOptions].
 */
object ShaftRenderer {

    // ─────────────────────────────────────────────────────────────────────────────
    // Public styling options injected by the caller (screen/drawing wrapper).
    // ─────────────────────────────────────────────────────────────────────────────

    /** Visual settings for outlines/fills. All stroke widths are in **pixels**. */
    data class RenderOptions(
        val outline: Color,
        val outlineWidthPx: Float,
        val bodyFill: Color,
        val linerFill: Color,
        val taperFill: Color,
        val threadFill: Color,
        val threadHatch: Color = Color.Black,
    )

    // ─────────────────────────────────────────────────────────────────────────────
    // Minimal layout contract adapter (mm→px mapping)
    // ─────────────────────────────────────────────────────────────────────────────

    /** Snapshot of the layout mapping used during a draw pass. */
    data class Layout(
        val pxPerMm: Float,
        val minXMm: Float,
        val centerlineYPx: Float,
        val contentLeftPx: Float,
        val contentRightPx: Float,
        val contentTopPx: Float,
        val contentBottomPx: Float,
    ) {
        /** Maps axial position in millimeters to canvas X in pixels. */
        fun xPx(mm: Float): Float = contentLeftPx + (mm - minXMm) * pxPerMm
        /** Maps outer diameter (mm) to radius (px) using current scale. */
        fun rPx(diaMm: Float): Float = (diaMm * 0.5f) * pxPerMm
    }

    /** Adapter from the canonical ShaftLayout.Result. */
    fun from(layout: ShaftLayout.Result): Layout = Layout(
        pxPerMm = layout.pxPerMm,
        minXMm = layout.minXMm,
        centerlineYPx = layout.centerlineYPx,
        contentLeftPx = layout.contentLeftPx,
        contentRightPx = layout.contentRightPx,
        contentTopPx = layout.contentTopPx,
        contentBottomPx = layout.contentBottomPx,
    )

    // ─────────────────────────────────────────────────────────────────────────────
    // Entry point
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Render the entire shaft.
     * @param spec Model in **mm**
     * @param layout Layout mapping computed by [ShaftLayout]
     * @param opts Colors and stroke widths (px)
     * @param textMeasurer Reserved for future labels; currently unused here
     */
    fun DrawScope.draw(
        spec: ShaftSpec,
        layout: ShaftLayout.Result,
        opts: RenderOptions,
        textMeasurer: TextMeasurer,
    ) {
        val L = from(layout)
        val cy = L.centerlineYPx

        // Bodies (filled rectangle + single outline)
        for (b in spec.bodies) {
            val x0 = L.xPx(b.startFromAftMm)
            val x1 = L.xPx(b.startFromAftMm + b.lengthMm)
            val r = L.rPx(b.diaMm)
            val top = cy - r
            val size = Size(x1 - x0, r * 2f)

            drawRect(color = opts.bodyFill, topLeft = Offset(x0, top), size = size)
            drawRect(color = opts.outline, topLeft = Offset(x0, top), size = size,
                style = Stroke(width = opts.outlineWidthPx))
        }

        // Tapers — fill the actual trapezoid (quad), then draw edges and end caps
        for (t in spec.tapers) {
            val x0 = L.xPx(t.startFromAftMm)
            val x1 = L.xPx(t.startFromAftMm + t.lengthMm)
            val r0 = L.rPx(t.startDiaMm)
            val r1 = L.rPx(t.endDiaMm)
            val top0 = cy - r0
            val bot0 = cy + r0
            val top1 = cy - r1
            val bot1 = cy + r1

            // Fill the quad (trapezoid) to match engineering drawing convention
            val path = Path().apply {
                moveTo(x0, top0)   // top-left
                lineTo(x1, top1)   // top-right (slope)
                lineTo(x1, bot1)   // bottom-right
                lineTo(x0, bot0)   // bottom-left (slope)
                close()
            }
            drawPath(path = path, color = opts.taperFill)

            // Edges
            drawLine(color = opts.outline, start = Offset(x0, top0), end = Offset(x1, top1), strokeWidth = opts.outlineWidthPx)
            drawLine(color = opts.outline, start = Offset(x0, bot0), end = Offset(x1, bot1), strokeWidth = opts.outlineWidthPx)
            // End caps
            drawLine(color = opts.outline, start = Offset(x0, top0), end = Offset(x0, bot0), strokeWidth = opts.outlineWidthPx)
            drawLine(color = opts.outline, start = Offset(x1, top1), end = Offset(x1, bot1), strokeWidth = opts.outlineWidthPx)
        }

        // Threads (filled rect + outline + diagonal hatch clipped to rect)
        for (th in spec.threads) {
            val x0 = L.xPx(th.startFromAftMm)
            val x1 = L.xPx(th.startFromAftMm + th.lengthMm)
            val r = L.rPx(th.majorDiaMm)
            val top = cy - r
            val bot = cy + r
            val left = minOf(x0, x1)
            val right = maxOf(x0, x1)
            val size = Size(right - left, bot - top)

            // Fill + outline envelope
            drawRect(color = opts.threadFill, topLeft = Offset(left, top), size = size)
            drawRect(color = opts.outline,    topLeft = Offset(left, top), size = size,
                style = Stroke(width = opts.outlineWidthPx))

            // Diagonal hatch — spacing tied to pitch when available; clipped to rect bounds.
            drawThreadHatch(
                leftPx = left,
                topPx = top,
                rightPx = right,
                bottomPx = bot,
                pxPerMm = L.pxPerMm,
                pitchMm = th.pitchMm,
                color = opts.threadHatch
            )
        }

        // Liners (identical to bodies: filled rect + outline)
        for (ln in spec.liners) {
            val x0 = L.xPx(ln.startFromAftMm)
            val x1 = L.xPx(ln.startFromAftMm + ln.lengthMm)
            val r = L.rPx(ln.odMm)
            val top = cy - r
            val size = Size(x1 - x0, r * 2f)

            drawRect(color = opts.linerFill, topLeft = Offset(x0, top), size = size)
            drawRect(color = opts.outline,   topLeft = Offset(x0, top), size = size,
                style = Stroke(width = opts.outlineWidthPx))
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Draw short diagonal segments across a rectangle to imply external threads.
     * Spacing follows physical pitch when provided; otherwise a readable pixel fallback is used.
     * Everything here is **px‑space** (conversion already done via layout).
     */
    private fun DrawScope.drawThreadHatch(
        leftPx: Float,
        topPx: Float,
        rightPx: Float,
        bottomPx: Float,
        pxPerMm: Float,
        pitchMm: Float?,
        color: Color,
    ) {
        if (rightPx <= leftPx || bottomPx <= topPx || pxPerMm <= 0f) return

        val hatchMinPx = 8f                         // minimum gap so lines are legible
        val spacing = kotlin.math.max(
            hatchMinPx,
            (pitchMm ?: 0f) * pxPerMm
        )
        val stroke = kotlin.math.max(0.75f, 1.0f * this.density)

        val topEdge = topPx
        val botEdge = bottomPx

        // Clip to the thread rect to prevent bleed into neighbors
        withTransform({ clipRect(leftPx, topPx, rightPx, bottomPx) }) {
            var hx = leftPx + 4f
            while (hx <= rightPx + 4f) {
                // 8px diagonal segment (same visual as the original implementation)
                drawLine(
                    color = color,
                    start = Offset(hx - 4f, botEdge),
                    end = Offset(hx + 4f, topEdge),
                    strokeWidth = stroke
                )
                hx += spacing
            }
        }
    }
}
