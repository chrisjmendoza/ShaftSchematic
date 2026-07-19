# UI Contract
Version: v0.5.x
Last updated: 2026-07-18 — §5.2 "Planned Preview Tap + Implicit Bodies" documented as shipped and merged into §3.1.1 (also fixes broken section numbering, a 5.2 appearing before 5.1); §§3.2–3.6 trimmed to summaries pointing at the more current in-source `AddComponentDialogs.md`.

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

There are two paths to open an add dialog:

1. **Tap-to-add** (tap on canvas gap): sets `tapAddStartMm` and `tapAddGapMm` from the tapped
   position (`ShaftScreen.kt`, tap-to-add state ~lines 299-303, dialog wiring ~lines 768-861),
   then opens the appropriate `tapAdd*Open` dialog state.
2. **FAB chooser** (`InlineAddChooserDialog`): computes default start via `computeAddDefaults()` (see §3.1.2), sets the same state vars, then opens the same dialogs.

Both paths go through the full dialog — there is **no quick-add bypass** that skips user input.

**Implicit (auto) bodies** — shipped, not planned. Derived, read-only gap-fillers; never
persisted in `ShaftSpec`:
- Computed by `ui/resolved/ResolvedComponent.kt`: `resolveComponents()` calls
  `deriveAutoBodies()`, producing `ResolvedBody(source = ResolvedComponentSource.AUTO)` entries.
- Fill axial gaps between explicit (sacred) components. When OAL is manually authored, a base
  auto body spans 0 → OAL immediately; derived OAL does **not** seed a base auto body.
- Promotion to an explicit `Body` happens the moment the user edits one of the auto-body card's
  fields (Start / Length / Ø): `promoteIfNeeded()` in `ComponentCarousel.kt` calls
  `onAddBody(...)` on that first genuine edit and persists the section into `ShaftSpec.bodies`.
  Viewing the card without editing it never promotes it (see the "Auto-body promotion"
  invariant in `CLAUDE.md`).

### 3.1.2 Default Start Position (`computeAddDefaults`)

The default start for a new component is the **furthest FWD end** among sacred components only:
- All tapers
- All liners
- Threads with `excludeFromOAL = false`

Bodies are fillers and are **excluded** from this calculation. Excluded threads sit outside the shaft envelope and are **excluded**. Coupler bolt slots are pure reference overlays and are likewise **excluded**. This ensures new components always default to the next logical open slot in the shaft, not past the end.

### 3.1.3 Auto-Selection After Add

When any `add*At` function completes in the ViewModel, `selectedComponentId` is set to the newly added component's ID. The carousel auto-scrolls to and highlights the new component.

### 3.1.4 Body Split/Merge and the Carousel

Bodies are independent spec entities. The carousel shows **one card per body** in the spec — there is no deduplication. When a sacred component is placed over a body the engine produces two body fragments (each with a unique ID); the carousel shows both as separate cards so the user can edit each section's diameter independently.

When a sacred component is deleted the engine merges adjacent body fragments back into one card. The merged diameter is `max(left.diaMm, right.diaMm)`; the user can adjust it afterward.

### 3.1.5 Direction Chip (AFT / FWD Toggle)

Add dialogs that expose a direction toggle (Liner, Taper, Coupler Bolt Slot) use a custom `DirectionChip` composable:

- **Selected state**: 2 dp primary-color border, `primaryContainer` background.
- **Unselected state**: no border, `surface` background.

The border (not fill) is the selection indicator. An outlined unselected chip would visually compete with the selected chip; the borderless unselected state keeps the hierarchy clear.

### 3.2–3.6 Per-Dialog Contracts

The authoritative, current per-dialog field contract lives in
`app/src/main/java/com/android/shaftschematic/docs/AddComponentDialogs.md` (covers
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

**3.4 Liner Dialog** — See `AddLinerDialog` there for fields. Not covered there: the dialog
displays `freeToEndMm`, which is always ViewModel-computed; UI cannot calculate mm values itself.

**3.5 Liner Authored Reference (AFT/FWD)** — not restated in the in-source doc; kept here as
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
If reordering UI added later:
- UI emits intent: `onReorder(fromIndex, toIndex)`
- VM updates `_componentOrder`
- NO geometry recalculation in UI layer

Note: `_componentOrder` may remain as a stable tie-breaker, but spatial order is authoritative.

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

# 8. Summary

The UI:
- Holds temporary editing buffers
- Commits to ViewModel only on deliberate action
- Performs no geometry or validation logic
- Reactively displays ViewModel state
- Delegates *all* computations outward

UI is a pure presentation layer with strict boundaries.