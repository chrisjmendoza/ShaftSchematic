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
 */
@Serializable
data class Taper(
    override val id: String = UUID.randomUUID().toString(),
    override val startFromAftMm: Float = 0f,
    override val lengthMm: Float = 0f,
    val startDiaMm: Float = 0f,
    val endDiaMm: Float = 0f,
) : Segment

/** Basic invariants for a Taper. */
fun Taper.isValid(overallLengthMm: Float): Boolean =
    isWithin(overallLengthMm) && startDiaMm >= 0f && endDiaMm >= 0f

/** Maximum diameter across the taper span. */
val Taper.maxDiaMm: Float get() = max(startDiaMm, endDiaMm)
