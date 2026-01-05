ShaftDrawing Contract
----------------------

Layer: UI → Compose  
Purpose: Provide a composable surface for rendering shaft previews. Handles
text measurement, layout computation, optional grid drawing, and delegates
component geometry rendering to ShaftRenderer.

Version: v0.4 (2026-01-04)

Invariants
- Input model ([ShaftSpec]) always uses canonical millimeters.  
- All unit conversions (mm ↔ in) happen only for grid/labels, never for geometry.  
- This file must not mutate model state; it is pure rendering.  
- Renderer (ShaftRenderer) is unit-agnostic and receives only mm + layout mapping.

Responsibilities
- Own a Compose Canvas that:
  1) Computes layout with ShaftLayout.compute().
  2) Draws the optional engineering grid (if enabled).
  3) Delegates component geometry drawing to ShaftRenderer.draw().
- Bridge persisted preview preferences into RenderOptions:
  - Preview colors (presets + Custom palette)
  - Black/White Only override (forces black outlines and disables fills)
  - Highlight selection (glow + edge under-stroke)
- Manage text measurement via rememberTextMeasurer to avoid allocations.
- Ensure preview always shows something:
  - If spec.overallLengthMm == 0, extend length to last occupied component end.

Notes
- RenderOptions configures colors, stroke widths, and thread styling.
- Grid is drawn here (not by the renderer) for separation of concerns.
- Accessibility: labels and badges (e.g., free-to-end) should be exposed for screen readers.
- UI sizing and trimming (header row vs. preview box) are controlled at the Composable level.

Do Nots
- Do not perform business logic or spec mutation here.
- Do not use MaterialTheme styling inside draw scopes; pass explicit colors.
