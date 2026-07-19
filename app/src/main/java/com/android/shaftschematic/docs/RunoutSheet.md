# RunoutSheet & WearDocument

**Files:**
- `geom/RunoutBubbleLayout.kt` — shared bubble placement engine (stations, rows, x-solve, leader routing + collision verification)
- `ui/screen/RunoutRoute.kt` — runout station config, canvas preview, PDF preview overlay
- `ui/screen/WearRoute.kt` — wear inspection document tab
- `pdf/RunoutPdfComposer.kt` — letter-landscape runout PDF generation
- `pdf/WearPdfComposer.kt` — letter-landscape wear document PDF generation

---

## Responsibilities

### RunoutRoute
- Let the user set TIR orientation (Looking AFT / Looking FORWARD / Not set).
- Let the user override the station count per component (bodies, tapers, liners).
- Render a live canvas preview of the shaft with runout bubbles.
- Export a hand-fill-in PDF runout sheet via SAF.
- Preview the PDF in-app via `PdfPreviewOverlay` with a Tune options sheet.

### WearRoute
- Display a brief explanation of the blank-outline field form.
- Preview the wear document PDF in-app via `PdfPreviewOverlay` with a Tune options sheet.
- Export a blank shaft outline PDF via SAF for field damage and dye-pen inspection marking.

Both tabs share the same layout pattern: outer `Column` with `systemBarsPadding()`, a toolbar `Row` (hamburger + title), `HorizontalDivider`, then a vertically-scrollable inner `Column`.

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
- Phase 2 wear: digital damage annotation — tap zones, severity rating, dye-pen pass/fail toggle.
