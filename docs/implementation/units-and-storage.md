# Units & Storage – Implementation Notes

⚠️ Implementation Notes

This document describes UI or implementation behavior.
It is not a system contract.

## Units policy (v2)
- Canonical storage is millimeters in `ShaftSpec` today.
- UI unit is user-selected and persisted as “last used unit”.
- Import:
  - v2 reads `preferred_unit`; no lock is respected.
  - v1 ignores `unit_locked` for compatibility; `preferred_unit` is honored.
  - legacy uses current Settings default.
- Export currently uses v2 with `preferred_unit` only.

## UnitsStore behavior
- Stores preferences only (no geometry).
- Exposes flows for reactive updates.
- Optionally stores grid visibility and preview background mode.

## Internal storage notes
- Paths are sandboxed to internal storage.
- I/O is suspend/off-main.
- Provides directory resolution (projects, exports, cache) and atomic writes.
- Surfaces I/O errors as domain exceptions.

---

### Authority
This document describes current implementation behavior only.

Authoritative rules and invariants live in:
- docs/ARCHITECTURE.md
- docs/UI_CONTRACT.md
- docs/VALIDATION_RULES.md

If this document conflicts with a contract, the contract wins.
