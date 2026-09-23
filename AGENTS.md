# ShaftSchematic – Agent Instructions

The project's conventions and critical invariants live in **one** file: [`CLAUDE.md`](CLAUDE.md)
at the repo root. Read it in full before editing anything; it is written for any coding agent,
not only Claude, and every rule in it applies here.

This file used to carry a verbatim copy of `CLAUDE.md` for Codex-style agents that look for
`AGENTS.md`. The copy drifted three times in two weeks — every invariant added to one had to be
hand-mirrored into the other, and a rule present in only one of them is a rule half the agents
never see. So the copy is gone; this file is the pointer.

Then, in order:

1. `docs/contracts/INDEX.md` — the per-subsystem contract docs. Read the relevant one before
   editing a subsystem, and update it in the same change if behavior changes.
2. `CONTRIBUTING.md` — architecture overview and coding guidelines.
3. `docs/STYLE_GUIDE.md` — comment and style conventions (no date stamps, no prior-code
   narratives, "on-device report" attribution).

Commit policy: do not auto-commit; the user reviews every change before it lands.
