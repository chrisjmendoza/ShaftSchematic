# Components Ordering – UI Contract (v1.1, LOCKED)

## Rule (Locked)
**Newest-on-top** is mandatory. The editor must display components so the most recently added item appears first within its type list. The UI must **not** resort by geometry or expose a user-facing sort toggle.

## Rationale
After adding, users immediately edit. Surfacing the newest minimizes scrolling and respects temporal intent.

## Implementation
- ViewModel inserts newly created components at index 0 in their respective lists.
- `ComponentsUnifiedList` must keep each list’s order as-is (no `.sorted*` calls).
- The combined list may group by type in a fixed order (Bodies, Tapers, Threads, Liners) for predictability.

## QA
- Add Body → Body #1 appears at top of the Body group.
- Add another Body → now it appears above the previous one.
- No UI affordance exists to switch to spatial sort; recompositions don’t change order.
