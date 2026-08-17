// app/src/main/java/com/android/shaftschematic/model/Undercut.kt
package com.android.shaftschematic.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Authoring reference for an undercut's *distance* value (UI display only — see
 * `geom/UndercutMath.kt`'s `undercutStartToCanonicalMm`/`canonicalToUndercutStartMm`).
 *
 * Same role as [WearSpotReference]: it never changes canonical storage
 * ([Undercut.startFromAftMm]), only how the "Distance" field is entered and re-displayed.
 * The four references mirror the wear spot's set — two S.E.T. (Small End of Taper) datums
 * plus the edges of an associated liner ([Undercut.referenceLinerId]); the liner pair is
 * offered only while such a liner exists, since converting against it needs its edges.
 * - [AFT_SET]: the entered value locates the undercut's **AFT edge**, measured FWD from
 *   the AFT taper's SET.
 * - [FWD_SET]: the entered value locates the undercut's **FWD edge**, measured AFT from
 *   the FWD taper's SET.
 * - [LINER_AFT]: the entered value locates the undercut's **AFT edge**, measured FWD from
 *   the reference liner's AFT edge.
 * - [LINER_FWD]: the entered value locates the undercut's **FWD edge**, measured AFT from
 *   the reference liner's FWD edge.
 *
 * Back-compat note: [LINER_AFT]/[LINER_FWD] are additive enum values — a document that
 * uses them will not decode in app builds that predate them (the same additive-enum rule
 * every envelope enum carries); files that stick to the SET references are unaffected.
 */
@Serializable
enum class UndercutReference { AFT_SET, FWD_SET, LINER_AFT, LINER_FWD }

/**
 * A recorded undercut section — an axial span machined below the surrounding shaft
 * surface (weld-repair undercut, cleanup cut), documented on its own printed drawing
 * with zoomed detail windows (see `docs/archive/UndercutDrawing_PLAN.md`).
 *
 * A **pure reference feature**, the same contract class as [WearSpot] / [WearPit] /
 * [WearDiaReading] / [com.android.shaftschematic.model.RunoutReading] /
 * [CouplerBoltSlot]: it never affects `coverageEndMm`, `ensureOverall`, body
 * resolution, collision/overlap validation, or the Free-to-End badge, and it lives
 * outside [ShaftSpec] entirely (in [UndercutRecord], the document envelope).
 *
 * ## Coordinate rule
 * [startFromAftMm] is canonical **shaft space** — mm from the shaft's AFT face (x=0) to
 * the undercut's AFT edge. Undercuts are deliberately NOT keyed to a component: a cut
 * routinely sits inside a liner but may cross a liner edge or span several components,
 * so component-local storage would constrain what the shop can record. With no component
 * key there are no orphans and nothing to prune at decode; the only staleness is a span
 * extending past the current shaft extent (OAL shrank), which is a non-blocking warning
 * plus a render-layer clamp — the record itself is never mutated.
 *
 * [authoredReference] records which reference point the machinist entered the distance
 * against, purely so the "Distance" field can re-display the same authored number on the
 * next visit. Switching it in the UI re-projects the displayed value only —
 * [startFromAftMm] never moves as a result. Editing the shaft later (moving a taper,
 * changing OAL, repositioning the reference liner) moves the reference point, so the
 * *displayed* distance changes; the canonical position stays where it was authored, same
 * rule as [WearSpot].
 *
 * Units: mm.
 *
 * @property id Stable identifier.
 * @property startFromAftMm Shaft-space mm from the AFT face to the undercut's AFT edge.
 *   Canonical storage regardless of [authoredReference].
 * @property lengthMm Axial length of the undercut.
 * @property diaMm Measured Ø of the undercut floor — stored **verbatim** (golden rule:
 *   user inputs are sacred; no snap/round/derive ever rewrites it). `0` means the
 *   undercut was placed but no Ø has been entered yet: the detail overlay shows "—",
 *   the printed PDF skips the callout — same rule as [WearDiaReading.diaMm].
 * @property authoredReference Which reference point the distance was authored against
 *   (display-only; see [UndercutReference]).
 * @property referenceLinerId The [Liner.id] the distance converts against when
 *   [authoredReference] is a `LINER_*` value — display metadata only, never a geometry
 *   key: the undercut still lives in shaft space and renders wherever it is regardless
 *   of this liner. If the liner is later deleted, the Distance field falls back to the
 *   [UndercutReference.AFT_SET] projection (canonical untouched); empty for SET-authored
 *   undercuts.
 * @property note Free-text note (e.g. "weld undercut", "cleanup pass").
 */
@Serializable
data class Undercut(
    val id: String = UUID.randomUUID().toString(),
    val startFromAftMm: Float = 0f,
    val lengthMm: Float = 0f,
    val diaMm: Float = 0f,
    val authoredReference: UndercutReference = UndercutReference.AFT_SET,
    val referenceLinerId: String = "",
    val note: String = "",
)

/**
 * Per-document undercut record — the payload of the Undercut Drawing tab/PDF. Lives
 * beside [WearRecord] and `RunoutConfig` in the document envelope
 * (`ShaftDocCodec.ShaftDocV1`, `@SerialName("undercut_record")`) — NOT inside
 * [ShaftSpec] — so undercut data never touches geometry resolution, collision, or
 * coverage math. The field is additive + defaulted, so older files (no
 * `undercut_record`) round-trip to an empty record with no envelope version bump.
 *
 * @property undercuts Recorded undercut sections (see [Undercut]).
 * @property exaggerationFrac Drawn-depth exaggeration for this sheet, `0..0.25` (the
 *   geom-side cap `UNDERCUT_EXAGGERATION_MAX_FRAC`): the sheet's **deepest** cut draws
 *   at this fraction of its local surface Ø and shallower cuts scale relative to it
 *   (`normalizedNotchFloorDiaMm`), so sheets with very different absolute depths read
 *   alike. `0` = true scale. Display-only styling for the drawing — it never changes a
 *   stored or printed Ø — but it is per-document (a sheet keeps its chosen look), so it
 *   lives here rather than in app prefs. Additive + defaulted; older files get 0.25.
 */
@Serializable
data class UndercutRecord(
    val undercuts: List<Undercut> = emptyList(),
    val exaggerationFrac: Float = 0.25f,
)
