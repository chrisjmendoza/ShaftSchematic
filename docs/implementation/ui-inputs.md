# UI Inputs – Implementation Notes

⚠️ Implementation Notes

This document describes UI or implementation behavior.
It is not a system contract.

## Numeric input behavior
- Currently no live writes while typing; commit-on-blur discipline.
- Field currently displays filtered text; callback receives raw text.
- Invalid input currently reverts to the last valid value on blur.

### Implementation details
- Maintain internal `currentText` and `lastValidText`.
- Filter user input with `filterNumericInput(...)` before parsing.
- Update `lastValidText` only when `parseValid(...)` succeeds.
- Invoke `onCommit(rawText)` only on valid blur/IME Done.
- Surface an optional error affordance on invalid commit attempts.

See also:
- docs/UI_CONTRACT.md (authoritative UI rules)

## Parsing helpers
- `parseFractionOrDecimal(text)` currently returns `null` on invalid input.
- `toMmOrNull(text, unit)` currently returns `null` on invalid input.
- Trims/sanitizes without throwing; formatting is handled at the UI edge.

## Text filters
- `filterNumericInput(raw, allowNegative, allowFraction)` is the shared filter.
- Currently allows digits, one decimal separator, optional leading `-`, optional slash, and spaces for `W N/D`.
- Currently rejects alphabetic/unit characters and multiple dots/slashes.

---

### Authority
This document describes current implementation behavior only.

Authoritative rules and invariants live in:
- docs/ARCHITECTURE.md
- docs/UI_CONTRACT.md
- docs/VALIDATION_RULES.md

If this document conflicts with a contract, the contract wins.
