# Liner Wear Areas — Feature Scoping Document

**Date:** 2026-07-18 · **Scoped by:** Fable (for implementation by other models)
**Status:** PROPOSAL — awaiting Chris's review. Do not implement until approved.
**Reference:** Chris's shop sketch (Tidewater job 934918 STBD) — three liner detail views,
each "cut out" of the shaft with break symbols, enlarged, with wear spans drawn as lines
on the liner and dimensioned (e.g. `37¾`, `15½`, `110 FROM CPLG S.E.T.`).

---

## 1. What this feature is

On the **Wear Document tab**, the user sees the shaft profile. Tapping a **liner**
"zooms in" on it: the liner is broken out of the shaft (break symbols on both sides,
exactly like the shop-sketch convention) and drawn enlarged so there's room for detail.
Inside that detail view the user records **wear spots**: axial start/end of each worn
band plus its measurements. Wear spots render as marked bands on the liner in both the
detail view and the exported wear PDF, with dimension callouts.

This digitizes what machinists already draw by hand: the shop sketch's middle drawing is
a liner detail with wear lines at `3T` and `15½` and an anchor dim `110 FROM CPLG S.E.T.`

### Explicitly out of scope (this phase)
- Wear on bodies/tapers (model leaves the door open; UI is liners-only for now).
- Severity ratings, dye-pen pass/fail digitization, photos (existing Phase-2 wear notes).
- Editing liner geometry from the detail view (geometry stays on the Shaft tab).

---

## 2. UX flow

### 2.1 Wear tab (overview level)
1. Replace the current static explanation block with an interactive shaft canvas at the
   top (same pattern as `RunoutRoute`'s preview canvas: `ShaftLayout.compute` +
   `ShaftRenderer.draw` with `resolvedComponents` — **never raw spec**; see
   `docs/archive/runout_wear_resolved_components_fix_2026-07-18.md`).
2. Liners are tap targets. Hit-test in **mm space**: invert `layout.xPx()` to map the tap
   x back to mm, pick the liner whose span contains it (liners never overlap each other
   in practice; if two match, pick the nearer edge). Add a subtle affordance: liners get
   a faint tint + "tap to inspect" hint text below the canvas.
3. Liners that already have wear spots show a small badge (count) above the liner.

### 2.2 Liner detail view (zoom level)
Full-screen overlay (same pattern as `PdfPreviewOverlay` — a composable overlay, not a
nav destination, with `BackHandler` to dismiss):

```
┌──────────────────────────────────────────────────────────┐
│ ←  Seal sleeve — wear inspection                         │
│                                                          │
│    ~╱╲   ┌────────────────────────────────┐   ╱╲~        │
│   body ⟩ │   ▒▒▒▒▒            ▒▒▒▒        │ ⟨ body       │
│    ~╲╱   └────────────────────────────────┘   ╲╱~        │
│          |––2.5––|▒5.0▒|                                 │
│          |← from AFT edge                                │
│                                                          │
│  Wear spots                              [+ Add spot]    │
│  ┌ Spot 1 ────────────────────────────────── 🗑 ┐        │
│  │ Start from liner AFT edge (in)  [ 2.5   ]     │        │
│  │ Length (in)                     [ 5.0   ]     │        │
│  │ Min diameter measured (in)      [ 5.480 ]     │        │
│  │ Notes                           [ ...   ]     │        │
│  └───────────────────────────────────────────────┘        │
└──────────────────────────────────────────────────────────┘
```

- The liner is drawn at a **fixed large scale** (fill ~80% of width), with break-edge
  S-curves (`drawBreakEdge` convention) on the neighboring geometry stubs — the "cut
  out" look from the sketch.
- Each wear spot renders as a hatched/tinted band on the liner at its true position, and
  gets a small dimension rail below: offset-from-AFT-edge and band length. Live update
  as fields commit.
- Fields use `NumericInputField` and obey its contract (commit on blur only if changed —
  `docs/NumberField.md`). Unit conversion at the UI edge only; model is mm.
- Add spot → appends with sensible defaults (start = 0, length = 25.4 mm, no reading);
  Delete → confirm-free removal (single undo via snackbar is a nice-to-have, not
  required).

### 2.3 Values captured per wear spot (proposed — confirm with Chris)
| Field | Model | Required |
|---|---|---|
| Start from liner AFT edge | `startMm: Float` | yes |
| Length | `lengthMm: Float` | yes |
| Minimum measured diameter in the band | `minDiaMm: Float` (0 = not recorded) | no |
| Free-text note ("scored", "pitted 6 o'clock"…) | `note: String` | no |

**Open question for Chris:** is one min-diameter reading per band enough, or do you want
multiple readings (position + diameter pairs) inside a band? The model below can grow a
`readings: List<WearReading>` later without breaking files (additive optional field).

---

## 3. Data model

New file `model/WearSpot.kt`:

```kotlin
/**
 * A recorded wear band on a liner. Reference feature ONLY:
 * never affects OAL, coverage, body resolution, or collision validation.
 * Units: mm. startMm is measured from the liner's AFT edge (liner-local space),
 * so wear records survive the liner being repositioned on the shaft.
 */
@Serializable
data class WearSpot(
    val id: String = UUID.randomUUID().toString(),
    val linerId: String,
    val startMm: Float = 0f,     // from liner AFT edge, liner-local
    val lengthMm: Float = 0f,
    val minDiaMm: Float = 0f,    // 0 = no reading recorded
    val note: String = "",
)

/** Per-document wear inspection record. Lives beside RunoutConfig in the envelope. */
@Serializable
data class WearRecord(
    val spots: List<WearSpot> = emptyList(),
)
```

Design decisions baked in:
- **Liner-local coordinates.** `startMm` is relative to the liner's AFT edge, not the
  shaft. Editing the liner's position on the Shaft tab must not silently relocate wear
  history. Clamp rendering (not data) when a spot extends past the liner end.
- **Keyed by `linerId`.** If the liner is deleted, its wear spots become orphans —
  filter orphans on load (`spots.filter { spec.liners.any { ln -> ln.id == it.linerId } }`)
  and drop them on next save. Cheap and predictable.
- **Flat list, not nested in `Liner`.** `Liner` is geometry; wear is inspection data.
  Keeping `ShaftSpec` purely geometric preserves every existing contract (resolution,
  collision, coverage) with zero risk. This mirrors how `couplerBoltSlots` stayed out of
  geometry paths, and how `RunoutConfig` lives beside the spec, not inside it.

---

## 4. Persistence

`doc/ShaftDocCodec.kt`, `ShaftDocV1` — add one optional field with a default (the
documented pattern: *"All new fields must carry a default value so older files
deserialize cleanly"*):

```kotlin
@SerialName("wear_record")
val wearRecord: WearRecord = WearRecord(),
```

- **No version bump.** Additive + defaulted round-trips both directions
  (`ignoreUnknownKeys = true` for older builds reading newer files).
- Thread through `Decoded`, `encodeV1` call sites, `AutosaveManager` snapshot (join the
  same `combine` that carries `runoutConfig`), and `io/ShaftBackup` if it enumerates
  envelope fields explicitly.
- Add round-trip tests beside `persistence/DocVersionTest.kt`.

---

## 5. ViewModel

`ShaftViewModel` (follow `docs/ShaftViewModel.md` ownership rules):

```kotlin
private val _wearRecord = MutableStateFlow(WearRecord())
val wearRecord: StateFlow<WearRecord> = _wearRecord.asStateFlow()

fun addWearSpot(linerId: String)
fun updateWearSpot(id: String, startMm: Float, lengthMm: Float, minDiaMm: Float, note: String)
fun removeWearSpot(id: String)
```

- Setters are plain state updates; no geometry side effects, no snapping, no interaction
  with `ensureOverall`/auto-body logic — wear is reference-only by contract.
- Load/save wired wherever `runoutConfig` already is (open, save, autosave restore,
  new-document reset).

---

## 6. Rendering

### 6.1 Detail view (Compose canvas)
New file `ui/screen/LinerWearDetail.kt`:
- Layout math is self-contained (it draws ONE liner + stubs, not the whole shaft):
  `pxPerMm = usableWidthPx / linerLengthMm` capped so very short liners don't explode.
- Neighbor stubs: draw ~24 dp of the adjacent resolved component's outline on each side
  at correct relative diameter, terminated with the S-curve break edge. Get neighbors
  from `resolvedComponents` (the segments adjacent to the liner span).
- Wear bands: `linerFillColor`-family tint + diagonal hatch (reuse the hatch approach
  from `ShaftRenderer`'s thread hatch), plus a thin dimension rail below with the two
  values formatted via the existing `LengthFormat` utilities in the active unit.

### 6.2 Wear PDF (`pdf/WearPdfComposer.kt`)
Phase-2 of this feature (see §8). Target layout mirrors the shop sketch:
- Main profile (existing) stays; liners with wear spots get their bands drawn on the
  main profile (thin hatched bands — visible but not dominant).
- Below the main profile, one **detail strip per liner that has wear spots** (max 3 per
  page; overflow to a second page): broken-out liner at large scale, wear bands hatched,
  dims: liner AFT edge → band start, band length, min-Ø reading printed inside/below the
  band, plus the liner's anchor dim from the AFT SET (reuse `LinerSpanBuilder` math —
  this is the `110 FROM CPLG S.E.T.` line in the sketch).
- All detail-strip geometry from **resolved components** passed in (the parameter added
  by the 2026-07-18 fix).

---

## 7. Contracts & invariants (add to `docs/` when implemented)

1. **Wear is reference-only.** Never affects `coverageEndMm`, `ensureOverall`,
   body resolution, collision/overlap validation, or the Free-to-End badge. (Same class
   of rule as coupler bolt slots — see `CLAUDE.md`.)
2. **Liner-local space.** `WearSpot.startMm` is from the liner AFT edge. Converting to
   shaft space is `liner.startFromAftMm + spot.startMm`, done at render time only.
3. **Unit edge rule.** mm in model/VM; in↔mm only in composables/formatters.
4. **Commit-on-blur.** All numeric fields per `NumberField.md`; no keystroke commits.
5. **Resolved components everywhere.** Any drawing of shaft context (overview canvas,
   neighbor stubs, PDF strips) uses the resolved list, never raw `spec.bodies`.
6. **Orphan policy.** Spots whose `linerId` no longer exists are dropped on load.

---

## 8. Implementation phases (each lands independently)

| Phase | Scope | Files |
|---|---|---|
| **1 — Model + persistence** | `WearSpot`/`WearRecord`, envelope field, VM state + setters, autosave, round-trip tests | `model/WearSpot.kt` (new), `doc/ShaftDocCodec.kt`, `ui/viewmodel/ShaftViewModel.kt`, `data/AutosaveManager.kt`, tests |
| **2 — Wear tab canvas + tap** | Interactive overview canvas on WearRoute, liner hit-testing, wear badges | `ui/screen/WearRoute.kt` |
| **3 — Detail overlay** | Break-out liner view, wear band rendering, add/edit/delete spot cards | `ui/screen/LinerWearDetail.kt` (new) |
| **4 — PDF detail strips** | Bands on main profile + per-liner detail strips in the wear PDF | `pdf/WearPdfComposer.kt`, `docs/RunoutSheet.md` update |

Phases 1–3 are one reviewable unit if preferred; 4 is genuinely separable.

## 9. Test plan

- **Model:** liner-local ↔ shaft-space conversion; orphan filtering; clamping of a spot
  extending past the liner end (renders clamped, data preserved).
- **Persistence:** envelope round-trip with/without `wear_record`; legacy file (no
  field) → empty record; forward-compat (older build ignores field, doesn't destroy it —
  note: older builds WILL drop it on re-save because they re-encode; document this).
- **VM:** add/update/remove flows; reset on new document; restore from autosave.
- **PDF:** page-bounds test in the style of `PdfLayoutBoundsTest` for detail strips
  (nothing draws outside content rect; ≤3 strips per page).

## 10. Open questions for Chris

1. **Readings per band** — single min-Ø enough, or multiple (position, Ø) pairs? (§2.3)
2. **Reference edge** — spot start measured from liner AFT edge always, or follow the
   liner's `authoredReference` (AFT/FWD) so the detail view matches how the liner was
   authored?
3. **Bands on the main wear-PDF profile** — draw them there too, or detail strips only?
4. **Detail strip selection** — auto (any liner with spots) or a per-liner "include in
   PDF" toggle like the coupler-slot dimension-rail toggle?
5. **Bodies later?** If wear on plain body sections is coming, `WearSpot.linerId`
   becomes `componentId` — cheap to generalize now (rename + resolve against any
   resolved component). Say the word and Phase 1 does it from the start.
