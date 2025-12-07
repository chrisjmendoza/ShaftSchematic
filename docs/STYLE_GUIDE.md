# ShaftSchematic Style Guide
Version: v0.4.x

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
- `shaftWidth` for bodies, tapers, liners' top/bottom
- `dimWidth` for ticks, hatch, dimensions

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
Follow the package map from Architecture.md

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

Instrumentation tests for:

Dialog behavior

StateFlow integration

Preview rendering correctness

Snapshot testing is not implemented unless explicitly added later.

8. Summary
This style guide enforces consistency, architectural discipline, and long-term maintainability across ShaftSchematic.

All contributions must follow this document.
