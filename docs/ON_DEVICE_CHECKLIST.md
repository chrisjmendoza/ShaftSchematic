# On-device checklist

Things that shipped to `main` but have not yet been judged on a phone or tablet. Each item says
what to do, what "good" looks like, and where it came from. Tick it off (or delete it) once you
have looked; move anything that fails to `TODO.md` §Bugs with what you saw.

Everything here is already green in the unit suite and `lintVitalRelease` — this list is about
how it reads on hardware and on paper, which the tests cannot judge.

Last updated: 2026-10-22.

---

## Undercut + previews (2026-10-22 — later merges)

Use job 935061 (Polar Ranger STBD) and one job with two or more liners holding cuts.

### Total length of cuts — `4f9c5ff`
- [ ] **Per-job switch** (Undercut tab → Drawing settings, under Split cuts at liner edges; also in
  the undercut preview's Options). Off: every section prints its cut lengths and S.E.T. lead with
  NO overall dimension above them, and the strip loses that band rather than leaving a gap. The
  zoomed detail page matches. On again brings it back. Undo steps it.
- [ ] **Settings default** (Settings → PDF Export Options → "Undercut drawing: total length of
  cuts"). Off there: a job that never touched its own switch prints without the total, and its
  caption says it is following the default; a job switched on itself keeps its total.
- [ ] Does a sheet without the total read clearly to the coworkers who asked for it?

### Cut numbers count within each section — `ac1bb2a`
- [ ] On a liner holding several cuts, the detail canvas numbers, the "Cut N of M" card titles and
  the edge-to-edge chips all count from 1 in that section.
- [ ] The Undercut tab's list groups cuts under each section's heading, numbered from 1 under each;
  a cut on no section sits under "Not on the shaft". Deleting from the list says "Cut N deleted —
  <section>" and Undo restores it.

### Live preview sliders — `e8432c1`, `a5128fc`
- [ ] **Undercut preview**: drag Line thickness — the page redraws under the finger, then sharpens
  on release.
- [ ] **Wear preview**: same for Line thickness and Shaft height.

## Undercut Drawing (2026-10-21 — three merges)

Use job 935061 (Polar Ranger STBD) and one job with two or more liners holding cuts.

### Rail values lift above the chain — `d2ed7c6`
- [x] **Single section, landscape.** Short cuts' values sit ABOVE the chain line under the total
  rail, each with a thin leader down to its own span; only the nested row sits below. *Verdict
  2026-10-21: "BEA-UTIFUL".*
- [ ] **Crowded page** (4 sections, or the landscape 2×2 grid). Short values stay BELOW the chain as
  before — the lift must not shrink the shaft to make room.
- [ ] **Zoomed detail page** (tap a liner on the Undercut tab). Same lifted layout as the printed
  strip — it always lifts there.
- [ ] Pure-fraction values (`15/16"`, `5/16"`) print at the small digit size next to `1 3/8"`.
  Pre-existing fraction typography, not part of the lift. Decide whether it bothers you on paper.

### Portrait page, S.E.T. lead, Sections to print — `08b1727`
- [ ] **Portrait on paper.** A job with 2+ sections prints portrait with the strips stacked AFT→FWD,
  full width. Header line fits (long customer names shrink slightly). Each strip's title sits
  under its own figure, not floating between two.
- [ ] **Page: Auto | Landscape | Portrait** (Drawing settings, and the preview's Options). Forcing
  Landscape on a 2-section job gives the old 2-column grid; forcing Portrait on one section
  centres it. Caption says "this sheet prints portrait (2 sections)".
- [ ] **Printer dialog** asks for portrait paper when the sheet is portrait.
- [ ] **S.E.T. lead.** Every strip with cuts ends its chain with a half dimension (one arrowhead at
  the cut/liner edge, open end at the strip edge) whose value is the distance to the S.E.T. The
  value sits IN the line, not on a leader. Title and lead name the same S.E.T.
- [ ] **Distance to S.E.T.: Nearest | AFT | FWD.** Switching flips both the title and the lead; on
  the FWD liner, AFT gives a long figure (hundreds of inches) and the title turns to "FROM AFT
  S.E.T.".
- [ ] **Draw to the shaft end + lead.** With it on, the FWD strip chains to the S.E.T. (`12 3/4"`)
  and prints NO extra lead on that side; the MID strip still has its lead.
- [ ] **Sections to print** (above the Print buttons, 2+ sections). Untick one: the list rows for
  its cuts say "Not on the printout", the page goes landscape if one section remains, and the
  sheet prints only what is ticked. Untick all: Print/Export greyed with the message, Preview
  shows the "No undercut sections selected" page.
- [ ] **Export all** (Output tab) with every undercut section unticked skips the undercut drawing
  and says so in its result line.

### Shop-user pass — `ad72e5a`
- [x] **Add undercut** button opens "Where is the cut?" (liner rows + bare shaft AFT/FWD). Cancel
  records nothing. A liner row opens a "New undercut" card; backing out leaves no phantom cut.
  *Verdict 2026-10-22: opens correctly.*
- [ ] **Card order** on a phone: Measure From, Distance, Length, Ø, Notes, then "+ Next cut", then the
  collapsed "More ways to place this cut" — Distance should be visible without scrolling the card.
- [x] **Pill wording**: "Recorded" when clean, "Record cut" on a new cut, "Confirm change" on an edit.
  *Verdict 2026-10-22: all three working correctly.*
- [ ] **Fraction hint** under Distance and the `e.g. 5.625 or 5 5/8` placeholder in an empty Ø field
  (placeholder shows while focused and empty).
- [ ] **Past the liner edge** question reads as a yes/no ("Yes, it does" / "No, let me check").
- [ ] **Drawing settings** is collapsed below the list; order Page, Distance to S.E.T., Split cuts at
  liner edges, Drawn depth, Draw to the shaft end.
- [ ] **Split cuts at liner edges** is where you would look for it — directly under Distance to
  S.E.T. in both the tab and the preview's Options, no longer under Shade in Components
  (`d2ed7c6`).

### Older undercut items still open
- [ ] Print one undercut sheet with **Line art (no shading)** on: do the open steps + full-height
  faces read on paper without the grey core? (The ruling assumed yes from the screen mode.)
- [ ] **Undercut gap after/before Cut N** field (2026-10-20, `f92eb1f`): type a gap after Cut 1 and
  check the Distance re-reads from the datum and prints correctly.

---

## Undercut card UX pass 2 + S-break margins (2026-10-22, one merge)
Use job 935061 (Polar Ranger STBD), MID liner page.
- [ ] **Discard ✗** on the "Record cut" / "Confirm change" pill is a round white button with a red ✗
  that reads as a button in light AND dark theme; tapping it discards and records nothing.
- [ ] **"More ways to place this cut"** sits under a divider as a heading; opened, its chips sit in a
  tinted panel. It is absent on a card with nothing behind it.
- [ ] **Whole liner** chip is offered on an empty liner and NOT once the liner has a recorded cut.
- [ ] **Extend past liner** fields are gone. A cut running past the liner shows one grey line under
  Length ("Extends 3.5 in past the liner's FWD edge"); answer "No, let me check" to the question
  and the red warning replaces that line for that end.
- [ ] **Butt chips** read "On the AFT side of:" then "On the FWD side of:" with "Cut N" chips; the
  gap field reads "Gap to Cut N (in)". Tap each once and confirm the cut lands on the side named.
- [ ] **S-break margins** on the detail canvas (second merge, 2026-10-22): both ends show the SAME
  stock past the chain — about 8 in each on a phone — so the cuts sit centred; the S.E.T. lead's
  line stubs are shorter and the white inset at each screen edge is halved, so the drawing is a
  little larger. If 8 in a side still reads long, say so: the lever left is the lead's text
  clearance.
- [ ] **PDF options on the undercut preview**: opening the panel moves the page into a strip under
  the top bar (cropped to the drawing) with the panel below it — on a portrait page (2+ sections)
  the strip fills the width. The Undercut tab's own Options panel and the detail page's Drawing
  options stop below their canvas.
- [ ] **Printed strip** (Preview): equal plain stock before both breaks on every strip; the manual
  figure shows the same.

## Help & FAQ paragraphs — `b7424f9` (2026-10-21)
- [x] Expand "Record undercut sections" and a bullet topic (Settings Reference → Editor Screen).
  Paragraphs sit a clear gap apart and bullets a smaller gap apart; neither reads too loose.
  *Verdict 2026-10-21: "Help looks great. I'm very happy with that."*

## Help search — `2565ea3` (2026-10-21)
- [ ] Search "undercuts": "Record undercut sections" is the first card; the matched words are
  highlighted (bold on a tinted background) in the heading, title and text. Check the highlight
  colour reads in dark theme and high contrast.
- [ ] Search "how do I add a liner": the filler words are ignored and the liner topics lead.

## Undercut nested rail rows — `cbcc0d7` (2026-10-21)
- [x] Polar Ranger STBD, FWD liner strip: the nested 4¾″ / 2⅜″ / 5½″ row sits one row higher,
  a clear row of air between it and the shaft, its values clear of the chain's arrowheads.
  *Verdict 2026-10-21 (from the regenerated sheet): "Beautiful."*

## In-app manual — `8feedc3` (2026-10-14)
- [ ] Help → User manual opens; dark theme keeps the page light; a rotation restores the section
  (not the exact scroll). The new Undercut figure is the portrait sheet with both S.E.T. leads.
- [ ] Tap a Help topic's "manual" link — it lands on the right section.

## Runout Move mode — `a5508c2` (2026-10-09)
- [ ] Corner toggle on; touch picks a bubble up at once; tap selects a bubble or a component; the
  −/+ toolbar acts on the selection. Judge the pickup haptic and the dashed component frame.
- [ ] Toggle off: the canvas behaves exactly as before.

## PDF options sheets (2026-10-03 — `7ae2976`, `1635fa8`)
- [ ] **Sections**: judge the order and which start open on each document's sheet.
- [ ] **Non-modal panel**: with the panel up, pinch-zoom the page; drag a slider — the page re-renders
  under the finger and keeps your zoom; the panel never covers the page (page strip + capped panel).
- [ ] Wear sheet: a commit redraws the page while the panel is up (page-strip layout).

## Threads (2026-10-02/03 — `f7c7c88`, `7c1ca09`, `e893cb6`)
- [ ] **Hatch slant** 3× default and **density** slider — judge both on a printed schematic.
- [ ] **Thread relief**: switch on the card + Add dialog, Relief length / Ø / "Relief at" chips;
  the groove prints at the S.E.T. end and follows the thread when its end flips.
- [ ] Known open: a very full **dual-units consolidated sheet** can print overlapping rail labels
  when the footer grows a line. Try one if you print dual units.

## Keyways (2026-10-02 — `6d794ec`, plus standard sizes / captured)
- [ ] ONE "Keyway" checkbox gates the whole section on Body/Taper cards and both Add dialogs;
  unticking with values asks first; Undo brings the values back.
- [ ] Captured keyway toggle + inset field; "Standard size…" picker fills W × D through the typed path.

## Seal areas / face finish (2026-09-29 — `c212494`, `64a3434`, `eb2795a`, `80c4804`)
- [ ] **Square | Blend** chips (labels above, outlines on all chips); **Seal areas** AFT/FWD as their
  own section with a length under each ticked face.
- [ ] Printed: dashed seal cuts anchored at the face, silhouette flat through them, S-break gap clear
  of them; a blend at the same face does not push the dashes inward.
- [ ] Footer reserves its height (no overlap on a long footer); Ø prints 3 decimals unless whole
  (`10.990"`, `8"`); the coupling switch.

## OAL commit-on-accept — `a8ff9cb` (2026-09-29)
- [ ] Edit 368.5 → 367.75: nothing commits per keystroke; ✓ / Done / blur-with-change commits, ✗
  reverts; an emptied field reverts and never zeroes the shaft.

## Coupler cross-drilled bolt hole — `b8e4e0c` (2026-09-17)
- [ ] Hole: Seam | Cross-drilled; From keyway 90° draws the hidden bore (dashed walls), In line draws
  the circle; footer lines (Ø, "Hole center from FWD/AFT", clocking note only with a keyway).
- [ ] Bolt rows never compress inside a compressed body (true pitch).

## Final Schematic tab (2026-09-14)
- [ ] "Start from original schematic", edit a liner on the Final only — the original is untouched;
  Final sheets carry `Drawing: Final`, the FINAL badge and `_Final` filenames; Reset / Discard undo.

## Label / Ø-callout collision — `1f58ec9` (merged 2026-09-23)
- [ ] A component printing both a name and a Ø below the shaft: the name slides along its span or
  drops a row; it never prints through the callout.

## Misc shipped-unverified (2026-09-14 … 10-13)
- [ ] **Output font** (Settings → Drawing) changes every sheet's typeface, previews re-render.
- [ ] **Tap-to-rename** title and draft names.
- [ ] **Manual bug batch** (`ead7a45`): new drawings follow Settings → Units; TIR fractions; taper
  `3/4"/ft`; one filename builder (a `/` in a customer name never reaches the filename).
- [ ] **Share diagnostic logs** (Settings → Data) attaches the two log files once.
- [ ] **Component names on drawing** is the FIRST row of Settings → Drawing (moved off PDF Export,
  `4bc3667`); a component card's "Show name on drawing" row says it is following that setting.
- [ ] **Add Thread dialog** (`fa189bf`, `b2eb76e`): the column scrolls on a small phone; the chip
  reads "Metric (mm)"; a thread added with "Include in OAL" off hangs off the chosen shaft end.
- [ ] **Selection ring** in the editor preview is the thinner 4 px ring (`90613d4`).

---

## Device-class passes (bigger sittings)

- [ ] **Tablet pass** (`e80ae82`): every tab in portrait (MEDIUM) and landscape (EXPANDED); the
  permanent sidebar; rotate mid-edit on each tab; carousel card width in its pane; the tuning
  page strip in landscape; the PDF preview closing on a tablet (must stay free to rotate). Judge
  the 0.55/0.45 pane split and the 720 dp readable width by eye.
- [ ] **Dark and high-contrast** chrome pass (Settings → Appearance). The five white sheets must
  stay white with black ink.
- [ ] **Accessibility**: 200% system font (any clipped row), one TalkBack pass over the five sheet
  tabs.
- [ ] **Material3 1.4.0 visual pass**: tuning sliders, options panel page-strip cap, Square | Blend
  chips, AlertDialogs, Switch/Checkbox density, numeric-field blur commits; decide the pinch-zoom
  centroid question (where a pinch zooms from).

## One-time setup (not a device check, but owed)
- [ ] **Crashlytics activation**: add Crashlytics to the Firebase app, download
  `google-services.json`, add the `GOOGLE_SERVICES_JSON` Actions secret, force one test crash from
  a distributed build.

---

### How to report back
A screenshot or a photo of the paper plus one line ("good", or what is off) is enough. Anything
that fails goes to `TODO.md` §Bugs; anything that passes gets deleted here.
