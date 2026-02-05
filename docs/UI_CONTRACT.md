# UI Contract
Version: v0.4.x

## Purpose
This document defines all UI interaction rules, screen behaviors, dialog behavior, input handling, and UI–ViewModel boundaries.  
The UI is responsible for **presenting** data, not **interpreting** or **computing** it.

---

## Current Behavior (v0.4.x)

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

# 2. Input Fields (NumericInputField)

### 2.1 Commit-on-Blur Rule (Mandatory)
Numeric fields **must not** commit changes while typing.

`LocalState.text` ← user types (filtered)
On blur or Done:
`parse text → float?`
`VM.update*(parsed value)`

 

Benefits:
- No jitter from immediate recomposition
- User can type partial numbers without errors

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
- Non-numeric → filtered out during typing; invalid parses revert to last valid text on blur

ViewModel must handle empty/invalid numeric commits safely.

### 2.4 Last-Valid + Error Feedback
- Numeric fields maintain `lastValidText` and revert on invalid commit attempts.
- Error indicators appear **only after blur/Done** and clear automatically once valid.

### 2.5 Numeric Field Clarifications (Current Behavior)
- Input is filtered live while typing.
- Commit happens only after a successful parse.
- Invalid input reverts on blur/Done.
- Errors are field‑local and non‑modal (no blocking dialogs).

See also: [Numeric input behavior](docs/implementation/ui-inputs.md).

## Authority
This document is authoritative for UI rules and boundaries.
If other documentation conflicts with this file, this file takes precedence.

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

### 3.2 Taper Dialog
Real-time fields:
- length
- startDia (SET)
- endDia (LET)
- optional taperRate

Rules:
- If SET & LET both given → taperRate ignored
- If only one diameter given → ViewModel derives the other from taperRate
- If neither diameter nor taperRate is sufficient → blocking error

### 3.3 Thread Dialog
UI displays:
- majorDia
- pitchMm
- tpi (imperial)

UI must never compute pitch↔tpi; ViewModel handles it.

### 3.4 Liner Dialog
Displays: freeToEndMm (ViewModel computed)

UI cannot calculate mm values.

### 3.5 Liner Authored Reference (AFT/FWD)
- Liners separate authored reference from physical geometry.
- UI must project authored “Start” based on selected reference.
- Switching AFT/FWD must **not** mutate physical geometry.
- ViewModel stores reference metadata; geometry remains canonical.

---

# 4. Component List (Ordering)

### 4.1 What ordering means
When provided, `componentOrder` from the ViewModel is **authoritative** and may mix types.
If `componentOrder` is empty, the UI falls back to spatial order (AFT → FWD).

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

## Planned / Future Behavior (Not Yet Implemented)

# 5.2 Planned Preview Tap + Implicit Bodies (Upcoming)

**Planned behavior (not yet implemented):**
- Preview taps produce raw mm coordinates.
- ViewModel performs snapping and stores snapped positions.
- Implicit bodies are derived and read-only (computed gaps between components).
- Promotion to an explicit Body is required before editing.
- When OAL is manually authored, a base auto body spans 0 → OAL immediately.
- Derived OAL does **not** seed a base body.

 

# 5.1 Preview Color Preferences (Settings)

Preview color preferences apply to the on-screen Preview only.

- Users select from presets: Stainless, Steel, Bronze, Transparent, or Custom.
- When Custom is selected, a theme-based palette is available.
- A “Black/White Only” toggle forces black outlines and disables fills in Preview.
- When Black/White Only is enabled, color controls are disabled but retain their last selections.

---

# 6. Validation Feedback (Current Behavior)

- Field-level errors may appear after commit attempts.
- There is no modal, blocking validation pipeline in the UI today.

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