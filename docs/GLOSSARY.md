# Glossary
Version: v0.4.x

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
- Supported on `Taper` (taper-hosted), including keyway length and a spooned flag.

Planned:
- Body-hosted keyways.
- Keyways will never exist as standalone components.

### Pilot Diameter (future)
Centering diameter for couplings.

### Bolt Circle Diameter (BCD) (future)
Circular pattern for coupling bolts.

---

# Summary
This glossary is authoritative and must remain consistent with all contracts across the system.