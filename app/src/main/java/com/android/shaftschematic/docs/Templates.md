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

*(Open: whether "liner sizing 4–12" keys off liner OD (implemented) or the shaft Ø under the
liner — plan §7 Q1. In normal marine practice liner OD ≈ shaft Ø + ¼", so both readings round
to the same inch for most shafts; the two derivations diverge only on unusually thick liners.)*

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
