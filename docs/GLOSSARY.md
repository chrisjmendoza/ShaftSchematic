# Glossary
Version: v0.5.x
Last updated: 2026-08-05 — renderer stroke terms corrected to the actual `RenderOptions`
field names (`outlineWidthPx`/`dimLineWidthPx`); reference-only feature kinds went from five
to **seven** (worn sections, undercuts); added the consolidated-sheet paper terms
(consolidated sheet, S-break, default sizing curve, Shaft height slider, liner
compression). 2026-07-28 — added the reference-only inspection terms (wear spot/pit,
measured-Ø reading, runout reading, witness tick); corrected auto-body promotion to the
checkbox-only "Explicit body" path. 2026-07-21 — Keyway entry: body-hosted keyways now shipped (were shelved); added keyways-180°-apart notes; corrected explicit-vs-auto-body entry after the "non-negotiable bodies" revert (bodies are fluid fillers, never collide).

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

### outlineWidthPx
`RenderOptions` field: primary outline stroke thickness (px) for bodies, tapers, liners’
top/bottom, and envelopes.

### dimLineWidthPx
`RenderOptions` field: stroke thickness (px) for auxiliary/secondary lines — ticks, hatch,
and dimension lines.

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

### Consolidated Sheet
The single-page output that carries the schematic's dimension rails + spec footer plus the
elected runout and/or wear content (`ConsolidatedVariant`: All three | Schematic + Runout |
Schematic + Wear). Composed by `composeRunoutPdf(consolidated = true)` and owned by the
Consolidated Output tab; the Runout tab's own buttons still produce the **classic**
standalone runout sheet.

### S-break (round-stock break)
The S-curve end-cap glyph (`pdf/BreakSymbol.kt`, `drawBreakEdge()`) that replaces a straight
end cap when a long body is drawn foreshortened, so the drawing reads as a shortened
cylindrical bar rather than a literal-length rectangle. **Bodies only** — tapers, threads,
and liners are never broken this way. Liners foreshorten in *size* only, down to a finite
width floor.

### Default sizing curve
The 100% base for drawn shaft height (`defaultShaftHeightPt` / `defaultVisualScale`,
`geom/ProfileCompression.kt`): drawn height is linear in true diameter through 4" → 0.75"
and 8" → 1.25" on paper, continuing past both anchors and meeting the 1.5" ceiling at 10".
The anchor **heights** are user settings (Settings → PDF Export → "Default drawing size");
the anchor diameters stay fixed at 4"/8".

### Shaft height slider
The per-job multiplier (`RunoutConfig.heightScale`, 50–300%) applied to the solved profile
scale on the runout/consolidated sheets **and** the schematic — one value behind every
drawing output. Selected by drawn-height **value in paper inches**; commits near the
standard height snap to exactly 100%. The drawn shaft is hard-capped at 1.5" on paper
(`PROFILE_MAX_SHAFT_HEIGHT_PT`, an absolute ceiling).

### Liner compression
The per-job pair (`RunoutConfig.linersProportional` / `linerCompression`) controlling how
far liners may foreshorten below true length. Feeds a **best-effort** width floor
(`linerMinFracOfTrue` → `ProfileFeatureSpan.minWidthFracOfTrue`) that the scale solve
ignores; raised floors that don't fit shrink uniformly (`fracFitFactor`). Drawing height
takes precedence — neither control ever changes the drawn shaft height.

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
- **Keyways 90° apart:** `ShaftSpec.keyways90Apart` + `keyways90Cw` — a drawing note that the
  shaft's keyways are clocked 90° apart instead of 180°, with direction (CW/CCW) measured from
  the AFT keyway, viewed from aft. Mutually exclusive with Keyways 180° apart (enabling one
  clears the other). Renders as an edge notch in the silhouette — bottom edge for CW, top edge
  for CCW — not a hidden dashed line; the spoon bowl is not drawn at 90°.

Non-goal:
- Keyways will never exist as standalone components.

### Explicit vs auto body
An **explicit** body is a stored `ShaftSpec.bodies` entry; an **auto body** is derived at
resolve time (never stored) to fill unoccupied spans. Both are fluid base material / fillers:
bodies never collide (`collidingIds()` checks only sacred taper/thread/liner pairs), and a
sacred component added over a plain body splits it (`splitBodiesAround`) — a body that has a
keyway stays whole and is trimmed for drawing instead. Promote an auto body to explicit ONLY by
ticking the "Explicit body" checkbox on its carousel card (there is no field-edit promotion
path; the card's Ø field sets the shared bare-shaft `autoBodyDiaMm` without promoting). (The
"explicit bodies are non-negotiable" experiment was reverted 2026-07-21 — it raised false
collision warnings on normal drafts.)

### Reference-only feature
A record that is drawn on the shaft but never participates in geometry: it does not affect
OAL/`coverageEndMm`, body resolution, collision, or the Free-to-End badge. Seven kinds:
coupler bolt slots (in `ShaftSpec`), and — in the document envelope — wear spots, wear pits,
measured-Ø readings, worn sections, runout readings, and undercuts.

### Wear Spot
A recorded liner wear band (`WearSpot` in `WearRecord.spots`): liner-local start/length from
the liner's AFT edge, plus a note. Drawn as a hatched band on the wear document; clamped for
rendering only, stored data never mutated. (Its old per-band min-Ø field is retired from
entry and print — superseded by measured-Ø readings; the model field survives for old files.)

### Wear Pit
A pit / dye-penetrant failure marker (`WearPit`), drawn as a hand-style "X" (small or large
symbol size) on any liner, taper, or body. Keyed by resolved component id + component-local
axial + across fraction.

### Measured-Ø Reading
A measured diameter at an axial station (`WearDiaReading` in `WearRecord.diaReadings`),
recorded via the wear overlay's "Add Ø" tool. Printed on the wear document as a value below
the shaft with a leader to a witness tick (liner readings on the liner's detail strip;
body/taper readings under the main profile). Placement by `geom/WearDiaCalloutLayout.kt`.
The typed value is stored verbatim (golden rule); `diaMm = 0` = placed-but-empty
(overlay-only, never printed).

### Witness Tick
The thin vertical line across a component's full drawn height marking where a measured-Ø
reading was taken — the anchor the callout leader points at.

### Worn Section
A designated measured area (`WornSection` in `WearRecord.wornSections`) authored on the
Consolidated Output tab. **Shaft-space** (`startFromAftMm` + `lengthMm`) — it may cross
component edges, so it has no orphans and is never pruned at decode. Carries a **list** of
measured diameters, stored verbatim (golden rule); values ≤ 0 never print. Drawn on the
consolidated sheet as boundary lines at the span ends with the values rotated 90° inside
the profile over knockout halos.

### Runout Reading
A per-station TIR value + high-spot clock marker (`RunoutReading`), recorded by tapping a
bubble on the runout sheet. Keyed by `(componentId, stationIndex)`; 30-minute clock ticks
for the high spot.

### Undercut
A machined-below-surface span (`Undercut` in `UndercutRecord.undercuts`) printed on its own
Undercut Drawing tab/PDF. Reference-only and **shaft-space** (canonical `startFromAftMm`),
so a cut may span components; no orphans, never pruned at decode. Drawn as an **open**
silhouette step — void fill plus full-height section faces and floor lines, never closed by
a lid. Drawn depth is display-exaggerated per sheet (`UndercutRecord.exaggerationFrac`);
printed Ø values stay the stored numbers.

### Pilot Diameter (future)
Centering diameter for couplings.

### Bolt Circle Diameter (BCD) (future)
Circular pattern for coupling bolts.

---

# Summary
This glossary is authoritative and must remain consistent with all contracts across the system.