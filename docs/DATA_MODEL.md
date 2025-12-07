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
) : Segment
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

# 📄 **COMPONENT_CONTRACTS.md**
```
# Component Contracts
Version: v0.4.x

## Overview
This document defines the exact rules for **validation**, **interpretation**, and **rendering responsibilities** for each component type in ShaftSchematic.

Rendering rules here describe geometric intent only; pixel computation always occurs in the Layout Engine.

---

# 1. Body Contract

## Data
Body defines a constant-diameter cylindrical section.

Fields:
- startFromAftMm
- lengthMm
- diaMm

## Validation
- All fields ≥ 0
- endFromAftMm ≤ overallLengthMm

## Rendering
Body renders as two horizontal lines at +radius and –radius, from xStart to xEnd.

Renderer uses:
top = cy - rPx(diaMm/2)
bottom = cy + rPx(diaMm/2)
left = xPx(start)
right = xPx(end)

 

Stroke rules:
- Top/bottom edges: `shaftWidth`
- No end-ticks (bodies visually merge into adjacent bodies/tapers)

---

# 2. Taper Contract

## Data
Taper defines a linear transition between two diameters.

Fields:
- startDiaMm
- endDiaMm

## Validation
- lengthMm > 0
- diameters ≥ 0
- startFromAftMm ≥ 0
- endFromAftMm ≤ overallLengthMm

## Taper Rate Logic
Parsing supported:
- 1:12
- 1/12
- decimal ("0.0833")
- legacy integer (“1” → “1:12”)

Rules:
1. If both SET and LET provided → taperRate ignored.
2. If one missing → derive from taperRate.
3. If both missing → invalid input.

ViewModel performs derivation; renderer never interprets taperRate.

## Rendering
Renderer draws:
- Top edge sloping from startRadius → endRadius
- Bottom edge parallel to top

Stroke: `shaftWidth`.

---

# 3. Thread Contract

## Data
External thread definition.

Fields:
- majorDiaMm
- pitchMm (mm/turn)
- tpi (optional)
- lengthMm

## Validation
- All fields ≥ 0
- pitchMm may be 0 (renders with no hatch)
- Thread section must lie within overallLengthMm

## Normalization
Rules:
- If pitch present but tpi missing → compute tpi
- If tpi present but pitch missing → compute pitchMm
- If both present → leave as-is

## Rendering
Thread area renders as:
- Outer rectangle (major diameter)
- 45° hatch lines spaced at a constant pixel spacing (e.g., 8px)

Renderer uses:
left = xPx(start)
right = xPx(end)
radius = rPx(majorDia/2)
top = cy - radius
bottom = cy + radius

yaml
 

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