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
 * The drawn size of a wear [WearPit] "X" symbol. Machinists mark small pits/holes with a
 * little "X" and larger cavities with a bigger one; this is a **symbol** size (how the X is
 * drawn on the sheet), not the pit's true physical diameter — so it scales with the drawing,
 * not with the model, exactly like the hand convention.
 */
@Serializable
enum class PitSize { SMALL, LARGE }

/**
 * A single pit / dye-penetrant failure marker on a component, drawn as an "X".
 *
 * A **pure reference feature**, the same contract class as [WearSpot] / [CouplerBoltSlot] /
 * [com.android.shaftschematic.model.RunoutReading]: it never affects `coverageEndMm`,
 * `ensureOverall`, body resolution, collision/overlap validation, or the Free-to-End badge,
 * and it lives outside [ShaftSpec] entirely (in [WearRecord], the document envelope).
 *
 * Unlike [WearSpot] (which is liner-only), a pit may sit on **any** pit-eligible component —
 * a liner, a taper, or a body (explicit or auto). It is therefore keyed by the *resolved*
 * component id ([componentId]), the same identity a runout reading uses. A pit whose
 * component no longer resolves (geometry edited away) is simply not drawn — orphan handling
 * happens at the render layer, not at decode (bodies/tapers/auto-body ids aren't known to the
 * codec), matching `RunoutReadings`.
 *
 * ## Coordinate rule
 * [axialMm] is measured from the component's **AFT edge**, component-local — NOT shaft space —
 * so a pit survives the component being repositioned, exactly like [WearSpot.startMm].
 * Shaft-space conversion is `component.startMmPhysical + pit.axialMm`, done at render time only.
 * [acrossFrac] places the X vertically within the drawn segment: `0` = the top outline,
 * `1` = the bottom outline (purely visual, reference-only — clamped to a comfortable interior
 * band by `clampPitAcrossFrac` so the symbol stays on the metal).
 *
 * Units: [axialMm] is mm; [acrossFrac] is a unitless fraction.
 *
 * @property id Stable identifier.
 * @property componentId The resolved component ([ResolvedComponent.id]) this pit sits on.
 * @property axialMm Offset from the component's AFT edge, component-local (not shaft space).
 * @property acrossFrac Vertical position across the drawn segment, `0` (top) .. `1` (bottom).
 * @property size Drawn X size — [PitSize.SMALL] (little hole) or [PitSize.LARGE] (bigger cavity).
 */
@Serializable
data class WearPit(
    val id: String = UUID.randomUUID().toString(),
    val componentId: String = "",
    val axialMm: Float = 0f,
    val acrossFrac: Float = 0.5f,
    val size: PitSize = PitSize.SMALL,
)

/**
 * Per-document wear inspection record. Lives beside `RunoutConfig` in the document
 * envelope (`ShaftDocCodec.ShaftDocV1`) — NOT inside [ShaftSpec] — so wear data never
 * touches geometry resolution, collision, or coverage math. See [WearSpot] for the
 * reference-only contract and coordinate rule.
 *
 * @property spots Liner wear bands (see [WearSpot]).
 * @property pits Pit / dye-failure "X" markers on liners, tapers, and bodies (see [WearPit]).
 *   Additive + defaulted, so older files (no `pits`) round-trip to an empty list with no
 *   envelope version bump.
 */
@Serializable
data class WearRecord(
    val spots: List<WearSpot> = emptyList(),
    val pits: List<WearPit> = emptyList(),
)
