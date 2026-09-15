Navigation Contracts
--------------------

Files: AppNav.kt, InternalDocRoutes.kt, PdfExportRoute.kt (ui/nav/);
ShaftEditorRoute.kt, ShaftRoute.kt, StartScreen.kt, RunoutRoute.kt, WearRoute.kt,
UndercutRoute.kt, OutputRoute.kt, HelpRoute.kt, HelpSearch.kt, EditorDocumentTitle.kt,
RenameShaftDocumentDialog.kt (ui/screen/)  
Layer: UI → Nav

Version: v0.12 (2026-09-15)

Invariants
- Routes are stable, typed constants or sealed routes.
- No heavy work in nav composables; they wire screens and VM scopes.
- The Schematic / Runout / Wear / Undercut / Consolidated Output switch is **in-editor tab
  state** (sidebar), not NavHost navigation — leaving the editor route discards tab state.

Route graph (AppNav.kt NavHost)
- `start` → StartScreen (New Drawing / Open / Unsaved drafts list (up to 3) / Settings /
  Help & FAQ / Send Feedback)
- `editor` → **ShaftEditorRoute** — the editor container. Owns the sidebar overlay
  (`EditorSidebarOverlay`) and the `EditorTab` state switching between:
  - Schematic tab → ShaftRoute → ShaftScreen
  - Runout tab → RunoutRoute (runout authoring; exports the classic runout sheet)
  - Wear tab → WearRoute
  - Undercut Drawing tab → UndercutRoute (`docs/contracts/UndercutDrawing.md`) — same "built" gating as
    Runout/Wear
  - Consolidated Output tab → OutputRoute (`docs/contracts/RunoutSheet.md` Consolidation step 5) —
    consolidated-sheet variants, worn-section editor, "Shaft height" slider, the
    liner-compression pair, blank draft, Export all; same "built" gating, last in the
    sidebar
  - Sidebar **tools group** (bottom, never dimmed by the "built" gate — no tool reads
    anything from the shaft): Keyway calculator · **Taper calculator** · Unit converter ·
    Help & FAQ · Settings, in that order. The two calculators are adjacent. Each calculator
    is an `AlertDialog` emitted by ShaftEditorRoute as a sibling of the tab content, with the
    route owning only a `rememberSaveable` open flag and passing the document unit as
    `defaultUnit`: `BoreKeywayCalcDialog`, `TaperCalcDialog`
    (`docs/DESIGN_INTENT.md` §3.8, solve in `util/TaperCalcMath.kt`), `UnitConverterDialog`.
    Calculators are document-free — they read nothing from the spec, write nothing back, and
    never mark the session dirty.
- `settings` → SettingsRoute — main page plus two in-screen sub-pages (`SettingsPage`:
  Preview Colors, PDF Export), back-arrow returns to the main page before leaving the route
- `about` → AboutRoute
- `help?topic={topic}` → HelpRoute — static Help & FAQ content (no ViewModel). The `topic`
  argument is **optional** (`NavType.StringType`, default `""`), so the bare `help` route
  still matches and lands at the top of the list. **Every caller builds the route through
  `helpRoute(topicKey: String? = null)`** (AppNav.kt) — the query syntax and the argument
  name (`HELP_TOPIC_ARG`, pattern `HELP_ROUTE_PATTERN`) are stated once there and nowhere
  else.
  **Seven entry points**: the editor sidebar's tools group (directly above Settings), the
  Start screen's button column (under Settings), the Settings list row, and a `?` icon
  button on each of the **Runout, Wear, Undercut and Consolidated Output** tabs. Help is
  reference content reached for mid-job, so it must keep a top-level entry — Settings alone
  is the failing state (`docs/DESIGN_INTENT.md` §3.7).
  - **Per-tab `?` buttons** (`TabHelpButton`, HelpRoute.kt, testTag `tab_help`,
    contentDescription "Help for this tab"): one construction, placed at the **trailing end
    of each tab's toolbar row** after the Save icon, so it never displaces the Print-primary
    button in `DocumentActionButtons`. Each opens Help deep-linked to that tab's how-to
    topic — `record-runout`, `record-wear-readings`, `record-undercut-sections`,
    `consolidated-output-and-export-all` (the `HELP_TOPIC_*` constants in HelpSearch.kt;
    `HelpSearchTest` pins each to a topic that still exists). The Schematic tab
    deliberately carries none — its help is the Getting Started material one sidebar tap
    away. The callback `onOpenHelpTopic: (String) -> Unit` is plumbed from AppNav through
    `ShaftEditorRoute` exactly as `onOpenHelp`/`onSave`/`onTitleClick` are; there is no
    second Help channel.
  - **Topic keys are derived, never authored** — `helpTopicKey(title)` (HelpSearch.kt, pure)
    slugifies the title to kebab-case, and `HelpTopic.key` is what the `LazyColumn` item
    key, the `rememberSaveable` expansion key, and the route argument all use. Titles must
    therefore stay distinct under slugification (`HelpSearchTest` asserts uniqueness across
    the real content). A deep link expands its topic on first composition (a **seed** for
    the saved expansion, so it can still be collapsed) and scrolls the list to it via
    `helpTopicItemIndex`, which counts items exactly as the list lays them out — one header
    per section, then its topics. An unknown key is ignored: top of list, nothing expanded.
  - **Search** — an `OutlinedTextField` pinned above the list inside the Scaffold content
    (testTag `help_search`, label/placeholder "Search help", a × trailing icon while
    non-empty). Filtering is the pure `filterHelpSections(sections, query)`: blank or
    whitespace-only returns the content unchanged, otherwise case-insensitive substring over
    title OR body, sections with no surviving topic dropped, order preserved. An empty
    result prints `No topics match "<query>".` **While a query is active its hits render
    EXPANDED** — a search hit that still needs a tap to read is the failing state — as an
    `expanded || searching` read at the card, never a write to the saved expansion, so
    clearing the query restores every card's own state.
  Five sections: Getting Started, **Glossary**, How-To
  Guides, **Settings Reference**, FAQ. The Glossary sits second so a term can be looked up
  without reading past the guides; it defines shop and app vocabulary (AFT/FWD, blank draft,
  S-break, coupling face, dual units, L.E.T./S.E.T., liner compression, measurement
  reference, OAL, runout station/bubble, Shade in Components, shaft height, TIR, trace depth
  exaggeration) in the wording of `docs/DESIGN_INTENT.md` §4. Topics
  restate current behavior — a behavior change must update the matching topic in the same
  change (the screen is the user-facing summary of the contract docs). The Settings
  Reference section carries the same obligation for **every user-visible Settings
  control**: adding, renaming, or re-defaulting a control on any Settings page (main,
  Preview Colors, PDF Export) must update its entry in the same change.
- `developerOptions` → DeveloperOptionsRoute
- `achievements` → AchievementsRoute
- `openLocal` / `saveLocal` → internal-storage document pickers (InternalDocRoutes.kt)
- `pdfPreview` → PdfPreviewScreen
- `exportPdf` → PdfExportRoute (SAF export flow)

Responsibilities
- **AppNav.kt:** Define NavHost, start destination, and route graph.
- **ShaftEditorRoute.kt:** Editor container — sidebar, tab switch, back handling.
- **ShaftRoute.kt:** Wire VM ↔ ShaftScreen; own SAF PDF export for the schematic.
- **StartScreen.kt:** Landing screen — recents, "Unsaved drafts" card (up to 3 entries
  from `ShaftViewModel.drafts`: row title (see below), relative age, tap to
  `continueDraft(id)`, X icon → "Discard this draft?" confirm → `discardDraft(id)`),
  entry to editor/settings/help. AppNav wires `drafts`/`continueDraft`/`discardDraft`
  from the VM. See `docs/contracts/Persistence.md` (Autosave / draft ring).

Start screen draft names (`StartScreen.kt`, pure `draftRowTitle(entry)`)
- A draft row's primary text is, in order: the **saved file name** it was opened from
  (extension stripped), else the name the save screen would suggest —
  `DocumentNaming.suggestedBaseName(jobNumber, customer, vessel)` off the row's own
  `SessionSnapshot` — else the literal **"Untitled draft"**. A draft that was never saved
  still carries the project information the user typed, and three rows reading "Untitled
  draft" can only be told apart by their age, which is not how a shaft is remembered.
- When the **suggested** name is used the secondary line reads **"Unsaved draft · \<relative
  age\>"**, so a row that looks like a saved file is never taken for one; that line also
  carries the age, and the trailing age column is dropped for those rows so the time does not
  print twice. The saved-name and "Untitled draft" rows are unchanged — age in the trailing
  column, no secondary line.
- This NAMES A ROW and nothing else: no name is written to the draft, the entry stays unsaved
  and unnamed, and only saving (or the title-strip tap) names the document.

Document title strip (`ui/screen/EditorDocumentTitle.kt`)
- **Every** editor tab renders `EditorDocumentTitle` directly above its toolbar: the saved
  file name (extension stripped) or "Untitled draft", plus a trailing ` *` while
  `ShaftViewModel.hasUnsavedChanges` is true. `testTag("editor_document_title")`.
- Runout station counts / TIR readings, wear spots / pits / Ø readings, undercuts, and the
  Consolidated tab's worn sections and per-job sliders are all part of the same
  full-session snapshot the dirty flag compares
  (`ShaftViewModel.hasUnsavedChanges`, `docs/contracts/ShaftViewModel.md`), so editing on any tab
  raises the asterisk exactly like a spec edit. Surfacing it on only one tab is the bug
  this replaced.
- Each non-Schematic tab also carries a **Save** icon at the trailing edge of its toolbar
  (`testTag("toolbar_save")`, same tag and same `onSave` lambda as the Schematic's), so
  the asterisk is actionable where it is seen — otherwise the user must navigate back to
  the Schematic to save. `onSave` is plumbed from `AppNav` through `ShaftEditorRoute`; it
  quick-saves a named document and routes to `saveLocal` for an unnamed one.
- **The strip is TAPPABLE — it is the document's naming affordance.** `EditorDocumentTitle`
  takes an optional `onClick`; non-null makes the strip `clickable` (Material ripple,
  `Role.Button`, contentDescription "Document name — tap to rename" — the rendered text is a
  name plus a bare asterisk and says nothing about what tapping does). A null `onClick` leaves
  the strip inert with no click semantics.
- **The tap's meaning is decided in ONE place — `AppNav`**, beside `onSave`/`onSaveAs`:
  an **untitled** document (`currentDocumentName == null`) navigates to `saveLocal`, the Save
  As route that already seeds the name from `DocumentNaming.suggestedBaseName`; a **saved**
  document opens `RenameShaftDocumentDialog` and, on success, `setCurrentDocumentName(toName)`
  is what makes the strip follow the rename on every tab. `onTitleClick` is plumbed from
  `AppNav` through `ShaftEditorRoute` to all five tabs exactly as `onSave` is. **Every tab gets
  the same behaviour** — there is deliberately no "current tab" branch, so one strip can never
  come to mean different things on different tabs.
- **Shared rename dialog** (`ui/screen/RenameShaftDocumentDialog.kt`, testTags
  `rename_doc_field` / `rename_doc_confirm`): used by BOTH the editor's title tap and the Open
  screen's per-file "Rename" menu item, which is where it came from. It owns the typed name and
  the storage work — sanitize → `InternalStorage.normalizeShaftDocName` → blank check →
  same-name check → `exists`-refuses-overwrite → `InternalStorage.rename` — and reports through
  `onRenamed(toName)` / `onDismiss` / `onError(message)`. The callers own the consequences,
  which differ: the Open screen refreshes its list and updates the session name when the renamed
  file was the open one; the editor updates the session name and posts errors to its own
  `SnackbarHost` (the one `offerRenameAfterQuickSave` uses). `onRenamed` and `onDismiss` are
  terminal — the caller closes the dialog; `onError` leaves it open over the name to fix.
  A rename **never overwrites**, the same posture as the post-save rename offer.
- **Rename offer after a quick-save.** A document saved before its Job # / Customer / Vessel
  existed keeps whatever name it was first given, so after the editor's quick-save AppNav
  compares that name against `DocumentNaming.renameSuggestionBase` (the same suggestion the
  save screen makes) and, when they differ, shows a snackbar — "Saved. Rename to ‘…’?" with a
  one-tap **Rename** action — on the editor's own `SnackbarHost`. Rename succeeds → the
  document name is updated via `setCurrentDocumentName`, so the title strip follows on every
  tab. Constraints: it **never overwrites** (skipped when a save already occupies the target
  name, the same posture as the Open screen's rename dialog), and each distinct from→to pair
  is offered **at most once per editor session** so a declined offer cannot nag on every save
  — later job-info edits form a new pair and are offered again. The unsaved-changes guard's
  Save path deliberately does **not** carry the offer: that save clears the way for a
  session-replacing action, so its snackbar would outlive the screen it belongs to.
- The composable applies **no window insets of its own** — the caller owns them.
  `ShaftScreen` passes the status-bar inset (its `TopAppBar` then zeroes its own); the
  other four tabs already sit inside a `systemBarsPadding()` column and pass nothing.

Unsaved-changes guard (`AppNav.kt`)
- A single `runGuarded(action)` helper + one shared `UnsavedChangesDialog`, hoisted to
  NavHost scope, gate **every** session-replacing entry point: Start's New / Open /
  Open-recent, the editor's New / Open, and the editor's "Close Document" overflow
  item. `runGuarded` runs `action` immediately when `vm.hasUnsavedWork()` is false;
  otherwise it shows the dialog (Save / Don't save / Cancel).
- **Save** reuses the `pendingPostSaveAction` continuation: if the document already has
  a name it quick-saves and resumes `action`; otherwise it stashes `action` and
  navigates to `saveLocal`, which resumes it after a successful save (dropped on
  cancel). This makes Save-then-continue work from Start too, not just the editor.
- **Don't save** proceeds without saving — worded deliberately, not "Discard", because
  the autosave draft-ring entry is left intact as the safety net.
- **Close Document** (`ShaftScreen.kt` `OverflowMenu`, testTag
  `overflow_close_document`, plumbed through `ShaftRoute`/`ShaftEditorRoute`): a clean
  session closes to Start immediately; a dirty one goes through the same guard
  (Save-then-close / Don't-save-close / Cancel-stay). Close itself is
  `newDocument()` + navigate home.
- See `docs/archive/Autosave_Incident_2026-07-25.md` (root cause #4 / fix #4) and
  `docs/contracts/ShaftViewModel.md` (`hasUnsavedWork()`) for the full-snapshot comparison this
  guard relies on.

Do Nots
- Do not create ViewModels manually; use DI/factories.
- Do not perform I/O in nav lambdas.
