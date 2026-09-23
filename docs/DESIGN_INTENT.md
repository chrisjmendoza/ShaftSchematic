# ShaftSchematic — Design Intent

Status: **DRAFT — all ten questions answered.** Q1–Q2 and Q4–Q6 were answered 2026-08-29,
Q7–Q10 on 2026-09-01, Q3 on 2026-09-02; all are folded into the body sections below (§1.1,
§1.2, §3.1, §3.3–§3.8). §6 keeps the ledger. Started 2026-08-29 as the running
guide for product and UI decisions, so features stop being piece-mealed page by page and
start following one set of rules. Technical contracts stay where they are (`CLAUDE.md`,
`docs/contracts/`, `docs/PDF_EXPORT.md`); this document owns the *why* and the *defaults* —
when a new control, page, or output is added, it should be answerable from here which
surface it belongs on, what it defaults to, and what it may never do.

---

## 1. What the app is

ShaftSchematic designs **marine propulsion shafts** and produces **shop paper**: dimensioned
single-page PDF documents a machinist or surveyor works from. The model is canonical
millimeters; inches are a display edge. The paper is the product — every screen exists to
get a correct, readable sheet out of the shop printer.

The documents:

| Document | Tab | Job it does |
| --- | --- | --- |
| Shaft Schematic | Schematic | The build drawing: geometry, dimension rails, keyways, footer spec. |
| Runout Sheet | Runout | Field measurement form: bubbles at stations for TIR readings + high spots. |
| Wear Document | Wear | Condition record: liner strips, wear areas, pits, measured diameters. |
| Undercut Drawing | Undercut | Machined-below-surface record: cut spans, depths, section strips. |
| Consolidated Sheet | Output | One page combining schematic + runouts and/or wear for the job file. |

### 1.1 Audience (ruling 2026-08-29)

Every document is **internal shop paper**: read by the machinists at work and filed with
the workorder. No client, surveyor, or class society sees these sheets, and none leaves
the building. Consequences:

- Defaults may favor shop legibility over external formality. The display exaggerations
  (wear trace, undercut depth, blend width) are acceptable as defaults because the
  audience knows the conventions — the hard bound stays the existing license: no
  exaggerated span may carry a number a machinist could read as measured.
- No sheet needs a client-facing variant, disclaimer, or letterhead treatment, and no
  future control should be justified by an external audience.

### 1.2 Document hierarchy (ruling 2026-09-01, Q7)

All five outputs are required, but they are not peers. The **consolidated sheet and the
schematic are the primaries** — the pages the job actually runs on. **Runout and wear**
come next. The **undercut drawing is the least essential long-term** — it must keep
working, but when effort must be rationed (polish passes, new options plumbing, layout
work), it is rationed in that order. The wear tab's eventual retirement into the
consolidated flow (§5) is consistent with this hierarchy.

## 2. Standing doctrines (pointers, not restatements)

These are binding and live in `CLAUDE.md` — listed here only so this document reads
complete:

- **Golden rule**: typed values are sacred; no system rewrites them.
- **Reference features** never move geometry, OAL, or collision.
- **Draw-both-sites**: a mark that appears on canvas and PDF renders identically from one
  pure construction.
- **Unit edge**: conversion only at display/input; canonical mm everywhere else.
- **Exaggeration is licensed only where no number can mislead a machinist** (no rail, no
  callout on the exaggerated span).
- **Commit-on-release**: tuning drags are visual-only; persistence happens on release.

## 3. Options doctrine (established 2026-08-29)

### 3.1 The baseline set

**Every preview page that renders the shaft profile offers, on its PDF options sheet:**

1. **Shaft height** — the shared per-job multiplier (`RunoutConfig.heightScale`, one value
   behind every drawing). On the wear sheet it scales the main profile band only, clamped
   to the absolute paper band.
2. **Line thickness.**
3. **Body S-break threshold** — *where compression exists.* The wear and undercut documents
   draw at flat scale (no foreshortening; their only break is the fixed long-span trigger),
   so the slider is deliberately absent there — an inert control is worse than a missing
   one. **Ruling 2026-08-29 (Q4): flat scale is the standing property of those documents.**
   Compression there is not planned and should not be built speculatively; the question
   reopens only on an on-device report that a long shaft makes them unusable, and if either
   document ever does gain compression, its sheet gains the slider the same day.
4. **Shade in Components** (Bodies / Tapers / Liners, plus "Explicit bodies only").

The undercut sheet is the one partial exception: its normal form draws **no whole-shaft
profile at all** (detail strips own the page; the profile is only the empty-record
fallback), so it carries neither the height slider nor the S-break slider.

### 3.2 Placement and order

- **Order of presumed use, top to bottom**: content election first (compact chips), then the
  page-reshaping sliders (Shaft height, S-break, Line thickness, Liner & taper compression), then
  page-specific controls, then typography (arrows, fractions), then the rarely-touched
  groups as **collapsed expandables** (Measurement reference, Shade in Components), and
  **dual units always last** (standing rule: dual/rarely-used options trail).
- **Content toggles are compact**: chips, not captioned switch rows. Explanations live in
  Help, not in every sheet.
- **Page-specific controls live on that page's sheet only** (wear strip block on the wear
  sheet; bubble sliders on the runout/consolidated sheets; dimension arrows only where
  rails print).
- A control that would be **inert** on a page does not appear there, ever.

### 3.3 Where a value lives

- **App-wide look** → `PdfPrefs` (captured by Drawing profiles). Line weight, S-break
  threshold, shading, fraction style, arrows, bubble size/drop.
- **Per-job fit** → the document envelope (`RunoutConfig`, records). Shaft height, liner
  compression, strip elections, coupling face.
- **Session-only** → screen state. Blank draft.
- A new control must be placed on this axis explicitly before it is built.
- **Ruling 2026-08-29 (Q2): bubble size and drop stay app-wide** (`PdfPrefs`, captured by
  Drawing profiles). Any future move of a styling knob to per-job is decided case by case,
  not by a blanket rule.
- **Ruling 2026-09-02 (Q3): look controls are single-sourced.** A look control on a
  document's options sheet is a *remote control* for the ONE app-wide pref — the same
  `PdfPrefs` value Settings → Drawing keeps — never a per-page copy. Changing it anywhere
  changes every document; the app stays consistent by construction, and a job's sheets read
  as one family in the workorder file. There is **no per-document override axis for look
  prefs and none is planned**: it would fight Drawing profiles (a profile applied over
  per-page overrides silently changes nothing on the pages carrying them), and it would
  buy a hypothetical no on-device case has asked for.
- **"Drawn differently if needed" is served on the per-job axis, one control at a time.**
  When an on-device case shows one control genuinely needs to differ between jobs, that
  ONE control is promoted from look to fit: a per-job slot in the envelope that follows the
  app-wide default until set, plus a "Save as default" path back — exactly as "Trace depth
  exaggeration" already works (`WearRecord.traceDepthFrac` over
  `PdfPrefs.wearTraceDepthFrac`). Case by case (the Q2 rule), never a blanket override
  system.
- **What earns a sheet row.** A look control gets a row on a document's options sheet when
  it must be *judged against the page it changes* (line weight, S-break, shading,
  fractions, arrows, dual-unit layout, the wear sliders, runout bubbles). It stays
  Settings-only when it is a baseline the sheet's own controls already stand on (the
  sizing-curve anchors — the per-job Shaft height multiplies them), a capability gate
  (per-component units, liner shoulders), a default for a document field (dual-units
  default), or profile management. A control that only means something on one document
  may live on that document's sheet alone (runout bubble size/height) — still app-wide,
  still captured by profiles. Legibility rule: a sheet's header says its drawing settings
  are app-wide, and the per-job controls on it say "saved with this job" in their own
  caption — the reader never has to guess which kind a row is.

### 3.4 Output actions

- **Every preview offers both Print and Export.** Print composes the identical page through
  the same composer call (`util/PdfPrint`); a preview that can only export is a bug.
- **Print is the primary action** (ruling 2026-09-01, Q8): the shop works from paper and
  prints directly from the device; PDF export is the secondary path — a backup/archive
  copy, not the daily route. Wherever both actions appear, Print takes the visually
  primary treatment and leads; Export takes the secondary treatment. "Export all" stays
  an export-only batch (the Android print framework is one interactive job at a time, so
  a batch-print variant is not planned).
- Blank-draft, gates, and export naming behave identically between the tab-body buttons and
  the preview's own actions — one factored action per route, used by both.

### 3.5 Experimental controls

A control added to *find* a value (rather than to be one) is labeled experimental in its
caption and listed here. Current: **Bubble height** (`runoutBubbleDropScale`) — added to
find the sweet spot where the runout pointer lines fit best.

**End state (ruling 2026-08-29, Q5): once the sweet spot is found, the value becomes the
default and the slider moves behind dev options** — kept on hand in case users ask for it,
never deleted outright. That is the template for retiring any experimental control: bake
the found value in, gate the knob, keep the plumbing.

### 3.6 Canvas/paper parity (ruling 2026-08-29, Q6)

On-screen authoring canvases mirror print styling (sizes, shading) as closely as
practical, so the screen manages expectations of what the output looks like. Divergence
is allowed only as a **named allowance** for touch/authoring ergonomics — the current
example is the runout canvas's bubble presentation vs. the output rendering. Any new
divergence gets added to this list deliberately, never slipped in as a side effect.

### 3.7 Help and discoverability (ruling 2026-09-01, Q9)

Help — the glossary included — must be reachable from the app's main menu, not buried
behind Settings ("so hidden I don't even know where to find it" — the failing state).
The rule going forward: any reference content a user might reach for mid-job (what a term
means, what a control does) gets a top-level entry point; Settings is for changing
things, not for finding explanations. Reaching the screen is only half of it — Help also
carries a **search field** and each document tab (Runout, Wear, Undercut, Consolidated
Output) carries a **"?" button that opens Help on that tab's own guide**, so the answer is
one tap from the work rather than a scroll through five sections.

### 3.8 Standalone calculators (Q10)

Small shop calculations should not require building a shaft document. The bore keyway
calculator set the pattern; a **taper calculator** (enter the known values, read the
taper rate) joins it. Calculators are document-free utilities: they never write into the
open document, and their entry point sits with the other utilities, not inside a
component card.

**Interaction model (ruled on-device for the taper calculator, the pattern for the rest):**
a calculator answers when asked, not while the question is being typed — a **Calculate**
button is the one trigger. The answer appears **in the empty field it belongs to**, as an
italic preview, never as typed text (the golden rule reaches the calculators: a field holds
what the user typed and nothing else; a ✓ lets the user *keep* the preview as an input by
explicit act). When too little has been entered, the fields that could complete the solve
turn red and the message names them. A result is a snapshot of the entries it came from and
drops the moment any of them changes, so a stale number is never on screen. Typo feedback
(text that does not read as a number) stays immediate; feedback about the *set* of values
waits for the button.

## 4. Wording conventions

- **"Shade in Components"** — the component shading group (renamed from "Shade in PDF").
- **"Measurement reference"** — the AFT/FWD/Auto tiering choice, same label on sheets and
  in Settings (formerly "Dimension tiering reference" in Settings).
- **"Labels"** — the global component-name default (`showComponentTitles`); the per-card
  tri-state override keeps its own "Show name on drawing" wording.
- **"Blank draft"** — the write-in mode, one term everywhere.
- Control captions are one line or absent; sentences belong in Help.

## 5. Known debts / deferred

- The wear tab's eventual retirement into the consolidated flow (`WEAR_TAB_ENABLED`).
- Bubble height slider: end state decided (§3.5 — dev-options gate), but the move itself
  waits on the sweet spot being found on-device.
- Canvas bubble ergonomics vs print fidelity: the Runout canvas now follows the bubble
  size/drop prefs so authoring matches paper; if touch targets ever suffer at small sizes,
  hit radius may need a floor independent of the drawn radius — that would be a named
  allowance under §3.6.
- UI / rendering / output polish pass: Chris is collecting a concrete list of what and
  where needs updating on-device (Q10); the items become §3 rules when they land.
- "Explicit bodies only" shading does not reach the wear/undercut documents (their
  `SimpleShaftProfile` body pass takes one fill for every run), so those two sheets hide
  the checkbox per §3.2's no-inert-controls rule. If those documents should honor it, the
  pref needs threading through that pass and the undercut strip windows. The per-component
  "Shade on drawing" flag (`shadeOnDrawing`, cards) shares this exact boundary: it decides
  fills on the schematic and runout/consolidated sheets only, and honoring it on wear/
  undercut is the same threading job.

---

## 6. Questions for Chris (answer inline; the doc gets rewritten around the answers)

### Resolved 2026-08-29 (rulings folded into the body)

- **Q1 — Audience.** All five documents are internal: machinists + workorder file; no
  client ever sees them. → §1.1.
- **Q2 — App-wide vs per-job.** Bubble size/drop stay global; further determinations
  case by case, later. → §3.3.
- **Q4 — Compression on wear/undercut.** Flat scale stands; reopen only if it becomes a
  problem on-device. → §3.1 item 3.
- **Q5 — Bubble height end state.** Find the sweet spot, bake it in as the default, hide
  the slider behind dev options (kept on hand). → §3.5.
- **Q6 — Canvas/paper parity.** As close as possible, with named allowances (bubbles) so
  output expectations stay managed. → §3.6.

### Resolved 2026-09-01 (rulings folded into the body)

- **Q7 — Consolidation roadmap.** All outputs required; consolidated + schematic are the
  primaries, then runout and wear, undercut least essential long-term. → §1.2.
- **Q8 — Print vs export.** The shop primarily prints from the device; export is the
  secondary/backup path — Print gets the primary button treatment. → §3.4.
- **Q9 — Glossary.** Help is too hidden: add it to the side menu and make the glossary
  easy to locate. → §3.7.
- **Q10 — Next design pass.** A standalone taper calculator (→ §3.8); UI/rendering/output
  polish waits on Chris's concrete on-device list (→ §5).

### Resolved 2026-09-02 (ruling folded into the body)

- **Q3 — What earns a sheet row vs Settings-only.** Chris: "a class that keeps settings
  consistent across the app… allow a page to be drawn differently if needed." Two readings
  were put to him — (a) sheet rows stay remote controls for the one shared app-wide pref,
  with "drawn differently" served on the existing per-job axis; (b) a new per-document
  override slot per look pref — and he took the recommendation of (a): consistency by
  construction, no fight with Drawing profiles, and any real divergence need promotes one
  control to per-job case by case (the trace-depth precedent). → §3.3.

### Open

*(none)*
