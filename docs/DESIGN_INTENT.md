# ShaftSchematic — Design Intent

Status: **DRAFT — awaiting answers** (questions in §6). Started 2026-08-29 as the running
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

## 2. Standing doctrines (pointers, not restatements)

These are binding and live in `CLAUDE.md` — listed here only so this document reads
complete:

- **Golden rule**: typed values are sacred; no system rewrites them.
- **Reference features** never move geometry, OAL, collision, or the Free-to-End badge.
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
   one. If either document ever gains compression, its sheet gains the slider the same day.
4. **Shade in Components** (Bodies / Tapers / Liners, plus "Explicit bodies only").

The undercut sheet is the one partial exception: its normal form draws **no whole-shaft
profile at all** (detail strips own the page; the profile is only the empty-record
fallback), so it carries neither the height slider nor the S-break slider.

### 3.2 Placement and order

- **Order of presumed use, top to bottom**: content election first (compact chips), then the
  page-reshaping sliders (Shaft height, S-break, Line thickness, Liner compression), then
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

### 3.4 Output actions

- **Every preview offers both Print and Export.** Print composes the identical page through
  the same composer call (`util/PdfPrint`); a preview that can only export is a bug.
- Blank-draft, gates, and export naming behave identically between the tab-body buttons and
  the preview's own actions — one factored action per route, used by both.

### 3.5 Experimental controls

A control added to *find* a value (rather than to be one) is labeled experimental in its
caption and listed here. Current: **Bubble height** (`runoutBubbleDropScale`) — added to
find the sweet spot where the runout pointer lines fit best; once a good default is known
it may be retired into a constant.

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
- Bubble height slider's end state (see §3.5).
- Canvas bubble ergonomics vs print fidelity: the Runout canvas now follows the bubble
  size/drop prefs so authoring matches paper; if touch targets ever suffer at small sizes,
  hit radius may need a floor independent of the drawn radius.
- "Explicit bodies only" shading does not reach the wear/undercut documents (their
  `SimpleShaftProfile` body pass takes one fill for every run), so those two sheets hide
  the checkbox per §3.2's no-inert-controls rule. If those documents should honor it, the
  pref needs threading through that pass and the undercut strip windows. The per-component
  "Shade on drawing" flag (`shadeOnDrawing`, cards) shares this exact boundary: it decides
  fills on the schematic and runout/consolidated sheets only, and honoring it on wear/
  undercut is the same threading job.

---

## 6. Questions for Chris (answer inline; the doc gets rewritten around the answers)

**Q1 — Audience per document.** Who actually reads each of the five documents (shop
machinist, surveyor, customer, class society)? Which ones leave the building? This drives
how conservative each sheet's defaults must be (exaggerations, shading, blank drafts).

> A:

**Q2 — App-wide vs per-job defaults.** Bubble size and drop landed app-wide (`PdfPrefs`,
so profiles capture them). Right call, or should any styling knob be per-job like Shaft
height? What's your instinct for the rule of thumb?

> A:

**Q3 — What earns a sheet row vs Settings-only.** The options sheets duplicate several
Settings → Drawing controls so they can be judged against the live page. Should every
app-wide look control appear in both places, or is there a class that should be
Settings-only to keep the sheets short?

> A:

**Q4 — Compression on wear/undercut.** Today those documents never foreshorten (flat
scale). Long shafts make their strips/profile cramped instead. Should they ever gain the
compression system (and with it the S-break slider), or is flat scale a fixed property of
those records?

> A:

**Q5 — Bubble height end state.** Once you find the sweet spot: keep the slider, or bake
the value in as the new default and retire it?

> A:

**Q6 — Canvas/paper parity.** Should on-screen authoring canvases always mirror print
styling (sizes, shading) as closely as possible, or is the canvas allowed to optimize for
touch/authoring even where it diverges from paper?

> A:

**Q7 — Consolidation roadmap.** Is the long-term shape "Output tab is the product, the
other tabs are authoring surfaces"? If so, which standalone exports (classic runout sheet,
wear document, undercut drawing) must survive forever regardless?

> A:

**Q8 — Print vs export in practice.** Does the shop mostly print directly from the device,
or export PDFs into a job folder / send them elsewhere? (Decides whether Print or Export
gets the primary button treatment, and whether Export-all should also batch-print.)

> A:

**Q9 — Glossary.** Any terms on the sheets that still read wrong to a shop audience?
(e.g. "Liner compression", "Measurement reference", "Coupling face", "Trace depth
exaggeration").

> A:

**Q10 — The next design pass.** What are the top three friction points you hit on-device
that this document should turn into rules next? (Free-form; these become §3 additions.)

> A:
