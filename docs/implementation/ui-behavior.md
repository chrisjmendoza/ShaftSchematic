# UI Behavior – Implementation Notes

⚠️ Implementation Notes

This document describes UI or implementation behavior.
It is not a system contract.

## Overall Length – auto vs manual
- Default: Overall starts at 0 with a ghost placeholder.
- Auto mode: Overall currently grows to the last occupied end of all components.
- Manual lock: committing a value currently switches to manual and holds it.
- Unlock: clearing the field currently switches back to auto.

## ViewModel-owned behavior (observed)
- Oversize handling: if components extend past manual overall, the input shows an error and the free‑to‑end badge turns red with a negative value.
- Signed free‑to‑end logic uses `overallLengthMm - lastOccupiedEndMm`.
- Manual vs auto overall switching is driven by committed input state.

## Free‑to‑End badge
- Normal styling uses surface colors; oversize uses error colors.

## Preview box
- Fixed-height band with a consistent aspect ratio.
- Renders via `ShaftDrawing(spec, unit, ...)` without altering geometry.
- IME safety is handled by the screen scaffold, not the preview itself.

## Inline add chooser dialog
- Modal chooser for Body/Liner/Thread/Taper.
- Invokes the corresponding callback and dismisses.
- Includes a Close button that dismisses only.

---

### Authority
This document describes current implementation behavior only.

Authoritative rules and invariants live in:
- docs/ARCHITECTURE.md
- docs/UI_CONTRACT.md
- docs/VALIDATION_RULES.md

If this document conflicts with a contract, the contract wins.
