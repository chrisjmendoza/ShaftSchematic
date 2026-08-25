# Bore Keyway Rough-Cutter Depth Calculator — Plan (2026-08-24)

Status: **BUILT 2026-08-24** — `geom/BoreKeywayMath.kt` + `ui/screen/BoreKeywayCalcDialog.kt`,
launched from the editor sidebar's bottom group. Requested on-device 2026-08-24; the four open
questions below were settled the same day (answers inline). Awaiting on-device verification.

## Purpose

Calculate the depth to measure at the edges of a **narrower roughing cutter** so its flat
bottom lands on the same plane as the specified finished keyway, for a keyway cut into the
inside surface of a **circular bore** (the hub/coupling side — not shaft geometry).

Because the bore surface curves, a narrower cutter's edges sit closer to the keyway
centerline where the bore surface is lower, so at the same flat-bottom plane a narrower
cutter always measures a **smaller** edge depth than the finished keyway.

## Geometry

Origin at the lowest point of the bore; bore surface height at half-width x is
`Y(x) = R − √(R² − x²)`. Keyway depth is measured from the bore surface **at the outer
edges** of the cutter/keyway down to the flat bottom.

## The formula (exact — the R terms cancel)

```
R = D / 2
Depth_current = Depth_final + √(R² − (W_final/2)²) − √(R² − (W_current/2)²)
```

Inputs: bore diameter `D`, finished keyway width `W_final`, finished keyway depth at the
edges `Depth_final`, current (rough) cutter width `W_current`.

Primary output: `Depth_current` — the target depth at the current cutter's outer edges.
Useful secondary outputs: the finished spec echoed back, and
`Depth_final − Depth_current` (the correction).

## Validation

Reject / flag: any input ≤ 0; `W_final/2 ≥ R`; **`W_current > W_final`**.

**`W_current > W_final` (added 2026-08-25, on-device report).** A roughing cutter cuts INSIDE
the finished profile, so a cutter wider than the finished keyway would leave it oversize —
there is no target depth, because reaching the plane with that cutter is already the wrong
cut. The bare geometry returns a perfectly plausible number here, which is exactly why the
check is needed: a 2" cutter left over from a 2½" keyway job kept calculating after the job
switched to a 1½" keyway. This also **replaces** the old `W_current/2 ≥ R` check, which is now
unreachable: a cutter can be no wider than the keyway, and the keyway is already bounded inside
the bore, so the cutter's radicand is positive by construction.

**`Depth_current ≤ 0`** — a very narrow cutter with a shallow finished depth can put the
finished plane *above* the bore surface at the current edges, meaning the cutter never breaks
the surface at its edges when it reaches the plane. Surface as a message; never print a
negative depth.

Base checks (bore + keyway, no cutter) live in `validateBoreKeyway` so the entry surface can
mark the offending **field** before any cutter is typed; every rejected entry sets its field's
error state as well as printing a reason.

**Deliberately out of scope:** no plausibility check of keyway size vs bore beyond the
geometric bound (this is a measuring aid, not a design validator).

## Invariants (pin in tests)

1. `W_current == W_final` → `Depth_current == Depth_final` (exact, FP tolerance).
2. `W_current < W_final` → `Depth_current < Depth_final` — **always** (√(R²−x²) is strictly
   decreasing in x), assert unconditionally.
3. `Depth_current → Depth_final` as `W_current → W_final`.
4. Smaller bore → larger correction; larger bore → smaller correction (flatter surface).

## Test vectors (verified by hand 2026-08-24)

| D | W_final | Depth_final | W_current | Depth_current |
|---|---|---|---|---|
| 7.000 | 1.500 | 0.59375 (19/32) | 1.000 | ≈ 0.5483 (≈ 35/64) |
| 8.000 | 1.750 | 0.59375 | 1.000 | ≈ 0.5282 |
| 7.000 | 1.500 | 0.5625 | 1.000 | ≈ 0.5171 |
| any | = W_final | d | = W_final | = d exactly |

## App-fit decisions (settled by convention)

- **Pure math in `geom/BoreKeywayMath.kt`** — unit-agnostic floats (the geometry needs no
  unit conversion; only display formatting cares), unit-tested with the vectors above plus
  every validation case. No `pdf → ui` dep, the usual posture.
- **Entry** accepts fractions via the existing `parseFractionOrDecimal` (`19/32` works
  today); commit-on-blur per `NumberField.md`.
- **Output**: decimal is the authoritative machining value; nearest-64th fractional
  approximation (e.g. "≈ 35/64") is a secondary line and **never substitutes** for the
  decimal. Compose-side text stays plain (editable/derived text is never prettified —
  `FractionTypography.md`); nothing prints on any PDF in v1.
- **Document-independent**: reads nothing from `ShaftSpec` (bore diameter is the hub's
  dimension), stores nothing in the document, no dirty state, no export surface, outside
  the add-dialog-parity invariant. Golden rule trivially safe — all outputs derived.

## Decisions (settled on-device 2026-08-24)

1. **Placement: (c)** — dialog launched from the sidebar's bottom group ("Keyway
   calculator", above Settings), reachable from every tab and never dimmed by the "built"
   gate (a tool, not a document view).
2. **Units: in|mm chip defaulting to the document unit** ("I always have on imperial, mm is
   rare for me"). The chip is unit-REINTERPRETING, not converting — geometry is
   unit-independent; typed numbers are read in the selected unit. The nearest-64th line
   prints only in inches.
3. **Start blank, nothing persisted** — "it's just a tool I plan to use while working, has
   no effect on the shaft schematic." No DataStore, no dirty mark, no envelope field.
4. **Up to 2 cutters** — each entered width gets its own result row ("At most I've ever seen
   3 used for a single propeller and that was a wide one").

Live recompute per keystroke (no commit-on-blur): there is no model write for the blur
contract to guard — the `NumberField` doctrine protects stored component values, and this
dialog stores nothing.
