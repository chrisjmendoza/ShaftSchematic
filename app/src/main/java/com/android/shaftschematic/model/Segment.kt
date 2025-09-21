package com.android.shaftschematic.model

/**
 * Common contract for linear shaft segments measured from the **AFT** end.
 *
 * All distances are in **millimeters** (mm).
 *
 * - [startFromAftMm]: distance from the AFT face toward the FWD direction (>= 0).
 * - [lengthMm]: axial length of the segment (>= 0).
 */
interface Segment {
    val id: String
    val startFromAftMm: Float
    val lengthMm: Float
}

/* ────────────────────────────────────────────────────────────────────────────
 * Validation helpers (non-throwing) for basic geometry constraints
 * ──────────────────────────────────────────────────────────────────────────── */

/** The segment fits within [overallLengthMm] and has non-negative fields. */
fun Segment.isWithin(overallLengthMm: Float): Boolean {
    if (startFromAftMm < 0f || lengthMm < 0f) return false
    return startFromAftMm + lengthMm <= overallLengthMm + 1e-3f
}

/** End position from AFT in mm (start + length). */
val Segment.endFromAftMm: Float get() = startFromAftMm + lengthMm
