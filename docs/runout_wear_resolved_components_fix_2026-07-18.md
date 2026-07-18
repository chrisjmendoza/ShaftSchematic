# Runout / Wear documents drew from raw spec instead of resolved components

**Date:** 2026-07-18
**Branch:** `fix/runout-wear-resolved-components` (off `feat/auto-taper-rate`)
**Reported symptom:** On the NorthSound Marine example shaft, the schematic and part
listing are correct, but the Runout Sheet "thinks there is a body component in the
taper" (station bubbles cluster inside the AFT taper with stacked leaders), and on the
Wear Document some parts don't line up.

---

## Root cause

The model layer allows a stored `Body` to overlap tapers and liners. That is by design:
body geometry is only turned into drawable segments at **resolution** time
(`ui/resolved/ResolvedComponent.kt → resolveComponents()`), which:

1. **subtracts** bodies against non-bodies (a body overlapping a taper is clipped to the
   part outside the taper),
2. **splits/merges** the fragments (`normalizeBodies`), and
3. derives **auto-bodies** for uncovered gaps.

The schematic screen and the main schematic PDF both consume this resolved list
(`composeShaftPdf(resolvedComponents = …)`). The runout and wear pipelines never did —
four call sites consumed **raw** `spec.bodies`:

| Site | Raw-spec use |
|---|---|
| `RunoutPdfComposer.computePlacedStations` | station entries from `spec.bodies` |
| `RunoutPdfComposer.drawShaftProfile` / `shaftOuterRPxAt` | body rectangles + OD lookup from `spec.bodies` |
| `WearPdfComposer.drawWearShaftProfile` | body rectangles from `spec.bodies` |
| `RunoutRoute` | station-count selector `entries` and canvas `drawRunoutMarkers` from `spec.bodies/tapers/liners` |

On the NorthSound shaft, a stored body starts at 0 and overlaps the AFT taper (0–12″).
The schematic resolves that overlap away; the runout/wear documents did not, so:

- the station selector listed "Body #1" **before** "AFT Taper" (sorted by raw start 0),
- body runout stations landed **inside the taper span**, colliding with the taper's own
  stations (hence the stacked bubbles hanging low at the aft end),
- the wear/runout profiles drew the body rectangle **through the taper trapezoid**
  ("parts aren't lining up").

Any shaft whose raw bodies overlap tapers/liners, or that relies on auto-body fill,
reproduced some flavor of this.

## Fix

Follow the exact pattern `composeShaftPdf` already established — the resolved list is
passed in and resolved bodies replace `spec.bodies`:

- **`pdf/ShaftPdfComposer.kt`** — new shared helper
  `internal fun ShaftSpec.withResolvedBodies(resolved: List<ResolvedComponent>?)`:
  returns a spec copy whose `bodies` are the resolved body segments (tapers, threads,
  liners, coupler bolt slots pass through resolution verbatim, so only bodies swap).
- **`pdf/RunoutPdfComposer.kt`** — `composeRunoutPdf` takes
  `resolvedComponents: List<ResolvedComponent>? = null`; profile drawing, OD lookup,
  max-OD, and station computation all use `docSpec = spec.withResolvedBodies(…)`.
- **`pdf/WearPdfComposer.kt`** — same parameter; profile + max-OD use `docSpec`.
- **`ui/screen/RunoutRoute.kt`** —
  - station selector `entries` built from `resolvedComponents` (auto-bodies appear as
    "Body (auto)", carousel parity), `distinctBy { id }` so a body split into fragments
    keeps one row;
  - canvas `drawRunoutMarkers` iterates `resolvedComponents`;
  - both `composeRunoutPdf` call sites pass `resolvedComponents`;
  - preview `LaunchedEffect` keys include `resolvedComponents`.
- **`ui/screen/WearRoute.kt`** — collects `vm.resolvedComponents`, passes it through
  both `composeWearPdf` call sites, added to preview keys.

OAL window / SET-position math is untouched — it never depended on bodies.

## Behavior changes to be aware of

- **Station rows now mirror the drawn shaft.** A body fully covered by a taper
  disappears from the station list (it has no measurable surface). Adjacent body
  segments that resolution merges (e.g. a clipped Body #1 fragment + Body #2) appear as
  **one** row/section — which is physically right: it's one continuous cylindrical run.
- **Auto-body spans now get stations and profile outline** on the runout/wear documents
  (previously they were silently skipped, leaving gaps).
- **Override keys:** station-count overrides for auto-bodies are keyed by the positional
  auto-id (`auto_body_<start>_<end>`); they detach if the geometry changes. Same
  trade-off the carousel already makes for auto cards.

## Verification

- `:app:compileDebugKotlin` clean; full `:app:testDebugUnitTest` suite passes.
- Same-math SVG before/after comparison published as an artifact for markup review
  (raw-spec layout vs. resolved layout on a NorthSound-shaped example).

## Docs

`app/src/main/java/com/android/shaftschematic/docs/RunoutSheet.md` — the "Pass 1 —
Collect" section should eventually note that components are the **resolved** list, not
raw spec (updated in this change).
