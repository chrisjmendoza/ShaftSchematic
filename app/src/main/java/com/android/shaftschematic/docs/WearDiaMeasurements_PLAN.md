# Wear Diameter Measurements — Implementation Plan

**Status: IMPLEMENTED as designed (2026-07-28, branch `feat/wear-dia-readings`) — this doc
is the as-built design record. See `docs/RunoutSheet.md` § "Wear Diameter Measurements" for
the maintained contract.** Deviations from the plan: the strip's leader region reuses the
existing label headroom instead of a separate leader band (zero cost for reading-free
strips); the callout engine is two-phase (plan → finish) like `RunoutBubbleLayout` so both
surfaces can reserve exact band heights before fixing vertical layout; on-device feedback
(2026-07-29) moved Add Ø out of the Pits tool row into its own "Diameter measurements"
section (+ Remove Ø chip), and **retired the per-band min-Ø field/label** — this plan's
"leader can pass near a min-Ø label" caveat is therefore moot; `WearSpot.minDiaMm` remains
in the model for old files only.

Digitizes the shop's hand-marked diameter readings on the wear sheet (reference photo:
Tidewater STBD 934918, 2025-10-14): the machinist measures actual diameters at several
axial stations along a wear area / liner / taper / body and writes each value below the
profile with a short leader line pointing from the measured spot to the number
(e.g. `9.755  9.66  9.68  9.753` fanned under a liner, `10.000` at the unworn edge).
This is the diameter analog of runout bubbles: tap a component in the wear detail
overlay → add a Ø reading at that spot → it prints on the wear document with a leader.

## Contract class: reference-only feature (5th of its kind)

`WearDiaReading` follows the exact posture of wear pits / wear spots / runout readings /
coupler bolt slots (see CLAUDE.md):

- **Never** affects `coverageEndMm`/OAL, `ensureOverall`, body resolution/split/merge,
  collision, or the Free-to-End badge.
- Lives **outside `ShaftSpec`** — a new `diaReadings: List<WearDiaReading> = emptyList()`
  field on `WearRecord` (`model/WearSpot.kt`). Additive + defaulted ⇒ rides the existing
  `wear_record` envelope field: **no codec, autosave, snapshot, import, or `newDocument`
  plumbing changes** (same trick as `WearRecord.pits`).
- **Keyed by resolved component id** (`componentId = ResolvedComponent.id`) — a reading may
  sit on any liner, taper, or body (explicit or auto), same identity rule as `WearPit`.
- **Orphan handling at the render layer, not decode**: a reading whose `componentId` no
  longer resolves is simply not drawn (auto-body/taper ids aren't known to the codec) —
  same rule as pits and runout readings.
- **Draw-both-sites lockstep**: the callout (witness tick + leader + value text) must be
  drawn identically in the canvas overlay (`ComponentWearDetailOverlay`) and the PDF
  (`WearPdfComposer`), sharing pure math in `geom/` with no `pdf → ui` dependency.

## Data model

```kotlin
@Serializable
data class WearDiaReading(
    val id: String = UUID.randomUUID().toString(),
    val componentId: String = "",  // resolved component id (liner/taper/body)
    val axialMm: Float = 0f,       // component-local, from the component's AFT edge
    val diaMm: Float = 0f,         // measured diameter, canonical mm; 0 = no value yet
)
```

- `axialMm` component-local from AFT edge (survives repositioning) — identical convention
  to `WearPit.axialMm` / `WearSpot.startMm`. Shaft space = `startMmPhysical + axialMm`,
  render-time only.
- No `acrossFrac`: a diameter belongs to the axial cross-section, not a surface point.
  The witness tick spans the full drawn diameter at that station; the leader hangs from
  the **bottom** surface (labels always go below — matches the sketch and the
  "diameter callouts are BELOW-only" schematic rule).
- `diaMm = 0` means "station placed, value not entered yet" — drawn in the overlay
  (so the user can find/edit it) but **skipped on the printed PDF** (a pointer to nothing
  is noise). KDoc must state this.
- **Golden rule**: `diaMm` is a user-typed measurement — stored verbatim, never snapped,
  rounded, or clamped. `axialMm` from a tap is a coarse gesture (clamp to `[0, len]` is
  fine at placement); once stored it is never rewritten by any system.

## ViewModel (`ShaftViewModel`)

Mirror the pit API — plain `_wearRecord` updates, no geometry side effects:

```kotlin
fun addWearDiaReading(componentId: String, axialMm: Float, diaMm: Float)
fun updateWearDiaReading(id: String, diaMm: Float)   // value only; position is placement-fixed
fun removeWearDiaReading(id: String)
```

Dirty tracking / undo ride the existing `wearRecord` flow automatically.

## Pure math & layout (new files, `geom/`, android-free, unit-tested)

### `geom/WearDiaMath.kt` — sizing / hit-test / clamp (mirrors `WearPitMath.kt`)

- `diaAxialLocalMm(physicalMm, componentStartMm)` — same as `pitAxialLocalMm`.
- `DiaHitTarget(id, cx, topY, botY)` + `pickDiaReadingAt(px, py, targets, padPx)` —
  hit-test against the witness tick (a vertical segment), generous pad, nearest wins.
- Constants: tick overshoot past the surface, leader gap, label pad.

### `geom/WearDiaCalloutLayout.kt` — label spread + leader routing (the core)

One engine used by BOTH draw sites and BOTH surfaces (profile band + strip band).
Scaled-down sibling of `RunoutBubbleLayout` — same invariant style, label-width-aware
instead of fixed-radius circles:

Input: sorted stations `(stationX, surfaceBottomY)` + label widths + band geometry
(`contentLeft/Right`, `labelTextHeight`, `minGapPt`, `rowGapPt`, `leaderGapPt`).
Output per reading: `labelCx`, `row` (0 or 1), leader polyline (2 or 3 vertices).

Algorithm (all order-preserving; label x-order == station x-order, always):

1. **Row assignment**: all on row 0 if a single-row PAVA spread fits
   `contentLeft..contentRight` with per-pair min pitch
   `(w_i + w_j)/2 + minGapPt`. Otherwise alternate rows `0,1,0,1,…` (the sketch's
   stagger); cross-row pairs need only `(w_i + w_j)/2·0 + minGapPt` horizontal…
   no — cross-row pairs need `max(w_i, w_j)/2 + minGapPt` clearance against the
   **row-1 leader drop** (see 3). Same-row pairs (two apart) need full
   `(w_i + w_j)/2 + minGapPt`.
2. **X solve**: least-squares spread (pool-adjacent-violators, same as
   `RunoutBubbleLayout.solveBubbleX` — lift that private `isotonicNonDecreasing`
   into a shared internal or replicate it) with the pitch chain from (1), clamped to
   band bounds. Labels sit directly under their stations whenever there is room.
3. **Leaders**:
   - Row 0: straight diagonal `(stationX, surfaceBottomY + leaderGapPt) → (labelCx, row0Top)`.
   - Row 1: **dogleg** `(stationX, surfaceBottomY + leaderGapPt) → (labelCx, elbowY) →
     (labelCx, row1Top)` where `elbowY` is just above the row-0 label band — the final
     drop is vertical at `labelCx`, which invariant (1) keeps `minGapPt` clear of every
     row-0 label edge. Same provably-clear construction as the runout dogleg, one row
     shallower.
   - Verification pass in tests: no leader-label intersection, no leader-leader proper
     crossing, across randomized configs (port the assertion style from
     `RunoutBubbleLayoutTest`).
4. Degenerate fallback: if even two rows can't fit the band width, compress uniformly
   (flagged in the result) — same posture as `RunoutBubblePlan.compressed`.

Row Y placement is the caller's (each surface anchors `row0Top` below its own deepest
drawn edge; `rowStep = labelTextHeight + rowGapPt`).

## Value formatting

Bare number, no `Ø` prefix (matches the hand sketch and keeps labels narrow):
**≤3 decimals, trailing zeros trimmed, same as the schematic's `formatDiaWithUnit`
number part** — extract/share that helper (move to `util/` if it's private in
`ShaftPdfComposer`) rather than inventing a new format. Unit conversion at the edge only.

## PDF rendering (`WearPdfComposer`)

Two surfaces, one layout engine:

- **Liner readings → that liner's detail strip** (the zoomed view is where the shop
  reads them; profile-scale would crowd the names row). Band sits **below the cylinder**,
  after the min-Ø row: `row0Top = cylBottom + minDiaRowPt + WEAR_DIA_BAND_GAP_PT`.
  Witness tick: vertical line across the full cylinder height at `xAtStrip(axialMm)`,
  overshooting each edge slightly (`WEAR_DIA_TICK_OVERSHOOT_PT ≈ 2`).
  Leader from tick bottom to label per the engine.
  - `computeWearStripInnerLayout` (`pdf/WearStripLayout.kt`) grows a reserved
    `diaBandRows: Int` (0/1/2) × `WEAR_DIA_ROW_PT` (≈ 12) band between cylinder and
    title; cylinder shrinks first, same degradation order as the rail label rows.
    Strips already absorb surplus page height (cap `WEAR_STRIP_HEIGHT_MAX_PT = 170`),
    so the common case costs nothing visible.
  - Known caveat (accepted): a leader can pass near a min-Ø label; readings and bands
    rarely share exact x. Revisit only if real sheets show collisions.
- **Body/taper readings → main profile**, band below the shaft, **below** the
  names/direction row (`WEAR_PROFILE_NAMES_ROW_PT`): `row0Top = namesRowBottom + gap`.
  Surface Y at the station: `cy + rPx(diaAt(axialMm))` (taper Ø interpolated, same as
  `drawWearPitsOnProfile`). Reserve the band in `preferredProfileHeightPt` **only when
  ≥1 body/taper reading exists** (readings-free sheets are pixel-identical to today).
- Liner readings do **not** draw on the main profile (strip-only); body/taper readings
  draw profile-only. A liner beyond the strip cap loses its readings on print — noted
  limitation, same class as other strip-overflow content.
- `diaMm == 0` readings are skipped on the PDF.
- **Blank mode (`blankValues = true`)**: readings omitted entirely — consistent with
  bands/pits/min-Ø (`effectiveRecord = WearRecord()` already handles it). Future option
  (not now): print leaders + empty write-in rules at recorded stations.

## Overlay UI (`ComponentWearDetailOverlay`, `LinerWearDetail.kt`)

- Tool chips row gains **"Add Ø"** (with existing Add X / Remove X). In Add-Ø mode:
  - Tap on the drawn segment → hit-test existing ticks first (`pickDiaReadingAt`):
    hit → open the value dialog for that reading (edit/delete); miss → clamp tap x to
    `[0, len]` component-local, `addWearDiaReading(...)` **after** the dialog saves
    (placing then Cancel must not leave a ghost zero-value reading — create on Save).
  - Existing readings draw regardless of mode; hit-test-to-edit only in Add-Ø mode
    (same "explicit tool" posture that keeps stray taps from editing).
- **Value dialog**: small AlertDialog with one numeric field (unit-edge conversion,
  commit semantics per `NumberField.md`), Save / Cancel / Delete (Delete only when
  editing an existing reading). No clock ring — this is the trivial cousin of
  `RunoutBubbleDialog`.
- **Canvas drawing**: witness tick across the segment + leader + value below, using the
  SAME `WearDiaCalloutLayout` engine (px units) anchored under the drawn box, above the
  "← AFT / FWD →" caption. A zero-value reading draws its tick with a hollow placeholder
  (e.g. `—`) so it's findable — overlay-only affordance, never printed.
- `Clear all pits` stays pits-only; no bulk-clear for readings (dialog Delete suffices;
  add later if field use asks for it).

## Tests (JVM)

- `WearDiaCalloutLayoutTest`: order preservation, min-pitch invariants, leader
  clearance verification across randomized configs, two-row fallback, compression flag,
  single-reading trivial case.
- `WearDiaMathTest`: hit-test pad/nearest-wins, axial clamp.
- `WearRecordCodecTest` (or existing codec test file): old JSON without `dia_readings`
  round-trips; readings survive encode/decode.
- `WearStripLayoutTest`: `computeWearStripInnerLayout` with `diaBandRows` — budget
  degradation order, zero-rows unchanged vs today (regression pin).

## Docs & memory

- `docs/RunoutSheet.md`: new "Wear Diameter Measurements" section (posture table entry,
  draw-both-sites rule, layout engine pointer).
- `CLAUDE.md`: add to the reference-features invariant block (short — point at the doc).
- No dates / no prior-code narrative in `.kt` comments.

## Verification (no on-device round-trip needed)

Same-math SVG artifact (established workflow): a small JVM `main`/test in the scratchpad
drives `WearDiaCalloutLayout` + the strip/profile geometry with 2–3 sample specs
(incl. the photo's 4-readings-under-a-liner case and a crowded 8-reading case) and emits
SVG mirroring the PDF draw calls; publish via Artifact for markup review. Then
`gradlew test` + `assembleDebug`.

## Open questions (defaulted, flag to Chris)

1. Blank mode: print write-in leader stations for recorded readings? **Default: no** —
   blank stays fully record-free, consistent with pits/bands.
2. Nominal-vs-measured delta (photo's `10.000` edge reading looks like the nominal):
   out of scope — readings are flat values; no delta computation.
3. Liner readings on the main profile too? **Default: strip-only** (profile has the
   names row and bands already; strip is the zoomed reading surface).
