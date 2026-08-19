// file: app/src/main/java/com/android/shaftschematic/geom/BlendProfileMath.kt
package com.android.shaftschematic.geom

import com.android.shaftschematic.model.BlendProfile

/**
 * BlendProfileMath — the drawn curve joining two radii across an axial span.
 *
 * A **blend** is a machined smooth transition between two diameters: no square shoulder,
 * no dimensioned taper rate. It is a silhouette feature only — it carries no dimension
 * rail and no footer row — so nothing here may feed a printed number.
 *
 * The primitive is deliberately general: "join radius A to radius B over `[x0, x1]`",
 * expressed in `(x, radius)` points like [KeywaySilhouettePoint] and [SurfacePoint]. The
 * same call serves a body's blended face, a liner shoulder fillet, and an undercut end
 * radius; only the caller's choice of the two radii differs.
 *
 * Pure and android-free (geom posture): `pdf` and `ui` may depend on this, never the
 * reverse. Unit-tested on a plain JVM (`BlendProfileMathTest`).
 */

/**
 * Fraction of the span eased at the aft end, given which end carries the larger radius.
 *
 * Each preset is one `(easeAft, easeFwd)` pair handed to [blendRadiusFrac] — the fraction
 * of the span spent easing at each end, with a straight ramp between. A full ease meets its
 * neighbour with a horizontal tangent (no corner); a zero ease meets it with a corner.
 *
 * The drawn curve is a C1 parabolic biarc, not a dimensioned circular arc — it fits the
 * authored length and diameter step exactly at every combination, with no degenerate cases
 * to guard. That is the right trade while a blend prints no radius value. A true-arc profile
 * belongs here as a fourth [BlendProfile] the day a radius becomes a printed machining
 * call-out.
 */
fun BlendProfile.easeAftFrac(largerAtAft: Boolean): Float = when (this) {
    BlendProfile.OGEE -> 0.5f
    BlendProfile.EASED_CONE -> 0.25f
    BlendProfile.FILLET -> if (largerAtAft) 0.5f else 0f
}

/** Fraction of the span eased at the fwd end, given which end carries the larger radius. */
fun BlendProfile.easeFwdFrac(largerAtAft: Boolean): Float = when (this) {
    BlendProfile.OGEE -> 0.5f
    BlendProfile.EASED_CONE -> 0.25f
    BlendProfile.FILLET -> if (largerAtAft) 0f else 0.5f
}

/** Points sampled across a blend span. Enough that the curve reads smooth at print scale. */
const val BLEND_CURVE_STEPS = 24

/**
 * Normalized radius fraction at [t] ∈ `[0, 1]` for a transition easing over [easeStart]
 * of the span at the start and [easeEnd] at the end.
 *
 * Returns 0 at `t = 0` and 1 at `t = 1`, strictly non-decreasing between. The middle runs
 * at a constant slope `m`; each ease is the parabola that carries the slope between 0 and
 * `m`, so the join is C¹ and the eased end meets its neighbour with a horizontal tangent.
 * Solving `m·a/2 + m·(1 − a − b) + m·b/2 = 1` gives `m = 1 / (1 − (a + b) / 2)`, which is
 * why a full `0.5 / 0.5` ease is exactly a symmetric parabolic S with `m = 2`.
 *
 * [easeStart] and [easeEnd] are clamped into `[0, 1]` and scaled down together if they
 * would sum past 1, so no caller can produce a discontinuous curve.
 */
fun blendRadiusFrac(t: Float, easeStart: Float, easeEnd: Float): Float {
    val tc = t.coerceIn(0f, 1f)
    var a = easeStart.coerceIn(0f, 1f)
    var b = easeEnd.coerceIn(0f, 1f)
    val sum = a + b
    if (sum > 1f) { a /= sum; b /= sum }

    val m = 1f / (1f - (a + b) / 2f)
    return when {
        // Leading ease: slope ramps 0 → m over [0, a].
        a > 0f && tc < a -> m * tc * tc / (2f * a)
        // Trailing ease: slope ramps m → 0 over [1 - b, 1], measured back from the end.
        b > 0f && tc > 1f - b -> {
            val u = (1f - tc) / b
            1f - m * b * u * u / 2f
        }
        // Straight middle.
        else -> m * a / 2f + m * (tc - a)
    }
}

/**
 * The blend's surface polyline over `[x0Mm, x1Mm]`, running [radius0Mm] → [radius1Mm].
 *
 * Points are aft → fwd, both endpoints included, `[steps] + 1` of them. A degenerate span
 * or an equal-radius pair yields the two endpoints only — there is no step to blend, so
 * callers draw nothing.
 */
fun blendPolyline(
    x0Mm: Float,
    x1Mm: Float,
    radius0Mm: Float,
    radius1Mm: Float,
    profile: BlendProfile,
    steps: Int = BLEND_CURVE_STEPS,
): List<SurfacePoint> {
    val span = x1Mm - x0Mm
    val drop = radius1Mm - radius0Mm
    if (span <= 0f || drop == 0f || steps < 1) {
        return listOf(SurfacePoint(x0Mm, radius0Mm * 2f), SurfacePoint(x1Mm, radius1Mm * 2f))
    }
    val largerAtAft = radius0Mm > radius1Mm
    val a = profile.easeAftFrac(largerAtAft)
    val b = profile.easeFwdFrac(largerAtAft)
    return (0..steps).map { i ->
        val t = i.toFloat() / steps
        val r = radius0Mm + drop * blendRadiusFrac(t, a, b)
        SurfacePoint(x0Mm + span * t, r * 2f)
    }
}

/**
 * Drawn axial width (px/pt) for a blend of true width [trueWidthPx] inside a host run of
 * drawn width [hostWidthPx].
 *
 * A blend is a couple of inches on a shaft that can be twenty-five feet long: at true
 * scale on a compressed sheet it collapses to sub-pixel and the feature is invisible on
 * exactly the drawings that need it. The drawn width therefore takes a floor of
 * [minWidthPx] — display exaggeration in the same posture as the undercut notch depth and
 * the wear trace, and safe here because a blend prints no dimension and no footer row, so
 * no exaggerated number can reach a machinist. The stored length is never touched.
 *
 * The floor yields to the host: a blend never draws wider than [MAX_BLEND_FRAC_OF_HOST] of
 * the run it is cut into, so a short body can't be swallowed by its own blend.
 */
fun drawnBlendWidthPx(trueWidthPx: Float, hostWidthPx: Float, minWidthPx: Float): Float {
    if (trueWidthPx <= 0f || hostWidthPx <= 0f) return 0f
    val ceiling = hostWidthPx * MAX_BLEND_FRAC_OF_HOST
    return maxOf(trueWidthPx, minWidthPx).coerceAtMost(ceiling)
}

/** A blend never eats more than this fraction of the drawn run it is machined into. */
const val MAX_BLEND_FRAC_OF_HOST = 0.4f

/** Minimum drawn blend width so the curve still reads on a compressed sheet (PDF points). */
const val MIN_BLEND_WIDTH_PT = 7f

/** Minimum drawn blend width on the preview canvas (px at the canvas's own scale). */
const val MIN_BLEND_WIDTH_PX = 10f
