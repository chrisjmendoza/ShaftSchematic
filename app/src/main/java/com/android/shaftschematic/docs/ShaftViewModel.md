ShaftViewModel Contract
-----------------------

Layer: UI → ViewModel  
Purpose: Owns editable ShaftSpec state, unit selection, grid toggle, and routes all commits from the UI to the model/persistence.

Version: v0.8 (2026-08-06)

Invariants
- All stored geometry is **canonical millimeters (mm)**.  
- Conversions (mm ↔ in) happen **on commit** from UI text → mm once.  
- Exposed state uses StateFlow; UI reads snapshots.

Responsibilities
- Hold `ShaftSpec`, `UnitSystem`, `showGrid`, and project meta fields.  
- Commit APIs accept raw text (e.g., `onSetOverallLengthRaw`), parse, convert, clamp, and update.  
- Expose derived values (e.g., `freeToEndMm`) from the model.  
- Load/save documents via `io/InternalStorage.kt` (atomic internal `.shaft` saves, listing,
  delete/rename) and `io/ShaftBackup.kt` (zip backup/restore). There is no repository class.
- Hold persisted display settings: `lineThicknessScale` (0.5–2.0, applied to preview and PDF stroke widths; 1.0 = default thin weight, 2.0 = original thick weight).
- Own autosave/draft-history state (`data/AutosaveManager.kt`, `data/DraftRing.kt`;
  full contract in `docs/Persistence.md`, incident background in
  `docs/Autosave_Incident_2026-07-25.md`):
  - `currentDraftId` — a UUID identifying this editing session's ring slot. Minted at
    construction; re-minted in `newDocument()` and `importJson()` so switching
    documents can never touch another document's draft entry.
  - `_savedSnapshot` — the dirty-gate baseline (last saved-to-file or freshly-loaded
    full session snapshot), held in a `MutableStateFlow` so `hasUnsavedChanges`
    re-evaluates the moment a save reseats it. Seeded blank at construction; reseated
    by `markDocumentSaved()` (all four explicit-save call sites go through it) and by
    `importJson()`/`newDocument()`. The 1.5 s-debounced autosave observer writes a
    `DraftEntry` only when the live snapshot differs from the baseline
    (`shouldWriteDraft`) **and** the session is not factory-default
    (`SessionSnapshot.isDefaultSession()`, `DraftRing.kt` — blocks the phantom blank
    draft the async unit-preference restore used to create on empty-ring launches),
    and removes this session's entry exactly once on the dirty→clean transition
    (which also cleans up previously-persisted phantoms).
  - `markDocumentSaved()` **also removes this session's draft-ring entry immediately**
    (guarded by `draftPersisted`). The observer's dirty→clean removal only runs on the
    *next* combine emission, which never comes when the user saves and navigates away
    without editing again — before this, saved documents lingered on the StartScreen
    as stale "Untitled draft" rows. `newDocument()`/`importJson()` drop
    `draftPersisted` to `false` *before* calling `markDocumentSaved()`, so opening or
    creating a document still never deletes the previous session's safety-net draft
    (the "Don't save" path keeps its draft).
  - `hasUnsavedChanges: StateFlow<Boolean>` — reactive companion to
    `hasUnsavedWork()`: `combine(sessionSnapshotFlow, _savedSnapshot)` through
    `shouldWriteDraft`, undebounced. Drives the editor document-title asterisk
    (`ShaftScreen` title strip, `testTag("editor_document_title")`).
  - `hasUnsavedWork()` — returns `shouldWriteDraft(buildCurrentSnapshot(),
    savedSnapshot)`, the **same** full-snapshot comparison as the autosave dirty gate,
    so spec, metadata, position, unit-lock, OAL mode, wear record, and runout
    readings/config all count as unsaved work. The legacy per-field
    `_savedSpec`/`_savedJobNumber`/`_savedCustomer`/`_savedVessel`/`_savedNotes`
    fields are gone; `markDocumentSaved()` now sets only `savedSnapshot`. Backs the
    universal unsaved-changes guard in `AppNav.kt` (`docs/Navigation.md`). See
    `docs/Autosave_Incident_2026-07-25.md` (root cause #4) for why the old
    partial comparison mattered.
  - `drafts: StateFlow<List<AutosaveManager.DraftEntry>>` — replaces the old
    single-slot `_hasDraft` boolean; backs the StartScreen "Unsaved drafts" list
    (up to 3). Boolean-only callers derive from `drafts` directly.
  - `continueDraft(draftId)` — restores a specific ring entry into the editor and
    adopts its `draftId`/document name; session stays dirty until an explicit save.
  - `discardDraft(draftId)` — removes exactly one ring entry; if it was the current
    session, also resets the editor to a blank document (`newDocument()`, minting a
    fresh `currentDraftId`). No-arg `discardDraft()` discards the current session's
    own draft.
- Own **session-scoped undo/redo** — a single `SessionHistory<EditState>`
  (`ui/viewmodel/SessionHistory.kt`) recording every drawing-editor edit, not just
  deletes:
  - `EditState` (`ui/viewmodel/EditState.kt`) is the undoable slice: `spec`,
    `wearRecord`, `runoutReadings`, `undercutRecord`, `overallIsManual`. Metadata
    (customer/vessel/job number/notes/shaft position/unit) is deliberately **not**
    undoable, and neither is carousel row order — rows are derived from the spec
    (resolved components in physical order), so restoring the spec restores them.
  - A central collector (`combine(spec, wearRecord, runoutReadings, undercutRecord,
    overallIsManual)` in `init`) records an `EditState` on every emission via
    `editHistory.record(edit, System.currentTimeMillis())`. `SessionHistory` owns the
    policy: edits within 600 ms of the previous record coalesce into one undo step (a
    typing burst = one step), the stack caps at 50 (oldest evicted), redo clears on any
    genuine new state, and an identical-to-head state is a no-op.
  - `undoEdit()` / `redoEdit()` pop/push `SessionHistory` and apply the restored
    `EditState` back onto the five flows via `applyEditState()`, guarded by
    `isRestoringHistory` so the collector does not re-record the restore as a new edit
    (belt-and-suspenders — `SessionHistory.record`'s identical-state no-op is the
    authoritative backstop).
  - `canUndo` / `canRedo`: `StateFlow<Boolean>` mirroring `editHistory.canUndo` /
    `.canRedo`, updated by `updateHistoryFlags()` after every record/undo/redo.
  - History is dropped (`clearEditHistory()`) at every session boundary: `newShaft()`,
    `newDocument()`, `importJson()`, `continueDraft()`, and the autosave auto-restore
    path in `init` — undo must never cross back into a different document's state.
  - **Replaces the old delete-only undo entirely.** `LastDeleted`,
    `deleteHistory`/`redoHistory`, `isRedoing`, `canUndoDeletes`/`canRedoDeletes`,
    `undoLastDelete()`/`redoLastDelete()`, and `clearDeleteHistory()` are all removed;
    the delete snackbar's "Undo" action now calls `undoEdit()` (see `ShaftRoute.kt`).
    `removeX()` methods are unchanged in effect (body-merge behavior preserved) but no
    longer push their own per-delete history — recovery goes through the general
    session history like any other edit.

Add APIs
- `addLinerAt(startMm, lengthMm, odMm, reference: LinerAuthoredReference = AFT)` — the `reference` parameter records which end the user measured from; stored on `Liner.authoredReference` for the carousel edit card to display correctly. The default is `AFT` for the quick-add path which does not ask for a reference.
- `addTaperAt(startMm, lengthMm, startDiaMm, endDiaMm, rateText, reference: LinerAuthoredReference = AFT, keyway…)` — `startDiaMm`/`endDiaMm` arrive **x-ordered AFT → FWD** (the Add dialog orders the typed S.E.T./L.E.T. by the taper's physical half, `ui/input/TaperSetLetMapping.kt`); `reference` records the measured-from end and is stored on `Taper.authoredReference` so the carousel card reopens in that frame. Which end is the Small End — used to derive a missing diameter from the rate and to seed the next dialog's SET/LET defaults — comes from `taperSmallEndAtStart` against `oalAfterTaperAddMm(…)`, the OAL the shaft carries **after** the add (auto-OAL mode grows to cover the new span; a manual OAL stands).
- `addCouplerBoltSlotAt(startMm, holeDiaMm, count, spacingMm, through = true, depthMm = 0f, reference: SlotAuthoredReference = FWD)` — adds a coupler bolt-slot row. **Does not** call `ensureOverall()` (slots never drive OAL); no body split. Paired with `updateCouplerBoltSlot(index, …)`, `updateCouplerBoltSlotReference/Label/ShowRail`, and `removeCouplerBoltSlot(id)` (recoverable via the general `undoEdit()` session history; no body merge). See `CouplerBoltSlot.md`.

Do Nots
- Do not format values for display (UI edge only).  
- Do not perform rendering/layout math.  
- Do not mutate from inside composables; use explicit commit calls.

Notes
- Use `parseFractionOrDecimal` and `toMmOrNull` helpers for consistency.  
- Guard against negative lengths; no-op on invalid parse.  
- Emit minimal updates to avoid recomposition thrash.

Future Enhancements
- Debounced autosave.  
- Multi-spec project lists.

Change Log
----------
**v0.8 (2026-08-06)**
- **`componentOrder` removed.** The newest-first cross-type order (`_componentOrder`,
  `orderAdd`/`orderRemove`/`ensureOrderCoversSpec`, the `EditState.componentOrder` field and
  the `ShaftScreen`/`ComponentCarouselPager` pass-through) is gone: the carousel has rendered
  resolved components in **physical** order since the resolved pipeline landed, and nothing
  read the list. The undo/redo collector now combines five flows (`spec`, `wearRecord`,
  `runoutReadings`, `undercutRecord`, `overallIsManual`) through the typed `combine` overload
  instead of the `Array<Any?>` one. Nothing persisted changes — order was never in the
  document envelope. `ComponentKey`/`ComponentKind` stay (model-layer physical ordering,
  card/test tags). See `ComponentsOrdering.md` v1.3.
- **`addTaperAt` takes the authored reference** and orders diameters by the taper's physical
  half against the post-add OAL (see Add APIs above).

**v0.7 (2026-07-26)**
- **Session-scoped undo/redo replaces delete-only undo.** New `SessionHistory<EditState>`
  (`ui/viewmodel/SessionHistory.kt`, generic + pure) and `EditState`
  (`ui/viewmodel/EditState.kt`: spec + wearRecord + runoutReadings + componentOrder +
  overallIsManual) cover every drawing edit — not just deletes. Central recorder over
  the combined flows (`isRestoringHistory` guard), `undoEdit()`/`redoEdit()`,
  `canUndo`/`canRedo` `StateFlow`s; history cleared at session boundaries
  (`newShaft`/`newDocument`/`importJson`/`continueDraft`/autosave auto-restore). 600 ms
  coalescing window (typing burst = one step), 50-step cap, redo cleared on new state.
  Old delete-only machinery fully removed: `LastDeleted`,
  `deleteHistory`/`redoHistory`, `isRedoing`, `canUndoDeletes`/`canRedoDeletes`,
  `undoLastDelete()`/`redoLastDelete()`, `clearDeleteHistory()`. `removeX()` methods
  simplified (body-merge behavior preserved); the delete snackbar's "Undo" now calls
  `undoEdit()`. Tests: `SessionHistoryTest` (8), `ShaftViewModelUndoRedoTest` (3),
  `ShaftViewModelRemoveTest` migrated (+2 delete-undo-via-`undoEdit` recovery tests).

**v0.6 (2026-07-25)**
- Autosave draft-history rework (fixes the 2026-07-25 data-loss incident — see
  `docs/Autosave_Incident_2026-07-25.md`): `currentDraftId` (per-session identity)
  and `savedSnapshot` (dirty-gate baseline) added; `_hasDraft` boolean replaced by
  `drafts: StateFlow<List<AutosaveManager.DraftEntry>>`; new
  `continueDraft(id)`/`discardDraft(id)`, no-arg `discardDraft()` kept. The autosave
  observer now writes only when dirty (`shouldWriteDraft`) and removes the entry on
  the dirty→clean transition, instead of unconditionally overwriting a single slot
  on every change (including document loads).

**v0.5 (2026-07-11)**
- Added coupler bolt-slot APIs: `addCouplerBoltSlotAt`, `updateCouplerBoltSlot` (+ `Reference`/`Label`/`ShowRail`), `removeCouplerBoltSlot`. Slots never call `ensureOverall()` and never split bodies. (Historical note: delete-undo then went through `LastDeleted.CouplerBoltSlot`, since replaced by `SessionHistory`; the `Label` updater was deleted 2026-07-26 as dead.)

**v0.4 (2026-06-19)**
- `updateBody()`, `updateTaper()`, `updateLiner()`, `updateThread()` — removed `snapForwardFrom()` cascade. Editing a component now mutates only that component; other components' positions are completely untouched.
- Removed `_autoSnap` StateFlow, `autoSnap` property, and `setAutoSnap()`. (The explicit `snapChainFrom()` / `snapChainFromId()` entry points that briefly replaced auto-snap were themselves later removed unused — snapping lives only in coarse gestures, `ui/viewmodel/SnapUtils.kt`; the model-layer `ShaftSpec.snapForwardFrom` extension remains, exercised by tests.)

**v0.3 (2026-06-19)**
- `updateTaperAuthoredReference()` added — persists the user's AFT/FWD carousel reference toggle on `Taper.authoredReference`.
- `updateThread()` — `effectiveStart` for excluded threads now uses `−lengthMm` (AFT) / `overallLengthMm` (FWD) directly inside `_spec.update {}`, matching `syncExcludedThreadPositions()`. Eliminates a transient `0f` position that caused the thread to flash at the shaft face when the carousel committed on blur in manual OAL mode.
- Auto-snap removed from `removeBody()`, `removeTaper()`, `removeThread()`, `removeLiner()`.

**v0.2 (2026-06-18)**
- `addLinerAt` now accepts an optional `reference: LinerAuthoredReference` parameter (default `AFT`). Passed through from `AddLinerDialog` via `ShaftScreen` → `ShaftRoute` → ViewModel.

**v0.1 (2025-10-04)**
- Initial contract document.
