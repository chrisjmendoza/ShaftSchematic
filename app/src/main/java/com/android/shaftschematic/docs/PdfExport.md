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
| `curveLoHeightIn` | `0.5` | Sizing-curve anchor: drawn height (paper in) of a 4" shaft at 100% (0.25–1.5) |
| `curveHiHeightIn` | `1.0` | Sizing-curve anchor: drawn height (paper in) of an 8" shaft at 100% (0.25–1.5) |

All fields are also reachable in the preview screen's Tune sheet, except the sizing-curve
anchors, which live in Settings → PDF Export → "Default drawing size" (app-level default;
the per-job "Shaft height" slider multiplies on top).

---

## PdfPreviewScreen

Full-resolution in-memory preview via `PdfDocument` + `PdfRenderer` (2× raster),
pinch-to-zoom 0.5×–8×, double-tap reset.

- **Options sheet (Tune icon):** blank draft (write-in) toggle, component labels, line
  thickness (50–200%), "Shaft height" slider, liner compression control, measurement
  reference (Auto/AFT/FWD), shade bodies/tapers/liners — bound to `PdfPrefs` (or, for the
  height/liner-compression pair, the per-job `RunoutConfig`) via VM setters, persisted,
  applied live (each option is a `LaunchedEffect` key). Blank draft is session-scoped, not
  persisted.
  - The sheet's content is taller than a phone screen, so it carries its own
    `verticalScroll` plus `navigationBarsPadding()` — without them the bottom rows clip
    mid-checkbox behind the navigation bar. Its height is capped at **78% of the screen**
    (`LocalConfiguration.screenHeightDp * 0.78f`): a sheet expanded to the status bar
    leaves no edge to swipe it back down by (on-device report).
- **Orientation:** `DisposableEffect` unlocks rotation on entry and restores the
  portrait lock on dispose — every other screen stays portrait-only.
- **Pipeline:** snapshot `vm.currentPdfPrefs` on main thread → `Dispatchers.IO` →
  temp PDF via `composeShaftPdf` → rasterize page 0 at 2× → pan/zoom Canvas.
  Temp file deleted after rasterization; failures show an error, never crash.
- **Top bar:** Back · Tune · Refresh (reset zoom/pan) · Print (system print of a freshly
  composed page, state snapshotted on the UI thread) · PDF (`onExport()` → SAF).

## Invariants
- No model state mutated in either screen — rendering and preference changes only.
