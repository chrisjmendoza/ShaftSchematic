ShaftRenderer Contract
----------------------

Layer: UI → Drawing → Render  
Purpose: Draw the shaft and its components (bodies, tapers, threads, liners) onto a Canvas in mm-space.

Version: v0.3 (2025-10-04)

Invariants
- **All geometry is canonical millimeters (mm)**. No unit conversion is performed here.  
- Renderer never performs formatting, parsing, or persistence logic.  
- Centerline (if visible) aligns with shaft midline `cy`.  
- Grid/labels are anchored by the caller; renderer draws geometry only.

Responsibilities
- Render shaft bodies, tapers, threads, and liners using geometry from `ShaftSpec`.  
- Draw all geometry at the correct physical scale defined by `pxPerMm`.  
- Support optional centerline overlays via `RenderOptions`.  
- Restore thread hatch pattern (diagonal fill) from current thread spec data.  
- Respect the app contract’s “no unit math in renderer/layout” rule.  

Threads
- Default style: unified profile (crest/root rails + pitch-spaced flanks).  
- Legacy style: diagonal hatch helper (`drawThreadHatch`) remains available.

Performance
- Avoid allocations in hot paths except small Paths for tapers.  
- No density/unit lookups inside draw scopes; all styling is pre-injected.

Do Nots
- Do not fetch theme (MaterialTheme) or UI state inside draw lambdas.  
- Do not convert units; keep everything in px here.
