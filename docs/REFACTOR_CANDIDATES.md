# Refactor Candidates — catalog only

Created 2026-08-05 (day-run polish, on-device request: "If there are places we can
optimize with a refactor, I am open to it… if you think there are good options but are
risky to try, document them instead into an md file for future review and direction.")

**Nothing in this file has been applied.** Each entry is a behavior-preserving refactor
that looked worthwhile but carries enough risk (multi-surface touch, drawing fidelity,
product ambiguity) that it wants an explicit go-ahead and its own review cycle. Entries
are ordered by recommendation strength.

---

## 1. Unify the PDF→bitmap raster helpers

**What:** Two near-identical "compose a page → temp PDF → `PdfRenderer` raster at 2× →
delete temp file" implementations exist: `renderPdfPreviewBitmap` (private,
`ui/screen/PdfPreviewScreen.kt`) and `renderPdfPageBitmap` (internal,
`ui/screen/RunoutRoute.kt`, also consumed by `OutputRoute`). Extract one helper (e.g.
`util/PdfRaster.kt`) taking a `composePage: (PdfDocument.Page) -> Unit` lambda —
the same shape `util/PdfSafExport.writeShaftPdfToUri` already uses.

**Why:** One raster pipeline to harden (error page, temp-file hygiene, render scale)
instead of two drifting copies.

**Risk:** LOW-MEDIUM — pure plumbing, but it touches the preview paths of three tabs;
subtle differences (bitmap config, background fill, scale) must be diffed carefully
before merging them.

**Recommendation:** Do it in a quiet window with on-device preview checks on all three
surfaces. Good first candidate.

## 2. Shared spec → `ProfileFeatureSpan` builder

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

## 3. Hoist the compressed-body draw loop into `pdf/BreakSymbol.kt`

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

## 4. Options-sheet block extraction

**What:** The line-thickness slider block and the Shade-in-PDF checkbox group each
exist ~3×: `PdfOptionsSheet` (PdfPreviewScreen), `RunoutWearOptionsSheet`
(RunoutRoute), and the Settings → PDF Export page. Extract `LineThicknessSliderRow` and
`ShadeInPdfChecks` composables (same posture as the shared `ShaftHeightSlider` /
`LinerCompressionControl`).

**Why:** The 78%-height/scroll fix and any future option addition currently multiply
across surfaces.

**Risk:** LOW-MEDIUM — pure UI extraction, but the three surfaces have slightly
different typography/spacing and the Settings page adds a text field; a shared
composable needs those knobs or the surfaces converge visually (which may be fine —
product call on whether they SHOULD look identical).

**Recommendation:** Fine to do whenever; pairs naturally with any next options-sheet
change.

## 5. Long-body bubble count — unimplemented product seam (decision needed)

**What:** `RunoutConfig.BODY_SHORT_THRESHOLD_MM` (914 mm) and
`RunoutConfig.BODY_LONG_COUNT` are defined and documented ("Default for long bodies —
user bumps this up as needed") but **never read** — the default-count logic uses
`BODY_DEFAULT_COUNT` for every body regardless of length. Either implement the
length-based bump (long bodies default to more stations) or delete the two constants.

**Why:** As written the KDoc promises behavior the app doesn't have.

**Risk:** Implementing it changes default bubble counts on existing jobs' sheets (only
where no per-component override exists). Deleting is zero-risk.

**Recommendation:** Product decision — implement or delete. (Both constants currently
kept; nothing removed.)

## 5b. OAL-spacing pref: build the control or drop the setter (decision needed)

**What:** `PdfPrefs.oalSpacingFactor` has a live READ side (persisted, consumed by
`ShaftPdfComposer` for the gap above the OAL rail) but its ViewModel setter
(`setPdfOalSpacingFactor`, `ShaftViewModelSettings.kt`) has **no UI caller** — the
changelog records it as scaffolding "for future UI callers" that were never built. The
pref is therefore permanently at its default (2.5) for every user.

**Recommendation:** Product decision — add the Settings control (a small slider,
1.0–6.0) or remove the setter + persistence and hard-code the factor. (Setter kept for
now.)

## 5c. Spec-level warnings: pick a UI surface (decision needed)

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
