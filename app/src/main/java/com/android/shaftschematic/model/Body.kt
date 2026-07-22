package com.android.shaftschematic.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Cylindrical body section (constant diameter).
 *
 * Units: **mm** (millimeters).
 *
 * @property startFromAftMm Distance from AFT face toward FWD where this body starts.
 * @property lengthMm Axial length of the body.
 * @property diaMm Outer diameter of the body.
 * @property keywayWidthMm Optional keyway width (0 = none).
 * @property keywayDepthMm Optional keyway depth (0 = none).
 * @property keywayLengthMm Optional keyway length (0 = none).
 * @property keywayOffsetFromEndMm Axial distance from the referenced body end face
 *   ([keywayEnd]) to the near edge of the keyway slot.
 *   0 = open keyway (starts at the end face, open-ended there).
 *   > 0 = floating keyway (inset from the face, rounded at both ends).
 * @property keywayEnd Which body end face the keyway is referenced from (AFT or FWD).
 *   Intermediate shafts with fitted couplings carry keyways at a shaft end that is a
 *   plain cylindrical body — this picks the face the offset is measured from.
 * @property keywaySpooned Whether the open keyway's closed (LET) end is spooned — an enlarged
 *   circle drawn around the mill end (which stays as an inner reference line).
 *   Ignored when [keywayOffsetFromEndMm] > 0 (floating keyways have no open end to reference).
 */
@Serializable
data class Body(
    override val id: String = UUID.randomUUID().toString(),
    override val startFromAftMm: Float = 0f,
    override val lengthMm: Float = 0f,
    val diaMm: Float = 0f,
    val keywayWidthMm: Float = 0f,
    val keywayDepthMm: Float = 0f,
    val keywayLengthMm: Float = 0f,
    val keywayOffsetFromEndMm: Float = 0f,
    val keywayEnd: LinerAuthoredReference = LinerAuthoredReference.AFT,
    val keywaySpooned: Boolean = false,
    /** Optional user-defined label for display (not used for geometry). */
    val label: String? = null,
) : Segment

/** Basic invariants for a Body. */
fun Body.isValid(overallLengthMm: Float): Boolean =
    isWithin(overallLengthMm) &&
        diaMm >= 0f &&
        keywayWidthMm >= 0f &&
        keywayDepthMm >= 0f &&
        keywayLengthMm >= 0f &&
        keywayOffsetFromEndMm >= 0f &&
        (keywayOffsetFromEndMm + keywayLengthMm) <= lengthMm

/** True if this body has a keyway defined (all three dimensions non-zero). */
val Body.hasKeyway: Boolean get() = keywayWidthMm > 0f && keywayDepthMm > 0f && keywayLengthMm > 0f

/**
 * Absolute AFT-origin axial span of this body's keyway as (lo, hi) mm, or null when
 * the body has no keyway. Resolves the AFT/FWD end-face reference to physical space.
 */
fun Body.keywayAbsSpanMm(): Pair<Float, Float>? {
    if (!hasKeyway) return null
    return when (keywayEnd) {
        LinerAuthoredReference.AFT -> {
            val near = startFromAftMm + keywayOffsetFromEndMm
            near to near + keywayLengthMm
        }
        LinerAuthoredReference.FWD -> {
            val near = startFromAftMm + lengthMm - keywayOffsetFromEndMm
            (near - keywayLengthMm) to near
        }
    }
}

/** Strips all keyway fields (used when a geometry change makes the keyway unplaceable). */
fun Body.withoutKeyway(): Body = copy(
    keywayWidthMm = 0f,
    keywayDepthMm = 0f,
    keywayLengthMm = 0f,
    keywayOffsetFromEndMm = 0f,
    keywaySpooned = false,
)
