# ShaftSchematic Style Guide
Version: v0.5.x

This document defines the conventions for Kotlin, Compose, architectural boundaries, commit messages, and file organization.

---

# 1. Kotlin Code Style

- Use `val` by default; use `var` only for local state in UI.
- Use data classes with immutable fields.
- Use explicit types for public APIs.
- Keep functions pure unless mutating ViewModel state.

### Naming
- Classes: `PascalCase`
- Functions: `camelCase`
- Constants: `ALL_CAPS`
- Files: One top-level class per file

### Comment Conventions (2026-07-28)

- **No date stamps in code comments.** The *when* belongs to git history and
  `CHANGELOG.md`; comments carry the timeless *why*. ("(2026-07-21, by design)" → "(by
  design)".)
- **No prior-code narratives.** Comments describe current functionality and constraints
  only — never what removed/replaced code did or which bug it caused. A new contributor
  must never read about code that no longer exists. Load-bearing warnings keep the
  constraint and its consequence in present/conditional tense: "doing X would cause Y",
  not "the old X did Y and was removed".
- Dates and history ARE welcome in `docs/*.md`, `CHANGELOG.md`, and commit messages —
  that is the changelog layer. Citing a doc by filename (e.g.
  `docs/Autosave_Incident_2026-07-25.md`) from a comment is fine.
- Attribute user-driven changes neutrally ("on-device report"), never by name.

---

# 2. Compose UI Style

### Rules:
- UI must NEVER perform geometry or px-per-mm math.
- Use `collectAsState()` for StateFlow observations.
- Use `commit-on-blur` for all numeric fields.
- Use local state (`remember`) only for editing buffers.

### File Placement
- Screens/dialogs → `ui/screen/`
- Input fields → `ui/input/`
- Drawing composables → `ui/drawing/compose/`

---

# 3. Rendering & Layout Style

These rules enforce architecture boundaries:

### Layout:
- Only in `ui/drawing/render/ShaftLayout.kt`
- Only computes pixel coordinates
- Never reads fields directly from UI

### Renderer:
- Only in `ui/drawing/render/ShaftRenderer.kt`
- Only consumes Layout Result
- Never performs unit or geometry calculations

### Stroke Rules:
Both live on `RenderOptions` (`ui/drawing/render/RenderOptions.kt`):
- `outlineWidthPx` for bodies, tapers, liners' top/bottom, envelopes
- `dimLineWidthPx` for ticks, hatch, dimensions and other auxiliary lines

Renderer may not invent new stroke sizes.

---

# 4. ViewModel Style

- All state mutations via `_spec.update { … }`
- Never expose MutableStateFlow publicly
- ViewModel interprets taper rate, unit conversions, validation
- UI triggers intent methods only

Good example:
```
fun updateBody(id: String, newValue: Float) {
    _spec.update { it.updateBody(id) { b -> b.copy(diaMm = newValue) } }
}
Bad example (illegal):
```
```
body.diaMm = newValue // mutation of model object
```
5. Commit Message Convention
Format:

<type>: <short summary>

Optional detailed body.
Types:

feat: new feature

fix: bug fix

refactor: no behavior change

docs: documentation update

test: test additions

chore: non-functional changes

Examples:

feat: add new taper dialog with rate derivation
fix: correct liner end-tick stroke usage
docs: rewrite PDF export spec
6. Directory Rules
Do:
Follow the package map from ARCHITECTURE.md

Keep each class in a single file

Keep rendering code isolated from UI

Do Not:
Create ui/components/

Add geometry math to UI layer

Duplicate renderer logic anywhere

7. Testing Style
Use Arrange → Act → Assert

Unit tests for:

Taper rate parsing

Thread normalization

Model migration

Layout coordinate mapping

Compose UI tests (Robolectric, `@RunWith(RobolectricTestRunner::class)` +
`createComposeRule()`) run on the **JVM** under `src/test/` — dialog/field behavior,
commit-on-blur, and StateFlow integration are covered there, so they run with the normal
unit-test task and need no device or emulator. Prefer this over `src/androidTest/` for
anything that does not require real device services.

Instrumentation tests (`src/androidTest/`) for:

Anything requiring a real device/emulator

Preview rendering correctness

Snapshot testing is not implemented unless explicitly added later.

8. Summary
This style guide enforces consistency, architectural discipline, and long-term maintainability across ShaftSchematic.

All contributions must follow this document.
