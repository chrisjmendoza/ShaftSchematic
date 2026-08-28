# Templates — contract

Reusable starting points for a new drawing: a shaft's **shape**, saved apart from the job it
came from. Design plan and open questions: `docs/archive/Templates_And_DiaVisibility_PLAN.md`.

## Files

| Layer | File |
|---|---|
| Store | `io/TemplateStorage.kt` — `<filesDir>/templates/` |
| Bucketing (pure) | `template/TemplateBuckets.kt` |
| Browser | `ui/screen/TemplatesRoute.kt` (nav route `templates`) |
| Preview | `ui/drawing/compose/ShaftThumbnail.kt` |
| Save entry | `ui/nav/InternalDocRoutes.kt` → Save Drawing → "Save as template…" |
| Load / save VM | `ShaftViewModel.applyTemplate` / `exportTemplateJson` |
| Starters | `assets/templates/*.shaft`, seeded once via `SettingsStore.starterTemplatesSeeded` |

## Invariants

**A template is an ordinary `.shaft` document.** No new file format, no codec change, no
envelope field. Any path that reads a shaft document can read a template.

**A template carries geometry only, scrubbed at WRITE time.** `exportTemplateJson` encodes
`spec` + `preferredUnit` + `unitLocked` and nothing else job-shaped (the codec-owned
`station_interval_version` stamp rides every encode — see `RunoutSheet.md` §Measurement
stations). Job number, customer, vessel, shaft position, notes, `RunoutConfig`, and the
wear / runout / undercut records are dropped when the file is written, not merely ignored
when it is read — a template that still held a customer name would carry it into every
drawing built from it, and into any copy of that file. `applyTemplate` clears the same
fields again on load as a second line of defence for any file authored before this rule or
hand-copied into the folder.

**Buckets are derived, never stored.** `templateSizeBucket` / `templateLinerCount` read the
spec at scan time. There is no index file, so nothing can fall out of sync and an edited
template re-files itself. Adding a stored bucket key would reintroduce exactly that class of
bug.

**Loading a template starts a new, unnamed, dirty session.**
- *Unnamed* (`currentDocumentName = null`) so the first Save prompts for a name — a template
  can never be overwritten by the drawing made from it.
- *Dirty* — `applyTemplate` deliberately does **not** call `markDocumentSaved()` the way
  `importJson` does. Marking it saved would leave a loaded template counting as "no unsaved
  work", so quitting would lose it: the draft ring only protects a session it can see as
  dirty, and a template-loaded session is not blank (`isDefaultSession()` is false).
- Choosing a template is session-replacing, so `AppNav` routes it through the same
  unsaved-changes guard as New / Open / Open-recent.

**Component ids are kept, not re-minted.** Ids never cross document boundaries — wear, runout
and undercut records key within a single document — so two drawings from one template sharing
ids is harmless.

## Bucketing

- **Size**: the **largest** liner OD rounded to the nearest whole inch, clamped to 4"–12".
  Outside that range → `TemplateSizeBucket.Other` (never dropped). No liners →
  `TemplateSizeBucket.None`, listed first, because a straight shaft template still needs a home.
- **Count**: 1 / 2 / 3, then `THREE_PLUS`. Liners with `odMm <= 0` are unfinished rows and do
  not count.
- Canonical units stay mm; the inch numbers are bucket **identity**. Headings are formatted in
  the user's active unit at the display layer, per the unit-edge rule.
- Empty buckets are hidden by the browser — a list of 27 empty rows is worse than a short one.

**Size keys off liner OD — DECIDED** (plan §7 Q1, closed on-device 2026-08-27): the liner
size is "usually the numbers we're measuring from initially for sizing", so the bucket keys
off the largest liner OD, exactly as implemented. Do not re-key to the shaft Ø under the
liner without a new ruling.

**Per-component labels travel — DECIDED** (on-device discretion, 2026-08-27): `Liner.label`,
`Body.label`, etc. ride into a template unscrubbed. They describe how the shaft is AUTHORED
(the `Persistence.md` posture for per-component overrides), the seed name and descriptor make
them visible at save time, and scrubbing typed text would sit badly with the golden rule. A
job-specific label in a template is the author's to rename, not the codec's to erase.

## Descriptor and name seed (`template/TemplateDescriptor.kt`, pure)

Two templates in the same bucket often differ only in liner POSITIONS, which a name can't be
trusted to record — so the browser derives it. Every card's caption is `templateDescriptor`:
OAL, the CANONICAL max Ø (`ShaftSpec.maxOuterDiaMm()` — tapers and threads included), the
liner count, and a **zone string** — each liner's center mapped into aft/mid/fwd thirds of the
span, AFT→FWD (`A·M·F` spread, `A·A·F` two-aft-one-fwd; span = OAL, falling back to
`coverageEndMm()`; boundaries belong to the forward side). Formatting follows the shop
convention (`formatLenWithUnit`/`formatDiaWithUnit`), in the user's ACTIVE unit (unit-edge
rule); a linerless shaft says "No liners" exactly once (`templateBucketPath` fixes the old
"No liners · No liners" doubling in the save dialog too).

`suggestedTemplateName` (moved here from the nav layer, tested) seeds the save dialog with the
same facts in filename-safe form — `6in 3 liners A-M-F` — and the call site dedupes against the
existing store (`dedupeTemplateName`, case-insensitive " (2)", " (3)"…). A seed is a seed:
the user's typed name is never rewritten.

## Browser expansion

Size and count sections open **independently** (`Set`-keyed state, count keys composed with
their parent size label) — comparing two candidates is the whole job of this screen, and an
exclusive accordion made cross-bucket comparison impossible. Nothing is expanded on arrival;
state is `rememberSaveable` (string keys), so rotation keeps what was open. Headers swap
ExpandLess/ExpandMore and carry testTags (`template_bucket_*` / `template_count_*`).

Template rename runs the typed name through the same `DocumentNaming.sanitizePart` documents
get (one implementation — the Open screen's private copy was deleted), pre-selects the whole
name, and short-circuits a same-name rename instead of reporting a collision against itself.

## Search, sort, dates, refresh

The browser carries the Open screen's controls (`template/TemplateSearch.kt`, pure): a search
field and Name | Date sort chips (re-tap toggles direction; Date ↓ is the default — the
store's own order). A non-blank query REPLACES the accordion with a flat filtered list —
buckets would hide matches sitting in collapsed sections — matching case-insensitively over
the display name AND the derived descriptor, with the zone separators folded (`A-A-F` finds
`A·A·F`; the filename and display spellings are the same fact). A blank query restores the
accordion with its expansion state untouched. Sort applies to the flat results and within
each open count group (grouping is structure, not ordering). Each card shows a relative date
(`util/RelativeDate.kt` — promoted from the Open screen, ONE implementation). The route
rescans on RESUME (skipping the first, which the initial scan covers), so a template saved
from the editor appears when the browser comes back; a starter-seeding failure surfaces as a
snackbar + AppLog event (count only — the privacy rule).

## Envelope

The template envelope has ONE definition — `doc/TemplateEnvelope.kt`
(`templateDocFor`/`encodeTemplateJson`, pure): `ShaftViewModel.exportTemplateJson` delegates
to it and `TemplateScrubTest` calls it directly. The test used to hand-copy the envelope,
which let the real writer and the scrub test drift (the copy had silently omitted
`unitOverrides`). Add a field to the envelope and the scrub test sees exactly what the app
writes.

## Previews

`ShaftThumbnail` makes the same two calls as the editor's `ShaftDrawing`
(`ShaftLayout.compute` → `ShaftRenderer.draw`) with everything interactive removed: no pan,
zoom, double-tap reset, tap-to-select, grid, highlight, or debug overlays. There is no second
renderer and no duplicated drawing math. Each card resolves its own spec with the pure
`resolveComponents`, and the `LazyColumn` composes only visible cards, so a collapsed bucket
costs nothing.

## Starter templates

Three bundled files (4"/1 liner, 6"/2 liners, 8"/3 liners) seed **once** on first run so the
browser is not empty. Deliberately simpler than the sample-shaft seeder: a boolean flag, no
version, no hash ledger — templates are user-owned the moment they appear, so there is no
"update the bundled copy" story. Never overwrites; a name collision is skipped. Content is
decoded before it is written, so a malformed asset cannot plant a file the browser chokes on.
A user who deletes every starter does not get them back. The one-shot flag sets only when a
run had no failures or seeded something — a run where EVERY asset failed leaves it unset so a
fixed build can retry (there is no manual restore path). The Templates browser awaits the
(idempotent) seeder before its first scan, so a first launch cannot race it into showing an
empty store.

**Their geometry is placeholder** — plausible proportions derived from the bundled sample
shafts, pending which layouts are actually reached for (plan §7 Q14). Each taper's
`taperRateText` matches its drawn geometry exactly (SET/LET/length derive from the labeled
1:12 / 1:16 rate), pinned by `StarterTemplateAssetsTest` — a starter must never print a rate
its own diameters contradict, or trip the rate-mismatch warning out of the box.

## Non-goals (v1)

No export/import or sharing of template files, no folders or tags, no reordering. Management is
Rename and Delete from the card's overflow menu.

## Store safety

- **Save never silently overwrites.** "Save as template…" checks the store (case-insensitive,
  so one browser row cannot fork into case-variant files) and confirms before replacing —
  the same protection the document save screen has.
- **Rename reports what actually happened.** `TemplateStorage.rename` returns a
  `RenameResult` (`OK` / `SOURCE_MISSING` / `TARGET_EXISTS` / `IO_ERROR`) and the browser
  maps each to its own message — a vanished source is not reported as a name collision.
- **Names are single path segments.** `normalizeShaftDocName` (shared with documents)
  collapses `/` and `\`, so a typed name can never resolve outside the store's directory
  and overwrite an unrelated file.
- **Use decodes before applying.** A template that loads but no longer decodes (replaced or
  truncated on disk after the scan) gets a snackbar, not a crash — `applyTemplate` rethrows
  on a bad document by design.
