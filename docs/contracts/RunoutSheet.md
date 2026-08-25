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
- Let the user override the station count per component (bodies, tapers, liners), down
  to **0** — a component not being measured draws no bubbles on the canvas or either PDF
  (`collectRunoutStations` skips counts ≤ 0). Readings keyed to a zeroed component are
  kept, undrawn (the render-layer orphan rule), and reappear if the count is raised.
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

Both tabs share the same shell: outer `Column` with `systemBarsPadding()`, the shared
`EditorDocumentTitle` strip (`docs/contracts/Navigation.md`), a toolbar `Row` (hamburger + title +
Save), `HorizontalDivider`, then a vertically-scrollable inner `Column`.

**RunoutRoute additionally pins its preview.** The live canvas + its one-line tap hint sit
**outside** the scroll region, between the toolbar divider and a second `HorizontalDivider`;
the scrolling `Column` below takes the remainder via `Modifier.weight(1f)`. The bubble-count
editor's whole purpose is watching the profile change, so the preview has to stay on screen
while the stations are scrolled to (on-device request). Anything added to the pinned block
costs the scroll region height on a phone — keep it to the preview and the hint.

Order inside the scroll region is deliberate (on-device request):

1. **TIR orientation**
2. **Document export group** — blank-draft toggle, gate message, Preview / Print / Export.
   Producing a sheet is the routine path and must need no scrolling.
3. **Measurement station editor**, after a divider — reached only when the document needs
   adjusting, and the one section whose length grows with the shaft.

The pinned block, its padding, and its divider are all inside the same
`spec.overallLengthMm > 0f` guard, so an OAL-less spec leaves no orphan rule above the
controls.

### Wear tab body order

The Wear tab follows the **same convention** — output first, options after (on-device request):
options placed above the buttons push the thing the page exists for off the screen. Its scroll
region reads, top to bottom:

1. **Interactive shaft canvas** + the "Tap a body, taper, or liner…" hint. (Unlike RunoutRoute's,
   the wear canvas scrolls with the content — it is a tap surface for authoring, not a preview
   being watched while something else is adjusted.)
2. **Document output group** — the blank-draft switch, the gate message when the export gate is
   closed, then Preview / Print / Export. **Blank draft belongs to this group**, not to the
   options below it: it selects WHICH document the three buttons produce, rather than restyling
   one.
3. `HorizontalDivider`, then the **per-job customization rows** — trace depth
   (`WearTraceDepthControlRow`), the dye-pen PASS/FAIL chips, and the Components election
   (`WearStripComponentChecks`).

---

## Measurement stations (counts, fragments, identity)

**Counts are length-driven.** `geom/RunoutBubbleLayout.kt`'s `defaultStationCount(kind, lengthMm)`
gives one station per `RunoutConfig.RUNOUT_STATION_INTERVAL_MM` (**20 inches**):

| Kind | Default | Why |
|---|---|---|
| Body | `ceil(L / 20")`, min 1 | Length is the only thing that decides how many readings a uniform surface wants |
| Liner | `ceil(L / 20")`, min 2 | The edge-inset convention needs both ends; a long stern-tube liner earns more |
| Taper | 2, always | One inset from each of the S.E.T. and L.E.T. ends — the shop convention, not a density choice |

All capped at `RunoutConfig.MAX_STATIONS_PER_COMPONENT` (10); `componentOverrides` still wins
and may exceed the cap. This replaced a flat "3 per body whatever its length", which put three
readings on a 1–2" run and only three on a 100" line shaft (on-device report).

**A fragmented body is ONE component.** A body split by liners or tapers draws as several runs,
but the user sees one carousel name, one station-editor row, and one override — so
`collectRunoutStations` groups spans by id, derives the count **once** from the summed length,
and apportions it across the runs by length (`apportionStations`, largest-remainder). A run too
short to earn a station gets none. Deriving per run was the second half of the reported bug: a
three-run body drew 3 + 3 + 3 = 9 bubbles while its editor row read "3".

**`stationIndex` runs continuously AFT→FWD across a component's runs**, so a reading keyed
`(componentId, stationIndex)` identifies exactly one bubble. Restarting the index per run made
one key match several bubbles at once.

**Both draw sites build spans through `ui/resolved/RunoutSpans.kt`** (`runoutComponentSpans`),
keyed by the **base** body id. Do not rebuild spans from `spec.bodies` or from
`withResolvedBodies` output: the canvas keying by base id while the PDF kept the resolved
fragment id (`"<id>#2"`) meant that, on any body a liner had split, `overrides["X"]` missed and
`readings.find("X", 1)` missed — the count override and the hand-entered TIR value never
reached the paper, while both looked right on screen. Unfragmented bodies hide the defect
entirely (base id == fragment id).

**Legacy documents are frozen, not migrated.** Because a reading is keyed by station *index*
rather than by position, station 1 of 3 is not where station 1 of 5 is — changing a default
count slides measured values onto spots they were not measured at, or off the end.
`ShaftDocCodec.freezeLegacyStationCounts` therefore writes the pre-interval count (3 body /
2 taper / 2 liner) into `componentOverrides` for every component that already carries a reading
and has no override — visible and editable in the station editor, not hidden state. Documents
with no readings pick up the new defaults. Ids are classified without resolving: a taper or
liner id matches its list, anything else (stored or auto body) is a body.

**The freeze applies to legacy documents ONLY, gated on the `station_interval_version`
envelope stamp** (additive, default 0; `encodeV1` itself stamps
`CURRENT_STATION_INTERVAL_VERSION` on every write, so no caller can forget it).
A pre-interval file and one authored after look identical otherwise — neither carries an
override — so without the stamp the freeze pins NEW documents to the OLD defaults: a 100" body
drawn today gets 5 stations, and reopening would cut it to 3 and orphan the readings at 4 and
5. The stamp lives in the encoder, not in each writer, precisely so a future writer that
forgets it is impossible rather than merely discouraged.

**The station editor lives in one place.** `ui/screen/RunoutStationEditor.kt` provides
`buildRunoutStationEntries` + `RunoutStationCountEditor`, hosted by BOTH the Runout tab and the
Consolidated Output tab (whose sheet the bubbles actually print on — adjusting a count used to
mean leaving the tab). On the Output tab the rows sit behind a **collapsed-by-default**
"Runout bubbles" expander — they are an occasional tweak, and an open wall of rows buried the
tab's output actions (on-device report) — with the "Runout sheet →" button beside it for the
full authoring surface. The Output tab's order is: Sheet content election, then the output
group (blank-draft toggle + Preview / Print / Export — the actions the tab is for), then the
tuning sections (bubbles, sliders, worn sections), then Export all.

---

## Draggable stations (authored positions, 2026-08-16)

A station's position is normally **derived**. Press-and-hold a bubble on the Runout tab's live
canvas and it can be **dragged along its component** — to mark a spot that actually got
measured rather than the one the interval math picked (on-device request). A dragged position
is an authored value in the golden-rule sense: no derivation may move it again.

### Data — `model/RunoutStationPlacement.kt`

`RunoutStationPlacements` rides the envelope as `runout_stations` (additive + defaulted;
absent → every station derives as before). **Pure reference feature**, same posture as runout
readings: never touches `coverageEndMm`/OAL, body resolution, collision, or the Free-to-End
badge.

- **`axialMm` is component-local**, from the AFT edge of the component's aft-most run — the
  `WearPit.axialMm` convention. Not px, not drawn-x: the canvas maps mm linearly while the sheet
  maps them through the compressed profile, so a position stored in either output space would
  print somewhere else. A fragmented body measures local distance **across** its gaps, keeping
  one scalar addressing every run.
- **Keyed `(componentId, stationIndex)`**, and **never pruned at decode** — station identity
  needs resolved components plus count overrides the codec cannot see. Orphans go unread at the
  render layer, so lowering a count and raising it again restores the positions.
- **Undoable**: `runoutStationPlacements` is in `EditState` (a drag is direct manipulation, so
  a mis-drag should cost one undo) — and so is the **count-override slice** of `RunoutConfig`
  (`EditState.stationCountOverrides`): a +/− writes placements, readings, and count in one
  step, and restoring the first two without the third left a phantom derived bubble behind
  every undo of a "+". The rest of `RunoutConfig` (sliders, TIR direction, coupling face)
  stays out of the snapshot; its commits re-emit through the undo recorder but produce
  identical `EditState`s, which `SessionHistory.record` no-ops — the history cannot flood.

### A drag pins ONE station

Only the station under the finger is stored. Untouched siblings stay derived — they keep
tracking geometry edits, and a body's derived stations keep their **drawn-even placement over
the compressed sheet** (the on-device readability rule; freezing the whole component silently
traded it for physical-mm placement, bunching untouched ticks under foreshortened runs).
Derived positions never depend on pinned ones, so pinning one bubble moves nothing else.

Two consequences are engineered rather than hoped for:

- **Order repair** (`collectRunoutStations`): a pin holds physical mm while a derived body
  sibling holds drawn-even x, so under a compressed map — or after a geometry edit moves a
  derived sibling — the two can land out of index order. The **derived station yields**
  (clamped to the pin's drawn position; coincident ticks are legal, the bubble planner keeps
  the circles apart regardless); the pin never moves. The sheet always reads AFT→FWD.
- **The clamp fence** a drag works inside is the component's full current set
  (`currentStationPositions`, `RunoutRoute.kt` — stored pins verbatim, derived stations read
  off the drawn bubbles), but the fence is never committed; it exists only so the neighbour
  clamp has something to clamp against.

Count edits are the one action that stores the **whole** current set: `+`/`−` on a component
with any pin merge pins over derived spots (`currentLocalStationPositions` — pins verbatim,
never coerced), insert/remove, and freeze the result, so an index renumber never moves a
bubble the user can see.

### Escape hatches — Reset, Reset all, Undo move

Three ways back, scoped smallest to largest, all existing only while there is something to
undo (a fully derived document shows none of them — a permanent reset would imply hidden
position state on every document; pinned by `RunoutStationEditorTest`):

- **"Undo move"** (`runout_undo_move`) — a chip beside the canvas hint, appearing after a
  committed drag. One tap restores the moved station's pin as it stood **before** that drag;
  when the drag was that station's first, the pre-drag state is *derived*, so the undo un-pins
  it back to automatic placement rather than freezing it at its old derived spot
  (`LastBubbleMove.previousMm == null`). No other bubble is touched either way. Screen state,
  not persistence: replaced by the next drag, cleared when used or when a reset makes it moot
  (undoing a drag onto a component just returned to derived would silently re-pin it). The
  session Undo covers the same edit — this chip is the zero-thought path for "I nudged that by
  accident".
- **Per-row "Reset"** (`runout_reset_positions`) — on a pinned component's station-editor
  row; returns that one component to derived placement.
- **"Reset all bubble positions"** (`runout_reset_all_positions`) — below the station rows in
  the shared editor (both hosts), shown while ≥ 1 component is authored; returns the whole
  document to derived placement. Recoverable — placements are in `EditState`, so a session
  undo brings the dragged positions back.

### The clamp — order is the invariant

`clampDraggedStationMm` holds a drag inside its component and `RUNOUT_MIN_STATION_GAP_MM`
(0.5", scaled down on a short component by `effectiveStationGapMm`) clear of the stations either
side. **A station may never cross its neighbour.** Crossing would either renumber the stations
under their typed TIR values or print station 3 to the left of station 2 — a typed TIR is as
sacred as a typed diameter. The gap is not a collision guard (`planRunoutBubbles` already makes
bubble overlap geometrically impossible); it stops two stations landing on effectively the same
spot, where the sheet could not say which reading was taken where.

### Count changes on a pinned component

Fully derived components are untouched — "+"/"−" change the count and every position
re-derives, as before. A component with any pin works on its full merged set and keeps what
the user placed:

- **"+" inserts into the widest gap** (`planStationInsertion`), gaps running neighbour-to-
  neighbour plus each end of the usable band to its nearest station. Two default stations →
  the new one lands between them; one station → it takes the other two-station default
  position ("the normal location a second bubble would be added"); stations clustered at one
  end → it takes the empty end rather than squeezing into the cluster. The band is the
  edge-inset band for tapers/liners and the full span for bodies, matching
  `runoutStationPositionsMm`.
- Inserting **renumbers the stations above it, and the readings travel with them**
  (`RunoutReadings.withStationInserted`). A value belongs to the physical spot it was measured
  at, so leaving the keys alone would slide every reading forward of the insertion one station
  aft.
- **"−" removes the most redundant unmeasured station** (`authoredStationIndexToRemove`): first
  prefer a station carrying no reading, then among those take the one closest to the midpoint
  between its neighbours. That is the geometric inverse of the insertion rule, so **"−" undoes
  "+"** — add a station between two dragged bubbles, change your mind, and the pair come back
  untouched. `withStationRemoved` re-keys the readings above it.

### Gesture — `RunoutRoute.kt`

`detectDragGesturesAfterLongPress` in its own `pointerInput`, declared **after** the tap
detector so it takes the main pass first and its consumed changes keep `transformable` from
panning the canvas out from under the finger. Both handlers map touches through the one
`toPlanSpace` helper (invert the Canvas `graphicsLayer`), then `pickBubbleAt`, so hit-testing
and drawing cannot disagree about where a bubble is.

- **A long press and a tap share one press**, so `suppressBubbleTap` (set when the long press
  fires, cleared by the next `onPress`) stops the finger-up that ends a drag from also opening
  the reading dialog on the bubble just moved.
- **Commit on release** — the `PreviewTuning` doctrine. The in-progress set lives in
  `DraggingRunoutStation` and the canvas re-plans from it every frame; **no ViewModel write may
  happen on a drag frame**, or the unsaved-changes asterisk flips instantly and the undo history
  takes a step per coalescing window. A pickup that never moved commits nothing at all
  (`originalPositionsMm`).
- The bubble under the finger draws a heavier ring — transient screen affordance, no PDF
  counterpart, so the draw-both-sites rule is untouched.

### Both plans must be fed

`composeRunoutPdf` takes `runoutStationPlacements` and threads it into **both**
`collectRunoutStations` calls — the prelim linear-map plan that sizes the vertical budget as
well as the final plan on the compressed mapping. Feeding the prelim different stations than the
drawing uses reserves the wrong number of bubble rows.

The Output tab's preview rasterizes the real PDF, so it follows automatically; only the Runout
tab's native canvas hosts the gesture. Note that the canvas maps mm **linearly** while the sheet
compresses, so a bubble dragged to look centred in the preview will not look centred on paper in
a foreshortened region. The mm is the same either way — only the drawn x differs.

---

## Liner Wear Inspection (UI, Phase 2/3, 2026-07-18)

See `docs/archive/LinerWearAreas_Proposal.md` for the full feature scope; this section covers only
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
ratio; only the destination units and API differ, exactly like the runout marker). The detail
canvas's base half-arm was halved 2026-08-14 so LARGE lands where SMALL used to draw — an
OVERLAY-only correction (on-device clarification: the overlay drew its X's oversized; the
printed sizes below were confirmed right and are untouched):
- **Canvas detail:** `LinerWearDetail.kt`'s `drawPitX` (Compose `DrawScope`), base half-arm
  `PIT_SMALL_HALF_DP` (2.25dp; large 4.5dp).
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
`docs/archive/WearDiaMeasurements_PLAN.md` for the full design.

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
- **PDF detail strip** (`WearPdfComposer.drawWearStripWindow`): a reading prints on the strip of
  the component it belongs to, whenever that component has one — liners always, and (since
  2026-08-14) an elected taper or body too — witness tick across the full cylinder height (overshoot
  `WEAR_DIA_TICK_OVERSHOOT_PT`), value labels in a band reserved **below** the cylinder by
  `computeWearStripInnerLayout(diaBandPt = …)` (label rows only; the leader region reuses
  the existing label headroom, so a reading-free strip's layout is byte-identical to
  before — `WearStripDiaBandTest`). Labels use `formatDiaWithUnit` — the same ≤3-decimal
  format as the footer's Ø text, no `Ø` prefix (matches the hand sketch, keeps labels
  narrow). **The per-band min-Ø label is retired** (on-device report: it collided with
  these values under a wear band) — measured-Ø readings ARE the diameter story now; the
  spot card no longer offers the min-Ø field and `WearSpot.minDiaMm` survives only for
  old files (stored value passed through commits verbatim, never printed).
- **PDF main profile**: a body/taper reading whose component has **no strip on this page** prints
  under the whole-shaft profile, in a band
  below the names/direction row; the leader originates on the drawn bottom surface (taper Ø
  interpolated at the station, same as pits). The profile band reserves the height via
  `preferredProfileHeightPt` only when such readings exist. The strip is always the preferred
  surface — it is the zoomed one — so liner readings never draw on the profile, and a body/taper
  reading moves off the profile as soon as its component is elected onto a strip
  (`buildProfileDiaCalloutInput(skipComponentIds = …)`). A component past the strip cap
  loses its readings on print — same class of limitation as other strip-overflow content.
- **Canvas overlay**: same engine in px, anchored under the drawn segment.
- **Blank draft**: readings omitted entirely (`effectiveRecord = WearRecord()`), consistent
  with bands/pits.

### Worn-profile trace

Inside a **wear band**, the valued readings additionally pull the drawn surface in: the liner's
top and bottom edges dip through the measured diameters instead of running straight, and the
band's fill follows them, so the material measured away shows as white slivers above and below
(on-device report: a liner measured almost half an inch down still printed as a perfect
cylinder). Pure construction in **`geom/WearTraceMath.kt`** (`buildWearTrace` /
`smoothWearTrace` / `sequenceWearTraces`, `WearTraceMathTest`); the two draw sites — the **PDF
liner detail strip** (`WearPdfComposer.drawWearDetailStrip`) and the **canvas overlay**
(`ComponentWearDetailOverlay`, whose silhouette path carries the dip so fill, stroke and the
clipped red band tint all bite together) — walk that same output through their own scale, so
they render identically (the draw-both-sites rule, same posture as the pit "X").

The trace is **smoothed**: real wear flows rather than bevelling (on-device request), so
`smoothWearTrace` fits a **monotone cubic Hermite (Fritsch–Carlson)** curve through the vertices
in `(localMm, depthFrac)` space and hands back a *denser vertex run of the same type* —
`WEAR_TRACE_SMOOTH_SAMPLES` (16) samples between each pair — which both sites keep walking with
their existing straight `lineTo` loops. Sampled points, never Bézier control points: each site
maps a vertex through its **own** x function, and only sampled points survive that mapping. The
scheme is Fritsch–Carlson specifically because it **cannot overshoot** — every sample stays
inside the depth range of its own segment's endpoints, so a deep station beside a shallow one
never bulges outside the metal or digs deeper than measured (a Catmull-Rom spline would do
both). The **invariants are unchanged**: the original vertices are emitted verbatim, so at each
station the drawn depth is still exactly `max(exaggerated, true-scale)`; equal-x pairs (a reading
on a band edge) stay a verbatim vertical jump; flat runs — notably the zero-depth shaft between
two bands — stay dead flat; a run under three vertices passes through untouched. Both sites
smooth **per band**, before sequencing, so a band's grey fill and its surface edges walk exactly
the same vertices. The rendered curve has a same-math SVG preview in
`WearStripWindowSvgPreviewTest` (scenario **G**, `build/reports/wear-strip-preview/`).

Depth is **display-exaggerated**, the undercut notch's posture: it is normalized to the
record's deepest valued **liner** reading (`deepestWearDepthMm`, computed ONCE per sheet, so
every band scales against the same worst wear; body/taper readings never trace and stay out of
the baseline), which draws at the sheet's exaggeration cap — but **never shallower than true
scale**, so a monstrous wear past the cap keeps its true proportion. A band with no valued
reading keeps its straight edges and full-rect fill; readings at or above nominal contribute a
surface (0-depth) vertex. Draw-only: no model, resolve, OAL, collision, or codec change, and
printed Ø values stay the stored numbers.

The cap is **user-set**, `WEAR_TRACE_MIN_DEPTH_FRAC`..`WEAR_TRACE_MAX_DEPTH_FRAC`
(**5–25%** in 1% steps; 25% is both the hard high end and the shipped default — on-device
verdict "25% should be our high end"):

- **Per job** — `WearRecord.traceDepthFrac`, an additive optional field on the existing
  `wear_record` envelope (no codec plumbing; an older file decodes to `null`). `null` = *follow
  the global default*, so a job that never touched its slider tracks later changes to that
  default while a touched job stays pinned. Display dial, not a measurement: coercing it into
  range is correct (the `RunoutConfig.heightScale` posture), and it never moves a stored or
  printed Ø.
- **Global default** — `PdfPrefs.wearTraceDepthFrac`, Settings → Drawing → **"Wear depth
  exaggeration"** (full DataStore round-trip, coerced into range on read and write).
- **One resolution** — `effectiveWearTraceDepthFrac(recordFrac, globalFrac)` in
  `geom/WearTraceMath.kt`. Every consumer goes through it; no site re-derives. It reaches the
  PDF as `composeWearPdf(traceDepthFrac = …)` (threaded on to `drawWearDetailStrip` →
  `buildWearTrace(maxDepthFrac = …)`) and the canvas as
  `ComponentWearDetailOverlay(traceDepthFrac = …)`, both resolved at the same call site so the
  two draw sites can never disagree. It is also a **re-render key** on the wear preview: a job's
  own override rides `wearRecord`, but a change to the Settings default reaches an
  un-overridden document only through the key.
- **UI** — the "Trace depth exaggeration" row (shared `WearTraceDepthControlRow`, wrapping
  `WearTraceDepthSlider` with drag tracked locally and committed once on release) writes the
  per-job override; its **"Save as default"** button — enabled only while the effective value
  differs from the stored global — writes the current value to the global pref AND clears the
  override in the same action, so the job then follows the default it just created. ONE
  construction hosted on **two** surfaces: the Wear tab body and the wear preview's PDF options
  sheet, so the two can never drift.

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
  - *Pointer legibility rework (2026-08-16, on-device report: "hard to see where they
    land")*: three refinements, all in the shared engine so canvas and PDF stay
    identical. (1) The waterfill is **braked by station fidelity** — the spread level is
    the largest whose least-squares solve keeps every |bubbleX − stationX| within
    `spreadMaxOffset` (= 1 × sameRowPitch); an unbraked page-filling comb over clustered
    stations turned the pointers near-horizontal. A sheet whose geometric minimums
    already exceed the bound takes no widening at all. (2) **Straight leaders aim at the
    circle's center** and stop on the rim (the hand-sheet drafting convention) — the
    arrival direction alone identifies the landing circle. (3) **Split clearances**:
    straight leaders are verified against foreign circles at the wider visual clearance
    (`STRAIGHT_LEADER_CLEARANCE_RADIUS_FRAC` 0.35 × radius ≈ 8 pt on the PDF) and reroute
    to a dogleg when they'd graze; dogleg segments keep the geometric 0.5 × minGap —
    their diagonals legitimately skim the lane just above the row-0 tops, and testing
    them at the visual clearance would break the repair loop's convergence guarantee.
    (4) **The dogleg elbow dips for slope** (2026-08-25, same on-device report re-raised:
    the earlier three refinements fixed the *straight* leaders' aim but left the dogleg
    diagonals near-horizontal — 15.6°–17.7° measured on the engine's own preview cases).
    The elbow descends until the diagonal makes `LEADER_DOGLEG_MIN_SLOPE` (≈26.6°), bounded
    by the bubble's own top and by a per-leader circle-clearance search; the corridor stays
    the fallback the repair loop flattens to, which is what preserves convergence. Doglegs
    below 25° fall ~80% on realistic sheets at zero page cost.

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
  Values and rail LINES share ONE collision space (`geom/DimensionRailLayout.kt`, planned
  before drawing): a value too short to seat in its break floats into the *next* tier's
  band, so a colliding value slides horizontally along its own span first and bumps only
  when the slide has no room, and every rail above a floating value lifts by one label
  band. The lift is folded into `railsBlockH` from a prelim linear-map plan —
  inline-vs-floating depends only on drawn width, but the real compressed map needs the
  budget the lift feeds. See `docs/PDF_EXPORT.md` §5.4.
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
    Settings → Drawing → "Default drawing size" —
    `PdfPrefs.curveLoHeightIn`/`curveHiHeightIn`, an inverted pair flattens at the low
    anchor) and is never diluted by shaft length — EXCEPT when a pinned span (a
    **keyway-bearing body**, whose drawn slot geometry must stay real) needs the room:
    then the height yields instead (`solveMaxProfileScale` bisects the largest scale
    that still lays out; "doesn't have to be perfectly proportional, just close").
    **Tapers may shrink but never equalize**: no flat floor (a flat floor equalizes
    unequal tapers when both clamp to it — on-device report) — a ratio-preserving
    fraction-of-true floor instead (`PROFILE_TAPER_MIN_FRAC_OF_TRUE` 0.7, λ-fit, never
    lowers the height; relative taper widths always read true). The taper fraction is
    deliberately the pool's LARGEST — within the shared λ, width flows to spans in
    proportion to their fraction, so tapers out-prioritize body runs ("sacrifice a
    little more of the body compression to make the tapers more proportional" —
    on-device request, 2026-08-14; body runs fund it at 0.30 and their relative lengths
    still read because their fraction is ratio-preserving too). The x axis is
    otherwise schematic: spans may foreshorten but each kind keeps a writable minimum
    drawn width
    (`PROFILE_MIN_THREAD_PT` 36; `PROFILE_MIN_BODY_RUN_PT` 64 — room to write
    diameters and hang runout leaders, on-device request — and body runs additionally
    carry a ratio-preserving fraction-of-true floor in the SHARED λ pool,
    `PROFILE_BODY_RUN_MIN_FRAC_OF_TRUE` 0.30, the same mechanism tapers and liners use
    (on-device report: with proportional liners the body runs starved down to their
    flat floors — equalized slivers, "I can't tell that the span between the aft and
    mid liner is longer"), so gaps and raises shrink TOGETHER under one λ and relative
    body-run lengths always read; `PROFILE_MIN_LINER_PT` 100 —
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
    only BODY runs get the S-break pair, and only when squeezed below a **user-set
    fraction of their true drawn width** (`breakForCompression`, `pdf/BreakSymbol.kt` —
    one predicate for `drawBodiesForRunout`, the schematic's
    `drawBodiesCompressedCenterBreak`, and the schematic footer's compression note;
    milder foreshortening prints a plain outline — a break on a barely-squeezed run was
    noise, on-device report). The threshold is `PdfPrefs.sBreakThresholdFrac` —
    Settings → Drawing → "Body S-break" **and** the PDF Options sheet of this preview
    (see *PDF Options sheet* below), **default half**, 5% steps, **Never** (0) =
    compression breaks off entirely, 100% = break on any foreshortening ("why lock it in
    one way when different users may want different outputs", on-device request); the
    classic long-span trigger `COMPRESS_TRIGGER_PT` is independent of the slider and
    fires at every setting. The pair lays out via `breakPairLayout` — same file, unit-tested —
    which widens the classic gap up to half the run before flattening the glyph, so the
    two edges' curves always keep ≥ 1 pt of daylight and never overlap (on-device
    report); liners/tapers/threads foreshorten silently, like the hand sheets. Everything rides
    the one `xAt` — dimension rails, bubble stations, worn sections, wear marks. The
    bubble-row budget is solved on a prelim linear map (scale ↔ rows cycle), and the
    drawing plan re-solves on the real mapping. The SCHEMATIC composer uses the same
    scale + engine (`ShaftPdfComposer` — dims, callout leaders, keyways, and the
    compression footer note all ride the compressed `xAt`).
  - *Blended faces:* body blends and seal areas print on this sheet exactly as on the
    schematic — `drawBodiesForRunout` decomposes the same `bodyDrawEdges`
    (`ui/resolved/BodyBlends.kt`) as `ShaftPdfComposer` and the preview canvas, with the
    curves riding the compressed `xAt` (drawn width floored at `MIN_BLEND_WIDTH_PT`, the
    schematic's rule). The S-break pair is cut into the FLAT span so a curve is never
    broken, the break decision stays on the run's FULL drawn width, body shade fill
    follows the curves (it is drawn inside the body pass, not as a square pre-fill), and
    end caps stand at the neighbour's radius. Requires a resolve pass — without
    `resolvedComponents` the faces simply stay square, the schematic's fallback. The wear
    document deliberately keeps square faces (it omits machining detail, same posture as
    its keyway omission).
  - *Liner grey, conditionally:* liners follow the `shadedLiners` pref like bodies and
    tapers **unless the sheet prints Ø values inside the profile** — against a grey liner
    every sheet-white knockout reads as a pasted box (on-device request), so on such a
    sheet liners draw unfilled whatever the pref says. One predicate decides it,
    `consolidatedSheetHasInProfileValues` (`pdf/RunoutPdfComposer.kt`): wear info elected
    in, not a blank draft, and at least one worn-section value > 0 or one valued reading
    keyed to a component that still resolves. The composer builds its `linerFill` from it
    and the Output tab's options sheet locks the "Liners" checkbox with it
    (`RunoutWearOptionsSheet(linerShadeLocked)` — disabled and displayed unchecked, with
    the caption "Ø values print inside the profile on this sheet"; **display-only**, the
    stored pref is never rewritten). The classic runout sheet and the Runout tab's live
    canvas never carry in-profile text, so there the pref simply applies.
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

## Coupling Face (end view, 2026-08-15)

The shops hand-draw a coupling end view on the runout sheet — an outer circle at the coupling
OD, an inner circle at the pilot (register) bore with its keyseat, the **pilot runout** written
in the bore, and the note that it was taken *looking forward*. That sketch is now printed.

### Data posture — reference-only, no new plumbing

- **The pilot runout rides the existing readings list.** It is one value for the whole face, not
  a station on a component, so it is stored as an ordinary `RunoutReading` under the reserved
  `COUPLING_PILOT_COMPONENT_ID = "coupling_pilot"` (`model/RunoutReading.kt`) at
  `stationIndex = 0`. Value and high-spot marker are both optional, exactly like a bubble's, and
  the value is stored verbatim (golden rule).
  **The reserved id deliberately matches no resolved component**, which makes it look like an
  orphan to anything keyed on live stations. Nothing may prune it: runout readings are never
  pruned at decode (`ShaftDocCodec` passes them through untouched — verified, there is no
  component-keyed prune pass anywhere in the app), and the render layer only *skips* what it
  cannot place. Same posture as wear pits and dia readings.
- **Reference-only**, like every other mark on this sheet: the face never touches
  `coverageEndMm`/OAL, body resolution, collision, or the Free-to-End badge.
- **Visibility is a per-job election** — `RunoutConfig.showCouplingFace`, additive and defaulted
  **`false`** (on-device request: not every inspection measures the coupling, and a document
  written before the field existed reprints byte-identically until the face is elected).
  No codec change. ViewModel setter `setShowCouplingFace`, the `setTirDirection` pattern.
- The face is **runout content**: `drawFace = config.showCouplingFace && drawBubbles`, so a
  Schematic + Wear consolidated sheet carries no face, exactly as it carries no TIR line.

### Geometry — `geom/CouplingFaceMath.kt`

Pure Kotlin (no Android imports), so the composer and `CouplingFaceMathTest` read one set of
numbers. `couplingFaceLayout(outerR, boltCount)` resolves everything from the drawn OD radius:

| Feature | Ratio |
|---|---|
| Pilot bore radius | `COUPLING_PILOT_FRAC = 0.44` × outer R |
| Keyseat half-width | `COUPLING_KEYWAY_SLOT_HALF_FRAC = 0.22` × pilot R |
| Keyseat outward depth | `COUPLING_KEYWAY_DEPTH_FRAC = 0.25` × pilot R |
| Bolt circle radius | `(outerR + pilotR) / 2` |
| Bolt hole radius | `COUPLING_BOLT_HOLE_FRAC = 0.10` × outer R |
| Bolt angles | `-90° + (360°/count) · (i + 0.5)` |

**The coupling keyseat protrudes OUTWARD from the pilot bore** — the key sits between shaft and
hub, so the shaft carries a keyway cut *into* it while the coupling carries a keyseat cut into
the surrounding hub material. It is drawn as an arc gap at 12 o'clock with two walls running
outward to `pilotR + keywayDepth` and a flat cap: a small box standing on the bore, open into
it. This is **deliberately opposite the runout bubble glyph's inward slot** (which is the shaft
convention and stays unchanged). Both share the arc-gap technique; neither may be unified onto
the other.

Bolts are rotated a **half pitch** off 12 o'clock so no hole ever lands behind the keyseat, and
the keyseat cap always stops short of the bolt holes
(`pilotR + depth < boltCircleR − boltHoleR` — pinned by test). `boltCount < 1` draws no bolt
circle at all: the plain two-circle face, which is the hand-sketch minimum.

**Bolt count source:** `spec.couplerBoltSlots.firstOrNull()?.count ?: 0` — a coupler bolt slot's
count *is* the coupling's bolt count, so no new field is authored for it.

### Drawing — PDF-only

ONE implementation, `drawCouplingFace` in `pdf/RunoutPdfComposer.kt`, serving both the classic
and consolidated sheets. **PDF-only by design** — the same posture as the schematic's diameter
callouts: both in-app previews rasterize the real PDF, and the Runout tab's pinned canvas stays
lean (runouts only). There is no canvas twin, so no draw-both-sites rule applies here.

- Outer circle in outline paint, no fill.
- Bolt circle as a **dashed** thin construction line on a **local copy** of the dim paint (the
  shared paint is never mutated), carrying solid-stroke holes.
- Pilot bore + outward keyseat as above.
- Value centred in the bore at `pilotR × 0.60` — the bubble's text ratio — through the shared
  `util/RunoutValueFormat.formatRunoutValue`. High spot as the same short red dash straddling
  the rim via `clockTickRimOffset`, here on the **pilot** rim.
- Caption `"Coupling — looking fwd"` centred under the circle at 8 pt.
- **Blank drafts** draw all the geometry and omit the value and marker — a write-in circle,
  exactly the rule the bubbles follow.

### Placement + vertical budget

The face sits bottom-right, sharing the band above the footer with the TIR line. Everything
above therefore reserves the **taller of the two lanes**:

```
bottomLaneH   = max(drawBubbles ? TIR_LINE_HEIGHT_PT : 0, drawFace ? COUPLING_FACE_BLOCK_PT : 0)
bottomLaneTopY = footerTop − bottomLaneH
availableH     = bottomLaneTopY − shaftTopBudgetY      // prelim AND final pass, one value
```

`COUPLING_FACE_OUTER_R_PT = 36` (1 in dia on paper), `COUPLING_FACE_BLOCK_PT = 96`
(2·R + caption lane + pads), `COUPLING_FACE_PAD_PT = 8`. Reserving off `tirY` alone would let the
shaft and its bubbles run down through the face. The TIR line itself keeps its own y
(`footerTop − TIR_LINE_HEIGHT_PT`); its write-in rule is **clamped** to stop short of the face's
block rather than running under it (`drawTirLine`'s `right` parameter). The face draws after the
profile, marks, bubbles, and TIR line — it owns a reserved block, so the consolidated sheet's
"marks first, text last" in-profile ordering is untouched.

### Authoring UI — three surfaces, ONE field

The election is a checkbox in both **PDF options sheets** — the Runout tab preview's and the
Output tab's consolidated one (`RunoutWearOptionsSheet(showCouplingFaceRow, couplingFaceOn)`,
gated off for the wear/undercut documents where it would be inert) — and again on the Runout
tab body, where it pairs with a **"Pilot runout…"** button (enabled only when the face is on).
All three bind `vm.setShowCouplingFace`, so they cannot drift. It is a checkbox commit, not a
drag, so there is no live-tuning channel: `runoutConfig` is already a re-render key in both
previews' render-inputs records, which is what refreshes the page.

The button opens the **existing** `RunoutBubbleDialog` titled "Coupling pilot", seeded from
`runoutReadings.find(COUPLING_PILOT_COMPONENT_ID, 0)` and saving through the ordinary
`vm.setRunoutReading` — the face's value and a station's value are authored identically.

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

**Even-spread waterfill, braked by station fidelity (engine rule 7).** The minimum pitches
are collision floors, not a layout goal. When the page has slack, every adjacent gap floor
rises toward one common level (`Σ max(gap, L) = available`, capped at
`RunoutBubbleGeometry.spreadPitch` = 1.5 × sameRowPitch) so the bubbles use the width and a
machinist can write beside a circle without the pen crossing a neighbour's leader — but the
spread is **braked**: the level taken is the largest whose least-squares solve keeps every
`|bubbleX − stationX|` within `RunoutBubbleGeometry.spreadMaxOffset` (= 1 × sameRowPitch).
An unbraked page-filling comb over clustered stations turned the pointers near-horizontal
(on-device report, 2026-08-16). A sheet whose geometric floors alone already exceed the
bound takes no widening at all; a page with no slack keeps the exact minimum-pitch layout.
Floors only ever grow, so no collision guarantee changes.

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
Bubble x positions minimise Σ(bubbleX − stationX)² subject to the (waterfilled) pitch
floors and page bounds (isotonic regression / pool-adjacent-violators). Bubbles sit
**directly under their stations** whenever there is room; dense clusters spread
symmetrically and stay centred over their stations. Bubble order always equals station
order.

### Leaders — verified, with dogleg fallback
Each leader is first tried as a straight segment from `(stationX, shaftSurfaceY)` **aimed
at the circle's center and clipped at the rim** (engine rule 5, the hand-sheet drafting
convention — the arrival direction alone identifies the landing circle; a station so close
the segment degenerates keeps the plain top-center attach). The engine then runs an
explicit collision check — segment-vs-circle against every other bubble and
segment-vs-segment against every other leader. A **straight** leader is verified at the
wider visual clearance (`STRAIGHT_LEADER_CLEARANCE_RADIUS_FRAC` = 0.35 × radius, ≈ 8 pt on
the PDF): one that would merely graze a foreign circle reads as entering it, so it fails
and reroutes. **Dogleg** segments keep the geometric `minGap/2` — their diagonals
legitimately skim the corridor 0.75·minGap above the row-0 circle tops, and testing them
at the visual clearance would flag every dogleg and break the convergence argument below.
Any leader that fails is re-routed as a **dogleg**:

```
(stationX, surfaceY)          vertical stub down to the common departure line
   → (stationX, departY)      (departY = deepest shaft surface; zero-length when already there)
   → (bubbleX, elbowY)        diagonal — dips below the row-0 corridor for slope (see below)
   → (bubbleX, bubbleTop)     vertical drop through the rows (clears circles by crossRowPitch)
```

**The elbow dips for slope (engine rule 5).** The diagonal carries all of the leader's
sideways travel. Pinned at the corridor 0.75·minGap above the row-0 tops it has a constant
~14 pt of vertical budget on the PDF against a horizontal run of up to a same-row pitch, so
its slope degenerates precisely where doglegs are common: it leaves the shaft nearly tangent
to the profile line and points at nothing (on-device report, "not clear to where
they are pointing"). `elbowY` therefore descends until the diagonal makes
`LEADER_DOGLEG_MIN_SLOPE` (0.5 rise/run, ≈26.6°) over its own run, capped at the bubble's own
top so the closing segment stays a drop, and capped again by a search that only accepts
a depth whose diagonal clears every foreign circle by `minGap/2`. The dip costs **no page
height** — it stays inside the band the rows already occupy, so `sectionHeight` is unchanged.
The slope is a **target, not an invariant**: on an overloaded sheet a neighbouring circle
blocks the dip and the leader finishes on the corridor, as before.

**Convergence.** At *corridor* level all dogleg diagonals run between the same two horizontal
lines with matching left-to-right order at both ends, so corridor-vs-corridor crossings are
geometrically impossible. A dipped diagonal leaves that common line and so may cross a
neighbour; the repair's answer is to flatten it back to the corridor. Each leader therefore
moves through at most two states — straight → dipped dogleg → corridor dogleg — and the loop
provably converges to **zero intersections** in every non-compressed configuration. The unit
suite asserts this across randomized stress configurations, stepped shaft surfaces (OD jumps),
and dense component boundaries.

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
(`PdfDimensionRenderer.drawPlanned`), so all drawing outputs read the same; a span too short
to seat the label with arrow room falls back to a continuous line with the label above (it
keeps its inward arrows — direction follows the span's width, not the label's placement).
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
- **Dye pen PASS/FAIL is selectable in-app** (`WearRecord.dyePenResult` — additive, defaulted,
  no version bump; Wear tab "Dye pen inspection:" Pass/Fail chips, tap the selected chip to
  deselect). A selection draws an "X" inside its checkbox (`drawWearNotesArea`); the other box
  stays present and blank so the form always reads as the same two-box row. No selection — and
  every blank write-in draft, via `effectiveRecord` — keeps both boxes blank for hand-marking,
  the original posture. Reference-only: no geometry effect anywhere.

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

The **shaft profile is drawn on top** of the wear document (2026-07-21, user request):
body/taper pit "X"s live on the whole-shaft profile, so it stays visible by default. Since
2026-08-14 it is elective — `WearRecord.showShaftProfile = false` drops the whole band (profile,
OAL rail, on-profile bands/pits, liner names, direction reference) and hands its height to the
detail strips, with no phantom gap left where it was; the header, strips, and dye-pen row are
unaffected. The
detail strips below pick their layout from `determineWearPdfMode(collectWearStripWindows(
components, wearRecord.stripComponentIds).size)` — a pure function of the
**elected strip-window count** (an elected taper joins its nearest elected liner in one combined
window; see "Strip windows" below), which by default is every liner with positive length and OD, whether or
not it has recorded wear (the shop's normal operating procedure — the sheet always shows all
liners; a spotless liner's strip simply has no bands/callouts and its rail shows edge witness bars
only — a band-less rail draws no spanning length). Orphan spots on a since-deleted liner are dropped by `collectWearLinerGroups`; pits
don't affect the mode:

| Liners | Mode | Page shows |
|---|---|---|
| 0 | `PROFILE_FORM` | Shaft profile only (still prints any recorded pits). |
| 1 | `COMBINED` | Shaft profile + wear bands, with one full-width detail strip below. |
| 2+ | `GRID` | Shaft profile on top + the detail strips **packed into rows by their actual drawn width** below (`packWearStripWindows`) — fewest rows by default, a deeper row count when it buys a ≥ `WEAR_PACK_ROW_SCALE_GAIN` larger shared scale, as many strips per row as the page's width allows (up to `WEAR_STRIP_MAX_PER_ROW` = 3). Row BUDGET: **2 with the profile shown, 3 with it hidden** (`wearStripMaxRows`). Whatever doesn't fit is a "+N more" overflow note. |

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

**`GRID` layout — dynamic row packing** (2026-08-15, on-device request; `packWearStripWindows`,
`pdf/WearStripLayout.kt`, pure/unit-tested). The fixed 2-column grid gave every strip half the page
whatever it drew, so two short components hogged a row a third could have shared, and a FWD taper
fell into the "+N more" note even on a sheet with the profile hidden. The packer instead fills each
row by the windows' REAL drawn widths:

The rule, in order: **the row count whose scale earns it, then the largest scale within it, then
re-expand whitespace.**

- **Row budget** — `wearStripMaxRows(showShaftProfile)`: **2 rows with the profile shown, 3 with it
  hidden** (`WEAR_STRIP_MAX_ROWS_WITH_PROFILE` / `WEAR_STRIP_MAX_ROWS_NO_PROFILE`). The page holds
  three bands of content either way; with the shaft on it, one of them IS the shaft, so hiding it
  hands the strips that band as a third row. ONE rule — the composer never re-derives it inline.
- **Fewest rows by default, more when the scale earns them.** The baseline is the fewest rows any
  scale can reach (the greedy packing at the **scale floor**, where every footprint is smallest) —
  but merely FITTING a row count is not a reason to stay there: three liners that fit one row were
  forced into it at a fraction of the size the page could print, cramped over a half-empty band
  (on-device report). Every deeper count up to the budget is auditioned, and a deeper one wins
  when its solved shared scale beats the current choice by **`WEAR_PACK_ROW_SCALE_GAIN` (1.25×)**
  — an extra row must buy a meaningfully bigger drawing. The guard is what keeps the common
  short-strip sheet honest the other way: a pair already at (or near) `WEAR_STRIP_MAX_PT_PER_MM`
  sharing a row stays side by side, tall, because stacking it would buy almost nothing (the
  regression the old strict fewest-rows rule was written against).
- **Rows are filled by width**, greedy first-fit over the windows **in order** (AFT→FWD is never
  rearranged to fit more; first-fit is optimal in row count for a fixed order, which is what makes
  the scale solve's monotonicity argument hold), capped at `WEAR_STRIP_MAX_PER_ROW` = 3 — past
  three side by side a strip's rail values and title have no room left to read.
- **Whitespace before drawn size.** A window's footprint is its drawn run plus a neighbor stub on
  each side, and rows are separated by a gutter. The scale is solved at **tight** spacing
  (`WEAR_STRIP_STUB_MIN_PT` 20 pt / `WEAR_STRIP_COL_GAP_MIN_PT` 16 pt — the most permissive test):
  binary search the largest `ptPerMm` in `[WEAR_STRIP_MIN_PT_PER_MM, WEAR_STRIP_MAX_PT_PER_MM]` at
  which the windows still pack into **exactly the row count chosen above** AND no row overruns the
  content width. A page that fits at the cap shrinks not at all. Then, with the scale and row
  assignment fixed, a second binary search re-expands the spacing toward full
  (`WEAR_STRIP_STUB_WIDTH_PT` 34 / `WEAR_STRIP_COL_GAP_PT` 22) as far as the rows allow. So an
  election spends its whitespace before any component draws smaller, and one that would have
  overflowed instead fits by drawing smaller.
- **Overflow** — when even the scale floor can't pack the election into the row budget, the longest
  prefix that fits is settled there (the most permissive packing, so it is the longest prefix any
  scale reaches) and the scale/whitespace passes then run on that prefix alone, so the surviving
  strips draw as large as the page allows rather than staying pinned at the floor. The tail goes to
  the "+N more" note.
- **Row height on the packed path follows the profile toggle.** With the profile shown the fixed
  cap holds (`WEAR_STRIP_HEIGHT_MAX_PT`) and the profile band absorbs the slack as before, its
  shaft slack-centered inside it. With the profile hidden the rows OWN the band: a multi-row page
  splits the whole height between its rows — capping them too stranded the bottom half of the page
  as dead white under two top-pinned rows (on-device report). Only a LONE row keeps a guard, at
  the height a two-row page would give it (`max(WEAR_STRIP_HEIGHT_MAX_PT, (band − gap) / 2)`), so
  it cannot stretch into a short fat cylinder; its leftover height goes to the page bottom (the
  rows pin to the top of the content band, so the spare white sits above the notes as ordinary
  bottom margin rather than a hole under the header). The single-column `COMBINED` path keeps its
  own posture (the cap lifts when the profile is hidden, so the lone full-width strip fills the
  page).
- **Spacing is uniform page-wide** — one stub width and one gutter for the whole sheet; a page whose
  strips had different stub widths would read as a mistake. The composer threads the packed stub
  into `drawWearStripWindow(stubWidthPt = …)`, so every end style (S-break edge, thread hatch, flat
  cap) measures off the packed value and can never overrun the cell it was sized for.
- **Facing S-break curls never cross a gutter.** A BREAK end's glyph sits on the cell edge and its
  return sweep bulges outward by `BREAK_EDGE_OUTWARD_REACH_FRAC` (= fullness · √3/6 ≈ 0.43) of the
  amplitude — at the radii a tall page draws, two facing curls interweave across the packed gutter
  (on-device report: every strip-to-strip gutter on an exported sheet read as one woven knot).
  After packing, `spreadWearStripRowGutters` widens exactly the gutters whose facing ends are
  BREAKs to `reach_L + reach_R + stroke + WEAR_STRIP_GUTTER_DAYLIGHT_PT` (radius bound: the
  tallest cylinder a strip that row height can draw), funded by the slack a centered row parks at
  its margins — footprints and the shared scale never move, the row re-centers. A row without the
  slack widens proportionally, and the backstop `wearStripBreakAmplitudePt` clamps each curl's
  amplitude to its share of the gutter (the same degrade-not-overlap posture as `breakPairLayout`)
  — so the curls flatten rather than ever cross. Gutter widths may therefore differ between pairs;
  only the stub width stays uniform. The two
  floors are set by the break glyph, not by taste: the S-break sits at the stub's outer end and
  reaches ~0.17 × the stub radius back inward (so 20 pt still leaves a clear run of shaft to the
  component's edge cap) and ~0.26 × outward across the cell edge — two neighbours both bulge, which
  is what the 16 pt gutter floor has to host.
- **The ONE shared scale invariant is unchanged.** The packer divides the page's WIDTH only; it
  never gives a window its own scale, so relative component lengths still read true (a 22" liner
  draws half the width of a 44" one). On a `GRID` page `WearStripPacking.ptPerMm` replaces the
  `sharedWearStripWindowPtPerMm` solve; the `COMBINED` single full-width strip and the undercut
  sheet still call that function.
- **Cells** — each window's cell is exactly its own footprint wide, laid left to right a gutter
  apart, with the whole row **centered** in the content width (the fixed grid's "a partial row is
  centered" convention, now applied to every row). Leftover slack sits at the page margins —
  except what the post-packing `spreadWearStripRowGutters` pass spends widening the gutters whose
  facing ends draw S-break curls (see the facing-curls bullet below). Vertically the packed ROW
  count feeds `computeWearVerticalLayout` — the same split `computeWearStripGridLayout` makes
  internally — so the profile still never shrinks below its minimum and the "nothing wasted /
  nothing overflows" guarantee carries over unchanged.
- Degenerate inputs (no windows, non-positive content width, `maxRows ≤ 0`) return an empty packing
  rather than throwing.

**The undercut sheet keeps the fixed 2-column grid.** `computeWearStripGridLayout`,
`WEAR_STRIP_GRID_COLUMNS`, `WEAR_STRIP_GRID_MAX_PER_PAGE` and `selectWearStripWindowsForPage` are
unchanged in behavior and signature because `pdf/UndercutStripLayout.kt` /
`UndercutPdfComposer.kt` still use them; the packer is purely additive and only the WEAR composer's
`GRID` branch calls it.

Everything inside a strip — horizontal cylinder/stub layout, dimension rail, measured-Ø callouts,
anchor-from-SET title, pit "X"s — is the same `drawWearStripWindow` the single-column path uses;
only the per-strip rectangle and the page's stub width differ.

---

### Wear Detail Strips (Phase 4, 2026-07-18; 2-column grid 2026-07-21; dynamic row packing 2026-08-15)

`composeWearPdf` takes an optional `wearRecord: WearRecord = WearRecord()` param (see
`docs/archive/LinerWearAreas_Proposal.md` §6.2). Every existing call site is unaffected by the
default. All strip geometry (liner spans, neighbor diameters for the break-out stubs)
comes from `docSpec` — the spec after `withResolvedBodies(resolvedComponents)` — never
raw `spec.bodies`, same contract as the rest of this document. This section describes the
strip content itself, shared by the `COMBINED` (single full-width) and `GRID` (packed rows)
layouts (see "Wear PDF Rendering Modes" above for how each positions the strips).

**Selection & pagination** — `pdf/WearStripLayout.kt` (android-free, unit-tested directly,
`WearStripLayoutTest`):
- `collectWearLinerGroups` builds one group per **elected drawable liner** (positive length +
  OD), attaching whatever spots `wearRecord` holds against it — including none (2026-07-27:
  every liner gets a strip regardless of recorded wear), sorted aft → fwd. Orphaned spots (stale
  `linerId`) are dropped defensively (the authoritative drop is at decode time,
  `ShaftDocCodec`).
- The election is `WearRecord.stripComponentIds` (2026-08-14, additive/defaulted — no envelope
  version bump): `null` means the default election, every drawable liner (`defaultWearStripComponentIds`),
  which is exactly the historical sheet; a non-null list is the machinist's authored set of
  **resolved component ids** (liners, tapers, bodies — explicit or auto), and an empty list
  prints no strips. Ids that no
  longer resolve are skipped at the **render layer**, never pruned at decode — the pit/Ø-reading
  rule. Both the election and `WearRecord.showShaftProfile` are read from the passed record even
  in blank-draft mode: a write-in sheet blanks values, never the drawing's shape.
- **Two hosts, one state** (2026-08-15): the "Components" section (`WearStripComponentChecks`)
  renders BOTH on the **Wear tab body** — last of the customization rows, below the output block
  (see "Wear tab body order") — and in the **preview's PDF options sheet**, from the one
  composable with the same
  `WearRecord.stripComponentIds` / `WearRecord.showShaftProfile` bindings and the same
  `vm.setWearStripComponents` / `vm.setWearShowShaftProfile` setters, so the two surfaces can
  never disagree. Electing components is authoring work, not print-time styling, so reaching it
  must not require opening the preview (on-device request). Both can be composed at once — the
  tab body stays in the tree behind the preview overlay — so the composable takes a
  `testTagPrefix` (`wear_strip` on the sheet, `wear_tab_strip` on the tab body); a shared literal
  tag would resolve to two nodes. The per-checkbox rule lives in the pure
  `toggleWearStripSelection` (`WearRoute.kt`, `WearStripSelectionTest`), not in either surface's
  lambda.
- **Quick actions** (2026-08-15) sit under the checkbox list: **Default (all liners)** →
  `onSetSelection(null)`, **All** → every currently offered id in sheet order, **None** → the
  empty list (test tags `<prefix>_default` / `<prefix>_all` / `<prefix>_none`). Default is
  NOT the same as hand-ticking every liner: clearing back to `null` makes the sheet *follow the
  shaft*, so a liner added afterwards gets a strip on its own, whereas an authored list is a
  fixed set and later components arrive unticked. That is the recovery path for a document whose
  shaft was built out after the election was authored (save-as, template, a late liner add) —
  the rows are rebuilt from the live shaft on every composition, so a late component always
  *appears*, it just arrives unticked.
- Pagination depends on the mode. A `GRID` page paginates off the **packer**: `placedCount` says
  how many windows fit in the row budget, and the tail overflows (see "dynamic row packing" below).
  The single-column paths still use `selectWearStripWindowsForPage`, which caps at
  `WEAR_STRIP_MAX_PER_PAGE` (3). Strips beyond either limit are
  **not** put on a second PDF page — `composeWearPdf` only ever receives a single
  caller-supplied `PdfDocument.Page` (every call site does one `startPage` /
  `finishPage`), and growing that into true multi-page output would mean changing the
  function's signature and every call site. Instead, overflow renders as one text note
  line ("+N more liner(s)/component(s): ...") in a reserved band just above the notes area.
  Revisit if/when `composeWearPdf` grows multi-page support.

**Strip windows — taper/body strips and combined taper+liner strips (2026-08-14)**

A strip is a **window** onto the shaft (`WearStripWindow`, `pdf/WearStripLayout.kt`): an ordered
run of `WearStripComponentSeg` (a component's own span — liner, taper, or body) and
`WearStripGapSeg` (the shaft between two components in the same window). A window with a single
component is exactly the historical per-liner strip, which is the compatibility guarantee: a
liner-only election groups and lays out bit-for-bit as `collectWearLinerGroups` +
`computeWearStripHorizontalLayout` always did (pinned by `WearStripWindowTest` and
`WearStripWindowSvgPreviewTest`).

- **Grouping** (`collectWearStripWindows`): each elected **taper** attaches to the NEAREST
  elected liner — smallest gap, ties go AFT (the more aftward liner wins) — forming one combined
  window, **but only when that gap is within the join threshold** (`trueGapMaxMm`, below): the
  contest picks the nearest liner on true mm, and the threshold then says whether the winner's
  claim stands. At most one taper joins a liner from each side; a taper that loses the contest, a
  taper **farther from its nearest liner than the threshold**, a taper elected with no liners on
  the sheet, and every elected **body** get their own single-component window. Bodies never join a
  liner: they are the shaft's fluid base, so a body strip is its own run. Windows come out
  AFT→FWD, and the window count feeds `determineWearPdfMode` / grid selection exactly as the liner
  count used to.
  A far pair used to share a window regardless (2026-08-15 on-device report): the window's total
  width pushed the liner off-centre in its cell, the taper crowded it, and the S-break sat almost
  against the taper's large end. Two separate strips read correctly — each component centres in
  its own cell, on the page's one shared scale, and each end stub stands a full stub width clear.
- **Gap mode** is decided from **mm alone** (`wearStripGapDrawsTrue`), so the shared-scale solve
  stays non-circular: `gapMm ≤ trueGapMaxMm` draws TRUE — the real
  shaft outline under the gap, sampled off `docSpec` by `wearStripGapProfile`/`outerDiaMmAt` with
  a vertex pair either side of every component edge so a step in Ø draws as a step — and anything
  longer compresses to a fixed `WEAR_STRIP_BREAK_GAP_PT` (40 pt) run marked by the S-break pair
  (`drawBreakEdge`, same glyph and eye convention as the window's own neighbor stubs). Touching
  components get no gap segment at all. Each break stands off its component by a
  `WEAR_STRIP_BREAK_LEAD_PT` (10 pt, < half the gap run) **lead-in of the gap's TRUE outline** —
  `outerDiaMmAt` half a millimetre inside the gap, falling back to the adjacent component's edge
  Ø only where nothing resolves. Jumping the outline straight to the neighbour's edge Ø made the
  component itself look shifted, and a break drawn hard against that edge left no connecting
  shaft at all (on-device report).
- **Window ends** are NOT a blanket S-break: `wearStripEndStyle(spec, edgeMm, aftSide)` looks at
  every component span extending past the edge — none → `FLAT`, all threads → `THREAD_END`,
  otherwise → `BREAK`. The break claims "the shaft continues past here", so a `THREAD_END` draws
  the whole remaining threaded shaft (flat outer edge + diagonal hatch at a fixed pitch, sized by
  `wearStripEndThreadDiaMm`) and a `FLAT` end draws no stub at all — just its own edge cap at full
  `outline` weight, the thin per-segment caps being component boundaries rather than shaft ends.
  This is the wear detail overlay's `leftIsEndThread`/`rightIsEndThread` convention
  (`ui/screen/LinerWearDetail.kt`), applied to the printed strip.
- **The join threshold is user-set** (2026-08-15, on-device answer: "make it a slider, up to a
  foot"). `PdfPrefs.wearJoinGapMaxMm`, **canonical mm**, `PDF_WEAR_JOIN_GAP_MIN_MM` 0 ..
  `PDF_WEAR_JOIN_GAP_MAX_MM` 304.8 (12"), default `PDF_WEAR_JOIN_GAP_DEFAULT_MM` = 76.2 (3") —
  which `WEAR_STRIP_TRUE_GAP_MAX_MM` reads as the pure API's default parameter, so there is ONE
  number behind the pref and the layout code. `composeWearPdf` hands it to
  `collectWearStripWindows(components, ids, trueGapMaxMm)`. **0 breaks on any positive gap**
  (touching components still draw contiguous — they produce no gap segment at all); 12" draws a
  foot of shaft true between a taper and its liner. It decides **attachment outright** (see
  "Grouping"): a taper past it is a separate strip, so under the current builder every gap inside
  a window draws TRUE and a compressed gap is never emitted. The compressed-gap segment mode, its
  break/lead-in draw path, and the per-cluster title split are all KEPT — `WearStripWindow` is a
  general model of a strip window, and a window constructed with a compressed gap still draws and
  titles correctly (pinned on directly-constructed windows in `WearStripEndsAndClustersTest`).
  App-wide with no per-job override (the body S-break posture), full DataStore round-trip, and a
  **re-render key** on the wear preview because the composer reads it off the `PdfPrefs` snapshot,
  which is not snapshot state. Two hosts, one `WearJoinGapSlider`: Settings → Drawing →
  "Taper–liner join" (with a Default reset and a caption) and the wear preview's PDF options
  sheet. The slider is the app's first **length-valued** drawing pref, so it converts at the UI
  edge only — inches snap to 1/2" (`LengthFormat.formatInchesSmart`, so 12.7 mm reads `1/2"`),
  millimetres to 10 mm; the stored value is always mm.
- **One mapping per window**: `WearStripWindow.xAt(mm, leftPt, ptPerMm)` — true-scale segments at
  the sheet's shared scale, a compressed gap mapped linearly across its fixed width. Monotone,
  exact at every segment boundary, extrapolating at the sheet scale outside the window (the
  stubs' own space). Everything in the strip draws through it — the same one-piecewise-mapping
  posture as `geom/ProfileCompression.kt`.
- **Drawn content** — a liner segment keeps everything it has always had (wear bands, worn-profile
  trace, chained spots rail, pits, Ø callouts, end caps, title + anchor label); a taper segment
  draws its trapezoid from the resolved start/end Ø, a body segment its rectangle. Every segment's
  radius scales against the window's largest Ø — and ACROSS strips, heights are page-proportional
  (`wearStripHeightFrac`): the window with the page's largest reference Ø fills its band and every
  other strip draws at its true diameter ratio to it, the vertical analogue of the one shared
  mm→pt width scale (on-device report: a body strip drew at the same height as a liner almost an
  inch larger in OD; the earlier every-strip-fills-its-band rule is superseded). The rail's
  witness bars run down to the liner's ACTUAL drawn top, so a height-scaled strip's bars never
  stop in the air above its surface. A combined window keeps the taper's true Ø ratio to the
  liner as before. The chained rail measures WEAR, so it belongs to the window's liner; a
  taper/body-only window has none. Blank drafts follow the same lines-in/values-out rule: every
  cluster that prints an anchor takes the same write-in title — name, a writing rule where the
  anchor value goes, then `WEAR_BLANK_ANCHOR_SUFFIX` — because a body strip's anchor is measured
  the same way a liner's is (see "Titles" below for the clusters that print none).
- **Titles are per attachment CLUSTER**, not one joined title per window (2026-08-15, on-device
  request). `wearStripClusters(window)` splits the component run at every **compressed** gap —
  components joined by true-scale gaps, or touching, stay in one cluster — because a break means
  the two sides are NOT adjacent and one joined `"A + B — dist FROM SET"` title would misread as a
  single continuous area. Under the membership rule above every built window is a SINGLE cluster
  (its gaps are all true-scale), so in practice each strip prints one title; the split machinery
  stays and governs any window that does carry a compressed gap. Each cluster names its own
  components AFT→FWD, joined with " + ".
  `wearStripClusterShowsAnchor(cluster)` then decides the anchor-from-SET dimension: a cluster
  holding a **taper** prints none — the strip's own dimension rail is the measuring surface and a
  taper at the shaft end is self-evidently placed — while a lone liner or lone body run keeps it:
  `"<Liner> — 110 FROM AFT S.E.T."`, `"<Body #2> — 42 FROM FWD S.E.T."`. Placement: a window whose
  ONE cluster carries an anchor keeps the historical form (left at `contentLeft`, or right-aligned
  when measured from the FWD SET); every other label **centers under its own cluster's drawn
  span**, processed left→right and pushed 8 pt clear of its predecessor before being clamped /
  ellipsized against `contentRight`. All labels share the one title baseline. Blank drafts cluster
  identically — an anchored cluster keeps the write-in construction (name, a writing rule,
  `WEAR_BLANK_ANCHOR_SUFFIX`), a cluster with no anchor prints names only, since a location that
  needs no measurement needs no blank. The anchor value itself is one rule for both:
  `wearStripAnchorForSpan` / `buildSpanAnchorLabel` (`pdf/WearStripLayout.kt`) take a shaft-space
  span + the measurement-space `SetPositions` and apply `mapToLinerDimsForPdf`'s comparison —
  edges rebased through `computeOalWindow`, AFT SET → span start vs. span end → FWD SET, the
  **nearer edge wins with ties going AFT**. `buildLinerAnchorLabel` is that helper over the
  liner's own span, so the liner path prints exactly what it always did (pinned in
  `WearStripLayoutTest`), and `linerAnchorForPdf` — the FWD right-align cue — reads the same rule,
  so the wording and the alignment can never disagree. `pdf/WearStripComponents.kt` owns the
  shared `wearStripComponentsFor` / `buildWearStripTitleById` pair, which BOTH the printed titles
  and the options sheet's checkboxes read, so a checkbox and the strip it elects always read the
  same.
- **Pits and Ø readings** follow their component: a body/taper reading whose component has a strip
  on this page prints IN that strip (at the zoomed scale, same helpers as a liner —
  `pitCenterY`/`pitHalfArm`, `planDiaCallouts`, taper radius interpolated), and only a reading
  whose component has no strip keeps the under-the-main-profile placement
  (`buildProfileDiaCalloutInput(skipComponentIds = …)`). Profile pits keep drawing on the profile
  regardless.
- **Shared scale** — `sharedWearStripWindowPtPerMm` takes each window's fixed break-gap points off
  its cell first (they are spent whatever the scale is), then solves `sharedWearStripPtPerMm` over
  what's left. For liner-only pages it is identical to solving on the raw liner lengths. A `GRID`
  page takes its scale from `packWearStripWindows` instead (which solves it against the whole page
  width, whitespace squeezed first); `COMBINED` and the undercut strips still call this function.
  Either way there is exactly ONE mm→pt scale on the sheet.

**Main profile** — liners with ≥1 wear spot get thin **vertical-line** bands
(`drawWearBandsOnProfile` → `drawVerticalBand`) at their true axial position, clamped to the
liner span (`clampWearBandToLiner`), drawn after the profile's own liner outlines. Visible but
not dominant — same alpha/weight/pitch as the old diagonal hatch, only the stroke orientation
changed (2026-07-22, to match how the shop marks wear areas by hand — the vertical tick style
in the reference sketch). The broken-out detail strips fill their bands a **light grey**
instead (a black FILL paint whose alpha is `wearBandShadeAlpha(pdfPrefs.wearBandShadeFrac)`,
shipping as the same wash as `shadeFill`): they are the surface pits get marked into, by the
printed "X"s and by the machinist's pen on the printed sheet, and the diagonal hatch they used
to carry buried both (2026-08-14 on-device report; `drawHatchBand` retired).

That grey is **user-set** — `PdfPrefs.wearBandShadeFrac`, a fraction of full black, default
`PDF_WEAR_BAND_SHADE_DEFAULT` = 40/255 (the historical fixed alpha, so an untouched install
draws exactly the shipped look), range `PDF_WEAR_BAND_SHADE_MIN` 5% ..
`PDF_WEAR_BAND_SHADE_MAX` 35%, 1% steps. **The cap is the point of the range**: past it a
heavier wash starts burying the pit marks in the band — printed and hand-drawn alike — which is
exactly what retired the diagonal hatch. App-wide with no per-job override (unlike the trace
depth), full DataStore round-trip, and a **re-render key** on the wear preview because the
composer reads it off the `PdfPrefs` snapshot, which is not snapshot state. Two hosts, one
`WearBandShadeSlider`: Settings → Drawing → "Wear area shade" (with a Default reset) and the
wear preview's PDF options sheet. The MAIN profile's vertical-stroke bands are a different mark
and are not styled by it. Each on-page liner's **name** is also printed centered under its span
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

**Per-strip layout** — `computeWearStripHorizontalLayout` centers a break-out liner between
two fixed-width neighbor stubs. Its scale is **one shared mm→pt value for the whole sheet**
(2026-08-14, on-device report: each strip scaled to fill its own cell, so a liner less than
half the length of its siblings printed just as wide): `sharedWearStripPtPerMm` takes the
largest scale that still fits every strip inside its own cell — `min(innerWidth_i / length_i)`,
capped at `WEAR_STRIP_MAX_PT_PER_MM` and deliberately **not** floored to
`WEAR_STRIP_MIN_PT_PER_MM`, since flooring a shared scale would overflow the longest strip's
cell. One long component therefore shrinks the whole page together — proportion wins — and a
shorter liner centers in its cell's slack. The composer passes it as
`ptPerMmOverride`; `null` (the undercut document's strips, which reuse this same function)
keeps the legacy per-strip fit, capped/floored so very short/long spans don't explode/vanish.
The wear composer itself now goes through `computeWearStripWindowLayout`, the multi-component
form of the same arithmetic (a window's drawn run centered between the two stubs) — geometrically
identical for a single-component window; see "Strip windows" below.
`computeWearStripInnerLayout`
then splits the strip's own vertical band into the chained rail's fallback label rows and its
rail line (top), the liner cylinder, and the title row (bottom) (see "Dimension rail" below) —
the cylinder shrinks first, and if a pathological input leaves no room at all, the rail's label
rows drop toward zero (the rail line still draws; labels are simply not placed) rather than
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
- Light-grey wear bands on the liner at strip-local scale (a solid wash, unlike the
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
  midpoint and allowed to overhang (never dropped); the value **seats in a break**
  (`seatsInBreak`) when there's arrow room beside it (same test as
  `DimensionRailLayout.canFitInwardArrows`); and a label is bumped to the next stacked row
  when it would otherwise overlap an already-placed label — the crowding fallback for
  short bands/gaps whose label is wider than the span itself.
- **Arrow direction is a separate flag** (`arrowInward`, from
  `DimensionRailLayout.arrowsPointInward`): heads turn outward only on a span too narrow to
  hold both between its witness lines, never merely because the label overhung and fell to a
  row. The two were one flag until an overhanging label on a roomy span cost it its inward
  heads (see `docs/PDF_EXPORT.md` §5.4). `planUndercutRailRows` reserves its fallback rows
  off `seatsInBreak` — the arrows say nothing about rows.
- **Drawing (2026-07-28)**: a label that fits inside its span (`seatsInBreak == true`, which
  also guarantees the break's stubs keep arrow room at `DIM_BREAK_TEXT_PAD_PT`) **seats in
  a break cut in the span line, vertically centred** — the schematic's value-in-a-break
  convention, consistent across drawing outputs. Only overhanging labels use the stacked
  ABOVE-line rows; break-seated labels can never collide since chained spans are disjoint.
  The wear/runout end-to-end OAL lines follow the same rule.
  `PdfDimensionRenderer` itself isn't reused directly: it's built around the schematic's
  multi-tier DATUM/LOCAL rail stacking (spans that overlap in x get assigned different
  rails), whereas a wear strip's rail is a single flat chain of never-overlapping spans —
  different enough on the tiering model that the minimal shared idea (label centering, arrow
  direction, collision-bump) is replicated as small pure functions in `WearStripLayout.kt`
  instead of bending that renderer's API to a shape it wasn't built for. (Both now draw the
  rail above the cylinder/outline.)
- The rail's own vertical budget is FIXED — `WEAR_RAIL_MAX_LABEL_ROWS` (2) stacked
  label rows, regardless of how many wear spots the liner has (the rail is always one chained
  line no matter how many spans it's divided into; the old per-spot row budget scaled with
  spot count, which no longer applies). `computeWearStripInnerLayout` no longer takes a
  `spotCount` parameter. `WearPdfComposer`'s `drawWearStripRail` draws the witness lines,
  arrowed spans, and labels, clamping any label row beyond what `computeWearStripInnerLayout`
  actually fit for this strip's height to the last available row rather than draw past the
  strip's edges.
- **Fallback rows sit ABOVE the rail line (2026-08-14).** The rail line drops to one
  `WEAR_RAIL_WITNESS_RUN_PT` (9 pt) above the cylinder — that run carries the witness lines
  and nothing else — and the row budget moves to `[stripTop, railY]`, row 0 nearest the rail
  and stacking upward (`drawWearStripRail` baselines at
  `railY − labelGapPt − fm.descent − row × rowStepPt`, `labelGapPt` = witness overshoot + 1 so
  a value never prints over a witness line). Rows used to stack *downward* into that same
  witness run, so a label too wide for its span — a short end span, say — printed across the
  witness lines (on-device report on a printed sheet). Above is also where the schematic's
  dimension rails put a value that cannot seat in its line, so the two surfaces now agree.
  `computeWearStripInnerLayout`'s guarantees widen to
  `stripTop ≤ railY − railLabelRows × rowHeightPt` and `railY ≤ cylTop ≤ cylBottom ≤ stripBottom`;
  the cylinder still shrinks first and the rows still drop toward zero rather than overflow.
  The undercut strips pass `witnessRunPt = 0` (`computeUndercutStripInnerLayout`) — they place
  both of their own rail lines off `cylTop` and already reserve a full label row of clear air
  there — so their geometry is untouched by this change.

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
| `pdfPrefs.shadedLiners` | Draws a light-grey fill rect before each liner outline — suppressed on a sheet that prints Ø values inside the profile (`consolidatedSheetHasInProfileValues`) |

Fills are drawn before outlines so the outline strokes are always visible on top.

---

## PdfPreviewOverlay

`PdfPreviewOverlay` is an in-place full-screen composable (not a nav destination) used by both RunoutRoute and WearRoute. It shares the file with RunoutRoute.

```
PdfPreviewOverlay(
    bitmap, loading, title, onClose, onExport,
    optionsSheet: (@Composable () -> Unit)? = null,
    sheetTunesPage: Boolean = false,
    inkBand: InkBand? = null,
)
```

When `optionsSheet` is non-null, a **Tune** icon appears in the overlay toolbar. Tapping it opens a `ModalBottomSheet` (skips partial expansion) containing the composable, height-capped by the overlay itself. `sheetTunesPage` says that sheet reshapes THIS page (Runout, Consolidated Output, and **Wear** pass `true`; Undercut takes the `false` default): it switches the open preview to the page-strip layout and takes the sheet's scrim off — see **Live tuning** below. `inkBand` is where that page carries ink (`util/PdfInkBounds.kt`), supplied by the routes whose sheets tune; the strip crops to it.

**Stacking:** the zoom/pan `Box` is `clipToBounds()`, so the transformed page tucks **behind** the toolbar instead of sliding over it. A `graphicsLayer` scale/translate draws outside its layout node unless clipped, which let a zoomed-in page cover Close/Export (on-device report); hit testing was always bounded by layout, so this is a drawing fix, not a touch one. Also used by the undercut tab.

**Rotation:** the app is locked to portrait, but the runout/wear sheets are landscape, so — like the schematic `PdfPreviewScreen` — the overlay unlocks rotation while open (`DisposableEffect` sets `SCREEN_ORIENTATION_UNSPECIFIED`, restoring `SCREEN_ORIENTATION_PORTRAIT` on dismiss). Turning the device landscape then lets the letterboxed `ContentScale.Fit` preview fill the width.

All four routes (Runout, Wear, Undercut, Consolidated Output) pass `RunoutWearOptionsSheet` as the lambda:

| Control | Bound to |
|---|---|
| Blank draft (write-in) (Switch) | the hosting tab's session-only blank-draft state, via `onSetBlankDraft` — row renders only when that callback is non-null (Consolidated Output, Wear, Undercut) |
| Line thickness (Slider 50–200%) | `vm.setLineThicknessScale()` |
| Trace depth exaggeration (Slider + "Save as default") | `vm.setWearTraceDepthFrac()` / `vm.setPdfWearTraceDepthFrac()` — only when `showWearControls` (Wear only) |
| Wear area shade (Slider 5–35%, 1% steps) | `vm.setPdfWearBandShadeFrac()` — only when `showWearControls` (Wear only) |
| Components (Checkboxes + Default / All / None) | `vm.setWearShowShaftProfile()` / `vm.setWearStripComponents()` — only when `showWearControls` (Wear only); the selection callback is nullable, and `null` is the "Default (all liners)" action. The **Wear tab body** hosts the same section, same bindings (see "Selection & pagination") |
| Taper–liner join (Slider 0–12", 1/2" steps; 10 mm in metric) | `vm.setPdfWearJoinGapMaxMm()` — only when `showWearControls` (Wear only); canonical mm, displayed in the session's unit |
| Body S-break (Slider Never–Always, 5% steps) | `vm.setPdfSBreakThresholdFrac()` — only when `showSBreak` (see below) |
| Shaft height (Slider) | `vm.setRunoutHeightScale()` — only when `showHeightControls` (Consolidated Output **and Runout**) |
| Liner compression (Checkbox + Slider) | `vm.setLinersProportional()` / `vm.setLinerCompression()` — only when `showHeightControls` (Consolidated Output **and Runout**) |
| Measurement reference (Radio: Auto / AFT / FWD) | `vm.setPdfTieringMode()` — only when `showMeasurementReference` (Consolidated Output only) |
| Shade Bodies (Checkbox) | `vm.setPdfShadedBodies()` |
| Shade Tapers (Checkbox) | `vm.setPdfShadedTapers()` |
| Shade Liners (Checkbox) | `vm.setPdfShadedLiners()` — locked (disabled, shown unchecked) when the document prints Ø values inside the profile; display-only, the pref is never rewritten |

The Consolidated Output **and Runout** instances turn on the Shaft height / Liner compression
pair — one composer serves both sheets and reads `config.heightScale` /
`config.linerMinFracOfTrue` whether or not it is drawing the consolidated variant, so hiding
the pair on the Runout sheet left the classic sheet's drawn height governed from a different
tab. Only Consolidated Output adds the Measurement reference radios — the same set the
schematic Tune sheet exposes
(`PdfPreviewScreen.kt`'s `PdfOptionsSheet`), minus Component labels and the blank Ø-callouts
sub-toggle: the consolidated composer never reads either of those two prefs, so they would be
inert controls on this sheet. Only the **Wear** instance turns on `showWearControls`, the block
that tunes the wear strips; the other three documents draw no wear strips.

The **blank-draft row** is on for every instance — Consolidated Output, Wear, Undercut, and
Runout all have a write-in mode. Wear and Undercut pass the SAME state their tab-body switch
owns (`blankDraft` + `onSetBlankDraft = { blankDraft = it }`), so the two surfaces always agree
and either one re-renders the preview through the route's existing `blankDraft` render key.

So, top to bottom: the **Wear** sheet shows Blank draft → Line thickness → Components (complete
shaft + per-component checkboxes + the Default/All/None quick actions) → Trace depth
exaggeration → Wear area shade → Taper–liner join → Fractions → Shade in PDF → Dual units +
layout; the **Undercut** sheet shows Blank draft → Line thickness → Fractions → Shade in PDF →
Dual units + layout; the **Runout** sheet Blank draft → Coupling face → Line thickness →
Body S-break → Shaft height → Liner compression → Fractions → Shade in PDF → Dual units +
layout. Two ordering rules, both on-device requests: the live-tuning sliders LEAD (matching
the schematic Tune sheet's group), and the dual-units pair sits LAST on every sheet —
drawing- and output-specific controls first, rarely used options at the foot. The wear sliders are commit-on-release like every other
slider here, so the Wear preview is **not** a live-drag surface (`tuning` stays null): the
release commit re-renders the whole page through the route's render keys. It does pass
`sheetTunesPage = true` all the same — a commit that redraws the page is worthless if the sheet
covers it (the wear sheet hid the preview outright, on-device report) — so the Wear route
measures the ink band on every render pass (there are no draft frames to skip here) and hands it
to the overlay.

All of these values are included in the render loop's `RenderInputs` holder so changing any option immediately re-renders the preview bitmap.

**Live tuning (Runout + Consolidated Output).** Both routes pass a `PreviewTuning` (`ui/screen/PreviewTuning.kt`) into the sheet, so the sliders here — Line thickness and Body S-break on both routes, plus Shaft height and Liner compression on the Consolidated Output AND Runout sheets — reshape the page **while the finger is still on the track** ("see the differences without choosing, closing menu, opening menu, choosing" — on-device request). The shared controls report their in-progress value through an optional `onDrag: (Float?) -> Unit` (same units as their commit callback, `null` on release); the route folds it into the render inputs as `override ?: committed`. Three rules hold:

- **Visual only.** A drag frame never writes DataStore and never updates `RunoutConfig` — persistence and the per-job dirty mark stay on commit-on-release. The Wear and Undercut routes leave `tuning` at its `null` default and are unaffected.
- **Draft then sharp.** The loop is `snapshotFlow { RenderInputs(…) }.conflate().collect { … }` — latest-wins, so intermediate drag values are dropped while a render is in flight — and drag frames raster at `renderScale = 1` (¼ the pixels). When the drag ends the overrides go null, the inputs change once more, and that pass restores `PDF_PREVIEW_RENDER_SCALE`. The spinner is held back across drag frames and that release pass so the page never strobes.
- **The page keeps a strip on top.** Both routes pass `sheetTunesPage = true` to `PdfPreviewOverlay`, because live rendering is worthless if the menu covers the page ("It may render live but the menu with the sliders is in the way. I can see the PDF Preview area lighten up on moving a slider but I can't see anything. I need to close the menu to see the changes." — on-device report). While such a sheet is open the overlay drops the centered full-size `Image` for a **page strip** pinned `TopCenter` under the toolbar (a `Canvas` with the same modifier chain plus an explicit `contentDescription`, drawing through the shared `drawPageBand`): the sheets are LANDSCAPE, so a whole page needs only `screenWidthDp × (PDF_PAGE_HEIGHT_PT / PDF_PAGE_WIDTH_PT)` of height (`fitWidthPageHeightDp`, `ui/screen/PreviewTuning.kt` — the real page constants, never a magic ratio). The strip actually shows the page's **ink band** — `util/PdfInkBounds.kt` measures the rendered bitmap's first/last inked rows, the routes hold it in a `previewInkBand` state updated on **sharp passes only** (a drag frame must not resize the strip under the finger) and pass it as `PdfPreviewOverlay(inkBand = …)`, so the blank top margin stops eating the strip while inked content — rail, footer — is never cropped. Opening the sheet **resets zoom/pan** to fit; predictable over preserved, since an inspection zoom would push the strip off-screen exactly when the sliders need it. Closing restores the normal layout (fill, pinch 0.5×–8×).
- **Sheet capped below the strip — by the overlay.** `PdfPreviewOverlay` wraps `optionsSheet()` in a `Box(heightIn(max = …))`: `tuningSheetMaxHeightDp(screenHeight, strip, sheetChrome)` = screen height − strip − `PREVIEW_TOP_CHROME_DP` (88 dp of status bar + toolbar) − the sheet's own chrome, clamped to `[TUNING_SHEET_MIN_FRAC 40%, PREVIEW_SHEET_MAX_FRAC 78%]` of the screen. **Sheet chrome** is `TUNING_SHEET_CHROME_DP` (48 dp: M3's drag handle) plus the measured `WindowInsets.navigationBars` bottom — both stack OUTSIDE a content-column cap, so ignoring them left the sheet overlapping the strip and swallowing the drawing's lowest callouts and footer (on-device report). On a 393 × 851 dp phone with a 48 dp nav bar: a 303.7 dp strip over a 363.3 dp sheet. The clamp order is **sheet floor first, strip takes the remainder** (`tuningPageStripHeightDp`, computed first so the cap derives from it) — on a short/wide screen the page fits to the shrunken strip, because it is zoomable once the sheet closes and crushed sliders are not usable at all. The sheet already scrolls internally, so the cap never loses content. `RunoutWearOptionsSheet` itself carries **no** cap: only the overlay knows the strip. The **Wear** preview carries no LIVE-tuning channel — its own controls commit on release and re-render the whole page — but it takes `sheetTunesPage = true` anyway, because the page it redraws has to stay visible while the sheet that redrew it is open. Only the **Undercut** preview keeps `sheetTunesPage = false` and the plain `PREVIEW_SHEET_MAX_FRAC` (78%) cap with the full-size centered preview: its sheet changes nothing about the page's shape.
- **Undimmed scrim.** `ModalBottomSheet`'s scrim is one **full-window** rect — it covers the page strip too and cannot be restricted to the gap below it — so a tuning sheet passes `Color.Transparent` for the whole time it is open, not just during a drag. The overlay's black surround already reads as separation between strip and sheet. (The schematic's `PdfPreviewScreen`, which has no black surround, paints the strip-to-sheet gap itself and drops that during a drag.) Tap-outside-to-dismiss is unaffected: the transparent scrim still takes the tap.

The option blocks are shared composables in `ui/screen/ShaftHeightSlider.kt` — `LineThicknessSlider`, `SBreakThresholdSlider`, `WearTraceDepthControlRow`, `WearBandShadeSlider`, `WearJoinGapSlider`, and `ShadeInPdfChecks` (heading + the three checkboxes + the `linerShadeLocked` behavior) — used by this sheet and by the schematic preview's `PdfOptionsSheet`, so an added option lands on both surfaces at once. The wear controls have further hosts: `WearTraceDepthControlRow` is also the Wear tab body's row, and `WearBandShadeSlider` / `WearJoinGapSlider` also sit in Settings → Drawing (wrapped there with a Default reset and a caption), so no surface can drift from another. Settings → PDF Export keeps its own copy of the checkbox rows: they sit in a `spacedBy(12.dp)` column with a padded heading, and adopting the sheets' tighter block would restyle that page. Same prefs, same setters. `SBreakThresholdSlider` is additionally the Settings → Drawing control (that page adds only its explanatory caption), so the threshold reads and writes the one app-wide pref from every surface.

**`showSBreak` is asymmetric across the four callers.** The Runout and Consolidated Output routes pass `showSBreak = true` with their collected `pdfSBreakThresholdFrac`; the Wear and Undercut routes leave it at its `false` default, because those documents never draw compression breaks and the control would be inert noise there. The schematic's `PdfOptionsSheet` shows it unconditionally.

The Consolidated Output tab passes `linerShadeLocked = consolidatedSheetHasInProfileValues(…)` computed from the same inputs its composer call gets (wear record, resolved component ids, the elected variant's `includeWearInfo`, the blank-draft flag), so the checkbox can never show a shade the sheet won't draw. The other three callers (Runout, Wear, Undercut) leave the parameter at its `false` default.

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
- Bubbles never touch each other; leader lines never enter a bubble or cross another leader
  (engine-verified; see the algorithm section). Every bubble is **joined** to its station by a
  leader that starts at the station's x on the shaft surface and ends on the bubble's rim —
  nothing suppresses or shortens a leader because the bubble happens to sit near its station.
- Keyway reference cutout — an **open-topped keyway slot** at 12-o'clock (the top arc is broken across the slot mouth; two walls descend into the circle with a bottom connector), replacing the older protruding square notch. Nothing extends past the rim. Drawn identically in BOTH the PDF (`drawRunoutBubbleRingPdf`) and the canvas preview (`drawRunoutBubbleRing`).
- **Runout readings are reference-only** (like coupler bolt slots / wear spots): a per-station TIR value + high-spot marker that never affect OAL/`coverageEndMm`, body resolution, collision, or the Free-to-End badge. Both are optional and independent; a sheet exports fine with neither. See "Runout Bubble Editor".
- Any recorded value/marker is drawn identically in BOTH draw sites (the two must stay in lockstep — `RunoutRoute.drawRunoutMarkers` ⇔ `RunoutPdfComposer.drawPlacedBubbles`).
- OAL arrows bracket the SET-to-SET span, not the full `overallLengthMm`.
- Every tab's preview bitmap comes from the ONE shared raster helper
  `util/PdfRaster.renderPdfPageBitmap` (`composePage` lambda in, bitmap out) at
  `PDF_PREVIEW_RENDER_SCALE` (2×) for sharpness on high-density displays — its
  `renderScale` parameter defaults to it, and only a live tuning drag passes 1 for its
  draft frames. Its temp PDF is deleted after rasterisation, and any failure returns null
  so the tab shows an error instead of crashing.

---

## Future Options

- Multiple orientation diagrams on one sheet (e.g., Looking AFT + Looking FWD side-by-side).
- Printable measurement table rows below each bubble.
- User-selectable keyway reference angle (the cutout is currently fixed at 12 o'clock; the high-spot
  marker is already fully user-placed).
- Severity rating and photos on wear spots (explicitly out of scope for the liner wear
  feature — see `docs/archive/LinerWearAreas_Proposal.md` §1). The sheet-level dye-pen PASS/FAIL is
  digitized (see "Key layout decisions" above); a per-spot rating is not.
- Wear *bands* on bodies/tapers, not just liners (pit "X" markers already work on all three — see
  "Wear Pits" above; bands remain liner-only for now). Was the proposal's §10.5 open question.
