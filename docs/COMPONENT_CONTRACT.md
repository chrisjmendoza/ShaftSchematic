# Component vs Feature Contract
Version: v0.5.x
Last updated: 2026-08-13 — the auto-body card's Ø field is **per-section**
(`AutoDiaOverride`), not one shaft-wide value; still no field-edit promotion path.
2026-08-05 — the "not components — envelope records" bullet now lists all
reference-only kinds, adding **worn sections** (`WearRecord.wornSections`) and **undercuts**
(`UndercutRecord`, its own `undercut_record` envelope field). 2026-07-21 — reverted "explicit bodies are non-negotiable" (it raised false collision warnings on normal drafts); bodies are the fluid base again — they don't collide, and plain bodies split around sacred components while keyed bodies are protected.

This document is **normative**.
It defines what constitutes a **component** versus a **feature** in ShaftSchematic.

---

## 1) Overview

### Component
A **component** is an axial, ordered entity that occupies a span along the shaft’s X axis.
Components:
- have `startFromAftMm` and `lengthMm`
- participate in ordering and layout decisions
- are stored as first-class lists in `ShaftSpec`

### Feature
A **feature** is a cut/annotation owned by a **host component**.
Features:
- cannot exist independently
- have no standalone axial ordering
- are validated and rendered relative to their host component

---

## 2) Component Rules

- Components occupy an axial span: `[startFromAftMm, startFromAftMm + lengthMm]`.
- Components participate in:
  - ordering and snapping (measurement-space, mm)
  - overlap checks (warnings only unless a rule explicitly blocks)
  - naming/identification (stable `id`)
  - export (PDF consumes component lists)

### Current components
- **Body**: constant-diameter span.
  - Can be adjacent to other components; diameter discontinuities are allowed (may warn).

- **Taper**: linear transition span between two diameters.
  - Taper parameter derivation/normalization occurs in the ViewModel; renderer draws from diameters.

- **Liner**: sleeve/bearing span.
  - Describes OD over a span; may overlap other components (typically allowed; may warn).

- **Threads**: threaded span.
  - Intended for shaft-end threading; enforcement of “end-only” constraints is a ViewModel/validation rule.

- **Coupler Bolt Slot**: reference overlay for muff-coupling bolt cutouts.
  - A first-class list in `ShaftSpec` (`couplerBoltSlots`) and implements `Segment`, but it is a **pure reference marker**, not a geometric span.
  - Draws a row of `count` cutouts (pitch `spacingMm`, diameter `holeDiaMm`) straddling the shaft outline; `through`/`depthMm` distinguish through vs blind.
  - Excluded from OAL/`coverageEndMm`, excluded from collision detection (`collisionGroup()` → null), and never splits or merges bodies.
  - Position authored from AFT/FWD (default FWD); `showDimensionRail` is deferred (no rail drawn in v1). See DATA_MODEL.md for the full field list.

- **Not components — envelope records**: wear spots, wear pits, measured-Ø readings, worn
  sections, runout readings, and undercuts are reference-only *inspection records* stored in
  the document envelope (`WearRecord` / `RunoutReadings` / `UndercutRecord`), not in
  `ShaftSpec`, and are outside this contract — they never resolve, collide, or occupy spans.
  - **Worn sections** (`WearRecord.wornSections`) and **undercuts**
    (`UndercutRecord.undercuts`, its own `undercut_record` envelope field) are additionally
    **shaft-space**, not component-keyed: a span may cross component edges, so they have no
    orphans and are never pruned at decode. Their authored Distance reference is
    display-only metadata — canonical geometry never moves on a reference switch.
  - Neither has a carousel card or an Add dialog — they are authored only on their own tab
    (Consolidated Output / Undercut Drawing), which keeps them outside the
    add-dialog-parity invariant.
  - See DATA_MODEL.md §Serialization, `CLAUDE.md`,
    `docs/contracts/RunoutSheet.md` /
    `docs/contracts/UndercutDrawing.md`.

---

## 2.1 Implicit Body Spans (Derived)

- Not components; they are derived gaps between components.
- Computed deterministically from resolved geometry and never stored in `ShaftSpec`.
- Fill axial regions not occupied by explicit components.
- Split/retreat automatically when explicit components are added.
- Never overlap explicit components.
- Do not participate in snapping.
- Must never define measurement references.
- Must never be persisted.
- May be promoted to explicit Body components when editing is required.

### Auto vs Manual Bodies (Important Distinction)

- **Auto bodies** (derived):
  - Ephemeral and read-only
  - Generated from OAL + explicit components (`ui/resolved/ResolvedComponent.kt`,
    `deriveAutoBodies()`), tagged `ResolvedComponentSource.AUTO`
  - Removed or split as explicit components occupy their span
- **Manual bodies** (explicit, current):
  - Persisted components stored in `ShaftSpec.bodies`
  - Replace auto bodies in overlapping regions
  - Never coexist with auto bodies in the same region

**Rule:** Manual body components promote over auto bodies in any overlapping span. This
promotion is live today: the carousel's auto-body card promotes ONLY when the user ticks its
**"Explicit body"** checkbox (`BodyPagerCard.kt` calls `onAddBody(...)`), persisting the
section as a real `Body` in `ShaftSpec`. There is no field-edit promotion path — the card's
editable Ø writes a per-section `AutoDiaOverride` without promoting; viewing the card never
promotes it.

**Per-section Ø:** the Ø field applies to **that one auto span**
(`onSetAutoSectionDia(startMm, endMm, diaMm)` → `ShaftViewModel.setAutoSectionDiaMm` →
`ShaftSpec.withAutoSectionDia`), so bare-shaft sections may differ without any of them
becoming explicit. The override is keyed in shaft space by an anchor at the span midpoint,
beats the legacy shaft-wide `ShaftSpec.autoBodyDiaMm` and neighbor derivation, and changes the
drawn diameter only — span boundaries stay derived. Anchors under a component (or inside a gap
absorbed into an explicit-body run) are dormant, never pruned, and resurrect when their span
does. When a separator is deleted and two differing sections remerge, the joined run takes the
**more aftward** section's Ø — aft is authored first. See DATA_MODEL.md §`AutoDiaOverride`.

### Two explicit bodies never fuse

`normalizeBodies` (resolve) merges contiguous body spans into one drawn run so a bare-shaft gap
flows into the explicit body beside it and inherits its Ø. That merge stops at an **explicit**
body: one never gets absorbed into a run that already carries an explicit body.

Without that stop, a stepped shaft built from two abutting bodies resolved to a **single run at
the aft-most diameter**, with one carousel card instead of two — the second body's Ø and card
silently discarded. It hides well, because any taper/thread/liner between the bodies flushes the
accumulator, and on a marine shaft there usually is one; a shaft that is simply Ø6 stepping up to
Ø8 is what exposes it. Auto spans still merge into an explicit body for continuity — that is what
the pass is for.

### A body face can be blended

`Body.blendAftMm` / `blendFwdMm` / `blendProfile` cut a smooth machined transition INWARD from a
face, out of the body that carries it: the curve leaves the neighbouring diameter at the face and
reaches the body's own Ø that far in. **No other component's span moves — drawn or stored** — so
the golden rule holds by construction and two neighbouring blends can never contend for the same
material.

- Diameters are **derived** from whatever sits across the face, never typed. Re-diametering a
  neighbour re-curves the blend; derived values are exactly what may move.
- Nothing across the face, or a neighbour at the same Ø → **no blend drawn** (not an error).
- Liners are excluded from the ordinary neighbour lookup — a sleeve over mid-body is not a
  diameter the shaft steps to. The exception is a face a liner **butts directly against**, a real
  seal area: the shaft IS cut down under the liner, but that seat is covered and never drawn, and
  its depth varies job to job. The curve there leaves from the **midpoint of the liner OD and the
  body Ø** (`seatDiaUnderLiner`) — a derived visual cue, not a measurement, so nothing authors it.
  Stepping straight to the liner OD would overstate the shoulder; running to a seat nobody entered
  would be a made-up number. A seat authored as its own body under the liner is **not** consulted:
  `subtractBodiesAgainstNonBodies` trims a fully covered body out of the drawing, so there is
  nothing on the sheet for the curve to arrive at.
- A face may carry a **seal area** — `Body.blendAftSeal`/`blendFwdSeal` (or `AutoBlend.seal`) —
  drawing `SEAL_GROOVE_COUNT` (3) cuts across the curve for the fiberglass to seat into. Each cut
  is a V notch in both silhouette edges plus a DASHED line across seated on the notch floors —
  never a solid full-height line, which is the glyph for a component face and made the shaft read
  as segments (on-device report); the dash is finer than the hidden-keyway pattern on purpose (`sealNotchGeom` sizes the notch;
  line and notch derive from the same geometry so they meet exactly). Authored as the third state of a face's
  finish chips (Square | Blend | Seal area) — exclusive as presented, but a seal area INCLUDES its
  blend, since the cuts are machined into the blended section. A fixed count, since the drawing is a cue rather
  than something to machine from.
- Silhouette only — no dimension rail, no footer row, no effect on OAL, coverage, or collision.
- Draw sites: the schematic canvas (`ShaftRenderer`), the schematic PDF (`ShaftPdfComposer`),
  and the runout/consolidated sheet (`RunoutPdfComposer.drawBodiesForRunout`) all decompose the
  same `bodyDrawEdges`, so all three print the same curve. On the compressed sheets the S-break
  pair is cut into the FLAT span — the curves at the faces stay whole — and the break decision
  stays on the run's full drawn width (a blend is a face detail, not a reason to read as more or
  less compressed). The **wear document deliberately keeps square faces**: it omits machining
  detail by product decision, the same posture as its keyway omission.
- The face is the run's **DRAWN outer edge**, not the stored position. A bare-shaft gap absorbed
  into a body's run moves that edge outward, and the drawn step moves with it; matching the stored
  value dropped the blend whenever a neighbour shortened.
- Explicit bodies store their blends as fields; **auto spans anchor them in shaft space**
  (`AutoBlend` / `ShaftSpec.autoBlends`, the `AutoDiaOverride` posture — anchor at the span
  midpoint, aft-most wins per face, dormant under a component, never pruned). A saved template
  therefore keeps its seal areas when liners or the overall length move under it. Both kinds
  resolve to the same `BodyBlend`.
- An anchor only applies where the auto span survives as its **own AUTO run** — bounded by
  non-bodies, e.g. bare shaft between two liners. A gap flanked by an explicit body is absorbed
  into that body's run, where the body's own blend already covers the face.
- A split body blends on the run holding the **stored** face, never an interior fragment edge.

Geometry: `ui/resolved/BodyBlends.kt`; curve math `geom/BlendProfileMath.kt` (a general
join-two-radii primitive, shared with the queued liner-shoulder fillet and undercut end radius).

### Explicit bodies are the fluid base (reverted 2026-07-21)

An explicit (stored) body is the shaft's base material / filler — **not** a rigid
collider. The "explicit bodies are non-negotiable" experiment was reverted because it
raised false collision warnings on normal drafts.

- A body does **not** participate in collision detection. `collidingIds()` checks only
  taper/thread/liner pairs (sacred-vs-sacred), never bodies. A body legitimately runs
  UNDER a liner (a sleeve over the shaft) and UP AGAINST a taper.
- There is **no** hard-block on adding or moving a sacred component over a body. Adding a
  taper/thread/liner over a plain body **splits** the body (`splitBodiesAround`) as it
  always did. The `bodyOverlapErrorMm` / `nonBodyOverlapErrorMm` helpers and the
  liner↔body "boundary negotiation" (`linerBodyBoundaryAdjust` /
  `updateLinerWithBodyBoundary`) no longer exist.
- The resolve layer (`subtractBodiesAgainstNonBodies`) trims the *drawn* body around
  overlapping components, so a *stored* body span crossing a liner/taper is not a conflict.

**Light protection (kept):** a body that HAS A KEYWAY is never split — it stays one whole
card (keyway intact) and the resolve layer still trims it for drawing. Plain (unkeyed)
bodies split as before.

**Engine guard (kept):** on delete, `mergeBodiesAround` refuses to merge two flanking
bodies across a component that still occupies the freed span, preventing a long phantom body.

---

## 3) Feature Rules

- Features are attached to a **host component**.
- Features have no independent axial ordering; they do not participate as independent segments.
- Features are validated and rendered relative to their host.
- Features are emitted in PDF/export as annotations of the host component (not as independent items).

---

## 4) Keyway Feature

- Keyways are **features**, not components.
- Supported on **Tapers** (taper-hosted) and **Bodies** (body-hosted). Body keyways were
  un-shelved 2026-07-20: intermediate shafts with fitted couplings carry keyways in plain
  cylindrical bodies at the shaft ends (the shaft can simply end on a body).
- Keyways will **never** exist as standalone components.
- At most **one keyway per host** component; a shaft has as many keyways as it has
  keyway-bearing hosts.

Keyway attributes (host-owned, `model/Taper.kt` / `model/Body.kt`):
- `length` (stored as `keywayLengthMm`)
- `width` (stored as `keywayWidthMm`)
- `depth` (stored as `keywayDepthMm`)
- reference offset:
  - Taper: `keywayOffsetFromSetMm` — measured from the SET face
  - Body: `keywayOffsetFromEndMm` — measured from the referenced end face, with
    `keywayEnd` (AFT | FWD) selecting which face
  - In both: 0 = open keyway at the referenced face, > 0 = floating keyway inset from it
- `spoon flag` (stored as `keywaySpooned` — there is no `keywayHasSpoon` alias)

Body keyways survive body split/merge by **absolute position**: `carryBodyKeyway`
(`model/ShaftSpecExtensions.kt`) re-anchors the offset to the surviving fragment's face,
and drops the keyway if a cut passes through it.

### Keyway clocking — 180° / 90° apart

`ShaftSpec.keyways180Apart` states the shaft's keyways are clocked 180° from each other.
`ShaftSpec.keyways90Apart` (+ `keyways90Cw`) states they are clocked 90° apart instead,
with the direction measured **from the AFT keyway, viewed from aft** (CW/CCW). The two
flags are **mutually exclusive** — `ShaftViewModel.setKeyways180Apart(true)` clears
`keyways90Apart` and vice versa; the UI never enforces this itself, it only reflects
whichever flag the ViewModel currently reports. Both are only meaningful when
`spec.keywayCount() >= 2`; UI surfaces the toggles (and, for 90°, the CW/CCW chips) only
then, and the PDF prints the matching footer note under the same condition.

**180° hidden-line rendering.** When `keyways180Apart` is set, the keyway nearest the AFT
face — the shop's measurement datum — stays **solid** (near side); every other keyway is
drawn as a **hidden feature**: dashed outline (`HIDDEN_DASH_ON`/`HIDDEN_DASH_OFF` =
6/4 px) with **no** white void fill, since the near surface is unbroken in a plan view.
`ShaftSpec.hiddenKeywayHostIds()` is the single source of this classification — it returns
the host IDs to draw hidden, and both `ShaftRenderer` (preview) and `ShaftPdfComposer`
(export) consume it, so the two surfaces never diverge. This is the standard drafting
convention for a feature on the far side of the part; the footer note stays as well.

**90° notch rendering.** A 90° secondary keyway is neither near-side (solid) nor far-side
(hidden/dashed) — it is drawn on an **edge** of the silhouette: a depth-deep notch cut into
the profile outline itself, not a dashed reference line. Which edge is derived from the
same aft-view clock convention as runout high-spot markers (12 o'clock = up, increasing
clockwise): the AFT keyway is always drawn facing the page viewer (solid, near side), which
— because the drawing runs AFT-at-page-left / FWD-at-page-right — puts it at an
aft-observer's **3 o'clock**. Rotating **CW** from that reference lands 90° further around
at **6 o'clock**, the profile's **bottom edge**; rotating **CCW** lands at **12 o'clock**,
the profile's **top edge**. So: `keyways90Cw = true` → notch on the bottom edge;
`keyways90Cw = false` (CCW) → notch on the top edge. The spoon bowl (see "Spooned keyways")
is a face-view construct: it draws on face-on slots (the primary, or a 180° hidden slot,
dashed) but has no edge-on projection, so a spooned 90° secondary draws as a plain notch.

---

## 5) Ownership Boundaries

- **ViewModel owns**:
  - validation
  - normalization/derivation
  - enforcement of component/feature rules (including what constitutes an “existing” feature)

- **Renderer owns**:
  - visualization only
  - must not infer geometry or reinterpret feature intent

- **PDF export owns**:
  - consuming validated data
  - emitting output derived from component and feature fields
  - no re-validation and no reinterpretation of rules
