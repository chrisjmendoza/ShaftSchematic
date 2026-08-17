# Backup / Restore Strategy for Saved Shafts

**Date:** 2026-07-12
**Status:** ✅ Implemented 2026-07-12 (all four layers; see CHANGELOG). The
pruning root-cause fix uses a seed-time SHA-256 ledger — slightly stronger than
the byte-compare sketched below. Key code: `io/ShaftBackup.kt`,
`io/InternalStorage.kt` (ledger pruning), `ui/screen/SettingsRoute.kt` (Data
section), `ui/nav/InternalDocRoutes.kt` (Import / Save a copy),
`res/xml/{backup_rules,data_extraction_rules}.xml`.
**Driver:** Updates have gone wrong and deleted existing saves; need user-controlled
backup and restore so entered shafts are never unrecoverable.

---

## Current state (verified against code)

- Saved shafts are `.shaft` JSON files in the app-private sandbox:
  `filesDir/shafts/` (`io/InternalStorage.kt:42-44`). Saves are already atomic
  (`.tmp` write + `.bak` swap, `InternalStorage.kt:107-130`).
- Format is a versioned envelope (`ShaftDocV1`, `doc/ShaftDocCodec.kt`) with a
  non-destructive version guard — newer/unparseable files throw and surface a
  snackbar; they are never deleted on parse failure.
- The **only** SAF (system file picker) usage is PDF export
  (`ui/nav/PdfExportRoute.kt:69`). There is no shaft export, import, Save As,
  or backup UI anywhere. `SHAFT_MIME` exists in `DocumentFormat.kt:28` but is
  unused.
- `android:allowBackup="true"` with **no backup rules wired** — the stub
  `backup_rules.xml` / `data_extraction_rules.xml` files exist but are not
  referenced from the manifest.

## Root cause candidate for "update deleted my saves"

`filesDir` is not cleared on update, so a normal update cannot delete saves.
The one destructive path in the codebase is **sample pruning**:

`InternalStorage.prunePreviouslySeededBundledSamples` (`InternalStorage.kt:461-481`,
invoked from `seedBundledSamples` when the bundled-sample seed version bumps)
**deletes** any `.shaft` file whose base name matches a current bundled sample
and whose notes still carry the `[SAMPLE]` marker. A shaft entered by opening a
seeded sample, editing it, and saving under the same name (with the `[SAMPLE]`
notes prefix intact) is silently deleted at the first launch after an update
that bumps the seed version. This matches the reported symptom exactly.

**Fix regardless of any backup feature:** pruning must never delete a file the
user has touched. Only delete when the stored content is identical to what was
originally seeded (compare against the bundled asset, or store a content hash
at seed time). When in doubt, rename to `<name> (edited)` instead of deleting.

---

## Recommended strategy — four layers, in build order

### 1. Stop the bleeding: make sample pruning non-destructive (smallest, first)
As above. This is a bug fix, not a feature, and removes the known data-loss path.

### 2. Manual bulk backup + restore (the headline feature)
Settings screen gains two actions:

- **"Back up all shafts…"** → SAF `CreateDocument` for a single zip
  (`ShaftSchematic-backup-YYYY-MM-DD.zip`) containing every `.shaft` file plus a
  small `manifest.json` (app version, doc schema version, count, timestamp).
  The user picks the destination — Downloads, SD card, Google Drive — so the
  copy lives **outside the app sandbox** and survives uninstall, update, and
  device migration.
- **"Restore from backup…"** → SAF `OpenDocument`, reads the zip, validates each
  entry through the existing `ShaftDocCodec` guard, and imports with a
  collision policy of **rename** (`<name> (restored)`) — never overwrite
  silently, never skip silently; show a summary ("14 restored, 2 renamed").

Why zip-of-everything rather than per-file: the failure mode is losing the
whole folder, so the recovery unit should be the whole folder. One tap, one
file, no bookkeeping. `java.util.zip` — no new dependencies.

### 3. Per-shaft "Save a copy…" / "Import shaft…" (Save As to a different location)
Once the SAF plumbing from layer 2 exists, single-document export is nearly
free: `CreateDocument(SHAFT_MIME)` writing `vm.exportJson()` (the dead
`SHAFT_MIME` constant finally earns its keep), and `OpenDocument` → existing
`importJson()`. This covers the "save as to put them in a different location"
request and doubles as device-to-device sharing of a single design.

### 4. Automatic safety nets (defense in depth, zero user action)
- **Local rolling snapshots:** on first launch after `versionCode` changes,
  zip `shafts/` into `filesDir/backups/` before any migration/seeding runs;
  keep the last 3. If an update ever goes wrong again, "Restore from backup"
  can also list these internal snapshots. Cheap insurance aimed precisely at
  the "update goes wrong" scenario.
- **Wire up Android Auto Backup rules:** reference `data_extraction_rules.xml`
  from the manifest, include `filesDir/shafts/`, exclude the autosave DataStore.
  Google-account cloud restore on reinstall for free. Do not rely on this alone
  — it is opaque, quota-limited (25 MB, fine for JSON), and device-dependent —
  but it costs one manifest attribute and one XML file.

## What I would *not* build

- **Continuous sync to a cloud provider** — heavy (auth, conflict resolution)
  for a single-user shop tool; a dated zip in Drive via layer 2 achieves the
  recovery goal.
- **Changing the primary save location to user-visible storage (SAF tree)** —
  rewrites every save/load path and makes autosave latency subject to SAF;
  the sandbox + explicit backup copies is the better division.

## Suggested order & rough effort

| Step | What | Effort |
|------|------|--------|
| 1 | Non-destructive sample pruning + test | small |
| 2 | Zip backup/restore via SAF + Settings UI | ~1 day |
| 3 | Per-shaft export/import (Save a copy) | small, after 2 |
| 4a | Pre-migration rolling snapshots | small |
| 4b | Auto Backup rules wiring | trivial |

Key files: `io/InternalStorage.kt`, `doc/ShaftDocCodec.kt`,
`ui/screen/SettingsRoute.kt`, `ui/nav/AppNav.kt`, `AndroidManifest.xml`,
`res/xml/data_extraction_rules.xml`.
