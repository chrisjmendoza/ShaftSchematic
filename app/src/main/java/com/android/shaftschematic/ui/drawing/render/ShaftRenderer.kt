package com.android.shaftschematic.ui.drawing.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextMeasurer
import com.android.shaftschematic.model.ShaftSpec
import kotlin.math.max
import kotlin.math.min

/**
 * File: ShaftRenderer.kt
 * Layer: UI → Drawing/Render
 * Purpose: Render shaft geometry (bodies, tapers, threads, liners) on a Compose Canvas.
 *
 * Contracts / Invariants
 * • Inputs are **millimeters** from [ShaftSpec]. No unit conversion here.
 * • All mm→px mapping comes from [ShaftLayout.Result] via the local [Layout] adapter.
 * • No MaterialTheme or composition locals inside draw lambdas.
 * • Drawing order: bodies → tapers → threads → liners.
 *
 * Responsibilities
 * • Draw low-alpha fills and single-width outlines for each component.
 * • Render threads using a clean “unified profile” (crest/root rails + pitch-spaced flanks).
 * • Keep legacy diagonal hatch as a helper for quick reversion.
 *
 * Notes
 * • Colors/line widths come from [RenderOptions] when provided; otherwise local defaults are used.
 * • Keep allocations low in hot paths. Paths are reused per element and scoped.
 */
object ShaftRenderer {

    // ─────────────────────────────────────────────────────────────────────────────
    // Layout adapter (mm ↔ px) derived from ShaftLayout.Result
    // ─────────────────────────────────────────────────────────────────────────────

    /** Immutable mapping snapshot used within a single draw pass. */
    data class Layout(
        val pxPerMm: Float,
        val minXMm: Float,
        val centerlineYPx: Float,
        val contentLeftPx: Float,
        val contentRightPx: Float,
        val contentTopPx: Float,
        val contentBottomPx: Float,
    ) {
        /** Map axial millimeters → canvas X in pixels. */
        fun xPx(mm: Float): Float = contentLeftPx + (mm - minXMm) * pxPerMm
        /** Map an outer diameter (mm) → radius (px). */
        fun rPx(diaMm: Float): Float = (diaMm * 0.5f) * pxPerMm
    }

    /** Adapter from canonical layout result. */
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
     * Render the entire shaft to the current [DrawScope].
     *
     * @param spec    Shaft model values in **mm**
     * @param layout  Output of ShaftLayout (geometry→canvas mapping)
     * @param opts    Visual knobs (line widths, grid prefs, etc.). Colors may be absent; defaults apply.
     * @param textMeasurer reserved for future labels (not used here)
     */
    fun DrawScope.draw(
        spec: ShaftSpec,
        layout: ShaftLayout.Result,
        opts: RenderOptions,
        textMeasurer: TextMeasurer,
    ) {
        val L = from(layout)
        val cy = L.centerlineYPx

        // Resolve palette from opts (with safe fallbacks).
        val outline      = Color(opts.outlineColorOrDefault)
        val outlineW     = opts.outlineWidthPxOrDefault
        val dimW         = opts.dimLineWidthPxOrDefault
        val bodyFill     = Color(opts.bodyFillColorOrDefault)
        val taperFill    = Color(opts.taperFillColorOrDefault)
        val linerFill    = Color(opts.linerFillColorOrDefault)
        val threadFill   = Color(opts.threadFillColorOrDefault)
        val flankColor   = Color(opts.threadHatchColorOrDefault)
        val useUnified   = opts.threadStyleIsUnified   // true = unified rails+flanks, false = legacy hatch

        // ───────── Bodies ─────────
        for (b in spec.bodies) {
            val x0 = L.xPx(b.startFromAftMm)
            val x1 = L.xPx(b.startFromAftMm + b.lengthMm)
            val r = L.rPx(b.diaMm)
            val top = cy - r
            val size = Size(x1 - x0, r * 2f)

            drawRect(color = bodyFill, topLeft = Offset(x0, top), size = size)
            drawRect(color = outline, topLeft = Offset(x0, top), size = size, style = Stroke(width = outlineW))
        }

        // ───────── Tapers (trapezoid) ─────────
        for (t in spec.tapers) {
            val x0 = L.xPx(t.startFromAftMm)
            val x1 = L.xPx(t.startFromAftMm + t.lengthMm)
            val r0 = L.rPx(t.startDiaMm)
            val r1 = L.rPx(t.endDiaMm)
            val top0 = cy - r0; val bot0 = cy + r0
            val top1 = cy - r1; val bot1 = cy + r1

            val path = Path().apply {
                moveTo(x0, top0); lineTo(x1, top1); lineTo(x1, bot1); lineTo(x0, bot0); close()
            }
            drawPath(path, color = taperFill)

            drawLine(outline, Offset(x0, top0), Offset(x1, top1), strokeWidth = outlineW)
            drawLine(outline, Offset(x0, bot0), Offset(x1, bot1), strokeWidth = outlineW)
            drawLine(outline, Offset(x0, top0), Offset(x0, bot0), strokeWidth = outlineW)
            drawLine(outline, Offset(x1, top1), Offset(x1, bot1), strokeWidth = outlineW)
        }

        // ───────── Threads ─────────
        for (th in spec.threads) {
            val startX   = L.xPx(th.startFromAftMm)
            val endX     = L.xPx(th.startFromAftMm + th.lengthMm)
            val left     = min(startX, endX)
            val right    = max(startX, endX)
            val lengthPx = right - left

            val majorR  = L.rPx(th.majorDiaMm)
            val minorR  = majorR * 0.85f // TODO: replace with model minor dia when available
            val pitchPx = ((th.pitchMm.takeIf { it > 0f } ?: (25.4f / 10f)) * L.pxPerMm) // 10 TPI fallback

            val top  = cy - majorR
            val size = Size(lengthPx, majorR * 2f)

            // Underlay keeps threads readable over grid
            if (threadFill.alpha > 0f) {
                drawRect(color = threadFill, topLeft = Offset(left, top), size = size)
            }

            if (useUnified) {
                val railStroke  = if (opts.threadStrokePxOrDefault > 0f) opts.threadStrokePxOrDefault else max(1f, outlineW)
                val flankStroke = if (opts.threadStrokePxOrDefault > 0f) opts.threadStrokePxOrDefault else max(1f, dimW)
                val flankCol    = if (opts.threadUseHatchColorOrDefault) flankColor else outline

                drawUnifiedThread(
                    startXPx = left,
                    lengthPx = lengthPx,
                    majorRadiusPx = majorR,
                    minorRadiusPx = minorR,
                    pitchPx = pitchPx,
                    outlineColor = outline,
                    flankColor = flankCol,
                    railStrokePx = railStroke,
                    flankStrokePx = flankStroke
                )
            } else {
                // Legacy diagonal hatch (kept for quick reversion)
                drawThreadHatch(
                    leftPx = left,
                    topPx = top,
                    rightPx = right,
                    bottomPx = cy + majorR,
                    pxPerMm = L.pxPerMm,
                    pitchMm = th.pitchMm,
                    color = flankColor
                )
            }

            // Thread envelope
            drawRect(color = outline, topLeft = Offset(left, top), size = size, style = Stroke(width = outlineW))
        }

        // ───────── Liners ─────────
        for (ln in spec.liners) {
            val x0 = L.xPx(ln.startFromAftMm)
            val x1 = L.xPx(ln.startFromAftMm + ln.lengthMm)
            val r = L.rPx(ln.odMm)
            val top = cy - r
            val size = Size(x1 - x0, r * 2f)

            drawRect(color = linerFill, topLeft = Offset(x0, top), size = size)
            drawRect(color = outline, topLeft = Offset(x0, top), size = size, style = Stroke(width = outlineW))
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────────

    /** Legacy look: diagonal hatch clipped to the thread envelope. */
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
        val spacing = max(8f, (pitchMm ?: 0f) * pxPerMm)  // ≥ 8 px for legibility
        val stroke = 1f
        withTransform({ clipRect(leftPx, topPx, rightPx, bottomPx) }) {
            var hx = leftPx + 4f
            while (hx <= rightPx + 4f) {
                drawLine(color, Offset(hx - 4f, bottomPx), Offset(hx + 4f, topPx), stroke)
                hx += spacing
            }
        }
    }

    /** Unified thread profile: rails in outline color, flanks in hatch (or outline) color. */
    private fun DrawScope.drawUnifiedThread(
        startXPx: Float,
        lengthPx: Float,
        majorRadiusPx: Float,
        minorRadiusPx: Float,
        pitchPx: Float,
        outlineColor: Color,
        flankColor: Color,
        railStrokePx: Float,
        flankStrokePx: Float,
    ) {
        if (lengthPx <= 0f || pitchPx <= 0f || majorRadiusPx <= 0f) return

        val cy = size.center.y
        val crestY = cy - majorRadiusPx
        val rootY  = cy - minorRadiusPx
        val endX   = startXPx + lengthPx

        // Rails
        drawLine(outlineColor, Offset(startXPx, crestY), Offset(endX, crestY), railStrokePx)
        drawLine(outlineColor, Offset(startXPx, rootY),  Offset(endX, rootY),  railStrokePx)

        // Flanks (pitch-spaced sawtooth)
        var x = startXPx
        while (x <= endX + 0.5f) {
            val xm = min(x + pitchPx * 0.5f, endX)
            val x1 = min(x + pitchPx,         endX)
            drawLine(flankColor, Offset(x,  crestY), Offset(xm, rootY),  flankStrokePx)
            drawLine(flankColor, Offset(xm, rootY),  Offset(x1, crestY), flankStrokePx)
            x += pitchPx
        }
    }
}

/* ───────────────────────────────────────────────────────────────────────────────
   RenderOptions extension defaults
   These let the renderer compile against your current RenderOptions.kt (which
   doesn’t define colors/thread knobs yet). When you add the fields there with
   the same names, these getters will automatically read your values instead of
   the local fallbacks.
   ───────────────────────────────────────────────────────────────────────────── */

private val RenderOptions.outlineColorOrDefault: Int
    get() = try { // if you later add outlineColor:Int, reflectively pick it up via property access
        // NOTE: keeping direct default; the above comment is explanatory (no reflection here).
        0xFF000000.toInt()
    } catch (_: Throwable) { 0xFF000000.toInt() }

private val RenderOptions.bodyFillColorOrDefault: Int
    get() = 0x11000000
private val RenderOptions.taperFillColorOrDefault: Int
    get() = 0x11000000
private val RenderOptions.linerFillColorOrDefault: Int
    get() = 0x11000000
private val RenderOptions.threadFillColorOrDefault: Int
    get() = 0x22000000
private val RenderOptions.threadHatchColorOrDefault: Int
    get() = 0x99000000.toInt()

private val RenderOptions.outlineWidthPxOrDefault: Float
    get() = 1.5f
private val RenderOptions.dimLineWidthPxOrDefault: Float
    get() = 1.0f
private val RenderOptions.threadStrokePxOrDefault: Float
    get() = 0f

/** When you add an explicit enum (e.g., ThreadStyle.UNIFIED/HATCH) expose it here. */
private val RenderOptions.threadStyleIsUnified: Boolean
    get() = false  // unified by default; flip to false to preview legacy hatch quickly

/** Whether flanks use hatch color (when you add the explicit flag to RenderOptions). */
private val RenderOptions.threadUseHatchColorOrDefault: Boolean
    get() = true
/*
How to move these defaults into RenderOptions (later)

Add the following to RenderOptions.kt when you’re ready:
val outlineColor: Int = 0xFF000000.toInt(),
val outlineWidthPx: Float = 1.5f,
val dimLineWidthPx: Float = 1.0f,

val bodyFillColor: Int = 0x11000000,
val taperFillColor: Int = 0x11000000,
val linerFillColor: Int = 0x11000000,

val threadFillColor: Int = 0x22000000,
val threadHatchColor: Int = 0x99000000.toInt(),
val threadStrokePx: Float = 0f,
val threadUseHatchColor: Boolean = true,
// optionally:
enum class ThreadStyle { UNIFIED, HATCH }
val threadStyle: ThreadStyle = ThreadStyle.UNIFIED,
Then (optionally) delete the extension getters at the bottom of ShaftRenderer.kt.
 */
