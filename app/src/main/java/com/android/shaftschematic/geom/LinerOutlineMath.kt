package com.android.shaftschematic.geom

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The undercut sheet's **liner outline** (`PdfPrefs.undercutLinerOutline`): a fine dotted trace
 * of each liner's ORIGINAL edges — its OD and its two end faces — over the stretches the cuts have
 * erased, so a cut split between the body and a liner still shows where the liner was (on-device
 * request). Pure geometry, shared by the canvas pass (`ui/screen/UndercutSharedDraw.kt`) and the
 * PDF composer (`pdf/UndercutPdfComposer.kt`), so the two draw the same dotted lines from the same
 * numbers. Every value is shaft-space mm; the callers map x and Ø through their own scales and
 * mirror each piece about the centreline.
 */

/**
 * A liner's shaft-space span and OD — the outline's input. [surface] is the liner's OWN outer
 * surface aft → fwd: a single seg at [odMm] for a square liner, the reduced shoulder segs at a
 * shouldered end (`ui/resolved/SurfaceSegs.kt`'s `linerSurfaceSegs`, modelled square), so the
 * retrace follows the shoulder step.
 */
data class UndercutLinerOd(
    val id: String,
    val startMm: Float,
    val endMm: Float,
    val odMm: Float,
    val surface: List<SurfaceSeg> = listOf(SurfaceSeg(startMm, endMm, odMm, odMm)),
)

/** Which original liner edge a [LinerOutlineSeg] retraces. */
enum class LinerOutlineEdge {
    /** The OD line (or a shoulder's reduced OD), axial from `x0Mm` to `x1Mm` at `dia0Mm`. */
    SURFACE,

    /** A shoulder step inside the liner, radial at `x0Mm` from `dia0Mm` up to `dia1Mm`. */
    STEP,

    /** The AFT end face, radial at `x0Mm` (= `x1Mm`) from `dia0Mm` up to `dia1Mm`. */
    AFT_FACE,

    /** The FWD end face, radial at `x0Mm` (= `x1Mm`) from `dia0Mm` up to `dia1Mm`. */
    FWD_FACE,
}

/**
 * One dotted piece. A [LinerOutlineEdge.SURFACE] piece runs `x0Mm → x1Mm` at `dia0Mm`
 * (`== dia1Mm`); every other piece stands at `x0Mm` from `dia0Mm` to `dia1Mm`.
 */
data class LinerOutlineSeg(
    val edge: LinerOutlineEdge,
    val x0Mm: Float,
    val x1Mm: Float,
    val dia0Mm: Float,
    val dia1Mm: Float,
)

/**
 * The dotted pieces that retrace [liner]'s original outline where [notches] erased it — ERASED
 * STRETCHES ONLY: a liner no cut touches yields nothing, so its own solid outline is never
 * doubled.
 *
 * - **OD**: the union of every notch's span clipped to the liner (a nested cut adds nothing its
 *   parent did not already erase), split over the liner's own [UndercutLinerOd.surface] so each
 *   stretch sits at that seg's Ø — the shoulder OD over a shoulder — and a merged stretch that runs
 *   across a shoulder step retraces the step as a [LinerOutlineEdge.STEP].
 * - **End faces**: a notch running ACROSS a liner edge (past it by more than [epsMm] on both
 *   sides) erases that face from the deepest such notch's DRAWN floor up to the liner's surface at
 *   that end (the shoulder OD on a shouldered end); the face is retraced from the higher of that
 *   floor and the stock's Ø just outside the edge (the step the face originally stood on, read off
 *   [segs]'s outer envelope). A notch merely ending at the edge draws its own section face there
 *   and erases nothing of the liner's.
 */
internal fun linerOutlineSegments(
    liner: UndercutLinerOd,
    notches: List<UndercutNotch>,
    segs: List<SurfaceSeg>,
    epsMm: Float = UNDERCUT_TOUCH_EPS_MM,
): List<LinerOutlineSeg> {
    if (liner.endMm <= liner.startMm || liner.odMm <= 0f) return emptyList()
    val surface = liner.surface.filter { it.endMm > it.startMm }.sortedBy { it.startMm }
        .ifEmpty { listOf(SurfaceSeg(liner.startMm, liner.endMm, liner.odMm, liner.odMm)) }
    fun segDia(s: SurfaceSeg) = max(s.diaStartMm, s.diaEndMm)
    val out = mutableListOf<LinerOutlineSeg>()

    // Merged erased stretches of the liner's span.
    val merged = mutableListOf<Pair<Float, Float>>()
    notches
        .map { max(it.startMm, liner.startMm) to min(it.endMm, liner.endMm) }
        .filter { (a, b) -> b - a > epsMm }
        .sortedBy { it.first }
        .forEach { (a, b) ->
            val last = merged.lastOrNull()
            if (last != null && a <= last.second + epsMm) merged[merged.lastIndex] = last.first to max(last.second, b)
            else merged += a to b
        }

    for ((a, b) in merged) {
        surface.forEach { s ->
            val x0 = max(a, s.startMm)
            val x1 = min(b, s.endMm)
            if (x1 - x0 > epsMm) out += LinerOutlineSeg(LinerOutlineEdge.SURFACE, x0, x1, segDia(s), segDia(s))
        }
        for (i in 0 until surface.size - 1) {
            val x = surface[i].endMm
            val d0 = segDia(surface[i])
            val d1 = segDia(surface[i + 1])
            if (abs(d1 - d0) > epsMm && a < x - epsMm && b > x + epsMm) {
                out += LinerOutlineSeg(LinerOutlineEdge.STEP, x, x, min(d0, d1), max(d0, d1))
            }
        }
    }

    fun face(edgeMm: Float, outsideMm: Float, topDiaMm: Float, kind: LinerOutlineEdge) {
        val crossing = notches.filter { it.startMm < edgeMm - epsMm && it.endMm > edgeMm + epsMm }
        if (crossing.isEmpty()) return
        val floor = crossing.minOf { it.floorDiaMm }
        val seat = outerDiaAt(segs, outsideMm)
        val bottom = max(floor, seat)
        if (bottom < topDiaMm - epsMm) {
            out += LinerOutlineSeg(kind, edgeMm, edgeMm, bottom, topDiaMm)
        }
    }
    face(liner.startMm, liner.startMm - LINER_OUTLINE_SEAT_PROBE_MM, segDia(surface.first()), LinerOutlineEdge.AFT_FACE)
    face(liner.endMm, liner.endMm + LINER_OUTLINE_SEAT_PROBE_MM, segDia(surface.last()), LinerOutlineEdge.FWD_FACE)
    return out
}

/** How far outside a liner edge its seat Ø is read — clear of the face, inside the neighbour. */
internal const val LINER_OUTLINE_SEAT_PROBE_MM = 0.5f
