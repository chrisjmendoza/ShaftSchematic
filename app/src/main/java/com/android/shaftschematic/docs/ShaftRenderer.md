ShaftRenderer Contract
----------------------
Inputs & Units
- Consumes ShaftSpec measured in millimeters. No unit conversions inside the renderer.
- All mm→px mapping is provided by ShaftLayout.Result and used only via xPx/rPx/pxPerMm.

Drawing Order
- Bodies → Tapers → Threads → Liners. Change order only if visual hierarchy must differ.

Strokes & Fills
- Each component is rendered as a filled region with a single outline of constant width (px).
- Outlines use RenderOptions.outline and RenderOptions.outlineWidthPx.

Threads
- Default style: "Unified profile" (crest/root rails + pitch-spaced flanks).
    - Rails use outline color; flanks use threadHatch color (lighter) for readability.
    - Pitch uses model pitchMm; fallback is 10 TPI (25.4 / 10 mm) if absent/≤0.
    - Minor radius approximates 0.85 × major radius unless model supplies a minor diameter.
- Legacy style: "Diagonal hatch". Helper kept (drawThreadHatch). Revert by swapping call in the thread loop.

Performance
- No allocations in hot paths except small Paths for tapers.
- No density/unit lookups inside draw scopes; all styling is pre-injected.

Do Nots
- Do not fetch theme (MaterialTheme) or UI state inside draw lambdas.
- Do not convert units; keep everything in px here.
