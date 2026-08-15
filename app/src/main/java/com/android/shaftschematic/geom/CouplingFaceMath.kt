package com.android.shaftschematic.geom

/**
 * CouplingFaceMath — pure layout for the **coupling end view** printed on the runout sheets.
 *
 * The shops hand-draw the coupling looking forward: an outer circle at the coupling OD, an
 * inner circle at the pilot (register) bore with its keyseat at 12 o'clock, and the pilot
 * runout written inside it. This file owns every ratio in that picture, so the composer and
 * the unit test read the same numbers and the drawing can be reasoned about without a Canvas.
 *
 * No Android imports — same posture as `RunoutReadingMath`/`WearPitMath`: geometry is shared,
 * `pdf` may depend on `geom`, never the reverse.
 *
 * All radii are in the caller's units (the composer works in PDF points); angles are degrees
 * in the screen convention, `-90` = 12 o'clock, increasing clockwise.
 */

/** Pilot (register) bore radius as a fraction of the coupling OD radius. */
const val COUPLING_PILOT_FRAC = 0.44f

/**
 * Coupling keyseat at 12 o'clock, as fractions of the pilot radius: an arc gap of half-width
 * [COUPLING_KEYWAY_SLOT_HALF_FRAC] in the pilot circle with two walls running **outward** to
 * [COUPLING_KEYWAY_DEPTH_FRAC] past the rim, closed by a flat cap — a small box sitting on top
 * of the bore, open into it.
 *
 * The direction is deliberately **opposite** the runout bubble glyph's inward slot: the key
 * sits between shaft and hub, so the shaft carries a keyway cut into it while the coupling
 * carries a keyseat cut into the surrounding hub material. Do not unify the two constructions.
 *
 * The depth is modest enough that the cap always clears the bolt circle's holes
 * (`pilotR + depth < boltCircleR − boltHoleR`, pinned by `CouplingFaceMathTest`).
 */
const val COUPLING_KEYWAY_SLOT_HALF_FRAC = 0.22f
const val COUPLING_KEYWAY_DEPTH_FRAC = 0.25f

/** Bolt hole radius as a fraction of the coupling OD radius. */
const val COUPLING_BOLT_HOLE_FRAC = 0.10f

/**
 * Every circle in the coupling end view, resolved from the one input the caller chooses (the
 * drawn OD radius) plus the bolt count.
 *
 * @property outerR Coupling OD radius.
 * @property pilotR Pilot/register bore radius.
 * @property keywaySlotHalf Half-width of the keyseat box straddling the pilot rim at 12 o'clock.
 * @property keywayDepth How far that box stands **outward** past the pilot rim (into the hub).
 * @property boltCircleR Radius of the (dashed) bolt circle — midway between pilot and OD.
 * @property boltHoleR Radius of one bolt hole.
 * @property boltAngleDegs Bolt hole angles, empty when the coupling has no bolt data.
 */
data class CouplingFaceLayout(
    val outerR: Float,
    val pilotR: Float,
    val keywaySlotHalf: Float,
    val keywayDepth: Float,
    val boltCircleR: Float,
    val boltHoleR: Float,
    val boltAngleDegs: List<Float>,
)

/**
 * Lay out a coupling end view of drawn radius [outerR] with [boltCount] bolt holes.
 *
 * Bolts are evenly pitched and rotated by **half a pitch** off 12 o'clock, so no hole ever
 * lands behind the keyseat — with the box spanning roughly ±13° of the pilot rim, the nearest
 * bolt sits a half pitch (`180°/count`) away, clear at every count a coupling realistically
 * carries. A [boltCount] below 1 yields no bolt circle at all: the plain two-circle face,
 * which is the hand-sketch minimum.
 */
fun couplingFaceLayout(outerR: Float, boltCount: Int): CouplingFaceLayout {
    val r = outerR.coerceAtLeast(0f)
    val pilotR = r * COUPLING_PILOT_FRAC
    val angles = if (boltCount < 1) {
        emptyList()
    } else {
        val pitch = 360f / boltCount
        List(boltCount) { i -> -90f + pitch * (i + 0.5f) }
    }
    return CouplingFaceLayout(
        outerR = r,
        pilotR = pilotR,
        keywaySlotHalf = pilotR * COUPLING_KEYWAY_SLOT_HALF_FRAC,
        keywayDepth = pilotR * COUPLING_KEYWAY_DEPTH_FRAC,
        boltCircleR = (r + pilotR) / 2f,
        boltHoleR = r * COUPLING_BOLT_HOLE_FRAC,
        boltAngleDegs = angles,
    )
}
