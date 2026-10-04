// app/src/main/java/com/android/shaftschematic/model/Threads.kt
package com.android.shaftschematic.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonNames
import java.util.UUID

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
 *                          (but still renders in the preview/PDF). Position is fixed at the shaft
 *                          end determined by [isAftEnd].
 * @property isAftEnd When [excludeFromOAL] is true: true = thread sits at the AFT end (start = 0),
 *                    false = FWD end (start = OAL − length). Ignored when included in OAL.
 * @property tpi Optional threads-per-inch (imperial). Preferred when set; see [normalized].
 * @property metricDesignation Optional ISO metric designation authored by the user (e.g.
 *                             `M20×2.5`). When present the thread is metric-mode: its printed
 *                             spec is the designation verbatim and its effective display unit is
 *                             mm. Canonical geometry stays in [majorDiaMm]/[pitchMm] (parsed
 *                             from the designation at entry, never re-derived here — golden rule).
 *                             See [com.android.shaftschematic.util.ThreadDesignation].
 * @property showNameOnDrawing Whether this thread's name prints as a component label under the
 *                             schematic. Tri-state, draw-only — mirrors
 *                             [com.android.shaftschematic.model.Body.showNameOnDrawing]: `null`
 *                             (default) follows the Settings switch, explicit `true`/`false`
 *                             overrides it either way; never rewrites [label] or geometry.
 * @property reliefLenMm Axial length of the thread RELIEF — the plain groove turned below the
 *                       thread's root at the end where the thread runs up to its shoulder (on a
 *                       propeller shaft, the taper's S.E.T.), so the tool can run out and the nut
 *                       seats against the shoulder. The relief is PART of the stored span: the
 *                       [reliefLenMm] at the relief end is groove, the rest is threaded. Adding
 *                       one moves nothing — [startFromAftMm]/[lengthMm] are untouched. Stored
 *                       verbatim; `0` (the default, and every document saved before the field)
 *                       means no relief.
 * @property reliefDiaMm Diameter of the relief groove, stored verbatim. A value at or above
 *                       [majorDiaMm] is kept as typed and flagged by an advisory; the drawing
 *                       clamps it below the major diameter where it is DRAWN
 *                       (`geom/ThreadReliefMath.kt`).
 * @property reliefAtAft Which end of the thread carries the relief: `true` = its AFT end, `false`
 *                       = its FWD end. The authoring surfaces seed it from
 *                       [suggestedThreadReliefAtAft] when the relief is switched on; the stored
 *                       default only matters once a relief length is set.
 *
 * A thread has a relief exactly when [hasRelief] — length and diameter both > 0. There is no
 * stored flag (the captured-keyway posture: captured ⇔ inset > 0).
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
    val isAftEnd: Boolean = true,
    val tpi: Float? = null,
    @JsonNames("metricDesignation", "metric_designation")
    val metricDesignation: String? = null,
    /** Optional user-defined label for display (not used for geometry). */
    val label: String? = null,
    val showNameOnDrawing: Boolean? = null,
    val reliefLenMm: Float = 0f,
    val reliefDiaMm: Float = 0f,
    val reliefAtAft: Boolean = false,
) : Segment {

    /** True when this thread carries a relief groove: [reliefLenMm] and [reliefDiaMm] both > 0. */
    val hasRelief: Boolean get() = reliefLenMm > 0f && reliefDiaMm > 0f

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
            (tpi ?: 0f) > 0f -> 25.4f / tpi!!
            else -> pitchMm
        }
        val tpiVal = when {
            (tpi ?: 0f) > 0f -> tpi
            pitch > 0f       -> 25.4f / pitch
            else             -> tpi
        }
        return copy(pitchMm = pitch, tpi = tpiVal)
    }

}

/**
 * Quick bounds validation for this segment against an overall shaft length.
 * Does not check overlaps; use aggregate checks if needed.
 */
fun Threads.isValid(overallLengthMm: Float): Boolean =
    isWithin(overallLengthMm) &&
        majorDiaMm >= 0f &&
        pitchMm >= 0f

/** How close (mm) a thread end must sit to a shaft end to count as AT that end. */
private const val THREAD_AT_SHAFT_END_EPS_MM = 0.5f

/**
 * The end a NEW thread relief should default to. The groove belongs at the thread's INBOARD end —
 * the end away from the shaft end, where the thread runs up to its shoulder:
 * - an AFT-end thread (start ≈ 0, or excluded from OAL with [isAftEnd]) → `false`, the relief at
 *   its FWD end, against the S.E.T.;
 * - a FWD-end thread (end ≈ [overallLengthMm], or excluded with `!isAftEnd`) → `true`;
 * - a mid-shaft thread → `false`.
 *
 * A seed only: both authoring surfaces read it at the moment the relief is switched on, and it
 * is never applied to a stored thread (the `suggestedBodyKeywayEnd` posture).
 */
fun suggestedThreadReliefAtAft(
    startFromAftMm: Float,
    lengthMm: Float,
    excludeFromOAL: Boolean,
    isAftEnd: Boolean,
    overallLengthMm: Float,
): Boolean {
    if (excludeFromOAL) return !isAftEnd
    if (startFromAftMm <= THREAD_AT_SHAFT_END_EPS_MM) return false
    if (overallLengthMm > 0f && startFromAftMm + lengthMm >= overallLengthMm - THREAD_AT_SHAFT_END_EPS_MM) return true
    return false
}
