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
 * @property couplerBoltSlots Radial muff-coupler bolt cutouts (reference features; never
 *           affect OAL/coverage). Optional — defaults empty for back-compat.
 * @property keyways180Apart Annotation that the shaft's keyways are clocked 180° apart
 *           from each other (e.g. body keyway opposite the taper keyway on an
 *           intermediate shaft). Pure drawing note — no geometric effect. Only
 *           meaningful when the shaft has ≥ 2 keyways. Defaults false for back-compat.
 */
@Serializable
data class ShaftSpec(
    val overallLengthMm: Float = 0f,
    val bodies: List<Body> = emptyList(),
    val tapers: List<Taper> = emptyList(),
    val threads: List<Threads> = emptyList(),
    val liners: List<Liner> = emptyList(),
    val couplerBoltSlots: List<CouplerBoltSlot> = emptyList(),
    val keyways180Apart: Boolean = false,
)

/** Number of keyways defined across all components (tapers + bodies). */
fun ShaftSpec.keywayCount(): Int =
    tapers.count { it.hasKeyway } + bodies.count { it.hasKeyway }

/**
 * Host component IDs whose keyway should render as **hidden lines** (far-side, dashed).
 *
 * Applies only when [keyways180Apart] is set and the shaft has ≥ 2 keyways. The keyway
 * nearest the AFT face (smallest absolute center) is the shop measurement datum, so it
 * stays solid (near side); every other keyway is on the far side and renders hidden.
 * Empty otherwise — no clocking note, or too few keyways to be meaningful.
 */
fun ShaftSpec.hiddenKeywayHostIds(): Set<String> {
    if (!keyways180Apart) return emptySet()
    val centers = buildList {
        tapers.forEach { t -> t.keywayAbsSpanMm()?.let { add(t.id to (it.first + it.second) * 0.5f) } }
        bodies.forEach { b -> b.keywayAbsSpanMm()?.let { add(b.id to (it.first + it.second) * 0.5f) } }
    }
    if (centers.size < 2) return emptySet()
    val nearId = centers.minByOrNull { it.second }!!.first
    return centers.asSequence().map { it.first }.filterNot { it == nearId }.toSet()
}

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
    if (!couplerBoltSlots.all { it.isValid(overallLengthMm) }) return false
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
 * Returns a copy where every excluded thread's [Threads.startFromAftMm] is placed
 * immediately outside the shaft span so it renders adjacent to — but never overlapping —
 * any shaft feature:
 *  - AFT end → startFromAftMm = −lengthMm   (thread ends flush with the AFT face at 0)
 *  - FWD end → startFromAftMm = overallLengthMm  (thread starts flush with the FWD face)
 *
 * [ShaftLayout] expands its coordinate window to accommodate these negative / over-OAL
 * positions, so both the preview and PDF show the threads outside the dimensioned span.
 *
 * Called whenever OAL or the excluded-thread topology changes.
 */
fun ShaftSpec.syncExcludedThreadPositions(): ShaftSpec {
    val oal = overallLengthMm
    val synced = threads.map { th ->
        if (!th.excludeFromOAL) th
        else {
            val newStart = if (th.isAftEnd) -th.lengthMm else oal
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
