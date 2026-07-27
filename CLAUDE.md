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
- **Keyways 180° apart**: the spec-level toggle appears on keyway-bearing cards when the
  shaft has ≥ 2 keyways, and in `AddBodyDialog`/`AddTaperDialog` when adding would reach
  ≥ 2 (≥ 1 existing + this dialog's keyway defined). Same condition on both surfaces.
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
of auto-body/subtraction geometry. See `docs/CouplerBoltSlot.md`.

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
strip pits (PDF). Pure sizing/hit-test/clamp math lives in `geom/WearPitMath.kt` (shared, no
`pdf → ui` dep). See `docs/RunoutSheet.md` (Wear Pits).

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
value formatting in `util/RunoutValueFormat.kt`. See `docs/RunoutSheet.md` (Runout Bubble
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
mutates them except a direct user action. See `docs/ShaftScreen.md`.

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
