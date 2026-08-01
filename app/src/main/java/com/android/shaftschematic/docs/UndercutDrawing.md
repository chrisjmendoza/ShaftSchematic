# Undercut Drawing

Shipped contract for the Undercut Drawing tab/PDF — the shop's record of machined-below-surface
sections (weld-repair undercuts, cleanup cuts), documented as zoomed detail windows with chained
dimensions. The sixth reference-only feature, same posture as wear spots / pits / dia readings /
runout readings / coupler bolt slots (`CLAUDE.md`). Design rationale lives in
`docs/UndercutDrawing_PLAN.md`; this file documents shipped, current behavior.

**Files:**
- `model/Undercut.kt` — `Undercut`, `UndercutRecord`, `UndercutReference`
- `geom/UndercutMath.kt` — conversion pair, validators, cluster windows, liner strips
  (`UndercutStrip`), hit-tests, constants
- `geom/SurfaceProfileMath.kt` — `SurfaceSeg`, outer-surface envelope, notch-profile geometry
- `ui/resolved/SurfaceSegs.kt` — the one `resolvedComponents → SurfaceSeg` mapping every draw
  site shares
- `ui/screen/UndercutRoute.kt` — the tab: overview canvas, "Add undercut", blank-draft toggle,
  preview/print/export
- `ui/screen/UndercutDetail.kt` — `UndercutWindowDetailOverlay`, the full-screen zoomed window +
  cards
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
- Cards edit a **local draft** that previews on the canvas and reaches the record only on
  Confirm. The previewed draft's notch draws **dashed** in the selection (primary) color —
  provisional, unmistakable against the liner and the settled cuts — and switches to the
  **error color** while its confirm check fails (out of shaft bounds, or overlapping an
  adjacent cut: the same `undercutConfirmIssue` that disables the Confirm button, so the
  drawing and the button never disagree). Confirm settles it into the normal solid
  outline. Cards are editable here only — there is no card on `UndercutRoute` itself and no
  carousel card / Add dialog anywhere (see "Contracts & Invariants").

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
  machinist's back — `effectiveUndercutReference` in `UndercutDetail.kt`). Empty for
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
  a draft may not intrude into another cut's bounds (two overlapping undercuts are physically one
  cut, and would double-dimension the chain rail). Checked only when **confirming** a drafted card
  (see "Undercut cards" below), against the clamped spans of every OTHER cut on the sheet;
  touching edge-to-edge is legal. Confirm-time only, so nothing already stored is retroactively
  rejected — the `isUndercutStaleOverrun` posture.
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
  candidates win over pad-only candidates; remaining ties break to the nearer span edge).
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
  `deepestUndercutDepthMm(undercuts, segs, oalMm)` (Ø-reduction of the deepest **measured**
  cut; placeholders and cuts that removed nothing contribute 0):

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

  Region topology still comes from `notchProfiles` at the TRUE floor — a cut that never
  touched the neighboring stock must not draw into it; only the floor line and shoulders
  deepen. Ø callout leaders anchor on the drawn floor; labels print the stored value.
  Display-only: canonical values and printed Ø are untouched (golden rule). The notch
  **mouth is open**: the void fill overdraws the surface stroke across the cut, so no
  outline runs across the top of an undercut in either draw site.

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
  and the caller surfaces it as a non-blocking warning ("Ø meets or exceeds shaft surface here"
  in the card), never a block or a rewritten value.

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
(unentered), and returns the new id. The route's global "Add undercut" button calls it with the
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
- **Confirm** (`testTag "undercut_confirm"`) is enabled only while the draft differs from stored
  AND `undercutConfirmIssue` is null — `undercutSpanIssue` (shaft bounds) then
  `undercutOverlapIssue` against the clamped spans of every OTHER cut **on the sheet**. It calls
  `updateUndercut` with the draft **verbatim** (golden rule), plus `updateUndercutReference` only
  when the chips actually moved (so a card merely displaying its `LINER_*`→`AFT_SET` fallback
  never rewrites the stored reference). The carousel then **follows** the confirmed cut to its
  new aft → fwd index.
- **Cancel** (`testTag "undercut_cancel"`) is enabled while dirty and drops the draft back to
  stored values — including the reference chips.
- The blocking reason shows inline on the card while Confirm is disabled; the non-blocking stale
  and Ø-vs-surface warnings still show, read off the **draft** so they describe what the canvas
  is previewing.

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
- **Length** field: validator runs `undercutSpanIssue` against the draft's canonical start.
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
  Absent on the add flow's pending card, which has nothing recorded to delete — its Cancel
  discards the page.
- **Warnings** (non-blocking, both can show together): "Extends past shaft end — re-measure"
  (`isUndercutStaleOverrun`) and "Ø meets or exceeds shaft surface here" (`diaMm > 0` and `diaMm
  >= minOuterDiaOver` over the clamped span).

### Overlay "Add undercut…"
Between the canvas and the carousel: **"Add undercut in this liner"** on a `LinerStrip`
(`testTag "undercut_add_in_liner"` — the authoring entry point that makes an undercut-free liner
worth tapping on the overview at all), **"Add undercut here"** on a `FreeStrip`
(`testTag "undercut_add_in_strip"`). Disabled while a pending draft already exists — one at a
time.

It creates a **draft-only page**, not a record entry: the carousel gains a page at the correct
aft → fwd position, the canvas previews the cut, and `vm.addUndercut` runs only on **Confirm**
(followed by `updateUndercut` when the draft also carries a Ø or a note — `addUndercut` lands
only the span and reference). Cancel discards the page outright, so a cancelled add leaves **no
ghost cut** in the record.

Default section (`defaultUndercutSpan`): centred in the aft-most free gap of the strip's range
(the liner's span, else the window) wide enough for `DEFAULT_UNDERCUT_LENGTH_MM`, else centred in
the widest gap left and shortened to fit — so a fresh draft never opens already overlapping a
recorded cut, which would block Confirm before a single value had been typed. Reference
`LINER_AFT` + that liner's id on a liner strip (so the very first typed Distance reads against
the datum the machinist is standing at), `AFT_SET` on a free strip.

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

`pdf/UndercutPdfComposer.kt` — landscape US Letter (792 × 612 pt), 36 pt margins, the Wear page
skeleton reused deliberately:

```
┌─── header: job info line / "UNDERCUT RECORD" ─────────────────────────────┐
│   ←────────── OAL (AFT SET → FWD SET) ─────────────────────────→          │
│   [shaft profile — full length, notches cut at each undercut]             │
│   ← AFT                                                          FWD →    │
│   [detail strip: total rail / chained rail / profile / Ø / name + SET]    │
│   Notes: ______________________________________________________________   │
└───────────────────────────────────────────────────────────────────────────┘
```

- **Header**: two centred lines (job info / `UNDERCUT_DOC_TITLE` = "UNDERCUT RECORD", a
  one-constant change). Blank mode gets a taller header with 5 edge-to-edge writing rules
  (Customer/Vessel/Job #/Date/Side).
- **OAL line**: SET-to-SET arrows with witness lines, typed-OAL label seated in a break
  mid-span (falls back to continuous-line-plus-label-above when too short) — identical rule to
  the wear/runout sheets. Blank mode cuts an empty break.
- **Main profile**: always on top, full resolved profile scaled SET-to-SET (`ptPerMm` derived
  from the SET-to-SET span) with every undercut's notch cut in at true position/scale — the
  same construction the strips draw zoomed, not a separate "marker style". No per-strip
  dimensions on the profile; the strips own the numbers.
- **Strip source — `buildUndercutStrips`**: the composer builds the same sealed `UndercutStrip`
  list the canvas uses (see "Pure math" above) — one `LinerStrip` per liner holding ≥ 1 cut
  (`docSpec.liners` filtered to `lengthMm > 0 && odMm > 0`, the drawable-liner filter
  `collectWearLinerGroups` also applies), plus one `FreeStrip` per bare-shaft cluster window for
  the leftover cuts. **Liner titles** come from `util/buildLinerTitleById(docSpec)` — the same
  shared custom-label-else-positional-default map the carousel, the wear sheet, and the runout
  sheet use, so a liner-anchored strip is identifiable at a glance and never drifts from the
  liner's name shown elsewhere.
- **Detail strips**: page mode from strip count (`determineUndercutPdfMode`, delegating to
  `determineWearPdfMode`): 0 → `PROFILE_FORM` (profile only — also what blank mode always
  produces, since its record is dropped before the strips are built), 1 → `COMBINED` (one
  full-width strip), 2+ → `GRID` (2-column, max 4 + "+N more" overflow note).
  Vertical/horizontal strip banding reuses
  `computeWearVerticalLayout`/`computeWearStripGridLayout`/`computeWearStripHorizontalLayout`
  verbatim (count-driven, content-agnostic). Per strip:
  - the profile drawn over the strip's **draw range** (`strip.drawStartMm`/`drawEndMm`) at
    strip-local scale, with a break edge at each cut end (flat + thread hatch when a draw-range
    end coincides with a threaded physical shaft end, S-curve break otherwise) and notches cut
    in. On a `LinerStrip` the draw range is the **whole liner** plus any cut overhang, padded
    each side, so the liner's true edges are always visible and a neighbor sliver shows before
    the break edge (on-device report: a padded window with no visible liner edges printed as an
    anonymous grey slab). On a `FreeStrip` the draw range is just the padded cluster window, as
    before;
  - **chained dimension rail** (`buildUndercutRailSpans`), run over the strip's **chain range**
    (`strip.chainStartMm`/`chainEndMm`) — **not** the draw range: chain AFT datum → first
    shoulder, each undercut's own length, each inter-cut gap, remainder to chain FWD datum. On a
    `FreeStrip` the chain range equals the draw range (the original window-edge chain, so the
    pad spans **are labelled** — they locate the cluster inside its zoom window). On a
    `LinerStrip` the chain range is the liner's own edges (extended only by overhang), so the
    rail's outer witness lines land on a real datum and the **pad between it and the break edge
    is deliberately left undimensioned** — an arbitrary zoom margin is not a figure worth
    printing. Zero-length spans are omitted (never drawn as degenerate zero-width dims);
  - **a second rail line above the chain — the strip total** (`buildUndercutTotalSpan`, first
    shoulder → last shoulder). **Returns `null` (nothing drawn, no reserved band) for a strip
    with fewer than two drawable undercuts** — with exactly one undercut, a total span would
    just restate that undercut's own length, already dimensioned on the chain below;
  - **Ø callouts below** via `planDiaCallouts`/`buildUndercutDiaStations` (leader to notch floor,
    `formatDiaWithUnit`, **no "Ø" prefix**); an undercut with `diaMm <= 0` is **skipped
    entirely** on the printed callouts (no placeholder for an unrecorded value) — its notch
    still draws (at the symbolic floor) and still gets dimensioned on the rail, so the section
    isn't lost from the sheet, only its Ø value is absent;
  - **title at the bottom** (`buildUndercutStripTitle(linerTitle, anchorLabel)`): a `LinerStrip`
    prints `"<liner title> — <dist> FROM AFT/FWD S.E.T."` (e.g. `"AFT Liner — 250.0 FROM AFT
    S.E.T."`) — the same `name — anchor` construction the wear sheet uses for that liner, so it
    reads identically wherever the liner is named. A `FreeStrip` has nothing to name (a
    bare-shaft span carries no shop identity) and prints the anchor alone. The S.E.T. is chosen
    by proximity (`undercutAnchorFor`: strip midpoint vs SET-to-SET midpoint), distance measured
    to the strip's **near** shoulder, title aligned toward its SET (left for AFT, right for
    FWD) — reported as a magnitude even when the strip sits outboard of its chosen SET. A liner
    strip with zero drawable cuts (every assigned span clamped away) still prints just the liner
    name, with no anchor. Blank mode: a writing rule + both directions printed for the
    machinist to circle one, always left-aligned (a write-in sheet has no presumed measurement
    direction).
- **Blank/template mode** (`blankValues = true`): `effectiveRecord = UndercutRecord()` — the
  record is dropped before the strips are built, so the page is always the profile-only form
  with header writing rules and an empty OAL break; no strips at all (matches the wear sheet's
  decision that blank templates carry no recorded stations).
- **Notes row**: `Notes: ____` only — no dye-pen PASS/FAIL checkboxes (that's a wear/inspection
  concern with no place on a machining record).
- Standard composer contract: `pdfPrefs` shading, `lineThicknessScale`, `resolvedComponents`
  (`withResolvedBodies`) — same signature shape as `composeWearPdf`/`composeRunoutPdf`.

---

## Contracts & Invariants

- **Reference-only, sixth of its kind**: never affects `coverageEndMm`/OAL, body
  resolution/split/merge, `collidingIds()`, `maxOuterDiaMm`, the Free-to-End badge, or
  `ExportPdfGate.hasComponents`. Lives outside `ShaftSpec`, in `UndercutRecord`
  (`undercut_record` envelope field).
- **No carousel card, no Add dialog anywhere** — undercuts are authored only on the Undercut
  tab / its detail overlay, deliberately outside the "Add dialogs mirror carousel cards"
  invariant (`CLAUDE.md`).
- **Not component-keyed** — canonical storage is shaft-space `startFromAftMm`; there is no
  orphan concept and nothing is pruned at decode, unlike wear spots (which ARE pruned).
- **Golden rule** — `startFromAftMm`, `lengthMm`, `diaMm`, and `referenceLinerId` round-trip
  verbatim; no field commit path snaps, rounds, or derives a stored value. The overlay's draft is
  a staging area, not a filter: Confirm passes the drafted values through unchanged.
- **Confirm is the only write path from a card** — fields, chips, and the note edit a local
  draft; `updateUndercut`/`updateUndercutReference`/`addUndercut` run on Confirm alone, and Cancel
  reverts everything (a cancelled add leaves no record entry at all). Delete is the one immediate
  card action. Card order is keyed on stored values, so cards reorder only on Confirm.
- **Draw-both-sites, in lockstep**: the notch (void fill + shoulders + floor, cut against the
  local outer-surface envelope) renders identically in `UndercutRoute`/
  `UndercutWindowDetailOverlay` (canvas) and `UndercutPdfComposer` (PDF), from the one shared
  pure pipeline: `clampUndercutSpan` → `effectiveNotchDiaMm(diaMm, minOuterDiaOver(segs, …))` →
  `notchProfiles(surfaceSegsFrom(resolved), …)`. `buildUndercutStrips` (liner strips for cuts
  overlapping a liner, `clusterUndercuts`-derived free windows for the rest) is the single
  source of truth for "what's one zoomed view" consumed by the overview affordances, the detail
  overlay, and the PDF strips — all three agree by construction.
- **Ø-0 placeholder never prints**: an unentered Ø draws a symbolic shallow floor in every
  overlay/canvas draw site but is skipped by `buildUndercutDiaStations` on the PDF — same rule
  as `WearDiaReading.diaMm == 0`.
- **Single-undercut strips print no total span** — `buildUndercutTotalSpan` requires ≥ 2
  drawable undercuts, so a lone section's chained-rail length is never redundantly restated on
  a second rail line.
- **`referenceLinerId` is display metadata, never a geometry key** — the notch, the strip
  assignment (`assignUndercutLiner`), and the chain range are all decided by the undercut's
  actual shaft-space span versus the liner's actual span; the stored reference liner only
  affects what the Distance field shows and converts against.

---

## Future options

- A dimension rail directly on the main (whole-shaft) profile, so a viewer scanning the full
  shaft sees roughly where a cluster sits without opening a strip (currently only the strips
  carry numbers, by design — "the strips own the numbers").
- Per-strip free-text notes (today there is one page-level Notes rule; no per-undercut or
  per-strip note field beyond `Undercut.note`).
- A way to clear a Ø back to unentered (0) without deleting the whole undercut — see the
  "Measured Ø field" note above.
- Document title string ("UNDERCUT RECORD" vs "WELD UNDERCUTS", etc.) and the
  `UNDERCUT_CLUSTER_GAP_MM`/`UNDERCUT_WINDOW_PAD_MM` constants are tunable on device feedback —
  each a one-line change (see `UndercutDrawing_PLAN.md` §11 for the original defaults list).
