# Shaft Templates + Per-Component Ø Visibility — Plan

Date: 2026-08-11
Status: **All six parts implemented** on `feat/templates-dia-toggle`, uncommitted, 1270 unit
tests green (was 1197 on main). Every open question in §7 was resolved with the recommendation
stated there — each remains a small change if the answer differs. Two corrections to this
document, found while building:

- **§C said the blank-draft toggle is persisted. It is not** — `setPdfBlankDraft` is
  deliberately session-only ("a forgotten sticky toggle would silently blank every future
  export"). The new Ø-callout switch matches it, which also answers **Q16** in the opposite
  direction from my recommendation, and better.
- **§D.4 was worse than described.** Reading the code more carefully while fixing it: the
  count is applied **per drawn run**, so a body split into three by liners drew nine bubbles
  while its editor row said "3". That is the direct cause of the "3 runouts on a 1–2" segment"
  you reported — not just the flat default. Both are fixed. See CHANGELOG 2026-08-11.

What shipped is recorded in `CHANGELOG.md` (2026-08-11) and the contract docs
(`Templates.md`, `RunoutSheet.md` §"Measurement stations", `PDF_EXPORT.md` §5.3,
`AddComponentDialogs.md`, `CLAUDE.md`). The sections below are the design as written before
implementation, kept for review context.

Requested work, in one document because it lands together:

| Part | What | Size |
|---|---|---|
| **A1** | Home-screen **template browser** — collapsible sections (liner size → liner count → layouts), each leaf showing shaft previews you pick from | Large |
| **A2** | **Save as template** — an option near "Save as" that files the current drawing into the template store | Small–medium |
| **B** | **Show/hide Ø toggle** on component cards (bodies included) — keep a measured diameter off the part of the schematic where it wasn't measured | Small |
| **C** | **Ø callouts on blank write-in drafts** — an option to print the leaders or leave the sheet clear for hand-writing | Small |
| **D** | **Runout station interval** — one bubble per 20", replacing the flat "3 per body" default | Small–medium |
| **E** | **Bubble-count access from the Consolidated tab** — stop having to switch screens to change a count | Small |

Recommended build order is **B → C → E → D → A2 → A1 → seed templates**. B, C and E are
small and independent; D should follow E because it lands in the same runout code and because
of the defects in §D.4 that it forces a decision about; A2 creates the storage layer that gives
A1 something to browse.

> **§D.4 is the one part of this document I'd read first.** Tracing why short body segments
> get 3 bubbles turned up a fragment-identity mismatch between the runout preview and the
> printed runout sheet that looks like it can drop hand-entered TIR values from the print.
> It is not something you asked for, I have not changed anything, and it needs your call.

---

# Part B — Per-component Ø visibility toggle

## B.1 The problem being solved

On-device report: a shaft whose body ran under fiberglass for most of its length, with one
bare window that could actually be measured. The schematic printed the body's Ø callout at
the **center of the body** — i.e. over the fiberglassed run — implying the measurement was
taken somewhere it could not have been taken. The value was right; the *placement* lied.

Today the below-shaft Ø callouts are built in exactly one place
([ShaftPdfComposer.kt:1416](app/src/main/java/com/android/shaftschematic/pdf/ShaftPdfComposer.kt#L1416)):

```kotlin
internal fun buildBodyOdCallouts(bodies: List<Body>): List<DiaCallout> =
    bodies.filter { it.diaMm > 0f }
        .groupBy { it.diaMm }                       // one callout per UNIQUE Ø
        .sortedByDescending { it.key }
        .mapNotNull { (diaMm, group) ->
            val anchor = group.maxByOrNull { it.lengthMm }   // ← anchored at the LONGEST body
            …
        }
```

with an identical liner mirror at [:1433](app/src/main/java/com/android/shaftschematic/pdf/ShaftPdfComposer.kt#L1433).
"Longest body of that Ø" is why the label landed on the fiberglass run.

## B.2 Proposed behavior

A per-card toggle — **"Show Ø on drawing"**, default **on** — that removes that component
from the below-shaft Ø callout pass. It is a **draw-only flag**: same posture as
`keywaySpooned` (draw-only variant) and `CouplerBoltSlot.showDimensionRail`. It changes
nothing in the model, resolve, OAL, collision, body splitting, or footer geometry. The
golden rule is untouched — no typed value is rewritten, moved, or rounded; only whether a
leader gets drawn.

**The load-bearing detail: filter before grouping.** Hidden components must be dropped
*before* `groupBy { it.diaMm }`, not after. That way, if two bodies share Ø 6.000" and you
hide the fiberglassed one, the callout doesn't vanish — its anchor **moves to the longest
still-visible body of that Ø**, i.e. onto the section you actually measured. That is the
behavior that solves the reported case, and it means the practical workflow is:

> split the run into two body rows (fiberglassed + measurable window), hide Ø on the
> fiberglassed one, leave it shown on the window.

If instead a single body row is hidden and no sibling shares its Ø, that Ø simply does not
print below the shaft (it still prints in the footer — see Q6).

## B.3 Which cards get the toggle

| Card | Gets toggle? | Why |
|---|---|---|
| Body (explicit) | **Yes** | The reported case. Field: `Body.showDiaOnDrawing` |
| Liner | **Yes** | Same callout engine, same failure mode (a liner Ø anchored on the wrong sleeve) |
| Body (auto) | **Yes, but shaft-level** | Auto spans have no stored row. One flag, `ShaftSpec.showAutoBodyDia`, matching the single `autoBodyDiaMm` — "one piece of stock, one Ø, one visibility" |
| Taper | **Not in v1** (Q5) | Tapers get no below-shaft callout; their Ø prints as footer L.E.T./S.E.T. |
| Thread | **Not in v1** (Q5) | Major Ø is footer-only |

## B.4 Model / persistence

```kotlin
// model/Body.kt
val showDiaOnDrawing: Boolean = true,

// model/Liner.kt
val showDiaOnDrawing: Boolean = true,

// model/ShaftSpec.kt   (covers every auto span)
val showAutoBodyDia: Boolean = true,
```

Additive, defaulted, inside `ShaftSpec` → **no envelope version bump, no codec change, no
autosave/snapshot/import plumbing**. Old documents decode with `true`, which is today's
behavior exactly. `@JsonNames` alias not needed (new field, no rename history).

## B.5 Implementation notes / gotchas

1. **The resolved→Body mapping drops fields.** The composer rebuilds `Body` objects from
   `ResolvedBody` at
   [ShaftPdfComposer.kt:142-151](app/src/main/java/com/android/shaftschematic/pdf/ShaftPdfComposer.kt#L142-L151),
   carrying only `id / start / length / dia`. The new flag has to be carried through there,
   looked up from `spec.bodies` by **`resolvedBodyBaseId(comp.id)`** — not the raw id — because
   a body split by a liner or taper resolves into multiple fragments with derived ids. Every
   fragment of a hidden body must be hidden.
2. **Auto bodies flow through the same list**, so `showAutoBodyDia` is applied in the same
   mapping (`source == AUTO → spec.showAutoBodyDia`).
3. **One call site.** `buildBodyOdCallouts` + `buildLinerOdCallouts` are used only at
   [:443](app/src/main/java/com/android/shaftschematic/pdf/ShaftPdfComposer.kt#L443).
   Diameter callouts are PDF-only (no canvas equivalent), so the draw-both-sites rule does
   **not** apply here — there is nothing to keep in sync.
4. **Blank drafts.** In write-in mode the callout becomes an empty rule to hand-write on. A
   hidden Ø should be hidden there too (the toggle is about *where the value belongs*, not
   about whether a value exists). **Part C** adds the master switch for whether a blank sheet
   prints Ø leaders at all; the two compose as an AND — the master switch decides *whether
   any* leaders print, this toggle decides *which*.
5. **Unrelated observation, worth a look while in this file:** the "keyway-bearing body pins
   at true scale" span at
   [:227](app/src/main/java/com/android/shaftschematic/pdf/ShaftPdfComposer.kt#L227)
   filters `bodiesForPdf.filter { it.hasKeyway }` — but `bodiesForPdf` comes from that same
   field-dropping mapping, which never copies the keyway fields, so `hasKeyway` is always
   false whenever `resolvedComponents` is supplied (i.e. always, from the app). The pin looks
   **inert in production** and live only in tests that pass `resolvedComponents = null`. I have
   not changed anything; flagging it because the fix is the same one-line mapping edit this
   work touches. Want it fixed in the same change, separately, or left alone? (Q8)

## B.6 UI

Pattern copied from the coupler-slot "show dimension rail" row
([ComponentCarousel.kt:1301](app/src/main/java/com/android/shaftschematic/ui/screen/ComponentCarousel.kt#L1301)):
a 48dp-min toggleable row, label left, `Checkbox` right.

- **Label:** "Show Ø on drawing"
- **Placement:** immediately **below the Ø field**, so it reads as a modifier of that field
  (the explicit-body checkbox stays above the fields where it is now).
- **Auto-body card:** same row under the Ø field, bound to the shaft-level flag, with the
  label "Show bare-shaft Ø on drawing" so it's obvious the toggle is shared by every auto span.
- **testTags:** `body_show_dia_toggle`, `liner_show_dia_toggle`, `autobody_show_dia_toggle`.

### ⚠ This touches a critical invariant — needs your call (Q4)

CLAUDE.md: *"Every control that exists in a component's carousel edit card must also appear
in its Add dialog under the same conditions."* Strictly applied, `AddBodyDialog` and
`AddLinerDialog` would each need a "Show Ø on drawing" checkbox.

There is already one **documented carve-out** for exactly this class of control — the coupler
bolt slot's deferred "show dimension rail" toggle lives on the card only, and CLAUDE.md
records it as an explicit exception. My recommendation is to follow that precedent: a
post-hoc *display* property (default on, only ever touched after you see the printed sheet in
the wrong place) has no business in an add dialog, where it would be a permanently-checked
box adding noise to every add. If you want strict parity instead, it's two extra checkboxes
and two extra params — cheap, just noisier.

Either way the invariant text in CLAUDE.md and `AddComponentDialogs.md` gets updated in the
same change, so the rule and the code never disagree.

## B.7 Tests

- `BodyOdCalloutsTest` (exists): hidden body excluded; **anchor moves to the longest visible
  body of the same Ø**; all-hidden group produces no callout; hidden body with a unique Ø
  produces no callout.
- `LinerOdCalloutsTest` (exists): the same four, mirrored.
- New: decode of a pre-feature document yields `showDiaOnDrawing == true` for bodies and liners.
- New: VM setters are no-ops when the value is unchanged (must not dirty the document — same
  guard as `updateCouplerBoltSlotShowRail`).
- New: fragment-aware hiding — a body split by a liner hides **all** its fragments.
- Instrumented: toggle row present and functional on the explicit-body, auto-body, and liner cards.

## B.8 Docs to update with the change

`docs/PDF_EXPORT.md` §5.3 (the below-only/tiered/footer-formatted callout contract), CLAUDE.md
(add the flag to the "Diameter callouts are BELOW-only…" invariant + the add-dialog carve-out),
`AddComponentDialogs.md`, `CHANGELOG.md`.

---

# Part A — Shaft templates

## A.1 What was asked for

> A template section on the home screen, where I can save shaft templates without opening an
> existing shaft and printing it blank. After the button there would be collapsible sections,
> starting with liner sizing (4–12), then number of liners (1–3), and each of those would then
> have differently laid out shaft drawings, with a preview of the shaft. They don't have to be
> populated, but if I create a template from an existing drawing, then it gets added to the
> template storage area. The values could be generic but close to the sizing.

Two tiers: **A1** the browser, **A2** the save path.

## A.2 Storage

New `io/TemplateStorage.kt`, a near-mirror of `InternalStorage` pointed at
`<filesDir>/templates/`, same `.shaft` extension, same atomic `.tmp`/`.bak` save, same
list/load/delete/rename surface. Templates are ordinary `.shaft` documents — no new file
format, no new codec, and a template can be opened by every existing path if it ever needs to be.

**Buckets are derived from the file, never stored in an index.** A new pure module
`template/TemplateBuckets.kt`:

```kotlin
data class TemplateSummary(
    val filename: String,
    val displayName: String,
    val sizeBucket: SizeBucket,     // liner size 4"…12", or NONE / OTHER
    val linerCount: Int,            // 0,1,2,3+   (bucketed as LinerCountBucket)
    val spec: ShaftSpec,
    val unit: UnitSystem,
)
```

No sidecar index means nothing to keep in sync and no orphan class of bug — the file *is* the
truth, and re-bucketing after an edit is automatic. Cost is decoding N files to build the
list; sample docs are ~1.6 KB, so even a few hundred templates is a sub-100 ms IO-dispatcher
scan, cached in memory and re-scanned only when the store changes. If it ever gets slow, an
index can be added later as a pure cache without changing the contract.

## A.3 Bucketing rules — **needs your answers (Q1–Q3)**

My reading of "liner sizing (4–12)" is **liner OD in inches, rounded to the nearest inch**,
which is the number the shop says out loud. But it could equally mean the *shaft* size the
liner sits on, which is a different (smaller) number and changes every bucket. This is the one
genuinely ambiguous thing in the whole spec, and it is one pure function either way — see Q1.

Working proposal:

- **Size bucket:** nearest inch of the **largest liner OD**, clamped to 4"–12". Outside that
  range → an "Other sizes" bucket at the bottom. Zero liners → a "No liners" bucket at the top
  (a straight shaft template still deserves a home).
- **Multiple liners with different ODs:** bucket by the largest (Q2).
- **Count bucket:** 1, 2, 3, and **"3+"** for anything above (Q3).
- **Units:** the bucket boundary is canonical mm; the *label* is formatted in the user's
  current unit through the existing formatters, so mm users see mm headings. Model stays mm-only
  per the unit-edge rule.
- Buckets with nothing in them are **hidden**, not shown empty — 27 empty accordion rows is
  worse than a short list. (Q9 if you'd rather see the whole grid as a "these are the slots"
  map.)

## A.4 Tier 1 — the browser

### Where it lives

Recommendation: a **dedicated `templates` route**, reached from a "Start from Template" button
on the home screen directly under "New Drawing". Reasons: `StartScreen` today is a centered,
**non-scrolling** `Column` that already stacks Unsaved drafts + Recent + four buttons; an
accordion with preview thumbnails inline would need the whole screen converted to a scroll
container and would push the primary buttons off-screen on a phone. The accordion itself is one
composable either way, so if you want it inline after all it's a drop-in — say the word (Q10).

### Structure

```
Templates                                            [screen]
├─ ▸ No liners                    (3)
├─ ▾ 6" liners                    (5)
│   ├─ ▸ 1 liner                  (2)
│   └─ ▾ 2 liners                 (3)
│       ├─ ┌───────────────────────────────┐
│       │  │  [ shaft preview thumbnail ]  │   Trawler, taper both ends
│       │  │  OAL 94.5"  ·  max Ø 6.0"     │   ⋮
│       │  └───────────────────────────────┘
│       ├─ …
└─ ▸ 8" liners                    (2)
```

- `LazyColumn`, outer accordion = size, inner = liner count, leaf = template cards.
- Card = preview thumbnail + name + a derived caption (OAL, max Ø, component counts) +
  overflow menu (Use / Rename / Delete).
- Expansion state is remembered per session; on first open, expand nothing (or the first
  non-empty bucket — trivial either way).

### Thumbnails

New `ui/drawing/compose/ShaftThumbnail.kt`: a static preview that calls the *same*
`ShaftLayout.compute` → `ShaftRenderer.draw` pair `ShaftDrawing` uses, minus gestures, pan/zoom,
the reset button, grid, highlight, and tap-to-select. No new drawing math and no second
renderer — this is the same "one draw implementation" discipline the sheets follow. Each card
runs `resolveComponents(spec, overallIsManual)` (already pure) on its decoded spec;
`LazyColumn` only composes visible rows, so an off-screen bucket costs nothing.

Colors: honor the user's preview color settings so a template looks like what they'll get.

### Choosing a template

Tapping **Use** must go through the existing `runGuarded` unsaved-changes prompt in
`AppNav` — same as New / Open / Open recent — then load into a **new, unnamed** document.

New VM entry point `applyTemplate(raw: String)`, which is `importJson` minus the job identity:

| Carried from the template | Reset |
|---|---|
| `spec` (all geometry, keyways, slots) | `jobNumber`, `customer`, `vessel`, `shaftPosition`, `notes` |
| `preferredUnit`, `unitLocked` | `wearRecord`, `runoutReadings`, `undercutRecord` |
| derived OAL mode (`overallIsManual`) | `currentDocumentName` → **null** |
| | `runoutConfig` → defaults (Q11 — this holds your per-job "Shaft height" / liner-compression tuning; carrying it is defensible if you tune per *shaft family* rather than per job) |

Two rules worth pinning:

- **`currentDocumentName = null`**, so the first Save prompts for a name. A template can
  never be silently overwritten by the drawing made from it.
- **The session starts dirty, not clean.** `importJson` ends with `markDocumentSaved()`, which
  would make a freshly-loaded template count as "no unsaved work" — quit the app and the work is
  gone, because `isDefaultSession()` only protects *blank* sessions from the draft ring, and a
  template-loaded session isn't blank but also isn't saved anywhere. `applyTemplate` should
  leave the baseline blank so the autosave draft ring protects it from the first moment.
- Component **ids are kept as-is** (not re-minted). Ids never cross document boundaries —
  wear/runout/undercut records key within a single document — so two drawings from the same
  template sharing ids is harmless.

## A.5 Tier 2 — save as template

**Entry point:** an outlined **"Save as template…"** button on the existing Save Drawing screen
([InternalDocRoutes.kt:683](app/src/main/java/com/android/shaftschematic/ui/nav/InternalDocRoutes.kt#L683)),
sitting next to "Save a copy to device…". That's the "save as or something similar area" you
described, and it keeps one place to learn. (Q12: also add it to the editor overflow menu?)

**Flow:** button → small dialog with

- template name (pre-filled from the derived bucket, e.g. `6in 2-liner — taper both ends`),
- the derived bucket shown read-only (`6" liners · 2 liners`) so you can see where it will file,
- Save / Cancel, with the same overwrite confirmation the document save uses.

**Job data is stripped at WRITE time, not just at load time.** A template file that still
contains a customer name, vessel, job number, notes, wear pits, runout readings and undercuts
is a job-data leak into every drawing made from it, and it would also ride along into any
template you export or share. The write path encodes a scrubbed envelope: spec + unit +
unit-lock only. Load-time stripping stays as a belt-and-braces second line for any template
authored before this rule (or hand-copied into the folder).

**Management:** Rename and Delete from the template card's overflow menu (Delete confirms).
That is the whole management surface for v1 — no folders, no tags, no reordering.

## A.6 Seeded starter templates

You said templates "don't have to be populated", and the primary path is you saving real
drawings. But an empty browser on first launch reads as broken, and "the values could be
generic but close to the sizing" suggests you do want something in there.

Recommendation: ship a **thin** seed in `assets/templates/` — my suggestion is 9 files
(3 common liner sizes × 1/2/3 liners) with round, plausible geometry — reusing the *exact*
seeding machinery `sample_shafts` already has (version-gated, hash-ledgered, never overwrites
user files, prunes only provably-untouched files). It needs its own seed-version key in
`SettingsStore` so template seeding and sample seeding version independently.

**This is where I need the most input (Q13–Q14):** which sizes, and — more importantly —
which *layouts*. "Differently laid out shaft drawings" is your domain knowledge, not mine. The
layouts I'd guess at from the existing sample shafts are: prop-nut thread → prop taper → body →
cutless liner → fwd taper; taper both ends with two liners; straight body with coupling keyway
and one liner. If you list the variants you actually reach for, I'll author them; otherwise the
honest alternative is to seed nothing and let the store fill up from your real work, which is
also a perfectly good answer.

## A.7 Files touched (Part A)

**New**
`io/TemplateStorage.kt` · `template/TemplateBuckets.kt` (pure) ·
`ui/screen/TemplatesRoute.kt` · `ui/drawing/compose/ShaftThumbnail.kt` ·
`assets/templates/*.shaft` · `docs/Templates.md` (contract, + README index line)

**Modified**
`ui/screen/StartScreen.kt` (entry button) · `ui/nav/AppNav.kt` (route + guarded load) ·
`ui/nav/InternalDocRoutes.kt` ("Save as template…") ·
`ui/viewmodel/ShaftViewModel.kt` (`applyTemplate`, `exportTemplateJson`) ·
`data/SettingsStore.kt` (template seed version + ledger) · `CHANGELOG.md`

## A.8 Tests (Part A)

- `TemplateBuckets`: pure bucketing — 0/1/2/3/4 liners, mixed ODs, out-of-range ODs, exact
  4"/12" boundaries, mm-authored documents.
- `TemplateStorage`: save/list/load/delete/rename against a temp dir (mirrors the existing
  `InternalStorage` tests), including the atomic-save and name-normalization paths.
- Scrub-on-write: a template written from a document with job metadata + wear/runout/undercut
  records decodes with all of it empty.
- `applyTemplate`: metadata cleared, spec loaded, `currentDocumentName == null`, session
  reports unsaved work (draft-ring protection), OAL mode derived not inherited.
- Seeding: never overwrites a user template; version gate; ledger prune only touches
  byte-identical files.
- Instrumented: browser renders buckets, expands, and "Use" routes through the
  unsaved-changes guard.

---

# Part C — Ø callouts on blank write-in drafts

> "For blank write-in templates, let there be an option for adding diameter callouts, or they
> can just be hand written."

Today a blank draft always draws the Ø leaders with an empty rule where the number goes
(`DiameterLeaderRenderer(blankValues = …)`), so the layout is fixed before you pick up a
pencil. You want the choice: leaders printed and ready to fill, or a clear shaft you annotate
freehand.

**Proposal:** a "Ø callouts" option that only applies in blank mode, sitting **directly under
the existing blank-draft switch** in the PDF preview's options sheet
([PdfPreviewScreen.kt:625](app/src/main/java/com/android/shaftschematic/ui/screen/PdfPreviewScreen.kt#L625))
and greyed out when blank mode is off. Off → the whole below-shaft callout pass is skipped for
that sheet.

Details:

- **Schematic sheet only.** The runout / consolidated sheets carry no below-shaft Ø callouts
  (their measured values print *inside* the profile), so there is nothing to gate there.
- **Scope of the switch:** it drops the leaders entirely — line, arrow, and rule — not just the
  value. A blank leader with nothing to write on is worse than no leader.
- **Lifetime:** the blank-draft toggle it lives under is already a **persisted** preference
  (`vm.setPdfBlankDraft` → DataStore), so this rides along the same way and remembers your
  choice between sheets. (Q16 if you'd rather it reset to "on" every time — one is "the way I
  print", the other is "a per-sheet decision".)
- **Composition with Part B:** master switch AND per-component toggle. A component whose Ø is
  hidden never prints, blank or not; with the master switch off, none print.
- Wiring is one new field on `PdfExportOptions` (defaulted `true` → today's behavior) plus the
  `if (calls.isNotEmpty())` guard at
  [ShaftPdfComposer.kt:443](app/src/main/java/com/android/shaftschematic/pdf/ShaftPdfComposer.kt#L443).
  No model change, nothing in the document envelope.

---

# Part D — Runout station interval (one bubble per 20")

> "The default runouts always try to give body segments 3 runouts, even when it's an inch or
> two long. Let's set an interval for runout bubbles, one for every 20 inches."

## D.1 Why it happens today

Two compounding causes, and the second is the one that actually produces the 1"-segment-with-
3-bubbles case you saw:

1. `RunoutConfig.BODY_DEFAULT_COUNT = 3` is **flat** — length is not consulted. The KDoc
   records this as deliberate ("Longer bodies do NOT default to more stations; the user raises
   the count per component"). You're overriding that decision, which is your call — I'm noting
   it so the doc gets rewritten rather than silently contradicted.
2. The count is applied **per drawn body run, not per stored body**. A body that a liner or
   taper splits resolves into several fragments, and `collectRunoutStations` walks *spans*, so
   each fragment independently gets the full default. A body cut into three runs by two liners
   gets **3 + 3 + 3 = 9** bubbles, and the 1–2" leftover run at the end gets a full 3 of its
   own. Meanwhile the station editor collapses all fragments into one row (`distinctBy { it.id }`)
   that reads "3" — so the number on the screen and the number on the sheet already disagree.

An interval-based default fixes both symptoms at once: the 1" run asks for `ceil(1/20) = 1`.

## D.2 Proposed rule

```kotlin
// RunoutConfig.companion
const val RUNOUT_STATION_INTERVAL_MM = 508f   // 20 inches

fun defaultStationCount(kind: RunoutComponentKind, lengthMm: Float): Int = when (kind) {
    BODY  -> ceil(lengthMm / RUNOUT_STATION_INTERVAL_MM).toInt().coerceIn(1, MAX)
    LINER -> max(2, ceil(lengthMm / RUNOUT_STATION_INTERVAL_MM).toInt()).coerceAtMost(MAX)
    TAPER -> 2
}
```

- **Bodies:** `ceil(length / 20")`, floor of **1** — every run gets at least one reading, no run
  gets three because it exists. 60" body → 3 (what it gets today), 100" → 5, 2" → 1.
- **Tapers:** stay at **2**. A taper is short by nature and its two stations are edge-inset by
  convention, one near the S.E.T. and one near the L.E.T. — that is the shop convention, not a
  density choice. (Q17)
- **Liners:** `max(2, ceil(length / 20"))` — 2 is the floor because the edge-inset convention
  needs both ends, but a 60" stern-tube liner gets 3. (Q17)
- **Cap:** a 400" line shaft would ask for 20 stations in one run, and the layout engine notes
  ~27 stations is where a letter page starts compressing. A per-component cap (**suggest 10**)
  keeps a long shaft printable, and the existing `RunoutBubblePlan.compressed` flag still
  catches the pathological cases. (Q18)
- **Overrides are untouched** — `componentOverrides[id]` still wins, so anything you set by hand
  stays exactly as set. The interval only changes what happens when you've set nothing.

**One function, both consumers.** The default is read in two places —
[RunoutBubbleLayout.kt:152](app/src/main/java/com/android/shaftschematic/geom/RunoutBubbleLayout.kt#L152)
(the geometry, shared by PDF and canvas) and
[RunoutRoute.kt:347](app/src/main/java/com/android/shaftschematic/ui/screen/RunoutRoute.kt#L347)
(the number the +/− editor shows). Both must call the same function or the editor will claim
"3" while the sheet draws 1. The station-editor row also needs the component's **length**,
which it already has from the resolved component.

## D.3 Existing documents: readings can shift under you

Runout readings are keyed `(componentId, stationIndex)`. Change a component's default count and
station 1 of 3 is no longer at the same place as station 1 of 5 — a value you hand-entered
against a physical spot now labels a different spot, or orphans and silently stops printing.
That is a typed value being invalidated by a system change, which is the thing the golden rule
exists to prevent.

**Proposed migration:** at decode, for every component that **has at least one recorded
reading** and **no explicit override**, materialize an override equal to the *old* default
(3 body / 2 taper / 2 liner). Existing sheets then reprint pixel-identical, new documents get
the interval, and the freeze is visible and editable in the station editor rather than hidden.
It needs no envelope change (it writes into the existing `componentOverrides` map) and the codec
can classify ids without resolving: match against `spec.bodies` / `tapers` / `liners`, and treat
anything unmatched (auto-body spans) as a body.

If you'd rather not carry migration code for a handful of old documents, the alternative is to
accept the shift and re-check any runout sheet you reprint. (Q19)

## D.4 ⚠ Defects found while tracing this — need your call

I read these out of the code while working out where the interval belongs. **I have not changed
anything**, and I have not reproduced them on a device — they are code-reading findings, stated
with the confidence that gives.

**D.4.1 — The runout PDF and the runout preview key stations differently.**
The canvas builds its spans with the **base** body id
([RunoutRoute.kt:741](app/src/main/java/com/android/shaftschematic/ui/screen/RunoutRoute.kt#L741),
`resolvedBodyBaseId(rc.id)`), while the PDF builds its spans from `withResolvedBodies`, which
keeps the **fragment** id (`"<id>#2"`,
[ShaftPdfComposer.kt:1498-1500](app/src/main/java/com/android/shaftschematic/pdf/ShaftPdfComposer.kt#L1498-L1500) →
[RunoutPdfComposer.kt:243](app/src/main/java/com/android/shaftschematic/pdf/RunoutPdfComposer.kt#L243)).
For a body that a liner splits, that means:

- **A count override doesn't reach the print.** The editor writes `overrides["X"]`; the PDF asks
  for `overrides["X#2"]` and misses, so the second run silently keeps its default.
- **A hand-entered TIR value can fail to print.** The reading is stored against `("X", 1)` and
  the PDF looks up `("X#2", 1)` — `readings.find` misses and the bubble prints empty. The value
  is still in the file; it just doesn't reach the paper.

Both are invisible on an unfragmented body, where the fragment id *is* the base id — which is
presumably why this hasn't bitten before. It bites exactly on shafts with liners over bodies.

**D.4.2 — Fragments of one body reuse station indices.**
On the canvas side all fragments share the base id and each restarts its `stationIndex` at 0, so
two runs of the same body both own `("X", 0)`. One reading, drawn in two bubbles.

**Proposed fix (one change, fixes both):** a single shared span builder used by both draw sites
— base-id keyed, with `stationIndex` assigned continuously across a component's fragments in
AFT→FWD order. That is the same "one implementation, two draw sites" rule the wear pits, spoon
bowls, and runout markers already follow, and the runout sheet is the one place it isn't held.
It is roughly a 40-line change plus tests, and it has to happen **before or with** Part D, because
an interval default makes fragment counts differ from each other and turns a latent mismatch into
a visible one.

Q20 asks how you want this handled. Note that the fix changes which stations a stale reading
lands on for fragmented bodies — the §D.3 migration should cover the same documents.

---

# Part E — Bubble-count access from the Consolidated tab

> "In the consolidated sheet, I had no way to adjust the bubble count, I had to switch screens
> to the runout sheet. Perhaps add a button to go to runout bubble sheet for faster (and
> clearer) access?"

The station editor is a self-contained block on the Runout tab
([RunoutRoute.kt:495-507](app/src/main/java/com/android/shaftschematic/ui/screen/RunoutRoute.kt#L495-L507)):
a title and one `RunoutStationRow` per component, each a label with +/− buttons calling
`vm.setRunoutBubbleCount`. Nothing about it is Runout-tab-specific.

**Two ways to fix it, and I'd do both:**

- **(a) The button you asked for.** `EditorTab` state lives in `ShaftEditorRoute`
  ([:53](app/src/main/java/com/android/shaftschematic/ui/screen/ShaftEditorRoute.kt#L53))
  and each route currently receives only `onOpenSidebar`, so this is one new callback
  (`onGoToRunout = { activeTab = EditorTab.RUNOUT }`) threaded into `OutputRoute`. Trivial.
- **(b) The editor itself, in place.** Extract the block as `RunoutStationCountEditor` and show
  it on the Output tab in a bottom sheet behind an "Adjust bubbles" button. The Output tab
  already hosts the worn-section editor, the variant picker, and the Shaft-height slider, so a
  per-job control is at home there — and it fixes the actual complaint (*having to switch
  screens*) rather than making the switch faster.

Recommendation: **(b) as the primary control, (a) as an "Open runout sheet" escape hatch** for
when you want the full authoring surface. One extracted composable serves both, so the two
surfaces can't drift. If you only want the button, say so and (b) drops out (Q21).

Worth noting: with Part D in, the counts you get by default should be right far more often, so
this control becomes the exception rather than the routine step it is today.

---

# §7 Questions for you

Answer by number — anything you skip, I'll take my recommendation as the default.

**Part A — templates**

1. **What does "liner sizing 4–12" key off?** (a) liner **OD** rounded to the nearest inch
   *(my assumption)*, or (b) the **shaft/body Ø** under the liner, or (c) something else you'd
   call the "size".
2. Template with **two liners of different ODs** — bucket by the **largest** *(my default)*,
   the aft-most, or list it under both?
3. **More than 3 liners** — a "3+" bucket *(my default)*, or does that never happen?
4. **Add-dialog parity carve-out** for the Ø toggle — follow the coupler-slot precedent and keep
   it card-only *(my recommendation)*, or add the checkbox to the Add dialogs too?
5. Should the toggle also cover **tapers and threads** (which would mean suppressing their
   **footer** L.E.T./S.E.T./thread lines, since they have no below-shaft callout), or bodies +
   liners only *(my default)*?
6. When a body's Ø is hidden on the drawing, should the **footer "Body:" Ø list** still show that
   diameter? My instinct: **yes, keep it** — the value is true for the shaft, it was only the
   *placement* that misled. Say if you want it gone from both.
7. ~~Blank drafts and hidden Ø~~ — **answered by your Part C ask.** Master switch (Part C)
   decides whether a blank sheet prints Ø leaders at all; the per-component toggle (Part B)
   decides which. Flag it if you read that composition differently.
8. The apparently-inert **keyway true-scale pin** (§B.5.5) — fix it in this change, file it
   separately, or leave it alone?
9. **Empty buckets** — hide them *(my default)*, or show all 4"–12" × 1–3 slots greyed out so the
   structure is visible?
10. Template browser as its **own screen** off a home button *(my recommendation, home screen
    doesn't scroll today)*, or inline on the home screen as you first described?
11. Should a template carry its **`runoutConfig`** (per-job "Shaft height", liner compression,
    S-break)? Reset to defaults *(my default)* or carry it along?
12. Also put "Save as template" in the **editor overflow menu**, or Save-screen only *(my default)*?
13. **Which liner sizes** should the seeded starters cover?
14. **Which layouts** do you actually reach for? (This is the one I can't guess well — a list
    like "thread+taper aft / body / liner / taper fwd" per size is enough and I'll author them.)

**Anything I should not assume**

15. Should templates be **exportable/importable** (share a template file with another device or
    another tech), or is on-device only fine for v1? I've assumed on-device only, but the storage
    layer makes export nearly free if you want it.

**Parts C / D / E — blank-draft callouts, station interval, bubble access**

16. The blank-draft **"Ø callouts" switch** — remembered between sheets like the blank-draft
    toggle it sits under *(my default)*, or reset to on every time so it's a per-sheet decision?
17. **Interval for tapers and liners** — tapers stay at a fixed 2 and liners get
    `max(2, ceil(L/20"))` *(my default)*, or should the 20" interval govern all three kinds
    uniformly?
18. **Per-component station cap** — 10 *(my suggestion)*, some other number, or no cap and let
    the layout engine compress?
19. **Existing documents with readings** — freeze their counts at decode so old sheets reprint
    identically *(my recommendation)*, or accept that reprints shift and skip the migration code?
20. **§D.4 (preview/print station-key mismatch)** — fix it as part of this work *(my
    recommendation, since D lands in the same code)*, file it as its own change, or leave it
    until you've confirmed it on a real shaft? If you have a saved job with a **liner sitting on
    top of a body** and TIR values entered, printing it would confirm or kill this in about a
    minute — that's the check I can't run from here.
21. **Part E** — the in-place bubble editor plus a jump button *(my recommendation)*, or just the
    button you asked for?
22. **20 inches** — is that the number, or was it shorthand for "something length-based"? It is a
    one-constant change either way, and if it should be a **Settings** slider rather than a fixed
    constant, say so now — that's a different (small) piece of plumbing.
