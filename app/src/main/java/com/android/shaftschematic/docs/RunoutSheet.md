# RunoutSheet & WearDocument

**Files:**
- `geom/RunoutBubbleLayout.kt` — shared bubble placement engine (stations, rows, x-solve, leader routing + collision verification)
- `ui/screen/RunoutRoute.kt` — runout station config, canvas preview, PDF preview overlay
- `ui/screen/WearRoute.kt` — wear inspection document tab; interactive shaft canvas + liner
  wear tap/badges (Phase 2, see "Liner Wear Inspection (UI)" below)
- `ui/screen/LinerWearDetail.kt` — full-screen liner wear detail overlay (Phase 3)
- `ui/screen/LinerWearMath.kt` — pure math for tap hit-testing, band clamping, and detail-canvas
  scale, shared by the two files above
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
- Export a hand-fill-in PDF runout sheet via SAF.
- Preview the PDF in-app via `PdfPreviewOverlay` with a Tune options sheet.

### WearRoute
- Render a live, tappable shaft canvas (liners only) so the user can inspect/record wear —
  see "Liner Wear Inspection (UI)" below.
- Preview the wear document PDF in-app via `PdfPreviewOverlay` with a Tune options sheet.
- Export a blank shaft outline PDF via SAF for field damage and dye-pen inspection marking.

Both tabs share the same layout pattern: outer `Column` with `systemBarsPadding()`, a toolbar `Row` (hamburger + title), `HorizontalDivider`, then a vertically-scrollable inner `Column`.

---

## Liner Wear Inspection (UI, Phase 2/3, 2026-07-18)

See `docs/LinerWearAreas_Proposal.md` for the full feature scope; this section covers only
the UI contract added on top of the existing wear-document tab.

**Overview canvas (`WearRoute.kt`)** — same rendering pattern as `RunoutRoute`'s preview
canvas (`ShaftLayout.compute` + `ShaftRenderer.draw` against `resolvedComponents`, never raw
spec), but deliberately **no pinch-to-zoom** — this canvas only needs a single tap gesture, so
skipping the transformable/zoom state kept the tap-coordinate math simple (no scale/offset to
divide out). After `ShaftRenderer.draw`, `drawLinerWearAffordances` adds a faint primary-tint
fill + border over every liner (tap affordance) and a small count badge above any liner that
already has ≥1 recorded wear spot.

**Tap hit-testing** inverts the existing `ShaftLayout.Result.xMmFromPx` to get the tap position
in mm, then calls the pure `pickLinerIdAtMm` (`LinerWearMath.kt`) to pick the liner whose span
contains it — ties (a tap exactly on a shared boundary) broken by whichever liner has the
nearer edge. A hit opens `LinerWearDetailOverlay` for that liner's id.

**Detail overlay (`LinerWearDetail.kt`)** — a full-screen composable, not a nav destination,
same shape as `PdfPreviewOverlay`: its own `BackHandler` plus a back-arrow top bar. Layout math
is self-contained (draws one liner + short neighbor stubs, not the whole shaft, so it does not
use `ShaftLayout`): `computeLinerDetailPxPerMm` is width-driven but capped by an available-height
budget so a very short liner doesn't blow its drawn diameter off-canvas. Neighbor stubs (~24dp)
come from `resolvedComponents` at the diameter touching the liner, terminated at their *far* end
with a Compose port of the pdf layer's S-curve break edge (`pdf/BreakSymbol.kt`'s math,
redrawn with Compose `Path`/`DrawScope` rather than importing pdf code) — the edge touching the
liner itself is a plain line (a real boundary, not a "cut"). Wear bands render as hatched/tinted
rects at `clampWearBandToLiner` positions (visual clamp only; the underlying `WearSpot` is never
mutated) with a small per-spot dimension rail below (offset from the liner's AFT edge, then band
length, formatted via the existing `disp`/`abbr` helpers in the active unit). Spot cards below
the canvas use `NumericInputField` for Start/Length/Min-Ø (commit-on-blur, tap-and-leave no-op,
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

**Label rule (2026-07-11):** the printed OAL value is always the user's **typed OAL**
(`spec.overallLengthMm`) — the same "OAL never changes" rule as the main schematic
(`OverallLength.md`). The arrows still bracket the drawn SET-to-SET span; only the label
uses the typed value. Previously these documents labeled the SET-to-SET distance "OAL",
which meant the runout/wear sheets and the schematic could show two different numbers
under the same name for the same shaft.

---

## Wear Document Page Layout

`WearPdfComposer` targets U.S. Letter landscape (792 × 612 pt) with 36 pt margins (720 × 540 pt content area).

```
┌── header line 1: Customer / Vessel / Job # / Date / Side  (centred) ──────┐
│── header line 2: OAL / "WEAR / INSPECTION RECORD"         (centred) ──────│
├────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│                                                                             │
│          [shaft profile — centred vertically, blank for hand annotation]   │
│         |←──────────── OAL: nnn.nnnn" ────────────────────→|              │
│         |                                                   |  ← witness lines
│   ══════╪═══════════════════════════════════════════════════╪══════         │
│                                                                             │
│   Dye pen inspection:  PASS □   FAIL □     Notes: ____________________    │
└────────────────────────────────────────────────────────────────────────────┘
```

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
- Shaft centre is `(midTop + midBot) / 2` where `midTop = headerBottom + WEAR_HEADER_GAP_PT` and `midBot = notesY − WEAR_NOTES_GAP_PT`. It is independent of the OAL line position and sits at exactly the page centre for the default margins.
- OAL dimension line is computed **after** the horizontal scale factor (`ptPerMm`) is known: `oalLineY = shaftCy − rPx(maxBodyDia) − WEAR_OAL_ABOVE_SHAFT_PT`. This anchors it well above the actual drawn shaft top (raised 90 pt, same convention as the runout sheet) rather than at a fixed offset from the header.
- Witness (extension) lines are drawn at `x0` and `x1` from just above the shaft top up through the dimension line, matching standard engineering drawing convention.
- Notes row is anchored at `contentBot − WEAR_NOTES_BOTTOM_OFFSET_PT`, independent of shaft size.

**Layout constants (`WearPdfComposer.kt`):**

| Constant | Value | Role |
|---|---|---|
| `WEAR_HEADER_HEIGHT_PT` | 36 | Two-line header block height (rule sits at this offset from `contentTop`) |
| `WEAR_HEADER_GAP_PT` | 16 | Gap from header rule to drawing area top |
| `WEAR_OAL_ABOVE_SHAFT_PT` | 90 | Gap from shaft top edge to OAL dimension line (parity with `RunoutPdfComposer.OAL_LINE_SPACE_PT`) |
| `WEAR_NOTES_BOTTOM_OFFSET_PT` | 24 | Distance of notes baseline above `contentBot` |
| `WEAR_NOTES_GAP_PT` | 28 | Gap from drawing area bottom to notes baseline |

---

### Wear Detail Strips (Phase 4, 2026-07-18)

`composeWearPdf` takes an optional `wearRecord: WearRecord = WearRecord()` param (see
`docs/LinerWearAreas_Proposal.md` §6.2). Every existing call site is unaffected by the
default. All strip geometry (liner spans, neighbor diameters for the break-out stubs)
comes from `docSpec` — the spec after `withResolvedBodies(resolvedComponents)` — never
raw `spec.bodies`, same contract as the rest of this document.

**Selection & pagination** — `pdf/WearStripLayout.kt` (android-free, unit-tested directly,
`WearStripLayoutTest`):
- `collectWearLinerGroups` groups `wearRecord.spots` by liner, keeps only liners with ≥1
  spot, sorted aft → fwd. Orphaned spots (stale `linerId`) are dropped defensively (the
  authoritative drop is at decode time, `ShaftDocCodec`).
- `selectWearStripsForPage` caps at `WEAR_STRIP_MAX_PER_PAGE` (3). Liners beyond that are
  **not** put on a second PDF page — `composeWearPdf` only ever receives a single
  caller-supplied `PdfDocument.Page` (every call site does one `startPage` /
  `finishPage`), and growing that into true multi-page output would mean changing the
  function's signature and every call site. Instead, overflow renders as one text note
  line ("+N more liner(s) with wear spots ..., page limit 3") in a reserved band just
  above the notes area. Revisit if/when `composeWearPdf` grows multi-page support.

**Main profile** — liners with ≥1 wear spot get thin hatched bands (`drawWearBandsOnProfile`)
at their true axial position, clamped to the liner span (`clampWearBandToLiner`), drawn
after the profile's own liner outlines. Visible but not dominant — same alpha/weight
convention as the thread hatch already on this page.

**Vertical page split** — `computeWearVerticalLayout` splits the profile band into a
(possibly shrunk) main-profile region followed by up to 3 stacked strips. The profile
never shrinks below `max(WEAR_MIN_PROFILE_HEIGHT_PT, 2×drawn-shaft-radius + margin)` —
folding in the actual radius matters because `ptPerMm` here is a purely horizontal
(SET-to-SET) scale, so a short/wide shaft's true diameter isn't otherwise height-aware.
When the preferred strip height doesn't fit, every strip shrinks together (never
independently, never past the main profile). By construction the last strip's bottom
always lands exactly on the reserved area's bottom edge.

**Per-strip layout** — `computeWearStripHorizontalLayout` centers a break-out liner
(scaled `ptPerMm` local to the strip, capped/floored so very short/long liners don't
explode/vanish) between two fixed-width neighbor stubs; `computeWearStripInnerLayout`
then splits the strip's own vertical band into a title row, the liner cylinder, and the
single chained dimension rail below it (see "Dimension rail" below) — the cylinder
shrinks first, and if a pathological input leaves no room at all, the rail's label rows
drop toward zero (the rail line still draws; labels are simply not placed) rather than
let anything render past the strip's bottom edge. Each strip draws:
- Neighbor stubs at the resolved diameter abutting the liner (`neighborDiaMmAtAft` /
  `neighborDiaMmAtFwd`, falling back to the liner's own OD when there's no neighbor),
  broken out with the standard S-curve edge (`BreakSymbol.drawBreakEdge`).
- Hatched wear bands on the liner at strip-local scale, clamped the same way as the
  main-profile bands, plus a min-Ø reading printed just above each band — omitted
  entirely when `minDiaMm == 0` (unrecorded).
- One chained dimension rail below the cylinder (see "Dimension rail" below).
- One anchor-from-SET label per strip (`buildLinerAnchorLabel`) — the digitized form of
  the shop sketch's "110 FROM CPLG S.E.T." line. It reuses `mapToLinerDimsForPdf` +
  `LinerSpanBuilder.buildLinerSpans` verbatim, so the number always matches the liner
  dimension shown on the main schematic PDF.

**Dimension rail (2026-07-18 rework)** — replaces the original per-spot "AFT edge → band
start" / "band start → band end" text rows with one standard chained dimension rail below
the liner cylinder, following the same witness-line/arrowed-span/centered-label
convention the main schematic uses (`pdf/render/PdfDimensionRenderer.kt`):
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
  `PdfDimensionRenderer` itself isn't reused directly: it's built around the schematic's
  multi-tier DATUM/LOCAL rail stacking (spans that overlap in x get assigned different
  rails) and draws its rails ABOVE the shaft outline, whereas a wear strip's rail is a
  single flat chain of never-overlapping spans BELOW the liner cylinder — different enough
  on both the tiering model and the draw direction that the minimal shared idea (label
  centering, arrow direction, collision-bump) is replicated as small pure functions in
  `WearStripLayout.kt` instead of bending that renderer's API to a shape it wasn't built
  for.
- The rail's own vertical budget is now FIXED — `WEAR_RAIL_MAX_LABEL_ROWS` (2) stacked
  label rows reserved above the rail line, regardless of how many wear spots the liner
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
- Keyway reference marker — an open square notch straddling the rim at 12-o'clock (key-at-top-centre convention, like the hand-drawn sheets) — appears on every bubble in BOTH the PDF and the canvas preview.
- OAL arrows bracket the SET-to-SET span, not the full `overallLengthMm`.
- The preview bitmap is rendered at 2× raster scale for sharpness on high-density displays.
- Temp PDF files used for preview rendering are deleted after rasterisation.

---

## Future Options

- User-selectable keyway reference angle.
- Multiple orientation diagrams on one sheet (e.g., Looking AFT + Looking FWD side-by-side).
- Printable measurement table rows below each bubble.
- Severity rating / dye-pen pass-fail digitization and photos on wear spots (explicitly out of
  scope for the liner wear feature — see `docs/LinerWearAreas_Proposal.md` §1).
- Wear on bodies/tapers, not just liners (see the proposal's §10.5 open question).
