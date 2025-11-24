# ShaftSchematic – AI Agent Instructions

## Project Overview
Android app for modeling marine prop-shaft assemblies with real-time preview and PDF export. Single-activity Compose app using Material3, targeting Android 8.0+ (API 28).

## Critical Architecture Rules

### Units: Millimeters Everywhere
- **All model geometry stored in millimeters** (`ShaftSpec`, `Body`, `Taper`, `Threads`, `Liner`)
- Convert to inches **only at UI edges**: formatting, grid legends, dimension labels
- Never store or calculate geometry in inches
- Example: `Body.diaMm`, `Taper.lengthMm` are canonical; UI displays based on `UnitSystem` preference

### Rendering: Single Source of Truth
Drawing logic lives in **one place only** to avoid divergence between preview and PDF:

```
ui/drawing/render/ShaftLayout.kt   → Computes mm→px mapping, layout bounds
ui/drawing/render/ShaftRenderer.kt → Draws geometry (bodies/tapers/threads/liners)
ui/drawing/compose/ShaftDrawing.kt → Compose wrapper, draws grid + axis labels ONLY
pdf/ShaftPdfComposer.kt            → PDF export uses same Layout + Renderer
```

**Invariants:**
- `ShaftDrawing` draws grid + axis labels, delegates geometry to `ShaftRenderer`
- `ShaftRenderer` draws all geometry + single "Overall" label (no grid duplication)
- `ShaftLayout.compute()` provides `pxPerMm`, `minXMm`, `maxXMm`, `centerlineYPx`
- Scale fits **both axes**: `pxPerMm = min(widthPx/(maxX-minX), heightPx/maxDiameter)`

**Common pitfalls:**
- ❌ Drawing "Overall" label in multiple places → use `ShaftRenderer` only
- ❌ Fixed X=0 at canvas left → must use `left + (0 - minXMm) * pxPerMm` for origin
- ❌ Ignoring vertical fit → scale must accommodate `maxOuterDiaMm()`

### Package Structure (Canonical)
```
com.android.shaftschematic/
├─ MainActivity.kt
├─ data/         ← SettingsStore (DataStore), repositories
├─ model/        ← ShaftSpec, Body, Taper, Threads, Liner (all mm)
├─ pdf/          ← ShaftPdfComposer
├─ ui/
│  ├─ drawing/
│  │  ├─ compose/  ← ShaftDrawing (grid + labels)
│  │  └─ render/   ← ShaftLayout, ShaftRenderer (geometry)
│  ├─ input/       ← Form widgets (NOT ui/components/)
│  ├─ screen/      ← ShaftScreen, AddComponentDialogs
│  └─ viewmodel/   ← ShaftViewModel, factory
└─ util/         ← UnitSystem, parsing helpers
```

**File placement rules:**
- Rendering code: `ui/drawing/render/` (Canvas logic)
- Compose wrappers: `ui/drawing/compose/` (Composables that host Canvas)
- Input widgets: `ui/input/` (not `ui/components/`)
- Match package declaration to folder structure exactly

## State Management

### ViewModel Pattern
- `ShaftViewModel` extends `AndroidViewModel` (needs Application context for DataStore)
- **Always use factory:** `viewModel(factory = ShaftViewModelFactory)`
- State exposed via `StateFlow`; mutate with `.update { it.copy(...) }`
- Commit-on-blur for inputs: no VM mutation while typing, update on Done/focus loss

### Persistence
- User preferences: `SettingsStore` (DataStore) for default unit + grid visibility
- Document state: JSON save/load via SAF (Storage Access Framework)
- Thread pitch normalization: `ShaftSpec.normalized()` populates both `pitchMm` and `tpi` after decode

## UI Input Conventions
1. **Tap-to-clear numeric fields** only when committed value is `0`
2. **No state mutation while typing** (commit on blur/Done)
3. `NumberField.kt` in `ui/input/` implements this (if it exists)

## Type Contracts (Keep in Sync)
```kotlin
// RenderOptions fields
paddingPx: Int              // not Float
textSizePx: Float
gridUseInches: Boolean
showGrid: Boolean

// ShaftLayout.Result (minimum API)
pxPerMm: Float
contentLeftPx, contentTopPx, contentRightPx, contentBottomPx: Float
minXMm, maxXMm: Float
centerlineYPx: Float
spec: ShaftSpec
```

## Build & Dependencies
- **Gradle:** Version catalogs in `gradle/libs.versions.toml`
- **Kotlin:** 2.2.20 with Compose plugin (no manual `composeOptions`)
- **Compose BOM:** 2024.09.00 controls all Compose artifact versions
- **DataStore:** 1.1.1 for preferences persistence
- **Target SDK:** 36, Min SDK: 28

**Common dependency patterns:**
```kotlin
implementation(platform(libs.androidx.compose.bom))
implementation(libs.androidx.compose.material3)
implementation(libs.androidx.datastore.preferences)
```

## Development Workflows

### Adding a New Component Type
1. **Model:** Add data class in `model/` (all fields in mm)
2. **ViewModel:** Add mutation methods (`addX()`, `removeX(id)`), parsing helpers
3. **Renderer:** Draw geometry in `ShaftRenderer.draw()` using `layout.xPx()`, `layout.rPx()`
4. **UI:** Add dialog/form in `screen/AddComponentDialogs.kt`
5. **Validation:** Update `ShaftSpec.validate()` and coverage helpers

### Running the App
- **Build:** Android Studio → Run (or `./gradlew assembleDebug`)
- **Tests:** `./gradlew test` (unit), `./gradlew connectedAndroidTest` (instrumented)
- **Lint:** `./gradlew lint`

### Debugging
- Use Android Studio's Layout Inspector for Compose previews
- Check DataStore values: Device File Explorer → `/data/data/com.android.shaftschematic/files/datastore/`
- PDF issues: Verify `ShaftPdfComposer` mirrors `ShaftDrawing` render path exactly

## Code Style
- **Immutability:** Prefer `data class` + `.copy()`; avoid mutable collections
- **Null safety:** Use `?:`, `?.let {}` instead of `!!`
- **Comments:** File headers describe "What/Inputs/Outputs"; KDoc for public APIs
- **Imports:** Run "Optimize Imports" before committing

## References
- Architecture overview: `CONTRIBUTING.md`
- Package map + file rules: `docs/Codebase Structure & File Rules.md`
- Main README: Feature list + dependencies
