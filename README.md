📐 ShaftSchematic

ShaftSchematic is an Android application for rapidly modeling marine prop-shaft assemblies and exporting clean, dimensioned drawings as PDFs. It supports multi-segment shafts with bodies, tapers, threads, liners, and coupler bolt slots.

This tool is built for machinists, shipyards, repair techs, and engineering teams that need fast, clear shaft visualizations without CAD overhead.

✨ Current Features

Real-Time Shaft Modeling

- Bodies (with keyways), tapers (with keyways and auto taper-rate calculation), threads (with OAL include/exclude), liners, and coupler bolt slots (reference-only cutouts). Keyways can be clocked 180° apart (far-side one renders as hidden dashed lines) or 90° apart with a CW/CCW direction (renders as an edge notch); the two are mutually exclusive
- Bodies are the fluid base material: plain bodies split around tapers/threads/liners, keyed bodies stay whole; derived auto-bodies fill unoccupied spans. Bodies never participate in collision detection
- Resolved-component pipeline: auto-bodies fill unoccupied spans in the preview without being persisted; each auto span can carry its own bare-shaft Ø
- Live preview with grid, centerline, and component labels; tap-to-add at position
- Preview colors configurable via Settings (presets + Custom theme palette), Black/White Only drafting mode
- Line thickness control (50%–200%, persisted, affects preview + PDF)

Editing Workflow

- Component carousel with edit cards; Add dialogs mirror the carousel cards control-for-control
- Templates: save the current drawing as a template, then start from it in a browser that buckets by liner size and count (geometry only — job metadata is scrubbed on write)
- Unit switching (mm / inch) at the UI edge only — the model is always canonical millimeters
- Validation: blocking errors (dialogs, badges, export gate) and non-blocking warnings (overlaps among sacred components). Bodies are fillers and never collide
- Delete with multi-step Undo; undo/redo history menu
- Per-component "Show Ø on drawing" toggle — keep a measured diameter off the part of the schematic where it could not have been measured

Documents

- Shaft drawing: one-page landscape technical PDF with dimension tiers, Ø callouts, and title block. Dimension values seat in a break in the line, and fractions are set as real stacked or diagonal fractions (Settings → Drawing → Fractions)
- Runout sheet: inline shaft preview with collision-free runout bubbles — one station per 20" of component length, overridable per component — straight leaders that aim at their own station, tap-to-record TIR value + high-spot clock marker, and an optional coupling end view with its pilot reading
- Wear document: shaft profile + detail strips for liners and elected tapers/bodies, tap-to-record wear bands, pit "X" markers, measured-Ø readings (value callouts with leaders) and a worn-profile trace that dips through them; PASS/FAIL dye-pen checkboxes and field notes; blank write-in variants
- Undercut drawing: machined-below-surface cuts as open silhouette steps with liner-anchored detail strips, a per-sheet cut-depth exaggeration slider, and user-selectable shading / line-art styles
- Consolidated output: one sheet carrying the schematic's rails and footer plus the elected runout and wear information (All three / Schematic + Runout / Schematic + Wear), worn-section values printed inside the profile, a per-job "Shaft height" + liner-compression control, and "Export all" to batch-write the checked documents into one picked folder
- Paper sizing follows the hand-sheet convention: drawn height comes from true diameter on a proportional sizing curve, long runs foreshorten above per-kind width floors, and a body compressed past your chosen threshold prints the S-break symbol
- Live tuning: drag Line thickness, Body S-break, Shaft height or Liner compression with a preview open and the page re-renders under your finger — the sheet shows as a fit-width page strip so the control never covers what it is changing
- Every document previews, prints directly, or exports through the file picker; each also has a blank write-in variant for hand-marking on the job
- All five reachable from the editor sidebar (Schematic / Runout Sheet / Wear Document / Undercut Drawing / Consolidated Output tabs)

Persistence & Data Safety

- Internal `.shaft` library (JSON, versioned envelope with migrations) plus SAF open/export
- Autosave into a 3-entry draft ring keyed per document; Start screen lists recent documents and unsaved drafts
- Backup & restore: ZIP backup/restore via file picker, per-shaft import/export, pre-update snapshots, Android Auto Backup rules

Misc

- Settings screen — units, grid, preview colors, line thickness, Appearance (System/Light/Dark + high contrast; the white paper sheets keep fixed ink either way), undercut drawing styles, and a Drawing section with "Default drawing size" (the proportional 4" → 0.5" / 8" → 1" sizing-curve anchors), "Body S-break" (how far a body may compress before it prints the break symbol), "Dimension arrows" (Small/Medium/Large arrowheads), "Fractions" (Stacked/Diagonal/Plain) and "Wear depth exaggeration"; plus PDF Export prefs and Data backup/restore
- Help & FAQ screen (how-to guides, a Settings reference covering every field and option, FAQ), Developer Options, Achievements screen, Project-Info sheet
- Portrait-locked UI, except the PDF preview screens, which unlock rotation so a landscape sheet can fill the display

📂 Project Structure
```
app/
└─ com.android.shaftschematic/
   ├─ MainActivity.kt (single-activity host)
   ├─ model/     → ShaftSpec (root aggregate, mm), Body, Taper, Threads, Liner,
   │              CouplerBoltSlot, Undercut, WearSpot, WornSection, RunoutReading,
   │              ProjectInfo, migrations
   ├─ geom/      → pure geometry: OAL computations, SET positions, dimension-tier
   │              assignment, runout bubble + dimension-rail layout, profile
   │              compression, undercut/surface, wear trace/pit, keyway spoon,
   │              worn-section math
   ├─ doc/       → ShaftDocCodec (JSON serialization + format migrations)
   ├─ io/        → InternalStorage (app-private .shaft library), ShaftBackup, templates
   ├─ data/      → SettingsStore (DataStore), AutosaveManager, DraftRing
   ├─ pdf/       → ShaftPdfComposer, RunoutPdfComposer, WearPdfComposer,
   │              UndercutPdfComposer, wear/undercut strip layouts, BreakSymbol
   │              + dim/, notes/, render/ (dimension & annotation rendering)
   ├─ settings/  → PdfPrefs, RunoutConfig, AppearancePrefs
   ├─ ui/
   │   ├─ drawing/   → compose/ShaftDrawing (preview host),
   │   │              render/ (ShaftLayout, ShaftRenderer, GridRenderer)
   │   ├─ screen/    → StartScreen, ShaftEditorRoute (sidebar + tabs), ShaftScreen,
   │   │              ComponentCarousel, AddComponentDialogs, TemplatesRoute,
   │   │              Runout/Wear/Undercut/Output/Settings/Help routes
   │   ├─ input/     → NumericInputField (commit-on-blur numeric entry)
   │   ├─ resolved/  → ResolvedComponent (derived auto-body pipeline), runout spans
   │   ├─ viewmodel/ → ShaftViewModel, factory, snap utils, session history
   │   ├─ nav/       → AppNav, PDF export routes
   │   └─ dialog/, config/, util/, theme/
   └─ util/      → UnitSystem, parsing, taper rate auto-calc, fraction typography,
                   PDF raster + SAF export helpers, naming/titles
docs/            → all project documentation (see below)
```

📚 Documentation

Everything lives under `docs/` — one root, three layers:

- `CLAUDE.md` (repo root) — project conventions and the critical invariants that cross subsystems
- [docs/contracts/](docs/contracts/) — per-subsystem contracts; read the relevant one before editing a subsystem. [INDEX.md](docs/contracts/INDEX.md) maps them by area
- [docs/](docs/) — repo-level references: architecture, data model, validation rules, PDF export spec, glossary, style guide, roadmap, plus plans for work not yet built
- [docs/archive/](docs/archive/) — resolved analyses, shipped design plans, and closed incident reports. Nothing there describes current behavior; each file says so at the top and points at its living contract

🔧 Requirements

Android Studio recent enough for the Android Gradle Plugin pinned in `gradle/libs.versions.toml`

Kotlin 2.2.20 (with Compose compiler plugin)

Jetpack Compose (Material3) via BOM 2024.09.00

DataStore Preferences 1.1.1, kotlinx.serialization 1.9.0

Robolectric 4.16 — Compose UI tests run on the JVM, no device needed

Min SDK 28, Target SDK 36

Dependencies (gradle/libs.versions.toml):

```toml
[versions]
agp = "9.3.1"
kotlin = "2.2.20"
composeBom = "2024.09.00"
kotlinx-serialization = "1.9.0"
datastore = "1.1.1"
robolectric = "4.16"
```

🚀 Build & Run

Clone repository

Open in Android Studio

Let Gradle sync

Run on a device or emulator

`./gradlew testDebugUnitTest` runs the suite — CI gates on it, so a red suite distributes nothing

📘 Usage Guide

1. Start screen: create a New Drawing, start from a Template, Open a saved shaft, or Continue Draft
2. Set the overall shaft length (manual, or auto from components)
3. Add bodies, tapers, threads, liners, or coupler bolt slots via + Add Component
4. Edit any component in the carousel; switch units anytime
5. Use the sidebar to switch between the Schematic, Runout Sheet, Wear Document, Undercut Drawing, and Consolidated Output tabs
6. Record inspection data where it belongs — TIR values and high spots on the Runout tab; wear bands, pits and measured Ø on the Wear tab; cuts on the Undercut tab
7. Preview, print, or export the current document to PDF from the top bar (SAF picker); the Consolidated tab can export every checked document at once
8. Back up or restore your shaft library from Settings → Data

🛠️ Roadmap

See [docs/ROADMAP.md](docs/ROADMAP.md) for the release-series roadmap and [TODO.md](TODO.md) for the active development queue.

📄 License

Licensed under the [MIT License](LICENSE) — Copyright © 2026 Chris Mendoza.

You may use, copy, modify, and distribute this source code, including in commercial
projects. ShaftSchematic is used as a worked example in instructional material, and
students and readers are explicitly welcome to build on it — no separate permission
needed.

Two scope notes, spelled out at the bottom of [LICENSE](LICENSE): the reference
photographs in the top-level `assets/` directory are **not** covered by the license and
must not be redistributed (the app's own bundled assets under `app/src/main/assets/`
are covered), and the license grants no rights to the "ShaftSchematic" name or icon, so
forks should ship under their own branding. Third-party components (Android SDK,
AndroidX, Jetpack Compose) remain under their own licenses.

ShaftSchematic is a drafting aid — it does not verify or certify any design, and all
dimensions remain the responsibility of the engineer or machinist who reviews them.

📜 Changelog

See CHANGELOG.md for version history.
