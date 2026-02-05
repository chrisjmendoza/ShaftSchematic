# Validation Rules (Current Behavior)
Version: v0.5 (2026-02-01)

## Purpose
This document describes **current** validation behavior as implemented today. It does **not** promise a full validation pipeline.

Validation is enforced only at three boundaries:
- **UI commit boundary** (numeric fields): invalid input does not commit.
- **Parsing helpers**: invalid input is rejected during parsing.
- **Model invariants**: basic sanity checks when values are written.

See also: [Parsing helpers](docs/implementation/taper-parser.md).

## Authority
This document is authoritative for current validation behavior.
If other documentation conflicts with this file, this file takes precedence.

---

# 1. UI Commit Boundary (Numeric Fields)

Numeric fields:
- Filter input as the user types.
- Commit **only** on successful parse.
- Revert to the last valid value on blur/Done if parsing fails.

This prevents malformed input from reaching the model, but it is **not** a global validation pass.

---

# 2. Parsing Helpers

Parsing helpers reject invalid input:
- `parseFractionOrDecimal(...)` returns `null` for malformed input.
- `toMmOrNull(...)` returns `null` if parsing fails.

The UI uses these helpers to decide whether to commit values. This avoids NaN/∞ creation through normal UI paths.

---

# 3. Model Invariants (Basic Sanity Only)

Model-level checks are minimal and structural:
- Non-negative lengths/diameters.
- Segment validity against overall length (basic bounds).

There is **no** full-spec validation pass, warning tier system, or batch audit today.

Limited overlap prevention exists only where explicitly checked in UI-level helpers (e.g., start overlap guardrails for specific components). No global overlap detection is guaranteed.

---

# 4. Out of Scope / Future Validation

The following concepts are **intentionally not implemented yet** and are **not** guaranteed by current code:
- Global overlap warnings.
- NaN/∞ audits beyond parsing boundaries.
- Severity-based validation (warning vs error).
- Full-spec validation passes (pre-export or batch).

Future validation designs are documented separately as non-binding references.

---

# 5. Summary

Current validation is **local and boundary‑driven**. It prevents malformed input from entering the model, but does not provide comprehensive geometry auditing or warning tiers.