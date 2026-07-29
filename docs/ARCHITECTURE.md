# ShaftSchematic Architecture
Version: v0.5.x
Last updated: 2026-07-28 — documented the document envelope + reference-only record family
(wear spots/pits/measured-Ø readings, runout readings/config), the shared pure-math `geom/`
layer and its draw-both-sites rule, the three-document PDF layer (schematic / runout / wear,
blank drafts, preview-as-rasterized-PDF), and corrected auto-body promotion to the
checkbox-only path ("Explicit body") with the no-snap golden rule. 2026-07-21 — reverted
"explicit bodies are non-negotiable" in the ViewModel layer notes (bodies are fluid fillers
again, no collision, plain bodies split around sacred components). 2026-07-18 —
resolved-component pipeline documented as shipped; PDF export corrected to a separate
drawing path; removed the nonexistent "Overall" dimension label claim.

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
Defines immutable data classes (Body, Taper, Threads, Liner, CouplerBoltSlot) and the root ShaftSpec aggregate.

`CouplerBoltSlot` is a **pure reference overlay** (muff-coupling bolt cutouts): it is
excluded from OAL/`coverageEndMm`, excluded from collision detection, and never splits
bodies. Its list defaults empty, so older documents decode unchanged (no migration).

**Reference-only records outside `ShaftSpec` (the document envelope).** Inspection data
never touches geometry resolution, so it lives beside the spec in the document envelope
(`doc/ShaftDocCodec.ShaftDocV1`), not inside it:

- `WearRecord` (`wear_record`): liner **wear spots** (bands, keyed by `linerId` — orphans
  pruned at decode), **wear pits** ("X" markers), and **measured-Ø readings**
  (`WearDiaReading` — value callouts with leaders on the wear document). Pits and readings
  key on *resolved* component ids (liner/taper/body, explicit or auto), which the codec
  cannot know — their orphans are skipped at the **render layer**, never pruned at decode.
- `RunoutReadings` (`runout_readings`): per-station TIR value + high-spot clock marker,
  keyed by `(componentId, stationIndex)`; render-layer orphan handling.
- `RunoutConfig`: station-count overrides + TIR orientation.

All are additive/defaulted (no envelope version bumps) and share one contract: they never
affect `coverageEndMm`/OAL, body resolution, collision, or the Free-to-End badge. The
authoritative invariants live in `CLAUDE.md` and
`app/src/main/java/com/android/shaftschematic/docs/RunoutSheet.md`.

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

Resolved component pipeline (shipped):
- `ui/resolved/ResolvedComponent.kt` implements `resolveComponents(spec, overallIsManual)`,
  which resolves explicit components and calls `deriveAutoBodies()` to fill the gaps.
- Auto bodies (`ResolvedComponentSource.AUTO`, `ResolvedComponentType.BODY_AUTO`) are **not**
  persisted in `ShaftSpec` — they are regenerated deterministically on every resolve.
- Auto bodies are downstream of OAL + explicit components and never overlap them
  (`subtractBodiesAgainstNonBodies()` carves body spans around sacred components for rendering).
- Auto bodies must never define measurement references or snapping anchors.
- Manual OAL seeds a base auto body spanning 0 → OAL; derived OAL does not
  (`deriveAutoBodies(overallLengthMm = if (overallIsManual) spec.overallLengthMm else 0f, …)`).
- **Explicit vs auto bodies (reverted 2026-07-21):** both stored `ShaftSpec.bodies` (explicit)
  and derived auto-bodies are fluid base material / fillers. Bodies **do not** collide —
  `collidingIds()` checks only taper/thread/liner pairs (sacred-vs-sacred). There is no
  hard-block on adding/moving a component over a body; the removed `bodyOverlapErrorMm` /
  `nonBodyOverlapErrorMm` helpers no longer exist. (The "explicit bodies are non-negotiable"
  experiment was reverted — it raised false collision warnings on normal drafts.)
- **Body split/merge:** adding a taper/thread/liner over a plain body splits it
  (`splitBodiesAround`); a body that has a keyway is never split (kept whole, trimmed for
  drawing by `subtractBodiesAgainstNonBodies`). On delete, `mergeBodiesAround` heals flanking
  bodies but refuses to merge across a component still occupying the freed span (phantom-body guard).
- **Auto-body promotion is checkbox-only.** An auto-body card shows Start/Length as disabled
  derived fields — there is no field-edit promotion path. Promotion happens only when the user
  ticks the **"Explicit body"** checkbox (`ComponentCarousel.kt`), which calls `onAddBody(...)`
  with the derived span to persist a real `Body`; unchecking an explicit body's checkbox opens a
  confirm dialog and demotes via `onRemoveBody`. The auto-body card's **Ø field** is editable but
  does NOT promote — it sets the single shared bare-shaft diameter (`ShaftSpec.autoBodyDiaMm`,
  0 = derive from neighbors). See the "Auto-body promotion" invariant in `CLAUDE.md`.
- **Golden rule — user inputs are sacred.** Committed field values reach the ViewModel
  verbatim: no snap, rounding, or "helpful" adjustment on any typed-commit path (the old
  snap-to-anchor update wrappers were removed 2026-07-26). Snapping exists only for coarse
  gestures (tap-to-add, `ui/viewmodel/SnapUtils.kt`).
- **Reference-only state flows.** The ViewModel also owns `_wearRecord`, `_runoutReadings`,
  and `_runoutConfig` (plain state updates, no geometry side effects), wired into autosave,
  snapshot restore, JSON import/export, and `newDocument` exactly like the spec.

Liner authored reference:
- Liners store authored reference (AFT/FWD) as metadata.
- Physical geometry remains canonical for layout, snapping, and export.
- Switching authored reference must never mutate physical geometry.

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
- Draw coupler bolt slot cutouts (row of circles straddling the shaft outline, mirrored top/bottom)
- Draw dimension-style elements (ticks, hatch)

Note: the renderer draws no text/labels at all (no "Overall" label or otherwise). See
`app/src/main/java/com/android/shaftschematic/docs/Rendering.md` for the current contract.

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

### 4.5 Shared Pure-Math Layer (`geom/`)
Android-free, JVM-unit-tested placement/measurement engines shared by the Compose canvases
AND the PDF composers, so paired renderings are identical by construction (the repo's
**draw-both-sites rule**: any feature drawn on both a canvas and a PDF must flow through one
shared engine — never re-implemented per renderer):

- `OalComputations.kt` / `OalWindow.kt` / `SetPositions.kt` — OAL window + AFT/FWD SET
  positions (the measurement datums every document brackets).
- `RunoutBubbleLayout.kt` — runout bubble stations/rows/x-solve/leader routing with
  engine-verified collision guarantees (canvas preview ⇔ runout PDF).
- `RunoutReadingMath.kt` — bubble clock/hit-test math for recorded TIR values.
- `WearPitMath.kt` — pit "X" sizing/hit-test/clamp (overlay canvas ⇔ wear PDF).
- `WearDiaMath.kt` + `WearDiaCalloutLayout.kt` — measured-Ø witness-tick hit-testing and
  the label-width-aware callout placement engine (single-row fan → two-row stagger with
  dogleg leaders → flagged compression), used by the wear overlay canvas, the wear PDF's
  liner strips, and its main-profile band.
- `KeywaySpoonMath.kt` — spooned-keyway bowl math (schematic canvas ⇔ schematic PDF).
- `DiameterCalloutLayout.kt` / `DeterministicTierAssigner.kt` — schematic Ø-callout tiering
  and dimension-rail tier assignment (PDF-only consumers, kept here for JVM testability).

Rule: this layer never imports Android/Compose classes and never depends on `pdf/` or `ui/`.

---

### 5. Compose UI Layer (`ui/drawing/compose/`, `ui/screen/`, `ui/input/`)
Responsibilities:
- Display grid, labels, and the Canvas host
- Show dialogs for component creation and editing
- Handle text input with commit-on-blur
- Display validation errors and warnings
- Persist and expose user preferences (units, grid, preview colors)
- Host the three document tabs (Schematic / Runout Sheet / Wear Document —
  `ui/nav/`, `RunoutRoute.kt`, `WearRoute.kt`) and the full-screen
  `ComponentWearDetailOverlay` (tap a component on the wear canvas → broken-out segment
  with explicit tool chips: Add X / Remove X pit markers, Add Ø measured-diameter readings)

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

**Preview rendering (`ShaftLayout` + `ShaftRenderer`) and the PDF composers
(`ShaftPdfComposer`, `RunoutPdfComposer`, `WearPdfComposer`) are SEPARATE drawing paths.**
They share model and layout-math *concepts* but not code: `ShaftPdfComposer` never calls
`ShaftLayout.compute()` and instead has its own fit functions — `computeBodyOnlyPtPerMm`,
`computeDetailPtPerMm`, `computePdfPtPerMmFitAxes` — and its own drawing functions
(`drawBodiesPlain`/`drawBodiesCompressedCenterBreak`, `drawTapers`, `drawThreads`, `drawLiners`).

Rules:
- Computes its own `ptPerMm` from page content width/height and overallLengthMm (see the three
  fit functions above); does not reuse `ShaftLayout`'s `pxPerMm`.
- Draws title block + shaft geometry using its own drawing routines

The **runout sheet** and **wear document** composers scale to the **SET-to-SET span** (not
`overallLengthMm`) because field measurements originate at the SET faces; both accept the
resolved component list (auto-bodies included) and a `blankValues` write-in mode
(lines-in/values-out hand-fill templates). The wear document's per-liner detail strips and
measured-Ø callout bands are laid out by the pure `pdf/WearStripLayout.kt` +
`geom/WearDiaCalloutLayout.kt`. All in-app PDF previews **rasterize the real composed PDF**
(there is no separate preview draw path for these documents). Contracts:
`docs/PDF_EXPORT.md` and the in-source `docs/RunoutSheet.md`.

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