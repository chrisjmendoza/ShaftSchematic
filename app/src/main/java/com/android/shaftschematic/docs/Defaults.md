# Component Defaults (v1.1)

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

> Canonical units are **millimeters (mm)**. Helpers return **mm** values for the active unit selection.

---

## Parameter Order (Contract)

```text
addBodyAt(startFromAftMm, lengthMm, diaMm)
addLinerAt(startFromAftMm, lengthMm, odMm)
addTaperAt(startFromAftMm, lengthMm, startDiaMm, endDiaMm)
addThreadAt(startFromAftMm, lengthMm, pitchMm, majorDiaMm)  ← pitch third, major Ø last
```

> This order is enforced in UI and assumed by the ViewModel. Swapping `pitchMm`/`majorDiaMm` will yield incorrect TPI (e.g., ~0.508 TPI).

---

## Defaults (User Intent)

### When **Unit = inches**

* **Body Length:** 16 in
* **Liner Length:** 16 in
* **Taper Length:** 16 in
* **Taper Slope:** 1:12 (`endDia = startDia + length × (1/12)`)
* **Thread Length:** **5 in**
* **Thread Major Ø:** **6 in**
* **Thread Pitch:** **4 TPI** (i.e., `25.4 / 4 = 6.35 mm` pitch)

### When **Unit = mm**

* **Body Length:** 100 mm
* **Liner Length:** 100 mm
* **Taper Length:** 100 mm
* **Taper Slope:** 1:12
* **Thread Length:** 127 mm (5 in)
* **Thread Major Ø:** 152.4 mm (6 in)
* **Thread Pitch:** 6.35 mm (4 TPI)

> UI may seed diameters for Body/Liner/Taper from the last segment’s diameter; Thread’s default **Major Ø** is fixed per spec above.

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
```

Inline creation calls must respect the **Parameter Order** contract above. For example:

```text
onAddThread(startMm, threadLenMm, threadPitchMm, threadMajorDiaMm)
```

---

## QA

* Inch mode → New Thread shows: **Length 5.00 in**, **TPI 4**, **Major Ø 6.00 in**.
* Metric mode → New Thread shows: **Length 127 mm**, **Pitch 6.35 mm**, **Major Ø 152.4 mm**.
* Taper default end Ø = **start Ø + length × (1/12)**.
* `addThreadAt` is called with **[start, length, pitch, majorDia]**; TPI does **not** render as 0.508.

---

## Changelog

* **v1.1** — Added thread defaults (5" length, 6" majorØ, 4 TPI) and clarified parameter order.
* **v1.0** — Initial centralization and documentation of defaults.
