📐 ShaftSchematic

ShaftSchematic is an Android application for rapidly modeling marine prop-shaft assemblies and exporting clean, dimensioned drawings as PDFs. It supports multi-segment shafts with bodies, tapers, threads, liners, and coupler bolt slots.

This tool is built for machinists, shipyards, repair techs, and engineering teams that need fast, clear shaft visualizations without CAD overhead.

✨ Current Features

Real-Time Shaft Modeling

- Bodies (with keyways), tapers (with keyways and auto taper-rate calculation), threads (with OAL include/exclude), liners, and coupler bolt slots (reference-only cutouts). Keyways can be clocked 180° apart (far-side one renders as hidden dashed lines) or 90° apart with a CW/CCW direction (renders as an edge notch); the two are mutually exclusive
- Bodies are the fluid base material: plain bodies split around tapers/threads/liners, keyed bodies stay whole; derived auto-bodies fill unoccupied spans. Bodies never participate in collision detection
- Resolved-component pipeline: auto-bodies fill unoccupied spans in the preview without being persisted
- Live preview with grid, centerline, and component labels; tap-to-add at position
- Preview colors configurable via Settings (presets + Custom theme palette), Black/White Only drafting mode
- Line thickness control (50%–200%, persisted, affects preview + PDF)

Editing Workflow

- Component carousel with edit cards; Add dialogs mirror the carousel cards control-for-control
- Unit switching (mm / inch) at the UI edge only — the model is always canonical millimeters
- Validation: blocking errors (dialogs, badges, export gate) and non-blocking warnings (overlaps among sacred components, free-to-end badge). Bodies are fillers and never collide
- Delete with multi-step Undo; undo/redo history menu

Documents

- Shaft drawing: one-page landscape technical PDF with dimension tiers, Ø callouts, and title block
- Runout sheet: inline shaft preview with collision-free alternating runout bubbles and TIR label; tap a bubble to record its TIR value + high-spot marker
- Wear document: shaft profile + per-liner detail strips, tap-to-record wear bands, pit "X" markers, and measured-Ø readings (value callouts with leaders); PASS/FAIL dye-pen checkboxes and field notes; blank write-in variants
- Undercut drawing: machined-below-surface cuts as open silhouette steps with liner-anchored detail strips, a per-sheet cut-depth exaggeration slider, and user-selectable shading / line-art styles
- Consolidated output: one sheet carrying the schematic's rails and footer plus the elected runout and wear information (All three / Schematic + Runout / Schematic + Wear), worn-section values printed inside the profile, a per-job "Shaft height" + liner-compression control, and "Export all" to batch-write the checked documents into one picked folder
- All five reachable from the editor sidebar (Schematic / Runout Sheet / Wear Document / Undercut Drawing / Consolidated Output tabs)

Persistence & Data Safety

- Internal `.shaft` library (JSON, versioned envelope with migrations) plus SAF open/export
- Autosave / draft restore on launch; Start screen with recent documents
- Backup & restore: ZIP backup/restore via file picker, per-shaft import/export, pre-update snapshots, Android Auto Backup rules

Misc

- Settings screen — units, grid, preview colors, line thickness, Appearance (System/Light/Dark + high contrast; the white paper sheets keep fixed ink either way), undercut drawing styles, and PDF Export prefs including "Default drawing size" (the proportional 4" → 0.5" / 8" → 1" sizing curve anchors) and "Body S-break" (how far a body may compress before it prints the break symbol); plus Data backup/restore
- Help & FAQ screen (how-to guides, a Settings reference covering every field and option, FAQ), Developer Options, Achievements screen, Project-Info sheet
- Portrait-locked UI (landscape is currently disabled)

📂 Project Structure
```
app/
└─ com.android.shaftschematic/
   ├─ MainActivity.kt (single-activity host)
   ├─ model/     → ShaftSpec (root aggregate, mm), Body, Taper, Threads, Liner,
   │              CouplerBoltSlot, Undercut, WearSpot, WornSection, RunoutReading,
   │              ProjectInfo, migrations
   ├─ geom/      → pure geometry: OAL computations, SET positions,
   │              dimension-tier assignment, runout bubble layout,
   │              profile compression, undercut/surface + worn-section math
   ├─ doc/       → ShaftDocCodec (JSON serialization + format migrations)
   ├─ io/        → InternalStorage (app-private .shaft library), ShaftBackup
   ├─ data/      → SettingsStore (DataStore), AutosaveManager
   ├─ pdf/       → ShaftPdfComposer, RunoutPdfComposer, WearPdfComposer,
   │              UndercutPdfComposer
   │              + dim/, notes/, render/ (dimension & annotation rendering)
   ├─ settings/  → PdfPrefs, RunoutConfig, AppearancePrefs
   ├─ ui/
   │   ├─ drawing/   → compose/ShaftDrawing (preview host),
   │   │              render/ (ShaftLayout, ShaftRenderer, GridRenderer)
   │   ├─ screen/    → StartScreen, ShaftEditorRoute (sidebar + tabs), ShaftScreen,
   │   │              ComponentCarousel, AddComponentDialogs,
   │   │              Runout/Wear/Undercut/Output/Settings/Help routes
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
5. Use the sidebar to switch between the Schematic, Runout Sheet, Wear Document, Undercut Drawing, and Consolidated Output tabs
6. Export the current document to PDF from the top bar (SAF picker)
7. Back up or restore your shaft library from Settings → Data

🛠️ Roadmap

See docs/ROADMAP.md for the release-series roadmap and TODO.md for the active development queue.

📄 License

Pending — private/closed until final licensing decision.

📜 Changelog

See CHANGELOG.md for version history.
