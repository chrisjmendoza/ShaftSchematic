ShaftScreen Contract
--------------------

Layer: UI → Screens  
Purpose: Present the shaft editor surface and bind ViewModel state to user controls.

Version: v0.9 (2026-07-18)

---

Invariants
-----------
- Model values are canonical **millimeters (mm)** at all times.  
- All unit conversion (mm ↔ in) occurs **only at the UI edge** for display and input.  
- Component carousel shows the **resolved** component list (auto-bodies included) in
  **physical position order** along the shaft. See `ComponentsOrdering.md` (v1.2).  
- Text fields **commit on blur** or IME “Done”; no live ViewModel writes while typing.
  **Exception:** the OAL field commits on every keystroke in manual mode (intentional —
  the preview updates live; see CLAUDE.md).  
- IME padding is applied **only to the scrollable region**, not the entire screen.  
- Renderer and layout layers are **mm-only** (no unit logic in rendering or layout).

---

Responsibilities
----------------
- **Header Row (TopAppBar):**  
  - Hamburger icon → opens the editor sidebar (Schematic / Runout / Wear tabs)
  - Undo/Redo history menu (`HistoryMenu`)
  - Project-Info icon
  - New / Open / Save / Export-PDF action icons
  - Overflow menu (⋮) → Settings, Clear All, etc.

- **Preview Card:**  
  - Fixed preview area rendering the shaft via `ShaftDrawing(...)`  
  - Optional grid overlay (user setting)  
  - Transparent or theme-color background (user selectable)  
  - “Free to end” badge aligned **TopStart**, shown only in manual OAL mode
    (see `FreeToEndBadge.md`)
  - Style (`PreviewCard`): `RectangleShape`, transparent container and inner Box —
    the preview draws on the screen background; colors come from preview color
    settings, not the card. Sizing: `heightIn(120–200 dp)`, `aspectRatio(3.0)`.
    Grid colors are hardcoded translucent black (`0x55000000` majors,
    `0x22000000` minors, `GridRenderer.kt`), not theme-derived.
    No px/pt math leaks into the model; IME safety is handled by the screen
    scaffold, not the preview box.

- **Settings (Preferences):**
  - Units (mm/in) affect labels and input formatting only (model remains mm)
  - Grid visibility in Preview
  - Preview Colors: presets (Stainless/Steel/Bronze/Transparent) + Custom palette
  - Black/White Only mode (forces black outlines and disables fills in Preview)

- **Scrollable Form Area:**  
  - Overall length field (unit-aware; commits per keystroke in manual mode)  
  - Project information fields (Job Number, Customer, Vessel, Notes)  
  - Component carousel for **Body**, **Taper**, **Thread**, **Liner**, and
    **Coupler Bolt Slot** (see `ComponentCarousel.kt`)

- **Component Card:**  
  - Displays a component title such as “Body #1”  
  - Hosts a **trash-can remove icon** aligned **Top-End** within the card chrome  
  - Contains input fields (`CommitNum`) with proper unit abbreviation labels

- **Add Component button:**  
  - Full-width “+ Add Component” button inside the scrollable column (not a FAB)  
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
**v0.9 (2026-07-18)**
- **Doc sweep corrections:** ordering invariant updated to resolved-pipeline physical
  order (newest-on-top superseded — see `ComponentsOrdering.md` v1.2); header row
  updated to actual TopAppBar contents (sidebar hamburger, undo/redo, project info,
  overflow menu); Free-to-End badge corrected to TopStart + manual-OAL-only; FAB
  replaced by in-column “+ Add Component” button; Coupler Bolt Slot added to the
  component list; OAL per-keystroke commit exception documented.

**v0.8 (2026-06-23)**
- **Thread AFT/FWD in Add dialog restored:** `AddThreadDialog` now shows "Thread end: AFT | FWD" chips (and hides the Start field) when `countInOal = false`, matching the carousel card. `onSubmit` signature updated to include `isAftEnd: Boolean`; threaded through `ShaftScreen → ShaftRoute → ShaftViewModel.addThreadAt()`. Contract documented in `AddComponentDialogs.md`.
- **Numeric commit guard:** `NumericInputField` now captures text at focus-gain (`textWhenFocused`) and skips `commitOrRevert()` on blur when the value is unchanged. Prevents spurious auto-body promotion and unnecessary ViewModel calls.
- **Auto-body length=1 bug fixed:** OAL field updates `spec.overallLengthMm` on every keystroke; this cycled the auto-body ID and reset `promoted` state each character. Combined with the unconditional blur-commit, the first `CommitNum` blur created a real body with the transient (1") dimensions. Fixed by the commit guard above.
- **Free-to-End badge hidden when only bodies present:** Badge now suppresses when no precision components (tapers, non-excluded threads, liners) exist and shaft is not oversized. See `FreeToEndBadge.md`.
- **OAL zero-clear:** OAL field clears to empty on focus when current value is "0".
- **Add Body defaults to remaining OAL:** In manual OAL mode, `+ Add Component → Body` pre-fills Length with `OAL − startMm`.

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
