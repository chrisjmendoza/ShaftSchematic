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
 * (see `CLAUDE.md` and `docs/archive/LinerWearAreas_Proposal.md` §7):
 * - It never affects `coverageEndMm`, body resolution, or collision/overlap
 *   validation.
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
 *   **Never entered or printed** — [WearDiaReading] owns the diameter story (readings at
 *   exact stations); printing a per-band label here would collide with those callouts
 *   under a wear band (on-device report). The field exists only so older files
 *   round-trip; commits pass the stored value through unchanged.
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
 * The dye penetrant inspection's recorded outcome. `null` (no selection on [WearRecord])
 * means "not recorded in-app": both printed checkboxes stay blank for hand-marking, the
 * original form posture. A selection prints an "X" inside its box; the other box stays
 * present and blank, so the sheet always reads as the same two-box form.
 */
@Serializable
enum class DyePenResult { PASS, FAIL }

/**
 * A single pit / dye-penetrant failure marker on a component, drawn as an "X".
 *
 * A **pure reference feature**, the same contract class as [WearSpot] / [CouplerBoltSlot] /
 * [com.android.shaftschematic.model.RunoutReading]: it never affects `coverageEndMm`,
 * body resolution, or collision/overlap validation,
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
 * A single measured-diameter reading at an axial station on a component — the digital form
 * of the shop's hand-written diameter values under a wear area (a value below the shaft
 * with a leader line pointing at the measured spot).
 *
 * A **pure reference feature**, the same contract class as [WearSpot] / [WearPit] /
 * [com.android.shaftschematic.model.RunoutReading]: it never affects `coverageEndMm`,
 * body resolution, or collision/overlap validation,
 * and it lives outside [ShaftSpec] entirely (in [WearRecord], the document envelope).
 *
 * Like [WearPit], a reading may sit on **any** liner, taper, or body (explicit or auto) and
 * is keyed by the *resolved* component id ([componentId]). A reading whose component no
 * longer resolves is simply not drawn — orphan handling at the render layer, not decode.
 *
 * ## Coordinate rule
 * [axialMm] is measured from the component's **AFT edge**, component-local — NOT shaft
 * space — so a reading survives the component being repositioned, exactly like
 * [WearPit.axialMm]. Shaft-space conversion is `component.startMmPhysical + axialMm`, done
 * at render time only. No across position: a diameter belongs to the whole cross-section
 * at that station, and its callout always hangs below the drawn shaft.
 *
 * [diaMm] is the machinist's typed measurement — stored **verbatim** (golden rule: user
 * inputs are sacred; no snap/round/derive ever rewrites it). `0` means the station was
 * placed but no value has been entered yet: the detail overlay still draws it (findable,
 * editable), but the printed PDF skips it — a leader pointing at no value is noise.
 *
 * Units: mm.
 */
@Serializable
data class WearDiaReading(
    val id: String = UUID.randomUUID().toString(),
    val componentId: String = "",
    val axialMm: Float = 0f,
    val diaMm: Float = 0f,
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
 * @property diaReadings Measured-diameter readings on liners, tapers, and bodies (see
 *   [WearDiaReading]). Additive + defaulted, same no-version-bump rule as [pits].
 * @property wornSections Designated worn areas whose measured Ø values print inside the
 *   shaft profile on the runout sheet (see [WornSection] — shaft-space, never pruned at
 *   decode). Additive + defaulted, same no-version-bump rule as [pits].
 * @property dyePenResult The dye penetrant inspection's in-app selection, printed as an "X"
 *   in the matching PASS/FAIL checkbox of the wear sheet's notes row; `null` keeps both
 *   boxes blank for hand-marking (see [DyePenResult]). Reference-only data — no geometry
 *   effect anywhere. Additive + defaulted, same no-version-bump rule as [pits].
 * @property traceDepthFrac This job's worn-profile trace exaggeration — how deep the record's
 *   deepest liner reading draws, as a fraction of the drawn radius
 *   (`geom/WearTraceMath.kt`). `null` = follow the Settings → Drawing default
 *   (`PdfPrefs.wearTraceDepthFrac`), so a job that never touched its slider tracks later
 *   changes to that default while a touched job stays pinned; `effectiveWearTraceDepthFrac`
 *   resolves the pair for every consumer. Display-only styling — it never changes a stored or
 *   printed Ø, and the trace still never draws shallower than true scale — but it is
 *   per-document (a sheet keeps its chosen look), so it lives here rather than only in app
 *   prefs. Additive + defaulted, same no-version-bump rule as [pits].
 * @property stripComponentIds Which components get a broken-out detail strip on the wear
 *   sheet. `null` (the default) is the default election — every drawable liner, the historical
 *   sheet — while a non-null list is the machinist's authored set of **resolved component ids**
 *   (liners, tapers, bodies, explicit or auto), so a liner added later never silently rewrites
 *   an authored sheet. An empty list means no strips. Ids that no longer resolve are skipped at
 *   the render layer, NEVER pruned at decode (auto-body/taper ids aren't known to the codec) —
 *   the [WearPit]/[WearDiaReading] rule. Layout-only: it changes what the sheet draws, never a
 *   stored or printed value. Additive + defaulted, same no-version-bump rule as [pits].
 * @property showShaftProfile Whether the whole-shaft profile band prints on the wear sheet
 *   (with its OAL rail, on-profile wear bands and pits, liner names, and direction reference).
 *   `false` gives that vertical budget to the detail strips; the header, dye-pen row, and every
 *   elected strip still print. Layout-only, and per-document, so it lives here. Additive +
 *   defaulted, same no-version-bump rule as [pits].
 * @property compactStrips Whether the detail strips print at the shaft's own page scale instead
 *   of stretching toward the content width. The sheet keeps ONE shared strip scale either way —
 *   this only lowers its ceiling to the main profile's pt/mm (floored at
 *   `WEAR_STRIP_COMPACT_MIN_PT_PER_MM`), so a strip's drawn width matches its span on the
 *   profile above it and the page reads denser. Layout-only, and per-document, so it lives here.
 *   Additive + defaulted, same no-version-bump rule as [pits].
 * @property stripSizeFrac Multiplier on the detail strips' height ceiling — the page's own row
 *   budget scaled up or down. `1` (the default) is the traditional height a full page of rows
 *   gives a strip; the settable range and the base cap live with the layout math
 *   (`WEAR_STRIP_SIZE_FRAC_MIN`/`_MAX`/`_DEFAULT` and `wearRowHeightCapPt`,
 *   `pdf/WearStripLayout.kt` — the model stays free of any `pdf` import, so the literal `1f` is
 *   repeated here rather than referenced). Display-only, and per-document, so it lives here: it
 *   changes how tall a strip draws, never a stored or printed measurement. Additive + defaulted,
 *   same no-version-bump rule as [pits].
 */
@Serializable
data class WearRecord(
    val spots: List<WearSpot> = emptyList(),
    val pits: List<WearPit> = emptyList(),
    val diaReadings: List<WearDiaReading> = emptyList(),
    val wornSections: List<WornSection> = emptyList(),
    val traceDepthFrac: Float? = null,
    val dyePenResult: DyePenResult? = null,
    val stripComponentIds: List<String>? = null,
    val showShaftProfile: Boolean = true,
    val compactStrips: Boolean = false,
    val stripSizeFrac: Float = 1f,
)
