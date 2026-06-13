# RunoutSheet & WearDocument

**Files:**
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

Both the canvas preview and the PDF use the same three-pass algorithm:

### Pass 1 — Collect
For each component (sorted AFT→FWD), compute `N` axial station positions:
- **Bodies:** evenly distributed across full length.
- **Tapers / Liners:** inset from each edge by `RUNOUT_EDGE_INSET_MM` (≈ 25.4 mm) so measurements land on the cylindrical run-out, not the transition slope.

Each station produces:
- `stationX` — canvas/page X at the axial measurement point.
- `shaftBottomY` — canvas/page Y at the shaft's outer surface at that station.
- `bubbleX` — bubble center X, spread horizontally within the component. Bubbles are centred on the component's page midpoint; slot width = bubble diameter + minimum gap. The group may splay beyond the component edges symmetrically if N slots exceed the component width.

### Pass 2 — Greedy Level Assignment
All bubbles are sorted by `bubbleX`. Each is assigned the **lowest level** where its circle does not horizontally overlap any already-placed bubble at that level (i.e., `prevRightEdge[level] + minGap ≤ bubbleLeft`).

- Level 0 → shortest leader (closest to shaft).
- Level N → `SHORT_LEADER + N × (LONG_LEADER − SHORT_LEADER)`.

This guarantees zero overlap regardless of station density or component count. In typical configurations only levels 0–1 are used; level 2+ appears only when adjacent components produce densely-packed bubbles at a shared boundary.

### Pass 3 — Draw
Each bubble's leader line is drawn **diagonally** from `(stationX, shaftBottomY)` to `(bubbleX, bubbleTop)`. Because `stationX ≠ bubbleX` in general and spreading is monotonic within each component, leaders fan outward and cannot enter any bubble's interior.

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

**Why:** Thread components at the aft/fwd ends are NOT drawn in the shaft profile (the profile only draws bodies, tapers, and liners). If the span were based on `overallLengthMm`, the OAL arrows would extend into un-drawn whitespace. Basing the span on the SET faces keeps the arrow tips coincident with the visible shaft ends.

The OAL label shows the SET-to-SET distance, which is the physically meaningful dimension for a machinist.

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
| OAL dimension line | ≈ 275 (shaft top − 16 pt) |
| Drawing area bottom | 524 |
| Notes / checkboxes | 552 |

**Key layout decisions:**
- Header is split across two centred lines so long job-info strings don't overflow the 720 pt content width.
- Shaft centre is `(midTop + midBot) / 2` where `midTop = headerBottom + WEAR_HEADER_GAP_PT` and `midBot = notesY − WEAR_NOTES_GAP_PT`. It is independent of the OAL line position and sits at exactly the page centre for the default margins.
- OAL dimension line is computed **after** the horizontal scale factor (`ptPerMm`) is known: `oalLineY = shaftCy − rPx(maxBodyDia) − WEAR_OAL_ABOVE_SHAFT_PT`. This anchors it just above the actual drawn shaft top rather than at a fixed offset from the header.
- Witness (extension) lines are drawn at `x0` and `x1` from just above the shaft top up through the dimension line, matching standard engineering drawing convention.
- Notes row is anchored at `contentBot − WEAR_NOTES_BOTTOM_OFFSET_PT`, independent of shaft size.

**Layout constants (`WearPdfComposer.kt`):**

| Constant | Value | Role |
|---|---|---|
| `WEAR_HEADER_HEIGHT_PT` | 36 | Two-line header block height (rule sits at this offset from `contentTop`) |
| `WEAR_HEADER_GAP_PT` | 16 | Gap from header rule to drawing area top |
| `WEAR_OAL_ABOVE_SHAFT_PT` | 16 | Gap from shaft top edge to OAL dimension line |
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
    lineThicknessScale: Float = 1.0f,
)

fun composeWearPdf(
    page: PdfDocument.Page, spec: ShaftSpec,
    project: ProjectInfo, unit: UnitSystem,
    pdfPrefs: PdfPrefs = PdfPrefs(),
    lineThicknessScale: Float = 1.0f,
)
```

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
- Thread components produce no runout stations and are not drawn in the shaft profile.
- The PDF page is U.S. Letter landscape (792 × 612 pt).
- Canvas preview and PDF use the same spreading and level-assignment logic so they look identical.
- Keyway reference marker (small filled square at 12-o'clock) appears on every bubble in the PDF.
- OAL arrows bracket the SET-to-SET span, not the full `overallLengthMm`.
- The preview bitmap is rendered at 2× raster scale for sharpness on high-density displays.
- Temp PDF files used for preview rendering are deleted after rasterisation.

---

## Future Options

- User-selectable keyway reference angle.
- Multiple orientation diagrams on one sheet (e.g., Looking AFT + Looking FWD side-by-side).
- Printable measurement table rows below each bubble.
- Phase 2 wear: digital damage annotation — tap zones, severity rating, dye-pen pass/fail toggle.
