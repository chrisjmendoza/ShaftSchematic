# Archive

Resolved analyses, completed reviews, and superseded proposals — kept for historical
context. Nothing in this folder describes current behavior; the living docs are in
`docs/` and `app/src/main/java/com/android/shaftschematic/docs/`.

| File | What it was | Outcome |
|---|---|---|
| `AUDIT.md` | Codebase audit, 2026-05-27 | All 9 findings fixed by 2026-05-30 |
| `ANALYSIS.md` | Findings analysis, 2026-06 | 20/22 fixed; 2 intentionally deferred/removed |
| `REVIEW_feat-auto-taper-rate.md` | Code review of the auto taper-rate branch | All 10 findings fixed on-branch, 2026-07-12 |
| `OAL_THREAD_BUG_ANALYSIS.md` | Incident report: OAL dimension vs excluded threads | Resolved; fix (identity OAL window) still in place |
| `CouplerBoltSlot_Proposal.md` | Design proposal for coupler bolt slots | Implemented; contract lives in the in-source `CouplerBoltSlot.md` |
| `runout_bubble_collision_system_2026-07-18.md` | Design record: collision-free runout bubble layout | Shipped (`geom/RunoutBubbleLayout.kt`); contract folded into `RunoutSheet.md` |
| `runout_wear_resolved_components_fix_2026-07-18.md` | Bug-fix record: runout/wear docs used raw spec | Shipped; contract folded into `RunoutSheet.md` |
