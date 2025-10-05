# Free-to-End Badge – Contract (v1.1, restored style)

## Computation (model)
`freeMm = spec.freeToEndMm()` — mm only, clamped ≥ 0, computed in the model layer.

## Display
- Overlay location: **Top Center** of preview; `padding(top = 6.dp)`.
- Style: `Surface` with `shape = RoundedCornerShape(8.dp)`,
  `color = surface.copy(alpha = 0.85)`, `tonalElevation = 2.dp`.
- Text: `typography.labelSmall`, color `onSurface`.
- Label format: `Free to end: {value} {unit}` using current UI unit.

## Invariants
- No layout-dependent math in computation.
- Hidden when `overallLengthMm <= 0`.
