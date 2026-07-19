Model Conventions
-----------------

Layer: Model  
Purpose: Shared expectations across Body, Taper, ThreadSpec, Liner, CouplerBoltSlot, Segment.

Version: v0.4 (2026-07-18)

Invariants
- All fields are **Float mm** unless stated otherwise.  
- `startFromAftMm + lengthMm` gives end; for most components this must be ≤ `overallLengthMm`.  
- Thread pitch/TPI are **both canonical stored fields**: `Threads.pitchMm` (mm/turn) and
  `Threads.tpi: Float?` (imperial) live side by side in the model. `Threads.normalized()`
  populates whichever is missing from the other (`tpi` present → `pitchMm = 25.4/tpi`;
  `pitchMm` present → `tpi = 25.4/pitchMm`) and is applied on decode (`ShaftDocCodec.decode`
  calls `.normalized()`). Other units (in, mixed) are still forbidden — only mm and TPI,
  the two canonical thread-pitch representations, are stored.
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

`CouplerBoltSlot` — reference-only feature (see `CouplerBoltSlot.md`)
- One axial **row** of radial bolt cutouts. `startFromAftMm` = the aft-most cutout center; `count`, `spacingMm` describe the row; `holeDiaMm`, `through`/`depthMm` the hole.
- `SlotAuthoredReference` (AFT / **FWD** default) is UI-only; canonical `startFromAftMm` is always stored AFT-face. FWD → `startFromAftMm = OAL − enteredFwd − (count−1)·spacingMm`.
- **Excluded from OAL/coverage**: `coverageEndMm` and `maxOuterDiaMm` ignore slots. Its `lengthMm` (derived footprint) exists only for layout/ordering, never for OAL.
- Never split bodies, never collide. `isValid(overallLengthMm)` checks non-negative fields, `count ≥ 1`, and that all cutout centers fall within `0..OAL`.

Responsibilities
- Keep data classes passive (no business logic).  
- Provide `isValid(overallLengthMm)` checks per type (skip for excluded threads).

Do Nots
- Do not embed UI types or formatting.  
- Do not store inches, or any unit besides mm and thread TPI (the two canonical
  thread-pitch fields); no other imperial fields belong in the model.
- Do not clamp or mutate an excluded thread's `startFromAftMm` to keep it within 0..OAL — that would destroy the intended rendering position.

Notes
- `ShaftSpec` hosts aggregate helpers: `coverageEndMm`, `freeToEndMm`, `maxOuterDiaMm`.
- `syncExcludedThreadPositions()` must be called after any OAL change or excluded-thread topology change.

Change Log
----------
**v0.4 (2026-07-18)**
- Corrected thread-pitch convention: `pitchMm` and `tpi` are both canonical stored
  fields (kept in sync by `Threads.normalized()`), not "pitchMm stored, TPI UI-only."

**v0.3 (2026-07-11)**
- Added `CouplerBoltSlot` reference-feature conventions (excluded from OAL/coverage/collision; FWD-default authoring reference).

**v0.2 (2026-06-18)**
- Documented that excluded-thread `startFromAftMm` may be negative or ≥ OAL.
- Added `LinerAuthoredReference` semantics and taper direction convention notes.

**v0.1 (2025-10-04)**
- Initial conventions document.
