Model Conventions
-----------------

Layer: Model  
Purpose: Shared expectations across Body, Taper, ThreadSpec, Liner, Segment.

Version: v0.2 (2026-06-18)

Invariants
- All fields are **Float mm** unless stated otherwise.  
- `startFromAftMm + lengthMm` gives end; for most components this must be ≤ `overallLengthMm`.  
- Thread pitch stored as **pitchMm**; TPI only at UI edge.
- **Exception — excluded threads:** `Threads` with `excludeFromOAL = true` are placed outside the 0..OAL shaft span by `syncExcludedThreadPositions()`. Their `startFromAftMm` will be **negative** for AFT-end threads (`–lengthMm`) or equal to `overallLengthMm` for FWD-end threads. Do not validate excluded-thread positions against `overallLengthMm`.

`LinerAuthoredReference` (enum: AFT / FWD)
- Stored on `Liner.authoredReference`. Records which end the user measured from when they added the liner.
- `AFT` (default) → user gave an AFT-face start; length extends FWD.
- `FWD` → user gave a FWD-face start; the UI computes the AFT start as `OAL − startFwd − length`.
- The field is UI-only metadata; the model always stores the canonical AFT start after conversion.

`LinerAuthoredReference` on `Taper`
- `Taper.authoredReference` mirrors `Liner.authoredReference` — same semantics: AFT (default) or FWD.
- The carousel edit card uses this to label and convert the Start input; the canonical `startFromAftMm` is always stored AFT-face.
- When a user selects "FWD" in `AddTaperDialog`, SET and LET are swapped before submission so the model's `startDiaMm/endDiaMm` pair is always stored AFT → FWD.

Responsibilities
- Keep data classes passive (no business logic).  
- Provide `isValid(overallLengthMm)` checks per type (skip for excluded threads).

Do Nots
- Do not embed UI types or formatting.  
- Do not store inches, TPI, or mixed units.
- Do not clamp or mutate an excluded thread's `startFromAftMm` to keep it within 0..OAL — that would destroy the intended rendering position.

Notes
- `ShaftSpec` hosts aggregate helpers: `coverageEndMm`, `freeToEndMm`, `maxOuterDiaMm`.
- `syncExcludedThreadPositions()` must be called after any OAL change or excluded-thread topology change.

Change Log
----------
**v0.2 (2026-06-18)**
- Documented that excluded-thread `startFromAftMm` may be negative or ≥ OAL.
- Added `LinerAuthoredReference` semantics and taper direction convention notes.

**v0.1 (2025-10-04)**
- Initial conventions document.
