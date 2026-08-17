TaperRate Contract
------------------

Layer: Util  
File: `util/TaperRateAuto.kt`  
Purpose: Compute an auto-derived taper rate display string from Length + SET + LET
geometry, and parse/validate user-entered manual taper rate text.

Version: v1.0 (2026-07-18)  
Supersedes: `TaperParser.md`, `TaperParserContract.md` — those documented
`util/TaperParser.kt`, which was deleted 2026-07-11 alongside this file's introduction.
The two old docs remain on disk (not deleted per doc-repo policy) but describe code that
no longer exists; do not use them as a reference for taper rate behavior.

Invariants
- **Auto-by-default:** a taper's rate is computed automatically from Length + SET + LET
  whenever both diameters are real, positive values. Manual mode is an explicit,
  user-owned override — see `AddComponentDialogs.md` for the dialog/card mode-toggle
  contract (seeding, no re-derivation from string comparison, no composition-time writes).
- **Sentinel guard:** the dialog's `-1` ("not provided") and the model's `0` default are
  sentinels, never geometry. `autoTaperRate` returns `null` rather than fabricate a rate
  from `lengthMm <= 0`, either diameter `<= 0`, or `setDiaMm == letDiaMm` (zero slope).
- **Common-rate snapping tolerance is 3% relative slope error**
  (`DEFAULT_SLOPE_ERROR_TOLERANCE = 0.03f`). This is a **confirmed product decision —
  do not change without product approval.**
- **Bare `1` is blocked as ambiguous** manual rate text; must be rewritten as a full
  ratio or fraction (`1:1`, `1/1` are fine).
- **Exact fallback format** is `1:N.NNN` (3 decimals, not trimmed) when no common rate
  is within tolerance; a *snapped* common rate is formatted with trailing zeros trimmed
  (e.g. `1:16`, not `1:16.000`).

Responsibilities
- `autoTaperRate(lengthMm, setDiaMm, letDiaMm, referenceDiaMm = max(setDiaMm, letDiaMm), commonOneToN = DEFAULT_COMMON_TAPER_ONE_TO_N, maxRelativeSlopeError = 0.03f, exactDecimals = 3): AutoTaperRateResult?`
  - `null` if `lengthMm <= 0f`, `setDiaMm <= 0f`, `letDiaMm <= 0f`, or the diameters are
    equal (no slope).
  - Computes the exact slope (`|LET − SET| / length`) and exact `N` (`length / |LET − SET|`).
  - Snaps to a common `1:N` from `DEFAULT_COMMON_TAPER_ONE_TO_N = [20, 16, 14, 12, 10, 8]`
    when its relative slope error is within tolerance. Among within-tolerance candidates
    whose relative error is within `0.01` (absolute) of the single best candidate's error
    ("comparably close" — shop preference decides), the **bore-preferred** rate wins:
    `1:16` at or under a 6 in (152.4 mm) reference bore (`referenceDiaMm`), `1:12` above
    it. A candidate that is clearly closer (error gap `> 0.01`) always wins on geometry
    regardless of bore preference.
  - Otherwise returns the exact `1:N.NNN` text with `matchedCommonOneToN = null`.
  - `AutoTaperRateResult(text, matchedCommonOneToN, exactOneToN)`.
- `autoTaperRateText(...): String?` — convenience wrapper returning just `.text`.
- `parseTaperRateText(text, allowAmbiguousBareOne = true): Float?` — parses `"a:b"`,
  `"a/b"`, or a bare number (interpreted as `1:N` when `≥ 1`, or as a direct slope when
  `< 1`). Bare `"1"` is rejected only when `allowAmbiguousBareOne = false`.
- `manualTaperRateBlockingMessage(rateText, lengthMm, setDiaMm, letDiaMm): String?` —
  blocking (non-null) when: the rate is blank but required to derive a missing end
  (`lengthMm > 0` and exactly one of SET/LET is present); the text is exactly `"1"`;
  or the text fails to parse via `parseTaperRateText(raw, allowAmbiguousBareOne = false)`.
  `null` means no blocking issue.
- `manualTaperRateWarning(rateText, lengthMm, setDiaMm, letDiaMm, maxRelativeSlopeError = 0.03f): String?` —
  non-blocking warning shown when Length + SET + LET are all present, the rate parses
  and isn't blocked, but its relative error against the geometry-derived exact slope
  exceeds `maxRelativeSlopeError`.

Do Nots
- Do not fabricate a rate from sentinel or zero diameters.
- Do not change the 3% tolerance without product approval (confirmed decision).
- Do not re-derive Auto/Manual mode from string comparison after initial seeding — mode
  is user-owned state (`AddComponentDialogs.md`).
- Do not write `taperRateText` to the model from a composition-time effect; only on
  explicit user commits.
- Do not convert units here; all inputs/outputs are canonical mm (slope is mm/mm,
  dimensionless).

Notes
- Dialog/card-side behavior (mode toggle, seeding rules, blank-revert semantics, warning
  display) is documented in `AddComponentDialogs.md` — this doc covers only the pure
  functions in `util/TaperRateAuto.kt`.
- `BORE_BREAK_MM = 152.4f` (6 in) and `COMPARABLY_CLOSE_MARGIN = 0.01f` are private
  constants in `TaperRateAuto.kt` backing the bore-preference tie-break described above.

Change Log
----------
**v1.0 (2026-07-18)**
- Initial contract for `TaperRateAuto.kt`'s `autoTaperRate`/`autoTaperRateText`/
  `parseTaperRateText`/`manualTaperRateBlockingMessage`/`manualTaperRateWarning`.
- Supersedes `TaperParser.md` and `TaperParserContract.md` (documented the deleted
  `util/TaperParser.kt`).
