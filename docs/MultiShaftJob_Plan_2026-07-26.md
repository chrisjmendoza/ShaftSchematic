# Multi-Shaft Per Job — Feasibility & Architecture Plan (2026-07-26)

**Request:** most inspection jobs are one job number per shaft, but sometimes two (or
more) shafts share one job number. Wanted: two shafts under one job number, with the
ability to select between them.

**Verdict: very feasible — and the cheap version is also the safe version.** This is a
navigation/creation problem, not a data-model problem. The recommendation is **derived
job grouping over the existing one-shaft-per-file format** (no new file format, no
manifest), plus an "Add shaft to this job" action and an in-editor quick-switch.

---

## 1. What the code does today (anchors)

- **One `.shaft` file = one shaft.** V1 envelope: `spec` + flat job metadata
  (`jobNumber`, `customer`, `vessel`, `shaftPosition`, `notes`) + `wearRecord` +
  `runoutReadings` (`doc/ShaftDocCodec.kt:47-79`). `decode()` **refuses** any file with
  `version > 1` (`ShaftDocCodec.kt:108-111`) — a real v2 format would be unopenable on
  every shipped build. Additive optional keys, by contrast, are free
  (`ignoreUnknownKeys`).
- **`shaftPosition` (PORT/STBD/CENTER/OTHER) already exists** and is the natural
  per-shaft discriminator within a job; it already flows into save-name suggestions
  (`InternalDocRoutes.kt:542-552`) and the schematic PDF footer.
- **Document identity is a filename** (`ShaftViewModel._currentDocumentName`), and the
  safety-critical subsystems are all keyed per document: the 3-entry autosave draft
  ring (per-session `currentDraftId`), the SessionHistory undo (cleared at every
  new/open/import boundary), and the unsaved-changes guard (`AppNav.kt:76-110`).
- **Listing never decodes content** — `InternalStorage.list` returns name + mtime only
  (`InternalStorage.kt:46-70`). Grouping by job number will need a metadata peek.
- **Pre-existing bug this feature exposes:** runout and wear export filenames omit
  `shaftPosition` (`RunoutRoute.kt:812-821`, `WearRoute.kt:443-451`) while the
  schematic export includes it (`PdfExportRoute.kt:236-243`). Two shafts on one job
  already collide on runout/wear PDF names **today**.

## 2. Options considered

**A — Multi-shaft envelope (v2 file holds N shafts).** Feasible but disproportionate.
It rewrites document identity across the ViewModel, the autosave ring (the subsystem
behind the 2026-07-25 data-loss incident — maximal blast radius), undo history,
save/open/rename, and every PDF route ("which shaft is active?"), and it breaks file
compatibility with every shipped build. All that for "sometimes two shafts."

**B — Derived job grouping of single-shaft files.** Keep one shaft per file. "A job" is
a *derived view*: all saved docs whose trimmed, non-blank `jobNumber` matches
(case-insensitive), discriminated by `shaftPosition`. Zero format change, zero autosave
change, zero undo change; switching shafts is just the existing guarded open-recent
path (`runGuarded` → `importJson` → `setCurrentDocumentName`, `AppNav.kt:159-175`).
The one real cost: a lightweight metadata peek when listing (decode envelope metadata
only, cached by name+mtime, corrupt files degrade to name-only rows).

A persisted job-manifest file was considered and **rejected** — it drifts on
rename/delete/restore; derived grouping is self-healing.

**C — B plus job-aware creation (recommended).** Option B plus the workflow that
actually creates the second shaft: **"Add shaft to this job"** — a
`newSiblingDocument()` variant of `newDocument()` that carries over
`jobNumber`/`customer`/`vessel` (+ unit prefs), starts geometry blank, prompts for a
distinct `shaftPosition`, and mints a fresh draft id exactly like existing session
boundaries. Optional: "Duplicate as sibling" (copy geometry too — twin shafts are
often near-identical).

## 3. Why C

1. Every hard subsystem (draft ring, undo boundaries, dirty guard, atomic save,
   backup/restore, PDF flows) already behaves correctly for two sibling files — C
   composes them; A rewrites them.
2. Perfect backward compatibility: old builds open every file; backups round-trip;
   nothing migrates.
3. Reversible: if jobs later grow real shared state (job-level notes, a combined
   two-shaft report), a v2 envelope can still be added — and the metadata-peek
   infrastructure gets reused. Question 5 below is the one thing that would change
   this calculus early.

## 4. Phased build (each phase ships/tests independently)

- **Phase 0 (tiny, ship first — it's a live bug):** add the position suffix to
  runout/wear export filenames, mirroring the schematic export. Pure-function change,
  unit-testable.
- **Phase 1 — metadata peek + grouped library:** `InternalStorage` gains a
  decode-metadata-only listing (IO dispatcher, mtime-keyed cache). Open screen groups
  rows by non-blank job number: job header + position-badged children; blank job
  numbers stay flat. Purely presentational.
- **Phase 2 — editor quick-switch:** sibling selector (e.g. tappable position chip
  next to the document title strip). Switching reuses the existing guarded open path —
  Save/Don't-save/Cancel prompt and draft-ring safety net come for free. UI test: dirty
  session prompts before switching.
- **Phase 3 — "Add shaft to this job":** `newSiblingDocument()` + entry points in the
  job group row and the editor selector. Optionally "Duplicate as sibling."
- **Phase 4 (polish, optional):** position/job badges on StartScreen recents; warn on
  duplicate (job, position) pairs; "export both shafts" convenience.

## 5. Risks

- `jobNumber` is free text — typos split a job silently; blank must never group.
  Mitigate: trim + case-insensitive compare only, show the raw string in the header.
- Metadata peek must be off-main, cached, and tolerant of corrupt/legacy files.
- The quick-switch must go through `runGuarded` — a direct `importJson` would bypass
  the unsaved-work guard. Keep it on the existing open path.
- Two siblings with the same position (e.g. both PORT) → selector falls back to
  filenames.
- Metadata edits do **not** propagate between siblings in this design (see Q1).

## 6. Product questions for Chris

1. Should editing customer/vessel/job number in one shaft update the sibling file too,
   or stay independent? (Independent is what this design gives.)
2. Is `shaftPosition` a sufficient in-job label, or do jobs have 2+ shafts on the same
   side (needing a free-text per-shaft label)?
3. Cap at two shafts per job, or genuinely N? (Design handles N for free.)
4. New sibling: blank geometry by default, or copy from the first shaft (twins)?
5. Will you ever want a *combined* job-level PDF (both shafts on one report)? That's
   the first real driver for a multi-shaft file format — good to know before Phase 1.
6. Quick-switch on a dirty document: prompt (current guard behavior, safe default) or
   auto-save?
