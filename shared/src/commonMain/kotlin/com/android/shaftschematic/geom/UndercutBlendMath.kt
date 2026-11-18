package com.android.shaftschematic.geom

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Body-face blends on the undercut drawings. A blended face draws its ramp on the detail strips
 * the way the schematic does, and where a cut has taken part of the ramp away the solid line
 * stops at the cut's edge — that is where the machined blend now really ends — while the part
 * the cut removed is retraced in the liner outline's phantom line up to the face, where it meets
 * the liner's own retrace, and on along the body's flat surface past the ramp's foot as far as
 * the same cut took that away too (on-device requests). Pure geometry in shaft-space mm, shared by the canvas pass
 * (`ui/screen/UndercutSharedDraw.kt`) and the PDF composer (`pdf/UndercutPdfComposer.kt`); the
 * callers map x and Ø through their own scales and mirror every line about the centreline.
 */

/**
 * One blended face's surface at its TRUE mm span, aft → fwd — the very points the surface
 * envelope samples (`ui/resolved/SurfaceSegs.kt`), so the drawn ramp is the surface the notches
 * are cut against. [atAftFace] says which end of [points] is the body's face. [flatToMm] is where
 * the body's flat surface beyond the ramp's foot ends — the body's far face, or the middle of the
 * flat run when the far face is blended too; null when the body has no flat run on this side.
 */
data class UndercutBlendCurve(
    val atAftFace: Boolean,
    val points: List<SurfacePoint>,
    val flatToMm: Float? = null,
) {
    val startMm: Float get() = points.first().xMm
    val endMm: Float get() = points.last().xMm
    val faceMm: Float get() = if (atAftFace) startMm else endMm
    /** The Ø the ramp reaches at the face — the height of the body's face line there. */
    val faceDiaMm: Float get() = if (atAftFace) points.first().diaMm else points.last().diaMm

    /** The ramp followed by the body's flat surface on to [flatToMm], aft → fwd. */
    internal val pointsWithFlat: List<SurfacePoint>
        get() {
            val to = flatToMm ?: return points
            return if (atAftFace) {
                if (to > endMm) points + SurfacePoint(to, points.last().diaMm) else points
            } else {
                if (to < startMm) listOf(SurfacePoint(to, points.first().diaMm)) + points else points
            }
        }
}

/** The Ø of [points] (x ascending) at [xMm], linear between vertices and held past either end. */
internal fun surfacePolylineDiaAt(points: List<SurfacePoint>, xMm: Float): Float {
    if (points.isEmpty()) return 0f
    if (xMm <= points.first().xMm) return points.first().diaMm
    for (i in 1 until points.size) {
        val p0 = points[i - 1]
        val p1 = points[i]
        if (xMm <= p1.xMm) {
            val span = p1.xMm - p0.xMm
            return if (span <= 0f) p1.diaMm else p0.diaMm + (p1.diaMm - p0.diaMm) * ((xMm - p0.xMm) / span)
        }
    }
    return points.last().diaMm
}

/**
 * [points] (x ascending) cut down to `[x0Mm, x1Mm]`: the interpolated ends, every vertex strictly
 * between them, and a vertex at each of [stations] that falls inside — the places a caller's
 * axial map changes scale, so a sloped line stays on the surface across them. Empty when the
 * range misses the polyline.
 */
fun clipSurfacePolyline(
    points: List<SurfacePoint>,
    x0Mm: Float,
    x1Mm: Float,
    stations: List<Float> = emptyList(),
): List<SurfacePoint> {
    if (points.size < 2) return emptyList()
    val lo = max(x0Mm, points.first().xMm)
    val hi = min(x1Mm, points.last().xMm)
    if (hi <= lo) return emptyList()
    val xs = (listOf(lo) + points.map { it.xMm }.filter { it > lo && it < hi } +
        stations.filter { it > lo && it < hi } + hi).sorted().distinct()
    return xs.map { SurfacePoint(it, surfacePolylineDiaAt(points, it)) }
}

/**
 * The stretches of [curve] that [notches] erased, each as its own polyline aft → fwd — what the
 * drawings retrace in phantom line. Read off the notches' drawn REGIONS ([UndercutNotch.profiles]),
 * not their spans: where a cut's floor stands above the ramp nothing was removed, the ramp's solid
 * line still shows there, and a retrace over it would double it. Touching and nested cuts merge
 * into one stretch; a blend no cut reaches yields nothing. A stretch that takes in part of the
 * ramp runs on along the body's flat surface ([UndercutBlendCurve.flatToMm]) as far as it was
 * erased, so the retrace reaches the cut's edge rather than stopping at the ramp's foot (on-device
 * request); a cut lying wholly on the flat body, clear of the ramp, is not retraced.
 */
fun erasedBlendTraces(
    curve: UndercutBlendCurve,
    notches: List<UndercutNotch>,
    epsMm: Float = UNDERCUT_TOUCH_EPS_MM,
): List<List<SurfacePoint>> {
    if (curve.points.size < 2) return emptyList()
    val pts = curve.pointsWithFlat
    val lo = pts.first().xMm
    val hi = pts.last().xMm
    val merged = mutableListOf<Pair<Float, Float>>()
    notches
        .flatMap { it.profiles }
        .map { max(it.startMm, lo) to min(it.endMm, hi) }
        .filter { (a, b) -> b - a > epsMm }
        .sortedBy { it.first }
        .forEach { (a, b) ->
            val last = merged.lastOrNull()
            if (last != null && a <= last.second + epsMm) merged[merged.lastIndex] = last.first to max(last.second, b)
            else merged += a to b
        }
    return merged
        .filter { (a, b) -> min(b, curve.endMm) - max(a, curve.startMm) > epsMm }
        .map { (a, b) -> clipSurfacePolyline(pts, a, b) }
        .filter { it.size >= 2 }
}

/**
 * A body's top surface with its blended faces: AFT ramp, flat run, FWD ramp, as one polyline
 * aft → fwd, and the Ø each face line stands to. Both undercut draw sites stroke (and the sheet
 * fills) a blended body from these points.
 */
data class BlendedBodyTop(val points: List<SurfacePoint>, val aftFaceDiaMm: Float, val fwdFaceDiaMm: Float)

/**
 * The [BlendedBodyTop] of the body run `[startMm, endMm]` at [diaMm], or null when neither face
 * carries one of [curves]. Matched by span — a ramp belongs to the run whose face it starts at.
 */
fun blendedBodyTop(
    curves: List<UndercutBlendCurve>,
    startMm: Float,
    endMm: Float,
    diaMm: Float,
    epsMm: Float = UNDERCUT_TOUCH_EPS_MM,
): BlendedBodyTop? {
    val aft = curves.firstOrNull { it.atAftFace && abs(it.faceMm - startMm) <= epsMm && it.endMm <= endMm + epsMm }
    val fwd = curves.firstOrNull { !it.atAftFace && abs(it.faceMm - endMm) <= epsMm && it.startMm >= startMm - epsMm }
    if (aft == null && fwd == null) return null
    return BlendedBodyTop(
        points = (aft?.points ?: listOf(SurfacePoint(startMm, diaMm))) +
            (fwd?.points ?: listOf(SurfacePoint(endMm, diaMm))),
        aftFaceDiaMm = aft?.faceDiaMm ?: diaMm,
        fwdFaceDiaMm = fwd?.faceDiaMm ?: diaMm,
    )
}
