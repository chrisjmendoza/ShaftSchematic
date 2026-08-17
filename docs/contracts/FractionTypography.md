# Fraction Typography

How a fraction is SET wherever the app draws one — previews and PDF exports alike.

Two files own it:

| File | Role |
| --- | --- |
| `util/FractionText.kt` | Pure parser. Splits a label into plain / fraction / gap runs. No Android. |
| `util/FractionTextRenderer.kt` | Paint + Canvas. Measures and draws those runs. |

Public API is one pair of extensions, and they must always be used **together**:

```kotlin
val w = paint.measureRichText(label)          // instead of paint.measureText(label)
canvas.drawRichText(label, x, y, paint)       // instead of canvas.drawText(label, x, y, paint)
```

---

## 1. Why the label is parsed, not formatted

Labels are assembled all over the app and most of them concatenate a formatted length into a
sentence — `"OAL: 12 5/8\""`, `"1/4\" × 1/8\" × 2 1/2\""`, `"3/16\" from AFT"`. Parsing at the
draw site means every one of those gets built-up fractions without its producer knowing anything
about typography, and a fraction arriving from somewhere unexpected (a user-typed taper rate,
a liner label) is set the same way as one from `LengthFormat`.

`LengthFormat.formatInchesSmart` therefore emits **plain `n/d` for every denominator** and never
a Unicode vulgar glyph. A font only carries `¼ ½ ¾ ⅛ ⅜ ⅝ ⅞` — which is exactly why one sheet
could print a proper `⅝` beside a typed-out `11/16`, two different constructions at two
different weights and heights. One spelling in, one look out. Pinned by `LengthFormatTest`.

`printedTaperRate` (the footer's `1:16 → 3/4"/ft` shop shorthand in `ShaftPdfComposer.kt`) spells
its fraction plain for the same reason; pinned by `TaperRatePrintNotationTest`.

The parser still recognises vulgar glyphs and the Unicode fraction slash `⁄` on the way in, so
text that already carries them renders identically.

## 2. What counts as a fraction

A digits-slash-digits token whose neighbours are **not** a digit, `.`, `,` or another slash.
That guard is the whole safety story:

| Input | Result |
| --- | --- |
| `3/16`, `399/4000`, `12 5/8"` | set as a fraction |
| `12/25/2026` | plain — a date |
| `1.5/2`, `3/16.5` | plain — part of a decimal |
| `1/0` | plain — a typo, not a fraction |
| `1:12`, `in/ft` | plain — no slash between digits |

Nothing caps the denominator. 64ths are nowhere near the limit; a four-digit denominator just
widens the bar.

### The guard is not enough on its own — free text draws plain

A job number like `24/1138` is a valid fraction by every rule a parser can see. The only thing
that knows better is the field it came from, so the **caller** decides: the footer's free-text
fields (Customer / Vessel / Job # / Date) keep plain `drawText`, and only generated spec lines
go through `drawRichText`. `ellipsizeToWidth(…, rich = …)` carries the same distinction — its
flag must match how the caller will draw, or a line is clipped at the wrong width.

## 3. The glyph

`FractionStyle.STACKED` is the shop-ruler fraction: numerator over denominator, bar centred on the **math axis**
(half the base font's cap height above the baseline — where a minus sign sits), which is what
makes the fraction read as centred on the same optical line as the text beside it.

`FractionStyle.DIAGONAL` — raised numerator, slanted solidus, denominator on the baseline — is
the **shipped** construction, on an on-device verdict that it reads better on screen and in
print at the sizes these sheets are actually read at. `FractionStyle.INLINE` draws plain `n/d`.
All three are user-selectable; see §3.1.

`FractionStyle.Default` is the single source for that choice — `PdfPrefs.fractionStyle`'s
default, `FractionTextStyle.Default` and `fromName`'s fallback all read it, so a fresh install,
an unreadable stored value and the renderer's baseline can never disagree.

Proportions are ratios of a font size, never absolute points, so one preset serves the 7 pt rail
label and the 30 px preview caption alike.

**Spacing is per-style, not shared** — `FractionTextStyle.Stacked` / `.Diagonal` / `.Plain`,
selected by `forStyle`. The two constructions present different ink at the edges of their box, so
one pair of numbers cannot serve both (on-device report: a spacing that read well diagonal read
slightly off stacked):

- **Stacked** leads with the BAR — a horizontal stroke reaching the box edge at mid-height, which
  binds to a preceding digit the way a hyphen would — and closes on a numerator row that a
  trailing `"` attaches itself to. It carries the looser gap (0.14) and the side bearing (0.045).
- **Diagonal** leads with the numerator's own glyph at cap height and closes on the baseline, so
  it takes the tighter gap (0.10) and **no side bearing at all** — side bearing is a stacked-only
  metric, since diagonal's box opens and closes on glyph ink — and being the wider construction,
  it is the one that should spend less on air.

Never `copy(style = …)` one preset onto another style; that is exactly the bug `forStyle` exists
to prevent, and `FractionStyleSettingTest` pins it.

### The size contract that keeps layout untouched

Digits sit at **0.64** of the base size — pushed to the largest value that still keeps the whole
stack inside the base font's own ascent and descent. Because the stack lands **inside the normal
line box**, nothing that budgets vertical space for a line of text had to change or even know:
not the dimension-rail planner's label bands, not the strip rails' rows, not the footer's line
pitch. `FractionTextRendererTest` pins this by rendering to a bitmap and checking the ink
bounds — raise `digitScale` too far and that test fails, which is the intended alarm.

Width is the only thing that moves, and it moves **down**: a stack is one digit column wide, not
two plus a slash. At the schematic's 7 pt rail size, `21 5/16"` measures 27 pt plain, 16.3 pt
stacked, 19.4 pt diagonal.

### Width buys inline eligibility

A value seats in a break only if the span affords `labelWidth + 2·textPad + 2·arrowSize`
(`DimensionRailLayout.canFitInwardArrows`); otherwise it falls back above a continuous line. So a
narrower construction is not just tidier — it seats more values.

`PdfDimensionRenderer.textPad` is therefore the shared `DIM_BREAK_TEXT_PAD_PT` (4 pt), the same
gap the wear/undercut strip rails already cut, rather than the 6 pt it used to carry on its own.
Padding wider than the value itself pushed short spans into the fallback for no gain in
legibility (on-device report: a value that plainly fitted its rail printed above it). For
`21 5/16"` at 7 pt the requirement went 36.6 pt → 32.3 pt.

Diagonal costs about 3.6 pt more than stacked and so seats slightly less often; Settings →
Drawing → "Dimension arrows" at **Small** (3 pt) gives 2 pt of that back.

## 3.1 The setting, and why it is a mirror rather than a parameter

`PdfPrefs.fractionStyle` — **Settings → Drawing → "Fractions"**, and the same
`FractionStyleChips` picker in both PDF options sheets (ungated there, unlike the arrowhead
size: every document those sheets serve prints lengths, so every one draws fractions). Diagonal
is the shipped default; Plain restores flat `n/d`.

The draw sites do **not** take a style parameter. They read `FractionTypography.active`, a
process-wide `@Volatile` mirror — the same shape as `SettingsStore.pdfPrefs` and for the same
reason recorded when that one was reviewed: this is a uniform, app-wide drawing decision with no
per-call variation, and threading it by hand through every composer's private draw functions
costs more than it buys.

Two consequences worth knowing:

- **`SettingsStore.updatePdfPrefs` is the only writer.** It mirrors `fractionStyle` into
  `FractionTypography` on every update, so any path that edits `PdfPrefs` keeps the two in step.
  Write the mirror anywhere else and the Settings UI and the ink can disagree.
- **A style change is invisible to Compose**, because the mirror is not snapshot state. Each
  preview's render-inputs record therefore carries `fractionStyle` purely as a **re-render key**
  — it is never passed to a composer. Drop it from one of those records and that tab keeps
  showing the old fractions until something else invalidates it. The same trick the arrowhead
  size and the shade flags already use.

Tests: `FractionStyleSettingTest` covers pref → mirror → an unqualified `measureRichText`, which
is the whole chain. Because the mirror is process-wide it also restores the shipped style in
`@After`; a test that moves it and does not put it back leaves the next test class drawing in
the wrong style.

## 4. Measure and draw are one pair

Every site that draws with `drawRichText` must measure with `measureRichText`. Measuring inline
and drawing built-up over-reserves the break in a dimension line and leaves the value sitting
off-centre in it; measuring built-up and drawing inline overruns the gap. Both functions run
through the same private `FractionTextRenderer.layout`, so the two can never disagree about a
given string — the risk is only in a call site that pairs one with the other's counterpart.

`drawRichText` honours `paint.textAlign` exactly as `drawText` does, and takes the same
baseline `y`, so converting a site is a rename.

Text with no fraction in it costs one scan and then goes straight to `Canvas.drawText`.

## 5. Where it draws

Length values reach these surfaces. Diameters are unaffected — `formatDiaWithUnit` is decimal by
shop convention and never carried a fraction.

- `pdf/render/PdfDimensionRenderer.kt` — the schematic's stacked rails and the consolidated
  sheet's, including the top OAL rail (`labelWidth` + `drawLabel`).
- `pdf/ShaftPdfComposer.kt` — footer spec lines (`drawFooterLine`). The free-text job fields
  above them stay plain; see §2.
- `pdf/RunoutPdfComposer.kt` — the OAL rail.
- `pdf/WearPdfComposer.kt` — strip rails, strip anchor titles, and the OAL rail.
- `pdf/UndercutPdfComposer.kt` — strip rails, strip anchor titles, the OAL rail.
- `ui/screen/LinerWearDetail.kt`, `ui/screen/UndercutDetail.kt` — the on-screen sheets' dim
  segments, so the preview and the paper construct a fraction the same way.

Editable text is deliberately **not** included: `dispKw` and the numeric input fields still show
plain `1 1/2`, which is what round-trips through `parseFractionOrDecimal`.
