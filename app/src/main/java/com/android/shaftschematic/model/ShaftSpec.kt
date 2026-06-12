// app/src/main/java/com/android/shaftschematic/model/ShaftSpec.kt
package com.android.shaftschematic.model

import kotlinx.serialization.Serializable

/**
 * Root aggregate for a shaft specification.
 *
 * Units: **mm** (millimeters).
 *
 * All axial positions are measured AFT → FWD. Overlaps are permitted; call [validate] for
 * basic bounds checks. Helper methods provide coverage, free length, and max OD summaries.
 *
 * @property overallLengthMm Total shaft length AFT → FWD.
 * @property bodies Constant-diameter cylindrical segments.
 * @property tapers Linear transitions between diameters.
 * @property threads External threaded segments.
 * @property liners Outer sleeves/liners.
 */
@Serializable
data class ShaftSpec(
    val overallLengthMm: Float = 0f,
    val bodies: List<Body> = emptyList(),
    val tapers: List<Taper> = emptyList(),
    val threads: List<Threads> = emptyList(),
    val liners: List<Liner> = emptyList(),
)

/**
 * Basic non-overlap validation: checks non-negative fields and that each segment is within
 * [overallLengthMm]. Does not test for intersections or sequencing constraints.
 */
fun ShaftSpec.validate(): Boolean {
    if (overallLengthMm < 0f) return false
    if (!bodies.all { it.isValid(overallLengthMm) }) return false
    if (!tapers.all { it.isValid(overallLengthMm) }) return false
    if (!threads.all { it.isValid(overallLengthMm) }) return false
    if (!liners.all { it.isValid(overallLengthMm) }) return false
    return true
}

/** Farthest occupied axial end across all components (mm). */
fun ShaftSpec.coverageEndMm(): Float {
    val bodyEnd   = bodies.maxOfOrNull  { it.startFromAftMm + it.lengthMm } ?: 0f
    val taperEnd  = tapers.maxOfOrNull  { it.startFromAftMm + it.lengthMm } ?: 0f
    val linerEnd  = liners.maxOfOrNull  { it.startFromAftMm + it.lengthMm } ?: 0f
    val threadEnd = threads.filter { !it.excludeFromOAL }
                          .maxOfOrNull { it.startFromAftMm + it.lengthMm } ?: 0f
    return maxOf(bodyEnd, taperEnd, linerEnd, threadEnd)
}

/** Remaining free axial distance from coverage end to [overallLengthMm] (≥ 0). */
fun ShaftSpec.freeToEndMm(): Float =
    (overallLengthMm - coverageEndMm()).coerceAtLeast(0f)

/**
 * Returns a copy where every excluded thread's [Threads.startFromAftMm] is snapped to the
 * shaft end declared by [Threads.isAftEnd]:
 *  - AFT end → startFromAftMm = 0
 *  - FWD end → startFromAftMm = overallLengthMm − lengthMm (clamped ≥ 0)
 *
 * Called whenever OAL or the excluded-thread topology changes.
 */
fun ShaftSpec.syncExcludedThreadPositions(): ShaftSpec {
    val oal = overallLengthMm
    val synced = threads.map { th ->
        if (!th.excludeFromOAL) th
        else {
            val newStart = if (th.isAftEnd) 0f else (oal - th.lengthMm).coerceAtLeast(0f)
            if (th.startFromAftMm == newStart) th else th.copy(startFromAftMm = newStart)
        }
    }
    return if (synced == threads) this else copy(threads = synced)
}

/** Maximum outer diameter observed across all components (mm). */
fun ShaftSpec.maxOuterDiaMm(): Float {
    var maxDia = 0f
    bodies.forEach  { maxDia = maxOf(maxDia, it.diaMm) }
    tapers.forEach  { maxDia = maxOf(maxDia, it.maxDiaMm) }
    threads.forEach { maxDia = maxOf(maxDia, it.majorDiaMm) }
    liners.forEach  { maxDia = maxOf(maxDia, it.odMm) }
    return maxDia
}

/**
 * Back-compat normalization:
 * returns a copy where each [Threads] has both [Threads.pitchMm] and [Threads.tpi]
 * populated when either one is present and > 0. Call this after JSON decode.
 */
fun ShaftSpec.normalized(): ShaftSpec =
    copy(
        threads = threads.map { it.normalized() },
        liners = liners.map { it.normalized() }
    )
