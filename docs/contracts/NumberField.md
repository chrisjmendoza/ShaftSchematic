Numeric Input Field Contract
----------------------------

Layer: UI → Input  
File: `ui/input/NumericInputField.kt` (composable `NumericInputField`; wrapped by
`CommitNum` in `ComponentCarousel.kt` and `CommitNumField` in `AddComponentDialogs.kt`)  
Purpose: Single-line numeric field that holds local text; commits on blur/IME Done via callback.

Version: v0.13 (2026-10-30; the per-field ✓ / ✗ pair is gone — `acceptCancel`,
`LocalFieldAcceptCancel` removed: with the "/" key it filled a half-width field and hid the value,
on-device report. The Overall Length row swaps to the card Save / Cancel bar off
`PendingCardEdits`, `onPendingEdit` is the handle it acts on, and a carousel or preview card change
under a pending edit prompts Save | Discard | Keep editing).
v0.12 (2026-10-30; the carousel card's Save button is replaced by the OAL's ✓ / ✗ pair
on each card field while it holds an edit — `acceptCancel`, `LocalFieldAcceptCancel`; the
`CardDirtyState` registry went with the button).
v0.11 (2026-10-28; the filter keeps the trailing run of LETTERS and inch marks, so
`12ft` reaches the parser whole and is refused; seeds format in `Locale.US`; the "/" key needs a
number-pad keyboard as well as a fraction-capable field).
v0.10 (2026-10-28; released focus parks on the window root — `FocusParkingRoot` — so
clearing focus out of touch mode, and the dialog dismissal after it, never lands in the first
text field on the screen).
v0.9 (2026-10-28; every card rule a commit lambda used to enforce by returning early is
the field's `validator` — diameters, relief, captured inset, bolt Count/spacing/depth, TPI, and
the Length's span overlap — so a refused value always reverts and is named).
v0.8 (2026-10-28; ONE grammar reads hyphenated mixed fractions, decimal commas and unit
suffixes — a suffix converts, never drops; the unit-blind parser refuses any suffix; the filter
keeps the hyphen, the comma and a trailing suffix; length/Ø fields gate on `isLengthText`).
v0.7 (2026-10-27; the box's text, the settled text and the rejection notice survive an
activity recreation, and the focus baseline is the settled text).
v0.6 (2026-10-16; a rejected value is reverted to the caller's value AND named in a
`"<typed> not applied — <reason>"` notice that only an edit clears; the unused `showValidationErrors` switch was removed).
v0.5 (2026-08-29; the edge-trigger and external-refresh edges of `onDirtyChange` are now
pinned by tests). v0.4 (2026-08-29) added the `onDirtyChange` contract behind the carousel card's
Save button, and the Length "> 0" validator convention. v0.3 2026-07-26 added the focus-baseline
rule and test coverage. v0.2 2026-07-18 superseded v0.1 "NumberField" — the composable was
renamed `NumericInputField`, contract filename kept for link stability)

Invariants
- No live writes while typing; commit-on-blur discipline.
- **Tap-and-leave is a no-op (critical invariant, see CLAUDE.md):** the field captures
  its text when focus is gained and only calls `onCommit` on blur if the value
  actually changed. Blur without an edit must not commit — this prevents spurious
  auto-body promotion and unnecessary ViewModel updates.
- **A commit requires a focus baseline.** The decision lives in
  `ui/input/BlurCommitPolicy.kt` (`shouldCommitOnBlur`), extracted so it is unit-testable.
  A null baseline means focus was never gained, so there is nothing to commit. This is not
  a hypothetical: Compose delivers an initial `onFocusChanged` with `isFocused = false`
  when the modifier first attaches, and until 2026-07-26 that fired `onCommit` on **every
  composition**. The dirty gate and undo history absorbed it wherever the commit was a
  genuine no-op, but not everywhere: composing an **auto-body** card committed its
  displayed (derived) Ø into `ShaftSpec.autoBodyDiaMm`, pinning the bare-shaft Ø and
  marking a freshly-opened document dirty; on explicit bodies, `rememberBodyDefaults`
  reset the Add-Body length default. Do not restore a "commit defensively when the
  baseline is null" rule.
- The baseline is re-captured on **each** focus gain, not once at composition — otherwise
  a second visit to the field compares against stale text and commits spuriously. It is the
  **settled** text (below), which equals the box's text on every focus gain but one: a
  restored edit that never landed (next bullet), which a blur must hand over rather than read
  as unchanged and leave on screen, unapplied.
- **Typed text survives an activity recreation** (a tablet rotation; on-device report: a typed
  undercut Ø came back blank). The box's text, the settled text and the rejection notice are
  `rememberSaveable`, keyed on `initialText` exactly as the plain state was. By default a restore
  commits nothing: the focus baseline stays plain state, so the attach-time unfocused callback
  still finds no baseline. The state is saved before the teardown, whose own blur commits as any blur
  does; where that commit lands in state the recreation discards, the restored text is the only
  copy and its next blur lands it. Restoration does not compare `rememberSaveable` inputs, so
  the field also saves the `initialText` it was seeded from and drops the saved state when the
  incoming value differs (the teardown blur's commit already reached the model) — otherwise it
  would report a phantom uncommitted edit.
- **`landRestoredEdit = true`** is for a caller whose commits land in state the teardown blur
  cannot reach (the undercut card's draft, saved before the teardown): a field restored still
  holding an edit commits it ONCE, after the restoring composition — a rejected one reverts with
  its notice, as a blur's would. Only a restore can compose a field dirty (a fresh field's text
  is its settled text), so it never fires on an ordinary composition. Default `false`.
- **Invalid input reverts AND names what it threw away — the field never silently swaps a
  value.** On a failed commit (blur or IME Done; the text does not parse, or `validator` returns
  a reason) the box goes back to the value the caller holds — the last committed text, or the
  incoming `initialText` — never to a half-typed intermediate the caller was never handed (`3`
  on the way to `3/`). The field then shows, as error supporting text (`isError`),
  `"<typed> not applied — <reason>"`: the validator's own text as the reason, `not a number` for
  a parse failure, and `Blank entry` in place of an empty value (`rejectedValueNotice`). The
  notice takes precedence over `externalIssueText` and persists until the text is **edited** (a
  real text change — a cursor move or the select-all on focus is not one). **Focus never clears
  it**: a focus change can arrive with nobody touching the field (in non-touch mode — hardware
  keyboards, ChromeOS — a cleared focus is handed straight back to the first focusable), and a
  notice wiped that way is never read. A revert with only the rule beside it reads as if the OLD value were at fault — a
  typed value gone with no word of it (on-device report). A caller whose value must survive an
  error does not reject it here: it commits and gates downstream (the undercut card,
  `UndercutDrawing.md`; the wear-spot card's Start/Length, `RunoutSheet.md` — verbatim commit,
  `wearSpotSpanIssue` shown, band clamped for display only). The revert still settles the field for `onDirtyChange`.
- Field displays formatted text; callback receives raw text.
- **IME Done commits unconditionally**, even with no edit — a deliberate asymmetry with
  blur. Done is an explicit "I mean this" gesture; blur is passive. Pinned by test.
- **`onDirtyChange` reports, it never commits.** The optional
  `onDirtyChange: ((Boolean) -> Unit)?` says whether the field currently holds an edit that has
  not landed — its text against the *settled* text, meaning the text a walk-away would leave
  behind: the incoming `initialText`, then whatever a commit or a revert settled on. It fires
  `true` the first time the text diverges and `false` again on a successful commit, on a revert
  (failed parse or failed `validator`), and when an external model update refreshes the field.
  It is **edge-triggered** (a `LaunchedEffect` keyed on the flag), so a run of keystrokes past
  the first reports nothing. The listener is read through `rememberUpdatedState`, so a caller
  handing over a fresh lambda or a fresh registry is never left writing into the previous one.
  Adding a report must never add a commit — the blur/IME/Save paths above stay the only writers.

Length validator convention
- Every carousel card's **Length** field carries
  `validator = { positiveLengthErrorMm(toMmOrNull(raw, unit)) }`
  (`ui/util/StartOverlapValidation.kt`) — one shared helper on all four cards, message
  "Must be > 0". A rejected value takes the standard validator path: the field reverts, names
  the refused value ("0 not applied — Must be > 0"), and **does not commit**, so a zero length stops at the card instead of
  silently landing a component with no span (the short-segment advisory starts *above* 0, so
  nothing downstream would have said a word). This is a gate on an *entry*, not a rewrite of a
  stored value — the golden rule is untouched.
- Not applied to keyway W/D/L (the Keyway checkbox's confirmed removal clears a keyway), and
  not to the Add dialogs, whose Submit gates already require a positive length (they name a `0`
  under the field with the same "Must be > 0" — `requirePositive` on `CommitNumField`).

A rule lives in the validator, never in the commit lambda
- **A commit lambda must not early-return on a value it will not apply.** It runs AFTER the field
  has settled on the typed text, so a `return@CommitNum` leaves that text on screen over the
  stored value with no notice — the screen lies about what is stored (emulator QA: a thread
  relief Ø of 0 stayed on screen, the reopened editor showed 4.925 again). The rule goes in the
  `validator`; the lambda then commits whatever reaches it, verbatim. Remaining `?: return` null
  guards in commit lambdas sit behind a `parseValid` that already proved the text parses, so they
  never fire.
- The card rules, each a validator (message in quotes):
  - Diameters — explicit body Ø, liner OD, taper S.E.T./L.E.T. Ø, thread major Ø, relief Ø,
    bolt-hole Ø: "Must be > 0" (`positiveDiaErrorMm`, `ui/util/StartOverlapValidation.kt`). A
    taper Ø of 0 used to re-derive that end from the rate — a different value from the one typed.
    **Not** the AUTO-body Ø: `≤ 0` there clears that section's override, a documented feature.
  - Thread relief length and Ø (`ThreadReliefSection.kt`): "Must be > 0" — switching the relief
    off is the switch's job.
  - Captured-keyway inset, taper and body (`capturedInsetErrorMm`, `KeywaySection.kt`): "Must be
    > 0" — un-capturing is the "Captured keyway" toggle's job.
  - TPI: "Must be > 0".
  - Bolt-slot Count (`boltSlotCountError`, `AddDialogGates.kt`): any number passes the parse gate
    so the validator can say "Must be a whole number ≥ 1" for `0`, `2.5`, `1/2`. `0` was coerced
    to 1 and `2.5` dropped without a word; `AddCouplerBoltSlotDialog` shows the same line and
    keeps Add disabled.
  - Bolt-slot Spacing (count > 1) and Depth (blind): "Must be > 0", the dialog's own gate.
  - Bolt-slot position / Count / Spacing that would put a FWD-measured row's aft-most hole
    before the AFT face: "Row extends past the AFT end of the shaft" — it was clamped onto the
    face, showing a different position from the one typed. Past the FWD end stays legal.
  - Liner and thread **Length**: the span with the new length is checked like a Start
    (`spanOverlapErrorMm`) — "Overlaps <card title>" for another liner (thread). Running past
    the OAL is not refused.

Test coverage
- `ui/input/BlurCommitPolicyTest` — the predicate, pure JVM.
- `ui/input/NumericInputFieldBlurTest` — the restoration edges via `StateRestorationTester`
  (typed text back, no commit from the restore itself, the restored edit landing on the next
  blur, a moved model value winning over saved text, `landRestoredEdit` landing it at once), and the predicate actually wired into the field:
  real focus/blur/IME events, re-focus baseline, invalid-input revert (to the caller's value,
  never a half-typed intermediate) with its `"<typed> not applied — <reason>"` notice for a
  validator rejection, a parse failure and a blank entry, the notice surviving a re-focus (both a
  deliberate one and the unasked-for refocus a cleared focus gets in non-touch mode) and clearing
  on an edit, and the
  `onDirtyChange` edges (clean on compose, dirty on divergence, clean again after a commit,
  a revert, and a validator rejection). Two further edges are pinned as of 2026-08-29: a RUN of
  divergent edits still reports exactly once (edge-triggered, not per keystroke), and an
  external model refresh arriving while the field holds focus settles it clean **without**
  writing anything back. Runs on the JVM under Robolectric.
- `ui/screen/CardSaveBarTest` — the card Save / Cancel bar in the Overall Length row: the OAL
  field at rest and on focus alone, the bar (naming the field and card) once a card field is
  edited; ✓ lands a still-focused value (the chip-tap case) with no second commit on a later
  blur and brings the OAL back, ✗ restores without committing, a refused ✓ names the value, two
  fields share one bar, a departing field still lands its edit, a checkbox tap brings up no bar;
  plus `fieldLabelWithoutUnit` and `pendingEditName`.
- `ui/screen/UnsavedCardEditDialogTest` — the card-change prompt: Save, Discard and Keep editing
  each fire exactly their own action, and the text names the field without its unit and the card.
- `ui/util/StartOverlapValidationTest` — `positiveLengthErrorMm`, `positiveDiaErrorMm`, the
  Length span check and partner naming, pure JVM.
- `ui/screen/CardFieldRefusalTest` / `ThreadReliefCardTest` — the REAL cards refusing a bolt Count
  of 0 or 2.5, a liner Length into its neighbour, a liner OD or taper Ø of 0, a relief value of 0,
  each named and nothing committed (Robolectric).
- `ui/input/FocusParkingRootTest` — out of touch mode a released focus jumps to the first field
  without the root (the mechanism, pinned so a platform change shows) and lands on no field with
  it; a tapped field still takes focus (Robolectric).

Focus release
- **Releasing focus never hands it to another field.** `FocusManager.clearFocus()` — the OAL
  field's ✓/Done/✗, the auto-body Ø scope dialog opening, the undercut and wear editors' own
  clears, the card Save / Cancel bar's ✓ / ✗ — ends with no text field focused. Out of touch mode (a hardware or
  Bluetooth keyboard, or a key the IME sends as a raw key event) Android's `View.clearFocus()`
  immediately refocuses the window, and the Compose view answers with an "enter" search that
  picks the top-left focusable composable — on the editor, the Overall Length field. Cancelling
  "Apply diameter to…" therefore left the OAL focused with the keyboard up, the next digits went
  into the shaft length, and a blur would have committed them (emulator QA). The ONE rule that
  prevents it is app-wide: `MainActivity` wraps its content in `FocusParkingRoot`
  (`ui/input/FocusParking.kt`), a focusable box around everything that the enter search finds
  first, so the refocus parks there — no field, no cursor, no keyboard. In touch mode the
  platform does not refocus and the box is never focused. Do not answer a jump with a
  per-dialog `requestFocus` or a hidden first field: either is a second mechanism, and a new
  dialog would miss it.
- A dialog dismissed while a field is focused returns focus to THAT field (the window regains
  focus on the same node) — the field being edited, never an unrelated one.

Keyboard
- **Number fields ask for the number pad** (`KeyboardType.Decimal`); whether one appears is up to
  the keyboard app. Gboard shows its pad; SwiftKey and Samsung Keyboard answer a number field with
  their full layout (which carries `/`). The app does not force a pad: a phone-style keypad is
  the only one those keyboards offer, and what it carries varies by device (on-device ruling:
  leave the regular keyboard so every device keeps a way to type a fraction). The calculators,
  the taper rate (`1:12`) and the thread designation ask for the text keyboard outright.
- **A number pad has no `/`** (Gboard's offers digits, `-`, space, `,` and `.`), so a
  fraction-capable number-pad field carries its own fraction key in the field's trailing slot
  (`FractionKey`, `ui/input/FractionKey.kt`, testTag `fraction_key`, description "Fraction bar —
  types /"). Its face is a small stacked fraction — a grey (`onSurfaceVariant`) "n" over a 1 dp
  bar over a "d", built from text, never a Unicode vulgar-fraction glyph — because a bare "/"
  read as a divider rather than "fraction" (on-device report). It types a bar AT THE CURSOR, replacing a selection the way a typed key would. With the pad's space or
  hyphen it enters `10 1/4` and `10-1/4`. The key shows only while the field is focused (an idle
  field keeps its full width), only on fields that accept a fraction (`allowFraction`) AND ask
  for the number pad (`KeyboardType.Decimal`), and only in an inch document — the app root
  provides `LocalFractionKeyEnabled` from the document unit. A whole-number field opts out
  (`fractionKey = false` — the bolt-slot Count, on the card and in the Add dialog alike). A tap
  never takes focus from the field (the button is `focusProperties { canFocus = false }`, like
  the OAL ✓/✗), so the keyboard stays up. Text-keyboard fields carry no key: their keyboard
  already has `/` (the taper rate on the card used to show one, emulator QA).
- Hosts: `NumericInputField` (every card field, the undercut and wear editors), the Overall
  Length field, the Add dialogs' number fields and the new-shaft diameter prompt. A field whose
  state is a plain `String` cannot insert at its cursor (replacing its text from outside leaves
  the cursor before the bar), so those hosts bridge through `FieldCursor`
  (`rememberFieldCursor()`), which keeps the last edit's cursor and IME composition.
- `ui/input/FractionKeyTest` — cursor arithmetic, the `String` bridge, and the key wired into
  `NumericInputField`: shown only focused, only for fraction fields, only in inches; a tap
  between keystrokes commits `10 1/4` (Robolectric).

Responsibilities
- Maintain internal `text` state; select-all on focus for quick overwrite.
- Invoke `onCommit(rawText)` on blur or IME Done, only when changed. IME Done keeps focus and
  moves the blur baseline to the settled text, so a later blur judges "changed" against what Done
  landed, never against the pre-Done text (on-device report: Done, retype the old value, move on
  — the box showed the old value while the model kept the new one).
- **A card field's held edit is saved or cancelled from the Overall Length row** — the third
  path to the SAME commit. Chips, toggles and checkboxes never take focus, so a typed value
  followed by a chip tap sits uncommitted in a still-focused field (on-device report: a body
  keyway length that never landed). While a card field holds such an edit, the editor's Overall
  Length row shows the card Save / Cancel bar in the OAL field's place (`OverallLengthRow` →
  `CardSaveBar`, `ShaftScreen.kt`; tags `card_save_bar` / `card_save` / `card_cancel`): it names
  the waiting field and its card and puts the OAL's own green ✓ / red ✗ in the OAL's own action
  slot, and the OAL field returns once the card is clean (on-device ruling: a Save at the end of a
  long card was hidden; the OAL row is always on screen above the carousel). ONE construction,
  `AcceptCancelButtons` (`ui/input/AcceptCancelButtons.kt` — the green ✓ `ACCEPT_GREEN`, the red ✗
  in the theme's error colour, neither button focusable), behind the OAL field and the bar. The
  field itself shows no pair: in the trailing slot beside the "/" key it made three 48 dp targets
  that hid the value in a half-width field (on-device report). Pinned by `CardSaveBarTest`.
- **`onPendingEdit` hands the save and cancel answers to the host** — a
  `FieldEditHandle(accept, cancel)` while the field is dirty (exactly while `onDirtyChange` would
  report dirty), null once it settles or leaves composition. `accept` returns whether the value
  landed (false = refused, notice shown, focus kept — a card change waiting on Save then stays
  on that card). `accept` runs the IME Done commit
  (`commitOrRevert`) and releases focus when it lands; a refusal keeps focus with its notice.
  `cancel` puts the settled text back, commits nothing, and releases focus. Both move the focus
  baseline to the settled text BEFORE releasing focus, so the blur that follows cannot commit a
  second time. `CommitNum` and `CommitDesignationField` (the metric thread designation) register
  the handle with the editor's `PendingCardEdits` (`ui/screen/PendingCardEdit.kt`, through
  `LocalPendingCardEdits`, keyed by an identity token the field instance owns) — no per-call-site
  wiring, so a field added to a card cannot miss it. The card title's rename editor does not
  register: it is a bespoke field with its own commit. Outside the editor (Add dialogs, undercut
  and wear editors) nothing is provided and a field reports to nobody.
- **A card change under a pending edit asks first.** A carousel swipe, an edge arrow, a tap on a
  peeking card, or a tap on a component in the drawing (or a warning line) while a card field holds
  an unsaved value opens "Save changes?" (`UnsavedCardEditDialog`) — **Save** runs the handle's
  `accept` and then changes cards, **Discard** its `cancel` and then changes cards, **Keep
  editing** stays (a swipe that already moved the pager scrolls back to the card holding the edit).
  The hidden field would otherwise keep focus and the keyboard, and typing would land on a card the
  user cannot see (on-device ruling). Pinned by `UnsavedCardEditDialogTest`.
- Support decimals and shop fractions in display. **Inch fields read back through ONE formatter,
  `inchFieldText` (`util/InchFieldFormat.kt`)**, behind `formatDisplay`/`disp` (cards) and
  `toDisplayString` (Add-dialog seeds), keyway fields included: a value on the 1/32 grid
  (within `INCH_FIELD_GRID_TOLERANCE_IN`, the `Float` storage noise) shows as its reduced
  fraction (`12 11/32`) under Settings → Units "Inch fields show: Fractions" (the default), or
  as its EXACT decimal (`12.34375`) under "Decimals" — never a rounded one, which the reader had
  to convert back by eye (on-device report). Off the grid, at least 4 decimals in both styles.
  The style only decides values where both spellings are the same number (`0.5` is `1/2`); the
  formatter cannot tell how a value was typed and does not try. Every form parses back through
  `parseLengthMmOrNull`. Display only: device preference (`SettingsStore.inchFieldStyleFlow`),
  mirrored into the snapshot-state `InchFieldDisplay.style` by ONE writer, the root collector in
  `MainActivity`, so a field recomposes when the setting changes.
- Optional `validator` and `externalIssueText` parameters surface inline field issues. There is
  no switch to suppress the rejection notice: a refused value is always reported.
- Optional `placeholder: String?` and `hint: String?` (both default `null`; byte-identical to the
  field without them when null). `placeholder` is the `OutlinedTextField` placeholder — an example
  of what to type (Material 3 shows it only while the field is focused and empty). `hint` is
  supporting text shown ONLY when there is neither a rejection notice nor an `externalIssueText`:
  precedence is rejection notice > `externalIssueText` > `hint`, because an error outranks a tip.
  The undercut card uses both (Distance hint, Measured Ø placeholder) through its private
  `UndercutNum` wrapper; `WearNum` is unchanged.

Do Nots
- Do not mutate ViewModel directly.
- Do not parse or convert (VM handles it).

The OAL field
- `OverallLengthField` is not a `NumericInputField`, but it follows the same commit-on-blur
  discipline (commit only when the text changed since focus, `shouldCommitOnBlur`) plus an
  explicit ✓/✗ accept/cancel pair and IME Done. It never commits per keystroke: an
  intermediate value reached the drawing that way (on-device report). See `ShaftScreen.md`.

---

Input pipeline (filters + parsing)
----------------------------------
The field composes two util layers (formerly documented in `TextFilters.md` and
`Parsing.md`):

**Typing filters (`util/TextFilters.kt`)** — permissive while typing; validation
happens on commit. Pure transformations that drop offending characters rather than
rejecting the edit:
- `filterNumericInput(raw, allowNegative, allowFraction, allowColon = false)`:
  digits; one leading `-` sign (if allowed); one `.` decimal point, and `,` anywhere before it
  (a decimal comma `1,5` or thousands groups `1,234.5` — the parser decides which); one `/`
  fraction slash, whitespace for `W N/D`, and ONE hyphen separating a whole number from its
  fraction (`10-1/4` — only right after a digit and only before the slash) when
  `allowFraction`; one `:` for ratio entry like `1:12` when `allowColon = true` (taper rate
  text); and the **trailing run of letters and inch marks** (`"`, `″`, `'`) at the END of the
  text — kept as typed so the parser reads it (`260mm`, `12in`, `11"`) or refuses it (`12ft`,
  `10 inches`: "not applied — not a number"). Letters anywhere else, and everything else, are
  dropped. Dropping the hyphen turned `10-1/4` into `101/4` (= 25.25), deleting the comma turned
  `1,5` into `15`, eating a pasted `mm` stored a metric value in the document unit, and keeping
  only the suffix letters turned `12ft` into an accepted 12 (emulator QA) — each a valid, wrong
  number. This is the only numeric typing filter (the taper rate has its own,
  `filterTaperRateInput`).
- Filters never enforce numeric validity (commit path does) and never block paste or
  select-all.
- **Seeds are formatted for the parser, not the device locale.** Every text a number field is
  seeded with — `formatDisplay`/`disp`, the Add dialogs' `toDisplayString` and TPI seed,
  `fmtTrim`, `formatRunoutValue` — formats in `Locale.US`. A locale-following `%.4f` on a
  comma-decimal device (de, fr, es, pt-BR) turns 12" into `12,0000`, which trims to `12,` — a
  shape the parser refuses — and `1.505` into `1,505`, the ambiguous thousands form.

**Parsing (`util/Parsing.kt`)** — pure, side-effect-free, neutral by design (no
clamping, no range enforcement — ViewModel setters layer validation on top). ONE grammar,
read by every parser here; there is no second copy elsewhere:
- **Number shapes**: a decimal (`12`, `1.25`, `.5`); a fraction (`3/4`); a mixed fraction
  with a space or a hyphen (`15 1/2`, `10-1/4`, `10 - 1/4`); a ratio (`1:12`). Only a `-`
  BETWEEN a whole number and a fraction is a separator — a leading `-` is a sign (`-1/4`,
  `-10-1/4` = −10.25, where the field allows negatives).
- **Commas**: a value whose only separator is one comma with no `.` reads the comma as the
  decimal point (`1,5` = 1.5, `12,75`) — a European-locale numeric keypad sends `,` as its
  decimal key. EXCEPT the thousands-ambiguous shape, one comma before exactly three digits with
  a non-zero whole part (`1,500`): it could be 1.5 or 1500, so it is **not a number** and the
  field says so rather than guessing (`0,500` and `,500` have no thousands reading and read as
  0.5). With a `.` present, commas are thousands separators only on 3-digit groups before the
  dot (`1,234.5`); any other comma shape is not a number.
- **Unit suffixes** (lengths only): `mm`, or `in` / `"` / `″` / `''` for inches, any case,
  with or without a space before it. A suffix names the unit the value is in, whatever the
  document unit: `260mm` in an inch drawing stores 260 mm, `11"` in a millimetre drawing
  stores 279.4 mm. Any other trailing letters (`12ft`, `5'`, `4tpi`) make the text not a
  number — a suffix is read or refused, **never dropped** (the old parser stripped any
  trailing letters, so `260mm` in an inch drawing stored 260 INCHES, emulator QA). A
  converted value displays afterwards in the document unit, which is correct: canonical is mm.
- `parseFractionOrDecimal(raw): Double?` — the **unit-blind** parser, for unitless fields:
  TPI, counts, ratios, runout TIR values. It returns `null` for a value carrying ANY unit
  suffix, so no caller can multiply the result by the document unit and silently lose one.
  Length and Ø code must not call it.
- `parseLengthMmOrNull(raw, unit): Double?` — a length in canonical mm: the suffix's unit
  when there is one, `unit` (the document unit) otherwise; `null` on blank or invalid.
- `toMmOrNull(text, unit): Float?` — the same, as a `Float`, for commit-on-blur field
  handlers (the `CommitNum` pattern above) that must distinguish "no edit" from "typed zero"
  — a `null` result means the handler skips the commit entirely rather than writing a zero
  the user never typed. Used across `ui/screen` (`ShaftScreen.kt`, `AddComponentDialogs.kt`,
  the per-kind `*PagerCard.kt` files, `ComponentCarousel.kt`, `LinerWearDetail.kt`,
  `UndercutDetail.kt`, `WornSectionEditor.kt`, `ShaftDiaPrompt.kt`).
- `parseToMm(raw, unit): Double` — the same, returning **`0.0`** (not null) on invalid input,
  for callers that always need a number and treat "couldn't parse" the same as "zero".
- `parseLengthInUnit(raw, unit): Double?` — a length expressed IN `unit` (a suffix in the
  other unit is converted), for tools that compute in the entry unit: the bore-keyway
  calculator, the unit converter, the taper calculator's length entries.
- `isLengthText(raw)` / `isUnitlessNumberText(raw)` — the two field gates. `CommitNum`'s,
  `WearNum`'s, `UndercutNum`'s and the Add dialogs' default `parseValid` is `isLengthText`;
  a unitless field passes its own (TPI: `isUnitlessNumberText`; the bolt-slot Count: a whole
  number; the taper rate: its own parser). The card field and the Add-dialog field for the
  same quantity therefore accept exactly the same spellings.
- The taper rate has its own grammar (`parseTaperRateText`, `TaperRate.md`): a bare decimal
  under 1 is a slope only up to 0.25 (3"/ft); `.75` is refused as ambiguous like a bare `3/4`.
