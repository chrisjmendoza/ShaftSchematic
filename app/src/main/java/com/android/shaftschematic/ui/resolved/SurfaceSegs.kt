// file: app/src/main/java/com/android/shaftschematic/ui/resolved/SurfaceSegs.kt
package com.android.shaftschematic.ui.resolved

import com.android.shaftschematic.geom.SurfaceSeg
import com.android.shaftschematic.geom.blendPolyline
import com.android.shaftschematic.model.LinerAuthoredReference

/**
 * Map resolved components to their outer-surface contributions
 * (`geom/SurfaceProfileMath.kt`'s [SurfaceSeg]) for surface-envelope math — the single
 * shared mapping used by every undercut draw site (canvas overlay and PDF), so the two
 * compute the identical local surface by construction.
 *
 * - Bodies and liners contribute a constant Ø; where a liner overlaps a body the
 *   envelope's max-wins rule makes the liner the surface, matching the drawn profile.
 * - Tapers contribute their linear Ø run.
 * - Threads contribute their major-Ø envelope (the drawn hatched outline).
 * - Coupler bolt slots are radial cutouts, not surface material — skipped.
 *
 * [blends] refines a blended body face into its curve instead of a square step, so a cut or a
 * reading that lands in the transition sees the diameter actually there. The curve is sampled
 * at its TRUE mm span — the drawn-width floor that keeps a short blend visible on paper is a
 * drawing exaggeration and must never reach geometry. Passing none (the default) yields square
 * faces, which is what [bodyBlends] itself needs when it looks up what each face steps to.
 */
fun surfaceSegsFrom(
    components: List<ResolvedComponent>,
    blends: List<BodyBlend> = emptyList(),
): List<SurfaceSeg> =
    components.flatMap { c ->
        when (c) {
            is ResolvedBody -> blendedBodySegs(c, blends)
            is ResolvedLiner -> listOf(SurfaceSeg(c.startMmPhysical, c.endMmPhysical, c.odMm, c.odMm))
            is ResolvedTaper -> listOf(SurfaceSeg(c.startMmPhysical, c.endMmPhysical, c.startDiaMm, c.endDiaMm))
            is ResolvedThread -> listOf(SurfaceSeg(c.startMmPhysical, c.endMmPhysical, c.majorDiaMm, c.majorDiaMm))
            is ResolvedCouplerBoltSlot -> emptyList()
        }
    }.filter { it.endMm > it.startMm && (it.diaStartMm > 0f || it.diaEndMm > 0f) }

/**
 * One body's surface: the flat run, with each blended face replaced by the sampled curve.
 * An unblended body yields the single constant-Ø seg it always did.
 */
private fun blendedBodySegs(b: ResolvedBody, blends: List<BodyBlend>): List<SurfaceSeg> {
    val mine = blends.filter { it.bodyId == b.id }
    if (mine.isEmpty()) return listOf(SurfaceSeg(b.startMmPhysical, b.endMmPhysical, b.diaMm, b.diaMm))

    val aft = mine.firstOrNull { it.end == LinerAuthoredReference.AFT }
    val fwd = mine.firstOrNull { it.end == LinerAuthoredReference.FWD }
    var flat0 = b.startMmPhysical
    var flat1 = b.endMmPhysical
    val out = mutableListOf<SurfaceSeg>()

    fun addCurve(x0: Float, x1: Float, dia0: Float, dia1: Float, blend: BodyBlend) {
        val pts = blendPolyline(x0, x1, dia0 / 2f, dia1 / 2f, blend.profile)
        for (i in 1 until pts.size) {
            out += SurfaceSeg(pts[i - 1].xMm, pts[i].xMm, pts[i - 1].diaMm, pts[i].diaMm)
        }
    }

    if (aft != null) {
        val end = (b.startMmPhysical + aft.lengthMm).coerceAtMost(b.endMmPhysical)
        if (end > b.startMmPhysical) { addCurve(b.startMmPhysical, end, aft.neighbourDiaMm, b.diaMm, aft); flat0 = end }
    }
    if (fwd != null) {
        val start = (b.endMmPhysical - fwd.lengthMm).coerceAtLeast(flat0)
        if (b.endMmPhysical > start) { addCurve(start, b.endMmPhysical, b.diaMm, fwd.neighbourDiaMm, fwd); flat1 = start }
    }
    if (flat1 > flat0) out += SurfaceSeg(flat0, flat1, b.diaMm, b.diaMm)
    return out
}
