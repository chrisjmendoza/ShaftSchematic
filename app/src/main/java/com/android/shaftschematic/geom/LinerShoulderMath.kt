// file: app/src/main/java/com/android/shaftschematic/geom/LinerShoulderMath.kt
package com.android.shaftschematic.geom

import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * LinerShoulderMath — the drawn silhouette of a liner with stepped shoulders, shared by every
 * draw site (preview canvas + schematic PDF), the `KeywaySilhouetteMath` (x, radius) convention.
 *
 * A shoulder is machined INTO the liner end: over the outermost shoulder length the OD drops to
 * a reduced diameter, with a full-height step face at the inner boundary and a fillet where the
 * face meets the liner OD. Everything here is drawn-unit geometry; the callers map mm through
 * their own xAt/rAt. The stored values are never rewritten — a shoulder longer than the liner
 * accommodates is clamped where it is DRAWN, the blend-width posture, and that exaggeration/
 * clamping is safe because no shoulder number prints on a rail (the radius reaches paper only
 * as a footer note).
 *
 * The fillet is a true circular quarter-round (axial span = radial drop = radius), not an ease
 * curve — which is why it does not go through `BlendProfileMath.blendRadiusFrac`: that primitive
 * joins two diameters over an INDEPENDENT axial span, and a machined corner radius has no
 * independent span to give it.
 */

/** One vertex of the liner's drawn TOP silhouette: x and RADIUS from the centerline. */
data class ShoulderPoint(val xPx: Float, val rPx: Float)

/**
 * Standard shoulder-edge radii offered by the picker, in INCHES (0 = sharp corner).
 * Provisional set chosen without shop input — the warning-threshold posture; adjust freely.
 * The UI converts to canonical mm on selection (golden rule: the stored mm is exact).
 */
val LINER_SHOULDER_STD_RADII_IN: List<Float> =
    listOf(0f, 1f / 16f, 1f / 8f, 3f / 16f, 1f / 4f, 3f / 8f, 1f / 2f)

/** Sample count for the fillet quarter-arc. */
const val SHOULDER_ARC_STEPS = 8

/** The drawn fillet keeps this fraction of the step height as its ceiling. */
const val SHOULDER_FILLET_MAX_FRAC_OF_STEP = 0.9f

/** …and this fraction of the run's drawn width, so a big radius can't eat a short liner. */
const val SHOULDER_FILLET_MAX_FRAC_OF_RUN = 0.1f

/** One end's shoulder in drawn units, already floored/clamped — input to [linerTopSilhouette]. */
data class ShoulderDrawSpec(
    val lenPx: Float,
    val odRPx: Float,
    val filletRPx: Float,
)

/**
 * Resolve one end's shoulder into drawn units, or null when it draws as no step at all —
 * a reduced OD at or above the liner OD has no step to show (not an error; the values are
 * kept stored, the drawing simply cannot express them).
 *
 * [trueLenPx] is the shoulder length through the site's own x map; the drawn length takes the
 * same visibility floor AND host-fraction cap as a blend ([drawnBlendWidthPx] with
 * [minWidthPx] — its [MAX_BLEND_FRAC_OF_HOST] cap is per end, so two shoulders can never
 * meet), then the fillet is capped against the step height and the run width.
 */
fun shoulderDrawSpec(
    trueLenPx: Float,
    runWidthPx: Float,
    linerRPx: Float,
    shoulderRPx: Float,
    filletRPx: Float,
    minWidthPx: Float,
): ShoulderDrawSpec? {
    if (runWidthPx <= 0f || trueLenPx <= 0f) return null
    if (shoulderRPx >= linerRPx) return null
    val len = drawnBlendWidthPx(trueLenPx, runWidthPx, minWidthPx)
    if (len <= 0f) return null
    val step = linerRPx - shoulderRPx
    val fillet = min(
        filletRPx.coerceAtLeast(0f),
        min(step * SHOULDER_FILLET_MAX_FRAC_OF_STEP, runWidthPx * SHOULDER_FILLET_MAX_FRAC_OF_RUN),
    )
    return ShoulderDrawSpec(lenPx = len, odRPx = shoulderRPx, filletRPx = fillet)
}

/**
 * The liner's TOP silhouette from aft cap to fwd cap, shoulders and fillets included.
 * A draw site strokes consecutive points at `cy − r`, mirrors them at `cy + r`, closes the
 * end caps vertically at the FIRST and LAST points' radii, and builds its fill polygon from
 * the same list — fill and stroke can never disagree.
 */
fun linerTopSilhouette(
    x0: Float,
    x1: Float,
    linerRPx: Float,
    aft: ShoulderDrawSpec?,
    fwd: ShoulderDrawSpec?,
    arcSteps: Int = SHOULDER_ARC_STEPS,
): List<ShoulderPoint> = buildList {
    if (aft != null) {
        val xStep = x0 + aft.lenPx
        add(ShoulderPoint(x0, aft.odRPx))
        add(ShoulderPoint(xStep, aft.odRPx))
        if (aft.filletRPx > 0f) {
            // Face rises to the arc's spring point; the quarter-round carries it to the OD.
            add(ShoulderPoint(xStep, linerRPx - aft.filletRPx))
            addAll(filletArc(xStep, linerRPx, aft.filletRPx, arcSteps, intoRun = true))
        } else {
            add(ShoulderPoint(xStep, linerRPx))
        }
    } else {
        add(ShoulderPoint(x0, linerRPx))
    }

    if (fwd != null) {
        val xStep = x1 - fwd.lenPx
        if (fwd.filletRPx > 0f) {
            addAll(filletArc(xStep, linerRPx, fwd.filletRPx, arcSteps, intoRun = false))
            add(ShoulderPoint(xStep, linerRPx - fwd.filletRPx))
        } else {
            add(ShoulderPoint(xStep, linerRPx))
        }
        add(ShoulderPoint(xStep, fwd.odRPx))
        add(ShoulderPoint(x1, fwd.odRPx))
    } else {
        add(ShoulderPoint(x1, linerRPx))
    }
}

/**
 * Quarter-round fillet at a step corner. [xFace] is the step face's x; the arc springs from
 * `(xFace, linerR − r)` and lands tangent on the OD one radius INTO the run —
 * aft-ward ([intoRun] false) or fwd-ward (true). Points are emitted travelling aft → fwd,
 * matching [linerTopSilhouette]'s direction.
 */
internal fun filletArc(
    xFace: Float,
    linerRPx: Float,
    r: Float,
    steps: Int,
    intoRun: Boolean,
): List<ShoulderPoint> {
    val cx = if (intoRun) xFace + r else xFace - r
    val cr = linerRPx - r
    return (1..steps).map { i ->
        val t = (Math.PI / 2) * i / steps
        val dx = (r * cos(t)).toFloat()
        val dr = (r * sin(t)).toFloat()
        ShoulderPoint(
            xPx = if (intoRun) cx - dx else cx + dx,
            rPx = cr + dr,
        )
    }.let { if (intoRun) it else it.reversed() }
}
