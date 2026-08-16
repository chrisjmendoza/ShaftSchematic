# Changelog

All notable changes to **ShaftSchematic** will be documented in this file.

The format is inspired by [Keep a Changelog](https://keepachangelog.com/) and follows [Semantic Versioning](https://semver.org/).

---

## 2026-08-15

### fix: the wear preview's options menu no longer hides the page it changes

Opening the tune menu over the wear preview covered the sheet completely, so every change had to
be made blind — choose, close the menu, look, reopen. The wear preview now uses the same layout
the runout and output previews already had: the page stays pinned above the menu as a full-width
strip, cropped to where the drawing actually is so no blank margin eats the view, and the menu is
capped to the room left under it. Each change still commits on release and redraws the whole page,
and now that redraw is visible as it happens.

### feat: pick which components print, without opening the preview

The Components section — the complete-shaft toggle, a checkbox per liner, taper, and body, and the
Default / All / None row — used to live only inside the preview's tune menu. Choosing what a sheet
draws is part of building the document, not part of styling the print, so it now sits on the Wear
tab itself, under the dye-pen result. It is the same section in both places, backed by the same
choice: tick a liner on the tab and it is already ticked in the menu, and the other way round.

### feat: the worn profile curves the way worn metal does

The traced liner surface — the drawn edge that dips through the measured diameters inside a wear
band — used to run straight from one reading to the next, so a worn area printed as a chain of
bevels with a sharp corner at every station. Shop photos of the real thing show a smooth, flowing
hollow, and that is what prints now: the transitions between depth stations curve.

The curve is fitted so it can never say more than the measurements do. It passes exactly through
every measured station, at exactly the depth that station drew before, and between two stations it
stays inside their two depths — it cannot bulge out past the surface just outside a hollow, and it
cannot dig deeper than anything that was actually measured. Unworn shaft between two bands stays
dead flat, and a band with no readings still draws its plain straight edges. Everything built on
the trace follows it: the band's grey fill, the surface outline, and the tinted area on screen all
bite together, on paper and in the on-screen wear detail alike.

### fix: wear strip ends, break gaps, and titles read the way the shaft actually is

Three corrections to the wear sheet's detail strips, all from printed sheets.

**A strip end only breaks where the shaft continues.** The S-curve break is a promise that there
is more shaft past the stub, and it was being drawn at every window end — including the end of the
shaft itself. Now the end style follows what is actually beyond the window edge: nothing beyond
draws no stub at all, just the component's own end face at full weight; a remainder that is
nothing but the threaded end draws that whole thread, flat-ended and hatched; and only a genuine
continuation keeps the break. This is the convention the on-screen wear detail already used, now
matched on paper.

**A compressed gap draws the shaft that is really there.** The outline used to jump straight to
the adjacent component's edge diameter, which made the component itself look like it had shifted,
and the break sat hard against that edge with no connecting shaft drawn at all. Each break now
stands off by a short lead-in of the gap's true outline, so the strip reads: component edge → a
bit of real shaft → break → the removed length → break → a bit of real shaft → next component.

**Titles belong to what is attached.** A compressed break means the components either side are
*not* adjacent, so a single joined "A + B — 110 FROM AFT S.E.T." title across one misread as a
continuous area. Each attached cluster now titles itself, centred under its own drawn span. And an
attached taper + liner prints no from-SET measurement: the strip's dimension rail is the measuring
surface, and a taper sitting at the shaft end needs no distance to explain where it is. A lone
liner or a lone body run keeps the anchor dimension, in exactly the placement it has always had.

### fix: a taper far from its liner now prints on its own strip

A taper used to be pulled into the nearest liner's detail strip no matter how far away it sat, the
distance between them drawn as a compressed break inside the shared window. On paper that read
badly: the window had to be wide enough for both, which pushed the liner well off the centre of its
cell, left the taper crowded up against it, and put the break almost touching the taper's large
end.

The taper–liner join slider now decides **attachment**, not just how the gap between them is drawn.
A taper within the set distance of its nearest liner shares that liner's strip, with the shaft
between them drawn true, exactly as before; a taper beyond it simply gets its own strip, centred in
its own cell like any other lone component and titled by name. So the slider reads the way it
sounds: how close a taper has to be before it counts as part of the liner's area — from touching
only, at zero, out to a foot.

### feat: coupling face — the end view, digitized

The shops hand-draw a coupling end view in the corner of a runout sheet: the coupling OD, the
pilot (register) bore with its keyseat, the pilot runout written inside it, and the note that it
was taken looking forward. That sketch now prints, on both the classic runout sheet and the
consolidated one, bottom-right in the band the "TIR's taken looking" line already occupied.

**The pilot runout is authored like any other reading.** "Pilot runout…" on the Runout tab opens
the same bubble editor a station opens — a value and a high-spot clock marker, both optional —
and the reading rides the existing readings list under a reserved id rather than any new field.
The value centres in the bore, the high spot straddles its rim, and a blank write-in draft draws
the whole face with the numbers left out, exactly as the bubbles behave.

**The keyseat points outward.** The key sits between shaft and hub, so the shaft's keyway is cut
into it while the coupling's is cut into the surrounding hub material: on the face it reads as a
small box standing on top of the bore, deliberately the opposite of the runout bubble's inward
slot. Bolt holes come from the coupler bolt slot's count and are rotated half a pitch off
12 o'clock, so no hole ever hides behind the keyseat; a shaft with no slots authored draws the
plain two-circle face the sketch shows at minimum.

**Elected per job, off by default** — not every inspection measures the coupling, and a document
saved before this existed reprints unchanged. The "Coupling face" checkbox appears in the PDF
options of both the Runout and the Output tab preview and again beside the pilot-runout button,
all three writing the one per-job field, so the two sheets can never disagree. Everything above
the face reserves against it rather than the TIR lane, so the shaft and its bubbles never run
down through it, and the TIR write-in rule stops short of the block instead of crossing it.

### feat: wear strip follow-ups — anchor dimensions everywhere, a settable join, and one-tap contents

Three on-device answers to the questions the strip-windows work left open.

**Every strip prints its anchor dimension.** Wear is measured from the S.E.T. or the liner edge,
so a taper or body strip needs the same "110 FROM AFT S.E.T." line a liner strip has always
carried — it used to print the bare component name. A strip with no liner now measures its own
span against the nearer S.E.T. (ties go AFT, the liner rule exactly) and right-aligns its title
when FWD-referenced, the same direction cue. Blank write-in drafts follow: the value becomes a
writing rule with "AFT / FWD" printed for circling, on every strip rather than only the liner
ones. Combined taper+liner strips still print the LINER's anchor, unchanged. One shared helper
(`wearStripAnchorForSpan` / `buildSpanAnchorLabel`) now backs the liner label, the span label,
and the title-alignment cue, so the number, the wording, and the alignment can never disagree —
and the liner path prints exactly what it always did, pinned by test.

**Taper–liner join is a slider.** How much bare shaft may sit between a taper and the liner
sharing its detail strip before the run compresses to an S-break was a fixed 3". It is now
`PdfPrefs.wearJoinGapMaxMm`, **0–12"** in 1/2" steps (10 mm steps in metric), default the
shipped 3" — in Settings → Drawing → "Taper–liner join" and in the wear preview's PDF Options,
one pref behind both, commit-on-release. At 0 any gap breaks; touching components still draw
joined at every setting, since they have no gap to compress. The threshold changes only how a
gap *draws* — which taper joins which liner is decided on true millimetres either way. Stored in
canonical mm and converted only for display, like every other length in the app.

**Components quick actions.** The Components list gains **Default (all liners)** / **All** /
**None**. Default is the important one: it clears the election so the sheet *follows the shaft*
again, and a liner added later gets a strip on its own — the recovery path for a document whose
shaft was built out after the contents were authored. Hand-ticking every box is not the same
thing: an authored list is a fixed set, and later components arrive unticked.

---

## 2026-08-14

### feat: wear detail strips keep their proportions, and the sheet's contents are elective

Two changes to the wear document, both from an on-device report.

**Proportional strips.** Every detail strip was scaled to fill its own grid cell, so a liner
less than half the length of its siblings printed just as wide as they did. All strips on a
sheet now share ONE mm→pt scale (`sharedWearStripWindowPtPerMm`, over the primitive
`sharedWearStripPtPerMm`): the largest scale that still fits
every strip inside its cell. A 22" liner draws half the width of a 44" one, and a shorter
strip centers in its cell's slack. The shared scale is capped but deliberately **not** floored
— flooring it would overflow the longest strip's cell — so one long component shrinks the page
together rather than lying about the others. The undercut document's strips reuse the same
layout function and are untouched (the shared scale arrives as an optional override).

**Elective contents.** The wear preview's PDF Options gains a **Components** section: a
"Complete shaft" checkbox for the whole-shaft profile band, then one checkbox per
strip-eligible component (liners, tapers, and bodies including bare-shaft runs), listed
AFT→FWD under the same titles the sheet prints. Unticking the complete shaft drops the profile
band — profile, OAL rail, on-profile wear bands and pits, liner names, direction reference —
and hands its height to the strips, leaving no gap behind; the header, the strips, and the
dye-pen row are unaffected.

The election is per document (`WearRecord.stripComponentIds`, additive/defaulted — it rides the
existing `wear_record` envelope field, no file-format bump) and defaults to exactly today's
sheet: profile plus every drawable liner. The first component tick materializes that default
before applying the change, so a liner added later never silently rewrites an authored sheet;
an id whose component has been edited away is skipped when the sheet draws, never pruned from
the file. Blank write-in drafts follow the same election and profile toggle — a draft blanks
values, never the drawing's shape.

**Taper, body, and combined strips.** An elected taper draws its trapezoid and an elected body
its rectangle, each with the neighbor stubs, pits, and measured-Ø callouts a liner strip has
always had. An elected taper whose nearest elected liner is also on the sheet joins that liner's
strip instead of taking its own: drawn contiguously with the real shaft outline between them when
they are within 3", or joined by an S-break connector when they are further apart. Bodies always
get their own strip. A strip is now a *window* onto the shaft — components and the gaps between
them, all drawn through one piecewise mm→pt mapping — so a liner-only sheet, which is one
single-component window per liner, prints exactly as it always did.

Titles follow: `"AFT Taper + AFT Liner — 110 FROM AFT S.E.T."` for a combined strip, the plain
component name for a taper or body strip, and the unchanged `"AFT Liner — 110 FROM AFT S.E.T."`
for a liner's. A body or taper measured-Ø reading now prints IN its own strip when it has one,
at the zoomed scale, and only keeps its under-the-main-profile placement when it doesn't.

### fix: pit X marks sized to the hand convention — old Small is the new Large (overlay only)

The wear-inspection overlay drew its pit "X"s oversized (on-device report: "these are my
small x marks — these should be the large, and the small proportionally smaller"). The
overlay's base size is halved, so a Large X now lands exactly where Small used to and Small
sits proportionally under it. Printed output is untouched: the wear sheet, detail strips,
and consolidated sheet were confirmed right on paper and keep their exact sizes and stroke.
(An initial over-correction also halved the printed marks; it was reverted the same day —
only intermediate builds between the two commits carried it.)

### feat: the wear preview's PDF options tune the sheet being looked at

The wear document's PDF Options carried only line thickness, fractions, and shading —
none of the wear-specific dials (on-device report). The sheet now hosts, top to bottom:
a Blank-draft switch (the same state as the tab's toggle, so the two always agree — the
undercut preview gains the same row), Line thickness, the Trace depth exaggeration row
(the Wear tab's own control, one shared construction with its "Save as default" button),
and a new **Wear area shade** slider.

Wear area shade makes the detail strips' grey wash user-set (`PdfPrefs.wearBandShadeFrac`,
5–35% of full black, also in Settings → Drawing): the default reproduces the shipped look
exactly, and the cap is deliberate — the band is where pit X's land, printed and
hand-drawn, and a heavier fill would bury them, which is why the diagonal hatch was
retired. The main profile's vertical-stroke bands are a different mark and stay fixed.

The wear sheet's "Dye pen inspection: PASS □ FAIL □" row was hand-marking only. The Wear
tab now carries Pass/Fail chips: selecting one prints an "X" inside that checkbox, the
other box stays present and blank, and tapping the selected chip again deselects it —
returning both boxes to blank exactly as before (which is also what every blank write-in
draft prints, unchanged). The selection saves with the document.

### feat: tapers draw more proportional; body runs give a little more compression

On the consolidated sheet a short taper drew barely wider than the S-breaks beside it
(on-device request: "sacrifice a little more of the body compression to make the tapers
more proportional — liners get the most proportionality but tapers are important too").
The taper fraction-of-true floor rises from 50% to 70% of true width and the body-run
fraction eases from 35% to 30% — within the shared λ pool, width flows in proportion to
the fractions, so tapers now out-prioritize body runs on every squeezed sheet
(schematic, runout, and consolidated all share the constants).

Nothing structural changes: both floors stay ratio-preserving, so unequal tapers still
never equalize and body-run relative lengths still read; liner behavior, keyway pins,
and the drawn height are untouched.

### feat: adjustable worn-profile trace depth (5–25%), per job + Settings default

The trace shipped with a fixed 25% exaggeration cap; the on-device verdict was "25% should
be our high end" — so it's now a dial. The Wear tab gets a "Trace depth exaggeration"
slider (5–25%, 1% steps) that pins the value for the open document, with a **Save as
default** button that promotes the current value to Settings → Drawing → "Wear depth
exaggeration" and un-pins the document in one tap — the job then follows the default it
just created. A document that never touches its slider always follows the Settings
default; a touched one keeps its chosen look forever (the value rides the wear record in
the saved file).

One shared slider construction serves both rows, one pure resolver
(`effectiveWearTraceDepthFrac`) decides the drawn value everywhere — the detail overlay,
the wear PDF, and the Output tab's Export All can never disagree. The safety rule is
unchanged at every setting: the trace never draws shallower than true scale, and printed
Ø values never move.

### feat: wear document field-use fixes + the worn-profile trace

Four changes from a day of real shop use (on-device reports):

- **The worn-profile trace.** A liner measured almost half an inch down still printed as a
  perfect cylinder — the measured diameters never reached the drawing. Now, inside a wear
  area, the liner's surface line dips through the measured Ø stations (both edges, mirrored)
  in the detail strip and the on-screen detail overlay, and the band's fill follows the dip so
  the material measured away reads as white slivers. Depth is display-exaggerated the same way
  undercut notches are — normalized to the record's deepest liner reading, never drawn
  shallower than true scale. Pure math in `geom/WearTraceMath.kt`, one construction for both
  draw sites. Draw-only: no model, codec, or printed-value change.
- **Wear areas fill light grey, not diagonal hatch.** The detail strips are where pits get
  marked — by the printed X's and by pen on the printed sheet — and the diagonal hatch buried
  both. The on-screen overlay's red band likewise drops its diagonals for a flat tint. The
  main profile's vertical-stroke bands (the hand-marking convention) are unchanged.
- **Rail values sit above the rail.** A label too wide for its short span used to fall back
  to rows *below* the rail line — straight across the witness lines. Fallback rows now stack
  above the rail, matching the schematic's dimension rails. Undercut strips already did this
  and are untouched.
- **The wear screen stays put while adding Ø readings.** Adding measured diameters kept
  scrolling the screen back down to the wear-spot card: the card's field silently kept focus,
  and every dialog dismissal handed it back, IME and all. Canvas taps and the Ø dialog now
  release focus, so the view stays on the drawing.

### feat: station counts go to 0 — skip the bubbles on components not being measured

The runout station editor's − button stopped at 1, so every body, taper, and liner always
carried at least one bubble whether or not it was going to be measured (on-device request:
"I need to be able to set bubbles to 0 on any section in case I'm not measuring them").

A count of 0 is now a legal override: the component draws no bubbles on the Runout tab's
canvas, the classic runout sheet, or the consolidated sheet, and the rest of the shaft's
bubbles are untouched. TIR readings already recorded on a zeroed component are kept in the
document, just not drawn — the same render-layer orphan rule as everywhere else — so
raising the count brings them back. Works from both hosts of the station editor (Runout
tab and Consolidated Output tab).

### feat: offer a one-tap rename after quick-save when job info suggests a new name

The Save screen suggests a filename from Job # / Customer / Vessel at the first save, but the
toolbar Save on an already-named document silently reuses the existing name. A shaft saved
before its job information was filled in therefore kept a stale date-based name — "Shaft_2026…"
— with nothing to prompt otherwise (on-device report).

The editor's quick-save now compares the saved name against the same suggestion the Save screen
would make, and when they differ it offers the change in a snackbar: "Saved. Rename to ‘…’?"
with a one-tap **Rename**. Taking it renames the file and updates the document name, so the
title strip on every tab follows immediately.

It stays out of the way by design. The offer never overwrites — a name already taken by another
save is skipped silently — and each from→to pair is offered at most once per editor session, so
declining it once ends it until the job information actually changes. The unsaved-changes
prompt's Save is left alone: the user is on their way to another document there, and a snackbar
would outlive the screen it came from.

### fix: post-merge cleanup of the fraction typography system

Four small items from a same-day review of the merged fraction work:

- The Wear and Undercut PDF previews now re-render on a fraction-style change. Their
  rasterizing effects key on `pdfFractionStyle` like every other preview's render-inputs
  record — without the key, the style reaches the ink through `FractionTypography.active`
  (not snapshot state) and an open preview kept drawing the old construction until an
  unrelated input changed. Exports were never affected; they compose fresh.
- The wear document's liner strip anchor title — the one label on paper that still printed
  its fraction inline — now goes through `ellipsizeToWidth(rich = true)` + `drawRichText`,
  matching the identical construct in the undercut composer.
- `printedTaperRate` spells `3/4"/ft` plain instead of embedding a literal `¾`. The glyph
  rendered correctly (the parser normalizes vulgar fractions on the way in), but it was a
  second producer of Unicode fractions outside `FractionText.kt`'s parse map — against the
  "one spelling in, one look out" contract. `TaperRatePrintNotationTest` now pins that no
  printed rate carries a vulgar glyph.
- Comment/doc corrections: the renderer's header now states the real digit scale (0.64),
  and the Diagonal preset drops an inert `sideBearingFrac` override — side bearing is a
  stacked-only metric, so the tighter value never applied. `FractionTypography.md` §3/§5
  updated to match (diagonal takes the tighter gap and no side bearing; the wear composer's
  site list includes its strip anchor titles).

### feat: shipped drawing defaults move to Diagonal fractions and Small arrowheads

Both on on-device verdicts after using them on real sheets: diagonal "better for readability on
screen and outputs", Small arrowheads "do the job well with minimal space taking". Neither
changes an existing install — both prefs are persisted per device, so this only decides what a
fresh one starts with.

They also compose in the right direction. Diagonal costs ~3.6 pt more width than stacked and so
seats inline slightly less often; a Small head is 2 pt shorter than Medium and a break's stubs
must each be at least `arrowSize` long, so the new arrow default hands most of that back.

`FractionStyle.Default` is now the single source for the fraction choice — `PdfPrefs`'s default,
`FractionTextStyle.Default` and `fromName`'s fallback all read it, so a fresh install, an
unreadable stored value and the renderer's baseline cannot disagree. `FractionStyleSettingTest`
pins exactly that, and the flip caught its own regression: a test alias reading
`FractionTextStyle.Default` for "the stacked preset" silently became diagonal.

### feat: one built-up fraction construction everywhere the app draws one

`LengthFormat` reached for a Unicode vulgar glyph when the font had one (`½ ¼ ¾ ⅛ ⅜ ⅝ ⅞`) and
typed the rest out, so a single sheet could print a proper `⅝` beside a bare `11/16` — two
constructions at two different weights and two different heights (on-device report).

Fractions are now **set**, not spelled. `util/FractionText.kt` splits a label into plain and
fraction runs; `util/FractionTextRenderer.kt` draws the fraction as a real stack — numerator
over denominator, bar on the math axis — through one pair of extensions, `Paint.measureRichText`
and `Canvas.drawRichText`. `formatInchesSmart` emits plain `n/d` for every denominator and no
Unicode fraction at all: one spelling in, one look out.

Digits set at 0.64 of the base size, which is the largest that still keeps the stack inside the
base font's own ascent and descent — so no vertical layout budget in the app changed. Width
moves down, not up (a stack is one digit column wide), which is why the measure and draw calls
had to convert together at every site; a rail that reserved the inline width would leave its
value off-centre in the break.

Wired into the schematic rails, the schematic footer's spec lines, the runout/consolidated OAL
rail, the wear and undercut strip rails and titles, and the on-screen wear and undercut sheets —
so a preview and its paper construct a fraction identically. Diameters are untouched (decimal by
shop convention), and so is editable text: the input fields still show plain `1 1/2`, which is
what round-trips through `parseFractionOrDecimal`.

Deliberately not converted: the footer's free-text job fields. A job number like `24/1138` is a
valid fraction by every rule a parser can see, so the caller decides rather than the guard —
though the guard alone already leaves dates (`12/25/2026`), decimals (`1.5/2`) and ratios
(`1:12`) as plain text.

See `docs/FractionTypography.md`.

### feat: Settings → Drawing → "Fractions" picks the construction

Stacked / Diagonal / Plain, a new `PdfPrefs.fractionStyle` — so the two constructions can be
compared on a real sheet instead of by editing a constant. The same `FractionStyleChips` picker
appears in both PDF options sheets, **ungated** unlike the arrowhead size: every document those
sheets serve prints lengths, so every one of them draws fractions.

It is the only control in that section that also restyles the **on-screen** sheets, because the
previews and the exports share one renderer. Plain restores flat `n/d` and is the escape hatch.

The style reaches the ink through `FractionTypography.active`, a process-wide `@Volatile` mirror
written only by `SettingsStore.updatePdfPrefs` — the `SettingsStore.pdfPrefs` pattern, chosen
for the reason recorded when that one was reviewed: threading a uniform, app-wide drawing
decision through every composer's private draw functions costs more than it buys. Because the
mirror is not snapshot state, each preview's render-inputs record carries `fractionStyle` as a
re-render key and never as a composer argument.

`FractionStyleSettingTest` covers the whole chain — pref → mirror → an unqualified
`measureRichText` — and restores the shipped style in `@After`, since a process-wide mirror left
moved would have the next test class drawing in the wrong style.

### fix: a dimension value that fitted its rail no longer prints above it

On-device report, with the value visibly narrower than the gap it was refused. The break costs
`labelWidth + 2·textPad + 2·arrowSize`, and `PdfDimensionRenderer` carried its own `textPad` of
6 pt — wider on each side than a 16 pt value is across. It now uses the shared
`DIM_BREAK_TEXT_PAD_PT` (4 pt), the gap the wear and undercut strip rails already cut, so one
convention serves every rail in the app. For `21 5/16"` at the schematic's 7 pt rail size the
span requirement drops 36.6 pt → 32.3 pt.

Fraction spacing is now **per-style** (`FractionTextStyle.Stacked` / `.Diagonal` / `.Plain`, via
`forStyle`) rather than one shared set of numbers, which read well diagonal and slightly off
stacked (on-device report). The cause is structural: stacked leads with the BAR, a horizontal
stroke reaching the box edge at mid-height that binds to the preceding digit the way a hyphen
would, and closes on a numerator row that a trailing `"` attaches itself to; diagonal leads with
the numerator's own glyph at cap height and closes on the baseline. Stacked takes the looser
gap and bearing (0.14 / 0.045), diagonal the tighter (0.10 / 0.025) — which also suits diagonal
being the wider construction. Do not `copy(style = …)` one preset onto another style.

Diagonal costs ~3.6 pt more width than stacked and so seats inline slightly less often;
"Dimension arrows" at Small (3 pt) returns 2 pt of that.

### feat: Runout tab pins its preview and leads with the export controls

The preview scrolled away with everything else, so changing a component's station count —
the one edit whose whole point is watching the bubbles move — meant editing blind
(on-device request). The canvas and its tap hint now sit outside the scroll region, pinned
between the toolbar and the controls; the scrolling column takes the remainder.

The order below it is inverted to match how the tab is actually used. TIR orientation and
then the full export group (blank-draft toggle, Preview, Print, Export) come first, so
producing a sheet needs no scrolling at all. The measurement-station editor moves to the
bottom, behind a divider — it is reached only when the document needs adjusting, and it is
the one section whose length grows with the shaft.

### feat: the document title (and Save) now appear on every editor tab

Only the Schematic told you which document was open and whether it had unsaved changes.
Change the station count on a bubble, drop a wear pit, or add an undercut and the work was
just as unsaved — but the tab you were looking at said nothing about it (on-device report).

The dirty flag itself was already correct: `ShaftViewModel.hasUnsavedChanges` compares a
full session snapshot that includes the runout config and readings, the wear record, and
the undercut record. It simply had one render site. The strip is now the shared
`EditorDocumentTitle` composable, drawn above the toolbar on the Runout, Wear, Undercut,
and Consolidated Output tabs as well.

Each of those four tabs also gains a **Save** icon at the trailing edge of its toolbar,
reusing the Schematic's `onSave` (quick-save when named, the save-name screen when not).
Without it the asterisk would report unsaved work on a tab with nowhere to act on it —
Save lived only in the Schematic toolbar and was not in the sidebar either.

### fix: dimension arrows stop turning outward on spans with room to spare

On-device report from a blank (write-in) inspection sheet: rails printed their arrowheads
**outward** — the cramped-span convention, tips hanging past the extension lines — on spans
that were nowhere near cramped, and two adjacent spans' heads crossed at their shared
boundary into an X.

Root cause: arrow direction was tied to where the *value* landed. A span too short to seat
its value in a break in the line falls back to a continuous line with the value floating
above it, and that fallback also flipped its arrows out. On a blank draft the reserved
write-in gap is a fixed 60 pt, so whole rails fall back for a reason that has nothing to do
with whether two arrowheads fit.

Direction now comes from the **span's own width** (`DimensionRailLayout.arrowsPointInward`):
heads turn outward only when they cannot fit between the extension lines at all. A fallback
value overhead no longer costs a span its inward arrows. Same split applied to the
wear/undercut strip rails, where `seatsInBreak` (the break) and `arrowInward` (the heads) had
been one flag — the undercut rail's fallback-row reservation follows `seatsInBreak`, as its
contract already said it did.

### fix: short blank-draft spans get a smaller write-in gap instead of none

The 60 pt write-in gap was all-or-nothing: a span that couldn't host it fell back to a plain
continuous line, so some rails on a write-in sheet printed with nowhere to write the value
(on-device report). A short span now cuts whatever gap it affords, down to
`BLANK_DIM_GAP_MIN_PT` (28 pt, about a cramped `19 1/2`); only a span too tight for even that
keeps the continuous-line fallback, where a gap too small to write in would just read as a
printed break. One `labelWidth(span)` feeds both the planner's reserved box and the cut gap,
so the two can't disagree.

### feat: dimension arrowhead size — Small / Medium / Large

Arrowheads are also **slimmer and shorter**: a 2:1 V (barb spread half the head's length,
matching the wear/undercut strip rails) at 4 pt instead of a 1:1 V at 5 pt, which read as a
fat blob at sheet scale. The size is a preference — `PdfPrefs.arrowSizePt`, **Small 3 /
Medium 4 (default) / Large 5 pt** (Large is the historical head) — in both PDF options
sheets and Settings → Drawing → "Dimension arrows". It feeds the schematic and consolidated
sheets' dimension rails; the wear/undercut strip rails keep their own fixed head.

### fix: blank-draft footer columns split the band evenly

The FWD column got noticeably less writing room than the other two: the footer weights its
band 40 / 36 / 24 toward the left and middle columns, where a printed sheet's long free text
lives (taper specs, customer and vessel names). A blank draft prints no values at all — every
line is a rule running to its column edge — so that weighting just reads as a short FWD rule.
Blank drafts now split the band into equal thirds, with the last column padded like the
others so all three rules come out the same length. Printed footers keep the old weighting.

### fix: blank-draft footer job info lines up with the taper columns

On a blank (write-in) schematic the footer's middle job-info block started its writing rules
one line above the end columns' rules — the AFT/FWD columns spend their first line on a
"AFT Taper"/"FWD Taper" heading, and the middle block has no heading of its own. Customer /
Vessel / Job # / Date / Side now start one line down, so every rule on the sheet shares a
baseline with its neighbours. The blank pitch's fit-clamp counts the extra line, so a full
sheet still tightens inside the reserved band instead of running past the margin. Printed
footers are unchanged: their lines carry values rather than rules, and their band has no
spare line.

### feat: Save/Cancel on the Project Information sheet

On-device report: whatever field was typed into last lost its text unless the field was
clicked out of first — the sheet's fields committed on blur, and closing the sheet straight
from the keyboard never produced a blur commit. The sheet now edits a **local draft** and
commits from an explicit **Save** button, which pushes only the fields that differ from the
document (opening and saving without an edit does not mark it dirty). **Cancel** drops the
draft: a changed field reverts to what the document held, and a field that started blank goes
back to blank. The Shaft Position dropdown is part of the same draft.

Dismissing the sheet *implicitly* — swipe down, scrim tap, back — with a pending edit raises
a **"Save changes?"** prompt offering **Save · Discard · Keep editing**. Three choices rather
than the Material two, because an accidental swipe has two plausible intents: meant to close
it (so save it) or fat-fingered it (so put me back). A clean draft closes silently, and the
explicit **Cancel** button is never guarded — confirming a deliberate discard is the same
decision asked twice. The prompt blocks the sheet from settling closed rather than reacting
after it has animated away, so "Keep editing" leaves the sheet exactly where it was with the
draft intact.

The per-field `CommitTextField` (blur/Done commit) is gone, replaced by a plain
`DraftTextField` whose text lives in the sheet's state. Component-card numeric fields are
unchanged — they keep commit-on-blur, since each one drives geometry on its own.

## 2026-08-13

### feat: auto-body sections can carry individual diameters

On-device request: a shaft in the shop had a couple of bare-shaft sections at slightly
different diameters, and pinning one meant promoting the span to an explicit body. The
auto-body card's Ø field now commits a **per-section** override instead of the shaft-wide
value: `ShaftSpec.autoDiaOverrides`, a list of shaft-space `AutoDiaOverride(anchorMm, diaMm)`
entries (anchor placed at the span midpoint on commit, Ø stored verbatim). An auto span
draws at the aft-most override anchored inside it, falling back to the legacy shaft-wide
`autoBodyDiaMm` and then neighbor derivation — so existing documents render unchanged.

Deleting the separator between two differing sections merges them at the **more aftward**
section's Ø (aft is authored first); the forward override lies dormant rather than being
pruned — no orphans by construction, the runout-readings posture — so re-adding a component
there resurrects it exactly as typed. Clearing the field drops that section's override only.
Overrides never move span boundaries, and a gap absorbed into an adjacent explicit-body run
keeps the explicit Ø. "Show bare-shaft Ø on drawing" stays one flag for all auto spans;
differing sections print as separate below-shaft callouts since grouping is by value. The
field rides `ShaftSpec` additively (no codec change; old docs decode to an empty list), and
the shaft-wide setter is gone from the UI path (`setAutoSectionDiaMm` replaces it).

### chore: relicense to MIT

Replaces the proprietary "public for viewing and reference only" terms (`3be8d39`) with the
**MIT License**. ShaftSchematic is used as a worked example in instructional material, and
under the old terms a reader had no right to build on what they were being shown; MIT grants
that outright — use, modification, redistribution, and commercial use, with no separate
permission needed.

The carve-outs that matter are stated as scope notes at the bottom of `LICENSE`:

- **The top-level `assets/` reference photographs are NOT licensed.** They are photographs
  of shop hand-drawings kept for development reference; all rights reserved, no
  redistribution. `app/src/main/assets/` **is** covered — that directory is the app's own
  sample shafts and starter templates, not the photographs.
- **Name and branding reserved** — the license grants no rights to the "ShaftSchematic"
  name or icon; forks should ship under their own branding.
- **Engineering disclaimer**, alongside the license's warranty disclaimer: this is a
  drafting aid, it certifies nothing, and every dimension remains the responsibility of the
  engineer or machinist who reviews it.

`README.md`'s License section rewritten to match.

Note the direction of travel: a permissive grant is effectively one-way. Future versions can
be released under different terms at any time, but rights already granted on a published
commit cannot be withdrawn from anyone who took a copy under them.

---

## 2026-08-12

### feat: consolidated-sheet preview options match the schematic preview's applicable set

The Consolidated Output tab's PDF preview options sheet (`RunoutWearOptionsSheet`) now
also hosts Blank draft (write-in), Shaft height, Liner compression, and Measurement
reference — the same controls the schematic preview's Tune sheet exposes, minus Component
labels and the blank Ø-callouts sub-toggle (the consolidated composer never reads those
two prefs, so they'd sit there inert). All four are adjustable while watching the open
preview reshape; Shaft height and Liner compression previously required leaving the
preview to reach the tab's own sliders, and Measurement-reference changes now also
re-render the open preview (folded into the render loop's `ConsolidatedRenderInputs` so a
tiering change is no longer silently invisible until the next full re-open). The Wear,
Undercut, and Runout tabs keep their existing, unchanged sheet.

### feat: body Ø callouts are now opt-in (default off)

`Body.showDiaOnDrawing` and `ShaftSpec.showAutoBodyDia` default **false**: the schematic
prints a body's below-shaft Ø callout only when its card's "Show Ø on drawing" switch is
turned on (on-device request — the diameter is wanted only sometimes, and the footer's
"Body:" list always carries every Ø regardless). Liner OD callouts keep their default-on
posture. Documents saved with the switch already set keep their setting verbatim; documents
that never touched it — including everything authored before the switch existed — now print
no body Ø callouts until one is switched on.

### fix: Consolidated Output tab ordered around its outputs

On-device report: the runout-bubble rows at the top buried what the tab is for, and the
output buttons sat at the bottom. The tab now reads: **Sheet content election → the output
group (blank-draft toggle + Preview / Print / Export, kept together) → tuning** (runout
bubbles, sliders, worn sections) **→ Export all**. The per-component station rows sit behind
a collapsed-by-default "Runout bubbles" expander with the "Runout sheet →" button beside it
— counts are an occasional tweak, not the tab's headline.

### fix: taper footers drop the (AFT)/(FWD) suffix on L.E.T. / S.E.T.

The taper spec boxes printed `L.E.T. (AFT):` / `S.E.T. (FWD):`. On-device report: beside an
AFT taper, "(FWD)" read as if the line were asking about the FWD taper. The footer column
already names which end of the shaft the box describes, and which face is the small end is
shop knowledge — the suffix only confused. Labels are now bare `L.E.T.:` / `S.E.T.:` on every
footer on every page (one shared `buildFooterEndColumns` feeds them all; blank drafts
included). The large end is still always the L.E.T. whichever physical face carries it.

### fix: blank-draft write-in lines sized for handwriting

On-device report from a consolidated blank sheet: the fill-in rules neither spaced out nor
ran long enough to write on. Three changes, all in the shared blank-form helpers so every
blank document moves together:

- **Footer rules run to the column edge** instead of a fixed ~1" (`drawFooter`, shared by
  the schematic and consolidated footers) — room for a real customer name or diameter.
- **Footer lines open to a handwriting pitch** (`FOOTER_LINE_FACTOR_BLANK` 1.8 → 2.2, band
  `FOOTER_BLOCK_BLANK_PT` 150 → 200 pt), with the pitch fit-clamped to the band so the
  fullest column (taper + spooned note + thread) tightens instead of overrunning the page.
- **Dimension write-in gaps widen** (`BLANK_DIM_GAP_PT` 46 → 60 pt) on every sheet that
  cuts them; spans too short for the wider gap keep the existing continuous-line fallback.

### fix: review findings on the templates / Ø-toggle / station-interval branch

Fixes from the review of the 2026-08-11 work, all behavior-preserving for existing documents:

- **The `station_interval_version` stamp moved into `encodeV1` itself.** The field's decode
  default is 0 (legacy), so a future writer that forgot to pass it would compile cleanly while
  producing files that reopen with counts pinned to the old defaults and trailing readings
  orphaned. The encoder now stamps every write unconditionally; no caller can forget.
- **Start screen scrolls.** The home column (title, drafts, recents, five buttons) overran a
  small phone's height with no way to reach Settings; centered layout is unchanged when the
  content fits.
- **"Save as template…" confirms before replacing** an existing template of the same name
  (case-insensitive), matching the document save screen. It previously overwrote silently.
- **Document/template names are single path segments.** `normalizeShaftDocName` collapses
  `/` and `\` — a name like `../shafts/Job 1` could previously resolve outside the store's
  directory and silently overwrite a saved job document.
- **Starter templates' taper rates match their geometry.** The placeholder assets labeled
  tapers `1:12`/`1:16` that were cut at 1:24, 1:15, and 1:13 — the first drawing from a
  starter printed a wrong rate and tripped the rate-mismatch warning. SET/LET/length now
  derive from the labeled rate (pinned by `StarterTemplateAssetsTest`); the geometry remains
  placeholder pending plan §7 Q14.
- **Template previews resolve like the editor.** `ShaftThumbnail` derived manual-OAL mode as
  `OAL > 0`, so a stored auto-OAL template previewed a leading auto-fill span that vanished
  on load; it now uses `applyTemplate`'s `> coverageEndMm + eps` predicate.
- **Template store hardening**: rename returns a typed result (a vanished source is no longer
  reported as "name already taken"; an unnormalizable name gets feedback instead of a silent
  no-op); "Use" re-decodes before applying so a file corrupted after the scan snackbars
  instead of crashing; the browser awaits the (idempotent) first-run seeder so a first open
  cannot race it into "No templates yet"; the seed one-shot flag is not set when every asset
  failed, so a fixed build can retry; a just-seeded name joins the collision set.
- **One span mapping everywhere**: the station editor's rows now derive from
  `runoutComponentSpans` (the same builder both draw sites use) instead of a parallel fold,
  so editor rows can never disagree with drawn bubbles about identity or eligibility.
- **Template size buckets divide in Double**, so an OD stored as Float mm on an exact
  half-inch boundary (3.5" = 88.9 mm) cannot round down a bucket.

Left deliberately unfixed, still awaiting a call: the inert keyway true-scale pin (plan §7
Q8 — confirmed inert in `ShaftPdfComposer` AND `RunoutPdfComposer`, with
`ShaftHeightSlider`'s estimate modeling the pin the composers drop), and whether user-typed
component labels should be scrubbed from templates.

---

## 2026-08-11

Spec: `docs/Templates_And_DiaVisibility_PLAN.md` (parts A1, A2, B, C, D, E). Open questions
in that doc's §7 are unanswered; every one was resolved with the recommendation stated there,
so each is a one-line change if the answer differs.

### feat: show/hide a component's Ø on the schematic

A per-card **"Show Ø on drawing"** switch on explicit-body, auto-body and liner cards
(`Body.showDiaOnDrawing`, `Liner.showDiaOnDrawing`, `ShaftSpec.showAutoBodyDia` — additive,
defaulted `true`, so no envelope version bump and pre-feature documents print exactly as
before). Draw-only: nothing in the model, resolve, OAL, collision or footer geometry moves,
and no stored diameter is ever rewritten.

From an on-device report: a body ran under fiberglass for most of its length with one bare
window that could be measured, and the Ø callout printed at the body's center — over the
fiberglass — implying a reading was taken where it could not have been. The filter is applied
**before** the group-by-Ø in `buildBodyOdCallouts`/`buildLinerOdCallouts`, so hiding one body
of a shared-Ø group does not delete the value: the anchor moves to the longest body of that Ø
that is still shown. The lookup strips fragment ids (`resolvedBodyBaseId`), so hiding a body a
liner has split hides every one of its runs. Auto spans share one flag, matching the single
bare-shaft Ø. The footer's "Body:" Ø list is unchanged — the value is true for the shaft; it
was only the placement that misled.

Card-only, with no Add-dialog counterpart: the same carve-out the coupler slot's "Show
dimension rail" already has, for the same reason — it is a display choice made after seeing a
printed sheet, not a property of the component being added.

### feat: Ø callouts optional on blank write-in drafts

A **"Ø callouts"** sub-switch under the blank-draft toggle in the PDF options sheet
(`PdfExportOptions.blankDiaCallouts`, session-only like the blank toggle it sits under).
Off drops the whole callout pass — line, arrow and rule — leaving the shaft clear to annotate
freehand; a blank leader with nothing to write on is worse than no leader. One rule
(`PdfExportOptions.showDiaCallouts`) serves all four sites that build export options, so they
cannot disagree. Composes with the per-component toggle as an AND.

### fix: runout station counts follow length, and reach the printed sheet

**One station per 20 inches** (`defaultStationCount`, `RUNOUT_STATION_INTERVAL_MM`), replacing
the flat "3 per body whatever its length". Bodies take `ceil(length / 20")` (min 1), liners the
same floored at 2 (the edge-inset convention needs both ends), tapers stay at 2 (shop
convention). Capped at 10 per component. Overrides still win.

The reported symptom — three bubbles on a 1–2" segment — had a second cause: the count was
applied **per drawn run, not per component**. A body split by two liners drew 3 + 3 + 3 = 9
bubbles while its station-editor row said "3". Counts are now derived once per component and
apportioned across its runs by length (`apportionStations`, largest-remainder).

Two further defects, found while tracing that and fixed with it:

- **Count overrides and TIR values did not reach the printed runout sheet** for any body a
  liner had split. The live canvas keyed stations by the base body id while the PDF kept the
  resolved fragment id (`"<id>#2"`), so `overrides["X"]` missed and `readings.find("X", 1)`
  missed — the value stayed in the file and never printed. Both sites now build spans through
  one shared mapping, `ui/resolved/RunoutSpans.kt`. Invisible on unfragmented bodies, which is
  why it survived this long.
- **Fragments reused station indices**, each run restarting at 0, so one reading key identified
  several bubbles. Indices now run continuously AFT→FWD across a component's runs.

**Existing documents are protected**: at decode, any component that already carries a reading
and has no override gets its pre-interval count frozen into `componentOverrides`
(`ShaftDocCodec.freezeLegacyStationCounts`). A reading is keyed by station index, not by
position, so station 1 *of 3* is not where station 1 *of 5* is — changing a count slides a
measured value onto a spot it was not measured at, or off the end entirely. The golden rule
applies to a typed TIR exactly as it does to a typed diameter. The freeze is visible and
editable in the station editor, and documents with no readings pick up the new defaults.

The freeze is gated on a new envelope stamp, `station_interval_version` (additive, defaulted
0, no envelope version bump; writers stamp `CURRENT_STATION_INTERVAL_VERSION`). Without it the
freeze cannot tell a pre-interval document from one authored after — neither carries an
override — and would pin NEW documents to the OLD defaults: a 100" body drawn today defaults
to 5 stations, and reopening it would drop that to 3 and orphan the readings at 4 and 5.

### feat: bubble counts editable from the Consolidated Output tab

The station editor is now a shared composable (`RunoutStationEditor.kt`) hosted by both the
Runout tab and the Consolidated tab, plus a "Runout sheet →" button for the full authoring
surface. On-device report: the bubbles print on the consolidated sheet but changing one meant
leaving the tab. One composable, two hosts — the surfaces cannot drift.

### feat: shaft templates

**A template store** (`<filesDir>/templates/`, `io/TemplateStorage.kt`) holding ordinary
`.shaft` documents — no new format, no codec change. **Save → "Save as template…"** files the
current drawing there; the dialog shows the bucket it will land in.

A template carries **geometry only**. Job number, customer, vessel, position, notes and every
measurement record are stripped at WRITE time (`ShaftViewModel.exportTemplateJson`), not merely
ignored on load — a template that still held a customer name would carry it into every drawing
built from it, and into any copy of the file.

**A browser** (`TemplatesRoute.kt`, reached from "Start from Template" on the home screen):
collapsible sections by liner size (4"–12", plus "No liners" and "Other sizes"), then by liner
count, then cards with a drawn preview. Both grouping keys are **derived from each stored spec
on scan** (`template/TemplateBuckets.kt`) — no index file to fall out of sync, so an edited
template re-files itself. Empty buckets are hidden. Previews use `ShaftThumbnail`, which calls
the same `ShaftLayout.compute` → `ShaftRenderer.draw` pair as the editor with everything
interactive stripped; there is no second renderer.

Choosing a template routes through the same unsaved-changes guard as New/Open, then
`applyTemplate` loads it as a **new, unnamed, dirty** session: unnamed so the first Save
prompts (a template can never be overwritten by the drawing made from it), dirty because
`importJson`'s closing `markDocumentSaved()` would leave a loaded template counting as "no
unsaved work" — quitting would lose it, since the draft ring only protects a session it can
see as dirty.

Three bundled starter templates (4"/1 liner, 6"/2 liners, 8"/3 liners) seed once on first run
so the browser is not empty; deleting them is permanent. **Their geometry is placeholder** —
plausible proportions derived from the bundled sample shafts, pending the answer to the plan's
Q14 (which layouts are actually reached for).

---

## 2026-08-10

### refactor: KeywaySpan type + withBodyAt extraction (code-review findings)

Two of three external-review findings applied; the third (SettingsStore's in-memory
`PdfPrefs` mirror being a process-wide singleton) is deliberately left alone — converting
it to an injected dependency only pays off if DI is ever introduced, and threading it
through the layout + PDF composers by hand would cost more than it buys.

**`KeywaySpan` replaces `Pair<Float, Float>`** as the return type of
`Body.keywayAbsSpanMm()` / `Taper.keywayAbsSpanMm()` (`model/KeywaySpan.kt`): named
`loMm`/`hiMm` edges plus a `centerMm` accessor that absorbs the `(lo + hi) * 0.5f`
duplicated at the clocking-datum and PDF-footer call sites. Destructuring call sites
(`val (lo, hi) = …`) are unchanged — the data class destructures in the same order.

**`ShaftSpec.withBodyAt(index, startMm, lengthMm, diaMm)`** (`ShaftSpecExtensions.kt`)
extracts `ShaftViewModel.updateBody`'s inline list edit into a pure, tested model
function — same posture as `withKeyways180Apart`/`withNewOal`. The ViewModel method is
now a delegate plus its two side effects (`rememberBodyDefaults`, `ensureOverall`).
`WithBodyAtTest` pins the contract: out-of-range index returns the same instance,
length/Ø clamp to ≥ 0, start stays verbatim (golden rule), keyway fields survive.

### chore: proprietary license

The repository was public with no license file while `README.md` described it as
"private/closed" — the two did not describe the same repository, and with no license the
terms were left to be inferred. `LICENSE` now states them: copyright reserved, public for
viewing and reference only, no right to use/copy/modify/distribute without written
permission, and no rights implied by the platform's fork or clone affordances. Contribution
assignment, a third-party-components carve-out (Android SDK / AndroidX / Compose stay under
their own licenses), warranty disclaimer, and a drafting-aid liability note are included.
The README's License section now matches.

### feat(help): drawn figures under the topics

Four Canvas figures in the new `ui/screen/HelpIllustrations.kt`, wired through an optional
`HelpTopic.illustration` slot: AFT/FWD reference, plain-vs-spooned keyway, the S-break pair,
and true-vs-drawn undercut depth.

**Drawn from the production geometry, not captured.** The spoon figure calls
`keywaySpoonBowl` and the S-break figure calls `breakPairLayout` + `drawBreakEdge` (via
`nativeCanvas`, the route `drawWornSections` already uses to serve both a composer and a
canvas), so retuning `SPOON_BOWL_WIDTH_RATIO` or the glyph's control geometry moves the help
figure with it — a screenshot would have drifted silently. The two schematic figures
illustrate a convention rather than a glyph and own their layout; their doc comments say so.

**Paper, not chrome.** Figures depict printed output, so they draw fixed `SheetInk` on
forced-white sheets in every theme — dark theme's near-white `onSurface` would be invisible
ink on a white sheet. Only the frame and caption follow the app theme. Each figure exposes
its caption as `contentDescription`, and the topic body text still carries the full
explanation on its own.

### docs(help): four topics the screen never covered

A read-through against shipped behavior turned up features with no entry in Settings →
Help & FAQ. `HelpRoute.kt` gains:

- **"Undo and redo"** (Getting Started) — the toolbar History menu, the burst-coalescing
  rule (a typing burst is one undo step), and the fact that history is per-session and not
  saved with the file.
- **"Coupler bolt slots"** (How-To) — the Add dialog's controls in the order they appear
  (Measure From, distance to the first slot, hole Ø, count, conditional Spacing, Through
  hole → conditional Depth), the off-the-end bounds warning, the reference-feature posture,
  and the card's dimension-rail toggle.
- **"Why is Export PDF greyed out?"** (FAQ) — the two `exportPdfGate` conditions, why a
  slots-only spec doesn't count as having components, and why a body under a liner is
  never the cause.
- **"Record runout"** (expanded) — default station counts per kind, the one-inch edge
  inset and its reason, per-component overrides including zero, and the printed
  "TIR's taken looking AFT / FORWARD" line.

Content only — no new state, no ViewModel, no layout change to the screen.

---

## 2026-08-07

### fix(preview): the tuning strip shows the drawing, and the menu stops clear of it

On-device report against the tuning layout: the sheet still covered the bottom ~90 dp of
the page (diameter callouts and footer gone), and the strip spent its top third on blank
paper — "The shaft rendering has a LOT of white space on top and we're losing some of the
items under the shaft. Can we move it up a bit to clear some of that white space or perhaps
bring the menu down just a little more?" Both, as it turns out.

**The sheet's own chrome is now budgeted.** `heightIn` caps a bottom sheet's CONTENT
column; M3 stacks its drag handle (4 dp bar + 22 dp padding a side = 48 dp) and the sheet's
bottom window inset OUTSIDE that cap, so the real sheet stood ~48 dp plus a navigation bar
taller than the layout math believed and overlapped the page. `TUNING_SHEET_CHROME_DP` plus
the measured `WindowInsets.navigationBars` bottom now comes out of the budget at both
preview surfaces.

**The strip crops to the page's ink band.** A composed page rarely inks its full height —
the top margin plus unused rail room ran to about a third of the page. New
`util/PdfInkBounds.kt` measures the rendered bitmap's first and last inked rows (one row at
a time through `getPixels`, sampled every `height/200` rows and `width/256` pixels, ink =
any channel below 0xF0, padded 2.5% a side) and the strip is sized and drawn to that band.
Ink is never cropped — the OAL rail and the footer are ink, so they are inside the band by
construction; only paper is. The band is measured on **sharp passes only**, so a slider drag
never resizes the strip or the sheet under a moving finger.

The pure pair was restructured **strip first, cap derived from the strip**
(`tuningPageStripHeightDp` → `tuningSheetMaxHeightDp`), which retires the old circular
definition and guarantees the two can never disagree; a new `drawPageBand` in
`ui/screen/PreviewTuning.kt` is the ONE strip draw implementation for both sites (the
overlay swaps its `Image` for a `Canvas` in strip layout), and the sheet cap moved out of
`RunoutWearOptionsSheet` into `PdfPreviewOverlay`, which is the only thing that knows the
strip. On a 393 × 851 dp phone with a 48 dp navigation bar: a 303.7 dp strip over a 363.3 dp
sheet — with the chrome, exactly the screen. View-layer only: no composer, no preference,
no `RunoutConfig` touched. `PreviewTuningTest` and the new `PdfInkBoundsTest` pin the math.

### fix(preview): the page stays visible above the tune menu

On-device report against the live-tuning drop: "It may render live but the menu with the
sliders is in the way. I can see the PDF Preview area lighten up on moving a slider but I
can't see anything. I need to close the menu to see the changes." The direction — "the
preview rendering being allotted a space near the top… and the menu only reaching the
bottom of that preview."

**The page takes a strip on top while the sheet is open.** The exported sheets are
LANDSCAPE (792 × 612 pt), so a page fitted to a portrait screen's *width* needs only
`screenWidth × 612/792` of height — the whole drawing fits a strip near the top with room
to spare. Opening a tuning options sheet now switches the preview to that layout: the page
is redrawn fit-width and **top-aligned** under the app bar (from the real
`PDF_PAGE_WIDTH_PT`/`PDF_PAGE_HEIGHT_PT` constants, never a magic ratio), and the sheet is
capped at `tuningSheetMaxHeightDp` = screen height − strip − 88 dp of status bar and app
bar. On a 393 × 851 dp phone: a 303.7 dp strip over a 459.3 dp sheet — the two plus the
chrome are exactly the screen. The sheets already scroll internally, so nothing is lost.

**Short/wide screens: the sheet keeps its floor, the strip yields.** The cap is clamped to
40–78% of the screen height; when the fit-width page cannot also fit, the page fits to the
shrunken strip instead (`tuningPageStripHeightDp`). A cramped page is still readable and
zooms once the sheet closes; cramped sliders are not usable at all. All of it is pure and
unit-tested in `ui/screen/PreviewTuning.kt` / `PreviewTuningTest`.

**Zoom resets when the sheet opens** — deliberate, predictable over preserved: an
inspection zoom would push the strip off-screen exactly when the sliders need it in view.
Closing restores the normal layout (fill, pinch 0.5×–8×, double-tap reset).

**Scrim.** `ModalBottomSheet`'s scrim is one full-window rect — it covers the strip and
cannot be restricted to the gap below it — so a tuning sheet passes `Color.Transparent` for
the whole time it is open, not only during a drag. On the shared `PdfPreviewOverlay` the
black surround already separates strip from sheet; the schematic's `PdfPreviewScreen`
paints the strip-to-sheet gap itself and drops even that while a slider is being dragged.
Tap-outside-to-dismiss is unaffected. The overlay's `sheetScrimColor` parameter is replaced
by `sheetTunesPage: Boolean`, which drives layout and scrim together.

Applies to the three surfaces whose sheets tune the page — the schematic preview, the
classic runout sheet, and the Consolidated Output tab. The **Wear** and **Undercut**
previews keep the full-size centered page and the plain 78% sheet cap: their sheets tune
nothing, so there is nothing to watch. The live-tuning machinery itself (draft raster,
conflated render loop, commit-on-release persistence) is untouched — this is layout only.

### feat(preview): sliders tune the open preview live

On-device request: "see the differences without choosing, closing menu, opening menu,
choosing". The four tuning sliders — **Line thickness**, **Body S-break**, **Shaft
height**, **Liner compression** — now reshape the open preview **while the finger is still
on the track**, on all three preview surfaces: the schematic (`PdfPreviewScreen`), the
Consolidated Output tab, and the classic runout sheet. The options sheet also stops
dimming the drawing during a drag — the page above is the thing being judged, so
`scrimColor` goes transparent for the duration and the modal dimming returns on release.

**A drag is a visual-only channel.** Each shared control in `ui/screen/ShaftHeightSlider.kt`
gained an optional `onDrag: (Float?) -> Unit` — the in-progress value every frame in the
same units as its commit callback, `null` on release. The height slider converts drawn
inches → `heightScale` exactly as its commit does but **without** the standard-height
detent (snapping is a commit rule; applying it per frame would make the drawing jump under
the finger). The screens park the value in a `PreviewTuning` holder
(`ui/screen/PreviewTuning.kt`) and render `override ?: committed`. **No DataStore write and
no `RunoutConfig` update happens on a drag frame** — commit-on-release is untouched, so a
drag never persists a setting and never marks the job dirty. Callers that don't opt in
(Settings, the wear and undercut options sheets) keep the defaulted no-op.

**Conflated render loop.** Each screen's multi-key `LaunchedEffect` became
`snapshotFlow { RenderInputs(…) }.conflate().collect { … }` — latest-wins, so intermediate
drag values are dropped while a render is in flight and the newest always renders. The
`RenderInputs` data class captures everything the old key list covered plus the overrides
and a draft flag; the schematic's also picks up **`curveLoHeightIn`/`curveHiHeightIn`**,
which its key list omitted, so a Settings change to "Default drawing size" no longer leaves
an open preview at its old height.

**Draft res, then sharp.** `util/PdfRaster.renderPdfPageBitmap` gained
`renderScale: Int = PDF_PREVIEW_RENDER_SCALE`; the three screens pass 1 while a drag is
live (≈¼ the pixels) and the pass after the release restores the full 2×. The spinner is
held back across drag frames and that release pass so the page never strobes.

No geometry, composer, or persistence change — the drawing math is untouched; only which
values reach it, when, and at what raster.

## 2026-08-06

### feat(ui): Body S-break joins the PDF Options sheets

On-device request: "Can we add S breaks to pdf preview settings too?" The threshold was
reachable only from Settings → Drawing, which meant leaving the drawing to change the
setting that shapes it. It now also sits on the per-document **PDF Options** sheets —
directly under Line thickness, exactly the way Line thickness already lives in both
places — on the schematic preview (`PdfOptionsSheet`) and the runout / consolidated
previews (`RunoutWearOptionsSheet(showSBreak = true)`). The wear and undercut sheets,
which share that composable, leave `showSBreak` at its `false` default: their documents
never draw compression breaks, so the control would be inert there.

ONE shared `SBreakThresholdSlider` (`ui/screen/ShaftHeightSlider.kt`, beside
`LineThicknessSlider`) backs all three surfaces, with the "Never / below N%" formatter
living next to it; Settings → Drawing now renders that shared control plus its
explanatory caption instead of its own copy of the slider. One app-wide pref
(`PdfPrefs.sBreakThresholdFrac`) throughout, so a move on any surface is the same move —
and every preview already keys its re-render on `vm.pdfSBreakThresholdFrac`, so the
sheet redraws under the open options sheet. Pure UI wiring over a tested pref: no
geometry, persistence, or composer change.

### fix(settings): Default drawing size + Body S-break move up to the main Settings page

On-device report: "The S break is way too deep in settings, it's under pdf export. Default
body size drawing and S break both need to be in settings, not pdf export area." Both
controls define the **drawing itself** — how tall a shaft prints and when a foreshortened
body admits it — so burying them a page deep behind "PDF Export Options" filed them as
export plumbing. They now sit in a new **Drawing** section on the main Settings page,
directly under the PDF Export Options row and above Editor Screen, in their original order
("Default drawing size" then "Body S-break"). The PDF Export sub-page keeps what is
genuinely about exporting: open-after-export, component titles, Shade in PDF, Template
mode, and the dimension tiering reference. Pure relocation — same composables, same
prefs, same setters, no geometry or persistence change. Help's Settings Reference moves
both topics in with the main-page entries and every doc pointer retargets from
"Settings → PDF Export" to "Settings → Drawing".

### docs(help): Settings reference — every field and option covered

On-device direction: "Let's make sure all relevant docs are updated, including the help
section. It needs to be thorough covering settings fields and options." Help & FAQ gains a
**Settings Reference** section — one topic per Settings area (Units, Appearance, Editor
Screen, Preview Colors, Undercut Drawing style, the four PDF Export groups, Achievements,
Data, Help/About/Developer Options) naming every control by its on-screen label with what
it changes and its default, plus a closing topic for the per-job controls that live on a
document rather than in Settings (Shaft height, liner compression, blank draft, cut-depth
exaggeration). `Navigation.md` extends the "Help tracks behavior" rule to cover every
Settings control by name. Doc sweep alongside it fixed the stale pre-proportional sizing
anchors (4" → 0.75" / 8" → 1.25") still quoted in `GLOSSARY.md`, `ROADMAP.md`, `TODO.md`,
and `README.md` — the shipped standard is the proportional 4" → 0.5" / 8" → 1" pair — and
brought the glossary's S-break entry up to the new user-set threshold.

### fix(pdf): S-breaks only on real compression — user-adjustable threshold (default: half of true)

On-device report: a 6" body run between a taper and a liner carried a full S-break pair.
It was drawn at ~74% of true width — the old rule broke on ANY foreshortening beyond
1 pt of paper, so a barely-squeezed run got the same glyph as one hiding twenty feet of
shaft. New rule ("institute a compression with no break, and after a certain length add
the S break to indicate a longer span"): a body run prints a plain outline until the
compressed x-map squeezes it below a **threshold fraction of its true drawn width**,
half by default; below that, enough shaft is hidden that the pair must mark the longer
span. Bodies ONLY — liners/tapers still foreshorten silently, per the standing invariant.

The threshold is a **user preference**, not a fixed constant — "add a slider for body S
break compression setting? So if I want an S break at 25% or more, or 75%, to no break
and all hidden compression. Figured why lock it in one way when different users may want
different outputs." Settings → PDF Export → **"Body S-break"**: a slider in 5% steps
(commit on release) with a "Default (50%)" reset button, the same posture as Line
Thickness. The readout reads "Never" at the low end — compression breaks off entirely,
all foreshortening hidden — and "below N%" elsewhere; at 100% any foreshortening at all
breaks. Persisted as `PdfPrefs.sBreakThresholdFrac` (DataStore key
`pdf_sbreak_threshold_frac`, default `PDF_SBREAK_THRESHOLD_DEFAULT` = 0.5, clamped to
0–1), and every preview that rasterizes with the current prefs — schematic, runout,
consolidated output — keys its re-render on it, so the sheet updates live.

One predicate (`breakForCompression`, `pdf/BreakSymbol.kt`, unit-tested in
`BreakThresholdTest`) drives all three consumers — the schematic's
`drawBodiesCompressedCenterBreak`, the consolidated sheet's `drawBodiesForRunout`, and
the schematic footer's compression note — all reading the ONE pref, so the note and the
drawn breaks can never disagree. The classic long-span trigger (`COMPRESS_TRIGGER_PT`,
≥ 220 pt of paper) is deliberately **outside** the slider and unchanged: a run that eats
220 pt of paper at true scale is not hidden compression, so it still shows its break at
every setting, "Never" included. The rails print true lengths over every span either way;
the threshold trades a marker on mild compression for a quieter sheet. Sample numbers at
the 1" standard and the default 50%: a 6" run (true 54 pt) floored at 40 pt on the
schematic = 74% → plain; the 302" shaft's 29–31" runs at 25–40% of true → break, as before.

The "one regular tier bump" adjustment removed `OVERALL_EXTRA_PT` but missed the second
padding source: `PdfPrefs.oalSpacingFactor` (default 2.5, no UI control — permanently at
default for every user, catalogued as REFACTOR_CANDIDATES §5b) still added
`0.75 × railGap` above the schematic's OAL rail via `extraClearRails`. The on-device
one-tier rule settles that open decision: the pref is obsolete, so the field, DataStore
key/flow, ViewModel collector, and `setPdfOalSpacingFactor` are removed and the
schematic's OAL now truly rides `railGap × (maxRail + 1)`. The consolidated sheet was
already correct (it never read the pref). §5b marked resolved.

### fix(taper): S.E.T. lands on the face it faces — orientation keyed on the physical half

A taper added with the Add dialog's direction chip left on AFT (the default) but placed in
the FWD half of the shaft stored its diameters backwards: the dialog keyed its SET/LET swap
on the **measure-from chip**, while diameter derivation, the carousel's labels, the renderer
and the keyway's SET reference all key on the taper's **physical half** (midpoint ≤ OAL/2).
The two are independent choices — where you measured from says nothing about which half the
taper lands in. When they disagreed the taper drew small-end-inboard, the card printed
S.E.T. 7 / L.E.T. 6 against the typed 6 / 7, and a keyway "from SET" started at the far face
(`docs/TaperOrientation_Analysis_2026-07-26.md`, §4 Scenario A).

`AddTaperDialog`'s submit now orders the typed values with `taperAddDiameterOrder` over
`classifyTaperSideByMidpoint` (`ui/input/TaperSetLetMapping.kt`), which is also the rule
`ShaftViewModel.taperSmallEndAtStart` now delegates to instead of restating — one convention,
one implementation. The half is judged against `oalAfterTaperAddMm(…)`, the OAL the shaft
carries **after** the change: in auto-OAL mode the add itself grows the shaft (on a blank
shaft the pre-add OAL is 0), and classifying against the stale value put SET on the wrong
face and derived a rate-filled end for the wrong face (Scenario B). `addTaperAt` and
`updateTaper` both use it, and both now read the typed S.E.T./L.E.T. back out of the
x-ordered pair through that same half, so a FWD-half taper no longer seeds the next dialog's
SET default from a LET.

The Add dialog also passes its measure-from chip through to `Taper.authoredReference`
(`onSubmit → ShaftScreen → ShaftRoute → addTaperAt`), the same pass-through liners have had:
a taper added "measure from FWD" reopens in that frame instead of showing AFT with a
converted Start (Scenario C). The field is additive + defaulted — no envelope bump.

**Stored documents are not repaired.** A reversed pair written by an earlier build decodes
exactly as saved (golden rule); only newly added tapers get the corrected ordering. Pinned by
`TaperAddOrientationTest` (all four half × measure-from combinations, both the both-ends and
single-end-plus-rate variants, card labels and keyway face) and
`TaperAuthoredReferencePersistenceTest`. Contracts: `Model_Conventions.md` v0.5,
`AddComponentDialogs.md` v1.7, `ShaftViewModel.md` v0.8.

### chore(carousel): physical order accepted — the newest-first plumbing is gone

Product decision: the carousel's physical display order is correct and stays. `ComponentsOrdering.md`
v1.1 had locked a newest-on-top rule that the app stopped following when the resolved-component
pipeline landed (newest-on-top cannot coexist with auto-bodies interleaved at their spans), and
v1.2 recorded the drift as an open question. It is now closed, and the dangling machinery removed:
`ShaftViewModel._componentOrder`/`componentOrder`, `orderAdd`/`orderRemove`/`ensureOrderCoversSpec`
and their ~20 call sites, `EditState.componentOrder`, and the parameter threaded
`ShaftRoute → ShaftScreen → ComponentCarouselPager` (declared there, never read).

Undo/redo is unaffected: rows are derived from the spec, so restoring the spec restores them,
and every order mutation already accompanied a spec mutation. Dropping the flow took the
history collector from six flows to five, replacing the `Array<Any?>` `combine` overload — with
its unchecked casts and arity `check()` — with the typed one. Nothing persisted changes: the
document envelope never had a field for order, so every existing file opens exactly as before.

`ComponentKind` stays (cards, snackbars, test tags), as does `ComponentKey` — it feeds the
model-layer physical ordering helpers `ShaftSpec.buildPhysicalKeyOrder`/`snapForwardFrom`.
`BodySplitResult.removedIds`/`addedIds` also stay: no longer an order feed, but the record of
which stored bodies a split/merge replaced, asserted throughout `BodySplitMergeTest`. Two
order-only tests in `ShaftViewModelRemoveTest` were dropped and its delete+undo tests now
assert spec restoration. Contracts: `ComponentsOrdering.md` v1.3, `ShaftViewModel.md` v0.8,
`docs/ARCHITECTURE.md`, `docs/UI_CONTRACT.md` §4.2.

### ci: unit tests gate every distributed build — red blocks

`distribute.yml` now runs `testDebugUnitTest` before `assembleDebug`, so a red suite stops
the build and nothing reaches Firebase — only green builds distribute (on-device direction).
The workflow also triggers on `chore/**` and `fix/**` branches (previously only `main` and
`feat/**`), so review branches get testable builds too.

### refactor: one raster helper, one Shade-in-PDF block, and the dead long-body constants gone

Three behavior-preserving cleanups off `docs/REFACTOR_CANDIDATES.md` (§1, §4, §5). No
drawing, preference, or wording changes; 1153 tests green.

**One PDF→bitmap raster path** (§1). Every in-app preview composed its own temp PDF and
rasterized it — and there were **four** copies of that pipeline, not the two the catalog
recorded: `renderPdfPreviewBitmap` (PdfPreviewScreen), `renderPdfPageBitmap` (RunoutRoute,
also used by OutputRoute), `renderWearBitmap` (WearRoute), and `renderUndercutBitmap`
(UndercutRoute). All four are replaced by `util/PdfRaster.renderPdfPageBitmap(context,
composePage)`, which takes the same `composePage: (PdfDocument.Page) -> Unit` lambda as the
hardened SAF write path (`util/PdfSafExport.writeShaftPdfToUri`) — the raster sibling of that
one-write-path rule. The copies were behaviorally identical: same `ARGB_8888` bitmap, same
white `eraseColor`, same 2× scale (now the named `PDF_PREVIEW_RENDER_SCALE`), same 792 × 612
page (now `PDF_PAGE_WIDTH_PT`/`PDF_PAGE_HEIGHT_PT`), same `runCatching{}.getOrNull()` posture,
same close/delete order. They differed only in their temp-file prefix (now one
`"pdf_preview_"`) and in the schematic one taking typed compose arguments instead of a lambda.
The "must be called off the main thread" contract, documented on only one of the four, now
rides the shared helper. Known and unchanged: a `composePage` throw still leaves its temp file
in the cache — the delete lives in the raster stage's `finally` — recorded in
REFACTOR_CANDIDATES §1 as its own future change.

**Shared "Shade in PDF" block** (§4). The Bodies/Tapers/Liners checkbox trio existed three
times. It is now `ShadeInPdfChecks` in `ui/screen/ShaftHeightSlider.kt`, beside the shared
`LineThicknessSlider`, carrying the heading, the three rows, and the defaulted
`linerShadeLocked` display-only lock (disabled + shown unchecked + the "Ø values print inside
the profile on this sheet" caption; the stored pref is still never rewritten). Both PDF options
sheets use it, so the next option lands on both at once. **Settings → PDF Export stays
bespoke**: its rows sit in a `spacedBy(12.dp)` column under a padded heading, so adopting the
sheets' tighter block would have restyled that page — same prefs, same setters, and a comment
at the site records why.

**Long-body bubble-count constants deleted** (§5, product decision). `RunoutConfig`'s
`BODY_SHORT_THRESHOLD_MM` (914 mm) and `BODY_LONG_COUNT` were defined and documented but never
read — verified zero readers in main and test — and their KDoc promised a length-based default
the app never had. Default bubble counts stay **uniform**; the user raises the count per
component (`componentOverrides`) when a run wants extra readings. `BODY_DEFAULT_COUNT`'s KDoc
now says exactly that.

### fix(pdf): dimension labels clear each other — slide along the span, lift the next tier

On-device report, with screenshots of the consolidated sheet: two short spans on *different*
rails printed their values literally on top of each other (a 19½" taper span and a 21½" span
at overlapping x), and another value ("11 ½") was struck through by a neighbouring rail's
dimension line. The direction: "There is enough space to print the label along the span, and
there is enough vertical space to move the next tier up to clear the label as well, so please
make adjustments for the labels and spans to clear each other."

Root cause: `PdfDimensionRenderer` tracked placed labels **per rail** (`labelBoundsByRail`),
but a span too short to seat its value in the line break floats that value `textAboveDy`
(12 pt) above its own rail while rails are only `railDy` apart (18 pt on the consolidated
sheet) — so a floating label physically lives in the *next tier's* band, invisible to that
tier's collision list and vice versa. Rail **lines** were never obstacles at all, so a value
could be struck through by a neighbouring rail's line. The only escape was a bounded bump
**upward**, which pushed the label further into the tier above, and no mechanism existed to
give a tier extra room. Rails were drawn span-by-span, so the renderer never saw the full
set up front.

New rule: one collision space, planned up front by the pure `geom/DimensionRailLayout.kt`
(no Android imports, unit-tested — same posture as `DiameterCalloutLayout`). It places every
span at once, top OAL rail included, treating both placed labels and every rail **line** as
obstacles. A collision is resolved by **sliding the value horizontally along its own span**
first — the smallest shift from center that clears everything, inside
`[xa+textPad+half, xb−textPad−half]` and tightened by `arrowSize` on both sides for an inline
value so the break keeps its inward arrows — and only then by bumping a floating value
vertically. A rail carrying a floating value **lifts every rail above it** (OAL included) by
one label band, cumulative per intervening fallback rail, so lines clear values. Placement is
least-slide-room-first, so the wide span is the one that moves.

Inline-vs-fallback is decided from x-geometry alone, so the lifts are known before any
vertical budget: `ShaftPdfComposer` folds `topLift` into its `computeTopY` fit loop (shrink
rail gap, then text size, until the lifted block still clears the content top), and
`RunoutPdfComposer` adds it to `railsBlockH` off a prelim linear-map plan — the same
prelim-then-resolve posture the bubble budget already uses, since the real compressed x map
needs the budget the lift feeds. `PdfDimensionRenderer` keeps only the Canvas work
(`drawPlanned`); its internal collision/bump code is gone. Unchanged: the value-in-the-break
primary path, the `canFitInwardArrows` predicate, inward arrows on inline spans, and the
blank-draft write-in gap (planned on `blankLabelWidthPx`, so hand-write-in windows get the
same clearance). The classic runout sheet (`consolidated = false`) has no rails and is
untouched. Pinned by `DimensionRailLayoutTest`.

Follow-up from the same on-device review: the OAL rail now rides exactly **one regular
tier pitch** above the highest component tier on both composers ("it is the topmost
measurement, but it doesn't need such a large gap… make use of our whitespace more
efficiently") — the extra OAL padding constants (`OVERALL_EXTRA_PT` 16 pt schematic,
`RUNOUT_OAL_EXTRA_PT` 14 pt consolidated) are removed; the planner's lift is the only
thing that widens the gap, and only when the tier below floats a label into the lane.
The freed height goes back to the shaft on the consolidated sheet's budget.

### fix(pdf): liners shade again on the consolidated sheet — unless values print inside them

On-device report: the Consolidated Sheet Preview drew liners white even with Settings'
"Shade in PDF → Liners" checked. Root cause: the in-profile-value change hard-coded
`linerFill = null` in `RunoutPdfComposer` — the halo-legibility rule (a sheet-white knockout
over grey reads as a pasted box) was applied to *every* sheet, including the ones that print
no values inside the profile at all. New rule: liners follow `shadedLiners` like bodies and
tapers, and go unfilled only when in-profile Ø values actually print. One shared predicate
decides it — `consolidatedSheetHasInProfileValues` (wear info elected in, not a blank draft,
and at least one worn-section value > 0 or one valued reading keyed to a component that still
resolves; wear bands and pit X's are marks, not text, and never suppress the fill). The
composer builds `linerFill` from it, and the Consolidated Output tab's PDF options sheet
locks its "Liners" checkbox with the same call (`RunoutWearOptionsSheet(linerShadeLocked)`:
disabled, displayed unchecked, captioned "Ø values print inside the profile on this sheet") —
display-only, the stored pref is never rewritten, so the choice returns as soon as the sheet
stops printing values. The classic runout sheet and the Runout tab's live canvas carry no
in-profile text and now shade liners per the pref as well. Predicate pinned by
`ConsolidatedInProfileValuesTest`.

### feat(ui): line thickness gets a Default (100%) button and a magnetic detent

On-device report: "I tried messing with the line size and had some trouble landing on
100%." Same treatment as the shaft-height slider's Standard button: every line-thickness
surface now has a "Default (100%)" reset button, and slider commits within ±5% of 100%
snap to exactly 100% (`snappedLineThickness`). The two options-sheet copies (schematic
PDF preview, runout/wear sheets) are replaced by one shared `LineThicknessSlider`
(`ShaftHeightSlider.kt`); the Settings → Editor Screen control keeps its typed % field —
typed values are never snapped (golden rule) — but shares the button and detent.

### fix(pdf): balance — body runs join the λ pool; liners can't consume the page

On-device report: "The output needs more body… I can't tell that the span between the
aft and mid liner is longer… The liners are taking up way too much space. There has to
be some kind of balance." Root cause: the liner fraction-of-true raises λ-fit the page
to the brim, so by the time the proportional-distribution phase ran there was zero
slack left — every body run clamped to its flat `PROFILE_MIN_BODY_RUN_PT` floor and
drew the same width, erasing the relative lengths that make a shaft readable. The
liners hadn't taken *too much* by any single rule; they had simply taken it all first.
Fix: body gaps now carry a ratio-preserving fraction-of-true floor of their own —
`PROFILE_BODY_RUN_MIN_FRAC_OF_TRUE` = 0.35, threaded through `walkSpans` /
`buildCompressedProfileXMap` / `fracFitFactor` as `gapMinFracOfTrue` — sharing the
**single λ** with the liner and taper raises. Everything in the pool shrinks together
under one factor, so relative lengths always read within every kind: a 900 mm body run
draws 1.8× a 500 mm one exactly as two unequal tapers keep their ratio. The "the page
affords liners ~N% of true length" readout now settles lower than it did against fixed
gap floors — that lower number *is* the balance. Height precedence untouched:
`solveMaxProfileScale` stays frac-blind, so only a keyway pin still yields the drawn
shaft height.

### fix(pdf): standard drawn height finalized — 8" shaft draws 1" tall

On-device verdict on the latest build ("this 1.25 just looks bad… make the 8"
diameter a 1" height drawing"; 1.125" still read heavy). The standard sizing-curve
anchors are now **4" → 0.5" / 8" → 1.0"** — still proportional through the origin, and
exactly the rule from the original rulered hand sketches (8" → 1", 6" → 3/4",
4" → 1/2"). Two-value default change (`PdfPrefs.curveLo/HiHeightIn`, geom consts
36/72 pt); the anchors remain adjustable in Settings → "Default drawing size".

---

## 2026-08-05 (evening — default sizing curve, S-break gap, options-sheet scroll)

### feat(pdf): liner proportionality — per-job checkbox + compression slider

On-device request ("add a checkbox to keep liners proportional lengthwise… the key
components we are measuring are the tapers and liners"; "a liner compression slider
would be a helpful tool as well"). New per-job pair beside the "Shaft height" slider
(Consolidated Output tab + the schematic preview's Tune sheet, one `RunoutConfig` value
behind both, rides the `.shaft` envelope): **"Keep liners proportional lengthwise"**
pins liners at their true-scale drawn width — the drawn height yields when the page
can't fit them, the keyway-body posture — and the **"Liner compression" slider**
(0–100%, default 100% = historical behavior) bounds how far liners may foreshorten
below true scale when unchecked. Engine: `ProfileFeatureSpan.minWidthFracOfTrue`
(geom, unit-tested — a fraction-of-true width floor, monotone so both bisection solves
keep their contracts); composers feed it from `RunoutConfig.linerMinFracOfTrue`.
Applies to the schematic and the runout/consolidated sheets; legacy files decode to
free compression. Liners still never get an S-break.

### feat(pdf): default sizing curve — 8" draws 1.25", 4" draws 0.75", linear between

On-device request ("make 8\" shaft the default size of 1.25\" and proportionally work
our way down to 4\" at .75\" and auto adjust the sizes in between… leave the adjustable
slider setup for further customization"). `defaultShaftHeightPt` / `defaultVisualScale`
(`geom/ProfileCompression.kt`, pure, unit-tested): drawn height is linear in true
diameter through 4" → 0.75" and 8" → 1.25" on paper (6" → 1"), continues past both
anchors so sizes always differentiate, and meets the absolute 1.5" ceiling exactly at
10". Replaces the flat 0.40 pt/mm `VISUAL_DIA_SCALE_PT_PER_MM` (kept only as the
degenerate-diameter fallback) as the 100% base in both composers and both height-slider
surfaces; the "Shaft height" slider multiplies on top, unchanged.

### fix(pdf): the S-break pair always keeps daylight — the two edges never overlap

On-device report (schematic screenshot — the paired break glyphs crossed on a tall
shaft's narrow compressed runs). Each edge's curves reach inward toward its partner —
the main S by √3/6 of the amplitude, the return sweep by 1.5·√3/6 — so the classic
`min(20pt, len/4)` gap could be narrower than the glyphs themselves. New
`breakPairLayout` (`pdf/BreakSymbol.kt`, unit-tested `BreakPairLayoutTest`): the gap
widens up to half the run when the full glyph needs the room, then the amplitude
flattens, keeping ≥ 1 pt of daylight ("at worst 1px") between the nearest curves plus
the stroke width. Applied at all four pair sites (schematic, runout/consolidated, wear,
undercut composers); single-edge draws (liner strips) are unaffected.

### feat(settings): sizing-curve anchor heights are user-adjustable

On-device follow-up ("say I decide I want to make 4\" shafts a default of .5\"… set
that up programmatically so I don't have to come back"). New Settings → PDF Export →
**"Default drawing size"**: two sliders set what a 4" and an 8" shaft draw on paper
(0.25"–1.5" in 1/16" steps, standard 0.75"/1.25") and the whole curve re-derives —
`defaultShaftHeightPt`/`defaultVisualScale` now take the anchor heights as parameters
(`PdfPrefs.curveLoHeightIn`/`curveHiHeightIn`, persisted app-wide via DataStore, fed to
both composers and both "Shaft height" slider surfaces). The anchor diameters stay
fixed at 4"/8" and the absolute 1.5" ceiling still caps everything. An inverted pair
(8" below 4") flattens the line at the 4" value — a larger shaft never draws smaller —
with an inline warning; a "Standard (0.75″ / 1.25″)" button restores the defaults.
A live example line shows what a 6" shaft would draw as the sliders move.

### fix(pdf): proportional defaults restored; tapers never compress; schematic lean floors

On-device report with screenshot (8" shaft "looks so chubby and wrong, and the tapers
look bad too… two very different lengths and you can't tell from the drawing").
(1) **Default height restored to the proportional hand-sheet line**: the standard
sizing-curve anchors are now 4" → 0.5625" / 8" → 1.125" — a line through the origin,
matching the historical 0.40 pt/mm look within a half percent — replacing the
0.75"/1.25" defaults, which remain one slider-tap away in Settings → "Default drawing
size" for anyone who wants the taller read. (2) **Tapers may shrink but never
equalize** (on-device refinement: "I don't mind if tapers shrink, I just want them to
stay proportional to their relative widths"): tapers drop their flat 80 pt floor —
which equalized unequal tapers when both clamped to it — for a ratio-preserving
fraction-of-true floor (`PROFILE_TAPER_MIN_FRAC_OF_TRUE` = 0.5, λ-fit like the liner
raises; both tapers scale by the same factor at every squeeze, so a 19½" and an 11½"
taper always visibly differ, and the drawn height never yields to it). (3) **Schematic
lean floors** (experiment, `SCHEMATIC_MIN_*`: thread 28 / body run 40 / liner 56): the
schematic's values live on rails and callouts — not inside spans — so it trades
write-in floor room for proportion; the runout/consolidated sheet keeps the writable
`PROFILE_MIN_*` floors.

### chore(repo): day-run polish — dead code removed, comments and docs trued up

Overnight repo sweep (on-device request: dead code, out-of-date inline comments,
"thorough document combing", heavy polish; agents + review). **Dead code removed** (all
verified zero live callers): `formatDim`, `drawBodiesPlain`, `KEYWAY_SQUARE_SIZE_PT`,
`Achievements.byId`, `SEEDED_SAMPLE_NOTES_PREFIX` (intent folded into
`isSeededSampleNotes` KDoc), and from `ShaftViewModel`: `hasDraft`,
`clearRunoutReading`, `updateWearPitSize`, `snapChainFrom`/`snapChainFromId`,
`moveComponentUp`/`Down`/`moveComponent`; plus 11 unused imports. Deliberately kept:
`VerboseLog.w` (facade symmetry), `specWarningMessages` (staged seam),
`setPdfOalSpacingFactor` and the long-body bubble constants (open product decisions —
see the new `docs/REFACTOR_CANDIDATES.md`, which also catalogs behavior-preserving
refactor options that want their own review). **Comments**: ~40 stale/convention
violations fixed across pdf/ui/data (wrong height-scale claims, phantom "output
picker", phase labels, date stamp, prior-code narratives). **Docs**: 20+ files trued
to the code — five tabs/five documents everywhere, phantom fit functions removed,
render-field renames, envelope records completed (worn sections + undercuts), version
claims fixed, contract-doc path references made resolvable.

### fix(pdf): drawing height takes precedence — liner proportionality is best-effort

On-device direction ("make sure our proportional controls aren't interfering with our
drawing height, that one takes precedence, the liner compression is secondary").
The liner pair no longer trades drawn height: the scale solve ignores the liner
raises entirely (`solveMaxProfileScale` is frac-blind again), and at the solved scale
the raised liner floors λ-fit whatever room the page has (`fracFitLambda`/
`fracFitFactor`, pure, unit-tested) — shrinking themselves, never the shaft; flat
floors and keyway pins are untouched, and only keyway-pinned bodies may still yield
the height (the documented invariant). "Keep liners proportional lengthwise" now
means: as proportional as the page affords at the selected height. The slider readout
reports what liners actually keep ("Liners keep at least ~N% of true length. The
drawn height never changes." — `estimatedLinerKeptFracOfTrue`, replacing the
height-cost readout, which is obsolete by design).

### fix(ui): liner slider shows its height cost; options sheets stop below the status bar

On-device review of the liner control ("gives no indication on how it's changing the
height of the schematic"; the sheet "goes all the way up to my notification bar").
(1) The "Liner compression" readout now shows the drawn shaft height LIVE during the
drag — `estimatedShaftHeightIn` (unit-tested) runs the composers' `solveMaxProfileScale`
arithmetic over the spec, so the line reads "Full height keeps (~1.25″)" until the liner
demand actually starts trading height, then "the shaft draws ~1.13″ tall (1.25″ at
100%)". (2) Both PDF Options sheets cap their expanded height at ~78% of the screen —
a sheet that reached the status bar left no edge to swipe it back down by.

### fix(ui): PDF Options sheets scroll — bottom checkboxes no longer clip

On-device report ("I can't scroll down further and that is what the check boxes look
like" — the Shade-in-PDF rows clipped mid-checkbox behind the navigation bar, the
Liners box peeking out under Tapers). The schematic preview's `PdfOptionsSheet` had
outgrown a phone screen (Shaft height slider) with no scroll of its own; it and the
shared `RunoutWearOptionsSheet` now wrap in `verticalScroll` + `navigationBarsPadding`,
same posture as the project-info sheet.

---

## 2026-08-05 (morning review — Consolidated Output tab, absolute height cap, bubble spread)

### feat(output): the Consolidated Output tab — sheet-content variants + Export all

On-device review of the overnight wave ("one more output tab… consolidated output where
the user can select which I want… and see the preview"; "Export-all would live under the
prior new tab"). New `EditorTab.OUTPUT` (`ui/screen/OutputRoute.kt`, last in the
sidebar): consolidated-sheet content election (`ConsolidatedVariant` — **All three**
default | Schematic + Runout | Schematic + Wear; rails + footer always on, bubbles/TIR
and wear info each electable via `composeRunoutPdf(includeBubbles/includeWearInfo)`;
elected-out bubbles return their lanes to the shaft), the worn-section editor (moved
from the Runout tab), the "Shaft height" slider, blank draft, preview/print/export, and
**Export all** — five document checkboxes batch-written to one picked folder
(`OpenDocumentTree` + `createPdfInTree`), each through the hardened write. The Runout
tab returns to **runouts only** ("the runout editor should only focus on the runouts"):
bubble canvas (wear overlays now render on the Output tab's true-PDF preview), TIR,
station counts, and the classic runout sheet as its own document. Supersedes the
overnight wave's on-tab output picker.

### feat(pdf): schematic follows the "Shaft height" slider; the 1.5" ceiling is absolute

On-device review: "I intended this" (schematic slider) and "width can be limited when
height limit is reached to keep proportionality" (cap). `composeShaftPdf` takes the same
per-job `heightScale`; the slider also lives in the schematic preview's Tune sheet. The
1.5" ceiling (`PROFILE_MAX_SHAFT_HEIGHT_PT`) is now ABSOLUTE — a short shaft whose
width-fit would draw taller is capped, keeps true proportion, and simply doesn't span
the page — replacing the overnight growth-only cap. Slider UX: shared
`ShaftHeightSlider` with the track ending where the cap engages for the current shaft
(`effectiveHeightScaleMax` — "informs me the limit"), commits within ±5% of 100%
snapping to exactly 1.0 + a Reset button ("don't want to fight the slider").

### feat(ui): shaft-height slider selects by VALUE — inches on paper, not percentage

On-device request ("the end of the slider would be 1.5\" and I can select the height by
value, not percentage"). `ShaftHeightSlider` now reads and selects the drawn shaft
height directly in paper inches: the track runs from the 50% height to 1.5" (or the
shaft's 300% height when less), the title shows the picked value (e.g. 1.13″), and the
reset button reads "Standard (X″)". The picked value converts back to the stored per-job
multiplier (`drawnShaftHeightPt` / `heightFracForDrawnHeight`, pure geom, unit-tested —
`effectiveHeightScaleMax` retired), so `RunoutConfig.heightScale` and every composer are
unchanged; commits near the standard height still snap to exactly 100%.

### feat(export): hardened SAF writes + collision gate on every export surface

On-device request ("please unify"). One write path (`util/PdfSafExport.writeShaftPdfToUri`)
for the schematic, runout, wear, undercut, consolidated, and batch exports: a composer
throw repaints the page as a valid "PDF export failed" error page and still writes —
never a truncated/unopenable file — with success-only follow-ups (auto-open, first-PDF
achievement) keyed off the result. The collision export gate now also disables the wear
and undercut tabs' buttons with the standard message.

### feat(runout): bubbles distribute the width evenly; body stations spread over the drawn span

On-device request with the hand-drawn reference ("more even distribution of the space
under the shaft… keep the pointer lines straight"). (1) **Even-spread waterfill**
(`geom/RunoutBubbleLayout.kt` rule 7): with page slack, every adjacent bubble-gap floor
rises to one common level (Σ max(gap, L) = available, capped at `spreadPitch` = 1.5 ×
sameRowPitch) — a dense sheet divides the width evenly among its bubbles (the hand-sheet
look), a sparse sheet spreads comfortably near its stations; floors only ever grow, so
the engine's no-contact/no-crossing guarantees are untouched, leaders stay straight
wherever they clear, and a rerouted one keeps the clean vertical-drop dogleg. Replaces
the cross-row-only leader-clearance widening. (2) **Body stations**
place evenly across each body's DRAWN span (cell midpoints in page x, inverted to
physical mm via the new `CompressedProfileXMap.mmAt`) — body surfaces are uniform, and
physical midpoints bunched into foreshortened runs. Liners/tapers keep the physical
edge-inset convention (worn areas rarely reach a liner's edges — the best reading
spots). Both bubble draw sites share the engine, so preview and PDF stay identical.

---

## 2026-08-05 (output picker + shaft-height slider + liner size compression)

### feat(runout): output selection on the Runout tab — Consolidated (default) | Runout only | Schematic | Wear

On-device request ("pick from the original outputs as individuals, and the consolidated
page… we put so much work into designing and implementing them"). The Runout tab's
Preview / Print / Export buttons now act on a selected output (`ui/screen/OutputDoc.kt`,
FilterChip row): the consolidated ONE-SHEET stays the default; the **classic runout
sheet** returns as "Runout only" (`composeRunoutPdf(consolidated = false)` — one-line
header + raised OAL span line + profile/bubbles/TIR, no rails/footer/wear, restored
`drawRunoutHeader`/`drawOalSpanLine`); the Schematic (honoring the persisted
Standard/Template mode) and the Wear Document export from here too. Selection is
session-only (resets to Consolidated — the blank-draft posture); the original tabs keep
their own buttons; Undercut deliberately stays off the picker. Filenames via
`buildOutputFilename` (consolidated takes `_consolidated`, freeing `_runout` for the
classic sheet). The schematic's export gate (components + no collisions) now guards this
whole surface — the consolidated sheet embeds schematic dimensions. See
`docs/PDF_EXPORT.md` §5.6 and `docs/RunoutSheet.md` (Consolidation step 4).

### feat(runout): "Shaft height" slider — exaggerate or shrink the drawn shaft, capped at 1.5"

On-device request. `RunoutConfig.heightScale` (per-job, rides the `.shaft` envelope like
the undercut exaggeration slider; legacy files default to 1.0) multiplies the sheet's
solved profile scale — 50%–300%, applied after the conventional max(width-fit, visual
scale, value-need) solve. The `PROFILE_MAX_SHAFT_HEIGHT_PT` ceiling (108 pt = 1.5" on
paper) is **absolute** (on-device direction, review follow-up): a short shaft whose
width-fit would draw taller is capped too — it keeps true proportion and simply doesn't
span the page, leaving room for the dimension rails — and the page budget caps
everything. Pure arithmetic in `exaggeratedProfileScale`
(`geom/ProfileCompression.kt`, unit-tested); commit-on-release slider on the Runout tab,
shown for the Consolidated / Runout outputs it affects.

### fix(pdf): liners compress in SIZE — the pin was a misreading of the no-compression rule

On-device clarification: "no compressing liners" meant no **body-style S-break cutouts**
— proportional size compression is fine. Both composers drop the liner `Float.MAX_VALUE`
pin for a finite `PROFILE_MIN_LINER_PT` floor (100 pt — room to write wear values in):
liners foreshorten in proportion above the floor, never draw an S-break (a body-only draw
path), and the height-yield solve (`solveMaxProfileScale`) now serves keyway-bearing
bodies alone. Dimension labels still print TRUE lengths; the footer compression note keys
off actual foreshortening, liners included.

---

## 2026-08-04 (both PDFs — visual diameter scale + proportional compression v2)

### fix(pdf): schematic gets the hand-sheet sizing too; body runs keep writable width and show relative length

On-device reports (schematic's 8" shaft still printed tiny; compressed body runs too
narrow to write diameters in, and equal-cap allocation hid which run was longer).
(1) The **schematic composer** now uses the same visual-scale + compression engine as the
runout sheet: drawn height proportional to TRUE diameter (`VISUAL_DIA_SCALE_PT_PER_MM`,
0.40 pt/mm — 7-8" shafts ≈ 1"+ tall, 5-6" ≈ 3/4", per the rulered hand sketches), all
consumers (dimension rails, Ø-callout leaders, keyways, threads, compression footer note)
riding the compressed piecewise `xAt`; the old `computeDetailPtPerMm` width-fit dilution
is gone, and body-only shafts break-compress too. (2) **Allocation v2**
(`geom/ProfileCompression.kt`): **liners never compress** (pinned at true scale — when
they need the room the drawn height yields via the `solveMaxProfileScale` bisection;
"doesn't have to be perfectly proportional, just close"); tapers/threads/body runs may
foreshorten but keep writable floors (`PROFILE_MIN_TAPER_PT` 80 / `PROFILE_MIN_THREAD_PT`
36 / `PROFILE_MIN_BODY_RUN_PT` 64), keyway-bearing bodies pin like liners, and above the
floors width distributes **in proportion to true length** (monotone bisection solve —
longer runs draw visibly longer, equal runs equal, nothing stretches past true scale);
replaces the equal-cap waterfill and the height-diluting width pre-clamp. Only body runs
draw the S-break pair; tapers/threads foreshorten silently, per the hand sheets.

---

## 2026-08-04 (runout sheet — the consolidated ONE-SHEET: schematic rails + footer join)

### feat(runout): full consolidated drawing — dimensions above, wear inside, bubbles below, spec footer

On-device request, with the complete hand-drawn reference ("If I can fit all this by
hand, then our app should have no problem"). The runout sheet now prints the whole story
on one page: the schematic's dimension system runs ABOVE the shaft (liner/taper span
builders + RailPlanner tiers + PdfDimensionRenderer with value-in-break and blank-draft
write-in gaps; OAL on the topmost rail, replacing the old standalone OAL line), labels
print true typed lengths while the drawn spans ride the compressed mapping; and the
schematic's 3-column footer block draws at the bottom (drawFooter made internal — one
implementation for both documents: taper Rate/L.E.T./S.E.T./Length/KW/Threads columns,
work-order center with bold Side and keyway-clocking note, blank write-in rules),
replacing the sheet's one-line header. TIR line sits directly above the footer. Rail
count feeds the vertical budget before the diameter-scale solve. Wear marks, worn
sections, in-profile Ø values, and bubbles are unchanged. See `docs/RunoutSheet.md`
(Consolidation step 3).

---

## 2026-08-04 (runout sheet — hand-sheet body compression, shaft prints ~1.25" tall)

### fix(runout): real body compression replaces the vertical stretch — an 8" shaft never looks tiny

On-device report (DEFENDER PORT build, with the shop's hand-drawn reference sheet): the
runout profile drew at true SET-to-SET proportion, so a long 8" shaft came out sliver-thin
— the drawing convention is the opposite: shaft ~1–1.25" tall, body sections compressed
"to give the impression of a thicker shaft". The composer now solves a diameter scale from
the target height (`RUNOUT_TARGET_SHAFT_HEIGHT_PT` = 1.25" — same target as the
schematic's `BODY_ONLY_TARGET_HEIGHT_PT`), the in-profile value needs, and page caps, then
maps x through the new pure piecewise engine `geom/ProfileCompression.kt`: detail features
(tapers/liners/threads) keep TRUE proportions at that scale; body runs absorb the overflow
by equal-cap waterfill (short bodies never shrink; long runs share the squeeze) and draw
the S-break pair whenever actually foreshortened. Bubble stations, worn sections, wear
marks, and witness lines all ride the same mapping. Short shafts that already meet the
target keep the classic linear map. This supersedes the same-day `vShaftScale` vertical
stretch (removed); per-value text auto-fit stays as the backstop. Canvas preview keeps its
linear box-fit map (interactive surface; the PDF preview shows the real sheet).

---

## 2026-08-04 (runout sheet — in-profile value legibility polish)

### fix(runout): values fit the profile — auto-fit text, vertical exaggeration, no liner grey

On-device report (first build test): in-profile Ø values towered past the thin
proportional profile, and their white halos read as pasted boxes against grey liners.
Three changes: (1) every value now auto-fits its local band — shrunk until value + halo
sit inside the surface lines (`fittedValueTextSize` in `geom/WornSectionMath.kt`,
per-section for worn values / per-station for readings, floored at 6 pt PDF / 14 px
canvas); (2) the PDF profile stretches vertically when in-profile values exist
(`vShaftScale` — smallest scale that seats every value at full text size, capped by the
space above the bubbles; proportionality deliberately sacrificed per on-device decision,
dia-to-dia ratios kept, value-less sheets stay true-scale); (3) liners draw unfilled on
this sheet regardless of the `shadedLiners` pref, canvas and PDF, so knockouts blend into
the paper. See `docs/RunoutSheet.md` (Legibility).

---

## 2026-08-04 (runout sheet — wear marks migrate, in-profile readings; consolidation step 2)

### feat(runout): dia readings inside the profile, wear bands + pit X's on the sheet, wear tab retired

On-device request following the worn-sections review. Measured-Ø point readings
(`WearDiaReading`) no longer print as below-shaft leader callouts on the consolidated
sheet — each draws INSIDE the profile at its station as a single rotated, halo-backed
column (`drawDiaReadingsInProfile`, reusing the worn-section layout engine with a
degenerate span; liners included, `Ø`-prefixed). Wear-area bands (vertical-line marks,
clamped to their liner span) and pit X's migrate onto the runout profile
(`drawWearMarksOnRunoutProfile` — reuses the wear composer's `drawVerticalBand` and
`drawWearPitsOnProfile` constructions, so every mark stays identical across sites).
Z-order per request: marks first, then worn-section boundaries, then ALL value text last
over knockout halos — text always readable. Same order and same shared implementations on
the preview canvas and the PDF. Blank drafts drop the migrated wear data (wear-doc blank
rule) and keep worn-section boundaries as write-in areas. Division of labor (follow-up
on-device decision): the Wear page **stays as the authoring surface** for
spots/pits/point-readings — its tab and PDF are unchanged — while the Runout sheet is the
consolidated output featuring that wear information; `WEAR_TAB_ENABLED` (`EditorTab.kt`)
remains as the one-line switch for a future full consolidation. See
`docs/RunoutSheet.md` (Consolidation step 2).

---

## 2026-08-04 (runout sheet — worn sections with in-profile Ø values; consolidation step 1)

### feat(runout): designated worn sections print measured diameters inside the profile

On-device request (with hand sketch), first step of consolidating the runout and wear
sheets into one document. A worn section marks a measured area on the runout sheet's shaft
profile: a boundary line at each end and the section's measured Ø values printed INSIDE
the profile — rotated 90°, stacked across the span, each value over a sheet-white halo so
no profile line draws through a number. New reference-only `WornSection` model
(`WearRecord.wornSections`, additive `wear_record` field, never pruned at decode) —
shaft-space span like undercuts (crosses component edges, no orphans), S.E.T.-referenced
Distance authoring via the shared exact-inverse conversion pair, measured values stored
verbatim in list order (golden rule; ≤ 0 never prints). ONE draw implementation for both
sites: `drawWornSections` in `RunoutPdfComposer` is also run by the RunoutRoute preview
canvas through `nativeCanvas` — preview and print cannot diverge; pure column/halo layout
in `geom/WornSectionMath.kt` (unit-tested). Editor: "Worn sections" list + add/edit dialog
on the runout screen (S.E.T. chips, Distance/Length, up to 6 Ø fields). Blank drafts keep
the boundaries (write-in areas) and drop values, same rule as write-in bubbles. Wear-doc
strips/pits/callouts unchanged — their migration is the open consolidation question
(`docs/RunoutSheet.md`, Worn Sections).

---

## 2026-08-04 (settings — appearance themes, undercut drawing style, help & FAQ)

### feat(settings): Appearance — System/Light/Dark theme + High contrast

On-device request (settings customization wave). New Appearance section on the main
settings page: theme mode chips (System / Light / Dark) plus a High contrast switch, with
four schemes in `ui/theme/Theme.kt` (high-contrast light = blue-on-white, high-contrast
dark = amber-on-black, pure black/white grounds). `ShaftSchematicTheme` is now actually
wired into `MainActivity` (it previously existed unwired — TODO §4.1b theme decision),
driven by persisted `AppThemeMode`/`high_contrast` settings. Default is Light + off, which
reproduces the historical bare-`MaterialTheme` look exactly — nothing changes until the
user opts in. Dynamic (Material You) color dropped deliberately so the preview-color
presets resolve predictably. Dark/high-contrast chrome still needs an on-device visual
pass. New contract doc: `docs/Appearance.md`; plan: `docs/SettingsCustomization_PLAN.md`.

### fix(theme): paper-sheet canvases pinned to fixed ink (`ui/theme/SheetInk.kt`)

Prerequisite for dark mode: the five white-sheet canvases (undercut overview + detail,
wear overview + detail, runout preview) captured `colorScheme.onSurface` (near-white in
dark theme) as drawing ink — dark mode would have printed invisible lines on the white
sheet. Sheet ink now comes from `SheetInk` (Outline = black; LinerTint / WearRed pinned to
the historical light-theme tertiary / error values), so the sheets render identically in
every theme. Interactive affordances (tap tints, selection, draft outlines) stay
theme-driven. Light-theme rendering is visually unchanged (onSurface ≈ black → pure
black is the only delta).

### feat(undercut): user-stylable drawing shades + line-art (color-removal) mode

On-device request. New "Undercut Drawing" block on Settings → Preview Colors: shade color
(Grey default / Bronze / Blue), shade intensity (Light / Standard / Dark), and a
**Line art (no shading)** switch that empties every fill — white drawing, black outlines
only. Resolved by new pure `util/UndercutStyle.kt`; the alpha ladder hangs off
`UNDERCUT_SECTION_FILL_ALPHA` so "section core one step lighter than the liner" holds at
every intensity, and the Standard/Grey default reproduces the previous fixed shades
(pinned by `UndercutStyleTest`). `drawUndercutNotches` gains a `sectionFillColor`
parameter (was a hardcoded black-alpha). Screen-only: the printed undercut PDF keeps
standard drawing colors (PDF line-art is a considered follow-up in the plan doc).

### feat(help): in-app Help & FAQ screen

On-device request. New `help` nav route + `HelpRoute.kt` (Settings → "Help & FAQ"):
static expandable topic cards in three sections — Getting Started, How-To Guides, FAQ
(~19 topics: units, carousel editing, bodies/auto-bodies, keyways, wear/runout/undercut
recording, export/print/blank templates, backup, golden-rule and reference-feature
explanations). No ViewModel; content restates current behavior and must be updated in the
same change as a behavior change (noted in `docs/Navigation.md`).

---

## 2026-08-03 (undercut drawing — section cores fill one step lighter than the liner)

### feat(undercut): 50%-lighter grey fill inside each cut section

On-device request: the section's remaining core (between the floor lines) drew at the same
grey as the liner, so the step read only through its outline. The core is now erased to
the sheet colour and refilled at half the liner shade's alpha
(`UNDERCUT_SECTION_FILL_ALPHA` = 20/255 in `geom/SurfaceProfileMath.kt`), so every cut
span reads one step lighter than the liner around it. Applied identically in all draw
sites (route overview + detail overlay canvases, PDF, SVG preview); voids stay pure white,
liner shade unchanged.

---

## 2026-08-03 (undercut drawing — cuts are open silhouette steps, not boxes on the liner)

### fix(undercut): each cut is its own stepped rectangle section — no lid, no liner outline across it

On-device report (with the original hand sketch): the printed cuts read as white boxes
pasted ON the liner — a lid drawn along the surface plus the liner outline surviving across
the cut span. The "complete box" reading of the sketch was wrong: its rectangles are
**silhouette steps**. A cut now draws as material removed — the void erases the surface
stroke and fill down to the floor and the mouth stays **open** (nothing redraws over it), a
full-height **section face** stands at each end (top surface to bottom surface, like any
machined diameter step; skipped where a taper has run down to the floor —
`NOTCH_FACE_MIN_STEP_PX` in `geom/SurfaceProfileMath.kt`), and the floor lines cross the
span — so over the cut's span only the undercut section exists, its own reduced-Ø
rectangle between two faces. Same construction in all draw sites (route overview + detail
overlay canvases, PDF, SVG preview); the draft dash applies to faces and floor. Grey liner /
white voids and depth exaggeration unchanged.

---

## 2026-08-03 (PDF preview — blank-draft toggle surfaced on the preview itself)

### feat(pdf): always-visible "Blank draft (write-in)" chip on the schematic preview

On-device report: the blank-template output was reachable only through the Tune options
sheet — hard to find, even when demoing the app. The schematic PDF preview now overlays a
labeled FilterChip (top-center, checkmark when active, testTag `pdf_blank_toggle`) that
drives the same session-only `pdfBlankDraft` state as the sheet's switch — the two can
never disagree, and toggling re-renders the preview live. Blank mode itself is unchanged
(session-only, `_BlankDraft` filename suffix).

---

## 2026-08-03 (undercut editor — body-only cuts default to the nearer S.E.T.)

### feat(undercut): bare-shaft cut's Distance reference picked by SET proximity

On-device request (covering the rare body-only cut): a cut outside any liner has no liner
edge to measure from, so its Distance defaults to whichever S.E.T. is nearer —
`nearestSetReference` in `geom/UndercutMath.kt`, midpoint-vs-midpoint, tie breaking AFT.
The printed bare-shaft strip's title anchor (`undercutAnchorFor`) now delegates its side
choice to the same helper, so the card's default Distance and the sheet's "FROM … S.E.T."
always read from the same datum. Liner cuts are unchanged (`LINER_AFT` — the edge the
machinist is standing at); stored references are never rewritten. Pinned in
`UndercutMathTest`.

---

## 2026-08-03 (undercut editor — preview window extends over liner-edge overhang)

### fix(undercut): detail overlay widens live for a draft overhanging the liner

On-device report: a cut edited past the liner's AFT edge (rare but legal) poked outside
the zoomed strip's drawing, which stayed pinned to the stored record's range until
Confirm. The overlay's window now follows what is **previewed** — the stored cuts with the
live draft substituted — via `undercutPreviewDrawRange` (`geom/UndercutMath.kt`): widened,
never narrowed, with the standard 1" pad of neighbour stock beyond the overhang, exactly
the range a confirmed overhang gets when the strip rebuilds. PDF strips are unaffected
(they only see confirmed cuts, already extended by `linerStripFor`). Pinned in
`UndercutMathTest`.

---

## 2026-08-03 (undercut editor — Length edits no longer rewrite the Distance field)

### fix(undercut): golden rule — length edit keeps the authored Distance fixed

On-device report (step-by-step screenshots): with the Distance authored against Liner FWD,
shortening Length 12 → 10 rewrote the Distance field 5 → 7. The displayed distance under a
FWD-flavored reference is `fwdRef − canonicalStart − length`, and the Length commit held
canonical `startFromAftMm` fixed — so the derived Distance absorbed the length delta,
rewriting a typed value. The Length field now re-derives canonical from the active
reference at the new length (`undercutCanonicalForNewLength` in `geom/UndercutMath.kt`,
the existing conversion pair composed): the authored Distance never moves, the cut's FWD
end stays pinned where it was located, and the cut grows/shrinks AFT-ward. Exact no-op
under AFT-flavored references. The Length validator now checks the recomputed canonical
(it previously validated the wrong resulting span under FWD references). Pinned in
`UndercutMathTest`; "canonical never moves" clarified (reference switching only) in
`CLAUDE.md` + `docs/UndercutDrawing.md`.

---

## 2026-08-03 (undercut sheet — liner strip titles locate the liner, not the cut)

### fix(undercut): liner strip anchor is the liner's own edge-to-SET datum

On-device report: a liner 20" from the AFT S.E.T. with its first cut 11.5" in from the
liner edge printed as "AFT Liner — 31.5" FROM AFT S.E.T." — the title named the liner but
measured to the cut's near shoulder, adding the two figures. A liner strip's title now
carries the **liner's own** location (`buildLinerAnchorLabel` + `linerAnchorForPdf` — the
identical number the schematic and wear sheet print for that liner, chosen from the same
SET), and cuts stay located by the strip's chain rail, measured from the liner's edges
(11.5" in this example). Bare-shaft strips keep the cut-proximity anchor
(`undercutAnchorFor`) — there is no liner to reference. A liner strip whose cuts all clamp
away now also prints its anchor, not just the bare name. SVG preview mirrors the same
branch; `docs/UndercutDrawing.md` updated.

---

## 2026-08-03 (schematic footer — shop notation for common taper rates)

### feat(pdf): 1:12 and 1:16 print as 1"/ft and ¾"/ft on inch drawings

On-device request: the two rates the shop uses most now print in taper-per-foot form —
`Rate: 1"/ft` for 1:12 and `Rate: ¾"/ft` for 1:16 (real fraction character) — matching how
they're hand-written. Applies whether the rate is auto-snapped or typed as `1:12`/`1:16`;
every other rate keeps its ratio form (1:10, 1:20, exact `1:N.NNN`, or the user's own
manual text, verbatim). Metric drawings keep the ratio for all rates — inch-per-foot
notation would clash with mm dimensions. Print-layer only (`printedTaperRate` in
`ShaftPdfComposer`): the stored `taperRateText`, the carousel/dialog rate fields, and the
`TaperRateAuto` contract are untouched. Pinned by `TaperRatePrintNotationTest`.

---

## 2026-08-03 (secondary sheets — OAL label matches the schematic)

### fix(pdf): runout/wear/undercut OAL prints fractions like the schematic

On-device report from printed test documents: a 158 ⅛" shaft printed its OAL as
`158.1250"` on the runout and wear sheets (raw 4-decimal, while the schematic showed the
fraction). All three secondary composers (`RunoutPdfComposer`, `WearPdfComposer`,
`UndercutPdfComposer`) hand-rolled the label with `%.4f`; they now route through the same
`formatLenDim` the schematic's OAL rail uses — inches snap to mixed sixteenth fractions
with a 3-decimal fallback, mm gains a decimal (2 → 3) to match. The `OAL:` prefix and the
seat-in-the-break layout are unchanged.

---

## 2026-08-01 (undercut drawing — boxed cut sections)

### fix(undercut): cut sections drawn as complete boxes

On-device request, referencing the feature's original hand sketch: every undercut section
now closes into a full outlined rectangle. The notch outline gains a top edge along the
region's surface polyline — so a cut crossing a liner edge steps over that edge rather than
closing on a straight chord — joining the two shoulders and the floor, mirrored top and
bottom, at the notch outline's own weight and colour. The void fill still overdraws the
*component's* surface stroke, so the closing line is the notch's, not a leftover half-stroke;
a draft's dash and status colour apply to it too, making a provisional cut a dashed box.
Canvas (`drawUndercutNotches`, shared by the route overview and the detail overlay), PDF
(`UndercutPdfComposer`) and the SVG preview move in lockstep. No other geometry changed.

Follow-up on-device request — "keep the liner grey, make the undercuts white": a real detail
strip now always shades its liner span (no longer gated on the `shadedLiners` pref; bodies and
tapers stay pref-driven, the blank template's edges-only strip stays clear paper) while the
notch voids stay pure white, so the boxed sections read as cuts. Both undercut canvases paint
on a hard-coded white sheet, so their component fills became fixed black-alpha instead of theme
colours — a dark-theme tint washed into the paper and left the white voids nothing to contrast
against; the liner takes the PDF shade fill's weight, the overview's bodies/tapers stay lighter.

---

## 2026-08-01 (undercut PDF — single-level fallback values above the line)

### fix(pdf): chain fallback values go above the rail when it's the only label level

On-device report: a small value that can't seat in its span's break (a narrow pad like
1 ⅛") was pushed under the dimension line even when nothing sat above the rail, reading
as orphaned. `planUndercutRailRows` now puts fallback rows ABOVE the line when the chain
has no total rail over it (single-cut cluster), reserving the band above via
`chainAboveBandPt`; with a total rail present they tuck below as before. Row pitch,
level separation and the started strip's hand-draw band are unchanged.

---

## 2026-08-01 (undercut PDF — rails pulled in close to the shaft)

### fix(pdf): undercut rail height cut roughly in half, level spacing kept

On-device report: after the level-separation round the dimension rails floated far above
the shaft. The chain is now resolved before the vertical split and the rail reserves only
the fallback rows its labels actually use (`undercutRailRowBudget`: min 1, max 2; started
strips keep the full 2-row hand-drawing budget), and the surplus rail headroom is halved
(30→15pt). Level-to-level spacing (chain↔total 20pt, 17pt fallback row pitch) is
unchanged; `UNDERCUT_CYL_BELOW_EXTRA_MAX_PT` 88→96pt keeps the started strip's surplus
split even.

---

## 2026-08-01 (undercut drawing — status pill, strips-only sheet, output polish)

### feat(undercut): auto-confirm on leave + floating status pill

On-device report: the per-card Confirm button was tucked away and easy to forget.
- A valid dirty draft now **auto-confirms when its card is left** (swipe, notch tap, add,
  or closing the overlay); a blocked draft never silently commits or discards — a dialog
  offers Keep editing / Discard. Sweep covers stragglers from late blur commits.
- A **floating status pill** at the canvas/carousel boundary is the always-visible truth:
  green check "Saved"; "Confirm change" + discard (✕) while editing; error-styled with the
  blocking reason while conflicted. Per-card Confirm/Cancel buttons removed.

### feat(pdf): strips-only undercut sheet + started-strip write-in template

On-device report: real undercut drawings show only the cut sections. With ≥1 strip the
sheet drops the shaft profile and OAL line entirely — header, one AFT/FWD row, strips
filling the page, Notes. With nothing recorded, the page prints one **started strip per
liner** — no notches or dimensions, just the two chain-datum bars and a circle-one
"FROM AFT / FWD S.E.T." writing rule. The **blank/template** sheet draws only the
starting geometry there (on-device report): the liner's two vertical end faces, the
neighbour stock slivers outboard of them and the break edges, with its span left as clear
paper — no fill, no top/bottom surface lines — for the hand-drawn liner and cuts. An
**empty-record export** keeps the liner fully drawn. The whole-shaft profile form
survives only for a shaft with no drawable liners.

### fix(pdf): rail spacing, section size, end air

- Total rail band 22→38pt (~20pt air to the chain rail); fallback labels clear the
  arrowheads on a 17pt row pitch with white halos, drawn in a second pass so no witness
  line strikes through.
- Cylinder capped (0.38 × band, 170pt ceiling) so full-page strips stay drawing-sized;
  break-edge amplitude capped at 18pt so the end lobes stop sprawling.
- Every strip end now draws ≥24pt of neighbor stock outside the chain datums
  (pt-floor, widening only outward — no datum or printed dimension changes).

### fix(ui): preview page tucks behind the toolbar

`PdfPreviewOverlay`'s zoom/pan content is clipped to its container (draw-only fix;
buttons were always tappable) — covers undercut, wear, and runout previews.

---

## 2026-07-31 (undercut drawing — card carousel with draft/confirm, adjacency guard)

### feat(undercut): swipeable card carousel, draft-until-confirm editing

On-device reports: the overlay's vertical card stack forced scrolling between the drawing
and the fields; editing one cut could cross-wire values into another (cards recomposed
positionally over a list that re-sorted mid-edit — a golden-rule violation, eliminated
structurally below); and two shallow cuts in one liner drew as hairlines because a deeper
cut in another liner owned the sheet's exaggeration reference.

- **Card carousel** (`HorizontalPager`, ComponentCarousel-style neighbour peek): canvas
  pinned above, swipe between cuts, ordered aft → fwd; swiping highlights the notch, tapping
  a notch pages to its card.
- **Draft-until-confirm**: fields edit a local per-id draft previewed live on the canvas —
  dashed notch in the selection color, switching to the error color while the confirm check
  fails; **Confirm** commits verbatim (and only then do cards reorder, the carousel following
  the confirmed cut); **Cancel** reverts everything including reference chips. "Add
  undercut" is draft-only until confirmed — no ghost cuts. Pages/drafts/commits are keyed by
  cut id and ordering reads stored values only, so the positional cross-wire bug cannot
  recur (pinned by `UndercutDraftTest` + ViewModel target-only tests).
- **Adjacency guard** `undercutOverlapIssue`: a draft may not intrude into another cut's
  bounds (edge-to-edge touching legal); Confirm disables with the reason inline. Stored
  data is never retroactively rejected.
- **Exaggeration curve fix**: the depth ratio is square-root compressed and floored at
  `UNDERCUT_MIN_SHARE_OF_EXAGGERATION` (0.25 of the slider), so shallow cuts stay readable
  on sheets that also carry a much deeper cut; deepest-draws-at-slider and
  deeper-draws-deeper are unchanged.

Docs: `docs/UndercutDrawing.md`, `docs/UndercutDrawing_PLAN.md`.

---

## 2026-07-31 (undercut drawing — depth exaggeration slider, open notch mouths)

### feat(undercut): per-sheet drawn-depth exaggeration normalized to the deepest cut

On-device reports: real undercuts (1/16"–1/2" on shafts up to ~10" Ø) are hairlines at
true scale, and a stroke ran across the top of each cut (the surface outline surviving
the void fill).

- **Exaggeration slider** on the Undercut tab (0–25%, `UNDERCUT_EXAGGERATION_MAX_FRAC`),
  stored per document as `UndercutRecord.exaggerationFrac` (additive envelope field,
  older files default 0.25). The sheet's **deepest measured cut** draws at the slider
  fraction of its local surface Ø; shallower cuts scale relative to it
  (`normalizedNotchFloorDiaMm` + `deepestUndercutDepthMm`, whole-sheet normalization),
  so sheets with very different absolute depths read alike while proportions within a
  sheet stay honest. Never drawn shallower than reality; 0% = true scale; Ø-0
  placeholders draw at half the slider (4% visibility floor) and are excluded from the
  normalization reference. Region topology still comes from the TRUE floor; printed Ø
  values are always the stored measurements.
- **Open notch mouths**: the void fill now overdraws the surface stroke across each cut
  in both draw sites — no outline runs across the top of an undercut.
- Fix folded in: a Ø at/above the local surface degenerates to the surface instead of
  drawing outside the shaft.

Docs: `docs/UndercutDrawing.md`, `CLAUDE.md` invariant block, `docs/UndercutDrawing_PLAN.md`.

---

## 2026-07-31 (undercut drawing — liner-anchored strips, liner references, route list)

### feat(undercut): liner-aware authoring + liner-anchored detail strips

Second iteration of the Undercut Drawing tab (the tab itself, its `undercut_record`
envelope field, cluster-window strips, and the `UNDERCUT RECORD` PDF shipped 2026-07-30
and are on `main`; that introduction predates this changelog entry). On-device report:
zoomed strips printed as an anonymous grey slab with no liner edges, tapping the overview
off a cut did nothing, and cuts could not be removed without opening the overlay.

- **Liner-anchored strips**: a cut inside a liner now draws that liner's whole span,
  wear-style — true edges visible, neighbor slivers + break edges outboard, dimension
  chain anchored on the liner's edges (extended only by a cut overhanging an edge; the
  zoom pad is never dimensioned), liner display title on the strip. Bare-shaft cuts keep
  the padded cluster-window style. `geom/UndercutMath.kt`'s sealed `UndercutStrip`
  (`buildUndercutStrips`, max-overlap `assignUndercutLiner`) feeds overview, overlay, and
  PDF so all three agree by construction.
- **Liner references**: `UndercutReference` gains `LINER_AFT`/`LINER_FWD` beside the two
  S.E.T. datums, with a display-only `Undercut.referenceLinerId` (canonical shaft-space
  position never moves; a deleted reference liner falls back to the AFT S.E.T.
  projection). Note: files authored with the liner references do not decode in builds
  predating them (additive-enum rule); S.E.T.-only files are unaffected.
- **Tap-to-zoom liners**: every liner on the overview is a tap target (badge shows its
  cut count); an undercut-free liner opens as an empty authoring strip with
  "Add undercut in this liner" (centered default, Liner AFT reference preselected).
- **Recorded undercuts list** on the route: one row per cut (reference-aware distance,
  length, Ø, stale warning), tap to zoom, per-row delete.

Docs: `docs/UndercutDrawing.md` (contract), `docs/UndercutDrawing_PLAN.md` (status),
`CLAUDE.md` invariant block.

---

## 2026-07-30 (keyway clocking — 90° apart added alongside 180°)

### feat(ui): "Keyways 90° apart" toggle + CW/CCW direction chips

On-device report: a shaft came in with keyways 90° apart rather than 180°. Added
`ShaftSpec.keyways90Apart` + `keyways90Cw` (direction from the AFT keyway, viewed from
aft) alongside the existing `keyways180Apart` flag — the two are mutually exclusive
(`ShaftViewModel.setKeyways180Apart`/`setKeyways90Apart` each clear the other). Surfaced
on keyway-bearing carousel cards (Body, Taper) and in `AddBodyDialog`/`AddTaperDialog`
under the same ≥ 2-keyway gate as before, keeping add-dialog parity. Rendered as a
depth-deep notch on an outline edge (bottom for CW, top for CCW) rather than the 180°
flag's hidden dashed line; the spoon bowl is not drawn at 90°. Docs:
`docs/COMPONENT_CONTRACT.md`, `docs/DATA_MODEL.md`, `docs/GLOSSARY.md`,
`AddComponentDialogs.md`.

---

## 2026-07-29 (wear overlay — diameter measurements get their own section; min-Ø retired)

### fix(ui): Add Ø moved out of the Pits tool row; dedicated section with Remove Ø

On-device feedback: the lone "Add Ø" chip inside the Pits tool row blended in and read as
a pit action. The wear overlay now has a **"Diameter measurements"** section below Pits —
own header, recorded count, and tool chips **Add Ø** / **Remove Ø** (tap a measurement
tick to delete it; a miss is a no-op, same posture as Remove X). One canvas tool stays
active at a time across both sections (`WearCanvasTool`), and each section shows its own
helper text. Docs: `RunoutSheet.md` UI paragraph + `UI_CONTRACT.md` §7.5.

### fix(ui): wear detail canvas — break edges no longer clip; zoom-out to 0.5×

The broken-out assembly's layout now reserves edge padding (`SEG_EDGE_PAD_DP` = 32dp per
side) for the neighbor stubs' S-curve break edges, whose bulge extends up to `r × 0.6`
past the stub's outer x — a full-width liner previously clipped the curves at the canvas
edges, reading as mis-sized stubs (on-device report). Zoom range widened from 1×–6× to
0.5×–6× for a step-back overall view; pan still resets at ≤1×.

### feat(ui): pinch-to-zoom on the wear detail canvas

The component wear overlay's broken-out canvas now supports pinch-to-zoom (1×–6×) with
two-finger pan, for accurate pit / wear-band / measured-Ø placement — the RunoutRoute
preview's transform pattern (`transformable` → `graphicsLayer`, taps inverted through the
scale-about-centre + translate transform so hit-testing and placement stay in canvas
space at any zoom; zoomed-in taps gain effective precision). Pan resets when zoomed back
out to 1×. The WearRoute overview canvas stays deliberately zoom-free.

### fix(wear): per-band min-Ø field and printed label retired

On-device report: the min-Ø label under a wear band collided with the measured-Ø callout
values at the same spot — and the readings say the same thing better (exact stations vs
one value per band). The spot card's "Min diameter measured" field and the strip's
`⌀value` label are removed; `formatMinDiaLabelOrNull` deleted. `WearSpot.minDiaMm` stays
in the model so old files round-trip (commits pass the stored value through verbatim; it
is never entered or printed).

---

## 2026-07-28 (wear document — measured-Ø readings)

### feat(wear): tap-to-add diameter measurements, printed as callouts with leaders

Digitizes the shop's hand-written diameter values under a worn section (reference photo:
values fanned below the shaft, each with a leader to the measured spot, nominal at the
unworn edge). The fifth reference-only feature — never affects OAL/resolve/collision.

- **Model**: `WearDiaReading(componentId, axialMm, diaMm)` in `WearRecord.diaReadings` —
  additive envelope field (no codec/version changes; old files decode unchanged). Keyed by
  resolved component id (liner/taper/body, explicit or auto), component-local axial from
  the AFT edge; orphans skipped at the render layer (like pits/runout readings, unlike
  spots). `diaMm` stored verbatim (golden rule); `0` = placed-but-empty (overlay-only,
  never printed).
- **UI**: "Add Ø" tool chip in `ComponentWearDetailOverlay` beside Add X / Remove X — tap
  the segment to record a measured diameter (value dialog; created only on Save, so Cancel
  leaves no ghost), tap an existing witness tick to edit/delete. Canvas draws ticks +
  fanned value callouts below the segment.
- **Placement engine**: new pure `geom/WearDiaCalloutLayout.kt` (+ `geom/WearDiaMath.kt`
  hit-testing) — `RunoutBubbleLayout`'s label-width-aware sibling: order-preserving
  least-squares spread (shared PAVA solver), single-row fan → two-row stagger with dogleg
  leaders → flagged uniform compression; randomized JVM tests assert no leader crossings
  or label intrusions.
- **PDF**: liner readings print on that liner's detail strip (full-height witness tick +
  value band reserved below the cylinder via `computeWearStripInnerLayout(diaBandPt)` —
  reading-free strips are pixel-identical to before, regression-pinned); body/taper
  readings print under the main profile below the names row (band reserved only when
  present; taper surface Ø interpolated at the station). Labels use `formatDiaWithUnit`,
  no `Ø` prefix. Blank drafts omit readings like all recorded wear.
- **Tests**: `WearDiaCalloutLayoutTest`, `WearDiaMathTest`, `WearStripDiaBandTest`,
  envelope round-trip/legacy-decode additions to `WearRecordPersistenceTest`, plus a
  same-math SVG preview generator (`WearDiaCalloutSvgPreviewTest` →
  `app/build/reports/wear-dia-preview/`).
- **Docs**: CLAUDE.md invariant block; `RunoutSheet.md` §Wear Diameter Measurements;
  as-built plan `docs/WearDiaMeasurements_PLAN.md`; root-doc refresh (ARCHITECTURE,
  DATA_MODEL, PDF_EXPORT, UI_CONTRACT, GLOSSARY, COMPONENT_CONTRACT, ROADMAP — envelope
  records, `geom/` shared-engine layer, checkbox-only auto-body promotion, blank-mode
  strip retention).

---

## 2026-07-28 (wear document — all liners, write-in template, layout reclaim)

Four rounds of on-device feedback in one day, all on the wear/inspection sheet.

### feat(pdf): every liner gets a detail strip; blank write-in template keeps the drawing

- **All liners always drawn** (normal shop operating procedure): `collectWearLinerGroups`
  returns every drawable liner (positive length + OD) with a possibly-empty spot list, so
  the page mode (`PROFILE_FORM`/`COMBINED`/`GRID`) is a function of the shaft's liner
  count. A spotless liner's strip has no bands or min-Ø readings.
- **Blank draft (write-in) keeps the strips**: previously it collapsed to profile-only.
  Now the profile and every zoomed liner strip render with dimension lines kept and values
  left out — the machinist fills the sheet in by hand after printing.
- **Circle-one anchor titles**: blank strip titles read `<Liner> — ____ FROM  AFT / FWD
  S.E.T.` (`WEAR_BLANK_ANCHOR_SUFFIX`) — both directions print, the machinist circles one;
  always left-aligned since a write-in sheet doesn't presume a measurement direction.
- **Shared positional liner titles**: all four wear-PDF draw sites (strip titles, names
  under the profile, blank prefixes, overflow note) use `buildLinerTitleById` — custom
  label wins, else AFT/MID/FWD defaults — identical names to the carousel and runout sheet.

### fix(pdf): wear sheet spacing — blank header room, edge-bar rails, profile band reclaim

- **Blank header** 36 → 56 pt with writing rules 24 pt apart (handwriting room); blank
  profile→strips gap 18 → 8 pt pays for it. Printed header unchanged.
- **Band-less rails show edge witness bars only** — every strip in blank mode, spotless
  liners on the printed sheet. No spanning line/arrows/length value: rails measure
  distances to wear areas, not each liner's OAL.
- **Profile band reclaim**: the band shrinks toward a content-derived preferred height
  (OAL region + shaft Ø + names row) instead of absorbing all leftover page height with
  the shaft floating centered; strips grow into the freed space (≤ 170 pt each,
  `WEAR_STRIP_HEIGHT_MAX_PT`), remainder returns to the band (shaft slack-centered,
  clamped clear of the strips). OAL clearance above the shaft 90 → 44 pt.
  New `preferredProfileHeightPt`/`maxStripHeightPt` params on
  `computeWearVerticalLayout`/`computeWearStripGridLayout` (defaults preserve the
  absorb-all-slack behavior, pinned by tests).

### fix(pdf): uniform liner strip heights

- Every strip's liner cylinder now fills the strip's fixed vertical budget, so all zoomed
  liners on a page render at the **same height** — the per-strip horizontal scale
  (length-derived) no longer leaks into cylinder height. Lengths keep their horizontal
  proportions; liner OD differences are deliberately not height-encoded (product
  decision). Neighbor stubs keep their true diameter ratio to the liner, clamped to the
  liner radius. `computeWearStripRadii` rewritten (4-param), tests replaced.

### fix(pdf): OAL out of the header; blank spans get a writable mid-span break

- Header no longer carries OAL, on either the wear document's printed or blank header —
  it duplicated the drawing's own end-to-end span. Blank header fields spread
  edge-to-edge across the full content width for large handwriting, and the title
  centers on line 2.
- Blank OAL spans (wear + runout) cut an empty `BLANK_DIM_GAP_PT`-wide writable break
  mid-span, dropping the old "OAL:" label + rule — the same convention the schematic's
  dimension breaks already use. No "OAL" wording where handwriting goes.
- Printed spans **keep** the small "OAL" prefix (product decision: print output is
  compact and it reads as a nice visual identifier) — wear/runout OAL lines and the
  schematic's `oalSpan()` top rail are unchanged in print.
- Printed wear/runout OAL values now **seat in a break cut mid-span**, vertically centred
  on the line — the schematic's value-in-a-break convention, kept consistent across
  drawing outputs (previously the label floated above a continuous line). Short-span
  fallback mirrors `PdfDimensionRenderer` (continuous line, label above).
- The wear strips' **chained rail values** seat in breaks too: a label that fits inside
  its span (the inward-arrow test) sits in the line's gap; overhanging labels for short
  bands/gaps keep the stacked below-line fallback rows. Shared pad
  `DIM_BREAK_TEXT_PAD_PT` (`BlankFormText.kt`) across all break sites.

### chore: comment hygiene — no dates, no prior-code narratives

- Removed date stamps (~90 across 27 files) and removed-code narratives (~65 sentences
  across 28 files) from `.kt` comments. Comments now state current behavior and
  constraints only; load-bearing warnings keep their consequence in present/conditional
  tense ("doing X would cause Y"). History lives here and in `docs/*.md` — see
  `docs/STYLE_GUIDE.md` §"Comment Conventions".

## 2026-07-26 (wear detail)

### feat(ui): threaded shaft ends drawn flat + hatched in the wear detail overlay

On-device report: the AFT taper's wear-inspection view showed an S-curve break on its aft
stub, implying the shaft continues — but the only thing aft of that taper is the end thread.

- **`LinerWearDetail.kt`:** a neighbor **thread with nothing beyond it** now renders as a
  flat-ended stub with a diagonal thread hatch (`drawThreadStubHatch`, same convention as
  `ShaftRenderer.drawThreadHatch`) instead of the S-break. Any other neighbor keeps the
  break (the fixed-width stub genuinely truncates it); no neighbor still means no stub
  (the component's own flat edge — the already-correct FWD-taper case). Overlay-only: the
  wear PDF's detail strips are liner strips with real material on both sides, unchanged.
- **TODO:** added the runout-sheet tap-to-place bubble + leader line idea (Backlog §6).

## 2026-07-26 (later still)

### fix: phantom blank drafts on launch; swipe selection bricked by orphaned ids

Two on-device reports from branch testing.

- **fix(autosave): phantom blank "Untitled draft" on app open.** The dirty-gate baseline
  is seeded synchronously at ViewModel init (blank spec, unit = mm default); the async
  settings restore then flips the unit preference (inches), making the untouched blank
  session "dirty" — 1.5 s later the observer persisted a blank draft. Only bites when the
  draft ring is empty (a non-empty ring auto-restores its newest entry instead), which
  became the common state once saves started clearing their ring entry. Fix: a
  factory-default session never writes a draft — new pure predicate
  `SessionSnapshot.isDefaultSession()` (`DraftRing.kt`, deliberately ignores unit/config
  flips) gates the autosave observer and the title-bar dirty flag; the observer's
  dirty→clean branch now also deletes existing phantoms on their next debounce tick.
  `ShaftViewModel.isSessionDefault()` delegates to the same predicate. 6 new tests.
- **fix(ui): orphaned selection bricked swipe-to-highlight.** `isUserInitiatedScroll`
  treated a selection whose id no longer resolves to any carousel row (`selectedIndex ==
  -1`) as "our own catch-up scroll", so swipe adoption never armed: swiping changed cards
  but never updated the selection, and the preview highlight never followed until a
  preview tap set a valid id. Orphans are routine — auto-body ids are position-derived
  and regenerate on every edit (much more visible now that repaired drawings are all-auto),
  and open/new carried the previous document's selection. Fix: an orphaned selection
  counts as user-initiated (the follow effect never animates toward a missing row, so no
  fight is possible), and `importJson`/`newDocument`/`restoreSnapshot` clear the selection
  at the session boundary so the carousel's seed effect reselects. Pre-existing in the
  inline logic since before the 6e4f19e extraction; not a regression from this branch's
  fixes.
- **feat(ui): the first component is highlighted on open.** The seed effect was keyed on
  `rowsSorted.size`, so opening a document with the same row count as the previous one
  never re-seeded, and an open-time race could seed a stale id. Replaced with a pure,
  tested decision (`seedSelectionAction`, `CarouselSelectionSync.kt`): nothing selected →
  seed + scroll to the **first** card (the AFT-most component — thread, taper, whatever
  leads the shaft; per product decision the highlight defaults to the first item rather
  than remembering a previous selection); selection orphaned → adopt the current page
  **without scrolling** (highlight returns after auto-body id churn without yanking the
  user off the card they're editing); live selection → leave alone. No highlight only
  when the highlight toggle is off or the shaft has no components.

Suite: 820 tests green.

---

## 2026-07-26 (later)

### fix(ui): typed field commits are never snapped

On-device (branch testing): editing a FWD taper's length from 12.0157" to 12" and
blurring left the model — and the drawing — at 12.0157". The commit path routed through
`applySnapped{Body,Taper,Thread,Liner}Update`, which snapped the recomputed start/end to
component-edge anchors within ±1 mm (0.04"). The 12.0157"→12" edit moves the taper's
start by exactly 0.4 mm, so the snap pulled it back to the adjacent body's edge and the
length recomputed to its old value — the typed number was silently undone. Pre-existing
(wired since 2025-12), not a regression from today's fixes; it only bites on edits
smaller than the tolerance, which is exactly the "remove the fractional tail" case.

- Carousel update callbacks are now wired directly; the four snap wrappers and the
  `snapAnchors` plumbing are deleted. Typed values reach the ViewModel verbatim.
- Tap-to-add snapping (`SnapUtils.kt`) is unchanged — snapping belongs to coarse
  gestures, not typed numbers. A sub-mm gap left next to a resized component is real,
  visible, and auto-filled; a silent revert is neither.
- Invariant pinned in `CLAUDE.md` + `docs/ShaftScreen.md` (v0.12): do not reintroduce
  snapping into typed-commit update paths — the companion to the 2026-06-19 removal of
  the `snapForwardFrom` cascade from VM updates.

---

## 2026-07-26 (late)

### fix: body-merge diameter rule, unique fragment ids, IME field coverage

Root-caused an on-device report ("opened a saved file, every body was explicit with
changed diameters"): between `89c49d0` (07-21) and the 07-26 fixes, the composition-commit
bug (see the entry below) combined with the then-current promote-on-any-commit auto-body
card, so **merely paging the carousel silently promoted every composed auto span to a
stored Body** — diameters drifted via display-unit rounding plus the
nearest-upstream-explicit-body derivation cascade. Current code has no mass-promotion path
(checkbox-only, test-pinned); the remaining related defects are fixed here.

- **fix(model): `mergeBodiesAround` no longer invents a diameter.** Deleting a component
  between two bodies merged them with `diaMm = max(A, B)`, silently rewriting a stored
  diameter. Now fragments merge only when their diameters already agree (< 0.001 mm);
  otherwise both bodies stay and the freed span auto-fills. The split→merge round trip
  still restores one body — split fragments inherit the parent Ø verbatim.
  (`ShaftSpecExtensions.kt`; `BodySplitMergeTest` updated + 2 new cases.)
- **fix(resolve): trimmed body fragments get unique resolved ids.** All fragments of a
  stored body trimmed around a taper/thread/liner reused `body.id`, producing duplicate
  `HorizontalPager` keys (a Lazy-layout crash) and duplicate `explicitIndex` rows —
  reachable today via a keyway-bearing body (never split on add) with a component over it.
  Fragment #1 keeps the stored id (wear-pit / runout / selection references intact);
  later fragments get `"<id>#2"`, `"#3"`, … with `resolvedBodyBaseId()` for spec lookups.
  Carousel maps rows via base id (`buildCarouselRows`, now pure + tested); `RunoutRoute`
  strips the suffix so runout station keys are byte-identical to before.
  (`ResolvedComponent.kt`, `ComponentCarousel.kt`, `RunoutRoute.kt`; new
  `BodyFragmentIdTest` (6), `CarouselRowMappingTest` (4).)
- **fix(ui): keyboard no longer covers the focused field.** Under edge-to-edge the IME
  arrives as insets, and `imePadding()` sat *after* `verticalScroll` in the editor column —
  padding the content, never shrinking the viewport, so Compose's keep-focused-child-in-view
  had nothing to do. `imePadding()` now precedes `verticalScroll` in `ShaftScreen` (and is
  added to `LinerWearDetail`, which had none), so the viewport shrinks and the focused
  field auto-scrolls into view — including fields at the bottom of carousel cards. No
  change to `NumericInputField`, `CAROUSEL_HEIGHT`, or the FAB insets. Contract updated in
  `ShaftScreen.md`.
- **docs: investigation/writeup cleanup.** Removed 7 dated one-off reports (deep audit,
  cleanup/doc sweeps, loop/night-run logs, build log, instrumentation verification — all
  in git history) and scrubbed dangling references; open items they contained were moved
  into `TODO.md` (§2.1, §2.2, §2.3, §4.1b, §6). Kept: incident/proposal docs that contract
  docs cite, and the two analyses awaiting product decisions.

**Data advisory (no code change):** drawings **saved by builds distributed 2026-07-21 →
2026-07-26** may carry auto-promoted explicit bodies (this report), or a spuriously pinned
bare-shaft Ø from the one-day composition-commit window. Neither is auto-repairable — a
promoted/pinned value is indistinguishable from a deliberate one. Manual repair: uncheck
**"Explicit body"** on each wrongly-explicit card (auto-fill regenerates), then enter `0`
in any auto-body Ø field to restore derive-from-neighbors. Tell for a pinned Ø: change a
neighboring body's Ø and see whether the bare-shaft spans follow.

Suite: 796 → 808 tests, all green.

---

## 2026-07-26 (night)

### test: instrumentation burndown (TODO §5.2) + JVM Compose harness

Closes the testable half of the §5.2 instrumentation backlog and adds the harness the rest
needs. Verification of what was already implemented is in
`docs/Instrumentation_Verification_2026-07-26.md`. Suite: 719 → 796 tests, all green.

- **fix(input): `NumericInputField` fired `onCommit` on every composition.** Found by the
  new Compose test. Compose delivers an initial `onFocusChanged` with `isFocused = false`
  when the modifier attaches; the blur branch treated that null focus-baseline as "commit
  defensively" and fired. The spurious commit was absorbed by the dirty gate and undo
  history (both compare state) wherever the commit was a genuine no-op. Two places it was
  not:
  - **Auto-body card (the serious one).** Its Ø field commits to
    `ShaftSpec.autoBodyDiaMm` while displaying the *resolved* Ø — which, when
    `autoBodyDiaMm == 0`, is the value derived from neighbors. So composing the card
    wrote that derived value back as an explicit override, flipping the bare-shaft Ø from
    derived to **pinned** (an override > 0 wins over neighbor derivation). That is a real
    `ShaftSpec` change: it marks a freshly-opened document dirty with no user edit and
    records an undo entry, and the auto spans stop tracking their neighbors afterward.
  - **Explicit-body card.** `rememberBodyDefaults` is not state-compared, so composing the
    card set `sessionAddDefaults.bodyLenMm` to that body's length, which prefills Length in
    the next tap-to-add Add Body dialog (`ShaftScreen.kt:765`). `HorizontalPager` composes
    pages ahead of the visible one, so the winning card need not have been on screen.
    (`bodyDiaMm` is written too but never read — the dialog's Ø comes from
    `rememberAddDialogDefaults`.)

  `shouldCommitOnBlur` now requires a focus baseline, which covers every
  `NumericInputField` call site at once. Contract in `NumberField.md` unchanged in intent;
  this makes the code match it.
- **Robolectric harness** — Compose UI tests now run on the JVM via `testDebugUnitTest`,
  no device or emulator. Added `robolectric` + `testImplementation` of `ui-test-junit4`
  and `testOptions { unitTests { isIncludeAndroidResources = true } }`. Replaces the
  androidTest route that produced the rotted `EditorTopBarExportPdfTest` deleted earlier
  today; tests assert against `testTag`s, not composable parameter lists.
- **Pure extractions** (behavior-preserving), so the regression-prone logic is testable
  without a UI harness at all:
  - `ui/input/BlurCommitPolicy.kt` — `shouldCommitOnBlur`, out of the `onFocusChanged` lambda.
  - `ui/screen/CarouselSelectionSync.kt` — `carouselTargetIndex`, `shouldAnimateToSelection`,
    `isUserInitiatedScroll`, `shouldAdoptSwipeSelection`, out of the two carousel
    `LaunchedEffect`s.
  - `ui/screen/AddDialogGates.kt` — the five Add-dialog confirm-button `ok` expressions.
  - `ui/viewmodel/SnapUtils.kt` — `snapToleranceMm`, `snapRawPositionMm(raw, spec, unit)`,
    `gapToNextAnchorMm(spec, …)`; the `ShaftViewModel` methods now delegate, and the snap
    tolerance constants moved out of the VM companion.
- **New tests (77)**: `BlurCommitPolicyTest` (8), `NumericInputFieldBlurTest` (7,
  Robolectric/Compose), `CarouselSelectionSyncTest` (16), `AddDialogGatesTest` (22),
  `TapAddPositionTest` (17), `ShaftLayoutMappingTest` (7).
- **Verified, not changed**: "carousel scrolls to selected after preview tap" works
  end-to-end (on-device report confirmed in code) — it was unchecked only because §5.2
  tracks tests, not features.

---

## 2026-07-26 (evening)

### chore(cleanup): Wave 2 deletion pass — verified-dead code removed (no behavior change)

Executes "Wave 2" of `docs/cleanup_sweep_2026-07-11.md` (C-2 delete-only items + C-7
dead model items). Every candidate was re-verified against current code before
deletion (two later sweeps had already taken some, and the 2026-07-26 undo/redo rework
had already deleted the entire old delete-undo subsystem). Full JVM suite green.

- **Files deleted**: `model/RendererAliases.kt`, `model/TaperDirection.kt` (shadowed by
  the private enum in `TaperTitles.kt`), `model/ShaftSpecMigrations.kt`,
  `ui/drawing/ReferenceEnd.kt`, `ui/screen/ComponentType.kt`.
- **RenderOptions** pruned 40 → 15 fields: grid/legend block (GridRenderer hardcodes
  its own values), `textSizePx`, `showCenterline`, `referenceEnd` (+ both dead
  `ReferenceEnd` enums), the removed edge-ring highlight params
  (`highlightEdgeColor/Alpha/ExtraPx`), and the unreachable `ThreadStyle.UNIFIED` path
  (enum + `drawUnifiedThread` + `threadStyle`/`threadUseHatchColor`/`threadStrokePx`) —
  every construction site already forced HATCH. Unused `textMeasurer` param dropped
  from `ShaftRenderer.draw` (and the now-unneeded `rememberTextMeasurer()` in
  ShaftDrawing/WearRoute).
- **ViewModel**: deleted dead `newShaft()`, the write-only `didRestoreAutosave`
  flag/getter/consumer, the caller-less `unlockAchievement(Definition)` overload, the
  dead-end `updateCouplerBoltSlotLabel` (its 5-hop UI plumbing chain was never
  invoked; the slot card has no title editor — noted in `docs/CouplerBoltSlot.md`),
  and `updateTaper`'s no-op self-copies.
- **Screens**: dead callback plumbing removed end-to-end (`onSetUnit`, `onToggleGrid`,
  `unitLocked` param, `onMoveComponentUp/Down` screen params, `onNavigateHome` in
  ShaftRoute, `onExportRunout`/`onExportWear` chains — both routes export locally via
  SAF), the never-read `highlightId` local, `ExpandableSection` +
  `clickableWithoutRipple`, and ~30 unused imports in `ShaftScreen.kt`.
- **PDF**: `DimSpan.labelBottom` (never non-null) + its render branch and
  `textBelowDy`/`minGap`/`lineAdvance` ctor params in `PdfDimensionRenderer`; dead
  `computeBodyOnlyPtPerMm` (test retargeted at the live `computeDetailPtPerMm`),
  `computePdfPtPerMmFitAxes`, `fmtLen`, `hasAftTaper`/`hasFwdTaper`, `textSmall`;
  `drawRunoutHeader`'s unused `spec`/`unit`/`oalMm` params + stale "OAL in header" KDoc.
- **Model/util**: `AddDefaultsConfig` reduced to the inch presets + live `BODY_DIA_MM`
  (ten unit-aware helpers, `*_MM` twins, `TAPER_RATIO` deleted; `docs/Defaults.md`
  v1.3), `Threads.hasPitch` (test-only; 3 tests removed), `Liner.startMmPhysical`
  getter (serialization unaffected — `@SerialName` carries the JSON key), dead elvis in
  `Threads.normalized()`, `filterDecimalPermissive`, deprecated
  `InternalStorage.normalizeJsonName` alias.
- **Deliberately NOT deleted** (open product decisions): the `componentOrder`
  subsystem incl. `moveComponentUp/Down` (ComponentsOrdering.md contract question;
  order participates in undo snapshots), `ui/theme` (theme-wiring decision), the
  `ui/util/*Naming.kt` shims (repoint, don't delete — Wave 3).

### docs: taper orientation discrepancy analysis (TODO §2.3)

- Investigation written to `docs/TaperOrientation_Analysis_2026-07-26.md` — no code
  change. Finding: three SET/LET conventions coexist (Add dialog keys the swap on the
  measure-from toggle; derivation/labels key on the midpoint half; keyway placement
  keys on diameter magnitude), so a taper added into the opposite half from its
  measure-from direction stores SET at the wrong face — drawn backwards, card labels
  swapped. Also: the Add path drops the FWD measuring reference (`authoredReference`
  never set). Includes a 2-minute on-device repro recipe and recommended fixes
  (re-key the dialog swap on the physical half + persist `authoredReference`), with a
  data-repair option left as a product decision.

### docs: multi-shaft-per-job feasibility plan

- `docs/MultiShaftJob_Plan_2026-07-26.md` — architecture assessment for "two shafts
  under one job number, selectable". Recommends derived job grouping over the existing
  one-shaft-per-file format (no format change, no manifest) + an "Add shaft to this
  job" flow and an in-editor sibling quick-switch; flags the pre-existing runout/wear
  export-filename collision (missing position suffix) as Phase 0.

## 2026-07-26

### fix(warnings): removed the taper-vs-body Ø mismatch advisory

- Removed the "Ø differs from adjacent body by >10%" carousel-card warning
  (`hasTaperBodyMismatch` + helpers in `ui/util/ComponentWarnings.kt`). Despite the
  2026-07-25 orientation fix it still misfired on-device for a FWD taper whose LET
  correctly matched the adjacent body Ø (the Add-dialog and carousel-edit storage
  paths still disagree on SET/LET ordering — open item in `TODO.md` §2.3), and the
  mismatch is visible in the drawing itself. Removed by user request rather than
  re-fixed. Taper cards keep the very-short-segment advisory; body-step and liner-OD
  warnings are unchanged. Regression test pins the no-warning behavior
  (`ComponentWarningsTest.kt`); docs updated (`docs/VALIDATION_RULES.md` §3.3).

### fix(drafts): saved documents no longer linger as "Untitled draft" + editor document title bar

- **Bug**: saving a draft (including confirming an overwrite in the Save dialog) left
  its entry in the StartScreen **Unsaved drafts** list, showing as a stale
  "Untitled draft" row even though the save succeeded. Root cause:
  `markDocumentSaved()` only reseated the dirty baseline; the draft-ring removal lived
  solely in the autosave observer's dirty→clean branch, which only runs on the *next*
  state emission — save-then-navigate-home never produces one.
- **Fix**: `markDocumentSaved()` now removes the session's draft-ring entry
  immediately (gated on `draftPersisted`; `newDocument()`/`importJson()` clear that
  flag first, so open/new still keeps the previous session's safety-net draft — the
  "Don't save" path is unchanged). Existing stale rows clear on their next
  continue-and-save or discard.
- **feat(editor)**: document title strip above the editor action bar
  (`testTag("editor_document_title")`), desktop-editor style: the saved file name
  (extension stripped) or "Untitled draft", with a trailing ` *` while there are
  unsaved changes — a live saved-vs-draft indicator. Backed by a new reactive
  `ShaftViewModel.hasUnsavedChanges` flow sharing the exact full-snapshot comparison
  used by the autosave dirty gate (`_savedSnapshot` var → `MutableStateFlow`, session
  snapshot combine hoisted and shared with the observer).
- Docs: `ShaftViewModel.md`, `ShaftScreen.md`, `Persistence.md`.

### fix(ci): version auto-increment — shallow clone froze every build at 1.3.321 (321)

- **Bug**: `versionCode = versionBase(320) + git rev-list --count HEAD`, but the
  distribute workflow's `actions/checkout@v4` step used the default **shallow clone
  (depth 1)**, so the commit count was always 1 in CI. Every Firebase App Distribution
  upload — each a genuinely newer build — was labelled the duplicate version
  **1.3.321 (321)**. (The `versionBase` floor added earlier was a workaround for this
  same symptom without fixing the cause.)
- **Fix**: `fetch-depth: 0` on the checkout step (`.github/workflows/distribute.yml`),
  so CI sees full history and the version now increments automatically with every
  commit. First post-fix build jumps forward to ~1.3.634 — installed devices update
  normally.
- **Guard**: `app/build.gradle.kts` now fails the build (`GradleException`) when
  `CI=true` and `git rev-parse --is-shallow-repository` reports true, so a future
  shallow checkout breaks loudly instead of silently uploading duplicates again.

### feat(pdf): blank drafts (write-in mode) + direct print for all three documents

- **Blank draft (write-in) mode** for the schematic, runout sheet, and wear document:
  the full drawing, dimension lines, and form layout print unchanged, but every VALUE
  (dimension numbers, Ø callouts, footer specs, job info, date, OAL, TIR readings,
  recorded wear) is replaced by a writable blank — reuse a similar shaft's layout on a
  new job, or stock blank forms for phone-free inspections. On-device request.
  - Dimension lines still cut their in-line break (fixed `BLANK_DIM_GAP_PT` width) with
    no text — the gap is the write-in spot. Footer/header labels get writing rules
    (`pdf/BlankFormText.kt`); the STBD/PORT stamp becomes a `Side:` rule.
  - Schematic footer lines space out for handwriting (`FOOTER_LINE_FACTOR_BLANK` 1.8 vs
    1.35) and the footer band grows (150 pt vs 96 pt).
  - Runout keeps its bubbles (they are the write-in circles) but drops recorded TIR
    values/high spots; the TIR-direction line always prints as a fill-in blank. Wear
    prints as a fresh form (no recorded bands/pits/strips). Render-only — recorded data
    is never modified.
  - Toggles: PDF preview options sheet (schematic, session-only VM state — deliberately
    not persisted) and on the runout/wear screens. Blank exports get a `_BlankDraft`
    filename suffix.
- **Direct print** (`util/PdfPrint.kt`): Print buttons on the PDF preview top bar and
  the runout/wear screens hand the exact same composer call to the Android print
  framework (US Letter landscape) — no export-to-PDF-then-open-then-print detour.
- Tests: `BlankDraftFooterTest` (labels kept in order, zero digits, body-KW label,
  standard output unchanged). Docs: `docs/PDF_EXPORT.md` §5.5.

### test: Export-PDF gate extracted to pure logic; stale androidTest removed

- `EditorTopBarExportPdfTest` (androidTest) had rotted — its `ShaftScreen` call site was
  dozens of parameters behind and broke androidTest compilation. The behavior it covered
  is worth keeping, so the enable/message logic moved to `ui/util/ExportPdfGate.kt`
  (pure) and is now JVM-tested in `ExportPdfGateTest` (no-components message,
  enabled-with-component, collision message, slots-only stays disabled). The stale
  androidTest is deleted; `:app:compileDebugAndroidTestKotlin` compiles again.

### feat(ui): auto-body Ø editable — single bare-shaft diameter for all auto spans

- Auto-body carousel cards unlock the **Ø** field (Start/Length stay disabled/derived;
  positioning remains automatic unless the body is made explicit). On-device request.
- Typing a Ø sets `ShaftSpec.autoBodyDiaMm` — one user-set bare-shaft diameter shared by
  **all** auto spans (the shaft between components is one piece of stock). It wins over
  neighbor derivation (`resolveAutoBodyDia`) and the `normalizeBodies` diameter-continuity
  carry; 0/legacy docs keep the fully derived behavior. Editing Ø does **not** promote
  the card — the explicit-body checkbox stays the sole promotion path.
- New `ShaftViewModel.setAutoBodyDiaMm`; spec field is back-compat (defaults 0, rides the
  existing spec serialization, undo/redo via `EditState` for free).
- Tests: `AutoBodyDiaOverrideTest` (override on all spans, wins over neighbor Ø, unset
  keeps derivation, positioning untouched, JSON roundtrip + legacy decode).

### fix(pdf): nested FWD-anchored datum spans no longer cross lower dimension lines

- **Bug** (on-device report): with liners measured from FWD, the datum to the aft liner
  (57⅛″) landed on a lower rail than the nested datum to the fwd liner (22⅛″), so the
  22⅛″ span's extension line cut straight through the 57⅛″ dimension line.
- Root cause: `DeterministicTierAssigner` sorted DATUM spans by start ascending. FWD
  chains share their **end** (the FWD SET), not their start, so in AUTO tiering mode
  (raw x-space) the *containing* span sorted first and took the lower rail.
- Fix: DATUM spans now sort by **length ascending** (then start), so nested chains stack
  inner→outer whichever end they share. Shorter-first guarantees a containing span always
  lands above a contained one (any tier blocked for the inner is blocked for the outer).
- Regression tests: nested FWD-datum ordering + a full repro of the on-device shaft with
  a no-extension-line-crossing invariant check (`DeterministicTierAssignerTest`).

### fix(ui): explicit-body checkbox no longer jumps on promotion

- On the auto-body carousel card, the "Explicit body" checkbox row now sits **above**
  the greyed Start/Length/Ø fields — the same position it has on the explicit-body
  card — so it no longer jumps from below the fields to above them when checked
  (on-device report: "disorienting"). Behavior unchanged; `ComponentCarousel.kt`.

### feat(editor): session-wide undo/redo — every drawing edit undoable (50 steps, burst coalescing)

- **Replaces the old delete-only undo.** Every drawing-editor edit — not just
  component deletes — is now undoable: geometry (spec), wear record (spots + pits),
  runout readings, component order, and OAL manual/auto mode. Metadata
  (customer/vessel/job number/notes/shaft position/unit) is deliberately excluded —
  not undoable.
- New `ui/viewmodel/SessionHistory.kt`: a generic, pure undo/redo history over any
  snapshot type. Edits within a 600 ms window coalesce into one undo step (a typing
  burst = one step), the stack caps at 50 (oldest evicted on overflow), redo clears
  on any genuine new state, and an identical-to-head state is a no-op.
- New `ui/viewmodel/EditState.kt`: the undoable snapshot (spec + wearRecord +
  runoutReadings + componentOrder + overallIsManual).
- `ShaftViewModel` records a central `EditState` snapshot off the combined flows
  (guarded by `isRestoringHistory` so applying an undo/redo doesn't re-record
  itself); new `undoEdit()`/`redoEdit()` + `canUndo`/`canRedo` `StateFlow`s. History
  is cleared at every session boundary (`newShaft`/`newDocument`/`importJson`/
  `continueDraft`/autosave auto-restore) so undo can never cross into another
  document's state.
- **Old delete-only machinery fully removed**: `LastDeleted`,
  `deleteHistory`/`redoHistory`, `isRedoing`, `canUndoDeletes`/`canRedoDeletes`,
  `undoLastDelete()`/`redoLastDelete()`, `clearDeleteHistory()`. `removeX()` methods
  simplified (body-merge behavior preserved). The delete snackbar's "Undo" action and
  the header-row `HistoryMenu` ("Undo"/"Redo") both now call the general
  `undoEdit()`/`redoEdit()`.
- 13 new/migrated tests: `SessionHistoryTest` (8), `ShaftViewModelUndoRedoTest` (3),
  `ShaftViewModelRemoveTest` migrated (+2 delete-undo-via-`undoEdit` recovery tests);
  full suite green.

---

## 2026-07-25

### feat(ui): explicit-body checkbox — greyed auto-body fields, checkbox-only promotion, confirmed demotion

- Auto-body cards (`ResolvedComponentSource.AUTO`) now show Start/Length/Ø as **disabled**
  (greyed, derived-value) fields; the field-edit promotion path (`promoteIfNeeded`) is removed.
- The sole promotion path is the "Explicit body" checkbox (relabeled from "Make editable
  body", unchecked on the auto card) — checking it calls `onAddBody` with the auto-body's
  current derived dims, guarded by a once-only `promoted` state.
- Explicit-body cards carry the same checkbox, checked. Unchecking opens a "Make body
  automatic?" confirmation dialog (extra sentence when the body has a keyway, warning it
  will be lost); confirming demotes via the existing `onRemoveBody(b.id)` pipeline, which
  the resolve layer regenerates as an auto-fill span. Cancel keeps the body explicit.
- `testTag`s: `body_explicit_checkbox`, `body_demote_confirm`. `ComponentCarousel.kt`.

### fix(validation): taper-vs-body warning reads the physical face diameter (FWD-taper false positive)

- **Bug** (on-device report): a FWD-end taper with LET matching an abutting body's Ø still
  warned "Ø differs from adjacent body by >10%". The Add-taper path always stores
  `startDiaMm = SET` regardless of shaft half, while the carousel edit path stores the pair
  x-ordered — the naive face mapping read the SET, not the LET, at the body-adjacent face.
- **Fix**: new private helper `taperFaceDiametersMm(taper, overallLengthMm)` in
  `ComponentWarnings.kt` derives the physical (AFT face, FWD face) diameters — SET/LET by
  magnitude, placed by the same SET-by-shaft-half convention as
  `ShaftViewModel.taperSmallEndAtStart` — and `hasTaperBodyMismatch` now compares against
  those instead of the raw stored fields. 4 regression tests added (FWD/AFT ×
  matching/mismatching) in `ComponentWarningsTest.kt`. Thresholds unchanged.
- **Follow-up logged, not fixed**: `ShaftRenderer.kt` draws the taper trapezoid strictly
  x-ordered (`startDiaMm` at the AFT face unconditionally), so an Add-path-created,
  never-edited FWD taper may **render** with its small end at the body face even though the
  SET/LET-by-shaft-half convention says otherwise — a storage-order discrepancy between the
  two Add/edit paths and the renderer that needs an on-device check and a canonical-order
  decision. Tracked in `TODO.md` §2.3.

### fix(autosave): draft history — dirty-gated 3-slot ring replaces single always-overwriting slot

- **Root cause**: the single-slot autosave (`autosave_last_session`) wrote unconditionally
  every 1.5 s debounce tick, including the write triggered by *loading* a document, so
  reopening an edited-but-never-saved shaft let the pristine reload silently overwrite the
  only copy of the edits within seconds. See `docs/Autosave_Incident_2026-07-25.md`.
- **Dirty gate**: the autosave observer now writes a draft only when the live session
  differs from the last saved/loaded baseline (`DraftRing.shouldWriteDraft`); a
  freshly-loaded pristine document can never clobber anything again, and the entry is
  removed on the dirty→clean transition (explicit save).
- **3-entry ring, per-document identity**: `data/AutosaveManager.kt` v2 replaces the
  single DataStore slot with `autosave_drafts`, a ring of up to 3 `DraftEntry` records
  keyed by a per-session `draftId` minted on `newDocument()`/`importJson()`, so editing
  one document can never touch another's draft. New pure `data/DraftRing.kt`
  (`upsertDraft`, `shouldWriteDraft`).
- **StartScreen drafts list**: the single "Continue Draft/Discard Draft" pair is replaced
  by an "Unsaved drafts" card listing up to 3 (name or "Untitled draft", relative age,
  tap to continue, X icon → confirm discard).
- **Legacy migration**: any existing single-slot draft is transparently wrapped into the
  new ring on first read, then the old key is removed.
- **Full-snapshot `hasUnsavedWork()`**: the editor's unsaved-changes check now reuses the
  same full-session comparison as the autosave dirty gate (`shouldWriteDraft`), so wear
  records, runout readings/config, shaft position, unit-lock, and OAL mode all count as
  unsaved work — previously only the spec + 4 metadata fields did, which is why the
  incident's wear-mark edits went unguarded. Legacy per-field `_savedSpec`/`_savedJobNumber`/
  `_savedCustomer`/`_savedVessel`/`_savedNotes` fields removed.
- **Universal unsaved-changes guard**: a single `runGuarded` + shared `UnsavedChangesDialog`
  at NavHost scope now gates every session-replacing action (Start's New/Open/Open-recent,
  editor New/Open, Close Document) — "Save" reuses the save-then-continue flow from
  anywhere, "Don't save" leaves the draft-ring entry intact as the safety net.
- **Close Document**: new editor overflow-menu item — closes cleanly to Start when the
  session is clean, otherwise routes through the same guard.
- 19 new tests (`DraftRingTest` × 12, `AutosaveDraftSerializationTest` × 3,
  `ShaftViewModelUnsavedWorkTest` × 4); full suite green (697).

---

## 2026-07-24

### refactor: extract ShaftPreviewPanel.kt + ShaftScreenController.kt from ShaftScreen.kt

- **`ui/screen/ShaftPreviewPanel.kt`** (189 lines): `PreviewCard` (now `internal`),
  `PreviewOalBadge` (private), `FreeToEndBadge` (private) + its `lastOccupiedEndMm`
  wrapper (internal) — moved verbatim from `ShaftScreen.kt`.
- **`ui/screen/ShaftScreenController.kt`** (107 lines): `AddDefaults` +
  `computeAddDefaults` (internal), `applySnapped{Body,Taper,Thread,Liner}Update`
  (internal), `snapBounds` (private) — moved verbatim from `ShaftScreen.kt`.
- Pure code-motion, zero behavior change, full unit test suite green. `ShaftScreen.kt`
  1452 → 1314 lines. Shared format helpers (`abbr`, `disp`, `formatDisplay`,
  `toMmOrNull`, `parseFractionOrDecimal`, `tpiToPitchMm`) and the dialogs/menus stay in
  `ShaftScreen.kt`. The fuller "controller owns all VM-side intents, composables
  stateless" redesign remains open (`TODO.md` §1).

### chore: dead-code sweep — snap-era leftovers, dead imports, tiering audit

Read-only audit + deletion pass targeting zero-production-reference code; full unit test suite
stayed green throughout.

**Deleted:**
- `ShaftSpec.findRightNeighbor` (`model/ShaftSpecExtensions.kt`) — fully dead, no references
  anywhere.
- Snap-era test-only helpers superseded when the per-update snap cascade was removed
  pre-auto-body: `snapForwardFromOrdered`, `snapFromOrigin`, `shiftAllBy`, `findLeftNeighbor`
  (`ShaftSpecExtensions.kt`), plus their 4 test cases in `ShaftSpecSnapExtensionsTest.kt`. Live
  `snapForwardFrom` (still used by `ShaftViewModel.kt`) is untouched.
- Dead imports: `threadWarningMessage` in `ShaftScreen.kt`, `snapForwardFromOrdered` in
  `ShaftViewModel.kt`.
- Unused `kind` default parameter on `DeterministicTierAssigner.assign` (every caller already
  passed it explicitly; the parameter is now required).

**Kept deliberately (not dead code):**
- `ShaftSpec.validate()` — no production caller, but test-only and load-bearing in
  `SampleShaftAssetsTest` (bundled-sample sanity checks). `docs/VALIDATION_RULES.md`'s two stale
  "dead code" claims (§1.1, §3.1) corrected to "test-only".
- The unreachable `geom.SpanKind.OAL` defensive path in `DeterministicTierAssigner` — documents
  the OAL-never-tiers contract; removal would be a coordinated API change, not a dead-code
  delete.
- `ui.screen.parseFractionOrDecimal`/`toMmOrNull` vs `util.Parsing` duplication — both live;
  consolidation is already tracked in `NumberField.md`.

Docs: `docs/VALIDATION_RULES.md` §1.1/§3.1, `TODO.md` §4.1/§4.3. No production behavior changed.

### feat(validation): §3–4 non-blocking warning rules

Implemented the five warning rules in `docs/VALIDATION_RULES.md` §3–4 that were previously
marked "planned — not yet implemented", as pure functions in `ui/util/ComponentWarnings.kt`.
All are non-blocking and see only **stored** components — auto-bodies stay invisible to them,
and `blockingExportError()` (the PDF export gate) is untouched.

Carousel-card-level (wired into `ComponentCarousel.kt`, joined with `"; "` when a card has more
than one):
- **§3.2 body Ø step** — stored bodies abutting within `ADJACENCY_EPS_MM = 0.5f` mm, both
  diameters `> 0`, warn when `max/min diameter ratio > 1.5` (`BODY_STEP_WARN_RATIO`, strict —
  exactly `1.5` is silent).
- **§3.3 taper face vs abutting body** — warn when
  `|taperFaceDia − bodyDia| / bodyDia > 0.10` (`TAPER_BODY_MISMATCH_WARN_FRAC`, strict).
- **§3.5 liner OD vs overlapping body** — warn when `liner.odMm < body.diaMm − 0.001f` on a
  liner span that positively overlaps a stored body.

New signatures: `bodyWarningMessages(spec, body)`, `taperWarningMessages(spec, taper)`,
`linerWarningMessages(spec, liner)` — `threadWarningMessage(thread)` is unchanged.

Computed-only, awaiting a UI-surface decision — `specWarningMessages(spec)`:
- **§4.3 tiny segments** — count of stored components (bodies/tapers/liners/non-excluded
  threads) with length in `(0, 1] mm`.
- **§4.3 zero-body coverage** — flags when `spec.bodies` is empty but at least one
  taper/liner/non-excluded thread exists.

Thresholds (`ADJACENCY_EPS_MM`, `SHORT_SEGMENT_MM`, `BODY_STEP_WARN_RATIO`,
`TAPER_BODY_MISMATCH_WARN_FRAC`) are named constants chosen as engineering defaults, flagged
for user review. 24 new tests in `ComponentWarningsTest.kt`. Docs:
`docs/VALIDATION_RULES.md` §3.2/§3.3/§3.5/§4.3, `TODO.md` §2.2.

### fix(validation): free-to-end badge OAL=0 fallback + taper slope inert-length test lock-in

`FreeToEndBadge` (`ShaftScreen.kt`) now gets its value from a new pure helper,
`freeToEndSignedMm(spec)` (`ui/util/FreeToEndBadgeMath.kt`). When `overallLengthMm == 0`
(manual-OAL mode, no length entered yet) it falls back to `lastOccupiedEndMm()` — the same
fallback the preview's `safeSpec` uses — so the badge reads `0` instead of a phantom red
negative "oversized" value. A genuinely oversized shaft (OAL > 0, components running past
the end) still goes negative/red as before; suppression and visibility rules are unchanged.
Covered by new `FreeToEndBadgeMathTest.kt` (5 cases).

Also confirmed and pinned with tests (no production code changed) that taper slope
validation/derivation — `autoTaperRate`, `manualTaperRateWarning`,
`manualTaperRateBlockingMessage`'s derive-prompt, and `ShaftViewModel.deriveTaperDiameters`
— is already inert at `lengthMm <= 0`; pure-syntax checks like the ambiguous bare `"1"`
correctly still fire regardless of length. New pinning tests in `TaperRateAutoTest.kt` and
`TaperRateTest.kt`. Docs: `docs/FreeToEndBadge.md` (v1.3), `docs/VALIDATION_RULES.md` §3.3,
`TODO.md` §2.1.

### docs: refresh TODO.md — sync shipped work, bump "Last updated"

`TODO.md` had drifted behind several merged features. Body keyways are un-shelved (removed
from the non-goals list) since they shipped; added shipped rows for the runout bubble editor,
spooned keyways, diameter callouts, dim-value-in-break, and the spooned-KW footer note.
"Last updated" bumped to 2026-07-24. No code changes.

### feat: spooned-keyway footer note

When a keyway is spooned, the schematic PDF footer now prints a reader note directly under
that keyway's spec line — `KW length to base of spoon (mill end)` — so a reader knows the
stated KW length runs to the base of the spoon bowl (where the mill cut ends), not to the tip
of the spoon. Applies to taper and body keyways (`SPOONED_KW_NOTE` in
`buildFooterEndColumns`, `ShaftPdfComposer.kt`); non-spooned keyways are unchanged. Covered
by `SpoonedKeywayFooterNoteTest`.

---

## 2026-07-22

### fix: runout bubble values keep three decimals (thousandths)

`formatRunoutValue` now shows a **fixed 3 decimal places in both units with trailing zeros
kept**, so a TIR reading of `.010` stays `.010` instead of shrinking to `.01`. Previously
inches used 4 dp and both units stripped trailing zeros, which made bubble values read at
inconsistent widths. The leading-zero drop (`0.010 → .010`) and thousandths resolution match
how a machinist hand-writes a TIR reading. Shared by the bubble dialog, on-screen preview, and
PDF composer, so every surface renders identically. Docs: `docs/RunoutSheet.md`.

### feat: dimension values now seated in a break in the dimension line

Schematic PDF dimension lines (`PdfDimensionRenderer`) draw their value **inside a break
in the line** — the drafting convention `|←—— 237 1/2" ——→|` — instead of floating above a
continuous line:

- **Inline path.** The main dimension line is drawn as two stubs with the value centered in
  the gap between them, vertically centered on the line. Eligibility reuses the existing
  `canFitInwardArrows` predicate, so an inline span always gets inward-pointing arrows that
  line up with the value.
- **Fallback path.** Short spans, and any label that would collide with one already placed
  on that rail, revert to the original look: continuous line, label floating above with the
  existing bump-on-collision loop, and outward arrows.
- **Top OAL rail included** — `drawTop`/`drawOnRail` share the same `drawSpan`, so the top
  OAL line gets the same break treatment as the numbered component rails.
- **PDF + preview, no canvas twin.** Applies to both the exported PDF and the on-screen PDF
  preview (the preview rasterizes the real PDF through the same composer/renderer path).
  The on-screen schematic canvas has no horizontal dimension rails, so there's no
  draw-both-sites counterpart to update. Docs: `docs/PDF_EXPORT.md` §5.4.

### feat: schematic diameter callouts — below the shaft, 3-decimal, two-tier

On-shaft Ø callouts on the schematic PDF were cleaned up per shop-print convention:

- **Three decimals, not four.** The diameter label now uses the footer's `formatDiaWithUnit`
  (≤3 decimals / thousandths, trailing zeros trimmed) instead of the old raw `%.4f`, so a Ø5"
  body reads `Ø 5"` — identical to the footer's "Body:" line.
- **Moved below the shaft.** Body OD callouts no longer alternate above/below; every callout —
  body and liner — hangs below the shaft where there's clear room.
- **Liners now get callouts too.** New `buildLinerOdCallouts` mirrors `buildBodyOdCallouts`: one
  label per unique OD, anchored at the center of the longest segment carrying it. Same-size
  segments collapse to a single label; differing sizes each get their own. Bodies and liners are
  **separate OD groups** — a Ø5" liner and a Ø5" body produce two independent callouts.
- **Overlap check / two-level writing.** New pure, JVM-tested `geom/DiameterCalloutLayout.kt`
  (`assignTiers` — greedy interval coloring, `MAX_TIERS = 2`, `MIN_GAP = 4f`) bumps
  horizontally-close labels onto a second row, the same two-tier posture as the runout bubbles.
  Body and liner labels are tiered together so a liner label never crashes into a body label.
  `DiameterLeaderRenderer` measures label widths, reads back the tier, and draws at
  `shaftBottomY + leaderRise + tier × tierStep`.
- **Tests:** new `geom/DiameterCalloutLayoutTest.kt` (tier math), new `pdf/LinerOdCalloutsTest.kt`,
  and `pdf/BodyOdCalloutsTest.kt` updated to the all-BELOW behavior. Docs: `docs/PDF_EXPORT.md` §5.3.

### feat: wear document — dimensions above the shaft, vertical wear marks

Two tweaks so the wear document reads like the hand-marked sheet:

- **Liner detail strips: rail and title swapped.** The chained dimension rail now draws **above**
  the liner cylinder (witness lines up, span labels stacking down toward it) and the liner
  title + "…FROM CPLG S.E.T." anchor draws **below** it. The min-Ø reading follows to just below
  each band, clear of the rail. `computeWearStripInnerLayout` reserves the rail's row budget at the
  top and the title (+ headroom) at the bottom now; its guarantee is
  `stripTop ≤ railY ≤ cylTop ≤ cylBottom ≤ stripBottom` (`WearStripLayout.kt`,
  `WearPdfComposer.kt`). Layout tests updated.
- **Main profile wear areas as vertical lines.** On the full-shaft profile the wear band is now
  filled with **vertical** strokes (`drawVerticalBand`) instead of diagonal hatch — same weight,
  alpha, and pitch, only the orientation changed, matching how the shop marks wear areas by hand.
  The broken-out detail strips keep the diagonal hatch.
- **Liner names on the main profile.** Each wear liner's name is now printed centered under its
  span on the main profile (`drawWearLinerNamesOnProfile`), sharing the row with the "← AFT / FWD →"
  labels (clamped clear of them). It's a lightweight reference tying each band to its broken-out
  strip — chosen over boxed zoom callouts, which don't scale to 3 liners. Uses the same name the
  strip title shows.

### feat: spooned keyways draw an enlarged circle at the closed end

`keywaySpooned` (already on `Taper`/`Body`, and already surfaced in the add-dialogs, carousel, and
PDF footer text) now actually **draws**. A spooned **open** keyway keeps the normal keyway —
full-length walls and the mill semicircle at the closed (LET) end — and **adds** an enlarged circle
around that end, matching the shop/CAD "spooned" convention. The mill semicircle stays as an inner
reference line inside the circle. Ignored for floating keyways (the toggle is already disabled there).

- **Shared math:** new `geom/KeywaySpoonMath.kt` — `keywaySpoonBowl(...)` resolves the circle
  (radius, centre, wall-tangent point, major-arc sweep). Two tunable drawing constants:
  `SPOON_BOWL_WIDTH_RATIO = 2.4` (diameter ÷ slot width) and `SPOON_BOWL_LET_SHIFT_RATIO = 0.5`
  (how far the centre sits back from the LET tip, so the mill end runs ~¾ through the circle). Pure,
  no `pdf → ui` dep — same posture as `WearPitMath`/`RunoutReadingMath`.
- **Draw sites:** the circle is drawn **identically** in both keyway draw sites —
  `ShaftRenderer.drawKeywaySlot` (canvas) and `ShaftPdfComposer.drawKeywaySlotPdf` (PDF) — as two
  additive draws (a white disc into the void fill, the major arc onto the outline); the non-spooned
  path is untouched.
- **Docs/tests:** CLAUDE.md invariant, corrected `Taper`/`Body` docstrings, new `KeywaySpoonMathTest`.

### feat: reset button on the runout shaft preview

The runout tab's live shaft/bubble preview gains a "Reset view" Refresh button (top-right), mirroring
the schematic drawing preview's control — it returns the pinch-zoom/pan to `scale 1 / offset 0`
(`RunoutRoute.kt`).

## 2026-07-21

### feat: AFT/FWD direction reference on the wear document + direction-aligned strip titles

So anyone in the shop can orient the printed wear sheet: "← AFT" and "FWD →" are drawn under the
shaft profile (`drawWearDirectionRef`, AFT left / FWD right — the SET/schematic convention). Each
detail strip's title is also aligned to match its measurement direction — a FWD-SET-referenced
strip right-aligns its title, an AFT-SET-referenced one left-aligns it (`linerAnchorForPdf`). (The
earlier AFT/FWD caption was added to the in-app detail *overlay*; this puts it on the exported PDF.)

### feat: rotate the runout & wear PDF previews to landscape

The runout and wear documents are landscape, but their in-app preview (`PdfPreviewOverlay`) was
letterboxed in the portrait-locked app. It now unlocks device rotation while open (restoring
portrait on dismiss), matching the schematic `PdfPreviewScreen` — turn the device sideways and the
`ContentScale.Fit` preview fills the width. Both routes share the one overlay, so this covers both.

### feat: wear pits (X markers) on bodies, tapers & liners; 2-column wear-strip grid

Digitizes the hand-drawn pit / dye-penetrant "X": tap a body, taper, or liner on the Wear tab
to open it enlarged, then mark pits as small or large X's. The X's print at true position on the
wear-document PDF. Fourth reference-only feature (like coupler bolt slots / wear spots / runout
readings). See the "Wear Pits" section of `docs/RunoutSheet.md`.

- **Model/persistence:** `WearPit`/`PitSize` in `WearRecord.pits` (`model/WearSpot.kt`) — stored in
  the **existing** `wear_record` envelope field, so no new autosave/snapshot/import plumbing.
  Reference-only (never affects OAL/coverage/body resolution/collision/Free-to-End). Keyed by the
  **resolved component id** so a pit can sit on a liner, taper, or body (explicit or auto); orphans
  are skipped at the render layer (auto-body/taper ids aren't known to the codec), same posture as
  runout readings. Stored as component-local `axialMm` (from the AFT edge) + a visual `acrossFrac`.
- **Shared math:** `geom/WearPitMath.kt` — X sizing (large arm = small × 2, a *symbol* size not the
  pit's true Ø), across-fraction clamping, and pit hit-testing. Drawn identically in all sites.
- **UI:** the Wear overview now tints **every** body/taper/liner as a tap target (badge = spots +
  pits). Tapping opens `ComponentWearDetailOverlay` (`ui/screen/LinerWearDetail.kt`, generalized from
  the liner-only overlay — a rect for body/liner, a trapezoid for a taper). Explicit **Add X /
  Remove X** tool chips + a **Clear all pits** button so a stray tap can't place or delete by
  accident; a **Small / Large** brush. A **← AFT / FWD →** caption under the drawn box shows the
  shaft-direction reference. Liners keep the full wear-band editor plus pits; bodies and tapers get
  pits only. `ShaftViewModel` gained `addWearPit`/`updateWearPitSize`/`removeWearPit`.
- **Wear PDF:** X's drawn at true axial + across position on the main profile (`drawWearPitsOnProfile`,
  taper Ø interpolated at the pit) and on the detail strips.
- **Wear PDF layout change:** the shaft profile is now **always drawn on top**. The old strips-only
  mode (3+ wear liners dropped the profile) is replaced by `WearPdfMode.GRID` — the detail strips lay
  out in a **2-column grid** (two side by side, third on the next row) so they take ~2 rows and the
  profile (with its body/taper pits) always stays visible. New `computeWearStripGridLayout` reuses the
  existing vertical-layout math by row count. Modes: 0 → `PROFILE_FORM`, 1 → `COMBINED`, 2+ → `GRID`.
- Tests: `geom/WearPitMathTest`, pit round-trip + orphan-survival cases in `WearRecordPersistenceTest`,
  grid-layout tests replacing the strips-only tests in `WearStripLayoutTest`. Suite green.

### fix: revert "non-negotiable bodies" — it flagged normal body-under-sleeve as collisions

The 2026-07-21 "explicit bodies are non-negotiable" change was wrong for real drafts. A body
legitimately runs under a liner (a sleeve over the shaft) and up against a taper; the resolve
layer trims the *drawn* body around those, but the collision check read the *raw stored*
bodies and flagged those normal overlaps as errors — labeling them by a stored-list index
("Overlaps Body 1") that didn't match any drawn card, and blocking PDF export.

- **Reverted:** bodies are out of `collidingIds()` again; removed the `bodyOverlapErrorMm` /
  `nonBodyOverlapErrorMm` hard-blocks from the Add dialogs and carousel start/length fields;
  removed the liner↔body boundary negotiation. No stored body data is touched — the fix is
  purely in the check, so every explicit body and typed value is preserved.
- **Kept (engine correctness):** `mergeBodiesAround` now **refuses to merge across a component
  still occupying the freed span** (that could manufacture a long phantom body on delete);
  and a **keyed body is never split** (light protection — stays one whole card, keyway intact;
  plain bodies split as before).
- **Kept (feature):** body keyways, the 180° hidden-line clocking, and the "Make editable
  body" checkbox all remain. Body/AddBody keyway inputs are now **gated behind a "Keyway"
  checkbox** so they only appear when turned on.
- Tests: reverted CollidingIds/StartOverlap body cases, replaced BodyCollisionTest, added
  merge-across-guard + keyed-split-skip tests; suite green (612).

### feat: interactive runout bubbles — tap to record TIR value + high-spot marker

Digitizes the hand-filled runout bubble. Tapping a bubble on the Runout tab opens a "zoom-in"
editor (`ui/screen/RunoutBubbleDialog.kt`) to record that station's TIR reading and high-spot
direction; both print on the preview and PDF export. Both are optional — a sheet still exports
blank. See `docs/RunoutBubbleEditor_PLAN.md` and the "Runout Bubble Editor" section of
`docs/RunoutSheet.md`.

- **Model/persistence:** `model/RunoutReading.kt` (`RunoutReading`/`RunoutReadings`) — reference-only
  (never affects OAL/coverage/collision/Free-to-End, like coupler bolt slots / wear spots). Additive
  `runout_readings` envelope field (no version bump); keyed by `(componentId, stationIndex)` with
  render-layer orphan handling. Wired through `ShaftViewModel` (`setRunoutReading`/`clearRunoutReading`,
  autosave combine, snapshot/import/export/new-doc) and `AutosaveManager.SessionSnapshot`.
- **High spot:** placed by tapping/dragging the ring; snaps to **30-minute clock ticks** (0–23,
  12 o'clock = 0, clockwise). Off-ring touches do nothing. Pure math in `geom/RunoutReadingMath.kt`
  (`snapToClockTick`, `bubbleAngleDeg`, `clockTickRimOffset`, `isOnRingBand`, `pickBubbleAt`).
- **Value:** canonical mm, entered/shown in the active unit (`util/formatRunoutValue`), parsed on Save.
- **Rendering (both draw sites, lockstep):** value centred in the circle + a short red high-spot
  **dash straddling the rim** (no radial line — keeps the value legible), in
  `RunoutRoute.drawRunoutMarkers` and `RunoutPdfComposer.drawPlacedBubbles` (`composeRunoutPdf`
  gained a `runoutReadings` param).
- **Keyway cutout:** the protruding square notch is replaced by an **open-topped keyway slot** cut into
  the top of the circle (top arc broken across the slot mouth; nothing protrudes past the rim), drawn
  identically on canvas and PDF.
- **Engine:** `stationIndex` added to `RunoutStationX`/`PlacedRunoutBubble` for stable bubble identity.
- **Bubble sizing (tuned from a printed sheet):** circle enlarged (`BUBBLE_RADIUS_PT` 20 → 23 ≈ 0.64 in
  dia; on-screen 6 → 7 dp), printed value text shrunk (`r * 0.85` → `r * 0.60`), and
  `formatRunoutValue` now drops the leading zero (`0.003 → .003`) — more room to hand-write the value.
- Tests: `RunoutReadingTest`, `RunoutReadingMathTest`, `RunoutValueFormatTest` (leading-zero + trim). Suite green.

### feat: explicit bodies are non-negotiable components (+ auto-body promotion, liner boundary negotiation)

Reworks the body model so an **explicit** body is a first-class, rigid component instead of
a fluid filler. Auto-bodies (derived, unstored) stay fluid and flow around everything as
before — they remain how you shape a shaft. Motivated by keyed line-shaft end bodies that
must never be fragmented.

- **Non-negotiable bodies:** explicit bodies now participate in `collidingIds()` (red card +
  blocked PDF export) against tapers, threads, liners, and other bodies. Adding or moving any
  component onto an explicit body is **hard-blocked** — `bodyOverlapErrorMm` /
  `nonBodyOverlapErrorMm` disable the Add button (all four Add dialogs) and the carousel
  start/length fields. A taper/thread/liner over an explicit body is refused; shape auto-body
  regions and lock a span to explicit (via Add Body or the new checkbox) when it's final.
  Because overlapping adds can't happen, explicit bodies are never split.
- **"Make editable body" checkbox:** the auto-body carousel card promotes the derived fill
  into a real, editable Body — the discoverable path to add a keyway to a line-shaft end span.
- **Liner ↔ body boundary negotiation:** editing a liner's length so it moves a shared edge
  with an abutting explicit body prompts to shorten the body (overlap) or grow it to fill
  (gap) — `linerBodyBoundaryAdjust` + `updateLinerWithBodyBoundary` apply both atomically.
  The "filling" of an auto-body, but confirmed, since explicit bodies never split.
- **Behavior change:** threaded/tapered ends now require a fluid auto-body core, not an
  explicit body (you can't overlap an explicit one). Existing collision/validation tests
  updated to the reversed rule.
- **Tests:** +16 (body collision, overlap validators, boundary math, start-validator
  reversal); suite 573 → 589 green.

## 2026-07-20

### feat: body keyways + "keyways 180° apart" note (fitted-coupling intermediate shafts)

Some intermediate shafts with fitted couplings end on a plain cylindrical body that
carries the coupling keyway — previously keyways were taper-only and documented as a
non-goal (reversed; ROADMAP/COMPONENT_CONTRACT/DATA_MODEL/VALIDATION_RULES updated).
Ending a shaft on a body already worked (coverage/auto-OAL treat bodies as first-class);
this adds the keyway the use case needs.

- **Model:** `Body` gains taper-style keyway fields (`keywayWidthMm/DepthMm/LengthMm`,
  `keywaySpooned`) referenced from an AFT/FWD end face (`keywayEnd` +
  `keywayOffsetFromEndMm`; 0 = open at the face, > 0 = floating). `Body.isValid`
  enforces offset + length ≤ body length. New `Body.hasKeyway`, `Body.keywayAbsSpanMm()`,
  `ShaftSpec.keywayCount()`. All additive + defaulted — no doc-format version bump.
- **Split/merge carry:** `carryBodyKeyway` keeps a body keyway at its **absolute**
  position across body split/merge/expansion (re-anchors the offset to the surviving
  fragment's face; drops the keyway if a cut passes through it).
- **180° apart:** spec-level `ShaftSpec.keyways180Apart`. Toggle appears on keyway-bearing
  cards/dialogs only when the shaft has (or would reach) ≥ 2 keyways; PDF prints "Keyways
  180° apart" in the footer middle column. **Hidden-line rendering:** the aft-most keyway
  (measurement datum) stays solid; every other keyway draws as a far-side hidden feature —
  dashed outline (6/4 px), no void fill — in both preview and PDF.
  `ShaftSpec.hiddenKeywayHostIds()` is the single classifier both surfaces consume.
- **UI:** explicit-body carousel card + `AddBodyDialog` gain the full keyway section
  (KW from AFT|FWD, W×D, L, offset, spooned) — dialog/card parity maintained; auto-body
  card intentionally unchanged (promote first). `AddTaperDialog` gains the 180° toggle.
- **Drawing:** preview + PDF keyway slot drawing generalized (`drawKeywaySlot` /
  `drawKeywaySlotPdf`) and drawn for keyed bodies from model geometry; PDF footer lists
  "Body KW: W × D × L" in the column matching the keyway's half of the shaft.
- **Auto-body → explicit:** the auto-body carousel card gains a **"Make editable body"**
  checkbox that promotes the derived fill into a real, editable Body (its current
  Start/Length/Ø) — the discoverable path to add a keyway to a line-shaft end span or lock
  a span in. Reuses the existing promotion; a field edit still promotes as before.
- **Tests:** +32 (model invariants/abs-span/carry, keyway 180° clocking classifier +
  taper abs-span, codec round-trip + legacy decode, footer columns); suite 541 → 573 green.

---

## 2026-07-18

### feat: wear PDF rendering modes — liners-only page at 3+ wear liners

Matches shop practice (wear doc shows the liner cutouts OR the shaft drawing, not
both stacked). Automatic three-way rule, no toggle (`determineWearPdfMode`,
`pdf/WearStripLayout.kt`): 0 wear liners → blank shaft-profile field form (unchanged);
1–2 → combined page as before (path untouched); **3+ → strips-only** — no shaft
profile or OAL dimension line (header + dye-pen/notes stay), strips grow into the
freed page (130 pt each at 3 strips, capped 216 pt) with extra inter-strip gap.
Orphaned spots count as zero. +14 tests (mode boundaries incl. 2→3, strips-only
layout bounds/cap/fill); suite 527 → 541 green.

### feat: liner wear areas — inspection recording on the Wear tab + PDF detail strips

Digitizes the shop-sketch liner wear workflow (`docs/LinerWearAreas_Proposal.md`; build
record: `docs/LinerWearAreas_BuildLog_2026-07-18.md`). Uncommitted decisions from the
proposal's §10 were resolved and are logged there.

- **Model/persistence:** `model/WearSpot.kt` (`WearSpot`/`WearRecord`) — reference-only
  (never affects OAL/coverage/collision), liner-local AFT-edge canonical coordinates,
  additive `wear_record` envelope field (no version bump), orphan spots dropped at
  decode, wired through autosave; backup needed no change (raw byte copies).
- **Wear tab:** interactive shaft canvas (resolved components), liners are tap targets
  with tint affordance + spot-count badges.
- **Detail overlay:** full-screen break-out liner (S-curve stubs, eye outward), hatched
  wear bands with per-spot dimension rails, editable spot cards (commit-on-blur).
- **Input spec (user):** per-spot "Measure From" — AFT SET / FWD SET / Liner AFT /
  Liner FWD (canonical storage unchanged; display projection only) — and **blocking
  in-span validation** (start or start+length outside the liner rejects the commit
  inline); stale overruns (liner shortened later) warn + render clamped, never block.
  Min-Ø stays optional (0 = not recorded); start + length required.
- **Wear PDF:** thin hatched bands on the main profile + up to 3 broken-out detail
  strips per page (auto-selected aft→fwd, overflow noted as "+N more"), each with
  common-factor radius scaling (preserves the liner-vs-shaft OD step), anchor-from-SET
  title, and a **chained dimension rail** (AFT edge → band start, band lengths, gaps,
  trailing remainder — provably tiles the liner; narrow-span label fallback).
- Tests 431 → 527 (`WearRecordPersistenceTest`, `ShaftViewModelWearSpotTest`,
  `AutosaveSnapshotWearRecordTest`, `LinerWearMathTest`, `WearStripLayoutTest` et al).

### fix: wear OAL line raised to runout spacing; runout bubble leader clearance

(Committed earlier today as `61ef2c6`; entry added retroactively.) Wear document's OAL
dimension line raised 16 → 90 pt above the shaft (matches the runout sheet). Runout
bubbles gain a leader-clearance spread — cross-row gaps widen by up to 1.6×minGap
(+8 pt on PDF) funded from row slack, so bubbles keep writing room from neighboring
leaders; tight rows degrade to prior behavior.

### feat: collision-free runout bubble layout — shared engine, alternating rows

Runout bubble placement is rewritten around a hard guarantee: **bubbles never touch
each other, and leader lines never enter a bubble or cross another leader.** The old
greedy level assignment only prevented same-level bubble overlap — a leader to a
level-1 bubble could slice through a level-0 circle, adjacent levels could physically
overlap (38 pt step vs 40 pt bubbles), and the PDF and canvas preview had drifted apart
(different body-station math, different taper inset caps, preview silently dropping
overflow bubbles).

- **New shared engine `geom/RunoutBubbleLayout.kt`** (pure Kotlin, JVM-tested) used by
  BOTH `RunoutPdfComposer` and the `RunoutRoute` canvas preview — identical renderings
  by construction; placement logic no longer lives in any renderer.
- **Alternating rows** (0,1,0,1 within each component, phase-flip at crowded component
  boundaries), globally aligned row heights anchored below the deepest drawn shaft
  point — the hand-drawn shop convention.
- **Spacing invariants** make bubble contact geometrically impossible; bubble x
  positions are a least-squares fit (pool-adjacent-violators) so bubbles sit directly
  under their stations whenever there is room. Two rows is width-optimal — every
  leader's drop needs its own horizontal lane past the rows above it, so deeper stacks
  can never pack tighter (see `docs/archive/runout_bubble_collision_system_2026-07-18.md`).
- **Leader verification + dogleg re-routing**: every leader is collision-checked
  (segment-circle, segment-segment); failures re-route as doglegs through a common
  departure line + corridor + vertical drop, which provably converges to zero
  intersections. Physically impossible densities (~27+ stations/page) compress and
  flag themselves (`RunoutBubblePlan.compressed`).
- **Standardised station math** (was silently different between PDF and preview):
  bodies use cell midpoints; taper/liner edge inset caps at 20 % of length.
- 20 unit tests (`geom/RunoutBubbleLayoutTest.kt`) incl. randomized stress configs,
  stepped-OD shafts, and degenerate overload.

### feat: runout sheet drawing conventions — raised OAL, keyway notch

- **OAL dimension raised** to 90 pt (≈ 1.25 in) above the shaft top with witness
  (extension) lines dropping to the shaft's actual top edge at each SET face — the
  schematic/wear-document convention; the line no longer crowds the profile.
- **Keyway reference marker** upgraded from a 4 pt filled square to a 7 pt **open
  square notch straddling the rim at 12-o'clock** (key-at-top convention, like the
  hand-drawn sheets), and now drawn in the canvas preview too (was PDF-only).
- Docs corrected: threads ARE drawn on the runout profile (hatched envelopes, no
  stations; excluded-from-OAL threads sit outside the SET-to-SET arrows at their
  physical position) — `RunoutSheet.md` previously claimed otherwise.

### fix: runout & wear documents now use resolved components

The runout sheet and wear document built their profiles and measurement stations from
**raw** `spec.bodies`, while the schematic uses the resolved component list (bodies
subtracted against tapers/liners, split/merged, auto-fill gaps). A stored body
overlapping the AFT taper therefore produced runout stations *inside* the taper
(stacked, low-hanging bubbles), listed "Body #1" above "AFT Taper" in the station
selector, and drew body rectangles through the taper trapezoid on the wear document.

- `composeRunoutPdf` / `composeWearPdf` now accept `resolvedComponents` (same contract
  as `composeShaftPdf`) via a shared `ShaftSpec.withResolvedBodies` helper; profile,
  OD lookups, and station placement all use resolved bodies.
- `RunoutRoute` station selector + canvas bubbles and `WearRoute` previews/exports all
  pass `vm.resolvedComponents`.
- Auto-body spans now get stations and outline on both documents (previously silently
  skipped); auto segments are labeled "Body (auto)" — carousel parity.
- Analysis: `docs/archive/runout_wear_resolved_components_fix_2026-07-18.md`.

### docs: liner wear-area feature proposal

`docs/LinerWearAreas_Proposal.md` — scoping document for tap-a-liner wear inspection
on the Wear tab (break-out detail view matching the shop-sketch convention, wear spots
with liner-local start/length + min-Ø reading, `wear_record` envelope field, 4-phase
implementation plan). Awaiting review; 5 open questions listed in §10.

---

## 2026-07-12

### feat: backup & restore for saved shafts (+ fix for update data loss)

Layered protection against losing saved shafts to a bad update:

- **Fixed the root cause of saves lost on update.** Sample pruning previously
  deleted any file whose name matched a bundled sample and whose notes carried
  the `[SAMPLE]` marker — silently destroying shafts a user had built by
  editing a seeded sample. Pruning is now ledger-driven: at seed time the app
  records a SHA-256 of exactly what it wrote, and on a version bump it deletes
  only files still byte-identical to that hash. Anything edited (or predating
  the ledger) is kept. Regression-tested.
- **Settings → Data → "Back up all shafts…"** writes every saved shaft into a
  single dated zip (with a manifest) at any location you pick — Drive,
  Downloads, SD card — so a copy lives outside the app sandbox.
- **Settings → Data → "Restore from backup…"** imports a backup zip. Never
  overwrites: identical docs are skipped, name collisions are saved as
  `<name> (restored)`. Result summary shown in a snackbar.
- **Open screen → "Import"** brings a single `.shaft` file from anywhere on
  the device into Saved (same never-overwrite policy).
- **Save screen → "Save a copy to device…"** exports the current shaft as a
  `.shaft` file to a picked location (device-to-device sharing, ad-hoc copies).
- **Automatic pre-update snapshots:** on the first launch after an app-version
  change, the whole saves folder is zipped into internal `backups/` (keeping
  the last 3) *before* any migration or seeding runs.
- **Android Auto Backup wired up:** `shafts/` is now included in Google cloud
  backup and device-to-device transfer rules, so a reinstall can restore saves
  without any manual step.
- New `ShaftBackup` util (zip write/read, restore policy, snapshots) with a
  JVM test suite; seeding tests extended to cover the ledger-guarded pruning.

### fix: auto taper-rate review fixes (formatting, sentinels, state ownership)

Post-review hardening of the auto taper-rate feature shipped 2026-07-11:

- **Snapped `1:10` / `1:20` no longer corrupt to `1:1` / `1:2`.** The common-rate
  formatter trimmed trailing zeros off integer output; zeros are now only trimmed
  in the fractional part. Regression-tested.
- **No rate is fabricated from missing diameters.** `autoTaperRate` now requires
  both diameters to be real positive values; the dialog's `-1` "not provided"
  sentinel and the model's `0` default previously produced garbage rates (e.g.
  `1:2.970` from SET 100 / LET blank) that could survive a switch to Manual and
  drive missing-end derivation.
- **Viewing a taper card no longer mutates the document.** The carousel card's
  composition-time `LaunchedEffect` write to `taperRateText` (which dirtied and
  autosaved untouched files, and could rewrite a derive-pending diameter) is
  removed. The model is written only on explicit commits: geometry edits carry
  the recomputed rate; tapping the Auto chip syncs the stored text.
- **Auto/Manual mode is now user-owned state**, seeded once per taper instead of
  re-derived from string equality — a typed manual rate that happened to match
  the computed text no longer silently flips the card back to Auto.
- **Bore preference actually works now.** The 1:16 (≤ 6 in) / 1:12 (> 6 in)
  preference was a dead-code float-equality tie-break; it now selects among
  within-tolerance candidates whose errors are comparably close (≤ 1 pt apart),
  while a clearly closer candidate still wins on geometry. Expressed in
  canonical mm (152.4) per the unit-edge rule. Covered by new tests.
- **Blank manual rate reverts instead of committing.** Committing `""` left the
  field blank while `updateTaper`'s `ifBlank` kept the old rate in the model/PDF.
- **Dialog no longer clobbers a typed manual rate** when toggling Auto → Manual;
  the rate field's display is derived, matching the carousel pattern.
- **Carousel card gained the Auto one-end-missing message** ("Auto needs
  Length + SET + LET…") for parity with the Add dialog.
- **PDF footer rate fallback delegates to the shared formatter**, so a
  blank-rate taper prints the same snapped/exact text the card shows on screen.

## 2026-07-11

### feat: auto taper-rate from Length + SET + LET (auto default, 3-decimal exact)

- Added automatic taper-rate calculation when Length, SET, and LET are present.
- New **Rate mode** control (`Auto | Manual`) in both Add Taper dialog and taper
  carousel card. Auto mode is the default.
- Auto mode snaps to common shop tapers when close (3% slope tolerance), and falls
  back to exact `1:N.NNN` when not close.
- Auto matching now carries the shop preference order for common bores: 1:16 is the
  preferred small-bore candidate (6" and under), 1:12 is preferred above 6" when
  inputs are otherwise comparably close.
- Exact auto rate now preserves 3 decimal places for review (thousandths-friendly).
- Manual mode now rejects bare `1` as ambiguous, allows intentional `1/1`, requires
  a rate when one taper end must be derived, and warns when a typed manual rate does
  not match Length + SET + LET.
- Added `TaperRateAutoTest` coverage for snapping, exact fallback, alternate common
  lists, and invalid-input guards.

### fix: taper rate input accepts colon ratios on Android keyboards

- Taper rate fields now request an ASCII-capable keyboard so users can enter
  ratio forms like `1:12` even when the numeric keypad omits `:`.
- Numeric input filtering now supports an opt-in colon mode used by taper-rate
  inputs, while preserving existing numeric/fraction behavior for all other
  fields.
- Added focused unit coverage for input filtering in
  `app/src/test/java/com/android/shaftschematic/util/TextFiltersTest.kt`.

### fix: cleanup sweep wave 1 — 5 bugs + hot-path fixes (branch `fix/wave1-cleanup`)

From `docs/cleanup_sweep_2026-07-11.md` Part 1 and the Wave-1 one-liners:

- **Multi-step redo works** — replayed deletes no longer clear the redo stack
  (`isRedoing` guard in the five `removeX` paths); previously the first redo destroyed
  the remaining redo entries.
- **No more save-state crash from previews** — runout/wear preview bitmaps moved from
  `rememberSaveable` to `remember`; an `ImageBitmap` is not saveable and threw on
  backgrounding with a preview open.
- **Runout/wear exports match their previews** — the SAF export launchers now pass
  `pdfPrefs` and `lineThicknessScale` like the preview paths always did.
- **Autosave restores the full session** — `SessionSnapshot` now carries `runoutConfig`,
  `unitLocked`, and `overallIsManual` (older drafts still decode via defaults). Opening
  a document also derives the OAL-manual flag from the file (oversized OAL ⇒ manual)
  instead of leaking the previous session's flag — an authored oversized OAL can no
  longer be snapped down by the auto-sync on open.
- **PDF compression note matches the drawing** — the "compressed for clarity" footer
  note now tests the resolved body list the geometry pass actually drew.
- **Footer "Body:" line lists only drawn bodies** — it previously read raw `spec.bodies`,
  so a degenerate body row (zero-length, or fully swallowed by body subtraction under a
  liner/taper) printed a phantom Ø that appeared nowhere in the drawing or carousel.
  Now it lists authored bodies as actually drawn.
- **Preview hot path** — `layout.dbg()` no longer formats debug strings every pan/zoom
  frame when verbose logging is off; `RenderOptions` is remembered instead of rebuilt
  per recomposition.
- **Carousel pages keyed by component id** — per-page state (scroll, focus) follows the
  component when one is inserted/removed instead of bleeding to the neighbor.
- **Line-thickness sliders commit on release** — dragging no longer writes DataStore and
  re-renders the PDF preview on every frame (PDF preview sheet, runout/wear sheet,
  Settings).

### feat: classic S-break symbol on compression breaks (all three PDFs)

Long-body compression breaks now draw the full round-stock break symbol: the existing
S-curve plus a return sweep that starts at one tip of the S, arcs back on the opposite
side, and dies into the centerline — closing the "eye" that makes the break read as a
revolved 3D surface instead of a flat wave. The two edges of a break alternate (left
edge closes its eye at the bottom, right edge at the top), matching how the symbol is
drawn by hand. The eye is shaded with a light translucent wash (~18% black, the
shaded-body recipe) to deepen the 3D read. One shared `drawBreakEdge` in
`pdf/BreakSymbol.kt` replaces the three private copies in `ShaftPdfComposer`,
`RunoutPdfComposer`, and `WearPdfComposer` — the wear document's slightly different
double-wave variant now matches the other two documents.

### fix: audit remediation pass — 13 bugs + dead-code sweep (branch `fix/audit-remediation`)

Fixes every live bug found by the deep audit (`docs/deep_audit_2026-07.md`, Part 1 §1.0 B1–B13) plus the confirmed dead code. 379 unit tests green (23 net new).

**Data integrity**
- **Atomic saves** — `InternalStorage.save()` now writes to a `.tmp` file, keeps the previous version as `name.shaft.bak`, then swaps into place. A crash or disk-full mid-save can no longer corrupt the only copy of a document. `.tmp`/`.bak` siblings are invisible to the file list. (B5; new `InternalStorageAtomicSaveTest`.)
- **Corrupt files no longer crash the app** — both open paths (Open screen + Start-screen recents) now catch decode failures and show "file may be damaged" instead of throwing inside a coroutine. (B4)
- **Newer-format files are refused, not silently gutted** — `ShaftDocCodec.decode()` now checks the envelope `version`; files from a future app version throw `UnsupportedDocVersionException` (surfaced with an "update the app" message) instead of decoding with unknown fields dropped and destroyed on re-save. (B6; new `DocVersionTest`.)

**Geometry / domain correctness**
- **Taper-rate derivation is direction-aware** — `deriveTaperDiameters` previously assumed the start diameter was the *large* end, so an AFT-end taper entered as SET + rate derived an upside-down taper (LET smaller than SET). It now takes `smallEndAtStart` (classified by taper midpoint, matching the SET/LET labeling rule in `taperSetLetMapping`) and always derives SET < LET. Params renamed `setMm/letMm` → `startDiaMm/endDiaMm` to match what they actually are. (B3; `TaperRateTest` rewritten with AFT + FWD cases.)
- **FWD-referenced coupler slots re-anchor on OAL change** — `withNewOal()` now preserves a slot's authored distance from the FWD face (same rule as FWD-ref tapers/liners); previously slots silently drifted off the coupling when OAL changed. `shiftAllBy()` also includes slots now. (B1; new `WithNewOalTest` cases.)
- **Slot-only drafts are protected** — `isSessionDefault()` now checks `couplerBoltSlots`, so an autosaved draft containing only slots can't be clobbered by the restore path. (B2)
- **Coupler-slot bounds validation at add time** — `AddCouplerBoltSlotDialog` now blocks rows that bite past the AFT face or run past the FWD end (mirrors `CouplerBoltSlot.isValid`). (B7; new `CouplerBoltSlotTest` for the model.)

**PDF output**
- **Long text can't overrun footer columns** — footer lines (all three columns) and the runout/wear headers are now ellipsized to their column width via `ellipsizeToWidth()`. (B8)
- **One OAL number across all three documents** — the runout sheet and wear document now print the *typed* OAL as the label (arrows still bracket the drawn SET-to-SET span), matching the main schematic's "OAL label never changes" rule from `docs/OverallLength.md`. Previously they printed the SET-to-SET distance labeled "OAL". (B12; `RunoutSheet.md` updated.) **Review note:** this supersedes the older RunoutSheet.md convention — revert `RunoutPdfComposer`/`WearPdfComposer` call sites if SET-to-SET was intended.
- **Metric footers print pitch in mm** — thread callouts now show `2 mm pitch` in metric mode instead of TPI (TPI kept for inch mode). (B13; `FooterUnitsTest` strengthened.)
- **Slot cutouts use the drawn surface radius** — `drawCouplerBoltSlots` now takes the same body list the composer actually drew (resolved bodies incl. auto-bodies), so a slot over an auto-body region no longer falls back to the global max OD. (B9)
- **Wear OAL line anchors to the true outline top** — uses `maxOuterDiaMm()` (liners/tapers included) instead of body diameters only. (B10)

**UX**
- **"Save" in the unsaved-changes dialog no longer swallows the pending action** — with a known filename it quick-saves and continues the New/Open; otherwise the action resumes after a successful Save-As (dropped on cancel). (B11)
- Removed the debug pointer-event logging loop that ran on every carousel delete button.

**Dead code removed (~1,400 lines, zero callers, verified by grep + green build):** `ui/editor/ComponentCarousel.kt` (stale pre-refactor copy), `ui/drawing/LayoutMap.kt` + `ui/config/DisplayCompressionConfig.kt` + `ui/drawing/DrawingConfig.kt` (abandoned display-compression system), `data/ShaftRepository.kt` + `ShaftFileRepository.kt` + `NoopShaftRepository.kt` (unused repo layer), `data/MetaInfo.kt`, `pdf/ShaftPdfComposerCompat.kt`, `ui/nav/SafRoutes.kt`, `ui/input/NumberField.kt` (wrong package) + `Inputs.kt` + `ShaftMetaSection.kt` + `UnitSelector.kt`, `util/TaperParser.kt`, `util/HintStyle.kt`, and `geom.computeExcludedThreadLengths` (production-dead since the immutable-OAL fix; its two tests removed with it).

### feat: coupler bolt slots — radial muff-coupler bolt cutouts

New component type: **coupler bolt slots** — one axial row of radial bolt cutouts carved into the shaft at a muff-coupler location. Each cutout renders as a circle straddling the shaft outline (half in the shaft, half in the coupling), mirrored top and bottom, everywhere the shaft is drawn (preview + all three PDFs).

A coupler bolt slot is a **pure reference feature**: it never affects overall length (`coverageEndMm` ignores it), never splits bodies, and never collides with other components. This is the key simplification versus threads/liners.

**Model** — **`model/CouplerBoltSlot.kt`** (new) + `SlotAuthoredReference { AFT, FWD }`. Fields: `startFromAftMm` (first/aft-most cutout center), `holeDiaMm`, `count`, `spacingMm`, `through` + `depthMm` (blind), `authoredReference` (default **FWD**), `showDimensionRail` (deferred; off), `label`. Added `couplerBoltSlots: List<CouplerBoltSlot>` to `ShaftSpec` (defaults empty → back-compat, no migration) and wired `validate()`.

**Enum / branches** — `ComponentKind.COUPLER_BOLT_SLOT`; branches added in `ShaftSpecExtensions` (`segmentFor`/`withSegmentStart`), `StartOverlapValidation.collisionGroup()` (→ null, no collisions), and the `ShaftRoute` delete-snackbar label.

**Resolved layer** — `ResolvedCouplerBoltSlot`, resolved *after* body resolution so it never enters auto-body/subtraction geometry, then merged back by position for the carousel.

**ViewModel** — `addCouplerBoltSlotAt` / `updateCouplerBoltSlot` (+ `Reference`/`Label`/`ShowRail`) / `removeCouplerBoltSlot`; `LastDeleted.CouplerBoltSlot` undo/redo; ordering + session defaults. Deliberately does **not** call `ensureOverall()`.

**Render** — overlay pass in **`ui/drawing/render/ShaftRenderer.kt`** (preview) and `drawCouplerBoltSlots()` in **`pdf/ShaftPdfComposer.kt`**, reused by `RunoutPdfComposer` and `WearPdfComposer`. New `RenderOptions.slotFillColor`.

**UI** — "Coupler Bolt Slot" entry in `InlineAddChooserDialog`; new `AddCouplerBoltSlotDialog` (position from AFT/**FWD default**, hole Ø, count, spacing, through/blind + depth); carousel edit card with the same controls plus the deferred "show dimension rail" toggle. When FWD-referenced, the entered position locates the fwd-most cutout and the row extends aft.

**Deferred (v1):** the per-slot dimension rail is captured (`showDimensionRail`) but not drawn; through vs blind draw the same cutout for now. See `docs/archive/CouplerBoltSlot_Proposal.md`.

---

## 2026-06-23

### docs: CLAUDE.md + AddComponentDialogs.md — lock in dialog/card parity invariants

Added project-level documentation to prevent future regressions where controls present in carousel edit cards get accidentally dropped from their corresponding Add dialogs.

- **`CLAUDE.md`** (project root) — Claude Code loads this at the start of every session. Lists critical do-not-remove invariants: dialog/card parity, numeric commit guard, auto-body promotion, Free-to-End badge suppression, and commit policy.
- **`docs/AddComponentDialogs.md`** — New contract doc. Tables every field per dialog, states the parity rule explicitly, and includes a "Do Nots" section referencing the thread AFT/FWD regression as the canonical failure mode.
- **`docs/ShaftScreen.md`** — Updated to v0.8 with changelog entries for all 2026-06-23 fixes.
- **`docs/README.md`** — Added `AddComponentDialogs.md` to the index.

### fix: thread AFT/FWD end selector missing from Add Thread dialog

`AddThreadDialog` was missing the "Thread end: AFT | FWD" chip selector that appears in the carousel edit card when a thread is excluded from OAL. The feature existed in the card (`ComponentCarousel.kt`) but was never wired into the add dialog.

When `Count in OAL` is toggled off, the dialog now shows AFT/FWD chips and hides the Start field — matching the carousel card exactly. `isAftEnd` is passed through: `onSubmit → ShaftScreen.onAddThread → ShaftRoute → ShaftViewModel.addThreadAt()` and stored on the `Threads` model object.

**`ui/screen/AddComponentDialogs.kt`** — `AddThreadDialog`: added `isAftEnd` state, conditional Start/chips layout, updated `onSubmit` signature.  
**`ui/screen/ShaftScreen.kt`** — `onAddThread` signature updated.  
**`ui/screen/ShaftRoute.kt`** — passes `isAftEnd` to `vm.addThreadAt()`.  
**`ui/viewmodel/ShaftViewModel.kt`** — `addThreadAt()` accepts and stores `isAftEnd`.

### fix: auto-body not promoted on tap-and-leave; body length = 1 bug

`NumericInputField` previously called `onCommit` on every blur, even when the user hadn't changed the field's value. For auto-body carousel cards, this silently triggered `promoteIfNeeded()` — converting the virtual auto-body into a stored body using whatever dimensions were current at the time of the blur.

**Root cause**: The OAL field updates `spec.overallLengthMm` on every keystroke. While the user was typing a multi-digit OAL (e.g. "158.125"), the auto-body's span changed with each character, resetting its ID and therefore its `promoted` state (keyed on `component.id`). Any unfocused CommitNum field in the auto-body card would then commit — with the transient auto-body dimensions — and create a real body prematurely (e.g. with length = 1" when OAL was momentarily "1").

**Fix**: `NumericInputField` now captures the text value at focus-gain (`textWhenFocused`) and only calls `commitOrRevert()` on blur if the text actually changed. A tap-and-leave with no edit is a no-op. This prevents spurious auto-body promotion and avoids unnecessary model updates from unchanged fields throughout the app.

**`ui/input/NumericInputField.kt`** — added `textWhenFocused` state; blur handler now guards on `text.text != captured`.

### fix: numeric fields select-all on focus; zero clears in OAL field

Two input UX fixes:

- **Numeric input fields** (Start, Length, Ø, and all other `NumericInputField` instances) now select all text when focused. Typing immediately replaces the existing value without needing to manually clear it first. Implemented by switching `NumericInputField` from `String` to `TextFieldValue` state with a `TextRange(0, length)` selection on focus gain.
- **OAL field** now clears to empty when focused and the current value is "0" (new drawing default), preventing a leading zero from being prepended to the user's input.

**`ui/input/NumericInputField.kt`** — `String` state replaced with `TextFieldValue`; select-all on focus; `OutlinedTextField` switched to `TextFieldValue` overload.  
**`ui/screen/ShaftScreen.kt`** — OAL field `onFocusChanged`: clear text when value is `"0"` on focus gain.

### fix: Add Body defaults to remaining OAL length in manual mode

When the user taps `+ Add Component → Body` while in Manual OAL mode, the `AddBodyDialog` now pre-fills the Length field with the remaining shaft space (`OAL − startMm`) instead of the session default. This means a first body on a manually-sized shaft fills the full shaft length by default — avoiding the confusing state where the dialog opened with 16" on a 158" shaft.

In auto mode the session default is used unchanged.

**`ui/screen/ShaftScreen.kt`** — `chooserOpen` → `onAddBody` lambda: `tapAddGapMm` set to `spec.overallLengthMm - d.startMm` when `overallIsManual`.

### fix: Free-to-End badge hidden when only bodies are present

The Free-to-End badge in Manual OAL mode was showing a large "free" value even when the shaft was visually fully covered — e.g. "Free to end: 148.125 in" on a 158.125" shaft with a single 10" body. This was confusing because the auto-body system always generates a trailing virtual body from the last real component to the OAL, so the shaft appears completely filled in the preview.

**Root cause**: `lastOccupiedEndMm()` only counts stored (explicit) bodies; it did not see the auto-body covering the remainder. The badge correctly computed `OAL − realBodyEnd` but that gap was always covered visually.

**Fix**: `FreeToEndBadge` now returns early (hides) when there are no precision components (tapers, non-excluded threads, liners) **and** the shaft is not oversized. When only bodies exist, auto-bodies always cover the remainder up to OAL, so the badge value would always be misleading. The red/oversized warning still fires regardless, ensuring users are still told when a body exceeds the OAL.

**`ui/screen/ShaftScreen.kt`** — added early-return guard in `FreeToEndBadge`.  
**`docs/FreeToEndBadge.md`** — invariant documented.

### feat: Open page — search, sort by name/date, and date column in list

The Open drawing page previously showed a flat alphabetical list with no filtering or sorting. It now has:

- **Search field** at the top with a clear (×) button — filters by filename as you type, shows "No drawings match…" when nothing found.
- **Name / Date sort chips** — tap to switch column; tap again to flip direction (↑/↓). Date defaults to descending (most recent first). Name defaults to ascending.
- **Date column** under each filename — shows relative age ("Today", "Yesterday", "3w ago", etc.) so you can see at a glance when each drawing was last saved. The old "Open" text label is removed.
- File list now loads via `listWithMetadata()` so timestamps are always available.

**`ui/nav/InternalDocRoutes.kt`** — `files` changed from `List<String>` to `List<Pair<String,Long>>`; `displayFiles` derived state for filter+sort; search + sort header added as sticky `LazyColumn` item.

### feat: Start screen recent list — card layout, chevron, limit 3

The recent documents section on the Start screen was a plain divider list that didn't read as tappable and had misaligned text when names were short.

- Wrapped in a `Card` (surfaceVariant) for visual grouping.
- Name now fills available width (`weight(1f)`) so the relative date and chevron always right-align.
- `KeyboardArrowRight` chevron added to each row to signal tappability.
- Limit reduced from 5 → 3 files (reduces clutter for a tool where drawings are rarely revisited).

**`ui/screen/StartScreen.kt`** — card wrapping, weight fix, chevron icon, `take(3)`.

### refactor: extract ViewModel settings setters to ShaftViewModelSettings.kt

`ShaftViewModel.kt` was 2134 lines — a single class managing spec mutations, 40+ state flows, autosave, undo/redo, achievements, dev options, and persisted settings. The 237-line block of settings setter functions has been extracted to a new `ShaftViewModelSettings.kt` extension file in the same package.

32 private backing fields (`_openPdfAfterExport`, `_pdfTieringMode`, `_previewBlackWhiteOnly`, `_devOptionsEnabled`, verbose logging fields, achievement fields, etc.) were promoted to `internal` to allow the extension functions to access them. All callers outside the `ui.viewmodel` package received explicit imports.

**`ui/viewmodel/ShaftViewModelSettings.kt`** (new) — 237 lines; all settings setters + `syncVerboseLogConfig` as extension functions on `ShaftViewModel`.  
**`ui/viewmodel/ShaftViewModel.kt`** — 32 fields changed from `private` to `internal`; settings setter block removed; file reduced to ~1800 lines.  
**`ui/nav/InternalDocRoutes.kt`, `PdfExportRoute.kt`, `ui/screen/AboutRoute.kt`, `DeveloperOptionsRoute.kt`, `PdfPreviewScreen.kt`, `RunoutRoute.kt`, `SettingsRoute.kt`** — added `ui.viewmodel.*` imports so extension functions resolve outside their declaring package.

### test: fix stale test expectations after LengthFormat and UnitFormat updates

Two unit tests were asserting against formats that changed in later commits and were never updated:

- `LengthFormatTest` expected `"1 3/4"` but `formatInchesSmart()` now returns `"1 ¾"` (Unicode fractions added in a prior commit).
- `FooterUnitsTest` expected `" in"` unit suffix but `formatDiaWithUnit()`/`formatLenWithUnit()` now produce `"\""` (quote suffix, per shop convention).

Both test expectations updated to match current output. No production code changed.

### fix: remove deprecated `composed{}` in `clickableWithoutRipple` (analysis #12)

`Modifier.composed {}` is deprecated in Compose since Foundation 1.6. `clickableWithoutRipple` was using it unnecessarily — the wrapper added nothing over a direct `Modifier.clickable(interactionSource = null, indication = null)` call, which is valid in Foundation 1.7+ (BOM 2024.09.00 used in this project).

**`ui/screen/ShaftScreen.kt`** — `clickableWithoutRipple` rewritten without `composed {}`.

### chore: remove `-Xlambdas=class` Kotlin compiler flag (analysis #13)

`-Xlambdas=class` was added to work around a legacy Compose compiler issue. It forces lambda expressions to compile to anonymous classes, bypassing SAM/invoke-based inlining. The modern Kotlin 2.x + Compose K2 compiler handles this correctly without the flag; keeping it degrades runtime performance (more class loading, more GC pressure).

**`app/build.gradle.kts`** — removed `freeCompilerArgs.add("-Xlambdas=class")`.

### fix: reset dev-option sub-flags on startup when dev options disabled (analysis #14)

All 8 developer sub-flags (OAL debug label, helper line, preview box overlay, component debug labels, render layout overlay, OAL markers, verbose logging categories) persisted to DataStore across restarts. If a debug APK was handed to a customer with any flags enabled, they would remain active indefinitely.

`SettingsStore.resetDevSubFlagsIfDisabled(ctx)` now resets all 8 flags to false on startup unless dev options are explicitly enabled. Called from `ShaftViewModel.init` via `ShaftViewModelSettings.resetDevFlagsOnStartup()`.

**`data/SettingsStore.kt`** — added `resetDevSubFlagsIfDisabled()`.  
**`ui/viewmodel/ShaftViewModelSettings.kt`** — added `resetDevFlagsOnStartup()` extension; called from VM init.

### fix: LET/SET direction now determined from actual taper geometry (analysis #15)

The PDF footer labeled taper ends as "L.E.T." and "S.E.T." without stating which physical end was which. For coupling tapers (FWD taper, LET is at the FWD end) this was ambiguous and potentially misleading.

`letSet()` in `ShaftPdfComposer` now compares `startDiaMm` vs `endDiaMm` to determine which end is the larger (LET) and smaller (SET), then includes the direction label in the footer: `L.E.T. (AFT): …` / `S.E.T. (FWD): …`. Since `startDiaMm` is always the AFT-facing end of the taper model, the direction is deterministic.

**`pdf/ShaftPdfComposer.kt`** — new `LetSetResult` data class; `letSet()` rewritten to derive direction from geometry.  
**`pdf/FooterUnitsTest.kt`, `pdf/FooterOrderTest.kt`** — assertions updated from `"L.E.T.: "` / `"S.E.T.: "` to `"L.E.T. ("` / `"S.E.T. ("` pattern.

### feat: Save As and quick-save by document name (analysis #16)

The Save toolbar button now distinguishes between two states:
- **Named document** (opened from file or previously saved): silently overwrites the existing file without navigating to the name dialog.
- **Unsaved/new document**: navigates to the name dialog as before.

A new "Save As…" item in the overflow menu always navigates to the name dialog, allowing a renamed copy to be saved without overwriting the original.

`vm.currentDocumentName: StateFlow<String?>` tracks the active filename across open, save, rename, and recent-open operations. `vm.setCurrentDocumentName()` is called in all four code paths.

**`ui/viewmodel/ShaftViewModel.kt`** — `_currentDocumentName` StateFlow added; `setCurrentDocumentName()` and `newDocument()` reset added.  
**`ui/nav/AppNav.kt`** — `onSave` lambda split into quick-save vs navigate; `onSaveAs` added; recent-open path sets name.  
**`ui/nav/InternalDocRoutes.kt`** — `setCurrentDocumentName()` called after save (both overwrite and new-name paths) and after open.  
**`ui/screen/ShaftEditorRoute.kt`, `ShaftRoute.kt`, `ShaftScreen.kt`** — `onSaveAs` parameter threaded through.  
**`ui/screen/ShaftScreen.kt`** — `OverflowMenu` receives `onSaveAs` and shows "Save As…" item.

### fix: move "Highlight selection" toggle from editor to Settings (analysis #19)

The "Highlight selection in preview" Switch was rendered inline in the component editor body — a persistent setting that most users never change taking up vertical space in the editing area.

Moved to the Settings screen, persisted via `SettingsStore`, and backed by `vm.showHighlightSelection: StateFlow<Boolean>` that flows into `ShaftScreen`.

**`data/SettingsStore.kt`** — `KEY_SHOW_HIGHLIGHT_SELECTION`, `showHighlightSelectionFlow()`, `setShowHighlightSelection()` added.  
**`ui/viewmodel/ShaftViewModel.kt`** — `_showHighlightSelection` StateFlow; collector in `init`.  
**`ui/viewmodel/ShaftViewModelSettings.kt`** — `setShowHighlightSelection()` extension.  
**`ui/screen/ShaftRoute.kt`** — collects and passes `showHighlightSelection`.  
**`ui/screen/SettingsRoute.kt`** — Switch row added after showGrid toggle.  
**`ui/screen/ShaftScreen.kt`** — inline Switch removed from editor body; `showHighlightSelection` parameter added.

### fix: collapse double header into single TopAppBar (analysis #20)

The top of the editor screen had a `Text("Shaft Editor")` label stacked above a `TopAppBar(title = {})` with an empty title. The combined chrome wasted approximately 56dp of vertical space.

Collapsed into a single `TopAppBar(title = { Text("Shaft Editor") })` carrying all existing navigation icons and actions.

**`ui/screen/ShaftScreen.kt`** — `Column { Text + TopAppBar(title={}) }` replaced with `TopAppBar(title = { Text(...) })`.

### feat: custom labels for Body, Taper, and Thread components (analysis #18)

Bodies, Tapers, and Threads previously had auto-generated display names only ("Body #1", "AFT Taper", etc.). Liners already supported custom labels. All three component types now support optional user-defined labels.

Tap the card title to enter edit mode — an `OutlinedTextField` replaces the title, pre-filled with the current label (or blank for a new one). Focus-lost and Enter commit the label. Clear the field to revert to the auto-generated name.

`label: String? = null` is added to each data class with a default so existing serialized documents deserialize without error. `buildBodyTitleById`, `buildTaperTitleById`, and `buildThreadTitleById` now prefer the custom label when set.

**`model/Body.kt`, `model/Taper.kt`, `model/Threads.kt`** — `label: String? = null` field added.  
**`util/BodyTitles.kt`, `util/TaperTitles.kt`, `util/ThreadTitles.kt`** — custom label wins over auto-generated title.  
**`ui/viewmodel/ShaftViewModel.kt`** — `updateBodyLabel()`, `updateTaperLabel()`, `updateThreadLabel()` added.  
**`ui/screen/ComponentCarousel.kt`** — tap-to-edit label added to Body, Taper, Thread cards (same `titleContent` pattern as Liner); `onUpdateBodyLabel`, `onUpdateTaperLabel`, `onUpdateThreadLabel` threaded through.  
**`ui/screen/ShaftScreen.kt`, `ui/screen/ShaftRoute.kt`** — new label callbacks threaded through.

---

## 2026-06-22

### chore: auto-derive versionCode and versionName from git commit count

`versionCode` was a manually-maintained integer (`3`); `versionName` was a static `"1.2.0"`. Firebase App Distribution was labelling every upload as a duplicate because neither changed between builds.

Both are now derived from `git rev-list --count HEAD` at build time. The current count (244) becomes the patch digit:
- `versionCode = gitCount` (e.g., 244, 245, …)
- `versionName = "1.2.$gitCount"` (e.g., `1.2.244`, `1.2.245`, …)

Every commit automatically produces a uniquely-identified build with no manual editing. The major.minor (`1.2`) is still bumped manually when a breaking change or significant feature milestone warrants it.

**`app/build.gradle.kts`** — added `gitCount` exec block; replaced hardcoded `versionCode`/`versionName`.

---

### feat: Project Information moved to modal bottom sheet

Customer, Vessel, Job #, Shaft Position, and Notes were in a collapsible section inside the editor scroll area, consuming vertical space needed by the component carousel. They now live in a `ModalBottomSheet` opened from a new toolbar clipboard icon (Assignment). The scroll area is now entirely dedicated to components.

**`ui/screen/ShaftScreen.kt`** — removed `ExpandableSection("Project Information")` from scroll area; added `IconButton` (Assignment icon) to `TopAppBar`; added `ProjectInfoBottomSheet` composable with `rememberModalBottomSheetState(skipPartiallyExpanded = true)`.

---

### fix: FWD-reference taper length edit now keeps the FWD end anchored

Editing the length of a FWD-referenced taper was passing `startFromAftMm` through unchanged, so the FWD end drifted inward as the taper grew. The length commit handler now recomputes `physStart = OAL − authoredFromFwd − newLen` so the FWD face stays fixed and the AFT start slides.

**`ui/screen/ComponentCarousel.kt`** — `CommitNum("Length")` handler for tapers.

---

### fix: FWD-ref tapers and liners now stay anchored when OAL changes

`onSetOverallLengthMm()` and `ensureOverall()` were calling `.copy(overallLengthMm = …).syncExcludedThreadPositions()`. FWD-referenced tapers and liners have their start position stored physically from the AFT face, so a raw OAL change left them in place — drifting relative to the FWD face they were authored from.

New `ShaftSpec.withNewOal(newOal: Float)` recomputes `startFromAftMm` for every FWD-referenced taper and liner (`newStart = newOal − authoredFromFwd − length`) before calling `syncExcludedThreadPositions()`. Both OAL mutation paths now use it.

**`model/ShaftSpecExtensions.kt`** — added `withNewOal()`.  
**`ui/viewmodel/ShaftViewModel.kt`** — `onSetOverallLengthMm()` and `ensureOverall()` use `withNewOal()`.

---

### fix: excluded end thread shift no longer pushes shaft FWD edge past right margin

`ptPerMm` was computed from `overallLengthMm` alone, then the excluded-AFT-thread origin shift (`left += threadLen × ptPerMm`) consumed page width that wasn't accounted for in the scale. For the Siberian Sea shaft (OAL 147⅞", 4.5" prop thread), the FWD taper was drawing ~16pt past the right margin.

The fix computes the full content span — `contentMinMm` (excluded AFT thread tails, negative) through `contentMaxMm` (excluded FWD thread tails, past OAL) — before deriving `ptPerMm`. The shaft body's portion of that span is passed as `effectiveGeomWidthPt` to `computeDetailPtPerMm`, ensuring all content lands within `geomRect` regardless of thread count or length.

**`pdf/ShaftPdfComposer.kt`** — `contentMinMm`, `contentMaxMm`, `contentSpanMm`, `effectiveGeomWidthPt` computed before `ptPerMm`; bodies-only branch also updated.

---

### feat: body OD callouts on shaft drawing and footer

Body diameters were only visible inside the open carousel card. They now appear on the exported PDF in two places:

- **Drawing**: one leader-line callout per unique body OD (`Ø value`), placed above and below alternating for readability. Anchor is the center of the longest body section for that OD.
- **Footer center column**: "Body: Ø X, Ø Y" row listing all unique body ODs, appended after the date.

**`pdf/ShaftPdfComposer.kt`** — `buildBodyOdCallouts()` groups bodies by OD, picks longest anchor per group, alternates `LeaderSide.ABOVE/BELOW`; live `DiameterLeaderRenderer` call replaces the prior stub; `drawFooter()` adds body OD row.

**`test/pdf/BodyOdCalloutsTest.kt`** — 9 tests: empty/zero skip, single above, center placement, same-OD uses longest body, two ODs, alternating sides, three ODs cycle, OD accuracy.

---

### fix: float precision in inch↔mm conversions

Three conversion sites in `ShaftScreen.kt` used `25.4f` (Float literal) for inch↔mm math, introducing rounding error on common shaft dimensions. For example, `5 15/16"` (5.9375") via Float arithmetic can lose sub-thou accuracy that survives Double arithmetic.

All three sites now use the canonical `MM_PER_IN = 25.4` Double constant and promote through `Double` before rounding back to `Float`:
- `toMmOrNull()` — inch input to mm storage: `(num.toDouble() * MM_PER_IN).toFloat()`
- `formatDisplay()` — mm to inch display: `(valueMm.toDouble() / MM_PER_IN).toFloat()`
- `tpiToPitchMm()` — TPI to pitch mm: `(MM_PER_IN / tpi.toDouble()).toFloat()`

**`ui/screen/ShaftScreen.kt`** — three `25.4f` → Double arithmetic; added `MM_PER_IN` import.

**`test/ui/screen/UnitConversionTest.kt`** — 10 tests: mm passthrough, whole-number inch, common fractions (5 15/16, 1/8), blank/invalid null, tpiToPitchMm 16/20 TPI, formatDisplay round-trip.

---

### feat: recent documents list on start screen

The start screen now shows the 5 most recently modified shaft documents between the title and the New Drawing / Open… buttons. Tapping a recent entry loads it directly into the editor without going through the Open… dialog.

**`io/InternalStorage.kt`** — `listWithMetadata(dir)` returns `List<Pair<String, Long>>` (filename, lastModifiedMs) sorted newest-first, mirrors existing `list()` filter for `.shaft` and legacy `.json`.

**`ui/screen/StartScreen.kt`** — added `recentFiles` and `onOpenRecent` params; renders up to 5 recent entries with display name and relative date ("Today", "Yesterday", "N days ago", etc.).

**`ui/nav/AppNav.kt`** — start composable loads recent files on mount via `LaunchedEffect`; `onOpenRecent` handler loads file from storage and navigates to editor.

**`test/io/RecentFilesTest.kt`** — 7 tests: empty dir, single file, newest-first sort, non-shaft excluded, legacy json included, directories excluded, multi-file ordering.

---

### test: WithNewOalTest, PdfLayoutBoundsTest, BodySplitMergeTest

**`test/model/WithNewOalTest.kt`** — 17 tests: AFT components unchanged, FWD reanchors on grow/shrink, FWD flush with shaft face, mixed AFT+FWD spec, liner reanchoring and `endMmPhysical` consistency, excluded thread sync, OAL clamp, idempotency, old-copy regression.

**`test/pdf/PdfLayoutBoundsTest.kt`** — 9 tests: no-thread baseline, excluded AFT overflow regression (Siberian Sea), excluded FWD overflow, both ends, short shaft, very short/wide shaft (diameter-bound), variable AFT thread lengths.

**`test/model/BodySplitMergeTest.kt`** — 19 tests: mid-split, AFT/FWD edge inserts, full-consume, non-overlap, touching endpoints, multiple bodies, merge flanking/single-side/float-drift-tolerance, round-trips at center/AFT end/FWD end.

---

## 2026-06-19

### fix: updating a component no longer repositions other components

`updateBody`, `updateTaper`, `updateLiner`, and `updateThread` were calling `snapForwardFrom()` whenever the updated component's start or length changed, silently cascading position changes to every downstream component in the chain. This violated the fundamental invariant that component inputs are user-authored and must not be mutated by anything other than an explicit user action on that component.

The auto-snap block, `_autoSnap` state, and `setAutoSnap()` have been removed from all update paths. `snapChainFrom()` / `snapChainFromId()` remain as explicitly-invoked operations.

**`ui/viewmodel/ShaftViewModel.kt`** — removed `snapForwardFrom` cascade and `_autoSnap` flag from all four `updateX()` functions.  
**`test/ui/viewmodel/ShaftViewModelUpdateTest.kt`** — 9 new tests covering liner, body, taper, thread, and mixed-spec update isolation.

---

### fix: PDF dimension unit suffix changed from " in" to `"`

All inch-unit dimension labels now use the standard `"` suffix instead of ` in` (e.g., `4.997"` not `4.997 in`). Applies to diameters, lengths, and OAL labels across the shaft, runout, and wear PDF composers.

**`pdf/UnitFormat.kt`** — `formatDim()`, `formatLenDim()`, `formatLenWithUnit()`, `formatDiaWithUnit()`.  
**`pdf/RunoutPdfComposer.kt`**, **`pdf/WearPdfComposer.kt`** — OAL display line.

---

### fix: common inch fractions render as Unicode symbols in PDF dimensions

`LengthFormat.formatInchesSmart()` now substitutes common fractions with Unicode characters (½ ¼ ¾ ⅛ ⅜ ⅝ ⅞) so dimension text reads like hand-drawn notation rather than `3/4` or `7/8`.

**`util/LengthFormat.kt`** — `unicodeFractions` map applied in `formatInchesSmart()`.

---

### feat: taper AFT/FWD reference toggle in carousel edit card

The taper carousel card now shows a "Measure From: AFT / FWD" chip row (matching the liner card). Selecting FWD lets the user enter the start distance from the FWD end; the model always stores the canonical `startFromAftMm`. `Taper.authoredReference` (new field, default AFT) persists the user's choice so the field label and value are correct on re-open.

**`model/Taper.kt`** — added `authoredReference: LinerAuthoredReference` field.  
**`ui/screen/ComponentCarousel.kt`** — AFT/FWD toggle + start field adapts label and converts value.  
**`ui/screen/ShaftRoute.kt`** — wires `onUpdateTaperReference` callback.  
**`ui/viewmodel/ShaftViewModel.kt`** — `updateTaperAuthoredReference()`.  
**`docs/Model_Conventions.md`** — updated.

---

### fix: auto-snap removed from all component delete paths

The snap-forward-on-delete behavior (shifting subsequent components left after a deletion) has been removed from `removeBody()`, `removeTaper()`, `removeThread()`, and `removeLiner()`. Body split/merge (added earlier) makes positional snap on delete incorrect — merged bodies already fill the freed span.

**`ui/viewmodel/ShaftViewModel.kt`** — removed `snapFromKey` / `snapFromOrigin` logic from all four remove functions.

---

### fix: PDF footer columns positioned at even thirds, all left-aligned

The three footer columns (AFT, project info, FWD) were computed with an asymmetric gutter formula that bunched the center and right blocks toward the left half of the page. Replaced with clean thirds: `colW = rect.width() / 3`, anchor each column at `rect.left + n × colW`. All columns remain left-aligned.

**`pdf/ShaftPdfComposer.kt`** — simplified column layout in `drawFooter()`.

---

### fix: PDF footer shows authored taper rate text instead of computed 1:N ratio

The `Rate:` line in the AFT/FWD taper footer blocks was always re-derived via `rate1toN()` (e.g. `1:16`), ignoring the `taperRateText` field the user typed (e.g. `3/4"/FT`). The authored string is now used when non-empty; `rate1toN()` is the fallback.

**`pdf/ShaftPdfComposer.kt`** — `buildFooterEndColumns()` uses `tp.taperRateText.trim().ifEmpty { rate1toN(tp) }` for both AFT and FWD taper rate lines.

---

### fix: consolidate conflicting EPS constants in PDF composer

`ShaftPdfComposer.kt` had two proximity tolerances with overlapping scope: `END_EPS_MM = 0.5` (imported from `geom/OalComputations.kt`) and a private `EPS_MM = 0.01`. The 50× discrepancy meant that `detectEndFeatures()` and `getAftEndThread()` / `getFwdEndThread()` / `getAftEndTaper()` / `getFwdEndTaper()` used different thresholds for what counts as "at the shaft end", potentially causing mismatches between which features show up in the footer. Removed `EPS_MM`; all proximity checks now use `END_EPS_MM`.

**`pdf/ShaftPdfComposer.kt`** — removed `private const val EPS_MM`; replaced four usages with `END_EPS_MM`.

---

### fix: Project Information section expanded by default

Customer, Vessel, and Job # were hidden behind a collapsed section on every new drawing, adding friction at job-start. The section now opens expanded.

**`ui/screen/ShaftScreen.kt`** — `ExpandableSection("Project Information", initiallyExpanded = true)`.

---

### feat: body auto-split on add, auto-merge on delete

Adding any taper, liner, or thread now splits any overlapping body into two independent fragments (each keeping the parent's `diaMm` and a new UUID). Deleting a taper/liner/thread merges the flanking body fragments back into one body (merged diameter = max of the two). Single-side boundary case: the lone adjacent body expands to fill the freed span rather than merging.

**`model/ShaftSpecExtensions.kt`** — new `splitBodyAt()` and `mergeAdjacentBodies()` functions.  
**`ui/viewmodel/ShaftViewModel.kt`** — all `add*At()` / `delete*()` paths call split/merge; included in the undo snapshot.

---

### feat: full keyway inputs in Add Taper dialog

`AddTaperDialog` gains KW Width, KW Depth, KW Length, KW Offset, and Spooned toggle fields, mirroring the carousel edit card. Previously these were only editable after adding.

**`ui/screen/AddComponentDialogs.kt`**

---

### fix: add dialogs always open; bodies and excluded threads excluded from default-start

The FAB chooser previously quick-added bodies, liners, and tapers without showing a dialog. All paths now open the full dialog. `computeAddDefaults()` no longer counts bodies or excluded threads when finding the next open slot — they were pushing new component start positions past the shaft end. Body–taper pairs removed from `collidingIds()` (bodies are fillers; taper overlap is intentional). All `add*At()` methods now auto-select the newly created component.

**`ui/screen/ShaftScreen.kt`**, **`ui/viewmodel/ShaftViewModel.kt`**, **`model/ShaftSpecExtensions.kt`**  
**`docs/DATA_MODEL.md`**, **`docs/UI_CONTRACT.md`**, **`docs/VALIDATION_RULES.md`** updated.

---

### fix: direction chip selected state uses border, not fill

`DirectionChip` (AFT/FWD toggle in Add Taper and Add Liner dialogs) replaced `FilterChip` with a custom `OutlinedButton`: selected state shows a 2dp primary-color border + tinted container; unselected has no border. Previously the outlined border on the unselected chip made it visually appear to be the active choice.

**`ui/screen/AddComponentDialogs.kt`**

---

### fix: PDF dimension arrows default inward; arrow size reduced

Arrow tips were flipping outward by an overly strict threshold. `canFitInwardArrows` loosened from `spacing × 1.5` to `spacing × 1.0` so arrows now default inward (engineering convention) and flip outward only when truly cramped. Arrow size reduced from 7 → 5 pt to match hand-sketch reference drawings.

**`pdf/render/PdfDimensionRenderer.kt`**

---

### fix: PDF export no longer rejects excluded threads as out-of-bounds

`blockingExportError()` was triggering "start must be ≥ 0" on excluded threads, which deliberately have `startFromAftMm = −lengthMm`. Excluded threads are now skipped in that check.

**`ui/nav/PdfExportRoute.kt`**

---

### fix: `CommitNumField` commits on every keystroke; external resets don't jump cursor

Values were lost when tapping "Add" while a text field was still focused (the on-blur commit hadn't fired yet). `CommitNumField` now commits on every keystroke. `LaunchedEffect(initial)` handles external value resets without moving the cursor mid-type.

**`ui/screen/AddComponentDialogs.kt`**

---

### fix: excluded thread flashes at shaft face during carousel swipe

In manual OAL mode, `updateThread()` wrote `effectiveStart = 0f` for AFT excluded threads as a temporary value, expecting `ensureOverall()` → `syncExcludedThreadPositions()` to correct it. `ensureOverall()` exits early in manual mode without calling sync, so the `0f` position persisted — placing the thread at the shaft AFT face and causing it to visually overlap the adjacent taper for a single frame. The trigger: `NumericInputField.onFocusChanged` fires a commit when the carousel's `HorizontalPager` clears focus from the excluded-thread card while the user swipes to the adjacent taper card.

Fix: `updateThread()` now derives the correct position (`−lengthMm` for AFT, `overallLengthMm` for FWD) directly inside the `_spec.update {}` call, using the same formula as `syncExcludedThreadPositions()`. The position is always correct regardless of OAL mode, with no transient wrong state.

**`ui/viewmodel/ShaftViewModel.kt`** — `updateThread()` `effectiveStart` for excluded threads.

---

### fix: PDF footer FWD column nudged to 76% of content width

The FWD footer column was at 72% of the content area width; adjusted to 76% for a more balanced three-column layout with the AFT block anchored at the left margin and center block at 40%.

**`pdf/ShaftPdfComposer.kt`** — `rightX = rect.left + rect.width() * 0.76f` in `drawFooter()`.

---

## 2026-06-18

### feat: taper/liner direction toggle; excluded thread rendering; add-time collision warnings

**Direction toggles in add dialogs**
- `AddTaperDialog` — AFT/FWD chip controls which end is the SET. SET/LET labels on the diameter fields swap accordingly; model stores diameters in AFT→FWD order regardless.
- `AddLinerDialog` — "Measure From" AFT/FWD chip writes `LinerAuthoredReference` through `ShaftRoute` → `ShaftViewModel.addLinerAt()` so the carousel card reflects the chosen reference on first render.

**Excluded thread rendering**
- `syncExcludedThreadPositions`: AFT excluded threads placed at `startFromAftMm = −lengthMm`, FWD at `OAL`, sitting flush with the shaft face without overlapping tapers.
- `ShaftLayout.compute`: `minXMm` / `maxXMm` now expand to include excluded threads outside `0..OAL` so they render in both the preview and PDF without clipping.

**Add-time collision warnings**
- New `collectAddWarnings()`: pre-submit overlap check in Taper, Liner, and Thread add dialogs. Warns on cross-type overlaps and shaft bounds when OAL is manual. Bodies and excluded threads are skipped. Warning confirmation dialog offers "Add Anyway / Cancel" — nothing is silently blocked.

**Carousel auto-scroll fix**
- `ComponentCarousel`: size-based auto-scroll `LaunchedEffect` is now conditional on no existing selection, preventing it from overriding the user's swipe after adding a component.

**Tests** — `CollisionWarningsTest` (13 cases), `ShaftSpecTest` +`syncExcludedThreadPositions` (4 cases), `ShaftLayoutTest` +excluded-thread coordinate expansion (4 cases). All passing.

**`model/ShaftSpec.kt`**, **`ui/drawing/render/ShaftLayout.kt`**, **`ui/screen/AddComponentDialogs.kt`**, **`ui/screen/ComponentCarousel.kt`**, **`ui/screen/ShaftRoute.kt`**, **`ui/screen/ShaftScreen.kt`**, **`ui/util/CollisionWarnings.kt`** (new), **`ui/viewmodel/ShaftViewModel.kt`**  
**Docs**: `ShaftLayout v0.4`, `Model_Conventions v0.2`, `ShaftViewModel v0.2`, `ShaftScreen v0.7`, `VALIDATION_APPENDIX`.

---

### ci: Firebase App Distribution workflow

Added GitHub Actions workflow for distributing debug APKs to testers via Firebase App Distribution on every push to `main`. Uses service-account auth via the Firebase CLI.

---

## 2026-06-11

### fix: OAL bracket moves with include/exclude; label is always the typed value

The `excludeFromOAL` toggle on end threads now correctly controls **bracket position only** — the OAL label is always `spec.overallLengthMm`, the value the user typed.

- **Excluded**: bracket spans AFT SET → FWD SET. Thread is drawn outside the bracket.
- **Included**: bracket spans shaft AFT end → FWD SET, grouping the thread inside the arrow.
- Label never changes in either case. Component measurements always reference SET.

Domain rationale: threads don't need to be a specific length on a new shaft; liners and tapers do. The toggle exists for customers (e.g. Coast Guard) who specify exact total lengths so shafts are interchangeable spares. Nothing is ever dimensioned from a thread end.

**`pdf/dim/LinerSpanBuilder.kt`** — `oalSpan()` gains an explicit `labelMm` param (default = bracket width) so the label can be decoupled from the bracket span.  
**`pdf/ShaftPdfComposer.kt`** — bracket endpoints driven by include/exclude; `labelMm = spec.overallLengthMm` always.  
**`geom/OalComputations.kt`** — `computeOalWindow` always returns `(0.0, overallLengthMm)`; `computeExcludedThreadLengths` retained for future SET-to-SET annotation work.

---

## 2026-06-11

### feat: runout screen v2 — inline preview + layout overhaul

- **`RunoutRoute.kt`** — complete rewrite: `RunoutComponentEntry` data class, inline shaft preview via `ShaftRenderer`/`ShaftLayout`, scrollable column layout, sidebar nav integration, `resolvedComponents` support.
- **`ComponentCarousel.kt`** — removed bubble-count stepper controls (95 lines). Bubble counts are managed through the runout config; per-component stepping in the carousel was redundant.
- **`ShaftRoute.kt` / `ShaftScreen.kt`** — removed `runoutConfig` and `onSetRunoutBubbleCount` threading that was coupling the main screen to runout state. `ComponentCarousel` retains defaulted params for backward compatibility.

---

### feat: line thickness control

- **`SettingsRoute.kt`** — `LineThicknessControl` composable: slider (50%–200%) + typeable `%` field with on-blur clamping. 100% = new default thin weight; 200% = original thick weight.
- **`SettingsStore.kt`** — `KEY_LINE_THICKNESS_SCALE` DataStore key; `lineThicknessScaleFlow()` / `setLineThicknessScale()`.
- **`ShaftViewModel.kt`** — `lineThicknessScale: StateFlow<Float>`, collected from DataStore on init, exposed for UI and PDF export.
- **`ShaftPdfComposer.kt`** — `OUTLINE_PT_BASE = 1.25 pt`, `DIM_PT_BASE = 0.8 pt` (100% defaults). `composeShaftPdf()` gains `lineThicknessScale` param; scale applied to both paint objects.
- **`ShaftDrawing.kt`** — `outlineWidthPx = 2f * lineThicknessScale.coerceIn(0.5f, 2.0f)`.
- **`PdfExportRoute.kt` / `PdfPreviewScreen.kt`** — pass `lineThicknessScale` through to the composer.

---

### fix: OAL dimension respects include-thread toggle

The PDF OAL dimension arrow previously always measured **SET to SET** regardless of whether end threads were marked as included in OAL. Root cause: when `excludeFromOAL = false` the coordinate origin shifts by `threadLength`, so both SET endpoints moved by the same delta and the rendered distance was unchanged.

Fix in `ShaftPdfComposer.kt`: detects any end thread with `!excludeFromOAL` anchored to position 0 (AFT) or `overallLengthMm` (FWD), and substitutes the physical shaft end coordinate (`0.0` or `win.oalMm`) for the SET coordinate in the `oalSpan()` call. Component dimension rails continue to reference SET positions.

---

## 2026-05-30 (6)

### feat: yellow warning badges — non-blocking validation now visible in UI

- **`ComponentWarnings.kt`** — new utility with per-component warning functions:
  - Any component with `0 < lengthMm < 1 mm` → "Very short segment (< 1 mm)"
  - Thread with `pitchMm == 0` → "Zero pitch — thread renders flat"
- **`ComponentCard`** gains a `warningMessage: String?` slot rendered as a yellow
  `tertiaryContainer` chip below the title, distinct from the red error chip.
  Body, Taper, Thread, and Liner cards all pass their computed warning.
- **`FreeToEndBadge`** now has three states: normal → yellow (0–10 mm clearance) → red (negative/oversized). Previously only normal and red.
- Stale `TODO.md` entries for keyway drawing marked complete.

---

## 2026-05-30 (5)

### fix: selection highlight — single thin ring instead of double box

- Removed the inner white "edge" ring from the two-ring highlight system.
  Only the outer cyan glow ring is drawn now, giving a single clean selection
  box that doesn't compete visually with component lines (keyways, threads, etc.).
- Reduced `highlightGlowExtraPx` from 8 → 2 px so the ring is noticeably
  thinner while still clearly marking the selected component.

---

## 2026-05-30 (4)

### fix: shared app signing + corrected keyway schematic convention

#### Signing
- Committed `debug.keystore` to the project root so every machine that clones
  the repo signs with the same key. Android now treats sideloaded builds from
  any machine as app updates rather than new installs — no more uninstall/data-wipe
  when switching computers.
- Added `signingConfigs.shared` in `build.gradle.kts` (debug + release both use it).
- `.gitignore` updated with `!debug.keystore` exception; release keystores remain blocked.

#### Keyway rendering — full rewrite to match shop schematic convention
- **Previous behaviour:** drew a notch cutting down from the top surface of the
  taper, showing depth. Wrong axis and wrong convention.
- **Correct convention** (confirmed from shop hand-drawings in `assets/`): the keyway
  is shown as a **plan-view rectangle centred on the shaft centreline** — height
  represents keyway **width** (W) to scale, horizontal span represents keyway
  **length** (L) to scale. Depth is never drawn; it appears only in the PDF footer text.
- The closed (LET) end uses a **concave semicircle** matching the mill-cutter profile.
  For floating keyways both ends are semicircular; for open keyways the SET face is
  already closed by the taper's end-face line.
- Interior filled **white** so the keyway reads as a void against any taper fill colour
  (steel grey, bronze, etc.). Fill is inset one line-width from the SET face so the
  taper's end-face line retains its full visual weight.
- Fix applied identically to `ShaftRenderer` (preview) and `ShaftPdfComposer` (PDF).

---

## 2026-05-30 — Carousel extraction refactor

Extracted the component carousel out of `ShaftScreen.kt` into `ui/screen/ComponentCarousel.kt`.

**Moved to `ComponentCarousel.kt` (~740 lines):**
- `ComponentCarouselPager` — pager, selection seeding, swipe detection, LaunchedEffects
- `EdgeNavButton` — left/right arrow buttons
- `ComponentPagerCard` — per-component editor content (Body, Taper, Thread, Liner)
- `ComponentCard` — shared card chrome (title, error/warning chips, delete button)
- Carousel-private helpers: `CommitNum`, `dispKw`, `fmtTrim`, `pitchMmToTpi`, `CAROUSEL_HEIGHT`

**Stayed in `ShaftScreen.kt` (1434 lines, down from 2322):**
- All screen-level composables (header, preview, OAL badge, dialogs, FAB)
- Shared display helpers promoted from `private` to `internal`: `abbr`, `disp`, `formatDisplay`, `toMmOrNull`, `parseFractionOrDecimal`, `tpiToPitchMm`

No behaviour changes. All unit tests pass.

---

## 2026-05-30 — Doc refresh

Updated TODO.md, BRIEFING.md, and ROADMAP.md to reflect current state:
- TODO restructured around v0.5.x sprint (ShaftScreen refactor as §1). All completed v0.4.x work collapsed. Stale entries removed. Body keyway formally shelved.
- BRIEFING.md: status table updated with validation, keyways, and signing; architecture invariant corrected (dual rendering paths); component model table updated with keyway fields; active sprint section rewritten.
- ROADMAP.md: v0.4.x marked complete; v0.5.x deliverables documented; v1.0 definition of done updated.

---

## 2026-05-30 (8) — fix: sidebar UX, toolbar hamburger, runout PDF layout

### Sidebar
- **Hamburger button** replaces the Home icon in the top toolbar. Tapping it opens the sidebar overlay — no persistent rail taking up horizontal space.
- **Home button removed from toolbar** — it now lives only inside the sidebar (not duplicated).
- **Thin handle tab removed** — the sidebar is opened exclusively via the toolbar button.
- `navigationBarsPadding()` added inside the sidebar panel so Settings is never hidden under the system navigation bar.
- `statusBarsPadding()` was already present, keeping the title clear of the status bar.

### Runout PDF — complete layout rewrite
- **Bubble collision eliminated**: Bubbles are no longer placed directly below their station's axial position. Instead a fan-spread algorithm distributes bubble X positions evenly across the page width, guaranteeing no circles overlap.
- **Monotonic assignment**: Even-indexed stations → row 0 (shorter leaders), odd-indexed → row 1 (longer leaders). Because the mapping is monotonic (station order = bubble order), leader lines cannot cross each other — they fan out cleanly, exactly matching the hand-drawn reference.
- **Leaders touch the shaft**: Each leader now starts from the shaft's ACTUAL outer surface at the station's axial position (interpolated through tapers), not from a fixed maximum-diameter y.
- **Shaft centred vertically**: The shaft profile is now sized from its real maximum outer diameter and centred in the upper portion of the page, with the bubble area and TIR line filling the lower portion.

### TIR direction label (RunoutRoute)
- Label text corrected to "Looking AFT" / "Looking FORWARD" with an explanation that this determines clock-position reference (3 o'clock looking aft ≠ 3 o'clock looking forward).

---

## 2026-05-30 (7) — feat: runout drawing + wear document + sidebar nav

### Navigation
- New collapsible **sidebar icon rail** in `ShaftEditorRoute` (always visible, 52 dp collapsed).
  Three tabs: **Schematic** (always enabled), **Runout Sheet** and **Wear Document** (enabled
  once the shaft has ≥1 component and a non-zero OAL). Tab state survives configuration changes.
  Files: `EditorTab.kt`, `EditorSidebar.kt`, `ShaftEditorRoute.kt`.

### Data model
- `RunoutConfig` — new serializable data class persisted in every `.shaft` file:
  - `componentOverrides: Map<String, Int>` — per-component bubble count overrides.
  - `tirDirection: TirDirection` — AFT / FORWARD / UNSET; printed on the runout sheet.
- `TirDirection` enum in `settings/RunoutConfig.kt`.
- `ShaftDocCodec.ShaftDocV1` gains `runout_config` field (default = empty → backward-compat).
- `ShaftViewModel` gains `_runoutConfig` StateFlow, `setRunoutBubbleCount()`, `setTirDirection()`.
  Config is saved in `exportJson()`, restored in `importJson()`, reset in `newDocument()`.

### Runout PDF (`pdf/RunoutPdfComposer.kt`)
Page: landscape US Letter. Regions top→bottom:
1. **Header strip** — Customer, Vessel, Job#, Date, STBD/PORT, OAL in a single compact line.
2. **OAL span line** — Single arrow-to-arrow dimension, SET to SET only.
3. **Shaft profile** — Bodies (with compression breaks), tapers (with keyway indicators),
   liners. No dimension tiers, no component labels.
4. **Bubble area** — Each component's stations drawn as circles with diagonal leader lines.
   - Tapers: N stations (default 2) inset `RUNOUT_EDGE_INSET_MM` (25.4 mm / 1 inch) from
     each edge — readings on the edge face are unreliable.
   - Liners: same inset convention.
   - Bodies: N stations (default 3) evenly distributed, no inset.
   - Within each component, stations alternate row 0 (short leader) and row 1 (long leader)
     to avoid horizontal overlap between adjacent circles.
   - Small filled square at the top of each circle = keyway-at-top reference marker.
5. **TIR line** — "TIR's taken looking: ___" with optional direction label.

### Wear document PDF (`pdf/WearPdfComposer.kt`)
Same shaft profile + compact header, no bubbles. Dye-pen PASS/FAIL checkboxes + notes fill-in
line at the bottom. For hand-annotating damage, pitting, and inspection results in the field.

### Carousel changes (`ComponentCarousel.kt`)
- `RunoutStationControl` composable added to Body, Taper, and Liner cards. Shows
  "Runout stations: N [−] [+]" using the effective count (override or default).
- `ComponentCarouselPager` and `ComponentPagerCard` gain `runoutConfig` and
  `onSetRunoutBubbleCount` params (both defaulted — backward-compat).

### Screen routing
- `RunoutRoute.kt` — TIR direction selector + Export button; writes runout PDF via SAF.
- `WearRoute.kt` — Export button; writes wear document PDF via SAF.
- `ShaftRoute.kt` — wires `runoutConfig` and `onSetRunoutBubbleCount` from ViewModel.

---

## Versioning Notes

- Early development used git tags (`v0.2.0`, `v0.3.1`) for milestones.
- Starting with `1.1.1`, the changelog and the app `versionName` are kept in sync; future releases follow this convention.
- Note: `v0.2.0` and `v0.3.0` point to the same commit (`d1a4da5`).

## 2026-05-30 (3)

### feat: keyway drawing on taper — open and floating keyway styles

#### Model
- `Taper` gains `keywayOffsetFromSetMm: Float = 0f` (backward-compatible; default 0 = open keyway at SET face).
- `hasKeyway` extension property: true when width, depth, and length are all non-zero.
- `isValid` now enforces `offset >= 0` and `offset + length <= taperLength`.

#### Two keyway styles
- **Open keyway** (`offset = 0`, 95% case): slot starts at the SET face, open-ended there, wall only at the LET side. The Spoon toggle applies here.
- **Floating keyway** (`offset > 0`, 5% case): slot is inset from the SET face, walls on both sides. Spoon toggle is disabled and grayed in the UI.

#### Rendering
- `ShaftRenderer` draws the keyway notch on the taper's top surface in the preview: fills the notch area with the taper fill color (erasing the top outline inside the slot), redraws the top line in the two segments outside the slot, then draws walls and floor in the outline color.
- `ShaftPdfComposer` draws the same notch on the PDF canvas using a white fill to erase the top line inside the slot, with the same wall/floor logic.
- The notch floor follows the taper slope (drawn as a diagonal line matching the top surface angle).

#### UI
- Carousel taper card gains **"KW Offset from SET"** field between Length and the Spoon toggle.
- Spoon toggle is automatically disabled when offset > 0 (floating keyway has no open face to spoon).

#### Tests
- `TaperKeywayTest`: 11 cases covering `hasKeyway`, offset validation, boundary conditions, and backward-compat default.

---

## 2026-05-30 (2)

### feat: validation UI hookup — blocking errors surface in dialogs, cards, and export

#### Add dialogs (Liner + Thread)
- `CommitNumField` inside Add dialogs now accepts an `errorText` parameter and shows it in red below the Start field using `OutlinedTextField`'s `isError`/`supportingText`.
- `AddLinerDialog` and `AddThreadDialog` compute `startOverlapErrorMm` live as fields change. The **Add button is disabled** when a blocking start error exists (overlap, negative start, thread-between-components). The error message appears immediately on the start field so the user knows why.

#### Carousel component cards
- `ComponentCard` gains an `errorMessage: String?` parameter. When non-null, a Material 3 error-container chip is rendered below the card title.
- Thread and Liner cards pass their current `startOverlapErrorMm` result to this slot, so cards with placement errors show a visible red badge at all times.

#### PDF export gate
- `PdfExportRoute` now calls `blockingExportError(spec)` before launching the SAF file picker. If any thread or liner has a blocking validation error the picker is never opened; instead an `AlertDialog` displays the error message and returns the user to the editor on dismiss.

---

## 2026-05-30

### fix: selection box not shown on initial swipe after opening a file

- `ComponentCarouselPager` now seeds `selectedComponentId` immediately when components first load (via the existing `LaunchedEffect(rowsSorted.size)` that auto-scrolls to the last card), so the highlight glow appears as soon as the carousel is visible.
- Fixed swipe detection guard: the `pagerScrollStartedByUser` flag was only set when `selectedIndex == pagerState.currentPage`, but with no selection `selectedIndex` was `-1`, so all swipes were silently ignored. The guard now also triggers when `selectedComponentId` is `null`.

---

## 2026-05-29

### feat: tap-to-add pipeline, thread validation, taper-rate restoration, pdfPrefs persistence

#### Tap-to-add pipeline (TODO §1.2 + §1.3)

- `ShaftDrawing` now accepts an `onTapAtMm` lambda. Taps that land on an existing component still fire `onTapComponentId`; taps on empty space fire `onTapAtMm` with the raw mm coordinate.
- `ShaftViewModel` gains `pendingAddPositionMm: StateFlow<Float?>`, `setTapAddPosition(rawMm)` (snaps via `snapRawPositionMm` before storing), `clearPendingAddPosition()`, and `gapToNextAnchorMm(positionMm, min=50f)` (distance to next snap anchor, minimum 50 mm).
- `ShaftRoute` wires the three new callbacks to `ShaftScreen`; `pendingAddPositionMm` and the computed gap length are passed down as parameters.
- When `pendingAddPositionMm` is non-null `ShaftScreen` shows `InlineAddChooserDialog`. Selecting Body, Liner, or Taper opens the corresponding add dialog with the tapped position pre-filled in the Start field and the gap length pre-filled in the Length field. Thread routes through the existing `AddThreadDialog` with the tapped start.
- `AddBodyDialog`, `AddLinerDialog`, and `AddTaperDialog` each gain optional `initialStartMm` and `initialLengthMm` overrides that take precedence over the spec-derived defaults when provided.

#### Thread start/placement fixes (TODO §2.x)

- **End-snap bug fixed:** `applySnappedThreadUpdate` previously snapped both the start and end positions independently. This could silently extend a thread's length when the derived end position happened to land within snap tolerance of a body boundary (e.g. a 99 mm thread moved to start=0 would snap its end to the 100 mm body anchor, becoming 100 mm). The function now snaps only the start and preserves the original length.
- **"Threads at ends only" validation rule implemented:** `startOverlapErrorMm` returns `"Thread must be at a shaft end, not between components"` when a thread has a Body or Liner ending at-or-before its start *and* another Body or Liner starting at-or-after its end (i.e. surrounded on both sides). Adjacency is handled with a 1 mm epsilon so end-to-start touching qualifies.

#### Taper-rate restoration (TODO §3.2)

- `Taper` model gains a `taperRateText: String = ""` field (kotlinx.serialization `@Serializable`; backward-compatible default `""`).
- `ShaftViewModel` companion exposes `parseRateText(text)` — parses `1:12`, `3/4`, decimals, and bare integers (bare int N interpreted as 1:N) — and `deriveTaperDiameters(setMm, letMm, lengthMm, rateText)`: if both SET and LET are > 0 the rate is ignored; if only one diameter is provided the missing one is derived from the rate and length; zero length or unparseable rate returns diameters unchanged.
- `addTaperAt` and `updateTaper` accept an optional `rateText: String = ""` and call `deriveTaperDiameters` before storing. `updateTaper` also falls back to the taper's stored `taperRateText` when the caller passes a blank rate.
- The taper carousel card has a new `Rate (1:12, 3/4, or decimal)` commit field; all `onUpdateTaper` call sites pass the stored `taperRateText` through.
- `onAddTaper` / `onUpdateTaper` callbacks throughout the stack (`ShaftScreen`, `ShaftRoute`) updated from `(Float, Float, Float, Float)` to `(Float, Float, Float, Float, String)`.
- `TaperRateTest.kt` — 9 new unit tests covering `parseRateText` (colon, slash, decimal, bare int, blank, invalid) and `deriveTaperDiameters` (both provided, derive LET, derive SET, blank rate, zero length, clamp-to-zero).

#### pdfPrefs persistence (SettingsStore TODO)

- Added `KEY_PDF_OAL_SPACING_FACTOR = floatPreferencesKey("pdf_oal_spacing_factor")` to `SettingsStore`.
- `pdfOalSpacingFactorFlow(ctx)` reads the stored value (defaults to `PdfPrefs().oalSpacingFactor = 2.5f`).
- `suspend fun setPdfOalSpacingFactor(ctx, factor)` writes the clamped value to DataStore.
- `ShaftViewModel.init` now collects `pdfOalSpacingFactorFlow` and keeps `SettingsStore._pdfPrefs` in sync, matching the existing pattern for `tieringMode` and `showComponentTitles`.
- `ShaftViewModel.setPdfOalSpacingFactor(factor, persist)` added for future UI callers.
- Removed the `TODO: persist _pdfPrefs via your existing persistence layer` comment from `SettingsStore.updatePdfPrefs`; all three `PdfPrefs` fields are now fully persisted.

#### VS Code test integration

- `.vscode/tasks.json` — "Test (JVM unit tests)" (default test task), "Compile (debug Kotlin)" (default build task with Kotlin error problem matcher), "Test (single file)" (prompts for filter pattern).
- `.vscode/settings.json` — configures `java.import.gradle.*`, `java.project.sourcePaths`, `java.project.referencedLibraries`, and `java.test.config` for the Extension Pack for Java test runner.

---

## 2026-05-28 (audit low items)

- Fixed `hasCenterBreak` footer note: replaced disconnected mm-space heuristic with the same `bodyLengthMm × ptPerMm ≥ COMPRESS_TRIGGER_PT` condition used by the actual rendering code.
- `VALIDATION_RULES.md`: marked all documented-but-unimplemented non-blocking warnings as `(planned — not yet implemented)` so the doc accurately reflects current state.
- `BRIEFING.md`: updated sprint status — tap-to-select is shipped (✅), resolved component pipeline is partial (not "not started").
- Added cross-reference comments to the duplicate `END_EPS_MM = 0.5` constants in `OalComputations.kt` and `ShaftPdfComposer.kt`.

## 2026-05-28 (audit items)

- Fixed PDF component label collision: labels now use greedy row assignment so overlapping labels (e.g. AFT Thread + AFT Taper at the same position) stack into separate rows instead of printing on top of each other.
- Deleted ~200 lines of dead code from `ShaftPdfComposer.kt` (`drawLinerDimensionsPdf`, `drawDimensionsLikePreview`, `drawDimWithExtensionsAvoidingOverlap`, `drawArrowInward`, `drawZigZagBreak`, `pickAftFwdTapers`, `fmtDia`, `fmtThread`, `fmtTaper` and associated constants). Removed blanket `@Suppress("unused")` annotation.
- Corrected `docs/PDF_EXPORT.md`: PDF does not use `ShaftRenderer`; `ShaftPdfComposer` has its own geometry drawing path. Dual-path divergence is now documented explicitly.
- Added `LinerDimAdapterTest` with 8 unit tests covering `mapToLinerDimsForPdf`: AUTO proximity anchoring, forced AFT/FWD modes, offset values, measurement-space rebasing with excluded threads.

## 2026-05-28

- Replaced PDF body center-break symbol with standard engineering S-curve edges. Each compressed body stub now ends with an S-shaped cut line instead of a straight cap; both edges curve in the same direction so the break reads as two matching cut faces across a narrow gap.

---

## 2026-05-27

- Fixed PDF OAL dimension lines landing at thread tip instead of taper SET when end threads are included in OAL. `computeSetPositionsInMeasureSpace` now derives SET positions from actual taper geometry instead of hardcoding 0/OAL.
- Updated `oalSpan` to take explicit SET endpoints `(x1Mm, x2Mm)` so the OAL label always matches the arrow positions.
- Added 4 unit tests covering SET position derivation (excluded, included, no-taper, overlapping cases).
- Added `AUDIT.md` — full codebase review covering architecture, dead code, test gaps, and documentation accuracy.
- Corrected `BRIEFING.md` field name errors: `startDiaMm`/`endDiaMm`, `odMm`, `excludeFromOAL`.

---

## [1.1.1] - 2026-01-08

### Added
- `.shaft` document filenames (content remains JSON), plus legacy `.json` compatibility and migration (`c98550f`).
- Connected-device instrumentation test guard (opt-in) to protect internal saves (multiple commits).
- Component snapping engine and helpers (multiple commits).
- Developer Options for debug tooling / gated verbose logging (multiple commits).
- Saved-shaft delete support plus tests (multiple commits).
- Thread “Include in OAL” toggle (exclude end threads from OAL window) (multiple commits).
- OAL window contract tests for determinism (multiple commits).
- Preview color presets + B/W mode (multiple commits).
- Shaft position selection persisted and printed in PDF footer (`a96a889`).
- Taper keyway (KW) width/depth fields + footer output (`15701e1`).
- Developer option to show OAL value in the preview box (`c0eb165`).

### Changed
- Save/open behavior and filename suggestions improved; overwrite confirmation added (`8743637`).
- PDF export UX improved (optional auto-open after export) (`56a293d`).
- PDF layout refined (shifted content for better spacing) (`c592a1c`).
- PDF footer and taper dimensioning refined (multiple commits).
- Editor toolbar/navigation redesigned (Home button, New/Open/Save, History dropdown, overflow menu) (multiple commits).
- Editor component carousel: tighter arrows/UX tweaks (multiple commits).
- Editor component titles made deterministic and more informative (`7a2e37e`):
    - Bodies: physical aft→fwd numbering.
    - Liners: positional AFT/MID/FWD naming; numbers only when needed; optional user override via inline title editing.
    - Tapers: AFT/FWD direction naming based on diameter trend; numbers only when needed.
- Preview overlay: removed OAL from the Free-to-End badge; Free-to-End only shows in Manual mode (`c0eb165`).
- App locked to portrait for more predictable editor layout (`700d8b2`).
- “Shaft Editor” header typography strengthened for clearer hierarchy (`7a2e37e`).
- Project/docs and dev tooling iterated (multiple commits).
- Android Gradle Plugin bumped (`070d916`).

### Fixed
- Gradle connected-test safety guard adjusted for Kotlin DSL compatibility (`c98550f`).
- Feedback email chooser behavior (`c80a7d5`).
- Stabilized component delete behavior (remove action timing, snackbar/undo flow) (multiple commits).
- Fixed PDF scaling/layout edge cases, taper dimension rendering, and unit-safe footer formatting (multiple commits).
- Settings and Developer Options screens are scrollable so all options are reachable (`27a8761`).

### Internal
- Version bump to `1.1.1` (`1027792`).
- Changelog refresh work (`4e502de`).

---

## [0.3.1] - 2025-09-16

### Added
- Full-rectangle preview rendering for components (multiple commits).
- Editor UI structure improvements (FAB + bottom sheet; more usable scaffolding) (`2f99695`).

### Changed
- Editor unit handling and dropdown behavior improved (`663157a`).
- Taper handling + input UX improvements (`48aaad6`).
- PDF title block / layout helpers refactor (`2d2f61c`).

### Fixed
- Updated `ShaftDrawingView` layout call to match `ShaftLayout` API (`2f424bd`).

## [0.2.0] - 2025-09-14

### Added
- Coverage chip hint (`coverageChipHint()`) in `ShaftViewModel` for concise, unit-aware messages.
- Settings menu in `TopAppBar` with toggle to choose between **chip-style** or **text-style** coverage hints.
- `SettingsDialog` component with temporary state management (persistence TODO).
- Export PDF action added to `TopAppBar` (optional), keeping Floating Action Button export as well.

### Internal
- Initial changelog created (`d1a4da5`).

### Changed
- Updated `ShaftScreen` scaffold to include Settings and Export actions in the `TopAppBar`.
- Improved overall UI structure and consistency.

---

## [0.1.0] - Initial Commit

### Added
- Project setup with package name `com.android.shaftschematic`.
- Core data models: `ShaftSpecMm`, `BodySegmentSpec`, `KeywaySpec`, `LinerSpec`, `TaperSpec`, `ThreadSpec`.
- `UnitSystem` enum for inches/mm conversion.
- `ShaftViewModel` with state flows for spec + unit handling.
- `ShaftScreen` UI with Compose, including input fields for:
    - Basics (length, diameter, chamfer, shoulder length).
    - Body segments (dynamic add/remove).
    - Keyways (dynamic add/remove).
    - Tapers with ratio handling.
    - Threads (forward + aft).
    - Liners (dynamic add/remove).
- Export-to-PDF feature using `ShaftPdfComposer` with:
    - Span/segment drawing.
    - Tapers, threads, keyways, liners.
    - Dimension arrows and overall length.
    - Simple title block with project info.
- Git integration with `.gitignore`, initial README, and project structure.

### Internal
- Initial project import (multiple commits).
