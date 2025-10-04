TextFilters Contract
--------------------

Layer: Util → UI  
Purpose: Lightweight input filters that keep numeric/fraction typing ergonomic.

Version: v0.1 (2025-10-04)

Invariants
- Filters are permissive while typing; validation happens on commit.  
- Pure transformations, no side effects.

Responsibilities
- Allow digits, one decimal separator, slash, and whitespace for `W N/D`.  
- Block obviously broken patterns (multiple slashes, etc.).

Do Nots
- Do not enforce numeric validity (VM does that).  
- Do not block edit gestures (paste/select all).
