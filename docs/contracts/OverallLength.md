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
`ShaftSpecExtensions.kt` `ShaftSpec.lastOccupiedEndMm()` and `ShaftSpec.coverageEndMm()`
(`model/ShaftSpec.kt`), which the ViewModel now calls rather than duplicating.
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

### Load-time mode (open, template apply, template preview)

`overallIsManual` is **derived from the document**, never inherited from the previous session.
One pure predicate decides it — `ShaftSpec.oalIsManualOnLoad()` (`model/ShaftSpec.kt`) — and it
is manual on either signal:

- `overallLengthMm > coverageEndMm() + 1e-3` — free length to the FWD end.
- `overallLengthMm > 0` **and** the aft-most component starts past 0 — a leading bare-shaft
  span. Auto mode passes `overallLengthMm = 0f` to `deriveAutoBodies`, which derives leading and
  trailing spans only when a manual OAL is in play, so loading such a document as auto drops
  that span from the drawing.

Membership mirrors `coverageEndMm`: threads excluded from OAL sit outside the 0..OAL span and
are skipped on both ends. A spec with no components in the envelope is never manual by the
leading-gap rule.

All three load paths read the one predicate — `ShaftViewModel.importJson`,
`ShaftViewModel.applyTemplate`, and the template card's preview resolve in `TemplatesRoute`.
A private predicate at any of them lets a preview disagree with the drawing it becomes.

---

## Change Log
**v1.3 (2026-08-24)** — Added the "Load-time mode" section: `ShaftSpec.oalIsManualOnLoad()` is
the single load-time OAL-mode predicate (adds the leading-gap clause); the ViewModel's private
`coverageEndMm` duplicate is gone.
**v1.2 (2026-07-18)** — Fixed the sample to filter `!it.excludeFromOAL` on threads before
folding into the max, matching the real `lastOccupiedEndMm()`/`coverageEndMm` implementations
(the old sample summed all threads, including excluded ones).
