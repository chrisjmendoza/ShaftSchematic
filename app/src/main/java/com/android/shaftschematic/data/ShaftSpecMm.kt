package com.android.shaftschematic.data

/**
 * Core shaft spec in millimeters.
 *
 * You can use the optional "aft"/"forward" single fields for convenience, or
 * just use the lists. The drawing code will merge both.
 */
data class ShaftSpecMm(
    val overallLengthMm: Float,

    // Convenience singletons
    val aftThread: ThreadSpec? = null,
    val forwardThread: ThreadSpec? = null,
    val aftTaper: TaperSpec? = null,
    val forwardTaper: TaperSpec? = null,

    // Collections (use these for everything else)
    val threads: List<ThreadSpec> = emptyList(),
    val tapers: List<TaperSpec> = emptyList(),
    val liners: List<LinerSpec> = emptyList(),
    val bodies: List<BodySegmentSpec> = emptyList()
)

/** Straight cylindrical segment of the shaft. */
data class BodySegmentSpec(
    /** Distance from AFT end to the start of this segment (mm). */
    val startFromAftMm: Float,
    /** Length of this segment along the axis (mm). */
    val lengthMm: Float,
    /** Outer diameter (mm). */
    val diaMm: Float,
    /** If true, draw with “squiggle breaks” to indicate visual compression. */
    val compressed: Boolean = false,
    /**
     * Optional visual width factor (0–1] applied to this segment when drawing,
     * without changing its real length for dimensioning.
     */
    val compressionFactor: Float? = null
)

/** Linear conical change in diameter. */
data class TaperSpec(
    val startFromAftMm: Float,
    val lengthMm: Float,
    val startDiaMm: Float,
    val endDiaMm: Float
)

/** External thread area (simplified as a box with hatch). */
// data/ThreadSpec.kt (or wherever it lives)
data class ThreadSpec(
    val startFromAftMm: Float,
    val lengthMm: Float,
    val majorDiaMm: Float,
    val pitchMm: Float = 0f,      // NEW
    val endLabel: String = ""     // NEW  ("AFT" or "FWD" for display/PDF)
)


/** A sleeve/liner sitting on the shaft OD. */
data class LinerSpec(
    val startFromAftMm: Float,
    val lengthMm: Float,
    val odMm: Float
)
