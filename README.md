📐 ShaftSchematic

ShaftSchematic is an Android application for rapidly modeling marine prop-shaft assemblies and exporting clean, dimensioned drawings as PDFs. It supports multi-segment shafts with bodies, tapers, threads, liners, and coupler bolt slots.

This tool is built for machinists, shipyards, repair techs, and engineering teams that need fast, clear shaft visualizations without CAD overhead.

✨ Current Features

Real-Time Shaft Modeling

- Bodies (with keyways), tapers (with keyways and auto taper-rate calculation), threads (with OAL include/exclude), liners, and coupler bolt slots (reference-only cutouts). Keyways can be clocked 180° apart — the far-side one renders as hidden dashed lines
- Explicit bodies are first-class, non-negotiable components (collide, never split); derived auto-bodies stay fluid and fill unoccupied spans
- Resolved-component pipeline: auto-bodies fill unoccupied spans in the preview without being persisted
- Live preview with grid, centerline, and component labels; tap-to-add at position
- Preview colors configurable via Settings (presets + Custom theme palette), Black/White Only drafting mode
- Line thickness control (50%–200%, persisted, affects preview + PDF)

Editing Workflow

- Component carousel with edit cards; Add dialogs mirror the carousel cards control-for-control
- Unit switching (mm / inch) at the UI edge only — the model is always canonical millimeters
- Validation: blocking errors (dialogs, badges, export gate — including any overlap of an explicit body, and overlaps among sacred components) and non-blocking warnings (free-to-end badge)
- Delete with multi-step Undo; undo/redo history menu

Documents

- Shaft drawing: one-page landscape technical PDF with dimension tiers, callouts, grid, and title block
- Runout sheet: inline shaft preview with collision-free alternating runout bubbles and TIR label
- Wear document: shaft profile with PASS/FAIL dye-pen checkboxes and field notes
- All three reachable from the editor sidebar (Schematic / Runout / Wear tabs)

Persistence & Data Safety

- Internal `.shaft` library (JSON, versioned envelope with migrations) plus SAF open/export
- Autosave / draft restore on launch; Start screen with recent documents
- Backup & restore: ZIP backup/restore via file picker, per-shaft import/export, pre-update snapshots, Android Auto Backup rules

Misc

- Settings screen (units, grid, preview colors, PDF prefs, line thickness), Developer Options, Achievements screen, Project-Info sheet
- Portrait-locked UI (landscape is currently disabled)

📂 Project Structure
```
app/
└─ com.android.shaftschematic/
   ├─ MainActivity.kt (single-activity host)
   ├─ model/     → ShaftSpec (root aggregate, mm), Body, Taper, Threads, Liner,
   │              CouplerBoltSlot, ProjectInfo, migrations
   ├─ geom/      → pure geometry: OAL computations, SET positions,
   │              dimension-tier assignment, runout bubble layout
   ├─ doc/       → ShaftDocCodec (JSON serialization + format migrations)
   ├─ io/        → InternalStorage (app-private .shaft library), ShaftBackup
   ├─ data/      → SettingsStore (DataStore), AutosaveManager
   ├─ pdf/       → ShaftPdfComposer, RunoutPdfComposer, WearPdfComposer
   │              + dim/, notes/, render/ (dimension & annotation rendering)
   ├─ settings/  → PdfPrefs, RunoutConfig
   ├─ ui/
   │   ├─ drawing/   → compose/ShaftDrawing (preview host),
   │   │              render/ (ShaftLayout, ShaftRenderer, GridRenderer)
   │   ├─ screen/    → StartScreen, ShaftEditorRoute (sidebar + tabs), ShaftScreen,
   │   │              ComponentCarousel, AddComponentDialogs, Runout/Wear/Settings routes
   │   ├─ input/     → NumericInputField (commit-on-blur numeric entry)
   │   ├─ resolved/  → ResolvedComponent (derived auto-body pipeline)
   │   ├─ order/     → ComponentOrder (component identity/ordering layer)
   │   ├─ viewmodel/ → ShaftViewModel, factory, snap utils
   │   ├─ nav/       → AppNav, PDF export routes
   │   └─ dialog/, config/, util/, theme/
   ├─ util/     → UnitSystem, parsing, taper rate auto-calc, naming/titles
   └─ docs/     → in-source contract docs (see below)
```

📚 Documentation

- `CLAUDE.md` — project conventions and critical invariants
- `app/src/main/java/com/android/shaftschematic/docs/` — per-subsystem contract docs (read the relevant one before editing a subsystem; `README.md` there is the index)
- `docs/` — repo-level reference docs (architecture, data model, validation rules, PDF export), proposals, and archived analyses

🔧 Requirements

Android Studio Koala or newer

Kotlin 2.2.20 (with Compose compiler plugin)

Jetpack Compose (Material3) via BOM 2024.09.00

DataStore Preferences 1.1.1

Coroutines 1.8+

Min SDK 28, Target SDK 36

Dependencies (gradle/libs.versions.toml):

```toml
[versions]
kotlin = "2.2.20"
composeBom = "2024.09.00"
datastore = "1.1.1"

[libraries]
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }
```

🚀 Build & Run

Clone repository

Open in Android Studio

Let Gradle sync

Run on a device or emulator

📘 Usage Guide

1. Start screen: create a New Drawing, Open a saved shaft, or Continue Draft
2. Set the overall shaft length (manual, or auto from components)
3. Add bodies, tapers, threads, liners, or coupler bolt slots via + Add Component
4. Edit any component in the carousel; switch units anytime
5. Use the sidebar to switch between Schematic, Runout, and Wear tabs
6. Export the current document to PDF from the top bar (SAF picker)
7. Back up or restore your shaft library from Settings → Data

🛠️ Roadmap

See docs/ROADMAP.md for the release-series roadmap and TODO.md for the active development queue.

📄 License

Pending — private/closed until final licensing decision.

📜 Changelog

See CHANGELOG.md for version history.
