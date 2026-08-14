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
| `arrowSizePt` | `4` | Dimension-rail arrowhead length (pt): Small `3` / Medium `4` / Large `5` |

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
- **Dimension arrows** (`arrowSizePt`): three chips — **Small 3 (default)** / Medium 4 /
  Large 5 pt (Large is the historical head) — in Settings → Drawing → "Dimension arrows" and
  in both PDF Options sheets (one shared `DimensionArrowSizeChips`, one app-wide pref). A
  chip tap IS the commit, so unlike the sliders there is no `onDrag` channel and no
  `PreviewTuning` override; each preview keys its render inputs on `vm.pdfArrowSizePt` so the
  page redraws at once. It sizes the heads on the two composers that build a
  `PdfDimensionRenderer` — the schematic and the consolidated sheet; the wear/undercut strip
  rails keep their own fixed 4 pt head. A smaller head also slightly widens inline
  eligibility, since a break's stubs must each be at least `arrowSize` long.
- **Fractions** (`fractionStyle`): three chips — Stacked / **Diagonal (default)** / Plain — in
  Settings → Drawing → "Fractions" and in both PDF Options sheets (one shared
  `FractionStyleChips`, one app-wide pref). **Ungated in the sheets**, unlike the arrowhead
  size: every document they serve prints lengths, so every one draws fractions. A chip tap IS
  the commit — no `onDrag` channel, no `PreviewTuning` override. It is the one control in this
  section that also restyles the **on-screen** sheets, because both draw families go through
  `util/FractionTextRenderer.kt`. The style reaches the ink through the process-wide
  `FractionTypography.active` mirror rather than a composer argument, so each preview carries
  `fractionStyle` in its render inputs purely as a re-render key. See
  `docs/FractionTypography.md` §3.1.

---

## PdfPreviewScreen

Full-resolution preview through the shared `util/PdfRaster.renderPdfPageBitmap`
(`PdfDocument` + `PdfRenderer`, 2× raster), pinch-to-zoom 0.5×–8×, double-tap reset.

- **Options sheet (Tune icon):** blank draft (write-in) toggle, component labels, line
  thickness (50–200%), "Body S-break" threshold, "Dimension arrows" size, "Shaft height"
  slider, liner compression control, measurement reference (Auto/AFT/FWD), shade
  bodies/tapers/liners — bound to
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
    mid-checkbox behind the navigation bar. Its height cap arrives as a `maxHeightDp`
    parameter computed by the hosting screen from `tuningSheetMaxHeightDp` (see **Tuning
    layout** below) — this sheet tunes the page live, so it stops below the page strip
    instead of taking the historical 78% of the screen, and only the screen knows the strip.
    Both caps keep the sheet clear of the status bar, which would otherwise leave no edge to
    swipe it back down by (on-device report).
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
  - **Tuning layout — the page keeps a strip on top.** Live rendering is worthless if the
    menu covers the page: on a phone the options sheet filled virtually the whole screen
    ("It may render live but the menu with the sliders is in the way. I can see the PDF
    Preview area lighten up on moving a slider but I can't see anything. I need to close
    the menu to see the changes." — on-device report). While the sheet is open the preview
    switches to the **tuning layout**, whose pure math lives in `ui/screen/PreviewTuning.kt`:
    - The PDF pages are LANDSCAPE, so a whole page fits a strip only
      `screenWidthDp × (PDF_PAGE_HEIGHT_PT / PDF_PAGE_WIDTH_PT)` tall
      (`fitWidthPageHeightDp`, never a magic ratio). The Canvas draws the page fit-width and
      **top-aligned** under the app bar instead of centered-and-fitted.
    - **The strip carries the page's ink band, not the whole sheet.** A composed page rarely
      inks its full height — the top margin plus unused rail room ran to ~30% of the page,
      so the drawing sat low in the strip under a band of white while items below the shaft
      fell off ("The shaft rendering has a LOT of white space on top and we're losing some
      of the items under the shaft" — on-device report). `util/PdfInkBounds.kt` measures the
      rendered bitmap's first and last inked rows (`InkBand`, padded 2.5% a side; one row at
      a time through `getPixels`, sampled every `height/200` rows and `width/256` pixels, ink
      = any channel < 0xF0) and the strip is `fitWidthPageHeightDp × InkBand.frac`. Ink is
      never cropped — the OAL rail and the footer are ink, so they are inside the band by
      construction; only paper is. The band is measured on **sharp (non-draft) passes only**,
      so a slider drag never resizes the strip or the sheet under the finger, and a page with
      no band yet draws whole.
    - **The pair is strip-first, cap-derived.** `tuningPageStripHeightDp(screenWidth,
      screenHeight, sheetChromeDp, inkFrac)` then `tuningSheetMaxHeightDp(screenHeight,
      strip, sheetChromeDp)` = screen height − strip − `PREVIEW_TOP_CHROME_DP` (88 dp: status
      bar + app bar) − sheet chrome, clamped to `[TUNING_SHEET_MIN_FRAC 40%,
      PREVIEW_SHEET_MAX_FRAC 78%]` of the screen. The cap takes the strip as an argument
      rather than recomputing it, so the two can never disagree.
    - **The sheet's own chrome is budgeted.** `heightIn` caps the sheet's CONTENT column;
      M3 stacks its drag handle (4 dp bar + 22 dp padding a side = `TUNING_SHEET_CHROME_DP`
      48 dp) and the sheet's bottom window inset OUTSIDE that cap, so the real sheet stood
      ~48 dp + nav bar taller than the budget and covered the bottom of the page (on-device
      report). Both draw sites pass `TUNING_SHEET_CHROME_DP + WindowInsets.navigationBars`
      bottom. On a 393 × 851 dp phone with a 48 dp nav bar: strip 303.7 dp, sheet 363.3 dp —
      with the chrome, exactly the screen. The sheet scrolls internally, so no content is
      lost. Where `Configuration.screenHeightDp` excludes the system bars the cap comes out
      conservative: a slightly shorter sheet, never a covered page.
    - **Clamp order: the sheet keeps its floor, the strip yields the remainder**
      (`tuningPageStripHeightDp`). On a short/wide screen the page fits to the shrunken
      strip — it is zoomable once the sheet closes; the sliders are not usable at all if
      crushed.
    - **One draw helper.** `DrawScope.drawPageBand(bitmap, band, stripHeightPx)`
      (`PreviewTuning.kt`) crops to the band, fits to strip width/height, centers
      horizontally and pins to the top. Both strip sites call it — the schematic preview's
      Canvas and `PdfPreviewOverlay`'s (which swaps its `Image` for a `Canvas` in strip
      layout, same modifier chain plus an explicit `contentDescription`) — so they cannot
      drift. The non-strip path is untouched: the normal preview still shows the real page,
      margins and all.
    - Opening the sheet **resets zoom/pan** to fit. Deliberate: an inspection zoom would
      put the strip off-screen exactly when the sliders need it visible — predictable over
      preserved. Closing returns the normal layout (centered, pinch 0.5×–8×, double-tap
      reset).
  - **Scrim.** `ModalBottomSheet`'s scrim is one **full-window** rect — it cannot be
    restricted to the area below the strip, and dimming the strip is what the layout exists
    to prevent — so `scrimColor` is `Color.Transparent` whenever the sheet tunes the page.
    The preview Box paints the strip-to-sheet **gap** itself with `BottomSheetDefaults.ScrimColor`
    to keep the modal affordance, and drops even that while a slider is being dragged. The
    transparent scrim still handles tap-outside-to-dismiss, unchanged.
  - The blank-draft chip overlaid on the preview is **hidden while the sheet is open** — it
    would sit on the page strip, and the sheet's own first row is that same switch.
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
