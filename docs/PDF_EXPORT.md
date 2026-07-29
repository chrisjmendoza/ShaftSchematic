# PDF Export Specification
Version: v0.5.x
Last updated: 2026-07-28 — §5.5 wear-document blank-draft bullet corrected (blank mode keeps
the profile AND every liner's detail strip since 2026-07-28, values-out only) and extended
for measured-Ø readings; §5.3 gains a pointer to the wear document's own measured-Ø callout
system. 2026-07-22 — added §5.4 Inline Dimension Text (dimension values now seated in a break in the line, drafting-convention style, PDF export + preview); added §5.3 On-Shaft Diameter Callouts (body/liner OD leaders now all-BELOW, ≤3-decimal formatting, two-tier stacking); previously 2026-07-18 fixed page orientation (landscape, not portrait), clarified preview/PDF as separate drawing paths (named the three fit functions), replaced the "no display compression" invariant with the actual round-stock S-break behavior, fixed the AUDIT.md path.

## Purpose
Defines the **single-page** PDF export process.  
PDF output must faithfully reproduce the shaft schematic at high resolution with no geometry distortion.

---

# 1. High-Level Process

spec → ShaftLayout (PDF size) → ShaftPdfComposer (draws geometry + dimensions + footer) → Final PDF Document

Preview rendering (`ShaftLayout` + `ShaftRenderer`) and the PDF composers (`ShaftPdfComposer`,
`RunoutPdfComposer`, `WearPdfComposer`) are **SEPARATE drawing paths**. They share model geometry
(mm) and layout-math *concepts* but not code:
- `ShaftPdfComposer` never calls `ShaftLayout.compute()`. It computes its own point-per-mm scale
  via three fit functions: `computeBodyOnlyPtPerMm`, `computeDetailPtPerMm`,
  `computePdfPtPerMmFitAxes` (which one is used depends on export mode/body-only vs. detail).
- Unit formatting conventions match the preview, but the pixel/point math is independent.

**Note:** `ShaftPdfComposer` contains its own geometry drawing functions (`drawBodiesPlain`,
`drawBodiesCompressedCenterBreak`, `drawTapers`, `drawThreads`, `drawLiners`) separate from
`ShaftRenderer`. This is an intentional architectural split, not a bug to unify — see
`docs/archive/AUDIT.md` §4.4 for history.

**Coupler bolt slots** are drawn on **all three PDF profiles** — the main schematic (`ShaftPdfComposer`), the runout sheet (`RunoutPdfComposer`), and the wear document (`WearPdfComposer`) — via a shared `drawCouplerBoltSlots` helper. Each cutout is a circle straddling the shaft outline (half in the shaft, half in the coupling), mirrored on the top and bottom edges, at each cutout position along the row. No dimension rail is drawn in v1 (the `showDimensionRail` toggle exists but is deferred).

---

# 2. Page Format

### Standard Page
- US Letter: 11" × 8.5" (792 × 612 pt)
- **Landscape** orientation (`PdfDocument.PageInfo.Builder(792, 612, 1)` —
  `PdfExportRoute.kt`, `PdfPreviewScreen.kt`)
- 50–75 pt margins (configurable)

### PDF Coordinate System
- 1pt = 1/72 inch
- (0,0) at top-left corner

`ShaftDrawing` (the Compose composable) is NOT used for PDF export.
Preview color settings (presets/custom palette and Black/White Only) are preview-only.
PDF uses its own fixed black-and-white styling inside `ShaftPdfComposer`.

---

# 3. Layout Flow

1. Define content bounds based on margins.
2. Compute `ptPerMm` using one of `ShaftPdfComposer`'s own fit functions (not `ShaftLayout`):
   `computeBodyOnlyPtPerMm`, `computeDetailPtPerMm`, or `computePdfPtPerMmFitAxes`, each taking
   the geometry rect's width/height in points and fitting `overallLengthMm` / `maxOuterDiaMm`.
3. Draw shaft geometry using `ShaftPdfComposer`'s own drawing functions (`drawBodiesPlain` /
   `drawBodiesCompressedCenterBreak`, `drawTapers`, `drawThreads`, `drawLiners`) — **not**
   `ShaftRenderer`.
4. Draw title block.

---

# 4. Title Block Specification

### Position:
Top of page, full width.

### Fields:
- Project / Drawing Title
- Description (optional)
- Date
- Units (always “mm”)
- Overall Length
- Scale (“1:1”, “2:1”, or “Scale to Fit”)
- Drawn By (optional)
- Revision (optional)

### Font Rules:
- Sans-serif
- 10–14 pt depending on field importance
- Black text only

---

# 5. Scale Notation

Compute:
scaleFactor = pxPerMmPDF * (1 inch in px) / 25.4mm

 

Then:
if scaleFactor ≈ 1 → "1:1"
if scaleFactor ≈ 2 → "2:1"
else → "Scale to Fit"

 

Users do not configure scale manually.

---

# 5.1 Line Thickness Scale

`composeShaftPdf()` accepts a `lineThicknessScale: Float` parameter (range 0.5–2.0, default 1.0). It is applied to two base stroke widths:

- `OUTLINE_PT_BASE = 1.25 pt` × scale → body/taper/thread/liner outline strokes
- `DIM_PT_BASE = 0.8 pt` × scale → dimension tick and arrow strokes

At 1.0 the output matches the current default thin weight. At 2.0 it matches the original pre-rebased thick weight. The scale is persisted in DataStore and passed from `PdfExportRoute` and `PdfPreviewScreen`.

---

# 5.2 OAL Dimension Span

The OAL span's label value always equals `spec.overallLengthMm` — the user's typed value.
The bracket **position** changes based on `excludeFromOAL`:

- **Excluded**: bracket spans AFT SET → FWD SET (threads outside the bracket).
- **Included**: bracket spans shaft AFT end → FWD SET (thread grouped inside the bracket).

The label is passed as an explicit `labelMm` override to `oalSpan()` so it is always the typed OAL regardless of bracket width. Component dimension rails always reference SET positions.

**Label text (2026-07-28):** printed end-to-end spans keep their small `"OAL"` prefix —
`oalSpan()` (`pdf/dim/LinerSpanBuilder.kt`) renders `"OAL " + formatLenDim(labelMm, unit)`,
and the wear/runout OAL lines print `"OAL: value"` (product decision: compact print output
reads well and the prefix is a nice visual identifier). Blank/write-in drafts drop the
wording entirely — the renderer cuts an empty writable break mid-span and draws no label
text (see `RunoutSheet.md` §"OAL Dimension Alignment" and the blank-draft sections), and
the wear header never carries the OAL in either mode.

---

# 5.3 On-Shaft Diameter Callouts

Body OD and liner OD each get a leader-line "Ø" callout hanging **BELOW** the shaft
(`buildBodyOdCallouts` / `buildLinerOdCallouts` in `ShaftPdfComposer.kt`, drawn by
`pdf/notes/DiameterLeaderRenderer.kt`). Body callouts previously alternated above/below;
they are now all-BELOW, same as liners.

- **Grouping:** one callout per unique OD, anchored at the horizontal center of the
  *longest* segment carrying that OD. Bodies group by `Body.diaMm`; liners group by
  `Liner.odMm`. Bodies and liners are **separate groups** — a liner OD is never merged
  with a body OD even when the values match numerically.
- **Formatting:** labels use `formatDiaWithUnit` (≤3 decimals, trailing zeros trimmed),
  the same convention as the footer's "Ø" text — not the old raw 4-decimal formatting.
- **Two-tier stacking:** horizontally-close labels stack onto a second row instead of
  overlapping, the same posture as the runout bubbles' two-row layout (see
  `RunoutSheet.md`). Tier assignment is pure, JVM-tested math in
  `geom/DiameterCalloutLayout.kt` (`assignTiers` — greedy left-to-right interval
  coloring, capped at `MAX_TIERS = 2`, `MIN_GAP = 4f` pt clearance); the renderer only
  measures label widths and reads back the tier.
- **PDF-only:** there is no on-screen canvas diameter leader, so the "draw identically
  in both sites" rule that applies to coupler bolt slots / wear pits / runout markers
  does not apply here.
- **Distinct from the wear document's measured-Ø callouts.** These schematic callouts label
  *nominal* body/liner ODs from the spec. The wear document has its own callout system for
  *measured* diameters (`WearRecord.diaReadings` → `geom/WearDiaCalloutLayout.kt`, drawn on
  the liner detail strips and under the main profile, with a canvas twin in the wear
  overlay). See `RunoutSheet.md` §"Wear Diameter Measurements".

---

# 5.4 Inline Dimension Text (value seated in the line break)

Dimension values on the schematic's horizontal dimension lines (`PdfDimensionRenderer.drawSpan`)
are drawn seated **inside a break in the line** — the hand-drafting convention
`|←—— 237 1/2" ——→|` — instead of floating above a continuous line.

- **Inline (primary) path.** The main dimension line is drawn as two stubs,
  `xa → gapLeft` and `gapRight → xb`, where `gapLeft = cx - half - textPad` and
  `gapRight = cx + half + textPad` (gap width = label width + 2×`textPad`, centered on
  the clamped label center `cx`). The value is drawn in that gap, vertically centered on
  the line at baseline `y - (fm.ascent + fm.descent) / 2`.
- **Eligibility.** Inline mode requires both resulting stubs be at least `arrowSize`
  long — the *same* predicate (`canFitInwardArrows`) already used to choose inward vs.
  outward arrowheads. Because it's the same predicate, an inline span always gets
  inward-pointing arrows aligned with the value; there is no separate "should this be
  inline" flag to keep in sync.
- **Fallback path.** When the span is too short, or the inline label would collide with
  a label already placed on that rail, `drawSpan` reverts to the original behavior: one
  continuous line `xa → xb`, the label floating above it at baseline `y - textAboveDy`,
  the existing bounded bump-up-on-collision loop, and outward arrows.
- **Top OAL rail included.** `drawTop` and `drawOnRail` both delegate to the same
  `drawSpan`, so the top OAL dimension line gets the identical inline-break treatment as
  the numbered component rails below it.
- **Unchanged:** extension lines, `labelBottom` (SET name below the rail), `drawArrow`,
  `canFitInwardArrows`, and the collision/bump helpers.
- **Scope: PDF-only, no canvas twin.** `PdfDimensionRenderer` backs both the exported
  PDF and the on-screen PDF preview — `PdfPreviewScreen` rasterizes the real PDF via
  `composeShaftPdf` → `ShaftPdfComposer` → this same renderer, so there is no separate
  preview draw path to keep in sync. The on-screen schematic canvas (`ShaftRenderer`) has
  no horizontal dimension rails at all, so the repo's "draw identically in both sites"
  rule (spooned keyways, wear pits, runout markers) does not apply here.

---

# 5.5 Blank Drafts (write-in mode) & Direct Print

**Blank drafts** (`PdfExportOptions.blankValues`, plus a `blankValues` parameter on
`composeRunoutPdf`/`composeWearPdf`) print the full drawing and form layout with every
VALUE blanked so the sheet can be filled in by hand in the field — e.g. reusing a
similar shaft's layout for a new inspection, or stocking blank forms where phones
aren't allowed.

Rules (shared helpers in `pdf/BlankFormText.kt`):

- **Dimension lines** still cut their break, at a fixed writable width
  (`BLANK_DIM_GAP_PT`), but draw no value text — the gap is the write-in spot. Same
  eligibility/fallback logic as §5.4; label *bounds* are still reserved so gaps on the
  same rail never overlap. `labelBottom` (SET names) are identifiers and still print.
- **Ø leader callouts** print `Ø` + a writing rule instead of the value.
- **Schematic footer** keeps every label (`Rate:`, `L.E.T. (…):`, `KW:`, `Customer:` …)
  followed by a writing rule (`BLANK_RULE_PT`); the bold STBD/PORT stamp becomes a
  `Side:` rule. Lines space out (`FOOTER_LINE_FACTOR_BLANK` = 1.8 vs 1.35) and the
  footer band grows (`FOOTER_BLOCK_BLANK_PT` = 150 pt vs 96 pt) because handwriting is
  larger than print. `buildFooterEndColumns(blankValues = true)` returns label-only
  lines — same count and order as standard, no digits (JVM-tested in
  `BlankDraftFooterTest`).
- **Runout sheet**: header labels get rules; the OAL dimension line carries no label of any
  kind (2026-07-28) — it cuts an empty `BLANK_DIM_GAP_PT`-wide break at mid-span instead,
  the same convention as the dimension-line rule above, so the machinist writes the value
  straight into the break. Recorded TIR values / high-spot ticks are not drawn (bubbles
  remain — they ARE the write-in circles), and the TIR-direction line always prints as a
  fill-in blank.
- **Wear document**: header job-info fields spread edge-to-edge with equal writing rules
  and the title centers on line 2 — the header never carries an OAL field, printed or
  blank (2026-07-28). The OAL dimension line blanks the same way as the runout sheet's
  (empty mid-span break, no label). The profile AND every liner's zoomed detail strip
  still render (2026-07-28 — blank mode keeps the drawing; strips' dimension lines keep
  their edge witness bars, values left out); recorded wear DATA (bands, pit X's,
  measured-Ø callouts) is omitted — the print is a fresh inspection form.
  Recorded data in the app is never touched; blanking is render-only.
- The blank toggle is **session-only, never persisted** (schematic:
  `ShaftViewModel.pdfBlankDraft`, runout/wear: local screen state) — a forgotten sticky
  toggle would silently blank every future export. Blank exports get a `_BlankDraft`
  filename suffix.

**Direct print** (`util/PdfPrint.kt`, `printShaftPdfPage`) wraps the same composers in a
`PrintDocumentAdapter` (US Letter landscape, 1 page) and hands them to the Android print
framework — Print buttons live on the PDF preview top bar and the runout/wear screens.
A print and an export of the same document are composed by the same call and are
therefore identical.

---

# 6. PDF Rendering Invariants

1. Export is **single page** only.
2. No multi-page continuation.
3. No BOM tables.
4. **Round-stock display compression exists for long bodies** (this replaces an earlier "no
   display compression" claim, which is no longer true). `ShaftPdfComposer.drawBodiesCompressedCenterBreak()`
   triggers per-body when that body's on-paper length reaches `COMPRESS_TRIGGER_PT` (220 pt): the
   body is drawn as two shortened stubs, each capped with an S-curve "round-stock break" symbol
   (`pdf/BreakSymbol.kt`, `drawBreakEdge()`) instead of a straight end cap, so the drawing reads as
   a foreshortened cylindrical bar rather than a literal-length rectangle. The footer prints an
   explanatory compression note (`showCompressionNote`) whenever any drawn body triggers this.
   Only bodies are compressed this way — tapers/threads/liners are never broken.
5. No component overlays, cross-sections, or detailed machinist symbols (aside from the
   round-stock break symbol above, which is a length-compression cue, not a machinist symbol).
6. Geometry must reflect the same logic as preview.
7. PDF export never modifies the model.

---

# 6.1 Measurement & Tiering Rules

- Forced AFT/FWD uses a single global reference for numeric baselines.
- AUTO preserves per-component anchor/proximity behavior.
- Tiering affects rail stacking only and never changes numeric values.
- Units are passed explicitly and never derived from tiering or measurement reference.
- DATUM spans tier shortest-first (`DeterministicTierAssigner`), so nested datum chains
  stack inner→outer regardless of which end they share (AFT chains share their start,
  FWD chains share their end). A span that contains another must sit on a higher rail —
  otherwise the inner span's extension lines would cut through the outer span's
  dimension line (2026-07-26 liner-datum bug).

---

# 7. Error Handling

- If `overallLengthMm == 0`, export stops.
- If layout fails (rare), PDF generation aborts with user-visible error.

---

# 8. Summary
This contract ensures PDF output is consistent, readable, scalable, and fully aligned with the in-app schematic preview.

PDF export must always reflect the renderer’s output exactly.