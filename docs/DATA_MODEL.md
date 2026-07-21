# ShaftSchematic Data Model
Version: v0.5.x
Last updated: 2026-07-21 — reverted "explicit bodies are non-negotiable" (false collision warnings on normal drafts); bodies are the fluid base again (no collision, plain bodies split around sacred components, keyed bodies protected). 2026-07-18 — component field listings corrected to match `model/*.kt`; fixed nonexistent `keywayHasSpoon` alias; removed a garbled duplicate tail section.

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
  component. Promote one to explicit with the "Make editable body" checkbox (or Add Body) to
  lock a span / add a keyway.

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
val Body.keywayAbsSpanMm(): Pair<Float, Float>?  // absolute AFT-origin span of the slot
val Taper.maxDiaMm get() = max(startDiaMm, endDiaMm)

Spec-level keyway note:
- ShaftSpec.keyways180Apart: Boolean (default false) — the shaft's keyways are clocked
  180° apart. Meaningful only when spec.keywayCount() >= 2 (UI + PDF gate on that).
- ShaftSpec.hiddenKeywayHostIds(): Set<String> — when the flag is set, the aft-most
  keyway (smallest absolute center, the measurement datum) stays solid; every other
  keyway's host id is returned so the renderer/PDF draw it as a hidden feature (dashed,
  no void fill). No geometric effect — pure drawing classification.
- Taper.keywayAbsSpanMm(): Pair<Float, Float>?  // absolute AFT-origin span (for clocking)
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
Format:
@Serializable
data class ShaftDocV1(
    val version: Int = 1,
    val preferredUnit: UnitSystem = UnitSystem.INCHES,
    val unitLocked: Boolean = true,
    val spec: ShaftSpec
)
Migration:

Backfill missing UUIDs

Normalize thread pitch/tpi relationships

`couplerBoltSlots` round-trips automatically through `ShaftDocCodec` with no schema/version bump: the list defaults empty and decode uses `ignoreUnknownKeys`, so documents written before the field decode unchanged.

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
- app/src/main/java/com/android/shaftschematic/docs/Rendering.md (in-source preview rendering contract)