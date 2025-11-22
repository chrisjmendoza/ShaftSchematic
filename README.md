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

Single-step undo buffer (multi-step undo coming later)

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
├─ MainActivity.kt
├─ data/
│   ├─ model segments: Body, Taper, Threads, Liner, ShaftSpecMm
│   └─ Segment geometry helpers
├─ pdf/
│   └─ ShaftPdfComposer.kt
├─ ui/
│   ├─ screen/ → Editor UI (ShaftScreen)
│   ├─ drawing/ → Canvas rendering engine (ShaftDrawing)
│   └─ compose/ → UI components (cards, dialogs, inputs)
├─ ui/viewmodel/
│   ├─ ShaftViewModel.kt
│   └─ ShaftViewModelFactory.kt
└─ util/
├─ UnitSystem.kt
└─ UnitsStore.kt

🔧 Requirements

Android Studio Koala or newer

Kotlin 1.9+

Jetpack Compose (Material3) via BOM

DataStore Preferences 1.1.7

Coroutines 1.8+

Android 8.0+ recommended

Dependencies (simplified):

dependencies {
implementation(platform("androidx.compose:compose-bom:<latest>"))
implementation("androidx.compose.material3:material3")
implementation("androidx.datastore:datastore-preferences:1.1.7")
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}

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

Units persist via DataStore

Full shaft autosave is planned for an upcoming sprint

“Save” / “Save As” project files will be introduced with versioned file formats

🛠️ Roadmap
Active

Delete + Undo (v1) — in progress

Better precision input (fractions, 4–6 decimals)

Next Sprints

Inline “+ Add here” between segments

Tap-to-edit directly from the preview

File save system (autosave/drafts/Save As)

PDF dimension clarity improvements

Web/Multi-platform port (concept phase)

📄 License

Pending — private/closed until final licensing decision.

📜 Changelog

See CHANGELOG.md for version history.
Latest entries include rendering updates, UI cleanups, validation improvements, and the new Delete/Undo system.