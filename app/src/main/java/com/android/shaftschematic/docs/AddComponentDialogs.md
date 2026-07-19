# AddComponentDialogs Contract (v1.4, 2026-07-18)

## Purpose
Composable dialogs for adding new components: `AddBodyDialog`, `AddLinerDialog`,
`AddThreadDialog`, `AddTaperDialog`, `AddCouplerBoltSlotDialog`. Each dialog is the
**add-time counterpart** to the component's carousel edit card in `ComponentCarousel.kt`.

Default field values are seeded per `Defaults.md` (`ui/config/AddDefaultsConfig.kt`
+ `SessionAddDefaults`).

## Entry point — InlineAddChooserDialog
The chooser (`ui/dialog/InlineAddChooserDialog.kt`) is the modal that precedes these
dialogs: five actions (Add **Body**, **Liner**, **Taper**, **Thread**, **Coupler Bolt
Slot**), lambda-based API (`onDismiss`, `onAddBody`, `onAddLiner`, `onAddThread`,
`onAddTaper`, `onAddCouplerBoltSlot`), invoke-then-dismiss semantics plus a Close
button that only dismisses. It lives outside `ShaftScreen` so screen refactors don't
remove it.

---

## Core invariant — dialog/card parity

> **Every control in a carousel edit card must also appear in its Add dialog under
> the same conditions.**

Corollary: if you add a control to a carousel card, add it to the Add dialog too, and
vice versa. Parity is checked per-condition (e.g. "only when excluded from OAL"), not
just per field.

Failure mode: the AFT/FWD thread-end selector was present in the carousel card but
missing from `AddThreadDialog` for several versions (restored 2026-06-23).

---

## Per-dialog contracts

### AddBodyDialog
| Field | Always shown |
|-------|-------------|
| Start | ✓ |
| Length | ✓ |
| Diameter (Ø) | ✓ |

### AddLinerDialog
| Field / control | Condition |
|-----------------|-----------|
| Measure From: AFT \| FWD chips | Always |
| Start from AFT / FWD | Always (label follows chip) |
| Length | Always |
| Outer Ø | Always |

### AddThreadDialog
| Field / control | Condition |
|-----------------|-----------|
| Start | Only when `countInOal = true` |
| Thread end: AFT \| FWD chips | Only when `countInOal = false` |
| Major Ø | Always |
| TPI | Always |
| Length | Always |
| Count in OAL toggle | Always |

The Start field is **replaced** by the AFT/FWD chips when excluded from OAL — it is
not hidden in addition to them. Matches `ComponentCarousel.kt` `ResolvedThread` branch,
`!includeInOal` block.

`isAftEnd` is passed through: `onSubmit → ShaftScreen.onAddThread → ShaftRoute →
ShaftViewModel.addThreadAt()` and stored on the `Threads` model object.

### AddTaperDialog
| Field / control | Condition |
|-----------------|-----------|
| Measure From: AFT \| FWD chips | Always |
| Start from SET / LET | Always (label follows chip) |
| Length | Always |
| SET Ø / LET Ø | Always (labels swap for FWD) |
| Rate mode: Auto \| Manual | Always |
| Rate | Always (read-only in Auto, editable in Manual) |
| Keyway fields | Always |

Manual taper-rate rules:
- Bare `1` is invalid/ambiguous and must be rewritten as a full ratio or fraction.
- `1/1` is allowed.
- When only one taper end is present, Manual mode requires a valid rate so the
  missing end can be derived.
- When Length + SET + LET are all present, a typed manual rate that disagrees with
  the geometry is shown as a warning for review.

Auto taper-rate rules (both surfaces — dialog and carousel card):
- Auto rate is computed only when Length and **both** diameters are real positive
  values. The dialog's `-1` "not provided" and the model's `0` defaults are
  sentinels; a rate must never be fabricated from them (`autoTaperRate` returns
  null and the surface shows "Auto needs Length + SET + LET…").
- Auto/Manual mode is **user-owned state** — seeded once per taper (blank stored
  text or text matching the computed rate ⇒ Auto), never re-derived from string
  comparison afterward. Re-deriving silently discards an explicit mode choice.
- The model is written only on explicit user commits (geometry edits carry the
  recomputed rate; the Auto chip tap syncs the stored text). **Never** write
  `taperRateText` from a composition-time effect — merely viewing a card must
  not dirty the document (see `NumberField.md` tap-and-leave rule).
- Blank manual rate text must not commit: `updateTaper` keeps the old rate on
  blank (`ifBlank`), so committing `""` would leave the field empty while the
  model retains the old rate. Blank reverts like any other invalid input.

### AddCouplerBoltSlotDialog
| Field / control | Condition |
|-----------------|-----------|
| Measure From: AFT \| FWD chips | Always (default FWD) |
| First slot from AFT / FWD | Always (label follows chip) |
| Hole Ø | Always |
| Count | Always |
| Spacing | Only when `count > 1` |
| Through hole toggle | Always |
| Depth | Only when blind (`through = false`) |

Matches `ComponentCarousel.kt` `ResolvedCouplerBoltSlot` branch. The carousel card carries
one extra control — the **"show dimension rail"** toggle (deferred; off by default). It is
a card-only affordance (not an add-time choice), so its absence from the dialog is
intentional and does not violate parity. See `CouplerBoltSlot.md`.

FWD-reference math: the entered position locates the fwd-most cutout; the ViewModel stores
the aft-most center as `startFromAftMm = OAL − enteredFwd − (count−1)·spacingMm`.

---

## Do Nots
- Do **not** remove the AFT/FWD thread-end selector from `AddThreadDialog`; it is only
  conditionally visible but must always be present in the excluded-from-OAL branch.
- Do **not** add collision/overlap checks for excluded threads — they live outside the
  shaft span by design.
- Do **not** call `onSubmit` with a negative `startMm` when the thread is excluded from
  OAL; the ViewModel derives position from `isAftEnd` + OAL via
  `syncExcludedThreadPositions()`.
- Do **not** add collision/overlap checks for coupler bolt slots — they are reference
  cutouts that overlay other components by design.

---

## Change log
**v1.4 (2026-07-18)**
- Absorbed `InlineAddChooserDialog.md` (chooser contract now documented here) and
  linked `Defaults.md` for seed values.

**v1.3 (2026-07-12)**
- Added Auto taper-rate rules: positive-diameter sentinel guard, user-owned mode
  state (no re-derivation), no composition-time model writes, blank-rate revert.
- The Auto one-end-missing block/message now applies to **both** surfaces (was
  dialog-only — a parity gap).

**v1.2 (2026-07-11)**
- Added taper **Rate mode: Auto | Manual** parity requirement.
- Clarified taper rate field behavior: read-only in Auto mode, editable in Manual mode.
- Added manual taper-rate rules: bare `1` blocked, `1/1` allowed, missing-end derivation
  requires a rate, mismatch with Length + SET + LET warns.

**v1.1 (2026-07-11)**
- Added `AddCouplerBoltSlotDialog` contract + its dialog/card parity note (show-dimension-rail
  is a card-only affordance).

**v1.0 (2026-06-23)**
- Initial contract. Documents dialog/card parity rule and thread AFT/FWD restoration.
- `AddThreadDialog.onSubmit` signature updated to include `isAftEnd: Boolean`.
- When `countInOal = false`: Start field hidden, "Thread end: AFT | FWD" chips shown
  using `DirectionChip` (same component as `AddLinerDialog`).
