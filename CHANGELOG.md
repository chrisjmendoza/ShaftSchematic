# Changelog

All notable changes to **ShaftSchematic** will be documented in this file.

The format is inspired by [Keep a Changelog](https://keepachangelog.com/) and follows [Semantic Versioning](https://semver.org/).

---

## 2026-07-21

### feat: interactive runout bubbles — tap to record TIR value + high-spot marker

Digitizes the hand-filled runout bubble. Tapping a bubble on the Runout tab opens a "zoom-in"
editor (`ui/screen/RunoutBubbleDialog.kt`) to record that station's TIR reading and high-spot
direction; both print on the preview and PDF export. Both are optional — a sheet still exports
blank. See `docs/RunoutBubbleEditor_PLAN.md` and the "Runout Bubble Editor" section of
`docs/RunoutSheet.md`.

- **Model/persistence:** `model/RunoutReading.kt` (`RunoutReading`/`RunoutReadings`) — reference-only
  (never affects OAL/coverage/collision/Free-to-End, like coupler bolt slots / wear spots). Additive
  `runout_readings` envelope field (no version bump); keyed by `(componentId, stationIndex)` with
  render-layer orphan handling. Wired through `ShaftViewModel` (`setRunoutReading`/`clearRunoutReading`,
  autosave combine, snapshot/import/export/new-doc) and `AutosaveManager.SessionSnapshot`.
- **High spot:** placed by tapping/dragging the ring; snaps to **30-minute clock ticks** (0–23,
  12 o'clock = 0, clockwise). Off-ring touches do nothing. Pure math in `geom/RunoutReadingMath.kt`
  (`snapToClockTick`, `bubbleAngleDeg`, `clockTickRimOffset`, `isOnRingBand`, `pickBubbleAt`).
- **Value:** canonical mm, entered/shown in the active unit (`util/formatRunoutValue`), parsed on Save.
- **Rendering (both draw sites, lockstep):** value centred in the circle + a short red high-spot
  **dash straddling the rim** (no radial line — keeps the value legible), in
  `RunoutRoute.drawRunoutMarkers` and `RunoutPdfComposer.drawPlacedBubbles` (`composeRunoutPdf`
  gained a `runoutReadings` param).
- **Keyway cutout:** the protruding square notch is replaced by an **open-topped keyway slot** cut into
  the top of the circle (top arc broken across the slot mouth; nothing protrudes past the rim), drawn
  identically on canvas and PDF.
- **Engine:** `stationIndex` added to `RunoutStationX`/`PlacedRunoutBubble` for stable bubble identity.
- Tests: `RunoutReadingTest` (model + codec round-trip / orphan pass-through), `RunoutReadingMathTest`
  (clock snap, angle↔rim round-trip, ring band, bubble pick). Suite green.

---

## 2026-07-18

### feat: wear PDF rendering modes — liners-only page at 3+ wear liners

Matches shop practice (wear doc shows the liner cutouts OR the shaft drawing, not
both stacked). Automatic three-way rule, no toggle (`determineWearPdfMode`,
`pdf/WearStripLayout.kt`): 0 wear liners → blank shaft-profile field form (unchanged);
1–2 → combined page as before (path untouched); **3+ → strips-only** — no shaft
profile or OAL dimension line (header + dye-pen/notes stay), strips grow into the
freed page (130 pt each at 3 strips, capped 216 pt) with extra inter-strip gap.
Orphaned spots count as zero. +14 tests (mode boundaries incl. 2→3, strips-only
layout bounds/cap/fill); suite 527 → 541 green.

### feat: liner wear areas — inspection recording on the Wear tab + PDF detail strips

Digitizes the shop-sketch liner wear workflow (`docs/LinerWearAreas_Proposal.md`; build
record: `docs/LinerWearAreas_BuildLog_2026-07-18.md`). Uncommitted decisions from the
proposal's §10 were resolved and are logged there.

- **Model/persistence:** `model/WearSpot.kt` (`WearSpot`/`WearRecord`) — reference-only
  (never affects OAL/coverage/collision), liner-local AFT-edge canonical coordinates,
  additive `wear_record` envelope field (no version bump), orphan spots dropped at
  decode, wired through autosave; backup needed no change (raw byte copies).
- **Wear tab:** interactive shaft canvas (resolved components), liners are tap targets
  with tint affordance + spot-count badges.
- **Detail overlay:** full-screen break-out liner (S-curve stubs, eye outward), hatched
  wear bands with per-spot dimension rails, editable spot cards (commit-on-blur).
- **Input spec (Chris):** per-spot "Measure From" — AFT SET / FWD SET / Liner AFT /
  Liner FWD (canonical storage unchanged; display projection only) — and **blocking
  in-span validation** (start or start+length outside the liner rejects the commit
  inline); stale overruns (liner shortened later) warn + render clamped, never block.
  Min-Ø stays optional (0 = not recorded); start + length required.
- **Wear PDF:** thin hatched bands on the main profile + up to 3 broken-out detail
  strips per page (auto-selected aft→fwd, overflow noted as "+N more"), each with
  common-factor radius scaling (preserves the liner-vs-shaft OD step), anchor-from-SET
  title, and a **chained dimension rail** (AFT edge → band start, band lengths, gaps,
  trailing remainder — provably tiles the liner; narrow-span label fallback).
- Tests 431 → 527 (`WearRecordPersistenceTest`, `ShaftViewModelWearSpotTest`,
  `AutosaveSnapshotWearRecordTest`, `LinerWearMathTest`, `WearStripLayoutTest` et al).

### fix: wear OAL line raised to runout spacing; runout bubble leader clearance

(Committed earlier today as `61ef2c6`; entry added retroactively.) Wear document's OAL
dimension line raised 16 → 90 pt above the shaft (matches the runout sheet). Runout
bubbles gain a leader-clearance spread — cross-row gaps widen by up to 1.6×minGap
(+8 pt on PDF) funded from row slack, so bubbles keep writing room from neighboring
leaders; tight rows degrade to prior behavior.

### feat: collision-free runout bubble layout — shared engine, alternating rows

Runout bubble placement is rewritten around a hard guarantee: **bubbles never touch
each other, and leader lines never enter a bubble or cross another leader.** The old
greedy level assignment only prevented same-level bubble overlap — a leader to a
level-1 bubble could slice through a level-0 circle, adjacent levels could physically
overlap (38 pt step vs 40 pt bubbles), and the PDF and canvas preview had drifted apart
(different body-station math, different taper inset caps, preview silently dropping
overflow bubbles).

- **New shared engine `geom/RunoutBubbleLayout.kt`** (pure Kotlin, JVM-tested) used by
  BOTH `RunoutPdfComposer` and the `RunoutRoute` canvas preview — identical renderings
  by construction; placement logic no longer lives in any renderer.
- **Alternating rows** (0,1,0,1 within each component, phase-flip at crowded component
  boundaries), globally aligned row heights anchored below the deepest drawn shaft
  point — the hand-drawn shop convention.
- **Spacing invariants** make bubble contact geometrically impossible; bubble x
  positions are a least-squares fit (pool-adjacent-violators) so bubbles sit directly
  under their stations whenever there is room. Two rows is width-optimal — every
  leader's drop needs its own horizontal lane past the rows above it, so deeper stacks
  can never pack tighter (see `docs/archive/runout_bubble_collision_system_2026-07-18.md`).
- **Leader verification + dogleg re-routing**: every leader is collision-checked
  (segment-circle, segment-segment); failures re-route as doglegs through a common
  departure line + corridor + vertical drop, which provably converges to zero
  intersections. Physically impossible densities (~27+ stations/page) compress and
  flag themselves (`RunoutBubblePlan.compressed`).
- **Standardised station math** (was silently different between PDF and preview):
  bodies use cell midpoints; taper/liner edge inset caps at 20 % of length.
- 20 unit tests (`geom/RunoutBubbleLayoutTest.kt`) incl. randomized stress configs,
  stepped-OD shafts, and degenerate overload.

### feat: runout sheet drawing conventions — raised OAL, keyway notch

- **OAL dimension raised** to 90 pt (≈ 1.25 in) above the shaft top with witness
  (extension) lines dropping to the shaft's actual top edge at each SET face — the
  schematic/wear-document convention; the line no longer crowds the profile.
- **Keyway reference marker** upgraded from a 4 pt filled square to a 7 pt **open
  square notch straddling the rim at 12-o'clock** (key-at-top convention, like the
  hand-drawn sheets), and now drawn in the canvas preview too (was PDF-only).
- Docs corrected: threads ARE drawn on the runout profile (hatched envelopes, no
  stations; excluded-from-OAL threads sit outside the SET-to-SET arrows at their
  physical position) — `RunoutSheet.md` previously claimed otherwise.

### fix: runout & wear documents now use resolved components

The runout sheet and wear document built their profiles and measurement stations from
**raw** `spec.bodies`, while the schematic uses the resolved component list (bodies
subtracted against tapers/liners, split/merged, auto-fill gaps). A stored body
overlapping the AFT taper therefore produced runout stations *inside* the taper
(stacked, low-hanging bubbles), listed "Body #1" above "AFT Taper" in the station
selector, and drew body rectangles through the taper trapezoid on the wear document.

- `composeRunoutPdf` / `composeWearPdf` now accept `resolvedComponents` (same contract
  as `composeShaftPdf`) via a shared `ShaftSpec.withResolvedBodies` helper; profile,
  OD lookups, and station placement all use resolved bodies.
- `RunoutRoute` station selector + canvas bubbles and `WearRoute` previews/exports all
  pass `vm.resolvedComponents`.
- Auto-body spans now get stations and outline on both documents (previously silently
  skipped); auto segments are labeled "Body (auto)" — carousel parity.
- Analysis: `docs/archive/runout_wear_resolved_components_fix_2026-07-18.md`.

### docs: liner wear-area feature proposal

`docs/LinerWearAreas_Proposal.md` — scoping document for tap-a-liner wear inspection
on the Wear tab (break-out detail view matching the shop-sketch convention, wear spots
with liner-local start/length + min-Ø reading, `wear_record` envelope field, 4-phase
implementation plan). Awaiting review; 5 open questions listed in §10.

---

## 2026-07-12

### feat: backup & restore for saved shafts (+ fix for update data loss)

Layered protection against losing saved shafts to a bad update:

- **Fixed the root cause of saves lost on update.** Sample pruning previously
  deleted any file whose name matched a bundled sample and whose notes carried
  the `[SAMPLE]` marker — silently destroying shafts a user had built by
  editing a seeded sample. Pruning is now ledger-driven: at seed time the app
  records a SHA-256 of exactly what it wrote, and on a version bump it deletes
  only files still byte-identical to that hash. Anything edited (or predating
  the ledger) is kept. Regression-tested.
- **Settings → Data → "Back up all shafts…"** writes every saved shaft into a
  single dated zip (with a manifest) at any location you pick — Drive,
  Downloads, SD card — so a copy lives outside the app sandbox.
- **Settings → Data → "Restore from backup…"** imports a backup zip. Never
  overwrites: identical docs are skipped, name collisions are saved as
  `<name> (restored)`. Result summary shown in a snackbar.
- **Open screen → "Import"** brings a single `.shaft` file from anywhere on
  the device into Saved (same never-overwrite policy).
- **Save screen → "Save a copy to device…"** exports the current shaft as a
  `.shaft` file to a picked location (device-to-device sharing, ad-hoc copies).
- **Automatic pre-update snapshots:** on the first launch after an app-version
  change, the whole saves folder is zipped into internal `backups/` (keeping
  the last 3) *before* any migration or seeding runs.
- **Android Auto Backup wired up:** `shafts/` is now included in Google cloud
  backup and device-to-device transfer rules, so a reinstall can restore saves
  without any manual step.
- New `ShaftBackup` util (zip write/read, restore policy, snapshots) with a
  JVM test suite; seeding tests extended to cover the ledger-guarded pruning.

### fix: auto taper-rate review fixes (formatting, sentinels, state ownership)

Post-review hardening of the auto taper-rate feature shipped 2026-07-11:

- **Snapped `1:10` / `1:20` no longer corrupt to `1:1` / `1:2`.** The common-rate
  formatter trimmed trailing zeros off integer output; zeros are now only trimmed
  in the fractional part. Regression-tested.
- **No rate is fabricated from missing diameters.** `autoTaperRate` now requires
  both diameters to be real positive values; the dialog's `-1` "not provided"
  sentinel and the model's `0` default previously produced garbage rates (e.g.
  `1:2.970` from SET 100 / LET blank) that could survive a switch to Manual and
  drive missing-end derivation.
- **Viewing a taper card no longer mutates the document.** The carousel card's
  composition-time `LaunchedEffect` write to `taperRateText` (which dirtied and
  autosaved untouched files, and could rewrite a derive-pending diameter) is
  removed. The model is written only on explicit commits: geometry edits carry
  the recomputed rate; tapping the Auto chip syncs the stored text.
- **Auto/Manual mode is now user-owned state**, seeded once per taper instead of
  re-derived from string equality — a typed manual rate that happened to match
  the computed text no longer silently flips the card back to Auto.
- **Bore preference actually works now.** The 1:16 (≤ 6 in) / 1:12 (> 6 in)
  preference was a dead-code float-equality tie-break; it now selects among
  within-tolerance candidates whose errors are comparably close (≤ 1 pt apart),
  while a clearly closer candidate still wins on geometry. Expressed in
  canonical mm (152.4) per the unit-edge rule. Covered by new tests.
- **Blank manual rate reverts instead of committing.** Committing `""` left the
  field blank while `updateTaper`'s `ifBlank` kept the old rate in the model/PDF.
- **Dialog no longer clobbers a typed manual rate** when toggling Auto → Manual;
  the rate field's display is derived, matching the carousel pattern.
- **Carousel card gained the Auto one-end-missing message** ("Auto needs
  Length + SET + LET…") for parity with the Add dialog.
- **PDF footer rate fallback delegates to the shared formatter**, so a
  blank-rate taper prints the same snapped/exact text the card shows on screen.

## 2026-07-11

### feat: auto taper-rate from Length + SET + LET (auto default, 3-decimal exact)

- Added automatic taper-rate calculation when Length, SET, and LET are present.
- New **Rate mode** control (`Auto | Manual`) in both Add Taper dialog and taper
  carousel card. Auto mode is the default.
- Auto mode snaps to common shop tapers when close (3% slope tolerance), and falls
  back to exact `1:N.NNN` when not close.
- Auto matching now carries the shop preference order for common bores: 1:16 is the
  preferred small-bore candidate (6" and under), 1:12 is preferred above 6" when
  inputs are otherwise comparably close.
- Exact auto rate now preserves 3 decimal places for review (thousandths-friendly).
- Manual mode now rejects bare `1` as ambiguous, allows intentional `1/1`, requires
  a rate when one taper end must be derived, and warns when a typed manual rate does
  not match Length + SET + LET.
- Added `TaperRateAutoTest` coverage for snapping, exact fallback, alternate common
  lists, and invalid-input guards.

### fix: taper rate input accepts colon ratios on Android keyboards

- Taper rate fields now request an ASCII-capable keyboard so users can enter
  ratio forms like `1:12` even when the numeric keypad omits `:`.
- Numeric input filtering now supports an opt-in colon mode used by taper-rate
  inputs, while preserving existing numeric/fraction behavior for all other
  fields.
- Added focused unit coverage for input filtering in
  `app/src/test/java/com/android/shaftschematic/util/TextFiltersTest.kt`.

### fix: cleanup sweep wave 1 — 5 bugs + hot-path fixes (branch `fix/wave1-cleanup`)

From `docs/cleanup_sweep_2026-07-11.md` Part 1 and the Wave-1 one-liners:

- **Multi-step redo works** — replayed deletes no longer clear the redo stack
  (`isRedoing` guard in the five `removeX` paths); previously the first redo destroyed
  the remaining redo entries.
- **No more save-state crash from previews** — runout/wear preview bitmaps moved from
  `rememberSaveable` to `remember`; an `ImageBitmap` is not saveable and threw on
  backgrounding with a preview open.
- **Runout/wear exports match their previews** — the SAF export launchers now pass
  `pdfPrefs` and `lineThicknessScale` like the preview paths always did.
- **Autosave restores the full session** — `SessionSnapshot` now carries `runoutConfig`,
  `unitLocked`, and `overallIsManual` (older drafts still decode via defaults). Opening
  a document also derives the OAL-manual flag from the file (oversized OAL ⇒ manual)
  instead of leaking the previous session's flag — an authored oversized OAL can no
  longer be snapped down by the auto-sync on open.
- **PDF compression note matches the drawing** — the "compressed for clarity" footer
  note now tests the resolved body list the geometry pass actually drew.
- **Footer "Body:" line lists only drawn bodies** — it previously read raw `spec.bodies`,
  so a degenerate body row (zero-length, or fully swallowed by body subtraction under a
  liner/taper) printed a phantom Ø that appeared nowhere in the drawing or carousel.
  Now it lists authored bodies as actually drawn.
- **Preview hot path** — `layout.dbg()` no longer formats debug strings every pan/zoom
  frame when verbose logging is off; `RenderOptions` is remembered instead of rebuilt
  per recomposition.
- **Carousel pages keyed by component id** — per-page state (scroll, focus) follows the
  component when one is inserted/removed instead of bleeding to the neighbor.
- **Line-thickness sliders commit on release** — dragging no longer writes DataStore and
  re-renders the PDF preview on every frame (PDF preview sheet, runout/wear sheet,
  Settings).

### feat: classic S-break symbol on compression breaks (all three PDFs)

Long-body compression breaks now draw the full round-stock break symbol: the existing
S-curve plus a return sweep that starts at one tip of the S, arcs back on the opposite
side, and dies into the centerline — closing the "eye" that makes the break read as a
revolved 3D surface instead of a flat wave. The two edges of a break alternate (left
edge closes its eye at the bottom, right edge at the top), matching how the symbol is
drawn by hand. The eye is shaded with a light translucent wash (~18% black, the
shaded-body recipe) to deepen the 3D read. One shared `drawBreakEdge` in
`pdf/BreakSymbol.kt` replaces the three private copies in `ShaftPdfComposer`,
`RunoutPdfComposer`, and `WearPdfComposer` — the wear document's slightly different
double-wave variant now matches the other two documents.

### fix: audit remediation pass — 13 bugs + dead-code sweep (branch `fix/audit-remediation`)

Fixes every live bug found by the deep audit (`docs/deep_audit_2026-07.md`, Part 1 §1.0 B1–B13) plus the confirmed dead code. 379 unit tests green (23 net new).

**Data integrity**
- **Atomic saves** — `InternalStorage.save()` now writes to a `.tmp` file, keeps the previous version as `name.shaft.bak`, then swaps into place. A crash or disk-full mid-save can no longer corrupt the only copy of a document. `.tmp`/`.bak` siblings are invisible to the file list. (B5; new `InternalStorageAtomicSaveTest`.)
- **Corrupt files no longer crash the app** — both open paths (Open screen + Start-screen recents) now catch decode failures and show "file may be damaged" instead of throwing inside a coroutine. (B4)
- **Newer-format files are refused, not silently gutted** — `ShaftDocCodec.decode()` now checks the envelope `version`; files from a future app version throw `UnsupportedDocVersionException` (surfaced with an "update the app" message) instead of decoding with unknown fields dropped and destroyed on re-save. (B6; new `DocVersionTest`.)

**Geometry / domain correctness**
- **Taper-rate derivation is direction-aware** — `deriveTaperDiameters` previously assumed the start diameter was the *large* end, so an AFT-end taper entered as SET + rate derived an upside-down taper (LET smaller than SET). It now takes `smallEndAtStart` (classified by taper midpoint, matching the SET/LET labeling rule in `taperSetLetMapping`) and always derives SET < LET. Params renamed `setMm/letMm` → `startDiaMm/endDiaMm` to match what they actually are. (B3; `TaperRateTest` rewritten with AFT + FWD cases.)
- **FWD-referenced coupler slots re-anchor on OAL change** — `withNewOal()` now preserves a slot's authored distance from the FWD face (same rule as FWD-ref tapers/liners); previously slots silently drifted off the coupling when OAL changed. `shiftAllBy()` also includes slots now. (B1; new `WithNewOalTest` cases.)
- **Slot-only drafts are protected** — `isSessionDefault()` now checks `couplerBoltSlots`, so an autosaved draft containing only slots can't be clobbered by the restore path. (B2)
- **Coupler-slot bounds validation at add time** — `AddCouplerBoltSlotDialog` now blocks rows that bite past the AFT face or run past the FWD end (mirrors `CouplerBoltSlot.isValid`). (B7; new `CouplerBoltSlotTest` for the model.)

**PDF output**
- **Long text can't overrun footer columns** — footer lines (all three columns) and the runout/wear headers are now ellipsized to their column width via `ellipsizeToWidth()`. (B8)
- **One OAL number across all three documents** — the runout sheet and wear document now print the *typed* OAL as the label (arrows still bracket the drawn SET-to-SET span), matching the main schematic's "OAL label never changes" rule from `docs/OverallLength.md`. Previously they printed the SET-to-SET distance labeled "OAL". (B12; `RunoutSheet.md` updated.) **Review note:** this supersedes the older RunoutSheet.md convention — revert `RunoutPdfComposer`/`WearPdfComposer` call sites if SET-to-SET was intended.
- **Metric footers print pitch in mm** — thread callouts now show `2 mm pitch` in metric mode instead of TPI (TPI kept for inch mode). (B13; `FooterUnitsTest` strengthened.)
- **Slot cutouts use the drawn surface radius** — `drawCouplerBoltSlots` now takes the same body list the composer actually drew (resolved bodies incl. auto-bodies), so a slot over an auto-body region no longer falls back to the global max OD. (B9)
- **Wear OAL line anchors to the true outline top** — uses `maxOuterDiaMm()` (liners/tapers included) instead of body diameters only. (B10)

**UX**
- **"Save" in the unsaved-changes dialog no longer swallows the pending action** — with a known filename it quick-saves and continues the New/Open; otherwise the action resumes after a successful Save-As (dropped on cancel). (B11)
- Removed the debug pointer-event logging loop that ran on every carousel delete button.

**Dead code removed (~1,400 lines, zero callers, verified by grep + green build):** `ui/editor/ComponentCarousel.kt` (stale pre-refactor copy), `ui/drawing/LayoutMap.kt` + `ui/config/DisplayCompressionConfig.kt` + `ui/drawing/DrawingConfig.kt` (abandoned display-compression system), `data/ShaftRepository.kt` + `ShaftFileRepository.kt` + `NoopShaftRepository.kt` (unused repo layer), `data/MetaInfo.kt`, `pdf/ShaftPdfComposerCompat.kt`, `ui/nav/SafRoutes.kt`, `ui/input/NumberField.kt` (wrong package) + `Inputs.kt` + `ShaftMetaSection.kt` + `UnitSelector.kt`, `util/TaperParser.kt`, `util/HintStyle.kt`, and `geom.computeExcludedThreadLengths` (production-dead since the immutable-OAL fix; its two tests removed with it).

### feat: coupler bolt slots — radial muff-coupler bolt cutouts

New component type: **coupler bolt slots** — one axial row of radial bolt cutouts carved into the shaft at a muff-coupler location. Each cutout renders as a circle straddling the shaft outline (half in the shaft, half in the coupling), mirrored top and bottom, everywhere the shaft is drawn (preview + all three PDFs).

A coupler bolt slot is a **pure reference feature**: it never affects overall length (`coverageEndMm` ignores it), never splits bodies, and never collides with other components. This is the key simplification versus threads/liners.

**Model** — **`model/CouplerBoltSlot.kt`** (new) + `SlotAuthoredReference { AFT, FWD }`. Fields: `startFromAftMm` (first/aft-most cutout center), `holeDiaMm`, `count`, `spacingMm`, `through` + `depthMm` (blind), `authoredReference` (default **FWD**), `showDimensionRail` (deferred; off), `label`. Added `couplerBoltSlots: List<CouplerBoltSlot>` to `ShaftSpec` (defaults empty → back-compat, no migration) and wired `validate()`.

**Enum / branches** — `ComponentKind.COUPLER_BOLT_SLOT`; branches added in `ShaftSpecExtensions` (`segmentFor`/`withSegmentStart`), `StartOverlapValidation.collisionGroup()` (→ null, no collisions), and the `ShaftRoute` delete-snackbar label.

**Resolved layer** — `ResolvedCouplerBoltSlot`, resolved *after* body resolution so it never enters auto-body/subtraction geometry, then merged back by position for the carousel.

**ViewModel** — `addCouplerBoltSlotAt` / `updateCouplerBoltSlot` (+ `Reference`/`Label`/`ShowRail`) / `removeCouplerBoltSlot`; `LastDeleted.CouplerBoltSlot` undo/redo; ordering + session defaults. Deliberately does **not** call `ensureOverall()`.

**Render** — overlay pass in **`ui/drawing/render/ShaftRenderer.kt`** (preview) and `drawCouplerBoltSlots()` in **`pdf/ShaftPdfComposer.kt`**, reused by `RunoutPdfComposer` and `WearPdfComposer`. New `RenderOptions.slotFillColor`.

**UI** — "Coupler Bolt Slot" entry in `InlineAddChooserDialog`; new `AddCouplerBoltSlotDialog` (position from AFT/**FWD default**, hole Ø, count, spacing, through/blind + depth); carousel edit card with the same controls plus the deferred "show dimension rail" toggle. When FWD-referenced, the entered position locates the fwd-most cutout and the row extends aft.

**Deferred (v1):** the per-slot dimension rail is captured (`showDimensionRail`) but not drawn; through vs blind draw the same cutout for now. See `docs/archive/CouplerBoltSlot_Proposal.md`.

---

## 2026-06-23

### docs: CLAUDE.md + AddComponentDialogs.md — lock in dialog/card parity invariants

Added project-level documentation to prevent future regressions where controls present in carousel edit cards get accidentally dropped from their corresponding Add dialogs.

- **`CLAUDE.md`** (project root) — Claude Code loads this at the start of every session. Lists critical do-not-remove invariants: dialog/card parity, numeric commit guard, auto-body promotion, Free-to-End badge suppression, and commit policy.
- **`docs/AddComponentDialogs.md`** — New contract doc. Tables every field per dialog, states the parity rule explicitly, and includes a "Do Nots" section referencing the thread AFT/FWD regression as the canonical failure mode.
- **`docs/ShaftScreen.md`** — Updated to v0.8 with changelog entries for all 2026-06-23 fixes.
- **`docs/README.md`** — Added `AddComponentDialogs.md` to the index.

### fix: thread AFT/FWD end selector missing from Add Thread dialog

`AddThreadDialog` was missing the "Thread end: AFT | FWD" chip selector that appears in the carousel edit card when a thread is excluded from OAL. The feature existed in the card (`ComponentCarousel.kt`) but was never wired into the add dialog.

When `Count in OAL` is toggled off, the dialog now shows AFT/FWD chips and hides the Start field — matching the carousel card exactly. `isAftEnd` is passed through: `onSubmit → ShaftScreen.onAddThread → ShaftRoute → ShaftViewModel.addThreadAt()` and stored on the `Threads` model object.

**`ui/screen/AddComponentDialogs.kt`** — `AddThreadDialog`: added `isAftEnd` state, conditional Start/chips layout, updated `onSubmit` signature.  
**`ui/screen/ShaftScreen.kt`** — `onAddThread` signature updated.  
**`ui/screen/ShaftRoute.kt`** — passes `isAftEnd` to `vm.addThreadAt()`.  
**`ui/viewmodel/ShaftViewModel.kt`** — `addThreadAt()` accepts and stores `isAftEnd`.

### fix: auto-body not promoted on tap-and-leave; body length = 1 bug

`NumericInputField` previously called `onCommit` on every blur, even when the user hadn't changed the field's value. For auto-body carousel cards, this silently triggered `promoteIfNeeded()` — converting the virtual auto-body into a stored body using whatever dimensions were current at the time of the blur.

**Root cause**: The OAL field updates `spec.overallLengthMm` on every keystroke. While the user was typing a multi-digit OAL (e.g. "158.125"), the auto-body's span changed with each character, resetting its ID and therefore its `promoted` state (keyed on `component.id`). Any unfocused CommitNum field in the auto-body card would then commit — with the transient auto-body dimensions — and create a real body prematurely (e.g. with length = 1" when OAL was momentarily "1").

**Fix**: `NumericInputField` now captures the text value at focus-gain (`textWhenFocused`) and only calls `commitOrRevert()` on blur if the text actually changed. A tap-and-leave with no edit is a no-op. This prevents spurious auto-body promotion and avoids unnecessary model updates from unchanged fields throughout the app.

**`ui/input/NumericInputField.kt`** — added `textWhenFocused` state; blur handler now guards on `text.text != captured`.

### fix: numeric fields select-all on focus; zero clears in OAL field

Two input UX fixes:

- **Numeric input fields** (Start, Length, Ø, and all other `NumericInputField` instances) now select all text when focused. Typing immediately replaces the existing value without needing to manually clear it first. Implemented by switching `NumericInputField` from `String` to `TextFieldValue` state with a `TextRange(0, length)` selection on focus gain.
- **OAL field** now clears to empty when focused and the current value is "0" (new drawing default), preventing a leading zero from being prepended to the user's input.

**`ui/input/NumericInputField.kt`** — `String` state replaced with `TextFieldValue`; select-all on focus; `OutlinedTextField` switched to `TextFieldValue` overload.  
**`ui/screen/ShaftScreen.kt`** — OAL field `onFocusChanged`: clear text when value is `"0"` on focus gain.

### fix: Add Body defaults to remaining OAL length in manual mode

When the user taps `+ Add Component → Body` while in Manual OAL mode, the `AddBodyDialog` now pre-fills the Length field with the remaining shaft space (`OAL − startMm`) instead of the session default. This means a first body on a manually-sized shaft fills the full shaft length by default — avoiding the confusing state where the dialog opened with 16" on a 158" shaft.

In auto mode the session default is used unchanged.

**`ui/screen/ShaftScreen.kt`** — `chooserOpen` → `onAddBody` lambda: `tapAddGapMm` set to `spec.overallLengthMm - d.startMm` when `overallIsManual`.

### fix: Free-to-End badge hidden when only bodies are present

The Free-to-End badge in Manual OAL mode was showing a large "free" value even when the shaft was visually fully covered — e.g. "Free to end: 148.125 in" on a 158.125" shaft with a single 10" body. This was confusing because the auto-body system always generates a trailing virtual body from the last real component to the OAL, so the shaft appears completely filled in the preview.

**Root cause**: `lastOccupiedEndMm()` only counts stored (explicit) bodies; it did not see the auto-body covering the remainder. The badge correctly computed `OAL − realBodyEnd` but that gap was always covered visually.

**Fix**: `FreeToEndBadge` now returns early (hides) when there are no precision components (tapers, non-excluded threads, liners) **and** the shaft is not oversized. When only bodies exist, auto-bodies always cover the remainder up to OAL, so the badge value would always be misleading. The red/oversized warning still fires regardless, ensuring users are still told when a body exceeds the OAL.

**`ui/screen/ShaftScreen.kt`** — added early-return guard in `FreeToEndBadge`.  
**`docs/FreeToEndBadge.md`** — invariant documented.

### feat: Open page — search, sort by name/date, and date column in list

The Open drawing page previously showed a flat alphabetical list with no filtering or sorting. It now has:

- **Search field** at the top with a clear (×) button — filters by filename as you type, shows "No drawings match…" when nothing found.
- **Name / Date sort chips** — tap to switch column; tap again to flip direction (↑/↓). Date defaults to descending (most recent first). Name defaults to ascending.
- **Date column** under each filename — shows relative age ("Today", "Yesterday", "3w ago", etc.) so you can see at a glance when each drawing was last saved. The old "Open" text label is removed.
- File list now loads via `listWithMetadata()` so timestamps are always available.

**`ui/nav/InternalDocRoutes.kt`** — `files` changed from `List<String>` to `List<Pair<String,Long>>`; `displayFiles` derived state for filter+sort; search + sort header added as sticky `LazyColumn` item.

### feat: Start screen recent list — card layout, chevron, limit 3

The recent documents section on the Start screen was a plain divider list that didn't read as tappable and had misaligned text when names were short.

- Wrapped in a `Card` (surfaceVariant) for visual grouping.
- Name now fills available width (`weight(1f)`) so the relative date and chevron always right-align.
- `KeyboardArrowRight` chevron added to each row to signal tappability.
- Limit reduced from 5 → 3 files (reduces clutter for a tool where drawings are rarely revisited).

**`ui/screen/StartScreen.kt`** — card wrapping, weight fix, chevron icon, `take(3)`.

### refactor: extract ViewModel settings setters to ShaftViewModelSettings.kt

`ShaftViewModel.kt` was 2134 lines — a single class managing spec mutations, 40+ state flows, autosave, undo/redo, achievements, dev options, and persisted settings. The 237-line block of settings setter functions has been extracted to a new `ShaftViewModelSettings.kt` extension file in the same package.

32 private backing fields (`_openPdfAfterExport`, `_pdfTieringMode`, `_previewBlackWhiteOnly`, `_devOptionsEnabled`, verbose logging fields, achievement fields, etc.) were promoted to `internal` to allow the extension functions to access them. All callers outside the `ui.viewmodel` package received explicit imports.

**`ui/viewmodel/ShaftViewModelSettings.kt`** (new) — 237 lines; all settings setters + `syncVerboseLogConfig` as extension functions on `ShaftViewModel`.  
**`ui/viewmodel/ShaftViewModel.kt`** — 32 fields changed from `private` to `internal`; settings setter block removed; file reduced to ~1800 lines.  
**`ui/nav/InternalDocRoutes.kt`, `PdfExportRoute.kt`, `ui/screen/AboutRoute.kt`, `DeveloperOptionsRoute.kt`, `PdfPreviewScreen.kt`, `RunoutRoute.kt`, `SettingsRoute.kt`** — added `ui.viewmodel.*` imports so extension functions resolve outside their declaring package.

### test: fix stale test expectations after LengthFormat and UnitFormat updates

Two unit tests were asserting against formats that changed in later commits and were never updated:

- `LengthFormatTest` expected `"1 3/4"` but `formatInchesSmart()` now returns `"1 ¾"` (Unicode fractions added in a prior commit).
- `FooterUnitsTest` expected `" in"` unit suffix but `formatDiaWithUnit()`/`formatLenWithUnit()` now produce `"\""` (quote suffix, per shop convention).

Both test expectations updated to match current output. No production code changed.

### fix: remove deprecated `composed{}` in `clickableWithoutRipple` (analysis #12)

`Modifier.composed {}` is deprecated in Compose since Foundation 1.6. `clickableWithoutRipple` was using it unnecessarily — the wrapper added nothing over a direct `Modifier.clickable(interactionSource = null, indication = null)` call, which is valid in Foundation 1.7+ (BOM 2024.09.00 used in this project).

**`ui/screen/ShaftScreen.kt`** — `clickableWithoutRipple` rewritten without `composed {}`.

### chore: remove `-Xlambdas=class` Kotlin compiler flag (analysis #13)

`-Xlambdas=class` was added to work around a legacy Compose compiler issue. It forces lambda expressions to compile to anonymous classes, bypassing SAM/invoke-based inlining. The modern Kotlin 2.x + Compose K2 compiler handles this correctly without the flag; keeping it degrades runtime performance (more class loading, more GC pressure).

**`app/build.gradle.kts`** — removed `freeCompilerArgs.add("-Xlambdas=class")`.

### fix: reset dev-option sub-flags on startup when dev options disabled (analysis #14)

All 8 developer sub-flags (OAL debug label, helper line, preview box overlay, component debug labels, render layout overlay, OAL markers, verbose logging categories) persisted to DataStore across restarts. If a debug APK was handed to a customer with any flags enabled, they would remain active indefinitely.

`SettingsStore.resetDevSubFlagsIfDisabled(ctx)` now resets all 8 flags to false on startup unless dev options are explicitly enabled. Called from `ShaftViewModel.init` via `ShaftViewModelSettings.resetDevFlagsOnStartup()`.

**`data/SettingsStore.kt`** — added `resetDevSubFlagsIfDisabled()`.  
**`ui/viewmodel/ShaftViewModelSettings.kt`** — added `resetDevFlagsOnStartup()` extension; called from VM init.

### fix: LET/SET direction now determined from actual taper geometry (analysis #15)

The PDF footer labeled taper ends as "L.E.T." and "S.E.T." without stating which physical end was which. For coupling tapers (FWD taper, LET is at the FWD end) this was ambiguous and potentially misleading.

`letSet()` in `ShaftPdfComposer` now compares `startDiaMm` vs `endDiaMm` to determine which end is the larger (LET) and smaller (SET), then includes the direction label in the footer: `L.E.T. (AFT): …` / `S.E.T. (FWD): …`. Since `startDiaMm` is always the AFT-facing end of the taper model, the direction is deterministic.

**`pdf/ShaftPdfComposer.kt`** — new `LetSetResult` data class; `letSet()` rewritten to derive direction from geometry.  
**`pdf/FooterUnitsTest.kt`, `pdf/FooterOrderTest.kt`** — assertions updated from `"L.E.T.: "` / `"S.E.T.: "` to `"L.E.T. ("` / `"S.E.T. ("` pattern.

### feat: Save As and quick-save by document name (analysis #16)

The Save toolbar button now distinguishes between two states:
- **Named document** (opened from file or previously saved): silently overwrites the existing file without navigating to the name dialog.
- **Unsaved/new document**: navigates to the name dialog as before.

A new "Save As…" item in the overflow menu always navigates to the name dialog, allowing a renamed copy to be saved without overwriting the original.

`vm.currentDocumentName: StateFlow<String?>` tracks the active filename across open, save, rename, and recent-open operations. `vm.setCurrentDocumentName()` is called in all four code paths.

**`ui/viewmodel/ShaftViewModel.kt`** — `_currentDocumentName` StateFlow added; `setCurrentDocumentName()` and `newDocument()` reset added.  
**`ui/nav/AppNav.kt`** — `onSave` lambda split into quick-save vs navigate; `onSaveAs` added; recent-open path sets name.  
**`ui/nav/InternalDocRoutes.kt`** — `setCurrentDocumentName()` called after save (both overwrite and new-name paths) and after open.  
**`ui/screen/ShaftEditorRoute.kt`, `ShaftRoute.kt`, `ShaftScreen.kt`** — `onSaveAs` parameter threaded through.  
**`ui/screen/ShaftScreen.kt`** — `OverflowMenu` receives `onSaveAs` and shows "Save As…" item.

### fix: move "Highlight selection" toggle from editor to Settings (analysis #19)

The "Highlight selection in preview" Switch was rendered inline in the component editor body — a persistent setting that most users never change taking up vertical space in the editing area.

Moved to the Settings screen, persisted via `SettingsStore`, and backed by `vm.showHighlightSelection: StateFlow<Boolean>` that flows into `ShaftScreen`.

**`data/SettingsStore.kt`** — `KEY_SHOW_HIGHLIGHT_SELECTION`, `showHighlightSelectionFlow()`, `setShowHighlightSelection()` added.  
**`ui/viewmodel/ShaftViewModel.kt`** — `_showHighlightSelection` StateFlow; collector in `init`.  
**`ui/viewmodel/ShaftViewModelSettings.kt`** — `setShowHighlightSelection()` extension.  
**`ui/screen/ShaftRoute.kt`** — collects and passes `showHighlightSelection`.  
**`ui/screen/SettingsRoute.kt`** — Switch row added after showGrid toggle.  
**`ui/screen/ShaftScreen.kt`** — inline Switch removed from editor body; `showHighlightSelection` parameter added.

### fix: collapse double header into single TopAppBar (analysis #20)

The top of the editor screen had a `Text("Shaft Editor")` label stacked above a `TopAppBar(title = {})` with an empty title. The combined chrome wasted approximately 56dp of vertical space.

Collapsed into a single `TopAppBar(title = { Text("Shaft Editor") })` carrying all existing navigation icons and actions.

**`ui/screen/ShaftScreen.kt`** — `Column { Text + TopAppBar(title={}) }` replaced with `TopAppBar(title = { Text(...) })`.

### feat: custom labels for Body, Taper, and Thread components (analysis #18)

Bodies, Tapers, and Threads previously had auto-generated display names only ("Body #1", "AFT Taper", etc.). Liners already supported custom labels. All three component types now support optional user-defined labels.

Tap the card title to enter edit mode — an `OutlinedTextField` replaces the title, pre-filled with the current label (or blank for a new one). Focus-lost and Enter commit the label. Clear the field to revert to the auto-generated name.

`label: String? = null` is added to each data class with a default so existing serialized documents deserialize without error. `buildBodyTitleById`, `buildTaperTitleById`, and `buildThreadTitleById` now prefer the custom label when set.

**`model/Body.kt`, `model/Taper.kt`, `model/Threads.kt`** — `label: String? = null` field added.  
**`util/BodyTitles.kt`, `util/TaperTitles.kt`, `util/ThreadTitles.kt`** — custom label wins over auto-generated title.  
**`ui/viewmodel/ShaftViewModel.kt`** — `updateBodyLabel()`, `updateTaperLabel()`, `updateThreadLabel()` added.  
**`ui/screen/ComponentCarousel.kt`** — tap-to-edit label added to Body, Taper, Thread cards (same `titleContent` pattern as Liner); `onUpdateBodyLabel`, `onUpdateTaperLabel`, `onUpdateThreadLabel` threaded through.  
**`ui/screen/ShaftScreen.kt`, `ui/screen/ShaftRoute.kt`** — new label callbacks threaded through.

---

## 2026-06-22

### chore: auto-derive versionCode and versionName from git commit count

`versionCode` was a manually-maintained integer (`3`); `versionName` was a static `"1.2.0"`. Firebase App Distribution was labelling every upload as a duplicate because neither changed between builds.

Both are now derived from `git rev-list --count HEAD` at build time. The current count (244) becomes the patch digit:
- `versionCode = gitCount` (e.g., 244, 245, …)
- `versionName = "1.2.$gitCount"` (e.g., `1.2.244`, `1.2.245`, …)

Every commit automatically produces a uniquely-identified build with no manual editing. The major.minor (`1.2`) is still bumped manually when a breaking change or significant feature milestone warrants it.

**`app/build.gradle.kts`** — added `gitCount` exec block; replaced hardcoded `versionCode`/`versionName`.

---

### feat: Project Information moved to modal bottom sheet

Customer, Vessel, Job #, Shaft Position, and Notes were in a collapsible section inside the editor scroll area, consuming vertical space needed by the component carousel. They now live in a `ModalBottomSheet` opened from a new toolbar clipboard icon (Assignment). The scroll area is now entirely dedicated to components.

**`ui/screen/ShaftScreen.kt`** — removed `ExpandableSection("Project Information")` from scroll area; added `IconButton` (Assignment icon) to `TopAppBar`; added `ProjectInfoBottomSheet` composable with `rememberModalBottomSheetState(skipPartiallyExpanded = true)`.

---

### fix: FWD-reference taper length edit now keeps the FWD end anchored

Editing the length of a FWD-referenced taper was passing `startFromAftMm` through unchanged, so the FWD end drifted inward as the taper grew. The length commit handler now recomputes `physStart = OAL − authoredFromFwd − newLen` so the FWD face stays fixed and the AFT start slides.

**`ui/screen/ComponentCarousel.kt`** — `CommitNum("Length")` handler for tapers.

---

### fix: FWD-ref tapers and liners now stay anchored when OAL changes

`onSetOverallLengthMm()` and `ensureOverall()` were calling `.copy(overallLengthMm = …).syncExcludedThreadPositions()`. FWD-referenced tapers and liners have their start position stored physically from the AFT face, so a raw OAL change left them in place — drifting relative to the FWD face they were authored from.

New `ShaftSpec.withNewOal(newOal: Float)` recomputes `startFromAftMm` for every FWD-referenced taper and liner (`newStart = newOal − authoredFromFwd − length`) before calling `syncExcludedThreadPositions()`. Both OAL mutation paths now use it.

**`model/ShaftSpecExtensions.kt`** — added `withNewOal()`.  
**`ui/viewmodel/ShaftViewModel.kt`** — `onSetOverallLengthMm()` and `ensureOverall()` use `withNewOal()`.

---

### fix: excluded end thread shift no longer pushes shaft FWD edge past right margin

`ptPerMm` was computed from `overallLengthMm` alone, then the excluded-AFT-thread origin shift (`left += threadLen × ptPerMm`) consumed page width that wasn't accounted for in the scale. For the Siberian Sea shaft (OAL 147⅞", 4.5" prop thread), the FWD taper was drawing ~16pt past the right margin.

The fix computes the full content span — `contentMinMm` (excluded AFT thread tails, negative) through `contentMaxMm` (excluded FWD thread tails, past OAL) — before deriving `ptPerMm`. The shaft body's portion of that span is passed as `effectiveGeomWidthPt` to `computeDetailPtPerMm`, ensuring all content lands within `geomRect` regardless of thread count or length.

**`pdf/ShaftPdfComposer.kt`** — `contentMinMm`, `contentMaxMm`, `contentSpanMm`, `effectiveGeomWidthPt` computed before `ptPerMm`; bodies-only branch also updated.

---

### feat: body OD callouts on shaft drawing and footer

Body diameters were only visible inside the open carousel card. They now appear on the exported PDF in two places:

- **Drawing**: one leader-line callout per unique body OD (`Ø value`), placed above and below alternating for readability. Anchor is the center of the longest body section for that OD.
- **Footer center column**: "Body: Ø X, Ø Y" row listing all unique body ODs, appended after the date.

**`pdf/ShaftPdfComposer.kt`** — `buildBodyOdCallouts()` groups bodies by OD, picks longest anchor per group, alternates `LeaderSide.ABOVE/BELOW`; live `DiameterLeaderRenderer` call replaces the prior stub; `drawFooter()` adds body OD row.

**`test/pdf/BodyOdCalloutsTest.kt`** — 9 tests: empty/zero skip, single above, center placement, same-OD uses longest body, two ODs, alternating sides, three ODs cycle, OD accuracy.

---

### fix: float precision in inch↔mm conversions

Three conversion sites in `ShaftScreen.kt` used `25.4f` (Float literal) for inch↔mm math, introducing rounding error on common shaft dimensions. For example, `5 15/16"` (5.9375") via Float arithmetic can lose sub-thou accuracy that survives Double arithmetic.

All three sites now use the canonical `MM_PER_IN = 25.4` Double constant and promote through `Double` before rounding back to `Float`:
- `toMmOrNull()` — inch input to mm storage: `(num.toDouble() * MM_PER_IN).toFloat()`
- `formatDisplay()` — mm to inch display: `(valueMm.toDouble() / MM_PER_IN).toFloat()`
- `tpiToPitchMm()` — TPI to pitch mm: `(MM_PER_IN / tpi.toDouble()).toFloat()`

**`ui/screen/ShaftScreen.kt`** — three `25.4f` → Double arithmetic; added `MM_PER_IN` import.

**`test/ui/screen/UnitConversionTest.kt`** — 10 tests: mm passthrough, whole-number inch, common fractions (5 15/16, 1/8), blank/invalid null, tpiToPitchMm 16/20 TPI, formatDisplay round-trip.

---

### feat: recent documents list on start screen

The start screen now shows the 5 most recently modified shaft documents between the title and the New Drawing / Open… buttons. Tapping a recent entry loads it directly into the editor without going through the Open… dialog.

**`io/InternalStorage.kt`** — `listWithMetadata(dir)` returns `List<Pair<String, Long>>` (filename, lastModifiedMs) sorted newest-first, mirrors existing `list()` filter for `.shaft` and legacy `.json`.

**`ui/screen/StartScreen.kt`** — added `recentFiles` and `onOpenRecent` params; renders up to 5 recent entries with display name and relative date ("Today", "Yesterday", "N days ago", etc.).

**`ui/nav/AppNav.kt`** — start composable loads recent files on mount via `LaunchedEffect`; `onOpenRecent` handler loads file from storage and navigates to editor.

**`test/io/RecentFilesTest.kt`** — 7 tests: empty dir, single file, newest-first sort, non-shaft excluded, legacy json included, directories excluded, multi-file ordering.

---

### test: WithNewOalTest, PdfLayoutBoundsTest, BodySplitMergeTest

**`test/model/WithNewOalTest.kt`** — 17 tests: AFT components unchanged, FWD reanchors on grow/shrink, FWD flush with shaft face, mixed AFT+FWD spec, liner reanchoring and `endMmPhysical` consistency, excluded thread sync, OAL clamp, idempotency, old-copy regression.

**`test/pdf/PdfLayoutBoundsTest.kt`** — 9 tests: no-thread baseline, excluded AFT overflow regression (Siberian Sea), excluded FWD overflow, both ends, short shaft, very short/wide shaft (diameter-bound), variable AFT thread lengths.

**`test/model/BodySplitMergeTest.kt`** — 19 tests: mid-split, AFT/FWD edge inserts, full-consume, non-overlap, touching endpoints, multiple bodies, merge flanking/single-side/float-drift-tolerance, round-trips at center/AFT end/FWD end.

---

## 2026-06-19

### fix: updating a component no longer repositions other components

`updateBody`, `updateTaper`, `updateLiner`, and `updateThread` were calling `snapForwardFrom()` whenever the updated component's start or length changed, silently cascading position changes to every downstream component in the chain. This violated the fundamental invariant that component inputs are user-authored and must not be mutated by anything other than an explicit user action on that component.

The auto-snap block, `_autoSnap` state, and `setAutoSnap()` have been removed from all update paths. `snapChainFrom()` / `snapChainFromId()` remain as explicitly-invoked operations.

**`ui/viewmodel/ShaftViewModel.kt`** — removed `snapForwardFrom` cascade and `_autoSnap` flag from all four `updateX()` functions.  
**`test/ui/viewmodel/ShaftViewModelUpdateTest.kt`** — 9 new tests covering liner, body, taper, thread, and mixed-spec update isolation.

---

### fix: PDF dimension unit suffix changed from " in" to `"`

All inch-unit dimension labels now use the standard `"` suffix instead of ` in` (e.g., `4.997"` not `4.997 in`). Applies to diameters, lengths, and OAL labels across the shaft, runout, and wear PDF composers.

**`pdf/UnitFormat.kt`** — `formatDim()`, `formatLenDim()`, `formatLenWithUnit()`, `formatDiaWithUnit()`.  
**`pdf/RunoutPdfComposer.kt`**, **`pdf/WearPdfComposer.kt`** — OAL display line.

---

### fix: common inch fractions render as Unicode symbols in PDF dimensions

`LengthFormat.formatInchesSmart()` now substitutes common fractions with Unicode characters (½ ¼ ¾ ⅛ ⅜ ⅝ ⅞) so dimension text reads like hand-drawn notation rather than `3/4` or `7/8`.

**`util/LengthFormat.kt`** — `unicodeFractions` map applied in `formatInchesSmart()`.

---

### feat: taper AFT/FWD reference toggle in carousel edit card

The taper carousel card now shows a "Measure From: AFT / FWD" chip row (matching the liner card). Selecting FWD lets the user enter the start distance from the FWD end; the model always stores the canonical `startFromAftMm`. `Taper.authoredReference` (new field, default AFT) persists the user's choice so the field label and value are correct on re-open.

**`model/Taper.kt`** — added `authoredReference: LinerAuthoredReference` field.  
**`ui/screen/ComponentCarousel.kt`** — AFT/FWD toggle + start field adapts label and converts value.  
**`ui/screen/ShaftRoute.kt`** — wires `onUpdateTaperReference` callback.  
**`ui/viewmodel/ShaftViewModel.kt`** — `updateTaperAuthoredReference()`.  
**`docs/Model_Conventions.md`** — updated.

---

### fix: auto-snap removed from all component delete paths

The snap-forward-on-delete behavior (shifting subsequent components left after a deletion) has been removed from `removeBody()`, `removeTaper()`, `removeThread()`, and `removeLiner()`. Body split/merge (added earlier) makes positional snap on delete incorrect — merged bodies already fill the freed span.

**`ui/viewmodel/ShaftViewModel.kt`** — removed `snapFromKey` / `snapFromOrigin` logic from all four remove functions.

---

### fix: PDF footer columns positioned at even thirds, all left-aligned

The three footer columns (AFT, project info, FWD) were computed with an asymmetric gutter formula that bunched the center and right blocks toward the left half of the page. Replaced with clean thirds: `colW = rect.width() / 3`, anchor each column at `rect.left + n × colW`. All columns remain left-aligned.

**`pdf/ShaftPdfComposer.kt`** — simplified column layout in `drawFooter()`.

---

### fix: PDF footer shows authored taper rate text instead of computed 1:N ratio

The `Rate:` line in the AFT/FWD taper footer blocks was always re-derived via `rate1toN()` (e.g. `1:16`), ignoring the `taperRateText` field the user typed (e.g. `3/4"/FT`). The authored string is now used when non-empty; `rate1toN()` is the fallback.

**`pdf/ShaftPdfComposer.kt`** — `buildFooterEndColumns()` uses `tp.taperRateText.trim().ifEmpty { rate1toN(tp) }` for both AFT and FWD taper rate lines.

---

### fix: consolidate conflicting EPS constants in PDF composer

`ShaftPdfComposer.kt` had two proximity tolerances with overlapping scope: `END_EPS_MM = 0.5` (imported from `geom/OalComputations.kt`) and a private `EPS_MM = 0.01`. The 50× discrepancy meant that `detectEndFeatures()` and `getAftEndThread()` / `getFwdEndThread()` / `getAftEndTaper()` / `getFwdEndTaper()` used different thresholds for what counts as "at the shaft end", potentially causing mismatches between which features show up in the footer. Removed `EPS_MM`; all proximity checks now use `END_EPS_MM`.

**`pdf/ShaftPdfComposer.kt`** — removed `private const val EPS_MM`; replaced four usages with `END_EPS_MM`.

---

### fix: Project Information section expanded by default

Customer, Vessel, and Job # were hidden behind a collapsed section on every new drawing, adding friction at job-start. The section now opens expanded.

**`ui/screen/ShaftScreen.kt`** — `ExpandableSection("Project Information", initiallyExpanded = true)`.

---

### feat: body auto-split on add, auto-merge on delete

Adding any taper, liner, or thread now splits any overlapping body into two independent fragments (each keeping the parent's `diaMm` and a new UUID). Deleting a taper/liner/thread merges the flanking body fragments back into one body (merged diameter = max of the two). Single-side boundary case: the lone adjacent body expands to fill the freed span rather than merging.

**`model/ShaftSpecExtensions.kt`** — new `splitBodyAt()` and `mergeAdjacentBodies()` functions.  
**`ui/viewmodel/ShaftViewModel.kt`** — all `add*At()` / `delete*()` paths call split/merge; included in the undo snapshot.

---

### feat: full keyway inputs in Add Taper dialog

`AddTaperDialog` gains KW Width, KW Depth, KW Length, KW Offset, and Spooned toggle fields, mirroring the carousel edit card. Previously these were only editable after adding.

**`ui/screen/AddComponentDialogs.kt`**

---

### fix: add dialogs always open; bodies and excluded threads excluded from default-start

The FAB chooser previously quick-added bodies, liners, and tapers without showing a dialog. All paths now open the full dialog. `computeAddDefaults()` no longer counts bodies or excluded threads when finding the next open slot — they were pushing new component start positions past the shaft end. Body–taper pairs removed from `collidingIds()` (bodies are fillers; taper overlap is intentional). All `add*At()` methods now auto-select the newly created component.

**`ui/screen/ShaftScreen.kt`**, **`ui/viewmodel/ShaftViewModel.kt`**, **`model/ShaftSpecExtensions.kt`**  
**`docs/DATA_MODEL.md`**, **`docs/UI_CONTRACT.md`**, **`docs/VALIDATION_RULES.md`** updated.

---

### fix: direction chip selected state uses border, not fill

`DirectionChip` (AFT/FWD toggle in Add Taper and Add Liner dialogs) replaced `FilterChip` with a custom `OutlinedButton`: selected state shows a 2dp primary-color border + tinted container; unselected has no border. Previously the outlined border on the unselected chip made it visually appear to be the active choice.

**`ui/screen/AddComponentDialogs.kt`**

---

### fix: PDF dimension arrows default inward; arrow size reduced

Arrow tips were flipping outward by an overly strict threshold. `canFitInwardArrows` loosened from `spacing × 1.5` to `spacing × 1.0` so arrows now default inward (engineering convention) and flip outward only when truly cramped. Arrow size reduced from 7 → 5 pt to match hand-sketch reference drawings.

**`pdf/render/PdfDimensionRenderer.kt`**

---

### fix: PDF export no longer rejects excluded threads as out-of-bounds

`blockingExportError()` was triggering "start must be ≥ 0" on excluded threads, which deliberately have `startFromAftMm = −lengthMm`. Excluded threads are now skipped in that check.

**`ui/nav/PdfExportRoute.kt`**

---

### fix: `CommitNumField` commits on every keystroke; external resets don't jump cursor

Values were lost when tapping "Add" while a text field was still focused (the on-blur commit hadn't fired yet). `CommitNumField` now commits on every keystroke. `LaunchedEffect(initial)` handles external value resets without moving the cursor mid-type.

**`ui/screen/AddComponentDialogs.kt`**

---

### fix: excluded thread flashes at shaft face during carousel swipe

In manual OAL mode, `updateThread()` wrote `effectiveStart = 0f` for AFT excluded threads as a temporary value, expecting `ensureOverall()` → `syncExcludedThreadPositions()` to correct it. `ensureOverall()` exits early in manual mode without calling sync, so the `0f` position persisted — placing the thread at the shaft AFT face and causing it to visually overlap the adjacent taper for a single frame. The trigger: `NumericInputField.onFocusChanged` fires a commit when the carousel's `HorizontalPager` clears focus from the excluded-thread card while the user swipes to the adjacent taper card.

Fix: `updateThread()` now derives the correct position (`−lengthMm` for AFT, `overallLengthMm` for FWD) directly inside the `_spec.update {}` call, using the same formula as `syncExcludedThreadPositions()`. The position is always correct regardless of OAL mode, with no transient wrong state.

**`ui/viewmodel/ShaftViewModel.kt`** — `updateThread()` `effectiveStart` for excluded threads.

---

### fix: PDF footer FWD column nudged to 76% of content width

The FWD footer column was at 72% of the content area width; adjusted to 76% for a more balanced three-column layout with the AFT block anchored at the left margin and center block at 40%.

**`pdf/ShaftPdfComposer.kt`** — `rightX = rect.left + rect.width() * 0.76f` in `drawFooter()`.

---

## 2026-06-18

### feat: taper/liner direction toggle; excluded thread rendering; add-time collision warnings

**Direction toggles in add dialogs**
- `AddTaperDialog` — AFT/FWD chip controls which end is the SET. SET/LET labels on the diameter fields swap accordingly; model stores diameters in AFT→FWD order regardless.
- `AddLinerDialog` — "Measure From" AFT/FWD chip writes `LinerAuthoredReference` through `ShaftRoute` → `ShaftViewModel.addLinerAt()` so the carousel card reflects the chosen reference on first render.

**Excluded thread rendering**
- `syncExcludedThreadPositions`: AFT excluded threads placed at `startFromAftMm = −lengthMm`, FWD at `OAL`, sitting flush with the shaft face without overlapping tapers.
- `ShaftLayout.compute`: `minXMm` / `maxXMm` now expand to include excluded threads outside `0..OAL` so they render in both the preview and PDF without clipping.

**Add-time collision warnings**
- New `collectAddWarnings()`: pre-submit overlap check in Taper, Liner, and Thread add dialogs. Warns on cross-type overlaps and shaft bounds when OAL is manual. Bodies and excluded threads are skipped. Warning confirmation dialog offers "Add Anyway / Cancel" — nothing is silently blocked.

**Carousel auto-scroll fix**
- `ComponentCarousel`: size-based auto-scroll `LaunchedEffect` is now conditional on no existing selection, preventing it from overriding the user's swipe after adding a component.

**Tests** — `CollisionWarningsTest` (13 cases), `ShaftSpecTest` +`syncExcludedThreadPositions` (4 cases), `ShaftLayoutTest` +excluded-thread coordinate expansion (4 cases). All passing.

**`model/ShaftSpec.kt`**, **`ui/drawing/render/ShaftLayout.kt`**, **`ui/screen/AddComponentDialogs.kt`**, **`ui/screen/ComponentCarousel.kt`**, **`ui/screen/ShaftRoute.kt`**, **`ui/screen/ShaftScreen.kt`**, **`ui/util/CollisionWarnings.kt`** (new), **`ui/viewmodel/ShaftViewModel.kt`**  
**Docs**: `ShaftLayout v0.4`, `Model_Conventions v0.2`, `ShaftViewModel v0.2`, `ShaftScreen v0.7`, `VALIDATION_APPENDIX`.

---

### ci: Firebase App Distribution workflow

Added GitHub Actions workflow for distributing debug APKs to testers via Firebase App Distribution on every push to `main`. Uses service-account auth via the Firebase CLI.

---

## 2026-06-11

### fix: OAL bracket moves with include/exclude; label is always the typed value

The `excludeFromOAL` toggle on end threads now correctly controls **bracket position only** — the OAL label is always `spec.overallLengthMm`, the value the user typed.

- **Excluded**: bracket spans AFT SET → FWD SET. Thread is drawn outside the bracket.
- **Included**: bracket spans shaft AFT end → FWD SET, grouping the thread inside the arrow.
- Label never changes in either case. Component measurements always reference SET.

Domain rationale: threads don't need to be a specific length on a new shaft; liners and tapers do. The toggle exists for customers (e.g. Coast Guard) who specify exact total lengths so shafts are interchangeable spares. Nothing is ever dimensioned from a thread end.

**`pdf/dim/LinerSpanBuilder.kt`** — `oalSpan()` gains an explicit `labelMm` param (default = bracket width) so the label can be decoupled from the bracket span.  
**`pdf/ShaftPdfComposer.kt`** — bracket endpoints driven by include/exclude; `labelMm = spec.overallLengthMm` always.  
**`geom/OalComputations.kt`** — `computeOalWindow` always returns `(0.0, overallLengthMm)`; `computeExcludedThreadLengths` retained for future SET-to-SET annotation work.

---

## 2026-06-11

### feat: runout screen v2 — inline preview + layout overhaul

- **`RunoutRoute.kt`** — complete rewrite: `RunoutComponentEntry` data class, inline shaft preview via `ShaftRenderer`/`ShaftLayout`, scrollable column layout, sidebar nav integration, `resolvedComponents` support.
- **`ComponentCarousel.kt`** — removed bubble-count stepper controls (95 lines). Bubble counts are managed through the runout config; per-component stepping in the carousel was redundant.
- **`ShaftRoute.kt` / `ShaftScreen.kt`** — removed `runoutConfig` and `onSetRunoutBubbleCount` threading that was coupling the main screen to runout state. `ComponentCarousel` retains defaulted params for backward compatibility.

---

### feat: line thickness control

- **`SettingsRoute.kt`** — `LineThicknessControl` composable: slider (50%–200%) + typeable `%` field with on-blur clamping. 100% = new default thin weight; 200% = original thick weight.
- **`SettingsStore.kt`** — `KEY_LINE_THICKNESS_SCALE` DataStore key; `lineThicknessScaleFlow()` / `setLineThicknessScale()`.
- **`ShaftViewModel.kt`** — `lineThicknessScale: StateFlow<Float>`, collected from DataStore on init, exposed for UI and PDF export.
- **`ShaftPdfComposer.kt`** — `OUTLINE_PT_BASE = 1.25 pt`, `DIM_PT_BASE = 0.8 pt` (100% defaults). `composeShaftPdf()` gains `lineThicknessScale` param; scale applied to both paint objects.
- **`ShaftDrawing.kt`** — `outlineWidthPx = 2f * lineThicknessScale.coerceIn(0.5f, 2.0f)`.
- **`PdfExportRoute.kt` / `PdfPreviewScreen.kt`** — pass `lineThicknessScale` through to the composer.

---

### fix: OAL dimension respects include-thread toggle

The PDF OAL dimension arrow previously always measured **SET to SET** regardless of whether end threads were marked as included in OAL. Root cause: when `excludeFromOAL = false` the coordinate origin shifts by `threadLength`, so both SET endpoints moved by the same delta and the rendered distance was unchanged.

Fix in `ShaftPdfComposer.kt`: detects any end thread with `!excludeFromOAL` anchored to position 0 (AFT) or `overallLengthMm` (FWD), and substitutes the physical shaft end coordinate (`0.0` or `win.oalMm`) for the SET coordinate in the `oalSpan()` call. Component dimension rails continue to reference SET positions.

---

## 2026-05-30 (6)

### feat: yellow warning badges — non-blocking validation now visible in UI

- **`ComponentWarnings.kt`** — new utility with per-component warning functions:
  - Any component with `0 < lengthMm < 1 mm` → "Very short segment (< 1 mm)"
  - Thread with `pitchMm == 0` → "Zero pitch — thread renders flat"
- **`ComponentCard`** gains a `warningMessage: String?` slot rendered as a yellow
  `tertiaryContainer` chip below the title, distinct from the red error chip.
  Body, Taper, Thread, and Liner cards all pass their computed warning.
- **`FreeToEndBadge`** now has three states: normal → yellow (0–10 mm clearance) → red (negative/oversized). Previously only normal and red.
- Stale `TODO.md` entries for keyway drawing marked complete.

---

## 2026-05-30 (5)

### fix: selection highlight — single thin ring instead of double box

- Removed the inner white "edge" ring from the two-ring highlight system.
  Only the outer cyan glow ring is drawn now, giving a single clean selection
  box that doesn't compete visually with component lines (keyways, threads, etc.).
- Reduced `highlightGlowExtraPx` from 8 → 2 px so the ring is noticeably
  thinner while still clearly marking the selected component.

---

## 2026-05-30 (4)

### fix: shared app signing + corrected keyway schematic convention

#### Signing
- Committed `debug.keystore` to the project root so every machine that clones
  the repo signs with the same key. Android now treats sideloaded builds from
  any machine as app updates rather than new installs — no more uninstall/data-wipe
  when switching computers.
- Added `signingConfigs.shared` in `build.gradle.kts` (debug + release both use it).
- `.gitignore` updated with `!debug.keystore` exception; release keystores remain blocked.

#### Keyway rendering — full rewrite to match shop schematic convention
- **Previous behaviour:** drew a notch cutting down from the top surface of the
  taper, showing depth. Wrong axis and wrong convention.
- **Correct convention** (confirmed from shop hand-drawings in `assets/`): the keyway
  is shown as a **plan-view rectangle centred on the shaft centreline** — height
  represents keyway **width** (W) to scale, horizontal span represents keyway
  **length** (L) to scale. Depth is never drawn; it appears only in the PDF footer text.
- The closed (LET) end uses a **concave semicircle** matching the mill-cutter profile.
  For floating keyways both ends are semicircular; for open keyways the SET face is
  already closed by the taper's end-face line.
- Interior filled **white** so the keyway reads as a void against any taper fill colour
  (steel grey, bronze, etc.). Fill is inset one line-width from the SET face so the
  taper's end-face line retains its full visual weight.
- Fix applied identically to `ShaftRenderer` (preview) and `ShaftPdfComposer` (PDF).

---

## 2026-05-30 — Carousel extraction refactor

Extracted the component carousel out of `ShaftScreen.kt` into `ui/screen/ComponentCarousel.kt`.

**Moved to `ComponentCarousel.kt` (~740 lines):**
- `ComponentCarouselPager` — pager, selection seeding, swipe detection, LaunchedEffects
- `EdgeNavButton` — left/right arrow buttons
- `ComponentPagerCard` — per-component editor content (Body, Taper, Thread, Liner)
- `ComponentCard` — shared card chrome (title, error/warning chips, delete button)
- Carousel-private helpers: `CommitNum`, `dispKw`, `fmtTrim`, `pitchMmToTpi`, `CAROUSEL_HEIGHT`

**Stayed in `ShaftScreen.kt` (1434 lines, down from 2322):**
- All screen-level composables (header, preview, OAL badge, dialogs, FAB)
- Shared display helpers promoted from `private` to `internal`: `abbr`, `disp`, `formatDisplay`, `toMmOrNull`, `parseFractionOrDecimal`, `tpiToPitchMm`

No behaviour changes. All unit tests pass.

---

## 2026-05-30 — Doc refresh

Updated TODO.md, BRIEFING.md, and ROADMAP.md to reflect current state:
- TODO restructured around v0.5.x sprint (ShaftScreen refactor as §1). All completed v0.4.x work collapsed. Stale entries removed. Body keyway formally shelved.
- BRIEFING.md: status table updated with validation, keyways, and signing; architecture invariant corrected (dual rendering paths); component model table updated with keyway fields; active sprint section rewritten.
- ROADMAP.md: v0.4.x marked complete; v0.5.x deliverables documented; v1.0 definition of done updated.

---

## 2026-05-30 (8) — fix: sidebar UX, toolbar hamburger, runout PDF layout

### Sidebar
- **Hamburger button** replaces the Home icon in the top toolbar. Tapping it opens the sidebar overlay — no persistent rail taking up horizontal space.
- **Home button removed from toolbar** — it now lives only inside the sidebar (not duplicated).
- **Thin handle tab removed** — the sidebar is opened exclusively via the toolbar button.
- `navigationBarsPadding()` added inside the sidebar panel so Settings is never hidden under the system navigation bar.
- `statusBarsPadding()` was already present, keeping the title clear of the status bar.

### Runout PDF — complete layout rewrite
- **Bubble collision eliminated**: Bubbles are no longer placed directly below their station's axial position. Instead a fan-spread algorithm distributes bubble X positions evenly across the page width, guaranteeing no circles overlap.
- **Monotonic assignment**: Even-indexed stations → row 0 (shorter leaders), odd-indexed → row 1 (longer leaders). Because the mapping is monotonic (station order = bubble order), leader lines cannot cross each other — they fan out cleanly, exactly matching the hand-drawn reference.
- **Leaders touch the shaft**: Each leader now starts from the shaft's ACTUAL outer surface at the station's axial position (interpolated through tapers), not from a fixed maximum-diameter y.
- **Shaft centred vertically**: The shaft profile is now sized from its real maximum outer diameter and centred in the upper portion of the page, with the bubble area and TIR line filling the lower portion.

### TIR direction label (RunoutRoute)
- Label text corrected to "Looking AFT" / "Looking FORWARD" with an explanation that this determines clock-position reference (3 o'clock looking aft ≠ 3 o'clock looking forward).

---

## 2026-05-30 (7) — feat: runout drawing + wear document + sidebar nav

### Navigation
- New collapsible **sidebar icon rail** in `ShaftEditorRoute` (always visible, 52 dp collapsed).
  Three tabs: **Schematic** (always enabled), **Runout Sheet** and **Wear Document** (enabled
  once the shaft has ≥1 component and a non-zero OAL). Tab state survives configuration changes.
  Files: `EditorTab.kt`, `EditorSidebar.kt`, `ShaftEditorRoute.kt`.

### Data model
- `RunoutConfig` — new serializable data class persisted in every `.shaft` file:
  - `componentOverrides: Map<String, Int>` — per-component bubble count overrides.
  - `tirDirection: TirDirection` — AFT / FORWARD / UNSET; printed on the runout sheet.
- `TirDirection` enum in `settings/RunoutConfig.kt`.
- `ShaftDocCodec.ShaftDocV1` gains `runout_config` field (default = empty → backward-compat).
- `ShaftViewModel` gains `_runoutConfig` StateFlow, `setRunoutBubbleCount()`, `setTirDirection()`.
  Config is saved in `exportJson()`, restored in `importJson()`, reset in `newDocument()`.

### Runout PDF (`pdf/RunoutPdfComposer.kt`)
Page: landscape US Letter. Regions top→bottom:
1. **Header strip** — Customer, Vessel, Job#, Date, STBD/PORT, OAL in a single compact line.
2. **OAL span line** — Single arrow-to-arrow dimension, SET to SET only.
3. **Shaft profile** — Bodies (with compression breaks), tapers (with keyway indicators),
   liners. No dimension tiers, no component labels.
4. **Bubble area** — Each component's stations drawn as circles with diagonal leader lines.
   - Tapers: N stations (default 2) inset `RUNOUT_EDGE_INSET_MM` (25.4 mm / 1 inch) from
     each edge — readings on the edge face are unreliable.
   - Liners: same inset convention.
   - Bodies: N stations (default 3) evenly distributed, no inset.
   - Within each component, stations alternate row 0 (short leader) and row 1 (long leader)
     to avoid horizontal overlap between adjacent circles.
   - Small filled square at the top of each circle = keyway-at-top reference marker.
5. **TIR line** — "TIR's taken looking: ___" with optional direction label.

### Wear document PDF (`pdf/WearPdfComposer.kt`)
Same shaft profile + compact header, no bubbles. Dye-pen PASS/FAIL checkboxes + notes fill-in
line at the bottom. For hand-annotating damage, pitting, and inspection results in the field.

### Carousel changes (`ComponentCarousel.kt`)
- `RunoutStationControl` composable added to Body, Taper, and Liner cards. Shows
  "Runout stations: N [−] [+]" using the effective count (override or default).
- `ComponentCarouselPager` and `ComponentPagerCard` gain `runoutConfig` and
  `onSetRunoutBubbleCount` params (both defaulted — backward-compat).

### Screen routing
- `RunoutRoute.kt` — TIR direction selector + Export button; writes runout PDF via SAF.
- `WearRoute.kt` — Export button; writes wear document PDF via SAF.
- `ShaftRoute.kt` — wires `runoutConfig` and `onSetRunoutBubbleCount` from ViewModel.

---

## Versioning Notes

- Early development used git tags (`v0.2.0`, `v0.3.1`) for milestones.
- Starting with `1.1.1`, the changelog and the app `versionName` are kept in sync; future releases follow this convention.
- Note: `v0.2.0` and `v0.3.0` point to the same commit (`d1a4da5`).

## 2026-05-30 (3)

### feat: keyway drawing on taper — open and floating keyway styles

#### Model
- `Taper` gains `keywayOffsetFromSetMm: Float = 0f` (backward-compatible; default 0 = open keyway at SET face).
- `hasKeyway` extension property: true when width, depth, and length are all non-zero.
- `isValid` now enforces `offset >= 0` and `offset + length <= taperLength`.

#### Two keyway styles
- **Open keyway** (`offset = 0`, 95% case): slot starts at the SET face, open-ended there, wall only at the LET side. The Spoon toggle applies here.
- **Floating keyway** (`offset > 0`, 5% case): slot is inset from the SET face, walls on both sides. Spoon toggle is disabled and grayed in the UI.

#### Rendering
- `ShaftRenderer` draws the keyway notch on the taper's top surface in the preview: fills the notch area with the taper fill color (erasing the top outline inside the slot), redraws the top line in the two segments outside the slot, then draws walls and floor in the outline color.
- `ShaftPdfComposer` draws the same notch on the PDF canvas using a white fill to erase the top line inside the slot, with the same wall/floor logic.
- The notch floor follows the taper slope (drawn as a diagonal line matching the top surface angle).

#### UI
- Carousel taper card gains **"KW Offset from SET"** field between Length and the Spoon toggle.
- Spoon toggle is automatically disabled when offset > 0 (floating keyway has no open face to spoon).

#### Tests
- `TaperKeywayTest`: 11 cases covering `hasKeyway`, offset validation, boundary conditions, and backward-compat default.

---

## 2026-05-30 (2)

### feat: validation UI hookup — blocking errors surface in dialogs, cards, and export

#### Add dialogs (Liner + Thread)
- `CommitNumField` inside Add dialogs now accepts an `errorText` parameter and shows it in red below the Start field using `OutlinedTextField`'s `isError`/`supportingText`.
- `AddLinerDialog` and `AddThreadDialog` compute `startOverlapErrorMm` live as fields change. The **Add button is disabled** when a blocking start error exists (overlap, negative start, thread-between-components). The error message appears immediately on the start field so the user knows why.

#### Carousel component cards
- `ComponentCard` gains an `errorMessage: String?` parameter. When non-null, a Material 3 error-container chip is rendered below the card title.
- Thread and Liner cards pass their current `startOverlapErrorMm` result to this slot, so cards with placement errors show a visible red badge at all times.

#### PDF export gate
- `PdfExportRoute` now calls `blockingExportError(spec)` before launching the SAF file picker. If any thread or liner has a blocking validation error the picker is never opened; instead an `AlertDialog` displays the error message and returns the user to the editor on dismiss.

---

## 2026-05-30

### fix: selection box not shown on initial swipe after opening a file

- `ComponentCarouselPager` now seeds `selectedComponentId` immediately when components first load (via the existing `LaunchedEffect(rowsSorted.size)` that auto-scrolls to the last card), so the highlight glow appears as soon as the carousel is visible.
- Fixed swipe detection guard: the `pagerScrollStartedByUser` flag was only set when `selectedIndex == pagerState.currentPage`, but with no selection `selectedIndex` was `-1`, so all swipes were silently ignored. The guard now also triggers when `selectedComponentId` is `null`.

---

## 2026-05-29

### feat: tap-to-add pipeline, thread validation, taper-rate restoration, pdfPrefs persistence

#### Tap-to-add pipeline (TODO §1.2 + §1.3)

- `ShaftDrawing` now accepts an `onTapAtMm` lambda. Taps that land on an existing component still fire `onTapComponentId`; taps on empty space fire `onTapAtMm` with the raw mm coordinate.
- `ShaftViewModel` gains `pendingAddPositionMm: StateFlow<Float?>`, `setTapAddPosition(rawMm)` (snaps via `snapRawPositionMm` before storing), `clearPendingAddPosition()`, and `gapToNextAnchorMm(positionMm, min=50f)` (distance to next snap anchor, minimum 50 mm).
- `ShaftRoute` wires the three new callbacks to `ShaftScreen`; `pendingAddPositionMm` and the computed gap length are passed down as parameters.
- When `pendingAddPositionMm` is non-null `ShaftScreen` shows `InlineAddChooserDialog`. Selecting Body, Liner, or Taper opens the corresponding add dialog with the tapped position pre-filled in the Start field and the gap length pre-filled in the Length field. Thread routes through the existing `AddThreadDialog` with the tapped start.
- `AddBodyDialog`, `AddLinerDialog`, and `AddTaperDialog` each gain optional `initialStartMm` and `initialLengthMm` overrides that take precedence over the spec-derived defaults when provided.

#### Thread start/placement fixes (TODO §2.x)

- **End-snap bug fixed:** `applySnappedThreadUpdate` previously snapped both the start and end positions independently. This could silently extend a thread's length when the derived end position happened to land within snap tolerance of a body boundary (e.g. a 99 mm thread moved to start=0 would snap its end to the 100 mm body anchor, becoming 100 mm). The function now snaps only the start and preserves the original length.
- **"Threads at ends only" validation rule implemented:** `startOverlapErrorMm` returns `"Thread must be at a shaft end, not between components"` when a thread has a Body or Liner ending at-or-before its start *and* another Body or Liner starting at-or-after its end (i.e. surrounded on both sides). Adjacency is handled with a 1 mm epsilon so end-to-start touching qualifies.

#### Taper-rate restoration (TODO §3.2)

- `Taper` model gains a `taperRateText: String = ""` field (kotlinx.serialization `@Serializable`; backward-compatible default `""`).
- `ShaftViewModel` companion exposes `parseRateText(text)` — parses `1:12`, `3/4`, decimals, and bare integers (bare int N interpreted as 1:N) — and `deriveTaperDiameters(setMm, letMm, lengthMm, rateText)`: if both SET and LET are > 0 the rate is ignored; if only one diameter is provided the missing one is derived from the rate and length; zero length or unparseable rate returns diameters unchanged.
- `addTaperAt` and `updateTaper` accept an optional `rateText: String = ""` and call `deriveTaperDiameters` before storing. `updateTaper` also falls back to the taper's stored `taperRateText` when the caller passes a blank rate.
- The taper carousel card has a new `Rate (1:12, 3/4, or decimal)` commit field; all `onUpdateTaper` call sites pass the stored `taperRateText` through.
- `onAddTaper` / `onUpdateTaper` callbacks throughout the stack (`ShaftScreen`, `ShaftRoute`) updated from `(Float, Float, Float, Float)` to `(Float, Float, Float, Float, String)`.
- `TaperRateTest.kt` — 9 new unit tests covering `parseRateText` (colon, slash, decimal, bare int, blank, invalid) and `deriveTaperDiameters` (both provided, derive LET, derive SET, blank rate, zero length, clamp-to-zero).

#### pdfPrefs persistence (SettingsStore TODO)

- Added `KEY_PDF_OAL_SPACING_FACTOR = floatPreferencesKey("pdf_oal_spacing_factor")` to `SettingsStore`.
- `pdfOalSpacingFactorFlow(ctx)` reads the stored value (defaults to `PdfPrefs().oalSpacingFactor = 2.5f`).
- `suspend fun setPdfOalSpacingFactor(ctx, factor)` writes the clamped value to DataStore.
- `ShaftViewModel.init` now collects `pdfOalSpacingFactorFlow` and keeps `SettingsStore._pdfPrefs` in sync, matching the existing pattern for `tieringMode` and `showComponentTitles`.
- `ShaftViewModel.setPdfOalSpacingFactor(factor, persist)` added for future UI callers.
- Removed the `TODO: persist _pdfPrefs via your existing persistence layer` comment from `SettingsStore.updatePdfPrefs`; all three `PdfPrefs` fields are now fully persisted.

#### VS Code test integration

- `.vscode/tasks.json` — "Test (JVM unit tests)" (default test task), "Compile (debug Kotlin)" (default build task with Kotlin error problem matcher), "Test (single file)" (prompts for filter pattern).
- `.vscode/settings.json` — configures `java.import.gradle.*`, `java.project.sourcePaths`, `java.project.referencedLibraries`, and `java.test.config` for the Extension Pack for Java test runner.

---

## 2026-05-28 (audit low items)

- Fixed `hasCenterBreak` footer note: replaced disconnected mm-space heuristic with the same `bodyLengthMm × ptPerMm ≥ COMPRESS_TRIGGER_PT` condition used by the actual rendering code.
- `VALIDATION_RULES.md`: marked all documented-but-unimplemented non-blocking warnings as `(planned — not yet implemented)` so the doc accurately reflects current state.
- `BRIEFING.md`: updated sprint status — tap-to-select is shipped (✅), resolved component pipeline is partial (not "not started").
- Added cross-reference comments to the duplicate `END_EPS_MM = 0.5` constants in `OalComputations.kt` and `ShaftPdfComposer.kt`.

## 2026-05-28 (audit items)

- Fixed PDF component label collision: labels now use greedy row assignment so overlapping labels (e.g. AFT Thread + AFT Taper at the same position) stack into separate rows instead of printing on top of each other.
- Deleted ~200 lines of dead code from `ShaftPdfComposer.kt` (`drawLinerDimensionsPdf`, `drawDimensionsLikePreview`, `drawDimWithExtensionsAvoidingOverlap`, `drawArrowInward`, `drawZigZagBreak`, `pickAftFwdTapers`, `fmtDia`, `fmtThread`, `fmtTaper` and associated constants). Removed blanket `@Suppress("unused")` annotation.
- Corrected `docs/PDF_EXPORT.md`: PDF does not use `ShaftRenderer`; `ShaftPdfComposer` has its own geometry drawing path. Dual-path divergence is now documented explicitly.
- Added `LinerDimAdapterTest` with 8 unit tests covering `mapToLinerDimsForPdf`: AUTO proximity anchoring, forced AFT/FWD modes, offset values, measurement-space rebasing with excluded threads.

## 2026-05-28

- Replaced PDF body center-break symbol with standard engineering S-curve edges. Each compressed body stub now ends with an S-shaped cut line instead of a straight cap; both edges curve in the same direction so the break reads as two matching cut faces across a narrow gap.

---

## 2026-05-27

- Fixed PDF OAL dimension lines landing at thread tip instead of taper SET when end threads are included in OAL. `computeSetPositionsInMeasureSpace` now derives SET positions from actual taper geometry instead of hardcoding 0/OAL.
- Updated `oalSpan` to take explicit SET endpoints `(x1Mm, x2Mm)` so the OAL label always matches the arrow positions.
- Added 4 unit tests covering SET position derivation (excluded, included, no-taper, overlapping cases).
- Added `AUDIT.md` — full codebase review covering architecture, dead code, test gaps, and documentation accuracy.
- Corrected `BRIEFING.md` field name errors: `startDiaMm`/`endDiaMm`, `odMm`, `excludeFromOAL`.

---

## [1.1.1] - 2026-01-08

### Added
- `.shaft` document filenames (content remains JSON), plus legacy `.json` compatibility and migration (`c98550f`).
- Connected-device instrumentation test guard (opt-in) to protect internal saves (multiple commits).
- Component snapping engine and helpers (multiple commits).
- Developer Options for debug tooling / gated verbose logging (multiple commits).
- Saved-shaft delete support plus tests (multiple commits).
- Thread “Include in OAL” toggle (exclude end threads from OAL window) (multiple commits).
- OAL window contract tests for determinism (multiple commits).
- Preview color presets + B/W mode (multiple commits).
- Shaft position selection persisted and printed in PDF footer (`a96a889`).
- Taper keyway (KW) width/depth fields + footer output (`15701e1`).
- Developer option to show OAL value in the preview box (`c0eb165`).

### Changed
- Save/open behavior and filename suggestions improved; overwrite confirmation added (`8743637`).
- PDF export UX improved (optional auto-open after export) (`56a293d`).
- PDF layout refined (shifted content for better spacing) (`c592a1c`).
- PDF footer and taper dimensioning refined (multiple commits).
- Editor toolbar/navigation redesigned (Home button, New/Open/Save, History dropdown, overflow menu) (multiple commits).
- Editor component carousel: tighter arrows/UX tweaks (multiple commits).
- Editor component titles made deterministic and more informative (`7a2e37e`):
    - Bodies: physical aft→fwd numbering.
    - Liners: positional AFT/MID/FWD naming; numbers only when needed; optional user override via inline title editing.
    - Tapers: AFT/FWD direction naming based on diameter trend; numbers only when needed.
- Preview overlay: removed OAL from the Free-to-End badge; Free-to-End only shows in Manual mode (`c0eb165`).
- App locked to portrait for more predictable editor layout (`700d8b2`).
- “Shaft Editor” header typography strengthened for clearer hierarchy (`7a2e37e`).
- Project/docs and dev tooling iterated (multiple commits).
- Android Gradle Plugin bumped (`070d916`).

### Fixed
- Gradle connected-test safety guard adjusted for Kotlin DSL compatibility (`c98550f`).
- Feedback email chooser behavior (`c80a7d5`).
- Stabilized component delete behavior (remove action timing, snackbar/undo flow) (multiple commits).
- Fixed PDF scaling/layout edge cases, taper dimension rendering, and unit-safe footer formatting (multiple commits).
- Settings and Developer Options screens are scrollable so all options are reachable (`27a8761`).

### Internal
- Version bump to `1.1.1` (`1027792`).
- Changelog refresh work (`4e502de`).

---

## [0.3.1] - 2025-09-16

### Added
- Full-rectangle preview rendering for components (multiple commits).
- Editor UI structure improvements (FAB + bottom sheet; more usable scaffolding) (`2f99695`).

### Changed
- Editor unit handling and dropdown behavior improved (`663157a`).
- Taper handling + input UX improvements (`48aaad6`).
- PDF title block / layout helpers refactor (`2d2f61c`).

### Fixed
- Updated `ShaftDrawingView` layout call to match `ShaftLayout` API (`2f424bd`).

## [0.2.0] - 2025-09-14

### Added
- Coverage chip hint (`coverageChipHint()`) in `ShaftViewModel` for concise, unit-aware messages.
- Settings menu in `TopAppBar` with toggle to choose between **chip-style** or **text-style** coverage hints.
- `SettingsDialog` component with temporary state management (persistence TODO).
- Export PDF action added to `TopAppBar` (optional), keeping Floating Action Button export as well.

### Internal
- Initial changelog created (`d1a4da5`).

### Changed
- Updated `ShaftScreen` scaffold to include Settings and Export actions in the `TopAppBar`.
- Improved overall UI structure and consistency.

---

## [0.1.0] - Initial Commit

### Added
- Project setup with package name `com.android.shaftschematic`.
- Core data models: `ShaftSpecMm`, `BodySegmentSpec`, `KeywaySpec`, `LinerSpec`, `TaperSpec`, `ThreadSpec`.
- `UnitSystem` enum for inches/mm conversion.
- `ShaftViewModel` with state flows for spec + unit handling.
- `ShaftScreen` UI with Compose, including input fields for:
    - Basics (length, diameter, chamfer, shoulder length).
    - Body segments (dynamic add/remove).
    - Keyways (dynamic add/remove).
    - Tapers with ratio handling.
    - Threads (forward + aft).
    - Liners (dynamic add/remove).
- Export-to-PDF feature using `ShaftPdfComposer` with:
    - Span/segment drawing.
    - Tapers, threads, keyways, liners.
    - Dimension arrows and overall length.
    - Simple title block with project info.
- Git integration with `.gitignore`, initial README, and project structure.

### Internal
- Initial project import (multiple commits).
