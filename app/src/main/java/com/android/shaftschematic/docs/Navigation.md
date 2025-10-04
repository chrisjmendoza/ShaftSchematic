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
- **StartScreen.kt:** Lightweight landing screen; links to editor.

Do Nots
- Do not create ViewModels manually; use DI/factories.  
- Do not perform I/O in nav lambdas.
