# AddComponentDialogs Contract (v1.17, 2026-10-29)

## Purpose
Composable dialogs for adding new components: `AddBodyDialog`, `AddLinerDialog`,
`AddThreadDialog`, `AddTaperDialog`, `AddCouplerBoltSlotDialog`. Each dialog is the
**add-time counterpart** to the component's carousel edit card (`BodyPagerCard.kt`,
`TaperPagerCard.kt`, `ThreadPagerCard.kt`, `LinerPagerCard.kt`, `CouplerBoltSlotPagerCard.kt`).

Default field values are seeded per `Defaults.md` (`ui/config/AddDefaultsConfig.kt`
+ `SessionAddDefaults`).

## Entry point — InlineAddChooserDialog
The chooser (`ui/dialog/InlineAddChooserDialog.kt`) is the modal that precedes these
dialogs: five actions (Add **Body**, **Liner**, **Taper**, **Thread**, **Coupler Bolt
Slot**), lambda-based API (`onDismiss`, `onAddBody`, `onAddLiner`, `onAddThread`,
`onAddTaper`, `onAddCouplerBoltSlot`), invoke-then-dismiss semantics plus a Close
button that only dismisses. It lives outside `ShaftScreen` so screen refactors don't
remove it.

## Unreadable entries say so
Every numeric dialog field (`CommitNumField`) carries a `parseValid` gate — `isLengthText` by
default (a length or Ø: decimals, fractions, `10-1/4`, a decimal comma, a `mm`/`in`/`"` suffix —
`NumberField.md`), `isUnitlessNumberText` for TPI, a whole number for the bolt-slot Count, and
none for the fields that explain their own text (the taper rate's issue line, the thread
designation's "Not a metric designation — e.g. M20×2.5"). Non-blank text that fails it is
marked in error with **"Not a number"** under the field (`unreadableEntryError`), in the same
error line the dialogs already use for "Overlaps another component"; a caller's own `errorText`
takes the line first. Blank is "not entered yet" and is left to the Add gate. So a disabled Add
button always has a visible reason — a hyphenated `25-3/16` used to leave Add disabled with
nothing marked (emulator QA). The new-shaft "Shaft diameter" prompt (`ShaftDiaPrompt.kt`,
`shaftDiaPromptError`) carries the same line plus "Must be > 0".

Because the dialog fields and the card fields gate on the SAME predicates, the card and the Add
dialog for a quantity accept exactly the same spellings (parity applies to input grammar too).

A field the Add gate needs above zero (`requirePositive` — every Length, the body Ø, liner OD,
thread major Ø and TPI, bolt-hole Ø, Spacing and Depth) shows **"Must be > 0"** under a typed
`0`, the card's own wording for the refusal the card makes (`nonPositiveEntryError`). The taper's
S.E.T./L.E.T. are not marked: a blank or 0 end is the dialog's documented way to derive it from
the rate. The bolt-slot **Count** reads any number and shows **"Must be a whole number ≥ 1"** for
`0`, `2.5`, `1/2` (`boltSlotCountError`, shared with the card's validator); it is never coerced —
`0` used to be stored as 1 (emulator QA). Add stays disabled.

A negative **Start** (the number pad has a `-` and the dialogs filter nothing) shows **"Must be
≥ 0"** under the field — the cards' wording, ONE constant (`MUST_BE_NON_NEGATIVE_MSG`, through
`addDialogNegativeStartError`) — on all four positioned dialogs, from either reference end. Add
stays disabled; before, a typed `-5` left it disabled with nothing marked.

The Add gate covers the **optional** fields too: every optional numeric field the dialog SHOWS —
keyway W / D / L and the captured inset, blend and seal-area lengths, relief length and Ø, liner
shoulder length and Ø, the taper's S.E.T. / L.E.T. — must be blank or readable
(`optionalEntriesReadable`, with `keywayEntryTexts` / `threadReliefEntryTexts` naming each
section's fields). A field showing "Not a number" holds Add: submitting it would store 0 in place
of the typed value (a keyway ticked with W `1,500` landed with W = 0, emulator QA). Blank stays
allowed where it was, and a HIDDEN section — keyway unticked, relief off, seal area unticked, a
face left Square — never gates, whatever its fields hold.

## Overlaps are named before Add, the same from either reference end
The Liner, Taper and Thread dialogs show an overlap **inline under the Start field**, naming the
partner by the title its carousel card shows (`proposedSpanOverlapMessage` → "Overlaps FWD
Taper"; several: "Overlaps AFT Liner, FWD Taper" — `componentCardTitles`, a user label winning),
for AFT- and FWD-measured starts alike. What blocks is unchanged: another liner (for a liner) or
another thread (for a thread), or a thread off the shaft end, is a blocking `startError` (the card
refuses the same span), Add disabled; a crossing kind (a liner over a taper, a taper over a
thread, …) is shown in the same red line with Add still live, and "Add Anyway?" lists the same
partners (`collectAddWarnings`, one "Overlaps <title>" line each, plus the bounds line). Before,
a crossing-kind overlap surfaced only in the post-submit confirm, as "Overlaps Taper 1" — which
read as the FWD path skipping the check (the FWD seed happened to land on a taper, the AFT one
on a liner).

A FWD-measured liner or taper whose length would carry it past the AFT face shows **"Runs past
the AFT end of the shaft"** and cannot be added (`addDialogPhysStartMm` /
`addDialogFwdStartPastAft`). It used to be clamped onto the face — a different FWD distance
from the one typed. With no OAL yet a FWD start is placed at 0, as before.

## Seeds that do not collide
The Liner and Taper dialogs' Start seeds come from the ONE seeder, `addStartSeed(spec, length,
fromFwd)` (`ShaftScreenController.kt`), each from its own reference end and for the length the
dialog opens with: a seed whose span would overlap a taper, OAL-counted thread or liner moves to
the first free span that holds it — the AFT-most from AFT, the FWD-most from FWD (flush against
that span's FWD end; `addSeedFromFwdMm` shows it as a FWD distance, 0 when the FWD face is free).
Bodies are fillers and never push a seed. With no free span long enough the seed stands and the
inline line says what it overlaps. Add Body and Add Thread keep their seeds (a thread belongs at a
shaft end, not in the first free gap).

## A seed is exact until it is edited
Every field an Add dialog fills from a stored or derived mm value — Start, Length, Ø, the coupler
row's hole Ø / spacing / depth, and the blend, seal-area and thread-relief presets a section fills
in when it opens — keeps that EXACT mm until the user edits the field (`SeededMmField`,
`ui/screen/AddDialogSeededField.kt`, the `valueForSave` posture of the edit dialogs). The inline
overlap line, the Add gate and the submitted value all read the exact seed while the field is
untouched; an edited field is parsed as typed, even when retyped to read like the seed. The text
shown is unchanged (3 decimals). Parsing the rounded display back moved the value: a Start seeded
flush against a liner ending at 35.42422" showed 35.424 and opened on a false "Overlaps AFT
Liner", and rounding the other way left a hairline auto body (emulator QA). A blur or Done that
hands back the same text is not an edit. The captured-keyway inset preset (0.5 in) displays exactly
in either keyway unit and stays plain text, since the keyway-unit chip reinterprets what it means.

## The keyboard never covers the dialog
All five Add dialogs are `ImeResizingAlertDialog`s (`ui/screen/DialogImeResize.kt`, shared with
the calculators, the runout reading editor and the worn-section editor), and each one's `text`
is a `verticalScroll` column. With the keyboard up the dialog box sits in the room ABOVE it:
every field below the focused one scrolls into reach and Cancel / Add stay on screen. A plain
`AlertDialog` pans instead — the window slides up only far enough to show the cursor — so the
Taper dialog's Captured keyway / Keyway spooned and the Add button sat under the keyboard and a
tap aimed at KW L typed into the keyboard (emulator QA). The box is lifted by the keyboard inset
the dialog window receives, not by the platform's window resize alone, which on API 36 often did
not happen (see the helper's KDoc). A new Add dialog — or any dialog that takes typed input —
uses the same helper; the confirm dialogs over them ("Add Anyway?") take no input and stay plain.

## Back on a filled dialog asks before discarding
Back, or a tap outside, on an Add dialog the user has changed anything in opens a confirm instead
of throwing the entry away (on-device ruling: "it should ask to be safe"): **"Discard this
&lt;component&gt;?"** ("Discard this liner?", "… body?", "… taper?", "… thread?", "… coupler bolt
slot?"), text "What you typed will be lost.", buttons **Keep editing** (the dialog stays, every
field, chip and switch as it was) and **Discard** (the dialog closes through `onCancel`, nothing is
added). Back or a tap outside on the confirm itself is Keep editing. An untouched dialog closes on
Back exactly as before, with no prompt. The explicit **Cancel** button stays a direct discard — a
deliberate choice, where the guard exists for the stray swipe or tap — and **Add** is unchanged.
Test tags: `add_dialog_discard_confirm`, `add_dialog_keep_editing`, `add_dialog_discard`.

"Changed anything" means any keystroke in any field — counted the moment it lands, before the
field commits — any chip moved to another option, any switch or checkbox flipped. Re-tapping the
selected chip is not a change; a section's preset seeded as it opens is part of the control change
that opened it.

Mechanism: every Add dialog hosts through ONE `DiscardGuardedDialog`
(`ui/screen/DialogDiscardGuard.kt`), which wraps `ImeResizingAlertDialog` and guards only its
`onDismissRequest` (the IME padding and window sizing are untouched). The dirty flag
(`DialogEdits`) is **raised by the controls themselves**, through the `LocalDialogEdits`
composition local the guard provides: the dialogs' number field (`CommitNumField`), their chip
(`DirectionChip`, so `KeywayUnitEntryChips` and the relief end chips too), `DialogSwitch`, the
captured-keyway row, and the shared sections' leaf controls (`KeywayGateRow`,
`ThreadReliefSwitchRow`, `ChipRow` and the seal-area checkboxes in `BlendSection`, the shoulder
radius picker). That is deliberate: a field or chip added to a dialog from those pieces is covered
without the dialog listing it, where a per-dialog snapshot of state-vs-seeds would have to be
extended by hand for every new control. The same controls on the carousel cards see no local and
mark nothing. A raw Material control placed straight into an Add dialog would bypass the guard —
use `DialogSwitch` or call `DialogEdits.mark()` in its callback. The worn-section editor
(`WornSectionDialog`) uses the same guard ("Discard this worn section?", or "Discard changes to
this worn section?" when editing one); its controls are its own, so each marks the guard in its
callback. Pinned by `AddDialogDiscardGuardTest`.

---

## Core invariant — dialog/card parity

> **Every control in a carousel edit card must also appear in its Add dialog under
> the same conditions.**

Corollary: if you add a control to a carousel card, add it to the Add dialog too, and
vice versa. Parity is checked per-condition (e.g. "only when excluded from OAL"), not
just per field.

### The bounds warning is shared, not mirrored

The dialogs' pre-submit "Falls outside shaft span (OAL *n*")" warning and the carousel
cards' "Extends past shaft length (OAL *n*")" chip (both quote the OAL in the document's
display unit — `120"` on an inch drawing, `3048 mm` on a metric one — through
`formatLenWithUnit`, so every caller passes the unit) are **two wordings of ONE predicate** —
`outsideShaftSpan` in `ui/util/ComponentWarnings.kt`, reached through
`outsideShaftSpanMessage` (dialogs, via `collectAddWarnings`) and `pastShaftEndMessage`
(cards). Parity here is enforced by construction rather than by duplication: **do not fork the
comparison back out into either surface**, and do not "fix" one side's eps, its
`overallLengthMm > 0` guard, or its excluded-thread skip in isolation. Both are advisory —
neither blocks the add, the commit, or export (`VALIDATION_RULES.md` §3.1a).

This is not a parity carve-out: nothing new was added to either surface's controls. The Save
button on a card is card chrome, not a component property control, and is likewise outside the
parity rule — the dialogs commit through their own Add button.

Failure mode: the AFT/FWD thread-end selector was present in the carousel card but
missing from `AddThreadDialog` for several versions (restored 2026-06-23).

---

## Units — what is shared, and the one card-only control

**Under the parity rule (both surfaces):** the Add Thread dialog's **Imperial (TPI) |
Metric (mm)** mode. It decides what a thread *is* — a metric thread stores a
designation (`Threads.metricDesignation`, e.g. `M20×2.5`) and its major Ø and pitch are
parsed from that designation rather than typed — so it is value entry, not display. The
thread card is its counterpart: a thread stored with a designation shows a **Thread
designation** field in place of the imperial Major Ø / TPI pair, and a thread stored without
one shows the pair. The card carries no mode *switch* — the stored mode selects the field.
Re-designating an existing thread is deliberately not offered: a different designation is a
different thread, added as one.

**Also under the parity rule:** the **"Keyway in: in | mm"** chip in the keyway section of
`AddBodyDialog` / `AddTaperDialog` and of the Body / Taper cards, shown when Settings → Drawing →
*Per-component units* is on and the Keyway checkbox is ON (it lives inside the gated section).
It sets the unit the keyway's four fields (W, D, L, inset) are **typed** in as well as the unit its footer line prints in, so it is value entry, not display — a
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
| Measure From: AFT \| FWD chips | Always (mirrors the explicit-body card; switching never moves the body; FWD re-anchors on an OAL change). Stored as `Body.authoredReference`. testTags `add_body_ref_aft` / `add_body_ref_fwd` (card: `body_ref_aft` / `body_ref_fwd`) |
| Start from AFT / FWD | Always (label follows chip). Independent AFT/FWD seeds, as in `AddLinerDialog`; submits the PHYSICAL start (`addDialogPhysStartMm`). A FWD start that would reach past the AFT face shows `ADD_PAST_AFT_END_MSG` and holds Add — refused, never clamped |
| Length | Always |
| Diameter | Always — labelled "Diameter (unit)" on the dialog AND on both body cards (auto and explicit); the cards once said a bare "Ø (unit)", which a new user has nothing to read against |
| Keyway checkbox | Always. The gate for the whole keyway section (shared `KeywayGateRow`, `KeywaySection.kt`); default OFF in the dialog. On the card it seeds from `hasAnyKeywayValue` so a half-typed keyway keeps its section open, and unticking a card with any value opens "Remove keyway?" (`kw_remove_confirm`) before clearing W/D/L/inset/spooned |
| KW from: AFT \| FWD chips | Only while the Keyway checkbox is ON (keyway end-face reference). Default seeded by `ShaftSpec.suggestedBodyKeywayEnd`: opposite the shaft's existing keyway when exactly one side is taken (an aft taper keyway suggests FWD, and vice versa); both/neither → AFT. The SAME seed drives the card's chips for a not-yet-real keyway (parity of behavior, on-device report: a new body keyway defaulting onto the taken side read as a second aft keyway). A seed only — the chips always win, and nothing stored is ever rewritten by it |
| KW W / KW D / KW L | Only while the Keyway checkbox is ON (blank = 0 = no keyway; while OFF the dialog submits 0 whatever the hidden fields hold) |
| Captured keyway toggle | Only while the Keyway checkbox is ON. Captured ⇔ inset > 0 — no stored flag (the card derives the toggle from `keywayOffsetFromEndMm`; the dialog holds it as local state). Turning it ON with a blank inset seeds `AddDefaultsConfig.KEYWAY_INSET_IN` (0.5 in); turning it OFF commits an open keyway (inset 0) — the dialog keeps the typed text until submit, and the card remembers the cleared inset for the session and restores it on the next ON (`keywayInsetForCaptured`) |
| KW Inset from AFT / FWD | Only while the Keyway checkbox and the Captured keyway toggle are both ON (label follows chip). On the card, a blank or ≤ 0 commit is a no-op — the toggle is the one control that clears the inset |
| Keyway spooned toggle | Only while the Keyway checkbox is ON (disabled + "N/A — captured" while captured; while OFF the dialog submits `false`) |
| Keyways 180° apart toggle | Only when the shaft will have ≥ 2 keyways (≥ 1 existing **and** this dialog's keyway is fully defined) and the Keyway checkbox is ON |
| Keyways 90° apart toggle | Same condition as the 180° toggle |
| CW \| CCW direction chips | Only when the Keyways 90° apart toggle is on (and so only while the Keyway checkbox is ON) |

Matches `BodyPagerCard.kt`'s explicit-body branch. The **auto-body**
card intentionally shows only Start/Length/Ø — no Measure From chips (its span is derived) — Start/Length disabled/greyed (derived),
Ø editable (sets the shared bare-shaft `ShaftSpec.autoBodyDiaMm` without promoting) —
auto-bodies are derived and cannot host a keyway until promoted; that reduced card is
not a parity violation. The 180°/90°-apart toggles write spec-level
`ShaftSpec.keyways180Apart`/`keyways90Apart` (+ `keyways90Cw` for the CW/CCW chips) — the
card's switches appear when `spec.keywayCount() >= 2`, which is the same condition
evaluated at add time. On the cards the clocking section sits **inside** the Keyway gate, so a
card whose own keyway section is closed does not show it — the toggles appear on the cards that
carry the keyways. The two toggles are mutually exclusive (enabling one locally
clears the other's dialog state, mirroring the ViewModel's clearing behavior on commit).

The explicit-body card's **"Explicit body"** checkbox (checked; unchecking demotes back
to auto-fill via a confirmation dialog) — and the auto-body card's own "Explicit body"
checkbox (unchecked; the sole promotion path) — are **card-state, not add-time state**,
the same posture as the coupler-bolt-slot card's card-only "show dimension rail" toggle.
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
| "Include thread in OAL" switch | Always — the card's label, verbatim (the dialog once said "Count in OAL"; one control, one name on both surfaces). FIRST row, as on the card: it decides whether the next row is Start or the Thread end chips (on-device report) |
| Start | Only when `countInOal = true` |
| Thread end: AFT \| FWD chips | Only when `countInOal = false` |
| Thread spec mode: Imperial (TPI) \| Metric (mm) chips | Always |
| Major Ø | Only in Imperial mode |
| TPI | Only in Imperial mode |
| Thread designation (`M20×2.5`) | Only in Metric mode |
| Length | Always |
| Thread relief switch (`add_thread_relief`) | Always (default OFF) |
| Relief length | Only while the relief switch is on |
| Relief Ø | Only while the relief switch is on |
| Relief at: AFT \| FWD chips (`add_thread_relief_end`) | Only while the relief switch is on |

The Start field is **replaced** by the AFT/FWD chips when excluded from OAL — it is
not hidden in addition to them. Matches `ThreadPagerCard.kt`,
`!includeInOal` block.

The card's "Include thread in OAL" switch additionally ASKS whether the shaft's OAL follows the
thread (Shorten/Lengthen OAL, Keep OAL, Cancel — `OverallLength.md` §"Thread inclusion prompt").
That prompt is a **confirmation on a change to a STORED thread** (the `kw_remove_confirm` /
`body_demote_confirm` class), not a control: an added thread has no prior inclusion to change, so
`AddThreadDialog` carries nothing for it and its switch stays a plain switch. Not a parity gap.

`isAftEnd` is passed through: `onSubmit → ShaftScreen.onAddThread → ShaftRoute →
ShaftViewModel.addThreadAt()` and stored on the `Threads` model object.

`metricDesignation` rides the same path (`onSubmit → ShaftScreen.onAddThread → ShaftRoute →
ShaftViewModel.addThreadAt`): in Metric mode the designation is stored verbatim and the
major Ø / pitch handed over are the ones **parsed from it**, so the two can never disagree.
A coarse designation with the pitch omitted (`M20`) submits pitch `0` — omitted, not unset —
so it does not block the Add button. The ViewModel additionally registers an implicit **mm**
unit override for that thread; see `docs/DATA_MODEL.md`.

**Thread relief** (parity with the thread card's `ThreadReliefCardSection`,
`ui/screen/ThreadReliefSection.kt` — the switch row and the chips are the SAME composables on
both surfaces): the groove turned below the thread's root at the end where it runs up to its
shoulder. A thread has a relief exactly when `reliefLenMm > 0` and `reliefDiaMm > 0` — no
stored flag. The relief is PART of the thread's stored span; nothing moves when one is added.
Switching it on with nothing typed seeds Relief length = 1.5 × pitch
(`AddDefaultsConfig.THREAD_RELIEF_LEN_PITCHES`; 4 TPI → 0.375″; the hatch's 2.5 mm fallback
pitch when there is none) and Relief Ø = major Ø − 1.3 × pitch
(`THREAD_RELIEF_DIA_BELOW_MAJOR_PITCHES`, just under a 60° thread's root), both through the
pure `threadReliefForSwitch`. The END follows `suggestedThreadReliefAtAft` (the thread's
INBOARD end: an AFT-end thread → FWD, a FWD-end thread → AFT, mid-shaft → FWD) read LIVE from
the dialog's start / Count-in-OAL / Thread-end state until the "Relief at" chips are tapped; a
tapped chip is kept (`reliefEndChoice`). Seeding it once left the relief on the shaft-end side
after the thread end or the OAL switch changed (on-device report). The card's "Thread end" and
OAL switches carry a relief still at the suggested end the same way
(`Threads.withReliefEndFollowing`, in `setThreadEndPosition` / `setThreadExcludeFromOal`). Values are typed in
the document unit. While the switch is OFF the dialog submits relief 0/0 whatever the hidden
fields hold (`threadReliefSubmitValues`). `reliefLenMm, reliefDiaMm, reliefAtAft` ride
`onSubmit → ShaftScreen.onAddThread → ShaftRoute → ShaftViewModel.addThreadAt` verbatim.

On the card the switch (`thread_relief`) is derived from the stored relief length, so stored
values hold it open; switching OFF commits 0/0 and remembers the values for the card session so
switching back on restores them (and their end); a blank or ≤ 0 field commit is a no-op — the
switch is the one control that clears. The "Relief at" chips are `thread_relief_end`
(`_aft`/`_fwd`); every card commit goes through `ShaftViewModel.setThreadRelief`.

### AddTaperDialog
| Field / control | Condition |
|-----------------|-----------|
| Measure From: AFT \| FWD chips | Always — the row label is the card's "Measure From:" (the dialog once said "Direction:") |
| Start from SET / LET | Always (label follows chip) |
| Length | Always |
| SET Ø / LET Ø | Always (labels swap for FWD) |
| Rate mode: Auto \| Manual | Always |
| Rate | Always (read-only in Auto, editable in Manual) |
| Keyway checkbox | Always. The gate for the whole keyway section (shared `KeywayGateRow`, `KeywaySection.kt`); default OFF in the dialog. On the card it seeds from `hasAnyKeywayValue` so a half-typed keyway keeps its section open, and unticking a card with any value opens "Remove keyway?" (`kw_remove_confirm`) before clearing W/D/L/inset/spooned |
| KW W / KW D / KW L | Only while the Keyway checkbox is ON (blank = 0 = no keyway; while OFF the dialog submits 0 whatever the hidden fields hold) |
| Captured keyway toggle | Only while the Keyway checkbox is ON — same rules as `AddBodyDialog` (captured ⇔ `keywayOffsetFromSetMm` > 0, 0.5 in seed, OFF commits inset 0) |
| KW Inset from SET | Only while the Keyway checkbox and the Captured keyway toggle are both ON |
| Keyway spooned toggle | Only while the Keyway checkbox is ON (disabled + "N/A — captured" while captured; while OFF the dialog submits `false`) |
| Keyways 180° apart toggle | Only when the shaft will have ≥ 2 keyways (≥ 1 existing **and** this dialog's keyway is fully defined) and the Keyway checkbox is ON |
| Keyways 90° apart toggle | Same condition as the 180° toggle |
| CW \| CCW direction chips | Only when the Keyways 90° apart toggle is on (and so only while the Keyway checkbox is ON) |

Submit ordering (SET/LET → the stored pair):
- The model stores `startDiaMm`/`endDiaMm` x-ordered AFT → FWD, and SET faces the nearer
  shaft end. The submit handler therefore orders the typed values by the taper's **physical
  half** — `taperAddDiameterOrder` over `classifyTaperSideByMidpoint`
  (`ui/input/TaperSetLetMapping.kt`) — judged against `spec.overallLengthMm`, the shaft's
  authored OAL.
- **Not** by the Measure From chip. The chip only resolves the Start (`FWD → OAL − start −
  length`); a taper measured from AFT can still be placed in the FWD half, and keying the
  swap on the chip stores SET at the wrong face — drawn small-end-inboard, card labels
  reading swapped against the typed values.
- The chip itself is passed through as `Taper.authoredReference` (`onSubmit →
  ShaftScreen.onAddTaper → ShaftRoute → ShaftViewModel.addTaperAt`), so the card reopens in
  the user's measuring frame — same pass-through as `AddLinerDialog`.
- Pinned by `TaperAddOrientationTest` (all four half × measure-from combinations) and
  `TaperAuthoredReferencePersistenceTest`.

Manual taper-rate rules (both surfaces carry the one label `Rate (1:12, 3/4"/ft, or decimal)`):
- Accepted: a ratio (`1:12`), inches per foot (`3/4"/ft` and its spellings — `TaperRate.md`),
  or a decimal.
- Both rate fields filter keystrokes through the ONE `filterTaperRateInput`
  (`util/TextFilters.kt`), which keeps every character those spellings use (digits `. / :`,
  spaces, `"` `″` `'`, the letters of `in`/`ft`). The card's field never runs the numeric
  filter: it drops inch marks, letters and the second slash, leaving the bare `3/4` the
  parser refuses (on-device report).
- A bare slash fraction (`3/4`, `1/1`) is ambiguous between a slope and inches per foot and is
  blocked with a message naming the explicit forms; the parser never guesses. So is a bare
  decimal steeper than 0.25 (`.75` — the 3/4"/ft figure without its `/ft`), on both surfaces
  (`TaperRate.md`).
- Bare `1` is invalid/ambiguous and must be rewritten as a full ratio or inches per foot.
- When only one taper end is present, Manual mode requires a valid rate so the
  missing end can be derived.
- When Length + SET + LET are all present, a typed manual rate that disagrees with
  the geometry is shown as a warning for review.

Auto taper-rate rules (both surfaces — dialog and carousel card):
- Auto rate is computed only when Length and **both** diameters are real positive
  values. The dialog's `-1` "not provided" and the model's `0` defaults are
  sentinels; a rate must never be fabricated from them (`autoTaperRate` returns
  null and the surface shows "Auto needs Length + SET + LET…").
- Auto/Manual mode is **user-owned state, STORED on the taper** (`Taper.taperRateManual`):
  the dialog submits its chips' choice (`onSubmit(…, rateManual)` → `addTaperAt(rateManual)`),
  and the card's chips write it (`setTaperRateMode` — Auto syncing the stored text to the
  computed rate in the same edit). A Manual rate therefore survives a reopen and a Length edit
  keeps it. Only a taper with no stored mode (saved before the field) seeds it from the text
  (blank or equal to the computed rate ⇒ Auto) — which is what lost a Manual `1:12` equal to the
  computed rate: it reopened as Auto and the next Length edit wrote `1:13.029` (emulator QA).
  Never re-derived from string comparison afterward. Re-deriving silently discards an explicit
  mode choice.
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
| Hole: Seam \| Cross-drilled chips | Always (default Seam) — draw-only style, but chosen at add time |
| From keyway: 90° \| In line chips | Only when Cross-drilled (default 90°) — clocking, draw + footer text only |
| Measure From: AFT \| FWD chips | Always (default FWD) |
| First slot / Hole center from AFT / FWD | Always (label follows both chip rows via `slotStartFieldLabel`) |
| Hole Ø | Always |
| Count | Always |
| Spacing | Only when `count > 1` |
| Through hole toggle | Always |
| Depth | Only when blind (`through = false`) |

Matches `CouplerBoltSlotPagerCard.kt`. The carousel card carries
one extra control — the **"show dimension rail"** toggle (off by default; on, the row prints
one rail span from its authored shaft end to the nearest hole center). It is a card-only
affordance (not an add-time choice), so its absence from the dialog is intentional and does
not violate parity. See `CouplerBoltSlot.md`.

---

## Card-only display toggles (parity carve-out)

A control qualifies as card-only — present on the carousel card, deliberately absent from the
Add dialog — when all three hold:

1. it changes only how an already-drawn component **prints**, never geometry, position, or a
   stored value;
2. it is **default-on/off in the common case**, so in a dialog it would be a permanently
   pre-set box on every add; and
3. it is reached for **after looking at a printed sheet**, not while adding a component.

Seven controls qualify today — the three below plus "Shade on drawing", "Show name on drawing",
"Compress on drawing" and the per-component "Prints in" unit chip; `CLAUDE.md` holds the full
list and the reasoning for each:

| Control | Cards | Why card-only |
|---|---|---|
| **Show dimension rail** | `ResolvedCouplerBoltSlot` | Prints the row's position span (authored end → nearest hole center) on the rail sheets; off by default |
| **Show dimension rail** | Body (explicit only) | Prints the body's own length span on the rail sheets (`buildBodyLengthSpans`). Tri-state `Body.showDimensionRail`: unset follows position — on for a body whose stored span touches a shaft end face (where a coupling goes, on-device ruling), off mid-shaft — with a caption saying which applies; a tap stores an explicit value. Position is only known once the body is placed, and the default is right for the common case, so in `AddBodyDialog` it would be a preset box. The auto-body card omits it: auto spans never get the rail |
| **Show Ø on drawing** | Body (explicit + auto), Liner | Controls whether the Ø callout prints below the shaft. Bodies and the bare shaft default **off** — body Ø callouts are opt-in per card (on-device preference; the footer carries no body Ø line). Liners default on |

Anything that moves a value or a component stays under the parity rule above. See
`docs/PDF_EXPORT.md` §5.3 for what the Ø toggle does to the callout pass.

**Unit-converter launcher — a TOOL, outside parity entirely.** The calculator icon on the Add
dialogs' title rows opens `UnitConverterDialog` (`ui/screen/UnitConverterDialog.kt`), which is the
`BoreKeywayCalcDialog` posture: reads nothing from the shaft, writes nothing back (the user reads
the converted value and types it), stores nothing, marks nothing dirty. A control that writes no
component value is neither under the parity rule nor a carve-out from it — the same reasoning
that keeps the sidebar's keyway calculator off this contract. The icon is **capability-gated**
(Settings → Drawing → "Unit converter in Add dialogs", `dialogUnitConverterEnabled`, default
OFF — most shops work in one unit, so the icon is noise for them; the `linerShouldersEnabled`
posture): off means the dialogs render their plain titles. The converter stays reachable from
the sidebar Tools group at every setting, so its absence from dialogs and carousel cards costs
nothing.

**Body blend — under the parity rule, NOT a carve-out.** A blend changes the drawn silhouette,
so it fails test 1 above. `AddBodyDialog` and the explicit-body carousel card must both expose
the same section, and they do so by sharing one composable (`ui/screen/BlendSection.kt`) — the
length FIELDS are slots because the card commits on blur while the dialog holds local state until
submit, but every control and every visibility condition is decided in one place:

| Control | Shown when |
|---|---|
| "Component blend" chips, per face — **Square \| Blend** | always |
| Axial length field, per face ("Blend AFT/FWD") | that face is **Blend** |
| "At a liner" chips, per face — **Seat \| Liner OD** (`body_blend_aft_liner_target_<SEAT\|LINER_OD>`, `body_blend_fwd_…`) | that face is **Blend** — shown whether or not a liner butts it today (the dialog cannot know the neighbour; one rule on both surfaces) |
| Shape chips — S-curve \| Fillet \| Eased cone | either face is Blend |
| Seal area checkbox, per face (AFT / FWD) — under its own "Seal areas" heading | always |
| Seal area length field, per face ("Seal area AFT/FWD") | that face's seal is ON (both surfaces — parity) |

**"At a liner"** chooses where a blended face's curve leaves from when a liner butts it: **Seat**
(default — the derived half-way shoulder between the liner OD and the body Ø) or **Liner OD** (the
curve runs all the way up to the sleeve, no seat — on-device request). It changes drawn geometry,
so it is under parity: the dialog holds it as local state and submits it with the body
(`blendAftToLinerOd`/`blendFwdToLinerOd`; a square face submits `false`), the explicit card commits
each tap verbatim through `updateBodyBlend` (one undo step, the seal-checkbox posture), and the auto
card writes it onto the face's `AutoBlend` anchor. The "Component blend" heading was "Face finish",
which read as nonsense on a component card (on-device report).

**A seal area is a section property, not part of the blend.** The Square | Blend chips only decide whether the body
END has a shoulder (a blend ramp up to the neighbour) or is square; the seal area — the grooves the
fiberglass seats into — has its own per-face on/off and length, independent of the blend (on-device
report, photo of a shaft on the lathe). The seal area is measured from the face either way — it sits
in the same place on a square end and a blended one (on-device report: a blend pushed the dashes
inward). A Square | Blend change never touches the seal flags, and a seal toggle never
touches the blend length. Ticking a seal seeds an empty seal length with the preset
(`AddDefaultsConfig.SEAL_LEN_IN`, 4 in, capped at a quarter of the body — `defaultSealLenMm`);
unticking keeps the typed value (`sealLenForSeal`). A stored `0` follows the blend length (documents
saved before the field). The "Seal areas" section is a sibling of "Component blend" — never a
third Square | Blend chip, never nested under a blend control.

The auto-body card carries the same section, anchored in shaft space (`AutoBlend`), so a template's
seal areas survive liners moving under it; the dialog has no auto counterpart because an Add dialog
always creates an explicit body.

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
- Do **not** reimplement the shaft-span bounds comparison in a dialog or a card; both read
  `outsideShaftSpan` (see *The bounds warning is shared, not mirrored*).

---

## Change log
**v1.17 (2026-10-29)**
- `AddBodyDialog` gains the "Measure From: AFT | FWD" chips and a "Start from AFT / FWD" field,
  mirroring the explicit-body card's new row (`Body.authoredReference`); `onSubmit` takes the
  reference right after `diaMm`. On-device request.

**v1.16 (2026-10-28)**
- A negative Start shows "Must be ≥ 0" (the cards' constant); the Add gate requires every visible
  optional numeric field to be blank or readable (`optionalEntriesReadable`). Emulator QA.

**v1.15 (2026-10-28)**
- All five Add dialogs moved to `ImeResizingAlertDialog` (Add Liner's content now scrolls too):
  fields and buttons stay above the keyboard. Emulator QA.

**v1.14 (2026-10-28)**
- Overlaps named inline before Add on the Liner/Taper/Thread dialogs from either reference end,
  by card title; "Add Anyway?" lists the same named partners. FWD starts past the AFT face
  refused, not clamped. Liner/Taper seeds fit their length from the chosen end (`addStartSeed`).
  `requirePositive` "Must be > 0" lines; the bolt Count's "Must be a whole number ≥ 1", never
  coerced. Taper Rate mode stored (`taperRateManual`) and submitted by the dialog. Emulator QA.

**v1.13 (2026-10-23)**
- **"Standard size…" picker removed** from the Body/Taper cards and both Add dialogs, with the
  ANSI B17.1 / DIN 6885-1 key-stock tables (`geom/KeyStockStandards.kt`,
  `ui/screen/KeywayStdSizePicker.kt`) — on-device ruling: the shop types W × D every time, so the
  menu was a row of noise under a pair of fields it never filled. The keyway unit chip now governs
  entry and print only. Parity holds: the control left all four surfaces together.

**v1.12 (2026-10-03)**
- Thread **relief** (on-device request, from a machine-shop textbook figure of a thread's
  run-out groove): a "Thread relief" switch revealing Relief length, Relief Ø and "Relief at:
  AFT | FWD" chips on `AddThreadDialog` and the thread card, under the parity rule (shared
  `ThreadReliefSwitchRow`/`ThreadReliefEndChips`). Relief ⇔ length > 0 and Ø > 0, no stored
  flag; part of the stored span, so nothing moves. `AddThreadDialog.onSubmit` gains
  `reliefLenMm, reliefDiaMm, reliefAtAft`.

**v1.11 (2026-10-02)**
- The keyway block is a gated **section** on all four keyway surfaces (Body/Taper cards,
  `AddBodyDialog`, `AddTaperDialog`) behind ONE shared "Keyway" checkbox row (`KeywayGateRow`,
  `ui/screen/KeywaySection.kt`) — on-device request: every taper showed every keyway control.
  Everything keyway-related sits inside it, the keyway unit chip and the clocking section
  included. Cards seed the gate from `hasAnyKeywayValue` (any of W/D/L typed) and route the
  untick through ONE decision (`keywayGateAction`): with values present it confirms through
  "Remove keyway?" (`kw_remove_confirm`) before clearing; stored values always hold a card's
  section open, so an Undo of that removal shows the fields again. Dialogs default OFF and
  submit a zero keyway (spooned `false`) while it is off.

**v1.10 (2026-09-30)**
- Captured keyways (the shop term for what the app called a "floating" keyway — inset from the
  S.E.T. or the referenced face, mill arcs at both ends): the always-visible "KW Offset from …"
  field is replaced on all four keyway surfaces (Body/Taper cards, `AddBodyDialog`,
  `AddTaperDialog`) by a **Captured keyway** toggle that reveals a **KW Inset from SET / AFT /
  FWD** field. No model change — captured ⇔ offset > 0. Parity holds control-for-control.

**v1.9 (2026-08-29)**
- The dialogs' bounds warning and the carousel cards' new past-OAL chip now share ONE
  predicate (`outsideShaftSpan`); `collectAddWarnings` was refactored onto it and the dialog's
  message text is byte-identical (pinned by an unchanged `CollisionWarningsTest`). Recorded the
  no-forking rule. The card Save button's new disabled/enabled state is card chrome and stays
  outside the parity rule.

**v1.8 (2026-08-18)**
- Add Thread dialog gains the **Imperial (TPI) | Metric (mm)** spec-mode chips: a
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
