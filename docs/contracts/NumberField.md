Numeric Input Field Contract
----------------------------

Layer: UI → Input  
File: `ui/input/NumericInputField.kt` (composable `NumericInputField`; wrapped by
`CommitNum` in `ComponentCarousel.kt` and `CommitNumField` in `AddComponentDialogs.kt`)  
Purpose: Single-line numeric field that holds local text; commits on blur/IME Done via callback.

Version: v0.3 (2026-07-26; adds the focus-baseline rule and test coverage. v0.2 2026-07-18
superseded v0.1 "NumberField" — the composable was renamed `NumericInputField`, contract
filename kept for link stability)

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

Test coverage
- `ui/input/BlurCommitPolicyTest` — the predicate, pure JVM.
- `ui/input/NumericInputFieldBlurTest` — the predicate actually wired into the field:
  real focus/blur/IME events, re-focus baseline, invalid-input revert. Runs on the JVM
  under Robolectric.

Responsibilities
- Maintain internal `text` state; select-all on focus for quick overwrite.
- Invoke `onCommit(rawText)` on blur or IME Done, only when changed.
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
clamping, no range enforcement — ViewModel setters layer validation on top):
- `parseFractionOrDecimal(raw): Double?` — decimal, `N/D`, or `W N/D` in entered
  units; tolerates trailing unit suffixes; `null` on invalid. Returns `Double?`.
- `parseToMm(raw, unit): Double` — converts to mm for inches; returns `0.0` (not
  null) on invalid input. There is **no `toMmOrNull` in this file**.

**Known duplication (consolidation candidate):** `ui/screen/ShaftScreen.kt`
(~lines 1289–1305) defines its own internal, Float-based `toMmOrNull` and
`parseFractionOrDecimal`, and most inline commit paths in that screen use them
instead of `util.parseToMm`. Near-identical parsing logic in two layers —
consolidate onto one convention if touching either.
