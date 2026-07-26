# Component Defaults (v1.3, 2026-07-26)

Central reference for default values used when creating new components.

---

## Source of Truth

**Code:** `app/src/main/java/com/android/shaftschematic/ui/config/AddDefaultsConfig.kt`

`AddDefaultsConfig` holds the **inch presets** (`*_IN` constants — authoritative for user
intent) plus `BODY_DIA_MM` (the auto-body bare-shaft Ø fallback used by
`ResolvedComponent`) and the coupler-bolt-slot constants. All real defaulting happens in
`SessionAddDefaults.initial()`, which converts the inch presets to canonical **mm**.

> The old unit-aware `default*Mm(unit)` helper functions and the `*_MM` twin constants
> were deleted in the 2026-07-26 dead-code pass — they had no callers. If a unit-aware
> default is ever needed again, add it to `SessionAddDefaults`, not here.

**Coupler bolt slot defaults:**
`SLOT_HOLE_DIA_IN`, `SLOT_SPACING_IN`, `SLOT_DEPTH_IN`, `SLOT_COUNT_DEFAULT`. Consumed
(converted to mm) by `SessionAddDefaults.initial()` — see `Notes` below.

---

## Parameter Order (Contract)

```text
addBodyAt(startFromAftMm, lengthMm, diaMm)
addLinerAt(startFromAftMm, lengthMm, odMm)
addTaperAt(startFromAftMm, lengthMm, startDiaMm, endDiaMm)
addThreadAt(startFromAftMm, lengthMm, majorDiaMm, pitchMm, excludeFromOAL, isAftEnd)  ← major Ø third, pitch fourth
```

> This order is enforced in UI and assumed by the ViewModel (`ShaftViewModel.addThreadAt`;
> full signature: `addThreadAt(startMm, lengthMm, majorDiaMm, pitchMm, excludeFromOAL = false, isAftEnd = true)`).
> Swapping `majorDiaMm`/`pitchMm` will yield incorrect TPI (e.g., ~0.508 TPI).

---

## Defaults (User Intent)

* **Body Length:** 16 in (Ø 7 in)
* **Liner Length:** 16 in (Ø 8 in)
* **Taper Length:** 12 in (SET Ø 6 in, LET Ø 7 in)
* **Taper Slope:** 1:12 (`endDia = startDia + length × (1/12)`)
* **Thread Length:** **6 in**
* **Thread Major Ø:** **5 in**
* **Thread Pitch:** **4 TPI** (i.e., `25.4 / 4 = 6.35 mm` pitch)
* **Coupler bolt slot:** hole Ø 0.5 in, count 2, spacing 2 in, blind depth 0.25 in

Metric display is the exact mm conversion of the same physical sizes (e.g. body
406.4 mm × Ø 177.8 mm). The first Add of a session seeds from
`SessionAddDefaults.initial()` (inch presets, converted); subsequent Adds reuse the
session's last-used size per component type. Thread's default **Major Ø** is fixed per
spec above, not derived from a prior segment.

---

## QA

* Inch mode → New Thread shows: **Length 6.00 in**, **TPI 4**, **Major Ø 5.00 in**.
* Metric mode → New Thread shows: **Length 152.4 mm**, **Pitch 6.35 mm**, **Major Ø 127.0 mm**.
* Thread pitch UI rule: display "TPI" as `25.4 / pitchMm` when unit = inches; on
  commit, TPI input converts back to `pitchMm = 25.4 / TPI`.
* Taper default end Ø = **start Ø + length × (1/12)**.
* `addThreadAt` is called with **[start, length, majorDia, pitch]**; TPI does **not** render as 0.508.

---

## Changelog

* **v1.3 (2026-07-26)** — Dead-code pass: removed the ten unused `default*Mm(unit)`
  helpers and the `*_MM` twin constants (except the live `BODY_DIA_MM` auto-body
  fallback). Doc now points at `SessionAddDefaults.initial()` as the only defaulting
  path.
* **v1.2 (2026-07-18)** — Corrected values to match `AddDefaultsConfig.kt`; fixed the
  `addThreadAt` parameter order; added diameter helpers + slot constants.
* **v1.1** — Added thread defaults (5" length, 6" majorØ, 4 TPI) and clarified parameter order.
* **v1.0** — Initial centralization and documentation of defaults.
