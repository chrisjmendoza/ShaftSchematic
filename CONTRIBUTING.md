# Contributing to ShaftSchematic

## Before you edit anything

Per-subsystem contract docs live in `app/src/main/java/com/android/shaftschematic/docs/`
(index: `README.md` in that folder). **Read the relevant contract doc before editing a
subsystem**, and update it in the same change if you alter behavior. Project-wide
conventions and critical invariants are in `CLAUDE.md` at the repo root.

## Architecture overview

**Model (mm only):** `ShaftSpec` + components (`Body`, `Taper`, `Threads`, `Liner`,
`CouplerBoltSlot`). All geometry is stored in millimeters. Unit conversion (mm ↔ in)
happens only at the UI edge for display and input — never in the model, ViewModel,
or renderers.

**State/VM:** `ShaftViewModel` exposes StateFlow for spec, unit, grid, and project meta.
The VM parses/normalizes UI input → mm. Always instantiate via `ShaftViewModelFactory`.

**UI:** `ShaftEditorRoute` hosts the sidebar and the Schematic / Runout / Wear tabs;
`ShaftRoute` wires the VM to `ShaftScreen` and owns SAF PDF export. `ShaftScreen` is
pure Compose Material3 UI. The carousel lives in `ComponentCarousel.kt`.

**Rendering — two separate paths (important):**

- Preview: `ShaftDrawing` (Compose host) → `ShaftLayout.compute()` (mm→px mapping)
  → `ShaftRenderer` (DrawScope geometry).
- PDF: `ShaftPdfComposer` / `RunoutPdfComposer` / `WearPdfComposer` use the same model
  and layout math but draw with **their own Canvas code**.

A fix in the preview renderer does **not** propagate to the PDF composers (or vice
versa) automatically. When you change how a component draws, check both paths.

## Data flow

1. User edits fields → `ShaftScreen` calls VM setters (commit-on-blur; see
   `docs/NumberField.md` — a tap-and-leave with no edit must be a no-op).
2. VM updates StateFlow → routes/screens recompose.
3. Preview and documents render from the **resolved** component list
   (`ui/resolved/ResolvedComponent.kt`), which derives auto-bodies for unoccupied
   spans without persisting them.

## Coding guidelines

- **Units:** keep geometry in mm. Convert at the edges (formatting, grid legend,
  dimension labels).
- **Immutability:** treat state as immutable data classes; use
  `MutableStateFlow.update { it.copy(...) }`.
- **Compose:** prefer stable Material3 APIs; isolate experimental APIs behind `@OptIn`.
- **No geometry logic in composables** — the VM and `geom/` are the geometry
  authorities.
- **Packages:** align folder ↔ package names (e.g. `pdf/` for
  `com.android.shaftschematic.pdf`). Current top-level packages: `model/`, `geom/`,
  `doc/`, `io/`, `data/`, `pdf/`, `settings/`, `ui/` (`drawing/`, `screen/`, `input/`,
  `resolved/`, `order/`, `viewmodel/`, `nav/`, `dialog/`, `config/`, `util/`,
  `theme/`), `util/`.
- **Comments/KDoc:** file header "What this does / Inputs / Outputs" on renderer/layout
  files; KDoc public functions.

## Adding a new component type (quick recipe)

1. **Model:** add the data class in `model/` (all fields in mm).
2. **ViewModel:** add `addX(...)`, `removeX(id)`, parsing helpers (raw → mm).
3. **Resolved pipeline:** decide how it participates in `resolveComponents()`
   (ordering, auto-body interaction, collision group).
4. **Renderer:** draw preview geometry in `ShaftRenderer`.
5. **PDF:** add drawing code to the PDF composer(s) — this is a separate path and
   will not pick up the preview code.
6. **UI:** add the carousel card and the Add dialog. They must mirror each other
   control-for-control (see `CLAUDE.md` invariants and `docs/AddComponentDialogs.md`).
7. **Validation:** update validation/warning rules if the component affects coverage
   or collisions.
8. **Docs:** add or update the contract doc in the in-source `docs/` folder.

## Commits

Do not auto-commit; changes are reviewed before every commit. Keep tooling updates in
separate `chore(build)` commits.
