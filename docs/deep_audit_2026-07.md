# ShaftSchematic — Deep Audit & Productization Plan

**Date:** 2026-07-11
**Scope:** Full repository read — every production source file (~20,500 lines Kotlin), all 52 test files (~6,500 lines, 356 tests executed and verified green on this branch), both doc trees (~5,000 lines), build config, CI workflows, and all 253 commits of git history (2025-09 → 2026-07).
**Branch audited:** `feature/coupler-bolt-slots` (including ~875 uncommitted lines).
**Prior audits:** This builds on AUDIT.md (2026-05-27) and ANALYSIS.md (2026-06-19). Items resolved there were re-verified and are not repeated unless they regressed. This pass goes deeper: data integrity, test *quality* (not just count), the new coupler-slot feature, commercial viability.

---

# PART 1 — TECHNICAL AUDIT

## 1.0 Executive summary

This is a genuinely good solo codebase — better than the median professional Android app in domain modeling, documentation discipline, and test count. The mm-only model, the `geom/` package, the versioned document envelope, and the OAL/excluded-thread semantics (resolved with real domain authority) are the work of someone who understands both the machining domain and software invariants.

It is also carrying four structural liabilities that get more expensive every month:

1. **Four independent drawing codebases** for the same geometry (preview + 3 PDF composers). The OAL display bug of 2026-06-11 leaked into five surfaces precisely because of this. It will happen again.
2. **A data-integrity story that has already lost user data once** (the reinstall wipe documented in BACKUP_PLAN.md) and still has no implemented backup, no atomic writes, and an app-crash on corrupt files.
3. **Test coverage that is broad but partly illusory** — roughly seven test files re-implement production logic inside the test ("mirror tests") and therefore cannot catch regressions in the code they claim to cover. CI runs zero tests.
4. **A per-component "shotgun surgery" pattern**: adding a component type requires ~45–55 manual touchpoints (your own estimate in the coupler-slot proposal). The current branch proves the risk: **two touchpoints were missed** (§1.2, bugs B1–B2).

None of these block continued personal use. All four block selling it. The good news: the domain core is clean enough that every fix below is a refactor, not a rewrite.

### Live bugs found in this pass (new — not in prior audits)

| # | Severity | Bug | Location |
|---|---|---|---|
| B1 | **High** | `withNewOal()` re-anchors FWD-referenced tapers and liners but **not coupler bolt slots**, whose default authored reference is FWD. Change the OAL and every FWD-referenced slot silently drifts off the coupling. This is the exact bug class fixed for tapers/liners on 2026-06-22 (ANALYSIS #21). | `model/ShaftSpecExtensions.kt:158` |
| B2 | Medium | `isSessionDefault()` ignores `couplerBoltSlots` — a draft containing only slots is treated as an empty session, so autosave restore can clobber it. `shiftAllBy()` also skips slots. | `ui/viewmodel/ShaftViewModel.kt:737`, `ShaftSpecExtensions.kt:192` |
| B3 | **High (domain)** | Taper-rate derivation is inverted for AFT-direction tapers. `deriveTaperDiameters` computes the far-end diameter as `start − rate·length`, i.e. it assumes the start diameter is the **large** end. `AddTaperDialog` labels the AFT-taper start field "S.E.T. Ø" (Small End). A machinist entering SET + rate 1:12 for an AFT propeller taper gets a taper that narrows *inboard* — upside-down. `TaperRateTest` locks the wrong convention in (its "SET" is 100 and derived "LET" is 80). Only correct for FWD tapers, where the dialog swap happens to compensate. Frequency is low (entering both diameters bypasses the rate), but this produces a **wrong drawing** when hit. | `ShaftViewModel.kt:1902`, `AddComponentDialogs.kt:584`, `TaperRateTest.kt` |
| B4 | **High (data)** | Opening a corrupt/truncated `.shaft` file **crashes the app**. `OpenLocalDocumentRoute`'s item-click path calls `vm.importJson(text)` with no exception handling; the recent-files path in `AppNav` wraps only the file *read* in `runCatching` — `importJson` throws inside `.onSuccess`, uncaught, inside a coroutine. Combined with non-atomic saves (B5), a crash mid-save creates exactly the file that then crashes every open attempt. | `ui/nav/InternalDocRoutes.kt:323`, `ui/nav/AppNav.kt:470` |
| B5 | **High (data)** | `InternalStorage.save()` writes with plain `File.writeText()` — no temp-file-then-rename. Process death or disk-full mid-write corrupts the *only* copy of the document. Your own contract doc (`docs/ShaftRepository.md`) mandates "atomic writes (temp → rename)". | `io/InternalStorage.kt:100` |
| B6 | Medium | `ShaftDocCodec.decode()` never checks the `version` field. A file written by a future v2 app decodes in v1 with unknown fields silently dropped (`ignoreUnknownKeys`); saving it back **silently destroys the v2 data**. Fine for one user; unacceptable once two people on different versions share files. | `doc/ShaftDocCodec.kt` |
| B7 | Medium | `AddCouplerBoltSlotDialog` performs no bounds check against OAL — a slot row can be authored partly or wholly off the shaft. `CouplerBoltSlot.isValid()` would reject it, but nothing calls `validate()` on the add path. | `AddComponentDialogs.kt:252` |
| B8 | Medium | PDF footer columns are placed at fixed page fractions (0.40 / 0.76) with no text measurement. A long customer or vessel name overruns the FWD-taper column. Runout/wear headers concatenate the same fields into one unmeasured line. | `pdf/ShaftPdfComposer.kt:955`, `RunoutPdfComposer.kt:430` |
| B9 | Low | `drawCouplerBoltSlots` computes the local surface radius from the raw spec only. Over a region covered only by an *auto* body, it falls back to the shaft's max OD — slot circles render at the wrong radius on mixed-OD shafts. | `ShaftPdfComposer.kt:877` |
| B10 | Low | Wear document anchors its OAL dimension line to the max **body** diameter only; if the largest OD is a liner or taper LET, the line overlaps geometry. | `WearPdfComposer.kt:122` |
| B11 | Low | The unsaved-changes dialog's "Save" button navigates to the save screen but **drops the pending action** — after saving, the New/Open the user asked for never happens. | `ui/nav/AppNav.kt:498` |
| B12 | Low | The main PDF's "OAL" label always shows the typed OAL; the runout and wear PDFs label the SET-to-SET span "OAL". Same word, different number on documents from the same job whenever end geometry isn't taper-flush. | `RunoutPdfComposer.kt`, `WearPdfComposer.kt` |
| B13 | Low | Footer thread callout always prints TPI (`Ø × TPI × len`) even in millimeter mode — metric users expect pitch in mm. | `ShaftPdfComposer.kt:1044` |

---

## 1.1 Architecture — is the structure sound for where this might go?

### What is right (and should not be touched)

- **The mm-only canonical model with UI-edge conversion.** This one decision has prevented an entire genre of unit bugs and is enforced consistently. Keep it sacred.
- **`geom/` (OalComputations, OalWindow, SetPositions, DeterministicTierAssigner).** Double-precision, coordinate-anchored, list-order independent, deterministic, well-tested. This is the best code in the repo and is exactly the kind of pure-Kotlin core that ports anywhere.
- **The versioned document envelope + migration posture** (`@JsonNames` aliases, `normalized()`, default-valued fields). Right instincts; needs the version check (B6) to be complete.
- **Full-spec snapshot undo.** Restoring `beforeSpec + beforeOrder` wholesale is the correct, boring design and it survives body split/merge correctly.
- **The invariant documentation habit.** CLAUDE.md + per-file contracts caught a real regression (the AFT/FWD thread selector) and got it restored. Few solo projects have this.

### The structural problem that matters most: one geometry, four renderers

`ShaftRenderer` (Compose preview), `ShaftPdfComposer`, `RunoutPdfComposer`, and `WearPdfComposer` each contain their own body/taper/thread/liner drawing code, their own constants (`COMPRESS_TRIGGER_PT` is defined three times), and even *visually different* break-glyph curves (the main composer draws a single-cubic S; runout and wear draw a double-cubic — your three shop documents show different break symbols for the same shaft). `RunoutRoute`'s canvas preview adds a fifth copy of the station/surface-radius math. Keyway drawing exists twice (Compose + android.Canvas). Even *within* `ShaftRenderer`, the resolved-components and raw-spec branches duplicate ~200 lines verbatim.

This isn't aesthetic. The documented history proves the failure mode: the 2026-06-11 OAL bug had to be fixed in **five places** (preview, three PDFs, and the input field). TODO.md itself elevates "a fix in one does not propagate to the other" to a *guardrail* — i.e., the architecture problem has been accepted as a law of nature. It shouldn't be. The fix is a single platform-neutral pass that converts `(spec, resolved) → List<DrawPrimitive>` (lines, polys, arcs, circle-cutouts, break-glyphs), with two thin sinks: one for `DrawScope`, one for `android.graphics.Canvas`. Dimension rails and footers stay per-document; the *shaft profile* becomes single-source.

**Verdict on "does it need to change now while the surface area is small":** the profile-drawing unification — yes, now. It's the last cheap moment; every new component type (coupler slots just did this) multiplies by four. Everything else below can be staged.

### The second structural problem: component-type shotgun surgery

The coupler-slot proposal counted ~45–55 touchpoints to add one component type; this audit found the two that were missed (B1, B2). The `when(kind)` branches are exhaustive (good — the compiler catches missing enum arms), but the *implicit* participation decisions (does this type re-anchor on OAL change? does it count toward "session empty"? does it shift? does it snap?) are scattered and invisible to the compiler. Before adding liner shoulders and fiberglass bodies (both on the roadmap), extract a small per-type capability descriptor — `participatesInOal`, `anchorsToReference`, `collisionGroup`, `splitsBodies`, `countsAsContent` — consulted from the shared code paths. That turns each future component into one file plus UI, instead of a scavenger hunt.

### ViewModel and settings state

`ShaftViewModel` is 2,015 lines plus a 300-line extension file; `init` launches ~35 coroutines (one per settings key). Worse than the size is the **triplication of settings state**: each PDF pref lives simultaneously in a VM `StateFlow`, in DataStore, and in `SettingsStore._pdfPrefs` (a `@Volatile` global mutable mirror). `PdfPreviewScreen` explicitly depends on the mirror being updated synchronously before coroutines run — a fragile ordering contract documented only in a comment. One `data class AppSettings` in a single DataStore flow, exposed as one `StateFlow<AppSettings>`, deletes ~500 lines and the entire class of sync bugs. Separately, `orderAdd`/`orderRemove` are called *inside* `_spec.update {}` lambdas in every add path — `MutableStateFlow.update` may re-execute its lambda on contention, duplicating order entries. Your own ARCHITECTURE.md forbids this ("ordering mutations occur **after** spec updates"). Low practical risk today (main-thread only), but it's a violation of your own written law in ~8 places.

### Layering for the future (multi-user / cloud / multi-shop)

- `model/ShaftSpecExtensions.kt` imports `ui.order.ComponentKey` — the model layer depends on the UI package. One package move fixes it; do it before anything else grows roots.
- The document format has **no identity or provenance**: no document ID, no created/modified timestamps, no author, no app-version stamp. The moment two machinists at one shop exchange files, you cannot answer "which one is newer" or "who drew this." Adding `docId`, `createdAt`, `modifiedAt`, `authoredBy`, `appVersion` to the envelope is back-compat-free today (all defaulted) and nearly impossible to retrofit correctly later.
- There is no repository abstraction in actual use (`ShaftRepository`/`ShaftFileRepository`/`NoopShaftRepository` exist but are **dead code**); UI routes call `InternalStorage` directly and ViewModel does JSON. For a future sync story you'd want save/load funneled through one interface — but do *not* build sync infrastructure now (see Part 2). Just funnel.

**Overall architecture verdict:** sound foundation, one urgent unification (rendering), one cheap insurance policy (document identity + version check), one hygiene sweep (settings state, model→UI import). Not a rewrite. Multi-user/cloud would be an additive layer, not a restructuring — *if* the above is done first.

---

## 1.2 Code quality & technical debt — ranked by risk × effort

Ranked by (probability of producing a wrong drawing, lost data, or a stalled feature) × (cost growth if deferred). Effort assumes your demonstrated pace.

| Rank | Item | Risk | Effort | Notes |
|---|---|---|---|---|
| 1 | **Atomic writes + corrupt-file handling + backup Tier 1&2** (B4, B5; BACKUP_PLAN.md) | Data loss — *already happened once* | 2–3 days | Temp-file+rename in `InternalStorage.save`; try/catch around every `importJson` call with a "file is damaged" dialog; keep a `.bak` of the previous version on overwrite; implement the ZIP export/restore from your own plan; verify Auto Backup actually restores (`allowBackup=true` is set but `backup_rules.xml`/`data_extraction_rules.xml` are still unedited template stubs and aren't referenced from the manifest). |
| 2 | **Coupler-slot misses on this branch** (B1, B2, B7, B9) | FWD slots drift on OAL edit; feature is untested and hasn't run on a device | 1 day | Fix before merge. The branch's own proposal doc says "not yet run on device." |
| 3 | **Taper-rate inversion for AFT tapers** (B3) | Wrong physical part | 0.5–1 day | Decide the convention with your machinist hat on, fix `deriveTaperDiameters` to be direction-aware, fix the mislabeled test, add AFT+FWD cases. |
| 4 | **Unify shaft-profile drawing** (§1.1) | Silent divergence across shop documents; 4× cost per feature | 1–2 weeks | Highest-effort item on the list and still worth it. Do after 1–3, before any new component type. |
| 5 | **CI runs tests; merge-on-green actually gates** | Regressions ship silently | 0.5 day | `distribute.yml` builds `assembleDebug` and uploads to Firebase; **no workflow runs `testDebugUnitTest`**, yet `merge-on-green.yml` auto-merges PRs. Add a test job and make it a required check. |
| 6 | **Release build hygiene** | Blocks any distribution | 1 day | Release is signed with the committed debug keystore (`storePassword "android"`) — anyone with the APK's signature can push an "update" over a user's install; `isMinifyEnabled=false`; `versionCode = git rev-list --count` collides across branches and breaks on shallow clones. Needs: real upload key in a keystore *outside* the repo, minify+keep rules for kotlinx-serialization, explicit versioning. |
| 7 | **Settings-state triplication** (§1.1) | Preview/export show different prefs than saved | 2–3 days | Also removes the `PdfPreviewScreen` ordering hazard. |
| 8 | **Dead code sweep** (~1,300 lines) | Cognitive load; misleads future contributors (and future AI sessions) | 0.5 day | Confirmed zero callers: `ui/editor/ComponentCarousel.kt` (334 — an entire stale carousel), `ui/drawing/LayoutMap.kt` + `DisplayCompressionConfig`, `ui/drawing/DrawingConfig.kt`, `data/ShaftFileRepository.kt` + `NoopShaftRepository`, `pdf/ShaftPdfComposerCompat.kt`, `ui/nav/SafRoutes.kt` (both routes), `ui/input/NumberField.kt` (wrong package `shaftschematic.ui.input`), `ui/input/Inputs.kt`, `ui/input/ShaftMetaSection.kt`, `ui/input/UnitSelector.kt`, `geom.computeExcludedThreadLengths` (production), `util/TaperParser.parseTaperDisplay`. |
| 9 | **Parsing duplication** | Two grammars for shop-critical input | 1 day | `ShaftScreen.parseFractionOrDecimal` (Float, accepts `:`) vs `util/Parsing.parseFractionOrDecimal` (Double, doesn't) vs `AddComponentDialogs.toDisplayString` vs `formatDisplay`. One `util` module, one grammar, one formatter; docs (`Parsing.md`) already point at `util`. |
| 10 | **Doc consolidation** | Contradictory "authoritative" contracts mislead | 1–2 days | Two doc trees disagree: `ComponentsOrdering.md` says newest-on-top is "LOCKED"; `UI_CONTRACT.md` §4 says spatial order is authoritative. `PDF_EXPORT.md` says portrait *and* landscape, and both "uses ShaftRenderer" and "has its own drawing code." `CONTRIBUTING.md` describes a shared renderer that doesn't exist. `Defaults.md` vs `ThreadDefaults.md` disagree on thread length. `DATA_MODEL.md` contains paste artifacts ("yaml Copy code") and an orphaned section. Pick one tree (in-source), delete/redirect the other, fix the four contradictions. |
| 11 | Production debug leftovers | Log noise, perf | 0.5 day | Infinite `awaitPointerEventScope` logging loop attached to **every** carousel delete button (`ComponentCarousel.kt:993`), `Log.d` in delete paths, `ENABLE_TAP_SELECT_DEBUG` machinery. |
| 12 | Achievements system | Product identity | Decision, then 0.5 day | Gated off by default, but it adds settings keys, flows, a screen, and unlock calls sprinkled through routes. For a professional shop tool, remove it or move it fully behind dev options. |
| 13 | Misc smells | Low | ongoing | `Liner.endMmPhysical` persisted derived field; `Taper.authoredReference` typed as `LinerAuthoredReference`; private `maxOuterDiaMm()` duplicate in the composer; `formatDim(unit: Any?)` string-matching; edit-time snap not unit-aware; Compose BOM 2024.09 is ~2 years old against Kotlin 2.2.20/AGP 9.2.1, with duplicate dependency declarations (navigation 2.8.2 hardcoded *and* 2.9.5 via catalog) and unused deps (appcompat, Material Views, room-compiler). |

---

## 1.3 Test coverage — what's tested, what's dangerously not

**Status: 356 JVM tests, 0 failures, executed during this audit on the current branch.** That headline is real and better than most commercial apps this size. But the distribution matters more than the count.

### Genuinely well-covered (tests exercise real production code)

- Model helpers and invariants: `ShaftSpecTest` (23), `SegmentTest` (18), `WithNewOalTest` (17), `CollidingIdsTest` (17), `TaperKeywayTest` (11).
- Geometry: `OalComputationsTest` (13 — including excluded/included/no-taper/overlap cases), `DeterministicTierAssignerTest` (6).
- Parsing/units/formatting: `ParsingTest`, `UnitSystemTest`, `UnitConversionTest`, `LengthFormatTest` — the paths where a wrong number becomes a wrong part.
- File management: `InternalStorage` delete/rename/migration/normalization/seeding/recents (well-designed `File`-injected internals made this testable).
- JVM-visible PDF logic: `LinerDimAdapterTest` (9), `BodyOdCalloutsTest` (9), `SpanDedupingTest`, `TaperSideDetectionTest`, footer detection/order/units, `OalSpanLabelTest`.
- Validation: `StartOverlapValidationTest` (9), `CollisionWarningsTest` (14), `BlockingExportErrorTest` (7).

### The mirror-test problem (coverage that cannot catch regressions)

At least seven test files **re-implement the production logic inside the test and assert on the copy** — their own comments say so: `ShaftViewModelUpdateTest` ("the tests mirror what each updateX() function does"), `ShaftViewModelRemoveTest`, `PdfLayoutBoundsTest` ("replicates the … math from ShaftPdfComposer (lines 129–151 after fix)"), `TaperFwdRefLengthTest` ("mirrors ComponentCarousel exactly"), parts of `BodySplitMergeTest` and `ShaftSpecSnapExtensionsTest`. If the real `updateTaper` or the real composer fit-math changes — the exact regressions these tests were written to prevent — **they still pass.** The riskiest logic in the app (ViewModel update semantics, PDF page-fit, FWD-reference math in the carousel) has *illusory* coverage. Fix: make the logic under test callable (extract pure functions the UI/VM delegates to — you already did this successfully for `deriveTaperDiameters`, `buildFooterEndColumns`, `blockingExportError`), then point the existing tests at the real functions.

### Dangerously untested (silent error → wrong physical part)

1. **The resolved-component / auto-body pipeline** (`resolveComponents`, `deriveAutoBodies`, `subtractBodiesAgainstNonBodies`, `normalizeBodies`) — zero tests. This decides what geometry *appears on the drawing* between explicit components, has non-obvious diameter-inheritance rules, and has already produced one shipped bug (auto-body length=1, fixed 2026-06-23). This is the single highest-value test target in the repo.
2. **Coupler bolt slots** — the entire feature on this branch has zero tests (model validity, FWD authoring math in dialog/card/`physFromAuthored`, exclusion invariants) and per its own proposal has never run on a device.
3. **All actual drawing code** — no golden/pixel tests anywhere. `composeShaftPdf` and friends can be executed under Robolectric and rasterized via `PdfRenderer` (the app already does exactly this for previews). Five or six golden-PDF snapshot tests (body-only; two-taper+liners reference shaft; excluded threads both ends; long-body break; keyway; coupler slots) would convert "I eyeballed it on my phone" into regression protection for the thing you sell — the paper.
4. **`ShaftDocCodec` round-trip with a fully-populated document** (every component type incl. slots, keyways, labels, runout config, all metadata). Today codec coverage is indirect, via bundled-sample tests. One property-style round-trip test is cheap insurance for the file format.
5. **Autosave/restore, undo/redo, `syncExcludedThreadPositions` interplay** — the stateful choreography where past regressions clustered.
6. Instrumented tests exist (4 real ones) but are blocked by the (justified) gradle guard and not run in CI — effectively decorative.

### CI

No workflow runs any test. `merge-on-green.yml` requires "checks" that don't include a test job, and `distribute.yml` happily ships any compiling commit to Firebase. **One 15-line workflow addition (`./gradlew testDebugUnitTest` on PR + push) is the highest ROI half-day in this table.**

---

## 1.4 Error handling & data integrity

Behavior observed by reading every failure path:

| Scenario | Current behavior | Assessment |
|---|---|---|
| Corrupt/truncated `.shaft` opened from list | **App crash** (uncaught `SerializationException` in coroutine) — B4 | Must fix; with B5 the app can corrupt its own file and then crash on every open of it |
| Process death mid-save | Truncated file, no previous copy — B5 | Must fix (atomic write + `.bak`) |
| File from a newer app version | Decodes with unknown fields silently dropped; save destroys them — B6 | Must fix before any second user exists |
| Corrupt autosave draft | `AutosaveManager.restore` returns null — draft silently lost | Acceptable, but worth a one-line "couldn't restore draft" notice |
| Autosave write failure | Silently swallowed (`catch (_: Exception)`) | Acceptable for a draft; log it |
| PDF composer throws mid-export | **Excellent**: valid error-page PDF written instead of a truncated file (`PdfExportRoute`) | Model behavior — this is the standard the file-save path should meet |
| Preview render failure | `runCatching` + error message, temp files cleaned | Good |
| Bad numeric input | Filtered at keystroke, revert-on-invalid commit, no-op commit guard (2026-06-23 fix) | Good; contract-documented |
| NaN/∞/absurd values | Rejected at parse edge; VM clamps negatives | Fine, though VALIDATION_RULES.md claims "validation only in ViewModel" — reality is split between parsers, dialogs, and VM; update the doc |
| Reinstall / different-signature install | App-private files wiped — **this already happened** ("the lost shafts") | Auto Backup is nominally on (`allowBackup=true`) but the rules files are unedited templates, not referenced in the manifest, and restore was never verified. Backup plan Tiers 1–2 remain unimplemented since 2026-05-27 |
| No crash reporting / telemetry | Crashes in the field are invisible | Fine for personal use; blocking for a product — you will not know why a customer's app died |

**The pattern:** render-side failure handling is thoughtful; *storage-side* failure handling is the weakest part of the codebase, and it guards the only irreplaceable thing — the user's documents.

---

## 1.5 The PDF generation approach specifically

**Would it hold up to a wider variety of shafts than you've tested?** Mostly yes for geometry, no for text, with several sharp edges:

**Robust:** page-fit math (including excluded-thread overhang — regression-tested, if only by mirror), fit-to-band rail shrinking with font-size fallback, greedy label row assignment, deterministic tiering, white-background/theme-safety guardrails, the error-page fallback, explicit `requireFinite` guards on taper coordinates, template mode, line-thickness scaling applied consistently.

**Will break on real-world variety:**

1. **Long free text** — footer columns at fixed x-fractions (B8): "Northwest Fisheries Consolidated LLC" as a customer will overwrite the FWD taper block. Needs `measureText` + ellipsize/wrap. Same for runout/wear single-line headers.
2. **Many liners/dimensions** — the rail-label collision system gives up after 3 bumps and draws anyway (`MAX_BUMPS = 3`, `PdfDimensionRenderer`) — a 4-liner shaft with tight offsets can still print overlapping numbers, silently.
3. **Extreme aspect ratios** — a 30-ft shaft with a 4" OD on one landscape Letter page: everything fits (the math holds) but at ~1.4 pt/inch the dimensions become decorative. There is no "this drawing is too dense to read" warning, and multi-page is a stated non-goal. At minimum, warn on export when `ptPerMm` falls below a readability floor.
4. **Cross-document consistency** — B10, B12, B13, and the divergent break glyphs (§1.1). A surveyor comparing the schematic against the runout sheet sees two "OAL" values and two break symbols. These are exactly the details shop customers judge.
5. **No golden tests** (§1.3.3) — every one of the ~40 PDF fix-commits in the log was verified by eye. That does not scale past one user.

**The three-composer structure** (schematic / runout / wear) is the right *product* shape — the document set is your differentiator — but they share code by copy-paste. `drawCouplerBoltSlots` (shared, `internal`, reused by all three) proves the right pattern; the rest of the profile drawing should follow it (§1.1).

---

## 1.6 Compose Multiplatform — actual cost, actual benefit

**What ports today with near-zero work (~2,500 lines):** `model/`, `geom/`, `doc/` (kotlinx-serialization is KMP), parsing/formatting utils, validation, tier assignment, the resolved-component pipeline. This is the valuable IP and it is *already* almost pure — the only blockers are the `ui.order` import in the model (§1.1) and `java.util.UUID`/`String.format` (trivial `expect/actual`).

**What does not port:** all four drawing paths (`android.graphics.Canvas`, `Paint`, `Path`, `PdfDocument`, `PdfRenderer`), DataStore usage as structured (DataStore itself is KMP-ready now, minor), SAF file flows, `FileProvider`/intents. The PDF story is the hard part: **there is no mature KMP PDF-generation library.** Realistic approach: emit your own display-list (the same `DrawPrimitive` layer from §1.1) and back it with per-platform sinks — Android `PdfDocument`, JVM PDFBox/OpenPDF for desktop, iOS `UIGraphicsPDFRenderer`. That is genuinely feasible *because* your PDFs are lines, arcs, and text — no images, no fonts beyond sans-serif — but it is real work.

**Actual costs (assuming rendering is unified first — which you should do anyway):**

- **Desktop (Windows/macOS via CMP):** 4–8 weeks part-time. UI reflow is modest (your screens are phone-portrait-shaped; desktop wants a two-pane layout), file I/O is easier than Android, PDF via PDFBox is well-trodden. **Benefit: real.** Shops have a Windows PC at the front desk; the person pricing the job isn't holding a phone. Desktop also unlocks "print directly."
- **iOS:** 3–5 months part-time, *plus* $99/yr, Mac hardware, App Store review, and a second binary to keep alive. CMP-iOS is production-usable now but you'd be debugging Compose-on-iOS quirks alone. **Benefit: speculative** — you have zero evidence of iOS demand from your buyer set. Do not do this on spec.
- **Web:** CMP-Wasm is the least mature target; skip.

**Honest recommendation:** Do not adopt CMP now. Do the §1.1 unification and the model-purity fixes *now* (they pay for themselves inside Android), which keeps the CMP option open at ~20% of the cost of exercising it later. Revisit desktop only when a paying shop asks "can I do this at my desk?" — and expect that to be the first platform request, not iOS.

---

## 1.7 Security & data handling — if this ever touches a shop's proprietary schematics

Current posture is reasonable *for a personal tool* and under-designed for customer data:

1. **Committed debug keystore signs release builds.** The keystore (password "android") is public in the repo and in every clone. Anyone can build an APK that installs as an *update* over a customer's app — inheriting its data. Before any distribution: generate a real upload key, keep it out of git, rotate the applicationId's trust by releasing under the new key from day one (there is no installed base to migrate — this is free *today* and painful later).
2. **Backup/export paths are undesigned data flows.** With `allowBackup=true` and template rules, customer job data (customer names, vessels, job numbers, geometry) is eligible for Google-cloud device backup by OS default — probably fine, but it should be a decision, not an accident. Document it; consider excluding DataStore debug flags.
3. **Feedback email attaches `.shaft` files** (user-initiated, visible) — acceptable, but the email body auto-includes job metadata; make the attachment step explicit in the UI copy.
4. **No encryption at rest** — app-private storage is adequate for Android's threat model; shops won't demand more on-device. If cloud sync ever exists, *that* is where encryption/tenancy design happens — another reason not to improvise sync.
5. **Verbose logging can emit geometry and filenames to logcat** — properly gated behind dev options and reset-on-startup (good fix, 2026-06-23). Keep PDF/IO categories from ever logging metadata values, only sizes.
6. **No privacy policy / data-safety declaration** — required for Play distribution; trivial for an app with zero network calls (which, today, is a genuine selling point: *"your drawings never leave your phone"* — lean on it).
7. **No telemetry at all** — a privacy plus and an operations minus; if you sell it, add opt-in crash reporting only (e.g., a crash log the user can choose to email), consistent with the no-network positioning.

---

# PART 2 — PRODUCTIZATION PLAN

## 2.1 Honest commercial viability

**What this actually is:** not a CAD competitor — a **shaft-job documentation system**. The unit of value is the printed pack: dimensioned schematic + runout measurement sheet + wear/inspection record, produced in minutes at the shaft, by the machinist, without CAD. Having read the whole codebase: the schematic editor is the *input method*; the three PDFs are the *product*. The runout and wear documents are the differentiator — nothing mainstream produces those as a set, and they're the pages that go in the job folder the customer, the surveyor, and the insurer see.

**Market sizing, honestly.** Marine machine shops, prop shops, and shipyard machine divisions doing propshaft work: order of hundreds of shops in North America (concentrated where you are — PNW fishing fleet, Gulf, Great Lakes, Northeast), each with 1–10 people who'd touch this. Even heroic penetration (100 shops × ~$300/yr) is **~$30K ARR** — meaningful side income, not a company. Adjacent expansion (hydro, industrial rotating equipment, pump/motor shops) could double or triple that, but each adjacency dilutes the marine-specific conventions (SET/LET, AFT/FWD, muff couplers) that make this credible. Set expectations accordingly: this is a profitable niche tool with a moat made of domain fluency, not a venture path.

**Why you specifically can win it:** you are the ICP; the domain decisions in this codebase (OAL is sacred and SET-to-SET; threads are drawn but excluded; runout stations inset 1" from transitions; keyway spooning) are things no generalist developer would get right and no shop will trust from someone who hasn't held an indicator on a shaft. Your distribution channel is your existing reputation — that's the entire go-to-market.

**The main risks:** (1) shops are happy with paper and Excel — the bar isn't "better than CAD," it's "better than the clipboard," and the clipboard is free and never crashes; (2) single-maintainer risk — a shop that adopts this for job records needs it to exist in five years (this is why file-format openness and PDF output matter: even if the app dies, the records don't); (3) Android-only — check what phones your actual first five buyers carry before believing iOS doesn't matter.

**Verdict:** worth productizing as a deliberate side business with a hard gate: get *one shop that isn't yours* to pay anything at all before investing past the M1 list below.

## 2.2 Minimum sellable feature set vs. nice-to-have

**Blockers (a stranger's shop, paying money, no hand-holding):**

1. **Data safety** — atomic saves, corrupt-file recovery instead of crash, ZIP backup/restore, verified reinstall survival (Part 1, rank 1). A shop's job records vanishing once ends the relationship and the referral chain.
2. **Release integrity** — real signing key, minified release build, Play internal track or managed APK distribution, crash visibility (rank 6 + opt-in crash email).
3. **Shop identity on the paper** — company name/logo/phone on the title block and all three documents. Shops will not hand a customer paperwork branded as nothing. This is small (a settings section + footer slot) and disproportionately important.
4. **The correctness list** — B1–B13, because the pitch is "trustworthy paperwork." One upside-down taper in a demo to a machinist and the product is dead in that shop.
5. **Text robustness on PDFs** (B8) — real shops have long names.
6. **`.shaft` file sharing** — a "Share" action (intent with the file) so two phones in one shop can pass a job. This substitutes for sync indefinitely and costs an afternoon (SAF save route already exists, currently dead — resurrect it as Share/Export).
7. **Licensing/paywall** — simplest viable: Play subscription or a license key you issue manually for direct sales. Manual keys are fine at n<20 shops.

**Explicitly NOT needed to sell (resist these):** cloud sync, accounts, multi-user editing, iOS/desktop, DXF, presets library, landscape phone editor, undo/redo beyond deletes, fiberglass bodies/liner shoulders (finish them for *your* work, but they don't gate a sale), achievements (remove).

## 2.3 Pricing & packaging — recommendation, not a list

**Recommendation: shop-level license (site license per physical shop), $299/year, unlimited devices at that shop, 60-day free trial, sold by you in person for the first cohort.** Reasoning against the alternatives:

- **Per-seat SaaS** fights the culture: machinists share devices, owners buy tools per-shop, and metering seats in a 4-person shop creates friction worth more than the revenue delta. Per-shop matches how they buy grinders and software alike.
- **One-time license** matches the culture *better* ("I own my tools") but starves maintenance — and this product has real ongoing costs (OS churn, file-format stewardship, your time). Split the difference for the earliest adopters: **first 5–10 shops get a founder deal — $499 one-time, perpetual, includes updates** — which respects the tool-buying mentality, funds the work, converts your relationships into commitments, and creates references. Everyone after: $299/yr.
- Anchor the price against the alternative that owners actually compare: not CAD ($2–5K/yr) but *shop time* — one hour of machinist time saved per job pays for a month. Say exactly that sentence when selling.
- Keep the free tier as: full editor, exports watermarked "EVALUATION" after trial. Never cripple saving — crippling a shop's own data would poison word-of-mouth.

## 2.4 The first five buyers and how to reach them

Given that you work in this industry (and the reference drawings in the repo are from real jobs — Aleutian Spray, Siberian Sea — i.e., PNW fishing fleet):

1. **Your own shop** — not a sale, but the reference install: every job you run produces the demo pack, and coworkers become the usability lab. Get 2–3 coworkers using it on real jobs this month.
2. **The prop shop you already trade work with** — prop shops see the most shaft volume and produce the most repetitive paperwork; the runout sheet alone is their pitch. This is buyer #1 because trust is pre-established.
3. **Two independent marine machine shops in your metro** you know by name from the trade — the owner-operator kind, 3–8 machinists, still hand-drawing shaft sketches. Walk in with a printed pack from a real (anonymized) job and the phone.
4. **A shipyard machine-shop foreman** you've worked alongside — yards are slower buyers (procurement) but one yard adoption is worth ten small shops for credibility. Plant the seed now, expect the sale in 6–12 months.
5. **One shop outside your region** (Gulf or Alaska) reached through a supplier/rep or trade contact — deliberately, as the test of whether this sells *without* your face, which tells you if it's a product or a favor.

**How to reach them — the honest version:** you don't need marketing, you need the artifact. A three-page printed pack from a real job, on the counter, and the sentence "I drew this on my phone at the shaft in ten minutes." Ask each viewer two questions and write down the answers verbatim: *"What would stop you from using this on Monday?"* and *"What does your current shaft paperwork look like?"* Their answers are your real backlog — expect "logo on the drawing," "can my other guy open it," and at least one document type you haven't built. Do not build anything else until you've heard the same request twice.

## 2.5 The next three actions this month, by leverage

1. **Make the data trustworthy, then put it in three coworkers' hands.** (~3 days) Fix B4/B5 (atomic writes, corrupt-file dialog, `.bak`), implement ZIP backup/restore from your own BACKUP_PLAN.md, fix the coupler-slot branch bugs (B1/B2/B7) and actually run the branch on a device before merging. Then install it for 2–3 people at your shop and watch them use it on live jobs. *Leverage: converts the product from "my tool" to "our tool" and surfaces the usability truths you're blind to — while removing the one failure mode (lost job data) that would end adoption permanently.*

2. **Run the counter test with the printed pack at two outside shops.** (~2 half-days, zero code) Real job, three documents, the two questions above, price mentioned out loud ($299/yr or $499 founder-perpetual) so you observe the flinch or the nod. *Leverage: this is the cheapest possible test of the only genuinely uncertain variable — willingness to pay. Everything in Part 1 is fixable with time; a market that shrugs is not.*

3. **Close the correctness-and-CI loop.** (~2 days) Fix B3 (taper-rate inversion) with direction-aware derivation and corrected tests; fix B8 (footer text measurement); add the CI test job and make merge-on-green gate on it; add the first two golden-PDF snapshot tests (reference two-taper shaft + excluded-threads shaft). *Leverage: the pitch is "trustworthy paperwork" — these are the items that protect the demo from the one bug that would cost a machinist's trust, and the CI gate protects every fix after it.*

Deliberately **not** this month: rendering unification (do it next month, before liner shoulders), Play Store listing (after the counter test says yes), desktop/CMP (after a shop asks), cloud anything (after ten shops).

---

## Appendix A — Strengths worth preserving (so a refactor doesn't lose them)

- mm-only canonical model; unit conversion strictly at the UI edge
- `geom/` package: Double-precision, coordinate-anchored, deterministic
- OAL semantics resolved with domain authority and documented (OAL_THREAD_BUG_ANALYSIS.md is a model incident report)
- Versioned envelope + `@JsonNames` migration posture; legacy `.json` support; sample seeding that never overwrites user data
- Full-snapshot delete undo/redo; commit-on-blur discipline with the no-change guard
- PDF export error-page fallback; theme-safe white-background guardrail
- Dialog/card parity contract, enforced in docs *and* in-code comments after a real regression
- 356 green tests and the habit of adding tests with fixes
- Connected-test guard protecting device data; verbose logging architecture with startup reset

## Appendix B — Documentation debt quick list

- `docs/` vs in-source `docs/` contradictions: ordering (newest-on-top vs spatial), PDF orientation, shared-renderer claims, thread defaults (5" vs 6")
- `CONTRIBUTING.md` describes an architecture that no longer exists ("PDF: no special work if you used the shared renderer")
- `README.md`: stale FAB references, stale dependency versions, roadmap duplicating/contradicting ROADMAP.md
- `DATA_MODEL.md`: ChatGPT paste artifacts, orphaned "Liner Contract" section
- ARCHITECTURE.md still lists the resolved-component pipeline as "planned (not yet implemented)" — it shipped
- VALIDATION_RULES.md ("validation only in ViewModel") vs reality (parse-edge + dialogs + VM); VALIDATION_APPENDIX overlap matrix contradicts §5.1 of the same rules
- ROADMAP "non-goals: cloud sync (never)" vs v0.7 "optional cloud save" in the same file
