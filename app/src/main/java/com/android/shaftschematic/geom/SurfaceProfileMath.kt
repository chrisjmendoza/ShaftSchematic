// file: app/src/main/java/com/android/shaftschematic/geom/SurfaceProfileMath.kt
package com.android.shaftschematic.geom

import kotlin.math.max
import kotlin.math.min

/**
 * Outer-surface profile math — the local shaft surface as a piecewise-linear Ø function
 * of shaft-space x, and the undercut **notch** geometry cut against it.
 *
 * An undercut is not tied to a component: its span may cross a liner edge, a body Ø
 * step, or run onto a taper. The notch renderers (canvas overlay and PDF — the two must
 * stay in lockstep) therefore need the *actual* local outer surface over the span, not
 * one component's Ø. This file computes it from a neutral segment list so `geom` stays
 * free of `ui`/`pdf` imports:
 *
 * - [SurfaceSeg] — one component's outer surface contribution, built at the call site
 *   from resolved components (bodies/liners/threads constant Ø, tapers linear).
 * - [buildSurfaceEnvelope] — the upper envelope (max Ø wins where segments overlap:
 *   a liner over a body IS the surface there), as linear pieces.
 * - [notchProfiles] — the drawable notch region(s) for an undercut span + floor Ø,
 *   each with its surface polyline (including step points at e.g. a liner edge, so a
 *   notch crossing the edge shows the taller shoulder on the liner side).
 *
 * Everything is pure and unit-tested on a plain JVM (`SurfaceProfileMathTest`).
 */

private const val SURFACE_EPS_MM = 1e-3f

/**
 * One component's outer-surface contribution: linear Ø from [diaStartMm] at [startMm] to
 * [diaEndMm] at [endMm]. Constant-Ø components use the same value at both ends.
 */
data class SurfaceSeg(
    val startMm: Float,
    val endMm: Float,
    val diaStartMm: Float,
    val diaEndMm: Float,
) {
    /** Ø at [xMm], linearly interpolated; callers clamp [xMm] into the seg's span. */
    fun diaAt(xMm: Float): Float {
        val len = endMm - startMm
        if (len <= SURFACE_EPS_MM) return max(diaStartMm, diaEndMm)
        val t = ((xMm - startMm) / len).coerceIn(0f, 1f)
        return diaStartMm + t * (diaEndMm - diaStartMm)
    }
}

/**
 * One linear piece of the outer-surface envelope: Ø runs [dia0Mm] → [dia1Mm] over
 * `[x0Mm, x1Mm]`. Pieces from [buildSurfaceEnvelope] are sorted aft → fwd and
 * non-overlapping; adjacent pieces sharing an x with different Ø values encode a
 * surface **step** (a liner end face, a body Ø change).
 */
data class EnvelopePiece(val x0Mm: Float, val x1Mm: Float, val dia0Mm: Float, val dia1Mm: Float) {
    fun diaAt(xMm: Float): Float {
        val len = x1Mm - x0Mm
        if (len <= SURFACE_EPS_MM) return max(dia0Mm, dia1Mm)
        val t = ((xMm - x0Mm) / len).coerceIn(0f, 1f)
        return dia0Mm + t * (dia1Mm - dia0Mm)
    }
}

/** Outer Ø at [xMm]: the max over all segs covering it, or `0` where nothing covers. */
fun outerDiaAt(segs: List<SurfaceSeg>, xMm: Float): Float =
    segs.filter { xMm >= it.startMm - SURFACE_EPS_MM && xMm <= it.endMm + SURFACE_EPS_MM }
        .maxOfOrNull { it.diaAt(xMm) } ?: 0f

/**
 * The outer-surface envelope over `[x0Mm, x1Mm]`: where segments overlap, the largest Ø
 * wins (the outermost material is the surface). Returns sorted, non-overlapping linear
 * pieces covering only the x ranges some segment covers — uncovered gaps produce no
 * piece. Within any returned piece the winning segment is unique, so the piece is
 * exactly linear: candidate boundaries are every clipped segment edge plus every
 * pairwise crossing of overlapping linear Ø functions.
 */
fun buildSurfaceEnvelope(segs: List<SurfaceSeg>, x0Mm: Float, x1Mm: Float): List<EnvelopePiece> {
    if (x1Mm - x0Mm <= SURFACE_EPS_MM) return emptyList()
    val clipped = segs.mapNotNull { s ->
        val a = max(s.startMm, x0Mm)
        val b = min(s.endMm, x1Mm)
        if (b - a <= SURFACE_EPS_MM) null else SurfaceSeg(a, b, s.diaAt(a), s.diaAt(b))
    }
    if (clipped.isEmpty()) return emptyList()

    // Cut positions: every clipped segment edge, deduped within epsilon.
    val cuts = mutableListOf<Float>()
    for (s in clipped) {
        cuts += s.startMm
        cuts += s.endMm
    }
    cuts.sort()
    val xs = mutableListOf<Float>()
    for (c in cuts) if (xs.isEmpty() || c - xs.last() > SURFACE_EPS_MM) xs += c

    val pieces = mutableListOf<EnvelopePiece>()
    for (i in 0 until xs.size - 1) {
        val xa = xs[i]
        val xb = xs[i + 1]
        if (xb - xa <= SURFACE_EPS_MM) continue
        val covering = clipped.filter { it.startMm <= xa + SURFACE_EPS_MM && it.endMm >= xb - SURFACE_EPS_MM }
        if (covering.isEmpty()) continue

        // Refine by pairwise crossings so each refined interval has a single winner.
        val refined = mutableListOf(xa, xb)
        for (p in covering.indices) for (q in p + 1 until covering.size) {
            val f = covering[p]
            val g = covering[q]
            val da = f.diaAt(xa) - g.diaAt(xa)
            val db = f.diaAt(xb) - g.diaAt(xb)
            if (da * db < 0f) {
                val xc = xa + (xb - xa) * (da / (da - db))
                if (xc > xa + SURFACE_EPS_MM && xc < xb - SURFACE_EPS_MM) refined += xc
            }
        }
        refined.sort()
        for (j in 0 until refined.size - 1) {
            val ra = refined[j]
            val rb = refined[j + 1]
            if (rb - ra <= SURFACE_EPS_MM) continue
            val mid = (ra + rb) / 2f
            val best = covering.maxByOrNull { it.diaAt(mid) } ?: continue
            pieces += EnvelopePiece(ra, rb, best.diaAt(ra), best.diaAt(rb))
        }
    }
    return pieces
}

/** Smallest envelope Ø over `[x0Mm, x1Mm]` (piecewise linear ⇒ extrema at piece ends); `0` when nothing covers. */
fun minOuterDiaOver(segs: List<SurfaceSeg>, x0Mm: Float, x1Mm: Float): Float {
    val env = buildSurfaceEnvelope(segs, x0Mm, x1Mm)
    if (env.isEmpty()) return 0f
    return env.minOf { min(it.dia0Mm, it.dia1Mm) }
}

/** Largest envelope Ø over `[x0Mm, x1Mm]`; `0` when nothing covers. */
fun maxOuterDiaOver(segs: List<SurfaceSeg>, x0Mm: Float, x1Mm: Float): Float {
    val env = buildSurfaceEnvelope(segs, x0Mm, x1Mm)
    if (env.isEmpty()) return 0f
    return env.maxOf { max(it.dia0Mm, it.dia1Mm) }
}

/** One vertex of a notch's surface polyline. Two consecutive points sharing an x encode a surface step face. */
data class SurfacePoint(val xMm: Float, val diaMm: Float)

/**
 * One drawable notch region: over `[startMm, endMm]` the local surface Ø strictly
 * exceeds [floorDiaMm], so material is removed between the surface and the floor.
 *
 * [surface] is the surface polyline across the region, aft → fwd, including both
 * endpoints, every envelope breakpoint, and duplicated-x step points. Renderers draw:
 * - the **void fill** — the closed polygon `surface + (endMm, floor) + (startMm, floor)`
 *   (mirrored about the centerline for the bottom half), painted background/white so the
 *   original surface stroke and fills are erased inside the cut. Nothing redraws over the
 *   mouth — the cut is OPEN at the surface, a step in the silhouette;
 * - the **outline** — a full-height **section face** at [startMm] and [endMm] (one
 *   vertical from top surface to bottom surface, like any machined diameter step, drawn
 *   only where that end's surface stands at least [NOTCH_FACE_MIN_STEP_PX] above the
 *   floor at draw scale), and the floor lines across the span, mirrored. Each cut thereby
 *   reads as its own reduced-Ø rectangle section between two faces — the hand-sketch
 *   convention. A region boundary caused by the surface *meeting* the floor (a taper
 *   running down to the undercut Ø) draws no face there.
 */
data class NotchProfile(
    val startMm: Float,
    val endMm: Float,
    val floorDiaMm: Float,
    val surface: List<SurfacePoint>,
)

/**
 * Minimum drawn step (px) between a notch region's end surface and its floor for that end
 * to draw a section face — below this the surface has effectively met the floor (a taper
 * running down into the cut) and a full-height face line would be spurious. Px-space so
 * every draw site (canvas overlays, PDF, SVG preview) applies the identical visibility
 * rule at its own scale.
 */
const val NOTCH_FACE_MIN_STEP_PX = 0.5f

/**
 * Alpha (black over the white sheet) filling an undercut section's remaining core — HALF
 * the liner shade's 40/255, so the cut span reads one step lighter than the liner around
 * it (on-device request: contrast between the section and the liner). The core is erased
 * to white first, so this is the section's absolute tone, not a darkening overlay. Shared
 * by every draw site (canvas overlays, PDF, SVG preview) so screen and print agree.
 */
const val UNDERCUT_SECTION_FILL_ALPHA = 20f / 255f

/**
 * Compute the drawable notch region(s) for an undercut spanning `[x0Mm, x1Mm]` with
 * floor Ø [floorDiaMm]. Portions where the surface Ø is at or below the floor (no
 * material to remove — e.g. the span runs off a liner onto a smaller bare shaft, or an
 * implausible entered Ø) yield no region: nothing is drawn there, and callers surface
 * the condition as a non-blocking warning, never a blocked or rewritten value.
 * Uncovered gaps in the surface likewise split regions.
 */
fun notchProfiles(
    segs: List<SurfaceSeg>,
    x0Mm: Float,
    x1Mm: Float,
    floorDiaMm: Float,
): List<NotchProfile> {
    val env = buildSurfaceEnvelope(segs, x0Mm, x1Mm)
    if (env.isEmpty()) return emptyList()

    val profiles = mutableListOf<NotchProfile>()
    var run: MutableList<SurfacePoint>? = null

    fun endRun() {
        val r = run
        if (r != null && r.size >= 2 && r.last().xMm - r.first().xMm > SURFACE_EPS_MM) {
            profiles += NotchProfile(r.first().xMm, r.last().xMm, floorDiaMm, r.toList())
        }
        run = null
    }

    for (piece in env) {
        val d0 = piece.dia0Mm - floorDiaMm
        val d1 = piece.dia1Mm - floorDiaMm

        // Drawable sub-interval of this piece (where surface Ø > floor Ø).
        val sub: Triple<Float, Float, Pair<Float, Float>>? = when {
            d0 > SURFACE_EPS_MM && d1 > SURFACE_EPS_MM ->
                Triple(piece.x0Mm, piece.x1Mm, piece.dia0Mm to piece.dia1Mm)
            d0 <= SURFACE_EPS_MM && d1 <= SURFACE_EPS_MM -> null
            else -> {
                val xc = piece.x0Mm + (piece.x1Mm - piece.x0Mm) * (d0 / (d0 - d1))
                if (d0 > SURFACE_EPS_MM) Triple(piece.x0Mm, xc, piece.dia0Mm to floorDiaMm)
                else Triple(xc, piece.x1Mm, floorDiaMm to piece.dia1Mm)
            }
        }
        if (sub == null) {
            endRun()
            continue
        }
        val (sa, sb, dias) = sub
        val (diaA, diaB) = dias

        val current = run
        val continues = current != null && sa - current.last().xMm <= SURFACE_EPS_MM
        if (continues && current != null) {
            // Step face: same x, different Ø — keep both points so the shoulder draws.
            if (kotlin.math.abs(current.last().diaMm - diaA) > SURFACE_EPS_MM) {
                current += SurfacePoint(sa, diaA)
            }
            current += SurfacePoint(sb, diaB)
        } else {
            endRun()
            run = mutableListOf(SurfacePoint(sa, diaA), SurfacePoint(sb, diaB))
        }
    }
    endRun()
    return profiles
}
