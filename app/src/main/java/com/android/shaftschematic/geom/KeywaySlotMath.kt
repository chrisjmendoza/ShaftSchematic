// file: app/src/main/java/com/android/shaftschematic/geom/KeywaySlotMath.kt
package com.android.shaftschematic.geom

/**
 * Plan-view keyway slot draw math — the part of the slot that is a TRANSVERSE dimension.
 *
 * A sheet carries two scales: the diameter scale (vertical — the drawn shaft height, what the
 * "Shaft height" slider moves) and the compressed axial map (horizontal). A keyway's WIDTH is a
 * transverse dimension, so it rides the DIAMETER scale and stays proportional to the drawn shaft
 * at every height; its offset and length ride the axial map. Sizing the width off the axial
 * scale instead pins it to the page width, and raising the height then grows the shaft while the
 * keyway stays put (on-device report).
 *
 * **Every round part of the slot is an ELLIPSE, x from the axial scale and y from the diameter
 * scale** — the mill arcs and the spoon bowl alike. Drawing them as true circles at the
 * transverse scale instead makes their AXIAL extent grow with the height slider while the slot's
 * length stays page-bound, and the spoon bowl (2.4× the keyway width) blows up until it swallows
 * its own slot (on-device report). An ellipse costs nothing to draw: `drawArc` sweeps a
 * PARAMETRIC angle on its oval, and scaling y alone leaves every angle — the wall tangent
 * included — exactly where the circle put it, so the shared bowl math needs no anisotropic term.
 *
 * Pure and android-free (`geom` posture) so both draw sites build the slot from one place:
 * - canvas: `ShaftRenderer.drawKeywaySlot`,
 * - PDF: `ShaftPdfComposer.drawKeywaySlotPdf`.
 */

/** A drawn slot never spans more than this fraction of its host's drawn diameter. */
const val MAX_KEYWAY_FRAC_OF_HOST_DIA = 0.4f

/** Minimum drawn keyway width so the slot still reads on paper (PDF points). */
const val MIN_KEYWAY_WIDTH_PT = 3f

/** Minimum drawn keyway width on the preview canvas (px at the canvas's own scale). */
const val MIN_KEYWAY_WIDTH_PX = 4f

/**
 * Stroke widths the slot must span before the floor stops raising it. Two is the legibility
 * criterion exactly: walls centred at ±width/2 leave `width − stroke` of daylight between their
 * inner edges, so a slot two strokes wide reads as a slot with one full line width of white in
 * it. Demanding more than that starts lifting ordinary shafts off true scale — see
 * `KeywayWidthFidelityTest`, which pins where the floor may and may not reach.
 */
const val KEYWAY_MIN_WIDTH_STROKES = 2.0f

/**
 * Drawn half-width (px/pt) of a keyway slot whose true half-width AT THE DIAMETER SCALE is
 * [trueHalfWidthPx], inside a host of drawn radius [hostRadiusPx].
 *
 * **The true width is never shrunk.** A keyway is a fifth of its shaft, not a couple of inches
 * on a twenty-five foot one, so unlike the blend width it is legible at true scale on any
 * drawing that isn't tiny: the normal result here is exactly [trueHalfWidthPx], and the slot is
 * then proportional to its host to the pixel.
 *
 * The only exaggeration is a FLOOR for the corner where the drawn shaft is small enough that a
 * true slot would close up — [minWidthPx], or [KEYWAY_MIN_WIDTH_STROKES] × [strokeWidthPx] when
 * the line-thickness slider is heavy enough that thinner walls would merge into one line. Safe
 * in the blend-floor posture: a keyway prints no dimension rail, only footer text built from the
 * stored W × D × L, so no exaggerated number can reach a machinist.
 *
 * Only that floor yields to [MAX_KEYWAY_FRAC_OF_HOST_DIA] of the host's drawn diameter, so a
 * small shaft can't be swallowed by its own keyway. Clamping the TRUE width there instead would
 * silently narrow an authored keyway on a stubby taper end, which is the one thing this must
 * not do; the only bound on a true width is the shaft itself.
 */
fun drawnKeywayHalfWidthPx(
    trueHalfWidthPx: Float,
    hostRadiusPx: Float,
    minWidthPx: Float,
    strokeWidthPx: Float,
): Float {
    if (trueHalfWidthPx <= 0f) return 0f
    val floorHalf = maxOf(minWidthPx, strokeWidthPx * KEYWAY_MIN_WIDTH_STROKES) / 2f
    if (hostRadiusPx <= 0f) return maxOf(trueHalfWidthPx, floorHalf)
    return maxOf(trueHalfWidthPx, minOf(floorHalf, hostRadiusPx * MAX_KEYWAY_FRAC_OF_HOST_DIA))
        .coerceAtMost(hostRadiusPx)
}

/**
 * Minimum drawn axial length for a slot whose end arcs reach [halfWidthPx] inward along the axis —
 * one arc when the slot is open at its referenced face (that face closes the other end), two when
 * it floats. Pass the AXIAL half-width: the arcs are ellipses, and this is the x term.
 *
 * Guards authored geometry, not the scale split: a keyway shorter than half its own width would
 * otherwise run its arcs past its straight walls and invert the slot.
 */
fun minKeywaySlotLenPx(halfWidthPx: Float, openEnd: Boolean): Float =
    halfWidthPx * (if (openEnd) 1f else 2f)
