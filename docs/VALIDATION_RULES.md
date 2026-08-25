# Validation Rules  
Version: v0.5.x
Last updated: 2026-08-24 — §5/§5.2/§6: the export gate now checks taper overlaps
(`blockingExportError()` runs a taper pass through `collidingIds()`, the carousel badge's own
predicate). Removed the stale "Taper overlaps never block export" claim; recorded the remaining
Thread↔Liner gap in §6. 2026-08-05 — removed a stale header claim that §3.3's taper-vs-body Ø mismatch
advisory compares a "physical face diameter (`taperFaceDiametersMm`)": that advisory was
**removed entirely 2026-07-26** by product decision and no such helper exists. §3.3's body
already records the removal correctly. 2026-07-24 — corrected two stale "`ShaftSpec.validate()` is dead code" claims
(§1.1, §3.1): it is not called by production code, but it **is** exercised by unit tests
(`ShaftSpecTest`, `SampleShaftAssetsTest` bundled-sample sanity checks) — test-only, kept
deliberately (confirmed in the 2026-07-24 dead-code sweep, not deleted). 2026-07-24 — §3.2/§3.3/§3.5/§4.3: the five warning rules flagged
"(planned — not yet implemented)" are now implemented in `ui/util/ComponentWarnings.kt`
(`bodyWarningMessages`, `taperWarningMessages`, `linerWarningMessages`, `specWarningMessages`).
All five are pure, non-blocking, and rules see only **stored** components (auto-bodies are
invisible to them); thresholds are tunable named constants pending Chris's review. 2026-07-24 — §3.3 noted that taper slope validation/derivation is inert at
`lengthMm <= 0` (guarded in `TaperRateAuto.kt` / `deriveTaperDiameters`; pinned by unit
tests). 2026-07-21 — reverted "explicit bodies are non-negotiable" (§5.1): bodies never collide, no hard-block on adds/moves over a body, plain bodies split around sacred components (keyed bodies protected); removed `bodyOverlapErrorMm`/`nonBodyOverlapErrorMm`/liner↔body negotiation references. 2026-07-18 — §2.3 numeric "safety filters" (NaN/Infinity/>100000 rejection) were never implemented; removed the false claim. Validation is not ViewModel-only (overlap/bounds checks live in `ui/util/StartOverlapValidation.kt`, called from Compose UI). Negative-start rejection is a dialog-level Submit gate, not a ViewModel rejection; `ShaftSpec.validate()` exists but is dead code. §6 export gate corrected to what `blockingExportError()` actually checks.

## Purpose
This document defines all validation behavior used by ShaftSchematic.  
Validation ensures data consistency, machining plausibility, and clean export conditions—without restricting legitimate edge-case layouts.

Validation is **not** performed only in the ViewModel. The ViewModel owns numeric parsing and
per-field clamping (e.g. length/diameter ≥ 0), but overlap/bounds validation lives in
`ui/util/StartOverlapValidation.kt` (`startOverlapErrorMm`, `collectAddWarnings`) and is called
directly from Compose UI code — `AddComponentDialogs.kt`, `ComponentCarousel.kt`,
`ShaftScreen.kt`. This is a deliberate exception to the general "UI performs no validation
logic" rule stated elsewhere in this doc set; treat `StartOverlapValidation.kt` as part of the
validation system regardless of which layer it happens to live in.

Neither the Layout Engine nor the Renderer performs validation.

---

# 1. Validation Categories

Validation is divided into two tiers:

## 1.1 Blocking Errors (Hard Fail)
These prevent:
- Saving a component
- Committing edits in dialogs
- Exporting to PDF

Examples:
- Negative lengths or diameters
- startFromAft < 0 (Add dialogs only — see the note below; existing components can be edited to
  a negative start via the carousel without the ViewModel rejecting it)
- endFromAft > overallLengthMm
- Missing taper parameters (SET/LET/taperRate insufficient)
- overallLengthMm < coverageEndMm

Blocking means **the dialog stays open** and the change is not accepted.

**Important caveat on `startFromAft < 0`:** this is enforced only as an **Add dialog Submit
button enabled-condition** (e.g. `AddComponentDialogs.kt`: `val ok = startMm >= 0f && …`) — a
UI-level gate on the *add* flow. The ViewModel's `update*()` functions (`updateBody`,
`updateTaper`, `updateThread`, `updateLiner` in `ShaftViewModel.kt`) do **not** reject or clamp
a negative `startFromAftMm` on commit; they only clamp `lengthMm`/diameter fields to
`max(0f, …)`. `ShaftSpec.validate()` (`model/ShaftSpec.kt`) implements the stricter bounds check
described in this document. Nothing in the app calls it in production, but it is **test-only** —
exercised by `ShaftSpecTest`/`SampleShaftAssetsTest` — and is kept deliberately; it is not dead
code.

---

## 1.2 Non-Blocking Warnings (Soft Issues)
These allow saving and exporting but are visually flagged.

Examples:
- Component overlaps
- Very small features (e.g., extremely short bodies)
- Zero-pitch threads
- Large diameter discontinuities between components
- Liner OD ≤ shaft body diameter
- Small free-to-end space (<10 mm)

Warnings appear in the component list or via icons; they do **not** block workflow.

---

# 2. Numeric Input Validation

Validation of raw input values occurs during commit-on-blur inside dialogs or number fields.

## 2.1 Accepted Input Patterns
- `"123"`
- `"123."`
- `".25"`
- `"0.5"`
- `""` → interpreted as **0**
- `"."` → interpreted as **0**

## 2.2 Invalid Input
If user commits:
- alphabetic text,
- multiple decimals,
- malformed numbers,

…then:
1. The change is **not committed**, and  
2. UI reverts to last valid committed value.

## 2.3 Numeric Safety Filter — Gap (advisory only)
**Sanity advisory (2026-08-24):** `ComponentWarnings.kt` now flags a component length >
`SANITY_MAX_COMPONENT_LENGTH_MM` (15,000 mm) or any diameter field > `SANITY_MAX_DIA_MM`
(1,000 mm) as a **non-blocking** warning ("Length/Diameter exceeds … — check for a typo"),
surfaced via the existing yellow carousel warning badge across bodies, tapers, threads, and
liners. Both thresholds are provisional, chosen without shop input — same posture as the §2.2
step-ratio/short-segment thresholds — and never clamp, round, or otherwise rewrite the typed
value (golden rule).

Beyond that advisory, **no blocking numeric safety filter exists anywhere in the codebase.**
This section previously claimed a
blocking rejection of `Float.NaN` / `Float.POSITIVE_INFINITY` / `Float.NEGATIVE_INFINITY` /
negative values, and a sanity-max rejection above `100000f`. Neither was ever built:
- `util/Parsing.kt`'s `parseToMm()`/`parseFractionOrDecimal()` are explicitly documented to be
  neutral — "do not clamp negatives or enforce ranges here" — and contain no NaN/Infinity/range
  checks.
- `NumericInputField` and `ShaftViewModel` contain no such checks either.

What actually happens instead:
- **Parse-or-revert** (§2.2): unparseable text is never committed; the field reverts to the
  last committed value. This incidentally screens out most ways to *type* a NaN/Infinity, but
  does nothing to bound magnitude or sign.
- **Per-field validators**: individual fields clamp specific values downstream (e.g.
  `ShaftViewModel.updateTaper/updateThread/updateLiner` clamp `lengthMm`/diameter
  fields to `max(0f, …)` on commit; `updateBody` delegates the same clamp to
  `ShaftSpec.withBodyAt`), but this is per-field clamping, not a general safety filter,
  and it does not cover every numeric field (notably `startFromAftMm` — see §3.1).

If a NaN/Infinity/huge value reaches the model through a non-UI path (e.g. a hand-edited saved
file), nothing in this codebase currently guards against it.

---

# 3. Component-Level Validation

All components share core validation constraints.

## 3.1 Shared Rules for All Segments
startFromAftMm >= 0
lengthMm >= 0
endFromAftMm <= overallLengthMm

These three rules are the **intended** shared contract, but only some are actually enforced as
a hard block today:
- `lengthMm >= 0` and the diameter fields **are** clamped on commit (`max(0f, …)`) — in
  `ShaftViewModel` for tapers/threads/liners, in `ShaftSpec.withBodyAt` (the model function
  `updateBody` delegates to) for bodies.
- `startFromAftMm >= 0` is **not** enforced by the ViewModel on update — `updateBody` /
  `updateTaper` / `updateThread` / `updateLiner` write `startMm` through unclamped. The only
  place this is gated is the **Add dialog's Submit button enabled-condition**
  (`AddComponentDialogs.kt`, e.g. `val ok = startMm >= 0f && …`), which blocks *adding* a new
  component with a negative start but does not stop an existing component from being edited to
  one via the carousel.
- `endFromAftMm <= overallLengthMm` is not enforced as a hard commit-time block either; see the
  overlap/bounds checks in §5 for what actually runs (`startOverlapErrorMm` in
  `ui/util/StartOverlapValidation.kt`).
- `model/ShaftSpec.kt` does define `fun ShaftSpec.validate(): Boolean` implementing the fuller
  bounds check described in this document. No production caller invokes it — it is **test-only**,
  exercised by `ShaftSpecTest` and `SampleShaftAssetsTest` (bundled-sample sanity checks) — and is
  kept deliberately (confirmed in the 2026-07-24 dead-code sweep, not deleted).

---

## 3.2 Body Validation
- diaMm ≥ 0 (blocking)
- diaMm may be 0 (degenerate visual case, but allowed)

Non-blocking warning:
- Zero-length body *(implemented)*
- Diameter discontinuity vs neighbors *(implemented, 2026-07-24)* — `bodyWarningMessages(spec, body)`
  in `ui/util/ComponentWarnings.kt`. Fires when a stored body's face abuts another stored body's
  face within `ADJACENCY_EPS_MM = 0.5f` mm (either end, either direction), both diameters are
  `> 0`, and `max(diaMm) / min(diaMm) > BODY_STEP_WARN_RATIO` (`1.5f`, strict — a ratio of exactly
  `1.5` is silent). Auto-bodies are not considered (stored `spec.bodies` only). Message: "Large Ø
  step vs adjacent body", shown on the affected body's carousel card (joined with any other
  warning for that card via `"; "`). `BODY_STEP_WARN_RATIO` and `ADJACENCY_EPS_MM` are named
  constants pending Chris's review.

---

## 3.3 Taper Validation
Required:
- lengthMm > 0
- startDiaMm ≥ 0
- endDiaMm ≥ 0

Keyway (taper-hosted feature):
- keywayLengthMm ≥ 0 (blocking)
- keywayLengthMm ≤ taper.lengthMm (blocking)
- Spoon is optional and non-blocking.
- Spoon is allowed only when a keyway exists:
	- If keywayLengthMm == 0, keywaySpooned must be false (blocking)

Missing keyway data is valid (all keyway fields may be 0/false).

Keyway (body-hosted feature — same rules referenced from the body's AFT/FWD end face):
- keywayWidthMm / keywayDepthMm / keywayLengthMm / keywayOffsetFromEndMm ≥ 0 (blocking, `Body.isValid`)
- keywayOffsetFromEndMm + keywayLengthMm ≤ body.lengthMm (blocking, `Body.isValid`)
- `keywayEnd` (AFT | FWD) selects the referenced face; offset 0 = open at that face.
- Spoon is optional, non-blocking, and ignored for floating keyways (offset > 0).

### Taper Rate Behavior
Superseded by the Auto/Manual rate-mode system — authoritative contract in the
`docs/contracts/TaperRate.md` and `docs/contracts/AddComponentDialogs.md`. In brief:
- **Auto mode** (default): rate computed from Length + SET + LET when all are real
  positive values; sentinels (`-1`, `0`) never fabricate a rate.
- **Manual mode**: required when one diameter is missing (derives the missing end;
  derived diameter must be ≥ 0); a manual rate disagreeing with complete geometry
  shows a **warning**, it is not silently ignored.
- Missing both SET and LET with no usable rate → **blocking**.
- Bare `1` is blocked as ambiguous; common-rate snapping uses a 3% tolerance
  (confirmed product decision).
- Slope validation/derivation (`autoTaperRate`, `manualTaperRateWarning`, and
  `manualTaperRateBlockingMessage`'s derive-prompt) is inert at `lengthMm <= 0` — no
  rate is computed or demanded for a zero/negative-length taper. Pure-syntax checks
  (e.g. rejecting an ambiguous bare `"1"`) are independent of length and still fire
  regardless. Same guard applies to `ShaftViewModel.deriveTaperDiameters`. Pinned by
  unit tests in `TaperRateAutoTest.kt`/`TaperRateTest.kt` (2026-07-24).

Non-blocking warnings:
- Extremely steep tapers *(planned — not yet implemented)*
- Very short segment (< 1 mm) — `taperWarningMessages(spec, taper)` in
  `ui/util/ComponentWarnings.kt`.
- **Removed (2026-07-26): taper-vs-body Ø mismatch advisory** ("Ø differs from adjacent
  body by >10%", implemented 2026-07-24, orientation-fixed 2026-07-25). Removed by user
  request: the mismatch is directly visible in the drawing, and the rule kept misfiring on
  FWD tapers even after the orientation fix because the two taper storage paths (Add dialog
  stores `startDiaMm = SET` regardless of shaft half; carousel edit stores the pair
  x-ordered) still disagree — see the open renderer/storage orientation item in `TODO.md`
  §2.3. Do not reintroduce this advisory without resolving that discrepancy first. A
  regression test in `ComponentWarningsTest.kt` pins that a large taper-vs-body Ø
  difference produces no warning.

---

## 3.4 Thread Validation
Required:
- majorDiaMm ≥ 0 (blocking)
- lengthMm ≥ 0 (blocking)

### Pitch ↔ TPI Rules
Normalization rules:
- If pitch present & tpi missing → compute tpi
- If tpi present & pitch missing → compute pitch
- If both present → leave unchanged, but validate consistency
- If neither present → blocking

Invalid cases: no NaN/Infinity/negative-value guard actually exists for this conversion (see
§2.3 — there is no numeric safety filter anywhere in the codebase). `Threads.normalized()`
(`model/Threads.kt`) computes the missing side unconditionally when the other is `> 0f`; a
degenerate input that produced NaN/Infinity would pass through uncaught.

Non-blocking warnings:
- pitchMm = 0 (thread rendered flat, allowed)

---

## 3.5 Liner Validation
Required:
- odMm ≥ 0 (blocking)
- endFromAftMm ≤ overallLengthMm (blocking)

Non-blocking warnings:
- odMm < underlying shaft diameter *(implemented, 2026-07-24)* —
  `linerWarningMessages(spec, liner)` in `ui/util/ComponentWarnings.kt`. Fires when the liner's
  axial span has a positive-length overlap (`overlapLenMm > 0`) with a stored body whose
  `diaMm > 0`, and `liner.odMm < body.diaMm − 0.001f` (the `0.001f` slack absorbs float
  round-trip noise; anything at or above it is silent — not a strict-inequality boundary in the
  same sense as the ratio/fraction thresholds elsewhere). Auto-bodies are not considered (stored
  `spec.bodies` only). Message: "Liner OD smaller than shaft Ø beneath it", shown on the liner's
  carousel card (joined with any other warning for that card via `"; "`).
- Very thin liner vs body diameter *(planned — not yet implemented)*

---

## 3.6 Coupler Bolt Slot Validation

Coupler bolt slots are a **pure reference overlay** and are validated in isolation.
Unlike sacred components, they are **not** bounded against `overallLengthMm` (the
shared `endFromAftMm <= overallLengthMm` rule in §3.1 does not apply) — much as
excluded threads are skipped in envelope checks.

`isValid` requires:
- `holeDiaMm ≥ 0`, `spacingMm ≥ 0`, `depthMm ≥ 0` (all fields non-negative) (blocking)
- `count ≥ 1` (blocking)
- All cutout centers lie within the shaft (`0 ≤ each center`, and the row does not
  run past the shaft) (blocking)

`depthMm` is ignored when `through = true`.

Coupler bolt slots are **excluded from all collision detection** (`collisionGroup()`
→ null); they never produce overlap warnings and never block another component.

---

# 4. ShaftSpec-Level Validation

### 4.1 Global Requirements
- overallLengthMm ≥ 0 (blocking)
- coverageEndMm ≤ overallLengthMm (blocking)

### 4.2 Full-Spec Blocking Errors
- Any component in an invalid state
- Invalid numeric values
- Invalid taper derivation parameters
- Unbounded thread or liner

### 4.3 Full-Spec Non-Blocking Warnings
- Component overlaps (machinist may intend) *(implemented)*
- Rapid diameter changes *(planned — not yet implemented)*
- Tiny segments (e.g., < 1 mm) *(implemented, 2026-07-24 — spec-level count)* —
  `specWarningMessages(spec)` in `ui/util/ComponentWarnings.kt` counts stored components with
  length in `(0, SHORT_SEGMENT_MM]` (`SHORT_SEGMENT_MM = 1f` mm) across bodies, tapers, liners,
  and non-excluded threads (excluded threads are skipped, matching §3.1/§5.2's treatment of
  them as outside the envelope), and emits `"$tiny segments shorter than 1 mm"` when the count
  is `> 0`. This is in addition to the existing **component-level** short-segment check already
  folded into `bodyWarningMessages`/`taperWarningMessages`/`linerWarningMessages`/
  `threadWarningMessages` (same `SHORT_SEGMENT_MM` threshold; all four are list-returning, so
  every applicable warning surfaces on the card).
  `specWarningMessages` is pure and unit-tested and renders as a dismissable banner above the
  component carousel on the Schematic tab (`ui/screen/SpecWarningBanner.kt`), 2026-08-25.
- Free-to-end space < 10 mm *(implemented)*
- Zero-body coverage (no explicit bodies in `ShaftSpec`; auto bodies are derived and do not
  satisfy this warning) *(implemented, 2026-07-24 — surfaced 2026-08-25)* —
  `specWarningMessages(spec)` emits `"No explicit bodies — shaft body is all auto-fill"` when
  `spec.bodies` is empty and at least one taper, liner, or non-excluded thread exists
  (`hasAnyNonBodyComponent`). Like the tiny-segment count above, this is pure and unit-tested
  and shares the same Schematic-tab banner, 2026-08-25.

---

# 5. Overlap Rules

Overlaps **never** block an edit or a commit — a component may always be moved into an overlap
and the document saved. They do gate **export**: see §5.2 and §6 for which pairs.

### 5.1 Bodies do not collide (reverted 2026-07-21)

Bodies are the shaft's fluid base material / fillers — **not** colliders. (The "explicit
bodies are non-negotiable" experiment was reverted because it raised false collision warnings
on normal drafts.) `collidingIds()` checks only taper/thread/liner pairs (sacred-vs-sacred),
never bodies. A body legitimately runs under a liner and up against a taper; the resolve layer
(`subtractBodiesAgainstNonBodies`) trims the *drawn* body around those components, so a stored
body span crossing them is not a conflict.

There is **no** hard-block on adding or moving a component over a body. Adding a
taper/thread/liner over a plain body **splits** it (`splitBodiesAround`) as it always did; a
body that has a keyway is never split (it stays one whole card and is trimmed for drawing). The
removed `bodyOverlapErrorMm` / `nonBodyOverlapErrorMm` helpers and the liner↔body "boundary
negotiation" (`linerBodyBoundaryAdjust` / `updateLinerWithBodyBoundary`) no longer exist.

No body pair is checked for collision — the checks below are all sacred-vs-sacred.

### 5.2 Sacred-Component Overlaps — Warning Shown

The following pairs are checked by `collidingIds()`. A warning ("Overlaps another component") is
shown in the carousel card when detected, and the same set gates the toolbar/tab Export buttons
via `exportPdfGate()`; the taper pairs additionally block the schematic export through
`blockingExportError()` (§6):
- Taper ↔ Taper
- Taper ↔ Thread (non-excluded only)
- Taper ↔ Liner
- Thread ↔ Thread (non-excluded only)
- Thread ↔ Liner
- Liner ↔ Liner

Excluded threads (`excludeFromOAL = true`) are skipped in all collision checks — they sit outside the shaft envelope and their position is always derived.

Coupler bolt slots are likewise skipped in all collision checks (`collisionGroup()` → null). As pure reference overlays they may sit over any component without warning.

Reasoning: marine machining workflows often use stacked geometry and nested regions; overlaps are flagged as warnings only, never blocking.

### 5.3 Add-time pre-submit warnings (`collectAddWarnings`)

When the user taps **Add** in the Taper, Liner, or Thread dialog, `collectAddWarnings()`
(`ui/util/StartOverlapValidation.kt`) runs before the component is committed. If
collisions or bounds violations are found, a confirmation dialog appears
("Add Anyway?" / "Cancel") — the add is **never silently blocked**.

| Check | Condition | Applies when |
|-------|-----------|--------------|
| Bounds | `start < 0` or `end > OAL` | OAL is manual (not auto) |
| Taper collision | overlaps any existing Taper | always |
| Thread collision | overlaps any existing non-excluded Thread | always |
| Liner collision | overlaps any existing Liner | always |
| Body overlap | — | **never** — a body is fluid base material; a sacred add over a plain body just splits it (`splitBodiesAround`) |
| Excluded thread | — | **skipped** (outside shaft span by design) |
| Coupler bolt slot | — | **never** (`collisionGroup()` → null) |

---

# 6. Export Validation (PDF)

Before exporting:
1. `blockingExportError(spec)` (`ui/nav/PdfExportRoute.kt`) runs — **not** a general
   "ViewModel runs full validation" pass (there is no such single entry point; see the Purpose
   section above and §3.1).
2. If it returns a non-null message → cancel export, show a blocking dialog with that reason.
3. If it returns `null` → export continues, regardless of any outstanding non-blocking warnings.

`blockingExportError()` checks three component kinds:
- **Non-excluded Threads** (`excludeFromOAL = false`), via `startOverlapErrorMm()`
  (`ui/util/StartOverlapValidation.kt`): pairwise Thread↔Thread overlap, the "thread must be at
  a shaft end" rule, plus `start ≥ 0`. Excluded threads are skipped — they intentionally sit at
  negative/OAL+ `startFromAftMm` outside the envelope.
- **Liners**, via `startOverlapErrorMm()`: pairwise Liner↔Liner overlap, plus `start ≥ 0`.
- **Tapers**, via `collidingIds()` (`model/ShaftSpecExtensions.kt`) — Taper↔Taper,
  Taper↔Thread (non-excluded), and Taper↔Liner, per §5.2 — plus the `start ≥ 0` guard that
  `startOverlapErrorMm()` still contributes for a taper. `startOverlapErrorMm()` has no
  collision group for tapers (`collisionGroup()` → null), so the overlap answer deliberately
  comes from `collidingIds()`: that is the same predicate behind the taper card's blocking
  badge, so the gate and the badge cannot disagree.

It does **not** check Bodies or Coupler Bolt Slots. Bodies are fillers, not collision
participants (§5.1) — a taper or liner crossing a stored body span is normal, the resolve layer
trims the drawn body around it, and `collidingIds()` excludes bodies, so it never blocks export.
Coupler bolt slots are reference overlays outside the OAL envelope and never gate export
(`collisionGroup()` → null, consistent with §3.6/§5.2).

**Known gap:** the thread and liner passes go through `startOverlapErrorMm()`, which compares
each kind only against its own kind — a Thread↔Liner overlap is flagged by `collidingIds()`
(§5.2) and by the button-level `exportPdfGate()`, but is not caught by `blockingExportError()`
itself.

PDF export does not interpret warnings; UI handles presentation.

---

# 7. Validation Invariants (Required)

1. Validation occurs **only** before state update or export.
2. Renderer/Layout must never throw validation errors.
3. UI performs overlap/bounds validation directly (`ui/util/StartOverlapValidation.kt`, called
   from `AddComponentDialogs.kt`/`ComponentCarousel.kt`/`ShaftScreen.kt`) in addition to string
   formatting — this is a deliberate, documented exception (see the Purpose section and §3.1),
   not a violation to fix.
4. Warnings do not affect behavior, only UI hints.
5. Blocking errors from `startOverlapErrorMm` prevent the Add dialog's Submit and PDF export
   (§6); they do not retroactively block edits made after a component already exists (§3.1).
6. Derivation (taper rate, pitch/tpi) is validated before application.

---

# 8. Debugging Checklist

When you see unexpected validation behavior, check in order: numeric parsing →
taper derivation (most common failure) → thread pitch/TPI conversion →
`overallLengthMm` vs `coverageEndMm` → UUID stability across edited components →
dialogs committing partial values.

---

# 9. Summary

Validation ensures:
- Consistency of geometric data  
- Prevention of impossible machining configurations  
- A clean PDF export state  
- Freedom for machinists to intentionally create overlaps or complex geometry  

Blocking errors prevent data corruption.  
Warnings inform but do not restrict the workflow.

This document defines the authoritative validation system used across all components and the entire ShaftSpec.