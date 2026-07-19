# Coupler Bolt Slots — Feature Proposal

**Date:** 2026-07-11
**Status:** ✅ Implemented (v1) on `feature/coupler-bolt-slots` — compiles clean; not yet run on device. See §Implementation notes.
**Branch:** `feature/coupler-bolt-slots` (off `main`)

Muff-coupler bolt slots: radial bolt holes carved into the shaft at a coupler location. On a
muff-coupled shaft the coupler is a sleeve over the joint; radial bolts seat in the seam so the
coupler can't spin or walk. **We model only the shaft-side cutouts, not the sleeve.**

---

## Scope (confirmed with domain owner)

1. **Bolt slots only.** No coupler-sleeve schematic — we don't draw sleeves yet.
2. **Cutouts sit on the shaft side**, half in the shaft / half in the coupling. On our side-view
   schematic we show the half that's in the shaft: a semicircular bite out of the shaft outline.
   **Expose both through and blind** for now so the owner can test which fits practice.
3. **User-defined count** — each shaft is a custom build; the user sets how many slots.
4. **Dimensioned from an end** (defaults to **FWD**), showing distance-from-end and spacing between
   slots — but the **dimension rail is deferred** (see below).
5. **Cutouts render everywhere the shaft is drawn** (preview + all PDFs), like any other component.
   A per-card **"show dimension rail" toggle, OFF by default**, is a *later* nicety — the owner has
   never hand-drawn one needing a rail. Not priority.

---

## 1. Data model

New standalone flat list on `ShaftSpec` — `couplerBoltSlots: List<CouplerBoltSlot>` (default empty
→ back-compat safe). **Not** nested under a "coupler" grouping (we don't model the sleeve).

Each entry = one **axial row** of bolt cutouts at a coupler location:

| Field | Type | Notes |
|---|---|---|
| `id` | `String` | UUID, like other segments |
| `startFromAftMm` | `Float` | Physical position (from AFT) of the **first** cutout center |
| `authoredReference` | `AFT \| FWD` | Input-display reference; **default FWD**. Reuse liner's pattern |
| `holeDiaMm` | `Float` | Diameter of each cutout |
| `count` | `Int` | Number of cutouts in the row (user-defined, ≥ 1) |
| `spacingMm` | `Float` | Axial center-to-center pitch between adjacent cutouts (used when `count > 1`) |
| `through` | `Boolean` | Through vs blind |
| `depthMm` | `Float` | Blind depth; ignored when `through = true` |
| `showDimensionRail` | `Boolean = false` | Deferred; per-card rail toggle, off by default |
| `label` | `String?` | Optional display label |

**Segment fit.** Implement `Segment` with `startFromAftMm` and a derived
`lengthMm = (count − 1) · spacingMm + holeDiaMm` (axial footprint, so `ShaftLayout` can widen the
window if the row sits near an end). **But treat as a pure reference feature:**
- **Excluded** from `coverageEndMm()` / `ensureOverall()` → never affects OAL.
- **Excluded** from collision/overlap checks (`collisionGroup() → null`).
- **No body-splitting** (`splitBodiesAround` is NOT called) — cutouts don't consume axial body space.

This is the big simplification vs threads/liners: the hardest thread logic (splitting, OAL
exclusion math, collisions) does **not** apply here.

**Clocking is not modeled in v1.** True radial angle doesn't read on a side view; cutouts are drawn
on the top/bottom outline for visibility regardless of real clock position. Can add `clockingDeg`
later if a top-down view is ever introduced.

---

## 2. Rendering

Coordinate system is a **side view, symmetric about a horizontal centerline** (axial = x mm, radial
= diameter split top/bottom — `ShaftLayout.kt`).

Chosen representation (per owner: "cutouts should appear on the shaft like any other component"):
**a semicircular notch cut out of the shaft's top edge, mirrored on the bottom edge**, at each
cutout center along the row. Radius = `holeDiaMm / 2`; the notch bites *into* the profile (half in
shaft). Blind vs through can be conveyed by fill/hatch depth later; for v1 both draw the same notch.

- Rejected **(a) top-down dot** — doesn't read on a side view.
- Rejected **(b) dashed vertical line only** — that's dimensioning, not the physical cutout the owner
  asked to see on the shaft. (Kept in reserve for the deferred dimension rail.)
- Chosen **(c) scaled cutout on the profile** — literal, matches "carved out of the side."

Render sites (must all show the cutouts):
- Preview: `ui/drawing/render/ShaftRenderer.kt` draw loop (add a slot pass after threads / before
  liners) + resolved-component color options in `ShaftDrawing.kt`.
- PDFs: `pdf/ShaftPdfComposer.kt` shaft-profile drawing; also `RunoutPdfComposer.kt` /
  `WearPdfComposer.kt` reuse the shaft profile, so verify cutouts appear there too.

---

## 3. UI

Same add/edit trio as threads:
- Add entry "Coupler Bolt Slot" in `ui/dialog/InlineAddChooserDialog.kt`.
- New `AddCouplerBoltSlotDialog` in `AddComponentDialogs.kt` — fields: position (FWD-referenced),
  hole Ø, count, spacing, through/blind + blind depth.
- Carousel edit card in `ComponentCarousel.kt` (add `ResolvedCouplerBoltSlot` branch) — same fields,
  plus the deferred **"show dimension rail"** toggle (off by default).

---

## 4. Dimensioning

- Slots are **reference features** — they do **not** drive OAL (consistent with the OAL doc: only
  liners/tapers are the exact-length features).
- When rails are eventually built: dimension **from an end (default FWD)** to the row, plus
  **spacing between adjacent cutouts**. Owner confirmed FWD default.
- **Deferred:** dimension rail is a per-card opt-in, OFF by default, low priority. v1 ships the
  visual cutouts only. Rail builder would live in `pdf/dim/` alongside `LinerSpanBuilder`.

---

## 5. Wiring surface (effort)

Full cross-type parity, ~45–55 touchpoints across ~15–20 files (mapped):
`ComponentKind` enum + every `when` branch (`ShaftSpecExtensions.segmentFor/withSegmentStart`,
`ShaftRoute` snackbar, `StartOverlapValidation` → null), `ShaftSpec` list, `CouplerBoltSlot` model,
`ResolvedComponent` variant + `maxDiaMm/…` branches, ViewModel add/update/remove + `LastDeleted` undo
+ ordering (`orderAdd`/`ensureOrderCoversSpec`) + `rememberSlotDefaults`, add dialog + chooser +
carousel card, preview + PDF render, serialization (envelope is back-compat via default empty list).

Serialization needs **no migration** — old files decode with an empty slot list.

---

## Deferred / later

- Dimension rail for slots — the `showDimensionRail` field + per-card toggle exist and persist, but no rail is drawn yet (off by default, low priority).
- Blind-vs-through visual distinction (both draw identical circle cutout in v1; the `through`/`depthMm` data is captured).
- `clockingDeg` / top-down view.
- Coupler sleeve schematic.

---

## Implementation notes (v1)

Model settled as a **circle straddling the shaft outline** (half in shaft / half in coupling),
one axial **row** of `count` cutouts, `spacingMm` apart, authored from **FWD** by default. Files
touched:

- **Model:** `model/CouplerBoltSlot.kt` (new; `SlotAuthoredReference`), `couplerBoltSlots` list on
  `ShaftSpec` (+ `validate`). Excluded from `coverageEndMm`/OAL by design.
- **Enum/branches:** `ComponentKind.COUPLER_BOLT_SLOT`; branches in `ShaftSpecExtensions`,
  `StartOverlapValidation` (→ null, no collisions), `ShaftRoute` snackbar.
- **Resolved:** `ResolvedCouplerBoltSlot` — appended **after** body resolution (never enters
  auto-body/subtraction geometry), sorted back in by position.
- **ViewModel:** `addCouplerBoltSlotAt` / `updateCouplerBoltSlot` (+ reference/label/showRail) /
  `removeCouplerBoltSlot`; `LastDeleted.CouplerBoltSlot` undo/redo; order + session defaults.
  **No `ensureOverall()`**, no body-split.
- **Render:** overlay pass in `ShaftRenderer` (preview) and `drawCouplerBoltSlots` in
  `ShaftPdfComposer`, reused by Runout + Wear PDFs — cutouts appear everywhere the shaft is drawn.
- **UI:** `AddCouplerBoltSlotDialog`, chooser entry, carousel edit card (position from AFT/FWD,
  hole Ø, count, spacing, through/blind + depth, show-rail toggle).
- **Persistence:** back-compat automatic — new list defaults empty; `ignoreUnknownKeys` on decode.

**Verified:** `:app:compileDebugKotlin` and `:app:compileDebugUnitTestKotlin` both BUILD SUCCESSFUL.
**Not yet done:** run on emulator/device to eyeball the cutout rendering and dialog UX.
