# PDF Export & Preview (routes/screens)

**Files:** `ui/nav/PdfExportRoute.kt`, `ui/screen/PdfPreviewScreen.kt`  
**Version:** v1.0 (2026-07-18) — consolidates the former `PdfExportRoute.md` and
`PdfPreviewScreen.md`. For the composer/drawing pipeline itself see
`docs/PDF_EXPORT.md` at repo level.

---

## PdfExportRoute

Exports the current shaft to a **single-page PDF** (US Letter landscape, 792×612 pt)
via SAF, delegating drawing to `composeShaftPdf`.

- Launch system "Create Document" (no storage permissions); guard against multiple
  launches during recomposition; `onFinished()` after success or cancel.
- **Units:** model is canonical mm; labels/formatting handled by the composer.
- **Storage:** JSON stays internal; PDFs export via SAF to user-chosen locations.
- Version string via `PackageManager` (no BuildConfig dependency).
- IO guarded by `runCatching`; streams closed in `finally`.

### PdfPrefs — appearance knobs passed to every export

| Field | Default | Effect |
|---|---|---|
| `showComponentTitles` | `true` | Draw component label rows below the shaft |
| `tieringMode` | `AUTO` | Which end dimensions are anchored to (AFT / FWD / Auto) |
| `shadedBodies` | `false` | Fill body sections with light grey |
| `shadedTapers` | `false` | Fill taper trapezoids with light grey |
| `shadedLiners` | `false` | Fill liner sections with light grey |
| `oalSpacingFactor` | `2.5` | Extra gap above OAL rail (1.0–6.0) |

All fields are also reachable in the preview screen's Tune sheet.

---

## PdfPreviewScreen

Full-resolution in-memory preview via `PdfDocument` + `PdfRenderer` (2× raster),
pinch-to-zoom 0.5×–8×, double-tap reset.

- **Options sheet (Tune icon):** component labels, line thickness (50–200%),
  measurement reference (Auto/AFT/FWD), shade bodies/tapers/liners — all bound to
  `PdfPrefs` via VM setters, persisted to DataStore, applied live (each option is a
  `LaunchedEffect` key).
- **Orientation:** `DisposableEffect` unlocks rotation on entry and restores the
  portrait lock on dispose — every other screen stays portrait-only.
- **Pipeline:** snapshot `vm.currentPdfPrefs` on main thread → `Dispatchers.IO` →
  temp PDF via `composeShaftPdf` → rasterize page 0 at 2× → pan/zoom Canvas.
  Temp file deleted after rasterization; failures show an error, never crash.
- **Top bar:** Back · Tune · Refresh (reset zoom/pan) · PDF (`onExport()` → SAF).

## Invariants
- No model state mutated in either screen — rendering and preference changes only.
