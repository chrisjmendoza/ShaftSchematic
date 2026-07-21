# Runout Bubble Editor — Build Plan & Progress Log

**Branch:** `feat/runout-bubble-editor` (cut off `main` @ `24169a5`)
**Started:** 2026-07-21 (overnight autonomous run via `/loop`)
**Status legend:** ⬜ not started · 🟡 in progress · ✅ done · ⚠️ blocked/needs-user

> This file is the **resume anchor**. Any fresh context (after a summary or a usage-window
> reset) must read this file first, run `git status` / `git log --oneline -8`, run the test
> suite, and continue from the first ⬜/🟡 task. Update the checkboxes and the "Session log"
> at the bottom after every work increment.

---

## 1. Feature summary (from Chris, 2026-07-21)

Make the runout **bubbles interactive**:

1. **Clickable bubbles** — tapping a bubble on the Runout screen opens a popup dialog with a
   large version of that bubble.
2. **High-spot marker** — in the popup, tapping a spot on the **ring** places a marker denoting
   the high spot for that station. Tapping/dragging **on the ring** moves the marker; the marker
   follows the finger until release. Touching anywhere **not on the ring** does nothing (the
   ring is an annular hit-band; the center hosts the value input).
3. **Snap** — marker angle snaps to **30-minute increments on a 12-hour clock face** = 24
   positions, every 15°. 12 o'clock = top.
4. **Value input** — a text box in the **middle** of the bubble to type the TIR reading.
5. **Optional** — marker and value are both optional. A runout sheet exports fine with neither.
   If entered, they **print on both the on-screen preview and the PDF export**.
6. **Buttons** — three: **Clear** (wipe marker + value), **Cancel** (discard, close), **Save**
   (persist, close).
7. **Reopen/edit** — a saved bubble reopens with its saved marker + value intact; they persist
   until the user edits them.
8. **Keyway cutout rendering** (polish, lower priority than the above):
   - Current: an open square notch box drawn straddling the top rim, sticking **out past** the
     circle boundary.
   - **Minimum acceptable:** clip the box so only its **lower half** shows (nothing sticks out
     past the rim).
   - **Ideal:** render the circle **with the keyway cutout represented** — remove the top arc of
     the circle where the keyway sits, and show the slot descending into the circle (open at top,
     closed at the slot bottom). Mirror the taper keyway convention (`drawKeywayNotchPdf`).
   - Chris: "I could live with [minimum]. That's a polish item — focus on the runout circle
     functionality."

---

## 2. Decisions made (Chris can veto in his loop kick-off message)

- **D1 — Value units:** store TIR value as **canonical mm** (`valueMm: Float?`), enter/display in
  the app's **active unit** via `NumericInputField`, consistent with the project unit-edge rule.
  Nothing is persisted yet, so this is cheaply reversible. *(If Chris wants always-thousandths-of-
  an-inch regardless of app unit, that's a contained change to the input formatting + storage.)*
- **D2 — Branch:** `feat/runout-bubble-editor` off `main` for a clean independent diff (not off
  `feat/body-keyway`).
- **D3 — High-spot angle storage:** store the drawn angle as a snapped value. Representation:
  `highSpotHalfHours: Int?` in `[0, 23]` (0 = 12 o'clock, increasing clockwise; angle = `n * 15°`).
  Self-documenting and matches the clock mental model. `TirDirection` (existing) governs shop
  *interpretation* of the clock direction and is NOT applied to the drawn angle — we draw the
  marker exactly where the user placed it.
- **D4 — Stable identity / orphan policy:** a reading is keyed by **`(componentId, stationIndex)`**
  where `stationIndex` is the ordinal of the station among that component's stations as produced by
  `collectRunoutStations`. If the user reduces a component's station count, readings whose
  `stationIndex` no longer maps to a station are **orphan-dropped** at resolve/decode time (mirrors
  `WearSpot` orphan-drop). Documented limitation: changing station count can drop trailing readings.
- **D5 — Editor UI state** (which bubble is open) is **local** to `RunoutRoute` (`remember`),
  holding the selected bubble key + a working copy. Only Save writes through to the ViewModel.
- **D6 — Keyway ambition:** implement the **ideal** cutout if the core functionality lands with
  time to spare; otherwise ship the **minimum** (clip to lower half). Never let keyway polish block
  or destabilize the interactive core.

---

## 3. Architecture map (verified 2026-07-21)

**Data model / persistence**
- `settings/RunoutConfig.kt` — existing runout config (per-component count overrides + `TirDirection`).
  Only bubble *counts* and TIR direction; no values/angles today.
- `doc/ShaftDocCodec.kt` — `ShaftDocV1` envelope. Extension point: add an additive `@Serializable`
  defaulted field (KDoc says new defaulted fields round-trip silently; no version bump). Template to
  copy: `wearRecord: WearRecord` (`@SerialName("wear_record")`).
- `model/WearSpot.kt` — **the template** for a new interactive per-item record (flat `@Serializable`
  list beside `RunoutConfig` in the envelope, outside `ShaftSpec`, orphan-drop at decode).

**Placement engine (shared, pure Kotlin) — DO NOT re-implement in a renderer**
- `geom/RunoutBubbleLayout.kt` — `collectRunoutStations` → `planRunoutBubbles` → `RunoutBubblePlan.finish`.
  - `RunoutStationX(componentId, stationMm, stationX)` — station identity today is `(componentId, stationMm)`.
  - `PlacedRunoutBubble(componentId, stationMm, stationX, surfaceY, bubbleX, bubbleCenterY, row, leader)`
    — hit-test target; already carries `componentId`, `bubbleX`, `bubbleCenterY`; `geom.radius` is the radius.
  - **PLANNED CHANGE:** add `stationIndex: Int` to `RunoutStationX` and `PlacedRunoutBubble` so each
    bubble carries its stable key. Assign it in per-component station order inside `collectRunoutStations`.

**On-screen render (Compose Canvas)** — `ui/screen/RunoutRoute.kt`
- Canvas ~lines 302–356; `transformable` (pinch-zoom/pan) at ~300.
- `DrawScope.drawRunoutMarkers(...)` ~502–565: leader polyline, `drawCircle` (stroke), keyway box
  (`notchSize = radius*0.35f`, offset UP by `radius + 0.6*notchSize` → sticks past rim; white blank
  rect then stroked outline). **No value text, no click handling today.**
- Bitmap preview: `renderRunoutBitmap` ~585–619; `PdfPreviewOverlay` ~641–743.

**PDF render (Android Canvas)** — `pdf/RunoutPdfComposer.kt`
- `composeRunoutPdf(...)` ~90–258; shares the placement engine with the canvas.
- `drawPlacedBubbles(c, bubbles, outline)` ~532–556: mirror of `drawRunoutMarkers`. Keyway box
  `sq = KEYWAY_SQUARE_SIZE_PT (7f)`, `top = bubbleCenterY - BUBBLE_RADIUS_PT - sq*0.6f`.
- Constants ~609–617: `BUBBLE_RADIUS_PT=20`, `BUBBLE_MIN_GAP_PT=5`, `SHORT_LEADER_PT=18`,
  `KEYWAY_SQUARE_SIZE_PT=7`.
- `drawKeywayNotchPdf` ~516 draws a *real* keyway notch on tapers — reuse this convention for the
  ideal bubble cutout.
- KDoc ~73–77 already anticipates "a radial line inside the circle to indicate high-spot direction."

**⚠️ Two draw sites must stay in lockstep** — any bubble visual change (value text, high-spot radial
line, keyway cutout) goes into BOTH `drawRunoutMarkers` (Compose) and `drawPlacedBubbles` (PDF).
`docs/RunoutSheet.md` mandates they render identically.

**Screen / ViewModel**
- `EditorTab.RUNOUT` → `ShaftEditorRoute.kt` ~87–91 → `RunoutRoute(vm, onExportRunout, onOpenSidebar)`.
- `ui/viewmodel/ShaftViewModel.kt` owns runout state: `_runoutConfig` ~344; `setRunoutBubbleCount`
  ~352; `setTirDirection` ~365. Autosave combine includes `runoutConfig` (index [7] ~548); restored
  in snapshot ~880; export/import JSON ~1952/1977; reset ~2017. **New readings state wires into all
  these same sites.**

**Interaction template to copy** — `ui/screen/WearRoute.kt` + `ui/screen/LinerWearDetail.kt` +
`ui/screen/LinerWearMath.kt`: `pointerInput` tap → invert canvas transform → pure-math hit-test →
open detail overlay → `NumericInputField` (commit-on-blur) → `vm.addWearSpot/updateWearSpot/removeWearSpot`.

**Docs**
- `docs/RunoutSheet.md` — authoritative subsystem doc (must be updated). Notes future options:
  "User-selectable keyway reference angle", per-bubble measurement table rows.
- `docs/archive/runout_bubble_collision_system_2026-07-18.md` — placement engine deep dive.

**Tests**
- `app/src/test/java/com/android/shaftschematic/geom/RunoutBubbleLayoutTest.kt` — placement engine only.
- No tests for RunoutConfig serialization, PDF render, keyway notch, VM runout methods, or interaction.

---

## 4. Task breakdown

### Phase 0 — Setup ✅
- [x] Map subsystem (done via Explore agent).
- [x] Create branch `feat/runout-bubble-editor`.
- [x] Write this plan/progress doc.
- [x] Baseline: full suite green (BUILD SUCCESSFUL, 0 failures, ~23s).

### Phase 1 — Data model + persistence ✅
- [x] Added `model/RunoutReading.kt`: `RunoutReading(componentId, stationIndex, valueMm?,
      highSpotHalfHours?)` + `RunoutReadings` with `find`/`withReading`/`without`; empty entries
      auto-dropped.
- [x] Extended `ShaftDocV1` + `Decoded` (`ShaftDocCodec.kt`) with `@SerialName("runout_readings")`;
      readings pass through decode untouched (orphan pruning deferred to render layer — documented).
- [x] `AutosaveManager.SessionSnapshot` gained `runoutReadings` (defaulted).
- [x] ViewModel: `_runoutReadings` StateFlow + `setRunoutReading` / `clearRunoutReading`; wired into
      autosave combine (now 12 flows), `restoreSnapshot`, `exportJson`, `importJson`, `newDocument`.
- [x] Tests: `model/RunoutReadingTest.kt` — helpers, envelope round-trip, absent→empty, legacy→empty,
      orphan pass-through.

### Phase 2 — Pure interaction math ✅  (`ui/screen/RunoutReadingMath.kt`, unit-tested)
- [x] `bubbleAngleDeg` (0=top, clockwise), `snapToClockTick` (24 ticks/15°, wraparound),
      `clockTickAngleDeg`, `clockTickRimOffset` (y-down), `clockTickLabel`, `isOnRingBand`,
      `pickBubbleAt`.
- [x] Tests: `ui/screen/RunoutReadingMathTest.kt` — angles, snap wraparound, rim round-trip, labels,
      band edges, nearest-bubble pick.

### Phase 3 — Engine change: stable station index ✅
- [x] Added `stationIndex` (defaulted 0) to `RunoutStationX` + `PlacedRunoutBubble`; assigned per
      component in `collectRunoutStations` via `forEachIndexed`; propagated through `finish()`.
- [ ] (pending build) confirm existing `RunoutBubbleLayoutTest` still green.

### Phase 4 — Popup editor UI ✅  (`ui/screen/RunoutBubbleDialog.kt`)
- [x] Compose `Dialog` with a large bubble Canvas (circle, 24 ticks, cardinal labels, keyway slot).
- [x] Draw high-spot radial line + rim dot at the current tick.
- [x] Two `pointerInput`s (tap + `detectDragGestures`) mapped via `markerTickFromTouch` → only act on
      the ring band (`isOnRingBand`); off-ring touches ignored; centre passes to the value field.
- [x] Centre value field (own text state, active-unit, parsed to mm on Save — robust for explicit Save).
- [x] Buttons: **Clear** (reset working marker + value in place — the marker-removal affordance),
      **Cancel** (discard, close), **Save** (`vm.setRunoutReading`, close; empty ⇒ reading removed).
- [x] Working state seeds from the stored reading, so reopening shows saved values until edited.

### Phase 5 — Wire clicks on the Runout canvas ✅
- [x] `computeRunoutPreview` (Density ext) hoists the plan so draw + tap share identical geometry.
- [x] `pointerInput` + `detectTapGestures` on the preview Box; inverts the graphicsLayer scale/offset
      (via `rememberUpdatedState` so it reads live scale/offset without re-keying) → `pickBubbleAt`
      (generous tolerance) → opens the dialog. Pinch/pan `transformable` retained alongside.

### Phase 6 — Render values + markers in BOTH draw sites ✅
- [x] `RunoutReadings` lookup threaded into `drawRunoutMarkers` (Compose) and `drawPlacedBubbles` (PDF);
      `composeRunoutPdf` + `renderRunoutBitmap` gained a `runoutReadings` param (wired at both call sites;
      preview `LaunchedEffect` re-renders on readings change).
- [x] Value text centred in the circle via shared `util/formatRunoutValue` (active unit, both sites).
- [x] High-spot marker (red radial line + rim dot) at `highSpotHalfHours` via shared `clockTickRimOffset`.
- [ ] (pending build/visual) confirm identical look canvas vs PDF via a rendered preview / artifact.

### Phase 7 — Keyway cutout rendering ✅ (ideal implemented)
- [x] Replaced the protruding square notch with an **open-topped keyway slot**: the top arc is broken
      across the slot mouth (`drawArc` gap at 12 o'clock) and two slot walls descend into the circle with
      a bottom connector — nothing protrudes past the rim. Implemented identically in both sites
      (`drawRunoutBubbleRing` Compose / `drawRunoutBubbleRingPdf`). `KEYWAY_SQUARE_SIZE_PT` now unused.

> **Moved:** the pure clock/hit-test math now lives in `geom/RunoutReadingMath.kt` (was `ui/screen`) so
> the PDF composer shares it without a `pdf → ui` dependency. Value formatting shared via
> `util/RunoutValueFormat.kt`.

### Phase 8 — Docs + changelog + memory ✅
- [x] `docs/RunoutSheet.md`: new "Runout Bubble Editor" section + responsibilities bullet + updated
      Contracts (keyway cutout, reference-only readings, two-draw-site lockstep); retired the
      "user-selectable keyway angle / table rows" future options that this covers.
- [x] `CHANGELOG.md` — 2026-07-21 entry.
- [x] CLAUDE.md — new "Runout readings are reference features" invariant.
- [x] Memory updated (see Session log).

### Phase 9 — Verification & handoff ✅
- [x] Full unit suite green: **580 tests, 0 failures, 0 errors** (`testDebugUnitTest --rerun-tasks`).
      Main app compiles (`compileDebugKotlin`).
- [x] Same-math SVG artifact published for markup review (link in Session log).
- [x] Everything left **uncommitted** on `feat/runout-bubble-editor` (no-GPG-when-away).
- [⚠️] **Pre-existing, unrelated:** `compileDebugAndroidTestKotlin` fails on
      `androidTest/.../EditorTopBarExportPdfTest.kt:199` (missing ~14 callbacks like
      `onAddCouplerBoltSlot`, `onUpdateTaperLabel`, `onOpenSidebar`). **Verified this fails
      identically on a clean tree with all my work stashed** — it is stale instrumented-test code
      inherited from `main`, NOT caused by this feature, and not in the unit-test gate. Left as-is
      (fixing an unrelated screen's test is out of scope for this branch).

---

## 5. Guardrails (project invariants that constrain this work)

- **Canonical mm everywhere in the model/VM/renderer;** unit conversion only at the UI input edge.
- **Runout readings are reference-only:** must NOT affect OAL (`coverageEndMm`), collision
  (`collidingIds`), body split/merge, or overlap validation. (Same posture as coupler bolt slots.)
- **Placement logic lives only in `geom/RunoutBubbleLayout.kt`** — never re-implement it in a renderer.
- **Both bubble draw sites render identically** (`drawRunoutMarkers` ⇔ `drawPlacedBubbles`).
- **`NumericInputField` commits on blur only if value changed.**
- **No auto-commit; no GPG signing while Chris is away.** Work stays on the branch, uncommitted,
  for his review.
- **Deliverables go to `.md` files**, not just chat (this doc; final summary here).

---

## 6. Open questions for Chris (non-blocking; defaults chosen — see §2)

1. Value units — canonical mm + active unit (D1) vs always thousandths-of-an-inch? *(default: D1)*
2. Keyway cutout — is the "ideal" true-cutout worth the extra render work, or is "minimum" fine? *(default: do ideal if time, else minimum — D6)*
3. Should the value also render as a small **table row** under each bubble on the PDF (a noted future
   option in `RunoutSheet.md`), or only inside the circle? *(default: inside the circle only)*

---

## 7. Session log (append newest at top; keep the running status honest)

### 2026-07-21 — post-review tweak: high-spot = rim dash (Chris feedback)
- Per Chris's reference sheet: the high-spot mark is a **short dash straddling the rim** at the clock
  tick, **not** a radial line from centre (the line crowded the centred value). Changed all three
  surfaces — `RunoutRoute.drawRunoutMarkers`, `RunoutPdfComposer.drawPlacedBubbles`,
  `RunoutBubbleDialog` (dialog marker now shop-red too, WYSIWYG). Dash spans ~0.70r–1.30r along the
  radial. Suite still green; artifact + docs updated.

### 2026-07-21 — ✅ FEATURE COMPLETE (overnight autonomous run) — REVIEW SUMMARY

**What I built** — the full interactive runout bubble editor, Phases 1–9. Tap a bubble on the
Runout tab → a "zoom-in" dialog to record that station's TIR value + high-spot marker (drag/tap the
ring, snaps to 30-min clock ticks); both optional; both print on preview + PDF. The protruding
keyway square is replaced by a proper open-topped cutout into the circle. All decisions D1–D6 held.

**Status: all green.** Unit suite **580 tests, 0 failures**. Main app compiles.

**How to review (suggested order):**
1. **Artifact (2 min):** same-math SVG of the new rendering (keyway cutout, value, clock marker,
   dialog mock, before/after) → https://claude.ai/code/artifact/253e7437-628c-43fa-8869-2bf6afea4331
2. **Model/persistence:** `model/RunoutReading.kt`, `doc/ShaftDocCodec.kt` (+`runout_readings`),
   `ShaftViewModel` (`setRunoutReading`/`clearRunoutReading` + 6 persistence sites),
   `data/AutosaveManager.kt`.
3. **Math (pure, tested):** `geom/RunoutReadingMath.kt` + `test/.../geom/RunoutReadingMathTest.kt`.
4. **UI:** `ui/screen/RunoutBubbleDialog.kt` (the popup) and the `RunoutRoute.kt` diff (hoisted
   `computeRunoutPreview`, tap→dialog, new `drawRunoutMarkers` + `drawRunoutBubbleRing`).
5. **PDF:** `pdf/RunoutPdfComposer.kt` (`drawPlacedBubbles` + `drawRunoutBubbleRingPdf`, new
   `runoutReadings` param).
6. Tests: `test/.../model/RunoutReadingTest.kt`.

**Files:** new — `model/RunoutReading.kt`, `geom/RunoutReadingMath.kt`, `util/RunoutValueFormat.kt`,
`ui/screen/RunoutBubbleDialog.kt`, 2 test files, this plan. Modified — `RunoutBubbleLayout.kt`,
`ShaftDocCodec.kt`, `AutosaveManager.kt`, `ShaftViewModel.kt`, `RunoutRoute.kt`,
`RunoutPdfComposer.kt`, `docs/RunoutSheet.md`, `CHANGELOG.md`, `CLAUDE.md`.

**Everything is UNCOMMITTED** on `feat/runout-bubble-editor` (no-GPG-when-away) — review before commit.

**Open questions for you (defaults chosen; all reversible):**
1. Value unit = active-unit mm (D1), not forced thousandths. OK?
2. Keyway = full cutout (went past the "minimum lower-half" you'd accept). Fine, or want it simpler?
3. Clear = reset-in-place then Save to persist (not one-tap delete). OK?
4. High-spot marker colour = red. OK for photocopied sheets?

**Not done (deliberate / can't):**
- On-device visual check: I can't run the emulator here. The rendering is compile-verified + mirrored
  in the artifact, but the dialog gestures / small-bubble legibility want a real look on hardware.
- **Pre-existing** `compileDebugAndroidTestKotlin` failure (`EditorTopBarExportPdfTest.kt:199`,
  unrelated stale callbacks) — verified present on a clean `main`-based tree; NOT from this feature.

### 2026-07-21 — kickoff
- Mapped subsystem (Explore agent). Created branch. Wrote this plan. Decisions D1–D6 recorded.
- Next: Phase 0 baseline test run, then Phase 1 (data model + persistence).
