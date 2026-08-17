# ShaftSchematic Data Model
Version: v0.5.x
Last updated: 2026-08-13 — added `ShaftSpec.autoDiaOverrides` (`AutoDiaOverride`: per-section
bare-shaft Ø for a single auto-body span, shaft-space anchor, dormant anchors never pruned);
`autoBodyDiaMm` demoted to the legacy shaft-wide fallback.
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
  card's editable Ø field instead writes a **per-section** `AutoDiaOverride` for that one span
  (see below) without promoting.

#### `AutoDiaOverride` — per-section bare-shaft Ø

```
@Serializable
data class AutoDiaOverride(
    val anchorMm: Float = 0f,   // shaft-space mm from the AFT face; system-placed (span midpoint at commit)
    val diaMm: Float = 0f,      // user-typed, stored VERBATIM (golden rule)
)
```

Individual auto sections may carry slightly different diameters without being promoted, so the
auto-body card's Ø field is **per-section**. An auto span whose extent contains an anchor —
the half-open interval `[startMm, endMm)` — draws at that `diaMm`.

- **Precedence per span:** aft-most anchor inside the span → `ShaftSpec.autoBodyDiaMm` (the
  legacy shaft-wide value, no UI writes it any more) → neighbor derivation
  (`resolveAutoBodyDia`). It also wins over the `normalizeBodies` diameter-continuity carry,
  and a section-authored run seeds no continuity forward, so an override never leaks into the
  next auto run.
- **Merge rule:** delete the component separating two auto sections and the gaps join into one
  run holding both anchors — the run takes the **aft-most** override's Ø, because the aft
  section is authored first. The fwd one lies dormant.
- **Shaft-space keying, no orphans:** auto spans have no stored row and their ids are
  position-derived, so anchors are stored in shaft space (the `Undercut` / `WornSection`
  posture). An anchor that lands inside a component, or inside a gap absorbed into an
  explicit-body run, is **dormant** — not applied, never pruned at decode — and resurrects
  unchanged if its span reappears.
- **Draw-only:** never affects OAL/coverage, span positioning, body resolution, collision, or
  the Free-to-End badge. Written by `ShaftSpec.withAutoSectionDia` (upsert; `≤ 0` clears).

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
    // 0 = open keyway (starts at the face); > 0 = floating (rounded both ends).
    val keywayOffsetFromEndMm: Float = 0f,
    val keywayEnd: LinerAuthoredReference = LinerAuthoredReference.AFT,  // which face the offset is measured from
    val keywaySpooned: Boolean = false,
    val label: String? = null,  // optional user-defined display label; not used for geometry
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
    // > 0 = floating keyway (inset from SET, rounded at both ends).
    val keywayOffsetFromSetMm: Float = 0f,
    val keywaySpooned: Boolean = false,  // no "keywayHasSpoon" alias exists
    val taperRateText: String = "",  // user-authored rate text (e.g. "1:12"); derived/validated in the ViewModel
    val authoredReference: LinerAuthoredReference = LinerAuthoredReference.AFT,
    val label: String? = null,  // optional user-defined display label; not used for geometry
) : Segment

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
) : Segment
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
    val authoredReference: LinerAuthoredReference = LinerAuthoredReference.AFT,
    val endMmPhysical: Float = 0f,  // kept in sync with start + length by Liner.normalized()
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
    val showDimensionRail: Boolean = false,    // deferred — no rail drawn in v1
    val label: String = "",
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
freeToEndMm
freeToEndMm
fun ShaftSpec.freeToEndMm(): Float =
    (overallLengthMm - coverageEndMm()).coerceAtLeast(0f)
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
    val spec: ShaftSpec,
    val runoutConfig: RunoutConfig = RunoutConfig(),     // @SerialName("runout_config")
    val wearRecord: WearRecord = WearRecord(),           // @SerialName("wear_record")
    val runoutReadings: RunoutReadings = RunoutReadings(),// @SerialName("runout_readings")
    val runoutStationPlacements: RunoutStationPlacements  // @SerialName("runout_stations")
        = RunoutStationPlacements(),
    val undercutRecord: UndercutRecord = UndercutRecord() // @SerialName("undercut_record")
)

**Reference-only inspection records** live in the envelope, never in `ShaftSpec`, so they
can never affect geometry resolution:

- `WearRecord(spots, pits, diaReadings, wornSections)` — liner wear bands (`WearSpot`,
  keyed by `linerId`), pit "X" markers (`WearPit`), measured-diameter readings
  (`WearDiaReading`) — the middle two keyed by *resolved* component id with
  component-local `axialMm` — and worn sections (`WornSection`).
- `WornSection` — a designated measured area authored on the Consolidated Output tab. Unlike
  the other wear marks it is **shaft-space** (`startFromAftMm` + `lengthMm`), so it may
  cross component edges: no component key, no orphans. `authoredReference` reuses
  `UndercutReference` SET values as display-only Distance metadata (canonical never moves on
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
- `UndercutRecord(undercuts, exaggerationFrac)` — its own envelope field
  (`@SerialName("undercut_record")`), sibling of `wear_record`. Each `Undercut` is a
  machined-below-surface span stored in **shaft space** (`startFromAftMm` + `lengthMm`) — a
  cut may sit inside a liner or straddle a component edge, so undercuts are deliberately
  **not** component-keyed: there are no orphans and nothing is pruned at decode. The
  authored Distance reference (AFT/FWD SET or a reference liner's edge) is display-only
  metadata; canonical geometry never moves on a reference switch. `diaMm` is a typed
  measurement stored verbatim (`0` = placed-but-empty, never printed).
  `exaggerationFrac` (`0..UNDERCUT_EXAGGERATION_MAX_FRAC` = 0.25, default 0.25) is a
  per-sheet **drawn-depth** exaggeration only — printed Ø values stay the stored numbers.

**Orphan policy** differs by key type: wear *spots* (liner-id-keyed, ids the codec knows)
are pruned at **decode**; pits, measured-Ø readings, runout readings, and runout station
placements key on resolved ids (incl. auto-bodies) the codec cannot know, so their orphans
are skipped at the **render layer** and survive decode untouched. Worn sections and
undercuts are shaft-space, so the question does not arise — they are never pruned.

Migration:

Backfill missing UUIDs

Normalize thread pitch/tpi relationships

`couplerBoltSlots`, `keyways180Apart`, `keyways90Apart`, `keyways90Cw`, `autoBodyDiaMm`,
`showAutoBodyDia`, `autoDiaOverrides`, and
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