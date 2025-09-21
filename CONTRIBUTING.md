-- Architecture overview --

Model (mm only): ShaftSpec + components (Body, Taper, ThreadSpec, Liner). All geometry stored in millimeters.

State/VM: ShaftViewModel exposes StateFlow for spec, unit, showGrid, and project meta. VM parses/normalizes UI input → mm.

UI: ShaftRoute wires VM ↔ ShaftScreen, owns SAF PDF export. ShaftScreen is pure Compose Material3 UI.

Rendering (single source of truth):

ShaftDrawing (preview Composable) builds RenderOptions and computes ShaftLayout.

ShaftRenderer (DrawScope extension) draws grid + geometry + labels.

ShaftPdfComposer renders the same layout/renderer to PDF (+ title block).

-- Data flow --

User edits fields → ShaftScreen calls VM setters.

VM updates StateFlow → ShaftRoute/Screen recomposes.

Preview: ShaftDrawing(spec, unit, showGrid) → ShaftLayout.compute() → ShaftRenderer.draw().

Export: ShaftPdfComposer.exportToStream(spec, unit, showGrid, title) mirrors preview.

-- Coding guidelines --

Units: keep geometry in mm. Convert at the edges (formatting, grid legend, dimension labels).

Immutability: treat state as immutable data classes; use MutableStateFlow.update { it.copy(...) }.

Compose: prefer stable Material3 APIs; avoid experimental unless isolated behind @OptIn.

Rendering: do NOT duplicate drawing logic in multiple places. Route all drawing through ShaftLayout + ShaftRenderer.

Packages: align folder ↔ package names (e.g., pdf/ for com.android.shaftschematic.pdf).

Imports: run “Optimize Imports” after edits; avoid bringing in lifecycle-compose unless needed.

Comments/KDoc: add a file header “What this does / Inputs / Outputs” on renderer/layout; KDoc public functions.

Adding a new component (quick recipe)

Model: add fields to the component data class (mm).

ViewModel: add addX(...), removeX(id), parsing helpers (raw → mm).

Renderer: draw geometry inside ShaftRenderer.draw(...) (use layout.pxPerMm + helpers).

UI: add inputs in ShaftScreen (labels reflect UnitSystem), then call VM setters.

PDF: no special work if you used the shared renderer.