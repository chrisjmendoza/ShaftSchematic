# ShaftSchematic — Codebase Audit
**Date:** 2026-05-27  
**Auditor:** Claude (external consultant pass)  
**Branch audited:** `fix/pdf-dimension-measurements`  
**App version:** 1.1.1 (v0.4.x series)

---

## Executive Summary

ShaftSchematic is a well-structured Android app with a clear domain model and good separation of concerns. The MVVM pipeline (`ShaftSpec → ViewModel → ShaftLayout → ShaftRenderer → ShaftDrawing`) is sound and consistently applied. The core geometry and PDF export machinery are solid.

The primary issues found are: a significant accumulation of dead code in `ShaftPdfComposer.kt` that the `@Suppress("unused")` annotation is hiding, material inaccuracies in `BRIEFING.md` that will mislead future developers, missing test coverage for several non-trivial PDF adapter functions, and one visible user-facing rendering defect (component label collision in PDFs).

---

## 1. What Is Working Well

**Architecture is clean and consistently applied.** The `ShaftSpec` → `OalWindow` → `SetPositions` → `DimSpan` → `RailPlanner` → `PdfDimensionRenderer` chain is well-defined. Each layer has a single responsibility. The measurement-space vs. physical-space distinction is handled correctly everywhere it matters.

**The OAL/SET fix (this branch) is correct.** `computeSetPositionsInMeasureSpace` now derives SET positions from actual taper geometry. The fix is domain-accurate: OAL is always SET-to-SET in marine machining. The 4 new tests in `OalComputationsTest` cover the important cases (excluded, included, no-taper, overlapping). All 49 tests pass.

**Serialization and back-compat are handled well.** `ShaftDocCodec` uses a versioned envelope with a legacy fallback path. `Threads.normalized()` handles the pitch ↔ TPI dual-representation correctly. The `@JsonNames` alias on `excludeFromOAL` handles the three known serialized forms. Field-level migration is present and tested (`ThreadExcludeFromOalJsonTest`, `ShaftDocEnvelopeExcludeFromOalTest`).

**The `StartOverlapValidation` scope is intentionally conservative and correct.** Only Thread↔Thread and Liner↔Liner are blocked; all other overlaps are allowed per the marine machining domain rule. The design decision is sound and matches `VALIDATION_RULES.md` section 5.

**The `DeterministicTierAssigner` is well-tested.** Tier/rail assignment is the most complex algorithmic piece and has dedicated tests.

**Component label rendering on the PDF** is geometrically centered per component span and clipped to the geometry rect — the individual label placement logic is correct.

---

## 2. HIGH — Visible User-Facing Defect

### 2.1 Component labels collide when components share the same X region

**File:** [ShaftPdfComposer.kt:368](app/src/main/java/com/android/shaftschematic/pdf/ShaftPdfComposer.kt#L368)

All component labels are drawn at the **same fixed Y** (`yBottomOfShaft + COMPONENT_LABEL_OFFSET_PT`). There is no horizontal collision detection. When two components occupy overlapping X ranges — the most common case being an AFT thread at x=0 followed immediately by a taper that also starts near x=0 — their labels are drawn on top of each other and become unreadable.

The constants `LABEL_STACK_STEP_PT` and `LABEL_LEADER_PT` are defined (lines 422–423) and a full collision-avoidance algorithm exists in the dead `drawDimWithExtensionsAvoidingOverlap()` function, but neither is wired into `drawComponentLabelsPdf()`. The collision logic was built but not connected.

**Impact:** Garbled component labels on any PDF where an end thread and its adjacent taper start at the same position — which is the normal configuration for propeller shaft ends.

**Fix direction:** Track occupied X intervals per label row. When a new label would overlap an existing one, either shift it to a second row or nudge the text horizontally. The existing constants and algorithm provide a starting point.

---

## 3. MEDIUM — Dead Code Accumulation

### 3.1 Multiple dead private functions in `ShaftPdfComposer.kt`

The `@file:Suppress("MemberVisibilityCanBePrivate", "unused")` annotation at the top of the file silences all dead-code warnings. As a result, a significant volume of unreachable code has accumulated:

| Function | Lines | Notes |
|---|---|---|
| `drawLinerDimensionsPdf()` | 598–641 | No callers. Duplicates the active dimensions path but with a double-offset bug (`pageX(dimMm + measureStartMm)` where `pageX` already accounts for `measureStartMm`). |
| `drawDimensionsLikePreview()` | 874–935 | No callers. Was the original preview-style dimension approach before the rail planner. |
| `drawDimWithExtensionsAvoidingOverlap()` | 937–995 | Only called from the dead `drawDimensionsLikePreview`. |
| `drawZigZagBreak()` | 729–750 | No callers. Replaced by `drawSCurveBreak()` but never deleted. |
| `pickAftFwdTapers()` | 1142–1153 | No callers. Superseded by `selectFooterTapers()`. |
| `fmtDia()` | 1177–1181 | No callers. |
| `fmtThread()` | 1190–1195 | No callers. |
| `fmtTaper()` | 1197–1201 | No callers. |
| `computePdfPtPerMmFitAxes()` | 500–510 | `internal` but not called from within the file. May be test-only; verify before deleting. |
| `Interval` data class | 872 | Only referenced in `drawDimensionsLikePreview`. |

**Impact:** ~250 lines of dead code increasing cognitive load, hiding intent, and (in the case of `drawLinerDimensionsPdf`) containing a latent double-offset bug that would produce wrong results if ever re-activated.

**Fix:** Remove all the above. Remove the `@Suppress("unused")` annotation so the compiler can catch future drift. Verify `computePdfPtPerMmFitAxes` has no test callers before deleting.

---

## 4. MEDIUM — Documentation Inaccuracies in `BRIEFING.md`

`BRIEFING.md` is the primary onboarding document. It contains several factual errors about the code model:

### 4.1 Threads flag is inverted

> **BRIEFING.md line 92:** `includeInOal` flag

**Actual code:** `excludeFromOAL: Boolean = false` in `Threads.kt`

The semantics are inverted. The flag is "exclude" (opt-out), not "include" (opt-in). This will cause immediate confusion for any developer reading the briefing and then looking at the code. The OAL Window section (line 101) repeats the error: *"when end threads have `includeInOal = false`"* — it should be *"when `excludeFromOAL = true`"*.

### 4.2 Threads field name wrong

> **BRIEFING.md line 92:** `Taper` description says *"Stores both `aftDiaMm` / `fwdDiaMm`"*

**Actual code:** Fields are `startDiaMm` and `endDiaMm` in `Taper.kt`.

### 4.3 Liner field name wrong

> **BRIEFING.md line 93:** `Liner` described as storing `outerDiaMm`

**Actual code:** Field is `odMm` in `Liner.kt`.

### 4.4 PDF export does not use ShaftRenderer

> **BRIEFING.md line 59:** *"ShaftRenderer is the single source of truth for geometry drawing (both preview and PDF use it)"*  
> **PDF_EXPORT.md section 1:** *"spec → ShaftLayout (PDF size) → ShaftRenderer (PDF canvas)"*

**Actual code:** `ShaftPdfComposer.kt` contains its own geometry drawing functions (`drawBodiesPlain`, `drawBodiesCompressedCenterBreak`, `drawTapers`, `drawThreads`, `drawLiners`). It does **not** call `ShaftRenderer`. The two rendering paths (preview and PDF) share the same model and layout math but use entirely separate drawing code.

This is a notable divergence from the stated architectural intent. It means a geometry fix in `ShaftRenderer` may not be reflected in the PDF unless the same fix is applied to the PDF drawing functions.

**Fix:** Either:
- (a) Refactor the PDF composer to actually call `ShaftRenderer` for geometry (the architecturally correct path, but requires ShaftRenderer to support Android Canvas, not just Compose DrawScope), or
- (b) Update BRIEFING.md and PDF_EXPORT.md to accurately describe the dual-path reality, and add a code comment explaining why the two paths exist separately.

---

## 5. MEDIUM — Test Coverage Gaps

### 5.1 `mapToLinerDimsForPdf` — untested adapter

**File:** [ShaftPdfComposer.kt:683](app/src/main/java/com/android/shaftschematic/pdf/ShaftPdfComposer.kt#L683)

This private function converts `spec.liners` to `LinerDim` objects used throughout the PDF dimension pipeline. It contains non-trivial logic: proximity-based anchor inference, `AUTO` vs. `AFT`/`FWD` forced anchor override, and SET-relative offset computation. It is `private` and has no unit tests. The `LinerDim` model path is tested elsewhere but not this specific adapter.

### 5.2 `buildTaperLengthSpans` — untested span builder

**File:** [ShaftPdfComposer.kt:649](app/src/main/java/com/android/shaftschematic/pdf/ShaftPdfComposer.kt#L649)

`internal` function, testable, but no tests exist. It drives taper length dimension lines on the PDF. Edge cases (no tapers, taper with excluded thread, overlapping positions) are not covered.

### 5.3 `selectFooterTapers` — minimal coverage

**File:** [ShaftPdfComposer.kt:455](app/src/main/java/com/android/shaftschematic/pdf/ShaftPdfComposer.kt#L455)

`internal`, has a dedicated test file (`FooterEndDetectionTest`), but the single-taper mid-point classification logic (`midMm <= oal * 0.5f`) has no tests for the boundary condition or the degenerate `oal == 0` case.

### 5.4 No test for `buildLinerSpans` with `PdfTieringMode.AUTO` proximity flip

**File:** [LinerSpanBuilder.kt](app/src/main/java/com/android/shaftschematic/pdf/dim/LinerSpanBuilder.kt)

`buildLinerSpans` in `AUTO` mode picks anchor by proximity (`distFwd < distAft`). The tested cases in the existing suite all use a single-liner setup. There is no test for a liner positioned exactly at the midpoint (anchor flip boundary) or for a multi-liner AUTO scenario.

### 5.5 `DeterministicTierAssigner` vs `RailPlanner` — integration untested

The two tier-assignment classes (`DeterministicTierAssigner` and `RailPlanner`) are both present. `DeterministicTierAssigner` has its own test file. `RailPlanner` is the one actually used in `composeShaftPdf`. There is no test verifying the full `buildLinerSpans → RailPlanner → PdfDimensionRenderer` pipeline end-to-end.

---

## 6. LOW — Minor Issues

### 6.1 `hasCenterBreak` heuristic is disconnected from actual rendering

**File:** [ShaftPdfComposer.kt:1218](app/src/main/java/com/android/shaftschematic/pdf/ShaftPdfComposer.kt#L1218)

`hasCenterBreak()` uses a conservative geometric rule (`oalMm > 3000 && bodies >= 2`) to decide whether to show a "compression note" in the footer. But the actual center-break rendering in `drawBodiesCompressedCenterBreak` uses a *per-body* pixel threshold (`bodyLenPt >= COMPRESS_TRIGGER_PT`). These two checks are independent and can disagree: a shaft could render a center-break without triggering the footer note, or trigger the note without any break being drawn.

### 6.2 VALIDATION_RULES.md documents warnings not visibly implemented

Several non-blocking warnings described in section 3–4 of `VALIDATION_RULES.md` have no evident implementation:
- *"Diameter discontinuity vs neighbors"* (§3.2)
- *"Large mismatch with adjacent body diameter"* for tapers (§3.3)  
- *"odMm < underlying shaft diameter"* for liners (§3.5)
- *"Zero-body coverage"* at spec level (§4.3)

These may be planned but unimplemented, or implemented in UI code not found during this audit (e.g., inside dialog composables). If unimplemented, the document is aspirational rather than descriptive. Either label them `TODO` in the document or implement them.

### 6.3 `BRIEFING.md` sprint status is stale

BRIEFING.md line 166 marks "1.2 Preview Tap → mm (not started)" but the recent commit history (`b3272a0 Add preview tap selection`, `cbb5a8d Elevate add component button`) suggests this is at least partially in progress. The BRIEFING sprint section should be updated to reflect current state.

### 6.4 Double dead `END_EPS_MM` constant

`END_EPS_MM` is defined twice: once as `0.5` (Double) in `OalComputations.kt` and once as `0.5f` (Float) in `ShaftPdfComposer.kt`. The comment in `OalComputations.kt` correctly notes they should match. This works correctly but is a maintenance hazard — they could diverge when one file is updated. Consider defining it once in a shared constants file.

---

## 7. Resolved Component Pipeline — Status Note

`ResolvedComponent.kt` is a complete and well-implemented resolved-component pipeline. `resolveComponents()`, `deriveAutoBodies()`, `subtractBodiesAgainstNonBodies()`, and `normalizeBodies()` are all implemented. However, `ShaftRenderer` only uses it when `components != null`, and the PDF composer only uses resolved components for body geometry (not tapers, threads, or liners). The pipeline is available but not yet fully wired into the rendering path. This is consistent with BRIEFING.md's "partially implemented" note — flagging here for visibility.

---

## 8. Summary Table

| # | Severity | Area | Issue |
|---|---|---|---|
| 1 | HIGH | PDF Rendering | Component labels collide in PDFs — all drawn at same Y |
| 2 | MEDIUM | ShaftPdfComposer | ~250 lines of dead code suppressed by `@Suppress("unused")` |
| 3 | MEDIUM | BRIEFING.md | `includeInOal` should be `excludeFromOAL` (inverted); `aftDiaMm`/`fwdDiaMm` should be `startDiaMm`/`endDiaMm`; `outerDiaMm` should be `odMm` |
| 4 | MEDIUM | BRIEFING.md + PDF_EXPORT.md | PDF does not use ShaftRenderer; has its own duplicate drawing code |
| 5 | MEDIUM | Tests | `mapToLinerDimsForPdf`, `buildTaperLengthSpans`, and liner AUTO-anchor edge cases lack unit tests |
| 6 | LOW | ShaftPdfComposer | `hasCenterBreak` footer heuristic disconnected from actual rendering trigger |
| 7 | LOW | VALIDATION_RULES.md | Several documented warnings appear unimplemented |
| 8 | LOW | BRIEFING.md | Sprint status stale (tap selection in progress) |
| 9 | LOW | OalComputations + ShaftPdfComposer | `END_EPS_MM` defined twice; could diverge |

---

## 9. Recommended Action Order

1. **Fix component label collision** (§2.1) — visible to end users, constants and partial algorithm already exist.
2. **Delete dead code** (§3.1) — low risk, reduces maintenance surface, removes the `@Suppress` blanket.
3. **Update BRIEFING.md** (§4.1–4.4) — 15-minute fix, prevents developer confusion.
4. **Add tests for `mapToLinerDimsForPdf` and `buildTaperLengthSpans`** (§5.1–5.2) — these are the most logic-dense untested paths in the PDF pipeline.
5. **Decide on dual renderer paths** (§4.4) — either converge them or formally document why they're separate.
