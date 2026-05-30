# ShaftSchematic TODO

**Version: v0.4.x Development Queue (Updated)**

This file reflects the current active sprint, aligned with all contracts as of v0.4.x.  
Tasks are grouped by sequencing and dependency rather than category.

---

## 0. Current System State

**Core model** → stable  
**ShaftLayout & renderer** → contract locked  
**One-page PDF export** → stable, theme-safe, background explicitly painted  
**Validation rules** → formalized, ready for UI wiring  
**Taper components** → taper-rate logic restored in docs but not fully re-implemented  
**Carousel, selection logic** → recently repaired; refactor still pending  
**Snapping (edit/delete auto-snap)** → implemented + unit tested; tap-to-add snapping not implemented  
**"Add at position" UX** → not implemented  
**Delete** → present; Insert-Between workflow still missing  
**Preview free-to-end badge** → implemented in mm-space; preview-mode OAL=0 behavior may still need alignment  
**Internal saves (app storage)** → existing-saves list + filtering + overwrite confirmation  
**Default filenames** → add shaft position suffix when meaningful; never position-only (falls back to generated name)  
**Open drawing list** → long filenames wrap (no overlap with action/menu)

---

## 0.1 Recently Completed (UX/Workflow)

- [x] Default save name includes shaft position suffix (PORT/STBD/CENTER)
- [x] Position-only naming falls back to generated `Shaft_yyyyMMdd_HHmm`
- [x] Save screen shows existing saves under input and filters as user types
- [x] Clicking an existing save name populates the field for quick edits (mated jobs)
- [x] Overwrite confirmation when saving over an existing internal file
- [x] Open drawing list wraps long filenames to prevent overlap
- [x] PDF default filename uses same naming convention (job/customer/vessel + optional side)
- [x] SAF JSON default filename updated to match naming convention (future-proof; UI may not expose this yet)

### Tiering & Measurement System Stabilization (PDF + Preview)

- [x] Separate tier origin (rail stacking) from measurement reference (numeric baseline)
- [x] Enforce single global measurement reference for forced AFT/FWD modes
- [x] Preserve AUTO behavior for per-component anchor proximity
- [x] Ensure PDF numeric labels compute as |boundary − reference| in forced modes
- [x] Stabilize unit handling so tiering/measurement changes never affect units
- [x] Fix Settings → ViewModel → PDF export wiring for tiering mode (pdfPrefs sync)
- [x] Remove autosave restore gating on default unit (inches vs mm)
- [x] Add autosave restore skip logging for diagnostics

---

## 1. Sprint Priority: Snapping + Add-At-Position Pipeline

### 1.1 Snap Engine (Pure mm-space, no UI math)

**Goal:** Provide ViewModel with deterministic snapping of axial measurements.

**Tasks**

- [x] Create `SnapEngine.kt` in `ui/viewmodel/`
- [x] Implement:
  - [x] `buildSnapAnchors(spec: ShaftSpec): List<Float>`  
        _anchors = 0, overallLengthMm, all component start/end positions_
  - [x] `snapPositionMm(rawMm, anchors, toleranceMm)`

**Requirements**

- No px math
- No UI dependencies
- [x] Unit tested (edge cases: empty anchors, near-ties, far-out values)
- Source of truth for snapping (next features: tap-to-add / insert-at-position): **ViewModel**
      - UI may convert tap px→mm and pass **raw mm** to VM
      - VM performs snapping and stores `pendingAddPositionMm`
      - UI must not decide final snapped positions
- Default snap tolerance:
  - Metric UI: `toleranceMm = 1.0`
  - Imperial UI: `toleranceMm` equivalent of `0.04 in` (≈ 1.0 mm)
- Tolerance is always stored and applied in mm-space (UI converts from user-facing units)

---

### 1.2 Preview Tap → mm Conversion

**Goal:** Allow tapping the preview to specify an insertion point.

**Tasks**

- [x] Add `onTapAtMm` lambda to `ShaftDrawing`
- [x] Convert tap `Offset` → mm:  
      `(tapX - contentLeftPx) / pxPerMm + minXMm`
- [x] Send result → ViewModel
- [x] Snap using `SnapEngine` before storing
- [x] Add `pendingAddPositionMm: Float?` to ViewModel

**Notes**

- UI must never decide geometry.
- VM must never know pixels.
- VM is the snapping authority: UI passes raw mm.
- Only horizontal tap position matters; vertical is used for hit-testing only.

---

### 1.3 Add-At-Position Flow (Liners, Bodies, Tapers, Threads)

**Goal:** Insert components at user-chosen axial positions with correct prefilled defaults.

**Tasks**

- [x] Add VM intent: `setTapAddPosition(rawMm)` + `pendingAddPositionMm` state
- [x] Show chooser dialog: Body / Taper / Liner / Threads
- [x] Prefill defaults based on axial gap to next anchor (`gapToNextAnchorMm`, min 50mm)
- [ ] Use `freeToEndMm()` for tail-placement logic (deferred)
- [x] Body default length = distance to next anchor or 50 mm minimum (per UI contract)
- [ ] Run validation before confirm (deferred to 2.1)

**Constraints**

- Position must be treated exactly like add-from-carousel adds
- No direct manipulation of geometry in UI layer

---

### 1.4 Resolved Components + Auto Bodies (Planned)

**Goal:** Establish a derived component pipeline that generates auto bodies for UI/rendering without persisting them.

**Tasks**

- [ ] Implement resolved component pipeline with auto body generation for UI and rendering (no persistence).
- [ ] Seed initial auto body when OAL is manually authored.
- [ ] Expose AFT/FWD authored reference toggle in liner component card UI.
- [ ] Unify Add Component UX into single entry point after authored-reference UI is complete.

**Constraints**

- Auto bodies must never define measurement references.
- Auto bodies must never affect snapping anchors.
- Auto bodies must never be persisted.
- UI component ordering must be spatial (AFT→FWD), not insertion-based.

---

### HIGH 1.5 Add keyway support to Body components

Keyways are hosted features (not standalone components).

Scope:
- data model
- editor UI
- validation
- preview rendering
- PDF footer output

---

## 2. Validation & UI Connection

### 2.x Regression Note — Thread Start/Placement & Allowed Locations

- [x] REGRESSION: Fixed `applySnappedThreadUpdate` — only snaps start, preserves original
      length. The root cause was that snapping both start and end independently could
      accidentally extend a thread to the nearest anchor when the user moved it to position 0.

- [x] RULE: Threads are only allowed at shaft ends. Implemented in `startOverlapErrorMm`:
      returns error if a thread has a Body/Liner ending at-or-before its start AND another
      Body/Liner starting at-or-after its end (surrounded on both sides).

### 2.1 Hook Validation System Into UI

**Goal:** Surface blocking errors + warnings consistently across dialogs and list.

**Tasks**

- [x] Field-level error highlighting (red) — Start field in Add dialogs shows error text
- [x] Error badges in component carousel cards — Thread/Liner cards show error chip below title
- [x] Disable "Add" when violations are blocking — Add dialogs gate on `startOverlapErrorMm`
- [x] On export: full validation; block on blocking errors — `PdfExportRoute` checks before opening SAF picker
- [ ] Warning badges (yellow) — non-blocking warnings not yet shown (planned, next sprint)
- [ ] Warnings show inline but never block dialog-close (planned)

**Extra**

- [ ] Add real-time (on-blur) validation checks for taper inputs
- [ ] Ensure taper-rate derivation logic is covered by validation, not UI hacks

---

## 3. Recent Contract Fixes That Need Code Implementation

_These are documented in ARCHITECTURE.md and TODO.md previously but not yet implemented._

### 3.1 Preview Badge: Free-to-End

- [x] Compute `freeToEndMm` in mm-space only
- [x] Replace any px- or layout-dependent logic (mm-space only, deterministic)
- [x] Clamp negative to zero (**applies to model helper** `freeToEndMm()`; UI badge may use signed value for oversize warning)
- [ ] Use `safeSpec` if `overallLengthMm=0` (preview mode)

### HIGH 3.2 Taper-Rate Restoration (DONE)

- [x] Add taper-rate input handling (`taperRateText` field in Taper model; edit field in carousel)
- [x] Support formats: `1:12`, `3/4`, decimals, bare int (via `parseRateText`)
- [x] Derive missing SET/LET when appropriate (`deriveTaperDiameters`)
- [x] **If both SET and LET are filled, taper-rate input is ignored**
- [ ] Validate slope only when length > 0 (deferred to 2.1 validation wiring)
- [x] Persist new field in the model (`Taper.taperRateText`; backward-compatible default `""`)
- [x] Ensure renderer remains unchanged (draws from diameters)

### 3.3 Components Empty-State UX (DONE)

- [x] Make entire empty-state card tappable (not just button)
- [x] Card tap and button share same add-handler
- [x] Proper ripple + accessibility semantics
- [x] Visual affordance now matches interaction

---

## 4. Rendering / Component Enhancements (Backlog)

- [ ] Liner shoulders: add aft/fwd shoulder length fields and render stepped shoulders
- [ ] Keyways drawing: render keyway indicator on taper segments (schematic symbol), using existing KW dims
- [ ] FIBERGLASS: support fiberglassed body segments (model flag + renderer treatment TBD; decide hatch/pattern and labeling)

### 4.1 PDF Footer: AFT Taper Info Block Missing (DONE)

- [x] Fix end-feature detection to be thread-shoulder aware
- [x] Remove/neutralize redundant gating (hasAftTaper vs detector mismatch)
- [x] Preserve taper RATE formatting
- [x] Add JVM tests via `buildFooterEndColumns()` seam

### 4.2 Threads: “Count in OAL” Toggle (Create + Edit) (DONE)

- [x] Wire UI + dialog + ViewModel (`excludeFromOAL`)
- [x] Confirm OAL shifting uses threads only
- [x] Add JVM tests for `computeOalWindow` measurement-space shifting

---

## 5. Tech Debt & Structural Cleanups

### 5.1 ShaftScreen.kt Refactor Plan

**Goal:** Reduce file size, isolate responsibilities, eliminate recursion issues.

**Plan**

- [ ] Extract carousel into a production `ComponentCarousel.kt` (currently implemented inside `ShaftScreen.kt`; `ui/editor/ComponentCarousel.kt` exists but is not the wired implementation)
- [ ] Extract preview region into `ShaftPreviewPanel.kt`
- [ ] Move event wiring into a dedicated `ShaftScreenController.kt` (VM → UI glue)
- [ ] Ensure controller owns all VM-side intents so composables stay stateless
- [ ] Keep `ShaftScreen.kt` as a coordinator only

### 5.2 Dialog Cleanup

- [ ] Standardize confirm/cancel patterns
- [ ] Standardize commit-on-blur across all fields
- [ ] Remove leftover legacy length-editing utilities

### 5.3 Build Tooling & Version Catalog Hygiene

- [ ] Keep Gradle wrapper, AGP, and libs.versions.toml in sync
- [ ] Do not revert Android Studio–initiated catalog updates unless breaking
- [ ] When tooling updates occur, isolate into chore(build) commit where possible

### 5.x LOW — Post-Tiering Cleanup (Deferred)

- [ ] Audit tiering-related helpers for dead or redundant code (read-only until v0.5.x)
- [ ] Add optional debug overlay showing tier origin and measurement reference (preview only)
- [ ] Add internal documentation note describing tier origin vs measurement reference

---

## 6. Testing Burndown

### 6.1 Unit

- [x] SnapEngine
- [x] `freeToEndMm()`
- [x] Taper rate parsing + derivation (`TaperRateTest.kt`)
- [x] Thread pitch ↔ TPI conversions
- [x] `computeOalWindow` shifts measurement origin for excluded end threads
- [x] PDF footer end-feature detection for AFT/FWD taper blocks

### 6.2 Instrumentation

- [ ] Commit-on-blur correctness
- [ ] Blocking-dialog behavior
- [ ] Preview-tap → adds at correct position
- [ ] Carousel scrolls to selected after tap in preview

---

## 7. Short-Term Backlog (v0.5.x After Snapping)

_Not in the current sprint, but next in line._

- [ ] Selection → contextual "Add near selected" defaults
- [ ] Inline "Add here" buttons between components in list
- [ ] Start integrating Preset Library (common tapers, common shoulder patterns)
- [ ] Explore undo/redo architecture (heavy but needed soon)
- [ ] Unit system abstraction (core stays mm, UI can be metric or imperial)
- [ ] Quick conversion helper:
      - Inline mm ↔ in calculator for dialogs
      - Reuse same conversion engine as dimension display

---

## 8. PDF Export Contract (Explicit)

- PDF pages must always paint a white background explicitly
- PDF rendering must not depend on app theme or system dark mode
- Export path must remain Canvas/PdfDocument-based (no Compose coupling)

**Guardrails**

- Single source of truth for footer end-feature presence is `detectEndFeatures()`
- Keep `buildFooterEndColumns()` internal + unit-tested; avoid regressions to draw-only logic

Tiering Guardrail: Tier origin (rail stacking), measurement reference (numeric baseline), and units are independent concerns. Any future changes to one must not affect the others. See commit history for the tiering/measurement stabilization baseline.

### 8.1 Next PDF Priority: Legibility (After Footer Fix)

- [x] Center dimension labels over measured spans (midpoint-centered; bounded vertical bump only, no horizontal shifting)
- [x] Arrowheads inside/outside based on fit
- [x] Reduce inch decimals to 3 places (fallback when not a clean fraction)
- [x] Fraction display for PDF inch dims (mixed fractions snapped to nearest 1/16)

_Deferred until after layout stability:_ tolerance settings

---

## 9. Explicit Non-Goals (Do NOT Implement Yet)

- Multi-page PDF or foldouts
- DXF export
- BOM / machining tables
- Stress analysis or deflection math
- Non-linear scaling modes
- Any cloud sync or AI features

**These belong to the long-term roadmap and must not creep into v0.4.x.**

---

## 10. Future Ideas (v0.6.x+ / nice-to-have)

_These are explicitly out-of-scope for current work, but worth keeping on the radar._

- [ ] Dual-unit dimension display:
      - Primary units (shop preference: likely inches)
      - Secondary units (mm) in smaller text or on a second line
      - PDF export contract update to support dual-line dims
- [ ] Advanced conversion utilities:
      - Saved "favorite conversions" (e.g., 1" keyway depths, common shaft diameters)
      - Possibly a dedicated "Conversions" screen or tool

**Design Note (Mental Model)**

- Core stays mm-only.
- UI presents units based on user preference (mm or in).
- Snap tolerance is conceptually "1mm worth of slack", which we mirror in imperial as ~0.04 in, so the feel of snapping is the same regardless of units.
- You'll never be debugging geometry in both mm and inches; you'll just debug mm, and the UI does language translation.
