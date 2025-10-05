package com.android.shaftschematic.model

import kotlinx.serialization.Serializable

/**
 * Root aggregate for a shaft specification.
 *
 * All geometric values are stored in **millimeters (mm)**.
 * Components are positioned by **startFromAftMm** measured from the AFT end
 * toward the FWD direction. No assumptions are made about overlap; consumers
 * can call [validate] to check bounds and simple invariants.
 */
@Serializable
data class ShaftSpec(
    /** Overall shaft length measured AFT→FWD in mm. */
    val overallLengthMm: Float = 0f,

    /** Constant-diameter cylindrical segments. */
    val bodies: List<Body> = emptyList(),

    /** Linear transitions between diameters. */
    val tapers: List<Taper> = emptyList(),

    /** External threaded segments. */
    val threads: List<Threads> = emptyList(),

    /** Outer sleeves/liners. */
    val liners: List<Liner> = emptyList(),
)

/* ────────────────────────────────────────────────────────────────────────────
 * Aggregate helpers (optional, non-throwing)
 * ──────────────────────────────────────────────────────────────────────────── */

/** Quick bounds/negatives check for all segments; does not check overlaps. */
fun ShaftSpec.validate(): Boolean {
    if (overallLengthMm < 0f) return false
    if (!bodies.all { it.isValid(overallLengthMm) }) return false
    if (!tapers.all { it.isValid(overallLengthMm) }) return false
    if (!threads.all { it.isValid(overallLengthMm) }) return false
    if (!liners.all { it.isValid(overallLengthMm) }) return false
    return true
}

/** End-of-coverage position (max of start+length across all segments). */
// === SINGLE SOURCE OF TRUTH ===
// Farther occupied end across ALL components (mm).
fun ShaftSpec.coverageEndMm(): Float {
    val bodyEnd   = bodies.maxOfOrNull  { it.startFromAftMm + it.lengthMm } ?: 0f
    val taperEnd  = tapers.maxOfOrNull  { it.startFromAftMm + it.lengthMm } ?: 0f
    val linerEnd  = liners.maxOfOrNull  { it.startFromAftMm + it.lengthMm } ?: 0f
    val threadEnd = threads.maxOfOrNull { it.startFromAftMm + it.lengthMm } ?: 0f
    return maxOf(bodyEnd, taperEnd, linerEnd, threadEnd)
}
fun ShaftSpec.freeToEndMm(): Float =
    (overallLengthMm - coverageEndMm()).coerceAtLeast(0f)

/** Maximum outer diameter found across all components (mm). */
fun ShaftSpec.maxOuterDiaMm(): Float {
    var maxDia = 0f
    bodies.forEach  { maxDia = maxOf(maxDia, it.diaMm) }
    tapers.forEach  { maxDia = maxOf(maxDia, it.maxDiaMm) }
    threads.forEach { maxDia = maxOf(maxDia, it.majorDiaMm) }
    liners.forEach  { maxDia = maxOf(maxDia, it.odMm) }
    return maxDia
}
