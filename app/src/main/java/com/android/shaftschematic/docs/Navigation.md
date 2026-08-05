Navigation Contracts
--------------------

Files: AppNav.kt, InternalDocRoutes.kt, PdfExportRoute.kt (ui/nav/);
ShaftEditorRoute.kt, ShaftRoute.kt, StartScreen.kt, RunoutRoute.kt, WearRoute.kt,
UndercutRoute.kt, HelpRoute.kt (ui/screen/)  
Layer: UI → Nav

Version: v0.6 (2026-08-05)

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
  - Undercut Drawing tab → UndercutRoute (`docs/UndercutDrawing.md`) — same "built" gating as
    Runout/Wear
  - Consolidated Output tab → OutputRoute (`docs/RunoutSheet.md` Consolidation step 5) —
    consolidated-sheet variants, worn-section editor, "Shaft height" slider, Export all;
    same "built" gating, last in the sidebar
- `settings` → SettingsRoute
- `about` → AboutRoute
- `help` → HelpRoute — static Help & FAQ content (no ViewModel); entered from Settings.
  Topics restate current behavior — a behavior change must update the matching topic in
  the same change (the screen is the user-facing summary of the contract docs).
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
  from `ShaftViewModel.drafts`: name or "Untitled draft", relative age, tap to
  `continueDraft(id)`, X icon → "Discard this draft?" confirm → `discardDraft(id)`),
  entry to editor/settings. AppNav wires `drafts`/`continueDraft`/`discardDraft`
  from the VM. See `docs/Persistence.md` (Autosave / draft ring).

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
- See `docs/Autosave_Incident_2026-07-25.md` (root cause #4 / fix #4) and
  `docs/ShaftViewModel.md` (`hasUnsavedWork()`) for the full-snapshot comparison this
  guard relies on.

Do Nots
- Do not create ViewModels manually; use DI/factories.
- Do not perform I/O in nav lambdas.
