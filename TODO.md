# ShaftSchematic TODO

**Version: v0.5.x Development Queue**  
**Last updated: 2026-08-17**

Tasks are ordered by priority. Completed series are collapsed to a single summary line to
keep this readable — full detail lives in `CHANGELOG.md` and git history.

---

## 0. Current System State (updated 2026-08-05)

| Area | Status |
|---|---|
| Core model (Body, Taper, Threads, Liner) | ✅ Stable |
| ShaftLayout & ShaftRenderer | ✅ Contract-locked |
| PDF export — one-page, landscape | ✅ Stable |
| Validation — blocking errors | ✅ Wired in UI (Add dialogs, carousel badges, export gate) |
| Validation — non-blocking warnings | ✅ Yellow badges in carousel; FreeToEndBadge 3-state |
| Snapping engine | ❌ Removed — whole pipeline (`SnapUtils.kt`) deleted with tap-to-add; nothing snaps a position any more (golden rule) |
| Tap-to-add pipeline | ❌ Removed — canvas tap is selection-only; components are added via the FAB chooser only |
| OAL window / excluded thread logic | ✅ Implemented & unit-tested |
| Taper rate input + derivation | ✅ Implemented (taperRateText, parseRateText, deriveTaperDiameters) |
| Taper rate colon entry (`1:12`) | ✅ Keyboard-compatible on Android (ASCII rate input + colon filter path) |
| Taper rate auto-calc (Length + SET + LET) | ✅ Auto-by-default with manual override; 3% common-rate snap + exact `1:N.NNN` fallback; bare `1` blocked, mismatch warning shown |
| Keyway on Taper | ✅ Open + floating, plan-view rectangle, mill-cutter arc, white fill |
| Carousel selection fix | ✅ Fixed (seeded on load, swipe works before first tap) |
| Shared signing config | ✅ debug.keystore committed; all machines update-install |
| Internal save/open | ✅ Working |
| Backup & restore | ✅ Zip backup/restore via file picker, per-shaft import/export, pre-update snapshots (keep 3), Auto Backup rules; sample pruning made non-destructive (seed-hash ledger) |
| Autosave / draft restore | ✅ Reworked 2026-07-25 — dirty-gated 3-entry draft ring (per-document identity) replaces the single always-overwriting slot that caused a data-loss incident; StartScreen shows an "Unsaved drafts" list. See `docs/archive/Autosave_Incident_2026-07-25.md` |
| ShaftScreen.kt | ✅ Carousel, preview panel, and event wiring extracted (2322 → 1235 lines at the 2026-07 extraction; grown since from later feature work) |
| Sidebar nav (5 tabs) | ✅ Schematic / Runout Sheet / Wear Document / Undercut Drawing / Consolidated Output (`EditorSidebar` + `EditorTab` + `ShaftEditorRoute`) |
| Runout drawing | ✅ RunoutPdfComposer, inline shaft preview, scrollable layout, collision-free alternating bubble layout via shared `geom/RunoutBubbleLayout.kt`; resolved-component geometry (2026-07-18) |
| Wear document | ✅ WearPdfComposer, dye-pen PASS/FAIL checkboxes, field notes; resolved-component geometry (2026-07-18). Reworked 2026-07-28: every liner gets a detail strip (with or without wear), blank write-in template (circle-one AFT/FWD anchors, edge-bar rails), profile-band space reclaim, uniform strip heights, shared positional liner titles. On-device verified through the layout round |
| Liner wear areas | ✅ Built 2026-07-18 (all 4 phases + input spec: SET/liner-edge references, blocking span validation, PDF detail strips with dimension rails) — awaiting on-device verification. Build record in git history (`LinerWearAreas_BuildLog_2026-07-18.md`, pruned in `35ca87f`); design record in `docs/archive/LinerWearAreas_Proposal.md` |
| Wear pits (X markers) | ✅ Built 2026-07-21 — small/large pit "X"s on bodies, tapers & liners (tap to open a segment; explicit Add X / Remove X / Clear all tools); drawn on the wear PDF profile + strips. Wear PDF now keeps the shaft profile always on top with a 2-column detail-strip grid. See CHANGELOG + "Wear Pits" in `docs/contracts/RunoutSheet.md`. Awaiting on-device verification |
| Body keyways | ✅ Built 2026-07-20 — taper-style keyway on bodies (open + floating), 180°-apart hidden-line toggle, auto-body promotion via the "Explicit body" checkbox (checkbox-only, reworked 2026-07-25); split/merge carry keeps keyway at absolute position |
| Runout bubble editor | ✅ Built 2026-07-21 — tap a bubble to record TIR value + high-spot clock marker; open-topped keyway cutout in the bubble; drawn identically on canvas + PDF |
| Spooned keyways | ✅ Built 2026-07-22 — draw-only enlarged bowl at the closed (LET) end of an open keyway; footer note "KW length to base of spoon" added 2026-07-24 |
| Diameter callouts (schematic PDF) | ✅ Built 2026-07-22 — on-shaft Ø callouts below the shaft, 3-decimal, two-tier stacking, liners included as a separate OD group |
| Dimension values in a break | ✅ Built 2026-07-22 — PDF dimension lines seat the value in a gap in the line with inward arrows; short/colliding spans fall back to label-above |
| Line thickness control | ✅ Slider 50%–200% in Settings, DataStore-persisted, affects preview + PDF |
| OAL include-thread toggle | ✅ PDF OAL span now extends to shaft ends when thread marked included |
| Resolved component pipeline | ✅ Wired into schematic screen/PDF + runout & wear documents (2026-07-18) |
| Undo/redo | ✅ Built 2026-07-26 — session-scoped `SessionHistory` over `EditState` (spec + wear + runout + order), 600 ms coalescing, 50-step cap, editor history menu |
| Undercut drawing (tab + PDF) | ✅ Built 2026-07-30→08-03 — `UndercutRecord` in its own envelope field, shaft-space cuts (no orphans), settled open-notch convention (silhouette step + full-height section faces, mouth never lidded), liner-anchored detail strips, cut-depth exaggeration slider, user shading / line-art styles (`UndercutPdfComposer`) |
| Consolidated Output tab | ✅ Built 2026-08-04→08-05 — `EditorTab.OUTPUT` / `OutputRoute.kt`: `ConsolidatedVariant` election (All three / Schematic + Runout / Schematic + Wear), worn-section editor (values inside the profile over knockout halos), "Shaft height" + liner-compression controls, **Export all** batch-write to a picked folder. The classic standalone runout sheet stays on the Runout tab |
| Profile sizing (PDF) | ✅ Built 2026-08-04→08-06 — per-job value-based "Shaft height" slider (paper inches, hard cap 1.5"), proportional default sizing curve 4" → 0.5" / 8" → 1" with user-adjustable anchors (Settings → Drawing → "Default drawing size"), liner compression with height precedence, S-break pair minimum gap plus a user-set compression threshold (Settings → Drawing → "Body S-break", default 50%), even-spread runout bubbles |
| Appearance settings | ✅ Built 2026-08-04 — System/Light/Dark + high contrast for Compose chrome; paper sheets pinned to fixed ink (`SheetInk.kt`), PDF untouched. See `docs/contracts/Appearance.md` |
| Help + Achievements screens | ✅ Built 2026-08-04 (`HelpRoute.kt`, `AchievementsRoute.kt`) |
| Export hardening | ✅ Built 2026-08-05 — every SAF write goes through `util/PdfSafExport` (composer throw → valid error page, never a truncated file); the collision gate guards every export surface |
| Insert-Between workflow | 🔲 Not implemented |
| Liner shoulders | 🔲 Not implemented |
| Fiberglass body support | 🔲 Not implemented |

---

## 1. Active Sprint — Refactor Complete (Carousel Phase)

- [x] Extractions done 2026-07 — carousel (`ComponentCarousel.kt`), preview panel
  (`ShaftPreviewPanel.kt`), event wiring (`ShaftScreenController.kt`); details in CHANGELOG
- [ ] Controller owns all VM-side intents (composables stateless) — design work, not a pure move

---

## 2. Validation Enhancements

### 2.1 Remaining Validation Items

- [x] 2026-07 items done — taper on-blur field validation; slope-guard pinning tests;
  `freeToEndMm` safeSpec fallback (`freeToEndSignedMm`). Details in CHANGELOG
- [x] No numeric upper bound on inputs — DONE 2026-08-24 as a non-blocking per-component
  sanity warning (length > 15 m, any Ø > 1 m → yellow carousel badge, "check for a typo");
  thresholds provisional pending shop input, nothing blocks or rewrites the typed value.
  See `VALIDATION_RULES.md` §2.3
- [x] Export gate checked only thread/liner positions — taper overlaps now block export too
  (2026-08-24; `blockingExportError()` runs a taper pass through `collidingIds()`, the same
  predicate as the carousel badge). The remaining Thread↔Liner sub-gap closed 2026-08-25:
  the gate now runs the whole `collidingIds()` set once, so every §5.2 sacred pair blocks the
  schematic export — see `VALIDATION_RULES.md` §6 (no known gaps left)

### 2.2 Warning Rules (VALIDATION_RULES.md §3–4)

- [x] All five §3–4 warning rules computed + shown on carousel cards, 2026-07-24
  (`ui/util/ComponentWarnings.kt`, `specWarningMessages`). §3.3 taper-vs-body mismatch
  later removed by product decision — see §2.3
- [x] Spec-level warnings (`specWarningMessages`) now show as a dismissable banner above the
  carousel on the Schematic tab, 2026-08-25 (`ui/screen/SpecWarningBanner.kt`,
  `ui/util/SpecWarningVisibility.kt`). Dismissal keys off the message set and is Compose view
  state only (not persisted, not in `EditState`/undo).
- [ ] Review the warning thresholds picked during the 2026-07-24 loop (1.5× adjacent-body
  step, 0.5 mm adjacency eps in `ui/util/ComponentWarnings.kt`) — chosen without shop input;
  sanity-check against real drawings. (Taper-mismatch 10% threshold is moot — that rule was
  removed 2026-07-26.)

### 2.3 Bug Fixes (open)

- [x] §3.3 taper-vs-body Ø-mismatch advisory — fixed 2026-07-25, still misfired on-device,
  **removed entirely 2026-07-26 by product decision** (the difference is visible in the
  drawing itself; regression test pins the no-warning behavior). Do not reintroduce.
- [x] **Taper orientation discrepancy — fixed 2026-08-06** (forward fix per the analysis's
  recommendation): the Add dialog's SET/LET swap is re-keyed on the **physical half**
  (judged against the post-add OAL — `oalAfterTaperAddMm`), the measure-from chip persists
  as `Taper.authoredReference`, and `addTaperAt`/`updateTaper` derivation lost the stale-OAL
  face bug. Data-repair normalization deliberately **declined** — reversed pairs stored by
  earlier builds decode exactly as saved (golden rule; pinned by test). Full detail:
  CHANGELOG 2026-08-06 + `docs/archive/TaperOrientation_Analysis_2026-07-26.md` (marked RESOLVED).
- [x] **OAL reload edge — fixed 2026-08-24:** a file saved with manual OAL exactly equal to the
  content end reloaded as "auto", and auto mode derives no leading/trailing auto span — silently
  dropping a *leading* span when components don't start at 0. Fixed by the chosen candidate:
  the shared pure `ShaftSpec.oalIsManualOnLoad()` also treats OAL as manual when the aft-most
  component starts > 0. All three load paths (`importJson`, `applyTemplate`, template-card
  preview) share it. The doc-envelope schema change was declined. Pinned by `ShaftSpecTest`.

---

## 3. Rendering / Component Backlog

- [x] **Liner shoulders with a radius selector** — DONE 2026-08-25 (questions answered
  same day: radius per-end from a standard list — sharp, 1/16"–1/2", provisional; prints as a
  FOOTER NOTE only). Per-end length + shoulder Ø + edge radius on `Liner` (verbatim storage);
  ONE shared silhouette (`geom/LinerShoulderMath.kt`) decomposed by the preview canvas and the
  schematic PDF; **capability-gated** behind Settings → "Liner shoulders" (default OFF,
  on-device request — authored shoulders keep their controls and always draw). SVG review
  artifact in `LinerShoulderSvgPreviewTest`. **Follow-ups deliberately open:** the
  runout/consolidated sheet's liner pass still draws square ends (same rollout order as
  blends), and `SurfaceSegs` treats a shouldered liner as full OD (wear/undercut readings
  near a shoulder read the un-stepped surface).
- [x] **Seal-area radius grooves** — DONE 2026-08-22 as part of the body-blend work, once it was
  clear the cuts sit ON the blended section running up to the liner rather than on a plain
  cylindrical run. A blended face carries a `Seal area (3 cuts)` flag; each cut draws as a V notch
  in both silhouette edges with a dashed line across the notch floors. Fixed count and no printed
  value — a schematic cue, not something to machine from. See `docs/COMPONENT_CONTRACT.md` and
  `docs/PDF_EXPORT.md` §5.2b.
- [x] **Blends and seal cuts on the consolidated / runout sheet** — DONE 2026-08-24:
  `drawBodiesForRunout` (`RunoutPdfComposer`) now decomposes the same `bodyDrawEdges` as the
  schematic composer — curves + seal cuts ride the compressed `xAt`, the S-break is cut into
  the FLAT span, body shade fill follows the curves, end caps stand at the neighbour's radius.
  The **wear document deliberately keeps square faces** (it omits machining detail by product
  decision, same posture as its keyway omission). Compressed-map SVG review artifact +
  face-position assertions in `BlendSvgPreviewTest`. See `docs/contracts/RunoutSheet.md`
  (Blended faces) and `docs/COMPONENT_CONTRACT.md`.
- [ ] **Fiberglass body segments** — model flag, dark fill / hatch pattern, label.
  Reference: `assets/20251022_172641.jpg`. Two halves, and the second is the open one
  (2026-08-14): (a) *selection* — which body sections are fiberglassed, presumably a
  per-`Body` flag with the usual add-dialog/carousel-card parity; (b) **styling — undecided**.
  Get a sketch or a photographed sheet before choosing, the same way the "indicated wear"
  squiggle convention is blocked on one. Note the existing interaction: a fiberglassed run is
  exactly the case that motivated per-body `showDiaOnDrawing`, since a Ø cannot be measured
  through the wrap.
- [ ] **Additional output fonts** (requested 2026-08-14) — a font choice for the printed
  sheets, so a shop can pick a look rather than take the platform default. Constraints worth
  writing down now: the PDF composers draw with `android.graphics.Paint`, so a face has to be
  a real `Typeface` (a bundled `.ttf` asset or a system family), and **every text metric in
  the layout is measured live from the paint** — dimension-rail label widths, the ellipsize
  helper, the fraction renderer's cap-height and advance math — so a face swap is safe by
  construction *provided* nothing hard-codes a width. Check the fraction stack against a
  condensed or slab face before shipping one: `FractionTextRendererTest` pins that the stack
  stays inside the font's own ascent/descent, and a face with unusual metrics is exactly what
  that test exists to catch. Same pref posture as `PdfPrefs.fractionStyle`.
- [x] **Runout bubble leader clarity** — DONE 2026-08-25. Diagnosis: leaders were never
  missing or proximity-suppressed; the *dogleg diagonal* was confined to a fixed ~14 pt lane
  above row 0 against an unbounded horizontal run, so it went near-horizontal (15.6°–17.7°
  measured) exactly where doglegs occur and pointed at nothing. Fixed in the pure engine by
  letting the elbow dip for slope (`LEADER_DOGLEG_MIN_SLOPE` ≈26.6°), bounded by the bubble's
  own top and a per-leader clearance search; no page-height cost, no renderer changes, all
  collision guarantees preserved. The interim options listed here were not needed: a witness
  tick would have eaten the very lane depth that was the problem, and the alternation was
  already correct.

---

## 4. Tech Debt

### 4.1 Dialog Cleanup (`§5.2`)

- [x] Done — confirm/cancel patterns and commit-on-blur standardized; legacy length-editing
  utilities removed in the 2026-07-24 dead-code sweep. The `parseFractionOrDecimal` /
  `toMmOrNull` duplication remains — tracked in `NumberField.md`

### 4.1b Deferred Refactor Waves (from the 2026-07-11 cleanup sweep, report in git history)

Waves 1–2 shipped (Wave 1 fixes 2026-07-11; Wave 2 deletion pass 2026-07-26, `ad5b198`). Remaining:

- [x] **Theme decision:** resolved 2026-08-04 — `ShaftSchematicTheme` wired into
  `MainActivity`, driven by the new Appearance setting (System/Light/Dark + high contrast,
  default Light = historical look). Sheet canvases pinned to fixed ink (`SheetInk.kt`) so
  dark mode can't blank the drawings; PDF untouched (theme-independent per §8). Remaining:
  on-device visual pass of dark/high-contrast chrome — see
  `docs/contracts/Appearance.md`.
- [x] **Wave 3 as written — closed 2026-08-25, re-scoped below.** A full duplication audit
  found the pure-math layer already unified (scale solve `geom/ProfileCompression.kt`,
  S-break `pdf/BreakSymbol.kt`, rails `pdf/render/PdfDimensionRenderer.kt`, blends
  `ui/resolved/BodyBlends.kt`, keyway/coupler-slot/footer draws shared out of
  `ShaftPdfComposer`), so "one shared profile-drawing helper" is overtaken by events —
  what survives is ORCHESTRATION duplication, itemized below. The **ViewModel
  update-method generics** sub-item is closed as largely N/A: per-kind update logic is
  load-bearing (OAL-provisional small-end detection in `updateTaper`, excluded-thread
  start re-derivation, split/merge asymmetry, `carryBodyKeyway`), and a forced generic
  would wrap it in a leakier read. Optional narrow polish only: collapse the 4 label +
  2 showDia trivial setters (~70 lines) behind a lens-shaped private helper.
- [x] **Wave 3 re-scoped — ALL FIVE EXECUTED 2026-08-25 (evening run)**, dispositions:
  1. DONE — `geom/ProfileFeatureSpans.kt`: `profileFeatureSpans` builds the one span
     structure for the schematic (lean floors), the runout/consolidated sheet (writable
     floors), and the liner-compression estimator; the keyway-window pin helpers moved
     there too (their proper pure home).
  2. DONE — `COMPRESS_TRIGGER_PT`/`ZIGZAG_GAP_MAX_PT` live once in `pdf/BreakSymbol.kt`
     (the UC_ aliases folded in).
  3. DONE — `pdf/BodyRunDraw.kt`: `drawBodyRunsWithBreaks` is the ONE body-run pass both
     blend-aware composers call. The mechanical diff found exactly one behavioral
     divergence — the runout copy painted the right stub's shade fill AFTER the break
     edge, letting a shaded body's fill cover part of the S-curve; unified on the
     schematic's correct order.
  4. DONE — `pdf/SimpleShaftProfile.kt`: `drawSimpleShaftProfile` is the shared
     square-face wear/undercut whole-shaft profile (ONE thread-hatch impl folded in).
     Posture RULED 2026-08-26: the wear document's no-machining-detail posture stays the
     standing decision; the undercut document merely draws the same simple profile TODAY
     and remains free to grow blends later ("allow blends in case we ever need them") —
     the KDoc says so. The page-geometry preambles
     were deliberately NOT extracted (no net saving; would couple two sheets' private
     tuning knobs — reasons in the file KDoc). Line-thickness scaling of secondary
     strokes now applies to the undercut sheet too (it had never received the wear
     sheet's fix).
  5. DONE — `pdf/SheetHeader.kt`: one blank-draft header for the wear/undercut pair;
     the runout header deliberately left (no title, left-aligned, different baseline —
     folding it in would be mostly flags; reasons in the file header). The schematic's
     thread-hatch divergence from the other three sheets is NOT changed — it is a
     visible difference and stays an open product question (below).
- [x] **Thread-hatch convention — unified 2026-08-26** (on-device ruling: "no sense in
  having different forms with different outputs"): every sheet now hatches through the ONE
  `drawThreadHatch` + shared pitch/paint recipe (thread's own pitch capped 4–18 pt,
  60%-dim-weight alpha-160), and the preview canvas mirrors the same geometry
  (`ShaftRenderer.drawThreadHatch`, its user-set hatch color kept). The schematic's old
  short-tick convention is gone. Pinned by `ThreadHatchParityTest` — pixel equality of the
  same thread across the schematic, wear/undercut, and runout profile passes.
- [ ] ~~Wave 3 original list~~ (retained for history):
  1. Shared `ProfileFeatureSpan` builder in `geom/` — the span list is built 3× by hand
     (`ShaftPdfComposer`, `RunoutPdfComposer`, `ShaftHeightSlider.estimatedLinerKeptFracOfTrue`)
     and the UI estimator mirrors the composers by convention only (`REFACTOR_CANDIDATES.md` #2).
  2. Hoist `COMPRESS_TRIGGER_PT` (220f) + `ZIGZAG_GAP_MAX_PT` (20f) into `pdf/BreakSymbol.kt` —
     four private copies (`UC_`-prefixed in the undercut composer) of the constants gating the
     already-shared break functions; tuning one and missing three is the invited failure.
  3. `drawBodyRunWithBreak` for the two blend-aware composers (`drawBodiesCompressedCenterBreak`
     / `drawBodiesForRunout`, ~200 near-identical LOC over the same `bodyDrawEdges`) — needs an
     SVG-artifact review pass; the sites differ in real ways (`REFACTOR_CANDIDATES.md` #3).
  4. Wear/Undercut twin shaft-profile draw + page-geometry preamble
     (`drawWearShaftProfile`/`drawUndercutShaftProfile` + setup, ~245 near-identical LOC) — the
     strongest undocumented drift risk: wear's square-face/no-keyway posture is a written product
     decision, undercut's identical posture is written nowhere. Write the contract sentence
     (deliberate twin, or divergence allowed?) before or with the extraction.
  5. Lower priority: unify the 3-way blank-draft header layout (Runout/Wear/Undercut,
     ~150 LOC of re-typed layout decisions over shared primitives); the schematic's thread
     hatch has silently diverged from the Runout/Wear/Undercut convention (different step
     math, paint, clip) — reconcile or document as deliberate (product eyes needed: it is a
     visible difference between sheets).
- [x] **Wave 4: structural splits — audited AND EXECUTED 2026-08-25; five files split, LEAVE
  for one.** All splits were pure moves of self-contained functions (explicit params, no
  shared mutable state), not restructurings; full suite 1795 green after every step. In order:
  1. ~~`RunoutRoute.kt`~~ — DONE 2026-08-25: `PdfPreviewOverlay` + `RunoutWearOptionsSheet` +
     `openRunoutPdf` moved verbatim to `ui/screen/PdfPreviewOverlay.kt` (724 lines;
     RunoutRoute.kt 1908 → 1251). They are called from all four PDF-bearing routes and only
     lived in RunoutRoute by history. Zero call-site changes; doc pointers updated
     (`RunoutSheet.md`, `PDF_EXPORT.md`); suite 1795 green.
  2. ~~`ShaftViewModel.kt`~~ — DONE 2026-08-25: 3128 → 1377 via the proven
     `ShaftViewModelSettings.kt` extension pattern, three verbatim-move stages, suite
     green after each: `ShaftViewModelWear.kt` (269) + `ShaftViewModelRunout.kt` (222) +
     `ShaftViewModelUndercut.kt` (111), then `ShaftViewModelComponents.kt` (944, the
     component CRUD with its load-bearing per-kind logic untouched), then
     `ShaftViewModelPersistence.kt` (340). Backing flows/helpers the extensions need were
     promoted `private` → `internal`; call sites changed by imports only. 1377 is the
     expected floor — `StateFlow` declarations (with their contract KDoc), the `init`
     wiring, undo/EditState, and the draft ring stay in the class by design.
  3. ~~`WearPdfComposer.kt`~~ — DONE 2026-08-25: `drawWearStripWindow` + its strip-exclusive
     helpers/constants moved verbatim to `pdf/WearPdfComposerStrip.kt` (777 lines; composer
     1915 → 1173), pairing the draw half with the math half in `WearStripLayout.kt`.
     `drawVerticalBand` deliberately stayed (main-profile + `RunoutPdfComposer` consumer —
     it was never strip-exclusive). Doc pointers updated (`RunoutSheet.md`,
     `FractionTypography.md`).
  4. ~~`UndercutDetail.kt`~~ — DONE 2026-08-25: the cross-file shared tail split by
     dependency — pure reference/notch/SET math to `geom/UndercutOverlayMath.kt` (160
     lines, model-only imports), the `DrawScope` notch pass + resolved→liner-span mapping
     to `ui/screen/UndercutSharedDraw.kt` (124 lines). UndercutDetail.kt 1926 → 1683. The
     window-geometry and canvas-helper sections turned out overlay-private ("shared by the
     Canvas renderer and the tap handler" meant the overlay's own) and stayed. CLAUDE.md
     undercut invariant + `UndercutDrawing.md` updated.
  5. ~~`ComponentCarousel.kt`~~ — DONE 2026-08-25: the five `ComponentPagerCard` `when`
     branches extracted verbatim to `BodyPagerCard.kt` (478) / `TaperPagerCard.kt` (336) /
     `ThreadPagerCard.kt` (206) / `LinerPagerCard.kt` (265) /
     `CouplerBoltSlotPagerCard.kt` (146); the dispatcher stays as a thin `when` passing
     every argument by name (ComponentCarousel.kt 1876 → 925). Each card takes only the
     ~11–28 parameters its kind uses instead of sharing the ~35-callback surface. All
     `testTag`s unchanged; add-dialog-parity location pointers updated in CLAUDE.md +
     `AddComponentDialogs.md`/`COMPONENT_CONTRACT.md`/`UI_CONTRACT.md`/`ARCHITECTURE.md`/
     `VALIDATION_RULES.md`.
  - **LEAVE `ShaftPdfComposer.kt` (2082)**: cohesive single pipeline with real region
    banners; its complexity lives in the 484-line `composeShaftPdf` entry function (inline
    scale/placement math), which a file split does not shrink. Revisit only after Wave-3
    items 3–4 reshape what is composer-local vs shared.

### 4.2 Build Tooling (`§5.3`)

- [ ] Keep Gradle wrapper, AGP, and `libs.versions.toml` in sync
- [ ] Isolate tooling updates into `chore(build)` commits
- [x] Currency pass 2026-08-25 (suite 1795 green after): Gradle wrapper 9.6.1 → 9.7.1
  (AGP 9.3.1 + JUnit 4.13.2 already current); core-ktx 1.17.0, lifecycle 2.10.0,
  activity 1.13.0, navigation 2.9.8, datastore 1.2.1, robolectric 4.16.1,
  androidx.test junit 1.3.0, espresso 3.7.0, coroutines 1.11.0. Catalog hygiene:
  appcompat/coroutines/material moved into the catalog, duplicate literal deps removed
  from `app/build.gradle.kts` (including a stale `navigation-compose:2.8.2` losing to the
  catalog's version at resolution), dead `serialization = "1.6.3"` key and the entirely
  unused Room entries deleted (no Room usage anywhere in `app/src`).
  **Deferred, with reasons — do NOT bump blind:**
  - **compileSdk-37 chain**: core/core-ktx 1.19.0, lifecycle 2.11.0, and Compose BOM
    2026.08.00 all require compileSdk 37, but stable Robolectric (4.16.x) certifies only
    through API 36 — the whole Compose test suite runs on Robolectric, so compileSdk 37
    waits for Robolectric 4.17 stable, then moves as ONE coordinated bump.
  - **Compose BOM 2024.09.00 → 2026.04.01** (last compileSdk-36-safe BOM): real Compose
    API surface over ~19 months — its own branch with a compile + visual pass, not a chore.
  - ~~Kotlin 2.2.20 → 2.3.20 (+ kotlinx-serialization 1.11.0)~~ — **DONE 2026-08-25
    (evening run)**: the feared Kotlin-2.3/AGP-9 `kotlin-android` conflict did not
    materialize — full suite AND `assembleDebug` clean on 2.3.20/AGP 9.3.1. Only fallout:
    `createTempDir` promoted deprecation → error in 6 test files (19 sites), migrated to
    `kotlin.io.path.createTempDirectory`. Kotlin 2.4.0 (K1 drop, annotation-target and
    warning-promotion changes) remains its own future branch.
- [x] Bump `actions/checkout` and `actions/setup-java` to v5 in the Firebase workflow —
  done 2026-07-28, deprecation warning cleared

### 4.3 Post-Tiering Cleanup (LOW, deferred to v0.5.x)

- [x] Tiering helpers audited 2026-07-24 — one dead default parameter removed; unreachable
  `SpanKind.OAL` path kept as contract documentation
- [ ] Add optional debug overlay showing tier origin and measurement reference (preview only)

---

## 5. Testing Burndown

### 5.1 Unit (Complete)

- [x] Complete — freeToEndMm, taper rates, thread pitch ↔ TPI, OAL exclusion,
  PDF footer, LinerDimAdapter, TaperDimSpan, BlockingExportError, StartOverlapValidation,
  TaperKeyway. Details in git history

### 5.2 Instrumentation (Done 2026-07-26)

- [x] Done — Robolectric JVM Compose harness added (`testDebugUnitTest`, no device; assert
  against `testTag`s, never composable parameter lists); commit-on-blur, blocking-dialog
  gates, and carousel selection sync all extracted pure + covered. Found
  and fixed the `NumericInputField` composition-commit bug along the way. Suite 719 → 796.
  Full detail: CHANGELOG "2026-07-26 (night)".
  **Deliberately not covered** (decision, not a gap): no Compose test for
  `ComponentCarouselPager` (~35 params, all callbacks — that coupling is what rotted the
  deleted androidTest) or the Add dialogs; their logic is pure and covered.
- [x] **CI gates on the test suite — decided + done 2026-08-06** ("yes, set it up so only
  green builds, red blocks"): `distribute.yml` runs `testDebugUnitTest` before
  `assembleDebug`, so a red suite stops the build and nothing distributes. Triggers
  extended to `chore/**` and `fix/**` so review branches get builds too.

**Incidental finding (product question, not a defect):** the preview hit-test
(`ShaftDrawing.kt:229-239`) covers Body/Taper/Thread/Liner but **not** `ResolvedCouplerBoltSlot`,
so tapping a slot selects the body underneath it and a slot's carousel card is unreachable by
tap. Plausibly deliberate — a slot always overlies something, and letting it win the hit-test
would make that body untappable at the slot. Decide before changing.

---

## 6. Backlog (v0.5.x+)

- [x] **Bore keyway rough-cutter depth calculator** — DONE 2026-08-24 (requested same day):
  standalone shop-floor tool computing the edge depth for a narrower rough cutter so its flat
  bottom lands on the finished keyway's plane in a bore. Sidebar bottom group ("Keyway
  calculator", every tab, never gated on a built shaft); up to 2 cutters; in|mm chip
  defaulting to the document unit; decimal authoritative + nearest-64th scale check; blank
  every open, nothing persisted. Pure math `geom/BoreKeywayMath.kt` (spec vectors +
  invariants pinned). Plan + settled decisions: `docs/BoreKeywayCalculator_Plan_2026-08-24.md`.
  Awaiting on-device verification.

- [ ] **Multi-shaft per job number** (requested 2026-07-26): sometimes two shafts share one
  job number; want to select between them. Feasibility + phased architecture plan in
  `docs/MultiShaftJob_Plan_2026-07-26.md` (recommends derived job grouping over
  single-shaft files — no file-format change; Phase 0 fixes the existing runout/wear
  export-filename collision for same-job shafts). Awaiting answers to the plan's
  6 product questions before building.
- [x] **Carousel ordering — decided + done 2026-08-06**: physical order accepted (the
  resolved-component display is correct); the dangling newest-first `componentOrder`
  plumbing removed from the ViewModel, `EditState`, and the pager parameter chain.
  Nothing persisted changes. `ComponentsOrdering.md` v1.3 records the decision.
- [ ] Title-strip follow-ups (from the 2026-07-25 night run; liked, not yet requested):
  tappable title → Save As / rename; smarter untitled-draft row names on StartScreen (derive
  from job/customer/vessel via `DocumentNaming.suggestedBaseName`); title strip on the
  Runout/Wear tabs too.
- [ ] Selection → contextual "Add near selected" defaults
- [ ] Inline "Add here" buttons between components in list
- [x] Undo/redo architecture — session-scoped `SessionHistory`, done 2026-07-26; covers every
  drawing edit, 600 ms coalescing, 50-step cap. See
  `docs/contracts/ShaftViewModel.md`
- [ ] Undo/redo follow-ups (future scope, not blocking v1.0): cross-session/persisted
  undo history (currently in-memory, cleared on process death and at every
  new/open/import boundary), and metadata (customer/vessel/job number/notes/shaft
  position/unit) is deliberately excluded from the undoable state — revisit if that
  becomes a complaint.
- [ ] Preset library (common tapers, common shoulder patterns)
- [x] Dual-unit display (shipped 2026-08-18): inline `1 1/2" [38.1 mm]`, single-line by
  design (width-only, no tier/height budget change — the earlier stacked attempt was what
  collided). Sheet-wide toggle, off by default. See CHANGELOG 2026-08-18.
- [ ] Quick inline mm ↔ in calculator in dialogs
- [x] **Mixed units within one drawing** (shipped 2026-08-18, requested 2026-08-17): per-component
  in/mm chip (capability-gated) + metric thread designation entry, honored on every sheet. Display
  axis only (`util/DisplayUnits.kt`, envelope `unit_overrides`), off by default. **Follow-ups still
  open:** carousel numeric *entry* fields still take the document unit (the chip governs how a
  component PRINTS, not how its fields are typed) — a metric keyway is entered in inches and stored
  mm; and standard metric key-stock presets for keyways are not built yet. Original design notes
  retained below.
  A real shaft drawing turned
  up with some dimensions in mm and others in inches. The app previously allowed exactly one
  unit per document (`preferredUnit`, `ShaftDocCodec.kt:65`; `docs/DATA_MODEL.md` L315).
  **Feasibility is better than it looks.** The model is already millimeter-canonical —
  `UnitSystem` converts only at the UI/render edge, and `RunoutReading.kt:46` states values
  are "never converted in the model" — so this is a display-and-entry change, not a data
  migration. Seams: `pdf/UnitFormat.kt` (`formatLenDim` / `formatLenWithUnit` /
  `formatDiaWithUnit`), `ShaftScreen.formatDisplay` (L1232), `util/RunoutValueFormat.kt`,
  plus wherever a per-value override gets stored. ~150 `UnitSystem` call sites, so scope the
  override narrowly rather than threading a unit parameter through all of them.
  **Safety requirement, not a nicety:** the moment a drawing mixes units, every dimension on
  it must carry its own unit suffix. A single-unit drawing can declare the unit once in the
  title block; a mixed one where a bare `2.5` could mean either is exactly how a shaft gets
  machined wrong. An implementation that does not label per dimension should not ship.
  **Observed pattern (provisional — example page being sourced 2026-08-17):** on the drawing
  that prompted this, the *threads* and the *keyway* were in mm and everything else had been
  converted to inches by the leads. If that holds up, the mixing is not arbitrary: it is
  "features defined by a metric standard keep their native units; measured geometry follows
  the shop's working unit." A metric thread has a designation (`M20×2.5`) that stops meaning
  anything once converted to decimal inches, and metric keyways come from standard metric key
  stock — so those two resist conversion for a real reason, while a body length is just a
  measurement and converts cleanly. Confirm against the example page before designing to it.
  Note the asymmetry for labelling: `M20×2.5` is self-declaring, a keyway written `6 × 6` is
  not, and that is the dangerous one.
  **Answers as built:**
  1. Granularity — **per component**, keyed by resolved id in `unit_overrides`; a metric thread
     carries an implicit mm override.
  2. Entry — a `Prints in: in | mm` chip on each explicit Body/Taper/Thread/Liner **card**
     (card-only by decision: a post-hoc display toggle, and there is no resolved id to key an
     override to at add time — the third documented carve-out from add-dialog parity); the Add
     Thread dialog adds an Imperial/Metric M-designation mode, which IS under the parity rule
     because it is value entry.
  3. `preferredUnit` is the default for components with no override; overrides survive a change
     to it (they are absolute, not deltas).
  4. All documents honor overrides (schematic, runout, wear, undercut, consolidated).
  5. `unitLocked` is unchanged — it still pins the **document** unit; overrides are independent.
  Related but distinct: dual-unit *display* shows both units for every dimension; this shows one
  chosen unit per component. §8 guardrail respected — tier origin, measurement reference, and
  units remain independent concerns.
- [x] Backup auto-mirror folder — DONE 2026-08-25: user picks a SAF folder once in Settings →
  Data (persisted tree URI, `takePersistableUriPermission` read+write); every internal document
  save silently mirrors a copy there, overwrite-in-place by display name. Hooked in
  `InternalStorage.save`'s Context overload, fire-and-forget on `BackupMirror`'s own IO scope —
  a revoked grant, deleted folder or IO error can never delay or roll back the save; it logs on
  the IO channel and never clears the stored URI (the user may re-grant). Drafts, templates,
  zip restores and snapshots are excluded structurally (they use the directory-taking save
  overload / DataStore). Was Tier 3 of the 2026-05-27 backup plan.
  **Both v1 bounds closed the same day:** a delete and a rename now propagate
  (`BackupMirror.onDocumentDeleted` / `onDocumentRenamed`, hooked from `InternalStorage.delete`
  and `rename`'s Context overloads and only when the internal operation succeeded — the folder
  copy of a document that is still here is a backup, not a leftover). A rename is
  write-new-then-delete-old, never SAF `renameDocument` (tree-URI rename support varies by
  provider), and the old copy only goes once the new one is provably written. Settings → Data
  gained a **"Mirror all now"** catch-up row for documents saved before the folder was picked,
  reporting "Mirrored N of M". Write and delete resolve a name through one matcher
  (`findMirrorEntry`) so a delete can never miss the copy a write was maintaining.
- [ ] "Indicated wear" rendering style for wear bands (requested 2026-07-18): match the shop
  hand-sketch convention — squiggly/wavy lines along the liner top and bottom edges in
  the worn region, with straight lines depicting the wear on the liner face itself —
  as an alternative/refinement to the current hatched bands. Specific ideas exist;
  get a sketch/photo before building. Applies to detail strips + overlay (main-profile
  bands probably stay hatched at that scale).
- [ ] Compact wear-strip option: strips currently stretch the liner toward full content
  width for readability; a denser mode (don't stretch, natural/shared scale) would ease
  crowded 3-strip pages. Full-stretch reads well, so keep it the default.
- [ ] **Runout sheet: tap-to-place bubble** (requested 2026-07-26) — tap a shaft location,
  then tap where the bubble should sit. **The leader half of this item is superseded**: the
  auto leader shipped 2026-08-25 (§3) and answers the shared clarity complaint, so a manual
  placement would reuse the same engine-planned leader rather than introducing a second
  convention. What is left is the *placement* gesture — and note that long-press-drag on a
  bubble already pins a station (`RunoutStationPlacements`), which covers most of the need.
  Worth confirming on-device whether anything remains wanted here before building it.
- [x] **Drawing preset profiles (app-wide)** (asked 2026-08-14, shipped 2026-08-25). Settings →
  Drawing → "Profiles": save the current drawing look under a name, apply / rename / delete it,
  plus a section-wide **Restore Drawing defaults** behind a confirmation. `settings/DrawingProfile.kt`
  (payload + pure codec), one `drawing_profiles` JSON map in DataStore, applied through the
  existing setters so every mirror fires. App-wide as decided: no per-doc field, no envelope
  change, no "active profile" state. Scope = the whole `PdfPrefs` plus line thickness; capability
  gates, the dual-units default, theme/preview/undercut styling and dev options are out.
  The per-job `RunoutConfig` pair (Shaft height, liner compression) stays per-document — a look
  is app-wide, a fit is per-job.

---

## 7. Explicit Non-Goals (Do NOT Implement)

- Multi-page PDF or foldouts
- DXF export
- BOM / machining tables
- Stress analysis or deflection math
- Non-linear scaling modes
- Cloud sync or AI features

---

## 8. Guardrails

- PDF pages must always paint a white background explicitly
- PDF rendering must not depend on app theme or system dark mode
- Tier origin, measurement reference, and units are independent concerns — changes to one must not affect the others
- `ShaftRenderer` and `ShaftPdfComposer` are separate drawing paths — a fix in one does not propagate to the other automatically
- `blockingExportError()` is the single gate for PDF export; do not add secondary gates elsewhere
