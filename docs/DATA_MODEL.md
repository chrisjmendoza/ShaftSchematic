# ShaftSchematic Data Model
Version: v0.4.x

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
Body
@Serializable
data class Body(
    override val id: String = UUID.randomUUID().toString(),
    override val startFromAftMm: Float = 0f,
    override val lengthMm: Float = 0f,
    val diaMm: Float = 0f,
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
    val keywaySpooned: Boolean = false, // aka “keywayHasSpoon”
) : Segment

Keyways are features, not standalone components.
They are currently taper-associated and cannot exist without a host.

Note: Keyways may also be added to Body components in a future revision.

Keyway invariants (hosted feature):
- keywayLengthMm >= 0
- keywayLengthMm <= host component length
Derived:
val Taper.maxDiaMm get() = max(startDiaMm, endDiaMm)
val Taper.minDiaMm get() = min(startDiaMm, endDiaMm)
Threads
@Serializable
data class Threads(
    override val id: String = UUID.randomUUID().toString(),
    override val startFromAftMm: Float = 0f,
    val majorDiaMm: Float = 0f,
    val pitchMm: Float = 0f,
    override val lengthMm: Float = 0f,
    val excludeFromOAL: Boolean = false,
    val tpi: Float? = null
) : Segment
Normalization:
If pitch present & tpi missing → compute tpi
If tpi present & pitch missing → compute pitchMm
Liner
@Serializable
data class Liner(
    override val id: String = UUID.randomUUID().toString(),
    override val startFromAftMm: Float = 0f,
    override val lengthMm: Float = 0f,
    val odMm: Float = 0f,
) : Segment
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

Hatch lines are drawn entirely in pixel space using fixed dp widths.  
Renderer never recalculates pitchMm → pixels.

---

# 4. Liner Contract

## Data
Outer sleeve/bearing liner.

Field:
- odMm

## Validation
- All fields ≥ 0
- endFromAftMm ≤ overallLengthMm

## Free-to-End
ViewModel computes:
freeToEndMm = overallLengthMm - (start + length)

yaml
 
No pixel math allowed.

## Rendering
Renderer draws:
- Top edge: shaftWidth
- Bottom edge: shaftWidth
- End ticks: dimWidth

Ticks emphasize liner boundaries without dominating the drawing.

---

# 5. Invariants Across Components

1. All mm → px conversion occurs ONLY in ShaftLayout.
2. Renderer never reads component mm fields directly for pixel lengths.
3. Renderer never performs pitch/diameter/derivation logic.
4. Components must preserve UUID across edits.
5. All validation errors occur in ViewModel, not renderer/UI.

This document is the authoritative source for component behavior.