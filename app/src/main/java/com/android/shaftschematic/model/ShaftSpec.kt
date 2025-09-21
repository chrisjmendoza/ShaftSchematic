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
    val threads: List<ThreadSpec> = emptyList(),

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
fun ShaftSpec.coverageEndMm(): Float {
    var end = 0f
    bodies.forEach  { end = maxOf(end, it.endFromAftMm) }
    tapers.forEach  { end = maxOf(end, it.endFromAftMm) }
    threads.forEach { end = maxOf(end, it.endFromAftMm) }
    liners.forEach  { end = maxOf(end, it.endFromAftMm) }
    return end
}

/** Maximum outer diameter found across all components (mm). */
fun ShaftSpec.maxOuterDiaMm(): Float {
    var maxDia = 0f
    bodies.forEach  { maxDia = maxOf(maxDia, it.diaMm) }
    tapers.forEach  { maxDia = maxOf(maxDia, it.maxDiaMm) }
    threads.forEach { maxDia = maxOf(maxDia, it.majorDiaMm) }
    liners.forEach  { maxDia = maxOf(maxDia, it.odMm) }
    return maxDia
}
