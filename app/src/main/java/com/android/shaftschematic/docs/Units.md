# Units Policy (v2)

- Canonical storage: **millimeters** in `ShaftSpec`.
- UI unit: chosen by the user, persisted to Settings as the “last used unit”.
- Import:
    - v2: reads `preferred_unit`; **no lock** is respected.
    - v1: `unit_locked` is ignored (for compatibility); `preferred_unit` is honored.
    - legacy: uses current Settings default.
- Export: v2 with `preferred_unit` only.

**Goal:** A shop can open any file, freely switch units, and print/export in the desired unit without re-saving the document.
