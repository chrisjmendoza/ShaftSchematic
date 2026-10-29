# Adaptive Layout — Phones, Tablets, Orientation

Files: `ui/adaptive/WindowSize.kt`, `ui/adaptive/ReadableWidth.kt`, `ui/adaptive/Orientation.kt`,
`ui/adaptive/EditorChrome.kt`, `res/values/integers.xml`, `res/values-sw600dp/integers.xml`,
`AndroidManifest.xml`; consumers: `ShaftEditorRoute`, `ShaftScreen`, the sheet tabs, every list
screen, both PDF preview screens
Layer: UI (layout policy)
Version: v1.2 (2026-10-29 — the EXPANDED editor's drawing band is as tall as the taller of the
drawing and its side column, under the cap, and yields height to the keyboard; an open PDF
preview options panel docks beside the page. v1.1 2026-10-27 —
saved state survives a rotation across the EXPANDED boundary; MEDIUM editor carousel fills the
column. v1.0 2026-09-16)

## Why

The app was designed as a portrait phone app. A tablet is the natural demo and shop-floor
device — a shaft drawing wants width — and on one the phone layout read as a flat strip of
shaft over a column of controls stretched edge to edge. This contract is the ONE place the
app's shape-by-window decisions are stated; every screen keys on the same axis.

## Contract

### One axis: the window WIDTH class

- `WindowWidthClass { COMPACT, MEDIUM, EXPANDED }` with Material's breakpoints — under 600 dp,
  600–839 dp, 840 dp and up (`WINDOW_WIDTH_MEDIUM_DP`, `WINDOW_WIDTH_EXPANDED_DP`). The pure
  `windowWidthClassFor(widthDp)` is unit-tested (`WindowSizeTest`); the composable reader
  `currentWindowWidthClass()` takes `LocalConfiguration.screenWidthDp` (system bars excluded,
  so a window is classified by the width the content actually gets) and recomposes on
  rotation, split-screen resize and fold.
- **No height class.** The layouts that care about height read the real height in dp at their
  own site: the preview tuning sheet's page strip at COMPACT / MEDIUM (`PreviewTuning.kt`), and the EXPANDED
  editor's drawing band, which yields to the keyboard (below).
- **Two panes only at EXPANDED** (`WindowWidthClass.twoPane`). A MEDIUM window split in two
  leaves each pane narrower than a phone, and the carousel cards and sheet controls are
  phone-width designs; MEDIUM is a taller single column, nothing more. (The Schematic editor's
  EXPANDED layout is a top band over a full-width carousel rather than two side-by-side panes —
  `twoPane` still names the branch; see `ShaftScreen.md` "Window width".)
- No `material3-window-size-class` / `material3-adaptive` dependency: three thresholds and one
  reader are the whole need, and the classification stays pure and testable without them.
  Reach for the library only if a layout ever needs its posture/hinge data.

### What each class does

| Surface | COMPACT (phone) | MEDIUM (portrait tablet, landscape phone) | EXPANDED (landscape tablet) |
|---|---|---|---|
| Schematic / Final editor (`ShaftScreen`) | today's column; the action bar prints NO title (the icons left it ".."; the document title strip names the screen) | preview cap 280 dp, bar title shown, carousel fills the column's leftover height (floor 360 dp) | drawing band across the top (4:1 preview + OAL / add / warnings side column) over a full-width carousel, current card centred, neighbours peeking; the band is as tall as the taller of the drawing and the side column (under the cap) and yields height to the keyboard |
| Runout / Wear / Undercut tabs | today's column, 200 dp canvas | 280 dp canvas | two panes: canvas + print group \| controls |
| Consolidated Output tab | today's column | readable width | readable width (see `RunoutSheet.md`) |
| Editor sidebar | slide-in overlay (200 dp) | overlay | **permanent** 240 dp panel beside the tabs; hamburgers hidden |
| List screens (Start, Settings, Help, About, Achievements, Developer Options, Templates, Open, Save As) | full width | `readableWidth()` — 720 dp cap, centred | same |
| PDF preview screens | rotation unlocked while open | same | same (already free) |
| PDF previews, options panel open (`PdfPreviewScreen`, `PdfPreviewOverlay`) | page strip under the app bar (the page's ink band, fit-width), panel capped below it | same | panel **docked** as a right-hand column (380 dp, at most half the box); the page is the ordinary full page, fitted at full height into the width beside it |

### The EXPANDED editor's drawing band

- **Height = max(drawing, side column), under the cap.** The band is the larger of the height the
  4:1 drawing wants over the width beside the side column (`expandedDrawingBandHeightDp`) and the
  side column's own content height (the OAL box, the add button, the warnings — measured past
  the scroll's minimum height), capped at the preview's 320 dp ceiling (`expandedBandHeightDp`).
  A band sized from the drawing alone clipped the second of two warnings mid-line beside empty
  paper (on-device report). Past the cap the side column scrolls inside the band and a fade at
  its foot (`EXPANDED_SIDE_COLUMN_FADE_DP`, drawn only while it can scroll further) says so — a
  warning list still never pushes the carousel below the cap.
- **The band yields to the keyboard.** With the keyboard up the band shrinks so the component
  region under it keeps `EXPANDED_CAROUSEL_MIN_HEIGHT_DP` (220 dp: the heading, the card's top,
  one two-up field row with its label and its "not applied" notice), never below the drawing's
  120 dp floor plus padding (`expandedBandHeightUnderImeDp`, reading `WindowInsets.ime` against
  the band's viewport). A band that held its height left the focused card ~65 dp, clipping the
  field's label and hiding its notice (on-device report). With no keyboard the band is unchanged.
  The preview keeps its shape in a shorter band: it is held to the width its aspect takes at the
  band's height (`expandedPreviewMaxWidthDp`), so it draws smaller rather than squashed.
- All of it is pure arithmetic in `ui/screen/EditorTabletLayout.kt`, pinned by
  `EditorTabletLayoutTest`; COMPACT and MEDIUM do not read it.

### The PDF previews' docked options panel

- **At EXPANDED an open options panel docks beside the page.** The page strip and capped panel
  are the phone's answer to "the panel may never cover the page"; on a landscape tablet they left
  the strip a third of the screen tall, so a portrait undercut page drew at a quarter of the strip's
  width with unreadable values (on-device report). Both hosts resolve ONE `previewPageLayout`
  (`FULL` / `STRIP` / `DOCKED`, `PreviewTuning.kt`): docked, the panel is a right-hand column
  (`PREVIEW_PANEL_DOCK_WIDTH_DP`, never more than half the box — `dockedPanelWidthDp`) whose
  content scrolls in the column's full height, and the page is the ordinary full page — no ink-band
  crop — fitted into the width left of it (`dockedPageWidthDp`), zoomable as ever.
- Every panel docks there, page-tuning or not. COMPACT and MEDIUM keep the strip / centered page
  unchanged. The page block and the panel each have ONE call site per host; only their boxes change.
- The zoom resets when the layout value changes (open, close, or a rotation across the EXPANDED
  boundary); the render loop, draft raster, commit-on-release, re-render indicator and the panel's
  close / Back handling do not depend on where the panel sits. Pinned by `PreviewTuningTest`
  (pure) and `PreviewOptionsPanelTest` (Robolectric at `w1280dp-h800dp-land`).

Per-surface details live in the owning contracts (`ShaftScreen.md`, `RunoutSheet.md`,
`UndercutDrawing.md`, `Navigation.md`, `UI_CONTRACT.md`); this table is the map.

### Rules every adaptive screen follows

- **One composable per block, called from both branches.** A screen that lays out two ways
  extracts its content blocks (preview pane, components pane, canvas group, controls) into
  private composables and has the single-column and the two-pane branch CALL them. Duplicating
  a block into each branch is how the phone and the tablet drift — a control added to one and
  not the other is exactly the bug the add-dialog-parity invariant exists to prevent on cards.
- **A block a width change MOVES has one saved-state identity.** `rememberSaveable` keys its
  values by composition PATH, so content composed from a different call site per branch reads
  none of the other branch's saved values: a rotation across the EXPANDED boundary resets it,
  and the unread values are saved again and come back a rotation later (on-device report — the
  Runout tab's Blank draft read ON, OFF, ON over two rotations; an open undercut detail page
  dropped back to the list, and a closed one reappeared later). Either give the block ONE call
  site with only its surroundings conditional (`ShaftEditorRoute` composes the tab content from
  a single place and only the permanent panel beside it comes and goes), or make it
  `movableContentOf`, which roots the path at the content itself (`ShaftScreen`'s carousel and
  warning banner). A `SaveableStateHolder` alone does NOT fix this: the keys inside its provider
  are still path-based. Pinned by `EditorRotationStateTest`, in place and across a recreation.
  The sheet tabs need neither: their saved state (`showPreview`, `blankDraft`, the undercut
  detail anchor, the selected wear component) is declared above their own width branch, and
  the blocks the branch moves hold only scroll positions.
- **COMPACT is byte-identical to the pre-tablet layout.** Nothing in this contract changes a
  phone. Every existing `testTag` survives in every branch.
- **Pinned things stay pinned.** The preview card and the sheet canvases sit above their
  scrolling controls on a phone; in a pane they stay at the top of that pane and the pane's
  remaining content scrolls under them. A canvas that scrolls away is the failing state.
- **`readableWidth()` goes on the page's one scrolling column, never per row.** The cap
  (`READABLE_CONTENT_MAX_WIDTH`, 720 dp) is a property of the page; rows keep their own
  `fillMaxWidth()`. It is a no-op on a phone.
- **Sheet ink and PDF output are untouched.** The window class changes how much SCREEN a
  drawing gets; it changes nothing about what is drawn (`Appearance.md`) and nothing in a
  composer. The four sheet canvases fit their `ShaftLayout` to whatever size they are given,
  so a taller canvas is a bigger drawing, not a different one.
- **Nothing here reaches the model, the ViewModel, or a document.** Layout is a display
  decision; a document never records the device it was edited on.

### Orientation

- **Phones lock to portrait; tablets rotate freely.** ONE resource decides it:
  `R.integer.activity_orientation` — `1` (`SCREEN_ORIENTATION_PORTRAIT`) in `values/`, `-1`
  (`SCREEN_ORIENTATION_UNSPECIFIED`, the system's own rotation policy) in `values-sw600dp/`.
  `MainActivity.onCreate` applies it (`requestedOrientation = baseActivityOrientation()`)
  before `setContent`. The manifest keeps a literal `portrait` and must NOT reference the
  resource: a manifest attribute is resolved once at package parse and cannot vary by
  configuration — lint `ManifestResource` fails the build on it, and the `values-sw600dp`
  value would simply never be read, leaving every tablet locked to portrait. On a phone the
  runtime call restates the manifest's value; on a tablet it lifts the lock at creation (a
  tablet held landscape at launch recreates once into landscape).
- **A screen that unlocks rotation restores the BASE orientation, never portrait.** The two PDF
  preview screens call `unlockRotation()` on entry and `restoreBaseOrientation()` on dispose
  (`ui/adaptive/Orientation.kt`, reading the same resource). Restoring a hard-coded portrait
  would lock a tablet to portrait the first time a preview closed — the bug this replaced.
- **Rotation recreates the activity.** No `configChanges` handling: Compose state that must
  survive is `rememberSaveable` (active tab, sidebar, dialog-open flags, `showPreview`,
  `blankDraft`, selected wear component); the ViewModel holds the document and the selection.
  A numeric field's typed text is saveable too (`NumericInputField`, `NumberField.md`), and so
  is the runout bubble editor (which bubble, its TIR text, its high spot). The undercut detail
  page's unconfirmed drafts are not: a typed Ø the rotation's own blur committed into a draft
  is lost with the draft, but the field brings the text back and lands it on its next blur.
  Transient `remember` state (pinch zoom/pan on the runout canvas,
  the rasterized preview bitmaps, expanded dropdowns) resets on rotation, and the bitmaps
  regenerate from their `LaunchedEffect`s. Acceptable for a tablet; do not add `configChanges`
  to paper over it, which would also stop the layouts re-keying on the new width.

### Permanent sidebar

- In an EXPANDED window `ShaftEditorRoute` lays the sidebar out as a permanent 240 dp panel
  beside the tab content (`EditorSidebarPanel`, the SAME content composable the phone overlay
  hosts) and provides `LocalSidebarPermanent = true`; every tab toolbar reads it to hide its
  hamburger, since there is nothing to open. A CompositionLocal rather than a parameter: six
  tab routes would otherwise each carry a boolean whose only consumer is one icon. Default
  `false`, so a tab hosted outside the editor (a test, a preview) keeps its menu button.

## Testing

- `WindowSizeTest` pins the breakpoints and the two-pane rule.
- `EditorRotationStateTest` drives the width class through `LocalConfiguration` at a tablet
  window and pins saved state across EXPANDED ↔ MEDIUM, both in place and through
  `StateRestorationTester` (save in one branch, restore in the other).
- Robolectric hosts a route at a tablet size with `@Config(qualifiers = "w1280dp-h800dp-land")`
  (the suite's phone default is `w400dp-h800dp`); the two-pane tests assert the pane tags exist
  at tablet width and are absent at phone width, and that the hamburger follows
  `LocalSidebarPermanent`.
- On-device: the tablet pass (portrait and landscape, every tab, a rotation mid-edit, the
  tuning sheet's page strip in landscape) is a TODO item until a tablet has been in hand.

## Do Nots

- Do not key a layout on `LocalConfiguration.orientation` — width class is the axis, and a
  landscape phone is MEDIUM, not a tablet.
- Do not split a MEDIUM window into panes.
- Do not add a second copy of a content block for the tablet branch.
- Do not restore `SCREEN_ORIENTATION_PORTRAIT` literally anywhere; go through
  `restoreBaseOrientation()`.
- Do not persist anything about the window or device into a document.
