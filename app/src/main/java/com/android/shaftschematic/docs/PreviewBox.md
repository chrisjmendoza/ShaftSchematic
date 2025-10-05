# Preview Drawing Box – Contract (v1.0)

## Style
- Card: `RoundedCornerShape(16.dp)`, container `surfaceVariant`.
- Background inside Box: `surfaceVariant`.
- Optional grid: `outline` at ~25% alpha.
- Fixed-height band: `heightIn(min = 120.dp, max = 200.dp)`, `aspectRatio(3.8)`.

## Behavior
- Renders shaft via `renderShaft(spec, unit)`.
- Does not scale or modify geometry; preview only.

## Invariants
- Stable Material3 APIs only.
- No px/pt math leaks into model; model stays in mm.
- IME safety handled outside this section (screen scaffold).
