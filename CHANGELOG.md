# Changelog

All notable changes to **ShaftSchematic** will be documented in this file.

The format is inspired by [Keep a Changelog](https://keepachangelog.com/) and follows [Semantic Versioning](https://semver.org/).

---

## 2026-05-30 (6)

### feat: yellow warning badges — non-blocking validation now visible in UI

- **`ComponentWarnings.kt`** — new utility with per-component warning functions:
  - Any component with `0 < lengthMm < 1 mm` → "Very short segment (< 1 mm)"
  - Thread with `pitchMm == 0` → "Zero pitch — thread renders flat"
- **`ComponentCard`** gains a `warningMessage: String?` slot rendered as a yellow
  `tertiaryContainer` chip below the title, distinct from the red error chip.
  Body, Taper, Thread, and Liner cards all pass their computed warning.
- **`FreeToEndBadge`** now has three states: normal → yellow (0–10 mm clearance) → red (negative/oversized). Previously only normal and red.
- Stale `TODO.md` entries for keyway drawing marked complete.

---

## 2026-05-30 (5)

### fix: selection highlight — single thin ring instead of double box

- Removed the inner white "edge" ring from the two-ring highlight system.
  Only the outer cyan glow ring is drawn now, giving a single clean selection
  box that doesn't compete visually with component lines (keyways, threads, etc.).
- Reduced `highlightGlowExtraPx` from 8 → 2 px so the ring is noticeably
  thinner while still clearly marking the selected component.

---

## 2026-05-30 (4)

### fix: shared app signing + corrected keyway schematic convention

#### Signing
- Committed `debug.keystore` to the project root so every machine that clones
  the repo signs with the same key. Android now treats sideloaded builds from
  any machine as app updates rather than new installs — no more uninstall/data-wipe
  when switching computers.
- Added `signingConfigs.shared` in `build.gradle.kts` (debug + release both use it).
- `.gitignore` updated with `!debug.keystore` exception; release keystores remain blocked.

#### Keyway rendering — full rewrite to match shop schematic convention
- **Previous behaviour:** drew a notch cutting down from the top surface of the
  taper, showing depth. Wrong axis and wrong convention.
- **Correct convention** (confirmed from shop hand-drawings in `assets/`): the keyway
  is shown as a **plan-view rectangle centred on the shaft centreline** — height
  represents keyway **width** (W) to scale, horizontal span represents keyway
  **length** (L) to scale. Depth is never drawn; it appears only in the PDF footer text.
- The closed (LET) end uses a **concave semicircle** matching the mill-cutter profile.
  For floating keyways both ends are semicircular; for open keyways the SET face is
  already closed by the taper's end-face line.
- Interior filled **white** so the keyway reads as a void against any taper fill colour
  (steel grey, bronze, etc.). Fill is inset one line-width from the SET face so the
  taper's end-face line retains its full visual weight.
- Fix applied identically to `ShaftRenderer` (preview) and `ShaftPdfComposer` (PDF).

---

## 2026-05-30 — Carousel extraction refactor

Extracted the component carousel out of `ShaftScreen.kt` into `ui/screen/ComponentCarousel.kt`.

**Moved to `ComponentCarousel.kt` (~740 lines):**
- `ComponentCarouselPager` — pager, selection seeding, swipe detection, LaunchedEffects
- `EdgeNavButton` — left/right arrow buttons
- `ComponentPagerCard` — per-component editor content (Body, Taper, Thread, Liner)
- `ComponentCard` — shared card chrome (title, error/warning chips, delete button)
- Carousel-private helpers: `CommitNum`, `dispKw`, `fmtTrim`, `pitchMmToTpi`, `CAROUSEL_HEIGHT`

**Stayed in `ShaftScreen.kt` (1434 lines, down from 2322):**
- All screen-level composables (header, preview, OAL badge, dialogs, FAB)
- Shared display helpers promoted from `private` to `internal`: `abbr`, `disp`, `formatDisplay`, `toMmOrNull`, `parseFractionOrDecimal`, `tpiToPitchMm`

No behaviour changes. All unit tests pass.

---

## 2026-05-30 — Doc refresh

Updated TODO.md, BRIEFING.md, and ROADMAP.md to reflect current state:
- TODO restructured around v0.5.x sprint (ShaftScreen refactor as §1). All completed v0.4.x work collapsed. Stale entries removed. Body keyway formally shelved.
- BRIEFING.md: status table updated with validation, keyways, and signing; architecture invariant corrected (dual rendering paths); component model table updated with keyway fields; active sprint section rewritten.
- ROADMAP.md: v0.4.x marked complete; v0.5.x deliverables documented; v1.0 definition of done updated.

---

## Versioning Notes

- Early development used git tags (`v0.2.0`, `v0.3.1`) for milestones.
- Starting with `1.1.1`, the changelog and the app `versionName` are kept in sync; future releases follow this convention.
- Note: `v0.2.0` and `v0.3.0` point to the same commit (`d1a4da5`).

## 2026-05-30 (3)

### feat: keyway drawing on taper — open and floating keyway styles

#### Model
- `Taper` gains `keywayOffsetFromSetMm: Float = 0f` (backward-compatible; default 0 = open keyway at SET face).
- `hasKeyway` extension property: true when width, depth, and length are all non-zero.
- `isValid` now enforces `offset >= 0` and `offset + length <= taperLength`.

#### Two keyway styles
- **Open keyway** (`offset = 0`, 95% case): slot starts at the SET face, open-ended there, wall only at the LET side. The Spoon toggle applies here.
- **Floating keyway** (`offset > 0`, 5% case): slot is inset from the SET face, walls on both sides. Spoon toggle is disabled and grayed in the UI.

#### Rendering
- `ShaftRenderer` draws the keyway notch on the taper's top surface in the preview: fills the notch area with the taper fill color (erasing the top outline inside the slot), redraws the top line in the two segments outside the slot, then draws walls and floor in the outline color.
- `ShaftPdfComposer` draws the same notch on the PDF canvas using a white fill to erase the top line inside the slot, with the same wall/floor logic.
- The notch floor follows the taper slope (drawn as a diagonal line matching the top surface angle).

#### UI
- Carousel taper card gains **"KW Offset from SET"** field between Length and the Spoon toggle.
- Spoon toggle is automatically disabled when offset > 0 (floating keyway has no open face to spoon).

#### Tests
- `TaperKeywayTest`: 11 cases covering `hasKeyway`, offset validation, boundary conditions, and backward-compat default.

---

## 2026-05-30 (2)

### feat: validation UI hookup — blocking errors surface in dialogs, cards, and export

#### Add dialogs (Liner + Thread)
- `CommitNumField` inside Add dialogs now accepts an `errorText` parameter and shows it in red below the Start field using `OutlinedTextField`'s `isError`/`supportingText`.
- `AddLinerDialog` and `AddThreadDialog` compute `startOverlapErrorMm` live as fields change. The **Add button is disabled** when a blocking start error exists (overlap, negative start, thread-between-components). The error message appears immediately on the start field so the user knows why.

#### Carousel component cards
- `ComponentCard` gains an `errorMessage: String?` parameter. When non-null, a Material 3 error-container chip is rendered below the card title.
- Thread and Liner cards pass their current `startOverlapErrorMm` result to this slot, so cards with placement errors show a visible red badge at all times.

#### PDF export gate
- `PdfExportRoute` now calls `blockingExportError(spec)` before launching the SAF file picker. If any thread or liner has a blocking validation error the picker is never opened; instead an `AlertDialog` displays the error message and returns the user to the editor on dismiss.

---

## 2026-05-30

### fix: selection box not shown on initial swipe after opening a file

- `ComponentCarouselPager` now seeds `selectedComponentId` immediately when components first load (via the existing `LaunchedEffect(rowsSorted.size)` that auto-scrolls to the last card), so the highlight glow appears as soon as the carousel is visible.
- Fixed swipe detection guard: the `pagerScrollStartedByUser` flag was only set when `selectedIndex == pagerState.currentPage`, but with no selection `selectedIndex` was `-1`, so all swipes were silently ignored. The guard now also triggers when `selectedComponentId` is `null`.

---

## 2026-05-29

### feat: tap-to-add pipeline, thread validation, taper-rate restoration, pdfPrefs persistence

#### Tap-to-add pipeline (TODO §1.2 + §1.3)

- `ShaftDrawing` now accepts an `onTapAtMm` lambda. Taps that land on an existing component still fire `onTapComponentId`; taps on empty space fire `onTapAtMm` with the raw mm coordinate.
- `ShaftViewModel` gains `pendingAddPositionMm: StateFlow<Float?>`, `setTapAddPosition(rawMm)` (snaps via `snapRawPositionMm` before storing), `clearPendingAddPosition()`, and `gapToNextAnchorMm(positionMm, min=50f)` (distance to next snap anchor, minimum 50 mm).
- `ShaftRoute` wires the three new callbacks to `ShaftScreen`; `pendingAddPositionMm` and the computed gap length are passed down as parameters.
- When `pendingAddPositionMm` is non-null `ShaftScreen` shows `InlineAddChooserDialog`. Selecting Body, Liner, or Taper opens the corresponding add dialog with the tapped position pre-filled in the Start field and the gap length pre-filled in the Length field. Thread routes through the existing `AddThreadDialog` with the tapped start.
- `AddBodyDialog`, `AddLinerDialog`, and `AddTaperDialog` each gain optional `initialStartMm` and `initialLengthMm` overrides that take precedence over the spec-derived defaults when provided.

#### Thread start/placement fixes (TODO §2.x)

- **End-snap bug fixed:** `applySnappedThreadUpdate` previously snapped both the start and end positions independently. This could silently extend a thread's length when the derived end position happened to land within snap tolerance of a body boundary (e.g. a 99 mm thread moved to start=0 would snap its end to the 100 mm body anchor, becoming 100 mm). The function now snaps only the start and preserves the original length.
- **"Threads at ends only" validation rule implemented:** `startOverlapErrorMm` returns `"Thread must be at a shaft end, not between components"` when a thread has a Body or Liner ending at-or-before its start *and* another Body or Liner starting at-or-after its end (i.e. surrounded on both sides). Adjacency is handled with a 1 mm epsilon so end-to-start touching qualifies.

#### Taper-rate restoration (TODO §3.2)

- `Taper` model gains a `taperRateText: String = ""` field (kotlinx.serialization `@Serializable`; backward-compatible default `""`).
- `ShaftViewModel` companion exposes `parseRateText(text)` — parses `1:12`, `3/4`, decimals, and bare integers (bare int N interpreted as 1:N) — and `deriveTaperDiameters(setMm, letMm, lengthMm, rateText)`: if both SET and LET are > 0 the rate is ignored; if only one diameter is provided the missing one is derived from the rate and length; zero length or unparseable rate returns diameters unchanged.
- `addTaperAt` and `updateTaper` accept an optional `rateText: String = ""` and call `deriveTaperDiameters` before storing. `updateTaper` also falls back to the taper's stored `taperRateText` when the caller passes a blank rate.
- The taper carousel card has a new `Rate (1:12, 3/4, or decimal)` commit field; all `onUpdateTaper` call sites pass the stored `taperRateText` through.
- `onAddTaper` / `onUpdateTaper` callbacks throughout the stack (`ShaftScreen`, `ShaftRoute`) updated from `(Float, Float, Float, Float)` to `(Float, Float, Float, Float, String)`.
- `TaperRateTest.kt` — 9 new unit tests covering `parseRateText` (colon, slash, decimal, bare int, blank, invalid) and `deriveTaperDiameters` (both provided, derive LET, derive SET, blank rate, zero length, clamp-to-zero).

#### pdfPrefs persistence (SettingsStore TODO)

- Added `KEY_PDF_OAL_SPACING_FACTOR = floatPreferencesKey("pdf_oal_spacing_factor")` to `SettingsStore`.
- `pdfOalSpacingFactorFlow(ctx)` reads the stored value (defaults to `PdfPrefs().oalSpacingFactor = 2.5f`).
- `suspend fun setPdfOalSpacingFactor(ctx, factor)` writes the clamped value to DataStore.
- `ShaftViewModel.init` now collects `pdfOalSpacingFactorFlow` and keeps `SettingsStore._pdfPrefs` in sync, matching the existing pattern for `tieringMode` and `showComponentTitles`.
- `ShaftViewModel.setPdfOalSpacingFactor(factor, persist)` added for future UI callers.
- Removed the `TODO: persist _pdfPrefs via your existing persistence layer` comment from `SettingsStore.updatePdfPrefs`; all three `PdfPrefs` fields are now fully persisted.

#### VS Code test integration

- `.vscode/tasks.json` — "Test (JVM unit tests)" (default test task), "Compile (debug Kotlin)" (default build task with Kotlin error problem matcher), "Test (single file)" (prompts for filter pattern).
- `.vscode/settings.json` — configures `java.import.gradle.*`, `java.project.sourcePaths`, `java.project.referencedLibraries`, and `java.test.config` for the Extension Pack for Java test runner.

---

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
