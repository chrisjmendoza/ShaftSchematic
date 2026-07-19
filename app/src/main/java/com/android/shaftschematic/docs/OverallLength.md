# Overall Length — Auto vs Manual (v1.2, 2026-07-18)

## Scope
Defines how the **Overall Length** field behaves, how it interacts with components, and how the UI signals errors. Applies to `ShaftViewModel`, `ShaftRoute`, and `ShaftScreen`.

---

## UX Summary
- **Default**: Overall starts at **0** with a **ghost “0”** placeholder in the input.
- **Auto mode** (default): Overall automatically grows to the **last occupied end** of all components.
- **Manual lock**: When the user types a value and commits (IME Done or blur), Overall switches to **manual** and holds that exact value.
- **Unlock**: Clearing the field (empty) switches back to **auto** and snaps Overall to the current last occupied end.
- **Oversize**: If components extend past a **manual** Overall, the input shows an **error** and the Free-to-End badge turns **red** with a **negative** value.

---

## Business Rules
- Canonical units are **millimeters** (mm). All computations are in mm; formatting happens at the UI edge.
- Auto growth is **one-way**: It never shrinks below the last occupied end while in auto mode.
- Manual mode **disables auto growth** completely until the user clears the field or edits it again.
- Oversize is allowed but surfaced as an error state (no automatic correction).

---

## PDF OAL Dimension Span

The OAL label always shows the user's typed value (`spec.overallLengthMm`). It never changes when threads are included or excluded. Only the **bracket position** moves:

- **Excluded** (`excludeFromOAL = true`): bracket spans **AFT SET → FWD SET**. The threads are drawn outside the bracket.
- **Included** (`excludeFromOAL = false`): bracket spans **shaft AFT end (0.0) → FWD SET**, visually grouping the AFT thread inside the OAL arrow.

Symmetrically for FWD end threads. Label is passed explicitly to `oalSpan(..., labelMm = spec.overallLengthMm)` so it is always the typed OAL regardless of bracket width.

Component dimension rails (liners, taper lengths) always reference SET positions and are unaffected by this toggle.

---

## Computation

### Last occupied end (mm)
Excluded threads (`excludeFromOAL = true`) live outside the 0..OAL span by design
(`Model_Conventions.md`) and must be filtered out before folding into the max — otherwise
an AFT excluded thread's negative start or a FWD excluded thread's `OAL`-anchored end would
corrupt the result. Both real implementations filter first:
`ShaftSpecExtensions.kt` `ShaftSpec.lastOccupiedEndMm()` and the ViewModel's `coverageEndMm`.
```
/** Latest occupied end (in mm) from all components. */
fun ShaftSpec.lastOccupiedEndMm(): Float {
    var maxEnd = 0f
    bodies.forEach   { maxEnd = max(maxEnd, it.startFromAftMm + it.lengthMm) }
    tapers.forEach   { maxEnd = max(maxEnd, it.startFromAftMm + it.lengthMm) }
    threads.filter { !it.excludeFromOAL }
           .forEach { maxEnd = max(maxEnd, it.startFromAftMm + it.lengthMm) }
    liners.forEach   { maxEnd = max(maxEnd, it.startFromAftMm + it.lengthMm) }
    return maxEnd
}
```

---

## Change Log
**v1.2 (2026-07-18)** — Fixed the sample to filter `!it.excludeFromOAL` on threads before
folding into the max, matching the real `lastOccupiedEndMm()`/`coverageEndMm` implementations
(the old sample summed all threads, including excluded ones).
