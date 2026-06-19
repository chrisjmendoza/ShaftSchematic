ShaftViewModel Contract
-----------------------

Layer: UI → ViewModel  
Purpose: Owns editable ShaftSpec state, unit selection, grid toggle, and routes all commits from the UI to the model/persistence.

Version: v0.2 (2026-06-18)

Invariants
- All stored geometry is **canonical millimeters (mm)**.  
- Conversions (mm ↔ in) happen **on commit** from UI text → mm once.  
- Exposed state uses StateFlow; UI reads snapshots.

Responsibilities
- Hold `ShaftSpec`, `UnitSystem`, `showGrid`, and project meta fields.  
- Commit APIs accept raw text (e.g., `onSetOverallLengthRaw`), parse, convert, clamp, and update.  
- Expose derived values (e.g., `freeToEndMm`) from the model.  
- Load/save specs via `ShaftRepository`.
- Hold persisted display settings: `lineThicknessScale` (0.5–2.0, applied to preview and PDF stroke widths; 1.0 = default thin weight, 2.0 = original thick weight).

Add APIs
- `addLinerAt(startMm, lengthMm, odMm, reference: LinerAuthoredReference = AFT)` — the `reference` parameter records which end the user measured from; stored on `Liner.authoredReference` for the carousel edit card to display correctly. The default is `AFT` for the quick-add path which does not ask for a reference.

Do Nots
- Do not format values for display (UI edge only).  
- Do not perform rendering/layout math.  
- Do not mutate from inside composables; use explicit commit calls.

Notes
- Use `parseFractionOrDecimal` and `toMmOrNull` helpers for consistency.  
- Guard against negative lengths; no-op on invalid parse.  
- Emit minimal updates to avoid recomposition thrash.

Future Enhancements
- Debounced autosave.  
- Multi-spec project lists.

Change Log
----------
**v0.2 (2026-06-18)**
- `addLinerAt` now accepts an optional `reference: LinerAuthoredReference` parameter (default `AFT`). Passed through from `AddLinerDialog` via `ShaftScreen` → `ShaftRoute` → ViewModel.

**v0.1 (2025-10-04)**
- Initial contract document.
