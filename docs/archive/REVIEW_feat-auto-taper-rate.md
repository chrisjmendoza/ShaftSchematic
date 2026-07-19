# Code Review — `feat/auto-taper-rate` (vs `main`)

**Date:** 2026-07-11
**Scope:** 11 files, +491/−45 (commits `e910a4e`, `7c2f359`)
**Method:** 8 independent finder passes (line-by-line, removed-behavior, cross-file, reuse, simplification, efficiency, altitude, CLAUDE.md conventions) followed by adversarial verification of each surviving candidate against the actual code on both sides of the diff.

**Bottom line:** The architecture is sound (pure util layer + tests is the right shape), but the branch has **one critical formatting bug**, **two data-corruption paths**, and several state-management defects that should be fixed before merge. The advertised bore-preference feature is dead code, and the "manual values preserved" fix in the carousel does not hold up.

---

## Resolution — 2026-07-12

All ten findings were fixed on this branch (uncommitted working tree, pending review):

| # | Finding | Outcome |
|---|---------|---------|
| 1 | `formatOneToN` corrupts 1:10/1:20 | **Fixed** — zeros only trimmed in the fractional part; regression tests added |
| 2 | Carousel `LaunchedEffect` mutates model on display | **Fixed** — effect removed; model written only on explicit commits (geometry edits carry the rate, Auto chip tap syncs) |
| 3 | Dialog fabricates rate from −1 sentinel | **Fixed** — `autoTaperRate` requires both diameters > 0; tests added |
| 4 | Mode inferred from string equality, resets on key change | **Fixed** — `autoRate` seeded once per `t.id`, user-owned afterward |
| 5 | Bore preference dead code | **Fixed** — implemented as comparably-close band (≤ 1 pt error gap) among within-tolerance candidates; geometry wins when clearly closer; tests added for both directions |
| 6 | Blank rate commits but model keeps old rate | **Fixed** — blank no longer passes `parseValid`; reverts like other invalid input |
| 7 | Dialog Auto→Manual clobbers typed rate | **Fixed** — display derived (`rateDisplayText`); `rateText` never overwritten by Auto |
| 8 | Parity gap: Auto one-end-missing message dialog-only | **Fixed** — same message/condition added to the carousel card |
| 9 | PDF `rate1toN` diverges from UI formatter | **Fixed** — delegates to `autoTaperRateText`, falls back to "—" |
| 10 | mm→inch conversion in util | **Fixed** — `BORE_BREAK_MM = 152.4f`, `MM_PER_IN` import removed |

Docs updated: `CHANGELOG.md` (fix entry), `docs/AddComponentDialogs.md` (v1.3 — Auto rate rules, parity note). Verified: `:app:compileDebugKotlin` and `:app:testDebugUnitTest` pass; `TaperRateAutoTest` 16/16.

---

## Critical

### 1. Snapped 1:10 and 1:20 render as "1:1" and "1:2"
**`app/src/main/java/com/android/shaftschematic/util/TaperRateAuto.kt:60`** (root cause in `formatOneToN`, ~line 179)

The snap branch calls `formatOneToN(n, decimals = 0, trimTrailingZeros = true)`. With `decimals = 0` there is no decimal point, so `trimEnd('0')` strips the integer's own trailing zero: `"20"` → `"2"`, `"10"` → `"1"`. Both 10 and 20 are in `DEFAULT_COMMON_TAPER_ONE_TO_N`.

- Example: Length 100 mm, SET 50, LET 60 → exact 1:10 → displayed, **stored**, and PDF-printed as `1:1` (parsed downstream as slope 1.0 — 10× steeper).
- The test suite never exercises 10 or 20 (only 14/16), which is why `:app:test` passed.
- **Fix:** only trim when `decimals > 0`, or format integers with `"%.0f"` and no trimming.

### 2. Carousel taper card mutates the model just by being displayed
**`app/src/main/java/com/android/shaftschematic/ui/screen/ComponentCarousel.kt:562`** — verified end-to-end

The new `LaunchedEffect` calls `onUpdateTaper(...)` whenever `autoRate` is true and stored text ≠ computed text. Blank `taperRateText` is the model default (`Taper.kt:37`) and `autoRate` initializes to true on blank, so **every taper from a pre-feature save gets rewritten the moment its card composes** — swiping the carousel dirties the document (`_spec.update` → `hasUnsavedWork` → debounced autosave) with zero user edits. This violates the project's no-spurious-update invariant (CLAUDE.md numeric-commit rule; `docs/ShaftViewModel.md` "do not mutate from inside composables").

Worse, for a legacy derive-pending taper (one diameter = 0), `autoTaperRate` treats 0 as a real diameter (`diaDelta = |0 − D| = D`), fabricates a rate like `1:3.000`, and `updateTaper` → `deriveTaperDiameters` (guard at `ShaftViewModel.kt:1945` doesn't protect the one-zero case) **overwrites the zero diameter with a fabricated value (up to 2×D) just from viewing the card**.

- **Fix:** never write the model from composition. Compute the auto text for display only, and commit it only on explicit user edits (the `nextRateText` path already does this). Also add `setDiaMm > 0f && letDiaMm > 0f` guards to `autoTaperRate`.

### 3. Add Taper dialog computes a garbage auto rate from the −1 "not provided" sentinel
**`app/src/main/java/com/android/shaftschematic/ui/screen/AddComponentDialogs.kt:545-568`**

`letMm = toMmOrNull(letText, unit) ?: -1f` is fed straight into `autoTaperRateText`, which has no positive-diameter guard: with SET 100, LET blank, Length 300 → `diaDelta = |−1 − 100| = 101` → `"1:2.970"`. The `LaunchedEffect` writes that into `rateText`. Auto mode blocks Add (good), so the user follows the hint and switches to Manual — where the garbage rate **remains prefilled, passes all validation, and the Add button enables**. Submitting derives the missing LET from a rate fabricated out of the sentinel.

- **Fix:** same guard as #2 (`autoTaperRate` returns null unless both diameters > 0), plus don't seed the manual field from an auto text computed while inputs were incomplete.

---

## High

### 4. Manual mode silently flips back to Auto (mode is inferred, not stored)
**`app/src/main/java/com/android/shaftschematic/ui/screen/ComponentCarousel.kt:533`**

`autoRate` is `rememberSaveable(t.id, computedRateText, t.taperRateText)` with initializer `isBlank || text == computed`. Any change to either key **re-runs the initializer and discards the user's explicit chip selection**:

- User taps Manual and types a rate that happens to equal the computed text (e.g. types `1:16` when geometry is 1:16) → mode resets to Auto, field disables, and the next geometry edit overwrites their pinned rate.
- The flip also happens in the other direction (Auto → Manual) when a geometry edit makes the stored text stop matching (verified in the zero-delta case).

The summary's claim that "existing stored manual taper-rate text is no longer blindly overwritten" only holds while the strings differ — the mode is a fragile string comparison, re-evaluated on every key change. **Fix:** store the mode explicitly (dialog-local state seeded once per `t.id`, or a model/UI-state flag), not derived from text equality.

### 5. Advertised bore-aware 1:16/1:12 preference is dead code
**`app/src/main/java/com/android/shaftschematic/util/TaperRateAuto.kt:56`** (+ `preferredCommonOneToN` ~line 172)

`preferenceRank` is only consulted via `thenBy` after `compareBy { relativeError }` — i.e. only when two candidates have **bitwise-equal Float relative errors**, which never happens for distinct 1:N values. Independently, adjacent common slopes differ by ≥ ~14% while the snap tolerance is 3%, so at most one candidate can ever be within tolerance. The CHANGELOG/BRIEFING/ROADMAP claim ("1:16 preferred at 6″ and under, 1:12 above when comparably close") describes behavior that **cannot occur**. The Copilot summary hedged this as "preference order, not an override" — in reality it never fires at all, and no test exercises it.

- **Fix:** either implement it (among candidates within tolerance, pick by preference) with a test, or delete `preferredCommonOneToN` / `BORE_BREAK_IN` / `referenceDiaMm` / `CommonCandidate.preferenceRank` and correct the docs.

### 6. Clearing the manual rate shows blank but the model keeps the old rate
**`app/src/main/java/com/android/shaftschematic/ui/screen/ComponentCarousel.kt:646`** + `ShaftViewModel.kt:1024` — verified

New `parseValid` accepts blank; with both diameters present `manualTaperRateBlockingMessage("")` is null, so clearing the field commits `""` — but `updateTaper` does `rateText.ifBlank { old.taperRateText }`, keeping e.g. `1:12`. The spec doesn't change, so the field's local state stays blank: **UI shows no rate while model and PDF still carry the old one**, persistently. Pre-branch, blank failed parse and reverted. **Fix:** either revert blank in the field, or make `updateTaper` accept an explicit clear.

### 7. Dialog: toggling Auto → Manual destroys the typed manual rate
**`app/src/main/java/com/android/shaftschematic/ui/screen/AddComponentDialogs.kt:568`**

`LaunchedEffect(autoRate, computedRateText) { if (autoRate) rateText = computedRateText.orEmpty() }` clobbers `rateText` the moment Auto is selected. Type a custom rate in Manual, tap Auto, tap Manual again — the typed rate is gone. The carousel derives the display instead (`if (autoRate) computed else stored`), which is the right pattern; the dialog should do the same and drop the effect (the submit path already picks `computedRateText` when auto).

---

## Medium

### 8. Dialog/card parity gap on the Auto one-end-missing condition (CLAUDE.md rule)
The Add dialog blocks Auto mode with one end present and shows "Auto needs Length + SET + LET…" (`AddComponentDialogs.kt:563`); the carousel card has **no equivalent condition** — its Auto mode happily computes from a 0 diameter and commits it (see #2). CLAUDE.md / `docs/AddComponentDialogs.md`: parity is per-condition, both directions. Fixing #2/#3's guards should include surfacing the same message in the card.

### 9. PDF footer re-implements exact-rate formatting and diverges from the UI
**`app/src/main/java/com/android/shaftschematic/pdf/ShaftPdfComposer.kt:1123`** (`rate1toN`, used at :1043/:1068 when `taperRateText` is blank)

`"1:" + "%.0f"` rounding vs the util's 3%-snap + 3-decimal fallback: a legacy blank-rate taper whose card was never swiped into view exports e.g. `1:15` where the UI would show `1:14.600`. Call `autoTaperRateText(...)` (null → "—") so screen and export share one formatter.

### 10. mm→inch conversion inside util business logic (unit-edge rule)
**`app/src/main/java/com/android/shaftschematic/util/TaperRateAuto.kt:172`**

`referenceDiaMm / MM_PER_IN <= BORE_BREAK_IN` performs an imperial-unit decision below the UI edge, contrary to CLAUDE.md's canonical-mm rule. Trivial fix: `BORE_BREAK_MM = 152.4f` compared mm-to-mm. (Moot if #5 is resolved by deletion.)

---

## Cleanup notes (non-blocking)

- The whole Auto/Manual block (compute + block/warn + chip row + sync) is duplicated nearly line-for-line between `AddTaperDialog` and the carousel card; a shared composable/state holder would make the mandated parity structural. The two already disagree (#8), and their issue-text styling differs (`CommitNumField.highlight` vs `NumericInputField.externalIssueText`).
- `manualTaperRateWarning` internally re-calls `manualTaperRateBlockingMessage` and re-parses; both call sites also call the blocking helper separately — the same string is parsed up to 4× per evaluation, per recomposition, without `remember`. Cheap to memoize.
- `parseTaperRateText` doesn't handle mixed fractions (`1 3/4`) that the old field-level parser accepted; verified as materially a *fix* (the old path stored text the ViewModel couldn't parse anyway), but worth a decision: reject at keystroke filter, or support mixed fractions in the util.
- `autoTaperRateText` duplicates `autoTaperRate`'s full 7-parameter signature just to append `?.text`.
- The bare-`1` rule is encoded in three places (parser flag, blocking message, inline `parseValid` lambda) that must stay in sync.

## What's good

- Extracting the logic into a pure, tested util is the right call; `Locale.US` formatting avoids locale drift; the ViewModel delegation preserves legacy parsing semantics exactly (verified against main's `parseRateText`, including bare-integer handling).
- The Add-dialog confirm gating in Auto mode (one-end-missing block) is correct as far as it goes.
- Validation split (blocking vs warning) is a sensible model; the helpers guard the sentinel cases (`> 0f` checks) — the gap is only in `autoTaperRate` itself.

## Recommended fix order

1. `formatOneToN` trim bug (#1) + tests for 1:10 and 1:20.
2. Positive-diameter guards in `autoTaperRate` (#2b, #3) + test.
3. Remove the carousel `LaunchedEffect` model write; display-derive instead (#2a, aligns with #7's pattern).
4. Explicit mode state instead of string-equality inference (#4).
5. Blank-clear semantics (#6), dialog clobber (#7), parity message (#8).
6. Decide bore-preference: implement-with-test or delete (#5, #10).
7. PDF formatter reuse (#9) and cleanup notes as time allows.
