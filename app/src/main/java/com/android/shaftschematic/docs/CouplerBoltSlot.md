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
| `showDimensionRail` | Per-slot dimension-rail opt-in — **deferred**, off by default, not drawn in v1 |
| `label` | Optional display label |

Derived: `lengthMm = (count − 1) · spacingMm + holeDiaMm` (axial footprint) and
`centerMmAt(i) = startFromAftMm + i · spacingMm`.

`isValid(overallLengthMm)`: non-negative fields, `count ≥ 1`, and all cutout centers
within `0 .. overallLengthMm` (± epsilon).

---

## Core invariant — reference-only feature

A coupler bolt slot **never**:
- affects overall length — excluded from `coverageEndMm()` / `ensureOverall()`;
- splits or merges bodies (no `splitBodiesAround` on add, no merge on remove);
- collides — `ComponentKind.COUPLER_BOLT_SLOT.collisionGroup()` returns `null`.

It is resolved as `ResolvedCouplerBoltSlot` **after** body resolution and appended by
position, so it never enters auto-body derivation or body subtraction.

Do **not** add slots to `coverageEndMm`, `ensureOverall`, body split/merge, overlap
validation, or `maxOuterDiaMm` (a slot's `maxDiaMm()` is 0 — it does not define shaft OD).

---

## Authoring reference (AFT / FWD)

- **AFT**: entered position = the aft-most cutout center from the AFT face → stored directly.
- **FWD** (default): entered position locates the **fwd-most** cutout from the FWD face;
  the row extends aft. `startFromAftMm = OAL − enteredFwd − (count−1)·spacingMm`.

Same convention is applied identically in `AddCouplerBoltSlotDialog` and the carousel card,
so editing round-trips.

---

## Rendering

Each cutout is a **circle straddling the shaft outline**, mirrored on the top and bottom
edges, drawn on top of all other geometry. The local shaft outer radius at each cutout's
axial position determines where the circle sits (falls back to the shaft's max OD if no
component covers that position).

Drawn everywhere the shaft is drawn:
- Preview: overlay pass in `ui/drawing/render/ShaftRenderer.kt` (color: `RenderOptions.slotFillColor`).
- PDFs: `drawCouplerBoltSlots()` in `pdf/ShaftPdfComposer.kt`, reused by `RunoutPdfComposer`
  and `WearPdfComposer`.

**v1 deferrals:** no dimension rail is drawn (`showDimensionRail` persists but is unused);
through vs blind render identically.

---

## ViewModel API (`ShaftViewModel`)

- `addCouplerBoltSlotAt(startMm, holeDiaMm, count, spacingMm, through, depthMm, reference = FWD)`
  — newest-on-top; remembers session defaults; **no `ensureOverall()`**.
- `updateCouplerBoltSlot(index, startMm, holeDiaMm, count, spacingMm, through, depthMm)`
- `updateCouplerBoltSlotReference / updateCouplerBoltSlotLabel / updateCouplerBoltSlotShowRail`
- `removeCouplerBoltSlot(id)` — multi-step undo via `LastDeleted.CouplerBoltSlot`; no body merge.

---

## UI parity (see `AddComponentDialogs.md`)

`AddCouplerBoltSlotDialog` and the `ResolvedCouplerBoltSlot` carousel card both expose:
Measure From (AFT | FWD), hole Ø, count, spacing (only when `count > 1`), through/blind
toggle, and depth (only when blind). The carousel card additionally has the deferred
"show dimension rail" toggle.

---

## Change Log
**v1.0 (2026-07-11)** — Initial contract. Feature implemented on `feature/coupler-bolt-slots`.
