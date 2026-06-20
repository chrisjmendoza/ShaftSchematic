ShaftLayout Contract
--------------------

Layer: UI → Drawing → Layout  
Purpose: Compute mm→px mapping, world bounds, and view scaling for rendering shaft geometry.

Version: v0.4 (2026-06-18)

Invariants
- Input model (`ShaftSpec`) and all component dimensions are **mm**.  
- Layout is **pure math**; no UI or rendering logic.  
- Output defines pixel-space transforms and view bounds for Canvas drawing.  
- Grid and scaling calculations are **unit-agnostic**; centered on the shaft span including any excluded-thread overhangs.
- `minXMm` may be **negative** when excluded AFT threads exist; `maxXMm` may exceed `overallLengthMm` when excluded FWD threads exist.

Responsibilities
- Determine `pxPerMm` scale factor based on preview box size and model length.  
- Compute world bounds including excluded threads that sit outside the 0..OAL shaft span.  
- Provide coordinate mapping functions: `xPx(mm)`, `rPx(mm)`.  
- Normalize geometry to ensure fit with consistent margins.  
- Report layout metrics used by ShaftRenderer.

Excluded-thread coordinate expansion
- `minXMm = min(0, min startFromAftMm of AFT excluded threads)`  
- `maxXMm = max(OAL, max (startFromAftMm + lengthMm) of FWD excluded threads)`  
- This ensures excluded threads render adjacent to — but never clipped by — the shaft boundary.

Do Nots
- Do not draw or allocate UI resources.  
- Do not perform unit conversions.  
- Do not depend on Compose or Material APIs.

Notes
- `compute()` is called on spec/size changes.  
- Ensures two-axis scaling to avoid the “tiny shaft” issue.  
- Excluded threads have negative `startFromAftMm` (AFT) or `startFromAftMm ≥ OAL` (FWD) after `syncExcludedThreadPositions()` runs.

Future Enhancements
- Layout caching for unchanged specs.  
- “Fit height” mode and dynamic aspect hints.  
- Viewport zoom levels for interaction.

Change Log
----------
**v0.4 (2026-06-18)**
- `minXMm` and `maxXMm` now expand to include excluded threads positioned outside the 0..OAL shaft span, so they render without clipping in both the preview and the PDF.
- Removed hardcoded `minXMm = 0f`; value is now the minimum of 0 and any AFT excluded thread start.

**v0.3 (2025-10-04)**
- Initial stable version; `minXMm` was hardcoded to 0.
