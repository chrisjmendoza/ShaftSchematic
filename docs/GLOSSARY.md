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

### Blend
A smooth machined transition from a body face into the diameter it steps to — no square
shoulder and, crucially, **no taper rate**. On these sheets "taper" means a precision fit
(rate, SET/LET, keyway); a hand-worked blend is none of those, which is why it is its own
term rather than a short taper. Authored per face on an explicit `Body`
(`blendAftMm` / `blendFwdMm`) with a profile: **S-curve** (tangent at both ends, the
default), **Fillet** (tangent at the large end only), or **Eased cone**. The blend is cut
INWARD out of the body carrying it, so no other component's span moves; its diameters are
DERIVED from whatever sits across the face — and at a **seal area**, where a liner butts the
face, from the midpoint of the liner OD and the body Ø, since the machined seat is hidden
under the liner. **Silhouette only** — no dimension rail and no
footer row; rails keep dimensioning the stored span (dimension to the theoretical sharp
corner). Not to be confused with a *fillet radius* on a liner shoulder.

### Seal area
The radius cuts a body-to-liner transition carries for the **fiberglass to seat into** — the
shop cuts 3–4 rings across the blended section running up to the liner. Drawn as a fixed 3
cuts (`SEAL_GROOVE_COUNT`), each a V notch in both silhouette edges with a **dashed** line
across seated on the notch floors: a solid full-height line is this drawing's glyph for a
component face, and three of them made one shaft read as three or four segments. A schematic
cue, not a count to machine from, and it prints no value. Authored as the third state of a
face's finish chips (**Square | Blend | Seal area**) — exclusive as presented, but a seal area
INCLUDES its blend, since the cuts are machined into the blended section. Stored per face as
`Body.blendAftSeal` / `blendFwdSeal`, or `AutoBlend.seal` on a bare-shaft span.

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
Allows continued operations but signals risk. The **caution** rung of the chrome severity
ladder (error → caution → neutral), drawn on `tertiaryContainer` — amber in every scheme.
A warning must name a PROBLEM the user can act on: a line describing normal behaviour reads
as an error in that styling and cheapens the ones that matter (the "No explicit bodies" note
was removed on those grounds, 2026-08-27). See `docs/contracts/Appearance.md`.

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
end cap when a body is drawn appreciably foreshortened, so the drawing reads as a shortened
cylindrical bar rather than a literal-length rectangle. **Bodies only** — tapers, threads,
and liners are never broken this way. Liners foreshorten in *size* only, down to a finite
width floor. How much squeeze earns the glyph is a user setting
(`PdfPrefs.sBreakThresholdFrac`, Settings → Drawing → "Body S-break", default half of
true drawn width; **Never** hides compression entirely, 100% breaks on any foreshortening) —
one predicate, `breakForCompression`, behind the single body-run pass both composers call.
The independent long-span trigger (`COMPRESS_TRIGGER_PT`, 220 pt of paper at true scale)
fires at every setting. **The footer carries no compression note** — the glyph itself is the
statement that the run is foreshortened, so prose repeating it is redundant.

### Default sizing curve
The 100% base for drawn shaft height (`defaultShaftHeightPt` / `defaultVisualScale`,
`geom/ProfileCompression.kt`): drawn height is linear in true diameter through the two
anchors, continuing past both until the 1.5" ceiling. The STANDARD anchors are
**proportional** — 4" → 0.5" and 8" → 1" on paper (6" → 3/4"), a line through the origin,
so the ceiling is met at 12". The anchor **heights** are user settings (Settings →
Drawing → "Default drawing size"); the anchor diameters stay fixed at 4"/8" and the top of
the settable range IS the absolute ceiling, derived from it rather than restated.

### Shaft height slider
The per-job multiplier (`RunoutConfig.heightScale`) applied to the solved profile scale on
the runout/consolidated sheets **and** the schematic — one value behind every drawing
output. Selected by drawn-height **value in paper inches**; commits near the standard
height snap to exactly 100%. The drawn shaft is clamped to an absolute paper **band**,
1/2" … 1 1/2" (`PROFILE_MIN_SHAFT_HEIGHT_PT` / `PROFILE_MAX_SHAFT_HEIGHT_PT`) — the floor
lets a long shaft be shrunk to uncramp the sheet, the ceiling leaves room to write in, and
neither end is a multiple of this shaft's own curve height. The floor never raises a shaft
above the sizing curve. The stored multiplier's own bounds (0.25–6.0) are wider than the
band on purpose: they only have to express it on any diameter.

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
- **Drawn across two scales.** A plan-view slot's offset and length ride the compressed axial
  map; its WIDTH rides the DIAMETER scale, so the slot stays proportional to the drawn shaft at
  every "Shaft height" setting. Every round part — mill arcs, spoon bowl — is the ellipse those
  two terms make, never a circle: a circle's axial extent grows with the height slider while the
  slot's length stays page-bound. The drawn width is true — a visibility floor and a host ceiling
  exist (`geom/KeywaySlotMath.kt`) but reach only the smallest shafts at the lowest settings.
  See `docs/PDF_EXPORT.md` §5.2c.

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