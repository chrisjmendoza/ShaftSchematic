ShaftScreen Contract
--------------------

Layer: UI → Screens  
Purpose: Present the shaft editor surface and bind ViewModel state to user controls.

Version: v0.25 (2026-10-29 — the thread card's "Include thread in OAL" switch asks whether the
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
closing the dialog never drops the cursor and keyboard into the OAL field. With
  one auto section the value is the shaft's Ø and goes straight to `onSetAllAutoSectionsDia`.
  Covered by `AutoBodyDiaScopeDialogTest`.
- **New-shaft diameter prompt.** On the ORIGINAL drawing the first OAL commit on a brand-new
  shaft opens `ShaftDiaPromptDialog` (`shaft_dia_prompt`, field `shaft_dia_prompt_field`,
  `shaft_dia_prompt_set` / `shaft_dia_prompt_skip`), wired in `ShaftRoute`. See
  `OverallLength.md`.
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
  The OAL field follows the same rule — it no longer commits per keystroke (deleting one
  digit on the way from 368.5 to 367.75 committed a transient 36″ shaft and left a
  re-anchored taper's residue behind, on-device report). It commits on **accept**: the green
  ✓ beside the field, IME Done, or a blur after the text changed since focus (the
  `BlurCommitPolicy` baseline rule), each exactly once. The red ✗ restores the stored value
  and commits nothing. The ✓/✗ pair shows only while the value is being modified (focused
  AND the text differs from the on-focus baseline); the slot is reserved so the row does not
  jump, and the field is a narrowed value box (160–240 dp), not a full-width banner.
  Unparseable text commits nothing: ✓/Done leave it as typed, a blur restores the stored
  value — both under a `"<typed> not applied — not a number"` line (`OverallLength.md`). The OAL field's **display never rewrites the
  user's own text**: a typed `150 3/4` stays a fraction rather than echoing back as
  `150.75` (on-device report). The text re-derives from the model only when the field is
  unfocused AND no longer parses to the model value (undo, an edit from elsewhere, a
  reverted empty commit). An **empty** field on Done/blur commits nothing and reverts to
  the stored value — it never zeroes the shaft. See `OverallLength.md`.  
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
  - Project-Info icon
  - New / Open / Save / Export-PDF action icons. Export-PDF is gated by
    `ui/util/ExportPdfGate.kt` (pure, JVM-tested in `ExportPdfGateTest`): enabled only
    with ≥ 1 real component (coupler bolt slots don't count) and no collisions; a tap
    while disabled shows the gate's message as a snackbar.
  - Overflow menu (⋮) → Save As…, **Close Document** (testTag
    `overflow_close_document`; clean → closes to Start, dirty → shared unsaved-changes
    guard — see `Navigation.md`), Settings, Clear All, etc.

- **Preview Card:** (`PreviewCard`, `PreviewOalBadge` — both in
  `ui/screen/ShaftPreviewPanel.kt`, extracted from `ShaftScreen.kt` 2026-07-24, pure
  code move, no behavior change)
  - Fixed preview area rendering the shaft via `ShaftDrawing(...)`  
  - Optional grid overlay (user setting)  
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
  - Preview Colors: presets (Stainless/Steel/Bronze/Transparent) + Custom palette
  - Black/White Only mode (forces black outlines and disables fills in Preview)

- **Scrollable Form Area:**  
  - Overall length field (`OverallLengthField`, unit-aware, narrowed value box; commits on
    accept — ✓ / Done / blur-with-change — with the ✓/✗ accept/cancel pair beside it while
    the value is being modified; never per keystroke)  
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

Window width
------------
Window width is the editor's **one** adaptive axis (`ui/adaptive/WindowSize.kt`,
`currentWindowWidthClass()`); the policy for the app as a whole lives in `Adaptive.md`. Height
is never a class here. The Final Schematic tab hosts this same screen through
`ShaftRoute(target = FINAL)`, so it inherits every branch below.

- **COMPACT** (< 600 dp) — the phone layout, unchanged: one column with the preview card pinned
  under the app bar (`heightIn(min 120, max 200.dp)`, `aspectRatio(3f)`) above a single
  scrolling column holding the OAL field, the dimensioned-OAL / debug labels, the Components
  heading, the "+ Add Component" button, `SpecWarningBanner`, and the carousel.
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
    card, `weight(1f)`, and a **side column** holding the OAL field and its labels, the
    "+ Add Component" button, and `SpecWarningBanner`. The column is the controls' width
    (`EXPANDED_SIDE_COLUMN_WIDTH_DP`, 260 dp) plus the OAL field's 96 dp ✓/✗ action slot: the
    OAL box takes the controls' width (`OverallLengthField(fieldMaxWidth = Dp.Unspecified)`
    lifts its 240 dp cap — the phone keeps `OAL_FIELD_MAX_WIDTH`) with the slot to its right,
    and the button and the warnings are padded by the slot so all three share one width and
    one right edge (on-device report: a 240 dp box over a wider button looked unrelated to it).
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
(`previewBlock(maxHeight, aspect)`, `oalBlock`, `componentsHeader(hint)`,
`addComponentButton`, `carouselBlock(peekPageWidth, fillHeight)`) and each branch only decides
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
(in place and across a recreation). The tab itself has the same problem one level up, solved
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
- `ComponentCard` also ends every card with a **Save** button (`card_save_button`) — a
  full-width, centered, filled `Button`. Fields commit on blur and IME Done, but
  chips/toggles/checkboxes never TAKE focus, so a typed value followed by a chip tap sat
  uncommitted (on-device report). Save force-clears focus, driving the one existing
  commit-on-blur path — no second commit pipeline. Card-only: the Add dialogs commit through
  their own Add button.
  **It is DISABLED while nothing is pending** (on-device request): greyed out is a solid
  statement that every field on this card is saved, and filled is the visible thing to press.
  The state is the aggregate of the fields' own `onDirtyChange` reports, collected by the
  card's `CardDirtyState` through the `LocalCardDirtyState` composition local — fields
  register themselves, there is no per-call-site key list to keep in sync. Commit-on-blur is
  otherwise **completely unchanged**; the button only reflects and flushes it. Accepted
  trade-off: with nothing pending it is no longer a tap-anywhere keyboard dismissal — IME
  back and Done still are. See `NumberField.md`.  
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
