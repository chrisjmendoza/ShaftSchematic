# RunoutSheet & WearDocument

**Files:**
- `geom/RunoutBubbleLayout.kt` — shared bubble placement engine (stations, rows, x-solve, leader routing + collision verification)
- `geom/ProfileCompression.kt` — pure compressed-profile x mapping + height/scale solve
  (`buildCompressedProfileXMap`, `solveMaxProfileScale`, `exaggeratedProfileScale`,
  `drawnShaftHeightPt`/`heightFracForDrawnHeight`)
- `geom/WornSectionMath.kt` — pure worn-section column/halo layout + value auto-fit
- `pdf/BreakSymbol.kt` — S-curve break edge geometry + `breakPairLayout` (shared by every
  composer and the Compose port in the wear detail overlay)
- `ui/screen/RunoutRoute.kt` — runout station config, canvas preview, PDF preview overlay
- `ui/screen/OutputRoute.kt` — Consolidated Output tab: variant election, worn-section
  editor, shaft-height slider, preview/print/export + "Export all"
- `ui/screen/OutputDoc.kt` — output selection + filename shapes (`buildOutputFilename`)
- `ui/screen/WornSectionEditor.kt` — worn-section list + `WornSectionDialog`
- `ui/screen/ShaftHeightSlider.kt` — shared "Shaft height" slider (selects drawn height by
  value in paper inches)
- `ui/screen/WearRoute.kt` — wear inspection document tab; interactive shaft canvas + wear
  tap/badges over bodies/tapers/liners (Phase 2, see "Liner Wear Inspection (UI)" below)
- `ui/screen/LinerWearDetail.kt` — full-screen component wear detail overlay
  (`ComponentWearDetailOverlay`; liners get bands + pits, bodies/tapers get pits only — see
  "Wear Pits" below)
- `ui/screen/LinerWearMath.kt` — pure math for tap hit-testing, band clamping, and detail-canvas
  scale, shared by the two files above
- `geom/WearPitMath.kt` — pure pit ("X" marker) sizing/hit-test/clamp math, shared by the canvas
  and PDF draw sites (no `pdf → ui` dep)
- `pdf/RunoutPdfComposer.kt` — letter-landscape runout PDF generation
- `pdf/WearPdfComposer.kt` — letter-landscape wear document PDF generation
- `pdf/WearStripLayout.kt` — android-free pure-math layout for the wear PDF's per-liner
  detail strips (Phase 4); see "Wear Detail Strips" below

---

## Responsibilities

### RunoutRoute
- Let the user set TIR orientation (Looking AFT / Looking FORWARD / Not set).
- Let the user override the station count per component (bodies, tapers, liners).
- Render a live canvas preview of the shaft with runout bubbles.
- **Tap a bubble to record its TIR value + high-spot marker** — see "Runout Bubble Editor" below.
- Export a fill-in PDF runout sheet via SAF (any recorded values/markers are printed in place).
- Preview the PDF in-app via `PdfPreviewOverlay` with a Tune options sheet.

### WearRoute
- Render a live, tappable shaft canvas (bodies, tapers, and liners) so the user can
  inspect/record wear — see "Liner Wear Inspection (UI)" and "Wear Pits" below.
- Preview the wear document PDF in-app via `PdfPreviewOverlay` with a Tune options sheet.
- Export the wear document PDF via SAF — recorded wear prints by default; the blank
  (write-in) template is the same document in **blank-draft mode** (`blankValues = true`),
  for field damage and dye-pen inspection marking.

Both tabs share the same layout pattern: outer `Column` with `systemBarsPadding()`, a toolbar `Row` (hamburger + title), `HorizontalDivider`, then a vertically-scrollable inner `Column`.

---

## Liner Wear Inspection (UI, Phase 2/3, 2026-07-18)

See `docs/LinerWearAreas_Proposal.md` for the full feature scope; this section covers only
the UI contract added on top of the existing wear-document tab.

**Overview canvas (`WearRoute.kt`)** — same rendering pattern as `RunoutRoute`'s preview
canvas (`ShaftLayout.compute` + `ShaftRenderer.draw` against `resolvedComponents`, never raw
spec), but deliberately **no pinch-to-zoom** — this canvas only needs a single tap gesture, so
skipping the transformable/zoom state kept the tap-coordinate math simple (no scale/offset to
divide out). After `ShaftRenderer.draw`, `drawWearAffordances` adds a faint primary-tint
fill + border over every liner (tap affordance) and a small count badge above any liner that
already has ≥1 recorded wear spot. (It has since been generalized to every pit-eligible
component — bodies and tapers too; see "Wear Pits" below.)

**Tap hit-testing** inverts the existing `ShaftLayout.Result.xMmFromPx` to get the tap position
in mm, then calls the pure `pickLinerIdAtMm` (`LinerWearMath.kt`) to pick the liner whose span
contains it — ties (a tap exactly on a shared boundary) broken by whichever liner has the
nearer edge. A hit opens `LinerWearDetailOverlay` for that liner's id.

**Detail overlay (`LinerWearDetail.kt`)** — a full-screen composable, not a nav destination,
same shape as `PdfPreviewOverlay`: its own `BackHandler` plus a back-arrow top bar. Its
broken-out canvas supports **pinch-to-zoom / two-finger pan** (2026-07-29, on-device request:
accurate pit / band / Ø placement) via the RunoutRoute preview's transform pattern —
`transformable` → `graphicsLayer`, taps inverted through the transform so placement math is
zoom-independent. (The WearRoute *overview* canvas remains deliberately zoom-free — single
tap-to-open only.) Layout math
is self-contained (draws one liner + short neighbor stubs, not the whole shaft, so it does not
use `ShaftLayout`): `computeLinerDetailPxPerMm` is width-driven but capped by an available-height
budget so a very short liner doesn't blow its drawn diameter off-canvas. Neighbor stubs (~24dp)
come from `resolvedComponents` at the diameter touching the liner, terminated at their *far* end
with a Compose port of the pdf layer's S-curve break edge (`pdf/BreakSymbol.kt`'s math,
redrawn with Compose `Path`/`DrawScope` rather than importing pdf code) — the edge touching the
liner itself is a plain line (a real boundary, not a "cut").

**End-thread stubs (2026-07-26):** a neighbor **thread with nothing beyond it** is the shaft's
threaded end, not a truncation — its stub gets a **flat outer edge + diagonal thread hatch**
(`drawThreadStubHatch`, same "legacy look" convention as `ShaftRenderer.drawThreadHatch`, fixed
8 px pitch since the stub is symbolic) instead of the S-break, which would misread as "shaft
continues past here". Any other neighbor type keeps the break (the fixed-width stub genuinely
truncates it), and no neighbor at all still means no stub — the component's own flat edge is the
end. Overlay-only: the wear PDF's detail strips are liner strips, and a liner always has real
material on both sides, so their break stubs are unchanged (no draw-both-sites obligation). Wear bands render as hatched/tinted
rects at `clampWearBandToLiner` positions (visual clamp only; the underlying `WearSpot` is never
mutated) with a small per-spot dimension rail below (offset from the liner's AFT edge, then band
length, formatted via the existing `disp`/`abbr` helpers in the active unit). Spot cards below
the canvas use `NumericInputField` for Start/Length (commit-on-blur, tap-and-leave no-op,
per `NumberField.md`) plus a same-discipline Notes field, a delete icon per card, and an
"Add spot" button wired to `ShaftViewModel.addWearSpot`/`updateWearSpot`/`removeWearSpot`.

**Break-edge eye orientation (2026-07-18 fix):** `drawBreakEdgeCompose`'s `eyeAtTop` must be
chosen so the eye's larger "sweep" curve bulges into the **void** side of the break, never the
material side. This is the *opposite* of the flag choice used for a centered compression break
(`ShaftPdfComposer`/`WearPdfComposer`'s body-shortening breaks, where the two break edges face a
shared gap in the middle — there, left edge = false, right edge = true): here each stub's break
sits at its own far/outer end (void beyond it, material toward the liner), so the mapping
inverts — left (AFT) stub = `eyeAtTop = true`, right (FWD) stub = `eyeAtTop = false`. The same
fix applies to `WearPdfComposer.kt`'s `drawWearDetailStrip` neighbor-stub break calls (its main
shaft-profile compression break is unaffected — that one *is* the centered-gap case and keeps
the original flags). See `drawBreakEdgeCompose`'s KDoc for the full derivation.

**"Measure from" reference (Change 1, 2026-07-18 post-review spec)** — each `WearSpot` carries
an additive, defaulted `authoredReference: WearSpotReference` (`LINER_AFT` / `LINER_FWD` /
`AFT_SET` / `FWD_SET`), display-only metadata recording which of four reference points the
Start field was entered against. **Canonical storage is unchanged**: `WearSpot.startMm` is
always liner-local, measured from the liner's AFT edge — the same convention documented on the
model. Conversion (`ui/screen/LinerWearMath.kt`):
- `LINER_AFT` / `AFT_SET` (AFT-referenced): the entered value locates the band's **AFT edge**,
  measured FWD from the reference point. `LINER_AFT` canonical = entered as-is; `AFT_SET`
  canonical = `(aftSetXMm + entered) − liner.startFromAftMm`.
- `LINER_FWD` / `FWD_SET` (FWD-referenced): the entered value locates the band's **FWD edge**,
  measured AFT from the reference point. `LINER_FWD` canonical = `linerLengthMm − entered −
  lengthMm`; `FWD_SET` canonical = `(fwdSetXMm − entered) − liner.startFromAftMm − lengthMm`.

`wearStartToCanonicalMm`/`canonicalToWearStartMm` are the pure, exactly-inverse conversion pair.
AFT/FWD SET positions come from `geom/OalComputations.kt`'s `computeOalWindow` +
`computeSetPositionsInMeasureSpace` (computed once per overlay open in `LinerWearDetailOverlay`
and threaded down to each `WearSpotCard`) — its `measureStartMm` is always `0.0`, so the
returned measure-space X values already are physical shaft-space mm from AFT, the same space as
`liner.startFromAftMm`. Switching the "Measure From" chip re-projects the *displayed* Start
value only and persists the reference immediately via `ShaftViewModel.updateWearSpotReference`
(mirrors `updateLinerAuthoredReference`/`updateCouplerBoltSlotReference`) — canonical `startMm`
never moves as a result, same rule as the Liner/CouplerBoltSlot AFT/FWD chips.

**Blocking in-span validation (Change 2)** — a wear band's canonical span
`[startMm, startMm + lengthMm]` must lie entirely within `[0, linerLengthMm]`. This is enforced
at **entry** via `NumericInputField`'s `validator`/`externalIssueText` (per `NumberField.md`):
an out-of-span commit is rejected, the field reverts, and the model is never touched. Checked
for both the Start field (after converting to canonical via the active reference) and the
Length field (existing canonical start + new length must fit) using the pure
`wearSpotSpanIssue(...)` classifier (epsilon `1e-3mm`, boundary-exact bands accepted). Stale
data — a spot that was valid when recorded but no longer fits because the liner was later
shortened — is **not** retroactively blocked: the render clamp (`clampWearBandToLiner`) remains
the safety net, and the spot's card shows a small warning icon + "Extends past liner end —
re-measure" text instead, driven by the separate non-blocking `isWearSpotStaleOverrun(...)`
classifier. `ShaftViewModel.addWearSpot`'s default 25.4mm (1in) band length is clamped to the
liner's own length so the default is never rejected on a tiny liner.

---

## Wear Pits (interactive "X" markers, 2026-07-21)

The shop hand-marks pits / dye-penetrant failures as little "X"s on the shaft — small for a tiny
hole, larger for a bigger cavity. This digitizes that, and answers the proposal's §10.5 open
question ("wear on bodies/tapers"). Pits are the fourth reference-only feature (see `CLAUDE.md`).

**Data model** (`model/WearSpot.kt`): `WearPit(id, componentId, axialMm, acrossFrac, size: PitSize)`
in `WearRecord.pits` — stored in the **existing** `wear_record` envelope field (no new field, so
no autosave/snapshot/import/`newDocument` plumbing changed; it all rides `WearRecord` as before).
- **Keyed by resolved component id.** Unlike a `WearSpot` (liner-only, keyed by `linerId`), a pit
  sits on any pit-eligible component — a **liner, taper, or body** (explicit or auto). `componentId`
  is the `ResolvedComponent.id`, the same identity a runout reading uses.
- **`axialMm`** is component-local (from the component's AFT edge), so a pit survives the component
  being repositioned — same convention as `WearSpot.startMm`. Shaft-space = `startMmPhysical + axialMm`.
- **`acrossFrac`** ∈ interior band (`clampPitAcrossFrac`, `[0.08, 0.92]`) places the X vertically
  within the drawn segment (`0` = top outline, `1` = bottom) — purely visual.
- **`size`** = `SMALL` | `LARGE`, a **symbol** size (how big the X is drawn), not the pit's true
  diameter. `LARGE` arm = `SMALL` arm × `PIT_LARGE_TO_SMALL_RATIO` (2.0 — small is half of large).
- **Orphans** (component no longer resolves): skipped at the **render layer**, not pruned at decode
  — auto-body/taper ids aren't known to the codec, same as runout readings. (Wear spots ARE pruned
  at decode, against the liner list — pits are deliberately not.)

**ViewModel** (`ShaftViewModel`): `addWearPit(componentId, axialMm, acrossFrac, size)` /
`removeWearPit(id)` — plain `_wearRecord` updates, no geometry side effects. (A pit's
size is chosen at add time; resizing an existing pit has no UI and no VM method.)

**UI** — the overview canvas (`WearRoute.drawWearAffordances`) now tints **every** pit-eligible
component (body/taper/liner) as a tap target and badges each with its total recorded wear
(spots + pits). A tap opens `ComponentWearDetailOverlay` (`LinerWearDetail.kt`, generalized from the
liner-only overlay): it breaks the tapped component out of the shaft (S-curve stubs) and draws it
enlarged — a rect for a body/liner, a trapezoid for a taper (`componentEdgeDias` +
`radiusLocalPx`). A **← AFT / FWD →** caption under the drawn box gives the shaft-direction
reference (AFT drawn left, FWD right — the schematic/PDF convention). **Liners** still get the full
wear-band editor (`WearSpotCard`s, dimension rail); **bodies/tapers** get pits only. Pit interaction
uses **explicit tool chips** so a stray tap can't edit by accident: **Add X** (tap places at the
current Small/Large brush), **Remove X** (tap deletes the X hit; a miss is a no-op), and a **Clear
all pits** button. The tap handler and the Canvas renderer share one layout
(`computeSegDetailLayout`) so a tapped X removes exactly the X that was drawn (`pickPitAt` from
`geom/WearPitMath.kt`, generous touch pad).

**Rendering** (all draw sites, in lockstep — same crossed-line construction, same small:large
ratio; only the destination units and API differ, exactly like the runout marker):
- **Canvas detail:** `LinerWearDetail.kt`'s `drawPitX` (Compose `DrawScope`), base half-arm
  `PIT_SMALL_HALF_DP` (4.5dp; large 9dp).
- **PDF main profile:** `WearPdfComposer.drawWearPitsOnProfile` — X at each pit's true axial +
  across position, taper diameter interpolated at the pit's axial. Base half-arm
  `WEAR_PIT_SMALL_HALF_PROFILE_PT` (1.7pt; large 3.4pt). The shaft profile is always drawn now (see "Wear PDF
  Rendering Modes"), so body/taper pits always have a whole-shaft view.
- **PDF detail strip:** liner pits also drawn on the broken-out strip (base half-arm 2.5pt; large 5.0pt),
  reinforcing the profile pits at the strip's larger scale.

---

## Wear Diameter Measurements (measured-Ø callouts, 2026-07-28)

The digital form of the shop's hand-written diameter values under a worn section (reference
photo: values fanned below the shaft, each with a leader pointing at the measured spot, plus
the nominal at an unworn edge). The fifth reference-only feature — see `CLAUDE.md` and
`docs/WearDiaMeasurements_PLAN.md` for the full design.

**Data model** (`model/WearSpot.kt`): `WearDiaReading(id, componentId, axialMm, diaMm)` in
`WearRecord.diaReadings` — additive/defaulted, rides the existing `wear_record` envelope
field (no codec/autosave plumbing). Keyed by **resolved component id** (liner, taper, or
body — explicit or auto), component-local `axialMm` from the AFT edge, no across position
(a diameter belongs to the whole cross-section; callouts always hang **below**). `diaMm` is
stored **verbatim** (golden rule); `0` = station placed but no value yet — drawn in the
overlay (findable/editable), **skipped on the printed PDF**. Orphans skipped at the render
layer, never decode (`WearRecordPersistenceTest` pins that readings on unknown component
ids survive decode).

**ViewModel**: `addWearDiaReading` / `updateWearDiaReading` / `removeWearDiaReading` —
plain `_wearRecord` updates, no geometry side effects.

**UI** (`ComponentWearDetailOverlay`): a dedicated **"Diameter measurements"** section below
Pits (own header + recorded count), with its own tool chips **Add Ø** / **Remove Ø**
(on-device feedback: a lone Add Ø chip inside the Pits tool row blended in and read as a
pit action). Both sections share ONE active canvas tool (`WearCanvasTool`) — selecting a
chip in either section deselects the other's, so a stray tap can never do the wrong
section's action. In Add Ø mode a tap on an existing witness tick opens its edit dialog
(Save / Cancel / Delete); a tap on bare metal opens the add dialog for that axial position —
the reading is created **only on Save**, so cancelling never leaves a ghost zero-value
station. In Remove Ø mode a tap on a tick deletes its reading (a miss is a no-op, same
posture as Remove X). The dialog is one numeric field (unit conversion at the edge, value
stored verbatim). The canvas draws a thin witness tick across the segment at each station
plus the value callouts fanned below (a value-less reading shows "—").

**Placement engine — single source of truth: `geom/WearDiaCalloutLayout.kt`** (pure,
`WearDiaCalloutLayoutTest`), `RunoutBubbleLayout`'s label-width-aware sibling: labels on
one row when they fit, else two alternating rows (the sketch's stagger); order-preserving
least-squares x spread (shared PAVA solver); spacing invariants sized to label half-widths
(same-row `(wᵢ+wⱼ)/2 + minGap`, cross-row `max(wᵢ,wⱼ)/2 + minGap`); row-1 leaders dogleg
via an elbow above the row-0 band so drops provably clear every label; uniform compression
(flagged) only in degenerate widths. Hit-test math in `geom/WearDiaMath.kt`
(`pickDiaReadingAt`, point-to-tick distance).

**Rendering** (draw-both-sites lockstep, all through the one engine):
- **PDF detail strip** (`WearPdfComposer.drawWearDetailStrip`): liner readings print on
  their liner's strip — witness tick across the full cylinder height (overshoot
  `WEAR_DIA_TICK_OVERSHOOT_PT`), value labels in a band reserved **below** the cylinder by
  `computeWearStripInnerLayout(diaBandPt = …)` (label rows only; the leader region reuses
  the existing label headroom, so a reading-free strip's layout is byte-identical to
  before — `WearStripDiaBandTest`). Labels use `formatDiaWithUnit` — the same ≤3-decimal
  format as the footer's Ø text, no `Ø` prefix (matches the hand sketch, keeps labels
  narrow). **The per-band min-Ø label is retired** (on-device report: it collided with
  these values under a wear band) — measured-Ø readings ARE the diameter story now; the
  spot card no longer offers the min-Ø field and `WearSpot.minDiaMm` survives only for
  old files (stored value passed through commits verbatim, never printed).
- **PDF main profile**: body/taper readings print under the whole-shaft profile, in a band
  below the names/direction row; the leader originates on the drawn bottom surface (taper Ø
  interpolated at the station, same as pits). The profile band reserves the height via
  `preferredProfileHeightPt` only when such readings exist. Liner readings do **not** draw
  on the profile (the strip is the zoomed reading surface); a liner past the strip cap
  loses its readings on print — same class of limitation as other strip-overflow content.
- **Canvas overlay**: same engine in px, anchored under the drawn segment.
- **Blank draft**: readings omitted entirely (`effectiveRecord = WearRecord()`), consistent
  with bands/pits.

---

## Worn Sections (in-profile measured Ø, 2026-08-04 — runout/wear consolidation step 1)

First step of consolidating the runout and wear sheets into one document (on-device
request, with hand sketch): a **designated worn section** marks a measured area directly on
the **runout sheet's** shaft profile, and its measured diameters print **inside** the
profile — each value rotated 90° (reading bottom-to-top), stacked left→right across the
span — with a sheet-white **halo** knocked out behind every value, so no profile line runs
through a measurement number ("the lines will not draw where the numbers are").

- **Model — `model/WornSection.kt`**, `WearRecord.wornSections` (additive envelope field on
  `wear_record`, no codec change, never pruned at decode). A pure reference feature — the
  standard contract: never affects OAL/coverage, body resolution, collision, or the badge.
- **Coordinates**: shaft-space canonical `startFromAftMm` + `lengthMm` (the `Undercut`
  convention — a worn area may cross component edges; no component key → no orphans).
  `authoredReference` (reuses `UndercutReference`, SET values only: `AFT_SET`/`FWD_SET`) is
  display metadata for the Distance field; switching it re-projects the displayed value via
  the shared exact-inverse pair in `geom/UndercutMath.kt` — canonical never moves.
- **Values**: `diaMm: List<Float>` — the machinist's typed measurements, stored verbatim in
  list order (golden rule), printed as `Ø` + `formatDiaWithUnit`. Values ≤ 0 never print;
  an empty section draws boundaries only. Render-layer clamp for OAL overrun
  (`clampUndercutSpan`), plus a clamp to the drawn SET-to-SET window on the PDF.
- **Drawing — ONE implementation for both sites**: `drawWornSections` in
  `pdf/RunoutPdfComposer.kt` draws boundary lines (full local profile height at each end)
  plus the halo-and-rotated-text columns; the `RunoutRoute` preview canvas calls the SAME
  function through `nativeCanvas`, so preview and print cannot diverge. Halos are drawn
  after the profile and before the bubbles — they erase profile lines behind values but
  never a bubble or leader. Pure column/halo layout in `geom/WornSectionMath.kt`
  (`layoutWornSectionValues`, unit-tested; overflow-flagged, never dropped or reordered).
- **Editor**: "Worn sections" list on the runout screen + `WornSectionDialog` (S.E.T.
  chips, Distance, Length, up to 6 Ø fields; delete inside the dialog). No carousel card
  and no Add-component dialog — outside the add-dialog-parity invariant, like undercuts.
- **Blank draft**: boundaries print (they are the write-in areas), values drop — same rule
  as the write-in bubbles.
### Consolidation step 5 (2026-08-05, morning review) — Consolidated Output tab, variants, batch export, bubble spread

Same-day review feedback on step 4; supersedes step 4's picker placement:

- **The Consolidated Output tab** (`EditorTab.OUTPUT`, `ui/screen/OutputRoute.kt`, last in
  the sidebar, enabled when built): the Runout tab's step-4 output picker moved here and
  reshaped. The tab carries the consolidated sheet's **content election**
  (`ConsolidatedVariant`: **All three** (default) | Schematic + Runout | Schematic + Wear
  — the schematic rails + footer are always on; bubbles/TIR and wear info each electable
  via `composeRunoutPdf(includeBubbles/includeWearInfo)`; electing bubbles out returns
  their lanes to the shaft area), the **"Shaft height" slider**, the **worn-section
  editor** (moved from the Runout tab — sections print on this sheet), blank draft, and
  Preview/Print/Export of the consolidated sheet.
- **"Export all"** (same tab): checkboxes for the five documents (consolidated [current
  variant], schematic, runout, wear, undercut — all on by default) + one folder pick
  (`OpenDocumentTree` + `createPdfInTree`); each file goes through the hardened write, so
  one composer failure costs one file (an error page inside it), never the batch. A
  result line reports written/failed counts; nothing auto-opens.
- **The Runout tab returns to runouts only** ("the runout editor should only focus on
  the runouts"): live bubble canvas (wear overlays removed — they render on the Output
  tab's true-PDF preview), TIR selector, station counts, bubble value entry, and
  Preview/Print/Export of the **classic runout sheet** (`consolidated = false`,
  suffix `_runout`).
- **Shaft-height slider everywhere it matters**: the schematic composer takes the same
  per-job value (`composeShaftPdf(heightScale)`, slider also in the schematic PDF
  preview's Tune sheet — "I intended this"). The **1.5" ceiling is ABSOLUTE** (review
  correction of step 4's growth-only cap): a short shaft whose width-fit would draw
  taller is capped and simply doesn't span the page — "we still need room for the
  dimensional rails". Slider UX: shared `ShaftHeightSlider` — selects the drawn height
  **by value in paper inches** ("select the height by value, not percentage"): the track
  runs to 1.5" (or the shaft's 300% height when less), the picked value converts back to
  the stored multiplier (`drawnShaftHeightPt`/`heightFracForDrawnHeight`, pure), commits
  near the standard height snap to exactly 100% (`snappedHeightScale` — "don't want to
  fight the slider"), and a "Standard (X″)" button restores the default.
- **Export hardening unified** ("please unify"): every SAF export (schematic, runout,
  wear, undercut, consolidated, batch) writes through
  `util/PdfSafExport.writeShaftPdfToUri` — a composer throw yields a valid error page,
  never a truncated file — and the collision export gate now guards the wear and
  undercut tabs too.
- **Runout bubble algorithm** (on-device request with the hand-drawn reference):
  - *Body stations place evenly across the DRAWN span* — cell midpoints in page x,
    inverted to physical mm (`CompressedProfileXMap.mmAt`) — because a body surface is
    uniform and physical midpoints bunch into foreshortened runs. Liners/tapers keep the
    physical edge-inset convention (worn areas rarely reach a liner's edges — those are
    the best reading spots).
  - *Even-spread waterfill* (`RunoutBubbleLayout` rule 7): every adjacent bubble gap
    floor rises toward one common level (Σ max(gap, L) = available, capped at
    `spreadPitch` = 1.5 × sameRowPitch) so bubbles distribute the width under the shaft
    evenly instead of bunching; leaders stay straight wherever they clear, and a
    rerouted one keeps the clean vertical-drop dogleg. Floors only ever grow — no
    collision guarantee changes ("I did have two make contact" — the engine still makes
    contact geometrically impossible).

---

### Consolidation step 4 (2026-08-05) — output picker, shaft-height slider, liner size compression

Overnight wave (on-device request), three features on the Runout tab:

- **Output picker** — the tab's Preview / Print / Export buttons now act on a selected
  output: **Consolidated** (default), **Runout only**, **Schematic**, **Wear**
  (`ui/screen/OutputDoc.kt`; FilterChip row above the blank-draft toggle). The original
  outputs stay first-class alternatives — "we put so much work into designing and
  implementing them". Session-scoped selection (resets to Consolidated; a persisted
  sticky pick would silently export the wrong document later — the blank-draft posture).
  The Schematic / Wear / Undercut tabs keep their own hard-wired buttons; the Undercut
  Drawing is deliberately absent from the picker (it authors on its own tab). Filenames
  follow each document's historical shape via `buildOutputFilename`
  (`{customer_vessel_job | fallback}_{suffix}[_BlankDraft].pdf`; the consolidated sheet
  takes the `_consolidated` suffix, freeing `_runout` for the classic sheet). Because the
  consolidated sheet embeds the schematic's dimensions, the schematic's export gate
  (`exportPdfGate` — components exist, no collisions) now guards this whole surface.
- **Classic runout sheet restored** — `composeRunoutPdf(..., consolidated = false)`
  prints the original standalone layout: one-line job header, raised OAL span line
  (witness lines to the SET faces, value-in-break), profile + bubbles + TIR only — no
  dimension rails, no footer, no wear info (`drawRunoutHeader` / `drawOalSpanLine`,
  restored with the layout constants `HEADER_HEIGHT_PT` / `OAL_GAP_PT` /
  `OAL_LINE_SPACE_PT`). Both modes share the compressed profile, the bubble engine, and
  the shaft-height slider; blank-draft rules per mode are unchanged from each layout's
  own history.
- **"Shaft height" slider** — `RunoutConfig.heightScale` (per-job, rides the `.shaft`
  envelope like the undercut sheet's exaggeration slider; additive field, legacy files
  default to 1.0). A multiplier on the sheet's solved profile scale: 50%–300%
  (`PROFILE_HEIGHT_SCALE_MIN/MAX`), applied AFTER the conventional
  max(width-fit, visual scale, value-need) solve. The
  `PROFILE_MAX_SHAFT_HEIGHT_PT` = 108 pt ceiling is **absolute** (on-device direction):
  a short shaft whose width-fit would draw taller is capped too — it keeps true
  proportion and simply doesn't span the page width, leaving room for the dimension
  rails and the rest of the sheet; the page budget caps everything. (Step 4's percentage
  sliders ended their track at a computed `effectiveHeightScaleMax`; that helper is
  **gone** — step 5 replaced it with the value-based slider, which picks a drawn height in
  paper inches and converts back to the stored multiplier via
  `drawnShaftHeightPt`/`heightFracForDrawnHeight`. See step 5 above.) Pure arithmetic in
  `exaggeratedProfileScale` (`geom/ProfileCompression.kt`, unit-tested). Slider UI on the
  Runout tab (drag-local, commit-on-release), shown only for the Consolidated / Runout
  outputs it affects. The schematic keeps its fixed convention.
- **Liner size compression** (both composers) — on-device clarification of the earlier
  "no compressing liners" rule: what liners must never get is a **body-style S-break
  cutout**; proportional **size** compression is fine. Liners drop their `Float.MAX_VALUE`
  pin for a finite `PROFILE_MIN_LINER_PT` (100 pt) floor — they foreshorten in proportion
  above it, never draw an S-break (that glyph remains a body-only draw path), and the
  height-yield solve now serves keyway-bearing bodies alone. Dimension labels still print
  TRUE lengths, and the footer compression note keys off actual foreshortening, liners
  included.

---

### Consolidation step 3 (2026-08-04) — the ONE-SHEET: schematic rails + footer join

On-device request ("If I can fit all this by hand, then our app should have no problem",
with the full hand-drawn consolidated sheet): the runout sheet now prints the complete
consolidated drawing —

- **Dimension rails ABOVE the shaft** — the schematic's own system verbatim:
  `buildLinerSpans` + `buildTaperLengthSpans` spans, `RailPlanner` tier assignment
  (honoring `pdfPrefs.tieringMode`), `PdfDimensionRenderer` (value-in-break, smart
  arrows, blank-draft write-in gaps). OAL rides the topmost rail (`oalSpan`, label always
  the typed OAL). Labels print TRUE lengths while the drawn spans ride the compressed
  mapping — a foreshortened liner still reads its full length, exactly like the hand
  sheets. Compact lane constants (`RUNOUT_RAIL_GAP_PT` etc.) keep the block tight. This
  replaces the old standalone OAL span line.
- **Footer block at the bottom** — the schematic's `drawFooter` itself (made internal;
  ONE implementation for both documents): AFT/FWD taper columns (Rate, L.E.T., S.E.T.,
  Length, KW incl. spooned note, Threads), work-order center (Customer/Vessel/Job#/Date,
  bold Side, keyway-clocking note, Body Ø line), blank-draft write-in rules. Replaces
  the sheet's old one-line header; the footer's compression note keys off ACTUAL
  foreshortening (`xMap.isCompressedOver`). The TIR line sits directly above the footer.
- **Page order:** margin → OAL rail → dim tiers → shaft (compressed, with wear info) →
  bubbles → TIR line → footer → margin. Rail count feeds the vertical budget before the
  diameter-scale solve, so tall tier stacks squeeze the shaft area, not the page.

---

### Consolidation step 2 (2026-08-04, same day) — wear marks migrate, wear tab KEPT

On-device request following the worn-sections review:

- **In-profile Ø readings replace below-shaft callouts on this sheet.** Every
  `WearDiaReading` (body, taper, AND liner — the wear document itself zooms liner readings
  onto detail strips instead) draws at its station INSIDE the profile as a single rotated
  column over a knockout halo — `drawDiaReadingsInProfile` (`RunoutPdfComposer`), reusing
  `layoutWornSectionValues` with a degenerate span so the column centers on the station.
  Labels: `Ø` + `formatDiaWithUnit` (the wear doc's own callout engine keeps its
  prefix-free labels; two conventions until consolidation settles). Value-less readings
  and orphans skipped, as ever.
- **Wear areas + pit X's migrate here** ("for now for testing"): `drawWearMarksOnRunoutProfile`
  draws each spot's vertical-line band clamped to its liner span (reuses the wear
  composer's `drawVerticalBand` construction) and each pit X via the wear composer's own
  `drawWearPitsOnProfile` (made internal; per-surface `smallHalf`, `geom/WearPitMath.kt`
  keeps every X identical).
- **Z-order rule (text always on top):** marks first — bands, then X's — then worn-section
  boundaries, then ALL value text last (worn-section values, then dia readings), each over
  its sheet-white halo so nothing lines through a number. Same order on the preview canvas
  and the PDF (shared implementations via `nativeCanvas`).
- **Blank drafts** drop bands/X's/readings entirely (the wear doc's blank rule) and keep
  only worn-section boundaries as write-in areas.
- **Legibility (on-device report — values towered over the thin proportional profile,
  halos read as pasted white boxes):**
  - *Auto-fit:* every value shrinks until it (plus halo) sits inside its local band —
    surface-to-surface × `WORN_VALUE_BAND_FIT_FRAC` — via the pure
    `fittedValueTextSize` (`geom/WornSectionMath.kt`, unit-tested); one size per worn
    section (its longest value governs), per-station for point readings. Floored at
    `WORN_VALUE_MIN_TEXT_PT` (6 pt PDF) / 14 px canvas so numbers never become dust; at
    the floor a slight halo overhang is accepted.
  - *Profile compression — the hand-sheet convention (PDF; both composers share it):*
    drawn shaft **height follows TRUE diameter on the default sizing curve**
    (`defaultShaftHeightPt`; the STANDARD anchors are proportional — 8" → 1",
    6" → 3/4", 4" → 1/2", a line through the origin, the hand-sheet rule from the
    original rulered sketches (taller defaults read "chubby" on-device) — the line
    continues past both anchors until the absolute 1.5" ceiling; the flat 0.40 pt/mm
    `VISUAL_DIA_SCALE_PT_PER_MM` remains only as the degenerate-diameter fallback in
    `defaultVisualScale`; the anchor HEIGHTS are user-adjustable app-wide via
    Settings → PDF Export → "Default drawing size" —
    `PdfPrefs.curveLoHeightIn`/`curveHiHeightIn`, an inverted pair flattens at the low
    anchor) and is never diluted by shaft length — EXCEPT when a pinned span (a
    **keyway-bearing body**, whose drawn slot geometry must stay real) needs the room:
    then the height yields instead (`solveMaxProfileScale` bisects the largest scale
    that still lays out; "doesn't have to be perfectly proportional, just close").
    **Tapers may shrink but never equalize**: no flat floor (a flat floor equalizes
    unequal tapers when both clamp to it — on-device report) — a ratio-preserving
    fraction-of-true floor instead (`PROFILE_TAPER_MIN_FRAC_OF_TRUE`, λ-fit, never
    lowers the height; relative taper widths always read true). The x axis is
    otherwise schematic: spans may foreshorten but each kind keeps a writable minimum
    drawn width
    (`PROFILE_MIN_THREAD_PT` 36; `PROFILE_MIN_BODY_RUN_PT` 64 — room to write
    diameters and hang runout leaders, on-device request; `PROFILE_MIN_LINER_PT` 100 —
    room to write wear values in, see the 2026-08-05 liner clarification below; the
    SCHEMATIC composer instead uses the lean `SCHEMATIC_MIN_*` floors — 28/40/56 —
    because its values live on rails and callouts, not inside spans, so proportion
    wins there; liners additionally honor the per-job **"Liner compression" pair** —
    `RunoutConfig.linersProportional` checkbox holds them at true width and the
    `linerCompression` slider bounds how far they may foreshorten via
    `ProfileFeatureSpan.minWidthFracOfTrue`, control on the Output tab + schematic Tune
    sheet — "the key components we are measuring are the tapers and liners"; the
    **drawing height takes precedence**: the raises are best-effort, ignored by the
    scale solve and λ-fitted to the room the page has at the selected height
    (`fracFitFactor`) — a liner request never lowers the drawn shaft), and **above the
    floors width distributes in proportion to true length** — a longer body run draws
    visibly longer, equal runs draw equal (on-device request), no span ever stretches
    past true scale. Pure engine `geom/ProfileCompression.kt`
    (`buildCompressedProfileXMap` + monotone `solveSpanWidths` bisection, unit-tested);
    only BODY runs get the S-break pair when foreshortened (`drawBodiesForRunout` /
    the schematic's `drawBodiesCompressedCenterBreak` trigger on actual foreshortening;
    the pair lays out via `breakPairLayout` — `pdf/BreakSymbol.kt`, unit-tested — which
    widens the classic gap up to half the run before flattening the glyph, so the two
    edges' curves always keep ≥ 1 pt of daylight and never overlap, on-device report);
    liners/tapers/threads foreshorten silently, like the hand sheets. Everything rides
    the one `xAt` — dimension rails, bubble stations, worn sections, wear marks. The
    bubble-row budget is solved on a prelim linear map (scale ↔ rows cycle), and the
    drawing plan re-solves on the real mapping. The SCHEMATIC composer uses the same
    scale + engine (`ShaftPdfComposer` — dims, callout leaders, keyways, and the
    compression footer note all ride the compressed `xAt`).
  - *No liner grey on this sheet:* liners draw unfilled on both the canvas preview and
    the PDF regardless of the `shadedLiners` pref — against a grey liner every white
    knockout read as a pasted box (on-device request). Bodies/tapers keep the pref.
- **Division of labor (on-device decision):** the Wear page **stays** as the authoring
  surface — spots, pits, and point Ø readings are placed/edited there, and its own PDF is
  unchanged — while the Runout sheet is the consolidated **output** that features that
  wear information on its profile. Worn sections author on the Runout screen directly.
  `WEAR_TAB_ENABLED` (`EditorTab.kt`, currently `true`) remains as the one-line switch
  for a future full consolidation that retires the tab.
- **Consolidation direction (on-device decisions, 2026-08-04, not scheduled):**
  - *Liner detail strips (zoomed wear views):* the consolidated sheet starts WITHOUT
    them — go by the drawing. Future options under consideration: inline zoomed sections
    when only ≤ 2 liners carry wear ("it'll be tight"), or a **secondary print page
    option** carrying just the strips.
  - *Point readings (`WearDiaReading`):* to be migrated INTO worn sections over time (one
    authoring model), UI to be worked out. Until then they stay their own in-profile mark.
  - *North star:* ONE sheet — spec header + dimensioned schematic + runout + wear + notes
    (second hand sketch, 2026-08-04). Explicitly deferred.

---

## Runout Bubble Editor (interactive value + high-spot, 2026-07-21)

Tapping a bubble on the `RunoutRoute` preview opens `RunoutBubbleDialog` — a "zoom-in" on that one
bubble for recording its TIR reading and high-spot direction. Both are optional and independent.

**Data model** (`model/RunoutReading.kt`, reference-only): `RunoutReading(componentId, stationIndex,
valueMm?, highSpotHalfHours?)` in a `RunoutReadings` set, stored in the document envelope
(`ShaftDocCodec.ShaftDocV1.runoutReadings`, `@SerialName("runout_readings")`) beside `RunoutConfig`
and `WearRecord` — never inside `ShaftSpec`. Owned by `ShaftViewModel._runoutReadings` with
`setRunoutReading` (passing both values as null clears the entry — there is no separate
clear method); wired into the autosave combine, snapshot restore, JSON
import/export, and `newDocument` exactly like `runoutConfig`/`wearRecord`.

- **Value**: canonical mm. Entered/shown in the active unit via `util/formatRunoutValue` (fixed
  **3 dp / thousandths in both units, trailing zeros kept** so every bubble reads at the same
  precision — `.010` stays `.010`) and parsed back to mm on Save — unit conversion only at the edge.
- **High spot**: `highSpotHalfHours` ∈ `[0, 23]`, **30-minute clock ticks** (the shop's hand
  convention), 0 = 12 o'clock, clockwise, 15° each. Snapped from the free drag angle by
  `snapToClockTick`.
- **Identity / orphans**: keyed by `(componentId, stationIndex)`, where `stationIndex` is the
  station's ordinal within its component (assigned by `collectRunoutStations`, carried on
  `RunoutStationX`/`PlacedRunoutBubble`). Changing a component's station count can leave a reading
  whose index no longer maps to a live station — it is simply not drawn (render-time lookup misses)
  and pruned on the next edit. No decode-time drop (station identity needs resolved components +
  overrides, which the codec lacks).

**Dialog interaction** (`RunoutBubbleDialog.kt`): a large bubble Canvas with 24 clock ticks, cardinal
labels (12/3/6/9), and the keyway slot. Two `pointerInput`s (tap + `detectDragGestures`) map the
touch via `markerTickFromTouch` → `bubbleAngleDeg` → `snapToClockTick`, but **only when the touch is
on the ring band** (`isOnRingBand`); off-ring touches and the hollow centre (which hosts the value
field) are ignored. The marker follows the finger until release. Three buttons: **Clear** (reset the
working marker + value in place — the marker-removal affordance), **Cancel** (discard, close),
**Save** (persist via `setRunoutReading`, close; saving with neither value nor marker removes the
reading). Working state seeds from the stored reading, so reopening shows saved data until edited.

**Canvas tap → dialog** (`RunoutRoute`): `computeRunoutPreview` (a `Density` extension) hoists the
shared plan so the renderer and the tap handler compute identical geometry. The tap handler inverts
the preview's `graphicsLayer` zoom/pan (scale about the centre pivot, then translate — read live via
`rememberUpdatedState` so the gesture detector isn't re-keyed on every zoom) to map the tap into plan
space, then `pickBubbleAt` (generous tolerance for the small on-screen bubbles) selects the bubble.

**Rendering** (both draw sites, in lockstep): the recorded value is drawn centred in the circle and
the high-spot marker as a **short red dash straddling the rim** at the tick angle
(`clockTickRimOffset`) — no radial line, so the centred value stays legible (matches the hand sheets). The
pure clock/hit-test math lives in `geom/RunoutReadingMath.kt` (shared by `ui.screen` and `pdf` with
no `pdf → ui` dependency); value formatting in `util/RunoutValueFormat.kt`.

Bubble sizing (tuned 2026-07-21 from a printed sheet): the circle is roomy enough to hand-write a
value in (`BUBBLE_RADIUS_PT = 23` ≈ 0.64 in dia; on-screen `radius = 7.dp`), and the printed value
sits small inside it (`textSize = r * 0.60`). `formatRunoutValue` shows a **fixed 3 decimals
(thousandths), trailing zeros kept** (`.010`, not `.01`), and **drops the leading zero** before
the decimal (`0.010 → .010`, `-0.003 → -.003`) so the value fits and reads like a hand-written TIR.
Keep the radius and the `0.60` text ratio identical in both draw sites (`RunoutRoute.drawRunoutMarkers`
⇔ `RunoutPdfComposer.drawPlacedBubbles`).

---

## Bubble Placement Algorithm (RunoutRoute only)

**Single source of truth: `geom/RunoutBubbleLayout.kt`.** Both the canvas preview and
the PDF call the same engine (`collectRunoutStations` → `planRunoutBubbles` →
`RunoutBubblePlan.finish`), so the two renderings are identical by construction —
station math, row assignment, bubble x positions, and leader routing. Do not re-implement
any placement logic in a renderer. The engine is pure Kotlin (no Android imports) and is
covered by `geom/RunoutBubbleLayoutTest.kt`.

### Stations (mm domain)
Components are the **resolved** list (`resolveComponents()` output), not raw spec:
resolved bodies are subtracted against tapers/liners, split/merged, and include
auto-body gap fill. Raw spec bodies may legally overlap tapers/liners and must never
be used for station placement or profile drawing (2026-07-18 fix).

Per component (`runoutStationPositionsMm`):
- **Bodies:** cell midpoints, `(i + 0.5) · length / count` — even coverage of the full length.
- **Tapers / Liners:** inset from each edge by `min(RUNOUT_EDGE_INSET_MM ≈ 25.4 mm, 20% of length)` so measurements land on the cylindrical run, not the transition slope.

(2026-07-18: the canvas preview and PDF previously used *different* station math —
body stations `len/(count+1)` on the PDF vs cell midpoints on canvas, inset caps 20% vs
35%. Standardised on cell midpoints and the 20% cap in the shared engine.)

### Rows — alternating, globally aligned
Within each component, consecutive stations **alternate rows** (0, 1, 0, 1, …) — the
hand-drawn shop convention. Single-station components sit on row 0. When a component
would start on the same row the previous component ended on, close enough to collide,
its phase flips. All bubbles in a row share one centre Y anchored below the **deepest
drawn shaft point** (aligned rows across the whole sheet — bubble depth no longer varies
with the local shaft OD).

Spacing invariants (centre-to-centre horizontal, enforced between x-adjacent bubbles):

| pair | minimum dx | why |
|---|---|---|
| same row | `2·radius + minGap` | circles can never touch |
| different row | `radius + minGap` | a vertical leader drop at one bubble's x clears every circle in the rows above |

`rowStep = 2·radius + minGap` vertically, so circles on different rows are disjoint at any
dx. Because `2 × crossRowPitch ≥ sameRowPitch`, adjacent-pair constraints are sufficient
for all pairs.

**Leader clearance — comfort margin beyond the geometric minimum.** `crossRowPitch` is the
bare minimum that keeps a deeper bubble's leader from touching a shallower neighbour — it
does not leave room for a machinist to write a reading beside that neighbour without the
pen crossing the leader (reported from a real field PDF: a row-0 bubble sitting right next
to a row-1 bubble's leader between two mid-shaft liners). When a row has horizontal slack —
station spacing wide enough that the pitch constraint isn't the only thing pinning bubble
positions — every cross-row adjacent gap is widened by up to `RunoutBubbleGeometry
.leaderClearance` (`= minGap × LEADER_CLEARANCE_FACTOR`, `LEADER_CLEARANCE_FACTOR = 1.6`,
i.e. 8 pt at the PDF's `minGap = 5 pt`) on top of `crossRowPitch`. The extra is split evenly
across the eligible gaps and capped so the total never exceeds the row's actual available
span, so it can only ever grow a gap — never shrink one below its geometric minimum. A tight
row (no slack to spend) degrades to exactly the old behaviour: zero widening, same pitches
as before this existed.

**Two rows is a hard design point, not a simplification.** Every leader's final drop
passes through every row band above its bubble and needs its own horizontal lane
(`crossRowPitch`) past the circles there — so each bubble consumes ~one lane of width
regardless of how deep it sits. Rows 3+ therefore cannot reduce splay or increase
capacity; they only add page height and longer leaders. In tight regions the alternation
plus the boundary phase flip already put every binding adjacent pair on different rows
(the minimum pitch), which is the densest packing this leader convention allows. When the
station count cannot fit the content width at minimum clearances (~27 stations on a
letter page), spacing compresses uniformly (`RunoutBubblePlan.compressed = true`,
degenerate configs only — the collision guarantees are void in that case and
`RunoutBubbleResult.unresolvedCollisions` reports what's left).

### Bubble x — least-squares under constraints
Bubble x positions minimise Σ(bubbleX − stationX)² subject to the pitch constraints and
page bounds (isotonic regression / pool-adjacent-violators). Bubbles sit **directly under
their stations** whenever there is room; dense clusters spread symmetrically and stay
centred over their stations. Bubble order always equals station order.

### Leaders — verified, with dogleg fallback
Each leader is first tried as a straight diagonal from `(stationX, shaftSurfaceY)` to the
top of its bubble. The engine then runs an explicit collision check — segment-vs-circle
against every other bubble (inflated by `minGap/2`) and segment-vs-segment against every
other leader. Any leader that fails is re-routed as a **dogleg**:

```
(stationX, surfaceY)          vertical stub down to the common departure line
   → (stationX, departY)      (departY = deepest shaft surface; zero-length when already there)
   → (bubbleX, elbowY)        diagonal in the corridor above the row-0 circle tops
   → (bubbleX, bubbleTop)     vertical drop through the rows (clears circles by crossRowPitch)
```

All dogleg diagonals run between the same two horizontal lines with matching left-to-right
order at both ends, so dogleg-vs-dogleg crossings are geometrically impossible; the repair
loop therefore provably converges to **zero intersections** in every non-compressed
configuration. The unit test suite asserts this across randomized stress configurations,
stepped shaft surfaces (OD jumps), and dense component boundaries.

---

## OAL Dimension Alignment

Both `RunoutPdfComposer` and `WearPdfComposer` derive the horizontal draw span from the **SET-to-SET** extent, not `overallLengthMm`:

```
aftSetMm  = computeSetPositionsInMeasureSpace(oalWindow, spec).aftSETxMm
fwdSetMm  = computeSetPositionsInMeasureSpace(oalWindow, spec).fwdSETxMm
drawSpanMm = fwdSetMm − aftSetMm
ptPerMm    = contentWidth / drawSpanMm
xAt(mm)    = contentLeft + (mm − aftSetMm) × ptPerMm
```

**Why:** Measurements on these documents always originate from the SET faces (see `computeSetPositionsInMeasureSpace`), so the arrows bracket the SET-to-SET span regardless of end threads. Threads ARE drawn — hatched envelopes at their physical position, purely for visual reference — and end threads (including excluded-from-OAL threads, which live outside 0..OAL after `syncExcludedThreadPositions`) stick out past the arrow tips into the margins.

**Layout (2026-07-18):** the runout sheet's OAL dimension line sits `OAL_LINE_SPACE_PT` = 90 pt (≈ 1.25 in) above the shaft top — raised so it doesn't crowd the profile — with witness (extension) lines dropping to the shaft's actual top edge at each SET face (gap 3 pt, extending 5 pt past the line), matching the schematic/wear-document convention.

**Label rule (2026-07-11; header/blank wording trimmed 2026-07-28):** the printed value is
always the user's **typed OAL** (`spec.overallLengthMm`) — the same "OAL never changes" rule
as the main schematic (`OverallLength.md`). The arrows still bracket the drawn SET-to-SET
span; only the label uses the typed value. The printed label keeps its small `"OAL: "`
prefix (product decision, 2026-07-28: compact print output reads well and the prefix is a
nice visual identifier) and **seats in a break cut mid-span, vertically centred on the
line** — the same value-in-a-break convention as the schematic's dimension lines
(`PdfDimensionRenderer.drawSpan`), so all drawing outputs read the same; a span too short
for the break + inward arrows falls back to a continuous line with the label above.
Neither document's **header** repeats the OAL (it would just duplicate this span), and the
blank/write-in variant of this line carries no label text at all — the machinist
hand-writes the value in an empty break cut mid-span: see "Blank draft" below.

---

## Wear Document Page Layout

`WearPdfComposer` targets U.S. Letter landscape (792 × 612 pt) with 36 pt margins (720 × 540 pt content area).

```
┌── header line 1: Customer / Vessel / Job # / Date / Side  (centred) ──────┐
│── header line 2: "WEAR / INSPECTION RECORD"                (centred) ──────│
├────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│                                                                             │
│          [shaft profile — centred vertically, blank for hand annotation]   │
│         |←──────────────── nnn.nnnn" ─────────────────────→|              │
│         |                                                   |  ← witness lines
│   ══════╪═══════════════════════════════════════════════════╪══════         │
│                                                                             │
│   Dye pen inspection:  PASS □   FAIL □     Notes: ____________________    │
└────────────────────────────────────────────────────────────────────────────┘
```

The header never carries the OAL (that would duplicate the drawing's own end-to-end
dimension line below it) — see "Label rule" above.

**Y positions on a 612 pt page (36 pt margin), for a typical 5 in / 127 mm shaft:**

| Element | Y (pt) |
|---|---|
| Header line 1 baseline | 46 |
| Header line 2 baseline | 60 |
| Header separator rule | 72 |
| Drawing area top | 88 |
| **Shaft centre** | **≈ 306** (page centre) |
| Shaft top edge | ≈ 291 (depends on shaft OD and scale) |
| OAL dimension line | ≈ 201 (shaft top − 90 pt) |
| Drawing area bottom | 524 |
| Notes / checkboxes | 552 |

**Key layout decisions:**
- Header is split across two centred lines so long job-info strings don't overflow the 720 pt content width.
- Shaft centre is anchored under the profile band's OAL region — `profileTop + WEAR_OAL_TOP_REGION_PT + WEAR_OAL_ABOVE_SHAFT_PT + rPx(maxDia)` — plus half of any band slack, so a roomy band (0–1 strips, or strips capped at `WEAR_STRIP_HEIGHT_MAX_PT`) still reads centred while a snug one doesn't strand white space above the strips (2026-07-28; it used to be the band midpoint `(profileTop + profileBottom) / 2`).
- OAL dimension line is computed **after** the horizontal scale factor (`ptPerMm`) is known: `oalLineY = shaftCy − rPx(maxBodyDia) − WEAR_OAL_ABOVE_SHAFT_PT`. This anchors it above the actual drawn shaft top (44 pt since 2026-07-28) rather than at a fixed offset from the header.
- Witness (extension) lines are drawn at `x0` and `x1` from just above the shaft top up through the dimension line, matching standard engineering drawing convention.
- **Direction reference** (`drawWearDirectionRef`, 2026-07-21): "← AFT" (left) and "FWD →" (right) drawn just below the shaft's bottom edge so a shop reader can orient the whole sheet (AFT drawn left, FWD right — the SET/schematic convention).
- Notes row is anchored at `contentBot − WEAR_NOTES_BOTTOM_OFFSET_PT`, independent of shaft size.

**Layout constants (`WearPdfComposer.kt`):**

| Constant | Value | Role |
|---|---|---|
| `WEAR_HEADER_HEIGHT_PT` | 36 | Two-line header block height (rule sits at this offset from `contentTop`) |
| `WEAR_HEADER_GAP_PT` | 16 | Gap from header rule to drawing area top |
| `WEAR_OAL_ABOVE_SHAFT_PT` | 44 | Gap from shaft top edge to OAL dimension line. Reduced from 90 on 2026-07-28 (device feedback): 90 was inherited for parity with `RunoutPdfComposer.OAL_LINE_SPACE_PT`, from when the profile filled the page; on the strip-bearing wear sheet nothing occupies that band, so 44 clears the line + label and the rest of the height goes to the strips |
| `WEAR_NOTES_BOTTOM_OFFSET_PT` | 24 | Distance of notes baseline above `contentBot` |
| `WEAR_NOTES_GAP_PT` | 28 | Gap from drawing area bottom to notes baseline |

---

### Wear PDF Rendering Modes (2026-07-21 — profile always on top)

The **shaft profile is always drawn on top** of the wear document now (2026-07-21, user
request): body/taper pit "X"s live on the whole-shaft profile, so it must stay visible. The
detail strips below pick their layout from `determineWearPdfMode(collectWearLinerGroups(
docSpec.liners, wearRecord).size)` — since 2026-07-27 that is a pure function of the shaft's
**drawable liner count**: every liner with positive length and OD gets a strip, whether or
not it has recorded wear (the shop's normal operating procedure — the sheet always shows all
liners; a spotless liner's strip simply has no bands/callouts and its rail shows edge witness bars
only — a band-less rail draws no spanning length). Orphan spots on a since-deleted liner are dropped by `collectWearLinerGroups`; pits
don't affect the mode:

| Liners | Mode | Page shows |
|---|---|---|
| 0 | `PROFILE_FORM` | Shaft profile only (still prints any recorded pits). |
| 1 | `COMBINED` | Shaft profile + wear bands, with one full-width detail strip below. |
| 2+ | `GRID` | Shaft profile on top + the detail strips in a **2-column grid** below — two side by side, the third on the next row, so the strips take ~2 rows and the profile keeps the top. Up to `WEAR_STRIP_GRID_MAX_PER_PAGE` = 4 shown; "+N more" overflow note beyond. |

**Blank draft (write-in) mode** (`blankValues = true`, 2026-07-27; header/OAL reworked
2026-07-28): the same page — profile AND every liner's zoomed strip — renders as a hand-fill
template, mirroring the blank schematic's lines-in/values-out posture. The five header
job-info fields (`Customer:` / `Vessel:` / `Job #:` / `Date:` / `Side:`) spread edge-to-edge
across the full content width with equally sized writing rules (room for large handwriting,
device feedback), and the title "WEAR / INSPECTION RECORD" centers on the second line — the
header never carries an OAL field, printed or blank. The OAL dimension line itself keeps its
witness lines and arrowheads but drops the "OAL:" label + rule entirely; instead the span line
gets an empty `BLANK_DIM_GAP_PT`-wide break cut at mid-span — no underline, no text — the exact
convention `PdfDimensionRenderer` uses for blank schematic dimension breaks, so the machinist
writes the measured OAL directly into the gap. Recorded wear (bands, pits, measured-Ø readings) is omitted;
each strip's dimension rail shows **only the
two liner-edge witness bars** — no spanning line, arrowheads, or labels, since a band-less rail's
one span would merely re-state the liner's own length and the rail's job is dimensioning distances
to wear areas (2026-07-28 device feedback); the strip title's anchor value becomes a
writing rule followed by `WEAR_BLANK_ANCHOR_SUFFIX` ("FROM  AFT / FWD  S.E.T." — both directions
print, the machinist circles one), always left-aligned since the write-in sheet doesn't presume a
measurement direction. The blank header is taller (`WEAR_HEADER_HEIGHT_BLANK_PT` = 56 pt vs 36,
rule lines `WEAR_HEADER_BLANK_LINE_GAP_PT` = 24 pt apart) for handwriting room; the space comes out
of the profile→strips gap (`WEAR_STRIP_TOP_GAP_BLANK_PT` = 8 pt vs 18) per 2026-07-28 device
feedback.

This replaced the old strips-only mode (which dropped the profile at 3+ wear liners). Now that
bodies/tapers can carry pits, keeping the shaft always visible matters more than giving the strips
the whole page; compressing the strips two-up is what keeps the combined page from crowding.

**`GRID` layout** — `computeWearStripGridLayout` (`pdf/WearStripLayout.kt`) reuses
`computeWearVerticalLayout` with the **row** count (`ceil(strips / 2)`), so the profile still never
shrinks below its minimum and the "nothing wasted / nothing overflows" guarantee carries over
unchanged; each strip then takes its row's vertical band and one equal-width column slot across the
content width (`WEAR_STRIP_COL_GAP_PT` gutter). A partial last row (e.g. the lone third strip) is
**centered** at the same column width as a full row. Everything inside a strip — horizontal
cylinder/stub layout, dimension rail, measured-Ø callouts, anchor-from-SET title, and now pit "X"s — is the
same `drawWearDetailStrip` used by the single-column path; only the per-strip rectangle differs.

---

### Wear Detail Strips (Phase 4, 2026-07-18; 2-column grid 2026-07-21)

`composeWearPdf` takes an optional `wearRecord: WearRecord = WearRecord()` param (see
`docs/LinerWearAreas_Proposal.md` §6.2). Every existing call site is unaffected by the
default. All strip geometry (liner spans, neighbor diameters for the break-out stubs)
comes from `docSpec` — the spec after `withResolvedBodies(resolvedComponents)` — never
raw `spec.bodies`, same contract as the rest of this document. This section describes the
strip content itself, shared by the `COMBINED` (single full-width) and `GRID` (2-column)
layouts (see "Wear PDF Rendering Modes" above for how each positions the strips).

**Selection & pagination** — `pdf/WearStripLayout.kt` (android-free, unit-tested directly,
`WearStripLayoutTest`):
- `collectWearLinerGroups` builds one group per **drawable liner** (positive length + OD),
  attaching whatever spots `wearRecord` holds against it — including none (2026-07-27: every
  liner gets a strip regardless of recorded wear), sorted aft → fwd. Orphaned spots (stale
  `linerId`) are dropped defensively (the authoritative drop is at decode time,
  `ShaftDocCodec`).
- `selectWearStripsForPage` caps at `WEAR_STRIP_MAX_PER_PAGE` (3). Liners beyond that are
  **not** put on a second PDF page — `composeWearPdf` only ever receives a single
  caller-supplied `PdfDocument.Page` (every call site does one `startPage` /
  `finishPage`), and growing that into true multi-page output would mean changing the
  function's signature and every call site. Instead, overflow renders as one text note
  line ("+N more liner(s): ...") in a reserved band just above the notes area. Revisit
  if/when `composeWearPdf` grows multi-page support.

**Main profile** — liners with ≥1 wear spot get thin **vertical-line** bands
(`drawWearBandsOnProfile` → `drawVerticalBand`) at their true axial position, clamped to the
liner span (`clampWearBandToLiner`), drawn after the profile's own liner outlines. Visible but
not dominant — same alpha/weight/pitch as the old diagonal hatch, only the stroke orientation
changed (2026-07-22, to match how the shop marks wear areas by hand — the vertical tick style
in the reference sketch). The broken-out detail strips still use the diagonal hatch
(`drawHatchBand`). Each on-page liner's **name** is also printed centered under its span
(`drawWearLinerNamesOnProfile`, 2026-07-22), sharing the row with the "← AFT / FWD →" labels
(clamped clear of them) — a lightweight reference tying each band to its broken-out strip, using
the same shared title the strip title shows: `buildLinerTitleById` (`util/LinerTitles.kt`) —
custom label wins, else a positional AFT/MID/FWD default — identical names to the carousel
cards and runout sheet.

**Vertical page split** — `computeWearVerticalLayout` splits the profile band into a
(possibly shrunk) main-profile region followed by up to 3 stacked strips. The profile
never shrinks below `max(WEAR_MIN_PROFILE_HEIGHT_PT, 2×drawn-shaft-radius + margin)` —
folding in the actual radius matters because `ptPerMm` here is a purely horizontal
(SET-to-SET) scale, so a short/wide shaft's true diameter isn't otherwise height-aware.
When the preferred strip height doesn't fit, every strip shrinks together (never
independently, never past the main profile). The profile band also no longer absorbs all
leftover height — it shrinks toward a content-derived preferred height (OAL region + shaft
diameter + names row, `preferredProfileHeightPt`) and the strips absorb the surplus, capped at
`WEAR_STRIP_HEIGHT_MAX_PT` = 170 pt per strip with any remainder returning to the profile band,
whose shaft is slack-centered (2026-07-28 device feedback: dead white between shaft and strips).
By construction the last strip's bottom always lands exactly on the reserved area's bottom edge.

**Per-strip layout** — `computeWearStripHorizontalLayout` centers a break-out liner
(scaled `ptPerMm` local to the strip, capped/floored so very short/long liners don't
explode/vanish) between two fixed-width neighbor stubs; `computeWearStripInnerLayout`
then splits the strip's own vertical band into the single chained dimension rail (top), the
liner cylinder, and the title row (bottom) (see "Dimension rail" below) — the cylinder
shrinks first, and if a pathological input leaves no room at all, the rail's label rows
drop toward zero (the rail line still draws; labels are simply not placed) rather than
let anything render past the strip's bottom edge. (The rail and title were **swapped**
2026-07-22 — dimensions above the shaft, the liner title/anchor below it — to match how the
shop marks the sheet by hand.) The liner cylinder's radius always fills that vertical
budget (`computeWearStripRadii`), so every strip on the page draws its liner at the SAME
height regardless of the liner's length or OD — the strip's horizontal scale (`ptPerMm`,
which varies per liner since it's length-derived) must never leak into cylinder height
(on-device report: liners of different lengths were rendering at different heights).
Length differences stay horizontal-only; liner OD differences are deliberately not
height-encoded either (product decision). Each strip draws:
- Neighbor stubs at the resolved diameter abutting the liner (`neighborDiaMmAtAft` /
  `neighborDiaMmAtFwd`, falling back to the liner's own OD when there's no neighbor),
  scaled to their true diameter ratio against the liner and clamped to the liner's own
  radius (an oversized neighbor can't overflow the cylinder band), broken out with the
  standard S-curve edge (`BreakSymbol.drawBreakEdge`).
- Hatched wear bands on the liner at strip-local scale (diagonal hatch, unlike the
  main-profile bands' vertical lines), clamped the same way. (The per-band min-Ø reading
  that printed below each band is retired — superseded by the measured-Ø callouts, see
  "Wear Diameter Measurements" above.)
- One chained dimension rail above the cylinder (see "Dimension rail" below).
- One anchor-from-SET label per strip (`buildLinerAnchorLabel`) — the digitized form of
  the shop sketch's "110 FROM CPLG S.E.T." line. It reuses `mapToLinerDimsForPdf` +
  `LinerSpanBuilder.buildLinerSpans` verbatim, so the number always matches the liner
  dimension shown on the main schematic PDF. The **title is aligned to match the measurement
  direction** as a visual cue (2026-07-21): a FWD-SET-referenced strip right-aligns its title,
  an AFT-SET-referenced one left-aligns it (`linerAnchorForPdf` → `LinerAnchor`).

**Dimension rail (2026-07-18 rework; moved above the cylinder 2026-07-22)** — replaces the
original per-spot "AFT edge → band start" / "band start → band end" text rows with one standard
chained dimension rail **above** the liner cylinder, following the same
witness-line/arrowed-span/centered-label convention the main schematic uses
(`pdf/render/PdfDimensionRenderer.kt`):
- `buildWearStripRailSpans` (`pdf/WearStripLayout.kt`) walks the liner's clamped wear
  bands aft → fwd and builds the ordered chain: liner AFT edge → first band start, each
  band's own length, the gap between consecutive bands, and the trailing remainder to the
  liner FWD edge. Zero-length spans (a band starting exactly at the AFT edge, two
  back-to-back bands with no gap, a band ending exactly at the FWD edge) are omitted —
  the chain still covers `[0, linerLengthMm]` exactly, since an omitted span had zero mm
  to contribute. Bands that overlap each other (legal — only the liner-bounds check is
  enforced at entry, not inter-spot overlap) have their effective start pulled forward to
  the running cursor so the chain never runs backward or double-counts the overlap.
- `layoutWearStripRail` resolves that chain to on-page geometry: each label is centered on
  its own span when it fits with padding on both sides, else centered on the span's
  midpoint and allowed to overhang (never dropped); arrowheads point inward when there's
  room beside the label, outward when cramped (same test as
  `PdfDimensionRenderer.canFitInwardArrows`); and a label is bumped to the next stacked row
  when it would otherwise overlap an already-placed label — the crowding fallback for
  short bands/gaps whose label is wider than the span itself.
- **Drawing (2026-07-28)**: a label that fits inside its span (`arrowInward == true`, which
  also guarantees the break's stubs keep arrow room at `DIM_BREAK_TEXT_PAD_PT`) **seats in
  a break cut in the span line, vertically centred** — the schematic's value-in-a-break
  convention, consistent across drawing outputs. Only overhanging labels use the stacked
  below-line rows; break-seated labels can never collide since chained spans are disjoint.
  The wear/runout end-to-end OAL lines follow the same rule.
  `PdfDimensionRenderer` itself isn't reused directly: it's built around the schematic's
  multi-tier DATUM/LOCAL rail stacking (spans that overlap in x get assigned different
  rails), whereas a wear strip's rail is a single flat chain of never-overlapping spans —
  different enough on the tiering model that the minimal shared idea (label centering, arrow
  direction, collision-bump) is replicated as small pure functions in `WearStripLayout.kt`
  instead of bending that renderer's API to a shape it wasn't built for. (Both now draw the
  rail above the cylinder/outline.)
- The rail's own vertical budget is now FIXED — `WEAR_RAIL_MAX_LABEL_ROWS` (2) stacked
  label rows reserved between the rail line and the cylinder top (they stack downward from
  the rail line toward the cylinder), regardless of how many wear spots the liner
  has (the rail is always one chained line no matter how many spans it's divided into;
  the old per-spot row budget scaled with spot count, which no longer applies).
  `computeWearStripInnerLayout` no longer takes a `spotCount` parameter. `WearPdfComposer`'s
  `drawWearStripRail` draws the witness lines, arrowed spans, and labels, clamping any
  label row beyond what `computeWearStripInnerLayout` actually fit for this strip's height
  to the last available row rather than draw past the strip's bottom edge.

---

## PDF Appearance Options

Both composers accept:

```kotlin
fun composeRunoutPdf(
    page: PdfDocument.Page, spec: ShaftSpec, config: RunoutConfig,
    project: ProjectInfo, unit: UnitSystem,
    pdfPrefs: PdfPrefs = PdfPrefs(),
    resolvedComponents: List<ResolvedComponent>? = null,
    lineThicknessScale: Float = 1.0f,
)

fun composeWearPdf(
    page: PdfDocument.Page, spec: ShaftSpec,
    project: ProjectInfo, unit: UnitSystem,
    pdfPrefs: PdfPrefs = PdfPrefs(),
    resolvedComponents: List<ResolvedComponent>? = null,
    lineThicknessScale: Float = 1.0f,
)
```

`resolvedComponents` follows the `composeShaftPdf` contract: when provided, resolved
bodies replace `spec.bodies` (via `ShaftSpec.withResolvedBodies`) for the profile,
OD lookups, and runout stations. Both routes always pass `vm.resolvedComponents`.

| Parameter | Effect |
|---|---|
| `lineThicknessScale` (0.5–2.0) | Scales `strokeWidth` on all `OUTLINE_PT` and `DIM_PT` paints |
| `pdfPrefs.shadedBodies` | Draws a light-grey (`Color.argb(40,0,0,0)`) fill rect before each body outline |
| `pdfPrefs.shadedTapers` | Draws a light-grey trapezoid path before each taper outline |
| `pdfPrefs.shadedLiners` | Draws a light-grey fill rect before each liner outline |

Fills are drawn before outlines so the outline strokes are always visible on top.

---

## PdfPreviewOverlay

`PdfPreviewOverlay` is an in-place full-screen composable (not a nav destination) used by both RunoutRoute and WearRoute. It shares the file with RunoutRoute.

```
PdfPreviewOverlay(
    bitmap, loading, title, onClose, onExport,
    optionsSheet: (@Composable () -> Unit)? = null,
)
```

When `optionsSheet` is non-null, a **Tune** icon appears in the overlay toolbar. Tapping it opens a `ModalBottomSheet` (skips partial expansion) containing the composable.

**Stacking:** the zoom/pan `Box` is `clipToBounds()`, so the transformed page tucks **behind** the toolbar instead of sliding over it. A `graphicsLayer` scale/translate draws outside its layout node unless clipped, which let a zoomed-in page cover Close/Export (on-device report); hit testing was always bounded by layout, so this is a drawing fix, not a touch one. Also used by the undercut tab.

**Rotation:** the app is locked to portrait, but the runout/wear sheets are landscape, so — like the schematic `PdfPreviewScreen` — the overlay unlocks rotation while open (`DisposableEffect` sets `SCREEN_ORIENTATION_UNSPECIFIED`, restoring `SCREEN_ORIENTATION_PORTRAIT` on dismiss). Turning the device landscape then lets the letterboxed `ContentScale.Fit` preview fill the width.

Both routes pass `RunoutWearOptionsSheet` as the lambda:

| Control | Bound to |
|---|---|
| Line thickness (Slider 50–200%) | `vm.setLineThicknessScale()` |
| Shade Bodies (Checkbox) | `vm.setPdfShadedBodies()` |
| Shade Tapers (Checkbox) | `vm.setPdfShadedTapers()` |
| Shade Liners (Checkbox) | `vm.setPdfShadedLiners()` |

All four values are included in the `LaunchedEffect` key list so changing any option immediately re-renders the preview bitmap.

---

## Back-Press Handling

Both routes add `BackHandler(enabled = showPreview) { showPreview = false }` before the `if (showPreview)` block. This intercepts the system back gesture while the overlay is visible, dismissing the overlay instead of propagating to the NavController.

`LinerWearDetailOverlay` hosts its own unconditional `BackHandler` internally (rather than the caller adding a conditional one) since `WearRoute` only composes it while `selectedLinerId != null` — there is nothing to gate.

---

## Contracts & Invariants

- Model dimensions are canonical **mm**; all px/pt conversion happens inside the composer/preview.
- Thread components produce no runout stations; they are drawn as hatched envelopes for visual reference only and may extend past the OAL arrows (excluded threads sit outside the SET-to-SET span).
- The PDF page is U.S. Letter landscape (792 × 612 pt).
- Canvas preview and PDF share one placement engine (`geom/RunoutBubbleLayout.kt`) so they are identical by construction — never re-implement placement in a renderer.
- Bubbles never touch each other; leader lines never enter a bubble or cross another leader (engine-verified; see the algorithm section).
- Keyway reference cutout — an **open-topped keyway slot** at 12-o'clock (the top arc is broken across the slot mouth; two walls descend into the circle with a bottom connector), replacing the older protruding square notch. Nothing extends past the rim. Drawn identically in BOTH the PDF (`drawRunoutBubbleRingPdf`) and the canvas preview (`drawRunoutBubbleRing`).
- **Runout readings are reference-only** (like coupler bolt slots / wear spots): a per-station TIR value + high-spot marker that never affect OAL/`coverageEndMm`, body resolution, collision, or the Free-to-End badge. Both are optional and independent; a sheet exports fine with neither. See "Runout Bubble Editor".
- Any recorded value/marker is drawn identically in BOTH draw sites (the two must stay in lockstep — `RunoutRoute.drawRunoutMarkers` ⇔ `RunoutPdfComposer.drawPlacedBubbles`).
- OAL arrows bracket the SET-to-SET span, not the full `overallLengthMm`.
- The preview bitmap is rendered at 2× raster scale for sharpness on high-density displays.
- Temp PDF files used for preview rendering are deleted after rasterisation.

---

## Future Options

- Multiple orientation diagrams on one sheet (e.g., Looking AFT + Looking FWD side-by-side).
- Printable measurement table rows below each bubble.
- User-selectable keyway reference angle (the cutout is currently fixed at 12 o'clock; the high-spot
  marker is already fully user-placed).
- Severity rating / dye-pen pass-fail digitization and photos on wear spots (explicitly out of
  scope for the liner wear feature — see `docs/LinerWearAreas_Proposal.md` §1).
- Wear *bands* on bodies/tapers, not just liners (pit "X" markers already work on all three — see
  "Wear Pits" above; bands remain liner-only for now). Was the proposal's §10.5 open question.
