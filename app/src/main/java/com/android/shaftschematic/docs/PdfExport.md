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
| `curveLoHeightIn` | `0.5` | Sizing-curve anchor: drawn height (paper in) of a 4" shaft at 100% (0.25–1.5) |
| `curveHiHeightIn` | `1.0` | Sizing-curve anchor: drawn height (paper in) of an 8" shaft at 100% (0.25–1.5) |
| `sBreakThresholdFrac` | `0.5` | Body S-break threshold: a body run breaks once drawn below this fraction of its true length (0–1; `0` = never break on compression) |

All fields are also reachable in the preview screen's Tune sheet, except the sizing-curve
anchors, which live only in Settings → Drawing (app-level defaults) under "Default
drawing size"; the per-job "Shaft height" slider multiplies on top of the sizing curve.

- **Body S-break** (`sBreakThresholdFrac`): slider in 5% steps, commits on release, with a
  "Default (50%)" reset button — the same posture as Line Thickness, and like Line
  Thickness it lives in **both** places: Settings → Drawing → "Body S-break" and the PDF
  Options sheets of the schematic and runout/consolidated previews (one shared
  `SBreakThresholdSlider`, one app-wide pref — see below). The readout shows
  "Never" at 0 and "below N%" elsewhere. It governs only the *compression* trigger of
  `breakForCompression` (`pdf/BreakSymbol.kt`), shared by the schematic body loop, the
  consolidated sheet's body loop, and the footer's compression note. The traditional
  long-span trigger `COMPRESS_TRIGGER_PT` (220 pt of paper) is independent and unaffected:
  at "Never" a genuinely long run still shows its break. Bodies only — liners and tapers
  foreshorten silently at every setting. Every preview that rasterizes with the current
  `PdfPrefs` keys its `LaunchedEffect` on `vm.pdfSBreakThresholdFrac`, so the change is live.

---

## PdfPreviewScreen

Full-resolution preview through the shared `util/PdfRaster.renderPdfPageBitmap`
(`PdfDocument` + `PdfRenderer`, 2× raster), pinch-to-zoom 0.5×–8×, double-tap reset.

- **Options sheet (Tune icon):** blank draft (write-in) toggle, component labels, line
  thickness (50–200%), "Body S-break" threshold, "Shaft height" slider, liner compression
  control, measurement reference (Auto/AFT/FWD), shade bodies/tapers/liners — bound to
  `PdfPrefs` (or, for the
  height/liner-compression pair, the per-job `RunoutConfig`) via VM setters, persisted,
  applied live (each option is a `LaunchedEffect` key). Blank draft is session-scoped, not
  persisted.
  - Line thickness is the shared `LineThicknessSlider` (`ShaftHeightSlider.kt`), used by
    this sheet and the runout/wear options sheet: a "Default (100%)" reset button plus a
    ±5% magnetic detent on slider release (`snappedLineThickness`), the same posture as
    the shaft-height slider's Standard button, so 100% never has to be fished for by
    pixel (on-device report). The Settings → Editor Screen control keeps its own layout
    (it adds a typed % field, which is never snapped) but shares the detent and button.
  - Shade bodies/tapers/liners is the shared `ShadeInPdfChecks` (`ShaftHeightSlider.kt`) —
    heading, three checkbox rows, and the `linerShadeLocked` display-only lock the
    consolidated sheet uses — the same block as the runout/wear options sheet. Settings →
    PDF Export keeps its own copy (its rows live in a `spacedBy(12.dp)` column with a
    padded heading); the prefs and setters are identical.
  - The sheet's content is taller than a phone screen, so it carries its own
    `verticalScroll` plus `navigationBarsPadding()` — without them the bottom rows clip
    mid-checkbox behind the navigation bar. Its height is capped at **78% of the screen**
    (`LocalConfiguration.screenHeightDp * 0.78f`): a sheet expanded to the status bar
    leaves no edge to swipe it back down by (on-device report).
- **Live tuning:** the four tuning sliders — Line thickness, Body S-break, Shaft height,
  Liner compression — reshape the page **while the finger is still on the track**
  ("see the differences without choosing, closing menu, opening menu, choosing" —
  on-device request). Each shared control (`ui/screen/ShaftHeightSlider.kt`) carries an
  optional `onDrag: (Float?) -> Unit`: the in-progress value every frame in the SAME units
  as its commit callback (the height slider converts drawn inches → `heightScale` exactly
  as its commit does, **minus** the standard-height detent — snapping is a commit rule),
  and `null` on release. The screen parks it in a `PreviewTuning`
  (`ui/screen/PreviewTuning.kt`) and the render loop reads `override ?: committed`.
  - **Visual only.** No DataStore write and no `RunoutConfig` update happens on a drag
    frame; commit-on-release is untouched, so nothing persists and the job is not marked
    dirty by a drag. Callers that don't opt in (Settings, the wear/undercut sheets) keep
    the no-op default.
  - **Draft resolution.** `renderPdfPageBitmap(renderScale = …)` takes 1 while a drag is
    live (≈¼ the pixels, so the page keeps up) and the pass after the release restores
    `PDF_PREVIEW_RENDER_SCALE`. The spinner is held back for drag frames and for that
    release pass — the current page stays up instead of strobing.
  - **Scrim.** The options `ModalBottomSheet` passes
    `scrimColor = if (tuning.active) Color.Transparent else BottomSheetDefaults.ScrimColor`:
    the page above is the thing being judged, so the dimming comes off for the drag and
    the modal affordance returns on release.
- **Orientation:** `DisposableEffect` unlocks rotation on entry and restores the
  portrait lock on dispose — every other screen stays portrait-only.
- **Pipeline:** `snapshotFlow { SchematicRenderInputs(…) }.conflate().collect { … }` →
  snapshot `vm.currentPdfPrefs` on main thread → `Dispatchers.IO` →
  `renderPdfPageBitmap` (temp PDF via the `composeShaftPdf` lambda → rasterize page 0) →
  pan/zoom Canvas. The `RenderInputs` holder is a data class capturing **everything** the
  composed page reads — including the fields that reach the composer inside the `PdfPrefs`
  snapshot rather than as arguments (shade flags, component titles, tiering mode, S-break
  threshold, and the sizing-curve anchors `curveLoHeightIn`/`curveHiHeightIn`, which the
  older `LaunchedEffect` key list omitted, so a Settings change to "Default drawing size"
  left an open preview at its old height). Omitting an input here is a stale-preview bug.
  `conflate()` is latest-wins: intermediate values produced while a render is in flight are
  dropped and the newest always renders — which is what makes a slider drag keep up. Temp
  file deleted after rasterization;
  failures return null and show an error, never crash. **ONE raster helper**
  (`util/PdfRaster.kt`) serves every tab's preview — schematic, runout, wear, undercut,
  consolidated output — the raster sibling of the one hardened SAF write path
  (`util/PdfSafExport.writeShaftPdfToUri`), and it takes the same `composePage` lambda, so
  a preview always shows the real composed page.
- **Top bar:** Back · Tune · Refresh (reset zoom/pan) · Print (system print of a freshly
  composed page, state snapshotted on the UI thread) · PDF (`onExport()` → SAF).

## Invariants
- No model state mutated in either screen — rendering and preference changes only.
