package com.android.shaftschematic.pdf

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.android.shaftschematic.geom.MIN_BLEND_WIDTH_PT
import com.android.shaftschematic.geom.ShoulderDrawSpec
import com.android.shaftschematic.geom.linerTopSilhouette
import com.android.shaftschematic.geom.shoulderDrawSpec
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.LinerAuthoredReference
import com.android.shaftschematic.model.shoulderOn
import kotlin.math.abs

// ──────────────────────────────────────────────────────────────────────────────
// Shouldered liners — ONE spec construction and ONE fill/stroke decomposition for the
// schematic and the runout/consolidated sheet. Each sheet maps mm through its OWN xAt/rPx
// (the schematic and the consolidated sheet compress differently), so a shoulder inherits
// its liner's foreshortening on whichever sheet it prints; the clamps, the radius doubling
// and the silhouette decomposition are shared, so the two sheets cannot drift apart.
//
// The two composers keep their own pass structure — the schematic fills and strokes one
// liner at a time, the runout sheet fills every liner in a pre-pass under the outlines —
// so the fill and the stroke are separate entry points rather than one draw call.
// ──────────────────────────────────────────────────────────────────────────────

/** Both ends of one liner in drawn units at one sheet's scale; null on an end that draws square. */
internal data class LinerShoulderSpecs(val aft: ShoulderDrawSpec?, val fwd: ShoulderDrawSpec?) {
    /** True when neither end has a step to draw — the liner is the plain rectangle it always was. */
    val square: Boolean get() = aft == null && fwd == null
}

/**
 * Resolve [ln]'s shoulders into drawn units for the sheet whose maps are [xAt]/[rPx].
 *
 * Each end's TRUE drawn length comes from mapping the shoulder's own two mm endpoints, so a
 * compressed liner's shoulder compresses with it; `shoulderDrawSpec` then applies the
 * blend-width visibility floor and host cap. The fillet value is a RADIUS in mm and [rPx]
 * takes a DIAMETER, so it maps at twice its stored value.
 */
internal fun linerShoulderSpecs(
    ln: Liner,
    x0: Float,
    x1: Float,
    linerRPx: Float,
    xAt: (Float) -> Float,
    rPx: (Float) -> Float,
    minWidthPx: Float = MIN_BLEND_WIDTH_PT,
): LinerShoulderSpecs = LinerShoulderSpecs(
    aft = linerShoulderSpecFor(ln, LinerAuthoredReference.AFT, x0, x1, linerRPx, xAt, rPx, minWidthPx),
    fwd = linerShoulderSpecFor(ln, LinerAuthoredReference.FWD, x0, x1, linerRPx, xAt, rPx, minWidthPx),
)

private fun linerShoulderSpecFor(
    ln: Liner,
    end: LinerAuthoredReference,
    x0: Float,
    x1: Float,
    linerRPx: Float,
    xAt: (Float) -> Float,
    rPx: (Float) -> Float,
    minWidthPx: Float,
): ShoulderDrawSpec? {
    val s = ln.shoulderOn(end) ?: return null
    val trueLenPx = when (end) {
        LinerAuthoredReference.AFT -> abs(xAt(ln.startFromAftMm + s.lenMm) - x0)
        LinerAuthoredReference.FWD -> abs(x1 - xAt(ln.startFromAftMm + ln.lengthMm - s.lenMm))
    }
    return shoulderDrawSpec(
        trueLenPx = trueLenPx,
        runWidthPx = abs(x1 - x0),
        linerRPx = linerRPx,
        shoulderRPx = rPx(s.odMm),
        filletRPx = rPx(s.radiusMm * 2f),
        minWidthPx = minWidthPx,
    )
}

/**
 * The liner's shade fill: the rectangle a square liner always drew, or the closed silhouette
 * polygon mirrored about [cy]. Fill and stroke decompose the SAME `linerTopSilhouette`, so
 * the shade can never disagree with the outline it sits under.
 */
internal fun drawLinerFillPdf(
    c: Canvas,
    cy: Float,
    x0: Float,
    x1: Float,
    linerRPx: Float,
    specs: LinerShoulderSpecs,
    fill: Paint,
) {
    if (specs.square) {
        c.drawRect(x0, cy - linerRPx, x1, cy + linerRPx, fill)
        return
    }
    val pts = linerTopSilhouette(x0, x1, linerRPx, specs.aft, specs.fwd)
    val path = Path()
    path.moveTo(pts.first().xPx, cy - pts.first().rPx)
    pts.drop(1).forEach { path.lineTo(it.xPx, cy - it.rPx) }
    pts.reversed().forEach { path.lineTo(it.xPx, cy + it.rPx) }
    path.close()
    c.drawPath(path, fill)
}

/**
 * The liner's elevated outline: the top and bottom surface lines in [outline], the end caps
 * in the thin [dim] paint. The step faces ride the silhouette's point list, so they reach the
 * stroke with no extra draw code, and a cap stands at the REDUCED OD on a shouldered end —
 * the cap IS the shoulder's outer face.
 */
internal fun drawLinerOutlinePdf(
    c: Canvas,
    cy: Float,
    x0: Float,
    x1: Float,
    linerRPx: Float,
    specs: LinerShoulderSpecs,
    outline: Paint,
    dim: Paint,
) {
    if (specs.square) {
        val top = cy - linerRPx
        val bot = cy + linerRPx
        c.drawLine(x0, top, x1, top, outline)
        c.drawLine(x0, bot, x1, bot, outline)
        c.drawLine(x0, top, x0, bot, dim)
        c.drawLine(x1, top, x1, bot, dim)
        return
    }
    val pts = linerTopSilhouette(x0, x1, linerRPx, specs.aft, specs.fwd)
    for (i in 1 until pts.size) {
        val a = pts[i - 1]
        val b = pts[i]
        c.drawLine(a.xPx, cy - a.rPx, b.xPx, cy - b.rPx, outline)
        c.drawLine(a.xPx, cy + a.rPx, b.xPx, cy + b.rPx, outline)
    }
    c.drawLine(x0, cy - pts.first().rPx, x0, cy + pts.first().rPx, dim)
    c.drawLine(x1, cy - pts.last().rPx, x1, cy + pts.last().rPx, dim)
}
