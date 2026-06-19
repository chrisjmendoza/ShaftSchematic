ShaftScreen Contract
--------------------

Layer: UI → Screens  
Purpose: Present the shaft editor surface and bind ViewModel state to user controls.

Version: v0.7 (2026-06-18)

---

Invariants
-----------
- Model values are canonical **millimeters (mm)** at all times.  
- All unit conversion (mm ↔ in) occurs **only at the UI edge** for display and input.  
- Component list is a **unified mixed list** (no grouping) ordered newest → top  
  by `startFromAftMm` descending.  
- Text fields **commit on blur** or IME “Done”; no live ViewModel writes while typing.  
- IME padding is applied **only to the scrollable region**, not the entire screen.  
- Renderer and layout layers are **mm-only** (no unit logic in rendering or layout).

---

Responsibilities
----------------
- **Header Row:**  
  App bar providing:
  - Navigation/back
  - Editor actions (save/export/etc)
  - Settings entry point

- **Preview Card:**  
  - Fixed preview area rendering the shaft via `ShaftDrawing(...)`  
  - Optional grid overlay (user setting)  
  - Transparent or theme-color background (user selectable)  
  - “Free to end” badge aligned **TopCenter** with formatted distance and unit suffix

- **Settings (Preferences):**
  - Units (mm/in) affect labels and input formatting only (model remains mm)
  - Grid visibility in Preview
  - Preview Colors: presets (Stainless/Steel/Bronze/Transparent) + Custom palette
  - Black/White Only mode (forces black outlines and disables fills in Preview)

- **Scrollable Form Area:**  
  - Overall length field (unit-aware, commit-on-blur)  
  - Project information fields (Job Number, Customer, Vessel, Notes)  
  - Unified components list for **Body**, **Taper**, **Thread**, and **Liner**

- **Component Card:**  
  - Displays a component title such as “Body #1”  
  - Hosts a **trash-can remove icon** aligned **Top-End** within the card chrome  
  - Contains input fields (`CommitNum`) with proper unit abbreviation labels

- **Floating Action Button:**  
  - “+” icon (IME-safe positioning)  
  - Opens the Add-Chooser dialog for new components

---

Do Nots
--------
- Do **not** group components by type in the list.  
- Do **not** write model state inside the preview or renderer; render only.  
- Do **not** pre-convert inches before calling formatters (avoids “3.937 in” bug).  
- Do **not** apply IME padding globally; only the scrollable area should move.

---

Notes
------
- `spec.freeToEndMm()` provides mm; `formatDisplay(mm, unit)` converts and formats it once for display.  
- `formatDisplay()` always expects mm input.  
- Free-to-End badge text includes the unit abbreviation (e.g. “Free to end: 100 in” or “2540 mm”).  
- `ComponentCard` handles its own remove button; callers simply supply `onRemove = { … }`.  
- Persistence, serialization, and other business logic live strictly in the ViewModel.  
- Scaffold uses system-bar insets only; FAB uses `WindowInsets.ime.union(WindowInsets.navigationBars)`.

---

Future Enhancements
-------------------
- Spec-anchored grid (10 mm / 25.4 mm major spacing)  
- Adaptive preview aspect ratio  
- Drag-to-reorder components  
- User setting for preview background color or theme  
- Animated insert / remove transitions for component cards

---

Change Log
-----------
**v0.7 (2026-06-18)**
- **Pre-submit collision warnings in add dialogs:** `AddTaperDialog`, `AddLinerDialog`, and `AddThreadDialog` now call `collectAddWarnings()` before committing. If the proposed position overlaps existing tapers, non-excluded threads, or liners — or falls outside the shaft span when OAL is manual — a confirmation dialog appears listing each issue with "Add Anyway" and "Cancel" options. The add is never silently blocked. Bodies are excluded from collision checks (they auto-split). Excluded threads skip the check entirely (they live outside the shaft span by design). All three dialogs accept a new `overallIsManual: Boolean` parameter (default `false`) threaded from `ShaftScreen`.

**v0.6 (2026-06-18)**
- **Taper direction toggle in `AddTaperDialog`:** Added AFT/FWD `FilterChip` pair. Selecting "FWD" lets the user enter the FWD-face start and computes the AFT start as `OAL − startFwd − length`. SET and LET labels swap for FWD tapers so the model's `startDiaMm/endDiaMm` pair is always stored AFT → FWD. No clamping of the start position is applied.
- **Liner reference in `AddLinerDialog`:** Added "Measure From: AFT / FWD" `FilterChip` pair matching the edit card pattern. A `LinerAuthoredReference` value is passed through `onAddLiner → ShaftScreen → ShaftRoute → ShaftViewModel.addLinerAt()` so the carousel edit card reflects the correct reference after creation.
- **Carousel auto-jump fix:** `LaunchedEffect(rowsSorted.size)` in `ComponentCarouselPager` now only fires when `selectedComponentId == null`, preventing it from overriding user-initiated selections.
- **Excluded thread rendering:** `syncExcludedThreadPositions()` now places AFT excluded threads at `startFromAftMm = −lengthMm` and FWD excluded threads at `startFromAftMm = OAL`, so they appear adjacent to the shaft face rather than overlapping it. `ShaftLayout.compute()` expands `minXMm`/`maxXMm` to include these out-of-span positions.

**v0.5 (2026-05-30)**
- Fixed: selection highlight (glow) not visible on initial swipe after opening a file. `ComponentCarouselPager` now seeds selection when the component list first loads, and treats any swipe as user-initiated when no component is selected.

**v0.3 (2025-10-04)**  
- Added transparent preview option; removed forced surface background.  
- Moved all component remove buttons to **Top-End** of `ComponentCard`.  
- Corrected Free-to-End badge math and unit formatting rules (no pre-divide).  
- Reinforced commit-on-blur and IME-safe FAB behavior.  
- Updated contract structure and notes to match current implementation.  

**v0.2 (2025-09)**  
- Introduced unified component list.  
- Established canonical mm-only model and UI conversion edge rules.  
- Added grid toggle, FAB positioning rules, and overall layout hierarchy.
