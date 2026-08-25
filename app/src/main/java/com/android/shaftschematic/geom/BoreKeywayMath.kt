// file: app/src/main/java/com/android/shaftschematic/geom/BoreKeywayMath.kt
package com.android.shaftschematic.geom

import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * BoreKeywayMath — target edge depth for a rough keyway cutter in a circular bore.
 *
 * A keyway cut into the inside surface of a bore is specified by width and by depth measured
 * from the bore surface AT THE OUTER EDGES of the keyway down to the flat bottom. Because the
 * bore surface curves, a narrower roughing cutter's edges sit closer to the keyway centerline
 * where the surface is lower — so when both cutters' flat bottoms reach the same finished
 * plane, the narrower cutter always measures a SMALLER edge depth.
 *
 * The whole solve reduces to one exact expression (the bore-radius terms cancel):
 *
 *     depth_current = depth_final + √(R² − (w_final/2)²) − √(R² − (w_current/2)²)
 *
 * Everything here is unit-agnostic — the geometry holds in any unit as long as every input
 * uses the same one. This is a shop-floor measuring aid for the HUB side of a coupling: it
 * reads nothing from the shaft model and stores nothing, and it deliberately does NOT check
 * whether the keyway is plausible for the bore (measuring aid, not a design validator).
 */

/** Why a solve produced no depth. Order = check order; the first failure wins. */
enum class BoreKeywayIssue {
    /** One or more of the four inputs is ≤ 0. */
    NON_POSITIVE_INPUT,

    /** The finished keyway's half-width reaches the bore radius — no bore surface at its edge. */
    FINAL_WIDTH_EXCEEDS_BORE,

    /**
     * The cutter is wider than the finished keyway. A roughing cutter cuts INSIDE the finished
     * profile, so a wider one would leave the keyway oversize — there is no target depth to
     * measure, because reaching the plane with it is already the wrong cut. The bare geometry
     * happily returns a number here, which is exactly why this check exists (on-device report:
     * a 2" cutter still calculated against a 1½" keyway after the job was switched).
     */
    CUTTER_WIDER_THAN_KEYWAY,

    /**
     * The finished plane sits at or above the bore surface at this cutter's edges — the cutter
     * never breaks the surface at its edges when its bottom reaches the plane, so there is no
     * edge depth to measure. Happens when the finished depth is small relative to the curvature
     * difference (a very narrow cutter in a small bore).
     */
    CUTTER_NEVER_BREAKS_SURFACE,
}

/**
 * Slack on the cutter-vs-keyway comparison, so a width typed two ways (`1.5` and `1 1/2`)
 * never reads as wider than itself. Far below any real cutter tolerance.
 */
private const val WIDTH_EPS = 1e-9

/** One solved cutter: exactly one of [depth] / [issue] is non-null. */
data class BoreKeywayResult(
    val depth: Double? = null,
    val issue: BoreKeywayIssue? = null,
)

/**
 * How far the bore surface rises above the bore's lowest point at [halfWidth] off the keyway
 * centerline. Caller guarantees `halfWidth < radius`.
 */
fun boreSurfaceRise(radius: Double, halfWidth: Double): Double =
    radius - sqrt(radius * radius - halfWidth * halfWidth)

/**
 * Validate the finished keyway against the bore, independent of any cutter — null when the
 * three base inputs can host a solve. Split out so the entry surface can mark the offending
 * FIELD rather than waiting for a cutter to be typed before anything reads wrong.
 */
fun validateBoreKeyway(boreDia: Double, finalWidth: Double, finalDepth: Double): BoreKeywayIssue? {
    if (boreDia <= 0.0 || finalWidth <= 0.0 || finalDepth <= 0.0) {
        return BoreKeywayIssue.NON_POSITIVE_INPUT
    }
    if (finalWidth / 2.0 >= boreDia / 2.0) return BoreKeywayIssue.FINAL_WIDTH_EXCEEDS_BORE
    return null
}

/**
 * The depth to measure at the OUTER EDGES of a [cutterWidth]-wide cutter so its flat bottom
 * lands on the plane of the finished keyway ([finalWidth] wide, [finalDepth] deep at its
 * edges) in a bore of [boreDia]. All inputs in one shared unit; the result is in that unit.
 *
 * A cutter equal in width to the finished keyway returns [finalDepth] (to FP tolerance — the
 * sqrt terms are identical but the intermediate sum re-rounds); a narrower one always returns
 * less (√(R²−x²) is strictly decreasing in x). A WIDER one is rejected
 * ([BoreKeywayIssue.CUTTER_WIDER_THAN_KEYWAY]) rather than solved: the geometry would answer,
 * but the cut it describes is wrong.
 *
 * That bound also removes any need to check the cutter against the bore: a cutter can be no
 * wider than the keyway, and the keyway is already bounded inside the bore, so the radicand
 * for the cutter term is positive by construction.
 */
fun roughCutterTargetDepth(
    boreDia: Double,
    finalWidth: Double,
    finalDepth: Double,
    cutterWidth: Double,
): BoreKeywayResult {
    if (cutterWidth <= 0.0) return BoreKeywayResult(issue = BoreKeywayIssue.NON_POSITIVE_INPUT)
    validateBoreKeyway(boreDia, finalWidth, finalDepth)?.let { return BoreKeywayResult(issue = it) }
    val r = boreDia / 2.0
    if (cutterWidth > finalWidth + WIDTH_EPS) {
        return BoreKeywayResult(issue = BoreKeywayIssue.CUTTER_WIDER_THAN_KEYWAY)
    }

    val depth = finalDepth +
        sqrt(r * r - (finalWidth / 2.0) * (finalWidth / 2.0)) -
        sqrt(r * r - (cutterWidth / 2.0) * (cutterWidth / 2.0))
    if (depth <= 0.0) return BoreKeywayResult(issue = BoreKeywayIssue.CUTTER_NEVER_BREAKS_SURFACE)
    return BoreKeywayResult(depth = depth)
}

/**
 * The nearest 64th to [value], as a reduced-fraction label for checking with a machinist's
 * scale — "35/64", "1/2", "1 13/64", "2". Display companion only: the decimal result stays
 * the authoritative machining value and this label must never replace it.
 *
 * Null for values that round to zero or below — a scale reading below 1/128 is noise.
 */
fun nearestSixtyFourthLabel(value: Double): String? {
    val total = (value * 64.0).roundToInt()
    if (total <= 0) return null
    val whole = total / 64
    var num = total % 64
    if (num == 0) return whole.toString()
    var den = 64
    while (num % 2 == 0) { num /= 2; den /= 2 }
    val frac = "$num/$den"
    return if (whole > 0) "$whole $frac" else frac
}
