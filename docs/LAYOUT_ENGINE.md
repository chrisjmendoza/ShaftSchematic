# ShaftLayout Engine
Version: v0.4.x

## Purpose
The layout engine converts millimeter-space geometry into pixel-space coordinates for rendering.  
It performs *all* mm→px scaling, offsets, and coordinate normalization.

The layout engine does **not**:
- Modify geometry
- Validate component semantics
- Render anything
- Apply compression or distortion

---

# 1. Inputs & Outputs

## Input
- `spec: ShaftSpec`
- `renderBoundsPx: RectF` (drawing region inside padding)
- `renderOptions: RenderOptions` (text size, padding, grid settings)

## Output
A fully normalized `ShaftLayout.Result`, containing:
```
data class Result(
    val pxPerMm: Float,
    val minXMm: Float,
    val maxXMm: Float,
    val contentLeftPx: Float,
    val contentRightPx: Float,
    val contentTopPx: Float,
    val contentBottomPx: Float,
    val centerlineYPx: Float
)
```
2. Two-Axis Fit Scaling (Authoritative)
To guarantee geometry is undistorted:

horizontalFit = drawableWidthPx / overallLengthMm
verticalFit   = drawableHeightPx / maxOuterDiaMm
pxPerMm = min(horizontalFit, verticalFit)
This scaling rule is mandatory for:

Canvas rendering

PDF export
(both use identical logic, differing only in available pixels)

3. Coordinate Mapping
Axial Coordinate (X)
fun xPx(mm: Float): Float =
    contentLeftPx + (mm - minXMm) * pxPerMm
Radial Coordinate (R → ±Y)
fun rPx(radMm: Float): Float =
    radMm * pxPerMm
Centerline
centerlineYPx = contentTopPx + (drawableHeightPx / 2f)
4. Bounding Box Calculation
1) Compute axial bounds
minXMm = 0f
maxXMm = max(spec.overallLengthMm, spec.coverageEndMm())
2) Compute radial bounds
maxOuterDiaMm = max(
    all body diameters,
    all taper LET/SET,
    all thread major diameters,
    all liner odMm
)
3) Fit to available canvas region using two-axis rule.
5. Layout Invariants
Layout must never read component mm and convert to px manually outside the two mapping functions.

Layout never performs validation.

Layout never interprets component semantics (e.g., taper rate, thread pitch).

Layout does not draw the grid or geometry.

Layout does not implement “compression” or any non-uniform scale.

PDF export must call the same layout engine.

6. Error Boundaries
If overallLengthMm = 0 → layout returns pxPerMm = 0 and renderer draws nothing.

If maxOuterDiaMm = 0 → treat diameter as 1mm to avoid divide-by-zero.

If renderBoundsPx is too small → layout still returns valid structure but may clamp drawing.

7. Summary
The layout engine is the sole authority on coordinate mapping.
All geometry sent to the renderer is already pixel-perfect and scaled identically for preview and PDF output.

No other system computes pixel coordinates.

---

# 📄 **RENDERING_ENGINE.md**
```
# ShaftRenderer
Version: v0.4.x

## Purpose
The rendering engine draws all shaft geometry using **pre-computed pixel coordinates** supplied by `ShaftLayout.Result`.  
It is the *only* layer allowed to draw bodies, tapers, threads, liners, and the “Overall” label.

Renderer performs **no mm→px conversion** and **no geometry calculations** (e.g., taper rate, pitch).

---

# 1. Rendering Responsibilities

### Renderer Draws:
- Bodies
- Tapers
- Threads (major-diameter envelope + hatch)
- Liners (top/bottom + tick marks)
- Overall length dimension line + label

### Renderer Does NOT Draw:
- Grid (ShaftDrawing handles this)
- Labels for individual components (future feature)
- UI overlays (selection, error highlighting)
- PDFs directly (PDF uses same renderer but in a PDF canvas)

---

# 2. Stroke Conventions

### Strokes from DrawingConfig:
- `shaftWidth`: For bodies, tapers, liners’ top/bottom
- `dimWidth`: For ticks, hatch strokes, dimension lines

### Anti-rules:
- Renderer must never create arbitrary stroke widths.
- Renderer must not change stroke widths based on scale.

All strokes are invariant to pxPerMm.

---

# 3. Component Rendering Rules

## 3.1 Body
top = cy - rPx(dia/2)
bottom = cy + rPx(dia/2)
left = xPx(start)
right = xPx(end)

Draw two horizontal lines.
Draw two horizontal lines.

---

## 3.2 Taper
radiusStart = rPx(startDia/2)
radiusEnd = rPx(endDia/2)
topLine: (left, cy - radiusStart) → (right, cy - radiusEnd)
bottomLine: (left, cy + radiusStart) → (right, cy + radiusEnd)

 

Stroke: `shaftWidth`.

---

## 3.3 Threads
Envelope rectangle:
top = cy - rPx(major/2)
bottom = cy + rPx(major/2)
left = xPx(start)
right = xPx(end)

 

Hatch spacing:
- Fixed pixel spacing (e.g., 8px)
- 45° angle negative slope
- Hatch does not represent real pitch

Hatch always uses `dimWidth`.

---

## 3.4 Liner
Same top/bottom as body, but:

- Ends contain vertical ticks:
tickHeightPx = rPx(od/2) * 0.25f
Tick stroke = dimWidth

 

---

# 4. Overall Dimension Label
Renderer draws a single “Overall: ###mm” label centered above the shaft.

Rule:
- UI must NEVER draw this label.
- Renderer must draw it consistently for canvas and PDF.

---

# 5. Rendering Invariants

1. Renderer consumes pixel coordinates ONLY from `ShaftLayout.Result`.
2. Renderer never converts mm directly.
3. Renderer never validates geometry.
4. Renderer never interprets semantic fields (pitch, taper rate).
5. Renderer output is identical across preview and PDF (scale differs).

---

# 6. Rendering Order (Required)

1. Bodies  
2. Tapers  
3. Liners  
4. Threads  
5. Dimension line + ticks  
6. Overall label  

This order minimizes visual occlusion and matches drafting standards.

---

# 7. Summary
ShaftRenderer is a pure rendering system isolated from business logic and layout concerns.  
It consumes pixel-exact parameters and draws a consistent engineering schematic.

The renderer must remain stateless and deterministic.