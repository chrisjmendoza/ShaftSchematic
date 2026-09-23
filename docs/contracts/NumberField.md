Numeric Input Field Contract
----------------------------

Layer: UI → Input  
File: `ui/input/NumericInputField.kt` (composable `NumericInputField`; wrapped by
`CommitNum` in `ComponentCarousel.kt` and `CommitNumField` in `AddComponentDialogs.kt`)  
Purpose: Single-line numeric field that holds local text; commits on blur/IME Done via callback.

Version: v0.5 (2026-08-29; the edge-trigger and external-refresh edges of `onDirtyChange` are now
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
  a second visit to the field compares against stale text and commits spuriously.
- Invalid input reverts on blur to the last valid text instead of committing.
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
  "Must be > 0". A rejected value takes the standard validator path: the field shows the
  error, reverts, and **does not commit**, so a zero length stops at the card instead of
  silently landing a component with no span (the short-segment advisory starts *above* 0, so
  nothing downstream would have said a word). This is a gate on an *entry*, not a rewrite of a
  stored value — the golden rule is untouched.
- Deliberately **not** applied to diameter fields (an auto-body Ø of `≤ 0` clears that
  section's override, a documented feature), not to keyway fields (blank/0 clears a keyway),
  and not to the Add dialogs, whose Submit gates already require a positive length.

Test coverage
- `ui/input/BlurCommitPolicyTest` — the predicate, pure JVM.
- `ui/input/NumericInputFieldBlurTest` — the predicate actually wired into the field:
  real focus/blur/IME events, re-focus baseline, invalid-input revert, and the
  `onDirtyChange` edges (clean on compose, dirty on divergence, clean again after a commit,
  a revert, and a validator rejection). Two further edges are pinned as of 2026-08-29: a RUN of
  divergent edits still reports exactly once (edge-triggered, not per keystroke), and an
  external model refresh arriving while the field holds focus settles it clean **without**
  writing anything back. Runs on the JVM under Robolectric.
- `ui/screen/ComponentCardSaveButtonTest` — the Save button off the aggregate: several fields on
  one card, a field leaving composition, and instant-commit controls that never register, plus
  `CardDirtyState` itself.
- `ui/util/StartOverlapValidationTest` — `positiveLengthErrorMm`, pure JVM.

Responsibilities
- Maintain internal `text` state; select-all on focus for quick overwrite.
- Invoke `onCommit(rawText)` on blur or IME Done, only when changed.
- **The carousel card's Save button is a third path to the SAME commit** — chips, toggles,
  and checkboxes never take focus, so a typed value followed by a chip tap sits uncommitted
  in a still-focused field with nothing visibly wrong (on-device report: a body keyway
  length that never landed). `ComponentCard`'s Save (`card_save_button`) force-clears focus,
  which drives this field's blur commit; it adds no second commit pipeline, and the
  no-change rule above still applies (Save after tap-without-edit is a no-op). Pinned by
  `ComponentCardSaveButtonTest`.
- **Save is enabled off the aggregate of `onDirtyChange`.** A `CommitNum` inside a
  `ComponentCard` registers itself with that card's `CardDirtyState`
  (`ui/screen/CardDirtyState.kt`) through the `LocalCardDirtyState` composition local, keyed
  by an identity token the field instance owns and dropped on dispose. There is deliberately
  **no per-call-site key list**: a card carries dozens of fields across four kinds, and a
  hand-maintained list is a second place for the button and the fields to drift — the first
  field added without a key would silently stop lighting Save up. `CommitDesignationField`
  (the metric thread designation) registers the same way. The card title's rename editor does
  not: only `content` is wrapped, and that editor is a bespoke field with its own commit.
  Instant-commit controls (chips, checkboxes, switches, sliders) never register — they have no
  uncommitted state.
- Support decimals and shop fractions in display.
- Optional `validator` and `externalIssueText` parameters surface inline field issues.

Do Nots
- Do not mutate ViewModel directly.
- Do not parse or convert (VM handles it).

Known exception
- The OAL field intentionally commits on every keystroke in manual mode (live preview);
  it does not use this field's commit-on-blur discipline. See `ShaftScreen.md`.

---

Input pipeline (filters + parsing)
----------------------------------
The field composes two util layers (formerly documented in `TextFilters.md` and
`Parsing.md`):

**Typing filters (`util/TextFilters.kt`)** — permissive while typing; validation
happens on commit. Pure transformations that drop offending characters rather than
rejecting the edit:
- `filterNumericInput(raw, allowNegative, allowFraction, allowColon = false)`:
  digits, one leading `-` (if allowed), one decimal separator, one `/` fraction slash
  and whitespace for `W N/D` (if allowed), and one `:` for ratio entry like `1:12`
  when `allowColon = true` (taper rate text). This is the only typing filter (the
  unused `filterDecimalPermissive` was deleted 2026-07-26).
- Filters never enforce numeric validity (commit path does) and never block paste or
  select-all.

**Parsing (`util/Parsing.kt`)** — pure, side-effect-free, neutral by design (no
clamping, no range enforcement — ViewModel setters layer validation on top). All
three parsers live here; there is no second copy elsewhere:
- `parseFractionOrDecimal(raw): Double?` — decimal, `N/D`, `W N/D`, or a `N:D`
  ratio (e.g. a typed taper rate like `1:12`) in entered units; tolerates trailing
  unit suffixes; `null` on invalid. Explicit-import callers include
  `BoreKeywayCalcDialog.kt` (ratio input is harmless there) and the `ui/screen`
  commit paths listed below.
- `parseToMm(raw, unit): Double` — converts to mm for inches; returns **`0.0`**
  (not null) on invalid input. For callers that always need a number and treat
  "couldn't parse" the same as "zero".
- `toMmOrNull(text, unit): Float?` — converts to mm for inches, but returns
  **`null`** on blank or invalid input instead of `0.0`. For commit-on-blur field
  handlers (the `CommitNum` pattern above) that must distinguish "no edit" from
  "typed zero" — a `null` result means the handler skips the commit entirely
  rather than writing a zero the user never typed. Used across `ui/screen`
  (`ShaftScreen.kt`, `AddComponentDialogs.kt`, the per-kind `*PagerCard.kt` files,
  `ComponentCarousel.kt`, `LinerWearDetail.kt`, `UndercutDetail.kt`,
  `WornSectionEditor.kt`).
