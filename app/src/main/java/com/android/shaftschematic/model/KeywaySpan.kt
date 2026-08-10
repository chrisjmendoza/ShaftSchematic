package com.android.shaftschematic.model

/**
 * Absolute AFT-origin axial span of a keyway slot.
 *
 * Units: **mm** (millimeters).
 *
 * [loMm] is the aft-most edge and [hiMm] the fwd-most edge (`loMm <= hiMm`), regardless of
 * which end face the keyway is referenced from — [Body.keywayAbsSpanMm] and
 * [Taper.keywayAbsSpanMm] resolve the AFT/FWD/SET reference before constructing the span.
 * Destructures in that order: `val (lo, hi) = span`.
 */
data class KeywaySpan(val loMm: Float, val hiMm: Float) {
    /** Axial center of the slot, mm from the AFT face. */
    val centerMm: Float get() = (loMm + hiMm) * 0.5f
}
