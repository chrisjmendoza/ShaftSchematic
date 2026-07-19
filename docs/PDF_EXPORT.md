# PDF Export Specification
Version: v0.5.x
Last updated: 2026-07-18 — fixed page orientation (landscape, not portrait); clarified preview/PDF as separate drawing paths (named the three fit functions); replaced the "no display compression" invariant with the actual round-stock S-break behavior; fixed the AUDIT.md path.

## Purpose
Defines the **single-page** PDF export process.  
PDF output must faithfully reproduce the shaft schematic at high resolution with no geometry distortion.

---

# 1. High-Level Process

spec → ShaftLayout (PDF size) → ShaftPdfComposer (draws geometry + dimensions + footer) → Final PDF Document

Preview rendering (`ShaftLayout` + `ShaftRenderer`) and the PDF composers (`ShaftPdfComposer`,
`RunoutPdfComposer`, `WearPdfComposer`) are **SEPARATE drawing paths**. They share model geometry
(mm) and layout-math *concepts* but not code:
- `ShaftPdfComposer` never calls `ShaftLayout.compute()`. It computes its own point-per-mm scale
  via three fit functions: `computeBodyOnlyPtPerMm`, `computeDetailPtPerMm`,
  `computePdfPtPerMmFitAxes` (which one is used depends on export mode/body-only vs. detail).
- Unit formatting conventions match the preview, but the pixel/point math is independent.

**Note:** `ShaftPdfComposer` contains its own geometry drawing functions (`drawBodiesPlain`,
`drawBodiesCompressedCenterBreak`, `drawTapers`, `drawThreads`, `drawLiners`) separate from
`ShaftRenderer`. This is an intentional architectural split, not a bug to unify — see
`docs/archive/AUDIT.md` §4.4 for history.

**Coupler bolt slots** are drawn on **all three PDF profiles** — the main schematic (`ShaftPdfComposer`), the runout sheet (`RunoutPdfComposer`), and the wear document (`WearPdfComposer`) — via a shared `drawCouplerBoltSlots` helper. Each cutout is a circle straddling the shaft outline (half in the shaft, half in the coupling), mirrored on the top and bottom edges, at each cutout position along the row. No dimension rail is drawn in v1 (the `showDimensionRail` toggle exists but is deferred).

---

# 2. Page Format

### Standard Page
- US Letter: 11" × 8.5" (792 × 612 pt)
- **Landscape** orientation (`PdfDocument.PageInfo.Builder(792, 612, 1)` —
  `PdfExportRoute.kt`, `PdfPreviewScreen.kt`)
- 50–75 pt margins (configurable)

### PDF Coordinate System
- 1pt = 1/72 inch
- (0,0) at top-left corner

`ShaftDrawing` (the Compose composable) is NOT used for PDF export.
Preview color settings (presets/custom palette and Black/White Only) are preview-only.
PDF uses its own fixed black-and-white styling inside `ShaftPdfComposer`.

---

# 3. Layout Flow

1. Define content bounds based on margins.
2. Compute `ptPerMm` using one of `ShaftPdfComposer`'s own fit functions (not `ShaftLayout`):
   `computeBodyOnlyPtPerMm`, `computeDetailPtPerMm`, or `computePdfPtPerMmFitAxes`, each taking
   the geometry rect's width/height in points and fitting `overallLengthMm` / `maxOuterDiaMm`.
3. Draw shaft geometry using `ShaftPdfComposer`'s own drawing functions (`drawBodiesPlain` /
   `drawBodiesCompressedCenterBreak`, `drawTapers`, `drawThreads`, `drawLiners`) — **not**
   `ShaftRenderer`.
4. Draw title block.

---

# 4. Title Block Specification

### Position:
Top of page, full width.

### Fields:
- Project / Drawing Title
- Description (optional)
- Date
- Units (always “mm”)
- Overall Length
- Scale (“1:1”, “2:1”, or “Scale to Fit”)
- Drawn By (optional)
- Revision (optional)

### Font Rules:
- Sans-serif
- 10–14 pt depending on field importance
- Black text only

---

# 5. Scale Notation

Compute:
scaleFactor = pxPerMmPDF * (1 inch in px) / 25.4mm

 

Then:
if scaleFactor ≈ 1 → "1:1"
if scaleFactor ≈ 2 → "2:1"
else → "Scale to Fit"

 

Users do not configure scale manually.

---

# 5.1 Line Thickness Scale

`composeShaftPdf()` accepts a `lineThicknessScale: Float` parameter (range 0.5–2.0, default 1.0). It is applied to two base stroke widths:

- `OUTLINE_PT_BASE = 1.25 pt` × scale → body/taper/thread/liner outline strokes
- `DIM_PT_BASE = 0.8 pt` × scale → dimension tick and arrow strokes

At 1.0 the output matches the current default thin weight. At 2.0 it matches the original pre-rebased thick weight. The scale is persisted in DataStore and passed from `PdfExportRoute` and `PdfPreviewScreen`.

---

# 5.2 OAL Dimension Span

The OAL label always equals `spec.overallLengthMm` — the user's typed value. The bracket **position** changes based on `excludeFromOAL`:

- **Excluded**: bracket spans AFT SET → FWD SET (threads outside the bracket).
- **Included**: bracket spans shaft AFT end → FWD SET (thread grouped inside the bracket).

The label is passed as an explicit `labelMm` override to `oalSpan()` so it is always the typed OAL regardless of bracket width. Component dimension rails always reference SET positions.

---

# 6. PDF Rendering Invariants

1. Export is **single page** only.
2. No multi-page continuation.
3. No BOM tables.
4. **Round-stock display compression exists for long bodies** (this replaces an earlier "no
   display compression" claim, which is no longer true). `ShaftPdfComposer.drawBodiesCompressedCenterBreak()`
   triggers per-body when that body's on-paper length reaches `COMPRESS_TRIGGER_PT` (220 pt): the
   body is drawn as two shortened stubs, each capped with an S-curve "round-stock break" symbol
   (`pdf/BreakSymbol.kt`, `drawBreakEdge()`) instead of a straight end cap, so the drawing reads as
   a foreshortened cylindrical bar rather than a literal-length rectangle. The footer prints an
   explanatory compression note (`showCompressionNote`) whenever any drawn body triggers this.
   Only bodies are compressed this way — tapers/threads/liners are never broken.
5. No component overlays, cross-sections, or detailed machinist symbols (aside from the
   round-stock break symbol above, which is a length-compression cue, not a machinist symbol).
6. Geometry must reflect the same logic as preview.
7. PDF export never modifies the model.

---

# 6.1 Measurement & Tiering Rules

- Forced AFT/FWD uses a single global reference for numeric baselines.
- AUTO preserves per-component anchor/proximity behavior.
- Tiering affects rail stacking only and never changes numeric values.
- Units are passed explicitly and never derived from tiering or measurement reference.

---

# 7. Error Handling

- If `overallLengthMm == 0`, export stops.
- If layout fails (rare), PDF generation aborts with user-visible error.

---

# 8. Summary
This contract ensures PDF output is consistent, readable, scalable, and fully aligned with the in-app schematic preview.

PDF export must always reflect the renderer’s output exactly.