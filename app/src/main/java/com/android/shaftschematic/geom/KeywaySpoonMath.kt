// file: app/src/main/java/com/android/shaftschematic/geom/KeywaySpoonMath.kt
package com.android.shaftschematic.geom

import kotlin.math.asin
import kotlin.math.sqrt

/**
 * "Spooned" keyway pure math — the closed (LET) end of an open keyway opens into an enlarged
 * **bowl** that merges with the slot (a keyhole). The straight slot walls run tangent into the
 * bowl and the SET-facing mouth wedge is left open, so the slot and circle read as one void.
 * This matches the shop/CAD "spooned" convention (a larger circle drawn at the keyway end).
 *
 * The bowl is an ELLIPSE in drawn space with its own y-semi ([KeywaySpoonBowl.ry]): the x-semi
 * rides the slot's AXIAL half-width, and the y-semi is the slot's drawn half-HEIGHT plus the
 * bowl's axial poke past the LET tip — so the drawn clearance around the slot reads uniform.
 * Deriving the y-semi by stretching a shaft-space circle instead gave each axis its own scale's
 * clearance, and on a compressed sheet (diameter scale far above the axial scale) the bowl drew
 * tall, with several times more daylight above the slot walls than past the mill arc
 * (on-device report).
 *
 * Kept as small, dependency-free top-level functions (no Compose/Android imports, `geom`
 * package) so the geometry is directly unit-testable in a plain JVM test — the same posture as
 * `geom/WearPitMath.kt` and `geom/RunoutReadingMath.kt`.
 *
 * The bowl is drawn **identically** in both draw sites (they must stay in lockstep):
 * - canvas: `ShaftRenderer.drawKeywaySlot` (Compose `DrawScope`),
 * - PDF: `ShaftPdfComposer.drawKeywaySlotPdf` (`android.graphics.Canvas`/`Paint`).
 *
 * Only meaningful for **open** keyways (offset ≈ 0); floating keyways ignore the spoon flag.
 */

/**
 * Bowl diameter as a multiple of the keyway (slot) width — the AXIAL term. The bowl reads as a
 * prominent round pocket merged with the slot end, matching the shop sketch. Drawing constant,
 * tunable here.
 */
const val SPOON_BOWL_WIDTH_RATIO = 2.4f

/**
 * How far the bowl centre sits from the LET tip toward SET, as a fraction of the bowl radius —
 * controls where the mill semicircle lands inside the circle:
 * - `0f` → bowl far edge is tangent at the LET tip (mill end reaches the circle's far edge)
 * - `1f` → bowl is centred on the LET tip (mill end runs halfway through the circle)
 * - `0.5f` (default) → halfway between the two: the mill end runs ~¾ through and the circle
 *   pokes past the tip by half a radius.
 * The poke-past distance (`radius × shiftRatio`) is also the bowl's drawn CLEARANCE above and
 * below the slot walls — one number, so the spoon reads equally tight all around.
 * Drawing constant, tunable here.
 */
const val SPOON_BOWL_LET_SHIFT_RATIO = 0.5f

/**
 * Resolved spoon-bowl geometry, in the caller's drawing unit (px for canvas, pt for PDF).
 *
 * @property cx          bowl centre X (on the centreline)
 * @property radius      bowl x-semi (axial half-extent)
 * @property ry          bowl y-semi (transverse half-extent) — draw the oval `cy ± ry`
 * @property wallEndX    X where the straight slot walls meet the bowl (their LET-side terminus)
 * @property arcStartDeg drawArc start angle — degrees, 0° = 3 o'clock, clockwise (y-down)
 * @property arcSweepDeg drawArc sweep — the major arc around the far side, excluding the mouth wedge
 */
data class KeywaySpoonBowl(
    val cx: Float,
    val radius: Float,
    val ry: Float,
    val wallEndX: Float,
    val arcStartDeg: Float,
    val arcSweepDeg: Float,
)

/**
 * Compute the spoon bowl at the closed (LET) end of a keyway slot.
 *
 * The bowl centre is offset from the keyway's LET tip ([letX]) toward SET by
 * [shiftRatio] × radius (see [SPOON_BOWL_LET_SHIFT_RATIO]), which sets how far the mill
 * semicircle runs into the circle and keeps the two rounded ends clear. The y-semi is
 * [halfH] + that same poke-past distance, so the drawn clearance above the walls matches the
 * drawn clearance past the mill arc whatever the sheet's two scales are.
 *
 * `drawArc` sweeps a PARAMETRIC angle, so the wall-tangent angle derives from the drawn ratio
 * `halfH / ry` and holds exactly on the drawn ellipse.
 *
 * @param letX       far/closed end of the slot (kwLetX) — the LET tip the bowl is offset from
 * @param dir        +1 when the slot extends rightward (LET to the right of SET), else −1
 * @param halfW      half the slot width, AXIAL term (same unit as [letX])
 * @param halfH      half the slot's drawn width, TRANSVERSE term (the diameter-scale half-height)
 * @param widthRatio bowl x-diameter ÷ slot width
 * @param shiftRatio bowl-centre offset from the LET tip toward SET, ÷ bowl radius
 */
fun keywaySpoonBowl(
    letX: Float,
    dir: Float,
    halfW: Float,
    halfH: Float,
    widthRatio: Float = SPOON_BOWL_WIDTH_RATIO,
    shiftRatio: Float = SPOON_BOWL_LET_SHIFT_RATIO,
): KeywaySpoonBowl {
    val radius = halfW * widthRatio
    // Centre sits `shiftRatio` radii back from the LET tip toward SET (shiftRatio=1 → centred on it).
    val cx = letX - dir * radius * (1f - shiftRatio)
    // Uniform drawn clearance: the walls' daylight equals the bowl's poke past the LET tip.
    val ry = halfH + radius * shiftRatio
    // The slot walls (at cy ± halfH) meet the ellipse where sin(t) = halfH / ry.
    val wallFrac = if (ry > 0f) (halfH / ry).coerceIn(0f, 1f) else 1f
    val wallEndX = cx - dir * radius * sqrt((1f - wallFrac * wallFrac).coerceAtLeast(0f))
    val phi = Math.toDegrees(asin(wallFrac.toDouble())).toFloat()
    // The mouth (slot opening) points from the bowl back toward SET: 180° when dir > 0, else 0°.
    val mouthDeg = if (dir > 0f) 180f else 0f
    return KeywaySpoonBowl(
        cx = cx,
        radius = radius,
        ry = ry,
        wallEndX = wallEndX,
        arcStartDeg = mouthDeg + phi,
        arcSweepDeg = 360f - 2f * phi,
    )
}
