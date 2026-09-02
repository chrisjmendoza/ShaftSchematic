# UI Contract
Version: v0.5.x
Last updated: 2026-08-05 — the editor now has **five** tabs: §7.6 (Undercut Drawing) and
§7.7 (Consolidated Output) added, §7.5 notes the `WEAR_TAB_ENABLED` retirement switch, and
§5.2 points at the app-theme / sheet-ink contract. 2026-07-28 — added §7.5 pointing the Runout Sheet / Wear Document tab
interactions (incl. the wear overlay's Add X / Remove X / Add Ø tools) at the in-source
`RunoutSheet.md` contract. 2026-07-21 — §3.1.4 corrected after the "non-negotiable bodies" revert: bodies are fluid fillers (no collision, plain bodies split around sacred components, keyed bodies protected); removed `bodyOverlapErrorMm`/liner↔body-negotiation references. 2026-07-18 — §5.2 "Planned Preview Tap + Implicit Bodies" documented as shipped and merged into §3.1.1 (also fixes broken section numbering, a 5.2 appearing before 5.1); §§3.2–3.6 trimmed to summaries pointing at the more current in-source `AddComponentDialogs.md`.

## Purpose
This document defines all UI interaction rules, screen behaviors, dialog behavior, input handling, and UI–ViewModel boundaries.  
The UI is responsible for **presenting** data, not **interpreting** or **computing** it.

---

# 1. Responsibilities

## UI Layer **Does**
- Display current state (`StateFlow` → Compose)
- Provide user input controls
- Maintain local editing buffers (e.g., text fields)
- Commit values only when editing is complete
- Render grid + axis labels (but not geometry)
- Show validation results (blocking / warnings)

## UI Layer **Does NOT**
- Perform mm→px math
- Read or compute geometry values
- Derive taper rates, pitches, or diameters
- Validate component rules beyond basic formatting
- Render bodies/tapers/threads/liners
- Modify model objects directly

Only the ViewModel may change the `ShaftSpec`.

---

# 2. Input Fields (NumberField)

### 2.1 Commit Timing

Two commit strategies are used depending on context:

**Add dialog fields (`CommitNumField`)** — commit on **every keystroke** (and again on blur for safety).
This is mandatory because the user may tap the "Add" / "Submit" button while a field is still focused, before a blur event fires. Committing on keystroke ensures the value is always captured regardless of focus order.
- `LaunchedEffect(initial)` detects external resets (e.g., dialog re-open) without causing cursor-jump on normal typing.
- `onFocusChanged` also commits on blur as a belt-and-suspenders safety net.

**Carousel edit fields** — commit on **blur / Done** only.
- Avoids recomposition jitter during typing.
- User can type partial numbers freely.

In both cases `parse text → float?` and `VM.update*(parsed)` are called only with valid values; invalid input reverts to the last committed text.

### 2.2 Tap-to-Clear(0)
When the committed value is exactly `0f`, tapping the field clears it.

When value ≠ 0:
- Tap does **not** clear
- Cursor appears at end of text

### 2.3 Allowed Input Styles
- `"123"`
- `"123."`
- `".5"`
- `""` → interpreted as 0 on commit  
- Non-numeric → ignored and field resets to last valid text

ViewModel must handle empty/invalid numeric commits safely.

---

# 3. Dialog Contracts

### 3.1 Common Rules
All Add/Edit dialogs follow the same conventions:

- Local state holds raw user input
- Committing resolves fields into a validated component
- Dialog stays open on **blocking validation error**
- Dialog closes only when:
  - Update succeeds, and  
  - ViewModel applies new spec

### 3.1.1 Add Entry Points & Implicit (Auto) Bodies

There is ONE path to open an add dialog: the **FAB chooser**
(`InlineAddChooserDialog`), which computes the default start via `computeAddDefaults()`
(see §3.1.2), sets `addStartMm`/`addLengthMm`, and opens the matching `add*Open` dialog state.

It goes through the full dialog — there is **no quick-add bypass** that skips user input.

**Tap-to-add is removed.** Tapping a gap in the preview canvas used to snap the tapped position
and open the same chooser there. It fired unintentionally far more often than it was wanted and
was never used deliberately (on-device report), so the canvas tap is now **selection only** — a
tap on a component highlights it, and a tap on bare canvas does nothing. Removing it retired the
whole snap pipeline with it (`ui/viewmodel/SnapUtils.kt`, `ShaftViewModel.snapRawPositionMm` /
`gapToNextAnchorMm` / the pending-position state): nothing else ever snapped a position. Do not
reintroduce a bare-canvas tap action without asking — the complaint was about the gesture
existing at all, not about how it behaved.

**Implicit (auto) bodies** — shipped, not planned. Derived, read-only gap-fillers; never
persisted in `ShaftSpec`:
- Computed by `ui/resolved/ResolvedComponent.kt`: `resolveComponents()` calls
  `deriveAutoBodies()`, producing `ResolvedBody(source = ResolvedComponentSource.AUTO)` entries.
- Fill axial gaps between explicit (sacred) components. When OAL is manually authored, a base
  auto body spans 0 → OAL immediately; derived OAL does **not** seed a base auto body.
- Promotion to an explicit `Body` happens ONLY by ticking the **"Explicit body"** checkbox
  (`BodyPagerCard.kt` calls `onAddBody(...)` with the derived span). There is no
  field-edit promotion path: the auto-body card's Start/Length are disabled derived fields,
  and its editable Ø field sets the shared bare-shaft `ShaftSpec.autoBodyDiaMm` without
  promoting. Viewing the card never promotes it (see the "Auto-body promotion" invariant
  in `CLAUDE.md`).

### 3.1.2 Default Start Position (`computeAddDefaults`)

The default start for a new component is the **furthest FWD end** among sacred components only:
- All tapers
- All liners
- Threads with `excludeFromOAL = false`

Bodies are **excluded** from this calculation (the default targets the FWD-most sacred edge, and auto-bodies fill from there). Excluded threads sit outside the shaft envelope and are **excluded**. Coupler bolt slots are pure reference overlays and are likewise **excluded**. This ensures new components always default to the next logical open slot in the shaft, not past the end.

### 3.1.3 Auto-Selection After Add

When any `add*At` function completes in the ViewModel, `selectedComponentId` is set to the newly added component's ID. The carousel auto-scrolls to and highlights the new component.

### 3.1.4 Explicit vs auto bodies in the Carousel

Bodies are independent spec entities; the carousel shows **one card per stored body**. Both
stored (explicit) and derived (auto) bodies are fluid base material / fillers. A sacred
component added or moved over a plain body **splits** it (`splitBodiesAround`) — there is no
hard-block, and bodies never raise collision warnings. A body that has a keyway is never split:
it stays one whole card (keyway intact) and the resolve layer trims it for drawing. Auto-bodies
(derived, unstored) get "Body (auto)" cards and are promoted to explicit only via the
"Explicit body" checkbox (see §3.1.1 — no field-edit promotion path).

(The "explicit bodies are non-negotiable" experiment was reverted 2026-07-21 — it raised false
collision warnings on normal drafts. The `bodyOverlapErrorMm` / `nonBodyOverlapErrorMm` hard
blocks and the liner↔body boundary negotiation `linerBodyBoundaryAdjust` /
`updateLinerWithBodyBoundary` no longer exist.)

On delete, flanking bodies merge back — `max(left.diaMm, right.diaMm)` — but `mergeBodiesAround`
refuses to merge across a component still occupying the freed span (phantom-body guard).

**Two explicit bodies never fuse in resolve.** `normalizeBodies` merges contiguous body spans so
a bare-shaft gap flows into the explicit body beside it, but it stops at an explicit body — one is
never absorbed into a run that already carries one. Without that stop a Ø6 body butted to a Ø8 body
resolved to a single run at the aft-most Ø with **one card instead of two**. See
`docs/COMPONENT_CONTRACT.md`.

**Face finish section.** Below "Show Ø on drawing", one chip row per face —
**AFT / FWD: Square | Blend | Seal area** — with an axial length field under any face that is not
Square, and shape chips (S-curve | Fillet | Eased cone) once some face is finished.

The three modes are mutually exclusive *as presented*, which is a deliberate small fiction:
**Seal area includes the blend**, because the radius cuts are machined across the blended section
and there is nowhere to put them on a square face. It is presented that way because the shop
describes a face as square, blended, or a seal area — a seal area is a thing you add, not a
modifier on something else. The earlier nesting (a Blend checkbox revealing a Seal area checkbox)
hid the seal behind a control nobody would think to tick first. Storage is unaffected: the model
still carries a length and a flag independently, and `blendFaceMode`/`blendLenForMode`
(`ComponentCarousel.kt`, unit-tested) are the only projection. Switching Blend ↔ Seal area keeps
the typed length, and **Square is the off switch** — it clears the length and the seal flag,
leaving the other face untouched.

Two rules make the rows read correctly, both learned on device:
- **Every chip keeps a visible outline, selected or not.** M3's default filter chip gives an
  unselected chip a transparent container and no border, which rendered "Square" and "Blend" as
  plain words beside the one chip that looked like a control — so the way to turn a face off did
  not look tappable ("I can't disable the blend or seal area"). Do not restore `border = null`.
- **Label above the row, equal-weight chips.** A leading label gutter plus each chip's ~32 dp of
  internal padding left barely 50 dp for text, so even "Square" ellipsized to "Squ…". The label
  now sits above its chips, handing the row its full width (~40% more per chip), and the chips
  split that width equally so the longest label in a row sizes all of them. Chip text is
  `labelMedium` — a step down from the chip default — for headroom at large font scales. The
  gutter also caused the original symptom: "FWD" renders wider than "AFT", enough on its own to
  wrap that row's last chip while the AFT row fit. It changes drawn geometry, so it is
under the add-dialog-parity rule — `AddBodyDialog` shows the same section, and both surfaces
render one shared composable (`ui/screen/BlendSection.kt`) so they cannot drift. The auto-body card
carries the same section, writing a shaft-space `AutoBlend` anchor instead of a stored field. It
is deliberately NOT gated behind promotion: promoting pins a span's start and length, so a blend
authored against a promoted body's boundary is stranded when a template's liners or overall length
later move, while an anchored one follows the span that re-derives around it. (The keyway section
stays promotion-only — a keyway is real geometry needing a stable host, a blend is draw-only.)

### 3.1.5 Direction Chip (AFT / FWD Toggle)

Add dialogs that expose a direction toggle (Liner, Taper, Coupler Bolt Slot) use a custom `DirectionChip` composable:

- **Selected state**: 2 dp primary-color border, `primaryContainer` background.
- **Unselected state**: no border, `surface` background.

The border (not fill) is the selection indicator. An outlined unselected chip would visually compete with the selected chip; the borderless unselected state keeps the hierarchy clear.

### 3.2–3.6 Per-Dialog Contracts

The authoritative, current per-dialog field contract lives in
`docs/contracts/AddComponentDialogs.md` (covers
`AddBodyDialog`, `AddLinerDialog`, `AddThreadDialog`, `AddTaperDialog`,
`AddCouplerBoltSlotDialog`) — it is kept up to date with feature work (e.g. the taper
Auto/Manual rate-mode system) faster than this document. Consult it first; the notes below
only capture what it does **not** state.

**3.2 Taper Dialog** — See `AddTaperDialog` there for fields and the Auto/Manual taper-rate
rules (these superseded the older "SET & LET both given → taperRate ignored" wording that used
to live here). Not covered there: the UI never derives geometry itself — it assembles
Length/SET/LET/rate text and submits it; all derivation happens in the ViewModel.

**3.3 Thread Dialog** — See `AddThreadDialog` there for fields. Not covered there: the dialog
also surfaces `pitchMm` alongside TPI; UI must never compute pitch↔TPI conversion — the
ViewModel handles it (`Threads.normalized()`).

**3.4 Liner Dialog** — See `AddLinerDialog` there for fields. Not covered there: any derived mm
value the dialog shows is ViewModel-computed; UI cannot calculate mm values itself.

**3.5 Liner Authored Reference (AFT/FWD)** — not restated in the contracts pack; kept here as
the canonical statement:
- Liners separate authored reference from physical geometry.
- UI must project authored “Start” based on selected reference.
- Switching AFT/FWD must **not** mutate physical geometry.
- ViewModel stores reference metadata; geometry remains canonical.

**3.6 Coupler Bolt Slot Dialog** — See `AddCouplerBoltSlotDialog` there for the full field
table, FWD-reference math, and Do-Nots (including the card-only "show dimension rail" toggle
parity note). Not covered there: the dialog also carries a **Label** field (free text,
carousel-display only). As a reference overlay, adding/editing/removing a slot never
splits/merges bodies, never changes OAL, and never triggers collision warnings.

---

# 4. Component List (Ordering)

### 4.1 What ordering means
The component list reflects **spatial order** (AFT → FWD) derived from resolved geometry.
Insertion order must never determine display ordering.

### 4.2 Reordering
There is no stored display order to reorder: the ViewModel keeps none, and rows are derived
from the resolved components (`docs/contracts/ComponentsOrdering.md` v1.3). A reordering UI would have
to introduce that state deliberately — UI emits an intent, the VM owns the list, and NO
geometry recalculation happens in the UI layer. Spatial order stays authoritative.

---

# 5. Canvas Rendering Bridge

UI element `ShaftDrawing` is responsible only for:
- Drawing grid
- Drawing axis labels
- Building `renderOptions` from UI + user preferences (grid toggle, preview colors, black/white override)
- Passing `layoutResult` and `renderOptions` to `ShaftRenderer`

UI must never:
- Draw geometry
- Compute pxPerMm
- Compute component boundaries

---

# 5.1 Preview Color Preferences (Settings)

Preview color preferences apply to the on-screen Preview only.

- Users select from presets: Stainless, Steel, Bronze, Transparent, or Custom.
- When Custom is selected, a theme-based palette is available.
- A “Black/White Only” toggle forces black outlines and disables fills in Preview.
- When Black/White Only is enabled, color controls are disabled but retain their last selections.

---

# 5.2 App Theme (Settings → Appearance)

The app theme (System / Light / Dark + High contrast, persisted by
`settings/AppearancePrefs.kt`) styles **Compose chrome only**. The white-sheet document
canvases (undercut overview/detail, wear overview/detail, runout preview) draw with fixed
ink from `ui/theme/SheetInk.kt` and must **never** read `MaterialTheme.colorScheme` — dark
theme's near-white `onSurface` would print invisible ink on a white sheet. The undercut
sheets' fills are additionally user-styled via `util/UndercutStyle.kt` (still fixed inks,
never theme roles, and never leaking into the PDF composers).

Authoritative contract:
`docs/contracts/Appearance.md`.

---

# 5.3 Units (Settings → Drawing)

Two independent switches, both **off by default** so a document that never touches them
prints exactly as it did before they existed.

- **Per-component units** — a capability gate. On, every explicit Body / Taper / Thread /
  Liner card in the carousel grows a `Prints in: in | mm` chip; off, no chip appears
  anywhere and existing overrides simply lie dormant in the document (they are never
  pruned). The auto-body and coupler-bolt-slot cards carry no chip. The chip is **card-only**
  — a deliberate carve-out from the Add-dialog-parity invariant, reasoned in
  `docs/contracts/AddComponentDialogs.md`.
- **Dual-unit display** — the global **default** for new documents. Each document persists
  its own `dual_units` value, and the sheet-wide toggle on the document writes this default
  back so the two stay in step.
- **Dual units** — the DOCUMENT's own flag, so it also appears on every PDF options sheet
  (schematic, runout, consolidated, wear, undercut), not only here. The Settings switch is the
  DEFAULT for new documents; the sheet switch changes the document in hand. The layout chips below
  it are disabled while it is off — display-only, and never rewriting the stored layout.
- **Dual-unit layout** (`PdfPrefs.dualUnitLayout`; also on every PDF options sheet) — how the two
  terms are SET once a document has them: **Inline** (`1 1/2" [38.1 mm]`, the default) or
  **Stacked** (primary over secondary). A stack is about 55% narrower, so more values sit inside
  the dimension line instead of above it; it is one line taller, and a sheet whose vertical budget
  cannot absorb that reverts to Inline for the WHOLE sheet. Inert on a single-unit document.

Both are display-axis controls: geometry stays canonical mm, and neither ever rewrites a
value the user typed (golden rule). Numeric **entry** fields keep taking the document unit
regardless of a component's print override — see `docs/contracts/ShaftScreen.md`.

Authoritative contracts: `docs/DATA_MODEL.md` (storage),
`docs/contracts/AddComponentDialogs.md` (surface parity), `docs/PDF_EXPORT.md` §4 (printing).

---

# 6. Validation Feedback

### 6.1 Blocking Errors
- Highlight field in red
- Disable Save / Confirm
- Tooltip-style explanation permitted

### 6.2 Non-Blocking Warnings
- Yellow warning icon in component list
- User may save/export regardless

### 6.3 Full-Spec Validation
When exporting or saving, ViewModel runs full validation and sends:
- Success event OR  
- Error message: UI shows snackbar/dialog

---

# 7. Events & StateFlow Integration

UI listens to:
- spec: StateFlow<ShaftSpec>
- order: StateFlow<List<ComponentKey>>
- validation warnings: SharedFlow
- snackbar messages: SharedFlow

UI emits only:
- Intents (add, edit, delete, reorder)
- Numeric field commit events
- Dialog open/close events

No other responsibilities.

---

# 7.5 Runout Sheet & Wear Document Tabs

This contract predates the sidebar's document tabs (`EditorTab` — Schematic, Runout Sheet,
Wear Document, Undercut Drawing, Consolidated Output); their UI behavior is owned by the
`docs/contracts/RunoutSheet.md` (authoritative)
rather than duplicated here. Summary of the boundaries, which follow the same rules as above:

- **RunoutRoute** — station-count overrides, TIR orientation, tap-a-bubble editor
  (`RunoutBubbleDialog`: TIR value + high-spot clock marker). All bubble placement comes
  from the shared `geom/RunoutBubbleLayout.kt` engine; the route never computes placement.
- **WearRoute** — tappable overview canvas (component tint + wear-count badges) opening
  `ComponentWearDetailOverlay`: a **Pits** section (tool chips **Add X** / **Remove X**)
  and a separate **Diameter measurements** section (**Add Ø** / **Remove Ø**; add/edit via
  a Save/Cancel/Delete value dialog — readings are created only on Save). One canvas tool
  is active at a time across both sections. Liner cards keep the wear-spot editor
  (`NumberField.md` commit rules apply).
- Both tabs preview by **rasterizing the real composed PDF** (`PdfPreviewOverlay`) — the
  UI never re-draws document geometry itself, and all hit-testing/placement math lives in
  `geom/` (`WearPitMath`, `WearDiaMath`, `WearDiaCalloutLayout`, `RunoutReadingMath`).
- The Wear tab is the **authoring surface** for wear data; the single flag
  `WEAR_TAB_ENABLED` (`ui/screen/EditorTab.kt`, currently `true`) hides the tab in one line
  when a future full consolidation retires it, without touching the wear code paths.
- **Output actions** — every document tab (these two, Undercut, and Consolidated Output)
  renders the ONE shared trio `ui/screen/DocumentActionButtons.kt`: a filled
  **Print &lt;doc&gt;** leading, then outlined **Preview &lt;doc&gt;** and **Export &lt;doc&gt; PDF**.
  Print is the primary output action (DESIGN_INTENT §3.4) — the shop prints from the
  device and a PDF file is the backup copy. All three share the route's export gate; the
  gate's disabled message stays with the route. The preview chrome mirrors the weighting:
  a labelled Print button with an Export icon beside it. See `docs/PDF_EXPORT.md` §5.5.

---

# 7.6 Undercut Drawing Tab

`EditorTab.UNDERCUT` / `ui/screen/UndercutRoute.kt`. Authoring surface for undercut
sections; behavior owned by
`docs/contracts/UndercutDrawing.md` (authoritative).
Boundary summary:

- Undercuts are authored **only here** — no carousel card and no Add dialog, so they sit
  outside the add-dialog-parity invariant. The tab offers a list-row editor alongside the
  canvas (the list rows are also the TalkBack-accessible path).
- The Distance field is authored against one of four references (AFT/FWD SET or a reference
  liner's AFT/FWD edge). Switching the reference is **display-only** — the UI never moves
  canonical shaft-space geometry (`geom/UndercutMath.kt` owns the conversion pair).
- Drawn notch depth is display-exaggerated by the per-sheet "Cut depth exaggeration" slider
  (`UndercutRecord.exaggerationFrac`); the UI reads the exaggeration, it never computes the
  normalization (`geom/SurfaceProfileMath.kt` + `geom/UndercutMath.kt` do).
- Canvas and PDF share one pure pipeline; the route performs no clustering, clamping, or
  hit-test math of its own.

---

# 7.7 Consolidated Output Tab

`EditorTab.OUTPUT` / `ui/screen/OutputRoute.kt`. The one-stop surface for the consolidated
sheet; behavior owned by
`docs/contracts/RunoutSheet.md` (Consolidation step 5),
with the export/paper rules in `docs/PDF_EXPORT.md` §5.6–5.7. Boundary summary:

- **Content election** (`ConsolidatedVariant`): All three (default) | Schematic + Runout |
  Schematic + Wear. Session-only state; it selects what the composer draws, it does not
  change stored data.
- **Worn-section editor** — the authoring surface for `WearRecord.wornSections`
  (reference-only, shaft-space spans). Layout is pure math in `geom/WornSectionMath.kt`;
  the route never places values itself.
- **"Shaft height" slider** (`RunoutConfig.heightScale`, per-job) and the **liner
  compression** control — the same per-job values the schematic preview's Tune sheet
  exposes (`ShaftHeightSlider` / `LinerCompressionControl`); both commit through the
  ViewModel, and all scale solving is pure (`geom/ProfileCompression.kt`).
- **Blank-draft toggle** and **Export all** — checkboxes for the five documents written to
  one picked folder. Every export goes through `util/PdfSafExport.writeShaftPdfToUri` and
  the shared collision export gate; the UI presents the written/failed result only.
- Like the other document tabs, the preview **rasterizes the real composed PDF** — no
  parallel UI draw path.

---

# 8. Summary

The UI:
- Holds temporary editing buffers
- Commits to ViewModel only on deliberate action
- Performs no geometry or validation logic
- Reactively displays ViewModel state
- Delegates *all* computations outward

UI is a pure presentation layer with strict boundaries.