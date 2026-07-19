# Liner Wear Areas — Build Log (2026-07-18)

**Branch:** `feat/liner-wear-areas` (uncommitted — Chris reviews on return)
**Spec:** `docs/LinerWearAreas_Proposal.md`. Chris approved starting the build while
away; the proposal's §10 open questions are answered below with reversible defaults.

## §10 decisions made in Chris's absence (all cheap to change)

1. **Readings per band → single `minDiaMm` for now.** Model can grow
   `readings: List<WearReading>` additively later without breaking files (the
   proposal's own recommendation).
2. **Reference edge → canonical liner-local AFT-edge storage, AFT-edge display.**
   Same canonical-AFT pattern as Liner geometry itself. Projecting the *display*
   through the liner's `authoredReference` is a small UI-only follow-up if wanted —
   stored data is unambiguous either way.
   **SUPERSEDED (see "Post-review changes" below):** Chris's spec wants entry against
   any of four reference points (AFT SET / FWD SET / Liner AFT / Liner FWD edge), not
   just AFT-edge display. Canonical storage stays liner-local AFT-edge mm exactly as
   decided here — only the *entry/display* side grew a genuine per-spot
   `authoredReference` (was previously assumed fixed at AFT-edge display).
3. **Bands on the main wear-PDF profile → yes** (thin hatched bands), plus detail
   strips — matches the proposal's target layout and helps orientation.
4. **Detail strip selection → auto** (every liner with ≥1 spot), max 3 strips/page
   with overflow page. A per-liner include toggle can be added later.
5. **Generalize to bodies → no, keep `linerId`.** Not actually cheap: auto-bodies
   are derived per-resolution and have **unstable IDs**, so `componentId` wear on
   bodies needs an identity story first. Liners have stable spec UUIDs. Revisit when
   body wear is really wanted.

## Phase status

| Phase | Scope | Status |
|---|---|---|
| 1 | Model + persistence + VM + tests | **done** — `model/WearSpot.kt` (new: `WearSpot`/`WearRecord`); `doc/ShaftDocCodec.kt` (additive `wear_record` field on `ShaftDocV1`/`Decoded`, no version bump, orphan filtering against `spec.liners` at decode time); `ui/viewmodel/ShaftViewModel.kt` (`_wearRecord` StateFlow + `addWearSpot`/`updateWearSpot`/`removeWearSpot`, wired into autosave `combine`, `restoreSnapshot`, `exportJson`, `importJson`, `newDocument`); `data/AutosaveManager.kt` (`SessionSnapshot.wearRecord`); `io/ShaftBackup.kt` needed no change (it copies raw file bytes, never re-encodes the envelope). 15 new tests (`persistence/WearRecordPersistenceTest.kt` x5, `data/AutosaveSnapshotWearRecordTest.kt` x2, `ui/viewmodel/ShaftViewModelWearSpotTest.kt` x8); full suite 431→446, all green. |
| 2 | Wear tab interactive canvas + liner tap + badges | **done** — `ui/screen/WearRoute.kt`: replaced the static "Phase 2 will add…" text with a live `ShaftLayout.compute` + `ShaftRenderer.draw` canvas against `resolvedComponents` (mirrors `RunoutRoute`'s preview canvas; no pinch-zoom — kept simple since this canvas only needs a single tap gesture, not pan/zoom). Liners get a faint primary-tint fill + border (tap affordance) and a small count badge above any liner with recorded spots (`drawLinerWearAffordances`, a `DrawScope` overlay pass after `ShaftRenderer.draw`). Tap hit-testing inverts `ShaftLayout.Result.xMmFromPx` then calls the new pure `pickLinerIdAtMm` (ties broken by nearer edge). "Tap a liner to inspect wear." hint text below the canvas. |
| 3 | Detail overlay (break-out liner, spot cards) | **done** — `ui/screen/LinerWearDetail.kt` (new): full-screen `LinerWearDetailOverlay` composable (not a nav destination), same shape as `PdfPreviewOverlay` — its own `BackHandler` + back-arrow top bar. Self-contained layout math (not `ShaftLayout`): liner drawn at `computeLinerDetailPxPerMm` scale (width-driven, height-capped so short liners don't explode), centered with ~24dp neighbor stubs on each side pulled from `resolvedComponents` (real edge at the liner, S-curve break — a Compose port of `pdf/BreakSymbol.kt`'s math, no pdf import — at the far end). Wear bands render as hatched/tinted rects at `clampWearBandToLiner` positions (clamped visually; stored data untouched) with a per-spot dimension rail (offset + length, formatted via `disp`/`abbr` in the active unit). Spot cards below use `NumericInputField` (commit-on-blur, tap-and-leave no-op) for Start/Length/Min-Ø plus a same-discipline Notes text field, a delete icon per card, and an "Add spot" button. |
| 4 | PDF: bands on profile + detail strips | **done** — `pdf/WearStripLayout.kt` (new, android-free pure math: liner/wear-spot grouping+pagination aft→fwd max 3/page, band clamping, vertical/horizontal strip layout, neighbor-diameter lookup, anchor-from-SET label reusing `LinerSpanBuilder`); `pdf/WearPdfComposer.kt` (`composeWearPdf` gains optional `wearRecord: WearRecord = WearRecord()` — all existing call sites unaffected; draws thin hatched wear bands on the main profile plus up to 3 broken-out detail strips below it, shrinking the main profile to make room; overflow beyond 3 liners on one page renders as a text note, since the composer only receives a single `PdfDocument.Page` — see `selectWearStripsForPage`'s KDoc for why true pagination would need a signature change). 27 new tests (`pdf/WearStripLayoutTest.kt`); full suite green, no regressions. |

New pure-math file `ui/screen/LinerWearMath.kt` (Phase 2/3: tap→liner selection incl. tie-break, wear-band clamping, liner-local→px mapping, detail-canvas scale) with `ui/screen/LinerWearMathTest.kt` (16 tests). Full suite 473→489 (446 baseline + Phase 4's 27 + these 16), all green.

(Statuses updated as phases land; nothing committed.)

## Final integration + state

All four phases complete. Coordinator wired the live `wearRecord` into both
`composeWearPdf` call sites in `WearRoute.kt` (export + preview) and added
`wearRecord` to the preview `LaunchedEffect` keys so the PDF preview re-renders as
spots change. Full suite: **489 tests, 0 failures** (431 baseline → +15 phase 1,
+16 phases 2/3, +27 phase 4).

**For Chris's review (beyond the §10 defaults above):**
- **>3 wear liners on the PDF** render as a "+N more liner(s)" note instead of a
  second page — `composeWearPdf` receives a single caller-created page, so true
  multi-page needs a signature change at the call sites. Say the word if you want it.
- Detail-view neighbor stubs replicate the S-break visual in Compose (no pdf import).
- Everything is uncommitted on `feat/liner-wear-areas`; review with `git diff main`.

## SVG review findings (2026-07-18)

A same-math SVG review of the detail strips (rendered from a Python port of
`WearStripLayout.kt`/`WearPdfComposer.kt`) found two rendering defects, both fixed:

1. **Uniform radius cap erased the liner-vs-shaft diameter step.** `rPxStrip` capped
   the liner's radius AND both neighbor stubs' radii independently to the strip's
   vertical budget (`rCap`) — so whenever the liner and a neighbor were BOTH over
   budget (the common case: an 8" liner over a 7" shaft, ~166pt raw radius vs a
   ~108pt-tall strip), both flattened to the identical capped radius and the visible
   OD step disappeared. Fixed with a new pure function,
   `computeWearStripRadii` (`pdf/WearStripLayout.kt`): when the largest raw radius
   among the liner + both neighbors exceeds the budget, ALL THREE scale by the SAME
   factor (`budget / largestRaw`) instead of each being clamped on its own — ratios
   (and therefore the visible step) survive the "zoom out." `WearPdfComposer.kt`'s
   `drawWearDetailStrip` now resolves both neighbor diameters up front and calls this
   once instead of three independent `rPxStrip` calls.
2. **Min-Ø label crowded the strip title.** The cylinder's top edge sat only ~6pt
   below the title baseline (an ad hoc `+6f` folded into `titleHeightPt` at the call
   site did double duty as both "title line height" and "gap before the cylinder") —
   so a min-Ø reading pinned to the cylinder's top edge landed just ~6pt under the
   title text, reading as overlapping/crowded. Fixed by adding an explicit
   `labelHeadroomPt` parameter (default `WEAR_STRIP_LABEL_HEADROOM_PT = 11f`) to
   `computeWearStripInnerLayout`, reserved as its own gap between the title line and
   `cylTop`; the composer now passes the title's bare text size (no fudge) and lets
   the function add the headroom. Confirmed in the regenerated SVG: clearance went
   from ~6pt to ~11pt.

Both fixes are pure/testable — 6 new tests added to `WearStripLayoutTest.kt`
(common-factor scaling with/without capping, ratio preservation, zero-budget
edge case, headroom reservation, headroom under a pathologically short strip),
27→33 tests in that file, full suite 489→495, all green. The regenerated review
SVG (`liner_wear_pdf_preview.svg` in scratch) shows the OD step and label
clearance now correctly resolved (markers ③/④ updated from flagged defects to
"FIXED" call-outs with the before/after numbers).

**Left for Chris's call (not touched by this pass):**
- (a) With 2+ strips on the page, the OAL dimension line's nominal 90pt gap above
  the shaft top (`WEAR_OAL_ABOVE_SHAFT_PT`) floor-clamps to as little as ~50pt in
  this example — the strips push the profile's vertical center upward, shrinking
  the room above it. Enforcing the full 90pt gap unconditionally would shrink the
  main profile further to compensate. Current behavior: clamp and let the gap
  shrink rather than fight the strips for room.
- (b) Very short bodies (e.g. a 50mm coupling stub) render as thin hairline slivers
  on the main profile — easy to miss or misread as a stray line. Pre-existing,
  not introduced by the wear-strip feature.

## Post-review changes (Chris's input spec, 2026-07-18)

Chris reviewed the built feature and specified two changes to wear-spot input,
implemented on top of the four phases above (still on `feat/liner-wear-areas`,
uncommitted):

1. **Four "Measure from" reference options.** `model/WearSpot.kt` gains
   `enum class WearSpotReference { LINER_AFT, LINER_FWD, AFT_SET, FWD_SET }` and an
   additive `authoredReference: WearSpotReference = WearSpotReference.LINER_AFT`
   field (no envelope version bump — verified round-trip, incl. old files without
   the key defaulting to `LINER_AFT`). Canonical storage is still liner-local
   AFT-edge mm; the reference only records how the Start value was authored so the
   card re-displays it the same way. Conversion lives as two pure, exactly-inverse
   functions in `ui/screen/LinerWearMath.kt`: `wearStartToCanonicalMm` and
   `canonicalToWearStartMm` — see `RunoutSheet.md`'s "Liner Wear Inspection (UI)"
   section for the per-reference formulas. AFT/FWD SET positions are computed once
   per overlay open via `geom/OalComputations.kt`'s `computeOalWindow` +
   `computeSetPositionsInMeasureSpace` (same calls `WearPdfComposer` already uses)
   and threaded down to each spot card. The UI adds a 2×2 `FilterChip` "Measure
   From" row per spot card (`LinerWearDetail.kt`'s `WearSpotCard`/
   `WearReferenceChip`, styled like `ComponentCarousel.kt`'s existing AFT/FWD
   chips); tapping a chip persists the reference immediately via the new
   `ShaftViewModel.updateWearSpotReference(id, reference)` setter (mirrors
   `updateLinerAuthoredReference`) without touching `startMm`/`lengthMm`.
2. **Blocking in-span validation.** A band's canonical span must lie entirely
   within `[0, linerLengthMm]` — checked at entry (both the Start field, after
   converting to canonical, and the Length field) via `NumericInputField`'s
   `validator`, so an out-of-span commit is rejected inline, the field reverts,
   and the model is never touched. Pure classifier: `wearSpotSpanIssue(...)` in
   `LinerWearMath.kt` (epsilon `1e-3mm`, boundary-exact bands accepted). Stale
   data — valid when recorded, now out-of-span because the liner was shortened
   afterward — is **not** retroactively blocked: the existing render clamp stays
   the safety net, and the card shows a warning icon + "Extends past liner end —
   re-measure" instead, driven by a separate non-blocking classifier,
   `isWearSpotStaleOverrun(...)`. `addWearSpot`'s default 25.4mm (1in) length is
   now clamped to the liner's own length so the default is never rejected on a
   tiny liner.

**Unrelated fix bundled in the same pass (device screenshot review):** the
Compose break-edge port's shaded "eye" was sitting on the wrong side — toward the
liner instead of out into the stub/void. Root cause: the `eyeAtTop` flag
convention this file copied from `ShaftPdfComposer`'s *centered compression
break* (two break edges facing a shared gap in the middle) doesn't apply as-is to
a *single far-end stub break* (void on one side only, material on the other) —
the mapping actually inverts between the two cases. Fixed by swapping
`eyeAtTop` in both `LinerWearDetail.kt`'s `drawBreakEdgeCompose` call sites and
`WearPdfComposer.kt`'s `drawWearDetailStrip` neighbor-stub break calls (its main
shaft-profile compression break is a real centered-gap case and was already
correct — left untouched). See `drawBreakEdgeCompose`'s KDoc for the full
geometric derivation.

Tests: `LinerWearMathTest` +15 (reference conversions ×4 incl. round-trip,
asymmetric-SET hand check, span validation incl. Chris's two rejection examples
and boundary-exact acceptance, stale-classifier agreement),
`ShaftViewModelWearSpotTest` +5 (tiny-liner length clamp, unchanged-liner default,
`updateWearSpotReference` ×3), `WearRecordPersistenceTest` +2 and
`AutosaveSnapshotWearRecordTest` +1 (authoredReference round-trip/default).
Full suite 495 → **518, all green**. `:app:assembleDebug` succeeds.

## Dimension rails on the wear-sheet detail strips (2026-07-18, polish pass)

Chris reviewed a real export and asked for proper dimension rails on the wear sheet's
per-liner detail strips, replacing the rudimentary per-spot arrow/text rows from Phase 4.
Spec: one chained dimension rail below the liner cylinder — standard rail convention
(witness lines from the cylinder/band edges, arrowed spans, centered labels) — covering,
in order: liner AFT edge → first band start, each band's length, inter-band gaps, and the
trailing remainder to the liner FWD edge. Zero-length spans omitted. Everything else
(strip title with the anchor-from-SET label, min-Ø labels near bands, main profile with no
rails) stays as Phase 4 left it.

**Rail math — replicated, not reused.** `pdf/render/PdfDimensionRenderer.kt` (the main
schematic's rail renderer) is built around the schematic's multi-tier DATUM/LOCAL rail
stacking (spans overlapping in x get assigned different rails, stacked ABOVE the shaft
outline). A wear strip's rail is a single flat chain of never-overlapping spans BELOW the
liner cylinder — mismatched enough on both the tiering model and the draw direction that
reusing the class would mean either bending its API to a shape it wasn't designed for or
duplicating most of its logic anyway. So the minimal shared idea — center a label on its
span when it fits, let it overhang centered when it doesn't, flip arrows outward when
cramped, bump a colliding label to a fallback row — was replicated as small pure functions
in `pdf/WearStripLayout.kt` instead:
- `buildWearStripRailSpans(linerLengthMm, clampedBands, unit)` walks the sorted, already
  render-clamped wear bands and returns the ordered `WearRailSpan` chain (mm, liner-local).
  Zero-length leading/trailing/gap spans are omitted; the chain still tiles
  `[0, linerLengthMm]` exactly since an omitted span had zero mm to contribute. Overlapping
  bands (legal — only the liner-bounds check is enforced at entry, not inter-spot overlap)
  have their effective start pulled forward to the running cursor so the chain never runs
  backward or double-counts the overlap.
- `layoutWearStripRail(spans, xAtStripMm, labelWidthPt, ...)` resolves that chain to
  on-page geometry: per-span label centering/clamping and inward-vs-outward arrow choice
  (same tests as `PdfDimensionRenderer`), plus a row-bump collision fallback (lowest free
  row wins) for when adjacent spans are too narrow for their own labels. `labelWidthPt` is
  a caller-supplied function (`Paint.measureText` in the composer) so this stays pure/JVM
  -testable like the rest of the file — no Android import.
- `WearPdfComposer.drawWearStripRail` does the actual `Canvas` drawing: witness lines from
  the cylinder bottom down to the rail line, an arrowed dimension line per chained span,
  and each label at whatever row `layoutWearStripRail` assigned (clamped to however many
  rows `computeWearStripInnerLayout` actually fit for this strip's height).

**Budget rework.** `computeWearStripInnerLayout` no longer takes a `spotCount` parameter —
the old contract multiplied the reserved row height by the number of wear spots (each spot
got its own text row); the rail is now always ONE chained line regardless of how many spans
it's divided into, so the reserved budget is fixed: `WEAR_RAIL_MAX_LABEL_ROWS` (2) stacked
label positions above the rail line. The function now returns `railY` (the rail line's y)
and `railLabelRows` (how many of the 2 budgeted rows actually fit, 0–2) instead of the old
`fittingRows`. Guarantee preserved: the cylinder shrinks first, and only once it's squeezed
to zero height does `railLabelRows` start dropping below the full budget — the rail line
itself always draws (even with 0 label rows, in a pathologically short strip), it just
loses room to place labels on it.

Tests: `WearStripLayoutTest` — 3 existing `computeWearStripInnerLayout` tests rewritten for
the new fixed-budget contract (ordinary strip gets the full 2-row budget; nothing fits when
the strip has no room; the cylinder squeezes to zero before the rail budget drops below its
max), 10 new tests for `buildWearStripRailSpans` (chain sums to the full liner length
exactly, contiguous chain, zero-length leading/trailing/gap spans omitted, a band clamped
entirely away contributes no span, overlapping bands don't double-count or run the chain
backward, labels use `formatLenDim`) and `layoutWearStripRail` (ordinary well-spaced spans
land on row 0 with inward arrows, narrow adjacent spans bump the colliding label to a
fallback row, an over-wide label centers on the span midpoint without being dropped, rail
geometry stays inside the strip's own x-bounds end to end using Chris's reviewed
liner-1 example). Full suite 518 → **527, all green**
(`:app:compileDebugKotlin` / `:app:testDebugUnitTest` / `:app:assembleDebug` all succeed).

**Same-math SVG review** (`scratchpad/liner_wear_pdf_preview.svg`, regenerated): both
strips (Liner 1, 2 recorded spots; Liner 2, 1 recorded spot) now show a clean chained rail
— e.g. Liner 1 reads `2.362" | 5.118" | 3.937" | 3.543" | 0.787"` end to end under the
cylinder, summing to the liner's full 400 mm (15.748") length. The narrow trailing 0.787"
span naturally exercised the outward-arrow fallback (label too wide for the span even
centered) without any dedicated test data forcing it — confirms the fallback triggers on
realistic, not just synthetic, inputs. No row-bump was needed in this particular dataset
(all bands wide enough); the row-bump fallback itself is covered by a dedicated synthetic
unit test instead. Nothing questionable visually — rail sits cleanly below the cylinder,
inside the strip bounds, doesn't crowd the min-Ø labels or the neighbor stubs.
