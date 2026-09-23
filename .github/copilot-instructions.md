# ShaftSchematic – AI Agent Instructions

The project's conventions and critical invariants live in **one** file: `CLAUDE.md` at the
repo root. Read it in full before editing anything; every rule in it applies to any coding
agent. This file is deliberately only a pointer — a second copy of the rules drifted from the
first within weeks, and a rule present in one copy only is a rule half the agents never see.

Authoritative sources, in order:

1. `CLAUDE.md` — project conventions and critical invariants (mm-canonical model, unit
   conversion only at the UI edge, the golden rule that user inputs are never rewritten,
   add-dialog / carousel-card parity, reference-only features, draw-both-sites rules).
2. `docs/contracts/INDEX.md` — per-subsystem contract docs. Read the relevant one before
   editing a subsystem, and update it in the same change if behavior changes.
3. `CONTRIBUTING.md` — architecture overview and coding guidelines.
4. `docs/STYLE_GUIDE.md` — comment conventions.

Commit policy: do not auto-commit; the user reviews every change before it lands.
