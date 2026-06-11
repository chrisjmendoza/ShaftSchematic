# PDF Export Specification
Version: v0.5.x

## Purpose
Defines the **single-page** PDF export process.  
PDF output must faithfully reproduce the shaft schematic at high resolution with no geometry distortion.

---

# 1. High-Level Process

spec → ShaftLayout (PDF size) → ShaftPdfComposer (draws geometry + dimensions + footer) → Final PDF Document

PDF export shares:
- Same `ShaftLayout` scaling rules as the preview
- Same model geometry (mm) and unit formatting conventions

**Note:** `ShaftPdfComposer` contains its own geometry drawing functions (`drawBodiesCompressedCenterBreak`, `drawTapers`, `drawThreads`, `drawLiners`) separate from `ShaftRenderer`. This is a known divergence from the intended architecture — geometry fixes must be applied in both paths until they are unified. See AUDIT.md §4.4.

---

# 2. Page Format

### Standard Page
- US Letter: 8.5" × 11"
- Portrait orientation
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
2. Compute:
pxPerMmPDF = min(
contentWidthPx / overallLengthMm,
contentHeightPx / maxOuterDiaMm
)

 
3. Call `ShaftLayout.compute` with PDF bounds.
4. Draw shaft using `ShaftRenderer`.
5. Draw title block.

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

The OAL arrow always spans `[0.0, win.oalMm]` = the full shaft from AFT end to FWD end. The label always equals the user's input (`spec.overallLengthMm`). The `excludeFromOAL` flag on end threads does not alter the OAL bracket or value.

All component dimension rails (liner offsets, taper lengths) reference SET positions — the physical taper start/end positions in shaft space.

---

# 6. PDF Rendering Invariants

1. Export is **single page** only.
2. No multi-page continuation.
3. No BOM tables.
4. No display compression.
5. No component overlays, cross-sections, or detailed machinist symbols.
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