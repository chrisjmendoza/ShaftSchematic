# On-device checklist

Things that shipped to `main` but have not yet been judged on a phone or tablet. Each item says
what to do, what "good" looks like, and where it came from. Tick it off (or delete it) once you
have looked; move anything that fails to `TODO.md` §Bugs with what you saw.

Everything here is already green in the unit suite and `lintVitalRelease` — this list is about
how it reads on hardware and on paper, which the tests cannot judge.

A headless AVD pass on 2026-10-27 (phone `calc_test` and `Pixel_Tablet`, driven over adb) covered
the functional items: what it passed is gone from this list, and what it failed is either fixed
(re-check below) or in `TODO.md` §Bugs. It cannot judge paper, the printer dialog, the SAF picker,
haptics, TalkBack, pinch-zoom (single-touch input only) or how a sheet reads to the shop — those
items stay.

Last updated: 2026-10-27.

---

## Fixed 2026-10-27 — re-check on hardware

Failures from the AVD pass, fixed on `fix/on-device-pass-2026-10-27`. One line per fix: what to
look at, what good looks like.

### Undercut, wear, previews
- [ ] **Crowded undercut page** (4 sections, Auto portrait): the FWD strip's nested row and every
  below-chain value sit in clear air under the chain, never on the liner outline.
- [ ] **Strip order**: strips print AFT→FWD by their first cut — a bare-shaft cut AFT of the AFT
  liner prints above the AFT-liner strip, not below it.
- [ ] **Detail canvas while a draft moves**: butt a cut to the far side of a neighbour (or move it
  past the old chain start) — the chain and the S.E.T. lead follow the draft at once, with no
  phantom gap where it was and no missing span.
- [ ] **Discard resets the butt chips**: pick "On the AFT side of: Cut 1", then Discard ✗ — no chip
  stays selected and no (negative) gap field is left on the card.
- [ ] **Wear sheet**: the "FWD Liner" name sits clear of the "FWD →" marker at the right end of the
  whole-shaft view.
- [ ] **Undercut preview with PDF options open**: the "Notes: ____" line at the foot of the page
  strip shows above the panel, landscape and portrait.

### Editor
- [ ] **Taper card Rate** (Manual): type `3/4"/ft` — it is accepted and the footer prints
  "Rate: ¾"/ft".
- [ ] A FWD-referenced taper flush with the FWD end shows **`0`, not `-0`**, in "Start from FWD".
- [ ] **Footer fraction spacing**: "Rate: ¾"/ft", "Thread relief: ⅜" × …", "KW: 1¼" × ⁹⁄₁₆" × …" each
  keep the space before the fraction.
- [ ] **Phone editor bar** shows its icons with no ".." title; a tablet still shows "Shaft Editor".
- [ ] **Add Body after the new-shaft prompt** offers the bare-shaft Ø (8″ on an 8″ shaft, not 0.984″).
- [ ] **Add Taper after the new-shaft prompt** seeds its S.E.T. Ø from the bare-shaft Ø too (8″, not
  0.984″); a body or taper already on the drawing still wins.
- [ ] **Add Coupler Bolt Slot** opens with Add enabled (position seeded 1″ from the face).
- [ ] **Empty Final tab** after "Discard final drawing" has History — Undo from right there.
- [ ] **Add Thread dialog**: the "Relief at" chips look like the dialog's other chip rows; the
  switches, chips and typed values survive a system font-scale change.

### Theme, font, large text
- [ ] **Output font** (Serif): the dimension-rail values and Ø callouts change face with the names
  and footer — the whole sheet in one face.
- [ ] **Help search**: every new query opens on its first result, never scrolled past it.
- [ ] **Dark / High-contrast Dark**: the status-bar clock and icons are visible (light on the dark
  bar).
- [ ] **High contrast**: the Wear / Undercut overview tap tint, border and badge, and the undercut
  detail canvas's selection, are the fixed purple ink — not amber on the white paper.
- [ ] **Settings switch rows** (High contrast included) toggle from a tap on their label.
- [ ] **200% system font**: the sidebar scrolls to its Settings row; chip rows wrap whole chips onto
  a second line (thread card Thread end, "Metric (mm)", undercut Page) instead of breaking a word;
  the OAL label reads "OAL (in)" unclipped; PDF preview titles take at most two lines.
- [ ] **Schematic names**: neighbouring names ("AFT Thread", "AFT Taper") and a liner's name beside
  its Ø callout print with a clear gap (≈ 8 pt), not as one run.
- [ ] **Add dialogs on a fully built shaft** (814689) open on the first bare-shaft section, not at
  the OAL.
- [ ] **Runout canvas**: in Move mode TalkBack reads the move gestures; the outermost bubbles' rings
  are no longer clipped at the canvas edge.

### Tablet
- [ ] **Rotation keeps tab state**: Runout "Blank draft" ON stays ON over two rotations; an open
  undercut detail page survives landscape → portrait; a closed one never comes back later.
- [ ] **Typed, uncommitted values survive rotation** (an undercut's Measured Ø), and the runout
  reading dialog stays open with its TIR and high spot.
- [ ] **Final tab, empty, landscape**: no hamburger beside the permanent sidebar.
- [ ] **Portrait tablet (MEDIUM) editor**: the component card runs to the foot of the screen with
  its Diameter field whole.
- [ ] **Undercut detail page, landscape, keyboard up**: the drawing gives way and the card being
  typed into stays on screen.
- [ ] **Undercut / Wear / Runout preview overlay, landscape**: the status-bar strip is the app-bar
  colour, not black.

---

## Undercut + previews (2026-10-22 — later merges)

*Functional behaviour verified on the AVD 2026-10-27; the paper/eye judgment is still yours.*

### Total length of cuts — `4f9c5ff`
- [ ] Does a sheet without the total read clearly to the coworkers who asked for it?

### Cut numbers count within each section — `ac1bb2a`
- [ ] A cut on no section sits under "Not on the shaft" in the Undercut tab's list (the AVD pass
  could not construct one).

## Undercut Drawing (2026-10-21 — three merges)

*Functional behaviour verified on the AVD 2026-10-27; the paper/eye judgment is still yours.*

### Rail values lift above the chain — `d2ed7c6`
- [x] **Single section, landscape.** Short cuts' values sit ABOVE the chain line under the total
  rail, each with a thin leader down to its own span; only the nested row sits below. *Verdict
  2026-10-21: "BEA-UTIFUL".*
- [ ] Pure-fraction values (`15/16"`, `5/16"`) print at the small digit size next to `1 3/8"`.
  Pre-existing fraction typography, not part of the lift. Decide whether it bothers you on paper.

### Portrait page, S.E.T. lead, Sections to print — `08b1727`
- [ ] **Portrait on paper.** A job with 2+ sections prints portrait with the strips stacked AFT→FWD,
  full width. Header line fits (long customer names shrink slightly). Each strip's title sits
  under its own figure, not floating between two.
- [ ] **Printer dialog** asks for portrait paper when the sheet is portrait.
- [ ] **Export all** (Output tab) with every undercut section unticked skips the undercut drawing
  and says so in its result line.

### Shop-user pass — `ad72e5a`
- [x] **Add undercut** button opens "Where is the cut?" (liner rows + bare shaft AFT/FWD). Cancel
  records nothing. A liner row opens a "New undercut" card; backing out leaves no phantom cut.
  *Verdict 2026-10-22: opens correctly.*
- [x] **Pill wording**: "Recorded" when clean, "Record cut" on a new cut, "Confirm change" on an edit.
  *Verdict 2026-10-22: all three working correctly.*

### Older undercut items still open
- [ ] Print one undercut sheet with **Line art (no shading)** on: do the open steps + full-height
  faces read on paper without the grey core? (The ruling assumed yes from the screen mode.)

---

## Undercut card UX pass 2 + S-break margins (2026-10-22, one merge)
Use job 935061 (Polar Ranger STBD), MID liner page.

*Functional behaviour verified on the AVD 2026-10-27; the paper/eye judgment is still yours.*
- [ ] **S-break margins** on the detail canvas (second merge, 2026-10-22): both ends show the SAME
  stock past the chain — about 8 in each on a phone — so the cuts sit centred; the S.E.T. lead's
  line stubs are shorter and the white inset at each screen edge is halved, so the drawing is a
  little larger. If 8 in a side still reads long, say so: the lever left is the lead's text
  clearance.

## Help & FAQ paragraphs — `b7424f9` (2026-10-21)
- [x] Expand "Record undercut sections" and a bullet topic (Settings Reference → Editor Screen).
  Paragraphs sit a clear gap apart and bullets a smaller gap apart; neither reads too loose.
  *Verdict 2026-10-21: "Help looks great. I'm very happy with that."*

## Undercut nested rail rows — `cbcc0d7` (2026-10-21)
- [x] Polar Ranger STBD, FWD liner strip: the nested 4¾″ / 2⅜″ / 5½″ row sits one row higher,
  a clear row of air between it and the shaft, its values clear of the chain's arrowheads.
  *Verdict 2026-10-21 (from the regenerated sheet): "Beautiful."*

## In-app manual — `8feedc3` (2026-10-14)
- [ ] On a tablet, a rotation restores the manual's section (not the exact scroll); the new
  Undercut figure is the portrait sheet with both S.E.T. leads. (The phone is portrait-locked, so
  the AVD pass could not rotate it, and the figure was not inspected.)

## Runout Move mode — `a5508c2` (2026-10-09)

*Functional behaviour verified on the AVD 2026-10-27; the paper/eye judgment is still yours.*
- [ ] Judge the pickup haptic and the dashed component frame.

## PDF options sheets (2026-10-03 — `7ae2976`, `1635fa8`)

*Functional behaviour verified on the AVD 2026-10-27; the paper/eye judgment is still yours.*
- [ ] **Sections**: judge the order and which start open on each document's sheet.
- [ ] **Non-modal panel**: with the panel up, pinch-zoom the page, then drag a slider — the
  re-render keeps your zoom.

## Threads (2026-10-02/03 — `f7c7c88`, `7c1ca09`, `e893cb6`)

*Functional behaviour verified on the AVD 2026-10-27; the paper/eye judgment is still yours.*
- [ ] **Hatch slant** 3× default and **density** slider — judge both on a printed schematic.
- [ ] **Thread relief** on paper: the groove prints at the S.E.T. end (too thin to judge at screen
  raster).
- [ ] Known open: a very full **dual-units consolidated sheet** can print overlapping rail labels
  when the footer grows a line. Try one if you print dual units.

## Seal areas / component blend (2026-09-29 — `c212494`, `64a3434`, `eb2795a`, `80c4804`)
- [ ] Printed: the S-break gap stays clear of the seal cuts, and a blend at the same face does not
  push the dashes inward (neither was exercised on the AVD).
- [ ] Footer reserves its height (no overlap on a long footer); the coupling switch.

## OAL commit-on-accept — `a8ff9cb` (2026-09-29)
- [ ] Blur-with-change commits (✓, Done, ✗ and an emptied field were verified on the AVD; a blur
  had no reliable target there).

## Coupler cross-drilled bolt hole — `b8e4e0c` (2026-09-17)
- [ ] Bolt rows never compress inside a compressed body (true pitch).

## Final Schematic tab (2026-09-14)
- [ ] Final exports carry the `_Final` filename suffix (needs the system file picker).

## Misc shipped-unverified (2026-09-14 … 10-13)
- [ ] **Manual bug batch** (`ead7a45`): TIR fractions; one filename builder (a `/` in a customer
  name never reaches the filename).

---

## Device-class passes (bigger sittings)

- [ ] **Accessibility**: one TalkBack pass over the five sheet tabs.
- [ ] **Material3 1.4.0**: decide the pinch-zoom centroid question (where a pinch zooms from).

## One-time setup (not a device check, but owed)
- [ ] **Crashlytics activation**: add Crashlytics to the Firebase app, download
  `google-services.json`, add the `GOOGLE_SERVICES_JSON` Actions secret, force one test crash from
  a distributed build.

---

### How to report back
A screenshot or a photo of the paper plus one line ("good", or what is off) is enough. Anything
that fails goes to `TODO.md` §Bugs; anything that passes gets deleted here.
