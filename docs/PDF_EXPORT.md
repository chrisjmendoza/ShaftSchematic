# PDF Export Specification
Version: v0.5.x
Last updated: 2026-08-12 — §5.6 documents the consolidated preview's Tune sheet gaining
Blank draft, Shaft height, Liner compression, and Measurement reference (the schematic
Tune sheet's applicable set, minus Component labels and the blank Ø-callouts sub-toggle,
which the consolidated composer never reads). 2026-08-06 (b) — §5.4/§5.5 gain the one-collision-space rule for dimension
labels (pure `geom/DimensionRailLayout.kt`: rail lines are obstacles too, slide along the
span before bumping, tiers above a floating value lift by one label band, composer budgets
include the lifts). 2026-08-06 — §5.6 gains the conditional liner-shading rule
(`consolidatedSheetHasInProfileValues`) and the locked "Liners" checkbox. 2026-08-05 (b) — §1/§3 name the single real fit function
(`computeDetailPtPerMm`; the `computeBodyOnlyPtPerMm`/`computePdfPtPerMmFitAxes` names never
existed); §4 units corrected (printed values follow the document's ACTIVE unit, not always
mm); §5.5 gains the Tune options-sheet inventory; §5.7 names only the public
`fracFitFactor`. 2026-08-05 — §5.7 gains the default sizing curve (linear, superseding the
flat visual scale as the 100% base; the shipped anchors were 4" → 0.75" / 8" → 1.25" that
day and are now the proportional 4" → 0.5" / 8" → 1" — §5.7 is the current statement) with
user-adjustable anchor heights (Settings → Drawing → "Default drawing size",
`PdfPrefs.curveLo/HiHeightIn`); §6.4 documents the S-break pair's minimum-gap layout
(`breakPairLayout`, ≥ 1 pt of daylight) and the foreshortening trigger (since 2026-08-06 a
user-set threshold, Settings → Drawing → "Body S-break" — §6.4). Previously 2026-07-28 — §5.5 wear-document blank-draft bullet corrected (blank mode keeps
the profile AND every liner's detail strip since 2026-07-28, values-out only) and extended
for measured-Ø readings; §5.3 gains a pointer to the wear document's own measured-Ø callout
system. 2026-07-22 — added §5.4 Inline Dimension Text (dimension values now seated in a break in the line, drafting-convention style, PDF export + preview); added §5.3 On-Shaft Diameter Callouts (body/liner OD leaders now all-BELOW, ≤3-decimal formatting, two-tier stacking); previously 2026-07-18 fixed page orientation (landscape, not portrait), clarified preview/PDF as separate drawing paths (fit functions named; corrected 2026-08-05), replaced the "no display compression" invariant with the actual round-stock S-break behavior, fixed the AUDIT.md path.

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
  via a single fit function, `computeDetailPtPerMm`, which fits `overallLengthMm` /
  `maxOuterDiaMm` into the geometry rect. (The height it actually draws at is then set by the
  sizing curve + "Shaft height" slider — see §5.7.)
- Unit formatting conventions match the preview, but the pixel/point math is independent.

**Note:** `ShaftPdfComposer` contains its own geometry drawing functions
(`drawBodiesCompressedCenterBreak`, `drawTapers`, `drawThreads`, `drawLiners`) separate from
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
2. Compute `ptPerMm` with `ShaftPdfComposer`'s own fit function (not `ShaftLayout`):
   `computeDetailPtPerMm`, taking the geometry rect's width/height in points and fitting
   `overallLengthMm` / `maxOuterDiaMm`.
3. Draw shaft geometry using `ShaftPdfComposer`'s own drawing functions
   (`drawBodiesCompressedCenterBreak`, `drawTapers`, `drawThreads`, `drawLiners`) — **not**
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
- Units — printed values follow the document's **active unit** (inches or mm), not always
  mm. Every printed length/diameter goes through `formatLenWithUnit` / `formatDiaWithUnit`
  with the document's `UnitSystem`; only the model layer is unconditionally mm.
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

## Visibility

Two independent controls gate the pass; both must allow a callout for it to print.

**Per component — "Show Ø on drawing"** (`Body.showDiaOnDrawing`, `Liner.showDiaOnDrawing`,
and `ShaftSpec.showAutoBodyDia` for every auto span at once). A switch under the Ø field on
the body / auto-body / liner cards. All three are additive envelope fields. **Body and
bare-shaft callouts are opt-in — those two flags default `false`** (on-device preference:
the schematic stays clean unless a body's Ø is deliberately shown, and a document with no
flags prints no body Ø callouts; the footer's "Body:" list still carries every Ø). Liners
default `true`.

- **The filter runs before the grouping.** Hiding one body of a shared-Ø group does not
  remove the value from the sheet — the anchor moves to the longest body of that Ø that is
  still shown. The on-device case was a body running under fiberglass with one bare window,
  where a callout over the covered run claimed a reading had been taken there. Model the two
  runs as separate bodies and show only the window's Ø — the callout anchors on the surface
  that was actually measured. A body whose Ø no shown body carries simply does not print
  below the shaft.
- **Fragment-aware.** `ShaftSpec.bodyForPdf` looks the flag up by `resolvedBodyBaseId`, so a
  body a liner has split hides every one of its drawn runs.
- **The footer is not gated.** The "Body:" Ø list still shows the diameter — the value is
  true for the shaft; only its placement on the drawing was misleading.
- Draw-only: no geometry, no OAL, no collision, no stored value changes. Card-only, with no
  Add-dialog counterpart (see the carve-out in `CLAUDE.md` / `AddComponentDialogs.md`).

**Per sheet — blank drafts** (`PdfExportOptions.blankDiaCallouts`, a sub-switch under the
blank-draft toggle in the PDF options sheet; session-only, like that toggle). Off skips the
whole pass — line, arrow and write-in rule — so the shaft prints clear for freehand
annotation; a blank leader with nothing to write on is worse than no leader. Ignored outside
blank mode.

`PdfExportOptions.showDiaCallouts` (`!blankValues || blankDiaCallouts`) is the single place
the sheet-level rule lives; the four call sites that build export options pass the raw
preference and never re-derive it.

---

# 5.4 Inline Dimension Text (value seated in the line break)

Dimension values on the schematic's horizontal dimension lines
(`PdfDimensionRenderer.drawPlanned`) are drawn seated **inside a break in the line** — the
hand-drafting convention `|←—— 237 1/2" ——→|` — instead of floating above a continuous line.

- **Inline (primary) path.** The main dimension line is drawn as two stubs,
  `xa → gapLeft` and `gapRight → xb`, where `gapLeft = cx - half - textPad` and
  `gapRight = cx + half + textPad` (gap width = label width + 2×`textPad`, centered on
  the clamped label center `cx`). The value is drawn in that gap, vertically centered on
  the line at baseline `y - (fm.ascent + fm.descent) / 2`.
- **Eligibility.** Inline mode requires both resulting stubs be at least `arrowSize`
  long (`canFitInwardArrows`), so an inline span always keeps inward arrows aligned with
  the value. Eligibility is decided from **x-geometry alone**, so the set of fallback
  spans is known before any vertical budget is chosen.
  - In full: a span seats its value only if it affords
    `labelWidth + 2·textPad + 2·arrowSize`. **Both other terms are therefore fit budget,
    not just cosmetics.** `textPad` is the shared `DIM_BREAK_TEXT_PAD_PT` (4 pt) — the same
    gap the wear/undercut strip rails cut, so one convention serves every rail. It was
    formerly a private 6 pt here, which is wider *per side* than a 16 pt value is across,
    and it pushed values that plainly fitted their rail into the fallback (on-device
    report). Do not restore a renderer-local pad.
  - `labelWidth` is the **rich** width (`Paint.measureRichText`): a built-up fraction is
    narrower than its characters inline, so the fraction typography widened eligibility as
    a side effect. Diagonal fractions cost ~3.6 pt more than stacked and seat slightly less
    often; a Small arrowhead returns 2 pt of that. See
    `app/.../docs/FractionTypography.md`.
- **Fallback path.** A span too short for that reverts to the original behavior: one
  continuous line `xa → xb`, with the value floating above it at baseline
  `y - textAboveDy`. It keeps its inward arrows — see below.
- **Arrow direction is decided from the span, not from the value.** Outward (tips-in)
  heads hang *outside* the extension lines, so two spans meeting at a shared boundary
  cross their heads into an X there. `DimensionRailLayout.arrowsPointInward` therefore
  turns them out only when the heads cannot fit *between* the extension lines at all —
  `(xb − xa) ≥ 2·arrowSize + ARROW_CLEAR` — and the planner reports it per span as
  `Placement.arrowsInward`. Direction was previously tied to `Placement.inline`, which
  spent the cramped convention on any span whose value merely fell back: a blank draft's
  60 pt write-in gaps push whole rails to fallback, and the outward heads on adjacent
  spans overlapped (on-device report).
- **Arrowhead shape and size.** A slim 2:1 V — barb spread is half the head's length —
  one shape at every rail draw site. The length is user-set: `PdfPrefs.arrowSizePt`,
  **Small 3 (default) / Medium 4 / Large 5 pt**, from either PDF options sheet or
  Settings → Drawing → "Dimension arrows". It reaches the schematic and consolidated
  composers (the two that build a `PdfDimensionRenderer`); the wear/undercut strip rails
  keep their own fixed 4 pt head. A smaller head also widens inline eligibility slightly,
  since the stub requirement is `arrowSize`.
- **One collision space (`geom/DimensionRailLayout.kt`).** A floating value lives in the
  vertical band of the *next rail up*, so labels and rail lines from different tiers
  cannot be resolved rail by rail. A pure planner places **every** span at once — the top
  OAL rail included — treating both the placed labels and every rail **line** as
  obstacles, so no value is ever printed over another value or struck through by a
  neighbouring tier's dimension line.
  - **Slide before bump.** A colliding value first slides **horizontally along its own
    span** — the smallest shift from center that clears everything, bounded by
    `[xa + textPad + half, xb − textPad − half]`, tightened by `arrowSize` on both sides
    for an inline value so the break keeps its inward arrows. Only a floating value that
    cannot slide clear bumps vertically (bounded, never past the content top).
  - **Tiers lift.** A rail carrying at least one floating value pushes every rail
    **above** it — the OAL rail included — up by one label band (glyph height + gap),
    cumulatively per intervening fallback rail, so the lines clear the values.
  - **Budgets include the lifts.** `ShaftPdfComposer` folds `topLift` into its
    `computeTopY` fit loop (shrink rail gap, then text size, until the lifted block still
    clears `geomRect.top`); `RunoutPdfComposer` adds it to `railsBlockH` before the shaft
    scale is solved — the inline/fallback split depends only on drawn width, so a prelim
    plan on the linear map answers it, the same prelim-then-resolve posture the bubble
    budget uses.
  - Placement order is least-slide-room-first, so a wide span is the one that moves.
    Pinned by `DimensionRailLayoutTest`.
- **Top OAL rail included.** The OAL span is planned and drawn through the same path as
  the numbered component rails below it, so it gets the identical inline-break treatment
  and participates in the same collision space. It is the topmost measurement but rides
  exactly **one regular tier pitch** above the highest component tier — both composers;
  no extra OAL padding constant (on-device report: the wider gap wasted whitespace). The
  planner's lift is the only thing that widens the gap, and only when the tier below
  floats a label into the lane.
- **Unchanged:** extension lines, `labelBottom` (SET name below the rail), `drawArrow`,
  and `canFitInwardArrows`.
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

- **Dimension lines** still cut their break, at a writable width (`BLANK_DIM_GAP_PT` = 60 pt,
  sized for handwriting a mixed-number dimension on a clipboard), but draw no value text —
  the gap is the write-in spot. Same eligibility/fallback/collision logic as §5.4: the
  planner measures the write-in width instead of the value text, so gaps are reserved — and
  slid or lifted clear of each other — exactly as printed values are. `labelBottom` (SET
  names) are identifiers and still print.
  - **A short span shrinks its gap rather than losing it**
    (`DimensionRailLayout.blankGapWidth`): a span that cannot host 60 pt plus its pad and
    arrowheads cuts whatever it affords, down to `BLANK_DIM_GAP_MIN_PT` (28 pt, about a
    cramped `19 1/2`). Only a span too tight for even that keeps the continuous-line
    fallback — there, a gap too small to write a dimension into is worse than an unbroken
    line, since it reads as a printed break. The full 60 pt gap had been all-or-nothing, so
    rails on ordinary-length spans printed with nowhere to write (on-device report).
  - One `labelWidth(span)` answers both the planner's reserved box and the cut gap, so a
    shrunk gap can never disagree with what was reserved for it. The shrink leaves
    `GAP_FIT_SLACK` of float headroom so a gap sized to the span cannot fail its own
    inward-arrow eligibility test by a rounding hair.
- **Ø leader callouts** print `Ø` + a writing rule instead of the value.
- **Schematic footer** keeps every label (`Rate:`, `L.E.T.:`, `KW:`, `Customer:` …)
  followed by a writing rule that runs to the **column edge** (a fixed ~1" rule was too
  short to hand-write a customer name — on-device report); the bold STBD/PORT stamp
  becomes a `Side:` rule. Lines space out to a handwriting pitch
  (`FOOTER_LINE_FACTOR_BLANK` = 2.2 vs 1.35) inside a taller band
  (`FOOTER_BLOCK_BLANK_PT` = 200 pt vs 96 pt); `drawFooter` fit-clamps the pitch to the
  band, so the fullest column (taper + spooned note + thread) tightens toward printed
  density instead of overrunning the page. The middle job-info column starts one line
  **lower** than the end columns (`midLeadLines`) whenever those lead with an
  `AFT Taper`/`FWD Taper` heading: the middle block has no heading, so flush tops would
  put its rules half a pitch off every neighbouring rule. The fit-clamp counts that extra
  line. Printed footers stay flush — their lines carry values, not rules, and the 96 pt band
  has no spare line. The three columns also split the band into **equal thirds** on a blank
  draft (last column padded like the others, so every rule is the same length) instead of the
  printed 40 / 36 / 24 weighting, which exists for printed free text — customer and vessel
  names — and on a value-less sheet only makes the FWD rules short. Both blank footers — schematic and
  consolidated — share this one implementation. `buildFooterEndColumns(blankValues =
  true)` returns label-only lines — same count and order as standard, no digits
  (JVM-tested in `BlankDraftFooterTest`).
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
- On the schematic PDF preview the toggle surfaces **twice from one state**: an
  always-visible `FilterChip` overlaid top-center on the preview itself (testTag
  `pdf_blank_toggle` — added because the options-sheet switch alone was too hard to find,
  on-device report) and the original switch in the Tune options sheet. Both drive
  `setPdfBlankDraft`, so they can never disagree; toggling re-renders the preview live.

**The Tune options sheet** (`PdfPreviewScreen.kt`) hosts, in order: Blank draft (write-in),
Component labels, Line thickness, the **"Shaft height" slider** and the **liner compression**
control (both §5.7 — the same per-job `RunoutConfig` values the Consolidated Output tab
exposes, §5.6), Measurement reference, and the Shade-in-PDF checkboxes. The sheet scrolls,
capped at 78% of screen height so it never covers the preview entirely.

**Direct print** (`util/PdfPrint.kt`, `printShaftPdfPage`) wraps the same composers in a
`PrintDocumentAdapter` (US Letter landscape, 1 page) and hands them to the Android print
framework — Print buttons live on the PDF preview top bar and the runout/wear screens.
A print and an export of the same document are composed by the same call and are
therefore identical.

---

# 5.6 The Consolidated Output tab (variants + batch export)

The **Consolidated Output** tab (`EditorTab.OUTPUT`, `ui/screen/OutputRoute.kt`) is the
one-stop surface for the consolidated sheet; every original tab keeps its own hard-wired
preview/print/export producing its own document (the Runout tab's is the **classic**
standalone runout sheet, `composeRunoutPdf(consolidated = false)`, suffix `_runout`).

**Sheet content** (`ConsolidatedVariant`): the schematic's dimension rails + spec footer
are always on; the runout bubbles/TIR line and the wear info (marks, worn sections,
in-profile Ø values) are each electable — **All three** (default) | Schematic + Runout |
Schematic + Wear. Electing bubbles out returns their lanes to the shaft area. Selection
is session-only (resets to All three).

**Liner shading is conditional here**: liners follow the `shadedLiners` pref like bodies and
tapers, **except** on a sheet that prints Ø values inside the profile — those values sit on
sheet-white knockout halos, and grey underneath turns each halo into a pasted box, so liners
draw unfilled there whatever the pref says. One predicate decides it,
`consolidatedSheetHasInProfileValues` (`pdf/RunoutPdfComposer.kt`): wear info elected in, not
a blank draft, and at least one worn-section value > 0 or one valued reading keyed to a
component that still resolves (wear bands and pit X's are marks, not text — they never
suppress the fill). The composer builds `linerFill` from it and this tab's preview options
sheet locks its "Liners" checkbox with it (disabled, displayed unchecked, caption "Ø values
print inside the profile on this sheet") — display-only; the stored pref is never rewritten,
so it returns the moment the sheet stops printing in-profile values. The classic runout sheet
(`consolidated = false`) has no in-profile text and simply honors the pref.

**Also on this tab**: the per-job "Shaft height" slider (§5.7), the worn-section editor
(sections print on this sheet), the blank-draft toggle, and **Export all** — checkboxes
for the five documents (consolidated [current variant], schematic, runout, wear,
undercut; all on by default) written to one picked folder (`OpenDocumentTree` +
`createPdfInTree`), each through the hardened write path, with a written/failed result
line. Nothing auto-opens after a batch.

**The consolidated preview's Tune sheet** (`RunoutWearOptionsSheet`, `ui/screen/RunoutRoute.kt`)
hosts, in order: Blank draft (write-in) — this tab's own toggle, shown here too so the
sheet can be judged live — Line thickness, Body S-break, the **"Shaft height" slider** and
the **liner compression** control (§5.7, the same per-job `RunoutConfig` values as the
tab's own controls), Measurement reference, and the Shade-in-PDF checkboxes: the same set
as the schematic Tune sheet (§5.5) minus Component labels and the blank Ø-callouts
sub-toggle, which the consolidated composer never reads, so they would be inert here. The
other tabs reuse the same sheet with these additions off: the Runout instance keeps Line
thickness, Body S-break (the classic sheet draws compression breaks too), and the shade
checkboxes — the classic sheet honors the same per-job height/liner values, but they are
tuned from the Output tab or the schematic Tune sheet, and it draws no dimension rails, so
the Measurement-reference radios would be inert there; the Wear and Undercut instances
show only Line thickness and the shade checkboxes.

**Hardened writes everywhere**: every SAF export in the app goes through
`util/PdfSafExport.writeShaftPdfToUri` — a composer throw repaints the page as a valid
"PDF export failed" error page and still writes it, so a truncated/unopenable file is
never left behind; success-only follow-ups (auto-open, the first-PDF achievement) key off
its Boolean. The collision export gate (`exportPdfGate`) guards the schematic, runout,
wear, undercut, and consolidated surfaces alike.

---

# 5.7 "Shaft height" slider (per-job profile exaggeration)

`RunoutConfig.heightScale` (per-job, in the `.shaft` envelope; legacy files default to
100%) multiplies the solved profile scale on the **runout/consolidated sheets AND the
schematic** — one value behind every drawing output (slider on the Consolidated Output
tab and in the schematic preview's Tune sheet, both `ShaftHeightSlider`).

- Range 50%–300% (`PROFILE_HEIGHT_SCALE_MIN/MAX`).
- **100% = the default sizing curve** (`defaultShaftHeightPt`, `geom/ProfileCompression.kt`):
  the STANDARD anchors are **proportional** — 8" → 1", 6" → 3/4", 4" → 1/2" on paper,
  a line through the origin (the hand-sheet rule from the original rulered sketches;
  taller defaults read "chubby" on-device) — continuing past both anchors until the
  1.5" ceiling. `defaultVisualScale` feeds every composer solve and both slider surfaces —
  the runout/consolidated sheet maxes it with the width-fit and in-profile value
  demands; the schematic uses the curve alone. The flat 0.40 pt/mm
  `VISUAL_DIA_SCALE_PT_PER_MM` remains only as the degenerate-diameter fallback.
- **The anchor heights are settings** (Settings → Drawing → "Default drawing size";
  `PdfPrefs.curveLoHeightIn`/`curveHiHeightIn`, persisted app-wide, standard
  0.5"/1.0", settable 0.25"–1.5" in 1/16" steps): change what a 4" and an 8"
  shaft draw and the whole line re-derives — no code edit (a taller pair like
  0.75"/1.25" is a deliberate choice here). The anchor DIAMETERS stay fixed at 4"/8".
  An inverted pair (8" set below 4") flattens the line at the 4" value in the
  geometry — a larger shaft never draws smaller — and the Settings page warns inline.
  A "Standard" button restores the proportional pair.
- **Tapers may shrink but never equalize** (on-device direction: two very different
  taper lengths must never draw equal): tapers carry NO flat width floor — a flat
  floor equalizes unequal tapers when both clamp to it — and use a ratio-preserving
  fraction-of-true floor instead (`PROFILE_TAPER_MIN_FRAC_OF_TRUE` = 0.5, λ-fit like
  the liner raises, so the drawn height never yields to it; ratio preservation is
  structural — both tapers scale by the same factor at every squeeze). The SCHEMATIC
  composer additionally uses lean floors (`SCHEMATIC_MIN_THREAD_PT` 28 /
  `_BODY_RUN_PT` 40 / `_LINER_PT` 56) — its values live on dimension rails and
  callouts, so proportion wins there; the runout/consolidated sheet keeps the writable
  `PROFILE_MIN_*` floors for in-profile values.
- **Body runs may shrink but never equalize either — balance** (on-device report: "the
  liners are taking up way too much space… I can't tell that the span between the aft
  and mid liner is longer. There has to be some kind of balance"): the body gaps between
  features carry a ratio-preserving fraction-of-true floor of their own,
  `PROFILE_BODY_RUN_MIN_FRAC_OF_TRUE` = 0.35, and it joins the **same single λ pool** as
  the taper and liner raises (`walkSpans`/`buildCompressedProfileXMap`/`fracFitFactor`
  take it as `gapMinFracOfTrue`). Two consequences. (1) A liner raise can no longer
  consume the page: when the pool overflows, liners and body runs shrink *together*
  under one λ instead of the liners taking all the slack and leaving every gap clamped
  to its flat 64 pt floor — which is what equalized them. (2) Relative lengths always
  read **within every kind** — a 900 mm body run still draws 1.8× a 500 mm one, exactly
  as two unequal tapers keep their ratio, because a common λ scales them identically.
  The "the page affords liners ~N% of true length" readout
  (`estimatedLinerKeptFracOfTrue`) reports this shared λ, so it now settles lower than
  it did with fixed gap floors — that lower number is the balance, not a regression.
  Height precedence is untouched: `solveMaxProfileScale` stays frac-blind, so no body
  run (short of a keyway pin) ever lowers the drawn shaft.
- The **1.5" ceiling is absolute** (`PROFILE_MAX_SHAFT_HEIGHT_PT` = 108 pt): the drawn
  shaft never exceeds 1.5" on paper at any slider position — a short shaft whose
  width-fit would draw taller is capped too, keeps true proportion, and simply doesn't
  span the page (room for the dimension rails). The page budget caps everything.
  Pure arithmetic: `exaggeratedProfileScale` (`geom/ProfileCompression.kt`).
- Slider UX: selects the drawn height **by value in paper inches** — the track runs
  from the 50% height to 1.5" (or the shaft's 300% height when less), and the picked
  value converts back to the stored multiplier
  (`drawnShaftHeightPt`/`heightFracForDrawnHeight`, pure). Commits near the standard
  height snap to exactly 100% (`snappedHeightScale`); a "Standard (X″)" button restores
  the default.
- **Liner compression (per-job pair, same two surfaces)**: the measured components —
  tapers and liners — are what the sheets are about, so liners can be held proportional
  lengthwise. **The drawing height takes precedence; liner compression is secondary**
  (on-device direction): neither control ever changes the drawn shaft height. Checkbox
  "Keep liners proportional lengthwise" (`RunoutConfig.linersProportional`): liners hold
  true-scale width up to what the page affords at the selected height; the slider is
  disabled while checked. Slider "Liner compression" (`RunoutConfig.linerCompression`,
  0–100%, default 100%): how far liners may foreshorten below true scale — 100% = down
  to the 100 pt writable floor (historical behavior), 0% = not at all. Both feed the
  derived `linerMinFracOfTrue` → `ProfileFeatureSpan.minWidthFracOfTrue` (geom,
  unit-tested): a BEST-EFFORT width floor of `max(100pt, frac × true width)` — the
  scale solve ignores it entirely, and when the raised floors don't fit at the solved
  scale they shrink uniformly to fit (`fracFitFactor`); flat floors
  and keyway pins are untouched, and only keyway pins may still yield the height.
  Applies to the schematic (`composeShaftPdf(linerMinFracOfTrue)`) and the
  runout/consolidated sheets (from `config`); rides the `.shaft` envelope (additive,
  legacy default = free compression). The readout under the slider shows LIVE what
  liners actually keep — "Liners keep at least ~N% of true length. The drawn height
  never changes." (`estimatedLinerKeptFracOfTrue`, `ShaftHeightSlider.kt`,
  unit-tested).

---

---

# 6. PDF Rendering Invariants

1. Export is **single page** only.
2. No multi-page continuation.
3. No BOM tables.
4. **Round-stock display compression exists for long bodies** (this replaces an earlier "no
   display compression" claim, which is no longer true). `ShaftPdfComposer.drawBodiesCompressedCenterBreak()`
   triggers per-body when that body's on-paper length reaches `COMPRESS_TRIGGER_PT` (220 pt) —
   or when the compressed profile x-map squeezes it below a **user-set fraction of its true
   drawn width** (`breakForCompression`, `pdf/BreakSymbol.kt` — ONE predicate shared with the
   consolidated sheet's body loop and the footer's compression note, so the note and the drawn
   breaks can never disagree). The fraction is `PdfPrefs.sBreakThresholdFrac`, set in
   Settings → Drawing → **"Body S-break"** (slider, 5% steps, "Default (50%)" reset):
   **Never** (0) suppresses compression breaks entirely — all foreshortening stays hidden —
   and 100% breaks on any foreshortening at all ("why lock it in one way when different users
   may want different outputs" — on-device request). `COMPRESS_TRIGGER_PT` is deliberately
   NOT governed by the slider: a run that eats 220 pt of paper at true scale is not hidden
   compression, so it breaks at every setting. At the default half, milder foreshortening
   prints a plain outline — a run kept at half scale or better still reads honestly, and
   a break pair on a barely-squeezed run was noise (on-device report: a 6" run at ~74% of
   true carried the pair); the rails print true lengths either way. Whichever trigger
   fires first: the
   body is drawn as two shortened stubs, each capped with an S-curve "round-stock break" symbol
   (`pdf/BreakSymbol.kt`, `drawBreakEdge()`) instead of a straight end cap, so the drawing reads as
   a foreshortened cylindrical bar rather than a literal-length rectangle. The pair's gap and
   amplitude come from `breakPairLayout` (same file, unit-tested): the classic gap (≤ 20 pt,
   ≤ ¼ of the run) widens up to half the run when the glyph needs the room, then the amplitude
   flattens, so the two edges' curves always keep ≥ 1 pt of daylight and never overlap
   (on-device report). The footer prints an
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