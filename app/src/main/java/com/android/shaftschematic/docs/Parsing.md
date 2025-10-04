Parsing Contract
----------------

Layer: Util  
Purpose: Canonicalize parsing logic for decimals, fractions, and unit-aware conversion to mm.

Version: v0.1 (2025-10-04)

Invariants
- Fraction grammar supports `N/D`, `W N/D`, and decimal forms.  
- Pure functions; no side effects.

Responsibilities
- `parseFractionOrDecimal(text)` → Float (in entered units).  
- `toMmOrNull(text, unit)` → Float mm (null on invalid).  
- Trim/sanitize without throwing.

Do Nots
- Do not format output strings.  
- Do not read UI state or theme.
