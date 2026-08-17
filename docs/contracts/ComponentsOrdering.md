# Components Ordering – UI Contract (v1.3)

> **Decision (2026-08-06):** physical order is the accepted behavior. v1.1 of this contract
> locked a **newest-on-top** rule; v1.2 recorded that the app had stopped doing that once the
> resolved-component pipeline was wired into the carousel. The product call is now made —
> physical order stays, and the dangling newest-first plumbing has been **removed** (see
> "Removed" below). Reviving newest-on-top would be a new feature, not a regression fix.

## Current Rule
The carousel displays the **resolved** component list (`resolveComponents()` in
`ui/resolved/ResolvedComponent.kt`), sorted by `startMmPhysical` then a per-type sort
key. Auto-bodies interleave at their physical positions. There is no user-facing sort
toggle, and no stored order anywhere — row order is a pure function of the spec.

Newest-on-top cannot coexist with interleaved auto-bodies: an auto-body has no creation
time, it is derived from the gaps between the components around it.

## Removed (2026-08-06)
`ShaftViewModel` no longer keeps a newest-first `componentOrder`:
- `_componentOrder` / `componentOrder: StateFlow<List<ComponentKey>>`,
- `orderAdd` / `orderRemove` / `ensureOrderCoversSpec` and their ~20 call sites in the
  add/remove/split/merge paths,
- `EditState.componentOrder` (undo/redo snapshot) — rows are derived from `spec`, so
  restoring the spec restores the rows; the history collector now combines five flows,
- the `componentOrder` parameter threaded `ShaftRoute → ShaftScreen →
  ComponentCarouselPager`, where it was never read.

Nothing persisted changed: the order list was never part of the document envelope
(`doc/ShaftDocCodec.kt` has no field for it), so every existing document opens exactly as
before.

## Stable facts
- `ComponentKind` values: `BODY, TAPER, THREAD, LINER, COUPLER_BOLT_SLOT`. Kept — it tags
  cards, snackbars and test tags.
- `ComponentKey` (id + kind) is kept for the **model-layer** physical ordering helpers
  (`ShaftSpec.buildPhysicalKeyOrder`, `snapForwardFrom` in `model/ShaftSpecExtensions.kt`).
  It is not a display order.
- Coupler bolt slots appear in the carousel like any other component but are
  reference-only for geometry (they never affect OAL, split bodies, or collide).
  See `CouplerBoltSlot.md`.

## QA
- Add a component that starts before an existing one → it appears **earlier** in the
  carousel (physical order), not on top.
- Recompositions don't change order; ordering is deterministic for a given spec.
- Delete a component then Undo → the row comes back in its physical position (it is derived
  from the restored spec).
