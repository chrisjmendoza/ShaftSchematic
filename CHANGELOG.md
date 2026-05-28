# Changelog

All notable changes to **ShaftSchematic** will be documented in this file.

The format is inspired by [Keep a Changelog](https://keepachangelog.com/) and follows [Semantic Versioning](https://semver.org/).

---

## Versioning Notes

- Early development used git tags (`v0.2.0`, `v0.3.1`) for milestones.
- Starting with `1.1.1`, the changelog and the app `versionName` are kept in sync; future releases follow this convention.
- Note: `v0.2.0` and `v0.3.0` point to the same commit (`d1a4da5`).

## 2026-05-28 (audit low items)

- Fixed `hasCenterBreak` footer note: replaced disconnected mm-space heuristic with the same `bodyLengthMm × ptPerMm ≥ COMPRESS_TRIGGER_PT` condition used by the actual rendering code.
- `VALIDATION_RULES.md`: marked all documented-but-unimplemented non-blocking warnings as `(planned — not yet implemented)` so the doc accurately reflects current state.
- `BRIEFING.md`: updated sprint status — tap-to-select is shipped (✅), resolved component pipeline is partial (not "not started").
- Added cross-reference comments to the duplicate `END_EPS_MM = 0.5` constants in `OalComputations.kt` and `ShaftPdfComposer.kt`.

## 2026-05-28 (audit items)

- Fixed PDF component label collision: labels now use greedy row assignment so overlapping labels (e.g. AFT Thread + AFT Taper at the same position) stack into separate rows instead of printing on top of each other.
- Deleted ~200 lines of dead code from `ShaftPdfComposer.kt` (`drawLinerDimensionsPdf`, `drawDimensionsLikePreview`, `drawDimWithExtensionsAvoidingOverlap`, `drawArrowInward`, `drawZigZagBreak`, `pickAftFwdTapers`, `fmtDia`, `fmtThread`, `fmtTaper` and associated constants). Removed blanket `@Suppress("unused")` annotation.
- Corrected `docs/PDF_EXPORT.md`: PDF does not use `ShaftRenderer`; `ShaftPdfComposer` has its own geometry drawing path. Dual-path divergence is now documented explicitly.
- Added `LinerDimAdapterTest` with 8 unit tests covering `mapToLinerDimsForPdf`: AUTO proximity anchoring, forced AFT/FWD modes, offset values, measurement-space rebasing with excluded threads.

## 2026-05-28

- Replaced PDF body center-break symbol with standard engineering S-curve edges. Each compressed body stub now ends with an S-shaped cut line instead of a straight cap; both edges curve in the same direction so the break reads as two matching cut faces across a narrow gap.

---

## 2026-05-27

- Fixed PDF OAL dimension lines landing at thread tip instead of taper SET when end threads are included in OAL. `computeSetPositionsInMeasureSpace` now derives SET positions from actual taper geometry instead of hardcoding 0/OAL.
- Updated `oalSpan` to take explicit SET endpoints `(x1Mm, x2Mm)` so the OAL label always matches the arrow positions.
- Added 4 unit tests covering SET position derivation (excluded, included, no-taper, overlapping cases).
- Added `AUDIT.md` — full codebase review covering architecture, dead code, test gaps, and documentation accuracy.
- Corrected `BRIEFING.md` field name errors: `startDiaMm`/`endDiaMm`, `odMm`, `excludeFromOAL`.

---

## [1.1.1] - 2026-01-08

### Added
- `.shaft` document filenames (content remains JSON), plus legacy `.json` compatibility and migration (`c98550f`).
- Connected-device instrumentation test guard (opt-in) to protect internal saves (multiple commits).
- Component snapping engine and helpers (multiple commits).
- Developer Options for debug tooling / gated verbose logging (multiple commits).
- Saved-shaft delete support plus tests (multiple commits).
- Thread “Include in OAL” toggle (exclude end threads from OAL window) (multiple commits).
- OAL window contract tests for determinism (multiple commits).
- Preview color presets + B/W mode (multiple commits).
- Shaft position selection persisted and printed in PDF footer (`a96a889`).
- Taper keyway (KW) width/depth fields + footer output (`15701e1`).
- Developer option to show OAL value in the preview box (`c0eb165`).

### Changed
- Save/open behavior and filename suggestions improved; overwrite confirmation added (`8743637`).
- PDF export UX improved (optional auto-open after export) (`56a293d`).
- PDF layout refined (shifted content for better spacing) (`c592a1c`).
- PDF footer and taper dimensioning refined (multiple commits).
- Editor toolbar/navigation redesigned (Home button, New/Open/Save, History dropdown, overflow menu) (multiple commits).
- Editor component carousel: tighter arrows/UX tweaks (multiple commits).
- Editor component titles made deterministic and more informative (`7a2e37e`):
    - Bodies: physical aft→fwd numbering.
    - Liners: positional AFT/MID/FWD naming; numbers only when needed; optional user override via inline title editing.
    - Tapers: AFT/FWD direction naming based on diameter trend; numbers only when needed.
- Preview overlay: removed OAL from the Free-to-End badge; Free-to-End only shows in Manual mode (`c0eb165`).
- App locked to portrait for more predictable editor layout (`700d8b2`).
- “Shaft Editor” header typography strengthened for clearer hierarchy (`7a2e37e`).
- Project/docs and dev tooling iterated (multiple commits).
- Android Gradle Plugin bumped (`070d916`).

### Fixed
- Gradle connected-test safety guard adjusted for Kotlin DSL compatibility (`c98550f`).
- Feedback email chooser behavior (`c80a7d5`).
- Stabilized component delete behavior (remove action timing, snackbar/undo flow) (multiple commits).
- Fixed PDF scaling/layout edge cases, taper dimension rendering, and unit-safe footer formatting (multiple commits).
- Settings and Developer Options screens are scrollable so all options are reachable (`27a8761`).

### Internal
- Version bump to `1.1.1` (`1027792`).
- Changelog refresh work (`4e502de`).

---

## [0.3.1] - 2025-09-16

### Added
- Full-rectangle preview rendering for components (multiple commits).
- Editor UI structure improvements (FAB + bottom sheet; more usable scaffolding) (`2f99695`).

### Changed
- Editor unit handling and dropdown behavior improved (`663157a`).
- Taper handling + input UX improvements (`48aaad6`).
- PDF title block / layout helpers refactor (`2d2f61c`).

### Fixed
- Updated `ShaftDrawingView` layout call to match `ShaftLayout` API (`2f424bd`).

## [0.2.0] - 2025-09-14

### Added
- Coverage chip hint (`coverageChipHint()`) in `ShaftViewModel` for concise, unit-aware messages.
- Settings menu in `TopAppBar` with toggle to choose between **chip-style** or **text-style** coverage hints.
- `SettingsDialog` component with temporary state management (persistence TODO).
- Export PDF action added to `TopAppBar` (optional), keeping Floating Action Button export as well.

### Internal
- Initial changelog created (`d1a4da5`).

### Changed
- Updated `ShaftScreen` scaffold to include Settings and Export actions in the `TopAppBar`.
- Improved overall UI structure and consistency.

---

## [0.1.0] - Initial Commit

### Added
- Project setup with package name `com.android.shaftschematic`.
- Core data models: `ShaftSpecMm`, `BodySegmentSpec`, `KeywaySpec`, `LinerSpec`, `TaperSpec`, `ThreadSpec`.
- `UnitSystem` enum for inches/mm conversion.
- `ShaftViewModel` with state flows for spec + unit handling.
- `ShaftScreen` UI with Compose, including input fields for:
    - Basics (length, diameter, chamfer, shoulder length).
    - Body segments (dynamic add/remove).
    - Keyways (dynamic add/remove).
    - Tapers with ratio handling.
    - Threads (forward + aft).
    - Liners (dynamic add/remove).
- Export-to-PDF feature using `ShaftPdfComposer` with:
    - Span/segment drawing.
    - Tapers, threads, keyways, liners.
    - Dimension arrows and overall length.
    - Simple title block with project info.
- Git integration with `.gitignore`, initial README, and project structure.

### Internal
- Initial project import (multiple commits).
