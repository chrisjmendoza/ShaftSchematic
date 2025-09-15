package com.android.shaftschematic.ui.drawing.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.android.shaftschematic.data.BodySegmentSpec
import com.android.shaftschematic.data.LinerSpec
import com.android.shaftschematic.data.ShaftSpecMm
import com.android.shaftschematic.data.TaperSpec
import com.android.shaftschematic.data.ThreadSpec
import kotlin.math.max

/**
 * Renders the shaft geometry (bodies, tapers, threads, liners).
 *
 * EXPECTED FIELDS on ShaftLayout.Result (rename here if yours differ):
 *  - spec: ShaftSpecMm                // the input spec used for layout
 *  - pxPerMm: Float                   // uniform scale (px per mm) for X and diameters
 *  - contentLeftPx: Float             // left drawing bound (inside padding)
 *  - contentRightPx: Float            // right drawing bound (inside padding)
 *  - contentTopPx: Float              // top drawing bound (inside padding)
 *  - contentBottomPx: Float           // bottom drawing bound (inside padding)
 *  - centerlineYPx: Float             // vertical center of the shaft axis in px
 *
 * If your layout provides separate X/Y scales, change `pxPerMm` usage for diameters to your Y scale.
 */
class ShaftRenderer {

    // ------------- paints -------------
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        strokeJoin = Paint.Join.MITER
        strokeCap = Paint.Cap.BUTT
    }

    private val thinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        strokeCap = Paint.Cap.BUTT
    }

    private val hatchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    /**
     * Main entry. Draws the entire shaft.
     */
    fun render(canvas: Canvas, layout: ShaftLayout.Result, opts: RenderOptions) {
        // Apply configured line widths/colors
        outlinePaint.strokeWidth = opts.lineWidthPx
        thinPaint.strokeWidth = max(1f, opts.dimLineWidthPx) // used for inner details (like thread hatch)

        // Colors: keep it simple (black). If your layout exposes colors, set them here.
        outlinePaint.color = 0xFF000000.toInt()
        thinPaint.color = 0xFF000000.toInt()
        hatchPaint.color = 0xFF000000.toInt()

        val spec: ShaftSpecMm = layout.spec

        // Draw order: bodies → tapers → threads → liners
        // (If you prefer bodies under everything, leave as-is.)
        spec.bodies.forEach { drawBodySegment(canvas, layout, opts, it) }
        spec.tapers.forEach { drawTaperSegment(canvas, layout, opts, it) }
        spec.aftTaper?.let { drawTaperSegment(canvas, layout, opts, it) }
        spec.forwardTaper?.let { drawTaperSegment(canvas, layout, opts, it) }
        spec.threads.forEach { drawThreadSegment(canvas, layout, opts, it) }
        spec.liners.forEach { drawLinerSegment(canvas, layout, opts, it) }

        // (Dimension lines / labels are usually drawn in the Layout or another helper;
        //  if you want them here, we can add a drawDimensions(...) pass.)
    }

    // ---------------- bodies ----------------

    private fun drawBodySegment(
        canvas: Canvas,
        layout: ShaftLayout.Result,
        opts: RenderOptions,
        b: BodySegmentSpec
    ) {
        val pxPerMm = layout.pxPerMm
        val x0 = layout.contentLeftPx + b.startFromAftMm * pxPerMm
        val x1 = x0 + b.lengthMm * pxPerMm
        val cy = layout.centerlineYPx
        val r = (b.diaMm * pxPerMm) * 0.5f
        val yTop = cy - r
        val yBot = cy + r

        // Body as a rectangle centered on centerline
        canvas.drawLine(x0, yTop, x1, yTop, outlinePaint)
        canvas.drawLine(x0, yBot, x1, yBot, outlinePaint)
        canvas.drawLine(x0, yTop, x0, yBot, outlinePaint)
        canvas.drawLine(x1, yTop, x1, yBot, outlinePaint)

        // Optional: compression mark (wavy) if shortened in drawing (not implemented here).
        // If you keep a compression flag/factor, we can draw a squiggle in the middle.
    }

    // ---------------- tapers (TRAPEZOID) ----------------

    private fun drawTaperSegment(
        canvas: Canvas,
        layout: ShaftLayout.Result,
        opts: RenderOptions,
        t: TaperSpec
    ) {
        val pxPerMm = layout.pxPerMm
        val x0 = layout.contentLeftPx + t.startFromAftMm * pxPerMm
        val x1 = x0 + t.lengthMm * pxPerMm

        val cy = layout.centerlineYPx
        val r0 = (t.startDiaMm * pxPerMm) * 0.5f
        val r1 = (t.endDiaMm   * pxPerMm) * 0.5f

        val y0Top = cy - r0
        val y0Bot = cy + r0
        val y1Top = cy - r1
        val y1Bot = cy + r1

        // Build a trapezoid path: top-left → top-right → bottom-right → bottom-left → close
        val path = Path().apply {
            moveTo(x0, y0Top)
            lineTo(x1, y1Top)
            lineTo(x1, y1Bot)
            lineTo(x0, y0Bot)
            close()
        }

        // Stroke the outline (fill is optional & left transparent)
        canvas.drawPath(path, outlinePaint)
    }

    // ---------------- threads ----------------

    private fun drawThreadSegment(
        canvas: Canvas,
        layout: ShaftLayout.Result,
        opts: RenderOptions,
        th: ThreadSpec
    ) {
        val pxPerMm = layout.pxPerMm
        val x0 = layout.contentLeftPx + th.startFromAftMm * pxPerMm
        val x1 = x0 + th.lengthMm * pxPerMm
        val cy = layout.centerlineYPx
        val r = (th.majorDiaMm * pxPerMm) * 0.5f
        val yTop = cy - r
        val yBot = cy + r

        // Outline box for the threaded region
        canvas.drawLine(x0, yTop, x1, yTop, outlinePaint)
        canvas.drawLine(x0, yBot, x1, yBot, outlinePaint)
        canvas.drawLine(x0, yTop, x0, yBot, outlinePaint)
        canvas.drawLine(x1, yTop, x1, yBot, outlinePaint)

        // Simple hatch to indicate thread
        // Spacing derived from pitch if present; fallback to 3 mm visual
        val pitchMm = if (th.pitchMm > 0f) th.pitchMm else 3f
        val stepPx = pitchMm * pxPerMm

        var x = x0 + stepPx * 0.5f
        while (x < x1 - 1f) {
            // small diagonal strokes top→bottom to suggest threads
            canvas.drawLine(x - stepPx * 0.3f, yTop, x + stepPx * 0.3f, yBot, thinPaint)
            x += stepPx
        }
    }

    // ---------------- liners ----------------

    private fun drawLinerSegment(
        canvas: Canvas,
        layout: ShaftLayout.Result,
        opts: RenderOptions,
        ln: LinerSpec
    ) {
        val pxPerMm = layout.pxPerMm
        val x0 = layout.contentLeftPx + ln.startFromAftMm * pxPerMm
        val x1 = x0 + ln.lengthMm * pxPerMm
        val cy = layout.centerlineYPx
        val r = (ln.odMm * pxPerMm) * 0.5f
        val yTop = cy - r
        val yBot = cy + r

        // Outline the liner region (same as a body, usually smaller Ø)
        canvas.drawLine(x0, yTop, x1, yTop, outlinePaint)
        canvas.drawLine(x0, yBot, x1, yBot, outlinePaint)
        canvas.drawLine(x0, yTop, x0, yBot, outlinePaint)
        canvas.drawLine(x1, yTop, x1, yBot, outlinePaint)
    }
}
