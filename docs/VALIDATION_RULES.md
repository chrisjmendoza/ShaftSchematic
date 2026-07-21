# Validation Rules  
Version: v0.5.x
Last updated: 2026-07-18 — §2.3 numeric "safety filters" (NaN/Infinity/>100000 rejection) were never implemented; removed the false claim. Validation is not ViewModel-only (overlap/bounds checks live in `ui/util/StartOverlapValidation.kt`, called from Compose UI). Negative-start rejection is a dialog-level Submit gate, not a ViewModel rejection; `ShaftSpec.validate()` exists but is dead code. §6 export gate corrected to what `blockingExportError()` actually checks.

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
described in this document, but it is **dead code** — nothing in the app calls it.

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

## 2.3 Numeric Safety Filter — Gap (nothing implemented)
**No numeric safety filter exists anywhere in the codebase.** This section previously claimed a
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
  `ShaftViewModel.updateBody/updateTaper/updateThread/updateLiner` clamp `lengthMm`/diameter
  fields to `max(0f, …)` on commit), but this is per-field clamping, not a general safety filter,
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
- `lengthMm >= 0` and the diameter fields **are** clamped on commit in `ShaftViewModel`
  (`max(0f, …)`).
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
  bounds check described in this document, but it is **dead code** — no caller invokes it
  anywhere in the app.

---

## 3.2 Body Validation
- diaMm ≥ 0 (blocking)
- diaMm may be 0 (degenerate visual case, but allowed)

Non-blocking warning:
- Zero-length body *(implemented)*
- Diameter discontinuity vs neighbors *(planned — not yet implemented)*

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
in-source `TaperRate.md` and `AddComponentDialogs.md`. In brief:
- **Auto mode** (default): rate computed from Length + SET + LET when all are real
  positive values; sentinels (`-1`, `0`) never fabricate a rate.
- **Manual mode**: required when one diameter is missing (derives the missing end;
  derived diameter must be ≥ 0); a manual rate disagreeing with complete geometry
  shows a **warning**, it is not silently ignored.
- Missing both SET and LET with no usable rate → **blocking**.
- Bare `1` is blocked as ambiguous; common-rate snapping uses a 3% tolerance
  (confirmed product decision).

Non-blocking warnings:
- Extremely steep tapers *(planned — not yet implemented)*
- Large mismatch with adjacent body diameter *(planned — not yet implemented)*

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
- odMm < underlying shaft diameter *(planned — not yet implemented)*
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
- Tiny segments (e.g., < 1 mm) *(planned — not yet implemented)*
- Free-to-end space < 10 mm *(implemented)*
- Zero-body coverage (no explicit bodies in `ShaftSpec`; auto bodies are derived and do not satisfy this warning) *(planned — not yet implemented)*

---

# 5. Overlap Rules

Overlaps **never** block validation.

### 5.1 Explicit bodies are non-negotiable (2026-07-21)

Explicit (stored) bodies are first-class, rigid components — **not** fillers. `collidingIds()`
flags an explicit body overlapping a taper, non-excluded thread, liner, or another body
(red card + blocked PDF export). Nothing may be added or moved onto an explicit body:
`bodyOverlapErrorMm` hard-blocks the Add dialogs and the carousel start/length fields (and
`nonBodyOverlapErrorMm` blocks a *body* being moved onto any other component). Because
overlapping adds are refused, explicit bodies are never split.

Auto-bodies (derived at resolve time, never stored) stay fluid and flow around every
component — they never reach `collidingIds` and are how a shaft is shaped.

The one exception is the **liner ↔ body boundary negotiation** (`linerBodyBoundaryAdjust`):
a liner length edit at a shared edge with an abutting explicit body offers to shorten/grow
that body (confirm/cancel) instead of hard-blocking.

Checked body pairs:

- Body ↔ Taper
- Body ↔ Liner
- Body ↔ Thread (non-excluded only; excluded threads live outside the envelope)
- Body ↔ Body

### 5.2 Sacred-Component Overlaps — Warning Shown

The following pairs are also checked by `collidingIds()`. A warning ("Overlaps another component") is shown in the carousel card when detected:
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
| Body overlap (hard block) | overlaps any explicit Body | always — refuses the add (`bodyOverlapErrorMm`), not "Add Anyway" |
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

`blockingExportError()` actually checks only two component kinds, both via
`startOverlapErrorMm()` (`ui/util/StartOverlapValidation.kt`):
- **Non-excluded Threads** (`excludeFromOAL = false`): pairwise Thread↔Thread overlap, plus
  `start ≥ 0`. Excluded threads are skipped — they intentionally sit at negative/OAL+
  `startFromAftMm` outside the envelope.
- **Liners**: pairwise Liner↔Liner overlap, plus `start ≥ 0`.

It does **not** check Bodies, Tapers, or Coupler Bolt Slots at all. In particular, **Taper
overlaps never block export** — they are only surfaced as a non-blocking warning via
`collidingIds()` (§5.2); a shaft with two overlapping tapers exports successfully. Coupler bolt
slots are reference overlays outside the OAL envelope and never gate export (`collisionGroup()`
→ null, consistent with §3.6/§5.2).

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