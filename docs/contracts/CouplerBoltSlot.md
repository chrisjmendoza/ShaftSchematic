# Coupler Bolt Slot Contract (v1.0, 2026-07-11)

Layer: Model → ViewModel → Render/UI
Purpose: Radial muff-coupler bolt cutouts on the shaft. One entry = one **axial row** of
cutouts at a coupler location.

See also: `docs/archive/CouplerBoltSlot_Proposal.md` (design + scoping), `Model_Conventions.md`,
`AddComponentDialogs.md`, `ComponentsOrdering.md`.

---

## What it is

On a muff-coupled shaft, radial bolts seat in the seam between shaft and coupler so the
coupler cannot spin or walk. On the shaft side that means radial holes carved into the
surface — **half in the shaft, half in the coupling**. We model only the shaft-side
cutouts (no coupler sleeve).

---

## Model — `CouplerBoltSlot` (`model/CouplerBoltSlot.kt`)

Implements `Segment`. Stored on `ShaftSpec.couplerBoltSlots: List<CouplerBoltSlot>`
(defaults empty → back-compat; no persistence migration).

| Field | Meaning |
|-------|---------|
| `id` | Stable UUID |
| `startFromAftMm` | Physical position (from AFT) of the **first / aft-most** cutout center |
| `holeDiaMm` | Diameter of each cutout |
| `count` | Number of cutouts in the row (Int, ≥ 1; user-defined per custom build) |
| `spacingMm` | Axial center-to-center pitch between adjacent cutouts (used when `count > 1`) |
| `through` | `true` = through-hole, `false` = blind |
| `depthMm` | Blind depth; ignored when `through = true` |
| `authoredReference` | `SlotAuthoredReference` AFT / **FWD** (default FWD) — UI display only |
| `holeStyle` | `BoltHoleStyle` **SEAM** (default) / CROSS — **draw-only**; see "Hole style" below |
| `clocking` | `BoltHoleClocking` **DEG_90** (default) / IN_LINE — a CROSS hole's position around the shaft relative to the keyway; ignored for SEAM. Draw + footer text only |
| `showDimensionRail` | Per-slot dimension-rail opt-in — off by default; ON prints ONE span from the `authoredReference` shaft end to the nearest hole center, labelled `authoredCenterMm` (see "Dimension rail" below) |
| `label` | Optional display label |

Derived: `lengthMm = (count − 1) · spacingMm + holeDiaMm` (axial footprint),
`centerMmAt(i) = startFromAftMm + i · spacingMm`, `authoredCenterMm(oal)` (the distance from
the `authoredReference` face to the nearest hole center — what the card shows and the footer
prints; storage never moves), and `isHiddenCrossBore` (`CROSS` ∧ `DEG_90`).

`isValid(overallLengthMm)`: non-negative fields, `count ≥ 1`, and every cutout's full
footprint (center ± half hole Ø) within `0 .. overallLengthMm` (± epsilon) — the hole
edges are checked, not just the centers.

---

## Core invariant — reference-only feature

A coupler bolt slot **never**:
- affects overall length — excluded from `coverageEndMm()`;
- splits or merges bodies (no `splitBodiesAround` on add, no merge on remove);
- collides — `ComponentKind.COUPLER_BOLT_SLOT.collisionGroup()` returns `null`.

It is resolved as `ResolvedCouplerBoltSlot` **after** body resolution and appended by
position, so it never enters auto-body derivation or body subtraction.

Do **not** add slots to `coverageEndMm`, body split/merge, overlap
validation, or `maxOuterDiaMm` (a slot's `maxDiaMm()` is 0 — it does not define shaft OD).

---

## Authoring reference (AFT / FWD)

- **AFT**: entered position = the aft-most cutout center from the AFT face → stored directly.
- **FWD** (default): entered position locates the **fwd-most** cutout from the FWD face;
  the row extends aft. `startFromAftMm = OAL − enteredFwd − (count−1)·spacingMm`.

Same convention is applied identically in `AddCouplerBoltSlotDialog` and the carousel card,
so editing round-trips.

`AddCouplerBoltSlotDialog` opens on a row that FITS, so "Add" is live from the first frame
(`couplerBoltSlotSeedMm`, `AddDialogGates.kt`): the position seeds at
`AddDefaultsConfig.SLOT_FROM_END_IN` (1″) or one hole Ø, whichever is larger, from the chosen
face; measured from AFT the suggested start (end of the last component) is kept when the row
fits there. A FWD seed of 0 put the nearest hole center on the shaft end — half the hole off the
shaft, "Row extends past the end of the shaft", Add disabled (on-device report).

---

## Hole style (SEAM / CROSS)

`holeStyle` says what kind of hole the row is, and changes the **drawing only**:

- **SEAM** (default): the muff-coupler cutout — a radial hole on the shaft's outer surface,
  half in the shaft, half in the coupling sleeve. Every row saved before the field existed
  decodes as SEAM (serialization default), so existing documents print byte-identically.
- **CROSS**: a bolt hole cross-drilled straight through the shaft on a diameter — the plain
  coupling end with no taper, just a bolt hole located from the end of the shaft to the
  hole's center (on-device request). The entry is the same as a one-hole seam row: Measure
  From FWD, hole Ø, and the distance from the face to the hole **center**; the position field
  is labelled "Hole center from FWD/AFT" on both surfaces (`slotStartFieldLabel`, ONE source).

Position, `holeDiaMm`, `count`, `spacingMm`, through/blind, `isValid`, `lengthMm`, and the
reference-only posture are identical for both — switching the style on the card
(`updateCouplerBoltSlotStyle`) rewrites nothing else. The coupling end view on the runout
sheets takes its bolt count from the first **SEAM** row (`RunoutPdfComposer`): a cross-drilled
hole is a bolt through the shaft, not a flange bolt on the face.

### Clocking against the keyway (CROSS only)

`clocking` records where the cross-drilled hole sits around the shaft relative to the keyway —
the keyway-clocking-note posture: drawing + footer text, no geometric effect.

- **DEG_90** (default — a coupling bolt in line with the keyway would pass through the key;
  the configuration reported from the floor): the keyway draws face-on, so the hole's axis
  lies in the page and the bore is **hidden** — two dashed walls one hole width apart from the
  top surface to the bottom (through) or to the drill depth with a dashed floor (blind; the
  bore is drawn entering from the TOP silhouette). ONE pure construction, `crossBoreLines`
  (`geom/BoltHoleMath.kt`), feeds both draw sites; dashes are the hidden-keyway
  `HIDDEN_DASH_ON`/`OFF`.
- **IN_LINE**: the hole faces the viewer and draws as the centerline circle.

The chip row ("From keyway: 90° | In line") appears on the dialog and the card only while
Cross-drilled is selected — the count > 1 → Spacing pattern.

### Footer lines (CROSS only)

A cross-drilled row prints, in the end column of the face it was **quoted from**
(`authoredReference`), after the taper/thread block (`crossDrilledHoleLines`, `SheetFooter.kt`):
`Bolt hole: Ø <dia>[ × <count>]`, `Hole center from FWD|AFT: <authoredCenterMm>[ @ <pitch>]`,
and the clocking note `90° from keyway` / `In line with keyway` — the note prints ONLY when the
shaft carries at least one keyway ("from keyway" says nothing without one, the
`keywayClockingFooterNote` rule). Values resolve the row's own display unit and honor dual
display; blank drafts keep the labels and rule the values. **SEAM rows still print nothing**
(existing sheets byte-identical). Either style may additionally opt into the dimension rail
below.

---

## Dimension rail (`showDimensionRail`, card-only, off by default)

The card's "Show dimension rail" switch adds **ONE `DimSpan`** (kind LOCAL) to the schematic
AND the consolidated sheet's rail set, through the one shared builder `buildCouplerSlotSpans`
(`pdf/ShaftPdfComposer.kt`, beside `buildTaperLengthSpans`; `RunoutPdfComposer`'s consolidated
branch calls the same function). The span runs from the **shaft end** the row was measured from
(`authoredReference`: AFT = the AFT face at 0, FWD = the FWD face at OAL — the shaft end, never a
SET) to the **nearest hole center** (the fwd-most center under FWD, the aft-most under AFT), and
its label is the stored distance `authoredCenterMm` through `formatLenDimDualLabel` in the row's
own display unit (`displayUnits.unitFor(slot.id)` — the slot's id is its resolved id, so a
`unit_overrides` entry keys to it), honoring dual display. The planner tiers it like any other
span; blank drafts rule the value like every other span. OFF contributes nothing, so a sheet that
never touched the switch prints byte-identically. A hole centered on its face (distance 0) prints
no span. Draw-only — the span moves no geometry, and the row stays outside OAL, body resolution
and collision. The switch stays **card-only** (the add-dialog-parity carve-out: a post-hoc print
choice with a stable default, reached for after looking at a printed sheet).

---

## Rendering

A **SEAM** cutout is a **circle straddling the shaft outline**, mirrored on the top and
bottom edges, drawn on top of all other geometry. The local shaft outer radius at each
cutout's axial position determines where the circle sits (falls back to the shaft's max OD
if no component covers that position). A **CROSS** hole clocked in line with the keyway is
**ONE circle on the shaft centerline** at the same axial position — the hole as it is seen on
the near surface in plan view; clocked 90° it is the **hidden bore** described above. Both
draw sites branch on style and clocking from the same constructions (the preview overlay and
`drawCouplerBoltSlots`) and must stay identical.

Drawn everywhere the shaft is drawn:
- Preview: overlay pass in `ui/drawing/render/ShaftRenderer.kt` (color: `RenderOptions.slotFillColor`).
- PDFs: `drawCouplerBoltSlots()` in `pdf/ShaftPdfComposer.kt`, reused by `RunoutPdfComposer`,
  `WearPdfComposer`, and `UndercutPdfComposer` (the undercut composer calls it only on its
  whole-shaft fallback profile — a sheet with any detail strip prints no slots; see the
  known-gap note in the tracking docs).

### Compression: the row's window never compresses

The hole circles are sized on the **diameter** scale (`rPx(holeDiaMm)`) while their centers ride
the compressed axial map, so inside a foreshortened body a 1″-on-3″ row printed as an
overlapping chain and the S-break gap could land on the row (manual-figure report). The fix is
the body-keyway WINDOW rule (CLAUDE.md "A body keyway's WINDOW never compresses"), applied to
bolt rows:

- **Pinned window.** `boltRowProtectedSpansMm` (`geom/ProfileFeatureSpans.kt`) is each row's
  footprint — first hole center − hole radius … last hole center + hole radius — padded by one
  hole diameter at each end and clamped to the shaft; a cross-drilled hole's window is the same
  construction (its circle or hidden bore is one hole wide). `boltRowPinnedSpans` turns it into a
  `Float.MAX_VALUE` feature span in the ONE builder `profileFeatureSpans`, so both composers and
  the height-slider estimator pin the span the holes draw in. The window — never the host body —
  pins; the drawn height yields to it like any pin.
- **The S-break gap steers clear of it.** The same windows join the keyway windows and seal areas
  as `breakGapCenter` avoid ranges (`drawBodyRunsWithBreaks(boltRowAvoidSpansMm)`); a run with no
  clear placement prints plain, as for a keyway window.
- **Centers off the row's own mapped span.** `boltHoleCentersX` (`geom/BoltHoleMath.kt`) places
  every hole off the row's own mapped window — `xAt(first center − r)` plus `(r + i·spacing)` at
  the window's local pt/mm — the way `drawKeywayNotchBodyPdf` derives a slot's axial scale from
  its pinned window. Pinned, that local scale IS the diameter scale, so the drawn pitch is the
  true pitch and the holes stay round and correctly spaced. Both draw sites take their centers
  from it; on the preview's linear map it reduces to `xPx(center)` exactly, so only the PDF's
  spacing changed.

Reference-feature posture is untouched: the window reaches neither OAL, body resolution nor
collision — it is a drawing decision only.

**Still identical:** through vs blind render the same on the seam style.

---

## ViewModel API (`ShaftViewModel`)

- `addCouplerBoltSlotAt(startMm, holeDiaMm, count, spacingMm, through, depthMm, reference = FWD,
  holeStyle = SEAM, clocking = DEG_90, target)` — newest-on-top; remembers session defaults;
  never touches OAL.
- `updateCouplerBoltSlot(index, startMm, holeDiaMm, count, spacingMm, through, depthMm, target)`
- `updateCouplerBoltSlotReference(index, reference, target)` /
  `updateCouplerBoltSlotStyle(index, style, target)` /
  `updateCouplerBoltSlotClocking(index, clocking, target)` /
  `updateCouplerBoltSlotShowRail(index, show, target)`
  (`updateCouplerBoltSlotLabel` was deleted 2026-07-26 — dead end-to-end; the slot card
  has no title editor. Re-add it together with the card's editable title if slot
  renaming ever ships.)
- `removeCouplerBoltSlot(id, target)` — recoverable via the general session `undoEdit()` (see
  `ShaftViewModel.md`); no body merge.

Every one of those takes `target: SpecTarget = SpecTarget.ORIGINAL` as its **last** parameter —
which of the document's two geometries the edit lands on; see `docs/contracts/FinalSchematic.md`.

---

## UI parity (see `AddComponentDialogs.md`)

`AddCouplerBoltSlotDialog` and the `ResolvedCouplerBoltSlot` carousel card both expose:
Hole (Seam | Cross-drilled), From keyway (90° | In line — only when Cross-drilled), Measure
From (AFT | FWD), hole Ø, count, spacing (only when `count > 1`), through/blind toggle, and
depth (only when blind). The carousel card
additionally has the card-only "show dimension rail" toggle (see "Dimension rail" above). The
add chooser's button reads "Coupler Bolt Slot / Hole" so the cross-drilled case is findable.

---

## Change Log
**v1.0 (2026-07-11)** — Initial contract. Feature implemented on `feature/coupler-bolt-slots`.

**2026-10-13** — `showDimensionRail` implemented (ONE LOCAL span from the authored shaft end to
the nearest hole center, both rail sheets, `buildCouplerSlotSpans`); the row's padded window
pins at true scale through `profileFeatureSpans` (`boltRowPinnedSpans`), the S-break gap avoids
it, and both draw sites place hole centers off the window's own scale (`boltHoleCentersX`) —
manual-figure report: overlapping holes inside a compressed body.

**2026-09-17** — `holeStyle` (SEAM / CROSS): the cross-drilled coupling-end bolt hole, drawn
as one circle on the centerline; additive, draw-only, mirrored on dialog and card. Same day:
`clocking` (DEG_90 default / IN_LINE) — 90° from the keyway draws the hidden bore
(`crossBoreLines`); cross-drilled rows gained footer lines (Ø, center distance, clocking note).

**2026-07-26** — Slot deletion is now undone via the general session-scoped `undoEdit()`
(`SessionHistory<EditState>` in `ShaftViewModel.md`), not the removed `LastDeleted`
delete-only history. No behavior change to the slot itself.
