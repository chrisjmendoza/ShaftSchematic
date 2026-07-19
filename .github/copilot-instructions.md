# ShaftSchematic – AI Agent Instructions

## Project Overview
Android app for modeling marine prop-shaft assemblies with real-time preview and PDF
export (shaft drawing, runout sheet, wear document). Single-activity Compose app using
Material3, targeting Android 8.0+ (API 28), Target SDK 36.

Authoritative sources, in order:
1. `CLAUDE.md` (repo root) — project conventions and critical invariants
2. `app/src/main/java/com/android/shaftschematic/docs/` — per-subsystem contract docs
   (index: `README.md` in that folder). Read the relevant doc before editing a subsystem.
3. `CONTRIBUTING.md` — architecture overview and coding guidelines

## Critical Architecture Rules

### Units: Millimeters Everywhere
- **All model geometry stored in millimeters** (`ShaftSpec`, `Body`, `Taper`, `Threads`,
  `Liner`, `CouplerBoltSlot`)
- Convert to inches **only at UI edges**: formatting, grid legends, dimension labels
- Never store or calculate geometry in inches

### Rendering: Two Separate Drawing Paths
Preview and PDF share the same model and layout math but have **separate Canvas code**:

```
ui/drawing/render/ShaftLayout.kt   → mm→px mapping, layout bounds (shared math)
ui/drawing/render/ShaftRenderer.kt → preview geometry (DrawScope)
ui/drawing/compose/ShaftDrawing.kt → Compose host; grid + axis labels only
pdf/ShaftPdfComposer.kt            → shaft drawing PDF (own drawing code)
pdf/RunoutPdfComposer.kt           → runout sheet PDF
pdf/WearPdfComposer.kt             → wear document PDF
```

A fix in `ShaftRenderer` does **not** propagate to the PDF composers automatically
(or vice versa). When changing how anything draws, check both paths.

### Package Structure (Canonical)
```
com.android.shaftschematic/
├─ MainActivity.kt
├─ model/     ← ShaftSpec, components, migrations (all mm)
├─ geom/      ← pure geometry: OAL, SET positions, tier assignment, runout bubbles
├─ doc/       ← ShaftDocCodec (JSON serialization + migrations)
├─ io/        ← InternalStorage (.shaft library), ShaftBackup
├─ data/      ← SettingsStore (DataStore), AutosaveManager
├─ pdf/       ← PDF composers + dim/, notes/, render/
├─ settings/  ← PdfPrefs, RunoutConfig
├─ ui/
│  ├─ drawing/   ← compose/ (hosts), render/ (layout + renderers)
│  ├─ screen/    ← StartScreen, ShaftEditorRoute, ShaftScreen, ComponentCarousel,
│  │              AddComponentDialogs, Runout/Wear/Settings routes
│  ├─ input/     ← NumericInputField (commit-on-blur numeric entry)
│  ├─ resolved/  ← ResolvedComponent (derived auto-body pipeline)
│  ├─ order/     ← ComponentOrder
│  ├─ viewmodel/ ← ShaftViewModel, factory, snap utils
│  ├─ nav/       ← AppNav, PDF export routes
│  └─ dialog/, config/, util/, theme/
└─ util/      ← UnitSystem, parsing, taper rate auto-calc, naming helpers
```
Match package declaration to folder structure exactly.

## State Management
- `ShaftViewModel` extends `AndroidViewModel`; **always use**
  `viewModel(factory = ShaftViewModelFactory)`
- State exposed via `StateFlow`; mutate with `.update { it.copy(...) }`
- **Commit-on-blur** for numeric inputs: no VM mutation while typing;
  `ui/input/NumericInputField.kt` implements this, and a tap-and-leave with no edit
  must be a no-op (critical invariant — see `docs/NumberField.md`)
- Exception: the OAL field commits on every keystroke in manual mode (intentional)

## Persistence
- User preferences: `SettingsStore` (DataStore)
- Document state: internal `.shaft` JSON library (`io/InternalStorage.kt`) with
  versioned envelope + migrations (`doc/ShaftDocCodec.kt`); SAF for open/export
- Backup: `io/ShaftBackup.kt` (ZIP backup/restore, pre-update snapshots)

## Build & Dependencies
- Gradle version catalogs in `gradle/libs.versions.toml`
- Kotlin 2.2.20 with Compose plugin; Compose BOM 2024.09.00; DataStore 1.1.1
- **Build:** `./gradlew assembleDebug` · **Tests:** `./gradlew test` ·
  **Lint:** `./gradlew lint`

## Code Style
- Immutability: `data class` + `.copy()`; avoid mutable collections
- Null safety: `?:`, `?.let {}` instead of `!!`
- File headers describe "What/Inputs/Outputs"; KDoc for public APIs
