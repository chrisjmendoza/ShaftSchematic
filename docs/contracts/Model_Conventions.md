Model Conventions
-----------------

Layer: Model  
Purpose: Shared expectations across Body, Taper, ThreadSpec, Liner, CouplerBoltSlot, Segment.

Version: v0.6 (2026-08-24)

Invariants
- All fields are **Float mm** unless stated otherwise.  
- `startFromAftMm + lengthMm` gives end; for most components this must be ≤ `overallLengthMm`.  
- Thread pitch/TPI are **both canonical stored fields**: `Threads.pitchMm` (mm/turn) and
  `Threads.tpi: Float?` (imperial) live side by side in the model. `Threads.normalized()`
  populates whichever is missing from the other (`tpi` present → `pitchMm = 25.4/tpi`;
  `pitchMm` present → `tpi = 25.4/pitchMm`) and is applied on decode (`ShaftDocCodec.decode`
  calls `.normalized()`). Other units (in, mixed) are still forbidden — only mm and TPI,
  the two canonical thread-pitch representations, are stored.
- `Threads.metricDesignation: String?` is the **third** thread-spec field and the only one
  that is text: an ISO metric designation authored by the user (`M20×2.5`). It is not a unit
  and not geometry — `majorDiaMm`/`pitchMm` remain the canonical mm values, parsed from the
  designation once at entry (`util/ThreadDesignation.kt`) and never re-derived from it in the
  model. Its presence marks the thread metric-mode: the printed spec is the designation
  verbatim (a designation converted to decimal inches stops meaning anything), and the
  display layer resolves that thread to mm. `null` = imperial, the pre-existing behavior.
- **Exception — excluded threads:** `Threads` with `excludeFromOAL = true` are placed outside the 0..OAL shaft span by `syncExcludedThreadPositions()`. Their `startFromAftMm` will be **negative** for AFT-end threads (`–lengthMm`) or equal to `overallLengthMm` for FWD-end threads. Do not validate excluded-thread positions against `overallLengthMm`.

`LinerAuthoredReference` (enum: AFT / FWD)
- Stored on `Liner.authoredReference`. Records which end the user measured from when they added the liner.
- `AFT` (default) → user gave an AFT-face start; length extends FWD.
- `FWD` → user gave a FWD-face start; the UI computes the AFT start as `OAL − startFwd − length`.
- The field is UI-only metadata; the model always stores the canonical AFT start after conversion.

`LinerAuthoredReference` on `Taper`
- `Taper.authoredReference` mirrors `Liner.authoredReference` — same semantics: AFT (default) or FWD.
- The carousel edit card uses this to label and convert the Start input; the canonical `startFromAftMm` is always stored AFT-face.
- `AddTaperDialog` passes the chip through (`onSubmit → ShaftScreen.onAddTaper → ShaftRoute → ShaftViewModel.addTaperAt(reference = …)`), so a taper added "measure from FWD" reopens in that frame instead of falling back to AFT with a converted Start.

Taper `startDiaMm`/`endDiaMm` are **x-ordered, SET faces the nearer shaft end**
- Storage is positional: `startDiaMm` is the diameter at the AFT-most face, `endDiaMm` at the FWD-most face. SET/LET are display labels.
- Which face carries the Small End is decided by the taper's **physical half** — `classifyTaperSideByMidpoint` (`ui/input/TaperSetLetMapping.kt`), midpoint ≤ OAL/2 → SET at the start face. Every writer and reader shares that one rule: the Add dialog's submit order (`taperAddDiameterOrder`), rate derivation (`ShaftViewModel.taperSmallEndAtStart` → same function), the carousel's labels (`taperSetLetMapping`), the renderer's trapezoid, and the keyway's SET-face reference.
- The measure-from chip is **not** that signal — it says where the Start was measured from, nothing about which half the taper lands in. Keying the swap on it stores SET at the wrong face whenever the two disagree.
- At add/edit time the half is judged against `oalAfterTaperAddMm(…)`: the OAL the shaft carries **after** the change (auto-OAL grows to cover the span; a manual OAL stands). The pre-add OAL is stale — on a blank shaft it is 0.
- Documents written before this rule are **not** repaired on decode: a stored reversed pair loads exactly as saved (golden rule).

`CouplerBoltSlot` — reference-only feature (see `CouplerBoltSlot.md`)
- One axial **row** of radial bolt cutouts. `startFromAftMm` = the aft-most cutout center; `count`, `spacingMm` describe the row; `holeDiaMm`, `through`/`depthMm` the hole.
- `SlotAuthoredReference` (AFT / **FWD** default) is UI-only; canonical `startFromAftMm` is always stored AFT-face. FWD → `startFromAftMm = OAL − enteredFwd − (count−1)·spacingMm`.
- **Excluded from OAL/coverage**: `coverageEndMm` and `maxOuterDiaMm` ignore slots. Its `lengthMm` (derived footprint) exists only for layout/ordering, never for OAL.
- Never split bodies, never collide. `isValid(overallLengthMm)` checks non-negative fields, `count ≥ 1`, and that every cutout's full footprint (center ± half hole Ø) falls within `0..OAL` (edges checked, not just centers).

Responsibilities
- Keep data classes passive (no business logic).  
- Provide `isValid(overallLengthMm)` checks per type (skip for excluded threads).

Do Nots
- Do not embed UI types or formatting.  
- Do not store inches, or any unit besides mm and thread TPI (the two canonical
  thread-pitch fields); no other imperial fields belong in the model.
- Do not clamp or mutate an excluded thread's `startFromAftMm` to keep it within 0..OAL — that would destroy the intended rendering position.

Notes
- `ShaftSpec` hosts aggregate helpers: `coverageEndMm`, `freeToEndMm`, `maxOuterDiaMm`,
  `oalIsManualOnLoad` (the single load-time OAL-mode predicate — see
  `docs/contracts/OverallLength.md`).
- `syncExcludedThreadPositions()` must be called after any OAL change or excluded-thread topology change.

Change Log

**v0.6 (2026-08-24)**
- Added `oalIsManualOnLoad` to the aggregate-helper list (single load-time OAL-mode predicate).
- Corrected the coupler-slot `isValid` description: hole edges are checked, not just centers.

**v0.5 (2026-08-06)** — entry not recorded at the time; see git history.
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
