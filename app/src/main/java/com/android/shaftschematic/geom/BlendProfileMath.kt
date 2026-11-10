// file: app/src/main/java/com/android/shaftschematic/geom/BlendProfileMath.kt
package com.android.shaftschematic.geom

import com.android.shaftschematic.model.BlendProfile
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * BlendProfileMath — the drawn curve joining two radii across an axial span.
 *
 * A **blend** is a machined smooth transition between two diameters: no square shoulder,
 * no dimensioned taper rate. It carries no dimension rail and no footer row, and nothing
 * here may feed a printed number: an Angle-mode face's callouts print the STORED angle and
 * corner radius, never anything measured off this geometry (a sheet's axial and diameter
 * scales differ and the drawn width is floored, so a drawn slope is not the true angle).
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
 * to guard — the right trade for a Length-mode blend, which prints no value. A face that
 * prints a machining call-out is an Angle-mode face: a straight cone and a TRUE circular
 * corner radius ([ChamferShape]), not one of these presets.
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
 * Points are aft → fwd, both endpoints included. An eased [profile] samples `[steps] + 1` of
 * them; a [chamfer] (an Angle-mode face) yields its own vertices instead — the cone's ends and
 * the corner radius's arc samples ([blendFracPoints]). A degenerate span or an equal-radius pair
 * yields the two endpoints only — there is no step to blend, so callers draw nothing.
 */
fun blendPolyline(
    x0Mm: Float,
    x1Mm: Float,
    radius0Mm: Float,
    radius1Mm: Float,
    profile: BlendProfile,
    steps: Int = BLEND_CURVE_STEPS,
    chamfer: ChamferShape? = null,
): List<SurfacePoint> {
    val span = x1Mm - x0Mm
    val drop = radius1Mm - radius0Mm
    if (span <= 0f || drop == 0f || steps < 1) {
        return listOf(SurfacePoint(x0Mm, radius0Mm * 2f), SurfacePoint(x1Mm, radius1Mm * 2f))
    }
    return blendFracPoints(profile, chamfer, largerAtAft = radius0Mm > radius1Mm, steps).map { p ->
        SurfacePoint(x0Mm + span * p.t, (radius0Mm + drop * p.frac) * 2f)
    }
}

/**
 * One vertex of a blend's normalized shape: [t] the axial fraction of the span from its AFT end,
 * [frac] the fraction of the way from the aft radius to the fwd radius. Both draw paths (the mm
 * surface envelope and the drawn silhouette) map these same points through their own scales, so
 * the two can never describe different shapes.
 */
data class BlendFracPoint(val t: Float, val frac: Float)

/**
 * The normalized vertices of a blend, aft → fwd, `(0, 0)` to `(1, 1)`.
 *
 * Length mode ([chamfer] null): [steps] + 1 samples of the eased [profile]. Angle mode: the
 * [chamfer]'s own vertices ([ChamferShape.fracPoints], large end first) oriented by
 * [largerAtAft] — read as they are when the aft end carries the larger radius, mirrored end for
 * end otherwise. The profile is ignored in Angle mode (a chamfer is a straight cone).
 */
fun blendFracPoints(
    profile: BlendProfile,
    chamfer: ChamferShape?,
    largerAtAft: Boolean,
    steps: Int = BLEND_CURVE_STEPS,
): List<BlendFracPoint> {
    if (chamfer != null) {
        val fromLarge = chamfer.fracPoints()
        return if (largerAtAft) fromLarge
        else fromLarge.asReversed().map { BlendFracPoint(1f - it.t, 1f - it.frac) }
    }
    val a = profile.easeAftFrac(largerAtAft)
    val b = profile.easeFwdFrac(largerAtAft)
    val n = steps.coerceAtLeast(1)
    return (0..n).map { i ->
        val t = i.toFloat() / n
        BlendFracPoint(t, blendRadiusFrac(t, a, b))
    }
}

/** [BlendFracPoint] of a point given large-end-first ([ChamferShape.fracPoints] form), oriented aft → fwd. */
fun BlendFracPoint.orientedForAft(largerAtAft: Boolean): BlendFracPoint =
    if (largerAtAft) this else BlendFracPoint(1f - t, 1f - frac)

// ──────────────────────────────────────────────────────────────────────────────
// Angle-mode faces: a straight cone at a stored angle, with an optional corner radius
// ──────────────────────────────────────────────────────────────────────────────

/** An Angle-mode blend angle must lie strictly inside this open range (degrees from the axis). */
const val BLEND_ANGLE_MIN_DEG = 0f
const val BLEND_ANGLE_MAX_DEG = 90f

/** Arc samples across a chamfer's corner radius — enough that the round reads at print scale. */
const val CHAMFER_ARC_STEPS = 12

/** An angle as its field and its callout show it: the stored value, trailing zeros trimmed (`45`, `30.5`). */
fun formatBlendAngleDeg(deg: Float): String =
    String.format(Locale.US, "%.3f", deg).trimEnd('0').trimEnd('.')

/** True when [angleDeg] is a usable Angle-mode angle: finite and strictly between 0° and 90°. */
fun isValidBlendAngle(angleDeg: Float?): Boolean =
    angleDeg != null && angleDeg.isFinite() && angleDeg > BLEND_ANGLE_MIN_DEG && angleDeg < BLEND_ANGLE_MAX_DEG

/**
 * Axial length of a straight cone climbing [riseMm] (a RADIUS difference — half the diameter
 * step) at [angleDeg] measured from the shaft axis: `rise / tan(angle)`. Null when there is no
 * rise or the angle is not usable. 45° climbs one for one.
 */
fun chamferConeLengthMm(riseMm: Float, angleDeg: Float): Float? {
    if (riseMm <= 0f || !isValidBlendAngle(angleDeg)) return null
    return (riseMm / tan(Math.toRadians(angleDeg.toDouble()))).toFloat()
}

/**
 * The true geometry of an Angle-mode face: a straight cone at [angleDeg] from the axis climbing
 * [riseMm], with a corner radius [radiusMm] rounding the corner at the cone's SMALL-diameter end
 * — tangent to the cone and to the smaller-diameter surface (the shop sketch: a 45° chamfer from a
 * liner down to the body, then a 3/8" R into the body).
 *
 * The tangent setback is `R·tan(θ/2)`, measured along each line from the sharp corner, so the
 * round meets the cone [setbackMm]·cosθ axially before the corner and the flat surface
 * [setbackMm] past it. The curve's axial extent is therefore [coneLenMm] + [setbackMm] —
 * [spanMm]. A blend never reaches across its own face, so when the smaller diameter is ACROSS the
 * face (a body stepping down to a smaller neighbour) the round starts at the face and the cone
 * follows it; the shape is the same construction mirrored end for end ([blendFracPoints]).
 *
 * [radiusMm] here is the DRAWN radius: a stored radius too large to fit the cone is clamped to
 * the largest that does (the round then consumes the whole cone) — never the stored value, which
 * prints verbatim. Units: mm.
 */
data class ChamferShape(
    val angleDeg: Float,
    val riseMm: Float,
    /** Axial run of the cone between its two sharp corners: `rise / tan(angle)`. */
    val coneLenMm: Float,
    /** Drawn corner radius, already clamped to what fits (0 = a sharp corner). */
    val radiusMm: Float,
) {
    private val theta: Double get() = Math.toRadians(angleDeg.toDouble())

    /** The round's tangent setback from the sharp corner along each line, `R·tan(θ/2)`. */
    val setbackMm: Float get() = (radiusMm * tan(theta / 2.0)).toFloat()

    /** Axial extent of the whole curve: the cone plus the round's run-out past the corner. */
    val spanMm: Float get() = coneLenMm + setbackMm

    /**
     * The shape's vertices from the LARGE end `(0, 0)` to the small end `(1, 1)`: t = axial
     * fraction of [spanMm], frac = fraction of [riseMm] dropped. A sharp corner is the cone's two
     * ends; a rounded one is the cone's large end, its tangent point, then [arcSteps] samples of
     * the round down to the small surface.
     */
    fun fracPoints(arcSteps: Int = CHAMFER_ARC_STEPS): List<BlendFracPoint> {
        if (radiusMm <= 0f) return listOf(BlendFracPoint(0f, 0f), BlendFracPoint(1f, 1f))
        val n = arcSteps.coerceAtLeast(1)
        return buildList {
            add(BlendFracPoint(0f, 0f))
            for (k in 0..n) {
                val p = arcFrac(theta * k / n)
                // A round clamped to the whole cone starts AT the large end; a repeated vertex
                // would be a zero-length surface segment.
                if (p.t - last().t > 1e-6f) add(p)
            }
            // The shape always lands exactly on the small surface.
            if (last().t >= 1f - 1e-6f) set(lastIndex, BlendFracPoint(1f, 1f)) else add(BlendFracPoint(1f, 1f))
        }
    }

    /** The straight cone's midpoint — where an angle callout's leader lands. */
    fun coneMidFrac(): BlendFracPoint {
        val tangent = arcFrac(0.0).takeIf { radiusMm > 0f } ?: BlendFracPoint(1f, 1f)
        return BlendFracPoint(tangent.t / 2f, tangent.frac / 2f)
    }

    /** The round's midpoint — where a radius callout's leader lands; null for a sharp corner. */
    fun cornerMidFrac(): BlendFracPoint? = if (radiusMm <= 0f) null else arcFrac(theta / 2.0)

    /**
     * A point on the round, [sweep] radians past its tangent with the cone (0 = on the cone,
     * θ = on the small surface). The round's centre sits [radiusMm] above the small surface at the
     * flat tangent point, so a point at angle φ = −π/2 − θ + sweep lies `R·(1 + sin φ)` above it.
     */
    private fun arcFrac(sweep: Double): BlendFracPoint {
        val span = spanMm.toDouble()
        if (span <= 0.0 || riseMm <= 0f) return BlendFracPoint(1f, 1f)
        val phi = -PI / 2.0 - theta + sweep
        val s = span + radiusMm * cos(phi)
        val heightAboveSmall = radiusMm * (1.0 + sin(phi))
        val drop = riseMm - heightAboveSmall
        return BlendFracPoint(
            (s / span).toFloat().coerceIn(0f, 1f),
            (drop / riseMm).toFloat().coerceIn(0f, 1f),
        )
    }
}

/**
 * The [ChamferShape] for a face climbing [riseMm] at [angleDeg] with a stored corner radius
 * [radiusMm], or null when there is no rise or the angle is not usable.
 *
 * The drawn radius is clamped to the largest round the cone can host — its tangent point may
 * reach the cone's large end but never pass it, `R ≤ L / (cosθ·tan(θ/2))`. Clamping the DRAWN
 * round is not rewriting what was typed: the stored radius is what prints.
 */
fun chamferShape(riseMm: Float, angleDeg: Float, radiusMm: Float): ChamferShape? {
    val cone = chamferConeLengthMm(riseMm, angleDeg) ?: return null
    val theta = Math.toRadians(angleDeg.toDouble())
    val maxR = (cone / (cos(theta) * tan(theta / 2.0))).toFloat()
    val r = radiusMm.takeIf { it.isFinite() && it > 0f }?.coerceAtMost(maxR) ?: 0f
    return ChamferShape(angleDeg = angleDeg, riseMm = riseMm, coneLenMm = cone, radiusMm = r)
}

/**
 * Drawn axial width (px/pt) for a blend of true width [trueWidthPx] inside a host run of
 * drawn width [hostWidthPx].
 *
 * A blend is a couple of inches on a shaft that can be twenty-five feet long: at true
 * scale on a compressed sheet it collapses to sub-pixel and the feature is invisible on
 * exactly the drawings that need it. The drawn width therefore takes a floor of
 * [minWidthPx] — display exaggeration in the same posture as the undercut notch depth and
 * the wear trace, and safe here because a blend prints no dimension and no footer row, and
 * an Angle-mode face's callouts print its STORED angle and radius, so no exaggerated number
 * can reach a machinist. The stored length is never touched.
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
