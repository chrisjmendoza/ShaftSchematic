package com.android.shaftschematic.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Cylindrical liner/sleeve on the shaft (outer diameter only).
 *
 * Units: **mm** (millimeters).
 *
 * @property startFromAftMm Where the liner starts (from AFT toward FWD).
 * @property lengthMm Axial length of the liner.
 * @property odMm Outside diameter of the liner.
 */
@Serializable
data class Liner(
    override val id: String = UUID.randomUUID().toString(),
    override val startFromAftMm: Float = 0f,
    override val lengthMm: Float = 0f,
    val odMm: Float = 0f,
    /** Optional user-defined label for display (not used for geometry). */
    val label: String? = null,
) : Segment

/** Basic invariants for a Liner. */
fun Liner.isValid(overallLengthMm: Float): Boolean =
    isWithin(overallLengthMm) && odMm >= 0f
