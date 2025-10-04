TaperParser Contract
--------------------

Layer: Util  
Purpose: Parse user-provided taper rate inputs and derive missing SET/LET when instructed.

Version: v0.1 (2025-10-04)

---

Invariants
-----------
- Supports `a:b`, fraction (`3/4`), and decimal (`0.75`) forms.
- Output is **slope ratio (mm/mm)**; no unit IO here.

---

Responsibilities
----------------
- `parseTaperRate(text)` → slope (Float).
- Helpers to derive SET/LET from slope and length when one endpoint is missing.
- Validation: length > 0 mm, derived diameters ≥ 0.

---

Do Nots
--------
- Do **not** write to model directly.
- Do **not** convert units or format labels.

---

Notes
------
- When both SET & LET are present, **ignore** rate (display-only).

---

Future Enhancements
-------------------
- Support “1:12 legacy bare `1` == 1:12”.

---

Change Log
-----------
**v0.1 (2025-10-04)** Initial contract.
