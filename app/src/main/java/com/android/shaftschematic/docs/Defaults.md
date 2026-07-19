# Component Defaults (v1.2, 2026-07-18)

Central reference for default values used when creating new components.

---

## Source of Truth

**Code:** `app/src/main/java/com/android/shaftschematic/ui/config/AddDefaultsConfig.kt`

**Helpers (mm out):**

* `defaultBodyLenMm(unit)`
* `defaultLinerLenMm(unit)`
* `defaultTaperLenMm(unit)`
* `defaultThreadLenMm(unit)`
* `defaultThreadMajorDiaMm(unit)`
* `defaultThreadPitchMm()`
* `defaultBodyDiaMm(unit)`
* `defaultLinerOdMm(unit)`
* `defaultTaperSetDiaMm(unit)`
* `defaultTaperLetDiaMm(unit)`

> Canonical units are **millimeters (mm)**. Helpers return **mm** values for the active unit selection.

**Coupler bolt slot defaults (constants only, no unit-aware helper function):**
`SLOT_HOLE_DIA_IN`/`SLOT_HOLE_DIA_MM`, `SLOT_SPACING_IN`/`SLOT_SPACING_MM`,
`SLOT_DEPTH_IN`/`SLOT_DEPTH_MM`, `SLOT_COUNT_DEFAULT`. Consumed directly (already in mm,
inch-based) by `SessionAddDefaults.initial()` — see `Notes` below.

---

## Parameter Order (Contract)

```text
addBodyAt(startFromAftMm, lengthMm, diaMm)
addLinerAt(startFromAftMm, lengthMm, odMm)
addTaperAt(startFromAftMm, lengthMm, startDiaMm, endDiaMm)
addThreadAt(startFromAftMm, lengthMm, majorDiaMm, pitchMm, excludeFromOAL, isAftEnd)  ← major Ø third, pitch fourth
```

> This order is enforced in UI and assumed by the ViewModel (`ShaftViewModel.addThreadAt`, ~line 1163;
> full signature: `addThreadAt(startMm, lengthMm, majorDiaMm, pitchMm, excludeFromOAL = false, isAftEnd = true)`).
> Swapping `majorDiaMm`/`pitchMm` will yield incorrect TPI (e.g., ~0.508 TPI).

---

## Defaults (User Intent)

### When **Unit = inches**

* **Body Length:** 16 in (Ø 7 in)
* **Liner Length:** 16 in (Ø 8 in)
* **Taper Length:** 12 in (SET Ø 6 in, LET Ø 7 in)
* **Taper Slope:** 1:12 (`endDia = startDia + length × (1/12)`)
* **Thread Length:** **6 in**
* **Thread Major Ø:** **5 in**
* **Thread Pitch:** **4 TPI** (i.e., `25.4 / 4 = 6.35 mm` pitch)
* **Coupler bolt slot:** hole Ø 0.5 in, count 2, spacing 2 in, blind depth 0.25 in

### When **Unit = mm**

* **Body Length:** 406.4 mm (Ø 177.8 mm)
* **Liner Length:** 406.4 mm (Ø 203.2 mm)
* **Taper Length:** 304.8 mm (SET Ø 152.4 mm, LET Ø 177.8 mm)
* **Taper Slope:** 1:12
* **Thread Length:** 152.4 mm (6 in)
* **Thread Major Ø:** 127.0 mm (5 in)
* **Thread Pitch:** 6.35 mm (4 TPI)
* **Coupler bolt slot:** hole Ø 12.7 mm, count 2, spacing 50.8 mm, blind depth 6.35 mm

> The `*_MM` constants are the exact decimal equivalents of the `*_IN` constants
> (hardcoded rather than computed at runtime, "to avoid drift and match drawings/docs
> precisely" per the source comment) — both columns above describe the same physical
> size. In practice, the first Add of a session seeds from `SessionAddDefaults.initial()`
> (same constants, always inch-based internally); subsequent Adds reuse the session's
> last-used size per component type. Thread's default **Major Ø** is fixed per spec
> above, not derived from a prior segment.

---

## Usage in UI (`ShaftScreen`)

`ShaftScreen` reads defaults via the helpers:

```text
defaultBodyLenMm(unit)
defaultLinerLenMm(unit)
defaultTaperLenMm(unit)
defaultThreadLenMm(unit)
defaultThreadMajorDiaMm(unit)
defaultThreadPitchMm()
defaultBodyDiaMm(unit)
defaultLinerOdMm(unit)
defaultTaperSetDiaMm(unit)
defaultTaperLetDiaMm(unit)
```

Inline creation calls must respect the **Parameter Order** contract above. For example:

```text
onAddThread(startMm, threadLenMm, threadMajorDiaMm, threadPitchMm)
```

---

## QA

* Inch mode → New Thread shows: **Length 6.00 in**, **TPI 4**, **Major Ø 5.00 in**.
* Metric mode → New Thread shows: **Length 152.4 mm**, **Pitch 6.35 mm**, **Major Ø 127.0 mm**.
* Thread pitch UI rule: display "TPI" as `25.4 / pitchMm` when unit = inches; on
  commit, TPI input converts back to `pitchMm = 25.4 / TPI`.
  (`defaultThreadPitchMm()` is unit-independent — always derived from `THREAD_TPI_IN`.)
* Taper default end Ø = **start Ø + length × (1/12)**.
* `addThreadAt` is called with **[start, length, majorDia, pitch]**; TPI does **not** render as 0.508.

---

## Changelog

* **v1.2 (2026-07-18)** — Corrected values to match `AddDefaultsConfig.kt`: Body/Liner
  length 406.4 mm, Taper length 304.8 mm / 12 in (was 100 mm / 16 in); Thread length
  6 in / 152.4 mm and Major Ø 5 in / 127.0 mm (were swapped). Fixed the
  `addThreadAt` parameter order (major Ø before pitch, not after — matches
  `ThreadDefaults.md`). Added the missing diameter helpers (`defaultBodyDiaMm`,
  `defaultLinerOdMm`, `defaultTaperSetDiaMm`, `defaultTaperLetDiaMm`) and the
  coupler-bolt-slot default constants.
* **v1.1** — Added thread defaults (5" length, 6" majorØ, 4 TPI) and clarified parameter order.
* **v1.0** — Initial centralization and documentation of defaults.
