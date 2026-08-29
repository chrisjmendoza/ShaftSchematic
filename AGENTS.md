# ShaftSchematic – Codex Instructions

## Project overview
Android app (Kotlin / Jetpack Compose) for designing marine propulsion shafts.
All model values are **canonical millimeters (mm)**. Unit conversion (mm ↔ in) happens
only at the UI edge for display and input — never in the model, ViewModel, or renderer.

## Docs
Detailed contracts live in `docs/contracts/`.
Read the relevant doc before editing a subsystem. Key files:
- `ShaftScreen.md` — overall screen contract, commit-on-blur rule, unit edge rule
- `AddComponentDialogs.md` — add-dialog parity rules (mirror carousel cards)
- `NumberField.md` — numeric input field contract
- `ShaftViewModel.md` — ViewModel responsibilities and state ownership
- `Model_Conventions.md` — model layer rules
- `CouplerBoltSlot.md` — coupler bolt slot feature contract (reference-only cutouts)

## Comment conventions
No date stamps and no prior-code narratives in `.kt` comments — comments state current
behavior and constraints only; git history, `CHANGELOG.md`, and `docs/*.md` own the
*when* and the history. Load-bearing warnings keep constraint + consequence in
present/conditional tense ("doing X would cause Y"). Attribute user-driven changes as
"on-device report", never by name. See `docs/STYLE_GUIDE.md` §"Comment Conventions".

## Critical invariants — do not remove or weaken these

### Add dialogs must mirror carousel cards
Every control that exists in a component's **carousel edit card** must also appear in
its **Add dialog** under the same conditions. Removing a control from one without removing
it from the other is a bug.

Specifically:
- **Thread excluded from OAL** (`countInOal = false`): `AddThreadDialog` must show
  "Thread end: AFT | FWD" chips and hide the Start field — same as the carousel card
  (`ThreadPagerCard.kt`, `!includeInOal` block).
- **Liner AFT/FWD reference**: `AddLinerDialog` must show "Measure From: AFT | FWD" chips.
- **Body keyway**: `AddBodyDialog` and the explicit-body carousel card must both expose
  the keyway section (KW from AFT | FWD chips, W × D, L, offset, spooned toggle). The
  auto-body card intentionally omits it (auto-bodies can't host keyways until promoted).
  The AFT/FWD chips' DEFAULT is seeded by `ShaftSpec.suggestedBodyKeywayEnd` on BOTH
  surfaces — opposite the shaft's existing keyway when exactly one side is taken,
  AFT otherwise; a seed only, never applied to a stored keyway.
- **Keyway clocking**: the spec-level 180°/90° toggles and the CW/CCW chips appear on
  keyway-bearing cards when the shaft has ≥ 2 keyways, and in
  `AddBodyDialog`/`AddTaperDialog` when adding would reach ≥ 2 (≥ 1 existing + this
  dialog's keyway defined). Same condition on both surfaces. 180° and 90° are mutually
  exclusive.
- **Taper AFT/FWD reference**: `AddTaperDialog` must show AFT/FWD direction chips.
- **Coupler bolt slot**: `AddCouplerBoltSlotDialog` and the `ResolvedCouplerBoltSlot`
  carousel card must both expose Measure From (AFT | FWD), hole Ø, count, spacing (only
  when count > 1), through/blind toggle + depth (only when blind). The card additionally
  has the deferred "show dimension rail" toggle.

**Carve-out — post-hoc display toggles are card-only.** A control that only exists to change
how an *already-drawn* component prints, has a stable default, and is reached for after
looking at a printed sheet is not a property of the component being added; in an Add dialog
it would be a permanently-preset box adding noise to every add. Exactly six qualify: the
coupler slot's "show dimension rail", **"Show Ø on drawing"** (`Body`/`Liner`/auto-body
cards), **"Shade on drawing"** (`shadeOnDrawing`, explicit `Body`/`Taper`/`Liner` cards —
TRI-STATE like the name flag: unset follows the kind's "Shade in Components" checkbox
(`shadedBodies`/`shadedTapers`/`shadedLiners`, with `shadeExplicitBodiesOnly` still carving
auto runs out of the body default), explicit ON shades that one component even with the kind
off — on-device request: shade a named SKF section without shading every body — and explicit
OFF keeps it unshaded with the kind on. Effective per-run decisions are precomputed by the
pure `unshadedBodyRunIds`/`unshadedTaperIds`/`unshadedLinerIds`
(`ui/resolved/ResolvedComponent.kt`) and threaded as id sets into the fill passes, which now
always receive a paint; the consolidated sheet's in-profile-values liner lock and the
wear/undercut documents' one-fill-per-kind `SimpleShaftProfile` boundary both stand above the
per-component flag), **"Show name on drawing"** (`showNameOnDrawing`, all four explicit component cards —
TRI-STATE per-component gate on the schematic's name label: unset follows the global
`showComponentTitles` pref, an explicit ON prints even with that pref off, an explicit OFF
hides even with it on — the pref is the default, never a master gate over an authored
choice. The field name is fresh: the retired `showLabelOnDrawing` key is IGNORED at decode,
because the flag's first build blanket-serialized `true` under it on every component and
honoring those stamps as authored overrides made one checked toggle appear to turn every
label on — on-device report. Do not resurrect the old key), **"Compress on drawing"** (`Body.compressOnDrawing`, explicit-body cards — see the
compression invariant below; its authoring default is set at creation, so the dialog would
be a preset box), and the per-component **"Prints in: in | mm"** unit chip (explicit
`Body`/`Taper`/
`Thread`/`Liner` cards, at the FOOT of the card — an override also has nothing to key to before the
component has a resolved id). Anything that changes geometry, position, or a value stays under the
parity rule above — including the Add Thread dialog's Imperial/Metric mode (value entry, mirrored on
the thread card by the field the stored mode selects) and the **"Keyway in: in | mm"** chip, which
sets the unit the keyway's own fields are TYPED in as well as printed in, and therefore appears in
`AddBodyDialog`/`AddTaperDialog` as well as on the cards.

### Coupler bolt slots are reference features
Coupler bolt slots (`ShaftSpec.couplerBoltSlots`) are radial cutouts drawn on the shaft
but they **never** affect overall length (`coverageEndMm` ignores them), **never** split
bodies, and **never** collide with other components (`collisionGroup() → null`). Do not
add them to `coverageEndMm`, body-split/merge, or overlap validation.
They are resolved as `ResolvedCouplerBoltSlot` *after* body resolution so they stay out
of auto-body/subtraction geometry. See `docs/contracts/CouplerBoltSlot.md`.

### Wear pits are reference features
Wear pits (`WearRecord.pits` — a `WearPit` "X" marker per pit/dye-failure, small or large) are
**reference-only**, the same posture as wear spots / coupler bolt slots / runout readings. They
**never** affect `coverageEndMm`/OAL, body resolution, or collision, and
they live outside `ShaftSpec` (inside `WearRecord`, so they ride the existing `wear_record`
envelope field — no new field, no autosave/snapshot/import plumbing). Unlike wear spots (liner-only,
keyed by `linerId`), a pit sits on **any** pit-eligible component — a liner, taper, or body
(explicit or auto) — keyed by the **resolved component id** (`WearPit.componentId`), component-local
`axialMm` from the AFT edge + a visual `acrossFrac`. Orphan pits (component no longer resolves) are
skipped at the **render layer**, not pruned at decode (auto-body/taper ids aren't known to the
codec) — same rule as runout readings; wear spots, by contrast, ARE pruned at decode. The "X" must
be drawn **identically** (same crossed-line construction, same small:large ratio) in all draw sites:
`ComponentWearDetailOverlay`'s `drawPitX` (canvas), `WearPdfComposer`'s `drawWearPitsOnProfile` +
strip pits (PDF), and the consolidated runout sheet (canvas + PDF), which reuses
`drawWearPitsOnProfile` itself (per-surface `smallHalf`). Pure sizing/hit-test/clamp math lives in `geom/WearPitMath.kt` (shared, no
`pdf → ui` dep). See `docs/contracts/RunoutSheet.md` (Wear Pits).

### Wear diameter readings are reference features
Measured-Ø readings (`WearRecord.diaReadings` — a `WearDiaReading` per measured station,
printed as a value below the shaft with a leader to a witness tick) are **reference-only**,
the same posture as wear pits / wear spots / runout readings / coupler bolt slots. They
**never** affect `coverageEndMm`/OAL, body resolution, or collision,
and they ride the existing `wear_record` envelope field (additive `diaReadings` list — no
codec/autosave plumbing). Keyed by **resolved component id** (liner/taper/body, explicit or
auto) + component-local `axialMm`; orphans are skipped at the **render layer**, never
pruned at decode (same rule as pits/runout readings). `diaMm` is a typed measurement —
stored **verbatim** (golden rule); `0` = placed-but-empty, drawn only in the overlay, never
printed. Callouts are placed by the shared pure engine `geom/WearDiaCalloutLayout.kt`
(order-preserving spread, two-row stagger, dogleg leaders) and must render **identically**
in both draw sites: `ComponentWearDetailOverlay` (canvas) and `WearPdfComposer` (a reading draws
in its component's detail strip whenever that component has one on the sheet — liners always, a
taper or body once elected onto a strip — and under the main profile otherwise). Labels
use `formatDiaWithUnit`, no `Ø` prefix. On the **consolidated runout sheet** the same
readings instead draw INSIDE the profile at their station — one rotated haloed column via
`drawDiaReadingsInProfile` (`RunoutPdfComposer`), liners included, `Ø`-prefixed — replacing
below-shaft callouts there; the wear document itself (the authoring surface) keeps its
callout engine unchanged. Valued **liner** readings inside a wear band additionally drive the
drawn **worn-profile trace** — the liner's surface line dips through the measured diameters in
the liner detail strip and the detail overlay, drawn identically in both sites from the pure
`geom/WearTraceMath.kt` and display-exaggerated like the undercut notch (normalized to the
record's deepest liner reading, never shallower than true scale). That exaggeration cap is
**user-set**, 5–25% (`WEAR_TRACE_MIN_DEPTH_FRAC`..`WEAR_TRACE_MAX_DEPTH_FRAC`, the max also the
default): per job via `WearRecord.traceDepthFrac` (additive/optional, `null` = follow the
Settings → Drawing "Wear depth exaggeration" default, `PdfPrefs.wearTraceDepthFrac`), resolved
ONCE by `effectiveWearTraceDepthFrac` and handed to both draw sites from the same call site.
See `docs/contracts/RunoutSheet.md` (Wear Diameter
Measurements) and `docs/archive/WearDiaMeasurements_PLAN.md`.

### Worn sections are reference features
Worn sections (`WearRecord.wornSections` — a `WornSection` per designated measured area,
step 1 of the runout/wear consolidation) are **reference-only**, the same posture as the
other wear/runout marks. They **never** affect `coverageEndMm`/OAL, body resolution,
or collision, and they ride the existing `wear_record` envelope
field (additive `wornSections` list — no codec plumbing). Like undercuts they are
**shaft-space** (`startFromAftMm` + `lengthMm`, may cross component edges, no orphans,
never pruned at decode; `authoredReference` reuses `UndercutReference` SET values as
display-only Distance metadata — canonical never moves on a reference switch). `diaMm` is
a **list** of typed measurements — stored verbatim in list order (golden rule); values ≤ 0
never print. They draw on the **runout sheet**: boundary lines at the span ends and the
values **inside the profile**, rotated 90°, each over a sheet-white halo so no profile
line crosses a number. ONE draw implementation — `drawWornSections`
(`pdf/RunoutPdfComposer.kt`); the `RunoutRoute` canvas deliberately draws no worn
sections or in-profile values (that tab authors runouts only — the consolidated marks
live on the Output tab's rasterized real-PDF preview); pure layout in
`geom/WornSectionMath.kt`. No carousel card, no Add dialog
(outside the add-dialog-parity invariant). **Consolidated-sheet z-order: marks first, text
last** — wear-area bands and pit X's (migrated from the retired wear tab,
`drawWearMarksOnRunoutProfile`), then worn-section boundaries, then ALL value text over
knockout halos (worn values, then `drawDiaReadingsInProfile`); do not draw any mark after
the text passes. Values **auto-fit their local band** (`fittedValueTextSize`, floor
`WORN_VALUE_MIN_TEXT_PT`/14 px canvas); the PDF profile follows the **hand-sheet
compression convention** — drawn height follows TRUE diameter on the **default sizing
curve** (`defaultShaftHeightPt`: STANDARD anchors are PROPORTIONAL — 8" → 1",
6" → 3/4", 4" → 1/2", a line through the origin, the hand-sheet rule from the
original rulered sketches; taller pairs read "chubby" on-device and are a deliberate
Settings choice, never the default — Settings → Drawing "Default drawing size",
`PdfPrefs.curveLoHeightIn`/`curveHiHeightIn`; an inverted pair flattens at the low
anchor: a larger shaft never draws smaller; `defaultVisualScale` keeps the flat
`VISUAL_DIA_SCALE_PT_PER_MM` only as the degenerate fallback),
spans foreshortened above per-kind width floors via the
pure mapping `geom/ProfileCompression.kt`, everything through the one piecewise `xAt`.
**TAPERS may shrink but NEVER equalize** — they carry NO flat floor (a flat floor made
a 19.5" and an 11.5" taper draw identical, on-device report); instead a
ratio-preserving fraction-of-true floor (`PROFILE_TAPER_MIN_FRAC_OF_TRUE` 0.7, λ-fit
like the liner raises — ratio preservation is structural: same λ, same K threshold, so
relative taper widths always read true, and the drawn height never yields to it). The
taper fraction is deliberately the λ pool's largest: width flows in proportion to the
fractions, so tapers out-prioritize body runs (on-device request — body compression is
the give that funds taper proportionality). The
SCHEMATIC composer uses the lean `SCHEMATIC_MIN_*` floors (28/40/56 — its values live
on rails/callouts, so proportion wins); the runout/consolidated sheet keeps the
writable `PROFILE_MIN_*` floors.
Body runs compressed below a threshold fraction of their true drawn width
(`breakForCompression`, `pdf/BreakSymbol.kt` — milder foreshortening prints a plain
outline; bodies ONLY) draw the S-break pair laid out by `breakPairLayout` — gap widens up to half
the run, then amplitude flattens, so the two edges always keep ≥ 1 pt of daylight and
never overlap. The threshold is **user-set** — `PdfPrefs.sBreakThresholdFrac`, Settings →
Drawing → "Body S-break", default **half** (`PDF_SBREAK_THRESHOLD_DEFAULT`), 5% steps,
**Never** (0) at the low end = compression stays entirely hidden, 100% = break on any
foreshortening ("why lock it in one way when different users may want different outputs"
— on-device request). ONE consumer, `drawBodyRunsWithBreaks` — the single body-run pass both
composers call, `pdf/BodyRunDraw.kt`. **A broken run's shade fill ends ON the S curve**, never
on a square rect at the break line: the stroked glyph and the stub-fill boundary build from the
ONE cubic construction (`appendBreakEdgeS`/`breakEdgeSPath`/`breakStubFillPath`,
`pdf/BreakSymbol.kt`), so they cannot drift — a straight-edged fill left an unfilled crescent
inside the outline in one half of each stub and spilled grey into the paper gap in the other
(on-device report). The wear/undercut document's `SimpleShaftProfile` derives its fill and
outline breaks from the same decision (`simpleBodyBreak`), so its gap stays bare paper too. **No footer compression note**: the S-break pair IS the
statement that a run is foreshortened, so prose repeating it is redundant (on-device direction)
and cost a footer row on exactly the long shafts with the least room. Do not reintroduce
`showCompressionNote`. The long-span trigger `COMPRESS_TRIGGER_PT` is
deliberately NOT governed by the slider — a run eating 220 pt of paper at true scale is
not hidden compression, so a compressible body breaks at every setting, Never included. **A
body keyway's WINDOW never compresses; the rest of its body compresses and breaks like any
other run** (on-device direction: a 95%-shaft body must keep its break or a long shaft cannot
render). The protected window — the slot span padded by one keyway width, clamped to the body
(`bodyKeywayProtectedSpansMm`, STORED spec) — pins at true scale
(`keywayPinnedBodySpans`), the break gap steers clear of it (`breakGapCenter`, both body
passes; a run with no clear placement prints plain), and the slot draw derives pt/mm from
its OWN mapped span so it stays true-size inside a compressed body
(`drawKeywayNotchBodyPdf`). Pinning the whole host body BECAUSE of its keyway, or suppressing
its break outright, would be a regression on both sides. Separately, **an explicit body can
opt out of compression wholesale** — `Body.compressOnDrawing` false pins its WHOLE stored
span at true scale (`compressOptOutBodySpans`, added in the ONE builder `profileFeatureSpans`
so both composers and the height-slider estimator agree) and suppresses its S-break, the
long-span trigger included (`drawBodyRunsWithBreaks` guards the trigger on the flag; the
foreshortening predicate self-disables at true width). New explicit bodies are created
opted-OUT (`addBodyAt` — an authored section reads at true proportion, on-device request: a
named 12″ section printed with an S-break); the SERIALIZATION default is `true`, so bodies in
already-saved documents keep compressing until their card's "Compress on drawing" checkbox —
the escape hatch that keeps a huge explicit body renderable — is unticked. The drawn height
yields to the pin (`solveMaxProfileScale`), which is exactly why the checkbox exists. Auto
spans never opt out — bare shaft is the compression give. Split/merge fragments carry
`label`, both show-flags, and `compressOnDrawing` (`carryBodyDisplay` — merge takes the AFT
fragment's values; dropping them silently reset authored display choices).
**Liners compress in SIZE only** (finite `PROFILE_MIN_LINER_PT` floor — proportional
foreshortening, NEVER a body-style S-break cutout; the S-break glyph is a body-only draw
path); the per-job **"Liner compression" pair** (`RunoutConfig.linersProportional` +
`linerCompression` → derived `linerMinFracOfTrue`, fed to
`ProfileFeatureSpan.minWidthFracOfTrue`) can raise the liner floor toward true width —
**the drawing height takes PRECEDENCE**: the raises are best-effort, never enter the
scale solve, and λ-fit whatever room the page has at the selected height
(`fracFitFactor`) — do not let a liner demand lower the drawn shaft; control on the
Output tab + the schematic and runout preview Tune sheets with a live kept-% readout
(`estimatedLinerKeptFracOfTrue`); ONLY keyway-bearing bodies stay pinned at true width
with the height yielding (`solveMaxProfileScale`). Body runs join the same λ pool
(`PROFILE_BODY_RUN_MIN_FRAC_OF_TRUE` 0.30 — ratio-preserving gap floors), so liner raises
can never consume the page and body-run relative lengths always read.
The **"Shaft height" slider** (`RunoutConfig.heightScale`,
per-job in the envelope — ONE value behind the runout/consolidated sheets AND the
schematic, `composeShaftPdf(heightScale)`) multiplies the solved scale; the drawn shaft
is clamped to an ABSOLUTE paper BAND — **1/2" to 1 1/2"** (`PROFILE_MIN_SHAFT_HEIGHT_PT` …
`PROFILE_MAX_SHAFT_HEIGHT_PT`) — and by the page budget (`exaggeratedProfileScale`,
pure/unit-tested). Both ends are paper measures, never multiples of this shaft's own curve
height. The ceiling caps even a short shaft whose width-fit would draw taller (it then simply
doesn't span the page); the floor is what lets a LARGE shaft — usually a long one, which cramps
the schematic — be shrunk out of the way, and it **never raises a shaft above the sizing curve**
(a shaft whose standard height is already under the floor keeps that standard, so the
proportional hand-sheet rule is untouched at 100%). **Everything derives from those two
constants** — the Settings anchor-height range (`PDF_CURVE_HEIGHT_MAX_IN`), the slider track and
caption, the Settings blurb, the Help topics (`HEIGHT_CAP_LABEL`/`HEIGHT_FLOOR_LABEL_IN`) — so
moving one moves the whole system; do not restate the figures. The multiplier bounds
(`PROFILE_HEIGHT_SCALE_MIN`/`MAX` = 0.25–6.0) are deliberately wider than the band, since they
only have to EXPRESS it on any shaft; the height clamp is what bounds the drawing. The slider
selects the drawn height by VALUE in paper inches — the track spans the band
(`drawnShaftHeightPt`/`heightFracForDrawnHeight`);
commits near the standard height snap to exactly 100%. **A tuning-slider drag is a
visual-only override** (`ui/screen/PreviewTuning.kt`): while the finger is on Line
thickness / Body S-break / Shaft height / Liner compression the open preview re-renders
from the in-progress value (conflated render loop, draft raster, undimmed sheet scrim),
but NO DataStore write and NO `RunoutConfig` update may happen on a drag frame —
persistence and the per-job dirty mark stay on commit-on-release. **An open tuning sheet
may never cover the page**: the preview switches to the fit-width **page strip** pinned
under the app bar and the sheet is capped below it (`tuningPageStripHeightDp` →
`tuningSheetMaxHeightDp`, pure, off the real `PDF_PAGE_*_PT` constants; sheet floor 40%
of the screen, strip yields the remainder), and the full-window `ModalBottomSheet` scrim
goes transparent so the strip is never dimmed. The strip shows the page's **ink band**
(`util/PdfInkBounds.kt` — blank paper cropped, inked rows never cropped, band measured on
sharp passes only so it can't resize under a dragging finger) and the cap counts the
sheet's OWN chrome that stacks outside the content column — drag handle +
navigation-bar inset, `TUNING_SHEET_CHROME_DP` — or the menu eats the callouts and footer.
ONE draw helper feeds both strip sites (`drawPageBand`). The Wear/Undercut sheets carry no
LIVE-tuning channel — their own controls (wear: strip election + trace depth + wear-area
shade + taper–liner join; both: blank draft) commit on release and re-render the whole page
— but the WEAR sheet still takes the **page-strip layout** (`sheetTunesPage = true`, ink band
measured on every pass since there are no draft frames to skip): a commit that redraws the
page is worthless while the sheet covers it (on-device report). Only the UNDERCUT sheet keeps
the plain 78% cap with the centered full-size page. Liners follow `shadedLiners` like
bodies and tapers **except when in-profile values print** — a sheet-white knockout halo over
grey reads as a pasted box, so on such a sheet liners draw unfilled whatever the pref says —
and whatever a liner's per-component `shadeOnDrawing` says: the lock stands above the
per-component flag too.
ONE predicate decides it, `consolidatedSheetHasInProfileValues`
(`pdf/RunoutPdfComposer.kt` — wear info elected in, not a blank draft, and at least one
worn-section value > 0 or one valued reading on a component that still resolves): the
composer builds `linerFill` with it and the Output tab's PDF options sheet locks its
"Liners" checkbox with it (`RunoutWearOptionsSheet(linerShadeLocked)` — disabled and shown
unchecked, **display-only**; the stored pref is never rewritten). The classic runout sheet
and the Runout tab's live canvas carry no in-profile text, so both simply honor the pref.
Division of labor: the Wear page is the **authoring surface** for
spots/pits/point-readings (tab visible; `WEAR_TAB_ENABLED` in `EditorTab.kt` is the
one-line retirement switch for a future full consolidation); the Runout tab authors
**runouts only** (its buttons produce the classic standalone runout sheet,
`composeRunoutPdf(consolidated = false)`); and the **Consolidated Output tab**
(`EditorTab.OUTPUT`, `ui/screen/OutputRoute.kt`) owns the consolidated sheet — content
election (`ConsolidatedVariant`: All three (default) | Schematic + Runout | Schematic +
Wear, via `includeBubbles`/`includeWearInfo`), the worn-section editor, the "Shaft
height" slider, and **Export all** (checked documents batch-written to a picked folder).
Every SAF export goes through the hardened `util/PdfSafExport.writeShaftPdfToUri`
(composer throw → valid error page, never a truncated file) and the collision export
gate guards every export surface. See `docs/contracts/RunoutSheet.md` (Consolidation step 5) and
`docs/PDF_EXPORT.md` §5.6–5.7.

### Undercuts are reference features
Undercut sections (`UndercutRecord.undercuts` — an `Undercut` per machined-below-surface
span, printed on its own Undercut Drawing tab/PDF) are **reference-only**, the same posture
as wear spots / pits / dia readings / runout readings / coupler bolt slots. They **never**
affect `coverageEndMm`/OAL, body resolution, or collision, and they
live outside `ShaftSpec` in their own envelope field (`undercut_record`, sibling of
`wear_record`). **Deliberately NOT component-keyed**: canonical storage is shaft-space
`startFromAftMm` (an undercut may cross a liner edge or span components), so there are no
orphans and nothing is pruned at decode. The Distance field is authored against one of four
references — `AFT_SET`/`FWD_SET` or a reference liner's `LINER_AFT`/`LINER_FWD` edge
(display-only metadata; canonical never moves on a reference *switch* — the
`WearSpotReference` pattern, conversion pair in `geom/UndercutMath.kt`. A **Length edit**
is different: it re-derives canonical at the new length (`undercutCanonicalForNewLength`)
so the authored Distance never rewrites itself — under a FWD reference the cut's FWD end
stays pinned and the cut grows AFT-ward). Detail strips are liner-anchored: a cut inside a liner draws
that liner's whole span (`buildUndercutStrips`), not just a padded window; bare-shaft cuts still
cluster into padded windows. `diaMm` is a typed
measurement — stored **verbatim** (golden rule); `0` = placed-but-empty, drawn with a
symbolic shallow floor in the overlay, never printed. Notch **depth is display-exaggerated**
(`normalizedNotchFloorDiaMm` — drawn depth normalized to the sheet's deepest cut
(`deepestUndercutDepthMm`) at the per-sheet "Cut depth exaggeration" slider value
(`UndercutRecord.exaggerationFrac`, cap `UNDERCUT_EXAGGERATION_MAX_FRAC` = 25%), never
shallower than true, region topology from the TRUE floor) because real cuts are hairline-thin
at scale; printed Ø values stay the stored numbers. No carousel card and no Add dialog —
undercuts are authored only on their tab, keeping them outside the add-dialog-parity
invariant. The notch (void fill erasing surface stroke and fill — the mouth stays **OPEN**,
never closed by a lid — plus a full-height **section face** at each end and the floor lines,
so each cut is its own reduced-Ø rectangle **step in the silhouette**, cut against the
**local outer-surface envelope**; the section's core fills one step **lighter** than the
liner shade — `UNDERCUT_SECTION_FILL_ALPHA`, half the liner's alpha) must render
**identically** in all draw sites — `UndercutRoute`/`UndercutWindowDetailOverlay` (canvas)
and `UndercutPdfComposer` (PDF) — from the shared pure pipeline `geom/SurfaceProfileMath.kt`
+ `geom/UndercutMath.kt` (cluster windows, clamps, hit-tests; no `pdf → ui` dep)
+ `geom/UndercutOverlayMath.kt` (reference resolution, `buildUndercutNotches`, S.E.T.
positions) with `ui/resolved/SurfaceSegs.kt` as the single resolved→surface mapping; the two
canvas sites additionally share `ui/screen/UndercutSharedDraw.kt`, which holds what `geom/`
cannot — the `DrawScope` notch pass and the resolved→liner-span mapping. See
`docs/archive/UndercutDrawing_PLAN.md`.

### Paper sheets are theme-independent
The app theme (Settings → Appearance: System/Light/Dark + high contrast; default Light =
the historical look) styles Compose chrome only. The five white-sheet canvases (undercut
overview/detail, wear overview/detail, runout preview) draw with fixed ink from
`ui/theme/SheetInk.kt` — never `MaterialTheme.colorScheme` — because dark theme's
near-white onSurface would print invisible ink on the white sheet. The undercut sheets'
fills are additionally user-styled via `util/UndercutStyle.kt` (shade color/intensity +
line-art mode; the Standard/Grey default reproduces the historical fixed shades, and the
section core stays half the liner alpha at every intensity — `UndercutStyleTest`) — still
fixed inks, never theme roles, and never leaking into the PDF composers. See
`docs/contracts/Appearance.md`.

### Runout stations are per COMPONENT, never per drawn run
Station counts are length-driven — one per `RUNOUT_STATION_INTERVAL_MM` (20") via
`geom/RunoutBubbleLayout.kt`'s `defaultStationCount` (bodies `ceil(L/20")` min 1; liners the
same floored at 2 for the edge-inset convention; tapers a flat 2, the shop convention; all
capped at `MAX_STATIONS_PER_COMPONENT`). `componentOverrides` still wins.

A body split by liners/tapers draws as several runs but is **ONE component**: one carousel
name, one station-editor row, one override, one continuous run of `stationIndex` values
AFT→FWD. `collectRunoutStations` therefore groups spans by id, derives the count ONCE from the
summed length, and apportions it across the runs (`apportionStations`, largest-remainder).
Deriving per run is the bug this replaced — a 1–2" leftover fragment collected a full default's
worth of bubbles, and indices restarting at 0 per run made one reading key identify several
bubbles.

**Both draw sites must build spans through `ui/resolved/RunoutSpans.kt`** (`runoutComponentSpans`)
— base-id keyed. The canvas keying by base id while the PDF kept the resolved fragment id
(`"<id>#2"`) silently dropped count overrides AND hand-entered TIR values from the printed
sheet on any fragmented body; it is invisible on unfragmented bodies, so it hides well. Do not
rebuild spans from `spec.bodies` or from `withResolvedBodies` output.

Changing a default count moves stations under readings keyed `(componentId, stationIndex)`, so
`ShaftDocCodec.freezeLegacyStationCounts` freezes the pre-interval count for any component that
already carries a reading and has no override. A typed TIR is as sacred as a typed diameter.

### Dragged runout stations are authored positions
A bubble long-pressed and dragged on the Runout tab's canvas stores a
`RunoutStationPlacement` (`RunoutStationPlacements`, envelope field `runout_stations`) — a
**reference feature** with the same posture as runout readings (never affects
`coverageEndMm`/OAL, body resolution, or collision; keyed
`(componentId, stationIndex)`; orphans skipped at the **render layer**, never pruned at decode).
`axialMm` is **component-local from the AFT edge** (the `WearPit.axialMm` convention) and
measured in shaft space **across** a fragmented body's gaps; a position stranded in a gap is
pulled onto the nearest run by `resolveStationShaftMm` at render, never rewritten in storage.
Storing mm — not px, not drawn-x — is what lets one position serve the canvas's linear map and
the sheet's compressed map. Placements are in `EditState` (a drag is direct manipulation, so
it undoes), and so is the **station-count override slice** of `RunoutConfig`
(`EditState.stationCountOverrides` mirrors `componentOverrides`; a +/− writes placements,
readings, and count together, and restoring the first two without the third left a phantom
derived bubble behind every undo of a "+"). The REST of `RunoutConfig` (sliders, TIR
direction, coupling face) stays non-undoable — its commits re-emit through the recorder but
produce identical `EditState`s, which `SessionHistory.record` no-ops, so the history cannot
flood.

An authored position is a typed input: **no derivation may move it**. A drag pins **exactly
one station** — untouched siblings stay derived, keeping their automatic behaviour: they
track geometry edits, and a body's derived stations keep their **drawn-even placement over
the compressed sheet** (the on-device readability rule pinning-the-whole-component silently
discarded; derived positions never depend on pinned ones, so pinning one bubble moves nothing
else). Where a pin and a re-derived sibling would print out of index order (compressed maps,
geometry edits), the **derived station yields** — `collectRunoutStations`' order repair clamps
it to the pin, never the reverse — so the sheet always reads AFT→FWD. A drag is clamped inside
its component and `RUNOUT_MIN_STATION_GAP_MM` clear of its neighbours and **may never cross
one**: crossing would renumber stations under their typed TIRs. Count changes on a component
with any pin work on the **full merged set** (`currentLocalStationPositions` — pins verbatim,
never coerced) and then freeze it wholesale, the one action that pins everything (an
insert/remove renumbers neighbours; freezing keeps every bubble planted through it): "+"
inserts into the widest gap (`planStationInsertion`, so the usual pair gets the new one
between them), "−" removes the most redundant unmeasured station
(`authoredStationIndexToRemove`, the geometric inverse, so "−" undoes "+"), and both
**re-key the readings** with their stations
(`RunoutReadings.withStationInserted`/`withStationRemoved`) so every value stays on the bubble
it was measured at. Pure math in `geom/RunoutStationPlacementMath.kt` (no `pdf → ui` dep).
The gesture commits **once on release** (`PreviewTuning` doctrine — no ViewModel write on a
drag frame), and `composeRunoutPdf` must thread placements into **both** `collectRunoutStations`
calls, the prelim budget plan included. See `docs/contracts/RunoutSheet.md` (Draggable stations).

### Runout readings are reference features
Per-station runout readings (`RunoutReadings` in the doc envelope — a TIR value + high-spot
clock marker per bubble) are **reference-only**, same posture as coupler bolt slots and wear
spots. They **never** affect `coverageEndMm`/OAL, body resolution, or
collision, and live outside `ShaftSpec`. Both fields are optional; a sheet exports
fine with neither. Keyed by `(componentId, stationIndex)` with render-layer orphan handling
(a reading whose station no longer exists is simply not drawn). The value + high-spot marker
and the keyway cutout must be drawn **identically in both bubble draw sites** —
`RunoutRoute.drawRunoutMarkers` (canvas) and `RunoutPdfComposer.drawPlacedBubbles` (PDF).
Pure clock/hit-test math lives in `geom/RunoutReadingMath.kt` (shared, no `pdf → ui` dep);
value formatting in `util/RunoutValueFormat.kt`. One reserved key, `COUPLING_PILOT_COMPONENT_ID`
(`"coupling_pilot"`, station 0 — the coupling face's pilot runout), deliberately matches no
resolved component and must **never** be pruned as an orphan. See `docs/contracts/RunoutSheet.md` (Runout Bubble
Editor, Coupling Face) and `docs/archive/RunoutBubbleEditor_PLAN.md`.

### Body blends are a draw-only face treatment
`Body.blendAftMm` / `blendFwdMm` / `blendProfile` cut a smooth machined transition **inward from
one face, out of the body that carries it** — the curve leaves the neighbouring diameter AT the
face and reaches the body's own Ø that far in. Nothing else moves: no other component's span
changes, drawn or stored, so the golden rule holds by construction. The blend's diameters are
**derived** from whatever sits across the face; nothing across the face, or a neighbour at the same
Ø, draws no blend at all. A liner is excluded from that lookup — a sleeve over mid-body is not a
diameter the shaft steps to — EXCEPT where a liner butts the face (a seal area): the shaft is cut
down under the liner but that seat is never drawn and its depth varies job to job, so the curve
leaves from the **midpoint of the liner OD and the body Ø** (`seatDiaUnderLiner`), a derived visual
cue nothing authors. A seat authored as its own body under a liner is NOT consulted —
`subtractBodiesAgainstNonBodies` trims a fully covered body out of the drawing.
Explicit bodies carry blends as stored fields; **auto spans carry them as shaft-space anchors**
(`AutoBlend`, `ShaftSpec.autoBlends` — the `AutoDiaOverride` posture: anchor at the span midpoint,
aft-most wins, dormant under a component, NEVER pruned), so a saved template keeps its seal areas
when the liners or the overall length move under it. Both resolve to the same `BodyBlend`, and the
draw sites cannot tell them apart. The face a blend curves from is the run's **DRAWN outer edge**
(for an explicit body that is its stored span, since an explicit body never absorbs the gap
beside it — see the normalize rule below; body fragmentation still trims it). Every bare-shaft
gap survives as its own auto run, so gap-side steps are the auto run's own faces and carry
`AutoBlend` anchors; an explicit face that meets a same-Ø surviving gap has no step and draws
no blend there. A blended face may carry a **seal area** (`Body.blendAftSeal`/`blendFwdSeal`, `AutoBlend.seal`) —
the radius cuts the fiberglass seats into, a fixed `SEAL_GROOVE_COUNT` (3) at `sealGrooveFracs`
stations (evenly spaced, margin at each end). Each cut draws as a **V notch in both silhouette
edges plus a DASHED line across seated on the notch floors** (`sealNotchGeom` — depth rides the
blend's drawn width, capped against the shaft radius; notch width capped against the groove pitch
so the V's never merge; dash `SEAL_DASH_ON_PT`/`SEAL_DASH_OFF_PT`, deliberately finer than the
hidden-keyway 6/4 so a near-side cut never reads as a far-side feature). Inset + dash are both
load-bearing: a solid full-height stroke is this drawing's glyph for a component face, and three of
them made one shaft read as 3–4 segments (on-device report) — so seal lines stop on the notch floors, and
`sealGrooveLines` + `curvePoints` derive floor and notch from the SAME `sealNotchGeom` so they
cannot disagree. The shop cuts 3–4, but the sheet is a cue rather than a count to machine from.
The cuts are made INTO the blend, so the control only appears once that face is blended, and both
draw sites build them from the same `bodyDrawEdges` (`aftSeal`/`fwdSeal` carry FLOOR radii; the
notches ride the curve point lists, so fill and stroke inherit them with no draw-site code). Blends print **no dimension rail and no footer row** — the
rails keep dimensioning the STORED span (dimension to the theoretical sharp corner), which is why
nothing in `DimensionRailLayout` or either composer's rail pass changed. That silence is what
licenses the one exaggeration: a 2" blend on a 25' shaft is sub-pixel at true scale, so the
**drawn** width takes a floor (`MIN_BLEND_WIDTH_PT`/`_PX`) capped at `MAX_BLEND_FRAC_OF_HOST` of
its run — the undercut-depth/wear-trace posture, safe ONLY because no exaggerated number can reach
a machinist. The stored length is never rewritten; a length longer than the body is clamped where
it is DRAWN. All three draw sites decompose the SAME `bodyDrawEdges` (`ui/resolved/BodyBlends.kt`) —
`ShaftRenderer` builds one silhouette path; `ShaftPdfComposer` and the runout/consolidated sheet's
the runout sheet keep their flat span separate (ONE shared body pass, `drawBodyRunsWithBreaks`
in `pdf/BodyRunDraw.kt`) because that span still hosts the S-break pair
(the break is cut into the FLAT span, the curves stay whole, and the break decision stays on the
run's FULL drawn width). The wear document deliberately keeps square faces — it omits machining
detail by product decision, the same posture as its keyway omission. Pure curve math in `geom/BlendProfileMath.kt`, deliberately
a general "join two radii across an axial span" primitive (`(x, radius)` points, the
`KeywaySilhouetteMath`/`SurfaceProfileMath` convention) so the queued liner-shoulder fillet and the
undercut end radius call it rather than reimplementing it. `surfaceSegsFrom` takes the blends so an
undercut or wear reading in a transition sees the real diameter — sampled at the blend's **true**
mm span, never the drawn floor. The controls are under the **add-dialog-parity rule**, not the
card-only carve-out (they change geometry): one shared `ui/screen/BlendSection.kt` renders them on
both carousel cards and in `AddBodyDialog`, as one chip row per face —
**Square | Blend | Seal area**. Those modes are exclusive AS PRESENTED only: a seal area INCLUDES
its blend (the cuts are machined across the blended section), and the stored model keeps length and
seal flag independent — `blendFaceMode`/`blendLenForMode` are the only projection, and switching
Blend ↔ Seal keeps the typed length. Do not restore the nested Blend-checkbox-reveals-Seal-checkbox
layout: it hid the seal behind a control nobody thinks to tick first (on-device report). Related: `normalizeBodies` never fuses an **explicit** body with
anything — not with another explicit body (absorbing one drops its Ø and its carousel card, so a
Ø6-to-Ø8 stepped shaft drew as one run) and not with adjacent auto fill (absorbing the gap made a
shortened explicit body span the whole run again: the typed length had no visible effect, its
selection highlight covered the merged run, its dimension rail span measured the merged extent,
and the remainder's auto card vanished — on-device report). Only auto spans merge with each
other; an auto run beside an explicit body inherits its Ø for continuity, so a same-Ø neighbour
draws at the same diameter with just the component face line between. See
`docs/COMPONENT_CONTRACT.md`.

### Liner shoulders are capability-gated, drawn from one silhouette
`Liner` shoulder fields (per end: length + reduced Ø + edge radius, all stored verbatim —
golden rule) draw a machined step at the liner end. ONE shared silhouette
(`geom/LinerShoulderMath.kt` → `linerTopSilhouette`, quarter-round fillet — deliberately NOT a
`BlendProfileMath` ease curve, a corner radius has no independent axial span) is decomposed by
BOTH draw sites (`ShaftRenderer` liner pass, `ShaftPdfComposer.drawLiners`); fill and stroke
build from the same point list. The authoring UI (liner card + `AddLinerDialog`, under
add-dialog parity) is gated by Settings → "Liner shoulders" (`SettingsStore.
linerShouldersEnabledFlow`, default OFF — on-device request: don't inundate the UI), but a
liner already carrying shoulder values keeps its controls and always draws them: a device pref
may hide empty entry fields, never authored work. The edge radius comes from the standard list
(`LINER_SHOULDER_STD_RADII_IN`, provisional) and prints ONLY as a footer line ("Liner shoulder
R: …") — no leader, no rail — which is what licenses the drawn clamps (blend-width floor,
oversize clamped where DRAWN, values never rewritten). The runout/consolidated sheet draws
shoulders too, through the ONE shared pass `pdf/LinerShoulderDraw.kt` (`linerShoulderSpecs` +
fill/stroke decomposition — both composers map mm through their OWN `xAt`/`rPx`, so a shoulder
inherits its liner's foreshortening; square liners keep their literal rect/line draws,
byte-identical). The surface envelope sees the step: shoulders ride `ResolvedLiner` (copied
verbatim at resolve — liners never fragment) and `linerSurfaceSegs` emits the reduced-OD end
segs at TRUE stored mm spans, never drawn floors; the step is modelled square (the fillet is a
corner treatment, not a diameter over a span), a shoulder OD at or above the liner OD
contributes no step, and crossing shoulder lengths clamp aft-first. The wear document stays
square by product decision.

### Breadcrumbs log events, never content
`util/AppLog` is the always-on diagnostic file (ring of two 256 KB files, shared via
Settings → Data → "Share diagnostic logs"); `util/VerboseLog` stays the dev-options-gated
logcat channel — do not merge them. A breadcrumb may say what happened and carry an exception;
it may **never** carry a document field value, a geometry number, or any other thing the user
typed into a drawing (document NAMES are allowed — the sharer owns them). `util/CrashReporter`
is the ONE seam to Crashlytics: Firebase is optional configuration (`app/google-services.json`
gitignored, plugins applied conditionally, CI materializes it from a secret), so nothing
outside that file may call Firebase directly — a direct call throws on every build without the
json. Collection stays ON in debug builds; the debug variant is what testers install.
See `docs/contracts/Diagnostics.md`.

### Drawing profiles are app-wide, never per-document
Named drawing preset profiles (`settings/DrawingProfile.kt`, Settings → Drawing → "Profiles")
capture the drawing LOOK — the whole `PdfPrefs` plus line thickness — as one DataStore JSON
map. A profile is device preferences and nothing more: **no doc-envelope field, no per-document
state, no "active profile" tracking** — applying is a one-shot copy through the EXISTING
setters (so every mirror fires, `FractionTypography` included), and a document never remembers
which profile drew it. Do not "improve" this by persisting a profile reference anywhere in a
document. Excluded from capture, deliberately: capability gates (per-component units, liner
shoulders — they decide which controls exist), the dual-units default (document behavior), the
per-job `RunoutConfig` pair (a FIT, not a look), theme/preview/undercut styling, dev options.
"Restore Drawing defaults" resets exactly the captured set, so a profile can always be undone.
Enums are stored by NAME through the tolerant `fromName` helpers; every payload field is
defaulted — profiles from older builds must keep loading.

### A keyway's WIDTH rides the diameter scale, its LENGTH the axial map
A sheet carries two scales: `diaPtPerMm` (the drawn shaft height, what the "Shaft height" slider
moves) and the compressed x map. A plan-view keyway straddles both — offset and length are axial,
**width and mill-arc radius are transverse and therefore ride `diaPtPerMm`**, so the slot stays
proportional to the drawn shaft at every height. Sizing the width off the axial scale pins it to
the page width (the x map always fills the content width), and raising the height then grows the
shaft while the keyway stays the size it was (on-device report). **Every round part of the slot is
an ELLIPSE** — x axial, y diametral — mill arcs and spoon bowl alike. True circles at the
transverse scale are the tempting shortcut (the mill radius is W/2 by definition, and the coupler
bolt holes ARE `rPx` circles), but a circle's AXIAL extent then grows with the height slider while
the slot's length stays page-bound, and the spoon bowl at 2.4× the keyway width swells until it
swallows its own slot (on-device report). The ellipse is free: `drawArc` sweeps a PARAMETRIC
angle, so the wall-tangent angle `geom/KeywaySpoonMath.kt` derives holds on the drawn ellipse.
The bowl's two semi-axes are INDEPENDENT: x rides the axial slot width
(`SPOON_BOWL_WIDTH_RATIO`), and y is the slot's drawn half-height plus the bowl's axial
poke-past distance (`KeywaySpoonBowl.ry` — uniform drawn clearance). Deriving y by stretching
the x-radius by `halfH / halfW` gave each axis its own scale's clearance, so the bowl drew tall
on every compressed sheet — several times more daylight above the slot walls than past the mill
arc (on-device report). Both draw sites take `ry` from the math; neither re-derives it. One
pure source for both draw sites, `geom/KeywaySlotMath.kt` (`ShaftRenderer.drawKeywaySlot` canvas /
`ShaftPdfComposer.drawKeywaySlotPdf` PDF; the canvas's two terms coincide, the construction is
shared anyway). The drawn width is **TRUE** — a keyway is a quarter of its shaft, not a blend's
couple of inches on twenty-five feet, so it reads at true scale and the normal result is exact
proportion. Only two clamps, and neither normally fires: a visibility floor
(`MIN_KEYWAY_WIDTH_PT`/`_PX`, raised to `KEYWAY_MIN_WIDTH_STROKES` — **2**, the legibility
criterion exactly, since walls leave `width − stroke` of daylight; more than that lifts ordinary
shafts off true), licensed by the blend-floor posture because a keyway prints **no dimension
rail**, only footer text off the stored W × D × L. `MAX_KEYWAY_FRAC_OF_HOST_DIA` caps the FLOOR
only — never a true width: clamping true geometry there would silently narrow an authored keyway
on a stubby taper end, and the only bound on a true width is the silhouette itself.
`KeywayWidthFidelityTest` pins the reach — at default line weight every 2″–14″ shaft draws exactly
true at 100% height and up. The drawn length floors at `minKeywaySlotLenPx` (AXIAL arc radii),
guarding authored geometry: a keyway shorter than half its own width would run its arcs past its
walls. Untouched: the 90° silhouette notch was always radial, and the body-keyway window's
true-scale pin (`keywayPinnedBodySpans`) still protects the axial term. See
`docs/PDF_EXPORT.md` §5.2c.

### Spooned keyways are a draw-only variant
`keywaySpooned` (on `Taper` and `Body`) is a **drawing** flag — it changes nothing in the model,
resolve, OAL, collision, or footer geometry (only the footer *text* gains `(spooned)` plus a
`SPOONED_KW_NOTE` line under the KW spec: KW length runs to the base of the spoon, where the
mill ends). A spooned
**open** keyway keeps the normal keyway (full-length walls + mill semicircle) and **adds** an
enlarged circle around the closed (LET) end — the mill semicircle stays as an inner reference line
inside the bowl. It is **ignored for floating keyways** (offset > 0) — the UI disables the toggle
there. The bowl must be drawn **identically in both keyway draw sites** —
`ShaftRenderer.drawKeywaySlot` (canvas) and `ShaftPdfComposer.drawKeywaySlotPdf` (PDF). Pure bowl
math (radius, y-semi, wall tangent, major-arc sweep) lives in `geom/KeywaySpoonMath.kt` (shared,
no `pdf → ui` dep); `SPOON_BOWL_WIDTH_RATIO` sizes its axial term and the y-semi is the slot
half-height plus the poke-past clearance (see the keyway-scale invariant above). Same posture as the wear-pit
"X" and runout-marker draw-both-sites rules.

### Diameter callouts are BELOW-only, tiered, and footer-formatted
On-shaft diameter callouts (body OD, liner OD — `buildBodyOdCallouts`/`buildLinerOdCallouts`
in `ShaftPdfComposer.kt`) all hang **BELOW** the shaft; do not reintroduce above/below
alternation. Labels use `formatDiaWithUnit` (≤3 decimals, trailing zeros trimmed) to match the
footer's "Ø" text — never the raw 4-decimal format. Bodies and liners are **separate OD
groups** — a liner OD is never deduped against a body OD. Horizontally-close labels stack onto
a second row via `geom/DiameterCalloutLayout.kt` (pure, unit-tested), the same two-tier
posture as runout bubbles. PDF-only — no on-screen canvas equivalent, so no draw-both-sites
rule applies.

Two visibility controls gate the pass, and they compose as an AND:
- **Per component** — `Body.showDiaOnDrawing` / `Liner.showDiaOnDrawing` (and
  `ShaftSpec.showAutoBodyDia`, ONE flag for every auto span even though per-section Ø
  overrides exist — visibility is all-or-nothing for bare shaft). Draw-only,
  additive/defaulted. **Body and bare-shaft callouts are
  OPT-IN — both flags default `false`** (on-device preference: the schematic stays clean
  unless a body's Ø is deliberately shown; a document with no flags prints no body Ø
  callouts). Liners default `true`. The filter runs **BEFORE the group-by-Ø** — that is the
  whole point: hiding one
  body of a shared-Ø group moves the anchor to the longest body of that Ø still shown, rather
  than deleting the value (the on-device case: a Ø printed over a fiberglassed run that could
  not have been measured there). The spec→drawable mapping (`ShaftSpec.bodyForPdf`) must strip
  fragment ids with `resolvedBodyBaseId`, so hiding a split body hides every run. The footer's
  "Body:" Ø list is deliberately NOT gated — the value is true for the shaft; only its
  placement was wrong. These toggles are **card-only**, an explicit carve-out from the
  add-dialog-parity invariant on the same grounds as the coupler slot's "show dimension rail".
- **Per sheet, blank drafts only** — `PdfExportOptions.blankDiaCallouts` drops the whole
  pass (line, arrow, and rule) so a write-in sheet can be annotated freehand. One rule,
  `PdfExportOptions.showDiaCallouts`, is the only place the two combine; the four sites that
  build export options pass the raw preference and never re-derive it.

See `docs/PDF_EXPORT.md` §5.3.

### Dimension values seat in a break in the line
`PdfDimensionRenderer.drawPlanned` draws each dimension line as **two stubs**
(`xa→gapLeft`, `gapRight→xb`) with the value seated in the gap, vertically centered on the
line — not floating above a continuous line. The gap (label width + 2·`textPad`) is cut
**only** when both stubs can host an inward arrowhead (`canFitInwardArrows`). Spans too
short for that **fall back** to the original style (continuous line, label above at
`textAboveDy`). Do not reintroduce always-above label placement. The top OAL rail is planned
and drawn through the same path, so it breaks too.

**Arrow direction is a SEPARATE question from where the value landed**
(`DimensionRailLayout.arrowsPointInward`, `Placement.arrowsInward`): outward (tips-in) heads
hang OUTSIDE the extension lines, so on spans sharing a boundary they meet and cross into an
X. That price is only worth paying when the heads genuinely do not fit *between* the extension
lines — `(xb − xa) ≥ 2·arrowSize + ARROW_CLEAR` — so a fallback value overhead never costs its
span the inward heads. Do not re-tie direction to `Placement.inline`. Same split in the
wear/undercut strip rails (`WearRailSpanLayout.seatsInBreak` decides the break, `arrowInward`
the heads, through the same shared predicate); `planUndercutRailRows` reserves rows off
`seatsInBreak`, never off the arrows. The head is a slim 2:1 V (barb spread = half the length),
one shape at every rail site; its length is user-set — `PdfPrefs.arrowSizePt`, Small 3 /
(default) / Medium 4 / Large 5 pt, in both PDF options sheets and Settings → Drawing
"Dimension arrows". It reaches only the two dimension-rail composers (schematic, consolidated);
the wear/undercut strips keep their own fixed head.

**Labels and rail LINES share ONE collision space** — a floating value lives in the *next*
rail's band, so per-rail collision tracking is blind exactly where the labels overlap. The
pure planner `geom/DimensionRailLayout.kt` places every span at once (top OAL rail included),
treating both placed labels and every rail line as obstacles; the renderer keeps only the
Canvas work, and no collision logic may be duplicated back into it. Resolution order:
(a) **slide the value horizontally along its own span** — smallest shift from center inside
`[xa+textPad+half, xb−textPad−half]`, tightened by `arrowSize` on both sides for an inline
value so the break keeps its inward arrows; (b) only then bump a floating value vertically
(bounded, never past the content top). A rail carrying a floating value **lifts every rail
above it** — the OAL rail included — by one label band (glyph height + gap), cumulative per
intervening fallback rail. Inline-vs-fallback is decided from **x-geometry alone**, so the
lifts are known before the vertical budget: both composers fold them in
(`ShaftPdfComposer`'s `computeTopY` fit loop; `RunoutPdfComposer`'s `railsBlockH`, off a
prelim linear-map plan since the real x map needs the budget). Blank drafts plan on the
write-in gap width, so the gaps get the same clearance as printed values; a span too short
for the full `BLANK_DIM_GAP_PT` **shrinks its gap** toward `BLANK_DIM_GAP_MIN_PT`
(`blankGapWidth`) instead of losing it, and only a span too tight for even that falls back to
a gapless continuous line. ONE `labelWidth(span)` feeds both the planner's reserved box and
the cut gap — they can never disagree.
PDF-only — the on-screen preview rasterizes the real PDF, so there is no separate
draw path and no canvas equivalent to keep in sync. See `docs/PDF_EXPORT.md` §5.4.

### Fractions are SET, never spelled
Every drawn fraction goes through ONE construction — `util/FractionText.kt` (pure parser:
plain / fraction / gap runs, no Android) feeding `util/FractionTextRenderer.kt` (the
`Paint.measureRichText` + `Canvas.drawRichText` pair). `LengthFormat.formatInchesSmart`
emits plain `n/d` for **every** denominator and never a Unicode vulgar glyph — a font only
carries `¼ ½ ¾ ⅛ ⅜ ⅝ ⅞`, which is what let one sheet print a proper `⅝` beside a typed-out
`11/16`. Do not reintroduce a vulgar-glyph map. Digits set at `digitScale` 0.64 with the
bar on the **math axis** (half the base cap height above the baseline); that scale is the
largest that keeps the stack inside the base font's own ascent/descent, so **no vertical
layout budget changes** — `FractionTextRendererTest` renders to a bitmap and fails if a
raise breaks it. A stack is NARROWER than the same characters inline, so **measure and
draw convert together at every site**: measuring plain and drawing rich leaves a rail's
value off-centre in its break (`ellipsizeToWidth(rich = …)` carries the same flag). The
neighbour guard keeps dates (`12/25/2026`), decimals (`1.5/2`) and ratios (`1:12`) plain,
but it CANNOT save a job number like `24/1138` — free-text footer fields (Customer /
Vessel / Job # / Date) therefore stay on plain `drawText` by caller choice, and editable
text (`dispKw`, numeric fields) is never prettified because it must round-trip through
`parseFractionOrDecimal`. **Spacing is per-style** (`FractionTextStyle.Stacked`/`.Diagonal`/
`.Plain`, via `forStyle` — never `copy(style = …)` one preset onto another): stacked leads with
the BAR at mid-height, which binds to the preceding digit like a hyphen, and closes on a
numerator row a trailing `"` attaches to, so it takes the looser numbers; diagonal leads with the
numerator glyph at cap height and closes on the baseline, so it reads correctly tighter. A
narrower construction also **seats more values inline** — the break costs
`labelWidth + 2·textPad + 2·arrowSize`, so `PdfDimensionRenderer.textPad` is the shared
`DIM_BREAK_TEXT_PAD_PT` (4 pt, the strip rails' gap), NOT a private 6 pt: padding wider than the
value itself pushed short spans into the above-line fallback for nothing. The style is
**user-set** — `PdfPrefs.fractionStyle`, Settings →
Drawing → "Fractions" plus the same `FractionStyleChips` ungated in both PDF options sheets:
Stacked | **Diagonal (default)** | Plain, one source in `FractionStyle.Default` (the pref
default, `FractionTextStyle.Default` and `fromName`'s fallback all read it). Draw sites take NO
style parameter; they read the
process-wide `FractionTypography.active` mirror, whose ONLY writer is
`SettingsStore.updatePdfPrefs` (the `SettingsStore.pdfPrefs` pattern — threading a uniform
drawing decision through every composer's private draw functions costs more than it buys). That
mirror is not snapshot state, so every preview's render-inputs record must carry `fractionStyle`
as a **re-render key** or that tab keeps drawing the old style. See `docs/contracts/FractionTypography.md`.

### Mixed units and dual display are a DISPLAY AXIS
Per-component display units (`unit_overrides` — resolved component id → `UnitSystem`) and
inline dual display (`dual_units`) live in the doc envelope and change **only how a value
prints**. Canonical geometry stays millimeters everywhere (golden rule): no override may
rewrite a stored value, enter geometry, resolve, OAL, collision, or any layout solve. Both
default OFF (`emptyMap()`, `false`), so a document that never touches them prints
byte-identically to before they existed. One resolver answers "which unit for this
component?" — `util/DisplayUnits.kt` (`unitFor(componentId)`, falling back to the document
unit); sites with no component in hand (OAL rail, bare shaft) use the document unit
directly. An override whose id no longer resolves is **skipped at the render layer, never
pruned** — the runout-reading/wear-pit posture.

Dual values are SET in one of two layouts — `PdfPrefs.dualUnitLayout`, Settings → Drawing →
"Dual-unit layout" and both PDF options sheets. **INLINE** (the default) is
`<primary> [<secondary>]` on one line; **STACKED** is the primary over the secondary. Either way
**both terms always carry their unit suffix** — the moment a sheet mixes units, a bare number is
how a shaft gets machined wrong — and the SECONDARY always takes the compact format (mm to one
decimal), never the dimension formatter's 3 decimals: a converted value is a courtesy, not a
measurement.

A stack is **~55% NARROWER** (it measures as its wider LINE, not the sum plus brackets), which is
the whole economic case: values that inline dual pushed above the dimension line seat back in the
break, and every value restored to a break removes a fallback rail — and with it one label band of
lift from every rail above. `DualStackLedgerTest` pins that ledger and fails if stacking ever costs
more paper than it saves.

Stacking is the app's **first label that moves HEIGHT**, so it carries a discipline the
width-only fraction work never needed: **every vertical budget it touches derives from ONE number**,
`Paint.dualStackMetrics()` (`util/DualLabelRenderer.kt`), and the two terms survive as separate
strings all the way to the draw site (`util/DualLabel.kt` — a pre-joined `String` cannot be
stacked). The rail planner takes it as an INFLATED ASCENT (`geom/DimensionRailLayout.TextMetrics`)
and needs no other change; the wear/undercut strips thread ONE row height into both the reserved
band and the drawn rows (`wearRailRowHeightPt`/`undercutRailRowHeightPt` — they were two
independent numbers before, nesting only by luck); the Ø tier step, the wear Ø callout rows, and
the consolidated sheet's rotated in-profile values read it too. Measure and draw convert together
at every site (`measureDualLabel`/`drawDualLabel`), the fraction pair's rule.

**A sheet that cannot afford the stack gives it up WHOLE** — per sheet, never per label, because a
page with some two-line and some one-line values reads as a mistake. Each composer decides once,
before drawing, and logs the fallback. A **keyway** carries its own unit the same way, under a derived key
(`keywayUnitKey(id)` = `"<id>#kw"`, resolved by `DisplayUnits.keywayUnitFor` with the chain
keyway → component → document): a European shaft arrives with its keyway and thread metric and
nothing else, and flipping the parent taper to print one metric keyway would drag its L.E.T. /
S.E.T. / Length along. Unlike the component chip it governs **entry** as well as print — typing a
20 × 12 mm keyway as 0.7874 × 0.4724 in is a rounding hazard — so it is value entry, lives under the
add-dialog-parity rule (NOT the card-only carve-out), and sits with the keyway fields rather than at
the card's foot. A metric thread
(`Threads.metricDesignation`, e.g. `M20×2.5`, parsed by `util/ThreadDesignation.kt`) prints
its designation verbatim and carries an implicit mm override; a designation converted to
decimal inches stops meaning anything. Numeric **entry** fields always take the document
unit, on cards and in Add dialogs alike — the chip governs how a component PRINTS, not how
it is typed. The `Prints in: in | mm` chip is card-only, the third documented carve-out from
the add-dialog-parity invariant. See `docs/DATA_MODEL.md`, `docs/PDF_EXPORT.md` §4, and
`docs/contracts/AddComponentDialogs.md`.

### Golden rule: user inputs are SACRED
A value the user typed into a component field is kept **exactly as entered** — no system
(snap, rounding, derivation, "helpful" adjustment) may rewrite it, no matter how small
the edit (.001 counts). The user changes component values; components get put in their
place; auto-bodies fill the gaps — that is the design. Derived values (auto rate text,
auto-body spans) may move; authored values may not.

Concretely: carousel update callbacks (`onUpdateBody/Taper/Thread/Liner`) receive
committed field values **verbatim** — no snap-to-anchor on any typed-commit path. The removed
`applySnapped{…}Update` wrappers (2026-07-26) snapped recomputed start/end to
component-edge anchors (±1 mm) and silently rewrote typed values: shortening a
FWD-referenced taper by less than the tolerance snapped its start back to the old
boundary, undoing the edit entirely. **Nothing snaps a position any more**: the only coarse
gesture that did — tap-to-add — was removed along with its whole snap pipeline
(`ui/viewmodel/SnapUtils.kt`), so any reintroduced snapping is new code, not a restoration.
Same posture as the 2026-06-19 removal of the
`snapForwardFrom` cascade from ViewModel updates: positions are user-authored; nothing
mutates them except a direct user action. See `docs/contracts/ShaftScreen.md`.

### The preview canvas tap is selection only
Tapping a component in the Schematic tab's preview highlights it (`onTapComponentId`); tapping
bare canvas does **nothing**. The bare-canvas tap used to open an add-component chooser at the
tapped position — it fired unintentionally far more often than it was wanted and was never used
deliberately (on-device report). Components are added from the FAB chooser, the single add
entry point. Do not reintroduce a bare-canvas tap action without asking: the objection was to
the gesture existing, not to its behavior. See `docs/UI_CONTRACT.md` §3.1.1.

### Numeric input commit behavior
`NumericInputField` only calls `onCommit` on blur **if the value changed** since focus
was gained. A tap-and-leave with no edit must be a no-op. This prevents spurious
auto-body promotion and unnecessary ViewModel updates. See `docs/contracts/NumberField.md`.

A commit also requires a **focus baseline**: `shouldCommitOnBlur`
(`ui/input/BlurCommitPolicy.kt`) returns false when the captured-on-focus text is null,
because Compose delivers an initial `onFocusChanged` with `isFocused = false` on attach.
Do not restore a "null baseline → commit defensively" rule — that fired `onCommit` on
every composition, and `rememberBodyDefaults` (unlike the dirty gate and undo history)
does not dedup, so composing an explicit-body card rewrote the Add-Body length default —
and worse, composing an **auto-body** card committed its displayed (derived) Ø into
`ShaftSpec.autoBodyDiaMm`, pinning the bare-shaft Ø and marking the document dirty with no
user edit. Fixed 2026-07-26, pinned by `BlurCommitPolicyTest` + `NumericInputFieldBlurTest`.

### Auto-body promotion
Auto-body cards in the carousel (`ResolvedComponentSource.AUTO`) show Start/Length as
**disabled** (greyed, derived-value) fields — there is no field-edit promotion path. The
**Ø field is editable** and commits a **per-section** override (`ShaftSpec.autoDiaOverrides`
— a shaft-space `AutoDiaOverride(anchorMm, diaMm)` list, anchor system-placed at the span
midpoint; `diaMm` stored verbatim, golden rule). Per-span precedence: aft-most anchor inside
the span `[start, end)` → legacy shaft-wide `autoBodyDiaMm` (> 0) → neighbor derivation.
When a separator's deletion merges two differing sections, the merged span takes the **more
aftward** override (aft is authored first); the FWD one lies **dormant — never pruned** (no
orphans by construction, same posture as runout readings/wear pits), so re-splitting the run
resurrects it as authored. Clearing the field (≤ 0) drops that section's override only.
Overrides never affect auto-span positioning and do **not** promote the card. A gap beside an
explicit body survives as its own run (never absorbed), so its anchors stay live while the gap
exists.
Promotion to a real body happens only on an **explicit user action**: ticking the
**"Explicit body"** checkbox (relabeled from "Make editable body"). Checking it calls
`onAddBody` with the auto-body's current derived Start/Length/Ø, guarded by a `promoted`
state so it fires once. Explicit-body cards carry the same "Explicit body" checkbox,
checked; unchecking opens an AlertDialog ("Make body automatic?", with an extra sentence
when `body.hasKeyway` warning that the keyway will be lost) — confirming demotes via the
existing `onRemoveBody(b.id)` pipeline (the resolve layer regenerates the auto-fill span);
Cancel keeps it explicit. On **both** cards the checkbox row sits **above** the
Start/Length/Ø fields, so it stays put when checking it swaps the card from auto to
explicit. `testTag`s: `body_explicit_checkbox`, `body_demote_confirm`. See
`BodyPagerCard.kt`.

### Bodies are fillers, not collision participants
Bodies (stored `ShaftSpec.bodies`) are the shaft's fluid base. A body legitimately runs
**under a liner** (a sleeve over the shaft) and **up against a taper**; the resolve layer
(`subtractBodiesAgainstNonBodies`) trims the *drawn* body around those components, so a
*stored* body span that crosses them is **not** a conflict. Therefore bodies are
**excluded from `collidingIds()`** — do not re-add them. (An earlier "non-negotiable
bodies" experiment flagged those normal overlaps as errors and referenced bodies by a
stored-list index that didn't match the drawn cards — false "Overlaps Body N" warnings.
Reverted 2026-07-21.) Adding a taper/thread/liner over a body **splits** it as before
(`splitBodiesAround`), **except** a body that has a keyway, which is never fragmented
(light protection — it stays one whole card, keyway intact). On delete, `mergeBodiesAround`
rejoins flanking fragments but **never merges across a component still occupying the gap**
(that would manufacture a long phantom body).

### OAL field
The OAL is **always user-typed** — there is no auto mode. It calls `onSetOverallLengthMm`
on **every parseable keystroke** (not just on blur). This is intentional — the preview
updates live. Do not change this to commit-on-blur only. An **empty** field on IME-Done or
blur commits nothing and reverts the text to the stored value; it never zeroes the shaft.
`overallLengthMm == 0` means "not typed yet", not an error: the field draws no red state and
the renderer's `safeSpec` fallback (`ui/drawing/compose/ShaftDrawing.kt`, mirrored in
`ShaftThumbnail.kt` and the preview OAL badge) draws such a shaft to its coverage end.
Nothing backfills it — not a load, not the first component added.
See `docs/contracts/OverallLength.md`.

## Commit policy
Do **not** auto-commit. The user reviews changes before every commit.
