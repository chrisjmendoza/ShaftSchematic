package com.android.shaftschematic.pdf

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.android.shaftschematic.geom.hatchSpan
import com.android.shaftschematic.geom.reliefSpan
import com.android.shaftschematic.geom.shoulderEndMm
import com.android.shaftschematic.geom.threadHatchLean
import com.android.shaftschematic.geom.threadHatchRun
import com.android.shaftschematic.geom.threadHatchSpacing
import com.android.shaftschematic.geom.WearTraceVertex
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Threads
import com.android.shaftschematic.util.ThreadHatchStyle
import kotlin.math.abs
import kotlin.math.max
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
    dimStrokeWidthPt: Float,
    /**
     * Whether a thread's relief groove draws. The undercut document shows it (its detail strips
     * do, and the main profile must agree); the WEAR document passes false — it omits machining
     * detail by product decision, the same posture as its keyway, blend and liner-shoulder
     * omissions — so a relieved thread prints there as the plain hatched envelope.
     */
    threadRelief: Boolean = true,
    /**
     * Worn-profile traces by liner id (`wearLinerTraces`, `geom/WearTraceMath.kt`): a liner with
     * an entry draws its surface edges and fill through the measured diameters, the detail strip's
     * construction mapped through THIS profile's scale, so the main profile answers the "Trace
     * depth exaggeration" slider like the strips do (on-device report: the slider changed nothing
     * on the shaft). Empty — every caller but the wear document — draws plain cylinders.
     */
    linerTraces: Map<String, List<WearTraceVertex>> = emptyMap(),
) {
    // ── Shade fills first (drawn under all outlines) ──────────────────────
    bodyFill?.let { f ->
        spec.bodies.forEach { b ->
            if (b.lengthMm <= 0f || b.diaMm <= 0f) return@forEach
            val x0 = xAt(b.startFromAftMm); val x1 = xAt(b.startFromAftMm + b.lengthMm)
            val r = rPx(b.diaMm); val top = cy - r; val bot = cy + r
            // A broken run fills as two stubs, each ending on the very S the outline pass
            // strokes. One rectangle across the whole run laid grey through the break gap —
            // which reads as bare paper — and squared off the curves at both stub ends.
            val brk = simpleBodyBreak(x0, x1, r, outline.strokeWidth, geomRect)
            if (brk == null) {
                c.drawRect(x0, top, x1, bot, f)
            } else {
                if (brk.leftEnd > x0) {
                    c.drawPath(breakStubFillPath(x0, brk.leftEnd, top, bot, brk.amplitudePt), f)
                }
                if (x1 > brk.rightBeg) {
                    c.drawPath(breakStubFillPath(x1, brk.rightBeg, top, bot, brk.amplitudePt), f)
                }
            }
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
            val x0 = xAt(ln.startFromAftMm); val x1 = xAt(ln.startFromAftMm + ln.lengthMm)
            val verts = linerTraces[ln.id].orEmpty()
            if (verts.isEmpty()) {
                c.drawRect(x0, cy - r, x1, cy + r, f)
            } else {
                c.drawPath(tracedLinerFillPath(x0, x1, cy, r, verts) { mm -> xAt(ln.startFromAftMm + mm) }, f)
            }
        }
    }
    // Bodies, with a compression break in any long run.
    val capPaint = Paint(outline)
    spec.bodies.forEach { b ->
        if (b.lengthMm <= 0f || b.diaMm <= 0f) return@forEach
        val x0 = xAt(b.startFromAftMm); val x1 = xAt(b.startFromAftMm + b.lengthMm)
        val r = rPx(b.diaMm); val top = cy - r; val bot = cy + r
        val brk = simpleBodyBreak(x0, x1, r, capPaint.strokeWidth, geomRect)
        if (brk == null) {
            c.drawLine(x0, top, x1, top, outline); c.drawLine(x0, bot, x1, bot, outline)
            c.drawLine(x0, top, x0, bot, outline); c.drawLine(x1, top, x1, bot, outline)
        } else {
            val lEnd = brk.leftEnd; val rBeg = brk.rightBeg; val amp = brk.amplitudePt
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
        val verts = linerTraces[ln.id].orEmpty()
        if (verts.isEmpty()) {
            c.drawLine(x0, top, x1, top, outline); c.drawLine(x0, bot, x1, bot, outline)
        } else {
            // Inside a traced wear band the edges dip through the measured diameters, mirrored
            // about the centreline — the detail strip's own edge construction.
            val xAtLocal: (Float) -> Float = { mm -> xAt(ln.startFromAftMm + mm) }
            c.drawPath(tracedLinerEdgePath(x0, x1, top, 1f, r, verts, xAtLocal), outline)
            c.drawPath(tracedLinerEdgePath(x0, x1, bot, -1f, r, verts, xAtLocal), outline)
        }
        c.drawLine(x0, top, x0, bot, dimPaint); c.drawLine(x1, top, x1, bot, dimPaint)
    }
    // Threads — outline envelope + slanted hatch so the machinist knows the zone is threaded,
    // and the relief groove where one is set (the ONE shared [drawThreadPdf]).
    val hatchPaint = Paint(outline).apply { strokeWidth = dimStrokeWidthPt * 0.6f; alpha = 160 }
    spec.threads.forEach { th -> drawThreadPdf(c, th, cy, xAt, rPx, outline, hatchPaint, relief = threadRelief) }
    // Coupler bolt slots — reference cutouts, same as the main schematic.
    val slotFill = Paint(outline).apply { style = Paint.Style.FILL; alpha = 40 }
    drawCouplerBoltSlots(c, spec.couplerBoltSlots, spec, cy, xAt, rPx, outline, slotFill)
}

/** Where a simple-profile body run's break pair lands; null when the run prints plain. */
private data class SimpleBodyBreak(val leftEnd: Float, val rightBeg: Float, val amplitudePt: Float)

/**
 * The centre break for one body run of the simple profile — ONE derivation, read by the shade-fill
 * pre-pass AND by the outline pass below it. The two passes are separated (all fills go under all
 * outlines, so a liner's shade lands over a body's), which is exactly why the break geometry has to
 * come from a single place: a stub's fill boundary and the S drawn on it cannot otherwise be kept
 * in step. These sheets draw at one flat scale, so a run breaks on drawn length alone.
 */
private fun simpleBodyBreak(
    x0: Float,
    x1: Float,
    r: Float,
    strokeWidthPt: Float,
    geomRect: RectF,
): SimpleBodyBreak? {
    val lenPt = abs(x1 - x0)
    if (lenPt < COMPRESS_TRIGGER_PT) return null
    val (gap, amp) = breakPairLayout(
        runLenPt = lenPt,
        desiredAmplitudePt = r * 0.6f,
        strokeWidthPt = strokeWidthPt,
    )
    val mid = (x0 + x1) * 0.5f
    val half = gap * 0.5f
    return SimpleBodyBreak(
        leftEnd = (mid - half).coerceIn(geomRect.left, geomRect.right),
        rightBeg = (mid + half).coerceIn(geomRect.left, geomRect.right),
        amplitudePt = amp,
    )
}

/**
 * One whole thread on a PDF sheet — the ONE thread pass the schematic (`drawThreads`), the
 * runout/consolidated profile and this simple profile all call, so a thread (relieved or not)
 * prints pixel-identically on every sheet (`ThreadHatchParityTest`). The span splits through
 * `geom/ThreadReliefMath.kt`:
 * - the hatch and the major-Ø envelope (top/bottom lines + both end faces) cover the HATCH span
 *   only — the envelope's face on the relief side is the full-height step from the groove up to
 *   the major diameter;
 * - the relief draws as a reduced rectangle: top/bottom lines at the relief radius, no hatch, and
 *   at the shoulder end the thread's own face spans the relief Ø only — the neighbouring body or
 *   taper draws its own larger face there, so the silhouette steps down into the groove and the
 *   shoulder face belongs to the shoulder. A relief that takes the whole thread closes its other
 *   end at the relief Ø too.
 * No hidden lines: the step is a visible silhouette. With [relief] false (the wear document)
 * the groove is ignored and the whole stored span prints as the plain hatched envelope.
 */
internal fun drawThreadPdf(
    c: Canvas,
    th: Threads,
    cy: Float,
    xAt: (Float) -> Float,
    rPx: (Float) -> Float,
    outline: Paint,
    hatchPaint: Paint,
    relief: Boolean = true,
) {
    if (th.lengthMm <= 0f || th.majorDiaMm <= 0f) return
    val r = rPx(th.majorDiaMm); val top = cy - r; val bot = cy + r
    val plain = if (relief) th else th.copy(reliefLenMm = 0f, reliefDiaMm = 0f)
    val hatch = plain.hatchSpan()
    hatch?.let { hs ->
        val x0 = xAt(hs.startMm); val x1 = xAt(hs.endMm)
        drawThreadHatch(c, min(x0, x1), max(x0, x1), top, bot, hatchPaint, th)
        // Envelope on top of the hatch
        c.drawLine(x0, top, x1, top, outline); c.drawLine(x0, bot, x1, bot, outline)
        c.drawLine(x0, top, x0, bot, outline); c.drawLine(x1, top, x1, bot, outline)
    }
    plain.reliefSpan()?.let { rs ->
        val x0 = xAt(rs.startMm); val x1 = xAt(rs.endMm)
        val rr = rPx(rs.diaMm)
        c.drawLine(x0, cy - rr, x1, cy - rr, outline); c.drawLine(x0, cy + rr, x1, cy + rr, outline)
        val xs = xAt(rs.shoulderEndMm(th.reliefAtAft))
        c.drawLine(xs, cy - rr, xs, cy + rr, outline)
        if (hatch == null) {
            val xo = if (th.reliefAtAft) x1 else x0
            c.drawLine(xo, cy - rr, xo, cy + rr, outline)
        }
    }
}

/**
 * Slanted hatch clipped to `[x0,x1] × [top,bot]` — the threaded-zone mark for [thread], whose
 * full drawn height is `bot − top`: strokes spaced by the thread's own pitch at that drawn
 * height's diametral scale, thinned by the app-wide density ([threadHatchSpacing],
 * [ThreadHatchStyle.density]), and leaning by its own pitch over its own diameter at the
 * app-wide slant factor ([threadHatchLean], [ThreadHatchStyle.slant]). The ONE recipe every
 * sheet hatches a thread with, so the same thread prints identically on all of them. Shared
 * with the undercut sheet's detail strips, which hatch a window-clipped slice of the same thread.
 */
internal fun drawThreadHatch(
    c: Canvas, x0: Float, x1: Float, top: Float, bot: Float, paint: Paint, thread: Threads,
) {
    drawThreadHatchStrokes(
        c, x0, x1, top, bot, paint,
        spacingPt = threadHatchSpacing(bot - top, thread.pitchMm, thread.majorDiaMm, ThreadHatchStyle.density),
        lean = threadHatchLean(thread.pitchMm, thread.majorDiaMm, ThreadHatchStyle.slant),
    )
}

/** The hatch strokes themselves: full-band lines at run/rise [lean], [spacingPt] apart, clipped. */
internal fun drawThreadHatchStrokes(
    c: Canvas, x0: Float, x1: Float, top: Float, bot: Float, paint: Paint, spacingPt: Float, lean: Float,
) {
    if (x1 <= x0 || bot <= top || spacingPt <= 0f) return
    val saved = c.save()
    c.clipRect(x0, top, x1, bot)
    val run = threadHatchRun(bot - top, lean)
    var hx = x0 - run
    while (hx <= x1) {
        c.drawLine(hx, bot, hx + run, top, paint)
        hx += spacingPt
    }
    c.restoreToCount(saved)
}

/**
 * A traced liner's fill: the closed polygon between the traced TOP edge (left→right) and the
 * traced BOTTOM edge (right→left), both from the ONE sequenced vertex run — the strip's
 * `tracedBandFillPath` posture applied to the whole liner, so fill and stroke bite together.
 */
internal fun tracedLinerFillPath(
    leftPt: Float,
    rightPt: Float,
    cy: Float,
    radiusPt: Float,
    verts: List<WearTraceVertex>,
    xAtLocalMm: (Float) -> Float,
): Path = Path().apply {
    moveTo(leftPt, cy - radiusPt)
    verts.forEach { v -> lineTo(xAtLocalMm(v.localMm), cy - radiusPt + v.depthFrac * radiusPt) }
    lineTo(rightPt, cy - radiusPt)
    lineTo(rightPt, cy + radiusPt)
    for (i in verts.indices.reversed()) {
        val v = verts[i]
        lineTo(xAtLocalMm(v.localMm), cy + radiusPt - v.depthFrac * radiusPt)
    }
    lineTo(leftPt, cy + radiusPt)
    close()
}
