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
  **error color** while its confirm check fails (out of shaft bounds, or overlapping an
  adjacent cut: the same `undercutConfirmIssue` the status pill reads, so the drawing and the
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
enum class UndercutReference { AFT_SET, FWD_SET, LINER_AFT, LINER_FWD }

data class Undercut(
    val id: String = UUID.randomUUID().toString(),
    val startFromAftMm: Float = 0f,
    val lengthMm: Float = 0f,
    val diaMm: Float = 0f,
    val authoredReference: UndercutReference = UndercutReference.AFT_SET,
    val referenceLinerId: String = "",
    val note: String = "",
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
  entered against — one of **four** references, mirroring `WearSpotReference`'s set:
  `AFT_SET`/`FWD_SET` (measured from a taper's S.E.T.) or `LINER_AFT`/`LINER_FWD` (measured
  from a reference liner's own AFT/FWD edge). Switching it re-projects the *displayed* value
  only; `startFromAftMm` never moves.
- **`referenceLinerId`** names the `Liner.id` the distance converts against when
  `authoredReference` is a `LINER_*` value — display metadata only, never a geometry key (the
  undercut still lives in shaft space and renders wherever it is regardless of this liner). If
  that liner is later deleted, the Distance field falls back to the `AFT_SET` projection for
  display (canonical untouched, and the stored reference is never rewritten behind the
  machinist's back — `effectiveUndercutReference` in `geom/UndercutOverlayMath.kt`). Empty for
  SET-authored undercuts. Selecting a SET chip clears it back to `""`.
- **Back-compat**: `LINER_AFT`/`LINER_FWD` are additive enum values (added this iteration) — a
  document that uses one will not decode in an app build that predates them, the same rule
  every envelope enum carries. Files that stick to `AFT_SET`/`FWD_SET` are unaffected.
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
- **Blocking entry validation** `undercutSpanIssue(canonicalStartMm, lengthMm, oalMm)`: length
  must be `> ε`, and `[start, start+length]` must lie in `[0, oalMm]` (ε = `1e-3` mm,
  boundary-exact accepted). Wired as a `NumericInputField` validator on the Distance and Length
  fields — a violation reverts the field and never touches the model. The Ø field has **no**
  span validator: a measurement is sacred (golden rule); an implausible Ø is a non-blocking
  card warning instead.
- **Blocking confirm validation** `undercutOverlapIssue(canonicalStartMm, lengthMm, otherSpans)`:
  a draft must be either fully **clear** of every other cut (disjoint; touching edge-to-edge is
  legal) or fully **nested** with it — inside another cut, or containing one. A *partial*
  intrusion is blocked: two partially overlapping undercuts are physically one cut and would
  double-dimension the chain rail. So is a span **identical** to another's (within ε) — that is
  one cut entered twice, not a cut inside a cut. One wording for both,
  `UNDERCUT_PARTIAL_OVERLAP_MSG`, because the fix is the same. Checked only when **confirming**
  a drafted card (see "Undercut cards" below), against the clamped spans of every OTHER cut on
  the sheet. Confirm-time only, so nothing already stored is retroactively rejected —
  the `isUndercutStaleOverrun` posture; stored partial overlaps keep rendering as they always did.
- **Containment forest** `undercutNestingForest(spans)` → per span, its nesting `level` and the
  id of the smallest span containing it (`parentId`). Containment = `undercutSpanContains`: the
  child inside **both** parent edges within ε, and the two not the same span. A **shared edge is
  legal nesting** — the shop machines the original relief and then deepens a corroded section of
  it that may run right up to the relief's own shoulder (on-device intent), and that must print
  exactly as separately-authored adjacent sections would. Ties (equal-width containers, which
  shared edges can produce) break to the FIRST in record order, so the forest is deterministic.
  Partial overlaps are TOLERATED, not repaired — neither span contains the other, so both stay
  top-level siblings. The forest drives the nested notch build, the drawn-floor stacking, the
  extra rail rows, and which cuts the deepest-depth pool counts.
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
  sites stay identical. The step has zero axial width, so no fill area changes. Ø callout leaders anchor on the drawn floor; labels print the stored value.
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
  distinct from the liner around it. Same tone in every draw site. Both
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
  deliberately not style-driven** — the printed drawing keeps the standard black-ink shading
  (same posture as preview colors never leaking into `ShaftPdfComposer`); a PDF line-art option
  is a considered follow-up in `docs/SettingsCustomization_PLAN.md`, complicated by the
  strip's always-shaded-liner rule above.

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
  - `UndercutStrip.FreeStrip(window)` — bare-shaft cuts, no liner involved: draw range and
    chain range both equal the padded `UndercutWindow` from `clusterUndercuts` (the original,
    unchanged strip behavior).
  - `assignUndercutLiner(span, liners)` — the liner a cut belongs to for strip purposes: the
    one overlapping the largest share of the cut's span (`null` if none overlaps at all); an
    exact tie breaks to the AFT-most liner.
  - `linerStripFor(liner, assignedSpans, oalMm, padMm)` — builds one `LinerStrip` for a liner
    and its assigned cuts (`assignedSpans` may be empty — used to zoom an undercut-free liner
    for authoring, in which case the chain/draw range is just the liner's own span, padded).
  - `undercutPreviewDrawRange(strip, previewSpans, oalMm, padMm)` — the range the **detail
    overlay** actually renders: the strip's draw range **widened, never narrowed**, to hold the
    previewed spans (stored cuts with the live draft substituted). A draft edited past the
    strip's stored range — a cut overhanging a liner edge mid-edit (on-device report) — stays
    inside the drawing with the standard pad beyond it, the same range a confirmed overhang
    gets when the strip rebuilds on commit; never narrowing keeps the window stable while a
    draft shrinks a cut that had extended it. PDF strips don't need this (they only ever see
    confirmed cuts, which `linerStripFor`/`clusterUndercuts` already extend for).
  - `buildUndercutStrips(spans, liners, oalMm, gapMm, padMm)` — every cut overlapping a liner
    joins that liner's `LinerStrip` (one strip per liner holding ≥1 cut); the remaining
    bare-shaft cuts cluster into `FreeStrip`s via `clusterUndercuts`. Result sorted aft → fwd by
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
- **"Add undercut"** button (global — see "Add default" below) sets `anchorUndercutId` to the
  new undercut's id (clearing `anchorLinerId`), opening its strip immediately.
- **"Recorded undercuts" list**, below the canvas (`UndercutListRow`, one row per cut, aft →
  fwd, only shown once ≥ 1 undercut exists): a distance summary under its *effective* reference
  (`"<distance> from <tag>"`, where a `LINER_*` reference reads `"<liner title> AFT/FWD edge"`
  via `effectiveUndercutReference`/`undercutReferenceLinerFor`), a second line
  (`"L … · Ø …"`, Ø shown as `"—"` while unmeasured), and a stale-overrun warning icon
  (`isUndercutStaleOverrun`) when applicable. **This is a read-only summary + delete row, not
  an edit card** — tapping the row body (`testTag "undercut_row_<id>"`) calls `anchorToUndercut`
  to open the cut's owning strip (where the real edit card lives, per "Undercut cards" below);
  the trailing delete icon (`testTag "undercut_delete_<id>"`) removes the cut outright,
  confirm-free, without opening anything.

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
- **Selection and the open page are one thing**: swiping a card highlights its notch on the
  canvas, and tapping a notch pages the carousel to that card. A tap that hits nothing keeps the
  current card (there is no "nothing selected" state while cards exist).

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
  and every auto-save-on-leave.
- The blocking reason shows inline on the card while it is dirty; the non-blocking stale and
  Ø-vs-surface warnings still show, read off the **draft** so they describe what the canvas
  is previewing.

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
(tap-outside, back) is "Keep editing", so an edit is never lost to a stray tap. The pending add page follows the same rules — clear → its
add-confirm flow runs; blocked → the same dialog, Discard dropping the page.

The decision itself is the pure `undercutLeaveAction(draft, baseline, confirmIssue)` →
`NONE | COMMIT | PROMPT` (a pending card has no baseline, so it is dirty by construction).
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

  Tapping a SET chip sets the draft's reference and clears its `referenceLinerId` to `""`;
  tapping a Liner chip sets both the reference and the resolved liner's id. Either
  **re-projects the displayed Distance immediately** (canonical `startFromAftMm` never moves) but
  reaches `vm.updateUndercutReference` only on **Confirm**, so Cancel reverts the chip too. A
  stored `LINER_*` reference whose liner has been deleted displays as if `AFT_SET` were selected
  (`effectiveUndercutReference`) until a chip is tapped again — the stored value is not silently
  rewritten.
- **Distance** field (`WearNum`, shared wrapper around `NumericInputField`): label shows the
  active reference (`undercutReferenceLabel`); validator converts the entered value to
  canonical via `undercutStartToCanonicalMm` (passing the resolved reference liner's edges when
  applicable) then runs `undercutSpanIssue`. The **span** check stays a field validator (a value
  that could never be confirmed has no business entering the draft) while the **overlap** check
  is confirm-time only — a cut is legitimately moved past a neighbour by two separate field
  edits.
- **Length** field: a length edit keeps the **authored Distance** fixed — the commit re-derives
  canonical `startFromAftMm` from the active reference at the new length
  (`undercutCanonicalForNewLength`, the conversion pair composed). Identity under an AFT-flavored
  reference; under a FWD-flavored one the cut's FWD end (the datum the Distance was authored
  against) stays pinned and the cut grows/shrinks AFT-ward. Committing the new length against
  the old canonical would rewrite the displayed Distance by the length delta (on-device report:
  Distance 5 / Length 12 under Liner FWD became Distance 7 after shortening to 10) — a
  golden-rule violation. The "canonical never moves" rule covers reference *switching* only.
  The validator runs `undercutSpanIssue` against the same recomputed canonical.
- **Measured Ø** field: **no validator** — any parseable value ≥ 0 enters the draft verbatim
  (golden rule). Initial display is blank when `diaMm == 0` (unentered), else the formatted value.
  Because the underlying `NumericInputField` requires `parseValid` to accept the text to commit
  and an empty string does not parse, **a Ø typed once cannot be cleared back to blank/0 through
  this field** — the field reverts to the last committed non-blank value on blur instead of
  committing an empty edit. (A known as-built limitation, not a design intent; deleting the
  whole undercut is the only way to remove a Ø today.)
- **Notes**: free text straight into the draft (Confirm/Cancel own persistence, so the numeric
  fields' capture-on-focus discipline buys nothing here).
- **Delete** icon (confirm-free, **immediate** — deletion is not drafted; the page goes with it).
  Absent on the add flow's pending card, which has nothing recorded to delete — the pill's ✕
  discards that page.
- **Warnings** (non-blocking, both can show together): "Extends past shaft end — re-measure"
  (`isUndercutStaleOverrun`) and "Ø meets or exceeds shaft surface here" (`diaMm > 0` and `diaMm
  >=` the local surface Ø). For a draft that is **nested** inside another cut, the comparison is
  against the **surrounding cut's floor** and the wording becomes "Ø meets or exceeds the
  surrounding cut's floor here" — a nested cut removes material from its parent's floor, so the
  shaft surface is not the figure it can reach. Both terms come from `resolveUndercutFloors`
  (`surfaceDiaMm`, `nesting.parentId`), so the card and the notch build agree on what the cut is
  taken against.

### Overlay "Add undercut…"
Between the canvas and the carousel: **"Add undercut in this liner"** on a `LinerStrip`
(`testTag "undercut_add_in_liner"` — the authoring entry point that makes an undercut-free liner
worth tapping on the overview at all), **"Add undercut here"** on a `FreeStrip`
(`testTag "undercut_add_in_strip"`). Disabled while a pending draft already exists — one at a
time.

It creates a **draft-only page**, not a record entry: the carousel gains a page at the correct
aft → fwd position, the canvas previews the cut, and `vm.addUndercut` runs only when the draft is
**confirmed** — by the pill or by leaving the page (followed by `updateUndercut` when the draft
also carries a Ø or a note — `addUndercut` lands only the span and reference). The pill's ✕
discards the page outright, and so does "Discard" on a blocked leave, so a discarded add leaves
**no ghost cut** in the record.

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
tie breaks AFT. The route's global "Add undercut" seeds AT the AFT SET, where AFT_SET is
trivially the nearer datum.

### Overlay canvas contents
Aft → fwd: a **dimension rail above** — the strip total on the upper line when ≥ 2 undercuts,
the chained run below it. The chain runs over the strip's **chain range**
(`strip.chainStartMm`/`chainEndMm`), not its draw range: on a `FreeStrip` the two coincide (the
original window-edge chain); on a `LinerStrip` the chain anchors on the **liner's own edges**
(extended only by cut overhang), so the pad between a break edge and the liner edge is never
dimensioned. Below the rail, the **strip profile** (every resolved component clipped to the draw
range, liners painted last so a liner over a body reads as the surface — matching the notch
math's max-wins envelope) with **notches** cut in, and **Ø callouts below** via the shared
`planDiaCallouts` engine (one station per undercut at its axial centre, leader to the notch
floor, label `formatDiaWithUnit`, or `"—"` for an unentered Ø — the overlay's own placeholder,
never printed). A tap selects an undercut (highlight rect) and **pages the carousel to its card**;
a tap that hits nothing leaves the current card alone. The rails, notches, and Ø callouts all
read the selected card's **draft**, so an in-progress edit is dimensioned live.

**Strip ends**: an end that lands on the shaft's own extent (`x = 0` or `x = OAL`) draws a flat
edge; a threaded shaft end additionally gets the diagonal thread-stub hatch
(`drawThreadStubHatch`, shared with the wear overlay). Any other end is a truncation and gets
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
- **Strip end breaks** — `amp = min(r × 0.6, UNDERCUT_BREAK_AMP_MAX_PT)` (18 pt) in
  `drawUndercutWindowEnd`. The radius term keeps a small section's symbol legible; the absolute cap
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
    anonymous grey slab). On a `FreeStrip` the draw range is just the padded cluster window, as
    before. Both are then widened at layout time by `computeUndercutStripDrawRange` until the
    stock outside each **chain datum** prints at least `UNDERCUT_STRIP_MIN_PAD_PT` (24 pt) wide —
    the mm pad is scale-blind, so the same 1 in reads as ~36 pt beside a short liner on a
    full-width strip and under 9 pt in a grid cell, where the break edge sat all but against the
    liner's end face (on-device report: "oddly cramped"). It only ever widens, only outward, and
    stays clamped to `[0, oalMm]`, so no datum moves, no dimension changes, and a cut at a shaft
    end keeps its flat physical end;
  - **chained dimension rail** (`buildUndercutRailSpans`), run over the strip's **chain range**
    (`strip.chainStartMm`/`chainEndMm`) — **not** the draw range: chain AFT datum → first
    shoulder, each undercut's own length, each inter-cut gap, remainder to chain FWD datum. On a
    `FreeStrip` the chain range equals the draw range (the original window-edge chain, so the
    pad spans **are labelled** — they locate the cluster inside its zoom window). On a
    `LinerStrip` the chain range is the liner's own edges (extended only by overhang), so the
    rail's outer witness lines land on a real datum and the **pad between it and the break edge
    is deliberately left undimensioned** — an arbitrary zoom margin is not a figure worth
    printing. Zero-length spans are omitted (never drawn as degenerate zero-width dims). Fed the
    strip's **TOP-LEVEL spans only**: the forward cursor pulls an overlapping span's start up to
    itself, which collapses a fully NESTED cut to zero width and drops it silently. (That absorb
    rule stays — it is what keeps a legacy partially-overlapping pair from double-counting;)
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
    `formatDiaWithUnit`, **no "Ø" prefix**); an undercut with `diaMm <= 0` is **skipped
    entirely** on the printed callouts (no placeholder for an unrecorded value) — its notch
    still draws (at the symbolic floor) and still gets dimensioned on the rail, so the section
    isn't lost from the sheet, only its Ø value is absent;
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
- **Golden rule** — `startFromAftMm`, `lengthMm`, `diaMm`, and `referenceLinerId` round-trip
  verbatim; no field commit path snaps, rounds, or derives a stored value. The overlay's draft is
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
- **`referenceLinerId` is display metadata, never a geometry key** — the notch, the strip
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
  `UNDERCUT_STRIP_MIN_PAD_PT` floor, tunable independently.
