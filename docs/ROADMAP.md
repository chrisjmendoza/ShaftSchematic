# ShaftSchematic Roadmap
Version: v0.5.x  
Last updated: 2026-08-16

This roadmap defines the grounded, realistic, and approved feature trajectory for ShaftSchematic.

---

# v0.4.x — COMPLETE

**Delivered:**
- Core architecture (MVVM, mm-only model, Layout/Renderer pipeline)
- Snap engine + tap-to-add pipeline *(both removed in v0.5.x — the gesture fired
  unintentionally and was never used on purpose, and nothing else snapped)*
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

- [ ] **Liner shoulders with a radius selector** — aft/fwd shoulder length fields and stepped
  shoulder rendering in preview and PDF, plus a fillet radius at each shoulder edge (a real
  machining instruction, not a drawing nicety). Open: per-end or per-liner, standard-radius
  list or free entry, and how it prints. The arc math now exists — `geom/BlendProfileMath.kt`
  is a general "join two radii across an axial span" primitive built for the body blend and
  meant to be called here too, so this is a call site rather than new math
- [ ] **Fiberglass body support** — per-body flag with the usual dialog/card parity; styling
  (dark fill vs hatch vs label) is **undecided and blocked on a sketch or photographed sheet**,
  the same way the "indicated wear" squiggle convention is
- [ ] **Additional output fonts** — a typeface choice for the printed sheets. Safe by
  construction (every metric is measured live from the `Paint`), but check the fraction stack
  against a condensed or slab face before shipping one
- [ ] **Runout bubble leader clarity** — the 2026-08-16 pointer rework (station-fidelity brake,
  center-aimed leaders, dogleg reroute) answered the reported case, and long-press-drag now
  covers *moving* a station to a measured spot. What is still open from the original request is
  **tap-to-place a NEW bubble** at an arbitrary station rather than adding one with `+` and
  dragging it. The drag work settled the hard parts (authored positions, the neighbour clamp,
  reading re-keys), so this is now mostly a gesture + insertion-index question
- [ ] **Drag on the compressed preview** — the Runout tab's canvas maps mm linearly while the
  printed sheet foreshortens, so a bubble dragged to look centred in the preview does not look
  centred on paper in a compressed region (the stored mm is correct either way). Teaching the
  canvas the sheet's piecewise `xAt` would close the gap; on-device the linear preview has been
  fine so far, so this is watch-and-see rather than queued
- [ ] **Drawing preset profiles** — named, **app-wide** sets of drawing prefs plus a
  section-wide "restore Drawing defaults". Per-job `RunoutConfig` (Shaft height, liner
  compression) deliberately stays per-document: a *look* is app-wide, a *fit* is per-job
- [ ] **Multi-shaft per job number** — plan in `docs/MultiShaftJob_Plan_2026-07-26.md`
  (derived job grouping over single-shaft files; no format change). Awaiting answers to its
  6 product questions
- [ ] **Taper validation wiring** — rate derivation errors shown inline on fields; slope
  validation when `lengthMm > 0`
- [ ] **Controller owns all VM-side intents** — the remaining ShaftScreen refactor work
  (carousel, preview panel and event wiring are already extracted); design work, not a pure
  move, and lower priority

**Delivered in v0.5.x so far** (newest first):
- Body face blends — a smooth machined transition from a body face into whatever diameter it
  steps to, in place of a square shoulder, with S-curve / Fillet / Eased-cone profiles. Machined
  inward out of the body that carries it, so no other component moves; diameters derived from the
  neighbour; silhouette only (no rail, no footer row), with a drawn-width floor so a 2" blend
  still reads on a compressed sheet. Fixed alongside it: two abutting explicit bodies fused into
  one resolved run at the aft-most diameter
- Tap-to-add removed — the Schematic preview's canvas tap is selection only; components are
  added from the FAB chooser, the single entry point. Took the whole snap pipeline with it
- Draggable runout bubbles — press and hold a bubble on the Runout tab's live preview to slide
  it along its component and mark the spot actually measured; the drag pins that one station
  (its siblings stay automatic) and is clamped between its neighbours, so the sheet still reads
  AFT→FWD and a typed TIR never lands on a different bubble. `+`/`−` insert into the widest gap
  and remove the most redundant unmeasured station, so they undo each other, and station counts
  now ride the same undo step as the positions and readings they change. Undo move / per-row
  Reset / Reset all bubble positions
- Wear document round — every liner gets a detail strip and tapers/bodies can be elected onto
  one; strips pack into as many rows as the page can pay for and share one vertical diameter
  scale, so a bigger component draws bigger; facing S-breaks spread apart instead of weaving;
  worn-profile trace through the measured diameters with a user-set depth exaggeration; end
  styles, positional titles, and a taper–liner join slider
- Runout bubble pointers — the even spread is braked by station fidelity, straight leaders aim
  at the circle center, and a leader that would graze a foreign circle reroutes to a dogleg
- Fraction typography — one parser + one renderer behind every drawn fraction, set as a real
  stacked or diagonal fraction (Settings → Drawing → "Fractions"), never a Unicode vulgar glyph
- Templates + per-component Ø visibility — save-as-template and a browser bucketed by liner
  size/count (geometry only, metadata scrubbed); "Show Ø on drawing" per body/liner and for the
  bare shaft; runout stations derived per component at one per 20"; bubble counts editable from
  the Consolidated tab
- Coupling face end view — optional outward-keyseat end view on the runout sheets, per-job
  toggle (default off), with its own pilot runout reading
- Live preview tuning — Line thickness, Body S-break, Shaft height and Liner compression
  re-render the open preview under a dragging finger (draft raster, commit on release), with the
  sheet shown as a fit-width ink-band page strip so the control never covers the page
- Dimension arrow size (Small/Medium/Large) and wear-depth exaggeration as user settings
- Undercut Drawing — its own editor tab and PDF (`UndercutPdfComposer`) for
  machined-below-surface cuts: shaft-space spans (a cut may cross a liner edge), the settled
  open-notch convention (silhouette step + full-height section faces, mouth never lidded),
  liner-anchored detail strips, a per-sheet cut-depth exaggeration slider, and user-selectable
  shading / line-art styles
- Consolidated Output tab — one sheet carrying the schematic's rails and footer plus the
  elected runout/wear content (`ConsolidatedVariant`: All three | Schematic + Runout |
  Schematic + Wear), the worn-section editor (values printed inside the profile over
  knockout halos), and **Export all** — the checked documents batch-written to one picked
  folder. The classic standalone runout sheet stays on the Runout tab
- Profile sizing round — per-job value-based "Shaft height" slider (in paper inches, hard
  capped at 1.5"), a proportional default sizing curve (4" → 0.5", 8" → 1") with
  user-adjustable anchors in Settings → Drawing → "Default drawing size", the
  liner-compression control with height precedence (it never lowers the drawn height), an
  S-break pair minimum gap with a user-set compression threshold (Settings → Drawing →
  "Body S-break"), and even-spread runout bubbles
- Appearance settings — System/Light/Dark + high contrast for the Compose chrome, with the
  white paper sheets pinned to fixed ink so dark mode can never print invisible drawings
- Help screen and Achievements screen
- Hardened exports — every SAF write goes through `util/PdfSafExport` (a composer throw
  yields a valid error page, never a truncated file) and the collision export gate now
  guards every export surface
- Undo/redo — session-scoped `SessionHistory` over an `EditState` snapshot (spec + wear +
  runout + order), 600 ms coalescing, 50-step cap, exposed as an editor history menu
- Machining heuristic warnings — Ø-step and liner-OD-vs-shaft rules shipped on carousel
  cards (`ui/util/ComponentWarnings.kt`)
- Keyways 90° apart — spec-level clocking note alongside Keyways 180° apart (mutually
  exclusive), with a CW/CCW direction chip pair measured from the AFT keyway, viewed from
  aft; renders as a depth-deep notch on the silhouette's top/bottom edge (not a hidden
  dashed line) and is not paired with a spoon bowl
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
- Body keyways — keyways now host on bodies too (AFT/FWD end reference, open + floating), for intermediate shafts with fitted couplings that end on a plain body; survive body split/merge by absolute position
- Keyways 180° apart — spec-level clocking note; renders the far-side (non-aft-most) keyway as hidden dashed lines and prints a footer note; aft-most keyway stays solid as the measurement datum
- Shared signing config — single debug.keystore, all machines update-install without data wipe
- Selection highlight — single thin ring, seeded on file load
- Warning badge system — yellow per-component chips, 3-state free-to-end badge
- ShaftScreen.kt carousel extracted to `ComponentCarousel.kt`
- Sidebar nav (5 tabs: Schematic / Runout Sheet / Wear Document / Undercut Drawing /
  Consolidated Output)
- Runout drawing — inline shaft preview, scrollable layout, collision-free alternating bubble placement (shared `geom/RunoutBubbleLayout.kt` engine), TIR direction label
- Wear document — shaft profile + header + dye-pen PASS/FAIL checkboxes
- Liner wear areas — tap-to-inspect liners, wear-spot recording (SET/liner-edge
  references, blocking span validation), PDF detail strips with dimension rails
- Wear pits — tap-to-place "X" markers (small/large) on liners, tapers, and bodies;
  drawn on the wear document's profile + strips
- Runout bubble editor — tap a bubble to record TIR value + high-spot clock marker,
  printed in place on the runout sheet
- Wear diameter measurements — "Add Ø" tool in the wear overlay records measured
  diameters at tapped stations; printed as value callouts with leaders + witness ticks
  (liner readings on their detail strip, body/taper readings under the profile; shared
  `geom/WearDiaCalloutLayout.kt` engine)
- Blank drafts + direct print — write-in (lines-in/values-out) variants of all three
  documents, plus Android print-framework output identical to export
- Line thickness control — Settings slider 50%–200%, DataStore-persisted, applies to preview and PDF strokes
- OAL include-thread fix — bracket spans SET-to-SET (excluded) or shaft-end-to-SET (included); label always equals typed OAL
- Unsaved-changes guard — "Save / Discard / Cancel" dialog when New or Open is triggered with unsaved work; dirty state tracks spec + metadata fields against last save or load
- Component collision detection — global overlap scan across all non-excluded components; both colliding parties show red error chip; PDF export and preview blocked until collisions are resolved
- Body engine refinements — auto-body card gains a "Make editable body" checkbox (since relabeled "Explicit body", now the only promotion path); a keyed body is never split (stays one whole card); `mergeBodiesAround` won't merge across a component still occupying the freed span (no phantom long bodies). (Bodies remain fluid fillers and do not collide — a brief "non-negotiable bodies" experiment was reverted 2026-07-21 because it flagged normal body-under-sleeve as errors.)

---

# v0.6.x — UX & Machinist Tools

- Component presets (commonly used taper rates, diameters)
- Reference geometry overlays (ghosted previous measurement)
- Machining heuristic warnings: steep taper (diameter discontinuity and liner-OD-vs-shaft
  shipped in v0.5.x; the taper-vs-body Ø-mismatch advisory was removed by product decision
  and is not planned)
- Dual-unit display — **shipped** in v0.5.x (2026-08-18): inline `1 1/2" [38.1 mm]` alongside
  per-component mixed units, and a **stacked** two-line layout (Settings → Drawing →
  "Dual-unit layout"). The stack turned out to be NARROWER than the inline pair, so it seats
  values back inside the dimension line and largely pays for its own height; see
  `docs/DualUnitStacking_PLAN.md`.

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

---

# Summary

Focused progression toward a reliable, professional marine-machining design tool.  
No unapproved feature drift. All roadmap additions require explicit review against this document.
