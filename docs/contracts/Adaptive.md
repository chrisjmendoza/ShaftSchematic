# Adaptive Layout — Phones, Tablets, Orientation

Files: `ui/adaptive/WindowSize.kt`, `ui/adaptive/ReadableWidth.kt`, `ui/adaptive/Orientation.kt`,
`ui/adaptive/EditorChrome.kt`, `res/values/integers.xml`, `res/values-sw600dp/integers.xml`,
`AndroidManifest.xml`; consumers: `ShaftEditorRoute`, `ShaftScreen`, the sheet tabs, every list
screen, both PDF preview screens
Layer: UI (layout policy)
Version: v1.0 (2026-09-16)

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
- **No height class.** The one layout that cares about height — the preview tuning sheet's
  page strip (`PreviewTuning.kt`) — reads the real height in dp at its own site.
- **Two panes only at EXPANDED** (`WindowWidthClass.twoPane`). A MEDIUM window split in two
  leaves each pane narrower than a phone, and the carousel cards and sheet controls are
  phone-width designs; MEDIUM is a taller single column, nothing more.
- No `material3-window-size-class` / `material3-adaptive` dependency: three thresholds and one
  reader are the whole need, and the classification stays pure and testable without them.
  Reach for the library only if a layout ever needs its posture/hinge data.

### What each class does

| Surface | COMPACT (phone) | MEDIUM (portrait tablet, landscape phone) | EXPANDED (landscape tablet) |
|---|---|---|---|
| Schematic / Final editor (`ShaftScreen`) | today's column | preview cap 280 dp | two panes: preview + OAL + warnings \| components + carousel |
| Runout / Wear / Undercut tabs | today's column, 200 dp canvas | 280 dp canvas | two panes: canvas + print group \| controls |
| Consolidated Output tab | today's column | readable width | readable width (see `RunoutSheet.md`) |
| Editor sidebar | slide-in overlay (200 dp) | overlay | **permanent** 240 dp panel beside the tabs; hamburgers hidden |
| List screens (Start, Settings, Help, About, Achievements, Developer Options, Templates, Open, Save As) | full width | `readableWidth()` — 720 dp cap, centred | same |
| PDF preview screens | rotation unlocked while open | same | same (already free) |

Per-surface details live in the owning contracts (`ShaftScreen.md`, `RunoutSheet.md`,
`UndercutDrawing.md`, `Navigation.md`, `UI_CONTRACT.md`); this table is the map.

### Rules every adaptive screen follows

- **One composable per block, called from both branches.** A screen that lays out two ways
  extracts its content blocks (preview pane, components pane, canvas group, controls) into
  private composables and has the single-column and the two-pane branch CALL them. Duplicating
  a block into each branch is how the phone and the tablet drift — a control added to one and
  not the other is exactly the bug the add-dialog-parity invariant exists to prevent on cards.
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
  The manifest's `android:screenOrientation` references it, so the lock is applied before the
  first frame with no programmatic flip at launch.
- **A screen that unlocks rotation restores the BASE orientation, never portrait.** The two PDF
  preview screens call `unlockRotation()` on entry and `restoreBaseOrientation()` on dispose
  (`ui/adaptive/Orientation.kt`, reading the same resource). Restoring a hard-coded portrait
  would lock a tablet to portrait the first time a preview closed — the bug this replaced.
- **Rotation recreates the activity.** No `configChanges` handling: Compose state that must
  survive is `rememberSaveable` (active tab, sidebar, dialog-open flags, `showPreview`,
  `blankDraft`, selected wear component); the ViewModel holds the document and the selection.
  Transient `remember` state (pinch zoom/pan on the runout canvas, an in-progress bubble edit,
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
