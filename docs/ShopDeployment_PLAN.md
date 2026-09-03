# Shop Deployment — Users, Manager Lock, Audit Trail, Shop Oversight (PLAN)

Status: **DRAFT spec, 2026-09-02, revised 2026-09-03 — nothing built.** Written to turn "I'm
starting to think about shop deployments" into a buildable plan. §7 holds the product questions;
the doc gets rewritten around the answers, the way `DESIGN_INTENT.md` was. Nothing here overrides
`DESIGN_INTENT.md` or `CLAUDE.md`; where this plan proposes bending a standing rule it says so.

The 2026-09-03 revision reviewed the draft from the **shop owner / foreman's** seat rather than
the developer's. The first draft answered "how do we stop a worker destroying work"; a foreman's
daily questions are different — *which paper on the lathe is current, where is the drawing for
that boat, is the backup actually working, who took these readings* — and the plan now answers
those too. The revision also loosened the lock defaults (trash makes a delete gate redundant),
fixed a default that would have blocked the normal draftsman → machinist handoff, and cut the
security back to what internal shop paper warrants. Each change is marked **[rev]** in the body.

Related: `docs/DESIGN_INTENT.md` (§1.1 all documents are internal shop paper; §3.3 look prefs
are app-wide), `docs/contracts/Persistence.md`, `docs/contracts/Diagnostics.md`,
`docs/MultiShaftJob_Plan_2026-07-26.md` (document identity today is a filename).

---

## 0. Starting point — what exists today

Everything a deployment story needs is currently absent, so this is additive greenfield, not
an extension of something half-built. The audit that produced this plan found:

| Concern | Today |
|---|---|
| Identity | None. No user, author, operator, or device field anywhere — not in the envelope (`doc/ShaftDocCodec.kt` `ShaftDocV1`), not in settings, not in logs. |
| Timestamps | None stored. The only "when" is the filesystem `lastModified()`, used for the Saved-list relative date, reset by every write including restores. |
| Document identity | The filename. Rename = new identity (`MultiShaftJob_Plan` §2). |
| Delete | Hard `File.delete()` (`io/InternalStorage.delete`). No trash, no undo, no versioning beyond one `.bak` sibling overwritten on the next save. |
| Who can delete | Anyone holding the device: Open screen → row overflow → Delete → confirm. |
| Auth / lock | None. No PIN, password, biometric, keystore, or `security-crypto` dependency. The only "unlock" is Developer Options (7 taps on App Version, no re-lock, no confirmation) — a debug gate, not a protection. |
| Logging | `util/AppLog` — always-on, event-only breadcrumbs (never content), 2 × 256 KB ring, shared as an email attachment. Save/load/delete/rename/export/import already breadcrumb, but nothing attributes them to a person, and the ring overwrites itself. |
| Undo | `SessionHistory` — in-memory, cleared at every new/open/import. Not an audit source. |
| Backup | Zip of the library + manifest; restore never overwrites (renames "(restored)"). Per-device auto-mirror SAF folder. Android cloud backup (`allowBackup=true`) may carry `shafts/` to the signed-in Google account. |
| Distribution | Firebase App Distribution of a **debug-signed** build (shared committed `debug.keystore`) to one hardcoded tester email. No release signing key, no Play listing. |
| Multi-device / cloud | Cloud sync is an explicit non-goal (`TODO.md` §4). Each device is an island. |
| **[rev]** Finding a drawing | The Open screen's search (`ui/nav/InternalDocRoutes.kt`) matches the **filename only**; Customer / Vessel / Job # / Item are never read for the list. Sort is Name or Date. A row shows filename + relative file date, nothing about who saved it. |
| **[rev]** Mirror health | A failed mirror write is deliberately silent at save time (`BackupMirrorSection.kt`: a save must never be interrupted by a backup problem); it breadcrumbs to `AppLog` and shows as error-coloured supporting text inside Settings until the next success. No "last mirrored at" time exists anywhere, and nothing outside Settings says the mirror has been failing for a week. |
| **[rev]** What the printed sheet says about itself | The footer (`pdf/SheetFooter.kt`) prints Customer / Vessel / Job # / Item, taper and thread specs, and a **print-time** date (`SimpleDateFormat("yyyy-MM-dd").format(Date())` — there is no user-typed date field). No revision, no "printed by", no document id; the `filename` / `appVersion` parameters `drawFooter` takes are never drawn. |

Two precedents worth reusing: the envelope's additive-field-with-default pattern (a new field
old files decode without), and `AppLog`'s privacy rule (events, never content). One precedent
to explicitly **not** imitate: the 7-tap Developer Options unlock.

---

## 1. Goals and non-goals

**Goals**
1. A **manager** can configure a device so a **worker** cannot destroy existing work
   (delete forever, wipe the library with a restore, empty the trash).
2. The app knows **who is using it** well enough to stamp documents and log actions — without
   turning a shop tablet into a login wall that slows the daily "open, measure, print" loop.
3. A durable **audit trail**: who created/opened/changed/printed/deleted what, and when — on
   the device, exportable, and travelling with the document where that makes sense.
4. Basic **security hygiene** proportionate to internal shop paper (DESIGN_INTENT §1.1): a
   manager credential that can't be trivially bypassed, a session that ends when the tablet is
   left on the bench, and no accidental off-site copies the shop didn't ask for.
5. **[rev] Shop oversight** — the foreman's questions answered without opening a log: the
   **printed sheet identifies itself** (revision, who printed it, when) so the wrong paper on
   the lathe is detectable; a drawing is **findable by boat, customer, or job number**, not only
   by filename; a manager is **told on the Start screen when the backup has stopped working**;
   and the QC record (wear/runout readings) says **who measured and when**, separately from who
   drew.

**Non-goals (unless §7 answers change them)**
- No server, no accounts in the cloud, no network auth. Identity is **device-local**.
  (`TODO.md` §4 stands.)
- No encryption of `.shaft` files at rest. The Android app sandbox plus device lock is the
  boundary for internal shop paper; the credential store gets hashing and rate limiting, the
  drawings do not. **[rev]** Nor does the users store get an encrypted-file wrapper — see §4.1.
- No per-user *settings*. Drawing look stays app-wide (DESIGN_INTENT §3.3); what changes is
  **who may change it**.
- No cross-device sync of documents or users. A "shop config" export/import (§5.3) is the
  most this plan offers.
- **[rev]** No job scheduling, no status board, no time tracking. The one workflow state this
  plan adds is the per-document **Released** flag (§2.4a), because it protects the paper on the
  machine; anything beyond that is a different product.

---

## 2. Model

### 2.1 Users and roles

```
User(id: String /*UUID*/, name: String, role: Role, pinHash: String?, active: Boolean,
     createdAtEpochMs: Long)
enum Role { MANAGER, WORKER }
```

- Stored in a new DataStore file `users` (not `SettingsStore` — settings are look/behaviour
  prefs and are captured by Drawing profiles; users are not a look).
- **First run** on a device with no users: the app behaves exactly as today (single implicit
  user, no lock, no prompts). Nothing in this plan changes a device that never sets up users.
  Setting up the first manager is the opt-in.
- **Managers** always carry a PIN (§4.1). **Workers** may or may not, per policy (§7 Q3):
  the cheapest shop flow is "tap your name" with no PIN — enough for attribution, not for
  proof — and the plan supports both.
- A user is deactivated, never deleted: audit records and document stamps keep pointing at a
  real name.
- **[rev]** In a small yard the owner is often out on a boat. The plan expects **two managers
  as the normal case** (owner + foreman) rather than one, and the setup flow says so — the
  elevation model (§2.2) only works if a manager is physically reachable.

### 2.1a Device identity **[rev]**

```
DeviceConfig(deviceId: String /*UUID, minted once*/, deviceName: String /* "Lathe 2 tablet" */)
```

Manager-set on each tablet (Settings → Shop → This device). With two or three tablets sharing
a roster through §5.3, "who" is not enough: the audit record, the document `history`, and the
footer print stamp all carry **`deviceName`** so a manager reading a month's log or a sheet on
the floor knows which tablet it came from. Stored beside `users`, not in `SettingsStore`, and
**not** captured by Drawing profiles.

### 2.2 Session

```
Session(userId, startedAtEpochMs, elevatedUntilEpochMs?)
```

- One active session per device, held in the app process and mirrored to DataStore so a
  process death does not silently sign the worker out mid-job (the autosave draft ring is
  keyed per session today; that stays).
- Ends on explicit sign-out, on idle timeout (§4.2), or on app background for longer than
  the timeout. The **unsaved-changes guard and autosave ring are untouched** — a sign-out
  saves the draft first, exactly as Close Document does.
- **Elevation**: a worker hitting a locked action may hand the tablet to a manager, who
  enters their PIN to allow **that one action** (audit records both users). No persistent
  elevation — the manager does not become signed in.

### 2.3 Lock policy (manager-set, device-wide)

```
LockPolicy(
  workersMayDelete: Boolean = true,           // [rev] saved documents → trash (recoverable, so open by default)
  workersMayEditReleased: Boolean = false,    // [rev] edit a document a manager has marked Released (§2.4a)
  workersMayRename: Boolean = true,
  workersMayRestoreBackup: Boolean = false,   // restore a zip over the library
  workersMayEmptyTrash: Boolean = false,      // empty trash / delete forever
  workersMayEditTemplates: Boolean = false,   // [rev] change a shop template (when the templates branch lands)
  workersMayChangeDrawingLook: Boolean = true,// PdfPrefs + line thickness + profiles
  workersMayChangeDataSettings: Boolean = false, // mirror folder, backup, users, device name
  workersMayUnlockDevOptions: Boolean = false,
  idleTimeoutMin: Int = 60,                   // [rev] 0 = never; default one hour (§4.2)
  workerPinRequired: Boolean = false,
  trashRetentionDays: Int = 30,
)
```

**[rev] Gate only what cannot be undone.** The first draft gated delete and gated saving over
a drawing someone else last saved. Both are now open by default, for two reasons:

- **Trash makes the delete gate redundant.** A trashed drawing is recoverable for
  `trashRetentionDays`; the second layer of defence (recoverable) already covers the case the
  first (permission) was for. Gating both costs a manager PIN on every cleanup, and with the
  owner out of the building that stalls the shop. The manager-only set is the **irreversible**
  set: empty trash / delete forever, restore a backup over the library, roster and policy
  changes, device name, Developer Options.
- **The overwrite gate blocked the normal handoff.** Runout and wear readings live *inside*
  the same document as the drawing (`wear_record`, `RunoutReadings` in the envelope). The
  person who measures and draws is rarely the person at the lathe who adds the TIR readings
  afterwards — so "workers may not save over a drawing someone else last saved" would have put
  a manager PIN in front of **every reading entry**. The rule that actually matches the shop is
  the per-document **Released** flag (§2.4a): a drawing is free to edit until a manager releases
  it to the floor, and after that an edit needs elevation or a Save-as. `workersMayEditReleased`
  is that flag's gate; `workersMayOverwriteOthers` is dropped.

**[rev] Two things stay ungated on purpose.** "Share diagnostic logs" is content-free by
contract (`docs/contracts/Diagnostics.md`) and is how a bug report gets off the floor; it is
**not** under `workersMayChangeDataSettings`. And printing / exporting are audited, never
restricted (§4.3) — printing is the product.

Every gate is one flag, one place — `Guard` (§3.4) — so the dialog, the audit event and the
policy screen cannot drift.

The `workersMayChangeDrawingLook` flag is where this plan meets DESIGN_INTENT Q3: the look is
app-wide *because* a job's sheets must read as one family; in a shop that becomes "the
manager sets the look, workers tune the per-job fit" — the sheets' `OptionsScopeNote` already
tells a worker which rows are which.

### 2.4 Document stamps (envelope, additive)

New optional fields on `ShaftDocV1`, all defaulted so every existing file still decodes:

| Field | Serial name | Meaning |
|---|---|---|
| `docId` | `doc_id` | UUID minted at first save; survives rename, duplicate-for-mate mints a new one; restore keeps it. The audit trail keys on this, not the filename. |
| `createdBy` / `createdAtEpochMs` | `created_by` / `created_at` | User **name** + id at first save (name denormalised so the file reads on a device without that user). |
| `modifiedBy` / `modifiedAtEpochMs` | `modified_by` / `modified_at` | Last save. |
| `revision` | `revision` | Monotonic save counter. **[rev]** Printed on every sheet (§2.4b) — this is what makes it worth having. |
| `history` | `history` | Last **N = 20** `DocEvent(at, by, device, kind)` — save / rename / restore / import / release / print — so a file carries its own recent provenance through backup, mirror and import. Content-free, like every log line. **[rev]** carries `deviceName`. |
| **[rev]** `released` / `releasedBy` / `releasedAtEpochMs` | `released` / `released_by` / `released_at` | §2.4a. |

Golden rule unchanged: stamps are metadata the *system* writes; no user-typed value is touched.
A legacy/unstamped file gets stamped on its next save, never on open. The Project Info sheet
shows the stamps read-only ("Created by … · Last saved by … on … · Rev 7 · Released by … on …").

#### 2.4a Released **[rev]**

A manager marks a drawing **Released** when it goes to the floor (Project Info sheet, or the
Open-screen row menu). Semantics:

- Editing a released drawing asks `Guard` (`workersMayEditReleased`, default off): a worker
  sees the `LockedActionDialog` with a third choice — **Save as new revision**, which duplicates
  the document (new `docId`, `revision` restarts, `history` notes "revised from <docId>") and
  leaves the released one untouched. Elevation edits it in place and bumps `revision`.
- **Readings are not edits.** Adding or changing wear/runout readings on a released drawing is
  the QC step *after* release and must stay open to whoever holds the calipers. The
  `Released` gate covers `ShaftSpec` (geometry, footer fields, keyways, per-component display
  flags); it does not cover `wear_record`, `runout_readings`, `runout_stations`, or
  `RunoutConfig`. `Guard` takes the edit's *kind*, not just the document.
- Un-release is manager-only and audited (`doc.unrelease`).
- Managers are **not** exempt from the released check on their own edits — the dialog just
  resolves with their own PIN. The point of the flag is that changing a released drawing is
  never silent.

#### 2.4b The printed sheet identifies itself **[rev]**

Wrong paper on the machine is how a shaft gets scrapped, and today two prints of the same
drawing a week apart are indistinguishable except by the print date. Every composer's footer
gains **one short stamp line** at the footer's foot, in the smallest footer type:

```
Rev 7 · printed 2026-09-03 14:12 by C. Mendoza · Lathe 2 tablet · a3f9c2
```

- `Rev` is `revision` at print time; a released drawing prints **`Rev 7 · RELEASED`**, an
  unreleased one prints **`Rev 7 · DRAFT`** once the device has users (a device with no users
  prints neither word — nothing changes for a solo user).
- `printed …` replaces the existing generated footer date (the same `Date()` call; it moves,
  it is not duplicated). `by` is the session user; `Lathe 2 tablet` is `deviceName`; the
  trailing token is the first six characters of `docId`, so a sheet can be matched to its file
  after a rename.
- The existing dead `filename` / `appVersion` parameters on `drawFooter` are removed or put to
  use here — not left dangling.
- This is the ONE place the plan touches a drawing's face. It costs one footer line, which
  matters on exactly the long shafts with the least room (the compression-note lesson in
  `CLAUDE.md`): the stamp therefore takes the **same line** the generated date already occupies
  today, and blank drafts print it too (a blank sheet is still a specific revision).
- Nothing else from the audit trail ever prints on a drawing.

#### 2.4c Stale-save guard **[rev]**

If the mirror folder becomes a shared library (§7 Q1), two tablets can each open a copy of
one file and the second save silently wins. With `docId` + `revision` in the envelope this is
detectable for free: on save, if the file on disk carries the same `docId` and a **higher
`revision`** than the one this session opened, warn — *"Someone saved a newer revision of this
drawing (Rev 9, by …, on Lathe 2 tablet). Save over it, or Save as a copy?"* — and audit
either choice. No merge, no lock; one honest dialog.

#### 2.4d Measured-by stamps on readings **[rev]**

The wear and runout records are the shop's QC evidence, and "who measured and when" is a
different fact from "who last saved the drawing". `WearRecord` and `RunoutReadings` each gain
optional `measuredBy` / `measuredAtEpochMs` (system-written when their readings change,
additive, defaulted, content-free) — shown read-only on their tabs and in Project Info, never
printed on the sheet unless §7 Q12 says so. Same posture as every other stamp: the system
writes it, nothing rewrites a typed value.

### 2.5 Trash (soft delete)

`shafts/.trash/<docId>.shaft` + a sidecar `<docId>.meta.json` (original name, deleted-by,
deleted-at, device). Delete on the Open screen becomes **Move to trash** for everyone —
managers included — because the recoverable layer is worth having even when the permission
layer passes. Trash shows on the Open screen behind a "Trash (n)" row: Restore (refuses to
overwrite a live name, appends "(restored)" — the backup rule), Delete forever (manager or
elevation). Retention sweep on app start per `trashRetentionDays`.

**[rev] The mirror deletes only on purge.** The first draft mirrored a trash move as the
delete it already mirrors today — which would remove the copy on the shop PC while the tablet
copy was still recoverable, i.e. the copy the owner actually trusts would be the first to go.
A trash move now leaves the mirrored file in place; the mirror removes it on **purge**
(delete forever or retention sweep), and restore-from-trash re-mirrors. The zip backup
**includes** trash (manager-only on restore, §5.2) for the same reason.

### 2.6 Audit log

A **separate** channel from `AppLog` — `util/AuditLog` — because `AppLog` is a diagnostic ring
that is *supposed* to overwrite itself and be emailed to a developer, and an audit trail is
supposed to persist and be read by a manager. Sharing one file would put the wrong retention
and the wrong audience on both.

- **Format**: append-only JSONL, one file per month at `filesDir/audit/YYYY-MM.jsonl`.
  Retention manager-set (default: keep everything; §7 Q7).
- **Record**: `{ts, userId, userName, role, deviceId, deviceName, event, docId?, docName?,
  revision?, detail?, byUserId? (elevation), appVersion}`. `detail` is a short enum-ish string
  ("policy.workersMayDelete=false", "export.consolidated", "print.runout") — **never a document
  field value** (AppLog's rule, now load-bearing for a second file). **[rev]** `deviceName` and
  `revision` added.
- **Events** (the full list is the contract; each is one call in exactly one place):
  `user.signIn`, `user.signOut`, `user.timeout`, `user.elevate`, `user.denied`,
  `doc.create`, `doc.open`, `doc.save`, `doc.saveAs`, `doc.saveOverNewer` **[rev]** (§2.4c),
  `doc.rename`, `doc.duplicate`, `doc.release` / `doc.unrelease` **[rev]**, `doc.trash`,
  `doc.restore`, `doc.purge`, `doc.import`, `doc.export.<kind>`, `doc.print.<kind>`,
  `draft.discard`, `backup.write`, `backup.restore`, `mirror.failed` / `mirror.recovered`
  **[rev]** (§3.6), `settings.look` (which pref), `settings.data`, `policy.change`,
  `users.change`, `device.rename` **[rev]**, `template.change` **[rev]**, `devOptions.unlock`.
- **Tamper-evidence** — **[rev] deferred to Phase 3, and only if asked** (§4.1, §7 Q8). The
  file lives inside the app sandbox; a worker cannot reach it without a cable and a rooted
  tablet, and a shop that needs to prove more than that to an insurer will say so.
- **Surface**: Settings → **Activity** (manager-only): filter by user / document / device /
  event / date, and **Export** (CSV or the raw JSONL) through the same SAF path exports use.
  **[rev]** A one-line summary at the top — *"This month: 14 drawings created · 9 printed ·
  3 released · 2 trashed"* — is the owner's "how busy were we" answered from data already
  logged. Nothing here is ever printed on a drawing (the footer stamp in §2.4b is the
  document's own metadata, not the log).

---

## 3. UX

### 3.1 Sign-in

- A device with users shows a **Who's working?** screen ahead of Start: a grid of name
  buttons (managers marked). Tap = signed in (worker without PIN) or a numeric PIN pad.
  One tap for the common case; the shop loop is not slowed.
- The current user's name sits in the editor's sidebar footer with **Switch user** — the
  hand-the-tablet-over case. No sign-out button anywhere else.
- **[rev] Catch the wrong name where it matters.** Under tap-only sign-in the log is a claim,
  and the usual failure is the previous worker never switching. The save flow shows
  **"Saving as Chris"** (quick-save snackbar text and the Save-as dialog title), and the print
  sheet shows *"Printed by"* next to the Print button — the two moments a wrong name lands in a
  stamp are the two moments it is shown.

### 3.2 A locked action

One dialog, `LockedActionDialog`: *"Only a manager can delete saved drawings. Ask a manager
to enter their PIN, or cancel."* — PIN pad inline. Success runs the action once and logs
`user.elevate` + the action with `byUserId`. Failure logs `user.denied`. Never a silent
disable: a worker should see the affordance exists and who can unlock it (the "why is Export
greyed out" FAQ lesson). **[rev]** For a released drawing the dialog carries the third choice,
**Save as new revision** (§2.4a), so a worker is never simply stopped.

### 3.3 Manager screens (Settings, manager-only section **Shop**)

- **Users** — add / rename / deactivate / reset PIN / role.
- **This device** **[rev]** — device name (§2.1a).
- **Lock policy** — the §2.3 flags as switches with one-line captions, idle timeout, trash
  retention.
- **Activity** — the audit viewer + export + monthly summary line.
- **Shop config** (§5.3) — export / import users + policy.

`SettingsRoute`'s existing sections are untouched; **Shop** is a new section that only renders
for a signed-in manager (and never on a device with no users).

### 3.4 One gate

`Guard.check(action: LockedAction): GuardResult` — pure, unit-tested — takes the session, the
policy and (for released documents) the target's `released` flag and the edit's kind, and
returns Allowed / NeedsManager / Denied. Every call site (Open-screen row menu, quick-save,
Save-as, draft discard, backup restore, Settings entries, template edit, Dev Options unlock)
asks it and nothing else. This is the `blockingExportError()` posture from `TODO.md` §5: one
gate, no secondary ones.

### 3.5 Finding a drawing **[rev]**

The Open screen's search matches the filename only, and the library is where the shop's
memory lives — "the shaft for the *Sea Witch* two years ago" is the real query, and the boat's
name is in the footer fields, not the filename.

- **Search matches Customer, Vessel, Job #, and Item** as well as the filename. The list
  already reads each file for name + `lastModified`; reading `ProjectInfo` too is one envelope
  decode per row, cached per file mtime in a small sidecar index (`shafts/.index.json`,
  rebuilt lazily, never authoritative — the files are). The row shows the matched field under
  the name ("Vessel: Sea Witch") so a hit on a footer field explains itself.
- **Rows show "saved by"** once stamps exist (`modifiedBy`, device), and the sort chips gain
  **Saved by**; a **Mine** filter chip shows the signed-in user's drawings.
- **Released** drawings carry a small badge in the list, and the Trash row stays where §2.5
  put it.
- This is the piece of the plan that pays for itself on a device with **no users at all** —
  a solo shop still needs to find the boat — so it ships in Phase 1 and needs none of §2.1.

### 3.6 Backup health on the Start screen **[rev]**

The plan's real risk is not a worker deleting a file. It is a tablet in the bilge with nothing
backed up. Today a mirror failure surfaces only as supporting text inside Settings, and there
is no record of when the mirror last succeeded.

- `BackupMirror` records **`lastSuccessAtEpochMs`** and **`lastFailureAtEpochMs`** (+ the
  existing detail string) in DataStore, and the Settings row shows "Last copied: 3 days ago".
- The **Start screen** shows a manager-facing warning card once the mirror has **failed, or
  not run, for `MIRROR_STALE_DAYS`** (default 3): *"Backup copies have not gone through since
  Tuesday — folder access was withdrawn. Open Settings → Data."* Shown to managers; workers see
  a one-line notice so they can tell the manager. Dismissable per incident, returns on the
  next stale day. A device with no mirror folder configured shows nothing (the mirror is
  opt-in and stays so).
- `mirror.failed` / `mirror.recovered` audit events fire on the **transition**, not on every
  save, so the log records an outage as two lines.
- The save path is untouched: a failed mirror still never interrupts a save
  (`BackupMirrorSection.kt`'s rule stands).

---

## 4. Security

**[rev] Proportion.** The first draft carried PBKDF2 + an encrypted DataStore + a hash-chained
log + biometrics. That is a bank's posture for a shop tablet, and each piece has a cost: a
dependency, a recovery story, a screen. The threat model for internal shop paper is a worker
who should not be able to *casually* bypass the manager PIN, and a tablet that might be lost.
What survives that test is below; what does not is deferred to Phase 3 and only built if a
customer, insurer, or surveyor asks for it.

### 4.1 Credentials

- PINs (4–8 digits) hashed with **PBKDF2-HMAC-SHA256**, per-user random salt (platform
  `javax.crypto`; no new dependency). Verification is constant-time. The iteration count is
  a modest fixed constant — a 4–8 digit space cannot survive an offline brute force at any
  count, so the iterations are hygiene, not the defence; the defence is that the file is in
  the app sandbox and the rate limit is on the front door.
- Rate limiting: 5 failures → 30 s lockout, doubling; logged.
- **[rev] No `androidx.security:security-crypto` wrapper.** Google has deprecated that
  library, and encrypting a hash list that a rooted-device attacker could brute-force in
  seconds anyway buys nothing against the only attacker who could reach it. The plain `users`
  DataStore in the sandbox, excluded from cloud backup (§4.3), is the store.
- **[rev] Biometric deferred** to Phase 3, only if a manager finds the PIN pad slow.
- **Manager PIN recovery**: a second manager resets it. A device with exactly one manager gets
  a one-time **recovery code** shown at setup ("write it in the shop's book"). Forgetting both
  = clear app data = lose the audit log; the Shop config export (§5.3) is the mitigation and
  setup says so. **[rev]** Because a frustrated user's first instinct is "clear app data", the
  recovery-code step is **not skippable** at setup and the code is shown again on the Users
  screen to a signed-in manager.

### 4.2 Sessions

- Idle timeout (policy, **[rev] default one hour**) signs out to **Who's working?** after
  saving the draft. Returning from background past the timeout does the same. An hour is long
  enough that a machinist checking a dimension between cuts never signs in twice, and short
  enough that the tablet left on the bench overnight is not still "Chris" in the morning.
- Nothing about the device lock screen changes — the plan assumes the shop tablet has one and
  says so in Help.
- **[rev] Shop tablets are often offline and their clocks drift.** Every stamp and audit
  record carries the device's wall-clock time and nothing corrects it; Help says so, and the
  Activity screen shows a one-time notice if a record's timestamp precedes the previous one
  (a clock that went backwards). No NTP, no monotonic-clock arithmetic — an honest caveat
  beats a false precision.

### 4.3 Data leaving the device

- **Android cloud backup**: `allowBackup=true` currently lets `shafts/` ride the signed-in
  Google account's backup. Under this plan the audit log and `users` store are **excluded**
  from backup rules regardless; whether `shafts/` stays included is §7 Q9. **[rev]** Note the
  Google account on a shared shop tablet is the *shop's*, not a person's — which is what makes
  keeping the free off-site copy reasonable.
- "Share diagnostic logs" (AppLog) stays developer-facing, content-free, and **open to
  workers** (§2.3); it never attaches the audit log.
- Exports and prints are audited but not restricted — printing is the product. **[rev]** The
  `doc.export.<kind>` event is also the shop's answer to "did we send the surveyor that
  report" — worth naming, because that is the one time a shop wants the log for something
  other than a mistake.

### 4.4 Build and distribution (deployment hygiene, independent of the above)

- A **release signing key** before any shop rollout. Today every distributed build is
  debug-signed with the committed `debug.keystore`; a debug-signed app on a shop device is
  trivially replaceable by anyone with the same public key. Keystore in a CI secret, never in
  the repo. **[rev] This is the one security item in the plan that is real today, and it is
  Phase 0** — it needs nothing else here and should land before any of it.
- Firebase App Distribution **tester group** ("shop") instead of the hardcoded email; or a Play
  **internal testing** track if the shop devices have Play. Either keeps the current
  update-on-every-push cadence.
- Crashlytics activation (already on `TODO.md`) matters more once a device is out of reach.

---

## 5. Data and compatibility

### 5.1 Envelope
Additive fields only (§2.4); `version` stays 1. Old builds ignore the fields on decode
(`ignoreUnknownKeys` is already on) — a file saved by a new build still opens on an old one,
minus the stamps.

### 5.2 Storage layout
```
filesDir/
  shafts/            (unchanged)
  shafts/.trash/     (new)
  shafts/.index.json (new — search index, rebuildable, never authoritative)   [rev]
  audit/YYYY-MM.jsonl (new)
  logs/              (AppLog, unchanged)
datastore/
  users              (new — users + policy; plain DataStore, excluded from cloud backup)
  device             (new — deviceId / deviceName)                            [rev]
  settings           (unchanged — policy lives in users, not here)
```

**[rev] Device replacement carries the history.** The backup zip gains `audit/` and
`shafts/.trash/` alongside the library; **restoring** them is manager-only (`Guard`) and
merges — audit months append (dedup by `(ts, deviceId, event, docId)`), trash items land as
trash. A replacement tablet restored from a zip therefore starts with the shop's history, not
an empty one. `users` / `device` are **not** in the zip — that is what §5.3 is for, and a
roster should not ride along with every library backup a worker might make.

### 5.3 Shop config export / import
One JSON: users (names, roles, PIN hashes + salts, active), lock policy. Manager-only, via SAF.
Import **merges by user id** (never drops a local user; deactivates only if the file says so).
This is how a second and third tablet get the same roster without a server, and how a lost
device's roster is recovered. **[rev]** The device name is deliberately *not* in it — each
tablet names itself.

### 5.4 Migration
None required. A device with no users behaves as today; creating the first manager turns the
system on. Existing files stamp themselves on their next save. `AppLog` keeps every breadcrumb
it has; `AuditLog` starts empty. **[rev]** The footer stamp on a device with no users prints
`Rev n · printed <date>` and nothing else — the same line the date occupies today, so a solo
user's sheets gain a revision number and lose nothing.

---

## 6. Phasing

Each phase ships alone and is useful alone.

**Phase 0 — Release signing + tester group (§4.4).** **[rev]** Independent of everything
below and the only genuine security gap today. First.

**Phase 1 — Provenance, safety net, and the foreman's questions (no login yet).**
`docId` + stamps + `revision` + `history` in the envelope; the **footer print stamp** (§2.4b);
**trash** replaces hard delete (mirror deletes only on purge); **search over footer fields**
+ saved-by rows (§3.5); **mirror health** on the Start screen (§3.6); `AuditLog` with a
device-level **Operator** picker (a plain name list, no PIN, no roles — attribution only) and
a device name, so the log, the stamps and the sheet already say who and where. Settings →
Activity (read + export + summary line). **[rev]** Three of these need no users at all (stamp,
search, mirror health) and are the parts a solo shop gets value from on day one. This phase
alone answers "who last touched this, which paper is current, where is the boat's drawing,
and can I get it back".

**Phase 2 — Users, roles, lock, release.**
Manager PIN, Who's-working screen, `LockPolicy` + `Guard` + `LockedActionDialog`, elevation,
**Released** flag + Save-as-new-revision (§2.4a), stale-save guard (§2.4c), measured-by
stamps (§2.4d), "Saving as …" (§3.1), manager Settings section, rate limiting, recovery code,
backup-zip audit/trash carry (§5.2).

**Phase 3 — Shop hardening, on request.**
Shop-config export/import, backup-rule exclusions, template gate (when templates land),
biometric, tamper-evident chain — the last two only if a customer, insurer or surveyor asks.

Estimated size: Phase 0 is a CI change. Phase 1 is one focused branch (envelope + storage +
footer line + Open-screen search + one screen + tests) — larger than the first draft's Phase 1
because it absorbed the oversight features, but each is small and none touches geometry.
Phase 2 is the large one (auth, ~9 gated call sites, three screens). Phase 3 is a set of small
branches.

---

## 7. Questions for Chris (answer inline)

**[rev]** Each question now carries the reviewer's **recommended** answer from the foreman's
seat; an empty `A:` means the recommendation stands until overridden.

**Q1 — Devices.** How many tablets, and is a drawing expected to be opened on more than one?
(Affects whether §5.3 shop config is enough, or whether a shared folder for `.shaft` files —
the existing auto-mirror — becomes the de-facto library and needs its own lock story.)

> Recommended: plan for 2–3 tablets and assume a drawing WILL get opened on more than one via
> the mirror folder sooner or later — §2.4c's stale-save guard is cheap insurance either way.
>
> A:

**Q2 — What a worker must not do.** Delete only? Or also: overwrite a drawing someone else
last saved, rename, discard drafts, restore backups, change the drawing look?

> Recommended: gate only the irreversible set — empty trash / delete forever, restore over the
> library, roster/policy/device changes, Dev Options — plus editing a RELEASED drawing.
> Delete (→ trash), rename, drafts, saving over a colleague's drawing, and the drawing look
> stay open (§2.3). Trash is the safety net; a PIN on every cleanup is what makes shops stop
> using the feature.
>
> A:

**Q3 — Worker identity strength.** Is "tap your name" enough for attribution, or do workers
need PINs so the log is trustworthy? (Tap-only is faster on the bench; PINs make the audit
trail proof rather than a claim.)

> Recommended: tap-only, with "Saving as …" (§3.1) and the idle timeout as the honesty
> mechanisms. Worker PINs are a policy switch for a shop that wants them; default off.
>
> A:

**Q4 — Managers.** How many, and is biometric wanted or is a PIN fine?

> Recommended: two (owner + foreman) so elevation works when one is out; PIN only.
>
> A:

**Q5 — Idle timeout.** Should a tablet left on the bench sign itself out, and after how long?
Or is the device lock screen enough?

> Recommended: device lock screen for security, plus a one-hour idle sign-out for attribution
> (§4.2) — long enough not to be felt between cuts, short enough that the morning's first user
> is not yesterday's.
>
> A:

**Q6 — Trash retention.** 30 days default acceptable? Who may empty it?

> Recommended: 30 days, empty/purge manager-only, the mirror keeps its copy until purge (§2.5).
>
> A:

**Q7 — Audit retention and use.** Keep forever, or roll after N months? Who reads it — you,
on the tablet, or exported to a PC? Does it ever need to be **printed**?

> Recommended: keep forever (a month of shop activity is kilobytes); read on the tablet via
> the Activity filters, exported to CSV for anything longer; never printed.
>
> A:

**Q8 — Tamper evidence.** Is a hash-chained log worth having (protects against someone
editing the file with a USB cable), or is "workers can't reach the file" enough?

> Recommended: not now. The sandbox is the boundary for internal shop paper; build the chain
> the day a customer or insurer asks for it (Phase 3).
>
> A:

**Q9 — Cloud backup.** Today Android may back `shafts/` up to the signed-in Google account.
Keep that (free off-site copy), or exclude it (drawings never leave the building, DESIGN_INTENT
§1.1)?

> Recommended: keep it. The signed-in account on a shared shop tablet is the shop's own, the
> mirror folder is the primary backup and the cloud copy is the free second one; `users` and
> `audit/` are excluded regardless.
>
> A:

**Q10 — Distribution.** Firebase tester group, or Play internal track? Do the shop tablets
have Google accounts / Play?

> Recommended: Firebase tester group if the tablets are the shop's own with a shop Google
> account; Play internal track only if the shop wants updates without a developer in the loop.
> Release-signed either way (Phase 0).
>
> A:

**Q11 — Phase 1 first?** Ship provenance + trash + operator picker before any login, so the
shop gets the safety net now and the roles later?

> Recommended: yes — and Phase 1 now also carries the footer stamp, footer-field search, and
> mirror health, the three things a solo shop benefits from before any user exists.
>
> A:

**Q12 — Measured-by on the sheet.** **[rev]** Should the wear/runout sheets print who took the
readings and when (the QC-record use, §2.4d), or is it enough that the app shows it?

> Recommended: app-only for now. The footer stamp (§2.4b) already says who printed and when;
> a second name on the sheet is a line the long shafts cannot spare until a surveyor asks.
>
> A:
