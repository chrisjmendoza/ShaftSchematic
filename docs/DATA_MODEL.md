# ShaftSchematic Data Model
Version: v0.5.x
Last updated: 2026-10-27 — `AutoDiaOverride`: a section override no longer moves a
neighbouring section's Ø, and a component add/remove that merges or covers its section drops it
(`withAutoDiaOverridesSurviving`); `autoBodyDiaMm` is written again — the auto card's "All auto
sections" choice and the new-shaft Ø prompt (`withAutoBodyDia`).
2026-10-22 — `UndercutRecord` listing completed (`drawToSet`, `showTotalRail`,
`pageOrientation`, `setDatum`, `excludedLinerIds`, `excludedFreeCutIds`) and `WearRecord`'s
per-job sheet fields listed (`traceDepthFrac`, `dyePenResult`, `stripComponentIds`,
`showShaftProfile`, `compactStrips`, `stripSizeFrac`).
2026-08-13 — added `ShaftSpec.autoDiaOverrides` (`AutoDiaOverride`: per-section
bare-shaft Ø for a single auto-body span, shaft-space anchor); `autoBodyDiaMm` demoted to the
shaft-wide fallback.
2026-08-05 — envelope listing gains `undercutRecord`
(`@SerialName("undercut_record")`) and `WearRecord.wornSections`; added the `UndercutRecord`
and `WornSection` bullets (shaft-space spans, no orphans, never pruned at decode).
2026-07-30 — added `keyways90Apart`/`keyways90Cw` (90°-apart clocking,
mutually exclusive with `keyways180Apart`). 2026-07-28 — ShaftSpec sample corrected to include `keyways180Apart` +
`autoBodyDiaMm`; documented the document envelope's reference-only records (WearRecord
spots/pits/measured-Ø readings, RunoutReadings) and their orphan policies; auto-body
promotion corrected to the checkbox-only "Explicit body" path. 2026-07-21 — reverted
"explicit bodies are non-negotiable" (false collision warnings on normal drafts); bodies are
the fluid base again (no collision, plain bodies split around sacred components, keyed
bodies protected). 2026-07-18 — component field listings corrected to match `model/*.kt`;
fixed nonexistent `keywayHasSpoon` alias; removed a garbled duplicate tail section.

## Overview
The data model defines all immutable geometric entities that compose a shaft schematic. All measurements are stored in **millimeters** (`Float`). No rendering or UI logic is present in this layer.

The root aggregate is `ShaftSpec`, which contains lists of component types and the overall shaft length.

---

## Core Structures

### ShaftSpec
```
@Serializable
data class ShaftSpec(
    val overallLengthMm: Float = 0f,
    val bodies: List<Body> = emptyList(),
    val tapers: List<Taper> = emptyList(),
    val threads: List<Threads> = emptyList(),
    val liners: List<Liner> = emptyList(),
    val couplerBoltSlots: List<CouplerBoltSlot> = emptyList(),
    val keyways180Apart: Boolean = false,  // drawing note: keyways clocked 180° apart
    val keyways90Apart: Boolean = false,   // drawing note: keyways clocked 90° apart (mutually exclusive with keyways180Apart)
    val keyways90Cw: Boolean = true,       // 90°-apart direction from the AFT keyway, viewed from aft; meaningful only when keyways90Apart
    val autoBodyDiaMm: Float = 0f,         // LEGACY shaft-wide bare-shaft Ø; fallback for spans with no override, 0 = derive from neighbors
    val showAutoBodyDia: Boolean = false,  // one Ø-callout visibility for ALL auto spans (draw-only)
    val autoDiaOverrides: List<AutoDiaOverride> = emptyList(),  // per-section bare-shaft Ø, keyed by shaft-space anchor
    val autoBlends: List<AutoBlend> = emptyList(),  // shaft-space blended faces on auto-body spans
)
Responsibilities:
```
Defines the global boundary (overallLengthMm)

Holds typed component lists

Performs structural validation (bounds, non-negative)
```

Segment Interface
All axial components implement:
```
interface Segment {
    val id: String
    val startFromAftMm: Float
    val lengthMm: Float
}
```
Helpers:

val Segment.endFromAftMm: Float get() = startFromAftMm + lengthMm
fun Segment.isWithin(overallLengthMm: Float) =
    endFromAftMm <= overallLengthMm + 1e-3f && startFromAftMm >= 0f
Components

### Component Priority

**Sacred components** (Taper, Threads, Liner) have positional priority. Their authored positions define the shaft geometry. Default start-position calculations for new components are based only on where these end.

**Bodies describe raw shaft material** between sacred components. They are excluded from collision detection and from new-component default-start calculations.

#### Explicit vs auto bodies (reverted 2026-07-21)

Both stored `ShaftSpec.bodies` (**explicit**) and derived **auto-bodies** are fluid base
material / fillers. (The "explicit bodies are non-negotiable" experiment was reverted — it
raised false collision warnings on normal drafts.)

- **Bodies do not collide.** `collidingIds()` checks only taper/thread/liner pairs
  (sacred-vs-sacred), never bodies. A body legitimately runs under a liner and up against a
  taper; the resolve layer (`subtractBodiesAgainstNonBodies`) trims the *drawn* body around
  those components, so a stored body span crossing them is not a conflict.
- **No hard-block on adds/moves over a body.** The removed `bodyOverlapErrorMm` /
  `nonBodyOverlapErrorMm` helpers and the liner↔body "boundary negotiation"
  (`linerBodyBoundaryAdjust` / `updateLinerWithBodyBoundary`) no longer exist.
- **Auto-bodies** (derived at resolve via `deriveAutoBodies`, never stored) flow around every
  component. Promote one to explicit with the **"Explicit body"** checkbox on its carousel card
  (or Add Body) to lock a span / add a keyway — the checkbox is the ONLY promotion path; the
  card's editable Ø field instead writes, by the user's choice in its scope dialog, a
  **per-section** `AutoDiaOverride` for that one span or the shaft-wide `autoBodyDiaMm` (see
  below) without promoting.

#### `AutoDiaOverride` — per-section bare-shaft Ø

```
@Serializable
data class AutoDiaOverride(
    val anchorMm: Float = 0f,   // shaft-space mm from the AFT face; system-placed (span midpoint at commit)
    val diaMm: Float = 0f,      // user-typed, stored VERBATIM (golden rule)
)
```

Individual auto sections may carry slightly different diameters without being promoted. The
auto-body card's Ø commit asks for its scope when the shaft has more than one auto section —
"This section" writes an override for that span, "All auto sections" writes
`ShaftSpec.autoBodyDiaMm` and clears every override (`withAutoBodyDia`; with one section the
value applies directly as All). An auto span whose extent contains an anchor — the half-open
interval `[startMm, endMm)` — draws at that `diaMm`.

- **Precedence per span:** aft-most anchor inside the span → `ShaftSpec.autoBodyDiaMm` (the
  shaft-wide value: "All auto sections" and the new-shaft Ø prompt) → neighbor derivation
  (`resolveAutoBodyDia`). It also wins over the `normalizeBodies` diameter-continuity carry.
- **Isolation:** an overridden run leaves the continuity carry exactly as it would have been
  WITHOUT the override (the carried Ø, else the span's own no-override Ø), so a section's
  override never moves a neighbouring section's Ø — before or after it.
- **Merge rule:** when several anchors fall inside one span (hand-edited or legacy data), the
  **aft-most** wins and the rest lie dormant — aft is authored first.
- **Pruning — an override dies with its section:** a component **add or remove**
  (`addBodyAt`/`addTaperAt`/`addThreadAt`/`addLinerAt` and their `remove*`, "Explicit body"
  promotion and demotion included) keeps an override only when its anchor sits in an auto run
  both before and after the edit and that run's start did not move AFT-ward
  (`withAutoDiaOverridesSurviving`, `ui/resolved/AutoDiaOverridePrune.kt`). Removing the
  component AFT of a section merges it into its AFT neighbour → its override goes; removing
  the one FWD of it → kept; a component added over the anchor → goes; one added upstream
  inside the span → kept; an override already dormant before the edit → goes. Undo restores
  it (the history holds the whole spec).
- **Shaft-space keying, no orphans:** auto spans have no stored row and their ids are
  position-derived, so anchors are stored in shaft space (the `Undercut` / `WornSection`
  posture). An anchor a resize or OAL change strands inside a component is **dormant** — not
  applied, never pruned at decode or by the resize — and applies again when its span
  reappears. (A gap beside an explicit body survives as its own run, so its anchors stay live
  while the gap exists.)
- **Draw-only:** never affects OAL/coverage, span positioning, body resolution, or collision.
  Written by `ShaftSpec.withAutoSectionDia` (upsert; `≤ 0` clears) and cleared wholesale by
  `ShaftSpec.withAutoBodyDia`.

#### Body Split / Merge

The split/merge engine keeps plain bodies flowing around sacred components:

- **On add** (taper / liner / in-shaft thread): any plain body whose span overlaps the new component is removed and replaced with up to two fragment bodies — one on each side of the new component. Each fragment gets a new UUID and inherits the parent's `diaMm`. **A body that has a keyway is never split** — it stays one whole card (keyway intact) and the resolve layer trims it for drawing instead.
- **On delete**: `mergeBodiesAround` searches for a body whose right edge aligns with the deleted component's start (within 0.5 mm) and a body whose left edge aligns with its end. If both are found they merge into one body spanning the entire region; the merged diameter is `max(leftDiaMm, rightDiaMm)`. If only one side exists (component was at a shaft boundary), that body expands to fill the freed span. **Engine guard:** the merge is refused when another component still occupies the freed span, preventing a long phantom body.
- **Keyway carry**: body-hosted keyways survive split/merge by absolute position — `carryBodyKeyway` re-anchors `keywayOffsetFromEndMm` to the surviving fragment's referenced face, and drops the keyway when a cut passes through its span. A merged body keeps at most one keyway (left fragment's preferred).

The user can adjust the merged body's diameter manually after a merge.

Each physical body section is its own carousel card (independent selection, independent edit).

Body
@Serializable
data class Body(
    override val id: String = UUID.randomUUID().toString(),
    override val startFromAftMm: Float = 0f,
    override val lengthMm: Float = 0f,
    val diaMm: Float = 0f,
    // Keyway is a cut feature owned by the host component (0 values = "no keyway").
    // Body keyways serve intermediate shafts with fitted couplings, where the shaft
    // ends on a plain body that carries the keyway.
    val keywayWidthMm: Float = 0f,
    val keywayDepthMm: Float = 0f,
    val keywayLengthMm: Float = 0f,
    // Axial distance from the referenced end face (keywayEnd) to the keyway slot.
    // 0 = open keyway (starts at the face); > 0 = captured (inset, mill arcs both ends).
    // "Captured" is derived from this value — no stored flag.
    val keywayOffsetFromEndMm: Float = 0f,
    val keywayEnd: LinerAuthoredReference = LinerAuthoredReference.AFT,  // which face the offset is measured from
    val keywaySpooned: Boolean = false,
    val label: String? = null,  // optional user-defined display label; not used for geometry
    val showDiaOnDrawing: Boolean = false,  // below-shaft Ø callout visibility; OPT-IN for bodies
    val showNameOnDrawing: Boolean? = null, // schematic name-label; tri-state: null follows the global titles switch, explicit true/false overrides it (all four kinds; retired showLabelOnDrawing key ignored at decode)
    val shadeOnDrawing: Boolean? = null,    // PDF fill; tri-state: null follows the kind's "Shade in Components" checkbox (auto runs also follow "Explicit bodies only"), explicit overrides (Body/Taper/Liner only)
    val compressOnDrawing: Boolean = true,  // draw-only; false pins this body's stored span at true scale and suppresses its S-break. Serialization default true (saved documents keep compressing); authoring creates explicit bodies with false
    val showDimensionRail: Boolean? = null, // own-length dimension span on the schematic + consolidated sheet; tri-state: null follows position (on when the stored span touches a shaft end face, off mid-shaft), explicit true/false overrides it
    // Blended faces (draw-only): curve length inward from each face, 0 = square.
    val blendAftMm: Float = 0f,
    val blendFwdMm: Float = 0f,
    val blendAftSeal: Boolean = false,  // seal area: 3 radius cuts at the aft end, measured from the face (square or blended) — independent of blendAftMm
    val blendFwdSeal: Boolean = false,
    val blendAftSealLenMm: Float = 0f,  // seal-area length, measured from the face; 0 = follow blendAftMm
    val blendFwdSealLenMm: Float = 0f,
    val blendProfile: BlendProfile = BlendProfile.OGEE,
) : Segment
Taper
@Serializable
data class Taper(
    override val id: String = UUID.randomUUID().toString(),
    override val startFromAftMm: Float = 0f,
    override val lengthMm: Float = 0f,
    val startDiaMm: Float = 0f,
    val endDiaMm: Float = 0f,
    // Keyway is a cut feature owned by the host component (Taper).
    // 0 values represent “no keyway”.
    val keywayWidthMm: Float = 0f,
    val keywayDepthMm: Float = 0f,
    val keywayLengthMm: Float = 0f,
    // Axial distance from the SET face to the start of the keyway slot.
    // 0 = open keyway (starts at SET face, open-ended there).
    // > 0 = captured keyway (inset from SET, mill arcs at both ends); derived, no stored flag.
    val keywayOffsetFromSetMm: Float = 0f,
    val keywaySpooned: Boolean = false,  // no "keywayHasSpoon" alias exists
    val taperRateText: String = "",  // user-authored rate text (e.g. "1:12"); derived/validated in the ViewModel
    val authoredReference: LinerAuthoredReference = LinerAuthoredReference.AFT,
    val label: String? = null,  // optional user-defined display label; not used for geometry
    // Rate mode as the user chose it: true = Manual (taperRateText kept through geometry edits),
    // false = Auto, null = saved before the field (seeded from the text). Additive, mode only.
    val taperRateManual: Boolean? = null,
) : Segment

Blend fields on Body (drawing-only, additive, default 0/OGEE):
- `blendAftMm` / `blendFwdMm`: axial length of a machined **blend** cut INWARD from that face
  (0 = a square face). The curve eases from the neighbouring component's diameter AT the face to
  the body's own `diaMm` that far in, so the blend is machined out of its own body and never
  moves or trims another component. Diameters are DERIVED at resolve, never stored. Where a liner
  butts the face (a seal area) the curve leaves from the midpoint of the liner OD and `diaMm`
  (`seatDiaUnderLiner`) — the real seat is hidden under the liner and its depth varies job to job.
- `blendAftSeal` / `blendFwdSeal`: whether that end carries a seal area — the radius cuts the
  fiberglass seats into, drawn as 3 full-height dashed lines across the body (the silhouette stays
  flat — no notches). A section property, **independent of the blend length**: the seal area is
  measured **from the face** and sits in the same place whether the face is square
  (`blend*Mm == 0`) or blended — a ramp may overlap its outer end. A face with no step to blend
  drops its curve and keeps its grooves.
- `blendAftSealLenMm` / `blendFwdSealLenMm`: axial length of that seal area, measured from the face, typed and stored
  verbatim, drawing-only like the blend length (clamped only where DRAWN). `0` = follow the
  blend length — every document saved before the field existed decodes to it and keeps its
  grooves (on a square face a `0` resolves to no seal area drawn). Stored independently of the
  seal flag: unticking a seal keeps the typed length. Derived: `Body.blendSealLenMmOn(end)`.
- `blendProfile`: `OGEE` (tangent both ends, default) | `FILLET` (tangent at the large end only)
  | `EASED_CONE` (straight cone, both corners eased).
- Silhouette only: no dimension rail, no footer row, no effect on OAL, coverage, resolve spans,
  or collision. Rails keep dimensioning the STORED span (dimension to the theoretical sharp
  corner). A stored length longer than the body is clamped where it is DRAWN, never rewritten.
- Derived: `Body.hasBlendOn(end)`, `Body.blendMmOn(end)`; geometry in
  `ui/resolved/BodyBlends.kt`, curve math in `geom/BlendProfileMath.kt`.

`ShaftSpec.autoBlends: List<AutoBlend>` (additive, defaults empty) — blended faces on auto-body
spans, keyed in **shaft space** by anchor rather than by a component id, since auto ids are
position-derived and regenerate on every edit:
- `AutoBlend(anchorMm, end, lengthMm, profile, seal, sealLenMm)`. The span whose half-open extent
  `[startMm, endMm)` contains the anchor blends the named face; the anchor is system-placed at
  the span midpoint and carries no authored meaning. `lengthMm` is typed and stored verbatim.
- Aft-most anchor wins **per face**, so one span may carry both an aft and a fwd blend.
- Dormant under a component or inside an absorbed gap; **never pruned**, resurrects unchanged.
- Helpers: `List<AutoBlend>.autoBlendFor(startMm, endMm, end)`,
  `ShaftSpec.withAutoBlend(spanStartMm, spanEndMm, end, lengthMm, profile = OGEE, seal = false, sealLenMm = 0f)`
  (`model/AutoBlend.kt`; upsert — `lengthMm` ≤ 0 **with `seal = false`** clears that face only,
  and `seal`/`sealLenMm` carry the auto span's seal area the way `Body.blendAftSeal`/
  `blendAftSealLenMm` do for an explicit body; `sealLenMm` is stored verbatim, a negative coerced
  to 0, 0 = follow `lengthMm`). An anchor with `lengthMm = 0` and `seal = true` is a **seal-only
  face** — a square end whose seal area starts at the face; `autoBlendFor` honours it.

Keyways are features, not standalone components.
They are hosted on **Tapers** (offset from the SET face) or **Bodies** (offset from the
AFT/FWD end face selected by `keywayEnd`) and cannot exist without a host.
(Body-hosted keyways were un-shelved 2026-07-20 — intermediate shafts with fitted
couplings carry keyways in end bodies.)

Keyway invariants (hosted feature, both hosts):
- keywayLengthMm >= 0
- reference offset (keywayOffsetFromSetMm / keywayOffsetFromEndMm) >= 0
- reference offset + keywayLengthMm <= host component length
Derived:
val Taper.hasKeyway: Boolean get() = keywayWidthMm > 0f && keywayDepthMm > 0f && keywayLengthMm > 0f
val Body.hasKeyway:  Boolean get() = keywayWidthMm > 0f && keywayDepthMm > 0f && keywayLengthMm > 0f
val Body.keywayAbsSpanMm(): KeywaySpan?  // absolute AFT-origin span of the slot (loMm/hiMm/centerMm)
val Taper.maxDiaMm get() = max(startDiaMm, endDiaMm)

Spec-level keyway clocking note:
- ShaftSpec.keyways180Apart: Boolean (default false) — the shaft's keyways are clocked
  180° apart. Meaningful only when spec.keywayCount() >= 2 (UI + PDF gate on that).
- ShaftSpec.keyways90Apart: Boolean (default false) — the shaft's keyways are clocked
  90° apart instead of 180°. Same >= 2 keyway gate. Mutually exclusive with
  keyways180Apart — ShaftViewModel.setKeyways180Apart/setKeyways90Apart each clear the
  other flag when enabling.
- ShaftSpec.keyways90Cw: Boolean (default true) — direction of the 90° clocking, measured
  from the AFT keyway, viewed from aft (true = CW, false = CCW). Meaningful only when
  keyways90Apart is set.
- ShaftSpec.hiddenKeywayHostIds(): Set<String> — when keyways180Apart is set, the aft-most
  keyway (smallest absolute center, the measurement datum) stays solid; every other
  keyway's host id is returned so the renderer/PDF draw it as a hidden feature (dashed,
  no void fill). No geometric effect — pure drawing classification. (keyways90Apart uses a
  different rendering path — an edge notch, not a hidden dashed line — see
  docs/COMPONENT_CONTRACT.md "Keyway clocking — 180° / 90° apart".)
- Taper.keywayAbsSpanMm(): KeywaySpan?  // absolute AFT-origin span (for clocking)
Threads
@Serializable
data class Threads(
    override val id: String = UUID.randomUUID().toString(),
    override val startFromAftMm: Float = 0f,
    val majorDiaMm: Float = 0f,
    val pitchMm: Float = 0f,
    override val lengthMm: Float = 0f,
    val excludeFromOAL: Boolean = false,
    val isAftEnd: Boolean = true,
    val tpi: Float? = null,
    val label: String? = null,  // optional user-defined display label; not used for geometry
    val metricDesignation: String? = null,  // e.g. "M20×2.5"; parsed at entry, drives implicit mm unit override
    val showNameOnDrawing: Boolean? = null,  // tri-state name-label gate (see Body)
    val reliefLenMm: Float = 0f,     // relief groove length — PART of the stored span; 0 = none
    val reliefDiaMm: Float = 0f,     // relief groove Ø, stored verbatim
    val reliefAtAft: Boolean = false, // which end of the thread carries the groove
) : Segment {
    val hasRelief: Boolean  // reliefLenMm > 0 && reliefDiaMm > 0 — no stored flag
}
**Thread relief** (2026-10-03, on-device request): the plain groove turned below the thread's
root at the end where it runs up to its shoulder (on a propeller shaft, the taper's S.E.T.), so
the tool runs out and the nut seats. It is PART of the thread's stored span — the
`reliefLenMm` at the relief end is groove, the rest is thread — so adding one moves nothing:
`startFromAftMm`/`lengthMm` never change. Both values are stored verbatim (golden rule); an
authored length longer than the thread or a Ø at or above the major Ø is kept as typed, flagged
by an advisory (`threadWarningMessages`), and clamped only where DRAWN
(`geom/ThreadReliefMath.kt`). The three fields are additive and defaulted, so every document
saved before them decodes with no relief (`ThreadReliefCodecTest`); templates keep the relief
(it is geometry). `reliefAtAft` is seeded on the authoring surfaces from
`suggestedThreadReliefAtAft` (the thread's INBOARD end). A stored relief still at that suggested
end follows the thread when its "Thread end" or OAL switch changes
(`Threads.withReliefEndFollowing`); one put at the other end never moves.
`ResolvedThread` carries the three values verbatim (threads never fragment).

`isAftEnd` only matters when `excludeFromOAL = true`: true pins the thread's derived position
to the AFT end (start = 0, extending to negative mm outside the envelope), false pins it to the
FWD end (start = overallLengthMm). It is ignored when the thread counts toward OAL — position
is authored normally in that case.

Normalization:
If pitch present & tpi missing → compute tpi
If tpi present & pitch missing → compute pitchMm
Liner
@Serializable
data class Liner(
    override val id: String = UUID.randomUUID().toString(),
    override val startFromAftMm: Float = 0f,  // @SerialName("startMmPhysical")
    override val lengthMm: Float = 0f,
    val odMm: Float = 0f,
    val label: String? = null,  // optional user-defined display label; not used for geometry
    val showDiaOnDrawing: Boolean = true,  // below-shaft Ø callout visibility; defaults true (unlike Body)
    val showNameOnDrawing: Boolean? = null, // schematic name-label; tri-state, see Body
    val shadeOnDrawing: Boolean? = null,    // PDF fill; tri-state, see Body
    val authoredReference: LinerAuthoredReference = LinerAuthoredReference.AFT,
    val endMmPhysical: Float = 0f,  // kept in sync with start + length by Liner.normalized()
    // Shoulders: a machined step cut into the liner's OWN span at an end (the blend posture —
    // no other component's span moves). An end has a shoulder only when BOTH its length and
    // reduced OD are > 0; radius is the fillet where the step face meets the liner OD, picked
    // from a standard list and printed only as a footer note. All stored verbatim (golden
    // rule); all default 0, so documents that never touch shoulders decode byte-identical.
    val shoulderAftLenMm: Float = 0f,
    val shoulderAftOdMm: Float = 0f,
    val shoulderAftRadiusMm: Float = 0f,
    val shoulderFwdLenMm: Float = 0f,
    val shoulderFwdOdMm: Float = 0f,
    val shoulderFwdRadiusMm: Float = 0f,
) : Segment
`authoredReference` (AFT/FWD) only affects how the UI projects/displays the Start value; the
canonical `startFromAftMm`/`endMmPhysical` are always physical AFT-referenced geometry.
CouplerBoltSlot
@Serializable
data class CouplerBoltSlot(
    override val id: String = UUID.randomUUID().toString(),
    override val startFromAftMm: Float = 0f, // position of the first/aft-most cutout center
    val holeDiaMm: Float = 0f,
    val count: Int = 1,                        // user-defined, >= 1
    val spacingMm: Float = 0f,                 // axial center-to-center pitch between cutouts
    val through: Boolean = true,               // true = through-hole, false = blind
    val depthMm: Float = 0f,                   // blind depth; ignored when through
    val authoredReference: SlotAuthoredReference = SlotAuthoredReference.FWD,
    val showDimensionRail: Boolean = false,    // card-only; ON = one rail span, authored end → nearest hole center
    val label: String? = null,  // optional user-defined display label; not used for geometry
) : Segment

Derived axial footprint:
val CouplerBoltSlot.lengthMm get() = (count - 1) * spacingMm + holeDiaMm

`SlotAuthoredReference` selects the end the entered `startFromAftMm` is measured
from. When FWD-referenced the entered value locates the fwd-most cutout and the
row extends aft; the canonical `startFromAftMm` still stores the aft-most cutout
center, so physical geometry is reference-independent (same pattern as Liner).

    enum class SlotAuthoredReference { AFT, FWD }

Coupler bolt slots are a **pure reference feature** — they mark muff-coupling
bolt cutouts and never participate in shaft geometry:
- Excluded from `coverageEndMm()` and overall length (OAL) — never affect OAL.
- Excluded from collision/overlap validation (`collisionGroup()` → null).
- Never split or merge bodies (no body-split on add, no merge on remove).
- Resolved **after** body resolution, so they never participate in
  auto-body/subtraction geometry (see `ResolvedCouplerBoltSlot`).

Validation
Component-Level
Each component type implements:

Non-negative checks

Must lie within overallLengthMm

ShaftSpec-Level
fun ShaftSpec.validate(): Boolean { … }
Validation ensures structural integrity but allows:

Overlapping components

Non-continuous diameters

Non-uniform geometry

These conditions are handled at UI/UX level, not model layer.

Helpers
coverageEndMm
fun ShaftSpec.coverageEndMm(): Float = ...
maxOuterDiaMm
Used by layout engine for vertical fit.

Serialization & Migration
Format (the **document envelope**, `doc/ShaftDocCodec.ShaftDocV1` — abridged):
@Serializable
data class ShaftDocV1(
    val version: Int = 1,
    val preferredUnit: UnitSystem = UnitSystem.INCHES,   // @SerialName("preferred_unit")
    val unitLocked: Boolean = true,                      // @SerialName("unit_locked")
    val jobNumber: String = "", val customer: String = "", val vessel: String = "",
    val shaftPosition: ShaftPosition = ShaftPosition.OTHER,
    val notes: String = "",
    val item: String = "",   // optional shaft designation ("Tail shaft"); blank prints nothing, scrubbed from templates
    val spec: ShaftSpec,
    val finalSpec: ShaftSpec? = null,                    // @SerialName("final_spec") — the Final schematic; null = none yet
    val runoutConfig: RunoutConfig = RunoutConfig(),     // @SerialName("runout_config")
    val wearRecord: WearRecord = WearRecord(),           // @SerialName("wear_record")
    val runoutReadings: RunoutReadings = RunoutReadings(),// @SerialName("runout_readings")
    val runoutStationPlacements: RunoutStationPlacements  // @SerialName("runout_stations")
        = RunoutStationPlacements(),
    val undercutRecord: UndercutRecord = UndercutRecord(),// @SerialName("undercut_record")
    val unitOverrides: Map<String, UnitSystem> = emptyMap(), // @SerialName("unit_overrides")
    val dualUnits: Boolean = false                        // @SerialName("dual_units")
)

**The Final schematic** (`final_spec`) is the one envelope field that is NOT a reference
record: a whole second `ShaftSpec` — the drawing the shaft leaves with, after the wear and
undercut work moved a liner — created as a structural copy of `spec` (component ids included)
and independent from then on. `null` until "Start from original schematic"; never in a
template or a mate duplicate; the inspection records below stay keyed to the ORIGINAL. See
`docs/contracts/FinalSchematic.md`.

**Reference-only inspection records** live in the envelope, never in `ShaftSpec`, so they
can never affect geometry resolution:

- `WearRecord(spots, pits, diaReadings, wornSections, traceDepthFrac, dyePenResult,
  stripComponentIds, showShaftProfile, compactStrips, stripSizeFrac)` — liner wear bands
  (`WearSpot`, keyed by `linerId`), pit "X" markers (`WearPit`), measured-diameter readings
  (`WearDiaReading`) — the middle two keyed by *resolved* component id with
  component-local `axialMm` — and worn sections (`WornSection`). The last six are per-job
  wear-sheet fits and results, all additive + defaulted and none of them geometry:
  `traceDepthFrac` (`Float?`, `null` = follow `PdfPrefs.wearTraceDepthFrac`, resolved by
  `effectiveWearTraceDepthFrac`), `dyePenResult` (`DyePenResult?`, `null` = both boxes blank),
  `stripComponentIds` (`List<String>?`, `null` = every drawable liner; resolved ids, orphans
  skipped at render, never pruned), `showShaftProfile` (default `true`), `compactStrips`
  (default `false`), `stripSizeFrac` (default `1`).
- `WornSection` — a designated measured area authored on the Consolidated Output tab. Unlike
  the other wear marks it is **shaft-space** (`startFromAftMm` + `lengthMm`), so it may
  cross component edges: no component key, no orphans. `authoredReference` reuses
  the `AFT_SET`/`FWD_SET` values of `UndercutReference` as display-only Distance metadata (canonical never moves on
  a reference switch). `diaMm` is a **list** of typed measurements stored verbatim (golden
  rule); values ≤ 0 never print.
- `RunoutReadings` — per-station TIR value + high-spot marker, keyed by
  `(componentId, stationIndex)`.
- `RunoutStationPlacements` — its own envelope field (`@SerialName("runout_stations")`),
  one `RunoutStationPlacement` per bubble the user has **dragged** along its component,
  keyed like a reading by `(componentId, stationIndex)`. `axialMm` is component-local from
  the AFT edge (the `WearPit` convention) and measured in shaft space across a fragmented
  body's gaps. Storage is deliberately **partial**: a drag pins only the station it moved,
  and every station with no entry keeps deriving its position. Pins are stored verbatim
  (golden rule) — a pin stranded in a gap or beyond a shortened component is repaired at the
  **render layer**, never rewritten.
- **Auto-body keys.** Everything above keyed by resolved component id — readings, placements,
  `RunoutConfig.componentOverrides`, `WearPit`/`WearDiaReading`, `stripComponentIds` — may name
  a bare-shaft body, whose id is its span (`auto_body_<start>_<end>`). When an ORIGINAL edit
  re-identifies that span, the editor's spec seam renames those keys to the run's unambiguous
  successor in the same undo step (`geom/AutoBodyRekeyMath.kt`): component-local `axialMm`
  shifts by `oldStart − newStart` to hold its shaft position, and a derived station count that
  would change is frozen at its old value. A split or merge maps to nothing (orphaned at render
  as before). The id scheme itself is unchanged; nothing is re-keyed at decode.
- `UndercutRecord(undercuts, exaggerationFrac, drawToSet, showTotalRail, pageOrientation,
  setDatum, excludedLinerIds, excludedFreeCutIds)` — its own envelope field
  (`@SerialName("undercut_record")`), sibling of `wear_record`. Each `Undercut` is a
  machined-below-surface span stored in **shaft space** (`startFromAftMm` + `lengthMm`) — a
  cut may sit inside a liner or straddle a component edge, so undercuts are deliberately
  **not** component-keyed: there are no orphans and nothing is pruned at decode. The
  authored Distance reference (AFT/FWD SET, a reference liner's edge, or — for a cut sitting fully
  inside another — that parent cut's AFT/FWD shoulder: `UndercutReference.PARENT_AFT`/`PARENT_FWD`
  with the parent's id in the additive `Undercut.referenceCutId`, empty for every other reference)
  is display-only metadata; canonical geometry never moves on a reference switch. `diaMm` is a typed
  measurement stored verbatim (`0` = placed-but-empty, never printed).
  `exaggerationFrac` (`0..UNDERCUT_EXAGGERATION_MAX_FRAC` = 0.25, default 0.25) is a
  per-sheet **drawn-depth** exaggeration only — printed Ø values stay the stored numbers.
  `drawToSet` (`Boolean`, default `false`, additive — older files read `false`) is the per-job
  "Draw to the shaft end" switch: a liner strip whose chain ends within `UNDERCUT_SET_REACH_MM`
  (24 in) of its S.E.T. draws out to the shaft end and its chain rail ends at the S.E.T. Display and
  dimensioning only; no stored value moves. The other per-job fits, all additive + defaulted and
  none of them geometry: `showTotalRail` (`Boolean?`, default `null` = follow
  `PdfPrefs.undercutTotalRail`, resolved by `effectiveUndercutTotalRail`; `true`/`false` pins the
  job — "Total length of cuts"); `pageOrientation` (`UndercutPageOrientation` AUTO | LANDSCAPE |
  PORTRAIT, default AUTO = portrait at 2+ printed sections); `setDatum` (`UndercutSetDatum`
  NEAREST | AFT | FWD, default NEAREST — the S.E.T. every strip's title and lead measure to); both
  enums serialize by name through tolerant serializers (an unknown name decodes to the default).
  `excludedLinerIds` / `excludedFreeCutIds` are the "Sections to print" EXCLUSION lists — a liner
  or cut added later prints by default; orphan ids are skipped at render, never pruned.

**Orphan policy** differs by key type: wear *spots* (liner-id-keyed, ids the codec knows)
are pruned at **decode**; pits, measured-Ø readings, runout readings, and runout station
placements key on resolved ids (incl. auto-bodies) the codec cannot know, so their orphans
are skipped at the **render layer** and survive decode untouched. Worn sections and
undercuts are shaft-space, so the question does not arise — they are never pruned.

**Mixed units + dual display** (`unit_overrides`, `dual_units`) are a **display axis only** —
canonical geometry stays in millimeters everywhere (golden rule). `unitOverrides` maps a
resolved component id → its display `UnitSystem`; a component with no entry follows
`preferredUnit`. An override whose id no longer resolves is harmless (the resolver falls back
to `preferredUnit`) and is **not** pruned — the same render-layer posture as the readings
above. A metric-designation thread (`Threads.metricDesignation`, e.g. `M20×2.5`) carries an
implicit mm override registered by the ViewModel. A component's **keyway** may carry one of its own
under a derived key — `keywayUnitKey(id)` = `"<id>#kw"`, the same `#` convention a split body's runs
use — resolved by `DisplayUnits.keywayUnitFor` through the chain keyway → component → document.
It needs no new envelope field, and a key whose component no longer resolves is inert (never pruned,
the same posture as the readings above). `dualUnits` is the sheet-wide flag: when
true every dimension prints `<primary> [<secondary>]` inline (`pdf/UnitFormat.kt` `*Dual`),
seeded for new documents from a global default (`SettingsStore.dualUnitsDefaultFlow`) but
persisted per document. Overrides travel with a template (they describe authoring); the
per-job dual flag does not. The resolver is `util/DisplayUnits.kt`.

Migration:

Backfill missing UUIDs

Normalize thread pitch/tpi relationships

`couplerBoltSlots`, `keyways180Apart`, `keyways90Apart`, `keyways90Cw`, `autoBodyDiaMm`,
`showAutoBodyDia`, `autoDiaOverrides`, `autoBlends`, and
every envelope record above round-trip automatically through `ShaftDocCodec` with no
schema/version bump: each defaults empty/zero/false and decode uses `ignoreUnknownKeys`, so
documents written before a field existed decode unchanged.

Invariants
All geometry stored in millimeters.

All model types immutable (val fields only).

Every component has a stable UUID.

Model layer never computes pixel geometry.

Model layer never performs UI or rendering logic.

This document defines all geometry data structures in the system.

---

See also:
- docs/COMPONENT_CONTRACT.md (normative component vs feature rules)
- docs/UI_CONTRACT.md (UI, rendering, and responsibility boundaries)
- docs/contracts/Rendering.md (preview rendering contract)