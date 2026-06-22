# ShaftSchematic App — Engineering Analysis
*Date: 2026-06-19 | Branch: feat/runout-screen-v2 | Version: 1.1.1*

---

## Overview

End-to-end review of the ShaftSchematic Android app from four angles: machinist/domain correctness, mechanical engineering, Android development quality, and UI/UX. The reference drawings in the session photos (Aleutian Spray Fish / Siberian Sea shaft, and the two-taper multi-liner shaft) were used as ground truth for what a real shop drawing needs.

No fixes are applied here — this is analysis and recommendations only.

---

## 1. Machinist / Domain Correctness

### 1.1 Taper Rate Format in the Footer — Wrong for the Shop Floor

**Priority: High**

The PDF footer displays taper rate as a computed `1:N` ratio (e.g., `Rate: 1:16`). Real shop drawings use `3/4"/FT` or `1"/FT` — diameter change per foot of length, not a dimensionless ratio. The app correctly stores `taperRateText` (the user's own string, e.g., `"3/4 in/ft"` or `"1:12"`) but the footer ignores it entirely and re-derives via `rate1toN()`, which computes `lengthMm / (LET - SET)` and formats as `1:N`.

A machinist reading the PDF will see `1:16` where they expect `3/4 in/ft`. The authored `taperRateText` should be used in the footer when it exists. The computed `1:N` is acceptable as a fallback or secondary label.

Related: `ShaftPdfComposer.kt:1008` (`rate1toN()`)

### 1.2 Body Outer Diameter Not Shown on the Drawing

**Priority: High**

On both reference drawings the shaft body OD is prominently labeled — `Ø6.500"` in the second photo, written directly on the body section. The PDF currently does not render any diameter callout on the body. The `DiameterCallout` / `DiameterLeaderRenderer` code exists but is explicitly stubbed out:

```kotlin
val calls: List<DiaCallout> = emptyList()  // ShaftPdfComposer.kt line 292
```

Without OD callouts, the exported PDF is incomplete as a shop document. A machinist needs to know the body diameter from the drawing itself, not just from searching the footer.

### 1.3 Footer Missing Body OD Block

**Priority: Medium**

The footer (`buildFooterEndColumns()`) renders AFT/FWD taper details and thread callouts but has no block for body diameter(s). For a straight-shaft job (one or more bodies, no tapers), the body OD is the only critical diameter and it appears nowhere in the exported PDF. The second reference drawing labels the body OD in the middle area of the schematic; at minimum this should appear in the center footer column alongside Customer/Vessel/Job.

### 1.4 Intermediate Dimension Spans Not Shown

**Priority: Medium**

Looking at the first reference drawing: dimensions like `13 3/4"`, `12 1/4"`, `20"`, `5 3/4"`, `24 1/2"`, `6/32"` are all shown as dimension lines between specific features. The current PDF dimension system only generates:
- OAL top rail
- Liner offset from SET (near edge)  
- Liner length
- End-taper lengths

It does not show general feature-to-feature spacings (e.g., distance from AFT thread end to taper SET, or spacing between two liners). A machinist laying out the shaft needs these intermediate dimensions, not just the total OAL.

### 1.5 SEAL / Bearing Annotations

**Priority: Low**

The first reference drawing shows a "SEAL" label at a specific bearing location. There is no annotation mechanism in the app. Bearing seats, oil seal grooves, and similar features are callouts beyond the current component model. This is noted as a future addition — the domain is more complex than the current four component types cover.

### 1.6 Shaft Position Options Don't Include AFT/FWD

**Priority: Low**

The `ShaftPositionDropdown` in `ShaftScreen.kt:1009` offers:
```kotlin
listOf(ShaftPosition.PORT, ShaftPosition.STBD, ShaftPosition.CENTER, ShaftPosition.OTHER)
```
The enum in the explore output shows `PORT, STBD, AFT, FWD, OTHER`. The dropdown lists `CENTER` (which, if not in the enum, is a compile error — but since the app builds, `CENTER` must exist in the enum). However `AFT` and `FWD` are not offered, which matters for intermediate shafts in a tunnel or cutlass bearing arrangement. Worth reviewing what's actually in the enum vs what the dropdown shows.

---

## 2. Mechanical Engineering

### 2.1 Float Precision — Inch Fraction Accumulation

**Priority: Medium**

The model stores all measurements as `Float`. The parsing path `parseFractionOrDecimal()` → `toMmOrNull()` converts fractions like `3 7/16"` to mm via:
```kotlin
(3 + 7/16) * 25.4  →  3.4375 * 25.4  →  87.3125 mm
```
Most common shaft fractions (halves, quarters, eighths, sixteenths, thirty-seconds) are NOT exactly representable as Float after the × 25.4 conversion. `1/32" = 0.79375mm` — near the Float epsilon for values around 4000mm. Over many round-trips (save → load → display → re-enter) this can cause visible rounding in the inch display. The geometry layer already uses `Double` in several places (`OalWindow`, `OalComputations`), but the model itself is `Float`. At minimum, inch inputs should be stored at higher precision.

### 2.2 Two EPS Constants with Different Values and Overlapping Names

**Priority: Medium**

`geom/OalComputations.kt` exports `END_EPS_MM = 0.5` (imported into `ShaftPdfComposer.kt`).  
`ShaftPdfComposer.kt` also defines its own `private const val EPS_MM = 0.01` used in proximity checks for `getAftEndThread/getFwdEndThread/getAftEndTaper/getFwdEndTaper`.

So within the same file, proximity to a shaft end is checked at 0.5mm (via `END_EPS_MM`) in some functions and 0.01mm in others. The 0.5mm tolerance is ~1/64" which is coarse enough that a thread positioned at X=0.3mm would be treated as touching the AFT end. This inconsistency could cause components that aren't quite flush to show up in — or be dropped from — the footer.

### 2.3 Snap Tolerance Inconsistency Between ViewModel and Screen

**Priority: Low**

The ViewModel's `snapRawPositionMm()` uses unit-aware tolerance:
- Metric: 1.0mm
- Imperial: 0.04in × 25.4 = 1.016mm (nearly identical, but the intent is different)

The snap helpers called during component *update* in `ShaftScreen.kt` (lines 1360–1420) use:
```kotlin
SnapConfig()  // default toleranceMm = 1.0f always
```
These do not consult the ViewModel's unit system. When working in inches, users get metric snap tolerance on component edits. This is probably unnoticeable in practice since the values are nearly the same, but it's technically incorrect.

### 2.4 Excluded Thread / OAL Sync Timing

**Priority: Low**

`LaunchedEffect(overallIsManual, spec.bodies, spec.tapers, spec.threads, spec.liners)` syncs OAL in Auto mode. This runs on the composition, so there's a potential one-frame window where:
- The spec changes (component added)
- `resolvedComponents` gets recomputed
- But OAL hasn't synced yet (LaunchedEffect fires next frame)

For excluded threads (which modify the OAL window), this could briefly show an incorrect dimension on the preview. Not a data corruption issue — purely visual, one frame.

### 2.5 LET is Always the Larger Diameter — Not Always True for Coupling Tapers

**Priority: Low**

`letSet(t: Taper)` in `ShaftPdfComposer.kt:1002`:
```kotlin
val let = max(t.startDiaMm, t.endDiaMm)
val set = min(t.startDiaMm, t.endDiaMm)
```
LET (Large End of Taper) is forced to the larger diameter and SET to the smaller. For a propeller taper this is correct — the prop side is always the large end. For a coupling or flange taper that steps up toward the FWD end, this mapping is wrong. The footer would label SET/LET backwards relative to physical shaft orientation. The `taperRateText` field and the model's `startDiaMm`/`endDiaMm` preserve the authoring direction, but the footer rendering loses it.

---

## 3. Android Development Quality

### 3.1 God ViewModel — 2134 Lines, 40+ State Flows

**Priority: Medium**

`ShaftViewModel.kt` is 2134 lines. It manages: spec mutation, 25+ persisted settings flows, autosave, delete/undo/redo, component ordering, snap, tap-to-add, document I/O, session defaults, achievements, verbose logging, and dev options. The `init {}` block alone launches 25+ independent coroutines, one per setting key.

This works now but makes it hard to test any one concern in isolation. Splitting into at minimum:
- `ShaftSpecViewModel` (spec mutations, undo/redo)
- `ShaftSettingsViewModel` (persisted prefs bridge)
- `ShaftEditorViewModel` (UI state: selected component, tap-add, session defaults)

would reduce coupling and test surface significantly.

### 3.2 `composed {}` Modifier Is Deprecated

**Priority: Low**

`ShaftScreen.kt:1438` — `clickableWithoutRipple()` uses `Modifier.composed {}`. This API has been deprecated since Compose 1.5 in favor of `Modifier.Node`. The replacement is straightforward — use `Modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() })` inline, or build a proper `Modifier.Node`. Not a crash risk but it produces a lint warning and the deprecated path has known performance overhead.

### 3.3 `-Xlambdas=class` Compiler Flag Likely Unnecessary

**Priority: Low**

`app/build.gradle.kts` includes:
```kotlin
freeCompilerArgs = ["-Xlambdas=class"]
```
This was useful in early Compose to avoid `invokedynamic` on Android runtimes that handled it poorly. With minSdk=28 (Android 9, API 28), `invokedynamic` is fully supported and this flag just increases APK size by generating extra class files per lambda. It can likely be removed without any behavioral change.

### 3.4 init Block Launches 25+ Independent Coroutines

**Priority: Low**

Every settings preference has its own `viewModelScope.launch { SettingsStore.xyzFlow(getApplication()).collectLatest { ... } }`. With 25+ of these in `init {}`, there are 25+ coroutines collecting simultaneously from the same DataStore. The pattern is fine but verbose — they could be grouped into a single `combine()` or at least structured to share a single flow tap per DataStore instance. This doesn't cause bugs but is noisy and makes the init block hard to scan.

### 3.5 SettingsStore as Mutable Global Object

**Priority: Low**

`SettingsStore` is a Kotlin `object` (singleton) that exposes `var pdfPrefs: PdfPrefs` as a mutable field and `updatePdfPrefs { }` as a mutating function. This is global mutable state that both the ViewModel and the PDF composer read from. The ViewModel also maintains its own `_pdfTieringMode`, `_pdfShowComponentTitles`, etc. as separate StateFlows, then updates `SettingsStore.pdfPrefs` as a side effect. There are two representations of the same state, introducing a sync risk if any path updates one without the other.

### 3.6 Screen-Level Parameters Are 80+ in ShaftScreen

**Priority: Low**

`ShaftScreen()` has approximately 80 parameters (as of 2026-06-22 audit; was ~65 at initial analysis). This is a consequence of the "no state in screen composables" architecture, but it makes the call site (`ShaftRoute`) extremely long. Grouping related parameters into data classes (`ShaftScreenColorConfig`, `ShaftScreenDebugConfig`, `ShaftScreenCallbacks`) would reduce the signature without changing behavior.

### 3.7 onMoveComponentUp/Down Parameters Are No-Ops

**Priority: Low**

`ShaftScreen.kt:168-169`:
```kotlin
onMoveComponentUp: (String) -> Unit = {},      // reserved for future Move UI
onMoveComponentDown: (String) -> Unit = {},    // reserved for future Move UI
```
These are in the public function signature but nothing in the UI calls them. The ViewModel has `moveComponentUp/Down()` implementations. These parameters add dead weight. Remove them and call the ViewModel directly from a carousel UI element when the feature is actually built.

### 3.8 key(resetNonce) Wrapping the Entire Screen

**Priority: Low**

`ShaftScreen.kt:274`:
```kotlin
key(resetNonce) {
    // ... entire screen ...
}
```
This is a reasonable way to reset all Compose-local state on new/open operations, but it's a blunt instrument. It also resets scroll position, which could be jarring. A more surgical approach would reset only specific state holders (dialog open flags, scroll) via LaunchedEffect on the nonce.

### 3.9 Achievements System in a Professional Tool

**Priority: Aesthetic**

`util/Achievements.kt` implements a Steam-style achievement system. It's gated by `achievementsEnabled` (off by default) and adds 5+ settings keys, 2+ StateFlows, a dedicated screen, and unlock calls throughout the ViewModel. For a professional machinist tool, this adds code surface without domain value. Whether this stays as a fun easter egg or gets removed is a product call — but it should be reviewed.

### 3.10 Debug Flags Persisted to DataStore

**Priority: Low**

All dev options (verbose logging categories, OAL debug label, render overlays) persist across app restarts via DataStore. If a debug APK is handed to a real customer, any previously-enabled debug flags survive. A safer pattern is to reset dev flags to false on app startup unless dev options are explicitly enabled, or to store dev flags in a separate volatile preference that doesn't survive reinstall.

---

## 4. UI / UX

### 4.1 Portrait-Only Is the Biggest UX Limitation

**Priority: High**

`AndroidManifest.xml`:
```xml
android:screenOrientation="portrait"
```
Every shaft schematic is a horizontal object. The preview area on a portrait phone is roughly 3× wider than tall, but on a phone held portrait, the available width is only ~360dp — meaning a 150" shaft shows at very small scale. Rotating to landscape would give roughly 2× more horizontal space. Supporting landscape (even just for the preview / editor) would significantly improve the drawing's readability, especially for long multi-component shafts. This is the single highest-impact UX improvement available.

### 4.2 Project Information Collapsed by Default

**Priority: Medium**

`ShaftScreen.kt:674`:
```kotlin
ExpandableSection("Project Information", initiallyExpanded = false) {
```
Customer, Vessel, Job# — the core metadata of a work order — is hidden behind a collapsed section. In a job-shop context, users start every drawing by filling in these fields. Collapsing them by default adds friction and makes new users likely to miss them entirely. Suggest expanding by default or moving Job# at minimum to always-visible.

### 4.3 Undo/Redo Is Two Taps Away

**Priority: Medium**

Undo and Redo live inside a dropdown menu (`HistoryMenu` in `ShaftScreen.kt:900`). The user taps the history clock icon, then taps "Undo delete" from the dropdown. Standard Android UX for undo is a snackbar action (already used for delete confirmation — extend it with "Undo" action) or direct toolbar buttons. Two taps for undo is above the expected friction. A Snackbar "Deleted. UNDO" that calls `undoLastDelete()` inline is the more natural pattern.

### 4.4 No Recent Documents List

**Priority: Medium**

The Start Screen has New Drawing, Open..., Continue Draft, Settings. There is no list of recently opened files. For a daily-use tool, machinists are reopening yesterday's job often. The internal storage system exists; surfacing the 5 most recently saved files on the start screen would save significant friction.

### 4.5 "Swipe to Select" Hint Is Unclear

**Priority: Low**

`ShaftScreen.kt:717`:
```kotlin
Text("Swipe to select", style = MaterialTheme.typography.bodySmall)
```
This hint is displayed next to the "Components" heading. It's not clear which direction to swipe, or that swiping in the carousel advances through components (vs tapping). The preview also responds to tap-to-select. The two input paths (carousel swipe + preview tap) for selection should be explained more clearly, or the hint should be more specific ("Swipe carousel to navigate, tap preview to select").

### 4.6 "Highlight Selection in Preview" Toggle Is Noise

**Priority: Low**

`ShaftScreen.kt:696-701` renders a `Switch` labeled "Highlight selection in preview" inline in the editor. This is a developer-convenience setting that doesn't belong in the primary editor surface. Most users will never want to turn this off. Move it to Settings or Developer Options.

### 4.7 Double Header — "Shaft Editor" Text + TopAppBar

**Priority: Low**

The top of `ShaftScreen` renders:
1. A `Text("Shaft Editor", ...)` label with `statusBarsPadding()`
2. A standard `TopAppBar` with a hamburger icon and action buttons

These stack, creating a header region taller than necessary. The TopAppBar `title {}` is empty — the "Shaft Editor" text above it could just be the TopAppBar title. This wasted vertical space is especially noticeable on short phones.

### 4.8 No Confirmation on New / Unsaved Changes

**Priority: Low**

Tapping the New Document icon in the toolbar calls `onNew()` → `newDocument()` immediately. While autosave preserves the work (the Continue Draft flow on the start screen recovers it), there is no in-line "You have unsaved changes. Continue?" prompt. The `hasUnsavedWork()` method exists — it should gate the New/Open actions with a confirmation dialog.

### 4.9 No "Save As" / Document Rename

**Priority: Low**

The Save action overwrites the current file. There is no "Save As" to create a copy with a different name. In a shop that regularly builds near-identical shafts for the same vessel, "Save As" to create a variant is a common workflow. SAF already supports creating new files — the operation exists, just not exposed in the UI.

### 4.10 Component Naming Cannot Be Customized

**Priority: Low**

Bodies are "Body 1", "Body 2", etc. Tapers are "Taper 1", etc. Only Liners have an optional label field. For a shaft with 5 body segments (bearing journals, stuffing box, coupling pilot), having user-defined labels ("FWD Journal", "AFT Journal", "Coupling Pilot") would make the component list and the PDF labels far more useful.

---

## 5. Things That Are Working Well

- **Model architecture is sound**: Immutable data classes, all canonical units in mm, conversion only at the UI edge. This is the right call and prevents entire classes of unit bugs.
- **Autosave (1.5s debounce)**: Well-designed. The draft recovery flow is clean.
- **Delete undo/redo (10-step, full snapshot)**: Robust. Restoring `beforeSpec` + `beforeOrder` is the right approach; it handles body split/merge correctly.
- **Body center-break compression in PDF**: The S-curve break edge on long bodies is a clean detail that's rare in mobile apps — matches real drafting convention.
- **keyway rendering**: Width, depth, length, offset-from-SET, and spooned lead-in are all modeled. The PDF rendering of the keyway slot as a white void with outline is correct for section-view conventions.
- **SAF integration**: Using the Storage Access Framework for file I/O is the correct Android approach — no permissions needed, no Android 11+ scoped storage issues.
- **Versioned document format (.shaft, ShaftDocCodec V1)**: Good forward-compatibility foundation. Having migrations in `ShaftSpecMigrations.kt` is the right structure.
- **Excluded threads**: The AFT/FWD end thread that sits outside the OAL (e.g., a prop nut thread that doesn't count toward measured length) is a real-world need handled correctly.
- **Session add defaults**: Remembering last-used dimensions per component type is a good time-saver for repetitive work.

---

## 6. Summary of Priority Items

| # | Area | Issue | Priority | Status |
|---|------|--------|----------|--------|
| 1 | Machinist | Taper rate shows 1:N instead of shop-standard format | High | ✅ Fixed 2026-06-19 |
| 2 | Machinist | Body OD not shown on drawing (callouts stubbed out) | High | |
| 3 | UI/UX | Portrait-only orientation limits preview width | High | |
| 4 | Machinist | Footer missing body OD block | Medium | |
| 5 | Machinist | No intermediate feature-to-feature dimensions | Medium | |
| 6 | Dev | ViewModel is 2134 lines, 40+ flows, needs splitting | Medium | |
| 7 | UI/UX | Project Information collapsed by default | Medium | ✅ Fixed 2026-06-22 — moved to modal bottom sheet via toolbar Assignment icon |
| 8 | UI/UX | Undo is two taps; no snackbar UNDO action | Medium | |
| 9 | UI/UX | No recent documents on start screen | Medium | |
| 10 | Engineering | Float precision in inch→mm conversion | Medium | |
| 11 | Engineering | Two conflicting EPS constants in PDF composer | Medium | ✅ Fixed 2026-06-19 |
| 12 | Dev | `composed {}` is deprecated | Low | |
| 13 | Dev | `-Xlambdas=class` flag likely unnecessary | Low | |
| 14 | Dev | Debug flags persist across restarts | Low | |
| 15 | Machinist | LET/SET direction assumes prop-convention | Low | |
| 16 | UI/UX | No "Save As" / document rename | Low | |
| 17 | UI/UX | No confirmation on New with unsaved changes | Low | |
| 18 | UI/UX | Component names can't be customized (except liners) | Low | |
| 19 | UI/UX | "Highlight selection" toggle in editor surface | Low | |
| 20 | UI/UX | Double header region wastes vertical space | Low | |
| 21 | Engineering | FWD-ref tapers/liners drift on OAL change | High | ✅ Fixed 2026-06-22 — `withNewOal()` in ShaftSpecExtensions.kt; both OAL mutation paths updated |
| 22 | PDF | Excluded AFT thread shift overflows shaft FWD edge past right margin | High | ✅ Fixed 2026-06-22 — ptPerMm now derived from total content span (OAL + excluded thread tails); layout always fits within geomRect |
