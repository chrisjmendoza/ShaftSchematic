package com.android.shaftschematic.pdf

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.android.shaftschematic.geom.KeywaySilhouetteNotch
import com.android.shaftschematic.geom.KeywaySilhouettePoint
import com.android.shaftschematic.geom.MIN_KEYWAY_WIDTH_PT
import com.android.shaftschematic.geom.drawnKeywayHalfWidthPx
import com.android.shaftschematic.geom.fillPolygonMm
import com.android.shaftschematic.geom.floorMm
import com.android.shaftschematic.geom.keywaySilhouetteNotch
import com.android.shaftschematic.geom.keywaySpoonBowl
import com.android.shaftschematic.geom.minKeywaySlotLenPx
import com.android.shaftschematic.geom.wallEndMm
import com.android.shaftschematic.geom.wallStartMm
import com.android.shaftschematic.geom.yFor
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.KeywayClocking
import com.android.shaftschematic.model.LinerAuthoredReference
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.hasKeyway
import com.android.shaftschematic.model.keywayAbsSpanMm
import com.android.shaftschematic.ui.drawing.render.HIDDEN_DASH_OFF
import com.android.shaftschematic.ui.drawing.render.HIDDEN_DASH_ON
import kotlin.math.max
import kotlin.math.min

// ──────────────────────────────────────────────────────────────────────────────
// The whole-sheet keyway draw cluster — the schematic and the runout/consolidated
// sheet both cut their body and taper keyway slots through these functions
// (`RunoutPdfComposer` calls `drawBodyKeywaysPdf`/`drawTaperKeywayPdf` directly), so a
// keyway can never print on one sheet and go missing on the other.
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Body keyway pass for a whole sheet: the clocking decision plus the slot (or silhouette
 * notch) draw. The schematic and the runout/consolidated sheet both call this, so a keyway
 * can never print on one sheet and go missing from the other.
 *
 * [bodies] must be the **STORED** bodies. Keyways are authored on stored bodies and
 * [bodyForPdf] carries drawable geometry only — a resolved body's [hasKeyway] is always
 * false, so a resolved list draws nothing at all. Stored spans also keep one slot per
 * authored keyway where a liner trims the run into several drawn pieces, and put the slot at
 * its physical position (fragments and the center-break pair keep true end faces).
 *
 * [diaPtPerMm] is the sheet's DIAMETER scale — every transverse dimension a keyway draws (a
 * silhouette notch's depth, a plan-view slot's width) rides it, so keyways scale with the drawn
 * shaft height rather than with the compressed x map.
 */
internal fun drawBodyKeywaysPdf(
    c: Canvas,
    bodies: List<Body>,
    xAt: (Float) -> Float,
    cy: Float,
    diaPtPerMm: Float,
    outline: Paint,
    clocking: KeywayClocking = KeywayClocking.NONE,
    hiddenKeywayIds: Set<String> = emptySet(),
    secondaryKeywayIds: Set<String> = emptySet(),
) {
    val silhouetteKeyways = clocking == KeywayClocking.DEG_90_CW || clocking == KeywayClocking.DEG_90_CCW
    bodies.filter { it.hasKeyway }.forEach { b ->
        if (silhouetteKeyways && b.id in secondaryKeywayIds) {
            b.keywaySilhouetteNotch(clocking)?.let {
                drawKeywaySilhouetteNotchPdf(c, it, xAt, diaPtPerMm, cy, outline)
            }
        } else {
            drawKeywayNotchBodyPdf(c, b, xAt, cy, diaPtPerMm, outline, hidden = b.id in hiddenKeywayIds)
        }
    }
}

/**
 * One taper's keyway, drawn after its outline — the same clocking decision as
 * [drawBodyKeywaysPdf], shared by the schematic's taper pass and the runout sheet's so the
 * two keyway passes on one sheet cannot disagree about which host is a secondary.
 */
internal fun drawTaperKeywayPdf(
    c: Canvas,
    t: Taper,
    x0: Float, x1: Float, top0: Float, top1: Float,
    xAt: (Float) -> Float,
    cy: Float,
    diaPtPerMm: Float,
    outline: Paint,
    clocking: KeywayClocking = KeywayClocking.NONE,
    hiddenKeywayIds: Set<String> = emptySet(),
    secondaryKeywayIds: Set<String> = emptySet(),
) {
    val silhouetteKeyways = clocking == KeywayClocking.DEG_90_CW || clocking == KeywayClocking.DEG_90_CCW
    if (silhouetteKeyways && t.id in secondaryKeywayIds) {
        t.keywaySilhouetteNotch(clocking)?.let {
            drawKeywaySilhouetteNotchPdf(c, it, xAt, diaPtPerMm, cy, outline)
        }
    } else {
        drawKeywayNotchPdf(c, t, x0, x1, top0, top1, cy, diaPtPerMm, outline, hidden = t.id in hiddenKeywayIds)
    }
}

/**
 * Draw a 90°-clocked secondary keyway as a notch cut into the host's silhouette edge — the true
 * edge-on projection, where the slot's depth is what shows in profile.
 *
 * Geometry (span clamp, floor radii, top/bottom side) comes entirely from
 * `geom/KeywaySilhouetteMath.kt`; this only maps mm → pt. The canvas
 * mirror is `ShaftRenderer.drawKeywaySilhouetteNotch` — the two must stay in lockstep.
 *
 * The void polygon is filled first so it erases the host outline segment crossing the notch
 * (keyways draw after outlines), then the two walls and the floor are stroked. The original
 * surface line is deliberately not stroked — the notch is open at the surface.
 */
private fun drawKeywaySilhouetteNotchPdf(
    c: Canvas,
    notch: KeywaySilhouetteNotch,
    xAt: (Float) -> Float,
    ptPerMm: Float,
    cy: Float,
    outline: Paint,
) {
    fun y(radiusMm: Float) = notch.side.yFor(cy, radiusMm, ptPerMm)

    val path = Path().apply {
        notch.fillPolygonMm().forEachIndexed { i, p ->
            val px = xAt(p.xMm); val py = y(p.radiusMm)
            if (i == 0) moveTo(px, py) else lineTo(px, py)
        }
        close()
    }
    // The keyway is a void — always white, regardless of the host's fill shading.
    val whiteFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    c.drawPath(path, whiteFill)

    fun stroke(seg: Pair<KeywaySilhouettePoint, KeywaySilhouettePoint>) {
        val (a, b) = seg
        c.drawLine(xAt(a.xMm), y(a.radiusMm), xAt(b.xMm), y(b.radiusMm), outline)
    }
    stroke(notch.wallStartMm())
    stroke(notch.floorMm())
    stroke(notch.wallEndMm())
}

internal fun drawKeywayNotchPdf(
    c: Canvas,
    t: Taper,
    x0: Float, x1: Float,
    top0: Float,
    top1: Float,
    cy: Float,
    diaPtPerMm: Float,
    outline: Paint,
    hidden: Boolean = false,
) {
    if (x1 == x0 || t.keywayWidthMm <= 0f) return

    val setAtStart = t.startDiaMm <= t.endDiaMm
    val setX = if (setAtStart) x0 else x1
    val letX = if (setAtStart) x1 else x0
    val dir  = if (letX > setX) 1f else -1f

    // Axial scale from the taper's own pixel span — the compressed map is linear across one
    // taper, so this is its local pt/mm.
    val axialPtPerMm = if (t.lengthMm > 0f) kotlin.math.abs(x1 - x0) / t.lengthMm else 1f
    // The keyway sits at the SET (small) end, so the smaller drawn radius is the host the
    // slot must stay inside.
    val hostRadiusPt = min(cy - top0, cy - top1)

    drawKeywaySlotPdf(
        c, setX, dir, axialPtPerMm, diaPtPerMm, hostRadiusPt,
        t.keywayWidthMm, t.keywayOffsetFromSetMm, t.keywayLengthMm, cy, outline, hidden, t.keywaySpooned,
    )
}

/**
 * Body-hosted keyway (intermediate shafts with fitted couplings). Same plan-view slot
 * as the taper keyway, referenced from the body's AFT or FWD end face.
 */
internal fun drawKeywayNotchBodyPdf(
    c: Canvas,
    b: Body,
    xAt: (Float) -> Float,
    cy: Float,
    diaPtPerMm: Float,
    outline: Paint,
    hidden: Boolean = false,
) {
    if (b.keywayWidthMm <= 0f || b.lengthMm <= 0f) return
    val x0 = xAt(b.startFromAftMm)
    val x1 = xAt(b.startFromAftMm + b.lengthMm)
    if (x1 == x0) return

    val aftRef = b.keywayEnd == LinerAuthoredReference.AFT
    val refX = if (aftRef) x0 else x1
    val farX = if (aftRef) x1 else x0
    val dir  = if (farX > refX) 1f else -1f

    // The slot's AXIAL scale comes from ITS OWN mapped span, not the whole body's average: the
    // keyway window is pinned at true scale while the rest of a long body compresses, so a
    // body-average pt/mm would draw the slot shrunken inside the very window that was
    // pinned to keep it real. Within the pinned window the map is linear, so this is exact;
    // an anchor reconstructed one offset back keeps a floating slot at its mapped position.
    val span = b.keywayAbsSpanMm()
    val axialPtPerMm: Float
    val anchorX: Float
    if (span != null && span.hiMm > span.loMm) {
        val sLo = xAt(span.loMm); val sHi = xAt(span.hiMm)
        axialPtPerMm = kotlin.math.abs(sHi - sLo) / (span.hiMm - span.loMm)
        anchorX = (if (aftRef) sLo else sHi) - dir * b.keywayOffsetFromEndMm * axialPtPerMm
    } else {
        axialPtPerMm = kotlin.math.abs(x1 - x0) / b.lengthMm
        anchorX = refX
    }

    drawKeywaySlotPdf(
        c, anchorX, dir, axialPtPerMm, diaPtPerMm, (b.diaMm * 0.5f) * diaPtPerMm,
        b.keywayWidthMm, b.keywayOffsetFromEndMm, b.keywayLengthMm, cy, outline, hidden, b.keywaySpooned,
    )
}

/**
 * Shared keyway slot geometry. [refX] is the referenced face (SET face for tapers,
 * AFT/FWD end face for bodies); [dir] is +1 when the slot extends rightward from it.
 * offset ≈ 0 = open at the referenced face; > 0 = floating (mill arcs both ends).
 *
 * Two scales, because the sheet has two: the slot's offset and length ride
 * [axialPtPerMm] (the compressed x map's local scale), its WIDTH and mill-arc radius ride
 * [diaPtPerMm] — the same scale as the drawn shaft height, so the slot stays proportional to
 * its host at every "Shaft height" setting. See `geom/KeywaySlotMath.kt` for the floor and the
 * [hostRadiusPt] cap.
 *
 * [hidden] draws the slot as a far-side feature (keyways 180° apart): dashed outline and
 * **no** white void fill (the near surface is unbroken). Dash matches the preview
 * renderer (`HIDDEN_DASH_ON`/`HIDDEN_DASH_OFF`).
 */
private fun drawKeywaySlotPdf(
    c: Canvas,
    refX: Float,
    dir: Float,
    axialPtPerMm: Float,
    diaPtPerMm: Float,
    hostRadiusPt: Float,
    widthMm: Float,
    offsetMm: Float,
    lengthMm: Float,
    cy: Float,
    outline: Paint,
    hidden: Boolean = false,
    spooned: Boolean = false,
) {
    // Two half-widths, one per axis. [halfW] is the AXIAL term and drives every x: the slot's
    // straight run, its arc centres, and the spoon bowl's reach. [halfH] is the TRANSVERSE term
    // off the diameter scale, so the slot's drawn width tracks the drawn shaft height. Every
    // round part is then an ellipse (halfW, halfH) — a true circle at the transverse scale grows
    // axially with the height slider until the spoon bowl eats its own slot (on-device report).
    val halfW       = (widthMm * axialPtPerMm) / 2f
    val halfH       = drawnKeywayHalfWidthPx(
        trueHalfWidthPx = (widthMm * diaPtPerMm) / 2f,
        hostRadiusPx = hostRadiusPt,
        minWidthPx = MIN_KEYWAY_WIDTH_PT,
        strokeWidthPx = outline.strokeWidth,
    )
    val isOpen      = offsetMm < 0.01f
    val kwSetX      = refX + dir * offsetMm * axialPtPerMm
    val kwLetX      = kwSetX + dir * maxOf(lengthMm * axialPtPerMm, minKeywaySlotLenPx(halfW, isOpen))

    // Spooned (open keyways only): keep the normal keyway (full-length walls + mill semicircle) and
    // ADD an enlarged bowl around the closed (LET) end — the mill end stays as an inner reference
    // line inside the bowl. The bowl's y-semi comes from the math itself (uniform drawn
    // clearance), never from stretching its x-radius by the sheet's scale ratio — that drew a
    // tall bowl on every compressed sheet (on-device report). Floating keyways ignore the flag.
    // Mirrors the canvas renderer.
    val bowl        = if (spooned && isOpen && halfW > 0f) keywaySpoonBowl(kwLetX, dir, halfW, halfH) else null
    val bowlRy      = bowl?.ry ?: 0f

    val letArcCx    = kwLetX - dir * halfW
    val letArcStart = if (dir > 0) 270f else 90f
    val letOval     = android.graphics.RectF(letArcCx - halfW, cy - halfH, letArcCx + halfW, cy + halfH)

    val setArcCx    = kwSetX + dir * halfW
    val setArcStart = if (dir > 0) 90f else 270f
    val setOval     = android.graphics.RectF(setArcCx - halfW, cy - halfH, setArcCx + halfW, cy + halfH)

    val lineNear  = if (isOpen) kwSetX else setArcCx
    val lineFar   = letArcCx
    val lineLeft  = min(lineNear, lineFar)
    val lineRight = max(lineNear, lineFar)

    // ── White fill (keyway is a void in the material) ──
    // Far-side (hidden) keyways aren't cut into the near surface, so they get no fill.
    // Inset from the SET face by one stroke-width so the taper end-face line keeps
    // its full thickness where it meets the keyway fill.
    if (!hidden) {
        val strokeW   = outline.strokeWidth
        val fillNear  = if (isOpen) kwSetX + dir * strokeW else setArcCx
        val fillLeft  = min(fillNear, letArcCx)
        val fillRight = max(fillNear, letArcCx)
        val whiteFill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.FILL
            color = android.graphics.Color.WHITE
        }
        c.drawRect(fillLeft, cy - halfH, fillRight, cy + halfH, whiteFill)
        c.drawArc(letOval, letArcStart, 180f, false, whiteFill)
        if (!isOpen) c.drawArc(setOval, setArcStart, 180f, false, whiteFill)
        // Spoon bowl void: the enlarged disc around the LET end (its SET-side overlaps the slot).
        if (bowl != null) {
            c.drawOval(
                android.graphics.RectF(
                    bowl.cx - bowl.radius, cy - bowlRy, bowl.cx + bowl.radius, cy + bowlRy,
                ),
                whiteFill,
            )
        }
    }

    // ── Outline strokes on top (dashed for hidden far-side keyways) ──
    val stroke = if (hidden) {
        android.graphics.Paint(outline).apply {
            pathEffect = android.graphics.DashPathEffect(floatArrayOf(HIDDEN_DASH_ON, HIDDEN_DASH_OFF), 0f)
        }
    } else outline
    c.drawLine(lineLeft, cy - halfH, lineRight, cy - halfH, stroke)
    c.drawLine(lineLeft, cy + halfH, lineRight, cy + halfH, stroke)
    // Inner mill semicircle — the standard rounded end (kept as a reference line inside the bowl).
    c.drawArc(letOval, letArcStart, 180f, false, stroke)
    if (!isOpen) c.drawArc(setOval, setArcStart, 180f, false, stroke)
    // Spoon bowl: the enlarged ellipse's major arc around the far side.
    if (bowl != null) {
        val bowlOval = android.graphics.RectF(
            bowl.cx - bowl.radius, cy - bowlRy, bowl.cx + bowl.radius, cy + bowlRy,
        )
        c.drawArc(bowlOval, bowl.arcStartDeg, bowl.arcSweepDeg, false, stroke)
    }
    // Open keyway: shaft face end-line already closes the SET end.
}
