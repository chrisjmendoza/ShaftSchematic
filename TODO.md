# ShaftSchematic TODO

**Version: v0.5.x Development Queue**
**Last updated: 2026-10-27**

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
| Validation — non-blocking warnings | ✅ Carousel badges, spec banner |
| Snapping engine | ❌ Removed with tap-to-add — nothing snaps a position (golden rule) |
| Tap-to-add pipeline | ❌ Removed — canvas tap is selection-only; add via the "+ Add Component" button's chooser |
| OAL window / excluded threads | ✅ Implemented & unit-tested |
| Taper rate input, derivation, colon entry | ✅ Auto-by-default with manual override |
| Keyways — taper, body, spooned, captured, 180°/90° clocking | ✅ Shipped — one shared "Keyway" gate on cards + Add dialogs; the standard-size picker was removed 2026-10-23 (W × D are typed) |
| Threads — metric designations, relief groove, pitch-driven hatch (slant + density) | ✅ Shipped |
| Coupler bolt rows — seam slots + cross-drilled holes | ✅ Shipped |
| Liner shoulders | ✅ Shipped, capability-gated (default OFF) — schematic + runout/consolidated sheets, envelope-aware |
| Diagnostics — breadcrumb log + Share logs, guarded Crashlytics | ✅ Shipped — activation steps open, see §2 |
| Body blends + seal areas | ✅ Shipped on schematic and runout/consolidated sheets — face finish Square \| Blend, seal areas their own section |
| Final Schematic (`final_spec`) | ✅ Shipped — second geometry, Final tab, marked sheets |
| Undercut drawing (tab + PDF) | ✅ Shipped |
| Wear document | ✅ Shipped — strips, pits, dia readings, blank template |
| Runout drawing + bubble editor | ✅ Shipped — draggable stations, coupling face |
| Consolidated Output tab | ✅ Shipped — variant election, worn sections, Export all |
| Profile sizing (PDF) | ✅ Shipped — 1/2"–1 1/2" paper band, sizing curve, liner compression, S-break threshold |
| Mixed units + dual display | ✅ Shipped, off by default — one open composer bug, see §2 |
| PDF options | ✅ Non-modal panel (page stays zoomable), collapsible sections most-used first |
| Fraction typography | ✅ Shipped — Stacked / Diagonal (default) / Plain |
| Drawing preset profiles | ✅ Shipped, app-wide |
| Appearance settings (theme + high contrast) | ✅ Shipped — see §2 for the open on-device pass |
| Sidebar nav (6 tabs) | ✅ Schematic / Runout / Wear / Undercut / Final / Consolidated Output |
| Tablet layout | ✅ Shipped — window width class, permanent sidebar, free rotation on sw600dp; the Schematic editor at EXPANDED is a drawing band over a peeking carousel with two-up fields (verified on the Pixel_Tablet emulator 2026-10-23); the sheet tabs still use two panes; hardware pass open, see §2 |
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
- **Shop deployment — users, manager lock, audit trail, shop oversight** (requested 2026-09-02,
  revised 2026-09-03 from the foreman's seat). Nothing exists today: no identity, no timestamps,
  hard delete, filename-only search, silent mirror failures, debug-signed distribution. Plan in
  `docs/ShopDeployment_PLAN.md` — device-local users/roles, manager PIN gating only the
  IRREVERSIBLE set (purge, restore-over-library, roster/policy) plus a per-document **Released**
  flag behind ONE `Guard`, trash instead of hard delete (mirror deletes only on purge), envelope
  stamps (`doc_id`, revision, created/modified by, device), a **footer print stamp** (rev /
  printed by / device), Open-screen search over Customer/Vessel/Job/Item, mirror-health warning
  on Start, a durable `AuditLog` separate from `AppLog`, security cut to PIN hash + rate limit.
  Phase 0 = release signing key + tester group (§4.4); Phase 1 = provenance + trash + footer
  stamp + search + mirror health (no login). **Awaiting answers to its 12 product questions**
  (each carries a recommended answer).
- **Coupler-slot hit-test.** The preview hit-test covers Body/Taper/Thread/Liner but not
  `ResolvedCouplerBoltSlot`, so tapping a slot selects the body underneath and the slot's card is
  unreachable by tap. Plausibly deliberate — a slot always overlies something, and letting it win
  would make that body untappable at the slot. Decide before changing.
- **Runout sheet: tap-to-place bubble** (requested 2026-07-26). The leader half is superseded by
  the auto leader; long-press-drag already pins a station, which covers most of the need. Confirm
  on-device whether anything remains wanted here.
- **Provisional constants chosen without shop input** — sanity-check each against real drawings
  and shop practice:
  - Warning thresholds in `ui/util/ComponentWarnings.kt`: 1.5× adjacent-body step, 0.5 mm
    adjacency eps, and the implausible-value badge (length > 15 m, any Ø > 1 m).
  - The liner-shoulder edge-radius list `LINER_SHOULDER_STD_RADII_IN`.
  - The thread-relief seeds (length 1.5 × pitch, Ø = major − 1.3 × pitch).
- **Surfaced by the User Manual writers (2026-10-13)** — real but each is a product call, so none
  was changed in the manual's bug batch:
  - **Undo lives only on the Schematic and Final toolbars**, yet History covers runout, wear and
    undercut edits made on the other tabs. Add the History button to those toolbars, or accept
    "go back to the Schematic tab to undo" (the manual says that today).
  - **Tri-state card toggles cannot return to "follow the setting"** — once "Shade on drawing" /
    "Show name on drawing" is flipped on a card the component keeps its own value for good. A
    third "Default" state on the control, or a long-press reset.
  - **No Imperial/Metric switch on the thread card** — changing a thread between TPI and a metric
    designation means delete + re-add.
  - **Blend length, seal-area length, liner-shoulder length and reduced Ø never print** — only
    the shoulder radius reaches the footer (deliberate per CLAUDE.md, but a machinist cannot make
    these from the paper alone). Decide whether a footer line per feature is wanted.
  - **Wear detail opens with the Add X tool selected**, so the first tap places a pit.
  - **Home leaves no way back into the open job** except Unsaved drafts / Recent.
  - **A bare metric designation ("M20") is accepted with zero pitch** and then warns "Zero pitch
    — thread renders flat"; reject it at entry, or fill the ISO coarse pitch.
  - **The Line Thickness % box saves on every keystroke** (clamped 50–200, so "1" on the way to
    "150" briefly applies 50%) — the per-keystroke pattern removed from the OAL field.
  - **Template mode has no on-preview indicator**; it persists, sits only in Settings, and is not
    captured by Drawing profiles or reset by "Restore Drawing defaults".
  - **Double-tap zoom reset exists only on the schematic preview**; the shared PDF preview overlay
    (Runout / Wear / Undercut / Consolidated) has neither it nor a Reset-zoom icon. (The Undercut
    tab's zoomed detail page has its own double-tap and "Reset view" — a canvas, not the overlay.)
  - **Project Notes never print** (the wear/undercut "Notes:" areas are blank write-in lines).
  - **Measurement reference wording differs** — Settings "AFT (force AFT SET)" vs the panels'
    "AFT"; the Keyway calculator opens a dialog titled "Keyway rough-cutter depth".
  - **mm documents print rail dimensions at 3 decimals but footer/callout values at 1.**
- **Liner vs taper compression on the consolidated sheet.** With "Liner & taper compression"
  turned down, tapers reach ~40% of true length while liners stop at ~69%: the liners' flat
  `PROFILE_MIN_LINER_PT` floor is λ-immune on that sheet (the schematic's lean floor lands both
  on ~53%). The slider's top ~70% of travel therefore moves only liners. Options: lower the taper
  baseline gate, or make the liner floor λ-aware (risk: body runs start to equalize). Decide
  whether the mismatch matters on paper before changing anything.
- **Undercut strip: a neighbouring section's cut inside this strip's window** (on-device pass
  2026-10-27). A strip's draw window (liner + pad + S-break room) can reach over a cut that belongs
  to the next section — a bare-shaft cut ½″ off a liner, or the AFT liner's window covering a bare
  cut just AFT of it — and today draws plain full-Ø stock there, though the shaft is cut.
  Options: (1) keep plain stock (the strip shows only its own section), or (2) draw the
  neighbour's notch unlabelled — no Ø, no rail span, no number. **Recommendation: (2), through the
  ONE notch builder (`buildUndercutNotches`)**, so the silhouette is true and the two draw sites
  cannot differ. A pinned test asserts today's plain stock; change it with the ruling.

---

## 2. Open work

### Verification

The per-feature on-device checks live in `docs/ON_DEVICE_CHECKLIST.md` (what to look at, what
"good" looks like, which merge it came from); the items below are the standing ones. A headless
AVD recipe (phone `calc_test` + `Pixel_Tablet`, adb-driven UI dumps and screenshots) drove the
2026-10-27 pass; it lives in the maintainer's agent notes, outside the repo — ask for it rather
than re-inventing one.

- [ ] **Crashlytics activation** (one-time, console + repo settings): add Crashlytics to the
  existing Firebase app (the one behind `FIREBASE_APP_ID`), download `google-services.json`,
  put its contents in a new `GOOGLE_SERVICES_JSON` GitHub Actions secret (and optionally drop
  the file at `app/google-services.json` locally — it is gitignored), then force one test crash
  from a distributed build to confirm the pipe. Until then everything builds green with crash
  reporting simply inactive.
- [ ] On-device pass on the recent tail — the 2026-10-27 AVD pass covered the functional half
  (its failures are fixed or listed under §Bugs); what is left is hardware and paper judgment
  (haptics, pinch-zoom, printed sheets) plus a re-check of that pass's fixes, all in the checklist.
- [ ] One TalkBack pass over the five sheet tabs to hear the canvas summaries and confirm the list
  rows are a workable editing path (the 200% font walk and the dark / high-contrast pass were done
  on the AVD 2026-10-27). Rulings in `docs/contracts/Appearance.md` §Accessibility.
- [ ] On-device: print one undercut sheet with "Line art (no shading)" on and judge whether the
  notch construction alone reads on paper (the ruling assumed it does, from the screen mode).
- [ ] **Tablet pass on hardware.** Every tab in portrait (MEDIUM) and landscape (EXPANDED), the
  permanent sidebar, rotation mid-edit, the page strip, preview closing and the pane split were
  walked on the Pixel_Tablet emulator 2026-10-27; its failures (rotation state, MEDIUM carousel
  height, the Final hamburger, the undercut card under the keyboard) are fixed and listed for a
  re-check in the checklist. Still open on a real tablet: those re-checks, and the Schematic card
  under the soft keyboard (§Bugs). Candidates from the editor pass: the same peeking carousel on a
  portrait tablet (MEDIUM, contract says plain column today), an "N of M" counter in the
  Components header, the top-band treatment for the sheet tabs.
- [ ] **Material3 1.4.0 pinch-zoom centroid** (Compose BOM 2026.04.01; the visual pass of the
  sliders, page-strip cap, chips, dialogs and Switch/Checkbox density was done on the AVD
  2026-10-27): decide the four pinch-zoom surfaces' `rememberTransformableState` centroid
  deprecation on hardware — taking the centroid changes where a pinch zooms from.

### Bugs

- [ ] **Dual-units consolidated sheet: rail labels can overlap.** On a very full sheet with dual
  units on, growing the footer by one line (e.g. a thread-relief line) prints overlapping rail
  values. Suspected cause: `RunoutPdfComposer`'s `railsBlockH` estimates the fallback-rail lifts
  off a prelim linear-map plan, and the final compressed map seats fewer values inline than that
  plan assumed. Pre-existing; the thread-relief test fixture is arranged around it — un-arrange it
  when fixing.

Found on the 2026-10-27 AVD pass and not fixed on its branch:

- [ ] **Inverted taper accepted silently; three rules disagree.** A taper typed with S.E.T. Ø >
  L.E.T. Ø (S.E.T. 8, L.E.T. 7.25, Measure From FWD) gets no warning, and the card/drawing title
  goes by diameter trend ("AFT Taper"), the footer column by midpoint ("FWD Taper"), and the
  footer values by magnitude (L.E.T. / S.E.T. printed swapped against what was typed). Needs a
  ruling: warn on an inverted taper, or key the title and `letSet` on position.
- [ ] **Schematic EXPANDED editor with the keyboard up**: the fixed drawing band keeps its height
  and squeezes the card to one row of fields, the focused field's label clipped (tablet
  landscape). The undercut detail page got the yielding fix; this one was left.
- [ ] **Card AFT | FWD chips stack letters at 200% font** — the Body / Taper / Liner / Coupler
  cards' inline `FilterChip` rows are not on the shared `ChipLabel` / `ChipFlowRow`, so "FWD"
  still breaks one letter per line there.
- [ ] **Undercut tab list delete: the Undo snackbar is short** (~4 s) — on the AVD it was gone
  before it could be reached and the tap fell through to "Drawing settings" (top-bar Undo still
  restored the cut).
- [ ] **Share diagnostic logs — two-file case unverified.** The share sheet attaches `log.0.txt`
  once; the ring had never rotated, so attaching both files (`log.1.txt` too) has not been seen.
  Push the log past 256 KB, or check on a device that has rotated it.

### Tech debt

- [ ] Controller owns all VM-side intents (composables stateless) — design work, not a pure move.

### Build tooling

- [ ] Keep Gradle wrapper, AGP, and `libs.versions.toml` in sync.
- [ ] Isolate tooling updates into `chore(build)` commits.
- [ ] **Deferred, with reasons — do NOT bump blind:**
  - **compileSdk-37 chain**: core/core-ktx 1.19.0, lifecycle 2.11.0, and Compose BOM 2026.08.00
    all require compileSdk 37, but stable Robolectric (4.16.x) certifies only through API 36 —
    and the whole Compose test suite runs on Robolectric. Waits for Robolectric 4.17 stable,
    then moves as ONE coordinated bump.
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

- [ ] Selection → contextual "Add near selected" defaults.
- [ ] Inline "Add here" buttons between components in the list.
- [ ] Preset library (common tapers, common shoulder patterns).
- [ ] Final Schematic: "Create a new job from the final" — deliberately left out of the first pass.
- [ ] Coupler bolt rows: authoring which side a blind 90° cross-drilled hole enters from (it is
  always drawn from the top silhouette).
- [ ] Undercut sheet past four sections: a second page, plus an offer to spread the drawings out.
  Shop jobs typically carry 3 sections, and 4 per page is a handy ceiling (on-device ruling, no
  build yet). Today both shapes cap at 4 (`UNDERCUT_PORTRAIT_MAX_PER_PAGE` = the landscape grid's
  `WEAR_STRIP_GRID_MAX_PER_PAGE`) and a 5th prints only as the "+N more" note; *Sections to print*
  is the workaround (print the sheet twice with different sections ticked). The idea: when more
  than 4 sections print, write them onto two pages, and offer to spread fewer per page for room
  (e.g. 3 + 2 rather than 4 + 1). Blocker: every writer is single-page —
  `writeShaftPdfToUri`, `renderPdfPageBitmap`, `printShaftPdfPage` (page count 1) and the preview
  overlay's one bitmap — and `docs/PDF_EXPORT.md` §6 states "single page only", so this is the
  app's first multi-page document. Page shape stays per page from the same `undercutSheetStrips`
  count. **Decide first:** it contradicts §4's "Multi-page PDF or foldouts" non-goal, so building
  it means lifting that non-goal for this one document.
- [ ] **In-app number keypad** (a day or night run). Keyboard apps disagree about number fields:
  Gboard's number pad has no `/` (the in-field "/" key, `ui/input/FractionKey.kt`, covers it),
  and SwiftKey / Samsung Keyboard show their full layout instead of a pad. A keypad of the app's
  own would behave the same on every device: digits, `.`, space, `-`, `/`, backspace, Done, plus a
  row of common fractions (`1/2 1/4 1/8 1/16 …`). Work: number fields suppress the system IME and
  take the keypad's input, it must work inside dialogs (Add dialogs, calculators), a way back to
  the system keyboard (unit suffixes like `mm`, hardware keyboards), TalkBack, and a Settings
  switch to turn it off. On-device ruling so far: do not force a phone-style keypad — leave the
  regular keyboard until this exists (`docs/contracts/NumberField.md` §"Keyboard").
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
