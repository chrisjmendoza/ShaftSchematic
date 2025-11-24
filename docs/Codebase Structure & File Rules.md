1) Package map (canonical)
   com.android.shaftschematic
   ├─ MainActivity.kt
   ├─ data/
   │  ├─ MetaInfo.kt
   │  ├─ NoopShaftRepository.kt
   │  ├─ ShaftFileRepository.kt
   │  └─ ShaftRepository.kt
   ├─ model/
   │  ├─ Body.kt
   │  ├─ Liner.kt
   │  ├─ Numbers.kt
   │  ├─ Segment.kt
   │  ├─ ShaftSpec.kt
   │  ├─ ShaftSpecMigrations.kt
   │  ├─ Taper.kt
   │  └─ ThreadSpec.kt
   ├─ pdf/
   │  └─ ShaftPdfComposer.kt
   ├─ ui/
   │  ├─ drawing/
   │  │  ├─ DrawingConfig.kt
   │  │  ├─ ReferenceEnd.kt
   │  │  ├─ compose/
   │  │  │  └─ ShaftDrawing.kt
   │  │  └─ render/
   │  │     ├─ RenderOptions.kt
   │  │     ├─ ShaftLayout.kt
   │  │     └─ ShaftRenderer.kt
   │  ├─ input/
   │  │  └─ ShaftMetaSection.kt
   │  ├─ screen/
   │  │  ├─ AddComponentDialogs.kt
   │  │  ├─ ComponentType.kt
   │  │  └─ ShaftScreen.kt
   │  ├─ shaft/
   │  │  └─ ShaftRoute.kt
   │  ├─ theme/
   │  │  ├─ Color.kt
   │  │  ├─ Theme.kt
   │  │  └─ Type.kt
   │  └─ viewmodel/
   │     ├─ ShaftViewModel.kt
   │     └─ ShaftViewModelFactory.kt
   └─ util/
   ├─ HintStyle.kt
   ├─ Parsing.kt
   ├─ TaperParser.kt
   ├─ TextFilters.kt
   ├─ UnitSystem.kt
   └─ UnitsStore.kt
2) Ownership & rendering responsibilities

ShaftDrawing (compose)

Draws the grid and axis labels only.

Computes the grid origin using layout.minXMm:

xAtZero = contentLeftPx + (0 − minXMm) * pxPerMm.

Uses layout.centerlineYPx for the emphasized horizontal major.

Does not draw the centerline or overall label.

ShaftRenderer (render)

Draws all geometry (bodies, tapers, threads, liners) with full side walls.

Draws the single Overall label just below the lowest geometry.

No preview centerline.

ShaftLayout (render)

Exposes minXMm, maxXMm, centerlineYPx, and pxPerMm.

Scales to fit both axes:

pxPerMm = min( widthPx/(maxX−minX), heightPx/maxDiameterMm ).

3) Type contracts (must stay in sync)

RenderOptions

paddingPx: Int

textSizePx: Float

gridUseInches: Boolean

showGrid: Boolean (renderer’s internal grid; set false in preview since ShaftDrawing draws the grid)

ShaftLayout.compute(...)
leftPx: Float, topPx: Float, rightPx: Float, bottomPx: Float → Result

ShaftLayout.Result (minimum API)

pxPerMm: Float

contentLeftPx, contentTopPx, contentRightPx, contentBottomPx: Float

minXMm, maxXMm: Float

centerlineYPx: Float

spec: ShaftSpec

4) UI input rules

Commit-on-blur/Done; no VM state mutation while typing.

Tap-to-clear numeric fields only when the committed value is exactly 0 (clear on focus gain).

NumberField.kt in ui/input/ implements this behavior (exists in codebase).

5) File creation & placement rules

Rendering code lives only under ui/drawing/render/.
Compose wrappers that host a Canvas live in ui/drawing/compose/.

Input widgets live in ui/input/; do not create ui/components/.

When adding a new file:

Use package com.android.shaftschematic.<…> matching the tree above.

Pick a unique, descriptive filename (avoid “Copy of …”).

Update this Package map if you add/move a file.

6) “Add a new file” checklist

Choose package from the map above.

Create the file with the correct package line.

If it draws in the preview:

If it’s grid/labels, put it in drawing/compose and ensure it reads from ShaftLayout.Result.

If it’s geometry, put it in drawing/render and do not duplicate grid/labels/overall label.

Respect type contracts in §3 (e.g., paddingPx: Int).

Run a smoke compile; fix imports.

Update this doc’s package map if you added/moved anything.

Commit with a clear, conventional message.

7) PR checklist (quick)

No duplicate “Overall” label (renderer is the only source).

Grid origin uses minXMm; center major uses centerlineYPx.

pxPerMm derived by both axes fit.

No preview centerline drawn.

Input fields follow commit-on-blur and tap-to-clear(0) rules.

Package names/imports match com.android.shaftschematic….

Contract and this doc updated if structure changed.

8) Common pitfalls

Double overall label: make sure screens don’t add an extra Text("Overall …").

Misaligned origin: using left as X=0 instead of left + (0 − minXMm) * pxPerMm.

Fixed scale feel: forgetting to include max diameter in scale (vertical fit).

IME padding slab: apply IME insets to the FAB container only, not the whole screen.

Notes / guardrails

The preview stack remains:

ui/drawing/compose/ShaftDrawing.kt → draws grid + axis labels only.

ui/drawing/render/ShaftRenderer.kt → draws geometry + single "Overall" label.

ui/drawing/render/ShaftLayout.kt → computes pxPerMm, minXMm/maxXMm, centerlineYPx with two-axis fit.

Highlight feature (v1.1+): ShaftDrawing bridges highlightEnabled + highlightId → RenderOptions → ShaftRenderer
for tap-to-select visual feedback.