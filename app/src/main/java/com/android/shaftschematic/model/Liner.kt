package com.android.shaftschematic.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import java.util.UUID

enum class LinerAuthoredReference { AFT, FWD }

/**
 * Cylindrical liner/sleeve on the shaft (outer diameter only).
 *
 * Units: **mm** (millimeters).
 *
 * Geometry is stored as physical shaft coordinates in mm.
 * Authoring reference only affects the UI display of the start value.
 *
 * @property startMmPhysical Physical start position from AFT.
 * @property endMmPhysical Physical end position from AFT.
 * @property lengthMm Axial length of the liner.
 * @property odMm Outside diameter of the liner.
 * @property authoredReference AFT or FWD reference used for authoring display.
 * @property authoredStartFromFwdMm Start offset measured from the forward datum when authoredReference is FWD.
 */
@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class Liner(
    override val id: String = UUID.randomUUID().toString(),
    @kotlinx.serialization.SerialName("startMmPhysical")
    @JsonNames("startFromAftMm", "startMmPhysical")
    override val startFromAftMm: Float = 0f,
    override val lengthMm: Float = 0f,
    val odMm: Float = 0f,
    /** Optional user-defined label for display (not used for geometry). */
    val label: String? = null,
    val authoredReference: LinerAuthoredReference = LinerAuthoredReference.AFT,
    val authoredStartFromFwdMm: Float = 0f,
    @JsonNames("endFromAftMm", "endMmPhysical")
    val endMmPhysical: Float = 0f,
) : Segment {
    val startMmPhysical: Float get() = startFromAftMm
}

/** Normalize to ensure endMmPhysical matches start + length. */
fun Liner.normalized(): Liner {
    val computedEnd = startFromAftMm + lengthMm
    val end = if (endMmPhysical <= 0f && computedEnd > 0f) computedEnd else endMmPhysical
    return if (kotlin.math.abs(end - computedEnd) > 1e-3f) {
        copy(endMmPhysical = computedEnd)
    } else {
        copy(endMmPhysical = end)
    }
}

/** Copy with updated physical geometry (end is derived from start + length). */
fun Liner.withPhysical(startMmPhysical: Float, lengthMm: Float, odMm: Float): Liner =
    copy(
        startFromAftMm = startMmPhysical,
        lengthMm = lengthMm,
        odMm = odMm,
        endMmPhysical = startMmPhysical + lengthMm
    )

/** Basic invariants for a Liner. */
fun Liner.isValid(overallLengthMm: Float): Boolean =
    isWithin(overallLengthMm) && odMm >= 0f
