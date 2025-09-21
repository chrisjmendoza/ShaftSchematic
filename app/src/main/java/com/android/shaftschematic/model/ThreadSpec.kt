package com.android.shaftschematic.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * External thread specification on the shaft.
 *
 * Units: **mm** (millimeters).
 *
 * @property startFromAftMm Where the threaded section starts (from AFT toward FWD).
 * @property majorDiaMm Major diameter of the thread (outer).
 * @property pitchMm Thread pitch (distance per turn).
 * @property lengthMm Axial length of the threaded section.
 */
@Serializable
data class ThreadSpec(
    override val id: String = UUID.randomUUID().toString(),
    override val startFromAftMm: Float = 0f,
    val majorDiaMm: Float = 0f,
    val pitchMm: Float = 0f,
    override val lengthMm: Float = 0f,
) : Segment

/** Basic invariants for a ThreadSpec. */
fun ThreadSpec.isValid(overallLengthMm: Float): Boolean =
    isWithin(overallLengthMm) && majorDiaMm >= 0f && pitchMm >= 0f
