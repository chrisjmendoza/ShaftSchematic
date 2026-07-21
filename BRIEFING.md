# ShaftSchematic — Project Briefing

**Generated:** 2026-05-03  
**Last updated:** 2026-07-18  
**Current Version:** 1.1.1  
**Series:** v0.5.x — runout/wear docs, line thickness, OAL fix

> This is the narrative onboarding/architecture doc. Feature-by-feature status lives in
> `TODO.md` §0 (single source of truth); the release-series roadmap lives in
> `docs/ROADMAP.md`. Status is not duplicated here.

---

## What It Is

ShaftSchematic is an Android app (portrait-locked, single Activity, Jetpack Compose + Material3) for modeling marine propeller-shaft assemblies. A machinist or shipyard engineer can define a multi-segment shaft, see a live dimensioned preview, and export a one-page technical PDF — without opening CAD software.

Target users: machinists, shipyards, repair technicians, marine engineers.  
Target hardware: Android 8.0+ (API 28), Target SDK 36.

---

## Current Status (v1.1.1 — Stable)

The core feature set is **shipped and working**: modeling (bodies with keyways, tapers
with keyways and auto-rate, threads with OAL exclusion, liners, coupler bolt slots), live preview,
validation (blocking + warnings), three PDF documents (shaft drawing, runout sheet,
wear document), internal library with autosave and backup/restore, and full settings.

For the authoritative feature-by-feature status table, see **`TODO.md` §0 — Current
System State**. For what's next, see **`docs/ROADMAP.md`**.

---

## Architecture Summary

```
User Input → ShaftViewModel → ShaftSpec (mm)
           → resolveComponents() (derived auto-bodies, ui/resolved/)
           → ShaftLayout (px mapping)
           → ShaftRenderer (preview geometry) → ShaftDrawing (Compose host) → Screen
           → ShaftPdfComposer / RunoutPdfComposer / WearPdfComposer
             (PDF — separate drawing code, shared model + layout math)
```

**Key invariants:**
- All model geometry stored in **millimeters only**. Inches are only rendered at UI display edges.
- `ShaftViewModel` extends `AndroidViewModel` (needs `Application` for DataStore). Always instantiated via `ShaftViewModelFactory`.
- `ShaftLayout` fits both axes: `pxPerMm = min(width/oal, height/maxOD)`.
- `ShaftRenderer` (preview) and `ShaftPdfComposer` (PDF) are **separate drawing paths** sharing the same model and layout math but using separate Canvas drawing code. A fix in one does not propagate to the other automatically.
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
pdf/            ← ShaftPdfComposer, RunoutPdfComposer, WearPdfComposer + dim/ + notes/ + render/
data/           ← SettingsStore (DataStore), AutosaveManager
doc/            ← ShaftDocCodec (JSON serialization + migrations)
io/             ← InternalStorage (app-private file management), ShaftBackup
settings/       ← PdfPrefs, RunoutConfig
util/           ← UnitSystem, parsing helpers, PreviewColorSetting
```

---

## Component Model

A `ShaftSpec` is the root aggregate:

| Component | Description |
|---|---|
| `Body` | Constant-diameter cylinder. Fields: `diaMm`, `startFromAftMm`, `lengthMm`. Keyway hosted (end-referenced): `keywayWidthMm`, `keywayDepthMm`, `keywayLengthMm`, `keywayOffsetFromEndMm`, `keywayEnd` (AFT/FWD), `keywaySpooned`. Explicit bodies are non-negotiable (collide, never split). |
| `Taper` | Linear diameter transition. Fields: `startDiaMm` / `endDiaMm`, `lengthMm`, `taperRateText`. Keyway hosted (SET-referenced): `keywayWidthMm`, `keywayDepthMm`, `keywayLengthMm`, `keywayOffsetFromSetMm`, `keywaySpooned`. |
| `Threads` | Threaded segment. Fields: `majorDiaMm`, pitch (`pitchMm` + `tpi`), `excludeFromOAL`. |
| `Liner` | Outer sleeve. Fields: `odMm`, anchor reference (`LinerAnchor`), authored direction. |

`ShaftSpec` also carries `keyways180Apart` — a drawing note that the shaft's keyways are clocked 180° apart (far-side keyway renders hidden/dashed).

All axial positions are measured **AFT → FWD**. `ShaftSpec.validate()` checks non-negative values and segment bounds; it does not test for overlaps — overlap enforcement lives in collision detection (`collidingIds()`), separate from `validate()`. A liner over an *auto*-body is fine; an overlap of an *explicit* body is hard-blocked.

---

## Key Sub-Systems

### OAL Window
`geom/OalComputations.kt` — computes how much length is excluded at the AFT/FWD ends when end threads have `excludeFromOAL = true`. Also derives the actual SET (small end of taper) positions in measurement space from taper geometry. Coordinate-anchored (not list-order dependent). Tested in `OalComputationsTest`.

### Snap Engine
`ui/viewmodel/SnapUtils.kt` — `buildSnapAnchors(spec)` + `snapPositionMm(rawMm, anchors, toleranceMm)`. Pure mm, no pixel math. Default tolerance: 1.0 mm. Unit-tested in `ShaftSpecSnapExtensionsTest`.

### Tier Assignment
`geom/DeterministicTierAssigner.kt` — assigns PDF dimension tier/rail slots to components deterministically. Tested in `DeterministicTierAssignerTest`.

### Resolved Component Pipeline
`ui/resolved/ResolvedComponent.kt` — derived pipeline that generates auto bodies for unoccupied spans without persisting them. **Fully wired** (2026-07-18) into the schematic screen/PDF and the runout & wear documents — all rendering consumes the resolved list, not the raw spec.

### Internal Storage
`io/InternalStorage.kt` — manages the app-private `.shaft` file list; handles save, load, delete, overwrite confirmation. Filenames follow the convention: `{vessel/job}_{position}_{date}` (position suffix is optional, falls back to generated name).

### PDF Export
`pdf/ShaftPdfComposer.kt` — renders to `PdfDocument` using `ShaftLayout` for geometry, but draws with its **own Canvas drawing functions** (bodies, tapers, threads, liners), not `ShaftRenderer`. The two rendering paths share the same model and layout math but use separate drawing code — a fix in `ShaftRenderer` does not automatically propagate to the PDF. Includes: component labels (with row-based collision avoidance), major/minor grid, centerline rules, dimension tiers, footer (shaft position, taper KW data). Auto-open after export is configurable.

---

## Navigation / Screen Flow

```
StartScreen
  ├─ New Drawing → ShaftEditorRoute (blank spec)
  ├─ Open → file picker → ShaftEditorRoute (loaded spec)
  ├─ Continue Draft → ShaftEditorRoute (autosaved spec)
  ├─ Discard Draft → clears autosave → stays on Start
  └─ Settings → SettingsRoute

ShaftEditorRoute
  ├─ Component Carousel (swipe/select components)
  ├─ Add Component dialogs (Body / Taper / Threads / Liner)
  ├─ Delete + Undo (snackbar)
  ├─ Export PDF → system picker (SAF)
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
- **Overlaps** — `validate()` only checks bounds, not intersections; overlap enforcement is `collidingIds()`. Liners over *auto*-bodies are fine, but explicit bodies are non-negotiable: any overlap of one is hard-blocked and blocks export.
- **Committed-on-blur inputs** — numeric fields do not mutate VM state while the user is typing.
