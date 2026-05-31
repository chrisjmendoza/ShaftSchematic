# ShaftSchematic TODO

**Version: v0.5.x Development Queue**  
**Last updated: 2026-05-30**

Tasks are ordered by priority. Completed series are collapsed to a single summary line to keep this readable.

---

## 0. Current System State

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
| Keyway on Taper | ✅ Open + floating, plan-view rectangle, mill-cutter arc, white fill |
| Carousel selection fix | ✅ Fixed (seeded on load, swipe works before first tap) |
| Shared signing config | ✅ debug.keystore committed; all machines update-install |
| Internal save/open | ✅ Working |
| Autosave / draft restore | ✅ Working |
| ShaftScreen.kt | ✅ Carousel extracted to `ComponentCarousel.kt` (2322 → 1434 lines) |
| Resolved component pipeline | 🔲 Partial — not fully wired into rendering |
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
