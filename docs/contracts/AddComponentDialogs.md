# AddComponentDialogs Contract (v1.8, 2026-08-18)

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

## Units — what is shared, and the one card-only control

**Under the parity rule (both surfaces):** the Add Thread dialog's **Imperial (TPI) |
Metric (M-designation)** mode. It decides what a thread *is* — a metric thread stores a
designation (`Threads.metricDesignation`, e.g. `M20×2.5`) and its major Ø and pitch are
parsed from that designation rather than typed — so it is value entry, not display. The
thread card is its counterpart: a thread stored with a designation shows a **Thread
designation** field in place of the imperial Major Ø / TPI pair, and a thread stored without
one shows the pair. The card carries no mode *switch* — the stored mode selects the field.
Re-designating an existing thread is deliberately not offered: a different designation is a
different thread, added as one.

**Also under the parity rule:** the **"Keyway in: in | mm"** chip in the keyway section of
`AddBodyDialog` / `AddTaperDialog` and of the Body / Taper cards, shown when Settings → Drawing →
*Per-component units* is on. It sets the unit the keyway's four fields (W, D, L, offset) are
**typed** in as well as the unit its footer line prints in, so it is value entry, not display — a
European keyway is whole millimetres on an otherwise imperial shaft, and typing 20 × 12 mm as
0.7874 × 0.4724 in loses the number the shop was given. Stored as a derived-key override
(`"<componentId>#kw"` in `unit_overrides`), resolved keyway → component → document, so a keyway
with no choice behaves exactly as it always did.

**Card-only (a carve-out from the parity rule):** the per-component **"Prints in: in | mm"**
chip, at the FOOT of the card, shown on the explicit-**Body**, **Taper**, **Thread**, and **Liner** cards when
Settings → Drawing → *Per-component units* is on. It is the **third** post-hoc display
toggle to qualify for the carve-out recorded in `CLAUDE.md`, on the same grounds as the other
two: it changes only how an already-drawn component *prints*, its default is stable
("follows the document unit"), and it is reached for after looking at a printed sheet. In an
Add dialog it would be a permanently-preset chip pair on every add — and it would have
nothing to key an override to, since overrides key on the **resolved component id**, which
does not exist until the component does.

Two cards deliberately omit the chip: the **auto-body** card (a derived span — same posture
as its disabled Start/Length; promote it first) and the **coupler-bolt-slot** card.

The chip is display-only in a second sense that matters at the card: it governs how the
component **prints**, not the unit its own fields are typed in. Entry fields stay in the
document unit on both surfaces (see `ShaftScreen.md`), so a component set to print in mm is
still authored in inches on an inch document. That asymmetry is a known follow-up, tracked in
`TODO.md`.

---

## Per-dialog contracts

### AddBodyDialog
| Field / control | Condition |
|-----------------|-----------|
| Start | Always |
| Length | Always |
| Diameter (Ø) | Always |
| KW from: AFT \| FWD chips | Always (keyway end-face reference, default AFT) |
| KW W / KW D / KW L | Always (blank = 0 = no keyway) |
| KW Offset from AFT / FWD | Always (label follows chip; 0 = open, > 0 = floating) |
| Keyway spooned toggle | Always (disabled + "N/A — floating" when offset > 0) |
| Keyways 180° apart toggle | Only when the shaft will have ≥ 2 keyways (≥ 1 existing **and** this dialog's keyway is fully defined) |
| Keyways 90° apart toggle | Same condition as the 180° toggle |
| CW \| CCW direction chips | Only when the Keyways 90° apart toggle is on |

Matches `ComponentCarousel.kt` `ResolvedBody` explicit-body branch. The **auto-body**
card intentionally shows only Start/Length/Ø — Start/Length disabled/greyed (derived),
Ø editable (sets the shared bare-shaft `ShaftSpec.autoBodyDiaMm` without promoting) —
auto-bodies are derived and cannot host a keyway until promoted; that reduced card is
not a parity violation. The 180°/90°-apart toggles write spec-level
`ShaftSpec.keyways180Apart`/`keyways90Apart` (+ `keyways90Cw` for the CW/CCW chips) — the
card's switches appear when `spec.keywayCount() >= 2`, which is the same condition
evaluated at add time. The two toggles are mutually exclusive (enabling one locally
clears the other's dialog state, mirroring the ViewModel's clearing behavior on commit).

The explicit-body card's **"Explicit body"** checkbox (checked; unchecking demotes back
to auto-fill via a confirmation dialog) — and the auto-body card's own "Explicit body"
checkbox (unchecked; the sole promotion path) — are **card-state, not add-time state**,
the same posture as the coupler-bolt-slot card's deferred "show dimension rail" toggle.
On both cards the checkbox row sits **above** the Start/Length/Ø fields so its position
doesn't jump when checking it swaps the auto card for the explicit one.
`AddBodyDialog` has no equivalent control: an Add dialog always creates an explicit body
by definition, so there is nothing to toggle. This is intentional and not a parity gap.

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
| Thread spec mode: Imperial (TPI) \| Metric (M-designation) chips | Always |
| Major Ø | Only in Imperial mode |
| TPI | Only in Imperial mode |
| Thread designation (`M20×2.5`) | Only in Metric mode |
| Length | Always |
| Count in OAL toggle | Always |

The Start field is **replaced** by the AFT/FWD chips when excluded from OAL — it is
not hidden in addition to them. Matches `ComponentCarousel.kt` `ResolvedThread` branch,
`!includeInOal` block.

`isAftEnd` is passed through: `onSubmit → ShaftScreen.onAddThread → ShaftRoute →
ShaftViewModel.addThreadAt()` and stored on the `Threads` model object.

`metricDesignation` rides the same path (`onSubmit → ShaftScreen.onAddThread → ShaftRoute →
ShaftViewModel.addThreadAt`): in Metric mode the designation is stored verbatim and the
major Ø / pitch handed over are the ones **parsed from it**, so the two can never disagree.
A coarse designation with the pitch omitted (`M20`) submits pitch `0` — omitted, not unset —
so it does not block the Add button. The ViewModel additionally registers an implicit **mm**
unit override for that thread; see `docs/DATA_MODEL.md`.

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
| Keyways 180° apart toggle | Only when the shaft will have ≥ 2 keyways (≥ 1 existing **and** this dialog's keyway is fully defined) |
| Keyways 90° apart toggle | Same condition as the 180° toggle |
| CW \| CCW direction chips | Only when the Keyways 90° apart toggle is on |

Submit ordering (SET/LET → the stored pair):
- The model stores `startDiaMm`/`endDiaMm` x-ordered AFT → FWD, and SET faces the nearer
  shaft end. The submit handler therefore orders the typed values by the taper's **physical
  half** — `taperAddDiameterOrder` over `classifyTaperSideByMidpoint`
  (`ui/input/TaperSetLetMapping.kt`) — judged against `oalAfterTaperAddMm(…)`, the OAL the
  shaft will carry once the taper exists.
- **Not** by the Measure From chip. The chip only resolves the Start (`FWD → OAL − start −
  length`); a taper measured from AFT can still be placed in the FWD half, and keying the
  swap on the chip stores SET at the wrong face — drawn small-end-inboard, card labels
  reading swapped against the typed values.
- The chip itself is passed through as `Taper.authoredReference` (`onSubmit →
  ShaftScreen.onAddTaper → ShaftRoute → ShaftViewModel.addTaperAt`), so the card reopens in
  the user's measuring frame — same pass-through as `AddLinerDialog`.
- Pinned by `TaperAddOrientationTest` (all four half × measure-from combinations) and
  `TaperAuthoredReferencePersistenceTest`.

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

---

## Card-only display toggles (parity carve-out)

A control qualifies as card-only — present on the carousel card, deliberately absent from the
Add dialog — when all three hold:

1. it changes only how an already-drawn component **prints**, never geometry, position, or a
   stored value;
2. it is **default-on/off in the common case**, so in a dialog it would be a permanently
   pre-set box on every add; and
3. it is reached for **after looking at a printed sheet**, not while adding a component.

Exactly two controls qualify today (the per-component "Prints in" unit chip above is the
third carve-out, recorded in `CLAUDE.md`):

| Control | Cards | Why card-only |
|---|---|---|
| **Show dimension rail** | `ResolvedCouplerBoltSlot` | Deferred print affordance, off by default |
| **Show Ø on drawing** | Body (explicit + auto), Liner | Controls whether the Ø callout prints below the shaft. Bodies and the bare shaft default **off** — body Ø callouts are opt-in per card (on-device preference; the footer's "Body:" list still carries every Ø). Liners default on |

Anything that moves a value or a component stays under the parity rule above. See
`docs/PDF_EXPORT.md` §5.3 for what the Ø toggle does to the callout pass.

**Body blend — under the parity rule, NOT a carve-out.** A blend changes the drawn silhouette,
so it fails test 1 above. `AddBodyDialog` and the explicit-body carousel card must both expose
the same section, and they do so by sharing one composable (`ui/screen/BlendSection.kt`) — the
length FIELD is a slot because the card commits on blur while the dialog holds local state until
submit, but every control and every visibility condition is decided in one place:

| Control | Shown when |
|---|---|
| Face-finish chips, per face — **Square \| Blend \| Seal area** | always |
| Axial length field, per face | that face is not Square |
| Shape chips — S-curve \| Fillet \| Eased cone | either face is not Square |

**Seal area includes the blend** — the cuts are machined across the blended section, so the three
modes are exclusive as presented while the stored model keeps length and seal flag independent.

The auto-body card omits the section: an auto span is a derived gap with no card fields of its
own, and promoting it to an explicit body is the documented way to gain them.

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
**v1.8 (2026-08-18)**
- Add Thread dialog gains the **Imperial (TPI) | Metric (M-designation)** spec-mode chips: a
  metric thread stores a designation (`Threads.metricDesignation`, e.g. `M20×2.5`) with major Ø
  and pitch parsed from it. Value entry, so it sits under the parity rule (mirrored on the
  thread card by the field the stored mode selects); the mixed-units wave. This entry was added
  retroactively 2026-08-24 — the header was bumped without a log entry at the time.

**v1.7 (2026-08-06)**
- `AddTaperDialog` submit keys the SET/LET storage order on the taper's **physical half**
  (against the post-add OAL) instead of the Measure From chip, and passes the chip through as
  `Taper.authoredReference`. The dialog's controls are unchanged — the chips stay, so
  add-dialog parity with the taper card is unaffected.

**v1.6 (2026-07-30)**
- Spec-level "Keyways 90° apart" toggle (+ CW/CCW direction chips, viewed from aft, from
  the AFT keyway) added to `AddBodyDialog` + `AddTaperDialog` and both keyway-bearing
  carousel cards, under the same ≥ 2 keyway gate as "Keyways 180° apart". The two
  toggles are mutually exclusive.

**v1.5 (2026-07-20)**
- Body keyway support (un-shelved): `AddBodyDialog` gains the full keyway section
  (KW from AFT|FWD, W×D, L, offset, spooned) mirroring the explicit body card.
- Spec-level "Keyways 180° apart" toggle added to `AddBodyDialog` + `AddTaperDialog`
  and both keyway-bearing carousel cards, gated on the shaft having ≥ 2 keyways.

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
