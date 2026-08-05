# ShaftSchematic — Project Briefing

**Generated:** 2026-05-03  
**Last updated:** 2026-08-05 — five editor tabs / five PDF documents (undercut drawing + consolidated output), refreshed the PDF-export and navigation sections, corrected the snap-engine signature  
**Current Version:** computed at build time from git history — `app/build.gradle.kts` (`versionName = "1.3.<n>"`); no version is hard-coded in the docs  
**Series:** v0.5.x — runout/wear/undercut docs, consolidated output, line thickness, OAL fix

> This is the narrative onboarding/architecture doc. Feature-by-feature status lives in
> `TODO.md` §0 (single source of truth); the release-series roadmap lives in
> `docs/ROADMAP.md`. Status is not duplicated here.

---

## What It Is

ShaftSchematic is an Android app (portrait-locked, single Activity, Jetpack Compose + Material3) for modeling marine propeller-shaft assemblies. A machinist or shipyard engineer can define a multi-segment shaft, see a live dimensioned preview, and export a one-page technical PDF — without opening CAD software.

Target users: machinists, shipyards, repair technicians, marine engineers.  
Target hardware: Android 8.0+ (API 28), Target SDK 36.

---

## Current Status (Stable)

The core feature set is **shipped and working**: modeling (bodies with keyways, tapers
with keyways and auto-rate, threads with OAL exclusion, liners, coupler bolt slots), live preview,
validation (blocking + warnings), five PDF documents (shaft drawing, classic runout sheet,
wear document, undercut drawing, consolidated output sheet), internal library with autosave
and backup/restore, and full settings.

For the authoritative feature-by-feature status table, see **`TODO.md` §0 — Current
System State**. For what's next, see **`docs/ROADMAP.md`**.

---

## Architecture Summary

```
User Input → ShaftViewModel → ShaftSpec (mm)
           → resolveComponents() (derived auto-bodies, ui/resolved/)
           → ShaftLayout (px mapping — preview only)
           → ShaftRenderer (preview geometry) → ShaftDrawing (Compose host) → Screen
           → ShaftPdfComposer / RunoutPdfComposer / WearPdfComposer / UndercutPdfComposer
             (PDF — separate drawing code, own fit + compression math)
```

**Key invariants:**
- All model geometry stored in **millimeters only**. Inches are only rendered at UI display edges.
- `ShaftViewModel` extends `AndroidViewModel` (needs `Application` for DataStore). Always instantiated via `ShaftViewModelFactory`.
- `ShaftLayout` fits both axes: `pxPerMm = min(width/oal, height/maxOD)`.
- `ShaftRenderer` (preview) and `ShaftPdfComposer` (PDF) are **separate drawing paths** sharing the same model but using separate Canvas drawing code and separate scale math (the composer computes its own `ptPerMm`; it never calls `ShaftLayout`). A fix in one does not propagate to the other automatically.
- No geometry logic lives in Compose composables.

**Package layout:**
```
model/          ← immutable data classes (all mm)
geom/           ← pure geometry helpers (OAL, tier assignment, snap)
ui/viewmodel/   ← ShaftViewModel + SnapUtils + SessionAddDefaults
ui/drawing/     ← ShaftLayout, ShaftRenderer, GridRenderer, ShaftDrawing
ui/screen/      ← StartScreen, ShaftScreen, ShaftEditorRoute, dialogs
ui/input/       ← NumericInputField, TaperSetLetMapping
ui/resolved/    ← ResolvedComponent (derived auto-body pipeline)
ui/order/       ← ComponentOrder (ComponentKey, ComponentKind)
ui/theme/       ← Material3 theme
pdf/            ← ShaftPdfComposer, RunoutPdfComposer, WearPdfComposer, UndercutPdfComposer + dim/ + notes/ + render/
data/           ← SettingsStore (DataStore), AutosaveManager
doc/            ← ShaftDocCodec (JSON serialization + migrations)
io/             ← InternalStorage (app-private file management), ShaftBackup
settings/       ← PdfPrefs, RunoutConfig, AppearancePrefs
util/           ← UnitSystem, parsing helpers, PreviewColorSetting
```

---

## Component Model

A `ShaftSpec` is the root aggregate:

| Component | Description |
|---|---|
| `Body` | Constant-diameter cylinder. Fields: `diaMm`, `startFromAftMm`, `lengthMm`. Keyway hosted (end-referenced): `keywayWidthMm`, `keywayDepthMm`, `keywayLengthMm`, `keywayOffsetFromEndMm`, `keywayEnd` (AFT/FWD), `keywaySpooned`. Bodies are the fluid base: plain bodies split around sacred components, keyed bodies stay whole, and bodies never collide. |
| `Taper` | Linear diameter transition. Fields: `startDiaMm` / `endDiaMm`, `lengthMm`, `taperRateText`. Keyway hosted (SET-referenced): `keywayWidthMm`, `keywayDepthMm`, `keywayLengthMm`, `keywayOffsetFromSetMm`, `keywaySpooned`. |
| `Threads` | Threaded segment. Fields: `majorDiaMm`, pitch (`pitchMm` + `tpi`), `excludeFromOAL`. |
| `Liner` | Outer sleeve. Fields: `odMm`, anchor reference (`LinerAnchor`), authored direction. |

`ShaftSpec` also carries `keyways180Apart` — a drawing note that the shaft's keyways are clocked 180° apart (far-side keyway renders hidden/dashed) — and `keyways90Apart` + `keyways90Cw`, its mutually-exclusive 90°-apart alternative (CW/CCW from the AFT keyway, viewed from aft; renders as an edge notch, not a hidden line).

All axial positions are measured **AFT → FWD**. `ShaftSpec.validate()` checks non-negative values and segment bounds; it does not test for overlaps — overlap enforcement lives in collision detection (`collidingIds()`), separate from `validate()`. `collidingIds()` checks only sacred pairs (taper/thread/liner); bodies are fluid base material and never collide (a liner legitimately runs over a body).

---

## Key Sub-Systems

### OAL Window
`geom/OalComputations.kt` — computes how much length is excluded at the AFT/FWD ends when end threads have `excludeFromOAL = true`. Also derives the actual SET (small end of taper) positions in measurement space from taper geometry. Coordinate-anchored (not list-order dependent). Tested in `OalComputationsTest`.

### Snap Engine
`ui/viewmodel/SnapUtils.kt` — `buildSnapAnchors(spec)` + `snapPositionMm(rawMm, anchors, config: SnapConfig)`. Pure mm, no pixel math. Tolerance comes from `snapToleranceMm(unit)`: 1.0 mm in metric, 0.04 in (≈1.016 mm) in imperial, so the snap radius feels the same on screen either way. Unit-tested in `ShaftSpecSnapExtensionsTest`.

### Tier Assignment
`geom/DeterministicTierAssigner.kt` — assigns PDF dimension tier/rail slots to components deterministically. Tested in `DeterministicTierAssignerTest`.

### Resolved Component Pipeline
`ui/resolved/ResolvedComponent.kt` — derived pipeline that generates auto bodies for unoccupied spans without persisting them. **Fully wired** (2026-07-18) into the schematic screen/PDF and the runout & wear documents — all rendering consumes the resolved list, not the raw spec.

### Internal Storage
`io/InternalStorage.kt` — manages the app-private `.shaft` file list; handles save, load, delete, overwrite confirmation. Filenames follow the convention: `{vessel/job}_{position}_{date}` (position suffix is optional, falls back to generated name).

### PDF Export
`pdf/ShaftPdfComposer.kt` — renders to `PdfDocument` with its **own** scale math (`computeDetailPtPerMm`, plus the compressed x-map from `geom/ProfileCompression.kt`) and its **own Canvas drawing functions** (bodies, tapers, threads, liners), not `ShaftLayout`/`ShaftRenderer`. The two rendering paths share the model but not the drawing code — a fix in `ShaftRenderer` does not automatically propagate to the PDF. Includes: component labels (with row-based collision avoidance), centerline rules, dimension tiers, Ø callouts, footer (shaft position, taper KW data). PDFs draw no grid. Auto-open after export is configurable.

The four composers produce five documents: shaft drawing, classic runout sheet, wear
document, undercut drawing, and the consolidated output sheet
(`composeRunoutPdf(consolidated = true)`).

---

## Navigation / Screen Flow

```
StartScreen
  ├─ New Drawing → ShaftEditorRoute (blank spec)
  ├─ Open → file picker → ShaftEditorRoute (loaded spec)
  ├─ Unsaved drafts (rolling ring, up to 3 entries, per-document identity)
  │    ├─ tap an entry → continueDraft(draftId) → ShaftEditorRoute
  │    └─ discard an entry → discardDraft(draftId) → stays on Start
  └─ Settings → SettingsRoute (units, appearance, PDF export, data) / Help / About

ShaftEditorRoute (sidebar hosts 5 tabs)
  ├─ Schematic · Runout Sheet · Wear Document · Undercut Drawing · Consolidated Output
  ├─ Component Carousel (swipe/select components)
  ├─ Add Component dialogs (Body / Taper / Threads / Liner / Coupler Bolt Slot)
  ├─ Delete + Undo (snackbar); session undo/redo history menu
  ├─ Export PDF → system picker (SAF); "Export all" batch from the Output tab
  ├─ Save (internal) / Open (internal)
  └─ Developer Options (debug gating)
```

---

## Test Coverage

Unit tests live in `app/src/test/`:

| Test file | Covers |
|---|---|
| `ShaftSpecTest` | Spec helpers: coverage, freeToEnd, maxOD |
| `ShaftSpecSnapExtensionsTest` | Snap engine edge cases |
| `SegmentTest` | Segment validity |
| `ShaftPositionTest` | Position enum logic |
| `OalComputationsTest` | OAL exclusion logic |
| `DeterministicTierAssignerTest` | PDF tier assignment |
| `doc/` | Codec round-trip, migration |
| `persistence/` | Internal storage read/write |
| `pdf/` | PDF composer smoke tests |

Instrumented tests in `app/src/androidTest/` include a `ClearDataStoreRule` to isolate DataStore state between runs.

---

## Active Sprint

Sprint status, the active queue, and the release-series roadmap are tracked in
**`TODO.md`** and **`docs/ROADMAP.md`** — not duplicated here.

---

## Build Info

```
Kotlin:        2.2.20 (Compose compiler plugin)
Compose BOM:   2024.09.00
DataStore:     1.1.1
Min SDK:       28
Target SDK:    36
Build system:  Gradle version catalogs (gradle/libs.versions.toml)
```

**Run tests:**
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
./gradlew --no-configuration-cache test
```

---

## Known Constraints / Design Decisions

- **Portrait only** — landscape is disabled; editor layout optimized for portrait.
- **Single-page PDF only** — multi-page PDF is explicitly out of scope through v1.0.
- **No pixel math in ViewModel** — VM is the geometry authority; UI passes raw mm coordinates.
- **Auto bodies never persisted** — when the resolved pipeline is complete, auto-generated bodies exist only in the derived view layer.
- **Overlaps** — `validate()` only checks bounds, not intersections; overlap enforcement is `collidingIds()`, which flags only sacred pairs (taper/thread/liner). Bodies are fluid base material and never collide; a sacred component added over a plain body splits it, while a keyed body stays whole.
- **Committed-on-blur inputs** — numeric fields do not mutate VM state while the user is typing.
