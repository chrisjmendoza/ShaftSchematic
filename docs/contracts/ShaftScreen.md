ShaftScreen Contract
--------------------

Layer: UI → Screens  
Purpose: Present the shaft editor surface and bind ViewModel state to user controls.

Version: v0.27 (2026-11-10 — the start card: Overall Length, Shaft diameter and the project
information are the carousel's first card; the OAL row and the Project information sheet are gone.
v0.26 2026-10-29 — the explicit-body card and Add Body carry "Measure From: AFT | FWD".
v0.25 2026-10-29 — the thread card's "Include thread in OAL" switch asks whether the
OAL follows. v0.24 2026-10-29 — the EXPANDED drawing band grows to its side column under the cap
and yields height to the keyboard; the editor snackbar clears the navigation bar; the delete
snackbar's Undo returns the carousel to the restored card. v0.23
2026-10-28 — the warning banner names each problem part, lists overlaps, and
each line selects its part; the preview marks the same problems in red; an OAL change says what
it moved. v0.22 2026-10-28 — card fields refuse what they cannot apply through their validator,
never in the commit lambda; overlap messages name the partner by its card title; a Length is
checked for overlap like a Start; the banner and chip name the END a component runs past.
v0.21 2026-10-27, v0.20 2026-10-27, v0.19 2026-10-23, v0.18 2026-09-16, v0.17 2026-08-29)

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
- **Auto-body Ø scope.** The Body (auto) card's Ø commit chooses its scope. With
  `autoSectionCount` ≥ 2 (AUTO `ResolvedBody` rows, counted ONCE in `ComponentCarouselPager`
  and passed down through `ComponentPagerCard`) it opens "Apply diameter to…"
  (`autobody_dia_scope_dialog`): **This section** (`autobody_dia_this_section`) →
  `onSetAutoSectionDia(start, end, v)`; **All auto sections** (`autobody_dia_all_sections`) →
  `onSetAllAutoSectionsDia(v)` (`ShaftViewModel.setAutoBodyDiaMm`, which clears every section
  override); **Cancel**, back or an outside tap applies nothing and re-keys the field
  (`key(diaFieldResetNonce)`) so it shows the STORED value — a value that was not applied never
  stays in the box. The field's focus is cleared as the dialog opens, so the blur that IME Done
  leaves pending cannot re-commit after a choice or from the field the re-key disposes; that
clear parks focus on the window root (`FocusParkingRoot`, `NumberField.md` §"Focus release"), so
closing the dialog never drops the cursor and keyboard into another field. With
  one auto section the value is the shaft's Ø and goes straight to `onSetAllAutoSectionsDia`.
  Covered by `AutoBodyDiaScopeDialogTest`.
- **The start card is always the carousel's first page** (`ShaftStartCard`,
  `ui/screen/StartCard.kt`, title "Shaft & job", `testTag("start_card")`) — on the Schematic and
  Final tabs, at every width class (on-device request: the always-on OAL row nagged; a job starts
  with its OAL, its shaft size and its project information, and then the shaft is built). Top to
  bottom: **Overall Length** (`overall_length_field`), **Shaft diameter**
  (`start_card_shaft_dia`) with the note that a section of bare shaft takes its own Ø on its card
  (`start_card_dia_note`), then **Project information** — Job Number, Customer, Vessel, Item
  (optional), Shaft Position, Notes (`project_info_{job_number,customer,vessel,item,position,notes}`).
  It is NOT a component: no resolved id, no remove button, rename editor or unit chip; nothing
  deletes, reorders or pushes it out of first place. The pager's page 0 is the start card and
  component row `i` is page `i + 1`; `carouselSelectedPage` / `componentIdForPage`
  (`CarouselSelectionSync.kt`) are the ONE conversion, and a **null selection is the start card**.
  So a new or opened document (the ViewModel clears the selection at every session boundary) lands
  on it — replacing the "first component highlighted on open" seed (v0.13); a swipe onto it selects
  nothing; a preview tap, an add (the VM selects the new id), a warning line and a delete's Undo
  select a component and the carousel goes to its card; an orphaned selection adopts the page the
  carousel is on. The toolbar's Project information button selects null through the drawing tap's
  ask-first path, so it goes to the start card (and a pending card edit asks first). The geometry
  fields are bound to the editor's target (`ShaftRoute(target)` → `onSetOverallLengthMm`,
  `onSetShaftDiaMm`); project information is the document's. Field rules: `OverallLength.md`.
  Pinned by `StartCardTest`, `CarouselSelectionSyncTest`.
- **Thread card: "Include thread in OAL" asks.** Tapping the row (`thread_include_in_oal`) with
  an OAL typed and a thread length > 0 opens a prompt instead of flipping — "Exclude thread from
  OAL" offers **Shorten OAL**, "Include thread in OAL" offers **Lengthen OAL**, each beside
  **Keep OAL** and **Cancel** (`thread_oal_adjust` / `thread_oal_keep` / `thread_oal_cancel`), the
  old and new OAL named in the document unit. The switch shows the stored state until a choice
  is made; Cancel or an outside tap applies nothing. With no OAL or no thread length the switch
  flips at once (`adjustOal = false`). The card calls `onSetThreadExcludeFromOal(id,
  excludeFromOAL, adjustOal)` → `ShaftViewModel.setThreadExcludeFromOal` (rules, one undo step and
  the move snackbar: `OverallLength.md` §"Thread inclusion prompt"). Covered by
  `ThreadOalPromptCardTest`. A confirmation, not a control, so `AddThreadDialog` carries nothing.
- Text fields **commit on blur** or IME “Done”; no live ViewModel writes while typing.
  The start card's Overall Length is an ordinary card field (`CommitNum`) under the same rule —
  never per keystroke (deleting one digit on the way from 368.5 to 367.75 committed a transient
  36″ shaft and left a re-anchored taper's residue behind, on-device report): IME Done, a blur
  after the text changed since focus (the `BlurCommitPolicy` baseline rule), or the card Save
  bar's ✓, each exactly once; the bar's ✗ restores the stored value and commits nothing. An
  **empty** or unparseable entry commits nothing, restores the stored value under the shared
  `"<typed> not applied — not a number"` line, and never zeroes the shaft. Inch values read back
  through `inchFieldText` (fractions on the 1/32 grid). The project information fields
  (`CommitTextField`) commit on blur too and register with the Save bar like the numeric fields.
  See `OverallLength.md`.  
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

  The strip is also **tappable** — it is the document's naming affordance: a tap names an
  unsaved document (the Save As screen) or renames a saved one, decided once in `AppNav` and
  plumbed here as `onTitleClick` exactly as `onSave` is, so all five tabs behave identically.

- **Header Row (TopAppBar):**  
  - Hamburger icon → opens the editor sidebar (Schematic / Runout / Wear tabs)
  - Undo/Redo history menu (`HistoryMenu`) — general session-scoped undo/redo
    (`ShaftViewModel.undoEdit`/`redoEdit`, `canUndo`/`canRedo`), not delete-only;
    covers every drawing edit (spec, wear, runout readings, component order). See
    `ShaftViewModel.md`.
  - Project-Info icon (`toolbar_project_info`) → goes to the start card, where the project
    information is edited (selects null through the drawing tap's ask-first path)
  - New / Open / Save / Export-PDF action icons. Export-PDF is gated by
    `ui/util/ExportPdfGate.kt` (pure, JVM-tested in `ExportPdfGateTest`): enabled only
    with ≥ 1 real component (coupler bolt slots don't count) and no collisions; a tap
    while disabled shows the gate's message as a snackbar.
  - Overflow menu (⋮) → Save As…, **Close Document** (testTag
    `overflow_close_document`; clean → closes to Start, dirty → shared unsaved-changes
    guard — see `Navigation.md`), **Preview options…** (`overflow_preview_options` — the preview's
    device switches and the shared look settings it reads, `UI_CONTRACT.md` §3.1.1 "Preview
    options"), Settings, Clear All, etc.

- **Preview Card:** (`PreviewCard`, `PreviewOalBadge` — both in
  `ui/screen/ShaftPreviewPanel.kt`, extracted from `ShaftScreen.kt` 2026-07-24, pure
  code move, no behavior change)
  - Fixed preview area rendering the shaft via `ShaftDrawing(...)`  
  - Optional grid overlay (user setting)  
  - Grid stands down while the sheet layout is in effect (a millimetre grid means nothing on a
    compressed map).
  - Sheet layout (user setting, default ON): the shaft drawn like the schematic sheet — the
    sheet's compressed x map, diameter scale and S-breaks, through the sheet's own fit pipeline
    and body pass; linear while the OAL is 0 or a part hangs past an end — see `UI_CONTRACT.md`
    §3.1.1 "Sheet layout in the preview".
  - Dimension lines above the shaft (user setting, default ON): the schematic sheet's own
    rails and values over the preview's own x map — see `UI_CONTRACT.md` §3.1.1 "Dimension
    lines in the preview". While they draw, the developer OAL badge is hidden.
  - Reset-view button (top-right) shown only while the preview is zoomed or panned off its
    default view (`isPreviewViewMoved`); double-tap still resets.
  - Transparent or theme-color background (user selectable)  
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
  - Dimension lines in Preview (`showPreviewDimensionsFlow`, device-only, default ON)
  - Draw Preview Like the Sheet (`previewSheetLayoutFlow`, device-only, default ON), with one
    line saying fractions, arrows and S-breaks in the preview follow Settings → Drawing
  - Preview Colors: presets (Stainless/Steel/Bronze/Transparent) + Custom palette
  - Black/White Only mode (forces black outlines and disables fills in Preview)

- **Scrollable Form Area:**
  - The card Save / Cancel slot (`CardSaveSlot`) — empty while every card field is settled
  - Component carousel: the **start card** first (Overall Length, Shaft diameter, Project
    information — see the invariant above; **Item** is an optional shaft designation ("Tail
    shaft", "Line shaft"); blank is the default and prints nothing anywhere), then **Body**,
    **Taper**, **Thread**, **Liner**, and **Coupler Bolt Slot** (see `ComponentCarousel.kt`)

- **Component Card:**  
  - Displays a component title such as “Body #1”  
  - Hosts a **trash-can remove icon** aligned **Top-End** within the card chrome  
  - Contains input fields (`CommitNum`) with proper unit abbreviation labels

- **Add Component button:**  
  - Full-width “+ Add Component” button inside the scrollable column (not a FAB)  
  - Opens the Add-Chooser dialog for new components

---

Window width
------------
Window width is the editor's **one** adaptive axis (`ui/adaptive/WindowSize.kt`,
`currentWindowWidthClass()`); the policy for the app as a whole lives in `Adaptive.md`. Height
is never a class here. The Final Schematic tab hosts this same screen through
`ShaftRoute(target = FINAL)`, so it inherits every branch below.

- **COMPACT** (< 600 dp) — the phone layout, unchanged: one column with the preview card pinned
  under the app bar (`heightIn(min 120, max 200.dp)`, `aspectRatio(3f)`), the card Save / Cancel
  slot, and a single scrolling column holding the Components heading, the "+ Add Component" button, `SpecWarningBanner`, and the carousel.
- **MEDIUM** (600–839 dp: a portrait tablet, a phone in landscape) — the same single column,
  with the preview card's height ceiling raised to **280 dp** (the 3:1 ratio and the 120 dp
  floor stand, so a wide window stops drawing the shaft as a flat strip) and the **carousel
  filling the column's leftover height**: the phone's fixed `CAROUSEL_HEIGHT` (360 dp) left the
  card's Diameter field cut in half above an empty quarter of a portrait tablet (on-device
  report). The carousel sits in a box (`testTag("editor_medium_carousel")`) whose height is the
  pure `mediumCarouselHeight` (`ui/screen/EditorTabletLayout.kt`): the scroll viewport less
  everything above the carousel, measured off the column itself, never under 360 dp — a short
  window scrolls rather than squeezing the card — and the carousel fills it with the same
  `fillHeight` the EXPANDED region uses. The viewport is read outside the IME padding, so the
  keyboard scrolls the column instead of shrinking the card. Nothing splits: a MEDIUM window
  in two would leave both halves narrower than a phone.
- **EXPANDED** (≥ 840 dp: a landscape tablet) — a **drawing band across the top** and a
  **peeking carousel** filling the height under it. A shaft is long and thin, so the drawing
  takes the WIDTH; the side-by-side panes this replaced squeezed it into a 55% column as a
  flat strip while the cards on the right were clipped at the phone's fixed height (on-device
  report from the first tablet pass).
  - **The band** (`testTag("editor_pane_preview")` on the drawing box): a `Row` of the preview
    card, `weight(1f)`, and a **side column** holding the card Save / Cancel slot (empty while
    every card is clean), the "+ Add Component" button, and `SpecWarningBanner`. The column is the
    controls' width (`EXPANDED_SIDE_COLUMN_WIDTH_DP`, 260 dp) plus the Save bar's 96 dp ✓/✗ action
    slot; the bar's text takes the rest of the row, and the button and the warnings fill the
    column.
    The drawing's height is the pure `expandedDrawingBandHeightDp` (`ui/screen/EditorTabletLayout.kt`):
    the preview's shape over the width left beside the side column, clamped to the 120 dp
    floor and the **320 dp** ceiling. The BAND is the larger of that and the side column's
    measured content height, under the same ceiling (`expandedBandHeightDp`), so two warnings
    read whole instead of the second being clipped beside spare paper (on-device report). The preview is **4:1** there (`EXPANDED_PREVIEW_ASPECT`),
    not the column's 3:1: the band is width-bound, so the shaft draws at the same width either
    way and a 3:1 box was blank paper above and below it — height the cards can use. Past the
    ceiling the side column scrolls INSIDE the band, with a fade at its foot while it can scroll
    further, so a long warning list can never push the carousel below the ceiling. With the
    keyboard up the band yields height so the component region keeps 220 dp
    (`expandedBandHeightUnderImeDp`, never under the drawing's floor), and the preview narrows
    to keep its shape (`expandedPreviewMaxWidthDp`) — see `Adaptive.md`.
  - **The carousel region** (`testTag("editor_pane_components")`, `weight(1f)`): the
    Components heading with the hint **"Tap or swipe to select"**, then the carousel with
    `fillHeight = true` and `peekPageWidth = CAROUSEL_PEEK_PAGE_WIDTH_DP` (520 dp): the
    current card sits CENTRED at that width and the previous/next cards **peek** in on either
    side, faded to `CAROUSEL_PEEK_NEIGHBOUR_ALPHA` (0.45) and shrunk to
    `CAROUSEL_PEEK_NEIGHBOUR_SCALE` (0.94) one page off centre, interpolated from the pager's
    own offset fraction so a swipe cross-fades them. A **peeking card is a tap target, not an
    editor**: a transparent overlay (`carousel_peek_<page>`) takes the tap and animates that
    card to the centre (the pager's swipe passes through it), so a field on a faded card can
    never take focus from the one being edited; the selection follows exactly as a swipe's
    does (`CarouselSelectionSync`). A pager narrower than the card shows one whole card and no
    peek (`peekPageWidthDp`). The region carries `imePadding()` — it holds the text fields —
    and the current card scrolls inside itself.
  - **Tablet cards set paired fields two to a row.** The region provides
    `LocalCardFieldsTwoUp = true` (`ComponentCarousel.kt`), and the cards wrap the fields that
    read as a pair in `FieldPair(first, second)`: Start / Length on the body (auto and explicit),
    taper, liner and bolt-slot cards, the taper's two diameters, the thread's Major Ø / TPI,
    and each liner shoulder's length / Ø. On a phone the local is false and `FieldPair` emits
    the two fields straight into the card column — no wrapper, no extra spacing — so the phone
    card is laid out exactly as before; on a tablet they share a row at `weight(1f)` each,
    keeping the full 56 dp touch height (on-device request: a full-width box for a few
    characters wasted the card). Lone values, conditional fields, and rows that already pair
    (KW W × D) are left alone.
  - The band and the region keep the editor's 16 dp horizontal padding.

**Both branches emit the SAME content blocks.** `ShaftScreen` declares them once
(`previewBlock(maxHeight, aspect)`, `startCard(padding)` — handed to the carousel as its first
page — `componentsHeader(hint)`, `addComponentButton`, `carouselBlock(peekPageWidth,
fillHeight)`; the Save slot is the shared `CardSaveSlot`) and each branch only decides
where they go — composable lambdas rather than private composables because a private one would
have to restate the screen's parameter list (the carousel alone takes fifty callbacks), and a
signature copied twice is exactly the drift this prevents. The single column stacks the last
three through `componentsBlock(warningSlot, carouselHeight)` — header ("Swipe to select"),
button, the `SpecWarningBanner` in the slot, the carousel with one card filling the pager (at
`CAROUSEL_HEIGHT` on COMPACT, in the filled box on MEDIUM) — so **COMPACT is byte-identical to
before the tablet layout existed**; the EXPANDED branch places the same blocks separately.
Every `testTag` survives in both branches.

**The carousel and the warning banner are movable content** (`movableContentOf`), because they
are the blocks a rotation across the EXPANDED boundary MOVES between branches.
`rememberSaveable` keys its values by composition path, so a block composed from a different
branch per width class would read none of the other branch's saved values — a card's typed
text and scroll, an open add dialog's fields, a dismissed warning would reset on a rotation —
and the unread values, saved again, would come back a rotation later. Movable content roots
the path at itself rather than at the branch hosting it. Pinned by `EditorRotationStateTest`
(in place and across a recreation). Rooting the path there also takes it outside the editor's
`key(resetNonce)`, so each block re-enters `key(resetNonce)` INSIDE itself — without that, state
saved while the editor was off screen (the Open screen) was restored into the next document: the
start card opened scrolled to the previous card's offset, title cut off, and a dismissed warning
stayed dismissed (pinned by `EditorDocumentChangeStateTest`). Separately, every card
(`ComponentCard`) gives back the scroll the keyboard took when its viewport grows again
(`CardKeyboardScrollUndo`: up by the height regained, never above the pre-keyboard offset, never
down). The tab itself has the same problem one level up, solved
in `ShaftEditorRoute` (`Adaptive.md`).

The toolbar's hamburger (`testTag("toolbar_menu")`) is **hidden** when
`LocalSidebarPermanent.current` is true — in an EXPANDED window the sidebar is already laid out
beside the content, so the button would open a panel that is on screen. See `Navigation.md`.

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
- `formatDisplay(mm, unit)` converts and formats a value once for display; it always expects
  mm input.  
- `ComponentCard` handles its own remove button; callers simply supply `onRemove = { … }`.  
- On the phone and MEDIUM single-column layouts the card Save / Cancel slot stands ABOVE the
  scrolling editor column, not inside it, so the bar stays on screen with the keyboard up
  (on-device report: inside the column the bar scrolled above the keyboard with the focused
  field). EXPANDED keeps it in the side column beside the drawing.
- While a field on a `ComponentCard` (the start card's included) holds an edit that has not
  landed, the **slot above the carousel shows the card Save / Cancel bar** (`CardSaveSlot` →
  `CardSaveBar`; tags `card_save_bar` / `card_save` / `card_cancel`): "Unsaved change" over the
  waiting field and its card, and the **green ✓ / red ✗** (`ui/input/AcceptCancelButtons.kt`) at
  its end. ✓ commits through the field's own handle and drops the keyboard, ✗ puts the stored
  value back and commits nothing. With every field settled the slot takes **no room at all**; the
  bar grows in and shrinks out (`AnimatedVisibility`, height + fade, 180 ms) so the cards and the
  drawing band below slide instead of jumping the moment a field turns dirty (on-device request) —
  reserving its height would hold a row open over every clean card (the standing OAL row the start
  card replaced), and overlaying it would cover the top of the scrolled card where the focused
  field often sits. While it shrinks out its buttons do nothing (the edit was answered). Why
  above the carousel: a Save at the end of a long card was hidden (on-device ruling), and a pair
  inside each field hid the value in a half-width field (on-device report). Fields still commit
  on blur and IME Done; the bar is there for the case those miss — chips/toggles/checkboxes never
  TAKE focus, so a typed value followed by a chip tap sits uncommitted (on-device report), and the
  bar stays until it is answered. Fields register through `PendingCardEdits`
  (`ui/screen/PendingCardEdit.kt`), provided once around the editor; the Add dialogs commit through
  their own Add button and register nothing. Commit-on-blur is otherwise **completely unchanged**.
  **A card change under a pending edit asks first** — a carousel swipe, an edge arrow, a tap on a
  peeking card, a tap on a component in the drawing (or a warning line), or the toolbar's Project
  information button opens "Save changes?" (`UnsavedCardEditDialog`: Save | Discard | Keep
  editing); Keep editing after a swipe scrolls the pager back to the card holding the edit, since
  the selection never moved. The hidden field would otherwise keep focus and the keyboard, and
  typing would land on a card the user cannot see (on-device ruling). See `NumberField.md` and
  `OverallLength.md`.
- **Card fields carry two card-level validations, and neither rewrites a stored value.**
  A card whose component runs past `spec.overallLengthMm` shows the amber advisory chip
  "Extends past shaft length (OAL *n*")" (the OAL in the document's display unit, inch or
  mm, through `formatLenWithUnit`) — the SAME `outsideShaftSpan` predicate the add
  dialogs' bounds warning uses (`ui/util/ComponentWarnings.kt`; excluded threads skipped,
  bodies included). It is advisory, not the error chip: oversize is legal (`OverallLength.md`),
  the edit still commits verbatim, and neither `collidingIds()`, the export gate, nor the OAL
  field's own red state is touched. Above the carousel, `SpecWarningBanner` adds a NAMED line
  for the same condition (see the banner bullet below). **The wording names the END**: a span
  that starts before 0 reads "Extends past the AFT end of the shaft" on its card and "*Part*
  starts *d* before the AFT end" on the banner; one that runs beyond the OAL keeps "past shaft
  length" on the card and reads "*Part* runs *d* past the FWD end" on the banner. "Past shaft length"
  for a part hanging off the AFT end (a component whose start was typed before 0) read as wrong
  — emulator QA. The two halves (`startsBeforeAftEnd`,
  `endsPastOal`) ARE `outsideShaftSpan` (it is their OR); they exist only to name the end.
  Separately, the **Length** field rejects `≤ 0` ("Must be > 0", `positiveLengthErrorMm`)
  through the standard validator path — the field errors, reverts, and does not commit, so a
  zero length stops at the card. See `VALIDATION_RULES.md` §3.1a.  
- **The warning banner names the parts, and each line goes to its part.** `specIssues(spec, unit)`
  (`ui/util/SpecIssues.kt`) is the ONE problem list behind three surfaces — the banner, the
  preview's red marks, and the export gate's overlap message (`overlapExportMessage`, used by
  `blockingExportError` and `exportPdfGate`) — so they cannot name different parts. Kinds, in
  banner order: OVERLAP (one line per colliding pair, from `collidingIds`/`collisionPartnerIds`,
  the pairs the cards' "Overlaps …" chip names; bodies never collide) — "FWD Liner overlaps Body
  size transition taper", the sleeve named first (liner, then thread, then taper; AFT first
  within a kind); PAST_FWD_END — "Body #1 runs 4 13/16" past the FWD end"; PAST_AFT_END — "AFT
  Thread starts 2" before the AFT end"; TINY_SEGMENT — "Body #2 is shorter than 1 mm". Within a
  kind, AFT → FWD. Names are `componentCardTitles` (user labels win); distances print in the
  document unit through `formatLenWithUnit` (fractions in inches). Each line is a ≥ 48 dp
  `Role.Button` with a "Show" chevron that calls `onSelectComponentById` with the first named
  part — the preview tap's selection path, which the carousel follows
  (`ComponentCarouselPager`'s selection-following effect). More than `VISIBLE_ISSUE_LINES` (3)
  problems show three and a "+N more" row that expands (view state only). Dismissal is unchanged:
  keyed to the message set. A count line ("1 component extends past shaft length") left the
  user unable to tell which part was wrong — on-device report. ONE implementation hosted by all
  three width classes (the `warningBanner` movable content).
- **The preview marks the problems.** `ShaftScreen` builds `previewIssueMarks(specIssues(…))`
  (`ui/drawing/render/PreviewIssueMarks.kt`) and passes it through `PreviewCard` → `ShaftDrawing`
  → `RenderOptions.issueMarks`: each overlapping pair's shared stretch draws as a translucent red
  band with a red outline over the silhouette there, and each past-end overhang is red-tinted the
  same way with the real shaft end dashed across the content height (the OAL end stays where it
  is). Bands take a 10 px drawn floor so a short overlap is visible. The window widens to hold
  the marks (`ShaftLayout.compute(extentMm)`), never the OAL. The ink is a fixed warning red
  (`PREVIEW_ISSUE_INK`) that reads on the light, dark and high-contrast previews. Every other
  canvas passes no marks and no extent and is byte-identical; no PDF composer reads
  `RenderOptions`.
- **A card field refuses what it cannot apply through its `validator`, never in the commit
  lambda.** A lambda that early-returned on a value it would not apply left the typed text on
  screen over the stored value with no word of it (emulator QA: a relief Ø of 0 "kept" until the
  editor was reopened). Every such rule is the field's validator, so the field reverts and shows
  "<typed> not applied — <reason>" (`NumberField.md`): explicit body Ø, liner OD, taper
  S.E.T./L.E.T. Ø, thread major Ø, TPI, relief length/Ø, bolt-hole Ø, spacing and depth "Must be
  > 0" (`positiveDiaErrorMm`/`positiveLengthErrorMm`); the captured-keyway inset "Must be > 0"
  (`capturedInsetErrorMm` — un-capturing is the toggle's job, as removing a relief is the
  switch's); the bolt-slot Count "Must be a whole number ≥ 1" (`boltSlotCountError` — never
  coerced to 1); a bolt row measured from FWD that would reach past the AFT face "Row extends
  past the AFT end of the shaft" (it was clamped onto the face). The AUTO-body Ø stays ungated
  (≤ 0 clears that section's override). A value that passes is committed verbatim.
- **Overlap is checked on the span, whichever field grew it.** A liner's or thread's Length
  field runs the same overlap rule as its Start (`spanOverlapErrorMm` — another liner / thread,
  the start/length validator posture), so a Length of 99999 no longer lands a liner across every
  liner FWD of it while the same span as a Start was refused. Running past the OAL is NOT
  refused (the advisory above — an OAL shrink makes that state legitimately). Tapers have no
  same-kind refusal on either field; their overlaps show on the chip. Bolt slots never collide.
- **Overlap messages name the partner by the title its card shows** (`componentCardTitles` —
  the four card-title builders, a user label winning): the card chip (`collisionChipMessage`,
  every partner by `collidingIds`' own pair rule, `collisionPartnerIds`), the field refusal and
  the Add dialogs' inline line and "Add Anyway?" confirm all say "Overlaps FWD Taper" (several:
  "Overlaps AFT Liner, FWD Taper"). "Overlaps another component" and "Overlaps Taper 1" left the
  user guessing which card that was. The export gate's own message is unchanged.  
- Persistence, serialization, and other business logic live strictly in the ViewModel.  
- Scaffold uses system-bar insets only; the scroll column takes `imePadding` so the focused
  field (and the "+ Add Component" button beneath the carousel) stays above the keyboard.
- `computeAddDefaults()` lives in `ui/screen/ShaftScreenController.kt`. Shared format
  helpers (`abbr`, `disp`, `formatDisplay`, `toMmOrNull`, `parseFractionOrDecimal`,
  `tpiToPitchMm`) and the dialogs/menus remain in `ShaftScreen.kt`.
- **Project information is edited on the start card** (the Project Information bottom sheet, its
  draft/Save/Cancel and its "Save changes?" swipe guard are gone with it). Each free-text field is
  a `CommitTextField`: typing changes only the box; the text reaches its ViewModel setter on a
  blur after it changed since focus (single-line fields' IME action is Next, which moves on and so
  commits) or from the card Save bar, and while it holds an unsaved value it registers with
  `PendingCardEdits` like the numeric fields — the bar shows, and a card change asks Save |
  Discard | Keep editing, so text typed into the last field is never lost to a swipe (the failure
  the sheet's draft existed to prevent). The Shaft Position dropdown commits on selection. Pinned by
  `StartCardTest`.
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
  never used deliberately (on-device report). Components are added from the "+ Add Component"
  button's chooser (a full-width `Button` in the scroll column, not a FAB),
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
**v0.27 (2026-11-10)**
- **The start card** (on-device request: "Having OAL hanging out constantly has been nagging at
  me"): Overall Length, a Shaft diameter field (shaft-wide, keeps section overrides) and the project
  information are the carousel's first page on the Schematic and Final tabs. The OAL row
  (`OverallLengthRow` / `OverallLengthField`, its own ✓ / ✗), the Project Information bottom sheet
  (`ProjectInfoBottomSheet`, `ProjectInfoSheetTest`) and the first-OAL "Shaft diameter" prompt
  (`ShaftDiaPrompt.kt`) are removed. The slot above the carousel shows only the card Save / Cancel
  bar, animated in and out; the toolbar's Project information button goes to the start card. A new
  or opened document lands on the start card (supersedes v0.13's first-component seed).

**v0.26 (2026-10-29)**
- **Explicit bodies carry "Measure From: AFT | FWD"** (on-device request: an explicit body exists
  to sit at an exact point). The chips (`body_ref_aft` / `body_ref_fwd`) sit under the "Explicit
  body" checkbox and above Start/Length; Start reads "Start from AFT|FWD" and commits the physical
  start; a Length commit under FWD keeps the FWD end (`bodyPhysStartForNewLength`). Wired
  `onUpdateBodyReference → updateBodyAuthoredReference`; `onAddBody` takes the reference after Ø.
  `AddBodyDialog` mirrors the row (parity). The auto-body card carries none.

**v0.25 (2026-10-29)**
- **"Include thread in OAL" asks whether the OAL follows** (on-device report: an OAL measured over
  the FWD thread, the thread marked out of it). Shorten/Lengthen OAL, Keep OAL or Cancel; an
  included thread lands inside the shaft at its end. See the invariant above and
  `OverallLength.md` §"Thread inclusion prompt".

**v0.24 (2026-10-29)**
- **EXPANDED band = max(drawing, side column) under the 320 dp cap**, with a foot fade when the
  side column scrolls; **the band yields to the keyboard** so the focused card keeps its field,
  label and notice in view (on-device reports from the tablet pass). Pure numbers in
  `EditorTabletLayout.kt`, pinned by `EditorTabletLayoutTest`.
- **The editor snackbar pads for the navigation bar** (the Scaffold's content insets are
  horizontal only, so the gesture handle drew across the message — on-device report).
- **The delete snackbar's Undo selects the restored component** (`ShowDeletedSnack.componentId`
  → `ShaftViewModel.undoDeletion`), so the carousel animates back to its card through the
  ordinary selection-follow effect; a stale Undo selects nothing (on-device ruling: after
  "Thread deleted — Undo" the carousel stayed on the neighbouring page). See `ShaftViewModel.md`.

**v0.21 (2026-10-27)**
- **MEDIUM carousel fills the column** (`mediumCarouselHeight`, floor `CAROUSEL_HEIGHT`) —
  on-device report: the portrait tablet's card was clipped above ~330 dp of empty screen.
  COMPACT unchanged.
- **Carousel and warning banner are movable content**, so their saved state survives a rotation
  between the width branches (see "Window width"). Pinned by `EditorRotationStateTest`.

**v0.20 (2026-10-27)**
- **Auto-body Ø scope dialog** ("This section | All auto sections", on-device report: one
  auto section's edit changed the others) and the **new-shaft diameter prompt** after the first
  OAL (on-device request). See the two Invariants bullets above.

**v0.19 (2026-10-23)**
- **EXPANDED re-laid out: drawing band + peeking carousel.** The side-by-side panes squeezed
  the shaft into a 55% column as a flat strip while the card on the right was clipped at the
  phone's fixed carousel height (on-device report from the first tablet pass). The drawing now
  takes the full width across the top (4:1 box, height from the pure
  `expandedDrawingBandHeightDp`) with the OAL / add button / warnings in a 300 dp side column
  that scrolls inside the band; the carousel fills the height under it with the current card
  centred at 520 dp and its neighbours peeking faded and shrunk at the sides, each a tap target
  that brings it to the centre (`ComponentCarouselPager(peekPageWidth, fillHeight)`, numbers in
  `ui/screen/EditorTabletLayout.kt`, pinned by `EditorTabletLayoutTest`). The components block
  is split into header / add button / carousel lambdas so the phone column composes the same
  three in the same order — COMPACT and MEDIUM are byte-identical. Tablet cards set paired
  fields two to a row (`FieldPair` / `LocalCardFieldsTwoUp`), and the OAL box, add button and
  warnings in the side column share one width.

**v0.18 (2026-09-16)**
- **Window width branches added.** COMPACT is byte-identical to before. MEDIUM raises only the
  preview card's height ceiling to 280 dp. EXPANDED lays the editor out as two scrolling panes
  — preview / OAL / warnings on the left (`editor_pane_preview`), components on the right
  (`editor_pane_components`) — and drops the toolbar hamburger, since `ShaftEditorRoute` places
  the sidebar permanently beside the content there. The content blocks are declared once and
  invoked by both branches, so the phone and tablet layouts cannot drift. Pinned by
  `ShaftScreenTwoPaneTest`. See "Window width" and `Adaptive.md`.

**v0.16 (2026-08-29)**
- **Free-to-End badge removed.** The preview overlay is gone, along with
  `ui/util/FreeToEndBadgeMath.kt`, `ShaftSpec.freeToEndMm()` and the `FreeToEndBadge.md`
  contract (on-device direction: auto-bodies fill the gap to the OAL, so the number
  misleads). The preview card now carries only the OAL badge. Oversize is signalled by the
  OAL field's error state and the add-dialog "falls outside shaft span" warning — both
  unchanged. See `OverallLength.md` (v2.1).

**v0.15 (2026-08-17)**
- **Tap-to-add removed.** The preview canvas tap is selection only. The add-at-tapped-position
  chooser fired unintentionally and was never used on purpose (on-device report), so the
  gesture, its pending-position state (`setTapAddPosition`/`clearPendingAddPosition`/
  `pendingAddPositionMm`), and the entire snap pipeline it was the sole consumer of
  (`ui/viewmodel/SnapUtils.kt`, `snapRawPositionMm`, `gapToNextAnchorMm`) are gone. The
  "+ Add Component" button's chooser is now the only add entry point; its handoff state was renamed off the dead gesture
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
