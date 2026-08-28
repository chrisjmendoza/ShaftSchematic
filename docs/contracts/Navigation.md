Navigation Contracts
--------------------

Files: AppNav.kt, InternalDocRoutes.kt, PdfExportRoute.kt (ui/nav/);
ShaftEditorRoute.kt, ShaftRoute.kt, StartScreen.kt, RunoutRoute.kt, WearRoute.kt,
UndercutRoute.kt, HelpRoute.kt (ui/screen/)  
Layer: UI → Nav

Version: v0.8 (2026-08-14)

Invariants
- Routes are stable, typed constants or sealed routes.
- No heavy work in nav composables; they wire screens and VM scopes.
- The Schematic / Runout / Wear / Undercut / Consolidated Output switch is **in-editor tab
  state** (sidebar), not NavHost navigation — leaving the editor route discards tab state.

Route graph (AppNav.kt NavHost)
- `start` → StartScreen (New Drawing / Open / Unsaved drafts list (up to 3) / Settings)
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
- `settings` → SettingsRoute — main page plus two in-screen sub-pages (`SettingsPage`:
  Preview Colors, PDF Export), back-arrow returns to the main page before leaving the route
- `about` → AboutRoute
- `help` → HelpRoute — static Help & FAQ content (no ViewModel); entered from Settings.
  Four sections: Getting Started, How-To Guides, **Settings Reference**, FAQ. Topics
  restate current behavior — a behavior change must update the matching topic in the same
  change (the screen is the user-facing summary of the contract docs). The Settings
  Reference section carries the same obligation for **every user-visible Settings
  control**: adding, renaming, or re-defaulting a control on any Settings page (main,
  Preview Colors, PDF Export) must update its entry in the same change.
- `developerOptions` → DeveloperOptionsRoute
- `achievements` → AchievementsRoute
- `openLocal` / `saveLocal` → internal-storage document pickers (InternalDocRoutes.kt)
- `templates` → TemplatesRoute (`docs/contracts/Templates.md`) — the template browser,
  entered from Start's "Start from Template". Browsing is unguarded; **choosing** a template
  is session-replacing, so `onUseTemplate` runs through the same `runGuarded` unsaved-changes
  gate as New / Open before `vm.applyTemplate` + navigate to `editor`.
- `pdfPreview` → PdfPreviewScreen
- `exportPdf` → PdfExportRoute (SAF export flow)

Responsibilities
- **AppNav.kt:** Define NavHost, start destination, and route graph.
- **ShaftEditorRoute.kt:** Editor container — sidebar, tab switch, back handling.
- **ShaftRoute.kt:** Wire VM ↔ ShaftScreen; own SAF PDF export for the schematic.
- **StartScreen.kt:** Landing screen — recents, "Unsaved drafts" card (up to 3 entries
  from `ShaftViewModel.drafts`: name or "Untitled draft", relative age, tap to
  `continueDraft(id)`, X icon → "Discard this draft?" confirm → `discardDraft(id)`),
  entry to editor/settings. AppNav wires `drafts`/`continueDraft`/`discardDraft`
  from the VM. See `docs/contracts/Persistence.md` (Autosave / draft ring).

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
