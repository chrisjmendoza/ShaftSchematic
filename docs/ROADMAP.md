# ShaftSchematic Roadmap
Version: v0.4.x

This roadmap defines the grounded, realistic, and approved feature trajectory for ShaftSchematic.  
No unapproved features (multi-page PDF, BOM tables, compression) appear here.

---

# v0.4.x — Current Series
**Goals:**
- Complete architecture documentation
- Implement final rendering/stroke corrections
- Finalize taper rate logic in ViewModel
- Establish full validation rules
- Harden ShaftLayout for all edge cases
- Deliver stable single-page PDF export

**Outputs:**
- Batch 1–3 documentation
- Release candidate of core features

---

# v0.5.x — Component Expansion
**Primary Features:**
- Add Keyway component type
- Add Shoulder/Relief component type
- Add Coupling component type (hub + flange)
- Extend validation system to cover new types

**Rendering:**
- Add basic rendering rules for new components
- Maintain one-direction scaling and strict invariants

**UI:**
- Add dialogs for new component types
- Enhance component list with type icons

---

# v0.6.x — UX & Machinist Tools
**Focus Areas:**
- Component presets (commonly used diameters & lengths)
- Reference geometry overlays (ghosted)
- Improved selection/highlighting
- Export templates (title-only, compact view)

**Validation Enhancements:**
- Machining heuristics:
  - dangerous thin-wall indicators
  - incompatible taper dimensions
  - bearing/liner mismatch warnings

---

# v0.7.x — Optional Extensions
These are feature flags; not required for 1.0 but structurally compatible.

- Optional cloud save via SAF or Drive
- Import/export job metadata
- Optional DXF export (if approved later)

No PDF multi-page functionality.

---

# v1.0 — Production Release
**Definition of Done:**
- All component types implemented
- Full single-page PDF export
- All dialogs stable & validated
- High coverage unit tests for:
  - layout engine
  - renderer
  - validation rules
  - parsing
- Complete documentation set

**Non-goals:**
- Multi-page PDFs
- CNC G-code generation
- Finite element stress calculations

---

# Summary
This roadmap defines a focused progression toward a reliable, professional marine-machining design tool, with well-scoped incremental milestones and no unapproved feature drift.

All roadmap additions must be explicitly reviewed against this document.