Navigation Contracts
--------------------

Files: AppNav.kt, InternalDocRoutes.kt, PdfExportRoute.kt (ui/nav/);
ShaftEditorRoute.kt, ShaftRoute.kt, StartScreen.kt, RunoutRoute.kt, WearRoute.kt (ui/screen/)  
Layer: UI → Nav

Version: v0.2 (2026-07-18)

Invariants
- Routes are stable, typed constants or sealed routes.
- No heavy work in nav composables; they wire screens and VM scopes.
- The Schematic / Runout / Wear switch is **in-editor tab state** (sidebar), not
  NavHost navigation — leaving the editor route discards tab state.

Route graph (AppNav.kt NavHost)
- `start` → StartScreen (New Drawing / Open / Continue Draft / Settings)
- `editor` → **ShaftEditorRoute** — the editor container. Owns the sidebar overlay
  (`EditorSidebarOverlay`) and the `EditorTab` state switching between:
  - Schematic tab → ShaftRoute → ShaftScreen
  - Runout tab → RunoutRoute
  - Wear tab → WearRoute
- `settings` → SettingsRoute
- `about` → AboutRoute
- `developerOptions` → DeveloperOptionsRoute
- `achievements` → AchievementsRoute
- `openLocal` / `saveLocal` → internal-storage document pickers (InternalDocRoutes.kt)
- `pdfPreview` → PdfPreviewScreen
- `exportPdf` → PdfExportRoute (SAF export flow)

Responsibilities
- **AppNav.kt:** Define NavHost, start destination, and route graph.
- **ShaftEditorRoute.kt:** Editor container — sidebar, tab switch, back handling.
- **ShaftRoute.kt:** Wire VM ↔ ShaftScreen; own SAF PDF export for the schematic.
- **StartScreen.kt:** Landing screen — recents, draft restore, entry to editor/settings.

Do Nots
- Do not create ViewModels manually; use DI/factories.
- Do not perform I/O in nav lambdas.
