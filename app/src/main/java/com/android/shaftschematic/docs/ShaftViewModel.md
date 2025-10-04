ShaftViewModel Contract
-----------------------

Layer: UI → ViewModel  
Purpose: Owns editable ShaftSpec state, unit selection, grid toggle, and routes all commits from the UI to the model/persistence.

Version: v0.1 (2025-10-04)

Invariants
- All stored geometry is **canonical millimeters (mm)**.  
- Conversions (mm ↔ in) happen **on commit** from UI text → mm once.  
- Exposed state uses StateFlow; UI reads snapshots.

Responsibilities
- Hold `ShaftSpec`, `UnitSystem`, `showGrid`, and project meta fields.  
- Commit APIs accept raw text (e.g., `onSetOverallLengthRaw`), parse, convert, clamp, and update.  
- Expose derived values (e.g., `freeToEndMm`) from the model.  
- Load/save specs via `ShaftRepository`.

Do Nots
- Do not format values for display (UI edge only).  
- Do not perform rendering/layout math.  
- Do not mutate from inside composables; use explicit commit calls.

Notes
- Use `parseFractionOrDecimal` and `toMmOrNull` helpers for consistency.  
- Guard against negative lengths; no-op on invalid parse.  
- Emit minimal updates to avoid recomposition thrash.

Future Enhancements
- Undo/redo stack.  
- Debounced autosave.  
- Multi-spec project lists.
