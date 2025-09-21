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
 */
@Serializable
data class Body(
    override val id: String = UUID.randomUUID().toString(),
    override val startFromAftMm: Float = 0f,
    override val lengthMm: Float = 0f,
    val diaMm: Float = 0f,
) : Segment

/** Basic invariants for a Body. */
fun Body.isValid(overallLengthMm: Float): Boolean =
    isWithin(overallLengthMm) && diaMm >= 0f
