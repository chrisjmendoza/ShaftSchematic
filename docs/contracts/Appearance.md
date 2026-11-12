# Appearance — App Theme & Sheet Ink

Files: `ui/theme/Theme.kt`, `ui/theme/Color.kt`, `ui/theme/SheetInk.kt`,
`settings/AppearancePrefs.kt`, `MainActivity.kt`, `ui/screen/SheetSemantics.kt`; consumers:
every `ui/screen` sheet canvas
Layer: UI (theme) + Settings
Version: v1.0 (2026-08-04)

## Contract

### Theme selection (Settings → Appearance)

- `AppThemeMode` (`SYSTEM` / `LIGHT` / `DARK`) + a separate **High contrast** boolean.
  Persisted in `SettingsStore` (`theme_mode`, `high_contrast`); exposed as
  `ShaftViewModel.themeMode` / `.highContrast`; collected by `MainActivity`, which wraps
  `AppNav` in `ShaftSchematicTheme(themeMode, highContrast)`.
- **Default is `LIGHT` + high contrast off**, and that combination must reproduce the app's
  historical presentation (the pre-Appearance bare `MaterialTheme` look). An app update must
  never change the app's look until the user opts in. `AppThemeMode.fromName` falls back to
  `LIGHT` on missing/corrupt values for the same reason. **One deliberate exception**, the
  caution tier below: its historical value was a baseline default nobody chose, and this clause
  guards what was DECIDED rather than freezing what was never decided.
- Four schemes: Light, Dark, High-contrast Light, High-contrast Dark (`Theme.kt`).
  High contrast = pure black/white grounds, bold containers, one strong accent per scheme
  (`HcBlue` light / `HcAmber` dark) chosen to read on that scheme's surfaces **and** on the
  white sheet canvases.

### The severity ladder: error → caution → neutral

The app runs a **three-rung** severity ladder in chrome — `errorContainer` for an error →
`tertiaryContainer` for a caution → `surface` when fine — most visibly on the per-card component
warnings (`ComponentCarousel`) and the spec-level banner (`SpecWarningBanner`), which share the
caution rung; errors on a card use `errorContainer`.

`tertiaryContainer`/`onTertiaryContainer` therefore mean **caution**, and every scheme assigns
them (`Color.kt` → `WarnAmber*`; the high-contrast schemes reuse their own `HcBronze*` accent,
completing the `X`/`onX`/`XContainer`/`onXContainer` pattern the other three families already
follow). Before that they were the only container role left unset — including in the
high-contrast schemes, which set `primaryContainer`, `secondaryContainer` and `errorContainer`
and skipped this one — so all four schemes inherited M3's baseline `#FFD8E4`. A pale pink a few
degrees of hue from the error rung put "snug" and "oversized" at nearly the same colour on one
badge, and made an advisory banner read as an error (on-device report).

`tertiary` itself is **not** part of this ladder and must not be recoloured to match: it is the
preview-color **Bronze** preset (`PreviewColorPreset.BRONZE` → `scheme.tertiary`), and its
historical light value is pinned as `SheetInk.LinerTint`. In the dark scheme it is a warm bronze
(`DarkBronze`, `#E2B36B`) so the preset reads as bronze on the grey ground. Its only other reader
is the Custom "Tertiary" preview role and the developer dimension-debug overlay.

### The dark scheme is a neutral grey with a steel-blue accent

`DarkColorScheme` assigns every ground, accent and severity role itself (`Color.kt` →
`DarkGrey*`, `DarkSteelBlue*`, `DarkSlate*`) instead of inheriting M3's purple baseline. On the
purple-black, the editor preview's liner and taper fills sank into the ground (on-device report),
and the lavender accent made every filled button a glaring slab. Verified screen by screen on an
emulator; `DarkChromeContrastTest` measures every row below.

| Role | Value | Where this app uses it | Measured |
|---|---|---|---|
| background / surface | `#1F2023` | screens, the preview box, keyway voids | — |
| surfaceContainerLow | `#232427` | sidebar, bottom sheets | onSurface 12.7:1 |
| surfaceContainer | `#27282C` | menus | onSurface 12.0:1 |
| surfaceContainerHigh | `#303136` | dialogs | onSurface 10.6:1, primary 5.6:1 |
| surfaceContainerHighest / surfaceVariant | `#3A3B41` | carousel cards, Help rows, thumbnail cards | onSurface 9.1:1, onSurfaceVariant 7.1:1 |
| surfaceDim / surfaceBright / Lowest | `#1A1B1D` / `#3A3B40` / `#18191B` | M3 internals | — |
| onSurface / onBackground | `#E8E8EA` | values, titles; the cards' selected chip | 13.3:1 on the ground |
| onSurfaceVariant | `#CDCED5` | field labels, captions | ≥ 7.1:1 on every surface |
| outline | `#9A9BA4` | text-field borders, chip borders | 10.8:1 ground, 4.0:1 card |
| outlineVariant | `#5A5B62` | dividers, default chip borders | 2.4:1 ground |
| surfaceTint | `#C6C7CE` | tonal elevation — neutral, never tints | — |
| primary / onPrimary | `#8AAED8` / `#0B2848` | filled buttons, switches, checkboxes, sliders, focus, links, the preview's selection ring | 7.1:1 ground, 4.9:1 card; label 6.5:1 |
| primaryContainer / on | `#2C4A6B` / `#D6E5FA` | Add-dialog chips, the undercut "Unsaved" pill | 7.2:1 |
| secondary / secondaryContainer / on | `#B4C1D1` / `#34465B` / `#D9E5F3` | selected filter chips, the sidebar's current item, slider tracks | 7.6:1 |
| inversePrimary | `#2F5F9A` | the snackbar's action | 5.1:1 on `#E3E3E6` |
| inverse surface / on | `#E3E3E6` / `#2F3033` | snackbar | 10.3:1 |
| tertiary | `#E2B36B` | the Bronze preview preset | — |
| error / onError | M3 `#F2B8B5` / `#601410` | error text, field error state, the delete icon | 9.5:1 ground |
| errorContainer / on | `#A52A22` / `#FFFFFF` | a card's error chip, the undercut "Blocked" pill | 7.1:1 |
| tertiaryContainer / on (caution) | `#3E2F00` / `#FFDFA0` | the warnings banner, a card's caution chip | 10.1:1 |

The accent is a low-chroma steel blue, tone ~70: a full-width filled button is a calm block rather
than a lavender slab, and blue sits more than 90° of hue from every colour the app gives meaning —
bronze liners, amber caution, red error — so a selection never reads as a problem, for normal and
colour-deficient vision alike.

**The severity ladder steps in lightness in the dark**: error red ABOVE the card's lightness,
caution amber BELOW it. M3's dark error container and an amber of the same lightness read as one
olive under protanopia (1.02:1); the two now hold ≥ 1.4:1 under protanopia and ≥ 2:1 under
deuteranopia, and their text colours differ (white vs amber) besides. The light ladder is
unchanged.

**Chrome inks a light ground draws fixed** go through `ui/theme/ChromeInk.kt`; each returns
exactly the historical colour on a light ground and the role that plays the part on a dark one
(keyed on the scheme's own ground, so high-contrast dark takes it too): the cards' high-contrast
chips (`strongChipContainer`/`strongChipLabel` — black and white in light, the foreground pill
with ground-coloured text in dark: a black chip on a dark card read as a hole), the accept ✓
(`acceptGreen` — `#2E7D32` is 3.2:1 on the dark ground) and colour swatches' ring (`swatchRing`,
a 1 dp outline in dark only, so Transparent's placeholder and the undercut "Grey" black keep their
edge). `NumericInputField` takes `numericFieldColors()` — Material's own in light; in dark a
disabled (derived) field keeps its value at 5.6:1 and its label at 4.5:1 on the card instead of
2.7:1.

### The preview on a dark ground

The editor preview and the template thumbnails resolve their preset colours through ONE rule,
keyed on the scheme's own ground — `ColorScheme.isDarkPreviewGround()` (`surface` luminance
< 0.5), never a theme flag — in `util/PreviewColorPreset.kt`:

| | light ground | dark ground |
|---|---|---|
| Stainless (lerp surface → onSurface) | 0.50 | 0.68 |
| Steel (lerp surface → onSurface) | 0.75 | 0.90 |
| Steel fill alpha | 0.18 | 0.34 |
| Stainless fill alpha | 0.14 | 0.28 |
| Bronze fill alpha | 0.16 | 0.56 |
| Custom fill alpha | per-kind fallback | fallback × 2, capped at 0.45 |

The fill alphas live in `PreviewColorPreset.fillAlpha(darkGround, fallback)`, the one table both
`ShaftDrawing` and `ShaftThumbnail` read. A light ground resolves exactly as it always has.
`PreviewColorDarkGroundTest` measures the composited result on the dark scheme: every filled
preset ≥ 1.6:1 off the ground (Steel 2.47, Stainless 1.67, Bronze 3.59), the Steel outline — also
the rails', hatches' and seal dashes' ink — 10.77:1, and the liner and taper fills apart in hue
(Oklab distance 0.112, bronze warm, steel neutral). High-contrast dark's black ground takes the
same rule (Steel 2.45, Bronze 3.99, outline 15.6:1; Stainless, not a default, 1.46).

**The shaft is grey on a dark ground.** On a light ground an unshaded component is an empty
outline and the ground reads as paper; on a dark ground that outline read as a hole in the
background (on-device report). `ColorScheme.previewShaftGrey()` — `lerp(surface, onSurface,
SHAFT_GREY_LERP_DARK = 0.38)`, opaque, `null` on a light ground — is handed to the renderer as
`RenderOptions.baseFillColor` by `ShaftDrawing` and `ShaftThumbnail`. Every body, taper, liner and
thread stands on it; a shaded component is its kind fill flattened over it (one fill draw per
component), so "shaded vs not" still reads as grey vs tinted. The sheet-layout body pass takes the
grey as `drawBodyRunsWithBreaks(unfilledFill)`, null on every sheet. S-break gaps stay ground. The
Runout / Wear / Undercut canvases never set it. Measured (dark): bare grey 2.8:1 off the ground,
the Steel outline 3.9:1 on it; a shaded taper 1.7:1 and a bronze liner 1.9:1 off the bare grey,
the liner told from the neutral taper by chroma, which survives protanopia and deuteranopia.
Bronze's dark alpha is 0.56 because over the grey a thinner bronze mixed down to khaki.

The rest of the dark preview, all keyed on the dark ground and none of it reaching a sheet:

- **Keyway cuts show the ground** — `RenderOptions.voidColor` (white by default: every sheet
  canvas, and the preview on a light ground) is the box's background in the preview and the
  card's `surfaceVariant` in a thumbnail. A white slot was the brightest thing in the box and read
  as a highlight, not a cut; the plan slot, its spoon bowl and the 90° notch all take it.
- **Selection ring** — the accent, opaque, 10 px extra width (5 px outside the outline) instead of
  75% and 4 px, which all but vanished beside a light outline on the grey.
- **Black/White Only** draws white line art: the outline and hatch take `onSurface` and nothing is
  filled. The mode's fills are `Transparent` at the preset alpha — black at that alpha — which a
  light ground shows as a faint grey (left as it always was) but a dark one as black blocks.
- The reset-view disc is `surfaceContainerHighest` with an `onSurface` icon instead of a
  translucent white disc.

The white sheet canvases on the Runout / Wear / Undercut tabs are deliberately left unframed: a
hairline frame does nothing for the glare of a white page, and dimming the page would change what
the sheet shows.

- **No dynamic (Material You) color.** Schemes are fixed so the preview-color presets
  (Stainless/Steel/Bronze — theme-lerped) resolve predictably. Revisit deliberately, never
  as a side effect.

### Sheet ink is theme-independent (critical invariant)

The five paper-sheet canvases — `UndercutRoute` overview, `UndercutWindowDetailOverlay`,
`WearRoute` overview, `LinerWearDetail` (`ComponentWearDetailOverlay`), `RunoutRoute`
preview — draw on a forced-white sheet (`background(Color.White)`). The Help screen's
figures (`ui/screen/HelpIllustrations.kt`) follow the same rule for the same reason: they
depict printed output, so each draws `SheetInk` on a white `Surface` in every theme, and
only the figure's frame and caption are theme-colored chrome. Their **ink** comes from
`ui/theme/SheetInk`, never from `MaterialTheme.colorScheme`:

- `SheetInk.Outline` (black) — profile outlines, rails, sheet text, hatches.
- `SheetInk.LinerTint` — liner tint on wear/runout sheets (pinned historical light tertiary).
- `SheetInk.WearRed` — wear tints/hatches/pit X's (pinned historical light error red).
- `SheetInk.Paper` (white) — value knockouts on the sheet canvases (the wear detail rail's values print on it, drawn after every tick and line).

Reason: in dark theme `onSurface` is near-white — theme-driven ink would print invisible
lines on the white sheet. The pins hold the exact colors the sheets always had in light
theme, so enabling dark/high-contrast themes changes app chrome only, never the drawings.
The undercut sheets' component fills are additionally user-styled via `UndercutStyle`
(see `UndercutDrawing.md`) — still fixed ink colors, never theme roles. That style is
**screen-only and never reaches a composer**; the printed undercut drawing has its own
independent line-art preference, `PdfPrefs.undercutLineArt` (drawing-profile captured), so the
two surfaces are set separately and neither reads the other. The faint dotted liner outline drawn
over the cuts is fixed `SheetInk` on the canvases (theme-independent) and driven by its own app-wide
`PdfPrefs.undercutLinerOutline` on both surfaces. The cut numbers the detail canvas draws as an
input aid are fixed `SheetInk.Outline` text with no pref; they print on no sheet. Its sibling `PdfPrefs.undercutSplitAtLinerEdge`
(default off) decides whether the chain rail splits a cut at a liner edge it crosses.

**The editor preview is NOT a paper canvas** — it is theme-styled (the preview-color presets) — but
its problem marks (`RenderOptions.issueMarks`: overlap bands, past-end tints, the dashed real end;
`ShaftScreen.md`) take ONE fixed warning red, `PREVIEW_ISSUE_INK`
(`ui/drawing/render/PreviewIssueMarks.kt`), never a theme role: the mark is a statement about the
drawing, it reads on the light, dark and high-contrast previews alike, and a scheme's `error` role
would make the same problem a different colour per theme.

**Tap and selection affordances are fixed ink too** — the overview canvases' tap tint,
border and count badge (Wear, Undercut) and the undercut detail canvas's selection draw in
`SheetInk.Selection` (with `SheetInk.OnSelection` badge text), the historical light-theme
primary, so the default Light theme draws exactly what it always did. They used to follow
`colorScheme.primary`; the on-device pass found High contrast's amber reading weakly on the
white paper (every component on the wear overview carried a pale-yellow border), so the
"theme-driven affordance" posture was retired for them. Error marks on a draft (`colorScheme.error`)
remain theme-driven.

### System bars follow the app theme

`MainActivity` re-applies `enableEdgeToEdge` with explicit `SystemBarStyle`s whenever the
resolved theme flips (`isAppDarkTheme(themeMode)`, the ONE resolution `ShaftSchematicTheme` also
uses): light status/navigation icons over a dark app, dark icons over a light one. The bare
`enableEdgeToEdge()` follows the SYSTEM night mode, which left dark, invisible clock and status
icons over the Dark and High-contrast Dark app bars (on-device report). High contrast does not
enter the decision — each high-contrast scheme keeps its base scheme's ground.

PDF output is unconditionally theme-independent (composers use fixed `android.graphics`
colors) — nothing in this contract touches it.

## Accessibility

- **Sheet-canvas text does not scale with the system font.** A sheet canvas's drawn text is
  drawing ink — it depicts a printed sheet, not a piece of UI — so it stays at its fixed
  point size regardless of the device's font-scale setting, the same posture as the fixed
  `SheetInk` colors above. Ordinary Compose chrome (labels, fields, dialogs, menus) uses
  Material `sp` typography and **must** scale with the system setting; nothing in this
  contract exempts it. The carousel's fixed-height row (`ComponentCarousel.CAROUSEL_HEIGHT`)
  is safe only because its card's content column scrolls
  (`ComponentCard`'s `Modifier.verticalScroll(...)`, `ComponentCarousel.kt`) — dropping that
  scroll would clip fields at large font scales instead of letting the card grow.
- **Every sheet canvas carries a spoken summary.** The five white-sheet canvases (see
  above) are otherwise silent to a screen reader — a `Canvas` has no accessible structure of
  its own. Each one carries a `Modifier.semantics { contentDescription = … }` built by
  `ui/screen/SheetSemantics.kt`: counts only (how many undercuts/wear areas/pits/diameter
  readings/stations are on the sheet), **never** a diameter, length, or other geometry value —
  the same golden-rule posture as a breadcrumb (`Diagnostics.md`). Each summary also names
  the sheet's real accessible editing path — the list rows below the canvas (undercuts, wear
  detail) or the canvas's own gesture (wear overview's tap-to-inspect, runout's
  long-press-to-drag/tap-to-enter, plus its Move mode's −/+ toolbar — real buttons, so adding
  or removing a station on a selected component IS a TalkBack path) — because the canvas's
  placement gestures (tap a pixel, drag a bubble) are not themselves a TalkBack path.
- **Chrome survives a large system font** (on-device pass at 130% and 200%): the editor sidebar
  scrolls when it outgrows its panel (`EditorSidebarPanel`, one column for the overlay and the
  permanent panel, held at least the panel's height so the tools group still sits at the bottom
  while it fits); chip labels never break inside a word and chip rows wrap whole chips onto a
  second line (`ChipLabel` / `ChipFlowRow`, `ui/screen/ChipRows.kt`); the OAL field's label
  shortens to "OAL (in)" above 1.3× and never takes a second line; the PDF preview bars' titles
  are capped at two lines (ellipsized beyond). Settings switch rows toggle from their label as well as the switch
  (`SettingSwitchRow`).
- **Icon-only buttons always carry a `contentDescription`.** An `IconButton` whose only
  child is an `Icon` with no visible text sibling must describe its action ("Back", "Save",
  "Open navigation", …). An icon drawn beside its own visible text label keeps
  `contentDescription = null` — the text already carries the accessible name, and repeating
  it doubles what TalkBack reads.
- **Deliberately NOT done**, and why:
  - `semantics(mergeDescendants = true)` on carousel cards — merging a card into one node
    would fold its text fields into a single unreadable blob instead of letting TalkBack
    step through them individually.
  - Touch-target widening on canvas hit-tests (tap/drag targets for pits, bubbles, undercut
    windows) — Material controls already meet the 48 dp minimum, and a canvas's own
    placement gestures are not the accessible path for that data; the list/detail
    affordances named in the sheet summaries above are.
  - Reduced-motion handling — the app's animations (tap highlights, a reset-view tween,
    small transitions) are trivial and brief enough that motion-sensitivity accommodation
    was judged not worth the added surface.

## Known follow-ups (deliberate, not bugs)

- The Dark theme's chrome has had an emulator walk-through (editor, cards, dialogs, menus,
  sidebar, Settings, Help, templates, sheet tabs, PDF preview); the high-contrast schemes keep
  their own values and were not re-walked in that pass.

## Do Nots

- Do not read `MaterialTheme.colorScheme` for anything drawn on a white sheet canvas.
- Do not re-introduce dynamic color casually (it breaks preset predictability).
- Do not change the default away from `LIGHT` without an explicit product decision.
