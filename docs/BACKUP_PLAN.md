# Backup & Restore — Implementation Plan
**Date:** 2026-05-27  
**Status:** Planning (no code changes)  
**Motivation:** User `.shaft` files live exclusively in app-private internal storage. An accidental reinstall (or deploying a different APK over the real app) causes Android to wipe that directory with no recovery path. This plan adds a two-tier safety net without touching the core model, storage contract, or PDF pipeline.

---

## Current State

- Saved shafts live at `<filesDir>/shafts/*.shaft` (app-private, inaccessible without root)
- `InternalStorage.kt` handles all reads/writes to that directory
- SAF integration already exists for single-file open/export (`SafRoutes.kt`)
- No mechanism exists to back up or bulk-export the library

---

## Proposed Architecture — Two Tiers

### Tier 1 · Passive — Android BackupAgent (low effort, automatic)

Android's built-in backup system (`BackupAgent`) hooks into Google's cloud backup infrastructure. When enabled, the OS automatically backs up nominated files when the device is idle/charging on Wi-Fi. On reinstall, Android prompts to restore.

**This is the tier that would have saved the lost shafts.** It requires no user action after installation.

**What to implement:**
1. Add `android:allowBackup="true"` and `android:backupAgent` to `AndroidManifest.xml` (may already be set — needs a check)
2. Create `res/xml/backup_rules.xml` to include the `shafts/` directory and exclude DataStore prefs (which are large and regenerable)
3. Implement a minimal `ShaftBackupAgent` extending `BackupAgentHelper` with a `FileBackupHelper` pointed at the `shafts/` subdirectory

**Limitations:** Not guaranteed (user may have backup disabled; debug builds with a different signing key don't trigger restore). It's a safety net, not a reliable export.

**Files touched:** `AndroidManifest.xml`, new `ShaftBackupAgent.kt`, new `res/xml/backup_rules.xml`  
**Estimated effort:** ~2 hours

---

### Tier 2 · Active — Bulk Export/Import via SAF (user-triggered, reliable)

A "Backup & Restore" section added to the existing Settings screen. User explicitly exports all shaft files to a ZIP, and can restore from that ZIP later. Works with Google Drive, Downloads, email, USB — wherever the user picks via the system file picker.

#### Export Flow
1. User taps "Export backup…" in Settings
2. System `CreateDocument` picker opens with a suggested name: `ShaftSchematic_backup_YYYYMMDD.zip`
3. App writes a ZIP to the chosen URI containing:
   - All `*.shaft` files from `InternalStorage`
   - A `manifest.json` with: app version, date, file count, format version
4. Snackbar confirms: "Backed up N shafts"

#### Restore Flow
1. User taps "Restore from backup…" in Settings
2. System `OpenDocument` picker opens, filtered to `.zip`
3. App reads the ZIP, validates the manifest format version
4. For each `.shaft` in the ZIP:
   - If a file with the same name already exists: skip (never overwrite)
   - Otherwise: write to `InternalStorage`
5. Snackbar confirms: "Restored N shafts (M already existed, skipped)"

**Key design decision — no overwrites on restore.** Restoring never deletes or replaces existing shafts. The user merges their backup into the current library. This makes it safe to restore without losing any work done after the backup was made.

**Files touched:**
- `data/BackupManager.kt` (new) — ZIP read/write, manifest
- `io/InternalStorage.kt` — no changes to existing API; `BackupManager` calls `list()` and `load()`
- `ui/screen/SettingsRoute.kt` — add "Backup & Restore" section with two buttons
- `ui/nav/SafRoutes.kt` or new `BackupRoutes.kt` — SAF composables for ZIP create/open
- `AndroidManifest.xml` — no new permissions needed (SAF doesn't require `WRITE_EXTERNAL_STORAGE`)

**Estimated effort:** ~half day

---

## What Does Not Change

- `InternalStorage.kt` public API — `save`, `load`, `list`, `delete`, `rename` are untouched
- `ShaftDocCodec` — no format changes; backup just stores the same JSON files
- The autosave / draft restore flow — unaffected
- The PDF export flow — unaffected
- The data model — no new fields anywhere

---

## Tier 3 · Future — Auto-Mirror Folder (v0.7.x)

A designated backup folder that the app mirrors to silently on every save, so the off-device copy is always current without requiring the user to remember to export.

- User picks a SAF folder once in Settings; app stores the persistent URI in DataStore
- `InternalStorage.save()` gains an optional mirror step that writes a copy to the SAF URI
- Requires `takePersistableUriPermission(FLAG_GRANT_WRITE_URI_PERMISSION)` on the folder URI

This is the most transparent option but requires careful handling of URI permission lifecycle (revoked permissions, folder deleted, etc.). Deferred to v0.7.x to keep this PR scoped.

---

## Recommended Implementation Order

1. **Tier 1 first** — BackupAgent is a 2-hour change that immediately protects all users who have Google backup enabled. No UI required.
2. **Tier 2 second** — ZIP export/import gives explicit, reliable, portable backup for all users regardless of backup settings. This is the feature that would let users move shafts between devices or recover from any scenario.
3. **Tier 3 later** — Auto-mirror is a quality-of-life upgrade once the basics are solid.

---

## Open Questions

- Does `AndroidManifest.xml` currently have `android:allowBackup="false"`? If so, flipping it to true is safe for existing installs.
- Should the ZIP include the autosave draft as well, or only explicitly saved shafts? (Recommendation: explicitly saved shafts only — autosave is ephemeral.)
- Should "Restore" show a preview list of what's in the ZIP before committing? (Nice to have, not required for v1.)
