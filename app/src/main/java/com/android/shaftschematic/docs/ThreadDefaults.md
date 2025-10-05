# Thread Defaults (v1.0)

## Creation Parameter Order (Contract)
addThreadAt(
startFromAftMm: Float,
lengthMm: Float,
pitchMm: Float,
majorDiaMm: Float
)

## Defaults
- Inches selected:
    - Length: 6 in (152.4 mm)
    - Pitch: 4 TPI → 25.4 / 4 = 6.35 mm
- Metric selected:
    - Length: 100 mm
    - Pitch: (set per product defaults; currently 6.35 mm if mirroring 4 TPI)

## UI
- Display “TPI” as `25.4 / pitchMm` when unit = inches.
- On commit, `TPI` input converts back to `pitchMm = 25.4 / TPI`.
