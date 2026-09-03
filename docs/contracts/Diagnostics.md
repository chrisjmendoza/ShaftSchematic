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

**The attachment clip is built from the INTENT's mime type, never from a resolver lookup.**
`ClipData.newUri(resolver, …)` dereferences its `ContentResolver` for any `content://` URI —
and a FileProvider attachment is always `content://` — so the `null` resolver this once passed
made every tap of this button an NPE on the spot (on-device report: the app died twice, and
surviving a cache clear ruled the log files out). `FeedbackIntentFactory.setClipDataForUris`
therefore constructs the clip with the intent's own type, the construction AOSP's
`Intent.migrateExtraStreamToClipData` uses; `FeedbackIntentFactoryTest` pins it and fails with
that exact NPE on the old call. The clip has to carry **every** attachment, not just the first:
it is what propagates `FLAG_GRANT_READ_URI_PERMISSION` to whichever app the chooser picks.

The tap handler additionally catches `Throwable` — breadcrumb + "Could not share the logs."
snackbar — which is `AppLog`'s own "logging may never become a failure" posture applied to the
button that ships it. This screen is where a stuck tester is sent; a crash here takes the app
down *and* destroys the evidence instead of mailing it.

## The "may never become a failure" rule

Three subsystems exist to make failures survivable, and each is therefore held to the rule that
it may never *cause* one. They are listed together because the rule is the same and the
temptation to break it is the same — a `runCatching` around housekeeping looks like sloppiness
until you notice what the alternative crashes:

| Subsystem | Degrades to | Because |
|---|---|---|
| `AppLog` | a dropped line | it records the failures it must outlive |
| `AutosaveManager` | an empty draft ring | it runs from `viewModelScope` in the ViewModel's `init`, with no handler above it — a throw is a crash on launch |
| `SettingsStore` reads/writes | the default value, an unlanded write | reads feed Compose collectors; writes fire from bare `scope.launch` all over the UI |

Two shared constraints:
- **`CancellationException` is always rethrown, never swallowed.** The autosave observer is a
  `collectLatest` that cancels the in-flight write on every newer snapshot; eating that breaks
  the structured concurrency the debounce depends on.
- **The breadcrumb is still written.** Degrading quietly and degrading *silently* are different
  things — the second is how a bug survives a release.

There is deliberately **no `CoroutineExceptionHandler`** anywhere in the app. Its absence is what
makes the rule above load-bearing rather than belt-and-braces: nothing catches a throw that
escapes a `launch`, so every such surface has to hold its own.

---

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
