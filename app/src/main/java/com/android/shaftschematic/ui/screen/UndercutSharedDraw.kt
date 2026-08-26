package com.android.shaftschematic.ui.screen

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.android.shaftschematic.geom.NOTCH_FACE_MIN_STEP_PX
import com.android.shaftschematic.geom.UNDERCUT_SECTION_FILL_ALPHA
import com.android.shaftschematic.geom.UndercutLinerSpan
import com.android.shaftschematic.geom.UndercutNotch
import com.android.shaftschematic.ui.resolved.ResolvedComponent
import com.android.shaftschematic.ui.resolved.ResolvedLiner

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
