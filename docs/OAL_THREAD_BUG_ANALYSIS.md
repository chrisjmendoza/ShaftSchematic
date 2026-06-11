# OAL / Thread Exclusion Bug — Investigation Report

**Date:** 2026-06-11  
**Branch:** `feat/runout-screen-v2`  
**Status:** ✅ RESOLVED — see §6 for implemented fix  
**Reporter symptom:** "I input an OAL, added a 5″ thread and excluded it from OAL, and the OAL was changed to exclude the threads. The OAL is a sacred input and cannot be changed."

---

## TL;DR

- The **stored** OAL input (`ShaftSpec.overallLengthMm`) is **not** mutated when you add/exclude a thread (in manual mode). That value is safe.
- The **rendered** OAL — the dimension label on the schematic preview *and* every PDF, *and* the input field while in Auto mode — is **derived** by subtracting excluded end-thread lengths from the input. That derived number is what visibly "changed" to `input − threadLen`.
- Root cause: `computeOalWindow()` / `computeExcludedThreadLengths()` (`geom/OalComputations.kt`). This was introduced/extended by today's work (commits `44049d8`, `ff6593d`) and codified in `docs/OverallLength.md` v1.1 ("OAL spans SET-to-SET when excluded"). That model directly conflicts with the new directive that the OAL number is immutable.

---

## 1. Where OAL and threads live

| Concern | File | Line |
|---|---|---|
| Stored OAL input | `model/ShaftSpec.kt` (`overallLengthMm: Float`) | 22 |
| Thread model + `excludeFromOAL` flag (default `false`) | `model/Threads.kt` | 36–45 |
| OAL setter (clamps ≥0 only; no thread math) | `ui/viewmodel/ShaftViewModel.kt` `onSetOverallLengthMm` | 971–973 |
| Manual-lock flag | `ShaftViewModel.kt` `_overallIsManual` | 286–288 |
| Auto-grow helper | `ShaftViewModel.kt` `ensureOverall` | 985–990 |
| Add thread | `ShaftViewModel.kt` `addThreadAt` | 1295–1320 |
| Toggle include/exclude | `ShaftViewModel.kt` `setThreadExcludeFromOal` | 1351–1360 |
| Toggle UI | `ui/screen/ComponentCarousel.kt` ("Include thread in OAL") | 481–492 |

## 2. Is the stored input mutated? No (in the reported scenario)

`ensureOverall()` is the only thing that writes `overallLengthMm` after the user sets it:

```kotlin
// ShaftViewModel.kt:985
fun ensureOverall(minFreeMm: Float = 0f) = _spec.update { s ->
    if (_overallIsManual.value) return@update s        // manual → no-op
    val end = coverageEndMm(s)
    val minOverall = end + max(0f, minFreeMm)
    if (s.overallLengthMm < minOverall) s.copy(overallLengthMm = minOverall) else s  // only GROWS
}
```

- Typing an OAL value and committing sets manual mode (`ShaftScreen.kt:560–564`, `597–602`).
- In manual mode `ensureOverall()` is a no-op, so adding/excluding a thread (`addThreadAt`→`ensureOverall`, `setThreadExcludeFromOal`→`ensureOverall`) **cannot** change the stored value.
- Even in Auto mode, `ensureOverall` only ever **grows** — it never subtracts. So it can't produce the "shrunk to exclude the thread" symptom.

**Conclusion:** the stored input is intact. The bug is entirely in the **derived/rendered** OAL.

## 3. The actual mechanism — measure-space compression

`geom/OalComputations.kt`:

```kotlin
// :60  excluded end-thread engagement length, per end
fun computeExcludedThreadLengths(spec: ShaftSpec): ExcludedThreadLengths {
    val oalRaw = spec.overallLengthMm.toDouble().coerceAtLeast(0.0)
    val aftThread = findAftEndThread(spec)                 // thread starting at x=0
    val fwdThread = findFwdEndThread(spec, oalRaw)         // thread ending at x=oalRaw
    val aft = if (aftThread?.excludeFromOAL == true) aftThread.lengthMm... else 0.0
    val fwd = if (fwdThread?.excludeFromOAL == true) fwdThread.lengthMm... else 0.0
    return ExcludedThreadLengths(aft, fwd)
}

// :72  the OAL "window" SUBTRACTS excluded threads from the raw input
fun computeOalWindow(spec: ShaftSpec): OalWindow {
    val oalRaw = spec.overallLengthMm.toDouble().coerceAtLeast(0.0)
    val ex = computeExcludedThreadLengths(spec)
    val effective = max(0.0, oalRaw - ex.aftExcludedMm - ex.fwdExcludedMm)   // ← shrinks here
    return OalWindow(measureStartMm = aft, measureEndMm = aft + effective)
}
```

`OalWindow.oalMm = measureEndMm − measureStartMm = effective`. So **`win.oalMm` is the input minus excluded end-thread lengths.**

The printed label is literally that distance (`pdf/dim/LinerSpanBuilder.kt:62`):

```kotlin
fun oalSpan(x1Mm, x2Mm, unit) =
    DimSpan(..., labelTop = "OAL ${formatLenDim(x2Mm - x1Mm, unit)}", ...)
```

### Worked example (the reported case)
Input OAL = 100″, add a 5″ thread at the FWD end (occupies 95″–100″), mark **excluded**:
- `computeExcludedThreadLengths` → `fwd = 5″`.
- `computeOalWindow` → `effective = 100 − 5 = 95″`, so `win.oalMm = 95″`.
- No FWD taper ⇒ `sets.fwdSETxMm = win.oalMm = 95″`, `sets.aftSETxMm = 0`.
- `ShaftPdfComposer.kt:276–282`, excluded branch ⇒ `oalAft = 0`, `oalFwd = 95` ⇒ label **"OAL 95″"**.

The drawing now reads **95″** while the input field (manual) still reads **100″** → the user sees the OAL "changed."

## 4. Everywhere the derived OAL leaks (all shrink on exclude)

| Surface | File:line |
|---|---|
| Schematic preview dimension | `ui/drawing/compose/ShaftDrawing.kt:329` |
| Main shaft PDF | `pdf/ShaftPdfComposer.kt:227, 239, 276–282` |
| Runout PDF | `pdf/RunoutPdfComposer.kt:114–115, 176, 181` |
| Wear PDF | `pdf/WearPdfComposer.kt:95–96, 104, 107` |
| **Input field display in Auto mode** | `ui/screen/ShaftScreen.kt:517–518` (`displayMm = effectiveOalDisplayMm`), and `558, 595, 621` |

> Note `ShaftScreen.kt:518`: `val displayMm = if (overallIsManual) spec.overallLengthMm else effectiveOalDisplayMm`. In **manual** mode the field shows the raw input (good); in **Auto** mode it shows the shrunk value too.

## 5. Why today's "fix" (`ff6593d`) didn't address this

`ff6593d` only changed *which endpoint* the OAL arrow anchors to (SET vs shaft-end) for **included** threads. It left the `computeOalWindow` subtraction — the thing that shrinks the number for **excluded** threads — fully in place. The excluded path is exactly the default/reported path.

---

## 6. Proposed fix (smallest correct change)

**Principle from the directive:** the OAL number = the user's input, always. Include/exclude must change only how the thread is *drawn under the OAL bracket*, never the value.

**Core change — stop subtracting excluded threads from the OAL window.** In `geom/OalComputations.kt`, make `computeOalWindow` return the full input window:

```kotlin
fun computeOalWindow(spec: ShaftSpec): OalWindow {
    val oalRaw = spec.overallLengthMm.toDouble().coerceAtLeast(0.0)
    return OalWindow(measureStartMm = 0.0, measureEndMm = oalRaw)   // no exclusion math
}
```

Effect: `win.oalMm == spec.overallLengthMm` everywhere, so the schematic, all three PDFs, and the Auto-mode field all read the input value. `computeExcludedThreadLengths` becomes unused for the window (can be removed or retained only if the arrow-extent toggle still needs it).

This is a near-revert of the measure-space compression added today; the `excludeFromOAL` flag then only drives the arrow-extent toggle already present in `ShaftPdfComposer.kt:276–282`.

**Docs to update:** `docs/OverallLength.md` §"PDF OAL Dimension Span" (v1.1) currently specifies the SET-to-SET shrink behavior — it must be rewritten to the immutable-OAL rule.

### One open decision (changes the exact fix)
The directive says an **included** thread is "added to the OAL dimensional line." Today the geometry places end threads *inside* `[0, overallLengthMm]` (a FWD thread *ends at* `overallLengthMm`). So "included" can't visibly add length unless threads are modeled as extending *beyond* the input. Which is intended?

- **(C) Label-locked:** OAL label always = input; include/exclude only changes whether the bracket visually encloses the thread region. (Matches "number never mutates" most literally; the fix above is sufficient.)
- **(B) Additive:** input is the base length *excluding* end threads; an *included* thread makes the OAL read `input + threadLen` (and is positioned beyond the input). Larger change — affects thread placement geometry, not just the window.

The reported complaint (excluding shrank the number) is fixed identically under both. The difference only matters for what *including* should do.
