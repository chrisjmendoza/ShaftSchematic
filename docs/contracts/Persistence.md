# Persistence Contract (storage, doc format, units policy)

Layer: I/O + doc codec  
Files: `io/InternalStorage.kt`, `doc/ShaftDocCodec.kt`, `data/AutosaveManager.kt`,
`data/DraftRing.kt`  
Version: v1.1 (2026-07-25) — adds the draft-ring autosave rewrite (was v1.0,
2026-07-18, which consolidated the former `InternalStorage.md` and `Units.md`).
Backup & restore design lives in `docs/archive/BackupRestore_Strategy.md`
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

## The two DataStores (`data/SettingsStore.kt`, `data/AutosaveManager.kt`)

The app keeps **two** preference stores, deliberately separate so a preferences problem and a
draft problem cannot take each other down:

| Store | Holds | Written |
|---|---|---|
| `settings` (`Context.settingsDataStore`) | units, theme, the whole drawing look, the mirror folder, migration/seeding flags | on each preference change |
| `autosave_datastore` (`AutosaveManager`) | the draft ring | **every 1.5 s of editing** |

Invariants
- **Both delegates carry a `ReplaceFileCorruptionHandler`, and it is load-bearing.** DataStore's
  unhandled answer to a truncated or garbage file is to throw `CorruptionException` from *every*
  read. Settings reads and `AutosaveManager.loadDrafts` both run during startup — the latter from
  the ViewModel's `init`, inside a `viewModelScope.launch` with **no handler above it** — so the
  failure mode is not "preferences went back to default" but the app crashing on launch,
  permanently, recoverable only by clearing app data, which takes the drawings with it. A tablet
  yanked off power mid-write is all it takes, and shop-floor devices get yanked off power. The
  autosave store is the more exposed of the two precisely because it is rewritten constantly.
  Replacing a file costs preferences or drafts and nothing else: saved shafts are files in
  `filesDir/shafts`.
- **Every settings read and write goes through one guarded seam** — `Context.settingsPrefs`
  (a `Flow` with `.catch { emit(emptyPreferences()) }`) and `Context.editSettings`. The
  corruption handler repairs a broken file once; these keep a read or write that fails for any
  *other* reason (I/O error, full disk) from propagating. Reads feed Compose collectors and
  writes are fired from `scope.launch` all over the UI with nothing catching above them, so an
  unguarded throw on either side is a crash. `CancellationException` is rethrown, never
  swallowed.
- **`AutosaveManager` never throws**, by the same rule `AppLog` follows: a draft is a safety net,
  and a safety net that crashes the app is worse than none. Every store access goes through its
  private `guarded` helper — reads degrade to an empty ring, writes to a breadcrumb — which is
  what its KDoc already promised for decode failures, now true at the store level too. It
  rethrows `CancellationException` because the autosave observer is a `collectLatest` that
  cancels the in-flight write on every newer snapshot.
- Same posture `decodeDrawingProfiles` takes one level up — a corrupt value may cost the presets,
  never the screen. `DataStoreCorruptionTest` pins both halves against a throwaway file (the real
  stores are process-wide singletons keyed by file, so a test that corrupted one would leak into
  every other test in the JVM worker).
- A store is a **singleton per file, not per `Context`**: tests must assert their own writes
  rather than an absolute default, or they depend on execution order.

---

## Backup zip reading (`io/ShaftBackup.kt`)

Invariants
- **Entry paths are reduced to their basename** before anything else, so an entry name from a
  foreign zip can never influence where a file lands (zip-slip).
- **Size caps are enforced on the bytes actually read, never on `ZipEntry.size`.** That field is
  `-1` for any entry written as a stream — which is how `ZipOutputStream` writes them, so it is
  `-1` even for this app's own backups — and `-1 > MAX_ENTRY_BYTES` is false. A declared-size
  check therefore rejects nothing at all: a 40 KB zip of compressible data expanded to 40 MB in
  memory, on a tablet, unchecked. `readEntryCapped` stops reading at the cap instead, and
  `MAX_TOTAL_BYTES` / `MAX_ENTRIES` bound the archive as a whole so many small entries cannot add
  up to the same problem. `ShaftBackupTest` pins it with a zip that is small on disk and huge
  decompressed.
- **An oversize or unreadable entry is skipped, not fatal**: one bad member of an otherwise good
  backup must not cost the user the documents beside it.

---

## SAF pickers (`util/SafPickerLaunch.kt`)

Every `ActivityResultLauncher.launch` in the app goes through `launchPicker`.

Invariants
- **A missing picker is a message, never a crash.** `launch` ends in
  `startActivityForResult`, so it throws `ActivityNotFoundException` when nothing handles the
  intent. DocumentsUI is always present on a normal phone and *not* guaranteed on the hardware
  this app targets — enterprise-locked and stripped rugged tablets ship without it or with it
  disabled — where the unguarded call turns every export and backup button into an app kill.
- The breadcrumb's `what` is a **fixed label chosen at the call site** ("backup", "export", …),
  never the picker input: those inputs carry customer, vessel and job text, and `AppLog` records
  events, never document content (see `docs/contracts/Diagnostics.md`).
- Surfaces with a snackbar pass `onUnavailable` and show `NO_PICKER_MESSAGE`; the export routes
  have no snackbar host and leave the breadcrumb alone, which is still the difference between a
  silent button and a mystery.

---

## Backup auto-mirror (`io/BackupMirror.kt`, `io/BackupMirrorPlan.kt`)

One SAF folder, picked once in Settings → Data ("Mirror saves to folder", persisted tree URI
with `takePersistableUriPermission` read+write); every internal document save mirrors a copy
there under the same filename, and a delete or rename follows it there too.

Invariants
- **Three choke points, all Context overloads**: `InternalStorage.save(ctx, …)` →
  `onDocumentSaved`, `delete(ctx, …)` → `onDocumentDeleted`, `rename(ctx, …)` →
  `onDocumentRenamed`, each one line after the internal operation and **only when it
  succeeded** — the folder copy of a document that is still here is a backup, not a leftover.
  Exclusions are **structural, not filtered**: templates, zip restores, and pre-update
  snapshots use the directory-taking `save(dir, …)`/`delete(dir, …)` overloads (no hook) or
  their own streams, and autosave drafts live in DataStore and never reach `InternalStorage`
  at all.
- **The mirror may never cost the operation anything**: fire-and-forget on `BackupMirror`'s own
  `SupervisorJob + Dispatchers.IO` scope, hooked strictly after the internal call returned;
  every provider call wrapped; concurrent writes and deletes serialize on one mutex.
- **A found-revoked permission never clears the stored URI** — re-granting the same folder
  must be enough to resume; only the user's explicit "Stop" clears it. Failures log on the
  VerboseLog IO channel plus quiet session-only status text on the Settings rows.
- **Overwrite-in-place by display name** (`planMirrorWrite`): an existing document is written
  over (`"wt"`); only a genuinely new name is created — an unconditional create would leave
  `" (1)"` duplicates on every save. Created as `application/octet-stream`, the one MIME type
  that leaves a `.shaft` name intact; names carrying a path separator are rejected.
- **Write and delete resolve a name through the same matcher** (`findMirrorEntry`, exact before
  case-insensitive). A delete that matched more strictly than the write would leave behind
  exactly the copy the write had been maintaining. A name that is not in the folder is a
  **silent no-op** (`MirrorDeleteTarget.NotPresent`) — the folder is allowed to be behind.
- **A rename is write-new-then-delete-old**, never `DocumentsContract.renameDocument`: tree-URI
  rename support varies by provider, and a rename that silently does nothing would leave the
  same stale copy. The content is read back from internal storage under the **new** name (the
  hook runs after the internal rename), and the old copy is dropped **only once the new one is
  provably written** — a copy under a stale name is a cheaper failure than no copy at all.
- **"Mirror all now"** (Settings → Data, shown only with a folder picked) copies every saved
  document through the same per-document write, reporting `BackupMirror.CatchUp` as
  "Mirrored N of M" (plus a failure count) in its own row's supporting line. It deliberately
  does **not** drive the per-save status line — that line says what the last single save did.
- Pure decisions (find-or-create, find-to-delete, should-mirror, folder label) in
  `BackupMirrorPlan.kt` (unit-tested); `BackupMirror.kt` is the thin untestable SAF shell.

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
- Mixed units + dual display: `unit_overrides` (resolved component id → `UnitSystem`) and
  `dual_units` are additive envelope fields with defaults (`emptyMap()`, `false`) that
  reproduce single-unit output exactly — an older file opens unchanged, and a file written
  now still opens in a build that predates them. Both ride the autosave/draft snapshot
  (`AutosaveManager.SessionSnapshot`), so a mixed-unit session survives a crash and a draft
  restore. They are **document state, not undoable** — the same posture as `RunoutConfig`'s
  slider slice: dirtiness is derived from `buildCurrentSnapshot()`, not from `EditState`.
  An override whose component id no longer resolves is **not pruned** (the resolver falls
  back to the document unit) — the render-layer posture, matching runout readings and wear
  pits. Per-component overrides travel with a template (they describe how the shaft is
  authored); the per-job `dual_units` flag does not. `ShaftViewModel.setDualUnits` also
  writes the global Settings default, so the toggle and Settings → Drawing →
  *Dual-unit display* stay in step; a document open then overrides the session with the
  document's own stored value. See `docs/DATA_MODEL.md`.

**Goal:** a shop can open any file, freely switch units, and print/export in the
desired unit without re-saving the document.

---

## Autosave / draft ring (`data/AutosaveManager.kt`, `data/DraftRing.kt`)

Root cause and full writeup of the 2026-07-25 data-loss incident that motivated this
design: `docs/archive/Autosave_Incident_2026-07-25.md`.

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
