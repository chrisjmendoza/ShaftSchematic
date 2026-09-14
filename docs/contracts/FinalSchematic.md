# Final Schematic — Contract

The **Final Schematic** is the document's second drawing: the geometry the shaft is leaving
with, after the wear and undercut work moved a liner, lengthened it, or shortened it. The
original schematic is the record of what came in and is never changed by anything on this tab
— the two are the before and the after. Design record: `docs/archive/FinalSchematic_PLAN.md`.

## 1. Model

| Field | Where | Meaning |
|---|---|---|
| `final_spec` | `.shaft` envelope (`ShaftDocCodec.ShaftDocV1.finalSpec: ShaftSpec?`) | The final drawing's whole `ShaftSpec`. `null` = no final drawing yet. |
| `finalSpec` | `AutosaveManager.SessionSnapshot` | Same, in the draft ring. |
| `finalSpec` | `EditState` | Same, in the undo snapshot. |

Rules:

- **A second geometry, not a view.** Created by "Start from original schematic" as a
  structural copy of `spec`, component ids included. From then on the two are independent
  under the same golden rule: nothing typed into either is rewritten, and no edit on one
  reaches the other. There is no live link — moving a liner on the original later does not
  move it on the final.
- **Ids are shared on purpose.** The per-component unit overrides (`unit_overrides`, keyed by
  resolved id) apply to both drawings, and a component's identity survives into a future
  before/after comparison. Components added on the final get fresh ids.
- **Outside `ShaftSpec`, inside the envelope.** Sibling of `spec`, like `wear_record` and
  `undercut_record`. Additive + defaulted: files without it decode to `null`; a legacy
  spec-only file decodes to `null`. No version bump.
- **Exactly one per document.** Not a revision history. "Reset to original" replaces it with a
  fresh copy; "Discard" sets it back to `null`. Both are undoable.
- **Never in a template** (`exportTemplateJson` builds a fresh envelope without it; applying a
  template clears it) and **never in a mate duplicate** (`mateDuplicate` resets it — the mate
  is its own job). `newDocument` clears it.
- Wear, undercut, runout readings and station placements stay keyed to the ORIGINAL
  geometry. Nothing on this tab reads or writes them.

## 2. ViewModel — one editor, two targets

`SpecTarget { ORIGINAL, FINAL }` (`ui/viewmodel/SpecTarget.kt`) names which geometry a
mutation lands on. Every component mutator in `ShaftViewModelComponents.kt` and the OAL /
auto-section setters in `ShaftViewModel.kt` take `target: SpecTarget = SpecTarget.ORIGINAL`
as their LAST parameter and write through the ONE seam:

```kotlin
// members of ShaftViewModel
internal fun updateSpec(target: SpecTarget, transform: (ShaftSpec) -> ShaftSpec)
internal fun specValue(target: SpecTarget): ShaftSpec
```

- `FINAL` while no final exists is a **no-op** — the editor for it is unreachable then.
- The target is an **explicit parameter, never hidden ViewModel state**: a "current target"
  flag would put the wrong drawing one tab-switch away from every edit.
- `finalSpec: StateFlow<ShaftSpec?>` and `finalResolvedComponents` (derived from it exactly as
  `resolvedComponents` is from `spec`; empty while `null`).
- Lifecycle (`ShaftViewModelFinal.kt`): `startFinalSpec()` (copy; no-op if one exists),
  `resetFinalSpec()` (fresh copy), `discardFinalSpec()` (`null`).
- Selection (`selectedComponentId`) and session add-defaults are shared between targets.
- Undo is ONE history for the document: `finalSpec` rides `EditState`, the recorder combine and
  `applyEditState`, so a start/reset/discard and every final edit undo like any drawing edit.
- Autosave: `finalSpec` rides `SessionSnapshot` AND the session-snapshot combine (a field in
  the builder but not the combine is the data-loss gap `docs/archive/Autosave_Incident_2026-07-25.md`
  records). A session whose only difference is the final counts as unsaved work, and
  `isDefaultSession()` additionally requires `finalSpec == null`: the autosave observer vetoes
  "default" sessions, so without that a final started on an otherwise-blank document would
  die with the process.
- Decode migrates the ORIGINAL only (`normalized()`, `freezeLegacyStationCounts`); the final
  round-trips exactly as written — it can only exist in a file written by a build that has it.
- Unit-override setters (`setComponentUnit`, `setKeywayUnit`, the metric-thread implicit
  override) are deliberately targetless: the map is keyed by component id and both geometries
  share ids by construction.

## 3. Outputs

| Surface | Draws |
|---|---|
| Schematic tab · Runout tab · Wear document · Undercut drawing · Consolidated Output (every variant, Export all) | the ORIGINAL — unchanged by this feature |
| Final tab → schematic PDF (preview, export, print) | the FINAL — by default the plain schematic (the welding / machining copy that gets the liner placements updated); with **"Runout bubbles"** elected on its PDF options sheet, the consolidated **Schematic + Runout** sheet over the final geometry (`composeRunoutPdf(consolidated = true, includeBubbles = true, includeWearInfo = false)`, empty readings / placements / wear, default station counts). `composeRunoutPdf` takes only `blankValues` from `PdfExportOptions` — the schematic-only Ø-callout election and Template mode do NOT reach the bubbled sheet, exactly as they do not reach the Output tab's consolidated sheet; the options sheet's caption says so and the "Ø callouts" chip greys out while bubbles are elected |
| Final tab → runout sheet (classic standalone, `consolidated = false`, banner overflow) | the FINAL — no readings, no placements, no wear, default station counts: a blank **final measurement sheet** to take runouts on before the job ships |

The "Runout bubbles" election is a **session flag** (`ShaftViewModel.finalRunoutBubbles`,
`setFinalRunoutBubbles`), EXACTLY the Blank-draft posture: never persisted, per app session
(not reset per document — neither is Blank draft; `ShaftViewModelFinalRunoutBubblesTest` pins
the two in lockstep), default OFF (on-device direction: the final drawing is primarily for the
welding and machining work; bubbles are for the pre-ship measurement). It shows ONLY on the
final preview's options sheet — a `ContentChip` "Runout bubbles" at the end of the Content chip
row with a caption, `final_runout_bubbles_toggle`. ONE helper, `ui/nav/FinalSheetCompose.kt`
(`finalSheetKind(target, flag)` → `PLAIN | WITH_BUBBLES`, `composeSchematicSheet(...)`),
composes the sheet for the preview's render loop, its Print button and the export route alike,
so the three can never disagree; the preview's render-inputs record carries the kind (and the
whole tuned `RunoutConfig`, since the bubbled sheet also reads the coupling-face election).

Marking — a final sheet must never pass for the original:

- `ProjectInfo.drawingLabel` (blank on every existing caller → byte-identical output).
  Set to `"Final"` by every Final-tab surface.
- Schematic footer job block prints `Drawing: Final` (after Item, before Date); the bold
  **FINAL** badge shares the Side badge's line. A blank draft rules a `Drawing:` line only when
  the label is set.
- Runout header line appends `Drawing: Final`.
- Filenames take a `_Final` suffix through the ONE join `util/ExportFilename.kt`
  (`exportPdfFilename(base, drawingSuffix, blankDraft)` → `<base><suffix>[_BlankDraft].pdf`,
  so `…_Final.pdf`, `…_Final_BlankDraft.pdf`, `…_Final_Runout.pdf` with bubbles elected, and
  `…_Final_RunoutSheet.pdf` for the banner's blank classic sheet). The label and the suffix
  are one internal pair, `FINAL_DRAWING_LABEL` / `FINAL_FILENAME_SUFFIX`
  (`ui/nav/PdfExportRoute.kt`), read by the preview, the export and the Final tab, so a
  sheet and its filename cannot disagree about which drawing they are. The preview titles
  itself "Final PDF Preview" and its print job takes the suffix too.
- The export gate (`blockingExportError`) runs on the final spec exactly as on the original.
  A blocked final runout sheet reports through an AlertDialog ("Cannot print final runout
  sheet"), the `PdfExportRoute` pattern — the banner lambda has no reach into the editor's
  snackbar host.

## 4. UI

- `EditorTab.FINAL` — "Final Schematic", between UNDERCUT and OUTPUT (it follows undercuts in
  the shop process). Enabled by the same built-shaft rule as the other document tabs.
- `FinalRoute` (`ui/screen/FinalRoute.kt`):
  - **No final yet** → the light tab chrome (document title strip, toolbar with the tab name and
    Save) over an empty state: what the tab is, what it is not, and one primary button
    **"Start from original schematic"** (`startFinalSpec`).
  - **Final present** → the SAME editor as the Schematic tab: `ShaftRoute(target = FINAL)` — its
    `spec`/`resolvedComponents` come from the final flows and every callback passes the
    target — titled "Final Schematic", under a persistent banner *"Final drawing — the
    original schematic is untouched"* carrying **Reset to original** and **Discard** (both
    confirm) and the final runout sheet's Print / Export actions. Carousel, add dialogs,
    preview box, collision badges and warnings work unchanged because they are presentational
    over whatever spec they are handed.
  - The toolbar PDF button opens the preview on the final (`pdfPreview?target=final`), whose
    Export forwards its OWN target (`exportPdf?target=final`). Both routes take the argument
    as OPTIONAL with default `original` (`ui/nav/SpecTargetArg.kt`: `targetArg` /
    `specTargetFromArg` — anything but `final`, a stale deep link included, resolves to
    ORIGINAL, the drawing that always exists), so every existing `navigate("pdfPreview")` /
    `navigate("exportPdf")` lands where it did. `PdfPreviewScreen` and `PdfExportRoute` take
    `SpecTarget` from that argument — never from a flag on the ViewModel — and the preview's
    render-inputs record carries it as a re-render key.
  - The final runout sheet's Print / Export live in the banner's overflow with Reset / Discard:
    `composeRunoutPdf(consolidated = false)` over the final spec and `finalResolvedComponents`,
    `runoutConfig.copy(componentOverrides = emptyMap())` (an override was authored against the
    original's components; height scale and liner compression still follow the job), readings /
    placements / wear left at their empty defaults, every value snapshotted on the UI thread
    before the print adapter runs.
  - `ShaftEditorRoute.onExportFinalPdf` and every `FinalRoute` callback are defaulted, so no
    existing host changed.
- Help → Glossary carries a "Final Schematic" topic.

## 5. Not in this step

"Create a new job from the final" (promote `final_spec` → a new document's `spec`); any
before/after diff or overlay on one sheet; wear/undercut/runout records re-keyed to the final;
a consolidated output of the final.

## 6. Tests

`persistence/FinalSpecPersistenceTest` (envelope round-trip with ids / absent / omitted key /
legacy / `decodeEnvelope` / mate duplicate / snapshot round-trip / older draft / dirty gate /
default-session), `ui/viewmodel/ShaftViewModelFinalSpecTest` (target isolation both ways,
add/remove on the final, OAL + auto-section targets, no-op while null, start copies ids,
reset/discard, newDocument / template / applyTemplate / mate exclusion, save-and-reopen,
undo of start and of a final edit), `pdf/DrawingLabelPrintTest` (footer line placement on
both branches, header line), `ui/screen/EditorTabTest` (order, label), `ui/screen/FinalRouteTest`
(Robolectric over the real VM: start button vs banner), `ui/nav/SpecTargetArgTest`,
`util/ExportFilenameTest`.
