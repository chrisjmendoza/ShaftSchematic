# Runout Bubble Collision System — Review & Implementation (2026-07-18)

Branch: `feat/runout-bubble-collision` (off `fix/runout-wear-resolved-components`)

**Request:** review the runout drawings and bubble implementations; add a system that
guarantees pointer (leader) lines never intersect other lines or bubbles, with bubbles
alternating on level to fit better — matching the hand-drawn shop reference sheet.

---

## Review findings (old implementation)

The doc (`RunoutSheet.md`) claimed a collision-free three-pass algorithm shared by the
PDF and canvas preview. Neither claim held:

### 1. Leaders could slice through bubbles (the core defect)
The greedy level assignment only prevented **bubble-vs-bubble horizontal overlap on the
same level**. A leader line to a level-1 bubble travels through the level-0 band on its
way down; nothing checked that path. With densely packed bubbles at component boundaries
(exactly when level 1 gets used), a level-1 leader routinely passed through the interior
of a neighbouring level-0 circle.

### 2. Adjacent levels could physically overlap
`levelStep = LONG_LEADER − SHORT_LEADER = 38 pt`, but the bubble diameter is 40 pt.
Two bubbles on adjacent levels with horizontal centre distance < √(40² − 38²) ≈ 12.5 pt
overlapped. The greedy assignment allowed arbitrarily small horizontal distance across
levels, so this was reachable in dense configurations.

### 3. The per-level overlap check was unsound with varying ODs
Bubble Y was `shaftBottomY(station) + leader`, i.e. it tracked the local shaft OD. Two
bubbles on *different* levels under different ODs could end up at nearly the same actual
Y, but the collision logic only compared bubbles within the same level index.

### 4. Leaders could cross each other across components
Bubble groups were centred on each component's midpoint and could splay past the
component edges into the neighbour's territory; monotonicity was only guaranteed
*within* a component. Edge clamping (`coerceIn`) could also silently break ordering.

### 5. PDF and canvas preview had drifted apart
Despite the "same logic so they look identical" contract:
- Body stations: PDF used `len/(count+1)` interior points; canvas used cell midpoints
  `(i+0.5)·len/count`. For a 100 mm body with 2 stations: 33/67 vs 25/75.
- Taper/liner edge-inset cap: PDF 20% of length, canvas 35%.
- The canvas **silently dropped** bubbles that didn't fit its reserved height (level ≥ 1
  bubbles frequently vanished from the preview); the PDF drew them regardless.

### 6. Dead documentation
The KDoc described alternating two-row placement ("consecutive stations alternate
between row 1 and row 2") — the shop convention — but the code implemented greedy
lowest-level instead: with room available, everything landed in a single row.

---

## New system

**Single shared engine: `app/src/main/java/com/android/shaftschematic/geom/RunoutBubbleLayout.kt`**
(pure Kotlin, no Android imports, unit-testable on the JVM). Both
`RunoutPdfComposer` and the `RunoutRoute` canvas preview call it; neither renderer
contains placement logic anymore.

### Rows — alternating + globally aligned
- Within each component, stations alternate rows 0,1,0,1 (hand-drawn convention;
  what the old KDoc promised). Single-station components sit on row 0.
- Component boundary: if the previous component ended on row 0 close enough to collide,
  the next component starts on row 1 (phase flip keeps the rhythm).
- All bubbles in a row share one centre Y, anchored below the **deepest drawn shaft
  point** — depth no longer varies with local OD, which is both the hand-drawn look and
  what makes the collision math sound.

### Spacing invariants (make bubble contact impossible)
| pair | min centre dx | purpose |
|---|---|---|
| same row (adjacent) | `2r + gap` (45 pt) | circles disjoint |
| different row (adjacent) | `r + gap` (25 pt) | vertical leader drops clear all circles above |
- `rowStep = 2r + gap` (45 pt) vertically → different rows disjoint at any dx
  (fixes finding 2).
- `2 × crossRowPitch ≥ sameRowPitch` → enforcing only adjacent pairs covers all pairs.
- If the stations can't fit the width at minimum clearances (~27 stations on a letter
  page), the plan compresses and flags itself (`compressed = true`, guarantees void,
  reported honestly via `unresolvedCollisions`). See the addendum for why deeper row
  stacks can't raise this limit.

### Bubble x — least-squares under constraints
Minimise Σ(bubbleX − stationX)² subject to pitch constraints and page bounds, solved by
pool-adjacent-violators isotonic regression. Bubbles sit directly under their stations
when possible; dense clusters spread symmetrically, stay centred over their stations,
and bubble order always equals station order.

### Leaders — explicit verification + dogleg fallback (the "system to make sure")
1. Try a straight diagonal station → bubble top.
2. Verify against every other bubble (segment-circle, inflated by `gap/2`) and every
   other leader (segment-segment proper-crossing test).
3. Any failure re-routes the leader as a **dogleg**:
   `station → common departure line (deepest surface) → elbow above row 0 → vertical drop to bubble top`.

Why this converges to zero collisions:
- Dogleg diagonals all run between the same two horizontal lines with matching
  left-to-right order at both ends → dogleg-vs-dogleg crossings are geometrically
  impossible.
- The dogleg corridor is above every circle top (elbow clearance) and the vertical drop
  clears circles by the cross-row pitch → dogleg-vs-bubble intersections impossible.
- Therefore every remaining conflict involves a straight leader, and each repair pass
  converts at least one → terminates with zero in ≤ n passes (non-compressed configs).

The stub-to-departure-line detail matters: during testing, dogleg diagonals *did* cross
at OD steps (thin shaft next to a big coupler with a shifted cluster) when they started
at their differing surface heights. Dropping every dogleg to a common departure line
first eliminated the crossing class entirely — caught by the randomized stress test
(trial 11), not by inspection.

### Renderer changes
- `RunoutPdfComposer`: plans horizontally **before** the vertical layout, so the page
  reserves exactly the height the rows need (previously hardcoded to 2 rows). Draws
  leader polylines (2–4 vertices).
- `RunoutRoute` canvas: same engine; re-computes `ShaftLayout` once if the planned row
  count differs from the 2-row assumption. No longer silently drops overflow bubbles
  (fixes finding 5).

### Standardised station math (finding 5)
- Body stations: **cell midpoints** — better end coverage.
- Taper/liner inset: `min(25.4 mm, 20% of length)`.

## Tests

`app/src/test/java/com/android/shaftschematic/geom/RunoutBubbleLayoutTest.kt` — 20 tests:
station math, alternation and phase flip, single-station rows, least-squares centring,
order preservation, and full-invariant checks (circles disjoint, no leader enters a
foreign bubble at *exact* radius, no leader crossings, page bounds) across a typical
5-component shaft, dense component boundaries, stepped shaft surfaces, 8-stations-on-a-
short-component, 60 randomized stress configurations, and a degenerate 40-station
overload (asserts the `compressed` flag + graceful behaviour). Full app unit suite green.

## Addendum (2026-07-18, follow-up) — why not 3–4 rows for tight layouts?

Chris asked whether tight layouts should be allowed to stack 3–4 bubble rows. Answer
after working the geometry: **extra rows cannot relieve tight layouts** under any
readable leader convention, so the engine stays at two rows.

The limiting resource is horizontal **leader lanes**, not rows. A leader must travel
from the shaft (above the bubble field) down into its circle, so its final descent
crosses every row band above that bubble. Crossing a band requires ~`radius + clearance`
(≈ 22.5 pt) of horizontal separation from every circle in that band. Every bubble
therefore consumes ~one 25 pt lane of page width *no matter which row it sits on* —
40 stations need ~1000 pt of lanes whether they occupy 2 rows or 4. Depth adds page
height and leader length; it adds zero width capacity and reduces zero splay.

Two alternating rows already achieve the densest packing available: in tight regions,
per-component alternation plus the boundary phase flip put every binding adjacent pair
on different rows, i.e. at the minimum (cross-row) pitch. Deeper cycles cannot beat it.

Consequence: the original implementation's "deepen the cycle to 3 then 4 before
compressing" fallback was dead weight — it could never reduce the required width, and
because the phase-flip heuristic only ran at cycle 2, it could make the compressed
baseline slightly *worse*. Removed same day; `rowCount` is now always ≤ 2 and the plan
goes straight to the flagged compression fallback in physically impossible configs.

If tight sheets ever need more real capacity, the honest knobs are bubble radius and
minimum gap (the lane width scales with them): e.g. radius 16 pt + gap 3 pt lowers the
lane from 25 pt to 19 pt (~30% more stations per page) while circles stay large enough
for hand-written readings. That is a product decision about the sheet's look — not
implemented, ask Chris first.

## Follow-up drawing conventions (2026-07-18, same session)

Applied after Chris's markup review of the artifact:

- **OAL dimension raised**: `OAL_LINE_SPACE_PT` 18 → 90 pt (≈ 1.25 in above the shaft
  top; Chris asked for 1–1.5 in) with witness lines dropping to the shaft's actual top
  edge at each SET face (3 pt gap, 5 pt extension past the line) — the schematic/wear
  convention. The vertical layout reserves the new space, so shaft + bubbles recentre.
- **Keyway marker**: 4 pt filled square → 7 pt open square notch straddling the rim at
  12-o'clock (white fill blanks the rim and leader tip inside the notch). Added to the
  canvas preview too, which previously drew no keyway marker at all (parity gap).
- **Threads**: no code change needed — the composer already draws hatched thread
  envelopes at physical positions (excluded threads outside the SET-to-SET arrows, via
  `syncExcludedThreadPositions`). `RunoutSheet.md` had two stale lines claiming threads
  "are not drawn"; corrected, and thread scenarios added to the review artifact.

## Known limitation (out of scope)

Leaders are verified against bubbles and other leaders — not against the drawn shaft
profile itself. A leader from a station on a thin section adjacent to a much larger
component could in principle clip that component's lower corner before descending.
The common-departure-line dogleg makes this unlikely (stubs drop straight down at the
station x). If it shows up in practice, the engine's verification pass can take an
"obstacle segments" input — the shaft outline — with the same dogleg repair.
