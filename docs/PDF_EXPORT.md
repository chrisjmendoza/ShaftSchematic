# PDF Export Specification
Version: v0.4.x

## Purpose
Defines the **single-page** PDF export process.  
PDF output must faithfully reproduce the shaft schematic at high resolution with no geometry distortion.

---

# 1. High-Level Process

spec → ShaftLayout (PDF size) → ShaftRenderer (PDF canvas) → Title Block → Final PDF Document

 

PDF export reuses:
- Same `ShaftLayout` rules
- Same `ShaftRenderer` geometry routines

No additional geometry logic is permitted.

---

# 2. Page Format

### Standard Page
- US Letter: 8.5" × 11"
- Portrait orientation
- 50–75 pt margins (configurable)

### PDF Coordinate System
- 1pt = 1/72 inch
- (0,0) at top-left corner

ShaftDrawing is NOT used for PDF; PDF uses layout + renderer only.

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

# 6. PDF Rendering Invariants

1. Export is **single page** only.
2. No multi-page continuation.
3. No BOM tables.
4. No display compression.
5. No component overlays, cross-sections, or detailed machinist symbols.
6. Geometry must reflect the same logic as preview.
7. PDF export never modifies the model.

---

# 7. Error Handling

- If `overallLengthMm == 0`, export stops.
- If layout fails (rare), PDF generation aborts with user-visible error.

---

# 8. Summary
This contract ensures PDF output is consistent, readable, scalable, and fully aligned with the in-app schematic preview.

PDF export must always reflect the renderer’s output exactly.