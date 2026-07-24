# Free-to-End Badge – Contract (v1.4)

## Computation
The `FreeToEndBadge` composable (`ui/screen/ShaftPreviewPanel.kt` — moved from
`ShaftScreen.kt` 2026-07-24, pure code move) gets its signed value from the pure
helper `freeToEndSignedMm(spec)` (`ui/util/FreeToEndBadgeMath.kt`):
`freeSignedMm = effectiveOalMm - lastOccupiedEndMm(spec)` — mm only, **not clamped**.
`effectiveOalMm` is `spec.overallLengthMm`, **except** when that is exactly `0f`
(manual-OAL mode with no length entered yet), in which case it falls back to
`spec.lastOccupiedEndMm()` — the same fallback the preview's `safeSpec` uses to extend
a zero-OAL shaft to its last occupied end. This makes the OAL==0 case read `0` instead
of a phantom negative "oversized" value. A negative value in any other case means the
shaft is genuinely oversized (OAL > 0, components run past the end) and drives the red
state. (`spec.freeToEndMm()` in the model clamps ≥ 0 and is not what the badge uses.)

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
- At `overallLengthMm == 0` the value shows as `0` (via the `safeSpec` fallback above),
  not a hidden badge and not a red negative — visibility still follows the precision-
  component rule below.
- Hidden when no precision components (tapers/non-excluded threads/liners) are present
  AND shaft is not oversized. With only bodies, auto-bodies visually fill the remaining
  OAL span, so the badge would mislead the user. The oversized (red) case still shows
  regardless.
