# Undercut Drawing — Design Plan

Status: **implemented** on `feat/undercut-drawing` (2026-07-30). Shipped contract doc:
`docs/UndercutDrawing.md`. This file remains the design/rationale record; read the contract
doc for current behavior. Reference photo: hand sketch "Wilderness Explorer STBD Int. Shaft —
weld undercuts" (three reduced-diameter sections drawn zoomed, chained length dims above,
measured Ø values with leaders below, a total-span dim, and a long anchor dim to the SET).

§11's five defaults all shipped as chosen (no changes requested): "UNDERCUT RECORD" title,
`UNDERCUT_CLUSTER_GAP_MM` = 6 in / `UNDERCUT_WINDOW_PAD_MM` = 1 in, pad-span rail labels kept,
no dye-pen checkbox row, AFT_SET/FWD_SET-only references. Known as-built deviations from this
plan's §6/§8 narrative (see `docs/UndercutDrawing.md` for the full current-behavior contract):
- The overlay is anchored by undercut id (`anchorUndercutId`), not by window index/reference;
  windows are recomputed from the record on every composition. Undercut cards live **only**
  inside `UndercutWindowDetailOverlay` — `UndercutRoute` itself has no card list.
- A cluster of exactly one undercut prints **no** total-span rail line (it would just restate
  the chain's own single figure); `buildUndercutTotalSpan` requires ≥ 2 drawable undercuts.
- The Measured Ø field cannot be cleared back to blank/0 once a value has been typed —
  `NumericInputField` reverts blank input on blur, so removing a Ø today requires deleting the
  undercut.
- `pdf/UndercutStripLayout.kt` reuses `WearStripLayout`'s vertical/grid/horizontal/inner/rail
  machinery verbatim and forks only `buildUndercutRailSpans` (the chain walks a window's
  shaft-space span rather than a liner's local span) plus the composer-private draw helpers.

### Iteration 2 (liner-anchored strips) — 2026-07-31

On-device feedback on the shipped v1: a cut inside a liner zoomed to a padded window with no
visible liner edges, reading as an anonymous grey slab, and a liner with no recorded cut yet
had no way to open a strip to author one — a cut could only be added blind, from the global
"Add undercut" button, then hunted down.

Shipped shape (detail in `docs/UndercutDrawing.md`, current-behavior contract):
- `UndercutReference` gained `LINER_AFT`/`LINER_FWD`; `Undercut.referenceLinerId` names the
  reference liner (display-only, verbatim round-trip, no decode pruning — additive enum values,
  so a file using them won't decode pre-iteration).
- `geom/UndercutMath.kt` gained a sealed `UndercutStrip`: `LinerStrip` draws the **whole liner**
  (plus cut overhang) padded, chain-anchored on the liner's own edges; `FreeStrip` is the
  original padded cluster window, unchanged. `assignUndercutLiner` + `buildUndercutStrips`
  decide which cuts join which liner vs. cluster into bare-shaft windows — the one source both
  the canvas and the PDF composer read.
- Every liner is now a tap target on the overview (empty or not); a new "Recorded undercuts"
  list gives a read-only summary + delete row per cut; the overlay grew four reference chips
  (Liner pair shown only while a reference liner resolves) and an "Add undercut in this liner"
  button on liner strips.
- Drawn-depth exaggeration reworked: the fixed min/max ramp became a **per-sheet slider**
  ("Cut depth exaggeration", 0–25%, stored as `UndercutRecord.exaggerationFrac`) with drawn
  depth **normalized to the sheet's deepest cut** (`deepestUndercutDepthMm` +
  `normalizedNotchFloorDiaMm`), so sheets with very different absolute depths read alike.

### Iteration 3 (card carousel + draft/confirm) — 2026-07-31

On-device feedback: the overlay's vertical card stack forced scrolling between the drawing and
the fields, and every keystroke landed in the record. The cards became a swipeable carousel
(`ComponentCarouselPager`'s presentation) under a fixed canvas, ordered aft → fwd, editing a
**local draft** that previews on the canvas and reaches `UndercutRecord` only on **Confirm**
(Cancel reverts; Add is a draft-only page, so a cancelled add leaves no ghost cut). Confirm is
additionally gated on `undercutOverlapIssue` — a draft may not intrude into an adjacent cut's
bounds. See `docs/UndercutDrawing.md` §"Undercut cards — the overlay carousel".

---

## 1. What an undercut is here

A machinist documents **undercut sections** — axial spans where the shaft surface was
machined below the surrounding surface (weld repair undercuts, cleanup cuts). The shop
sheet shows *only the undercut regions, zoomed*, not the whole shaft, with:

- chained length dimensions across the undercut cluster (section lengths + gaps),
- a total dimension across the whole cluster,
- one measured diameter per section, printed below with a leader,
- one anchor dimension locating the cluster **from the S.E.T.** (Small End of Taper).

The user authors an undercut with exactly three numbers: **distance from SET (AFT or
FWD), length, diameter** — deliberately **not tied to any component**. An undercut
usually sits inside a liner but may cross a liner edge or span components; nothing may
constrain it to one component's extent.

## 2. Posture — the sixth reference-only feature

Undercuts follow the exact posture of wear spots / pits / dia readings / runout readings /
coupler bolt slots (`CLAUDE.md`):

- **Never** affect `coverageEndMm`, `lastOccupiedEndMm`, `ensureOverall`, OAL, body
  resolution/split/merge, `collidingIds()`, `maxOuterDiaMm`, the Free-to-End badge, or
  `ExportPdfGate.hasComponents`.
- Live **outside `ShaftSpec`**, in their own envelope record (see §3). They are *document
  content about a physical shaft*, not design geometry — same reasoning as `WearRecord`.
- **No carousel card, no Add dialog** — authored only on the Undercut tab. This
  deliberately keeps the feature outside the "Add dialogs mirror carousel cards"
  invariant.
- **Draw-both-sites**: every visual (the notch, the rail, the Ø callouts) renders
  identically on the canvas overlay and the PDF, from shared pure math in `geom/` (no
  `pdf → ui` dependency).
- **Golden rule**: typed values (distance, length, Ø) are stored/round-tripped verbatim.

Storage decision — why a new envelope field and not `WearRecord` or `ShaftSpec`:
- Not `ShaftSpec` (the `couplerBoltSlots` precedent): that path drags in resolve,
  carousel/Add-dialog parity, component ordering, and `withNewOal` — all machinery for
  *design* components. Undercuts are inspection/repair documentation.
- Not `WearRecord` (zero-plumbing path): the undercut drawing is a **separate printed
  document** from the wear/inspection record, with its own blank-template mode and its
  own lifecycle. Muddling the two records couples unrelated documents.
- So: `@SerialName("undercut_record")` sibling of `wear_record`/`runout_readings`.
  The plumbing is a known 7-site pattern (runout readings are the worked example, §7).

## 3. Data model

`model/Undercut.kt`:

```kotlin
/** Which SET the distance field was authored against. Display metadata only. */
@Serializable
enum class UndercutReference { AFT_SET, FWD_SET }

@Serializable
data class Undercut(
    val id: String = newId(),
    /** Canonical: shaft-space mm from the AFT face (x=0) to the undercut's AFT edge. */
    val startFromAftMm: Float = 0f,
    val lengthMm: Float = 0f,
    /** Measured/target Ø of the undercut floor. Verbatim. 0 = placed, not yet entered
     *  (drawn in the overlay, skipped on the printed PDF — same rule as WearDiaReading). */
    val diaMm: Float = 0f,
    val authoredReference: UndercutReference = UndercutReference.AFT_SET,
    val note: String = "",
)

@Serializable
data class UndercutRecord(val undercuts: List<Undercut> = emptyList())
```

**Coordinates.** Canonical storage is physical shaft space (mm from AFT face — the same
space as `Segment.startFromAftMm` and, since `OalWindow.measureStartMm == 0.0`, the same
space `computeSetPositionsInMeasureSpace` returns). The SET-relative *displayed* distance
is re-projected through an exact-inverse conversion pair (§4), the `WearSpotReference`
pattern. Canonical never moves once authored — editing the shaft later (moving a taper,
changing OAL) changes the *displayed* SET distance, never the stored position. Same rule
as wear spots; positions are user-authored.

**No component key → no orphans.** Nothing is pruned at decode; the only staleness is an
undercut extending past the current shaft extent (OAL shrank), which gets a non-blocking
card warning + render clamp (§4), mirroring `isWearSpotStaleOverrun`.

## 4. Pure math — `geom/UndercutMath.kt` (unit-tested, android-free)

- **Conversion pair** (exact algebraic inverses, `LinerWearMath` shape, but shaft-global —
  no component-local term):
  - `undercutStartToCanonicalMm(entered, ref, aftSetXMm, fwdSetXMm, lengthMm)`
    - `AFT_SET`: entered locates the **AFT edge**, measured FWD from AFT SET →
      `aftSetXMm + entered`
    - `FWD_SET`: entered locates the **FWD edge**, measured AFT from FWD SET →
      `fwdSetXMm − entered − lengthMm`
  - `canonicalToUndercutStartMm(startFromAftMm, ref, …)` — the inverse.
- **Blocking entry validation** `undercutSpanIssue(startFromAftMm, lengthMm, oalMm)`:
  length > 0 and span ⊆ `[0, OAL]` (ε = 1e-3 mm, boundary-exact accepted). Enforced via
  `NumericInputField` validator, field reverts on violation, model untouched
  (`NumberField.md`). The Ø field accepts any value ≥ 0 (it's a measurement — golden
  rule; a "Ø exceeds shaft surface" condition is a *warning*, not a block, §5).
- **Stale classifier** `isUndercutStaleOverrun(u, oalMm)` — non-blocking; card shows the
  wear-style "extends past shaft end — re-measure" warning; render clamps to `[0, OAL]`
  without mutating the record.
- **Cluster windows** — the unit of zooming. `clusterUndercuts(undercuts, oalMm)`:
  1. sort by clamped span start;
  2. merge spans whose gap ≤ `UNDERCUT_CLUSTER_GAP_MM` (152.4 mm / 6 in — tunable
     constant; the reference sketch's three sections read as one cluster);
  3. each cluster's window = `[minStart − pad, maxEnd + pad]` with
     `UNDERCUT_WINDOW_PAD_MM` (25.4 mm / 1 in) each side, clamped to `[0, OAL]`.
  Returns `UndercutWindow(startMm, endMm, undercutIds)` — consumed by the overlay, the
  overview affordances, and the PDF strips, so all three agree by construction.
- **Hit-test** `pickUndercutWindowAt(xMm, windows)` / `pickUndercutAt(xMm, undercuts, padMm)`.

## 5. Surface profile + notch geometry — `geom/SurfaceProfileMath.kt`

The novel geometry: a notch must draw against the **local outer surface**, which may step
(liner edges, body Ø changes) or slope (tapers) *within the undercut span* — this is what
"the drawing accommodates measurements beyond the liner" means.

- Neutral input type (keeps `geom` free of `ui.resolved` imports):
  `SurfaceSeg(startMm, endMm, diaStartMm, diaEndMm)` — built at call sites from
  `resolvedComponents` (bodies/liners constant, tapers linear; threads contribute their
  major Ø envelope; coupler bolt slots and unresolved items skipped). At overlapping
  coverage (liner over body) the **outer** surface wins: `outerDiaAt(segs, xMm)` = max
  over covering segs.
- `notchOutline(segs, x0Mm, x1Mm, undercutDiaMm)` returns the notch polygon breakpoints:
  vertical shoulder at `x0` from local surface down to the undercut radius, flat floor at
  the undercut radius, vertical shoulder at `x1` back up — **plus intermediate surface
  breakpoints** where the outer surface steps/slopes inside the span (so a notch crossing
  a liner edge shows the taller shoulder on the liner side). Where
  `undercutDiaMm ≥ outerDiaAt(x)` the notch has no depth: that portion is skipped
  (nothing drawn there) and the condition is surfaced as a non-blocking card warning
  ("Ø meets or exceeds shaft surface over part of this span").
- Rendering (both sites): notch = **void** — white fill (paper/background) from the
  surface line down to the floor, mirrored about the centerline, outlined with the
  standard stroke: shoulders + floor lines top and bottom. On the small overview/profile
  scale the same construction simply renders small; no alternate marker style.

## 6. UI — new editor tab

**Tab**: `EditorTab.UNDERCUT` ("Undercut Drawing"), same `isBuilt` gate and sidebar
posture as Runout/Wear (`EditorTab.kt`, `ShaftEditorRoute.kt`, `EditorSidebar.kt`).

**`ui/screen/UndercutRoute.kt`** (modeled on `WearRoute`):
- Toolbar (hamburger + title), scrollable column.
- **Overview canvas**: `ShaftLayout.compute` + `ShaftRenderer.draw` over
  `resolvedComponents`, then an affordance pass: notches drawn (shared geom math), faint
  primary tint over each **cluster window** + per-window count badge — the wear-area
  selection idiom, with the window replacing the component as the selectable area. Tap →
  invert `xMmFromPx` → `pickUndercutWindowAt` → open the detail overlay for that window.
  No pinch-zoom here (single-tap surface, same as WearRoute's overview).
- **"Add undercut" button**: creates a default undercut (1 in length clamped to OAL,
  Ø 0 = unentered, `AFT_SET` reference, positioned just FWD of the AFT SET) and opens the
  overlay on it. Precision comes from the numeric fields, not the tap — wear posture.
- **Undercut cards** (below the canvas, one per undercut, aft→fwd): "Measure From:
  AFT S.E.T. | FWD S.E.T." chips (re-project display only, persist reference immediately —
  the `updateWearSpotReference` pattern), Distance field, Length field, Ø field (all
  `NumericInputField`, commit-on-blur, unit at the edge), Notes, delete icon, stale/Ø
  warnings (§4/§5).
- Blank-draft toggle, Preview (`PdfPreviewOverlay` + `RunoutWearOptionsSheet`), Export
  (SAF `CreateDocument`), Print (`printShaftPdfPage`) — all straight WearRoute ports.

**`ui/screen/UndercutDetail.kt` — `UndercutWindowDetailOverlay`** (modeled on
`ComponentWearDetailOverlay`):
- Full-screen composable (own `BackHandler`), title = window range, pinch-to-zoom
  0.5×–6× + two-finger pan via the same transform pattern, taps inverted.
- Canvas draws the **window's local profile**: every resolved component intersecting the
  window at window-local scale (a `computeSegDetailLayout`-style shared layout fn used by
  renderer *and* tap handler), S-curve break edges at both window ends
  (`drawBreakEdgeCompose`; AFT stub `eyeAtTop = true`, FWD `false`), flat + thread-hatch
  when a window end coincides with a threaded shaft end, plain edge when it lands on the
  bare shaft end. Notches cut into that profile (§5). Component boundaries inside the
  window (liner edges etc.) draw naturally because the profile is real resolved geometry.
- **Dimension rail above** (canvas twin of the PDF strip rail, §8): chained spans +
  total span.
- **Ø callouts below** via the shared `planDiaCallouts` engine — one station per
  undercut at its axial center, leader to the notch floor, label `formatDiaWithUnit`,
  value-less (Ø 0) shows "—" here and never prints.
- Editing in the overlay: tap an undercut → highlights it and scrolls its card (cards
  are also listed under the overlay canvas, same as wear spots). No drag-editing.

**ViewModel** (`ShaftViewModel`): `_undercutRecord: MutableStateFlow<UndercutRecord>`,
`addUndercut`, `updateUndercut(id, startFromAftMm, lengthMm, diaMm, note)`,
`updateUndercutReference(id, ref)`, `removeUndercut(id)` — plain record updates, no
geometry side effects.

## 7. Persistence plumbing (the 7 known sites)

`undercut_record` rides the envelope exactly like `runout_readings` did:
1. `ShaftDocCodec.ShaftDocV1` + `Decoded` + both `decode` branches (additive, defaulted —
   **no version bump**; no decode-time pruning).
2. `AutosaveManager.SessionSnapshot` (+ defaulted field; `DraftEntry` untouched).
3. VM flow + accessor.
4. `snapshot()` + the autosave `combine`.
5. `EditState` (undo slice) + `currentEditState`/`applyEditState` + recorder combine.
6. `exportJson` / `importJson` / `newDocument` / `restoreSnapshot`.
7. `DraftRing.isDefaultSession` — decision: a session whose only content is undercuts
   **does count as user content** (mirrors wear: a record with entries isn't a default
   session). Verify how wearRecord is treated there and match it.

Round-trip pinned by `persistence/UndercutRecordPersistenceTest` (JSON shape + unknown-id
survival) and `data/AutosaveSnapshotUndercutTest`.

## 8. PDF — `pdf/UndercutPdfComposer.kt` + `pdf/UndercutStripLayout.kt`

Letter landscape 792×612, 36 pt margins — the Wear page skeleton reused deliberately:

- **Header**: two centered lines (job info / title **"UNDERCUT RECORD"**), blank mode gets
  the 5 edge-to-edge writing rules + taller header (`BlankFormText` helpers). Title
  wording is a one-constant change if the shop prefers e.g. "WELD UNDERCUTS".
- **OAL line**: SET-to-SET arrows, typed-OAL label seated in a break; blank mode = empty
  break. Identical rules to Wear/Runout (`drawWearOalLine` is the port source).
- **Main profile**: always on top; full resolved profile scaled SET-to-SET with notches
  drawn (§5) plus "← AFT / FWD →" direction row. No per-cluster dims on the profile —
  the strips own the numbers (wear posture).
- **Detail strips — one per cluster window**: mode from cluster count (0 =
  `PROFILE_FORM`, 1 = `COMBINED` full-width, 2+ = `GRID` 2-column, max 4 + "+N more"),
  reusing `computeWearVerticalLayout` / `computeWearStripGridLayout` verbatim (they're
  count-driven and content-agnostic). Per strip:
  - window profile at strip-local scale (same shared window-layout math as the overlay),
    break edges at window ends, notches;
  - **chained dimension rail above**: `buildUndercutRailSpans(window, undercuts)` —
    window AFT edge → first shoulder, each undercut length, each gap, remainder to the
    window FWD edge (zero-length spans omitted; overlaps cursor-clamped — the
    `buildWearStripRailSpans` algorithm over a window instead of a liner). Labels seat
    in a break cut when they fit, stacked-row fallback otherwise (`layoutWearStripRail`
    conventions). **Plus a second rail line above the chain**: one span across the whole
    cluster (first shoulder → last shoulder) — the sketch's "7 11/16" total. Windows pad
    spans (break-edge → first shoulder) are drawn without labels when the pad is
    synthetic — decision: **label them too**; they locate the cluster inside the window
    and cost nothing. Revisit on device.
  - **Ø callouts below** via `planDiaCallouts` (leader to notch floor, `formatDiaWithUnit`,
    Ø 0 skipped in print);
  - **anchor title at the bottom**: "<dist> FROM AFT/FWD S.E.T." — distance from the
    **cluster's near shoulder** to the SET chosen by **proximity** (cluster midpoint vs
    SET-to-SET midpoint, the `linerAnchorForPdf` idea), aligned toward its SET (left for
    AFT, right for FWD). Blank mode: writing rule + "FROM  AFT / FWD  S.E.T." suffix
    (circle-one), always left-aligned — exact wear parity.
- **Blank/template mode**: record-free (`effectiveRecord = UndercutRecord()`) → profile
  form only, no strips; lines-in/values-out everywhere else. Matches the wear decision
  that blank templates carry no recorded stations.
- **Notes row**: dye-pen checkboxes are wear-specific — undercut sheet gets just
  `Notes: ____` (decision; trivially extendable).
- Options: `pdfPrefs` shading, `lineThicknessScale`, `resolvedComponents` — standard
  composer contract (`withResolvedBodies`).

## 9. Files

New:
| File | Content |
|---|---|
| `model/Undercut.kt` | `Undercut`, `UndercutRecord`, `UndercutReference` |
| `geom/UndercutMath.kt` | conversion pair, validators, clusters, hit-tests, constants |
| `geom/SurfaceProfileMath.kt` | `SurfaceSeg`, `outerDiaAt`, `notchOutline` |
| `pdf/UndercutStripLayout.kt` | cluster/strip pure layout, rail spans builder, anchor label |
| `pdf/UndercutPdfComposer.kt` | the document composer |
| `ui/screen/UndercutRoute.kt` | tab route: overview canvas, cards, export/print/preview |
| `ui/screen/UndercutDetail.kt` | `UndercutWindowDetailOverlay` |
| `docs/UndercutDrawing.md` | shipped contract doc (Phase 5) |

Touched: `ShaftDocCodec.kt`, `AutosaveManager.kt`, `EditState.kt`, `ShaftViewModel.kt`,
`EditorTab.kt`, `ShaftEditorRoute.kt`, `EditorSidebar.kt`, `docs/README.md`,
`docs/Navigation.md`, `docs/RunoutSheet.md` (cross-ref), `CLAUDE.md` (invariant block).

Tests: `geom/UndercutMathTest`, `geom/SurfaceProfileMathTest`,
`pdf/UndercutStripLayoutTest`, `persistence/UndercutRecordPersistenceTest`,
`data/AutosaveSnapshotUndercutTest`, `ui/viewmodel/ShaftViewModelUndercutTest`, plus an
SVG same-math preview test (`UndercutStripSvgPreviewTest`) for markup review.

## 10. Phases / ownership

| Phase | Scope | Owner |
|---|---|---|
| 1 | model + codec + autosave + undo + VM ops + persistence tests | Sonnet agent |
| 2 | `UndercutMath` + `SurfaceProfileMath` + tests (the tricky geometry) | Fable (main) |
| 3 | UI tab + route + overlay + cards | Opus agent |
| 4 | strip layout + PDF composer + SVG preview test | Opus agent |
| 5 | contract doc, CLAUDE.md invariant, doc index/nav updates | Sonnet agent |

1 ∥ 2 (disjoint files) → 3 ∥ 4 (both consume 1+2; overlay and composer share the geom
helpers, coordinated by this spec) → 5 → full build + `testDebugUnitTest` sweep.

## 11. Open decisions for review (defaults chosen, all one-line changes)

1. Document title string — "UNDERCUT RECORD" (vs "WELD UNDERCUTS", "UNDERCUT DRAWING").
2. `UNDERCUT_CLUSTER_GAP_MM` = 6 in and `UNDERCUT_WINDOW_PAD_MM` = 1 in — tune on device.
3. Pad spans (break edge → first/last shoulder) are labeled — drop labels if noisy.
4. No dye-pen checkbox row on this sheet (Notes only).
5. References limited to AFT_SET/FWD_SET (no liner-edge references — undercuts aren't
   component-bound; add later only if the shop asks).
