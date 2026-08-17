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
| `BackupRestore_Strategy.md` | Backup/restore strategy, 2026-07-12 | Implemented (all four layers); contract lives in `Persistence.md` |
| `LinerWearAreas_Proposal.md` | Feature scoping: liner wear areas | Implemented 2026-07-18; contract folded into `RunoutSheet.md` (min-Ø since retired) |
| `RunoutBubbleEditor_PLAN.md` | Build plan + progress log for the runout bubble editor | Shipped 2026-07-21; contract in `RunoutSheet.md` and `CLAUDE.md` |
| `Autosave_Incident_2026-07-25.md` | Incident report: autosave slot destroyed unsaved edits | Resolved 2026-07-25 (dirty-gated draft ring); contract in `Persistence.md` / `ShaftViewModel.md` |
| `TaperOrientation_Analysis_2026-07-26.md` | Investigation: renderer/storage taper orientation | Resolved 2026-08-06 (forward fix; data repair declined) |
| `WearDiaMeasurements_PLAN.md` | Implementation plan: measured-Ø wear readings | Shipped 2026-07-28; contract in `RunoutSheet.md` §"Wear Diameter Measurements" |
| `UndercutDrawing_PLAN.md` | Design plan: undercut drawing tab + PDF | Shipped 2026-07-30→08-03; contract in `UndercutDrawing.md` |
| `Templates_And_DiaVisibility_PLAN.md` | Plan: shaft templates + per-component Ø visibility | All six parts shipped 2026-08-11/14; contracts in `Templates.md`, `PDF_EXPORT.md` §5.3 |

Still living in `docs/` (deliberately **not** archived): `MultiShaftJob_Plan_2026-07-26.md`
(not built — awaiting product answers, TODO §6) and `SettingsCustomization_PLAN.md`
(Phase 1 shipped; §2 deferrals are still open product decisions).
