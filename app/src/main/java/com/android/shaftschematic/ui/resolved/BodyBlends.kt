// file: app/src/main/java/com/android/shaftschematic/ui/resolved/BodyBlends.kt
package com.android.shaftschematic.ui.resolved

import com.android.shaftschematic.geom.BLEND_CURVE_STEPS
import com.android.shaftschematic.geom.SurfaceSeg
import com.android.shaftschematic.geom.blendRadiusFrac
import com.android.shaftschematic.geom.sealGrooveFracs
import com.android.shaftschematic.geom.sealNotchGeom
import com.android.shaftschematic.geom.easeAftFrac
import com.android.shaftschematic.geom.easeFwdFrac
import com.android.shaftschematic.geom.drawnBlendWidthPx
import com.android.shaftschematic.geom.outerDiaAt
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.BlendProfile
import com.android.shaftschematic.model.LinerAuthoredReference
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.autoBlendFor
import com.android.shaftschematic.model.blendMmOn
import com.android.shaftschematic.model.blendSealOn
import kotlin.math.abs
import kotlin.math.min

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
    /** Seal area: radius cuts drawn across the curve for the fiberglass to seat into. */
    val seal: Boolean = false,
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
 * Explicit bodies carry their blends as stored fields; auto spans carry them as shaft-space
 * anchors ([AutoBlend]), so a saved layout keeps its seal areas when the liners or the overall
 * length move under it. Both resolve to the same [BodyBlend] here, and every draw site is blind
 * to which kind it came from.
 *
 * A blend is dropped (not drawn, never an error) when there is no step to blend: nothing
 * across the face, or a neighbour at the same diameter.
 *
 * Liners are excluded from the ordinary neighbour lookup — a sleeve sitting over mid-body is
 * not a diameter the shaft steps to. The one exception is a face a liner butts directly
 * against, which is a real seal area: the shaft is cut down under the liner, but that seat is
 * covered by the liner and never drawn, and its true depth varies job to job (on-device: "the
 * size of the step can vary"). The blend there leaves from the MIDPOINT of the liner OD and
 * the body Ø — a derived visual cue, not a measurement, which is why nothing authors it. See
 * [seatDiaUnderLiner].
 */
fun bodyBlends(spec: ShaftSpec, components: List<ResolvedComponent>): List<BodyBlend> {
    val blended = spec.bodies.filter { it.blendAftMm > 0f || it.blendFwdMm > 0f }
    // Auto spans carry their blends as anchors, not as fields on a stored body, so the
    // early-out has to clear BOTH sources or a shaft with only bare-shaft seal areas
    // (no explicit body anywhere) returns before the auto pass runs.
    if (blended.isEmpty() && spec.autoBlends.isEmpty()) return emptyList()

    // Neighbour diameters come off the shaft's own surface, so a blend follows a taper's
    // local Ø as readily as a body's. Liners are sleeves, not steps, so they are left out.
    val segs: List<SurfaceSeg> = surfaceSegsFrom(components.filterNot { it is ResolvedLiner })

    val runsByBase = components
        .filterIsInstance<ResolvedBody>()
        .filter { it.source == ResolvedComponentSource.EXPLICIT }
        .groupBy { resolvedBodyBaseId(it.id) }

    val autoRuns = components
        .filterIsInstance<ResolvedBody>()
        .filter { it.source == ResolvedComponentSource.AUTO }

    return buildList {
        // Auto spans: the blend is anchored in shaft space, so the span that contains the
        // anchor wears it however the surrounding geometry has moved.
        for (run in autoRuns) {
            for (end in LinerAuthoredReference.values()) {
                val auto = spec.autoBlends.autoBlendFor(run.startMmPhysical, run.endMmPhysical, end)
                    ?: continue
                blendAt(components, segs, run, end, auto.lengthMm, auto.profile, auto.seal)
                    ?.let(::add)
            }
        }

        for (b in blended) {
            val runs = runsByBase[b.id] ?: continue
            for (end in LinerAuthoredReference.values()) {
                val stored = b.blendMmOn(end)
                if (stored <= 0f) continue

                // The face is the OUTER edge of the body's drawn extent, not its stored
                // position. A split body draws as several runs, so only the aft-most (or
                // fwd-most) one carries that face. An explicit body never absorbs the auto
                // gap beside it, so its drawn extent matches its stored span; a face that
                // meets a same-Ø surviving gap has no step and draws no blend there — the
                // step is the gap run's far face, which an [AutoBlend] anchor covers.
                val run = (
                    if (end == LinerAuthoredReference.AFT) runs.minByOrNull { it.startMmPhysical }
                    else runs.maxByOrNull { it.endMmPhysical }
                ) ?: continue
                val faceMm = if (end == LinerAuthoredReference.AFT) run.startMmPhysical
                             else run.endMmPhysical

                blendAt(components, segs, run, end, stored, b.blendProfile, b.blendSealOn(end))
                    ?.let(::add)
            }
        }
    }
}

/**
 * Resolve one face of one drawn run into a [BodyBlend], or null when there is no step to blend.
 *
 * Shared by the explicit and auto paths so the two can never disagree about what a face steps
 * to. The face is the run's own outer edge — its DRAWN extent (for an explicit body, its
 * stored span; body fragmentation still trims it).
 */
private fun blendAt(
    components: List<ResolvedComponent>,
    segs: List<SurfaceSeg>,
    run: ResolvedBody,
    end: LinerAuthoredReference,
    storedLengthMm: Float,
    profile: BlendProfile,
    seal: Boolean,
): BodyBlend? {
    if (storedLengthMm <= 0f) return null
    val runLen = run.endMmPhysical - run.startMmPhysical
    if (runLen <= BLEND_EPS_MM) return null
    val faceMm = if (end == LinerAuthoredReference.AFT) run.startMmPhysical else run.endMmPhysical

    // Sample just OUTSIDE the face: that is the diameter the curve leaves from.
    val probeMm = if (end == LinerAuthoredReference.AFT) faceMm - BLEND_EPS_MM * 10f
                  else faceMm + BLEND_EPS_MM * 10f
    // Shaft surface first (liners excluded); a liner butting the face is the seal-area case
    // and supplies a derived seat instead.
    val neighbourDia = outerDiaAt(segs, probeMm).takeIf { it > 0f }
        ?: seatDiaUnderLiner(components, probeMm, run.diaMm)
        ?: return null
    if (abs(neighbourDia - run.diaMm) <= BLEND_EPS_MM) return null

    return BodyBlend(
        bodyId = run.id,
        end = end,
        faceMm = faceMm,
        // Clamping the DRAWN curve is not rewriting what was typed.
        lengthMm = storedLengthMm.coerceAtMost(runLen),
        bodyDiaMm = run.diaMm,
        neighbourDiaMm = neighbourDia,
        profile = profile,
        seal = seal,
    )
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

/**
 * Derived seat diameter where a liner butts a body face — the MIDPOINT of the liner's OD and
 * the body's own Ø, or null when no liner covers [probeMm].
 *
 * The shaft really is cut down under a liner, but that seat is never drawn (the liner covers
 * it) and how far down it goes varies from job to job. Stepping the blend straight to the
 * liner OD would overstate the shoulder; running it to a seat nobody entered would be a made-up
 * measurement. Half-way reads as a shoulder without claiming a number, which is all a seal area
 * needs on a schematic. An under-liner seat authored as its own body is not consulted — it is
 * trimmed out of the drawing by `subtractBodiesAgainstNonBodies`, so there is nothing on the
 * sheet for the curve to arrive at.
 */
internal fun seatDiaUnderLiner(
    components: List<ResolvedComponent>,
    probeMm: Float,
    bodyDiaMm: Float,
): Float? {
    val liner = components
        .filterIsInstance<ResolvedLiner>()
        .filter { probeMm >= it.startMmPhysical - BLEND_EPS_MM && probeMm <= it.endMmPhysical + BLEND_EPS_MM }
        .maxByOrNull { it.odMm }
        ?: return null
    if (liner.odMm <= 0f) return null
    return (liner.odMm + bodyDiaMm) / 2f
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
    /**
     * Seal grooves on the aft curve: one (x, radius) per cut, in drawn units, radius at the
     * notch FLOOR — a draw site strokes `cy − r → cy + r` and the line lands exactly on the
     * bottoms of the two silhouette notches [curvePoints] cut for the same station.
     */
    val aftSeal: List<BodyEdgePoint> = emptyList(),
    /** Seal grooves on the fwd curve, same convention. */
    val fwdSeal: List<BodyEdgePoint> = emptyList(),
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
        aftCurve = aftSpan?.let { curvePoints(it, aft.profile, rAt, steps, seal = aft.seal) } ?: emptyList(),
        fwdCurve = fwdSpan?.let { curvePoints(it, fwd.profile, rAt, steps, seal = fwd.seal) } ?: emptyList(),
        flatX0 = flatX0,
        flatX1 = flatX1,
        flatR = r,
        capAftR = aft?.let { rAt(it.neighbourDiaMm) } ?: r,
        capFwdR = fwd?.let { rAt(it.neighbourDiaMm) } ?: r,
        aftSeal = if (aft?.seal == true) sealGrooveLines(aftSpan!!, aft.profile, rAt) else emptyList(),
        fwdSeal = if (fwd?.seal == true) sealGrooveLines(fwdSpan!!, fwd.profile, rAt) else emptyList(),
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
    seal: Boolean = false,
): List<BodyEdgePoint> {
    val r0 = rAt(span.diaAtAftMm)
    val r1 = rAt(span.diaAtFwdMm)
    val largerAtAft = r0 > r1
    val a = profile.easeAftFrac(largerAtAft)
    val b = profile.easeFwdFrac(largerAtAft)
    val w = span.xFwdPx - span.xAftPx

    fun surfaceR(t: Float) = r0 + (r1 - r0) * blendRadiusFrac(t, a, b)

    val notch = if (seal) sealNotchGeom(w, min(r0, r1)) else null
    if (notch == null) {
        return (0..steps).map { i ->
            val t = i.toFloat() / steps
            BodyEdgePoint(span.xAftPx + w * t, surfaceR(t))
        }
    }

    // Seal cuts break the silhouette: a V notch per groove, assembled as (t, radial inset)
    // stations. Regular samples inside a notch window are dropped so no surface point
    // pollutes the V. Both draw sites (and the fill polygons they build) iterate this list,
    // so the notches reach fill and stroke everywhere with no draw-site change.
    val dt = notch.halfWidthPx / w
    val fracs = sealGrooveFracs()
    val stations = buildList {
        for (i in 0..steps) {
            val t = i.toFloat() / steps
            if (fracs.none { g -> t > g - dt && t < g + dt }) add(t to 0f)
        }
        for (g in fracs) {
            add(g - dt to 0f)
            add(g to notch.depthPx)
            add(g + dt to 0f)
        }
        sortBy { it.first }
    }
    return stations.map { (t, inset) ->
        BodyEdgePoint(span.xAftPx + w * t, surfaceR(t) - inset)
    }
}

/**
 * Where a seal area's radius cuts cross its blend, in drawn units.
 *
 * The shop cuts 3–4 rings for the fiberglass to seat into, and they sit ON the blended section
 * running up to the liner. Each point carries the radius of the notch FLOOR — the local
 * surface minus [sealNotchGeom]'s depth — so a draw site stroking `cy − r → cy + r` produces a
 * line that ends exactly on the bottoms of the two silhouette notches [curvePoints] cuts at
 * the same station. Stopping short of the silhouette is deliberate: a full-height line is this
 * app's glyph for a component face, and the notch + inset line pair is what makes a groove
 * read as a cut instead of a boundary.
 *
 * Stations come from the shared [sealGrooveFracs] and keep a margin from the curve's own ends.
 */
internal fun sealGrooveLines(
    span: BlendDrawSpan,
    profile: BlendProfile,
    rAt: (Float) -> Float,
): List<BodyEdgePoint> {
    val r0 = rAt(span.diaAtAftMm)
    val r1 = rAt(span.diaAtFwdMm)
    val largerAtAft = r0 > r1
    val a = profile.easeAftFrac(largerAtAft)
    val b = profile.easeFwdFrac(largerAtAft)
    val w = span.xFwdPx - span.xAftPx
    val notch = sealNotchGeom(w, min(r0, r1)) ?: return emptyList()
    return sealGrooveFracs().map { t ->
        BodyEdgePoint(
            span.xAftPx + w * t,
            r0 + (r1 - r0) * blendRadiusFrac(t, a, b) - notch.depthPx,
        )
    }
}
