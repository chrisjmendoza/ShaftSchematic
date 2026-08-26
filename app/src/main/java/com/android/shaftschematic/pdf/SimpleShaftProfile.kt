package com.android.shaftschematic.pdf

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.android.shaftschematic.model.ShaftSpec
import kotlin.math.abs
import kotlin.math.min

// ──────────────────────────────────────────────────────────────────────────────
// The SIMPLE whole-shaft profile — ONE implementation shared by the wear document
// and the undercut document.
//
// "Simple" means: square faces, no keyways, no body blends or seal areas, no machining
// detail. For the WEAR document that is a standing product decision — it "omits machining
// detail by product decision", the same posture as its keyway omission. The UNDERCUT
// document merely draws the same simple profile TODAY and is deliberately free to grow
// machining detail later (on-device ruling: blends stay allowed there in case they are
// ever needed) — growing them means giving that sheet its own richer pass or extending
// this one behind a caller switch, never quietly upgrading the wear document with it.
//
// Both sheets draw this profile at ONE flat pt/mm (no compression solve, no
// foreshortening budget), so a long body run breaks purely on drawn length
// (COMPRESS_TRIGGER_PT) — never on the user's "Body S-break" compression threshold,
// which exists to expose hidden foreshortening these two sheets do not do. That, plus
// the absence of blends and of protected keyway windows, is why this stays separate
// from drawBodyRunsWithBreaks (the schematic / consolidated-sheet body pass): the fills
// here also come from the pre-pass below, so the run loop paints no fill of its own.
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Draws the whole shaft — shade fills, bodies (with a center break in a long run),
 * tapers, liners, threaded zones, and the reference coupler bolt slots — into the band
 * centred on [cy], mapping mm to points through [xAt] / [rPx].
 *
 * @param spec     The DRAWN spec (`withResolvedBodies` already applied by the caller).
 * @param geomRect The profile band; a break gap is clamped inside it.
 * @param ptPerMm  Flat axial scale, used only to size the thread hatch pitch.
 * @param dimStrokeWidthPt Stroke weight for the sheet's secondary lines — the liner end
 *        faces, and (× 0.6) the thread hatch. Both callers pass their dim weight as a
 *        ratio of the (already thickness-scaled) outline weight, so Settings → "Line
 *        thickness" reaches these strokes on both sheets; the parameter stays because
 *        each sheet's ratio rides its own private constants.
 */
internal fun drawSimpleShaftProfile(
    c: Canvas,
    spec: ShaftSpec,
    cy: Float,
    outline: Paint,
    geomRect: RectF,
    xAt: (Float) -> Float,
    rPx: (Float) -> Float,
    bodyFill: Paint?,
    taperFill: Paint?,
    linerFill: Paint?,
    ptPerMm: Float,
    dimStrokeWidthPt: Float,
) {
    // ── Shade fills first (drawn under all outlines) ──────────────────────
    bodyFill?.let { f ->
        spec.bodies.forEach { b ->
            if (b.lengthMm <= 0f || b.diaMm <= 0f) return@forEach
            val r = rPx(b.diaMm)
            c.drawRect(xAt(b.startFromAftMm), cy - r, xAt(b.startFromAftMm + b.lengthMm), cy + r, f)
        }
    }
    taperFill?.let { f ->
        spec.tapers.forEach { t ->
            if (t.lengthMm <= 0f || (t.startDiaMm <= 0f && t.endDiaMm <= 0f)) return@forEach
            val path = Path().apply {
                moveTo(xAt(t.startFromAftMm), cy - rPx(t.startDiaMm))
                lineTo(xAt(t.startFromAftMm + t.lengthMm), cy - rPx(t.endDiaMm))
                lineTo(xAt(t.startFromAftMm + t.lengthMm), cy + rPx(t.endDiaMm))
                lineTo(xAt(t.startFromAftMm), cy + rPx(t.startDiaMm))
                close()
            }
            c.drawPath(path, f)
        }
    }
    linerFill?.let { f ->
        spec.liners.forEach { ln ->
            if (ln.lengthMm <= 0f || ln.odMm <= 0f) return@forEach
            val r = rPx(ln.odMm)
            c.drawRect(xAt(ln.startFromAftMm), cy - r, xAt(ln.startFromAftMm + ln.lengthMm), cy + r, f)
        }
    }
    // Bodies, with a compression break in any long run.
    val capPaint = Paint(outline)
    spec.bodies.forEach { b ->
        if (b.lengthMm <= 0f || b.diaMm <= 0f) return@forEach
        val x0 = xAt(b.startFromAftMm); val x1 = xAt(b.startFromAftMm + b.lengthMm)
        val r = rPx(b.diaMm); val top = cy - r; val bot = cy + r
        val lenPt = abs(x1 - x0)
        if (lenPt < COMPRESS_TRIGGER_PT) {
            c.drawLine(x0, top, x1, top, outline); c.drawLine(x0, bot, x1, bot, outline)
            c.drawLine(x0, top, x0, bot, outline); c.drawLine(x1, top, x1, bot, outline)
        } else {
            val mid = (x0 + x1) * 0.5f
            val (gap, amp) = breakPairLayout(
                runLenPt = lenPt,
                desiredAmplitudePt = r * 0.6f,
                classicGapPt = min(ZIGZAG_GAP_MAX_PT, 0.25f * lenPt),
                strokeWidthPt = capPaint.strokeWidth,
            )
            val half = gap * 0.5f
            val lEnd = (mid - half).coerceIn(geomRect.left, geomRect.right)
            val rBeg = (mid + half).coerceIn(geomRect.left, geomRect.right)
            c.drawLine(x0, top, lEnd, top, outline); c.drawLine(x0, bot, lEnd, bot, outline)
            c.drawLine(x0, top, x0, bot, outline)
            drawBreakEdge(c, lEnd, top, bot, amp, capPaint, eyeAtTop = false)
            drawBreakEdge(c, rBeg, top, bot, amp, capPaint, eyeAtTop = true)
            c.drawLine(rBeg, top, x1, top, outline); c.drawLine(rBeg, bot, x1, bot, outline)
            c.drawLine(x1, top, x1, bot, outline)
        }
    }
    // Tapers — the trapezoid, square-faced at both ends.
    spec.tapers.forEach { t ->
        if (t.lengthMm <= 0f || (t.startDiaMm <= 0f && t.endDiaMm <= 0f)) return@forEach
        val x0 = xAt(t.startFromAftMm); val x1 = xAt(t.startFromAftMm + t.lengthMm)
        val top0 = cy - rPx(t.startDiaMm); val bot0 = cy + rPx(t.startDiaMm)
        val top1 = cy - rPx(t.endDiaMm);   val bot1 = cy + rPx(t.endDiaMm)
        c.drawLine(x0, top0, x1, top1, outline); c.drawLine(x0, bot0, x1, bot1, outline)
        c.drawLine(x0, top0, x0, bot0, outline); c.drawLine(x1, top1, x1, bot1, outline)
    }
    // Liners — surface lines at outline weight, end faces at the lighter dim weight.
    val dimPaint = Paint(outline).apply { strokeWidth = dimStrokeWidthPt }
    spec.liners.forEach { ln ->
        if (ln.lengthMm <= 0f || ln.odMm <= 0f) return@forEach
        val x0 = xAt(ln.startFromAftMm); val x1 = xAt(ln.startFromAftMm + ln.lengthMm)
        val r = rPx(ln.odMm); val top = cy - r; val bot = cy + r
        c.drawLine(x0, top, x1, top, outline); c.drawLine(x0, bot, x1, bot, outline)
        c.drawLine(x0, top, x0, bot, dimPaint); c.drawLine(x1, top, x1, bot, dimPaint)
    }
    // Threads — outline envelope + diagonal hatch so the machinist knows the zone is threaded.
    val hatchPaint = Paint(outline).apply { strokeWidth = dimStrokeWidthPt * 0.6f; alpha = 160 }
    spec.threads.forEach { th ->
        if (th.lengthMm <= 0f || th.majorDiaMm <= 0f) return@forEach
        val x0 = xAt(th.startFromAftMm); val x1 = xAt(th.startFromAftMm + th.lengthMm)
        val r = rPx(th.majorDiaMm); val top = cy - r; val bot = cy + r
        val pitchPt = ((th.pitchMm.takeIf { it > 0f } ?: 2.5f) * ptPerMm).coerceIn(4f, 18f)
        drawThreadHatch(c, x0, x1, top, bot, hatchPaint, pitchPt)
        c.drawLine(x0, top, x1, top, outline); c.drawLine(x0, bot, x1, bot, outline)
        c.drawLine(x0, top, x0, bot, outline); c.drawLine(x1, top, x1, bot, outline)
    }
    // Coupler bolt slots — reference cutouts, same as the main schematic.
    val slotFill = Paint(outline).apply { style = Paint.Style.FILL; alpha = 40 }
    drawCouplerBoltSlots(c, spec.couplerBoltSlots, spec, cy, xAt, rPx, outline, slotFill)
}

/**
 * Diagonal hatch clipped to `[x0,x1] × [top,bot]` — the threaded-zone mark. Shared with the
 * undercut sheet's detail strips, which hatch a window-clipped slice of the same thread.
 */
internal fun drawThreadHatch(
    c: Canvas, x0: Float, x1: Float, top: Float, bot: Float, paint: Paint, pitchPt: Float,
) {
    if (x1 <= x0) return
    val saved = c.save()
    c.clipRect(x0, top, x1, bot)
    var hx = x0 - (bot - top)
    while (hx <= x1) {
        c.drawLine(hx, bot, hx + (bot - top), top, paint)
        hx += pitchPt
    }
    c.restoreToCount(saved)
}
