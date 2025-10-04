NumberField Contract
--------------------

Layer: UI → Input  
Purpose: Single-line numeric field that holds local text; commits on blur/IME Done via callback.

Version: v0.1 (2025-10-04)

Invariants
- No live writes while typing; commit-on-blur discipline.  
- Field displays formatted text; callback receives raw text.

Responsibilities
- Maintain internal `text` state.  
- Invoke `onCommit(rawText)` on blur or IME Done.  
- Support decimals and shop fractions in display.

Do Nots
- Do not mutate ViewModel directly.  
- Do not parse or convert (VM handles it).
