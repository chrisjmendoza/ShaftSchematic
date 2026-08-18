# Dual-Unit Stacking — Plan

**Status: BUILT** (2026-08-18) — all ten steps of §10, including the three prerequisites. The
shipped default is still INLINE; stacking is a user choice (`PdfPrefs.dualUnitLayout`,
Settings → Drawing → "Dual-unit layout" and both PDF options sheets). This document is kept as
the reasoning behind the build, not as a to-do list; the contracts it fed are `CLAUDE.md`
§"Mixed units and dual display are a DISPLAY AXIS" and `docs/PDF_EXPORT.md` §4.

**What the build learned that the plan did not know**
- §3.2's baseline anchoring was inverted in the first draft and corrected in review: `drawLabel`
  computes its baseline from the REAL paint ascent, so with inflated planner metrics the baseline
  lands on the FIRST line. The primary draws at it, the secondary one advance below, and the
  renderer's own arithmetic never changed.
- The wear strip's drawn row step was a SECOND, independent number (`dimText.textSize + 3`) that
  only happened to nest inside the 13 pt budgeted row. Both now come from `wearRailRowHeightPt`.
- P2 needed one rule the plan did not foresee: chained spans SHARE a boundary extension line, so
  "own" must be decided by x-position, not by span identity, or every chained inline value would
  be pushed off its centre by its neighbour's line.
- The consolidated sheet's rails were never dual-aware at all (they built their spans without
  `displayUnits`). Fixed here, since a stacked rail with no second term to stack would have been a
  silent no-op.
**Written:** 2026-08-18, from an on-device schematic preview of a real dual-unit sheet.
**Reviewed:** 2026-08-18 — corrections folded in (baseline anchoring in §3.2/§5, the wear
rail's independent drawn row step in §6, the ledger test's inequality in §9, own-extension
exclusion in P2).
**Scope:** the vertical (and horizontal) budget work needed to set a dual value as a
**two-line stack** — primary over secondary — wherever the app draws a dimension value.

---

## 1. Why stacking, from the evidence

One dual-unit schematic preview of a 133" shaft shows four distinct failures. They are the
requirements list.

**(a) Dual labels are too wide to seat in the break, so they fall back above the line.**
`133" [3378.200 mm]` seated inline (its span is the whole shaft). The two ~25" spans did
not: `25 9/16" [649.287 mm]` and `25 3/16" [639.763 mm]` both printed above their rails.
Inline dual roughly **doubles** a label's width, and the break costs
`labelWidth + 2·textPad + 2·arrowSize` — so spans that comfortably seated a single-unit
value get pushed to the fallback path. Every fallback rail then **lifts every rail above it
by one label band** (`DimensionRailLayout.lifts`), which is the real height cost: inline
dual buys its single line by spending label bands on lifts.

**(b) A fallback label gets struck through by an extension line.** In
`25 3/16" [639.763 mm]` the closing `mm]` is crossed by a vertical extension line. This is a
**bug independent of stacking**: `DimensionRailLayout.plan` treats placed labels and
horizontal *rail lines* as obstacles, but **not extension lines**. An extension line runs
from the object up to **its own** rail, so a rail-2 extension line passes straight through
rail 1's band — exactly where rail 1 parks a fallback label. Single-unit labels are narrow
enough to usually miss one; a dual label is wide enough to be hit. Stacking narrows the label
but cannot fix this — a narrower box can still sit on a vertical line.

**(c) The secondary prints at absurd precision, and inconsistently across one sheet.**
The rails show `[3378.200 mm]` and `[649.287 mm]` (3 decimals, trailing zeros included)
while the Ø callout on the same sheet shows `[279.4 mm]`. Cause: rails use `formatLenDim`
(`"%.3f mm"`) and callouts/footer use `formatLenWithUnit` (`"%.1f"`, trimmed). Three
decimals of millimeter on a converted secondary is not a measurement — it is conversion
noise, and it costs roughly 17 pt of label width per rail at 10 pt text.

**(d) The footer ellipsizes dual values away.** `KW: 1 3/4" [44.5 mm] ×3/4" [19 mm] ×
21 1/2" [546.1…` and `Thread: 5.25" [133.4 mm] × 4 TPI × 5 13/16" [147.6 m…` — the value the
sheet exists to communicate is replaced by `…`. The footer needs **word wrapping**, which is
a footer-height problem, not a stacking problem.

**The rejected alternative.** Knocking out a white box behind a floating label (the
"white-box the text" idea) would stop (b), but it erases part of an extension line — the
witness line that proves *what* was measured — and breaks the continuity of the rail it
crosses. The break-in-the-line convention is a *deliberate, arrowed* interruption; a halo
over a witness line is an accidental one. Halo stays a bounded last resort (§7), never the
primary mechanism.

---

## 2. The arithmetic: stacked is CHEAPER, not more expensive

This is the finding that makes the feature worth building. Estimates at 10 pt (the
schematic's `dimText`, Roboto advances; an instrumented test must confirm the exact numbers
— §9):

| Form | Example | Width | Height |
|---|---|---|---|
| Single unit | `25 9/16"` | ≈ 30 pt | 11.7 pt |
| Inline dual, today | `25 9/16" [649.287 mm]` | ≈ 101 pt | 11.7 pt |
| Inline dual + compact secondary (P1) | `25 9/16" [649.3 mm]` | ≈ 84 pt | 11.7 pt |
| **Stacked dual + compact secondary** | `25 9/16"` over `649.3 mm` | **≈ 45 pt** | **≈ 25 pt** |

Stacked width is `max(primary, secondary)`, not `primary + secondary + brackets` — a **~55%
narrowing**. Since inline eligibility is a *width* test, stacking puts values back in the
break: the break cost for that label falls from ≈ 115 pt to ≈ 59 pt. Each label restored to
the break **removes a fallback rail**, and each removed fallback rail **removes one label
band of lift from every rail above it**.

So the height ledger runs both ways:

- **Cost:** a label band grows from `11.7 + 6 = 17.7` pt to `25 + 6 = 31` pt, and the lane
  pitch must exceed the stack height (§6).
- **Refund:** each eliminated fallback refunds one band × (number of rails above it).

On the sheet in §1 (two of three rails floating), stacking plausibly nets to **zero or
negative** height. It is not a "spend more paper" feature; it is a "stop spending paper on
lifts" feature. The plan must therefore be *measured*, not assumed — §9's ledger test records
the rail-block height before and after on the same document.

---

## 3. What the current code already gives us for free

The 2026-08 rail work left the schematic and consolidated rails in a much better state than
the earlier stacking attempt found them:

1. **Every vertical decision in those rails derives from one value object** —
   `DimensionRailLayout.TextMetrics(ascent, descent, aboveDy, labelGap)`, with
   `height = descent − ascent` and `band = height + labelGap`. Nothing else in the planner
   hard-codes a text height.
2. **A taller label can be expressed entirely inside those metrics.** Inflate `ascent` by one
   line advance:
   `TextMetrics(ascent = fm.ascent − advance, descent = fm.descent, aboveDy = unchanged)`.
   Then:
   - `height` becomes the full stack, so `band`, `lifts`, `topLift` and the collision boxes
     all grow correctly with **no change to the planner**;
   - `drawLabel` computes `baseline = top − textPaint.fontMetrics.ascent` — the **real paint
     ascent**, not the planner's inflated one — so the baseline lands on the **top** line:
     the renderer draws the primary at `baseline` and the secondary at `baseline + advance`,
     and its arithmetic does not change at all;
   - the inline case (`top = y − height/2`) centers the whole stack on the rail line, so the
     rail passes through the break *between* the two values — the drafting form we want (open
     question in §5);
   - the fallback case (`top = y − aboveDy + ascent`) keeps the **bottom** line at exactly the
     clearance above the rail it always had — with the real-ascent baseline above, the
     secondary's baseline lands at `y − aboveDy`, precisely where the single-line label's
     baseline sits today; the stack grows upward into the band the lift already reserved.
3. **Both composers already size their vertical budget from `topLift`** —
   `ShaftPdfComposer`'s `computeTopY` fit loop (which shrinks `railGap`, then text size) and
   `RunoutPdfComposer`'s `railsBlockH` (off a prelim linear-map plan). A taller band
   propagates into both budgets with no new plumbing.
4. **`geom/WearDiaCalloutLayout.kt` already takes `labelTextHeight` + `rowGap` as parameters**
   and derives its band height from them (`labelsHeightPt`, `bandHeightPt`), so it absorbs a
   stacked label by being *called* differently.
5. **The fraction renderer establishes both the precedent and the discipline.**
   `util/FractionTextRenderer.kt` states the contract stacking now consciously breaks:
   *"Width is the only thing that moves… Nothing that budgets vertical space has to change."*
   Stacked dual is the app's **first height-moving label change**, so it inherits the other
   half of that contract instead: **measure and draw convert together at every site.**

What is *not* free: the strip rails (wear, undercut) budget label rows as fixed point
constants, the below-shaft Ø callouts step tiers off `textSize × 1.4`, and the footer has a
fixed block height with no wrapping. Those are §6.

---

## 4. Prerequisites — independently shippable, do these first

Each is a standalone improvement to the *shipped* inline dual, and each reduces the work
stacking has to do.

**P1 — Compact secondary precision.** A converted secondary is a courtesy value, not a
measurement: print mm at 1 decimal (trailing zeros trimmed) and inches in the
decimal/fraction form the primary formatters already use. Implement inside `composeDual` by
giving it a *secondary* formatter instead of reusing the primary's — `formatLenDim`'s
`"%.3f mm"` stays the rule for a mm **primary** (unchanged single-unit output) while the
secondary routes through the compact `formatLenWithUnit` path. Result: `133" [3378.2 mm]`,
`25 9/16" [649.3 mm]`, and one precision convention per sheet instead of two. About 17 pt
narrower per rail label, no layout change, pinned by extending `UnitFormatDualTest`.

**P2 — Extension lines join the collision space.** `DimensionRailLayout.plan` must treat the
vertical extension segments as obstacles (each rail's pair, from the object clearance line up
to that rail's own **lifted** `y` — constructible inside `plan` with no API change, since
lifts are computed before placement) and resolve against them with the existing
slide-then-bump order. A span's **own** extension pair must be excluded, the same way its own
rail line already is: in the label-wider-than-span overhang case the label legitimately
overlaps its own extensions, and including them would make that case unsolvable (it would
bump to `MAX_BUMPS` every time). This fixes §1(b) for single-unit sheets too — a real drawing
bug — so it is worth its own commit and its own unit test (a label whose only clear position
requires sliding past a neighbouring rail's extension line, plus an overhang label that must
NOT be evicted by its own).

**P3 — Footer word wrapping.** Replace the footer's `ellipsizeToWidth(..., rich = true)` with
a pure `wrapRichLines(line, paint, maxW): List<String>` beside `util/FractionText.kt`,
breaking on spaces **and** on the `×` separators that structure the KW/Thread lines, with a
hanging indent on continuation rows. Then:

- the column line counts become *post-wrap* counts;
- the printed footer must derive its line pitch the way the blank path already does
  (`min(textSize × FACTOR_MAX, (band − 10) / maxLines).coerceAtLeast(textSize × 1.35)`) —
  today the printed path uses a flat `textSize × FOOTER_LINE_FACTOR`, and a full AFT-taper
  column (8 lines × 16.2 pt = 130 pt) already exceeds `FOOTER_BLOCK_PT` (96 pt);
- `footerBlockPt` becomes content-derived with a cap, since the composer already computes it
  before the vertical budget and `INFO_GAP_PT` (72 pt) holds the slack to give.

**The footer does NOT stack.** Footer lines are prose-shaped spec text (`KW: a × b × c`), not
dimension labels on a rail; stacking each value there would multiply an already-overflowing
block. The footer gets P1 + P3 and keeps inline dual. That is the scope boundary.

---

## 5. The API change: a dual value stops being a `String`

Every draw site today receives a formatted `String` (`DimSpan.labelTop`, `WearRailSpan.label`,
the `DiaCallout` labels). A stack needs the two terms separately, and it must be
**impossible to measure one way and draw the other** — the rule the fraction pair already
enforces.

Proposed shape:

```kotlin
// util/DualLabel.kt  (pure; no Android, no pdf/ui dependency)
data class DualLabel(val primary: String, val secondary: String?) {
    val isStacked: Boolean get() = secondary != null
    fun inline(): String = if (secondary == null) primary else "$primary [$secondary]"
}
```

- `pdf/UnitFormat.kt` grows `*DualLabel` builders beside the existing `*Dual` string
  functions (which stay — the footer and every inline site still need them).
- `util/FractionTextRenderer.kt` grows the matching pair —
  `Paint.measureDualLabel(label): Float` (the `max` of the two rich widths) and
  `Canvas.drawDualLabel(label, cx, baseline, paint)` (primary at `baseline`, secondary at
  `baseline + advance` — matching `drawLabel`'s existing real-ascent baseline, §3.2) — so a
  stacked label is measured and drawn by one pairing, exactly like
  `measureRichText`/`drawRichText`.
- `DimSpan.labelTop: String` becomes `DimSpan.label: DualLabel` (a single-unit sheet is
  `DualLabel(text, null)`, byte-identical output). Same for `WearRailSpan.label`.
- **`advance` gets exactly one definition**, in the renderer:
  `advance = (descent − ascent) + leading`, with
  `leading = DUAL_STACK_LEADING_FRAC × textSize` (start at 0.15). The planner's inflated
  `ascent` and the draw site's baseline step must both read one `dualStackMetrics(paint)`
  helper or they will drift. Note the leading does double duty: in the inline case the rail
  stubs align with the inter-line gap (the stack centers on the rail y, which lands mid-
  leading), so `DUAL_STACK_LEADING_FRAC` is also the visual seam the line points into — it may
  want to grow toward 0.25–0.3 em once seen on paper. Tune it in one place.

**Where the mode lives.** A user choice, `PdfPrefs.dualUnitLayout: INLINE | STACKED`
(Settings → Drawing "Dual-unit layout", plus both PDF options sheets) — the same posture as
fraction style, arrow size and the S-break threshold. It is a *preference*, not document
state, so like `fractionStyle` it must be carried as an explicit **re-render key** in every
preview's render-inputs record (`PdfPreviewScreen`, `OutputRoute`, `RunoutRoute`) or that tab
keeps drawing the old layout. Unlike `fractionStyle` it should **not** use a process-wide
mirror: it changes layout, and layout inputs are threaded explicitly in this codebase.

**Uniformity is guaranteed, not assumed:** `dual_units` is sheet-wide, so when stacking is
active *every* dimension label on the sheet is a two-line stack — the planner needs exactly
one (inflated) `TextMetrics` per plan, never mixed label heights. Per-component unit
overrides change *which* unit is primary per label, not whether a label is dual.

**Open question for the first on-device look:** with an inline stacked value, the rail line
passes between the two terms (primary above the line, secondary below). The alternative is to
seat both terms above the line's center. Between them, "primary above / secondary below" is
the closer analogue of the dimension-over-tolerance form a machinist already reads, and it
keeps the stack's optical center on the rail — recommend building that first and looking at it
before adding a preference for the other.

---

## 6. Site-by-site budget

Stack height at a given text size ≈ `2 × lineHeight + leading` ≈ **2.13 ×** the single-line
height (Roboto: `lineHeight ≈ 1.171 em`).

| Site | Today's budget | Stacked need | Change required |
|---|---|---|---|
| **Schematic rails** (`ShaftPdfComposer`, `PdfDimensionRenderer`, `DimensionRailLayout`) | band `11.7 + 6 = 17.7` pt at 10 pt text; lane pitch `LANE_GAP_PT + 6 = 30`, floor `minRailGap = 10`; text floor 7 pt | band `31` pt; lane pitch must exceed the stack height (≈ 25 pt + line clearance ≈ **29 pt**) | Inflated-ascent `TextMetrics` (§3.2). **`minRailGap` must become metrics-derived** (`height + 2·LINE_HALF_CLEAR + slack`) — a flat 10 pt floor lets the fit loop shrink the pitch until neighbouring rail lines run through the stacks. With the floor raised, the loop's coping mechanism shifts to text shrink; consider ordering text-shrink **before** gap-shrink in stacked mode only. |
| **Consolidated rails** (`RunoutPdfComposer`) | `railsBlockH = RUNOUT_BASE_DIM_OFFSET_PT(22) + railGap(18)·(maxRail+1) + 8 + prelimRailLift` | same formula with `railGap ≥ 29` | `RUNOUT_RAIL_GAP_PT` 18 → metrics-derived in stacked mode (+11 pt per rail). `prelimRailLift` grows and shrinks automatically. The cost lands on `availableH` → the solved shaft height (already capped by `PROFILE_MAX_SHAFT_HEIGHT_PT` and the slider), which degrades gracefully. |
| **Wear strip rails** (`WearStripLayout`, `WearPdfComposer`) | budget row `WEAR_STRIP_ROW_HEIGHT_PT = 13`, `WEAR_RAIL_MAX_LABEL_ROWS = 2`; rail budget `2×13 + witness 9 = 35` pt per strip. **Caution:** the *drawn* row step is a separate value — `drawWearStripRail`'s local `rowStepPt = dimText.textSize + 3f` (= 11 pt at 8 pt text) — which only coincidentally nests inside the 13 pt budget today | row ≈ `21` pt → rail budget `51` pt (**+16 pt per strip**) | `rowHeightPt` is a parameter of `computeWearStripInnerLayout`, but passing a stacked value there is NOT enough: the drawn step and the budgeted row must become **one threaded value** (the undercut site already does this — `UNDERCUT_RAIL_ROW_HEIGHT_PT` feeds both its budget and `drawUndercutRail`'s stepping) or two stacked rows overprint inside a correctly-sized band. Degradation is already right: the cylinder shrinks first, then `railLabelRows` drops. **This is the tightest budget on any sheet** (2–3 strips at 108 pt preferred) and the likeliest trigger for §7's fallback. |
| **Wear Ø callout rows** (`geom/WearDiaCalloutLayout.kt`) | `labelTextHeight = diaText.textSize (8)`, `WEAR_DIA_ROW_GAP_PT = 3`, up to 2 rows | `labelTextHeight ≈ 18` → band **+20 pt per strip** | Parameters only, no engine change. Note the current call passes `textSize` (not the font line height) as the row height; the stacked value must be the real stack height or the rows touch. |
| **Undercut strip rails** (`UndercutStripLayout`) | `UNDERCUT_RAIL_ROW_HEIGHT_PT = 17`, same 2-row budget, `UNDERCUT_RAIL_EXTRA_HEADROOM_MAX_PT = 15` | row ≈ `21` (**+4 pt per row**) | Cheapest site — its row height is already generous. `planUndercutRailRows` reserves off `seatsInBreak`, and stacking *increases* how many spans seat in the break, so reserved rows may actually drop. |
| **Below-shaft Ø callouts** (`DiameterLeaderRenderer`, `geom/DiameterCalloutLayout.kt`) | `tierStep = textPaint.textSize × 1.4`; 2 tiers | `tierStep ≥ stackHeight + gap` (≈ 22 pt at 8 pt text) | `tierStep` becomes stack-aware. Depth below the shaft grows by about one line per tier; `INFO_GAP_PT` (72 pt) absorbs it, but check against the footer top. Widths here use plain `measureText` — correct today, since Ø values are decimal, but a stacked Ø label must move to the `DualLabel` pair. |
| **In-profile rotated values** (`RunoutPdfComposer.drawWornSections`, `drawDiaReadingsInProfile`, `geom/WornSectionMath.kt`) | rotated −90°: **string length consumes drawn diameter**, `lineHeight` consumes shaft-axis width; auto-fit by `fittedValueTextSize`, floor `WORN_VALUE_MIN_TEXT_PT = 6` | stacking **helps** — the band need drops from the whole inline string to `max(line)`, so values fit at a larger text size | The axes swap here: a stack costs `2 × lineHeight` **along the shaft** and saves band height. `wornValueBandHeightNeeded(labelLength, lineHeight)` needs a two-label variant, and the "group wider than the span" overhang flag must count the doubled axial width. |
| **Footer** (`ShaftPdfComposer`) | `FOOTER_BLOCK_PT = 96`, printed pitch `textSize × 1.35`, ellipsized | — | **Stays inline** (§4). Gets P1 + P3. |

---

## 7. Degradation: per SHEET, never per label

A sheet with some two-line and some one-line values reads as a mistake. So:

1. Each composer computes its stacked budget **before drawing** (all the sites above are
   already budget-first).
2. If that budget cannot be met without pushing a *structural* floor past its limit — the
   wear/undercut cylinder below a minimum drawn height, the schematic's 7 pt text floor, the
   consolidated shaft below the height the slider selected — the **whole sheet falls back to
   inline dual** and prints consistently.
3. Only after that, and only for a label with no horizontal escape, may a floating label use a
   bounded knockout halo (§1's rejected-as-primary option) — the same "best effort: an
   unresolvable label still prints" posture `plan` already takes.
4. The fallback must be **observable**: log it, and surface it in the tuning sheet's readout
   the way `estimatedLinerKeptFracOfTrue` surfaces liner compression. A silent fallback reads
   as "stacking doesn't work".

---

## 8. Out of scope

- **Runout bubbles** (`util/RunoutValueFormat.kt`) — a TIR reading inside a small circle; it
  carries no unit suffix by construction and is not a dimension label. Never stacks.
- **On-screen authoring overlays** (`LinerWearDetail.kt`, `UndercutDetail.kt`) — these call the
  **non-dual** `formatDiaWithUnit`, so the authoring canvases show one unit regardless of the
  document's dual flag. Worth deciding separately; not a stacking question.
- **Numeric entry fields** — always the document unit (`docs/contracts/ShaftScreen.md`).
- **Footer stacking** — §4.

---

## 9. Tests

- **Pure, JVM** (`geom/`): stacked `TextMetrics` yields `band = advance + lineHeight`;
  `lifts`/`topLift` scale with the taller band; a stacked label seats inline on a span that
  the same value's inline dual label could not; P2's extension-line obstacle forces the
  expected slide.
- **The ledger test** (§2's claim, and the one that justifies the feature): over a fixture set
  of real documents, compare the **total rail-block height** — `railGap·(maxRail+1) + topLift`
  (the `computeTopY` / `railsBlockH` quantity), stacked vs inline dual. Comparing `topLift`
  alone is the wrong inequality: stacked mode also widens `railGap`, so lifts can shrink while
  the block still grows. A failing fixture is the honest place to learn that stacking is not
  worth it for that shape of shaft.
- **Instrumented / Robolectric** (real `Paint`): the §2 width table, and a bitmap render
  asserting the two lines overlap neither each other, nor the rail line, nor an extension line
  — the same posture as `FractionTextRendererTest`, which exists precisely to fail when a
  raise breaks the vertical budget.
- **Golden-ish**: the §1 document composed at both layouts, recording the rail-block height and
  the count of fallback rails, so a regression in either is visible.
- **Degradation**: a 3-strip wear sheet asserts whole-sheet inline fallback, never a mixed
  sheet.

---

## 10. Sequencing

| Step | Work | Ships alone? |
|---|---|---|
| 1 | **P1** compact secondary precision | Yes — pure win on the shipped inline dual |
| 2 | **P2** extension lines as obstacles | Yes — fixes a real single-unit bug |
| 3 | **P3** footer wrap + content-derived footer height | Yes — fixes the `…` truncation |
| 4 | `DualLabel` + measure/draw pair + `dualStackMetrics` | No (plumbing only, no visual change) |
| 5 | Schematic rails stacked (incl. metrics-derived `minRailGap`) | Yes |
| 6 | Consolidated rails + in-profile rotated values | Yes |
| 7 | Wear + undercut strips, Ø callout tiers | Yes |
| 8 | `PdfPrefs.dualUnitLayout` + Settings/options chips + re-render keys | Last — the switch that exposes 5–7 |

Steps 1–3 are worth doing whether or not stacking is ever built: each fixes something visible
on the sheet in §1.

---

## Appendix — constants inventory

| Constant | File | Value | Stacking role |
|---|---|---|---|
| `LABEL_GAP` | `geom/DimensionRailLayout.kt` | 6 | `band = height + this` |
| `LINE_HALF_CLEAR` | same | 1 | rail-line obstacle thickness |
| `MAX_BUMPS` | same | 3 | bounded vertical escape |
| `DIM_BREAK_TEXT_PAD_PT` | `pdf/BlankFormText.kt` | 4 | break gap padding, both sides |
| `BLANK_DIM_GAP_PT` / `_MIN_PT` | same | 60 / 28 | write-in gap (blank drafts draw no text, so they never stack) |
| `LANE_GAP_PT` | `pdf/ShaftPdfComposer.kt` | 24 (+6 → 30 pitch) | must exceed the stack height |
| `BASE_DIM_OFFSET_PT`, `BAND_CLEAR_PT` | same | 24, 12 | first-rail offset above the shaft |
| `TEXT_PT` (dim text = −2) | same | 12 → 10 | stack ≈ 25 pt |
| `INFO_GAP_PT`, `FOOTER_BLOCK_PT` | same | 72, 96 | the slack available to the footer |
| `FOOTER_LINE_FACTOR(_BLANK)` | same | 1.35 / 2.2 | footer pitch, P3 |
| `RUNOUT_RAIL_GAP_PT`, `RUNOUT_BASE_DIM_OFFSET_PT`, `RUNOUT_DIM_TEXT_PT` | `pdf/RunoutPdfComposer.kt` | 18, 22, 8.5 | consolidated rails |
| `WORN_VALUE_MIN_TEXT_PT` | same | 6 | rotated-value auto-fit floor |
| `WEAR_STRIP_ROW_HEIGHT_PT` | `pdf/WearStripLayout.kt` | 13 | → ≈ 21 |
| `WEAR_RAIL_MAX_LABEL_ROWS`, `WEAR_RAIL_WITNESS_RUN_PT` | same | 2, 9 | rail budget per strip |
| `WEAR_STRIP_HEIGHT_PT`, `WEAR_STRIP_GAP_PT` | same | 108, 14 | the strip band those rows come out of |
| `WEAR_STRIP_LABEL_HEADROOM_PT` | same | 11 | cylinder → title gap |
| `WEAR_DIA_TEXT_PT`, `WEAR_DIA_ROW_GAP_PT` | `pdf/WearPdfComposer.kt` | 8, 3 | Ø callout rows |
| `UNDERCUT_RAIL_ROW_HEIGHT_PT`, `UNDERCUT_RAIL_EXTRA_HEADROOM_MAX_PT` | `pdf/UndercutStripLayout.kt` | 17, 15 | cheapest site |
| `MAX_TIERS`, `MIN_GAP` | `geom/DiameterCalloutLayout.kt` | 2, 4 | below-shaft Ø tiers |
| `tierStep` | `pdf/notes/DiameterLeaderRenderer.kt` | `textSize × 1.4` | must become stack-aware |
| `digitScale` | `util/FractionTextRenderer.kt` | 0.64 | a fraction stays inside one line's ascent/descent — unchanged by this work |
