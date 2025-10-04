UnitsStore Contract
-------------------

Layer: Util → Settings  
Purpose: Persist and retrieve the user’s preferred measurement unit and related UI settings.

Version: v0.1 (2025-10-04)

Invariants
- Stores only preferences, never geometry.  
- Exposes flows for reactive updates.

Responsibilities
- Get/set `UnitSystem` (mm/in).  
- Optionally store `showGrid`, preview background mode.

Do Nots
- Do not convert or format numbers.  
- Do not access model or renderer directly.
