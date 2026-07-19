// app/src/main/java/com/android/shaftschematic/model/WearSpot.kt
package com.android.shaftschematic.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Authoring reference for a wear spot's *start* value (UI display only — see
 * `LinerWearMath.kt`'s `wearStartToCanonicalMm`/`canonicalToWearStartMm`).
 *
 * Same role as [LinerAuthoredReference]/[SlotAuthoredReference]: it never changes
 * canonical storage ([WearSpot.startMm]), only how the "Start" field is entered and
 * re-displayed.
 * - [LINER_AFT] / [AFT_SET]: the entered value locates the band's **AFT edge**,
 *   measured FWD from the reference point.
 * - [LINER_FWD] / [FWD_SET]: the entered value locates the band's **FWD edge**,
 *   measured AFT from the reference point.
 */
@Serializable
enum class WearSpotReference { LINER_AFT, LINER_FWD, AFT_SET, FWD_SET }

/**
 * A recorded wear band on a liner.
 *
 * This is a **pure reference feature** — same contract class as [CouplerBoltSlot]
 * (see `CLAUDE.md` and `docs/LinerWearAreas_Proposal.md` §7):
 * - It never affects `coverageEndMm`, `ensureOverall`, body resolution, collision/overlap
 *   validation, or the Free-to-End badge.
 * - It lives outside [ShaftSpec] entirely (flat list in [WearRecord], stored beside
 *   `RunoutConfig` in the document envelope) so geometry resolution never has to know
 *   about it.
 *
 * ## Coordinate rule
 * [startMm] is measured from the **liner's AFT edge**, in liner-local space — NOT
 * shaft space. This is the canonical storage convention: wear history survives the
 * liner being repositioned on the shaft. Converting to shaft space is
 * `liner.startFromAftMm + spot.startMm`, and that conversion happens at render time
 * only (never stored).
 *
 * [authoredReference] records which of four reference points ([WearSpotReference]) the
 * machinist entered [startMm] against, purely so the "Start" field can re-display the
 * same authored number on the next visit. Switching it in the UI re-projects the
 * displayed value only — [startMm] itself never moves as a result (see
 * `LinerWearMath.kt`).
 *
 * Units: mm.
 *
 * @property id Stable identifier.
 * @property linerId The [Liner.id] this wear spot belongs to. Spots whose `linerId`
 *   no longer matches any liner in the current spec are orphans — dropped on load
 *   (see `ShaftDocCodec.decode`).
 * @property startMm Offset from the liner's AFT edge, liner-local (not shaft space).
 *   Canonical storage — always liner-local AFT-edge mm regardless of [authoredReference].
 * @property lengthMm Axial length of the worn band.
 * @property minDiaMm Minimum measured diameter within the band. `0` = no reading recorded.
 * @property note Free-text note (e.g. "scored", "pitted 6 o'clock").
 * @property authoredReference Which reference point [startMm] was authored against
 *   (display-only; additive field, default [WearSpotReference.LINER_AFT] preserves the
 *   pre-existing behavior for old files — no envelope version bump needed).
 */
@Serializable
data class WearSpot(
    val id: String = UUID.randomUUID().toString(),
    val linerId: String = "",
    val startMm: Float = 0f,
    val lengthMm: Float = 0f,
    val minDiaMm: Float = 0f,
    val note: String = "",
    val authoredReference: WearSpotReference = WearSpotReference.LINER_AFT,
)

/**
 * Per-document wear inspection record. Lives beside `RunoutConfig` in the document
 * envelope (`ShaftDocCodec.ShaftDocV1`) — NOT inside [ShaftSpec] — so wear data never
 * touches geometry resolution, collision, or coverage math. See [WearSpot] for the
 * reference-only contract and coordinate rule.
 */
@Serializable
data class WearRecord(
    val spots: List<WearSpot> = emptyList(),
)
