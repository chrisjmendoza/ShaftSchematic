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
- `TemplateBuilderScreen.md` — blank-template builder contracts and invariants

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
- **Taper AFT/FWD reference**: `AddTaperDialog` must show AFT/FWD direction chips.

### Numeric input commit behavior
`NumericInputField` only calls `onCommit` on blur **if the value changed** since focus
was gained. A tap-and-leave with no edit must be a no-op. This prevents spurious
auto-body promotion and unnecessary ViewModel updates. See `NumberField.md`.

### Auto-body promotion
Auto-body cards in the carousel (`ResolvedComponentSource.AUTO`) promote to real bodies
only when the user **explicitly edits** one of their fields. Blur without a value change
must not trigger `promoteIfNeeded()`. See the `promoted` state in `ComponentCarousel.kt`.

### Free-to-End badge suppression
The badge is hidden when there are no precision components (tapers, non-excluded threads,
liners) and the shaft is not oversized. With only bodies, auto-bodies visually fill the
remainder, so the badge value would always mislead. See `FreeToEndBadge.md`.

### OAL field
The OAL field calls `onSetOverallLengthMm` on **every keystroke** in manual mode (not
just on blur). This is intentional — the preview updates live. Do not change this to
commit-on-blur only.

### Template Builder independence
`TemplateViewModel` is entirely separate from `ShaftViewModel`. It must never call
editor methods (`addBodyAt`, `exportJson`, etc.) and must never be hoisted above the
`"template"` nav route. The template OAL is fixed at 600 mm (notional, never shown).
PDF export always uses `PdfExportMode.BlankTemplate`. See `TemplateBuilderScreen.md`.

## Commit policy
Do **not** auto-commit. The user reviews changes before every commit.
