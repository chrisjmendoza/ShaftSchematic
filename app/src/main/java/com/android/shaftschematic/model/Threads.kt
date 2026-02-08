// app/src/main/java/com/android/shaftschematic/model/Threads.kt
package com.android.shaftschematic.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonNames
import java.util.UUID
import kotlin.math.abs

@Serializable
enum class ThreadAttachment { AFT, FWD }

/**
 * External thread specification on the shaft.
 *
 * Units: **mm** (millimeters).
 *
 * Geometry is measured AFT → FWD in canonical millimeters. This type implements [Segment].
 *
 * Back-compat:
 * - Older saves may only provide [pitchMm] (metric).
 * - Newer saves may provide [tpi] (imperial).
 * - Use [normalized] so both are available at runtime when either is present.
 *
 * @property id Stable identifier for this segment.
 * @property startFromAftMm Where the threaded section starts (from AFT toward FWD).
 * @property majorDiaMm Major diameter of the thread (outer).
 * @property pitchMm Thread pitch (distance per turn) in **mm/turn**. May be 0 in legacy files.
 * @property lengthMm Axial length of the threaded section.
 * @property excludeFromOAL If true, this thread is **excluded** from overall-length calculations
 *                          (but still renders in the preview/PDF).
 * @property tpi Optional threads-per-inch (imperial). Preferred when set; see [normalized].
 *
 * Invariants:
 * - All distances are ≥ 0.
 * - [lengthMm] > 0 to produce visible output.
 */
@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class Threads(
    override val id: String = UUID.randomUUID().toString(),
    override val startFromAftMm: Float = 0f,
    val majorDiaMm: Float = 0f,
    val pitchMm: Float = 0f,
    override val lengthMm: Float = 0f,
    @JsonNames("excludeFromOAL", "excludeFromOal", "exclude_from_oal")
    val excludeFromOAL: Boolean = false,
    /** End attachment when [excludeFromOAL] is true (null preserves legacy start-positioned behavior). */
    val endAttachment: ThreadAttachment? = null,
    val tpi: Float? = null,
    /** Authoring reference for UI input (AFT or FWD). */
    val authoredReference: AuthoredReference = AuthoredReference.AFT,
    /** Start offset measured from the forward datum when [authoredReference] is FWD. */
    val authoredStartFromFwdMm: Float = 0f,
) : Segment {

    /**
     * Returns a copy where **both** [pitchMm] (mm/turn) and [tpi] (threads/inch) are populated
     * whenever either is provided and > 0.
     *
     * - If only [tpi] exists, compute `pitchMm = 25.4 / tpi`.
     * - If only [pitchMm] exists, compute `tpi = 25.4 / pitchMm`.
     * - If neither is valid, fields are left as-is.
     */
    fun normalized(): Threads {
        val pitch = when {
            pitchMm > 0f -> pitchMm
            (tpi ?: 0f) > 0f -> 25.4f / (tpi ?: 1f)
            else -> pitchMm
        }
        val tpiVal = when {
            (tpi ?: 0f) > 0f -> tpi
            pitch > 0f       -> 25.4f / pitch
            else             -> tpi
        }
        return copy(pitchMm = pitch, tpi = tpiVal)
    }

    /** True if a usable pitch is available (either metric or imperial). */
    val hasPitch: Boolean get() = pitchMm > 0f || (tpi ?: 0f) > 0f
}

/** Resolve attachment for excluded threads, falling back to legacy coordinate placement. */
fun Threads.resolvedAttachment(overallLengthMm: Float, epsMm: Float = 1e-3f): ThreadAttachment? {
    if (!excludeFromOAL) return null
    if (endAttachment != null) return endAttachment
    val endMm = startFromAftMm + lengthMm
    if (abs(startFromAftMm) <= epsMm) return ThreadAttachment.AFT
    if (abs(endMm - overallLengthMm) <= epsMm) return ThreadAttachment.FWD
    if (overallLengthMm <= epsMm) return ThreadAttachment.AFT
    val center = startFromAftMm + lengthMm * 0.5f
    return if (center >= overallLengthMm * 0.5f) ThreadAttachment.FWD else ThreadAttachment.AFT
}

/** Effective start position for rendering and end detection. */
fun Threads.resolvedStartFromAftMm(overallLengthMm: Float): Float {
    val attachment = resolvedAttachment(overallLengthMm) ?: return startFromAftMm
    return when (attachment) {
        ThreadAttachment.AFT -> 0f
        ThreadAttachment.FWD -> (overallLengthMm - lengthMm).coerceAtLeast(0f)
    }
}

/** Effective end position for rendering and end detection. */
fun Threads.resolvedEndFromAftMm(overallLengthMm: Float): Float =
    resolvedStartFromAftMm(overallLengthMm) + lengthMm

/**
 * Quick bounds validation for this segment against an overall shaft length.
 * Does not check overlaps; use aggregate checks if needed.
 */
fun Threads.isValid(overallLengthMm: Float): Boolean =
    isWithin(overallLengthMm) &&
        majorDiaMm >= 0f &&
        pitchMm >= 0f
