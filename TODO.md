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
**Snapping** → not implemented (highest priority)  
**"Add at position" UX** → not implemented  
**Delete** → present; Insert-Between workflow still missing  
**Preview free-to-end badge** → doc-corrected; code update pending

---

## 1. Sprint Priority: Snapping + Add-At-Position Pipeline

### 1.1 Snap Engine (Pure mm-space, no UI math)

**Goal:** Provide ViewModel with deterministic snapping of axial measurements.

**Tasks**

- [ ] Create `SnapEngine.kt` in `ui/viewmodel/`
- [ ] Implement:
  - [ ] `buildSnapAnchors(spec: ShaftSpec): List<Float>`  
        _anchors = 0, overallLengthMm, all component start/end positions_
  - [ ] `snapPositionMm(rawMm, anchors, toleranceMm)`

**Requirements**

- No px math
- No UI dependencies
- Unit tested (edge cases: empty anchors, near-ties, far-out values)
- Default snap tolerance:
  - Metric UI: `toleranceMm = 1.0`
  - Imperial UI: `toleranceMm` equivalent of `0.04 in` (≈ 1.0 mm)
- Tolerance is always stored and applied in mm-space (UI converts from user-facing units)

---

### 1.2 Preview Tap → mm Conversion

**Goal:** Allow tapping the preview to specify an insertion point.

**Tasks**

- [ ] Add `onTapAtMm` lambda to `ShaftDrawing`
- [ ] Convert tap `Offset` → mm:  
      `(tapX - contentLeftPx) / pxPerMm + minXMm`
- [ ] Send result → ViewModel
- [ ] Snap using `SnapEngine` before storing
- [ ] Add `pendingAddPositionMm: Float?` to ViewModel

**Notes**

- UI must never decide geometry.
- VM must never know pixels.
- Only horizontal tap position matters; vertical is used for hit-testing only.

---

### 1.3 Add-At-Position Flow (Liners, Bodies, Tapers, Threads)

**Goal:** Insert components at user-chosen axial positions with correct prefilled defaults.

**Tasks**

- [ ] Add VM intent: `startAddAtPosition(positionMm)`
- [ ] Show chooser dialog: Body / Taper / Liner / Threads
- [ ] Prefill defaults based on axial gap to next anchor
- [ ] Use `freeToEndMm()` for tail-placement logic
- [ ] Body default length = distance to next anchor or 50 mm minimum (per UI contract)
- [ ] Run validation before confirm

**Constraints**

- Position must be treated exactly like add-from-carousel adds
- No direct manipulation of geometry in UI layer

---

## 2. Validation & UI Connection

### 2.1 Hook Validation System Into UI

**Goal:** Surface blocking errors + warnings consistently across dialogs and list.

**Tasks**

- [ ] Field-level error highlighting (red)
- [ ] Warning badges (yellow) in component list
- [ ] Disable "Confirm" when violations are blocking
- [ ] Warnings show inline but never block dialog-close
- [ ] On export: full validation; block on red, allow on yellow

**Extra**

- [ ] Add real-time (on-blur) validation checks for taper inputs
- [ ] Ensure taper-rate derivation logic is covered by validation, not UI hacks

---

## 3. Recent Contract Fixes That Need Code Implementation

_These are documented in ARCHITECTURE.md and TODO.md previously but not yet implemented._

### 3.1 Preview Badge: Free-to-End

- [ ] Compute `freeToEndMm` in mm-space only
- [ ] Replace any px- or layout-dependent logic (mm-space only, deterministic)
- [ ] Clamp negative to zero
- [ ] Use `safeSpec` if `overallLengthMm=0` (preview mode)

### 3.2 Taper-Rate Restoration

- [ ] Add taper-rate input handling
- [ ] Support formats: `1:12`, `3/4`, decimals, bare int
- [ ] Derive missing SET/LET when appropriate
- [ ] **If both SET and LET are filled, taper-rate input is ignored**
- [ ] Validate slope only when length > 0
- [ ] Persist new field in the model
- [ ] Ensure renderer remains unchanged (draws from diameters)

### 3.3 Components Empty-State UX (DONE)

- [x] Make entire empty-state card tappable (not just button)
- [x] Card tap and button share same add-handler
- [x] Proper ripple + accessibility semantics
- [x] Visual affordance now matches interaction

---

## 4. Tech Debt & Structural Cleanups

### 4.1 ShaftScreen.kt Refactor Plan

**Goal:** Reduce file size, isolate responsibilities, eliminate recursion issues.

**Plan**

- [ ] Extract carousel into `ComponentCarousel.kt`
- [ ] Extract preview region into `ShaftPreviewPanel.kt`
- [ ] Move event wiring into a dedicated `ShaftScreenController.kt` (VM → UI glue)
- [ ] Ensure controller owns all VM-side intents so composables stay stateless
- [ ] Keep `ShaftScreen.kt` as a coordinator only

### 4.2 Dialog Cleanup

- [ ] Standardize confirm/cancel patterns
- [ ] Standardize commit-on-blur across all fields
- [ ] Remove leftover legacy length-editing utilities

### 4.3 Build Tooling & Version Catalog Hygiene

- [ ] Keep Gradle wrapper, AGP, and libs.versions.toml in sync
- [ ] Do not revert Android Studio–initiated catalog updates unless breaking
- [ ] When tooling updates occur, isolate into chore(build) commit where possible

---

## 5. Testing Burndown

### 5.1 Unit

- [ ] SnapEngine
- [ ] `freeToEndMm()`
- [ ] Taper rate parsing + derivation
- [ ] Thread pitch ↔ TPI conversions

### 5.2 Instrumentation

- [ ] Commit-on-blur correctness
- [ ] Blocking-dialog behavior
- [ ] Preview-tap → adds at correct position
- [ ] Carousel scrolls to selected after tap in preview

---

## 6. Short-Term Backlog (v0.5.x After Snapping)

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

## 7. PDF Export Contract (Explicit)

- PDF pages must always paint a white background explicitly
- PDF rendering must not depend on app theme or system dark mode
- Export path must remain Canvas/PdfDocument-based (no Compose coupling)

---

## 8. Explicit Non-Goals (Do NOT Implement Yet)

- Multi-page PDF or foldouts
- DXF export
- BOM / machining tables
- Stress analysis or deflection math
- Non-linear scaling modes
- Any cloud sync or AI features

**These belong to the long-term roadmap and must not creep into v0.4.x.**

---

## 9. Future Ideas (v0.6.x+ / nice-to-have)

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
