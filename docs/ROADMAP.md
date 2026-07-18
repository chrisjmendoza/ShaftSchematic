# ShaftSchematic Roadmap
Version: v0.5.x  
Last updated: 2026-07-11

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

- [ ] **Taper validation wiring** — rate derivation errors shown inline on fields; slope validation when `lengthMm > 0`
- [ ] **Liner shoulders** — aft/fwd shoulder length fields; stepped shoulder rendering in preview and PDF
- [ ] **Fiberglass body support** — model flag, dark fill/hatch, label annotation
- [ ] **Preview panel + event wiring extraction** — remaining ShaftScreen refactor work (lower priority)

**Delivered in v0.5.x so far:**
- Backup & restore for saved shafts — Settings → Data gains "Back up all
	shafts…" (single zip to any picked location: Drive, Downloads, SD card) and
	"Restore from backup…" (never overwrites; identical docs skipped, collisions
	renamed "(restored)"). Open screen gains per-file Import; Save screen gains
	"Save a copy to device…". Automatic pre-update snapshots (zip of the saves
	folder, kept ×3) run before any migration/seeding, and Android Auto Backup /
	device-transfer rules now include `shafts/`. Root cause of saves lost on
	update fixed: sample pruning now only deletes files byte-identical to what
	was seeded (SHA-256 ledger); user-edited samples are never touched
- Auto taper-rate workflow — Add Taper and carousel taper cards now include
	`Rate mode: Auto | Manual` (Auto default). In Auto mode, rate is computed from
	Length + SET + LET, snaps to nearby common shop tapers (3% slope tolerance), and
	falls back to exact `1:N.NNN` when no common taper is close; manual mode rejects
	bare `1`, allows `1/1`, requires a rate when deriving a missing end, and warns
	when typed rate text disagrees with Length + SET + LET
- Taper rate keyboard compatibility — taper rate inputs now accept colon-ratio
	entry (`1:12`) on Android keyboards that omit `:` on numeric pads by using an
	ASCII-capable field + colon-aware filtering for the rate path
- Coupler bolt slots — reference overlay for muff-coupling bolt cutouts; new `CouplerBoltSlot` model type + `ShaftSpec.couplerBoltSlots` list, add-chooser entry, add dialog, carousel card; row of `count` cutouts at `spacingMm` pitch drawn straddling the shaft outline (mirrored top/bottom) in preview and all three PDFs; AFT/FWD authored reference (default FWD); pure reference — excluded from OAL/coverage, collision, and body split/merge; dimension rail toggle present but deferred (not drawn in v1)
- Taper keyway drawing (open + floating) — plan-view schematic convention, mill-cutter arc, white fill
- Shared signing config — single debug.keystore, all machines update-install without data wipe
- Selection highlight — single thin ring, seeded on file load
- Warning badge system — yellow per-component chips, 3-state free-to-end badge
- ShaftScreen.kt carousel extracted to `ComponentCarousel.kt`
- Sidebar nav (3 tabs: Schematic / Runout Sheet / Wear Document)
- Runout drawing — inline shaft preview, scrollable layout, collision-free alternating bubble placement (shared `geom/RunoutBubbleLayout.kt` engine), TIR direction label
- Wear document — shaft profile + header + dye-pen PASS/FAIL checkboxes
- Line thickness control — Settings slider 50%–200%, DataStore-persisted, applies to preview and PDF strokes
- OAL include-thread fix — bracket spans SET-to-SET (excluded) or shaft-end-to-SET (included); label always equals typed OAL
- Unsaved-changes guard — "Save / Discard / Cancel" dialog when New or Open is triggered with unsaved work; dirty state tracks spec + metadata fields against last save or load
- Component collision detection — global overlap scan across all non-excluded components; both colliding parties show red error chip; PDF export and preview blocked until collisions are resolved

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
