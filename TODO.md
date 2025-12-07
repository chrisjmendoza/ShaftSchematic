# ShaftSchematic TODO
Version: v0.4.x Work Queue

This file tracks the current development work for ShaftSchematic.  
It is organized by priority, aligned with the v0.4.x architecture and docs.

---

## 0. Status Snapshot

- Core model, layout, renderer, and PDF export contracts are documented.
- Single-page PDF export design is stable.
- Validation rules are defined (blocking vs warnings).
- UI contract (commit-on-blur, tap-to-clear, no geometry in UI) is defined.
- No snapping or “add at position” UX implemented yet.

---

## 1. Immediate Coding Tasks (Next Sprint)

### 1.1 Snap Engine (mm-space)

**Goal:** Make start/length inputs snap to useful anchors (AFT, OAL, component ends) without introducing any pixel math into the ViewModel.

- [ ] Add `SnapConfig` + helpers in a new file (proposed):  
  `app/src/main/java/.../ui/viewmodel/SnapEngine.kt`
- [ ] Implement:
  - [ ] `buildSnapAnchors(spec: ShaftSpec): List<Float>`
        - Include: 0 mm, overallLengthMm, all component start/end positions.
  - [ ] `snapPositionMm(rawMm: Float, anchors: List<Float>, toleranceMm: Float): Float`
- [ ] Write unit tests:
  - [ ] Snaps within tolerance to nearest anchor.
  - [ ] Returns raw value when no anchor within tolerance.
  - [ ] Works with empty anchor list (returns raw).

Snap logic must be **pure mm-space** and contain **no UI or px-per-mm logic**.

---

### 1.2 Tap-to-Add Position on Preview

**Goal:** Let the user tap the preview to choose where to add a component (liner, shoulder, etc.).

- [ ] Update `ShaftDrawing` composable to accept:
      `onTapAtMm: (Float) -> Unit`
- [ ] Convert tap `Offset` → axial mm using `ShaftLayout.Result`:
      - `mm = (tapX - contentLeftPx) / pxPerMm + minXMm`
- [ ] Pass snapped mm to ViewModel (using Snap Engine).
- [ ] Store in ViewModel as `pendingAddPositionMm`.

No geometry decisions in UI; just forward the mm position.

---

### 1.3 “Add Component at Position” Flow

**Goal:** Use `pendingAddPositionMm` to pre-fill Add dialogs with sensible defaults that land between existing components.

- [ ] Add VM intent: `startAddAtPosition(positionMm: Float)`
- [ ] Add simple chooser dialog:
      - “Add at 1234 mm”
      - Options: [Body], [Taper], [Threads], [Liner] (and future: [Shoulder], [Keyway], [Coupling])
- [ ] For each type, prefill:
      - `startFromAftMm = snapped position`
      - `lengthMm = gap to next anchor (or freeToEndMm)`

- [ ] Ensure all values go through validation before commit.

---

## 2. Validation & UI Wiring

### 2.1 Connect VALIDATION_RULES to UI

- [ ] Surface blocking errors as:
      - Red field highlights
      - Disabled “Save/Confirm” in dialogs
- [ ] Surface warnings as:
      - Yellow icons / badges in component list
- [ ] On export attempt:
      - [ ] Run full validation
      - [ ] Block on errors, allow on warnings

---

## 3. Testing & Hardening

- [ ] Add unit tests for:
      - SnapEngine behavior
      - Taper derivation (SET/LET/taperRate)
      - Thread pitch↔tpi normalization
      - freeToEndMm calculation

- [ ] Add instrumentation tests for:
      - Commit-on-blur behavior
      - Dialog stays open on blocking error
      - Export blocking on invalid state

---

## 4. Short-Term Backlog (Post-Snap)

- [ ] Selection + highlight behavior (selected component → add-near-selected defaults).
- [ ] Inline “Add here” buttons between components in the list.
- [ ] Preset library for common tapers / bodies (v0.6.x roadmap item).

---

## 5. Non-Goals for v0.4.x

- Multi-page PDFs
- BOM tables in PDF
- DXF export
- Compression / not-to-scale rendering
- Machining stress calculations

These must not be implemented until explicitly promoted into the roadmap.