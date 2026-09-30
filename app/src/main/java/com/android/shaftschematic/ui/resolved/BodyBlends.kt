// file: app/src/main/java/com/android/shaftschematic/ui/resolved/BodyBlends.kt
package com.android.shaftschematic.ui.resolved

import com.android.shaftschematic.geom.BLEND_CURVE_STEPS
import com.android.shaftschematic.geom.MAX_SEAL_FACES_TOTAL_FRAC_OF_HOST
import com.android.shaftschematic.geom.SurfaceSeg
import com.android.shaftschematic.geom.blendRadiusFrac
import com.android.shaftschematic.geom.sealGrooveFracs
import com.android.shaftschematic.geom.easeAftFrac
import com.android.shaftschematic.geom.easeFwdFrac
import com.android.shaftschematic.geom.drawnBlendWidthPx
import com.android.shaftschematic.geom.drawnSealWidthPx
import com.android.shaftschematic.geom.outerDiaAt
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.BlendProfile
import com.android.shaftschematic.model.LinerAuthoredReference
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.autoBlendFor
import com.android.shaftschematic.model.blendMmOn
import com.android.shaftschematic.model.blendSealLenMmOn
import com.android.shaftschematic.model.blendSealOn
import com.android.shaftschematic.model.hasBlendOn
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
 *
 * [lengthMm] == 0 is a SEAL-ONLY face: no ramp (a square face, or a blend with no step to
 * climb), [neighbourDiaMm] equal to [bodyDiaMm], and the seal area starting at the face itself.
 */
data class BodyBlend(
    val bodyId: String,
    val end: LinerAuthoredReference,
    val faceMm: Float,
    val lengthMm: Float,
    val bodyDiaMm: Float,
    val neighbourDiaMm: Float,
    val profile: BlendProfile,
    /** Seal area: radius cuts measured from the face, for the fiberglass to seat into. */
    val seal: Boolean = false,
    /**
     * Length of the seal area, measured FROM THE FACE whether or not a ramp sits there — already
     * resolved (a stored 0 has been replaced by the blend length) and clamped to the run. 0 when
     * the face carries no seal, or none fits.
     */
    val sealLenMm: Float = 0f,
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
 * A blend's CURVE is dropped (not drawn, never an error) when there is no step to blend:
 * nothing across the face, or a neighbour at the same diameter. A seal area is a property of
 * the body section, independent of the face finish, so a face with a seal keeps its grooves
 * either way, measured from the face whether or not a ramp sits there.
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
    val blended = spec.bodies.filter { b ->
        LinerAuthoredReference.values().any { b.hasBlendOn(it) || b.blendSealOn(it) }
    }
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
                blendAt(components, segs, run, end, auto.lengthMm, auto.profile, auto.seal, auto.sealLenMm)
                    ?.let(::add)
            }
        }

        for (b in blended) {
            val runs = runsByBase[b.id] ?: continue
            for (end in LinerAuthoredReference.values()) {
                val stored = b.blendMmOn(end)
                if (stored <= 0f && !b.blendSealOn(end)) continue

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

                blendAt(
                    components, segs, run, end, stored, b.blendProfile,
                    b.blendSealOn(end), b.blendSealLenMmOn(end),
                )?.let(::add)
            }
        }
    }
}

/**
 * Resolve one face of one drawn run into a [BodyBlend], or null when the face draws nothing —
 * no curve (square, or no step to blend) and no seal area.
 *
 * Shared by the explicit and auto paths so the two can never disagree about what a face steps
 * to. The face is the run's own outer edge — its DRAWN extent (for an explicit body, its
 * stored span; body fragmentation still trims it).
 *
 * The curve and the seal area are independent: a face with no curve still draws its seal area,
 * returned as a seal-only [BodyBlend] (`lengthMm = 0`, neighbour Ø = the run's own). Either way
 * the seal area is measured from the face.
 */
private fun blendAt(
    components: List<ResolvedComponent>,
    segs: List<SurfaceSeg>,
    run: ResolvedBody,
    end: LinerAuthoredReference,
    storedLengthMm: Float,
    profile: BlendProfile,
    seal: Boolean,
    storedSealLenMm: Float = 0f,
): BodyBlend? {
    if (storedLengthMm <= 0f && !seal) return null
    val runLen = run.endMmPhysical - run.startMmPhysical
    if (runLen <= BLEND_EPS_MM) return null
    val faceMm = if (end == LinerAuthoredReference.AFT) run.startMmPhysical else run.endMmPhysical

    // The curve needs a step to climb. A square face has none by definition; otherwise sample
    // just OUTSIDE the face — that is the diameter the curve leaves from. Shaft surface first
    // (liners excluded); a liner butting the face supplies a derived seat instead.
    val neighbourDia: Float? = if (storedLengthMm <= 0f) null else {
        val probeMm = if (end == LinerAuthoredReference.AFT) faceMm - BLEND_EPS_MM * 10f
                      else faceMm + BLEND_EPS_MM * 10f
        (outerDiaAt(segs, probeMm).takeIf { it > 0f }
            ?: seatDiaUnderLiner(components, probeMm, run.diaMm))
            ?.takeIf { abs(it - run.diaMm) > BLEND_EPS_MM }
    }
    // Clamping the DRAWN curve is not rewriting what was typed.
    val curveLen = if (neighbourDia == null) 0f else storedLengthMm.coerceAtMost(runLen)

    // The seal area is measured FROM THE FACE whether or not a ramp sits there, so the ramp takes
    // nothing from it; a stored 0 (every document from before the seal length had its own field)
    // follows the blend length, so an older seal keeps its grooves without anyone retyping it. A
    // length that resolves to nothing draws no seal — never an error.
    val sealLen = if (!seal) 0f else
        (storedSealLenMm.takeIf { it > 0f } ?: storedLengthMm).coerceIn(0f, runLen)
    val sealDrawn = seal && sealLen > 0f

    if (curveLen <= 0f && !sealDrawn) return null

    return BodyBlend(
        bodyId = run.id,
        end = end,
        faceMm = faceMm,
        lengthMm = curveLen,
        bodyDiaMm = run.diaMm,
        neighbourDiaMm = neighbourDia ?: run.diaMm,
        profile = profile,
        seal = sealDrawn,
        sealLenMm = if (sealDrawn) sealLen else 0f,
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
    // A seal-only face has no ramp: a zero-width span AT the face, so the seal area is placed
    // against the face itself.
    if (lengthMm <= 0f) return BlendDrawSpan(xFace, xFace, bodyDiaMm, bodyDiaMm)
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

/** A seal area's drawn span, anchored at its face, or null when none draws. */
data class SealDrawSpan(val xAftPx: Float, val xFwdPx: Float)

/**
 * This blend's seal area under the same x mapping, anchored AT THE FACE: AFT →
 * `[xFace, xFace + w]`, FWD → `[xFace − w, xFace]`.
 *
 * The seal area is measured from the face, never from the ramp's inboard end, so it sits in the
 * same place whether the face is square or blended (on-device report: a blend pushed the dashes
 * inward). A ramp at the same face may overlap the seal span's outer end; the two are
 * independent.
 *
 * The width is the TRUE width of [BodyBlend.sealLenMm] (`faceMm` … `faceMm ± sealLenMm`) with the
 * seal floor and the per-face cap applied ([drawnSealWidthPx]); the two-face total cap is applied
 * by [bodyDrawEdges], which sees both faces. Null when the face carries no seal or nothing fits.
 */
fun BodyBlend.sealDrawSpan(
    runStartMm: Float,
    runEndMm: Float,
    xAt: (Float) -> Float,
    minWidthPx: Float,
): SealDrawSpan? {
    if (!seal || sealLenMm <= 0f) return null
    val hostWidth = abs(xAt(runEndMm) - xAt(runStartMm))
    val xFace = xAt(faceMm)
    val sealEndMm = if (end == LinerAuthoredReference.AFT) faceMm + sealLenMm else faceMm - sealLenMm
    val trueWidth = abs(xAt(sealEndMm) - xFace)
    val w = drawnSealWidthPx(trueWidth, hostWidth, minWidthPx)
    if (w <= 0f) return null
    return sealSpanAtFace(end, xFace, w)
}

private fun sealSpanAtFace(end: LinerAuthoredReference, xFace: Float, w: Float): SealDrawSpan =
    if (end == LinerAuthoredReference.AFT) SealDrawSpan(xFace, xFace + w)
    else SealDrawSpan(xFace - w, xFace)

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
 * A seal area never touches the outline: [aftCurve]/[fwdCurve] carry the ramp only, the flat
 * span runs ramp to ramp, and the silhouette stays flat through the seal area. The cuts are
 * [aftSeal]/[fwdSeal] — one full-height dashed line per groove — and the seal areas' drawn
 * extents are [sealSpansX], which the S-break gap steers clear of. A seal-only face (no ramp)
 * contributes no curve points and caps at the body radius.
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
     * Seal grooves on the aft face's seal area: one (x, radius) per cut, in drawn units, radius
     * = the SILHOUETTE radius at that x (the body radius on the flat, the ramp's local radius
     * inside a ramp) — a draw site strokes `cy − r → cy + r` (dashed) and the line spans the
     * outline it crosses, top edge to bottom edge.
     */
    val aftSeal: List<BodyEdgePoint> = emptyList(),
    /** Seal grooves on the fwd face's seal area, same convention. */
    val fwdSeal: List<BodyEdgePoint> = emptyList(),
    /**
     * The drawn x-range of each seal area on this run (aft, then fwd), for the S-break gap to
     * steer clear of — a break cut through a seal area would leave a dashed line floating in the
     * paper gap. Fed to `breakGapCenter` as avoid ranges beside the keyway windows.
     */
    val sealSpansX: List<ClosedFloatingPointRange<Float>> = emptyList(),
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
    var aftSealSpan = aft?.sealDrawSpan(runStartMm, runEndMm, xAt, minWidthPx)
    var fwdSealSpan = fwd?.sealDrawSpan(runStartMm, runEndMm, xAt, minWidthPx)

    // Two seal areas on one run share MAX_SEAL_FACES_TOTAL_FRAC_OF_HOST between them, scaled
    // down together so their ratio holds: the flat span between them hosts the S-break pair.
    if (aftSealSpan != null && fwdSealSpan != null) {
        val aftW = aftSealSpan.xFwdPx - aftSealSpan.xAftPx
        val fwdW = fwdSealSpan.xFwdPx - fwdSealSpan.xAftPx
        val cap = abs(x1 - x0) * MAX_SEAL_FACES_TOTAL_FRAC_OF_HOST
        if (aftW + fwdW > cap) {
            val k = cap / (aftW + fwdW)
            aftSealSpan = sealSpanAtFace(LinerAuthoredReference.AFT, aftSealSpan.xAftPx, aftW * k)
            fwdSealSpan = sealSpanAtFace(LinerAuthoredReference.FWD, fwdSealSpan.xFwdPx, fwdW * k)
        }
    }

    // The flat span runs ramp to ramp; seal areas never shrink it.
    val flatX0 = aftSpan?.xFwdPx ?: x0
    val flatX1 = fwdSpan?.xAftPx ?: x1
    // Two blends on a short run can meet; give the flat span back rather than invert it.
    if (flatX1 <= flatX0) return BodyDrawEdges(emptyList(), emptyList(), x0, x1, r, r, r)

    // A zero-width span (a seal-only face) contributes no curve points — sampling it would emit
    // `steps + 1` coincident vertices.
    val aftCurve =
        if (aftSpan != null && aftSpan.xFwdPx - aftSpan.xAftPx > 0f) curvePoints(aftSpan, aft.profile, rAt, steps)
        else emptyList()
    val fwdCurve =
        if (fwdSpan != null && fwdSpan.xFwdPx - fwdSpan.xAftPx > 0f) curvePoints(fwdSpan, fwd.profile, rAt, steps)
        else emptyList()

    val radiusAt = { x: Float -> silhouetteRadiusAt(x, aftCurve, fwdCurve, r, flatX0, flatX1) }

    return BodyDrawEdges(
        aftCurve = aftCurve,
        fwdCurve = fwdCurve,
        flatX0 = flatX0,
        flatX1 = flatX1,
        flatR = r,
        capAftR = aft?.let { rAt(it.neighbourDiaMm) } ?: r,
        capFwdR = fwd?.let { rAt(it.neighbourDiaMm) } ?: r,
        aftSeal = sealGrooveLines(aftSealSpan, radiusAt),
        fwdSeal = sealGrooveLines(fwdSealSpan, radiusAt),
        sealSpansX = listOfNotNull(aftSealSpan, fwdSealSpan).map { it.xAftPx..it.xFwdPx },
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

/**
 * The drawn silhouette radius at [xPx]: inside a ramp, linearly interpolated between the two
 * neighbouring curve points; anywhere else, the flat body radius [flatR].
 *
 * The seal lines follow the outline they cross — a line landing inside a ramp spans the ramp's
 * local height, so it never pokes out past the outline or stops short of it.
 */
private fun silhouetteRadiusAt(
    xPx: Float,
    aftCurve: List<BodyEdgePoint>,
    fwdCurve: List<BodyEdgePoint>,
    flatR: Float,
    flatX0: Float,
    flatX1: Float,
): Float {
    val curve = when {
        aftCurve.isNotEmpty() && xPx < flatX0 -> aftCurve
        fwdCurve.isNotEmpty() && xPx > flatX1 -> fwdCurve
        else -> return flatR
    }
    if (xPx <= curve.first().xPx) return curve.first().rPx
    if (xPx >= curve.last().xPx) return curve.last().rPx
    for (i in 0 until curve.size - 1) {
        val a = curve[i]
        val b = curve[i + 1]
        if (xPx in a.xPx..b.xPx) {
            val dx = b.xPx - a.xPx
            return if (dx <= 0f) a.rPx else a.rPx + (b.rPx - a.rPx) * (xPx - a.xPx) / dx
        }
    }
    return flatR
}

/**
 * Where a seal area's radius cuts cross it, in drawn units.
 *
 * The shop cuts 3–4 rings for the fiberglass to seat into, measured from the face whether it is
 * square or blended. Each point carries the silhouette radius at its x ([silhouetteRadiusAt]), so a
 * draw site stroking `cy − r → cy + r` produces one full-height line that follows the outline it
 * crosses — the body radius on the flat, the ramp's local height inside a ramp. The draw sites
 * dash it (`SEAL_DASH_ON_PT`/`SEAL_DASH_OFF_PT`): a solid full-height line is this app's glyph for
 * a component face.
 *
 * Stations come from the shared [sealGrooveFracs] and keep a margin from the span's own ends.
 * Empty when the face has no seal area.
 */
internal fun sealGrooveLines(
    span: SealDrawSpan?,
    silhouetteRadiusAt: (Float) -> Float,
): List<BodyEdgePoint> {
    if (span == null) return emptyList()
    val w = span.xFwdPx - span.xAftPx
    return sealGrooveFracs().map { t ->
        val x = span.xAftPx + w * t
        BodyEdgePoint(x, silhouetteRadiusAt(x))
    }
}
