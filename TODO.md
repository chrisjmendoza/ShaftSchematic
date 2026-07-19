# ShaftSchematic TODO

**Version: v0.5.x Development Queue**  
**Last updated: 2026-07-18**

Tasks are ordered by priority. Completed series are collapsed to a single summary line to keep this readable.

---

## 0. Current System State (updated 2026-05-30)

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
| Autosave / draft restore | ✅ Working |
| ShaftScreen.kt | ✅ Carousel extracted to `ComponentCarousel.kt` (2322 → 1434 lines) |
| Sidebar nav (3 tabs) | ✅ EditorSidebar + EditorTab + ShaftEditorRoute updated |
| Runout drawing | ✅ RunoutPdfComposer, inline shaft preview, scrollable layout, collision-free alternating bubble layout via shared `geom/RunoutBubbleLayout.kt`; resolved-component geometry (2026-07-18) |
| Wear document | ✅ WearPdfComposer, dye-pen PASS/FAIL checkboxes, field notes; resolved-component geometry (2026-07-18) |
| Liner wear areas | ✅ Built 2026-07-18 (all 4 phases + input spec: SET/liner-edge references, blocking span validation, PDF detail strips with dimension rails) — awaiting Chris's on-device verification. See `docs/LinerWearAreas_BuildLog_2026-07-18.md` |
| Line thickness control | ✅ Slider 50%–200% in Settings, DataStore-persisted, affects preview + PDF |
| OAL include-thread toggle | ✅ PDF OAL span now extends to shaft ends when thread marked included |
| Resolved component pipeline | ✅ Wired into schematic screen/PDF + runout & wear documents (2026-07-18) |
| Insert-Between workflow | 🔲 Not implemented |
| Liner shoulders | 🔲 Not implemented |
| Fiberglass body support | 🔲 Not implemented |

---

## 1. Active Sprint — Refactor Complete (Carousel Phase)

- [x] **Carousel extraction** — `ComponentCarouselPager`, `EdgeNavButton`, `ComponentPagerCard`, `ComponentCard` moved to `ui/screen/ComponentCarousel.kt`. Carousel-private helpers (`CommitNum`, `dispKw`, `fmtTrim`, `pitchMmToTpi`) moved with them. Shared helpers (`abbr`, `disp`, `formatDisplay`, `toMmOrNull`, `parseFractionOrDecimal`, `tpiToPitchMm`) made `internal` so both files can reach them. ShaftScreen.kt: 2322 → 1434 lines.

**Remaining refactor work (lower priority — defer until ShaftScreen grows again):**

- [ ] Extract preview panel into `ShaftPreviewPanel.kt`
- [ ] Extract ViewModel event wiring into `ShaftScreenController.kt`

---

## 2. Validation Enhancements (Next After Refactor)

### 2.1 Remaining Validation Items

- [ ] Taper on-blur field validation — rate derivation errors (missing both diameters with no rate, derived diameter < 0) shown inline on the field
- [ ] Validate taper slope only when `lengthMm > 0` (currently deferred)
- [ ] `freeToEndMm` badge: use `safeSpec` when `overallLengthMm == 0` (preview-mode edge case)

### 2.2 Unimplemented Warning Rules (VALIDATION_RULES.md §3–4)

These are defined in the contract but not yet computed. Lower priority — add when working in adjacent areas:

- [ ] §3.2 Body: diameter discontinuity vs adjacent body
- [ ] §3.3 Taper: large mismatch with adjacent body diameter
- [ ] §3.5 Liner: `odMm < underlying shaft body diameter`
- [ ] §4.3 Spec: tiny segments < 1 mm *(partially done — warning exists but only checks component-level, not spec-level)*
- [ ] §4.3 Spec: zero-body coverage warning

---

## 3. Rendering / Component Backlog

- [ ] **Liner shoulders** — aft/fwd shoulder length fields, stepped shoulder rendering in preview and PDF
- [ ] **Fiberglass body segments** — model flag, dark fill / hatch pattern, label. Reference: `assets/20251022_172641.jpg`
- [ ] **`freeToEndMm` safeSpec** — preview-mode OAL=0 behavior (`§3.1`)

### Shelved (Not Required for Marine Propeller Shafts)

- ~~Body keyway support~~ — shelved; no shop use case identified

---

## 4. Tech Debt

### 4.1 Dialog Cleanup (`§5.2`)

- [ ] Standardize confirm/cancel patterns across all Add dialogs
- [ ] Standardize commit-on-blur across all fields
- [ ] Remove leftover legacy length-editing utilities

### 4.2 Build Tooling (`§5.3`)

- [ ] Keep Gradle wrapper, AGP, and `libs.versions.toml` in sync
- [ ] Isolate tooling updates into `chore(build)` commits

### 4.3 Post-Tiering Cleanup (LOW, deferred to v0.5.x)

- [ ] Audit tiering-related helpers for dead or redundant code
- [ ] Add optional debug overlay showing tier origin and measurement reference (preview only)

---

## 5. Testing Burndown

### 5.1 Unit (Complete)

- [x] SnapEngine, freeToEndMm, taper rate parsing + derivation
- [x] Thread pitch ↔ TPI, OAL exclusion, PDF footer end-feature detection
- [x] LinerDimAdapter (9 cases), TaperDimSpan (2 cases), BlockingExportError (7 cases)
- [x] StartOverlapValidation (10 cases), TaperKeyway (11 cases)

### 5.2 Instrumentation (Open)

- [ ] Commit-on-blur correctness
- [ ] Blocking-dialog behavior
- [ ] Preview-tap → adds at correct position
- [ ] Carousel scrolls to selected after tap in preview

---

## 6. Backlog (v0.5.x+)

- [ ] Selection → contextual "Add near selected" defaults
- [ ] Inline "Add here" buttons between components in list
- [ ] Undo/redo architecture (needed before v1.0)
- [ ] Preset library (common tapers, common shoulder patterns)
- [ ] Dual-unit display (primary in, secondary mm in smaller text)
- [ ] Quick inline mm ↔ in calculator in dialogs
- [ ] Backup auto-mirror folder — user picks a SAF folder once in Settings (persisted URI); every internal save silently mirrors a copy there so the off-device backup is always current. Needs `takePersistableUriPermission` + careful URI-permission lifecycle handling (revoked permission, deleted folder). Originally Tier 3 of the 2026-05-27 backup plan; the shipped backup system covers Tiers 1–2.
- [ ] "Indicated wear" rendering style for wear bands (Chris, 2026-07-18): match the shop
  hand-sketch convention — squiggly/wavy lines along the liner top and bottom edges in
  the worn region, with straight lines depicting the wear on the liner face itself —
  as an alternative/refinement to the current hatched bands. Chris has specific ideas;
  get a sketch/photo before building. Applies to detail strips + overlay (main-profile
  bands probably stay hatched at that scale).
- [ ] Compact wear-strip option: strips currently stretch the liner toward full content
  width for readability; a denser mode (don't stretch, natural/shared scale) would ease
  crowded 3-strip pages. Chris noted full-stretch reads well, so keep it the default.

---

## 7. Explicit Non-Goals (Do NOT Implement)

- Multi-page PDF or foldouts
- DXF export
- BOM / machining tables
- Stress analysis or deflection math
- Non-linear scaling modes
- Cloud sync or AI features
- Body keyway (shelved)

---

## 8. Guardrails

- PDF pages must always paint a white background explicitly
- PDF rendering must not depend on app theme or system dark mode
- Tier origin, measurement reference, and units are independent concerns — changes to one must not affect the others
- `ShaftRenderer` and `ShaftPdfComposer` are separate drawing paths — a fix in one does not propagate to the other automatically
- `blockingExportError()` is the single gate for PDF export; do not add secondary gates elsewhere
