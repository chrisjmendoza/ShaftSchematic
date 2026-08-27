# ShaftSchematic TODO

**Version: v0.5.x Development Queue**
**Last updated: 2026-08-27**

## What belongs here

**Only what is still open**: work not started, decisions not made, and constraints on future
work. Nothing else.

When an item ships, **delete it** — do not convert it into a record of how it went. `CHANGELOG.md`
owns what happened and when, the `docs/` contracts own how it behaves, and git history owns the
rest. A completed entry left here with its build narrative attached is a changelog entry in the
wrong file, and enough of them buries the handful of lines that actually say what to do next.

Two things are worth carrying out of a finished item before deleting it: any **follow-up that is
genuinely still open** (promote it to its own `[ ]` line, don't leave it buried in a `[x]`), and
any **"do not do X" ruling** — and that second one belongs in CLAUDE.md or a contract doc, with
at most a pointer from here.

---

## 0. System state

| Area | Status |
|---|---|
| Core model (Body, Taper, Threads, Liner) | ✅ Stable |
| ShaftLayout & ShaftRenderer | ✅ Contract-locked |
| PDF export — one-page, landscape | ✅ Stable |
| Validation — blocking errors | ✅ Add dialogs, carousel badges, export gate |
| Validation — non-blocking warnings | ✅ Carousel badges, spec banner, 3-state Free-to-End badge |
| Snapping engine | ❌ Removed with tap-to-add — nothing snaps a position (golden rule) |
| Tap-to-add pipeline | ❌ Removed — canvas tap is selection-only; add via the FAB chooser |
| OAL window / excluded threads | ✅ Implemented & unit-tested |
| Taper rate input, derivation, colon entry | ✅ Auto-by-default with manual override |
| Keyways — taper, body, spooned, 180°/90° clocking | ✅ Shipped |
| Liner shoulders | ✅ Shipped, capability-gated (default OFF) — schematic + runout/consolidated sheets, envelope-aware |
| Diagnostics — breadcrumb log + Share logs, guarded Crashlytics | ✅ Shipped — activation steps open, see §2 |
| Body blends + seal areas | ✅ Shipped on schematic and runout/consolidated sheets |
| Undercut drawing (tab + PDF) | ✅ Shipped |
| Wear document | ✅ Shipped — strips, pits, dia readings, blank template |
| Runout drawing + bubble editor | ✅ Shipped — draggable stations, coupling face |
| Consolidated Output tab | ✅ Shipped — variant election, worn sections, Export all |
| Profile sizing (PDF) | ✅ Shipped — 1/2"–1 1/2" paper band, sizing curve, liner compression, S-break threshold |
| Mixed units + dual display | ✅ Shipped, off by default — see §2 for open follow-ups |
| Fraction typography | ✅ Shipped — Stacked / Diagonal (default) / Plain |
| Drawing preset profiles | ✅ Shipped, app-wide |
| Appearance settings (theme + high contrast) | ✅ Shipped — see §2 for the open on-device pass |
| Sidebar nav (5 tabs) | ✅ Schematic / Runout / Wear / Undercut / Consolidated Output |
| Undo/redo | ✅ Session-scoped `SessionHistory` over `EditState` |
| Internal save/open, autosave draft ring | ✅ Shipped |
| Backup & restore, auto-mirror folder | ✅ Shipped |
| Bore keyway calculator | ✅ Shipped — sidebar tool, nothing persisted |
| Help + Achievements screens | ✅ Shipped |
| Export hardening | ✅ Every SAF write through `util/PdfSafExport`; one collision gate |
| Insert-Between workflow | 🔲 Not implemented |
| Fiberglass body support | 🔲 Not implemented — blocked, see §1 |

---

## 1. Open questions — decide before building

- **Fiberglass body styling.** Selection is straightforward (a per-`Body` flag under the usual
  add-dialog/carousel parity). **Styling is undecided** — get a sketch or a photographed sheet
  first, the same way the "indicated wear" convention is blocked on one. Reference:
  `assets/20251022_172641.jpg`. Note the existing interaction: a fiberglassed run is exactly the
  case that motivated per-body `showDiaOnDrawing`, since a Ø can't be measured through the wrap.
- **"Indicated wear" rendering style** (requested 2026-07-18) — the shop hand-sketch convention:
  squiggly lines along the liner edges in the worn region, straight lines for wear on the liner
  face. Specific ideas exist; **get a sketch/photo before building**. Detail strips + overlay;
  main-profile bands probably stay hatched at that scale.
- **Multi-shaft per job number** (requested 2026-07-26) — two shafts sometimes share a job
  number. Plan in `docs/MultiShaftJob_Plan_2026-07-26.md` (recommends derived job grouping over
  single-shaft files; no file-format change). **Awaiting answers to its 6 product questions.**
- **Coupler-slot hit-test.** The preview hit-test covers Body/Taper/Thread/Liner but not
  `ResolvedCouplerBoltSlot`, so tapping a slot selects the body underneath and the slot's card is
  unreachable by tap. Plausibly deliberate — a slot always overlies something, and letting it win
  would make that body untappable at the slot. Decide before changing.
- **Runout sheet: tap-to-place bubble** (requested 2026-07-26). The leader half is superseded by
  the auto leader; long-press-drag already pins a station, which covers most of the need. Confirm
  on-device whether anything remains wanted here.
- **Warning thresholds** picked during the 2026-07-24 loop (1.5× adjacent-body step, 0.5 mm
  adjacency eps in `ui/util/ComponentWarnings.kt`) — chosen without shop input; sanity-check
  against real drawings.

---

## 2. Open work

### Verification

- [ ] **Crashlytics activation** (one-time, console + repo settings): add Crashlytics to the
  existing Firebase app (the one behind `FIREBASE_APP_ID`), download `google-services.json`,
  put its contents in a new `GOOGLE_SERVICES_JSON` GitHub Actions secret (and optionally drop
  the file at `app/google-services.json` locally — it is gitignored), then force one test crash
  from a distributed build to confirm the pipe. Until then everything builds green with crash
  reporting simply inactive.
- [ ] On-device: tap Settings → Data → "Share diagnostic logs" once — the FileProvider
  attachment path is pinned by test only indirectly (Robolectric cannot resolve provider roots).
- [ ] On-device pass on the recent tail — several features are shipped but unverified on
  hardware; `CHANGELOG.md` is the running record of which.
- [ ] On-device visual pass of dark and high-contrast chrome (the Appearance schemes have only
  been reasoned about, not looked at). See `docs/contracts/Appearance.md`.

### Rendering / components

- [ ] **Mixed-unit follow-ups**: carousel numeric *entry* fields still take the document unit
  (the chip governs how a component PRINTS, not how its fields are typed), so a metric keyway is
  typed in inches and stored mm; and standard metric key-stock presets for keyways aren't built.
- [ ] **Additional output fonts** (requested 2026-08-14) — let a shop pick a look rather than
  take the platform default. Constraints: the PDF composers draw with `android.graphics.Paint`,
  so a face must be a real `Typeface` (bundled `.ttf` or a system family); every text metric is
  measured live from the paint, so a swap is safe by construction *provided* nothing hard-codes a
  width. Check the fraction stack against a condensed or slab face before shipping —
  `FractionTextRendererTest` exists to catch exactly that. Same pref posture as
  `PdfPrefs.fractionStyle`.
- [ ] Compact wear-strip option — strips stretch the liner toward full content width for
  readability; a denser mode (natural/shared scale) would ease crowded 3-strip pages.
  Full-stretch reads well, so it stays the default.

### Tech debt

- [ ] Controller owns all VM-side intents (composables stateless) — design work, not a pure move.
- [ ] Re-evaluate splitting `ShaftPdfComposer.kt`. It was left whole because its complexity lives
  in the long `composeShaftPdf` entry function rather than in file length, and because Wave-3
  items 3–4 were expected to reshape what is composer-local vs shared. Those landed
  (`pdf/BodyRunDraw.kt`, `pdf/SimpleShaftProfile.kt`), so the question is open again on the
  current shape.

### Build tooling

- [ ] Keep Gradle wrapper, AGP, and `libs.versions.toml` in sync.
- [ ] Isolate tooling updates into `chore(build)` commits.
- [ ] **Deferred, with reasons — do NOT bump blind:**
  - **compileSdk-37 chain**: core/core-ktx 1.19.0, lifecycle 2.11.0, and Compose BOM 2026.08.00
    all require compileSdk 37, but stable Robolectric (4.16.x) certifies only through API 36 —
    and the whole Compose test suite runs on Robolectric. Waits for Robolectric 4.17 stable,
    then moves as ONE coordinated bump.
  - **Compose BOM 2024.09.00 → 2026.04.01** (the last compileSdk-36-safe BOM): real Compose API
    surface over ~19 months — its own branch with a compile + visual pass, not a chore.
  - **Kotlin 2.4.0** (K1 drop, annotation-target and warning-promotion changes) — its own branch.

### Testing

Unit and instrumentation burndowns are complete (Robolectric JVM Compose harness; assert against
`testTag`s, never composable parameter lists). CI gates on the suite — a red `testDebugUnitTest`
blocks the build and nothing distributes.

**Deliberately not covered** (a decision, not a gap): no Compose test for
`ComponentCarouselPager` (~35 params, all callbacks — that coupling is what rotted the deleted
androidTest) or the Add dialogs; their logic is pure and covered.

---

## 3. Backlog (v0.5.x+)

- [ ] Title-strip follow-ups (liked, not yet requested): tappable title → Save As / rename;
  smarter untitled-draft row names on StartScreen (via `DocumentNaming.suggestedBaseName`);
  title strip on the Runout/Wear tabs too.
- [ ] Selection → contextual "Add near selected" defaults.
- [ ] Inline "Add here" buttons between components in the list.
- [ ] Preset library (common tapers, common shoulder patterns).
- [ ] Quick inline mm ↔ in calculator in dialogs.
- [ ] Undo/redo follow-ups (not blocking v1.0): cross-session/persisted history (currently
  in-memory, cleared on process death and at every new/open/import boundary), and metadata
  (customer/vessel/job/notes/shaft position/unit) is deliberately excluded from undoable state —
  revisit if that becomes a complaint.

---

## 4. Explicit non-goals (do NOT implement)

- Multi-page PDF or foldouts
- DXF export
- BOM / machining tables
- Stress analysis or deflection math
- Non-linear scaling modes
- Cloud sync or AI features

---

## 5. Guardrails

- PDF pages must always paint a white background explicitly.
- PDF rendering must not depend on app theme or system dark mode.
- Tier origin, measurement reference, and units are independent concerns — changes to one must
  not affect the others.
- `ShaftRenderer` and `ShaftPdfComposer` are separate drawing paths — a fix in one does not
  propagate to the other automatically.
- `blockingExportError()` is the single gate for PDF export; do not add secondary gates elsewhere.
