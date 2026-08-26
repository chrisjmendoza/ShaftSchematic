package com.android.shaftschematic.pdf

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.android.shaftschematic.geom.MIN_BLEND_WIDTH_PT
import com.android.shaftschematic.geom.SEAL_DASH_OFF_PT
import com.android.shaftschematic.geom.SEAL_DASH_ON_PT
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.settings.PDF_SBREAK_THRESHOLD_DEFAULT
import com.android.shaftschematic.model.KeywaySpan
import com.android.shaftschematic.ui.resolved.BodyBlend
import com.android.shaftschematic.ui.resolved.bodyDrawEdges
import kotlin.math.abs
import kotlin.math.min

// ──────────────────────────────────────────────────────────────────────────────
// Body runs with the S-break pair — ONE implementation for the schematic and the
// runout/consolidated sheet. The two composers maintained near-identical copies of this
// orchestration (same shared pure math underneath) whose only real divergence was an
// accidental z-order slip: the runout copy painted the right stub's shade fill AFTER the
// break edge, letting a shaded body's fill cover part of the S-curve. The schematic order
// (fill first, curve on top) is the behavior both sheets now share.
// ──────────────────────────────────────────────────────────────────────────────

internal fun drawBodyRunsWithBreaks(
    c: Canvas,
    bodies: List<Body>,
    cy: Float,
    xAt: (Float) -> Float,
    rPx: (Float) -> Float,
    outline: Paint,
    geomRect: RectF,
    fill: Paint? = null,
    /**
     * True-scale pt/mm — a body drawn below [breakMinFracOfTrue] of its true width at
     * this scale shows the S-break pair ([breakForCompression]); milder foreshortening
     * prints plain. 0 disables the check (break on span length alone).
     */
    truePtPerMm: Float = 0f,
    /** The user's `PdfPrefs.sBreakThresholdFrac`; 0 = never break on compression. */
    breakMinFracOfTrue: Float = PDF_SBREAK_THRESHOLD_DEFAULT,
    /**
     * Blended faces on these runs ([bodyBlends]). A blend is machined out of the body, so it
     * shortens the FLAT span and stands the end cap at the neighbouring diameter; the run's
     * compression treatment is otherwise untouched.
     */
    blends: List<BodyBlend> = emptyList(),
    /**
     * Protected body-keyway windows ([bodyKeywayProtectedSpansMm], absolute mm) the break
     * gap must never cut into: the slot region is pinned at true scale and must read as
     * real geometry, but the REST of a keyed body compresses and breaks like any other run —
     * a 95%-shaft body with an end keyway still needs its break (on-device report). The gap
     * shifts off the window ([breakGapCenter]); only a run with no clear placement at all
     * prints plain.
     */
    keywayAvoidSpansMm: List<KeywaySpan> = emptyList(),
) {
    val capPaint = Paint(outline).apply { style = Paint.Style.STROKE }
    val avoidX = keywayAvoidSpansMm.map {
        val a = xAt(it.loMm); val b2 = xAt(it.hiMm)
        minOf(a, b2)..maxOf(a, b2)
    }
    bodies.forEach { b ->
        if (b.lengthMm <= 0f || b.diaMm <= 0f) return@forEach
        val x0 = xAt(b.startFromAftMm); val x1 = xAt(b.startFromAftMm + b.lengthMm)
        val r = rPx(b.diaMm); val top = cy - r; val bot = cy + r

        val edges = bodyDrawEdges(
            runId = b.id,
            runStartMm = b.startFromAftMm,
            runEndMm = b.startFromAftMm + b.lengthMm,
            runDiaMm = b.diaMm,
            blends = blends,
            xAt = xAt,
            rAt = { dia -> rPx(dia) },
            minWidthPx = MIN_BLEND_WIDTH_PT,
        )
        val fx0 = edges.flatX0
        val fx1 = edges.flatX1

        // The break decision stays on the run's FULL drawn width — a blend is a face
        // detail, not a reason for the body to read as more or less compressed.
        val bodyLenPt = abs(x1 - x0)
        val foreshortened = breakForCompression(bodyLenPt, b.lengthMm, truePtPerMm, breakMinFracOfTrue)
        val compress = foreshortened || bodyLenPt >= COMPRESS_TRIGGER_PT

        drawBlendCurvePdf(c, edges.aftCurve, cy, outline, fill)
        drawBlendCurvePdf(c, edges.fwdCurve, cy, outline, fill)

        // Break layout first: the gap steers clear of any protected keyway window, and a
        // run with no clear placement falls back to the plain rectangle.
        val flatLenPt = abs(fx1 - fx0)
        val pair = if (compress) breakPairLayout(
            runLenPt = flatLenPt,
            desiredAmplitudePt = r * 0.6f,
            classicGapPt = min(ZIGZAG_GAP_MAX_PT, 0.25f * flatLenPt),
            strokeWidthPt = capPaint.strokeWidth,
        ) else null
        val gapCenter = pair?.let {
            breakGapCenter(minOf(fx0, fx1), maxOf(fx0, fx1), it.gapPt, avoidX)
        }

        if (pair == null || gapCenter == null) {
            // classic rectangle body
            if (fill != null) c.drawRect(fx0, top, fx1, bot, fill)
            c.drawLine(fx0, top, fx1, top, outline)
            c.drawLine(fx0, bot, fx1, bot, outline)
        } else {
            // break pair: two stubs, each with an S-curve end instead of a straight cap
            val (gap, amp) = pair
            val half = 0.5f * gap
            val leftEnd  = (gapCenter - half).coerceIn(geomRect.left, geomRect.right)
            val rightBeg = (gapCenter + half).coerceIn(geomRect.left, geomRect.right)

            // Left stub — S-curve on right end
            if (fill != null) c.drawRect(fx0, top, leftEnd, bot, fill)
            c.drawLine(fx0, top, leftEnd, top, outline)
            c.drawLine(fx0, bot, leftEnd, bot, outline)
            drawBreakEdge(c, leftEnd, top, bot, amp, capPaint, eyeAtTop = false)

            // Right stub — same-direction S-curve on left end (curves match so edges appear to merge)
            if (fill != null) c.drawRect(rightBeg, top, fx1, bot, fill)
            drawBreakEdge(c, rightBeg, top, bot, amp, capPaint, eyeAtTop = true)
            c.drawLine(rightBeg, top, fx1, top, outline)
            c.drawLine(rightBeg, bot, fx1, bot, outline)
        }

        // End caps last, at the OUTER ends of the whole run. A blended face caps at the
        // neighbour's radius (where the curve arrives), so the cap coincides with that
        // component's own face line instead of stranding a vertical inside the body.
        c.drawLine(x0, cy - edges.capAftR, x0, cy + edges.capAftR, outline)
        c.drawLine(x1, cy - edges.capFwdR, x1, cy + edges.capFwdR, outline)

        // Seal area: the radius cuts the fiberglass seats into, drawn across the blend.
        // Same construction and dash as the canvas renderer — both read `bodyDrawEdges`.
        // Dashed so the shaft still reads as one unit (a solid vertical is the
        // component-face glyph); finer than the hidden-keyway dash on purpose.
        if (edges.aftSeal.isNotEmpty() || edges.fwdSeal.isNotEmpty()) {
            val sealPaint = Paint(outline).apply {
                style = Paint.Style.STROKE
                pathEffect = android.graphics.DashPathEffect(
                    floatArrayOf(SEAL_DASH_ON_PT, SEAL_DASH_OFF_PT), 0f)
            }
            (edges.aftSeal + edges.fwdSeal).forEach { g ->
                c.drawLine(g.xPx, cy - g.rPx, g.xPx, cy + g.rPx, sealPaint)
            }
        }
    }
}
