package com.android.shaftschematic.ui.drawing.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextMeasurer
import com.android.shaftschematic.geom.keywaySpoonBowl
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.LinerAuthoredReference
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.hasKeyway
import com.android.shaftschematic.model.hiddenKeywayHostIds
import com.android.shaftschematic.model.maxOuterDiaMm
import com.android.shaftschematic.ui.resolved.ResolvedBody
import com.android.shaftschematic.ui.resolved.ResolvedComponent
import com.android.shaftschematic.ui.resolved.ResolvedComponentType
import com.android.shaftschematic.ui.resolved.ResolvedCouplerBoltSlot
import com.android.shaftschematic.ui.resolved.maxDiaMm
import com.android.shaftschematic.ui.resolved.ResolvedLiner
import com.android.shaftschematic.ui.resolved.ResolvedTaper
import com.android.shaftschematic.ui.resolved.ResolvedThread
import kotlin.math.max
import kotlin.math.min

/**
 * File: ShaftRenderer.kt
 * Layer: UI → Drawing/Render
 *
 * Purpose
 * Render shaft geometry (bodies, tapers, threads, liners) on a Compose Canvas.
 *
 * Contracts / Invariants
 * • Inputs are **millimeters** from [ShaftSpec]. No unit conversion here.
 * • All mm→px mapping comes from [ShaftLayout.Result] via the local [Layout] adapter.
 * • No MaterialTheme or composition locals inside draw lambdas.
 * • Drawing order: bodies → tapers → threads → liners.
 *
 * Notes
 * • Colors/line widths come from [RenderOptions].
 * • Keep allocations low in hot paths. Paths are reused per element and scoped.
 * • Highlight outline: when enabled & an ID matches, we paint a two-ring under-stroke (glow + edge)
 *   then the normal stroke on top. When highlight is off, visuals are identical to legacy.
 */
/**
 * Hidden-line dash for far-side (180°-apart) keyways, in px. Mirrored exactly in the PDF
 * (`DashPathEffect(floatArrayOf(HIDDEN_DASH_ON, HIDDEN_DASH_OFF), 0f)`) so preview and
 * export match. Kept module-level so both the renderer and the same-math preview agree.
 */
internal const val HIDDEN_DASH_ON = 6f
internal const val HIDDEN_DASH_OFF = 4f

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
     * @param opts    Visual knobs (line widths, colors, highlight, threads)
     * @param textMeasurer reserved for future labels (not used here)
     */
    fun DrawScope.draw(
        spec: ShaftSpec,
        layout: ShaftLayout.Result,
        opts: RenderOptions,
        textMeasurer: TextMeasurer,
        components: List<ResolvedComponent>? = null,
    ) {
        val L = from(layout)
        val cy = L.centerlineYPx

        // Resolve palette from opts (legacy ARGB Int → Color)
        val outline      = Color(opts.outlineColor)
        val outlineW     = opts.outlineWidthPx
        val dimW         = opts.dimLineWidthPx
        val bodyFill     = Color(opts.bodyFillColor)
        val taperFill    = Color(opts.taperFillColor)
        val linerFill    = Color(opts.linerFillColor)
        val threadFill   = Color(opts.threadFillColor)
        val flankColor   = Color(opts.threadHatchColor)
        val useUnified   = (opts.threadStyle == ThreadStyle.UNIFIED)

        // ───────── Highlight (resolved; no-ops if disabled) ─────────
        val hiEnabled  = opts.highlightEnabled
        val hiId       = opts.highlightId
        val hiGlowCol  = opts.highlightGlowColor
        val hiEdgeCol  = opts.highlightEdgeColor
        val hiGlowA    = opts.highlightGlowAlpha
        val hiEdgeA    = opts.highlightEdgeAlpha
        val hiGlowDx   = opts.highlightGlowExtraPx
        val hiEdgeDx   = opts.highlightEdgeExtraPx

        val resolvedBodies = components
            ?.filterIsInstance<ResolvedBody>()
            ?.filter { it.type == ResolvedComponentType.BODY || it.type == ResolvedComponentType.BODY_AUTO }

        // ───────── Bodies ─────────
        if (resolvedBodies != null) {
            for (b in resolvedBodies) {
                val x0 = L.xPx(b.startMmPhysical)
                val x1 = L.xPx(b.endMmPhysical)
                val r = L.rPx(b.diaMm)
                val top = cy - r
                val size = Size(x1 - x0, r * 2f)
                val topLeft = Offset(x0, top)

                // Fill
                drawRect(color = bodyFill, topLeft = topLeft, size = size)

                // Highlight under-stroke
                if (isHighlighted(hiEnabled, hiId, b.id)) {
                    drawHighlightStrokeRect(
                        topLeft = topLeft,
                        size = size,
                        baseStrokePx = outlineW,
                        glowColor = hiGlowCol, glowDx = hiGlowDx, glowAlpha = hiGlowA,
                        edgeColor = hiEdgeCol, edgeDx = hiEdgeDx, edgeAlpha = hiEdgeA
                    )
                }

                // Outline on top
                drawRect(color = outline, topLeft = topLeft, size = size, style = Stroke(width = outlineW))
            }
        } else {
            for (b in spec.bodies) {
                val x0 = L.xPx(b.startFromAftMm)
                val x1 = L.xPx(b.startFromAftMm + b.lengthMm)
                val r = L.rPx(b.diaMm)
            val top = cy - r
            val size = Size(x1 - x0, r * 2f)
            val topLeft = Offset(x0, top)

            // Fill
            drawRect(color = bodyFill, topLeft = topLeft, size = size)

            // Highlight under-stroke
            if (isHighlighted(hiEnabled, hiId, b.id)) {
                drawHighlightStrokeRect(
                    topLeft = topLeft,
                    size = size,
                    baseStrokePx = outlineW,
                    glowColor = hiGlowCol, glowDx = hiGlowDx, glowAlpha = hiGlowA,
                    edgeColor = hiEdgeCol, edgeDx = hiEdgeDx, edgeAlpha = hiEdgeA
                )
            }

            // Outline on top
                drawRect(color = outline, topLeft = topLeft, size = size, style = Stroke(width = outlineW))
            }
        }

        // Keyways 180° apart: far-side keyways render hidden (dashed, no fill). The
        // aft-most keyway (measurement datum) stays solid. Empty unless the flag is set.
        val hiddenKeywayIds = spec.hiddenKeywayHostIds()

        // ───────── Body keyways ─────────
        // Keyways live on explicit model bodies; draw from model geometry so resolved
        // fragment subtraction can't displace the slot.
        for (b in spec.bodies) {
            if (b.hasKeyway) drawKeywayNotchBody(b, L, outline, outlineW, hidden = b.id in hiddenKeywayIds)
        }

        val resolvedTapers = components?.filterIsInstance<ResolvedTaper>()

        // ───────── Tapers (trapezoid) ─────────
        if (resolvedTapers != null) {
            for (t in resolvedTapers) {
                val x0 = L.xPx(t.startMmPhysical)
                val x1 = L.xPx(t.endMmPhysical)
                val r0 = L.rPx(t.startDiaMm)
                val r1 = L.rPx(t.endDiaMm)
                val top0 = cy - r0; val bot0 = cy + r0
                val top1 = cy - r1; val bot1 = cy + r1

                val path = Path().apply {
                    moveTo(x0, top0); lineTo(x1, top1); lineTo(x1, bot1); lineTo(x0, bot0); close()
                }

                // Fill
                drawPath(path, color = taperFill)

                // Highlight under-stroke
                if (isHighlighted(hiEnabled, hiId, t.id)) {
                    drawHighlightStroke(
                        path = path,
                        baseStrokePx = outlineW,
                        glowColor = hiGlowCol, glowDx = hiGlowDx, glowAlpha = hiGlowA,
                        edgeColor = hiEdgeCol, edgeDx = hiEdgeDx, edgeAlpha = hiEdgeA
                    )
                }

                // Edges on top
                drawLine(outline, Offset(x0, top0), Offset(x1, top1), strokeWidth = outlineW)
                drawLine(outline, Offset(x0, bot0), Offset(x1, bot1), strokeWidth = outlineW)
                drawLine(outline, Offset(x0, top0), Offset(x0, bot0), strokeWidth = outlineW)
                drawLine(outline, Offset(x1, top1), Offset(x1, bot1), strokeWidth = outlineW)

                // Keyway — look up model taper by id for keyway data
                val modelTaper = spec.tapers.firstOrNull { it.id == t.id }
                if (modelTaper != null && modelTaper.hasKeyway) {
                    drawKeywayNotch(modelTaper, L, x0, x1, top0, top1, outline, outlineW, taperFill, hidden = t.id in hiddenKeywayIds)
                }
            }
        } else {
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

            // Fill
            drawPath(path, color = taperFill)

            // Highlight under-stroke
            if (isHighlighted(hiEnabled, hiId, t.id)) {
                drawHighlightStroke(
                    path = path,
                    baseStrokePx = outlineW,
                    glowColor = hiGlowCol, glowDx = hiGlowDx, glowAlpha = hiGlowA,
                    edgeColor = hiEdgeCol, edgeDx = hiEdgeDx, edgeAlpha = hiEdgeA
                )
            }

            // Edges on top
            drawLine(outline, Offset(x0, top0), Offset(x1, top1), strokeWidth = outlineW)
            drawLine(outline, Offset(x0, bot0), Offset(x1, bot1), strokeWidth = outlineW)
            drawLine(outline, Offset(x0, top0), Offset(x0, bot0), strokeWidth = outlineW)
                drawLine(outline, Offset(x1, top1), Offset(x1, bot1), strokeWidth = outlineW)

                if (t.hasKeyway) {
                    drawKeywayNotch(t, L, x0, x1, top0, top1, outline, outlineW, taperFill, hidden = t.id in hiddenKeywayIds)
                }
            }
        }

        val resolvedThreads = components?.filterIsInstance<ResolvedThread>()

        // ───────── Threads ─────────
        if (resolvedThreads != null) {
            for (th in resolvedThreads) {
                val startX   = L.xPx(th.startMmPhysical)
                val endX     = L.xPx(th.endMmPhysical)
                val left     = min(startX, endX)
                val right    = max(startX, endX)
                val lengthPx = right - left

                val majorR  = L.rPx(th.majorDiaMm)
                val minorR  = majorR * 0.85f // TODO: switch to model minor dia when available
                val pitchPx = ((th.pitchMm.takeIf { it > 0f } ?: (25.4f / 10f)) * L.pxPerMm) // fallback ≈10 TPI

                val top  = cy - majorR
                val size = Size(lengthPx, majorR * 2f)

                // Underlay to separate from grid
                if (threadFill.alpha > 0f) {
                    drawRect(color = threadFill, topLeft = Offset(left, top), size = size)
                }

                // Highlight under-stroke on the envelope
                if (isHighlighted(hiEnabled, hiId, th.id)) {
                    drawHighlightStrokeRect(
                        topLeft = Offset(left, top),
                        size = size,
                        baseStrokePx = outlineW,
                        glowColor = hiGlowCol, glowDx = hiGlowDx, glowAlpha = hiGlowA,
                        edgeColor = hiEdgeCol, edgeDx = hiEdgeDx, edgeAlpha = hiEdgeA
                    )
                }

                if (useUnified) {
                    val railStroke  = if (opts.threadStrokePx > 0f) opts.threadStrokePx else max(1f, outlineW)
                    val flankStroke = if (opts.threadStrokePx > 0f) opts.threadStrokePx else max(1f, dimW)
                    val flankCol    = if (opts.threadUseHatchColor) flankColor else outline

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

                // Envelope on top
                drawRect(color = outline, topLeft = Offset(left, top), size = size, style = Stroke(width = outlineW))
            }
        } else {
            for (th in spec.threads) {
                val startX   = L.xPx(th.startFromAftMm)
                val endX     = L.xPx(th.startFromAftMm + th.lengthMm)
            val left     = min(startX, endX)
            val right    = max(startX, endX)
            val lengthPx = right - left

            val majorR  = L.rPx(th.majorDiaMm)
            val minorR  = majorR * 0.85f // TODO: switch to model minor dia when available
            val pitchPx = ((th.pitchMm.takeIf { it > 0f } ?: (25.4f / 10f)) * L.pxPerMm) // fallback ≈10 TPI

            val top  = cy - majorR
            val size = Size(lengthPx, majorR * 2f)

            // Underlay to separate from grid
            if (threadFill.alpha > 0f) {
                drawRect(color = threadFill, topLeft = Offset(left, top), size = size)
            }

            // Highlight under-stroke on the envelope
            if (isHighlighted(hiEnabled, hiId, th.id)) {
                drawHighlightStrokeRect(
                    topLeft = Offset(left, top),
                    size = size,
                    baseStrokePx = outlineW,
                    glowColor = hiGlowCol, glowDx = hiGlowDx, glowAlpha = hiGlowA,
                    edgeColor = hiEdgeCol, edgeDx = hiEdgeDx, edgeAlpha = hiEdgeA
                )
            }

            if (useUnified) {
                val railStroke  = if (opts.threadStrokePx > 0f) opts.threadStrokePx else max(1f, outlineW)
                val flankStroke = if (opts.threadStrokePx > 0f) opts.threadStrokePx else max(1f, dimW)
                val flankCol    = if (opts.threadUseHatchColor) flankColor else outline

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

            // Envelope on top
                drawRect(color = outline, topLeft = Offset(left, top), size = size, style = Stroke(width = outlineW))
            }
        }

        val resolvedLiners = components?.filterIsInstance<ResolvedLiner>()

        // ───────── Liners ─────────
        if (resolvedLiners != null) {
            for (ln in resolvedLiners) {
                val x0 = L.xPx(ln.startMmPhysical)
                val x1 = L.xPx(ln.endMmPhysical)
                val r = L.rPx(ln.odMm)
                val top = cy - r
                val size = Size(x1 - x0, r * 2f)
                val topLeft = Offset(x0, top)

                // Fill
                drawRect(color = linerFill, topLeft = topLeft, size = size)

                // Highlight under-stroke
                if (isHighlighted(hiEnabled, hiId, ln.id)) {
                    drawHighlightStrokeRect(
                        topLeft = topLeft,
                        size = size,
                        baseStrokePx = outlineW,
                        glowColor = hiGlowCol, glowDx = hiGlowDx, glowAlpha = hiGlowA,
                        edgeColor = hiEdgeCol, edgeDx = hiEdgeDx, edgeAlpha = hiEdgeA
                    )
                }

                // Outline on top
                drawRect(color = outline, topLeft = topLeft, size = size, style = Stroke(width = outlineW))
            }
        } else {
            for (ln in spec.liners) {
                val x0 = L.xPx(ln.startFromAftMm)
                val x1 = L.xPx(ln.startFromAftMm + ln.lengthMm)
                val r = L.rPx(ln.odMm)
            val top = cy - r
            val size = Size(x1 - x0, r * 2f)
            val topLeft = Offset(x0, top)

            // Fill
            drawRect(color = linerFill, topLeft = topLeft, size = size)

            // Highlight under-stroke
            if (isHighlighted(hiEnabled, hiId, ln.id)) {
                drawHighlightStrokeRect(
                    topLeft = topLeft,
                    size = size,
                    baseStrokePx = outlineW,
                    glowColor = hiGlowCol, glowDx = hiGlowDx, glowAlpha = hiGlowA,
                    edgeColor = hiEdgeCol, edgeDx = hiEdgeDx, edgeAlpha = hiEdgeA
                )
            }

            // Outline
                drawRect(color = outline, topLeft = topLeft, size = size, style = Stroke(width = outlineW))
            }
        }

        // ───────── Coupler bolt slots (overlay; drawn on top of everything) ─────────
        if (spec.couplerBoltSlots.isNotEmpty()) {
            val slotFill = Color(opts.slotFillColor)

            // Outer radius (px) of the shaft surface at an axial position, so each cutout
            // can straddle the outline (half in the shaft, half in the coupling).
            fun surfaceRadiusPx(xMm: Float): Float {
                var maxDia = 0f
                if (components != null) {
                    for (c in components) {
                        if (c is ResolvedCouplerBoltSlot) continue
                        if (xMm < c.startMmPhysical - 1e-3f || xMm > c.endMmPhysical + 1e-3f) continue
                        val dia = if (c is ResolvedTaper) {
                            val span = c.endMmPhysical - c.startMmPhysical
                            val t = if (span > 1e-3f) ((xMm - c.startMmPhysical) / span).coerceIn(0f, 1f) else 0f
                            c.startDiaMm + (c.endDiaMm - c.startDiaMm) * t
                        } else {
                            c.maxDiaMm()
                        }
                        if (dia > maxDia) maxDia = dia
                    }
                }
                if (maxDia <= 0f) maxDia = spec.maxOuterDiaMm() // fallback: largest OD on the shaft
                return L.rPx(maxDia)
            }

            for (slot in spec.couplerBoltSlots) {
                val holeR = L.rPx(slot.holeDiaMm)
                if (holeR <= 0f || slot.count < 1) continue
                val highlighted = isHighlighted(hiEnabled, hiId, slot.id)
                for (i in 0 until slot.count) {
                    val cxMm = slot.startFromAftMm + i * slot.spacingMm
                    val cx = L.xPx(cxMm)
                    val rSurface = surfaceRadiusPx(cxMm)
                    // One cutout on the top surface, mirrored on the bottom surface.
                    for (surfY in floatArrayOf(cy - rSurface, cy + rSurface)) {
                        val center = Offset(cx, surfY)
                        drawCircle(color = slotFill, radius = holeR, center = center)
                        if (highlighted) {
                            drawCircle(
                                color = hiGlowCol.copy(alpha = hiGlowA),
                                radius = holeR + hiGlowDx,
                                center = center,
                                style = Stroke(width = outlineW)
                            )
                        }
                        drawCircle(color = outline, radius = holeR, center = center, style = Stroke(width = outlineW))
                    }
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Helpers (threads)
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

// ─────────────────────────────────────────────────────────────────────────────
// H I G H L I G H T  –  helpers
// ─────────────────────────────────────────────────────────────────────────────

/** True when highlight is enabled and the given component id matches the selected id. */
private fun isHighlighted(enabled: Boolean, selectedId: Any?, candidateId: Any?): Boolean =
    enabled && selectedId != null && candidateId != null && selectedId == candidateId

/**
 * Paint a two-ring under-stroke (glow + crisp edge). Call this *before* the normal stroke.
 * The base stroke width remains your existing outline weight.
 */
private fun DrawScope.drawHighlightStroke(
    path: Path,
    baseStrokePx: Float,
    glowColor: Color,
    glowDx: Float,
    glowAlpha: Float,
    edgeColor: Color,
    edgeDx: Float,
    edgeAlpha: Float,
) {
    // Single selection ring — outer glow only. The inner white edge ring was removed
    // because it created a distracting double-box appearance.
    drawPath(
        path = path,
        color = glowColor.copy(alpha = glowAlpha),
        style = Stroke(width = baseStrokePx + glowDx, cap = StrokeCap.Butt, join = StrokeJoin.Round)
    )
}

private fun DrawScope.drawHighlightStrokeRect(
    topLeft: Offset,
    size: Size,
    baseStrokePx: Float,
    glowColor: Color,
    glowDx: Float,
    glowAlpha: Float,
    edgeColor: Color,
    edgeDx: Float,
    edgeAlpha: Float,
) {
    // Single selection ring — outer glow only.
    drawRect(
        color = glowColor.copy(alpha = glowAlpha),
        topLeft = topLeft,
        size = size,
        style = Stroke(width = baseStrokePx + glowDx)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Keyway notch drawing
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Draw a keyway symbol centered on the shaft centerline, matching shop schematic convention.
 *
 * The keyway is shown as a plan-view (top-down) rectangle centered at [cy]:
 *   - Height = keywayWidthMm × pxPerMm  → WIDTH to scale
 *   - Horizontal span = keywayLengthMm  → LENGTH to scale
 *   - Depth is NOT drawn; it appears only as text in the PDF footer
 *
 * The LET (closed) end uses a concave semicircle — the mill-cutter profile.
 * The arc center is halfW inward from the LET face; straight lines stop there.
 *
 * Open keyway  (offset ≈ 0): SET face is the shaft end face, already drawn — no extra wall.
 * Floating keyway (offset > 0): both ends get a concave semicircle.
 */
private fun DrawScope.drawKeywayNotch(
    t: Taper,
    L: ShaftRenderer.Layout,
    x0: Float, x1: Float,
    @Suppress("UNUSED_PARAMETER") top0: Float,
    @Suppress("UNUSED_PARAMETER") top1: Float,
    outline: Color,
    outlineW: Float,
    @Suppress("UNUSED_PARAMETER") taperFill: Color,
    hidden: Boolean = false,
) {
    if (x1 == x0 || t.keywayWidthMm <= 0f) return

    val setAtStart = t.startDiaMm <= t.endDiaMm
    val setX = if (setAtStart) x0 else x1
    val letX = if (setAtStart) x1 else x0
    val dir  = if (letX > setX) 1f else -1f   // +1 = LET is to the right of SET

    drawKeywaySlot(
        refX = setX, dir = dir,
        widthMm = t.keywayWidthMm,
        offsetMm = t.keywayOffsetFromSetMm,
        lengthMm = t.keywayLengthMm,
        L = L, outline = outline, outlineW = outlineW, hidden = hidden,
        spooned = t.keywaySpooned,
    )
}

/**
 * Draw a body-hosted keyway (intermediate shafts with fitted couplings). Same plan-view
 * slot as the taper keyway, referenced from the body's AFT or FWD end face instead of
 * the SET face.
 */
private fun DrawScope.drawKeywayNotchBody(
    b: Body,
    L: ShaftRenderer.Layout,
    outline: Color,
    outlineW: Float,
    hidden: Boolean = false,
) {
    val x0 = L.xPx(b.startFromAftMm)
    val x1 = L.xPx(b.startFromAftMm + b.lengthMm)
    if (x1 == x0 || b.keywayWidthMm <= 0f) return

    val aftRef = b.keywayEnd == LinerAuthoredReference.AFT
    val refX = if (aftRef) x0 else x1
    val farX = if (aftRef) x1 else x0
    val dir  = if (farX > refX) 1f else -1f

    drawKeywaySlot(
        refX = refX, dir = dir,
        widthMm = b.keywayWidthMm,
        offsetMm = b.keywayOffsetFromEndMm,
        lengthMm = b.keywayLengthMm,
        L = L, outline = outline, outlineW = outlineW, hidden = hidden,
        spooned = b.keywaySpooned,
    )
}

/**
 * Shared keyway slot geometry. [refX] is the referenced face (SET face for tapers, the
 * AFT/FWD end face for bodies); [dir] is +1 when the slot extends rightward from it.
 * offset ≈ 0 = open at the referenced face; > 0 = floating (mill arcs both ends).
 *
 * [hidden] draws the slot as a far-side feature (keyways 180° apart): dashed outline and
 * **no** white void fill — the near shaft surface is unbroken, so nothing is carved away
 * in this view. Geometry is otherwise identical to the near-side (solid) slot.
 */
private fun DrawScope.drawKeywaySlot(
    refX: Float,
    dir: Float,
    widthMm: Float,
    offsetMm: Float,
    lengthMm: Float,
    L: ShaftRenderer.Layout,
    outline: Color,
    outlineW: Float,
    hidden: Boolean = false,
    spooned: Boolean = false,
) {
    val cy   = L.centerlineYPx

    val halfW    = (widthMm * L.pxPerMm) / 2f
    val offsetPx = offsetMm * L.pxPerMm
    val kwLenPx  = lengthMm * L.pxPerMm
    val kwSetX   = refX + dir * offsetPx
    val kwLetX   = kwSetX + dir * kwLenPx
    val isOpen   = offsetMm < 0.01f

    // Spooned (open keyways only): keep the normal keyway (full-length walls + mill semicircle) and
    // ADD an enlarged circle around the closed (LET) end — the mill end stays as an inner reference
    // line inside the bowl. Floating keyways ignore the flag.
    val bowl = if (spooned && isOpen && halfW > 0f) keywaySpoonBowl(kwLetX, dir, halfW) else null

    // Arc center is halfW inward from the LET face (concave mill-cut profile).
    val letArcCx    = kwLetX - dir * halfW
    val letArcStart = if (dir > 0) 270f else 90f
    val letArcBox   = androidx.compose.ui.geometry.Size(halfW * 2f, halfW * 2f)

    val setArcCx    = kwSetX + dir * halfW
    val setArcStart = if (dir > 0) 90f else 270f

    // Straight lines run from the SET side to the mill arc centre (unchanged by the spoon).
    val lineNear  = if (isOpen) kwSetX else setArcCx
    val lineFar   = letArcCx
    val lineLeft  = min(lineNear, lineFar)
    val lineRight = max(lineNear, lineFar)

    // ── White fill (keyway is a void — always white regardless of taper colour) ──
    // Far-side (hidden) keyways are not cut into the near surface, so they get no fill.
    // For open keyways, inset the fill from the SET face by one line-width so the
    // taper's end-face line retains its full thickness under the fill.
    if (!hidden) {
        val fillNear  = if (isOpen) kwSetX + dir * outlineW else setArcCx
        val fillLeft  = min(fillNear, letArcCx)
        val fillRight = max(fillNear, letArcCx)
        drawRect(
            color = Color.White,
            topLeft = Offset(fillLeft, cy - halfW),
            size = androidx.compose.ui.geometry.Size(fillRight - fillLeft, halfW * 2f)
        )
        drawArc(
            color = Color.White,
            startAngle = letArcStart, sweepAngle = 180f, useCenter = false,
            topLeft = Offset(letArcCx - halfW, cy - halfW), size = letArcBox
        )
        if (!isOpen) {
            drawArc(
                color = Color.White,
                startAngle = setArcStart, sweepAngle = 180f, useCenter = false,
                topLeft = Offset(setArcCx - halfW, cy - halfW), size = letArcBox
            )
        }
        // Spoon bowl void: the enlarged disc around the LET end (its SET-side overlaps the slot).
        if (bowl != null) {
            drawCircle(color = Color.White, radius = bowl.radius, center = Offset(bowl.cx, cy))
        }
    }

    // ── Outline strokes on top (dashed for hidden far-side keyways) ──
    val dash = if (hidden) PathEffect.dashPathEffect(floatArrayOf(HIDDEN_DASH_ON, HIDDEN_DASH_OFF), 0f) else null
    drawLine(outline, Offset(lineLeft, cy - halfW), Offset(lineRight, cy - halfW), strokeWidth = outlineW, pathEffect = dash)
    drawLine(outline, Offset(lineLeft, cy + halfW), Offset(lineRight, cy + halfW), strokeWidth = outlineW, pathEffect = dash)

    // Inner mill semicircle — the standard rounded end (kept as a reference line inside the bowl).
    drawArc(
        color = outline,
        startAngle = letArcStart, sweepAngle = 180f, useCenter = false,
        topLeft = Offset(letArcCx - halfW, cy - halfW), size = letArcBox,
        style = Stroke(width = outlineW, pathEffect = dash)
    )
    if (!isOpen) {
        drawArc(
            color = outline,
            startAngle = setArcStart, sweepAngle = 180f, useCenter = false,
            topLeft = Offset(setArcCx - halfW, cy - halfW), size = letArcBox,
            style = Stroke(width = outlineW, pathEffect = dash)
        )
    }
    // Spoon bowl: the enlarged circle's major arc around the far side (the walls run through
    // its SET-facing mouth to the inner mill end).
    if (bowl != null) {
        drawArc(
            color = outline,
            startAngle = bowl.arcStartDeg, sweepAngle = bowl.arcSweepDeg, useCenter = false,
            topLeft = Offset(bowl.cx - bowl.radius, cy - bowl.radius),
            size = androidx.compose.ui.geometry.Size(bowl.radius * 2f, bowl.radius * 2f),
            style = Stroke(width = outlineW, pathEffect = dash)
        )
    }
    // Open keyway: no SET-end wall — the shaft face end-line already closes the slot
    // (the same solid body/taper outline closes a hidden slot's open end too).
}
