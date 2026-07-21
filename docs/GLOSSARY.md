# Glossary
Version: v0.5.x
Last updated: 2026-07-21 — Keyway entry: body-hosted keyways now shipped (were shelved); added keyways-180°-apart and explicit-vs-auto-body notes.

Definitions of all terms used across architecture, components, rendering, validation, and PDF export.

---

# 1. Coordinate & Geometry Terms

### AFT
X = 0 reference plane (rear/stern direction).

### FWD
Positive X direction (toward bow).

### centerlineYPx
Vertical pixel coordinate of shaft’s axial center.

### pxPerMm
Scale factor converting millimeters → pixels.

### rPx(radMm)
Convert radial millimeters to pixels using pxPerMm.

### xPx(mm)
Convert axial mm to pixels using pxPerMm.

### overallLengthMm
Total envelope of the shaft; bounds all components.

### coverageEndMm
Farthest end point of any component.

### freeToEndMm
Remaining length from coverageEnd to overallLength.

---

# 2. Component Terms

### Body
Constant-diameter cylindrical region.

### Taper
Linear transition between two diameters.

### SET (Small End of Taper)
Smaller diameter.

### LET (Large End of Taper)
Larger diameter.

### Taper Rate
Slope ratio (length per unit diameter change).

### Threads
External thread region defined by major diameter and pitch.

### Liner
Outer sleeve or bearing surface.

### Coupler Bolt Slot
Reference marker for the bolt cutouts in a muff coupling — the row of holes through
which the coupling is bolted to the shaft. Modeled as a row of `count` cutouts at a
fixed axial pitch (`spacingMm`), each of diameter `holeDiaMm`, drawn straddling the
shaft outline (half in the shaft, half in the coupling), mirrored on the top and
bottom edges. Cutouts may be through-holes or blind (`through` / `depthMm`). It is a
**pure reference overlay**: it never contributes to overall length, is not checked for
collisions, and never splits bodies. Position is authored from the AFT or FWD end
(default FWD).

---

# 3. Rendering Terms

### shaftWidth
Primary stroke thickness for bodies, tapers, liners’ top/bottom.

### dimWidth
Stroke thickness for ticks, hatch, and dimension lines.

### Hatch
45° angled lines used to denote thread region (decorative, not mechanically accurate).

---

# 4. UI Terms

### Commit-on-Blur
Numeric field updates only when editing is complete.

### Tap-to-Clear(0)
Input field clears only when committed value is zero.

### Blocking Error
Prevents save/export.

### Warning (Non-Blocking)
Allows continued operations but signals risk.

---

# 5. PDF Terms

### pt (Point)
1/72 inch. PDF’s coordinate unit.

### Title Block
Header region containing metadata: date, units, scale, title.

### Scale to Fit
Non-integer scale factor used when geometry cannot be full-size.

---

# 6. Architecture Terms

### ViewModel
Holds state & applies validation and updates.

### ShaftLayout
Computes mm→px mapping.

### ShaftRenderer
Draws geometry using pixel coordinates.

### ShaftDrawing
Compose wrapper that draws grid and delegates to renderer.

---

# 7. Misc Marine Terms

### Keyway
Rectangular torque-transfer slot (a cut feature), owned by a host component.

Current state:
- Supported on `Taper` (SET-referenced offset) and `Body` (AFT/FWD end-referenced offset),
  each with keyway length and a spooned flag. Open (offset 0) or floating (offset > 0).
- Body-hosted keyways serve intermediate shafts with fitted couplings that end on a plain body.
- **Keyways 180° apart:** `ShaftSpec.keyways180Apart` — a drawing note that the shaft's keyways
  are clocked 180° from each other. The aft-most keyway (measurement datum) stays solid; every
  other keyway renders as a hidden feature (dashed, no void fill), plus a footer note.

Non-goal:
- Keyways will never exist as standalone components.

### Explicit vs auto body
An **explicit** body is a stored `ShaftSpec.bodies` entry — a first-class, non-negotiable
component (collides, hard-blocks overlapping adds/moves, never split). An **auto body** is
derived at resolve time (never stored) to fill unoccupied spans; it is fluid and flows around
every component. Promote an auto body to explicit by editing a field or ticking "Make editable
body" on its carousel card.

### Pilot Diameter (future)
Centering diameter for couplings.

### Bolt Circle Diameter (BCD) (future)
Circular pattern for coupling bolts.

---

# Summary
This glossary is authoritative and must remain consistent with all contracts across the system.