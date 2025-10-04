Model Conventions
-----------------

Layer: Model  
Purpose: Shared expectations across Body, Taper, ThreadSpec, Liner, Segment.

Version: v0.1 (2025-10-04)

Invariants
- All fields are **Float mm** unless stated otherwise.  
- `startFromAftMm + lengthMm` gives end; validate within `overallLengthMm`.  
- Thread pitch stored as **pitchMm**; TPI only at UI edge.

Responsibilities
- Keep data classes passive (no business logic).  
- Provide `isValid(overallLengthMm)` checks per type.

Do Nots
- Do not embed UI types or formatting.  
- Do not store inches, TPI, or mixed units.

Notes
- `ShaftSpec` hosts aggregate helpers: `coverageEndMm`, `freeToEndMm`, `maxOuterDiaMm`.
