RenderOptions Contract
----------------------

Layer: UI → Drawing → Config  
Purpose: Define styling and toggles for ShaftRenderer and ShaftDrawing.

Version: v0.3 (2025-10-04)

Invariants
- RenderOptions is immutable; each change creates a new instance.  
- All values are in **pixels/dp/colors**, never in mm.  
- Contains no model data; only view and style configuration.

Responsibilities
- Hold visual parameters:
  - Line widths (`shaftWidthPx`, `dimWidthPx`)
  - Colors for bodies/tapers/liners/thread hatch
  - Grid visibility (`showGrid`)
  - Centerline visibility (`showCenterline`)
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
- User color presets and print/dark modes.  
- Hatch angle customization and dimension line colors.
