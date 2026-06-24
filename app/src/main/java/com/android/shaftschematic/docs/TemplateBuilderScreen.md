# Template Builder Screen

Files: `ui/screen/TemplateScreen.kt`, `ui/viewmodel/TemplateViewModel.kt`  
Layer: UI → Screen + ViewModel  
Version: v0.1 (2026-06-24)

---

## Purpose

A stand-alone blank-template builder that lets users quickly assemble a proportional shaft schematic — without entering real measurements — and print it as a fillable PDF. The printed output shows dimension leader lines and arrows with blank write-here underlines in place of numeric values.

This screen is completely independent of the main editor. It does not share state with `ShaftViewModel`, has no file I/O, and has no undo/redo.

---

## Architecture

```
StartScreen ──navigate("template")──► TemplateScreen
                                            │
                                   TemplateViewModel  (scoped to nav back-stack entry)
                                            │
                                      ShaftSpec (in-memory, OAL = 600 mm notional)
                                            │
                               ┌────────────┴────────────┐
                          ShaftDrawing             composeShaftPdf
                       (existing renderer)       (BlankTemplate mode)
```

`TemplateViewModel` extends plain `ViewModel()` — no Application context needed, no factory.

---

## Fixed OAL

The template OAL is fixed at **600 mm** (the constant `TEMPLATE_OAL_MM`). This value is never displayed to the user; it exists only to give the renderer a coordinate system. Sliders operate in mm units but the screen never labels them as such.

---

## State

| Flow | Type | Description |
|---|---|---|
| `spec` | `StateFlow<ShaftSpec>` | In-memory shaft spec (OAL always 600 mm) |
| `selectedId` | `StateFlow<String?>` | Currently selected component ID |
| `selectedType` | `StateFlow<TemplateComponentType?>` | Derived from `spec + selectedId` |

---

## UI Contracts

### Chip bar
- Horizontal scrollable row at the top: **Add: [Body] [Thread] [Taper] [Liner]**
- Each chip appends a new component with sensible default proportions and immediately selects it (bottom sheet opens automatically).

### Drawing
- Uses the existing `ShaftDrawing` composable — no new renderer.
- `showGrid = false`, `showOalMarkers = false`.
- `highlightEnabled = selectedId != null`.
- `onTapComponentId`: selects explicit components; auto-body IDs not present in spec silently deselect.
- `onTapAtMm`: clears selection (tap on empty space).

### Bottom sheet
- Opens when `selectedId != null && selectedType != null`.
- `skipPartiallyExpanded = false` — sheet stops at half-height first so the drawing remains visible.
- Sliders per type:

| Type | Sliders |
|---|---|
| Body | Length, Diameter, Position |
| Thread | Length, Diameter, Position |
| Taper | Length, Large-end Ø, Small-end Ø, Position |
| Liner | Length, Outer Ø, Position |

- All slider ranges: Length 10–600 mm; Diameter/OD 10–200 mm; Position 0–(OAL − length).
- **Remove** button (red) at the bottom of the sheet deletes the selected component and closes the sheet.

---

## PDF Export

- Top bar **Print** icon (`Icons.Default.Print`) launches SAF `CreateDocument("application/pdf")`.
- PDF is written on `Dispatchers.IO` so the UI does not block.
- Page: US Letter landscape, 792 × 612 pt (matches the main editor export).
- Mode: `PdfExportMode.BlankTemplate` — see [PdfExportRoute.md](PdfExportRoute.md).

### BlankTemplate mode behaviour (PdfDimensionRenderer)
When `showDimensionValues = false`:
- The continuous dimension line is split into two halves with a **40 pt gap** at the centre.
- A short underline is drawn across the gap at label height — the "write here" indicator.
- Arrowheads are always inward.
- No label text is drawn.
- Body OD callouts are suppressed (`showLabels = false` guard in `ShaftPdfComposer`).
- Footer is suppressed.

---

## Default component proportions (notional mm)

| Type | Length | Diameter |
|---|---|---|
| Body | 150 mm | 80 mm |
| Thread | 60 mm | 75 mm |
| Taper | 60 mm | 80 → 60 mm |
| Liner | 100 mm | 95 mm OD |

All new components start at position 0 (AFT face). The user slides them into place.

---

## Invariants — do not break

- `TemplateViewModel` must remain independent of `ShaftViewModel`. It must never call `vm.addBodyAt()`, `vm.exportJson()`, or any other editor ViewModel method.
- The template spec OAL must remain fixed at `TEMPLATE_OAL_MM` (600 mm). Do not expose an OAL slider or allow user edits to `overallLengthMm`.
- PDF export must use `PdfExportMode.BlankTemplate`, never `Standard` or `Template`, so dimension leaders are preserved with blank underlines.
- `viewModel()` inside `TemplateScreen` is scoped to the `"template"` nav back-stack entry by the Compose Navigation runtime — do not hoist `TemplateViewModel` into `AppNav` or `MainActivity`.
