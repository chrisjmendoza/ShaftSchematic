# PdfExportRoute

**File:** `app/src/main/java/com/android/shaftschematic/ui/nav/PdfExportRoute.kt`  
**Purpose:** Export the current shaft to a **single-page PDF** (Letter, landscape) via Android’s Storage Access Framework (SAF), delegating drawing to `composeShaftPdf`.

---

## Responsibilities
- Launch system “Create Document” (no storage permissions).
- Build a `PdfDocument`, create a 792×612 pt page, call `composeShaftPdf`, and write to the chosen URI.
- Guard against multiple launches during recomposition.
- Notify caller with `onFinished()` after success or cancel.

## Contracts & Invariants
- **Units:** Model is canonical **mm**. Unit labels/formatting handled by the composer.
- **Page:** U.S. Letter landscape (792×612 pt). Future: Settings for A4, portrait, multi-page.
- **Storage:** JSON remains internal; **PDFs are exported via SAF** to user-chosen locations.
- **Version string:** Retrieved via `PackageManager` (no BuildConfig dependency).

## Inputs
- `ShaftViewModel.spec` (canonical mm).
- `ShaftViewModel.unit` (for labeling).
- `ProjectInfo` metadata (customer, vessel, job#).
- Default filename suggestion (timestamped).

## Outputs
- A single PDF document at the selected URI.
- Caller callback `onFinished()`.

## Error Handling
- IO guarded by `runCatching`; streams closed in `finally`.
- UI layer may show snackbar/toast on failure.

## Future Options
- Page size/orientation settings.
- Title-block controls and dimension toggles.
- Background grid parity with preview.
- Rich metadata injection.
