# ShaftSchematic – Claude Code Instructions

## Project overview
Android app (Kotlin / Jetpack Compose) for designing marine propulsion shafts.
All model values are **canonical millimeters (mm)**. Unit conversion (mm ↔ in) happens
only at the UI edge for display and input — never in the model, ViewModel, or renderer.

## Docs
Detailed contracts live in `app/src/main/java/com/android/shaftschematic/docs/`.
Read the relevant doc before editing a subsystem. Key files:
- `ShaftScreen.md` — overall screen contract, commit-on-blur rule, unit edge rule
- `AddComponentDialogs.md` — add-dialog parity rules (mirror carousel cards)
- `FreeToEndBadge.md` — badge visibility invariants
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
  (`ComponentCarousel.kt`, `ResolvedThread` branch, `!includeInOal` block).
- **Liner AFT/FWD reference**: `AddLinerDialog` must show "Measure From: AFT | FWD" chips.
- **Body keyway**: `AddBodyDialog` and the explicit-body carousel card must both expose
  the keyway section (KW from AFT | FWD chips, W × D, L, offset, spooned toggle). The
  auto-body card intentionally omits it (auto-bodies can't host keyways until promoted).
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

### Coupler bolt slots are reference features
Coupler bolt slots (`ShaftSpec.couplerBoltSlots`) are radial cutouts drawn on the shaft
but they **never** affect overall length (`coverageEndMm` ignores them), **never** split
bodies, and **never** collide with other components (`collisionGroup() → null`). Do not
add them to `coverageEndMm`, `ensureOverall`, body-split/merge, or overlap validation.
They are resolved as `ResolvedCouplerBoltSlot` *after* body resolution so they stay out
of auto-body/subtraction geometry. See `CouplerBoltSlot.md`.

### Wear pits are reference features
Wear pits (`WearRecord.pits` — a `WearPit` "X" marker per pit/dye-failure, small or large) are
**reference-only**, the same posture as wear spots / coupler bolt slots / runout readings. They
**never** affect `coverageEndMm`/OAL, body resolution, collision, or the Free-to-End badge, and
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
`pdf → ui` dep). See `RunoutSheet.md` (Wear Pits).

### Wear diameter readings are reference features
Measured-Ø readings (`WearRecord.diaReadings` — a `WearDiaReading` per measured station,
printed as a value below the shaft with a leader to a witness tick) are **reference-only**,
the same posture as wear pits / wear spots / runout readings / coupler bolt slots. They
**never** affect `coverageEndMm`/OAL, body resolution, collision, or the Free-to-End badge,
and they ride the existing `wear_record` envelope field (additive `diaReadings` list — no
codec/autosave plumbing). Keyed by **resolved component id** (liner/taper/body, explicit or
auto) + component-local `axialMm`; orphans are skipped at the **render layer**, never
pruned at decode (same rule as pits/runout readings). `diaMm` is a typed measurement —
stored **verbatim** (golden rule); `0` = placed-but-empty, drawn only in the overlay, never
printed. Callouts are placed by the shared pure engine `geom/WearDiaCalloutLayout.kt`
(order-preserving spread, two-row stagger, dogleg leaders) and must render **identically**
in both draw sites: `ComponentWearDetailOverlay` (canvas) and `WearPdfComposer` (liner
readings → that liner's detail strip; body/taper readings → under the main profile). Labels
use `formatDiaWithUnit`, no `Ø` prefix. On the **consolidated runout sheet** the same
readings instead draw INSIDE the profile at their station — one rotated haloed column via
`drawDiaReadingsInProfile` (`RunoutPdfComposer`), liners included, `Ø`-prefixed — replacing
below-shaft callouts there; the wear document itself (the authoring surface) keeps its
callout engine unchanged. See `RunoutSheet.md` (Wear Diameter
Measurements) and `WearDiaMeasurements_PLAN.md`.

### Worn sections are reference features
Worn sections (`WearRecord.wornSections` — a `WornSection` per designated measured area,
step 1 of the runout/wear consolidation) are **reference-only**, the same posture as the
other wear/runout marks. They **never** affect `coverageEndMm`/OAL, body resolution,
collision, or the Free-to-End badge, and they ride the existing `wear_record` envelope
field (additive `wornSections` list — no codec plumbing). Like undercuts they are
**shaft-space** (`startFromAftMm` + `lengthMm`, may cross component edges, no orphans,
never pruned at decode; `authoredReference` reuses `UndercutReference` SET values as
display-only Distance metadata — canonical never moves on a reference switch). `diaMm` is
a **list** of typed measurements — stored verbatim in list order (golden rule); values ≤ 0
never print. They draw on the **runout sheet**: boundary lines at the span ends and the
values **inside the profile**, rotated 90°, each over a sheet-white halo so no profile
line crosses a number. ONE draw implementation for both sites — `drawWornSections`
(`pdf/RunoutPdfComposer.kt`), called by the PDF and by the `RunoutRoute` canvas via
`nativeCanvas`; pure layout in `geom/WornSectionMath.kt`. No carousel card, no Add dialog
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
Settings choice, never the default — Settings → PDF Export "Default drawing size",
`PdfPrefs.curveLoHeightIn`/`curveHiHeightIn`; an inverted pair flattens at the low
anchor: a larger shaft never draws smaller; `defaultVisualScale` keeps the flat
`VISUAL_DIA_SCALE_PT_PER_MM` only as the degenerate fallback),
spans foreshortened above per-kind width floors via the
pure mapping `geom/ProfileCompression.kt`, everything through the one piecewise `xAt`.
**TAPERS may shrink but NEVER equalize** — they carry NO flat floor (a flat floor made
a 19.5" and an 11.5" taper draw identical, on-device report); instead a
ratio-preserving fraction-of-true floor (`PROFILE_TAPER_MIN_FRAC_OF_TRUE`, λ-fit like
the liner raises — ratio preservation is structural: same λ, same K threshold, so
relative taper widths always read true, and the drawn height never yields to it). The
SCHEMATIC composer uses the lean `SCHEMATIC_MIN_*` floors (28/40/56 — its values live
on rails/callouts, so proportion wins); the runout/consolidated sheet keeps the
writable `PROFILE_MIN_*` floors.
Foreshortened body runs draw the S-break pair laid out by `breakPairLayout`
(`pdf/BreakSymbol.kt`) — gap widens up to half the run, then amplitude flattens, so the
two edges always keep ≥ 1 pt of daylight and never overlap.
**Liners compress in SIZE only** (finite `PROFILE_MIN_LINER_PT` floor — proportional
foreshortening, NEVER a body-style S-break cutout; the S-break glyph is a body-only draw
path); the per-job **"Liner compression" pair** (`RunoutConfig.linersProportional` +
`linerCompression` → derived `linerMinFracOfTrue`, fed to
`ProfileFeatureSpan.minWidthFracOfTrue`) can raise the liner floor toward true width —
**the drawing height takes PRECEDENCE**: the raises are best-effort, never enter the
scale solve, and λ-fit whatever room the page has at the selected height
(`fracFitFactor`) — do not let a liner demand lower the drawn shaft; control on the
Output tab + schematic Tune sheet with a live kept-% readout
(`estimatedLinerKeptFracOfTrue`); ONLY keyway-bearing bodies stay pinned at true width
with the height yielding (`solveMaxProfileScale`). The **"Shaft height" slider** (`RunoutConfig.heightScale`,
per-job in the envelope — ONE value behind the runout/consolidated sheets AND the
schematic, `composeShaftPdf(heightScale)`) multiplies the solved scale; the drawn shaft
is hard-capped at **1.5" on paper** (`PROFILE_MAX_SHAFT_HEIGHT_PT`, an ABSOLUTE ceiling
— a short shaft that would draw taller at width-fit is capped and simply doesn't span
the page) and by the page budget (`exaggeratedProfileScale`, pure/unit-tested). The slider
selects the drawn height by VALUE in paper inches — track ends at 1.5" or the shaft's
300% height, whichever is less (`drawnShaftHeightPt`/`heightFracForDrawnHeight`);
commits near the standard height snap to exactly 100%. Liners draw **unfilled on this sheet**
regardless of `shadedLiners` so halos don't read as pasted boxes. Division of labor: the Wear page is the **authoring surface** for
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
gate guards every export surface. See `RunoutSheet.md` (Consolidation step 5) and
`docs/PDF_EXPORT.md` §5.6–5.7.

### Undercuts are reference features
Undercut sections (`UndercutRecord.undercuts` — an `Undercut` per machined-below-surface
span, printed on its own Undercut Drawing tab/PDF) are **reference-only**, the same posture
as wear spots / pits / dia readings / runout readings / coupler bolt slots. They **never**
affect `coverageEndMm`/OAL, body resolution, collision, or the Free-to-End badge, and they
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
+ `geom/UndercutMath.kt` (cluster windows, clamps, hit-tests; no `pdf → ui` dep) with
`ui/resolved/SurfaceSegs.kt` as the single resolved→surface mapping. See
`UndercutDrawing_PLAN.md`.

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
`Appearance.md`.

### Runout readings are reference features
Per-station runout readings (`RunoutReadings` in the doc envelope — a TIR value + high-spot
clock marker per bubble) are **reference-only**, same posture as coupler bolt slots and wear
spots. They **never** affect `coverageEndMm`/OAL, body resolution, collision, or the
Free-to-End badge, and live outside `ShaftSpec`. Both fields are optional; a sheet exports
fine with neither. Keyed by `(componentId, stationIndex)` with render-layer orphan handling
(a reading whose station no longer exists is simply not drawn). The value + high-spot marker
and the keyway cutout must be drawn **identically in both bubble draw sites** —
`RunoutRoute.drawRunoutMarkers` (canvas) and `RunoutPdfComposer.drawPlacedBubbles` (PDF).
Pure clock/hit-test math lives in `geom/RunoutReadingMath.kt` (shared, no `pdf → ui` dep);
value formatting in `util/RunoutValueFormat.kt`. See `RunoutSheet.md` (Runout Bubble
Editor) and `docs/RunoutBubbleEditor_PLAN.md`.

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
math (radius, wall tangent, major-arc sweep) lives in `geom/KeywaySpoonMath.kt` (shared, no
`pdf → ui` dep); the single `SPOON_BOWL_WIDTH_RATIO` constant sizes it. Same posture as the wear-pit
"X" and runout-marker draw-both-sites rules.

### Diameter callouts are BELOW-only, tiered, and footer-formatted
On-shaft diameter callouts (body OD, liner OD — `buildBodyOdCallouts`/`buildLinerOdCallouts`
in `ShaftPdfComposer.kt`) all hang **BELOW** the shaft; do not reintroduce above/below
alternation. Labels use `formatDiaWithUnit` (≤3 decimals, trailing zeros trimmed) to match the
footer's "Ø" text — never the raw 4-decimal format. Bodies and liners are **separate OD
groups** — a liner OD is never deduped against a body OD. Horizontally-close labels stack onto
a second row via `geom/DiameterCalloutLayout.kt` (pure, unit-tested), the same two-tier
posture as runout bubbles. PDF-only — no on-screen canvas equivalent, so no draw-both-sites
rule applies. See `docs/PDF_EXPORT.md` §5.3.

### Dimension values seat in a break in the line
`PdfDimensionRenderer.drawSpan` draws each dimension line as **two stubs**
(`xa→gapLeft`, `gapRight→xb`) with the value seated in the gap, vertically centered on the
line — not floating above a continuous line. The gap (label width + 2·`textPad`) is cut
**only** when both stubs can host an inward arrowhead — the same `canFitInwardArrows`
predicate that chooses arrow direction — so inline spans always get inward arrows. Short
spans, or a label colliding with one already placed on the rail, **fall back** to the
original style (continuous line, label above at `textAboveDy`, bounded bump). Do not
reintroduce always-above label placement. The top OAL rail uses the same `drawSpan`, so it
breaks too. PDF-only — the on-screen preview rasterizes the real PDF, so there is no separate
draw path and no canvas equivalent to keep in sync. See `docs/PDF_EXPORT.md` §5.4.

### Golden rule: user inputs are SACRED
A value the user typed into a component field is kept **exactly as entered** — no system
(snap, rounding, derivation, "helpful" adjustment) may rewrite it, no matter how small
the edit (.001 counts). The user changes component values; components get put in their
place; auto-bodies fill the gaps — that is the design. Derived values (auto OAL, auto
rate text, auto-body spans) may move; authored values may not.

Concretely: carousel update callbacks (`onUpdateBody/Taper/Thread/Liner`) receive
committed field values **verbatim** — no snap-to-anchor on any typed-commit path. The removed
`applySnapped{…}Update` wrappers (2026-07-26) snapped recomputed start/end to
component-edge anchors (±1 mm) and silently rewrote typed values: shortening a
FWD-referenced taper by less than the tolerance snapped its start back to the old
boundary, undoing the edit entirely. Snapping is for coarse gestures only (tap-to-add,
`ui/viewmodel/SnapUtils.kt`). Same posture as the 2026-06-19 removal of the
`snapForwardFrom` cascade from ViewModel updates: positions are user-authored; nothing
mutates them except a direct user action. See `ShaftScreen.md`.

### Numeric input commit behavior
`NumericInputField` only calls `onCommit` on blur **if the value changed** since focus
was gained. A tap-and-leave with no edit must be a no-op. This prevents spurious
auto-body promotion and unnecessary ViewModel updates. See `NumberField.md`.

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
**Ø field is editable** and sets the single bare-shaft Ø (`ShaftSpec.autoBodyDiaMm`,
0 = unset → derive from neighbors) shared by **all** auto spans — one piece of stock. It
wins over neighbor derivation, never affects auto-span positioning, and does **not**
promote the card.
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
`ComponentCarousel.kt`.

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

### Free-to-End badge suppression
The badge is hidden when there are no precision components (tapers, non-excluded threads,
liners) and the shaft is not oversized. With only bodies, auto-bodies visually fill the
remainder, so the badge value would always mislead. See `FreeToEndBadge.md`.

### OAL field
The OAL field calls `onSetOverallLengthMm` on **every keystroke** in manual mode (not
just on blur). This is intentional — the preview updates live. Do not change this to
commit-on-blur only.

## Commit policy
Do **not** auto-commit. The user reviews changes before every commit.
