# Overall Length (v2.1, 2026-08-29)

## Scope
Defines how the **Overall Length** field behaves, how it interacts with components, and how the
UI signals errors. Applies to `ShaftViewModel`, `ShaftRoute`, and `ShaftScreen`.

---

## UX Summary
- **The OAL is always user-typed.** There is no automatic mode: nothing derives, grows or
  shrinks `ShaftSpec.overallLengthMm` on the user's behalf. It is a component value under the
  golden rule (`CLAUDE.md`) — a typed length stands exactly as typed.
- **Default**: Overall starts at **0** — "not set yet", not "a zero-length shaft". The field
  shows `0`, and focusing it clears that ghost so the user can type without a leading zero.
- **Commit on every keystroke**: `onSetOverallLengthMm` fires on every parseable keystroke so
  the preview updates live. This is deliberate; do **not** narrow it to commit-on-blur.
- **Empty field**: an IME-Done or a blur on an empty field commits **nothing**. The field text
  re-derives from the stored value (a revert). It never zeroes the shaft.
- **Blur discipline**: a commit on blur also requires the text to have changed since focus
  (`ui/input/BlurCommitPolicy.kt`) — a tap-and-leave is a no-op.
- **Oversize**: if components extend past a **set** Overall (`> 0`), the input shows an
  **error**. A not-yet-set Overall (`0`) is never an error.

---

## Business Rules
- Canonical units are **millimeters** (mm). All computations are in mm; formatting happens at
  the UI edge.
- Oversize is allowed but surfaced as an error state (no automatic correction).
- **0 means "not yet authored".** Nothing backfills it — not a document load, not the first
  component added. The renderer's 0-OAL fallback — `ShaftSpec.renderSpanSpec()`
  (`ui/drawing/RenderSpanSpec.kt`), ONE implementation read by `ShaftDrawing` and
  `ShaftThumbnail` — draws such a shaft to its coverage end, and it is the only guard. It
  deliberately folds EXCLUDED threads into that end, unlike `coverageEndMm()`: a thread drawn
  outside the shaft envelope still has to fit inside the canvas. The preview OAL badge mirrors
  the same fallback.
- Consumers that need a span from a 0-OAL spec fall back themselves; they never write one back.
  `resolveComponents` passes `spec.overallLengthMm` straight into `deriveAutoBodies`, whose
  `<= 0f` branches are the genuine 0-OAL guards.
- The collision/add "falls outside shaft span" warning is gated on `overallLengthMm > 0f`
  alone (`ui/util/CollisionWarnings.kt`).

---

## PDF OAL Dimension Span

The OAL label always shows the user's typed value (`spec.overallLengthMm`). It never changes
when threads are included or excluded. Only the **bracket position** moves:

- **Excluded** (`excludeFromOAL = true`): bracket spans **AFT SET → FWD SET**. The threads are
  drawn outside the bracket.
- **Included** (`excludeFromOAL = false`): bracket spans **shaft AFT end (0.0) → FWD SET**,
  visually grouping the AFT thread inside the OAL arrow.

Symmetrically for FWD end threads. Label is passed explicitly to
`oalSpan(..., labelMm = spec.overallLengthMm)` so it is always the typed OAL regardless of
bracket width.

Component dimension rails (liners, taper lengths) always reference SET positions and are
unaffected by this toggle.

---

## Computation

### Last occupied end (mm)
Excluded threads (`excludeFromOAL = true`) live outside the 0..OAL span by design
(`Model_Conventions.md`) and must be filtered out before folding into the max — otherwise
an AFT excluded thread's negative start or a FWD excluded thread's `OAL`-anchored end would
corrupt the result. Both real implementations filter first:
`ShaftSpecExtensions.kt` `ShaftSpec.lastOccupiedEndMm()` and `ShaftSpec.coverageEndMm()`
(`model/ShaftSpec.kt`), which the ViewModel calls rather than duplicating.
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

### Load (open, template apply, template preview)
There is nothing to decide. A document's stored `overallLengthMm` is restored verbatim and
every load path resolves through the same one-argument `resolveComponents(spec)`, so a
template card's preview can never disagree with the drawing it becomes.

---

## Change Log
**v2.1 (2026-08-29)** — **Free-to-End badge removed entirely** (on-device direction: auto-bodies
fill the gap to the OAL automatically, so the number misleads). The preview overlay, its value
helper (`ui/util/FreeToEndBadgeMath.kt`), `ShaftSpec.freeToEndMm()` and the `FreeToEndBadge.md`
contract are all gone. Oversize is signalled by the OAL field's error state and the add-dialog
"falls outside shaft span" warning; both are unchanged.
**v2.0 (2026-08-29)** — **Auto OAL mode removed** (on-device direction). The OAL is always
user-typed; the Auto/Manual chips, the auto-sync `LaunchedEffect`, the session-only
`overallIsManual` flag (autosave draft field included), `ShaftSpec.oalIsManualOnLoad()` /
`envelopeStartMm()`, `ShaftViewModel.ensureOverall()` (a no-op in the surviving mode) and
`oalAfterTaperAddMm` are all gone, along with the `showOalHelperLine` dev option (its gate
became always-true). `resolveComponents(spec)` lost its mode parameter and always passes the
spec's OAL to `deriveAutoBodies`. New behavior: an empty field reverts instead of switching
mode; a 0 OAL renders no error and draws to coverage; the bounds warning fires whenever
`OAL > 0`; the Free-to-End badge is no longer mode-gated.
**v1.3 (2026-08-24)** — Added the "Load-time mode" section: `ShaftSpec.oalIsManualOnLoad()` is
the single load-time OAL-mode predicate (adds the leading-gap clause); the ViewModel's private
`coverageEndMm` duplicate is gone.
**v1.2 (2026-07-18)** — Fixed the sample to filter `!it.excludeFromOAL` on threads before
folding into the max, matching the real `lastOccupiedEndMm()`/`coverageEndMm` implementations
(the old sample summed all threads, including excluded ones).
