package com.android.shaftschematic.model

import kotlinx.serialization.Serializable
import java.util.UUID
import kotlin.math.max

/**
 * Linear taper between two diameters.
 *
 * Units: **mm** (millimeters).
 *
 * @property startFromAftMm Distance from AFT face toward FWD where the taper starts.
 * @property lengthMm Axial length of the taper.
 * @property startDiaMm Diameter at the taper's start.
 * @property endDiaMm Diameter at the taper's end.
 * @property keywayWidthMm Optional keyway width (0 = none).
 * @property keywayDepthMm Optional keyway depth (0 = none).
 * @property keywayLengthMm Optional keyway length (0 = none).
 * @property keywayOffsetFromSetMm Axial distance from SET face to the start of the keyway slot.
 *   0 = open keyway (starts at SET face, open-ended there).
 *   > 0 = floating keyway (inset from SET, rounded at both ends).
 * @property keywaySpooned Whether the open keyway's closed (LET) end is spooned — an enlarged
 *   circle drawn around the mill end (which stays as an inner reference line).
 *   Ignored when [keywayOffsetFromSetMm] > 0 (floating keyways have no open end to reference).
 */
@Serializable
data class Taper(
    override val id: String = UUID.randomUUID().toString(),
    override val startFromAftMm: Float = 0f,
    override val lengthMm: Float = 0f,
    val startDiaMm: Float = 0f,
    val endDiaMm: Float = 0f,
    val keywayWidthMm: Float = 0f,
    val keywayDepthMm: Float = 0f,
    val keywayLengthMm: Float = 0f,
    val keywayOffsetFromSetMm: Float = 0f,
    val keywaySpooned: Boolean = false,
    val taperRateText: String = "",
    val authoredReference: LinerAuthoredReference = LinerAuthoredReference.AFT,
    /** Optional user-defined label for display (not used for geometry). */
    val label: String? = null,
) : Segment

/** Basic invariants for a Taper. */
fun Taper.isValid(overallLengthMm: Float): Boolean =
    isWithin(overallLengthMm) &&
        startDiaMm >= 0f &&
        endDiaMm >= 0f &&
        keywayWidthMm >= 0f &&
        keywayDepthMm >= 0f &&
        keywayLengthMm >= 0f &&
        keywayOffsetFromSetMm >= 0f &&
        (keywayOffsetFromSetMm + keywayLengthMm) <= lengthMm

/** True if this taper has a keyway defined (all three dimensions non-zero). */
val Taper.hasKeyway: Boolean get() = keywayWidthMm > 0f && keywayDepthMm > 0f && keywayLengthMm > 0f

/**
 * Absolute AFT-origin axial span of this taper's keyway as (lo, hi) mm, or null when the
 * taper has no keyway. The keyway is referenced from the SET face (the smaller-diameter
 * end) and extends toward LET. Mirrors [Body.keywayAbsSpanMm] for keyway clocking logic.
 */
fun Taper.keywayAbsSpanMm(): Pair<Float, Float>? {
    if (!hasKeyway) return null
    val setAtStart = startDiaMm <= endDiaMm
    val setX = if (setAtStart) startFromAftMm else startFromAftMm + lengthMm
    val dir  = if (setAtStart) 1f else -1f
    val kwSet = setX + dir * keywayOffsetFromSetMm
    val kwLet = kwSet + dir * keywayLengthMm
    return minOf(kwSet, kwLet) to maxOf(kwSet, kwLet)
}

/** Maximum diameter across the taper span. */
val Taper.maxDiaMm: Float get() = max(startDiaMm, endDiaMm)
