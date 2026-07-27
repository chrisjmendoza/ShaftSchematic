# ShaftSchematic TODO

**Version: v0.5.x Development Queue**  
**Last updated: 2026-07-26**

Tasks are ordered by priority. Completed series are collapsed to a single summary line to
keep this readable — full detail lives in `CHANGELOG.md` and git history.

---

## 0. Current System State (updated 2026-07-24)

| Area | Status |
|---|---|
| Core model (Body, Taper, Threads, Liner) | ✅ Stable |
| ShaftLayout & ShaftRenderer | ✅ Contract-locked |
| PDF export — one-page, landscape | ✅ Stable |
| Validation — blocking errors | ✅ Wired in UI (Add dialogs, carousel badges, export gate) |
| Validation — non-blocking warnings | ✅ Yellow badges in carousel; FreeToEndBadge 3-state |
| Snapping engine | ✅ Implemented & unit-tested |
| Tap-to-add pipeline | ✅ Implemented |
| OAL window / excluded thread logic | ✅ Implemented & unit-tested |
| Taper rate input + derivation | ✅ Implemented (taperRateText, parseRateText, deriveTaperDiameters) |
| Taper rate colon entry (`1:12`) | ✅ Keyboard-compatible on Android (ASCII rate input + colon filter path) |
| Taper rate auto-calc (Length + SET + LET) | ✅ Auto-by-default with manual override; 3% common-rate snap + exact `1:N.NNN` fallback; bare `1` blocked, mismatch warning shown |
| Keyway on Taper | ✅ Open + floating, plan-view rectangle, mill-cutter arc, white fill |
| Carousel selection fix | ✅ Fixed (seeded on load, swipe works before first tap) |
| Shared signing config | ✅ debug.keystore committed; all machines update-install |
| Internal save/open | ✅ Working |
| Backup & restore | ✅ Zip backup/restore via file picker, per-shaft import/export, pre-update snapshots (keep 3), Auto Backup rules; sample pruning made non-destructive (seed-hash ledger) |
| Autosave / draft restore | ✅ Reworked 2026-07-25 — dirty-gated 3-entry draft ring (per-document identity) replaces the single always-overwriting slot that caused a data-loss incident; StartScreen shows an "Unsaved drafts" list. See `docs/Autosave_Incident_2026-07-25.md` |
| ShaftScreen.kt | ✅ Carousel extracted to `ComponentCarousel.kt` (2322 → 1434 lines) |
| Sidebar nav (3 tabs) | ✅ EditorSidebar + EditorTab + ShaftEditorRoute updated |
| Runout drawing | ✅ RunoutPdfComposer, inline shaft preview, scrollable layout, collision-free alternating bubble layout via shared `geom/RunoutBubbleLayout.kt`; resolved-component geometry (2026-07-18) |
| Wear document | ✅ WearPdfComposer, dye-pen PASS/FAIL checkboxes, field notes; resolved-component geometry (2026-07-18) |
| Liner wear areas | ✅ Built 2026-07-18 (all 4 phases + input spec: SET/liner-edge references, blocking span validation, PDF detail strips with dimension rails) — awaiting on-device verification. Build record in git history (`docs/LinerWearAreas_BuildLog_2026-07-18.md`) |
| Wear pits (X markers) | ✅ Built 2026-07-21 — small/large pit "X"s on bodies, tapers & liners (tap to open a segment; explicit Add X / Remove X / Clear all tools); drawn on the wear PDF profile + strips. Wear PDF now keeps the shaft profile always on top with a 2-column detail-strip grid. See CHANGELOG + "Wear Pits" in `docs/RunoutSheet.md`. Awaiting on-device verification |
| Body keyways | ✅ Built 2026-07-20 — taper-style keyway on bodies (open + floating), 180°-apart hidden-line toggle, auto-body promotion via the "Explicit body" checkbox (checkbox-only, reworked 2026-07-25); split/merge carry keeps keyway at absolute position |
| Runout bubble editor | ✅ Built 2026-07-21 — tap a bubble to record TIR value + high-spot clock marker; open-topped keyway cutout in the bubble; drawn identically on canvas + PDF |
| Spooned keyways | ✅ Built 2026-07-22 — draw-only enlarged bowl at the closed (LET) end of an open keyway; footer note "KW length to base of spoon" added 2026-07-24 |
| Diameter callouts (schematic PDF) | ✅ Built 2026-07-22 — on-shaft Ø callouts below the shaft, 3-decimal, two-tier stacking, liners included as a separate OD group |
| Dimension values in a break | ✅ Built 2026-07-22 — PDF dimension lines seat the value in a gap in the line with inward arrows; short/colliding spans fall back to label-above |
| Line thickness control | ✅ Slider 50%–200% in Settings, DataStore-persisted, affects preview + PDF |
| OAL include-thread toggle | ✅ PDF OAL span now extends to shaft ends when thread marked included |
| Resolved component pipeline | ✅ Wired into schematic screen/PDF + runout & wear documents (2026-07-18) |
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
- [ ] No numeric upper bound on inputs — documented as a gap in `VALIDATION_RULES.md` §2.3
  (from the 2026-07-18 doc sweep); a fat-fingered 4700000 mm body is accepted silently
- [ ] Export gate only checks thread/liner positions — taper overlaps never block export
  (from the 2026-07-18 doc sweep)

### 2.2 Warning Rules (VALIDATION_RULES.md §3–4)

- [x] All five §3–4 warning rules computed + shown on carousel cards, 2026-07-24
  (`ui/util/ComponentWarnings.kt`, `specWarningMessages`). §3.3 taper-vs-body mismatch
  later removed by product decision — see §2.3
- [ ] Decide UI surface for spec-level warnings (`specWarningMessages`) — UX decision. Both the
  tiny-segment count and zero-body-coverage message are computed and unit-tested but not wired
  to any screen (badge, banner, or elsewhere); the five carousel-card-level warnings above are
  already live.
- [ ] Review the warning thresholds picked during the 2026-07-24 loop (1.5× adjacent-body
  step, 0.5 mm adjacency eps in `ui/util/ComponentWarnings.kt`) — chosen without shop input;
  sanity-check against real drawings. (Taper-mismatch 10% threshold is moot — that rule was
  removed 2026-07-26.)

### 2.3 Bug Fixes (open)

- [x] §3.3 taper-vs-body Ø-mismatch advisory — fixed 2026-07-25, still misfired on-device,
  **removed entirely 2026-07-26 by product decision** (the difference is visible in the
  drawing itself; regression test pins the no-warning behavior). Do not reintroduce.
- [ ] **Taper orientation discrepancy — analysis done 2026-07-26, fix decision pending.**
  Full investigation in `docs/TaperOrientation_Analysis_2026-07-26.md` (includes a 2-minute
  on-device repro). Confirmed in code: the Add dialog keys its SET/LET swap on the
  **measure-from toggle**, while derivation/labels use the **midpoint half** and keyway
  placement uses **diameter magnitude** — a taper added into the opposite half from its
  measure-from direction stores SET at the wrong face (drawn backwards, card labels
  swapped). Also: the Add path never persists `authoredReference` (FWD measuring frame
  lost on reopen). Recommended: re-key the dialog swap on the physical half + thread the
  toggle into `addTaperAt`; data-repair normalization is a product decision (would rewrite
  stored docs and forecloses reversed tapers).
- [ ] **OAL reload edge (low):** a file saved with manual OAL exactly equal to the content end
  reloads as "auto" (`importJson` uses `> coverageEnd + 1e-3`), and the auto-sync effect then
  keeps OAL glued to the content end — silently dropping a *leading* auto span (components not
  starting at 0). Adds no bodies; changes the picture. Candidate fixes: also treat OAL as
  manual when the first component starts > 0, or persist the manual flag in the doc envelope
  (schema change). Found during the 2026-07-26 explicit-bodies investigation.

---

## 3. Rendering / Component Backlog

- [ ] **Liner shoulders** — aft/fwd shoulder length fields, stepped shoulder rendering in preview and PDF
- [ ] **Fiberglass body segments** — model flag, dark fill / hatch pattern, label. Reference: `assets/20251022_172641.jpg`

---

## 4. Tech Debt

### 4.1 Dialog Cleanup (`§5.2`)

- [x] Done — confirm/cancel patterns and commit-on-blur standardized; legacy length-editing
  utilities removed in the 2026-07-24 dead-code sweep. The `parseFractionOrDecimal` /
  `toMmOrNull` duplication remains — tracked in `NumberField.md`

### 4.1b Deferred Refactor Waves (from the 2026-07-11 cleanup sweep, report in git history)

Waves 1–2 shipped (Wave 1 fixes 2026-07-11; Wave 2 deletion pass 2026-07-26, `ad5b198`). Remaining:

- [ ] **Theme decision:** `ShaftSchematicTheme` exists but is never wired into `MainActivity`
  (no dark mode). Wire it (one line, but needs a dark-mode visual check of preview colors —
  PDF must stay theme-independent per §8) or delete `ui/theme`.
- [ ] **Wave 3:** shared PDF profile-drawing helper across the three composers + parity
  controls; ViewModel update-method generics
- [ ] **Wave 4:** structural splits of the remaining oversized files

### 4.2 Build Tooling (`§5.3`)

- [ ] Keep Gradle wrapper, AGP, and `libs.versions.toml` in sync
- [ ] Isolate tooling updates into `chore(build)` commits

### 4.3 Post-Tiering Cleanup (LOW, deferred to v0.5.x)

- [x] Tiering helpers audited 2026-07-24 — one dead default parameter removed; unreachable
  `SpanKind.OAL` path kept as contract documentation
- [ ] Add optional debug overlay showing tier origin and measurement reference (preview only)

---

## 5. Testing Burndown

### 5.1 Unit (Complete)

- [x] Complete — SnapEngine, freeToEndMm, taper rates, thread pitch ↔ TPI, OAL exclusion,
  PDF footer, LinerDimAdapter, TaperDimSpan, BlockingExportError, StartOverlapValidation,
  TaperKeyway. Details in git history

### 5.2 Instrumentation (Done 2026-07-26)

- [x] Done — Robolectric JVM Compose harness added (`testDebugUnitTest`, no device; assert
  against `testTag`s, never composable parameter lists); commit-on-blur, blocking-dialog
  gates, tap-add position, and carousel selection sync all extracted pure + covered. Found
  and fixed the `NumericInputField` composition-commit bug along the way. Suite 719 → 796.
  Full detail: CHANGELOG "2026-07-26 (night)".
  **Deliberately not covered** (decision, not a gap): no Compose test for
  `ComponentCarouselPager` (~35 params, all callbacks — that coupling is what rotted the
  deleted androidTest) or the Add dialogs; their logic is pure and covered.
- [ ] **Open: CI does not run the test suite.** `distribute.yml` runs only
  `./gradlew assembleDebug`; `merge-on-green.yml` just automerges. So all tests are
  local-only, which blunts the point of picking Robolectric. Adding `testDebugUnitTest`
  before the assemble step would gate distribution on green — natural to want, but it's a
  release-pipeline policy change (a red test blocks a build), so it needs a decision.

**Incidental finding (product question, not a defect):** the preview hit-test
(`ShaftDrawing.kt:229-239`) covers Body/Taper/Thread/Liner but **not** `ResolvedCouplerBoltSlot`,
so tapping a slot selects the body underneath it and a slot's carousel card is unreachable by
tap. Plausibly deliberate — a slot always overlies something, and letting it win the hit-test
would make that body untappable at the slot. Decide before changing.

---

## 6. Backlog (v0.5.x+)

- [ ] **Multi-shaft per job number** (requested 2026-07-26): sometimes two shafts share one
  job number; want to select between them. Feasibility + phased architecture plan in
  `docs/MultiShaftJob_Plan_2026-07-26.md` (recommends derived job grouping over
  single-shaft files — no file-format change; Phase 0 fixes the existing runout/wear
  export-filename collision for same-job shafts). Awaiting answers to the plan's
  6 product questions before building.
- [ ] **Carousel ordering product decision** (from the 2026-07-18 doc sweep):
  `ComponentsOrdering.md` v1.1 LOCKED newest-on-top, but the carousel actually displays
  resolved components in physical order and the ViewModel's newest-first `componentOrder` is
  unused for display (doc updated to v1.2 describing reality). Decide: accept physical order
  and remove the dangling `componentOrder` display plumbing, or restore newest-on-top as a
  regression fix.
- [ ] Title-strip follow-ups (from the 2026-07-25 night run; liked, not yet requested):
  tappable title → Save As / rename; smarter untitled-draft row names on StartScreen (derive
  from job/customer/vessel via `DocumentNaming.suggestedBaseName`); title strip on the
  Runout/Wear tabs too.
- [ ] Selection → contextual "Add near selected" defaults
- [ ] Inline "Add here" buttons between components in list
- [x] Undo/redo architecture — session-scoped `SessionHistory`, done 2026-07-26; covers every
  drawing edit, 600 ms coalescing, 50-step cap. See `docs/ShaftViewModel.md`
- [ ] Undo/redo follow-ups (future scope, not blocking v1.0): cross-session/persisted
  undo history (currently in-memory, cleared on process death and at every
  new/open/import boundary), and metadata (customer/vessel/job number/notes/shaft
  position/unit) is deliberately excluded from the undoable state — revisit if that
  becomes a complaint.
- [ ] Preset library (common tapers, common shoulder patterns)
- [ ] Dual-unit display (primary in, secondary mm in smaller text)
- [ ] Quick inline mm ↔ in calculator in dialogs
- [ ] Backup auto-mirror folder — user picks a SAF folder once in Settings (persisted URI); every internal save silently mirrors a copy there so the off-device backup is always current. Needs `takePersistableUriPermission` + careful URI-permission lifecycle handling (revoked permission, deleted folder). Originally Tier 3 of the 2026-05-27 backup plan; the shipped backup system covers Tiers 1–2.
- [ ] "Indicated wear" rendering style for wear bands (requested 2026-07-18): match the shop
  hand-sketch convention — squiggly/wavy lines along the liner top and bottom edges in
  the worn region, with straight lines depicting the wear on the liner face itself —
  as an alternative/refinement to the current hatched bands. Specific ideas exist;
  get a sketch/photo before building. Applies to detail strips + overlay (main-profile
  bands probably stay hatched at that scale).
- [ ] Compact wear-strip option: strips currently stretch the liner toward full content
  width for readability; a denser mode (don't stretch, natural/shared scale) would ease
  crowded 3-strip pages. Full-stretch reads well, so keep it the default.
- [ ] **Runout sheet: tap-to-place bubble with leader line** (requested 2026-07-26): tap a
  shaft location, then tap where the bubble should sit, and connect the two with a leader
  line per the normal drawing convention — instead of (or in addition to) the current
  auto-placed alternating bubble rows. Canvas + PDF must draw the leader identically
  (draw-both-sites rule, same posture as the bubble value/high-spot marker).

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
