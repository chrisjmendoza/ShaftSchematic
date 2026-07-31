# Undercut Drawing

Shipped contract for the Undercut Drawing tab/PDF — the shop's record of machined-below-surface
sections (weld-repair undercuts, cleanup cuts), documented as zoomed detail windows with chained
dimensions. The sixth reference-only feature, same posture as wear spots / pits / dia readings /
runout readings / coupler bolt slots (`CLAUDE.md`). Design rationale lives in
`docs/UndercutDrawing_PLAN.md`; this file documents shipped, current behavior.

**Files:**
- `model/Undercut.kt` — `Undercut`, `UndercutRecord`, `UndercutReference`
- `geom/UndercutMath.kt` — conversion pair, validators, cluster windows, hit-tests, constants
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
  count badge over each **cluster window** — the wear-area tap idiom, with the window (not a
  component) as the selectable area. No pinch-zoom on this canvas; the overlay owns zoom.
- **"Add undercut"** button: records a default section and opens its window (see "Add default"
  below).
- Blank-draft (write-in) toggle, PDF Preview (`PdfPreviewOverlay` + `RunoutWearOptionsSheet`),
  Export (SAF `CreateDocument`), Print (`printShaftPdfPage`) — straight ports of the wear tab's
  flows, all calling `composeUndercutPdf`.

### UndercutWindowDetailOverlay (`UndercutDetail.kt`)
- Full-screen "zoom in" on one cluster window: dimension rail above (chained run + cluster
  total), the window's real resolved profile with notches cut in, Ø callouts below, undercut
  cards below the canvas.
- Pinch-to-zoom (0.5×–6×) + two-finger pan; taps invert the same transform so hit-testing
  always runs in untransformed canvas space.
- Cards are editable here only — there is no card on `UndercutRoute` itself and no carousel
  card / Add dialog anywhere (see "Contracts & Invariants").

---

## Data model & coordinate rule

`model/Undercut.kt`:

```kotlin
enum class UndercutReference { AFT_SET, FWD_SET }

data class Undercut(
    val id: String = UUID.randomUUID().toString(),
    val startFromAftMm: Float = 0f,
    val lengthMm: Float = 0f,
    val diaMm: Float = 0f,
    val authoredReference: UndercutReference = UndercutReference.AFT_SET,
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
- **`authoredReference`** is display metadata only: which S.E.T. the "Distance" field is
  entered against. Switching it re-projects the *displayed* value; `startFromAftMm` never
  moves (the `WearSpotReference` pattern).
- **Golden rule**: `startFromAftMm`, `lengthMm`, and `diaMm` are round-tripped verbatim — no
  snap/round/derive ever rewrites a typed value. `diaMm == 0` means "placed, not yet measured":
  drawn in the overlay (as a symbolic floor, never a real Ø), never printed.
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
  every cluster window plus a small count badge above it (`drawUndercutWindowAffordances`).
- A tap inverts `ShaftLayout.Result.xMmFromPx` → `pickUndercutWindowAt(tapMm, windows)` → opens
  `UndercutWindowDetailOverlay` for that window.
- **The overlay is anchored by undercut id, not by window index or reference.** `UndercutRoute`
  holds `anchorUndercutId: String?`; windows are re-derived (`clusterUndercuts`) on every
  composition from the current record, and `activeWindow = windows.firstOrNull { anchorId in
  it.undercutIds }`. An anchor whose undercut no longer clusters into any window (deleted, or
  the shaft shrank past it) simply yields no active window — the anchor is never proactively
  cleared, since the record and the anchor can update in either order within a frame and
  clearing on a stale pass could close an overlay that was just opened.
- **"Add undercut"** (see "Add default" below) sets the anchor to the new undercut's id, which
  opens its window immediately.

### Add default
`vm.addUndercut(startFromAftMm, lengthMm)` records a section at the AFT S.E.T. position
(clamped to `[0, oalMm]`), length `DEFAULT_UNDERCUT_LENGTH_MM` = 25.4 mm (1 in, clamped to the
remaining shaft extent), Ø `0` (unentered), reference `AFT_SET`, and returns the new id.
Precision comes from the overlay's numeric fields afterward, never from the tap — the wear
posture: a tap only opens/selects, typing does the real work.

### Undercut cards — overlay only
**There is no card on `UndercutRoute` itself.** Cards render exclusively inside
`UndercutWindowDetailOverlay`, below its canvas, one per undercut in the open window (aft → fwd).
Each card:
- **"Measure From: AFT S.E.T. | FWD S.E.T."** chips (`WearChip`, shared with the wear overlay) —
  tapping persists `authoredReference` immediately via `vm.updateUndercutReference` and
  re-projects the *displayed* Distance only; canonical `startFromAftMm` never moves.
- **Distance** field (`WearNum`, shared wrapper around `NumericInputField`): label shows the
  active reference; validator converts the entered value to canonical via
  `undercutStartToCanonicalMm` then runs `undercutSpanIssue`.
- **Length** field: validator runs `undercutSpanIssue` against the current canonical start.
- **Measured Ø** field: **no validator** — any parseable value ≥ 0 commits verbatim (golden
  rule). Initial display is blank when `diaMm == 0` (unentered), else the formatted value.
  Because the underlying `NumericInputField` requires `parseValid` to accept the text to commit
  and an empty string does not parse, **a Ø typed once cannot be cleared back to blank/0 through
  this field** — the field reverts to the last committed non-blank value on blur instead of
  committing an empty edit. (A known as-built limitation, not a design intent; deleting the
  whole undercut is the only way to remove a Ø today.)
- **Notes**: free text, same tap-and-leave no-op discipline as the numeric fields (capture on
  focus, commit on blur only if changed).
- **Delete** icon (confirm-free).
- **Warnings** (non-blocking, both can show together): "Extends past shaft end — re-measure"
  (`isUndercutStaleOverrun`) and "Ø meets or exceeds shaft surface here" (`diaMm > 0` and `diaMm
  >= minOuterDiaOver` over the clamped span).

### Overlay canvas contents
Aft → fwd: a **dimension rail above** (cluster total on the upper line when ≥ 2 undercuts, the
chained run below it — window start → each shoulder → each gap → window end), the **window
profile** (every resolved component clipped to the window, liners painted last so a liner over
a body reads as the surface — matching the notch math's max-wins envelope) with **notches** cut
in, and **Ø callouts below** via the shared `planDiaCallouts` engine (one station per undercut
at its axial centre, leader to the notch floor, label `formatDiaWithUnit`, or `"—"` for an
unentered Ø — the overlay's own placeholder, never printed). A tap selects an undercut
(highlight rect + scrolls no card, since all cards are already visible below the canvas).

**Window ends**: an end that lands on the shaft's own extent (`x = 0` or `x = OAL`) draws a
flat edge; a threaded shaft end additionally gets the diagonal thread-stub hatch
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
│   [detail strip per cluster: total rail / chained rail / window / Ø / SET]│
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
  same construction the strips draw zoomed, not a separate "marker style". No per-cluster
  dimensions on the profile; the strips own the numbers.
- **Detail strips — one per cluster window**: page mode from cluster count
  (`determineUndercutPdfMode`, delegating to `determineWearPdfMode`): 0 → `PROFILE_FORM`
  (profile only — also what blank mode always produces, since its record is dropped before
  clustering), 1 → `COMBINED` (one full-width strip), 2+ → `GRID` (2-column, max 4 +
  "+N more" overflow note). Vertical/horizontal strip banding reuses
  `computeWearVerticalLayout`/`computeWearStripGridLayout`/`computeWearStripHorizontalLayout`
  verbatim (count-driven, content-agnostic). Per strip:
  - the window's real resolved profile at strip-local scale, with a break edge at each cut end
    (flat + thread hatch when a window end coincides with a threaded physical shaft end, S-curve
    break otherwise) and notches cut in;
  - **chained dimension rail** (`buildUndercutRailSpans`): window AFT edge → first shoulder,
    each undercut's own length, each inter-cut gap, remainder to window FWD edge. Zero-length
    spans are omitted (never drawn as degenerate zero-width dims), and the two pad spans (window
    edge → nearest shoulder) **are labelled** — they locate the cluster inside its zoom window;
  - **a second rail line above the chain — the cluster total** (`buildUndercutTotalSpan`, first
    shoulder → last shoulder). **Returns `null` (nothing drawn, no reserved band) for a cluster
    of fewer than two drawable undercuts** — with exactly one undercut, a total span would just
    restate that undercut's own length, already dimensioned on the chain below;
  - **Ø callouts below** via `planDiaCallouts`/`buildUndercutDiaStations` (leader to notch floor,
    `formatDiaWithUnit`, **no "Ø" prefix**); an undercut with `diaMm <= 0` is **skipped
    entirely** on the printed callouts (no placeholder for an unrecorded value) — its notch
    still draws (at the symbolic floor) and still gets dimensioned on the rail, so the section
    isn't lost from the sheet, only its Ø value is absent;
  - **anchor title at the bottom**: `"<dist> FROM AFT/FWD S.E.T."` — the S.E.T. chosen by
    proximity (`undercutAnchorFor`: cluster midpoint vs SET-to-SET midpoint), distance measured
    to the cluster's **near** shoulder, title aligned toward its SET (left for AFT, right for
    FWD). Reported as a magnitude even when the cluster sits outboard of its chosen SET. Blank
    mode: a writing rule + both directions printed for the machinist to circle one, always
    left-aligned (a write-in sheet has no presumed measurement direction).
- **Blank/template mode** (`blankValues = true`): `effectiveRecord = UndercutRecord()` — the
  record is dropped before clustering, so the page is always the profile-only form with header
  writing rules and an empty OAL break; no strips at all (matches the wear sheet's decision that
  blank templates carry no recorded stations).
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
- **Golden rule** — `startFromAftMm`, `lengthMm`, `diaMm` round-trip verbatim; no field commit
  path snaps, rounds, or derives a stored value.
- **Draw-both-sites, in lockstep**: the notch (void fill + shoulders + floor, cut against the
  local outer-surface envelope) renders identically in `UndercutRoute`/
  `UndercutWindowDetailOverlay` (canvas) and `UndercutPdfComposer` (PDF), from the one shared
  pure pipeline: `clampUndercutSpan` → `effectiveNotchDiaMm(diaMm, minOuterDiaOver(segs, …))` →
  `notchProfiles(surfaceSegsFrom(resolved), …)`. Cluster windows (`clusterUndercuts`) are the
  single source of truth for "what's one zoomed view" consumed by the overview affordances, the
  detail overlay, and the PDF strips — all three windowing decisions agree by construction.
- **Ø-0 placeholder never prints**: an unentered Ø draws a symbolic shallow floor in every
  overlay/canvas draw site but is skipped by `buildUndercutDiaStations` on the PDF — same rule
  as `WearDiaReading.diaMm == 0`.
- **Single-undercut clusters print no total span** — `buildUndercutTotalSpan` requires ≥ 2
  drawable undercuts, so a lone section's chained-rail length is never redundantly restated on
  a second rail line.

---

## Future options

- A dimension rail directly on the main (whole-shaft) profile, so a viewer scanning the full
  shaft sees roughly where a cluster sits without opening a strip (currently only the strips
  carry numbers, by design — "the strips own the numbers").
- Per-cluster free-text notes (today there is one page-level Notes rule; no per-undercut or
  per-cluster note field beyond `Undercut.note`).
- Liner-edge references for the Distance field (today limited to `AFT_SET`/`FWD_SET`; undercuts
  aren't component-bound, so a liner-edge reference would need its own care — only add if the
  shop asks).
- A way to clear a Ø back to unentered (0) without deleting the whole undercut — see the
  "Measured Ø field" note above.
- Document title string ("UNDERCUT RECORD" vs "WELD UNDERCUTS", etc.) and the
  `UNDERCUT_CLUSTER_GAP_MM`/`UNDERCUT_WINDOW_PAD_MM` constants are tunable on device feedback —
  each a one-line change (see `UndercutDrawing_PLAN.md` §11 for the original defaults list).
