# ShaftSchematic

ShaftSchematic is an Android app for quickly modeling and exporting a dimensioned shaft drawing. It supports variable body segments, tapers, threads, and liners, with a live preview and PDF export.

## Features

- **Live preview** of the shaft:
  - Bodies & liners as full rectangles
  - Tapers as closed polygons
  - Centerline only in *gaps* (masked under components)
- **Unit switching** (mm / in) with DataStore-backed persistence (UnitSystem)
- **Incremental modeling**: add bodies, tapers, threads, liners in the order you build the shaft
- **Validation nudges**: optional hints if total component length doesn’t match the overall length
- **Export to PDF** from the top bar
- **Clear all** to start fresh

## Screens & Structure

app/
└─ com.android.shaftschematic
├─ MainActivity.kt
├─ data/
│ ├─ ShaftSpecMm.kt
│ └─ (BodySegmentSpec, TaperSpec, ThreadSpec, LinerSpec, etc.)
├─ pdf/
│ └─ ShaftPdfComposer.kt
├─ ui/
│ ├─ screen/
│ │ └─ ShaftScreen.kt
│ └─ drawing/
│ └─ compose/
│ └─ ShaftDrawing.kt
├─ ui/viewmodel/
│ ├─ ShaftViewModel.kt
│ └─ ShaftViewModelFactory.kt (no-arg)
└─ util/
├─ UnitSystem.kt
└─ UnitsStore.kt (DataStore Preferences)

markdown
Copy code

## Requirements

- Android Studio Giraffe/Koala+ (AGP 8.x)
- Kotlin 1.9+
- Jetpack Compose BOM (Material3)
- DataStore Preferences `1.1.7`
- Coroutines `1.8.x` or newer

```gradle
dependencies {
  implementation(platform("androidx.compose:compose-bom:<latest>"))
  implementation("androidx.compose.material3:material3")
  implementation("androidx.datastore:datastore-preferences:1.1.7")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
Build & Run
Open the project in Android Studio.

Sync Gradle.

Run on a device/emulator (Android 8+ recommended).

Usage
Start with Overall Length.

Use the ➕ FAB to add Body, Taper, Thread, or Liner components.

Units can be switched from the top bar (mm/in).

Export PDF from the top bar.

Use ⋮ → Clear all to reset to a fresh shaft (default overall length).

Tip: Components are inserted in the order you add them—matching how you build the shaft.

PDF Export
Current implementation exports a single-page landscape PDF with a clear title block (see ShaftPdfComposer).

Files are created via the system document picker (SAF) or app-scoped files depending on the code path you use.

Persistence
Units selection is saved via DataStore (see UnitsStore).

TODO: Persist full shaft specs (on roadmap).

Contributing
Use conventional commits or detailed messages (recommended for solo dev tracking).

Keep CHANGELOG.md fresh (see below).

License
Private/internal for now. Add a license here when ready.

yaml
Copy code

---

# CHANGELOG.md (append)

```markdown
# Changelog
All notable changes to this project will be documented in this file.

## [0.3.0] - 2025-09-16
### Added
- **Preview drawing** now renders:
  - Bodies & liners as full rectangles (all 4 edges)
  - Tapers as closed 4-sided polygons
  - Dashed centerline is masked under components and shown only in gaps
- **Clear all** action in top bar overflow
- **No-arg ViewModelFactory** scaffold (future DI-ready)

### Changed
- **ShaftScreen** simplified to the preferred flow:
  - Initial view shows Overall Length, with dynamic sections only when components exist
  - Units dropdown and PDF export in top bar
  - Numeric field input handling (correct KeyboardOptions)
- **UnitsStore** now uses `UnitSystem` and DataStore Preferences `1.1.7`
- Compose modernization: `HorizontalDivider`, `menuAnchor` API, cleaner imports

### Fixed
- Unresolved references from older API usages (`KeyboardOptions`, deprecated calls)
- Crashes from duplicate LazyColumn keys and inconsistent state delegates
- Incorrect centerline rendering under occupied spans

### Notes
- PDF composer unchanged functionally in this release; geometry cleanup queued for next iteration.
- Grid toggle will return when preview `RenderOptions` integration is reintroduced.

## [0.2.0] - 2025-09-15
- (Previous milestone: dynamic lists, basic PDF export, menu cleanups, etc.)
