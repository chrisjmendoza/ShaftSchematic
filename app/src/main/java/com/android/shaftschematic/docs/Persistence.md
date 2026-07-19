# Persistence Contract (storage, doc format, units policy)

Layer: I/O + doc codec  
Files: `io/InternalStorage.kt`, `doc/ShaftDocCodec.kt`, `data/AutosaveManager.kt`  
Version: v1.0 (2026-07-18) — consolidates the former `InternalStorage.md` and
`Units.md`. Backup & restore design lives in `docs/BackupRestore_Strategy.md`
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
