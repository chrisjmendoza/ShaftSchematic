InternalStorage Contract
------------------------

Layer: I/O  
Purpose: Provide a safe, app-scoped file API (paths, read/write, exists) for repositories.

Version: v0.1 (2025-10-04)

Invariants
- All paths are sandboxed to internal storage.  
- Operations are suspend or off main thread.

Responsibilities
- Resolve directories (projects, exports, cache).  
- Atomic write helpers and safe reads.  
- Surface I/O errors as domain exceptions.

Do Nots
- Do not parse/serialize business objects.  
- Do not expose absolute paths to UI.
