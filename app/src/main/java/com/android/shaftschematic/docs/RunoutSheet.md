# RunoutSheet

**Files:**
- `ui/screen/RunoutRoute.kt` — screen UI, station config controls, canvas preview
- `pdf/RunoutPdfComposer.kt` — letter-landscape PDF generation

---

## Responsibilities

- Let the user set TIR orientation (Looking AFT / Looking FORWARD / Not set).
- Let the user override the station count per component (bodies, tapers, liners).
- Render a live canvas preview of the shaft with runout bubbles.
- Export a hand-fill-in PDF runout sheet via SAF.

---

## Bubble Placement Algorithm

Both the canvas preview and the PDF use the same three-pass algorithm:

### Pass 1 — Collect
For each component (sorted AFT→FWD), compute `N` axial station positions:
- **Bodies:** evenly distributed across full length.
- **Tapers / Liners:** inset from each edge by `RUNOUT_EDGE_INSET_MM` (≈ 25.4 mm) so measurements land on the cylindrical run-out, not the transition slope.

Each station produces:
- `stationX` — canvas/page X at the axial measurement point.
- `shaftBottomY` — canvas/page Y at the shaft's outer surface at that station.
- `bubbleX` — bubble center X, spread horizontally within the component. Bubbles are centred on the component's page midpoint; slot width = bubble diameter + minimum gap. The group may splay beyond the component edges symmetrically if N slots exceed the component width.

### Pass 2 — Greedy Level Assignment
All bubbles are sorted by `bubbleX`. Each is assigned the **lowest level** where its circle does not horizontally overlap any already-placed bubble at that level (i.e., `prevRightEdge[level] + minGap ≤ bubbleLeft`).

- Level 0 → shortest leader (closest to shaft).
- Level N → `SHORT_LEADER + N × (LONG_LEADER − SHORT_LEADER)`.

This guarantees zero overlap regardless of station density or component count. In typical configurations only levels 0–1 are used; level 2+ appears only when adjacent components produce densely-packed bubbles at a shared boundary.

### Pass 3 — Draw
Each bubble's leader line is drawn **diagonally** from `(stationX, shaftBottomY)` to `(bubbleX, bubbleTop)`. Because `stationX ≠ bubbleX` in general and spreading is monotonic within each component, leaders fan outward and cannot enter any bubble's interior.

---

## Contracts & Invariants

- Model dimensions are canonical **mm**; all px/pt conversion happens inside the composer/preview.
- Thread components produce no runout stations.
- The PDF page is U.S. Letter landscape (792 × 612 pt).
- Canvas preview and PDF use the same spreading and level-assignment logic so they look identical.
- Keyway reference marker (small filled square at 12-o'clock) appears on every bubble in the PDF.

---

## Future Options

- User-selectable keyway reference angle.
- Multiple orientation diagrams on one sheet (e.g., Looking AFT + Looking FWD side-by-side).
- Printable measurement table rows below each bubble.
