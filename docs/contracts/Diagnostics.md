# Diagnostics — AppLog, Share logs, CrashReporter

The evidence system for builds running on other people's phones. Two channels with distinct
jobs; do not merge them:

| Channel | Gate | Sink | Job |
|---|---|---|---|
| `util/VerboseLog` | Dev options + master verbose + category | logcat only | High-volume tracing at a desk with a cable |
| `util/AppLog` | **none — always on** | ring file + logcat mirror | Sparse breadcrumbs a tester can mail back after the fact |
| `util/CrashReporter` | Firebase config present | Crashlytics | Crashes + recorded non-fatals from distributed builds |

## AppLog — the always-on breadcrumb file

- **Ring**: lines append to `filesDir/logs/log.0.txt`; when a line would push it past 256 KB
  (`DEFAULT_MAX_FILE_BYTES`), the file rotates to `log.1.txt` (overwriting) and a fresh one
  starts. Two files, 512 KB cap total, bounded without a cleanup task; the older half holds the
  run *before* the one that broke.
- **Posture**: initialized by `ShaftSchematicApp` (the Application class exists for exactly this
  wiring and nothing else); every call before `init` is a no-op; every path swallows its own
  errors. Logging exists to explain a failure — it may never become one. Writes serialize on a
  single min-priority daemon thread; `flushBlocking` drains the queue (the crash handler's
  need). Pure seams (`shouldRotate`, `formatLine`, `throwableText`) are unit-tested without
  large files.
- **PRIVACY RULE** (also in CLAUDE.md): breadcrumbs record **events, never content**. What
  happened, which branch, an exception with its stack — yes. A document field value, a geometry
  number, a diameter, a reading — never. Document NAMES are allowed: the person sharing the log
  owns them, and a failure is unreadable without knowing which document it hit. When in doubt,
  log the event and leave the value out.
- **Sites** (keep them sparse — this file is for turning-point events, not tracing): all
  `PdfSafExport` branches (start / ok / composer throw with stack / no stream / tree-create
  failure), `InternalStorage` save/load/delete/rename/migrate, `BackupMirror` failures (beside
  the existing `VerboseLog.e` lines; successes stay VerboseLog-only), the autosave observer and
  restore catches, `.shaft` import failure, backup-zip failures, app start (version + GIT_SHA +
  crash-reporting state).
- **Crash handler**: `installCrashHandler` wraps the previously-installed default handler —
  write the crash locally, `flushBlocking`, then **delegate**. Delegation is the point:
  Crashlytics installs its handler during Firebase auto-init (before `Application.onCreate`),
  so installing ours in `onCreate` captures theirs as the delegate and both reports survive.
  Idempotent (Robolectric instantiates the Application per test).

## Share diagnostic logs

Settings → Data → "Share diagnostic logs", deliberately LAST in the section (rarely-used
options sit last). Reads `AppLog.logFiles()` at tap time (not composition — the log grows while
the screen is open), builds an email through the existing `FeedbackIntentFactory` machinery
(`createDiagnostics` — `createRaw` + `uriForFile`, FileProvider `files-path` root already in
`file_paths.xml`), with snackbar fallbacks for "no logs yet" and no email app. Robolectric
cannot resolve FileProvider roots, so the test pins that log files live under `filesDir`;
the actual share is a one-tap on-device check.

## CrashReporter — Firebase as optional configuration

- `app/google-services.json` is **gitignored** and belongs to the Firebase project, not the
  repo. `app/build.gradle.kts` applies the `google-services` + `firebase-crashlytics` plugins
  **only when the file exists**; the Crashlytics SDK itself is an unconditional dependency so
  the classes always compile. CI (`distribute.yml`) materializes the json from the
  `GOOGLE_SERVICES_JSON` secret before `assembleDebug`; an unset secret builds green with
  reporting inactive.
- `CrashReporter` is the **one seam**: `isActive` resolved once at Application init
  (`FirebaseApp.getApps` non-empty — the auto-init ContentProvider has run or had no config),
  `log`/`recordNonFatal` no-op when inactive. Nothing outside that file may call Firebase
  directly; a direct `FirebaseCrashlytics` call throws on exactly the builds with no json.
- **Collection stays ON in debug builds.** CI distributes the debug variant to testers; a
  debug opt-out would silence the only crashes anyone will ever see.
- `recordNonFatal` is for failures the code recovered from and would otherwise be invisible —
  the canonical site is `PdfSafExport`'s composer catch (the user sees an error page; the
  throwable behind it goes to AppLog with its stack and to Crashlytics as a non-fatal).

## Versions

Firebase BOM 34.18.0, google-services 4.5.0, crashlytics gradle plugin 3.0.8 — chosen against
AGP 9.3.1 / Kotlin 2.3.20 without moving AGP, Kotlin, compileSdk, or the Compose BOM (those
bumps are deferred deliberately; see TODO §Build tooling).
