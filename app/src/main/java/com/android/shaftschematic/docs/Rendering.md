Rendering Contract (preview pipeline)
=====================================

Layer: UI → Drawing (`ui/drawing/`)  
Purpose: Single contract for the on-screen preview pipeline — the Compose host
(`compose/ShaftDrawing.kt`), layout math (`render/ShaftLayout.kt`), geometry renderer
(`render/ShaftRenderer.kt`), and styling config (`render/RenderOptions.kt`).

Version: v1.0 (2026-07-18) — consolidates the former `ShaftDrawing.md`,
`ShaftLayout.md`, `ShaftRenderer.md`, and `RenderOptions.md` into one doc.

> **PDF is a separate path.** The PDF composers (`ShaftPdfComposer`,
> `RunoutPdfComposer`, `WearPdfComposer`) share the model and layout *concepts* but use
> their own Canvas drawing code and fit functions — they never call
> `ShaftLayout.compute()` or `ShaftRenderer`. A fix here does not propagate to PDF.

---

Shared invariants
-----------------
- Input model (`ShaftSpec`) and all component dimensions are canonical **mm**.
- No unit conversion anywhere in this subsystem; mm ↔ in happens only for grid
  legends/labels at the Compose edge.
- No model mutation; pure rendering.
- No MaterialTheme access inside draw scopes — all colors/styles pre-injected via
  `RenderOptions` (constructed at the Composable level).

---

ShaftDrawing (Compose host)
---------------------------
Owns the Compose Canvas:
1. Computes layout with `ShaftLayout.compute()`.
2. Draws the optional engineering grid (grid lives here, not in the renderer).
3. Delegates component geometry to `ShaftRenderer.draw()`.

Also bridges persisted preview preferences into `RenderOptions`: preview colors
(presets + Custom palette), Black/White Only override, selection highlight
(glow + edge under-stroke), and line thickness scale (`lineThicknessScale`, 0.5–2.0,
multiplies `outlineWidthPx`). Text measurement uses `rememberTextMeasurer`.
If `spec.overallLengthMm == 0`, the preview extends to the last occupied component
end so something always renders.

ShaftLayout (mm→px math)
------------------------
Pure math, no UI or Compose dependencies. `compute(spec, leftPx, topPx, rightPx,
bottomPx, marginPx, resolvedComponents)` determines `pxPerMm` (two-axis fit to avoid
the "tiny shaft" issue), world bounds, and mapping functions `xPx(mm)` / `rPx(mm)`.

Excluded-thread coordinate expansion:
- `minXMm = min(0, min startFromAftMm of AFT excluded threads)` — may be **negative**.
- `maxXMm = max(OAL, max end of FWD excluded threads)` — may exceed `overallLengthMm`.
- Ensures excluded threads render adjacent to — never clipped by — the shaft boundary.
  (Excluded threads sit at negative start (AFT) or ≥ OAL (FWD) after
  `syncExcludedThreadPositions()`.)

Degenerate inputs: axial span floors to `max(1f, overallLengthMm)`; diameter fit
floors at 10 mm (`minDiaForFitMm`); `pxPerMm` is coerced ≥ 0.0001 — it is never 0;
degenerate pixel bounds (width/height ≤ 0) floor to 1f internally, so `compute()`
always returns a valid `Result`.

`Result` fields: `pxPerMm`, `minXMm`, `maxXMm`, `contentLeftPx/RightPx/TopPx/BottomPx`,
`centerlineYPx`. Mapping: `xPx(mm) = contentLeftPx + (mm − minXMm) · pxPerMm`;
`rPx(radMm) = radMm · pxPerMm`; `centerlineYPx = contentTopPx + drawableHeight/2`.
Two-axis fit: `pxPerMm = max(min(width/axialSpan, height/max(maxOuterDiaMm,
minDiaForFitMm)), 0.0001f)`. `maxOuterDiaMm` scans body Ø, taper SET/LET, thread
major Ø, and liner OD; when `resolvedComponents` is supplied the scan includes auto
bodies. **Coupler bolt slots contribute 0** to both `maxOuterDiaMm` and `maxXMm`
(excluded from `coverageEndMm()`) — a slot never widens or heightens the layout
window.

ShaftRenderer (geometry)
------------------------
Draws bodies → tapers → threads → liners at physical scale from `pxPerMm`, plus a
final **overlay pass** for coupler bolt slots (reads `spec.couplerBoltSlots` directly,
not the resolved list): each cutout is a circle straddling the shaft outline (half
in / half out), mirrored top and bottom; local surface radius looked up from the
covering component (falls back to max OD); fill from `RenderOptions.slotFillColor`.
See `CouplerBoltSlot.md`.

- Threads: unified profile (crest/root rails + pitch-spaced flanks); legacy diagonal
  hatch helper (`drawThreadHatch`) remains available.
- `centerlineYPx` is a **Y-reference for positioning geometry only** — the renderer
  never draws a visible centerline stroke.
- Dead / unwired option: `RenderOptions.showCenterline` (default `true`) is never
  read anywhere; treat as inert until wired or removed.
- Performance: avoid allocations in hot paths except small Paths for tapers; no
  density/unit lookups inside draw scopes.

RenderOptions (styling config)
------------------------------
Immutable view/style configuration — pixels/dp/colors only, never mm, no model data:
line widths (`outlineWidthPx`, `dimLineWidthPx`), colors (outline, per-component
fills, thread colors, slot fill), grid semantics (`showGrid`, `gridUseInches`,
legend controls), highlight controls. Construct via `remember { RenderOptions(...) }`
with ColorScheme accessed at construction, never inside draw lambdas.

---

Do Nots (whole subsystem)
-------------------------
- No business logic, spec mutation, parsing, formatting, or persistence.
- No unit conversions below the Compose edge.
- No Compose/Material dependencies in `ShaftLayout`/`ShaftRenderer`.

Change Log
----------
**v1.0 (2026-07-18)** — merged four per-file docs; carried forward: excluded-thread
bounds expansion (ShaftLayout v0.4, 2026-06-18), centerline dead-code correction
(ShaftRenderer v0.5, 2026-07-18), line-thickness bridge (ShaftDrawing v0.4).
