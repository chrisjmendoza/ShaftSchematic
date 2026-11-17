# Section Sheets — Contract

A **section sheet** is one stretch of the shaft printed alone, as its own document — the shop's
"FWD end finish" sketch: the liner, the body step, the taper and the thread at one end, with a
break where the shaft carries on (on-device request). A job may carry any number of them, of the
original schematic and of the Final schematic.

## 1. Model

| Field | Where | Meaning |
|---|---|---|
| `sheet_sections` | `.shaft` envelope (`ShaftDocV1.sheetSections: List<SchematicSection>`) | The job's sections. Absent = none. |
| `sheetSections` | `AutosaveManager.SessionSnapshot` (builder AND combine) | Same, in the draft ring. |

`SchematicSection(id, name, firstId, lastId, final)` (`model/SchematicSection.kt`):

- **Print metadata, never geometry.** A section names the components it runs between and nothing
  else. It reaches neither the overall length, resolution nor collision, and no typed value is
  touched by it.
- `firstId` / `lastId` are **stored component ids** — a taper, thread, liner or explicit body.
  `null` = "from the AFT end" / "to the FWD end". Bare shaft cannot be named: its identity is its
  span, which every neighbour's edit moves.
- `final` names the drawing: the original schematic, or the Final schematic. The final keeps the
  original's component ids at the copy, but the two lists are independent.
- `name` prints as the sheet's title and names its file.
- **Never pruned.** A section naming a component that no longer exists is kept; it cannot print
  until it is edited (its chip is disabled, the editor says why). The window is derived every time
  a sheet is made, so a section follows its components through every edit.
- Not in the undo history (`EditState`) — the `sheet_date_sheets` posture. New and a template
  reset the list; a mate duplicate carries the ORIGINAL drawing's sections (same ids) and drops the
  final's with the final drawing.

## 2. The window

`resolveSheetSection(spec, components, firstId, lastId, title)` (`pdf/SchematicSection.kt`, pure)
→ `SheetSection(startMm, endMm, title, coreStartMm, coreEndMm)` or `null`.

- The **core** runs from the start of one named part to the end of the other (either order; a
  body split by liners counts whole). `null` ends are the drawn shaft's ends (`drawnExtentMm`,
  so an excluded end thread is inside).
- Where the core stops short of a shaft end the window takes a **stub** of the neighbouring
  stock: 0.75 × the diameter there, and never less than a blend or seal area on the body face it
  butts against plus 0.25 × that diameter. A stub that would leave less than 0.25 × Ø before the
  shaft end runs to the end.
- Whatever a stub clips is **drawn**; a taper, thread, liner or bolt row it clips prints **no
  name, no Ø, no rail and no footer lines** (`inSectionCore`). A body's name and Ø print wherever
  the body shows — the stock in a stub is the same body.

## 3. The sheet

A section is a WINDOW on the document's own geometry, never a cropped copy: `composeShaftPdf`
takes `section: SheetSection?` (`null` = the whole shaft, byte-identical) and draws the same spec.

- **x map** — `schematicProfileFit(section = …)`: the full sheet's floors and pins cut back to the
  window, solved over the window. A window that fits the page with room to spare draws LARGER —
  until the width is used or the paper band's ceiling (`PROFILE_MAX_SHAFT_HEIGHT_PT`) — and is
  centred; it never draws smaller than the full sheet would. The "Shaft height" slider still
  raises it.
- **Cut ends** — the draw passes are clipped to `breakWindowClipPath`, so fills and outlines end
  ON the S-break each cut end strokes (the undercut strips' construction). A window edge on the
  shaft's own end is flat; the clip stands off it.
- **Rails** — `SchematicRailSpans.inSection`: the full sheet's spans and VALUES, never re-derived.
  A span prints when at least one end is on the core and the other end is either on the sheet or
  is a datum (a S.E.T. or a shaft end) the sheet does not show — that one keeps its full value
  and prints **open at the cut**: no extension line and no arrowhead there
  (`PdfDimensionRenderer(clipXMin, clipXMax)`). A span from a datum on the sheet to a part off it
  is dropped. Rails left empty close up. The overall-length rail prints only when the window holds
  both of its ends.
- **Names / Ø callouts / footer** — the section's own parts only (§2). The footer's job block is
  unchanged; an end's draw-plate holes print only where the window reaches that end.
- **Title** — `name` prints bold in the label row at the top left (the before/after sheet's badge
  row). A blank name prints none.
- Always the plain schematic of its drawing (`sectionSheetKind`): the Final tab's "Runout
  bubbles" and "Original + Final" elections are whole-shaft documents and are hidden while a
  section is on screen. A final section is still marked `Drawing: Final`.

## 4. Surfaces

- **PDF preview** (`PdfPreviewScreen`, both targets) — a sheet picker row under the app bar:
  "Whole shaft", one chip per section of this drawing, and "Sections…" (`SectionSheetsDialog`:
  add, rename, re-point, delete). The chosen sheet is the screen's own state; **Print and Export
  follow it** — the export route takes `&section=<id>`, the print job is named for the section.
- **Export all** (Consolidated Output) — with "Schematic" ticked, every section of the ORIGINAL
  drawing that resolves is written beside it as its own file.
- **Filenames** — `buildSectionFilename`: the document base, then `[_Final]_section_<name>`
  (sanitized; `section` when blank), then `_BlankDraft`.

Test tags: `section_whole_shaft`, `section_chip_<id>`, `section_sheets_button`, `section_add`,
`section_edit_<id>`, `section_delete_<id>`, `section_name`, `section_from`, `section_to`,
`section_save`.
