ShaftLayout Contract
--------------------

Layer: UI → Drawing → Layout  
Purpose: Compute mm→px mapping, world bounds, and view scaling for rendering shaft geometry.

Version: v0.3 (2025-10-04)

Invariants
- Input model (`ShaftSpec`) and all component dimensions are **mm**.  
- Layout is **pure math**; no UI or rendering logic.  
- Output defines pixel-space transforms and view bounds for Canvas drawing.  
- Grid and scaling calculations are **unit-agnostic**; anchored at `x = 0 mm` and centerline.

Responsibilities
- Determine `pxPerMm` scale factor based on preview box size and model length.  
- Compute world bounds (`viewLeftMm`, `viewRightMm`, `viewTopMm`, `viewBotMm`).  
- Provide coordinate mapping functions: `xPx(mm)`, `rPx(mm)`.  
- Normalize geometry to ensure fit with consistent margins.  
- Clamp extents to prevent overscroll/partial rendering.  
- Report layout metrics used by ShaftRenderer.

Do Nots
- Do not draw or allocate UI resources.  
- Do not perform unit conversions.  
- Do not depend on Compose or Material APIs.

Notes
- `compute()` is called on spec/size changes.  
- Ensures two-axis scaling to avoid the “tiny shaft” issue.  
- Grid anchor uses `floor(min/gridStep)*gridStep` to keep 0-mm meridian stable.

Future Enhancements
- Layout caching for unchanged specs.  
- “Fit height” mode and dynamic aspect hints.  
- Viewport zoom levels for interaction.
