# PDF Export Route – Implementation Notes

⚠️ Implementation Notes

This document describes UI or implementation behavior.
It is not a system contract.

## PdfExportRoute
- Launches “Create Document” via SAF (no storage permissions).
- Builds a `PdfDocument`, creates a 792×612 pt page (US Letter landscape), calls `composeShaftPdf`, and writes to the chosen URI.
- Guards against multiple launches during recomposition.
- Notifies caller with `onFinished()` after success or cancel.

### Inputs
- `ShaftViewModel.spec` (mm)
- `ShaftViewModel.unit` (labeling)
- `ProjectInfo` metadata
- Default filename suggestion (timestamped)

### Error handling
- IO guarded by `runCatching`.
- Streams closed in `finally`.
- UI may show snackbar/toast on failure.

---

### Authority
This document describes current implementation behavior only.

Authoritative rules and invariants live in:
- docs/ARCHITECTURE.md
- docs/UI_CONTRACT.md
- docs/VALIDATION_RULES.md

If this document conflicts with a contract, the contract wins.
