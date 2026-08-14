# ShaftSchematic TODO

**Version: v0.5.x Development Queue**  
**Last updated: 2026-08-14**

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
| ShaftScreen.kt | ✅ Carousel, preview panel, and event wiring extracted (2322 → 1235 lines) |
| Sidebar nav (5 tabs) | ✅ Schematic / Runout Sheet / Wear Document / Undercut Drawing / Consolidated Output (`EditorSidebar` + `EditorTab` + `ShaftEditorRoute`) |
| Runout drawing | ✅ RunoutPdfComposer, inline shaft preview, scrollable layout, collision-free alternating bubble layout via shared `geom/RunoutBubbleLayout.kt`; resolved-component geometry (2026-07-18) |
| Wear document | ✅ WearPdfComposer, dye-pen PASS/FAIL checkboxes, field notes; resolved-component geometry (2026-07-18). Reworked 2026-07-28: every liner gets a detail strip (with or without wear), blank write-in template (circle-one AFT/FWD anchors, edge-bar rails), profile-band space reclaim, uniform strip heights, shared positional liner titles. On-device verified through the layout round |
| Liner wear areas | ✅ Built 2026-07-18 (all 4 phases + input spec: SET/liner-edge references, blocking span validation, PDF detail strips with dimension rails) — awaiting on-device verification. Build record in git history (`docs/LinerWearAreas_BuildLog_2026-07-18.md`) |
| Wear pits (X markers) | ✅ Built 2026-07-21 — small/large pit "X"s on bodies, tapers & liners (tap to open a segment; explicit Add X / Remove X / Clear all tools); drawn on the wear PDF profile + strips. Wear PDF now keeps the shaft profile always on top with a 2-column detail-strip grid. See CHANGELOG + "Wear Pits" in `app/src/main/java/com/android/shaftschematic/docs/RunoutSheet.md`. Awaiting on-device verification |
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
| Appearance settings | ✅ Built 2026-08-04 — System/Light/Dark + high contrast for Compose chrome; paper sheets pinned to fixed ink (`SheetInk.kt`), PDF untouched. See `app/src/main/java/com/android/shaftschematic/docs/Appearance.md` |
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
- [x] **Taper orientation discrepancy — fixed 2026-08-06** (forward fix per the analysis's
  recommendation): the Add dialog's SET/LET swap is re-keyed on the **physical half**
  (judged against the post-add OAL — `oalAfterTaperAddMm`), the measure-from chip persists
  as `Taper.authoredReference`, and `addTaperAt`/`updateTaper` derivation lost the stale-OAL
  face bug. Data-repair normalization deliberately **declined** — reversed pairs stored by
  earlier builds decode exactly as saved (golden rule; pinned by test). Full detail:
  CHANGELOG 2026-08-06 + `docs/TaperOrientation_Analysis_2026-07-26.md` (marked RESOLVED).
- [ ] **OAL reload edge (low):** a file saved with manual OAL exactly equal to the content end
  reloads as "auto" (`importJson` uses `> coverageEnd + 1e-3`), and the auto-sync effect then
  keeps OAL glued to the content end — silently dropping a *leading* auto span (components not
  starting at 0). Adds no bodies; changes the picture. Candidate fixes: also treat OAL as
  manual when the first component starts > 0, or persist the manual flag in the doc envelope
  (schema change). Found during the 2026-07-26 explicit-bodies investigation.

---

## 3. Rendering / Component Backlog

- [ ] **Liner shoulders with a radius selector** (expanded 2026-08-14) — aft/fwd shoulder
  length fields and stepped shoulder rendering in preview and PDF, plus a **radius selector**
  for the shoulder edge at each liner end: a machined liner shoulder is rarely a sharp corner,
  and the fillet is a real machining instruction, not just a drawing nicety. Open questions
  before building: is the radius per-end or one value per liner; does it come from a list of
  standard radii (like the taper-rate 3% snap list) or free entry; does it print as a value +
  leader, a footer note, or both. Shares the draw-both-sites rule — preview and PDF must
  construct the fillet identically, so the arc math belongs in `geom/`.
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
- [ ] **Runout bubble leader clarity** (on-device report 2026-08-14: "there are still times
  they are not clear to where they are pointing") — the auto-placed alternating bubble rows
  leave the eye guessing which station a bubble belongs to once rows stack or a shaft is
  crowded. Same underlying problem as the tap-to-place leader-line item in §6, and probably
  the same fix: draw a leader from the bubble to its station whenever the bubble is not
  directly over it, rather than relying on proximity. Cheaper interim options if the full
  leader is deferred: a witness tick at the station, or tightening the alternation so a bubble
  never sits closer to a neighbour's station than its own (`geom/RunoutBubbleLayout.kt` is pure
  and unit-tested, so the rule can be pinned before any drawing changes). Canvas + PDF must
  draw the leader identically — draw-both-sites, same posture as the bubble value/high-spot
  marker.

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
  `app/src/main/java/com/android/shaftschematic/docs/Appearance.md`.
- [ ] **Wave 3:** shared PDF profile-drawing helper across the three composers + parity
  controls; ViewModel update-method generics
- [ ] **Wave 4:** structural splits of the remaining oversized files

### 4.2 Build Tooling (`§5.3`)

- [ ] Keep Gradle wrapper, AGP, and `libs.versions.toml` in sync
- [ ] Isolate tooling updates into `chore(build)` commits
- [x] Bump `actions/checkout` and `actions/setup-java` to v5 in the Firebase workflow —
  done 2026-07-28, deprecation warning cleared

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
  `app/src/main/java/com/android/shaftschematic/docs/ShaftViewModel.md`
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
  **Related:** the auto-placed rows have their own clarity complaint (§3, 2026-08-14). If the
  leader gets built here, an automatic leader probably fixes both — decide the two together
  rather than shipping two conventions.
- [ ] **Drawing preset profiles (app-wide)** (asked 2026-08-14). Note first that per-user
  tailoring **already exists**: every drawing pref (fraction style, arrow size, line thickness,
  S-break threshold, sizing-curve anchors, shading) is persisted per device in DataStore, so
  each install already keeps its own look — the `PdfPrefs` defaults only decide what a *fresh*
  install starts with. What is genuinely missing is above that: a **section-wide "restore
  Drawing defaults"** (today only individual controls have their own reset buttons), and
  **named preset profiles** — save the current drawing prefs under a name and switch between
  them, for a shared device or a shop that wants a different look per customer.

  **Product decision (2026-08-14): profiles are APP-WIDE, never per-document.** A machinist is
  working against the clock; restyling per job is a hassle they should not have to think about,
  so a profile is set once and applies to every shaft they draw. This is what makes the feature
  cheap: it stays a set of DataStore prefs, with **no persisted per-doc field and no
  doc-envelope change**. Do not "improve" it later by having a document remember the profile it
  was drawn with.

  **Not in scope of that decision:** the per-job `RunoutConfig` pair ("Shaft height" +
  liner compression) stays per-document, and is not an exception to be tidied away. Those are
  not style preferences — they are how *this particular shaft* is made to fit the page, a
  geometry consequence of its own proportions, so a shared value would be wrong for the next
  shaft. The line is: a **look** is app-wide; a **fit** is per-job.

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
