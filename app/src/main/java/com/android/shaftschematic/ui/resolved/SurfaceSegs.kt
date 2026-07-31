// file: app/src/main/java/com/android/shaftschematic/ui/resolved/SurfaceSegs.kt
package com.android.shaftschematic.ui.resolved

import com.android.shaftschematic.geom.SurfaceSeg

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
 */
fun surfaceSegsFrom(components: List<ResolvedComponent>): List<SurfaceSeg> =
    components.mapNotNull { c ->
        when (c) {
            is ResolvedBody -> SurfaceSeg(c.startMmPhysical, c.endMmPhysical, c.diaMm, c.diaMm)
            is ResolvedLiner -> SurfaceSeg(c.startMmPhysical, c.endMmPhysical, c.odMm, c.odMm)
            is ResolvedTaper -> SurfaceSeg(c.startMmPhysical, c.endMmPhysical, c.startDiaMm, c.endDiaMm)
            is ResolvedThread -> SurfaceSeg(c.startMmPhysical, c.endMmPhysical, c.majorDiaMm, c.majorDiaMm)
            is ResolvedCouplerBoltSlot -> null
        }
    }.filter { it.endMm > it.startMm && (it.diaStartMm > 0f || it.diaEndMm > 0f) }
