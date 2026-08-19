// file: app/src/main/java/com/android/shaftschematic/ui/resolved/BodyBlends.kt
package com.android.shaftschematic.ui.resolved

import com.android.shaftschematic.geom.BLEND_CURVE_STEPS
import com.android.shaftschematic.geom.SurfaceSeg
import com.android.shaftschematic.geom.blendRadiusFrac
import com.android.shaftschematic.geom.easeAftFrac
import com.android.shaftschematic.geom.easeFwdFrac
import com.android.shaftschematic.geom.drawnBlendWidthPx
import com.android.shaftschematic.geom.outerDiaAt
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.BlendProfile
import com.android.shaftschematic.model.LinerAuthoredReference
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.blendMmOn
import kotlin.math.abs

/**
 * BodyBlends — the derived geometry behind a body's blended face, shared by every draw site.
 *
 * A blend is machined INWARD from one face of the body that carries it: the curve leaves the
 * neighbouring diameter at the face and reaches the body's own diameter [Body.blendAftMm] /
 * [Body.blendFwdMm] further in. Keeping it inside the owning body is what makes it safe —
 * no other component's span moves, drawn or stored, so the golden rule holds by construction
 * and there is no ordering dependency between two neighbouring blends.
 *
 * Nothing here is stored. The blend's diameters are DERIVED from whatever sits across the
 * face, so re-diametering a neighbour re-curves the blend automatically; derived values are
 * exactly what the golden rule allows to move.
 */

private const val BLEND_EPS_MM = 1e-3f

/**
 * One drawable blend: the curve runs from [neighbourDiaMm] at [faceMm] to [bodyDiaMm] at
 * [lengthMm] inward. [end] says which way "inward" points, and [bodyId] is the RESOLVED id
 * of the run it belongs to (a fragment id when the body is split).
 */
data class BodyBlend(
    val bodyId: String,
    val end: LinerAuthoredReference,
    val faceMm: Float,
    val lengthMm: Float,
    val bodyDiaMm: Float,
    val neighbourDiaMm: Float,
    val profile: BlendProfile,
)

/** A blend's drawn span, already floored and clamped, ready to hand to `blendPolyline`. */
data class BlendDrawSpan(
    val xAftPx: Float,
    val xFwdPx: Float,
    val diaAtAftMm: Float,
    val diaAtFwdMm: Float,
)

/**
 * Every drawable blend on the shaft.
 *
 * Only EXPLICIT bodies carry blends — an auto-body is a derived gap with no card fields to
 * set one on, and promoting it to an explicit body is the documented way to gain them.
 *
 * A blend is dropped (not drawn, never an error) when there is no step to blend: nothing
 * across the face, or a neighbour at the same diameter. Liners are excluded from the
 * neighbour lookup — a liner is a sleeve OVER the shaft, not a diameter the shaft steps to.
 */
fun bodyBlends(spec: ShaftSpec, components: List<ResolvedComponent>): List<BodyBlend> {
    val blended = spec.bodies.filter { it.blendAftMm > 0f || it.blendFwdMm > 0f }
    if (blended.isEmpty()) return emptyList()

    // Neighbour diameters come off the shaft's own surface, so a blend follows a taper's
    // local Ø as readily as a body's. Liners are sleeves, not steps, so they are left out.
    val segs: List<SurfaceSeg> = surfaceSegsFrom(components.filterNot { it is ResolvedLiner })

    val runsByBase = components
        .filterIsInstance<ResolvedBody>()
        .filter { it.source == ResolvedComponentSource.EXPLICIT }
        .groupBy { resolvedBodyBaseId(it.id) }

    return buildList {
        for (b in blended) {
            val runs = runsByBase[b.id] ?: continue
            for (end in LinerAuthoredReference.values()) {
                val stored = b.blendMmOn(end)
                if (stored <= 0f) continue

                // A split body draws as several runs; the blend belongs to the run that
                // actually carries the stored face, never to an interior fragment edge.
                val faceMm = if (end == LinerAuthoredReference.AFT) b.startFromAftMm
                             else b.startFromAftMm + b.lengthMm
                val run = runs.firstOrNull { r ->
                    val edge = if (end == LinerAuthoredReference.AFT) r.startMmPhysical else r.endMmPhysical
                    abs(edge - faceMm) <= BLEND_EPS_MM
                } ?: continue

                val runLen = run.endMmPhysical - run.startMmPhysical
                if (runLen <= BLEND_EPS_MM) continue

                // Sample just OUTSIDE the face: that is the diameter the curve leaves from.
                val probeMm = if (end == LinerAuthoredReference.AFT) faceMm - BLEND_EPS_MM * 10f
                              else faceMm + BLEND_EPS_MM * 10f
                val neighbourDia = outerDiaAt(segs, probeMm)
                if (neighbourDia <= 0f) continue
                if (abs(neighbourDia - run.diaMm) <= BLEND_EPS_MM) continue

                add(
                    BodyBlend(
                        bodyId = run.id,
                        end = end,
                        faceMm = faceMm,
                        // Clamping the DRAWN curve is not rewriting what was typed.
                        lengthMm = stored.coerceAtMost(runLen),
                        bodyDiaMm = run.diaMm,
                        neighbourDiaMm = neighbourDia,
                        profile = b.blendProfile,
                    )
                )
            }
        }
    }
}

/**
 * This blend's drawn span under an arbitrary x mapping, with the visibility floor applied.
 *
 * Both draw sites call this so the schematic canvas and the PDF place the identical curve;
 * [xAt] is each site's own mm → drawn-units mapping (linear on the canvas, the compressed
 * piecewise map on a sheet), and [minWidthPx] its own floor.
 */
fun BodyBlend.drawSpan(
    runStartMm: Float,
    runEndMm: Float,
    xAt: (Float) -> Float,
    minWidthPx: Float,
): BlendDrawSpan {
    val xFace = xAt(faceMm)
    val hostWidth = abs(xAt(runEndMm) - xAt(runStartMm))
    val inwardMm = if (end == LinerAuthoredReference.AFT) faceMm + lengthMm else faceMm - lengthMm
    val trueWidth = abs(xAt(inwardMm) - xFace)
    val w = drawnBlendWidthPx(trueWidth, hostWidth, minWidthPx)

    return if (end == LinerAuthoredReference.AFT) {
        BlendDrawSpan(xAftPx = xFace, xFwdPx = xFace + w, diaAtAftMm = neighbourDiaMm, diaAtFwdMm = bodyDiaMm)
    } else {
        BlendDrawSpan(xAftPx = xFace - w, xFwdPx = xFace, diaAtAftMm = bodyDiaMm, diaAtFwdMm = neighbourDiaMm)
    }
}

/** One vertex of a body's drawn silhouette edge: x and RADIUS, both in drawn units. */
data class BodyEdgePoint(val xPx: Float, val rPx: Float)

/**
 * A body run's drawn silhouette, split into the parts each draw site handles differently.
 *
 * The blend is machined out of the body, so the run's FLAT span shrinks by the drawn blend
 * width at each blended face and the curve occupies what it gave up. The end cap at a
 * blended face stands at the NEIGHBOUR's radius — that is where the curve arrives, and it
 * makes the cap coincide with the neighbouring component's own face line instead of leaving
 * a stray vertical stroke partway along the body.
 *
 * The flat span keeps the run's existing treatment untouched (S-break compression included),
 * which is why this is a decomposition rather than one polyline.
 */
data class BodyDrawEdges(
    val aftCurve: List<BodyEdgePoint>,
    val fwdCurve: List<BodyEdgePoint>,
    val flatX0: Float,
    val flatX1: Float,
    val flatR: Float,
    val capAftR: Float,
    val capFwdR: Float,
) {
    val hasBlend: Boolean get() = aftCurve.isNotEmpty() || fwdCurve.isNotEmpty()
}

/**
 * Decompose a body run into its drawn edges under an arbitrary x mapping.
 *
 * [xAt] maps shaft mm → drawn units (linear on the preview canvas, the compressed piecewise
 * map on a sheet) and [rAt] maps a diameter in mm → a drawn radius. Both draw sites call
 * this, so the canvas and the PDF place the identical curve by construction.
 *
 * A blend whose floored width would leave no flat span is dropped rather than allowed to
 * invert the run.
 */
fun bodyDrawEdges(
    runId: String,
    runStartMm: Float,
    runEndMm: Float,
    runDiaMm: Float,
    blends: List<BodyBlend>,
    xAt: (Float) -> Float,
    rAt: (Float) -> Float,
    minWidthPx: Float,
    steps: Int = BLEND_CURVE_STEPS,
): BodyDrawEdges {
    val x0 = xAt(runStartMm)
    val x1 = xAt(runEndMm)
    val r = rAt(runDiaMm)
    val mine = blends.filter { it.bodyId == runId }
    val aft = mine.firstOrNull { it.end == LinerAuthoredReference.AFT }
    val fwd = mine.firstOrNull { it.end == LinerAuthoredReference.FWD }

    val aftSpan = aft?.drawSpan(runStartMm, runEndMm, xAt, minWidthPx)
    val fwdSpan = fwd?.drawSpan(runStartMm, runEndMm, xAt, minWidthPx)

    var flatX0 = aftSpan?.xFwdPx ?: x0
    var flatX1 = fwdSpan?.xAftPx ?: x1
    // Two blends on a short run can meet; give the flat span back rather than invert it.
    if (flatX1 <= flatX0) { flatX0 = x0; flatX1 = x1; return BodyDrawEdges(emptyList(), emptyList(), x0, x1, r, r, r) }

    return BodyDrawEdges(
        aftCurve = aftSpan?.let { curvePoints(it, aft.profile, rAt, steps) } ?: emptyList(),
        fwdCurve = fwdSpan?.let { curvePoints(it, fwd.profile, rAt, steps) } ?: emptyList(),
        flatX0 = flatX0,
        flatX1 = flatX1,
        flatR = r,
        capAftR = aft?.let { rAt(it.neighbourDiaMm) } ?: r,
        capFwdR = fwd?.let { rAt(it.neighbourDiaMm) } ?: r,
    )
}

/**
 * Sample a blend's curve directly in drawn units.
 *
 * The mm-space [com.android.shaftschematic.geom.blendPolyline] serves the surface envelope;
 * this one serves the draw sites, where the span has already taken its visibility floor and
 * no longer corresponds to a true mm span. Both read the same [blendRadiusFrac], so the two
 * describe the same curve.
 */
private fun curvePoints(
    span: BlendDrawSpan,
    profile: BlendProfile,
    rAt: (Float) -> Float,
    steps: Int,
): List<BodyEdgePoint> {
    val r0 = rAt(span.diaAtAftMm)
    val r1 = rAt(span.diaAtFwdMm)
    val largerAtAft = r0 > r1
    val a = profile.easeAftFrac(largerAtAft)
    val b = profile.easeFwdFrac(largerAtAft)
    val w = span.xFwdPx - span.xAftPx
    return (0..steps).map { i ->
        val t = i.toFloat() / steps
        BodyEdgePoint(span.xAftPx + w * t, r0 + (r1 - r0) * blendRadiusFrac(t, a, b))
    }
}
