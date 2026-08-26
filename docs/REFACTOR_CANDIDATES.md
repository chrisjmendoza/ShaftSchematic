# Refactor Candidates — catalog only

Created 2026-08-05 (day-run polish, on-device request: "If there are places we can
optimize with a refactor, I am open to it… if you think there are good options but are
risky to try, document them instead into an md file for future review and direction.")

Each entry is a behavior-preserving refactor that looked worthwhile but carries enough
risk (multi-surface touch, drawing fidelity, product ambiguity) that it wants an explicit
go-ahead and its own review cycle. Entries are ordered by recommendation strength; ones
that have since been applied are marked **DONE** and kept for the record.

---

## 1. Unify the PDF→bitmap raster helpers — **DONE**

**What:** Near-identical "compose a page → temp PDF → `PdfRenderer` raster at 2× →
delete temp file" implementations existed: `renderPdfPreviewBitmap` (private,
`ui/screen/PdfPreviewScreen.kt`), `renderPdfPageBitmap` (internal,
`ui/screen/RunoutRoute.kt`, also consumed by `OutputRoute`), and — found while doing the
work — `renderWearBitmap` (`WearRoute.kt`) and `renderUndercutBitmap`
(`UndercutRoute.kt`): **four** copies, not two.

**Applied:** one `util/PdfRaster.renderPdfPageBitmap(context, composePage)` — the
`composePage: (PdfDocument.Page) -> Unit` shape `util/PdfSafExport.writeShaftPdfToUri`
already uses — now serves all five preview call sites (schematic, runout, wear, undercut,
consolidated output); the four local helpers are deleted. The copies differed only in
their temp-file prefix and in the schematic one taking compose arguments instead of a
lambda; bitmap config (`ARGB_8888`), white `eraseColor`, 2× scale (now the named
`PDF_PREVIEW_RENDER_SCALE`), 792 × 612 page (now `PDF_PAGE_WIDTH_PT`/`PDF_PAGE_HEIGHT_PT`),
`runCatching{}.getOrNull()` failure posture, and close/delete order were identical.

**Left open:** the helper still leaks its temp file if `composePage` throws — the delete
sits in the raster stage's `finally`, which the throw skips. Every copy behaved that way,
so the unification kept it; harden it (one `try/finally` around both stages) as its own
change if cache hygiene ever matters.

## 2. Shared spec → `ProfileFeatureSpan` builder — **DONE 2026-08-25**

Landed as `geom/ProfileFeatureSpans.kt`: `profileFeatureSpans(spec, linerFloorPt,
threadFloorPt, linerMinFracOfTrue)` serves the schematic (lean `SCHEMATIC_MIN_*` floors),
the runout/consolidated sheet (writable `PROFILE_MIN_*` floors), and the estimator. The
anticipated body-list parameter proved unnecessary — tapers/liners/threads read identically
from a stored spec and a `withResolvedBodies` copy, and the keyway pins must come from
STORED bodies anyway (a resolved body carries no keyway fields), so every caller passes the
stored spec. The keyway-window pin helpers moved into the same file. Original entry:


**What:** The feature-span list (tapers at `PROFILE_MIN_TAPER_PT`, liners at
`PROFILE_MIN_LINER_PT` + frac, threads at `PROFILE_MIN_THREAD_PT`, keyway bodies
pinned) is built in three places: `ShaftPdfComposer`, `RunoutPdfComposer`, and
`estimatedLinerKeptFracOfTrue` (`ui/screen/ShaftHeightSlider.kt`). Extract one builder
in `geom/` (geom already depends on `model` — see `OalComputations.kt`), parameterized
by the liner frac and the body source (spec bodies vs resolved-for-pdf bodies).

**Why:** The estimator's accuracy depends on mirroring the composers; today that mirror
is enforced by convention only. One builder makes drift impossible.

**Risk:** MEDIUM — the two composers feed slightly different body lists
(`bodiesForPdf` vs `docSpec.bodies`), so the builder needs a body-list parameter, and a
bad unification would silently change which spans pin.

**Recommendation:** Do it the next time the feature list changes for any other reason;
not urgent on its own.

## 3. Hoist the compressed-body draw loop — **DONE 2026-08-25**

Landed as `pdf/BodyRunDraw.kt`'s `drawBodyRunsWithBreaks` — ONE pass for the two
blend-aware composers (schematic + runout/consolidated), and the constants now live once in
`pdf/BreakSymbol.kt`. The wear/undercut pair deliberately did NOT merge into it: they draw
at one flat pt/mm with no compression solve, no blends, and no protected keyway windows, so
they share their own simpler pass (`pdf/SimpleShaftProfile.kt`) instead — the reasoning is
in both files' KDoc. The mechanical diff the review demanded paid for itself: it caught the
runout copy painting a shaded body's fill OVER part of the break's S-curve, now unified on
the schematic's order. Original entry:


**What:** All four composers repeat the same body loop: plain rectangle under the
trigger, else stubs + `breakPairLayout` + two `drawBreakEdge` calls — and each defines
its own `ZIGZAG_GAP_MAX_PT = 20f` copy. One shared internal
`drawBodyRunWithBreak(canvas, x0, x1, r, cy, paints, trigger, truePtPerMm)` would
collapse ~30 lines × 4 into one implementation and one constant.

**Why:** The S-break overlap fix had to be applied at four sites; the next glyph change
will too. This is the draw-both-sites invariant begging to become draw-one-site.

**Risk:** MEDIUM — the four sites differ in real ways (schematic fills + translate,
runout uses `geomRect` clamps, wear/undercut trigger on length only, not
foreshortening). A careless merge changes drawings. Needs a same-math SVG artifact pass
for review, per the drawing-change convention.

**Recommendation:** Worth doing, but only with the artifact review; don't fold it into
an unrelated wave.

## 4. Options-sheet block extraction — **DONE**

**What:** The line-thickness slider block and the Shade-in-PDF checkbox group each
existed ~3×: `PdfOptionsSheet` (PdfPreviewScreen), `RunoutWearOptionsSheet`
(RunoutRoute), and the Settings → PDF Export page.

**Applied:** the slider was already shared as `LineThicknessSlider`; the checkbox group is
now `ShadeInPdfChecks` beside it in `ui/screen/ShaftHeightSlider.kt` (heading + Bodies /
Tapers / Liners rows + the defaulted `linerShadeLocked` display-only lock, so the two
sheets that don't lock are untouched by it). Both PDF options sheets use it; setters still
go through `vm.setPdfShaded{Bodies,Tapers,Liners}`.

**Convergence call:** the two sheets' rows were byte-identical, so they converged. **The
Settings → PDF Export page stays bespoke** — its rows live in a `spacedBy(12.dp)` column
under a `padding(start = 4.dp, top = 4.dp)` heading, so dropping the sheets' block in
would have tightened its row gaps from 12 dp to 0 and changed the heading offset: a visual
change, not a behavior-preserving extraction. Its wording, prefs, and setters are
identical to the shared block, and a comment at the site records why it is separate.

## 5. Long-body bubble count — **RESOLVED: deleted**

**What:** `RunoutConfig.BODY_SHORT_THRESHOLD_MM` (914 mm) and
`RunoutConfig.BODY_LONG_COUNT` were defined and documented ("Default for long bodies —
user bumps this up as needed") but **never read** — the default-count logic uses
`BODY_DEFAULT_COUNT` for every body regardless of length.

**Decision:** default bubble counts stay **uniform**; the user raises the count per
component (`RunoutConfig.componentOverrides`) when a run wants extra readings. Both
constants are deleted and `BODY_DEFAULT_COUNT`'s KDoc now says so, instead of promising a
length-based bump the app never had. Zero readers existed in `app/src/main` or
`app/src/test`, so nothing else moved.

## 5b. OAL-spacing pref — RESOLVED (removed)

**Was:** `PdfPrefs.oalSpacingFactor` had a live READ side but no UI caller, so it sat
permanently at its 2.5 default for every user. The on-device direction "the OAL is the
topmost measurement, but it doesn't need such a large gap — one regular tier" made the
pref obsolete: the OAL gap is now a fixed rule (one tier pitch; the dimension-label
planner's lift is the only widener), so the field, DataStore key/flow, ViewModel
collector, and setter were all removed. Old installs keep a harmless orphaned
`pdf_oal_spacing_factor` key in DataStore.

## 5c. Height-slider track vs pinned-span ceiling (known drift)

**What:** The "Shaft height" slider's track and readout come from
`drawnShaftHeightPt`, which knows the sizing curve and the 1.5" cap but NOT the
pinned spans (keyway-bearing bodies — tapers no longer pin; their frac floors don't
touch the height). On a keyway-body-heavy shaft the composer's `solveMaxProfileScale`
stops the real height below what the slider offers. The
`estimatedLinerKeptFracOfTrue` machinery already runs the real solve in the UI — the
slider could use the same estimate for its track maximum, showing the actual
reachable ceiling per shaft.

**Risk:** LOW-MEDIUM — display-only, but the track max becoming spec-dependent needs
care around empty/degenerate specs.

**Recommendation:** Do it with the next slider-UX pass; until then the slider is
honest at the "~" level (the drawn height simply stops growing past the pin ceiling).

## 5d. Spec-level warnings: pick a UI surface (decision needed)

**What:** `specWarningMessages` (`ui/util/ComponentWarnings.kt`) is pure, unit-tested,
and deliberately unwired — its siblings feed the carousel cards, but the spec-level
aggregate has no surface. `TODO.md` already tracks the open UX decision.

**Recommendation:** Not a removal candidate — decide where spec-level warnings show
(editor banner? export gate advisory?) and wire it, or consciously retire the seam.

## 6. Retire `computeDetailPtPerMm` by migrating its tests

**What:** `computeDetailPtPerMm` (`pdf/ShaftPdfComposer.kt`) is production-dead —
kept only as the reference solve for `BodyOnlyScalingTest`, `TaperPdfScalingTest`,
`PdfLayoutBoundsTest`. Those tests could assert against the sizing-curve solve
(`defaultVisualScale` + `exaggeratedProfileScale` + `solveMaxProfileScale`) instead,
letting the function and `BODY_ONLY_TARGET_HEIGHT_PT` go.

**Why:** The tests currently pin a solve the composer no longer uses, which weakens
what they prove.

**Risk:** MEDIUM — rewriting scaling tests against the live solve must be done
thoughtfully or the tests become tautologies.

**Recommendation:** Low priority; fold into the next test-suite maintenance pass.

## 7. Not recommended now (looked at, declined)

- **`SettingsStore` flat API + per-field restore collectors** (`ShaftViewModel` init):
  ~90 members and one `viewModelScope.launch` per pref could be table-driven. High
  churn across the most-wired file in the app for mostly aesthetic payoff. Skip.
- **`VerboseLog.w`** is unused but stays — it is one quarter of a symmetric `d/i/w/e`
  logging facade; deleting it saves nothing and breaks the shape.
- **Preview canvas vs PDF composer split** (`ShaftRenderer` vs `pdf/`): intentional
  architecture, documented in `docs/archive/AUDIT.md` §4.4. Not a candidate.
