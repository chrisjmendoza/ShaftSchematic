Navigation Contracts
--------------------

Files: AppNav.kt, ShaftRoute.kt, StartScreen.kt  
Layer: UI → Nav

Version: v0.1 (2025-10-04)

Invariants
- Routes are stable, typed constants or sealed routes.  
- No heavy work in nav composables; they wire screens and VM scopes.

Responsibilities
- **AppNav.kt:** Define NavHost, start destination, and route graph.  
- **ShaftRoute.kt:** Provide typed entry for Shaft screen + params.  
- **StartScreen.kt:** Lightweight landing screen; links to editor and template builder.

Routes
- `"start"` — StartScreen (home)
- `"editor"` — ShaftEditorRoute (main editor, uses ShaftViewModel)
- `"template"` — TemplateScreen (blank-template builder, uses TemplateViewModel; independent of ShaftViewModel)
- `"settings"`, `"about"`, `"achievements"`, `"developerOptions"` — settings sub-screens
- `"openLocal"`, `"saveLocal"` — internal JSON file I/O
- `"pdfPreview"`, `"exportPdf"` — PDF preview and SAF export (editor only)

Do Nots
- Do not create ViewModels manually; use DI/factories.  
- Do not perform I/O in nav lambdas.
