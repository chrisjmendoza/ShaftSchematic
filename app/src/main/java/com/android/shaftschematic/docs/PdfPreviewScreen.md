# PdfPreviewScreen

**File:** `app/src/main/java/com/android/shaftschematic/ui/screen/PdfPreviewScreen.kt`

---

## Responsibilities

- Render a full-resolution in-memory preview of the shaft PDF via `PdfDocument` + `PdfRenderer` (2× raster scale).
- Support pinch-to-zoom (0.5×–8×) and double-tap-to-reset for inspecting dimension labels before export.
- Expose a quick-access PDF options sheet (Tune icon) so users can tweak appearance without navigating to Settings.
- Unlock landscape rotation while active; restore portrait on leave.

---

## PDF Options Sheet

Opened by the **Tune** icon in the top bar. Changes take effect immediately — each option is in the `LaunchedEffect` key so the preview re-renders automatically on every toggle.

| Control | Bound to |
|---|---|
| Component labels (Switch) | `PdfPrefs.showComponentTitles` via `vm.setPdfShowComponentTitles()` |
| Line thickness (Slider 50–200%) | `lineThicknessScale` via `vm.setLineThicknessScale()` |
| Measurement reference (Radio: Auto / AFT / FWD) | `PdfPrefs.tieringMode` via `vm.setPdfTieringMode()` |
| Shade Bodies (Checkbox) | `PdfPrefs.shadedBodies` via `vm.setPdfShadedBodies()` |
| Shade Tapers (Checkbox) | `PdfPrefs.shadedTapers` via `vm.setPdfShadedTapers()` |
| Shade Liners (Checkbox) | `PdfPrefs.shadedLiners` via `vm.setPdfShadedLiners()` |

All values are persisted to DataStore through the ViewModel; the sheet reads live StateFlows so it always reflects current state.

---

## Orientation

`DisposableEffect` sets `requestedOrientation = SCREEN_ORIENTATION_UNSPECIFIED` on entry (overriding the manifest's portrait lock) and restores `SCREEN_ORIENTATION_PORTRAIT` on dispose. Every other screen remains portrait-only.

The PDF page is US Letter landscape (792 × 612 pt), so rotating the device to landscape lets the full page fill the screen at zoom = 1.

---

## Rendering Pipeline

1. `LaunchedEffect` fires on spec, project, options, or any `PdfPrefs` change.
2. Snapshots `vm.currentPdfPrefs` synchronously on the main thread (SettingsStore's in-memory mirror is already updated by VM setters before any coroutine launches).
3. Switches to `Dispatchers.IO`, writes a temp PDF via `composeShaftPdf`, then rasterises page 0 at 2× via `PdfRenderer`.
4. Bitmap is displayed in a Compose Canvas with pan/zoom transform applied.

---

## Contracts & Invariants

- No model state is mutated here — pure rendering and user preference changes.
- Rendering is guarded by `runCatching`; failures show an error message (no crash).
- The temp PDF file is deleted after rasterisation.
- Landscape unlock is scoped to this composable via `DisposableEffect` — it is automatically restored even if the user navigates back mid-render.

---

## Top Bar Actions (left → right)

| Icon | Action |
|---|---|
| Back arrow | `onBack()` — pop backstack |
| Tune | Open PDF options sheet |
| Refresh | Animate zoom + pan back to 1× / zero |
| PDF | `onExport()` — navigate to SAF file picker |
