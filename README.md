📐 ShaftSchematic

ShaftSchematic is an Android application for rapidly modeling marine prop-shaft assemblies and exporting clean, dimensioned drawings as PDFs. It supports multi-segment shafts with bodies, tapers, threads, and liners, and includes a live render preview that updates as you edit.

This tool is built for machinists, shipyards, repair techs, and engineering teams that need fast, clear shaft visualizations without CAD overhead.

✨ Current Features
Real-Time Shaft Modeling

Add Bodies, Tapers, Threads, and Liners

Components rendered with:

Bodies & liners as closed rectangles

Tapers as true 4-point polygons

Threads as dimensioned segments

Live preview that masks the centerline underneath occupied spans

Clean Editing Workflow

Incremental component creation (in the order you build the shaft)

Unit switching (mm / inch) with DataStore persistence

Validation nudges when component total length doesn’t match overall length

Delete + Undo (v1)

Tap trash icon → segment is removed instantly

Snackbar with Undo restores it in the correct order and position

Multi-step undo buffer (up to 10 deletes)

PDF Export

One-page, landscape technical drawing

Includes:

Component labels

Major/minor grid

Centerline rules

Dimensioning and callouts

Reliable system-document picker integration (SAF)

Session Tools

Clear All → resets to a clean shaft

Dynamic layout that shows advanced sections only when components exist

📂 Project Structure
app/
└─ com.android.shaftschematic/
├─ MainActivity.kt (single-activity host)
├─ data/
│   ├─ SettingsStore.kt → DataStore persistence
│   └─ ShaftRepository / ShaftFileRepository → JSON I/O
├─ model/
│   ├─ ShaftSpec.kt → root aggregate (mm)
│   ├─ Body, Taper, Threads, Liner → component models
│   └─ Segment.kt → shared interface
├─ pdf/
│   ├─ ShaftPdfComposer.kt → PDF export engine
│   └─ render/, dim/, notes/ → dimension & annotation rendering
├─ ui/
│   ├─ drawing/
│   │   ├─ compose/ShaftDrawing.kt → preview wrapper
│   │   └─ render/ → ShaftLayout, ShaftRenderer, GridRenderer
│   ├─ screen/ → ShaftScreen, AddComponentDialogs
│   ├─ input/ → ShaftMetaSection, NumberField
│   ├─ viewmodel/ → ShaftViewModel, factory
│   └─ nav/ → AppNav, routing
├─ util/
│   ├─ UnitSystem.kt → mm/inch conversions
│   └─ Parsing.kt, TaperParser.kt → input parsing
└─ settings/ → PdfPrefs configuration

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

Set the overall shaft length

Press the ➕ Add Component FAB

Add bodies, tapers, threads, or liners in physical build order

Switch units anytime (top bar dropdown)

Export to PDF from the top-right icon

Use ⋮ → Clear All to reset the layout

Components are always sorted by their starting X-position, matching machining logic.

🧠 Persistence

Unit preference persists via DataStore (default unit + grid visibility)

Document state: JSON save/load via Storage Access Framework (SAF)

Versioned JSON envelope preserves unit preference and lock state per-document

Thread pitch normalization: auto-populates both `pitchMm` and `tpi` when either is present

🛠️ Roadmap
Active

Component highlighting in preview (tap-to-select)

Better precision input (fractions, 4–6 decimals)

Liner dimension rendering improvements

Next Sprints

Inline "+ Add here" between segments

Tap-to-edit directly from the preview

Autosave/drafts system

PDF dimension clarity improvements

Overlap detection and warnings

Web/Multi-platform port (concept phase)

📄 License

Pending — private/closed until final licensing decision.

📜 Changelog

See CHANGELOG.md for version history.
Latest entries include rendering updates, UI cleanups, validation improvements, and the new Delete/Undo system.