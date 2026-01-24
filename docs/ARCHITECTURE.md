# ShaftSchematic Architecture
Version: v0.4.x

## Overview
ShaftSchematic is an MVVM-based Android application designed to create accurate, dimensionally consistent shaft schematics for marine propulsion work. The system is built around a strict separation of responsibilities:

- **Model Layer:** Immutable geometry data (ShaftSpec + components).
- **ViewModel Layer:** All business logic, parsing, validation, state management.
- **Layout Engine:** Converts millimeter geometry into pixel coordinates using a two-axis fit.
- **Rendering Engine:** Draws all geometry using precomputed pixel coordinates.
- **UI Layer:** Compose-based screens, dialogs, and input controls. Contains *no* geometry or layout logic.

All geometry is stored in **millimeters**. Rendering is performed at arbitrary resolutions without loss of accuracy.

---

## System Flow (End-to-End)

User Input → ViewModel → ShaftSpec (model) → ShaftLayout (layout engine)
→ ShaftRenderer (geometry) → ShaftDrawing (Compose Canvas)
→ Screen → PDF Export (optional)

 

---

## Architectural Layers

### 1. Model Layer (`model/`)
Defines immutable data classes (Body, Taper, Threads, Liner) and the root ShaftSpec aggregate.

Key constraints:
- All geometry is millimeters.
- No UI fields, no rendering fields.
- All fields are `val`; mutations use `.copy()`.
- Model performs only *basic* validation (bounds, non-negative values).

Serialization is implemented via Kotlinx Serialization.  
Migrations guarantee backwards compatibility (ID backfill, thread pitch normalization).

---

### 2. ViewModel Layer (`ui/viewmodel/`)
Responsibilities:

- Owning the authoritative `StateFlow<ShaftSpec>`
- Handling user edits through stable, atomic update operations
- Converting raw strings into typed numbers (with error handling)
- Applying taper rate rules
- Performing full validation (blocking + non-blocking)
- Maintaining `_componentOrder` for UI ordering
- File import/export

The ViewModel **never** performs any pixel or rendering logic.

---

### 3. Layout Engine (`ui/drawing/render/ShaftLayout.kt`)
Purpose: Convert millimeter geometry into pixel coordinates.

Rules:
- Computes pxPerMm from **two-axis fit**:  
  Fit horizontally to overallLengthMm and vertically to maxOuterDiaMm; use the smaller scale.
- Computes:
  - min/max axial positions
  - centerlineYPx
  - pixel offsets for all geometry
- Provides helper functions:
  - `xPx(mm)`
  - `rPx(radiusMm)`

**Layout never uses UI density and never performs business logic.**

**Layout does NOT compress or distort geometry.**

---

### 4. Rendering Engine (`ui/drawing/render/ShaftRenderer.kt`)
The single source of truth for drawing geometry.

Responsibilities:
- Draw bodies, tapers, threads, liners
- Draw dimension-style elements (ticks, hatch)
- Draw the **single "Overall" dimension label** (UI never draws this)

Renderer rules:
- Consumes pixel coordinates ONLY from ShaftLayout.Result
- Never calculates px-per-mm manually
- Never reads mm fields directly for pixel math
- Only uses `shaftWidth` and `dimWidth` from DrawingConfig

Renderer does NOT:
- Draw grid
- Perform validation
- Modify or interpret model data

---

### 5. Compose UI Layer (`ui/drawing/compose/`, `ui/screen/`, `ui/input/`)
Responsibilities:
- Display grid, labels, and the Canvas host
- Show dialogs for component creation and editing
- Handle text input with commit-on-blur
- Display validation errors and warnings
- Persist and expose user preferences (units, grid, preview colors)

UI must NOT:
- Compute layout logic
- Compute geometry
- Perform scaling or px-per-mm computation
- Draw any shaft geometry (except grid/labels)

UI passes:
spec + layoutResult + renderOptions → ShaftDrawing

 

---

## PDF Export (`pdf/ShaftPdfComposer.kt`)
Purpose: Produce a **single-page** PDF export of the drawing.

Rules:
- Uses the **same layout engine** (no compression)
- Uses the **same renderer** (full geometric fidelity)
- Computes `pxPerMmPDF` from page content width and overallLengthMm
- Draws title block + rendered shaft

PDF export does NOT:
- Create multiple pages
- Create a BOM table
- Summarize components
- Change geometry scale non-uniformly

### Tiering & Measurement Invariants (PDF)
Tier origin controls rail stacking only. Measurement reference controls numeric baselines. Units are independent of both. These concerns must never be conflated.

---

## Component Ordering System
A separate StateFlow `_componentOrder` stores UI ordering for the component list.

- Uses stable UUIDs
- Must update after spec updates to avoid race conditions
- Does not affect render order of geometry (renderer uses geometric order)

---

## Invariants
1. Model uses millimeters only.
2. Layout produces pixel coordinates only; never reads mm directly.
3. Renderer consumes pixel coordinates only; never computes px-per-mm.
4. UI performs no geometry or scaling logic.
5. PDF export is single-page, exact-scale.

---

## Thread Safety & State Rules
- All mutating operations use `_spec.update { … }` for atomic state updates.
- Ordering mutations (`orderAdd`, `orderRemove`) occur **after** spec updates.
- Validators run in ViewModel before state commit.

---

## Summary
This architecture ensures clarity, correctness, and strict separation of geometry, rendering, and UI concerns. It supports reliable feature growth (future components, DXF export, machining calculators) without architectural changes.

This file is authoritative for all developer decisions.