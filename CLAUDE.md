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

### Explicit bodies are non-negotiable components
A stored (`ShaftSpec.bodies`) body is explicit and rigid — like a taper/liner, **not** a
filler. It participates in `collidingIds()` (red card + blocked PDF export), and nothing
may be added or moved onto it (`bodyOverlapErrorMm` / `nonBodyOverlapErrorMm` hard-block
the Add dialogs and carousel start/length fields). Explicit bodies are **never split** —
overlapping adds can't land, so no fragmentation happens. Auto-bodies (derived, unstored)
stay fluid and flow around everything. Do not re-add bodies to `splitBodiesAround` on
overlap, and do not exclude bodies from `collidingIds` again. The one exception is the
**liner ↔ body boundary negotiation** (`linerBodyBoundaryAdjust` /
`updateLinerWithBodyBoundary`): a liner length edit at a shared body edge offers to
shorten/grow that body instead of blocking. See `docs/COMPONENT_CONTRACT.md`.

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
