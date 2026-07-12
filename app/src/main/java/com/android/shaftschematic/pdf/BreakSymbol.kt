package com.android.shaftschematic.pdf

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path

// ──────────────────────────────────────────────────────────────────────────────
// Round-stock break symbol (shared by Shaft/Runout/Wear PDF composers)
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Draw one round-stock "S-break" edge at position [x], spanning the shaft from
 * [yTop] to [yBot].
 *
 * Two strokes: a full S-curve, plus a return sweep that starts at one tip of the
 * S, arcs back on the opposite side of that half's lobe, and dies into the S at
 * the centerline — enclosing the open "eye" that makes the break read as a
 * revolved round surface rather than a flat plate. [eyeAtTop] picks which tip
 * the sweep returns from; a break's two edges alternate (left edge bottom,
 * right edge top) the way the symbol is drawn by hand.
 *
 * Positive [amplitude] bulges the S right in the upper half and left in the
 * lower half; negative mirrors both strokes.
 */
internal fun drawBreakEdge(
    c: Canvas,
    x: Float,
    yTop: Float,
    yBot: Float,
    amplitude: Float,
    p: Paint,
    eyeAtTop: Boolean,
) {
    val h = yBot - yTop
    val cy = yTop + h / 2f
    val stroke = Paint(p).apply { style = Paint.Style.STROKE }

    // Return sweep: the S's own half-lobe (by de Casteljau subdivision at t=0.5)
    // mirrored about the break line and widened by RETURN_SWEEP_FULLNESS, so it
    // leaves the tip and rejoins the centerline tangent to the eye it closes.
    val k = RETURN_SWEEP_FULLNESS
    val sweep = Path().apply {
        if (eyeAtTop) {
            moveTo(x, yTop)
            cubicTo(x - k * amplitude / 2f, yTop + h / 6f, x - k * amplitude / 4f, yTop + h / 3f, x, cy)
        } else {
            moveTo(x, yBot)
            cubicTo(x + k * amplitude / 2f, yBot - h / 6f, x + k * amplitude / 4f, yBot - h / 3f, x, cy)
        }
    }

    // Shade the eye first so both strokes stay crisp on top. The closing curve is
    // the S's own half-lobe (subdivided controls), traced center → tip. Translucent
    // wash rather than opaque grey so it darkens shaded bodies too, like a shadow.
    val eye = Path(sweep).apply {
        if (eyeAtTop) {
            cubicTo(x + amplitude / 4f, yTop + h / 3f, x + amplitude / 2f, yTop + h / 6f, x, yTop)
        } else {
            cubicTo(x - amplitude / 4f, yBot - h / 3f, x - amplitude / 2f, yBot - h / 6f, x, yBot)
        }
        close()
    }
    c.drawPath(eye, Paint(p).apply { style = Paint.Style.FILL; color = EYE_SHADE_COLOR })

    c.drawPath(
        Path().apply {
            moveTo(x, yTop)
            cubicTo(x + amplitude, yTop + h / 3f, x - amplitude, yBot - h / 3f, x, yBot)
        },
        stroke,
    )
    c.drawPath(sweep, stroke)
}

/** Return-sweep width relative to the S's own lobe; 1 = exact mirror. */
private const val RETURN_SWEEP_FULLNESS = 1.5f

/** Light translucent wash inside the eye (~18% black; matches shaded-body recipe). */
private const val EYE_SHADE_COLOR = 0x2E000000
