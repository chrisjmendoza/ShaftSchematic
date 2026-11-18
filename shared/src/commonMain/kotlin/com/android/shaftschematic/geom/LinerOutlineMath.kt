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
 * mirror each piece about the centreline, except a [LinerOutlineEdge.FULL_FACE], which already
 * spans it as one line.
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

    /**
     * An end face a cut ran across, ONE full-height line at `x0Mm` (= `x1Mm`) from the top OD to
     * the bottom OD (`dia0Mm` = `dia1Mm` = the liner's surface Ø at that end), straight through
     * the body cross-section — never mirrored into two pieces.
     */
    FULL_FACE,
}

/**
 * The liner outline's line pattern, in sheet points: a PHANTOM line — one long dash, two short —
 * the drafting convention for material that has been machined away (on-device direction: a dotted
 * line already means a hidden edge on a schematic). On, off, on, off… from a piece's start, so
 * every piece opens on its long dash. ONE pattern behind the printed sheet and the canvases, which
 * scale it by their own drawn size ([linerOutlinePattern]).
 */
val LINER_OUTLINE_PHANTOM_PT: List<Float> = listOf(7f, 2f, 1.5f, 2f, 1.5f, 2f)

/** [LINER_OUTLINE_PHANTOM_PT] at [scale] drawn units per sheet point. */
fun linerOutlinePattern(scale: Float = 1f): FloatArray =
    FloatArray(LINER_OUTLINE_PHANTOM_PT.size) { LINER_OUTLINE_PHANTOM_PT[it] * scale }

/**
 * What the sheet's legend calls the phantom retrace. "Shaft", not "liner": the same line also
 * retraces a blend and the body surface beside a liner that a cut took away (on-device request).
 */
const val LINER_OUTLINE_LEGEND = "Original shaft surface"

/**
 * One dotted piece. A [LinerOutlineEdge.SURFACE] piece runs `x0Mm → x1Mm` at `dia0Mm`
 * (`== dia1Mm`); a [LinerOutlineEdge.STEP] stands at `x0Mm` from `dia0Mm` to `dia1Mm`, mirrored
 * about the centreline; a [LinerOutlineEdge.FULL_FACE] stands at `x0Mm` across the whole Ø
 * `dia1Mm`, top to bottom, as one line.
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
 *   sides) erases that face, and the face is retraced as ONE [LinerOutlineEdge.FULL_FACE] — the
 *   liner's surface Ø at that end (the shoulder OD on a shouldered end), top OD to bottom OD,
 *   straight through the body cross-section — so the liner's actual end placement reads at a
 *   glance (on-device request). Neither the cut floors nor the stock's Ø outside the edge bound
 *   it: trimmed to the seat, a face over a seat a hair under the OD would be a few pixels tall
 *   and read as missing (on-device report). Two cuts BUTTED at the edge, one each side, erase the
 *   face just the same — their shared edge draws only the step between their two floors — so it
 *   is retraced too (on-device report: the stub between the liner OD and the higher floor drew as
 *   a faint sliver, not the phantom line). A notch merely ending at the edge with nothing cut on
 *   the other side draws its own section face there and erases nothing of the liner's, so no face
 *   is retraced; nor is one at an edge no cut reaches, where the real face still stands.
 */
fun linerOutlineSegments(
    liner: UndercutLinerOd,
    notches: List<UndercutNotch>,
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

    fun face(edgeMm: Float, odMm: Float) {
        val crossed = notches.any { it.startMm < edgeMm - epsMm && it.endMm > edgeMm + epsMm }
        val butted = notches.any { abs(it.endMm - edgeMm) <= epsMm && it.startMm < edgeMm - epsMm } &&
            notches.any { abs(it.startMm - edgeMm) <= epsMm && it.endMm > edgeMm + epsMm }
        if (crossed || butted) {
            out += LinerOutlineSeg(LinerOutlineEdge.FULL_FACE, edgeMm, edgeMm, odMm, odMm)
        }
    }
    face(liner.startMm, segDia(surface.first()))
    face(liner.endMm, segDia(surface.last()))
    return out
}
