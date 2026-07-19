# ShaftSchematic Data Model
Version: v0.5.x
Last updated: 2026-07-18 — component field listings corrected to match `model/*.kt` (Body/Taper/Threads/Liner gained fields since this was last accurate); fixed nonexistent `keywayHasSpoon` alias; body keyway marked shelved; removed a garbled duplicate tail section.

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

#### Body Split / Merge (automatic)

Bodies are **independent spec entities** — each has its own UUID, position, length, and `diaMm`. The engine manages split/merge automatically:

- **On add** (taper / liner / in-shaft thread): any body whose span overlaps the new component is removed and replaced with up to two fragment bodies — one on each side of the new component. Each fragment gets a new UUID and inherits the parent's `diaMm`.
- **On delete**: the engine searches for a body whose right edge aligns with the deleted component's start (within 0.5 mm) and a body whose left edge aligns with its end. If both are found they merge into one body spanning the entire region; the merged diameter is `max(leftDiaMm, rightDiaMm)`. If only one side exists (component was at a shaft boundary), that body expands to fill the freed span.

The user can adjust the merged body's diameter manually after a merge.

Each physical body section is its own carousel card (independent selection, independent edit).

Body
@Serializable
data class Body(
    override val id: String = UUID.randomUUID().toString(),
    override val startFromAftMm: Float = 0f,
    override val lengthMm: Float = 0f,
    val diaMm: Float = 0f,
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
They are currently taper-associated and cannot exist without a host.

Body-hosted keyways are **shelved** — see `docs/ROADMAP.md` v1.0 "Non-goals": no marine
propeller shaft use case has been identified. Do not plan against this landing.

Keyway invariants (hosted feature):
- keywayLengthMm >= 0
- keywayOffsetFromSetMm >= 0
- keywayOffsetFromSetMm + keywayLengthMm <= host component length
Derived:
val Taper.hasKeyway: Boolean get() = keywayWidthMm > 0f && keywayDepthMm > 0f && keywayLengthMm > 0f
val Taper.maxDiaMm get() = max(startDiaMm, endDiaMm)
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