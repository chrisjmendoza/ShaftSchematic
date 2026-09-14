# Final Schematic — Design Plan

Status: **implemented** on `feat/final-schematic-2026-09-04` (2026-09-04, uncommitted pending review). Contract doc (current behavior once
shipped): `docs/contracts/FinalSchematic.md`. This file is the design/rationale record.

## 1. The shop problem

A liner's position is decided before the job starts, but the shaft is not what it was on paper.
Once the wear areas are mapped, the foreman can decide — after an undercut — to extend a liner,
shorten it, or move the whole thing a few inches so it seats on sound metal. The drawing the
job started from must survive that decision unchanged: it is the record of what came in, and
the before/after pair is what gets compared. What the shop needs is a **second** schematic —
the same sheet, drawn from the geometry the shaft is leaving with.

On-device request (2026-09-04): "a page for a new final Schematic where an original shaft's
components can be adjusted to create a new output like the original Schematic. I don't want to
change the original drawing, I want to be able to keep it for historical purpose, like a before
and after." Placement: after the Undercut sheet, because it follows undercuts in the process.
Consolidated output stays on the original. Runouts can be taken on the final drawing as a
final measurement sheet before the job ships. "Create a new job from the final" is a later
step, deliberately not built here.

## 2. Model — a second geometry, not a view

- `ShaftDocV1.final_spec: ShaftSpec? = null` — a whole `ShaftSpec`, sibling of `spec`. Absent
  in every existing file → `null` ("no final drawing yet"). Additive + defaulted: no version
  bump. Encoded verbatim by `ShaftDocCodec.encodeV1`, read back by `decode`.
- It is **NOT a reference feature** and NOT derived from the original. At "Start from original"
  it is a structural copy of `spec` — component ids INCLUDED — and from then on the two are
  independent geometries under the same golden rule: nothing the user types into either is
  ever rewritten, and no edit on one reaches the other. Keeping the ids is what lets the
  per-component unit overrides (`unit_overrides`, keyed by resolved id) and a future
  before/after diff line the two drawings up component for component.
- It lives OUTSIDE `ShaftSpec` (a `ShaftSpec` inside a `ShaftSpec` would let every consumer of
  the original see a second geometry it must ignore). Wear/undercut/runout records stay keyed
  to the original — those are the inspection of the shaft that came in.
- Exactly one final per document. Not a revision history: "Reset to original" replaces it with
  a fresh copy, "Discard" drops it. Both are undoable edits.
- Templates never carry it (a template is a pre-job shape). "Duplicate for mate" never carries
  it (the mate is its own job).

## 3. ViewModel — one editor, two targets

`SpecTarget { ORIGINAL, FINAL }` names which geometry a mutation lands on. Every component
mutator (`ShaftViewModelComponents.kt`: add/update/remove/label/shade/blend/keyway/… and the
OAL + auto-section setters in `ShaftViewModel.kt`) takes `target: SpecTarget = SpecTarget.ORIGINAL`
as its LAST parameter and writes through ONE seam:

```kotlin
internal inline fun ShaftViewModel.updateSpec(target: SpecTarget, transform: (ShaftSpec) -> ShaftSpec)
internal fun ShaftViewModel.specValue(target: SpecTarget): ShaftSpec
```

`ORIGINAL` → `_spec`; `FINAL` → `_finalSpec` (a `FINAL` write while no final exists is a
no-op — the editor for it is not reachable then). The default keeps every existing call site
byte-identical. The target is an explicit parameter, never hidden ViewModel state: a
"current target" flag would make the wrong drawing a tab-switch away from every edit.

State: `finalSpec: StateFlow<ShaftSpec?>`, `finalResolvedComponents` (derived the same way as
`resolvedComponents`, empty while null). Lifecycle: `startFinalSpec()` (copy, no-op if one
exists), `resetFinalSpec()` (fresh copy), `discardFinalSpec()` (null). Selection
(`selectedComponentId`) and session add-defaults are shared between targets — ids are shared
by construction, and the defaults are a session convenience.

Undo: `EditState.finalSpec` joins the snapshot (recorder combine + `applyEditState`), so a
start/reset/discard and every edit on the final undoes like any drawing edit. Autosave:
`SessionSnapshot.finalSpec` joins the combine (a field in the snapshot builder but not the
combine is the exact data-loss gap the autosave incident doc records). Persistence:
`newDocument` clears it, `importJson`/`restoreSnapshot` set it, `toDoc`/save write it.

## 4. Outputs

| Surface | Spec |
|---|---|
| Schematic tab, Runout tab, Wear document, Undercut drawing, Consolidated Output (all three variants, Export all) | original — unchanged |
| Final tab → schematic PDF (preview / export / print) | final |
| Final tab → runout sheet (classic standalone, `consolidated = false`) | final; NO readings, NO placements, NO wear, default station counts — a blank final measurement sheet |

Both final PDFs are marked so a sheet can never be mistaken for the original:
`ProjectInfo.drawingLabel` ("Final") prints as a `Drawing: Final` line in the schematic footer's
job block and the runout header line, plus a bold **FINAL** badge beside the Side badge on the
schematic footer. Blank drafts rule a `Drawing:` line only when the label is set. Filenames take
a `_Final` suffix. The export gate (`blockingExportError`) runs on the final spec exactly as it
does on the original.

## 5. UI

- `EditorTab.FINAL` ("Final Schematic"), between UNDERCUT and OUTPUT; enabled by the same
  built-shaft rule as the other document tabs.
- `FinalRoute`: with no final → an empty state (what it is, what it is not, one primary
  button "Start from original schematic"). With one → the SAME editor as the Schematic tab
  (`ShaftRoute` made target-aware: `target = FINAL`, spec/resolved from the final flows, every
  callback passing the target) under a persistent banner — "Final drawing · the original
  schematic is untouched" — carrying Reset / Discard (both confirm). The carousel, add
  dialogs, preview box, collision badges and warnings all work unchanged because they are
  presentational over whatever spec they are handed.
- The Final tab's toolbar Export/Preview/Print act on the final spec (route argument, never a
  flag read from the ViewModel).

## 6. Not in this step

- "Create a new job from the final" (promote `final_spec` → a new document's `spec`).
- Any diff/overlay between original and final on one sheet.
- Wear/undercut/runout records re-keyed to the final geometry.
- Consolidated output of the final.

## 7. Tests

Codec round-trip (present / absent / null), autosave snapshot round-trip + dirty gate, VM
target isolation (a FINAL edit never touches the original and vice versa; FINAL while null is a
no-op; start copies ids; reset/discard undo), newDocument/template/mate exclusion, footer and
header label lines (pure builders), tab order/enable rule.
