ShaftRepository Contract
------------------------

Layer: Data  
Purpose: Abstract persistence for loading/saving ShaftSpec and meta files.

Version: v0.1 (2025-10-04)

Invariants
- Repository methods are suspend and I/O-safe.  
- Stored representation is versioned; migrations occur on load.

Responsibilities
- Interface defines CRUD for ShaftSpec and project meta.  
- File-backed implementation (JSON/serialization).  
- No-op stub for tests.

Do Nots
- Do not leak platform file APIs to ViewModel/UI.  
- Do not perform unit conversions; store exact mm.

Notes
- Use atomic writes (temp → rename).  
- Pair with migrations for schema changes.

Future Enhancements
- Recent projects index.  
- Backup/restore and shareable export.
