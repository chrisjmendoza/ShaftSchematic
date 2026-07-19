# Free-to-End Badge – Contract (v1.2)

## Computation
The `FreeToEndBadge` composable (`ShaftScreen.kt`) computes its own signed value:
`freeSignedMm = spec.overallLengthMm - lastOccupiedEndMm(spec)` — mm only, **not
clamped**. A negative value means the shaft is oversized and drives the red state.
(`spec.freeToEndMm()` in the model clamps ≥ 0 and is not what the badge uses.)

## Display
- Overlay location: **Top Start** of preview; `padding(8.dp)`.
- Style: `Surface` with `shape = RoundedCornerShape(8.dp)`,
  `color = surface.copy(alpha = 0.85)`, `tonalElevation = 2.dp`.
- Text: `typography.labelSmall`, color `onSurface`.
- Label format: `Free to end: {value} {unit}` using current UI unit.

## Invariants
- No layout-dependent math in computation.
- Shown **only in manual OAL mode** (`overallIsManual`); never rendered in Auto OAL
  mode, where free-to-end is definitionally zero.
- Hidden when `overallLengthMm <= 0`.
- Hidden when no precision components (tapers/non-excluded threads/liners) are present
  AND shaft is not oversized. With only bodies, auto-bodies visually fill the remaining
  OAL span, so the badge would mislead the user. The oversized (red) case still shows
  regardless.
