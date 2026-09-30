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

/**
 * How many radius cuts a seal area draws. The shop cuts 3–4 for the fiberglass to seat into;
 * the drawing is a schematic cue, not a count to machine from, so it draws a fixed 3.
 */
const val SEAL_GROOVE_COUNT = 3

/**
 * Where the seal grooves cross the seal area, as fractions of its span.
 *
 * Evenly spaced with a margin at each end — `(i + 1) / (count + 1)` — so no groove lands on the
 * span's own ends: at the outboard end it would sit on the face itself, at the inboard end it
 * would read as a component boundary rather than a cut.
 */
fun sealGrooveFracs(count: Int = SEAL_GROOVE_COUNT): List<Float> =
    if (count < 1) emptyList() else (1..count).map { it.toFloat() / (count + 1) }

// Dash pattern for a seal cut's line. Each cut draws as ONE full-height line across the body,
// silhouette to silhouette, over a flat outline. Dashed on purpose: a solid full-height vertical
// is the glyph for a component face, and three solid lines made the shaft read as 3-4 segments
// when it is one whole unit (on-device report). Deliberately FINER than the hidden-keyway 6/4
// dash — that pattern means "far-side feature", and a seal cut is a near-side cut, so the two
// must never read alike. Shared verbatim by both draw sites and the SVG preview.
const val SEAL_DASH_ON_PT = 2.5f
const val SEAL_DASH_OFF_PT = 2f

/** A blend never eats more than this fraction of the drawn run it is machined into. */
const val MAX_BLEND_FRAC_OF_HOST = 0.4f

/**
 * The seal area's visibility floor, as a multiple of the blend's: three grooves with clear
 * surface between them need more room than one curve before they read at all.
 */
const val SEAL_AREA_MIN_WIDTH_FACTOR = 2f

/**
 * One seal area never draws wider than this fraction of its run. Wider than
 * [MAX_BLEND_FRAC_OF_HOST] because the grooved area IS most of a fiberglassed body (8 in of rings
 * on a 12 in section is ordinary); under half so one face can never claim the whole run.
 */
const val MAX_SEAL_FACE_FRAC_OF_HOST = 0.45f

/**
 * When BOTH faces of one run carry seal areas, their drawn widths together never exceed this
 * fraction of the run; both scale down in proportion (their ratio preserved) when they would.
 *
 * The run's flat span hosts the S-break pair, which is the drawing's statement that the run is
 * foreshortened, and the break gap steers clear of seal areas. Two seal areas at the per-face
 * cap would leave the gap nowhere to land and the run would print plain, reading as a short
 * body (on-device report) — so together they must leave it room.
 */
const val MAX_SEAL_FACES_TOTAL_FRAC_OF_HOST = 0.5f

/**
 * Drawn width of a seal area whose true width is [trueWidthPx] on a run [hostWidthPx] wide.
 *
 * Same posture as [drawnBlendWidthPx] — the stored length is never touched, the floor
 * (`minWidthPx × SEAL_AREA_MIN_WIDTH_FACTOR`) keeps the grooves legible on a compressed sheet, and
 * it is safe because a seal area prints no number. The seal area is measured from the face and is
 * independent of any ramp there, so the ramp takes nothing from its room; the per-face cap
 * ([MAX_SEAL_FACE_FRAC_OF_HOST]) wins over the floor. The two-face total
 * ([MAX_SEAL_FACES_TOTAL_FRAC_OF_HOST]) is applied by the caller, which sees both faces. Returns 0
 * when nothing fits.
 */
fun drawnSealWidthPx(trueWidthPx: Float, hostWidthPx: Float, minWidthPx: Float): Float {
    if (trueWidthPx <= 0f || hostWidthPx <= 0f) return 0f
    val room = hostWidthPx * MAX_SEAL_FACE_FRAC_OF_HOST
    return maxOf(trueWidthPx, minWidthPx * SEAL_AREA_MIN_WIDTH_FACTOR).coerceAtMost(room)
}

/** Minimum drawn blend width so the curve still reads on a compressed sheet (PDF points). */
const val MIN_BLEND_WIDTH_PT = 7f

/** Minimum drawn blend width on the preview canvas (px at the canvas's own scale). */
const val MIN_BLEND_WIDTH_PX = 10f
