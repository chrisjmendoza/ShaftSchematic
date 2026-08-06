# Persistence Contract (storage, doc format, units policy)

Layer: I/O + doc codec  
Files: `io/InternalStorage.kt`, `doc/ShaftDocCodec.kt`, `data/AutosaveManager.kt`,
`data/DraftRing.kt`  
Version: v1.1 (2026-07-25) — adds the draft-ring autosave rewrite (was v1.0,
2026-07-18, which consolidated the former `InternalStorage.md` and `Units.md`).
Backup & restore design lives in `docs/BackupRestore_Strategy.md`
(implementation: `io/ShaftBackup.kt`).

---

## InternalStorage (`io/InternalStorage.kt`)

Safe, app-scoped file API for saved shaft documents.

Invariants
- All paths sandboxed to a single `<filesDir>/shafts/` directory (`dir(ctx)`).
  There are **no separate exports/cache directories** — SAF export/sharing lives
  elsewhere, not here.
- Functions are **synchronous** (plain `File` I/O), not suspend — callers must
  dispatch to `Dispatchers.IO`. Exception: the bundled-sample-seeding entry points
  (`seedBundledSamplesIfNeeded`/`seedBundledSamples`) are `suspend` and internally
  `withContext(Dispatchers.IO)`.
- `.tmp`/`.bak` sibling files are invisible to `list`/`listWithMetadata`.

Responsibilities
- List/exists/save/load/delete/rename saved docs in `shafts/`.
- **Atomic save**: write `$name.tmp` → copy existing target to `$name.bak` (recovery
  copy) → delete old target → rename `.tmp` into place (copy+delete fallback).
  Process death or disk-full mid-write cannot corrupt the existing document.
- **Legacy migration**: `migrateLegacyJsonToShaft(ctx)` renames legacy `*.json` saves
  to `*.shaft`, suffixing `" (Migrated)"` / `" (Migrated N)"` on collisions.
- **Bundled-sample seeding**: copies `sample_shafts/` assets into `shafts/` on first
  run or seed-version bump. Decodes each asset via `ShaftDocCodec.decode()` (to derive
  a friendly filename and detect prior seeds); decode failures are skipped. Never
  overwrites a user document; collisions get `" (Sample)"` / `" (Sample N)"`.
- **Seed-hash ledger + non-destructive pruning**: seeded name → SHA-256 recorded via
  `SampleSeedSettings.setSeededSampleHashes`. On version bump, pruning deletes only
  ledgered files whose current content still byte-matches the recorded hash (provably
  untouched); user-edited files are left alone and dropped from the ledger. Files
  predating the ledger are never deleted.

Do Nots
- Do not expose absolute paths to UI.
- Do not assume any directory other than `shafts/`.

---

## Doc format & units policy (`doc/ShaftDocCodec.kt`)

- Canonical storage: **millimeters** in `ShaftSpec`; UI unit persisted to Settings
  as the "last used unit".
- Single envelope version `CURRENT_VERSION = 1` (`ShaftDocV1`) plus a bare-spec
  legacy fallback. `decode()` tries `ShaftDocV1` first (throws
  `UnsupportedDocVersionException` if `doc.version > CURRENT_VERSION`) and falls back
  to decoding raw JSON as a bare `ShaftSpec` for pre-envelope files.
- Export (`encodeV1` / `ShaftViewModel.exportJson()`): always serializes **both**
  `preferred_unit` and `unit_locked` (`encodeDefaults = true`).
- Import (`ShaftViewModel.importJson()`):
  - Envelope: `preferred_unit` applied via `setUnit(unit, persist = false)`;
    `unit_locked` decoded into `_unitLocked` session state, which only gates two
    internal bookkeeping paths (whether the background "last used unit" Settings flow
    may overwrite the selection, and whether a later `setUnit` persists as the new
    default). It never blocks switching units in the UI — **functionally inert** for
    "can the user change units on this file".
  - Legacy bare spec: current Settings default unit is used; doc treated as unlocked.
- Thread pitch/TPI: `decode()` calls `.normalized()` so metric-only (`pitchMm`) and
  imperial-only (`tpi`) saves both end up with both fields populated.
  See `Model_Conventions.md`.

**Goal:** a shop can open any file, freely switch units, and print/export in the
desired unit without re-saving the document.

---

## Autosave / draft ring (`data/AutosaveManager.kt`, `data/DraftRing.kt`)

Root cause and full writeup of the 2026-07-25 data-loss incident that motivated this
design: `docs/Autosave_Incident_2026-07-25.md`.

- **Storage**: one DataStore key, `autosave_drafts` — a JSON list of up to
  `DEFAULT_DRAFT_RING_MAX` (3) `DraftEntry(draftId, documentName?, updatedAtEpochMs,
  snapshot)`, newest-first. Replaces the old single-slot key `autosave_last_session`
  (still read once, for migration — see below).
- **Per-document identity**: `ShaftViewModel.currentDraftId` (a fresh UUID) is minted
  at construction and re-minted in `newDocument()` and `importJson()`. Working on one
  document's session can only ever upsert *that* document's ring entry — it can never
  overwrite another document's draft.
- **Dirty gate (the incident's actual fix)**: `DraftRing.shouldWriteDraft(current,
  saved)` — the 1.5 s-debounced autosave observer writes a `DraftEntry` only when the
  live session snapshot differs from `ShaftViewModel._savedSnapshot`, the last
  saved/loaded baseline (a `MutableStateFlow` since 2026-07-26, so the reactive
  `hasUnsavedChanges` flag re-evaluates on save). A freshly-opened, untouched document
  can never clobber an existing draft. The baseline is seeded blank at construction
  and reseated by `markDocumentSaved()` (all four explicit-save call sites route
  through it) and by `importJson()`/`newDocument()`.
- **Default-session gate (2026-07-26)**: the observer (and `hasUnsavedChanges`) also
  require `!snapshot.isDefaultSession()` (`DraftRing.kt`) — a factory-default session
  (empty spec, blank metadata) never writes a draft, no matter how it compares to the
  baseline. The predicate deliberately ignores unit/unit-lock/OAL-mode/runout-config:
  the async settings restore flips the unit preference after the baseline is seeded,
  which used to persist a phantom blank "Untitled draft" on every empty-ring launch.
  The observer's dirty→clean branch deletes previously-persisted phantoms on their
  next debounce tick. Do not "fix" the phantom by seeding the baseline later — the
  gate is the guarantee, not the seed timing.
- **Explicit-save removal (2026-07-26 fix)**: `markDocumentSaved()` removes the
  session's draft-ring entry **immediately**, gated on `draftPersisted`. Relying on
  the observer's dirty→clean branch alone was a bug: that branch only runs on the
  *next* combine emission, which never comes when the user saves and navigates away
  without editing again — saved documents lingered on the StartScreen as stale
  "Untitled draft" rows. `newDocument()`/`importJson()` drop `draftPersisted` to
  `false` *before* calling `markDocumentSaved()`, so open/new still never deletes the
  previous session's safety-net draft (the "Don't save" path keeps its draft).
- **Dirty → clean removal (observer backstop)**: if the live snapshot returns to
  matching the baseline while the session stays active (e.g. an edit is manually
  reverted), the observer removes that session's draft entry exactly once — saved
  work is not also listed as an unsaved draft.
- **Ring mechanics** (`DraftRing.kt`, pure, unit-tested, no `Context`/DataStore
  dependency): `upsertDraft(list, entry, max = 3)` replaces-and-moves-to-front an
  existing `draftId`, otherwise inserts at front; eviction is strictly
  oldest-by-`updatedAtEpochMs` (not list position), and only fires when the ring
  would exceed `max`.
- **Legacy migration**: `AutosaveManager.loadDrafts()` reads the old single-slot key
  once; if it holds a decodable snapshot, it is wrapped as a `DraftEntry`
  (`draftId = "legacy-migrated"`), merged into the ring, persisted, and the legacy
  key deleted. One-time, transparent to callers.
- **Restore-on-init**: same UX as before the rework — the newest draft auto-restores
  into a default/blank session at startup (`ShaftViewModel.init`), never into an
  already-initialized session.
- **UI surface**: `ShaftViewModel.drafts: StateFlow<List<DraftEntry>>` backs the
  StartScreen "Unsaved drafts" card (up to 3 entries, tap to
  `continueDraft(id)`, X icon → confirm → `discardDraft(id)`). See `Navigation.md`.

Invariants (see the incident doc's "Invariants going forward" for the authoritative list)
- A draft entry is written **only** for dirty (unsaved) sessions.
- Opening/creating a document must never mutate another document's draft entry.
- Explicit save removes the session's draft entry; discard removes exactly one entry.
- Ring capacity 3; eviction strictly oldest-first, and only on insertion of a new
  identity.
