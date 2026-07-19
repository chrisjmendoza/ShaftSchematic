# ShaftSchematic – Contracts Pack (v1.0, 2026-07-18)

**Purpose:** The authoritative per-subsystem contracts — invariants, behaviors, and
product decisions that the code alone can't express. Read the relevant doc before
editing a subsystem, and update it in the same change if behavior changes.
Project-wide invariants live in `CLAUDE.md`; repo-level references (architecture,
data model, validation, glossary, roadmap) live in `docs/` at the repo root.

**v1.0 consolidation:** the pack was reorganized from 31 micro-docs down to the 16
below — per-file API restatements were merged into subsystem contracts or deleted
where they merely mirrored code. Details: `docs/doc_sweep_2026-07-18.md`.

## Screen & editing

- **ShaftScreen.md** — editor screen contract: header, preview card (incl. preview-box
  styling), carousel, add button, IME rules, OAL keystroke-commit exception
- **AddComponentDialogs.md** — dialog/card parity rule (critical invariant), per-dialog
  field contracts, the InlineAddChooser entry point, auto/manual taper-rate UI rules
- **NumberField.md** — `NumericInputField` commit-on-blur contract (tap-and-leave
  no-op is a critical invariant) + the typing-filter and parsing pipeline beneath it
- **Defaults.md** — component default values (`AddDefaultsConfig.kt`) and the `addXAt`
  parameter-order contract (thread major-Ø before pitch!)
- **ComponentsOrdering.md** — carousel display order (resolved/physical, v1.2 —
  supersedes the locked newest-on-top rule; open product question)
- **FreeToEndBadge.md** — badge computation, placement, and visibility invariants

## Model & geometry

- **Model_Conventions.md** — model-layer rules: mm-only, pitch/TPI dual storage,
  component conventions
- **OverallLength.md** — OAL semantics: auto vs manual, coverage, excluded threads
- **TaperRate.md** — auto/manual taper-rate engine (`util/TaperRateAuto.kt`), 3% snap
  tolerance (confirmed product decision), sentinel guards
- **CouplerBoltSlot.md** — reference-only cutouts: never affect OAL, never collide,
  never split bodies (critical invariant)

## Rendering & documents

- **Rendering.md** — preview pipeline: ShaftDrawing host, ShaftLayout math,
  ShaftRenderer geometry, RenderOptions styling. PDF is a separate drawing path.
- **RunoutSheet.md** — runout + wear tabs, bubble collision engine
  (`geom/RunoutBubbleLayout.kt`), OAL alignment, PDF appearance options
- **PdfExport.md** — export route (SAF, Letter landscape 792×612) and PDF preview
  screen (options sheet, orientation unlock). Composer pipeline: `docs/PDF_EXPORT.md`.

## State & persistence

- **ShaftViewModel.md** — ViewModel responsibilities and state ownership
- **Persistence.md** — internal `.shaft` storage (atomic saves, migration, sample
  seeding), doc envelope format, units policy
- **Navigation.md** — route graph, editor container (sidebar + tabs), screen wiring

Pair each Kotlin file with a header comment referencing its contract doc and version.
