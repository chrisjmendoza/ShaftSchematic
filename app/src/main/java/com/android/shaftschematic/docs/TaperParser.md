TaperParser Contract
--------------------

Layer: Util  
Purpose: Parse user-provided taper rate inputs and derive missing SET/LET when instructed.

Version: v0.1 (2025-10-04)

Invariants
- Supports `a:b`, fraction (`3/4`), decimal (`0.75`).  
- Output is slope ratio (mm/mm); no unit IO.

Responsibilities
- `parseTaperRate(text)` → slope (Float).  
- Helpers derive SET/LET from slope and length when one endpoint is missing.  
- Validate: length > 0 mm; diameters ≥ 0.

Do Nots
- Do not write to model.  
- Do not convert units or format labels.
