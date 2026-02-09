# PDF Export Specification
Version: v0.4.x

## Authority
This document is authoritative for PDF export behavior.
If other documentation conflicts with this file, this file takes precedence.

OAL Authority (Explicit):
- The authored `overallLengthMm` is sacred.
- OAL is never derived from geometry, components, or threads.
- PDF dimension labels must always reflect the authored OAL exactly.

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
- **Landscape** orientation (792×612 pt)
- 50–75 pt margins (configurable)

### PDF Coordinate System
- 1pt = 1/72 inch
- (0,0) at top-left corner

ShaftDrawing is NOT used for PDF; PDF uses layout + renderer only.

Note: Preview color settings (presets/custom palette and Black/White Only) are preview-only.
PDF currently uses its own fixed styling via `RenderOptions` inside the PDF composer.

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
4. No component overlays, cross-sections, or detailed machinist symbols.
5. Geometry must reflect the same logic as preview.
6. PDF export never modifies the model.

---

# 6.1 Measurement & Tiering Rules

- Forced AFT/FWD uses a single global reference for numeric baselines.
- AUTO preserves per-component anchor/proximity behavior.
- Tiering affects rail stacking only and never changes numeric values.
- Units are passed explicitly and never derived from tiering or measurement reference.

# 6.2 Drawing Footprint vs Dimension Footprint

- All components (bodies, liners, tapers, threads) participate in rendering.
- Only dimension-participating components affect dimension rails and measured spans.
- Rendering footprint and dimension footprint are intentionally distinct.

# 6.3 Thread Semantics

INCLUDED threads:
- Participate in OAL.
- Participate in dimension rails and spans.
- Are treated as normal components for measurement.

EXCLUDED threads:
- Do NOT participate in OAL.
- Do NOT participate in dimension rails or spans.
- Are rendered as end attachments outside the working axis.
- Still contribute to the overall drawing footprint.
- Are always listed in the PDF footer.

AFT and FWD ends are evaluated independently and symmetrically.

# 6.4 Dimension Rail Anchoring Rules

- Dimension rails are anchored to the first and last dimension-participating components.
- Excluded threads shift the visual shaft geometry but do not extend dimension rails.
- Included threads extend dimension rails normally.
- Per-end anchoring is symmetric and independent (AFT vs FWD).

# 6.5 Component Compression Rules

- Bodies are compressible for layout purposes.
- Liners and tapers are sacred and non-compressible.
- Compression exists to preserve readability and page fit, not scale accuracy.

# 6.6 Footer Contract

- All threads (included or excluded) must appear in the PDF footer.
- Footer content is descriptive and independent of dimension participation.

---

# 7. Error Handling

- If `overallLengthMm == 0`, export stops.
- If layout fails (rare), PDF generation aborts with user-visible error.

---

# 8. Summary
This contract ensures PDF output is consistent, readable, scalable, and fully aligned with the in-app schematic preview.

PDF export must always reflect the renderer’s output exactly.