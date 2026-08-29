package com.android.shaftschematic.ui.drawing.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import com.android.shaftschematic.geom.KeywaySilhouetteNotch
import com.android.shaftschematic.geom.KeywaySilhouettePoint
import com.android.shaftschematic.geom.floorMm
import com.android.shaftschematic.geom.fillPolygonMm
import com.android.shaftschematic.geom.keywaySilhouetteNotch
import com.android.shaftschematic.geom.keywaySpoonBowl
import com.android.shaftschematic.geom.wallEndMm
import com.android.shaftschematic.geom.wallStartMm
import com.android.shaftschematic.geom.yFor
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.KeywayClocking
import com.android.shaftschematic.model.LinerAuthoredReference
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.hasKeyway
import com.android.shaftschematic.model.hiddenKeywayHostIds
import com.android.shaftschematic.model.keywayClocking
import com.android.shaftschematic.model.maxOuterDiaMm
import com.android.shaftschematic.model.secondaryKeywayHostIds
import com.android.shaftschematic.ui.resolved.ResolvedBody
import com.android.shaftschematic.ui.resolved.ResolvedComponent
import com.android.shaftschematic.ui.resolved.ResolvedComponentType
import com.android.shaftschematic.ui.resolved.ResolvedCouplerBoltSlot
import com.android.shaftschematic.ui.resolved.maxDiaMm
import com.android.shaftschematic.ui.resolved.ResolvedLiner
import com.android.shaftschematic.geom.MIN_BLEND_WIDTH_PX
import com.android.shaftschematic.geom.MIN_KEYWAY_WIDTH_PX
import com.android.shaftschematic.geom.drawnKeywayHalfWidthPx
import com.android.shaftschematic.geom.minKeywaySlotLenPx
import com.android.shaftschematic.geom.SEAL_DASH_OFF_PT
import com.android.shaftschematic.geom.SEAL_DASH_ON_PT
import com.android.shaftschematic.geom.ShoulderDrawSpec
import com.android.shaftschematic.geom.linerTopSilhouette
import com.android.shaftschematic.geom.shoulderDrawSpec
import com.android.shaftschematic.model.shoulderOn
import com.android.shaftschematic.ui.resolved.BodyDrawEdges
import com.android.shaftschematic.ui.resolved.BodyEdgePoint
import com.android.shaftschematic.ui.resolved.ResolvedTaper
import com.android.shaftschematic.ui.resolved.bodyBlends
import com.android.shaftschematic.ui.resolved.bodyDrawEdges
import com.android.shaftschematic.ui.resolved.ResolvedThread
import kotlin.math.abs
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
 * • Highlight outline: when enabled & an ID matches, we paint a glow under-stroke,
 *   then the normal stroke on top. When highlight is off, visuals are unaffected.
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
     */
    fun DrawScope.draw(
        spec: ShaftSpec,
        layout: ShaftLayout.Result,
        opts: RenderOptions,
        components: List<ResolvedComponent>? = null,
    ) {
        val L = from(layout)
        val cy = L.centerlineYPx

        // Z-order inside every component block below: fill first, then the highlight
        // under-stroke, then the outline/edges last — a highlight glow drawn after the
        // outline would swallow it, and a fill drawn late would cover its own edges.

        // Resolve palette from opts (legacy ARGB Int → Color)
        val outline      = Color(opts.outlineColor)
        val outlineW     = opts.outlineWidthPx
        val dimW         = opts.dimLineWidthPx
        val bodyFill     = Color(opts.bodyFillColor)
        val taperFill    = Color(opts.taperFillColor)
        val linerFill    = Color(opts.linerFillColor)
        val threadFill   = Color(opts.threadFillColor)
        val flankColor   = Color(opts.threadHatchColor)
        // PDF-shade mirror: components the PDF will print shaded get this overlay on top of
        // their normal preview fill, so the box answers "what prints shaded" live.
        val shadedIds    = opts.shadedComponentIds
        val shadeOverlay = Color(opts.shadeOverlayColor)

        // ───────── Highlight (resolved; no-ops if disabled) ─────────
        val hiEnabled  = opts.highlightEnabled
        val hiId       = opts.highlightId
        val hiGlowCol  = opts.highlightGlowColor
        val hiGlowA    = opts.highlightGlowAlpha
        val hiGlowDx   = opts.highlightGlowExtraPx

        val resolvedBodies = components
            ?.filterIsInstance<ResolvedBody>()
            ?.filter { it.type == ResolvedComponentType.BODY || it.type == ResolvedComponentType.BODY_AUTO }

        // ───────── Bodies ─────────
        // Blended faces need the resolved neighbours to know what diameter each face steps
        // to, so they ride the resolved branch only; without one, faces stay square.
        val blends = if (components != null) bodyBlends(spec, components) else emptyList()
        if (resolvedBodies != null) {
            for (b in resolvedBodies) {
                val edges = bodyDrawEdges(
                    runId = b.id,
                    runStartMm = b.startMmPhysical,
                    runEndMm = b.endMmPhysical,
                    runDiaMm = b.diaMm,
                    blends = blends,
                    xAt = { mm -> L.xPx(mm) },
                    rAt = { dia -> L.rPx(dia) },
                    minWidthPx = MIN_BLEND_WIDTH_PX,
                )
                val path = bodySilhouettePath(
                    edges, cy, L.xPx(b.startMmPhysical), L.xPx(b.endMmPhysical)
                )

                drawPath(path, color = bodyFill)
                if (b.id in shadedIds) drawPath(path, color = shadeOverlay)

                // Highlight under-stroke
                if (isHighlighted(hiEnabled, hiId, b.id)) {
                    drawHighlightStroke(
                        path = path,
                        baseStrokePx = outlineW,
                        glowColor = hiGlowCol, glowDx = hiGlowDx, glowAlpha = hiGlowA
                    )
                }

                drawPath(path, color = outline, style = Stroke(width = outlineW))

                // Seal area: the radius cuts the fiberglass seats into, drawn across the
                // blend. Dashed so the shaft still reads as one unit — a solid vertical is
                // the component-face glyph (on-device report: 3 solid lines looked like 3-4
                // segments). Finer than the hidden-keyway dash on purpose.
                val sealDash = PathEffect.dashPathEffect(floatArrayOf(SEAL_DASH_ON_PT, SEAL_DASH_OFF_PT), 0f)
                (edges.aftSeal + edges.fwdSeal).forEach { g ->
                    drawLine(
                        color = outline,
                        start = Offset(g.xPx, cy - g.rPx),
                        end = Offset(g.xPx, cy + g.rPx),
                        strokeWidth = outlineW,
                        pathEffect = sealDash,
                    )
                }
            }
        } else {
            for (b in spec.bodies) {
                val x0 = L.xPx(b.startFromAftMm)
                val x1 = L.xPx(b.startFromAftMm + b.lengthMm)
                val r = L.rPx(b.diaMm)
            val top = cy - r
            val size = Size(x1 - x0, r * 2f)
            val topLeft = Offset(x0, top)

            drawRect(color = bodyFill, topLeft = topLeft, size = size)
            if (b.id in shadedIds) drawRect(color = shadeOverlay, topLeft = topLeft, size = size)

            // Highlight under-stroke
            if (isHighlighted(hiEnabled, hiId, b.id)) {
                drawHighlightStrokeRect(
                    topLeft = topLeft,
                    size = size,
                    baseStrokePx = outlineW,
                    glowColor = hiGlowCol, glowDx = hiGlowDx, glowAlpha = hiGlowA
                )
            }

                drawRect(color = outline, topLeft = topLeft, size = size, style = Stroke(width = outlineW))
            }
        }

        // Keyway clocking: the aft-most keyway (measurement datum) always draws face-on; every
        // other host is a secondary. At 180° a secondary renders hidden (dashed, no fill); at
        // 90° it renders as a notch cut into a silhouette edge. Both sets are empty unless a
        // clocking note is set with ≥ 2 keyways.
        val clocking = spec.keywayClocking()
        val hiddenKeywayIds = spec.hiddenKeywayHostIds()
        val secondaryKeywayIds = spec.secondaryKeywayHostIds()
        val silhouetteKeyways = clocking == KeywayClocking.DEG_90_CW || clocking == KeywayClocking.DEG_90_CCW

        // ───────── Body keyways ─────────
        // Keyways live on explicit model bodies; draw from model geometry so resolved
        // fragment subtraction can't displace the slot.
        for (b in spec.bodies) {
            if (!b.hasKeyway) continue
            if (silhouetteKeyways && b.id in secondaryKeywayIds) {
                b.keywaySilhouetteNotch(clocking)?.let { drawKeywaySilhouetteNotch(it, L, outline, outlineW) }
            } else {
                drawKeywayNotchBody(b, L, outline, outlineW, hidden = b.id in hiddenKeywayIds)
            }
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

                drawPath(path, color = taperFill)
                if (t.id in shadedIds) drawPath(path, color = shadeOverlay)

                // Highlight under-stroke
                if (isHighlighted(hiEnabled, hiId, t.id)) {
                    drawHighlightStroke(
                        path = path,
                        baseStrokePx = outlineW,
                        glowColor = hiGlowCol, glowDx = hiGlowDx, glowAlpha = hiGlowA
                    )
                }

                drawLine(outline, Offset(x0, top0), Offset(x1, top1), strokeWidth = outlineW)
                drawLine(outline, Offset(x0, bot0), Offset(x1, bot1), strokeWidth = outlineW)
                drawLine(outline, Offset(x0, top0), Offset(x0, bot0), strokeWidth = outlineW)
                drawLine(outline, Offset(x1, top1), Offset(x1, bot1), strokeWidth = outlineW)

                // Keyway — look up model taper by id for keyway data
                val modelTaper = spec.tapers.firstOrNull { it.id == t.id }
                if (modelTaper != null && modelTaper.hasKeyway) {
                    if (silhouetteKeyways && t.id in secondaryKeywayIds) {
                        modelTaper.keywaySilhouetteNotch(clocking)
                            ?.let { drawKeywaySilhouetteNotch(it, L, outline, outlineW) }
                    } else {
                        drawKeywayNotch(modelTaper, L, x0, x1, top0, top1, outline, outlineW, taperFill, hidden = t.id in hiddenKeywayIds)
                    }
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

            drawPath(path, color = taperFill)
            if (t.id in shadedIds) drawPath(path, color = shadeOverlay)

            // Highlight under-stroke
            if (isHighlighted(hiEnabled, hiId, t.id)) {
                drawHighlightStroke(
                    path = path,
                    baseStrokePx = outlineW,
                    glowColor = hiGlowCol, glowDx = hiGlowDx, glowAlpha = hiGlowA
                )
            }

            drawLine(outline, Offset(x0, top0), Offset(x1, top1), strokeWidth = outlineW)
            drawLine(outline, Offset(x0, bot0), Offset(x1, bot1), strokeWidth = outlineW)
            drawLine(outline, Offset(x0, top0), Offset(x0, bot0), strokeWidth = outlineW)
                drawLine(outline, Offset(x1, top1), Offset(x1, bot1), strokeWidth = outlineW)

                if (t.hasKeyway) {
                    if (silhouetteKeyways && t.id in secondaryKeywayIds) {
                        t.keywaySilhouetteNotch(clocking)?.let { drawKeywaySilhouetteNotch(it, L, outline, outlineW) }
                    } else {
                        drawKeywayNotch(t, L, x0, x1, top0, top1, outline, outlineW, taperFill, hidden = t.id in hiddenKeywayIds)
                    }
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
                        glowColor = hiGlowCol, glowDx = hiGlowDx, glowAlpha = hiGlowA
                    )
                }

                drawThreadHatch(
                    leftPx = left,
                    topPx = top,
                    rightPx = right,
                    bottomPx = cy + majorR,
                    pxPerMm = L.pxPerMm,
                    pitchMm = th.pitchMm,
                    color = flankColor
                )

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
                    glowColor = hiGlowCol, glowDx = hiGlowDx, glowAlpha = hiGlowA
                )
            }

            drawThreadHatch(
                leftPx = left,
                topPx = top,
                rightPx = right,
                bottomPx = cy + majorR,
                pxPerMm = L.pxPerMm,
                pitchMm = th.pitchMm,
                color = flankColor
            )

            // Envelope on top
                drawRect(color = outline, topLeft = Offset(left, top), size = size, style = Stroke(width = outlineW))
            }
        }

        val resolvedLiners = components?.filterIsInstance<ResolvedLiner>()

        // ───────── Liners ─────────
        // One drawer for both branches. A liner with no shoulders keeps the plain rect
        // (byte-identical output); a shouldered one decomposes the same `linerTopSilhouette`
        // the PDF composer strokes, mirrored about the centerline, so the two sites can
        // never place a different step. Stored shoulder values come off the STORED liner —
        // liners never fragment, so the resolved id is the stored id.
        fun drawOneLiner(storedId: String, x0: Float, x1: Float, odMm: Float) {
            val r = L.rPx(odMm)
            val top = cy - r
            val size = Size(x1 - x0, r * 2f)
            val topLeft = Offset(x0, top)
            val stored = spec.liners.firstOrNull { it.id == storedId }

            fun spec(end: LinerAuthoredReference): ShoulderDrawSpec? {
                val s = stored?.shoulderOn(end) ?: return null
                val trueLenPx = when (end) {
                    LinerAuthoredReference.AFT -> abs(L.xPx(stored.startFromAftMm + s.lenMm) - x0)
                    LinerAuthoredReference.FWD ->
                        abs(x1 - L.xPx(stored.startFromAftMm + stored.lengthMm - s.lenMm))
                }
                return shoulderDrawSpec(
                    trueLenPx = trueLenPx,
                    runWidthPx = abs(x1 - x0),
                    linerRPx = r,
                    shoulderRPx = L.rPx(s.odMm),
                    // The stored value is a RADIUS; L.rPx maps a diameter.
                    filletRPx = L.rPx(s.radiusMm * 2f),
                    minWidthPx = MIN_BLEND_WIDTH_PX,
                )
            }

            val aftSpec = spec(LinerAuthoredReference.AFT)
            val fwdSpec = spec(LinerAuthoredReference.FWD)
            if (aftSpec == null && fwdSpec == null) {
                drawRect(color = linerFill, topLeft = topLeft, size = size)
                if (storedId in shadedIds) drawRect(color = shadeOverlay, topLeft = topLeft, size = size)
                if (isHighlighted(hiEnabled, hiId, storedId)) {
                    drawHighlightStrokeRect(
                        topLeft = topLeft,
                        size = size,
                        baseStrokePx = outlineW,
                        glowColor = hiGlowCol, glowDx = hiGlowDx, glowAlpha = hiGlowA
                    )
                }
                drawRect(color = outline, topLeft = topLeft, size = size, style = Stroke(width = outlineW))
                return
            }

            val pts = linerTopSilhouette(x0, x1, r, aftSpec, fwdSpec)
            val path = Path().apply {
                moveTo(pts.first().xPx, cy - pts.first().rPx)
                pts.drop(1).forEach { lineTo(it.xPx, cy - it.rPx) }
                pts.reversed().forEach { lineTo(it.xPx, cy + it.rPx) }
                close()
            }
            drawPath(path, color = linerFill)
            if (storedId in shadedIds) drawPath(path, color = shadeOverlay)
            if (isHighlighted(hiEnabled, hiId, storedId)) {
                drawHighlightStrokeRect(
                    topLeft = topLeft,
                    size = size,
                    baseStrokePx = outlineW,
                    glowColor = hiGlowCol, glowDx = hiGlowDx, glowAlpha = hiGlowA
                )
            }
            drawPath(path, color = outline, style = Stroke(width = outlineW))
        }

        if (resolvedLiners != null) {
            for (ln in resolvedLiners) {
                drawOneLiner(ln.id, L.xPx(ln.startMmPhysical), L.xPx(ln.endMmPhysical), ln.odMm)
            }
        } else {
            for (ln in spec.liners) {
                drawOneLiner(ln.id, L.xPx(ln.startFromAftMm), L.xPx(ln.startFromAftMm + ln.lengthMm), ln.odMm)
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
        // The app-wide hatch convention (PDF mirror: `pdf/SimpleShaftProfile.drawThreadHatch`
        // + its shared pitch recipe): full-band diagonals at the thread's own pitch, capped
        // 4–18 — the same thread must read the same on the preview as on every sheet.
        val spacing = ((pitchMm?.takeIf { it > 0f } ?: 2.5f) * pxPerMm).coerceIn(4f, 18f)
        val bandH = bottomPx - topPx
        val stroke = 1f
        withTransform({ clipRect(leftPx, topPx, rightPx, bottomPx) }) {
            var hx = leftPx - bandH
            while (hx <= rightPx) {
                drawLine(color, Offset(hx, bottomPx), Offset(hx + bandH, topPx), stroke)
                hx += spacing
            }
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
 * Paint the glow under-stroke. Call this *before* the normal stroke.
 * The base stroke width remains your existing outline weight.
 */
private fun DrawScope.drawHighlightStroke(
    path: Path,
    baseStrokePx: Float,
    glowColor: Color,
    glowDx: Float,
    glowAlpha: Float,
) {
    // Single selection ring — outer glow only. Do not add an inner white edge ring — it
    // creates a distracting double-box appearance.
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
 * The keyway is shown as a plan-view (top-down) rectangle centered on the centerline:
 *   - Height = keywayWidthMm at the DIAMETER scale → WIDTH to scale, proportional to the
 *     drawn shaft height (`geom/KeywaySlotMath.kt` — floored for visibility, capped against
 *     the host). This canvas has one uniform scale; the PDF's two differ.
 *   - Horizontal span = keywayLengthMm at the AXIAL scale → LENGTH to scale
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
    top0: Float,
    top1: Float,
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
        // The keyway sits at the SET (small) end, so the smaller drawn radius is the host
        // the slot must stay inside.
        hostRadiusPx = min(L.centerlineYPx - top0, L.centerlineYPx - top1),
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
        hostRadiusPx = L.rPx(b.diaMm),
        L = L, outline = outline, outlineW = outlineW, hidden = hidden,
        spooned = b.keywaySpooned,
    )
}

/**
 * Shared keyway slot geometry. [refX] is the referenced face (SET face for tapers, the
 * AFT/FWD end face for bodies); [dir] is +1 when the slot extends rightward from it.
 * offset ≈ 0 = open at the referenced face; > 0 = floating (mill arcs both ends).
 * [hostRadiusPx] is the drawn radius the slot is cut into — the cap on its drawn width.
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
    hostRadiusPx: Float,
    L: ShaftRenderer.Layout,
    outline: Color,
    outlineW: Float,
    hidden: Boolean = false,
    spooned: Boolean = false,
) {
    val cy   = L.centerlineYPx

    // Two half-widths, one per axis — the PDF slot's construction (`geom/KeywaySlotMath.kt`).
    // [halfW] is the AXIAL term driving every x; [halfH] is the TRANSVERSE term off the diameter
    // scale, floored for visibility and capped against the host. This canvas has ONE uniform
    // scale, so the two coincide here and every ellipse comes out round; the shared construction
    // is what keeps the sheet — where they genuinely differ — from drifting away from it.
    val halfW    = (widthMm * L.pxPerMm) / 2f
    val halfH    = drawnKeywayHalfWidthPx(
        trueHalfWidthPx = (widthMm * L.pxPerMm) / 2f,
        hostRadiusPx = hostRadiusPx,
        minWidthPx = MIN_KEYWAY_WIDTH_PX,
        strokeWidthPx = outlineW,
    )
    val offsetPx = offsetMm * L.pxPerMm
    val isOpen   = offsetMm < 0.01f
    val kwLenPx  = maxOf(lengthMm * L.pxPerMm, minKeywaySlotLenPx(halfW, isOpen))
    val kwSetX   = refX + dir * offsetPx
    val kwLetX   = kwSetX + dir * kwLenPx

    // Spooned (open keyways only): keep the normal keyway (full-length walls + mill semicircle) and
    // ADD an enlarged bowl around the closed (LET) end — the mill end stays as an inner reference
    // line inside the bowl. The bowl's y-semi comes from the shared math (uniform drawn
    // clearance), matching the PDF site. Floating keyways ignore the flag.
    val bowl = if (spooned && isOpen && halfW > 0f) keywaySpoonBowl(kwLetX, dir, halfW, halfH) else null
    val bowlRy = bowl?.ry ?: 0f

    // Arc center is halfW inward from the LET face (concave mill-cut profile).
    val letArcCx    = kwLetX - dir * halfW
    val letArcStart = if (dir > 0) 270f else 90f
    val letArcBox   = androidx.compose.ui.geometry.Size(halfW * 2f, halfH * 2f)

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
            topLeft = Offset(fillLeft, cy - halfH),
            size = androidx.compose.ui.geometry.Size(fillRight - fillLeft, halfH * 2f)
        )
        drawArc(
            color = Color.White,
            startAngle = letArcStart, sweepAngle = 180f, useCenter = false,
            topLeft = Offset(letArcCx - halfW, cy - halfH), size = letArcBox
        )
        if (!isOpen) {
            drawArc(
                color = Color.White,
                startAngle = setArcStart, sweepAngle = 180f, useCenter = false,
                topLeft = Offset(setArcCx - halfW, cy - halfH), size = letArcBox
            )
        }
        // Spoon bowl void: the enlarged disc around the LET end (its SET-side overlaps the slot).
        if (bowl != null) {
            drawOval(
                color = Color.White,
                topLeft = Offset(bowl.cx - bowl.radius, cy - bowlRy),
                size = androidx.compose.ui.geometry.Size(bowl.radius * 2f, bowlRy * 2f),
            )
        }
    }

    // ── Outline strokes on top (dashed for hidden far-side keyways) ──
    val dash = if (hidden) PathEffect.dashPathEffect(floatArrayOf(HIDDEN_DASH_ON, HIDDEN_DASH_OFF), 0f) else null
    drawLine(outline, Offset(lineLeft, cy - halfH), Offset(lineRight, cy - halfH), strokeWidth = outlineW, pathEffect = dash)
    drawLine(outline, Offset(lineLeft, cy + halfH), Offset(lineRight, cy + halfH), strokeWidth = outlineW, pathEffect = dash)

    // Inner mill semicircle — the standard rounded end (kept as a reference line inside the bowl).
    drawArc(
        color = outline,
        startAngle = letArcStart, sweepAngle = 180f, useCenter = false,
        topLeft = Offset(letArcCx - halfW, cy - halfH), size = letArcBox,
        style = Stroke(width = outlineW, pathEffect = dash)
    )
    if (!isOpen) {
        drawArc(
            color = outline,
            startAngle = setArcStart, sweepAngle = 180f, useCenter = false,
            topLeft = Offset(setArcCx - halfW, cy - halfH), size = letArcBox,
            style = Stroke(width = outlineW, pathEffect = dash)
        )
    }
    // Spoon bowl: the enlarged ellipse's major arc around the far side (the walls run through
    // its SET-facing mouth to the inner mill end).
    if (bowl != null) {
        drawArc(
            color = outline,
            startAngle = bowl.arcStartDeg, sweepAngle = bowl.arcSweepDeg, useCenter = false,
            topLeft = Offset(bowl.cx - bowl.radius, cy - bowlRy),
            size = androidx.compose.ui.geometry.Size(bowl.radius * 2f, bowlRy * 2f),
            style = Stroke(width = outlineW, pathEffect = dash)
        )
    }
    // Open keyway: no SET-end wall — the shaft face end-line already closes the slot
    // (the same solid body/taper outline closes a hidden slot's open end too).
}

/**
 * Draw a 90°-clocked secondary keyway as a notch cut into the host's silhouette edge — the
 * true edge-on projection, where the slot's depth is what shows in profile.
 *
 * Geometry (span clamp, floor radii, top/bottom side) comes entirely from
 * `geom/KeywaySilhouetteMath.kt`; this only maps mm → px. The PDF
 * mirror is `ShaftPdfComposer.drawKeywaySilhouetteNotchPdf` — the two must stay in lockstep.
 *
 * The void polygon is filled first so it erases the host outline segment crossing the notch
 * (keyways draw after outlines), then the two walls and the floor are stroked. The original
 * surface line is deliberately not stroked — the notch is open at the surface.
 */
private fun DrawScope.drawKeywaySilhouetteNotch(
    notch: KeywaySilhouetteNotch,
    L: ShaftRenderer.Layout,
    outline: Color,
    outlineW: Float,
) {
    val cy = L.centerlineYPx
    fun y(radiusMm: Float) = notch.side.yFor(cy, radiusMm, L.pxPerMm)

    val poly = notch.fillPolygonMm()
    val path = Path().apply {
        poly.forEachIndexed { i, p ->
            val px = L.xPx(p.xMm); val py = y(p.radiusMm)
            if (i == 0) moveTo(px, py) else lineTo(px, py)
        }
        close()
    }
    // The keyway is a void — always white, regardless of the host's fill colour.
    drawPath(path, color = Color.White)

    fun stroke(seg: Pair<KeywaySilhouettePoint, KeywaySilhouettePoint>) {
        val (a, b) = seg
        drawLine(outline, Offset(L.xPx(a.xMm), y(a.radiusMm)), Offset(L.xPx(b.xMm), y(b.radiusMm)), strokeWidth = outlineW)
    }
    stroke(notch.wallStartMm())
    stroke(notch.floorMm())
    stroke(notch.wallEndMm())
}

/**
 * A body run's closed silhouette: the top edge aft → fwd (any blended face replaced by its
 * curve), down the fwd cap, the mirrored bottom edge fwd → aft, and up the aft cap.
 *
 * With no blend the point list collapses to the run's four rectangle corners, so an unblended
 * body draws exactly as it always has. The PDF composer decomposes the same [BodyDrawEdges]
 * instead of building one path, because its flat span still has to host the S-break pair.
 */
private fun bodySilhouettePath(
    edges: BodyDrawEdges,
    cy: Float,
    x0: Float,
    x1: Float,
): Path {
    val topPts = buildList {
        if (edges.aftCurve.isNotEmpty()) addAll(edges.aftCurve) else add(BodyEdgePoint(x0, edges.capAftR))
        add(BodyEdgePoint(edges.flatX0, edges.flatR))
        add(BodyEdgePoint(edges.flatX1, edges.flatR))
        if (edges.fwdCurve.isNotEmpty()) addAll(edges.fwdCurve) else add(BodyEdgePoint(x1, edges.capFwdR))
    }
    return Path().apply {
        moveTo(topPts.first().xPx, cy - topPts.first().rPx)
        topPts.drop(1).forEach { lineTo(it.xPx, cy - it.rPx) }
        topPts.reversed().forEach { lineTo(it.xPx, cy + it.rPx) }
        close()
    }
}
