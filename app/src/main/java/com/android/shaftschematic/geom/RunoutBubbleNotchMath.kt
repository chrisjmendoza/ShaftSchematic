// file: app/src/main/java/com/android/shaftschematic/geom/RunoutBubbleNotchMath.kt
package com.android.shaftschematic.geom

import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.datumKeywayHostId
import com.android.shaftschematic.model.maxDiaMm
import kotlin.math.asin

/**
 * Runout bubble keyway notch — the 12-o'clock reference marker every runout bubble carries,
 * sized at the proportion of the SHAFT'S OWN keyway (on-device request: the notch read as far
 * larger than a real keyway).
 *
 * The bubble circle is read as the shaft's cross-section at bubble scale, so a keyway of width W
 * and depth D on a host of diameter Ø draws as a slot whose full width is the chord W on a circle
 * of diameter Ø, `2r · (W/Ø)`, and whose depth from the rim is `2r · (D/Ø)` — the way the shop
 * measures keyseat depth. ONE proportion per sheet ([sheetKeywayProportion]): the notch is the
 * reference marker, not a per-station feature, so every bubble on a sheet shows the same notch,
 * including a station on a component with no keyway of its own.
 *
 * Pure, no Android or Compose imports: both bubble draw sites — `RunoutRoute.drawRunoutBubbleRing`
 * (canvas) and `RunoutPdfComposer.drawRunoutBubbleRingPdf` (PDF) — draw from [bubbleKeywayNotch],
 * so the editor preview and the printed sheet cannot disagree. The coupling end view's hub keyseat
 * (`CouplingFaceMath`) is a different object and does not read this file.
 */

/** Fallback W/Ø when the shaft carries no keyway: the standard square key, W = Ø/4. */
const val BUBBLE_NOTCH_FALLBACK_WIDTH_OVER_DIA = 0.25f

/** Fallback D/Ø when the shaft carries no keyway: the standard square key, D = Ø/8. */
const val BUBBLE_NOTCH_FALLBACK_DEPTH_OVER_DIA = 0.125f

/**
 * Half-width floor in ring strokes. Below 1.5 strokes the two walls leave no daylight between
 * them and the slot reads as one thick line.
 */
const val BUBBLE_NOTCH_MIN_HALF_STROKES = 1.5f

/** Half-width floor as a fraction of the bubble radius, for a ring drawn with a hairline stroke. */
const val BUBBLE_NOTCH_MIN_HALF_FRAC_OF_R = 0.06f

/**
 * Half-width cap as a fraction of the bubble radius. A wider mouth eats so much of the top arc
 * that the ring stops reading as a circle.
 */
const val BUBBLE_NOTCH_MAX_HALF_FRAC_OF_R = 0.35f

/**
 * Depth floor in ring strokes. Three strokes leave about two strokes of daylight between the
 * slot floor and the rim ends; at two (one stroke of daylight) the floor line merges into the
 * broken rim on the default 23 pt sheet bubble and the notch reads as a nub on the ring rather
 * than a slot. Most real keyways (2·D/Ø ≈ 0.13–0.15 r) fall under this floor at the default
 * bubble scale, so the printed depth is the floor there and grows to the true proportion on
 * larger bubbles.
 */
const val BUBBLE_NOTCH_MIN_DEPTH_STROKES = 3f

/**
 * Depth cap as a fraction of the bubble radius. The TIR value sits centred in the bubble at
 * 0.60 r tall; a deeper slot runs its floor into the value.
 */
const val BUBBLE_NOTCH_MAX_DEPTH_FRAC_OF_R = 0.50f

/**
 * One bubble's keyway notch, in the draw site's own units (pt on the sheet, px on the canvas).
 *
 * @property slotHalf half the slot's width, measured from the bubble's vertical centreline.
 * @property slotDepth slot depth from the points where its walls meet the rim.
 * @property gapDeg half-angle of the rim gap the slot mouth breaks, measured from 12 o'clock —
 *   `asin(slotHalf / r)`, so the walls start exactly on the rim.
 */
data class BubbleKeywayNotch(
    val slotHalf: Float,
    val slotDepth: Float,
    val gapDeg: Float,
)

/**
 * The notch for a bubble of radius [r] whose ring strokes at [stroke], at the keyway proportion
 * [widthOverDia] (W/Ø) and [depthOverDia] (D/Ø) — normally [sheetKeywayProportion].
 *
 * Proportion first, legibility clamps after:
 * - `slotHalf = r · W/Ø`, clamped to `[max(1.5 · stroke, 0.06 r), 0.35 r]`;
 * - `slotDepth = 2r · D/Ø`, clamped to `[3 · stroke, 0.50 r]`.
 *
 * The floors are safe for the same reason the blend-width floor is: the notch prints no number,
 * so an enlarged notch can never reach a machinist as a dimension. A cap wins over a floor when
 * the two cross (a tiny bubble with a heavy stroke), so the ring always survives.
 */
fun bubbleKeywayNotch(
    r: Float,
    widthOverDia: Float,
    depthOverDia: Float,
    stroke: Float,
): BubbleKeywayNotch {
    if (r <= 0f) return BubbleKeywayNotch(0f, 0f, 0f)
    val halfFloor = maxOf(BUBBLE_NOTCH_MIN_HALF_STROKES * stroke, BUBBLE_NOTCH_MIN_HALF_FRAC_OF_R * r)
    val halfCap = BUBBLE_NOTCH_MAX_HALF_FRAC_OF_R * r
    val slotHalf = minOf(maxOf(r * widthOverDia, halfFloor), halfCap)

    val depthFloor = BUBBLE_NOTCH_MIN_DEPTH_STROKES * stroke
    val depthCap = BUBBLE_NOTCH_MAX_DEPTH_FRAC_OF_R * r
    val slotDepth = minOf(maxOf(2f * r * depthOverDia, depthFloor), depthCap)

    val gapDeg = Math.toDegrees(asin((slotHalf / r).coerceIn(0f, 1f).toDouble())).toFloat()
    return BubbleKeywayNotch(slotHalf, slotDepth, gapDeg)
}

/**
 * The (W/Ø, D/Ø) proportion every bubble on a sheet of [spec] draws its notch at.
 *
 * The keyway is the shaft's datum keyway — the AFT-most one ([datumKeywayHostId], the same pick
 * the clocking notes make). The host Ø is a taper's LARGE-end Ø (the key sits at the large end)
 * or a body's Ø. A shaft with no keyway, or a host with no Ø yet, takes the standard square-key
 * proportion ([BUBBLE_NOTCH_FALLBACK_WIDTH_OVER_DIA], [BUBBLE_NOTCH_FALLBACK_DEPTH_OVER_DIA]).
 *
 * Draw-only: reads the stored keyway, never writes it.
 */
fun sheetKeywayProportion(spec: ShaftSpec): Pair<Float, Float> {
    val fallback = BUBBLE_NOTCH_FALLBACK_WIDTH_OVER_DIA to BUBBLE_NOTCH_FALLBACK_DEPTH_OVER_DIA
    val hostId = spec.datumKeywayHostId() ?: return fallback
    val (widthMm, depthMm, hostDiaMm) =
        spec.tapers.firstOrNull { it.id == hostId }
            ?.let { Triple(it.keywayWidthMm, it.keywayDepthMm, it.maxDiaMm) }
            ?: spec.bodies.firstOrNull { it.id == hostId }
                ?.let { Triple(it.keywayWidthMm, it.keywayDepthMm, it.diaMm) }
            ?: return fallback
    if (hostDiaMm <= 0f) return fallback
    return (widthMm / hostDiaMm) to (depthMm / hostDiaMm)
}
