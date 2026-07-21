# Component vs Feature Contract
Version: v0.5.x
Last updated: 2026-07-21 — reverted "explicit bodies are non-negotiable" (it raised false collision warnings on normal drafts); bodies are the fluid base again — they don't collide, and plain bodies split around sacred components while keyed bodies are protected.

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

- **Coupler Bolt Slot**: reference overlay for muff-coupling bolt cutouts.
  - A first-class list in `ShaftSpec` (`couplerBoltSlots`) and implements `Segment`, but it is a **pure reference marker**, not a geometric span.
  - Draws a row of `count` cutouts (pitch `spacingMm`, diameter `holeDiaMm`) straddling the shaft outline; `through`/`depthMm` distinguish through vs blind.
  - Excluded from OAL/`coverageEndMm`, excluded from collision detection (`collisionGroup()` → null), and never splits or merges bodies.
  - Position authored from AFT/FWD (default FWD); `showDimensionRail` is deferred (no rail drawn in v1). See DATA_MODEL.md for the full field list.

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
  - Generated from OAL + explicit components (`ui/resolved/ResolvedComponent.kt`,
    `deriveAutoBodies()`), tagged `ResolvedComponentSource.AUTO`
  - Removed or split as explicit components occupy their span
- **Manual bodies** (explicit, current):
  - Persisted components stored in `ShaftSpec.bodies`
  - Replace auto bodies in overlapping regions
  - Never coexist with auto bodies in the same region

**Rule:** Manual body components promote over auto bodies in any overlapping span. This
promotion is live today: the carousel's auto-body card promotes on an explicit user action —
editing Start/Length/Ø, **or** ticking "Make editable body" — via `promoteIfNeeded()`
(`ComponentCarousel.kt`), persisting the section as a real `Body` in `ShaftSpec`. Viewing an
auto-body card without acting never promotes it.

### Explicit bodies are the fluid base (reverted 2026-07-21)

An explicit (stored) body is the shaft's base material / filler — **not** a rigid
collider. The "explicit bodies are non-negotiable" experiment was reverted because it
raised false collision warnings on normal drafts.

- A body does **not** participate in collision detection. `collidingIds()` checks only
  taper/thread/liner pairs (sacred-vs-sacred), never bodies. A body legitimately runs
  UNDER a liner (a sleeve over the shaft) and UP AGAINST a taper.
- There is **no** hard-block on adding or moving a sacred component over a body. Adding a
  taper/thread/liner over a plain body **splits** the body (`splitBodiesAround`) as it
  always did. The `bodyOverlapErrorMm` / `nonBodyOverlapErrorMm` helpers and the
  liner↔body "boundary negotiation" (`linerBodyBoundaryAdjust` /
  `updateLinerWithBodyBoundary`) no longer exist.
- The resolve layer (`subtractBodiesAgainstNonBodies`) trims the *drawn* body around
  overlapping components, so a *stored* body span crossing a liner/taper is not a conflict.

**Light protection (kept):** a body that HAS A KEYWAY is never split — it stays one whole
card (keyway intact) and the resolve layer still trims it for drawing. Plain (unkeyed)
bodies split as before.

**Engine guard (kept):** on delete, `mergeBodiesAround` refuses to merge two flanking
bodies across a component that still occupies the freed span, preventing a long phantom body.

---

## 3) Feature Rules

- Features are attached to a **host component**.
- Features have no independent axial ordering; they do not participate as independent segments.
- Features are validated and rendered relative to their host.
- Features are emitted in PDF/export as annotations of the host component (not as independent items).

---

## 4) Keyway Feature

- Keyways are **features**, not components.
- Supported on **Tapers** (taper-hosted) and **Bodies** (body-hosted). Body keyways were
  un-shelved 2026-07-20: intermediate shafts with fitted couplings carry keyways in plain
  cylindrical bodies at the shaft ends (the shaft can simply end on a body).
- Keyways will **never** exist as standalone components.
- At most **one keyway per host** component; a shaft has as many keyways as it has
  keyway-bearing hosts.

Keyway attributes (host-owned, `model/Taper.kt` / `model/Body.kt`):
- `length` (stored as `keywayLengthMm`)
- `width` (stored as `keywayWidthMm`)
- `depth` (stored as `keywayDepthMm`)
- reference offset:
  - Taper: `keywayOffsetFromSetMm` — measured from the SET face
  - Body: `keywayOffsetFromEndMm` — measured from the referenced end face, with
    `keywayEnd` (AFT | FWD) selecting which face
  - In both: 0 = open keyway at the referenced face, > 0 = floating keyway inset from it
- `spoon flag` (stored as `keywaySpooned` — there is no `keywayHasSpoon` alias)

Body keyways survive body split/merge by **absolute position**: `carryBodyKeyway`
(`model/ShaftSpecExtensions.kt`) re-anchors the offset to the surviving fragment's face,
and drops the keyway if a cut passes through it.

### Keyways 180° apart

`ShaftSpec.keyways180Apart` states the shaft's keyways are clocked 180° from each other.
It is only meaningful when `spec.keywayCount() >= 2`; UI surfaces the toggle only then,
and the PDF prints "Keyways 180° apart" in the footer's middle column under the same
condition.

**Hidden-line rendering.** When the flag is set (≥ 2 keyways), the keyway nearest the AFT
face — the shop's measurement datum — stays **solid** (near side); every other keyway is
drawn as a **hidden feature**: dashed outline (`HIDDEN_DASH_ON`/`HIDDEN_DASH_OFF` =
6/4 px) with **no** white void fill, since the near surface is unbroken in a plan view.
`ShaftSpec.hiddenKeywayHostIds()` is the single source of this classification — it returns
the host IDs to draw hidden, and both `ShaftRenderer` (preview) and `ShaftPdfComposer`
(export) consume it, so the two surfaces never diverge. This is the standard drafting
convention for a feature on the far side of the part; the footer note stays as well.

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
