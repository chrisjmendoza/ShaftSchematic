# ShaftSchematic Roadmap
Version: v0.5.x  
Last updated: 2026-05-30

This roadmap defines the grounded, realistic, and approved feature trajectory for ShaftSchematic.

---

# v0.4.x — COMPLETE

**Delivered:**
- Core architecture (MVVM, mm-only model, Layout/Renderer pipeline)
- Snap engine + tap-to-add pipeline
- OAL window + excluded thread logic
- Taper rate input (all formats: 1:12, 3/4, decimal)
- Full single-page PDF export (landscape, theme-safe, dimension tiers, footer)
- PDF label collision avoidance, measurement tiering system
- Internal save/open, autosave/draft restore
- Unit switching mm ↔ inch (persisted)
- Component delete + multi-step undo
- Settings (units, grid, PDF prefs, colors)
- Complete validation system — blocking errors (red) + warnings (yellow) wired throughout UI

---

# v0.5.x — Current Series

**In progress / next up:**

- [ ] **ShaftScreen.kt refactor** — extract carousel, preview panel, and event wiring into separate files. No behaviour change. Needed before the next round of feature additions.
- [ ] **Taper validation wiring** — rate derivation errors shown inline on fields; slope validation when `lengthMm > 0`
- [ ] **Liner shoulders** — aft/fwd shoulder length fields; stepped shoulder rendering in preview and PDF
- [ ] **Fiberglass body support** — model flag, dark fill/hatch, label annotation

**Delivered in v0.5.x so far:**
- Taper keyway drawing (open + floating) — plan-view schematic convention, mill-cutter arc, white fill
- Shared signing config — single debug.keystore, all machines update-install without data wipe
- Selection highlight — single thin ring, seeded on file load
- Warning badge system — yellow per-component chips, 3-state free-to-end badge

---

# v0.6.x — UX & Machinist Tools

- Component presets (commonly used taper rates, diameters)
- Reference geometry overlays (ghosted previous measurement)
- Undo/redo architecture
- Machining heuristic warnings: diameter discontinuity, liner OD vs shaft, steep taper
- Dual-unit display (primary inch, secondary mm in smaller text)

---

# v0.7.x — Optional Extensions

Feature-flag items; not required for 1.0 but structurally compatible:

- Optional cloud save via SAF or Drive
- Import/export job metadata
- Optional DXF export (if approved)

---

# v1.0 — Production Release

**Definition of Done:**
- All planned component types implemented (Body, Taper, Threads, Liner, Liner shoulders)
- Full single-page PDF export with all dimension conventions
- Complete validation (blocking + warnings) for all component types
- High unit test coverage: layout engine, renderer, validation, parsing
- Complete documentation set (BRIEFING, ARCHITECTURE, contracts)
- ShaftScreen.kt refactored — no single file > ~400 lines

**Non-goals (never):**
- Multi-page PDFs
- CNC G-code generation
- Finite element stress calculations
- BOM / machining tables
- Body keyway (shelved — no marine propeller shaft use case identified)

---

# Summary

Focused progression toward a reliable, professional marine-machining design tool.  
No unapproved feature drift. All roadmap additions require explicit review against this document.
