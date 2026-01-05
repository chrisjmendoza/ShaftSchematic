RenderOptions Contract
----------------------

Layer: UI → Drawing → Config  
Purpose: Define styling and toggles for ShaftRenderer and ShaftDrawing.

Version: v0.4 (2026-01-04)

Invariants
- RenderOptions is immutable; each change creates a new instance.  
- All values are in **pixels/dp/colors**, never in mm.  
- Contains no model data; only view and style configuration.

Responsibilities
- Hold visual parameters:
  - Line widths (`outlineWidthPx`, `dimLineWidthPx`)
  - Colors (`outlineColor`, `bodyFillColor`, `taperFillColor`, `linerFillColor`, thread colors)
  - Grid semantics (`showGrid`, `gridUseInches`, legend controls)
  - Highlighting controls (enabled + glow/edge colors)
- Provide stable defaults for light/dark themes.
- Supply configuration to both ShaftDrawing and ShaftRenderer.

Do Nots
- Do not store geometry or scaling.  
- Do not perform conversions or draw calls.  
- Do not depend on ViewModel or mutable state.

Notes
- Construct via `remember { RenderOptions(...) }`; pass to draw layers.  
- ColorScheme accessed at construction, never inside draw lambdas.

Future Enhancements
- PDF-facing color configuration (preview color settings are preview-only).  
- Hatch angle customization and dimension line colors.
