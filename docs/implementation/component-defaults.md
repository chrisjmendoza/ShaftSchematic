# Component Defaults – Implementation Notes

⚠️ Implementation Notes

This document describes UI or implementation behavior.
It is not a system contract.

## Source of truth
Code: app/src/main/java/com/android/shaftschematic/ui/config/AddDefaultsConfig.kt

Helpers (mm out):
- `defaultBodyLenMm(unit)`
- `defaultLinerLenMm(unit)`
- `defaultTaperLenMm(unit)`
- `defaultThreadLenMm(unit)`
- `defaultThreadMajorDiaMm(unit)`
- `defaultThreadPitchMm()`

## Parameter order (implementation)
```
addBodyAt(startFromAftMm, lengthMm, diaMm)
addLinerAt(startFromAftMm, lengthMm, odMm)
addTaperAt(startFromAftMm, lengthMm, startDiaMm, endDiaMm)
addThreadAt(startFromAftMm, lengthMm, majorDiaMm, pitchMm)
```

## Defaults (user intent)
### Unit = inches
- Body length: 16 in
- Liner length: 16 in
- Taper length: 12 in
- Taper SET/LET: 6 in → 7 in (1:12)
- Thread length: 6 in
- Thread major Ø: 5 in
- Thread pitch: 4 TPI (25.4 / 4 = 6.35 mm)

### Unit = mm
- Body length: 406.4 mm (16 in)
- Liner length: 406.4 mm (16 in)
- Taper length: 304.8 mm (12 in)
- Taper SET/LET: 152.4 mm → 177.8 mm (1:12)
- Thread length: 152.4 mm (6 in)
- Thread major Ø: 127.0 mm (5 in)
- Thread pitch: 6.35 mm

## Thread UI notes
- Display TPI as `25.4 / pitchMm` when unit = inches.
- On commit, `TPI` currently converts back to `pitchMm = 25.4 / TPI`.

---

### Authority
This document describes current implementation behavior only.

Authoritative rules and invariants live in:
- docs/ARCHITECTURE.md
- docs/UI_CONTRACT.md
- docs/VALIDATION_RULES.md

If this document conflicts with a contract, the contract wins.

---

### Authority
This document describes current implementation behavior only.

Authoritative rules and invariants live in:
- docs/ARCHITECTURE.md
- docs/UI_CONTRACT.md
- docs/VALIDATION_RULES.md

If this document conflicts with a contract, the contract wins.
