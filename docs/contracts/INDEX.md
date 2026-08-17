# ShaftSchematic – Contracts Pack (v1.2, 2026-08-16)

**Purpose:** The authoritative per-subsystem contracts — invariants, behaviors, and
product decisions that the code alone can't express. Read the relevant doc before
editing a subsystem, and update it in the same change if behavior changes.
Project-wide invariants live in `CLAUDE.md`; repo-level references (architecture,
data model, validation, glossary, roadmap) sit one level up in `docs/`.

**v1.2 relocation:** the pack moved out of the source tree
(`app/src/main/java/com/android/shaftschematic/docs/`) to `docs/contracts/`, so the repo
has ONE documentation root. Code comments cited it as `docs/X.md`, which read as the
repo-root folder and made the files hard to find; every reference now spells
`docs/contracts/X.md` and resolves from anywhere.

**v1.0 consolidation:** the pack was reorganized from 31 micro-docs down to the
per-subsystem contracts below (16 at the time, **20** today) — per-file API restatements
were merged into subsystem contracts or deleted where they merely mirrored code.
(Sweep record in git history.) Design plans (`*_PLAN.md`) are not contracts — once their
work ships they move to `docs/archive/`; each is listed under the contract it belongs to.

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
- **RunoutSheet.md** — runout + wear tabs **and the Consolidated Output tab** (consolidated
  sheet variants, worn sections, shaft-height slider, Export all), bubble collision engine
  (`geom/RunoutBubbleLayout.kt`), profile compression, OAL alignment, PDF appearance
  options. Design plan: `docs/archive/WearDiaMeasurements_PLAN.md`.
- **UndercutDrawing.md** — Undercut Drawing tab/PDF: shaft-space (not component-keyed)
  undercut sections, cluster-window zoom, notch geometry against the local outer surface,
  chained + total dimension rails. Design plan: `docs/archive/UndercutDrawing_PLAN.md`.
- **PdfExport.md** — export route (SAF, Letter landscape 792×612) and PDF preview
  screen (options sheet, orientation unlock). Composer pipeline: `docs/PDF_EXPORT.md`.
- **FractionTypography.md** — how a fraction is SET wherever the app draws one: the pure
  parser + the one Canvas renderer behind every drawn fraction, the Stacked/Diagonal/Plain
  setting and its process-wide mirror, and why measure and draw must convert together.

## State & persistence

- **ShaftViewModel.md** — ViewModel responsibilities and state ownership
- **Persistence.md** — internal `.shaft` storage (atomic saves, migration, sample
  seeding), doc envelope format, units policy
- **Templates.md** — the template store (geometry-only, scrubbed on write), derived
  size/count bucketing, the browser + thumbnail, and why a loaded template starts unnamed
  and dirty. Design plan: `docs/archive/Templates_And_DiaVisibility_PLAN.md`
- **Navigation.md** — route graph, editor container (sidebar + tabs), screen wiring
- **Appearance.md** — app theme (System/Light/Dark + high contrast) and the sheet-ink
  invariant: paper-sheet canvases draw fixed ink, never theme colors

Pair each Kotlin file with a header comment referencing its contract doc and version.
