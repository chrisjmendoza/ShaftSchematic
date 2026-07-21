# ShaftSchematic – Claude Code Instructions

## Project overview
Android app (Kotlin / Jetpack Compose) for designing marine propulsion shafts.
All model values are **canonical millimeters (mm)**. Unit conversion (mm ↔ in) happens
only at the UI edge for display and input — never in the model, ViewModel, or renderer.

## Docs
Detailed contracts live in `app/src/main/java/com/android/shaftschematic/docs/`.
Read the relevant doc before editing a subsystem. Key files:
- `ShaftScreen.md` — overall screen contract, commit-on-blur rule, unit edge rule
- `AddComponentDialogs.md` — add-dialog parity rules (mirror carousel cards)
- `FreeToEndBadge.md` — badge visibility invariants
- `NumberField.md` — numeric input field contract
- `ShaftViewModel.md` — ViewModel responsibilities and state ownership
- `Model_Conventions.md` — model layer rules
- `CouplerBoltSlot.md` — coupler bolt slot feature contract (reference-only cutouts)

## Critical invariants — do not remove or weaken these

### Add dialogs must mirror carousel cards
Every control that exists in a component's **carousel edit card** must also appear in
its **Add dialog** under the same conditions. Removing a control from one without removing
it from the other is a bug.

Specifically:
- **Thread excluded from OAL** (`countInOal = false`): `AddThreadDialog` must show
  "Thread end: AFT | FWD" chips and hide the Start field — same as the carousel card
  (`ComponentCarousel.kt`, `ResolvedThread` branch, `!includeInOal` block).
- **Liner AFT/FWD reference**: `AddLinerDialog` must show "Measure From: AFT | FWD" chips.
- **Body keyway**: `AddBodyDialog` and the explicit-body carousel card must both expose
  the keyway section (KW from AFT | FWD chips, W × D, L, offset, spooned toggle). The
  auto-body card intentionally omits it (auto-bodies can't host keyways until promoted).
- **Keyways 180° apart**: the spec-level toggle appears on keyway-bearing cards when the
  shaft has ≥ 2 keyways, and in `AddBodyDialog`/`AddTaperDialog` when adding would reach
  ≥ 2 (≥ 1 existing + this dialog's keyway defined). Same condition on both surfaces.
- **Taper AFT/FWD reference**: `AddTaperDialog` must show AFT/FWD direction chips.
- **Coupler bolt slot**: `AddCouplerBoltSlotDialog` and the `ResolvedCouplerBoltSlot`
  carousel card must both expose Measure From (AFT | FWD), hole Ø, count, spacing (only
  when count > 1), through/blind toggle + depth (only when blind). The card additionally
  has the deferred "show dimension rail" toggle.

### Coupler bolt slots are reference features
Coupler bolt slots (`ShaftSpec.couplerBoltSlots`) are radial cutouts drawn on the shaft
but they **never** affect overall length (`coverageEndMm` ignores them), **never** split
bodies, and **never** collide with other components (`collisionGroup() → null`). Do not
add them to `coverageEndMm`, `ensureOverall`, body-split/merge, or overlap validation.
They are resolved as `ResolvedCouplerBoltSlot` *after* body resolution so they stay out
of auto-body/subtraction geometry. See `docs/CouplerBoltSlot.md`.

### Runout readings are reference features
Per-station runout readings (`RunoutReadings` in the doc envelope — a TIR value + high-spot
clock marker per bubble) are **reference-only**, same posture as coupler bolt slots and wear
spots. They **never** affect `coverageEndMm`/OAL, body resolution, collision, or the
Free-to-End badge, and live outside `ShaftSpec`. Both fields are optional; a sheet exports
fine with neither. Keyed by `(componentId, stationIndex)` with render-layer orphan handling
(a reading whose station no longer exists is simply not drawn). The value + high-spot marker
and the keyway cutout must be drawn **identically in both bubble draw sites** —
`RunoutRoute.drawRunoutMarkers` (canvas) and `RunoutPdfComposer.drawPlacedBubbles` (PDF).
Pure clock/hit-test math lives in `geom/RunoutReadingMath.kt` (shared, no `pdf → ui` dep);
value formatting in `util/RunoutValueFormat.kt`. See `docs/RunoutSheet.md` (Runout Bubble
Editor) and `docs/RunoutBubbleEditor_PLAN.md`.

### Numeric input commit behavior
`NumericInputField` only calls `onCommit` on blur **if the value changed** since focus
was gained. A tap-and-leave with no edit must be a no-op. This prevents spurious
auto-body promotion and unnecessary ViewModel updates. See `NumberField.md`.

### Auto-body promotion
Auto-body cards in the carousel (`ResolvedComponentSource.AUTO`) promote to real bodies
only on an **explicit user action**: editing one of their fields, or ticking the
**"Make editable body"** checkbox. Blur without a value change must not trigger
`promoteIfNeeded()`. Both paths guard on the same `promoted` state so promotion fires
once. See `ComponentCarousel.kt`.

### Bodies are fillers, not collision participants
Bodies (stored `ShaftSpec.bodies`) are the shaft's fluid base. A body legitimately runs
**under a liner** (a sleeve over the shaft) and **up against a taper**; the resolve layer
(`subtractBodiesAgainstNonBodies`) trims the *drawn* body around those components, so a
*stored* body span that crosses them is **not** a conflict. Therefore bodies are
**excluded from `collidingIds()`** — do not re-add them. (An earlier "non-negotiable
bodies" experiment flagged those normal overlaps as errors and referenced bodies by a
stored-list index that didn't match the drawn cards — false "Overlaps Body N" warnings.
Reverted 2026-07-21.) Adding a taper/thread/liner over a body **splits** it as before
(`splitBodiesAround`), **except** a body that has a keyway, which is never fragmented
(light protection — it stays one whole card, keyway intact). On delete, `mergeBodiesAround`
rejoins flanking fragments but **never merges across a component still occupying the gap**
(that would manufacture a long phantom body).

### Free-to-End badge suppression
The badge is hidden when there are no precision components (tapers, non-excluded threads,
liners) and the shaft is not oversized. With only bodies, auto-bodies visually fill the
remainder, so the badge value would always mislead. See `FreeToEndBadge.md`.

### OAL field
The OAL field calls `onSetOverallLengthMm` on **every keystroke** in manual mode (not
just on blur). This is intentional — the preview updates live. Do not change this to
commit-on-blur only.

## Commit policy
Do **not** auto-commit. The user reviews changes before every commit.
