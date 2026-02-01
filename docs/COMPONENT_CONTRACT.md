# Component vs Feature Contract
Version: v0.4.x

This document is **normative**.
It defines what constitutes a **component** versus a **feature** in ShaftSchematic.

---

## 1) Overview

### Component
A **component** is an axial, ordered entity that occupies a span along the shaft’s X axis.
Components:
- have `startFromAftMm` and `lengthMm`
- participate in ordering and layout decisions
- are stored as first-class lists in `ShaftSpec`

### Feature
A **feature** is a cut/annotation owned by a **host component**.
Features:
- cannot exist independently
- have no standalone axial ordering
- are validated and rendered relative to their host component

---

## 2) Component Rules

- Components occupy an axial span: `[startFromAftMm, startFromAftMm + lengthMm]`.
- Components participate in:
  - ordering and snapping (measurement-space, mm)
  - overlap checks (warnings only unless a rule explicitly blocks)
  - naming/identification (stable `id`)
  - export (PDF consumes component lists)

### Current components
- **Body**: constant-diameter span.
  - Can be adjacent to other components; diameter discontinuities are allowed (may warn).

- **Taper**: linear transition span between two diameters.
  - Taper parameter derivation/normalization occurs in the ViewModel; renderer draws from diameters.

- **Liner**: sleeve/bearing span.
  - Describes OD over a span; may overlap other components (typically allowed; may warn).

- **Threads**: threaded span.
  - Intended for shaft-end threading; enforcement of “end-only” constraints is a ViewModel/validation rule.

---

## 2.1 Implicit Body Spans (Derived)

- Not components; they are derived gaps between components.
- Computed deterministically from resolved geometry and never stored in `ShaftSpec`.
- Fill axial regions not occupied by explicit components.
- Split/retreat automatically when explicit components are added.
- Never overlap explicit components.
- Do not participate in snapping.
- Must never define measurement references.
- Must never be persisted.
- May be promoted to explicit Body components when editing is required.

### Auto vs Manual Bodies (Important Distinction)

- **Auto bodies** (derived):
  - Ephemeral and read-only
  - Generated from OAL + explicit components
  - Removed or split as explicit components occupy their span
- **Manual bodies** (explicit, future):
  - Persisted components stored in `ShaftSpec`
  - Replace auto bodies in overlapping regions
  - Never coexist with auto bodies in the same region

**Rule:** Manual body components promote over auto bodies in any overlapping span.

---

## 3) Feature Rules

- Features are attached to a **host component**.
- Features have no independent axial ordering; they do not participate as independent segments.
- Features are validated and rendered relative to their host.
- Features are emitted in PDF/export as annotations of the host component (not as independent items).

---

## 4) Keyway Feature

- Keyways are **features**, not components.
- Currently supported on **Tapers** (taper-hosted).
- Planned support for **Bodies** (body-hosted).
- Keyways will **never** exist as standalone components.

Keyway attributes (host-owned):
- `length` (stored as `keywayLengthMm`)
- `spoon flag` (stored as `keywaySpooned`, aka “keywayHasSpoon”)

---

## 5) Ownership Boundaries

- **ViewModel owns**:
  - validation
  - normalization/derivation
  - enforcement of component/feature rules (including what constitutes an “existing” feature)

- **Renderer owns**:
  - visualization only
  - must not infer geometry or reinterpret feature intent

- **PDF export owns**:
  - consuming validated data
  - emitting output derived from component and feature fields
  - no re-validation and no reinterpretation of rules
