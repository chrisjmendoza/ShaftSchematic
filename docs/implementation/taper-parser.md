# Taper Parser – Implementation Notes

⚠️ Implementation Notes

This document describes UI or implementation behavior.
It is not a system contract.

## Behavior
- Supports `a:b`, fraction (`3/4`), and decimal (`0.75`).
- Output is slope ratio (mm/mm); no unit I/O here.
- Helpers derive SET/LET from slope and length when one endpoint is missing.
- Basic checks currently require length > 0 and derived diameters ≥ 0.

---

### Authority
This document describes current implementation behavior only.

Authoritative rules and invariants live in:
- docs/ARCHITECTURE.md
- docs/UI_CONTRACT.md
- docs/VALIDATION_RULES.md

If this document conflicts with a contract, the contract wins.
