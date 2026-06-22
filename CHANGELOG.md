# Changelog

All notable changes to **ShaftSchematic** will be documented in this file.

The format is inspired by [Keep a Changelog](https://keepachangelog.com/) and follows [Semantic Versioning](https://semver.org/).

---

## 2026-06-22

### chore: auto-derive versionCode and versionName from git commit count

`versionCode` was a manually-maintained integer (`3`); `versionName` was a static `"1.2.0"`. Firebase App Distribution was labelling every upload as a duplicate because neither changed between builds.

Both are now derived from `git rev-list --count HEAD` at build time. The current count (244) becomes the patch digit:
- `versionCode = gitCount` (e.g., 244, 245, …)
- `versionName = "1.2.$gitCount"` (e.g., `1.2.244`, `1.2.245`, …)

Every commit automatically produces a uniquely-identified build with no manual editing. The major.minor (`1.2`) is still bumped manually when a breaking change or significant feature milestone warrants it.

**`app/build.gradle.kts`** — added `gitCount` exec block; replaced hardcoded `versionCode`/`versionName`.

---

### feat: Project Information moved to modal bottom sheet

Customer, Vessel, Job #, Shaft Position, and Notes were in a collapsible section inside the editor scroll area, consuming vertical space needed by the component carousel. They now live in a `ModalBottomSheet` opened from a new toolbar clipboard icon (Assignment). The scroll area is now entirely dedicated to components.

**`ui/screen/ShaftScreen.kt`** — removed `ExpandableSection("Project Information")` from scroll area; added `IconButton` (Assignment icon) to `TopAppBar`; added `ProjectInfoBottomSheet` composable with `rememberModalBottomSheetState(skipPartiallyExpanded = true)`.

---

### fix: FWD-reference taper length edit now keeps the FWD end anchored

Editing the length of a FWD-referenced taper was passing `startFromAftMm` through unchanged, so the FWD end drifted inward as the taper grew. The length commit handler now recomputes `physStart = OAL − authoredFromFwd − newLen` so the FWD face stays fixed and the AFT start slides.

**`ui/screen/ComponentCarousel.kt`** — `CommitNum("Length")` handler for tapers.

---

### fix: FWD-ref tapers and liners now stay anchored when OAL changes

`onSetOverallLengthMm()` and `ensureOverall()` were calling `.copy(overallLengthMm = …).syncExcludedThreadPositions()`. FWD-referenced tapers and liners have their start position stored physically from the AFT face, so a raw OAL change left them in place — drifting relative to the FWD face they were authored from.

New `ShaftSpec.withNewOal(newOal: Float)` recomputes `startFromAftMm` for every FWD-referenced taper and liner (`newStart = newOal − authoredFromFwd − length`) before calling `syncExcludedThreadPositions()`. Both OAL mutation paths now use it.

**`model/ShaftSpecExtensions.kt`** — added `withNewOal()`.  
**`ui/viewmodel/ShaftViewModel.kt`** — `onSetOverallLengthMm()` and `ensureOverall()` use `withNewOal()`.

---

### fix: excluded end thread shift no longer pushes shaft FWD edge past right margin

`ptPerMm` was computed from `overallLengthMm` alone, then the excluded-AFT-thread origin shift (`left += threadLen × ptPerMm`) consumed page width that wasn't accounted for in the scale. For the Siberian Sea shaft (OAL 147⅞", 4.5" prop thread), the FWD taper was drawing ~16pt past the right margin.

The fix computes the full content span — `contentMinMm` (excluded AFT thread tails, negative) through `contentMaxMm` (excluded FWD thread tails, past OAL) — before deriving `ptPerMm`. The shaft body's portion of that span is passed as `effectiveGeomWidthPt` to `computeDetailPtPerMm`, ensuring all content lands within `geomRect` regardless of thread count or length.

**`pdf/ShaftPdfComposer.kt`** — `contentMinMm`, `contentMaxMm`, `contentSpanMm`, `effectiveGeomWidthPt` computed before `ptPerMm`; bodies-only branch also updated.

---

### test: WithNewOalTest, PdfLayoutBoundsTest, BodySplitMergeTest

**`test/model/WithNewOalTest.kt`** — 17 tests: AFT components unchanged, FWD reanchors on grow/shrink, FWD flush with shaft face, mixed AFT+FWD spec, liner reanchoring and `endMmPhysical` consistency, excluded thread sync, OAL clamp, idempotency, old-copy regression.

**`test/pdf/PdfLayoutBoundsTest.kt`** — 9 tests: no-thread baseline, excluded AFT overflow regression (Siberian Sea), excluded FWD overflow, both ends, short shaft, very short/wide shaft (diameter-bound), variable AFT thread lengths.

**`test/model/BodySplitMergeTest.kt`** — 19 tests: mid-split, AFT/FWD edge inserts, full-consume, non-overlap, touching endpoints, multiple bodies, merge flanking/single-side/float-drift-tolerance, round-trips at center/AFT end/FWD end.

---

## 2026-06-19

### fix: updating a component no longer repositions other components

`updateBody`, `updateTaper`, `updateLiner`, and `updateThread` were calling `snapForwardFrom()` whenever the updated component's start or length changed, silently cascading position changes to every downstream component in the chain. This violated the fundamental invariant that component inputs are user-authored and must not be mutated by anything other than an explicit user action on that component.

The auto-snap block, `_autoSnap` state, and `setAutoSnap()` have been removed from all update paths. `snapChainFrom()` / `snapChainFromId()` remain as explicitly-invoked operations.

**`ui/viewmodel/ShaftViewModel.kt`** — removed `snapForwardFrom` cascade and `_autoSnap` flag from all four `updateX()` functions.  
**`test/ui/viewmodel/ShaftViewModelUpdateTest.kt`** — 9 new tests covering liner, body, taper, thread, and mixed-spec update isolation.

---

### fix: PDF dimension unit suffix changed from " in" to `"`

All inch-unit dimension labels now use the standard `"` suffix instead of ` in` (e.g., `4.997"` not `4.997 in`). Applies to diameters, lengths, and OAL labels across the shaft, runout, and wear PDF composers.

**`pdf/UnitFormat.kt`** — `formatDim()`, `formatLenDim()`, `formatLenWithUnit()`, `formatDiaWithUnit()`.  
**`pdf/RunoutPdfComposer.kt`**, **`pdf/WearPdfComposer.kt`** — OAL display line.

---

### fix: common inch fractions render as Unicode symbols in PDF dimensions

`LengthFormat.formatInchesSmart()` now substitutes common fractions with Unicode characters (½ ¼ ¾ ⅛ ⅜ ⅝ ⅞) so dimension text reads like hand-drawn notation rather than `3/4` or `7/8`.

**`util/LengthFormat.kt`** — `unicodeFractions` map applied in `formatInchesSmart()`.

---

### feat: taper AFT/FWD reference toggle in carousel edit card

The taper carousel card now shows a "Measure From: AFT / FWD" chip row (matching the liner card). Selecting FWD lets the user enter the start distance from the FWD end; the model always stores the canonical `startFromAftMm`. `Taper.authoredReference` (new field, default AFT) persists the user's choice so the field label and value are correct on re-open.

**`model/Taper.kt`** — added `authoredReference: LinerAuthoredReference` field.  
**`ui/screen/ComponentCarousel.kt`** — AFT/FWD toggle + start field adapts label and converts value.  
**`ui/screen/ShaftRoute.kt`** — wires `onUpdateTaperReference` callback.  
**`ui/viewmodel/ShaftViewModel.kt`** — `updateTaperAuthoredReference()`.  
**`docs/Model_Conventions.md`** — updated.

---

### fix: auto-snap removed from all component delete paths

The snap-forward-on-delete behavior (shifting subsequent components left after a deletion) has been removed from `removeBody()`, `removeTaper()`, `removeThread()`, and `removeLiner()`. Body split/merge (added earlier) makes positional snap on delete incorrect — merged bodies already fill the freed span.

**`ui/viewmodel/ShaftViewModel.kt`** — removed `snapFromKey` / `snapFromOrigin` logic from all four remove functions.

---

### fix: PDF footer columns positioned at even thirds, all left-aligned

The three footer columns (AFT, project info, FWD) were computed with an asymmetric gutter formula that bunched the center and right blocks toward the left half of the page. Replaced with clean thirds: `colW = rect.width() / 3`, anchor each column at `rect.left + n × colW`. All columns remain left-aligned.

**`pdf/ShaftPdfComposer.kt`** — simplified column layout in `drawFooter()`.

---

### fix: PDF footer shows authored taper rate text instead of computed 1:N ratio

The `Rate:` line in the AFT/FWD taper footer blocks was always re-derived via `rate1toN()` (e.g. `1:16`), ignoring the `taperRateText` field the user typed (e.g. `3/4"/FT`). The authored string is now used when non-empty; `rate1toN()` is the fallback.

**`pdf/ShaftPdfComposer.kt`** — `buildFooterEndColumns()` uses `tp.taperRateText.trim().ifEmpty { rate1toN(tp) }` for both AFT and FWD taper rate lines.

---

### fix: consolidate conflicting EPS constants in PDF composer

`ShaftPdfComposer.kt` had two proximity tolerances with overlapping scope: `END_EPS_MM = 0.5` (imported from `geom/OalComputations.kt`) and a private `EPS_MM = 0.01`. The 50× discrepancy meant that `detectEndFeatures()` and `getAftEndThread()` / `getFwdEndThread()` / `getAftEndTaper()` / `getFwdEndTaper()` used different thresholds for what counts as "at the shaft end", potentially causing mismatches between which features show up in the footer. Removed `EPS_MM`; all proximity checks now use `END_EPS_MM`.

**`pdf/ShaftPdfComposer.kt`** — removed `private const val EPS_MM`; replaced four usages with `END_EPS_MM`.

---

### fix: Project Information section expanded by default

Customer, Vessel, and Job # were hidden behind a collapsed section on every new drawing, adding friction at job-start. The section now opens expanded.

**`ui/screen/ShaftScreen.kt`** — `ExpandableSection("Project Information", initiallyExpanded = true)`.

---

### feat: body auto-split on add, auto-merge on delete

Adding any taper, liner, or thread now splits any overlapping body into two independent fragments (each keeping the parent's `diaMm` and a new UUID). Deleting a taper/liner/thread merges the flanking body fragments back into one body (merged diameter = max of the two). Single-side boundary case: the lone adjacent body expands to fill the freed span rather than merging.

**`model/ShaftSpecExtensions.kt`** — new `splitBodyAt()` and `mergeAdjacentBodies()` functions.  
**`ui/viewmodel/ShaftViewModel.kt`** — all `add*At()` / `delete*()` paths call split/merge; included in the undo snapshot.

---

### feat: full keyway inputs in Add Taper dialog

`AddTaperDialog` gains KW Width, KW Depth, KW Length, KW Offset, and Spooned toggle fields, mirroring the carousel edit card. Previously these were only editable after adding.

**`ui/screen/AddComponentDialogs.kt`**

---

### fix: add dialogs always open; bodies and excluded threads excluded from default-start

The FAB chooser previously quick-added bodies, liners, and tapers without showing a dialog. All paths now open the full dialog. `computeAddDefaults()` no longer counts bodies or excluded threads when finding the next open slot — they were pushing new component start positions past the shaft end. Body–taper pairs removed from `collidingIds()` (bodies are fillers; taper overlap is intentional). All `add*At()` methods now auto-select the newly created component.

**`ui/screen/ShaftScreen.kt`**, **`ui/viewmodel/ShaftViewModel.kt`**, **`model/ShaftSpecExtensions.kt`**  
**`docs/DATA_MODEL.md`**, **`docs/UI_CONTRACT.md`**, **`docs/VALIDATION_RULES.md`** updated.

---

### fix: direction chip selected state uses border, not fill

`DirectionChip` (AFT/FWD toggle in Add Taper and Add Liner dialogs) replaced `FilterChip` with a custom `OutlinedButton`: selected state shows a 2dp primary-color border + tinted container; unselected has no border. Previously the outlined border on the unselected chip made it visually appear to be the active choice.

**`ui/screen/AddComponentDialogs.kt`**

---

### fix: PDF dimension arrows default inward; arrow size reduced

Arrow tips were flipping outward by an overly strict threshold. `canFitInwardArrows` loosened from `spacing × 1.5` to `spacing × 1.0` so arrows now default inward (engineering convention) and flip outward only when truly cramped. Arrow size reduced from 7 → 5 pt to match hand-sketch reference drawings.

**`pdf/render/PdfDimensionRenderer.kt`**

---

### fix: PDF export no longer rejects excluded threads as out-of-bounds

`blockingExportError()` was triggering "start must be ≥ 0" on excluded threads, which deliberately have `startFromAftMm = −lengthMm`. Excluded threads are now skipped in that check.

**`ui/nav/PdfExportRoute.kt`**

---

### fix: `CommitNumField` commits on every keystroke; external resets don't jump cursor

Values were lost when tapping "Add" while a text field was still focused (the on-blur commit hadn't fired yet). `CommitNumField` now commits on every keystroke. `LaunchedEffect(initial)` handles external value resets without moving the cursor mid-type.

**`ui/screen/AddComponentDialogs.kt`**

---

### fix: excluded thread flashes at shaft face during carousel swipe

In manual OAL mode, `updateThread()` wrote `effectiveStart = 0f` for AFT excluded threads as a temporary value, expecting `ensureOverall()` → `syncExcludedThreadPositions()` to correct it. `ensureOverall()` exits early in manual mode without calling sync, so the `0f` position persisted — placing the thread at the shaft AFT face and causing it to visually overlap the adjacent taper for a single frame. The trigger: `NumericInputField.onFocusChanged` fires a commit when the carousel's `HorizontalPager` clears focus from the excluded-thread card while the user swipes to the adjacent taper card.

Fix: `updateThread()` now derives the correct position (`−lengthMm` for AFT, `overallLengthMm` for FWD) directly inside the `_spec.update {}` call, using the same formula as `syncExcludedThreadPositions()`. The position is always correct regardless of OAL mode, with no transient wrong state.

**`ui/viewmodel/ShaftViewModel.kt`** — `updateThread()` `effectiveStart` for excluded threads.

---

### fix: PDF footer FWD column nudged to 76% of content width

The FWD footer column was at 72% of the content area width; adjusted to 76% for a more balanced three-column layout with the AFT block anchored at the left margin and center block at 40%.

**`pdf/ShaftPdfComposer.kt`** — `rightX = rect.left + rect.width() * 0.76f` in `drawFooter()`.

---

## 2026-06-18

### feat: taper/liner direction toggle; excluded thread rendering; add-time collision warnings

**Direction toggles in add dialogs**
- `AddTaperDialog` — AFT/FWD chip controls which end is the SET. SET/LET labels on the diameter fields swap accordingly; model stores diameters in AFT→FWD order regardless.
- `AddLinerDialog` — "Measure From" AFT/FWD chip writes `LinerAuthoredReference` through `ShaftRoute` → `ShaftViewModel.addLinerAt()` so the carousel card reflects the chosen reference on first render.

**Excluded thread rendering**
- `syncExcludedThreadPositions`: AFT excluded threads placed at `startFromAftMm = −lengthMm`, FWD at `OAL`, sitting flush with the shaft face without overlapping tapers.
- `ShaftLayout.compute`: `minXMm` / `maxXMm` now expand to include excluded threads outside `0..OAL` so they render in both the preview and PDF without clipping.

**Add-time collision warnings**
- New `collectAddWarnings()`: pre-submit overlap check in Taper, Liner, and Thread add dialogs. Warns on cross-type overlaps and shaft bounds when OAL is manual. Bodies and excluded threads are skipped. Warning confirmation dialog offers "Add Anyway / Cancel" — nothing is silently blocked.

**Carousel auto-scroll fix**
- `ComponentCarousel`: size-based auto-scroll `LaunchedEffect` is now conditional on no existing selection, preventing it from overriding the user's swipe after adding a component.

**Tests** — `CollisionWarningsTest` (13 cases), `ShaftSpecTest` +`syncExcludedThreadPositions` (4 cases), `ShaftLayoutTest` +excluded-thread coordinate expansion (4 cases). All passing.

**`model/ShaftSpec.kt`**, **`ui/drawing/render/ShaftLayout.kt`**, **`ui/screen/AddComponentDialogs.kt`**, **`ui/screen/ComponentCarousel.kt`**, **`ui/screen/ShaftRoute.kt`**, **`ui/screen/ShaftScreen.kt`**, **`ui/util/CollisionWarnings.kt`** (new), **`ui/viewmodel/ShaftViewModel.kt`**  
**Docs**: `ShaftLayout v0.4`, `Model_Conventions v0.2`, `ShaftViewModel v0.2`, `ShaftScreen v0.7`, `VALIDATION_APPENDIX`.

---

### ci: Firebase App Distribution workflow

Added GitHub Actions workflow for distributing debug APKs to testers via Firebase App Distribution on every push to `main`. Uses service-account auth via the Firebase CLI.

---

## 2026-06-11

### fix: OAL bracket moves with include/exclude; label is always the typed value

The `excludeFromOAL` toggle on end threads now correctly controls **bracket position only** — the OAL label is always `spec.overallLengthMm`, the value the user typed.

- **Excluded**: bracket spans AFT SET → FWD SET. Thread is drawn outside the bracket.
- **Included**: bracket spans shaft AFT end → FWD SET, grouping the thread inside the arrow.
- Label never changes in either case. Component measurements always reference SET.

Domain rationale: threads don't need to be a specific length on a new shaft; liners and tapers do. The toggle exists for customers (e.g. Coast Guard) who specify exact total lengths so shafts are interchangeable spares. Nothing is ever dimensioned from a thread end.

**`pdf/dim/LinerSpanBuilder.kt`** — `oalSpan()` gains an explicit `labelMm` param (default = bracket width) so the label can be decoupled from the bracket span.  
**`pdf/ShaftPdfComposer.kt`** — bracket endpoints driven by include/exclude; `labelMm = spec.overallLengthMm` always.  
**`geom/OalComputations.kt`** — `computeOalWindow` always returns `(0.0, overallLengthMm)`; `computeExcludedThreadLengths` retained for future SET-to-SET annotation work.

---

## 2026-06-11

### feat: runout screen v2 — inline preview + layout overhaul

- **`RunoutRoute.kt`** — complete rewrite: `RunoutComponentEntry` data class, inline shaft preview via `ShaftRenderer`/`ShaftLayout`, scrollable column layout, sidebar nav integration, `resolvedComponents` support.
- **`ComponentCarousel.kt`** — removed bubble-count stepper controls (95 lines). Bubble counts are managed through the runout config; per-component stepping in the carousel was redundant.
- **`ShaftRoute.kt` / `ShaftScreen.kt`** — removed `runoutConfig` and `onSetRunoutBubbleCount` threading that was coupling the main screen to runout state. `ComponentCarousel` retains defaulted params for backward compatibility.

---

### feat: line thickness control

- **`SettingsRoute.kt`** — `LineThicknessControl` composable: slider (50%–200%) + typeable `%` field with on-blur clamping. 100% = new default thin weight; 200% = original thick weight.
- **`SettingsStore.kt`** — `KEY_LINE_THICKNESS_SCALE` DataStore key; `lineThicknessScaleFlow()` / `setLineThicknessScale()`.
- **`ShaftViewModel.kt`** — `lineThicknessScale: StateFlow<Float>`, collected from DataStore on init, exposed for UI and PDF export.
- **`ShaftPdfComposer.kt`** — `OUTLINE_PT_BASE = 1.25 pt`, `DIM_PT_BASE = 0.8 pt` (100% defaults). `composeShaftPdf()` gains `lineThicknessScale` param; scale applied to both paint objects.
- **`ShaftDrawing.kt`** — `outlineWidthPx = 2f * lineThicknessScale.coerceIn(0.5f, 2.0f)`.
- **`PdfExportRoute.kt` / `PdfPreviewScreen.kt`** — pass `lineThicknessScale` through to the composer.

---

### fix: OAL dimension respects include-thread toggle

The PDF OAL dimension arrow previously always measured **SET to SET** regardless of whether end threads were marked as included in OAL. Root cause: when `excludeFromOAL = false` the coordinate origin shifts by `threadLength`, so both SET endpoints moved by the same delta and the rendered distance was unchanged.

Fix in `ShaftPdfComposer.kt`: detects any end thread with `!excludeFromOAL` anchored to position 0 (AFT) or `overallLengthMm` (FWD), and substitutes the physical shaft end coordinate (`0.0` or `win.oalMm`) for the SET coordinate in the `oalSpan()` call. Component dimension rails continue to reference SET positions.

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

## 2026-05-30 (8) — fix: sidebar UX, toolbar hamburger, runout PDF layout

### Sidebar
- **Hamburger button** replaces the Home icon in the top toolbar. Tapping it opens the sidebar overlay — no persistent rail taking up horizontal space.
- **Home button removed from toolbar** — it now lives only inside the sidebar (not duplicated).
- **Thin handle tab removed** — the sidebar is opened exclusively via the toolbar button.
- `navigationBarsPadding()` added inside the sidebar panel so Settings is never hidden under the system navigation bar.
- `statusBarsPadding()` was already present, keeping the title clear of the status bar.

### Runout PDF — complete layout rewrite
- **Bubble collision eliminated**: Bubbles are no longer placed directly below their station's axial position. Instead a fan-spread algorithm distributes bubble X positions evenly across the page width, guaranteeing no circles overlap.
- **Monotonic assignment**: Even-indexed stations → row 0 (shorter leaders), odd-indexed → row 1 (longer leaders). Because the mapping is monotonic (station order = bubble order), leader lines cannot cross each other — they fan out cleanly, exactly matching the hand-drawn reference.
- **Leaders touch the shaft**: Each leader now starts from the shaft's ACTUAL outer surface at the station's axial position (interpolated through tapers), not from a fixed maximum-diameter y.
- **Shaft centred vertically**: The shaft profile is now sized from its real maximum outer diameter and centred in the upper portion of the page, with the bubble area and TIR line filling the lower portion.

### TIR direction label (RunoutRoute)
- Label text corrected to "Looking AFT" / "Looking FORWARD" with an explanation that this determines clock-position reference (3 o'clock looking aft ≠ 3 o'clock looking forward).

---

## 2026-05-30 (7) — feat: runout drawing + wear document + sidebar nav

### Navigation
- New collapsible **sidebar icon rail** in `ShaftEditorRoute` (always visible, 52 dp collapsed).
  Three tabs: **Schematic** (always enabled), **Runout Sheet** and **Wear Document** (enabled
  once the shaft has ≥1 component and a non-zero OAL). Tab state survives configuration changes.
  Files: `EditorTab.kt`, `EditorSidebar.kt`, `ShaftEditorRoute.kt`.

### Data model
- `RunoutConfig` — new serializable data class persisted in every `.shaft` file:
  - `componentOverrides: Map<String, Int>` — per-component bubble count overrides.
  - `tirDirection: TirDirection` — AFT / FORWARD / UNSET; printed on the runout sheet.
- `TirDirection` enum in `settings/RunoutConfig.kt`.
- `ShaftDocCodec.ShaftDocV1` gains `runout_config` field (default = empty → backward-compat).
- `ShaftViewModel` gains `_runoutConfig` StateFlow, `setRunoutBubbleCount()`, `setTirDirection()`.
  Config is saved in `exportJson()`, restored in `importJson()`, reset in `newDocument()`.

### Runout PDF (`pdf/RunoutPdfComposer.kt`)
Page: landscape US Letter. Regions top→bottom:
1. **Header strip** — Customer, Vessel, Job#, Date, STBD/PORT, OAL in a single compact line.
2. **OAL span line** — Single arrow-to-arrow dimension, SET to SET only.
3. **Shaft profile** — Bodies (with compression breaks), tapers (with keyway indicators),
   liners. No dimension tiers, no component labels.
4. **Bubble area** — Each component's stations drawn as circles with diagonal leader lines.
   - Tapers: N stations (default 2) inset `RUNOUT_EDGE_INSET_MM` (25.4 mm / 1 inch) from
     each edge — readings on the edge face are unreliable.
   - Liners: same inset convention.
   - Bodies: N stations (default 3) evenly distributed, no inset.
   - Within each component, stations alternate row 0 (short leader) and row 1 (long leader)
     to avoid horizontal overlap between adjacent circles.
   - Small filled square at the top of each circle = keyway-at-top reference marker.
5. **TIR line** — "TIR's taken looking: ___" with optional direction label.

### Wear document PDF (`pdf/WearPdfComposer.kt`)
Same shaft profile + compact header, no bubbles. Dye-pen PASS/FAIL checkboxes + notes fill-in
line at the bottom. For hand-annotating damage, pitting, and inspection results in the field.

### Carousel changes (`ComponentCarousel.kt`)
- `RunoutStationControl` composable added to Body, Taper, and Liner cards. Shows
  "Runout stations: N [−] [+]" using the effective count (override or default).
- `ComponentCarouselPager` and `ComponentPagerCard` gain `runoutConfig` and
  `onSetRunoutBubbleCount` params (both defaulted — backward-compat).

### Screen routing
- `RunoutRoute.kt` — TIR direction selector + Export button; writes runout PDF via SAF.
- `WearRoute.kt` — Export button; writes wear document PDF via SAF.
- `ShaftRoute.kt` — wires `runoutConfig` and `onSetRunoutBubbleCount` from ViewModel.

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
