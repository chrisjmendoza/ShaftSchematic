# Taper Orientation Discrepancy — Analysis (2026-07-26)

**Source:** TODO §2.3 "Investigate renderer/storage taper orientation discrepancy"
(discovered 2026-07-25 while fixing the §3.3 taper-vs-body warning false positive).
**Status:** analysis only — no code changed. The canonical-convention decision is a
product call and is left open at the bottom.

---

## 1. The intended contract

The model stores taper diameters **x-ordered along the shaft axis**:
`startDiaMm` is the diameter at `startFromAftMm` (the AFT-most face of the taper),
`endDiaMm` at the FWD-most face. SET/LET are *display labels only*, assigned by which
**shaft half** the taper's midpoint sits in:

- `ui/input/TaperSetLetMapping.kt` — "Model semantics (unchanged): startDiaMm/endDiaMm
  are the left→right diameters along the taper. Therefore the UI must flip labels for
  FWD tapers, without swapping stored values."
  `classifyTaperSideByMidpoint` (line 51): midpoint ≤ OAL/2 → AFT taper (SET at start).
- `ShaftViewModel.taperSmallEndAtStart` (ShaftViewModel.kt:2347) mirrors the same rule
  for rate-derivation, with an explicit KDoc pointer back to `taperSetLetMapping`.

So the *convention* is: **SET faces the nearer shaft end** (SET-by-shaft-half), and
storage order is purely positional.

## 2. Who writes taper diameters, and which rule each writer uses

| Writer | Rule used to decide which value lands in `startDiaMm` | Consistent with contract? |
|---|---|---|
| `AddTaperDialog` submit (AddComponentDialogs.kt:819-824) | The dialog's **measure-from toggle** (`isFwd`): AFT → SET goes to `startDiaMm`; FWD → LET goes to `startDiaMm`. Present since 0b6d17b (2026-06-18). | **No** — the toggle is the user's *measuring* choice, not the taper's physical half |
| Carousel edit card (ComponentCarousel.kt:676, 794-806) | x-ordered binding via `taperSetLetMapping` — left field always writes `startDiaMm`; only the labels flip, by **midpoint** | Yes |
| `deriveTaperDiameters` (ShaftViewModel.kt:2317, called from `addTaperAt`:1329 and `updateTaper`:1370) | Fills a missing diameter with the sign chosen by **midpoint** (`taperSmallEndAtStart`) | Yes |

## 3. Who reads them, and which rule each reader uses

| Reader | Rule | Notes |
|---|---|---|
| `ShaftRenderer` trapezoid (ShaftRenderer.kt:212-217 resolved, 250-255 spec) | Strict x-order — draws `startDiaMm` at the AFT face unconditionally | Faithful to storage; correct **iff** the writer followed the convention |
| Keyway placement (ShaftRenderer.kt:670 `setAtStart = startDiaMm <= endDiaMm`; same in `Taper.keywayAbsSpanMm`, Taper.kt:65) | **Magnitude** — SET is whichever stored value is smaller | A third convention; agrees with midpoint only when the data already follows the contract |
| Carousel SET/LET labels, taper titles | **Midpoint** via `taperSetLetMapping` | |
| PDF footer / SET-position math | Magnitude-based detection (per the removed §3.3 warning's `taperFaceDiametersMm` notes) — not exhaustively re-verified here; re-check during the fix | |

Three conventions coexist: **toggle-keyed** (Add dialog), **midpoint-keyed**
(derivation, labels), **magnitude-keyed** (keyway/footer math). They only agree when
the stored data already satisfies SET-by-shaft-half.

## 4. The actual failure mode

The TODO's claim ("Add path stores `startDiaMm = SET` regardless of shaft half") is
what happens in the **default-toggle case**. The Add dialog *does* swap for FWD — but
keyed on the wrong signal. Concretely:

**Scenario A — the common one.** OAL 100″. User adds a taper with the direction toggle
left on AFT (the default), Start 80″, Length 15″, SET 6″, LET 7″. The dialog stores
`startDiaMm = 6` (SET at the x-left/inboard face). The midpoint (87.5″) is in the FWD
half, so the convention says SET belongs at the FWD tip:

- The renderer draws the **small end at the body face** and the large end at the tip —
  backwards for a FWD coupling/prop taper.
- The carousel labels the left field **L.E.T.** and shows 6″ under it (and 7″ under
  S.E.T.) — values appear swapped relative to what the user typed.
- A keyway "from SET" references the magnitude-small end = the inboard face — wrong
  face too.

**Scenario B — single-diameter + rate.** Same placement, user enters only SET + a
manual rate. The dialog puts the typed value in the slot chosen by the *toggle*, then
`deriveTaperDiameters` fills the other end with the sign chosen by the *midpoint*.
When the two disagree, the typed SET value can come out as the **large** end — a value
reinterpretation, not just a label swap.

**Scenario C — measuring frame silently dropped (separate small bug).** The dialog's
FWD toggle never leaves the dialog: `onSubmit` (ShaftScreen.kt:954-957) doesn't pass
it, and `addTaperAt` (ShaftViewModel.kt:1334-1349) never sets `authoredReference`, so
it defaults to AFT. A taper added "measure from FWD" reopens in the carousel showing
"Measure From: AFT" with a converted start value. Geometry is correct; the user's
measuring frame is lost. (Liners *do* persist their authored reference.)

**Minor:** `addTaperAt` calls `rememberTaperDefaults(setDiaMm = startDiaMm, letDiaMm =
endDiaMm)` (ShaftViewModel.kt:1352) — after a FWD-side add those are physically
LET/SET, so the next Add dialog's SET default can seed from a LET value.

## 5. On-device confirmation recipe (2 minutes)

1. New shaft, OAL 100″ (or any), leave everything else empty.
2. Add Taper: direction toggle **AFT** (default), Start 80″, Length 15″, SET 6″, LET 7″.
3. Expected if the bug is live: preview draws the *small* end at the 80″ (inboard) face;
   the taper's carousel card shows S.E.T. 7 / L.E.T. 6.
4. Repeat with the toggle on FWD, Start 5″ (from FWD), same diameters — this path
   stores correctly; compare the two cards.

## 6. Fix options

- **Fix 1 (entry-point, recommended):** in `AddTaperDialog`'s submit, key the SET/LET
  swap on the physical half — `classifyTaperSideByMidpoint` over
  (`physStartMm`, `lengthMm`, OAL) — instead of `isFwd`. One expression change plus
  unit tests; makes the writer agree with derivation, labels, renderer, and keyway math
  for all *new* tapers. (Keep the toggle driving only the start-position arithmetic.)
- **Fix 2 (do regardless):** thread the toggle through `onSubmit` → `addTaperAt` and
  store `authoredReference`, so the carousel reopens in the user's measuring frame.
  Also fix the `rememberTaperDefaults` SET/LET mislabel while there.
- **Fix 3 (data repair — needs your decision):** a normalization pass (at decode or in
  `ensureOverall`-time) that swaps any stored pair violating SET-by-midpoint-half.
  Repairs existing documents created through the buggy path, but **rewrites user data**
  and permanently forecloses representing a reversed taper (large end at the shaft
  tip). The UI labeling already forecloses that visually, so this is arguably safe —
  but it's a product call, not mine.

**Open product question:** is SET-by-shaft-half (SET always faces the nearer shaft
end) a universal truth for the shafts this app covers? Everything in the UI already
assumes it; Fix 3 would bake it into stored data as well.

## 7. Suggested tests once a fix direction is chosen

- Unit: Add-path storage for all four (toggle × half) combinations, both-diameters and
  single-diameter+rate variants.
- Unit: `authoredReference` round-trip through add → carousel display values.
- Pinning: renderer keyway `setAtStart` and carousel labels agree for convention-
  conforming specs (guards against reintroducing a fourth convention).
