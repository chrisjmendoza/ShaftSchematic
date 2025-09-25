package com.android.shaftschematic.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * External thread specification on the shaft.
 *
 * Units: **mm** (millimeters).
 *
 * Geometry is measured AFT → FWD in canonical millimeters. This type implements [Segment].
 *
 * @property startFromAftMm Where the threaded section starts (from AFT toward FWD).
 * @property majorDiaMm Major diameter of the thread (outer).
 * @property pitchMm Thread pitch (distance per turn).
 * @property lengthMm Axial length of the threaded section.
 * @property excludeFromOAL If true, this thread is **excluded** from overall-length calculations
 *                          (but still renders in the preview). This supports the common practice
 *                          of starting shaft measurement after the thread when desired.
 */
@Serializable
data class ThreadSpec(
    override val id: String = UUID.randomUUID().toString(),
    override val startFromAftMm: Float = 0f,
    val majorDiaMm: Float = 0f,
    val pitchMm: Float = 0f,
    override val lengthMm: Float = 0f,
    val excludeFromOAL: Boolean = false
) : Segment

/* ────────────────────────────────────────────────────────────────────────────
 * Validation
 * ──────────────────────────────────────────────────────────────────────────── */

/** Basic invariants for a ThreadSpec. */
fun ThreadSpec.isValid(overallLengthMm: Float): Boolean =
    isWithin(overallLengthMm) && majorDiaMm >= 0f && pitchMm >= 0f
