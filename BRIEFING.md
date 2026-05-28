# ShaftSchematic — Project Briefing

**Generated:** 2026-05-03  
**Current Version:** 1.1.1  
**Series:** v0.4.x development queue active

---

## What It Is

ShaftSchematic is an Android app (portrait-locked, single Activity, Jetpack Compose + Material3) for modeling marine propeller-shaft assemblies. A machinist or shipyard engineer can define a multi-segment shaft, see a live dimensioned preview, and export a one-page technical PDF — without opening CAD software.

Target users: machinists, shipyards, repair technicians, marine engineers.  
Target hardware: Android 8.0+ (API 28), Target SDK 36.

---

## Current Status (v1.1.1 — Stable)

The core feature set is **shipped and working**:

| Area | Status |
|---|---|
| Core data model (Body, Taper, Threads, Liner) | ✅ Stable |
| ShaftLayout & ShaftRenderer | ✅ Contract-locked |
| Live preview (Canvas, grid, labels) | ✅ Working |
| Preview color presets + B/W mode | ✅ Shipped |
| PDF export (one-page, landscape) | ✅ Stable, theme-safe |
| Unit switching mm ↔ inch (persisted) | ✅ Working |
| Component delete + multi-step Undo (up to 10) | ✅ Working |
| Internal save/open (`.shaft` JSON) | ✅ Working |
| SAF open/export | ✅ Working |
| Component carousel + editor UI | ✅ Working |
| Snapping engine (`SnapEngine`) | ✅ Implemented & unit-tested |
| OAL window / excluded thread logic | ✅ Implemented & unit-tested |
| Autosave / draft restore on launch | ✅ Working |
| Settings screen (units, grid, PDF prefs) | ✅ Working |
| Developer Options screen | ✅ Working |
| Achievements screen | ✅ Stub present |
| Portrait orientation lock | ✅ Enforced |

---

## Architecture Summary

```
User Input → ShaftViewModel → ShaftSpec (mm)
           → ShaftLayout (px mapping)
           → ShaftRenderer (geometry drawing)
           → ShaftDrawing (Compose canvas host)
           → Screen
           → ShaftPdfComposer (PDF, mirrors same renderer)
```

**Key invariants:**
- All model geometry stored in **millimeters only**. Inches are only rendered at UI display edges.
- `ShaftViewModel` extends `AndroidViewModel` (needs `Application` for DataStore). Always instantiated via `ShaftViewModelFactory`.
- `ShaftLayout` fits both axes: `pxPerMm = min(width/oal, height/maxOD)`.
- `ShaftRenderer` is the single source of truth for geometry drawing (both preview and PDF use it).
- No geometry logic lives in Compose composables or in the PDF composer directly.

**Package layout:**
```
model/          ← immutable data classes (all mm)
geom/           ← pure geometry helpers (OAL, tier assignment, snap)
ui/viewmodel/   ← ShaftViewModel + SnapUtils + SessionAddDefaults
ui/drawing/     ← ShaftLayout, ShaftRenderer, GridRenderer, ShaftDrawing
ui/screen/      ← StartScreen, ShaftScreen, ShaftEditorRoute, dialogs
ui/input/       ← NumberField, ShaftMetaSection
ui/resolved/    ← ResolvedComponent (derived pipeline, partial)
ui/order/       ← ComponentKey, ComponentKind (ordering layer)
ui/theme/       ← Material3 theme
pdf/            ← ShaftPdfComposer + dim/ + notes/ + render/
data/           ← SettingsStore (DataStore), AutosaveManager
doc/            ← ShaftDocCodec (JSON serialization + migrations)
io/             ← InternalStorage (app-private file management)
settings/       ← PdfPrefs, PdfTieringMode
util/           ← UnitSystem, parsing helpers, PreviewColorSetting
```

---

## Component Model

A `ShaftSpec` is the root aggregate:

| Component | Description |
|---|---|
| `Body` | Constant-diameter cylinder. Fields: `diaMm`, `startFromAftMm`, `lengthMm`. |
| `Taper` | Linear diameter transition. Stores both `startDiaMm` / `endDiaMm` + `lengthMm`. |
| `Threads` | Threaded segment. Stores `majorDiaMm`, pitch (`pitchMm` + `tpi`), `excludeFromOAL` flag. |
| `Liner` | Outer sleeve. Stores `odMm`, anchor reference (`LinerAnchor`), authored direction. |

All axial positions are measured **AFT → FWD**. `ShaftSpec.validate()` checks non-negative values and segment bounds; it does not test for overlaps (by design — overlaps are valid for liners over bodies).

---

## Key Sub-Systems

### OAL Window
`geom/OalComputations.kt` — computes how much length is excluded at the AFT/FWD ends when end threads have `excludeFromOAL = true`. Also derives the actual SET (small end of taper) positions in measurement space from taper geometry. Coordinate-anchored (not list-order dependent). Tested in `OalComputationsTest`.

### Snap Engine
`ui/viewmodel/SnapUtils.kt` — `buildSnapAnchors(spec)` + `snapPositionMm(rawMm, anchors, toleranceMm)`. Pure mm, no pixel math. Default tolerance: 1.0 mm. Unit-tested in `ShaftSpecSnapExtensionsTest`.

### Tier Assignment
`geom/DeterministicTierAssigner.kt` — assigns PDF dimension tier/rail slots to components deterministically. Tested in `DeterministicTierAssignerTest`.

### Resolved Component Pipeline
`ui/resolved/ResolvedComponent.kt` — early-stage derived pipeline intended to generate auto bodies for UI rendering without persisting them. **Partially implemented** — the full pipeline (auto body seeding, ordered rendering) is still planned in v0.4.x.

### Internal Storage
`io/InternalStorage.kt` — manages the app-private `.shaft` file list; handles save, load, delete, overwrite confirmation. Filenames follow the convention: `{vessel/job}_{position}_{date}` (position suffix is optional, falls back to generated name).

### PDF Export
`pdf/ShaftPdfComposer.kt` — renders to `PdfDocument` using the same `ShaftLayout` + `ShaftRenderer` as the screen preview. Includes: component labels, major/minor grid, centerline rules, dimension tiers, footer (shaft position, taper KW data). Auto-open after export is configurable.

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

## Active Sprint: v0.4.x

### In Progress / Next Up

**1.1 ✅ Snap Engine** — done  
**1.2 Preview Tap → mm (not started)** — add `onTapAtMm` lambda to `ShaftDrawing`; convert `Offset → mm`; route through `SnapEngine`; store `pendingAddPositionMm` in ViewModel.  
**1.3 Add-At-Position Flow (not started)** — `startAddAtPosition(positionMm)` VM intent; chooser dialog; prefilled defaults from axial gap.  
**1.4 Resolved Component Pipeline (not started)** — auto body seeding from OAL; AFT/FWD authored reference toggle in liner card; unified Add Component entry point.  
**1.5 Keyway support on Body (not started)** — data model field + editor UI (hosted feature, not standalone component).

### Roadmap Horizon

| Series | Focus |
|---|---|
| v0.5.x | Keyway (taper-hosted), Shoulder/Relief component, Coupling component |
| v0.6.x | Component presets, reference geometry overlays, machining heuristic warnings |
| v0.7.x | Optional cloud save, DXF export (if approved) |
| v1.0 | All component types, full single-page PDF, complete test coverage, complete docs |

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
- **Overlaps are valid** — liners intentionally overlap bodies; `validate()` only checks bounds, not intersections.
- **Committed-on-blur inputs** — numeric fields do not mutate VM state while the user is typing.
