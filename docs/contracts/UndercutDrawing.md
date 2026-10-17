# Undercut Drawing

Shipped contract for the Undercut Drawing tab/PDF — the shop's record of machined-below-surface
sections (weld-repair undercuts, cleanup cuts), documented as zoomed detail windows with chained
dimensions. The sixth reference-only feature, same posture as wear spots / pits / dia readings /
runout readings / coupler bolt slots (`CLAUDE.md`). Design rationale lives in
`docs/archive/UndercutDrawing_PLAN.md`; this file documents shipped, current behavior.

**Files:**
- `model/Undercut.kt` — `Undercut`, `UndercutRecord`, `UndercutReference`
- `geom/UndercutMath.kt` — conversion pair, validators, cluster windows, liner strips
  (`UndercutStrip`), hit-tests, constants
- `geom/SurfaceProfileMath.kt` — `SurfaceSeg`, outer-surface envelope, notch-profile geometry
- `geom/UndercutOverlayMath.kt` — the pure share between the tab and the overlay: reference
  resolution (`effectiveUndercutReference`, `undercutReferenceLinerFor`,
  `undercutDisplayedDistanceMm`, `undercutReferenceLabel`), the notch build pipeline
  (`UndercutNotch`, `buildUndercutNotches`), and `undercutSetPositions`
- `ui/resolved/SurfaceSegs.kt` — the one `resolvedComponents → SurfaceSeg` mapping every draw
  site shares
- `ui/screen/UndercutRoute.kt` — the tab: overview canvas, "Add undercut", blank-draft toggle,
  preview/print/export
- `ui/screen/UndercutDetail.kt` — `UndercutWindowDetailOverlay`, the full-screen zoomed window +
  cards
- `ui/screen/UndercutSharedDraw.kt` — what the tab and the overlay share but `geom/` cannot
  hold: the notch draw pass (`DrawScope.drawUndercutNotches`) and `linerSpansOf`
- `pdf/UndercutStripLayout.kt` — android-free pure layout for the PDF's per-cluster strips
- `pdf/UndercutPdfComposer.kt` — the document composer (`composeUndercutPdf`)

---

## Responsibilities

### UndercutRoute
- Render a live, tappable overview canvas (`ShaftLayout.compute` + `ShaftRenderer.draw` over
  `resolvedComponents`) with every undercut's notch cut into the profile and a faint tint +
  count badge over **every liner** (whether or not it holds cuts) and every bare-shaft
  **cluster window** — the wear-area tap idiom, with a strip (`geom/UndercutMath.kt`'s
  `UndercutStrip`) as the selectable area, not a bare window or a single component. No
  pinch-zoom on this canvas; the overlay owns zoom.
- **"Add undercut"** button: records a default section and opens its strip (see "Add default"
  below).
- **"Cut depth exaggeration" slider** (shown once at least one cut is recorded, directly under
  the canvas it restyles): 0 – `UNDERCUT_EXAGGERATION_MAX_FRAC` with a live "%" readout,
  committing continuously through `ShaftViewModel.setUndercutExaggeration` — the OAL field's
  live-update posture, not commit-on-blur (it is a `Slider`, not a `NumericInputField`). It
  drives `UndercutRecord.exaggerationFrac`; see "Drawn depth exaggeration" below.
- **"Recorded undercuts" list** below the canvas: a read-only summary + delete row per cut,
  aft → fwd — not an edit card (see "UI contract").
- Blank-draft (write-in) toggle, PDF Preview (`PdfPreviewOverlay` + `RunoutWearOptionsSheet`),
  Export (SAF `CreateDocument`), Print (`printShaftPdfPage`) — straight ports of the wear tab's
  flows, all calling `composeUndercutPdf`.

### UndercutWindowDetailOverlay (`UndercutDetail.kt`)
- Full-screen "zoom in" on one detail strip — a whole liner (plus overhang/pad) when the cuts
  live in one, or a padded bare-shaft cluster window otherwise: dimension rail above (chained
  run + strip total), the strip's real resolved profile with notches cut in, Ø callouts below.
  The canvas is **fixed** at the top; below it a **swipeable card carousel**, one page per cut,
  aft → fwd. An "Add undercut…" button between the two opens a draft-only page (see "UI
  contract"), the authoring entry point for a liner with no cuts yet.
- Pinch-to-zoom (0.5×–6×) + two-finger pan; taps invert the same transform so hit-testing
  always runs in untransformed canvas space.
- Cards edit a **local draft** that previews on the canvas and reaches the record only when
  **confirmed**. The previewed draft's notch draws **dashed** in the selection (primary) color —
  provisional, unmistakable against the liner and the settled cuts — and switches to the
  **error color** while its confirm check fails (out of shaft bounds, or partially overlapping
  another cut: the same `undercutConfirmIssue` the status pill reads, so the drawing and the
  pill never disagree). Confirming settles it into the normal solid
  outline. Cards are editable here only — there is no card on `UndercutRoute` itself and no
  carousel card / Add dialog anywhere (see "Contracts & Invariants").
- Saving lives on a **floating status pill** between the canvas and the carousel, and a draft
  **auto-confirms when its card is left** (blocked drafts ask first). Cards carry no
  Confirm/Cancel buttons — see "Saving: the status pill + leaving a card".

---

## Data model & coordinate rule

`model/Undercut.kt`:

```kotlin
enum class UndercutReference { AFT_SET, FWD_SET, LINER_AFT, LINER_FWD, PARENT_AFT, PARENT_FWD }

data class Undercut(
    val id: String = UUID.randomUUID().toString(),
    val startFromAftMm: Float = 0f,
    val lengthMm: Float = 0f,
    val diaMm: Float = 0f,
    val authoredReference: UndercutReference = UndercutReference.AFT_SET,
    val referenceLinerId: String = "",
    val note: String = "",
    val referenceCutId: String = "",
)

data class UndercutRecord(val undercuts: List<Undercut> = emptyList())
```

- **Canonical storage is shaft-space mm** (`startFromAftMm`, from the AFT face) — the same
  space as `Segment.startFromAftMm` and `computeSetPositionsInMeasureSpace`'s output (its
  `measureStartMm` is always `0.0`). Undercuts are **deliberately NOT keyed to a component**: a
  cut may sit inside a liner, cross a liner edge, or span several components.
- **No component key → no orphans, no decode pruning.** The only staleness is a span
  extending past the current shaft extent (OAL shrank) — a non-blocking card warning
  (`isUndercutStaleOverrun`) plus a render-layer clamp (`clampUndercutSpan`); the stored record
  is never mutated.
- **`authoredReference`** is display metadata only: which datum the "Distance" field is
  entered against — one of **six** references: `AFT_SET`/`FWD_SET` (measured from a taper's
  S.E.T.), `LINER_AFT`/`LINER_FWD` (measured from a reference liner's own AFT/FWD edge), the four
  mirroring `WearSpotReference`'s set, or `PARENT_AFT`/`PARENT_FWD` (measured from the AFT/FWD
  shoulder of the cut this one is NESTED in). Switching it re-projects the *displayed* value
  only; `startFromAftMm` never moves.
- **`referenceCutId`** names the `Undercut.id` a `PARENT_*` distance converts against — the
  parent cut (on-device request: a nested cut inside a relief that buried its liner's edges could
  be placed only from a S.E.T., while the printed nested rail row already dimensions it from the
  parent's shoulders). Display metadata only, never a geometry key, stored verbatim and never
  pruned at decode; empty for every other reference. A `PARENT_*` reference whose cut no longer
  resolves, or no longer **contains** this one (`undercutSpanContains`), displays as the **nearer
  S.E.T.** (`nearestSetReference` for the cut's own span — `effectiveUndercutReference` through
  `undercutReferenceParentSpan`); canonical never moves and the stored reference is never
  rewritten. Additive + defaulted: older files decode to `""`.
- **`referenceLinerId`** names the `Liner.id` the distance converts against when
  `authoredReference` is a `LINER_*` value — display metadata only, never a geometry key (the
  undercut still lives in shaft space and renders wherever it is regardless of this liner). If
  that liner is later deleted, the Distance field falls back to the `AFT_SET` projection for
  display (canonical untouched, and the stored reference is never rewritten behind the
  machinist's back — `effectiveUndercutReference` in `geom/UndercutOverlayMath.kt`). Empty for
  SET-authored undercuts. Selecting a SET chip clears it back to `""`.
- **Buried liner edge** (on-device request): a liner edge that some cut on the sheet runs
  *past* into the body — `cut.start < edge − ε && cut.end > edge + ε`, ε =
  `UNDERCUT_TOUCH_EPS_MM`, every cut counted, the one being edited included at its previewed
  span (`undercutLinerEdgeBuried`) — has no step left to measure from, so a `LINER_*` reference
  to that edge displays as the **nearer S.E.T.** for the cut's own span (`nearestSetReference`,
  the schematic's convention). Display-only, the deleted-liner fallback's posture: canonical
  never moves and the stored reference is never rewritten. A cut that merely ends at the edge, or
  trims the liner's end without crossing into the body, leaves a different Ø from the body next
  to it — that edge is still a datum.
- **Back-compat**: `LINER_AFT`/`LINER_FWD` and `PARENT_AFT`/`PARENT_FWD` are additive enum
  values — a document that uses one will not decode in an app build that predates them, the same
  rule every envelope enum carries. Files that stick to the older references are unaffected.
- **Golden rule**: `startFromAftMm`, `lengthMm`, `diaMm`, and `referenceLinerId` are
  round-tripped verbatim — no snap/round/derive ever rewrites a typed value, and
  `referenceLinerId` is never pruned at decode even when it names no liner in the document
  (the display fallback is a render-layer concern, not a decode-time one). `diaMm == 0` means
  "placed, not yet measured": drawn in the overlay (as a symbolic floor, never a real Ø),
  never printed.
- **Envelope storage**: `UndercutRecord` rides `ShaftDocCodec.ShaftDocV1.undercutRecord`
  (`@SerialName("undercut_record")`), a sibling of `wear_record`/`runout_readings`, additive +
  defaulted — no version bump. Plumbed through the same 7 sites as `runoutReadings`:
  `ShaftDocCodec` (both decode branches, no pruning), `AutosaveManager.SessionSnapshot`,
  `ShaftViewModel._undercutRecord` + `undercutRecord` accessor, `buildCurrentSnapshot`/autosave
  `combine`, `EditState` (undo slice) + `currentEditState`/`applyEditState` + the edit-history
  `combine`, `exportJson`/`importJson`/`newDocument`/`restoreSnapshot`. A session whose only
  content is undercuts is never mistaken for a factory-default session
  (`DraftRing.isDefaultSession`) in practice: the Undercut tab (and its "Add undercut" button)
  is only reachable once the shaft is "built" (≥1 component, non-zero OAL), and
  `isDefaultSession` already treats a non-zero OAL / non-empty spec as user content — so no
  separate undercut-specific check was needed there.

---

## Pure math

### `geom/UndercutMath.kt` — conversion pair, validation, clusters, hit-tests

- **Conversion pair** (exact algebraic inverses, shaft-global — no component-local term):
  - `undercutStartToCanonicalMm(reference, enteredMm, lengthMm, aftSetXMm, fwdSetXMm)`:
    `AFT_SET` → `aftSetXMm + enteredMm` (entered locates the AFT edge); `FWD_SET` →
    `fwdSetXMm − enteredMm − lengthMm` (entered locates the FWD edge).
  - `canonicalToUndercutStartMm(reference, canonicalStartMm, lengthMm, aftSetXMm, fwdSetXMm)` —
    the inverse, used to re-display the "Distance" field.
  - Both (and `undercutCanonicalForNewLength`) take defaulted `linerStartMm`/`linerEndMm` and
    `parentStartMm`/`parentEndMm`: `LINER_AFT` → `linerStart + entered`, `LINER_FWD` →
    `linerEnd − entered − length`, and the parent pair is exactly the liner formulas with the
    PARENT cut's span — `PARENT_AFT` → `parentStart + entered` (display `canonical − parentStart`),
    `PARENT_FWD` → `parentEnd − entered − length` (display `parentEnd − (canonical + length)`). A
    Length edit under `PARENT_FWD` therefore pins the cut's FWD end, as under `LINER_FWD`.
- **Liner extensions** (the card's Whole liner / Extend past liner view — see the overlay's
  Default section): `undercutLinerExtensionsMm(start, length, linerStart, linerEnd)` → `(aft, fwd)`
  overhang past each liner edge, each ≥ 0; `undercutSpanWithAftExtension(start, length,
  linerStart, ext)` / `undercutSpanWithFwdExtension(start, length, linerEnd, ext)` → the
  `(start, length)` after a typed extension, the opposite end pinned (negative `ext` = 0; `ext ==
  0` pulls an overhanging end back to the edge, leaves an inside end alone);
  `undercutIsWholeLiner(start, length, linerStart, linerEnd, eps)` — the span is the liner's own
  within ε (default `UNDERCUT_TOUCH_EPS_MM`, below); `maxUndercutAftExtensionMm(linerStart)` / `maxUndercutFwdExtensionMm(linerEnd, oal)` —
  the stock between the liner edge and that shaft end, ≥ 0; `clampUndercutExtension(typed, max)` →
  `UndercutExtensionClamp(appliedMm, limited)` (typed above max → max, limited; at or under →
  unchanged; negative → 0, not limited).
- **Buried liner edges**: `undercutLinerEdgeBuried(edgeMm, spans, eps = UNDERCUT_TOUCH_EPS_MM)` —
  true when some span strictly crosses the edge, more than ε past it on BOTH sides (ending at the
  edge, or covering the liner's end without crossing, is not buried);
  `buriedUndercutLinerEdgesMm(linerStart, linerEnd, spans)` — the buried edges of one liner, AFT
  first, the rail's split nodes. `effectiveUndercutReference(undercut, linerSpans, spans,
  aftSetXMm, fwdSetXMm)` (`geom/UndercutOverlayMath.kt`) applies them: a `LINER_*` reference to a
  buried edge resolves to `nearestSetReference` for the cut's own span; the default empty
  `spans` skips the check, so the two-argument form is the deleted-liner fallback alone.
- **Blocking confirm validation (span)** `undercutSpanIssue(canonicalStartMm, lengthMm, oalMm)`:
  length must be `> ε`, and `[start, start+length]` must lie in `[0, oalMm]` (ε = `1e-3` mm,
  boundary-exact accepted). It is **never a field validator**: every undercut field commits the
  typed value to the draft verbatim, and this check runs at **confirm** time inside
  `undercutConfirmIssue` (see "Undercut cards" below) — stated inline on the card and on the
  status pill, with the Keep editing / Discard prompt on leave. A field validator would revert
  the box to the previous value with only the rule beside it — a typed input modified on error
  with no notification (on-device report). The Ø field has no span check at all: a measurement is
  sacred (golden rule); an implausible Ø is a non-blocking card warning instead.
- **Blocking confirm validation** `undercutOverlapIssue(canonicalStartMm, lengthMm, otherSpans)`:
  a draft must be either fully **clear** of every other cut (disjoint; touching edge-to-edge is
  legal, within `UNDERCUT_TOUCH_EPS_MM` = 0.01 mm) or fully **nested** with it — inside another cut, or containing one. A *partial*
  intrusion is blocked: two partially overlapping undercuts are physically one cut and would
  double-dimension the chain rail. So is a span **identical** to another's (within ε) — that is
  one cut entered twice, not a cut inside a cut. One wording for both, because the fix is the
  same, and it **names the partner** (on-device report: the user could not tell which cut the
  draft overlapped): `undercutOverlapPartnerId(...)` returns the first partner's id, and
  `undercutOverlapMessage(position)` composes "Overlaps cut N — sit fully inside it or fully
  clear" when the partner has a page on this strip (N its 1-based page position, the card title's
  number), "Overlaps a cut outside this strip — sit fully inside it or fully clear" when it has
  none. `undercutConfirmIssue(draft, oalMm, otherSpans, pagePositionById)` is the one composition
  site, and the pill, the card's inline warning and the leave prompt all read its result, so the
  three always say the same thing. `UNDERCUT_PARTIAL_OVERLAP_MSG` = "Overlaps another cut — sit
  fully inside it or fully clear" stays as the generic wording for a caller with no page context
  (`undercutOverlapIssue`, `undercutConfirmIssue` without the map). All of them are short enough
  for the status pill's two lines on a phone. Checked
  only when **confirming** a drafted card (see "Undercut cards" below), against the clamped spans
  of every OTHER cut on the sheet. Confirm-time only, so nothing already stored is retroactively
  rejected — the `isUndercutStaleOverrun` posture; stored partial overlaps keep rendering as they
  always did.
- **Overlap bands** `undercutPartialOverlapsMm(draftStart, draftLength, otherSpans)` → one
  `(start, end)` interval `[max(starts), min(ends)]` per partner the draft partially overlaps, in
  `otherSpans` order — the SAME predicate `undercutOverlapIssue` blocks on (not clear, touching
  counts as clear; neither span contains the other; a duplicate span is a band over the whole
  span). Touching and nested partners contribute nothing. Feeds the overlay canvas's overlap band.
- **Touch slop** `UNDERCUT_TOUCH_EPS_MM` = 0.01 mm (0.0004"): the "clear" test of that one
  partial-overlap predicate, the default ε of **containment** (`undercutSpanContains`, so the
  containment forest, the confirm check and the overlap band share it), the nested shared-shoulder
  test and the butted-face test of the notch builder, and the default ε of `undercutIsWholeLiner`.
  The presets compute their edges in `Float` — "End at start of" as `other.start − L` then
  `start + L`, "Whole liner" as `end − start` then `start + L` — and at 8–16 m one ulp is about
  1e-3 mm, as large as the 1e-3 mm span ε, so a butted cut could read as overlapping and the Whole
  liner chip as unselected. Containment shares it because two tolerances leave a gap between them:
  a cut sitting on another's shoulder within drift (> 1e-3, < 0.01 mm) was neither clear nor
  contained, so it read as a PARTIAL overlap. No real overlap is 0.01 mm thin. The span ε still
  governs shaft bounds.
- **Edge-to-edge presets** (the card's two chip rows): `undercutStartAtEndOf(otherEnd)` →
  `otherEnd` ("Start at end of cut N": the draft's AFT end on the other cut's FWD end) and
  `undercutEndAtStartOf(otherStart, length)` → `otherStart − length` ("End at start of cut N":
  the draft's FWD end on the other cut's AFT end). Edge to edge, which the overlap check counts as
  clear; shaft bounds are the caller's `undercutSpanIssue` check (the card refuses an off-shaft
  result as "Not applied — <reason>", `undercutPresetRefusal` — a preset, so nothing typed is
  lost).
- **Containment forest** `undercutNestingForest(spans)` → per span, its nesting `level` and the
  id of the smallest span containing it (`parentId`). Containment = `undercutSpanContains`: the
  child inside **both** parent edges within `UNDERCUT_TOUCH_EPS_MM`, and the two not the same span
  (both edges coincident within it — a duplicate, still blocked). A **shared edge is
  legal nesting** — the shop machines the original relief and then deepens a corroded section of
  it that may run right up to the relief's own shoulder (on-device intent), and that must print
  exactly as separately-authored adjacent sections would. Ties (equal-width containers, which
  shared edges can produce) break to the FIRST in record order, so the forest is deterministic.
  Partial overlaps are TOLERATED, not repaired — neither span contains the other, so both stay
  top-level siblings. The forest drives the nested notch build, the drawn-floor stacking, the
  extra rail rows, and which cuts the deepest-depth pool counts.
- **Ø callout shelf anchor** `undercutCalloutAnchorMm(span, others)` /
  `undercutCalloutAnchorsMm(spans)` → where a cut's Ø leader leaves its own floor, in shaft-space
  mm: subtract every contained span from the cut's own, take the midpoint of the widest remaining
  segment (ties AFT-most), fall back to the span midpoint when nothing is left. Childless cuts
  are unchanged. See the strip callout rules below.
- **Stale classifier** `isUndercutStaleOverrun(startFromAftMm, lengthMm, oalMm)` — non-blocking;
  reuses `undercutSpanIssue` to detect a previously-valid record that no longer fits (OAL
  shrank). Card shows "Extends past shaft end — re-measure"; render clamps via
  `clampUndercutSpan`, which never mutates the stored `Undercut`.
- **Cluster windows** — the unit of zooming, `clusterUndercuts(spans, oalMm, gapMm =
  UNDERCUT_CLUSTER_GAP_MM, padMm = UNDERCUT_WINDOW_PAD_MM)`:
  1. sort clamped spans aft → fwd;
  2. merge spans whose gap is ≤ `UNDERCUT_CLUSTER_GAP_MM` (152.4 mm / 6 in);
  3. pad each cluster by `UNDERCUT_WINDOW_PAD_MM` (25.4 mm / 1 in) per side, clamped to
     `[0, oalMm]`; padded windows that still touch are defensively merged.

     Returns disjoint, sorted `UndercutWindow(startMm, endMm, undercutIds)` — consumed by the
     overview affordances, the detail overlay, and the PDF strips, so all three windowing
     decisions agree by construction.
- **Window-end break room** — `UNDERCUT_WINDOW_PAD_MM` is the **minimum** pad. A window end
  that is not the shaft's physical end draws an S-break whose amplitude scales with the drawn
  radius (`UNDERCUT_BREAK_AMP_FRAC_OF_R`, `geom/BreakGlyphMath.kt`), and on the detail canvas x
  and r share one scale, so the amplitude in shaft mm is `0.3 × Ø` — about 2.5 in on an 8.26 in
  shaft, far wider than the 1 in pad. Whenever a component face sat within the glyph's reach
  on the window's interior side, the S lobes were drawn across it (on-device report: a liner
  strip's AFT end landed 0.75 in inside a taper, and the break tangled with the taper face, a
  1/4 in body and the liner face). `undercutBreakRoomMm(diaMm)` =
  `Ø/2 · UNDERCUT_BREAK_AMP_FRAC_OF_R · (BREAK_EDGE_OUTWARD_REACH_FRAC +
  UNDERCUT_BREAK_CLEAR_FRAC_OF_AMP)` is the uniform stock a break needs between its line and
  the next face (glyph reach ≈ 0.433 × amp plus 0.25 × amp of daylight; the PDF caps its
  amplitude lower, so the same room suffices there). `withUndercutBreakRoom(startMm, endMm,
  segs, oalMm)` widens a draw range **outward only** until each non-physical end sits in a piece
  of the outer-surface envelope (`buildSurfaceEnvelope`, so a body under a liner never hides the
  liner's face) with that room before the piece's interior face; a piece too short to host the
  break (a stub body, a blend's sampled sub-pieces) is walked over to the next piece outward, and
  an end that reaches `0` / `oalMm` becomes the flat physical end. Room is sized off the piece's
  larger end Ø (conservative on a taper). Empty `segs` leaves the range unchanged; the rule is
  idempotent. Every strip-range builder takes `segs` (default empty, so callers without a
  profile are byte-identical) and applies it to the **draw range only** — chain datums never
  move. **Cuts are faces too** (on-device request: cuts run past a liner into the body crashed
  into the window-end S-break): the optional `cutSpans` (default empty, byte-identical) split the
  envelope at every cut shoulder, and a stretch a cut spans never hosts a break, so an end landing
  inside a cut, or within the room of a cut's shoulder on the interior side, moves outward past
  it. They are the STRIP's OWN cuts — its members, chained cuts included — never every cut on the
  sheet: a strip draws another strip's cut as plain stock, and walking its window past that cut
  would only widen overlapping draw ranges. The route (`buildUndercutStrips` per strip,
  `linerStripFor` with the liner's members), the detail overlay's preview range
  (`undercutPreviewDrawRange`, the strip's previewed spans) and the PDF strip's re-application
  after `computeUndercutStripDrawRange` (the strip's `undercutIds`) all pass them, so a free
  strip's draw range now also grows past its window when its pad is narrower than the room.
- **Hit-tests**: `pickUndercutWindowAt(xMm, windows)` (which window a tap landed in) and
  `pickUndercutAt(xMm, spans, padMm)` (which undercut inside an open window — inside-span
  candidates win over pad-only candidates; among several containing the tap the **innermost**
  (narrowest) wins, since a nested cut is the smaller target and the one drawn on top; remaining
  ties, and every pad-only hit, break to the nearer span edge).
- **Placeholder Ø** `effectiveNotchDiaMm(diaMm, minSurfaceDiaMm)`: a real Ø (`> 0`) is used
  verbatim; a placed-but-empty undercut (`diaMm == 0`) gets a symbolic shallow floor at
  `UNDERCUT_PLACEHOLDER_DEPTH_FRAC` (0.85) of the smallest local surface Ø over the span, so the
  section stays visible/tappable in the overlay. Display-only: never stored, never printed —
  see `buildUndercutDiaStations` below, which skips a `diaMm <= 0` undercut entirely on the PDF.
- **Drawn depth exaggeration** `normalizedNotchFloorDiaMm(diaMm, minSurfaceDiaMm,
  deepestDepthMm, exaggerationFrac)`: a real undercut removes 1/16"–1/2" from a shaft
  measured in whole inches — at true scale the notch is a hairline, so every notch draw site
  deepens the drawn floor (the hand-sheet convention: depth exaggerated, the printed Ø carries
  the real number). The exaggeration is **per sheet**, stored as
  `UndercutRecord.exaggerationFrac` and driven by the "Cut depth exaggeration" slider on the
  Undercut Drawing tab (0 – `UNDERCUT_EXAGGERATION_MAX_FRAC` = 0.25, live-committing through
  `ShaftViewModel.setUndercutExaggeration`; it lives in the record, not app prefs, so a
  document keeps the look it was authored with).

  The model is **normalized to the sheet's deepest cut**, computed once per compose by
  `deepestUndercutDepthMm(undercuts, segs, oalMm)` (Ø-reduction of the deepest **measured,
  top-level** cut; placeholders, cuts that removed nothing, and NESTED cuts contribute 0 — a
  child's depth is relative to its parent's floor, so pooling it from the base surface would
  hand the sheet a reference no cut draws against):

  ```
  share      = max(√(trueDepth / deepestDepthMm), UNDERCUT_MIN_SHARE_OF_EXAGGERATION)
  drawnDepth = minSurfaceDiaMm × exaggerationFrac × share
  ```

  So the deepest cut draws at exactly `exaggerationFrac` of its local surface Ø and shallower
  cuts scale relative to IT — with the ratio **square-root compressed** and floored at
  `UNDERCUT_MIN_SHARE_OF_EXAGGERATION` (0.25): a linear ratio let a shallow cut normalized
  against a much deeper one elsewhere on the shaft draw as a hairline (on-device report —
  two ~0.005"-deep cuts vanished beside a 0.05" cut in another liner). √ keeps
  deeper-draws-deeper ordering while shrinking the dynamic range; the floor guarantees every
  measured cut stays readable; a sheet whose worst cut is 1" deep still reads like one whose
  worst is 1/4" — the sheets-read-alike requirement. Rules: never shallower than reality
  (`drawnDepth ≥ trueDepth`, so a cut past the
  cap draws true); `exaggerationFrac == 0` is true scale; a placeholder (`diaMm == 0`) draws at
  `UNDERCUT_PLACEHOLDER_OF_EXAGGERATION` (0.5) of the sheet's exaggeration, never below the
  `UNDERCUT_PLACEHOLDER_MIN_DRAWN_FRAC` (0.04) visibility floor, and is excluded from the
  deepest-depth reference so an unmeasured cut can't squash the real ones.

  **Nested cuts stack.** A cut machined inside another is cut against its **parent's floor**,
  not the shaft surface: true floor `effectiveNotchDiaMm(childDia, parentTrueFloor)`, drawn floor
  `nestedNotchFloorDiaMm(childDia, parentTrueFloor, parentDrawnFloor, deepestDepthMm,
  exaggerationFrac)` — the exaggerated depth is computed RELATIVE to the parent's true floor and
  then subtracted from the parent's DRAWN floor. Two invariants: the stair is visible at **every**
  slider value (measuring relative to the parent floor is what stops
  `UNDERCUT_MIN_SHARE_OF_EXAGGERATION` flattening two shallow-from-the-base cuts into one step),
  and a child never draws shallower than true (`parentDrawn ≤ parentTrue` and
  `relDrawn ≥ relTrue`). Capped at `UNDERCUT_NESTED_MAX_DEPTH_FRAC` (0.75) of the parent's drawn
  floor unless the true relative depth demands deeper — truth beats prettiness — and floored above
  zero so every step has a floor line. Recursive: level 2 reads level 1's results.

  Region topology still comes from `notchProfiles` at the TRUE floor — a cut that never
  touched the neighboring stock must not draw into it; only the floor line and faces
  deepen. For a nested cut the topology runs against a one-segment local surface at the
  PARENT's true floor, so a child at or above that floor yields **no region at all** (nothing
  drawn; the card's non-blocking Ø warning is what says so).

  **A shared edge prints as ONE face.** Where a nested cut runs right up to its parent's own
  shoulder there is no material at the parent's floor at that station, so the face must run from
  the outer surface straight down to the child's floor — the exact silhouette two
  separately-authored adjacent sections give (relief floor / deeper floor / relief floor for a
  mid-span section; face / deeper floor / step / relief floor for a flush one). The builder does
  it with a **zero-width step point** at that end (`nestedSurfacePoints`, `SurfaceProfileMath`'s
  duplicated-x convention, carried recursively so a cut flush through two levels still reaches
  the shaft surface). Both draw sites already take a region's face height from its first/last
  surface point and draw faces AFTER the void, so the child's own full-height face covers the
  stroke-width sliver its void erased off the parent's face — no draw-site change, and the two
  sites stay identical. The step has zero axial width, so no fill area changes.

  **Butted sibling cuts share ONE step** (on-device report: two cuts placed edge to edge with the
  card's preset at different floors drew a hairline face up to the liner's full OD at the shared station — a
  sliver of full-size liner that is not there). Where another TOP-LEVEL cut's opposite end
  touches a top-level cut's end — `|other.start − this.end| ≤ UNDERCUT_TOUCH_EPS_MM` (0.01 mm, so
  a preset's Float drift still counts; a real gap does not) for its FWD end, mirrored for AFT —
  `resolveUndercutFloors` marks that end butted (`aftButted`/`fwdButted`) and sets its face top to
  the neighbour's **drawn** floor (the faces draw at the exaggerated depth; true floors are
  untouched). The builder (`buttedSurfacePoints`) adds the same zero-width step point the nested
  case uses, clamped to the surface standing at that end (never the span-wide minimum, so a cut
  overhanging onto smaller stock still steps up to its neighbour) and never below the cut's own
  drawn floor. So the deeper cut's face rises only to the shallower cut's floor, and the
  shallower cut's face there lands ON its own floor — a zero-height face the existing
  `NOTCH_FACE_MIN_STEP_PX` test in every draw site drops. Equal floors draw no face at all (one
  continuous floor). A run of three or more chains the rule pairwise; an end touching a liner
  edge, the shaft end, or nothing keeps the outer-surface face top. The rule runs before nested
  cuts resolve, so a child flush with a butted shoulder inherits the stepped face top. **Paint
  order**: each run of butted cuts paints shallowest drawn floor first, so the deeper cut — the
  one carrying the step face — paints last and the later cut's core erase cannot wipe half that
  face's stroke; cuts that touch nothing keep aft → fwd, and nested cuts still follow every
  top-level cut. Ø callout leaders anchor on the drawn floor; labels print the stored value.
  Display-only: canonical values and printed Ø are untouched (golden rule). Each notch region
  draws as a **step in the silhouette** — the hand-sketch convention: the void erases
  everything from the surface down to the floor (the void fill overdraws the *component's*
  surface stroke outward by one stroke width, so no ragged half-stroke survives across the
  mouth) and **nothing redraws over the mouth — the cut is OPEN at the surface, never closed
  by a lid**. The outline is a full-height **section face** at each region end (one vertical
  from top surface to bottom surface, like any machined diameter step, drawn only where that
  end's surface stands `NOTCH_FACE_MIN_STEP_PX` above the floor — a taper that has run down to
  the floor leaves no face) plus the floor lines across the span, mirrored. Each undercut
  thereby reads as its own reduced-Ø rectangle section seated between two faces — over the
  cut's span, only the undercut section exists (on-device report: a lid along the surface plus
  the surviving liner outline read as a white box pasted ON the liner instead of material
  removed FROM it — the "complete box" reading of the hand sketch was wrong; the sketch's
  rectangles are silhouette steps). Same construction in every draw site; the detail overlay's
  draft dash + status colour apply to the faces and floor, so a draft reads as a dashed step.

  **Grey liner, white cuts, light-grey section core** (on-device requests): a real detail strip
  **always** shades its liner span — the composer's `stripLinerFill`, not gated on
  `pdfPrefs.shadedLiners` (bodies and tapers stay pref-driven; the blank template's edges-only
  started strip draws no liner span, so it stays clear paper) — the notch voids stay pure white,
  and the section's remaining core (between the floor lines) fills one step **lighter** than the
  liner: erased to the sheet colour, then refilled at half the liner shade so the cut span reads
  distinct from the liner around it. Same tone in every draw site. **Unless the sheet is printed
  as line art**: `PdfPrefs.undercutLineArt` (below) drops every fill on the PDF, the always-shaded
  strip liner and the section core included, so "always shades its liner" reads "always, unless
  line art". The void erase runs either way — it removes the component's surface stroke across
  the mouth, which is construction, not a tone. Both
  canvases (route overview, detail overlay) paint onto a hard-coded white sheet, so their
  component fills are fixed ink colours rather than theme colours: a dark-theme tint
  (near-white `onSurface`/`tertiary`) would wash into the paper and leave the white voids nothing
  to read against. The
  voids stay pure white in every theme because the sheet itself is white in every theme — no
  dark-theme glare is introduced by the void that isn't already the sheet's.

  **On-screen shade styling — `util/UndercutStyle.kt`** (Settings → Preview Colors → Undercut
  Drawing): the two canvases take their fills from a persisted `UndercutStyle` —
  shade colour (Grey default / Bronze / Blue — fixed ink bases, never theme roles), shade
  intensity (Light / Standard / Dark), and **Line art** (the colour-removal mode: every fill
  fully transparent, white drawing with black outlines only; outlines/text stay `SheetInk`
  black — see `Appearance.md`). The alpha ladder hangs off `UNDERCUT_SECTION_FILL_ALPHA`
  (`geom/SurfaceProfileMath.kt`): liner = 2 × constant × intensity multiplier, section core and
  overview bodies/tapers = half the liner — so "core one step lighter than the liner" holds at
  every intensity, and the STANDARD/GREY default reproduces the historical fixed shades (liner
  ≈ the PDF `argb 40` weight, section = the constant exactly; pinned by `UndercutStyleTest`).
  `drawUndercutNotches` takes the core fill as its `sectionFillColor` parameter. **The PDF is
  deliberately not style-driven** — the printed drawing keeps the standard black ink, and no
  `UndercutStyle` value ever reaches a composer (same posture as preview colors never leaking
  into `ShaftPdfComposer`).

  **Print shading — `PdfPrefs.undercutLineArt`** (default `false`; Settings → PDF Export, and the
  undercut preview's PDF options sheet at the FOOT of the "Shade in Components" group, testTag
  `pdf_undercut_line_art`): the print side's own line-art switch, part of the drawing LOOK and
  therefore captured by drawing profiles. On, the undercut PDF draws **no shade fill anywhere** —
  `bodyFill`, `taperFill`, `linerFill`, `stripLinerFill` and the notch section core are all absent
  — and the sheet reads from the notch construction alone (void, full-height section faces, floor
  lines). Everything else is untouched: outlines, thread hatch, the coupler-slot cutout fill in
  `SimpleShaftProfile` (a reference-cutout marker, not shading), rails, text, and the void fill
  itself. ONE pure decision behind it, `undercutPdfFillPlan` (`pdf/UndercutPdfFillPlan.kt`,
  `UndercutPdfFillPlanTest`); the composer builds its paints from the plan and threads
  `sectionCoreFill` into `drawUndercutNotches`. The undercut document ONLY — the schematic, wear
  and runout composers never read it, which is why the row is offered on that one options sheet
  rather than on every one.

  It is **independent of the screen `UndercutStyle` line-art mode by design**: two settings with
  the same meaning on two surfaces, neither reading the other, because a style meant for the
  canvases must not leak into a composer. Turning one on does not turn the other on.

  **Liner outline — `PdfPrefs.undercutLinerOutline`** (default `true`; Settings → PDF Export
  beside the line-art row, and the undercut preview's PDF options sheet under it, testTag
  `pdf_undercut_liner_outline`, caption "Dotted outline of each liner's original edges and OD
  over the cuts"; captured by drawing profiles). On-device request: a cut split between the body
  and a liner left no sign of where the liner had been. A fine **dotted** retrace of each liner's
  original OD and end faces over the stretches the cuts ERASED — never over intact outline, so a
  liner no cut touches gets nothing. ONE pure builder, `linerOutlineSegments(liner, notches)` (`geom/LinerOutlineMath.kt`, `LinerOutlineMathTest`): the OD as the union of the notch
  spans clipped to the liner, split over the liner's OWN surface (`UndercutLinerOd.surface`, from
  `linerSurfaceSegs` — the reduced shoulder OD over a shoulder, modelled square), so a retraced
  stretch sits at the shoulder OD there and a stretch running across a shoulder step retraces the
  step; an end face only where a notch runs ACROSS that edge (a notch merely ending at the edge
  leaves the real face standing, and an edge no cut crosses keeps its real face), retraced as ONE
  full-height dotted line (`LinerOutlineEdge.FULL_FACE`) from the top OD to the bottom OD at that
  station — the liner's surface Ø at that end, the shoulder OD on a shouldered end — straight
  through the body cross-section, so the liner's actual end placement reads at a glance
  (on-device request). Neither the cut floors nor the stock's Ø just outside the edge bound it: a
  face trimmed to the seat was a few pixels tall on a seat a hair under the OD and read as
  missing (on-device report). Both draw sites stroke a `FULL_FACE` as a single line
  `cy − r(OD) → cy + r(OD)`; the OD stretches and shoulder steps stay mirrored pairs. Drawn AFTER the notch passes and before the Ø callouts
  and rails at every undercut site: the route overview and the detail overlay
  (`drawLinerOutlines`, `ui/screen/UndercutSharedDraw.kt` — `SheetInk.Outline` at
  `LINER_OUTLINE_CANVAS_ALPHA` 0.4, `dashPathEffect(2, 4)`, 1 dp) and the PDF main profile and
  detail strips (`drawLinerOutlinesPdf` — the dimension weight, grey, `DashPathEffect(1.5, 3)`).
  The canvases read the same pref (the route collects it and passes it to the overlay's
  `linerOutline`), and it is an Undercut-tab preview re-render key.

- **Strips — liner-anchored vs free windows.** The zoomed-view unit consumed by the overview
  affordances, the detail overlay, and the PDF is a sealed `UndercutStrip`, not a bare
  `UndercutWindow`:
  - `UndercutStrip.LinerStrip(linerId, linerStartMm, linerEndMm, drawStartMm, drawEndMm,
    chainStartMm, chainEndMm, undercutIds)` — the cuts live in a liner (or the liner was
    zoomed empty, to author into). `drawStartMm`/`drawEndMm` cover the **whole liner** plus any
    cut overhang past its edges, padded each side, so the liner's true edges and a sliver of
    neighboring stock are always visible (on-device report: a padded window with no visible
    liner edges printed/rendered as an anonymous grey slab). `chainStartMm`/`chainEndMm` are
    the liner's own edges extended only by overhang — **no pad** — so the rail's outer witness
    lines land on a real datum, leaving the pad between it and the break edge undimensioned.
  - `UndercutStrip.FreeStrip(window, drawStartMm, drawEndMm)` — bare-shaft cuts, no liner
    involved: the chain range is the padded `UndercutWindow` from `clusterUndercuts` (the
    original window-edge chain); the draw range defaults to the window and only grows outward
    past it by break room (`buildUndercutStrips`), so the extra stock prints undimensioned
    beyond the window edge and the window's pad spans keep their value.
  - `assignUndercutLiner(span, liners)` — the liner a cut belongs to for strip purposes: the
    one overlapping the largest share of the cut's span (`null` if none overlaps at all); an
    exact tie breaks to the AFT-most liner.
  - `assignUndercutLiners(spans, liners)` — every cut's liner for strip purposes, with
    **chaining** (on-device request): after the overlap-share rule, a bare-shaft cut touching
    (within `UNDERCUT_TOUCH_EPS_MM`), overlapping or nested with a cut assigned to liner L joins
    L too, repeated to a fixed point, so a run of cuts machined on from a liner's edge into the
    body stays on that liner's strip. A cut that overlaps a liner keeps its overlap-share liner;
    a bare-shaft cut reaching two liners' chains in one pass joins the AFT-most. Each pass reads
    the previous pass only, so the result does not depend on input order. `buildUndercutStrips`
    and the route's per-liner strip and cut counts all use it; the strip's chain range extends to
    the outermost chained shoulder like any other overhang.
  - `linerStripFor(liner, assignedSpans, oalMm, padMm, segs)` — builds one `LinerStrip` for a
    liner and its assigned cuts (`assignedSpans` may be empty — used to zoom an undercut-free
    liner for authoring, in which case the chain/draw range is just the liner's own span,
    padded). The padded draw range then gets break room against `segs`.
  - `undercutPreviewDrawRange(strip, previewSpans, oalMm, padMm, segs)` — the range the **detail
    overlay** actually renders: the strip's draw range **widened, never narrowed**, to hold the
    previewed spans (stored cuts with the live draft substituted). A draft edited past the
    strip's stored range — a cut overhanging a liner edge mid-edit (on-device report) — stays
    inside the drawing with the standard pad beyond it, the same range a confirmed overhang
    gets when the strip rebuilds on commit; never narrowing keeps the window stable while a
    draft shrinks a cut that had extended it. The widened range gets break room against `segs`
    (widening only, so the never-narrow rule holds). PDF strips don't need this (they only ever
    see confirmed cuts, which `linerStripFor`/`clusterUndercuts` already extend for).
  - `buildUndercutStrips(spans, liners, oalMm, gapMm, padMm, segs)` — every cut overlapping a
    liner, or chained to one that does (`assignUndercutLiners`), joins that liner's `LinerStrip`
    (one strip per liner holding ≥1 cut); the remaining
    bare-shaft cuts cluster into `FreeStrip`s via `clusterUndercuts`. Every strip's draw range
    gets break room against `segs` (the route, the detail overlay and the PDF composer all pass
    their real surface segs). Result sorted aft → fwd by
    draw start. This is the single source of truth the overview, the detail overlay, and the
    PDF composer all call, so the three cannot disagree about what one zoomed view covers.
  - `pickUndercutStripAt(xMm, strips)` — the strip containing a shaft-space tap, first-hit-wins
    aft → fwd order (liner strips can overlap a neighbor's pad; ties are visually
    indistinguishable at pad scale anyway).

### `geom/SurfaceProfileMath.kt` — outer surface + notch geometry

The novel geometry: a notch cuts against the **local outer surface**, which may step (liner
edges, body Ø changes) or slope (tapers) *within* the undercut span, since an undercut is not
bound to one component.

- `SurfaceSeg(startMm, endMm, diaStartMm, diaEndMm)` — one component's outer-surface
  contribution (linear; constant-Ø components use the same value at both ends). Kept neutral
  (no `ui.resolved` import) so `geom` stays free of UI/PDF dependencies.
- `outerDiaAt(segs, xMm)` — max over every seg covering `xMm` (outer material wins: a liner over
  a body is the surface there).
- `buildSurfaceEnvelope(segs, x0Mm, x1Mm)` — the upper envelope as sorted, non-overlapping
  linear `EnvelopePiece`s; candidate breakpoints are every clipped segment edge plus every
  pairwise crossing of overlapping linear Ø functions, so each returned piece has a unique
  winning segment.
- `minOuterDiaOver` / `maxOuterDiaOver` — envelope extrema over a span (piecewise linear, so
  extrema sit at piece ends).
- `notchProfiles(segs, x0Mm, x1Mm, floorDiaMm)` — the drawable notch region(s): everywhere the
  envelope Ø strictly exceeds `floorDiaMm`, material is removed between the surface and the
  floor. Each `NotchProfile` carries a `surface: List<SurfacePoint>` polyline across the region —
  including every envelope breakpoint and duplicated-x **step points**, so a notch crossing a
  liner edge shows the taller shoulder on the liner side rather than a single averaged slope.
  Where the surface is at or below the floor (the undercut Ø is too large, or the span runs off
  a liner onto smaller bare stock), that portion yields **no region** — nothing is drawn there,
  and the caller surfaces it as a non-blocking warning in the card ("Ø meets or exceeds shaft
  surface here", or "…the surrounding cut's floor here" for a nested cut, whose local surface IS
  its parent's floor), never a block or a rewritten value.

### `ui/resolved/SurfaceSegs.kt`

`surfaceSegsFrom(components: List<ResolvedComponent>)` — the single `resolved → SurfaceSeg`
mapping shared by every draw site: bodies/liners constant Ø, tapers linear, threads at their
major-Ø envelope, coupler bolt slots skipped (radial cutouts, not surface material). Both the
canvas overlay and the PDF composer call this (or, on the PDF side without a supplied resolved
list, an equivalent `surfaceSegsFromSpec` fallback built straight from `spec.bodies/tapers/
threads/liners`), so a notch is cut against an identical local surface everywhere.

---

## UI contract

### Tab gating
`EditorTab.UNDERCUT` ("Undercut Drawing") follows the same `isBuilt` gate as Runout/Wear
(`EditorTab.kt`, `ShaftEditorRoute.kt`, `EditorSidebar.kt`): disabled until the shaft has ≥1
component and a non-zero OAL. The sidebar's `ContentCut` icon sits after Wear Document in the
top nav group.

### Window width

The tab keys its skeleton on `currentWindowWidthClass()`; the policy behind the classes is
`docs/contracts/Adaptive.md`. Everything else in this section describes the COMPACT layout,
which is unchanged.

- **COMPACT** — one scrolling column: intro line, overview canvas + hint, "Add undercut",
  the exaggeration slider and the recorded-cut list, then the blank-draft switch, the gate
  message and `DocumentActionButtons`.
- **MEDIUM** — the same single column with a taller canvas box (at the phone's height a shaft
  drawn across a 600–840 dp column flattens into a rule). Nothing else moves.
- **EXPANDED** — two panes in a `Row` under the toolbar divider, a `VerticalDivider` between,
  each scrolling independently:
  - **left**, `testTag "sheet_pane_canvas"` — the overview canvas, then the intro line, the
    blank-draft switch, the gate message and `DocumentActionButtons`.
  - **right**, `testTag "sheet_pane_controls"` — the authoring side: "Add undercut", the cut
    depth exaggeration slider, and the recorded-cut list.

  The canvas is **pinned** out of the left pane's scroll: the exaggeration slider opposite it
  restyles what it draws, and a control whose effect scrolls off screen cannot be judged. The
  canvas sizes by PROPORTION there rather than by a fixed height, since the pane's width is the
  free variable.

The skeleton is the ONLY thing the width class changes. The tab composes its blocks **once**
(`canvasBlock`, `addButton`, `recordSection`, `printGroup`) and both branches call the same
values, so a phone layout and a tablet layout cannot drift; `sheetCanvasModifier` and
`SheetTwoPane` (`ui/screen/SheetPaneLayout.kt`) are shared with the Runout and Wear tabs. Every
`testTag` and the canvas's tap `pointerInput` survive unchanged.

The hamburger `IconButton` is omitted when `LocalSidebarPermanent.current` is true — there is
nothing to open when the sidebar is already laid out beside the tabs.

### Overview canvas (`UndercutRoute`)
- `ShaftLayout.compute` + `ShaftRenderer.draw` over `resolvedComponents`, then notches drawn
  first (they erase profile strokes inside each cut), then a faint primary tint + border over
  **every liner** (whether or not it holds cuts) and every bare-shaft **cluster window**, plus a
  small count badge above any target holding ≥ 1 cut (`drawUndercutStripAffordances`). Liners
  are unconditional tap targets — an empty liner can still be zoomed and authored into, matching
  the wear document's idiom (on-device report: tapping a liner that had no cut yet did nothing
  under the earlier windows-only behavior, leaving no way in).
- **Tap resolution order**, in shaft-space mm via `ShaftLayout.Result.xMmFromPx`:
  1. `pickUndercutStripAt(tapMm, strips)` — a strip claims the tap first (it covers its cuts
     plus context);
  2. failing that, `pickLinerIdAtMm` against every liner span — any liner, cut or not, opens as
     an empty `linerStripFor` strip to author in;
  3. no hit → no-op.

  A hit on a `LinerStrip` sets `anchorLinerId` (clearing `anchorUndercutId`); a hit on a
  `FreeStrip` sets `anchorUndercutId` to its first member (clearing `anchorLinerId`).
- **Two independent anchors, one live at a time, liner wins.** `UndercutRoute` holds
  `anchorLinerId: String?` and `anchorUndercutId: String?` (both `rememberSaveable`). Strips are
  re-derived (`buildUndercutStrips`) on every composition from the current record, so the
  anchors are ids, not indices. `activeStrip` resolves the liner anchor first (via
  `stripForLiner`, so an emptied liner strip doesn't slam shut mid-authoring), else the
  undercut anchor's owning strip, else `null`. An anchor that no longer resolves (undercut
  deleted, liner removed, shaft shrank past it) simply yields no overlay — never proactively
  cleared, since the record and the anchor can update in either order within a frame and
  clearing on a stale pass could close an overlay that was just opened.
- **The overlay's identity is fixed at open time.** Every opening goes through ONE route helper
  (`openOverlay(linerId, undercutId, selectId)`), which also sets `overlayKey` — `"liner:<id>"`
  for a liner strip, `"free:<id>"` for the cut a free strip was opened on — and passes it to the
  overlay as `stripKey`. Everything the overlay holds per strip (drafts, selection, the pending
  add, the leave prompt, the pager state, the zoom) is keyed on it. The free-strip anchor moves to
  a sibling when its own cut is deleted, and a key derived from the strip's members (the earlier
  lexicographic-minimum member id) changed on every add, delete, merge or split — silently
  dropping the drafts and the selection those edits came from.
- **"Add undercut"** button (global — see "Add default" below) sets `anchorUndercutId` to the
  new undercut's id (clearing `anchorLinerId`), opening its strip immediately on that cut's card.
- **"Recorded undercuts" list**, below the canvas (`UndercutListRow`, one row per cut, aft →
  fwd, only shown once ≥ 1 undercut exists): a distance summary under its *effective* reference
  (`"<distance> from <tag>"`, where a `LINER_*` reference reads `"<liner title> AFT/FWD edge"`
  via `effectiveUndercutReference`/`undercutReferenceLinerFor`), a second line
  (`"L … · Ø …"`, Ø shown as `"—"` while unmeasured), and a stale-overrun warning icon
  (`isUndercutStaleOverrun`) when applicable. **This is a read-only summary + delete row, not
  an edit card** — tapping the row body (`testTag "undercut_row_<id>"`) calls `anchorToUndercut`
  to open the cut's owning strip (where the real edit card lives, per "Undercut cards" below)
  **on that cut's own card** — the overlay's `initialSelectedId`, which seeds the selection and
  the pager's initial page (opening on page 0 showed the strip's aft-most cut instead); the
  trailing delete icon (`testTag "undercut_delete_<id>"`) removes the cut outright,
  confirm-free, without opening anything (no snackbar on this row; the tab's top-bar Undo takes it
  back, see "Undo").

### Add default
`vm.addUndercut(startFromAftMm, lengthMm, reference = AFT_SET, referenceLinerId = "")` records
a section at the AFT S.E.T. position (clamped to `[0, oalMm]`), length
`DEFAULT_UNDERCUT_LENGTH_MM` = 25.4 mm (1 in, clamped to the remaining shaft extent), Ø `0`
(unentered), and returns the new id. `AFT_SET` here is consistent with the nearest-SET rule,
not an exception to it — the seed sits AT the AFT SET, trivially the nearer datum; the
overlay's free-strip add, whose seed can land anywhere, picks by proximity
(`nearestSetReference`, see the overlay's Default section). The route's global "Add undercut" button calls it with the
SET-based defaults **immediately** (it has no card to draft into); the overlay's "Add undercut…"
button drafts first and calls it only on Confirm (below). Precision comes from the overlay's numeric fields
afterward, never from the tap — the wear posture: a tap only opens/selects, typing does the
real work.

### Undercut cards — the overlay carousel
**There is no card on `UndercutRoute` itself** — the "Recorded undercuts" list is a summary, not
a card (see above). Cards render exclusively inside `UndercutWindowDetailOverlay`, in a
**`HorizontalPager` carousel** below its fixed canvas — the `ComponentCarouselPager` presentation
(one card filling the width, a neighbour peek via `contentPadding`, swipe to change, pages keyed
by cut id so a card's state follows it), `testTag "undercut_card_pager"`. On-device report: a
vertical card stack forced scrolling between the drawing and the fields.

- **Order is aft → fwd** (proximity to a liner strip's AFT edge), keyed on **stored**
  `startFromAftMm` — plus the add draft at its fixed seed position. A card therefore moves only
  when a draft is **confirmed**; typing a new Distance never slides the card out from under the
  field being typed in (`buildUndercutPageIds`).
- **The title number is positional**: a stored cut's card reads `"Undercut N of M · aft → fwd"`
  (`undercutCardTitle`) — N is the cut's position in that aft → fwd page order, not an identity
  (the printed sheet carries no cut numbers), and the suffix puts the convention on the card
  (on-device report: a third cut added between two others read "Undercut 2 of 3" and was taken
  for the wrong cut). The pending card reads "New undercut". The Butt-against chips number cuts
  the same way.
- **Selection and the open page are one thing**: swiping a card highlights its notch on the
  canvas, and tapping a notch pages the carousel to that card. A tap that hits nothing keeps the
  current card (there is no "nothing selected" state while cards exist).
  - **Swipe → selection adopts SETTLED pages only**: the collector reads `currentPage` paired with
    `isScrollInProgress`, drops every emission while a scroll is in flight, maps the page to its
    cut id, and de-duplicates on the id. A half-way drag therefore selects nothing (and runs no
    leave sweep on a card the machinist never left), and a keyed reorder that carries the open
    card to a new index is not a swipe — adopting it undid a selection not yet paged to ("Add
    undercut" whose pending page sorts to the open card's index displaced that card one page on,
    and adopting it COMMITTED the default pending cut through the leave sweep). `settledPage` is
    not used: it refreshes only when a scroll ends, so a key-based reorder leaves it stale.
  - **Selection → page jumps with `scrollToPage`**, never `animateScrollToPage`, after one frame
    (`withFrameNanos`) so a keyed pager move from a reorder has landed, and only when the card at
    `currentPage` is not already the selection. An animated scroll reports every intermediate
    page as current; adopting one restarted the effect and cancelled the animation half-way
    between two cards (on-device report: a canvas tap two pages away, then the trash on the card
    in front, deleted a different cut).
  - **The trash acts only on the selected, settled card**: `onDelete` deletes when its card is
    `selectedId` and no scroll is in progress; otherwise it jumps back to the selected card and
    deletes nothing. A delete leaves the selection alone — clearing it ran the leave sweep over
    every other draft as if each had been left — and the heal (below) adopts the card the pager
    shows once the page goes.
  - **Heal**: whenever the page list or the record changes, a selection that is null or has no
    page on this strip (a cancelled add, a deleted cut, a cut an Undo or a confirmed edit moved to
    another strip, an initial selection that was never here) adopts the card at `currentPage`. The
    one exception is the cut Confirm just added, while the record has not published it yet
    (`awaitingRecordId`); once it is published it is either on a page here — and followed — or on
    another strip and healed away. The record is a key too, because a cut that lands on ANOTHER
    strip leaves this strip's page list unchanged.

**Draft editing.** Every control on a card writes to a local `UndercutDraft` (span, Ø, note,
reference + reference liner) — never straight to the record. Numeric fields keep the
commit-on-blur contract (`NumberField.md`); the commit lands in the draft. The canvas draws the
**selected** card's draft in place of its stored notch (`applyUndercutDraft` feeds the same
notch/rail/Ø-callout pipeline, so exaggeration and normalization behave exactly as for stored
cuts); every other card renders stored values.
- **Confirming** a draft requires it to differ from stored AND `undercutConfirmIssue` to be null —
  `undercutSpanIssue` (shaft bounds) then `undercutOverlapIssue` against the clamped spans of
  every OTHER cut **on the sheet** (`undercutOtherSpans`, the one pool the canvas, the card, the
  pill, and the leave check all measure against). It calls `updateUndercut` with the draft
  **verbatim** (golden rule), plus `updateUndercutReference` only when the chips actually moved
  (so a card merely displaying its `LINER_*`→`AFT_SET` fallback never rewrites the stored
  reference). There is exactly **one** confirm path (`confirmDraft`), taken by both the pill's tap
  and every auto-save-on-leave. Both first **flush the focused field** (`clearFocus`), so a value
  still in its box when the card is confirmed or left is part of what is confirmed — and only the
  field on the card being confirmed or LEFT (the overlay tracks the card holding focus,
  `focusedCardId`): a field just focused on the card the machinist is moving to keeps its focus.
  The decision then reads the draft **as it stands after the flush**: the pill's Confirm re-runs
  `undercutConfirmIssue` on it and records nothing while it fails (a typed Length that overlaps,
  leaves the shaft or is zero — the pill recomposes to Blocked with the reason), and the leave
  sweep evaluates every draft after the flush landed.
- **"Past the liner edge" for a flushed edit** is asked at overlay level, because the commit or
  close it holds up can take the card away (the add flow's card on Confirm, every card on Close):
  after the flush the overlay runs the card's own rule (`newLinerOverhangs`, pre-flush draft or
  baseline → flushed draft, against the strip's liner else the reference liner) and, when an end
  newly runs off, shows the SAME non-dismissable dialog (same strings and tags,
  `UndercutOverhangDialog`) before the Confirm, the sweep's commit of that card, or the close
  proceeds — on either answer the value is recorded as typed. On a stored card the answer is handed
  to the card (`overhangAnswers`), so "No" leaves its warning under the fields; the add flow's card
  does not outlive its confirm (the recorded cut opens a new card), so there "No" is an
  acknowledgement only and no warning carries over. The card's own question stays for ordinary
  field commits and stays quiet while the overlay flushes (`UndercutFlushGate`).
- **A draft's id is its map key and its card's page id.** The ONE writer (`writeDraft`) keys each
  entry by the page id and refuses a write from a card with no page — a disposed field's late
  blur, above all the add flow's card after its cut was recorded, which otherwise resurrected the
  pending draft as an invisible duplicate of the cut it had become and blocked every leave with
  "Overlaps cut 1" (on-device report). A PENDING draft with no pending page is dropped when the
  record publishes, the leave sweep skips any draft without a page, "Keep editing" never selects
  a card with no page, and the overlap pool (`undercutDraftPool`) never holds the cut of the card
  the draft is held under, so a reason can never name the card it is shown on. A refused write
  from a STORED card that carried an edit (a value typed into a card an Undo then took away)
  raises the `UNDERCUT_DISCARDED_DRAFT_MSG` snackbar, like the stale-draft drop; the add flow's
  card is exempt (its page goes only when it is confirmed, flush included, or cancelled).
- **No field on this card ever reverts a typed value.** Distance, Length, the two "Extend past
  liner" fields and Measured Ø carry no validator: anything that parses lands in the draft
  verbatim and the canvas previews it (the notch pass draws the render-clamped span, so a draft
  past the shaft end draws to the end). ALL span validity — shaft bounds, a positive length, no
  partial overlap — is confirm-time (`undercutConfirmIssue`), and so is every reason a value can
  be refused. Only text that does not parse is turned back, and the shared field names it
  (`"<typed> not applied — not a number"`, `NumberField.md`). The one clamp on a typed value — an
  extension past the shaft end — applies the maximum and says so under the field.
- The blocking reason shows inline on the card while it is dirty (under the fields, and on the
  pill); the non-blocking Ø-vs-surface warning still shows, read off the **draft** so it describes
  what the canvas is previewing. The stale-overrun warning shows only on an UNEDITED card — on a
  dirty draft the blocking reason already states the precise bound, and "re-measure" is advice
  about a recorded value, not one just typed.

**Saving: the status pill + leaving a card.** Cards have **no Confirm/Cancel buttons** — such a
row lived inside the card's own vertical scroll and was easy to miss and easy to forget
(on-device report). Two things replace it.

*The floating status pill* (`testTag "undercut_status_pill"`) sits in the band **between the
canvas and the carousel** — sharing the "← AFT / FWD →" row's empty middle, so it is always
visible whatever the card is scrolled to, and never covers the canvas's Ø callouts. It always
describes the **selected** card's draft, in three states:
- **Saved** — no dirty draft. Subtle `surfaceVariant` pill, green check, "Saved". Not a button.
- **Unsaved** — dirty and clear. `primaryContainer` pill, tappable **"Confirm change"**
  (`testTag "undercut_confirm"`) which confirms immediately and **stays on the card**, plus a
  separate small **✕ discard** (`testTag "undercut_cancel"`) that drops the draft back to stored
  values — including the reference chips — and discards the page outright for a pending add.
- **Blocked** — dirty with a non-null `undercutConfirmIssue`. `errorContainer` pill showing the
  blocking reason, **not** confirmable; the ✕ discard stays.

*Leaving the card saves it.* A dirty draft that clears `undercutConfirmIssue` is confirmed
automatically, through the identical `confirmDraft` path, when the machinist leaves its card:
swiping/paging to another card, tapping another notch, or closing the overlay (back arrow **and**
system back — both run `requestClose`). A **blocked** draft is never silently committed and never
silently dropped: leaving raises an AlertDialog whose text is the blocking reason, with
**"Keep editing"** (`testTag "undercut_leave_keep"` — returns to that card wherever the machinist
had got to, and cancels the close) and **"Discard"** (`testTag "undercut_leave_discard"` — drops
the draft and **resumes the sweep**, so a second unsettled draft gets its own question rather than
riding out on this answer, and only then does the navigation/close proceed). Dismissing the dialog
(tap-outside, back) is "Keep editing", so an edit is never lost to a stray tap. The pending add
page follows the same rules once it has been EDITED — clear → its add-confirm flow runs; blocked →
the same dialog, Discard dropping the page. An add page left **exactly as it was seeded** (the
default span, Ø 0, no note, the seeded reference) is **discarded** on leave — never committed and
never asked about: the machinist typed nothing, and committing the seeded default left stray
default-length cuts in the record (on-device report). The overlay keeps the seeded draft
(`pendingSeed`, whose start is also the pending page's fixed ordering key) to tell the two apart.
The pill's explicit **Confirm change** still records an untouched add — that is a deliberate tap.

The decision itself is the pure `undercutLeaveAction(draft, baseline, confirmIssue, pendingSeed)`
→ `NONE | COMMIT | DISCARD | PROMPT` (a pending card has no baseline, so it is dirty by
construction; a pending draft equal to `pendingSeed` is `DISCARD`, checked before the blocked
test). A `DISCARD` drops the draft and leaves the selection alone.
Mechanics that keep the carousel honest:
- The leave runs off a **selection change**, so swipes, notch taps, and the "Add undercut…"
  button all funnel through one check (`settleDraftsExcept(keepId = selectedId)`; closing sweeps
  with `keepId = null`).
- The sweep settles **every** draft but the selected one, not just the card just left: fields
  commit on **blur**, so a value typed and then swiped away from can land in its draft after that
  card is already behind the machinist. Settling only the outgoing card would strand such an edit
  — dirty, unsaved, and invisible, since the pill only ever states the selected card.
- The leave-commit passes `follow = false`, so `confirmDraft` does **not** re-select the confirmed
  cut: the carousel lands on the card the machinist was moving **to**, even when the commit
  reorders pages under it (the pager's id `key` carries the open page to its new index).
- "Keep editing" re-selects the blocked card **without** running leave handling on the card it was
  returning from (`skipLeaveOnce`) — otherwise stepping back off a pending add page would commit
  the add nobody asked for.

Each card:
- **"Measure From:"** chips (`WearChip`, shared with the wear overlay), in strict precedence
  order:
  - **"AFT S.E.T." | "FWD S.E.T."** always shown.
  - **"Liner AFT" | "Liner FWD"** shown only when a reference liner resolves
    (`undercutReferenceLinerFor` returns non-null), by precedence: (1) the undercut's own stored
    `referenceLinerId`, while it still resolves; (2) else the liner the open strip belongs to
    (`stripLiner`, when viewing from inside a `LinerStrip`); (3) else the liner overlapping the
    largest share of the cut (`assignUndercutLiner`). The stored liner wins so a cut authored
    against one liner keeps reading against it even when viewed from a neighbor's strip.
  - **"Parent AFT" | "Parent FWD"** (`testTag "undercut_ref_parent_aft"` / `"_fwd"`), a third
    row shown only while the draft sits **fully inside** a stored cut on the sheet — the parent is
    the smallest containing cut, the containment forest's rule (`undercutParentCandidate` over the
    sheet with this draft substituted, the draft itself excluded; containment within
    `UNDERCUT_TOUCH_EPS_MM`). The Distance then reads "Distance from Parent AFT (in)" /
    "Distance from Parent FWD (in)" against that cut's shoulders (`undercutReferenceParentSpan`).

  Tapping a SET chip sets the draft's reference and clears its `referenceLinerId` and
  `referenceCutId` to `""`; tapping a Liner chip sets the reference and the resolved liner's id
  (clearing `referenceCutId`); tapping a Parent chip sets the reference and the parent's id
  (`UndercutDraft.measuredFromParent`, clearing `referenceLinerId`). Either
  **re-projects the displayed Distance immediately** (canonical `startFromAftMm` never moves) but
  reaches `vm.updateUndercutReference(id, reference, referenceLinerId, referenceCutId)` only on
  **Confirm** (all three verbatim; a pending card's reference rides `vm.addUndercut`'s matching
  parameters), so Cancel reverts the chip too. A stored `LINER_*` reference whose liner has been
  deleted displays as if `AFT_SET` were selected (`effectiveUndercutReference`) until a chip is
  tapped again — the stored value is not silently rewritten. A `PARENT_*` reference whose parent
  is gone or no longer contains the draft's previewed span displays from the nearer S.E.T. the
  same way, live on the card (the baseline, `undercutDraftOf`, keeps the stored reference and id:
  containment depends on the previewed spans). Nothing new prints: the nested rail row
  (`buildNestedUndercutRailRows`) already dimensions a child from its forest parent's shoulders,
  whatever reference the Distance was typed against.

  **Buried edge** (see the data model): a Liner chip whose edge some cut on the sheet runs past
  into the body is **disabled** (`WearChip(enabled = false)` — dimmed, a tap does nothing;
  `testTag "undercut_ref_liner_aft"` / `"undercut_ref_liner_fwd"`), with the caption `"Liner AFT
  edge is cut into the body — measure from a S.E.T."` (FWD mirror, both when both) under the row
  (`testTag "undercut_ref_liner_aft_buried"` / `"_fwd_buried"`). The pool is every stored cut
  with this card's draft substituted, so the card's OWN typed Distance or Length crossing the
  edge counts the moment it lands — after the "Past the liner edge" question, whichever answer.
  The selected chip, the Distance label/value/entry conversion, and the Length edit's re-anchor
  all use the **effective** reference (`effectiveUndercutReference` over that pool), so the
  Distance re-reads from the nearer S.E.T. at once; canonical never moves, the draft's own
  reference is untouched (the baseline keeps the stored one — `undercutDraftOf` applies only the
  deleted-liner fallback, since burial depends on the previewed spans), and nothing reaches the
  record unless a chip is tapped and confirmed. The recorded-undercuts list row on the tab reads
  through the same rule over the stored spans.
- **Distance** field (`WearNum`, shared wrapper around `NumericInputField`): label shows the
  active reference (`undercutReferenceLabel`); the commit converts the entered value to
  canonical via `undercutStartToCanonicalMm` (passing the resolved reference liner's edges when
  applicable) and lands it in the draft **verbatim** — no validator. A Distance that puts the cut
  off the shaft is held in the draft, previewed, and blocked at confirm with its reason
  (`undercutSpanIssue` inside `undercutConfirmIssue`), exactly like an overlap — which is
  confirm-time for its own reason too: a cut is legitimately moved past a neighbour by two
  separate field edits.
- **Length** field: a length edit keeps the **authored Distance** fixed — the commit re-derives
  canonical `startFromAftMm` from the active reference at the new length
  (`undercutCanonicalForNewLength`, the conversion pair composed). Identity under an AFT-flavored
  reference; under a FWD-flavored one the cut's FWD end (the datum the Distance was authored
  against) stays pinned and the cut grows/shrinks AFT-ward. Committing the new length against
  the old canonical would rewrite the displayed Distance by the length delta (on-device report:
  Distance 5 / Length 12 under Liner FWD became Distance 7 after shortening to 10) — a
  golden-rule violation. The "canonical never moves" rule covers reference *switching* only.
  No validator: a length that leaves the shaft, or a zero length, lands in the draft verbatim and
  confirm blocks it ("Undercut extends past the shaft's FWD end" / "Length must be greater than
  zero").
- **Measured Ø** field: **no validator** — any parseable value ≥ 0 enters the draft verbatim
  (golden rule). Initial display is blank when `diaMm == 0` (unentered), else the formatted value.
  Because the underlying `NumericInputField` requires `parseValid` to accept the text to commit
  and an empty string does not parse, **a Ø typed once cannot be cleared back to blank/0 through
  this field** — the field reverts to the last committed value on blur instead of committing an
  empty edit, and says so ("Blank entry not applied — not a number"). (A known as-built
  limitation, not a design intent; deleting the whole undercut is the only way to remove a Ø
  today.)
- **Notes**: free text straight into the draft (Confirm/Cancel own persistence, so the numeric
  fields' capture-on-focus discipline buys nothing here).
- **Delete** icon (confirm-free, **immediate** — deletion is not drafted; the page goes with it).
  Absent on the add flow's pending card, which has nothing recorded to delete — the pill's ✕
  discards that page. The delete raises an **"Undercut deleted" snackbar with Undo** (Long
  duration, bottom of the overlay over the carousel, `testTag "undercut_delete_snackbar"`; a
  second delete replaces the first's offer). Its Undo calls `ShaftViewModel.restoreUndercut(u,
  index)` with the exact `Undercut` captured BEFORE the removal and its prior record index: the
  same object back verbatim (id and every stored value — golden rule), at that index or appended
  when it is out of range, and a **no-op when that id is already present** (a history Undo got
  there first). The carousel then returns to the restored card. On-device report: a cut deleted
  to restart it was lost with no way back.

- **Warnings** (non-blocking, both can show together): "Extends past shaft end — re-measure"
  (`isUndercutStaleOverrun`, unedited card only — see "Draft editing") and "Ø meets or exceeds shaft surface here" (`diaMm > 0` and `diaMm
  >=` the local surface Ø). For a draft that is **nested** inside another cut, the comparison is
  against the **surrounding cut's floor** and the wording becomes "Ø meets or exceeds the
  surrounding cut's floor here" — a nested cut removes material from its parent's floor, so the
  shaft surface is not the figure it can reach. Both terms come from `resolveUndercutFloors`
  (`surfaceDiaMm`, `nesting.parentId`), so the card and the notch build agree on what the cut is
  taken against.

### Undo
Both undercut surfaces carry **Undo / Redo** icons bound to the document's ONE session history
(`ShaftViewModel.canUndo`/`canRedo`/`undoEdit`/`redoEdit` — the same steps the Schematic tab's
history menu walks; `undercutRecord` rides `EditState`): the tab's toolbar (`testTag
"undercut_undo"`/`"undercut_redo"`, beside Save) and the overlay's top bar
(`"undercut_detail_undo"`/`"undercut_detail_redo"`, trailing end). There is no undercut-only
history. The delete snackbar deliberately restores the **cut, never a history step**: the history
coalesces records that land inside its window into one step, so stepping back from a delete could
also revert an edit made just before it.

An Undo can remove the cut whose card is showing, move it off the strip, or reorder the pages. The
overlay heals around it: a selection whose page vanished adopts the card the pager now shows
(the same healing a deleted or cancelled card gets), and a draft whose cut no longer has a page on
this strip is **dropped** — the one case a dirty draft goes without the leave prompt, because it
has nothing left to shadow (committing it would write over the undone state; prompting would
raise "can't be saved" for a card that no longer exists). It is never dropped **silently**: when
the dropped draft held an edit (it differs from the cut as this strip last held it), the overlay's
snackbar host shows "Unsaved changes to a removed cut were discarded" (`UNDERCUT_DISCARDED_DRAFT_MSG`,
Short); a draft equal to its baseline goes without a notice, since nothing typed was lost. A free
strip whose anchoring cut an Undo removes closes, like any anchor that stops resolving.

### Overlay "Add undercut…"
Between the canvas and the carousel: **"Add undercut in this liner"** on a `LinerStrip`
(`testTag "undercut_add_in_liner"` — the authoring entry point that makes an undercut-free liner
worth tapping on the overview at all), **"Add undercut here"** on a `FreeStrip`
(`testTag "undercut_add_in_strip"`). Disabled while a pending draft already exists — one at a
time.

It creates a **draft-only page**, not a record entry: the carousel gains a page at the correct
aft → fwd position, the canvas previews the cut, and `vm.addUndercut` runs only when the draft is
**confirmed** — by the pill or by leaving an EDITED page (followed by `updateUndercut` when the
draft also carries a Ø or a note — `addUndercut` lands only the span and reference). The pill's ✕
discards the page outright, so does "Discard" on a blocked leave, and so does leaving the page
untouched, so a discarded add leaves **no ghost cut** in the record.

Default section (`defaultUndercutSpan`): centred in the aft-most free gap of the strip's range
(the liner's span, else the window) wide enough for `DEFAULT_UNDERCUT_LENGTH_MM`, else centred in
the widest gap left and shortened to fit — so a fresh draft never opens already overlapping a
recorded cut, which would block confirming before a single value had been typed. Reference
`LINER_AFT` + that liner's id on a liner strip (so the very first typed Distance reads against
the datum the machinist is standing at); on a free strip the **nearer S.E.T.**
(`nearestSetReference`, `geom/UndercutMath.kt`) — a body-only cut has no liner edge, so the SETs
are its only datums, and the proximity rule is the same one `undercutAnchorFor` uses (it
delegates to `nearestSetReference` for the side) to anchor the printed bare-shaft strip's title,
so the card's default Distance and the sheet's anchor always read from the same SET. Midpoint
tie breaks AFT. A liner strip whose **AFT edge** a recorded cut already runs past into the body
(`undercutLinerEdgeBuried`) seeds the nearer S.E.T. for the default span instead of `LINER_AFT`,
with `referenceLinerId` `""` (the edge has no step left to measure from); a buried FWD edge alone
leaves the `LINER_AFT` seed. The route's global "Add undercut" seeds AT the AFT SET, where AFT_SET is
trivially the nearer datum.

Whole liner / Extend past liner (on-device request: a cut that covers the whole liner and runs on
into the adjacent body). Shown on a card only while it has a liner to act on, between the Measure
From chips and the Distance field. On a **liner strip** the block acts on the **strip's liner** —
the one the machinist opened and is looking at — never a different liner the cut's stored
reference happens to name; off a liner strip it falls back to the reference liner (the draft's
own, else the largest-share liner). The Measure From chips keep the reference liner: that is a
datum choice, not a span preset. **"Span: Whole liner"**
(`testTag "undercut_whole_liner"`, selected while `undercutIsWholeLiner` holds) is a one-tap
preset, applied only on the tap: the draft takes the liner's span and the `LINER_AFT` reference
for that liner, so Distance then reads 0. **"Extend past liner" AFT / FWD** (`undercut_extend_aft`
/ `undercut_extend_fwd`) show how far the span runs past each liner edge
(`undercutLinerExtensionsMm`, 0 for an end inside the liner). Typing an AFT value moves the AFT
end to `linerStart − ext` with the **FWD end pinned** (`undercutSpanWithAftExtension`); FWD is the
mirror with the **AFT end pinned** (`undercutSpanWithFwdExtension`). A typed 0 pulls an end that
overhangs the edge back to it and leaves an end inside the liner alone. Neither field changes the
reference. **An extension only runs into stock that is there** (on-device request): the most a side
can apply is the stock between the liner edge and that shaft end (`maxUndercutAftExtensionMm` = the
liner's AFT edge, `maxUndercutFwdExtensionMm` = `oalMm − linerEnd`), and a typed value past it is
not blocked — `clampUndercutExtension` applies the maximum and the card shows "Extension past the
shaft's AFT|FWD end isn't allowed — set to the maximum, …" under the row
(`undercut_extend_aft_note` / `undercut_extend_fwd_note`; it clears on that side's next un-limited
commit, and is shown only while the field still reads the applied maximum). The field re-seeds to
the applied value — a per-side counter re-keys it on every limited commit, since a repeat overrun
leaves the draft, and so `initialText`, unchanged. A side with no stock (the liner sits at that
shaft end) is disabled with the caption "Liner is at the shaft's AFT|FWD end — nothing to extend
into". Like every field on the card the two extension fields carry **no validator**: a clamped
span cannot leave the shaft, and a result with no length (an end pulled back past the other) lands
in the draft as computed and is blocked at confirm ("Length must be greater than zero"). The clamp is a **deliberate carve-out from the golden rule for a derived view field
only**: nothing stores the extension, and a cut past the shaft's end is a limit of the shaft, not a
measurement to keep; it applies at the moment of typing, and a stored span is never clamped after
the fact. **Nothing is stored**: the extensions are
a view of the stored shaft-space span against the authored liner edges — the "Distance from Liner
AFT" relation with the sign flipped — and nothing derived writes back into them. The golden rule
holds because a typed extension maps to canonical through the authored liner edge exactly as a
typed Distance does; the Distance and Length fields re-seed from the new draft (`NumericInputField`
keys its remembered text on `initialText`).

**"Past the liner edge" — an overhang typed by accident is asked about** (on-device request: the
machinist may not know the typed numbers run the cut off the liner, or a measurement may be
wrong). On a liner card (`stripLiner ?: refLiner`), a **Distance or Length commit** whose result
overhangs a liner edge it did not overhang before that commit (`newLinerOverhangs` in
`geom/UndercutMath.kt`: per end, `undercutLinerExtensionsMm` ≤ `UNDERCUT_TOUCH_EPS_MM` before and
> it after; an end already past that grows further, or one pulled back inside, is not new) opens
an AlertDialog (`undercut_overhang_dialog`) titled **"Past the liner edge"**: "This undercut will
run <amount> <unit> past the liner's AFT|FWD edge. Add that as an extension past the liner?" — one
line per end when both newly overhang, then "Add those as extensions past the liner?"
(`undercutOverhangQuestion`). Its two buttons are the ONLY way out (`DialogProperties(
dismissOnClickOutside = false, dismissOnBackPress = false)`): a tap outside would have to pick an
answer silently.
- **"Add extension"** (`undercut_overhang_accept`) — the overhang is intended; the Extend field
  already shows it. The end is recorded as acknowledged, and nothing more is shown.
- **"No"** (`undercut_overhang_decline`) — the end is flagged, and the card shows a standing
  warning under the Length field (`undercut_overhang_warning`): "Runs <amount> <unit> past the
  liner's AFT|FWD edge — double-check Distance and Length" (`undercutOverhangWarning`).
- **Nothing is rewritten either way**: the draft holds the typed value from the commit on, and the
  answer changes only what the card says. Confirm is never blocked — the geometry is legal.
- When that end's overhang returns to ≤ ε, its flag and its acknowledgement both clear, so the
  next overhang there is asked about afresh. The state (`UndercutLinerEnds`) is card-local,
  `remember(draft.id)`: it survives field re-seeds and resets when the card's cut changes
  identity — and, being card-local, it is not kept once the card leaves composition.
- Only Distance and Length ask. The Extend fields, Whole liner, the edge-to-edge presets and the Measure From
  chips never do: the first three are explicit span choices, and the chips change no span.

Edge-to-edge presets (on-device request: measuring each new cut's Distance from the previous
cut's end is tedious). Directly below the Span / Extend past liner block (below the Measure From
chips when the card has no reference liner), shown only while the page holds at least one OTHER
stored cut: TWO labelled rows, **"Start at end of:"** and **"End at start of:"**, each listing
EVERY other stored cut on the page as a chip **"Cut N"** (`undercutButtChipLabel`; N its 1-based
position in the page order, the card title's number), in page order, in a `FlowRow`
(`testTag "undercut_butt_after_<N>"` in the first row, `"undercut_butt_before_<N>"` in the
second). Both directions for every cut, chosen by the row and never by geometry: the single
"Butt against: After/Before cut N" row picked its side from the cuts' midpoints, and "After cut 5"
was read as AFT of cut 5 (on-device report). The pending card is never a target. A tap is a
one-tap preset, applied only on the tap and never shown selected: "Start at end of" sets the
canonical start to the other cut's FWD end (`UndercutDraft.startingAtEndOf` →
`undercutStartAtEndOf`), "End at start of" to its AFT end minus the draft's length
(`UndercutDraft.endingAtStartOf` → `undercutEndAtStartOf`). Length and reference are untouched, so
Distance re-seeds to the new position under the same datum. The result is validated with
`undercutSpanIssue`; a cut that would leave the shaft does **not** change the draft — the issue
shows as a card warning under the rows, worded "Not applied — <reason>" (`undercutPresetRefusal`,
`undercut_butt_issue`), until the next tap or draft change. Refusing a preset is the one refusal on
this card, and it loses nothing: no value was typed. A butted cut touches its neighbour, which the
overlap check counts as clear.

### Overlay canvas contents
Aft → fwd: a **dimension rail above** — the strip total on the upper line when ≥ 2 undercuts,
the chained run below it. The chain runs over the strip's **chain range**
(`strip.chainStartMm`/`chainEndMm`), not its draw range: on a `FreeStrip` the chain is the
window (the original window-edge chain; the draw range may extend past it by break room,
undimensioned); on a `LinerStrip` the chain anchors on the **liner's own edges**
(extended only by cut overhang), so the pad between a break edge and the liner edge is never
dimensioned. A **buried** liner edge (some previewed cut on the sheet runs past it,
`buriedUndercutLinerEdgesMm`) is an extra chain node inserted inside the span that crosses it —
the printed strip's `splitAtMm` rule, applied to the canvas's own stop list
(`undercutCanvasRailStops`). The chain's **labels stack** (on-device report: "I do want the
dimensions to stack"): `planUndercutCanvasRail` runs the stops' spans through the printed strip's
ONE label planner, `layoutWearStripRail` — a value centred on its span, slid inside it when it
fits, and a value that would overlap an already-placed one taking the next row up — and each
label lifts one text row per row it was assigned; the line and arrowheads keep the canvas's own
drawing (`drawUndercutDimSpan`). The plan is made before the canvas draws, off the same x map
(`computeUndercutWindowLayout` at the drawing Box's laid-out width; every input — stops, width,
window start and length, scale inputs, fraction style — is one `UndercutCanvasRailInputs` record
that is itself the `remember` key, so no input can drop out of the key), and the chain band grows by
one label row for every row past the first, so the reserved height and the drawn rows are one
number; the rail line keeps its distance off the profile and the extra rows stack above it.
Below the rail, the **strip profile** (every resolved component clipped to the draw
range, liners painted last so a liner over a body reads as the surface — matching the notch
math's max-wins envelope) with **notches** cut in, and **Ø callouts below** via the shared
`planDiaCallouts` engine (one station per undercut at its axial centre, leader to the notch
floor, label `formatDiaWithUnit`, or `"—"` for an unentered Ø — the overlay's own placeholder,
never printed). A tap selects an undercut (highlight rect) and **pages the carousel to its card**;
a tap that hits nothing leaves the current card alone. The rails, notches, and Ø callouts all
read the selected card's **draft**, so an in-progress edit is dimensioned live.

**Overlap band** (on-device report: "when it says it overlaps, I can't see the exact overlap").
While the previewed draft clears the shaft-bounds check (so an overlap is what blocks it), every interval from
`undercutPartialOverlapsMm` (against the same `undercutOtherSpans` pool the confirm check uses)
paints as a translucent band in the error colour (35% alpha) over the full profile height — the
selection highlight's height, `rPx(maxOdMm) + 6 px` either side of the centreline — with a thin
solid error-colour line at each band edge so a hairline overlap still shows. Z-order: after both
notch passes (settled cuts, then the dashed draft), before the selection highlight. Any other
issue (shaft bounds) draws no band.

**Strip ends**: an end that lands on the shaft's own extent (`x = 0` or `x = OAL`) draws a flat
edge; a threaded shaft end additionally gets the slanted thread-stub hatch
(`drawThreadStubHatch`, shared with the wear overlay — leaned and spaced by that thread's own
pitch at the app-wide slant and density, `geom/ThreadHatchMath.kt`). Any other end is a truncation and gets
the S-curve break (`drawBreakEdgeCompose`, AFT `eyeAtTop = true`, FWD `false`).

---

## PDF layout

`pdf/UndercutPdfComposer.kt` — landscape US Letter (792 × 612 pt), 36 pt margins.

### The strips own the page

A real undercut drawing shows **only the zoomed sections** — the feature's reference hand sketch
has no whole-shaft view at all — so a page carrying at least one detail strip draws **no main
profile and no SET-to-SET OAL line**. What replaces them is a single "← AFT / FWD →" orientation
row spanning the content width, directly under the header: the reader still has to know which way
the shaft runs, and that is the only whole-shaft fact the sheet needs. Everything the profile
band used to occupy goes to the strips (on-device report: the rails read cramped, and the profile
was not what a shop undercut drawing looks like).

```
┌─── header: job info line / "UNDERCUT RECORD" ─────────────────────────────┐
│   ← AFT                                                          FWD →    │
│   [detail strip: total rail / chained rail / profile / Ø / name + SET]    │
│   [detail strip: …]                                                       │
│   Notes: ______________________________________________________________   │
└───────────────────────────────────────────────────────────────────────────┘
```

Two pages have no cuts to strip, and they degrade in this order:

1. **No recorded cuts, ≥ 1 drawable liner → started liner strips.** The blank write-in template
   (whose record is dropped outright) and a non-blank export of an empty record produce the same
   page layout: one **started** strip per drawable liner (`linerStripFor(liner, emptyList(), …)`,
   the same call the overlay uses to zoom an undercut-free liner — so a started strip and a real
   one are the same figure at the same scale). Nothing is recorded on it — no notches, no rail
   spans or values, only the two chain-datum witness bars rising off the liner's own edges, with
   generous clear space above the cylinder for hand-drawn dimensions and below it for
   hand-written Ø values. The title is the write-in form: liner name, a rule for the distance,
   and `WEAR_BLANK_ANCHOR_SUFFIX`'s circle-one "FROM  AFT / FWD  S.E.T.". Every-liner coverage
   mirrors the wear blank's rule.

   **How much of the liner is printed depends on the page** (`linerSpanBlank = startedPage &&
   blankValues` in `drawUndercutDetailStrip`):
   - **Blank write-in template** — *starting geometry only*: the liner's two **vertical end
     faces** (full drawn height at its OD, outline weight), the **neighbour stock outboard of
     them** with its normal outline + fill, and the break edges. The liner's own span gets **no
     fill (whatever `pdfPrefs` shading says) and no top/bottom surface lines** — the middle is
     clear paper the machinist draws the liner surface and the undercuts onto. Implemented by
     calling `drawUndercutWindowProfile` twice over `[drawStart, linerStart]` and `[linerEnd,
     drawEnd]`: the liner edge sits at a window *end* in each, so the profile draws no cap there
     and the two end faces are the only vertical lines at those stations. The clip also keeps a
     body running *underneath* the liner (an unresolved `spec.bodies` span) out of the blank
     middle. On-device report: a fully outlined liner reads as a finished figure and invites
     sketching on top of printed lines.
   - **Non-blank export of an empty record** — the liner's zoomed profile drawn as usual (true
     edges, neighbour slivers, break edges, cylinder), just with nothing recorded on it. An
     export is a record of this shaft, so it keeps the liner fully drawn.
2. **No cuts and no drawable liner → the whole-shaft profile form.** The last fallback only:
   there is nothing to strip, and the page would otherwise be empty. This is the one layout that
   still draws the profile, the SET-to-SET OAL line (empty break in blank mode) and a direction
   row under the shaft.

### Vertical budget

With the profile gone, the drawing band (header rule + 16 pt → notes − 28 pt) is handed to the
strips minus a 22 pt orientation row, so a lone full-width strip owns ≈ 414 pt instead of the
≈ 190 pt cap it used to get. That height is spent on legibility, not on drawing a bigger shaft:

- `UNDERCUT_TOTAL_RAIL_BAND_PT` = 38 pt, split `UNDERCUT_TOTAL_RAIL_ABOVE_PT` = 18 pt above the
  total rail line (the row its value falls back to when it can't seat in a break) and 20 pt of
  clear separation down to the chained rail. Anything tighter and the total ("16″") reads as
  belonging to the chain below it — reported from the shop twice, at 14 pt and again at 22 pt.
- `UNDERCUT_RAIL_ROW_HEIGHT_PT` = 17 pt is the chained rail's fallback-label row pitch, used by
  **both** the reserved budget (`computeUndercutStripInnerLayout`) and the drawing
  (`drawUndercutRail`), so they cannot drift. A fallback label starts clear of the arrowheads
  straddling the line (`UC_RAIL_LABEL_GAP_PT` = 5 pt past the arrowhead — the head's barb
  spread is the same whichever way it points) — the narrow-gap value ("2″") used to sit on the
  rail.
- **Rows-used reservation** (`planUndercutRailRows`, 2026-08-01): the chain is resolved
  (`layoutWearStripRail`, pure horizontal geometry) *before* the vertical split, and the rail
  reserves only the fallback rows its labels actually landed on — minimum 1 row below so the rail
  line keeps clear air off the shaft, maximum the wear budget (`WEAR_RAIL_MAX_LABEL_ROWS` = 2). The
  fixed always-2-rows budget reserved crowding air most strips never used, floating the rail
  figures far above the surface they dimension (on-device report). A **started** strip keeps the
  full 2-row budget below: its band is the machinist's to hand-draw a chain into.
- **Fallback side follows the levels** (same date, on-device report): when a **total rail** sits
  above the chain, fallback values tuck BELOW the line (the total's figure owns the space above) —
  but when the chain is the sheet's **only label level** (single-cut cluster → no total span),
  a value pushed under the span line read as orphaned, so fallbacks stack ABOVE the line instead
  (`drawUndercutRail`'s `fallbackLabelAbove`, rows upward on the 17 pt pitch; the band above the
  rail is reserved via `chainAboveBandPt`, mutually exclusive with the total band; below keeps
  just the 1-row clear air). The wear strips reached the same conclusion later, from the same
  symptom, and moved their fallback rows above unconditionally (`RunoutSheet.md`, "Dimension
  rail") — the total-rail exception here stays, because there the band above the chain is the
  total rail's own witness run, and both fallback rows print over a page-white halo either way.
  `computeUndercutStripInnerLayout` therefore passes `witnessRunPt = 0` to
  `computeWearStripInnerLayout`: it places both of its rail lines itself off `cylTop`, and
  `belowRows` (never 0) already holds the clear air the wear strip's witness run holds.
- **Cylinder cap** — `max(UNDERCUT_CYL_MAX_FLOOR_PT, min(band × UNDERCUT_CYL_MAX_HEIGHT_FRAC,
  UNDERCUT_CYL_MAX_ABS_PT))`: 0.38 of the strip's band, never past 170 pt, never below the 96 pt
  floor (so a full-width strip draws ≈ 157 pt rather than the ≈ 207 pt a half-band fraction gave —
  the sections read oversized on the sheet, and their end breaks sprawled with them). Without a cap
  at all the delegation to `computeWearStripInnerLayout` would pour every reclaimed point into the
  drawn cylinder and print a slab. The surplus is spent in a fixed order: up to
  `UNDERCUT_RAIL_EXTRA_HEADROOM_MAX_PT` (15 pt — halved 2026-08-01 with the rows-used reservation,
  on-device report: rail figures floated far above the shaft) between the rail's label rows and
  the cylinder top, then the remainder split evenly — half between the Ø callout band and the
  title (capped at `UNDERCUT_CYL_BELOW_EXTRA_MAX_PT` = 96 pt, sized for the largest real surplus —
  the started write-in strip's — so the split stays even at the tighter cylinder cap instead of
  stacking the leftover at the top), half as air above the rails. An even split also
  holds the cylinder's centre line still whatever the cap is. A band short enough that the cylinder
  never reaches the cap (a 4-up grid cell) comes out bit-identical to the plain delegation.
- **Strip end breaks** — `amp = min(r × UNDERCUT_BREAK_AMP_FRAC_OF_R, UNDERCUT_BREAK_AMP_MAX_PT)`
  (0.6 × r, capped at 18 pt) in `drawUndercutWindowEnd`; the detail canvas's two window-end
  breaks read the same `UNDERCUT_BREAK_AMP_FRAC_OF_R` (uncapped), and so does the break-room
  math (`undercutBreakRoomMm`), so a retune moves the glyph and the room it gets together. The radius term keeps a small section's symbol legible; the absolute cap
  stops a tall strip's lobes from sprawling across the cylinder corners as heavy "ears". Peak
  lateral deviation is ≈ 0.29 × amp (the S) and ≈ 0.43 × amp (the return sweep), so ≈ 8 pt at the
  cap — inside the 16 pt `UNDERCUT_STRIP_EDGE_INSET_PT`. The profile-form page's mid-run
  compression break is **not** capped (one shaft, one scale, nothing to collide with).
- The strips-own-page banding calls `computeWearVerticalLayout`/`computeWearStripGridLayout` with
  the profile parameters **zeroed** (`minProfileHeightPt`/`preferredProfileHeightPt`/
  `profileToStripsGapPt` = 0) and no per-strip growth cap — the banding functions themselves are
  the wear sheet's, unchanged.

### Page parts

- **Header**: two centred lines (job info / `UNDERCUT_DOC_TITLE` = "UNDERCUT RECORD", a
  one-constant change). Blank mode gets a taller header with 5 edge-to-edge writing rules
  (Customer/Vessel/Job #/Date/Side).
- **OAL line** (profile-form fallback only): SET-to-SET arrows with witness lines, typed-OAL
  label seated in a break mid-span (falls back to continuous-line-plus-label-above when too
  short) — identical rule to the wear/runout sheets. Blank mode cuts an empty break.
- **Main profile** (profile-form fallback only): full resolved profile scaled SET-to-SET
  (`ptPerMm` derived from the SET-to-SET span) with every undercut's notch cut in at true
  position/scale — the same construction the strips draw zoomed, not a separate "marker style".
  No per-strip dimensions on it; the strips own the numbers.
- **Strip source — `buildUndercutStrips`**: the composer builds the same sealed `UndercutStrip`
  list the canvas uses (see "Pure math" above) — one `LinerStrip` per liner holding ≥ 1 cut
  (`docSpec.liners` filtered to `lengthMm > 0 && odMm > 0`, the drawable-liner filter
  `collectWearLinerGroups` also applies), plus one `FreeStrip` per bare-shaft cluster window for
  the leftover cuts. **Liner titles** come from `util/buildLinerTitleById(docSpec)` — the same
  shared custom-label-else-positional-default map the carousel, the wear sheet, and the runout
  sheet use, so a liner-anchored strip is identifiable at a glance and never drifts from the
  liner's name shown elsewhere.
- **Detail strips**: page mode from strip count (`determineUndercutPdfMode`, delegating to
  `determineWearPdfMode`) — counting **started** strips exactly like cut-derived ones: 1 →
  `COMBINED` (one full-width strip), 2+ → `GRID` (2-column, max 4 + "+N more" overflow note),
  0 → `PROFILE_FORM` (the fallback above). Vertical/horizontal strip banding reuses
  `computeWearVerticalLayout`/`computeWearStripGridLayout`/`computeWearStripHorizontalLayout`
  verbatim (count-driven, content-agnostic). Per strip:
  - the profile drawn over the strip's **draw range** (`strip.drawStartMm`/`drawEndMm`) at
    strip-local scale, with a break edge at each cut end (flat + thread hatch when a draw-range
    end coincides with a threaded physical shaft end, S-curve break otherwise) and notches cut
    in. On a `LinerStrip` the draw range is the **whole liner** plus any cut overhang, padded
    each side, so the liner's true edges are always visible and a neighbor sliver shows before
    the break edge (on-device report: a padded window with no visible liner edges printed as an
    anonymous grey slab). On a `FreeStrip` the draw range is the padded cluster window. Both carry
    break room (`withUndercutBreakRoom`, see the cluster-window rules above) and are then widened
    at layout time by `computeUndercutStripDrawRange` until the
    stock outside each **chain datum** prints at least `UNDERCUT_STRIP_MIN_PAD_PT` (24 pt) wide —
    the mm pad is scale-blind, so the same 1 in reads as ~36 pt beside a short liner on a
    full-width strip and under 9 pt in a grid cell, where the break edge sat all but against the
    liner's end face (on-device report: "oddly cramped"). It only ever widens, only outward, and
    stays clamped to `[0, oalMm]`, so no datum moves, no dimension changes, and a cut at a shaft
    end keeps its flat physical end. That widening is blind to the profile and can carry an end
    onto a short piece again, so `drawUndercutDetailStrip` runs `withUndercutBreakRoom` once
    more on its result before drawing;
  - **chained dimension rail** (`buildUndercutRailSpans`), run over the strip's **chain range**
    (`strip.chainStartMm`/`chainEndMm`) — **not** the draw range: chain AFT datum → first
    shoulder, each undercut's own length, each inter-cut gap, remainder to chain FWD datum. On a
    `FreeStrip` the chain range equals the cluster window (the original window-edge chain, so the
    pad spans **are labelled** — they locate the cluster inside its zoom window; any break room
    past the window edge is drawn but not dimensioned). On a
    `LinerStrip` the chain range is the liner's own edges (extended only by overhang), so the
    rail's outer witness lines land on a real datum and the **pad between it and the break edge
    is deliberately left undimensioned** — an arbitrary zoom margin is not a figure worth
    printing. Zero-length spans are omitted (never drawn as degenerate zero-width dims). Fed the
    strip's **TOP-LEVEL spans only**: the forward cursor pulls an overlapping span's start up to
    itself, which collapses a fully NESTED cut to zero width and drops it silently. (That absorb
    rule stays — it is what keeps a legacy partially-overlapping pair from double-counting;)
    **Buried liner edge node**: a `LinerStrip` passes its buried edges
    (`buriedUndercutLinerEdgesMm` over every live cut on the sheet) as `splitAtMm`, and a chained
    span strictly containing one is dimensioned as two spans meeting there — a cut overhanging
    the AFT edge reads `[shoulder → liner edge]` then `[liner edge → next node]`. Without the
    node, the overhang is dimensioned only from its own shoulder in the body, which nothing on
    the sheet ties to a datum; with it, the overhang reads against the liner edge's position and
    the strip title's liner-to-S.E.T. figure stays the locating datum. Lengths still sum to the
    chain; a station outside every span or on an existing node changes nothing; the default
    empty list is byte-identical. Nested rows are untouched;
  - **one extra chain row per nesting level ≥ 1**, stacked UNDER the level-0 chain
    (`buildNestedUndercutRailRows`) — chained dimensions run most-detailed nearest the part. Each
    level-k cut is chained against its **parent's own edges**: `parentStart → child`, `child`,
    `child → parentEnd`, with several children of one parent in sequence. Parents at one level are
    disjoint, so a level always lays out on ONE row. `planUndercutRailRows` reserves a row for each
    level's line plus that line's fallback labels (`UndercutRailRowPlan.nestedRows`, stepped by the
    one `undercutRailRowHeightPt` metric), so the strip budgets the height rather than drawing into
    the cylinder; the chain's own label rows stop where the nested rows begin;
  - **a second rail line above the chain — the strip total** (`buildUndercutTotalSpan`, first
    shoulder → last shoulder, over the TOP-LEVEL spans). **Returns `null` (nothing drawn, no
    reserved band) for a strip with fewer than two drawable top-level undercuts** — with exactly
    one, a total span would just restate that undercut's own length, already dimensioned on the
    chain below (a lone relief holding a nested cut included);
  - **Ø callouts below** via `planDiaCallouts`/`buildUndercutDiaStations` (leader to notch floor,
    `formatDiaWithUnit`, **no "Ø" prefix**); each leader leaves the **shelf anchor**
    `undercutCalloutAnchorMm` — the midpoint of the largest still-visible segment of that cut's
    OWN floor, found by subtracting from its span every other span contained in it (direct
    children suffice; deeper descendants lie inside them), ties to the AFT-most segment. A
    childless cut has one segment and so anchors at its span midpoint, exactly as it always did;
    a cut its children cover end to end draws no floor anywhere and falls back to the midpoint.
    The anchors are derived from **every drawable cut on the strip**, unmeasured ones included:
    a child with no Ø prints no callout but still replaces its parent's floor. Midpoint anchoring
    broke on nesting twice — a parent's midpoint can sit inside a child (the leader lands on the
    child's face or in the void), and concentric cuts share a midpoint outright, collapsing every
    leader onto ONE stem that fans out to the spread labels, a bird foot in which no leader
    identifies a floor (on-device report). Both draw sites take the same anchors
    (`UndercutPdfComposer` strips, `UndercutDetail`'s overlay); an undercut with `diaMm <= 0` is
    **skipped entirely** on the printed callouts (no placeholder for an unrecorded value) — its
    notch still draws (at the symbolic floor) and still gets dimensioned on the rail, so the
    section isn't lost from the sheet, only its Ø value is absent;
  - **title at the bottom** (`buildUndercutStripTitle(linerTitle, anchorLabel)`): a `LinerStrip`
    prints `"<liner title> — <dist> FROM AFT/FWD S.E.T."` (e.g. `"AFT Liner — 250.0 FROM AFT
    S.E.T."`) — the same `name — anchor` construction the wear sheet uses for that liner, so it
    reads identically wherever the liner is named. The distance is the **liner's own**
    edge-to-SET datum (`buildLinerAnchorLabel` + `linerAnchorForPdf`, the exact figure the
    schematic and wear sheet print for this liner), **never a cut's shoulder** — a title that
    names the liner but measures to a cut reads as the liner sitting at the cut's position
    (on-device report: a cut 11.5 in into a liner 20 in from the AFT SET printed the liner as
    31.5 in out; cuts are located on the chain rail, from the liner's edges). A `FreeStrip`
    has nothing to name (a bare-shaft span carries no shop identity) and prints the anchor
    alone: its S.E.T. is chosen by proximity (`undercutAnchorFor`: strip midpoint vs
    SET-to-SET midpoint), distance measured to the strip's **near** shoulder — reported as a
    magnitude even when the strip sits outboard of its chosen SET. Either way the title aligns
    toward its SET (left for AFT, right for FWD). A liner strip with zero drawable cuts (every
    assigned span clamped away) still prints the liner name and its anchor. Blank mode: a
    writing rule + both directions printed for the machinist to circle one, always
    left-aligned (a write-in sheet has no presumed measurement direction).
- **Blank/template mode** (`blankValues = true`): `effectiveRecord = UndercutRecord()` — the
  record is dropped before the strips are built (matching the wear sheet's decision that blank
  templates carry no recorded stations), so the page comes out as **started liner strips** with
  header writing rules, or the profile form when the shaft has no drawable liner. A started
  strip always draws in the write-in posture (title rule, no printed values) regardless of
  `blankValues` — an empty record has no recorded value to print anywhere — but only the blank
  template empties the liner's span down to its end faces; an export of an empty record keeps
  the liner fully drawn (see "started liner strips" above).
- **Notes row**: `Notes: ____` only — no dye-pen PASS/FAIL checkboxes (that's a wear/inspection
  concern with no place on a machining record).
- Standard composer contract: `pdfPrefs` shading, `lineThicknessScale`, `resolvedComponents`
  (`withResolvedBodies`) — same signature shape as `composeWearPdf`/`composeRunoutPdf`.

---

## Contracts & Invariants

- **Reference-only, sixth of its kind**: never affects `coverageEndMm`/OAL, body
  resolution/split/merge, `collidingIds()`, `maxOuterDiaMm`, or
  `ExportPdfGate.hasComponents`. Lives outside `ShaftSpec`, in `UndercutRecord`
  (`undercut_record` envelope field).
- **No carousel card, no Add dialog anywhere** — undercuts are authored only on the Undercut
  tab / its detail overlay, deliberately outside the "Add dialogs mirror carousel cards"
  invariant (`CLAUDE.md`).
- **Not component-keyed** — canonical storage is shaft-space `startFromAftMm`; there is no
  orphan concept and nothing is pruned at decode, unlike wear spots (which ARE pruned).
- **Golden rule** — `startFromAftMm`, `lengthMm`, `diaMm`, `referenceLinerId` and
  `referenceCutId` round-trip verbatim; no field commit path snaps, rounds, or derives a stored value. The overlay's draft is
  a staging area, not a filter: confirming passes the drafted values through unchanged — the pill's
  tap and the auto-save-on-leave share the one `confirmDraft` path, so neither can filter.
- **`confirmDraft` is the only write path from a card** — fields, chips, and the note edit a local
  draft; `updateUndercut`/`updateUndercutReference`/`addUndercut` run from that one function alone
  (pill tap or leaving the card), and discarding reverts everything (a discarded add leaves no
  record entry at all). Delete is the one immediate card action. Card order is keyed on stored
  values, so cards reorder only when a draft is confirmed.
- **A dirty draft is never silently committed OR silently dropped** — leaving a card commits it
  only while it clears `undercutConfirmIssue`; a blocked draft always asks (Keep editing /
  Discard), and dismissing the question keeps the edit.
- **Draw-both-sites, in lockstep**: the notch (void fill + boxed outline — surface-polyline top
  edge, shoulders, floor — cut against the
  local outer-surface envelope) renders identically in `UndercutRoute`/
  `UndercutWindowDetailOverlay` (canvas) and `UndercutPdfComposer` (PDF), from the ONE shared
  builder `buildUndercutNotches` (`geom/UndercutOverlayMath.kt`, over `resolveUndercutFloors`):
  `clampUndercutSpan` → containment forest → `effectiveNotchDiaMm` against the local surface (the
  outer envelope, or a PARENT's true floor for a nested cut) → `notchProfiles` → drawn floors
  swapped in. Both draw sites are pt-mapping and Canvas/DrawScope work only, so neither carries
  nesting logic; notches come back **parents before children**, which is what makes paint-over
  correct without depending on start coordinates. `buildUndercutStrips` (liner strips for cuts
  overlapping a liner, `clusterUndercuts`-derived free windows for the rest) is the single
  source of truth for "what's one zoomed view" consumed by the overview affordances, the detail
  overlay, and the PDF strips — all three agree by construction.
- **The PDF's strips own the page** — a sheet with ≥ 1 detail strip draws no whole-shaft profile
  and no OAL line, only the orientation row and the strips. The profile form is a fallback for a
  shaft with **no cuts and no drawable liner**; a shaft with liners but no cuts gets started
  strips. Do not reintroduce the profile above the strips.
- **Ø-0 placeholder never prints**: an unentered Ø draws a symbolic shallow floor in every
  overlay/canvas draw site but is skipped by `buildUndercutDiaStations` on the PDF — same rule
  as `WearDiaReading.diaMm == 0`.
- **Single-undercut strips print no total span** — `buildUndercutTotalSpan` requires ≥ 2
  drawable top-level undercuts, so a lone section's chained-rail length is never redundantly
  restated on a second rail line (a lone relief holding a nested cut counts as one).
- **Nested cuts are legal; partial overlaps and duplicate spans are not** — containment is
  eps-inclusive (a shared edge nests; identical spans do not), and a nested cut is drawn against
  its parent's floor, dimensioned on its own parent-anchored rail row, and excluded from the
  deepest-depth pool. A shared edge prints as one continuous face. The level-0 chain must never
  be fed a nested span (the cursor walk absorbs it), and the notch list must stay
  parents-before-children — which is why the order is level-first, never by start coordinate: a
  child sharing its parent's start would otherwise sort ambiguously.
- **`referenceLinerId` and `referenceCutId` are display metadata, never geometry keys** — the
  notch, the nesting forest, the strip
  assignment (`assignUndercutLiner`), and the chain range are all decided by the undercut's
  actual shaft-space span versus the liner's actual span; the stored reference liner only
  affects what the Distance field shows and converts against.

---

## Future options

- A locator band — a small whole-shaft key line with tick marks showing where each strip sits —
  if the shop ever asks for one back. The full profile itself is not coming back; the sections
  are the drawing.
- Per-strip free-text notes (today there is one page-level Notes rule; no per-undercut or
  per-strip note field beyond `Undercut.note`).
- A way to clear a Ø back to unentered (0) without deleting the whole undercut — see the
  "Measured Ø field" note above.
- Document title string ("UNDERCUT RECORD" vs "WELD UNDERCUTS", etc.) and the
  `UNDERCUT_CLUSTER_GAP_MM`/`UNDERCUT_WINDOW_PAD_MM` constants are tunable on device feedback —
  each a one-line change (see `docs/archive/UndercutDrawing_PLAN.md` §11 for the original defaults list).
  `UNDERCUT_WINDOW_PAD_MM` also sizes the **on-screen** overview affordances and the detail
  overlay's zoom windows, so it stays at 1 in; how wide the PDF's pad *prints* is the PDF's own
  `UNDERCUT_STRIP_MIN_PAD_PT` floor, tunable independently. The pad is a minimum: on a wide
  shaft the window-end break room (`undercutBreakRoomMm`, tuned by
  `UNDERCUT_BREAK_CLEAR_FRAC_OF_AMP` = 0.25 and `UNDERCUT_BREAK_AMP_FRAC_OF_R` = 0.6) is what
  the draw range actually gets.
