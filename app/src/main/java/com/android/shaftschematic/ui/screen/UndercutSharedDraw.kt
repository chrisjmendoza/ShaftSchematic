package com.android.shaftschematic.ui.screen

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.android.shaftschematic.geom.LinerOutlineEdge
import com.android.shaftschematic.geom.NOTCH_FACE_MIN_STEP_PX
import com.android.shaftschematic.geom.UNDERCUT_SECTION_FILL_ALPHA
import com.android.shaftschematic.geom.UndercutLinerOd
import com.android.shaftschematic.geom.UndercutLinerSpan
import com.android.shaftschematic.geom.UndercutNotch
import com.android.shaftschematic.geom.linerOutlineSegments
import com.android.shaftschematic.geom.undercutCutNumberPlacements
import com.android.shaftschematic.ui.theme.SheetInk
import com.android.shaftschematic.ui.resolved.ResolvedComponent
import com.android.shaftschematic.ui.resolved.ResolvedLiner
import com.android.shaftschematic.ui.resolved.linerSurfaceSegs

/**
 * The undercut helpers the detail overlay (`ui/screen/UndercutDetail.kt`) and the undercut
 * route (`ui/screen/UndercutRoute.kt`) both reach for but that cannot live in `geom/` — the
 * notch draw pass (Compose `DrawScope`) and the resolved→liner-span mapping (`ui/resolved`
 * types). Their pure counterparts are in `geom/UndercutOverlayMath.kt`.
 */

// ─────────────────────────────────────────────────────────────────────────────
// Liner references — shared by the overlay's cards and the route's undercut list
// ─────────────────────────────────────────────────────────────────────────────

/** Every resolved liner as an [UndercutLinerSpan], aft → fwd — the liner-reference pool. */
internal fun linerSpansOf(resolvedComponents: List<ResolvedComponent>): List<UndercutLinerSpan> =
    resolvedComponents
        .filterIsInstance<ResolvedLiner>()
        .sortedBy { it.startMmPhysical }
        .map { UndercutLinerSpan(it.id, it.startMmPhysical, it.endMmPhysical) }

/** Every resolved liner with its OD, aft → fwd — the liner-outline pass's input. */
internal fun linerOdsOf(resolvedComponents: List<ResolvedComponent>): List<UndercutLinerOd> =
    resolvedComponents
        .filterIsInstance<ResolvedLiner>()
        .sortedBy { it.startMmPhysical }
        .map {
            UndercutLinerOd(
                it.id, it.startMmPhysical, it.endMmPhysical, it.odMm,
                surface = linerSurfaceSegs(it.startMmPhysical, it.endMmPhysical, it.odMm, it.shoulderAft, it.shoulderFwd),
            )
        }

// ─────────────────────────────────────────────────────────────────────────────
// Notch pipeline — shared by the overview canvas and the detail overlay
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Draw notches as **steps in the silhouette**: [voidColor] fill from the local surface down to
 * the floor (mirrored about the centreline), erasing the profile strokes inside the cut — the
 * mouth stays OPEN at the surface, never closed by a lid — then the outline: a full-height
 * **section face** at each region end (top surface to bottom surface, like any machined
 * diameter step, only where that end's surface stands above the floor) and the floor lines
 * across the span. Each cut reads as its own reduced-Ø rectangle section between two faces —
 * the hand-sketch convention (on-device report: a lid along the surface read as a white box
 * pasted ON the liner instead of material removed FROM it). Coordinate mapping is supplied by
 * the caller ([xPx]/[rPx]) so the overview canvas and the zoomed window run the same
 * construction at their own scales.
 */
internal fun DrawScope.drawUndercutNotches(
    notches: List<UndercutNotch>,
    xPx: (Float) -> Float,
    rPx: (Float) -> Float,
    cy: Float,
    voidColor: Color,
    outlineColor: Color,
    strokeWidthPx: Float,
    // Section-core refill — one step lighter than the caller's liner shade (see
    // UndercutStyle). Transparent in line-art mode, which leaves the core sheet-white.
    sectionFillColor: Color = Color.Black.copy(alpha = UNDERCUT_SECTION_FILL_ALPHA),
    // Dashed shoulders/floor mark a DRAFT notch (provisional, not yet in the record) —
    // the overlay passes a dash + a status color (primary while valid, error while its
    // confirm check fails); settled notches and the overview pass neither.
    pathEffect: PathEffect? = null,
) {
    notches.forEach { n ->
        n.profiles.forEach { p ->
            if (p.surface.size < 2) return@forEach
            val rFloor = rPx(p.floorDiaMm)
            val x0 = xPx(p.startMm)
            val x1 = xPx(p.endMm)
            val rSurfStart = rPx(p.surface.first().diaMm)
            val rSurfEnd = rPx(p.surface.last().diaMm)
            // The void's surface boundary overdraws OUTWARD by the stroke width: the
            // component outline is stroked centred on the surface line, so a fill that
            // stops exactly there would leave half of the *component's* stroke ragged
            // across the mouth. The mouth is then closed by the notch's own top edge
            // below, at the notch outline's weight/colour.
            val od = strokeWidthPx

            val topVoid = Path().apply {
                moveTo(xPx(p.surface.first().xMm), cy - rSurfStart - od)
                for (i in 1 until p.surface.size) {
                    lineTo(xPx(p.surface[i].xMm), cy - rPx(p.surface[i].diaMm) - od)
                }
                lineTo(x1, cy - rFloor)
                lineTo(x0, cy - rFloor)
                close()
            }
            val botVoid = Path().apply {
                moveTo(xPx(p.surface.first().xMm), cy + rSurfStart + od)
                for (i in 1 until p.surface.size) {
                    lineTo(xPx(p.surface[i].xMm), cy + rPx(p.surface[i].diaMm) + od)
                }
                lineTo(x1, cy + rFloor)
                lineTo(x0, cy + rFloor)
                close()
            }
            drawPath(topVoid, color = voidColor)
            drawPath(botVoid, color = voidColor)

            // Remaining core: erased to the sheet colour, then refilled one step LIGHTER
            // than the liner shade ([sectionFillColor] — half its alpha) so the section
            // reads distinct from the liner around it (on-device request).
            drawRect(voidColor, topLeft = Offset(x0, cy - rFloor), size = Size(x1 - x0, 2f * rFloor))
            drawRect(
                sectionFillColor,
                topLeft = Offset(x0, cy - rFloor),
                size = Size(x1 - x0, 2f * rFloor),
            )

            // Step-section outline: full-height faces where the surface stands above the
            // floor, then the floor lines. No lid — the mouth stays open.
            if (rSurfStart > rFloor + NOTCH_FACE_MIN_STEP_PX) {
                drawLine(outlineColor, Offset(x0, cy - rSurfStart), Offset(x0, cy + rSurfStart), strokeWidthPx, pathEffect = pathEffect)
            }
            if (rSurfEnd > rFloor + NOTCH_FACE_MIN_STEP_PX) {
                drawLine(outlineColor, Offset(x1, cy - rSurfEnd), Offset(x1, cy + rSurfEnd), strokeWidthPx, pathEffect = pathEffect)
            }
            drawLine(outlineColor, Offset(x0, cy - rFloor), Offset(x1, cy - rFloor), strokeWidthPx, pathEffect = pathEffect)
            drawLine(outlineColor, Offset(x0, cy + rFloor), Offset(x1, cy + rFloor), strokeWidthPx, pathEffect = pathEffect)
        }
    }
}

/** The liner outline's ink alpha on the white-sheet canvases (over `SheetInk.Outline`). */
internal const val LINER_OUTLINE_CANVAS_ALPHA = 0.4f

/**
 * The liner outline (`PdfPrefs.undercutLinerOutline`): a fine DOTTED retrace of each liner's
 * original OD and end faces over the stretches [notches] erased — the OD and shoulder steps
 * mirrored about [cy], an end face ONE full-height line from top OD to bottom OD through the
 * body — the pieces come from the ONE pure builder the PDF composer also runs
 * ([linerOutlineSegments]).
 * Called AFTER the notch passes, so the dots lie over the voids, and before rails and callouts.
 */
internal fun DrawScope.drawLinerOutlines(
    liners: List<UndercutLinerOd>,
    notches: List<UndercutNotch>,
    xPx: (Float) -> Float,
    rPx: (Float) -> Float,
    cy: Float,
    color: Color,
    strokeWidthPx: Float,
) {
    val dots = PathEffect.dashPathEffect(floatArrayOf(2f, 4f))
    liners.forEach { liner ->
        linerOutlineSegments(liner, notches).forEach { seg ->
            val r0 = rPx(seg.dia0Mm)
            val r1 = rPx(seg.dia1Mm)
            when (seg.edge) {
                LinerOutlineEdge.SURFACE -> {
                    val x0 = xPx(seg.x0Mm)
                    val x1 = xPx(seg.x1Mm)
                    drawLine(color, Offset(x0, cy - r0), Offset(x1, cy - r0), strokeWidthPx, pathEffect = dots)
                    drawLine(color, Offset(x0, cy + r0), Offset(x1, cy + r0), strokeWidthPx, pathEffect = dots)
                }
                LinerOutlineEdge.FULL_FACE -> {
                    val x = xPx(seg.x0Mm)
                    drawLine(color, Offset(x, cy - r1), Offset(x, cy + r1), strokeWidthPx, pathEffect = dots)
                }
                LinerOutlineEdge.STEP -> {
                    val x = xPx(seg.x0Mm)
                    drawLine(color, Offset(x, cy - r1), Offset(x, cy - r0), strokeWidthPx, pathEffect = dots)
                    drawLine(color, Offset(x, cy + r0), Offset(x, cy + r1), strokeWidthPx, pathEffect = dots)
                }
            }
        }
    }
}

/**
 * Cut numbers (`PdfPrefs.undercutCutNumbers`): each cut's sheet number ([numbers] —
 * `undercutSheetNumbers`, the Undercut tab list's numbering) drawn where the ONE pure placement
 * [undercutCutNumberPlacements] puts it — inside the cut's section core centred on the shaft
 * centreline, or above the surface
 * when the core is too narrow — the same placement the PDF composer prints. A notch with no
 * number (a draft not yet in the record) draws nothing. Plain sheet-ink text, no background.
 * Called after the notch passes and the liner outline, before rails and callouts. The caller
 * gates it on the pref.
 */
internal fun DrawScope.drawUndercutCutNumbers(
    notches: List<UndercutNotch>,
    numbers: Map<String, Int>,
    xPx: (Float) -> Float,
    rPx: (Float) -> Float,
    cy: Float,
    textSizePx: Float,
    color: Color = SheetInk.Outline,
    padPx: Float = textSizePx * 0.3f,
) {
    if (notches.isEmpty() || numbers.isEmpty()) return
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        this.color = color.toArgb()
        textAlign = android.graphics.Paint.Align.CENTER
        textSize = textSizePx
    }
    // Both scales are linear through the origin at every canvas site; their unit steps turn the
    // paint's px metrics into the axial mm / Ø units the placement works in.
    val pxPerMmX = (xPx(1000f) - xPx(0f)) / 1000f
    val pxPerDiaMm = (rPx(1000f) - rPx(0f)) / 1000f
    if (pxPerMmX <= 0f || pxPerDiaMm <= 0f) return
    val capHeightPx = -paint.fontMetrics.ascent * 0.75f
    val placements = undercutCutNumberPlacements(
        notches = notches,
        numbers = numbers,
        glyphWidthMm = { paint.measureText(it) / pxPerMmX },
        glyphHeightMm = capHeightPx / pxPerDiaMm,
        padMm = padPx / pxPerMmX,
        padDiaMm = padPx / pxPerDiaMm,
    )
    val native = drawContext.canvas.nativeCanvas
    placements.forEach { p ->
        val y = if (p.inside) cy + rPx(p.baselineDiaMm) else cy - rPx(p.baselineDiaMm)
        native.drawText(p.label, xPx(p.xMm), y, paint)
    }
}
