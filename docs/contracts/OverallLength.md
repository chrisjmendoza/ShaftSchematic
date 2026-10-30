# Overall Length (v2.7, 2026-10-30)

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
- **Commit on accept, never per keystroke.** The field follows the component cards' discipline:
  `onSetOverallLengthMm` fires when the value is accepted — the green ✓ beside the field, IME
  Done, or a blur after the text changed — and never while typing. The red ✗ reverts the text to
  the stored value and commits nothing. Both buttons appear only while the value is being
  modified (focused, text differs from what it was on focus), so an edit in progress is visible
  as one; the field itself is a value box (`widthIn` cap), not a full-width banner. The
  per-keystroke commit was the documented live-preview exception until an on-device edit from
  368.5″ to 367.75″ committed 36″ on the way through: the drawing collapsed, then sprang back
  with the FWD taper re-anchored through the transient value (see Re-anchoring below).
- **Empty field**: an IME-Done or a blur on an empty field commits **nothing**. The field text
  re-derives from the stored value (a revert). It never zeroes the shaft.
- **Unreadable text says so**: ✓ / IME Done on text that does not parse commits nothing and
  keeps the text (the user is mid-edit); a blur restores the stored value. Both show the numeric
  fields' `"<typed> not applied — not a number"` line under the field until the text is edited
  or the edit is cancelled — a ✓ that did nothing read as a broken button (emulator QA). The
  field reads the shared grammar (`NumberField.md`): `368-1/2` and `368 1/2` commit, and a unit
  suffix converts (`9360mm` in an inch drawing is 9360 mm).
- **Blur discipline**: a commit on blur also requires the text to have changed since focus
  (`ui/input/BlurCommitPolicy.kt`) — a tap-and-leave is a no-op. A ✓ or ✗ resets that baseline
  before it clears focus, so the blur that follows cannot commit a second time or undo a cancel.
- **The OAL row yields to the card Save / Cancel bar** (on-device ruling: a Save at the end of a
  long card was hidden; this row is always on screen above the carousel). While a carousel card
  field holds a typed value that has not landed, `OverallLengthRow` shows `CardSaveBar` in the
  OAL field's place: "Unsaved change" over the waiting field and its card
  (`pendingEditName` — "“Length” on AFT Taper"), and the SAME green ✓ / red ✗
  (`AcceptCancelButtons`) in the OAL's own fixed action slot, so it reads as one control. ✓ saves
  the card value, ✗ puts its stored value back — the field's own handle
  (`NumericInputField.onPendingEdit`), never a second commit pipeline. The OAL field returns the
  moment the card is clean. With the field hidden, nothing can focus the OAL under a pending
  edit, so no prompt guards it. The card fields register through `PendingCardEdits` /
  `LocalPendingCardEdits` (`ui/screen/PendingCardEdit.kt`), provided once around the editor. A
  card change under a pending edit (swipe, edge arrow, peek tap, drawing tap) asks Save | Discard
  | Keep editing (`UnsavedCardEditDialog`). Pinned by `CardSaveBarTest` and
  `UnsavedCardEditDialogTest`.
- **Oversize**: if components extend past a **set** Overall (`> 0`), the input shows an
  **error**. A not-yet-set Overall (`0`) is never an error.
- **New-shaft diameter prompt** (on-device request: components added onto the default-sized
  bare shaft look wrong until its Ø is set). On the ORIGINAL drawing only, `ShaftRoute` reads
  `shouldPromptShaftDia(spec)` (`ui/screen/ShaftDiaPrompt.kt`: OAL ≤ 0, `autoBodyDiaMm` ≤ 0, no
  body/taper/thread/liner/bolt slot) BEFORE forwarding an OAL commit; if it was armed and the
  committed OAL is > 0, `ShaftDiaPromptDialog` opens — one diameter field in the document unit
  (fractions accepted, converted to mm at the edge), focused with the keyboard up; "Set"
  (enabled while the entry parses > 0) writes `ShaftViewModel.setAutoBodyDiaMm`, "Skip"
  dismisses. The accept path fires the mm and raw callbacks back to back: the second finds the
  OAL already set and is not armed, so the prompt shows once; the blur-with-change path arms
  the same way. A Skip is final (the OAL is no longer 0). Loading a document or template never
  prompts (they carry an OAL or components); `newDocument()` resets to a blank `ShaftSpec()`,
  so the next new drawing prompts again. The Ø is its own undo step after the OAL's. The
  prompt never writes the OAL — nothing backfills it (below).

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
  alone. That gate lives in the ONE bounds predicate, `outsideShaftSpan`
  (`ui/util/ComponentWarnings.kt`), which the add dialogs' pre-submit warning
  (`ui/util/CollisionWarnings.kt`, which only calls it) and the carousel cards' past-OAL chip
  both read — do not fork the comparison back out into either surface.

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

Component dimension rails (liners, end-taper lengths, mid-shaft taper positions) always reference SET positions and are
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

### Re-anchoring on an OAL change
`ShaftSpec.withNewOal` (`model/ShaftSpecExtensions.kt`) is the ONE OAL-change helper. AFT-referenced
components keep `startFromAftMm`; FWD-referenced explicit bodies, tapers, liners and coupler bolt
slots keep their authored distance from the FWD face, recovered from the stored start as `oldOal − start − span`.
That recovery is float-noisy on a long shaft: a `Float` at 9 m resolves to ~0.001 mm, so a taper
authored flush with the face ("0 from the FWD end") came back a few thousandths of a millimetre
past the shaft end after a round trip, the card printed a stray offset and the past-end warning lit
(on-device report). The helper therefore computes in `Double` and snaps a recovered distance within
`OAL_REANCHOR_SNAP_MM` (0.01 mm) to the zero it was authored as — a derivation cleanup, not a
rewrite of a typed value (nobody authors a 0.005 mm offset; a real 1/32″ survives verbatim,
`WithNewOalTest`). The bounds predicate's slop (`BOUNDS_EPS_MM`, `ui/util/ComponentWarnings.kt`)
is 0.01 mm for the same reason: at exactly the float resolution, one unit of rounding lit the
warning on a legitimately flush taper.

### The move snackbar
The re-anchor is silent in the model, so the editor SAYS what an OAL commit moved: on the editor's
snackbar host, `OAL 234 3/16": moved FWD Liner 4 13/16" aft (measured from FWD)` — up to two card
titles, then "+N more"; "aft" / "fwd" by direction; distances in the document unit
(`formatLenWithUnit`). It carries **Undo**: the commit is ONE history step (the field's mm and
raw-text callbacks land inside one coalescing window, and the second moves nothing), so Undo takes
back the length and the move together. The event carries the `EditState` the commit produced
(`ShowOalMovedSnack.expected`) and the action calls `undoEditIfStillAt(expected)`, which undoes only
while the editor is still in that state: the snackbar stays up while the user keeps editing (the
route's event collector waits on it, so a delete's snackbar queues behind it), and a blind
`undoEdit()` there took back the later card edit or delete and left the OAL and the moved parts in
place. Once anything has changed, Undo does nothing and says so ("Nothing undone — the drawing has
changed since. Use History → Undo."). The delete snackbar carries the same token
(`ShowDeletedSnack.expected`). The pure diff is
`oalMoveSummary(before, after, unit)` (`ui/util/OalMoveSummary.kt`): explicit bodies, tapers,
liners and coupler bolt rows whose stored start moved by more than `OAL_REANCHOR_SNAP_MM`, ordered AFT → FWD;
excluded threads are left out (they follow the end by definition). `ShaftViewModel.onSetOverallLengthMm`
emits `UiEvent.ShowOalMovedSnack` when it returns a line; nothing moved, no snackbar. Both
targets: the Final tab's OAL field takes the same path (`ShaftRoute(target = FINAL)`). On-device
report: an OAL correction from 239" to 234 3/16" carried a FWD-measured liner into a mid-shaft
taper and nothing said why the two now overlapped.

### Thread inclusion prompt
The thread card's **"Include thread in OAL"** switch ASKS whether the shaft's overall length
follows the thread (on-device report: a job's OAL was measured over the FWD thread, the thread was
marked out of the OAL, the sheet printed a thread-length too long, and switching the thread in put
it on top of the FWD end part). Tapping the row opens an `AlertDialog`; the switch keeps showing
the STORED state until a choice is made.
- **Out of the OAL** — "Exclude thread from OAL": `Shorten the overall length by the thread? OAL
  <O> → <O−L>. …` with **Shorten OAL** / **Keep OAL** / **Cancel**.
- **Into the OAL** — "Include thread in OAL": `Lengthen the overall length by the thread? OAL <O> →
  <O+L>. …` with **Lengthen OAL** / **Keep OAL** / **Cancel**.
- Lengths print in the document unit (`formatLenWithUnit`). testTags: `thread_include_in_oal` (the
  row), `thread_oal_adjust` (Shorten/Lengthen), `thread_oal_keep`, `thread_oal_cancel`. Cancel or
  an outside tap applies nothing.
- **No prompt** when `overallLengthMm <= 0` or the thread's `lengthMm <= 0`: the switch flips at
  once with no OAL change.

The ONE model helper is `ShaftSpec.withThreadOalInclusion(id, excludeFromOAL, adjustOal)`
(`model/ShaftSpecExtensions.kt`), and its OAL change IS `withNewOal` — exactly what typing the new
OAL into the field does: FWD-referenced explicit bodies, tapers, liners and bolt rows keep their distance from the
FWD face, AFT-referenced parts keep their start, nothing else moves. There is no second OAL-change
path. (A "nothing else moves" variant that would hold FWD-measured parts in place was considered
and deferred — a product question, not built.)
- **Out**: the thread goes out at the end it is SITTING at — AFT when its AFT gap `s` ≤ its FWD gap
  `O − (s + L)`, FWD otherwise. The stored `isAftEnd` is dormant while the thread is included (its
  chips are hidden), so a stale default sent a FWD thread to the AFT end. With no OAL typed the
  stored end stands. The excluded thread is then synced outside that end
  (`syncExcludedThreadPositions`); Shorten follows with `withNewOal(O − L)`, so a FWD thread stays
  physically where it was, now beyond the new FWD face.
- **In**: Lengthen first runs `withNewOal(O + L)`; then the thread is placed INSIDE the shaft at its
  end — `startFromAftMm = 0` at AFT, `OAL − L` at FWD (floored at 0). "Included in the OAL" means
  inside it; an included thread that kept its outside position (`−L` or `O`) lit the
  past-the-end warning. That start is derived (an excluded thread's start is system-placed), so it
  replaces a derived value, never a typed one. With Lengthen a FWD thread stays physically where it
  was, now inside the new OAL; an AFT thread lands at `[0, L]`.
- The OAL changes only when `O > 0` and `L > 0`; `withNewOal` clamps at 0 (a Shorten past zero is
  what typing would do).
- A thread relief still at its suggested end follows the move (`withThreadReliefFollowing`, the old
  suggestion read against the OAL the thread was on).

`ShaftViewModel.setThreadExcludeFromOal(id, excludeFromOAL, adjustOal = false, target)` applies it in
ONE `updateSpec` — the flag, the OAL change and the relief follow are one undo step. With
`adjustOal`, an OAL change that re-anchored a FWD-measured part emits the same
`UiEvent.ShowOalMovedSnack` as the field (`announceOalMove`, shared with `onSetOverallLengthMm`),
whose Undo takes the flag and the OAL back together. Both targets, like the field. The Add Thread
dialog carries nothing for it: the prompt is a confirmation on a change to a STORED thread (the
`body_demote_confirm` class), not a control (`AddComponentDialogs.md`).

### Load (open, template apply, template preview)
There is nothing to decide. A document's stored `overallLengthMm` is restored verbatim and
every load path resolves through the same one-argument `resolveComponents(spec)`, so a
template card's preview can never disagree with the drawing it becomes.

---

## Change Log
**v2.7 (2026-10-30)** — OAL row yields to the card Save / Cancel bar while a card field holds an
unsaved value; the field is hidden, so nothing can focus it under a pending edit. The ✓ / ✗ pair is
the ONE `AcceptCancelButtons`, shared with the bar.

**v2.6 (2026-10-29)** — **Explicit bodies re-anchor** (on-device request: an explicit body exists to
sit at an exact point, often off the coupling end): an explicit body carries "Measure From: AFT |
FWD" (`Body.authoredReference`), and `withNewOal` keeps a FWD body's distance from the FWD face like
a FWD taper's or liner's; the move snackbar names it.
**v2.5 (2026-10-29)** — **Thread inclusion prompt** (on-device report): the thread card's "Include
thread in OAL" switch asks whether the OAL follows (Shorten/Lengthen through `withNewOal`, or Keep);
a thread taken out goes out at the end it sits at, and one brought in lands inside the shaft at its
end (`withThreadOalInclusion`).
**v2.4 (2026-10-28)** — **The move snackbar** (on-device report): an OAL commit that re-anchors
FWD-measured parts says which moved, how far and which way, with Undo (`oalMoveSummary`). The
warning banner now names a part past an end ("Body #1 runs 4 13/16" past the FWD end") instead of
counting it, and the preview tints the overhang red (`ShaftScreen.md`).
**v2.3 (2026-10-27)** — **New-shaft diameter prompt** (on-device request): the first OAL commit
on a brand-new original drawing asks for the bare-shaft Ø (`shouldPromptShaftDia` /
`ShaftDiaPromptDialog`); Set writes `autoBodyDiaMm`, Skip is final.
**v2.2 (2026-09-29)** — **Per-keystroke commit removed** (on-device report: an edit from 368.5″
to 367.75″ committed 36″ in passing and collapsed the drawing). The field commits on accept (✓ /
IME Done / blur-with-change), ✗ cancels, the pair shows only while modified, and the field is
narrowed to a value box. `withNewOal` re-anchors in `Double` with a 0.01 mm zero-snap and
`BOUNDS_EPS_MM` widened to 0.01 mm, closing the flush-taper "past the shaft end" residue.
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
