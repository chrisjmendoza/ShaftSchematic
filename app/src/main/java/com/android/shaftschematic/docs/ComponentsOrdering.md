# Components Ordering – UI Contract (v1.2)

> **Supersession note (2026-07-18):** v1.1 of this contract locked a **newest-on-top**
> ordering rule. That rule is no longer what the app does: since the resolved-component
> pipeline was wired into the carousel, display order is **geometric** — components
> (including derived auto-bodies) appear in physical position order along the shaft.
> Newest-on-top cannot coexist with interleaved auto-bodies. This doc describes the
> current behavior; if newest-on-top is still the desired product behavior, that is a
> code regression to raise, not a doc fix.

## Current Rule
The carousel displays the **resolved** component list (`resolveComponents()` in
`ui/resolved/ResolvedComponent.kt`), sorted by `startMmPhysical` then a per-type sort
key. Auto-bodies interleave at their physical positions. There is no user-facing sort
toggle.

## Known dangling state
`ShaftViewModel` still maintains a newest-first `componentOrder`
(`ComponentKey`/`ComponentKind` in `ui/order/ComponentOrder.kt`) and threads it into
`ComponentCarouselPager`, but the carousel does not use it for display ordering.
Candidate for cleanup or for reviving newest-on-top — pending product decision.

## Stable facts
- `ComponentKind` values: `BODY, TAPER, THREAD, LINER, COUPLER_BOLT_SLOT`.
- Coupler bolt slots appear in the carousel like any other component but are
  reference-only for geometry (they never affect OAL, split bodies, or collide).
  See `CouplerBoltSlot.md`.

## QA
- Add a component that starts before an existing one → it appears **earlier** in the
  carousel (physical order), not on top.
- Recompositions don't change order; ordering is deterministic for a given spec.
