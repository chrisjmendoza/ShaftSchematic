ShaftScreen Contract
--------------------

Layer: UI → Screens  
Purpose: Present the shaft editor surface and bind ViewModel state to user controls.

Version: v0.15 (2026-08-17)

---

Invariants
-----------
- Model values are canonical **millimeters (mm)** at all times.  
- All unit conversion (mm ↔ in) occurs **only at the UI edge** for display and input.  
- **Per-component display units** (Settings → Drawing → *Per-component units*) widen the
  edge rule without breaking it: a component may carry a display-unit override
  (`unit_overrides`, keyed by resolved component id) that decides the unit it **prints** in on
  every sheet. Overrides are a display axis only — the model stays canonical mm, and an
  override never rewrites a stored value. The carousel's `ComponentUnitChip` sets it; a
  component with no override follows the document unit.
- **Entry fields take the document unit**, on the cards and in the Add dialogs, even for a
  component whose override prints it in the other unit. **One exception:** a component's KEYWAY
  fields (W, D, L, offset) take the keyway's own unit when it has one — that chip is value entry,
  not display, precisely so a metric keyway can be typed as the whole millimetres it was specified
  in rather than round-tripped through inches. `formatDisplay`/`disp` and
  `toMmOrNull` on a card are deliberately keyed to `unit`, not to the override — mixing entry
  units per field is a follow-up (`TODO.md`), and until it lands the chip's label reads
  "Prints in:" for exactly that reason.
- **Dual-unit display** (`dual_units`) prints both units on sheets, either inline
  (`1 1/2" [38.1 mm]`) or as a two-line stack — a drawing preference, Settings → Drawing →
  "Dual-unit layout". Both terms always carry a unit suffix, in either layout: on a mixed-unit
  drawing a bare number is how a shaft gets machined wrong.
- Component carousel shows the **resolved** component list (auto-bodies included) in
  **physical position order** along the shaft. See `ComponentsOrdering.md` (v1.2).  
- Text fields **commit on blur** or IME “Done”; no live ViewModel writes while typing.
  **Exception:** the OAL field commits on every keystroke in manual mode (intentional —
  the preview updates live; see CLAUDE.md). The OAL field's **display never rewrites the
  user's own text**: a typed `150 3/4` stays a fraction rather than echoing back as
  `150.75` (on-device report). The text re-derives from the model only when the field is
  unfocused AND no longer parses to the model value (auto-mode recompute, undo, an edit
  from elsewhere).  
- IME padding is applied **only to the scrollable region**, not the entire screen, and is
  chained **before** `verticalScroll` so it shrinks the scroll viewport (not just the
  content) — this lets Compose's focused-child-in-view behavior auto-scroll a focused
  field clear of the keyboard.  
- Renderer and layout layers are **mm-only** (no unit logic in rendering or layout).

---

Responsibilities
----------------
- **Document title strip** (above the TopAppBar, desktop-editor style;
  `testTag("editor_document_title")`): shows the saved file name
  (`ShaftViewModel.currentDocumentName`, extension stripped) or **"Untitled draft"**
  when the session has never been saved, with a trailing ` *` while
  `ShaftViewModel.hasUnsavedChanges` is true. This is the visible saved-vs-draft
  indicator. The strip consumes the status-bar inset; the TopAppBar below it is given
  `WindowInsets(0, 0, 0, 0)` so the inset is not applied twice.

  The strip is the shared `EditorDocumentTitle` composable
  (`ui/screen/EditorDocumentTitle.kt`) — **every** editor tab renders it, not just the
  Schematic. See `Navigation.md` § "Document title strip". It applies no window insets of
  its own: this site passes the status-bar inset modifier, the other tabs already sit
  inside a `systemBarsPadding()` column and pass nothing. The string comes from the pure
  `editorDocumentTitleText`, so the format is asserted without a Compose harness.

- **Header Row (TopAppBar):**  
  - Hamburger icon → opens the editor sidebar (Schematic / Runout / Wear tabs)
  - Undo/Redo history menu (`HistoryMenu`) — general session-scoped undo/redo
    (`ShaftViewModel.undoEdit`/`redoEdit`, `canUndo`/`canRedo`), not delete-only;
    covers every drawing edit (spec, wear, runout readings, component order, OAL
    mode). See `ShaftViewModel.md`.
  - Project-Info icon
  - New / Open / Save / Export-PDF action icons. Export-PDF is gated by
    `ui/util/ExportPdfGate.kt` (pure, JVM-tested in `ExportPdfGateTest`): enabled only
    with ≥ 1 real component (coupler bolt slots don't count) and no collisions; a tap
    while disabled shows the gate's message as a snackbar.
  - Overflow menu (⋮) → Save As…, **Close Document** (testTag
    `overflow_close_document`; clean → closes to Start, dirty → shared unsaved-changes
    guard — see `Navigation.md`), Settings, Clear All, etc.

- **Preview Card:** (`PreviewCard`, `PreviewOalBadge`, `FreeToEndBadge` — all in
  `ui/screen/ShaftPreviewPanel.kt`, extracted from `ShaftScreen.kt` 2026-07-24, pure
  code move, no behavior change)
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
  - Project information sheet (Job Number, Customer, Vessel, Item, Shaft Position, Notes) —
    opened from the toolbar, **Save/Cancel**, not commit-on-blur (see Notes). **Item** is an
    optional shaft designation ("Tail shaft", "Line shaft"); blank is the default and prints
    nothing anywhere.  
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
- Do **not** move `imePadding()` to after `verticalScroll` in the modifier chain — that pads
  the scrolled content instead of shrinking the viewport, so the keyboard can cover a
  focused field near the bottom without triggering auto-scroll.

---

Notes
------
- `spec.freeToEndMm()` provides mm; `formatDisplay(mm, unit)` converts and formats it once for display.  
- `formatDisplay()` always expects mm input.  
- Free-to-End badge text includes the unit abbreviation (e.g. “Free to end: 100 in” or “2540 mm”).  
- `ComponentCard` handles its own remove button; callers simply supply `onRemove = { … }`.  
- `ComponentCard` also ends every card with a **Save** button (`card_save_button`). Fields
  commit on blur and IME Done, but chips/toggles/checkboxes never TAKE focus, so a typed
  value followed by a chip tap sat uncommitted (on-device report). Save force-clears focus,
  driving the one existing commit-on-blur path — no second commit pipeline, and a no-op
  when nothing is focused or nothing changed. Card-only: the Add dialogs commit through
  their own Add button. See `NumberField.md`.  
- Persistence, serialization, and other business logic live strictly in the ViewModel.  
- Scaffold uses system-bar insets only; FAB uses `WindowInsets.ime.union(WindowInsets.navigationBars)`.
- `computeAddDefaults()` lives in `ui/screen/ShaftScreenController.kt`. Shared format
  helpers (`abbr`, `disp`, `formatDisplay`, `toMmOrNull`, `parseFractionOrDecimal`,
  `tpiToPitchMm`) and the dialogs/menus remain in `ShaftScreen.kt`.
- **The Project Information sheet is a DRAFT editor, not commit-on-blur.**
  `ProjectInfoBottomSheet` holds every field in local `rememberSaveable` draft state
  (`DraftTextField` — a plain field with no blur commit; the Shaft Position dropdown too)
  and reaches the ViewModel only from **Save**, which pushes just the fields that differ
  from the document (so open-and-save with no edit never marks it dirty). **Cancel** drops
  the draft — a field that was blank goes back to blank. The old `CommitTextField`
  committed on blur, so text typed into the last field was lost whenever the sheet closed
  straight from the keyboard, and there was nothing to revert to. Unlike the numeric
  component fields, these are free text with no derived geometry behind them, so the whole
  card commits as one unit.
- **Only the IMPLICIT exits are guarded.** Swipe-down, scrim tap, and back raise a
  "Save changes?" dialog (**Save · Discard · Keep editing**) when the draft differs from
  the document, and close silently when it doesn't. Three choices, not the Material two,
  because an accidental swipe has two plausible intents — meant to close (so save) or
  fat-fingered (so return). The **Cancel button is deliberately NOT guarded**: confirming
  a deliberate discard is a second prompt for the same decision. The gate hangs on the
  sheet state's `confirmValueChange` (reading the live dirty flag through
  `rememberUpdatedState`, since the state object is created once) as well as on
  `onDismissRequest` — blocking the settle keeps the sheet in place under the dialog, so
  "Keep editing" costs no second animation and the draft is never rebuilt. `testTag`s:
  `project_info_{sheet,job_number,customer,vessel,notes,save,cancel,keep_editing,
  discard_confirm,discard_save}`; behavior pinned by `ProjectInfoSheetTest` (Robolectric,
  real swipe gesture).
- **Typed field commits are never snapped.** Carousel update callbacks
  (`onUpdateBody/Taper/Thread/Liner`) receive the committed values verbatim. The old
  `applySnapped{…}Update` wrappers (removed 2026-07-26) snapped the recomputed start/end
  to component-edge anchors (±1 mm) and silently rewrote typed values — a taper-length
  edit smaller than the tolerance was undone entirely (the start snapped back to the old
  boundary and the length recomputed to its previous value). Do not reintroduce snapping
  into any typed-commit update path — same invariant as the 2026-06-19 removal of the
  `snapForwardFrom` cascade from ViewModel updates. **Nothing in the editor snaps a position
  any longer**: the one coarse gesture that did — tap-to-add — was removed with its whole
  snap pipeline (`ui/viewmodel/SnapUtils.kt`).
- **The preview canvas tap is selection only.** A tap on a component highlights it
  (`onTapComponentId`); a tap on bare canvas does nothing. It used to open an add chooser at
  the tapped position, which fired unintentionally far more often than it was wanted and was
  never used deliberately (on-device report). Components are added from the FAB chooser,
  which is the only add entry point (`docs/UI_CONTRACT.md` §3.1.1).

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
**v0.15 (2026-08-17)**
- **Tap-to-add removed.** The preview canvas tap is selection only. The add-at-tapped-position
  chooser fired unintentionally and was never used on purpose (on-device report), so the
  gesture, its pending-position state (`setTapAddPosition`/`clearPendingAddPosition`/
  `pendingAddPositionMm`), and the entire snap pipeline it was the sole consumer of
  (`ui/viewmodel/SnapUtils.kt`, `snapRawPositionMm`, `gapToNextAnchorMm`) are gone. The FAB
  chooser is now the only add entry point; its handoff state was renamed off the dead gesture
  (`tapAdd*` → `add*`).

**v0.14 (2026-08-14)**
- **Project Information sheet gains Save/Cancel:** the sheet now edits a local draft and
  commits on **Save** (changed fields only); **Cancel** reverts. Replaces the per-field
  commit-on-blur `CommitTextField` (removed), which dropped the last field's text when the
  sheet was closed with the keyboard still up. Swipe/scrim/back dismissal with a pending
  edit raises a "Save changes?" prompt (Save · Discard · Keep editing); a clean draft
  closes silently and the explicit Cancel button is never guarded.

**v0.13 (2026-07-26)**
- **First component highlighted on open (product decision):** with components present and
  highlighting enabled, opening/creating a document seeds the selection to the FIRST
  carousel card (AFT-most component) and scrolls to it — the highlight is visible
  immediately, not only after a swipe or preview tap. An orphaned selection (id no longer
  resolves — auto-body ids regenerate on every edit) self-heals by adopting the current
  page without scrolling. Decisions are pure + pinned: `seedSelectionAction` and the
  orphan arm of `isUserInitiatedScroll` in `CarouselSelectionSync.kt`.
  `importJson`/`newDocument`/draft-restore clear the selection so the seed always runs on
  a session boundary. No highlight only when the toggle is off or the shaft is empty.

**v0.12 (2026-07-26)**
- **Typed commits unsnapped:** removed the `applySnapped{Body,Taper,Thread,Liner}Update`
  wrappers and the `snapAnchors` plumbing; carousel update callbacks are wired directly.
  Field edits within ~1 mm of a component edge were being silently reverted (worst on
  FWD-referenced taper length edits). Tap-to-add snapping unchanged.

**v0.11 (2026-07-26)**
- **HistoryMenu is general undo/redo, not delete-only:** the header-row history menu now
  wires to `ShaftViewModel.undoEdit()`/`redoEdit()` (session-scoped, covers every drawing
  edit), replacing the old delete-only `undoLastDelete()`/`redoLastDelete()`. Menu item
  labels stay "Undo"/"Redo"; the delete snackbar's "Undo" action also now calls
  `undoEdit()`. See `ShaftViewModel.md`.

**v0.10 (2026-07-24)**
- **Preview panel + controller extraction:** `PreviewCard`/`PreviewOalBadge`/
  `FreeToEndBadge` moved to new file `ui/screen/ShaftPreviewPanel.kt`;
  `computeAddDefaults`/`applySnapped{Body,Taper,Thread,Liner}Update`/`snapBounds` moved
  to new file `ui/screen/ShaftScreenController.kt`. Pure code-motion, zero behavior
  change; `ShaftScreen.kt` 1452 → 1314 lines. Shared format helpers and dialogs/menus
  stay in `ShaftScreen.kt`.

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
